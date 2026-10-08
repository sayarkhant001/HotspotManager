import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')
sftp = ssh.open_sftp()

with sftp.file('/opt/hotspot-cloud/templates/index.html', 'r') as f:
    html = f.read().decode('utf-8')

# 1. Update Profiles Header to include '+ ပရိုဖိုင် အသစ်ဖန်တီးမည်'
old_prof_header = '''              <h3 style="font-size:15px; font-weight:800; color:#FFF; margin-bottom:12px; display:flex; align-items:center; gap:8px;">
                ⚡ ဘောက်ချာ ပရိုဖိုင်များနှင့် အမြန်နှုန်း သတ်မှတ်ချက်များ (Hotspot Profiles)
              </h3>'''

new_prof_header = '''              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:10px;">
                <h3 style="font-size:15px; font-weight:800; color:#FFF; margin:0; display:flex; align-items:center; gap:8px;">
                  ⚡ ဘောက်ချာ ပရိုဖိုင်များနှင့် အမြန်နှုန်း သတ်မှတ်ချက်များ (Hotspot Profiles)
                </h3>
                <button class="btn-primary" style="padding:6px 14px; font-size:12.5px;" onclick="openCreateProfileModal()">
                  + ပရိုဖိုင် အသစ်ဖန်တီးမည် (New Profile)
                </button>
              </div>'''

if old_prof_header in html:
    html = html.replace(old_prof_header, new_prof_header, 1)
    print("1. Replaced Profiles Header.")

