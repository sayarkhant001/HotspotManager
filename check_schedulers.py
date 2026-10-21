#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    print(f"\n=== {name} ({ip}) SCHEDULERS ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=8)
        c.connect()
        c.login('admin','Khant1234@')
        r = c.talk(['/system/scheduler/print','=.proplist=name,interval,on-event,start-time'])
        for x in r:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                ev = d.get('on-event','')
                if len(ev) > 70: ev = ev[:70] + '...'
                print(f"  {d.get('name','?'):35s} int={d.get('interval','?'):6s} start={d.get('start-time','?'):10s} event={ev}")
        # Also check scripts
        print(f"  --- SCRIPTS ---")
        s = c.talk(['/system/script/print','=.proplist=name,comment'])
        for x in s:
            if x[0] == '!re':
                d = {}
                for item in x[1:]:
                    if item.startswith('='):
                        k,v = item[1:].split('=',1)
                        d[k] = v
                print(f"  {d.get('name','?'):35s} {d.get('comment','')}")
        c.close()
    except Exception as e:
        print(f"  ERROR: {e}")
