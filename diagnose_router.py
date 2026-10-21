#!/usr/bin/env python3
"""
Diagnose MikroTik Router CPU Spike Issues
Check for per-user schedulers and configuration problems
"""

import sys
import os
sys.path.append('/opt/hotspot-cloud')

try:
    import cloud_mikrotik
except ImportError:
    print("Error: Could not import cloud_mikrotik module")
    sys.exit(1)

def diagnose_router(router_ip, router_port=8728, username='admin', password='Khant1234@'):
    """Diagnose a specific router for CPU spike issues"""
    try:
        client = cloud_mikrotik.RouterOSClient(router_ip, router_port, timeout=10)
        client.connect(username, password)
        
        print(f"\n=== Diagnosing Router {router_ip} ===")
        
        # 1. Check system resources
        resources = client.execute('/system/resource/print')
        if resources:
            cpu_load = resources[0].get('cpu-load', 'N/A')
            uptime = resources[0].get('uptime', 'N/A')
            free_memory = resources[0].get('free-memory', 'N/A')
            print(f"CPU Load: {cpu_load}%")
            print(f"Uptime: {uptime}")
            print(f"Free Memory: {free_memory} bytes")
        
        # 2. Check schedulers (main CPU spikers)
        schedulers = client.execute('/system/scheduler/print')
        print(f"\nTotal Schedulers: {len(schedulers)}")
        
        # Identify per-user schedulers
        user_schedulers = []
        system_schedulers = []
        
        for sched in schedulers:
            name = sched.get('name', '')
            if name and ('user' in name.lower() or name.isdigit()) and 'hs-' not in name:
                user_schedulers.append(sched)
            else:
                system_schedulers.append(sched)
        
        print(f"Per-user schedulers (CPU spikers): {len(user_schedulers)}")
        print(f"System schedulers: {len(system_schedulers)}")
        
        if user_schedulers:
            print("\n⚠️  WARNING: Found per-user schedulers that cause CPU spikes:")
            for sched in user_schedulers[:5]:  # Show first 5
                print(f"  - {sched.get('name')}: {sched.get('on-event', '')[:50]}...")
        
        # 3. Check hotspot users count
        users = client.execute('/ip/hotspot/user/print', '=.proplist=.id')
        print(f"\nHotspot Users: {len(users)}")
        
        # 4. Check active sessions
        active_sessions = client.execute('/ip/hotspot/active/print', '=.proplist=.id')
        print(f"Active Sessions: {len(active_sessions)}")
        
        # 5. Check scripts
        scripts = client.execute('/system/script/print', '=.proplist=.name')
        print(f"System Scripts: {len(scripts)}")
        
        client.close()
        return True
        
    except Exception as e:
        print(f"Error diagnosing router {router_ip}: {e}")
        return False

if __name__ == "__main__":
    # Diagnose all routers
    routers = [
        ('10.200.0.2', 'YadanarTun Wifi'),
        ('10.200.0.4', 'KyawGyi'),
        ('10.200.0.3', 'AYeikSitt1'),
        ('10.200.0.5', 'AYeikSitt2')
    ]
    
    for ip, name in routers:
        diagnose_router(ip)
        print("\n" + "="*50 + "\n")