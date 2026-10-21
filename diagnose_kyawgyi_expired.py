#!/usr/bin/env python3
"""Diagnose KyawGyi expired voucher leak"""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

def parse_re(r):
    d = {}
    for item in r[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    return d

c = RouterOSClient('10.200.0.4', 8728, timeout=20)
c.connect()
c.login('admin','Khant1234@')

users = c.talk(['/ip/hotspot/user/print',
    '=.proplist=.id,name,disabled,comment,limit-bytes-total,limit-uptime,profile'])
leaked = []
for u in users:
    if u[0] != '!re': continue
    d = parse_re(u)
    name = d.get('name','')
    if name in ['admin','default-trial']: continue
    comm = d.get('comment','')
    dis = d.get('disabled','')
    lbt = d.get('limit-bytes-total','')
    has_exp = '[EXPIRED:' in comm or '[EXHAUSTED:' in comm
    # Case 1: has expired tag but NOT disabled
    if has_exp and dis != 'true':
        leaked.append(('TAGGED_NOT_DISABLED', d))
    # Case 2: disabled=false, limit-bytes-total=1 (remnant)
    elif dis != 'true' and lbt == '1' and '[ACT:' in comm:
        leaked.append(('LBT_1_NOT_DISABLED', d))
    # Case 3: disabled=true but limit-bytes > 1 (could be re-enabled)
    elif dis == 'true' and has_exp:
        try:
            if int(lbt) > 1:
                leaked.append(('DISABLED_BUT_LBT_HIGH', d))
        except: pass

print(f"LEAKED VOUCHERS: {len(leaked)}")
for reason, l in leaked[:20]:
    nm = l.get('name','')
    print(f"  [{reason}] {nm:12s} dis={l.get('disabled')} lbt={l.get('limit-bytes-total')} prof={l.get('profile','')} comm={l.get('comment','')[:70]}")

# Check active sessions for zombie users
active = c.talk(['/ip/hotspot/active/print', '=.proplist=.id,user,bytes-in,bytes-out,uptime'])
act_count = sum(1 for a in active if a[0]=='!re')
print(f"\nACTIVE SESSIONS: {act_count}")
zombies = 0
for a in active:
    if a[0] != '!re': continue
    ad = parse_re(a)
    uname = ad.get('user','')
    if uname in ['admin','default-trial']: continue
    ures = c.talk(['/ip/hotspot/user/print', f'?name={uname}', '=.proplist=disabled,comment,limit-bytes-total'])
    for ur in ures:
        if ur[0] != '!re': continue
        ud = parse_re(ur)
        ucomm = ud.get('comment','')
        if '[EXPIRED:' in ucomm or '[EXHAUSTED:' in ucomm or ud.get('disabled','') == 'true':
            bused = int(ad.get('bytes-in',0)) + int(ad.get('bytes-out',0))
            print(f"  ZOMBIE: {uname} dis={ud.get('disabled')} lbt={ud.get('limit-bytes-total','')} session_bytes={bused}")
            zombies += 1

print(f"\nZOMBIE SESSIONS: {zombies}")

# Check voucher-activate script content
scripts = c.talk(['/system/script/print', '?name=voucher-activate', '=.proplist=source'])
for s in scripts:
    if s[0] != '!re': continue
    sd = parse_re(s)
    src = sd.get('source','')
    if 'V3' in src:
        print("\nvoucher-activate: V3 CONFIRMED")
    else:
        print(f"\nvoucher-activate: NOT V3! First 80 chars: {src[:80]}")
    # Check if REJECT block exists
    if 'EXPIRED' in src and 'EXHAUSTED' in src and 'disabled=yes' in src:
        print("  REJECT block: EXISTS")
    else:
        print("  REJECT block: MISSING!")

c.close()
