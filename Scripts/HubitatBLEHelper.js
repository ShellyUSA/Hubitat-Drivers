// ==========================================
// Hubitat BLE Helper
// Memory revision: 2026-09-08
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

// Fixed byte widths for IDs 0x00..0x60; 0 means unknown/variable length.
// A string replaces 78 nested definition arrays, including unused field names.
let BTHOME_SIZES =
  "1122332221332221" + // 0x00..0x0F
  "1122211111111111" + // 0x10..0x1F
  "1111111111111111" + // 0x20..0x2F
  "0000000000102242" + // 0x30..0x3F
  "2232221222234444" + // 0x40..0x4F
  "4220042000000000" + // 0x50..0x5F
  "1"; // 0x60..0x60
let MAX_SERVICE_DATA = 255;
let MAX_MODEL_NAME = 32;

function normalizeMac(raw) {
  if (typeof raw !== "string") return null;
  let normalized = "";
  for (let i = 0; i < raw.length; i++) {
    let ch = raw.charAt(i);
    if (ch !== ":") normalized = normalized + ch;
  }
  return normalized.toUpperCase();
}

// === Internal state ===
// Per-MAC packet/model state is bounded because random BLE addresses can rotate.
let deviceCache = {};
let deviceCacheKeys = [];
let MAX_CACHED_DEVICES = 32;
let DEVICE_CACHE_TTL_MS = 30 * 60 * 1000;

// === Concurrency control ===
let httpInFlight = false;    // Exactly one HTTP.POST may be active at a time
let HTTP_TIMEOUT_SECONDS = 10;
let pendingBatch = [];      // Serialized reports, never advertisement/decoder objects
let pendingBytes = 0;
let MAX_PENDING = 8;
let MAX_PENDING_BYTES = 2048;
let MAX_REPORT_BYTES = 768;
let MAX_POST_BYTES = 1024;   // Includes envelope and commas
let droppedReports = 0;

