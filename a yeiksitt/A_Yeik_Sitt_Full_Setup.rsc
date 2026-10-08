# ==============================================================================
#           A YEIK SITT WIFI – FULL CLOUD & HOTSPOT SETUP SCRIPT
#     RouterOS v7.x / v6.x Compatible | Direct IP Mode: 10.10.10.1 (No DNS Needed)
#     Cloud Management: 3.84.81.152:51820 | WireGuard IP: 10.200.0.3
# ==============================================================================

:put "=========================================================="
:put "   STARTING A YEIK SITT WIFI PROVISIONING (CLOUD & HOTSPOT) "
:put "=========================================================="

:global siteName "A_Yeik_Sitt_Wifi"
:global wifiSsid "A Yeik Sitt Wifi"
:global adminPass "Ayeiksitt1234@"
:global gwIp "10.10.10.1"

# 1. ROUTER IDENTITY & ADMIN CREDENTIALS
:do {
  /system identity set name="A_Yeik_Sitt_Wifi"
} on-error={}

:do {
  /user set [find name="admin"] password=$adminPass
  :put ("Admin Password set to: " . $adminPass)
} on-error={}

# 2. INTERFACE BRIDGE
:do {
  /interface bridge add name=hotspot-bridge protocol-mode=rstp comment="Hotspot LAN Bridge"
} on-error={
  :do { /interface bridge set [find name=hotspot-bridge] protocol-mode=rstp } on-error={}
}

# 3. BRIDGE PORTS (ether2 .. etherN)
:foreach p in=[/interface ethernet find] do={
  :local pName [/interface ethernet get $p name]
  :if ($pName != "ether1") do={
    :do {
      /interface bridge port add bridge=hotspot-bridge interface=$pName
    } on-error={}
  }
}

# 4. WI-FI AP CONFIGURATION (v7 wifi vs v6 wireless)
:local wifiConfigured false

# RouterOS v7 wifi
:do {
  :local v7Cmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set $w configuration.mode=ap configuration.ssid=\"A Yeik Sitt Wifi\" configuration.hide-ssid=no datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set $w mode=ap ssid=\"A Yeik Sitt Wifi\" disabled=no }; :local wName [/interface wifi get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
  [ :parse $v7Cmd ]
  :if ([:len [/interface wifi find]] > 0) do={ :set wifiConfigured true }
} on-error={}

# RouterOS v6 wireless
:if (!$wifiConfigured) do={
  :do {
    :local legacyCmd (":foreach w in=[/interface wireless find] do={ /interface wireless set $w ssid=\"A Yeik Sitt Wifi\" hide-ssid=no mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :local wName [/interface wireless get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
    [ :parse $legacyCmd ]
    :if ([:len [/interface wireless find]] > 0) do={ :set wifiConfigured true }
  } on-error={}
}

# 5. IP ADDRESSING & DHCP SERVER (10.10.10.1/24)
:do {
  /ip address add address="10.10.10.1/24" network="10.10.10.0" interface=hotspot-bridge comment="Hotspot Gateway"
} on-error={
  :do { /ip address set [find interface=hotspot-bridge] address="10.10.10.1/24" network="10.10.10.0" } on-error={}
}

:do {
  /ip pool add name=hs-pool ranges="10.10.10.10-10.10.10.254"
} on-error={
  :do { /ip pool set [find name=hs-pool] ranges="10.10.10.10-10.10.10.254" } on-error={}
}

:do {
  /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=1d authoritative=yes disabled=no
} on-error={
  :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool lease-time=1d disabled=no } on-error={}
}

:do {
  /ip dhcp-server network add address="10.10.10.0/24" gateway="10.10.10.1" netmask=24 dns-server="10.10.10.1,8.8.8.8,1.1.1.1" comment="Hotspot Network"
} on-error={
  :do { /ip dhcp-server network set [find address="10.10.10.0/24"] gateway="10.10.10.1" netmask=24 dns-server="10.10.10.1,8.8.8.8,1.1.1.1" } on-error={}
}

:do {
  /ip dns set allow-remote-requests=yes servers="8.8.8.8,1.1.1.1" cache-size=2048KiB
} on-error={}

# 6. WAN DHCP & NAT MASQUERADE
:do {
  /ip dhcp-client add interface=ether1 disabled=no comment="WAN Internet Connection"
} on-error={
  :do { /ip dhcp-client set [find interface=ether1] disabled=no } on-error={}
}

:do {
  /ip firewall nat add chain=srcnat out-interface=ether1 action=masquerade comment="WAN NAT Masquerade"
} on-error={}

# 7. CAPTIVE PORTAL STORAGE DIRECTORY
:local hsDir "hotspot"
:if ([:len [/file find name="flash/hotspot"]] > 0) do={ :set hsDir "flash/hotspot" }

# 8. HOTSPOT SERVER PROFILE & SERVER (DIRECT IP 10.10.10.1, NO DNS NAME)
:do {
  /ip hotspot profile add name=hs-profile hotspot-address="10.10.10.1" dns-name="" html-directory=$hsDir \
    login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit="" \
    keepalive-timeout=2m
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address="10.10.10.1" dns-name="" html-directory=$hsDir \
      login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit="" \
      keepalive-timeout=2m
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile keepalive-timeout=2m disabled=no
} on-error={
  :do { /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile keepalive-timeout=2m disabled=no } on-error={}
}

