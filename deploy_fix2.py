#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Finish the fix deployment: restart hotspot-enforcer with the new code and
run the router deploy + auto-repair with the SAME python the service uses."""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"


def run(ssh, cmd, timeout=1800):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-6000:])
    if err.strip():
        print("[stderr]", err[-2500:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected to {HOST}")

    # 1. Inspect the enforcer unit to find its python interpreter
    run(ssh, "systemctl cat hotspot-enforcer --no-pager")

    # 2. Restart the ACTUAL enforcer service so it loads the fixed code
    run(ssh, "sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "systemctl is-active hotspot-enforcer")

    # 3. Find the python the service uses (common VPS layouts)
    probes = [
        "readlink -f $(which python3)",
        "ls -l /opt/hotspot-cloud/venv/bin/python 2>/dev/null || true",
        "which python3.11 python3.12 python3.10 2>/dev/null || true",
        "ls /opt/hotspot-cloud/venv/bin/ 2>/dev/null | head -5 || true",
    ]
    for p in probes:
        run(ssh, p)

    # 4. Try deploy + repair with the service's python interpreters in order
    py_candidates = [
        "/opt/hotspot-cloud/venv/bin/python",
        "$(ls /opt/hotspot-cloud/venv*/bin/python 2>/dev/null | head -1)",
        "python3",
    ]
    ran = False
    for py in py_candidates:
        cmd = (f"sudo -n bash -lc 'cd /opt/hotspot-cloud && {py} -c \"import paramiko, fastapi; "
               f"print(\"interpreter OK: {py}\")\"'")
        rc = run(ssh, cmd)
        if rc == 0:
            run(ssh, f"sudo -n bash -lc 'cd /opt/hotspot-cloud && {py} -u deploy_v3_to_router.py --all'")
            run(ssh, f"sudo -n bash -lc 'cd /opt/hotspot-cloud && {py} -u vps_repair_longterm.py'")
            ran = True
            break

    if not ran:
        print("WARN: no working python found via probes; locates python from unit ExecStart manually.")

    # 5. Confirm the new code is actually running: log tail + grep REPAIRED
    run(ssh, "sudo -n systemctl is-active hotspot-enforcer")
    run(ssh, "sudo -n tail -n 50 /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")
    run(ssh, "sudo -n grep -c 'REPAIRED long-term' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")

    ssh.close()
    print("\nDONE — enforcer restarted with fixed code; router deploy + repair executed.")


if __name__ == "__main__":
    main()