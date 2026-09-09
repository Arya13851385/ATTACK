'use strict';

/* =========================================================================
 * Intranet Emergency Alert — Web (PWA) client
 *
 * Mirrors the native Android app's architecture as closely as the browser
 * platform allows:
 *   - WebSocket connection to the same on-premise broker, same wire format
 *   - Reconnect with capped exponential backoff + an app-level heartbeat
 *   - RED/YELLOW/WHITE state machine, Persian/RTL UI
 *   - Hold-to-confirm trigger buttons (accidental-trigger safeguard)
 *   - Full-screen takeover overlay for RED
 *   - Alarm audio + vibration (where the browser/OS allows)
 *
 * IMPORTANT LIMITATION (read this before relying on it for safety):
 * Browsers suspend JavaScript timers, WebSockets, and audio when a tab is
 * backgrounded or the screen locks. There is no browser equivalent of
 * Android's foreground service. This client does its best (Page Visibility
 * handling, aggressive reconnect-on-resume, Notification API where granted)
 * but it CANNOT guarantee delivery while the screen is off or the browser
 * is backgrounded. For guaranteed background reliability, use the native
 * Android app. Keep this page open and the screen on while monitoring.
 * ========================================================================= */

const AlertLevel = {
  WHITE: { wire: 'WHITE', label: 'وضعیت سفید', desc: 'پایان خطر / وضعیت عادی', cssClass: 'level-white' },
  YELLOW: { wire: 'YELLOW', label: 'وضعیت زرد', desc: 'خطر قریب‌الوقوع', cssClass: 'level-yellow' },
  RED: { wire: 'RED', label: 'وضعیت قرمز', desc: 'خطر فوری - اقدام آنی', cssClass: 'level-red' },
};

function levelFromWire(wire) {
  return Object.values(AlertLevel).find((l) => l.wire === wire) || AlertLevel.WHITE;
}

/* ---------------------------------------------------------------------
 * WebSocket client: reconnect with capped exponential backoff + heartbeat
 * ------------------------------------------------------------------- */
class AlertSocketClient {
  constructor({ onConnectionState, onMessage }) {
    this.onConnectionState = onConnectionState;
    this.onMessage = onMessage;
    this.ws = null;
    this.heartbeatTimer = null;
    this.reconnectTimer = null;
    this.lastPongAt = 0;
    this.reconnectAttempts = 0;
    this.manuallyStopped = true;
    this.host = null;
    this.port = null;
    this.deviceId = this._loadOrCreateDeviceId();
    this.deviceName = this._guessDeviceName();

    this.HEARTBEAT_INTERVAL_MS = 10_000;
    this.HEARTBEAT_TIMEOUT_MS = 25_000;
    this.BASE_RECONNECT_DELAY_MS = 1_000;
    this.MAX_RECONNECT_DELAY_MS = 30_000;
  }

  _loadOrCreateDeviceId() {
    let id = localStorage.getItem('emergency_device_id');
    if (!id) {
      id = 'web-' + Math.random().toString(36).slice(2) + Date.now().toString(36);
      localStorage.setItem('emergency_device_id', id);
    }
    return id;
  }

  _guessDeviceName() {
    const ua = navigator.userAgent;
    if (/android/i.test(ua)) return 'Android Web';
    if (/iphone|ipad|ipod/i.test(ua)) return 'iOS Web';
    return 'Web Client';
  }

  connect(host, port) {
    this.manuallyStopped = false;
    this.host = host;
    this.port = port;
    this.reconnectAttempts = 0;
    this._openSocket();
  }

  disconnect() {
    this.manuallyStopped = true;
    clearTimeout(this.reconnectTimer);
    clearInterval(this.heartbeatTimer);
    if (this.ws) {
      try { this.ws.close(1000, 'Client stopping'); } catch (e) { /* ignore */ }
    }
    this.ws = null;
    this.onConnectionState({ state: 'disconnected' });
  }

