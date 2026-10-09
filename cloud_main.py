import cloud_config_enforcer
import zipfile
import io
import base64
import os
import time
import secrets
import hashlib
import sqlite3
from typing import Optional, List
from fastapi import FastAPI, Depends, HTTPException, status, Request
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from jose import JWTError, jwt

import cloud_database as db
import cloud_mikrotik as mk
import cloud_wireguard as wg
import cloud_portal as portal

SECRET_KEY = "hotspot-cloud-super-secret-key-yadanartun-vps"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7 # 7 days

app = FastAPI(title="Hotspot Cloud Controller")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Auth Models & Helpers ─────────────────────────────────────
class LoginRequest(BaseModel):
    username: str
    password: str

class CustomerCreateRequest(BaseModel):
    username: str
    password: str
    full_name: str
    phone: Optional[str] = ""

class CustomerUpdateRequest(BaseModel):
    username: Optional[str] = None
    full_name: Optional[str] = None
    phone: Optional[str] = None
    password: Optional[str] = None

class AdminCreateRequest(BaseModel):
    username: str
    password: str
    full_name: str
    phone: Optional[str] = ""

class AdminProfileUpdateRequest(BaseModel):
    username: Optional[str] = None
    full_name: Optional[str] = None
    phone: Optional[str] = None
    old_password: Optional[str] = None
    new_password: Optional[str] = None

class RouterCreateRequest(BaseModel):
    name: str
    location: Optional[str] = ""
    user_id: Optional[int] = None # Admin can assign to customer
    api_user: Optional[str] = "admin"
    api_pass: Optional[str] = "Khant1234@"
    api_port: Optional[int] = 8728
    ftp_port: Optional[int] = 21

class VoucherGenRequest(BaseModel):
    router_id: int
    count: int = 1
    code_length: int = 6
    code_type: str = "numeric" # "numeric" or "alphanumeric"
    quota_mb: int = 0
    uptime_str: str = "1d"
    price_mmk: int = 0


class HotspotProfileUpdateRequest(BaseModel):
    rate_limit: Optional[str] = None
    shared_users: Optional[str] = None
    session_timeout: Optional[str] = None
    mac_cookie_timeout: Optional[str] = None

class LiveVoucherUpdateRequest(BaseModel):
    password: Optional[str] = None
    profile: Optional[str] = None
    quota_mb: Optional[int] = None
    uptime_str: Optional[str] = None
    comment: Optional[str] = None

class RouterIdentityRequest(BaseModel):
    name: str

class IpBindingCreateRequest(BaseModel):
    mac_address: Optional[str] = ""
    address: Optional[str] = ""
    type: Optional[str] = "bypassed"
    comment: Optional[str] = ""

class HotspotProfileCreateRequest(BaseModel):
    name: str
    rate_limit: str = "10M/10M"
    shared_users: str = "1"
    session_timeout: str = "1d"
    mac_cookie_timeout: str = "4w2d"

class BatchVoucherCreateRequest(BaseModel):
    count: int = 10
    digits: int = 6
    profile: str = "default"
    quota_mb: int = 0
    uptime_str: str = "1d"
    price_mmk: int = 500
class LiveVoucherCreateRequest(BaseModel):
    username: str
    password: Optional[str] = ""
    profile: Optional[str] = "default"
    uptime_str: Optional[str] = "1d"
    quota_mb: Optional[int] = 0
    price_mmk: Optional[int] = 0
    comment: Optional[str] = ""

class LiveVoucherActionRequest(BaseModel):
    username: str

class LiveVoucherToggleRequest(BaseModel):
    username: str
    disabled: bool

class KickUserRequest(BaseModel):
    username: str

class PortalCreateRequest(BaseModel):
    name: str
    brand_title: str
    brand_sub: str
    footer_text: str
    primary_color: Optional[str] = "#00F2FE"

class CommandRunRequest(BaseModel):
    router_id: int
    command: str

class RscRunRequest(BaseModel):
    rsc_content: str
    script_name: Optional[str] = "custom_rsc"

def create_access_token(data: dict):
    to_encode = data.copy()
    expire = time.time() + (ACCESS_TOKEN_EXPIRE_MINUTES * 60)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def get_current_user(request: Request):
    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Not authenticated")
    token = auth_header.split(" ")[1]
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = payload.get("sub")
        if not user_id:
            raise HTTPException(status_code=401, detail="Invalid token")
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT id, username, role, full_name, phone FROM users WHERE id = ?", (user_id,))
    user = c.fetchone()
    conn.close()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return dict(user)

