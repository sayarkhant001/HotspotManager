/* ─────────────────────────────────────────────────────────────
   အရိပ်စစ် WIFI (A YEIK SITT 1) – main.js
   Supports 4 to 12+ digit vouchers, account auth, persistent session remembering,
   seamless auto-login after router restarts/reconnects, and quota/expiration detection.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  var STORAGE_KEY = 'ayeiksitt_voucher';
  var STORAGE_TIME_KEY = 'ayeiksitt_voucher_time';
  var STORAGE_AUTH_TYPE_KEY = 'ayeiksitt_auth_type';
  var STORAGE_ACC_USER_KEY = 'ayeiksitt_account_user';
  var STORAGE_ACC_PASS_KEY = 'ayeiksitt_account_pass';

  function gid(id) { return document.getElementById(id); }

  /* ── LocalStorage Caching ────────────────────────────────── */
  function saveVoucher(code) {
    if (!code) return;
    try {
      localStorage.setItem(STORAGE_KEY, code.trim());
      localStorage.setItem(STORAGE_TIME_KEY, Date.now().toString());
      localStorage.setItem(STORAGE_AUTH_TYPE_KEY, 'voucher');
    } catch (e) {}
  }

  function saveAccount(user, pass) {
    if (!user) return;
    try {
      localStorage.setItem(STORAGE_ACC_USER_KEY, user.trim());
      localStorage.setItem(STORAGE_ACC_PASS_KEY, pass || '');
      localStorage.setItem(STORAGE_TIME_KEY, Date.now().toString());
      localStorage.setItem(STORAGE_AUTH_TYPE_KEY, 'account');
    } catch (e) {}
  }

  function clearStoredCredentials() {
    try {
      localStorage.removeItem(STORAGE_KEY);
      localStorage.removeItem(STORAGE_TIME_KEY);
      localStorage.removeItem(STORAGE_AUTH_TYPE_KEY);
      localStorage.removeItem(STORAGE_ACC_USER_KEY);
      localStorage.removeItem(STORAGE_ACC_PASS_KEY);
    } catch (e) {}
  }

  function clearStoredVoucher() {
    clearStoredCredentials();
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem(STORAGE_KEY);
      var time = localStorage.getItem(STORAGE_TIME_KEY);
      if (!code) return '';
      // Retain voucher for up to 30 days
      if (time && (Date.now() - parseInt(time, 10)) > 30 * 24 * 3600 * 1000) {
        clearStoredCredentials();
        return '';
      }
      return code.trim();
    } catch (e) {
      return '';
    }
  }

  function getStoredAccount() {
    try {
      var user = localStorage.getItem(STORAGE_ACC_USER_KEY);
      var pass = localStorage.getItem(STORAGE_ACC_PASS_KEY) || '';
      var time = localStorage.getItem(STORAGE_TIME_KEY);
      if (!user) return null;
      if (time && (Date.now() - parseInt(time, 10)) > 30 * 24 * 3600 * 1000) {
        clearStoredCredentials();
        return null;
      }
      return { user: user.trim(), pass: pass };
    } catch (e) {
      return null;
    }
  }

  function getStoredAuthType() {
    try {
      return localStorage.getItem(STORAGE_AUTH_TYPE_KEY) || 'voucher';
    } catch (e) {
      return 'voucher';
    }
  }

  /* ── Query Parameter Parsing ─────────────────────────────── */
  function parseQueryParams() {
    var params = {};
    var search = window.location.search.substring(1);
    if (!search) return params;
    var pairs = search.split('&');
    for (var i = 0; i < pairs.length; i++) {
      var pair = pairs[i].split('=');
      var key = decodeURIComponent(pair[0] || '').trim().toLowerCase();
      var val = decodeURIComponent(pair[1] || '').trim();
      if (key) params[key] = val;
    }
    return params;
  }

  /* ── Router Error Check ──────────────────────────────────── */
  function getRouterError() {
    var msgBox = gid('login_msg');
    if (msgBox) {
      var txt = msgBox.textContent || msgBox.innerText || '';
      txt = txt.trim();
      if (txt && txt.indexOf('$(') === -1 && txt !== '') {
        return txt;
      }
    }
    var q = parseQueryParams();
    if (q.error) {
      return q.error;
    }
    return '';
  }

  /* ── Voucher Input Logic (Supports 4 to 12 Digits) ───────── */
  function initVoucherInput() {
    var vInput = gid('voucher_input');
    var clearBtn = gid('clear_voucher_btn');
    var pasteBtn = gid('paste_voucher_btn');
    if (!vInput) return;

    function formatVoucherValue() {
      var val = vInput.value.replace(/[\s]/g, '').toUpperCase();
      vInput.value = val;
      if (clearBtn) clearBtn.style.display = val.length > 0 ? 'flex' : 'none';
      if (pasteBtn) pasteBtn.style.display = val.length === 0 ? 'flex' : 'none';
    }

    vInput.addEventListener('input', formatVoucherValue);

    if (clearBtn) {
      clearBtn.addEventListener('click', function () {
        vInput.value = '';
        clearStoredVoucher();
        formatVoucherValue();
        vInput.focus();
      });
    }

    if (pasteBtn && navigator.clipboard && navigator.clipboard.readText) {
      pasteBtn.addEventListener('click', function () {
        navigator.clipboard.readText().then(function (text) {
          if (text) {
            vInput.value = text.trim().toUpperCase();
            formatVoucherValue();
            vInput.focus();
          }
        }).catch(function () {
          vInput.focus();
        });
      });
    } else if (pasteBtn) {
      pasteBtn.style.display = 'none';
    }

    var q = parseQueryParams();
    var prefill = q.voucher || q.code || q.username || getStoredVoucher();
    if (prefill) {
      vInput.value = prefill.toUpperCase();
      formatVoucherValue();
    }
  }

  /* ── Password Visibility Toggle ──────────────────────────── */
  function initPasswordToggle() {
    var pwInput = gid('account_password');
    var toggleBtn = gid('toggle_password_btn');
    if (!pwInput || !toggleBtn) return;

    toggleBtn.addEventListener('click', function () {
      var isPw = pwInput.type === 'password';
      pwInput.type = isPw ? 'text' : 'password';
      toggleBtn.innerHTML = isPw
        ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>'
        : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    });
  }

  /* ── Tab Switching ───────────────────────────────────────── */
  function initTabs() {
    var tabVoucher = gid('tab_voucher');
    var tabAccount = gid('tab_account');
    var panelVoucher = gid('panel_voucher');
    var panelAccount = gid('panel_account');
    if (!tabVoucher || !tabAccount || !panelVoucher || !panelAccount) return;

    function switchTab(mode) {
      if (mode === 'voucher') {
        tabVoucher.classList.add('active');
        tabAccount.classList.remove('active');
        panelVoucher.classList.remove('hide');
        panelAccount.classList.add('hide');
        var vIn = gid('voucher_input');
        if (vIn) setTimeout(function () { vIn.focus(); }, 60);
      } else {
        tabAccount.classList.add('active');
        tabVoucher.classList.remove('active');
        panelAccount.classList.remove('hide');
        panelVoucher.classList.add('hide');
        var uIn = gid('account_input');
        if (uIn) setTimeout(function () { uIn.focus(); }, 60);
      }
    }

    tabVoucher.addEventListener('click', function () { switchTab('voucher'); });
    tabAccount.addEventListener('click', function () { switchTab('account'); });
  }

  /* ── Form Submission & Intelligent Auto-Reconnect ────────── */
  function initForm() {
    var form = gid('login_form');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');
    var pInput = gid('account_password');
    var uField = gid('username_field');
    var pField = gid('password_field');
    var msgBox = gid('login_msg');
    var submitBtn = gid('login_btn');
    if (!form || !uField || !pField) return;

    function showError(text) {
      if (!msgBox) return;
      msgBox.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink:0;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg> <span>' + text + '</span>';
      msgBox.classList.remove('hide');
    }

    var routerErr = getRouterError();

    // 1. If router returned an error (quota reached, expired, etc.)
    if (routerErr) {
      clearStoredCredentials();
      if (vInput) vInput.value = '';

      var errLower = routerErr.toLowerCase();
      var friendlyMsg = routerErr;

      if (errLower.indexOf('traffic limit') !== -1 || errLower.indexOf('transfer limit') !== -1) {
        friendlyMsg = 'ဒေတာ ကုန်သွားပါပြီ (Data Quota Reached) – ကျေးဇူးပြု၍ ဘောင်ချာအသစ် ဝယ်ယူသုံးစွဲပါ';
      } else if (errLower.indexOf('uptime limit') !== -1 || errLower.indexOf('session limit') !== -1) {
        friendlyMsg = 'ဘောင်ချာ သက်တမ်းကုန်သွားပါပြီ (Validity Expired) – ကျေးဇူးပြု၍ ဘောင်ချာအသစ် ဝယ်ယူသုံးစွဲပါ';
      } else if (errLower.indexOf('not found') !== -1 || errLower.indexOf('invalid username') !== -1) {
        friendlyMsg = 'ဘောင်ချာကုဒ် မှားယွင်းနေပါသည် (Invalid Voucher Code) – ပြန်လည်စစ်ဆေးပေးပါ';
      }

      showError(friendlyMsg);
      if (vInput) vInput.focus();
      return;
    }

    // 2. Intelligent Auto-Login after Router Restart or Reconnect
    var q = parseQueryParams();
    var authType = getStoredAuthType();
    var storedVoucher = getStoredVoucher();
    var storedAccount = getStoredAccount();

    if (!q.manual) {
      // Priority A: Saved Account
      if (authType === 'account' && storedAccount && storedAccount.user) {
        uField.value = storedAccount.user;
        pField.value = storedAccount.pass;
        saveAccount(storedAccount.user, storedAccount.pass);

        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<span style="display:inline-block;width:18px;height:18px;border:2.5px solid rgba(0,0,0,0.2);border-top-color:#000;border-radius:50%;animation:spin .7s linear infinite;margin-right:8px;vertical-align:middle;"></span> အကောင့်ဖြင့် အလိုအလျောက် ပြန်လည်ချိတ်ဆက်နေပါသည်...';
        }

        setTimeout(function () { form.submit(); }, 150);
        return;
      }

      // Priority B: Saved Voucher
      var autoVoucher = q.voucher || q.code || storedVoucher;
      if (autoVoucher) {
        var cleanCode = autoVoucher.trim().replace(/[\s]/g, '').toUpperCase();
        if (cleanCode.length >= 3) {
          uField.value = cleanCode;
          pField.value = cleanCode;
          saveVoucher(cleanCode);

          if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span style="display:inline-block;width:18px;height:18px;border:2.5px solid rgba(0,0,0,0.2);border-top-color:#000;border-radius:50%;animation:spin .7s linear infinite;margin-right:8px;vertical-align:middle;"></span> ဘောင်ချာဖြင့် အလိုအလျောက် ပြန်လည်ချိတ်ဆက်နေပါသည်...';
          }

          setTimeout(function () { form.submit(); }, 150);
          return;
        }
      }
    }

    // Prefill account input if available
    if (storedAccount && aInput) {
      aInput.value = storedAccount.user;
      if (pInput) pInput.value = storedAccount.pass;
    }

    // 3. Normal Manual Submission
    form.addEventListener('submit', function (e) {
      var isVoucherTab = gid('tab_voucher') && gid('tab_voucher').classList.contains('active');

      if (isVoucherTab) {
        var code = (vInput ? vInput.value : '').trim().replace(/[\s]/g, '').toUpperCase();
        if (!code || code.length < 3) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ ဘောင်ချာကုဒ် (အနည်းဆုံး ၄ လုံး) ရိုက်ထည့်ပါ');
          if (vInput) vInput.focus();
          return;
        }

        saveVoucher(code);
        uField.value = code;
        pField.value = code;
      } else {
        var user = (aInput ? aInput.value : '').trim();
        var pass = (pInput ? pInput.value : '').trim();

        if (!user) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ အသုံးပြုသူအမည် ထည့်သွင်းပါ');
          if (aInput) aInput.focus();
          return;
        }
        if (!pass) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ လျှို့ဝှက်နံပါတ် ထည့်သွင်းပါ');
          if (pInput) pInput.focus();
          return;
        }

        saveAccount(user, pass);
        uField.value = user;
        pField.value = pass;
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span style="display:inline-block;width:18px;height:18px;border:2.5px solid rgba(0,0,0,0.2);border-top-color:#000;border-radius:50%;animation:spin .7s linear infinite;margin-right:8px;vertical-align:middle;"></span> အင်တာနက် ချိတ်ဆက်နေပါသည်...';
      }
    });
  }

  /* ── Status Page Live Calculations ───────────────────────── */
  function initStatusPage() {
    var bytesInEl = gid('stat_bytes_in');
    var bytesOutEl = gid('stat_bytes_out');
    var dataUsedEl = gid('stat_data_used');
    var dataRemainingEl = gid('stat_data_remaining');
    var timeLeftEl = gid('stat_timeleft');
    var uEl = gid('display_username');
    var upEl = gid('stat_uptime');

    if (uEl && uEl.textContent.indexOf('$(') !== -1) uEl.textContent = '44851299';
    if (upEl && upEl.textContent.indexOf('$(') !== -1) upEl.textContent = 'Active';

    if (timeLeftEl) {
      var tl = timeLeftEl.textContent.trim();
      if (!tl || tl.indexOf('$(') !== -1 || tl === '0s' || tl === '0') {
        timeLeftEl.textContent = 'အကန့်အသတ်မရှိ (Unlimited)';
      }
    }

    function formatBytes(bytes) {
      var b = parseFloat(bytes) || 0;
      if (b < 1024) return b + ' B';
      if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
      if (b < 1073741824) return (b / 1048576).toFixed(1) + ' MB';
      return (b / 1073741824).toFixed(2) + ' GB';
    }

    var inBytes = parseFloat(bytesInEl ? bytesInEl.textContent : 0) || 0;
    var outBytes = parseFloat(bytesOutEl ? bytesOutEl.textContent : 0) || 0;
    var totalBytes = inBytes + outBytes;
    if (totalBytes > 0 && dataUsedEl) {
      dataUsedEl.textContent = formatBytes(totalBytes);
    }

    if (dataRemainingEl) {
      var rawRem = parseFloat(dataRemainingEl.textContent.trim());
      if (!isNaN(rawRem) && rawRem > 0) {
        dataRemainingEl.textContent = formatBytes(rawRem);
      } else {
        var uName = (gid('display_username') ? gid('display_username').textContent : '').toUpperCase();
        var quotaMatch = uName.match(/(\d+)\s*(GB|MB|G|M)/i);
        if (quotaMatch) {
          var num = parseFloat(quotaMatch[1]);
          var unit = quotaMatch[2].toUpperCase();
          var quotaBytes = num * (unit.indexOf('G') !== -1 ? 1073741824 : 1048576);
          var rem = Math.max(0, quotaBytes - totalBytes);
          dataRemainingEl.textContent = formatBytes(rem);
        } else {
          dataRemainingEl.textContent = 'အကန့်အသတ်မရှိ (Unlimited)';
        }
      }
    }
  }

  /* ── DOM Ready Initialization ────────────────────────────── */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      initTabs();
      initVoucherInput();
      initPasswordToggle();
      initForm();
      initStatusPage();
    });
  } else {
    initTabs();
    initVoucherInput();
    initPasswordToggle();
    initForm();
    initStatusPage();
  }
})();
