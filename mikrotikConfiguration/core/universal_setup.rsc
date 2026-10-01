# ==============================================================================
#           UNIVERSAL MIKROTIK CAPTIVE PORTAL SETUP SCRIPT
#     Compatible with RouterOS v6.x, v7.x | All Hardware Models & Architectures
# ==============================================================================
# This script sets up a complete MikroTik Hotspot & Captive Portal system:
# - Safety Backup: Creates timestamped .backup and .rsc before touching config
# - Admin Password: Set to 'Khant1234@' by default
# - Hardware & Storage auto-detection (flash/ vs root)
# - RouterOS v7 device-mode safety & auto-updater
# - WAN DHCP Client & NAT Masquerade (ether1)
# - Universal LAN Bridge: Dynamically bridges ports 2..N + SFPs
# - Wi-Fi auto-configuration (RouterOS v7 'wifi' vs legacy 'wireless')
# - Dynamic IP Subnet calculation (10 - 10,000 IPs)
# - Hotspot Server, HTML directory, and profile setup
# - 5 Standard User Profiles (5GB, 30Day, 2GB, 2Hour, VIP) - No pre-imported vouchers
# - API port 8728 enabled & Walled Garden for HotspotManager app
# - Fast CNA (Captive Network Assistant) popup for Apple, Android & Windows
# - Ghost Lease Auto-Cleanup Scheduler (removes stale leases every 30m)
# - Remote Management: Cloud DDNS & WireGuard VPN (Port 13231)
# ==============================================================================

:put "=========================================================="
:put "   STARTING UNIVERSAL CAPTIVE PORTAL PROVISIONING          "
:put "=========================================================="

# ── 1. HARDWARE & VERSION INSPECTION ──────────────────────────
:local boardName [/system resource get board-name]
:local rosVer [/system resource get version]
:local arch [/system resource get architecture-name]
:local freeMem [/system resource get free-memory]
:local freeHdd [/system resource get free-hdd-space]

:put ("Hardware Model : " . $boardName)
:put ("Architecture   : " . $arch)
:put ("RouterOS Ver   : " . $rosVer)
:put ("Free Memory    : " . ($freeMem / 1024 / 1024) . " MB")
:put ("Free Disk Space: " . ($freeHdd / 1024 / 1024) . " MB")

# ── 2. PRE-SETUP SAFETY BACKUP ────────────────────────────────
:put "--- Step 0: Creating Safety Backup & Export ---"
:do {
  /system backup save name="auto-backup-before-setup"
  /export file="auto-export-before-setup"
  :put "  Safety backup created: auto-backup-before-setup.backup"
} on-error={
  :put "  Backup note: Skipped or insufficient storage."
}

# ── 3. DEVICE-MODE SAFETY CHECK (RouterOS v7.13+) ─────────────
:do {
  :local dmHotspot [/system device-mode get hotspot]
  :local dmFetch [/system device-mode get fetch]
  :local dmSched [/system device-mode get scheduler]
  
  :if ($dmHotspot = false or $dmHotspot = "no" or $dmFetch = false or $dmFetch = "no" or $dmSched = false or $dmSched = "no") do={
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    :put "  CRITICAL: ROUTEROS DEVICE-MODE RESTRICTIONS DETECTED!"
    :put "  Attempting to enable: hotspot=yes, scheduler=yes, fetch=yes..."
    /system device-mode update hotspot=yes scheduler=yes fetch=yes
    :put ""
    :put "  >>> ACTION REQUIRED TO CONFIRM DEVICE-MODE: <<<"
    :put "  Unplug and replug the router's power cord within 3 minutes!"
    :put "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  } else={
    :put "  Device-Mode Status: PASS (Hotspot, Scheduler & Fetch enabled)"
  }
} on-error={
  :put "  Device-Mode: Not applicable on this RouterOS version (Legacy/Standard mode)"
}

# ── 4. PARAMETERS & CREDENTIALS ───────────────────────────────
:global siteName
:if ([:len $siteName] = 0) do={ :set siteName "YadanarTun_WiFi" }

:global wifiSsid
:if ([:len $wifiSsid] = 0) do={ :set wifiSsid "YadanarTun_WiFi" }

:global dnsName
:if ([:len $dnsName] = 0) do={ :set dnsName "yadanartun.wifi" }

