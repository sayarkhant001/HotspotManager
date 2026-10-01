# ==============================================================================
#            MIKROTIK IN-ROUTER CAPTIVE PORTAL BOOTSTRAP FETCHER
# ==============================================================================
# Use this when the router already has internet connectivity on ether1.
# Run directly in MikroTik Terminal:
#
#   /tool fetch url="https://raw.githubusercontent.com/sayarkhant001/mikrotikConfiguration/main/bootstrap.rsc" dst-path=bootstrap.rsc; /import file-name=bootstrap.rsc
# ==============================================================================

:global siteName
:if ([:len $siteName] = 0) do={ :set siteName "YadanarTun_WiFi" }

:global ipCapacity
:if ([:len $ipCapacity] = 0) do={ :set ipCapacity 250 }

:local repoBase "https://raw.githubusercontent.com/sayarkhant001/mikrotikConfiguration/main"

# 1. Storage Prefix (flash/ vs root)
:local prefix ""
:if ([:len [/file find name="flash"]] > 0) do={ :set prefix "flash/" }

:put ("=== Fetching Assets from GitHub for Site: " . $siteName . " ===")

# 2. Check Device-Mode for Fetch capability (RouterOS v7)
:do {
  :local dmFetch [/system device-mode get fetch]
  :if ($dmFetch = false or $dmFetch = "no") do={
    :put "CRITICAL: 'fetch' is disabled by device-mode!"
    :put "Running: /system/device-mode/update hotspot=yes scheduler=yes fetch=yes"
    /system device-mode update hotspot=yes scheduler=yes fetch=yes
    :error "PLEASE POWER-CYCLE THE ROUTER WITHIN 3 MINUTES, THEN RUN THIS COMMAND AGAIN."
  }
} on-error={}

# 3. Download Captive Portal Archive
:put ">>> Downloading captive portal package..."
/tool fetch url=($repoBase . "/sites/" . $siteName . "/hotspot.zip") dst-path=($prefix . "hotspot.zip")

# 4. Unzip Portal Archive (RouterOS v7)
:do {
  :put ">>> Extracting portal archive..."
  /file/unzip file-name=($prefix . "hotspot.zip") to-dir=($prefix . "hotspot")
} on-error={
  :put "Note: /file/unzip not supported on older ROS versions or already unpacked."
}

# 5. Fetch Universal Setup Script
:put ">>> Downloading universal setup script..."
/tool fetch url=($repoBase . "/core/universal_setup.rsc") dst-path=($prefix . "universal_setup.rsc")

# 6. Execute Setup
:put ">>> Executing setup script..."
/import file-name=($prefix . "universal_setup.rsc")
