import paramiko, re

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('3.84.81.152', username='ubuntu', key_filename=r'C:\Users\localhost\Downloads\mikrotik.pem')
sftp = ssh.open_sftp()

with sftp.file('/opt/hotspot-cloud/templates/index.html', 'r') as f:
    html = f.read().decode('utf-8')

# Find modal_create_live_voucher
pattern = r'(<div class="modal-overlay" id="modal_create_live_voucher">.*?<div class="modal-card".*?)(<div class="form-group">.*?<button class="btn-primary" onclick="submitCreateLiveVoucher\(\)">⚡ ရူတာသို့ ထည့်သွင်းမည် \(Inject\)</button>.*?</div>\s*</div>\s*</div>)'

replacement = '''<div class="modal-overlay" id="modal_create_live_voucher">
    <div class="modal-card" style="max-width: 480px;">
      <h3 style="color:#FFF; font-size:18px; margin-bottom:14px; display:flex; align-items:center; gap:8px;">⚡ ရူတာသို့ တိုက်ရိုက် ဘောက်ချာ အသစ်ထည့်သွင်းခြင်း</h3>
      <div class="form-group">
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
      </div>
      <div class="form-group">
        <label>အသုံးပြုနိုင်မည့်အချိန် (Time Limit)</label>
        <select class="form-control" id="lv_uptime">
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
        <input class="form-control" type="number" id="lv_quota" value="0" placeholder="0 ဆိုပါက အကန့်အသတ်မရှိ">
      </div>
      <div class="form-group">
        <label>စျေးနှုန်း (MMK)</label>
        <input class="form-control" type="number" id="lv_price" value="500" placeholder="500">
      </div>
      <div style="display:flex; justify-content:flex-end; gap:10px; margin-top:18px;">
        <button class="btn-sm btn-danger" onclick="closeModal('modal_create_live_voucher')">ပယ်ဖျက်မည်</button>
        <button class="btn-primary" onclick="submitCreateLiveVoucher()">⚡ ရူတာသို့ ထည့်သွင်းမည် (Inject)</button>
      </div>
    </div>
  </div>'''

match = re.search(pattern, html, re.DOTALL)
if match:
    html = html[:match.start()] + replacement + html[match.end():]
    print("Replaced modal_create_live_voucher successfully.")

# Also update generateRandomVoucherCode
gen_old = '''    function generateRandomVoucherCode() {
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      gid('lv_name').value = code;
    }'''

gen_new = '''    function generateRandomVoucherCode() {
      const digits = parseInt((gid('lv_digits') && gid('lv_digits').value) || 6);
      const min = Math.pow(10, digits - 1);
      const max = Math.pow(10, digits) - 1;
      const code = Math.floor(min + Math.random() * (max - min + 1)).toString();
      gid('lv_name').value = code;
    }'''

if gen_old in html:
    html = html.replace(gen_old, gen_new, 1)
    print("Updated generateRandomVoucherCode.")

with sftp.file('/opt/hotspot-cloud/templates/index.html', 'w') as f:
    f.write(html.encode('utf-8'))
print("Updated /opt/hotspot-cloud/templates/index.html successfully.")
