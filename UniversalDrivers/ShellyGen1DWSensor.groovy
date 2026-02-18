/**
 * Shelly Gen1 DW Sensor
 *
 * Pre-built standalone driver for Gen 1 Shelly Door/Window sensor (DW1, DW2).
 * Battery-powered device that sleeps most of the time.
 *
 * Wakes and fires action URL callbacks on:
 *   - Contact open / close (open_url / close_url)
 *   - Vibration detection (vibration_url, SHDW-2 only)
 *   - Dark / twilight threshold crossings (dark_url / twilight_url, SHDW-2 only)
 *
 * User-configurable preferences (dark_threshold, twilight_threshold,
 * vibration_sensitivity, temperature_offset, temperature_threshold,
 * lux_wakeup_enable, led_status_disable) are synced bidirectionally:
 * device-to-driver on first refresh, driver-to-device on Save Preferences
 * (queued for next wake-up if the device is asleep).
 *
 * Version: 1.1.0
 */

metadata {
  definition(name: 'Shelly Gen1 DW Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'ContactSensor'
    //Attributes: contact - ENUM ["open", "closed"]

    capability 'AccelerationSensor'
    //Attributes: acceleration - ENUM ["active", "inactive"]

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'IlluminanceMeasurement'
    //Attributes: illuminance - NUMBER, unit:lux

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
    attribute 'tilt', 'number'
    attribute 'voltage', 'number'
  }
}

preferences {
  // -- SHDW-2 Sensor Settings (synced to device via /settings) --
  input name: 'darkThreshold', type: 'number', title: 'Dark threshold (lux)',
    description: 'Lux level below which "dark" event fires (SHDW-2 only)',
    range: '0..400', required: false
  input name: 'twilightThreshold', type: 'number', title: 'Twilight threshold (lux)',
    description: 'Lux level below which "twilight" event fires (SHDW-2 only)',
    range: '0..400', required: false
  input name: 'vibrationSensitivity', type: 'enum', title: 'Vibration sensitivity',
    description: 'Vibration detection sensitivity (-1 = disabled, SHDW-2 only)',
    options: ['-1':'Disabled', '0':'Low', '1':'Medium', '2':'High', '3':'Very High'],
    required: false
  input name: 'temperatureOffset', type: 'decimal', title: 'Temperature offset',
    description: 'Calibration offset for temperature (SHDW-2 only)', required: false
  input name: 'temperatureThreshold', type: 'decimal', title: 'Temperature threshold',
    description: 'Min temperature change to trigger report (0.5\u20135.0, SHDW-2 only)',
    range: '0.5..5.0', required: false
  input name: 'luxWakeupEnable', type: 'bool', title: 'Enable lux-based wake-up',
    description: 'Wake on dark/twilight threshold crossings (SHDW-2 only)',
    defaultValue: false, required: false
  input name: 'ledStatusDisable', type: 'bool', title: 'Disable LED',
    defaultValue: false, required: false

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
 * Data will update on next wake-up (contact/vibration/lux event callback).
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
  if (settings.darkThreshold != null) {
    settingsMap.dark_threshold = (settings.darkThreshold as Integer).toString()
  }
  if (settings.twilightThreshold != null) {
    settingsMap.twilight_threshold = (settings.twilightThreshold as Integer).toString()
  }
  if (settings.vibrationSensitivity != null) {
    settingsMap.vibration_sensitivity = (settings.vibrationSensitivity as Integer).toString()
  }
  if (settings.temperatureOffset != null) {
    settingsMap.temperature_offset = (settings.temperatureOffset as BigDecimal).toString()
  }
  if (settings.temperatureThreshold != null) {
    settingsMap.temperature_threshold = (settings.temperatureThreshold as BigDecimal).toString()
  }
  if (settings.luxWakeupEnable != null) {
    settingsMap.lux_wakeup_enable = settings.luxWakeupEnable ? 'true' : 'false'
  }
  if (settings.ledStatusDisable != null) {
    settingsMap.led_status_disable = settings.ledStatusDisable ? 'true' : 'false'
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
 * GET Action Webhooks encode state in the path (e.g., /sensor/0).
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
 * Routes Gen 1 action URL callbacks for door/window sensor.
 * Contact events update contact attribute immediately.
 * Vibration events update acceleration attribute.
 * All events also trigger a refresh for full data.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'contact_open':
      sendEvent(name: 'contact', value: 'open', descriptionText: 'Contact opened')
      logInfo('Contact opened')
      parent?.componentRefresh(device)
      break
    case 'contact_close':
      sendEvent(name: 'contact', value: 'closed', descriptionText: 'Contact closed')
      logInfo('Contact closed')
      parent?.componentRefresh(device)
      break

    case 'vibration':
      sendEvent(name: 'acceleration', value: 'active', isStateChange: true,
        descriptionText: 'Vibration detected')
      logInfo('Vibration detected')
      // Auto-clear acceleration after 5 seconds
      runIn(5, 'clearAcceleration')
      parent?.componentRefresh(device)
      break

    case 'lux_dark':
      logInfo('Dark threshold crossed')
      parent?.componentRefresh(device)
      break
    case 'lux_twilight':
      logInfo('Twilight threshold crossed')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
      return  // Don't update lastUpdated for unhandled callbacks
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Clears the acceleration sensor back to inactive after vibration event.
 */
void clearAcceleration() {
  sendEvent(name: 'acceleration', value: 'inactive', descriptionText: 'Vibration cleared')
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

    if (key.startsWith('contact:')) {
      if (data.open != null) {
        String contactState = data.open ? 'open' : 'closed'
        sendEvent(name: 'contact', value: contactState, descriptionText: "Contact is ${contactState}")
        logInfo("Contact: ${contactState}")
      }
    } else if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
      }
    } else if (key.startsWith('lux:')) {
      if (data.value != null) {
        Integer lux = data.value as Integer
        sendEvent(name: 'illuminance', value: lux, unit: 'lux',
          descriptionText: "Illuminance is ${lux} lux")
      }
    } else if (key.startsWith('tilt:')) {
      if (data.value != null) {
        Integer tilt = data.value as Integer
        sendEvent(name: 'tilt', value: tilt, descriptionText: "Tilt angle is ${tilt}°")
      }
    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
      }
      if (data.voltage != null) {
        BigDecimal voltage = data.voltage as BigDecimal
        sendEvent(name: 'voltage', value: voltage, unit: 'V',
          descriptionText: "Battery voltage is ${voltage}V")
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
