#!/usr/bin/env python3
"""Deep CPU spike diagnosis for KyawGyi"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

c = RouterOSClient('10.200.0.4', 8728, timeout=12)
c.connect()
c.login('admin','Khant1234@')

# 1. All schedulers
print("=== SCHEDULERS ===")
sc = c.talk(['/system/scheduler/print', '=.proplist=.id,name,interval,start-time,on-event,comment'])
for s in sc:
    if s[0] == '!re':
        d = {}
        for item in s[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('name','?')} | int={d.get('interval','?')} | start={d.get('start-time','?')} | ev={d.get('on-event','')[:50]}")

# 2. All scripts
print("\n=== SCRIPTS ===")
sc2 = c.talk(['/system/script/print', '=.proplist=.id,name,comment'])
for s in sc2:
    if s[0] == '!re':
        d = {}
        for item in s[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('name','?')} | {d.get('comment','')}")

# 3. Hotspot user profiles (check rate-limits/queues)
print("\n=== PROFILES ===")
pr = c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,rate-limit,on-login,on-logout'])
for p in pr:
    if p[0] == '!re':
        d = {}
        for item in p[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('name','?')} | rate={d.get('rate-limit','?')}")

# 4. Check CPU-heavy processes: queues
print("\n=== SIMPLE QUEUES ===")
q = c.talk(['/queue/simple/print', '=.proplist=.id,name,limit-at,target,comment'])
for x in q:
    if x[0] == '!re':
        d = {}
        for item in x[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('name','?')} | target={d.get('target','?')} | limit={d.get('limit-at','?')} | {d.get('comment','')[:40]}")

# 5. Firewall rules count
print("\n=== FIREWALL ===")
fw = c.talk(['/ip/firewall/filter/print', '=.proplist=.id'])
print(f"  Filter rules: {sum(1 for x in fw if x[0]=='!re')}")
fw2 = c.talk(['/ip/firewall/mangle/print', '=.proplist=.id'])
print(f"  Mangle rules: {sum(1 for x in fw2 if x[0]=='!re')}")

# 6. Check hotspot hosts (potential ARP floods)
h = c.talk(['/ip/hotspot/host/print', '=.proplist=.id'])
print(f"  Hotspot hosts: {sum(1 for x in h if x[0]=='!re')}")

c.close()
