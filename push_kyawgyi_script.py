#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Push the updated voucher-activate to KyawGyi (slow big router) with a 30s
client timeout, then re-verify."""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"

REMOTE_PUSH = r'''#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
import cloud_config_enforcer as cce

ip = '10.200.0.5'
c = RouterOSClient(ip, 8728, timeout=30)
try:
    c.connect()
    if not c.login('admin', 'Khant1234@'):
        print('AUTHFAIL'); sys.exit(1)
    src = cce.VOUCHER_ACTIVATE_SOURCE
    ex = c.talk(['/system/script/print', '?name=voucher-activate', '=.proplist=.id'])
    got = [x for x in ex if len(x) > 1 and x[0] == '!re']
    if got and got[0].get('.id'):
        sid = got[0]['.id']
        c.talk(['/system/script/set', f'=.id={sid}', f'=source={src}'])
        print('voucher-activate UPDATED on KyawGyi (set)')
    else:
        c.talk(['/system/script/add', '=name=voucher-activate', f'=source={src}'])
        print('voucher-activate ADDED on KyawGyi')
    # verify
    r = c.talk(['/system/script/print', '?name=voucher-activate', '=.proplist=source'])
    cur = ''.join(x[1] for x in r if len(x) > 1 and x[0] == '!re')
    print('upProf_fallback=', 'upProf' in cur, 'len=', len(cur))
    c.close()
except Exception as e:
    print('ERR', e)
print('PUSH_DONE')
'''


def run(ssh, cmd, timeout=900):
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
    with sftp.open(f"{STAGE}/vps_push_kyawgyi.py", "w") as f:
        f.write(REMOTE_PUSH)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_push_kyawgyi.py {REMOTE}/vps_push_kyawgyi.py")
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u vps_push_kyawgyi.py'")

    ssh.close()
    print("DONE")


if __name__ == "__main__":
    main()