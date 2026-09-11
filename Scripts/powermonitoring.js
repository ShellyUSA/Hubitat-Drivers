// ==========================================
// Power Monitoring Collector & Reporter
// ==========================================
// Runs on a Shelly Gen2+ device to collect
// power readings from status events, average
// them over a configurable interval, and
// report aggregated data to a Hubitat hub.
//
// Supported components:
//   PM1/switch/cover  - single-phase, inline energy (aenergy.total)
//   EM1               - single-phase, separate energy (em1data)
//   EM                - 3-phase, separate energy (emdata)
// ==========================================

// === USER CONFIGURATION ===
let POWERMONITOR_SCRIPT_VERSION = "2.3.0";
let DEFAULT_REPORT_INTERVAL = 60; // Fallback if KVS lookup fails
let REPORT_INTERVAL = DEFAULT_REPORT_INTERVAL;
let REPORT_INTERVAL_KVS_KEY = "hubitat_sdm_pm_ri"; // KVS key for dynamic report interval (seconds)
let SETTINGS_REFRESH_INTERVAL_SECS = 900; // Refresh KVS settings at most every 15 minutes
let STATUS_POLL_INTERVAL_SECS = 900; // GetStatus is a fallback, not the normal sampling path
let STATUS_POLL_RETRY_SECS = 120; // Retry sooner after a failed startup/status poll
let REPORT_REQUEST_TIMEOUT_SECS = 10;
let REPORT_DRAIN_DELAY_MS = 100;
let SETTINGS_ADVANCE_DELAY_MS = 10;
let MAX_PENDING_REPORTS = 8;
let MAX_PENDING_BYTES = 2048;
let MAX_REPORT_BYTES = 768;
let MAX_COMPONENT_ID = 7; // Supported component IDs are 0..7; reject unexpected keys
let MAX_TRACKED_COMPONENTS = 16;

// Hubitat KVS configuration
let HUBITAT_KVS_KEY = "hubitat_sdm_ip"; // store only the IP (no protocol/port) in Shelly KVS
let HUBITAT_DEFAULT_IP = "192.168.1.4"; // fallback if KVS lookup fails
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";

// Report only when a value changes by at least its threshold. Set a threshold
// to 0 to report every interval when that value is available.
let THRESH_V = 1;      // voltage (V)
let THRESH_C = 0.05;   // current (A)
let THRESH_P = 5;      // power (W)
let THRESH_E = 5;      // energy (Wh)
let THRESH_F = 0.5;    // frequency (Hz)
let REPORT_SETTINGS = [
  [REPORT_INTERVAL_KVS_KEY, "interval"],
  ["hubitat_sdm_pm_th_v", "voltage"],
  ["hubitat_sdm_pm_th_c", "current"],
  ["hubitat_sdm_pm_th_p", "power"],
  ["hubitat_sdm_pm_th_e", "energy"],
  ["hubitat_sdm_pm_th_f", "frequency"],
];

// REMOTE_URL is built from KVS value (or fallback)
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;

// === Per-component accumulators ===
let comps = {}; // Keyed by component name (e.g. "pm1:0", "em:0")
let compKeys = []; // Track keys for iteration (mJS has no Object.keys)

// === Bounded operation state ===
// Only one status read, settings refresh, or report POST is allowed at a time.
let reportTimerHandle = null;
let reportCycleInProgress = false;
let statusPollInFlight = false;
let settingsRefreshInFlight = false;
let settingsRefreshIndex = 0;
let settingsRefreshCallback = null;
let settingsAdvanceTimerHandle = null;
let settingsCyclesRemaining = 0;
let statusPollCyclesRemaining = 0;
let statusEventsSinceReport = false;
let reportQueue = [];
let reportQueueBytes = 0;
let reportInFlight = false;
let reportInFlightToken = 0;
let reportInFlightData = null; // The accumulator only; never the report/payload object
let reportInFlightBytes = 0;
let reportDrainTimerHandle = null;
let REPORT_PREFIXES = ["switch", "pm1", "cover", "em", "em1"];
let PHASES = ["a", "b", "c"];
let statusHandlerHandle = null;
let powerMonitorStarted = false;

// P0 diagnostics. These are intentionally scalar counters only; no device
// status or HTTP response bodies are retained.
let totalStatusPolls = 0;
let failedStatusPolls = 0;
let statusEventsObserved = 0;
let totalReportsSent = 0;
let failedReports = 0;
let droppedReports = 0;
let replacedQueuedReports = 0;

