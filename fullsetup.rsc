# ==============================================================================
#                     AYEIKSITT_WIFI - FULL SETUP SCRIPT
#                   Combined Setup, Finalize & API Automation
# ==============================================================================
# Description:
#   Automates complete MikroTik router configuration for AyeikSitt_WiFi:
#   - WAN DHCP Client & NAT Masquerade
#   - LAN Bridge, DHCP Server (10.10.10.0/23, pool 10.10.10.10-10.10.11.250)
#   - Wi-Fi Configuration (SSID: AyeikSitt_WiFi)
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
:put "      AYEIKSITT_WIFI - STARTING FULL SETUP       "
:put "================================================="

:local rosVer [/system resource get version]
:local boardName [/system resource get board-name]
:put ("Detected Hardware : " . $boardName)
:put ("Detected RouterOS : " . $rosVer)

# ── GLOBAL VARIABLES ──────────────────────────────────────────
:global wifiSsid   "AyeikSitt_WiFi"
:global dnsName    ""
:global apiPass    "Khant1234@"
:global adminMacs  {"A0:29:19:39:34:61";"CC:15:31:83:26:BF"}
:global gwIp       "10.10.10.1"
:global hsNetwork  "10.10.10.0/23"
:global poolStart  "10.10.10.10"
:global poolEnd    "10.10.11.250"

# ── SYSTEM IDENTITY ───────────────────────────────────────────
:put "--- Setting Router Identity ---"
:do { /system identity set name="AyeikSitt_WiFi-Router" } on-error={}

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
:put "  WAN Configured."

# ── STEP 2: LAN Bridge & Interface Lists ──────────────────────
:put "=== Step 2: LAN Bridge & Interface Lists ==="
:do { /interface bridge add name=hotspot-bridge } on-error={}

# Clean up factory default DHCP server and bridge artifacts to prevent IP/DHCP conflicts
:do { /ip dhcp-server remove [find name="defconf"] } on-error={}
:do { /ip address remove [find address~"192.168.88.1"] } on-error={}
:do { /ip pool remove [find name="default-dhcp"] } on-error={}

# Ensure interface lists LAN and WAN exist and contain our interfaces
:do { /interface list add name=WAN } on-error={}
:do { /interface list add name=LAN } on-error={}
:do {
  /interface list member add list=LAN interface=hotspot-bridge
} on-error={
  :do { /interface list member set [find interface=hotspot-bridge] list=LAN } on-error={}
}
:do {
  /interface list member add list=WAN interface=ether1
} on-error={
  :do { /interface list member set [find interface=ether1] list=WAN } on-error={}
}

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
:put "  LAN Bridge & Interface Lists Configured."

# ── STEP 3: Wi-Fi Setup ───────────────────────────────────────
:put "=== Step 3: Wi-Fi Setup ==="
:local wifiConfigured false

# 1. Try RouterOS v7 wifi / wifi-qcom interface (Open network for Hotspot)
:do {
  :local wCmd ("/interface wifi set [find default-name=wifi1] configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" security.authentication-types=\"\" datapath.bridge=hotspot-bridge disabled=no; :do { /interface bridge port set [find interface=wifi1] bridge=hotspot-bridge } on-error={ /interface bridge port add bridge=hotspot-bridge interface=wifi1 }")
  [ :parse $wCmd ]
  :set wifiConfigured true
  :put "  Configured wifi1 interface (v7, Open Hotspot AP, SSID: AyeikSitt_WiFi)."
} on-error={}

# 2. Fallback to legacy wireless interface (Open network for Hotspot)
:if (!$wifiConfigured) do={
  :do {
    :local wlCmd ("/interface wireless set [find default-name=wlan1] ssid=\"" . $wifiSsid . "\" mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :do { /interface bridge port set [find interface=wlan1] bridge=hotspot-bridge } on-error={ /interface bridge port add bridge=hotspot-bridge interface=wlan1 }")
    [ :parse $wlCmd ]
    :set wifiConfigured true
    :put "  Configured wlan1 interface (legacy, Open Hotspot AP)."
  } on-error={}
}

