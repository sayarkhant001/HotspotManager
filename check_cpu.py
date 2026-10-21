#!/usr/bin/env python3
"""Check router CPU load + active session count"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    print(f"\n=== {name} ({ip}) ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=10)
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
                print(f"  CPU Load: {d.get('cpu-load','?')}%  Uptime: {d.get('uptime','?')}  Free Mem: {d.get('free-memory','?')}  Version: {d.get('version','?')}")
        a = c.talk(['/ip/hotspot/active/print', '=.proplist=.id'])
        print(f"  Active sessions: {sum(1 for x in a if x[0]=='!re')}")
        u = c.talk(['/ip/hotspot/user/print', '=.proplist=.id'])
        print(f"  Total users: {sum(1 for x in u if x[0]=='!re')}")
        c.close()
    except Exception as e:
        print(f"  ERROR: {e}")
