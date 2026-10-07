
# ==============================================================================
#           UNIVERSAL MIKROTIK HOTSPOT SETUP SCRIPT (PHONE GENERATED)
#     Compatible with RouterOS v6.x, v7.x | Direct IP Mode (No DNS Required)
# ==============================================================================

:put "=========================================================="
:put "   STARTING UNIVERSAL CAPTIVE PORTAL PROVISIONING          "
:put "=========================================================="

:global siteName "YadanarTun"
:global wifiSsid "YadanarTun"
:global dnsName ""
:global adminPass "Khant1234@"
:global ipCapacity 250

# 1. ADMIN CREDENTIALS
:do {
  /user set [find name="admin"] password=Khant1234@
  :put ("Admin Password set to: " . Khant1234@)
} on-error={}

# 2. DYNAMIC SUBNET & IP POOL
:local prefix 24
:local netmask "255.255.255.0"
:local netCidr "10.10.10.0/24"
:local netAddr "10.10.10.0"
:local gwIp "10.10.10.1"
:local poolStart "10.10.10.10"
:local poolEnd "10.10.10.254"
:local leaseTime "1d"

:if (250 > 250) do={
  :set prefix 22
  :set netmask "255.255.252.0"
  :set netCidr "10.10.8.0/22"
  :set netAddr "10.10.8.0"
  :set gwIp "10.10.8.1"
  :set poolStart "10.10.8.10"
  :set poolEnd "10.10.11.254"
}

# 3. INTERFACE BRIDGE
:do {
  /interface bridge add name=hotspot-bridge protocol-mode=rstp comment="Hotspot LAN Bridge"
} on-error={
  :do { /interface bridge set [find name=hotspot-bridge] protocol-mode=rstp } on-error={}
}

# 4. BRIDGE PORTS 2..N
:foreach p in=[/interface ethernet find] do={
  :local pName [/interface ethernet get $p name]
  :if ($pName != "ether1") do={
    :do {
      /interface bridge port add bridge=hotspot-bridge interface=$pName
    } on-error={}
  }
}

# 5. WI-FI AP CONFIGURATION (v7 wifi vs v6 wireless)
:local wifiConfigured false

# RouterOS v7 wifi
:do {
  :local v7Cmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set $w configuration.mode=ap configuration.ssid=\"YadanarTun\" configuration.hide-ssid=no datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set $w mode=ap ssid=\"YadanarTun\" disabled=no }; :local wName [/interface wifi get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
  [ :parse $v7Cmd ]
  :if ([:len [/interface wifi find]] > 0) do={ :set wifiConfigured true }
} on-error={}

# RouterOS v6 wireless
:if (!$wifiConfigured) do={
  :do {
    :local legacyCmd (":foreach w in=[/interface wireless find] do={ /interface wireless set $w ssid=\"YadanarTun\" hide-ssid=no mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :local wName [/interface wireless get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
    [ :parse $legacyCmd ]
    :if ([:len [/interface wireless find]] > 0) do={ :set wifiConfigured true }
  } on-error={}
}

# 6. IP ADDRESSING & DHCP SERVER
:local fullGwAddr ($gwIp . "/" . $prefix)

:do {
  /ip address add address=$fullGwAddr network=$netAddr interface=hotspot-bridge comment="Hotspot Gateway"
} on-error={
  :do { /ip address set [find interface=hotspot-bridge] address=$fullGwAddr network=$netAddr } on-error={}
}

:do {
  /ip pool add name=hs-pool ranges=($poolStart . "-" . $poolEnd)
} on-error={
  :do { /ip pool set [find name=hs-pool] ranges=($poolStart . "-" . $poolEnd) } on-error={}
}

:do {
  /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=$leaseTime authoritative=yes disabled=no
} on-error={
  :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool lease-time=$leaseTime disabled=no } on-error={}
}

:do {
  /ip dhcp-server network add address=$netCidr gateway=$gwIp netmask=$prefix dns-server=$gwIp comment="Hotspot Network"
} on-error={
  :do { /ip dhcp-server network set [find address=$netCidr] gateway=$gwIp netmask=$prefix dns-server=$gwIp } on-error={}
}

