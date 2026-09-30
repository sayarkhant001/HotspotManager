# ==============================================================================
#                      ALL GOOD WIFI - PROFILE CLEANUP SCRIPT
#                   Keeps ONLY 1GB_1H Profile, Removes All Others
# ==============================================================================
# Usage:
#   /import cleanup.rsc
# ==============================================================================

:put "============================================"
:put "  All Good WiFi - Profile Cleanup (1GB_1H)"
:put "============================================"

# -------------------------------------------------------
:put "[1/5] Creating or updating 1GB_1H profile..."
# -------------------------------------------------------
:do {
  /ip hotspot user profile add name="1GB_1H" rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h keepalive-timeout=2h idle-timeout=30m shared-users=1 \
    comment="1GB 1Hour"
  :put "  1GB_1H created (session-timeout=1h, 10Mbps, 1h mac-cookie)."
} on-error={
  /ip hotspot user profile set [find name="1GB_1H"] rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h keepalive-timeout=2h idle-timeout=30m shared-users=1 \
    comment="1GB 1Hour"
  :put "  1GB_1H already exists, updated."
}

# Update default profile with safe 1GB_1H matching values
:do {
  /ip hotspot user profile set [find name="default"] rate-limit="10M/10M" session-timeout=1h \
    mac-cookie-timeout=1h shared-users=1
} on-error={}

# -------------------------------------------------------
:put "[2/5] Migrating existing vouchers to 1GB_1H..."
# -------------------------------------------------------
:local migrated 0
:foreach uid in=[/ip hotspot user find] do={
  :local uname ""
  :do { :set uname [/ip hotspot user get $uid name] } on-error={}
  :if ($uname != "default-trial" and $uname != "admin") do={
    :local prof ""
    :do { :set prof [/ip hotspot user get $uid profile] } on-error={}
    :if ($prof != "1GB_1H") do={
      /ip hotspot user set $uid profile="1GB_1H"
      :set migrated ($migrated + 1)
    }
  }
}
:put ("  Migrated " . $migrated . " vouchers to 1GB_1H profile.")

# -------------------------------------------------------
:put "[3/5] Removing all other profiles..."
# -------------------------------------------------------
:foreach pid in=[/ip hotspot user profile find] do={
  :local pname [/ip hotspot user profile get $pid name]
  :if ($pname != "default" and $pname != "1GB_1H") do={
    :do {
      /ip hotspot user profile remove $pid
      :put ("  Removed old profile: " . $pname)
    } on-error={
      :put ("  Could not remove profile: " . $pname)
    }
  }
}

# -------------------------------------------------------
:put "[4/5] Normalizing passwords and 1GB byte limits (1,073,741,824 bytes)..."
# -------------------------------------------------------
:local fixedLimits 0
:local fixedPass 0
:foreach uid in=[/ip hotspot user find] do={
  :local uname [/ip hotspot user get $uid name]
  :if ($uname != "default-trial" and $uname != "admin") do={
    # Password sync
    :local pw [/ip hotspot user get $uid password]
    :if ([:len $pw] = 0) do={
      /ip hotspot user set $uid password=$uname
      :set fixedPass ($fixedPass + 1)
    }
    # 1GB Data limit
    :local lim [/ip hotspot user get $uid limit-bytes-total]
    :if ($lim != 1073741824) do={
      /ip hotspot user set $uid limit-bytes-total=1073741824
      :set fixedLimits ($fixedLimits + 1)
    }
  }
}
:local totalUsers [:len [/ip hotspot user find profile="1GB_1H"]]
:put ("  1GB_1H vouchers: " . $totalUsers . " total.")
:put ("  Byte limits set to 1GB: " . $fixedLimits . ", Passwords synchronized: " . $fixedPass)

# -------------------------------------------------------
:put "[5/5] Current User Profiles in RouterOS:"
# -------------------------------------------------------
/ip hotspot user profile print

:put ""
:put "============================================"
:put ("  CLEANUP COMPLETE! Only 1GB_1H profile active (" . $totalUsers . " vouchers).")
:put "============================================"
