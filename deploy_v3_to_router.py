#!/usr/bin/env python3
"""Deploy V3 scripts + startup self-heal to router(s).
   python3 deploy_v3_to_router.py [ip] | --all
"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
try:
    import cloud_database as db
except ImportError:
    db = None

USER = 'admin'
PASS = 'Khant1234@'

# Authoritative per-plan validity (profile session-timeout). The router's
# voucher-activate reads this to write [ORIG-UP:] + per-user limit-uptime
# (kernel cutoff), and the VPS enforcer's resolve_dur falls back to it — so
# every plan is time-cut EXACTLY at its real deadline. Approved shop mapping:
#   1-day plans: 2GB, 4GB (AYeikSitt1/YadanarTun), 500Ks, 1000Ks (KyawGyi)
#   long-term:   30Day, 40G_30D, 50GB-30000  (30 days)
#   test:        3 minutes
# NOTE: profiles expose the time limit as session-timeout; limit-uptime is a
# per-user property and is REJECTED on profiles (!trap unknown parameter).
PROFILE_SESSION_TIMEOUT = {
    '2gb': '1d',
    '4gb': '1d',
    '500ks': '1d',
    '1000ks': '1d',
    '30day': '30d',
    '40g_30d': '30d',
    '50gb-30000': '30d',
    'test': '3m',
}

# Auto-Hybrid Speed Enforcement:
#   PROFILE mode (default)  - every profile carries its plan rate-limit, so
#                             RouterOS shapes each user with its own dynamic
#                             queue (exact plan speeds, cheap at low load).
#   PCQ mode (peak, auto)   - router-side monitor clears profile rate-limits and
#                             enables ONE static PCQ queue (pcq-rate=0 => equal
#                             share of the real WAN) to save CPU under load.
# PROFILE_RATE_DEFAULT only seeds profiles whose rate-limit is currently EMPTY
# (cleared by the v1.10.7 all-PCQ fix). The router persists each profile's true
# speed in a [RATE:x] comment tag from then on (app edits included).
PROFILE_RATE_DEFAULT = {
    '10.200.0.2': '20M/20M',   # YadanarTun  (plans 10M-20M, common 20M)
    '10.200.0.4': '20M/20M',   # KyawGyi     (plans 20M-50M, common 20M)
    '10.200.0.3': '10M/10M',   # AYeikSitt1  (plans 10M)
    '10.200.0.5': '20M/20M',   # AYeikSitt2  (default)
}
WAN_BPS = 1000000000  # real WAN bandwidth (bps): PCQ aggregate ceiling + utilization math

# Startup self-heal: runs once at boot, no VPS needed
STARTUP_HEAL = r"""# V3 Startup Self-Heal
:log info "V3: Startup self-heal running..."
__SVC_BLOCK__
# Kick ghost sessions for disabled/expired users
:foreach a in=[/ip hotspot active find] do={
  :local aU [/ip hotspot active get $a user];
  :if ($aU != "admin" and $aU != "default-trial") do={
    :local uL [/ip hotspot user find name=$aU];
    :if ([:len $uL] = 0) do={
      :do { /ip hotspot active remove $a } on-error={};
    } else={
      :local uO [:pick $uL 0];
      :local isDis [/ip hotspot user get $uO disabled];
      :local c [/ip hotspot user get $uO comment];
      :if ($isDis = true or [:find $c "[EXHAUSTED:"] >= 0 or [:find $c "[EXPIRED:"] >= 0) do={
        :do { /ip hotspot active remove $a; /ip hotspot cookie remove [find user=$aU] } on-error={};
      };
    };
  };
};

