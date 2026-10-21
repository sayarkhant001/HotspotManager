#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_duration_parser.py — proves the long-term-voucher fix in
automated_enforcer_v3.py:
  * [ORIG-UP:] tag is parsed EXACTLY (30d/1mo/4w/1w2d/24h/...)
  * unknown validity  => None  (NEVER a 1-day default — this was killing
    long-term vouchers after just one day)
  * unknown quota     => 0     (never the old 1.66 GB guess)
  * format_ros_duration round-trips back to RouterOS limit-uptime strings.

Run:  python verify_duration_parser.py [path/to/automated_enforcer_v3.py]
"""
import io
import os
import re
import sys
from datetime import timedelta

SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "automated_enforcer_v3.py")


def extract_funcs(text):
    lines = text.splitlines()
    start = next(i for i, l in enumerate(lines) if l.startswith("def parse_ros_duration"))
    # End right after the body of parse_orig_limit (the '    return 0' inside it).
    po_start = next(i for i, l in enumerate(lines) if l.startswith("def parse_orig_limit"))
    end = next(i for i in range(po_start, len(lines)) if lines[i].strip() == "return 0" and not lines[i].startswith(" " * 8))
    body = "\n".join(lines[start:end + 1])
    ns = {"timedelta": timedelta, "re": re}
    exec(compile(body, "extracted", "exec"), ns)
    return ns


def main():
    with open(SRC, encoding="utf-8") as f:
        ns = extract_funcs(f.read())
    pn = ns["parse_ros_duration"]
    pd = ns["parse_duration"]
    po = ns["parse_orig_limit"]
    fr = ns["format_ros_duration"]
    D = timedelta(days=1)

    checks = []
    def ck(name, cond):
        checks.append((name, cond))
        print(("PASS " if cond else "FAIL !!! ") + name)

    # --- parse_ros_duration exact tag parsing ---
    ck("30d -> 30 days", pn("30d") == 30 * D)
    ck("7d -> 7 days", pn("7d") == 7 * D)
    ck("1mo -> 30 days", pn("1mo") == 30 * D)
    ck("1w2d -> 9 days", pn("1w2d") == 9 * D)
    ck("24h -> 1 day", pn("24h") == 1 * D)
    ck("1h30m -> 90 min", pn("1h30m") == timedelta(hours=1, minutes=30))
    ck("blank -> None", pn("") is None)
    ck("0s -> None", pn("0s") is None)

    # --- THE BUG: unknown duration MUST NOT default to 1 day ---
    ck("2GB (no time info) -> None (NOT 1 day!)", pd("2GB", "") is None)
    ck("1000Ks (no time info) -> None (NOT 1 day!)", pd("1000Ks", "") is None)
    ck("some-custom-plan -> None (NOT 1 day!)", pd("My Custom 5GB", "PROFILE [USED:100]") is None)

    # --- known duration via [ORIG-UP:] tag (authoritative) ---
    ck("ORIG-UP:7d -> 7 days", pd("2GB", "PROFILE [ORIG-UP:7d] [USED:100]") == 7 * D)
    ck("ORIG-UP:30d -> 30 days", pd("", "PROFILE [ORIG-UP:30d]") == 30 * D)
    ck("ORIG-UP:2d4h -> 52h", pd("", "[ORIG-UP:2d4h]") == timedelta(days=2, hours=4))

    # --- known duration via clearly-encoded plan names ---
    ck("30Day plan -> 30 days", pd("1GB 30Day", "") == 30 * D)
    ck("7d plan -> 7 days", pd("1GB 7d", "") == 7 * D)
    ck("1Hour plan -> 1 hour", pd("1GB_1H", "") == timedelta(hours=1))

    # --- quota parser fail-safe ---
    ck("unknown quota -> 0 (NOT 1.66GB!)", po("My Custom 5GB", "") == 0)
    ck("tagged ORIG-LIMIT wins", po("whatever", "[ORIG-LIMIT:2147483648]") == 2147483648)
    ck("50GB name -> exact", po("50GB", "") == 53687091200)

    # --- RouterOS limit-uptime formatting ---
    ck("fmt 29d23h", fr(timedelta(days=29, hours=23)) == "29d23h")
    ck("fmt 1h5m", fr(timedelta(hours=1, minutes=5)) == "1h5m")
    ck("fmt 45m", fr(timedelta(minutes=45)) == "45m")

    fails = [n for n, ok in checks if not ok]
    print(f"\n{len(checks) - len(fails)}/{len(checks)} checks passed")
    if fails:
        print("FAILED:", fails)
        sys.exit(1)
    print("OK: long-term vouchers are now protected from premature expiry.")


if __name__ == "__main__":
    main()