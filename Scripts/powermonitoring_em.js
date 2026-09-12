// Shelly EM collector for single-phase em1 and three-phase em components.
// Collects status-event readings, averages them, and posts them to Hubitat.

let VERSION = "3.0.1";
let REPORT_SECONDS = 60;
let REPORT_INTERVAL_KEY = "hubitat_sdm_pm_ri";
let HUBITAT_KVS_KEY = "hubitat_sdm_ip";
let DEFAULT_HUBITAT_IP = "192.168.1.4";
let HUBITAT_PORT = 39501;
let REMOTE_URL = "http://" + DEFAULT_HUBITAT_IP + ":" + HUBITAT_PORT;
let MAX_ID = 7;
let MAX_COMPONENTS = 1;
let MAX_RECORD_BYTES = 512;
let MAX_QUEUE = 6;
let MAX_QUEUE_BYTES = 1024;
let CHECKPOINT_KEY = "pmc";
let CHECKPOINT_META = "pmcm";
let QUEUE_KEY = "pmq";
let QUEUE_HEAD = "pmqh";
let QUEUE_COUNT = "pmqc";
let CHECKPOINT_VERSION = 3;
let CHECKPOINT_AGE_MS = 6 * 60 * 60 * 1000;
let DRAIN_DELAY_MS = 100;
let SETTINGS_REFRESH_MS = 15 * 60 * 1000;
let SETTING_KEYS = [REPORT_INTERVAL_KEY, "hubitat_sdm_pm_th_v", "hubitat_sdm_pm_th_c",
  "hubitat_sdm_pm_th_p", "hubitat_sdm_pm_th_e", "hubitat_sdm_pm_th_f"];
let THRESH_V = 1, THRESH_C = 0.05, THRESH_P = 5, THRESH_E = 5, THRESH_F = 0.5;
let PHASES = ["a", "b", "c"];

// [voltage sum, voltage count, current sum, current count,
//  power sum, power count, frequency sum, frequency count, energy]
let VS = 0, VC = 1, CS = 2, CC = 3, PS = 4, PC = 5, FS = 6, FC = 7, EN = 8;
let SENT_V = 9, SENT_C = 10, SENT_P = 11, SENT_F = 12, SENT_E = 13;
let PREFIXES = ["em", "em1", "emdata", "em1data"];
let components = [];
let drainTimer = null;
let reportTimer = null;
let reportInFlight = false;
let inFlightRecord = null;
let checkpointTimer = null;
let checkpointDirty = false;
let settingsIndex = 0;
let settingsInitial = false;

function storageAvailable() {
  return typeof Script !== "undefined" && Script.storage &&
    typeof Script.storage.getItem === "function" &&
    typeof Script.storage.setItem === "function" &&
    typeof Script.storage.removeItem === "function";
}

function number(value) {
  return typeof value === "number" && value === value &&
    value !== Infinity && value !== -Infinity;
}

function idFromText(text) {
  let id = parseInt(text, 10);
  return text !== "" + id || id < 0 || id > MAX_ID ? null : id;
}

function newData() {
  return [0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null];
}

function component(type, id) {
  let key = type + ":" + id;
  for (let i = 0; i < components.length; i++) {
    if (components[i].key === key) return components[i];
  }
  if (components.length >= MAX_COMPONENTS) return null;
  let entry = {key: key, type: type, id: id};
  if (type === "em") {
    entry.a = newData();
    entry.b = newData();
    entry.c = newData();
  } else {
    entry.data = newData();
  }
  components.push(entry);
  return entry;
}

function add(data, sum, count, value) {
  if (!number(value)) return false;
  data[sum] += value;
  data[count]++;
  return true;
}

function average(data, sum, count) {
  return data[count] ? data[sum] / data[count] : null;
}

function clearSamples(data) {
  data[VS] = 0; data[VC] = 0;
  data[CS] = 0; data[CC] = 0;
  data[PS] = 0; data[PC] = 0;
  data[FS] = 0; data[FC] = 0;
}

