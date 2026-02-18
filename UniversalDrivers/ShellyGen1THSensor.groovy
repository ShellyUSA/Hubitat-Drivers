/**
 * Shelly Gen1 TH Sensor
 *
 * Pre-built standalone driver for Gen 1 Shelly H&T (SHHT-1) temperature/humidity sensor.
 * Battery-powered device that sleeps most of the time and wakes briefly to send reports.
 *
 * The device fires {@code report_url} on wake, which triggers the app to immediately
 * poll {@code GET /status} for real sensor data (temperature, humidity, battery).
 * Threshold action URLs ({@code over_temp_url}, {@code under_temp_url}, {@code over_hum_url},
 * {@code under_hum_url}) also trigger a status poll when exceeded.
 *
 * User-configurable preferences (offsets, thresholds) are synced bidirectionally:
 * device-to-driver on first refresh, driver-to-device on Save Preferences (queued
 * for next wake-up if the device is asleep).
 *
 * Version: 1.1.0
 */

metadata {
  definition(name: 'Shelly Gen1 TH Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'RelativeHumidityMeasurement'
    //Attributes: humidity - NUMBER, unit:%

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
    attribute 'powerSource', 'string'  // "battery" or "usb"
  }
}

preferences {
  // -- Sensor Calibration (synced to device) --
  input name: 'temperatureOffset', type: 'decimal', title: 'Temperature offset',
    description: 'Calibration offset for temperature. Synced to/from device.', required: false
  input name: 'humidityOffset', type: 'decimal', title: 'Humidity offset',
    description: 'Calibration offset for humidity. Synced to/from device.', required: false

  // -- Reporting Thresholds (synced to device) --
  input name: 'temperatureThreshold', type: 'decimal', title: 'Temperature threshold',
    description: 'Min temperature change to trigger report (0.5\u20135.0). Synced to/from device.',
    range: '0.5..5.0', required: false
  input name: 'humidityThreshold', type: 'decimal', title: 'Humidity threshold',
    description: 'Min humidity change to trigger report (0.5\u20135.0). Synced to/from device.',
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
 * Initializes defaults and requests a status refresh.
 */
void installed() {
  logDebug('installed() called')
  initialize()
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
 * Initializes the device with default attribute values and requests a status refresh.
 */
void initialize() {
  logDebug('initialize() called')
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
  parent?.componentRefresh(device)
}

/**
 * Re-reads configuration from the physical device by clearing the sync flag.
 * The next refresh will re-sync device settings to driver preferences.
 */
void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
  // Clear sync flag so next refresh re-reads settings from device
  device.removeDataValue('gen1SettingsSynced')
  parent?.componentRefresh(device)
}

/**
 * Refreshes the device state. Note: battery device may be asleep.
 * Data will update on next wake-up (report_url callback).
 */
void refresh() {
  logDebug('refresh() called — note: battery device may be asleep')
  parent?.componentRefresh(device)
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
  if (settings.humidityOffset != null) {
    settingsMap.humidity_offset = (settings.humidityOffset as BigDecimal).toString()
  }
  if (settings.temperatureThreshold != null) {
    settingsMap.temperature_threshold = (settings.temperatureThreshold as BigDecimal).toString()
  }
  if (settings.humidityThreshold != null) {
    settingsMap.humidity_threshold = (settings.humidityThreshold as BigDecimal).toString()
  }
  if (settingsMap) {
    logDebug("Relaying device settings to parent: ${settingsMap}")
    parent?.componentUpdateGen1Settings(device, settingsMap)
  }
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly H&T.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

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
 * GET Action Webhooks encode state in the path (e.g., /temperature/0).
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
 * Routes Gen 1 action URL callbacks for H&T sensor.
 * On sensor_report (report_url wake-up), triggers immediate refresh to read data
 * before the device goes back to sleep. Threshold callbacks also trigger refresh.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'sensor_report':
      logInfo('Sensor wake-up report received — requesting status poll')
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

    case 'hum_over':
      logWarn('Over-humidity threshold exceeded')
      parent?.componentRefresh(device)
      break
    case 'hum_under':
      logWarn('Under-humidity threshold exceeded')
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
 * Handles temperature, humidity, battery, and power source (USB/battery).
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

    if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}",
          descriptionText: "Temperature is ${temp}\u00B0${scale}")
        logInfo("Temperature: ${temp}\u00B0${scale}")
      }
    } else if (key.startsWith('humidity:')) {
      if (data.value != null) {
        BigDecimal humidity = data.value as BigDecimal
        sendEvent(name: 'humidity', value: humidity, unit: '%',
          descriptionText: "Humidity is ${humidity}%")
        logInfo("Humidity: ${humidity}%")
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
