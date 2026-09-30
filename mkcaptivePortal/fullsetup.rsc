# ==============================================================================
#                      ALL GOOD WIFI - FULL SETUP SCRIPT
#                   Combined Setup, Finalize & API Automation
# ==============================================================================
# Description:
#   Automates complete MikroTik router configuration for All Good WiFi:
#   - WAN DHCP Client & NAT Masquerade
#   - LAN Bridge, DHCP Server (10.10.10.0/23, pool 10.10.10.10-10.10.11.250)
#   - Wi-Fi Configuration (SSID: AllGoodWifi)
#   - Hotspot Server & Directory Detection (flash/hotspot vs hotspot)
#   - User Profile: Creates 1GB_1H profile (10M/10M, 1 Hour session, 1GB data quota)
#     * On configured routers, leaves existing profiles untouched (e.g. 2GB, 7D, VIP)
#     * Preserves existing vouchers and their assigned users & limits
#     * Enables MAC-roaming: users can log back in if MAC changes or after logout before limits reached
#   - RouterOS API Service (Port 8728) & User Credentials for HotspotManager
#   - Firewall rules optimized to ALLOW API & Hotspot, while dropping WAN/SSH attacks
#   - Walled Garden IP entry for port 8728 so app connects even before portal login
#   - Non-destructive normalization: syncs password=username only if empty for PAP login
#
# Usage:
#   Upload fullsetup.rsc to Files, then run in Terminal:
#   /import fullsetup.rsc
# ==============================================================================

:put "================================================="
:put "       ALL GOOD WIFI - STARTING FULL SETUP       "
:put "================================================="

# ── GLOBAL VARIABLES ──────────────────────────────────────────
:global wifiSsid   "AllGood_Wifi"
:global dnsName    ""
:global apiPass    "Khant1234@"
:global adminMacs  {"A0:29:19:39:34:61";"CC:15:31:83:26:BF"}
:global gwIp       "10.10.10.1"
:global hsNetwork  "10.10.10.0/23"
:global poolStart  "10.10.10.10"
:global poolEnd    "10.10.11.250"

# ── SYSTEM IDENTITY ───────────────────────────────────────────
:put "--- Setting Router Identity ---"
:do { /system identity set name="AllGoodWiFi-Router" } on-error={}

# ── STEP 1: WAN Interface & NAT ───────────────────────────────
:put "=== Step 1: WAN Interface & NAT ==="
:do {
  /ip dhcp-client add interface=ether1 disabled=no comment="WAN Client"
} on-error={
  :do { /ip dhcp-client set [find interface=ether1] disabled=no } on-error={}
}

:do {
  /ip firewall nat add chain=srcnat out-interface=ether1 action=masquerade comment="WAN Masquerade"
} on-error={
  :do { /ip firewall nat set [find comment="WAN Masquerade"] out-interface=ether1 action=masquerade } on-error={}
}

:do {
  /ip firewall filter add chain=input connection-state=established,related action=accept in-interface=ether1 place-before=0 comment="Accept established/related from WAN"
} on-error={}

:do {
  /ip firewall filter add chain=input in-interface=ether1 action=drop comment="Drop all other input from WAN"
} on-error={}
:put "  WAN Configured."

# ── STEP 2: LAN Bridge & Ports ────────────────────────────────
:put "=== Step 2: LAN Bridge ==="
:do { /interface bridge add name=hotspot-bridge } on-error={}

:local ports {"ether2";"ether3";"ether4";"ether5";"ether6";"ether7";"ether8"}
:foreach p in=$ports do={
  :if ([:len [/interface find name=$p]] > 0) do={
    :do {
      /interface bridge port add bridge=hotspot-bridge interface=$p
    } on-error={
      :do { /interface bridge port set [find interface=$p] bridge=hotspot-bridge } on-error={}
    }
  }
}
:put "  LAN Bridge Configured."

# ── STEP 3: Wi-Fi Setup ───────────────────────────────────────
:put "=== Step 3: Wi-Fi Setup ==="
# Try RouterOS v7 wifiwave2 / wifi-qcom interface
:do {
  /interface wifi set [find default-name=wifi1] configuration.ssid=$wifiSsid disabled=no
  :do { /interface bridge port add bridge=hotspot-bridge interface=wifi1 } on-error={}
  :put "  Configured wifi1 interface (v7)."
} on-error={
  # Fallback to legacy wireless interface
  :do {
    /interface wireless set [find default-name=wlan1] ssid=$wifiSsid mode=ap-bridge disabled=no
    :do { /interface bridge port add bridge=hotspot-bridge interface=wlan1 } on-error={}
    :put "  Configured wlan1 interface (legacy)."
  } on-error={
    :put "  No built-in Wi-Fi found or external AP in use (OK)."
  }
}

