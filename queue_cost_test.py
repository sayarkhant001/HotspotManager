#!/usr/bin/env python3
"""
CONTROLLED EXPERIMENT: measure cost of per-user simple queues.
Removes profile rate-limit for 40s (stops queue churn), measures CPU, restores.
NOTE: data/time cutoff is UNAFFECTED - limit-bytes-total / limit-uptime are
kernel-enforced on each user and do not depend on profile rate-limit.
"""
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

def cpu_now(c):
    r = c.talk(['/system/resource/print'])
    for x in r:
        if x[0]=='!re':
            return pr(x).get('cpu-load','?')
    return '?'

ip = '10.200.0.4'
c = RouterOSClient(ip, 8728, timeout=15)
c.connect()
c.login('admin','Khant1234@')

print(f'Baseline CPU: {cpu_now(c)}%')

# Save current rate limits
profiles = c.talk(['/ip/hotspot/user/profile/print','=.proplist=.id,name,rate-limit'])
saved = {}
for x in profiles:
    if x[0]=='!re':
        d = pr(x)
        saved[d['name']] = (d.get('.id'), d.get('rate-limit',''))
print(f'Profiles: {len(saved)}')

# Kill queues by clearing rate-limit (queue will be removed when session ends)
for nm,(pid,rl) in saved.items():
    if rl:
        c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', '=rate-limit='])
        print(f'  Clear {nm} (was {rl})')

print('Rate limits cleared. Removing leftover queues...')
# Remove any leftover hotspot-* queues now (they'll stay for active sessions otherwise)
qs = c.talk(['/queue/simple/print','=.proplist=.id,name'])
rem = 0
for x in qs:
    if x[0]=='!re':
        d = pr(x)
        nm = d.get('name','')
        if nm.startswith('<hotspot-') or 'hotspot-' in nm:
            c.talk(['/queue/simple/remove', f'=.id={d.get(".id")}'])
            rem += 1
print(f'  Removed {rem} hotspot queues')

print('Waiting 40s for steady state...')
time.sleep(40)
during = cpu_now(c)
print(f'CPU WITHOUT queues: {during}%')

# Restore
print('Restoring rate limits...')
for nm,(pid,rl) in saved.items():
    if rl and pid:
        c.talk(['/ip/hotspot/user/profile/set', f'=.id={pid}', f'=rate-limit={rl}'])
print('Restored.')
time.sleep(5)
print(f'Final CPU: {cpu_now(c)}%')
c.close()