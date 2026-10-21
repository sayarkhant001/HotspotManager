#!/usr/bin/env python3
"""Check router board model"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    try:
        c = RouterOSClient(ip, 8728, timeout=8)
        c.connect()
        c.login('admin','Khant1234@')
        r = c.talk(['/system/routerboard/print'])
        for x in r:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                print(f"{name}: {d.get('board-name','?')} / {d.get('model','?')} / FW {d.get('current-firmware','?')}")
        # Also get CPU type from resource
        r2 = c.talk(['/system/resource/print'])
        for x in r2:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                print(f"  CPU: {d.get('cpu','?')} | {d.get('cpu-count','?')} cores | Mem {int(d.get('total-memory','0'))//1024//1024}MB")
        c.close()
    except Exception as e:
        print(f"{name}: ERROR {e}")