# 2. Add Modal: Create Hotspot Profile & Modal: Batch Generate Live Vouchers
modals_to_add = '''
  <!-- Modal: Create Hotspot User Profile on Router -->
  <div class="modal-overlay" id="modal_create_profile">
    <div class="modal-card" style="max-width: 480px;">
      <h3 style="color:#FFF; font-size:18px; margin-bottom:14px; display:flex; align-items:center; gap:8px;">
        ⚡ Hotspot Profile အသစ် ဖန်တီးမည် (RouterOS)
      </h3>
      <div class="form-group">
        <label>ပရိုဖိုင် အမည် (Profile Name) *</label>
        <input class="form-control" type="text" id="prof_new_name" placeholder="ဥပမာ: 2GB, 5GB, 1000Ks, 2Hour">
      </div>
      <div class="form-group">
        <label>အင်တာနက် အမြန်နှုန်း ကန့်သတ်ချက် (Rate Limit Rx/Tx)</label>
        <select class="form-control" id="prof_new_rate">
          <option value="5M/5M">5 Mbps (5M/5M)</option>
          <option value="10M/10M" selected>10 Mbps (10M/10M - Standard)</option>
          <option value="20M/20M">20 Mbps (20M/20M - Fast)</option>
          <option value="30M/30M">30 Mbps (30M/30M - High Speed)</option>
          <option value="50M/50M">50 Mbps (50M/50M - VIP)</option>
          <option value="">အကန့်အသတ်မရှိ (Unlimited)</option>
        </select>
      </div>
      <div class="form-group">
        <label>အသုံးပြုနိုင်မည့် အများဆုံးသက်တမ်း (Session Timeout)</label>
        <select class="form-control" id="prof_new_timeout">
          <option value="1h">၁ နာရီ (1 Hour)</option>
          <option value="2h">၂ နာရီ (2 Hours)</option>
          <option value="12h">၁၂ နာရီ (12 Hours)</option>
          <option value="1d" selected>၁ ရက် (24 Hours)</option>
          <option value="2d">၂ ရက် (48 Hours)</option>
          <option value="7d">၁ ပတ် (7 Days)</option>
          <option value="30d">၁ လ (30 Days)</option>
        </select>
      </div>
      <div class="form-group">
        <label>တစ်ပြိုင်နက် သုံးစွဲနိုင်သည့် စက်အရေအတွက် (Shared Users)</label>
        <input class="form-control" type="number" id="prof_new_shared" value="1" min="1" max="10">
      </div>
      <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:20px;">
        <button class="btn-sm btn-danger" onclick="closeModal('modal_create_profile')">ပယ်ဖျက်မည်</button>
        <button class="btn-primary" id="btn_submit_create_profile" onclick="submitCreateHotspotProfile()">⚡ Router ပေါ်သို့ ထည့်သွင်းမည်</button>
      </div>
    </div>
  </div>

  <!-- Modal: Batch Generate Live Vouchers Directly on Router -->
  <div class="modal-overlay" id="modal_batch_generate_live">
    <div class="modal-card" style="max-width: 500px;">
      <h3 style="color:#FFF; font-size:18px; margin-bottom:14px; display:flex; align-items:center; gap:8px;">
        ⚡ အုပ်စုလိုက် ဘောက်ချာ ထုတ်ဝေခြင်း (Batch Generate)
      </h3>
      <div class="form-group">
        <label>ဘောင်ချာ အရေအတွက် (Voucher Count)</label>
        <select class="form-control" id="batch_count">
          <option value="5">၅ စောင် (5 Vouchers)</option>
          <option value="10" selected>၁၀ စောင် (10 Vouchers)</option>
          <option value="20">၂၀ စောင် (20 Vouchers)</option>
          <option value="50">၅၀ စောင် (50 Vouchers)</option>
          <option value="100">၁၀၀ စောင် (100 Vouchers)</option>
        </select>
      </div>
      <div class="form-group">
        <label>ဂဏန်း အရေအတွက် (Digits Length)</label>
        <select class="form-control" id="batch_digits">
          <option value="4">၄ လုံး (4 Digits - Short)</option>
          <option value="6" selected>၆ လုံး (6 Digits - Standard)</option>
          <option value="8">၈ လုံး (8 Digits - Secure)</option>
        </select>
      </div>
      <div class="form-group">
        <label>ပရိုဖိုင် ရွေးချယ်ရန် (Destination Profile) *</label>
        <select class="form-control" id="batch_profile" onchange="onBatchProfileSelected(this.value)"></select>
      </div>
      <div class="form-group">
        <label>အသုံးပြုနိုင်မည့်အချိန် (Time Limit)</label>
        <select class="form-control" id="batch_uptime">
          <option value="1h">၁ နာရီ (1 Hour)</option>
          <option value="2h">၂ နာရီ (2 Hours)</option>
          <option value="3h">၃ နာရီ (3 Hours)</option>
          <option value="6h">၆ နာရီ (6 Hours)</option>
          <option value="12h">၁၂ နာရီ (12 Hours)</option>
          <option value="1d" selected>၁ ရက် (24 Hours)</option>
          <option value="2d">၂ ရက် (48 Hours)</option>
          <option value="7d">၁ ပတ် (7 Days)</option>
          <option value="30d">၁ လ (30 Days)</option>
          <option value="">အကန့်အသတ်မရှိ (Unlimited)</option>
        </select>
      </div>
      <div class="form-group">
        <label>ဒေတာပမာဏ (Data Quota MB)</label>
        <input class="form-control" type="number" id="batch_quota" value="0" placeholder="0 ဆိုပါက အကန့်အသတ်မရှိ">
      </div>
      <div class="form-group">
        <label>တစ်စောင်လျှင် စျေးနှုန်း (MMK)</label>
        <input class="form-control" type="number" id="batch_price" value="500">
      </div>
      <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:20px;">
        <button class="btn-sm btn-danger" onclick="closeModal('modal_batch_generate_live')">ပယ်ဖျက်မည်</button>
        <button class="btn-primary" id="btn_batch_submit" onclick="submitBatchGenerateLive()">⚡ အုပ်စုလိုက် ထုတ်ဝေမည် (Generate)</button>
      </div>
    </div>
  </div>
'''

if 'id="modal_create_profile"' not in html:
    target_modal = '<!-- Modal: Create Single Live Voucher on Router -->'
    html = html.replace(target_modal, modals_to_add + "\n  " + target_modal, 1)
    print("2. Added create_profile and batch_generate modals.")

