import sys, os
import json
import time
from datetime import datetime, timedelta
import threading
import traceback

sys.path.append('/opt/hotspot-cloud')
try:
    import cloud_database as db
except ImportError:
    db = None

from cloud_mikrotik import RouterOSClient

# ═════════════════════════════════════════════════════════════════════════
#  ROUTER ECONOMIC & AIRTIGHT QUOTA ENFORCER
#  Applied automatically by Hotspot Cloud VPS Controller
# ═════════════════════════════════════════════════════════════════════════

VOUCHER_ACTIVATE_SOURCE = """# Airtight Voucher Activation & Continuous Timer Engine
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0 or $u = "admin" or $u = "default-trial") do={ :return "" };

:local uList [/ip hotspot user find name=$u];
:if ([:len $uList] = 0) do={ :return "" };
:local uObj [:pick $uList 0];

:local curComm [/ip hotspot user get $uObj comment];
:local profName [/ip hotspot user get $uObj profile];
:local isDis [/ip hotspot user get $uObj disabled];

:local parseTag do={
  :local idx [:find $comm $tag];
  :if ($idx >= 0) do={
    :local rest [:pick $comm ($idx + [:len $tag]) [:len $comm]];
    :local endIdx [:find $rest "]"];
    :if ($endIdx >= 0) do={ :set rest [:pick $rest 0 $endIdx] };
    :return $rest;
  };
  :return "";
};

:local setTag do={
  :local idx [:find $comm $tag];
  :if ($idx >= 0) do={
    :local endIdx [:find $comm "]" $idx];
    :local pfx [:pick $comm 0 $idx];
    :local sfx "";
    :if ($endIdx >= 0) do={ :set sfx [:pick $comm ($endIdx + 1) [:len $comm]] };
    :return ($pfx . $tag . $val . "]" . $sfx);
  } else={
    :return ($comm . " " . $tag . $val . "]");
  };
};

# 1. STRICT CHECK: If user is disabled or has EXPIRED/EXHAUSTED tags, REJECT IMMEDIATELY
:if ($isDis = true or [:find $curComm "[EXPIRED:"] >= 0 or [:find $curComm "[EXHAUSTED:"] >= 0) do={
  :log warning ("Hotspot: Rejected login for expired/exhausted user " . $u . ". Kicking immediately.");
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 limit-uptime=1s;
  :return "";
};

# 2. RESOLVE ORIGINAL QUOTA LIMIT
:local origLim 0;
:local lStr [$parseTag comm=$curComm tag="[ORIG-LIMIT:"];
:if ([:len $lStr] > 0) do={ :set origLim [:tonum $lStr] };
:if ($origLim = 0) do={
  :if ($profName = "50GB-30000" or $curComm ~ "50GB") do={ :set origLim 53687091200 };
  :if ($profName = "40G_30D" or $curComm ~ "40G") do={ :set origLim 42949672960 };
  :if ($profName = "30Day" or $curComm ~ "30Day") do={ :set origLim 32212254720 };
  :if ($profName = "4GB" or $curComm ~ "4GB") do={ :set origLim 4294967296 };
  :if ($profName = "2GB" or $curComm ~ "2GB") do={ :set origLim 2147483648 };
  :if ($profName = "1GB_1H" or $curComm ~ "1GB") do={ :set origLim 1073741824 };
  :if ($profName = "1000Ks" or $curComm ~ "1000Ks") do={ :set origLim 1782579200 };
  :if ($profName = "500Ks" or $curComm ~ "500Ks") do={ :set origLim 786432000 };
  :if ($profName = "test") do={ :set origLim 52428800 };
  :if ($profName = "default") do={ :set origLim 1073741824 };
  :set curComm [$setTag comm=$curComm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
};

# 3. QUOTA CHECK & HARDWARE REMAINING LIMIT
:local usedBytes 0;
:local udStr [$parseTag comm=$curComm tag="[USED:"];
:if ([:len $udStr] > 0) do={ :set usedBytes [:tonum $udStr] };

# Check hardware counters on user object in case they exceeded before reset
:local hwUsed ([/ip hotspot user get $uObj bytes-in] + [/ip hotspot user get $uObj bytes-out]);
:if ($hwUsed > $usedBytes) do={ :set usedBytes $hwUsed };

:local remBytes ($origLim - $usedBytes);
:if ($origLim > 0 and $remBytes <= 0) do={
  :log warning ("Hotspot: User " . $u . " quota already exhausted (" . [:tostr $usedBytes] . " >= " . [:tostr $origLim] . ")! Kicking.");
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 comment=($curComm . " [EXHAUSTED:DATA]");
  :return "";
};

# Record BASE as the starting point for this active session
:set curComm [$setTag comm=$curComm tag="[BASE:" val=[:tostr $usedBytes]];
:set curComm [$setTag comm=$curComm tag="[USED:" val=[:tostr $usedBytes]];

:if ($origLim > 0) do={
  /ip hotspot user set $uObj limit-bytes-total=$remBytes;
};
/ip hotspot user reset-counters $uObj;

# 4. RECORD MAC ADDRESS (Do NOT lock mac-address on user object to allow seamless re-login)
:local m "";
:foreach a in=[/ip hotspot active find user=$u] do={
  :set m [/ip hotspot active get $a mac-address];
};
:if ([:len $m] > 0) do={
  :set curComm [$setTag comm=$curComm tag="[MAC:" val=$m];
};

# 5. CONTINUOUS COUNTDOWN TIMER
:local isFirstLogin true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirstLogin false };
:if ([:len [/system scheduler find name=$u]] > 0) do={ :set isFirstLogin false };

:local cDate [/system clock get date];
:local cTime [/system clock get time];

:if ($isFirstLogin) do={
  :local vDur "1d";
  :if ($profName = "50GB-30000" or $profName = "30Day" or $profName = "40G_30D" or $curComm ~ "30D" or $curComm ~ "Month") do={ :set vDur "30d" };
  :if ($profName = "1GB_1H" or $profName ~ "1H" or $curComm ~ "1Hour" or $curComm ~ "1H") do={ :set vDur "1h" };
  :if ($profName ~ "2H" or $curComm ~ "2Hour" or $curComm ~ "2H") do={ :set vDur "2h" };
  :if ($profName = "test" or $curComm ~ "3M") do={ :set vDur "3m" };
  
  :set curComm [$setTag comm=$curComm tag="[ACT:" val=([:tostr $cDate] . " " . [:tostr $cTime])];
  /ip hotspot user set $uObj comment=$curComm;
  :log info ("Hotspot: Voucher " . $u . " activated! Validity: " . [:tostr $vDur] . " started at " . [:tostr $cDate] . " " . [:tostr $cTime]);

  :local schedEvt ("/ip hotspot active remove [find user=\\\"" . $u . "\\\"]; /ip hotspot cookie remove [find user=\\\"" . $u . "\\\"]; /ip hotspot user set [find name=\\\"" . $u . "\\\"] disabled=yes limit-uptime=1s limit-bytes-total=1 comment=([/ip hotspot user get [find name=\\\"" . $u . "\\\"] comment] . \\\" [EXPIRED:TIME_OVERDUE]\\\"); /system scheduler remove [find name=\\\"" . $u . "\\\"]");
  :local schedComm ("Voucher continuous timer: " . [:tostr $vDur] . " from " . [:tostr $cDate] . " " . [:tostr $cTime]);

  :do {
    /system scheduler add name=$u start-date=$cDate start-time=$cTime interval=$vDur on-event=$schedEvt comment=$schedComm;
  } on-error={
    :do {
      /system scheduler add name=$u start-time=startup interval=$vDur on-event=$schedEvt comment=($schedComm . " (fallback)");
    } on-error={};
  };
} else={
  /ip hotspot user set $uObj comment=$curComm;
};
"""

