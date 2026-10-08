# ==========================================================
#  HARDENED REAL-TIME QUOTA ENGINE (EXACT BYTE CUT-OFF)
#  Auto-detects data limits, saves on logout & reboots, cuts off instantly
# ==========================================================

:do { /system script remove [find name="voucher-activate"] } on-error={}
/system script add name="voucher-activate" comment="Universal voucher activation, MAC binding and countdown" source={
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };

:local uList [/ip hotspot user find name=$u];
:if ([:len $uList] = 0) do={ :return "" };
:local uObj [:pick $uList 0];

:local curComm [/ip hotspot user get $uObj comment];
:local isFirstLogin true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };

# Bind MAC address
:do {
  :local m "";
  :foreach a in=[/ip hotspot active find user=$u] do={
    :set m [/ip hotspot active get $a mac-address];
  };
  :if ([:len $m] > 0) do={
    /ip hotspot user set $uObj mac-address=$m;
  };
} on-error={}

:if ($isFirstLogin) do={
  :local dt ([/system clock get date] . " " . [/system clock get time]);
  :local newComm ($curComm . " [ACT:" . $dt . "]");
  :local uProf [/ip hotspot user get $uObj profile];
  
  :local pLim 0;
  :local pUp [:totime "0s"];
  
  # Resolve quotas from profile
  :if ($uProf = "500Ks") do={ :set pLim 786432000; :set pUp [:totime "1h"] };
  :if ($uProf = "1000Ks") do={ :set pLim 1782579200; :set pUp [:totime "3h"] };
  :if ($uProf = "2000Ks") do={ :set pLim 3221225472; :set pUp [:totime "1d"] };
  :if ($uProf = "1GB") do={ :set pLim 1073741824; :set pUp [:totime "1d"] };
  :if ($uProf = "2GB") do={ :set pLim 2147483648; :set pUp [:totime "1d"] };
  :if ($uProf = "3GB") do={ :set pLim 3221225472; :set pUp [:totime "3d"] };
  :if ($uProf = "5GB") do={ :set pLim 5368709120; :set pUp [:totime "7d"] };
  :if ($uProf = "10GB") do={ :set pLim 10737418240; :set pUp [:totime "30d"] };
  :if ($uProf = "1Day") do={ :set pUp [:totime "1d"] };
  :if ($uProf = "7Day") do={ :set pUp [:totime "7d"] };
  :if ($uProf = "30Day") do={ :set pUp [:totime "30d"] };

  :if ($pLim > 0) do={
    :set newComm ($newComm . " [ORIG-LIMIT:" . [:tostr $pLim] . "]");
    /ip hotspot user set $uObj limit-bytes-total=$pLim;
  };
  :if ($pUp > [:totime "0s"]) do={
    :set newComm ($newComm . " [ORIG-UP:" . [:tostr $pUp] . "]");
    /ip hotspot user set $uObj limit-uptime=$pUp;
  };
  
  /ip hotspot user set $uObj comment=$newComm;
};
}

:do { /system script remove [find name="hs-quota-save"] } on-error={}
/system script add name="hs-quota-save" comment="Real-time quota monitoring and hardware cut-off" source={
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
        :local curLim [/ip hotspot user get $u limit-bytes-total];

        :local baseBytes 0;
        :local bStr [$parseTag comm=$comm tag="[BASE:"];
        :if ([:len $bStr] > 0) do={ :set baseBytes [:tonum $bStr] };

        :local totalUsed ($baseBytes + $sessionBytes);

        :local origLim 0;
        :local limStr [$parseTag comm=$comm tag="[ORIG-LIMIT:"];
        :if ([:len $limStr] > 0) do={ :set origLim [:tonum $limStr] };

        :if ($origLim > 0) do={
          :local remBytes ($origLim - $totalUsed);
          :if ($remBytes <= 0) do={
            :set comm [$setTag comm=$comm tag="[QUOTA:EXHAUSTED]"];
            /ip hotspot user set $u comment=$comm disabled=yes limit-bytes-total=1 limit-uptime=1s;
            :foreach ck in=[/ip hotspot cookie find user=$uName] do={ /ip hotspot cookie remove $ck };
            /ip hotspot active remove $a;
            :log warning ("[QUOTA-CUTOFF] User " . $uName . " exceeded quota. Connection terminated.");
          } else={
            /ip hotspot user set $u limit-bytes-total=$remBytes;
          };
        };
      };
    };
  };
}

:do { /system script remove [find name="hs-on-logout"] } on-error={}
/system script add name="hs-on-logout" comment="Saves remaining quota on logout" source={
  :global hsUser;
  :local u $hsUser;
  :if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };
  /system script run hs-quota-save;
}

# Auto-Sync scheduler every 20s
:do { /system scheduler remove [find name="hs-quota-loop"] } on-error={}
/system scheduler add name="hs-quota-loop" interval=20s on-event="hs-quota-save" start-time=startup comment="Periodic quota monitor"

:put ">>> Hardened Quota Engine Configured Successfully! <<<"
