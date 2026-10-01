/* ─────────────────────────────────────────────────────────────
   အရိပ်စစ် WIFI (A YEIK SITT 2) – Version 2.0 main.js
   Dynamic Multi-length Resilient Captive Portal Controller
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  function gid(id) { return document.getElementById(id); }

  /* ── LocalStorage Caching ────────────────────────────────── */
  var STORAGE_KEY = 'ayeiksitt2_voucher';
  var STORAGE_TIME_KEY = 'ayeiksitt2_voucher_time';

  function saveVoucher(code) {
    if (!code) return;
    try {
      localStorage.setItem(STORAGE_KEY, code.trim());
      localStorage.setItem(STORAGE_TIME_KEY, Date.now().toString());
    } catch (e) {}
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem(STORAGE_KEY);
      var time = localStorage.getItem(STORAGE_TIME_KEY);
      if (!code) return '';
      // 30 days expiration
      if (time && (Date.now() - parseInt(time, 10)) > 30 * 24 * 3600 * 1000) {
        localStorage.removeItem(STORAGE_KEY);
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

  /* ── Form Submission ─────────────────────────────────────── */
  function initForm() {
    var form = gid('login_form');
    var uField = gid('username_field');
    var pField = gid('password_field');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');
    var pwInput = gid('account_password');
    var loginBtn = gid('login_btn');

    if (!form || !uField || !pField) return;

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

        // RouterOS PAP Authentication: username == code, password == code
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
      }

      if (loginBtn) {
        loginBtn.disabled = true;
        loginBtn.innerHTML = '<span>ချိတ်ဆက်နေပါသည်...</span>';
        loginBtn.style.opacity = '0.7';
      }
    });
  }

  /* ── Auto-Fill from Storage or Query Params ───────────────── */
  function autoPopulate() {
    var vInput = gid('voucher_input');
    if (!vInput) return;

    var params = parseQueryParams();
    var paramCode = params.voucher || params.code || params.username || '';
    if (paramCode) {
      vInput.value = paramCode.toUpperCase();
      var form = gid('login_form');
      if (form) {
        setTimeout(function () { form.submit(); }, 300);
      }
      return;
    }

    var saved = getStoredVoucher();
    if (saved && !vInput.value) {
      vInput.value = saved;
    }
  }

  /* ── Initialize ──────────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', function () {
    initVoucherInput();
    initPasswordToggle();
    initTabs();
    initForm();
    autoPopulate();
  });

})();