function printDiagnostics(reason) {
  print(
    "Power monitor diagnostics (" + reason + ")" +
      ": version=" + POWERMONITOR_SCRIPT_VERSION +
      " components=" + compKeys.length +
      " queue=" + reportQueue.length +
      " queueBytes=" + reportQueueBytes +
      " reportInFlight=" + (reportInFlight ? 1 : 0) +
      " reportInFlightBytes=" + reportInFlightBytes +
      " statusInFlight=" + (statusPollInFlight ? 1 : 0) +
      " settingsInFlight=" + (settingsRefreshInFlight ? 1 : 0) +
      " statusPolls=" + totalStatusPolls +
      " statusFailures=" + failedStatusPolls +
      " statusEvents=" + statusEventsObserved +
      " reports=" + totalReportsSent +
      " reportFailures=" + failedReports +
      " dropped=" + droppedReports +
      " replaced=" + replacedQueuedReports,
  );
}

function noteStatusData() {
  statusEventsObserved++;
  statusEventsSinceReport = true;
}

// mJS does not provide the Array shift method. Remove and return the oldest queued
// report using only indexed access and the length property.
function dequeueReport() {
  if (reportQueue.length === 0) return null;
  let report = reportQueue[0];
  let lastIndex = reportQueue.length - 1;
  for (let i = 1; i <= lastIndex; i++) {
    reportQueue[i - 1] = reportQueue[i];
  }
  // Clear the stale tail reference before shrinking the array. This makes the
  // release explicit on the small mJS heap rather than relying on truncation.
  reportQueue[lastIndex] = null;
  reportQueue.length = lastIndex;
  reportQueueBytes -= report.bytes || 0;
  if (reportQueueBytes < 0) reportQueueBytes = 0;
  return report;
}

// Build a full URL from a KVS-stored IP (handles already-present protocol/port gracefully)
function buildRemoteUrlFromRaw(raw) {
  if (!raw || typeof raw !== "string")
    return HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
  let s = raw.trim();
  // if already contains protocol, return as-is (but ensure port exists)
  if (s.indexOf("http://") === 0 || s.indexOf("https://") === 0) {
    return s.indexOf(":") === -1 ? s + ":" + HUBITAT_PORT : s;
  }
  // if host:port provided, just add protocol
  if (s.indexOf(":") !== -1) return HUBITAT_PROTO + s;
  // otherwise append port
  return HUBITAT_PROTO + s + ":" + HUBITAT_PORT;
}

// Continue the serialized KVS chain only after the current callback frame has
// returned. This avoids retaining one response while the next RPC is created.
function scheduleSettingsAdvance() {
  if (settingsAdvanceTimerHandle !== null) return;
  settingsAdvanceTimerHandle = Timer.set(SETTINGS_ADVANCE_DELAY_MS, false, advanceSettingsRefresh);
}

function advanceSettingsRefresh() {
  settingsAdvanceTimerHandle = null;
  fetchNextReportSetting();
}

function onRemoteUrlFetched(res, err, msg) {
  if (err !== 0 || res === undefined || res === null) {
    print("KVS.Get did not return a value; using REMOTE_URL=" + REMOTE_URL);
    res = null;
    msg = null;
    scheduleSettingsAdvance();
    return;
  }
  // attempt to extract value from common response shapes
  let ipVal = null;
  if (typeof res.value === "string") ipVal = res.value;
  else if (res.result && typeof res.result.value === "string") ipVal = res.result.value;
  else if (typeof res === "string") ipVal = res;
  if (ipVal) {
    REMOTE_URL = buildRemoteUrlFromRaw(ipVal);
    print("KVS hubitat_sdm_ip found; REMOTE_URL set to " + REMOTE_URL);
  } else {
    print("KVS hubitat_sdm_ip empty; using REMOTE_URL=" + REMOTE_URL);
  }
  // Do not let the native callback retain the response object or string.
  ipVal = null;
  res = null;
  msg = null;
  scheduleSettingsAdvance();
}

// Try to read hub IP from Shelly KVS; on success replace REMOTE_URL. Graceful
// no-ops if KVS isn't available. The named callback avoids allocating a new
// closure on every periodic refresh.
function fetchRemoteUrlFromKVS() {
  if (typeof Shelly.call !== "function") {
    print("Shelly.call() not available; using REMOTE_URL=" + REMOTE_URL);
    scheduleSettingsAdvance();
    return;
  }
  try {
    Shelly.call("KVS.Get", { key: HUBITAT_KVS_KEY }, onRemoteUrlFetched);
  } catch (e) {
    print(
      "KVS.Get invocation failed; using REMOTE_URL=" +
        REMOTE_URL +
        " (" +
        e +
        ")",
    );
    scheduleSettingsAdvance();
  }
}

