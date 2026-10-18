
# ==============================================================================
#           UNIVERSAL MIKROTIK HOTSPOT SETUP SCRIPT (PHONE GENERATED)
#     Compatible with RouterOS v6.x, v7.x | Direct IP Mode (No DNS Required)
#     VERSION: 2.0 - NO PER-USER SCHEDULERS (CPU-spike-free for 10k+ vouchers)
# ==============================================================================

:put "=========================================================="
:put "   STARTING UNIVERSAL CAPTIVE PORTAL PROVISIONING          "
:put "=========================================================="

:global siteName "{{SITE_NAME}}"
:global wifiSsid "{{WIFI_SSID}}"
:global dnsName ""
:global adminPass "{{ADMIN_PASS}}"
:global ipCapacity {{IP_CAPACITY}}

# 1. ADMIN CREDENTIALS
:do {
  /user set [find name="admin"] password=$adminPass
  :put ("Admin Password set to: " . $adminPass)
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

:if ($ipCapacity > 250) do={
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
  :local v7Cmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set $w configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=no datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set $w mode=ap ssid=\"" . $wifiSsid . "\" disabled=no }; :local wName [/interface wifi get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
  [ :parse $v7Cmd ]
  :if ([:len [/interface wifi find]] > 0) do={ :set wifiConfigured true }
} on-error={}

# RouterOS v6 wireless
:if (!$wifiConfigured) do={
  :do {
    :local legacyCmd (":foreach w in=[/interface wireless find] do={ /interface wireless set $w ssid=\"" . $wifiSsid . "\" hide-ssid=no mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :local wName [/interface wireless get $w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=$wName } on-error={} }")
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

# Clean up any stale static DNS names pointing to hotspot gateway
:do {
  /ip dns static remove [find address=$gwIp]
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
    login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit="" \
    keepalive-timeout=2m
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name="" html-directory=$hsDir \
      login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit="" \
      keepalive-timeout=2m
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile keepalive-timeout=2m disabled=no
} on-error={
  :do { /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile keepalive-timeout=2m disabled=no } on-error={}
}

# Enforce active session idle & keepalive timeouts across all user profiles
:do {
  /ip hotspot user profile set [find] keepalive-timeout=2m idle-timeout=3m
} on-error={}

# 10. INSTANT CAPTIVE PORTAL CNA & DNS HIJACKING (APPLE, ANDROID, WINDOWS, XIAOMI)
:do {
  /ip hotspot walled-garden remove [find comment="Fast CNA Detection"]
} on-error={}

# Reject DoT (port 853) with immediate TCP reset so Android Private DNS falls back instantly
:do {
  /ip firewall filter add chain=input protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}
:do {
  /ip firewall filter add chain=forward protocol=tcp dst-port=853 action=reject reject-with=tcp-reset place-before=0 comment="Reject DoT Instant Fallback"
} on-error={}

# Force all DNS queries to the local router even if clients have hardcoded 8.8.8.8
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

# ==============================================================================
# 11. SCRIPT 1: voucher-activate
#     LEAN VERSION: NO per-user schedulers (prevents CPU spikes with 10k+ vouchers)
#     Time enforcement is done by VPS enforcer (every 20s) + hs-continuous-expire-monitor (1m)
# ==============================================================================
:do { /system script remove [find name="voucher-activate"] } on-error={}
/system script add name="voucher-activate" policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
  comment="Airtight Voucher Activation - NO per-user schedulers (CPU-safe for 10k+ vouchers)" source={
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };

:local uList [/ip hotspot user find name=$u];
:if ([:len $uList] = 0) do={ :return "" };
:local uObj [:pick $uList 0];

:local curComm [/ip hotspot user get $uObj comment];
:local profName [/ip hotspot user get $uObj profile];
:local isDis [/ip hotspot user get $uObj disabled];

:local parseTag do={
  :local idx [:find $comm $tag];
  :if ($idx >= 0) do={
    :local rest [:pick $comm ($idx + [:len $tag]) [:len $comm]];
    :local endIdx [:find $rest "]"];
    :if ($endIdx >= 0) do={ :set rest [:pick $rest 0 $endIdx] };
    :return $rest;
  };
  :return "";
};

:local setTag do={
  :local idx [:find $comm $tag];
  :if ($idx >= 0) do={
    :local endIdx [:find $comm "]" $idx];
    :local pfx [:pick $comm 0 $idx];
    :local sfx "";
    :if ($endIdx >= 0) do={ :set sfx [:pick $comm ($endIdx + 1) [:len $comm]] };
    :return ($pfx . $tag . $val . "]" . $sfx);
  } else={
    :return ($comm . " " . $tag . $val . "]");
  };
};

# STRICT CHECK: If user is disabled or EXPIRED/EXHAUSTED, REJECT IMMEDIATELY
:if ($isDis = true or [:find $curComm "[EXPIRED:"] >= 0 or [:find $curComm "[EXHAUSTED:"] >= 0) do={
  :log warning ("Hotspot: Rejected login for expired/exhausted user " . $u . ". Kicking immediately.");
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 limit-uptime=1s;
  :return "";
};

# RESOLVE ORIGINAL QUOTA LIMIT from comment tag or profile name
:local origLim 0;
:local lStr [$parseTag comm=$curComm tag="[ORIG-LIMIT:"];
:if ([:len $lStr] > 0) do={ :set origLim [:tonum $lStr] };
:if ($origLim = 0) do={
  :local cUpper ($profName . " " . $curComm);
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
  :if ($cUpper ~ "1.5GB" or $cUpper ~ "1.7GB" or $cUpper ~ "1700MB" or $profName = "1000Ks") do={ :set origLim 1782579200 };
  :if ($cUpper ~ "1GB" or $cUpper ~ "1G") do={ :set origLim 1073741824 };
  :if ($cUpper ~ "750MB" or $profName = "500Ks") do={ :set origLim 786432000 };
  :if ($cUpper ~ "500MB" or $cUpper ~ "500M") do={ :set origLim 524288000 };
  :if ($cUpper ~ "250MB" or $cUpper ~ "250M") do={ :set origLim 262144000 };
  :if ($cUpper ~ "100MB" or $cUpper ~ "100M") do={ :set origLim 104857600 };
  :if ($cUpper ~ "50MB" or $cUpper ~ "50M" or $cUpper ~ "test") do={ :set origLim 52428800 };
};

# QUOTA EXHAUSTION CHECK using stored USED bytes
:local usedBytes 0;
:local udStr [$parseTag comm=$curComm tag="[USED:"];
:if ([:len $udStr] > 0) do={ :set usedBytes [:tonum $udStr] };
# Also check hardware counters
:local hwUsed ([/ip hotspot user get $uObj bytes-in] + [/ip hotspot user get $uObj bytes-out]);
:if ($hwUsed > $usedBytes) do={ :set usedBytes $hwUsed };

:local remBytes ($origLim - $usedBytes);
:if ($origLim > 0 and $remBytes <= 0) do={
  :log warning ("Hotspot: User " . $u . " quota already exhausted (" . [:tostr $usedBytes] . " >= " . [:tostr $origLim] . ")! Kicking.");
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  :set curComm [$setTag comm=$curComm tag="[EXHAUSTED:" val="DATA]"];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 comment=$curComm;
  :return "";
};

# Set hardware byte counter so RouterOS enforces cutoff automatically too
:set curComm [$setTag comm=$curComm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
:set curComm [$setTag comm=$curComm tag="[BASE:" val=[:tostr $usedBytes]];
:set curComm [$setTag comm=$curComm tag="[USED:" val=[:tostr $usedBytes]];
:if ($origLim > 0) do={
  /ip hotspot user set $uObj limit-bytes-total=$remBytes;
};
/ip hotspot user reset-counters $uObj;

# Record MAC address in comment (for tracking, NOT for mac-locking the user object)
:local m "";
:foreach a in=[/ip hotspot active find user=$u] do={
  :set m [/ip hotspot active get $a mac-address];
};
:if ([:len $m] > 0) do={
  :set curComm [$setTag comm=$curComm tag="[MAC:" val=$m];
};

# Record activation time on FIRST login only
# NOTE: Time expiry is enforced by VPS enforcer (every 20s) + hs-continuous-expire-monitor (every 1m)
# NO per-user schedulers are created - this is the key to 10k+ voucher scalability
:local isFirstLogin true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };

:local cDate [/system clock get date];
:local cTime [/system clock get time];

:if ($isFirstLogin) do={
  :set curComm [$setTag comm=$curComm tag="[ACT:" val=([:tostr $cDate] . " " . [:tostr $cTime])];
  /ip hotspot user set $uObj comment=$curComm;
  :log info ("Hotspot: Voucher " . $u . " first activated at " . [:tostr $cDate] . " " . [:tostr $cTime]);
} else={
  /ip hotspot user set $uObj comment=$curComm;
};
}

# ==============================================================================
# 12. SCRIPT 2: hs-quota-save (30-SECOND SYNC - NOT 2s, to reduce flash wear)
#     Only writes to flash when delta >= 10MB to minimize CPU/write churn
# ==============================================================================
:do { /system script remove [find name="hs-quota-save"] } on-error={}
/system script add name="hs-quota-save" policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
  comment="Airtight Realtime Quota Saver & Hardware Cutoff (10MB delta threshold)" source={
  :foreach a in=[/ip hotspot active find] do={
    :local uName [/ip hotspot active get $a user];
    :if ($uName != "admin" and $uName != "default-trial") do={
      :local uList [/ip hotspot user find name=$uName];
      :if ([:len $uList] > 0) do={
        :local u [:pick $uList 0];
        :local sessionBytes ([/ip hotspot active get $a bytes-in] + [/ip hotspot active get $a bytes-out]);
        :local comm [/ip hotspot user get $u comment];

        :local baseBytes 0;
        :local bIdx [:find $comm "[BASE:"];
        :if ($bIdx >= 0) do={
          :local bRest [:pick $comm ($bIdx + 6) [:len $comm]];
          :local bEnd [:find $bRest "]"];
          :if ($bEnd >= 0) do={ :set bRest [:pick $bRest 0 $bEnd] };
          :set baseBytes [:tonum $bRest];
        };

        :local curStoredUsed 0;
        :local uIdx [:find $comm "[USED:"];
        :if ($uIdx >= 0) do={
          :local uRest [:pick $comm ($uIdx + 6) [:len $comm]];
          :local uEnd [:find $uRest "]"];
          :if ($uEnd >= 0) do={ :set uRest [:pick $uRest 0 $uEnd] };
          :set curStoredUsed [:tonum $uRest];
        };

        # Promote base if session counter reset (reboot)
        :if ($baseBytes = 0 and $curStoredUsed > $sessionBytes) do={
          :set baseBytes $curStoredUsed;
          :if ($bIdx >= 0) do={
            :local bEnd [:find $comm "]" $bIdx];
            :local pfx [:pick $comm 0 $bIdx];
            :local sfx "";
            :if ($bEnd >= 0) do={ :set sfx [:pick $comm ($bEnd + 1) [:len $comm]] };
            :set comm ($pfx . "[BASE:" . [:tostr $baseBytes] . "]" . $sfx);
          } else={
            :set comm ($comm . " [BASE:" . [:tostr $baseBytes] . "]");
          };
        };

        :local totalUsed ($baseBytes + $sessionBytes);
        :local origLim 0;
        :local lIdx [:find $comm "[ORIG-LIMIT:"];
        :if ($lIdx >= 0) do={
          :local lRest [:pick $comm ($lIdx + 12) [:len $comm]];
          :local lEnd [:find $lRest "]"];
          :if ($lEnd >= 0) do={ :set lRest [:pick $lRest 0 $lEnd] };
          :set origLim [:tonum $lRest];
        };
        :if ($origLim = 0) do={
          :local profName [/ip hotspot user get $u profile];
          :local cUpper ($profName . " " . $comm);
          :if ($cUpper ~ "100GB") do={ :set origLim 107374182400 };
          :if ($cUpper ~ "50GB") do={ :set origLim 53687091200 };
          :if ($cUpper ~ "30GB" or $cUpper ~ "30Day") do={ :set origLim 32212254720 };
          :if ($cUpper ~ "20GB") do={ :set origLim 21474836480 };
          :if ($cUpper ~ "15GB") do={ :set origLim 16106127360 };
          :if ($cUpper ~ "10GB") do={ :set origLim 10737418240 };
          :if ($cUpper ~ "7GB") do={ :set origLim 7516192768 };
          :if ($cUpper ~ "5GB") do={ :set origLim 5368709120 };
          :if ($cUpper ~ "4GB") do={ :set origLim 4294967296 };
          :if ($cUpper ~ "3GB") do={ :set origLim 3221225472 };
          :if ($cUpper ~ "2GB") do={ :set origLim 2147483648 };
          :if ($cUpper ~ "1.5GB" or $cUpper ~ "1000Ks") do={ :set origLim 1782579200 };
          :if ($cUpper ~ "1GB") do={ :set origLim 1073741824 };
          :if ($cUpper ~ "750MB" or $cUpper ~ "500Ks") do={ :set origLim 786432000 };
          :if ($cUpper ~ "500MB") do={ :set origLim 524288000 };
          :if ($cUpper ~ "250MB") do={ :set origLim 262144000 };
          :if ($cUpper ~ "100MB") do={ :set origLim 104857600 };
          :if ($cUpper ~ "50MB" or $cUpper ~ "test") do={ :set origLim 52428800 };
          :if ($origLim > 0) do={
            :set comm ($comm . " [ORIG-LIMIT:" . [:tostr $origLim] . "]");
          };
        };

        # Update [USED:...] in comment - Only write if delta >= 10MB to reduce flash churn
        :local delta ($totalUsed - $curStoredUsed);
        :if ($delta < 0) do={ :set delta (-$delta) };
        :if ($origLim > 0 and $totalUsed >= $origLim) do={
          # EXACT DATA CUTOFF TRIGGER
          :log warning ("Hotspot: Quota exceeded for " . $uName . " (" . [:tostr $totalUsed] . " >= " . [:tostr $origLim] . ") - KICKING AND DISABLING");
          :set uIdx [:find $comm "[USED:"];
          :if ($uIdx >= 0) do={
            :local endIdx [:find $comm "]" $uIdx];
            :local prefix [:pick $comm 0 $uIdx];
            :local suffix "";
            :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
            :set comm ($prefix . "[USED:" . [:tostr $totalUsed] . "]" . $suffix);
          } else={
            :set comm ($comm . " [USED:" . [:tostr $totalUsed] . "]");
          };
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$uName];
          /ip hotspot user set $u disabled=yes limit-bytes-total=1 comment=($comm . " [EXHAUSTED:DATA]");
        } else={
          :if ($delta >= 10485760) do={
            :set uIdx [:find $comm "[USED:"];
            :if ($uIdx >= 0) do={
              :local endIdx [:find $comm "]" $uIdx];
              :local prefix [:pick $comm 0 $uIdx];
              :local suffix "";
              :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
              :set comm ($prefix . "[USED:" . [:tostr $totalUsed] . "]" . $suffix);
            } else={
              :set comm ($comm . " [USED:" . [:tostr $totalUsed] . "]");
            };
            /ip hotspot user set $u comment=$comm;
          };
        };
      };
    };
  };
}

# 12b. SCHEDULER: hs-quota-saver at 30s (NOT 2s - reduces CPU/flash wear)
:do { /system scheduler remove [find name="hs-quota-saver"] } on-error={}
/system scheduler add name="hs-quota-saver" interval=30s start-time=startup \
  on-event="/system script run hs-quota-save" comment="Auto-persist user bytes every 30s (CPU-safe)"

# ==============================================================================
# 13. SCRIPT 3: hs-on-logout (BASE SYNC ON DISCONNECT)
# ==============================================================================
:do { /system script remove [find name="hs-on-logout"] } on-error={}
/system script add name="hs-on-logout" policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
  comment="Save quota and sync base state on user logout" source={
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0) do={ :set u $user };
:if ([:len $u] > 0 and $u != "admin" and $u != "default-trial") do={
  :local uList [/ip hotspot user find name=$u];
  :if ([:len $uList] > 0) do={
    :local uObj [:pick $uList 0];
    :local comm [/ip hotspot user get $uObj comment];
    :local hwIn [/ip hotspot user get $uObj bytes-in];
    :local hwOut [/ip hotspot user get $uObj bytes-out];
    :local hwTotal ($hwIn + $hwOut);

    :local base 0;
    :local bIdx [:find $comm "[BASE:"];
    :if ($bIdx >= 0) do={
      :local bRest [:pick $comm ($bIdx + 6) [:len $comm]];
      :local bEnd [:find $bRest "]"];
      :if ($bEnd >= 0) do={ :set bRest [:pick $bRest 0 $bEnd] };
      :set base [:tonum $bRest];
    };

    :local total ($base + $hwTotal);
    :local uIdx [:find $comm "[USED:"];
    :if ($uIdx >= 0) do={
      :local endIdx [:find $comm "]" $uIdx];
      :local prefix [:pick $comm 0 $uIdx];
      :local suffix "";
      :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set comm ($prefix . "[USED:" . [:tostr $total] . "]" . $suffix);
    } else={
      :set comm ($comm . " [USED:" . [:tostr $total] . "]");
    };
    :set bIdx [:find $comm "[BASE:"];
    :if ($bIdx >= 0) do={
      :local endIdx [:find $comm "]" $bIdx];
      :local prefix [:pick $comm 0 $bIdx];
      :local suffix "";
      :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set comm ($prefix . "[BASE:" . [:tostr $total] . "]" . $suffix);
    } else={
      :set comm ($comm . " [BASE:" . [:tostr $total] . "]");
    };
    /ip hotspot user set $uObj comment=$comm;
  };
};
}

