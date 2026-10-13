/* ─────────────────────────────────────────────────────────────
   Kyaw Gyi WiFi – Production main.js
   Supports 0-9, capital & small letters freely.
   Bulletproof auto-login loop protection, instant quota/expiration wipe,
   and seamless error recovery.
   ─────────────────────────────────────────────────────────── */

(function () {
  'use strict';

  var STORAGE_KEY = 'kyawgyi_wifi_voucher';
  var STORAGE_TIME_KEY = 'kyawgyi_wifi_voucher_time';
  var STORAGE_AUTH_TYPE_KEY = 'kyawgyi_wifi_auth_type';
  var STORAGE_ACC_USER_KEY = 'kyawgyi_wifi_account_user';
  var STORAGE_ACC_PASS_KEY = 'kyawgyi_wifi_account_pass';

  function gid(id) { return document.getElementById(id); }

  /* ── LocalStorage Management ─────────────────────────────── */
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
      localStorage.removeItem('yadanartun_wifi_voucher');
      localStorage.removeItem('yadanartun_wifi_voucher_time');
      localStorage.removeItem('yadanartun_wifi_auth_type');
      localStorage.removeItem('yt_wifi_voucher');
      localStorage.removeItem('yt_wifi_voucher_time');
      sessionStorage.removeItem('kyawgyi_auto_tried');
    } catch (e) {}
  }

  function getStoredVoucher() {
    try {
      var code = localStorage.getItem(STORAGE_KEY);
      var time = localStorage.getItem(STORAGE_TIME_KEY);
      if (!code) {
        code = localStorage.getItem('yadanartun_wifi_voucher');
        time = localStorage.getItem('yadanartun_wifi_voucher_time');
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

  function getRouterError() {
    var msgBox = gid('login_msg');
    if (msgBox) {
      var raw = msgBox.textContent || msgBox.innerText || '';
      var cleaned = raw.replace(/^[⚠️\s\u26A0]+/, '').trim();
      if (cleaned.length > 0 && cleaned.indexOf('$(') === -1) {
        return cleaned;
      }
    }
    var q = parseQueryParams();
    if (q.error) return q.error;
    return null;
  }

  /* ── UI Tab Setup ────────────────────────────────────────── */
  function setupTabs() {
    var btnVoucher = gid('tab_voucher');
    var btnAccount = gid('tab_account');
    var pnlVoucher = gid('panel_voucher');
    var pnlAccount = gid('panel_account');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');

    if (!btnVoucher || !btnAccount) return;

    function selectTab(type) {
      if (type === 'voucher') {
        btnVoucher.classList.add('active');
        btnAccount.classList.remove('active');
        if (pnlVoucher) pnlVoucher.classList.remove('hide');
        if (pnlAccount) pnlAccount.classList.add('hide');
        if (vInput) vInput.focus();
      } else {
        btnAccount.classList.add('active');
        btnVoucher.classList.remove('active');
        if (pnlAccount) pnlAccount.classList.remove('hide');
        if (pnlVoucher) pnlVoucher.classList.add('hide');
        if (aInput) aInput.focus();
      }
    }

    btnVoucher.addEventListener('click', function () { selectTab('voucher'); });
    btnAccount.addEventListener('click', function () { selectTab('account'); });

    var authType = getStoredAuthType();
    if (authType === 'account') {
      selectTab('account');
    }
  }

  /* ── Input Actions (Clear, Paste, Toggle Password) ───────── */
  function setupInputs() {
    var vInput = gid('voucher_input');
    var clearBtn = gid('clear_voucher_btn');
    var pasteBtn = gid('paste_voucher_btn');
    var togglePassBtn = gid('toggle_password_btn');
    var passField = gid('account_password');

    if (vInput) {
      function syncClearBtn() {
        if (!clearBtn) return;
        clearBtn.style.display = vInput.value.length > 0 ? 'inline-flex' : 'none';
      }

      vInput.addEventListener('input', function () {
        syncClearBtn();
      });

      if (clearBtn) {
        clearBtn.addEventListener('click', function () {
          vInput.value = '';
          clearStoredCredentials();
          syncClearBtn();
          vInput.focus();
        });
      }

      if (pasteBtn) {
        pasteBtn.addEventListener('click', function () {
          if (navigator.clipboard && navigator.clipboard.readText) {
            navigator.clipboard.readText().then(function (clip) {
              if (clip) {
                vInput.value = clip.trim();
                syncClearBtn();
                vInput.focus();
              }
            }).catch(function () {
              vInput.focus();
            });
          } else {
            vInput.focus();
          }
        });
      }

      syncClearBtn();
    }

    if (togglePassBtn && passField) {
      togglePassBtn.addEventListener('click', function () {
        if (passField.type === 'password') {
          passField.type = 'text';
          togglePassBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';
        } else {
          passField.type = 'password';
          togglePassBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
        }
      });
    }
  }

  /* ── Main Login & Form Logic ─────────────────────────────── */
  function setupLoginForm() {
    var form = gid('login_form');
    var uField = gid('username_field');
    var pField = gid('password_field');
    var vInput = gid('voucher_input');
    var aInput = gid('account_input');
    var pInput = gid('account_password');
    var msgBox = gid('login_msg');
    var submitBtn = gid('login_btn');
    var cancelAutoBar = gid('cancel_auto_bar');
    var cancelAutoBtn = gid('cancel_auto_btn');

    if (!form || !uField || !pField) return;

    function showError(text) {
      if (!msgBox) return;
      msgBox.innerHTML = '<span>&#9888;</span> <span>' + text + '</span>';
      msgBox.classList.remove('hide');
    }

    var autoTimer = null;

    function cancelAutoLogin() {
      if (autoTimer) {
        clearTimeout(autoTimer);
        autoTimer = null;
      }
      clearStoredCredentials();
      if (cancelAutoBar) cancelAutoBar.style.display = 'none';
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<span class="btn-shimmer"></span><span class="btn-text">ချိတ်ဆက်မည်</span>';
      }
      if (vInput) {
        vInput.value = '';
        vInput.focus();
      }
    }

    if (cancelAutoBtn) {
      cancelAutoBtn.addEventListener('click', cancelAutoLogin);
    }

    // Cancel auto-login immediately if user touches voucher input or presses a key
    if (vInput) {
      vInput.addEventListener('keydown', function () {
        if (autoTimer) {
          clearTimeout(autoTimer);
          autoTimer = null;
          if (cancelAutoBar) cancelAutoBar.style.display = 'none';
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = '<span class="btn-shimmer"></span><span class="btn-text">ချိတ်ဆက်မည်</span>';
          }
        }
      });
    }

    var routerErr = getRouterError();

    // 1. If router returned an error (quota reached, expired, invalid code)
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

    // 2. Intelligent Auto-Reconnection (Protected against infinite loops)
    var q = parseQueryParams();
    var authType = getStoredAuthType();
    var storedVoucher = getStoredVoucher();
    var storedAccount = getStoredAccount();
    var autoTried = sessionStorage.getItem('kyawgyi_auto_tried') === '1';

    // If query contains voucher/code param, always pre-fill
    var explicitCode = q.voucher || q.code;
    if (explicitCode && vInput) {
      vInput.value = explicitCode.trim();
    } else if (storedVoucher && vInput && !vInput.value) {
      vInput.value = storedVoucher;
    }

    // Auto-connect ONLY if NOT already tried in this session and NOT explicitly manual
    if (!q.manual && !autoTried) {
      if (authType === 'account' && storedAccount && storedAccount.user) {
        sessionStorage.setItem('kyawgyi_auto_tried', '1');
        uField.value = storedAccount.user;
        pField.value = storedAccount.pass;

        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> အကောင့်ဖြင့် ပြန်လည်ချိတ်ဆက်နေပါသည်...';
        }
        if (cancelAutoBar) cancelAutoBar.style.display = 'block';

        autoTimer = setTimeout(function () {
          form.submit();
        }, 1200);
        return;
      }

      var codeToTry = explicitCode || storedVoucher;
      if (codeToTry && codeToTry.length >= 3) {
        sessionStorage.setItem('kyawgyi_auto_tried', '1');
        uField.value = codeToTry.trim();
        pField.value = codeToTry.trim();

        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> ပြန်လည်ချိတ်ဆက်နေပါသည်...';
        }
        if (cancelAutoBar) cancelAutoBar.style.display = 'block';

        autoTimer = setTimeout(function () {
          form.submit();
        }, 1200);
        return;
      }
    }

    // 3. Normal Manual Submission
    form.addEventListener('submit', function (e) {
      if (autoTimer) {
        clearTimeout(autoTimer);
        autoTimer = null;
      }

      var isVoucher = gid('tab_voucher') && gid('tab_voucher').classList.contains('active');

      if (isVoucher) {
        var code = (vInput ? vInput.value : '').trim();
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
        sessionStorage.setItem('kyawgyi_auto_tried', '1');
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
        sessionStorage.setItem('kyawgyi_auto_tried', '1');
        uField.value = user;
        pField.value = pass;
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="live-pulse" style="margin-right:8px;"></span> ချိတ်ဆက်နေပါသည်...';
      }
    });
  }

  // DOM Ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      setupTabs();
      setupInputs();
      setupLoginForm();
    });
  } else {
    setupTabs();
    setupInputs();
    setupLoginForm();
  }
})();