function markDirty() {
  checkpointDirty = true;
  if (storageAvailable() && checkpointTimer === null) {
    checkpointTimer = Timer.set(30000, false, saveCheckpoint);
  }
}

function valueText(value) {
  return value === null || value === undefined ? "" : String(value);
}

function checkpointData(data) {
  return valueText(data[VS]) + "," + valueText(data[VC]) + "," +
    valueText(data[CS]) + "," + valueText(data[CC]) + "," +
    valueText(data[PS]) + "," + valueText(data[PC]) + "," +
    valueText(data[FS]) + "," + valueText(data[FC]) + "," + valueText(data[EN]) + "," +
    valueText(data[SENT_V]) + "," + valueText(data[SENT_C]) + "," +
    valueText(data[SENT_P]) + "," + valueText(data[SENT_F]) + "," + valueText(data[SENT_E]);
}

function restoreData(fields, data) {
  if (fields.length !== 9 && fields.length !== 14) return false;
  for (let i = 0; i < fields.length; i++) {
    if (fields[i] === "") {
      data[i] = null;
      continue;
    }
    let value = parseFloat(fields[i]);
    if (!number(value)) return false;
    data[i] = value;
  }
  if (fields.length === 9) {
    data[SENT_V] = null; data[SENT_C] = null; data[SENT_P] = null;
    data[SENT_F] = null; data[SENT_E] = null;
  }
  return true;
}

function checkpointCount() {
  let count = 0;
  for (let i = 0; i < components.length; i++) {
    count += components[i].type === "em" ? 3 : 1;
  }
  return count;
}

function checkpointAt(index) {
  let current = 0;
  for (let i = 0; i < components.length; i++) {
    let entry = components[i];
    if (entry.type === "em") {
      for (let p = 0; p < PHASES.length; p++) {
        if (current === index) return [entry.type, entry.id, PHASES[p], checkpointData(entry[PHASES[p]])].join(",");
        current++;
      }
    } else {
      if (current === index) return [entry.type, entry.id, "", checkpointData(entry.data)].join(",");
      current++;
    }
  }
  return null;
}

function saveCheckpoint() {
  checkpointTimer = null;
  if (!checkpointDirty || !storageAvailable()) return;
  let count = checkpointCount();
  let timestamp = Date.now();
  try {
    for (let i = 0; i < count; i++) {
      let record = CHECKPOINT_VERSION + "|" + timestamp + "|" + checkpointAt(i);
      if (record.length > MAX_RECORD_BYTES) return;
      Script.storage.setItem(CHECKPOINT_KEY + i, record);
    }
    for (let i = count; i < 3; i++) Script.storage.removeItem(CHECKPOINT_KEY + i);
    Script.storage.setItem(CHECKPOINT_META,
      CHECKPOINT_VERSION + "," + timestamp + "," + count);
    checkpointDirty = false;
  } catch (e) {
    print("EM checkpoint save failed: " + e);
  }
}

function restoreCheckpoint() {
  if (!storageAvailable()) return;
  let meta = Script.storage.getItem(CHECKPOINT_META);
  if (meta === null) return;
  let fields = meta.split(",");
  if (fields.length !== 3 || (parseInt(fields[0], 10) !== CHECKPOINT_VERSION && parseInt(fields[0], 10) !== 2)) return;
  let timestamp = parseInt(fields[1], 10);
  let count = parseInt(fields[2], 10);
  if (count < 0 || count > 3 || Date.now() - timestamp > CHECKPOINT_AGE_MS) return;
  for (let i = 0; i < count; i++) {
    let raw = Script.storage.getItem(CHECKPOINT_KEY + i);
    if (raw === null) return;
    let parts = raw.split("|");
    if (parts.length !== 3 || (parseInt(parts[0], 10) !== CHECKPOINT_VERSION && parseInt(parts[0], 10) !== 2) ||
        parseInt(parts[1], 10) !== timestamp) return;
    let record = parts[2].split(",");
    let id = idFromText(record[1]);
    if ((record.length !== 12 && record.length !== 17) || id === null ||
        (record[0] !== "em" && record[0] !== "em1")) continue;
    let entry = component(record[0], id);
    if (!entry) continue;
    let data = record[0] === "em" ? entry[record[2]] : entry.data;
    if (data) restoreData(record.slice(3), data);
  }
}

