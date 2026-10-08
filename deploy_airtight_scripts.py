import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')

v_activate_src = r''':global hsUser;
:local u $hsUser;
:if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };

:local uList [/ip hotspot user find name=$u];
:if ([:len $uList] = 0) do={ :return "" };
:local uObj [:pick $uList 0];

:local curComm [/ip hotspot user get $uObj comment];
:local isFirstLogin true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };

# Bind MAC address if not already bound
:do {
  :local m "";
  :foreach a in=[/ip hotspot active find user=$u] do={
    :set m [/ip hotspot active get $a mac-address];
  };
  :if ([:len $m] > 0) do={
    /ip hotspot user set $uObj mac-address=$m;
  };
} on-error={};

:local parseTag do={
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

:local uProf [/ip hotspot user get $uObj profile];
:local curLim [/ip hotspot user get $uObj limit-bytes-total];
:local curUp [/ip hotspot user get $uObj limit-uptime];

# Resolve ORIG-LIMIT
:local origLim 0;
:local lStr [$parseTag comm=$curComm tag="[ORIG-LIMIT:"];
:if ([:len $lStr] > 0) do={ :set origLim [:tonum $lStr] };

:if ($origLim = 0) do={
  :local cUpper ($uProf . " " . $curComm);
  :if ($cUpper ~ "100GB" or $cUpper ~ "100G") do={ :set origLim 107374182400 };
  :if ($cUpper ~ "50GB" or $cUpper ~ "50G") do={ :set origLim 53687091200 };
  :if ($cUpper ~ "30GB" or $cUpper ~ "30Day" or $cUpper ~ "30G") do={ :set origLim 32212254720 };
  :if ($cUpper ~ "20GB" or $cUpper ~ "20G") do={ :set origLim 21474836480 };
  :if ($cUpper ~ "15GB" or $cUpper ~ "15G") do={ :set origLim 16106127360 };
  :if ($cUpper ~ "10GB" or $cUpper ~ "10G") do={ :set origLim 10737418240 };
  :if ($cUpper ~ "7GB" or $cUpper ~ "7G") do={ :set origLim 7516192768 };
  :if ($cUpper ~ "5GB" or $cUpper ~ "5G") do={ :set origLim 5368709120 };
  :if ($cUpper ~ "4GB" or $cUpper ~ "4G") do={ :set origLim 4294967296 };
  :if ($cUpper ~ "3GB" or $cUpper ~ "3G") do={ :set origLim 3221225472 };
  :if ($cUpper ~ "2GB" or $cUpper ~ "2G") do={ :set origLim 2147483648 };
  :if ($cUpper ~ "1.5GB" or $cUpper ~ "1.7GB" or $cUpper ~ "1700MB" or $uProf = "1000Ks") do={ :set origLim 1782579200 };
  :if ($cUpper ~ "1GB" or $cUpper ~ "1G") do={ :set origLim 1073741824 };
  :if ($cUpper ~ "750MB" or $uProf = "500Ks") do={ :set origLim 786432000 };
  :if ($cUpper ~ "500MB" or $cUpper ~ "500M") do={ :set origLim 524288000 };
  :if ($cUpper ~ "250MB" or $cUpper ~ "250M") do={ :set origLim 262144000 };
  :if ($cUpper ~ "100MB" or $cUpper ~ "100M") do={ :set origLim 104857600 };
  :if ($cUpper ~ "50MB" or $cUpper ~ "50M" or $cUpper ~ "test") do={ :set origLim 52428800 };
  :if ($origLim = 0 and $curLim > 0) do={ :set origLim $curLim };
};

:if ($origLim > 0) do={
  :set curComm [$setTag comm=$curComm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
  :local baseBytes 0;
  :local bStr [$parseTag comm=$curComm tag="[BASE:"];
  :if ([:len $bStr] > 0) do={ :set baseBytes [:tonum $bStr] };

  :local usedBytes 0;
  :local udStr [$parseTag comm=$curComm tag="[USED:"];
  :if ([:len $udStr] > 0) do={ :set usedBytes [:tonum $udStr] };

  # Effective already consumed bytes
  :local priorBytes $baseBytes;
  :if ($usedBytes > $priorBytes) do={ :set priorBytes $usedBytes };

  :local remBytes ($origLim - $priorBytes);
  :if ($remBytes <= 0) do={
    :log warning ("Hotspot: User " . $u . " ALREADY EXHAUSTED DATA! REJECTING.");
    :do { /ip hotspot user set $uObj comment=$curComm disabled=yes limit-bytes-total=1 } on-error={};
    :do { /ip hotspot active remove [find user=$u] } on-error={};
    :do { /ip hotspot cookie remove [find user=$u] } on-error={};
    :return "";
  } else={
    # Hardware/kernel enforcement: set exact remaining bytes for this session
    :do { /ip hotspot user set $uObj limit-bytes-total=$remBytes } on-error={};
  };
};

# Resolve ORIG-UP
:local origUp [:totime "0s"];
:local uStr [$parseTag comm=$curComm tag="[ORIG-UP:"];
:if ([:len $uStr] > 0) do={ :set origUp [:totime $uStr] };

:if ($origUp = [:totime "0s"]) do={
  :local vIdx [:find $curComm "V:"];
  :if ($vIdx >= 0) do={
    :local rest [:pick $curComm ($vIdx + 2) [:len $curComm]];
    :local spIdx [:find $rest " "];
    :if ($spIdx > 0) do={ :set rest [:pick $rest 0 $spIdx] };
    :local brIdx [:find $rest "]"];
    :if ($brIdx > 0) do={ :set rest [:pick $rest 0 $brIdx] };
    :if ([:len $rest] > 0) do={
      :do { :set origUp [:totime $rest] } on-error={};
    };
  };
};

:if ($origUp = [:totime "0s"]) do={
  :local cUpper ($uProf . " " . $curComm);
  :if ($cUpper ~ "30D" or $cUpper ~ "30Day" or $cUpper ~ "Month") do={ :set origUp [:totime "30d"] };
  :if ($cUpper ~ "14D" or $cUpper ~ "2W") do={ :set origUp [:totime "14d"] };
  :if ($cUpper ~ "7D" or $cUpper ~ "1W") do={ :set origUp [:totime "7d"] };
  :if ($cUpper ~ "3D") do={ :set origUp [:totime "3d"] };
  :if ($cUpper ~ "2D") do={ :set origUp [:totime "2d"] };
  :if ($cUpper ~ "1D" or $cUpper ~ "24H" or $uProf = "5GB" or $uProf = "2GB" or $uProf = "1000Ks" or $uProf = "500Ks") do={ :set origUp [:totime "1d"] };
  :if ($cUpper ~ "12H") do={ :set origUp [:totime "12h"] };
  :if ($cUpper ~ "8H") do={ :set origUp [:totime "8h"] };
  :if ($cUpper ~ "6H") do={ :set origUp [:totime "6h"] };
  :if ($cUpper ~ "4H") do={ :set origUp [:totime "4h"] };
  :if ($cUpper ~ "3H") do={ :set origUp [:totime "3h"] };
  :if ($cUpper ~ "2H" or $uProf = "2Hour") do={ :set origUp [:totime "2h"] };
  :if ($cUpper ~ "1H") do={ :set origUp [:totime "1h"] };
  :if ($cUpper ~ "45M") do={ :set origUp [:totime "45m"] };
  :if ($cUpper ~ "30M") do={ :set origUp [:totime "30m"] };
  :if ($cUpper ~ "15M") do={ :set origUp [:totime "15m"] };
  :if ($cUpper ~ "3M" or $uProf = "test") do={ :set origUp [:totime "3m"] };
  :if ($origUp = [:totime "0s"] and $curUp != "00:00:00" and $curUp != "0s" and [:len $curUp] > 0) do={ :set origUp [:totime $curUp] };
};

:if ($origUp > [:totime "0s"]) do={
  :set curComm [$setTag comm=$curComm tag="[ORIG-UP:" val=[:tostr $origUp]];
  :local baseUp [:totime "0s"];
  :local buStr [$parseTag comm=$curComm tag="[BASE-UP:"];
  :if ([:len $buStr] > 0) do={ :set baseUp [:totime $buStr] };

  :local usedUp [:totime "0s"];
  :local uuStr [$parseTag comm=$curComm tag="[USED-UP:"];
  :if ([:len $uuStr] > 0) do={ :set usedUp [:totime $uuStr] };

  :local priorUp $baseUp;
  :if ($usedUp > $priorUp) do={ :set priorUp $usedUp };

  :local remUp ($origUp - $priorUp);
  :if ($remUp <= [:totime "0s"]) do={
    :log warning ("Hotspot: User " . $u . " ALREADY EXHAUSTED TIME! REJECTING.");
    :do { /ip hotspot user set $uObj comment=$curComm disabled=yes limit-uptime=1s } on-error={};
    :do { /ip hotspot active remove [find user=$u] } on-error={};
    :do { /ip hotspot cookie remove [find user=$u] } on-error={};
    :return "";
  } else={
    :do { /ip hotspot user set $uObj limit-uptime=$remUp } on-error={};
  };
};

:local cDate [/system clock get date];
:local cTime [/system clock get time];

:if ($isFirstLogin) do={
  :set curComm [$setTag comm=$curComm tag="[ACT:" val=([:tostr $cDate] . " " . [:tostr $cTime])];
  /ip hotspot user set $uObj comment=$curComm;
  :log info ("Hotspot: Voucher " . $u . " activated! (Quota: " . [:tostr $origLim] . ", Up: " . [:tostr $origUp] . ")");

  :if ($origUp > [:totime "0s"]) do={
    :do {
      /system scheduler add name=$u start-date=$cDate start-time=$cTime interval=$origUp \
        on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user set [find name=\"" . $u . "\"] disabled=yes limit-uptime=1s; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
        comment=("Voucher continuous timer: " . [:tostr $origUp] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);
    } on-error={
      :do {
        /system scheduler add name=$u start-time=startup interval=$origUp \
          on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user set [find name=\"" . $u . "\"] disabled=yes limit-uptime=1s; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
          comment=("Voucher continuous timer: " . [:tostr $origUp] . " (fallback)");
      } on-error={};
    };
  };
} else={
  /ip hotspot user set $uObj comment=$curComm;
};
'''

