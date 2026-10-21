#!/usr/bin/env python3
"""Check actual traffic throughput per router"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    try:
        c = RouterOSClient(ip, 8728, timeout=8)
        c.connect()
        c.login('admin','Khant1234@')
        r = c.talk(['/interface/print', '=.proplist=name,rx-byte,tx-byte,running'])
        # Find WAN
        for x in r:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                nm = d.get('name','')
                if 'ether1' in nm or 'wan' in nm.lower():
                    rx = int(d.get('rx-byte','0'))
                    tx = int(d.get('tx-byte','0'))
                    print(f"  {name}: WAN {nm} rx={rx/1024/1024:.0f}MB tx={tx/1024/1024:.0f}MB")
        c.close()
    except Exception as e:
        print(f"  {name}: ERROR {e}")
