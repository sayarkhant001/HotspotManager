import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')
sftp = ssh.open_sftp()

with sftp.file('/opt/hotspot-cloud/cloud_mikrotik.py', 'r') as f:
    mk_content = f.read().decode('utf-8')

if 'def add_profile' not in mk_content:
    profile_code = """    def add_profile(self, name, rate_limit="10M/10M", shared_users="1", session_timeout="1d", mac_cookie_timeout="4w2d"):
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

"""
    target = '    def add_voucher('
    mk_content = mk_content.replace(target, profile_code + target, 1)
    with sftp.file('/opt/hotspot-cloud/cloud_mikrotik.py', 'w') as f:
        f.write(mk_content.encode('utf-8'))
    print('Updated cloud_mikrotik.py with add_profile and remove_profile.')
else:
    print('add_profile already present in cloud_mikrotik.py')
