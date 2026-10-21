#!/usr/bin/env python3
"""Raw scheduler + script list to find CPU consumers"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

target = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.4'
c = RouterOSClient(target, 8728, timeout=20)
c.connect()
c.login('admin','Khant1234@')

print(f"=== SCHEDULERS on {target} (raw) ===")
res = c.talk(['/system/scheduler/print'])
for r in res:
    if r[0] in ('!re','!done'):
        if r[0] == '!done': continue
        d = {}
        for item in r[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        print(f"  {d.get('name','?'):35s} interval={d.get('interval','?'):8s} last={d.get('last-started','never'):18s} run-cnt={d.get('run-count','?')}")

print(f"\n=== SCRIPTS (sizes) on {target} ===")
res2 = c.talk(['/system/script/print'])
for r in res2:
    if r[0] == '!re':
        d = {}
        for item in r[1:]:
            if item.startswith('='):
                k,v = item[1:].split('=',1)
                d[k] = v
        src = d.get('source','')
        print(f"  {d.get('name','?'):30s} len={len(src):6d}")

c.close()
