// ==========================================
// Power Monitoring Collector & Reporter
// ==========================================
// Runs on a Shelly Gen2+ device to collect
// power readings from status events, average
// them over a configurable interval, and
// report aggregated data to a Hubitat hub.
// ==========================================

// === USER CONFIGURATION ===
let REPORT_INTERVAL = 60; // Seconds between reports to Hubitat
let REMOTE_URL = "http://192.168.1.4:39501"; // Hubitat hub IP:port

// === Internal accumulators ===
let voltages = [];
let currents = [];
let apowers = [];
let freqs = [];
let latestAenergy = null; // Most recent aenergy.total (Wh, cumulative)
let compName = null; // Component name from status events (e.g. "switch:0")
let compId = 0; // Component id number

// HTTP response handler
function onHTTPResponse(result, error_code, error_message) {
  if (error_code !== 0) {
    print("HTTP error:", error_code, error_message);
  }
}

// Compute average of a numeric array, rounded to 2 decimal places
function average(arr) {
  if (arr.length === 0) return null;
  let sum = 0;
  for (let i = 0; i < arr.length; i++) sum += arr[i];
  return Math.round((sum / arr.length) * 100) / 100;
}

// Status handler: collect power data from status change events
function onStatus(ev) {
  let d = ev.delta;
  if (d === undefined || d === null) return;

  let c = ev.component;
  if (c === undefined || c === null) return;

  // Only process power-capable components
  if (
    c.indexOf("switch") !== 0 &&
    c.indexOf("pm1") !== 0 &&
    c.indexOf("cover") !== 0
  )
    return;

  compName = c;
  if (ev.id !== undefined) compId = ev.id;

  if (typeof d.voltage === "number") voltages.push(d.voltage);
  if (typeof d.current === "number") currents.push(d.current);
  if (typeof d.apower === "number") apowers.push(d.apower);
  if (typeof d.freq === "number") freqs.push(d.freq);
  if (d.aenergy !== undefined && d.aenergy !== null) {
    if (typeof d.aenergy.total === "number") {
      latestAenergy = d.aenergy.total;
    }
  }
}

// Timer callback: average accumulated readings and send to Hubitat
function sendReport() {
  if (compName === null) {
    print("No power events received yet");
    return;
  }

  let v = average(voltages);
  let c = average(currents);
  let p = average(apowers);
  let f = average(freqs);

  if (v === null && c === null && p === null && f === null && latestAenergy === null) {
    print("No readings to report");
    return;
  }

  // Build the component update with only the fields we have
  let update = { id: compId };
  if (v !== null) update.voltage = v;
  if (c !== null) update.current = c;
  if (p !== null) update.apower = p;
  if (f !== null) update.freq = f;
  if (latestAenergy !== null) {
    update.aenergy = { total: latestAenergy };
  }

  // Wrap in the JSON structure the Hubitat driver expects:
  // { "dst": "...", "result": { "<component>": { ... } } }
  let result = {};
  result[compName] = update;

  let body = JSON.stringify({ dst: "powermon", result: result });

  Shelly.call(
    "HTTP.POST",
    { url: REMOTE_URL, body: body, content_type: "application/json" },
    onHTTPResponse
  );

  print("Reported:", body);

  // Reset averaged arrays; keep latestAenergy (it is cumulative)
  voltages = [];
  currents = [];
  apowers = [];
  freqs = [];
}

// Register status handler and reporting timer
Shelly.addStatusHandler(onStatus);
Timer.set(REPORT_INTERVAL * 1000, true, sendReport);

print(
  "Power monitor started: interval=" +
    JSON.stringify(REPORT_INTERVAL) +
    "s url=" +
    REMOTE_URL
);
