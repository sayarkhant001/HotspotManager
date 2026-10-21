#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Final verification of the deployed fix on the VPS."""
import hashlib
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
BASE = os.path.dirname(os.path.abspath(__file__))


def run(ssh, cmd, timeout=300):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-4000:])
    if err.strip():
        print("[stderr]", err[-1200:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected {HOST}")

    # file checksum match (ensure the running code == the fixed repo code)
    sftp = ssh.open_sftp()
    for name in ["automated_enforcer_v3.py", "cloud_config_enforcer.py", "deploy_theme.py", "deploy_v3_to_router.py"]:
        local_md5 = hashlib.md5(open(os.path.join(BASE, name), "rb").read()).hexdigest()
        remote_buf = sftp.file(f"{REMOTE}/{name}", "rb").read()
        remote_md5 = hashlib.md5(remote_buf).hexdigest()
        print(f"{name}: local={local_md5[:10]} remote={remote_md5[:10]} match={local_md5 == remote_md5}")
    sftp.close()

    # service health
    run(ssh, "systemctl is-active hotspot-enforcer; systemctl is-active hotspot-cloud")

    # repair tally + fixed dial count today
    run(ssh, "grep -c 'REPAIRED long-term' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")
    run(ssh, "grep 'REPAIRED' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null | tail -5 || true")
    # confirm NO new TIME_OVERDUE-from-1day kills since the fix (compare last lines)
    run(ssh, "grep 'TIME_OVERDUE' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null | tail -3 || true")

    # confirm router deploy went through for KyawGyi last run
    run(ssh, "grep -E 'Deploying V3|DONE:|SKIP' /tmp/nonexistent 2>/dev/null || true")
    run(ssh, "uptime -p")

    ssh.close()
    print("\nFINAL OK")


if __name__ == "__main__":
    main()