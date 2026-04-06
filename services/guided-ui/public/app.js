'use strict';

// ── DOM refs ─────────────────────────────────────────────────────────────────
const operationEl       = document.getElementById('operation');
const correlationEl     = document.getElementById('correlationId');
const numbersListEl     = document.getElementById('numbersList');
const addNumberBtn      = document.getElementById('addNumberBtn');
const outputPrefEl      = document.getElementById('outputPreference');
const prepareBtn        = document.getElementById('prepareBtn');
const sendBtn           = document.getElementById('sendBtn');
const payloadPreviewEl  = document.getElementById('payloadPreview');
const numbersErrorEl    = document.getElementById('numbersError');
const shapeIndicatorEl  = document.getElementById('shapeIndicator');
const shapeBadgeEl      = document.getElementById('shapeBadge');
const shapeDescEl       = document.getElementById('shapeDesc');
const loadingBarEl      = document.getElementById('loadingBar');
const responseMetaEl    = document.getElementById('responseMeta');
const responseBodyEl    = document.getElementById('responseBody');
const errorMetaEl       = document.getElementById('errorMeta');
const errorBodyEl       = document.getElementById('errorBody');

let preparedPayload = null;

// ── UUID ──────────────────────────────────────────────────────────────────────
function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

// ── JSON syntax highlighting ──────────────────────────────────────────────────
function highlightJson(obj) {
  const raw = JSON.stringify(obj, null, 2);
  return raw.replace(
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
    match => {
      if (/^"/.test(match)) {
        if (/:$/.test(match)) return `<span class="json-key">${match}</span>`;
        return `<span class="json-str">${match}</span>`;
      }
      if (/true|false/.test(match)) return `<span class="json-bool">${match}</span>`;
      if (/null/.test(match))       return `<span class="json-null">${match}</span>`;
      return `<span class="json-num">${match}</span>`;
    }
  );
}

// ── Shape detection ───────────────────────────────────────────────────────────
const META_KEYS = new Set(['precision', 'roundingMode', 'label', 'requestId', 'currency']);

function detectShape(payload) {
  if (!payload) return null;
  const keys = Object.keys(payload);
  const hasMeta = keys.some(k => META_KEYS.has(k));
  if (hasMeta) return 'v3';
  if (Array.isArray(payload.numbers)) return 'v2';
  return 'v1'; // shouldn't happen with current form but safe fallback
}

const SHAPE_INFO = {
  v2: { label: 'v2 shape', desc: 'array · no metadata → router will classify as v2', cls: 'active-v2' },
  v3: { label: 'v3 shape', desc: 'array + metadata → router will classify as v3',    cls: 'active-v3' },
};

function updateShapeIndicator(payload) {
  const shape = detectShape(payload);
  if (!shape || !SHAPE_INFO[shape]) return;
  const info = SHAPE_INFO[shape];
  shapeBadgeEl.textContent = info.label;
  shapeBadgeEl.className   = `shape-badge ${info.cls}`;
  shapeDescEl.textContent  = info.desc;
}

// ── Number inputs ─────────────────────────────────────────────────────────────
function addNumberInput(value = '') {
  const row = document.createElement('div');
  row.className = 'number-item';

  const input = document.createElement('input');
  input.type        = 'text';
  input.placeholder = '0';
  input.value       = value;
  input.setAttribute('inputmode', 'decimal');

  input.addEventListener('input', () => {
    validateNumberInput(input);
    invalidatePrepared();
    refreshLivePreview();
  });

  const removeBtn = document.createElement('button');
  removeBtn.type      = 'button';
  removeBtn.className = 'btn-remove';
  removeBtn.textContent = '✕';
  removeBtn.title     = 'Remove';
  removeBtn.addEventListener('click', () => {
    row.remove();
    invalidatePrepared();
    refreshLivePreview();
  });

  row.appendChild(input);
  row.appendChild(removeBtn);
  numbersListEl.appendChild(row);
  input.focus();
}

