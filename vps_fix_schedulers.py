#!/usr/bin/env python3
"""Force-correct scheduler intervals on a router via direct API set."""
import sys
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

targets = {
    'hs-quota-saver': ('60s', '/system script run hs-quota-save', 'V3 quota saver'),
    'hs-continuous-expire-monitor': ('3m', '/system script run voucher-expire-check', 'V3 expire monitor'),
}
# list all schedulers first
scheds = []
for r in c.talk(['/system/scheduler/print', '=.proplist=.id,name,interval,comment']):
    if r[0] == '!re':
        scheds.append(parse_re(r))
print('CURRENT:')
for s in scheds:
    print(f"  {s.get('name')}: {s.get('interval')} [{s.get('comment')}]")

for name, (interval, event, comment) in targets.items():
    matches = [s for s in scheds if s.get('name') == name]
    if not matches:
        print(f'  ADD {name} {interval}')
        c.talk(['/system/scheduler/add', f'=name={name}', f'=interval={interval}',
                f'=on-event={event}', '=start-time=startup', f'=comment={comment}'])
    else:
        sid = matches[0].get('.id')
        print(f'  SET {name} (={sid}) -> interval={interval}')
        c.talk(['/system/scheduler/set', f'=.id={sid}', f'=interval={interval}', f'=on-event={event}'])

# verify
for r in c.talk(['/system/scheduler/print', '=.proplist=name,interval']):
    if r[0] == '!re':
        d = parse_re(r)
        if d.get('name') in targets:
            print(f"AFTER {d.get('name')}: {d.get('interval')}")
c.close()