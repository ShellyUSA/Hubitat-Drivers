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
let DEFAULT_REPORT_INTERVAL = 60; // Fallback if KVS lookup fails
let REPORT_INTERVAL = DEFAULT_REPORT_INTERVAL;
let REPORT_INTERVAL_KVS_KEY = "hubitat_sdm_pm_ri"; // KVS key for dynamic report interval (seconds)

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
let REPORT_THRESHOLD_KVS_KEYS = {
  voltage: "hubitat_sdm_pm_th_v",
  current: "hubitat_sdm_pm_th_c",
  power: "hubitat_sdm_pm_th_p",
  energy: "hubitat_sdm_pm_th_e",
  frequency: "hubitat_sdm_pm_th_f",
};

// REMOTE_URL is built from KVS value (or fallback)
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;

// === Per-component accumulators ===
let comps = {}; // Keyed by component name (e.g. "pm1:0", "em:0")
let compKeys = []; // Track keys for iteration (mJS has no Object.keys)

// HTTP response handler
function onHTTPResponse(result, error_code, error_message) {
  if (error_code !== 0) {
    print("HTTP error:", error_code, error_message);
  }
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

// Try to read hub IP from Shelly KVS; on success replace REMOTE_URL. Graceful no-ops if KVS isn't available.
function fetchRemoteUrlFromKVS() {
  if (typeof Shelly.call !== "function") {
    print("Shelly.call() not available; using REMOTE_URL=" + REMOTE_URL);
    return;
  }
  try {
    Shelly.call("KVS.Get", { key: HUBITAT_KVS_KEY }, function (res, err, msg) {
      if (err !== 0 || res === undefined || res === null) {
        print("KVS.Get did not return a value; using REMOTE_URL=" + REMOTE_URL);
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
    });
  } catch (e) {
    print(
      "KVS.Get invocation failed; using REMOTE_URL=" +
        REMOTE_URL +
        " (" +
        e +
        ")",
    );
  }
}

// Read report interval from Shelly KVS; update REPORT_INTERVAL then invoke optional callback.
function fetchReportIntervalFromKVS(cb) {
  try {
    Shelly.call("KVS.Get", { key: REPORT_INTERVAL_KVS_KEY }, function (res, err, msg) {
      if (err === 0 && res) {
        let raw = null;
        if (typeof res.value === "string") raw = res.value;
        else if (typeof res.value === "number") raw = res.value;
        else if (res.result && res.result.value !== undefined) raw = res.result.value;
        if (raw !== null) {
          let parsed = parseInt(raw, 10);
          if (parsed > 0) {
            if (parsed !== REPORT_INTERVAL) {
              print("Report interval changed: " + JSON.stringify(REPORT_INTERVAL) + "s -> " + JSON.stringify(parsed) + "s");
            }
            REPORT_INTERVAL = parsed;
          }
        }
      }
      if (typeof cb === "function") cb();
    });
  } catch (e) {
    print("KVS report interval fetch failed: " + e);
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

// Refresh all reporting settings before scheduling the next cycle so changes
// saved in Hubitat take effect without restarting the script.
function fetchReportSettingsFromKVS(cb) {
  fetchReportIntervalFromKVS(function () {
    let remaining = 5;
    function complete() {
      remaining--;
      if (remaining === 0 && typeof cb === "function") cb();
    }
    fetchNumberFromKVS(REPORT_THRESHOLD_KVS_KEYS.voltage, function (value) {
      if (value !== null) THRESH_V = value;
      complete();
    });
    fetchNumberFromKVS(REPORT_THRESHOLD_KVS_KEYS.current, function (value) {
      if (value !== null) THRESH_C = value;
      complete();
    });
    fetchNumberFromKVS(REPORT_THRESHOLD_KVS_KEYS.power, function (value) {
      if (value !== null) THRESH_P = value;
      complete();
    });
    fetchNumberFromKVS(REPORT_THRESHOLD_KVS_KEYS.energy, function (value) {
      if (value !== null) THRESH_E = value;
      complete();
    });
    fetchNumberFromKVS(REPORT_THRESHOLD_KVS_KEYS.frequency, function (value) {
      if (value !== null) THRESH_F = value;
      complete();
    });
  });
}

// Schedule the next one-shot report timer using the current REPORT_INTERVAL
function scheduleNextReport() {
  Timer.set(REPORT_INTERVAL * 1000, false, sendReport);
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
      a: { vs: newSample(), cs: newSample(), ps: newSample(), fs: newSample(), e: null, lastV: null, lastC: null, lastP: null, lastF: null, sentV: null, sentC: null, sentP: null, sentF: null, sentE: null, sentAge: 0 },
      b: { vs: newSample(), cs: newSample(), ps: newSample(), fs: newSample(), e: null, lastV: null, lastC: null, lastP: null, lastF: null, sentV: null, sentC: null, sentP: null, sentF: null, sentE: null, sentAge: 0 },
      c: { vs: newSample(), cs: newSample(), ps: newSample(), fs: newSample(), e: null, lastV: null, lastC: null, lastP: null, lastF: null, sentV: null, sentC: null, sentP: null, sentF: null, sentE: null, sentAge: 0 },
    };
  } else {
    c = { type: type, id: id, vs: newSample(), cs: newSample(), ps: newSample(), fs: newSample(), e: null, lastV: null, lastC: null, lastP: null, lastF: null, sentV: null, sentC: null, sentP: null, sentF: null, sentE: null, sentAge: 0 };
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
    return;
  }

  // em1data: energy counter for single-phase em1 components
  if (type === "em1data") {
    let em1Key = "em1:" + JSON.stringify(id);
    let entry = getOrCreateComp(em1Key, "em1", id);
    if (typeof d.total_act_energy === "number") entry.e = d.total_act_energy;
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
    return;
  }

  // em1: single-phase energy meter (voltage, current, act_power, freq)
  if (type === "em1") {
    let entry = getOrCreateComp(comp, "em1", id);
    if (typeof d.voltage === "number") addSample(entry.vs, d.voltage);
    if (typeof d.current === "number") addSample(entry.cs, d.current);
    if (typeof d.act_power === "number") addSample(entry.ps, d.act_power);
    if (typeof d.freq === "number") addSample(entry.fs, d.freq);
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
}

// Build and send a POST request with normalized power monitoring params as JSON body.
// Applies per-field rounding and suppresses reports until a configured
// threshold is crossed. A threshold of 0 sends a report every interval.
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

  // Update last-known and last-sent tracking
  if (v !== null) { data.lastV = v; data.sentV = v; }
  if (cur !== null) { data.lastC = cur; data.sentC = cur; }
  if (p !== null) { data.lastP = p; data.sentP = p; }
  if (f !== null) { data.lastF = f; data.sentF = f; }
  if (e !== null) data.sentE = e;
  data.sentAge = 0;

  let url = REMOTE_URL + "/webhook/powermon/" + JSON.stringify(compId);
  Shelly.call(
    "HTTP.POST",
    { url: url, body: JSON.stringify(body), content_type: "application/json" },
    onHTTPResponse,
  );

  print("Reported:", url, JSON.stringify(body));
}

// Push fresh status readings into bounded accumulators for averaging.
// Unlike seedFromStatus(), does NOT set lastV/lastC/etc. -- those are
// updated by sendPostReport() after computing the cycle average.
function pushStatusReadings(res) {
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
          if (typeof s[vKey] === "number") addSample(ph.vs, s[vKey]);
          if (typeof s[cKey] === "number") addSample(ph.cs, s[cKey]);
          if (typeof s[pKey] === "number") addSample(ph.ps, s[pKey]);
          if (typeof s[fKey] === "number") addSample(ph.fs, s[fKey]);
        }
      } else if (prefixes[p] === "em1") {
        let entry = getOrCreateComp(key, "em1", id);
        if (typeof s.voltage === "number") addSample(entry.vs, s.voltage);
        if (typeof s.current === "number") addSample(entry.cs, s.current);
        if (typeof s.act_power === "number") addSample(entry.ps, s.act_power);
        if (typeof s.freq === "number") addSample(entry.fs, s.freq);
      } else {
        let entry = getOrCreateComp(key, prefixes[p], id);
        if (typeof s.voltage === "number") addSample(entry.vs, s.voltage);
        if (typeof s.current === "number") addSample(entry.cs, s.current);
        if (typeof s.apower === "number") addSample(entry.ps, s.apower);
        if (typeof s.freq === "number") addSample(entry.fs, s.freq);
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

// Timer callback: fetch fresh status, merge with accumulated deltas, report, then reschedule
function sendReport() {
  if (compKeys.length === 0) {
    print("No power events received yet");
    fetchReportSettingsFromKVS(scheduleNextReport);
    return;
  }

  // Fetch fresh readings so every cycle has at least one data point,
  // even when delta events don't fire for small value changes
  Shelly.call("Shelly.GetStatus", {}, function (res, err, msg) {
    if (err === 0 && res) {
      pushStatusReadings(res);
    }
    // Send reports even if GetStatus failed -- deltas may exist
    sendAllReports();
    // Re-read interval and thresholds from KVS, then schedule the next cycle.
    fetchReportSettingsFromKVS(scheduleNextReport);
  });
}

// Seed accumulators with initial readings from Shelly.GetStatus.
// Ensures the first report cycle includes all PM fields even if no
// status delta events arrive before the first timer fires (e.g., for
// devices with stable power draw that don't trigger frequent change events).
function seedFromStatus() {
  Shelly.call("Shelly.GetStatus", {}, function (res, err, msg) {
    if (err !== 0 || !res) {
      print("seedFromStatus: GetStatus failed, err=" + JSON.stringify(err));
      return;
    }
    // Iterate known component prefixes and seed their accumulators
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
            if (typeof s[vKey] === "number") { addSample(ph.vs, s[vKey]); ph.lastV = s[vKey]; }
            if (typeof s[cKey] === "number") { addSample(ph.cs, s[cKey]); ph.lastC = s[cKey]; }
            if (typeof s[pKey] === "number") { addSample(ph.ps, s[pKey]); ph.lastP = s[pKey]; }
            if (typeof s[fKey] === "number") { addSample(ph.fs, s[fKey]); ph.lastF = s[fKey]; }
          }
        } else if (prefixes[p] === "em1") {
          let entry = getOrCreateComp(key, "em1", id);
          if (typeof s.voltage === "number") { addSample(entry.vs, s.voltage); entry.lastV = s.voltage; }
          if (typeof s.current === "number") { addSample(entry.cs, s.current); entry.lastC = s.current; }
          if (typeof s.act_power === "number") { addSample(entry.ps, s.act_power); entry.lastP = s.act_power; }
          if (typeof s.freq === "number") { addSample(entry.fs, s.freq); entry.lastF = s.freq; }
        } else {
          // switch, pm1, cover: use voltage, current, apower, freq
          let entry = getOrCreateComp(key, prefixes[p], id);
          if (typeof s.voltage === "number") { addSample(entry.vs, s.voltage); entry.lastV = s.voltage; }
          if (typeof s.current === "number") { addSample(entry.cs, s.current); entry.lastC = s.current; }
          if (typeof s.apower === "number") { addSample(entry.ps, s.apower); entry.lastP = s.apower; }
          if (typeof s.freq === "number") { addSample(entry.fs, s.freq); entry.lastF = s.freq; }
          if (s.aenergy && typeof s.aenergy.total === "number") {
            entry.e = s.aenergy.total;
          }
        }
        print("Seeded " + key + " from GetStatus");
      }
    }

    // Also seed emdata/em1data energy counters
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
  });
}

// Initialize REMOTE_URL and REPORT_INTERVAL from KVS (async), seed from status, then start
fetchRemoteUrlFromKVS();
seedFromStatus();
Shelly.addStatusHandler(onStatus);
// Read report settings from KVS, then schedule the first one-shot report timer.
fetchReportSettingsFromKVS(scheduleNextReport);

print(
  "Power monitor started: default_interval=" +
    JSON.stringify(DEFAULT_REPORT_INTERVAL) +
    "s url=" +
    REMOTE_URL,
);
