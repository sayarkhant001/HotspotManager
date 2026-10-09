#!/usr/bin/env python3
"""
MikroTik Unified Remote Controller & Agent Bridge
=================================================
Provides full control and automated interaction with connected MikroTik routers
via the AWS VPS WireGuard tunnels (10.200.0.x) or direct SSH / RouterOS API.

Usage Examples:
  # 1. List all routers connected to the cloud controller
  python mikrotik_bridge.py list

  # 2. Run any command on a router via VPS
  python mikrotik_bridge.py cmd --router 3 --exec "/system resource print"
  python mikrotik_bridge.py cmd --router 3 --exec "/ip hotspot user print without-paging"

  # 3. Deploy an RSC configuration script
  python mikrotik_bridge.py rsc --router 3 --file my_script.rsc

  # 4. Direct SSH / API connection with custom credentials
  python mikrotik_bridge.py cmd --host 192.168.1.164 --user admin --pass Khant1234@ --exec "/interface print"

  # 5. IP Binding / Device Bypass (No captive portal login required)
  python mikrotik_bridge.py bypass --router 3 --mac 14:AB:C5:11:22:33 --comment "Manager Laptop"

  # 6. View connected DHCP devices on WiFi
  python mikrotik_bridge.py dhcp --router 3

  # 7. View router logs
  python mikrotik_bridge.py logs --router 3

  # 8. Reboot router
  python mikrotik_bridge.py reboot --router 3
"""

import sys
import os
import argparse
import json
import time
import paramiko

VPS_HOST = "3.84.81.152"
VPS_USER = "ubuntu"
VPS_KEY_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mikrotik.pem")
if not os.path.exists(VPS_KEY_PATH):
    # Fallback to Downloads
    VPS_KEY_PATH = r"C:\Users\localhost\Downloads\mikrotik.pem"

def get_vps_ssh():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(VPS_HOST, username=VPS_USER, key_filename=VPS_KEY_PATH, timeout=10)
    return ssh

def execute_on_vps_python(py_code, timeout=60):
    ssh = get_vps_ssh()
    stdin, stdout, stderr = ssh.exec_command("/opt/hotspot-cloud/venv/bin/python3 -", timeout=timeout)
    stdin.write(py_code)
    stdin.channel.shutdown_write()
    out = stdout.read().decode('utf-8')
    err = stderr.read().decode('utf-8')
    ssh.close()
    if err and not out:
        raise RuntimeError(f"VPS Python Error: {err}")
    return out

def list_routers():
    """Lists all routers from the VPS database."""
    py_code = """
import sqlite3, json
conn = sqlite3.connect('/opt/hotspot-cloud/data.db')
c = conn.cursor()
c.execute("SELECT id, name, location, wg_ip, api_port, api_user, api_pass, status, last_seen FROM routers")
rows = c.fetchall()
conn.close()
res = []
for r in rows:
    res.append({
        'id': r[0], 'name': r[1], 'location': r[2], 'wg_ip': r[3],
        'api_port': r[4], 'api_user': r[5], 'api_pass': r[6],
        'status': r[7], 'last_seen': r[8]
    })
print(json.dumps(res))
"""
    raw = execute_on_vps_python(py_code)
    try:
        return json.loads(raw.strip())
    except Exception:
        print("Raw output:", raw)
        return []

def get_router_by_id(router_id):
    routers = list_routers()
    for r in routers:
        if r['id'] == int(router_id):
            return r
    return None

def run_router_command(command, router_id=None, host=None, user='admin', password=''):
    """Executes a command on the target router via API or SSH."""
    if router_id:
        r = get_router_by_id(router_id)
        if not r:
            return f"Error: Router ID {router_id} not found."
        host = r['wg_ip']
        user = r['api_user']
        password = r['api_pass']

    if not host or not password:
        return "Error: Missing router connection details (host or password)."

    py_code = f"""
import sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

out = cloud_mikrotik.RouterOSClient.execute_cli_command(
    host={json.dumps(host)},
    user={json.dumps(user)},
    password={json.dumps(password)},
    command={json.dumps(command)},
    timeout=15
)
print(out)
"""
    return execute_on_vps_python(py_code).strip()

