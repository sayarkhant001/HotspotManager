#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deploy the profile limit-uptime fix (exact time cutoff for every plan)."""
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

    # 1. Upload updated deployer + enforcer + config scripts (session-timeout fix)
    sftp = ssh.open_sftp()
    for fn in ["deploy_v3_to_router.py", "automated_enforcer_v3.py",
               "cloud_config_enforcer.py", "cloud_config_enforcer_v3.py",
               "verify_profiles_full.py", "dryrun_kill_check.py"]:
        sftp.put(os.path.join(BASE, fn), f"{STAGE}/{fn}")
    sftp.close()
    for fn in ["deploy_v3_to_router.py", "automated_enforcer_v3.py",
               "cloud_config_enforcer.py", "cloud_config_enforcer_v3.py",
               "verify_profiles_full.py", "dryrun_kill_check.py"]:
        run(ssh, f"cp -f {STAGE}/{fn} {REMOTE}/{fn}")
    run(ssh, f"grep -c 'session-timeout' {REMOTE}/deploy_v3_to_router.py {REMOTE}/automated_enforcer_v3.py {REMOTE}/cloud_config_enforcer.py {REMOTE}/cloud_config_enforcer_v3.py")

    # 2. Deploy to all routers (sets profile limit-uptime + re-syncs scripts)
    run(ssh, f"cd {REMOTE} && {PY} -u deploy_v3_to_router.py --all")

    # 3. Restart enforcer (clears its _PROFILE_UP_CACHE so it re-reads profile uptimes)
    run(ssh, "sudo -n systemctl restart hotspot-enforcer")
    run(ssh, "sleep 6; systemctl is-active hotspot-enforcer")

    # 4. Verify limit-uptime now set
    run(ssh, f"cd {REMOTE} && {PY} -u vps_check_profiles_full.py")

    # 5. Quantify: how many vouchers now have a provable time limit
    run(ssh, f"cd {REMOTE} && {PY} -u vps_count_no_time.py")

    # 6. Dry-run kill check (should now flag only genuinely-past-deadline vouchers)
    run(ssh, f"cd {REMOTE} && {PY} -u vps_dryrun_kill.py")

    # 7. Force an enforcement sweep so overdue 1-day vouchers are cut EXACTLY now
    run(ssh, f"cd {REMOTE} && {PY} -u vps_repair_longterm.py")

    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()