/**
 * Shelly Gen1 Motion Sensor
 *
 * Pre-built standalone driver for Gen 1 Shelly Motion / Motion 2 (SHMOS-01, SHMOS-02).
 * Battery-powered PIR motion sensor that sleeps most of the time.
 *
 * Motion detection fires the {@code motion_url} action URL immediately on wake.
 * The app polls periodically to read illuminance, battery, and temperature.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Motion Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'MotionSensor'
    //Attributes: motion - ENUM ["inactive", "active"]

    capability 'IlluminanceMeasurement'
    //Attributes: illuminance - NUMBER, unit:lux

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'TamperAlert'
    //Attributes: tamper - ENUM ["clear", "detected"]

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
  }
}

preferences {
  // ── Hubitat-Side Settings ──
  input name: 'motionTimeout', type: 'number', title: 'Motion inactive timeout (seconds)',
    description: 'Hubitat-side timer. Seconds after motion before setting inactive (0 = use device timeout)',
    defaultValue: 60, range: '0..3600', required: true

  // ── Motion Detection (synced to device) ──
  input name: 'motionSensitivity', type: 'number',
    title: 'Motion sensitivity (1\u2013256)',
    description: 'PIR sensitivity \u2014 lower = more sensitive. Synced to/from device.',
    range: '1..256', required: false
  input name: 'motionBlindTimeMinutes', type: 'number',
    title: 'Motion blind time (minutes)',
    description: 'Cool-down between motion triggers. Synced to/from device.',
    range: '0..60', required: false

  // ── Tamper / Vibration (synced to device) ──
  input name: 'tamperSensitivity', type: 'number',
    title: 'Tamper/vibration sensitivity (0\u2013127)',
    description: '0 = disabled, lower = more sensitive. Synced to/from device.',
    range: '0..127', required: false

  // ── Device Configuration (synced to device) ──
  input name: 'ledStatusDisable', type: 'bool',
    title: 'Disable LED status indicator',
    description: 'Turn off the device status LED. Synced to/from device.',
    defaultValue: false, required: false
  input name: 'sleepTime', type: 'number',
    title: 'Sleep/wake-up interval (seconds)',
    description: 'How often device wakes to report. 0 = always awake. Synced to/from device.',
    range: '0..86400', required: false

  // ── Logging ──
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
  // If device-synced settings are still unpopulated, sync from device/cache first
  if (settings.motionSensitivity == null && settings.tamperSensitivity == null && settings.sleepTime == null) {
    logDebug('Device-synced settings not yet populated — requesting sync from device')
    device.removeDataValue('gen1SettingsSynced')
    parent?.componentRefresh(device)
  } else {
    relayDeviceSettings()
  }
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly Motion sensor.
 * Handles action URL callbacks for motion detection and sensor reports.
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
 * GET Action Webhooks encode state in the path (e.g., /motion_on/0).
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
 * Routes Gen 1 action URL callbacks for motion sensor events.
 * On motion detection, sets motion active and schedules inactive timeout.
 * On sensor report, triggers status poll to read lux/battery/temp.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'motion':
    case 'motion_on':
      setMotionActive()
      // Also poll for updated lux/battery/temp while device is awake
      parent?.componentRefresh(device)
      break

    case 'motion_off':
      setMotionInactive()
      break

    case 'tamper_alarm_on':
      sendEvent(name: 'tamper', value: 'detected', isStateChange: true,
        descriptionText: 'Tamper/vibration detected')
      logInfo('Tamper/vibration detected')
      parent?.componentRefresh(device)
      break

    case 'tamper_alarm_off':
      sendEvent(name: 'tamper', value: 'clear', isStateChange: true,
        descriptionText: 'Tamper/vibration cleared')
      logInfo('Tamper/vibration cleared')
      break

    case 'sensor_report':
      logInfo('Sensor wake-up report received — requesting status poll')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

void initialize() {
  logDebug('initialize() called')
  unschedule('setMotionInactive')
  sendEvent(name: 'motion', value: 'inactive', descriptionText: 'Initialized as inactive')
  sendEvent(name: 'tamper', value: 'clear', descriptionText: 'Initialized as clear')
}

void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
  if (settings.motionTimeout == null) {
    device.updateSetting('motionTimeout', 60)
  }
  // Clear sync flag so next refresh re-reads settings from device
  device.removeDataValue('gen1SettingsSynced')
  parent?.componentRefresh(device)
}

/**
 * Gathers device-side settings and sends them to the parent app for
 * relay to the Shelly device via GET /settings.
 * Only sends settings that have been configured (non-null).
 */
