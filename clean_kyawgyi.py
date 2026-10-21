#!/usr/bin/env python3
"""Clean junk scripts from KyawGyi router"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

KEEP = {'voucher-activate','hs-quota-save','voucher-expire-check','hs-on-logout',
        'v3-startup-heal','wexz_profiles_meta','sync-whitelisted-hosts',
        'github-rsc-fetch-run','ghost-lease-cleaner'}

c = RouterOSClient('10.200.0.4', 8728, timeout=10)
c.connect()
c.login('admin','Khant1234@')

scripts = c.talk(['/system/script/print','=.proplist=.id,name'])
for s in scripts:
    if s[0] != '!re': continue
    d = {}
    for item in s[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    name = d.get('name','')
    sid = d.get('.id','')
    if name not in KEEP and sid:
        c.talk(['/system/script/remove', f'=.id={sid}'])
        print(f"  Removed junk script: {name}")

c.close()
print("KyawGyi cleaned.")
