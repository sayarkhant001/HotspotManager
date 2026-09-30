# ==============================================================================
#                  A YEIK SITT WIFI (1) - FULL SETUP SCRIPT
#            Captive Portal: Glassmorphism Design (A Yeik Sitt 1)
# ==============================================================================
# Usage:
#   Upload "A Yeik Sitt 1" folder and fullsetup.rsc to MikroTik Files, then run:
#   /import fullsetup.rsc
# ==============================================================================

:put "================================================="
:put "    A YEIK SITT WIFI 1 - STARTING FULL SETUP     "
:put "================================================="

:local rosVer [/system resource get version]
:local boardName [/system resource get board-name]
:put ("Detected Hardware : " . $boardName)
:put ("Detected RouterOS : " . $rosVer)

# ── GLOBAL VARIABLES ──────────────────────────────────────────
:global wifiSsid   "A_Yeik_Sitt_Wifi"
:global dnsName    "ayeiksitt.lan"
:global apiPass    "Khant1234@"
:global adminMacs  {"A0:29:19:39:34:61";"CC:15:31:83:26:BF"}
:global gwIp       "10.10.10.1"
:global hsNetwork  "10.10.10.0/23"
:global poolStart  "10.10.10.10"
:global poolEnd    "10.10.11.250"

# ── SYSTEM IDENTITY ───────────────────────────────────────────
:put "--- Setting Router Identity ---"
:do { /system identity set name="AYeikSitt-Router" } on-error={}

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
:do {
  /interface wifi set [find default-name=wifi1] configuration.ssid=$wifiSsid disabled=no
  :do { /interface bridge port add bridge=hotspot-bridge interface=wifi1 } on-error={}
  :put "  Configured wifi1 interface (v7)."
} on-error={
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

:do {
  /ip dns static add name=$dnsName address=$gwIp comment="Hotspot Portal DNS"
} on-error={
  :do { /ip dns static set [find name=$dnsName] address=$gwIp } on-error={}
}
:put ("  Static DNS Configured: " . $dnsName . " -> " . $gwIp)
:put "  IP & DHCP Configured."

# ── STEP 5: Hotspot Server & Directory Detection ───────────────
:put "=== Step 5: Hotspot Server & Directory ==="
:local hsDir "A Yeik Sitt 1"
:if ([:len [/file find name="flash/A Yeik Sitt 1"]] > 0) do={
  :set hsDir "flash/A Yeik Sitt 1"
} else={
  :if ([:len [/file find name="A Yeik Sitt 1"]] > 0) do={
    :set hsDir "A Yeik Sitt 1"
  } else={
    :if ([:len [/file find name="flash/hotspot"]] > 0) do={
      :set hsDir "flash/hotspot"
    } else={
      :set hsDir "hotspot"
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

:foreach hp in=[/ip hotspot profile find] do={
  :do {
    /ip hotspot profile set $hp login-by=mac-cookie,http-chap,http-pap html-directory=$hsDir hotspot-address=$gwIp dns-name=$dnsName
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
} on-error={
  :do {
    /ip hotspot set [find name=hs-server] address-pool=hs-pool profile=hs-profile disabled=no
  } on-error={}
}
:put "  Hotspot Server Configured."

# ── STEP 6: User Profiles ─────────────────────────────────────
:put "=== Step 6: User Profiles ==="
:do {
  /ip hotspot user profile add name="1GB_1H" rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h keepalive-timeout=2h idle-timeout=30m shared-users=1 \
    comment="1GB 1Hour Standard"
} on-error={
  :do {
    /ip hotspot user profile set [find name="1GB_1H"] rate-limit="10M/10M" session-timeout=1h \
      mac-cookie-timeout=1h keepalive-timeout=2h idle-timeout=30m shared-users=1 \
      comment="1GB 1Hour Standard"
  } on-error={}
}

:do {
  /ip hotspot user profile set [find name="default"] rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h shared-users=1
} on-error={}

# ── STEP 7: API Service & Credentials ─────────────────────────
:put "=== Step 7: API Service ==="
:do { /ip service set api disabled=no port=8728 } on-error={}
:do { /ip service set api-ssl disabled=yes } on-error={}

:do {
  /user group add name=flutter_api_group policy=api,read,write,test,policy,password,sensitive comment="HotspotManager API"
} on-error={}

:do {
  /user add name=flutter_app password=$apiPass group=flutter_api_group comment="HotspotManager App API"
} on-error={
  :do { /user set [find name=flutter_app] password=$apiPass group=flutter_api_group } on-error={}
}

# ── STEP 8: Walled Garden & Admin Bypass ──────────────────────
:put "=== Step 8: Walled Garden & Admin Bypass ==="
:do { /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8728 action=accept comment="HotspotManager API" } on-error={}
:do { /ip hotspot walled-garden ip add dst-address=$gwIp dst-port=8729 action=accept comment="HotspotManager API-SSL" } on-error={}
:do { /ip hotspot walled-garden add dst-host=$dnsName action=allow comment="Hotspot Portal DNS" } on-error={}

:foreach mac in=$adminMacs do={
  :do {
    /ip hotspot ip-binding add mac-address=$mac type=bypassed comment="Admin Phone Bypass"
  } on-error={
    :do { /ip hotspot ip-binding set [find mac-address=$mac] type=bypassed } on-error={}
  }
}

# ── STEP 9: Clock & NTP ───────────────────────────────────────
:do { /system clock set time-zone-name=Asia/Yangon } on-error={}
:do { /system ntp client set enabled=yes } on-error={}
:do { /system ntp client servers add address=pool.ntp.org } on-error={}

# ── STEP 10: Firewall Rules ───────────────────────────────────
:do {
  /ip firewall filter add action=accept chain=input dst-port=8728,8729 protocol=tcp in-interface=hotspot-bridge place-before=0 comment="Allow HotspotManager API"
} on-error={}
:do { /ip firewall filter add action=accept chain=input dst-port=53,67 protocol=udp in-interface=hotspot-bridge comment="Allow DNS/DHCP UDP" } on-error={}
:do { /ip firewall filter add action=accept chain=input dst-port=53 protocol=tcp in-interface=hotspot-bridge comment="Allow DNS TCP" } on-error={}
:do { /ip firewall filter add action=accept chain=input dst-port=80 protocol=tcp in-interface=hotspot-bridge comment="Allow Hotspot Web" } on-error={}

:put ""
:put "================================================="
:put "    A YEIK SITT WIFI 1 - FULL SETUP COMPLETED    "
:put "================================================="
:put ("Router Model    : " . $boardName)
:put ("RouterOS Version: " . $rosVer)
:put "Gateway IP      : 10.10.10.1"
:put "Hotspot DNS     : ayeiksitt.lan (Points to 10.10.10.1)"
:put "SSID            : A_Yeik_Sitt_Wifi"
:put ("Portal Directory: " . $hsDir)
:put "================================================="
