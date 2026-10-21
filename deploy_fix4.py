#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix the enforcer crash (missing deploy_theme.py + portal_templates on VPS),
restart hotspot-enforcer, re-run the auto-repair sweep, retry KyawGyi deploy."""
import os
import posixpath
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
BASE = os.path.dirname(os.path.abspath(__file__))
PY = f"{REMOTE}/venv/bin/python"


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


def put_dir(sftp, local_dir, remote_dir):
    for root, dirs, files in os.walk(local_dir):
        rel = os.path.relpath(root, local_dir)
        target = remote_dir if rel == "." else posixpath.join(remote_dir, rel.replace("\\", "/"))
        try:
            sftp.mkdir(target)
        except Exception:
            pass
        for f in files:
            if f.startswith("."):
                continue
            sftp.put(os.path.join(root, f), posixpath.join(target, f))
            print("  uploaded", posixpath.join(target, f))


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print(f"Connected {HOST}")

    # 1. upload deploy_theme.py + portal_templates (the enforcer imports deploy_theme)
    sftp = ssh.open_sftp()
    sftp.put(os.path.join(BASE, "deploy_theme.py"), f"{STAGE}/deploy_theme.py")
    print("staged deploy_theme.py")
    put_dir(sftp, os.path.join(BASE, "portal_templates"), f"{STAGE}/portal_templates")
    sftp.close()
    run(ssh, f"cp -f {STAGE}/deploy_theme.py {REMOTE}/deploy_theme.py && mkdir -p {REMOTE}/portal_templates && cp -rf {STAGE}/portal_templates/. {REMOTE}/portal_templates/ && chmod 644 {REMOTE}/deploy_theme.py")
    run(ssh, f"ls {REMOTE}/portal_templates | head; ls {REMOTE}/deploy_theme.py")

    # 2. restart the enforcer service via sudo (works on this box)
    run(ssh, "sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "sleep 8; systemctl is-active hotspot-enforcer; systemctl status hotspot-enforcer --no-pager | head -6")

    # 3. re-run the offline-sweep auto-repair with the venv python
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u vps_repair_longterm.py'")

    # 4. retry KyawGyi (10.200.0.5) router deploy separately
    run(ssh, f"bash -lc 'cd {REMOTE} && timeout 240 {PY} -u deploy_v3_to_router.py 10.200.0.5 || echo ROUTER5_SKIPPED'")

    # 5. verification
    run(ssh, "sleep 5; systemctl is-active hotspot-enforcer")
    run(ssh, "grep -c 'REPAIRED long-term' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")
    run(ssh, "tail -n 40 /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")

    ssh.close()
    print("\nDONE — enforcer healthy, repair sweep executed, KyawGyi redeployed.")


if __name__ == "__main__":
    main()