#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Repair runner executed ON the VPS: runs the fixed offline sweep (which also
auto-repairs wrongly-disabled long-term vouchers) against every router."""
import sys

sys.path.append('/opt/hotspot-cloud')
import automated_enforcer_v3 as ae

conn = ae.db.get_db()
cur = conn.cursor()
cur.execute('SELECT * FROM routers')
rows = [dict(r) for r in cur.fetchall()]
conn.close()

print("Repair sweep starting over", len(rows), "router(s)", flush=True)
done = 0
for r in rows:
    try:
        ae.run_offline_quota_sweep(r)
        done += 1
        print("swept router", r.get("id"), r.get("name"), flush=True)
    except Exception as e:
        print("ERR router", r.get("id"), r.get("name"), e, flush=True)
print("REPAIR SWEEP FINISHED routers=", done, flush=True)