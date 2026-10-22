#!/usr/bin/env python3
"""Full CPU-spike diagnostic for one router: which process burns CPU, queue
counts (dynamic simple queues per user?), PCQ types present, FastTrack rules,
profile rate-limits, running sniffers, and current hotspot active count."""
import sys, json
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'


def parse_re(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


c = RouterOSClient(IP, timeout=10)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)


def print_rows(cmd, label):
    rows = [parse_re(x) for x in c.talk(cmd) if x[0] == '!re']
    print(f"\n--- {label} ({len(rows)} rows) ---")
    for r in rows[:12]:
        print('  ' + json.dumps(r))


# 1. CPU usage + process breakdown
res = [parse_re(x) for x in c.talk(['/system/resource/print']) if x[0] == '!re']
r = res[0] if res else {}
print(f"\n=== CPU: load={r.get('cpu-load')}%  uptime={r.get('uptime')}  arch={r.get('architecture-name')}  free-mem={r.get('free-memory')}")
for p in c.talk(['/system/resource/cpu/print']):
    if p[0] == '!re':
        d = parse_re(p)
        if d.get('load') and int(d.get('load', 0)) > 0:
            print(f"  CPU core {d.get('cpu')}: load={d.get('load')}% irq={d.get('irq')}% disk={d.get('disk')}%")

# 2. What consumes CPU (process histogram) — /tool profile isn't scriptable, so
#    use /system resource irq or check active management connections instead.
print("\n=== Active API/SSH/WinBox sessions (management traffic sources) ===")
for s in c.talk(['/user/active/print', '=.proplist=name,address,via,when']):
    if s[0] == '!re':
        d = parse_re(s)
        print(f"  {d.get('via')} {d.get('name')} from {d.get('address')} since {d.get('when')}")

# 3. Simple queues: total + dynamic (per-user hotspot queues)
sched_rows = [parse_re(x) for x in c.talk(['/queue/simple/print', '=.proplist=name,dynamic,target,queue']) if x[0] == '!re']
dyn = [q for q in sched_rows if q.get('dynamic') == 'true']
print(f"\n=== Simple Queues: total={len(sched_rows)} dynamic={len(dyn)} (per-user!) ===")
for q in dyn[:8]:
    print(f"  DYNAMIC {q.get('name')} target={q.get('target')} queue={q.get('queue')}")

# 4. Queue types (PCQ present?)
print("\n=== Queue Types (PCQ?) ===")
for q in c.talk(['/queue/type/print', '=.proplist=name,kind,classifier,rate']):
    if q[0] == '!re':
        d = parse_re(q)
        if d.get('kind') == 'pcq':
            print(f"  PCQ {d.get('name')} classifier={d.get('classifier')} rate={d.get('rate')}")

# 5. Firewall: is there a fasttrack rule?
print("\n=== Firewall Filter (fasttrack/established) ===")
fws = [parse_re(x) for x in c.talk(['/ip/firewall/filter/print', '=.proplist=action,chain,comment,disabled']) if x[0] == '!re']
for fw in fws:
    if fw.get('action') in ('fasttrack-connection', 'accept', 'drop', 'mark-connection') and fw.get('comment'):
        print(f"  {fw.get('action')} chain={fw.get('chain')} dis={fw.get('disabled')} # {fw.get('comment')}")
ft = [f for f in fws if f.get('action') == 'fasttrack-connection']
print(f"  fasttrack rules found: {len(ft)}")

# 6. Hotspot profiles rate-limit (per-user queue source!)
print("\n=== Hotspot User Profiles (rate-limit = per-user dynamic queue trigger) ===")
for p in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,rate-limit,session-timeout,shared-users']):
    if p[0] == '!re':
        d = parse_re(p)
        print(f"  {d.get('name')}: rate-limit={d.get('rate-limit') or '(none)'} session-timeout={d.get('session-timeout')} shared={d.get('shared-users')}")

# 7. Hotspot active users (real concurrent load)
act = [parse_re(x) for x in c.talk(['/ip/hotspot/active/print', '=.proplist=user']) if x[0] == '!re']
print(f"\n=== Hotspot ACTIVE sessions: {len(act)} ===")

# 8. Sniffer / torch running?
print("\n=== Packet Sniffer / Torch ===")
for s in c.talk(['/tool/sniffer/print']):
    if s[0] == '!re':
        d = parse_re(s)
        print(f"  sniffer running={d.get('running')} file={d.get('file-name')}")

c.close()
print("\nDONE")