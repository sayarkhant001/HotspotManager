#!/usr/bin/env python3
"""Raw process list from router - find CPU consumers"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

target = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.4'
c = RouterOSClient(target, 8728, timeout=20)
c.connect()
c.login('admin','Khant1234@')

print(f"=== PROCESSES on {target} ===")
# Raw talk to see exact response format
res = c.talk(['/system/process/print'])
print(f"Got {len(res)} sentences")
for r in res:
    if r[0] == '!re':
        d = {}
        for item in r[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        cpu = d.get('cpu','0')
        nm = d.get('name','?')
        args = d.get('args','')[:50]
        try:
            cpu_int = int(cpu)
        except:
            cpu_int = 0
        if cpu_int > 0:
            print(f"  CPU={cpu_int:4d}% {nm:20s} {args}")

c.close()
