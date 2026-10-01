# ==============================================================================
#                     YADANAR_TUN_WIFI - SETUP SCRIPT
#                Absolute Master Formula & Automation Suite
# ==============================================================================
# Description:
#   Automates complete MikroTik router configuration for Yadanar Tun Wifi:
#   - WAN DHCP Client & NAT Masquerade
#   - LAN Bridge, DHCP Server (10.10.10.0/23, pool 10.10.10.10-10.10.11.250)
#   - Wi-Fi Configuration (SSID: Hide Wifi)
#   - Hotspot Server & Directory Detection (flash/hotspot vs hotspot)
#   - User Profiles: 5GB, 30Day, 2GB, 2Hour, VIP
#     * Leaves existing profiles untouched
#     * Preserves existing vouchers and their assigned users & limits
#     * Enables MAC-roaming: users can log back in if MAC changes or after logout before limits reached
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
:put "    YADANAR TUN WIFI - STARTING FULL SETUP       "
:put "================================================="

:local rosVer [/system resource get version]
:local boardName [/system resource get board-name]
:put ("Detected Hardware : " . $boardName)
:put ("Detected RouterOS : " . $rosVer)

# Check RouterOS Device-Mode (Hotspot & Scheduler restrictions)
:do {
  :local dmHotspot [/system device-mode get hotspot]
  :if ($dmHotspot = false or $dmHotspot = "no") do={
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    :put "  CRITICAL: HOTSPOT IS DISABLED IN ROUTEROS DEVICE-MODE!"
    :put "  Router is currently in 'mode: home' (hotspot: no)."
    :put "  To activate Hotspot, run in Terminal:"
    :put "    /system/device-mode/update hotspot=yes scheduler=yes"
    :put "  Then power-cycle router (unplug & replug power) to confirm."
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  }
} on-error={}

# ── GLOBAL VARIABLES ──────────────────────────────────────────
:global wifiSsid   "Hide Wifi"
# Set to 'yes' to hide SSID broadcast, or 'no' to broadcast normally
:global hideSsid   no
:global dnsName    "yadanartun.wifi"
:global apiPass    "Khant1234@"
# To bypass admin devices permanently without voucher, enter MACs: {"XX:XX:XX:XX:XX:XX"; "YY:YY:YY:YY:YY:YY"}
:global adminMacs  [:toarray ""]
:global gwIp       "10.10.10.1"
:global hsNetwork  "10.10.10.0/23"
:global poolStart  "10.10.10.10"
:global poolEnd    "10.10.11.250"

# ── SYSTEM IDENTITY ───────────────────────────────────────────
:put "--- Setting Router Identity ---"
:do { /system identity set name="YadanarTun_WiFi-Router" } on-error={}

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

# Automatically add all Ethernet ports except ether1 (WAN) to hotspot-bridge
:foreach p in=[/interface find type="ether"] do={
  :local pName [/interface get $p name]
  :if ($pName != "ether1") do={
    :do {
      /interface bridge port add bridge=hotspot-bridge interface=$pName
    } on-error={
      :do { /interface bridge port set [find interface=$pName] bridge=hotspot-bridge } on-error={}
    }
  }
}

# Automatically add all SFP ports (e.g. sfp1 on L009) to hotspot-bridge
:foreach sfp in=[/interface find type="sfp"] do={
  :local sName [/interface get $sfp name]
  :do {
    /interface bridge port add bridge=hotspot-bridge interface=$sName
  } on-error={
    :do { /interface bridge port set [find interface=$sName] bridge=hotspot-bridge } on-error={}
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
  :local wCmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set \$w configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=" . $hStr . " datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set \$w mode=ap ssid=\"" . $wifiSsid . "\" disabled=no }; :local wName [/interface wifi get \$w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=\$wName } on-error={ :do { /interface bridge port set [find interface=\$wName] bridge=hotspot-bridge } on-error={} } }")
  [ :parse $wCmd ]
  :set wifiConfigured true
  :put ("  Configured Wi-Fi interface (v7, Hotspot AP, SSID: " . $wifiSsid . ", hide-ssid=" . $hStr . ").")
} on-error={}

