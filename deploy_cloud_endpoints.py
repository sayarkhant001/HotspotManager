import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')
sftp = ssh.open_sftp()

# 1. Update cloud_main.py
with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'r') as f:
    main_py = f.read().decode('utf-8')

# Add models if not present
if 'class HotspotProfileCreateRequest' not in main_py:
    models_code = '''
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
'''
    target_pos = main_py.find('class LiveVoucherCreateRequest')
    if target_pos > 0:
        main_py = main_py[:target_pos] + models_code + main_py[target_pos:]

# Add endpoints if not present
if '/api/routers/{rid}/profiles' not in main_py or '@app.post("/api/routers/{rid}/profiles")' not in main_py:
    profile_endpoint = '''
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
'''
    target_endpoint = '@app.get("/api/routers/{rid}/profiles")'
    main_py = main_py.replace(target_endpoint, profile_endpoint + "\n" + target_endpoint, 1)

with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'w') as f:
    f.write(main_py.encode('utf-8'))
print('Updated /opt/hotspot-cloud/cloud_main.py with profile and batch endpoints.')

stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart hotspot-cloud')
print('Restarted hotspot-cloud:', stdout.read().decode())
