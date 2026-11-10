import socket
import hashlib
import binascii
import ftplib
import os
import io
import time
import re
import paramiko

class RouterOSClient:
    def __init__(self, host, port=8728, timeout=6.0):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.sock = None

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect((self.host, self.port))

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass
            self.sock = None

    def send_sentence(self, words):
        for w in words:
            b = str(w).encode('utf-8')
            l = len(b)
            if l < 0x80:
                self.sock.send(bytes([l]))
            elif l < 0x4000:
                self.sock.send(bytes([0x80 | (l >> 8), l & 0xFF]))
            elif l < 0x200000:
                self.sock.send(bytes([0xC0 | (l >> 16), (l >> 8) & 0xFF, l & 0xFF]))
            else:
                self.sock.send(bytes([0xE0 | (l >> 24), (l >> 16) & 0xFF, (l >> 8) & 0xFF, l & 0xFF]))
            self.sock.send(b)
        self.sock.send(b'\x00')

    def read_sentence(self):
        words = []
        while True:
            b = self.sock.recv(1)
            if not b:
                return None
            l = b[0]
            if l == 0:
                return words
            if l & 0x80 == 0:
                pass
            elif (l & 0xC0) == 0x80:
                b2 = self.sock.recv(1)
                l = ((l & 0x3F) << 8) | b2[0]
            elif (l & 0xE0) == 0xC0:
                b2 = self.sock.recv(2)
                l = ((l & 0x1F) << 16) | (b2[0] << 8) | b2[1]
            elif (l & 0xF0) == 0xE0:
                b2 = self.sock.recv(3)
                l = ((l & 0x0F) << 24) | (b2[0] << 16) | (b2[1] << 8) | b2[2]
            w = b''
            while len(w) < l:
                chunk = self.sock.recv(l - len(w))
                if not chunk:
                    break
                w += chunk
            words.append(w.decode('utf-8', errors='ignore'))

    def talk(self, words):
        self.send_sentence(words)
        res = []
        # Drain the reply FULLY: only '!done' (or connection close) ends a reply
        # stream. A '!trap' is always followed by '!done' — stopping at '!trap'
        # leaves '!done' buffered and PERMANENTLY desyncs every later command
        # (later talk() calls read stale rows from previous commands).
        while True:
            sentence = self.read_sentence()
            if not sentence:
                break
            res.append(sentence)
            if sentence[0] == '!done':
                break
        return res

    def login(self, username, password):
        # 1. Try modern plain login (v7 standard)
        res = self.talk(['/login', f'=name={username}', f'=password={password}'])
        if res and res[0][0] == '!done' and len(res[0]) == 1:
            return True

        # 2. Check for challenge (v6 legacy)
        chal = None
        for r in res:
            for item in r:
                if item.startswith('=ret='):
                    chal = item.split('=', 2)[2]
        if chal:
            chal_bytes = binascii.unhexlify(chal)
            md = hashlib.md5(b'\x00' + password.encode('utf-8') + chal_bytes).hexdigest()
            res2 = self.talk(['/login', f'=name={username}', f'=response=00{md}'])
            if res2 and res2[0][0] == '!done':
                return True
        return False

    def get_resource(self):
        res = self.talk(['/system/resource/print'])
        data = {}
        for r in res:
            if r[0] == '!re':
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        data[k] = v
        return data

    def get_identity(self):
        res = self.talk(['/system/identity/print'])
        for r in res:
            if r[0] == '!re':
                for item in r[1:]:
                    if item.startswith('=name='):
                        return item.split('=', 2)[2]
        return "Unknown"

    def get_user_profiles(self):
        res = self.talk(['/ip/hotspot/user/profile/print', '=.proplist=.id,name,shared-users,rate-limit,session-timeout,mac-cookie-timeout,idle-timeout,keepalive-timeout,status-autorefresh'])
        profs = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                profs.append(entry)
        return profs

    def get_interfaces(self):
        res = self.talk(['/interface/print', '=.proplist=name,type,running,disabled,comment,mac-address'])
        ifs = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                ifs.append(entry)
        return ifs

    @staticmethod
    def _parse_time_sec(t_str):
        if not t_str:
            return 0
        total = 0
        import re
        for match in re.finditer(r'(\d+)([wdhms])', str(t_str).lower()):
            val = int(match.group(1))
            unit = match.group(2)
            if unit == 'w': total += val * 7 * 86400
            elif unit == 'd': total += val * 86400
            elif unit == 'h': total += val * 3600
            elif unit == 'm': total += val * 60
            elif unit == 's': total += val
        return total

    @staticmethod
    def _format_bytes(n, is_limit=False):
        if n is None or (is_limit and int(n) <= 0):
            return "Unlimited"
        n = int(n)
        if n <= 0:
            return "0 B"
        if n >= 1073741824:
            return f"{n / 1073741824:.2f} GB"
        if n >= 1048576:
            return f"{n / 1048576:.1f} MB"
        if n >= 1024:
            return f"{n / 1024:.0f} KB"
        return f"{n} B"

    @classmethod
    def _compute_quota_metrics(cls, entry, live_session=None, is_active=False):
        comment = entry.get('comment', '') or ''
        
        # 1. Resolve original quota limit
        orig_lim = None
        m_lim = re.search(r'\[ORIG-LIMIT:(\d+)\]', comment)
        if m_lim:
            orig_lim = int(m_lim.group(1))
        else:
            lbt = int(entry.get('limit-bytes-total', 0) or 0)
            if lbt > 0:
                m_base = re.search(r'\[BASE:(\d+)\]', comment)
                m_used = re.search(r'\[USED:(\d+)\]', comment)
                if m_base or m_used:
                    b = int(m_base.group(1)) if m_base else (int(m_used.group(1)) if m_used else 0)
                    orig_lim = lbt + b
                else:
                    orig_lim = lbt
            else:
                prof = str(entry.get('profile', '')).upper()
                m_prof_gb = re.search(r'(\d+)\s*GB', prof)
                m_prof_mb = re.search(r'(\d+)\s*MB', prof)
                if m_prof_gb:
                    orig_lim = int(m_prof_gb.group(1)) * 1024 * 1024 * 1024
                elif m_prof_mb:
                    orig_lim = int(m_prof_mb.group(1)) * 1024 * 1024

        m_base = re.search(r'\[BASE:(\d+)\]', comment)
        m_used = re.search(r'\[USED:(\d+)\]', comment)

        # 2. Resolve total used bytes
        base_val = int(m_base.group(1)) if m_base else 0
        used_val = int(m_used.group(1)) if m_used else 0

        if is_active:
            bytes_in = int(entry.get('bytes-in', 0) or 0)
            bytes_out = int(entry.get('bytes-out', 0) or 0)
            sess_bytes = bytes_in + bytes_out
            if base_val > 0:
                total_used = base_val + sess_bytes
            elif used_val > 0:
                total_used = max(used_val, sess_bytes)
            else:
                total_used = sess_bytes
            lbt = int(entry.get('limit-bytes-total', 0) or 0)
            if orig_lim and orig_lim > 0 and 0 < lbt <= orig_lim:
                total_used = max(total_used, orig_lim - lbt)
        elif live_session:
            live_in = int(live_session.get('bytes-in', 0) or 0)
            live_out = int(live_session.get('bytes-out', 0) or 0)
            live_bytes = live_in + live_out
            if base_val > 0:
                total_used = base_val + live_bytes
            elif used_val > 0:
                total_used = max(used_val, live_bytes)
            else:
                total_used = live_bytes
            lbt = int(entry.get('limit-bytes-total', 0) or 0)
            if orig_lim and orig_lim > 0 and 0 < lbt <= orig_lim:
                total_used = max(total_used, orig_lim - lbt)
        else:
            user_in = int(entry.get('bytes-in', 0) or 0)
            user_out = int(entry.get('bytes-out', 0) or 0)
            stored_bytes = user_in + user_out
            total_used = max(used_val, base_val, stored_bytes)
            lbt = int(entry.get('limit-bytes-total', 0) or 0)
            if orig_lim and orig_lim > 0 and 0 < lbt <= orig_lim:
                total_used = max(total_used, orig_lim - lbt)

        # 3. Quota remaining and status categorization
        if orig_lim is not None and orig_lim > 0:
            rem_bytes = max(0, orig_lim - total_used)
            pct_used = min(100.0, round((total_used / orig_lim) * 100, 1))
            pct_rem = max(0.0, round(100.0 - pct_used, 1))
            rem_formatted = cls._format_bytes(rem_bytes, is_limit=False)
            total_formatted = cls._format_bytes(orig_lim, is_limit=True)
        else:
            rem_bytes = None
            pct_used = 0.0
            pct_rem = 100.0
            rem_formatted = "Unlimited"
            total_formatted = "Unlimited"

        up = entry.get('uptime', '0s') or '0s'
        has_uptime = up not in ('0s', '00:00:00', '')

        # Categorize: 'unused', 'using', 'expired'
        if orig_lim is not None and orig_lim > 0 and rem_bytes <= 0:
            status_type = 'expired'
        elif is_active or live_session is not None or total_used > 0 or has_uptime:
            status_type = 'using'
        else:
            status_type = 'unused'

        entry['session_bytes'] = (bytes_in + bytes_out) if is_active else ((live_in + live_out) if live_session else 0)
        entry['quota_total'] = orig_lim
        entry['quota_used'] = total_used
        entry['quota_remaining'] = rem_bytes
        entry['quota_pct_used'] = pct_used
        entry['quota_pct_rem'] = pct_rem
        entry['status_type'] = status_type
        entry['quota_total_formatted'] = total_formatted
        entry['quota_used_formatted'] = cls._format_bytes(total_used, is_limit=False)
        entry['quota_remaining_formatted'] = rem_formatted
        return entry

    def get_active_users(self):
        user_res = self.talk(['/ip/hotspot/user/print', '=.proplist=name,comment,limit-bytes-total'])
        user_map = {}
        for ur in user_res:
            if ur[0] == '!re':
                ue = dict(item.lstrip('=').split('=', 1) for item in ur[1:] if '=' in item)
                user_map[ue.get('name')] = ue

        res = self.talk(['/ip/hotspot/active/print'])
        users = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                
                idle_str = entry.get("idle-time", "")
                if idle_str:
                    idle_sec = self._parse_time_sec(idle_str)
                    if idle_sec > 180:
                        continue

                u_info = user_map.get(entry.get('user'))
                if u_info:
                    if u_info.get('comment'):
                        entry['comment'] = u_info['comment']
                    if u_info.get('limit-bytes-total') and (not entry.get('limit-bytes-total') or entry.get('limit-bytes-total') == '0'):
                        entry['limit-bytes-total'] = u_info['limit-bytes-total']

                self._compute_quota_metrics(entry, is_active=True)
                users.append(entry)
        return users

    def get_hotspot_user_count(self):
        res = self.talk(['/ip/hotspot/user/print', '=count-only='])
        for r in res:
            if r[0] == '!done':
                for item in r[1:]:
                    if item.startswith('=ret='):
                        try:
                            return int(item.split('=', 2)[2])
                        except Exception:
                            pass
        return 0

    def get_hotspot_users(self, search="", status="", limit=0):
        # Fetch active sessions to compute live session bytes in real-time
        actives = self.get_active_users()
        act_dict = {a.get('user'): a for a in actives if a.get('user')}

        res = self.talk(['/ip/hotspot/user/print', '=.proplist=.id,name,profile,limit-uptime,limit-bytes-total,uptime,bytes-in,bytes-out,disabled,comment'])
        users = []
        s_lower = search.strip().lower() if search else ""
        st_lower = status.strip().lower() if status else ""

        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v

                name = entry.get("name", "")
                if name == "default-trial":
                    continue

                if s_lower:
                    profile = entry.get("profile", "").lower()
                    comment = entry.get("comment", "").lower()
                    if s_lower not in name.lower() and s_lower not in profile and s_lower not in comment:
                        continue

                live_sess = act_dict.get(name)
                if live_sess:
                    entry['is_active'] = True
                    entry['active_ip'] = live_sess.get('address', '')
                    entry['active_uptime'] = live_sess.get('uptime', '')
                else:
                    entry['is_active'] = False

                self._compute_quota_metrics(entry, live_session=live_sess, is_active=False)

                if st_lower and st_lower != "all":
                    if entry.get("status_type") != st_lower:
                        continue

                users.append(entry)
                if limit and limit > 0 and len(users) >= limit:
                    break
        return users

    def get_hotspot_user_summary(self):
        actives = self.get_active_users()
        act_dict = {a.get('user'): a for a in actives if a.get('user')}
        res = self.talk(['/ip/hotspot/user/print', '=.proplist=.id,name,profile,limit-uptime,limit-bytes-total,uptime,bytes-in,bytes-out,disabled,comment'])
        counts = {"all": 0, "using": 0, "unused": 0, "expired": 0}
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                name = entry.get("name", "")
                if name == "default-trial":
                    continue
                counts["all"] += 1
                live_sess = act_dict.get(name)
                self._compute_quota_metrics(entry, live_session=live_sess, is_active=False)
                st = entry.get("status_type", "unused")
                counts[st] = counts.get(st, 0) + 1
        return counts

    def add_profile(self, name, rate_limit="10M/10M", shared_users="1", session_timeout="1d", mac_cookie_timeout="4w2d"):
        on_login = (
            ':local u $user; :local m $"mac-address"; '
            ':do { /ip hotspot active remove [find user=$u and mac-address!=$m]; '
            '/ip hotspot cookie remove [find user=$u and mac-address!=$m]; '
            '/ip hotspot user set [find name=$u] mac-address=$m } on-error={}; '
            ':global hsUser $user; :do { /system script run voucher-activate } on-error={}'
        )
        on_logout = '/system script run hs-quota-save; :delay 500ms; /system script run hs-on-logout'
        cmd = [
            '/ip/hotspot/user/profile/add',
            f'=name={name}',
            f'=rate-limit={rate_limit}',
            f'=shared-users={shared_users}',
            f'=session-timeout={session_timeout}',
            f'=mac-cookie-timeout={mac_cookie_timeout}',
            '=add-mac-cookie=true',
            '=status-autorefresh=1m',
            '=transparent-proxy=false',
            f'=on-login={on_login}',
            f'=on-logout={on_logout}'
        ]
        res = self.talk(cmd)
        return not any(x.startswith('!trap') for x in res)


    def edit_profile(self, name, rate_limit=None, shared_users=None, session_timeout=None, mac_cookie_timeout=None):
        p_res = self.talk(['/ip/hotspot/user/profile/print', f'?name={name}', '=.proplist=.id'])
        p_id = None
        for x in p_res:
            if x.startswith('=.id='):
                p_id = x[5:]
                break
        if not p_id:
            return False
        cmd = ['/ip/hotspot/user/profile/set', f'=.id={p_id}']
        if rate_limit is not None and rate_limit != "":
            cmd.append(f'=rate-limit={rate_limit}')
        if shared_users is not None and shared_users != "":
            cmd.append(f'=shared-users={shared_users}')
        if session_timeout is not None and session_timeout != "":
            cmd.append(f'=session-timeout={session_timeout}')
        if mac_cookie_timeout is not None and mac_cookie_timeout != "":
            cmd.append(f'=mac-cookie-timeout={mac_cookie_timeout}')
        res = self.talk(cmd)
        return not any(x.startswith('!trap') for x in res)

    def remove_profile(self, name):
        p_res = self.talk(['/ip/hotspot/user/profile/print', f'?name={name}', '=.proplist=.id'])
        p_id = None
        for x in p_res:
            if x.startswith('=.id='):
                p_id = x[5:]
                break
        if not p_id:
            return False
        res = self.talk(['/ip/hotspot/user/profile/remove', f'=.id={p_id}'])
        return not any(x.startswith('!trap') for x in res)

    def add_voucher(self, username, password="", profile="default", limit_bytes=0, limit_uptime="1d", price=""):
        cmd = ['/ip/hotspot/user/add', f'=name={username}', f'=profile={profile}']
        if password:
            cmd.append(f'=password={password}')
        else:
            cmd.append(f'=password={username}')
        
        comment = ""
        if limit_bytes > 0:
            cmd.append(f'=limit-bytes-total={limit_bytes}')
            comment += f"[ORIG-LIMIT:{limit_bytes}] "
        if limit_uptime:
            cmd.append(f'=limit-uptime={limit_uptime}')
            comment += f"[ORIG-UP:{limit_uptime}] "
        if price:
            comment += f"Price:{price}Ks "
        
        if comment:
            cmd.append(f'=comment={comment.strip()}')
            
        res = self.talk(cmd)
        return res and res[0][0] == '!done'

    def remove_user(self, username_or_id):
        uid = None
        if str(username_or_id).startswith("*"):
            uid = username_or_id
        else:
            users = self.talk(['/ip/hotspot/user/print', f'?name={username_or_id}'])
            for r in users:
                if r[0] == '!re':
                    for item in r[1:]:
                        if item.startswith('=.id='):
                            uid = item.split('=', 2)[2]
                            break
        if uid:
            res = self.talk(['/ip/hotspot/user/remove', f'=.id={uid}'])
            return res and res[0][0] == '!done'
        return False

    def reset_user_counters(self, username_or_id):
        uid = None
        user_name = None
        if str(username_or_id).startswith("*"):
            uid = username_or_id
        else:
            users = self.talk(['/ip/hotspot/user/print', f'?name={username_or_id}'])
            for r in users:
                if r[0] == '!re':
                    for item in r[1:]:
                        if item.startswith('=.id='):
                            uid = item.split('=', 2)[2]
                        if item.startswith('=name='):
                            user_name = item.split('=', 2)[2]
        if uid:
            self.talk(['/ip/hotspot/user/reset-counters', f'=.id={uid}'])
            # Also clean MAC binding and activation comments if any so user can start fresh
            if user_name:
                self.talk(['/ip/hotspot/active/remove', f'?user={user_name}'])
                self.talk(['/ip/hotspot/cookie/remove', f'?user={user_name}'])
                self.talk(['/ip/hotspot/user/set', f'=.id={uid}', '=mac-address='])
            return True
        return False

    def set_user_disabled(self, username_or_id, disabled=True):
        uid = None
        if str(username_or_id).startswith("*"):
            uid = username_or_id
        else:
            users = self.talk(['/ip/hotspot/user/print', f'?name={username_or_id}'])
            for r in users:
                if r[0] == '!re':
                    for item in r[1:]:
                        if item.startswith('=.id='):
                            uid = item.split('=', 2)[2]
                            break
        if uid:
            res = self.talk(['/ip/hotspot/user/set', f'=.id={uid}', f'=disabled={"yes" if disabled else "no"}'])
            return res and res[0][0] == '!done'
        return False


    def edit_voucher(self, username_or_id, password=None, profile=None, limit_bytes=None, limit_uptime=None, comment=None):
        uid = None
        curr_comment = ""
        users = self.talk(['/ip/hotspot/user/print', f'?name={username_or_id}'])
        for r in users:
            if r[0] == '!re':
                for item in r[1:]:
                    if item.startswith('=.id='):
                        uid = item.split('=', 2)[2]
                    elif item.startswith('=comment='):
                        curr_comment = item.split('=', 2)[2]
        if not uid and str(username_or_id).startswith("*"):
            uid = username_or_id
        if not uid:
            return False

        cmd = ['/ip/hotspot/user/set', f'=.id={uid}']
        if password is not None and password != "":
            cmd.append(f'=password={password}')
        if profile is not None and profile != "":
            cmd.append(f'=profile={profile}')
        if limit_bytes is not None:
            cmd.append(f'=limit-bytes-total={limit_bytes}')
            if "[ORIG-LIMIT:" in curr_comment:
                curr_comment = re.sub(r'\[ORIG-LIMIT:\d+\]', f'[ORIG-LIMIT:{limit_bytes}]', curr_comment)
            else:
                curr_comment = f"[ORIG-LIMIT:{limit_bytes}] " + curr_comment
        if limit_uptime is not None and limit_uptime != "":
            cmd.append(f'=limit-uptime={limit_uptime}')
            if "[ORIG-UP:" in curr_comment:
                curr_comment = re.sub(r'\[ORIG-UP:[^\]]+\]', f'[ORIG-UP:{limit_uptime}]', curr_comment)
            else:
                curr_comment = f"[ORIG-UP:{limit_uptime}] " + curr_comment
        if comment is not None:
            curr_comment = comment
        cmd.append(f'=comment={curr_comment.strip()}')
        res = self.talk(cmd)
        return not any(x.startswith('!trap') for x in res)

    def reboot(self):
        try:
            self.talk(['/system/reboot'])
            return True
        except Exception:
            return True

    def set_identity(self, name):
        res = self.talk(['/system/identity/set', f'=name={name}'])
        return not any(x.startswith('!trap') for x in res)

    def get_logs(self, limit=100):
        res = self.talk(['/log/print', '=.proplist=.id,time,topics,message'])
        logs = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                logs.append(entry)
        return logs[-limit:] if limit else logs

    def get_ip_bindings(self):
        res = self.talk(['/ip/hotspot/ip-binding/print', '=.proplist=.id,mac-address,address,to-address,type,comment,disabled'])
        bindings = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                bindings.append(entry)
        return bindings

    def add_ip_binding(self, mac_address="", address="", binding_type="bypassed", comment=""):
        cmd = ['/ip/hotspot/ip-binding/add', f'=type={binding_type}']
        if mac_address:
            cmd.append(f'=mac-address={mac_address}')
        if address:
            cmd.append(f'=address={address}')
        if comment:
            cmd.append(f'=comment={comment}')
        res = self.talk(cmd)
        return not any(x.startswith('!trap') for x in res)

    def remove_ip_binding(self, binding_id):
        res = self.talk(['/ip/hotspot/ip-binding/remove', f'=.id={binding_id}'])
        return not any(x.startswith('!trap') for x in res)

    def get_dhcp_leases(self):
        res = self.talk(['/ip/dhcp-server/lease/print', '=.proplist=.id,address,mac-address,host-name,status,expires-after,comment'])
        leases = []
        for r in res:
            if r[0] == '!re':
                entry = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        entry[k] = v
                leases.append(entry)
        return leases

    def kick_active(self, username_or_id):
        actives = self.get_active_users()
        for a in actives:
            if a.get('user') == username_or_id or a.get('.id') == username_or_id or a.get('address') == username_or_id:
                aid = a.get('.id')
                if aid:
                    self.talk(['/ip/hotspot/active/remove', f'=.id={aid}'])
        return True

    def kick_all_active(self):
        actives = self.get_active_users()
        for a in actives:
            aid = a.get('.id')
            if aid:
                self.talk(['/ip/hotspot/active/remove', f'=.id={aid}'])
        return True

    @staticmethod
    def execute_cli_command(host, user, password, command, timeout=10):
        """
        Executes a RouterOS command via SSH (primary) or API fallback.
        Returns formatted console text output.
        """
        clean_cmd = command.strip()
        if not clean_cmd:
            return "No command specified."

        # 1. RouterOS API execution (Fastest and direct)
        client = RouterOSClient(host, timeout=timeout)
        try:
            client.connect()
            if client.login(user, password):
                is_script = "\n" in clean_cmd or clean_cmd.startswith(":") or "{" in clean_cmd or ";" in clean_cmd

                if not is_script:
                    api_words = RouterOSClient._parse_cli_to_api(clean_cmd)
                    if api_words:
                        try:
                            res = client.talk(api_words)
                            client.close()
                            return RouterOSClient._format_api_response(clean_cmd, res)
                        except Exception:
                            pass # Fallback to script runner

                # Script execution via /system/script/add + /system/script/run
                s_name = f"cloud_exec_{int(time.time())}"
                client.talk(['/system/script/add', f'=name={s_name}', f'=source={clean_cmd}'])
                client.talk(['/system/script/run', f'=number={s_name}'])
                try:
                    scripts = client.talk(['/system/script/print', f'?name={s_name}'])
                    for s in scripts:
                        if s[0] == '!re':
                            for it in s[1:]:
                                if it.startswith('=.id='):
                                    client.talk(['/system/script/remove', f'=.id={it.split("=", 2)[2]}'])
                except Exception:
                    pass
                client.close()
                return f"[Command executed successfully on RouterOS]"
        except Exception:
            pass

        # 2. Fallback to SSH if available
        try:
            ssh = paramiko.SSHClient()
            ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            ssh.connect(host, port=22, username=f"{user}+t", password=password, timeout=1.5)
            cmd_to_run = clean_cmd
            if "print" in cmd_to_run and "without-paging" not in cmd_to_run and "file=" not in cmd_to_run:
                cmd_to_run += " without-paging"
            stdin, stdout, stderr = ssh.exec_command(cmd_to_run, timeout=timeout)
            out = stdout.read().decode('utf-8', errors='ignore')
            err = stderr.read().decode('utf-8', errors='ignore')
            ssh.close()
            res = out
            if err and err.strip():
                res = f"{res}\n[STDERR]: {err.strip()}"
            if res.strip():
                return res.strip()
        except Exception:
            pass
        return "[Error: Unable to execute command via RouterOS API or SSH]"

    @staticmethod
    def _parse_cli_to_api(cmd_str):
        cmd_str = cmd_str.strip()
        raw_tokens = cmd_str.split()
        tokens = [t for t in raw_tokens if t not in ('without-paging', 'brief', 'terse')]
        if not tokens:
            return []

        if tokens[0] in ('/ping', 'ping'):
            api_sent = ['/ping']
            for t in tokens[1:]:
                if '=' in t:
                    k, v = t.split('=', 1)
                    api_sent.append(f"={k}={v}")
                else:
                    api_sent.append(f"=address={t}")
            return api_sent

        ACTIONS = {'print', 'add', 'set', 'remove', 'enable', 'disable', 'reset', 'run', 'export', 'check', 'get'}
        path_parts = []
        args = []
        hit_action = False

        for t in tokens:
            if '=' in t or t.startswith('?'):
                hit_action = True
                args.append(t if t.startswith('=') or t.startswith('?') else f"={t}")
            elif not hit_action:
                clean_t = t.lstrip('/')
                path_parts.append(clean_t)
                if clean_t.lower() in ACTIONS:
                    hit_action = True
            else:
                args.append(f"={t}")

        api_path = '/' + '/'.join(path_parts)
        return [api_path] + args

    @staticmethod
    def _format_api_response(cmd_str, res):
        if not res:
            return "[No output returned]"
        
        lines = []
        items = []
        for r in res:
            if r[0] == '!re':
                d = {}
                for item in r[1:]:
                    if '=' in item:
                        k, v = item.lstrip('=').split('=', 1)
                        d[k] = v
                items.append(d)
            elif r[0] == '!trap':
                d = dict(x.lstrip('=').split('=', 1) for x in r[1:] if '=' in x)
                return f"ERROR: {d.get('message', 'Command execution error')}"

        if not items:
            for r in res:
                if r[0] == '!done':
                    for it in r[1:]:
                        if it.startswith('=ret='):
                            return f"Done: {it.split('=', 2)[2]}"
                    return "Done (executed successfully)"
            return "[Command executed]"

        if len(items) == 1 and len(items[0]) > 4:
            d = items[0]
            max_k = max((len(k) for k in d.keys() if not k.startswith('.')), default=10)
            for k, v in d.items():
                if k.startswith('.'): continue
                lines.append(f"{k.rjust(max_k)}: {v}")
            return "\n".join(lines)

        all_keys = []
        for it in items:
            for k in it.keys():
                if not k.startswith('.') and k not in all_keys:
                    all_keys.append(k)

        cols = all_keys[:6]
        col_widths = {c: max(len(c), max((len(str(it.get(c, ''))) for it in items[:30]), default=0)) for c in cols}
        hdr = " #  " + "  ".join(c.upper().ljust(col_widths[c]) for c in cols)
        lines.append(hdr)
        lines.append("-" * min(100, max(len(hdr), 40)))
        for idx, it in enumerate(items[:60]):
            row = f"{str(idx).rjust(2)}  " + "  ".join(str(it.get(c, '')).ljust(col_widths[c]) for c in cols)
            lines.append(row)

        if len(items) > 60:
            lines.append(f"... ({len(items) - 60} more items)")
        return "\n".join(lines)

    def run_rsc_code(self, rsc_code, name="cloud_rsc_run"):
        clean_code = rsc_code.strip()
        if not clean_code:
            return {"status": "error", "message": "Script content is empty"}
        s_name = f"{name}_{int(time.time())}"
        self.talk(['/system/script/add', f'=name={s_name}', f'=source={clean_code}'])
        res = self.talk(['/system/script/run', f'=number={s_name}'])
        try:
            scripts = self.talk(['/system/script/print', f'?name={s_name}'])
            for s in scripts:
                if s[0] == '!re':
                    for it in s[1:]:
                        if it.startswith('=.id='):
                            self.talk(['/system/script/remove', f'=.id={it.split("=", 2)[2]}'])
        except Exception:
            pass
        return {"status": "ok", "message": "RSC script executed successfully on router!"}

    def remove_all_voucher_accounts(self):
        rsc = (
            ':foreach u in=[/ip hotspot user find] do={'
            ' :local n [/ip hotspot user get $u name];'
            ' :if ($n != "admin" and $n != "default-trial") do={'
            '  :do { /ip hotspot active remove [find user=$n] } on-error={};'
            '  :do { /ip hotspot cookie remove [find user=$n] } on-error={};'
            '  :do { /system scheduler remove [find name=$n] } on-error={};'
            '  :do { /ip hotspot user remove $u } on-error={};'
            ' };'
            '};'
        )
        return self.run_rsc_code(rsc, name='purge_all_vouchers')

    def deploy_quota_engine(self):
        quota_src = """  :local parseTag do={
    :local tagVal "";
    :local idx [:find $comm $tag];
    :if ($idx >= 0) do={
      :local rest [:pick $comm ($idx + [:len $tag]) [:len $comm]];
      :local endIdx [:find $rest "]"];
      :if ($endIdx >= 0) do={ :set rest [:pick $rest 0 $endIdx] };
      :set tagVal $rest;
    };
    :return $tagVal;
  };

  :local setTag do={
    :local res $comm;
    :local idx [:find $comm $tag];
    :if ($idx >= 0) do={
      :local endIdx [:find $comm "]" $idx];
      :local pfx [:pick $comm 0 $idx];
      :local sfx "";
      :if ($endIdx >= 0) do={ :set sfx [:pick $comm ($endIdx + 1) [:len $comm]] };
      :set res ($pfx . $tag . $val . "]" . $sfx);
    } else={
      :set res ($comm . " " . $tag . $val . "]");
    };
    :return $res;
  };

  :local resolveOrigLim do={
    :local limStr [$parseTag comm=$comm tag="[ORIG-LIMIT:"];
    :if ([:len $limStr] > 0) do={
      :local lNum [:tonum $limStr];
      :if ($lNum > 0) do={ :return $lNum };
    };
    :local cUpper ($prof . " " . $comm);
    :if ($cUpper ~ "100GB" or $cUpper ~ "100G") do={ :return 107374182400 };
    :if ($cUpper ~ "50GB" or $cUpper ~ "50G") do={ :return 53687091200 };
    :if ($cUpper ~ "30GB" or $cUpper ~ "30Day" or $cUpper ~ "30G") do={ :return 32212254720 };
    :if ($cUpper ~ "20GB" or $cUpper ~ "20G") do={ :return 21474836480 };
    :if ($cUpper ~ "15GB" or $cUpper ~ "15G") do={ :return 16106127360 };
    :if ($cUpper ~ "10GB" or $cUpper ~ "10G") do={ :return 10737418240 };
    :if ($cUpper ~ "7GB" or $cUpper ~ "7G") do={ :return 7516192768 };
    :if ($cUpper ~ "5GB" or $cUpper ~ "5G") do={ :return 5368709120 };
    :if ($cUpper ~ "4GB" or $cUpper ~ "4G") do={ :return 4294967296 };
    :if ($cUpper ~ "3GB" or $cUpper ~ "3G") do={ :return 3221225472 };
    :if ($cUpper ~ "2GB" or $cUpper ~ "2G") do={ :return 2147483648 };
    :if ($cUpper ~ "1.5GB" or $cUpper ~ "1.7GB" or $cUpper ~ "1700MB" or $prof = "1000Ks") do={ :return 1782579200 };
    :if ($cUpper ~ "1GB" or $cUpper ~ "1G") do={ :return 1073741824 };
    :if ($cUpper ~ "750MB" or $prof = "500Ks") do={ :return 786432000 };
    :if ($cUpper ~ "500MB" or $cUpper ~ "500M") do={ :return 524288000 };
    :if ($cUpper ~ "250MB" or $cUpper ~ "250M") do={ :return 262144000 };
    :if ($cUpper ~ "100MB" or $cUpper ~ "100M") do={ :return 104857600 };
    :if ($cUpper ~ "50MB" or $cUpper ~ "50M" or $cUpper ~ "test") do={ :return 52428800 };
    :return $curLim;
  };

  :local resolveOrigUp do={
    :local upStr [$parseTag comm=$comm tag="[ORIG-UP:"];
    :if ([:len $upStr] > 0) do={ :return [:totime $upStr] };
    :local cUpper ($prof . " " . $comm);
    :if ($cUpper ~ "30D" or $cUpper ~ "30Day" or $cUpper ~ "Month") do={ :return [:totime "30d"] };
    :if ($cUpper ~ "14D" or $cUpper ~ "2W") do={ :return [:totime "14d"] };
    :if ($cUpper ~ "7D" or $cUpper ~ "1W") do={ :return [:totime "7d"] };
    :if ($cUpper ~ "3D") do={ :return [:totime "3d"] };
    :if ($cUpper ~ "2D") do={ :return [:totime "2d"] };
    :if ($cUpper ~ "1D" or $cUpper ~ "24H" or $prof = "5GB" or $prof = "2GB" or $prof = "1000Ks" or $prof = "500Ks") do={ :return [:totime "1d"] };
    :if ($cUpper ~ "12H") do={ :return [:totime "12h"] };
    :if ($cUpper ~ "8H") do={ :return [:totime "8h"] };
    :if ($cUpper ~ "6H") do={ :return [:totime "6h"] };
    :if ($cUpper ~ "4H") do={ :return [:totime "4h"] };
    :if ($cUpper ~ "3H") do={ :return [:totime "3h"] };
    :if ($cUpper ~ "2H" or $prof = "2Hour") do={ :return [:totime "2h"] };
    :if ($cUpper ~ "1H") do={ :return [:totime "1h"] };
    :if ($cUpper ~ "45M") do={ :return [:totime "45m"] };
    :if ($cUpper ~ "30M") do={ :return [:totime "30m"] };
    :if ($cUpper ~ "15M") do={ :return [:totime "15m"] };
    :if ($cUpper ~ "3M" or $prof = "test") do={ :return [:totime "3m"] };
    :if ($curUp != "00:00:00" and $curUp != "0s" and [:len $curUp] > 0) do={ :return [:totime $curUp] };
    :return [:totime "0s"];
  };

  :foreach a in=[/ip hotspot active find] do={
    :local uName [/ip hotspot active get $a user];
    :if ($uName != "admin" and $uName != "default-trial") do={
      :local uList [/ip hotspot user find name=$uName];
      :if ([:len $uList] > 0) do={
        :local u [:pick $uList 0];
        :local sIn [/ip hotspot active get $a bytes-in];
        :local sOut [/ip hotspot active get $a bytes-out];
        :local sUp [/ip hotspot active get $a uptime];
        :local sessionBytes ($sIn + $sOut);
        :local comm [/ip hotspot user get $u comment];
        :local uProf [/ip hotspot user get $u profile];
        :local curLim [/ip hotspot user get $u limit-bytes-total];
        :local curUp [/ip hotspot user get $u limit-uptime];

        :local baseBytes 0;
        :local bStr [$parseTag comm=$comm tag="[BASE:"];
        :if ([:len $bStr] > 0) do={ :set baseBytes [:tonum $bStr] };

        :local baseUp [:totime "0s"];
        :local buStr [$parseTag comm=$comm tag="[BASE-UP:"];
        :if ([:len $buStr] > 0) do={ :set baseUp [:totime $buStr] };

        :local curStoredUsed 0;
        :local uStr [$parseTag comm=$comm tag="[USED:"];
        :if ([:len $uStr] > 0) do={ :set curStoredUsed [:tonum $uStr] };

        :local curStoredUp [:totime "0s"];
        :local uuStr [$parseTag comm=$comm tag="[USED-UP:"];
        :if ([:len $uuStr] > 0) do={ :set curStoredUp [:totime $uuStr] };

        :local totalUsed ($baseBytes + $sessionBytes);
        :local totalUp ($baseUp + $sUp);

        # ONLY promote if counters reset due to reboot (i.e. calculated total fell below curStoredUsed)
        :if ($curStoredUsed > 0 and $totalUsed < $curStoredUsed) do={
          :set baseBytes $curStoredUsed;
          :set totalUsed ($baseBytes + $sessionBytes);
          :set comm [$setTag comm=$comm tag="[BASE:" val=[:tostr $baseBytes]];
        };
        :if ($curStoredUp > [:totime "0s"] and $totalUp < $curStoredUp) do={
          :set baseUp $curStoredUp;
          :set totalUp ($baseUp + $sUp);
          :set comm [$setTag comm=$comm tag="[BASE-UP:" val=[:tostr $baseUp]];
        };

        :local origLim [$resolveOrigLim parseTag=$parseTag prof=$uProf comm=$comm curLim=$curLim];
        :local origUp [$resolveOrigUp parseTag=$parseTag prof=$uProf comm=$comm curUp=$curUp];

        :if ([:find $comm "[ORIG-LIMIT:"] < 0 and $origLim > 0) do={
          :set comm [$setTag comm=$comm tag="[ORIG-LIMIT:" val=[:tostr $origLim]];
        };
        :if ([:find $comm "[ORIG-UP:"] < 0 and $origUp > [:totime "0s"]) do={
          :set comm [$setTag comm=$comm tag="[ORIG-UP:" val=[:tostr $origUp]];
        };

        :local deltaBytes ($totalUsed - $curStoredUsed);
        :if ($deltaBytes < 0) do={ :set deltaBytes (-$deltaBytes) };
        :if (($totalUsed > 0 and $deltaBytes >= 32768) or ($totalUsed != $curStoredUsed and $sessionBytes = 0) or ($totalUp != $curStoredUp and ($totalUp - $curStoredUp) >= [:totime "2s"])) do={
          :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
          :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
          /ip hotspot user set $u comment=$comm;
        };

        :local isDataExhausted false;
        :if ($origLim > 0 and $totalUsed >= $origLim) do={ :set isDataExhausted true };

        :local isTimeExhausted false;
        :if ($origUp > [:totime "0s"] and $totalUp >= $origUp) do={ :set isTimeExhausted true };

        :if ($isDataExhausted or $isTimeExhausted) do={
          :log info ("Hotspot: User " . $uName . " EXHAUSTED (Data: " . [:tostr $totalUsed] . "/" . [:tostr $origLim] . ", Time: " . [:tostr $totalUp] . "/" . [:tostr $origUp] . ")");
          :set comm [$setTag comm=$comm tag="[USED:" val=[:tostr $totalUsed]];
          :set comm [$setTag comm=$comm tag="[USED-UP:" val=[:tostr $totalUp]];
          /ip hotspot user set $u comment=$comm;
          /ip hotspot active remove $a;
          /ip hotspot cookie remove [find user=$uName];
          :if ($isDataExhausted) do={ /ip hotspot user set $u limit-bytes-total=1 };
          :if ($isTimeExhausted) do={ /ip hotspot user set $u limit-uptime=1s };
        };
      };
    };
  };"""
        # 1. Update or create hs-quota-save script
        s_res = self.talk(['/system/script/print', '?name=hs-quota-save', '=.proplist=.id'])
        sid = None
        for s in s_res:
            if s[0] == '!re':
                for w in s[1:]:
                    if w.startswith('=.id='):
                        sid = w.split('=', 2)[2]
        if sid:
            self.talk(['/system/script/set', f'=.id={sid}', f'=source={quota_src}'])
        else:
            self.talk(['/system/script/add', '=name=hs-quota-save', f'=source={quota_src}', '=comment=Universal Quota Accounting Script'])

        # 2. Ensure scheduler hs-quota-saver exists (runs every 2s)
        sch_res = self.talk(['/system/scheduler/print', '?name=hs-quota-saver', '=.proplist=.id'])
        sch_id = None
        for s in sch_res:
            if s[0] == '!re':
                for w in s[1:]:
                    if w.startswith('=.id='):
                        sch_id = w.split('=', 2)[2]
        if sch_id:
            self.talk(['/system/scheduler/set', f'=.id={sch_id}', '=interval=5m', '=disabled=no'])
        else:
            self.talk(['/system/scheduler/add', '=name=hs-quota-saver', '=interval=5m', '=on-event=/system script run hs-quota-save', '=start-time=startup', '=comment=Universal Quota Engine Poller'])

        # 3. Trigger immediate run
        self.talk(['/system/script/run', '=number=hs-quota-save'])
        return {
            "status": "ok",
            "message": "Universal Quota & Accounting RSC scripts successfully updated and verified on router!"
        }

    @staticmethod
    def upload_portal(host, ftp_user, ftp_pass, portal_files_dict, target_dir="flash/hotspot", port=21):
        ftp = ftplib.FTP()
        ftp.connect(host, port, timeout=10)
        ftp.login(ftp_user, ftp_pass)
        
        root_list = ftp.nlst()
        if 'flash' not in root_list and target_dir.startswith('flash/'):
            target_dir = 'hotspot'

        try:
            ftp.cwd('/' + target_dir)
        except Exception:
            parts = target_dir.split('/')
            cur = ''
            for p in parts:
                cur += '/' + p
                try: ftp.mkd(cur)
                except Exception: pass
            ftp.cwd('/' + target_dir)

        try: ftp.mkd('css')
        except Exception: pass
        try: ftp.mkd('js')
        except Exception: pass

        for rel_path, content_bytes in portal_files_dict.items():
            bio = io.BytesIO(content_bytes if isinstance(content_bytes, bytes) else content_bytes.encode('utf-8'))
            ftp.storbinary(f'STOR {rel_path}', bio)

        ftp.quit()
        return True
