import paramiko
import json

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')

sftp = ssh.open_sftp()

# 1. Update cloud_mikrotik.py
with sftp.file('/opt/hotspot-cloud/cloud_mikrotik.py', 'r') as f:
    mk_content = f.read().decode('utf-8')

# Add remove_all_voucher_accounts method if not present
if 'def remove_all_voucher_accounts' not in mk_content:
    target = '    def deploy_quota_engine(self):'
    remove_method = '''    def remove_all_voucher_accounts(self):
        rsc = (
            ':foreach u in=[/ip hotspot user find] do={'
            ' :local n [/ip hotspot user get $u name];'
            ' :if ($n != "admin" and $n != "default-trial") do={'
            '  :do { /ip hotspot active remove [find user=$n] } on-error={};'
            '  :do { /ip hotspot cookie remove [find user=$n] } on-error={};'
            '  :do { /system scheduler remove [find name=$n] } on-error={};'
            '  :do { /ip hotspot user remove $u } on-error={};'
            ' };'
            '};'
        )
        return self.run_rsc_code(rsc, name='purge_all_vouchers')

    def deploy_quota_engine(self):'''
    mk_content = mk_content.replace(target, remove_method, 1)

# Write updated cloud_mikrotik.py
with sftp.file('/opt/hotspot-cloud/cloud_mikrotik.py', 'w') as f:
    f.write(mk_content.encode('utf-8'))
print("Updated /opt/hotspot-cloud/cloud_mikrotik.py")

# 2. Update cloud_main.py
with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'r') as f:
    main_content = f.read().decode('utf-8')

if '/api/routers/{rid}/remove-all-accounts' not in main_content:
    target_endpoint = '@app.delete("/api/routers/{rid}")'
    new_endpoint = '''@app.post("/api/routers/{rid}/remove-all-accounts")
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

@app.delete("/api/routers/{rid}")'''
    main_content = main_content.replace(target_endpoint, new_endpoint, 1)
    with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'w') as f:
        f.write(main_content.encode('utf-8'))
    print("Updated /opt/hotspot-cloud/cloud_main.py")

sftp.close()

# Restart cloud service
stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart hotspot-cloud')
print('Restart STDOUT:', stdout.read().decode())
print('Restart STDERR:', stderr.read().decode())

# Check service status
stdin, stdout, stderr = ssh.exec_command('sudo systemctl status hotspot-cloud --no-pager')
print('Service Status:\n', stdout.read().decode()[:500])

ssh.close()