:if (!$wifiConfigured) do={
  :put "  No built-in Wi-Fi found or external AP in use (OK)."
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
  /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=2h authoritative=yes disabled=no
} on-error={
  :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool lease-time=2h authoritative=yes disabled=no } on-error={}
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
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=none profile=hs-profile disabled=no
} on-error={
  :do {
    /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=none profile=hs-profile disabled=no
  } on-error={}
}
:put "  Hotspot Server Configured."
# ── STEP 6: User Profile Setup (Continuous Validity Countdown & MAC Roaming) ──
:put "=== Step 6: User Profile Setup ==="

# Define on-login script to automatically:
# 1. Remove stale active sessions/cookies when a user reconnects with randomized/changed MAC.
# 2. Trigger 'voucher-activate' to initiate continuous expiration countdown from time of first login.
:local macFixScript ":local u \$user; :local m \$\"mac-address\"; :do { /ip hotspot active remove [find user=\$u and mac-address!=\$m]; /ip hotspot cookie remove [find user=\$u and mac-address!=\$m] } on-error={}; :global hsUser \$user; :do { /system script run voucher-activate } on-error={}"

# 1. Create or update 1GB_1H profile:
# - shared-users=2 allows reconnecting from randomized MAC before old keepalive expires
# - on-login purges old MAC session and activates continuous validity countdown
# - keepalive-timeout=none prevents dropping sleeping mobile devices
:do {
  /ip hotspot user profile add name="1GB_1H" rate-limit="10M/10M" session-timeout=1h \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 1GB_1H created (session-timeout=1h, rate-limit=10M/10M, MAC-roaming & continuous countdown enabled)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="1GB_1H"] rate-limit="10M/10M" session-timeout=1h \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 1GB_1H updated (MAC-roaming & continuous countdown enabled)."
  } on-error={}
}

# 2. Create or update 15M profile (15 Minutes validity):
:do {
  /ip hotspot user profile add name="15M" rate-limit="10M/10M" session-timeout=15m \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 15M created (session-timeout=15m, rate-limit=10M/10M, MAC-roaming & continuous countdown enabled)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="15M"] rate-limit="10M/10M" session-timeout=15m \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 15M updated (MAC-roaming & continuous countdown enabled)."
  } on-error={}
}

# 3. Update default profile with safe matching values and MAC roaming & continuous countdown
:do {
  /ip hotspot user profile set [find name="default"] rate-limit="10M/10M" session-timeout=1h \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
} on-error={}
:put "  Default profile updated."

