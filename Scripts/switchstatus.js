// ==========================================
// Switch Status Reporter
// ==========================================
// Runs on a Shelly Gen2+ device to report
// switch on/off state to a Hubitat hub.
// Reports immediately on state change and
// periodically as a heartbeat.
// ==========================================

// === USER CONFIGURATION ===
// Hubitat KVS configuration
let HUBITAT_KVS_KEY = "hubitat_ip"; // store only the IP (no protocol/port) in Shelly KVS
let HUBITAT_DEFAULT_IP = "192.168.1.4"; // fallback if KVS lookup fails
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
let MIN_INTERVAL = 300; // Minimum heartbeat interval (seconds)
let MAX_INTERVAL = 420; // Maximum heartbeat interval (seconds)

// === Internal state ===
let timerHandle = null;

// HTTP response handler
function onHTTPResponse(result, error_code, error_message) {
  if (error_code !== 0) {
    print("HTTP error:", error_code, error_message);
  }
}

// Random integer between min and max (inclusive)
function randomInterval() {
  return (
    MIN_INTERVAL + Math.floor(Math.random() * (MAX_INTERVAL - MIN_INTERVAL + 1))
  );
}

// Build a full URL from a KVS-stored IP (handles already-present protocol/port gracefully)
function buildRemoteUrlFromRaw(raw) {
  if (!raw || typeof raw !== "string")
    return HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
  let s = raw.trim();
  if (s.indexOf("http://") === 0 || s.indexOf("https://") === 0) {
    return s.indexOf(":") === -1 ? s + ":" + HUBITAT_PORT : s;
  }
  if (s.indexOf(":") !== -1) return HUBITAT_PROTO + s;
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

// Extract switch id from component name (e.g. "switch:0") with fallback
function getSwitchId(comp, fallbackId) {
  if (typeof fallbackId === "number") return fallbackId;
  if (typeof comp === "string" && comp.indexOf("switch:") === 0) {
    let parsed = parseInt(comp.slice(7), 10);
    if (!isNaN(parsed)) return parsed;
  }
  return 0;
}

// Send switch state for a single component via GET
function sendSwitchReport(id, output) {
  let url =
    REMOTE_URL +
    "/webhook/switchmon/" +
    JSON.stringify(id) +
    "?output=" +
    JSON.stringify(output);

  Shelly.call("HTTP.GET", { url: url }, onHTTPResponse);

  print("Reported:", url);
}

// Reset the heartbeat timer with a new random interval
function resetTimer() {
  if (timerHandle !== null) {
    Timer.clear(timerHandle);
  }
  let interval = randomInterval();
  timerHandle = Timer.set(interval * 1000, false, sendHeartbeat);
}

// Heartbeat: query and report all switch states
function sendHeartbeat() {
  timerHandle = null;
  Shelly.call("Shelly.GetStatus", {}, function (res, err, msg) {
    if (err !== 0 || res === undefined || res === null) {
      print("GetStatus error:", err, msg);
      resetTimer();
      return;
    }

    let found = false;
    for (let k in res) {
      if (k.indexOf("switch:") === 0) {
        let sw = res[k];
        if (sw !== undefined && sw !== null && typeof sw.output === "boolean") {
          let id = getSwitchId(k, sw.id);
          sendSwitchReport(id, sw.output);
          found = true;
        }
      }
    }

    if (!found) {
      print("No switch components found in status");
    }

    resetTimer();
  });
}

// Status handler: detect switch output transitions from status deltas
Shelly.addStatusHandler(function (status) {
  if (status === undefined || status === null) return;
  let comp = status.component;
  if (typeof comp !== "string" || comp.indexOf("switch:") !== 0) return;

  let delta = status.delta;
  if (delta === undefined || delta === null) return;
  if (typeof delta.output !== "boolean") return;

  let id = getSwitchId(comp, status.id);
  sendSwitchReport(id, delta.output);
  resetTimer();
});

// Event handler fallback for firmwares that emit switch events with output in event.info
Shelly.addEventHandler(function (event) {
  if (event === undefined || event === null) return;
  if (typeof event.name !== "string" || event.name.indexOf("switch") !== 0)
    return;
  if (event.info === undefined || event.info === null) return;
  if (typeof event.info.output !== "boolean") return;

  let comp =
    typeof event.component === "string" &&
    event.component.indexOf("switch:") === 0
      ? event.component
      : "switch:" + JSON.stringify(getSwitchId(null, event.id));
  let id = getSwitchId(comp, event.id);
  sendSwitchReport(id, event.info.output);
  resetTimer();
});

// Initialize REMOTE_URL from KVS (async)
fetchRemoteUrlFromKVS();

// Start first heartbeat
resetTimer();

print(
  "Switch status reporter started: interval=" +
    JSON.stringify(MIN_INTERVAL) +
    "-" +
    JSON.stringify(MAX_INTERVAL) +
    "s url=" +
    REMOTE_URL,
);
