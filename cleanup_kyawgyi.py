#!/usr/bin/env python3
"""
Targeted cleanup for KyawGyi's oversized user table.
Archives users that are expired >= 2 days to reduce router CPU load.
Run AFTER automated cleanup; this is the manual deep-clean.
"""
import sys, time, json
from datetime import datetime, timedelta
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
try:
    import cloud_database as db
except ImportError:
    db = None

TARGET = '10.200.0.4'

def parse_re(r):
    d = {}
    for item in r[1:]:
        if item.startswith('='):
            k,v = item[1:].split('=',1)
            d[k] = v
    return d

c = RouterOSClient(TARGET, 8728, timeout=30)
c.connect()
c.login('admin','Khant1234@')

# Get clock
clk = c.talk(['/system/clock/print'])
clock = [parse_re(x) for x in clk if x[0] == '!re']
if not clock:
    print("No clock"); c.close(); sys.exit(1)
try:
    router_now = datetime.strptime(f"{clock[0].get('date','')} {clock[0].get('time','')}", "%Y-%m-%d %H:%M:%S")
except:
    router_now = datetime.now()
print(f"Router time: {router_now}")

# Fetch all users (one-time deep clean)
users = c.talk(['/ip/hotspot/user/print', '=.proplist=.id,name,comment,profile,disabled,bytes-in,bytes-out'])
all_users = [parse_re(x) for x in users if x[0] == '!re']
print(f"Total users on router: {len(all_users)}")

# Active usernames
act = c.talk(['/ip/hotspot/active/print', '=.proplist=user'])
active_names = {parse_re(x).get('user') for x in act if x[0] == '!re'}
print(f"Active: {len(active_names)}")

# Parse duration helper
def parse_duration_secs(comm, prof):
    c2 = (comm or '').lower()
    p = (prof or '').lower()
    if '[orig-up:' in c2:
        up = comm.split('[ORIG-UP:')[1].split(']')[0].strip().lower()
        if any(w in up for w in ['4w','30d','month']): return 30*86400
        if '7d' in up: return 7*86400
        if '3d' in up: return 3*86400
        if '1d' in up or '24h' in up: return 86400
        if '12h' in up: return 12*3600
        if '6h' in up: return 6*3600
        if '2h' in up: return 2*3600
        if '1h' in up: return 3600
        if 'test' in up or '3m' in up: return 180
    if any(k in p or k in c2 for k in ["50gb","30d","30day","40g","4w"]): return 30*86400
    if any(k in p or k in c2 for k in ["7d","1w"]): return 7*86400
    if "3d" in p or "3d" in c2: return 3*86400
    if any(k in p or k in c2 for k in ["1h","1hour"]): return 3600
    if "test" in p: return 180
    return 86400  # default 1 day

eligible = []
for u in all_users:
    name = u.get('name','')
    if not name or name in ('admin','default-trial'): continue
    if name in active_names: continue  # never touch active
    comm = u.get('comment','')
    is_dis = (u.get('disabled') == 'true')
    # Only archive DISABLED users with EXPIRED/EXHAUSTED tags
    if '[EXPIRED:' not in comm and '[EXHAUSTED:' not in comm:
        continue
    # Parse activation time
    if '[ACT:' not in comm:
        continue
    act_str = comm.split('[ACT:')[1].split(']')[0].strip()
    try:
        if '-' in act_str:
            act_dt = datetime.strptime(act_str, "%Y-%m-%d %H:%M:%S")
        else:
            act_dt = datetime.strptime(act_str, "%b/%d/%Y %H:%M:%S")
    except:
        continue
    dur = parse_duration_secs(comm, u.get('profile',''))
    expiry = act_dt + timedelta(seconds=dur)
    # Eligible if expired more than 2 days ago AND disabled
    if is_dis and router_now >= (expiry + timedelta(days=2)):
        eligible.append((u.get('.id'), name, comm))

print(f"Eligible for archival: {len(eligible)}")
for uid, name, comm in eligible[:10]:
    print(f"  {name:12s} {comm[:60]}")

# Archive them (remove from router, store in DB)
archived = 0
if db and eligible:
    conn = db.get_db()
    cur = conn.cursor()
    for uid, name, comm in eligible:
        try:
            c.talk(['/ip/hotspot/user/remove', f'=.id={uid}'])
            cur.execute("INSERT OR IGNORE INTO archived_vouchers (code, comment, archived_at) VALUES (?,?,?)",
                (name, comm, int(time.time())))
            archived += 1
            if archived % 100 == 0:
                print(f"  ... archived {archived}")
        except Exception as e:
            print(f"  FAILED {name}: {e}")
    conn.commit()
    conn.close()

print(f"\nTotal archived from router: {archived}")
print(f"Remaining on router: {len(all_users) - archived}")
c.close()