# ==============================================================================
# 14. SCRIPT 4: voucher-expire-check (CONTINUOUS GARBAGE COLLECTOR - 1 min)
#     Catches time-expired and already-disabled users trying to stay online
# ==============================================================================
:do { /system script remove [find name="voucher-expire-check"] } on-error={}
/system script add name="voucher-expire-check" policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
  comment="Garbage Collector & Disabled User Cutter (every 1m)" source={
:foreach a in=[/ip hotspot active find] do={
  :local aUser [/ip hotspot active get $a user];
  :if ($aUser != "admin" and $aUser != "default-trial") do={
    :local uList [/ip hotspot user find name=$aUser];
    :if ([:len $uList] = 0) do={
      :do {
        /ip hotspot active remove $a;
        /ip hotspot cookie remove [find user=$aUser];
      } on-error={};
    } else={
      :local uObj [:pick $uList 0];
      :local isDis [/ip hotspot user get $uObj disabled];
      :local comm [/ip hotspot user get $uObj comment];
      :if ($isDis = true or [:find $comm "[EXHAUSTED:"] >= 0 or [:find $comm "[EXPIRED:"] >= 0) do={
        :log warning ("Hotspot: Active session found for disabled/expired user " . $aUser . " - Kicking immediately");
        :do {
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$aUser];
        } on-error={};
      };
    };
  };
};
}