hs_save_src = r''':local parseTag do={
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

      # Only promote base if counters reset due to router reboot
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

      :local isDataExhausted false;
      :if ($origLim > 0 and $totalUsed >= $origLim) do={ :set isDataExhausted true };

      :local isTimeExhausted false;
      :if ($origUp > [:totime "0s"] and $totalUp >= $origUp) do={ :set isTimeExhausted true };

      :if ($isDataExhausted or $isTimeExhausted) do={
        :log warning ("Hotspot: User " . $uName . " EXHAUSTED - IMMEDIATE AIRTIGHT CUTOFF! (Data: " . [:tostr $totalUsed] . "/" . [:tostr $origLim] . ", Time: " . [:tostr $totalUp] . "/" . [:tostr $origUp] . ")");
        :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
        :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
        :set comm [$setTag comm=$comm tag="[BASE:" val=[:tostr $totalUsed]];
        :set comm [$setTag comm=$comm tag="[BASE-UP:" val=[:tostr $totalUp]];
        :do { /ip hotspot user set $u comment=$comm disabled=yes limit-bytes-total=1 limit-uptime=1s } on-error={};
        :do { /ip hotspot active remove [find user=$uName] } on-error={};
        :do { /ip hotspot cookie remove [find user=$uName] } on-error={};
      } else={
        # Persist live progress every 32KB or 2s
        :local deltaBytes ($totalUsed - $curStoredUsed);
        :if ($deltaBytes < 0) do={ :set deltaBytes (-$deltaBytes) };
        :if (($totalUsed > 0 and $deltaBytes >= 32768) or ($totalUsed != $curStoredUsed and $sessionBytes = 0) or ($totalUp != $curStoredUp and ($totalUp - $curStoredUp) >= [:totime "2s"])) do={
          :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
          :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
          :do { /ip hotspot user set $u comment=$comm } on-error={};
        };

        # Dynamic Hardware Ceiling: Keep limit-bytes-total set to exact remaining bytes
        :if ($origLim > 0) do={
          :local remBytes ($origLim - $totalUsed);
          :if ($remBytes > 0 and ($curLim = 0 or $remBytes < $curLim)) do={
            :do { /ip hotspot user set $u limit-bytes-total=$remBytes } on-error={};
          };
        };
      };
    };
  };
};
'''