:do {
  /ip dns set allow-remote-requests=yes servers="8.8.8.8,1.1.1.1" cache-size=2048KiB
} on-error={}

# 7. WAN DHCP & NAT MASQUERADE
:do {
  /ip dhcp-client add interface=ether1 disabled=no comment="WAN Starlink/ISP"
} on-error={
  :do { /ip dhcp-client set [find interface=ether1] disabled=no } on-error={}
}

:do {
  /ip firewall nat add chain=srcnat out-interface=ether1 action=masquerade comment="WAN NAT Masquerade"
} on-error={}

# 8. CAPTIVE PORTAL STORAGE DIRECTORY
:local hsDir "hotspot"
:if ([:len [/file find name="flash/hotspot"]] > 0) do={ :set hsDir "flash/hotspot" }

# 9. HOTSPOT SERVER PROFILE & SERVER (DIRECT IP GATEWAY, NO DNS NAME)
:do {
  /ip hotspot profile add name=hs-profile hotspot-address=$gwIp dns-name="" html-directory=$hsDir \
    login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name="" html-directory=$hsDir \
      login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
} on-error={
  :do { /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no } on-error={}
}

# 10. INSTANT CAPTIVE PORTAL CNA & DNS HIJACKING (APPLE, ANDROID, WINDOWS, XIAOMI)
# Clean up any stale probe domains from walled garden so router always intercepts probe requests
:do {
  /ip hotspot walled-garden remove [find comment="Fast CNA Detection"]
} on-error={}

# Reject DoT (port 853) with immediate TCP reset so Android Private DNS falls back instantly to standard DNS
:do {
  /ip firewall filter add chain=input protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}
:do {
  /ip firewall filter add chain=forward protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}

# Force all DNS queries (port 53 UDP/TCP) to the local router even if clients have hardcoded 8.8.8.8 or 1.1.1.1
:do {
  /ip firewall nat add chain=dstnat in-interface=hotspot-bridge protocol=udp dst-port=53 action=redirect to-ports=53 place-before=0 comment="Force DNS to Router"
} on-error={}
:do {
  /ip firewall nat add chain=dstnat in-interface=hotspot-bridge protocol=tcp dst-port=53 action=redirect to-ports=53 place-before=0 comment="Force DNS to Router"
} on-error={}

# Allow DNS and Hotspot HTTP on LAN bridge
:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=udp dst-port=53 action=accept comment="Allow Hotspot DNS UDP"
} on-error={}
:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=tcp dst-port=53 action=accept comment="Allow Hotspot DNS TCP"
} on-error={}
:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=tcp dst-port=80 action=accept comment="Allow Hotspot HTTP"
} on-error={}

# 11. SCRIPT 1: voucher-activate (UNIVERSAL QUOTA & DURATION RESOLVER)
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
  :local remBytes ($origLim - $baseBytes);
  :if ($remBytes <= 0) do={
    /ip hotspot user set $uObj limit-bytes-total=1;
    /ip hotspot active remove [find user=$u];
    /ip hotspot cookie remove [find user=$u];
  } else={
    /ip hotspot user set $uObj limit-bytes-total=$remBytes;
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
  :local remUp ($origUp - $baseUp);
  :if ($remUp <= [:totime "0s"]) do={
    /ip hotspot user set $uObj limit-uptime=1s;
    /ip hotspot active remove [find user=$u];
    /ip hotspot cookie remove [find user=$u];
  } else={
    /ip hotspot user set $uObj limit-uptime=$remUp;
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
        on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
        comment=("Voucher continuous timer: " . [:tostr $origUp] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);
    } on-error={
      :do {
        /system scheduler add name=$u start-time=startup interval=$origUp \
          on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
          comment=("Voucher continuous timer: " . [:tostr $origUp] . " (fallback)");
      } on-error={};
    };
  };
} else={
  /ip hotspot user set $uObj comment=$curComm;
};
}

