/* ─────────────────────────────────────────────────────────────
   YADANAR TUN WIFI – main.js
   Handles voucher input, quick paste, tab switching, and PAP login.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  function gid(id) { return document.getElementById(id); }

  /* ── Local Storage Management ────────────────────────────── */
  function saveVoucher(code) {
    if (!code) return;
    try {
      localStorage.setItem('yadanartun_voucher', code.trim());
      localStorage.setItem('yadanartun_voucher_time', Date.now().toString());
    } catch (e) {}
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem('yadanartun_voucher');
      var time = localStorage.getItem('yadanartun_voucher_time');
      if (!code) return '';
      // Expire cache after 30 days
      if (time && (Date.now() - parseInt(time, 10)) > 30 * 24 * 3600 * 1000) {
        localStorage.removeItem('yadanartun_voucher');
        return '';
      }
      return code.trim();
    } catch (e) {
      return '';
    }
  }

  /* ── URL Parameter Parsing (QR Code & Quick Links) ───────── */
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

  /* ── Voucher Input Controls (Typing, Paste, Clear) ───────── */
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

    formatVoucherValue();
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
    var panelVoucher = gid('field_voucher_panel');
    var panelAccount = gid('field_account_panel');
    var errorEl = gid('login_msg');

    function switchTab(mode) {
      if (mode === 'voucher') {
        tabVoucher && tabVoucher.classList.add('active');
        tabAccount && tabAccount.classList.remove('active');
        panelVoucher && panelVoucher.classList.remove('hide');
        panelAccount && panelAccount.classList.add('hide');
        var vIn = gid('voucher_input');
        if (vIn) setTimeout(function () { vIn.focus(); }, 60);
      } else {
        tabAccount && tabAccount.classList.add('active');
        tabVoucher && tabVoucher.classList.remove('active');
        panelAccount && panelAccount.classList.remove('hide');
        panelVoucher && panelVoucher.classList.add('hide');
        var uIn = gid('account_input');
        if (uIn) setTimeout(function () { uIn.focus(); }, 60);
      }
      if (errorEl && errorEl.textContent.indexOf('$(') !== -1) {
        errorEl.textContent = '';
        errorEl.classList.remove('active');
      }
    }

    if (tabVoucher) tabVoucher.addEventListener('click', function () { switchTab('voucher'); });
    if (tabAccount) tabAccount.addEventListener('click', function () { switchTab('account'); });
  }

  /* ── Form Validation & Submission ────────────────────────── */
  function initForm() {
    var form = gid('login_form');
    var btn = gid('login_btn');
    var errorEl = gid('login_msg');
    if (!form || !btn) return;

    function showError(msg) {
      if (errorEl) {
        errorEl.innerHTML = '<span class="error-icon">&#9888;</span> ' + msg;
        errorEl.classList.add('active');
      }
    }

    form.addEventListener('submit', function (e) {
      var isVoucherTab = gid('tab_voucher') && gid('tab_voucher').classList.contains('active');
      var usernameField = gid('username_field');
      var passwordField = gid('password_field');

      if (isVoucherTab) {
        var vInput = gid('voucher_input');
        var code = (vInput ? vInput.value : '').trim().replace(/[\s]/g, '').toUpperCase();
        if (!code || code.length < 3) {
          e.preventDefault();
          showError('Please enter your voucher code / ဘောင်ချာကုဒ် ထည့်သွင်းပါ');
          if (vInput) vInput.focus();
          return;
        }

        saveVoucher(code);
        if (usernameField) usernameField.value = code;
        if (passwordField) passwordField.value = code; // In PAP mode for voucher, password equals username
      } else {
        var uInput = gid('account_input');
        var pInput = gid('account_password');
        var user = (uInput ? uInput.value : '').trim();
        var pass = (pInput ? pInput.value : '').trim();

        if (!user) {
          e.preventDefault();
          showError('Please enter your username / အသုံးပြုသူအမည် ထည့်သွင်းပါ');
          if (uInput) uInput.focus();
          return;
        }
        if (!pass) {
          e.preventDefault();
          showError('Please enter your password / လျှို့ဝှက်နံပါတ် ထည့်သွင်းပါ');
          if (pInput) pInput.focus();
          return;
        }

        if (usernameField) usernameField.value = user;
        if (passwordField) passwordField.value = pass;
      }

      // Show connecting state
      btn.disabled = true;
      btn.innerHTML = '<span class="loading-ring" style="width:20px;height:20px;border-width:2.5px;display:inline-block;vertical-align:middle;margin-right:8px;"></span> CONNECTING… / ချိတ်ဆက်နေသည်…';
    });
  }

  /* ── Restore Stored Voucher or URL Parameters ────────────── */
  function handleUrlAndCache() {
    var params = parseQueryParams();
    var voucherCode = params.voucher || params.code || '';
    var user = params.username || params.user || '';
    var pass = params.password || params.pass || '';

    if (!voucherCode && user && (!pass || pass === user)) {
      voucherCode = user;
    }

    if (!voucherCode) {
      voucherCode = getStoredVoucher();
    }

    var vInput = gid('voucher_input');
    if (voucherCode && vInput) {
      vInput.value = voucherCode.toUpperCase();
      var clearBtn = gid('clear_voucher_btn');
      var pasteBtn = gid('paste_voucher_btn');
      if (clearBtn) clearBtn.style.display = 'flex';
      if (pasteBtn) pasteBtn.style.display = 'none';

      if (params.autologin === 'true' || params.autologin === '1' || params.auto === '1') {
        var btn = gid('login_btn');
        if (btn) setTimeout(function () { btn.click(); }, 350);
      }
    } else if (user && pass) {
      var tabAccount = gid('tab_account');
      if (tabAccount) tabAccount.click();
      var uInput = gid('account_input');
      var pInput = gid('account_password');
      if (uInput) uInput.value = user;
      if (pInput) pInput.value = pass;

      if (params.autologin === 'true' || params.autologin === '1' || params.auto === '1') {
        var btn = gid('login_btn');
        if (btn) setTimeout(function () { btn.click(); }, 350);
      }
    }
  }

  /* ── Status Page Data Formatting ─────────────────────────── */
  function initStatusPage() {
    var bytesInEl = gid('stat_bytes_in');
    var bytesOutEl = gid('stat_bytes_out');
    var dataUsedEl = gid('stat_data_used');
    var dataRemainingEl = gid('stat_data_remaining');

    if (!dataUsedEl || !bytesInEl || !bytesOutEl) return;

    function formatBytes(bytes) {
      var b = parseFloat(bytes) || 0;
      if (b < 1024) return b + ' B';
      if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
      if (b < 1073741824) return (b / 1048576).toFixed(1) + ' MB';
      return (b / 1073741824).toFixed(2) + ' GB';
    }

    var totalBytes = (parseFloat(bytesInEl.textContent) || 0) + (parseFloat(bytesOutEl.textContent) || 0);
    dataUsedEl.textContent = formatBytes(totalBytes);

    if (dataRemainingEl) {
      // Calculate remaining data if quota exists in user profile / username
      var uName = (gid('display_username') ? gid('display_username').textContent : '').toUpperCase();
      var quotaMatch = uName.match(/(\d+)\s*(GB|MB|G|M)/i);
      if (quotaMatch) {
        var num = parseFloat(quotaMatch[1]);
        var unit = quotaMatch[2].toUpperCase();
        var quotaBytes = num * (unit.indexOf('G') !== -1 ? 1073741824 : 1048576);
        var rem = Math.max(0, quotaBytes - totalBytes);
        dataRemainingEl.textContent = formatBytes(rem);
      } else {
        dataRemainingEl.textContent = 'Unlimited';
      }
    }
  }

  /* ── Hide Preloader When Ready ───────────────────────────── */
  function hideLoader() {
    var loader = gid('body_loading');
    if (loader) {
      loader.classList.add('hide');
    }
  }

  /* ── Initialization ──────────────────────────────────────── */
  window.addEventListener('DOMContentLoaded', function () {
    initVoucherInput();
    initPasswordToggle();
    initTabs();
    initForm();
    handleUrlAndCache();
    initStatusPage();
    setTimeout(hideLoader, 150);
  });

  window.addEventListener('load', hideLoader);

})();