HS_QUOTA_SAVE_SOURCE = """# Airtight Realtime Quota Saver & Hardware Cutoff
:foreach a in=[/ip hotspot active find] do={
  :local uName [/ip hotspot active get $a user];
  :if ($uName != "admin" and $uName != "default-trial") do={
    :local uList [/ip hotspot user find name=$uName];
    :if ([:len $uList] > 0) do={
      :local u [:pick $uList 0];
      :local sessionBytes ([/ip hotspot active get $a bytes-in] + [/ip hotspot active get $a bytes-out]);
      :local comm [/ip hotspot user get $u comment];
      
      :local baseBytes 0;
      :local bIdx [:find $comm "[BASE:"];
      :if ($bIdx >= 0) do={
        :local bRest [:pick $comm ($bIdx + 6) [:len $comm]];
        :local bEnd [:find $bRest "]"];
        :if ($bEnd >= 0) do={ :set bRest [:pick $bRest 0 $bEnd] };
        :set baseBytes [:tonum $bRest];
      };

      :local curStoredUsed 0;
      :local uIdx [:find $comm "[USED:"];
      :if ($uIdx >= 0) do={
        :local uRest [:pick $comm ($uIdx + 6) [:len $comm]];
        :local uEnd [:find $uRest "]"];
        :if ($uEnd >= 0) do={ :set uRest [:pick $uRest 0 $uEnd] };
        :set curStoredUsed [:tonum $uRest];
      };

      # Promote stored used if baseBytes was missing or uninitialized
      :if ($baseBytes = 0 and $curStoredUsed > $sessionBytes) do={
        :set baseBytes $curStoredUsed;
        :local bEnd [:find $comm "]" $bIdx];
        :if ($bIdx >= 0) do={
          :local pfx [:pick $comm 0 $bIdx];
          :local sfx "";
          :if ($bEnd >= 0) do={ :set sfx [:pick $comm ($endIdx + 1) [:len $comm]] };
          :set comm ($pfx . "[BASE:" . [:tostr $baseBytes] . "]" . $sfx);
        } else={
          :set comm ($comm . " [BASE:" . [:tostr $baseBytes] . "]");
        };
      };
      
      :local totalUsed ($baseBytes + $sessionBytes);
      :local origLim 0;
      :local lIdx [:find $comm "[ORIG-LIMIT:"];
      :if ($lIdx >= 0) do={
        :local lRest [:pick $comm ($lIdx + 12) [:len $comm]];
        :local lEnd [:find $lRest "]"];
        :if ($lEnd >= 0) do={ :set lRest [:pick $lRest 0 $lEnd] };
        :set origLim [:tonum $lRest];
      };
      :if ($origLim = 0) do={
        :local profName [/ip hotspot user get $u profile];
        :if ($profName = "50GB-30000" or $comm ~ "50GB") do={ :set origLim 53687091200 };
        :if ($profName = "40G_30D" or $comm ~ "40G") do={ :set origLim 42949672960 };
        :if ($profName = "30Day" or $comm ~ "30Day") do={ :set origLim 32212254720 };
        :if ($profName = "4GB" or $comm ~ "4GB") do={ :set origLim 4294967296 };
        :if ($profName = "2GB" or $comm ~ "2GB") do={ :set origLim 2147483648 };
        :if ($profName = "1GB_1H" or $comm ~ "1GB") do={ :set origLim 1073741824 };
        :if ($profName = "1000Ks" or $comm ~ "1000Ks") do={ :set origLim 1782579200 };
        :if ($profName = "500Ks" or $comm ~ "500Ks") do={ :set origLim 786432000 };
        :if ($profName = "test") do={ :set origLim 52428800 };
        :if ($profName = "default") do={ :set origLim 1073741824 };
        :set comm ($comm . " [ORIG-LIMIT:" . [:tostr $origLim] . "]");
      };
      
      # Update [USED:...] in comment
      :set uIdx [:find $comm "[USED:"];
      :if ($uIdx >= 0) do={
        :local endIdx [:find $comm "]" $uIdx];
        :local prefix [:pick $comm 0 $uIdx];
        :local suffix "";
        :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
        :set comm ($prefix . "[USED:" . [:tostr $totalUsed] . "]" . $suffix);
      } else={
        :set comm ($comm . " [USED:" . [:tostr $totalUsed] . "]");
      };
      
      # EXACT DATA CUTOFF TRIGGER
      :if ($origLim > 0 and $totalUsed >= $origLim) do={
        :log warning ("Hotspot: Quota exceeded for " . $uName . " (" . [:tostr $totalUsed] . " >= " . [:tostr $origLim] . ") - KICKING AND DISABLING");
        /ip hotspot active remove $a;
        /ip hotspot cookie remove [find user=$uName];
        /ip hotspot user set $u disabled=yes limit-bytes-total=1 comment=($comm . " [EXHAUSTED:DATA]");
      } else={
        # Only write comment to flash if delta is >= 10MB to eliminate CPU/log churn
        :if (($totalUsed - $curStoredUsed) > 10485760 or ($curStoredUsed - $totalUsed) > 10485760) do={
          /ip hotspot user set $u comment=$comm;
        };
      };
    };
  };
};
"""