# 12. SCRIPT 2: hs-quota-save (CONTINUOUS 5-SECOND SYNC & EXACT CUTOFF)
:do { /system script remove [find name="hs-quota-save"] } on-error={}
/system script add name="hs-quota-save" comment="Persists user data usage and continuous time every 5s" source={
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

        :local totalUsed ($baseBytes + $sessionBytes);
        :local totalUp ($baseUp + $sUp);

        :local origLim [$resolveOrigLim parseTag=$parseTag prof=$uProf comm=$comm curLim=$curLim];
        :local origUp [$resolveOrigUp parseTag=$parseTag prof=$uProf comm=$comm curUp=$curUp];

        :if ([:find $comm "[ORIG-LIMIT:"] < 0 and $origLim > 0) do={
          :set comm [$setTag comm=$comm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
        };
        :if ([:find $comm "[ORIG-UP:"] < 0 and $origUp > [:totime "0s"]) do={
          :set comm [$setTag comm=$comm tag="[ORIG-UP:" val=[:tostr $origUp]];
        };

        :local curStoredUsed 0;
        :local uStr [$parseTag comm=$comm tag="[USED:"];
        :if ([:len $uStr] > 0) do={ :set curStoredUsed [:tonum $uStr] };

        :local curStoredUp [:totime "0s"];
        :local uuStr [$parseTag comm=$comm tag="[USED-UP:"];
        :if ([:len $uuStr] > 0) do={ :set curStoredUp [:totime $uuStr] };

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
  };
}

:do { /system scheduler remove [find name="hs-quota-saver"] } on-error={}
/system scheduler add name="hs-quota-saver" interval=2s start-time=startup \
  on-event="/system script run hs-quota-save" comment="Auto-persist user bytes every 5s"

# 13. SCRIPT 3: hs-on-logout (BASE SYNC ON DISCONNECT)
:do { /system script remove [find name="hs-on-logout"] } on-error={}
/system script add name="hs-on-logout" comment="Syncs base quota and continuous time on logout" source={
  :local uName $user;
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

      /ip hotspot user set $u comment=$comm;
    };
  };
}

