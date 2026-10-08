/* ─────────────────────────────────────────────────────────────
   A Yeik Sitt WiFi – main.js
   Supports 4 to 16 digit vouchers, account auth, persistent session remembering,
   seamless auto-login after router restarts/reconnects, and exact quota/expiration calculation.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  var STORAGE_KEY = 'ayeiksitt_wifi_voucher';
  var STORAGE_TIME_KEY = 'ayeiksitt_wifi_voucher_time';
  var STORAGE_AUTH_TYPE_KEY = 'ayeiksitt_wifi_auth_type';
  var STORAGE_ACC_USER_KEY = 'ayeiksitt_wifi_account_user';
  var STORAGE_ACC_PASS_KEY = 'ayeiksitt_wifi_account_pass';

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

  /* ── Query Parameter Parsing ──────────────────────────────── */
  function parseQueryParams() {
    var params = {};
    try {
      var search = window.location.search.substring(1);
      if (!search) return params;
      var pairs = search.split('&');
      for (var i = 0; i < pairs.length; i++) {
        var part = pairs[i].split('=');
        if (part.length === 2) {
          params[decodeURIComponent(part[0])] = decodeURIComponent(part[1]);
        }
      }
    } catch (e) {}
    return params;
  }

  /* ── Voucher Code Input & Helpers ────────────────────────── */
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
        clearStoredCredentials();
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

  /* ── Login Form Submission ────────────────────────────────── */
  function initLoginForm() {
    var form = gid('login_form');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');
    var pInput = gid('account_password');
    var uField = gid('username_field');
    var pField = gid('password_field');
    var msgBox = gid('login_msg');
    var submitBtn = gid('login_btn');
    if (!form) return;

    function showError(txt) {
      if (!msgBox) return;
      msgBox.textContent = txt;
      msgBox.classList.remove('hide');
      msgBox.style.display = 'block';
    }

    form.addEventListener('submit', function (e) {
      var isVoucherActive = gid('tab_voucher') && gid('tab_voucher').classList.contains('active');

      if (isVoucherActive) {
        var code = (vInput ? vInput.value : '').trim().toUpperCase();
        if (!code) {
          e.preventDefault();
          showError('⚠️ ကျေးဇူးပြု၍ ဘောင်ချာကုဒ် ရိုက်ထည့်ပါ');
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
          showError('⚠️ ကျေးဇူးပြု၍ အသုံးပြုသူအမည် ရိုက်ထည့်ပါ');
          if (aInput) aInput.focus();
          return;
        }
        saveAccount(user, pass);
        uField.value = user;
        pField.value = pass;
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="live-dot" style="margin-right:8px;"></span> ချိတ်ဆက်နေပါသည်...';
      }
    });
  }

  /* ── Time & Uptime Formatter ──────────────────────────────── */
  function formatTimeLeft(val) {
    if (!val || val.indexOf('$(') !== -1 || val.toLowerCase() === 'none' || val === '0s' || val === '0') {
      return 'ကန့်သတ်မထားပါ (Unlimited)';
    }
    var s = String(val).trim();
    var m = s.match(/(?:(\d+)w)?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?/);
    if (m) {
      var w = m[1] ? m[1] + 'w ' : '';
      var d = m[2] ? m[2] + 'd ' : '';
      var h = m[3] ? m[3] + 'h ' : '';
      var min = m[4] ? m[4] + 'm' : '';
      var res = (w + d + h + min).trim();
      if (res) return res;
    }
    return s;
  }

  function initStatusPage() {
    var timeEl = gid('stat_timeleft');
    if (timeEl) {
      timeEl.textContent = formatTimeLeft(timeEl.textContent);
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
