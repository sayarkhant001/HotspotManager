#!/usr/bin/env python3
# [DEPRECATED since auto-hybrid speed enforcement] Do NOT run this on live
# routers - it clears ALL profile rate-limits and forces the old all-PCQ mode.
# The auto-hybrid PROFILE<->PCQ mode switch now lives in deploy_v3_to_router.py
# (pcq-mode-switch router monitor).
"""Finalize PCQ on a router: remove dynamic queues, briefly kick active hotspot
sessions (they auto-reconnect via mac-cookie and, with rate-limit now empty, will
NOT recreate per-user dynamic queues), then add the static hotspot-pcq queue.
Prints every trap."""
import sys, json, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'
DOWN_R = sys.argv[2] if len(sys.argv) > 2 else '20M'
UP_R = sys.argv[3] if len(sys.argv) > 3 else '20M'


def parse(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


def ok(res, label, quiet=False):
    traps = [json.dumps(r) for r in res if r[0] == '!trap']
    if traps:
        print(f'  !! {label}: TRAP -> {traps}')
        return False
    if not quiet:
        print(f'  OK {label}')
    return True


c = RouterOSClient(IP, timeout=15)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

# 1. Ensure all profiles have rate-limit cleared (idempotent)
for p in [parse(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,rate-limit']) if x[0] == '!re']:
    if p.get('rate-limit'):
        ok(c.talk(['/ip/hotspot/user/profile/set', f'=.id={p[".id"]}', '=rate-limit=']),
           f"clear rate-limit on {p.get('name')}")

# 2. Ensure PCQ types configured
for tname, cls, rte in [('pcq-hotspot-down', 'dst-address', DOWN_R),
                        ('pcq-hotspot-up', 'src-address', UP_R)]:
    qex = [parse(x) for x in c.talk(['/queue/type/print', f'?name={tname}', '=.proplist=.id']) if x[0] == '!re']
    if qex and qex[0].get('.id'):
        ok(c.talk(['/queue/type/set', f'=.id={qex[0][".id"]}', '=kind=pcq', f'=pcq-rate={rte}',
                   f'=pcq-classifier={cls}', '=pcq-limit=50', '=pcq-total-limit=2000']), f'set {tname}', quiet=True)
    else:
        ok(c.talk(['/queue/type/add', '=kind=pcq', f'=pcq-rate={rte}', f'=pcq-classifier={cls}',
                   '=pcq-limit=50', '=pcq-total-limit=2000', f'=name={tname}']), f'add {tname}', quiet=True)
print('  PCQ types ensured')

# 3. Kick all active hotspot sessions (mac-cookie auto-login; no dynamic queues
#    will be recreated because profiles now have rate-limit empty)
act = [parse(x) for x in c.talk(['/ip/hotspot/active/print', '=.proplist=.id']) if x[0] == '!re']
print(f'  kicking {len(act)} active sessions...')
for a in act:
    if a.get('.id'):
        c.talk(['/ip/hotspot/active/remove', f'=.id={a[".id"]}'])
time.sleep(3)  # let reconnects settle

# 4. Remove any leftover dynamic queues
removed = 0
for q in [parse(x) for x in c.talk(['/queue/simple/print', '=.proplist=.id,name,dynamic']) if x[0] == '!re']:
    if q.get('dynamic') == 'true' and str(q.get('name', '')).startswith('<hotspot-'):
        c.talk(['/queue/simple/remove', f'=.id={q[".id"]}'])
        removed += 1
print(f'  removed {removed} leftover dynamic queues')

# 5. Add/update the static hotspot-pcq queue
sex = [parse(x) for x in c.talk(['/queue/simple/print', '?name=hotspot-pcq', '=.proplist=.id']) if x[0] == '!re']
target = '10.10.10.0/23'
if sex and sex[0].get('.id'):
    ok(c.talk(['/queue/simple/set', f'=.id={sex[0][".id"]}', f'=target={target}',
               '=max-limit=1000M/1000M', '=queue=pcq-hotspot-up/pcq-hotspot-down',
               '=comment=V3 PCQ per-user cap']), 'set hotspot-pcq')
else:
    ok(c.talk(['/queue/simple/add', '=name=hotspot-pcq', f'=target={target}',
               '=max-limit=1000M/1000M', '=queue=pcq-hotspot-up/pcq-hotspot-down',
               '=comment=V3 PCQ per-user cap']), 'add hotspot-pcq')

# FINAL STATE
print('\n--- FINAL STATE ---')
for r in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,rate-limit']):
    if r[0] == '!re':
        d = parse(r)
        print(f'  profile {d.get("name")}: rate-limit={d.get("rate-limit") or "(empty)"}')
dyn = st = 0
for r in c.talk(['/queue/simple/print', '=.proplist=name,target,queue,dynamic']):
    if r[0] == '!re':
        d = parse(r)
        if d.get('dynamic') == 'true':
            dyn += 1
        if d.get('name') == 'hotspot-pcq':
            st += 1
            print(f"  STATIC {d.get('name')} target={d.get('target')} queue={d.get('queue')}")
print(f'  simple queues: static=hotspot-pcq:{st} dynamic={dyn}')
act2 = [x for x in c.talk(['/ip/hotspot/active/print', '=.proplist=user']) if x[0] == '!re']
print(f'  active sessions now: {len(act2)}')
c.close()
print('\nDONE')