# 3. Update modal_create_live_voucher to have Profile select and Digits select
old_single_voucher_form = '''      <div class="form-group">
        <label>ဘောင်ချာကုဒ် / အသုံးပြုသူအမည် (Username)</label>
        <div style="display:flex; gap:8px;">
          <input class="form-control" type="text" id="lv_name" placeholder="ဥပမာ: 859421">
          <button class="btn-sm btn-info" onclick="generateRandomVoucherCode()" style="white-space:nowrap;">🎲 Random</button>
        </div>
      </div>
      <div class="form-group">
        <label>လျှို့ဝှက်နံပါတ် (Password)</label>
        <input class="form-control" type="text" id="lv_pass" placeholder="အလွတ်ထားပါက ကုဒ်နှင့် အတူတူဖြစ်ပါမည်">
      </div>
      <div class="form-group">
        <label>Profile (ပရိုဖိုင်)</label>
        <input class="form-control" type="text" id="lv_profile" value="default" placeholder="default / 2GB / 1000Ks">
      </div>'''

new_single_voucher_form = '''      <div class="form-group">
        <label>ဘောင်ချာကုဒ် / အသုံးပြုသူအမည် (Username)</label>
        <div style="display:flex; gap:8px;">
          <select class="form-control" id="lv_digits" style="width:115px;" onchange="generateRandomVoucherCode()" title="ဂဏန်းအရေအတွက်">
            <option value="4">4 Digits</option>
            <option value="6" selected>6 Digits</option>
            <option value="8">8 Digits</option>
          </select>
          <input class="form-control" type="text" id="lv_name" placeholder="ဥပမာ: 859421" style="flex:1;">
          <button class="btn-sm btn-info" onclick="generateRandomVoucherCode()" style="white-space:nowrap;">🎲 Random</button>
        </div>
      </div>
      <div class="form-group">
        <label>လျှို့ဝှက်နံပါတ် (Password)</label>
        <input class="form-control" type="text" id="lv_pass" placeholder="အလွတ်ထားပါက ကုဒ်နှင့် အတူတူဖြစ်ပါမည်">
      </div>
      <div class="form-group">
        <label>ပရိုဖိုင် ရွေးချယ်ရန် (Destination Profile) *</label>
        <select class="form-control" id="lv_profile" onchange="onSingleProfileSelected(this.value)"></select>
      </div>'''

if old_single_voucher_form in html:
    html = html.replace(old_single_voucher_form, new_single_voucher_form, 1)
    print("3. Updated modal_create_live_voucher with profile select and digits length.")

# 4. Toast Notification CSS and Container
toast_ui = '''
  <!-- Floating Toast Notifications System -->
  <div id="toast_container" style="position:fixed; top:24px; right:24px; z-index:999999; display:flex; flex-direction:column; gap:10px; pointer-events:none;"></div>
'''
if 'id="toast_container"' not in html:
    html = html.replace('</body>', toast_ui + "\n</body>", 1)
    print("4. Added Toast Container.")