// Only completion of the native RPC frees the slot. A JS watchdog cannot
// cancel HTTP.POST and must never authorize an overlapping request.
function onHTTPResponse(result, error_code, error_message) {
  httpInFlight = false;
  if (error_code !== 0) print("BLE HTTP error:", error_code, error_message);
  // The drain timer sends after this callback and its response have unwound.
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
function bthomeFieldName(objId) {
  if (objId === 0x00) return "pid";
  if (objId === 0x01) return "battery";
  if (objId === 0x02 || objId === 0x45) return "temperature";
  if (objId === 0x03 || objId === 0x2E) return "humidity";
  if (objId === 0x05) return "illuminance";
  if (objId === 0x21) return "motion";
  if (objId === 0x2D) return "window";
  if (objId === 0x3A) return "button";
  if (objId === 0x3C) return "dimmer";
  if (objId === 0x3F) return "rotation";
  if (objId === 0x40) return "distanceMm";
  if (objId === 0x60) return "channel";
  if (objId === 0xF0) return "device_type_id";
  return null;
}

function addDecodedValue(result, name, value) {
  if (!name || typeof value === "undefined" || value === null) return;
  if (typeof result[name] === "undefined") {
    result[name] = value;
  } else if (typeof result[name] === "object" && result[name].length !== undefined) {
    result[name].push(value);
  } else {
    result[name] = [result[name], value];
  }
}

// Decode only forwarded fields directly into one object. The native parser
// materializes an array of rich objects even for fields we discard.
function decodeBTHome(buffer) {
  if (typeof buffer !== "string" || buffer.length === 0 ||
      buffer.length > MAX_SERVICE_DATA || (buffer.at(0) & 0xE1) !== 0x40) return null;
  let result = {};
  let position = 1;
  while (position < buffer.length) {
    let id = buffer.at(position++);
    let size = id < BTHOME_SIZES.length ? BTHOME_SIZES.at(id) - 48 : 0;
    if (id === 0xF0) size = 2;
    if (id === 0xF1) size = 4;
    if (id === 0xF2) size = 3;
    // Unknown or truncated objects terminate decoding, as in the legacy path.
    if (size === 0 || position + size > buffer.length) break;
    let name = bthomeFieldName(id);
    if (name !== null) {
      let value = buffer.at(position);
      if (size >= 2) value = value | (buffer.at(position + 1) << 8);
      if (size === 3) value = value | (buffer.at(position + 2) << 16);
      if (id === 0x02 || id === 0x45 || id === 0x3F) {
        if (value & 0x8000) value = value - 65536;
      }
      if (id === 0x02 || id === 0x03 || id === 0x05) value = value * 0.01;
      if (id === 0x45 || id === 0x3F) value = value * 0.1;
      addDecodedValue(result, name, value);
    }
    position = position + size;
  }
  return result;
}

// === Shelly Manufacturer Data Parser ===
let SHELLY_MFID = 0x0BA9;

// Block type -> payload size (bytes)
let SHELLY_TLV_SIZES = {};
SHELLY_TLV_SIZES[0x01] = 2;   // flags
SHELLY_TLV_SIZES[0x0A] = 6;   // MAC address
SHELLY_TLV_SIZES[0x0B] = 2;   // model ID (uint16 LE)

/**
 * Extract Shelly numeric model ID from BLE manufacturer-specific data.
 * Parses TLV blocks for type 0x0B (model ID).
 * Returns -1 if not found or API not available.
 */
function getShellyModelId(advData) {
  if (typeof advData !== "string" || advData.length === 0) return -1;
  if (typeof BLE === "undefined" || typeof BLE.GAP === "undefined") return -1;
  if (typeof BLE.GAP.parseManufacturerDataByVendor !== "function") return -1;

  let mfData = BLE.GAP.parseManufacturerDataByVendor(advData, SHELLY_MFID);
  if (typeof mfData !== "string" || mfData.length === 0) return -1;

  let pos = 0;
  while (pos < mfData.length) {
    let blockType = mfData.at(pos);
    pos = pos + 1;
    let blockSize = SHELLY_TLV_SIZES[blockType];
    // Break on unknown type: without knowing its size we cannot safely skip it.
    // Falls back to BTHome device_type_id or local_name identification layers.
    if (typeof blockSize === "undefined") break;
    if (pos + blockSize > mfData.length) break;
    if (blockType === 0x0B && blockSize === 2) {
      return 0xFFFF & ((mfData.at(pos + 1) << 8) | mfData.at(pos));
    }
    pos = pos + blockSize;
  }
  return -1;
}

// === Bounded device cache ===
function getNowMs() {
  if (typeof Shelly.getUptimeMs === "function") return Shelly.getUptimeMs();
  return Date.now();
}

function removeDeviceCacheKey(mac) {
  for (let i = 0; i < deviceCacheKeys.length; i++) {
    if (deviceCacheKeys[i] === mac) {
      for (let j = i + 1; j < deviceCacheKeys.length; j++) {
        deviceCacheKeys[j - 1] = deviceCacheKeys[j];
      }
      deviceCacheKeys.length = deviceCacheKeys.length - 1;
      return;
    }
  }
}

function pruneDeviceCache(now) {
  let writeIndex = 0;
  for (let i = 0; i < deviceCacheKeys.length; i++) {
    let mac = deviceCacheKeys[i];
    let entry = deviceCache[mac];
    if (entry && now - entry.lastSeen <= DEVICE_CACHE_TTL_MS) {
      deviceCacheKeys[writeIndex] = mac;
      writeIndex++;
    } else {
      delete deviceCache[mac];
    }
  }
  deviceCacheKeys.length = writeIndex;
}

function getDeviceCacheEntry(mac, now, create) {
  let entry = deviceCache[mac];
  if (entry && now - entry.lastSeen > DEVICE_CACHE_TTL_MS) {
    delete deviceCache[mac];
    removeDeviceCacheKey(mac);
    entry = undefined;
  }

  if (!entry && create) {
    pruneDeviceCache(now);
    if (deviceCacheKeys.length >= MAX_CACHED_DEVICES) {
      let oldestIndex = 0;
      let oldestSeen = deviceCache[deviceCacheKeys[0]].lastSeen;
      for (let i = 1; i < deviceCacheKeys.length; i++) {
        let candidate = deviceCache[deviceCacheKeys[i]];
        if (candidate.lastSeen < oldestSeen) {
          oldestIndex = i;
          oldestSeen = candidate.lastSeen;
        }
      }
      let oldestMac = deviceCacheKeys[oldestIndex];
      delete deviceCache[oldestMac];
      deviceCacheKeys[oldestIndex] = deviceCacheKeys[deviceCacheKeys.length - 1];
      deviceCacheKeys.length = deviceCacheKeys.length - 1;
    }
    entry = { hasPid: false, pid: -1, modelId: -1, modelStr: "", lastSeen: now };
    deviceCache[mac] = entry;
    deviceCacheKeys.push(mac);
  }

  if (entry) entry.lastSeen = now;
  return entry;
}

// === Dedup: check if pid is new for this MAC ===
function isNewPid(mac, pid, now) {
  let entry = getDeviceCacheEntry(mac, now, true);
  if (entry.hasPid && entry.pid === pid) return false;
  entry.pid = pid;
  entry.hasPid = true;
  return true;
}

// === HTTP POST with concurrency control ===
function dropOldestPendingReport() {
  pendingBytes = pendingBytes - pendingBatch[0].length;
  for (let i = 1; i < pendingBatch.length; i++) {
    pendingBatch[i - 1] = pendingBatch[i];
  }
  pendingBatch.pop();
}

// Called only by one repeating timer, outside BLE and HTTP callback stacks.
function drainPendingBatch() {
  if (httpInFlight || pendingBatch.length === 0) return;
  let body = '{"dst":"ble","messages":[';
  let count = 0;
  while (pendingBatch.length > 0 &&
         body.length + pendingBatch[0].length + 3 <= MAX_POST_BYTES) {
    if (count > 0) body = body + ",";
    body = body + pendingBatch[0];
    dropOldestPendingReport();
    count++;
  }
  body = body + "]}";
  httpInFlight = true;
  try {
    Shelly.call("HTTP.POST", {
      url: REMOTE_URL + "/webhook/ble/0",
      body: body,
      content_type: "application/json",
      timeout: HTTP_TIMEOUT_SECONDS,
    }, onHTTPResponse);
  } catch (e) {
    httpInFlight = false;
    print("BLE HTTP request failed:", e);
  }
}

// Count AND byte bounds protect against large repeated fields or local names.
// Preserve FIFO button events; under sustained overload, discard oldest first.
function sendBleReport(data) {
  let report = JSON.stringify(data);
  if (report.length > MAX_REPORT_BYTES) {
    droppedReports++;
    return;
  }
  while (pendingBatch.length >= MAX_PENDING ||
         pendingBytes + report.length > MAX_PENDING_BYTES) {
    dropOldestPendingReport();
    droppedReports++;
  }
  pendingBatch.push(report);
  pendingBytes = pendingBytes + report.length;
}

// === BLE Scanner Callback ===
function BLEScanCallback(event, result) {
  try {
    if (event === BLE.Scanner.SCAN_START) {
      print("BLE scanner started");
      return;
    }
    if (event === BLE.Scanner.SCAN_STOP) {
      print("BLE scanner stopped");
      return;
    }
    if (event !== BLE.Scanner.SCAN_RESULT || !result) return;
    // Must have BTHome v2 service data
    if (
      !result.service_data ||
      typeof result.service_data[BTHOME_SVC_ID] === "undefined"
    ) {
      return;
    }

    let serviceData = result.service_data[BTHOME_SVC_ID];
    if (typeof serviceData !== "string" || serviceData.length === 0 ||
        serviceData.length > MAX_SERVICE_DATA || (serviceData.at(0) & 0xE1) !== 0x40) return;
    // Get MAC address (uppercase, no colons)
    let mac = normalizeMac(result.addr);
    if (mac === null) return;

    let now = getNowMs();
    // BTHome puts PID (object 0x00) first. Drop repeated broadcasts before
    // allocating a decoded object, arrays, or manufacturer data.
    let cacheEntry = getDeviceCacheEntry(mac, now, false);
    if (serviceData.length >= 3 && serviceData.at(1) === 0x00 &&
        cacheEntry && cacheEntry.hasPid && cacheEntry.pid === serviceData.at(2)) return;
    let decoded = decodeBTHome(serviceData);
    if (decoded === null) return;
    let pid = typeof decoded.pid === "number" ? decoded.pid : -1;
    // PID is optional: missing PID must not suppress all future sensor updates.
    if (pid >= 0 && !isNewPid(mac, pid, now)) return;

    // Reuse the decoded object instead of copying every field into a second one.
    let body = decoded;
    body.cid = 0;
    body.pid = pid;
    body.mac = mac;

    // === Model identification (priority: mfData > BTHome device_type_id > cache) ===
    let modelId = -1;
    let modelStr = "";

    // Layer 1: Manufacturer data (most reliable)
    let hasAdvData = typeof result.advData === "string" && result.advData.length > 0;
    if (hasAdvData) {
      modelId = getShellyModelId(result.advData);
    }

    // Layer 2: BTHome device_type_id
    let hasDTID = typeof decoded.device_type_id !== "undefined";
    if (modelId < 0 && hasDTID) {
      modelId = decoded.device_type_id;
    }

    // Passive scanning does not guarantee a local_name. Preserve it when a
    // scan manager or another scan request provides one.
    if (typeof result.local_name === "string" && result.local_name.length > 0) {
      modelStr = result.local_name.slice(0, MAX_MODEL_NAME);
    }

    // Layer 3: bounded cache - reuse identification from a previous advertisement
    // Some devices only include identification in some advertisements.
    cacheEntry = getDeviceCacheEntry(mac, now, true);
    if (modelId < 0 && !modelStr && cacheEntry) {
      modelId = cacheEntry.modelId;
      modelStr = cacheEntry.modelStr;
    }

    // Update the bounded cache when identification is found.
    if (cacheEntry && (modelId >= 0 || modelStr)) {
      cacheEntry.modelId = modelId;
      cacheEntry.modelStr = modelStr;
    }

    // Send numeric model ID if found
    if (modelId >= 0) {
      body.modelId = modelId;
    }

    // Send string model if found
    if (modelStr) {
      body.model = modelStr;
    }

    // Add RSSI
    if (typeof result.rssi === "number") {
      body.rssi = result.rssi;
    }

    delete body.device_type_id;
    sendBleReport(body);
  } catch (e) {
    print("BLE scan callback error:", e);
  }
}

// === Initialization ===
function init() {
  let BLEConfig = Shelly.getComponentConfig("ble");
  // Since firmware 2.0, BLEConfig.enable no longer exists. Bluetooth scanning
  // is activated automatically when a script submits a scan request; only
  // BLEConfig.rpc.enable controls Bluetooth RPC/device control.
  let rpcEnabled = BLEConfig && BLEConfig.rpc && BLEConfig.rpc.enable === true;
  print("BLE config: rpc.enable=" + rpcEnabled +
    " scannerRunning=" + BLE.Scanner.isRunning());

  // Every script must submit its own request to the enhanced scan manager.
  // Filter to unencrypted BTHome v2 advertisements before they reach the JS
  // callback. Passive, duty-cycled scanning avoids scan-response allocations.
  let started = null;
  if (typeof BLE.Scanner.start === "function") {
    let scanOptions = {
      duration_ms: BLE.Scanner.INFINITE_SCAN,
      active: false,
      interval_ms: 1000,
      window_ms: 50,
      filters: [
        {
          serviceData: {
            service: BTHOME_SVC_ID,
            dataPrefix: "\x40",
            mask: "\xE1",
          },
        },
      ],
    };
    started = BLE.Scanner.start(scanOptions);
    if (!started) {
      // Keep a safe passive fallback for firmware that exposes start() but
      // predates serviceData filters.
      delete scanOptions.filters;
      started = BLE.Scanner.start(scanOptions);
    }
  } else if (typeof BLE.Scanner.Start === "function") {
    // Compatibility fallback for older Gen2 firmware.
    started = BLE.Scanner.Start({
      duration_ms: BLE.Scanner.INFINITE_SCAN,
      active: false,
    });
  }
  if (!started && !BLE.Scanner.isRunning()) {
    print("Error: Cannot start BLE scanner");
    return;
  }

  if (typeof BLE.Scanner.subscribe === "function") {
    BLE.Scanner.subscribe(BLEScanCallback);
  } else if (typeof BLE.Scanner.Subscribe === "function") {
    // Compatibility fallback for older Gen2 firmware.
    BLE.Scanner.Subscribe(BLEScanCallback);
  } else {
    print("Error: BLE scanner subscription API unavailable");
    return;
  }
  Timer.set(100, true, drainPendingBatch);
}

// Initialize hub URL from KVS
fetchRemoteUrlFromKVS();

// Start BLE scanning
init();

print(
  "Hubitat BLE Helper started: url=" + REMOTE_URL + " maxInflight=1",
);
