# ==============================================================================
#                     KYAW_GYI_WIFI - SETUP SCRIPT
#                Absolute Master Formula & Automation Suite
# ==============================================================================
# Description:
#   Automates complete MikroTik router configuration for Kyaw Gyi WiFi:
#   - WAN DHCP Client & NAT Masquerade
#   - LAN Bridge, DHCP Server (10.10.10.0/23, pool 10.10.10.10-10.10.11.250)
#   - Wi-Fi Configuration (SSID: Kyaw_Gyi)
#   - Captive Portal DNS: kyaw.gyi
#   - Hotspot Server & Directory Detection (flash/hotspot vs hotspot)
#   - Long-Day User Session Remembering:
#     * Cookie & MAC-cookie enabled (30 days persistence)
#     * Clients reconnecting after going offline or router restarts are remembered
#     * No re-asking for credentials until voucher date expires or quota is reached
#     * No new user profile creations performed during setup
#   - RouterOS API Service (Port 8728) & User Credentials for HotspotManager
#   - Firewall rules optimized to ALLOW API & Hotspot, while dropping WAN/SSH attacks
#   - Walled Garden IP entry for port 8728 so app connects even before portal login
#   - Non-destructive normalization: syncs password=username only if empty for PAP login
#
# Usage:
#   Upload setup.rsc to Files, then run in Terminal:
#   /import setup.rsc
# ==============================================================================

:put "================================================="
:put "      KYAW GYI WIFI - STARTING FULL SETUP        "
:put "================================================="

:local rosVer [/system resource get version]
:local boardName [/system resource get board-name]
:put ("Detected Hardware : " . $boardName)
:put ("Detected RouterOS : " . $rosVer)

# Check RouterOS Device-Mode (Switch Home Mode -> Advance Mode)
:do {
  :local dmHotspot [/system device-mode get hotspot]
  :if ($dmHotspot = false or $dmHotspot = "no") do={
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    :put "  SWITCHING ROUTER FROM HOME MODE TO ADVANCE/ENTERPRISE MODE..."
    :do { /system/device-mode/update mode=enterprise } on-error={
      :do { /system/device-mode/update mode=advanced } on-error={
        :do { /system/device-mode/update hotspot=yes scheduler=yes fetch=yes romon=yes traffic-flow=yes bandwidth-test=yes } on-error={}
      }
    }
    :put "  ACTION REQUIRED: Unplug & replug router power cord within 3 mins to confirm!"
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  }
} on-error={}

# ── GLOBAL VARIABLES ──────────────────────────────────────────
:global wifiSsid   "Kyaw_Gyi"
# Set to 'yes' to hide SSID broadcast, or 'no' to broadcast normally
:global hideSsid   no
:global dnsName    ""
:global apiPass    "Khant1234@"
# To bypass admin devices permanently without voucher, enter MACs: {"XX:XX:XX:XX:XX:XX"; "YY:YY:YY:YY:YY:YY"}
:global adminMacs  [:toarray ""]
:global gwIp       "10.10.10.1"
:global hsNetwork  "10.10.10.0/23"
:global poolStart  "10.10.10.10"
:global poolEnd    "10.10.11.250"

# ── SYSTEM IDENTITY ───────────────────────────────────────────
:put "--- Setting Router Identity ---"
:do { /system identity set name="Kyaw_Gyi-Router" } on-error={}

# ── STEP 1: WAN Interface & NAT ───────────────────────────────
:put "=== Step 1: WAN Interface & NAT ==="
:do {
  /ip dhcp-client add interface=ether1 disabled=no comment="WAN Client"
} on-error={
  :do { /ip dhcp-client set [find interface=ether1] disabled=no } on-error={}
}

# Ensure interface lists WAN and LAN exist
:do { /interface list add name=WAN } on-error={}
:do { /interface list add name=LAN } on-error={}
:do {
  /interface list member add list=WAN interface=ether1
} on-error={
  :do { /interface list member set [find interface=ether1] list=WAN } on-error={}
}

:do {
  /ip firewall nat add chain=srcnat out-interface-list=WAN action=masquerade comment="WAN Masquerade"
} on-error={
  :do { /ip firewall nat set [find comment~"WAN.*Masquerade"] out-interface-list=WAN action=masquerade } on-error={}
}
:put "  WAN Configured."

# ── STEP 2: LAN Bridge & Universal Interface Mapping ─────────
:put "=== Step 2: LAN Bridge & Port Assignment ==="
:do { /interface bridge add name=hotspot-bridge } on-error={}

# Clean up factory default DHCP server and bridge artifacts to prevent IP/DHCP conflicts
:do { /ip dhcp-server remove [find name="defconf"] } on-error={}
:do { /ip address remove [find address~"192.168.88.1"] } on-error={}
:do { /ip pool remove [find name="default-dhcp"] } on-error={}