# 5. JavaScript functions for Profile Management, Batch Vouchers, and Toast Notifications
js_code_to_add = '''
    // ── TOAST NOTIFICATION & INBOX SYSTEM ─────────────────────────
    const NOTIFICATION_INBOX = [];
    function showToast(title, message, type = 'success') {
      NOTIFICATION_INBOX.unshift({ title, message, type, time: new Date().toLocaleTimeString() });
      const container = gid('toast_container');
      if (!container) return;

      const toast = document.createElement('div');
      toast.style.pointerEvents = 'auto';
      toast.style.minWidth = '320px';
      toast.style.maxWidth = '420px';
      toast.style.padding = '14px 18px';
      toast.style.borderRadius = '12px';
      toast.style.backdropFilter = 'blur(20px)';
      toast.style.boxShadow = '0 10px 30px rgba(0,0,0,0.5)';
      toast.style.display = 'flex';
      toast.style.alignItems = 'flex-start';
      toast.style.gap = '12px';
      toast.style.transition = 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)';
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px) scale(0.95)';

      let borderCol = '#10B981', bgCol = 'rgba(16, 185, 129, 0.15)', icon = '✅';
      if (type === 'error') { borderCol = '#EF4444'; bgCol = 'rgba(239, 68, 68, 0.15)'; icon = '❌'; }
      else if (type === 'info') { borderCol = '#00F2FE'; bgCol = 'rgba(0, 242, 254, 0.15)'; icon = 'ℹ️'; }
      else if (type === 'warn') { borderCol = '#F59E0B'; bgCol = 'rgba(245, 158, 11, 0.15)'; icon = '⚠️'; }

      toast.style.border = `1px solid ${borderCol}`;
      toast.style.background = `rgba(15, 23, 42, 0.92)`;

      toast.innerHTML = `
        <span style="font-size:20px; line-height:1;">${icon}</span>
        <div style="flex:1;">
          <strong style="color:#FFF; font-size:13.5px; display:block; margin-bottom:2px;">${title}</strong>
          <span style="color:#94A3B8; font-size:12px; line-height:1.4;">${message}</span>
        </div>
        <span style="cursor:pointer; color:#94A3B8; font-size:16px; font-weight:700;" onclick="this.parentElement.remove()">×</span>
      `;

      container.appendChild(toast);
      requestAnimationFrame(() => {
        toast.style.opacity = '1';
        toast.style.transform = 'translateY(0) scale(1)';
      });

      setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px) scale(0.95)';
        setTimeout(() => toast.remove(), 300);
      }, 4500);
    }

    // ── PROFILE MODAL LOGIC ──────────────────────────────────────
    function openCreateProfileModal() {
      gid('prof_new_name').value = '';
      gid('prof_new_rate').value = '10M/10M';
      gid('prof_new_timeout').value = '1d';
      gid('prof_new_shared').value = '1';
      openModal('modal_create_profile');
    }

    async function submitCreateHotspotProfile() {
      const name = gid('prof_new_name').value.trim();
      const rate = gid('prof_new_rate').value.trim();
      const timeout = gid('prof_new_timeout').value.trim();
      const shared = gid('prof_new_shared').value.trim() || '1';
      const rid = CURRENT_ROUTER_STATUS_ID || CURRENT_CHECKING_RID;

      if (!name) return alert('ပရိုဖိုင် အမည် (Profile Name) ရိုက်ထည့်ပါ');
      if (!rid) return alert('ချိတ်ဆက်ထားသော Router ကို ရွေးချယ်ပါ');

      const btn = gid('btn_submit_create_profile');
      if (btn) { btn.disabled = true; btn.textContent = '⏳ ဖန်တီးနေပါသည်...'; }

      try {
        const res = await api(`/api/routers/${rid}/profiles`, 'POST', {
          name: name,
          rate_limit: rate,
          session_timeout: timeout,
          shared_users: shared
        });

        if (res && res.status === 'ok') {
          showToast('Profile Created', `ပရိုဖိုင် '${name}' ကို RouterOS ပေါ်သို့ အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ!`, 'success');
          closeModal('modal_create_profile');
          await loadRouterProfiles();
        } else {
          showToast('Profile Error', res.detail || 'Failed to create profile', 'error');
        }
      } catch (err) {
        showToast('Profile Error', err.message || 'Error creating profile', 'error');
      } finally {
        if (btn) { btn.disabled = false; btn.textContent = '⚡ Router ပေါ်သို့ ထည့်သွင်းမည်'; }
      }
    }

    // Populate profile selects dynamically
    function populateProfileSelects(profiles) {
      const singleSel = gid('lv_profile');
      const batchSel = gid('batch_profile');
      if (!singleSel && !batchSel) return;

      const opts = (profiles || []).map(p => {
        const speed = p['rate-limit'] || 'Unlimited';
        return `<option value="${p.name}">${p.name} (${speed})</option>`;
      }).join('');

      if (singleSel) singleSel.innerHTML = opts;
      if (batchSel) batchSel.innerHTML = opts;
    }

    function onSingleProfileSelected(pName) {
      autofillProfileDefaults(pName, 'lv_quota', 'lv_uptime', 'lv_price');
    }

    function onBatchProfileSelected(pName) {
      autofillProfileDefaults(pName, 'batch_quota', 'batch_uptime', 'batch_price');
    }

    function autofillProfileDefaults(pName, qId, upId, prId) {
      if (!pName) return;
      const lower = pName.toLowerCase();
      const qInput = gid(qId);
      const upInput = gid(upId);
      const prInput = gid(prId);

      if (lower.includes('100gb')) { if (qInput) qInput.value = '102400'; }
      else if (lower.includes('50gb')) { if (qInput) qInput.value = '51200'; }
      else if (lower.includes('30gb') || lower.includes('30day')) { if (qInput) qInput.value = '30720'; if (upInput) upInput.value = '30d'; if (prInput) prInput.value = '15000'; }
      else if (lower.includes('20gb')) { if (qInput) qInput.value = '20480'; }
      else if (lower.includes('10gb')) { if (qInput) qInput.value = '10240'; if (prInput) prInput.value = '5000'; }
      else if (lower.includes('5gb')) { if (qInput) qInput.value = '5120'; if (prInput) prInput.value = '2500'; }
      else if (lower.includes('2gb')) { if (qInput) qInput.value = '2048'; if (prInput) prInput.value = '1000'; }
      else if (lower.includes('1gb')) { if (qInput) qInput.value = '1024'; if (prInput) prInput.value = '500'; }
      else if (lower.includes('500mb')) { if (qInput) qInput.value = '500'; if (prInput) prInput.value = '300'; }
      else if (lower.includes('2hour')) { if (upInput) upInput.value = '2h'; if (prInput) prInput.value = '300'; }
      else if (lower.includes('1hour')) { if (upInput) upInput.value = '1h'; if (prInput) prInput.value = '200'; }
    }

    // ── BATCH GENERATE LIVE MODAL ────────────────────────────────
    function openBatchGenerateModal() {
      populateProfileSelects(CURRENT_ROUTER_PROFILES);
      gid('batch_count').value = '10';
      gid('batch_digits').value = '6';
      if (CURRENT_ROUTER_PROFILES && CURRENT_ROUTER_PROFILES.length > 0) {
        gid('batch_profile').value = CURRENT_ROUTER_PROFILES[0].name;
        onBatchProfileSelected(CURRENT_ROUTER_PROFILES[0].name);
      }
      openModal('modal_batch_generate_live');
    }

    async function submitBatchGenerateLive() {
      const rid = CURRENT_ROUTER_STATUS_ID || CURRENT_CHECKING_RID;
      if (!rid) return alert('ချိတ်ဆက်ထားသော Router ကို ရွေးချယ်ပါ');

      const count = parseInt(gid('batch_count').value || 10);
      const digits = parseInt(gid('batch_digits').value || 6);
      const prof = gid('batch_profile').value.trim() || 'default';
      const up = gid('batch_uptime').value;
      const quota = parseInt(gid('batch_quota').value || 0);
      const price = parseInt(gid('batch_price').value || 0);

      const btn = gid('btn_batch_submit');
      if (btn) { btn.disabled = true; btn.textContent = `⏳ ဘောက်ချာ ${count} စောင် Router သို့ ထည့်သွင်းနေပါသည်...`; }

      try {
        const res = await api(`/api/routers/${rid}/batch-vouchers`, 'POST', {
          count: count,
          digits: digits,
          profile: prof,
          uptime_str: up,
          quota_mb: quota,
          price_mmk: price
        });

        if (res && res.status === 'ok') {
          showToast('Batch Vouchers Success', `ဘောက်ချာ ${res.count} စောင်ကို Profile '${prof}' ဖြင့် Router သို့ အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ!`, 'success');
          closeModal('modal_batch_generate_live');
          await loadLiveVouchers();
        } else {
          showToast('Batch Error', res.detail || 'Failed to generate vouchers', 'error');
        }
      } catch (err) {
        showToast('Batch Error', err.message || 'Error generating batch vouchers', 'error');
      } finally {
        if (btn) { btn.disabled = false; btn.textContent = '⚡ အုပ်စုလိုက် ထုတ်ဝေမည် (Generate)'; }
      }
    }
'''

