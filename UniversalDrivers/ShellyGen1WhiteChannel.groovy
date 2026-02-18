/**
 * Shelly Gen1 White Channel
 *
 * Child driver for an individual white channel of the Shelly RGBW2 in white mode.
 * Each RGBW2 in white mode has 4 independent dimmer channels (white:0 through white:3).
 *
 * This driver is created as a driver-level child by the
 * {@code Shelly Gen1 RGBW2 White Parent} driver. All commands delegate
 * to the parent, which relays them to the app for REST endpoint routing.
 *
 * Commands use the Gen 1 {@code /white/{id}} endpoint:
 *   - On/Off: {@code GET /white/{id}?turn=on|off}
 *   - Brightness: {@code GET /white/{id}?turn=on&brightness=50}
 *   - Settings: {@code GET /settings/white/{id}?auto_off=300}
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 White Channel', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true

  input name: 'defaultState', type: 'enum', title: 'Power Restore State',
    options: ['on':'On', 'off':'Off', 'last':'Last State'],
    defaultValue: 'last', required: false

  input name: 'autoOnTime', type: 'number', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false

  input name: 'autoOffTime', type: 'number', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
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
  syncWhiteSettings()
  initialize()
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly RGBW2.
 * Gen 1 action URLs fire GET requests only.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }
    // No checkAndUpdateSourceIp() — this child driver's DNI is not the device IP,
    // so Hubitat does not route LAN messages directly here. IP change detection
    // is handled by the parent (ShellyGen1RGBW2WhiteParent).

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
 * Routes Gen 1 action URL callbacks for white channel events.
 * After state change, triggers a refresh to get brightness/power data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'white_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'White channel turned on')
      logInfo('White channel state changed to: on')
      parent?.componentRefresh(device)
      break
    case 'white_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'White channel turned off')
      logInfo('White channel state changed to: off')
      parent?.componentRefresh(device)
      break
    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

void initialize() {
  logDebug('initialize() called')
}

void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch and Level Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the white channel on by delegating to the parent driver.
 */
void on() {
  logDebug('on() called')
  parent?.componentWhiteOn(device)
}

/**
 * Turns the white channel off by delegating to the parent driver.
 */
void off() {
  logDebug('off() called')
  parent?.componentWhiteOff(device)
}

/**
 * Sets the white channel brightness level.
 * Delegates to parent driver which sends GET /white/{id}?turn=on&brightness={level}.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  Integer transitionMs = duration > 0 ? (duration * 1000) as Integer : null
  parent?.componentSetWhiteLevel(device, level as Integer, transitionMs)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch and Level Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to this white channel.
 * Called by the parent driver after receiving normalized status.
 * Handles switch state, brightness, and power monitoring values.
 *
 * Note: In the parent-child architecture, the parent's distributeStatus()
 * handles routing to children. This method exists for completeness but
 * the parent directly sends events to children via child.sendEvent().
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  String whiteId = device.getDataValue('whiteId') ?: '0'
  String whiteKey = "white:${whiteId}".toString()

  status.each { k, v ->
    String key = k.toString()
    if (key != whiteKey || !(v instanceof Map)) { return }

    Map data = v as Map
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      sendEvent(name: 'switch', value: switchState, descriptionText: "White channel turned ${switchState}")
    }
    if (data.brightness != null) {
      Integer level = data.brightness as Integer
      sendEvent(name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%")
    }
    if (data.apower != null) {
      sendEvent(name: 'power', value: data.apower as BigDecimal, unit: 'W')
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      sendEvent(name: 'energy', value: energyKwh, unit: 'kWh')
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Settings Sync                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Syncs driver preferences to the Shelly device via parent driver.
 * Called from updated() when preferences change.
 * Uses /settings/white/{id} endpoint for RGBW2 white mode settings.
 */
private void syncWhiteSettings() {
  Map whiteSettings = [:]
  if (settings.defaultState != null) { whiteSettings.defaultState = settings.defaultState }
  if (settings.autoOnTime != null) { whiteSettings.autoOnTime = settings.autoOnTime }
  if (settings.autoOffTime != null) { whiteSettings.autoOffTime = settings.autoOffTime }
  if (whiteSettings) {
    parent?.componentUpdateWhiteSettings(device, whiteSettings)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Settings Sync                                           ║
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

@Field static Boolean NOCHILDSWITCH = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
