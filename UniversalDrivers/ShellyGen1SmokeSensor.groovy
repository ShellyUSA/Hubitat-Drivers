/**
 * Shelly Gen1 Smoke Sensor (SHSM-01)
 *
 * Pre-built standalone driver for the Gen 1 Shelly Smoke sensor.
 * Battery-powered device that sleeps most of the time.
 *
 * Wakes and fires action URL callbacks on:
 *   - Smoke detected / smoke cleared (smoke_detected_url / smoke_cleared_url)
 *   - Temperature threshold crossings (over_temp_url / under_temp_url)
 *   - Periodic sensor report (report_url — bare wake-up trigger, data ignored)
 *
 * Note: report_url is used as a wake-up notification only (same pattern as H&T).
 * The query-parameter data is ignored; the callback simply triggers GET /status.
 *
 * User-configurable preferences (temperature_offset, temperature_threshold)
 * are synced bidirectionally: device-to-driver on first refresh, driver-to-device
 * on Save Preferences (queued for next wake-up if the device is asleep).
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Smoke Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'SmokeDetector'
    //Attributes: smoke - ENUM ["detected", "clear", "tested"]

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    attribute 'lastUpdated', 'string'
    attribute 'powerSource', 'string'
  }
}

preferences {
  // -- Sensor Calibration (synced to device) --
  input name: 'temperatureOffset', type: 'decimal', title: 'Temperature offset',
    description: 'Calibration offset for temperature. Synced to/from device.', required: false
  input name: 'temperatureThreshold', type: 'decimal', title: 'Temperature threshold',
    description: 'Min temperature change to trigger report (0.5\u20135.0). Synced to/from device.',
    range: '0.5..5.0', required: false

  // -- Logging --
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Sets default log level if not already configured.
 */
void installed() {
  logDebug('installed() called')
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Called when device settings are saved.
 * Relays user-changed preferences to the physical device (or queues for next wake-up).
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
  relayDeviceSettings()
}

/**
 * Gathers device-side settings and sends them to the parent app for
 * relay to the Shelly device via GET /settings.
 * Only sends settings that have been configured (non-null).
 * For sleepy devices, the app will queue settings if the device is unreachable.
 */
private void relayDeviceSettings() {
  Map settingsMap = [:]
  if (settings.temperatureOffset != null) {
    settingsMap.temperature_offset = (settings.temperatureOffset as BigDecimal).toString()
  }
  if (settings.temperatureThreshold != null) {
    settingsMap.temperature_threshold = (settings.temperatureThreshold as BigDecimal).toString()
  }
  if (settingsMap) {
    logDebug("Relaying device settings to parent: ${settingsMap}")
    parent?.componentUpdateGen1Settings(device, settingsMap)
  }
}

