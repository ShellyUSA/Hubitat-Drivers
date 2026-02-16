// ==========================================
// Power Monitoring Collector & Reporter
// ==========================================
// Runs on a Shelly Gen2+ device to collect
// power readings from status events, average
// them over a configurable interval, and
// report aggregated data to a Hubitat hub.
//
// Supported components:
//   PM1/switch/cover  — single-phase, inline energy (aenergy.total)
//   EM1               — single-phase, separate energy (em1data)
//   EM                — 3-phase, separate energy (emdata)
// ==========================================

// === USER CONFIGURATION ===
let REPORT_INTERVAL = 60; // Seconds between reports to Hubitat

// Hubitat KVS configuration
let HUBITAT_KVS_KEY = "hubitat_ip"; // store only the IP (no protocol/port) in Shelly KVS
let HUBITAT_DEFAULT_IP = "192.168.1.4"; // fallback if KVS lookup fails
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";
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
        print("KVS hubitat_ip found; REMOTE_URL set to " + REMOTE_URL);
      } else {
        print("KVS hubitat_ip empty; using REMOTE_URL=" + REMOTE_URL);
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

// Compute average of a numeric array, rounded to 2 decimal places
function average(arr) {
  if (arr.length === 0) return null;
  let sum = 0;
  for (let i = 0; i < arr.length; i++) sum += arr[i];
  return Math.round((sum / arr.length) * 100) / 100;
}

// Get or create a component accumulator entry
function getOrCreateComp(key, type, id) {
  if (comps[key]) return comps[key];
  let c;
  if (type === "em") {
    c = {
      type: "em",
      id: id,
      a: { vs: [], cs: [], ps: [], fs: [], e: null, lastV: null, lastC: null, lastP: null, lastF: null },
      b: { vs: [], cs: [], ps: [], fs: [], e: null, lastV: null, lastC: null, lastP: null, lastF: null },
      c: { vs: [], cs: [], ps: [], fs: [], e: null, lastV: null, lastC: null, lastP: null, lastF: null },
    };
  } else {
    c = { type: type, id: id, vs: [], cs: [], ps: [], fs: [], e: null, lastV: null, lastC: null, lastP: null, lastF: null };
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
      if (typeof d[p + "_voltage"] === "number") ph.vs.push(d[p + "_voltage"]);
      if (typeof d[p + "_current"] === "number") ph.cs.push(d[p + "_current"]);
      if (typeof d[p + "_act_power"] === "number") ph.ps.push(d[p + "_act_power"]);
      if (typeof d[p + "_freq"] === "number") ph.fs.push(d[p + "_freq"]);
    }
    return;
  }

  // em1: single-phase energy meter (voltage, current, act_power, freq)
  if (type === "em1") {
    let entry = getOrCreateComp(comp, "em1", id);
    if (typeof d.voltage === "number") entry.vs.push(d.voltage);
    if (typeof d.current === "number") entry.cs.push(d.current);
    if (typeof d.act_power === "number") entry.ps.push(d.act_power);
    if (typeof d.freq === "number") entry.fs.push(d.freq);
    return;
  }

  // pm1, switch, cover: single-phase power monitoring (voltage, current, apower, freq)
  if (type !== "switch" && type !== "pm1" && type !== "cover") return;

  let entry = getOrCreateComp(comp, type, id);
  if (typeof d.voltage === "number") entry.vs.push(d.voltage);
  if (typeof d.current === "number") entry.cs.push(d.current);
  if (typeof d.apower === "number") entry.ps.push(d.apower);
  if (typeof d.freq === "number") entry.fs.push(d.freq);
  if (d.aenergy !== undefined && d.aenergy !== null) {
    if (typeof d.aenergy.total === "number") {
      entry.e = d.aenergy.total;
    }
  }
}

// Build and send a POST request with normalized power monitoring params as JSON body
function sendPostReport(compId, compType, phase, data) {
  let v = average(data.vs);
  let cur = average(data.cs);
  let p = average(data.ps);
  let f = average(data.fs);

  // Fall back to last-known values when no deltas were received
  if (v === null && data.lastV !== null) v = data.lastV;
  if (cur === null && data.lastC !== null) cur = data.lastC;
  if (p === null && data.lastP !== null) p = data.lastP;
  if (f === null && data.lastF !== null) f = data.lastF;

  if (v === null && cur === null && p === null && f === null && data.e === null) {
    return;
  }

  let body = { dst: "powermon", cid: compId, comp: compType };
  if (phase) body.phase = phase;
  if (v !== null) body.voltage = v;
  if (cur !== null) body.current = cur;
  if (p !== null) body.apower = p;
  if (data.e !== null) body.aenergy = data.e;
  if (f !== null) body.freq = f;

  // Store reported values as last-known for next cycle
  if (v !== null) data.lastV = v;
  if (cur !== null) data.lastC = cur;
  if (p !== null) data.lastP = p;
  if (f !== null) data.lastF = f;

  let url = REMOTE_URL + "/webhook/powermon/" + JSON.stringify(compId);
  Shelly.call(
    "HTTP.POST",
    { url: url, body: JSON.stringify(body), content_type: "application/json" },
    onHTTPResponse,
  );

  print("Reported:", url, JSON.stringify(body));
}

