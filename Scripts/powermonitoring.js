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
let POWERMONITOR_SCRIPT_VERSION = "2.1.1";
let DEFAULT_REPORT_INTERVAL = 60; // Fallback if KVS lookup fails
let REPORT_INTERVAL = DEFAULT_REPORT_INTERVAL;
let REPORT_INTERVAL_KVS_KEY = "hubitat_sdm_pm_ri"; // KVS key for dynamic report interval (seconds)
let SETTINGS_REFRESH_INTERVAL_SECS = 900; // Refresh KVS settings at most every 15 minutes
let STATUS_POLL_INTERVAL_SECS = 900; // GetStatus is a fallback, not the normal sampling path
let STATUS_POLL_RETRY_SECS = 120; // Retry sooner after a failed startup/status poll
let REPORT_REQUEST_TIMEOUT_MS = 15000;
let MAX_PENDING_REPORTS = 16;

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
let settingsCyclesRemaining = 0;
let statusPollCyclesRemaining = 0;
let statusEventsSinceReport = false;
let reportQueue = [];
let reportInFlight = false;
let reportInFlightToken = 0;
let reportInFlightTimer = null;

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
      " reportInFlight=" + (reportInFlight ? 1 : 0) +
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
  for (let i = 1; i < reportQueue.length; i++) {
    reportQueue[i - 1] = reportQueue[i];
  }
  reportQueue.length = reportQueue.length - 1;
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

// Try to read hub IP from Shelly KVS; on success replace REMOTE_URL. Graceful
// no-ops if KVS isn't available. The callback lets startup and periodic
// settings refreshes serialize this request with the other KVS reads.
function fetchRemoteUrlFromKVS(cb) {
  if (typeof Shelly.call !== "function") {
    print("Shelly.call() not available; using REMOTE_URL=" + REMOTE_URL);
    if (typeof cb === "function") cb();
    return;
  }
  try {
    Shelly.call("KVS.Get", { key: HUBITAT_KVS_KEY }, function (res, err, msg) {
      if (err !== 0 || res === undefined || res === null) {
        print("KVS.Get did not return a value; using REMOTE_URL=" + REMOTE_URL);
        if (typeof cb === "function") cb();
        return;
      }
      // attempt to extract value from common response shapes
      let ipVal = null;
      if (typeof res.value === "string") ipVal = res.value;
      else if (res.result && typeof res.result.value === "string")
        ipVal = res.result.value;
      else if (typeof res === "string") ipVal = res;
      if (ipVal) {
        REMOTE_URL = buildRemoteUrlFromRaw(ipVal);
        print("KVS hubitat_sdm_ip found; REMOTE_URL set to " + REMOTE_URL);
      } else {
        print("KVS hubitat_sdm_ip empty; using REMOTE_URL=" + REMOTE_URL);
      }
      if (typeof cb === "function") cb();
    });
  } catch (e) {
    print(
      "KVS.Get invocation failed; using REMOTE_URL=" +
        REMOTE_URL +
        " (" +
        e +
        ")",
    );
    if (typeof cb === "function") cb();
  }
}

