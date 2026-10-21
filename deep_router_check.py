#!/usr/bin/env python3
"""Deep check of what's consuming router CPU (runs while enforcer stopped)"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

target = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.4'
c = RouterOSClient(target, 8728, timeout=15)
c.connect()
c.login('admin','Khant1234@')

print(f"=== {target} ===")

# 1. Running processes with CPU
print("\n-- PROCESSES (top by cpu) --")
try:
    pr = c.talk(['/system/process/print', '=.proplist=.id,name,cpu,args'])
    procs = []
    for p in pr:
        if p[0] != '!re': continue
        d = {}
        for item in p[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        try:
            procs.append((int(d.get('cpu','0')), d.get('name','?'), d.get('args','')[:60]))
        except: pass
    procs.sort(reverse=True)
    for cpu, nm, args in procs[:12]:
        print(f"  CPU={cpu:4d} {nm:25s} {args}")
except Exception as e:
    print(f"  err {e}")

# 2. Scheduler last-run times (find frequent fires)
print("\n-- SCHEDULERS (last-run) --")
sc = c.talk(['/system/scheduler/print', '=.proplist=.id,name,interval,last-started,next-run'])
for s in sc:
    if s[0] != '!re': continue
    d = {}
    for item in s[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    print(f"  {d.get('name','?'):35s} int={d.get('interval','?'):8s} last={d.get('last-started','never')}")

# 3. Simple queues count
print("\n-- SIMPLE QUEUES --")
q = c.talk(['/queue/simple/print', '=.proplist=.id'])
print(f"  Count: {sum(1 for x in q if x[0]=='!re')}")

# 4. Address lists
print("\n-- ADDRESS LISTS --")
al = c.talk(['/ip/firewall/address-list/print', '=.proplist=.id,list,address,comment'])
addr_lists = {}
for a in al:
    if a[0] != '!re': continue
    d = {}
    for item in a[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    lst = d.get('list','?')
    addr_lists[lst] = addr_lists.get(lst, 0) + 1
for lst, cnt in sorted(addr_lists.items(), key=lambda x: -x[1])[:10]:
    print(f"  {lst}: {cnt} entries")

# 5. Conntrack
print("\n-- CONNTRACK --")
ct = c.talk(['/ip/firewall/connection/print', '=.proplist=.id'])
print(f"  Active connections: {sum(1 for x in ct if x[0]=='!re')}")

c.close()