:do {
  /interface list member add list=LAN interface=hotspot-bridge
} on-error={
  :do { /interface list member set [find interface=hotspot-bridge] list=LAN } on-error={}
}

# Automatically add all Ethernet ports except ether1 (WAN) to hotspot-bridge (preserves active forwarding)
:foreach p in=[/interface find type="ether"] do={
  :local pName [/interface get $p name]
  :if ($pName != "ether1") do={
    :if ([:len [/interface bridge port find interface=$pName and bridge=hotspot-bridge]] = 0) do={
      :do { /interface bridge port add bridge=hotspot-bridge interface=$pName } on-error={}
    }
  }
}

# Automatically add all SFP ports (e.g. sfp1 on L009) to hotspot-bridge
:foreach sfp in=[/interface find type="sfp"] do={
  :local sName [/interface get $sfp name]
  :if ([:len [/interface bridge port find interface=$sName and bridge=hotspot-bridge]] = 0) do={
    :do { /interface bridge port add bridge=hotspot-bridge interface=$sName } on-error={}
  }
}

# Disable FastPath so Hotspot packet inspection & captive portal redirection are NEVER bypassed!
:do { /interface bridge settings set allow-fast-path=no } on-error={}
:put "  LAN Bridge & Ports Configured."

# ── STEP 3: Wi-Fi Setup ───────────────────────────────────────
:put "=== Step 3: Wi-Fi Setup ==="
:local wifiConfigured false

# 1. Try RouterOS v7 wifi / wifi-qcom interface (Hotspot AP)
:do {
  :local hStr "no"
  :if ($hideSsid = yes or $hideSsid = true or $hideSsid = "yes") do={ :set hStr "yes" }
  :local wCmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set \$w configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=" . $hStr . " datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set \$w mode=ap ssid=\"" . $wifiSsid . "\" disabled=no }; :local wName [/interface wifi get \$w name]; :if ([:len [/interface bridge port find interface=\$wName and bridge=hotspot-bridge]] = 0) do={ :do { /interface bridge port add bridge=hotspot-bridge interface=\$wName } on-error={} } }")
  [ :parse $wCmd ]
  :set wifiConfigured true
  :put ("  Configured Wi-Fi interface (v7, Hotspot AP, SSID: " . $wifiSsid . ", hide-ssid=" . $hStr . ").")
} on-error={}

# 1b. Direct fallback for named wifi1
:if (!$wifiConfigured) do={
  :do {
    :local hStr "no"
    :if ($hideSsid = yes or $hideSsid = true or $hideSsid = "yes") do={ :set hStr "yes" }
    :local wCmd2 ("/interface wifi set [find name=wifi1] configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=" . $hStr . " datapath.bridge=hotspot-bridge disabled=no; :if ([:len [/interface bridge port find interface=wifi1 and bridge=hotspot-bridge]] = 0) do={ :do { /interface bridge port add bridge=hotspot-bridge interface=wifi1 } on-error={} }")
    [ :parse $wCmd2 ]
    :set wifiConfigured true
    :put ("  Configured wifi1 interface (v7, Hotspot AP, SSID: " . $wifiSsid . ", hide-ssid=" . $hStr . ").")
  } on-error={}
}

# 2. Fallback to legacy wireless interface (Hotspot AP)
:if (!$wifiConfigured) do={
  :do {
    :local hStr "no"
    :if ($hideSsid = yes or $hideSsid = true or $hideSsid = "yes") do={ :set hStr "yes" }
    :local wlCmd ("/interface wireless set [find default-name=wlan1] ssid=\"" . $wifiSsid . "\" hide-ssid=" . $hStr . " mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :if ([:len [/interface bridge port find interface=wlan1 and bridge=hotspot-bridge]] = 0) do={ :do { /interface bridge port add bridge=hotspot-bridge interface=wlan1 } on-error={} }")
    [ :parse $wlCmd ]
    :set wifiConfigured true
    :put ("  Configured wlan1 interface (legacy, Hotspot AP, SSID: " . $wifiSsid . ", hide-ssid=" . $hStr . ").")
  } on-error={}
}

:if (!$wifiConfigured) do={
  :put "  No built-in Wi-Fi found or external AP in use (OK)."
}

# ── STEP 4: IP Address, Pool & DHCP Server ────────────────────
:put "=== Step 4: IP & DHCP ==="
:if ([:len [/ip address find interface=hotspot-bridge and address=($gwIp . "/23")]] = 0) do={
  :do {
    /ip address add address=($gwIp . "/23") network=10.10.10.0 interface=hotspot-bridge comment="Hotspot Gateway"
  } on-error={
    :do { /ip address set [find interface=hotspot-bridge] address=($gwIp . "/23") } on-error={}
  }
}

