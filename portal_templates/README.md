# Portal Themes — Preconfigured Captive Portals

10 shop-ready MikroTik captive portal sets under this folder. Each theme
folder (`emerald-glass/`, `midnight-purple/`, … `classic-light/`) contains all
files a router needs:

| File | Role |
|------|------|
| `index.html` | meta-redirect into `login.html` |
| `login.html`  | **Voucher + Member login page** |
| `status.html` | **Live session status** (remaining data/time, IP, MAC, uptime) |
| `logout.html` | Disconnect confirmation |
| `alogin.html` | "Connected" interstitial (auto-advances to status) |
| `errors.html` | Friendly error (quota/time finished, invalid code) |

## How the portals "grab the data" (reading the finished codes)

- The login form posts `<input name="username">` + `<input name="password">`
  to `$(link-login-only)` — i.e. the **customer's voucher code** goes straight
  into RouterOS, where the router-side `voucher-activate` script **reads the
  code** and activates the exact remaining quota/time (kernel-enforced cutoff).
- The voucher paste is remembered in `localStorage` (`wifi_voucher`) and
  auto-filled the next login if the previous attempt never finished.
- `status.html` renders RouterOS truth variables: `$(username)`, `$(ip)`,
  `$(mac)`, `$(bytes-in-nice)`, `$(bytes-out-nice)`, `$(uptime)`,
  `$(session-time-left)` — so the customer always sees the exact remaining
  data/time while connected.
- Friendly Myanmar + English messages translate RouterOS errors from the
  finished codes: **data quota finished, validity expired, invalid code,
  already logged in** — matching how the shop's Expired tab classifies them.

## Regenerate / rebrand

```bash
python build_portal_themes.py "Your Shop WiFi Name"
```

## Deploy to a router

1. From the Android app: **Portal Themes** → pick a theme → **Deploy**.
   - The app writes `hotspot/.portal_theme` with the theme id on the router.
2. The VPS enforcer reads that marker and uploads the matching folder
   (`deploy_theme.py`, FTP to `hotspot/` + `flash/hotspot/`) on its next
   sync. You can also trigger immediately via the admin API:
   `POST /api/routers/{rid}/theme {"theme_id":"emerald-glass"}`.
3. Or upload the folder manually as a `.zip` via **Custom Portal**.