VOUCHER_EXPIRE_CHECK_SOURCE = """# Continuous monitor & garbage collector for expired/exhausted vouchers
:foreach a in=[/ip hotspot active find] do={
  :local aUser [/ip hotspot active get $a user];
  :if ($aUser != "admin" and $aUser != "default-trial") do={
    :local uList [/ip hotspot user find name=$aUser];
    :if ([:len $uList] = 0) do={
      :do {
        /ip hotspot active remove $a;
        /ip hotspot cookie remove [find user=$aUser];
      } on-error={};
    } else={
      :local uObj [:pick $uList 0];
      :local isDis [/ip hotspot user get $uObj disabled];
      :local comm [/ip hotspot user get $uObj comment];
      :if ($isDis = true or [:find $comm "[EXHAUSTED:"] >= 0 or [:find $comm "[EXPIRED:"] >= 0) do={
        :log warning ("Hotspot: Active session found for disabled/expired user " . $aUser . " - Kicking immediately");
        :do {
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$aUser];
        } on-error={};
      };
    };
  };
};

:foreach s in=[/system scheduler find comment~"Voucher continuous timer"] do={
  :local sName [/system scheduler get $s name];
  :local uList [/ip hotspot user find name=$sName];
  :if ([:len $uList] = 0) do={
    :do { /system scheduler remove $s } on-error={};
  };
};
"""

