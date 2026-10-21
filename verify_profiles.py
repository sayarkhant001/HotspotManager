#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scan every online router's hotspot profiles: name vs limit-uptime.
Goal: confirm (a) AYeikSitt1 2GB/4GB limit-uptime = 1d (so those TIME_OVERDUE
kills at ~24h were legit), (b) no long-term-looking profile (7d/30d/1month/50GB)
carries a SHORT limit-uptime that the enforcer's resolve_dur fallback would use
to wrongly expire long-term vouchers, and (c) count enabled users per profile."""
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"

REMOTE_SCRIPT = r'''#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

routers = [('10.200.0.2', 'YadanarTun'), ('10.200.0.3', 'AYeikSitt1'),
           ('10.200.0.4', 'KyawGyi')]
for ip, nm in routers:
    print(f"=== {nm} {ip} ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=12)
        c.connect()
        if not c.login('admin', 'Khant1234@'):
            print("  AUTHFAIL"); continue
        profs = [parse_re(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,limit-uptime']) if x[0] == '!re']
        # counts of enabled users per profile
        users = [parse_re(x) for x in c.talk(['/ip/hotspot/user/print', '=.proplist=profile,disabled']) if x[0] == '!re']
        from collections import Counter
        en = Counter(u.get('profile','') for u in users if u.get('disabled') != 'true')
        to = Counter(u.get('profile','') for u in users)
        for p in profs:
            pn = p.get('name', '?')
            up = p.get('limit-uptime', '')
            print(f"  {pn:<20} limit-uptime={up or '(none)':<14} enabled={en.get(pn,0)} total={to.get(pn,0)}")
        c.close()
    except Exception as e:
        print(f"  ERR {e}")
print('DONE')
'''

def run(ssh, cmd, timeout=600):
    print(f"\n$ {cmd[:130]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-8000:])
    if err.strip():
        print("[stderr]", err[-1200:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print("Connected", HOST)
    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    with sftp.open(f"{STAGE}/vps_check_profiles.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_check_profiles.py {REMOTE}/vps_check_profiles.py")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_check_profiles.py")
    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()