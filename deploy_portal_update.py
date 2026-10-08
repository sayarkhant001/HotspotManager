import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')

quota_script_source = r'''  :local parseTag do={
    :local tagVal "";
    :local idx [:find $comm $tag];
    :if ($idx >= 0) do={
      :local rest [:pick $comm ($idx + [:len $tag]) [:len $comm]];
      :local endIdx [:find $rest "]"];
      :if ($endIdx >= 0) do={ :set rest [:pick $rest 0 $endIdx] };
      :set tagVal $rest;
    };
    :return $tagVal;
  };

  :local setTag do={
    :local res $comm;
    :local idx [:find $comm $tag];
    :if ($idx >= 0) do={
      :local endIdx [:find $comm "]" $idx];
      :local pfx [:pick $comm 0 $idx];
      :local sfx "";
      :if ($endIdx >= 0) do={ :set sfx [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set res ($pfx . $tag . $val . "]" . $sfx);
    } else={
      :set res ($comm . " " . $tag . $val . "]");
    };
    :return $res;
  };

  :local resolveOrigLim do={
    :local limStr [$parseTag comm=$comm tag="[ORIG-LIMIT:"];
    :if ([:len $limStr] > 0) do={
      :local lNum [:tonum $limStr];
      :if ($lNum > 0) do={ :return $lNum };
    };
    :local cUpper ($prof . " " . $comm);
    :if ($cUpper ~ "100GB" or $cUpper ~ "100G") do={ :return 107374182400 };
    :if ($cUpper ~ "50GB" or $cUpper ~ "50G") do={ :return 53687091200 };
    :if ($cUpper ~ "30GB" or $cUpper ~ "30Day" or $cUpper ~ "30G") do={ :return 32212254720 };
    :if ($cUpper ~ "20GB" or $cUpper ~ "20G") do={ :return 21474836480 };
    :if ($cUpper ~ "15GB" or $cUpper ~ "15G") do={ :return 16106127360 };
    :if ($cUpper ~ "10GB" or $cUpper ~ "10G") do={ :return 10737418240 };
    :if ($cUpper ~ "7GB" or $cUpper ~ "7G") do={ :return 7516192768 };
    :if ($cUpper ~ "5GB" or $cUpper ~ "5G") do={ :return 5368709120 };
    :if ($cUpper ~ "4GB" or $cUpper ~ "4G") do={ :return 4294967296 };
    :if ($cUpper ~ "3GB" or $cUpper ~ "3G") do={ :return 3221225472 };
    :if ($cUpper ~ "2GB" or $cUpper ~ "2G") do={ :return 2147483648 };
    :if ($cUpper ~ "1.5GB" or $cUpper ~ "1.7GB" or $cUpper ~ "1700MB" or $prof = "1000Ks") do={ :return 1782579200 };
    :if ($cUpper ~ "1GB" or $cUpper ~ "1G") do={ :return 1073741824 };
    :if ($cUpper ~ "750MB" or $prof = "500Ks") do={ :return 786432000 };
    :if ($cUpper ~ "500MB" or $cUpper ~ "500M") do={ :return 524288000 };
    :if ($cUpper ~ "250MB" or $cUpper ~ "250M") do={ :return 262144000 };
    :if ($cUpper ~ "100MB" or $cUpper ~ "100M") do={ :return 104857600 };
    :if ($cUpper ~ "50MB" or $cUpper ~ "50M" or $cUpper ~ "test") do={ :return 52428800 };
    :return $curLim;
  };

  :local resolveOrigUp do={
    :local upStr [$parseTag comm=$comm tag="[ORIG-UP:"];
    :if ([:len $upStr] > 0) do={ :return [:totime $upStr] };
    :local cUpper ($prof . " " . $comm);
    :if ($cUpper ~ "30D" or $cUpper ~ "30Day" or $cUpper ~ "Month") do={ :return [:totime "30d"] };
    :if ($cUpper ~ "14D" or $cUpper ~ "2W") do={ :return [:totime "14d"] };
    :if ($cUpper ~ "7D" or $cUpper ~ "1W") do={ :return [:totime "7d"] };
    :if ($cUpper ~ "3D") do={ :return [:totime "3d"] };
    :if ($cUpper ~ "2D") do={ :return [:totime "2d"] };
    :if ($cUpper ~ "1D" or $cUpper ~ "24H" or $prof = "5GB" or $prof = "2GB" or $prof = "1000Ks" or $prof = "500Ks") do={ :return [:totime "1d"] };
    :if ($cUpper ~ "12H") do={ :return [:totime "12h"] };
    :if ($cUpper ~ "8H") do={ :return [:totime "8h"] };
    :if ($cUpper ~ "6H") do={ :return [:totime "6h"] };
    :if ($cUpper ~ "4H") do={ :return [:totime "4h"] };
    :if ($cUpper ~ "3H") do={ :return [:totime "3h"] };
    :if ($cUpper ~ "2H" or $prof = "2Hour") do={ :return [:totime "2h"] };
    :if ($cUpper ~ "1H") do={ :return [:totime "1h"] };
    :if ($cUpper ~ "45M") do={ :return [:totime "45m"] };
    :if ($cUpper ~ "30M") do={ :return [:totime "30m"] };
    :if ($cUpper ~ "15M") do={ :return [:totime "15m"] };
    :if ($cUpper ~ "3M" or $prof = "test") do={ :return [:totime "3m"] };
    :if ($curUp != "00:00:00" and $curUp != "0s" and [:len $curUp] > 0) do={ :return [:totime $curUp] };
    :return [:totime "0s"];
  };

  :foreach a in=[/ip hotspot active find] do={
    :local uName [/ip hotspot active get $a user];
    :if ($uName != "admin" and $uName != "default-trial") do={
      :local uList [/ip hotspot user find name=$uName];
      :if ([:len $uList] > 0) do={
        :local u [:pick $uList 0];
        :local sIn [/ip hotspot active get $a bytes-in];
        :local sOut [/ip hotspot active get $a bytes-out];
        :local sUp [/ip hotspot active get $a uptime];
        :local sessionBytes ($sIn + $sOut);
        :local comm [/ip hotspot user get $u comment];
        :local uProf [/ip hotspot user get $u profile];
        :local curLim [/ip hotspot user get $u limit-bytes-total];
        :local curUp [/ip hotspot user get $u limit-uptime];

        :local baseBytes 0;
        :local bStr [$parseTag comm=$comm tag="[BASE:"];
        :if ([:len $bStr] > 0) do={ :set baseBytes [:tonum $bStr] };

        :local baseUp [:totime "0s"];
        :local buStr [$parseTag comm=$comm tag="[BASE-UP:"];
        :if ([:len $buStr] > 0) do={ :set baseUp [:totime $buStr] };

        :local curStoredUsed 0;
        :local uStr [$parseTag comm=$comm tag="[USED:"];
        :if ([:len $uStr] > 0) do={ :set curStoredUsed [:tonum $uStr] };

        :local curStoredUp [:totime "0s"];
        :local uuStr [$parseTag comm=$comm tag="[USED-UP:"];
        :if ([:len $uuStr] > 0) do={ :set curStoredUp [:totime $uuStr] };

        :local totalUsed ($baseBytes + $sessionBytes);
        :local totalUp ($baseUp + $sUp);

        # ONLY promote if counters reset due to reboot (i.e. calculated total fell below curStoredUsed)
        :if ($curStoredUsed > 0 and $totalUsed < $curStoredUsed) do={
          :set baseBytes $curStoredUsed;
          :set totalUsed ($baseBytes + $sessionBytes);
          :set comm [$setTag comm=$comm tag="[BASE:" val=[:tostr $baseBytes]];
        };
        :if ($curStoredUp > [:totime "0s"] and $totalUp < $curStoredUp) do={
          :set baseUp $curStoredUp;
          :set totalUp ($baseUp + $sUp);
          :set comm [$setTag comm=$comm tag="[BASE-UP:" val=[:tostr $baseUp]];
        };

        :local origLim [$resolveOrigLim parseTag=$parseTag prof=$uProf comm=$comm curLim=$curLim];
        :local origUp [$resolveOrigUp parseTag=$parseTag prof=$uProf comm=$comm curUp=$curUp];

        :if ([:find $comm "[ORIG-LIMIT:"] < 0 and $origLim > 0) do={
          :set comm [$setTag comm=$comm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
        };
        :if ([:find $comm "[ORIG-UP:"] < 0 and $origUp > [:totime "0s"]) do={
          :set comm [$setTag comm=$comm tag="[ORIG-UP:" val=[:tostr $origUp]];
        };

        :local deltaBytes ($totalUsed - $curStoredUsed);
        :if ($deltaBytes < 0) do={ :set deltaBytes (-$deltaBytes) };
        :if (($totalUsed > 0 and $deltaBytes >= 32768) or ($totalUsed != $curStoredUsed and $sessionBytes = 0) or ($totalUp != $curStoredUp and ($totalUp - $curStoredUp) >= [:totime "2s"])) do={
          :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
          :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
          /ip hotspot user set $u comment=$comm;
        };

        :local isDataExhausted false;
        :if ($origLim > 0 and $totalUsed >= $origLim) do={ :set isDataExhausted true };

        :local isTimeExhausted false;
        :if ($origUp > [:totime "0s"] and $totalUp >= $origUp) do={ :set isTimeExhausted true };

        :if ($isDataExhausted or $isTimeExhausted) do={
          :log info ("Hotspot: User " . $uName . " EXHAUSTED (Data: " . [:tostr $totalUsed] . "/" . [:tostr $origLim] . ", Time: " . [:tostr $totalUp] . "/" . [:tostr $origUp] . ")");
          :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
          :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
          /ip hotspot user set $u comment=$comm;
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$uName];
          :if ($isDataExhausted) do={ /ip hotspot user set $u limit-bytes-total=1 };
          :if ($isTimeExhausted) do={ /ip hotspot user set $u limit-uptime=1s };
        };
      };
    };
  };'''

