#!/usr/bin/env python3
"""Test if router-side schedulers cause the CPU spike.
   Slows hs-quota-saver to 5m and expire-monitor to 10m, measures CPU, restores.
   Usage: python3 test_script_load.py 10.200.0.4
"""
import sys, time
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

target = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.4'

def cpu_now():
    c = RouterOSClient(target, 8728, timeout=8)
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
            c.close()
            return d.get('cpu-load','?')
    c.close()
    return '?'

print(f"Before: CPU={cpu_now()}%")

# Slow down schedulers
c = RouterOSClient(target, 8728, timeout=10)
c.connect()
c.login('admin','Khant1234@')
c.talk(['/system/scheduler/set', '=.id=[/system/scheduler find name=hs-quota-saver]', '=interval=5m'])
c.talk(['/system/scheduler/set', '=.id=[/system/scheduler find name=hs-continuous-expire-monitor]', '=interval=10m'])
c.close()
print("Schedulers slowed to 5m/10m. Waiting 45s...")
time.sleep(45)

after = cpu_now()
print(f"After slowing: CPU={after}%")

# Restore
c = RouterOSClient(target, 8728, timeout=10)
c.connect()
c.login('admin','Khant1234@')
c.talk(['/system/scheduler/set', '=.id=[/system/scheduler find name=hs-quota-saver]', '=interval=30s'])
c.talk(['/system/scheduler/set', '=.id=[/system/scheduler find name=hs-continuous-expire-monitor]', '=interval=1m'])
c.close()
print("Restored to 30s/1m.")