// Read a non-negative numeric KVS value. Missing or malformed values leave the
// corresponding in-memory default unchanged.
function onNumberFromKVSResponse(res, err, msg) {
  let value = null;
  let raw = null;
  if (err === 0 && res) {
    if (typeof res.value === "string" || typeof res.value === "number") raw = res.value;
    else if (res.result && res.result.value !== undefined) raw = res.result.value;
    if (raw !== null) {
      let parsed = parseFloat(raw);
      if (parsed === parsed && parsed !== Infinity && parsed !== -Infinity && parsed >= 0) value = parsed;
    }
  }
  raw = null;
  res = null;
  msg = null;
  onReportSettingFetched(value);
}

function fetchNumberFromKVS(key) {
  try {
    Shelly.call("KVS.Get", { key: key }, onNumberFromKVSResponse);
  } catch (e) {
    print("KVS numeric setting fetch failed for " + key + ": " + e);
    onReportSettingFetched(null);
  }
}

// Apply one numeric setting without retaining the KVS response object.
function applyReportSetting(name, value) {
  if (value === null) return;
  if (name === "interval") {
    let interval = Math.floor(value);
    if (interval > 0) {
      if (interval !== REPORT_INTERVAL) {
        print("Report interval changed: " + JSON.stringify(REPORT_INTERVAL) + "s -> " + JSON.stringify(interval) + "s");
      }
      REPORT_INTERVAL = interval;
    }
  } else if (name === "voltage") {
    THRESH_V = value;
  } else if (name === "current") {
    THRESH_C = value;
  } else if (name === "power") {
    THRESH_P = value;
  } else if (name === "energy") {
    THRESH_E = value;
  } else if (name === "frequency") {
    THRESH_F = value;
  }
}

function setSettingsRefreshDelay() {
  settingsCyclesRemaining = Math.max(1, Math.ceil(SETTINGS_REFRESH_INTERVAL_SECS / REPORT_INTERVAL));
}

function setStatusPollDelay(seconds) {
  statusPollCyclesRemaining = Math.max(1, Math.ceil(seconds / REPORT_INTERVAL));
}

function finishSettingsRefresh() {
  settingsRefreshInFlight = false;
  setSettingsRefreshDelay();
  let cb = settingsRefreshCallback;
  settingsRefreshCallback = null;
  if (typeof cb === "function") cb();
}

function onReportSettingFetched(value) {
  let setting = REPORT_SETTINGS[settingsRefreshIndex - 1];
  if (setting) applyReportSetting(setting[1], value);
  scheduleSettingsAdvance();
}

// Fetch KVS settings serially. Six concurrent KVS.Get calls were a startup
// and runtime resource spike, especially when another script was active.
function fetchNextReportSetting() {
  if (settingsRefreshIndex >= REPORT_SETTINGS.length) {
    finishSettingsRefresh();
    return;
  }
  let setting = REPORT_SETTINGS[settingsRefreshIndex];
  settingsRefreshIndex++;
  fetchNumberFromKVS(setting[0]);
}

// Refresh all reporting settings at most once per configured refresh window.
// If a caller arrives while a refresh is active it waits for that same refresh.
function fetchReportSettingsFromKVS(cb, force) {
  if (typeof cb === "function") settingsRefreshCallback = cb;
  if (settingsRefreshInFlight) return;
  if (!force && settingsCyclesRemaining > 0) {
    settingsCyclesRemaining--;
    if (typeof cb === "function") {
      let callback = settingsRefreshCallback;
      settingsRefreshCallback = null;
      callback();
    }
    return;
  }
  settingsRefreshInFlight = true;
  settingsRefreshIndex = 0;
  fetchRemoteUrlFromKVS();
}

// Schedule the next one-shot report timer using the current REPORT_INTERVAL
function scheduleNextReport() {
  if (reportTimerHandle !== null) Timer.clear(reportTimerHandle);
  reportTimerHandle = Timer.set(REPORT_INTERVAL * 1000, false, sendReport);
}

// Use bounded sum/count accumulators instead of retaining every sample.
// This is important on multi-channel devices where status events can arrive
// frequently and the Shelly JavaScript heap is shared by all scripts.
function addMetric(data, prefix, value) {
  data[prefix + "Sum"] += value;
  data[prefix + "Count"]++;
}
function averageMetric(data, prefix) {
  let count = data[prefix + "Count"];
  return count === 0 ? null : data[prefix + "Sum"] / count;
}
function resetMetric(data, prefix) {
  data[prefix + "Sum"] = 0;
  data[prefix + "Count"] = 0;
}

