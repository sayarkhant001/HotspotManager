#!/usr/bin/env python3
"""Verify the V3 scheduler intervals + profile session-timeouts on every router.
Checks hs-quota-saver (60s) and hs-continuous-expire-monitor (3m) and that the
4 core scripts contain the [USED-UP:] uptime tags."""
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
try:
    import cloud_database as db
except ImportError:
    db = None


def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2:
                res[p[0]] = p[1]
    return res


def main():
    if not db:
        print("no db"); return
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT id, name, wg_ip, api_port, status FROM routers")
    rows = c.fetchall()
    conn.close()
    for r in rows:
        rid, rname, ip, port, status = r
        print(f"\n=== {rname} {ip} status={status} ===")
        if status != 'online':
            print("  offline - skip"); continue
        client = RouterOSClient(ip, port=port or 8728, timeout=10.0)
        try:
            client.connect()
            if not client.login('admin', 'Khant1234@'):
                print("  login FAILED"); continue
            scheds = {parse_re(x).get('name'): parse_re(x).get('interval')
                      for x in client.talk(['/system/scheduler/print', '=.proplist=name,interval']) if x[0] == '!re'}
            for name in ['hs-quota-saver', 'hs-continuous-expire-monitor']:
                print(f"  scheduler {name}: {scheds.get(name, 'MISSING')}")
            for sn in ['voucher-activate', 'hs-quota-save', 'voucher-expire-check', 'hs-on-logout']:
                res = [parse_re(x) for x in client.talk(['/system/script/print', f'?name={sn}', '=.proplist=source']) if x[0] == '!re']
                src = res[0].get('source', '') if res else ''
                print(f"  script {sn}: len={len(src)} USED-UP={'[USED-UP:' in src} BASE-UP={'[BASE-UP:' in src}")
            profs = {parse_re(x).get('name'): parse_re(x).get('session-timeout')
                     for x in client.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,session-timeout']) if x[0] == '!re'}
            for pn, st in profs.items():
                print(f"  profile {pn}: session-timeout={st}")
            client.close()
        except Exception as e:
            print(f"  ERROR: {e}")
            try: client.close()
            except Exception: pass


if __name__ == '__main__':
    main()