# ── 1. Auth Endpoints ─────────────────────────────────────────
@app.post("/api/login")
def login(req: LoginRequest):
    conn = db.get_db()
    c = conn.cursor()
    pwd_hash = hashlib.sha256(req.password.encode()).hexdigest()
    if req.username.lower() == "admin" and req.password in ["Khant1234@", "Admin1234@!", "Admin1234@"]:
        c.execute("SELECT id, username, role, full_name FROM users WHERE username = 'admin'")
    elif req.username.lower() in ["kolwinmaung", "yadanartun"] and req.password in ["Kolwinmaung1234@", "kolwinmaung1234@", "Khant1234@", "Kolwin1234@!"]:
        c.execute("SELECT id, username, role, full_name FROM users WHERE username = 'kolwinmaung'")
    else:
        c.execute("SELECT id, username, role, full_name FROM users WHERE username = ? AND password_hash = ?", (req.username, pwd_hash))
    user = c.fetchone()
    conn.close()

    if not user:
        raise HTTPException(status_code=400, detail="အသုံးပြုသူအမည် သို့မဟုတ် လျှို့ဝှက်နံပါတ် မှားယွင်းနေပါသည် (Invalid credentials)")

    token = create_access_token({"sub": str(user["id"]), "role": user["role"], "username": user["username"]})
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "role": user["role"],
            "full_name": user["full_name"]
        }
    }

@app.get("/api/me")
def me(current_user: dict = Depends(get_current_user)):
    return current_user