function newPowerData() {
  return {
    vSum: 0, vCount: 0, cSum: 0, cCount: 0,
    pSum: 0, pCount: 0, fSum: 0, fCount: 0,
    e: null,
    lastV: null,
    lastC: null,
    lastP: null,
    lastF: null,
    sentV: null,
    sentC: null,
    sentP: null,
    sentF: null,
    sentE: null,
  };
}

// Field-specific rounding: voltage=1dp, current=2dp, power=0dp, energy=0dp, freq=1dp
function roundV(val) { return val === null ? null : Math.round(val * 10) / 10; }
function roundC(val) { return val === null ? null : Math.round(val * 100) / 100; }
function roundP(val) { return val === null ? null : Math.round(val); }
function roundE(val) { return val === null ? null : Math.round(val * 10) / 10; }
function roundF(val) { return val === null ? null : Math.round(val * 10) / 10; }

// Get or create a component accumulator entry
function getOrCreateComp(key, type, id) {
  if (comps[key]) return comps[key];
  if (compKeys.length >= MAX_TRACKED_COMPONENTS) {
    print("Power monitor component limit reached; ignoring " + key);
    return null;
  }
  let c;
  if (type === "em") {
    c = {
      type: "em",
      id: id,
      a: newPowerData(),
      b: newPowerData(),
      c: newPowerData(),
    };
  } else {
    c = newPowerData();
    c.type = type;
    c.id = id;
  }
  comps[key] = c;
  compKeys.push(key);
  return c;
}

function hasNumber(value) {
  return typeof value === "number" && value === value && value !== Infinity && value !== -Infinity;
}

function normalizeComponentId(value) {
  if (!hasNumber(value) || Math.floor(value) !== value || value < 0 || value > MAX_COMPONENT_ID) return null;
  return value;
}

function parseComponentIdText(text) {
  if (typeof text !== "string" || text.length === 0) return null;
  let parsed = parseInt(text, 10);
  if ("" + parsed !== text) return null;
  return normalizeComponentId(parsed);
}

function isSupportedStatusType(type) {
  return type === "switch" || type === "pm1" || type === "cover" ||
    type === "em" || type === "em1" || type === "emdata" || type === "em1data";
}

function hasSinglePowerData(d) {
  return hasNumber(d.voltage) || hasNumber(d.current) || hasNumber(d.apower) ||
    hasNumber(d.freq) || (d.aenergy && hasNumber(d.aenergy.total));
}

function hasEmPowerData(d) {
  return hasNumber(d.a_voltage) || hasNumber(d.a_current) || hasNumber(d.a_act_power) || hasNumber(d.a_freq) ||
    hasNumber(d.b_voltage) || hasNumber(d.b_current) || hasNumber(d.b_act_power) || hasNumber(d.b_freq) ||
    hasNumber(d.c_voltage) || hasNumber(d.c_current) || hasNumber(d.c_act_power) || hasNumber(d.c_freq);
}

function pushPowerComponentStatus(type, key, id, d, isSeed) {
  if (!d || typeof d !== "object") return false;
  let entry = null;
  let accepted = false;

  if (type === "em") {
    if (!hasEmPowerData(d)) return false;
    entry = getOrCreateComp(key, "em", id);
    if (!entry) return false;
    let phases = PHASES;
    for (let i = 0; i < phases.length; i++) {
      let p = phases[i];
      let ph = entry[p];
      if (hasNumber(d[p + "_voltage"])) { addMetric(ph, "v", d[p + "_voltage"]); if (isSeed) ph.lastV = d[p + "_voltage"]; accepted = true; }
      if (hasNumber(d[p + "_current"])) { addMetric(ph, "c", d[p + "_current"]); if (isSeed) ph.lastC = d[p + "_current"]; accepted = true; }
      if (hasNumber(d[p + "_act_power"])) { addMetric(ph, "p", d[p + "_act_power"]); if (isSeed) ph.lastP = d[p + "_act_power"]; accepted = true; }
      if (hasNumber(d[p + "_freq"])) { addMetric(ph, "f", d[p + "_freq"]); if (isSeed) ph.lastF = d[p + "_freq"]; accepted = true; }
    }
    return accepted;
  }

  if (type === "em1") {
    if (!hasNumber(d.voltage) && !hasNumber(d.current) && !hasNumber(d.act_power) && !hasNumber(d.freq)) return false;
    entry = getOrCreateComp(key, "em1", id);
    if (!entry) return false;
    if (hasNumber(d.voltage)) { addMetric(entry, "v", d.voltage); if (isSeed) entry.lastV = d.voltage; accepted = true; }
    if (hasNumber(d.current)) { addMetric(entry, "c", d.current); if (isSeed) entry.lastC = d.current; accepted = true; }
    if (hasNumber(d.act_power)) { addMetric(entry, "p", d.act_power); if (isSeed) entry.lastP = d.act_power; accepted = true; }
    if (hasNumber(d.freq)) { addMetric(entry, "f", d.freq); if (isSeed) entry.lastF = d.freq; accepted = true; }
    return accepted;
  }

  if (!hasSinglePowerData(d)) return false;
  entry = getOrCreateComp(key, type, id);
  if (!entry) return false;
  if (hasNumber(d.voltage)) { addMetric(entry, "v", d.voltage); if (isSeed) entry.lastV = d.voltage; accepted = true; }
  if (hasNumber(d.current)) { addMetric(entry, "c", d.current); if (isSeed) entry.lastC = d.current; accepted = true; }
  if (hasNumber(d.apower)) { addMetric(entry, "p", d.apower); if (isSeed) entry.lastP = d.apower; accepted = true; }
  if (hasNumber(d.freq)) { addMetric(entry, "f", d.freq); if (isSeed) entry.lastF = d.freq; accepted = true; }
  if (d.aenergy && hasNumber(d.aenergy.total)) { entry.e = d.aenergy.total; accepted = true; }
  return accepted;
}

