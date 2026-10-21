#!/usr/bin/env python3
"""Tiny CPU-only probe (no heavy queries)"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    try:
        c = RouterOSClient(ip, 8728, timeout=6)
        c.connect()
        c.login('admin','Khant1234@')
        r = c.talk(['/system/resource/print'])
        for x in r:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                print(f"{name}: CPU={d.get('cpu-load','?')}% uptime={d.get('uptime','?')}")
        c.close()
        time.sleep(1)
    except Exception as e:
        print(f"{name}: ERROR {e}")
