#!/usr/bin/env python3
"""
V3 Automated Enforcer Daemon - Exact Quota/Time Cutoff + Auto-Reboot
=====================================================================
- Tier 1: Fast active sweep every 60s (active sessions only, <1% CPU)
- Tier 1b: Offline all-user sweep every 5 min
- Tier 2: Config audit every 6 hours or on boot
- Auto-Reboot: If router fails 6 consecutive times, SSH reboot it
"""
import sys, os, time, socket, logging, json, re
from datetime import datetime, timedelta
sys.path.append('/opt/hotspot-cloud')
try:
    import cloud_database as db
except ImportError:
    db = None

import cloud_config_enforcer as cce
from cloud_mikrotik import RouterOSClient
import deploy_theme as dt

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler('/opt/hotspot-cloud/automated_enforcer.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger("enforcer-v3")

online_state = {}
last_full_audit = {}
last_active_sweep = {}
last_cleanup_sweep = {}
last_offline_sweep = {}
consecutive_failures = {}
skip_until = {}
last_reboot_attempt = {}
last_probe_time = {}
last_version_probe = {}
last_theme_sync = {}
last_deployed_theme = {}
_REPAIR_MS = 10 * 60 * 1000
_PROFILE_UP_CACHE = {}
REBOOT_COOLDOWN = 600  # 10 min between reboot attempts


def init_online_state():
    """Load last-known router status from DB so enforcer restarts don't
    treat every router as 'just came online' (avoids mass audit CPU spike)."""
    if not db: return
    try:
        conn = db.get_db()
        c = conn.cursor()
        c.execute("SELECT id, status FROM routers")
        for rid, status in c.fetchall():
            online_state[rid] = (status == 'online')
        conn.close()
    except Exception as e:
        logger.error(f"Error initializing online_state: {e}")



def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

def is_port_open(host, port=8728, timeout=2.5):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(timeout)
        s.connect((host, port))
        s.close()
        return True
    except Exception:
        return False

def parse_ros_duration(value):
    """Convert a RouterOS duration string ('4w','1w2d','30d','24h','1h30m','3m',
    '2d4h','1mo') into a timedelta, or None when unknown / zero.

    This is the authoritative parser for the [ORIG-UP:] tag written by the
    router at activation time (the TRUE plan validity)."""
    v = (value or '').strip().lower()
    if not v or v in ('0s', 'none', '00:00:00', '0'): return None
    v = v.replace('month', 'mo').replace('months', 'mo')
    total = timedelta(0)
    for grp in re.findall(r'(\d+(?:\.\d+)?)\s*([a-z]+)', v):
        try: n = float(grp[0])
        except: continue
        u = grp[1]
        if u == 'y': total += timedelta(days=365)
        elif u == 'w': total += timedelta(weeks=int(n))
        elif u in ('mo', 'months'): total += timedelta(days=30)
        elif u == 'd': total += timedelta(days=int(n))
        elif u == 'h': total += timedelta(hours=int(n))
        elif u == 'm': total += timedelta(minutes=int(n))
        elif u == 's': total += timedelta(seconds=int(n))
    return total if total > timedelta(0) else None


def format_ros_duration(td):
    """Convert a timedelta back to a RouterOS limit-uptime string (e.g. '29d23h')."""
    if td is None or td <= timedelta(0): return '1s'
    total_s = int(td.total_seconds())
    if total_s <= 0: return '1s'
    days = total_s // 86400
    hours = (total_s % 86400) // 3600
    mins = (total_s % 3600) // 60
    if days:
        return f"{days}d{hours}h"
    if hours:
        return f"{hours}h{mins}m"
    if mins:
        return f"{mins}m"
    return "1m"


def parse_duration(prof_name, comment=""):
    """Resolve a voucher's continuous validity from the router's AUTHORITATIVE
    [ORIG-UP:] tag, falling back ONLY to clearly duration-encoded plan names.

    Returns a timedelta, or None when the validity is unknown.
    IMPORTANT: None means "do NOT treat as time-expired". A short default here
    previously cut long-term vouchers (7d/30d/50GB) after just 1 day."""
    c = comment or ""
    if '[ORIG-UP:' in c:
        try:
            up_tag = c.split('[ORIG-UP:')[1].split(']')[0].strip()
        except:
            up_tag = ''
        if up_tag:
            d = parse_ros_duration(up_tag)
            if d:
                return d
    p = (prof_name or "").lower()
    bl = p + ' ' + c
    known = [
        ("30day", timedelta(days=30)), ("30d", timedelta(days=30)),
        ("4w", timedelta(days=30)), ("50gb", timedelta(days=30)),
        ("40g", timedelta(days=30)), ("1month", timedelta(days=30)),
        ("7d", timedelta(days=7)), ("1w", timedelta(days=7)),
        ("3d", timedelta(days=3)),
        ("24h", timedelta(days=1)), ("1d", timedelta(days=1)),
        ("12h", timedelta(hours=12)), ("6h", timedelta(hours=6)),
        ("2h", timedelta(hours=2)), ("1h", timedelta(hours=1)),
        ("1hour", timedelta(hours=1)),
    ]
    for key, dur in known:
        if key in bl:
            return dur
    if 'test' in p or 'test' in c:
        return timedelta(minutes=3)
    # Unknown validity -> fail-safe: never expire by time.
    return None

def parse_orig_limit(prof_name, comment=""):
    c = (comment or "").lower()
    p = (prof_name or "").lower()
    if '[orig-limit:' in c:
        try: return int(comment.split('[ORIG-LIMIT:')[1].split(']')[0])
        except: pass
    if '50gb' in p or '50gb' in c: return 53687091200
    elif '40g' in p or '40g' in c: return 42949672960
    elif '30d' in p or '30day' in p: return 32212254720
    elif '4gb' in p or '4gb' in c: return 4294967296
    elif '2gb' in p or '2gb' in c: return 2147483648
    elif '1000ks' in p or '1000ks' in c: return 1782579200
    elif '1gb' in p or '1gb' in c: return 1073741824
    elif '500ks' in p or '500ks' in c: return 786432000
    elif 'test' in p: return 52428800
    # Unknown quota -> 0 (fail-safe: do not cut by guessed quota; the router
    # kernel + [USED:]/[ORIG-LIMIT:] tags are the authoritative cutoff).
    return 0

def record_audit(router_id, router_name, issues, fixes, summary):
    if not db: return
    now = int(time.time())
    try:
        conn = db.get_db()
        c = conn.cursor()
        c.execute("""CREATE TABLE IF NOT EXISTS router_audits (
            id INTEGER PRIMARY KEY AUTOINCREMENT, router_id INTEGER NOT NULL,
            router_name TEXT, timestamp INTEGER NOT NULL, is_healthy INTEGER NOT NULL,
            issues_json TEXT NOT NULL, fixes_json TEXT NOT NULL, summary TEXT NOT NULL);""")
        # Ensure routers table has version columns (safe ALTER in case of older DB)
        cols = [row[1] for row in c.execute("PRAGMA table_info(routers)").fetchall()]
        if 'routeros_version' not in cols:
            try: c.execute("ALTER TABLE routers ADD COLUMN routeros_version TEXT DEFAULT ''")
            except Exception: pass
        if 'update_available' not in cols:
            try: c.execute("ALTER TABLE routers ADD COLUMN update_available TEXT DEFAULT ''")
            except Exception: pass
        c.execute("INSERT INTO router_audits (router_id,router_name,timestamp,is_healthy,issues_json,fixes_json,summary) VALUES (?,?,?,?,?,?,?)",
            (router_id, router_name, now, 1 if not issues else 0, json.dumps(issues), json.dumps(fixes), summary))
        c.execute("UPDATE routers SET last_audit_time=?, audit_status=?, audit_summary=? WHERE id=?",
            (now, "HEALTHY" if not issues else "NEEDS_FIX", summary, router_id))
        conn.commit(); conn.close()
    except Exception as e:
        logger.error(f"DB Error: {e}")


def probe_router_version(router_id, router_name, host, user, pwd):
    """Lightweight 1-call RouterOS version + update probe (stored for admin dashboard).
    Runs on the enforcer's existing connection cadence; never adds a new loop."""
    if not db: return
    try:
        client = RouterOSClient(host, timeout=8.0)
        client.connect()
        if client.login(user, pwd):
            # version from /system/resource (cheap)
            res = client.talk(['/system/resource/print'])
            ver = ''
            for x in res:
                if x[0] == '!re':
                    d = parse_re(x)
                    ver = d.get('version', '') or ''
            # update availability (only if the router answers quickly)
            upd = ''
            try:
                up = client.talk(['/system/package/update/print'])
                for x in up:
                    if x[0] == '!re':
                        d = parse_re(x)
                        inst = d.get('installed-version', '') or ''
                        latest = d.get('latest-version', '') or ''
                        if latest and latest != inst:
                            upd = f"{inst} -> {latest}"
            except Exception:
                pass
            client.close()
            conn = db.get_db()
            c = conn.cursor()
            c.execute("UPDATE routers SET routeros_version=?, update_available=? WHERE id=?",
                (ver, upd, router_id))
            conn.commit(); conn.close()
            if upd:
                logger.info(f"Router {router_id} ({router_name}): update available {upd}")
    except Exception as e:
        try: client.close()
        except Exception: pass
        logger.error(f"Version probe failed for router {router_id} ({router_name}): {e}")


def reboot_router(host, user="admin", pwd="Khant1234@"):
    """SSH reboot for unresponsive routers"""
    try:
        import paramiko
        ssh = paramiko.SSHClient()
        ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        ssh.connect(host, port=22, username=user, password=pwd, timeout=8)
        ssh.exec_command('/system reboot', timeout=5)
        ssh.close()
        logger.info(f"REBOOTED router {host} via SSH")
        return True
    except Exception as e:
        logger.error(f"SSH reboot failed for {host}: {e}")
        return False

def run_fast_active_sweep(router_dict):
    """Tier 1: Check active sessions only - ULTRA lightweight.
    Reads quota/time tags from the ACTIVE SESSION comment (returned in 1 API call)
    instead of querying each user. Only does a targeted user lookup for sessions
    already clearly at/over their limit. Cuts ~82 API round-trips per sweep to
    1-3, removing CPU pressure during peak hours."""
    rid = router_dict["id"]
    rname = router_dict["name"]
    host = router_dict["wg_ip"]
    user = router_dict.get("api_user", "admin")
    pwd = router_dict.get("api_pass", "Khant1234@")

    client = RouterOSClient(host, timeout=12.0)
    try:
        client.connect()
        if not client.login(user, pwd): return

        # Router clock
        clock_res = client.talk(['/system/clock/print'])
        clock = [parse_re(x) for x in clock_res if x[0] == '!re']
        if not clock: client.close(); return
        try:
            router_now = datetime.strptime(f"{clock[0].get('date','')} {clock[0].get('time','')}", "%Y-%m-%d %H:%M:%S")
        except: router_now = datetime.now()

        # Active sessions WITH comment in ONE call (no per-user queries)
        act_res = client.talk(['/ip/hotspot/active/print', '=.proplist=.id,user,bytes-in,bytes-out,uptime,comment'])
        actives = [parse_re(x) for x in act_res if x[0] == '!re']

        fixes = []
        for a in actives:
            u_name = a.get('user', '')
            if not u_name or u_name in ['admin', 'default-trial']: continue

            aid = a.get('.id')
            session_bytes = int(a.get('bytes-in', 0)) + int(a.get('bytes-out', 0))
            # Session carries the user comment with [BASE:]/[USED:]/[ORIG-LIMIT:]/[ACT:] tags
            comm = a.get('comment', '') or ''

            # Parse quota from session comment only (NO user query yet)
            base_bytes = 0
            if '[BASE:' in comm:
                try: base_bytes = int(comm.split('[BASE:')[1].split(']')[0])
                except: pass
            orig_lim = parse_orig_limit('', comm)
            total_used = base_bytes + session_bytes
            is_quota_exhausted = (orig_lim > 0 and total_used >= orig_lim)
            is_tagged_expired = ('[EXPIRED:' in comm or '[EXHAUSTED:' in comm)

            # Time check from session comment (validity is NEVER guessed short —
            # unknown duration = NOT treated as time-expired; protects long-term plans).
            is_time_expired = False
            if '[ACT:' in comm:
                try:
                    act_str = comm.split('[ACT:')[1].split(']')[0].strip()
                    act_dt = datetime.strptime(act_str, "%Y-%m-%d %H:%M:%S") if '-' in act_str else datetime.strptime(act_str, "%b/%d/%Y %H:%M:%S")
                    dur = parse_duration('', comm)
                    if dur is not None and router_now >= (act_dt + dur):
                        is_time_expired = True
                except: pass

            # Only if NO quota/time proof yet, verify via one targeted user lookup.
            # A tag alone is NOT enough to kill an active long-term customer —
            # the user must also be truly disabled on the router (a re-activated
            # voucher that still holds remaining time/quota must NOT be killed).
            uid = None
            if not (is_time_expired or is_quota_exhausted):
                try:
                    q = client.talk(['/ip/hotspot/user/print', f'?name={u_name}', '=.proplist=.id,comment,disabled'])
                    m = [parse_re(x) for x in q if x[0] == '!re']
                    if m:
                        u_obj = m[0]
                        uid = u_obj.get('.id')
                        u_comm = u_obj.get('comment') or ''
                        if u_comm:
                            comm = u_comm
                            if '[BASE:' in u_comm:
                                try: base_bytes = int(u_comm.split('[BASE:')[1].split(']')[0])
                                except: pass
                            orig_lim = parse_orig_limit('', u_comm)
                            total_used = base_bytes + session_bytes
                            is_quota_exhausted = (orig_lim > 0 and total_used >= orig_lim)
                        if u_obj.get('disabled') == 'true':
                            is_tagged_expired = True
                        else:
                            # Enabled on router => REMAINING time/quota: never kill.
                            is_tagged_expired = False
                except Exception:
                    pass

            if is_quota_exhausted or is_time_expired or is_tagged_expired:
                # For actual violators, ensure we have uid before kicking
                if not uid:
                    try:
                        q = client.talk(['/ip/hotspot/user/print', f'?name={u_name}', '=.proplist=.id'])
                        m = [parse_re(x) for x in q if x[0] == '!re']
                        uid = m[0].get('.id') if m else None
                    except Exception:
                        pass

                reason = "DATA_QUOTA_EXCEEDED" if is_quota_exhausted else ("TIME_OVERDUE" if is_time_expired else "DISABLED")
                tag = "[EXHAUSTED:DATA]" if is_quota_exhausted else f"[EXPIRED:{reason}]"
                new_comm = comm if tag in comm else f"{comm} {tag}".strip()

                if aid: client.talk(['/ip/hotspot/active/remove', f'=.id={aid}'])
                ck = client.talk(['/ip/hotspot/cookie/print', f'?user={u_name}', '=.proplist=.id'])
                for c in [parse_re(x) for x in ck if x[0] == '!re']:
                    if c.get('.id'): client.talk(['/ip/hotspot/cookie/remove', f'=.id={c[".id"]}'])
                if uid:
                    client.talk(['/ip/hotspot/user/set', f'=.id={uid}', '=disabled=yes', '=limit-bytes-total=1', '=limit-uptime=1s', f'=comment={new_comm}'])
                    try: client.talk(['/ip/hotspot/user/reset-counters', f'=.id={uid}'])
                    except: pass
                logger.info(f"Router {rid} ({rname}): Auto-fixed user {u_name} ({reason}: Used={total_used}/{orig_lim}) - Kicked & Disabled + counters reset")
                fixes.append(f"{u_name} ({reason})")

        client.close()
        if fixes:
            record_audit(rid, rname, [], fixes, f"Active sweep fixed {len(fixes)} violations.")
    except Exception as e:
        try: client.close()
        except: pass
        raise

def run_offline_quota_sweep(router_dict):
    """Tier 1b: Scan ALL non-active users for quota/time violations (every 5 min)"""
    rid = router_dict["id"]
    rname = router_dict["name"]
    host = router_dict["wg_ip"]
    user = router_dict.get("api_user", "admin")
    pwd = router_dict.get("api_pass", "Khant1234@")

    client = RouterOSClient(host, timeout=30.0)
    try:
        client.connect()
        if not client.login(user, pwd): return

        clock_res = client.talk(['/system/clock/print'])
        clock = [parse_re(x) for x in clock_res if x[0] == '!re']
        if not clock: client.close(); return
        try:
            router_now = datetime.strptime(f"{clock[0].get('date','')} {clock[0].get('time','')}", "%Y-%m-%d %H:%M:%S")
            if router_now.year < 2025: router_now = datetime.now()
        except: router_now = datetime.now()

        act_res = client.talk(['/ip/hotspot/active/print', '=.proplist=user'])
        active_names = {parse_re(x).get('user') for x in act_res if x[0] == '!re'}

        all_user_res = client.talk(['/ip/hotspot/user/print', '=.proplist=.id,name,comment,profile,disabled,bytes-in,bytes-out'])
        all_users = [parse_re(x) for x in all_user_res if x[0] == '!re']

        def resolve_dur(prof, comm):
            """Validity from [ORIG-UP:] tag or duration-encoded name; fall back to
            the router PROFILE's own limit-uptime (authoritative for long-term
            plans whose names carry no time keyword like '4GB'). Cache per router+profile."""
            d = parse_duration(prof, comm)
            if d is None and prof:
                key = (rid, prof)
                val = _PROFILE_UP_CACHE.get(key, 'MISS')
                if val == 'MISS':
                    val = None
                    try:
                        pr_res = client.talk(['/ip/hotspot/user/profile/print', f'?name={prof}', '=.proplist=limit-uptime'])
                        for row in [parse_re(x) for x in pr_res if x[0] == '!re']:
                            val = parse_ros_duration(row.get('limit-uptime'))
                            break
                    except Exception:
                        val = None
                    _PROFILE_UP_CACHE[key] = val
                d = val or None
            return d

        fixes = []
        for u in all_users:
            uname = u.get('name', '')
            if uname in ['admin', 'default-trial'] or uname in active_names: continue
            comm = u.get('comment', '')
            prof = u.get('profile', '')
            uid = u.get('.id')
            is_dis = (u.get('disabled') == 'true')

            # Case A: has expired tag but still enabled
            if ('[EXPIRED:' in comm or '[EXHAUSTED:' in comm) and not is_dis:
                client.talk(['/ip/hotspot/user/set', f'=.id={uid}', '=disabled=yes', '=limit-bytes-total=1', '=limit-uptime=1s'])
                try: client.talk(['/ip/hotspot/user/reset-counters', f'=.id={uid}'])
                except: pass
                fixes.append(f"Disabled leaked user {uname}")
                continue

            # Case C: REPAIR long-term vouchers that a bug (1-day default
            # validity) previously disabled in error. Only touched when we can
            # POSITIVELY prove the plan is not finished:
            #   - [ACT:]+known validity OR duration-encoded name => time NOT elapsed,
            #   - [ORIG-LIMIT:]/[USED:] show quota NOT exhausted.
            if is_dis:
                try:
                    act_dt = None
                    if '[ACT:' in comm:
                        act_str = comm.split('[ACT:')[1].split(']')[0].strip()
                        act_dt = datetime.strptime(act_str, "%Y-%m-%d %H:%M:%S") if '-' in act_str else datetime.strptime(act_str, "%b/%d/%Y %H:%M:%S")
                    dur = resolve_dur(prof, comm)
                    if act_dt is not None and dur is not None:
                        # What was already consumed — the [USED:]/[BASE:] tags are the
                        # authoritative proof (the old buggy disable path reset the
                        # hardware counters, so bytes-in/out alone can be 0 even after use).
                        used_b = 0
                        if '[USED:' in comm:
                            try: used_b = int(comm.split('[USED:')[1].split(']')[0])
                            except: pass
                        base_b = 0
                        if '[BASE:' in comm:
                            try: base_b = int(comm.split('[BASE:')[1].split(']')[0])
                            except: pass
                        consumed = max(used_b, base_b,
                                       int(u.get('bytes-in', 0) or 0) + int(u.get('bytes-out', 0) or 0))
                        orig_b = parse_orig_limit(prof, comm)
                        time_not_elapsed = router_now < (act_dt + dur)
                        # "Let them feel the REST": restore ONLY when we KNOW exactly how
                        # much was consumed AND the true original limit. Without usage proof
                        # we could accidentally grant the FULL entitlement — never do that.
                        has_usage_proof = consumed > 0
                        rest_known = (orig_b > 0 and has_usage_proof and consumed < orig_b)
                        if time_not_elapsed and rest_known:
                            remaining = (act_dt + dur) - router_now
                            # cap at the original validity — never more than bought
                            if remaining > dur:
                                remaining = dur
                            # exact REST of quota, floored to a usable minimum, never > original
                            rem_bytes = max(1048576, orig_b - consumed)
                            if rem_bytes > orig_b:
                                rem_bytes = orig_b
                            clean_comm = comm
                            for tg in ['[EXPIRED:TIME_OVERDUE]', '[EXPIRED:DISABLED]', '[EXHAUSTED:DATA]', '[CUTOFF]', '[EXPIRED:1]', '[EXPIRED:TIME_OVERDUE]:', '[ERROR]']:
                                clean_comm = clean_comm.replace(tg, '').replace(tg + ' ', '')
                            clean_comm = re.sub(r'\[EXPIRED:[^\]]*\]', '', clean_comm)
                            clean_comm = ' '.join(clean_comm.split())
                            args = ['/ip/hotspot/user/set', f'=.id={uid}', '=disabled=no',
                                    f'=limit-uptime={format_ros_duration(remaining)}',
                                    f'=limit-bytes-total={rem_bytes}',
                                    f'=comment={clean_comm}']
                            client.talk(args)
                            logger.info(f"Router {rid} ({rname}): REPAIRED long-term voucher {uname} "
                                        f"(restored REMAINING {format_ros_duration(remaining)} / {rem_bytes} bytes, "
                                        f"consumed {consumed}/{orig_b}) — was wrongly disabled")
                            fixes.append(f"Restored remaining quota/time for {uname} (wrongly disabled)")
                            continue
                        # No positive proof of the remainder -> do NOT auto-restore full
                        # entitlement; leave for the shop to Renew manually.
                        logger.info(f"Router {rid} ({rname}): skipped restore of {uname} — "
                                    f"remainder not provable (time_left={time_not_elapsed}, consumed={consumed}, orig={orig_b})")
                except Exception as e:
                    logger.warning(f"Router {rid} ({rname}): repair check failed for {uname}: {e}")
                # Not provably wrong => it is genuinely finished or unknown: leave it.
                continue

            # Case B: check quota and time
            hw_used = int(u.get('bytes-in', 0)) + int(u.get('bytes-out', 0))
            stored_used = 0
            if '[USED:' in comm:
                try: stored_used = int(comm.split('[USED:')[1].split(']')[0])
                except: pass
            base_bytes = 0
            if '[BASE:' in comm:
                try: base_bytes = int(comm.split('[BASE:')[1].split(']')[0])
                except: pass
            total_used = max(stored_used, base_bytes + hw_used)
            orig_lim = parse_orig_limit(prof, comm)

            is_time_exp = False
            if '[ACT:' in comm:
                try:
                    act_str = comm.split('[ACT:')[1].split(']')[0].strip()
                    act_dt = datetime.strptime(act_str, "%Y-%m-%d %H:%M:%S") if '-' in act_str else datetime.strptime(act_str, "%b/%d/%Y %H:%M:%S")
                    dur = resolve_dur(prof, comm)
                    if dur is not None and router_now >= (act_dt + dur):
                        is_time_exp = True
                except: pass

            is_quota_exc = (orig_lim > 0 and total_used >= orig_lim)

            if is_quota_exc or is_time_exp:
                tag = "[EXHAUSTED:DATA]" if is_quota_exc else "[EXPIRED:TIME_OVERDUE]"
                new_comm = comm if tag in comm else f"{comm} {tag}".strip()
                client.talk(['/ip/hotspot/user/set', f'=.id={uid}', '=disabled=yes', '=limit-bytes-total=1', '=limit-uptime=1s', f'=comment={new_comm}'])
                try: client.talk(['/ip/hotspot/user/reset-counters', f'=.id={uid}'])
                except: pass
                reason = "DATA_QUOTA_EXCEEDED" if is_quota_exc else "TIME_OVERDUE"
                logger.info(f"Router {rid} ({rname}): Offline sweep: disabled user {uname} ({reason}: Used={total_used}/{orig_lim})")
                fixes.append(f"{uname} ({reason})")

        client.close()
        if fixes:
            record_audit(rid, rname, [], fixes, f"Offline sweep auto-fixed {len(fixes)} violations.")
    except Exception as e:
        logger.error(f"Error in offline sweep on router {rid} ({rname}): {e}")
        try: client.close()
        except: pass


def get_routers():
    if not db: return []
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers")
    rows = c.fetchall()
    conn.close()
    return [dict(r) for r in rows]

def run_enforcement_cycle():
    now = time.time()
    routers = get_routers()

    for r_dict in routers:
        rid = r_dict["id"]
        rname = r_dict["name"]
        wg_ip = r_dict.get("wg_ip", "")
        if not wg_ip: continue

        # PORT PROBE THROTTLE: only open a TCP probe every 60s per router.
        # Previously probed every 5s = 12 handshakes/min/router even when idle
        # -> needless CPU on loaded routers (esp. KyawGyi 1847 users).
        last_probe = last_probe_time.get(rid, 0.0)
        probe_now = (now - last_probe) >= 60
        if probe_now:
            last_probe_time[rid] = now

        if probe_now:
            reachable = is_port_open(wg_ip, port=r_dict.get("api_port", 8728), timeout=2.5)
        else:
            reachable = online_state.get(rid, False)  # trust last known state between probes
        was_online = online_state.get(rid, False)

        if not reachable:
            if was_online:
                logger.info(f"Router {rid} ({rname} @ {wg_ip}) went offline.")
            online_state[rid] = False

            # AUTO-REBOOT: If router fails too many times, reboot via SSH
            fails = consecutive_failures.get(rid, 0)
            if fails >= 6:
                last_rb = last_reboot_attempt.get(rid, 0)
                if (now - last_rb) > REBOOT_COOLDOWN:
                    logger.warning(f"Router {rid} ({rname}) unreachable after {fails} failures. Attempting SSH reboot...")
                    user = r_dict.get("api_user", "admin")
                    pwd = r_dict.get("api_pass", "Khant1234@")
                    if reboot_router(wg_ip, user, pwd):
                        last_reboot_attempt[rid] = now
                        consecutive_failures[rid] = 0
                        skip_until[rid] = now + 120  # wait 2 min for reboot
            continue

        just_came_online = (not was_online)
        online_state[rid] = True

        # Version + update probe (every 6h or on boot) for admin dashboard
        ver_elapsed = now - last_version_probe.get(rid, 0)
        if just_came_online or ver_elapsed >= 21600:
            last_version_probe[rid] = now
            try:
                probe_router_version(rid, rname, wg_ip,
                    r_dict.get("api_user", "admin"), r_dict.get("api_pass", "Khant1234@"))
            except Exception as e:
                logger.error(f"Version probe error on router {rid} ({rname}): {e}")

        # Tier 1: Fast Active Sweep (every 60s)
        skip_end = skip_until.get(rid, 0)
        if now < skip_end:
            pass
        elif (now - last_active_sweep.get(rid, 0)) >= 60:
            last_active_sweep[rid] = now
            try:
                run_fast_active_sweep(r_dict)
                consecutive_failures[rid] = 0
            except Exception as e:
                err_str = str(e).lower()
                if 'timed out' in err_str or 'timeout' in err_str or 'connection' in err_str:
                    fails = consecutive_failures.get(rid, 0) + 1
                    consecutive_failures[rid] = fails
                    if fails >= 6:
                        skip_until[rid] = now + 300
                        logger.warning(f"Router {rid} ({rname}) timed out {fails}x - backing off 5min + will reboot")
                    elif fails >= 3:
                        skip_until[rid] = now + 120
                        logger.warning(f"Router {rid} ({rname}) timed out {fails}x - backing off 2min")
                    else:
                        logger.error(f"Error in fast sweep on router {rid} ({rname}): {e}")
                else:
                    logger.error(f"Error in fast sweep on router {rid} ({rname}): {e}")

        # Tier 1b: Offline Quota Sweep (every 15 min - CPU safe)
        # Offline users are NOT consuming data, so a 15-min check is safe.
        if (now - last_offline_sweep.get(rid, 0)) >= 900:
            last_offline_sweep[rid] = now
            run_offline_quota_sweep(r_dict)

        # Tier 2: Full Config Audit + V3 Deploy (on boot or every 24 hours)
        # 24h instead of 6h because routers self-heal on boot now; audits are
        # heavy (they rewrite all scripts) and spike CPU on small routers.
        time_since_full = now - last_full_audit.get(rid, 0)
        if just_came_online or (time_since_full >= 86400):
            try:
                # Deploy V3 startup-heal + all scripts when router comes online
                if just_came_online:
                    try:
                        from deploy_v3_to_router import deploy_to_router
                        logger.info(f"Deploying V3 startup-heal to router {rid} ({rname}) [{wg_ip}]...")
                        deploy_to_router(wg_ip, r_dict.get("api_port", 8728))
                        logger.info(f"Router {rid} ({rname}): V3 startup-heal deployed successfully.")
                    except Exception as de:
                        logger.error(f"V3 deploy to router {rid} ({rname}) failed: {de}")

                unbind = just_came_online and (rid in [6, 7])
                logger.info(f"Running V3 audit on router {rid} ({rname}) [boot={just_came_online}]...")
                res = cce.audit_router_by_id(rid, auto_fix=True, unbind_macs=unbind)
                last_full_audit[rid] = now
                logger.info(f"Router {rid} ({rname}) audit: {res.get('status')} - Fixes: {len(res.get('fixes', []))}")
            except Exception as e:
                logger.error(f"Error auditing router {rid} ({rname}): {e}")

        # Tier 2b: Portal Theme Sync (on boot or every 24h via marker file)
        # The Android app writes hotspot/.portal_theme; here we read it and
        # upload the matching generated template folder when it changes.
        if just_came_online or (now - last_theme_sync.get(rid, 0)) >= 86400:
            try:
                last_theme_sync[rid] = now
                fu = r_dict.get("api_user", "admin")
                fp = r_dict.get("api_pass", "Khant1234@")
                fpt = r_dict.get("ftp_port", 21) or 21
                marker = dt.read_theme_marker(wg_ip, fu, fp, fpt)
                if marker:
                    if dt.theme_folder_exists(marker):
                        if last_deployed_theme.get(rid) != marker:
                            res_t = dt.deploy_theme(wg_ip, fu, fp, marker, fpt)
                            last_deployed_theme[rid] = marker
                            logger.info(f"Router {rid} ({rname}): theme sync '{marker}' -> {res_t.get('status')} ({res_t.get('message', '')})")
                        else:
                            logger.info(f"Router {rid} ({rname}): theme '{marker}' already applied.")
                    else:
                        logger.warning(f"Router {rid} ({rname}): marker theme '{marker}' not on VPS store.")
            except Exception as e:
                logger.error(f"Theme sync error on router {rid} ({rname}): {e}")

def main():
    logger.info("Starting V3 Automated Enforcer Daemon (exact cutoff + auto-reboot)...")
    init_online_state()
    # Only audit routers that were offline before (they need healing).
    # Already-online routers get audited on the 24h schedule instead.
    while True:
        try:
            run_enforcement_cycle()
        except Exception as e:
            logger.error(f"Main loop error: {e}")
        time.sleep(5)

if __name__ == '__main__':
    main()

