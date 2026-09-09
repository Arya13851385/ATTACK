'use strict';

/*
 * Caches the static app shell so the PWA still loads (UI + audio files)
 * if the phone briefly loses Wi-Fi to the broker. This does NOT and
 * cannot keep the WebSocket alive in the background — service workers
 * are also suspended/terminated by the OS, and reliably waking one up
 * to reconnect a socket requires a Push subscription backed by a public
 * push service, which would reintroduce a cloud dependency this project
 * deliberately avoids. Background reliability is a native-app-only
 * guarantee here (see AlertConnectionService in the Android app).
 */

const CACHE_NAME = 'emergency-alert-shell-v1';
const SHELL_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './css/styles.css',
  './js/app.js',
  './assets/audio/alert_red_siren.wav',
  './assets/audio/alert_yellow_chime.wav',
  './assets/audio/alert_white_calm.wav',
  './assets/icons/icon-72.png',
  './assets/icons/icon-96.png',
  './assets/icons/icon-128.png',
  './assets/icons/icon-144.png',
  './assets/icons/icon-152.png',
  './assets/icons/icon-180.png',
  './assets/icons/icon-192.png',
  './assets/icons/icon-384.png',
  './assets/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(SHELL_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Never intercept the WebSocket handshake or cross-origin requests.
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response.ok) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      }).catch(() => cached);
    })
  );
});
