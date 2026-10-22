#!/usr/bin/env python3
"""Test the EXACT full profile-set command used by the deploy, and dump current
queue/profile state, printing raw RouterOS replies (catches silent traps)."""
import sys, json
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

IP = sys.argv[1] if len(sys.argv) > 1 else '10.200.0.2'


def parse(r):
    d = {}
    for it in r[1:]:
        if it.startswith('='):
            p = it[1:].split('=', 1)
            if len(p) == 2:
                d[p[0]] = p[1]
    return d


def show(res, label):
    print(f"\n-- {label} --")
    for r in res:
        print('   ', json.dumps(r))


c = RouterOSClient(IP, timeout=10)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('login failed'); sys.exit(1)

# exact deploy command shape
on_login = ':global hsUser $user; :do { /system script run voucher-activate } on-error={}'
on_logout = ':do { /system script run hs-on-logout } on-error={}'
p = [parse(x) for x in c.talk(['/ip/hotspot/user/profile/print', '?name=2GB', '=.proplist=.id']) if x[0] == '!re']
pid = p[0].get('.id') if p else None
print(f'2GB id={pid}')
if pid:
    args = ['/ip/hotspot/user/profile/set', f'=.id={pid}',
            '=shared-users=1', '=add-mac-cookie=yes', '=mac-cookie-timeout=30d',
            '=keepalive-timeout=2m', '=idle-timeout=3m',
            '=rate-limit=',
            f'=on-login={on_login}', f'=on-logout={on_logout}']
    show(c.talk(args), 'FULL profile set (deploy verbatim)')
    show(c.talk(['/ip/hotspot/user/profile/print', '?name=2GB', '=.proplist=rate-limit,shared-users,on-login,on-logout']),
         'read back 2GB')
    # restore rate-limit
    c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', '=rate-limit=20M/20M'])

# current queue state
print('\n-- simple queues (all) --')
for r in c.talk(['/queue/simple/print', '=.proplist=.id,name,target,max-limit,queue,dynamic']):
    if r[0] == '!re':
        print('   ', json.dumps(parse(r)))

print('\n-- queue types pcq* (pcq fields) --')
for r in c.talk(['/queue/type/print', '=.proplist=name,kind,pcq-rate,pcq-classifier,pcq-limit,pcq-total-limit']):
    if r[0] == '!re':
        d = parse(r)
        if 'pcq' in d.get('name', ''):
            print('   ', json.dumps(d))

# try to set pcq-hotspot-down with exact fields and read back
q = [parse(x) for x in c.talk(['/queue/type/print', '?name=pcq-hotspot-down', '=.proplist=.id']) if x[0] == '!re']
if q:
    qid = q[0].get('.id')
    show(c.talk(['/queue/type/set', f'=.id={qid}', '=kind=pcq', '=pcq-rate=20M',
                 '=pcq-classifier=dst-address', '=pcq-limit=50', '=pcq-total-limit=2000']),
         'queue/type/set pcq-hotspot-down')
    show(c.talk(['/queue/type/print', f'?name=pcq-hotspot-down', '=.proplist=kind,pcq-rate,pcq-classifier,pcq-limit,pcq-total-limit']),
         'read back pcq-hotspot-down')

c.close()
print('\nDONE')