#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Raw probe: try setting profile limit-uptime and see the ACTUAL API response."""
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

c = RouterOSClient('10.200.0.4', 8728, timeout=12)   # KyawGyi
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('AUTHFAIL'); sys.exit(1)

print('--- full detail before (1000Ks) ---')
for r in c.talk(['/ip/hotspot/user/profile/print', '?name=1000Ks']):
    print(r)

print('--- set limit-uptime=1d ---')
r = c.talk(['/ip/hotspot/user/profile/set', '?name=1000Ks', '=limit-uptime=1d'])
print('raw:', r)

print('--- full detail after ---')
for r in c.talk(['/ip/hotspot/user/profile/print', '?name=1000Ks']):
    print(r)

print('--- proplist read ---')
for r in c.talk(['/ip/hotspot/user/profile/print', '?name=1000Ks', '=.proplist=name,limit-uptime']):
    print(r)
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
    with sftp.open(f"{STAGE}/vps_probe_set_profile.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_probe_set_profile.py {REMOTE}/vps_probe_set_profile.py")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_probe_set_profile.py")
    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()