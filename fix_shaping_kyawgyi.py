#!/usr/bin/env python3
"""
Fix KyawGyi: re-assert profile rate-limits so per-user simple queues return.
These queues are what limit each customer's bandwidth. Without them, a single
customer can open unlimited parallel connections -> conntrack + hotspot
accounting floods the CPU. This does NOT touch data/time cutoff
(limit-bytes-total / limit-uptime remain kernel-enforced on user objects).
"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

def pr(d):
    res = {}
    for item in d[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            res[k] = v
    return res

ip = '10.200.0.4'
c = RouterOSClient(ip, 8728, timeout=20)
c.connect()
c.login('admin','Khant1234@')

# 1. Show current profiles + their rate-limit
print('=== COnCURRENT PROFILES ===')
profiles = c.talk(['/ip/hotspot/user/profile/print','=.proplist=.id,name,rate-limit,limit-uptime,shared-users'])
prof_map = {}
for x in profiles:
    if x[0]=='!re':
        d = pr(x)
        prof_map[d.get('name')] = d
        print(f"  {d.get('name','?'):15s} rate={d.get('rate-limit','NONE')}")

# 2. Confirm queues are gone
q = c.talk(['/queue/simple/print','=.proplist=.id'])
print(f"\nSimple queues: {sum(1 for x in q if x[0]=='!re')}")

# 3. Re-assert rate limits (the values we know were there)
ratelimits = {
    'default': '50M/50M',
    '500Ks': '20M/20M',
    '1000Ks': '20M/20M',
    'test': '50M/50M',
    '30Day': '20M/20M',
    '40G_30D': '30M/30M',
}
count = 0
for name, rl in ratelimits.items():
    if name in prof_map:
        pid = prof_map[name].get('.id')
        if pid:
            # Only update if the rate-limit is missing/empty
            cur = prof_map[name].get('rate-limit','')
            if not cur or cur == '0/0':
                c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', f'=rate-limit={rl}'])
                print(f"  SET {name} rate-limit={rl}")
                count += 1
            else:
                print(f"  {name} already has rate-limit={cur}")
    else:
        print(f"  profile {name} not found")

c.close()
print(f"\nUpdated {count} profiles. Queues will re-form as sessions refresh.")