  sendAlert(level, note, room) {
    this._send({
      type: 'ALERT',
      level,
      note: note || null,
      room: room || null,
      senderId: this.deviceId,
      senderName: this.deviceName,
      timestamp: Date.now(),
      messageId: this._uuid(),
    });
  }

  _send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
      return true;
    }
    return false;
  }

  _uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }

  _wsUrl() {
    const isHttps = location.protocol === 'https:';
    const scheme = isHttps ? 'wss' : 'ws';
    return `${scheme}://${this.host}:${this.port}/alerts`;
  }

  _openSocket() {
    this.onConnectionState({ state: 'connecting', host: this.host, port: this.port });

    let socket;
    try {
      socket = new WebSocket(this._wsUrl());
    } catch (e) {
      this.onConnectionState({ state: 'failed', reason: e.message });
      this._scheduleReconnect();
      return;
    }
    this.ws = socket;

    socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.lastPongAt = Date.now();
      this.onConnectionState({ state: 'connected', host: this.host, port: this.port });
      this._send({
        type: 'HELLO',
        senderId: this.deviceId,
        senderName: this.deviceName,
        timestamp: Date.now(),
        messageId: this._uuid(),
      });
      this._startHeartbeat();
    };

    socket.onmessage = (event) => {
      this.lastPongAt = Date.now();
      let parsed;
      try {
        parsed = JSON.parse(event.data);
      } catch (e) {
        console.warn('Failed to parse incoming message', e);
        return;
      }
      if (parsed.type !== 'HEARTBEAT') {
        this.onMessage(parsed);
      }
    };

    socket.onclose = () => {
      clearInterval(this.heartbeatTimer);
      if (!this.manuallyStopped) this._scheduleReconnect();
    };

    socket.onerror = () => {
      this.onConnectionState({ state: 'failed', reason: 'اتصال ناموفق' });
    };
  }

  _startHeartbeat() {
    clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = setInterval(() => {
      const silence = Date.now() - this.lastPongAt;
      if (silence > this.HEARTBEAT_TIMEOUT_MS) {
        console.warn(`Heartbeat timeout after ${silence}ms — forcing reconnect`);
        try { this.ws && this.ws.close(); } catch (e) { /* ignore */ }
        return;
      }
      this._send({ type: 'HEARTBEAT', senderId: this.deviceId, timestamp: Date.now(), messageId: this._uuid() });
    }, this.HEARTBEAT_INTERVAL_MS);
  }

  _scheduleReconnect() {
    clearTimeout(this.reconnectTimer);
    this.reconnectAttempts += 1;
    const delay = Math.min(
      this.BASE_RECONNECT_DELAY_MS * Math.pow(2, this.reconnectAttempts - 1),
      this.MAX_RECONNECT_DELAY_MS
    );
    this.onConnectionState({ state: 'reconnecting', attempt: this.reconnectAttempts, delay });
    this.reconnectTimer = setTimeout(() => {
      if (!this.manuallyStopped) this._openSocket();
    }, delay);
  }
}

/* ---------------------------------------------------------------------
 * Audio + vibration
 * ------------------------------------------------------------------- */
class AlertMediaManager {
  constructor() {
    this.audioRed = document.getElementById('audioRed');
    this.audioYellow = document.getElementById('audioYellow');
    this.audioWhite = document.getElementById('audioWhite');
    this.yellowIntervalId = null;
    this.unlocked = false;
  }

  /** Must be called from within a user-gesture handler (mobile autoplay policy). */
  unlock() {
    [this.audioRed, this.audioYellow, this.audioWhite].forEach((el) => {
      el.muted = true;
      el.play().then(() => {
        el.pause();
        el.currentTime = 0;
        el.muted = false;
      }).catch(() => { el.muted = false; });
    });
    this.unlocked = true;
  }

