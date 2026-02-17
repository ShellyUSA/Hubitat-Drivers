// ==========================================
// Hubitat BLE Helper
// ==========================================
// Runs on a Shelly Gen2+ device to relay
// BLE advertisements from Shelly BLU devices
// to a Hubitat hub via HTTP POST.
//
// Decodes BTHome v2 service data (UUID fcd2)
// and sends decoded sensor/button values
// as JSON to Hubitat port 39501.
// ==========================================

// === USER CONFIGURATION ===
let HUBITAT_KVS_KEY = "hubitat_sdm_ip";
let HUBITAT_DEFAULT_IP = "192.168.1.4";
let HUBITAT_PORT = 39501;
let HUBITAT_PROTO = "http://";
let REMOTE_URL = HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;

// === BTHome v2 Constants ===
let BTHOME_SVC_ID = "fcd2";

// BTHome data type sizes: 0=uint8, 1=int8, 2=uint16, 3=int16, 4=uint24, 5=int24
let DT_U8 = 0;
let DT_I8 = 1;
let DT_U16 = 2;
let DT_I16 = 3;
let DT_U24 = 4;
let DT_I24 = 5;

// BTHome object ID definitions: [name, dataType, factor]
// factor is applied as multiplication to raw value (0 = no factor)
let BTH = {};
BTH[0x00] = ["pid", DT_U8, 0];
BTH[0x01] = ["battery", DT_U8, 0];
BTH[0x02] = ["temperature", DT_I16, 0.01];
BTH[0x03] = ["humidity", DT_U16, 0.01];
BTH[0x05] = ["illuminance", DT_U24, 0.01];
BTH[0x21] = ["motion", DT_U8, 0];
BTH[0x2d] = ["window", DT_U8, 0];
BTH[0x2e] = ["humidity", DT_U8, 0];
BTH[0x3a] = ["button", DT_U8, 0];
BTH[0x3f] = ["rotation", DT_I16, 0.1];
BTH[0x45] = ["temperature", DT_I16, 0.1];

// === Internal state ===
// Per-MAC last packet ID for deduplication
let lastPids = {};

// HTTP response handler
function onHTTPResponse(result, error_code, error_message) {
  if (error_code !== 0) {
    print("BLE HTTP error:", error_code, error_message);
  }
}

// === KVS URL Lookup (same pattern as switchstatus.js) ===
function buildRemoteUrlFromRaw(raw) {
  if (!raw || typeof raw !== "string")
    return HUBITAT_PROTO + HUBITAT_DEFAULT_IP + ":" + HUBITAT_PORT;
  let s = raw.trim();
  if (s.indexOf("http://") === 0 || s.indexOf("https://") === 0) {
    // Find where the host starts (after "://")
    let hostStart = s.indexOf("://") + 3;
    let hostPart = s.slice(hostStart);
    // Only append port if host portion has no colon (no port already specified)
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
    print("KVS.Get failed; using REMOTE_URL=" + REMOTE_URL + " (" + e + ")");
  }
}

// === BTHome v2 Decoder ===
function getByteSize(type) {
  if (type === DT_U8 || type === DT_I8) return 1;
  if (type === DT_U16 || type === DT_I16) return 2;
  if (type === DT_U24 || type === DT_I24) return 3;
  return 255;
}

function utoi(num, bitsz) {
  let mask = 1 << (bitsz - 1);
  return num & mask ? num - (1 << bitsz) : num;
}

function getUInt8(buffer) {
  return buffer.at(0);
}

function getInt8(buffer) {
  return utoi(getUInt8(buffer), 8);
}

function getUInt16LE(buffer) {
  return 0xffff & ((buffer.at(1) << 8) | buffer.at(0));
}

function getInt16LE(buffer) {
  return utoi(getUInt16LE(buffer), 16);
}

function getUInt24LE(buffer) {
  return (
    0x00ffffff & ((buffer.at(2) << 16) | (buffer.at(1) << 8) | buffer.at(0))
  );
}

function getInt24LE(buffer) {
  return utoi(getUInt24LE(buffer), 24);
}

function getBufValue(type, buffer) {
  if (buffer.length < getByteSize(type)) return null;
  if (type === DT_U8) return getUInt8(buffer);
  if (type === DT_I8) return getInt8(buffer);
  if (type === DT_U16) return getUInt16LE(buffer);
  if (type === DT_I16) return getInt16LE(buffer);
  if (type === DT_U24) return getUInt24LE(buffer);
  if (type === DT_I24) return getInt24LE(buffer);
  return null;
}