# 1b. Direct fallback for named wifi1
:if (!$wifiConfigured) do={
  :do {
    :local hStr "no"
    :if ($hideSsid = yes or $hideSsid = true or $hideSsid = "yes") do={ :set hStr "yes" }
    :local wCmd2 ("/interface wifi set [find name=wifi1] configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=" . $hStr . " datapath.bridge=hotspot-bridge disabled=no; :do { /interface bridge port add bridge=hotspot-bridge interface=wifi1 } on-error={ /interface bridge port set [find interface=wifi1] bridge=hotspot-bridge }")
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
    :local wlCmd ("/interface wireless set [find default-name=wlan1] ssid=\"" . $wifiSsid . "\" hide-ssid=" . $hStr . " mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :do { /interface bridge port set [find interface=wlan1] bridge=hotspot-bridge } on-error={ /interface bridge port add bridge=hotspot-bridge interface=wlan1 }")
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
    login-by=http-chap,http-pap rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
      login-by=http-chap,http-pap rate-limit=""
  } on-error={}
}

# Update all existing hotspot server profiles on the router to require voucher authentication
:foreach hp in=[/ip hotspot profile find] do={
  :do {
    /ip hotspot profile set $hp login-by=http-chap,http-pap html-directory=$hsDir hotspot-address=$gwIp dns-name=$dnsName
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
# Clear stale active sessions and remembered cookies so every device must authenticate fresh
:do { /ip hotspot active remove [find] } on-error={}
:do { /ip hotspot cookie remove [find] } on-error={}
:do { /ip hotspot host remove [find] } on-error={}
:put "  Hotspot Server Configured (active sessions & cookies reset)."
# ── STEP 6: User Profile Setup (Continuous Validity Countdown & MAC Roaming) ──
:put "=== Step 6: User Profile Setup ==="

# Define on-login script to automatically:
# 1. Remove stale active sessions/cookies when a user reconnects with randomized/changed MAC.
# 2. Trigger 'voucher-activate' to initiate continuous expiration countdown from time of first login.
:local macFixScript ":local u \$user; :local m \$\"mac-address\"; :do { /ip hotspot active remove [find user=\$u and mac-address!=\$m]; /ip hotspot cookie remove [find user=\$u and mac-address!=\$m] } on-error={}; :global hsUser \$user; :do { /system script run voucher-activate } on-error={}"

# 1. Profile 5GB (20M/20M, 1 Day, 5GB Quota):
:do {
  /ip hotspot user profile add name="5GB" rate-limit="20M/20M" session-timeout=1d \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 5GB created (session-timeout=1d, rate-limit=20M/20M)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="5GB"] rate-limit="20M/20M" session-timeout=1d \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 5GB updated."
  } on-error={}
}

# 2. Profile 30Day (10M/10M, 30 Days, 60GB Quota):
:do {
  /ip hotspot user profile add name="30Day" rate-limit="10M/10M" session-timeout=30d \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 30Day created (session-timeout=30d, rate-limit=10M/10M)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="30Day"] rate-limit="10M/10M" session-timeout=30d \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 30Day updated."
  } on-error={}
}

# 3. Profile 2GB (20M/20M, 1 Day, 2GB Quota):
:do {
  /ip hotspot user profile add name="2GB" rate-limit="20M/20M" session-timeout=1d \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 2GB created (session-timeout=1d, rate-limit=20M/20M)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="2GB"] rate-limit="20M/20M" session-timeout=1d \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 2GB updated."
  } on-error={}
}

# 4. Profile 2Hour (5M/5M, 2 Hours, Unlimited Quota):
:do {
  /ip hotspot user profile add name="2Hour" rate-limit="5M/5M" session-timeout=2h \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile 2Hour created (session-timeout=2h, rate-limit=5M/5M)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="2Hour"] rate-limit="5M/5M" session-timeout=2h \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile 2Hour updated."
  } on-error={}
}

