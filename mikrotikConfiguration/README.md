# 🚀 MikroTik Universal Captive Portal Configuration & Provisioning Engine

[![RouterOS](https://img.shields.io/badge/RouterOS-v6_%26_v7-red?style=for-the-badge&logo=mikrotik)](https://mikrotik.com)
[![Hardware](https://img.shields.io/badge/Hardware-All_Models-blue?style=for-the-badge)](https://mikrotik.com)
[![Capacity](https://img.shields.io/badge/Capacity-10_to_10%2C000_IPs-green?style=for-the-badge)](https://github.com/sayarkhant001/mikrotikConfiguration)
[![Captive Portal](https://img.shields.io/badge/Portal-YadanarTun__WiFi-orange?style=for-the-badge)](https://github.com/sayarkhant001/mikrotikConfiguration)

Automated, single-command provisioning system for MikroTik routers and luxury captive portals. Designed for field technicians and remote site deployments with zero manual subnet math or hardware confusion.

---

## ⚡ Quick Start: The One-Command Deployer

On any remote site Windows PC or laptop connected to the MikroTik router (default `192.168.88.1`), open **PowerShell** and run:

```powershell
irm https://raw.githubusercontent.com/sayarkhant001/mikrotikConfiguration/main/deploy.ps1 | iex
```

### What Happens When You Run This Command:
1. **Prompts for Site Name**: Choose `YadanarTun_WiFi`, `AYeikSitt_1`, `AYeikSitt_2`, or any custom site in the repo.
2. **Prompts for Wi-Fi SSID**: e.g., `YadanarTun_WiFi` or `AyeikSitt Free WiFi`.
3. **Prompts for DNS Redirect**: e.g., `yadanartun.wifi`.
4. **Prompts for Capacity (10 - 10,000 IPs)**: Computes the mathematically exact CIDR subnet, netmask, gateway, pool, and lease time automatically!
5. **Downloads Assets**: Pulls the site's `hotspot.zip` from GitHub and extracts it.
6. **Uploads to Router**: Transfers the captive portal files to the router's correct directory (`flash/hotspot` or `hotspot`).
7. **Generates & Runs Setup**: Applies the hardware-adaptive formula and configures the router.

---

## 📊 Dynamic Subnet & Capacity Matrix (10 – 10,000 IPs)

The system automatically calculates the correct subnet mask, gateway, and pool based on your requested user count:

| User Count | Prefix | Subnet Mask | Network CIDR | Gateway IP | Client DHCP Pool Range | Lease Time |
| :--- | :---: | :--- | :--- | :--- | :--- | :---: |
| **10 – 240** | `/24` | `255.255.255.0` | `10.10.10.0/24` | `10.10.10.1` | `10.10.10.10` – `10.10.10.254` | 2 Hours |
| **241 – 500** | `/23` | `255.255.254.0` | `10.10.10.0/23` | `10.10.10.1` | `10.10.10.10` – `10.10.11.250` | 2 Hours |
| **501 – 1,000** | `/22` | `255.255.252.0` | `10.10.8.0/22` | `10.10.8.1` | `10.10.8.10` – `10.10.11.250` | 1 Hour |
| **1,001 – 2,000** | `/21` | `255.255.248.0` | `10.10.0.0/21` | `10.10.0.1` | `10.10.0.10` – `10.10.7.250` | 1 Hour |
| **2,001 – 4,000** | `/20` | `255.255.240.0` | `10.10.0.0/20` | `10.10.0.1` | `10.10.0.10` – `10.10.15.250` | 45 Mins |
| **4,001 – 8,000** | `/19` | `255.255.224.0` | `10.10.0.0/19` | `10.10.0.1` | `10.10.0.10` – `10.10.31.250` | 30 Mins |
| **8,001 – 10,000**| `/18` | `255.255.192.0` | `10.10.0.0/18` | `10.10.0.1` | `10.10.0.10` – `10.10.63.250` | 30 Mins |

---

## 🛡️ Brand New Routers & Device-Mode Unlocking

On factory-fresh routers running **RouterOS v7.13+**, MikroTik defaults to `mode: home`, where `fetch: no` or `hotspot: no` are restricted.

The setup script automatically detects this and initiates:
```routeros
/system/device-mode/update hotspot=yes scheduler=yes fetch=yes
```

> ⚠️ **Physical Confirmation Required:** When this command triggers, MikroTik security requires physical authorization:
> **Unplug and replug the router's power cable (or press the Mode/Reset button) within 3 minutes.**

---

## 🧩 Hardware & RouterOS Universal Adaptation

| Component | Adaptive Behavior |
| :--- | :--- |
| **Storage (`flash/` vs `/`)** | Detects NOR Flash devices (e.g. `hEX`, `hAP ac²`, `L009`) and automatically deploys to `flash/hotspot`. On NAND, x86, CCR, and CHR devices, it targets `/hotspot`. |
| **Wireless Hardware** | Detects RouterOS v7 `wifi` / `wifi-qcom` (`wifi1`, `wifi2`) vs legacy `wireless` (`wlan1`). If no Wi-Fi hardware exists, it bridges ports for external APs (Ruijie Reyee, UniFi). |
| **Ethernet & SFP Ports** | Keeps `ether1` isolated as WAN DHCP-Client + NAT Masquerade. Automatically bridges all remaining ports (`ether2`..`etherN` + `sfp1`..`sfpN`) into `hotspot-bridge`. |
| **Packet Interception** | Automatically executes `/interface bridge settings set allow-fast-path=no` to ensure captive portal redirection is never bypassed by switch chip fast-pathing. |
| **HotspotManager App** | Enables RouterOS API on port `8728` and inserts a Walled Garden bypass rule so admin apps can connect before logging in. |
| **Voucher Architecture** | Creates the 5 core standard profiles (`5GB`, `30Day`, `2GB`, `2Hour`, `VIP`) with MAC-roaming enabled and zero pre-configured dummy accounts. |

---

## 📁 Repository Structure

```text
mikrotikConfiguration/
├── deploy.ps1                     # Interactive PC Deployment Script
├── bootstrap.rsc                  # In-Router Fetcher (for routers with internet)
├── core/
│   └── universal_setup.rsc        # Master hardware/version adaptive formula
└── sites/
    ├── YadanarTun_WiFi/
    │   └── hotspot.zip            # Luxury Captive Portal for Yadanar Tun
    ├── AYeikSitt_1/
    │   └── hotspot.zip            # Captive Portal for A Yeik Sitt 1
    └── AYeikSitt_2/
        └── hotspot.zip            # Captive Portal for A Yeik Sitt 2
```

---

## ➕ Adding a New Site in 60 Seconds

1. Design or edit your captive portal HTML files.
2. Select all files inside your portal folder (`login.html`, `status.html`, `css/`, etc.) and compress directly into `hotspot.zip`.
3. Create a folder in GitHub: `sites/<YourSiteName>/` and upload `hotspot.zip`.
4. Done! You can now deploy it on any router by running the one-liner and typing `<YourSiteName>`.

---

## 🌐 In-Router Direct Bootstrap (Optional)

If the router is already connected to an active internet upstream on `ether1`, you can provision directly from the MikroTik Terminal:

```routeros
:global siteName "YadanarTun_WiFi"; :global ipCapacity 500; /tool fetch url="https://raw.githubusercontent.com/sayarkhant001/mikrotikConfiguration/main/bootstrap.rsc" dst-path=bootstrap.rsc; /import file-name=bootstrap.rsc
```