/**
 * Decode BTHome v2 service data buffer into a result object.
 * Handles repeated object IDs (e.g., multiple buttons) by collecting into arrays.
 */
function decodeBTHome(buffer) {
  if (typeof buffer !== "string" || buffer.length === 0) return null;

  let dib = buffer.at(0);
  let encrypted = dib & 0x1 ? true : false;
  let version = dib >> 5;
  if (version !== 2) return null;
  if (encrypted) return null;

  buffer = buffer.slice(1);
  let result = {};

  while (buffer.length > 0) {
    let objId = buffer.at(0);
    let def = BTH[objId];
    if (typeof def === "undefined") break;

    buffer = buffer.slice(1);
    let raw = getBufValue(def[1], buffer);
    if (raw === null) break;

    let value = def[2] !== 0 ? raw * def[2] : raw;
    let name = def[0];

    // Handle repeated fields (multi-button devices)
    if (typeof result[name] === "undefined") {
      result[name] = value;
    } else {
      if (typeof result[name] === "object" && result[name].length !== undefined) {
        result[name].push(value);
      } else {
        result[name] = [result[name], value];
      }
    }

    buffer = buffer.slice(getByteSize(def[1]));
  }

  return result;
}

// === Dedup: check if pid is new for this MAC ===
function isNewPid(mac, pid) {
  if (typeof lastPids[mac] === "undefined") {
    lastPids[mac] = pid;
    return true;
  }
  if (lastPids[mac] === pid) return false;
  lastPids[mac] = pid;
  return true;
}

// === Send decoded BLE data to Hubitat ===
function sendBleReport(data) {
  let url = REMOTE_URL + "/webhook/ble/0";
  let body = JSON.stringify(data);

  Shelly.call(
    "HTTP.POST",
    { url: url, body: body, content_type: "application/json" },
    onHTTPResponse,
  );

  print("BLE report:", url, body);
}

// === BLE Scanner Callback ===
function BLEScanCallback(event, result) {
  if (event !== BLE.Scanner.SCAN_RESULT) return;

  // Must have BTHome v2 service data
  if (
    typeof result.service_data === "undefined" ||
    typeof result.service_data[BTHOME_SVC_ID] === "undefined"
  ) {
    return;
  }

  let decoded = decodeBTHome(result.service_data[BTHOME_SVC_ID]);
  if (decoded === null || typeof decoded === "undefined") return;

  // Get MAC address (uppercase, no colons)
  let mac = result.addr;
  if (typeof mac === "string") {
    mac = mac.toUpperCase();
    // Remove colons if present (e.g., "AA:BB:CC:DD:EE:FF" → "AABBCCDDEEFF")
    mac = mac.split(":").join("");
  } else {
    return;
  }

  // Dedup by pid per MAC
  let pid = typeof decoded.pid !== "undefined" ? decoded.pid : -1;
  if (!isNewPid(mac, pid)) return;

  // Build POST body with all decoded fields
  let body = {
    dst: "ble",
    cid: 0,
    pid: pid,
    mac: mac,
  };

  // Add model from local_name if available
  if (typeof result.local_name === "string" && result.local_name.length > 0) {
    body.model = result.local_name;
  }

  // Add RSSI
  if (typeof result.rssi === "number") {
    body.rssi = result.rssi;
  }

  // Copy all decoded BTHome fields (except pid which is already in body)
  for (let key in decoded) {
    if (key !== "pid") {
      body[key] = decoded[key];
    }
  }

  sendBleReport(body);
}

// === Initialization ===
function init() {
  let BLEConfig = Shelly.getComponentConfig("ble");
  if (!BLEConfig.enable) {
    print(
      "Error: Bluetooth is not enabled. Enable it from device settings.",
    );
    return;
  }

  if (BLE.Scanner.isRunning()) {
    print(
      "Info: BLE scanner already running, subscribing to scan results",
    );
  } else {
    let started = BLE.Scanner.Start({
      duration_ms: BLE.Scanner.INFINITE_SCAN,
      active: false,
    });
    if (!started) {
      print("Error: Cannot start BLE scanner");
      return;
    }
  }

  BLE.Scanner.Subscribe(BLEScanCallback);
}

// Initialize hub URL from KVS
fetchRemoteUrlFromKVS();

// Start BLE scanning
init();

print("Hubitat BLE Helper started: url=" + REMOTE_URL);
