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

# CPU-spike fix (PCQ): per-user cap applied via ONE static simple queue instead of
# one dynamic simple queue per logged-in user. Per router (matches each shop's
# common plan speed). Leave profiles' rate-limit EMPTY so RouterOS stops creating
# <hotspot-*> dynamic queues; PCQ divides the aggregate fairly per IP.
PCQ_PER_USER = {
    '10.200.0.2': '20M/20M',   # YadanarTun  (plans 10M-20M, common 20M)
    '10.200.0.4': '20M/20M',   # KyawGyi     (plans 20M-50M, common 20M)
    '10.200.0.3': '10M/10M',   # AYeikSitt1  (plans 10M)
    '10.200.0.5': '20M/20M',   # AYeikSitt2  (default)
}
PCQ_AGG_LIMIT = '1000M/1000M'  # aggregate ceiling (WAN); per-user governed by PCQ

# Startup self-heal: runs once at boot, no VPS needed
STARTUP_HEAL = r"""# V3 Startup Self-Heal
:log info "V3: Startup self-heal running..."
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
:local keep {"hs-quota-saver";"hs-continuous-expire-monitor";"v3-startup-heal";"cloud-heartbeat"};
:foreach sc in=[/system scheduler find] do={
  :local sN [/system scheduler get $sc name];
  :local isSys false;
  :foreach k in=$keep do={ :if ($sN = $k) do={ :set isSys true } };
  :if (!$isSys) do={ :do { /system scheduler remove $sc } on-error={} };
};
:log info "V3: Startup self-heal complete."
"""


def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

def deploy_to_router(ip, port=8728):
    print(f"Deploying V3 to {ip}:{port}...")
    c = RouterOSClient(ip, port, timeout=15)
    c.connect()
    if not c.login(USER, PASS):
        print(f"  AUTH FAILED"); return False

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

    # 1. Deploy startup self-heal
    set_script('v3-startup-heal', STARTUP_HEAL, 'V3 Boot Self-Heal')
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

    # 4. Fix profile hooks
    on_login = ':global hsUser $user; :do { /system script run voucher-activate } on-error={}'
    on_logout = ':do { /system script run hs-on-logout } on-error={}'
    for p in [parse_re(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name']) if x[0]=='!re']:
        if p.get('.id'):
            prof_name = p.get('name', '')
            lim_up = PROFILE_SESSION_TIMEOUT.get(prof_name.strip().lower(), '')
            args = ['/ip/hotspot/user/profile/set', f'=.id={p[".id"]}',
                '=shared-users=1', '=add-mac-cookie=yes', '=mac-cookie-timeout=30d',
                '=keepalive-timeout=2m', '=idle-timeout=3m',
                '=rate-limit=',  # EMPTY: stop per-user dynamic simple queues (CPU fix)
                f'=on-login={on_login}', f'=on-logout={on_logout}']
            if lim_up:
                args.append(f'=session-timeout={lim_up}')
            c.talk(args)
            if lim_up:
                print(f"  Profile {prof_name}: session-timeout={lim_up} (rate-limit cleared)")

    # 5. PCQ consolidation: ONE static queue instead of N per-user dynamic queues.
    #    This is the #1 CPU fix for 100-200 concurrent users on the L009.
    pcq_rate = PCQ_PER_USER.get(ip, '20M/20M')
    down_r, up_r = pcq_rate.split('/')
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
        # PCQ queue types (RouterOS v7 fields: pcq-rate per sub-queue, pcq-classifier)
        for tname, cls, rte in [('pcq-hotspot-down', 'dst-address', down_r),
                                ('pcq-hotspot-up', 'src-address', up_r)]:
            qex = [parse_re(x) for x in c.talk(['/queue/type/print', f'?name={tname}', '=.proplist=.id']) if x[0]=='!re']
            qargs = [f'=kind=pcq', f'=pcq-rate={rte}', f'=pcq-classifier={cls}',
                     '=pcq-limit=50', '=pcq-total-limit=2000', f'=name={tname}']
            if qex and qex[0].get('.id'):
                c.talk(['/queue/type/set', f'=.id={qex[0][".id"]}'] + qargs[1:])
            else:
                c.talk(['/queue/type/add'] + qargs)
        # Remove any stale per-user dynamic queues immediately
        removed = 0
        for q in [parse_re(x) for x in c.talk(['/queue/simple/print', '=.proplist=.id,name,dynamic']) if x[0]=='!re']:
            if q.get('dynamic') == 'true' and str(q.get('name', '')).startswith('<hotspot-'):
                c.talk(['/queue/simple/remove', f'=.id={q[".id"]}'])
                removed += 1
        if removed:
            print(f"  Removed {removed} per-user dynamic queues (CPU relief).")
        # ONE static simple queue over the whole hotspot subnet
        sex = [parse_re(x) for x in c.talk(['/queue/simple/print', '?name=hotspot-pcq', '=.proplist=.id']) if x[0]=='!re']
        sargs = ['=name=hotspot-pcq', f'=target={target}', f'=max-limit={PCQ_AGG_LIMIT}',
                 '=queue=pcq-hotspot-up/pcq-hotspot-down',
                 '=comment=V3 PCQ: per-user cap ' + pcq_rate + ' (CPU fix)']
        if sex and sex[0].get('.id'):
            c.talk(['/queue/simple/set', f'=.id={sex[0][".id"]}'] + sargs[1:])
        else:
            c.talk(['/queue/simple/add'] + sargs)
        print(f"  PCQ queue hotspot-pcq: target={target} per-user={pcq_rate} (1 queue, not per-user)")
    except Exception as qe:
        print(f"  WARN: PCQ setup failed: {qe}")
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

