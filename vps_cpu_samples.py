#!/usr/bin/env python3
"""Sample CPU load 3x (10s apart) + read the static hotspot-pcq queue counters
to confirm PCQ is shaping traffic, per router."""
import sys, json, time
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


c = RouterOSClient(IP, timeout=12)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

loads = []
for i in range(3):
    res = [parse(x) for x in c.talk(['/system/resource/print']) if x[0] == '!re']
    loads.append(res[0].get('cpu-load', '?'))
    if i < 2:
        time.sleep(10)
print(f'CPU load samples: {loads}  (avg={sum(int(x) for x in loads) / len(loads):.0f}%)')

# queue counters for the static queue
for r in c.talk(['/queue/simple/print', '?name=hotspot-pcq',
                 '=.proplist=name,rate,total-rate,bytes,packets,total-bytes,total-packets,max-limit,queue']):
    if r[0] == '!re':
        d = parse(r)
        print(f"STATIC QUEUE {d.get('name')}:")
        print(f"  queue={d.get('queue')} max-limit={d.get('max-limit')}")
        print(f"  rate={d.get('rate')} total-rate={d.get('total-rate')}")
        print(f"  bytes={d.get('bytes')} total-bytes={d.get('total-bytes')}")
        print(f"  packets={d.get('packets')} total-packets={d.get('total-packets')}")

# dynamic queues check
dyn = 0
for r in c.talk(['/queue/simple/print', '=.proplist=name,dynamic']):
    if r[0] == '!re':
        d = parse(r)
        if d.get('dynamic') == 'true':
            dyn += 1
print(f'dynamic queues: {dyn}')

# active count + cpu core irq
for r in c.talk(['/system/resource/cpu/print']):
    if r[0] == '!re':
        d = parse(r)
        print(f"  core {d.get('cpu')}: load={d.get('load')}% irq={d.get('irq')}%")
c.close()
print('DONE')