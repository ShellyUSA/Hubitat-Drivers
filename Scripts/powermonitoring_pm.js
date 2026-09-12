// Shelly PM collector for PM-capable switch, pm1, and cover components.
// Collects status-event readings, averages them, and posts them to Hubitat.

let VERSION = "3.0.1";
let REPORT_SECONDS = 60;
let REPORT_INTERVAL_KEY = "hubitat_sdm_pm_ri";
let HUBITAT_KVS_KEY = "hubitat_sdm_ip";
let DEFAULT_HUBITAT_IP = "192.168.1.4";
let HUBITAT_PORT = 39501;
let REMOTE_URL = "http://" + DEFAULT_HUBITAT_IP + ":" + HUBITAT_PORT;
let MAX_ID = 7;
let MAX_COMPONENTS = 4;
let MAX_RECORD_BYTES = 512;
let MAX_QUEUE = 4;
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

// [voltage sum, voltage count, current sum, current count,
//  power sum, power count, frequency sum, frequency count, energy]
let VS = 0, VC = 1, CS = 2, CC = 3, PS = 4, PC = 5, FS = 6, FC = 7, EN = 8;
let SENT_V = 9, SENT_C = 10, SENT_P = 11, SENT_F = 12, SENT_E = 13;
let PREFIXES = ["switch", "pm1", "cover"];
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
  let entry = {key: key, type: type, id: id, data: newData()};
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

function saveCheckpoint() {
  checkpointTimer = null;
  if (!checkpointDirty || !storageAvailable()) return;
  let timestamp = Date.now();
  try {
    for (let i = 0; i < components.length; i++) {
      let e = components[i];
      let record = CHECKPOINT_VERSION + "|" + timestamp + "|" +
        e.type + "," + e.id + "," + checkpointData(e.data);
      if (record.length > MAX_RECORD_BYTES) return;
      Script.storage.setItem(CHECKPOINT_KEY + i, record);
    }
    for (let i = components.length; i < MAX_COMPONENTS; i++) {
      Script.storage.removeItem(CHECKPOINT_KEY + i);
    }
    Script.storage.setItem(CHECKPOINT_META,
      CHECKPOINT_VERSION + "," + timestamp + "," + components.length);
    checkpointDirty = false;
  } catch (e) {
    print("PM checkpoint save failed: " + e);
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
  if (count < 0 || count > MAX_COMPONENTS || Date.now() - timestamp > CHECKPOINT_AGE_MS) return;
  for (let i = 0; i < count; i++) {
    let raw = Script.storage.getItem(CHECKPOINT_KEY + i);
    if (raw === null) return;
    let parts = raw.split("|");
    if (parts.length !== 3 || (parseInt(parts[0], 10) !== CHECKPOINT_VERSION && parseInt(parts[0], 10) !== 2) ||
        parseInt(parts[1], 10) !== timestamp) return;
    let record = parts[2].split(",");
    let id = idFromText(record[1]);
    if (id === null ||
        (record[0] !== "switch" && record[0] !== "pm1" && record[0] !== "cover")) continue;
    let entry = component(record[0], id);
    if (entry && record.length === 16) restoreData(record.slice(2), entry.data);
    else if (entry && record.length === 11) restoreData(record.slice(2), entry.data);
    else if (entry && record.length === 12 && record[2] === "") restoreData(record.slice(3), entry.data);
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

function reportBody(entry) {
  let d = entry.data;
  let v = round(average(d, VS, VC), 1);
  let c = round(average(d, CS, CC), 2);
  let p = round(average(d, PS, PC), 0);
  let f = round(average(d, FS, FC), 1);
  let e = d[EN] === null ? null : round(d[EN], 1);
  if (v === null && c === null && p === null && f === null && e === null) return null;
  if (!changed(v, d[SENT_V], THRESH_V) && !changed(c, d[SENT_C], THRESH_C) &&
      !changed(p, d[SENT_P], THRESH_P) && !changed(f, d[SENT_F], THRESH_F) &&
      !changed(e, d[SENT_E], THRESH_E)) return null;
  let body = "{\"dst\":\"powermon\",\"cid\":" + entry.id +
    ",\"comp\":\"" + entry.type + "\"";
  if (v !== null) body += ",\"voltage\":" + v;
  if (c !== null) body += ",\"current\":" + c;
  if (p !== null) body += ",\"apower\":" + p;
  if (e !== null) body += ",\"aenergy\":" + e;
  if (f !== null) body += ",\"freq\":" + f;
  return body + "}";
}

function sendReports() {
  refreshStatus();
  let queued = 0;
  for (let i = 0; i < components.length; i++) {
    let body = reportBody(components[i]);
    if (body === null) {
      clearSamples(components[i].data);
    } else if (putQueue(components[i].id + "|" + components[i].type + "|" + body)) {
      queued++;
      updateSent(components[i].data);
      clearSamples(components[i].data);
    }
  }
  print("PM report cycle: components=" + components.length + " queued=" + queued +
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
  if (errorCode !== 0) print("PM Hubitat POST failed: " + errorCode + " " + errorMessage);
  else print("PM Hubitat POST delivered");
  if (drainTimer === null) drainTimer = Timer.set(DRAIN_DELAY_MS, false, retryDrain);
}

function drainQueue() {
  if (!storageAvailable() || reportInFlight || queueCount() === 0) return;
  let record = takeQueue();
  if (record === null) return;
  let first = record.indexOf("|");
  let second = record.indexOf("|", first + 1);
  if (first < 0 || second < 0) return;
  let id = record.substring(0, first);
  let body = record.substring(second + 1);
  if (body.charAt(0) === "|") body = body.substring(1);
  inFlightRecord = record;
  reportInFlight = true;
  try {
    Shelly.call("HTTP.POST", {
      url: REMOTE_URL + "/webhook/powermon/" + id,
      body: body,
      content_type: "application/json",
      timeout: 10
    }, httpDone);
  } catch (e) {
    httpDone(null, -1, e);
  }
}

function readStatus(key) {
  if (typeof Shelly.getComponentStatus !== "function") return;
  try {
    let status = Shelly.getComponentStatus(key);
    if (status && typeof status === "object") addStatus(key, status);
    status = null;
  } catch (e) {
    print("PM status read failed: " + key + " " + e);
  }
}

function refreshStatus() {
  if (components.length === 0) {
    for (let p = 0; p < PREFIXES.length; p++) {
      for (let id = 0; id <= MAX_ID; id++) readStatus(PREFIXES[p] + ":" + id);
    }
    return;
  }
  for (let i = 0; i < components.length; i++) readStatus(components[i].key);
}

function addStatus(key, d) {
  let colon = key.indexOf(":");
  if (colon < 0) return;
  let type = key.substring(0, colon);
  let id = idFromText(key.substring(colon + 1));
  if (id === null || (type !== "switch" && type !== "pm1" && type !== "cover")) return;
  let entry = component(type, id);
  if (!entry) return;
  let data = entry.data;
  add(data, VS, VC, d.voltage);
  add(data, CS, CC, d.current);
  add(data, PS, PC, d.apower);
  add(data, FS, FC, d.freq);
  if (d.aenergy && number(d.aenergy.total)) data[EN] = d.aenergy.total;
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
    print("PM settings loaded: interval=" + REPORT_SECONDS + " thresholds=" +
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
  print("PM monitor started: version=" + VERSION);
}

start();