:if ([:len [/ip pool find name=hs-pool]] = 0) do={
  :do {
    /ip pool add name=hs-pool ranges=($poolStart . "-" . $poolEnd)
  } on-error={
    :do { /ip pool set [find name=hs-pool] ranges=($poolStart . "-" . $poolEnd) } on-error={}
  }
}

:if ([:len [/ip dhcp-server find name=hs-dhcp]] = 0) do={
  :do {
    /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=2h authoritative=yes disabled=no
  } on-error={
    :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool lease-time=2h authoritative=yes disabled=no } on-error={}
  }
}

:do {
  /ip dhcp-server network add address=$hsNetwork gateway=$gwIp netmask=23 dns-server=$gwIp comment="Hotspot Network"
} on-error={
  :do { /ip dhcp-server network set [find address=$hsNetwork] gateway=$gwIp netmask=23 dns-server=$gwIp } on-error={}
}

:do {
  /ip dns set allow-remote-requests=yes servers="8.8.8.8,1.1.1.1" cache-size=1024KiB
} on-error={}

# Static DNS mapping (only if dnsName is provided)
:if ([:len $dnsName] > 0) do={
  :do {
    /ip dns static add name=$dnsName address=$gwIp comment="Hotspot Portal DNS"
  } on-error={
    :do { /ip dns static set [find name=$dnsName] address=$gwIp } on-error={}
  }
  :put ("  Static DNS Configured: " . $dnsName . " -> " . $gwIp)
}
:put "  IP & DHCP Configured."

# ── STEP 5: Hotspot Server & Directory Detection ───────────────
:put "=== Step 5: Hotspot Server & Directory ==="
:local hsDir "hotspot"
:if ([:len [/file find name="flash/hotspot"]] > 0) do={
  :set hsDir "flash/hotspot"
} else={
  :if ([:len [/file find name="hotspot"]] > 0) do={
    :set hsDir "hotspot"
  } else={
    :if ([:len [/file find name="flash/mkcaptivePortal"]] > 0) do={
      :set hsDir "flash/mkcaptivePortal"
    } else={
      :if ([:len [/file find name="mkcaptivePortal"]] > 0) do={
        :set hsDir "mkcaptivePortal"
      }
    }
  }
}
:put ("  Using HTML directory: " . $hsDir)

:do {
  /ip hotspot profile add name=hs-profile hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
    login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
      login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
  } on-error={}
}

# Update all existing hotspot server profiles on the router to enable 30-day MAC cookies
:foreach hp in=[/ip hotspot profile find] do={
  :do {
    /ip hotspot profile set $hp login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d html-directory=$hsDir hotspot-address=$gwIp dns-name=$dnsName
  } on-error={}
}

# Remove any conflicting/orphaned hotspot servers on other names to prevent interface conflicts
:foreach hs in=[/ip hotspot find] do={
  :local hName [/ip hotspot get $hs name]
  :if ($hName != "hs-server") do={
    :do { /ip hotspot remove $hs } on-error={}
  }
}
:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=none profile=hs-profile disabled=no
} on-error={
  :do {
    /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=none profile=hs-profile disabled=no
  } on-error={}
}
:put "  Hotspot Server Configured (30-Day Cookie & MAC-Cookie persistence enabled)."

# ── STEP 6: User Profile Optimization (MAC Roaming & 30-Day Cookie Persistence) ──
# Note: Zero new user profiles are created during setup (managed directly via HotspotManager app).
# Configures the default user profile and existing user profiles to remember clients for 30 days.
:put "=== Step 6: User Profile MAC Roaming & Persistence (No Profile Creations) ==="

# Define on-login script to automatically:
# 1. Clean stale active sessions/cookies when a user reconnects with randomized/changed MAC.
# 2. Trigger 'voucher-activate' if present to track continuous expiration countdown.
:local macFixScript ":local u \$user; :local m \$\"mac-address\"; :do { /ip hotspot active remove [find user=\$u and mac-address!=\$m]; /ip hotspot cookie remove [find user=\$u and mac-address!=\$m] } on-error={}; :global hsUser \$user; :do { /system script run voucher-activate } on-error={}"

# 1. Update the default user profile for 30-day MAC cookie persistence and zero idle kicks
:do {
  /ip hotspot user profile set [find name="default"] \
    shared-users=2 \
    add-mac-cookie=yes \
    mac-cookie-timeout=30d \
    keepalive-timeout=none \
    idle-timeout=none \
    on-login=$macFixScript
  :put "  Default user profile updated: shared-users=2, add-mac-cookie=yes, mac-cookie-timeout=30d."
} on-error={}