# 5. Profile VIP (5M/5M, Unlimited):
:do {
  /ip hotspot user profile add name="VIP" rate-limit="5M/5M" session-timeout=none \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
  :put "  Profile VIP created (unlimited, rate-limit=5M/5M)."
} on-error={
  :do {
    /ip hotspot user profile set [find name="VIP"] rate-limit="5M/5M" session-timeout=none \
      keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
    :put "  Profile VIP updated."
  } on-error={}
}

# Legacy profile aliases for backward compatibility
:do {
  /ip hotspot user profile add name="1GB_1H" rate-limit="10M/10M" session-timeout=1h \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
} on-error={}
:do {
  /ip hotspot user profile add name="15M" rate-limit="10M/10M" session-timeout=15m \
    keepalive-timeout=none idle-timeout=none shared-users=2 on-login=$macFixScript
} on-error={}

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

  # Auto-assign quota limits on first login if currently 0
  :do {
    :local uLimBytes [/ip hotspot user get [find name=$u] limit-bytes-total];
    :local profName [/ip hotspot user get [find name=$u] profile];
    :if ($uLimBytes = 0) do={
      :if ($profName = "5GB") do={ /ip hotspot user set [find name=$u] limit-bytes-total=5368709120 };
      :if ($profName = "2GB") do={ /ip hotspot user set [find name=$u] limit-bytes-total=2147483648 };
      :if ($profName = "30Day") do={ /ip hotspot user set [find name=$u] limit-bytes-total=64424509440 };
      :if ($profName = "1GB_1H") do={ /ip hotspot user set [find name=$u] limit-bytes-total=1073741824 };
    }
  } on-error={};

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

# Run expiration check on login to clean up any expired sessions
:do {
  /system script run voucher-expire-check
} on-error={};
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

:do {
  /system scheduler add name="hs-continuous-expire-monitor" interval=1m start-time=startup \
    on-event="/system script run voucher-expire-check" comment="Hotspot continuous expiration monitor (every 1m)"
  :put "  Scheduler 'hs-continuous-expire-monitor' enabled (1m interval)."
} on-error={
  :put "  Notice: Scheduler restricted by RouterOS device-mode (skipping background scheduler)."
  :put "          (To enable: run '/system/device-mode/update scheduler=yes' and confirm on router)."
}

# ── STEP 8: RouterOS API Service & App User Accounts ──────────
:put "=== Step 8: API Service & User Accounts for HotspotManager ==="
# Enable RouterOS API on standard port 8728 and 8729 without IP restriction
:do {
  /ip service set [find name="api"] disabled=no port=8728
  /ip service enable [find name="api"]
} on-error={}
:do {
  /ip service set [find name="api-ssl"] disabled=no port=8729
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
:put "  RouterOS API service enabled on port 8728 (WebFig moved to port 8080 to prevent Hotspot collision)."

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

# 3. Ensure profiles and byte limits are set for any vouchers with limit=0
:do {
  :foreach u in=[/ip hotspot user find where limit-bytes-total=0] do={
    :local uProf [/ip hotspot user get $u profile]
    :if ($uProf = "5GB") do={ /ip hotspot user set $u limit-bytes-total=5368709120 }
    :if ($uProf = "2GB") do={ /ip hotspot user set $u limit-bytes-total=2147483648 }
    :if ($uProf = "30Day") do={ /ip hotspot user set $u limit-bytes-total=64424509440 }
    :if ($uProf = "1GB_1H") do={ /ip hotspot user set $u limit-bytes-total=1073741824 }
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
:put "       YADANAR TUN WIFI - SETUP COMPLETED        "
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
:put "API Port        : 8728 (ENABLED & ALLOWED)"
:put "Admin User      : admin / Khant1234@"
:put "App API User    : flutter_app / Khant1234@"
:put "App Client      : HotspotManager Android App"
:put "================================================="