function queueHead() {
  let raw = Script.storage.getItem(QUEUE_HEAD);
  let separator = raw === null ? -1 : raw.indexOf(":");
  if (separator > 0) raw = raw.substring(separator + 1);
  let value = parseInt(raw, 10);
  return value >= 0 && value < MAX_QUEUE ? value : 0;
}

function queueCount() {
  let value = parseInt(Script.storage.getItem(QUEUE_COUNT), 10);
  return value >= 0 && value <= MAX_QUEUE ? value : 0;
}

function queueMeta(head, count) {
  Script.storage.setItem(QUEUE_HEAD, String(head));
  Script.storage.setItem(QUEUE_COUNT, String(count));
}

function queueBytes() {
  let bytes = 0;
  let head = queueHead();
  let count = queueCount();
  for (let i = 0; i < count; i++) {
    let value = Script.storage.getItem(QUEUE_KEY + ((head + i) % MAX_QUEUE));
    if (value !== null) bytes += value.length;
  }
  return bytes;
}

function dropOldest() {
  let count = queueCount();
  if (!count) return;
  let head = queueHead();
  Script.storage.removeItem(QUEUE_KEY + head);
  queueMeta((head + 1) % MAX_QUEUE, count - 1);
}

function putQueue(record) {
  if (!storageAvailable() || record.length > MAX_RECORD_BYTES) return false;
  while (queueCount() >= MAX_QUEUE || queueBytes() + record.length > MAX_QUEUE_BYTES) {
    if (!queueCount()) return false;
    dropOldest();
  }
  let head = queueHead();
  let count = queueCount();
  Script.storage.setItem(QUEUE_KEY + ((head + count) % MAX_QUEUE), record);
  queueMeta(head, count + 1);
  return true;
}

function takeQueue() {
  let count = queueCount();
  if (!count) return null;
  let head = queueHead();
  let record = Script.storage.getItem(QUEUE_KEY + head);
  Script.storage.removeItem(QUEUE_KEY + head);
  queueMeta((head + 1) % MAX_QUEUE, count - 1);
  return record;
}

function round(value, places) {
  if (value === null) return null;
  let scale = places === 0 ? 1 : places === 1 ? 10 : 100;
  return Math.round(value * scale) / scale;
}

function changed(value, previous, threshold) {
  return value !== null && (previous === null || Math.abs(value - previous) >= threshold);
}

function updateSent(data) {
  let v = round(average(data, VS, VC), 1);
  let c = round(average(data, CS, CC), 2);
  let p = round(average(data, PS, PC), 0);
  let f = round(average(data, FS, FC), 1);
  let e = data[EN] === null ? null : round(data[EN], 1);
  if (v !== null) data[SENT_V] = v;
  if (c !== null) data[SENT_C] = c;
  if (p !== null) data[SENT_P] = p;
  if (f !== null) data[SENT_F] = f;
  if (e !== null) data[SENT_E] = e;
}

function body(entry, phase, data) {
  let v = round(average(data, VS, VC), 1);
  let c = round(average(data, CS, CC), 2);
  let p = round(average(data, PS, PC), 0);
  let f = round(average(data, FS, FC), 1);
  let e = data[EN] === null ? null : round(data[EN], 1);
  if (v === null && c === null && p === null && f === null && e === null) return null;
  if (!changed(v, data[SENT_V], THRESH_V) && !changed(c, data[SENT_C], THRESH_C) &&
      !changed(p, data[SENT_P], THRESH_P) && !changed(f, data[SENT_F], THRESH_F) &&
      !changed(e, data[SENT_E], THRESH_E)) return null;
  let result = "{\"dst\":\"powermon\",\"cid\":" + entry.id +
    ",\"comp\":\"" + entry.type + "\"";
  if (phase) result += ",\"phase\":\"" + phase + "\"";
  if (v !== null) result += ",\"voltage\":" + v;
  if (c !== null) result += ",\"current\":" + c;
  if (p !== null) result += ",\"apower\":" + p;
  if (e !== null) result += ",\"aenergy\":" + e;
  if (f !== null) result += ",\"freq\":" + f;
  return result + "}";
}