function pushEnergyStatus(type, id, d) {
  if (!d || typeof d !== "object") return false;
  if (type === "emdata") {
    if (!hasNumber(d.a_total_act_energy) && !hasNumber(d.b_total_act_energy) && !hasNumber(d.c_total_act_energy)) return false;
    let entry = getOrCreateComp("em:" + id, "em", id);
    if (!entry) return false;
    if (hasNumber(d.a_total_act_energy)) entry.a.e = d.a_total_act_energy;
    if (hasNumber(d.b_total_act_energy)) entry.b.e = d.b_total_act_energy;
    if (hasNumber(d.c_total_act_energy)) entry.c.e = d.c_total_act_energy;
    return true;
  }
  if (type === "em1data") {
    if (!hasNumber(d.total_act_energy)) return false;
    let entry = getOrCreateComp("em1:" + id, "em1", id);
    if (!entry) return false;
    entry.e = d.total_act_energy;
    return true;
  }
  return false;
}

// Status handler: collect power data from status change events. Only validated
// numeric fields are copied; the event object itself is never retained.
function onStatus(ev) {
  if (!ev || typeof ev.component !== "string") return;
  let d = ev.delta;
  if (!d || typeof d !== "object") return;

  let comp = ev.component;
  let colonIdx = comp.indexOf(":");
  let type = colonIdx >= 0 ? comp.substring(0, colonIdx) : comp;
  if (!isSupportedStatusType(type)) return;

  let rawId = ev.id;
  if (rawId === undefined || rawId === null) {
    rawId = colonIdx >= 0 ? parseComponentIdText(comp.substring(colonIdx + 1)) : 0;
    if (rawId === null) return;
  }
  let id = normalizeComponentId(rawId);
  if (id === null) return;

  let accepted = false;
  if (type === "emdata" || type === "em1data") accepted = pushEnergyStatus(type, id, d);
  else accepted = pushPowerComponentStatus(type, type + ":" + id, id, d, false);
  if (accepted) noteStatusData();
}

