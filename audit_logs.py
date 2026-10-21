#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Post-fix log analytics: exact-time kills vs repairs vs errors."""
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
LOG = "/opt/hotspot-cloud/automated_enforcer.log"


def run(ssh, cmd, timeout=300):
    print(f"\n$ {cmd}")
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
    print("Connected", HOST)

    run(ssh, f"wc -l {LOG}; date -u")
    # Time-based expiries (should all be legit; none since the parse-default fix)
    run(ssh, f"grep -c 'TIME_OVERDUE' {LOG}; echo '-- last 5 TIME_OVERDUE:'; grep 'TIME_OVERDUE' {LOG} | tail -5")
    # Quota-based expiries today (exact cuts)
    run(ssh, f"echo '-- quota kills (total):'; grep -c 'DATA_QUOTA_EXCEEDED' {LOG}")
    run(ssh, f"echo '-- quota kills today:'; grep 'DATA_QUOTA_EXCEEDED' {LOG} | tail -8")
    # Long-term repairs
    run(ssh, f"echo '-- long-term REPAIRED (total):'; grep -c 'REPAIRED' {LOG}; echo '-- last repairs:'; grep 'REPAIRED' {LOG} | tail -3")
    # Errors / failures
    run(ssh, f"echo '-- ERROR lines in last 200 lines:'; tail -200 {LOG} | grep -c 'ERROR'; echo '-- recent errors:'; grep 'ERROR' {LOG} | tail -4")
    # Any user killed but time NOT actually over (guard windows)
    run(ssh, f"echo '-- suspicious: disabled without marker in last 300 lines:'; tail -300 {LOG} | grep -cE 'disabled user'")

    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()