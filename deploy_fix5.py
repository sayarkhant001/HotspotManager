#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy the 'feel the rest' repair logic and run it against all routers."""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"


def run(ssh, cmd, timeout=1800):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-5000:])
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

    run(ssh, f"cp -f {STAGE}/automated_enforcer_v3.py {REMOTE}/automated_enforcer_v3.py && chmod 644 {REMOTE}/automated_enforcer_v3.py")
    run(ssh, f"grep -c 'rest_known' {REMOTE}/automated_enforcer_v3.py")

    run(ssh, "sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "sleep 8; systemctl is-active hotspot-enforcer; systemctl status hotspot-enforcer --no-pager | head -5")

    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u vps_repair_longterm.py'")

    # show the newest restore/skip decisions
    run(ssh, "sleep 2; grep -E 'restored REMAINING|skipped restore' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null | tail -15 || true")

    ssh.close()
    print("\nDONE — feel-the-rest repair deployed and executed.")


if __name__ == "__main__":
    main()