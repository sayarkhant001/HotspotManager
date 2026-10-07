package com.example.utils

/**
 * Universal MikroTik Provisioning Script Generator.
 * Fully compatible with RouterOS v6 and v7 across all hardware models.
 * Operates in Direct IP Gateway mode (hotspot-address=gwIp, dns-name="")
 * to completely eliminate DNS redirection issues and SSL errors on all mobile devices.
 */
object UniversalSetupHelper {

    fun generateScript(
        wifiSsid: String = "YadanarTun",
        adminPassword: String = "Khant1234@",
        capacity: Int = 250,
        wireguardScript: String? = null
    ): String {
        val safeSsid = wifiSsid.replace("\"", "").trim().ifBlank { "YadanarTun" }
        val safePass = adminPassword.replace("\"", "").trim().ifBlank { "Khant1234@" }
        val safeCap = capacity.coerceIn(10, 10000)

        val template = """
# ==============================================================================
#           UNIVERSAL MIKROTIK HOTSPOT SETUP SCRIPT (PHONE GENERATED)
#     Compatible with RouterOS v6.x, v7.x | Direct IP Mode (No DNS Required)
# ==============================================================================

:put "=========================================================="
:put "   STARTING UNIVERSAL CAPTIVE PORTAL PROVISIONING          "
:put "=========================================================="

:global siteName "__SAFE_SSID__"
:global wifiSsid "__SAFE_SSID__"
:global dnsName ""
:global adminPass "__SAFE_PASS__"
:global ipCapacity __SAFE_CAP__

# 1. ADMIN CREDENTIALS
:do {
  /user set [find name="admin"] password=__SAFE_PASS__
  :put ("Admin Password set to: " . __SAFE_PASS__)
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

:if (__SAFE_CAP__ > 250) do={
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
  :local pName [/interface ethernet get @DOL@p name]
  :if (@DOL@pName != "ether1") do={
    :do {
      /interface bridge port add bridge=hotspot-bridge interface=@DOL@pName
    } on-error={}
  }
}

# 5. WI-FI AP CONFIGURATION (v7 wifi vs v6 wireless)
:local wifiConfigured false

# RouterOS v7 wifi
:do {
  :local v7Cmd (":foreach w in=[/interface wifi find] do={ :do { /interface wifi set @DOL@w configuration.mode=ap configuration.ssid=\"__SAFE_SSID__\" configuration.hide-ssid=no datapath.bridge=hotspot-bridge security.authentication-types=\"\" disabled=no } on-error={ /interface wifi set @DOL@w mode=ap ssid=\"__SAFE_SSID__\" disabled=no }; :local wName [/interface wifi get @DOL@w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=@DOL@wName } on-error={} }")
  [ :parse @DOL@v7Cmd ]
  :if ([:len [/interface wifi find]] > 0) do={ :set wifiConfigured true }
} on-error={}

# RouterOS v6 wireless
:if (!@DOL@wifiConfigured) do={
  :do {
    :local legacyCmd (":foreach w in=[/interface wireless find] do={ /interface wireless set @DOL@w ssid=\"__SAFE_SSID__\" hide-ssid=no mode=ap-bridge security-profile=default disabled=no; :do { /interface wireless security-profile set [find default=yes] authentication-types=\"\" mode=none } on-error={}; :local wName [/interface wireless get @DOL@w name]; :do { /interface bridge port add bridge=hotspot-bridge interface=@DOL@wName } on-error={} }")
    [ :parse @DOL@legacyCmd ]
    :if ([:len [/interface wireless find]] > 0) do={ :set wifiConfigured true }
  } on-error={}
}

# 6. IP ADDRESSING & DHCP SERVER
:local fullGwAddr (@DOL@gwIp . "/" . @DOL@prefix)

:do {
  /ip address add address=@DOL@fullGwAddr network=@DOL@netAddr interface=hotspot-bridge comment="Hotspot Gateway"
} on-error={
  :do { /ip address set [find interface=hotspot-bridge] address=@DOL@fullGwAddr network=@DOL@netAddr } on-error={}
}

:do {
  /ip pool add name=hs-pool ranges=(@DOL@poolStart . "-" . @DOL@poolEnd)
} on-error={
  :do { /ip pool set [find name=hs-pool] ranges=(@DOL@poolStart . "-" . @DOL@poolEnd) } on-error={}
}

:do {
  /ip dhcp-server add name=hs-dhcp interface=hotspot-bridge address-pool=hs-pool lease-time=@DOL@leaseTime authoritative=yes disabled=no
} on-error={
  :do { /ip dhcp-server set [find name=hs-dhcp] interface=hotspot-bridge address-pool=hs-pool lease-time=@DOL@leaseTime disabled=no } on-error={}
}

:do {
  /ip dhcp-server network add address=@DOL@netCidr gateway=@DOL@gwIp netmask=@DOL@prefix dns-server=@DOL@gwIp comment="Hotspot Network"
} on-error={
  :do { /ip dhcp-server network set [find address=@DOL@netCidr] gateway=@DOL@gwIp netmask=@DOL@prefix dns-server=@DOL@gwIp } on-error={}
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
  /ip hotspot profile add name=hs-profile hotspot-address=@DOL@gwIp dns-name="" html-directory=@DOL@hsDir \
    login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
} on-error={
  :do {
    /ip hotspot profile set [find name=hs-profile] hotspot-address=@DOL@gwIp dns-name="" html-directory=@DOL@hsDir \
      login-by=cookie,http-chap,http-pap,mac-cookie http-cookie-lifetime=30d mac-cookie-timeout=30d rate-limit=""
  } on-error={}
}

:do {
  /ip hotspot add name=hs-server interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no
} on-error={
  :do { /ip hotspot set [find name=hs-server] interface=hotspot-bridge address-pool=hs-pool profile=hs-profile disabled=no } on-error={}
}

# 10. FAST CNA WALLED GARDEN (APPLE / ANDROID / WINDOWS)
:local cnaList {"captive.apple.com"; "hotspot.cisco.com"; "appleiphonecell.com"; "connectivitycheck.gstatic.com"; "connectivitycheck.android.com"; "clients3.google.com"; "msftconnecttest.com"}
:foreach host in=@DOL@cnaList do={
  :do {
    /ip hotspot walled-garden add dst-host=@DOL@host action=allow comment="Fast CNA Detection"
  } on-error={}
}

# 11. SCRIPT 1: voucher-activate (UNIVERSAL QUOTA & DURATION RESOLVER)
:do { /system script remove [find name="voucher-activate"] } on-error={}
/system script add name="voucher-activate" comment="Universal voucher activation, MAC binding and countdown" source={
:global hsUser;
:local u @DOL@hsUser;
:if ([:len @DOL@u] = 0 or @DOL@u = "admin" or @DOL@u = "default-trial") do={ :return "" };

:local uList [/ip hotspot user find name=@DOL@u];
:if ([:len @DOL@uList] = 0) do={ :return "" };
:local uObj [:pick @DOL@uList 0];

:local curComm [/ip hotspot user get @DOL@uObj comment];
:local isFirstLogin true;
:if ([:find @DOL@curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };

# Bind MAC address
:do {
  :local m "";
  :foreach a in=[/ip hotspot active find user=@DOL@u] do={
    :set m [/ip hotspot active get @DOL@a mac-address];
  };
  :if ([:len @DOL@m] > 0) do={
    /ip hotspot user set @DOL@uObj mac-address=@DOL@m;
  };
} on-error={};

:local parseTag do={
  :local tagVal "";
  :local idx [:find @DOL@comm @DOL@tag];
  :if (@DOL@idx >= 0) do={
    :local rest [:pick @DOL@comm (@DOL@idx + [:len @DOL@tag]) [:len @DOL@comm]];
    :local endIdx [:find @DOL@rest "]"];
    :if (@DOL@endIdx >= 0) do={ :set rest [:pick @DOL@rest 0 @DOL@endIdx] };
    :set tagVal @DOL@rest;
  };
  :return @DOL@tagVal;
};

:local setTag do={
  :local res @DOL@comm;
  :local idx [:find @DOL@comm @DOL@tag];
  :if (@DOL@idx >= 0) do={
    :local endIdx [:find @DOL@comm "]" @DOL@idx];
    :local pfx [:pick @DOL@comm 0 @DOL@idx];
    :local sfx "";
    :if (@DOL@endIdx >= 0) do={ :set sfx [:pick @DOL@comm (@DOL@endIdx + 1) [:len @DOL@comm]] };
    :set res (@DOL@pfx . @DOL@tag . @DOL@val . "]" . @DOL@sfx);
  } else={
    :set res (@DOL@comm . " " . @DOL@tag . @DOL@val . "]");
  };
  :return @DOL@res;
};

:local uProf [/ip hotspot user get @DOL@uObj profile];
:local curLim [/ip hotspot user get @DOL@uObj limit-bytes-total];
:local curUp [/ip hotspot user get @DOL@uObj limit-uptime];

# Resolve ORIG-LIMIT
:local origLim 0;
:local lStr [@DOL@parseTag comm=@DOL@curComm tag="[ORIG-LIMIT:"];
:if ([:len @DOL@lStr] > 0) do={ :set origLim [:tonum @DOL@lStr] };

:if (@DOL@origLim = 0) do={
  :local cUpper (@DOL@uProf . " " . @DOL@curComm);
  :if (@DOL@cUpper ~ "100GB" or @DOL@cUpper ~ "100G") do={ :set origLim 107374182400 };
  :if (@DOL@cUpper ~ "50GB" or @DOL@cUpper ~ "50G") do={ :set origLim 53687091200 };
  :if (@DOL@cUpper ~ "30GB" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "30G") do={ :set origLim 32212254720 };
  :if (@DOL@cUpper ~ "20GB" or @DOL@cUpper ~ "20G") do={ :set origLim 21474836480 };
  :if (@DOL@cUpper ~ "15GB" or @DOL@cUpper ~ "15G") do={ :set origLim 16106127360 };
  :if (@DOL@cUpper ~ "10GB" or @DOL@cUpper ~ "10G") do={ :set origLim 10737418240 };
  :if (@DOL@cUpper ~ "7GB" or @DOL@cUpper ~ "7G") do={ :set origLim 7516192768 };
  :if (@DOL@cUpper ~ "5GB" or @DOL@cUpper ~ "5G") do={ :set origLim 5368709120 };
  :if (@DOL@cUpper ~ "4GB" or @DOL@cUpper ~ "4G") do={ :set origLim 4294967296 };
  :if (@DOL@cUpper ~ "3GB" or @DOL@cUpper ~ "3G") do={ :set origLim 3221225472 };
  :if (@DOL@cUpper ~ "2GB" or @DOL@cUpper ~ "2G") do={ :set origLim 2147483648 };
  :if (@DOL@cUpper ~ "1.5GB" or @DOL@cUpper ~ "1.7GB" or @DOL@cUpper ~ "1700MB" or @DOL@uProf = "1000Ks") do={ :set origLim 1782579200 };
  :if (@DOL@cUpper ~ "1GB" or @DOL@cUpper ~ "1G") do={ :set origLim 1073741824 };
  :if (@DOL@cUpper ~ "750MB" or @DOL@uProf = "500Ks") do={ :set origLim 786432000 };
  :if (@DOL@cUpper ~ "500MB" or @DOL@cUpper ~ "500M") do={ :set origLim 524288000 };
  :if (@DOL@cUpper ~ "250MB" or @DOL@cUpper ~ "250M") do={ :set origLim 262144000 };
  :if (@DOL@cUpper ~ "100MB" or @DOL@cUpper ~ "100M") do={ :set origLim 104857600 };
  :if (@DOL@cUpper ~ "50MB" or @DOL@cUpper ~ "50M" or @DOL@cUpper ~ "test") do={ :set origLim 52428800 };
  :if (@DOL@origLim = 0 and @DOL@curLim > 0) do={ :set origLim @DOL@curLim };
};

:if (@DOL@origLim > 0) do={
  :set curComm [@DOL@setTag comm=@DOL@curComm tag="[ORIG-LIMIT:" val=[:tostr @DOL@origLim]];
  :if (@DOL@curLim = 0 or @DOL@curLim > (@DOL@origLim * 12 / 10)) do={
    /ip hotspot user set @DOL@uObj limit-bytes-total=@DOL@origLim;
  };
};

# Resolve ORIG-UP
:local origUp [:totime "0s"];
:local uStr [@DOL@parseTag comm=@DOL@curComm tag="[ORIG-UP:"];
:if ([:len @DOL@uStr] > 0) do={ :set origUp [:totime @DOL@uStr] };

:if (@DOL@origUp = [:totime "0s"]) do={
  :local vIdx [:find @DOL@curComm "V:"];
  :if (@DOL@vIdx >= 0) do={
    :local rest [:pick @DOL@curComm (@DOL@vIdx + 2) [:len @DOL@curComm]];
    :local spIdx [:find @DOL@rest " "];
    :if (@DOL@spIdx > 0) do={ :set rest [:pick @DOL@rest 0 @DOL@spIdx] };
    :local brIdx [:find @DOL@rest "]"];
    :if (@DOL@brIdx > 0) do={ :set rest [:pick @DOL@rest 0 @DOL@brIdx] };
    :if ([:len @DOL@rest] > 0) do={
      :do { :set origUp [:totime @DOL@rest] } on-error={};
    };
  };
};

:if (@DOL@origUp = [:totime "0s"]) do={
  :local cUpper (@DOL@uProf . " " . @DOL@curComm);
  :if (@DOL@cUpper ~ "30D" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "Month") do={ :set origUp [:totime "30d"] };
  :if (@DOL@cUpper ~ "14D" or @DOL@cUpper ~ "2W") do={ :set origUp [:totime "14d"] };
  :if (@DOL@cUpper ~ "7D" or @DOL@cUpper ~ "1W") do={ :set origUp [:totime "7d"] };
  :if (@DOL@cUpper ~ "3D") do={ :set origUp [:totime "3d"] };
  :if (@DOL@cUpper ~ "2D") do={ :set origUp [:totime "2d"] };
  :if (@DOL@cUpper ~ "1D" or @DOL@cUpper ~ "24H" or @DOL@uProf = "5GB" or @DOL@uProf = "2GB" or @DOL@uProf = "1000Ks" or @DOL@uProf = "500Ks") do={ :set origUp [:totime "1d"] };
  :if (@DOL@cUpper ~ "12H") do={ :set origUp [:totime "12h"] };
  :if (@DOL@cUpper ~ "8H") do={ :set origUp [:totime "8h"] };
  :if (@DOL@cUpper ~ "6H") do={ :set origUp [:totime "6h"] };
  :if (@DOL@cUpper ~ "4H") do={ :set origUp [:totime "4h"] };
  :if (@DOL@cUpper ~ "3H") do={ :set origUp [:totime "3h"] };
  :if (@DOL@cUpper ~ "2H" or @DOL@uProf = "2Hour") do={ :set origUp [:totime "2h"] };
  :if (@DOL@cUpper ~ "1H") do={ :set origUp [:totime "1h"] };
  :if (@DOL@cUpper ~ "45M") do={ :set origUp [:totime "45m"] };
  :if (@DOL@cUpper ~ "30M") do={ :set origUp [:totime "30m"] };
  :if (@DOL@cUpper ~ "15M") do={ :set origUp [:totime "15m"] };
  :if (@DOL@cUpper ~ "3M" or @DOL@uProf = "test") do={ :set origUp [:totime "3m"] };
  :if (@DOL@origUp = [:totime "0s"] and @DOL@curUp != "00:00:00" and @DOL@curUp != "0s" and [:len @DOL@curUp] > 0) do={ :set origUp [:totime @DOL@curUp] };
};

:if (@DOL@origUp > [:totime "0s"]) do={
  :set curComm [@DOL@setTag comm=@DOL@curComm tag="[ORIG-UP:" val=[:tostr @DOL@origUp]];
  :if (@DOL@curUp = "00:00:00" or @DOL@curUp = "0s" or [:len @DOL@curUp] = 0 or @DOL@curUp > (@DOL@origUp * 12 / 10)) do={
    /ip hotspot user set @DOL@uObj limit-uptime=@DOL@origUp;
  };
};

:local cDate [/system clock get date];
:local cTime [/system clock get time];

:if (@DOL@isFirstLogin) do={
  :set curComm [@DOL@setTag comm=@DOL@curComm tag="[ACT:" val=([:tostr @DOL@cDate] . " " . [:tostr @DOL@cTime])];
  /ip hotspot user set @DOL@uObj comment=@DOL@curComm;
  :log info ("Hotspot: Voucher " . @DOL@u . " activated! (Quota: " . [:tostr @DOL@origLim] . ", Up: " . [:tostr @DOL@origUp] . ")");

  :if (@DOL@origUp > [:totime "0s"]) do={
    :do {
      /system scheduler add name=@DOL@u start-date=@DOL@cDate start-time=@DOL@cTime interval=@DOL@origUp \
        on-event=("/ip hotspot active remove [find user=\"" . @DOL@u . "\"]; /ip hotspot user remove [find name=\"" . @DOL@u . "\"]; /ip hotspot cookie remove [find user=\"" . @DOL@u . "\"]; /system scheduler remove [find name=\"" . @DOL@u . "\"]") \
        comment=("Voucher continuous timer: " . [:tostr @DOL@origUp] . " from " . [:tostr @DOL@cDate] . " " . [:tostr @DOL@cTime]);
    } on-error={
      :do {
        /system scheduler add name=@DOL@u start-time=startup interval=@DOL@origUp \
          on-event=("/ip hotspot active remove [find user=\"" . @DOL@u . "\"]; /ip hotspot user remove [find name=\"" . @DOL@u . "\"]; /ip hotspot cookie remove [find user=\"" . @DOL@u . "\"]; /system scheduler remove [find name=\"" . @DOL@u . "\"]") \
          comment=("Voucher continuous timer: " . [:tostr @DOL@origUp] . " (fallback)");
      } on-error={};
    };
  };
} else={
  /ip hotspot user set @DOL@uObj comment=@DOL@curComm;
};
}

# 12. SCRIPT 2: hs-quota-save (CONTINUOUS 5-SECOND SYNC & EXACT CUTOFF)
:do { /system script remove [find name="hs-quota-save"] } on-error={}
/system script add name="hs-quota-save" comment="Persists user data usage and continuous time every 5s" source={
  :local parseTag do={
    :local tagVal "";
    :local idx [:find @DOL@comm @DOL@tag];
    :if (@DOL@idx >= 0) do={
      :local rest [:pick @DOL@comm (@DOL@idx + [:len @DOL@tag]) [:len @DOL@comm]];
      :local endIdx [:find @DOL@rest "]"];
      :if (@DOL@endIdx >= 0) do={ :set rest [:pick @DOL@rest 0 @DOL@endIdx] };
      :set tagVal @DOL@rest;
    };
    :return @DOL@tagVal;
  };

  :local setTag do={
    :local res @DOL@comm;
    :local idx [:find @DOL@comm @DOL@tag];
    :if (@DOL@idx >= 0) do={
      :local endIdx [:find @DOL@comm "]" @DOL@idx];
      :local pfx [:pick @DOL@comm 0 @DOL@idx];
      :local sfx "";
      :if (@DOL@endIdx >= 0) do={ :set sfx [:pick @DOL@comm (@DOL@endIdx + 1) [:len @DOL@comm]] };
      :set res (@DOL@pfx . @DOL@tag . @DOL@val . "]" . @DOL@sfx);
    } else={
      :set res (@DOL@comm . " " . @DOL@tag . @DOL@val . "]");
    };
    :return @DOL@res;
  };

  :local resolveOrigLim do={
    :local limStr [@DOL@parseTag comm=@DOL@comm tag="[ORIG-LIMIT:"];
    :if ([:len @DOL@limStr] > 0) do={
      :local lNum [:tonum @DOL@limStr];
      :if (@DOL@lNum > 0) do={ :return @DOL@lNum };
    };
    :local cUpper (@DOL@prof . " " . @DOL@comm);
    :if (@DOL@cUpper ~ "100GB" or @DOL@cUpper ~ "100G") do={ :return 107374182400 };
    :if (@DOL@cUpper ~ "50GB" or @DOL@cUpper ~ "50G") do={ :return 53687091200 };
    :if (@DOL@cUpper ~ "30GB" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "30G") do={ :return 32212254720 };
    :if (@DOL@cUpper ~ "20GB" or @DOL@cUpper ~ "20G") do={ :return 21474836480 };
    :if (@DOL@cUpper ~ "15GB" or @DOL@cUpper ~ "15G") do={ :return 16106127360 };
    :if (@DOL@cUpper ~ "10GB" or @DOL@cUpper ~ "10G") do={ :return 10737418240 };
    :if (@DOL@cUpper ~ "7GB" or @DOL@cUpper ~ "7G") do={ :return 7516192768 };
    :if (@DOL@cUpper ~ "5GB" or @DOL@cUpper ~ "5G") do={ :return 5368709120 };
    :if (@DOL@cUpper ~ "4GB" or @DOL@cUpper ~ "4G") do={ :return 4294967296 };
    :if (@DOL@cUpper ~ "3GB" or @DOL@cUpper ~ "3G") do={ :return 3221225472 };
    :if (@DOL@cUpper ~ "2GB" or @DOL@cUpper ~ "2G") do={ :return 2147483648 };
    :if (@DOL@cUpper ~ "1.5GB" or @DOL@cUpper ~ "1.7GB" or @DOL@cUpper ~ "1700MB" or @DOL@prof = "1000Ks") do={ :return 1782579200 };
    :if (@DOL@cUpper ~ "1GB" or @DOL@cUpper ~ "1G") do={ :return 1073741824 };
    :if (@DOL@cUpper ~ "750MB" or @DOL@prof = "500Ks") do={ :return 786432000 };
    :if (@DOL@cUpper ~ "500MB" or @DOL@cUpper ~ "500M") do={ :return 524288000 };
    :if (@DOL@cUpper ~ "250MB" or @DOL@cUpper ~ "250M") do={ :return 262144000 };
    :if (@DOL@cUpper ~ "100MB" or @DOL@cUpper ~ "100M") do={ :return 104857600 };
    :if (@DOL@cUpper ~ "50MB" or @DOL@cUpper ~ "50M" or @DOL@cUpper ~ "test") do={ :return 52428800 };
    :return @DOL@curLim;
  };

  :local resolveOrigUp do={
    :local upStr [@DOL@parseTag comm=@DOL@comm tag="[ORIG-UP:"];
    :if ([:len @DOL@upStr] > 0) do={ :return [:totime @DOL@upStr] };
    :local cUpper (@DOL@prof . " " . @DOL@comm);
    :if (@DOL@cUpper ~ "30D" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "Month") do={ :return [:totime "30d"] };
    :if (@DOL@cUpper ~ "14D" or @DOL@cUpper ~ "2W") do={ :return [:totime "14d"] };
    :if (@DOL@cUpper ~ "7D" or @DOL@cUpper ~ "1W") do={ :return [:totime "7d"] };
    :if (@DOL@cUpper ~ "3D") do={ :return [:totime "3d"] };
    :if (@DOL@cUpper ~ "2D") do={ :return [:totime "2d"] };
    :if (@DOL@cUpper ~ "1D" or @DOL@cUpper ~ "24H" or @DOL@prof = "5GB" or @DOL@prof = "2GB" or @DOL@prof = "1000Ks" or @DOL@prof = "500Ks") do={ :return [:totime "1d"] };
    :if (@DOL@cUpper ~ "12H") do={ :return [:totime "12h"] };
    :if (@DOL@cUpper ~ "8H") do={ :return [:totime "8h"] };
    :if (@DOL@cUpper ~ "6H") do={ :return [:totime "6h"] };
    :if (@DOL@cUpper ~ "4H") do={ :return [:totime "4h"] };
    :if (@DOL@cUpper ~ "3H") do={ :return [:totime "3h"] };
    :if (@DOL@cUpper ~ "2H" or @DOL@prof = "2Hour") do={ :return [:totime "2h"] };
    :if (@DOL@cUpper ~ "1H") do={ :return [:totime "1h"] };
    :if (@DOL@cUpper ~ "45M") do={ :return [:totime "45m"] };
    :if (@DOL@cUpper ~ "30M") do={ :return [:totime "30m"] };
    :if (@DOL@cUpper ~ "15M") do={ :return [:totime "15m"] };
    :if (@DOL@cUpper ~ "3M" or @DOL@prof = "test") do={ :return [:totime "3m"] };
    :if (@DOL@curUp != "00:00:00" and @DOL@curUp != "0s" and [:len @DOL@curUp] > 0) do={ :return [:totime @DOL@curUp] };
    :return [:totime "0s"];
  };

  :foreach a in=[/ip hotspot active find] do={
    :local uName [/ip hotspot active get @DOL@a user];
    :if (@DOL@uName != "admin" and @DOL@uName != "default-trial") do={
      :local uList [/ip hotspot user find name=@DOL@uName];
      :if ([:len @DOL@uList] > 0) do={
        :local u [:pick @DOL@uList 0];
        :local sIn [/ip hotspot active get @DOL@a bytes-in];
        :local sOut [/ip hotspot active get @DOL@a bytes-out];
        :local sUp [/ip hotspot active get @DOL@a uptime];
        :local sessionBytes (@DOL@sIn + @DOL@sOut);
        :local comm [/ip hotspot user get @DOL@u comment];
        :local uProf [/ip hotspot user get @DOL@u profile];
        :local curLim [/ip hotspot user get @DOL@u limit-bytes-total];
        :local curUp [/ip hotspot user get @DOL@u limit-uptime];

        :local baseBytes 0;
        :local bStr [@DOL@parseTag comm=@DOL@comm tag="[BASE:"];
        :if ([:len @DOL@bStr] > 0) do={ :set baseBytes [:tonum @DOL@bStr] };

        :local baseUp [:totime "0s"];
        :local buStr [@DOL@parseTag comm=@DOL@comm tag="[BASE-UP:"];
        :if ([:len @DOL@buStr] > 0) do={ :set baseUp [:totime @DOL@buStr] };

        :local totalUsed (@DOL@baseBytes + @DOL@sessionBytes);
        :local totalUp (@DOL@baseUp + @DOL@sUp);

        :local origLim [@DOL@resolveOrigLim parseTag=@DOL@parseTag prof=@DOL@uProf comm=@DOL@comm curLim=@DOL@curLim];
        :local origUp [@DOL@resolveOrigUp parseTag=@DOL@parseTag prof=@DOL@uProf comm=@DOL@comm curUp=@DOL@curUp];

        :if ([:find @DOL@comm "[ORIG-LIMIT:"] < 0 and @DOL@origLim > 0) do={
          :set comm [@DOL@setTag comm=@DOL@comm tag="[ORIG-LIMIT:" val=[:tostr @DOL@origLim]];
        };
        :if ([:find @DOL@comm "[ORIG-UP:"] < 0 and @DOL@origUp > [:totime "0s"]) do={
          :set comm [@DOL@setTag comm=@DOL@comm tag="[ORIG-UP:" val=[:tostr @DOL@origUp]];
        };

        :local curStoredUsed 0;
        :local uStr [@DOL@parseTag comm=@DOL@comm tag="[USED:"];
        :if ([:len @DOL@uStr] > 0) do={ :set curStoredUsed [:tonum @DOL@uStr] };

        :local curStoredUp [:totime "0s"];
        :local uuStr [@DOL@parseTag comm=@DOL@comm tag="[USED-UP:"];
        :if ([:len @DOL@uuStr] > 0) do={ :set curStoredUp [:totime @DOL@uuStr] };

        :local deltaBytes (@DOL@totalUsed - @DOL@curStoredUsed);
        :if (@DOL@deltaBytes < 0) do={ :set deltaBytes (-@DOL@deltaBytes) };
        :if ((@DOL@totalUsed > 0 and @DOL@deltaBytes >= 262144) or (@DOL@totalUsed != @DOL@curStoredUsed and @DOL@sessionBytes = 0) or (@DOL@totalUp != @DOL@curStoredUp and (@DOL@totalUp - @DOL@curStoredUp) >= [:totime "10s"])) do={
          :set comm [@DOL@setTag comm=@DOL@comm tag="[USED:" val=[:tostr @DOL@totalUsed]];
          :set comm [@DOL@setTag comm=@DOL@comm tag="[USED-UP:" val=[:tostr @DOL@totalUp]];
          /ip hotspot user set @DOL@u comment=@DOL@comm;
        };

        :local isDataExhausted false;
        :if (@DOL@origLim > 0 and @DOL@totalUsed >= @DOL@origLim) do={ :set isDataExhausted true };

        :local isTimeExhausted false;
        :if (@DOL@origUp > [:totime "0s"] and @DOL@totalUp >= @DOL@origUp) do={ :set isTimeExhausted true };

        :if (@DOL@isDataExhausted or @DOL@isTimeExhausted) do={
          :log info ("Hotspot: User " . @DOL@uName . " EXHAUSTED (Data: " . [:tostr @DOL@totalUsed] . "/" . [:tostr @DOL@origLim] . ", Time: " . [:tostr @DOL@totalUp] . "/" . [:tostr @DOL@origUp] . ")");
          :set comm [@DOL@setTag comm=@DOL@comm tag="[USED:" val=[:tostr @DOL@totalUsed]];
          :set comm [@DOL@setTag comm=@DOL@comm tag="[USED-UP:" val=[:tostr @DOL@totalUp]];
          /ip hotspot user set @DOL@u comment=@DOL@comm;
          /ip hotspot active remove @DOL@a;
          /ip hotspot cookie remove [find user=@DOL@uName];
          :if (@DOL@isDataExhausted) do={ /ip hotspot user set @DOL@u limit-bytes-total=1 };
          :if (@DOL@isTimeExhausted) do={ /ip hotspot user set @DOL@u limit-uptime=1s };
        };
      };
    };
  };
}

:do { /system scheduler remove [find name="hs-quota-saver"] } on-error={}
/system scheduler add name="hs-quota-saver" interval=5s start-time=startup \
  on-event="/system script run hs-quota-save" comment="Auto-persist user bytes every 5s"

# 13. SCRIPT 3: hs-on-logout (BASE SYNC ON DISCONNECT)
:do { /system script remove [find name="hs-on-logout"] } on-error={}
/system script add name="hs-on-logout" comment="Syncs base quota and continuous time on logout" source={
  :local uName @DOL@user;
  :if ([:len @DOL@uName] > 0 and @DOL@uName != "admin" and @DOL@uName != "default-trial") do={
    :local uList [/ip hotspot user find name=@DOL@uName];
    :if ([:len @DOL@uList] > 0) do={
      :local u [:pick @DOL@uList 0];
      :local comm [/ip hotspot user get @DOL@u comment];

      # 1. Sync BASE bytes from USED
      :local uIdx [:find @DOL@comm "[USED:"];
      :if (@DOL@uIdx >= 0) do={
        :local rest [:pick @DOL@comm (@DOL@uIdx + 6) [:len @DOL@comm]];
        :local endIdx [:find @DOL@rest "]"];
        :if (@DOL@endIdx >= 0) do={ :set rest [:pick @DOL@rest 0 @DOL@endIdx] };
        :local usedBytes [:tonum @DOL@rest];
        :if (@DOL@usedBytes > 0) do={
          :local bIdx [:find @DOL@comm "[BASE:"];
          :if (@DOL@bIdx >= 0) do={
            :local bEnd [:find @DOL@comm "]" @DOL@bIdx];
            :local prefix [:pick @DOL@comm 0 @DOL@bIdx];
            :local suffix "";
            :if (@DOL@bEnd >= 0) do={ :set suffix [:pick @DOL@comm (@DOL@bEnd + 1) [:len @DOL@comm]] };
            :set comm (@DOL@prefix . "[BASE:" . [:tostr @DOL@usedBytes] . "]" . @DOL@suffix);
          } else={
            :set comm (@DOL@comm . " [BASE:" . [:tostr @DOL@usedBytes] . "]");
          };
        };
      };

      # 2. Sync BASE-UP uptime from USED-UP
      :local uuIdx [:find @DOL@comm "[USED-UP:"];
      :if (@DOL@uuIdx >= 0) do={
        :local restUp [:pick @DOL@comm (@DOL@uuIdx + 9) [:len @DOL@comm]];
        :local endUp [:find @DOL@restUp "]"];
        :if (@DOL@endUp >= 0) do={ :set restUp [:pick @DOL@restUp 0 @DOL@endUp] };
        :if ([:len @DOL@restUp] > 0) do={
          :local buIdx [:find @DOL@comm "[BASE-UP:"];
          :if (@DOL@buIdx >= 0) do={
            :local buEnd [:find @DOL@comm "]" @DOL@buIdx];
            :local prefixUp [:pick @DOL@comm 0 @DOL@buIdx];
            :local suffixUp "";
            :if (@DOL@buEnd >= 0) do={ :set suffixUp [:pick @DOL@comm (@DOL@buEnd + 1) [:len @DOL@comm]] };
            :set comm (@DOL@prefixUp . "[BASE-UP:" . @DOL@restUp . "]" . @DOL@suffixUp);
          } else={
            :set comm (@DOL@comm . " [BASE-UP:" . @DOL@restUp . "]");
          };
        };
      };

      /ip hotspot user set @DOL@u comment=@DOL@comm;
    };
  };
}

# 14. SCRIPT 4: hs-quota-restore (BOOT RESTORATION & TIME DEDUCTION ACROSS REBOOTS)
:do { /system script remove [find name="hs-quota-restore"] } on-error={}
/system script add name="hs-quota-restore" comment="Restores quota and continuous time on router boot" source={
  :local parseTag do={
    :local tagVal "";
    :local idx [:find @DOL@comm @DOL@tag];
    :if (@DOL@idx >= 0) do={
      :local rest [:pick @DOL@comm (@DOL@idx + [:len @DOL@tag]) [:len @DOL@comm]];
      :local endIdx [:find @DOL@rest "]"];
      :if (@DOL@endIdx >= 0) do={ :set rest [:pick @DOL@rest 0 @DOL@endIdx] };
      :set tagVal @DOL@rest;
    };
    :return @DOL@tagVal;
  };

  :local setTag do={
    :local res @DOL@comm;
    :local idx [:find @DOL@comm @DOL@tag];
    :if (@DOL@idx >= 0) do={
      :local endIdx [:find @DOL@comm "]" @DOL@idx];
      :local pfx [:pick @DOL@comm 0 @DOL@idx];
      :local sfx "";
      :if (@DOL@endIdx >= 0) do={ :set sfx [:pick @DOL@comm (@DOL@endIdx + 1) [:len @DOL@comm]] };
      :set res (@DOL@pfx . @DOL@tag . @DOL@val . "]" . @DOL@sfx);
    } else={
      :set res (@DOL@comm . " " . @DOL@tag . @DOL@val . "]");
    };
    :return @DOL@res;
  };

  :local resolveOrigLim do={
    :local limStr [@DOL@parseTag comm=@DOL@comm tag="[ORIG-LIMIT:"];
    :if ([:len @DOL@limStr] > 0) do={
      :local lNum [:tonum @DOL@limStr];
      :if (@DOL@lNum > 0) do={ :return @DOL@lNum };
    };
    :local cUpper (@DOL@prof . " " . @DOL@comm);
    :if (@DOL@cUpper ~ "100GB" or @DOL@cUpper ~ "100G") do={ :return 107374182400 };
    :if (@DOL@cUpper ~ "50GB" or @DOL@cUpper ~ "50G") do={ :return 53687091200 };
    :if (@DOL@cUpper ~ "30GB" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "30G") do={ :return 32212254720 };
    :if (@DOL@cUpper ~ "20GB" or @DOL@cUpper ~ "20G") do={ :return 21474836480 };
    :if (@DOL@cUpper ~ "15GB" or @DOL@cUpper ~ "15G") do={ :return 16106127360 };
    :if (@DOL@cUpper ~ "10GB" or @DOL@cUpper ~ "10G") do={ :return 10737418240 };
    :if (@DOL@cUpper ~ "7GB" or @DOL@cUpper ~ "7G") do={ :return 7516192768 };
    :if (@DOL@cUpper ~ "5GB" or @DOL@cUpper ~ "5G") do={ :return 5368709120 };
    :if (@DOL@cUpper ~ "4GB" or @DOL@cUpper ~ "4G") do={ :return 4294967296 };
    :if (@DOL@cUpper ~ "3GB" or @DOL@cUpper ~ "3G") do={ :return 3221225472 };
    :if (@DOL@cUpper ~ "2GB" or @DOL@cUpper ~ "2G") do={ :return 2147483648 };
    :if (@DOL@cUpper ~ "1.5GB" or @DOL@cUpper ~ "1.7GB" or @DOL@cUpper ~ "1700MB" or @DOL@prof = "1000Ks") do={ :return 1782579200 };
    :if (@DOL@cUpper ~ "1GB" or @DOL@cUpper ~ "1G") do={ :return 1073741824 };
    :if (@DOL@cUpper ~ "750MB" or @DOL@prof = "500Ks") do={ :return 786432000 };
    :if (@DOL@cUpper ~ "500MB" or @DOL@cUpper ~ "500M") do={ :return 524288000 };
    :if (@DOL@cUpper ~ "250MB" or @DOL@cUpper ~ "250M") do={ :return 262144000 };
    :if (@DOL@cUpper ~ "100MB" or @DOL@cUpper ~ "100M") do={ :return 104857600 };
    :if (@DOL@cUpper ~ "50MB" or @DOL@cUpper ~ "50M" or @DOL@cUpper ~ "test") do={ :return 52428800 };
    :return @DOL@curLim;
  };

  :local resolveOrigUp do={
    :local upStr [@DOL@parseTag comm=@DOL@comm tag="[ORIG-UP:"];
    :if ([:len @DOL@upStr] > 0) do={ :return [:totime @DOL@upStr] };
    :local cUpper (@DOL@prof . " " . @DOL@comm);
    :if (@DOL@cUpper ~ "30D" or @DOL@cUpper ~ "30Day" or @DOL@cUpper ~ "Month") do={ :return [:totime "30d"] };
    :if (@DOL@cUpper ~ "14D" or @DOL@cUpper ~ "2W") do={ :return [:totime "14d"] };
    :if (@DOL@cUpper ~ "7D" or @DOL@cUpper ~ "1W") do={ :return [:totime "7d"] };
    :if (@DOL@cUpper ~ "3D") do={ :return [:totime "3d"] };
    :if (@DOL@cUpper ~ "2D") do={ :return [:totime "2d"] };
    :if (@DOL@cUpper ~ "1D" or @DOL@cUpper ~ "24H" or @DOL@prof = "5GB" or @DOL@prof = "2GB" or @DOL@prof = "1000Ks" or @DOL@prof = "500Ks") do={ :return [:totime "1d"] };
    :if (@DOL@cUpper ~ "12H") do={ :return [:totime "12h"] };
    :if (@DOL@cUpper ~ "8H") do={ :return [:totime "8h"] };
    :if (@DOL@cUpper ~ "6H") do={ :return [:totime "6h"] };
    :if (@DOL@cUpper ~ "4H") do={ :return [:totime "4h"] };
    :if (@DOL@cUpper ~ "3H") do={ :return [:totime "3h"] };
    :if (@DOL@cUpper ~ "2H" or @DOL@prof = "2Hour") do={ :return [:totime "2h"] };
    :if (@DOL@cUpper ~ "1H") do={ :return [:totime "1h"] };
    :if (@DOL@cUpper ~ "45M") do={ :return [:totime "45m"] };
    :if (@DOL@cUpper ~ "30M") do={ :return [:totime "30m"] };
    :if (@DOL@cUpper ~ "15M") do={ :return [:totime "15m"] };
    :if (@DOL@cUpper ~ "3M" or @DOL@prof = "test") do={ :return [:totime "3m"] };
    :if (@DOL@curUp != "00:00:00" and @DOL@curUp != "0s" and [:len @DOL@curUp] > 0) do={ :return [:totime @DOL@curUp] };
    :return [:totime "0s"];
  };

  # Clock check compatible with RouterOS v6 and v7
  :local dStr [/system clock get date];
  :local cYear 1970;
  :if ([:len @DOL@dStr] >= 10) do={
    :if ([:find @DOL@dStr "-"] >= 0) do={
      :set cYear [:tonum [:pick @DOL@dStr 0 4]];
    } else={
      :set cYear [:tonum [:pick @DOL@dStr 7 11]];
    };
  };
  :if (@DOL@cYear < 2025) do={ :delay 5s };

  :foreach u in=[/ip hotspot user find] do={
    :local uName [/ip hotspot user get @DOL@u name];
    :if (@DOL@uName != "admin" and @DOL@uName != "default-trial") do={
      :local comm [/ip hotspot user get @DOL@u comment];
      :local uProf [/ip hotspot user get @DOL@u profile];
      :local curLim [/ip hotspot user get @DOL@u limit-bytes-total];
      :local curUp [/ip hotspot user get @DOL@u limit-uptime];

      :local usedBytes 0;
      :local uStr [@DOL@parseTag comm=@DOL@comm tag="[USED:"];
      :if ([:len @DOL@uStr] > 0) do={ :set usedBytes [:tonum @DOL@uStr] };

      :local usedUp [:totime "0s"];
      :local uuStr [@DOL@parseTag comm=@DOL@comm tag="[USED-UP:"];
      :if ([:len @DOL@uuStr] > 0) do={ :set usedUp [:totime @DOL@uuStr] };

      :local origLim [@DOL@resolveOrigLim parseTag=@DOL@parseTag prof=@DOL@uProf comm=@DOL@comm curLim=@DOL@curLim];
      :local origUp [@DOL@resolveOrigUp parseTag=@DOL@parseTag prof=@DOL@uProf comm=@DOL@comm curUp=@DOL@curUp];

      :if ([:find @DOL@comm "[ORIG-LIMIT:"] < 0 and @DOL@origLim > 0) do={
        :set comm [@DOL@setTag comm=@DOL@comm tag="[ORIG-LIMIT:" val=[:tostr @DOL@origLim]];
      };
      :if ([:find @DOL@comm "[ORIG-UP:"] < 0 and @DOL@origUp > [:totime "0s"]) do={
        :set comm [@DOL@setTag comm=@DOL@comm tag="[ORIG-UP:" val=[:tostr @DOL@origUp]];
      };

      :if (@DOL@usedBytes > 0 or @DOL@usedUp > [:totime "0s"]) do={
        :if (@DOL@usedBytes > 0) do={
          :set comm [@DOL@setTag comm=@DOL@comm tag="[BASE:" val=[:tostr @DOL@usedBytes]];
        };
        :if (@DOL@usedUp > [:totime "0s"]) do={
          :set comm [@DOL@setTag comm=@DOL@comm tag="[BASE-UP:" val=[:tostr @DOL@usedUp]];
        };
        /ip hotspot user set @DOL@u comment=@DOL@comm;

        # Reset RAM counters so new boot session counts cleanly up to remaining allowance
        /ip hotspot user reset-counters @DOL@u;

        # Deduct consumed quota
        :if (@DOL@origLim > 0) do={
          :if (@DOL@usedBytes >= @DOL@origLim) do={
            /ip hotspot active remove [find user=@DOL@uName];
            /ip hotspot cookie remove [find user=@DOL@uName];
            /ip hotspot user set @DOL@u limit-bytes-total=1;
          } else={
            :local remLim (@DOL@origLim - @DOL@usedBytes);
            /ip hotspot user set @DOL@u limit-bytes-total=@DOL@remLim;
          };
        };

        # Deduct consumed uptime
        :if (@DOL@origUp > [:totime "0s"]) do={
          :if (@DOL@usedUp >= @DOL@origUp) do={
            /ip hotspot active remove [find user=@DOL@uName];
            /ip hotspot cookie remove [find user=@DOL@uName];
            /ip hotspot user set @DOL@u limit-uptime=1s;
          } else={
            :local remUp (@DOL@origUp - @DOL@usedUp);
            /ip hotspot user set @DOL@u limit-uptime=@DOL@remUp;
          };
        };

        # Check if user had a continuous validity timer scheduler that expired during shutdown
        :local sc [/system scheduler find name=@DOL@uName];
        :if ([:len @DOL@sc] > 0) do={
          :local nr [/system scheduler get @DOL@sc next-run];
          :if ([:len @DOL@nr] = 0 or @DOL@nr = "none" or @DOL@nr = "never") do={
            :log info ("Hotspot: User " . @DOL@uName . " validity timer expired during power outage");
            /ip hotspot active remove [find user=@DOL@uName];
            /ip hotspot cookie remove [find user=@DOL@uName];
            /ip hotspot user set @DOL@u limit-uptime=1s;
            /system scheduler remove @DOL@sc;
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
:local macFixScript ":local u @DOL@user; :local m @DOL@\"mac-address\"; :do { /ip hotspot active remove [find user=@DOL@u and mac-address!=@DOL@m]; /ip hotspot cookie remove [find user=@DOL@u and mac-address!=@DOL@m]; /ip hotspot user set [find name=@DOL@u] mac-address=@DOL@m } on-error={}; :global hsUser @DOL@user; :do { /system script run voucher-activate } on-error={}"
:local logoutHook "/system script run hs-quota-save; :delay 500ms; /system script run hs-on-logout"

:do {
  /ip hotspot user profile set [find name="default"] shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d keepalive-timeout=none idle-timeout=none on-login=@DOL@macFixScript on-logout=@DOL@logoutHook
} on-error={}
:foreach prof in=[/ip hotspot user profile find] do={
  :do {
    /ip hotspot user profile set @DOL@prof shared-users=1 add-mac-cookie=yes mac-cookie-timeout=30d keepalive-timeout=none idle-timeout=none on-login=@DOL@macFixScript on-logout=@DOL@logoutHook
  } on-error={}
}

# 16. API & SERVICES ENABLED FOR REMOTE ADMIN ACCESS
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
:put ("   SSID: __SAFE_SSID__ | Gateway IP: " . @DOL@gwIp . " (Direct IP Mode)")
:put ("   Capacity: __SAFE_CAP__ users on " . @DOL@netCidr)
:put ("   Admin User: admin | Admin Pass: __SAFE_PASS__")
:put "=========================================================="
"""
        var result = template
            .replace("__SAFE_SSID__", safeSsid)
            .replace("__SAFE_PASS__", safePass)
            .replace("__SAFE_CAP__", safeCap.toString())
            .replace("@DOL@", "$")

        if (!wireguardScript.isNullOrBlank()) {
            result += "\n\n# 17. CLOUD MANAGEMENT WIREGUARD VPN\n" + wireguardScript.trim() + "\n"
        }
        return result
    }
}