function sendReports() {
  refreshStatus();
  let queued = 0;
  for (let i = 0; i < components.length; i++) {
    let entry = components[i];
    if (entry.type === "em") {
      for (let p = 0; p < 3; p++) {
        let phase = PHASES[p];
        let report = body(entry, phase, entry[phase]);
        if (report === null) {
          clearSamples(entry[phase]);
        } else if (putQueue(entry.id + "|em|" + phase + "|" + report)) {
          queued++;
          updateSent(entry[phase]);
          clearSamples(entry[phase]);
        }
      }
    } else {
      let report = body(entry, "", entry.data);
      if (report === null) {
        clearSamples(entry.data);
      } else if (putQueue(entry.id + "|em1||" + report)) {
        queued++;
        updateSent(entry.data);
        clearSamples(entry.data);
      }
    }
  }
  print("EM report cycle: components=" + components.length + " queued=" + queued +
    " pending=" + queueCount());
  drainQueue();
  reportTimer = Timer.set(REPORT_SECONDS * 1000, false, sendReports);
}

function retryDrain() {
  drainTimer = null;
  drainQueue();
}

function httpDone(result, errorCode, errorMessage) {
  if (!reportInFlight) return;
  if (errorCode !== 0 && inFlightRecord !== null) putQueue(inFlightRecord);
  reportInFlight = false;
  inFlightRecord = null;
  if (errorCode !== 0) print("EM Hubitat POST failed: " + errorCode + " " + errorMessage);
  else print("EM Hubitat POST delivered");
  if (drainTimer === null) drainTimer = Timer.set(DRAIN_DELAY_MS, false, retryDrain);
}

function drainQueue() {
  if (!storageAvailable() || reportInFlight || queueCount() === 0) return;
  let record = takeQueue();
  if (record === null) return;
  let first = record.indexOf("|");
  let second = record.indexOf("|", first + 1);
  let third = record.indexOf("|", second + 1);
  if (first < 0 || second < 0 || third < 0) return;
  let id = record.substring(0, first);
  let payload = record.substring(third + 1);
  inFlightRecord = record;
  reportInFlight = true;
  try {
    Shelly.call("HTTP.POST", {
      url: REMOTE_URL + "/webhook/powermon/" + id,
      body: payload,
      content_type: "application/json",
      timeout: 10
    }, httpDone);
  } catch (e) {
    httpDone(null, -1, e);
  }
}

function addStatus(key, d) {
  let colon = key.indexOf(":");
  if (colon < 0) return;
  let type = key.substring(0, colon);
  let id = idFromText(key.substring(colon + 1));
  if (id === null || (type !== "em" && type !== "em1" &&
      type !== "emdata" && type !== "em1data")) return;
  let entry = component(type === "emdata" ? "em" : type === "em1data" ? "em1" : type, id);
  if (!entry) return;
  if (type === "em") {
    for (let p = 0; p < 3; p++) {
      let phase = PHASES[p];
      let data = entry[phase];
      add(data, VS, VC, d[phase + "_voltage"]);
      add(data, CS, CC, d[phase + "_current"]);
      add(data, PS, PC, d[phase + "_act_power"]);
      add(data, FS, FC, d[phase + "_freq"]);
    }
  } else if (type === "em1") {
    add(entry.data, VS, VC, d.voltage);
    add(entry.data, CS, CC, d.current);
    add(entry.data, PS, PC, d.act_power);
    add(entry.data, FS, FC, d.freq);
  } else if (type === "emdata") {
    for (let p = 0; p < 3; p++) {
      if (number(d[PHASES[p] + "_total_act_energy"])) entry[PHASES[p]][EN] = d[PHASES[p] + "_total_act_energy"];
    }
  } else if (number(d.total_act_energy)) {
    entry.data[EN] = d.total_act_energy;
  }
  markDirty();
}

