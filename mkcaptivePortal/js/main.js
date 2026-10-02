/* ─────────────────────────────────────────────────────────────
   Yadanar Tun Wifi – main.js
   Supports 4 to 12+ digit vouchers, account auth, and fast UI
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  function gid(id) { return document.getElementById(id); }

  /* ── LocalStorage Caching ────────────────────────────────── */
  function saveVoucher(code) {
    if (!code) return;
    try {
      localStorage.setItem('yt_wifi_voucher', code.trim());
      localStorage.setItem('yt_wifi_voucher_time', Date.now().toString());
    } catch (e) {}
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem('yt_wifi_voucher');
      var time = localStorage.getItem('yt_wifi_voucher_time');
      if (!code) return '';
      // Expire after 30 days
      if (time && (Date.now() - parseInt(time, 10)) > 30 * 24 * 3600 * 1000) {
        localStorage.removeItem('yt_wifi_voucher');
        return '';
      }
      return code.trim();
    } catch (e) {
      return '';
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

  /* ── Voucher Input Logic (Supports 4 to 12 Digits) ───────── */
  function initVoucherInput() {
    var vInput = gid('voucher_input');
    var clearBtn = gid('clear_voucher_btn');
    var pasteBtn = gid('paste_voucher_btn');
    if (!vInput) return;

    function formatVoucherValue() {
      // Remove spaces, uppercase, supports 4 to 12+ characters
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
    if (!form || !uField || !pField) return;

    function showError(text) {
      if (!msgBox) return;
      msgBox.innerHTML = '<span>&#9888;</span> <span>' + text + '</span>';
      msgBox.classList.remove('hide');
    }

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
        uField.value = user;
        pField.value = pass;
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> ချိတ်ဆက်နေပါသည်...';
      }
    });

    // Check if RouterOS returned error inside DOM
    if (msgBox && msgBox.innerHTML.trim().length > 0 && msgBox.innerHTML.indexOf('$(') === -1) {
      msgBox.classList.remove('hide');
    }
  }

  /* ── Status Page Live Uptime Counter & Data Formatter ─────── */
  function formatBytesToMB(val) {
    if (!val) return 'Unlimited';
    var s = String(val).trim();
    if (!s || s.indexOf('$(') !== -1 || s.toLowerCase() === 'unlimited' || s.toLowerCase() === 'none') {
      return 'Unlimited';
    }
    // Strip commas and extra spaces (e.g. "2,147,483,648" -> "2147483648")
    s = s.replace(/,/g, '').trim();
    var m = s.match(/([0-9]+(?:\.[0-9]+)?)\s*([a-zA-Z]+)?/);
    if (!m) return s;
    var num = parseFloat(m[1]);
    if (isNaN(num)) return s;
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
      // Raw bytes (no unit or b/byte/bytes)
      mb = num / (1024 * 1024);
    }

    if (mb >= 100) {
      return Math.round(mb) + ' MB';
    } else if (mb >= 1) {
      return (Math.round(mb * 10) / 10).toFixed(1) + ' MB';
    } else if (mb > 0) {
      return '< 1 MB';
    } else {
      return '0 MB';
    }
  }

  function initStatusPage() {
    var uptimeEl = gid('stat_uptime');
    if (uptimeEl && uptimeEl.textContent.indexOf('$(') !== -1) {
      uptimeEl.textContent = 'Active';
    }

    var timeleftEl = gid('stat_timeleft');
    if (timeleftEl && timeleftEl.textContent.indexOf('$(') !== -1) {
      timeleftEl.textContent = 'Unlimited';
    }

    var remainingEl = gid('stat_data_remaining');
    if (remainingEl) {
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
