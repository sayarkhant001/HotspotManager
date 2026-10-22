#!/usr/bin/env python3
"""Apply the PCQ CPU fix to one router with FULL response checking:
1. Clear rate-limit on all hotspot user profiles (kills per-user dynamic queues)
2. Ensure pcq-hotspot-down / pcq-hotspot-up exist and are configured
3. Remove stale dynamic <hotspot-*> simple queues
4. Create the ONE static simple queue hotspot-pcq over 10.10.10.0/23
Prints any !trap so nothing fails silently."""
import sys, json
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


def ok(res, label):
    for r in res:
        if r[0] == '!trap':
            print(f'  !! {label}: TRAP -> {json.dumps(r)}')
            return False
    print(f'  OK {label}')
    return True


c = RouterOSClient(IP, timeout=12)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

# 1. Clear rate-limit on all profiles
profiles = [parse(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,rate-limit']) if x[0] == '!re']
print(f'--- clearing rate-limit on {len(profiles)} profiles ---')
for p in profiles:
    if p.get('rate-limit'):
        ok(c.talk(['/ip/hotspot/user/profile/set', f'=.id={p[".id"]}', '=rate-limit=']),
           f"clear rate-limit on {p.get('name')}")

# 2. Ensure PCQ queue types exist and are configured
print('--- PCQ queue types ---')
for tname, cls, rte in [('pcq-hotspot-down', 'dst-address', DOWN_R),
                        ('pcq-hotspot-up', 'src-address', UP_R)]:
    qex = [parse(x) for x in c.talk(['/queue/type/print', f'?name={tname}', '=.proplist=.id']) if x[0] == '!re']
    if qex and qex[0].get('.id'):
        ok(c.talk(['/queue/type/set', f'=.id={qex[0][".id"]}', '=kind=pcq',
                   f'=pcq-rate={rte}', f'=pcq-classifier={cls}',
                   '=pcq-limit=50', '=pcq-total-limit=2000']), f'set {tname}')
    else:
        ok(c.talk(['/queue/type/add', '=kind=pcq', f'=pcq-rate={rte}',
                   f'=pcq-classifier={cls}', '=pcq-limit=50', '=pcq-total-limit=2000',
                   f'=name={tname}']), f'add {tname}')

# 3. Remove stale dynamic per-user queues
print('--- removing dynamic <hotspot-*> queues ---')
removed = 0
for q in [parse(x) for x in c.talk(['/queue/simple/print', '=.proplist=.id,name,dynamic']) if x[0] == '!re']:
    if q.get('dynamic') == 'true' and str(q.get('name', '')).startswith('<hotspot-'):
        c.talk(['/queue/simple/remove', f'=.id={q[".id"]}'])
        removed += 1
print(f'  removed {removed} dynamic queues')

# 4. Create ONE static simple queue over the whole hotspot subnet
print('--- static hotspot-pcq queue ---')
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

# Verify final state
print('\n--- FINAL STATE ---')
for r in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,rate-limit']):
    if r[0] == '!re':
        d = parse(r)
        print(f'  profile {d.get("name")}: rate-limit={d.get("rate-limit") or "(empty)"}')
for r in c.talk(['/queue/simple/print', '=.proplist=name,target,queue,dynamic']):
    if r[0] == '!re':
        d = parse(r)
        if d.get('name') == 'hotspot-pcq' or d.get('dynamic') == 'true':
            print(f"  queue {d.get('name')} target={d.get('target')} queue={d.get('queue')} dyn={d.get('dynamic')}")
for r in c.talk(['/queue/type/print', '=.proplist=name,kind,pcq-rate,pcq-classifier']):
    if r[0] == '!re':
        d = parse(r)
        if 'pcq-hotspot' in d.get('name', ''):
            print(f"  type {d.get('name')}: kind={d.get('kind')} pcq-rate={d.get('pcq-rate')} classifier={d.get('pcq-classifier')}")

c.close()
print('\nDONE')