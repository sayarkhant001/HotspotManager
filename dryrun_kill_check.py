#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DRY-RUN: run the DEPLOYED enforcer logic (imported from the VPS install)
against every enabled user on each online router and list exactly which
vouchers the current code would time-kill / time-guess. Zero state changes."""
import paramiko

HOST = "3.84.81.152"
USER = "ubuntu"
KEY = r"C:\Users\localhost\Downloads\mikrotik.pem"
REMOTE = "/opt/hotspot-cloud"
STAGE = "/home/ubuntu/hotfix"
PY = f"{REMOTE}/venv/bin/python"

REMOTE_SCRIPT = r'''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import sys
from datetime import datetime, timedelta
sys.path.append('/opt/hotspot-cloud')
import importlib
import automated_enforcer_v3 as ae    # the DEPLOYED module
from cloud_mikrotik import RouterOSClient

def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

routers = [('10.200.0.2', 'YadanarTun'), ('10.200.0.3', 'AYeikSitt1'),
           ('10.200.0.4', 'KyawGyi')]

for ip, nm in routers:
    print(f"=== {nm} {ip} ===")
    try:
        c = RouterOSClient(ip, 8728, timeout=12)
        c.connect()
        if not c.login('admin', 'Khant1234@'):
            print("  AUTHFAIL"); continue
        clock = [parse_re(x) for x in c.talk(['/system/clock/print']) if x[0] == '!re']
        try:
            router_now = datetime.strptime(f"{clock[0].get('date','')} {clock[0].get('time','')}", "%Y-%m-%d %H:%M:%S")
        except Exception:
            router_now = datetime.now()
        print(f"  router_now={router_now}")
        profs = {p['name']: p.get('session-timeout','') for p in
                 [parse_re(x) for x in c.talk(['/ip/hotspot/user/profile/print', '=.proplist=name,session-timeout']) if x[0]=='!re']}

        def resolve_dur_dry(prof, comm):
            d = ae.parse_duration(prof, comm)
            if d is None and prof:
                up = profs.get(prof, '')
                d = ae.parse_ros_duration(up) if up else None
            return d

        users = [parse_re(x) for x in c.talk(['/ip/hotspot/user/print', '=.proplist=name,profile,comment,disabled,bytes-in,bytes-out,limit-bytes-total']) if x[0]=='!re']
        flagged = 0
        for u in users:
            if u.get('disabled') == 'true': continue
            np_ = u.get('name','')
            if np_ in ('admin', 'default-trial'): continue
            comm = u.get('comment','') or ''
            prof = u.get('profile','')
            dur = None
            if '[ACT:' in comm:
                try:
                    act_s = comm.split('[ACT:')[1].split(']')[0].strip()
                    act_dt = datetime.strptime(act_s, "%Y-%m-%d %H:%M:%S") if '-' in act_s else datetime.strptime(act_s, "%b/%d/%Y %H:%M:%S")
                except Exception:
                    act_dt = None
                dur = resolve_dur_dry(prof, comm)
                if act_dt and dur is not None and router_now >= (act_dt + dur):
                    print(f"  !WOULD-KILL {np_}: prof={prof} parsed_dur={dur} act={act_s} elapsed={router_now-act_dt}")
                    flagged += 1
        print(f"  enabled users with ACT checked -> {flagged} would time-kill")
        # Also: how many enabled used long-term-ish plans have NO time resolution at all
        unknown = 0
        for u in users:
            if u.get('disabled') == 'true': continue
            comm = u.get('comment','') or ''
            prof = u.get('profile','')
            if '[ACT:' in comm and resolve_dur_dry(prof, comm) is None:
                unknown += 1
        print(f"  enabled users with [ACT:] but UNRESOLVABLE duration (never time-killed): {unknown}")
        c.close()
    except Exception as e:
        print(f"  ERR {e}")
print('DONE')
'''

def run(ssh, cmd, timeout=900):
    print(f"\n$ {cmd[:130]}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out[-12000:])
    if err.strip():
        print("[stderr]", err[-1500:])
    print(f"  -> rc={rc}")
    return rc


def main():
    key = paramiko.RSAKey.from_private_key_file(KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, pkey=key, timeout=30)
    print("Connected", HOST)
    run(ssh, f"mkdir -p {STAGE}")
    sftp = ssh.open_sftp()
    with sftp.open(f"{STAGE}/vps_dryrun_kill.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()
    run(ssh, f"cp -f {STAGE}/vps_dryrun_kill.py {REMOTE}/vps_dryrun_kill.py")
    run(ssh, f"cd {REMOTE} && {PY} -u vps_dryrun_kill.py")
    ssh.close()
    print("\nDONE")


if __name__ == "__main__":
    main()