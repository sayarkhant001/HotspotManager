#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify the updated voucher-activate script (with the [ORIG-UP:] profile
fallback) is present on each online router."""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"
BASE = os.path.dirname(os.path.abspath(__file__))

REMOTE_CHECK = r'''#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
routers = [('10.200.0.2', 'Router2', 8728), ('10.200.0.3', 'YadanarTun', 8728),
           ('10.200.0.4', 'AYeikSitt1', 8728), ('10.200.0.5', 'KyawGyi', 8728)]
for ip, nm, port in routers:
    try:
        c = RouterOSClient(ip, port, timeout=8)
        c.connect()
        if not c.login('admin', 'Khant1234@'):
            print(nm, ip, 'AUTHFAIL'); continue
        r = c.talk(['/system/script/print', '?name=voucher-activate', '=.proplist=source'])
        src = ''.join(x[1] for x in r if len(x) > 1 and x[0] == '!re')
        print(nm, ip, 'upProf_fallback=', 'upProf' in src, 'len=', len(src))
        c.close()
    except Exception as e:
        print(nm, ip, 'ERR', e)
print('CHECK_DONE')
'''


def run(ssh, cmd, timeout=600):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-4000:])
    if err.strip():
        print("[stderr]", err[-1500:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected {HOST}")

    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    with sftp.open(f"{STAGE}/vps_check_router_scripts.py", "w") as f:
        f.write(REMOTE_CHECK)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_check_router_scripts.py {REMOTE}/vps_check_router_scripts.py")
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u vps_check_router_scripts.py'")

    ssh.close()
    print("DONE")


if __name__ == "__main__":
    main()