// Build a small report object with normalized power monitoring params.
// The object is queued and sent by drainReportQueue(), which limits the device
// to one outbound HTTP request at a time.
function sendPostReport(compId, compType, phase, data) {
  let v = roundV(averageMetric(data, "v"));
  let cur = roundC(averageMetric(data, "c"));
  let p = roundP(averageMetric(data, "p"));
  let f = roundF(averageMetric(data, "f"));
  let e = roundE(data.e);

  // Fall back to last-known values when no deltas were received
  if (v === null && data.lastV !== null) v = roundV(data.lastV);
  if (cur === null && data.lastC !== null) cur = roundC(data.lastC);
  if (p === null && data.lastP !== null) p = roundP(data.lastP);
  if (f === null && data.lastF !== null) f = roundF(data.lastF);

  if (v === null && cur === null && p === null && f === null && e === null) {
    return;
  }

  // Check for significant change vs last-reported values. A zero threshold
  // intentionally makes an available value match on every reporting cycle.
  let changed = false;
  if (v !== null && (data.sentV === null || Math.abs(v - data.sentV) >= THRESH_V)) changed = true;
  if (cur !== null && (data.sentC === null || Math.abs(cur - data.sentC) >= THRESH_C)) changed = true;
  if (p !== null && (data.sentP === null || Math.abs(p - data.sentP) >= THRESH_P)) changed = true;
  if (e !== null && (data.sentE === null || Math.abs(e - data.sentE) >= THRESH_E)) changed = true;
  if (f !== null && (data.sentF === null || Math.abs(f - data.sentF) >= THRESH_F)) changed = true;
  if (!changed) return;

  let body = { dst: "powermon", cid: compId, comp: compType };
  if (phase) body.phase = phase;
  if (v !== null) body.voltage = v;
  if (cur !== null) body.current = cur;
  if (p !== null) body.apower = p;
  if (e !== null) body.aenergy = e;
  if (f !== null) body.freq = f;

  // Update last-known and last-sent tracking when the report is queued. If
  // delivery fails, mark these values unsent so the next cycle retries them.
  if (v !== null) { data.lastV = v; data.sentV = v; }
  if (cur !== null) { data.lastC = cur; data.sentC = cur; }
  if (p !== null) { data.lastP = p; data.sentP = p; }
  if (f !== null) { data.lastF = f; data.sentF = f; }
  if (e !== null) data.sentE = e;

  let reportKey = compType + ":" + compId + (phase ? ":" + phase : "");
  for (let i = 0; i < reportQueue.length; i++) {
    if (reportQueue[i].key === reportKey) {
      let serialized = JSON.stringify(body);
      body = null;
      if (serialized.length > MAX_REPORT_BYTES) { markReportUnsent(reportQueue[i]); droppedReports++; return; }
      let oldReport = reportQueue[i];
      reportQueueBytes += serialized.length - (oldReport.bytes || 0);
      reportQueue[i] = { key: reportKey, compId: compId, compType: compType, phase: phase, body: serialized, bytes: serialized.length, data: data };
      releaseReport(oldReport);
      replacedQueuedReports++;
      return;
    }
  }

  let serializedBody = JSON.stringify(body);
  body = null;
  if (serializedBody.length > MAX_REPORT_BYTES) { markDataUnsent(data); droppedReports++; return; }
  while (reportQueue.length >= MAX_PENDING_REPORTS || reportQueueBytes + serializedBody.length > MAX_PENDING_BYTES) {
    let dropped = dequeueReport();
    if (!dropped) break;
    markReportUnsent(dropped);
    releaseReport(dropped);
    droppedReports++;
  }
  reportQueue.push({ key: reportKey, compId: compId, compType: compType, phase: phase, body: serializedBody, bytes: serializedBody.length, data: data });
  reportQueueBytes += serializedBody.length;
}

function markDataUnsent(data) {
  if (!data) return;
  data.sentV = null;
  data.sentC = null;
  data.sentP = null;
  data.sentF = null;
  data.sentE = null;
}

function markReportUnsent(report) {
  if (report) markDataUnsent(report.data);
}

function releaseReport(report) {
  if (!report) return;
  report.key = null;
  report.compId = null;
  report.compType = null;
  report.phase = null;
  report.body = null;
  report.bytes = 0;
  report.data = null;
}

function scheduleReportDrain() {
  if (reportDrainTimerHandle !== null) return;
  reportDrainTimerHandle = Timer.set(REPORT_DRAIN_DELAY_MS, false, drainReportQueueAfterResponse);
}

function drainReportQueueAfterResponse() {
  reportDrainTimerHandle = null;
  drainReportQueue();
}

function onReportResponse(result, error_code, error_message, token) {
  if (!reportInFlight || token !== reportInFlightToken) {
    print("Ignoring late power report callback");
    result = null;
    error_message = null;
    return;
  }
  let data = reportInFlightData;
  reportInFlight = false;
  reportInFlightData = null;
  reportInFlightBytes = 0;

  if (error_code !== 0) {
    failedReports++;
    markDataUnsent(data);
    print("Power report HTTP error:", error_code, error_message);
  } else {
    totalReportsSent++;
  }
  // Release callback arguments before the next request is created. In
  // particular, do not let a native callback registry retain the response or
  // a report object through the next drain operation.
  data = null;
  result = null;
  error_message = null;
  scheduleReportDrain();
}

// Send one report at a time. The request body is created only immediately
// before dispatch and is released when the callback returns.
function drainReportQueue() {
  if (!reportCycleInProgress || reportInFlight) return;
  if (reportQueue.length === 0) {
    finishReportCycle();
    return;
  }

  let report = dequeueReport();
  let reportData = report.data;
  let reportBody = report.body;
  let reportBytes = report.bytes || 0;
  reportInFlight = true;
  let token = reportInFlightToken + 1;
  reportInFlightToken = token;
  let url = REMOTE_URL + "/webhook/powermon/" + report.compId;
  reportInFlightData = reportData;
  reportInFlightBytes = reportBytes;
  releaseReport(report);
  report = null;
  try {
    Shelly.call(
      "HTTP.POST",
      { url: url, body: reportBody, content_type: "application/json", timeout: REPORT_REQUEST_TIMEOUT_SECS },
      onReportResponse,
      token,
    );
  } catch (e) {
    reportBody = null;
    url = null;
    reportData = null;
    onReportResponse(null, -1, e, token);
    return;
  }
  reportBody = null;
  url = null;
  reportData = null;
  token = null;
}

