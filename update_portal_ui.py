import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')

sftp = ssh.open_sftp()
with sftp.file('/opt/hotspot-cloud/templates/index.html', 'r') as f:
    html = f.read().decode('utf-8')

# 1. Add button in modal top bar
old_top_bar = '''          <!-- Refresh Now -->
          <button class="btn-sm btn-info" onclick="refreshCurrentStatus()">
            🔄 Refresh
          </button>'''

new_top_bar = '''          <!-- Refresh Now -->
          <button class="btn-sm btn-info" onclick="refreshCurrentStatus()">
            🔄 Refresh
          </button>

          <!-- Remove All Voucher Accounts -->
          <button class="btn-sm btn-danger" onclick="removeAllVoucherAccounts(CURRENT_CHECKING_RID)" title="Remove All Voucher Accounts from Router">
            🗑️ Remove All Accounts
          </button>'''

if old_top_bar in html:
    html = html.replace(old_top_bar, new_top_bar, 1)
    print("Added button in modal top bar")

# 2. Add button in router actions list
old_acts = '''          <button class="btn-sm btn-info" onclick="event.stopPropagation(); checkRouterStatus(${r.id})">📊 မက်ထရစ် စစ်ဆေးမည်</button>
          <button class="btn-sm btn-amber" onclick="event.stopPropagation(); showScriptModal(${r.id})">📜 Script</button>
          <button class="btn-sm btn-success" onclick="event.stopPropagation(); deployPortalToRouter(${r.id})">🚀 Portal တင်မည်</button>'''

new_acts = '''          <button class="btn-sm btn-info" onclick="event.stopPropagation(); checkRouterStatus(${r.id})">📊 မက်ထရစ် စစ်ဆေးမည်</button>
          <button class="btn-sm btn-amber" onclick="event.stopPropagation(); showScriptModal(${r.id})">📜 Script</button>
          <button class="btn-sm btn-success" onclick="event.stopPropagation(); deployPortalToRouter(${r.id})">🚀 Portal တင်မည်</button>
          <button class="btn-sm btn-danger" onclick="event.stopPropagation(); removeAllVoucherAccounts(${r.id})" title="Remove All Accounts from Router">🗑️ Accounts ဖျက်မည်</button>'''

if old_acts in html:
    html = html.replace(old_acts, new_acts, 1)
    print("Added button in router row actions")

# 3. Add JS function
js_target = '    async function deleteRouter(rid) {'
js_func = '''    async function removeAllVoucherAccounts(rid) {
      if (!rid) return;
      if (!confirm('⚠️ သတိပေးချက်: Router ပေါ်ရှိ Voucher အကောင့်အားလုံး၊ Active Session များနှင့် Scheduler များကို အပြီးတိုင် ဖျက်ပစ်ပါမည်။ (admin နှင့် trial အကောင့်များ မပါဝင်ပါ)\\n\\nအမှန်တကယ် ဆက်လုပ်လိုပါသလား?')) return;
      const res = await api(`/api/routers/${rid}/remove-all-accounts`, 'POST');
      if (res && res.status === 'ok') {
        alert('✅ ' + (res.message || 'All voucher accounts removed successfully!'));
        if (typeof refreshCurrentStatus === 'function') refreshCurrentStatus();
        if (typeof loadRouters === 'function') loadRouters();
      } else {
        alert('❌ ' + (res.detail || 'Failed to remove accounts'));
      }
    }

    async function deleteRouter(rid) {'''

if js_target in html and 'removeAllVoucherAccounts' not in html:
    html = html.replace(js_target, js_func, 1)
    print("Added removeAllVoucherAccounts JS function")

with sftp.file('/opt/hotspot-cloud/templates/index.html', 'w') as f:
    f.write(html.encode('utf-8'))
sftp.close()

# Also check cf_pages_deploy or static if present
stdin, stdout, stderr = ssh.exec_command('find /opt/hotspot-cloud -name "index.html"')
print("Found html files:\n", stdout.read().decode())

ssh.close()
print("Updated index.html successfully!")