HS_ON_LOGOUT_SOURCE = """# Save quota and sync base state on user logout
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0) do={
  :set u $user;
};
:if ([:len $u] > 0 and $u != "admin" and $u != "default-trial") do={
  :local uList [/ip hotspot user find name=$u];
  :if ([:len $uList] > 0) do={
    :local uObj [:pick $uList 0];
    :local comm [/ip hotspot user get $uObj comment];
    :local hwIn [/ip hotspot user get $uObj bytes-in];
    :local hwOut [/ip hotspot user get $uObj bytes-out];
    :local hwTotal ($hwIn + $hwOut);
    
    :local base 0;
    :local bIdx [:find $comm "[BASE:"];
    :if ($bIdx >= 0) do={
      :local bRest [:pick $comm ($bIdx + 6) [:len $comm]];
      :local bEnd [:find $bRest "]"];
      :if ($bEnd >= 0) do={ :set bRest [:pick $bRest 0 $bEnd] };
      :set base [:tonum $bRest];
    };
    
    :local total ($base + $hwTotal);
    :local uIdx [:find $comm "[USED:"];
    :if ($uIdx >= 0) do={
      :local endIdx [:find $comm "]" $uIdx];
      :local prefix [:pick $comm 0 $uIdx];
      :local suffix "";
      :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set comm ($prefix . "[USED:" . [:tostr $total] . "]" . $suffix);
    } else={
      :set comm ($comm . " [USED:" . [:tostr $total] . "]");
    };
    :set bIdx [:find $comm "[BASE:"];
    :if ($bIdx >= 0) do={
      :local endIdx [:find $comm "]" $bIdx];
      :local prefix [:pick $comm 0 $bIdx];
      :local suffix "";
      :if ($endIdx >= 0) do={ :set suffix [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set comm ($prefix . "[BASE:" . [:tostr $total] . "]" . $suffix);
    } else={
      :set comm ($comm . " [BASE:" . [:tostr $total] . "]");
    };
    /ip hotspot user set $uObj comment=$comm;
  };
};
:do { /system script run hs-quota-save } on-error={};
"""