void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }
    checkAndUpdateSourceIp(msg)

    Map params = parseWebhookPath(msg)
    if (params?.dst) {
      logDebug("Action URL callback dst=${params.dst}, cid=${params.cid}")
      routeActionUrlCallback(params)
    } else {
      logDebug("No dst found in action URL callback — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /smoke_detected/0).
 * Falls back to raw header string if parsed headers Map lacks the request line.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst and cid keys, or null if not parseable
 */
@CompileStatic
private Map parseWebhookPath(Map msg) {
  String requestLine = null

  // Primary: search parsed headers Map for request line
  if (msg?.headers) {
    requestLine = ((Map)msg.headers).keySet()?.find { Object key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
  }

  // Fallback: parse raw header string (singular msg.header)
  if (!requestLine && msg?.header) {
    String rawHeader = msg.header.toString()
    String[] lines = rawHeader.split('\n')
    for (String line : lines) {
      String trimmed = line.trim()
      if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
        requestLine = trimmed
        break
      }
    }
  }

  if (!requestLine) { return null }

  String[] requestParts = requestLine.split(' ')
  if (requestParts.length < 2) { return null }
  String pathAndQuery = requestParts[1]

  // Strip leading slash
  String webhookPath = pathAndQuery.startsWith('/') ? pathAndQuery.substring(1) : pathAndQuery
  if (!webhookPath) { return null }

  // Defensive: strip query string if somehow present
  int qMarkIdx = webhookPath.indexOf('?')
  if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }

  String[] segments = webhookPath.split('/')
  if (segments.length < 2) { return null }

  Map result = [dst: segments[0], cid: segments[1]]

  // Parse key/value pairs from remaining path segments
  for (int i = 2; i + 1 < segments.length; i += 2) {
    result[segments[i]] = segments[i + 1]
  }

  return result
}

/**
 * Converts a hex-encoded IP address string to dotted-decimal format.
 *
 * @param hex The 8-character hex string (e.g., "C0A80164")
 * @return Dotted-decimal IP (e.g., "192.168.1.100"), or null if invalid
 */
@CompileStatic
private static String convertHexToIP(String hex) {
  if (!hex || hex.length() != 8) { return null }
  return [Integer.parseInt(hex[0..1], 16),
          Integer.parseInt(hex[2..3], 16),
          Integer.parseInt(hex[4..5], 16),
          Integer.parseInt(hex[6..7], 16)].join('.')
}

/**
 * Checks the source IP of an incoming LAN message against the stored device IP.
 * If different, updates the device data value and notifies the parent app.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 */
private void checkAndUpdateSourceIp(Map msg) {
  String hexIp = msg?.ip
  if (!hexIp) { return }
  String sourceIp = convertHexToIP(hexIp)
  if (!sourceIp) { return }
  String storedIp = device.getDataValue('ipAddress')
  if (!storedIp || sourceIp == storedIp) { return }
  logWarn("Device IP changed: ${storedIp} -> ${sourceIp}")
  device.updateDataValue('ipAddress', sourceIp)
  parent?.componentNotifyIpChanged(device, storedIp, sourceIp)
}

/**
 * Routes Gen 1 action URL callbacks for smoke sensor.
 * Smoke events update the smoke attribute immediately.
 * All event types trigger a refresh to get full status (temperature, battery).
 * The sensor_report callback is a bare wake-up trigger (report_url).
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'sensor_report':
      logDebug('Sensor report wake-up received — refreshing status')
      parent?.componentRefresh(device)
      break

    case 'smoke_detected':
      sendEvent(name: 'smoke', value: 'detected', descriptionText: 'Smoke detected!')
      logWarn('Smoke detected!')
      parent?.componentRefresh(device)
      break
    case 'smoke_cleared':
      sendEvent(name: 'smoke', value: 'clear', descriptionText: 'Smoke cleared')
      logInfo('Smoke cleared')
      parent?.componentRefresh(device)
      break

    case 'temp_over':
      logWarn('Over-temperature threshold exceeded')
      parent?.componentRefresh(device)
      break
    case 'temp_under':
      logWarn('Under-temperature threshold exceeded')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
      return  // Don't update lastUpdated for unhandled callbacks
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝






// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 *
 * Handles three component types:
 *   - smoke:0 — alarm boolean → smoke attribute (detected/clear)
 *   - temperature:0 — tC/tF → temperature attribute
 *   - devicepower:0 — battery integer + charger boolean → battery + powerSource
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  String scale = location.temperatureScale ?: 'F'

  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    if (key.startsWith('smoke:')) {
      if (data.alarm != null) {
        String smokeState = data.alarm ? 'detected' : 'clear'
        sendEvent(name: 'smoke', value: smokeState, descriptionText: "Smoke sensor is ${smokeState}")
        logInfo("Smoke: ${smokeState}")
      }
    } else if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
        logInfo("Temperature: ${temp}°${scale}")
      }
    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
        logInfo("Battery: ${battery}%")
      }
      if (data.charger != null) {
        String source = data.charger ? 'usb' : 'battery'
        sendEvent(name: 'powerSource', value: source,
          descriptionText: "Power source is ${source}")
        logInfo("Power source: ${source}")
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    logTrace(JsonOutput.prettyPrint(JsonOutput.toJson(message)))
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
