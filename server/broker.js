/**
 * Minimal reference LAN broker for the Intranet Emergency Alert app.
 *
 * Responsibilities:
 *   1. Serve the Progressive Web App (server/public/) as static files, so
 *      any phone on the LAN can browse to http://<this-machine-ip>:PORT/
 *      and use the alert dashboard directly — no app store, no build step.
 *   2. Advertise itself on the LAN via mDNS as "_emergencyalert._tcp" so
 *      the native Android client can find it with zero configuration.
 *   3. Accept WebSocket connections at ws://<host>:<port>/alerts from both
 *      the web clients and the native Android client (identical wire
 *      protocol for both).
 *   4. Track the last-known alert state and broadcast every ALERT message
 *      to all connected clients immediately (no queuing/store-and-forward),
 *      keeping LAN broadcast latency well under 500ms.
 *   5. Answer new connections with a STATE_SYNC message so late joiners
 *      immediately see the current status instead of only WHITE.
 *
 * Requirements: Node.js 18+, and two small deps (see package.json):
 *   npm install
 *   node broker.js
 *
 * Then, on any phone connected to the same Wi-Fi, open a browser to:
 *   http://<this-machine's-LAN-IP>:8765/
 * and optionally "Add to Home Screen" for an app-like icon.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');
const bonjour = require('bonjour')();
const { randomUUID } = require('crypto');

const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8765;
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.wav': 'audio/wav',
  '.ico': 'image/x-icon',
};

let currentState = {
  type: 'STATE_SYNC',
  level: 'WHITE',
  note: null,
  room: null,
  senderId: 'broker',
  senderName: 'سرور',
  timestamp: Date.now(),
  messageId: randomUUID(),
};

function serveStatic(req, res) {
  let reqPath = decodeURIComponent(req.url.split('?')[0]);
  if (reqPath === '/') reqPath = '/index.html';

  const filePath = path.normalize(path.join(PUBLIC_DIR, reqPath));

  // Prevent path traversal outside of PUBLIC_DIR.
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('صفحه مورد نظر یافت نشد');
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, {
      'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600',
    });
    res.end(data);
  });
}

const httpServer = http.createServer((req, res) => serveStatic(req, res));

const wss = new WebSocketServer({ server: httpServer, path: '/alerts' });

wss.on('connection', (ws, req) => {
  console.log(`Client connected from ${req.socket.remoteAddress}`);
  ws.send(JSON.stringify(currentState));

  ws.on('message', (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      console.warn('Dropping unparseable message:', e.message);
      return;
    }

    if (msg.type === 'HELLO' || msg.type === 'HEARTBEAT') {
      return; // liveness signals only, nothing to broadcast
    }

    if (msg.type === 'ALERT') {
      currentState = {
        type: 'ALERT',
        level: msg.level || 'WHITE',
        note: msg.note ?? null,
        room: msg.room ?? null,
        senderId: msg.senderId ?? 'unknown',
        senderName: msg.senderName ?? 'ناشناس',
        timestamp: Date.now(),
        messageId: randomUUID(),
      };
      const payload = JSON.stringify(currentState);
      let delivered = 0;
      wss.clients.forEach((client) => {
        if (client.readyState === client.OPEN) {
          client.send(payload);
          delivered += 1;
        }
      });
      console.log(`Broadcast ${currentState.level} to ${delivered} client(s)`);
    }
  });

  ws.on('close', () => console.log('Client disconnected'));
});

httpServer.listen(PORT, () => {
  console.log(`Emergency alert broker + web app listening on http://0.0.0.0:${PORT}/`);
  console.log(`WebSocket endpoint: ws://0.0.0.0:${PORT}/alerts`);
});

// Advertise on the LAN so the native Android app's NSD/mDNS discovery finds
// this broker with zero manual configuration. (The web client just uses the
// same origin it was loaded from, so it needs no discovery step at all.)
bonjour.publish({
  name: 'ChemZTech-AlertBroker',
  type: 'emergencyalert',
  protocol: 'tcp',
  port: PORT,
});

process.on('SIGINT', () => {
  console.log('Shutting down broker...');
  bonjour.unpublishAll(() => process.exit(0));
});
