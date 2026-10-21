#!/usr/bin/env python3
"""Comprehensive CPU diagnosis for KyawGyi"""
import sys, time
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
    ('10.200.0.3','AYeikSitt1')
]

for ip, name in targets:
    print(f'\n=== {name} ({ip}) ===')
    try:
        c = RouterOSClient(ip, 8728, timeout=15)
        c.connect()
        c.login('admin','Khant1234@')

        # CPU + uptime
        r = c.talk(['/system/resource/print'])
        for x in r:
            if x[0]=='!re':
                d = pr(x)
                cl = d.get('cpu-load','?')
                ut = d.get('uptime','?')
                print(f'  CPU={cl}% uptime={ut}')

        # Processes with CPU > 1
        proc = c.talk(['/system/process/print'])
        for p in proc:
            if p[0]!='!re': continue
            d = pr(p)
            try:
                if int(d.get('cpu','0')) > 1:
                    cpu = d.get('cpu','0')
                    nm = d.get('name','?')
                    args = d.get('args','')[:50]
                    print(f'  {cpu:>5}% {nm}')
            except: pass

        # Simple queues total
        q = c.talk(['/queue/simple/print'])
        qcnt = sum(1 for x in q if x[0]=='!re')
        print(f'  Simple queues: {qcnt}')

        c.close()
    except Exception as e:
        print(f'  ERROR: {e}')