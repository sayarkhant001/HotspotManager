#!/usr/bin/env python3
"""
HotspotManager – VPS Portal Theme Deployer
==========================================
Reads the `hotspot/.portal_theme` marker that the Android app writes on each
router, then uploads the matching generated theme folder
(`portal_templates/<theme-id>/`) over FTP so the captive portal restyles
without rebuilding the whole portal.

Target router layout (mirrors the app's uploadPortalZip behavior):
    hotspot/<file>   and   flash/hotspot/<file>

The theme folders themselves are produced by build_portal_themes.py.
On the VPS this module lives at /opt/hotspot-cloud/deploy_theme.py next to
/opt/hotspot-cloud/portal_templates/.
"""

import ftplib
import io
import logging
import os

logger = logging.getLogger("refinedportals")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_ROOT = os.path.join(BASE_DIR, "portal_templates")
PORTAL_TARGETS = ("hotspot", "flash/hotspot")
MARKER_PATHS = ("hotspot/.portal_theme", "flash/hotspot/.portal_theme")
SUPPORTED_FILES = ("index.html", "login.html", "status.html", "logout.html",
                   "alogin.html", "errors.html", "redirect.html", "error.html")


def read_theme_marker(host, ftp_user, ftp_pass, port=21, timeout=8):
    """Return the active theme id stored on the router (or None)."""
    ftp = None
    try:
        ftp = ftplib.FTP()
        ftp.connect(host, port, timeout=timeout)
        ftp.login(ftp_user, ftp_pass)
        for cand in MARKER_PATHS:
            try:
                buf = io.BytesIO()
                ftp.retrbinary("RETR " + cand, buf.write)
                data = buf.getvalue().decode("utf-8", errors="ignore").strip()
                if data:
                    return data
            except Exception:
                continue
        return None
    except Exception as e:
        logger.warning("read_theme_marker(%s) failed: %s", host, e)
        return None
    finally:
        if ftp:
            try:
                ftp.quit()
            except Exception:
                pass


def theme_folder_exists(theme_id):
    if not theme_id:
        return False
    safe = "".join(ch for ch in theme_id if ch.isalnum() or ch in "-_")
    return os.path.isfile(os.path.join(TEMPLATE_ROOT, safe, "login.html"))


def list_theme_files(theme_id):
    """Return list of (rel_name, bytes) from the theme folder that routers use."""
    safe = "".join(ch for ch in theme_id if ch.isalnum() or ch in "-_")
    folder = os.path.join(TEMPLATE_ROOT, safe)
    out = []
    if not os.path.isdir(folder):
        return out
    for fname in sorted(os.listdir(folder)):
        if fname in SUPPORTED_FILES:
            p = os.path.join(folder, fname)
            with open(p, "rb") as f:
                out.append((fname, f.read()))
    return out


def upload_theme_to_router(host, ftp_user, ftp_pass, theme_id, port=21, timeout=10):
    """Upload every themed portal file to a router. Returns (ok_count, error)."""
    files = list_theme_files(theme_id)
    if not files:
        return 0, f"theme '{theme_id}' has no supported files on this VPS"
    ftp = None
    uploaded = 0
    try:
        ftp = ftplib.FTP()
        ftp.connect(host, port, timeout=timeout)
        ftp.login(ftp_user, ftp_pass)
        for target in PORTAL_TARGETS:
            for rel_name, data in files:
                try:
                    ftp.storbinary("STOR {}/{}".format(target, rel_name), io.BytesIO(data))
                    uploaded += 1
                except Exception as e:
                    # First target may not be writable; second one will be tried.
                    logger.warning("upload %s/%s failed: %s", target, rel_name, e)
        return uploaded, None
    except Exception as e:
        return uploaded, str(e)
    finally:
        if ftp:
            try:
                ftp.quit()
            except Exception:
                pass


def deploy_theme(host, ftp_user, ftp_pass, theme_id, port=21):
    """High-level deploy: validate theme exists, upload files, return message."""
    if not theme_folder_exists(theme_id):
        return {
            "status": "error",
            "message": f"Theme '{theme_id}' not found on VPS template store",
        }
    ok, err = upload_theme_to_router(host, ftp_user, ftp_pass, theme_id, port)
    if ok > 0:
        return {
            "status": "ok",
            "message": f"Deployed theme '{theme_id}' ({ok} file(s) written)",
            "file_count": ok,
        }
    return {"status": "error", "message": f"Deploy failed: {err or 'unknown'}"}


def find_marker_theme(host, ftp_user, ftp_pass, port=21):
    """Read the router's marker and return a deploy dict if it changed."""
    marker = read_theme_marker(host, ftp_user, ftp_pass, port)
    if not marker or not theme_folder_exists(marker):
        return None
    return deploy_theme(host, ftp_user, ftp_pass, marker, port)


if __name__ == "__main__":
    import sys
    if len(sys.argv) >= 5:
        h = sys.argv[1]
        u = sys.argv[2]
        p = sys.argv[3]
        t = sys.argv[4]
        port = int(sys.argv[5]) if len(sys.argv) > 5 else 21
        print("Marker:", read_theme_marker(h, u, p, port))
        print(deploy_theme(h, u, p, t, port))
    else:
        print("Usage: deploy_theme.py <host> <user> <pass> <theme_id> [port]")