# Yadanar Tun WiFi - Hotspot Manager & Captive Portal System

<div align="center">

![HotspotManager Banner](https://img.shields.io/badge/MikroTik-RouterOS_v7-red?style=for-the-badge&logo=mikrotik)
![Android Version](https://img.shields.io/badge/Android_App-v1.4.7-green?style=for-the-badge&logo=android)
![SSID](https://img.shields.io/badge/SSID-Hide%20Wifi-blue?style=for-the-badge&logo=wi-fi)

<h3>High-Performance MikroTik Hotspot System with Real-Time Profile Alignment & Luxury Captive Portal</h3>

<p>
  <a href="https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.4.7.apk">
    <img src="https://img.shields.io/badge/Download_APK-v1.4.7_(Direct)-success?style=for-the-badge&logo=android" alt="Download APK" />
  </a>
</p>

</div>

---

## 📱 App Download for Users

- **Direct Download Link:** [HotspotManager-v1.4.7.apk](https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.4.7.apk)
- **Version:** v1.4.7 (versionCode: 24)
- **What's New in v1.4.7:**
  - 🗑️ **30-Day Auto-Prune Expired Vouchers:** Automatically cleans up expired vouchers that reached expiration 30 or more days ago, preventing local Room DB bloat while keeping active online users and unexpired/stock inventory 100% safe.
  - 🛡️ **Non-Disruptive Advance Mode:** Inspects RouterOS `/system/device-mode/print` first; prevents triggering hardware power-cycle confirmation prompts and preserves active connections for all connected clients.
  - ⚡ **Safe RouterOS Script Execution:** RouterOS API script runner executes scripts by name (`=number=$scriptName`) with output capture and cleanup, preventing API trap errors.
  - 🔄 **Voucher Release & Transfer:** Red circle button unbinds a client's MAC address from a voucher so it can be reinserted into another phone without losing remaining quota/time.
  - 📑 **Using & Expired Tabs:** Clean separation between vouchers currently being used and vouchers whose quota/time is fully expired. Removed Mikhmon references and removed blocked tab.
  - 🚀 **GitHub Remote Script Runner & Terminal Console:** Fetch and run `setup.rsc` and custom RSC scripts directly from GitHub with 1 tap.

---

## 👤 Where to Find Accounts in Winbox vs App

### 1. In MikroTik Winbox:
> ⚠️ **Important:** Do NOT look under `System` > `Users`. That menu is only for router admin logins (`admin`, `flutter_app`).
> 
> 👉 **Hotspot Accounts are located under:**
> **IP** ➔ **Hotspot** ➔ **Users** tab (contains all 4,586 imported voucher accounts).

### 2. In the HotspotManager App:
1. Open the app and log in with your Router IP (`192.168.1.164` or `10.10.10.1`), user `admin`, password `Khant1234@`.
2. Tap the **Vouchers** tab. The app automatically fetches all 4,586 accounts and displays them with their profile badges, quotas, and PIN passwords.

---

## ⚡ Setup & Accounts Scripts (`setup.rsc` & `accounts.rsc`)

1. **`setup.rsc`**: Master configuration formula (Bridge, DHCP, DNS, Wi-Fi `Hide Wifi`, 5 Profiles, Firewall, Captive Portal).
2. **`accounts.rsc`**: Automatically generated from `Accounts.xlsx` containing all 4,586 non-expired accounts.

To run both on any MikroTik router:
```routeros
# In MikroTik Terminal:
/import file-name=setup.rsc
/import file-name=accounts.rsc
```

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
> Download the official Hotspot Manager app (v1.3.5) here:
> 👉 https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.5.apk

### မြန်မာဘာသာ (Myanmar Unicode):
> 📢 **ရတနာထွန်း WiFi အသိပေးချက် -**
> ယခုအခါ Wi-Fi အမည် (SSID) ကို **Hide Wifi** ဟု ပြောင်းလဲထားပါသည်။
> Wi-Fi ချိတ်ဆက်ပြီးပါက မိမိတို့၏ Voucher Code (သို့မဟုတ်) အကောင့်နံပါတ်ဖြင့် Login ပြုလုပ်၍ အင်တာနက် အသုံးပြုနိုင်ပါသည်။
> HotspotManager App ဗားရှင်းသစ် (v1.3.5) ကို အောက်ပါလင့်ခ်မှ တိုက်ရိုက်ဒေါင်းလုဒ်ရယူနိုင်ပါသည် -
> 👉 https://github.com/sayarkhant001/HotspotManager/raw/main/HotspotManager-v1.3.5.apk