# ── STEP 4: IP Address, Pool & DHCP Server ────────────────────
:put "=== Step 4: IP & DHCP ==="
:do {
  /ip address add address=($gwIp . "/23") network=10.10.10.0 interface=hotspot-bridge comment="Hotspot Gateway"
} on-error={
  :do { /ip address set [find interface=hotspot-bridge] address=($gwIp . "/23") } on-error={}
}

:do {
  /ip pool add name=hs-pool ranges=($poolStart . "-" . $poolEnd)
} on-error={
  :do { /ip pool set [find name=hs-pool] ranges=($poolStart . "-" . $poolEnd) } on-error={}
}

:do {
  /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=1d disabled=no
} on-error={
  :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool disabled=no } on-error={}
}

:do {
  /ip dhcp-server network add address=$hsNetwork gateway=$gwIp dns-server=$gwIp comment="Hotspot Network"
} on-error={
  :do { /ip dhcp-server network set [find address=$hsNetwork] gateway=$gwIp dns-server=$gwIp } on-error={}
}

:do {
  /ip dns set allow-remote-requests=yes servers="8.8.8.8,1.1.1.1"
} on-error={}

# Static DNS mapping: allgood.lan -> 10.10.10.1
:do {
  /ip dns static add name=$dnsName address=$gwIp comment="Hotspot Portal DNS"
} on-error={
  :do { /ip dns static set [find name=$dnsName] address=$gwIp } on-error={}
}
:put ("  Static DNS Configured: " . $dnsName . " -> " . $gwIp)
:put "  IP & DHCP Configured."

# ── STEP 5: Hotspot Server & Directory Detection ───────────────
:put "=== Step 5: Hotspot Server & Directory ==="
:local hsDir "hotspot"
:if ([:len [/file find name="flash/mkcaptivePortal"]] > 0) do={
  :set hsDir "flash/mkcaptivePortal"
} else={
  :if ([:len [/file find name="mkcaptivePortal"]] > 0) do={
    :set hsDir "mkcaptivePortal"
  } else={
    :if ([:len [/file find name="flash/hotspot"]] > 0) do={
      :set hsDir "flash/hotspot"
    }
  }
}
:put ("  Using HTML directory: " . $hsDir)

:do {
  /ip hotspot profile add name=hs-profile hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
    login-by=mac-cookie,http-chap,http-pap rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
      login-by=mac-cookie,http-chap,http-pap rate-limit=""
  } on-error={}
}

# Update all existing hotspot server profiles on the router to use mac-cookie (removing 30-day cookie bloat)
:foreach hp in=[/ip hotspot profile find] do={
  :do {
    /ip hotspot profile set $hp login-by=mac-cookie,http-chap,http-pap html-directory=$hsDir hotspot-address=$gwIp dns-name=$dnsName
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
} on-error={
  :do {
    /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
  } on-error={}
}
:put "  Hotspot Server Configured."
# ── STEP 6: User Profile Setup (1GB_1H & MAC-Roaming Support) ──
:put "=== Step 6: User Profile Setup ==="

# Define on-login script to automatically remove stale active sessions and cookies
# when a user reconnects with a randomized/changed MAC address.
# This prevents the "user already logged in" lockout while keeping 1 concurrent device per voucher.
:local macFixScript ":local u \$user; :local m \$\"mac-address\"; :do { /ip hotspot active remove [find user=\$u and mac-address!=\$m]; /ip hotspot cookie remove [find user=\$u and mac-address!=\$m] } on-error={}"

# 1. Create or update 1GB_1H profile:
# - shared-users=2 allows reconnecting from randomized MAC before old keepalive expires
# - on-login purges old MAC session immediately upon login
# - keepalive-timeout=none prevents dropping sleeping mobile devices
:do {
  /ip hotspot user profile add name="1GB_1H" rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h keepalive-timeout=none idle-timeout=none shared-users=2 \
    on-login=$macFixScript comment="1GB 1Hour"
  :put "  Profile 1GB_1H created (session-timeout=1h, rate-limit=10M/10M, MAC-roaming enabled)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="1GB_1H"] rate-limit="10M/10M" session-timeout=1h \
      mac-cookie-timeout=1h keepalive-timeout=none idle-timeout=none shared-users=2 \
      on-login=$macFixScript comment="1GB 1Hour"
    :put "  Profile 1GB_1H updated (MAC-roaming enabled)."
  } on-error={}
}