def run_rsc_script(rsc_content, router_id=None, host=None, user='admin', password=''):
    """Uploads and runs an RSC script on the target router via FTP and /import."""
    if router_id:
        r = get_router_by_id(router_id)
        if not r:
            return f"Error: Router ID {router_id} not found."
        host = r['wg_ip']
        user = r['api_user']
        password = r['api_pass']

    py_code = f"""
import ftplib, io, time, sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

filename = f"agent_exec_{{int(time.time())}}.rsc"

# 1. Upload via FTP
try:
    ftp = ftplib.FTP()
    ftp.connect({json.dumps(host)}, 21, timeout=8)
    ftp.login({json.dumps(user)}, {json.dumps(password)})
    ftp.storbinary(f'STOR {{filename}}', io.BytesIO({json.dumps(rsc_content)}.encode('utf-8')))
    ftp.quit()
except Exception as e:
    print(f"FTP Upload Failed: {{e}}")
    sys.exit(0)

# 2. Execute /import
client = cloud_mikrotik.RouterOSClient({json.dumps(host)}, timeout=30)
client.connect()
if client.login({json.dumps(user)}, {json.dumps(password)}):
    res = client.talk(['/import', f'=file-name={{filename}}'])
    
    # 3. Cleanup file
    try:
        files = client.talk(['/file/print', f'?name={{filename}}'])
        for f in files:
            if f[0] == '!re':
                for it in f[1:]:
                    if it.startswith('=.id='):
                        client.talk(['/file/remove', f'=.id={{it.split("=", 2)[2]}}'])
    except Exception:
        pass
    client.close()
    print("Execution Success: " + str(res))
else:
    client.close()
    print("API Login Failed")
"""
    return execute_on_vps_python(py_code).strip()

def check_updates(router_id):
    """Checks for RouterOS package updates and RouterBOOT firmware."""
    r = get_router_by_id(router_id)
    if not r:
        return {}
    py_code = f"""
import sys, json
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

c = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=10)
c.connect()
if c.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])}):
    c.talk(['/system/package/update/check-for-updates'])
    up = c.talk(['/system/package/update/print'])
    up_data = {{}}
    for row in up:
        if row[0] == '!re':
            for it in row[1:]:
                if '=' in it:
                    k, v = it.lstrip('=').split('=', 1)
                    up_data[k] = v
    rb = c.talk(['/system/routerboard/print'])
    rb_data = {{}}
    for row in rb:
        if row[0] == '!re':
            for it in row[1:]:
                if '=' in it:
                    k, v = it.lstrip('=').split('=', 1)
                    rb_data[k] = v
    c.close()
    print(json.dumps({{'packages': up_data, 'routerboard': rb_data}}))
else:
    c.close()
    print("{{}}")
"""
    raw = execute_on_vps_python(py_code).strip()
    try:
        return json.loads(raw)
    except Exception:
        return {}

def install_router_update(router_id):
    """Triggers RouterOS package download and upgrade with automatic reboot."""
    r = get_router_by_id(router_id)
    if not r:
        return "Router not found"
    py_code = f"""
import sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

c = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=10)
try:
    c.connect()
    c.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])})
    c.talk(['/system/package/update/install'])
    c.close()
    print("Install Dispatched (Router is downloading updates and will reboot automatically)")
except Exception as e:
    print(f"Dispatched: {{e}}")
"""
    return execute_on_vps_python(py_code).strip()

def upgrade_routerboard(router_id):
    """Upgrades RouterBOOT hardware firmware."""
    r = get_router_by_id(router_id)
    if not r:
        return "Router not found"
    py_code = f"""
import sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

c = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=10)
c.connect()
if c.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])}):
    c.talk(['/system/routerboard/upgrade'])
    c.close()
    print("RouterBOOT firmware upgraded! Will take effect upon next reboot.")
else:
    c.close()
    print("API Login failed")
"""
    return execute_on_vps_python(py_code).strip()

def audit_router(router_id, auto_fix=True):
    """Audits router economic policies and power-cut persistence via VPS enforcer."""
    py_code = f"""
import sys, json
sys.path.append('/opt/hotspot-cloud')
import cloud_config_enforcer
res = cloud_config_enforcer.audit_router_by_id({router_id}, auto_fix={auto_fix})
print(json.dumps(res))
"""
    raw = execute_on_vps_python(py_code)
    try:
        return json.loads(raw.strip())
    except Exception:
        return {"raw": raw}

def audit_all_routers(auto_fix=True):
    """Audits all online routers for economic policies and power-cut persistence."""
    py_code = f"""
import sys, json
sys.path.append('/opt/hotspot-cloud')
import cloud_config_enforcer
res = cloud_config_enforcer.audit_all_online_routers(auto_fix={auto_fix})
print(json.dumps(res))
"""
    raw = execute_on_vps_python(py_code)
    try:
        return json.loads(raw.strip())
    except Exception:
        return {"raw": raw}

