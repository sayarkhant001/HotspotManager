#!/usr/bin/env python3
"""Check RouterOS firmware upgrade status"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1'),('10.200.0.2','YadanarTun')]:
    try:
        c = RouterOSClient(ip, 8728, timeout=10)
        c.connect()
        c.login('admin','Khant1234@')
        r = c.talk(['/system/package/update/print'])
        for x in r:
            print(f"  {name}: {x}")
        # Also check channel
        r2 = c.talk(['/system/package/update/settings/print'])
        for x in r2:
            print(f"  {name} settings: {x}")
        c.close()
    except Exception as e:
        print(f"  {name}: ERROR {e}")