// Read a non-negative numeric KVS value. Missing or malformed values leave the
// corresponding in-memory default unchanged.
function fetchNumberFromKVS(key, cb) {
  try {
    Shelly.call("KVS.Get", { key: key }, function (res, err, msg) {
      let value = null;
      if (err === 0 && res) {
        let raw = null;
        if (typeof res.value === "string" || typeof res.value === "number") raw = res.value;
        else if (res.result && res.result.value !== undefined) raw = res.result.value;
        if (raw !== null) {
          let parsed = parseFloat(raw);
          if (!isNaN(parsed) && parsed >= 0) value = parsed;
        }
      }
      if (typeof cb === "function") cb(value);
    });
  } catch (e) {
    print("KVS numeric setting fetch failed for " + key + ": " + e);
    if (typeof cb === "function") cb(null);
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
  fetchNextReportSetting();
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
  fetchNumberFromKVS(setting[0], onReportSettingFetched);
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
  fetchRemoteUrlFromKVS(fetchNextReportSetting);
}

// Schedule the next one-shot report timer using the current REPORT_INTERVAL
function scheduleNextReport() {
  if (reportTimerHandle !== null) Timer.clear(reportTimerHandle);
  reportTimerHandle = Timer.set(REPORT_INTERVAL * 1000, false, sendReport);
}

// Use bounded sum/count accumulators instead of retaining every sample.
// This is important on multi-channel devices where status events can arrive
// frequently and the Shelly JavaScript heap is shared by all scripts.
function newSample() { return { sum: 0, count: 0 }; }
function addSample(sample, value) {
  sample.sum += value;
  sample.count++;
}
function average(sample) {
  return sample.count === 0 ? null : sample.sum / sample.count;
}
function resetSample(sample) {
  sample.sum = 0;
  sample.count = 0;
}

function newPowerData() {
  return {
    vs: newSample(),
    cs: newSample(),
    ps: newSample(),
    fs: newSample(),
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

// Status handler: collect power data from status change events
function onStatus(ev) {
  let d = ev.delta;
  if (d === undefined || d === null) return;

  let comp = ev.component;
  if (comp === undefined || comp === null) return;

  let colonIdx = comp.indexOf(":");
  let type = colonIdx >= 0 ? comp.substring(0, colonIdx) : comp;
  let id = ev.id !== undefined ? ev.id : 0;

  // emdata: energy counters for 3-phase em components
  if (type === "emdata") {
    let emKey = "em:" + JSON.stringify(id);
    let entry = getOrCreateComp(emKey, "em", id);
    if (typeof d.a_total_act_energy === "number") entry.a.e = d.a_total_act_energy;
    if (typeof d.b_total_act_energy === "number") entry.b.e = d.b_total_act_energy;
    if (typeof d.c_total_act_energy === "number") entry.c.e = d.c_total_act_energy;
    noteStatusData();
    return;
  }

  // em1data: energy counter for single-phase em1 components
  if (type === "em1data") {
    let em1Key = "em1:" + JSON.stringify(id);
    let entry = getOrCreateComp(em1Key, "em1", id);
    if (typeof d.total_act_energy === "number") entry.e = d.total_act_energy;
    noteStatusData();
    return;
  }

  // em: 3-phase energy meter (a_voltage, a_current, a_act_power, a_freq, etc.)
  if (type === "em") {
    let entry = getOrCreateComp(comp, "em", id);
    let phases = ["a", "b", "c"];
    for (let i = 0; i < phases.length; i++) {
      let p = phases[i];
      let ph = entry[p];
      if (typeof d[p + "_voltage"] === "number") addSample(ph.vs, d[p + "_voltage"]);
      if (typeof d[p + "_current"] === "number") addSample(ph.cs, d[p + "_current"]);
      if (typeof d[p + "_act_power"] === "number") addSample(ph.ps, d[p + "_act_power"]);
      if (typeof d[p + "_freq"] === "number") addSample(ph.fs, d[p + "_freq"]);
    }
    noteStatusData();
    return;
  }

  // em1: single-phase energy meter (voltage, current, act_power, freq)
  if (type === "em1") {
    let entry = getOrCreateComp(comp, "em1", id);
    if (typeof d.voltage === "number") addSample(entry.vs, d.voltage);
    if (typeof d.current === "number") addSample(entry.cs, d.current);
    if (typeof d.act_power === "number") addSample(entry.ps, d.act_power);
    if (typeof d.freq === "number") addSample(entry.fs, d.freq);
    noteStatusData();
    return;
  }

  // pm1, switch, cover: single-phase power monitoring (voltage, current, apower, freq)
  if (type !== "switch" && type !== "pm1" && type !== "cover") return;

  let entry = getOrCreateComp(comp, type, id);
  if (typeof d.voltage === "number") addSample(entry.vs, d.voltage);
  if (typeof d.current === "number") addSample(entry.cs, d.current);
  if (typeof d.apower === "number") addSample(entry.ps, d.apower);
  if (typeof d.freq === "number") addSample(entry.fs, d.freq);
  if (d.aenergy !== undefined && d.aenergy !== null) {
    if (typeof d.aenergy.total === "number") {
      entry.e = d.aenergy.total;
    }
  }
  noteStatusData();
}

// Build a small report object with normalized power monitoring params.
// The object is queued and sent by drainReportQueue(), which limits the device
// to one outbound HTTP request at a time.
function sendPostReport(compId, compType, phase, data) {
  let v = roundV(average(data.vs));
  let cur = roundC(average(data.cs));
  let p = roundP(average(data.ps));
  let f = roundF(average(data.fs));
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

  let reportKey = compType + ":" + JSON.stringify(compId) + (phase ? ":" + phase : "");
  for (let i = 0; i < reportQueue.length; i++) {
    if (reportQueue[i].key === reportKey) {
      reportQueue[i] = { key: reportKey, compId: compId, compType: compType, phase: phase, body: body, data: data };
      replacedQueuedReports++;
      return;
    }
  }

  if (reportQueue.length >= MAX_PENDING_REPORTS) {
    let dropped = dequeueReport();
    if (dropped && dropped.data) {
      dropped.data.sentV = null;
      dropped.data.sentC = null;
      dropped.data.sentP = null;
      dropped.data.sentF = null;
      dropped.data.sentE = null;
    }
    droppedReports++;
    print("Power report queue full; dropping oldest report");
  }
  reportQueue.push({ key: reportKey, compId: compId, compType: compType, phase: phase, body: body, data: data });
}

function markReportUnsent(report) {
  if (!report || !report.data) return;
  report.data.sentV = null;
  report.data.sentC = null;
  report.data.sentP = null;
  report.data.sentF = null;
  report.data.sentE = null;
}

function onReportResponse(token, report, result, error_code, error_message) {
  if (!reportInFlight || token !== reportInFlightToken) {
    print("Ignoring late power report callback");
    return;
  }
  if (reportInFlightTimer !== null) Timer.clear(reportInFlightTimer);
  reportInFlightTimer = null;
  reportInFlight = false;

  if (error_code !== 0) {
    failedReports++;
    markReportUnsent(report);
    print("Power report HTTP error:", error_code, error_message);
  } else {
    totalReportsSent++;
    print("Power report sent:", report.key);
  }
  drainReportQueue();
}

function onReportTimeout(token, report) {
  if (!reportInFlight || token !== reportInFlightToken) return;
  reportInFlightTimer = null;
  reportInFlight = false;
  failedReports++;
  markReportUnsent(report);
  for (let i = 0; i < reportQueue.length; i++) markReportUnsent(reportQueue[i]);
  reportQueue = [];
  print("Power report timed out; abandoning pending reports until the next cycle");
  finishReportCycle();
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
  reportInFlight = true;
  let token = reportInFlightToken + 1;
  reportInFlightToken = token;
  let url = REMOTE_URL + "/webhook/powermon/" + JSON.stringify(report.compId);
  try {
    reportInFlightTimer = Timer.set(REPORT_REQUEST_TIMEOUT_MS, false, function () {
      onReportTimeout(token, report);
    });
    Shelly.call(
      "HTTP.POST",
      { url: url, body: JSON.stringify(report.body), content_type: "application/json" },
      function (result, error_code, error_message) {
        onReportResponse(token, report, result, error_code, error_message);
      },
    );
    print("Power report queued:", report.key);
  } catch (e) {
    onReportResponse(token, report, null, -1, e);
  }
}

// Push fresh status readings into bounded accumulators for averaging.
// Unlike seedFromStatus(), does NOT set lastV/lastC/etc. -- those are
// updated by sendPostReport() after computing the cycle average.
function pushStatusReadings(res, seed) {
  let isSeed = seed === true;
  let prefixes = ["switch", "pm1", "cover", "em", "em1"];
  for (let p = 0; p < prefixes.length; p++) {
    for (let id = 0; id < 8; id++) {
      let key = prefixes[p] + ":" + JSON.stringify(id);
      let s = res[key];
      if (!s) continue;

      if (prefixes[p] === "em") {
        let entry = getOrCreateComp(key, "em", id);
        let phases = ["a", "b", "c"];
        for (let j = 0; j < phases.length; j++) {
          let ph = entry[phases[j]];
          let vKey = phases[j] + "_voltage";
          let cKey = phases[j] + "_current";
          let pKey = phases[j] + "_act_power";
          let fKey = phases[j] + "_freq";
          if (typeof s[vKey] === "number") { addSample(ph.vs, s[vKey]); if (isSeed) ph.lastV = s[vKey]; }
          if (typeof s[cKey] === "number") { addSample(ph.cs, s[cKey]); if (isSeed) ph.lastC = s[cKey]; }
          if (typeof s[pKey] === "number") { addSample(ph.ps, s[pKey]); if (isSeed) ph.lastP = s[pKey]; }
          if (typeof s[fKey] === "number") { addSample(ph.fs, s[fKey]); if (isSeed) ph.lastF = s[fKey]; }
        }
      } else if (prefixes[p] === "em1") {
        let entry = getOrCreateComp(key, "em1", id);
        if (typeof s.voltage === "number") { addSample(entry.vs, s.voltage); if (isSeed) entry.lastV = s.voltage; }
        if (typeof s.current === "number") { addSample(entry.cs, s.current); if (isSeed) entry.lastC = s.current; }
        if (typeof s.act_power === "number") { addSample(entry.ps, s.act_power); if (isSeed) entry.lastP = s.act_power; }
        if (typeof s.freq === "number") { addSample(entry.fs, s.freq); if (isSeed) entry.lastF = s.freq; }
      } else {
        let entry = getOrCreateComp(key, prefixes[p], id);
        if (typeof s.voltage === "number") { addSample(entry.vs, s.voltage); if (isSeed) entry.lastV = s.voltage; }
        if (typeof s.current === "number") { addSample(entry.cs, s.current); if (isSeed) entry.lastC = s.current; }
        if (typeof s.apower === "number") { addSample(entry.ps, s.apower); if (isSeed) entry.lastP = s.apower; }
        if (typeof s.freq === "number") { addSample(entry.fs, s.freq); if (isSeed) entry.lastF = s.freq; }
        if (s.aenergy && typeof s.aenergy.total === "number") {
          entry.e = s.aenergy.total;
        }
      }
    }
  }

  // Update energy counters from emdata/em1data
  for (let id = 0; id < 8; id++) {
    let emdKey = "emdata:" + JSON.stringify(id);
    let emd = res[emdKey];
    if (emd) {
      let emKey = "em:" + JSON.stringify(id);
      let entry = getOrCreateComp(emKey, "em", id);
      if (typeof emd.a_total_act_energy === "number") entry.a.e = emd.a_total_act_energy;
      if (typeof emd.b_total_act_energy === "number") entry.b.e = emd.b_total_act_energy;
      if (typeof emd.c_total_act_energy === "number") entry.c.e = emd.c_total_act_energy;
    }
    let em1dKey = "em1data:" + JSON.stringify(id);
    let em1d = res[em1dKey];
    if (em1d) {
      let em1Key = "em1:" + JSON.stringify(id);
      let entry = getOrCreateComp(em1Key, "em1", id);
      if (typeof em1d.total_act_energy === "number") entry.e = em1d.total_act_energy;
    }
  }
}

// Send all accumulated reports for every tracked component, then reset samples
function sendAllReports() {
  for (let i = 0; i < compKeys.length; i++) {
    let entry = comps[compKeys[i]];

    if (entry.type === "em") {
      let phases = ["a", "b", "c"];
      for (let j = 0; j < phases.length; j++) {
        let ph = entry[phases[j]];
        sendPostReport(entry.id, "em", phases[j], ph);
        resetSample(ph.vs);
        resetSample(ph.cs);
        resetSample(ph.ps);
        resetSample(ph.fs);
      }
    } else {
      sendPostReport(entry.id, entry.type, null, entry);
      resetSample(entry.vs);
      resetSample(entry.cs);
      resetSample(entry.ps);
      resetSample(entry.fs);
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
  completeReportWithCurrentSamples();
}

function startReportStatusPoll() {
  statusPollInFlight = true;
  totalStatusPolls++;
  try {
    Shelly.call("Shelly.GetStatus", {}, onReportStatusResponse);
  } catch (e) {
    onReportStatusResponse(null, -1, e);
  }
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

// Seed accumulators with the one startup Shelly.GetStatus request. Subsequent
// status reads are handled by onReportStatusResponse and are intentionally
// infrequent fallback polls.
function seedFromStatus(cb) {
  totalStatusPolls++;
  try {
    Shelly.call("Shelly.GetStatus", {}, function (result, error_code, error_message) {
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
      if (typeof cb === "function") cb();
    });
  } catch (e) {
    failedStatusPolls++;
    setStatusPollDelay(STATUS_POLL_RETRY_SECS);
    print("seedFromStatus: GetStatus invocation failed: " + e);
    if (typeof cb === "function") cb();
  }
}

function startPowerMonitor() {
  Shelly.addStatusHandler(onStatus);
  // Startup work is deliberately serialized: URL, settings, then one status
  // seed. This prevents the old burst of seven concurrent RPC calls.
  fetchReportSettingsFromKVS(function () {
    seedFromStatus(function () {
      printDiagnostics("startup");
      print(
        "Power monitor started: version=" + POWERMONITOR_SCRIPT_VERSION +
          " interval=" + JSON.stringify(REPORT_INTERVAL) +
          "s statusPoll=" + JSON.stringify(STATUS_POLL_INTERVAL_SECS) +
          "s maxPending=" + JSON.stringify(MAX_PENDING_REPORTS),
      );
      scheduleNextReport();
    }, true);
  }, true);
}

startPowerMonitor();