# 14b. SCHEDULER: hs-continuous-expire-monitor (every 1m)
:do { /system scheduler remove [find name="hs-continuous-expire-monitor"] } on-error={}
/system scheduler add name="hs-continuous-expire-monitor" interval=1m start-time=startup \
  on-event="/system script run voucher-expire-check" comment="Hotspot continuous expiration monitor (every 1m)"

# ==============================================================================
# 15. CLEAN UP: Remove ALL per-user schedulers (if any old ones exist)
#     Per-user schedulers are the #1 cause of CPU spikes with large voucher lists
# ==============================================================================
:local sysSchedulerNames {"hs-quota-saver";"hs-continuous-expire-monitor";"cloud-heartbeat";"cloud_heartbeat";"hs-quota-restorer"};
:foreach sc in=[/system scheduler find] do={
  :local scName [/system scheduler get $sc name];
  :local isSystem false;
  :foreach sn in=$sysSchedulerNames do={
    :if ($scName = $sn) do={ :set isSystem true };
  };
  :if (!$isSystem) do={
    :do { /system scheduler remove $sc } on-error={};
    :log info ("Hotspot: Removed per-user scheduler: " . $scName);
  };
}
:put "All per-user schedulers cleaned up."

# ==============================================================================
# 16. USER PROFILE: on-login and on-logout hooks (NO MAC-LOCKING)
#     Uses lean voucher-activate (no per-user schedulers)
# ==============================================================================
:local onLoginHook ":global hsUser \$user; :do { /system script run voucher-activate } on-error={}"
:local onLogoutHook ":do { /system script run hs-on-logout } on-error={}"

