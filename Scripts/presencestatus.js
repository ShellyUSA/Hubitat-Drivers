// ==========================================
// Presence G4 Status Reporter
// ==========================================
// Runs on a Shelly Presence G4 to report
// per-zone occupancy and illuminance to a
// Hubitat hub.
// Reports immediately on state change and
// periodically as a heartbeat.
// ==========================================

// === USER CONFIGURATION ===
let HUBITAT_KVS_KEY = "hubitat_sdm_ip";
let HUBITAT_DEFAULT_IP = "192.168.1.4";
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
let MIN_INTERVAL = 300;
let MAX_INTERVAL = 420;

// === Internal state ===
let timerHandle = null;
let reporterReady = false;
let knownZones = {};
let knownIlluminance = { lux: null, illumination: null };

function onHTTPResponse(result, error_code, error_message) {
  if (error_code !== 0) {
    print("HTTP error:", error_code, error_message);
  }
}

function randomInterval() {
  return (
    MIN_INTERVAL + Math.floor(Math.random() * (MAX_INTERVAL - MIN_INTERVAL + 1))
  );
}

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

function getZoneId(comp, fallbackId) {
  if (typeof fallbackId === "number") return fallbackId;
  if (typeof comp === "string" && comp.indexOf("presencezone:") === 0) {
    let parsed = parseInt(comp.slice(13), 10);
    if (!isNaN(parsed)) return parsed;
  }
  return -1;
}

function getZoneSnapshot(cid) {
  let key = JSON.stringify(cid);
  if (!knownZones[key]) {
    knownZones[key] = { state: null, numObjects: null };
  }
  return knownZones[key];
}

function updateZoneSnapshot(cid, stateValue, numObjects) {
  let zone = getZoneSnapshot(cid);
  if (stateValue !== undefined) zone.state = stateValue;
  if (numObjects !== undefined) zone.numObjects = numObjects;
  return zone;
}

function sendZoneReport(cid, zone) {
  let url = REMOTE_URL + "/webhook/presencezone/" + JSON.stringify(cid);
  let body = {
    dst: "presencezone",
    cid: cid,
  };

  if (zone.state !== undefined && zone.state !== null) body.state = zone.state;
  if (zone.numObjects !== undefined && zone.numObjects !== null)
    body.numObjects = zone.numObjects;

  Shelly.call(
    "HTTP.POST",
    { url: url, body: JSON.stringify(body), content_type: "application/json" },
    onHTTPResponse,
  );

  print("Zone report:", url, JSON.stringify(body));
}

function sendIlluminanceReport() {
  let url = REMOTE_URL + "/webhook/illuminance/0";
  let body = {
    dst: "illuminance",
    cid: 0,
  };

  if (knownIlluminance.lux !== undefined && knownIlluminance.lux !== null)
    body.lux = knownIlluminance.lux;
  if (
    knownIlluminance.illumination !== undefined &&
    knownIlluminance.illumination !== null
  )
    body.illumination = knownIlluminance.illumination;

  Shelly.call(
    "HTTP.POST",
    { url: url, body: JSON.stringify(body), content_type: "application/json" },
    onHTTPResponse,
  );

  print("Illuminance report:", url, JSON.stringify(body));
}

function resetTimer() {
  if (timerHandle !== null) {
    Timer.clear(timerHandle);
  }
  let interval = randomInterval();
  timerHandle = Timer.set(interval * 1000, false, sendHeartbeat);
}

function sendHeartbeat() {
  timerHandle = null;
  Shelly.call("Shelly.GetStatus", {}, function (res, err, msg) {
    if (err !== 0 || res === undefined || res === null) {
      print("GetStatus error:", err, msg);
      resetTimer();
      return;
    }

    let foundZones = 0;
    for (let k in res) {
      if (k.indexOf("presencezone:") === 0) {
        let zoneStatus = res[k];
        if (zoneStatus !== undefined && zoneStatus !== null) {
          let cid = getZoneId(k, zoneStatus.id);
          if (cid >= 0) {
            let zone = updateZoneSnapshot(
              cid,
              zoneStatus.state,
              zoneStatus.num_objects,
            );
            sendZoneReport(cid, zone);
            foundZones++;
          }
        }
      } else if (k === "illuminance:0") {
        let luxStatus = res[k];
        if (luxStatus !== undefined && luxStatus !== null) {
          if (luxStatus.lux !== undefined) knownIlluminance.lux = luxStatus.lux;
          if (luxStatus.illumination !== undefined)
            knownIlluminance.illumination = luxStatus.illumination;
          sendIlluminanceReport();
        }
      }
    }

    if (foundZones === 0) {
      print("No presencezone components found in status");
    }

    resetTimer();
  });
}

function handleStatusDelta(comp, fallbackId, delta) {
  if (!reporterReady) return;
  if (delta === undefined || delta === null) return;

  if (typeof comp === "string" && comp.indexOf("presencezone:") === 0) {
    if (delta.state === undefined && delta.num_objects === undefined) return;

    let cid = getZoneId(comp, fallbackId);
    if (cid < 0) return;

    let zone = updateZoneSnapshot(cid, delta.state, delta.num_objects);
    sendZoneReport(cid, zone);
    resetTimer();
    return;
  }

  if (comp === "illuminance:0") {
    if (delta.lux === undefined && delta.illumination === undefined) return;

    if (delta.lux !== undefined) knownIlluminance.lux = delta.lux;
    if (delta.illumination !== undefined)
      knownIlluminance.illumination = delta.illumination;

    sendIlluminanceReport();
    resetTimer();
  }
}

function handleStatusMap(statusMap) {
  if (statusMap === undefined || statusMap === null || typeof statusMap !== "object")
    return;

  for (let comp in statusMap) {
    let delta = statusMap[comp];
    if (delta === undefined || delta === null || typeof delta !== "object") continue;
    if (
      !(typeof comp === "string" && comp.indexOf("presencezone:") === 0) &&
      comp !== "illuminance:0"
    )
      continue;
    handleStatusDelta(comp, delta.id, delta);
  }
}

Shelly.addStatusHandler(function (status) {
  if (status === undefined || status === null) return;

  if (
    typeof status.component === "string" &&
    status.delta !== undefined &&
    status.delta !== null
  ) {
    handleStatusDelta(status.component, status.id, status.delta);
    return;
  }

  if (status.params !== undefined && status.params !== null) {
    handleStatusMap(status.params);
    return;
  }

  handleStatusMap(status);
});

function startReporter() {
  reporterReady = true;
  resetTimer();

  Timer.set(3000, false, function () {
    sendHeartbeat();
  });

  print(
    "Presence G4 status reporter started: interval=" +
      JSON.stringify(MIN_INTERVAL) +
      "-" +
      JSON.stringify(MAX_INTERVAL) +
      "s url=" +
      REMOTE_URL,
  );
}

fetchRemoteUrlFromKVS(startReporter);
