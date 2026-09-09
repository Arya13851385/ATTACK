# Intranet Emergency Alert (Android)

A native Android app for LAN-only (intranet) emergency alerting — RED / YELLOW / WHITE
threat states, broadcast in under 500ms to every device on the subnet, with **zero**
dependency on the public internet, Google Play Services, or FCM.

## Why this architecture

| Requirement | Implementation |
|---|---|
| No cloud dependency | Plain `ws://` WebSocket to an on-premise broker on the same LAN |
| Zero-config discovery | Android NSD (mDNS/DNS-SD), service type `_emergencyalert._tcp` |
| Fallback when multicast is blocked | Manual host:port entry, persisted with DataStore |
| Reliable while backgrounded/killed | Foreground `Service` (`FOREGROUND_SERVICE_TYPE_SPECIAL_USE`), `START_STICKY`, boot receiver |
| Alarm sound even in silent/DND | `MediaPlayer` + `AudioAttributes.USAGE_ALARM` on `STREAM_ALARM` |
| Screen-on for RED | Brief `PARTIAL_WAKE_LOCK` + `setShowWhenLocked`/`setTurnScreenOn` on the full-screen activity |
| Reconnect resilience | Capped exponential backoff + app-level heartbeat (independent of TCP keep-alive) |
| Accidental-trigger safety | Trigger buttons only open a dialog; broadcasting requires a ~1.6s press-and-hold |
| Persian / RTL UI | `LocalLayoutDirection` forced to RTL app-wide; all UI strings in Persian |

## Project layout

```
app/
  src/main/kotlin/com/localnet/emergency/
    model/         AlertLevel, AlertMessage (wire format), ConnectionState
    network/       NsdDiscoveryManager (mDNS), AlertWebSocketClient (OkHttp WS + reconnect/heartbeat)
    repository/    AlertRepository — single source of truth, ties discovery+socket+state together
    service/       AlertConnectionService — the persistent foreground service
    audio/         AlertAudioManager — USAGE_ALARM playback per level
    vibration/     AlertVibrationManager — per-level haptic patterns
    notification/  NotificationHelper — per-level high-importance channels, full-screen intent
    receiver/      BootCompletedReceiver — restarts the service after reboot
    di/            Hilt module
    ui/            MainActivity, FullScreenAlertActivity, AlertViewModel, screens/, components/, theme/
server/
  broker.js        Minimal reference Node.js LAN broker (WebSocket + mDNS advertisement)
```

## Wire protocol (client <-> broker)

```json
{
  "type": "ALERT",          // HELLO | HEARTBEAT | ALERT | STATE_SYNC
  "level": "RED",           // RED | YELLOW | WHITE
  "note": "آتش‌سوزی در طبقه دوم",
  "room": "ساختمان B - طبقه 2",
  "senderId": "device-id",
  "senderName": "احمدی",
  "timestamp": 1737310000000,
  "messageId": "uuid"
}
```

Any client sends `ALERT`; the broker stamps a fresh timestamp/messageId and
fans it out to every open socket unmodified. New clients receive the
last-known state as `STATE_SYNC` immediately on connect.

## Running it end-to-end

1. **Broker** (any always-on machine on the same LAN/subnet):
   ```bash
   cd server
   npm install
   node broker.js
   ```
   This both opens the WebSocket endpoint and advertises itself via mDNS as
   `_emergencyalert._tcp`, so phones on the same Wi-Fi find it automatically.

2. **Android app**: open the `EmergencyAlertApp` folder in Android Studio
   (Iguana or later), let Gradle sync, run on a device or emulator connected
   to the *same* Wi-Fi network as the broker. On first launch the app:
   - requests the `POST_NOTIFICATIONS` runtime permission (API 33+),
   - starts the foreground service,
   - auto-discovers the broker over mDNS (6s timeout), or falls back to the
     manual "تنظیم دستی آدرس سرور" dialog if discovery is blocked.

3. Trigger an alert from any connected client's dashboard — press and hold
   a level button to confirm — and every other connected device updates
   within the WebSocket round-trip (well under 500ms on a healthy LAN).

## Things to swap in for your real deployment

- **Alarm/chime/calm audio**: `app/src/main/res/raw/*.wav` currently contain
  synthesized placeholder tones (a two-tone warble, a chime, a descending
  calm tone) generated for a working out-of-the-box build. Replace them with
  your organization's approved sounds — keep the same filenames or update
  `AlertAudioManager` and `NotificationHelper` accordingly.
- **Authorization**: the reference broker accepts `ALERT` from any connected
  client. For production, add authentication (e.g. a pre-shared token per
  device, or mTLS if you introduce `wss://` with an internal CA) before
  trusting `senderId`/`senderName`.
- **Broker deployment**: `broker.js` is intentionally minimal (in-memory
  state, no persistence). For a real deployment, run it under a process
  supervisor (systemd/pm2) and consider an MQTT broker (e.g. Mosquitto) if
  you need queuing/QoS guarantees beyond a simple broadcast relay — the
  Android `AlertWebSocketClient` would need an MQTT client swap
  (e.g. HiveMQ MQTT client) but the rest of the architecture is unchanged.
- **App icon**: `ic_shield.xml` is a simple vector placeholder; swap in your
  organization's branding.

## Web (PWA) client — `server/public/`

A mobile-friendly web version now lives alongside the broker and is served
directly by it — no separate hosting, no build step, still zero cloud
dependency. `broker.js` was updated to serve `server/public/` as static
files on the same port as the WebSocket endpoint.

**Run it:**
```bash
cd server
npm install
node broker.js
```
Then, on any phone/laptop on the same Wi-Fi, open a browser to:
```
http://<broker-machine's-LAN-IP>:8765/
```
Tap "شروع نظارت" (this unlocks audio — mobile browsers block autoplay until
a user gesture), optionally "Add to Home Screen" for an app-like icon, and
the dashboard behaves like the native app: same RED/YELLOW/WHITE state
machine, same hold-to-confirm trigger buttons, same wire protocol, alarm
audio + vibration where the browser/OS supports it.

**Read this before relying on the web client for anything safety-critical:**
Browsers suspend JavaScript (including the WebSocket connection, timers, and
audio) when the tab is backgrounded or the screen locks. There is no browser
equivalent of Android's foreground service (`AlertConnectionService`). This
client mitigates what it can — reconnect-on-visibility, the Notification API
where permission is granted, an experimental Screen Wake Lock request during
RED — but none of that is a guarantee. For monitoring stations where the
alert absolutely must be received even if a phone is asleep or the app is
backgrounded, use the native Android app; use the web client for quick
access from any device, or as a secondary/display-only client on a screen
that's always awake and in the foreground (e.g. a wall-mounted tablet
running the PWA in kiosk mode).

Other platform gaps worth knowing about:
- **iOS Safari**: no Vibration API at all (silently ignored here); audio is
  muted by the hardware silent switch, unlike the native app's
  `AudioAttributes.USAGE_ALARM`, which bypasses it.
- **Autoplay**: every browser requires a user tap before it will play audio
  automatically later — that's what the "شروع نظارت" unlock screen is for.