:do {
  /ip hotspot user profile set [find name="default"] \
    shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d \
    keepalive-timeout=2m idle-timeout=3m \
    on-login=$onLoginHook on-logout=$onLogoutHook
} on-error={}

:foreach prof in=[/ip hotspot user profile find] do={
  :do {
    /ip hotspot user profile set $prof \
      shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d \
      keepalive-timeout=2m idle-timeout=3m \
      on-login=$onLoginHook on-logout=$onLogoutHook
  } on-error={}
}

# ==============================================================================
# 17. API & SERVICES ENABLED FOR REMOTE ADMIN ACCESS
# ==============================================================================
:do { /ip service enable [find name="api"] } on-error={}
:do { /ip service set [find name="api"] port=8728 } on-error={}
:do { /ip service enable [find name="ssh"] } on-error={}
:do { /ip service set [find name="ssh"] port=22 } on-error={}
:do { /ip service enable [find name="ftp"] } on-error={}

# Allow Cloud WireGuard interface & subnet to access router in Firewall Input filter
:do {
  /ip firewall filter add chain=input in-interface=wg-cloud action=accept comment="Allow Cloud Remote Management" place-before=0
} on-error={}
:do {
  /ip firewall filter add chain=input src-address=10.200.0.0/24 action=accept comment="Allow Cloud WireGuard Subnet" place-before=0
} on-error={}

:do {
  /ip hotspot walled-garden ip add dst-port=8728 protocol=tcp action=accept comment="Allow HotspotManager App Port 8728"
} on-error={}

:put "=========================================================="
:put "   PROVISIONING COMPLETED SUCCESSFULLY!                  "
:put ("   SSID: " . $wifiSsid . " | Gateway IP: " . $gwIp . " (Direct IP Mode)")
:put ("   Capacity: " . [:tostr $ipCapacity] . " users on " . $netCidr)
:put ("   Admin User: admin | Admin Pass: " . $adminPass)
:put ("   CPU-SAFE: No per-user schedulers. Supports 10000+ vouchers.")
:put "=========================================================="