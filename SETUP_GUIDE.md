# Setting Up a New Customer / New Shop — Step-by-Step

This is the complete onboarding flow to put a new WiFi shop online with
HotspotManager (RouterOS + App + VPS).

---

## Part A — New Router / New Shop (one-time, ~15 min)

### 1. Connect the router
- Configure router WAN to get Internet.
- On the phone, open **HotspotManager** → **Settings → Connect Router**.
- Enter router LAN IP (e.g. `192.168.88.1`), user `admin`, password, API port
  (8728) and FTP port (21).

### 2. Run Universal Setup (one tap)
- **Settings → Universal Setup Helper → Run Full Setup**.
  This installs on the router:
  - firewall & walled-garden (keeps GitHub/app updates reachable pre-login),
  - hotspot service + interface + `hotspot/` directory,
  - **price plans/profiles** (rates, data, time, session timeouts, QoS),
  - **core scripts** `voucher-activate`, `hs-quota-save`, `voucher-expire-check`,
    `hs-on-logout`, `hs-quota-backup` + start-up scheduler (V3 **exact**
    quota/time enforcement, self-heal on reboot),
  - API/FTP services enabled, WireGuard listener for the VPS enforcer.

### 3. Load the captive portal
- **Settings → Custom Portal** — pick one of the **preconfigured themes** from
  **Portal Themes**, or upload your own `.zip` / `.rsc` portal.
- The themed `login.html` posts the customer's code (`username`+`password`),
  and `status.html` shows live remaining data/time from RouterOS variables.

### 4. Create profiles (price plans)
- **Profiles** screen: name, price (Ks), data quota (MB), validity (days),
  speeds (Mbps up/down), shared users, session timeout.

### 5. Generate stock
- **Vouchers → Generate** — pick profile, quantity (1 → thousands), code
  length, prefix. Tap **Print Unprinted** to cut thermal tickets, or A4 sheet.

### 6. Connect to the VPS cloud (optional, recommended)
- In **Settings → Cloud**, register/login and add this router (WireGuard auto).
- The VPS enforcer (`automated_enforcer_v3`) then watches the router and keeps
  exact quota/time cutoffs even when the shop phone is off.

---

## Part B — Everyday Sales Flow (per customer)

1. Customer asks for e.g. "1 GB, 1 day".
2. Shop taps **Sell / New Sale** (Dashboard → Sell More, or Vouchers tab).
3. App shows the next **Unused** (sellable) voucher for that profile —
   partially-consumed vouchers with remaining data/time are automatically
   offered again before fresh stock (they are still sellable).
4. Print the ticket (thermal or A4) and hand it out.
5. Customer opens WiFi, the captive portal appears → types the code →
   `voucher-activate` grants the exact remaining bytes/time and the kernel
   cuts the session when they're gone.
6. After the session ends, the voucher moves to **Expired** automatically.
   Live customers are never in Expired (sticky protection in v1.10.1).

---

## Part C — Daily Accounting

- **Dashboard**: Ks revenue (Today/Yesterday/7/30 days), vouchers sold count,
  exact data used per profile, top profile, active customers.
- **Vouchers tab**: exact **Using / Expired / Unused / Unprinted / Printed**
  counts; cleanup only deletes truly finished codes and never live users.
- **Active Sessions**: kick/ban/whitelist/release, AP detection, customer
  lookup by any finished code.

---

## Prerequisites checklist (before serving customers)
- [ ] Router reachable from the app (API + FTP)
- [ ] Universal Setup run and scripts healthy (re-run after RouterOS upgrades)
- [ ] At least one price profile created
- [ ] Voucher stock generated
- [ ] Captive portal loaded and shows the shop brand (Portal Themes)
- [ ] Bluetooth printer paired and test print OK (Vouchers → printer setup)
- [ ] (Cloud) Router added on the VPS so enforcement stays 24/7