# Restore remaining quota for enabled users
:foreach u in=[/ip hotspot user find disabled=no] do={
  :local uN [/ip hotspot user get $u name];
  :if ($uN != "admin" and $uN != "default-trial") do={
    :local c [/ip hotspot user get $u comment];
    :local origLim 0;
    :local lIdx [:find $c "[ORIG-LIMIT:"];
    :if ($lIdx >= 0) do={
      :local lR [:pick $c ($lIdx + 12) [:len $c]];
      :local lE [:find $lR "]"];
      :if ($lE >= 0) do={ :set lR [:pick $lR 0 $lE] };
      :set origLim [:tonum $lR];
    };
    :if ($origLim > 0) do={
      :local usedBytes 0;
      :local uIdx [:find $c "[USED:"];
      :if ($uIdx >= 0) do={
        :local uR [:pick $c ($uIdx + 6) [:len $c]];
        :local uE [:find $uR "]"];
        :if ($uE >= 0) do={ :set uR [:pick $uR 0 $uE] };
        :set usedBytes [:tonum $uR];
      };
      :local remBytes ($origLim - $usedBytes);
      :if ($remBytes <= 0) do={
        /ip hotspot user set $u disabled=yes limit-bytes-total=1 limit-uptime=1s;
      } else={
        /ip hotspot user set $u limit-bytes-total=$remBytes;
        /ip hotspot user reset-counters $u;
        :local bIdx [:find $c "[BASE:"];
        :if ($bIdx >= 0) do={
          :local eI [:find $c "]" $bIdx];
          :local px [:pick $c 0 $bIdx];
          :local sx "";
          :if ($eI >= 0) do={ :set sx [:pick $c ($eI+1) [:len $c]] };
          :set c ($px . "[BASE:" . [:tostr $usedBytes] . "]" . $sx);
        } else={
          :set c ($c . " [BASE:" . [:tostr $usedBytes] . "]");
        };
        /ip hotspot user set $u comment=$c;
      };
    };
  };
};
# Clean per-user schedulers
:local keep {"hs-quota-saver";"hs-continuous-expire-monitor";"v3-startup-heal";"cloud-heartbeat";"pcq-mode-switch"};
:foreach sc in=[/system scheduler find] do={
  :local sN [/system scheduler get $sc name];
  :local isSys false;
  :foreach k in=$keep do={ :if ($sN = $k) do={ :set isSys true } };
  :if (!$isSys) do={ :do { /system scheduler remove $sc } on-error={} };
};
:log info "V3: Startup self-heal complete."
"""

# Auto-Hybrid Speed Enforcement monitor (runs every 1m, 100% router-side).
# PROFILE mode (default): per-profile rate-limits are the truth; dynamic
#   per-user queues give every user exactly their plan speed.
# PCQ mode (peak): profile rate-limits cleared, ONE static PCQ queue
#   hotspot-pcq (pcq-rate=0 = equal share of the real WAN) enabled instead.
# Each profile's configured speed is persisted in a [RATE:x] comment tag
# (app edits included), so the restore is always accurate and reboot-safe.
PCQ_MODE_SWITCH = r"""# V3 Auto-Hybrid Speed Enforcement: PROFILE mode <-> PCQ mode
:global pcqMode; :if ([:typeof $pcqMode] != "str") do={ :set pcqMode "profile" };
:global pcqHigh; :if ([:typeof $pcqHigh] = "nil") do={ :set pcqHigh 0 } else={ :set pcqHigh [:tonum $pcqHigh] };
:if ([:typeof $pcqHigh] != "num") do={ :set pcqHigh 0 };
:global pcqLow; :if ([:typeof $pcqLow] = "nil") do={ :set pcqLow 0 } else={ :set pcqLow [:tonum $pcqLow] };
:if ([:typeof $pcqLow] != "num") do={ :set pcqLow 0 };
:global pcqWanBps; :if ([:typeof $pcqWanBps] = "nil") do={ :set pcqWanBps 1000000000 } else={ :set pcqWanBps [:tonum $pcqWanBps] };
:if ([:typeof $pcqWanBps] != "num" or $pcqWanBps <= 0) do={ :set pcqWanBps 1000000000 };

:local enterUtil 85;
:local enterUsers 100;
:local exitUtil 60;
:local exitUsers 80;
:local needHigh 3;
:local needLow 10;

# --- WAN interface: first default route's gateway interface ---
:local wanIf "";
:foreach r in=[/ip route find dst-address=0.0.0.0/0] do={
  :if ([:len $wanIf] = 0) do={
    :local st "";
    :do { :set st [/ip route get $r gateway-status] } on-error={};
    :local sp [:find $st " "];
    :if ([:typeof $sp] = "num") do={
      :if ($sp > 0) do={ :set wanIf [:pick $st 0 $sp] };
    } else={
      :if ([:len $st] > 0) do={ :set wanIf $st };
    };
  };
};