  playForLevel(level) {
    this.stop();
    if (level.wire === 'RED') {
      this.audioRed.currentTime = 0;
      this.audioRed.loop = true;
      this.audioRed.play().catch((e) => console.warn('Audio play blocked', e));
    } else if (level.wire === 'YELLOW') {
      const playOnce = () => {
        this.audioYellow.currentTime = 0;
        this.audioYellow.play().catch((e) => console.warn('Audio play blocked', e));
      };
      playOnce();
      this.yellowIntervalId = setInterval(playOnce, 4000);
    } else {
      this.audioWhite.currentTime = 0;
      this.audioWhite.play().catch((e) => console.warn('Audio play blocked', e));
    }
  }

  stop() {
    clearInterval(this.yellowIntervalId);
    this.yellowIntervalId = null;
    [this.audioRed, this.audioYellow, this.audioWhite].forEach((el) => {
      el.pause();
      el.currentTime = 0;
    });
  }

  vibrateForLevel(level) {
    if (!('vibrate' in navigator)) return; // iOS Safari has no Vibration API
    if (level.wire === 'RED') {
      navigator.vibrate([800, 200, 800, 200, 800, 200, 800]);
    } else if (level.wire === 'YELLOW') {
      navigator.vibrate([300, 700, 300, 700, 300]);
    } else {
      navigator.vibrate(150);
    }
  }

  cancelVibration() {
    if ('vibrate' in navigator) navigator.vibrate(0);
  }
}

/* ---------------------------------------------------------------------
 * App wiring
 * ------------------------------------------------------------------- */