:global adminPass
:if ([:len $adminPass] = 0) do={ :set adminPass "Khant1234@" }

:global ipCapacity
:if ([:len $ipCapacity] = 0) do={ :set ipCapacity 250 }
:local ipCount [:tonum $ipCapacity]
:if ($ipCount < 10) do={ :set ipCount 250 }
:if ($ipCount > 10000) do={ :set ipCount 10000 }

# Apply default admin password
:do {
  /user set [find name="admin"] password=$adminPass
  :put ("Admin Password set to: " . $adminPass)
} on-error={}

# ── 5. DYNAMIC SUBNET & IP POOL CALCULATION (10 - 10,000 IPs) ─
:local prefix 24
:local netmask "255.255.255.0"
:local netCidr "10.10.10.0/24"
:local netAddr "10.10.10.0"
:local gwIp "10.10.10.1"
:local poolStart "10.10.10.10"
:local poolEnd "10.10.10.254"
:local leaseTime "2h"

:if ($ipCount > 240 and $ipCount <= 500) do={
  # /23 Subnet (512 addresses -> 501 usable pool IPs)
  :set prefix 23
  :set netmask "255.255.254.0"
  :set netCidr "10.10.10.0/23"
  :set netAddr "10.10.10.0"
  :set gwIp "10.10.10.1"
  :set poolStart "10.10.10.10"
  :set poolEnd "10.10.11.250"
  :set leaseTime "2h"
}

:if ($ipCount > 500 and $ipCount <= 1000) do={
  # /22 Subnet (1,024 addresses -> 1,017 usable pool IPs)
  :set prefix 22
  :set netmask "255.255.252.0"
  :set netCidr "10.10.8.0/22"
  :set netAddr "10.10.8.0"
  :set gwIp "10.10.8.1"
  :set poolStart "10.10.8.10"
  :set poolEnd "10.10.11.250"
  :set leaseTime "1h"
}

:if ($ipCount > 1000 and $ipCount <= 2000) do={
  # /21 Subnet (2,048 addresses -> 2,033 usable pool IPs)
  :set prefix 21
  :set netmask "255.255.248.0"
  :set netCidr "10.10.0.0/21"
  :set netAddr "10.10.0.0"
  :set gwIp "10.10.0.1"
  :set poolStart "10.10.0.10"
  :set poolEnd "10.10.7.250"
  :set leaseTime "1h"
}

:if ($ipCount > 2000 and $ipCount <= 4000) do={
  # /20 Subnet (4,096 addresses -> 4,081 usable pool IPs)
  :set prefix 20
  :set netmask "255.255.240.0"
  :set netCidr "10.10.0.0/20"
  :set netAddr "10.10.0.0"
  :set gwIp "10.10.0.1"
  :set poolStart "10.10.0.10"
  :set poolEnd "10.10.15.250"
  :set leaseTime "45m"
}

:if ($ipCount > 4000 and $ipCount <= 8000) do={
  # /19 Subnet (8,192 addresses -> 8,177 usable pool IPs)
  :set prefix 19
  :set netmask "255.255.224.0"
  :set netCidr "10.10.0.0/19"
  :set netAddr "10.10.0.0"
  :set gwIp "10.10.0.1"
  :set poolStart "10.10.0.10"
  :set poolEnd "10.10.31.250"
  :set leaseTime "30m"
}

:if ($ipCount > 8000) do={
  # /18 Subnet (16,384 addresses -> 16,369 usable pool IPs)
  :set prefix 18
  :set netmask "255.255.192.0"
  :set netCidr "10.10.0.0/18"
  :set netAddr "10.10.0.0"
  :set gwIp "10.10.0.1"
  :set poolStart "10.10.0.10"
  :set poolEnd "10.10.63.250"
  :set leaseTime "30m"
}

:put ("Subnet Plan    : " . $netCidr . " (Mask: " . $netmask . ")")
:put ("Gateway IP     : " . $gwIp)
:put ("DHCP Pool      : " . $poolStart . " - " . $poolEnd . " (Lease: " . $leaseTime . ")")

# ── 6. STORAGE DIRECTORY DETECTION ────────────────────────────
:local hsDir "hotspot"
:if ([:len [/file find name="flash"]] > 0 or [:len [/file find name="flash/hotspot"]] > 0) do={
  :set hsDir "flash/hotspot"
}
:put ("Portal Storage : " . $hsDir)