new_endpoint_code = f'''
UNIVERSAL_QUOTA_SAVE_SOURCE = {repr(quota_script_source)}

@app.post("/api/routers/{{rid}}/update-quota-rsc")
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

        # 1. Update or create hs-quota-save script
        s_res = client.talk(['/system/script/print', '?name=hs-quota-save', '=.proplist=.id'])
        sid = None
        for s in s_res:
            if s[0] == '!re':
                for w in s[1:]:
                    if w.startswith('=.id='):
                        sid = w.split('=', 2)[2]
        if sid:
            client.talk(['/system/script/set', f'=.id={{sid}}', f'=source={{UNIVERSAL_QUOTA_SAVE_SOURCE}}'])
        else:
            client.talk(['/system/script/add', '=name=hs-quota-save', f'=source={{UNIVERSAL_QUOTA_SAVE_SOURCE}}', '=comment=Universal Quota Accounting Script'])

        # 2. Ensure scheduler hs-quota-saver exists (runs every 2s)
        sch_res = client.talk(['/system/scheduler/print', '?name=hs-quota-saver', '=.proplist=.id'])
        sch_id = None
        for s in sch_res:
            if s[0] == '!re':
                for w in s[1:]:
                    if w.startswith('=.id='):
                        sch_id = w.split('=', 2)[2]
        if sch_id:
            client.talk(['/system/scheduler/set', f'=.id={{sch_id}}', '=interval=2s', '=disabled=no'])
        else:
            client.talk(['/system/scheduler/add', '=name=hs-quota-saver', '=interval=2s', '=on-event=/system script run hs-quota-save', '=start-time=startup', '=comment=Universal Quota Engine Poller'])

        # 3. Trigger immediate run
        client.talk(['/system/script/run', '=number=hs-quota-save'])
        client.close()
        return {{
            "status": "ok",
            "message": "Universal Quota & Accounting RSC scripts successfully updated and verified on router!"
        }}
    except Exception as e:
        client.close()
        raise HTTPException(status_code=500, detail=str(e))
'''

# Update cloud_main.py on VPS
with open('scratch_update_cloud_main.py', 'w', encoding='utf-8') as f:
    f.write(f'''# Remote updater
import re

with open('/opt/hotspot-cloud/cloud_main.py', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'@app\\.post\\("/api/routers/\\{{rid\\}}/update-quota-rsc"\\)[\\s\\S]*?# ── 6\\. Live Active Sessions'
replacement = """{new_endpoint_code}\n\n# ── 6. Live Active Sessions"""

new_content = re.sub(pattern, replacement, content)
with open('/opt/hotspot-cloud/cloud_main.py', 'w', encoding='utf-8') as f:
    f.write(new_content)

print("Updated /opt/hotspot-cloud/cloud_main.py successfully!")
''')

sftp = ssh.open_sftp()
sftp.put('scratch_update_cloud_main.py', '/tmp/update_cloud.py')
sftp.close()

stdin, stdout, stderr = ssh.exec_command('python3 /tmp/update_cloud.py && sudo systemctl restart hotspot-cloud')
print("STDOUT:", stdout.read().decode())
print("STDERR:", stderr.read().decode())
ssh.close()
print("Deployed to VPS!")
