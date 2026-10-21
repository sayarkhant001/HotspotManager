#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Patient retry of the V3 deploy to KyawGyi (10.200.0.5)."""
import time
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
PY = "/opt/hotspot-cloud/venv/bin/python"

CMD = "bash -lc 'cd /opt/hotspot-cloud && sleep 30 && " + PY + " -u deploy_v3_to_router.py 10.200.0.5 || echo ROUTER5_FAILED'"


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=45)
    print("Connected; waiting 30s for KyawGyi API, then deploying...")
    stdin, stdout, stderr = ssh.exec_command(CMD, timeout=1500)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    print(out)
    if err.strip():
        print("[stderr]", err[-1500:])
    ssh.close()
    print("DONE")


if __name__ == "__main__":
    main()