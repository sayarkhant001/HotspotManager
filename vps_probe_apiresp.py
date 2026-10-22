#!/usr/bin/env python3
"""Probe exact RouterOS API responses for: profile rate-limit clear, PCQ queue
type config, and static simple queue add. Prints !trap / !re replies verbatim."""
import sys, json
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'


def parse(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


def show(res, label):
    print(f"\n-- {label} --")
    for r in res:
        print('   ', json.dumps(r))


c = RouterOSClient(IP, timeout=10)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

# 1. Read a profile .id
prof = [parse(x) for x in c.talk(['/ip/hotspot/user/profile/print', '?name=2GB', '=.proplist=.id,rate-limit']) if x[0] == '!re']
show(c.talk(['/ip/hotspot/user/profile/print', '?name=2GB', '=.proplist=.id,rate-limit']), 'read 2GB profile')
pid = prof[0].get('.id') if prof else None
print(f"2GB profile id={pid} rate-limit={prof[0].get('rate-limit') if prof else '?'}")

# 2. Try clearing rate-limit with empty value, print reply
if pid:
    res = c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', '=rate-limit='])
    show(res, 'set rate-limit= (empty)')
    show(c.talk(['/ip/hotspot/user/profile/print', f'=.id={pid}', '=.proplist=rate-limit']), 'read back rate-limit')
    # restore
    c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', '=rate-limit=20M/20M'])

# 3. Try queue type set with pcq fields, print reply
qid = [parse(x) for x in c.talk(['/queue/type/print', '?name=pcq-hotspot-down', '=.proplist=.id']) if x[0] == '!re']
if qid:
    q = qid[0].get('.id')
    show(c.talk(['/queue/type/set', f'=.id={q}', '=kind=pcq', '=pcq-rate=20M',
                 '=pcq-classifier=dst-address', '=pcq-limit=50', '=pcq-total-limit=2000']),
         'queue/type/set pcq-hotspot-down')
    show(c.talk(['/queue/type/print', f'=.id={q}', '=.proplist=name,kind,pcq-rate,pcq-classifier,pcq-limit,pcq-total-limit']),
         'read back queue type')

# 4. Try adding the static simple queue, print reply
show(c.talk(['/queue/simple/add', '=name=hotspot-pcq-test', '=target=10.10.10.0/23',
             '=max-limit=1000M/1000M', '=queue=pcq-hotspot-up/pcq-hotspot-down',
             '=comment=probe']), 'queue/simple/add hotspot-pcq-test')
show(c.talk(['/queue/simple/print', '?name=hotspot-pcq-test', '=.proplist=name,target,max-limit,queue,comment']),
     'read back simple queue')
# cleanup test queue
for r in c.talk(['/queue/simple/print', '?name=hotspot-pcq-test', '=.proplist=.id']):
    if r[0] == '!re':
        d = parse(r)
        if d.get('.id'):
            c.talk(['/queue/simple/remove', f'=.id={d[".id"]}'])

c.close()
print('\nDONE')