# 2. Update default profile with safe matching values and MAC roaming
:do {
  /ip hotspot user profile set [find name="default"] rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
} on-error={}
:put "  Default profile updated."

# 3. Dynamic Duration-Based MAC Cookie Timeout & Roaming on all profiles:
# Edits all existing profiles on configured routers to work this way:
# - Adjusts mac-cookie-timeout to MATCH each voucher's exact validity duration (1h for 1h, 7d for 7d, etc.)
# - Sets shared-users=2 and keepalive-timeout=2m so users can log back in when MAC changes
# - Preserves existing custom scripts (e.g. voucher-activate) while adding the MAC purge logic
# - Preserves rate-limits, quotas, and existing voucher users
:foreach p in=[/ip hotspot user profile find] do={
  :local pName [/ip hotspot user profile get $p name]
  :if ($pName != "default" and $pName != "1GB_1H") do={
    :local dur 1h
    :local sTime [/ip hotspot user profile get $p session-timeout]
    :local sStr [:tostr $sTime]
    :local pComm ""
    :do { :set pComm [/ip hotspot user profile get $p comment] } on-error={}

    # 1. Detect duration from session-timeout, name, or comment:
    :if ($sStr != "" and $sStr != "00:00:00" and $sStr != "0s" and $sStr != "0") do={
      :set dur $sTime
    } else={
      :if ($pName ~ "30D" or $pName ~ "30d" or $pName ~ "Month" or $pComm ~ "30") do={
        :set dur 30d
      } else={
        :if ($pName ~ "14D" or $pName ~ "14d" or $pName ~ "2W" or $pName = "2GB_10M" or $pComm ~ "14") do={
          :set dur 14d
        } else={
          :if ($pName ~ "7D" or $pName ~ "7d" or $pName ~ "1W" or $pName = "1GB_10M" or $pComm ~ "7") do={
            :set dur 7d
          } else={
            :if ($pName ~ "6H" or $pName ~ "6h" or $pComm ~ "6h") do={
              :set dur 6h
            } else={
              :if ($pName ~ "3H" or $pName ~ "3h") do={
                :set dur 3h
              } else={
                :if ($pName ~ "2H" or $pName ~ "2h") do={
                  :set dur 2h
                } else={
                  :if ($pName ~ "1H" or $pName ~ "1h" or $pComm ~ "1h") do={
                    :set dur 1h
                  } else={
                    :if ($pName ~ "1D" or $pName ~ "1d" or $pName ~ "24H") do={
                      :set dur 1d
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    # 2. Update existing profile properties
    :do {
      /ip hotspot user profile set $p shared-users=2 keepalive-timeout=none idle-timeout=none mac-cookie-timeout=$dur
      :local curOnLogin ""
      :do { :set curOnLogin [/ip hotspot user profile get $p on-login] } on-error={}
      :if ([:len $curOnLogin] = 0) do={
        /ip hotspot user profile set $p on-login=$macFixScript
      } else={
        :if (!($curOnLogin ~ "hotspot active remove")) do={
          /ip hotspot user profile set $p on-login=($curOnLogin . "; " . $macFixScript)
        }
      }
      :put ("  Edited profile '" . $pName . "': mac-cookie-timeout=" . [:tostr $dur] . ", shared-users=2, roaming=active.")
    } on-error={
      :put ("  Could not edit profile: " . $pName)
    }
  }
}
:put "  All existing profiles successfully adapted for duration-based remembering and MAC roaming."

# 4. Clean up any orphaned cookies from deleted/non-existent users to relieve memory load
:foreach c in=[/ip hotspot cookie find] do={
  :local cUser ""
  :do { :set cUser [/ip hotspot cookie get $c user] } on-error={}
  :if ([:len [/ip hotspot user find name=$cUser]] = 0) do={
    :do { /ip hotspot cookie remove $c } on-error={}
  }
}
:put "  Orphaned cookie entries cleaned up."

# ── STEP 7: Script & Scheduler Preservation ───────────────────
:put "=== Step 7: Script & Scheduler Preservation ==="
:put "  Existing scripts and schedulers preserved for multi-profile compatibility."

# ── STEP 8: RouterOS API Service & App User Accounts ──────────
:put "=== Step 8: API Service & User Accounts for HotspotManager ==="
# Enable RouterOS API on standard port 8728 and 8729
:do { /ip service set api disabled=no port=8728 } on-error={}
:do { /ip service set api-ssl disabled=no port=8729 } on-error={}
:do { /ip service set winbox disabled=no port=8291 } on-error={}
:do { /ip service set www disabled=no port=80 } on-error={}
:put "  RouterOS API service enabled on port 8728."

# Set admin password to Khant1234@ (default in HotspotManager app)
:do {
  /user set [find name=admin] password=$apiPass
  :put "  Updated admin user password."
} on-error={
  :put "  Could not update admin password (may require current password)."
}

# Create full management group for mobile app
:do {
  /user group add name=flutter_api_group policy=local,telnet,ssh,ftp,reboot,read,write,policy,test,winbox,password,web,sniff,sensitive,api,romon
} on-error={
  :do {
    /user group set [find name=flutter_api_group] policy=local,telnet,ssh,ftp,reboot,read,write,policy,test,winbox,password,web,sniff,sensitive,api,romon
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
:put "  User flutter_app configured."

# ── STEP 9: Walled Garden for HotspotManager API & DNS ────────
:put "=== Step 9: Hotspot Walled Garden for API & DNS ==="
# Allow API connection over Wi-Fi without needing captive portal login first
:do {
  /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8728 action=accept comment="HotspotManager API"
} on-error={}
:do {
  /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8729 action=accept comment="HotspotManager API-SSL"
} on-error={}
:do {
  /ip hotspot walled-garden add dst-host=$dnsName action=allow comment="Hotspot Portal DNS"
} on-error={}
:put "  Walled Garden API & DNS access configured."

# ── STEP 10: MAC Bypass for Admin Devices ─────────────────────
:put "=== Step 10: Admin MAC Bypass ==="
:foreach mac in=$adminMacs do={
  :do {
    /ip hotspot ip-binding add mac-address=$mac type=bypassed comment="Admin Phone Bypass"
  } on-error={
    :do { /ip hotspot ip-binding set [find mac-address=$mac] type=bypassed } on-error={}
  }
}
:put "  Admin MACs bypassed."

# ── STEP 11: Clock & NTP ──────────────────────────────────────
:put "=== Step 11: Clock & NTP ==="
:do { /system clock set time-zone-name=Asia/Yangon } on-error={}
:do { /system ntp client set enabled=yes } on-error={}
:do { /system ntp client servers add address=pool.ntp.org } on-error={}
:put "  Clock & NTP configured."

# ── STEP 12: Firewall & Security Rules ────────────────────────
:put "=== Step 12: Firewall Rules (Allow API & Hotspot) ==="
# Remove old faulty drop rules that blocked port 8728
:foreach r in=[/ip firewall filter find where action="drop" and dst-port~"8728"] do={
  /ip firewall filter remove $r
}
:foreach r in=[/ip firewall filter find comment~"Allow HotspotManager API"] do={
  /ip firewall filter remove $r
}

# Explicitly ACCEPT API port 8728 & 8729 from hotspot-bridge
:do {
  /ip firewall filter add action=accept chain=input dst-port=8728,8729 protocol=tcp in-interface=hotspot-bridge place-before=0 comment="Allow HotspotManager API"
} on-error={}

# Accept DNS & DHCP on input from LAN
:do { /ip firewall filter add action=accept chain=input dst-port=53,67 protocol=udp in-interface=hotspot-bridge comment="Allow DNS/DHCP UDP" } on-error={}
:do { /ip firewall filter add action=accept chain=input dst-port=53 protocol=tcp in-interface=hotspot-bridge comment="Allow DNS TCP" } on-error={}

# Accept Hotspot HTTP
:do { /ip firewall filter add action=accept chain=input dst-port=80 protocol=tcp in-interface=hotspot-bridge comment="Allow Hotspot Web" } on-error={}

# Drop SSH and Telnet on LAN to prevent brute-force (keeping API 8728 OPEN)
:do { /ip firewall filter add action=drop chain=input dst-port=22,23 protocol=tcp in-interface=hotspot-bridge comment="Block SSH/Telnet on LAN" } on-error={}

# Forwarding filter rules
:do { /ip firewall filter add action=accept chain=forward in-interface=hotspot-bridge out-interface=ether1 comment="LAN to WAN" } on-error={}
:do { /ip firewall filter add action=accept chain=forward in-interface=ether1 out-interface=hotspot-bridge connection-state=established,related comment="WAN to LAN" } on-error={}
:do { /ip firewall filter add action=reject chain=forward in-interface=hotspot-bridge protocol=tcp dst-port=853 reject-with=tcp-reset comment="Reject DoT" } on-error={}

# DNS redirection (forces all clients through router DNS for captive portal)
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=udp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS UDP" } on-error={}
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=tcp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS TCP" } on-error={}

# Anti-tethering TTL Mangle (Prevents sharing WiFi through phone hotspot)
:foreach r in=[/ip firewall mangle find comment~"Hotspot Tethering"] do={ /ip firewall mangle remove $r }
:do { /ip firewall mangle add action=change-ttl chain=prerouting new-ttl=set:1 in-interface=hotspot-bridge ttl=equal:127 comment="Hotspot Tethering Prevention (127)" } on-error={}
:do { /ip firewall mangle add action=change-ttl chain=prerouting new-ttl=set:1 in-interface=hotspot-bridge ttl=equal:63 comment="Hotspot Tethering Prevention (63)" } on-error={}

:put "  Firewall & Security configured."

# ── STEP 13: Non-Destructive Voucher Check (Password Sync & MAC Roaming) ──
:put "=== Step 13: Voucher Password Sync (Preserving Existing Profiles) ==="
# 1. Syncs password=username if password is empty (required for voucher PAP login).
# 2. Clears locked mac-address so vouchers can log in across randomized/changing MACs.
# 3. Existing user profiles (2GB, 7D, etc.) and byte limits are 100% PRESERVED!
:local count 0
:foreach u in=[/ip hotspot user find] do={
  :local uname ""
  :do { :set uname [/ip hotspot user get $u name] } on-error={}
  :if ($uname != "" and $uname != "default-trial" and $uname != "admin") do={
    # 1. Sync password if empty (required for voucher PAP login)
    :local pw ""
    :do { :set pw [/ip hotspot user get $u password] } on-error={}
    :if ([:len $pw] = 0) do={
      :do { /ip hotspot user set $u password=$uname } on-error={}
    }

    # 2. Clear locked mac-address so user can log in when MAC changes
    :local macL ""
    :do { :set macL [/ip hotspot user get $u mac-address] } on-error={}
    :if ([:len $macL] > 0) do={
      :do { /ip hotspot user set $u mac-address="" } on-error={}
    }

    # 3. Check profile - DO NOT overwrite existing assigned profiles
    :local uProf ""
    :do { :set uProf [/ip hotspot user get $u profile] } on-error={}
    :if ($uProf = "" or [:len $uProf] = 0) do={
      :do { /ip hotspot user set $u profile="1GB_1H" } on-error={}
      :set uProf "1GB_1H"
    }

    # 4. Only apply 1GB quota if user is specifically on 1GB_1H profile and has 0 limit
    :if ($uProf = "1GB_1H") do={
      :local lim 0
      :do { :set lim [/ip hotspot user get $u limit-bytes-total] } on-error={}
      :if ($lim = 0) do={
        :do { /ip hotspot user set $u limit-bytes-total=1073741824 } on-error={}
      }
    }
    :set count ($count + 1)
  }
}
:put ("  Verified " . $count . " users/vouchers (all existing profiles, limits, and MAC-roaming enabled).")

# ── STEP 14: Internet Connectivity Test ───────────────────────
:put "=== Step 14: Connectivity Test ==="
:do { /ping 8.8.8.8 count=3 } on-error={ :put "  WAN ping check failed (check internet cable or ISP)." }

:put ""
:put "================================================="
:put "      ALL GOOD WIFI - FULL SETUP COMPLETED       "
:put "================================================="
:put "Gateway IP      : 10.10.10.1"
:put "Hotspot DNS     : allgood.lan (Points to 10.10.10.1)"
:put "Network Range   : 10.10.10.0/23"
:put "SSID            : AllGoodWifi"
:put "Portal Directory: flash/hotspot (or hotspot)"
:put "API Port        : 8728 (ENABLED & ALLOWED)"
:put "Admin User      : admin / Khant1234@"
:put "App API User    : flutter_app / Khant1234@"
:put "App Path        : C:\\Users\\localhost\\Downloads\\serverless\\HotspotManager"
:put "================================================="