# Hook populateProfileSelects inside loadRouterProfiles
target_hook = 'CURRENT_ROUTER_PROFILES = res.profiles;'
replacement_hook = 'CURRENT_ROUTER_PROFILES = res.profiles; populateProfileSelects(res.profiles);'
if target_hook in html:
    html = html.replace(target_hook, replacement_hook, 1)
    print("5. Hooked populateProfileSelects into loadRouterProfiles.")

# Update generateRandomVoucherCode to use lv_digits
old_gen_random = '''    function generateRandomVoucherCode() {
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      gid('lv_name').value = code;
    }'''

new_gen_random = '''    function generateRandomVoucherCode() {
      const digits = parseInt((gid('lv_digits') && gid('lv_digits').value) || 6);
      const min = Math.pow(10, digits - 1);
      const max = Math.pow(10, digits) - 1;
      const code = Math.floor(min + Math.random() * (max - min + 1)).toString();
      gid('lv_name').value = code;
    }'''

if old_gen_random in html:
    html = html.replace(old_gen_random, new_gen_random, 1)
    print("6. Updated generateRandomVoucherCode to respect digits length.")

# Update openCreateLiveVoucherModal to populate profiles
old_open_single = '''    function openCreateLiveVoucherModal() {
      generateRandomVoucherCode();
      gid('lv_pass').value = '';
      gid('lv_profile').value = 'default';
      gid('lv_uptime').value = '1d';
      gid('lv_quota').value = '0';
      gid('lv_price').value = '500';
      openModal('modal_create_live_voucher');
    }'''

