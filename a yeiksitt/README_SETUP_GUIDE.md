# A Yeik Sitt WiFi – Cloud & Hotspot Setup Guide (အရိပ်စစ် ဝိုင်ဖိုင်)

ဤဖိုင်တွဲတွင် **A Yeik Sitt WiFi** အတွက် လိုအပ်သော Cloud Tunnel, Hotspot Setup, Cute Kid Captive Portal နှင့် Android App အားလုံး ပါဝင်ပါသည်။

---

## 🔑 Cloud & Router အကောင့် အချက်အလက်များ

| အမျိုးအမည် | အချက်အလက် (Credentials) |
|---|---|
| **Cloud Web / Android App အသုံးပြုသူအမည်** | `ayeiksitt` |
| **Cloud Web / Android App လျှို့ဝှက်နံပါတ်** | `Ayeiksitt1234@` |
| **Cloud Admin Dashboard** | `https://hotspot-admin.pages.dev` |
| **Router အမည် (SSID)** | `A Yeik Sitt Wifi` |
| **Router Local IP** | `10.10.10.1` (Direct IP Mode - No DNS needed) |
| **WireGuard Cloud IP** | `10.200.0.3` (Central VPS: 3.84.81.152) |
| **Remote Cloud Relay Port** | `3.84.81.152:8732` (Remote Winbox / API) |
| **Router Admin Password** | `Ayeiksitt1234@` |

---

## 📁 ဖိုင်တွဲဖွဲ့စည်းပုံ (Files Structure)

1. `hotspot/` – ချစ်စရာ ကလေးပုံလေးနှင့် ဖန်တီးထားသော မြန်မာဖောင့် Captive Portal ဖိုင်များ (Login, Status, Logout, Error, Images)
   - Status စာမျက်နှာတွင် လက်ကျန်ဒေတာကို MB ဖြင့် အတိအကျ ပြသပေးပါသည် (ဥပမာ: `1,894.2 MB` / `450.5 MB`)။
2. `A_Yeik_Sitt_Full_Setup.rsc` – Winbox တွင် တိုက်ရိုက် Run နိုင်သော တစ်ခုလုံး အပြီးအစီး Provisioning Script။
3. `WireGuard_Cloud_Only.rsc` – Router တွင် Hotspot ရှိပြီးသားဖြစ်၍ Cloud Tunnel သာ ချိတ်လိုပါက Run ရန် Script။
4. `Quota_Engine_Only.rsc` – Quota ကုန်ပါက ချက်ချင်း Logout ချပေးပြီး အတိအကျ ထိန်းသိမ်းပေးသော Script။
5. `HotspotManager-v1.4.9-ayeiksitt.apk` – Remote User ထံ ပေးပို့ရန် Android Application။

---

## 🚀 Winbox ဖြင့် Router Setup ပြုလုပ်နည်း (၃ ဆင့်ဖြင့် အလွယ်တကူ)

### အဆင့် (၁) - Captive Portal ဖိုင်များ တင်ခြင်း
1. Winbox ဖွင့်၍ Router သို့ ချိတ်ဆက်ပါ။
2. ဘယ်ဘက် Menu ရှိ **Files** ကို နှိပ်ပါ။
3. ဤ Folder ထဲမှ `hotspot` ဖိုင်တွဲတစ်ခုလုံးကို Winbox ၏ **Files** ထဲသို့ Drag & Drop ဆွဲထည့်ပါ (Router တွင် `flash` disk ပါပါက `flash/hotspot` ဖြစ်သွားပါမည်)။

### အဆင့် (၂) - Setup Script ကို Run ခြင်း
1. Winbox တွင် **New Terminal** ကို ဖွင့်ပါ။
2. `A_Yeik_Sitt_Full_Setup.rsc` ဖိုင်ကို Notepad ဖြင့်ဖွင့်ပြီး စာသားအားလုံးကို **Copy** ကူးယူပါ။
3. Winbox Terminal တွင် **Paste (Right-Click -> Paste)** ပြုလုပ်ပါ။
4. ခလုတ်တစ်ခုမျှ နှိပ်စရာမလိုဘဲ အောက်ပါအချက်များ အလိုအလျောက် ပြီးမြောက်သွားပါမည်:
   - Wi-Fi SSID `A Yeik Sitt Wifi` စတင်လွှင့်တင်ခြင်း။
   - Local IP `10.10.10.1` သတ်မှတ်ခြင်း (DNS မလိုဘဲ တိုက်ရိုက် အလုပ်လုပ်ပါသည်)။
   - ချစ်စရာ ကလေးပုံနှင့် Captive Portal အသက်ဝင်ခြင်း။
   - Quota ကုန်ပါက ချက်ချင်း အဆက်အသွယ်ဖြတ်တောက်သည့် Engine အသက်ဝင်ခြင်း။
   - Central Cloud Server (`3.84.81.152`) သို့ WireGuard VPN ဖြင့် အလိုအလျောက် ချိတ်ဆက်သွားခြင်း။

---

## 📱 Android App အသုံးပြုပုံ (Remote User)
1. `HotspotManager-v1.4.9-ayeiksitt.apk` ကို အသုံးပြုသူဖုန်းထဲသို့ ထည့်သွင်း (Install) ပါ။
2. App ကို ဖွင့်ပြီး Login ဝင်ပါ:
   - **Username:** `ayeiksitt`
   - **Password:** `Ayeiksitt1234@`
3. အောင်မြင်စွာ Login ဝင်ရောက်ပြီးပါက မိမိ၏ **A Yeik Sitt Wifi** ရူတာကို ဆိုင်တွင် ရှိနေသည်ဖြစ်စေ၊ အဝေးရောက်နေသည်ဖြစ်စေ မည်သည့်နေရာမှမဆို စောင့်ကြည့်၊ ဘောင်ချာထုတ်၊ အကောင့်ဖျက်/တိုး ပြုလုပ်နိုင်ပါပြီ။
