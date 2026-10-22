import sys, os, json, time, threading, traceback
from datetime import datetime, timedelta
sys.path.append('/opt/hotspot-cloud')
try:
    import cloud_database as db
except ImportError:
    db = None
from cloud_mikrotik import RouterOSClient

# V3: AIRTIGHT QUOTA & TIME ENFORCER
# RouterOS kernel does the cutting; VPS is backup-only sweeper.

VOUCHER_ACTIVATE_SOURCE = """# V3 Airtight Voucher Activation - REMAINING as hard limit
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
# REJECT disabled/expired/exhausted
:if ($isDis = true or [:find $curComm "[EXPIRED:"] >= 0 or [:find $curComm "[EXHAUSTED:"] >= 0) do={
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 limit-uptime=1s;
  :return "";
};
# RESOLVE ORIG LIMIT
:local origLim 0;
:local lStr [$parseTag comm=$curComm tag="[ORIG-LIMIT:"];
:if ([:len $lStr] > 0) do={ :set origLim [:tonum $lStr] };
:if ($origLim = 0) do={
  :local cU ($profName . " " . $curComm);
  :if ($cU ~ "100GB") do={ :set origLim 107374182400 };
  :if ($cU ~ "50GB") do={ :set origLim 53687091200 };
  :if ($cU ~ "40G") do={ :set origLim 42949672960 };
  :if ($cU ~ "30GB" or $cU ~ "30Day") do={ :set origLim 32212254720 };
  :if ($cU ~ "20GB") do={ :set origLim 21474836480 };
  :if ($cU ~ "10GB") do={ :set origLim 10737418240 };
  :if ($cU ~ "5GB") do={ :set origLim 5368709120 };
  :if ($cU ~ "4GB") do={ :set origLim 4294967296 };
  :if ($cU ~ "3GB") do={ :set origLim 3221225472 };
  :if ($cU ~ "2GB") do={ :set origLim 2147483648 };
  :if ($cU ~ "1.5GB" or $cU ~ "1000Ks") do={ :set origLim 1782579200 };
  :if ($cU ~ "1GB") do={ :set origLim 1073741824 };
  :if ($cU ~ "750MB" or $cU ~ "500Ks") do={ :set origLim 786432000 };
  :if ($cU ~ "500MB") do={ :set origLim 524288000 };
  :if ($cU ~ "250MB") do={ :set origLim 262144000 };
  :if ($cU ~ "100MB") do={ :set origLim 104857600 };
  :if ($cU ~ "50MB" or $cU ~ "test") do={ :set origLim 52428800 };
  :if ($origLim = 0) do={
    :local curLim [/ip hotspot user get $uObj limit-bytes-total];
    :if ($curLim > 0) do={ :set origLim $curLim };
  };
  :if ($origLim > 0) do={
    :set curComm [$setTag comm=$curComm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
  };
};
# CALCULATE USED from [USED:] + hw counters
:local usedBytes 0;
:local udStr [$parseTag comm=$curComm tag="[USED:"];
:if ([:len $udStr] > 0) do={ :set usedBytes [:tonum $udStr] };
:local hwIn [/ip hotspot user get $uObj bytes-in];
:local hwOut [/ip hotspot user get $uObj bytes-out];
:local baseBytes 0;
:local bStr [$parseTag comm=$curComm tag="[BASE:"];
:if ([:len $bStr] > 0) do={ :set baseBytes [:tonum $bStr] };
:local hwTotal ($baseBytes + $hwIn + $hwOut);
:if ($hwTotal > $usedBytes) do={ :set usedBytes $hwTotal };
# REJECT if exhausted
:if ($origLim > 0 and $usedBytes >= $origLim) do={
  /ip hotspot active remove [find user=$u];
  /ip hotspot cookie remove [find user=$u];
  :set curComm [$setTag comm=$curComm tag="[USED:" val=[:tostr $usedBytes]];
  /ip hotspot user set $uObj disabled=yes limit-bytes-total=1 comment=($curComm . " [EXHAUSTED:DATA]");
  :return "";
};
# KEY FIX: limit-bytes-total = REMAINING so RouterOS kernel cuts exactly
:set curComm [$setTag comm=$curComm tag="[BASE:" val=[:tostr $usedBytes]];
:set curComm [$setTag comm=$curComm tag="[USED:" val=[:tostr $usedBytes]];
:if ($origLim > 0) do={
  :local remBytes ($origLim - $usedBytes);
  /ip hotspot user set $uObj limit-bytes-total=$remBytes;
  /ip hotspot user reset-counters $uObj;
};
# ACTIVATION TIME (first login = continuous countdown start)
:local cDate [/system clock get date];
:local cTime [/system clock get time];
:local isFirst true;
:if ([:find $curComm "[ACT:"] >= 0) do={ :set isFirst false };
:if ($isFirst) do={
  :set curComm [$setTag comm=$curComm tag="[ACT:" val=([:tostr $cDate] . " " . [:tostr $cTime])];
};
# RESOLVE & SET REMAINING UPTIME
:local origUp [:totime "0s"];
:local upStr [$parseTag comm=$curComm tag="[ORIG-UP:"];
:if ([:len $upStr] > 0) do={ :set origUp [:totime $upStr] };
:if ($origUp = [:totime "0s"]) do={
  :local p ($profName . " " . $curComm);
  :if ($p ~ "30d" or $p ~ "30Day" or $p ~ "4w") do={ :set origUp 30d };
  :if ($p ~ "7d" or $p ~ "1w") do={ :set origUp 7d };
  :if ($p ~ "3d") do={ :set origUp 3d };
  :if ($p ~ "1d" or $p ~ "24h") do={ :set origUp 1d };
  :if ($p ~ "12h") do={ :set origUp 12h };
  :if ($p ~ "6h") do={ :set origUp 6h };
  :if ($p ~ "2h") do={ :set origUp 2h };
  :if ($p ~ "1h") do={ :set origUp 1h };
  :if ($p ~ "test") do={ :set origUp 3m };
  # Authoritative fallback: use the profile's own session-timeout (true plan
  # validity) for plans whose names don't encode a duration (long-term plans!).
  :if ($origUp = [:totime "0s"]) do={
    :local upProf [/ip hotspot user/profile get [find name=$profName] session-timeout];
    :if ([:len $upProf] > 0 and $upProf > [:totime "0s"]) do={ :set origUp $upProf };
  };
  :if ($origUp > [:totime "0s"]) do={
    :set curComm [$setTag comm=$curComm tag="[ORIG-UP:" val=[:tostr $origUp]];
  };
};
:if ($origUp > [:totime "0s"]) do={
  /ip hotspot user set $uObj limit-uptime=$origUp;
};
# RECORD MAC for roaming (don't lock user mac-address)
:local m "";
:foreach a in=[/ip hotspot active find user=$u] do={
  :set m [/ip hotspot active get $a mac-address];
};
:if ([:len $m] > 0) do={
  :set curComm [$setTag comm=$curComm tag="[MAC:" val=$m];
};
# Ensure uptime tracking tags exist (preserve existing accumulated values)
:if ([:find $curComm "[BASE-UP:"] < 0) do={ :set curComm ($curComm . " [BASE-UP:0]") };
:if ([:find $curComm "[USED-UP:"] < 0) do={ :set curComm ($curComm . " [USED-UP:0]") };
/ip hotspot user set $uObj comment=$curComm;
"""