# 2. Adapt all other existing user profiles on the router (WITHOUT creating any new profiles)
:foreach p in=[/ip hotspot user profile find] do={
  :local pName [/ip hotspot user profile get $p name]
  :if ($pName != "default") do={
    :do {
      /ip hotspot user profile set $p \
        shared-users=2 \
        add-mac-cookie=yes \
        mac-cookie-timeout=30d \
        keepalive-timeout=none \
        idle-timeout=none
      
      :local curOnLogin ""
      :do { :set curOnLogin [/ip hotspot user profile get $p on-login] } on-error={}
      :if ([:len $curOnLogin] = 0) do={
        /ip hotspot user profile set $p on-login=$macFixScript
      } else={
        :if (!($curOnLogin ~ "voucher-activate")) do={
          /ip hotspot user profile set $p on-login=($curOnLogin . "; " . $macFixScript)
        }
      }
      :put ("  Updated existing profile '" . $pName . "': add-mac-cookie=yes, mac-cookie-timeout=30d, keepalive=none, idle=none.")
    } on-error={}
  }
}
:put "  All user profiles configured for zero-prompt reconnection and 30-day MAC persistence."

# 5. Clean up any orphaned cookies from deleted/non-existent users to relieve memory load
:foreach c in=[/ip hotspot cookie find] do={
  :local cUser ""
  :do { :set cUser [/ip hotspot cookie get $c user] } on-error={}
  :if ([:len [/ip hotspot user find name=$cUser]] = 0) do={
    :do { /ip hotspot cookie remove $c } on-error={}
  }
}
:put "  Orphaned cookie entries cleaned up."

# ── STEP 7: Automated Continuous Validity Countdown & Expiration System ──
:put "=== Step 7: Automated Continuous Validity Countdown & Expiration System ==="

# Script 1: voucher-activate
# Runs on every user login. On the user's FIRST login (insertion), it calculates
# the validity duration and creates a persistent /system scheduler task.
# This countdown NEVER stops whether the user remains online, disconnects, or pauses!
# When the validity expires, it forcibly cuts off the session and deletes the user voucher.
:do {
  /system script remove [find name="voucher-activate"]
} on-error={}

/system script add name="voucher-activate" comment="Starts continuous countdown timer upon first voucher insertion/login" source={
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };

# Only schedule on FIRST login (when voucher has not been activated yet)
:local curComm [/ip hotspot user get [find name=$u] comment];
:local isFirstLogin true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };
:do {
  :if ([:len [/system scheduler find name=$u]] > 0) do={ :set isFirstLogin false };
} on-error={};

:if ($isFirstLogin) do={
  # Default fallback
  :local vDur "1h";

  # 1. Check if user has specific limit-uptime set (e.g. 15m, 1h, 1d)
  :do {
    :local uLim [/ip hotspot user get [find name=$u] limit-uptime];
    :local uStr [:tostr $uLim];
    :if ($uStr != "" and $uStr != "00:00:00" and $uStr != "0s" and $uStr != "0") do={
      :set vDur $uLim;
    }
  } on-error={};

  # 2. Check if user comment contains validity (e.g. "V:15m", "V:1h", "V:1d", "V:7d")
  :do {
    :local uComm [/ip hotspot user get [find name=$u] comment];
    :local vIdx [:find $uComm "V:"];
    :if ($vIdx >= 0) do={
      :local rest [:pick $uComm ($vIdx + 2) [:len $uComm]];
      :local spIdx [:find $rest " "];
      :if ($spIdx > 0) do={ :set rest [:pick $rest 0 $spIdx] };
      :if ([:len $rest] > 0) do={
        :set vDur [:tostr $rest];
      }
    }
  } on-error={};

  # 3. If vDur still default or 0, check user's assigned profile
  :if ($vDur = "1h" or $vDur = "0s") do={
    :do {
      :local profName [/ip hotspot user get [find name=$u] profile];
      :if ([:len $profName] > 0) do={
        :local p [/ip hotspot user profile find name=$profName];
        :if ([:len $p] > 0) do={
          :local sTime [/ip hotspot user profile get $p session-timeout];
          :local sStr [:tostr $sTime];
          :if ($sStr != "" and $sStr != "00:00:00" and $sStr != "0s" and $sStr != "0") do={
            :set vDur $sTime;
          } else={
            :if ($profName ~ "15M" or $profName ~ "15m") do={ :set vDur "15m" };
            :if ($profName ~ "30M" or $profName ~ "30m") do={ :set vDur "30m" };
            :if ($profName ~ "45M" or $profName ~ "45m") do={ :set vDur "45m" };
            :if ($profName ~ "1H" or $profName ~ "1h" or $profName = "1GB_1H") do={ :set vDur "1h" };
            :if ($profName ~ "2H" or $profName ~ "2h" or $profName = "2Hour") do={ :set vDur "2h" };
            :if ($profName ~ "3H" or $profName ~ "3h") do={ :set vDur "3h" };
            :if ($profName ~ "6H" or $profName ~ "6h") do={ :set vDur "6h" };
            :if ($profName ~ "1D" or $profName ~ "1d" or $profName ~ "24H" or $profName = "5GB" or $profName = "2GB") do={ :set vDur "1d" };
            :if ($profName ~ "7D" or $profName ~ "7d" or $profName ~ "1W") do={ :set vDur "7d" };
            :if ($profName ~ "14D" or $profName ~ "14d" or $profName ~ "2W") do={ :set vDur "14d" };
            :if ($profName ~ "30D" or $profName ~ "30d" or $profName ~ "Month" or $profName = "30Day") do={ :set vDur "30d" };
            :if ($profName ~ "VIP" or $profName = "VIP") do={ :set vDur "" };
          }
        }
      }
    } on-error={};
  }



  :local cDate [/system clock get date];
  :local cTime [/system clock get time];

  # Stamp activation timestamp on the user's comment first so validity is always tracked
  :do {
    :local actComm [/ip hotspot user get [find name=$u] comment];
    /ip hotspot user set [find name=$u] comment=($actComm . " [ACT:" . [:tostr $cDate] . " " . [:tostr $cTime] . "]");
    :log info ("Hotspot: Voucher " . $u . " activated! Validity duration: " . [:tostr $vDur]);
  } on-error={};

  # Add self-terminating countdown scheduler for this specific voucher (if allowed by device-mode)
  :if ([:len $vDur] > 0 and $vDur != "none" and $vDur != "0s") do={
    :do {
      /system scheduler add name=$u start-date=$cDate start-time=$cTime interval=$vDur \
        on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
        comment=("Voucher continuous timer: " . [:tostr $vDur] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);
    } on-error={
      # Fallback for universal RouterOS version compatibility (v6 and v7)
      :do {
        /system scheduler add name=$u start-time=startup interval=$vDur \
          on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
          comment=("Voucher continuous timer: " . [:tostr $vDur] . " (fallback)");
      } on-error={};
    };
  };
}

}
:put "  Script 'voucher-activate' registered."

