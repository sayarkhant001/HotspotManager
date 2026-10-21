#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""LIVE AUDIT: is every router cutting exactly at time AND quota, without
hurting sellers/customers (incl. long-term vouchers)?

Checks from the VPS:
  1. systemd: hotspot-enforcer + hotspot-cloud active?
  2. Deployed enforcer V3 code == repo code (sha1 of the 4 key files).
  3. routers table: wg_ip / status / last_audit_time / audit_status / ROS version.
  4. On EVERY router (via API): voucher-activate (upProf-fallback),
     hs-quota-save, voucher-expire-check, hs-on-logout hash-matched to the
     intended V3 sources + schedulers + profile hooks.
  5. Enforcer log tail: repairs (long-term) vs kills vs errors.
"""
import hashlib
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"
BASE = os.path.dirname(os.path.abspath(__file__))

FILES_TO_HASH = [
    "automated_enforcer_v3.py",
    "cloud_config_enforcer_v3.py",
    "cloud_config_enforcer.py",
    "deploy_theme.py",
]


def local_hashes():
    data = {}
    for fn in FILES_TO_HASH:
        path = os.path.join(BASE, fn)
        if os.path.exists(path):
            data[fn] = hashlib.sha1(open(path, "rb").read()).hexdigest()[:16]
    return data


REMOTE_AUDIT = r'''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys, json, hashlib, subprocess
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

LOCAL_HASHES = json.load(open(sys.argv[1]))

print("=== 1. SERVICES ===")
for svc in ("hotspot-enforcer", "hotspot-cloud"):
    try:
        out = subprocess.check_output(["systemctl", "is-active", svc], text=True).strip()
        print(f"  {svc}: {out}")
    except Exception as e:
        print(f"  {svc}: ERROR {e}")

print("=== 2. DEPLOYED CODE HASHES ===")
for fn, expect in LOCAL_HASHES.items():
    try:
        got = hashlib.sha1(open(f"/opt/hotspot-cloud/{fn}", "rb").read()).hexdigest()[:16]
        print(f"  {fn}: {'MATCH' if got == expect else 'MISMATCH! local='+expect+' remote='+got}")
    except Exception as e:
        print(f"  {fn}: ERR {e}")

print("=== 3. ROUTERS TABLE ===")
try:
    import cloud_database as db
    conn = db.get_db(); c = conn.cursor()
    c.execute("SELECT id, name, wg_ip, api_port, status, last_audit_time, audit_status, routeros_version FROM routers")
    rows = c.fetchall(); conn.close()
    for r in rows:
        print(f"  #{r['id']} {r['name']:<12} {r['wg_ip']} api={r['api_port']} status={r['status']} last_audit={r['last_audit_time']} audit={r['audit_status']} ros={r['routeros_version']}")
except Exception as e:
    print(f"  ERROR reading routers table: {e}")

print("=== 4. ROUTER BY ROUTER ===")
routers = [('10.200.0.2', 'Router2'), ('10.200.0.3', 'YadanarTun'),
           ('10.200.0.4', 'AYeikSitt1'), ('10.200.0.5', 'KyawGyi')]
try:
    import cloud_config_enforcer as cce
    EXPECT = {
        'voucher-activate': cce.VOUCHER_ACTIVATE_SOURCE,
        'hs-quota-save': cce.HS_QUOTA_SAVE_SOURCE,
        'voucher-expire-check': cce.VOUCHER_EXPIRE_CHECK_SOURCE,
        'hs-on-logout': cce.HS_ON_LOGOUT_SOURCE,
    }
except Exception as e:
    cce = None; EXPECT = {}
    print(f"  WARN cannot import cce: {e}")

def talk(c, path, proplist=".id", **q):
    args = [path + "/print", f"=.proplist={proplist}"]
    for k, v in q.items():
        args.append(f"?{k}={v}")
    out = []
    for r in c.talk(args):
        if r and r[0] == '!re':
            d = {}
            for item in r[1:]:
                if item.startswith('='):
                    p = item[1:].split('=', 1)
                    if len(p) == 2: d[p[0]] = p[1]
            out.append(d)
    return out

for ip, nm in routers:
    print(f"  -- {nm} {ip} --")
    try:
        c = RouterOSClient(ip, 8728, timeout=10)
        c.connect()
        if not c.login('admin', 'Khant1234@'):
            print("     AUTH FAILED"); continue
        for name, src in EXPECT.items():
            rs = talk(c, '/system/script', 'name,source', name=name)
            if not rs:
                print(f"     MISSING script: {name}"); continue
            got = rs[0].get('source', '')
            ok = hashlib.sha1(got.encode()).hexdigest()[:12] == \
                 hashlib.sha1(src.encode()).hexdigest()[:12]
            extra = ''
            if name == 'voucher-activate':
                extra = f" upProf_fallback={'upProf' in got}"
            print(f"     {name}: {'MATCH' if ok else 'MISMATCH'} len={len(got)}{extra}")
        scheds = {s.get('name', '') for s in talk(c, '/system/scheduler', 'name,interval')}
        for n in sorted({'hs-quota-saver', 'hs-continuous-expire-monitor', 'v3-startup-heal'}):
            print(f"     scheduler {n}: {'OK' if n in scheds else 'MISSING'}")
        profs = talk(c, '/ip/hotspot/user/profile', 'name,on-login,on-logout')
        ol_ok = sum(1 for p in profs if 'voucher-activate' in p.get('on-login', ''))
        ol = sum(1 for p in profs if 'hs-on-logout' in p.get('on-logout', ''))
        print(f"     profiles={len(profs)} on-login(voucher-activate)={ol_ok} on-logout(hs-on-logout)={ol}")
        c.close()
    except Exception as e:
        print(f"     ERR {e}")
print('CHECK_DONE')
'''


def run(ssh, cmd, timeout=900):
    print(f"\n$ {cmd[:140]}")
    if len(cmd) > 140:
        print(f"  [... command len={len(cmd)} chars]")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-12000:])
    if err.strip():
        print("[stderr]", err[-1500:])
    print(f"  -> rc={rc}")
    return rc


def main():
    import json as _json
    hashes = local_hashes()
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected {HOST}\nLocal hashes: {hashes}")

    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    with sftp.open(f"{STAGE}/vps_audit_live.py", "w") as f:
        f.write(REMOTE_AUDIT)
    with sftp.open(f"{STAGE}/audit_hashes.json", "w") as f:
        f.write(_json.dumps(hashes))
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_audit_live.py {REMOTE}/vps_audit_live.py")
    run(ssh, f"cd {REMOTE} && cp -f {STAGE}/audit_hashes.json {REMOTE}/audit_hashes.json")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_audit_live.py audit_hashes.json")

    print("\n=== 5. ENFORCER LOG TAIL (repairs / kills / errors) ===")
    run(ssh, "tail -160 /opt/hotspot-cloud/automated_enforcer.log | tail -70")

    ssh.close()
    print("\nAUDIT COMPLETE")


if __name__ == "__main__":
    main()