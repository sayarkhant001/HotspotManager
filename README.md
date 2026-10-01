# Yadanar Tun WiFi - Hotspot Manager & Captive Portal System

<div align="center">

![HotspotManager Banner](https://img.shields.io/badge/MikroTik-RouterOS_v7-red?style=for-the-badge&logo=mikrotik)
![Android Version](https://img.shields.io/badge/Android_App-v1.3.4-green?style=for-the-badge&logo=android)
![SSID](https://img.shields.io/badge/SSID-Hide%20Wifi-blue?style=for-the-badge&logo=wi-fi)

<h3>High-Performance MikroTik Hotspot System with Real-Time Profile Alignment & Luxury Captive Portal</h3>

<p>
  <a href="https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.4.apk">
    <img src="https://img.shields.io/badge/Download_APK-v1.3.4_(Direct)-success?style=for-the-badge&logo=android" alt="Download APK" />
  </a>
</p>

</div>

---

## 📱 App Download for Users

- **Direct Download Link:** [HotspotManager-v1.3.4.apk](https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.4.apk)
- **Version:** v1.3.4 (versionCode: 11)
- **Features in v1.3.4:**
  - **Dynamic Profile Alignment:** When an admin modifies any profile (Speed, Quota, Validity, Price), all unused vouchers and active online users automatically align with the new settings in real time on both MikroTik and local database.
  - **Single & Dual-Field Authentication:** Seamless support for Voucher PIN numbers and Username/Password accounts.
  - **Direct RouterOS API Integration:** Connects directly via port `8728` with zero third-party cloud dependency.

---

## 📡 Wi-Fi Configuration

- **SSID:** `Hide Wifi`
- **DNS Name:** `yadanartun.wifi`
- **Gateway IP:** `10.10.10.1` (`10.10.10.0/23` network)
- **DHCP Client Pool:** `10.10.10.10` - `10.10.11.250`
- **SSID Broadcast Option:**
  - Standard broadcast: `:global hideSsid no` (Default - phones detect and open captive portal immediately)
  - Hidden broadcast: `:global hideSsid yes` (Manual network addition required)

---

## ⚡ Master Setup Formula (`setup.rsc`)

The single, definitive script to configure the entire MikroTik Hotspot router from scratch:

```routeros
# In MikroTik Terminal:
/import file-name=setup.rsc
```

### Key Features of `setup.rsc`:
1. **Universal Port Bridging:** Automatically maps all Ethernet (`ether2` through `ether10`) and SFP interfaces (`sfp1`) to `hotspot-bridge`, reserving `ether1` for WAN.
2. **Wi-Fi 6 & Legacy Wireless:** Automatically sets up `wifi1` (RouterOS v7) or `wlan1` (RouterOS v6) with SSID `Hide Wifi`.
3. **5 Pre-configured Profiles:**
   - `5GB` (20M/20M, 1 Day, 5 GB)
   - `30Day` (10M/10M, 30 Days, 60 GB)
   - `2GB` (20M/20M, 1 Day, 2 GB)
   - `2Hour` (5M/5M, 2 Hours, Unlimited)
   - `VIP` (5M/5M, Unlimited)
4. **Continuous Validity Countdown:** On first voucher login, starts a persistent `/system scheduler` countdown timer that never pauses even when devices disconnect.
5. **MAC Roaming:** Devices with randomized MAC addresses can log back in seamlessly without quota corruption.
6. **Firewall & Security Hardening:** Prevents DoT circumvention, blocks unauthorized WAN forward, secures LAN management, and stops mobile tethering via TTL mangling.

---

## 🔄 Ruijie Reyee AP Migration Guide (`RAP2200(E)`, `EW1200`, `EW3000GX`)

To connect existing Ruijie Access Points to the MikroTik Hotspot:

1. **Disable Ruijie Portal:**
   - In Ruijie Reyee Cloud or local Master AP Web GUI (`192.168.110.1`), navigate to **Configuration** > **Auth & Accounts** > **Captive Portal**.
   - Turn **OFF** the captive portal policy for SSID `Hide Wifi`.
2. **Configure Open SSID:**
   - Navigate to **Configuration** > **Wireless** > **Wi-Fi**.
   - Set SSID Name: `Hide Wifi`.
   - Security / Encryption: **Open (None)**.
3. **Bridge / AP Mode (for EW1200 / EW3000GX):**
   - Switch device mode from *Router Mode* to *Access Point (Bridge) Mode* to avoid double-NAT and DHCP collisions.
4. **Physical Connection:**
   - Connect the WAN/PoE uplink port of each Ruijie AP to any LAN bridge port on the MikroTik (`ether2` through `ether8`).
   - The MikroTik router will handle all DHCP, bandwidth control, captive portal redirection, and accounting.

---

## 💬 Message Template for Users (SMS / Telegram / Viber)

### English:
> 📢 **Yadanar Tun WiFi Update:**
> Please connect to the Wi-Fi network: **Hide Wifi**
> When prompted, log in with your Voucher Code or Account Number.
> Download the official Hotspot Manager app (v1.3.4) here:
> 👉 https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.4.apk

### မြန်မာဘာသာ (Myanmar Unicode):
> 📢 **ရတနာထွန်း WiFi အသိပေးချက် -**
> ယခုအခါ Wi-Fi အမည် (SSID) ကို **Hide Wifi** သို့ ပြောင်းလဲထားပါသည်။
> Wi-Fi ချိတ်ဆက်ပြီးပါက မိမိတို့၏ Voucher ကုတ် သို့မဟုတ် အကောင့်နံပါတ်ဖြင့် Login ပြုလုပ်၍ အင်တာနက် အသုံးပြုနိုင်ပါသည်။
> HotspotManager App ဗားရှင်းသစ် (v1.3.4) ကို အောက်ပါလင့်ခ်မှ တိုက်ရိုက်ဒေါင်းလုဒ်ရယူနိုင်ပါသည် -
> 👉 https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.4.apk