def add_ip_binding(mac_address="", address="", binding_type="bypassed", comment="", router_id=None):
    """Adds an IP binding (bypassed, regular, blocked) to RouterOS."""
    if router_id:
        r = get_router_by_id(router_id)
        if not r:
            return f"Error: Router ID {router_id} not found."
        host = r['wg_ip']
        user = r['api_user']
        password = r['api_pass']

    py_code = f"""
import sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

client = cloud_mikrotik.RouterOSClient({json.dumps(host)}, timeout=10)
client.connect()
if client.login({json.dumps(user)}, {json.dumps(password)}):
    ok = client.add_ip_binding(
        mac_address={json.dumps(mac_address)},
        address={json.dumps(address)},
        binding_type={json.dumps(binding_type)},
        comment={json.dumps(comment)}
    )
    client.close()
    print("OK" if ok else "FAILED")
else:
    client.close()
    print("LOGIN_FAILED")
"""
    return execute_on_vps_python(py_code).strip()

def get_dhcp_leases(router_id):
    """Fetches DHCP leases from target router."""
    r = get_router_by_id(router_id)
    if not r:
        return []
    py_code = f"""
import sys, json
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

client = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=10)
client.connect()
if client.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])}):
    leases = client.get_dhcp_leases()
    client.close()
    print(json.dumps(leases))
else:
    client.close()
    print("[]")
"""
    raw = execute_on_vps_python(py_code)
    try:
        return json.loads(raw.strip())
    except Exception:
        return []

def get_logs(router_id, limit=50):
    """Fetches recent logs from target router."""
    r = get_router_by_id(router_id)
    if not r:
        return []
    py_code = f"""
import sys, json
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

client = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=10)
client.connect()
if client.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])}):
    logs = client.get_logs(limit={limit})
    client.close()
    print(json.dumps(logs))
else:
    client.close()
    print("[]")
"""
    raw = execute_on_vps_python(py_code)
    try:
        return json.loads(raw.strip())
    except Exception:
        return []

def reboot_router(router_id):
    """Reboots target router."""
    r = get_router_by_id(router_id)
    if not r:
        return "Router not found"
    py_code = f"""
import sys
sys.path.append('/opt/hotspot-cloud')
import cloud_mikrotik

client = cloud_mikrotik.RouterOSClient({json.dumps(r['wg_ip'])}, timeout=5)
try:
    client.connect()
    client.login({json.dumps(r['api_user'])}, {json.dumps(r['api_pass'])})
    client.reboot()
    client.close()
    print("Reboot Dispatched")
except Exception as e:
    print("Reboot Dispatched")
"""
    return execute_on_vps_python(py_code).strip()