hs_logout_src = r''':local uName $user;
:if ([:len $uName] > 0 and $uName != "admin" and $uName != "default-trial") do={
  :local uList [/ip hotspot user find name=$uName];
  :if ([:len $uList] > 0) do={
    :local u [:pick $uList 0];
    :local comm [/ip hotspot user get $u comment];

    # 1. Sync BASE bytes from USED
    :local uIdx [:find $comm "[USED:"];
    :if ($uIdx >= 0) do={
      :local rest [:pick $comm ($uIdx + 6) [:len $comm]];
      :local endIdx [:find $rest "]"];
      :if ($endIdx >= 0) do={ :set rest [:pick $rest 0 $endIdx] };
      :local usedBytes [:tonum $rest];
      :if ($usedBytes > 0) do={
        :local bIdx [:find $comm "[BASE:"];
        :if ($bIdx >= 0) do={
          :local bEnd [:find $comm "]" $bIdx];
          :local prefix [:pick $comm 0 $bIdx];
          :local suffix "";
          :if ($bEnd >= 0) do={ :set suffix [:pick $comm ($bEnd + 1) [:len $comm]] };
          :set comm ($prefix . "[BASE:" . [:tostr $usedBytes] . "]" . $suffix);
        } else={
          :set comm ($comm . " [BASE:" . [:tostr $usedBytes] . "]");
        };
      };
    };

    # 2. Sync BASE-UP uptime from USED-UP
    :local uuIdx [:find $comm "[USED-UP:"];
    :if ($uuIdx >= 0) do={
      :local restUp [:pick $comm ($uuIdx + 9) [:len $comm]];
      :local endUp [:find $restUp "]"];
      :if ($endUp >= 0) do={ :set restUp [:pick $restUp 0 $endUp] };
      :if ([:len $restUp] > 0) do={
        :local buIdx [:find $comm "[BASE-UP:"];
        :if ($buIdx >= 0) do={
          :local buEnd [:find $comm "]" $buIdx];
          :local prefixUp [:pick $comm 0 $buIdx];
          :local suffixUp "";
          :if ($buEnd >= 0) do={ :set suffixUp [:pick $comm ($buEnd + 1) [:len $comm]] };
          :set comm ($prefixUp . "[BASE-UP:" . $restUp . "]" . $suffixUp);
        } else={
          :set comm ($comm . " [BASE-UP:" . $restUp . "]");
        };
      };
    };

    :do { /ip hotspot user set $u comment=$comm } on-error={};
  };
};
'''

