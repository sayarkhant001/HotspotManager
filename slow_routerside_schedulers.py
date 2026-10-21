#!/usr/bin/env python3
"""Slow redundant router-side schedulers (robust version)"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

def pr(d):
    res = {}
    for item in d[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            res[k] = v
    return res

targets = [
    ('10.200.0.2','YadanarTun'),
    ('10.200.0.4','KyawGyi'),
    ('10.200.0.3','AYeikSitt1'),
]

for ip, name in targets:
    try:
        c = RouterOSClient(ip, 8728, timeout=12)
        c.connect()
        c.login('admin','Khant1234@')
        sc = c.talk(['/system/scheduler/print','=.proplist=.id,name,interval'])
        for x in sc:
            if x[0]=='!re':
                d = pr(x)
                nm = d.get('name','')
                sid = d.get('.id')
                if nm == 'hs-quota-saver' and sid:
                    c.talk(['/system/scheduler/set', f'=.id={sid}', '=interval=60s'])
                    print(f'  {name}: {nm} -> 60s (was {d.get("interval")})')
                elif nm == 'hs-continuous-expire-monitor' and sid:
                    c.talk(['/system/scheduler/set', f'=.id={sid}', '=interval=2m'])
                    print(f'  {name}: {nm} -> 2m (was {d.get("interval")})')
        c.close()
    except Exception as e:
        print(f'  {name}: ERROR {e}')