def main():
    parser = argparse.ArgumentParser(description="MikroTik Remote Controller & Agent Bridge")
    subparsers = parser.add_subparsers(dest="command")

    # list
    subparsers.add_parser("list", help="List all connected routers")

    # cmd
    cmd_p = subparsers.add_parser("cmd", help="Run a CLI command on a router")
    cmd_p.add_argument("--router", type=int, help="Router ID in database")
    cmd_p.add_argument("--host", help="Direct IP / hostname")
    cmd_p.add_argument("--user", default="admin", help="Router user")
    cmd_p.add_argument("--pass", dest="password", default="", help="Router password")
    cmd_p.add_argument("--exec", required=True, help="Command to execute")

    # rsc
    rsc_p = subparsers.add_parser("rsc", help="Deploy an RSC script to a router")
    rsc_p.add_argument("--router", type=int, required=True, help="Router ID")
    rsc_p.add_argument("--file", required=True, help="Path to RSC file")

    # bypass
    byp_p = subparsers.add_parser("bypass", help="Add IP binding bypass (no login needed)")
    byp_p.add_argument("--router", type=int, required=True, help="Router ID")
    byp_p.add_argument("--mac", required=True, help="MAC address")
    byp_p.add_argument("--ip", default="", help="IP address (optional)")
    byp_p.add_argument("--comment", default="Agent Bypassed Device", help="Comment")

    # dhcp
    dhcp_p = subparsers.add_parser("dhcp", help="View connected DHCP devices")
    dhcp_p.add_argument("--router", type=int, required=True, help="Router ID")

    # logs
    log_p = subparsers.add_parser("logs", help="View recent router logs")
    log_p.add_argument("--router", type=int, required=True, help="Router ID")
    log_p.add_argument("--limit", type=int, default=50, help="Number of log entries")

    # reboot
    reb_p = subparsers.add_parser("reboot", help="Reboot router")
    reb_p.add_argument("--router", type=int, required=True, help="Router ID")

    # check-updates
    chk_p = subparsers.add_parser("check-updates", help="Check for RouterOS and firmware updates")
    chk_p.add_argument("--router", type=int, required=True, help="Router ID")

    # upgrade
    upg_p = subparsers.add_parser("upgrade", help="Download and install RouterOS update (auto-reboot)")
    upg_p.add_argument("--router", type=int, required=True, help="Router ID")

    # audit
    aud_p = subparsers.add_parser("audit", help="Audit and enforce economic & power-cut configs on router")
    aud_p.add_argument("--router", type=int, required=True, help="Router ID")
    aud_p.add_argument("--no-fix", action="store_true", help="Audit only without applying fixes")

    # audit-all
    aud_all_p = subparsers.add_parser("audit-all", help="Audit and enforce configs on all online routers")
    aud_all_p.add_argument("--no-fix", action="store_true", help="Audit only without applying fixes")

    args = parser.parse_args()

    if args.command == "list":
        routers = list_routers()
        print(f"\n{'ID':<4} {'NAME':<24} {'LOCATION':<16} {'WG IP':<14} {'STATUS':<10}")
        print("-" * 72)
        for r in routers:
            print(f"{r['id']:<4} {r['name']:<24} {r['location'] or '-':<16} {r['wg_ip']:<14} {r['status']:<10}")
        print()

    elif args.command == "cmd":
        out = run_router_command(args.exec, router_id=args.router, host=args.host, user=args.user, password=args.password)
        print(out)

    elif args.command == "rsc":
        if not os.path.exists(args.file):
            print(f"Error: File '{args.file}' not found.")
            sys.exit(1)
        with open(args.file, 'r', encoding='utf-8') as f:
            content = f.read()
        res = run_rsc_script(content, router_id=args.router)
        print("RSC Deployment Result:\n", res)

    elif args.command == "bypass":
        res = add_ip_binding(mac_address=args.mac, address=args.ip, binding_type="bypassed", comment=args.comment, router_id=args.router)
        print("Bypass Result:", res)

    elif args.command == "dhcp":
        leases = get_dhcp_leases(args.router)
        print(f"\n{'IP ADDRESS':<16} {'MAC ADDRESS':<18} {'HOSTNAME':<22} {'STATUS':<10}")
        print("-" * 70)
        for l in leases:
            print(f"{l.get('address', '-'):<16} {l.get('mac-address', '-'):<18} {l.get('host-name', '-'):<22} {l.get('status', '-'):<10}")
        print()

    elif args.command == "logs":
        logs = get_logs(args.router, limit=args.limit)
        print(f"\n{'TIME':<12} {'TOPICS':<18} {'MESSAGE'}")
        print("-" * 80)
        for l in logs:
            print(f"{l.get('time', '-'):<12} {l.get('topics', '-'):<18} {l.get('message', '')}")
        print()

    elif args.command == "reboot":
        res = reboot_router(args.router)
        print(res)

    elif args.command == "check-updates":
        res = check_updates(args.router)
        pkg = res.get('packages', {})
        rb = res.get('routerboard', {})
        print("\n=== ROUTEROS PACKAGE STATUS ===")
        print(f"Channel:           {pkg.get('channel', 'stable')}")
        print(f"Installed Version: {pkg.get('installed-version', 'unknown')}")
        print(f"Latest Version:    {pkg.get('latest-version', 'unknown')}")
        print(f"Status:            {pkg.get('status', 'unknown')}")
        print("\n=== ROUTERBOOT HARDWARE FIRMWARE ===")
        print(f"Model:             {rb.get('model', 'unknown')}")
        print(f"Current Firmware:  {rb.get('current-firmware', 'unknown')}")
        print(f"Upgrade Firmware:  {rb.get('upgrade-firmware', 'unknown')}")
        upgradable = rb.get('upgrade-firmware') and rb.get('upgrade-firmware') != rb.get('current-firmware')
        print(f"Firmware Upgrade:  {'AVAILABLE (Run: upgrade-boot)' if upgradable else 'UP TO DATE'}\n")

    elif args.command == "upgrade":
        res = install_router_update(args.router)
        print(res)

    elif args.command == "upgrade-boot":
        res = upgrade_routerboard(args.router)
        print(res)

    elif args.command == "audit":
        res = audit_router(args.router, auto_fix=(not args.no_fix))
        print(json.dumps(res, indent=2))

    elif args.command == "audit-all":
        res = audit_all_routers(auto_fix=(not args.no_fix))
        print(json.dumps(res, indent=2))

    else:
        parser.print_help()

if __name__ == "__main__":
    main()