HS_QUOTA_SAVE_SOURCE = """# V3 Lightweight Quota Saver - writes only when delta >= 5MB
:foreach a in=[/ip hotspot active find] do={
  :local uName [/ip hotspot active get $a user];
  :if ($uName != "admin" and $uName != "default-trial") do={
    :local uList [/ip hotspot user find name=$uName];
    :if ([:len $uList] > 0) do={
      :local u [:pick $uList 0];
      :local sB ([/ip hotspot active get $a bytes-in] + [/ip hotspot active get $a bytes-out]);
      :local comm [/ip hotspot user get $u comment];
      :local baseBytes 0;
      :local bIdx [:find $comm "[BASE:"];
      :if ($bIdx >= 0) do={
        :local bR [:pick $comm ($bIdx + 6) [:len $comm]];
        :local bE [:find $bR "]"];
        :if ($bE >= 0) do={ :set bR [:pick $bR 0 $bE] };
        :set baseBytes [:tonum $bR];
      };
      :local curUsed 0;
      :local uIdx [:find $comm "[USED:"];
      :if ($uIdx >= 0) do={
        :local uR [:pick $comm ($uIdx + 6) [:len $comm]];
        :local uE [:find $uR "]"];
        :if ($uE >= 0) do={ :set uR [:pick $uR 0 $uE] };
        :set curUsed [:tonum $uR];
      };
      :local totalUsed ($baseBytes + $sB);
      :local origLim 0;
      :local lIdx [:find $comm "[ORIG-LIMIT:"];
      :if ($lIdx >= 0) do={
        :local lR [:pick $comm ($lIdx + 12) [:len $comm]];
        :local lE [:find $lR "]"];
        :if ($lE >= 0) do={ :set lR [:pick $lR 0 $lE] };
        :set origLim [:tonum $lR];
      };
      # EXACT CUTOFF: if totalUsed >= origLim, kick NOW
      :if ($origLim > 0 and $totalUsed >= $origLim) do={
        /ip hotspot active remove $a;
        /ip hotspot cookie remove [find user=$uName];
        :local nC $comm;
        :local ui [:find $nC "[USED:"];
        :if ($ui >= 0) do={
          :local eI [:find $nC "]" $ui];
          :local px [:pick $nC 0 $ui];
          :local sx "";
          :if ($eI >= 0) do={ :set sx [:pick $nC ($eI+1) [:len $nC]] };
          :set nC ($px . "[USED:" . [:tostr $totalUsed] . "]" . $sx);
        } else={ :set nC ($nC . " [USED:" . [:tostr $totalUsed] . "]") };
        /ip hotspot user set $u disabled=yes limit-bytes-total=1 comment=($nC . " [EXHAUSTED:DATA]");
      } else={
        # Accumulate session uptime into [USED-UP:]
        :local hwUpSec [:totime [/ip hotspot active get $a uptime]];
        :local baseUpSec 0;
        :local bUpIdx [:find $comm "[BASE-UP:"];
        :if ($bUpIdx >= 0) do={
          :local bUpR [:pick $comm ($bUpIdx + 8) [:len $comm]];
          :local bUpE [:find $bUpR "]"];
          :if ($bUpE >= 0) do={ :set bUpR [:pick $bUpR 0 $bUpE] };
          :set baseUpSec [:tonum $bUpR];
        };
        :local totalUpSec ($baseUpSec + $hwUpSec);
        :local uuIdx [:find $comm "[USED-UP:"];
        :if ($uuIdx >= 0) do={
          :local eI [:find $comm "]" $uuIdx];
          :local px [:pick $comm 0 $uuIdx];
          :local sx "";
          :if ($eI >= 0) do={ :set sx [:pick $comm ($eI+1) [:len $comm]] };
          :set comm ($px . "[USED-UP:" . [:tostr $totalUpSec] . "]" . $sx);
        } else={ :set comm ($comm . " [USED-UP:" . [:tostr $totalUpSec] . "]") };
        # Only write to flash if delta >= 5MB (5242880 bytes)
        :local delta ($totalUsed - $curUsed);
        :if ($delta < 0) do={ :set delta (0 - $delta) };
        :if ($delta > 5242880) do={
          :local nC $comm;
          :local ui [:find $nC "[USED:"];
          :if ($ui >= 0) do={
            :local eI [:find $nC "]" $ui];
            :local px [:pick $nC 0 $ui];
            :local sx "";
            :if ($eI >= 0) do={ :set sx [:pick $nC ($eI+1) [:len $nC]] };
            :set nC ($px . "[USED:" . [:tostr $totalUsed] . "]" . $sx);
          } else={ :set nC ($nC . " [USED:" . [:tostr $totalUsed] . "]") };
          /ip hotspot user set $u comment=$nC;
        } else={
          /ip hotspot user set $u comment=$comm;
        };
      };
    };
  };
};
"""

