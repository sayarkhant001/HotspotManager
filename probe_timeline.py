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


print("== file mtimes ==")
for fn in ["automated_enforcer_v3.py", "cloud_config_enforcer.py", "cloud_config_enforcer_v3.py"]:
    q(f"stat -c '%y  %n' /opt/hotspot-cloud/{fn}")

print("== daemon starts (log) ==")
q("grep -c 'Starting V3 Automated Enforcer Daemon' /opt/hotspot-cloud/automated_enforcer.log")
q("grep -n 'Starting V3 Automated Enforcer Daemon' /opt/hotspot-cloud/automated_enforcer.log | tail -8")

print("== earliest/latest TIME_OVERDUE by line ==")
q("grep -n 'TIME_OVERDUE' /opt/hotspot-cloud/automated_enforcer.log | head -3")
q("grep -n 'TIME_OVERDUE' /opt/hotspot-cloud/automated_enforcer.log | tail -3")

print("== enforcer PID start time ==")
q("ps -o pid,lstart,cmd -p $(pidof -s python3) 2>/dev/null; systemctl show hotspot-enforcer -p ActiveEnterTimestamp -p MainPID 2>/dev/null")

s.close()
print("OK")