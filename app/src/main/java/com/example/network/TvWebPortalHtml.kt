package com.example.network

object TvWebPortalHtml {

    fun render(deviceName: String, pinRequired: Boolean): String {
        val safeDevice = escapeHtml(deviceName)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>TV Beam — $safeDevice</title>
<style>
  :root {
    --bg: #070D19;
    --surface: #0E1729;
    --card: #16233B;
    --border: #243757;
    --cyan: #00E5FF;
    --amber: #FFB300;
    --green: #10B981;
    --text: #F4F7FB;
    --muted: #94A3B8;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
    background: radial-gradient(circle at top right, #0E233F 0%, var(--bg) 60%);
    color: var(--text);
    min-height: 100vh;
    padding: 24px 28px 48px;
  }
  .container { max-width: 1120px; margin: 0 auto; }
  header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 20px 24px;
    background: rgba(14, 23, 41, 0.92);
    border: 1px solid var(--border);
    border-radius: 20px;
    margin-bottom: 20px;
  }
  .brand { display: flex; align-items: center; gap: 14px; }
  .logo {
    width: 48px; height: 48px; border-radius: 14px;
    background: linear-gradient(135deg, #00E5FF, #007B94);
    display: flex; align-items: center; justify-content: center;
    color: #001F26; font-weight: 800; font-size: 20px;
  }
  h1 { font-size: 24px; font-weight: 700; letter-spacing: -0.3px; }
  .sub { color: var(--muted); font-size: 14px; margin-top: 3px; }
  .actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  .pill {
    display: inline-flex; align-items: center; gap: 8px;
    padding: 8px 14px; border-radius: 999px;
    background: rgba(16, 185, 129, 0.14); border: 1px solid rgba(16, 185, 129, 0.45);
    color: #34D399; font-size: 13px; font-weight: 600; cursor: pointer;
  }
  .pill.off {
    background: rgba(148, 163, 184, 0.12);
    border-color: rgba(148, 163, 184, 0.35);
    color: var(--muted);
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; }
  .pill.off .dot { background: #64748B; }
  .btn {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    padding: 12px 20px; border-radius: 12px; border: none;
    font-size: 15px; font-weight: 700; cursor: pointer; text-decoration: none;
    transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
    outline: none;
  }
  .btn:focus, .btn:hover, .pill:focus {
    transform: translateY(-2px) scale(1.02);
    box-shadow: 0 0 0 3px var(--cyan);
  }
  .btn-primary { background: var(--cyan); color: #001F26; }
  .btn-secondary { background: var(--card); color: var(--text); border: 1px solid var(--border); }
  .btn-amber { background: var(--amber); color: #261900; }
  .banner {
    padding: 14px 18px; border-radius: 14px;
    background: rgba(0, 229, 255, 0.08); border: 1px solid rgba(0, 229, 255, 0.28);
    color: #CFFAFE; font-size: 14px; margin-bottom: 18px;
    display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
  }
  .push-toast {
    display: none;
    padding: 14px 18px; border-radius: 14px;
    background: rgba(16, 185, 129, 0.18); border: 1px solid rgba(16, 185, 129, 0.55);
    color: #6EE7B7; font-size: 15px; font-weight: 700; margin-bottom: 18px;
  }
  .push-toast.show { display: block; }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
    gap: 18px;
  }
  .card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 18px;
    overflow: hidden;
    display: flex; flex-direction: column;
    transition: border-color 0.15s ease, transform 0.15s ease;
  }
  .card:focus-within, .card:hover { border-color: var(--cyan); }
  .thumb {
    width: 100%; height: 170px; background: #0B132B;
    display: flex; align-items: center; justify-content: center;
    position: relative; overflow: hidden;
  }
  .thumb img { width: 100%; height: 100%; object-fit: cover; }
  .badge {
    position: absolute; top: 10px; left: 10px;
    padding: 4px 10px; border-radius: 8px;
    background: rgba(7, 13, 25, 0.86); border: 1px solid var(--border);
    color: var(--cyan); font-size: 12px; font-weight: 700;
  }
  .duration {
    position: absolute; bottom: 10px; right: 10px;
    padding: 3px 8px; border-radius: 6px;
    background: rgba(7, 13, 25, 0.88);
    color: #fff; font-size: 12px; font-weight: 600;
  }
  .card-body { padding: 16px; display: flex; flex-direction: column; gap: 12px; flex: 1; }
  .fname {
    font-size: 16px; font-weight: 700; line-height: 1.35;
    word-break: break-word; color: var(--text);
  }
  .meta { font-size: 13px; color: var(--muted); display: flex; gap: 12px; flex-wrap: wrap; }
  .card-actions { display: flex; gap: 10px; margin-top: auto; flex-wrap: wrap; }
  .card-actions .btn { flex: 1; padding: 11px 14px; font-size: 14px; }
  .empty {
    text-align: center; padding: 64px 20px;
    background: var(--surface); border: 1px dashed var(--border); border-radius: 20px;
    color: var(--muted);
  }
  .empty h2 { color: var(--text); margin-bottom: 8px; font-size: 20px; }
  .modal {
    position: fixed; inset: 0; background: rgba(4, 8, 16, 0.92);
    display: none; align-items: center; justify-content: center; padding: 24px; z-index: 50;
  }
  .modal.open { display: flex; }
  .modal-box {
    width: 100%; max-width: 900px; background: var(--surface);
    border: 1px solid var(--border); border-radius: 20px; padding: 20px;
  }
  .modal-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; gap: 12px; }
  video { width: 100%; max-height: 70vh; border-radius: 12px; background: #000; }
  .pin-wrap {
    max-width: 420px; margin: 60px auto; padding: 28px;
    background: var(--surface); border: 1px solid var(--border); border-radius: 20px;
    text-align: center;
  }
  .pin-input {
    width: 100%; padding: 14px; font-size: 24px; text-align: center; letter-spacing: 8px;
    border-radius: 12px; border: 1px solid var(--border); background: var(--bg); color: var(--text);
    margin: 18px 0;
  }
  .pin-input:focus { outline: none; border-color: var(--cyan); box-shadow: 0 0 0 3px rgba(0,229,255,0.25); }
  .err { color: #FF5252; font-size: 14px; margin-bottom: 10px; min-height: 20px; }
</style>
</head>
<body>
<div class="container">
  <header>
    <div class="brand">
      <div class="logo">TV</div>
      <div>
        <h1>TV Beam Receiver</h1>
        <div class="sub">From: $safeDevice &bull; SHAREit-Style Hotspot Direct Save</div>
      </div>
    </div>
    <div class="actions">
      <button id="autoSaveToggle" class="pill" onclick="toggleAutoSave()">
        <span class="dot"></span> <span id="autoSaveLabel">Auto-Save Incoming: ON</span>
      </button>
      <button id="saveAllBtn" class="btn btn-amber" onclick="downloadAll()" style="display:none;">
        &#11015; Save All to TV Storage
      </button>
      <button class="btn btn-secondary" onclick="loadFiles()">&#8635; Refresh</button>
    </div>
  </header>

  <div id="pinGate" class="pin-wrap" style="display:${if (pinRequired) "block" else "none"};">
    <h2>Enter 4-Digit TV PIN</h2>
    <p class="sub" style="margin-top:8px;">Check your phone's TV Beam app for the PIN code.</p>
    <input id="pinField" class="pin-input" type="password" maxlength="4" inputmode="numeric" placeholder="••••" />
    <div id="pinErr" class="err"></div>
    <button class="btn btn-primary" style="width:100%;" onclick="verifyPin()">Unlock Files</button>
  </div>

  <div id="mainArea" style="display:${if (pinRequired) "none" else "block"};">
    <div id="pushToast" class="push-toast"></div>
    <div class="banner">
      <span>Tap <strong>Send</strong> on your phone to auto-save straight to TV Storage, or press <strong>Save to TV</strong> below.</span>
      <span id="countInfo">Connecting to phone...</span>
    </div>
    <div id="fileGrid" class="grid"></div>
  </div>
</div>

<div id="playerModal" class="modal">
  <div class="modal-box">
    <div class="modal-head">
      <strong id="playerTitle" style="font-size:17px;">Video Preview</strong>
      <button class="btn btn-secondary" onclick="closePlayer()">Close</button>
    </div>
    <video id="videoEl" controls autoplay playsinline></video>
  </div>
</div>

<script>
let currentPin = sessionStorage.getItem('tvbeam_pin') || '';
let filesCache = [];
let needPin = ${if (pinRequired) "true" else "false"};
let autoSaveEnabled = true;
let lastSeenPushSeq = -1;

if (needPin && currentPin) {
  verifyPin(currentPin);
} else if (!needPin) {
  loadFiles();
}

setInterval(() => {
  if (!needPin || currentPin) loadFiles(true);
}, 1200);

function toggleAutoSave() {
  autoSaveEnabled = !autoSaveEnabled;
  const btn = document.getElementById('autoSaveToggle');
  const lbl = document.getElementById('autoSaveLabel');
  if (autoSaveEnabled) {
    btn.classList.remove('off');
    lbl.textContent = 'Auto-Save Incoming: ON';
  } else {
    btn.classList.add('off');
    lbl.textContent = 'Auto-Save Incoming: OFF';
  }
}

async function verifyPin(preset) {
  const pin = preset || document.getElementById('pinField').value.trim();
  const res = await fetch('/api/auth?pin=' + encodeURIComponent(pin));
  const data = await res.json();
  if (data.ok) {
    currentPin = pin;
    sessionStorage.setItem('tvbeam_pin', pin);
    document.getElementById('pinGate').style.display = 'none';
    document.getElementById('mainArea').style.display = 'block';
    loadFiles();
  } else {
    document.getElementById('pinErr').textContent = 'Incorrect PIN. Please try again.';
  }
}

async function loadFiles(silent) {
  try {
    const res = await fetch('/api/files?pin=' + encodeURIComponent(currentPin) + '&client=TVBrowser');
    if (res.status === 401) {
      document.getElementById('pinGate').style.display = 'block';
      document.getElementById('mainArea').style.display = 'none';
      return;
    }
    const payload = await res.json();
    const items = payload.files || [];
    const pushSeq = payload.pushSeq || 0;
    const pushedIds = payload.pushedIds || [];

    if (lastSeenPushSeq === -1) {
      lastSeenPushSeq = pushSeq;
    } else if (pushSeq > lastSeenPushSeq) {
      lastSeenPushSeq = pushSeq;
      if (autoSaveEnabled && pushedIds.length > 0) {
        triggerAutoReceive(pushedIds, items);
      }
    }

    const signature = items.map(i => i.id + ':' + i.downloads).join('|');
    const oldSig = filesCache.map(i => i.id + ':' + i.downloads).join('|');
    filesCache = items;
    if (silent && signature === oldSig) return;
    renderFiles(items);
  } catch (e) {
    if (!silent) {
      document.getElementById('countInfo').textContent = 'Waiting for phone connection...';
    }
  }
}

function triggerAutoReceive(pushedIds, items) {
  const toDownload = items.filter(i => pushedIds.includes(i.id));
  if (toDownload.length === 0) return;
  const toast = document.getElementById('pushToast');
  toast.textContent = 'Receiving from Phone (' + toDownload.length + ' file): Saving straight to TV Storage...';
  toast.classList.add('show');
  toDownload.forEach((item, idx) => {
    setTimeout(() => {
      const a = document.createElement('a');
      a.href = '/download/' + item.id + '?pin=' + encodeURIComponent(currentPin);
      a.download = item.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }, idx * 800);
  });
  setTimeout(() => toast.classList.remove('show'), 6000);
}

function renderFiles(items) {
  const grid = document.getElementById('fileGrid');
  const countInfo = document.getElementById('countInfo');
  const saveAllBtn = document.getElementById('saveAllBtn');
  countInfo.textContent = items.length + ' file(s) ready';
  saveAllBtn.style.display = items.length > 1 ? 'inline-flex' : 'none';

  if (items.length === 0) {
    grid.innerHTML = '<div class="empty" style="grid-column:1/-1;"><h2>Ready to Receive from Phone</h2><p>Select videos or files in TV Beam on your phone and tap Send — they will save right into your TV Storage.</p></div>';
    return;
  }

  grid.innerHTML = items.map(item => {
    const q = '?pin=' + encodeURIComponent(currentPin);
    const dlUrl = '/download/' + item.id + q;
    const streamUrl = '/stream/' + item.id + q;
    const thumbHtml = item.hasThumb
      ? '<img src="/thumb/' + item.id + q + '" alt="thumb" />'
      : '<span style="font-size:42px;opacity:0.75;">' + iconFor(item.category) + '</span>';
    const durHtml = item.duration ? '<span class="duration">' + esc(item.duration) + '</span>' : '';
    const watchBtn = item.category === 'VIDEO'
      ? '<button class="btn btn-secondary" onclick="openPlayer(\'' + streamUrl + '\', \'' + escAttr(item.name) + '\')">&#9654; Watch</button>'
      : '';
    return '<div class="card">' +
      '<div class="thumb">' + thumbHtml +
        '<span class="badge">' + esc(item.category) + '</span>' + durHtml +
      '</div>' +
      '<div class="card-body">' +
        '<div class="fname">' + esc(item.name) + '</div>' +
        '<div class="meta"><span>' + esc(item.sizeText) + '</span>' +
          (item.downloads > 0 ? '<span style="color:#10B981;">&#10003; Saved ' + item.downloads + 'x</span>' : '<span>Ready</span>') +
        '</div>' +
        '<div class="card-actions">' +
          '<a class="btn btn-primary" href="' + dlUrl + '" download="' + escAttr(item.name) + '">&#11015; Save to TV</a>' +
          watchBtn +
        '</div>' +
      '</div>' +
    '</div>';
  }).join('');
}

function downloadAll() {
  filesCache.forEach((item, idx) => {
    setTimeout(() => {
      const a = document.createElement('a');
      a.href = '/download/' + item.id + '?pin=' + encodeURIComponent(currentPin);
      a.download = item.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }, idx * 900);
  });
}

function openPlayer(url, title) {
  document.getElementById('playerTitle').textContent = title;
  const v = document.getElementById('videoEl');
  v.src = url;
  document.getElementById('playerModal').classList.add('open');
  v.play().catch(() => {});
}

function closePlayer() {
  const v = document.getElementById('videoEl');
  v.pause();
  v.removeAttribute('src');
  v.load();
  document.getElementById('playerModal').classList.remove('open');
}

function iconFor(cat) {
  if (cat === 'VIDEO') return '&#127916;';
  if (cat === 'PHOTO') return '&#128444;';
  if (cat === 'AUDIO') return '&#127925;';
  if (cat === 'APK') return '&#128230;';
  return '&#128196;';
}
function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function escAttr(s) { return esc(s).replace(/'/g, "\\'"); }
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