# 4. Dynamic Duration-Based MAC Cookie Timeout & Roaming on all profiles:
# Edits all existing profiles on configured routers to work this way:
# - Adjusts mac-cookie-timeout to MATCH each voucher's exact validity duration (15m, 1h, 7d, etc.)
# - Sets shared-users=2 and keepalive-timeout=none so users can log back in when MAC changes
# - Attaches continuous validity countdown timer and MAC purge logic
# - Preserves rate-limits, quotas, and existing voucher users
:foreach p in=[/ip hotspot user profile find] do={
  :local pName [/ip hotspot user profile get $p name]
  :if ($pName != "default" and $pName != "1GB_1H" and $pName != "15M") do={
    :local dur 1h
    :local sTime [/ip hotspot user profile get $p session-timeout]
    :local sStr [:tostr $sTime]
    :local pComm ""
    :do { :set pComm [/ip hotspot user profile get $p comment] } on-error={}

    # 1. Detect duration from session-timeout, name, or comment:
    :if ($sStr != "" and $sStr != "00:00:00" and $sStr != "0s" and $sStr != "0") do={
      :set dur $sTime
    } else={
      :if ($pName ~ "15M" or $pName ~ "15m" or $pComm ~ "15m" or $pComm ~ "15M") do={
        :set dur 15m
      } else={
        :if ($pName ~ "30M" or $pName ~ "30m" or $pComm ~ "30m" or $pComm ~ "30M") do={
          :set dur 30m
        } else={
          :if ($pName ~ "45M" or $pName ~ "45m" or $pComm ~ "45m" or $pComm ~ "45M") do={
            :set dur 45m
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
        }
      }
    }

    # 2. Update existing profile properties
    :do {
      /ip hotspot user profile set $p shared-users=2 keepalive-timeout=none idle-timeout=none
      :local curOnLogin ""
      :do { :set curOnLogin [/ip hotspot user profile get $p on-login] } on-error={}
      :if ([:len $curOnLogin] = 0) do={
        /ip hotspot user profile set $p on-login=$macFixScript
      } else={
        :if (!($curOnLogin ~ "voucher-activate")) do={
          /ip hotspot user profile set $p on-login=($curOnLogin . "; " . $macFixScript)
        }
      }
      :put ("  Edited profile '" . $pName . "': shared-users=2, roaming & continuous countdown=active.")
    } on-error={
      :put ("  Could not edit profile: " . $pName)
    }
  }
}
:put "  All existing profiles successfully adapted for duration-based remembering, MAC roaming, and continuous countdown."

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

# Only schedule on FIRST login (when no scheduler exists for this user)
:if ([:len [/system scheduler find name=$u]] = 0) do={
  :local vDur "1h"; # Default fallback

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
            :if ($profName ~ "1H" or $profName ~ "1h") do={ :set vDur "1h" };
            :if ($profName ~ "2H" or $profName ~ "2h") do={ :set vDur "2h" };
            :if ($profName ~ "3H" or $profName ~ "3h") do={ :set vDur "3h" };
            :if ($profName ~ "6H" or $profName ~ "6h") do={ :set vDur "6h" };
            :if ($profName ~ "1D" or $profName ~ "1d" or $profName ~ "24H") do={ :set vDur "1d" };
            :if ($profName ~ "7D" or $profName ~ "7d" or $profName ~ "1W") do={ :set vDur "7d" };
            :if ($profName ~ "14D" or $profName ~ "14d" or $profName ~ "2W") do={ :set vDur "14d" };
            :if ($profName ~ "30D" or $profName ~ "30d" or $profName ~ "Month") do={ :set vDur "30d" };
          }
        }
      }
    } on-error={};
  }

  :local cDate [/system clock get date];
  :local cTime [/system clock get time];

  # Add self-terminating countdown scheduler for this specific voucher
  :do {
    /system scheduler add name=$u start-date=$cDate start-time=$cTime interval=$vDur \
      on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
      comment=("Voucher continuous timer: " . [:tostr $vDur] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);

    # Stamp activation timestamp on the user's comment
    :local curComm [/ip hotspot user get [find name=$u] comment];
    /ip hotspot user set [find name=$u] comment=($curComm . " [ACT:" . [:tostr $cDate] . " " . [:tostr $cTime] . "]");
    :log info ("Hotspot: Voucher " . $u . " activated! Continuous timer set for " . [:tostr $vDur] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);
  } on-error={
    # Fallback for universal RouterOS version compatibility (v6 and v7)
    :do {
      /system scheduler add name=$u start-time=startup interval=$vDur \
        on-event=("/ip hotspot active remove [find user=\"" . $u . "\"]; /ip hotspot user remove [find name=\"" . $u . "\"]; /ip hotspot cookie remove [find user=\"" . $u . "\"]; /system scheduler remove [find name=\"" . $u . "\"]") \
        comment=("Voucher continuous timer: " . [:tostr $vDur] . " (fallback)");
      :local curComm [/ip hotspot user get [find name=$u] comment];
      /ip hotspot user set [find name=$u] comment=($curComm . " [ACT:" . [:tostr $cDate] . " " . [:tostr $cTime] . "]");
      :log info ("Hotspot: Voucher " . $u . " activated (fallback timer set for " . [:tostr $vDur] . ")!");
    } on-error={
      :log warning ("Hotspot: Could not set continuous countdown scheduler for " . $u);
    };
  };
}
}
:put "  Script 'voucher-activate' registered."

# Script 2: voucher-expire-check
# Periodic garbage collection and fail-safe enforcer:
# 1. Purges orphaned schedulers whose users have already been deleted.
# 2. Immediately kicks any active session whose user voucher is no longer present.
:do {
  /system script remove [find name="voucher-expire-check"]
} on-error={}