# 9. INSTANT CAPTIVE PORTAL DETECTION (CNA & DNS INTERCEPTION)
:do {
  /ip firewall filter add chain=input protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}
:do {
  /ip firewall filter add chain=forward protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}

:do {
  /ip firewall nat add chain=dstnat in-interface=hotspot-bridge protocol=udp dst-port=53 action=redirect to-ports=53 place-before=0 comment="Force DNS to Router"
} on-error={}
:do {
  /ip firewall nat add chain=dstnat in-interface=hotspot-bridge protocol=tcp dst-port=53 action=redirect to-ports=53 place-before=0 comment="Force DNS to Router"
} on-error={}

:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=udp dst-port=53 action=accept comment="Allow Hotspot DNS UDP"
} on-error={}
:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=tcp dst-port=53 action=accept comment="Allow Hotspot DNS TCP"
} on-error={}
:do {
  /ip firewall filter add chain=input in-interface=hotspot-bridge protocol=tcp dst-port=80 action=accept comment="Allow Hotspot HTTP"
} on-error={}

# 10. WIREGUARD CLOUD TUNNEL (CENTRAL MANAGEMENT VPS)
:do {
  /interface wireguard add name=wg-cloud listen-port=13232 private-key="eEOn1K/yp8600PkNqJKMrxvZxxPBfmDDXUrdPKMczHk=" comment="Hotspot Cloud VPS Tunnel"
} on-error={
  /interface wireguard set [find name=wg-cloud] listen-port=13232 private-key="eEOn1K/yp8600PkNqJKMrxvZxxPBfmDDXUrdPKMczHk="
}

:do {
  /ip address add address=10.200.0.3/24 interface=wg-cloud comment="Cloud VPN Address"
} on-error={
  /ip address set [find interface=wg-cloud] address=10.200.0.3/24
}

:do {
  /interface wireguard peers add interface=wg-cloud endpoint-address="3.84.81.152" endpoint-port=51820 public-key="geEjgD9DIT4cbBHqR6vF5S9l2DDtyrZzQbon3bMQK2U=" allowed-address=10.200.0.0/24 persistent-keepalive=25s comment="VPS Server Endpoint"
} on-error={
  /interface wireguard peers set [find interface=wg-cloud] endpoint-address="3.84.81.152" endpoint-port=51820 public-key="geEjgD9DIT4cbBHqR6vF5S9l2DDtyrZzQbon3bMQK2U=" allowed-address=10.200.0.0/24 persistent-keepalive=25s
}

# Enable Services for Remote Management
:do { /ip service enable [find name="api"] } on-error={}
:do { /ip service set [find name="api"] port=8728 } on-error={}
:do { /ip service enable [find name="ssh"] } on-error={}
:do { /ip service set [find name="ssh"] port=22 } on-error={}
:do { /ip service enable [find name="ftp"] } on-error={}
:do { /ip service set [find name="ftp"] port=21 } on-error={}

# Allow remote access via WireGuard
:do {
  /ip firewall filter add chain=input in-interface=wg-cloud action=accept comment="Allow Cloud Remote Management" place-before=0
} on-error={}
:do {
  /ip firewall filter add chain=input src-address=10.200.0.0/24 action=accept comment="Allow Cloud WireGuard Subnet" place-before=0
} on-error={}

# 11. UNIVERSAL HARDENED QUOTA ENGINE (EXACT BYTE CUT-OFF & LIVE SYNC)

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

# 12. RUNNING SCHEDULER (AUTO-SAVE EVERY 20 SECONDS)
:do { /system scheduler remove [find name="hs-quota-loop"] } on-error={}
/system scheduler add name="hs-quota-loop" interval=20s on-event="hs-quota-save" start-time=startup comment="Periodic quota monitor"

# 13. USER PROFILES WITH ON-LOGIN HOOKS
:local profs {"default"; "500Ks"; "1000Ks"; "2000Ks"; "1GB"; "2GB"; "3GB"; "5GB"; "10GB"; "1Day"; "7Day"; "30Day"}

:foreach pr in=$profs do={
  :do {
    /ip hotspot user profile add name=$pr rate-limit="20M/20M" shared-users=1 \
      on-login=":global hsUser \"\$user\"; /system script run voucher-activate;" \
      on-logout=":global hsUser \"\$user\"; /system script run hs-on-logout;" \
      keepalive-timeout=2m idle-timeout=3m
  } on-error={
    :do {
      /ip hotspot user profile set [find name=$pr] rate-limit="20M/20M" shared-users=1 \
        on-login=":global hsUser \"\$user\"; /system script run voucher-activate;" \
        on-logout=":global hsUser \"\$user\"; /system script run hs-on-logout;" \
        keepalive-timeout=2m idle-timeout=3m
    } on-error={}
  }
}

:put "=========================================================="
:put "  >>> A YEIK SITT WIFI SETUP COMPLETED SUCCESSFULLY! <<<  "
:put "  Hotspot Gateway: 10.10.10.1 (Direct IP Mode)            "
:put "  WireGuard Cloud IP: 10.200.0.3 (Connected to VPS)        "
:put "  Remote Relay Port: 3.84.81.152:8732                     "
:put "  Admin Web/App: https://hotspot-admin.pages.dev          "
:put "=========================================================="
