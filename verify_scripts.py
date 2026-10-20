#!/usr/bin/env python3
"""Verify V3 scripts are deployed correctly on all routers"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

MARKERS = {
    'voucher-activate': 'V3',
    'hs-quota-save': 'V3 Lightweight',
    'voucher-expire-check': 'V3',
    'hs-on-logout': 'V3',
    'v3-startup-heal': 'V3',
}

for ip, name in [('10.200.0.2','YadanarTun'),('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1')]:
    print(f"\n=== {name} ({ip}) ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=10)
        c.connect()
        c.login('admin','Khant1234@')
        # Check scripts
        sc = c.talk(['/system/script/print', '=.proplist=.id,name,source,comment'])
        for s in sc:
            if s[0] != '!re': continue
            d = {}
            for item in s[1:]:
                if item.startswith('='):
                    k,v = item[1:].split('=',1)
                    d[k] = v
            nm = d.get('name','')
            if nm in MARKERS:
                src = d.get('source','')
                ok = MARKERS[nm] in src or 'V3' in src
                print(f"  {nm:25s} {'✅ V3' if ok else '❌ OLD!'}")
        # Check schedulers
        sched = c.talk(['/system/scheduler/print', '=.proplist=.id,name,on-event'])
        print("  Schedulers:")
        for s in sched:
            if s[0] != '!re': continue
            d = {}
            for item in s[1:]:
                if item.startswith('='):
                    k,v = item[1:].split('=',1)
                    d[k] = v
            print(f"    {d.get('name','?'):30s} -> {d.get('on-event','')}")
        c.close()
    except Exception as e:
        print(f"  ERROR: {e}")
