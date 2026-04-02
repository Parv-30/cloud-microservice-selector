const operationEl = document.getElementById("operation");
const correlationEl = document.getElementById("correlationId");
const numbersListEl = document.getElementById("numbersList");
const addNumberBtn = document.getElementById("addNumberBtn");
const outputPreferenceEl = document.getElementById("outputPreference");
const prepareBtn = document.getElementById("prepareBtn");
const sendBtn = document.getElementById("sendBtn");
const payloadPreviewEl = document.getElementById("payloadPreview");
const summaryEl = document.getElementById("summary");
const responseMetaEl = document.getElementById("responseMeta");
const responseBodyEl = document.getElementById("responseBody");
const errorMetaEl = document.getElementById("errorMeta");
const errorBodyEl = document.getElementById("errorBody");

const numbersError = document.getElementById("numbersError");

let preparedPayload = null;

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    const v = c === "x" ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function addNumberInput(value = "") {
  const row = document.createElement("div");
  row.className = "number-item";

  const input = document.createElement("input");
  input.type = "text";
  input.placeholder = "number";
  input.value = value;

  const removeBtn = document.createElement("button");
  removeBtn.type = "button";
  removeBtn.textContent = "Remove";
  removeBtn.addEventListener("click", () => {
    row.remove();
  });

  row.appendChild(input);
  row.appendChild(removeBtn);
  numbersListEl.appendChild(row);
}

function clearErrors() {
  numbersError.textContent = "";
}

function buildPayload() {
  clearErrors();
  const operation = operationEl.value;
  const outputPreference = outputPreferenceEl.value;

  const numbersInputs = Array.from(numbersListEl.querySelectorAll("input"));
  const numbers = [];
  for (const input of numbersInputs) {
    const parsed = Number(input.value.trim());
    if (input.value.trim() === "" || Number.isNaN(parsed)) {
      numbersError.textContent = "Every number must be numeric.";
      return null;
    }
    numbers.push(parsed);
  }

  if (numbers.length < 2) {
    numbersError.textContent = "At least two numbers are required.";
    return null;
  }

  if (operation === "divide") {
    const invalidDivisor = numbers.slice(1).some(n => n === 0);
    if (invalidDivisor) {
      numbersError.textContent = "For divide, all divisors after first number must be non-zero.";
      return null;
    }
  }

  const payload = { operation, numbers };

  if (outputPreference === "decimal") {
    payload.precision = "decimal";
  } else if (outputPreference === "rounded") {
    payload.precision = "integer";
  }

  return payload;
}

function payloadShape(payload) {
  const hasMetadata = ["precision", "roundingMode", "label", "requestId"]
    .some(k => Object.prototype.hasOwnProperty.call(payload, k));

  return hasMetadata ? "v3-like" : "v2-like";
}

function preferenceLabel(value) {
  if (value === "decimal") {
    return "decimal";
  }
  if (value === "rounded") {
    return "rounded";
  }
  return "none";
}

function renderPrepared(payload) {
  payloadPreviewEl.textContent = JSON.stringify(payload, null, 2);
  const shape = payloadShape(payload);
  const count = Array.isArray(payload.numbers) ? payload.numbers.length : 0;
  summaryEl.textContent = `operation=${payload.operation} | operands=${count} | preference=${preferenceLabel(outputPreferenceEl.value)} | shape=${shape} | correlationId=${correlationEl.value}`;
}

function renderResponse(status, body) {
  const meta = [];
  if (body && body.correlationId) meta.push(`correlationId=${body.correlationId}`);
  if (body && body.routedTo) meta.push(`routedTo=${body.routedTo}`);
  if (body && body.operation) meta.push(`operation=${body.operation}`);
  if (body && body.version) meta.push(`version=${body.version}`);
  responseMetaEl.textContent = `status=${status}${meta.length ? " | " + meta.join(" | ") : ""}`;
  responseBodyEl.textContent = JSON.stringify(body, null, 2);
}

function renderError(status, bodyText) {
  let parsed = null;
  try {
    parsed = JSON.parse(bodyText);
  } catch {
    parsed = null;
  }

  const correlationId = parsed && parsed.correlationId ? parsed.correlationId : "n/a";
  const code = parsed && parsed.code ? parsed.code : "n/a";
  const service = parsed && parsed.service ? parsed.service : "n/a";

  errorMetaEl.textContent = `status=${status} | correlationId=${correlationId} | code=${code} | service=${service}`;
  errorBodyEl.textContent = parsed ? JSON.stringify(parsed, null, 2) : bodyText;
}

prepareBtn.addEventListener("click", () => {
  const payload = buildPayload();
  if (!payload) {
    sendBtn.disabled = true;
    preparedPayload = null;
    return;
  }

  preparedPayload = payload;
  renderPrepared(payload);
  sendBtn.disabled = false;
});

sendBtn.addEventListener("click", async () => {
  if (!preparedPayload) {
    return;
  }

  const ok = window.confirm("Send this prepared request to router now?");
  if (!ok) {
    return;
  }

  responseMetaEl.textContent = "";
  errorMetaEl.textContent = "";
  responseBodyEl.textContent = "No response yet.";
  errorBodyEl.textContent = "No error.";

  const correlationId = correlationEl.value.trim();

  try {
    const resp = await fetch("/api/route", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Correlation-Id": correlationId
      },
      body: JSON.stringify(preparedPayload)
    });

    const text = await resp.text();
    const body = text ? JSON.parse(text) : {};

    if (resp.ok) {
      renderResponse(resp.status, body);
      return;
    }

    renderError(resp.status, text);
  } catch (err) {
    renderError(0, String(err));
  }
});

function invalidatePreparedRequest() {
  sendBtn.disabled = true;
  preparedPayload = null;
}

addNumberBtn.addEventListener("click", () => addNumberInput(""));

operationEl.addEventListener("change", invalidatePreparedRequest);
outputPreferenceEl.addEventListener("change", invalidatePreparedRequest);
numbersListEl.addEventListener("input", invalidatePreparedRequest);

correlationEl.value = uuidv4();
addNumberInput("10");
addNumberInput("5");
