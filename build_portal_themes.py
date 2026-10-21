#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HotspotManager – Portal Theme Generator
=======================================
Expands the parameterized base template into 10 ready-to-upload MikroTik
captive-portal folders under portal_templates/<theme-id>/.

Usage:
    python build_portal_themes.py [brand_name]

The theme palettes mirror the Android app (PortalThemeScreen ALL_THEMES).
Settings:
  PORTAL_TEMPLATE_BRAND  env var to override the SSID/brand shown ("WiFi Hotspot")
Generated files per theme: index.html, login.html, status.html, logout.html,
alogin.html, errors.html  (all self-contained, no external CSS).
"""

import os
import re
import sys
import shutil

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(BASE_DIR, "portal_templates", "base")
OUT = os.path.join(BASE_DIR, "portal_templates")
BRAND = os.environ.get("PORTAL_TEMPLATE_BRAND") or (sys.argv[1] if len(sys.argv) > 1 else "") or "WiFi Hotspot"

# id, name, primary, primary2, accent, bg, text, text_muted, radius, featured
THEMES = [
    ("emerald-glass",    "Emerald Glass",     "#00E676", "#10B981", "#059669", "#1B5E20", "#FFFFFF", "#A7F3D0", 24, True),
    ("midnight-purple",  "Midnight Purple",   "#7B2FFB", "#9333EA", "#C084FC", "#1E1B4B", "#FFFFFF", "#C4B5FD", 24, False),
    ("sunkissed-orange", "Sun-Kissed Orange", "#F97316", "#EA580C", "#F59E0B", "#7C2D12", "#FFFFFF", "#FDBA74", 20, False),
    ("ocean-blue",       "Ocean Blue",        "#0EA5E9", "#0284C7", "#38BDF8", "#0C4A6E", "#FFFFFF", "#BAE6FD", 22, False),
    ("cherry-blossom",   "Cherry Blossom",    "#EC4899", "#DB2778", "#F472B6", "#831843", "#FFFFFF", "#FBCFE8", 26, False),
    ("golden-buddha",    "Golden Buddha",     "#F59E0B", "#D97706", "#FBBF24", "#451A03", "#FFF3E0", "#FDE68A", 20, True),
    ("cyber-neon",       "Cyber Neon",        "#06B6D4", "#D946EF", "#F72585", "#111827", "#E0E7FF", "#94A3B8", 18, False),
    ("forest-canopy",    "Forest Canopy",     "#22C55E", "#15803D", "#4ADE80", "#14532D", "#FFFFFF", "#BBF7D0", 22, False),
    ("sunset-mandalay",  "Sunset Mandalay",   "#EF4444", "#DC2626", "#F87171", "#450A0A", "#FFFFFF", "#FECACA", 24, True),
    ("classic-light",    "Classic Light",     "#2563EB", "#1D4ED8", "#E5E7EB", "#FFFFFF", "#1F2937", "#64748B", 16, False),
]

HEX_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")


def hex_to_rgba(hex_color, alpha):
    """Convert #RRGGBB to rgba(r,g,b,a) string."""
    h = hex_color.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return f"rgba({r}, {g}, {b}, {alpha})"


def lighten(hex_color, factor):
    """Lighten/darken a hex color by factor (-1..1)."""
    h = hex_color.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    def blend(v):
        if factor >= 0:
            return int(v + (255 - v) * factor)
        return int(v * (1 + factor))
    return "#{:02X}{:02X}{:02X}".format(blend(r), blend(g), blend(b))


def build_theme(theme):
    tid, name, primary, primary2, accent, bg, text, muted, radius, featured = theme
    for c in (primary, primary2, accent, bg, text, muted):
        assert HEX_RE.match(c), f"invalid hex {c}"
    card_alpha = 0.82 if bg.upper() == "#FFFFFF" else 0.78
    replacements = {
        "[[BRAND]]": BRAND,
        "[[COLOR_BG]]": bg,
        "[[COLOR_CARD]]": hex_to_rgba(bg, card_alpha),
        "[[COLOR_BORDER]]": hex_to_rgba(primary, 0.35),
        "[[COLOR_PRIMARY]]": primary,
        "[[COLOR_PRIMARY2]]": primary2,
        "[[COLOR_ACCENT]]": accent,
        "[[COLOR_TEXT]]": text,
        "[[COLOR_MUTED]]": muted,
        "[[BTN_GRADIENT]]": f"linear-gradient(135deg, {primary} 0%, {primary2} 100%)",
        "[[RADIUS]]": f"{radius}px",
    }

    out_dir = os.path.join(OUT, tid)
    if os.path.isdir(out_dir):
        shutil.rmtree(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    for fname in ("index.html", "login.html", "status.html", "logout.html", "alogin.html", "errors.html"):
        src = os.path.join(BASE, fname)
        with open(src, "r", encoding="utf-8") as f:
            content = f.read()
        for key, val in replacements.items():
            content = content.replace(key, val)
        with open(os.path.join(out_dir, fname), "w", encoding="utf-8") as f:
            f.write(content)
    print(f"  ✓ {tid:<18} ({name}) → portal_templates/{tid}/")


def main():
    print(f"HotspotManager Portal Theme Generator — brand: {BRAND}")
    print("Building 10 themes from base template...\n")
    for t in THEMES:
        build_theme(t)
    print(f"\nDone. {len(THEMES)} theme folders under {os.path.relpath(OUT, BASE_DIR)}/")
    print("Each folder is a complete MikroTik hotspot directory (upload files to /hotspot).")


if __name__ == "__main__":
    main()