// Push fresh status readings into accumulator arrays for averaging.
// Unlike seedFromStatus(), does NOT set lastV/lastC/etc. — those are
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
          if (typeof s[vKey] === "number") ph.vs.push(s[vKey]);
          if (typeof s[cKey] === "number") ph.cs.push(s[cKey]);
          if (typeof s[pKey] === "number") ph.ps.push(s[pKey]);
          if (typeof s[fKey] === "number") ph.fs.push(s[fKey]);
        }
      } else if (prefixes[p] === "em1") {
        let entry = getOrCreateComp(key, "em1", id);
        if (typeof s.voltage === "number") entry.vs.push(s.voltage);
        if (typeof s.current === "number") entry.cs.push(s.current);
        if (typeof s.act_power === "number") entry.ps.push(s.act_power);
        if (typeof s.freq === "number") entry.fs.push(s.freq);
      } else {
        let entry = getOrCreateComp(key, prefixes[p], id);
        if (typeof s.voltage === "number") entry.vs.push(s.voltage);
        if (typeof s.current === "number") entry.cs.push(s.current);
        if (typeof s.apower === "number") entry.ps.push(s.apower);
        if (typeof s.freq === "number") entry.fs.push(s.freq);
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

// Send all accumulated reports for every tracked component, then reset arrays
function sendAllReports() {
  for (let i = 0; i < compKeys.length; i++) {
    let entry = comps[compKeys[i]];

    if (entry.type === "em") {
      let phases = ["a", "b", "c"];
      for (let j = 0; j < phases.length; j++) {
        let ph = entry[phases[j]];
        sendPostReport(entry.id, "em", phases[j], ph);
        ph.vs = [];
        ph.cs = [];
        ph.ps = [];
        ph.fs = [];
      }
    } else {
      sendPostReport(entry.id, entry.type, null, entry);
      entry.vs = [];
      entry.cs = [];
      entry.ps = [];
      entry.fs = [];
    }
  }
}

// Timer callback: fetch fresh status, merge with accumulated deltas, then report
function sendReport() {
  if (compKeys.length === 0) {
    print("No power events received yet");
    return;
  }

  // Fetch fresh readings so every cycle has at least one data point,
  // even when delta events don't fire for small value changes
  Shelly.call("Shelly.GetStatus", {}, function (res, err, msg) {
    if (err === 0 && res) {
      pushStatusReadings(res);
    }
    // Send reports even if GetStatus failed — deltas may exist
    sendAllReports();
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
            if (typeof s[vKey] === "number") { ph.vs.push(s[vKey]); ph.lastV = s[vKey]; }
            if (typeof s[cKey] === "number") { ph.cs.push(s[cKey]); ph.lastC = s[cKey]; }
            if (typeof s[pKey] === "number") { ph.ps.push(s[pKey]); ph.lastP = s[pKey]; }
            if (typeof s[fKey] === "number") { ph.fs.push(s[fKey]); ph.lastF = s[fKey]; }
          }
        } else if (prefixes[p] === "em1") {
          let entry = getOrCreateComp(key, "em1", id);
          if (typeof s.voltage === "number") { entry.vs.push(s.voltage); entry.lastV = s.voltage; }
          if (typeof s.current === "number") { entry.cs.push(s.current); entry.lastC = s.current; }
          if (typeof s.act_power === "number") { entry.ps.push(s.act_power); entry.lastP = s.act_power; }
          if (typeof s.freq === "number") { entry.fs.push(s.freq); entry.lastF = s.freq; }
        } else {
          // switch, pm1, cover: use voltage, current, apower, freq
          let entry = getOrCreateComp(key, prefixes[p], id);
          if (typeof s.voltage === "number") { entry.vs.push(s.voltage); entry.lastV = s.voltage; }
          if (typeof s.current === "number") { entry.cs.push(s.current); entry.lastC = s.current; }
          if (typeof s.apower === "number") { entry.ps.push(s.apower); entry.lastP = s.apower; }
          if (typeof s.freq === "number") { entry.fs.push(s.freq); entry.lastF = s.freq; }
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

// Initialize REMOTE_URL from KVS (async), seed from status, then start handlers/timer
fetchRemoteUrlFromKVS();
seedFromStatus();
Shelly.addStatusHandler(onStatus);
Timer.set(REPORT_INTERVAL * 1000, true, sendReport);

print(
  "Power monitor started: interval=" +
    JSON.stringify(REPORT_INTERVAL) +
    "s url=" +
    REMOTE_URL,
);