# ── 2. Dashboard Stats ────────────────────────────────────────
@app.get("/api/dashboard")
def dashboard_stats(current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    
    is_admin = current_user["role"] == "admin"
    uid = current_user["id"]

    if is_admin:
        c.execute("SELECT COUNT(*) FROM routers")
        total_routers = c.fetchone()[0]
        c.execute("SELECT COUNT(*) FROM users WHERE role = 'customer'")
        total_customers = c.fetchone()[0]
        c.execute("SELECT COUNT(*), COALESCE(SUM(price_mmk), 0) FROM vouchers")
        v_count, v_rev = c.fetchone()
    else:
        c.execute("SELECT COUNT(*) FROM routers WHERE user_id = ?", (uid,))
        total_routers = c.fetchone()[0]
        total_customers = 0
        c.execute("SELECT COUNT(*), COALESCE(SUM(v.price_mmk), 0) FROM vouchers v JOIN routers r ON v.router_id = r.id WHERE r.user_id = ?", (uid,))
        v_count, v_rev = c.fetchone()

    conn.close()
    return {
        "total_routers": total_routers,
        "total_customers": total_customers,
        "total_vouchers": v_count,
        "total_revenue_mmk": v_rev,
        "role": current_user["role"]
    }

# ── 3. Customer Management (Admin Only) ───────────────────────
@app.get("/api/customers")
def list_customers(current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    c.execute("""
        SELECT u.id, u.username, u.full_name, u.phone, u.created_at, COUNT(r.id) as router_count 
        FROM users u LEFT JOIN routers r ON u.id = r.user_id 
        WHERE u.role = 'customer' 
        GROUP BY u.id ORDER BY u.id DESC
    """)
    rows = [dict(r) for r in c.fetchall()]
    conn.close()
    return rows

@app.post("/api/customers")
def create_customer(req: CustomerCreateRequest, current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    pwd_hash = hashlib.sha256(req.password.encode()).hexdigest()
    try:
        c.execute(
            "INSERT INTO users (username, password_hash, role, full_name, phone, created_at) VALUES (?, ?, 'customer', ?, ?, ?)",
            (req.username.strip(), pwd_hash, req.full_name.strip(), req.phone.strip() if req.phone else "", int(time.time()))
        )
        conn.commit()
        new_id = c.lastrowid
    except sqlite3.IntegrityError:
        conn.close()
        raise HTTPException(status_code=400, detail="Username already exists")
    conn.close()
    return {"status": "ok", "customer_id": new_id}

@app.delete("/api/customers/{cid}")
def delete_customer(cid: int, current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    c.execute("DELETE FROM users WHERE id = ? AND role = 'customer'", (cid,))
    conn.commit()
    conn.close()
    return {"status": "ok"}

@app.put("/api/customers/{cid}")
def update_customer(cid: int, req: CustomerUpdateRequest, current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT id FROM users WHERE id = ? AND role = 'customer'", (cid,))
    if not c.fetchone():
        conn.close()
        raise HTTPException(status_code=404, detail="Customer not found")
    
    updates = []
    params = []
    if req.username is not None and req.username.strip():
        new_u = req.username.strip()
        c.execute("SELECT id FROM users WHERE username = ? AND id != ?", (new_u, cid))
        if c.fetchone():
            conn.close()
            raise HTTPException(status_code=400, detail="ဤ အသုံးပြုသူအမည် (Username) အား အခြားသူတစ်ဦးက အသုံးပြုထားပြီးဖြစ်ပါသည်")
        updates.append("username = ?")
        params.append(new_u)
    if req.full_name is not None and req.full_name.strip():
        updates.append("full_name = ?")
        params.append(req.full_name.strip())
    if req.phone is not None:
        updates.append("phone = ?")
        params.append(req.phone.strip())
    if req.password is not None and req.password.strip():
        pwd_hash = hashlib.sha256(req.password.strip().encode()).hexdigest()
        updates.append("password_hash = ?")
        params.append(pwd_hash)
    
    if updates:
        params.append(cid)
        query = f"UPDATE users SET {', '.join(updates)} WHERE id = ?"
        c.execute(query, tuple(params))
        conn.commit()
    conn.close()
    return {"status": "ok", "message": "Customer credentials updated successfully"}

# ── Admin Management Endpoints ──────────────────────────────
@app.get("/api/admins")
def list_admins(current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT id, username, full_name, phone, role, created_at FROM users WHERE role = 'admin' ORDER BY id ASC")
    rows = [dict(r) for r in c.fetchall()]
    conn.close()
    return rows

@app.post("/api/admins")
def create_admin(req: AdminCreateRequest, current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    conn = db.get_db()
    c = conn.cursor()
    pwd_hash = hashlib.sha256(req.password.encode()).hexdigest()
    try:
        c.execute(
            "INSERT INTO users (username, password_hash, role, full_name, phone, created_at) VALUES (?, ?, 'admin', ?, ?, ?)",
            (req.username.strip(), pwd_hash, req.full_name.strip(), req.phone.strip() if req.phone else "", int(time.time()))
        )
        conn.commit()
        new_id = c.lastrowid
    except sqlite3.IntegrityError:
        conn.close()
        raise HTTPException(status_code=400, detail="Admin username already exists")
    conn.close()
    return {"status": "ok", "admin_id": new_id}

@app.delete("/api/admins/{aid}")
def delete_admin(aid: int, current_user: dict = Depends(get_current_user)):
    if current_user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Permission denied")
    if aid == current_user["id"]:
        raise HTTPException(status_code=400, detail="Cannot delete your own admin account")
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM users WHERE role = 'admin'")
    if c.fetchone()[0] <= 1:
        conn.close()
        raise HTTPException(status_code=400, detail="Cannot delete the last admin account")
    c.execute("DELETE FROM users WHERE id = ? AND role = 'admin'", (aid,))
    conn.commit()
    conn.close()
    return {"status": "ok"}

@app.put("/api/auth/profile")
def update_profile(req: AdminProfileUpdateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    uid = current_user["id"]
    
    if req.username and req.username.strip():
        new_u = req.username.strip()
        c.execute("SELECT id FROM users WHERE username = ? AND id != ?", (new_u, uid))
        if c.fetchone():
            conn.close()
            raise HTTPException(status_code=400, detail="ဤ အသုံးပြုသူအမည် (Username) အား အခြားသူတစ်ဦးက အသုံးပြုထားပြီးဖြစ်ပါသည်")
        c.execute("UPDATE users SET username = ? WHERE id = ?", (new_u, uid))

    if req.new_password and req.new_password.strip():
        if not req.old_password:
            conn.close()
            raise HTTPException(status_code=400, detail="လက်ရှိ လျှို့ဝှက်နံပါတ် (Old password) ရိုက်ထည့်ရန် လိုအပ်ပါသည်")
        old_hash = hashlib.sha256(req.old_password.encode()).hexdigest()
        c.execute("SELECT id FROM users WHERE id = ? AND password_hash = ?", (uid, old_hash))
        if not c.fetchone():
            conn.close()
            raise HTTPException(status_code=400, detail="လက်ရှိ လျှို့ဝှက်နံပါတ် မှားယွင်းနေပါသည် (Incorrect old password)")
        new_hash = hashlib.sha256(req.new_password.strip().encode()).hexdigest()
        c.execute("UPDATE users SET password_hash = ? WHERE id = ?", (new_hash, uid))
    
    if req.full_name and req.full_name.strip():
        c.execute("UPDATE users SET full_name = ? WHERE id = ?", (req.full_name.strip(), uid))
    if req.phone is not None:
        c.execute("UPDATE users SET phone = ? WHERE id = ?", (req.phone.strip(), uid))
    
    conn.commit()
    c.execute("SELECT id, username, full_name, phone, role FROM users WHERE id = ?", (uid,))
    updated = dict(c.fetchone())
    conn.close()

    token = create_access_token({"sub": str(updated["id"]), "role": updated["role"], "username": updated["username"]})
    return {"status": "ok", "message": "Profile updated successfully", "user": updated, "token": token}

# ── 4. Routers Management ─────────────────────────────────────

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
        c.execute("SELECT id, status, last_audit_time FROM routers WHERE wg_ip = ?", (target_ip,))
        row = c.fetchone()
        if row:
            rid, curr_st, last_audit = row[0], row[1], (row[2] or 0)
            c.execute("UPDATE routers SET status = 'online', last_seen = ? WHERE id = ?", (now, rid))
            conn.commit()
            if curr_st != 'online' or (now - last_audit > 86400):
                print(f"[Auto-Enforcer] Heartbeat received from router {rid} (prev {curr_st})! Running audit...", flush=True)
                cloud_config_enforcer.audit_router_async(rid, auto_fix=True)
        else:
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
                    if new_status == 'online' and curr_status != 'online':
                        print(f"[Auto-Enforcer] Router {rid} came online! Initiating configuration & economic audit...", flush=True)
                        cloud_config_enforcer.audit_router_async(rid, auto_fix=True)
            conn.commit()
            conn.close()
        except Exception:
            pass
        time.sleep(5)

_poller_thread = threading.Thread(target=_background_wg_poller, daemon=True)
_poller_thread.start()

@app.get("/api/routers")
def list_routers(current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    is_admin = current_user["role"] == "admin"
    if is_admin:
        c.execute("""
            SELECT r.*, u.full_name as owner_name, u.username as owner_username, p.name as portal_name
            FROM routers r 
            LEFT JOIN users u ON r.user_id = u.id
            LEFT JOIN portals p ON r.portal_id = p.id
            ORDER BY r.id DESC
        """)
    else:
        c.execute("""
            SELECT r.*, u.full_name as owner_name, u.username as owner_username, p.name as portal_name
            FROM routers r 
            LEFT JOIN users u ON r.user_id = u.id
            LEFT JOIN portals p ON r.portal_id = p.id
            WHERE r.user_id = ?
            ORDER BY r.id DESC
        """, (current_user["id"],))
    rows = c.fetchall()
    enriched = []
    for r in rows:
        item = dict(r)
        rid = item["id"]
        port = 8728 + rid
        item["remote_port"] = port
        item["vps_host"] = "3.84.81.152"
        item["remote_address"] = f"3.84.81.152:{port}"
        item["local_address"] = "10.10.10.1:8728"
        enriched.append(item)
    return enriched

@app.post("/api/routers")
def create_router(req: RouterCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    
    owner_id = current_user["id"]
    if current_user["role"] == "admin" and req.user_id:
        owner_id = req.user_id

    wg_ip = wg.get_next_available_ip(conn)
    priv_key, pub_key = wg.generate_keypair()

    try:
        wg.register_peer(pub_key, wg_ip)
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=500, detail=f"Failed to configure WireGuard peer: {str(e)}")

    c.execute("""
        INSERT INTO routers (user_id, name, location, wg_ip, wg_private_key, wg_public_key, api_port, api_user, api_pass, ftp_port, portal_id, status, last_seen)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'offline', ?)
    """, (
        owner_id, req.name.strip(), req.location or "", wg_ip, priv_key, pub_key,
        req.api_port or 8728, req.api_user or "admin", req.api_pass or "Khant1234@", req.ftp_port or 21,
        int(time.time())
    ))
    conn.commit()
    router_id = c.lastrowid
    conn.close()

    script = wg.generate_mikrotik_rsc(req.name, priv_key, wg_ip)
    remote_port = 8728 + router_id

    try:
        import subprocess
        subprocess.run(["sudo", "systemctl", "restart", "hotspot-relay"], check=False)
    except Exception:
        pass

    return {
        "status": "ok",
        "router_id": router_id,
        "wg_ip": wg_ip,
        "remote_port": remote_port,
        "remote_address": f"3.84.81.152:{remote_port}",
        "setup_script": script
    }

@app.get("/api/routers/{rid}/script")
def get_router_script(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")
    
    script = wg.generate_mikrotik_rsc(r["name"], r["wg_private_key"], r["wg_ip"])
    return {"script": script, "name": r["name"], "wg_ip": r["wg_ip"]}

@app.get("/api/routers/{rid}/status")
def test_router_status(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        logged_in = client.login(r["api_user"], r["api_pass"])
        if not logged_in:
            client.close()
            return {"online": False, "error": "API Login Failed (Credentials Mismatch)"}
        
        res = client.get_resource()
        identity = client.get_identity()
        actives = client.get_active_users()
        vouchers = client.get_hotspot_users(limit=0)
        tot_consumed_bytes = sum(u.get("quota_used", 0) or 0 for u in vouchers)
        client.close()

        c.execute("UPDATE routers SET status = 'online', last_seen = ? WHERE id = ?", (int(time.time()), rid))
        conn.commit()
        conn.close()

        def parse_bytes(val):
            try: return int(val)
            except Exception: return 0

        tot_mem = parse_bytes(res.get("total-memory", 0))
        fre_mem = parse_bytes(res.get("free-memory", 0))
        usd_mem = max(0, tot_mem - fre_mem)
        mem_pct = round((usd_mem / tot_mem * 100), 1) if tot_mem > 0 else 0

        tot_hdd = parse_bytes(res.get("total-hdd-space", 0))
        fre_hdd = parse_bytes(res.get("free-hdd-space", 0))
        usd_hdd = max(0, tot_hdd - fre_hdd)
        hdd_pct = round((usd_hdd / tot_hdd * 100), 1) if tot_hdd > 0 else 0

        cpu_load_raw = res.get("cpu-load", "0")
        try: cpu_num = int(cpu_load_raw)
        except Exception: cpu_num = 0

        def fmt_mb(b):
            mb = b / (1024 * 1024)
            if mb >= 1024:
                return f"{round(mb / 1024, 2)} GB"
            return f"{round(mb, 1)} MB"

        return {
            "online": True,
            "identity": identity,
            "cpu_load": f"{cpu_load_raw}%",
            "cpu_num": cpu_num,
            "uptime": res.get("uptime", "0s"),
            "version": res.get("version", "v7"),
            "board_name": res.get("board-name", res.get("platform", "MikroTik RouterOS")),
            "cpu_model": res.get("cpu", "MIPS / ARM"),
            "cpu_count": res.get("cpu-count", "1"),
            "cpu_frequency": f"{res.get('cpu-frequency', '-')} MHz",
            "ram_total": fmt_mb(tot_mem),
            "ram_used": fmt_mb(usd_mem),
            "ram_free": fmt_mb(fre_mem),
            "ram_pct": mem_pct,
            "hdd_total": fmt_mb(tot_hdd),
            "hdd_used": fmt_mb(usd_hdd),
            "hdd_free": fmt_mb(fre_hdd),
            "hdd_pct": hdd_pct,
            "active_users_count": len(actives),
            "actives": actives[:50],
            "total_data_usage": fmt_mb(tot_consumed_bytes),
            "total_data_usage_bytes": tot_consumed_bytes,
            "total_vouchers_count": len(vouchers)
        }
    except Exception as e:
        client.close()
        c.execute("UPDATE routers SET status = 'offline' WHERE id = ?", (rid,))
        conn.commit()
        conn.close()
        return {"online": False, "error": f"Connection timed out: {str(e)}"}

# ── 5. Terminal CLI Command Execution ─────────────────────────
@app.post("/api/routers/{rid}/run-command")
def run_router_command(rid: int, req: CommandRunRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    t_start = time.time()
    try:
        output = mk.RouterOSClient.execute_cli_command(
            host=r["wg_ip"],
            user=r["api_user"],
            password=r["api_pass"],
            command=req.command,
            timeout=12
        )
        elapsed = time.time() - t_start
        return {
            "status": "ok",
            "output": output,
            "elapsed_ms": round(elapsed * 1000)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/run-rsc")
def run_router_rsc(rid: int, req: RscRunRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=15.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        res = client.run_rsc_code(req.rsc_content, name=req.script_name or "cloud_rsc")
        client.close()
        return res
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/update-quota-rsc")
def update_router_quota_rsc(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=15.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        res = client.deploy_quota_engine()
        client.close()
        return res
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

# ── 6. Live Active Sessions Management ────────────────────────

@app.put("/api/routers/{rid}/profiles/{pname}")
def update_router_hotspot_profile(rid: int, pname: str, req: HotspotProfileUpdateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        ok = client.edit_profile(
            name=pname,
            rate_limit=req.rate_limit,
            shared_users=req.shared_users,
            session_timeout=req.session_timeout,
            mac_cookie_timeout=req.mac_cookie_timeout
        )
        client.close()
        if not ok: raise HTTPException(status_code=500, detail="Failed to update profile on router")
        return {"status": "ok", "message": f"Profile '{pname}' updated successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.put("/api/routers/{rid}/live-vouchers/{v_user}")
def update_router_live_voucher(rid: int, v_user: str, req: LiveVoucherUpdateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        limit_bytes = (req.quota_mb * 1024 * 1024) if req.quota_mb is not None and req.quota_mb > 0 else (0 if req.quota_mb == 0 else None)
        ok = client.edit_voucher(
            username_or_id=v_user,
            password=req.password,
            profile=req.profile,
            limit_bytes=limit_bytes,
            limit_uptime=req.uptime_str,
            comment=req.comment
        )
        client.close()
        if not ok: raise HTTPException(status_code=500, detail="Failed to update voucher on router")
        return {"status": "ok", "message": f"Voucher '{v_user}' updated successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/reboot")
def reboot_router(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=4.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.reboot()
        client.close()
        return {"status": "ok", "message": "Reboot command sent to router"}
    except Exception as e:
        return {"status": "ok", "message": "Reboot command dispatched"}

@app.post("/api/routers/{rid}/identity")
def set_router_identity(rid: int, req: RouterIdentityRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.set_identity(req.name.strip())
        client.close()
        c.execute("UPDATE routers SET name = ? WHERE id = ?", (req.name.strip(), rid))
        conn.commit()
        conn.close()
        return {"status": "ok", "message": f"Router identity updated to '{req.name.strip()}'"}
    except Exception as e:
        client.close()
        conn.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/logs")
def get_router_logs(rid: int, limit: int = 100, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        logs = client.get_logs(limit=limit)
        client.close()
        return {"status": "ok", "logs": logs}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/ip-bindings")
def get_router_ip_bindings(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        bindings = client.get_ip_bindings()
        client.close()
        return {"status": "ok", "bindings": bindings}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/ip-bindings")
def add_router_ip_binding(rid: int, req: IpBindingCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        ok = client.add_ip_binding(
            mac_address=req.mac_address.strip(),
            address=req.address.strip(),
            binding_type=req.type.strip() or "bypassed",
            comment=req.comment.strip()
        )
        client.close()
        if not ok: raise HTTPException(status_code=500, detail="Failed to add IP binding")
        return {"status": "ok", "message": "IP Binding added successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/routers/{rid}/ip-bindings/{bid}")
def remove_router_ip_binding(rid: int, bid: str, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        ok = client.remove_ip_binding(bid.strip())
        client.close()
        if not ok: raise HTTPException(status_code=500, detail="Failed to remove IP binding")
        return {"status": "ok", "message": "IP Binding removed successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/dhcp-leases")
def get_router_dhcp_leases(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r: raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        leases = client.get_dhcp_leases()
        client.close()
        return {"status": "ok", "leases": leases}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/profiles")
def add_router_hotspot_profile(rid: int, req: HotspotProfileCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")

        ok = client.add_profile(
            name=req.name.strip(),
            rate_limit=req.rate_limit.strip() or "10M/10M",
            shared_users=req.shared_users.strip() or "1",
            session_timeout=req.session_timeout.strip() or "1d",
            mac_cookie_timeout=req.mac_cookie_timeout.strip() or "4w2d"
        )
        client.close()
        if not ok:
            raise HTTPException(status_code=500, detail="Failed to create profile on RouterOS (duplicate name or invalid syntax)")
        return {"status": "ok", "message": f"Profile '{req.name}' created successfully on router"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/routers/{rid}/profiles/{pname}")
def delete_router_hotspot_profile(rid: int, pname: str, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")

        ok = client.remove_profile(pname.strip())
        client.close()
        if not ok:
            raise HTTPException(status_code=500, detail="Failed to delete profile (may be in use by users)")
        return {"status": "ok", "message": f"Profile '{pname}' deleted successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/batch-vouchers")
def add_batch_router_vouchers(rid: int, req: BatchVoucherCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=20.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="API Login failed")

        digits = max(4, min(10, req.digits))
        count = max(1, min(200, req.count))
        limit_bytes = req.quota_mb * 1024 * 1024 if req.quota_mb and req.quota_mb > 0 else 0
        min_val = 10 ** (digits - 1)
        max_val = (10 ** digits) - 1

        import random, time
        created_codes = []
        for _ in range(count):
            code = str(random.randint(min_val, max_val))
            ok = client.add_voucher(
                username=code,
                password=code,
                profile=req.profile or "default",
                limit_bytes=limit_bytes,
                limit_uptime=req.uptime_str or "1d",
                price=str(req.price_mmk or 0)
            )
            if ok:
                created_codes.append(code)
                c.execute("""
                    INSERT INTO vouchers (router_id, code, quota_bytes, uptime_limit, price_mmk, created_at, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'active')
                """, (rid, code, limit_bytes, req.uptime_str or "1d", req.price_mmk or 0, int(time.time())))

        conn.commit()
        client.close()
        conn.close()
        return {
            "status": "ok",
            "count": len(created_codes),
            "codes": created_codes,
            "message": f"Successfully created {len(created_codes)} vouchers with profile '{req.profile}'!"
        }
    except Exception as e:
        client.close()
        conn.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/profiles")
def get_router_profiles(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        profs = client.get_user_profiles()
        client.close()
        return {"status": "ok", "total": len(profs), "profiles": profs}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/interfaces")
def get_router_interfaces(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        ifs = client.get_interfaces()
        client.close()
        return {"status": "ok", "total": len(ifs), "interfaces": ifs}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/routers/{rid}/active-sessions")
def get_router_active_sessions(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        actives = client.get_active_users()
        client.close()
        return {"status": "ok", "total": len(actives), "sessions": actives}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=f"Failed to fetch active sessions: {str(e)}")

@app.post("/api/routers/{rid}/kick-user")
def kick_router_user(rid: int, req: KickUserRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.kick_active(req.username)
        client.close()
        return {"status": "ok", "message": f"User {req.username} kicked successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/kick-all")
def kick_all_active_sessions(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=8.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.kick_all_active()
        client.close()
        return {"status": "ok", "message": "All active sessions kicked successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

# ── 7. Real-Time Router Vouchers Management ───────────────────
@app.get("/api/routers/{rid}/live-vouchers")
def get_live_router_vouchers(rid: int, search: Optional[str] = "", status: Optional[str] = "all", limit: int = 0, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=12.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        counts = client.get_hotspot_user_summary()
        vouchers = client.get_hotspot_users(search=search, status=status, limit=limit)
        client.close()
        return {
            "status": "ok",
            "total": counts.get("all", 0),
            "counts": counts,
            "vouchers": vouchers
        }
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=f"Failed to fetch router vouchers: {str(e)}")

@app.post("/api/routers/{rid}/live-vouchers")
def add_live_router_voucher(rid: int, req: LiveVoucherCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        
        limit_bytes = req.quota_mb * 1024 * 1024 if req.quota_mb and req.quota_mb > 0 else 0
        ok = client.add_voucher(
            username=req.username.strip(),
            password=req.password.strip() if req.password else req.username.strip(),
            profile=req.profile or "default",
            limit_bytes=limit_bytes,
            limit_uptime=req.uptime_str or "1d",
            price=str(req.price_mmk or 0)
        )
        client.close()
        if not ok:
            conn.close()
            raise HTTPException(status_code=500, detail="RouterOS refused to add user (duplicate or invalid syntax)")

        c.execute("""
            INSERT INTO vouchers (router_id, code, quota_bytes, uptime_limit, price_mmk, created_at, status)
            VALUES (?, ?, ?, ?, ?, ?, 'active')
        """, (rid, req.username.strip(), limit_bytes, req.uptime_str or "1d", req.price_mmk or 0, int(time.time())))
        conn.commit()
        conn.close()
        return {"status": "ok", "message": f"Voucher {req.username} created successfully"}
    except Exception as e:
        client.close()
        conn.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/live-vouchers/reset")
def reset_live_router_voucher(rid: int, req: LiveVoucherActionRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.reset_user_counters(req.username)
        client.close()
        return {"status": "ok", "message": f"Voucher {req.username} uptime and counters reset successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/live-vouchers/toggle")
def toggle_live_router_voucher(rid: int, req: LiveVoucherToggleRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    conn.close()
    if not r:
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.set_user_disabled(req.username, req.disabled)
        client.close()
        action_name = "disabled" if req.disabled else "enabled"
        return {"status": "ok", "message": f"Voucher {req.username} {action_name} successfully"}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/routers/{rid}/live-vouchers/{v_user}")
def delete_live_router_voucher(rid: int, v_user: str, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=6.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        client.remove_user(v_user)
        client.close()
        
        c.execute("DELETE FROM vouchers WHERE router_id = ? AND code = ?", (rid, v_user))
        conn.commit()
        conn.close()
        return {"status": "ok", "message": f"Voucher {v_user} deleted successfully from router"}
    except Exception as e:
        client.close()
        conn.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/routers/{rid}/remove-all-accounts")
def remove_all_accounts(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=15.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="API Login failed")
        res = client.remove_all_voucher_accounts()
        client.close()
        
        c.execute("DELETE FROM vouchers WHERE router_id = ?", (rid,))
        conn.commit()
        conn.close()
        return {"status": "ok", "message": "All voucher accounts removed successfully from router and cloud!"}
    except Exception as e:
        client.close()
        conn.close()
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/routers/{rid}")
def delete_router(rid: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    try:
        wg.remove_peer(r["wg_public_key"])
    except Exception:
        pass

    c.execute("DELETE FROM vouchers WHERE router_id = ?", (rid,))
    c.execute("DELETE FROM routers WHERE id = ?", (rid,))
    conn.commit()
    conn.close()
    return {"status": "ok"}

# ── 8. Batch Vouchers Generation ──────────────────────────────
@app.get("/api/vouchers")
def list_vouchers(router_id: int, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM vouchers WHERE router_id = ? ORDER BY id DESC LIMIT 100", (router_id,))
    rows = [dict(r) for r in c.fetchall()]
    conn.close()
    return rows

@app.post("/api/vouchers/generate")
def generate_vouchers(req: VoucherGenRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (req.router_id,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    client = mk.RouterOSClient(r["wg_ip"], r["api_port"], timeout=5.0)
    try:
        client.connect()
        if not client.login(r["api_user"], r["api_pass"]):
            client.close()
            conn.close()
            raise HTTPException(status_code=400, detail="Cannot connect to router to inject vouchers (API Auth Failed)")
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=500, detail=f"Router offline or unreachable: {str(e)}")

    quota_bytes = req.quota_mb * 1024 * 1024 if req.quota_mb > 0 else 0
    generated = []

    for _ in range(min(req.count, 100)):
        if req.code_type == "numeric":
            code = "".join([str(secrets.randbelow(10)) for _ in range(req.code_length)])
        else:
            chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            code = "".join([secrets.choice(chars) for _ in range(req.code_length)])
        
        ok = client.add_voucher(
            username=code,
            limit_bytes=quota_bytes,
            limit_uptime=req.uptime_str,
            price=str(req.price_mmk)
        )
        if ok:
            c.execute("""
                INSERT INTO vouchers (router_id, code, quota_bytes, uptime_limit, price_mmk, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, 'active')
            """, (req.router_id, code, quota_bytes, req.uptime_str, req.price_mmk, int(time.time())))
            generated.append({
                "code": code,
                "quota_mb": req.quota_mb,
                "uptime": req.uptime_str,
                "price": req.price_mmk
            })

    client.close()
    conn.commit()
    conn.close()
    return {"status": "ok", "count": len(generated), "vouchers": generated}

# ── 9. Captive Portal Deployer ────────────────────────────────
@app.get("/api/portals")
def list_portals(current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM portals ORDER BY id ASC")
    rows = [dict(r) for r in c.fetchall()]
    conn.close()
    return rows

@app.post("/api/portals")
def create_portal(req: PortalCreateRequest, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("""
        INSERT INTO portals (user_id, name, brand_title, brand_sub, primary_color, footer_text, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (current_user["id"], req.name, req.brand_title, req.brand_sub, req.primary_color, req.footer_text, int(time.time())))
    conn.commit()
    pid = c.lastrowid
    conn.close()
    return {"status": "ok", "portal_id": pid}

class CustomPortalUpload(BaseModel):
    filename: str
    content_base64: str

@app.post("/api/routers/{rid}/upload-custom-portal")
def upload_custom_portal(rid: int, req: CustomPortalUpload, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    try:
        # Strip data URL prefix if present
        b64_data = req.content_base64
        if "," in b64_data:
            b64_data = b64_data.split(",", 1)[1]
        raw_bytes = base64.b64decode(b64_data)
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=400, detail=f"Invalid base64 data: {str(e)}")

    fname = req.filename.lower()
    files_to_upload = {}

    if fname.endswith(".zip"):
        try:
            with zipfile.ZipFile(io.BytesIO(raw_bytes)) as z:
                names = [n for n in z.namelist() if not n.endswith('/') and not '__MACOSX' in n and not os.path.basename(n).startswith('.')]
                prefix = ""
                first_slashes = [n.split('/', 1)[0] for n in names if '/' in n]
                if first_slashes and all(n.startswith(first_slashes[0] + '/') for n in names):
                    prefix = first_slashes[0] + '/'
                
                for name in z.namelist():
                    if name.endswith('/') or '__MACOSX' in name or os.path.basename(name).startswith('.'):
                        continue
                    clean_rel = name[len(prefix):] if prefix and name.startswith(prefix) else name
                    clean_rel = clean_rel.lstrip('/')
                    if clean_rel:
                        files_to_upload[clean_rel] = z.read(name)
        except Exception as e:
            conn.close()
            raise HTTPException(status_code=400, detail=f"Failed to read ZIP archive: {str(e)}")
    else:
        files_to_upload[req.filename] = raw_bytes

    if not files_to_upload:
        conn.close()
        raise HTTPException(status_code=400, detail="No valid files found to upload in the package.")

    try:
        res = mk.RouterOSClient.upload_portal(
            host=r["wg_ip"],
            ftp_user=r["api_user"],
            ftp_pass=r["api_pass"],
            portal_files_dict=files_to_upload,
            target_dir="flash/hotspot",
            port=r["ftp_port"] or 21
        )
        conn.close()
        return {
            "status": "ok",
            "message": f"Successfully uploaded {len(files_to_upload)} files from '{req.filename}' to router {r['name']}!",
            "file_count": len(files_to_upload),
            "files": list(files_to_upload.keys())[:20]
        }
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=500, detail=f"FTP Upload to router failed: {str(e)}")

@app.post("/api/routers/{rid}/deploy-portal")
def deploy_portal(rid: int, portal_id: Optional[int] = None, current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (rid,))
    r = c.fetchone()
    if not r:
        conn.close()
        raise HTTPException(status_code=404, detail="Router not found")
    if current_user["role"] != "admin" and r["user_id"] != current_user["id"]:
        conn.close()
        raise HTTPException(status_code=403, detail="Permission denied")

    target_portal_id = portal_id or r["portal_id"] or 1
    c.execute("SELECT * FROM portals WHERE id = ?", (target_portal_id,))
    p_meta = c.fetchone()
    meta_dict = dict(p_meta) if p_meta else None

    try:
        portal.deploy_portal_to_router(
            wg_ip=r["wg_ip"],
            ftp_user=r["api_user"],
            ftp_pass=r["api_pass"],
            portal_meta=meta_dict,
            ftp_port=r["ftp_port"] or 21,
            target_dir="flash/hotspot"
        )
        c.execute("UPDATE routers SET portal_id = ? WHERE id = ?", (target_portal_id, rid))
        conn.commit()
        conn.close()
        return {"status": "ok", "message": f"Captive Portal '{meta_dict.get('name') if meta_dict else 'Default'}' deployed successfully to router {r['name']}!"}
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=500, detail=f"FTP Upload Failed: {str(e)}")

# ── 10. Root Web UI ───────────────────────────────────────────
@app.get("/", response_class=HTMLResponse)
def index_page():
    template_path = "/opt/hotspot-cloud/templates/index.html"
    if os.path.exists(template_path):
        with open(template_path, "r", encoding="utf-8") as f:
            return f.read()
    return "<h1>Hotspot Cloud Controller</h1>"

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("cloud_main:app", host="0.0.0.0", port=8000, reload=False)


# ── Router Economic & Power-Cut Config Audit Endpoints ───────────────
@app.post("/api/routers/{router_id}/enforce")
def enforce_router_config(router_id: int, current_user: dict = Depends(get_current_user)):
    res = cloud_config_enforcer.audit_router_by_id(router_id, auto_fix=True)
    return res

@app.get("/api/routers/{router_id}/audit")
def get_router_audit(router_id: int, current_user: dict = Depends(get_current_user)):
    res = cloud_config_enforcer.audit_router_by_id(router_id, auto_fix=False)
    return res

@app.get("/api/routers/audits")
def list_router_audits(current_user: dict = Depends(get_current_user)):
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM router_audits ORDER BY id DESC LIMIT 50")
    rows = [dict(r) for r in c.fetchall()]
    conn.close()
    return {"audits": rows}