new_open_single = '''    function openCreateLiveVoucherModal() {
      populateProfileSelects(CURRENT_ROUTER_PROFILES);
      generateRandomVoucherCode();
      gid('lv_pass').value = '';
      if (CURRENT_ROUTER_PROFILES && CURRENT_ROUTER_PROFILES.length > 0) {
        gid('lv_profile').value = CURRENT_ROUTER_PROFILES[0].name;
        onSingleProfileSelected(CURRENT_ROUTER_PROFILES[0].name);
      } else {
        gid('lv_profile').value = 'default';
        gid('lv_uptime').value = '1d';
        gid('lv_quota').value = '0';
        gid('lv_price').value = '500';
      }
      openModal('modal_create_live_voucher');
    }'''

if old_open_single in html:
    html = html.replace(old_open_single, new_open_single, 1)
    print("7. Updated openCreateLiveVoucherModal.")

# Update submitCreateLiveVoucher to use showToast
old_submit_single = '''      if (res && res.status === 'ok') {
        alert(`ဘောက်ချာ '${name}' ကို ရူတာသို့ အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ!`);
        closeModal('modal_create_live_voucher');
        loadLiveVouchers();
      } else {
        alert(res.detail || 'Failed to inject voucher');
      }'''

new_submit_single = '''      if (res && res.status === 'ok') {
        showToast('Voucher Created', `ဘောက်ချာ '${name}' ကို Profile '${prof}' ဖြင့် Router သို့ အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ!`, 'success');
        closeModal('modal_create_live_voucher');
        loadLiveVouchers();
      } else {
        showToast('Creation Failed', res.detail || 'Failed to inject voucher', 'error');
      }'''

if old_submit_single in html:
    html = html.replace(old_submit_single, new_submit_single, 1)
    print("8. Updated submitCreateLiveVoucher with showToast.")

# Add js_code_to_add before closing script tag
html = html.replace('</script>', js_code_to_add + "\n  </script>", 1)
print("9. Injected JavaScript logic.")

# Write back to VPS
with sftp.file('/opt/hotspot-cloud/templates/index.html', 'w') as f:
    f.write(html.encode('utf-8'))
print("Successfully deployed updated index.html to /opt/hotspot-cloud/templates/index.html!")