# --- WAN utilization (best-effort; stays 0 if measurement unavailable) ---
:local utilPct 0;
:if ([:len $wanIf] > 0) do={
  :local rx 0; :local tx 0;
  :do {
    :foreach m in=[/interface monitor-traffic $wanIf once] do={
      :do { :set rx [:tonum ($m->"rx-bits-per-second")] } on-error={};
      :do { :set tx [:tonum ($m->"tx-bits-per-second")] } on-error={};
    };
  } on-error={};
  :local peak $rx;
  :if ($tx > $peak) do={ :set peak $tx };
  :if ($pcqWanBps > 0) do={ :set utilPct (($peak * 100) / $pcqWanBps) };
};

# --- active hotspot users (admin/trial excluded) ---
:local act 0;
:foreach a in=[/ip hotspot active find] do={
  :local aU "";
  :do { :set aU [/ip hotspot active get $a user] } on-error={};
  :if ($aU != "admin" and $aU != "default-trial") do={ :set act ($act + 1) };
};

# --- persist every configured speed as a [RATE:x] comment tag (always) ---
:foreach p in=[/ip hotspot user profile find] do={
  :local rl "";
  :do { :set rl [/ip hotspot user profile get $p rate-limit] } on-error={};
  :if ([:len $rl] > 0) do={
    :local cm "";
    :do { :set cm [/ip hotspot user profile get $p comment] } on-error={};
    :if ([:typeof [:find $cm "[RATE:"]] = "nil") do={
      :local sep " ";
      :if ([:len $cm] = 0) do={ :set sep "" };
      :do { /ip hotspot user profile set $p comment=($cm . $sep . "[RATE:" . $rl . "]") } on-error={};
    };
  };
};
:if ($pcqMode = "profile") do={
  # --- PROFILE mode: self-heal rates from tags (reboot recovery), keep the
  #     PCQ aggregate OFF, then evaluate load ---
  :foreach p in=[/ip hotspot user profile find] do={
    :local rl "";
    :do { :set rl [/ip hotspot user profile get $p rate-limit] } on-error={};
    :if ([:len $rl] = 0) do={
      :local cm "";
      :do { :set cm [/ip hotspot user profile get $p comment] } on-error={};
      :local ti [:find $cm "[RATE:"];
      :if ([:typeof $ti] = "num") do={
        :local r [:pick $cm ($ti + 6) [:len $cm]];
        :local e [:find $r "]"];
        :if ([:typeof $e] = "num") do={
          :set r [:pick $r 0 $e];
          :do { /ip hotspot user profile set $p rate-limit=$r } on-error={};
        };
      };
    };
  };
  :do { /queue simple set [find name="hotspot-pcq"] disabled=yes } on-error={};

  :if ($utilPct >= $enterUtil or $act >= $enterUsers) do={
    :set pcqHigh ($pcqHigh + 1);
    :if ($pcqHigh >= $needHigh) do={
      # === ENTER PCQ MODE ===
      :foreach p in=[/ip hotspot user profile find] do={
        :do { /ip hotspot user profile set $p rate-limit="" } on-error={};
      };
      :foreach q in=[/queue simple find dynamic=yes] do={
        :local qn "";
        :do { :set qn [/queue simple get $q name] } on-error={};
        :if ([:typeof [:find $qn "<hotspot-"]] = "num") do={
          :do { /queue simple remove $q } on-error={};
        };
      };
      :do { /queue simple set [find name="hotspot-pcq"] disabled=no } on-error={};
      :set pcqMode "pcq";
      :set pcqHigh 0;
      :set pcqLow 0;
      :log warning ("V3: PCQ MODE ON (wan=" . $wanIf . " util=" . $utilPct . "% users=" . $act . ")");
    };
  } else={ :set pcqHigh 0 };
} else={
  # --- PCQ mode ---
  :if ($utilPct <= $exitUtil and $act <= $exitUsers) do={
    :set pcqLow ($pcqLow + 1);
    :if ($pcqLow >= $needLow) do={
      # === EXIT PCQ MODE: restore every profile's configured speed ===
      :foreach p in=[/ip hotspot user profile find] do={
        :local cm "";
        :do { :set cm [/ip hotspot user profile get $p comment] } on-error={};
        :local ti [:find $cm "[RATE:"];
        :if ([:typeof $ti] = "num") do={
          :local r [:pick $cm ($ti + 6) [:len $cm]];
          :local e [:find $r "]"];
          :if ([:typeof $e] = "num") do={
            :set r [:pick $r 0 $e];
            :do { /ip hotspot user profile set $p rate-limit=$r } on-error={};
          };
        };
      };
      :do { /queue simple set [find name="hotspot-pcq"] disabled=yes } on-error={};
      :set pcqMode "profile";
      :set pcqHigh 0;
      :set pcqLow 0;
      :log info ("V3: PROFILE MODE RESTORED (util=" . $utilPct . "% users=" . $act . ")");
    };
  } else={ :set pcqLow 0 };
};
"""


def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

def load_router_creds(ip):
    """Credential candidates for one router from the VPS DB: the VPS-only cloud
    service account FIRST (password-change-proof), then the stored admin login.
    Falls back to the legacy constants when the DB is unavailable."""
    cands = []
    svc_pass = ""
    try:
        import cloud_database as _db
        conn = _db.get_db(); cur = conn.cursor()
        cur.execute("SELECT svc_user, svc_pass, api_user, api_pass FROM routers WHERE wg_ip=?", (ip,))
        row = cur.fetchone(); conn.close()
        if row:
            if (row[1] or "").strip():
                svc_pass = row[1].strip()
                cands.append(((row[0] or "").strip() or 'cloudsvc', svc_pass))
            cands.append(((row[2] or "").strip() or 'admin', (row[3] or "").strip() or PASS))
    except Exception:
        pass
    cands.append((USER, PASS))
    seen = set(); out = []
    for u, p in cands:
        if (u, p) not in seen:
            seen.add((u, p)); out.append((u, p))
    return out, svc_pass


def deploy_to_router(ip, port=8728):
    print(f"Deploying V3 to {ip}:{port}...")
    c = RouterOSClient(ip, port, timeout=15)
    c.connect()
    creds, svc_pass = load_router_creds(ip)
    used = None
    for u, p in creds:
        try:
            if c.login(u, p):
                used = (u, p); break
        except Exception:
            continue
    if used is None:
        print(f"  AUTH FAILED"); return False
    if used[0] != 'cloudsvc' and svc_pass:
        # Legacy admin login still works -> (re)assert the VPS-only cloud
        # service account so future customer password changes can't lock out.
        try:
            from cloud_config_enforcer import assert_svc_user
            assert_svc_user(c, svc_pass)
        except Exception:
            pass

    def set_script(name, source, comment=""):
        ex = [parse_re(x) for x in c.talk(['/system/script/print', f'?name={name}', '=.proplist=.id']) if x[0]=='!re']
        if ex and ex[0].get('.id'):
            c.talk(['/system/script/set', f'=.id={ex[0][".id"]}', f'=source={source}', f'=comment={comment}'])
        else:
            c.talk(['/system/script/add', f'=name={name}', f'=source={source}', f'=comment={comment}'])
        print(f"  Script: {name}")

    def set_sched(name, interval, event, comment=""):
        for attempt in range(2):
            ex = [parse_re(x) for x in c.talk(['/system/scheduler/print', f'?name={name}', '=.proplist=.id']) if x[0]=='!re']
            if ex and ex[0].get('.id'):
                c.talk(['/system/scheduler/set', f'=.id={ex[0][".id"]}', f'=interval={interval}', f'=on-event={event}'])
            else:
                c.talk(['/system/scheduler/add', f'=name={name}', f'=interval={interval}', f'=on-event={event}', '=start-time=startup', f'=comment={comment}'])
            # Verify the change actually landed; retry once if not
            chk = [parse_re(x) for x in c.talk(['/system/scheduler/print', f'?name={name}', '=.proplist=interval']) if x[0]=='!re']
            if chk and chk[0].get('interval') == interval:
                break
            if attempt == 0:
                c.talk(['/system/scheduler/remove', f'?name={name}'])  # clear dup if add raced
        print(f"  Scheduler: {name} = {interval} (verified)")

    # 1. Deploy startup self-heal (+ VPS-only service account assertion so the
    #    cloud survives customer password changes and account deletion)
    try:
        from cloud_config_enforcer import assert_svc_user_rsc
        svc_block = assert_svc_user_rsc(svc_pass) if svc_pass else ''
    except Exception:
        svc_block = ''
    set_script('v3-startup-heal', STARTUP_HEAL.replace('__SVC_BLOCK__', svc_block), 'V3 Boot Self-Heal')
    set_sched('v3-startup-heal', '0s', '/system script run v3-startup-heal', 'V3 boot heal (once)')

    # 2. Deploy 4 core V3 scripts
    try:
        import cloud_config_enforcer as cce
        set_script('voucher-activate', cce.VOUCHER_ACTIVATE_SOURCE, 'V3 Airtight Activation')
        set_script('hs-quota-save', cce.HS_QUOTA_SAVE_SOURCE, 'V3 Lightweight Quota Saver')
        set_script('voucher-expire-check', cce.VOUCHER_EXPIRE_CHECK_SOURCE, 'V3 Expire GC')
        set_script('hs-on-logout', cce.HS_ON_LOGOUT_SOURCE, 'V3 On Logout Saver')
    except Exception as e:
        print(f"  WARN: cloud_config_enforcer import failed: {e}")

    # 3. Ensure schedulers
    set_sched('hs-quota-saver', '60s', '/system script run hs-quota-save', 'V3 quota saver')
    set_sched('hs-continuous-expire-monitor', '3m', '/system script run voucher-expire-check', 'V3 expire monitor')

    # 4. Fix profile hooks + per-profile speed limits (PROFILE MODE is the default).
    #    Every profile carries its plan rate-limit, so RouterOS shapes each user
    #    with its own dynamic queue (exact plan speeds). The authoritative speed
    #    is ALSO persisted as a [RATE:x] comment tag so the pcq-mode-switch
    #    monitor can restore it after every PCQ-mode cycle (and after reboots).
    on_login = ':global hsUser $user; :do { /system script run voucher-activate } on-error={}'
    on_logout = ':do { /system script run hs-on-logout } on-error={}'
    default_rate = PROFILE_RATE_DEFAULT.get(ip, '20M/20M')
    for p in [parse_re(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,rate-limit,comment,session-timeout']) if x[0]=='!re']:
        if not p.get('.id'):
            continue
        prof_name = p.get('name', '')
        lim_up = PROFILE_SESSION_TIMEOUT.get(prof_name.strip().lower(), '')
        rl = (p.get('rate-limit') or '').strip()
        cm = (p.get('comment') or '')
        # Authoritative speed: live rate-limit > [RATE:] tag > per-router default
        if not rl:
            ti = cm.find('[RATE:')
            if ti >= 0:
                rl = cm[ti + 6:].split(']')[0].strip()
            else:
                rl = default_rate
        # Persist the authoritative speed as a comment tag (idempotent)
        if '[RATE:' not in cm:
            cm = (cm + ' ' if cm else '') + f'[RATE:{rl}]'
            c.talk(['/ip/hotspot/user/profile/set', f'=.id={p[".id"]}', f'=comment={cm}'])
        args = ['/ip/hotspot/user/profile/set', f'=.id={p[".id"]}',
            '=shared-users=1', '=add-mac-cookie=yes', '=mac-cookie-timeout=30d',
            '=keepalive-timeout=2m', '=idle-timeout=3m',
            f'=rate-limit={rl}',  # PROFILE MODE: per-profile speed is the truth
            f'=on-login={on_login}', f'=on-logout={on_logout}']
        # NEVER override an existing session-timeout: customer-created plans own
        # their validity. The known-plan map only FILLS empty ones.
        cur_st = (p.get('session-timeout') or '').strip()
        set_st = bool(lim_up) and (not cur_st or cur_st == 'none' or cur_st == '0s')
        if set_st:
            args.append(f'=session-timeout={lim_up}')
        c.talk(args)
        if set_st:
            print(f"  Profile {prof_name}: session-timeout={lim_up} rate={rl}")
        else:
            print(f"  Profile {prof_name}: rate={rl} (validity kept: {cur_st or 'unlimited'})")

    # 5. PCQ peak-mode assets (installed DISABLED).
    #    pcq-rate=0 => each active IP gets an EQUAL SHARE of the real WAN
    #    (never exceeding the WAN ceiling). The pcq-mode-switch monitor (1m)
    #    enables this queue + clears profile rate-limits only under high load.
    pcq_max = f'{WAN_BPS}/{WAN_BPS}'
    try:
        # Discover the hotspot subnet from the hotspot-bridge interface address
        target = ''
        for a in [parse_re(x) for x in c.talk(['/ip/address/print', '?interface=hotspot-bridge', '=.proplist=address']) if x[0]=='!re']:
            addr = a.get('address', '')
            if '/' in addr:
                base, pfx = addr.split('/')
                pfx = int(pfx)
                parts = list(map(int, base.split('.')))
                mask = (0xFFFFFFFF << (32 - pfx)) & 0xFFFFFFFF
                net = ((parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]) & mask
                target = f"{'.'.join(str((net >> s) & 0xFF) for s in (24, 16, 8, 0))}/{pfx}"
                break
        if not target:
            target = '10.10.10.0/23'
        # PCQ queue types - equal share of the real WAN (pcq-rate=0)
        for tname, cls in [('pcq-hotspot-down', 'dst-address'),
                           ('pcq-hotspot-up', 'src-address')]:
            qex = [parse_re(x) for x in c.talk(['/queue/type/print', f'?name={tname}', '=.proplist=.id']) if x[0]=='!re']
            qargs = ['=kind=pcq', '=pcq-rate=0', f'=pcq-classifier={cls}',
                     '=pcq-limit=50', '=pcq-total-limit=2000', f'=name={tname}']
            if qex and qex[0].get('.id'):
                c.talk(['/queue/type/set', f'=.id={qex[0][".id"]}'] + qargs[1:])
            else:
                c.talk(['/queue/type/add'] + qargs)
        # ONE static simple queue over the whole hotspot subnet (installed DISABLED;
        # per-user dynamic queues from profile rate-limits are the PROFILE-mode truth)
        sex = [parse_re(x) for x in c.talk(['/queue/simple/print', '?name=hotspot-pcq', '=.proplist=.id']) if x[0]=='!re']
        sargs = ['=name=hotspot-pcq', f'=target={target}', f'=max-limit={pcq_max}',
                 '=queue=pcq-hotspot-up/pcq-hotspot-down', '=disabled=yes',
                 '=comment=V3 PCQ equal-share aggregate (auto peak mode)']
        if sex and sex[0].get('.id'):
            c.talk(['/queue/simple/set', f'=.id={sex[0][".id"]}'] + sargs[1:])
        else:
            c.talk(['/queue/simple/add'] + sargs)
        print(f"  PCQ aggregate hotspot-pcq: target={target} max={pcq_max} (installed DISABLED)")
        # WAN capacity for the monitor's utilization math (bps)
        env = [parse_re(x) for x in c.talk(['/system/script/environment/print', '?name=pcqWanBps', '=.proplist=.id']) if x[0]=='!re']
        if env and env[0].get('.id'):
            c.talk(['/system/script/environment/set', f'=.id={env[0][".id"]}', f'=value={WAN_BPS}'])
        else:
            c.talk(['/system/script/environment/add', '=name=pcqWanBps', f'=value={WAN_BPS}'])
    except Exception as qe:
        print(f"  WARN: PCQ setup failed: {qe}")
    # 6. Router-side mode switch: auto PROFILE <-> PCQ (works with no VPS/app)
    set_script('pcq-mode-switch', PCQ_MODE_SWITCH, 'V3 auto Profile<->PCQ')
    set_sched('pcq-mode-switch', '1m', '/system script run pcq-mode-switch', 'V3 auto PCQ<->Profile')
    print(f"  Profiles fixed. DONE: {ip}")
    c.close()
    return True

if __name__ == '__main__':
    if '--all' in sys.argv:
        targets = [('10.200.0.2',8728),('10.200.0.4',8728),('10.200.0.3',8728),('10.200.0.5',8728)]
        if db:
            conn = db.get_db(); cur = conn.cursor()
            cur.execute("SELECT wg_ip, api_port FROM routers")
            targets = [(r['wg_ip'], r['api_port']) for r in cur.fetchall()]
            conn.close()
        for ip, port in targets:
            try: deploy_to_router(ip, port)
            except Exception as e: print(f"  SKIP {ip}: {e}")
    elif len(sys.argv) > 1:
        deploy_to_router(sys.argv[1])
    else:
        print("Usage: python3 deploy_v3_to_router.py <ip> | --all")

