#!/usr/bin/env python3
"""Probe hotspot server + address pool + DHCP server so we know the exact
subnet to target with the single PCQ simple queue."""
import sys, json
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'


def parse_re(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


c = RouterOSClient(IP, timeout=10)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

print('=== /ip/hotspot server ===')
for r in c.talk(['/ip/hotspot/print', '=.proplist=name,interface,address-pool,addresses-per-mac']):
    if r[0] == '!re':
        d = parse_re(r)
        print('  ', json.dumps(d))

print('=== /ip/pool ===')
for r in c.talk(['/ip/pool/print', '=.proplist=name,ranges']):
    if r[0] == '!re':
        d = parse_re(r)
        print('  ', json.dumps(d))

print('=== /ip/dhcp-server ===')
for r in c.talk(['/ip/dhcp-server/print', '=.proplist=name,interface,address-pool']):
    if r[0] == '!re':
        d = parse_re(r)
        print('  ', json.dumps(d))

print('=== /ip/address (subnets) ===')
for r in c.talk(['/ip/address/print', '=.proplist=address,interface']):
    if r[0] == '!re':
        d = parse_re(r)
        print('  ', json.dumps(d))

print('=== hotspot active unique subnets ===')
ips = set()
for r in c.talk(['/ip/hotspot/active/print', '=.proplist=address']):
    if r[0] == '!re':
        d = parse_re(r)
        a = d.get('address', '')
        if a:
            parts = a.split('.')
            if len(parts) == 4:
                ips.add('.'.join(parts[:3]) + '.0')
print('  ', sorted(ips))

print('=== /queue/type (all) ===')
for r in c.talk(['/queue/type/print', '=.proplist=name,kind,rate,limit-at,classifier,queue-size,total-priority']):
    if r[0] == '!re':
        d = parse_re(r)
        print('  ', json.dumps(d))

c.close()
print('DONE')