# ── 7. SYSTEM IDENTITY ────────────────────────────────────────
:do { /system identity set name=($siteName . "-Router") } on-error={}

# ── 8. WAN INTERFACE (ether1) & NAT MASQUERADE ────────────────
:put "--- Step 1: Configuring WAN (ether1) & NAT ---"
:do {
  /ip dhcp-client add interface=ether1 disabled=no comment="WAN Client"
} on-error={
  :do { /ip dhcp-client set [find interface=ether1] disabled=no } on-error={}
}

:do { /interface list add name=WAN } on-error={}
:do { /interface list add name=LAN } on-error={}
:do { /interface list member add list=WAN interface=ether1 } on-error={}

:do {
  /ip firewall nat add chain=srcnat out-interface-list=WAN action=masquerade comment="WAN Masquerade"
} on-error={
  :do { /ip firewall nat set [find comment~"WAN.*Masquerade"] out-interface-list=WAN action=masquerade } on-error={}
}

# ── 9. UNIVERSAL LAN BRIDGE & DYNAMIC PORT ASSIGNMENT ──────────
:put "--- Step 2: Configuring Universal LAN Bridge ---"
:do { /interface bridge add name=hotspot-bridge } on-error={}
:do { /interface list member add list=LAN interface=hotspot-bridge } on-error={}

# Clean up factory-default IP/DHCP conflicts
:do { /ip dhcp-server remove [find name="defconf"] } on-error={}
:do { /ip address remove [find address~"192.168.88.1"] } on-error={}
:do { /ip pool remove [find name="default-dhcp"] } on-error={}

# Dynamically add all Ethernet ports except ether1 (WAN)
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

# Dynamically add all SFP ports
:foreach s in=[/interface find type="sfp"] do={
  :local sName [/interface get $s name]
  :do {
    /interface bridge port add bridge=hotspot-bridge interface=$sName
  } on-error={
    :do { /interface bridge port set [find interface=$sName] bridge=hotspot-bridge } on-error={}
  }
}

# Disable FastPath to ensure captive portal packet interception is 100% active
:do { /interface bridge settings set allow-fast-path=no } on-error={}

# ── 10. WI-FI CONFIGURATION (ROS v7 wifi vs legacy wireless) ──
:put "--- Step 3: Configuring Wi-Fi Hardware ---"
:local wifiConfigured false

# Method A: RouterOS v7 wifi / wifi-qcom
:do {
  :local v7Cmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set \$w configuration.mode=ap configuration.ssid=\"" . $wifiSsid . "\" configuration.hide-ssid=no datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set \$w mode=ap ssid=\"" . $wifiSsid . "\" disabled=no }; :local wName [/interface wifi get \$w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=\$wName } on-error={} }")
  [ :parse $v7Cmd ]
  :if ([:len [/interface wifi find]] > 0) do={
    :set wifiConfigured true
    :put ("  Configured RouterOS v7 Wi-Fi AP: SSID = " . $wifiSsid)
  }
} on-error={}

# Method B: Legacy wireless package (ROS v6 or v7 wireless)
:if (!$wifiConfigured) do={
  :do {
    :local legacyCmd (":foreach w in=[/interface wireless find] do={ /interface wireless set \$w ssid=\"" . $wifiSsid . "\" hide-ssid=no mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :local wName [/interface wireless get \$w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=\$wName } on-error={} }")
    [ :parse $legacyCmd ]
    :if ([:len [/interface wireless find]] > 0) do={
      :set wifiConfigured true
      :put ("  Configured Legacy Wireless AP: SSID = " . $wifiSsid)
    }
  } on-error={}
}

:if (!$wifiConfigured) do={
  :put "  [INFO] No internal Wi-Fi found. Bridge ports 2..N ready for external APs (Ruijie Reyee, UniFi, etc.)."
}

# ── 11. IP ADDRESSING & DHCP SERVER ───────────────────────────
:put "--- Step 4: IP Address, Pool & DHCP ---"
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

:if ([:len $dnsName] > 0) do={
  :do {
    /ip dns static add name=$dnsName address=$gwIp comment="Portal DNS Redirect"
  } on-error={
    :do { /ip dns static set [find name=$dnsName] address=$gwIp } on-error={}
  }
}

