import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mikrotik_bridge import get_vps_ssh

def deploy():
    ssh = get_vps_ssh()

    # 1. Update cloud_wireguard.py
    wireguard_py = '''import subprocess
import sqlite3
import time

VPS_ENDPOINT = "3.84.81.152"
VPS_WG_PORT = 51820
VPS_WG_PUBKEY = "geEjgD9DIT4cbBHqR6vF5S9l2DDtyrZzQbon3bMQK2U="

def generate_keypair():
    priv = subprocess.check_output(["wg", "genkey"], text=True).strip()
    pub = subprocess.check_output(["wg", "pubkey"], input=priv, text=True).strip()
    return priv, pub

def get_next_available_ip(db_conn):
    c = db_conn.cursor()
    c.execute("SELECT wg_ip FROM routers")
    used_ips = [row[0] for row in c.fetchall()]
    for i in range(2, 250):
        candidate = f"10.200.0.{i}"
        if candidate not in used_ips:
            return candidate
    raise Exception("No available WireGuard IPs remaining in 10.200.0.0/24")

def register_peer(router_pub_key, router_ip):
    subprocess.run([
        "sudo", "wg", "set", "wg0",
        "peer", router_pub_key,
        "allowed-ips", f"{router_ip}/32"
    ], check=True)

def remove_peer(router_pub_key):
    subprocess.run([
        "sudo", "wg", "set", "wg0",
        "peer", router_pub_key,
        "remove"
    ], check=False)

def get_peer_handshakes():
    """Returns a dict mapping router IP (e.g. '10.200.0.4') and pubkey to handshake epoch timestamp"""
    handshakes = {}
    try:
        out = subprocess.check_output(["sudo", "wg", "show", "wg0", "dump"], text=True)
        lines = out.strip().split("\\n")[1:]
        for line in lines:
            parts = line.split("\\t")
            if len(parts) >= 6:
                pubkey = parts[0]
                allowed_ips = parts[3]
                try:
                    hs_time = int(parts[4])
                except Exception:
                    hs_time = 0
                for ip in allowed_ips.split(","):
                    ip_clean = ip.split("/")[0].strip()
                    if ip_clean:
                        handshakes[ip_clean] = hs_time
                handshakes[pubkey] = hs_time
    except Exception:
        pass
    return handshakes

def generate_mikrotik_rsc(router_name, router_priv_key, router_ip):
    rsc = f"""# ==========================================================
#  WIREGUARD CLOUD TUNNEL FOR ROUTEROS v7: {router_name}
#  Connects router behind Starlink/NAT to Central Management VPS
# ==========================================================

:do {{
  /interface wireguard add name=wg-cloud listen-port=13232 private-key="{router_priv_key}" comment="Hotspot Cloud VPS Tunnel"
}} on-error={{
  /interface wireguard set [find name=wg-cloud] listen-port=13232 private-key="{router_priv_key}"
}}

:do {{
  /ip address add address={router_ip}/24 interface=wg-cloud comment="Cloud VPN Address"
}} on-error={{
  /ip address set [find interface=wg-cloud] address={router_ip}/24
}}

:do {{
  /interface wireguard peers add interface=wg-cloud endpoint-address="{VPS_ENDPOINT}" endpoint-port={VPS_WG_PORT} public-key="{VPS_WG_PUBKEY}" allowed-address=10.200.0.0/24 persistent-keepalive=25s comment="VPS Server Endpoint"
}} on-error={{
  /interface wireguard peers set [find interface=wg-cloud] endpoint-address="{VPS_ENDPOINT}" endpoint-port={VPS_WG_PORT} public-key="{VPS_WG_PUBKEY}" allowed-address=10.200.0.0/24 persistent-keepalive=25s
}}

# Ensure API, SSH and FTP are enabled for remote control
:do {{ /ip service enable [find name="api"] }} on-error={{}}
:do {{ /ip service set [find name="api"] port=8728 }} on-error={{}}
:do {{ /ip service enable [find name="ssh"] }} on-error={{}}
:do {{ /ip service set [find name="ssh"] port=22 }} on-error={{}}
:do {{ /ip service enable [find name="ftp"] }} on-error={{}}

# Allow remote management through WireGuard interface in firewall
:do {{
  /ip firewall filter add chain=input in-interface=wg-cloud action=accept comment="Allow Cloud Remote Management" place-before=0
}} on-error={{}}
:do {{
  /ip firewall filter add chain=input src-address=10.200.0.0/24 action=accept comment="Allow Cloud WireGuard Subnet" place-before=0
}} on-error={{}}

# Instant Handshake & Notification to Cloud Controller
:delay 1s
:do {{
  /ping count=3 10.200.0.1
}} on-error={{}}
:do {{
  /tool fetch url="http://10.200.0.1:8750/api/routers/heartbeat?ip={router_ip}" keep-result=no
}} on-error={{
  :do {{
    /tool fetch url="http://3.84.81.152:8750/api/routers/heartbeat?ip={router_ip}" keep-result=no
  }} on-error={{}}
}}

:put ">>> WireGuard Cloud Tunnel Configured! Router IP is {router_ip} <<<"
"""
    return rsc
'''

    sftp = ssh.open_sftp()
    with sftp.file('/opt/hotspot-cloud/cloud_wireguard.py', 'w') as f:
        f.write(wireguard_py)
    print("cloud_wireguard.py updated")

    # 2. Update cloud_main.py to include heartbeat endpoint & auto-online status check
    stdin, stdout, stderr = ssh.exec_command('cat /opt/hotspot-cloud/cloud_main.py')
    main_code = stdout.read().decode('utf-8')

    # Add heartbeat endpoint and background thread if not present
    if '/api/routers/heartbeat' not in main_code:
        heartbeat_code = '''
# ── Heartbeat & Auto-Online Real-Time Detection ──────────────
@app.get("/api/routers/heartbeat")
@app.post("/api/routers/heartbeat")
def router_heartbeat(ip: Optional[str] = None, request: Request = None):
    conn = db.get_db()
    c = conn.cursor()
    target_ip = ip
    if not target_ip and request and request.client:
        target_ip = request.client.host
    now = int(time.time())
    if target_ip:
        c.execute("UPDATE routers SET status = 'online', last_seen = ? WHERE wg_ip = ?", (now, target_ip))
        conn.commit()
    conn.close()
    return {"status": "ok", "ip": target_ip, "timestamp": now}

import threading

def _background_wg_poller():
    while True:
        try:
            hs_map = wg.get_peer_handshakes()
            now = int(time.time())
            conn = db.get_db()
            c = conn.cursor()
            c.execute("SELECT id, wg_ip, status FROM routers")
            for rid, wg_ip, curr_status in c.fetchall():
                hs = hs_map.get(wg_ip, 0)
                is_on = (hs > 0 and (now - hs) < 180)
                new_status = 'online' if is_on else 'offline'
                if new_status != curr_status or (is_on and hs > 0):
                    c.execute("UPDATE routers SET status = ?, last_seen = ? WHERE id = ?", (new_status, hs if hs > 0 else now, rid))
            conn.commit()
            conn.close()
        except Exception:
            pass
        time.sleep(5)

_poller_thread = threading.Thread(target=_background_wg_poller, daemon=True)
_poller_thread.start()
'''
        # Insert before list_routers
        idx = main_code.find('@app.get("/api/routers")')
        if idx != -1:
            main_code = main_code[:idx] + heartbeat_code + "\n" + main_code[idx:]
        else:
            main_code += "\n" + heartbeat_code

    # In list_routers, make sure it applies real-time handshakes
    old_list_block = '''    for r in rows:
        item = dict(r)
        rid = item["id"]'''

    new_list_block = '''    hs_map = wg.get_peer_handshakes()
    now_ts = int(time.time())
    for r in rows:
        item = dict(r)
        rid = item["id"]
        wg_ip = item.get("wg_ip")
        if wg_ip and wg_ip in hs_map:
            hs = hs_map[wg_ip]
            if hs > 0 and (now_ts - hs) < 180:
                item["status"] = "online"
                item["last_seen"] = hs'''

    if old_list_block in main_code and 'hs_map = wg.get_peer_handshakes()' not in main_code:
        main_code = main_code.replace(old_list_block, new_list_block, 1)

    with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'w') as f:
        f.write(main_code)
    print("cloud_main.py updated with heartbeat & real-time auto-online poller")

    sftp.close()

    # 3. Restart service
    stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart hotspot-cloud')
    print("Restarting service:", stdout.read().decode(), stderr.read().decode())
    ssh.close()
    print("VPS deployment completed successfully!")

if __name__ == '__main__':
    deploy()