# Script 2: voucher-expire-check (removed to preserve low CPU usage)
:do {
  /system script remove [find name="voucher-expire-check"]
  /system scheduler remove [find name="hs-continuous-expire-monitor"]
} on-error={}

# ── STEP 8: RouterOS API Service & App User Accounts ──────────
:put "=== Step 8: API Service & User Accounts for HotspotManager ==="
# Enable RouterOS API on standard port 8728 and 8729 with 30 max sessions
:do {
  /ip service set [find name="api"] disabled=no port=8728 max-sessions=30
  /ip service enable [find name="api"]
} on-error={}
:do {
  /ip service set [find name="api-ssl"] disabled=no port=8729 max-sessions=30
  /ip service enable [find name="api-ssl"]
} on-error={}
:do {
  /ip service set [find name="winbox"] disabled=no port=8291
  /ip service enable [find name="winbox"]
} on-error={}
:do {
  /ip service set [find name="www"] disabled=no port=8080
  /ip service enable [find name="www"]
} on-error={}
:put "  RouterOS API service enabled on port 8728 (Max sessions: 30, WebFig moved to port 8080)."

# Set admin password to Khant1234@ (default in HotspotManager app)
:do {
  /user set [find name=admin] password=$apiPass
  :put "  Updated admin user password."
} on-error={
  :put "  Could not update admin password (may require current password)."
}

# Create full management group for mobile app with complete policies
:do {
  /user group add name=flutter_api_group policy=local,telnet,ssh,ftp,reboot,read,write,policy,test,winbox,password,web,sniff,sensitive,api,romon,rest-api
} on-error={
  :do {
    /user group set [find name=flutter_api_group] policy=local,telnet,ssh,ftp,reboot,read,write,policy,test,winbox,password,web,sniff,sensitive,api,romon,rest-api
  } on-error={}
}

# Add or update flutter_app user
:do {
  /user add name=flutter_app password=$apiPass group=flutter_api_group comment="HotspotManager App API"
} on-error={
  :do {
    /user set [find name=flutter_app] password=$apiPass group=flutter_api_group
  } on-error={}
}
:put "  User flutter_app configured with full script & API execution rights."

