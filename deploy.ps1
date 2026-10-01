# ==============================================================================
#            MIKROTIK INTERACTIVE ONE-COMMAND SITE DEPLOYER
#               Universal Captive Portal Provisioning Tool
# ==============================================================================
param(
    [string]$SiteName = "",
    [string]$Ssid = "",
    [string]$DnsName = "",
    [int]$UserCapacity = 0,
    [string]$RouterIP = "",
    [string]$RouterUser = "admin",
    [string]$RouterPass = "",
    [switch]$AutoExecute,
    [switch]$SkipDeviceModeCheck
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Show-Banner {
    Clear-Host
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host "     MIKROTIK UNIVERSAL CAPTIVE PORTAL SITE DEPLOYER             " -ForegroundColor Yellow
    Write-Host "      Supports ROS v6/v7 | All Routerboard Models | 10-10,000 IPs" -ForegroundColor DarkCyan
    Write-Host "==================================================================" -ForegroundColor Cyan
    Write-Host ""
}

# ── ROUTEROS API CLIENT HELPER (Port 8728) ────────────────────
function Send-RosApiSentence($stream, [string[]]$words) {
    foreach ($w in $words) {
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($w)
        $len = $bytes.Length
        if ($len -lt 0x80) {
            $stream.WriteByte([byte]$len)
        } elseif ($len -lt 0x4000) {
            $len = $len -bor 0x8000
            $stream.WriteByte([byte](($len -shr 8) -band 0xFF))
            $stream.WriteByte([byte]($len -band 0xFF))
        }
        $stream.Write($bytes, 0, $bytes.Length)
    }
    $stream.WriteByte(0)
}

function Read-RosApiSentences($stream) {
    $sentences = @()
    while ($true) {
        $words = @()
        while ($true) {
            $b = $stream.ReadByte()
            if ($b -le 0) { break }
            $len = 0
            if (($b -band 0x80) -eq 0) {
                $len = $b
            } elseif (($b -band 0xC0) -eq 0x80) {
                $b2 = $stream.ReadByte()
                $len = (($b -band 0x3F) -shl 8) -bor $b2
            }
            $buf = New-Object byte[] $len
            $read = 0
            while ($read -lt $len) {
                $read += $stream.Read($buf, $read, $len - $read)
            }
            $words += [System.Text.Encoding]::ASCII.GetString($buf, 0, $len)
        }
        $sentences += ,$words
        if ($words -contains "!done" -or $words -contains "!trap") { break }
    }
    return $sentences
}

# ── SUBNET CALCULATOR (10 - 10,000 IPs) ───────────────────────
function Get-SubnetConfig([int]$count) {
    if ($count -lt 10) { $count = 10 }
    if ($count -gt 10000) { $count = 10000 }
    
    if ($count -le 240) {
        return @{
            Prefix    = 24
            Netmask   = "255.255.255.0"
            Network   = "10.10.10.0/24"
            NetAddr   = "10.10.10.0"
            Gateway   = "10.10.10.1"
            PoolStart = "10.10.10.10"
            PoolEnd   = "10.10.10.254"
            LeaseTime = "2h"
            Capacity  = 245
        }
    } elseif ($count -le 500) {
        return @{
            Prefix    = 23
            Netmask   = "255.255.254.0"
            Network   = "10.10.10.0/23"
            NetAddr   = "10.10.10.0"
            Gateway   = "10.10.10.1"
            PoolStart = "10.10.10.10"
            PoolEnd   = "10.10.11.250"
            LeaseTime = "2h"
            Capacity  = 501
        }
    } elseif ($count -le 1000) {
        return @{
            Prefix    = 22
            Netmask   = "255.255.252.0"
            Network   = "10.10.8.0/22"
            NetAddr   = "10.10.8.0"
            Gateway   = "10.10.8.1"
            PoolStart = "10.10.8.10"
            PoolEnd   = "10.10.11.250"
            LeaseTime = "1h"
            Capacity  = 1017
        }
    } elseif ($count -le 2000) {
        return @{
            Prefix    = 21
            Netmask   = "255.255.248.0"
            Network   = "10.10.0.0/21"
            NetAddr   = "10.10.0.0"
            Gateway   = "10.10.0.1"
            PoolStart = "10.10.0.10"
            PoolEnd   = "10.10.7.250"
            LeaseTime = "1h"
            Capacity  = 2033
        }
    } elseif ($count -le 4000) {
        return @{
            Prefix    = 20
            Netmask   = "255.255.240.0"
            Network   = "10.10.0.0/20"
            NetAddr   = "10.10.0.0"
            Gateway   = "10.10.0.1"
            PoolStart = "10.10.0.10"
            PoolEnd   = "10.10.15.250"
            LeaseTime = "45m"
            Capacity  = 4081
        }
    } elseif ($count -le 8000) {
        return @{
            Prefix    = 19
            Netmask   = "255.255.224.0"
            Network   = "10.10.0.0/19"
            NetAddr   = "10.10.0.0"
            Gateway   = "10.10.0.1"
            PoolStart = "10.10.0.10"
            PoolEnd   = "10.10.31.250"
            LeaseTime = "30m"
            Capacity  = 8177
        }
    } else {
        return @{
            Prefix    = 18
            Netmask   = "255.255.192.0"
            Network   = "10.10.0.0/18"
            NetAddr   = "10.10.0.0"
            Gateway   = "10.10.0.1"
            PoolStart = "10.10.0.10"
            PoolEnd   = "10.10.63.250"
            LeaseTime = "30m"
            Capacity  = 16369
        }
    }
}

Show-Banner

# ── 1. GATHER INTERACTIVE REQUIREMENTS ────────────────────────
if ([string]::IsNullOrWhiteSpace($SiteName)) {
    Write-Host "[STEP 1/4] Site & Portal Selection" -ForegroundColor White
    $SiteName = Read-Host "  Enter Site Name [Press ENTER for 'YadanarTun_WiFi']"
    if ([string]::IsNullOrWhiteSpace($SiteName)) { $SiteName = "YadanarTun_WiFi" }
}

if ([string]::IsNullOrWhiteSpace($Ssid)) {
    Write-Host "`n[STEP 2/4] Wireless & DNS Configuration" -ForegroundColor White
    $Ssid = Read-Host "  Enter Wi-Fi SSID to broadcast [Press ENTER for '$SiteName']"
    if ([string]::IsNullOrWhiteSpace($Ssid)) { $Ssid = $SiteName }
}

if ([string]::IsNullOrWhiteSpace($DnsName)) {
    $DefaultDns = "$($SiteName.ToLower().Replace('_','')).wifi"
    $DnsName = Read-Host "  Enter Captive Portal DNS name [Press ENTER for '$DefaultDns']"
    if ([string]::IsNullOrWhiteSpace($DnsName)) { $DnsName = $DefaultDns }
}

if ($UserCapacity -le 0) {
    Write-Host "`n[STEP 3/4] Capacity & Subnet Calculation (10 - 10,000 IPs)" -ForegroundColor White
    $IpInput = Read-Host "  Enter number of concurrent users/IPs needed (10-10000) [Press ENTER for 250]"
    $ReqIps = 250
    if (-not [string]::IsNullOrWhiteSpace($IpInput)) {
        if (-not [int]::TryParse($IpInput, [ref]$ReqIps) -or $ReqIps -lt 10 -or $ReqIps -gt 10000) {
            Write-Host "  ⚠️ Invalid number. Clamping to range 10-10000..." -ForegroundColor Yellow
            if ($ReqIps -lt 10) { $ReqIps = 10 }
            if ($ReqIps -gt 10000) { $ReqIps = 10000 }
        }
    }
} else {
    $ReqIps = $UserCapacity
}

$Subnet = Get-SubnetConfig $ReqIps

Write-Host "`n  Calculated Subnet Architecture:" -ForegroundColor Green
Write-Host "  -------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "  Subnet Prefix : /$($Subnet.Prefix) (Netmask: $($Subnet.Netmask))" -ForegroundColor Cyan
Write-Host "  Network CIDR  : $($Subnet.Network)" -ForegroundColor Cyan
Write-Host "  Gateway IP    : $($Subnet.Gateway)" -ForegroundColor Cyan
Write-Host "  DHCP Pool     : $($Subnet.PoolStart) - $($Subnet.PoolEnd)" -ForegroundColor Cyan
Write-Host "  Total Usable  : $($Subnet.Capacity) concurrent client IPs" -ForegroundColor Cyan
Write-Host "  Lease Time    : $($Subnet.LeaseTime) (auto-optimized for density)" -ForegroundColor Cyan
Write-Host "  -------------------------------------------------------" -ForegroundColor DarkGray

if ([string]::IsNullOrWhiteSpace($RouterIP)) {
    Write-Host "`n[STEP 4/4] MikroTik Connection Details" -ForegroundColor White
    $RouterIP = Read-Host "  Enter Router IP [Press ENTER for factory default 192.168.88.1]"
    if ([string]::IsNullOrWhiteSpace($RouterIP)) { $RouterIP = "192.168.88.1" }
}

if ([string]::IsNullOrWhiteSpace($RouterPass)) {
    $InputPass = Read-Host "  Enter Router Admin Password [Press ENTER for default 'Khant1234@']"
    if (-not [string]::IsNullOrWhiteSpace($InputPass)) {
        $RouterPass = $InputPass
    } else {
        $RouterPass = "Khant1234@"
    }
}

# ── 2. DOWNLOAD OR LOAD LOCAL ASSETS ──────────────────────────
$RepoOwner = "sayarkhant001"
$RepoName  = "mikrotikConfiguration"
$Branch    = "main"
$BaseUrl   = "https://raw.githubusercontent.com/$RepoOwner/$RepoName/$Branch"

$WorkDir = "$env:TEMP\mikrotik_deploy_$SiteName"
if (Test-Path $WorkDir) { Remove-Item $WorkDir -Recurse -Force }
New-Item -ItemType Directory -Path $WorkDir | Out-Null

$ZipFile = "$WorkDir\hotspot.zip"
$UniversalRscFile = "$WorkDir\universal_setup.rsc"

# Safe search for local assets first (offline fallback)
$searchDirs = @()
if ($PSScriptRoot) { $searchDirs += $PSScriptRoot }
if ($MyInvocation.MyCommand -and $MyInvocation.MyCommand.Path) {
    try { $searchDirs += (Split-Path -Parent $MyInvocation.MyCommand.Path) } catch {}
}
$searchDirs += (Get-Location).Path
$searchDirs += "c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager\mikrotikConfiguration"
$searchDirs += "c:\Users\localhost\Downloads\Portals\hotspot\HotspotManager"

$assetFound = $false
foreach ($dir in $searchDirs) {
    if (-not [string]::IsNullOrWhiteSpace($dir) -and (Test-Path $dir)) {
        $chkZip = Join-Path $dir "sites\$SiteName\hotspot.zip"
        $chkRsc = Join-Path $dir "core\universal_setup.rsc"
        if ((Test-Path $chkZip) -and (Test-Path $chkRsc)) {
            Copy-Item $chkZip $ZipFile -Force
            Copy-Item $chkRsc $UniversalRscFile -Force
            $assetFound = $true
            Write-Host "    Found local assets at: $dir" -ForegroundColor Green
            break
        }
    }
}

if (-not $assetFound) {
    # Try downloading from GitHub
    $ZipUrl = "$BaseUrl/sites/$SiteName/hotspot.zip"
    $UniversalRscUrl = "$BaseUrl/core/universal_setup.rsc"
    try {
        Write-Host "    Downloading portal from GitHub: $ZipUrl" -ForegroundColor Gray
        Invoke-WebRequest -Uri $ZipUrl -OutFile $ZipFile -UseBasicParsing
        Invoke-WebRequest -Uri $UniversalRscUrl -OutFile $UniversalRscFile -UseBasicParsing
        $assetFound = $true
        Write-Host "    GitHub assets downloaded successfully." -ForegroundColor Green
    } catch {
        # Fallback to HotspotManager repo
        $fallbackBase = "https://raw.githubusercontent.com/$RepoOwner/HotspotManager/$Branch/mikrotikConfiguration"
        try {
            Write-Host "    Retrying from HotspotManager repository..." -ForegroundColor Gray
            Invoke-WebRequest -Uri "$fallbackBase/sites/$SiteName/hotspot.zip" -OutFile $ZipFile -UseBasicParsing
            Invoke-WebRequest -Uri "$fallbackBase/core/universal_setup.rsc" -OutFile $UniversalRscFile -UseBasicParsing
            $assetFound = $true
            Write-Host "    HotspotManager assets downloaded successfully." -ForegroundColor Green
        } catch {}
    }
}

if (-not (Test-Path $ZipFile) -or -not (Test-Path $UniversalRscFile)) {
    Write-Host "`n❌ Error: Could not locate captive portal assets for '$SiteName'!" -ForegroundColor Red
    Write-Host "   Please ensure 'sites/$SiteName/hotspot.zip' is pushed to GitHub: $RepoOwner/$RepoName" -ForegroundColor Yellow
    Write-Host "   Or run deploy.ps1 from the 'mikrotikConfiguration' directory.`n" -ForegroundColor Yellow
    return
}

# Extract portal archive
Write-Host ">>> Extracting captive portal files..." -ForegroundColor Gray
$ExtractPath = "$WorkDir\hotspot"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($ZipFile, $ExtractPath)

# ── 3. PREPARE CUSTOMIZED SETUP.RSC ───────────────────────────
$CustomSetupRsc = "$WorkDir\setup.rsc"
$HeaderConfig = @"
# --- AUTOMATICALLY INJECTED PARAMETERS BY DEPLOYER ---
:global siteName "$SiteName"
:global wifiSsid "$Ssid"
:global dnsName "$DnsName"
:global adminPass "$RouterPass"
:global ipCapacity $($Subnet.Capacity)

"@

$CoreScript = Get-Content -Path $UniversalRscFile -Raw
$FinalScript = $HeaderConfig + "`n" + $CoreScript
Set-Content -Path $CustomSetupRsc -Value $FinalScript -Encoding ASCII

# ── 4. DETECT ROUTER HARDWARE & DEVICE-MODE VIA API ───────────
Write-Host "`n>>> Inspecting MikroTik hardware & RouterOS version ($RouterIP)..." -ForegroundColor Cyan
$apiPort = 8728
$apiConnected = $false
$hwModel = "Unknown"
$rosVersion = "Unknown"
$hasFlash = $false
$apiClient = $null
$apiStream = $null

try {
    $apiClient = New-Object System.Net.Sockets.TcpClient
    $iar = $apiClient.BeginConnect($RouterIP, $apiPort, $null, $null)
    $apiOpen = $iar.AsyncWaitHandle.WaitOne(2000, $false)
    if ($apiOpen) {
        $apiClient.EndConnect($iar)
        $apiStream = $apiClient.GetStream()
        
        Send-RosApiSentence $apiStream @("/login", "=name=$RouterUser", "=password=$RouterPass")
        $loginRes = Read-RosApiSentences $apiStream
        
        if ($loginRes -contains "!done") {
            $apiConnected = $true
            # Get resource
            Send-RosApiSentence $apiStream @("/system/resource/print")
            $resSentences = Read-RosApiSentences $apiStream
            foreach ($s in $resSentences) {
                foreach ($w in $s) {
                    if ($w -like "=board-name=*") { $hwModel = $w.Substring(12) }
                    if ($w -like "=version=*") { $rosVersion = $w.Substring(9) }
                }
            }
            
            # Check device mode
            Send-RosApiSentence $apiStream @("/system/device-mode/print")
            $dmSentences = Read-RosApiSentences $apiStream
            $dmHotspot = "yes"
            $dmFetch = "yes"
            foreach ($s in $dmSentences) {
                foreach ($w in $s) {
                    if ($w -like "=hotspot=*") { $dmHotspot = $w.Substring(9) }
                    if ($w -like "=fetch=*") { $dmFetch = $w.Substring(7) }
                }
            }
            
            # Check flash directory existence
            Send-RosApiSentence $apiStream @("/file/print", "?name=flash")
            $flashRes = Read-RosApiSentences $apiStream
            if ($flashRes.Count -gt 1) { $hasFlash = $true }
            
            Write-Host "    Model Detected : $hwModel" -ForegroundColor Green
            Write-Host "    RouterOS Ver   : $rosVersion" -ForegroundColor Green
            Write-Host "    Storage Type   : $(if ($hasFlash) {'Flash storage (flash/)'} else {'Standard storage (root)'})" -ForegroundColor Green
            
            if ($dmHotspot -eq "no" -or $dmFetch -eq "no") {
                Write-Host "    ⚠️ Device-Mode Restrictions: hotspot=$dmHotspot, fetch=$dmFetch" -ForegroundColor Yellow
                Write-Host "       Switching router from Home Mode to Advance Mode..." -ForegroundColor Cyan
                Send-RosApiSentence $apiStream @("/system/device-mode/update", "=mode=enterprise")
                Send-RosApiSentence $apiStream @("/system/device-mode/update", "=mode=advanced")
                Send-RosApiSentence $apiStream @("/system/device-mode/update", "=hotspot=yes", "=scheduler=yes", "=fetch=yes", "=romon=yes", "=traffic-flow=yes", "=bandwidth-test=yes")
                $null = Read-RosApiSentences $apiStream
                Write-Host "    >>> ACTION REQUIRED: Unplug & replug router power cord within 3 mins to confirm!" -ForegroundColor Magenta
            }
        }
    }
} catch {
    Write-Host "    API query note: $($_.Exception.Message)" -ForegroundColor DarkGray
}

# ── 5. UPLOAD ASSETS (FTP WITH API FALLBACK) ───────────────────
Write-Host "`n>>> Uploading setup script and portal files to router..." -ForegroundColor Cyan
$targetPrefix = if ($hasFlash) { "flash/" } else { "" }
$ftpSuccess = $false

# Method A: Try FTP
try {
    $ftpUri = "ftp://$RouterIP"
    $cred = New-Object System.Net.NetworkCredential($RouterUser, $RouterPass)
    
    $client = [System.Net.FtpWebRequest]::Create("$ftpUri/setup.rsc")
    $client.Method = [System.Net.WebRequestMethods+Ftp]::UploadFile
    $client.Credentials = $cred
    $client.UseBinary = $true
    $client.Timeout = 4000
    $bytes = [System.IO.File]::ReadAllBytes($CustomSetupRsc)
    $s = $client.GetRequestStream()
    $s.Write($bytes, 0, $bytes.Length)
    $s.Close()
    
    # Upload portal files
    Get-ChildItem -Path $ExtractPath -Recurse | ForEach-Object {
        $rel = $_.FullName.Substring($ExtractPath.Length).Replace("\", "/")
        $target = "$ftpUri/${targetPrefix}hotspot$rel"
        if ($_.PSIsContainer) {
            try {
                $mk = [System.Net.FtpWebRequest]::Create($target)
                $mk.Method = [System.Net.WebRequestMethods+Ftp]::MakeDirectory
                $mk.Credentials = $cred
                $mk.GetResponse().Close()
            } catch {}
        } else {
            try {
                $up = [System.Net.FtpWebRequest]::Create($target)
                $up.Method = [System.Net.WebRequestMethods+Ftp]::UploadFile
                $up.Credentials = $cred
                $up.UseBinary = $true
                $b = [System.IO.File]::ReadAllBytes($_.FullName)
                $str = $up.GetRequestStream()
                $str.Write($b, 0, $b.Length)
                $str.Close()
            } catch {}
        }
    }
    $ftpSuccess = $true
    Write-Host "    Assets uploaded successfully via FTP." -ForegroundColor Green
} catch {
    Write-Host "    FTP upload unavailable or firewalled. Trying API file stream..." -ForegroundColor DarkYellow
}

# Method B: Fallback to RouterOS API /file/add
if (-not $ftpSuccess -and $apiConnected) {
    try {
        Write-Host "    Uploading setup.rsc via RouterOS API..." -ForegroundColor Gray
        $rscText = Get-Content -Path $CustomSetupRsc -Raw
        Send-RosApiSentence $apiStream @("/file/add", "=name=setup.rsc", "=contents=$rscText")
        $fileRes = Read-RosApiSentences $apiStream
        
        Write-Host "    Uploading captive portal files via API..." -ForegroundColor Gray
        Get-ChildItem -Path $ExtractPath -Recurse | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
            $rel = $_.FullName.Substring($ExtractPath.Length).Replace("\", "/")
            $destName = "${targetPrefix}hotspot$rel"
            $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::GetEncoding("ISO-8859-1"))
            Send-RosApiSentence $apiStream @("/file/add", "=name=$destName", "=contents=$content")
            $null = Read-RosApiSentences $apiStream
        }
        Write-Host "    Assets uploaded successfully via RouterOS API." -ForegroundColor Green
    } catch {
        Write-Host "⚠️ API file upload error: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ── 6. DIRECT API EXECUTION OR MANUAL IMPORT ───────────────────
Write-Host "`n==================================================================" -ForegroundColor Green
Write-Host "             DEPLOYMENT ASSETS READY ON MIKROTIK                  " -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Green

$executedViaApi = $false
if ($apiConnected) {
    $runNow = "Y"
    if (-not $AutoExecute) {
        $runNow = Read-Host "`nExecute '/import setup.rsc' automatically on the router right now? [Y/n]"
    }
    if ([string]::IsNullOrWhiteSpace($runNow) -or $runNow.ToUpper() -eq "Y") {
        Write-Host ">>> Executing /import setup.rsc via RouterOS API..." -ForegroundColor Cyan
        Send-RosApiSentence $apiStream @("/import", "=file-name=setup.rsc")
        $impRes = Read-RosApiSentences $apiStream
        if ($impRes -contains "!done") {
            $executedViaApi = $true
            Write-Host "🎉 SUCCESS: Configuration formula applied and active on the router!" -ForegroundColor Green
        } else {
            Write-Host "    Import status: $($impRes -join ' ')" -ForegroundColor Yellow
        }
    }
}

if (-not $executedViaApi) {
    Write-Host "`nTo apply setup manually, run this inside MikroTik Winbox Terminal or SSH:" -ForegroundColor White
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "   /import file-name=setup.rsc" -ForegroundColor Black -BackgroundColor Green
    Write-Host "------------------------------------------------------------------" -ForegroundColor DarkGray
}

if ($apiClient -ne $null) { $apiClient.Close() }

Write-Host "`nSite Summary:" -ForegroundColor Gray
Write-Host "  - Site Name : $SiteName" -ForegroundColor Cyan
Write-Host "  - Hardware  : $hwModel (ROS $rosVersion)" -ForegroundColor Cyan
Write-Host "  - SSID      : $Ssid" -ForegroundColor Cyan
Write-Host "  - DNS Portal: http://$DnsName" -ForegroundColor Cyan
Write-Host "  - Capacity  : $($Subnet.Capacity) users on $($Subnet.Network)" -ForegroundColor Cyan
Write-Host "  - Admin User: admin | Admin Pass: $RouterPass" -ForegroundColor Cyan
Write-Host "  - Remote VPN: WireGuard UDP port 13231 (Cloud DDNS Enabled)" -ForegroundColor Cyan
Write-Host "`nUniversal Deployment completed successfully!`n" -ForegroundColor Green