(function main() {
  const els = {
    unlockGate: document.getElementById('unlockGate'),
    unlockBtn: document.getElementById('unlockBtn'),
    app: document.getElementById('app'),
    statusDot: document.getElementById('statusDot'),
    statusText: document.getElementById('statusText'),
    alertBanner: document.getElementById('alertBanner'),
    bannerLabel: document.getElementById('bannerLabel'),
    bannerDesc: document.getElementById('bannerDesc'),
    bannerRoom: document.getElementById('bannerRoom'),
    bannerNote: document.getElementById('bannerNote'),
    bannerSender: document.getElementById('bannerSender'),
    triggerButtons: Array.from(document.querySelectorAll('.trigger-list [data-level]')),
    manualServerBtn: document.getElementById('manualServerBtn'),
    retryBtn: document.getElementById('retryBtn'),
    confirmModal: document.getElementById('confirmModal'),
    confirmTitle: document.getElementById('confirmTitle'),
    roomInput: document.getElementById('roomInput'),
    noteInput: document.getElementById('noteInput'),
    holdButton: document.getElementById('holdButton'),
    holdFill: document.getElementById('holdFill'),
    cancelConfirmBtn: document.getElementById('cancelConfirmBtn'),
    manualModal: document.getElementById('manualModal'),
    hostInput: document.getElementById('hostInput'),
    portInput: document.getElementById('portInput'),
    cancelManualBtn: document.getElementById('cancelManualBtn'),
    connectManualBtn: document.getElementById('connectManualBtn'),
    fullAlertOverlay: document.getElementById('fullAlertOverlay'),
    fullAlertLabel: document.getElementById('fullAlertLabel'),
    fullAlertDesc: document.getElementById('fullAlertDesc'),
    fullAlertRoom: document.getElementById('fullAlertRoom'),
    fullAlertNote: document.getElementById('fullAlertNote'),
    ackBtn: document.getElementById('ackBtn'),
  };

  const media = new AlertMediaManager();
  let currentLevel = AlertLevel.WHITE;
  let pendingLevel = null;
  let holdTimer = null;
  let holdProgress = 0;

  const socket = new AlertSocketClient({
    onConnectionState: renderConnectionState,
    onMessage: handleIncomingMessage,
  });

  function getSavedServer() {
    const saved = localStorage.getItem('emergency_manual_server');
    if (saved) {
      try { return JSON.parse(saved); } catch (e) { /* ignore */ }
    }
    // Default: same host that served this page, same port, since this app
    // is served directly by the on-premise broker — zero configuration.
    return { host: location.hostname, port: Number(location.port) || 8765 };
  }

  function saveServer(host, port) {
    localStorage.setItem('emergency_manual_server', JSON.stringify({ host, port }));
  }

  function renderConnectionState(state) {
    els.statusDot.className = 'status-dot';
    switch (state.state) {
      case 'connected':
        els.statusDot.classList.add('connected');
        els.statusText.textContent = `متصل به ${state.host}:${state.port}`;
        break;
      case 'connecting':
        els.statusDot.classList.add('connecting');
        els.statusText.textContent = 'در حال اتصال...';
        break;
      case 'reconnecting':
        els.statusDot.classList.add('reconnecting');
        els.statusText.textContent = `تلاش مجدد برای اتصال (${state.attempt})...`;
        break;
      case 'failed':
        els.statusDot.classList.add('failed');
        els.statusText.textContent = `اتصال ناموفق: ${state.reason || ''}`;
        break;
      default:
        els.statusDot.classList.add('disconnected');
        els.statusText.textContent = 'قطع — به سرور متصل نیستید';
    }
  }

  function handleIncomingMessage(message) {
    if (message.type !== 'ALERT' && message.type !== 'STATE_SYNC') return;
    const level = levelFromWire(message.level);
    currentLevel = level;
    renderAlertState(level, message);

    media.playForLevel(level);
    media.vibrateForLevel(level);

    if (level.wire === 'RED') {
      showFullScreenAlert(level, message);
      tryShowNotification(level, message);
    } else if (level.wire === 'YELLOW') {
      tryShowNotification(level, message);
    } else {
      media.stop();
      media.cancelVibration();
      hideFullScreenAlert();
    }
  }

  function renderAlertState(level, message) {
    els.alertBanner.className = `alert-banner ${level.cssClass}`;
    els.bannerLabel.textContent = level.label;
    els.bannerDesc.textContent = level.desc;

    setMeta(els.bannerRoom, message.room ? `موقعیت: ${message.room}` : '');
    setMeta(els.bannerNote, message.note || '');
    setMeta(
      els.bannerSender,
      message.senderName ? `توسط ${message.senderName} در ${new Date(message.timestamp || Date.now()).toLocaleTimeString('fa-IR')}` : ''
    );
  }

  function setMeta(el, text) {
    if (text) {
      el.textContent = text;
      el.classList.remove('hidden');
    } else {
      el.textContent = '';
      el.classList.add('hidden');
    }
  }

  function showFullScreenAlert(level, message) {
    els.fullAlertLabel.textContent = level.label;
    els.fullAlertDesc.textContent = level.desc;
    setMeta(els.fullAlertRoom, message.room ? `موقعیت: ${message.room}` : '');
    setMeta(els.fullAlertNote, message.note || '');
    els.fullAlertOverlay.classList.remove('hidden');
    if (document.documentElement.requestFullscreen) {
      document.documentElement.requestFullscreen().catch(() => { /* best effort only */ });
    }
    if ('wakeLock' in navigator) {
      navigator.wakeLock.request('screen').catch(() => { /* best effort only */ });
    }
  }

  function hideFullScreenAlert() {
    els.fullAlertOverlay.classList.add('hidden');
  }

  function tryShowNotification(level, message) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    if (document.visibilityState === 'visible') return; // avoid double-alerting while looking at the tab
    try {
      new Notification(level.label, {
        body: [message.room, message.note].filter(Boolean).join(' — ') || level.desc,
        icon: 'assets/icons/icon-192.png',
        tag: 'emergency-alert',
        requireInteraction: level.wire === 'RED',
      });
    } catch (e) {
      console.warn('Notification failed', e);
    }
  }

  function requestNotificationPermission() {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().catch(() => { /* ignore */ });
    }
  }

  // ---- Trigger + hold-to-confirm flow ----
  function openConfirmModal(level) {
    pendingLevel = level;
    els.confirmTitle.textContent = `تأیید اعلام ${level.label}`;
    els.roomInput.value = '';
    els.noteInput.value = '';
    resetHold();
    els.confirmModal.classList.remove('hidden');
  }

  function closeConfirmModal() {
    els.confirmModal.classList.add('hidden');
    pendingLevel = null;
    resetHold();
  }

  function resetHold() {
    clearInterval(holdTimer);
    holdTimer = null;
    holdProgress = 0;
    els.holdFill.style.width = '0%';
  }

  function startHold() {
    if (holdTimer) return;
    const durationMs = 1600;
    const stepMs = 40;
    const steps = durationMs / stepMs;
    let step = 0;
    holdTimer = setInterval(() => {
      step += 1;
      holdProgress = step / steps;
      els.holdFill.style.width = `${Math.min(100, holdProgress * 100)}%`;
      if (step >= steps) {
        clearInterval(holdTimer);
        holdTimer = null;
        confirmTrigger();
      }
    }, stepMs);
  }

  function cancelHold() {
    if (holdTimer) {
      clearInterval(holdTimer);
      holdTimer = null;
    }
    holdProgress = 0;
    els.holdFill.style.width = '0%';
  }

  function confirmTrigger() {
    if (!pendingLevel) return;
    const room = els.roomInput.value.trim().slice(0, 80);
    const note = els.noteInput.value.trim().slice(0, 200);
    socket.sendAlert(pendingLevel.wire, note || null, room || null);
    // Optimistic local reflect, mirroring the native app's behavior.
    currentLevel = pendingLevel;
    renderAlertState(pendingLevel, {
      room: room || null,
      note: note || null,
      senderName: 'شما',
      timestamp: Date.now(),
    });
    closeConfirmModal();
  }

  // ---- Manual server dialog ----
  function openManualModal() {
    const current = getSavedServer();
    els.hostInput.value = current.host || '';
    els.portInput.value = current.port || 8765;
    els.manualModal.classList.remove('hidden');
  }

  function closeManualModal() {
    els.manualModal.classList.add('hidden');
  }

  // ---- Event bindings ----
  els.unlockBtn.addEventListener('click', () => {
    media.unlock();
    requestNotificationPermission();
    els.unlockGate.classList.add('hidden');
    els.app.classList.remove('hidden');
    const server = getSavedServer();
    socket.connect(server.host, server.port);
  });

  els.triggerButtons.forEach((btn) => {
    btn.addEventListener('click', () => openConfirmModal(AlertLevel[btn.dataset.level]));
  });

  els.cancelConfirmBtn.addEventListener('click', closeConfirmModal);

  ['pointerdown'].forEach((evt) =>
    els.holdButton.addEventListener(evt, (e) => { e.preventDefault(); startHold(); })
  );
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((evt) =>
    els.holdButton.addEventListener(evt, cancelHold)
  );

  els.manualServerBtn.addEventListener('click', openManualModal);
  els.cancelManualBtn.addEventListener('click', closeManualModal);
  els.connectManualBtn.addEventListener('click', () => {
    const host = els.hostInput.value.trim();
    const port = parseInt(els.portInput.value, 10) || 8765;
    if (!host) return;
    saveServer(host, port);
    socket.disconnect();
    socket.connect(host, port);
    closeManualModal();
  });

  els.retryBtn.addEventListener('click', () => {
    const server = getSavedServer();
    socket.disconnect();
    socket.connect(server.host, server.port);
  });

  els.ackBtn.addEventListener('click', () => {
    hideFullScreenAlert();
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => { /* ignore */ });
    }
  });

  // Reconnect promptly whenever the tab regains focus/visibility — this is
  // the best available mitigation for the background-suspension limitation
  // described at the top of this file.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      const server = getSavedServer();
      if (!socket.ws || socket.ws.readyState !== WebSocket.OPEN) {
        socket.disconnect();
        socket.connect(server.host, server.port);
      }
    }
  });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('service-worker.js').catch((e) => {
      console.warn('Service worker registration failed', e);
    });
  }
})();
