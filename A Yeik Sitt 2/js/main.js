/* ─────────────────────────────────────────────────────────────
   အရိပ်စစ် WIFI (A YEIK SITT 2) – Version 2.0 main.js
   Dynamic Multi-length Resilient Captive Portal Controller
   Supports 4 to 16 char vouchers, account auth, persistent session remembering,
   seamless auto-login after router restarts/reconnects, and quota/expiration detection.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  function gid(id) { return document.getElementById(id); }

  /* ── LocalStorage Caching ────────────────────────────────── */
  var STORAGE_KEY = 'ayeiksitt2_voucher';
  var STORAGE_TIME_KEY = 'ayeiksitt2_voucher_time';
  var STORAGE_AUTH_TYPE_KEY = 'ayeiksitt2_auth_type';
  var STORAGE_ACC_USER_KEY = 'ayeiksitt2_account_user';
  var STORAGE_ACC_PASS_KEY = 'ayeiksitt2_account_pass';

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
      // 30 days retention
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

  /* ── Voucher Input (4 to 16 Characters) ──────────────────── */
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
        ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>'
        : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    });
  }

  /* ── Tab Switcher ────────────────────────────────────────── */
  var currentTab = 'voucher';

  function initTabs() {
    var tabVoucher = gid('tab_voucher');
    var tabAccount = gid('tab_account');
    var panelVoucher = gid('panel_voucher');
    var panelAccount = gid('panel_account');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');

    if (!tabVoucher || !tabAccount) return;

    function selectTab(tab) {
      currentTab = tab;
      if (tab === 'voucher') {
        tabVoucher.classList.add('active');
        tabVoucher.setAttribute('aria-selected', 'true');
        tabAccount.classList.remove('active');
        tabAccount.setAttribute('aria-selected', 'false');
        if (panelVoucher) panelVoucher.classList.remove('hide');
        if (panelAccount) panelAccount.classList.add('hide');
        if (vInput) vInput.focus();
      } else {
        tabAccount.classList.add('active');
        tabAccount.setAttribute('aria-selected', 'true');
        tabVoucher.classList.remove('active');
        tabVoucher.setAttribute('aria-selected', 'false');
        if (panelAccount) panelAccount.classList.remove('hide');
        if (panelVoucher) panelVoucher.classList.add('hide');
        if (aInput) aInput.focus();
      }
    }

    tabVoucher.addEventListener('click', function () { selectTab('voucher'); });
    tabAccount.addEventListener('click', function () { selectTab('account'); });
  }

  /* ── Form Submission & Intelligent Auto-Reconnect ────────── */
  function initForm() {
    var form = gid('login_form');
    var uField = gid('username_field');
    var pField = gid('password_field');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');
    var pwInput = gid('account_password');
    var loginBtn = gid('login_btn');
    var msgBox = gid('login_msg');

    if (!form || !uField || !pField) return;

    function showError(text) {
      if (!msgBox) return;
      msgBox.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" style="flex-shrink:0;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg><span>' + text + '</span>';
      msgBox.style.display = 'flex';
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

    // 2. Intelligent Auto-Login after Router Restart or Network Reconnect
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

        if (loginBtn) {
          loginBtn.disabled = true;
          loginBtn.innerHTML = '<span>အကောင့်ဖြင့် အလိုအလျောက် ချိတ်ဆက်နေပါသည်...</span>';
          loginBtn.style.opacity = '0.7';
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

          if (loginBtn) {
            loginBtn.disabled = true;
            loginBtn.innerHTML = '<span>ဘောင်ချာဖြင့် အလိုအလျောက် ချိတ်ဆက်နေပါသည်...</span>';
            loginBtn.style.opacity = '0.7';
          }

          setTimeout(function () { form.submit(); }, 150);
          return;
        }
      }
    }

    // Prefill account if saved
    if (storedAccount && aInput) {
      aInput.value = storedAccount.user;
      if (pwInput) pwInput.value = storedAccount.pass;
    }

    // 3. Normal Manual Submission
    form.addEventListener('submit', function (e) {
      if (currentTab === 'voucher') {
        var code = (vInput ? vInput.value : '').replace(/[\s]/g, '').toUpperCase();
        if (!code || code.length < 3) {
          e.preventDefault();
          if (vInput) {
            vInput.focus();
            vInput.style.borderColor = '#F43F5E';
            setTimeout(function () { vInput.style.borderColor = ''; }, 1500);
          }
          return false;
        }

        uField.value = code;
        pField.value = code;
        saveVoucher(code);
      } else {
        var uname = (aInput ? aInput.value : '').trim();
        var pass = (pwInput ? pwInput.value : '');
        if (!uname) {
          e.preventDefault();
          if (aInput) aInput.focus();
          return false;
        }
        uField.value = uname;
        pField.value = pass;
        saveAccount(uname, pass);
      }

      if (loginBtn) {
        loginBtn.disabled = true;
        loginBtn.innerHTML = '<span>ချိတ်ဆက်နေပါသည်...</span>';
        loginBtn.style.opacity = '0.7';
      }
    });
  }

  /* ── Initialize ──────────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', function () {
    initVoucherInput();
    initPasswordToggle();
    initTabs();
    initForm();
  });

})();