# Built-in Remote Script & GitHub RSC Update Runner
:do {
  /system script add name="github-rsc-fetch-run" dont-require-permissions=yes policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
    source=":global remoteRscUrl; :if ([:len \$remoteRscUrl] > 0) do={ :log info (\"HotspotManager: Fetching RSC from \" . \$remoteRscUrl); /tool fetch url=\$remoteRscUrl dst-path=\"remote_update.rsc\" mode=https check-certificate=no; :delay 2s; /import file-name=\"remote_update.rsc\"; :delay 1s; /file remove [find name=\"remote_update.rsc\"]; :log info \"HotspotManager: Remote RSC script executed successfully!\"; }" \
    comment="Remote GitHub RSC runner"
} on-error={
  :do {
    /system script set [find name="github-rsc-fetch-run"] dont-require-permissions=yes policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon \
      source=":global remoteRscUrl; :if ([:len \$remoteRscUrl] > 0) do={ :log info (\"HotspotManager: Fetching RSC from \" . \$remoteRscUrl); /tool fetch url=\$remoteRscUrl dst-path=\"remote_update.rsc\" mode=https check-certificate=no; :delay 2s; /import file-name=\"remote_update.rsc\"; :delay 1s; /file remove [find name=\"remote_update.rsc\"]; :log info \"HotspotManager: Remote RSC script executed successfully!\"; }"
  } on-error={}
}

# ── STEP 9: Walled Garden for HotspotManager API, DNS & GitHub ────────
:put "=== Step 9: Hotspot Walled Garden for API, DNS & GitHub ==="
# Remove any conflicting walled-garden IP rules
:foreach w in=[/ip hotspot walled-garden ip find] do={
  :do { /ip hotspot walled-garden ip remove $w } on-error={}
}

# 1. Allow full access to Router Gateway (10.10.10.1) so app can connect to API before login
:do {
  /ip hotspot walled-garden ip add dst-address=$gwIp action=accept comment="Hotspot Gateway Full Access"
} on-error={}

# 2. Explicitly allow API TCP ports 8728 and 8729
:do {
  /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8728 protocol=tcp action=accept comment="HotspotManager API"
} on-error={}
:do {
  /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8729 protocol=tcp action=accept comment="HotspotManager API-SSL"
} on-error={}

# 3. Allow GitHub domains for OTA app updates and remote .rsc scripts without voucher
:do { /ip hotspot walled-garden add dst-host="api.github.com" action=allow comment="GitHub API for Updates" } on-error={}
:do { /ip hotspot walled-garden add dst-host="raw.githubusercontent.com" action=allow comment="GitHub Raw for RSC Scripts" } on-error={}
:do { /ip hotspot walled-garden add dst-host="objects.githubusercontent.com" action=allow comment="GitHub Assets" } on-error={}
:do { /ip hotspot walled-garden add dst-host="*.github.com" action=allow comment="GitHub Domain Wildcard" } on-error={}
:do { /ip hotspot walled-garden add dst-host="github.com" action=allow comment="GitHub Main Site" } on-error={}

:if ([:len $dnsName] > 0) do={
  :do {
    /ip hotspot walled-garden add dst-host=$dnsName action=allow comment="Hotspot Portal DNS"
  } on-error={}
}
:put "  Walled Garden API, DNS & GitHub access configured."

# ── STEP 10: MAC Bypass for Admin Devices ─────────────────────
:put "=== Step 10: Admin MAC Bypass ==="
# Remove previous admin phone bypass entries if adminMacs is empty
:if ([:len $adminMacs] = 0) do={
  :foreach b in=[/ip hotspot ip-binding find comment~"Admin Phone Bypass"] do={
    :do { /ip hotspot ip-binding remove $b } on-error={}
  }
  :put "  No admin MAC bypass active (all devices must authenticate)."
} else={
  :foreach mac in=$adminMacs do={
    :if ([:len $mac] > 0) do={
      :do {
        /ip hotspot ip-binding add mac-address=$mac type=bypassed comment="Admin Phone Bypass"
      } on-error={
        :do { /ip hotspot ip-binding set [find mac-address=$mac] type=bypassed } on-error={}
      }
    }
  }
  :put "  Admin MACs bypassed."
}

# ── STEP 11: Clock & NTP ──────────────────────────────────────
:put "=== Step 11: Clock & NTP ==="
:do { /system clock set time-zone-name=Asia/Yangon } on-error={}
:do { /system ntp client set enabled=yes } on-error={}
:do {
  :local ntpCmd "/system ntp client servers add address=pool.ntp.org"
  [ :parse $ntpCmd ]
} on-error={
  :do {
    :local ntpV6Cmd "/system ntp client set enabled=yes server-dns-names=pool.ntp.org"
    [ :parse $ntpV6Cmd ]
  } on-error={}
}
:put "  Clock & NTP configured."

# ── STEP 12: Firewall & Security Rules ────────────────────────
:put "=== Step 12: Firewall Rules (Allow API & Hotspot) ==="

# 1. Clean all existing firewall filter rules to ensure exact, clean ordering without defconf blocks
:foreach r in=[/ip firewall filter find] do={
  :do { /ip firewall filter remove $r } on-error={}
}