class RouterAuditor:
    def __init__(self, router_dict):
        self.router = router_dict
        self.host = router_dict["wg_ip"]
        self.user = router_dict.get("api_user", "admin")
        self.pwd = router_dict.get("api_pass", "Khant1234@")
        self.router_id = router_dict["id"]
        self.router_name = router_dict["name"]

    def audit_and_fix(self, auto_fix=True, unbind_macs=False):
        now = int(time.time())
        issues = []
        fixes = []

        if auto_fix:
            client = RouterOSClient(self.host, port=8728, timeout=12.0)
            try:
                client.connect()
                if not client.login(self.user, self.pwd):
                    issues.append("API Authentication failed on router.")
                else:
                    def parse_re(r):
                        res = {}
                        for item in r[1:]:
                            if item.startswith('='):
                                parts = item[1:].split('=', 1)
                                if len(parts) == 2:
                                    res[parts[0]] = parts[1]
                        return res

                    # Helper to set or create script safely via API
                    def apply_script(name, source, comment=""):
                        s_res = client.talk(['/system/script/print', f'?name={name}'])
                        sid = None
                        for r in s_res:
                            if r[0] == '!re':
                                for item in r[1:]:
                                    if item.startswith('=.id='): sid = item[5:]
                        if sid:
                            client.talk(['/system/script/set', f'=.id={sid}', f'=source={source}', f'=comment={comment}'])
                        else:
                            client.talk(['/system/script/add', f'=name={name}', f'=source={source}', f'=comment={comment}', '=policy=ftp,reboot,read,write,policy,test,password,sniff,sensitive,romon'])

                    # 1. Enforce Airtight Scripts via API
                    apply_script('voucher-activate', VOUCHER_ACTIVATE_SOURCE, "Airtight Voucher Activation & Continuous Timer")
                    apply_script('hs-quota-save', HS_QUOTA_SAVE_SOURCE, "Airtight Realtime Quota Saver & Hardware Cutoff")
                    apply_script('voucher-expire-check', VOUCHER_EXPIRE_CHECK_SOURCE, "Garbage Collector & Disabled Cutter")
                    apply_script('hs-on-logout', HS_ON_LOGOUT_SOURCE, "On Logout Quota Saver")

                    # 2. Enforce Fast Schedulers (Quota: 3s, Continuous Expire: 1m)
                    def ensure_scheduler(name, interval, on_event, comment=""):
                        sc_res = client.talk(['/system/scheduler/print', f'?name={name}'])
                        sc_id = None
                        for r in sc_res:
                            if r[0] == '!re':
                                for item in r[1:]:
                                    if item.startswith('=.id='): sc_id = item[5:]
                        if sc_id:
                            client.talk(['/system/scheduler/set', f'=.id={sc_id}', f'=interval={interval}', f'=on-event={on_event}', '=disabled=no'])
                        else:
                            client.talk(['/system/scheduler/add', f'=name={name}', f'=interval={interval}', f'=on-event={on_event}', f'=comment={comment}', '=start-time=startup'])

                    ensure_scheduler('hs-quota-saver', '3s', '/system script run hs-quota-save', "Realtime Quota Saver (every 3s)")
                    ensure_scheduler('hs-continuous-expire-monitor', '1m', '/system script run voucher-expire-check', "Hotspot continuous expiration monitor (every 1m)")

                    # Clean up device-mode failing cloud-heartbeat scheduler if present
                    hb_res = client.talk(['/system/scheduler/print', '?name=cloud-heartbeat'])
                    for r in hb_res:
                        if r[0] == '!re':
                            for item in r[1:]:
                                if item.startswith('=.id='):
                                    client.talk(['/system/scheduler/remove', f'=numbers={item[5:]}'])

                    # 3. Enforce User Profiles
                    profiles = client.talk(['/ip/hotspot/user/profile/print'])
                    for r in profiles:
                        if r[0] == '!re':
                            d = parse_re(r)
                            pid = d.get('.id')
                            pname = d.get('name', '')
                            if pid and pname:
                                cookie_time = "1d"
                                sess_time = "1d"
                                if any(k in pname for k in ["50GB", "30D", "30Day", "Month", "40G"]):
                                    cookie_time = "4w2d"
                                    sess_time = "4w2d"
                                elif any(k in pname for k in ["1H", "1Hour"]):
                                    cookie_time = "1h"
                                    sess_time = "1h"
                                elif any(k in pname for k in ["2H", "2Hour"]):
                                    cookie_time = "2h"
                                    sess_time = "2h"

                                client.talk([
                                    '/ip/hotspot/user/profile/set',
                                    f'=.id={pid}',
                                    '=on-login=:global hsUser $user; :do { /system script run voucher-activate } on-error={}',
                                    '=on-logout=/system script run hs-on-logout',
                                    f'=session-timeout={sess_time}',
                                    f'=mac-cookie-timeout={cookie_time}',
                                    '=keepalive-timeout=2m',
                                    '=idle-timeout=3m',
                                    '=status-autorefresh=1m',
                                    '=shared-users=1',
                                    '=add-mac-cookie=yes'
                                ])

                    # 4. Enforce Reyee Hardware / APs / Bridges Bypassed
                    reyee_macs = [
                        ("98:4A:6B:AE:30:20", "Reyee EW3000GX AP - Primary"),
                        ("98:4A:6B:AE:30:28", "Reyee EW3000GX AP - Secondary BSSID/LAN"),
                        ("4C:49:68:2D:0D:7D", "Reyee EW3000GX AP 2 - Primary"),
                        ("4C:49:68:2D:0D:85", "Reyee EW3000GX AP 2 - Secondary BSSID/LAN"),
                        ("98:4A:6B:F3:89:CD", "Ruijie AirMetro 460G Bridge 1"),
                        ("98:4A:6B:F3:8B:BD", "Ruijie AirMetro 460G Bridge 2")
                    ]
                    for mac, desc in reyee_macs:
                        b_res = client.talk(['/ip/hotspot/ip-binding/print', f'?mac-address={mac}'])
                        has_binding = any(r[0] == '!re' for r in b_res)
                        if not has_binding:
                            client.talk(['/ip/hotspot/ip-binding/add', f'=mac-address={mac}', '=type=bypassed', f'=comment={desc}'])

                    # 5. Voucher Sweep / MAC Unbinding
                    clock = [parse_re(r) for r in client.talk(['/system/clock/print']) if r[0] == '!re'][0]
                    router_now_str = f"{clock.get('date')} {clock.get('time')}"
                    router_now = datetime.strptime(router_now_str, "%Y-%m-%d %H:%M:%S")

                    users = [parse_re(r) for r in client.talk(['/ip/hotspot/user/print']) if r[0] == '!re']
                    disabled_count = 0
                    unbound_count = 0

                    for u in users:
                        uid = u.get('.id')
                        name = u.get('name')
                        if name in ['admin', 'default-trial']:
                            continue
                        comm = u.get('comment', '')
                        prof = u.get('profile', '')
                        b_in = int(u.get('bytes-in', 0))
                        b_out = int(u.get('bytes-out', 0))
                        hw_used = b_in + b_out

                        orig_lim = 0
                        if '[ORIG-LIMIT:' in comm:
                            try: orig_lim = int(comm.split('[ORIG-LIMIT:')[1].split(']')[0])
                            except Exception: pass
                        if orig_lim == 0:
                            if '50GB' in prof or '50GB' in comm: orig_lim = 53687091200
                            elif '40G' in prof or '40G' in comm: orig_lim = 42949672960
                            elif '30D' in prof or '30Day' in prof or '30Day' in comm: orig_lim = 32212254720
                            elif '4GB' in prof or '4GB' in comm: orig_lim = 4294967296
                            elif '2GB' in prof or '2GB' in comm: orig_lim = 2147483648
                            elif '1000Ks' in prof or '1000Ks' in comm: orig_lim = 1782579200
                            elif '1GB' in prof or '1GB' in comm: orig_lim = 1073741824
                            elif '500Ks' in prof or '500Ks' in comm: orig_lim = 786432000
                            elif 'test' in prof: orig_lim = 52428800
                            else: orig_lim = 1073741824

                        stored_used = 0
                        if '[USED:' in comm:
                            try: stored_used = int(comm.split('[USED:')[1].split(']')[0])
                            except Exception: pass
                        total_used = max(stored_used, hw_used)

                        is_time_expired = False
                        if '[ACT:' in comm:
                            act_str = comm.split('[ACT:')[1].split(']')[0].strip()
                            try:
                                if '-' in act_str: act_dt = datetime.strptime(act_str, "%Y-%m-%d %H:%M:%S")
                                else: act_dt = datetime.strptime(act_str, "%b/%d/%Y %H:%M:%S")
                                dur = timedelta(days=1)
                                if '50GB' in prof or '30D' in prof or '30Day' in prof or 'Month' in prof: dur = timedelta(days=30)
                                elif '1H' in prof or '1Hour' in prof: dur = timedelta(hours=1)
                                elif '2H' in prof or '2Hour' in prof: dur = timedelta(hours=2)
                                elif 'test' in prof: dur = timedelta(minutes=3)
                                if router_now >= (act_dt + dur):
                                    is_time_expired = True
                            except Exception: pass

                        is_already_disabled = (u.get('disabled') == 'true')
                        is_quota_exhausted = (orig_lim > 0 and total_used >= orig_lim)
                        is_already_expired = ('[EXPIRED:' in comm or '[EXHAUSTED:' in comm)

                        if is_quota_exhausted or is_time_expired or is_already_expired:
                            if not is_already_disabled:
                                reason = "[EXHAUSTED:DATA]" if is_quota_exhausted else "[EXPIRED:TIME_OVERDUE]"
                                new_comm = comm
                                if reason not in new_comm: new_comm = f"{new_comm} {reason}"
                                client.talk([
                                    '/ip/hotspot/user/set',
                                    f'=.id={uid}',
                                    '=disabled=yes',
                                    '=limit-bytes-total=1',
                                    '=limit-uptime=1s',
                                    f'=comment={new_comm}'
                                ])
                                disabled_count += 1
                        elif unbind_macs:
                            has_mac_bound = bool(u.get('mac-address') or '[MAC:' in comm)
                            if has_mac_bound or is_already_disabled:
                                new_comm = comm
                                if '[MAC:' in new_comm:
                                    pfx = new_comm.split('[MAC:')[0]
                                    sfx = new_comm.split('[MAC:')[1]
                                    if ']' in sfx: sfx = sfx.split(']', 1)[1]
                                    new_comm = (pfx + sfx).strip()
                                if '[ORIG-LIMIT:' not in new_comm:
                                    new_comm = f"{new_comm} [ORIG-LIMIT:{orig_lim}]"
                                if '[BASE:' in new_comm:
                                    pfx = new_comm.split('[BASE:')[0]
                                    sfx = new_comm.split('[BASE:')[1].split(']', 1)[1]
                                    new_comm = f"{pfx}[BASE:{total_used}]{sfx}"
                                else:
                                    new_comm = f"{new_comm} [BASE:{total_used}]"
                                if '[USED:' in new_comm:
                                    pfx = new_comm.split('[USED:')[0]
                                    sfx = new_comm.split('[USED:')[1].split(']', 1)[1]
                                    new_comm = f"{pfx}[USED:{total_used}]{sfx}"
                                else:
                                    new_comm = f"{new_comm} [USED:{total_used}]"

                                rem_bytes = max(1048576, orig_lim - total_used)
                                client.talk([
                                    '/ip/hotspot/user/set',
                                    f'=.id={uid}',
                                    '=disabled=no',
                                    '=mac-address=00:00:00:00:00:00',
                                    f'=limit-bytes-total={rem_bytes}',
                                    f'=comment={new_comm}'
                                ])
                                client.talk(['/ip/hotspot/user/reset-counters', f'=.id={uid}'])
                                unbound_count += 1

                    if unbind_macs:
                        client.talk(['/system/script/add', '=name=purge-now', '=source=/ip hotspot cookie remove [find]; /ip hotspot active remove [find]'])
                        client.talk(['/system/script/run', '=number=purge-now'])
                        client.talk(['/system/script/remove', '=numbers=purge-now'])
                        fixes.append(f"Unbound MACs from {unbound_count} valid vouchers, purged all active sessions & cookies to enforce fresh login.")

                    fixes.append(f"Airtight quota engine enforced. Swept users: {disabled_count} disabled/expired.")
            except Exception as fe:
                issues.append(f"Remediation connection warning: {fe}")
            finally:
                client.close()

        is_healthy = (len(issues) == 0)
        status_label = "HEALTHY" if is_healthy else "NEEDS_FIX"
        summary_text = "All airtight rules compliant." if is_healthy else f"Enforcer encountered: {', '.join(issues)}"

        if db:
            try:
                conn = db.get_db()
                c = conn.cursor()
                c.execute("""
                CREATE TABLE IF NOT EXISTS router_audits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    router_id INTEGER NOT NULL,
                    router_name TEXT,
                    timestamp INTEGER NOT NULL,
                    is_healthy INTEGER NOT NULL,
                    issues_json TEXT NOT NULL,
                    fixes_json TEXT NOT NULL,
                    summary TEXT NOT NULL
                );
                """)
                c.execute("""
                INSERT INTO router_audits (router_id, router_name, timestamp, is_healthy, issues_json, fixes_json, summary)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (self.router_id, self.router_name, now, 1 if is_healthy else 0, json.dumps(issues), json.dumps(fixes), summary_text))
                c.execute("""
                UPDATE routers 
                SET last_audit_time = ?, audit_status = ?, audit_summary = ?
                WHERE id = ?
                """, (now, status_label, summary_text, self.router_id))
                conn.commit()
                conn.close()
            except Exception as dbe:
                print(f"Database error storing audit: {dbe}")

        return {
            "router_id": self.router_id,
            "router_name": self.router_name,
            "timestamp": now,
            "healthy": is_healthy,
            "status": status_label,
            "issues": issues,
            "fixes": fixes,
            "summary": summary_text
        }

def audit_router_by_id(router_id, auto_fix=True, unbind_macs=False):
    if not db:
        return {"status": "error", "message": "Database not available"}
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE id = ?", (router_id,))
    row = c.fetchone()
    conn.close()
    if not row:
        return {"status": "error", "message": f"Router {router_id} not found."}
    r_dict = dict(row)
    auditor = RouterAuditor(r_dict)
    return auditor.audit_and_fix(auto_fix=auto_fix, unbind_macs=unbind_macs)

def audit_router_async(router_id, auto_fix=True, unbind_macs=False):
    t = threading.Thread(target=audit_router_by_id, args=(router_id, auto_fix, unbind_macs), daemon=True)
    t.start()
    return t

def audit_all_online_routers(auto_fix=True, unbind_macs=False):
    if not db:
        return []
    conn = db.get_db()
    c = conn.cursor()
    c.execute("SELECT * FROM routers WHERE status = 'online'")
    rows = c.fetchall()
    conn.close()
    results = []
    for r in rows:
        auditor = RouterAuditor(dict(r))
        res = auditor.audit_and_fix(auto_fix=auto_fix, unbind_macs=unbind_macs)
        results.append(res)
    return results