// Push fresh status readings into bounded accumulators for averaging.
// Unlike seedFromStatus(), does NOT set lastV/lastC/etc. -- those are
// updated by sendPostReport() after computing the cycle average.
function pushStatusReadings(res, seed) {
  if (!res || typeof res !== "object") return;
  let isSeed = seed === true;
  let prefixes = REPORT_PREFIXES;
  for (let p = 0; p < prefixes.length; p++) {
    for (let id = 0; id <= MAX_COMPONENT_ID; id++) {
      let key = prefixes[p] + ":" + id;
      pushPowerComponentStatus(prefixes[p], key, id, res[key], isSeed);
    }
  }

  // Update energy counters from emdata/em1data
  for (let id = 0; id <= MAX_COMPONENT_ID; id++) {
    pushEnergyStatus("emdata", id, res["emdata:" + id]);
    pushEnergyStatus("em1data", id, res["em1data:" + id]);
  }
  res = null;
}

function pushSingleStatusReading(key, status, seed) {
  if (typeof key !== "string" || !status || typeof status !== "object") return;
  let colonIdx = key.indexOf(":");
  if (colonIdx < 0) return;
  let type = key.substring(0, colonIdx);
  if (!isSupportedStatusType(type)) return;
  let id = parseComponentIdText(key.substring(colonIdx + 1));
  if (id === null) return;
  if (type === "emdata" || type === "em1data") pushEnergyStatus(type, id, status);
  else pushPowerComponentStatus(type, type + ":" + id, id, status, seed === true);
}

function readComponentStatus(key, seed) {
  let status = Shelly.getComponentStatus(key);
  if (status) pushSingleStatusReading(key, status, seed);
  status = null;
}

function seedFromKnownComponentStatuses() {
  for (let p = 0; p < REPORT_PREFIXES.length; p++) {
    for (let id = 0; id <= MAX_COMPONENT_ID; id++) {
      readComponentStatus(REPORT_PREFIXES[p] + ":" + id, true);
    }
  }
  for (let id = 0; id <= MAX_COMPONENT_ID; id++) {
    readComponentStatus("emdata:" + id, true);
    readComponentStatus("em1data:" + id, true);
  }
}

function collectTrackedComponentStatuses() {
  for (let i = 0; i < compKeys.length; i++) {
    let key = compKeys[i];
    readComponentStatus(key, false);
    let sepKey = null;
    if (key.indexOf("em:") === 0) sepKey = "emdata:" + key.substring(3);
    else if (key.indexOf("em1:") === 0) sepKey = "em1data:" + key.substring(4);
    if (sepKey) readComponentStatus(sepKey, false);
  }
}

// Send all accumulated reports for every tracked component, then reset samples
function sendAllReports() {
  for (let i = 0; i < compKeys.length; i++) {
    let entry = comps[compKeys[i]];

    if (entry.type === "em") {
      let phases = PHASES;
      for (let j = 0; j < phases.length; j++) {
        let ph = entry[phases[j]];
        sendPostReport(entry.id, "em", phases[j], ph);
        resetMetric(ph, "v");
        resetMetric(ph, "c");
        resetMetric(ph, "p");
        resetMetric(ph, "f");
      }
    } else {
      sendPostReport(entry.id, entry.type, null, entry);
      resetMetric(entry, "v");
      resetMetric(entry, "c");
      resetMetric(entry, "p");
      resetMetric(entry, "f");
    }
  }
}

function finishReportCycle() {
  if (!reportCycleInProgress || reportInFlight || reportQueue.length > 0 || statusPollInFlight) return;
  reportCycleInProgress = false;
  printDiagnostics("cycle complete");
  // This decrements an in-memory countdown and performs the serialized KVS
  // refresh only when the 15-minute refresh window expires.
  fetchReportSettingsFromKVS(scheduleNextReport, false);
}

function completeReportWithCurrentSamples() {
  sendAllReports();
  drainReportQueue();
}