function validateNumberInput(input) {
  const val = input.value.trim();
  if (val === '') {
    input.className = '';
    return null;
  }
  const n = Number(val);
  if (isNaN(n)) {
    input.className = 'invalid';
    return null;
  }
  input.className = 'valid';
  return n;
}

// ── Build payload ─────────────────────────────────────────────────────────────
function buildPayload(strict = true) {
  numbersErrorEl.textContent = '';
  const operation = operationEl.value;
  const pref      = outputPrefEl.value;

  const inputs  = Array.from(numbersListEl.querySelectorAll('input'));
  const numbers = [];
  let hasInvalid = false;

  for (const input of inputs) {
    const val = input.value.trim();
    if (val === '') { hasInvalid = true; continue; }
    const n = Number(val);
    if (isNaN(n)) { hasInvalid = true; continue; }
    numbers.push(n);
  }

  if (strict) {
    if (hasInvalid || inputs.some(i => i.value.trim() === '')) {
      numbersErrorEl.textContent = 'All number fields must be valid numeric values.';
      return null;
    }
    if (numbers.length < 2) {
      numbersErrorEl.textContent = 'At least two numbers are required.';
      return null;
    }
    if (operation === 'divide') {
      if (numbers.slice(1).some(n => n === 0)) {
        numbersErrorEl.textContent = 'Divisors (all numbers after the first) must be non-zero.';
        return null;
      }
    }
  }

  const payload = { operation, numbers };

  // metadata fields → determines v3 shape
  if (pref === 'decimal') payload.precision = 'decimal';
  if (pref === 'rounded') payload.precision = 'integer';

  const labelVal = document.getElementById('metaLabel')?.value.trim();
  const reqIdVal = document.getElementById('metaRequestId')?.value.trim();
  const currVal  = document.getElementById('metaCurrency')?.value;
  const rmVal    = document.getElementById('metaRounding')?.value;

  if (labelVal)           payload.label        = labelVal;
  if (reqIdVal)           payload.requestId    = reqIdVal;
  if (currVal && currVal !== 'none') payload.currency = currVal;
  if (rmVal  && rmVal  !== 'none') payload.roundingMode = rmVal;

  return payload;
}

// ── Live preview (non-strict, best-effort) ─────────────────────────────────────
function refreshLivePreview() {
  const payload = buildPayload(false);
  if (!payload || !payload.numbers || payload.numbers.length === 0) {
    payloadPreviewEl.innerHTML = '<span style="color:var(--text3)">// fill in the form above</span>';
    payloadPreviewEl.classList.remove('ready');
    return;
  }
  payloadPreviewEl.innerHTML = highlightJson(payload);
  payloadPreviewEl.classList.add('ready');
  updateShapeIndicator(payload);
}

// ── Invalidate prepared state ──────────────────────────────────────────────────
function invalidatePrepared() {
  preparedPayload = null;
  sendBtn.disabled = true;
}

// ── Prepare ───────────────────────────────────────────────────────────────────
prepareBtn.addEventListener('click', () => {
  const payload = buildPayload(true);
  if (!payload) {
    invalidatePrepared();
    return;
  }
  preparedPayload = payload;
  payloadPreviewEl.innerHTML = highlightJson(payload);
  payloadPreviewEl.classList.add('ready');
  updateShapeIndicator(payload);
  sendBtn.disabled = false;
  sendBtn.textContent = '⚡ Send Request';
});

