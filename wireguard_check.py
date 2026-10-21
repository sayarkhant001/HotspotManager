#!/usr/bin/env python3
"""Check WireGuard tunnel + hotspot profile settings + interfaces"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

for ip, name in [('10.200.0.4','KyawGyi'),('10.200.0.3','AYeikSitt1'),('10.200.0.2','YadanarTun')]:
    print(f"\n=== {name} ({ip}) ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=8)
        c.connect()
        c.login('admin','Khant1234@')

        # Hotspot profiles (idle/keepalive settings)
        pr = c.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,idle-timeout,keepalive-timeout,rate-limit,shared-users'])
        for p in pr:
            if p[0] != '!re': continue
            d = {}
            for item in p[1:]:
                if item.startswith('='):
                    k,v = item[1:].split('=',1)
                    d[k] = v
            print(f"  PROF {d.get('name','?'):12s} idle={d.get('idle-timeout','?')} keepalive={d.get('keepalive-timeout','?')} rate={d.get('rate-limit','?')} shared={d.get('shared-users','?')}")

        # Wireless interfaces (check for noise/interference)
        w = c.talk(['/interface/wifi/print', '=.proplist=.id,name,running,state'])
        for x in w[:4]:
            if x[0] != '!re': continue
            d = {}
            for item in x[1:]:
                if item.startswith('='):
                    k,v = item[1:].split('=',1)
                    d[k] = v
            print(f"  WIFI {d.get('name','?'):15s} running={d.get('running','?')} state={d.get('state','?')}")

        # Hotspot server settings
        hs = c.talk(['/ip/hotspot/print', '=.proplist=.id,name,interface,address-pool'])
        for x in hs[:4]:
            if x[0] != '!re': continue
            d = {}
            for item in x[1:]:
                if item.startswith('='):
                    k,v = item[1:].split('=',1)
                    d[k] = v
            print(f"  HS {d.get('name','?')} iface={d.get('interface','?')}")

        c.close()
    except Exception as e:
        print(f"  ERROR: {e}")
