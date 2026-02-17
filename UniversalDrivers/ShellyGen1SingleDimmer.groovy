/**
 * Shelly Gen1 Single Dimmer
 *
 * Pre-built standalone driver for Gen 1 dimmer Shelly devices.
 * Examples: Shelly Dimmer 1, Shelly Dimmer 2
 *
 * Gen 1 dimmers use HTTP REST action URLs and polling for brightness level.
 * Commands delegate to the parent app which routes to Gen 1 REST endpoints.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Single Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
  }
}

preferences {
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
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly dimmer.
 * Gen 1 action URLs fire GET requests only.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    Map params = parseWebhookQueryParams(msg)
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
 * GET Action Webhooks encode state in the path (e.g., /brightness/0).
 * Falls back to raw header string if parsed headers Map lacks the request line.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst and cid keys, or null if not parseable
 */
@CompileStatic
private Map parseWebhookQueryParams(Map msg) {
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

  // Strip leading slash and parse /<dst>/<cid>[?queryParams]
  String webhookPath = pathAndQuery.startsWith('/') ? pathAndQuery.substring(1) : pathAndQuery
  if (!webhookPath) { return null }
  int qMarkIdx = webhookPath.indexOf('?')
  if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }
  String[] segments = webhookPath.split('/')
  if (segments.length >= 2) {
    return [dst: segments[0], cid: segments[1]]
  }

  return null
}

/**
 * Routes Gen 1 action URL callbacks for dimmer events.
 * After state change, triggers a refresh to get brightness/power data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'light_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Light turned on')
      logInfo('Light state changed to: on')
      parent?.componentRefresh(device)
      break
    case 'light_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Light turned off')
      logInfo('Light state changed to: off')
      parent?.componentRefresh(device)
      break

    case 'input_short':
      logInfo('Input short push received')
      break
    case 'input_long':
      logInfo('Input long push received')
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
 * Turns the dimmer on by delegating to the parent app.
 */
void on() {
  logDebug('on() called')
  parent?.componentOn(device)
}

/**
 * Turns the dimmer off by delegating to the parent app.
 */
void off() {
  logDebug('off() called')
  parent?.componentOff(device)
}

/**
 * Sets the dimmer brightness level.
 * Delegates to parent app which sends GET /light/0?turn=on&brightness={level}.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentSetLevel(device, level as Integer, duration as Integer)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch and Level Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles light state, brightness, and power monitoring values.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  status.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('light:') || !(v instanceof Map)) { return }

    Map data = v as Map
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      sendEvent(name: 'switch', value: switchState, descriptionText: "Light turned ${switchState}")
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
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return settings.logLevel == 'warn' }
  else if (messageLevel == 'info') { return ['warn', 'info'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['warn', 'info', 'debug'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
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
