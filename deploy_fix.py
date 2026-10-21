#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy the long-term-voucher fix to the VPS and live routers.

Step 1: upload updated enforcer + config module + deploy/repair scripts
        (staged under /home/ubuntu/hotfix, then copied into /opt/hotspot-cloud).
Step 2: restart the enforcer + cloud services on the VPS.
Step 3: run `deploy_v3_to_router.py --all` on the VPS (updates router scripts).
Step 4: run vps_repair_longterm.py -> offline-sweep auto-repair.

Run from Windows:  python deploy_fix.py
"""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"

LOCAL_FILES = [
    "automated_enforcer_v3.py",
    "cloud_config_enforcer.py",
    "cloud_config_enforcer_v3.py",
    "deploy_v3_to_router.py",
    "verify_duration_parser.py",
    "vps_repair_longterm.py",
]

BASE = os.path.dirname(os.path.abspath(__file__))


def run(ssh, cmd, timeout=1200):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-5000:])
    if err.strip():
        print("[stderr]", err[-2000:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected to {HOST} as {USER}")

    # ── Step 1: stage + copy files (home is writable, /opt needs sudo) ──
    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    for name in LOCAL_FILES:
        sftp.put(os.path.join(BASE, name), f"{STAGE}/{name}")
        print(f"staged {name}")
    sftp.close()
    run(ssh, f"sudo -n bash -lc 'mkdir -p {REMOTE} && cp -f {STAGE}/*.py {REMOTE}/ && chmod 644 {REMOTE}/*.py'")
    run(ssh, f"ls -l {REMOTE}/automated_enforcer_v3.py {REMOTE}/cloud_config_enforcer.py {REMOTE}/deploy_v3_to_router.py")

    # ── Step 2: discover + restart services ──────────────────────────────
    run(ssh, "systemctl list-units --type=service --all --no-pager | grep -Ei 'enforcer|hotspot|spotlight' || true")
    for svc in ["spotlight-enforcer", "automated-enforcer", "hotspot-cloud"]:
        run(ssh, f"sudo -n systemctl restart {svc} 2>/dev/null || echo 'no such service: {svc}'")
    run(ssh, "systemctl list-units --type=service --no-pager | grep -Ei 'enforcer|hotspot|spotlight' || true")

    # ── Step 3: deploy V3 scripts to all routers ──────────────────────────
    run(ssh, "sudo -n bash -lc 'cd /opt/hotspot-cloud && python3 -u deploy_v3_to_router.py --all'")

    # ── Step 4: offline-sweep auto-repair ─────────────────────────────────
    run(ssh, "sudo -n bash -lc 'cd /opt/hotspot-cloud && python3 -u vps_repair_longterm.py'")

    # ── Sanity: show tail of enforcer log ─────────────────────────────────
    run(ssh, "sudo -n tail -n 40 /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")

    ssh.close()
    print("\nDONE — all three steps executed.")


if __name__ == "__main__":
    main()