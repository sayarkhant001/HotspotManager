import paramiko
import os
import subprocess
import re

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')

sftp = ssh.open_sftp()

# ── 1. UPDATE cloud_main.py ON VPS ─────────────────────────────
with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'r') as f:
    main_py = f.read().decode('utf-8')

# Ensure imports
if 'import zipfile' not in main_py:
    main_py = "import zipfile\nimport io\nimport base64\n" + main_py

new_endpoint = '''class CustomPortalUpload(BaseModel):
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

@app.post("/api/routers/{rid}/deploy-portal")'''

if '/api/routers/{rid}/upload-custom-portal' not in main_py:
    main_py = main_py.replace('@app.post("/api/routers/{rid}/deploy-portal")', new_endpoint, 1)
    with sftp.file('/opt/hotspot-cloud/cloud_main.py', 'w') as f:
        f.write(main_py.encode('utf-8'))
    print("Added /api/routers/{rid}/upload-custom-portal to cloud_main.py")

sftp.close()

# Restart cloud service
stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart hotspot-cloud')
print('Restart status:', stdout.read().decode())

ssh.close()
print("Cloud backend updated!")