private void relayDeviceSettings() {
  Map settingsMap = [:]
  if (settings.motionSensitivity != null) {
    settingsMap.motion_sensitivity = (settings.motionSensitivity as Integer).toString()
  }
  if (settings.motionBlindTimeMinutes != null) {
    settingsMap.motion_blind_time_minutes = (settings.motionBlindTimeMinutes as Integer).toString()
  }
  if (settings.tamperSensitivity != null) {
    settingsMap.tamper_sensitivity = (settings.tamperSensitivity as Integer).toString()
  }
  if (settings.ledStatusDisable != null) {
    settingsMap.led_status_disable = settings.ledStatusDisable ? 'true' : 'false'
  }
  if (settings.sleepTime != null) {
    settingsMap.sleep_time = (settings.sleepTime as Integer).toString()
  }
  if (settingsMap) {
    logDebug("Relaying device settings to parent: ${settingsMap}")
    parent?.componentUpdateGen1Settings(device, settingsMap)
  }
}

/**
 * Refreshes the device state. Note: battery devices may be asleep.
 * Data will update on next wake-up (motion or report).
 */
void refresh() {
  logDebug('refresh() called — note: battery device may be asleep')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Motion State Management                                     ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets motion to active and schedules the inactive timeout.
 * If motionTimeout is 0, relies on the device's own timeout via status polling.
 */
private void setMotionActive() {
  sendEvent(name: 'motion', value: 'active', isStateChange: true,
    descriptionText: 'Motion detected')
  logInfo('Motion detected')

  Integer timeout = settings.motionTimeout != null ? (settings.motionTimeout as Integer) : 60
  if (timeout > 0) {
    runIn(timeout, 'setMotionInactive')
  }
}

/**
 * Sets motion to inactive. Called by scheduled timeout or by status poll
 * showing motion is no longer active.
 */
void setMotionInactive() {
  if (device.currentValue('motion') == 'active') {
    sendEvent(name: 'motion', value: 'inactive', isStateChange: true,
      descriptionText: 'Motion inactive (timeout)')
    logInfo('Motion inactive')
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Motion State Management                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles motion state, illuminance (lux), temperature, and battery.
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

    if (key.startsWith('motion:')) {
      if (data.motion == true) {
        setMotionActive()
      } else if (data.motion == false) {
        setMotionInactive()
      }
    } else if (key.startsWith('lux:')) {
      if (data.value != null) {
        Integer lux = data.value as Integer
        sendEvent(name: 'illuminance', value: lux, unit: 'lux',
          descriptionText: "Illuminance is ${lux} lux")
        logInfo("Illuminance: ${lux} lux")
      }
    } else if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
        logInfo("Temperature: ${temp}°${scale}")
      }
    } else if (key.startsWith('tamper:')) {
      if (data.vibration == true) {
        sendEvent(name: 'tamper', value: 'detected', isStateChange: true,
          descriptionText: 'Tamper/vibration detected')
        logInfo('Tamper/vibration detected')
      } else if (data.vibration == false) {
        sendEvent(name: 'tamper', value: 'clear',
          descriptionText: 'Tamper/vibration cleared')
      }
    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
        logInfo("Battery: ${battery}%")
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
