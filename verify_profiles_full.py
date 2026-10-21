#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ground-truth profile limit-uptime via FULL detail (no proplist)."""
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

c = RouterOSClient('10.200.0.3', 8728, timeout=12)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('AUTHFAIL'); sys.exit(1)
rows = [parse_re(x) for x in c.talk(['/ip/hotspot/user/profile/print']) if x[0] == '!re']
for p in rows:
    print(f"name={p.get('name','?'):<14} session-timeout={p.get('session-timeout',''):<14} "
          f"idle-timeout={p.get('idle-timeout','')} "
          f"on-login={(p.get('on-login') or '')[:50]}")
c.close()
print('DONE')
'''

def run(ssh, cmd, timeout=600):
    print(f"\n$ {cmd[:130]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-6000:])
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
    with sftp.open(f"{STAGE}/vps_check_profiles_full.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_check_profiles_full.py {REMOTE}/vps_check_profiles_full.py")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_check_profiles_full.py")
    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()