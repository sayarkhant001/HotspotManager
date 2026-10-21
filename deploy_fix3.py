#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Redeploy the final enforcer (with profile-uptime fallback), restart
hotspot-enforcer, deploy router scripts, and run the auto-repair sweep using
the SAME venv python the service uses (run as ubuntu — no sudo needed)."""
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
    print(f"Connected {HOST}")

    # 1. stage + copy the updated enforcer (profile-uptime fallback included)
    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    sftp.put(os.path.join(os.path.dirname(os.path.abspath(__file__)), "automated_enforcer_v3.py"), f"{STAGE}/automated_enforcer_v3.py")
    sftp.close()
    run(ssh, f"cp -f {STAGE}/automated_enforcer_v3.py {REMOTE}/automated_enforcer_v3.py && chmod 644 {REMOTE}/automated_enforcer_v3.py")
    run(ssh, f"grep -c 'resolve_dur' {REMOTE}/automated_enforcer_v3.py")
    run(ssh, f"ls -la {REMOTE}/automated_enforcer_v3.py")

    # 2. quick sanity: venv python can import the fixed module for this file's syntax
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -m py_compile automated_enforcer_v3.py && echo COMPILE_OK'")

    # 3. restart the ACTUAL enforcer service (runs as ubuntu with venv python)
    run(ssh, "systemctl restart hotspot-enforcer --no-block 2>&1 || sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "sleep 5; systemctl is-active hotspot-enforcer; systemctl status hotspot-enforcer --no-pager | head -6")

    # 4. deploy V3 scripts to all routers (needs venv python for paramiko)
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u deploy_v3_to_router.py --all'")

    # 5. trigger offline-sweep auto-repair with the venv python (as ubuntu)
    run(ssh, f"bash -lc 'cd {REMOTE} && {PY} -u vps_repair_longterm.py'")

    # 6. verify: fresh log entries with REPAIRED / the sweep running under new code
    run(ssh, "sleep 3; tail -n 60 /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null | grep -E 'REPAIRED|Time check|time-expired|wrongly' || true")
    run(ssh, "grep -c 'REPAIRED long-term' /opt/hotspot-cloud/automated_enforcer.log 2>/dev/null || true")

    ssh.close()
    print("\nDONE — final enforcer deployed, service restarted, router scripts updated, auto-repair triggered.")


if __name__ == "__main__":
    main()