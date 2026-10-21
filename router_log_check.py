#!/usr/bin/env python3
"""Check router log for CPU/spam + hotspot host table"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

target = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.4'
c = RouterOSClient(target, 8728, timeout=15)
c.connect()
c.login('admin','Khant1234@')

print(f"=== {target} ===")

# Recent log entries
print("\n-- RECENT LOG (last 30) --")
try:
    lg = c.talk(['/log/print', '=.proplist=.id,time,topics,message', '?topics~hotspot'])
    seen = 0
    for x in lg:
        if x[0] != '!re': continue
        d = {}
        for item in x[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('time','?'):10s} {d.get('message','')[:100]}")
        seen += 1
        if seen >= 20: break
except Exception as e:
    print(f"  err {e}")

# Hotspot hosts count (potential DHCP/flood issue)
print("\n-- HOTSPOT HOSTS --")
h = c.talk(['/ip/hotspot/host/print', '=.proplist=.id,address,to-address,bypassed'])
hosts = [x for x in h if x[0] == '!re']
print(f"  Count: {len(hosts)}")

# Active sessions detail
print("\n-- ACTIVE SESSIONS --")
a = c.talk(['/ip/hotspot/active/print', '=.proplist=.id,user,address,uptime'])
for x in a[:15]:
    if x[0] != '!re': continue
    d = {}
    for item in x[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    print(f"  {d.get('user','?'):15s} {d.get('address','?'):15s} up={d.get('uptime','?')}")

# DHCP leases
print("\n-- DHCP LEASES --")
d = c.talk(['/ip/dhcp-server/lease/print', '=.proplist=.id,address,status'])
leases = [x for x in d if x[0] == '!re']
bound = sum(1 for x in leases if 'bound' in str(x))
print(f"  Total: {len(leases)} (bound-ish: {bound})")

c.close()
