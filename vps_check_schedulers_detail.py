#!/usr/bin/env python3
"""Inspect all schedulers on a router (find duplicates / stale intervals)."""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient


def parse_re(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'
c = RouterOSClient(IP, timeout=10)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)
for r in c.talk(['/system/scheduler/print', '=.proplist=name,interval,start-time,comment']):
    if r[0] == '!re':
        d = parse_re(r)
        print(f"name={d.get('name')} interval={d.get('interval')} start={d.get('start-time')} comment={d.get('comment')}")
c.close()