#!/usr/bin/env python3
"""Deep kernel-level CPU diagnosis - find the packet path consumer"""
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

ip = '10.200.0.4'  # KyawGyi
c = RouterOSClient(ip, 8728, timeout=20)
c.connect()
c.login('admin','Khant1234@')

print(f'=== KyawGyi deep diagnostics ===')

# 1. Interface traffic NOW (sample twice for rate)
def iface_stats():
    out = {}
    r = c.talk(['/interface/print','=.proplist=name,rx-byte,tx-byte,rx-drop,tx-drop,running,disabled'])
    for x in r:
        if x[0]=='!re':
            d = pr(x)
            nm = d.get('name','')
            out[nm] = d
    return out

s1 = iface_stats()
time.sleep(3)
s2 = iface_stats()
for nm in s1:
    d1, d2 = s1[nm], s2[nm]
    try:
        rx1 = int(d1.get('rx-byte','0')); rx2 = int(d2.get('rx-byte','0'))
        tx1 = int(d1.get('tx-byte','0')); tx2 = int(d2.get('tx-byte','0'))
        rxp = (rx2-rx1)/3.0; txp = (tx2-tx1)/3.0
        if rxp > 100000 or txp > 100000:
            print(f'  {nm}: RX {rxp/1e6:.1f} MB/s TX {txp/1e6:.1f} MB/s running={d2.get("running")}')
    except: pass

# 2. Conntrack count
try:
    ct = c.talk(['/ip/firewall/connection/print','=count-only='])
    print(f'  Conntrack: {ct}')
except Exception as e:
    print(f'  Conntrack err: {e}')

# 3. Bridge host count + mac table size
try:
    b = c.talk(['/interface/bridge/host/print','=count-only='])
    print(f'  Bridge hosts: {b}')
except Exception as e:
    print(f'  Bridge err: {e}')

# 4. Firewall filter counters (top by bytes)
try:
    fw = c.talk(['/ip/firewall/filter/print','=.proplist=.id,chain,comment,bytes'])
    rated = []
    for x in fw:
        if x[0]=='!re':
            d = pr(x)
            try: rated.append((int(d.get('bytes','0')), d.get('comment',d.get('chain','?'))))
            except: pass
    rated.sort(reverse=True)
    print('  Top firewall counters:')
    for b, cm in rated[:5]:
        print(f'    {b/1e6:.0f}MB  {cm}')
except Exception as e:
    print(f'  FW err: {e}')

# 5. DHCP server leases (binding churn)
try:
    l = c.talk(['/ip/dhcp-server/lease/print','=.proplist=.id,status,address'])
    st = {}
    for x in l:
        if x[0]=='!re':
            s = pr(x).get('status','?')
            st[s] = st.get(s,0)+1
    print(f'  DHCP leases: {st}')
except Exception as e:
    print(f'  DHCP err: {e}')

# 6. Active sessions count + total bytes
try:
    a = c.talk(['/ip/hotspot/active/print','=.proplist=.id,user,bytes-in,bytes-out'])
    tot_in = tot_out = 0
    for x in a:
        if x[0]=='!re':
            d = pr(x)
            tot_in += int(d.get('bytes-in','0')); tot_out += int(d.get('bytes-out','0'))
    print(f'  Active sessions: {sum(1 for x in a if x[0]=="!re")} (total {tot_in/1e9:.1f}GB in / {tot_out/1e9:.1f}GB out)')
except Exception as e:
    print(f'  HS err: {e}')

# 7. Routerboard CPU temp (thermal throttle?)
try:
    h = c.talk(['/system/health/print'])
    for x in h:
        if x[0]=='!re':
            d = pr(x)
            print(f'  Temp: {d} ')
except: pass

c.close()