# 2. Clean, deterministic INPUT chain rules:
# Unconditionally accept DHCP (UDP 67, 68) first before anything else!
:do { /ip firewall filter add action=accept chain=input dst-port=67,68 protocol=udp comment="Allow DHCP" } on-error={}
# Accept established, related, untracked
:do { /ip firewall filter add action=accept chain=input connection-state=established,related,untracked comment="Accept established,related,untracked" } on-error={}
# Drop invalid
:do { /ip firewall filter add action=drop chain=input connection-state=invalid comment="Drop invalid" } on-error={}
# Accept ICMP (Ping)
:do { /ip firewall filter add action=accept chain=input protocol=icmp comment="Accept ICMP" } on-error={}
# Explicitly ACCEPT API port 8728 & 8729 (HotspotManager App)
:do { /ip firewall filter add action=accept chain=input dst-port=8728,8729 protocol=tcp comment="Allow HotspotManager API" } on-error={}
# Accept DNS on input from hotspot-bridge
:do { /ip firewall filter add action=accept chain=input dst-port=53 protocol=udp in-interface=hotspot-bridge comment="Allow DNS UDP" } on-error={}
:do { /ip firewall filter add action=accept chain=input dst-port=53 protocol=tcp in-interface=hotspot-bridge comment="Allow DNS TCP" } on-error={}
# Accept Hotspot HTTP
:do { /ip firewall filter add action=accept chain=input dst-port=80 protocol=tcp in-interface=hotspot-bridge comment="Allow Hotspot Web" } on-error={}
# Drop SSH and Telnet on LAN to prevent brute-force (keeping API 8728 OPEN)
:do { /ip firewall filter add action=drop chain=input dst-port=22,23 protocol=tcp in-interface=hotspot-bridge comment="Block SSH/Telnet on LAN" } on-error={}
# Drop all other unsolicited input from WAN (ether1)
:do { /ip firewall filter add action=drop chain=input in-interface=ether1 comment="Drop all other input from WAN" } on-error={}

# 3. Clean, deterministic FORWARD chain rules:
# Accept established, related, untracked
:do { /ip firewall filter add action=accept chain=forward connection-state=established,related,untracked comment="Accept forward established,related,untracked" } on-error={}
# Drop invalid
:do { /ip firewall filter add action=drop chain=forward connection-state=invalid comment="Drop forward invalid" } on-error={}
# Reject DoT (forces clients to use router DNS so captive portal pops up)
:do { /ip firewall filter add action=reject chain=forward in-interface=hotspot-bridge protocol=tcp dst-port=853 reject-with=tcp-reset comment="Reject DoT" } on-error={}
# Accept ONLY authorized Hotspot clients (logged in via voucher or bypassed in ip-binding)
:do { /ip firewall filter add action=accept chain=forward hotspot=auth in-interface=hotspot-bridge comment="Accept authorized Hotspot clients" } on-error={}
# Accept Whitelisted / Bypassed Devices before dropping unauthorized clients
:do { /ip firewall filter add action=accept chain=forward src-address-list=whitelisted-devices in-interface=hotspot-bridge comment="Accept Whitelisted Devices" } on-error={}
# Drop all unauthorized Hotspot forwarding to WAN (forces captive portal login)
:do { /ip firewall filter add action=drop chain=forward in-interface=hotspot-bridge out-interface-list=WAN comment="Drop unauthorized Hotspot clients (WAN List)" } on-error={}
:do { /ip firewall filter add action=drop chain=forward in-interface=hotspot-bridge out-interface=ether1 comment="Drop unauthorized Hotspot clients (ether1)" } on-error={}
# Drop all new unsolicited connections coming from WAN
:do { /ip firewall filter add action=drop chain=forward connection-state=new connection-nat-state=!dstnat in-interface-list=WAN comment="Drop WAN unsolicited forward" } on-error={}

# 5. DNS redirection (forces all clients through router DNS for captive portal)
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=udp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS UDP" } on-error={}
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=tcp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS TCP" } on-error={}

# 6. Anti-tethering TTL Mangle (Prevents sharing WiFi through phone hotspot)
:foreach r in=[/ip firewall mangle find comment~"Hotspot Tethering"] do={ /ip firewall mangle remove $r }
:do { /ip firewall mangle add action=change-ttl chain=prerouting new-ttl=set:1 in-interface=hotspot-bridge ttl=equal:127 comment="Hotspot Tethering Prevention (127)" } on-error={}
:do { /ip firewall mangle add action=change-ttl chain=prerouting new-ttl=set:1 in-interface=hotspot-bridge ttl=equal:63 comment="Hotspot Tethering Prevention (63)" } on-error={}

:put "  Firewall & Security configured."

