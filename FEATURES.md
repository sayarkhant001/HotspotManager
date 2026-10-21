# HotspotManager — Full Feature Report (v1.10.x)

A complete enterprise Wi-Fi hotspot shop / ISP system: Android admin app +
MikroTik RouterOS engine (router-side scripts) + VPS cloud controller +
captive-portal theme library.

---

## 1. Voucher & Sales Engine
- **Voucher / Account generation** — single or bulk (thousands at once),
  auto-unique codes, prefix support, 4–12 digit codes, account mode (user+pass).
- **Exact lifecycle tabs (v1.10.1+)** — vouchers are partitioned into
  mutually-exclusive **Using / Expired / Unused** buckets; counts always add up
  to the total. **Expired = ONLY quota-finished or time-finised codes** —
  a live session is never expired (live + recently-online "sticky" protection).
  Bonus axles: **Unprinted / Printed** filters and **Used history**.
- **Exact quota & time enforcement (V3, on-router)** — `voucher-activate`
  refuses expired/re-used codes and sets `limit-bytes-total = REMAINING` so the
  RouterOS kernel cuts the session at the **exact** remaining byte.
  Continuous time countdown + MAC binding + 1-day exclusion logic.
- **Safe deletion** — "cleanup" only deletes provably finished codes and
  never touches live / recently-online users (would kick customers offline).
- **Renewal** — re-enable & pristine a finished voucher on the router.
- **Thermal & A4 printing** — Bluetooth thermal (58/80mm) with QR/barcode,
  custom styles, auto-cut, live preview; A4 sheet layouts; print-only-unprinted.

## 2. Dashboard & Money-First Metrics
- **Reverse hero card**: Ks sales, activated count, top profile, date filters
  (Today / Yesterday / 7 / 30 / All).
- **Usage metrics** — exact consumed MB/GB per profile (router-truth tags),
  vouchers sold, active now, per-profile revenue breakdown drill-down.
- **Real-time traffic**, hardware health (CPU with history, RAM, disk, temp),
  uptime, version, date-filtered session analytics.
- **Readable enterprise theme** — MikroPilot dark palette with
  theme-aware accent colors (v1.10.1 fixes dark-green-on-pale text seeability).

## 3. Network Topology (Live AP Map)
- Auto-detection of Ruijie AirMetro / Reyee / TP-Link APs & bridges.
- **Animated beams**, **pulsing health dots**, signal-strength meters and
  drill-down health strips (v1.10.0).
- AirMetro PtP/PtMP pair management (auto-pair, swap roles, backhaul stats).
- Allowlist/whitelist AP MACs, per-AP client kick / ban / whitelist.

## 4. App Flow Control (Ruijie-style categories)
32 services across 8 groups — Communications (Telegram/Viber/Skype/Discord/
WeChat/Zoom/WhatsApp), Social Media, Video Streaming, **VPN & Proxies**,
**Gambling**, **Adult Content**, **Banking & Finance**, **Gaming**.
Deploys `/ip firewall filter` Layer7 + address-list rules per toggle.

## 5. Active Sessions & Customer Management
- Live session list (dedup-safe, crash-guarded), kick / ban / whitelist /
  release, device model detection (AP vs phone vs PC), search by user/MAC/IP.
- Customer lookup by entering a finished code → shows plan, remaining
  quota/time, usage, and history.

## 6. Captive Portals & Themes
- **10 preconfigured portal themes** (login/status/logout/alogin/errors)
  with voucher+member login, voucher-code autofill, and friendly Myanmar
  quota/expired error messages. All portals POST the customer's code directly
  (`name="username"`/`password`) so `voucher-activate` reads and activates it.
- Portal preview, upload of custom .zip/.rsc portals, theme deploy marker
  (`hotspot/.portal_theme`) consumed by the VPS enforcer.

## 7. VPS Cloud Controller & Enforcer (CPU-safe)
- **Enforcer tiers**: active-session sweep 60s (targeted queries only),
  offline quota sweep 15min, full V3 audit on boot / 24h, theme sync 24h,
  port probe 60s — no bulk 1,800-user dumps (CPU spike fix).
- Auto-reboot on repeated failures, version+update probes, per-router audits,
  admin portal REST API (routers, portal deploy, theme deploy, statistics).

## 8. Router Management (Universal Setup)
- One-tap full provisioning (`UniversalSetupHelper`): firewall, QoS/shaping,
  hotspot profiles, walled garden for GitHub updates, WireGuard cloud link,
  scheduler scripts, API/FTP services, captive portal deploy.
- RSC script runner (3-tier engine), terminal console, password change,
  stats reset, IP bindings, PPPoE checks, router reboot.

## 9. Other
- Myanmar/English localization, GitHub update channel with in-app APK install,
  crash logger + diagnostics in Settings, print QR for every voucher,
  session history analytics, hardware traffic counters, hotspot URL/SSID
  detection, language switcher, Bluetooth printer setup wizard.