// ── Send ──────────────────────────────────────────────────────────────────────
sendBtn.addEventListener('click', async () => {
  if (!preparedPayload) return;

  // clear panels
  responseMetaEl.innerHTML = '';
  responseBodyEl.textContent = 'Waiting for response…';
  responseBodyEl.className = 'response-body';
  errorMetaEl.innerHTML = '';
  errorBodyEl.textContent = 'No error.';
  errorBodyEl.className = 'response-body';

  loadingBarEl.classList.add('active');
  sendBtn.disabled = true;
  sendBtn.textContent = 'Sending…';

  const correlationId = correlationEl.value.trim() || uuidv4();

  try {
    const resp = await fetch('/api/route', {
      method:  'POST',
      headers: {
        'Content-Type':   'application/json',
        'X-Correlation-Id': correlationId,
      },
      body: JSON.stringify(preparedPayload),
    });

    const text = await resp.text();
    let body = {};
    try { body = JSON.parse(text); } catch { body = { raw: text }; }

    loadingBarEl.classList.remove('active');
    sendBtn.disabled = false;
    sendBtn.textContent = '⚡ Send Request';

    if (resp.ok) {
      renderSuccess(resp.status, body);
      // rotate correlation ID for next request
      correlationEl.value = uuidv4();
    } else {
      renderError(resp.status, text, body);
    }
  } catch (err) {
    loadingBarEl.classList.remove('active');
    sendBtn.disabled = false;
    sendBtn.textContent = '⚡ Send Request';
    renderError(0, String(err), null);
  }
});

// ── Render success ────────────────────────────────────────────────────────────
function renderSuccess(status, body) {
  const chips = [];
  chips.push(`<span class="meta-chip success">HTTP ${status}</span>`);
  if (body.routedTo)      chips.push(`<span class="meta-chip highlight">routedTo: ${body.routedTo}</span>`);
  if (body.operation)     chips.push(`<span class="meta-chip">op: ${body.operation}</span>`);
  if (body.version)       chips.push(`<span class="meta-chip">ver: ${body.version}</span>`);
  if (body.confidence != null) {
    const pct = (body.confidence * 100).toFixed(1);
    chips.push(`<span class="meta-chip">confidence: ${pct}%</span>`);
  }
  if (body.correlationId) chips.push(`<span class="meta-chip">corr: ${body.correlationId.slice(0,8)}…</span>`);
  responseMetaEl.innerHTML = chips.join('');
  responseBodyEl.innerHTML = highlightJson(body);
  responseBodyEl.className = 'response-body has-success';
}

// ── Render error ──────────────────────────────────────────────────────────────
function renderError(status, rawText, parsed) {
  const chips = [];
  chips.push(`<span class="meta-chip error">HTTP ${status || 'network error'}</span>`);
  if (parsed?.code)          chips.push(`<span class="meta-chip warn">code: ${parsed.code}</span>`);
  if (parsed?.service)       chips.push(`<span class="meta-chip">service: ${parsed.service}</span>`);
  if (parsed?.correlationId) chips.push(`<span class="meta-chip">corr: ${parsed.correlationId.slice(0,8)}…</span>`);
  errorMetaEl.innerHTML = chips.join('');

  if (parsed) {
    errorBodyEl.innerHTML = highlightJson(parsed);
  } else {
    errorBodyEl.textContent = rawText;
  }
  errorBodyEl.className = 'response-body has-error';
}

// ── Wire change listeners ─────────────────────────────────────────────────────
operationEl.addEventListener('change', () => { invalidatePrepared(); refreshLivePreview(); });
outputPrefEl.addEventListener('change', () => { invalidatePrepared(); refreshLivePreview(); });
numbersListEl.addEventListener('input', () => { invalidatePrepared(); refreshLivePreview(); });

// metadata fields
['metaLabel','metaRequestId','metaCurrency','metaRounding'].forEach(id => {
  const el = document.getElementById(id);
  if (el) el.addEventListener('change', () => { invalidatePrepared(); refreshLivePreview(); });
  if (el) el.addEventListener('input',  () => { invalidatePrepared(); refreshLivePreview(); });
});

addNumberBtn.addEventListener('click', () => addNumberInput(''));

// ── Init ──────────────────────────────────────────────────────────────────────
correlationEl.value = uuidv4();
addNumberInput('10');
addNumberInput('5');
refreshLivePreview();
