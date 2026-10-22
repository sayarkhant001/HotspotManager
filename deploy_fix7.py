#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy: (1) router scheduler throttling (CPU spike fix), (2) [USED-UP:]/[BASE-UP:]
cumulative uptime tags in all 4 router scripts so the app shows continuous time
since activation instead of only the current session uptime."""
import os
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"
BASE = os.path.dirname(os.path.abspath(__file__))


def run(ssh, cmd, timeout=1800):
    print(f"\n$ {cmd[:130]}")
    if len(cmd) > 130:
        print(f"  [... len={len(cmd)} chars]")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-9000:])
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

    # 1. Upload updated deployer + router-script sources (uptime tags + throttled schedulers)
    sftp = ssh.open_sftp()
    for fn in ["deploy_v3_to_router.py", "cloud_config_enforcer.py", "cloud_config_enforcer_v3.py"]:
        sftp.put(os.path.join(BASE, fn), f"{STAGE}/{fn}")
    sftp.close()
    for fn in ["deploy_v3_to_router.py", "cloud_config_enforcer.py", "cloud_config_enforcer_v3.py"]:
        run(ssh, f"cp -f {STAGE}/{fn} {REMOTE}/{fn}")
    run(ssh, f"grep -c 'USED-UP' {REMOTE}/cloud_config_enforcer.py {REMOTE}/cloud_config_enforcer_v3.py")

    # 2. Deploy to all routers: re-syncs 4 scripts (with uptime tags) AND re-sets
    #    scheduler intervals to 60s/3m (CPU spike fix) + profile session-timeout.
    run(ssh, f"cd {REMOTE} && {PY} -u deploy_v3_to_router.py --all")

    # 3. Restart enforcer so the daemon picks up new script sources for audits
    run(ssh, "sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "sleep 6; systemctl is-active hotspot-enforcer")

    # 4. Verify schedulers are throttled on each online router
    run(ssh, f"cd {REMOTE} && {PY} -u vps_check_schedulers.py")

    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()