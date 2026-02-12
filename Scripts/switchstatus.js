// ==========================================
// Switch Status Reporter
// ==========================================
// Runs on a Shelly Gen2+ device to report
// switch on/off state to a Hubitat hub.
// Reports immediately on state change and
// periodically as a heartbeat.
// ==========================================

// === USER CONFIGURATION ===
let REMOTE_URL = "http://192.168.1.4:39501"; // Hubitat hub IP:port
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

// Extract switch id from component name (e.g. "switch:0") with fallback
function getSwitchId(comp, fallbackId) {
  if (typeof fallbackId === "number") return fallbackId;
  if (typeof comp === "string" && comp.indexOf("switch:") === 0) {
    let parsed = parseInt(comp.slice(7), 10);
    if (!isNaN(parsed)) return parsed;
  }
  return 0;
}

// Send switch state for a single component
function postSwitchState(comp, id, output) {
  let update = { id: id, output: output };
  let result = {};
  result[comp] = update;

  let body = JSON.stringify({ dst: "switchmon", result: result });

  Shelly.call(
    "HTTP.POST",
    { url: REMOTE_URL, body: body, content_type: "application/json" },
    onHTTPResponse,
  );

  print("Reported:", body);
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
          postSwitchState(k, id, sw.output);
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
  postSwitchState(comp, id, delta.output);
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
  postSwitchState(comp, id, event.info.output);
  resetTimer();
});

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