function onReportStatusResponse(result, error_code, error_message) {
  statusPollInFlight = false;
  if (error_code === 0 && result) {
    try {
      // Extract only the power fields needed by the accumulators. The full
      // GetStatus response becomes unreachable when this callback returns.
      pushStatusReadings(result, false);
      setStatusPollDelay(STATUS_POLL_INTERVAL_SECS);
    } catch (e) {
      failedStatusPolls++;
      setStatusPollDelay(STATUS_POLL_RETRY_SECS);
      print("GetStatus processing failed: " + e);
    }
  } else {
    failedStatusPolls++;
    setStatusPollDelay(STATUS_POLL_RETRY_SECS);
    print("GetStatus error:", error_code, error_message);
  }
  // The full status object can be large on devices with many components. Drop
  // callback references before creating any report payloads.
  result = null;
  error_message = null;
  completeReportWithCurrentSamples();
}

function startReportStatusPoll() {
  statusPollInFlight = true;
  totalStatusPolls++;
  let pollFailed = false;
  try {
    if (typeof Shelly.getComponentStatus !== "function") {
      Shelly.call("Shelly.GetStatus", {}, onReportStatusResponse);
      return;
    }
    // Read only tracked power components; avoid materializing full device status.
    collectTrackedComponentStatuses();
  } catch (e) {
    pollFailed = true;
    failedStatusPolls++;
    setStatusPollDelay(STATUS_POLL_RETRY_SECS);
    print("Component status poll failed: " + e);
  }
  statusPollInFlight = false;
  if (!pollFailed) setStatusPollDelay(STATUS_POLL_INTERVAL_SECS);
  completeReportWithCurrentSamples();
}

// Timer callback: use status events for normal sampling and poll only as a
// periodic fallback. This avoids allocating and processing a large status
// response every reporting interval.
function sendReport() {
  reportTimerHandle = null;
  if (reportCycleInProgress) {
    print("Power report cycle already in progress; ignoring duplicate timer");
    return;
  }
  reportCycleInProgress = true;

  let hadStatusEvents = statusEventsSinceReport;
  statusEventsSinceReport = false;
  if (hadStatusEvents) {
    completeReportWithCurrentSamples();
    return;
  }

  if (statusPollCyclesRemaining > 0) {
    statusPollCyclesRemaining--;
    completeReportWithCurrentSamples();
    return;
  }

  startReportStatusPoll();
}

function finishStartup() {
  printDiagnostics("startup");
  print(
    "Power monitor started: version=" + POWERMONITOR_SCRIPT_VERSION +
      " interval=" + JSON.stringify(REPORT_INTERVAL) +
      "s statusPoll=" + JSON.stringify(STATUS_POLL_INTERVAL_SECS) +
      "s maxPending=" + JSON.stringify(MAX_PENDING_REPORTS),
  );
  scheduleNextReport();
}

function onSeedStatusResponse(result, error_code, error_message) {
  if (error_code !== 0 || !result) {
    failedStatusPolls++;
    setStatusPollDelay(STATUS_POLL_RETRY_SECS);
    print("seedFromStatus: GetStatus failed:", error_code, error_message);
  } else {
    try {
      pushStatusReadings(result, true);
      setStatusPollDelay(STATUS_POLL_INTERVAL_SECS);
    } catch (e) {
      failedStatusPolls++;
      setStatusPollDelay(STATUS_POLL_RETRY_SECS);
      print("seedFromStatus: response processing failed: " + e);
    }
  }
  result = null;
  error_message = null;
  finishStartup();
}

// Seed accumulators with one startup status read. When the synchronous API is
// available, query only the known component keys so a large full-device status
// object is never materialized. Older firmware uses Shelly.GetStatus instead.
function seedFromStatus() {
  totalStatusPolls++;
  try {
    if (typeof Shelly.getComponentStatus === "function") {
      seedFromKnownComponentStatuses();
      setStatusPollDelay(STATUS_POLL_INTERVAL_SECS);
      finishStartup();
      return;
    }
    Shelly.call("Shelly.GetStatus", {}, onSeedStatusResponse);
  } catch (e) {
    failedStatusPolls++;
    setStatusPollDelay(STATUS_POLL_RETRY_SECS);
    print("seedFromStatus: GetStatus invocation failed: " + e);
    finishStartup();
  }
}

function startSeedAfterSettings() {
  seedFromStatus();
}

function startPowerMonitor() {
  if (powerMonitorStarted) return;
  powerMonitorStarted = true;
  statusHandlerHandle = Shelly.addStatusHandler(onStatus);
  // Startup work is deliberately serialized: URL, settings, then one status
  // seed. This prevents the old burst of seven concurrent RPC calls.
  fetchReportSettingsFromKVS(startSeedAfterSettings, true);
}

startPowerMonitor();
