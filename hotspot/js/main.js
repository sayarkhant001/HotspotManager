/* ─────────────────────────────────────────────────────────────
   Kyaw Gyi WiFi – High-Performance Portal Automation
   1-Click Direct Login • Zero Delay • Burmese Error Handling
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  var form = document.getElementById('login_form');
  var vInput = document.getElementById('voucher_input');
  var passHidden = document.getElementById('password_hidden');
  var memberUser = document.getElementById('member_user');
  var memberPass = document.getElementById('member_pass');
  var msgBox = document.getElementById('login_msg');
  var msgText = document.getElementById('login_msg_text');
  var btnConnect = document.getElementById('btn_connect');
  var btnText = document.getElementById('btn_text');

  var currentMode = 'voucher';

  // 1. Password synchronization for voucher mode
  function syncPassword() {
    if (currentMode === 'voucher' && vInput && passHidden) {
      passHidden.value = vInput.value.trim();
    }
  }

  if (vInput) {
    vInput.addEventListener('input', syncPassword);
    vInput.addEventListener('change', syncPassword);
  }

  // 2. Mode Switching (Voucher vs Member Account)
  window.switchMode = function (mode) {
    currentMode = mode;
    var tabV = document.getElementById('tab_voucher');
    var tabM = document.getElementById('tab_member');
    var secV = document.getElementById('section_voucher');
    var secM = document.getElementById('section_member');

    if (mode === 'voucher') {
      tabV.classList.add('active');
      tabM.classList.remove('active');
      secV.classList.remove('hide');
      secM.classList.add('hide');

      vInput.name = 'username';
      vInput.required = true;
      passHidden.name = 'password';

      if (memberUser) { memberUser.name = ''; memberUser.required = false; }
      if (memberPass) { memberPass.name = ''; memberPass.required = false; }

      syncPassword();
      setTimeout(function () { vInput.focus(); }, 50);
    } else {
      tabM.classList.add('active');
      tabV.classList.remove('active');
      secM.classList.remove('hide');
      secV.classList.add('hide');

      vInput.name = '';
      vInput.required = false;
      passHidden.name = '';

      if (memberUser) { memberUser.name = 'username'; memberUser.required = true; }
      if (memberPass) { memberPass.name = 'password'; memberPass.required = true; }

      if (memberUser) {
        setTimeout(function () { memberUser.focus(); }, 50);
      }
    }
  };

  // 3. Paste Clipboard Handler
  window.pasteVoucher = function () {
    if (navigator.clipboard && navigator.clipboard.readText) {
      navigator.clipboard.readText().then(function (text) {
        if (text) {
          vInput.value = text.trim();
          syncPassword();
          vInput.focus();
        }
      }).catch(function () {
        vInput.focus();
      });
    } else {
      vInput.focus();
    }
  };

  // 4. Show Friendly Error (Strictly removes .hide, only shows when content exists)
  function showError(msg) {
    if (!msgBox || !msgText) return;
    var trimmed = (msg || '').trim();
    if (!trimmed) {
      msgBox.classList.add('hide');
      return;
    }
    msgText.innerText = trimmed;
    msgBox.classList.remove('hide');
  }

  // 5. Inspect Server-Side Error from RouterOS
  var rawError = '';
  if (msgText && msgText.innerText) {
    rawError = msgText.innerText.trim();
  }

  if (rawError && rawError.indexOf('$(') === -1 && rawError.length > 0) {
    var errLower = rawError.toLowerCase();
    var friendly = rawError;

    if (errLower.indexOf('traffic limit') !== -1 || errLower.indexOf('transfer limit') !== -1) {
      friendly = 'ဒေတာ ကုန်သွားပါပြီ (Data Quota Finished) – ကျေးဇူးပြု၍ ဘောင်ချာအသစ် ဝယ်ယူသုံးစွဲပါ';
      try { localStorage.clear(); } catch(e) {}
    } else if (errLower.indexOf('uptime limit') !== -1 || errLower.indexOf('session limit') !== -1) {
      friendly = 'ဘောင်ချာ သက်တမ်းကုန်သွားပါပြီ (Validity Expired) – ကျေးဇူးပြု၍ ဘောင်ချာအသစ် ဝယ်ယူသုံးစွဲပါ';
      try { localStorage.clear(); } catch(e) {}
    } else if (errLower.indexOf('invalid username') !== -1 || errLower.indexOf('invalid password') !== -1) {
      friendly = 'ဘောင်ချာကုဒ် မှားယွင်းနေပါသည် (Invalid Code) – သေချာစစ်ဆေးပြီး ပြန်လည်ရိုက်ထည့်ပါ';
    } else if (errLower.indexOf('user') !== -1 && errLower.indexOf('not found') !== -1) {
      friendly = 'ဘောင်ချာကုဒ် မရှိပါ သို့မဟုတ် သက်တမ်းကုန်သွားပါပြီ';
      try { localStorage.clear(); } catch(e) {}
    } else if (errLower.indexOf('already logged in') !== -1) {
      friendly = 'ဤဘောင်ချာကို အခြားစက်တစ်ခုတွင် အသုံးပြုနေပါသည်';
    }

    showError(friendly);
    sessionStorage.setItem('kyawgyi_error_occurred', '1');
  } else {
    // If no error, ensure message box is strictly hidden
    if (msgBox) msgBox.classList.add('hide');
  }

  // 6. Native Form Submit
  if (form) {
    form.addEventListener('submit', function (e) {
      if (currentMode === 'voucher') {
        var code = (vInput ? vInput.value : '').trim();
        if (!code) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ ဘောင်ချာကုဒ် အရင်ရိုက်ထည့်ပါ');
          if (vInput) vInput.focus();
          return false;
        }
        syncPassword();
        try {
          localStorage.setItem('kyawgyi_wifi_voucher', code);
          localStorage.setItem('kyawgyi_wifi_auth_type', 'voucher');
          localStorage.setItem('kyawgyi_wifi_time', Date.now().toString());
        } catch (ex) {}
      } else {
        var u = (memberUser ? memberUser.value : '').trim();
        var p = (memberPass ? memberPass.value : '').trim();
        if (!u || !p) {
          e.preventDefault();
          showError('Username နှင့် Password နှစ်ခုစလုံး ဖြည့်သွင်းပါ');
          return false;
        }
      }

      if (btnText) btnText.innerText = 'ချိတ်ဆက်နေပါသည်...';
      if (btnConnect) btnConnect.style.opacity = '0.85';
      return true;
    });
  }

  // 7. Auto-fill Last Used Voucher (without auto-submitting dead codes)
  try {
    var savedVoucher = localStorage.getItem('kyawgyi_wifi_voucher');
    var hadError = sessionStorage.getItem('kyawgyi_error_occurred');
    if (savedVoucher && !hadError && vInput && !vInput.value) {
      vInput.value = savedVoucher.trim();
      syncPassword();
    }
    sessionStorage.removeItem('kyawgyi_error_occurred');
  } catch(e) {}

})();