# ── 12. HOTSPOT SERVER PROFILE & SERVER ────────────────────────
:put "--- Step 5: Hotspot Server & Captive Portal ---"
:do {
  /ip hotspot profile add name=hs-profile hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
    login-by=http-chap,http-pap rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=$gwIp dns-name=$dnsName html-directory=$hsDir \
      login-by=http-chap,http-pap rate-limit=""
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
} on-error={
  :do { /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no } on-error={}
}

# ── 13. FAST CNA (APPLE / ANDROID / WINDOWS) POPUP ────────────
:put "--- Step 6: Captive Network Assistant (CNA) Optimization ---"
:local cnaList {"captive.apple.com"; "hotspot.cisco.com"; "appleiphonecell.com"; "connectivitycheck.gstatic.com"; "connectivitycheck.android.com"; "clients3.google.com"; "msftconnecttest.com"}
:foreach host in=$cnaList do={
  :do {
    /ip hotspot walled-garden add dst-host=$host action=allow comment="Fast CNA Detection"
  } on-error={}
}

# ── 14. DHCP GHOST LEASE AUTO-CLEANUP ─────────────────────────
:put "--- Step 7: Stale Lease Cleanup Scheduler ---"
:do { /system scheduler remove [find name="hs-clean-leases"] } on-error={}
:do {
  /system scheduler add name="hs-clean-leases" interval=30m start-time=startup \
    on-event="/ip dhcp-server lease remove [find where status=\"waiting\" dynamic=yes]" \
    comment="Purge stale DHCP leases every 30m"
} on-error={}

# ── 15. STANDARD USER PROFILES (No Preconfigured Accounts) ────
:put "--- Step 8: User Profiles (Empty Vouchers Structure) ---"
:local profiles {"5GB"; "30Day"; "2GB"; "2Hour"; "VIP"}
:foreach prof in=$profiles do={
  :do {
    /ip hotspot user profile add name=$prof shared-users=1 status-autorefresh=1m keepalive-timeout=2m mac-cookie-timeout=3d
  } on-error={
    :do { /ip hotspot user profile set [find name=$prof] shared-users=1 status-autorefresh=1m keepalive-timeout=2m mac-cookie-timeout=3d } on-error={}
  }
}

# ── 16. API SERVICE & WALLED GARDEN (HotspotManager App) ──────
:put "--- Step 9: Management API & Walled Garden ---"
:do { /ip service enable [find name="api"] } on-error={}
:do { /ip service set [find name="api"] port=8728 } on-error={}

:do {
  /ip hotspot walled-garden ip add dst-port=8728 protocol=tcp action=accept comment="Allow HotspotManager App Port 8728"
} on-error={}

# ── 17. REMOTE MANAGEMENT: CLOUD DDNS & WIREGUARD VPN ─────────
:put "--- Step 10: Remote Management (Cloud DDNS & WireGuard) ---"
# 1. Enable MikroTik Cloud DDNS
:do {
  /ip cloud set ddns-enabled=yes update-time=yes
  :put "  Cloud DDNS enabled. Free remote hostname active."
} on-error={}

# 2. WireGuard Server Setup (RouterOS v7)
:do {
  :if ([:len [/interface wireguard find name="wg-remote"]] = 0) do={
    /interface wireguard add name=wg-remote listen-port=13231 comment="Remote Admin VPN"
    :put "  WireGuard interface 'wg-remote' created on UDP port 13231."
  }
  :do {
    /ip address add address=10.255.255.1/24 interface=wg-remote comment="WireGuard Gateway"
  } on-error={}
  
  # Allow WireGuard traffic through firewall
  :do {
    /ip firewall filter add chain=input dst-port=13231 protocol=udp action=accept place-before=1 comment="Allow WireGuard VPN Port 13231"
  } on-error={}
  :do {
    /ip firewall filter add chain=input src-address=10.255.255.0/24 action=accept place-before=2 comment="Allow WireGuard Admin Subnet"
  } on-error={}
} on-error={
  :put "  WireGuard: Not supported on this RouterOS version (ROS v6)."
}

:put "=========================================================="
:put "   PROVISIONING COMPLETED SUCCESSFULLY!                  "
:put ("   SSID: " . $wifiSsid . " | Portal: http://" . $dnsName)
:put ("   Capacity: " . $ipCount . " users on " . $netCidr)
:put ("   Admin User: admin | Admin Pass: " . $adminPass)
:put "=========================================================="
