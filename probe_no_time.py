import paramiko

k = paramiko.RSAKey.from_private_key_file(r"C:\Users\localhost\Downloads\mikrotik.pem")
s = paramiko.SSHClient()
s.set_missing_host_key_policy(paramiko.AutoAddPolicy())
s.connect("3.84.81.152", username="ubuntu", pkey=k, timeout=25)


def q(cmd):
    i, o, e = s.exec_command(cmd)
    out = o.read().decode(errors="replace")
    err = e.read().decode(errors="replace")
    print(out.strip())
    if err.strip():
        print("[stderr]", err.strip()[:400])


REMOTE = r'''#!/usr/bin/env python3
import sys
sys.path.append('/opt/hotspot-cloud')
from cloud_mikrotik import RouterOSClient

def parse_re(r):
    res = {}
    for item in r[1:]:
        if item.startswith('='):
            p = item[1:].split('=', 1)
            if len(p) == 2: res[p[0]] = p[1]
    return res

PROF_DUR_KEY = ['30d','30day','4w','7d','1w','3d','1d','24h','12h','6h','2h','1h','1hour','1month','50gb','40g']

for ip, nm in [('10.200.0.2','YadanarTun'), ('10.200.0.3','AYeikSitt1'), ('10.200.0.4','KyawGyi')]:
    try:
        c = RouterOSClient(ip, 8728, timeout=12)
        c.connect()
        if not c.login('admin','Khant1234@'):
            print(nm, 'AUTHFAIL'); continue
        users = [parse_re(x) for x in c.talk(['/ip/hotspot/user/print','=.proplist=name,profile,comment,disabled']) if x[0]=='!re']
        en = [u for u in users if u.get('disabled') != 'true' and u.get('name') not in ('admin','default-trial')]
        with_tag = 0; name_decoded = 0; no_time = 0
        for u in en:
            c0 = (u.get('comment') or '').lower()
            p0 = (u.get('profile') or '').lower()
            if '[orig-up:' in c0:
                with_tag += 1
            elif any(k in p0 or k in c0 for k in PROF_DUR_KEY):
                name_decoded += 1
            else:
                no_time += 1
        print(f"{nm}: enabled={len(en)}  [ORIG-UP:]tag={with_tag}  name-encoded={name_decoded}  NO-TIME-LIMIT(quota only)={no_time}")
        c.close()
    except Exception as e:
        print(nm, 'ERR', e)
print('DONE')
'''

sftp = s.open_sftp()
with sftp.open("/home/ubuntu/hotfix/vps_count_no_time.py", "w") as f:
    f.write(REMOTE)
sftp.close()
q("cp -f /home/ubuntu/hotfix/vps_count_no_time.py /opt/hotspot-cloud/vps_count_no_time.py")
q("cd /opt/hotspot-cloud && /opt/hotspot-cloud/venv/bin/python -u vps_count_no_time.py")
s.close()
print("OK")