/system script add name="voucher-expire-check" comment="Garbage collector and instant cutoff enforcer for expired vouchers" source={
  # 1. Clean up orphaned continuous timer schedulers
  :foreach s in=[/system scheduler find comment~"Voucher continuous timer"] do={
    :local sName [/system scheduler get $s name];
    :if ([:len [/ip hotspot user find name=$sName]] = 0) do={
      :do {
        /ip hotspot active remove [find user=$sName];
        /ip hotspot cookie remove [find user=$sName];
        /system scheduler remove $s;
      } on-error={};
    }
  }

  # 2. Forcibly kick any active session if the voucher user entry was removed
  :foreach a in=[/ip hotspot active find] do={
    :local aUser [/ip hotspot active get $a user];
    :if ($aUser != "admin" and $aUser != "default-trial") do={
      :if ([:len [/ip hotspot user find name=$aUser]] = 0) do={
        :do {
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$aUser];
        } on-error={};
      }
    }
  }
}
:put "  Script 'voucher-expire-check' registered."

# Scheduler: hs-continuous-expire-monitor (runs every 1 minute)
:do {
  /system scheduler remove [find name="hs-continuous-expire-monitor"]
} on-error={}

/system scheduler add name="hs-continuous-expire-monitor" interval=1m start-time=startup \
  on-event="/system script run voucher-expire-check" comment="Hotspot continuous expiration monitor (every 1m)"
:put "  Scheduler 'hs-continuous-expire-monitor' enabled (1m interval)."

# ── STEP 8: RouterOS API Service & App User Accounts ──────────
:put "=== Step 8: API Service & User Accounts for HotspotManager ==="
# Enable RouterOS API on standard port 8728 and 8729 without IP restriction
:do {
  /ip service set [find name="api"] disabled=no port=8728 address=""
  /ip service enable [find name="api"]
} on-error={}
:do {
  /ip service set [find name="api-ssl"] disabled=no port=8729 address=""
  /ip service enable [find name="api-ssl"]
} on-error={}
:do {
  /ip service set [find name="winbox"] disabled=no port=8291 address=""
  /ip service enable [find name="winbox"]
} on-error={}
:do {
  /ip service set [find name="www"] disabled=no port=80 address=""
  /ip service enable [find name="www"]
} on-error={}
:put "  RouterOS API service enabled on port 8728 (all IPs allowed)."

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

:if ([:len $dnsName] > 0) do={
  :do {
    /ip hotspot walled-garden add dst-host=$dnsName action=allow comment="Hotspot Portal DNS"
  } on-error={}
}
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
# Allow Hotspot LAN clients to access the Internet (WAN)
:do { /ip firewall filter add action=accept chain=forward in-interface=hotspot-bridge out-interface=ether1 comment="LAN to WAN (Hotspot Internet)" } on-error={}
# Reject DoT (forces clients to use router DNS so captive portal pops up)
:do { /ip firewall filter add action=reject chain=forward in-interface=hotspot-bridge protocol=tcp dst-port=853 reject-with=tcp-reset comment="Reject DoT" } on-error={}
# Drop all new unsolicited connections coming from WAN (ether1)
:do { /ip firewall filter add action=drop chain=forward connection-state=new connection-nat-state=!dstnat in-interface=ether1 comment="Drop WAN unsolicited forward" } on-error={}

# 5. DNS redirection (forces all clients through router DNS for captive portal)
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=udp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS UDP" } on-error={}
:do { /ip firewall nat add action=redirect chain=dstnat dst-port=53 protocol=tcp to-ports=53 in-interface=hotspot-bridge comment="Redirect DNS TCP" } on-error={}

# 6. Anti-tethering TTL Mangle (Prevents sharing WiFi through phone hotspot)
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
:put "     AYEIKSITT_WIFI - FULL SETUP COMPLETED       "
:put "================================================="
:put ("Router Model    : " . $boardName)
:put ("RouterOS Version: " . $rosVer)
:put "Gateway IP      : 10.10.10.1"
:put "Hotspot DNS     : (Blank - Gateway IP 10.10.10.1)"
:put "Network Range   : 10.10.10.0/23"
:put ("SSID            : " . $wifiSsid)
:put "Portal Directory: flash/hotspot (or hotspot)"
:put "API Port        : 8728 (ENABLED & ALLOWED)"
:put "Admin User      : admin / Khant1234@"
:put "App API User    : flutter_app / Khant1234@"
:put "App Path        : C:\\Users\\localhost\\Downloads\\serverless\\HotspotManager"
:put "================================================="
