// ==========================================
// BLU TRV Status Reporter
// ==========================================
// Runs on a Shelly BLU Gateway Gen3 to report
// BLU TRV thermostat state to a Hubitat hub.
// Reports immediately on state change and
// periodically as a heartbeat.
//
// Sends: current temperature, target setpoint,
// valve position, battery %, window open state.
// ==========================================

// === USER CONFIGURATION ===
let HUBITAT_KVS_KEY = "hubitat_sdm_ip";
let HUBITAT_DEFAULT_IP = "192.168.1.4";
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
let MIN_INTERVAL = 300; // Minimum heartbeat interval (seconds)
let MAX_INTERVAL = 420; // Maximum heartbeat interval (seconds)

// === Internal state ===
let timerHandle = null;
let knownTrvIds = [];

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

// === KVS URL Lookup ===
function buildRemoteUrlFromRaw(raw) {
  if (!raw || typeof raw !== "string")
    return HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
  let s = raw.trim();
  if (s.indexOf("http://") === 0 || s.indexOf("https://") === 0) {
    let hostStart = s.indexOf("://") + 3;
    let hostPart = s.slice(hostStart);
    return hostPart.indexOf(":") === -1 ? s + ":" + HUBITAT_PORT : s;
  }
  if (s.indexOf(":") !== -1) return HUBITAT_PROTO + s;
  return HUBITAT_PROTO + s + ":" + HUBITAT_PORT;
}

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

// === TRV Status Reporting ===

// Send TRV state to Hubitat via POST with JSON body.
// The driver's handlePostWebhook() extracts dst/cid from the body
// and routes to buildBluTrvWebhookEvents() which expects:
//   tC = current temperature (Celsius)
//   target = target setpoint (Celsius)
//   pos = valve position (0-100)
//   batt = battery percentage
function sendTrvReport(cid, data) {
  let url = REMOTE_URL + "/webhook/blutrv_status_change/" + JSON.stringify(cid);
  let body = {
    dst: "blutrv_status_change",
    cid: cid,
  };

  // Map BluTrv status fields to the driver's expected param names
  if (data.current_C !== undefined && data.current_C !== null) body.tC = data.current_C;
  if (data.target_C !== undefined && data.target_C !== null) body.target = data.target_C;
  if (data.pos !== undefined && data.pos !== null) body.pos = data.pos;
  if (data.battery !== undefined && data.battery !== null) body.batt = data.battery;

  Shelly.call(
    "HTTP.POST",
    { url: url, body: JSON.stringify(body), content_type: "application/json" },
    onHTTPResponse,
  );

  print("TRV report:", url, JSON.stringify(body));
}

// Extract blutrv component ID from component name (e.g. "blutrv:200")
function getTrvId(comp, fallbackId) {
  if (typeof fallbackId === "number") return fallbackId;
  if (typeof comp === "string" && comp.indexOf("blutrv:") === 0) {
    let parsed = parseInt(comp.slice(7), 10);
    if (!isNaN(parsed)) return parsed;
  }
  return -1;
}

// Track a newly discovered TRV ID
function trackTrvId(cid) {
  if (cid < 0) return;
  for (let i = 0; i < knownTrvIds.length; i++) {
    if (knownTrvIds[i] === cid) return;
  }
  knownTrvIds.push(cid);
}

// === Timer ===
function resetTimer() {
  if (timerHandle !== null) {
    Timer.clear(timerHandle);
  }
  let interval = randomInterval();
  timerHandle = Timer.set(interval * 1000, false, sendHeartbeat);
}

// === Heartbeat ===
// BluTrv components are dynamic and NOT included in Shelly.GetStatus.
// Discover them via Shelly.GetComponents, then poll each via BluTrv.GetStatus.

function sendHeartbeat() {
  timerHandle = null;
  discoverAndPollTrvs();
}

function discoverAndPollTrvs() {
  Shelly.call(
    "Shelly.GetComponents",
    { dynamic_only: true },
    function (res, err) {
      if (err !== 0 || !res || !res.components) {
        print("GetComponents error:", err);
        resetTimer();
        return;
      }

      let trvCount = 0;
      for (let i = 0; i < res.components.length; i++) {
        let comp = res.components[i];
        if (comp.key && comp.key.indexOf("blutrv:") === 0) {
          let cid = parseInt(comp.key.slice(7), 10);
          if (!isNaN(cid)) {
            trackTrvId(cid);
            pollTrv(cid);
            trvCount++;
          }
        }
      }

      if (trvCount === 0) {
        print("No blutrv components found");
      } else {
        print("Polled " + JSON.stringify(trvCount) + " TRV(s)");
      }

      resetTimer();
    },
  );
}

function pollTrv(cid) {
  Shelly.call("BluTrv.GetStatus", { id: cid }, function (res, err) {
    if (err !== 0 || !res) {
      print("BluTrv.GetStatus error for " + JSON.stringify(cid) + ":", err);
      return;
    }
    sendTrvReport(cid, res);
  });
}

// === Status Handler: real-time TRV state changes ===
// The gateway fires status events for blutrv:NNN components when the
// paired TRV reports changes over BLE (temperature, position, target, etc.)
Shelly.addStatusHandler(function (status) {
  if (status === undefined || status === null) return;
  let comp = status.component;
  if (typeof comp !== "string" || comp.indexOf("blutrv:") !== 0) return;

  let delta = status.delta;
  if (delta === undefined || delta === null) return;

  // Only report if a meaningful field changed
  if (
    delta.current_C === undefined &&
    delta.target_C === undefined &&
    delta.pos === undefined &&
    delta.battery === undefined
  ) {
    return;
  }

  let cid = getTrvId(comp, status.id);
  if (cid < 0) return;

  trackTrvId(cid);
  sendTrvReport(cid, delta);
  resetTimer();
});

// === Initialization ===
fetchRemoteUrlFromKVS();
resetTimer();

// Initial discovery and poll on startup (after short delay for KVS to resolve)
Timer.set(3000, false, function () {
  discoverAndPollTrvs();
});

print(
  "BLU TRV status reporter started: interval=" +
    JSON.stringify(MIN_INTERVAL) +
    "-" +
    JSON.stringify(MAX_INTERVAL) +
    "s url=" +
    REMOTE_URL,
);