function statusHandler(event) {
  if (!event || typeof event.component !== "string" ||
      !event.delta || typeof event.delta !== "object") return;
  let key = event.component;
  if (key.indexOf(":") < 0 && event.id !== undefined) key += ":" + event.id;
  addStatus(key, event.delta);
}

function nextSetting() {
  if (settingsIndex >= SETTING_KEYS.length) {
    Timer.set(SETTINGS_REFRESH_MS, false, startSettings);
    print("EM settings loaded: interval=" + REPORT_SECONDS + " thresholds=" +
      THRESH_V + "," + THRESH_C + "," + THRESH_P + "," + THRESH_E + "," + THRESH_F);
    if (settingsInitial) {
      settingsInitial = false;
      seed();
    }
    return;
  }
  settingsIndex++;
  try {
    Shelly.call("KVS.Get", {key: SETTING_KEYS[settingsIndex - 1]}, settingDone);
  } catch (e) {
    Timer.set(10, false, nextSetting);
  }
}

function settingDone(result, errorCode) {
  let raw = null;
  if (errorCode === 0 && result) {
    raw = result.value !== undefined ? result.value : result.result && result.result.value;
  }
  let value = parseFloat(raw);
  if (number(value) && value >= 0) {
    if (settingsIndex === 1 && value > 0) REPORT_SECONDS = Math.floor(value);
    else if (settingsIndex === 2) THRESH_V = value;
    else if (settingsIndex === 3) THRESH_C = value;
    else if (settingsIndex === 4) THRESH_P = value;
    else if (settingsIndex === 5) THRESH_E = value;
    else if (settingsIndex === 6) THRESH_F = value;
  }
  result = null;
  raw = null;
  Timer.set(10, false, nextSetting);
}

function startSettings() {
  settingsIndex = 0;
  nextSetting();
}

function readStatus(key) {
  if (typeof Shelly.getComponentStatus !== "function") return;
  try {
    let status = Shelly.getComponentStatus(key);
    if (status && typeof status === "object") addStatus(key, status);
    status = null;
  } catch (e) {
    print("EM status read failed: " + key + " " + e);
  }
}

function refreshStatus() {
  if (components.length === 0) {
    for (let p = 0; p < PREFIXES.length; p++) {
      for (let id = 0; id <= MAX_ID; id++) readStatus(PREFIXES[p] + ":" + id);
    }
    return;
  }
  for (let i = 0; i < components.length; i++) {
    let entry = components[i];
    readStatus(entry.key);
    if (entry.type === "em") readStatus("emdata:" + entry.id);
    else readStatus("em1data:" + entry.id);
  }
}

function seed() {
  for (let p = 0; p < PREFIXES.length; p++) {
    for (let id = 0; id <= MAX_ID; id++) readStatus(PREFIXES[p] + ":" + id);
  }
  if (reportTimer !== null) Timer.clear(reportTimer);
  reportTimer = Timer.set(REPORT_SECONDS * 1000, false, sendReports);
}

function hubitatIpDone(result, errorCode) {
  let raw = errorCode === 0 && result ?
    (result.value !== undefined ? result.value : result.result && result.result.value) : null;
  if (raw) {
    let value = String(raw);
    if (value.indexOf("http://") === 0 || value.indexOf("https://") === 0) REMOTE_URL = value;
    else REMOTE_URL = "http://" + value + (value.indexOf(":") < 0 ? ":" + HUBITAT_PORT : "");
  }
  settingsInitial = true;
  startSettings();
}

function start() {
  restoreCheckpoint();
  Shelly.addStatusHandler(statusHandler);
  try {
    Shelly.call("KVS.Get", {key: HUBITAT_KVS_KEY}, hubitatIpDone);
  } catch (e) {
    settingsInitial = true;
    startSettings();
  }
  print("EM monitor started: version=" + VERSION);
}

start();