# ── STEP 13: Non-Destructive Voucher Check (Fast & Efficient) ──
:put "=== Step 13: Voucher Password Sync & MAC Roaming ==="
# 1. Syncs password=username if password is empty (required for voucher PAP login)
:do {
  :foreach u in=[/ip hotspot user find where password=""] do={
    :local uname [/ip hotspot user get $u name]
    :if ($uname != "admin" and $uname != "default-trial") do={
      /ip hotspot user set $u password=$uname
    }
  }
} on-error={}

# 2. Clears locked mac-address so vouchers can log in across randomized/changing MACs
:do {
  :foreach u in=[/ip hotspot user find where mac-address!=""] do={
    :local uname [/ip hotspot user get $u name]
    :if ($uname != "admin") do={
      /ip hotspot user set $u mac-address=""
    }
  }
} on-error={}


:put "  Voucher optimization and normalization completed."

# ── STEP 13b: Hotspot Accounts Auto-Import ─────────────────────
:if ([:len [/file find name="accounts.rsc"]] > 0 or [:len [/file find name="flash/accounts.rsc"]] > 0) do={
  :put "=== Step 13b: Importing Hotspot Accounts (accounts.rsc) ==="
  :do { /import file-name=accounts.rsc } on-error={
    :do { /import file-name=flash/accounts.rsc } on-error={}
  }
}

# ── STEP 14: Internet Connectivity Test ───────────────────────
:put "=== Step 14: Connectivity Test ==="
:do { /ping 8.8.8.8 count=3 } on-error={ :put "  WAN ping check failed (check internet cable or ISP)." }

# ── STEP 15: RAM & Performance Optimization ───────────────────
:put "=== Step 15: RAM & Performance Optimization ==="

# 1. Cap DNS cache memory & TTL
:do {
  /ip dns set cache-size=1024KiB cache-max-ttl=1d
} on-error={}

# 2. Reduce logging RAM buffer (1000 lines -> 150 lines saves memory)
:do {
  /system logging action set [find name=memory] memory-lines=150
} on-error={}

# 3. Connection tracking dead-state optimization (cleans stale TCP sessions in 10s instead of 2m)
:do {
  :local ctCmd "/ip firewall connection tracking set tcp-close-wait-timeout=10s tcp-time-wait-timeout=10s tcp-fin-wait-timeout=10s tcp-syn-sent-timeout=10s"
  [ :parse $ctCmd ]
} on-error={}

# 4. Optimized DHCP lease time (2 hours instead of 24h prevents stale phone lease bloat)
:do {
  /ip dhcp-server set [find name=hs-dhcp] lease-time=2h
} on-error={}

# 5. Disable unused resource-heavy background services
:do { [ :parse "/ip smb set enabled=no" ] } on-error={}
:do { [ :parse "/ip socks set enabled=no" ] } on-error={}
:do { [ :parse "/ip upnp set enabled=no" ] } on-error={}
:do { [ :parse "/ip cloud set ddns-enabled=no" ] } on-error={}
:do { [ :parse "/tool bandwidth-server set enabled=no" ] } on-error={}

# 6. Clean stale unauthorized hotspot hosts & orphaned cookies to reclaim memory immediately
:foreach h in=[/ip hotspot host find] do={
  :do {
    :local isAuth [/ip hotspot host get $h authorized]
    :local isByp [/ip hotspot host get $h bypassed]
    :if (!$isAuth and !$isByp) do={
      /ip hotspot host remove $h
    }
  } on-error={}
}
:foreach c in=[/ip hotspot cookie find] do={
  :local cUser ""
  :do { :set cUser [/ip hotspot cookie get $c user] } on-error={}
  :if ([:len [/ip hotspot user find name=$cUser]] = 0) do={
    :do { /ip hotspot cookie remove $c } on-error={}
  }
}
:put "  RAM & Performance settings tuned."

:put ""
:put "================================================="
:put "         KYAW GYI WIFI - SETUP COMPLETED         "
:put "================================================="
:put ("Router Model    : " . $boardName)
:put ("RouterOS Version: " . $rosVer)
:put "Gateway IP      : 10.10.10.1"
:if ([:len $dnsName] > 0) do={
  :put ("Hotspot DNS     : " . $dnsName . " (" . $gwIp . ")")
} else={
  :put ("Hotspot DNS     : (IP-based: " . $gwIp . ")")
}
:put "Network Range   : 10.10.10.0/23"
:put ("SSID            : " . $wifiSsid)
:put "Portal Directory: flash/hotspot (or hotspot)"
:put "Persistence     : 30-Day Cookie & MAC-Cookie Auto-Reconnect"
:put "User Profiles   : Managed via HotspotManager (Zero dummy profiles created)"
:put "API Port        : 8728 (ENABLED & ALLOWED)"
:put "Admin User      : admin / Khant1234@"
:put "App API User    : flutter_app / Khant1234@"
:put "App Client      : HotspotManager Android App"
:put "================================================="