# 14. SCRIPT 4: hs-quota-restore (BOOT RESTORATION & TIME DEDUCTION ACROSS REBOOTS)
:do { /system script remove [find name="hs-quota-restore"] } on-error={}
/system script add name="hs-quota-restore" comment="Restores quota and continuous time on router boot" source={
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

  # Clock check compatible with RouterOS v6 and v7
  :local dStr [/system clock get date];
  :local cYear 1970;
  :if ([:len $dStr] >= 10) do={
    :if ([:find $dStr "-"] >= 0) do={
      :set cYear [:tonum [:pick $dStr 0 4]];
    } else={
      :set cYear [:tonum [:pick $dStr 7 11]];
    };
  };
  :if ($cYear < 2025) do={ :delay 5s };

  :foreach u in=[/ip hotspot user find] do={
    :local uName [/ip hotspot user get $u name];
    :if ($uName != "admin" and $uName != "default-trial") do={
      :local comm [/ip hotspot user get $u comment];
      :local uProf [/ip hotspot user get $u profile];
      :local curLim [/ip hotspot user get $u limit-bytes-total];
      :local curUp [/ip hotspot user get $u limit-uptime];

      :local usedBytes 0;
      :local uStr [$parseTag comm=$comm tag="[USED:"];
      :if ([:len $uStr] > 0) do={ :set usedBytes [:tonum $uStr] };

      :local usedUp [:totime "0s"];
      :local uuStr [$parseTag comm=$comm tag="[USED-UP:"];
      :if ([:len $uuStr] > 0) do={ :set usedUp [:totime $uuStr] };

      :local origLim [$resolveOrigLim parseTag=$parseTag prof=$uProf comm=$comm curLim=$curLim];
      :local origUp [$resolveOrigUp parseTag=$parseTag prof=$uProf comm=$comm curUp=$curUp];

      :if ([:find $comm "[ORIG-LIMIT:"] < 0 and $origLim > 0) do={
        :set comm [$setTag comm=$comm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
      };
      :if ([:find $comm "[ORIG-UP:"] < 0 and $origUp > [:totime "0s"]) do={
        :set comm [$setTag comm=$comm tag="[ORIG-UP:" val=[:tostr $origUp]];
      };

      :if ($usedBytes > 0 or $usedUp > [:totime "0s"]) do={
        :if ($usedBytes > 0) do={
          :set comm [$setTag comm=$comm tag="[BASE:" val=[:tostr $usedBytes]];
        };
        :if ($usedUp > [:totime "0s"]) do={
          :set comm [$setTag comm=$comm tag="[BASE-UP:" val=[:tostr $usedUp]];
        };
        /ip hotspot user set $u comment=$comm;

        # Reset RAM counters so new boot session counts cleanly up to remaining allowance
        /ip hotspot user reset-counters $u;

        # Deduct consumed quota
        :if ($origLim > 0) do={
          :if ($usedBytes >= $origLim) do={
            /ip hotspot active remove [find user=$uName];
            /ip hotspot cookie remove [find user=$uName];
            /ip hotspot user set $u limit-bytes-total=1;
          } else={
            :local remLim ($origLim - $usedBytes);
            /ip hotspot user set $u limit-bytes-total=$remLim;
          };
        };

        # Deduct consumed uptime
        :if ($origUp > [:totime "0s"]) do={
          :if ($usedUp >= $origUp) do={
            /ip hotspot active remove [find user=$uName];
            /ip hotspot cookie remove [find user=$uName];
            /ip hotspot user set $u limit-uptime=1s;
          } else={
            :local remUp ($origUp - $usedUp);
            /ip hotspot user set $u limit-uptime=$remUp;
          };
        };

        # Check if user had a continuous validity timer scheduler that expired during shutdown
        :local sc [/system scheduler find name=$uName];
        :if ([:len $sc] > 0) do={
          :local nr [/system scheduler get $sc next-run];
          :if ([:len $nr] = 0 or $nr = "none" or $nr = "never") do={
            :log info ("Hotspot: User " . $uName . " validity timer expired during power outage");
            /ip hotspot active remove [find user=$uName];
            /ip hotspot cookie remove [find user=$uName];
            /ip hotspot user set $u limit-uptime=1s;
            /system scheduler remove $sc;
          };
        };
      };
    };
  };
}

:do { /system scheduler remove [find name="hs-quota-restorer"] } on-error={}
/system scheduler add name="hs-quota-restorer" start-time=startup interval=0s \
  on-event="/system script run hs-quota-restore" comment="Restore quota limits on router boot"

# 15. USER PROFILE PERSISTENCE & ROAMING
:local macFixScript ":local u $user; :local m $\"mac-address\"; :do { /ip hotspot active remove [find user=$u and mac-address!=$m]; /ip hotspot cookie remove [find user=$u and mac-address!=$m]; /ip hotspot user set [find name=$u] mac-address=$m } on-error={}; :global hsUser $user; :do { /system script run voucher-activate } on-error={}"
:local logoutHook "/system script run hs-quota-save; :delay 500ms; /system script run hs-on-logout"

:do {
  /ip hotspot user profile set [find name="default"] shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d keepalive-timeout=none idle-timeout=none on-login=$macFixScript on-logout=$logoutHook
} on-error={}
:foreach prof in=[/ip hotspot user profile find] do={
  :do {
    /ip hotspot user profile set $prof shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d keepalive-timeout=none idle-timeout=none on-login=$macFixScript on-logout=$logoutHook
  } on-error={}
}

# 16. API & SERVICES ENABLED
:do { /ip service enable [find name="api"] } on-error={}
:do { /ip service set [find name="api"] port=8728 } on-error={}
:do { /ip service enable [find name="ftp"] } on-error={}

:do {
  /ip hotspot walled-garden ip add dst-port=8728 protocol=tcp action=accept comment="Allow HotspotManager App Port 8728"
} on-error={}

:put "=========================================================="
:put "   PROVISIONING COMPLETED SUCCESSFULLY!                  "
:put ("   SSID: YadanarTun | Gateway IP: " . $gwIp . " (Direct IP Mode)")
:put ("   Capacity: 250 users on " . $netCidr)
:put ("   Admin User: admin | Admin Pass: Khant1234@")
:put "=========================================================="
