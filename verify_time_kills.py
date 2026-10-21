#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify the recent TIME_OVERDUE kills on AYeikSitt1 were LEGITIMATE time
expirations (deadline = ACT + provable validity actually passed)."""
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"

REMOTE_SCRIPT = r'''#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient
from datetime import datetime

def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

c = RouterOSClient('10.200.0.3', 8728, timeout=12)
c.connect()
if not c.login('admin', 'Khant1234@'):
    print('AUTHFAIL'); sys.exit(1)

clock = [parse_re(x) for x in c.talk(['/system/clock/print']) if x[0] == '!re']
router_now = datetime.strptime(f"{clock[0].get('date','')} {clock[0].get('time','')}", "%Y-%m-%d %H:%M:%S")
print('ROUTER NOW (UTC off):', router_now)

# profiles with their limit-uptime (authoritative validity fallback)
profs = {}
for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,limit-uptime,rate-limit']):
    if x[0] == '!re':
        d = parse_re(x); profs[d.get('name','')] = d.get('limit-uptime','?')

# the users that were TIME_OVERDUE-killed, with kill timestamps from the log
targets = [
    ('078722', '2026-10-21 06:34:40'),
    ('745012', '2026-10-21 06:57:55'),
    ('351131', '2026-10-21 07:13:28'),
    ('739354', '2026-10-21 07:29:59'),
    ('318887', '2026-10-21 07:44:49'),
]
for uname, kill_ts in targets:
    try:
        rows = [parse_re(x) for x in c.talk(['/ip/hotspot/user/print', f'?name={uname}', '=.proplist=comment,profile,disabled,limit-bytes-total,limit-uptime']) if x[0] == '!re']
        if not rows:
            print(f"{uname}: GONE (pruned)")
            continue
        u = rows[0]
        comm = u.get('comment', '')
        act = ''
        if '[ACT:' in comm:
            act = comm.split('[ACT:')[1].split(']')[0].strip()
        orig_up = ''
        if '[ORIG-UP:' in comm:
            orig_up = comm.split('[ORIG-UP:')[1].split(']')[0].strip()
        print("-----")
        print(f"{uname}: killed@ {kill_ts}")
        print(f"  profile={u.get('profile')} (limit-uptime={profs.get(u.get('profile',''), '?')}) disabled={u.get('disabled')}")
        print(f"  [ACT:]={act}  [ORIG-UP:]={orig_up}")
        print(f"  comment={comm[:160]}")
        if act:
            try:
                adt = datetime.strptime(act, "%Y-%m-%d %H:%M:%S") if '-' in act else datetime.strptime(act, "%b/%d/%Y %H:%M:%S")
                kdt = datetime.strptime(kill_ts, "%Y-%m-%d %H:%M:%S")
                print(f"  elapsed-at-kill = {kdt - adt}")
            except Exception as e:
                print(f"  act parse err: {e}")
    except Exception as e:
        print(f"{uname}: ERR {e}")
c.close()
print('DONE')
'''

def run(ssh, cmd, timeout=600):
    print(f"\n$ {cmd[:130]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-8000:])
    if err.strip():
        print("[stderr]", err[-1200:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    with sftp.open(f"{STAGE}/vps_check_time_kills.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_check_time_kills.py {REMOTE}/vps_check_time_kills.py")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_check_time_kills.py")
    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()