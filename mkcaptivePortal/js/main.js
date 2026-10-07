/* ─────────────────────────────────────────────────────────────
   Yadanar Tun WiFi – main.js
   Supports 4 to 12+ digit vouchers, account auth, persistent session remembering,
   seamless auto-login after router restarts/reconnects, and quota/expiration detection.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  var STORAGE_KEY = 'yadanartun_wifi_voucher';
  var STORAGE_TIME_KEY = 'yadanartun_wifi_voucher_time';
  var STORAGE_AUTH_TYPE_KEY = 'yadanartun_wifi_auth_type';
  var STORAGE_ACC_USER_KEY = 'yadanartun_wifi_account_user';
  var STORAGE_ACC_PASS_KEY = 'yadanartun_wifi_account_pass';

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
      // Clean legacy keys if any
      localStorage.removeItem('kyawgyi_wifi_voucher');
      localStorage.removeItem('kyawgyi_wifi_voucher_time');
      localStorage.removeItem('kyawgyi_wifi_auth_type');
      localStorage.removeItem('kyawgyi_wifi_account_user');
      localStorage.removeItem('kyawgyi_wifi_account_pass');
      localStorage.removeItem('yt_wifi_voucher');
      localStorage.removeItem('yt_wifi_voucher_time');
    } catch (e) {}
  }

  function clearStoredVoucher() {
    clearStoredCredentials();
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem(STORAGE_KEY);
      var time = localStorage.getItem(STORAGE_TIME_KEY);
      if (!code) {
        code = localStorage.getItem('kyawgyi_wifi_voucher');
        time = localStorage.getItem('kyawgyi_wifi_voucher_time');
      }
      if (!code) {
        code = localStorage.getItem('yt_wifi_voucher');
        time = localStorage.getItem('yt_wifi_voucher_time');
      }
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
      if (!user) {
        user = localStorage.getItem('kyawgyi_wifi_account_user');
        pass = localStorage.getItem('kyawgyi_wifi_account_pass') || '';
      }
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
      return localStorage.getItem(STORAGE_AUTH_TYPE_KEY) || localStorage.getItem('kyawgyi_wifi_auth_type') || 'voucher';
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
      // If template variable was not replaced or empty
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
      if (clearBtn) {
        clearBtn.style.display = val.length > 0 ? 'flex' : 'none';
      }
      if (pasteBtn) {
        pasteBtn.style.display = val.length === 0 ? 'flex' : 'none';
      }
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

    // Prefill from URL (?voucher=XXXX or ?code=XXXX) or LocalStorage
    var q = parseQueryParams();
    var prefill = q.voucher || q.code || q.username || getStoredVoucher();
    if (prefill) {
      vInput.value = prefill.toUpperCase();
      formatVoucherValue();
    }
  }

  /* ── Password Visibility Toggle ──────────────────────────── */
  function initPasswordToggle() {
    var toggleBtn = gid('toggle_password_btn');
    var passInput = gid('account_password');
    if (!toggleBtn || !passInput) return;

    toggleBtn.addEventListener('click', function () {
      var isPassword = passInput.type === 'password';
      passInput.type = isPassword ? 'text' : 'password';
      toggleBtn.innerHTML = isPassword
        ? '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>'
        : '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    });
  }

  /* ── Tab Switching (Voucher vs Account) ────────────────────── */
  function initTabs() {
    var tabVoucher = gid('tab_voucher');
    var tabAccount = gid('tab_account');
    var panelVoucher = gid('panel_voucher');
    var panelAccount = gid('panel_account');
    if (!tabVoucher || !tabAccount || !panelVoucher || !panelAccount) return;

    tabVoucher.addEventListener('click', function () {
      tabVoucher.classList.add('active');
      tabAccount.classList.remove('active');
      panelVoucher.classList.remove('hide');
      panelAccount.classList.add('hide');
      var vIn = gid('voucher_input');
      if (vIn) vIn.focus();
    });

    tabAccount.addEventListener('click', function () {
      tabAccount.classList.add('active');
      tabVoucher.classList.remove('active');
      panelAccount.classList.remove('hide');
      panelVoucher.classList.add('hide');
      var aIn = gid('account_input');
      if (aIn) aIn.focus();
    });
  }

  /* ── Login Form & Intelligent Auto-Reconnect ──────────────── */
  function initLoginForm() {
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
      msgBox.innerHTML = '<span>&#9888;</span> <span>' + text + '</span>';
      msgBox.classList.remove('hide');
    }

    var routerErr = getRouterError();

    // 1. If router returned an error (e.g., quota reached, uptime expired, user deleted)
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

    // 2. Intelligent Auto-Login after Router Restart or Network Reconnect
    // If no error occurred and client has saved credentials, automatically log them in without prompting!
    var q = parseQueryParams();
    var authType = getStoredAuthType();
    var storedVoucher = getStoredVoucher();
    var storedAccount = getStoredAccount();

    if (!q.manual) {
      // Priority A: Saved Account credentials
      if (authType === 'account' && storedAccount && storedAccount.user) {
        uField.value = storedAccount.user;
        pField.value = storedAccount.pass;
        saveAccount(storedAccount.user, storedAccount.pass);

        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> အကောင့်ဖြင့် အလိုအလျောက် ပြန်လည်ချိတ်ဆက်နေပါသည်...';
        }

        setTimeout(function () {
          form.submit();
        }, 150);
        return;
      }

      // Priority B: Saved Voucher code
      var autoVoucher = q.voucher || q.code || storedVoucher;
      if (autoVoucher) {
        var cleanCode = autoVoucher.trim().replace(/[\s]/g, '').toUpperCase();
        if (cleanCode.length >= 3) {
          uField.value = cleanCode;
          pField.value = cleanCode;
          saveVoucher(cleanCode);

          if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> ဘောင်ချာဖြင့် အလိုအလျောက် ပြန်လည်ချိတ်ဆက်နေပါသည်...';
          }

          setTimeout(function () {
            form.submit();
          }, 150);
          return;
        }
      }
    }

    // Prefill account input if available for convenience
    if (storedAccount && aInput) {
      aInput.value = storedAccount.user;
      if (pInput) pInput.value = storedAccount.pass;
    }

    // 3. Normal Manual Submission
    form.addEventListener('submit', function (e) {
      var isVoucher = gid('tab_voucher') && gid('tab_voucher').classList.contains('active');

      if (isVoucher) {
        var code = (vInput ? vInput.value : '').trim().replace(/[\s]/g, '').toUpperCase();
        if (!code) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ ဘောင်ချာကုဒ်နံပါတ် ရိုက်ထည့်ပါ');
          if (vInput) vInput.focus();
          return;
        }
        if (code.length < 3) {
          e.preventDefault();
          showError('ဘောင်ချာကုဒ် အနည်းဆုံး ၄ လုံး ရှိရပါမည်');
          if (vInput) vInput.focus();
          return;
        }

        saveVoucher(code);
        uField.value = code;
        pField.value = code;
      } else {
        var user = (aInput ? aInput.value : '').trim();
        var pass = (pInput ? pInput.value : '');
        if (!user) {
          e.preventDefault();
          showError('ကျေးဇူးပြု၍ အသုံးပြုသူအမည် ရိုက်ထည့်ပါ');
          if (aInput) aInput.focus();
          return;
        }
        saveAccount(user, pass);
        uField.value = user;
        pField.value = pass;
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> ချိတ်ဆက်နေပါသည်...';
      }
    });
  }

  /* ── Status Page Live Uptime Counter & Data Formatter ─────── */
  function formatBytesToMB(val) {
    var s = String(val || '').trim();
    if (s.indexOf('GB') !== -1 || s.indexOf('MB') !== -1) {
      return s;
    }
    // Check if valid numeric
    var cleanS = s.replace(/,/g, '').trim();
    var m = cleanS.match(/([0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z]+)?/);
    if (m && !isNaN(parseFloat(m[1])) && s.indexOf('$(') === -1) {
      var num = parseFloat(m[1]);
      if (num === 0) return '0 MB';
      var unit = (m[2] || '').toLowerCase();
      var mb = 0;
      if (unit.indexOf('g') !== -1) {
        mb = num * 1024;
      } else if (unit.indexOf('m') !== -1) {
        mb = num;
      } else if (unit.indexOf('k') !== -1) {
        mb = num / 1024;
      } else {
        mb = num / (1024 * 1024);
      }

      if (mb >= 1024) {
        return (mb / 1024).toFixed(2) + ' GB';
      } else if (mb >= 100) {
        return Math.round(mb) + ' MB';
      } else if (mb >= 1) {
        return (Math.round(mb * 10) / 10).toFixed(1) + ' MB';
      } else if (mb > 0) {
        return '< 1 MB';
      } else {
        return '0 MB';
      }
    }

    // Fallback if RouterOS returned unlimited, empty, or unparsed
    return 'ကန့်သတ်မထားပါ';
  }

  function initStatusPage() {
    var uptimeEl = gid('stat_uptime');
    if (uptimeEl && uptimeEl.textContent.indexOf('$(') !== -1) {
      uptimeEl.textContent = 'အသုံးပြုနေဆဲ';
    }

    var timeleftEl = gid('stat_timeleft');
    if (timeleftEl && (timeleftEl.textContent.indexOf('$(') !== -1 || timeleftEl.textContent.trim() === 'none' || timeleftEl.textContent.trim() === '0s')) {
      timeleftEl.textContent = 'ကန့်သတ်မထားပါ';
    }

    var remainingEl = gid('stat_data_remaining');
    if (remainingEl && remainingEl.textContent.indexOf('GB') === -1 && remainingEl.textContent.indexOf('MB') === -1) {
      remainingEl.textContent = formatBytesToMB(remainingEl.textContent);
    }
  }

  /* ── DOM Ready Init ───────────────────────────────────────── */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      initTabs();
      initVoucherInput();
      initPasswordToggle();
      initLoginForm();
      initStatusPage();
    });
  } else {
    initTabs();
    initVoucherInput();
    initPasswordToggle();
    initLoginForm();
    initStatusPage();
  }
})();

