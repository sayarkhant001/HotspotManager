import urllib.request
import json
import time

login_req = urllib.request.Request(
    'http://3.84.81.152:8750/api/login',
    data=json.dumps({'username': 'admin', 'password': 'Khant1234@'}).encode(),
    headers={'Content-Type': 'application/json'}
)
login_res = urllib.request.urlopen(login_req)
token = json.loads(login_res.read())['token']
print("Logged in successfully to online portal!")

rsc_script = """
:log info "Admin Portal: Removing all hotspot accounts...";
/ip hotspot active remove [find user!="admin" and user!="default-trial"];
/ip hotspot cookie remove [find user!="admin" and user!="default-trial"];
/ip hotspot user remove [find name!="admin" and name!="default-trial"];
:log info "Admin Portal: All hotspot accounts removed successfully!";
"""

payload = json.dumps({
    'rsc_content': rsc_script.strip(),
    'script_name': 'remove_all_accounts'
}).encode()

rsc_req = urllib.request.Request(
    'http://3.84.81.152:8750/api/routers/3/run-rsc',
    data=payload,
    headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
)

print("Sending remove accounts script to router 3 via online portal API...")
t0 = time.time()
rsc_res = urllib.request.urlopen(rsc_req, timeout=30)
resp_data = json.loads(rsc_res.read())
print(f"Result (took {round(time.time() - t0, 2)}s):", resp_data)

# Now check remaining accounts on router 3
time.sleep(1)
v_req = urllib.request.Request(
    'http://3.84.81.152:8750/api/routers/3/live-vouchers',
    headers={'Authorization': f'Bearer {token}'}
)
v_res = urllib.request.urlopen(v_req)
data = json.loads(v_res.read())
print("Total accounts remaining on router 3:", data.get('total'))
print("Counts summary:", data.get('counts'))