VOUCHER_EXPIRE_CHECK_SOURCE = """# V3 Expired/exhausted session garbage collector
:foreach a in=[/ip hotspot active find] do={
  :local aUser [/ip hotspot active get $a user];
  :if ($aUser != "admin" and $aUser != "default-trial") do={
    :local uList [/ip hotspot user find name=$aUser];
    :if ([:len $uList] = 0) do={
      :do { /ip hotspot active remove $a; /ip hotspot cookie remove [find user=$aUser] } on-error={};
    } else={
      :local uObj [:pick $uList 0];
      :local isDis [/ip hotspot user get $uObj disabled];
      :local comm [/ip hotspot user get $uObj comment];
      :if ($isDis = true or [:find $comm "[EXHAUSTED:"] >= 0 or [:find $comm "[EXPIRED:"] >= 0) do={
        :do { /ip hotspot active remove $a; /ip hotspot cookie remove [find user=$aUser] } on-error={};
      };
    };
  };
};
:foreach s in=[/system scheduler find comment~"Voucher continuous timer"] do={
  :local sName [/system scheduler get $s name];
  :local uList [/ip hotspot user find name=$sName];
  :if ([:len $uList] = 0) do={ :do { /system scheduler remove $s } on-error={} };
};
"""

HS_ON_LOGOUT_SOURCE = """# V3 On-Logout: Save final bytes to [USED:] and [BASE:]
:global hsUser;
:local u $hsUser;
:if ([:len $u] = 0) do={ :set u $user };
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
      :local bR [:pick $comm ($bIdx + 6) [:len $comm]];
      :local bE [:find $bR "]"];
      :if ($bE >= 0) do={ :set bR [:pick $bR 0 $bE] };
      :set base [:tonum $bR];
    };
    :local total ($base + $hwTotal);
    # Update [USED:]
    :local uIdx [:find $comm "[USED:"];
    :if ($uIdx >= 0) do={
      :local eI [:find $comm "]" $uIdx];
      :local px [:pick $comm 0 $uIdx];
      :local sx "";
      :if ($eI >= 0) do={ :set sx [:pick $comm ($eI+1) [:len $comm]] };
      :set comm ($px . "[USED:" . [:tostr $total] . "]" . $sx);
    } else={ :set comm ($comm . " [USED:" . [:tostr $total] . "]") };
    # Update [BASE:]
    :set bIdx [:find $comm "[BASE:"];
    :if ($bIdx >= 0) do={
      :local eI [:find $comm "]" $bIdx];
      :local px [:pick $comm 0 $bIdx];
      :local sx "";
      :if ($eI >= 0) do={ :set sx [:pick $comm ($eI+1) [:len $comm]] };
      :set comm ($px . "[BASE:" . [:tostr $total] . "]" . $sx);
    } else={ :set comm ($comm . " [BASE:" . [:tostr $total] . "]") };
    # Accumulate final uptime into [USED-UP:] and update [BASE-UP:] for next session
    :local hwUp [/ip hotspot user get $uObj uptime];
    :local baseUpSec 0;
    :local bUpIdx [:find $comm "[BASE-UP:"];
    :if ($bUpIdx >= 0) do={
      :local bUpR [:pick $comm ($bUpIdx + 8) [:len $comm]];
      :local bUpE [:find $bUpR "]"];
      :if ($bUpE >= 0) do={ :set bUpR [:pick $bUpR 0 $bUpE] };
      :set baseUpSec [:tonum $bUpR];
    };
    :local sessionUpSec [:totime $hwUp];
    :local totalUpSec ($baseUpSec + $sessionUpSec);
    :local uuIdx [:find $comm "[USED-UP:"];
    :if ($uuIdx >= 0) do={
      :local eI [:find $comm "]" $uuIdx];
      :local px [:pick $comm 0 $uuIdx];
      :local sx "";
      :if ($eI >= 0) do={ :set sx [:pick $comm ($eI+1) [:len $comm]] };
      :set comm ($px . "[USED-UP:" . [:tostr $totalUpSec] . "]" . $sx);
    } else={ :set comm ($comm . " [USED-UP:" . [:tostr $totalUpSec] . "]") };
    :local bbIdx [:find $comm "[BASE-UP:"];
    :if ($bbIdx >= 0) do={
      :local eI2 [:find $comm "]" $bbIdx];
      :local px2 [:pick $comm 0 $bbIdx];
      :local sx2 "";
      :if ($eI2 >= 0) do={ :set sx2 [:pick $comm ($eI2+1) [:len $comm]] };
      :set comm ($px2 . "[BASE-UP:" . [:tostr $totalUpSec] . "]" . $sx2);
    } else={ :set comm ($comm . " [BASE-UP:" . [:tostr $totalUpSec] . "]") };
    /ip hotspot user set $uObj comment=$comm;
  };
};
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
                    issues.append("API Authentication failed.")
                else:
                    def parse_re(r):
                        res = {}
                        for item in r[1:]:
                            if item.startswith('='):
                                parts = item[1:].split('=', 1)
                                if len(parts) == 2:
                                    res[parts[0]] = parts[1]
                        return res

                    def apply_script(name, source, comment=""):
                        s_res = client.talk(['/system/script/print', f'?name={name}'])
                        existing = [parse_re(x) for x in s_res if x[0] == '!re']
                        if existing:
                            sid = existing[0].get('.id')
                            if sid:
                                client.talk(['/system/script/set', f'=.id={sid}', f'=source={source}', f'=comment={comment}'])
                        else:
                            client.talk(['/system/script/add', f'=name={name}', f'=source={source}', f'=comment={comment}'])
                        fixes.append(f"Script '{name}' deployed.")

                    # 1. Deploy V3 scripts
                    apply_script('voucher-activate', VOUCHER_ACTIVATE_SOURCE, "V3 Airtight Activation")
                    apply_script('hs-quota-save', HS_QUOTA_SAVE_SOURCE, "V3 Lightweight Quota Saver")
                    apply_script('voucher-expire-check', VOUCHER_EXPIRE_CHECK_SOURCE, "V3 Expire GC")
                    apply_script('hs-on-logout', HS_ON_LOGOUT_SOURCE, "V3 On Logout Saver")

                    # 2. Ensure schedulers exist
                    def ensure_scheduler(name, interval, on_event, comment=""):
                        sc = client.talk(['/system/scheduler/print', f'?name={name}'])
                        sc_list = [parse_re(x) for x in sc if x[0] == '!re']
                        if sc_list:
                            sid = sc_list[0].get('.id')
                            if sid:
                                client.talk(['/system/scheduler/set', f'=.id={sid}', f'=interval={interval}', f'=on-event={on_event}'])
                        else:
                            client.talk(['/system/scheduler/add', f'=name={name}', f'=interval={interval}', f'=on-event={on_event}', '=start-time=startup', f'=comment={comment}'])

                    ensure_scheduler('hs-quota-saver', '30s', '/system script run hs-quota-save', 'V3 quota saver')
                    ensure_scheduler('hs-continuous-expire-monitor', '1m', '/system script run voucher-expire-check', 'V3 expire monitor')

                    # 3. Set profile hooks: on-login → voucher-activate, on-logout → on-logout saver
                    on_login = ':global hsUser $user; :do { /system script run voucher-activate } on-error={}'
                    on_logout = ':do { /system script run hs-on-logout } on-error={}'
                    profiles = client.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name'])
                    for p in [parse_re(x) for x in profiles if x[0] == '!re']:
                        pid = p.get('.id')
                        if pid:
                            client.talk([
                                '/ip/hotspot/user/profile/set', f'=.id={pid}',
                                '=shared-users=1', '=add-mac-cookie=yes', '=mac-cookie-timeout=30d',
                                '=keepalive-timeout=2m', '=idle-timeout=3m',
                                f'=on-login={on_login}', f'=on-logout={on_logout}'
                            ])
                    fixes.append("All profiles updated with V3 hooks.")

                    # 4. Remove per-user schedulers (CPU spike killers)
                    sys_names = {'hs-quota-saver', 'hs-continuous-expire-monitor', 'cloud-heartbeat', 'cloud_heartbeat', 'hs-quota-restorer'}
                    all_sc = client.talk(['/system/scheduler/print', '=.proplist=.id,name'])
                    for sc in [parse_re(x) for x in all_sc if x[0] == '!re']:
                        sn = sc.get('name', '')
                        if sn and sn not in sys_names:
                            client.talk(['/system/scheduler/remove', f'=.id={sc.get(".id")}'])
                            fixes.append(f"Removed per-user scheduler: {sn}")

                    # 5. Unbind MACs if requested (fresh login enforcement)
                    if unbind_macs:
                        users = client.talk(['/ip/hotspot/user/print', '=.proplist=.id,name,mac-address,disabled'])
                        for u in [parse_re(x) for x in users if x[0] == '!re']:
                            mac = u.get('mac-address', '')
                            if mac and u.get('disabled') != 'true':
                                client.talk(['/ip/hotspot/user/set', f'=.id={u.get(".id")}', '=mac-address='])
                        client.talk(['/system/script/add', '=name=purge-now', '=source=/ip hotspot cookie remove [find]; /ip hotspot active remove [find]'])
                        client.talk(['/system/script/run', '=number=purge-now'])
                        client.talk(['/system/script/remove', '=numbers=purge-now'])
                        fixes.append("Unbound all MACs and purged sessions.")

            except Exception as fe:
                issues.append(f"Remediation connection warning: {fe}")
            finally:
                client.close()

        is_healthy = (len(issues) == 0)
        status_label = "HEALTHY" if is_healthy else "NEEDS_FIX"
        summary_text = "V3 airtight rules compliant." if is_healthy else f"Enforcer encountered: {', '.join(issues)}"

        if db:
            try:
                conn = db.get_db()
                c = conn.cursor()
                c.execute("""CREATE TABLE IF NOT EXISTS router_audits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    router_id INTEGER NOT NULL, router_name TEXT,
                    timestamp INTEGER NOT NULL, is_healthy INTEGER NOT NULL,
                    issues_json TEXT NOT NULL, fixes_json TEXT NOT NULL,
                    summary TEXT NOT NULL);""")
                c.execute("INSERT INTO router_audits (router_id, router_name, timestamp, is_healthy, issues_json, fixes_json, summary) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (self.router_id, self.router_name, now, 1 if is_healthy else 0, json.dumps(issues), json.dumps(fixes), summary_text))
                c.execute("UPDATE routers SET last_audit_time = ?, audit_status = ?, audit_summary = ? WHERE id = ?",
                    (now, status_label, summary_text, self.router_id))
                conn.commit()
                conn.close()
            except Exception as dbe:
                print(f"DB error: {dbe}")

        return {"router_id": self.router_id, "router_name": self.router_name, "timestamp": now,
                "healthy": is_healthy, "status": status_label, "issues": issues, "fixes": fixes, "summary": summary_text}

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
    auditor = RouterAuditor(dict(row))
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
        results.append(auditor.audit_and_fix(auto_fix=auto_fix, unbind_macs=unbind_macs))
    return results