import json

remote_code = f"""import sys, json
sys.path.insert(0, '/opt/hotspot-cloud')
import cloud_mikrotik as mk
import cloud_database as db

v_activate_src = {json.dumps(v_activate_src)}
hs_save_src = {json.dumps(hs_save_src)}
hs_logout_src = {json.dumps(hs_logout_src)}

conn = db.get_db()
c = conn.cursor()
c.execute('SELECT * FROM routers WHERE id = 3')
r = c.fetchone()
cli = mk.RouterOSClient(r['wg_ip'], r['api_port'], timeout=10.0)
cli.connect()
if cli.login(r['api_user'], r['api_pass']):
    # 1. Update voucher-activate
    v_res = cli.talk(['/system/script/print', '?name=voucher-activate', '=.proplist=.id'])
    for sc in v_res:
        if sc[0] == '!re':
            d = dict(item.lstrip('=').split('=', 1) for item in sc[1:] if '=' in item)
            sid = d.get('.id')
            cli.talk(['/system/script/set', f'=.id={{sid}}', '=source=' + v_activate_src])
            print('Updated voucher-activate successfully!')

    # 2. Update hs-quota-save
    s_res = cli.talk(['/system/script/print', '?name=hs-quota-save', '=.proplist=.id'])
    for sc in s_res:
        if sc[0] == '!re':
            d = dict(item.lstrip('=').split('=', 1) for item in sc[1:] if '=' in item)
            sid = d.get('.id')
            cli.talk(['/system/script/set', f'=.id={{sid}}', '=source=' + hs_save_src])
            print('Updated hs-quota-save successfully!')

    # 3. Update hs-on-logout
    l_res = cli.talk(['/system/script/print', '?name=hs-on-logout', '=.proplist=.id'])
    for sc in l_res:
        if sc[0] == '!re':
            d = dict(item.lstrip('=').split('=', 1) for item in sc[1:] if '=' in item)
            sid = d.get('.id')
            cli.talk(['/system/script/set', f'=.id={{sid}}', '=source=' + hs_logout_src])
            print('Updated hs-on-logout successfully!')

    cli.close()
"""

sftp = ssh.open_sftp()
with sftp.file('/tmp/deploy_airtight.py', 'w') as f:
    f.write(remote_code)
sftp.close()

stdin, stdout, stderr = ssh.exec_command('/opt/hotspot-cloud/venv/bin/python /tmp/deploy_airtight.py')
print('OUT:\n', stdout.read().decode())
print('ERR:\n', stderr.read().decode())
ssh.close()
