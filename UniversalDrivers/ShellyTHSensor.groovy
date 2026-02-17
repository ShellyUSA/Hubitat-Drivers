/**
 * Shelly Autoconf TH Sensor
 *
 * Pre-built standalone driver for Shelly temperature/humidity sensor devices.
 * Examples: Shelly H&T, Shelly Plus H&T
 *
 * This is a battery-powered sensor device that sleeps most of the time.
 * It wakes briefly to send temperature/humidity updates via webhooks.
 * When a sensor notification arrives, the driver also requests a battery
 * level update from the parent app while the device is awake.
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Sensor data arrives via parse() from Shelly webhook notifications.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf TH Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'RelativeHumidityMeasurement'
    //Attributes: humidity - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER

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
  input name: 'logLevel', type: 'enum', title: 'Logging Level', options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'], defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug("installed() called")
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
}

/**
 * Parses incoming LAN messages from the Shelly device.
 * POST requests (from Shelly scripts) carry data in the JSON body.
 * GET requests (from Shelly Action Webhooks) carry sensor data in URL query params.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    if (msg?.body) {
      handlePostWebhook(msg)
    } else {
      handleGetWebhook(msg)
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Handles POST webhook notifications from Shelly scripts.
 * Parses JSON body and routes to webhook params handler.
 *
 * @param msg The parsed LAN message map containing a JSON body
 */
private void handlePostWebhook(Map msg) {
  try {
    Map json = new groovy.json.JsonSlurper().parseText(msg.body) as Map
    String dst = json?.dst?.toString()
    if (!dst) { logTrace('POST webhook: no dst in body'); return }

    // BLE relay: forward to app for BLE device processing
    if (dst == 'ble') {
      logDebug('BLE relay received, forwarding to app')
      parent?.handleBleRelay(device, json)
      return
    }

    Map params = [:]
    json.each { k, v -> if (v != null) { params[k.toString()] = v.toString() } }

    logDebug("POST webhook dst=${dst}, cid=${params.cid}")
    logTrace("POST webhook params: ${params}")
    routeWebhookParams(params)
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

/**
 * Handles GET webhook notifications from Shelly Action Webhooks.
 *
 * @param msg The parsed LAN message map (no body)
 */
private void handleGetWebhook(Map msg) {
  Map params = parseWebhookQueryParams(msg)
  if (params?.dst) {
    logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
    logTrace("GET webhook params: ${params}")
    routeWebhookParams(params)
  } else {
    logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
  }
}

/**
 * Parses webhook GET request path and query string to extract routing and sensor data.
 * GET Action Webhooks carry routing in the path (e.g., /temperature/0) and
 * sensor values in query parameters (e.g., ?tC=22.5&tF=72.5&battPct=85).
 * Falls back to raw header string if parsed headers Map lacks the request line.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst, cid, and any parsed query parameter keys, or null if not parseable
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

  // Strip leading slash and separate path from query string
  String webhookPath = pathAndQuery.startsWith('/') ? pathAndQuery.substring(1) : pathAndQuery
  if (!webhookPath) { return null }

  String queryString = null
  int qMarkIdx = webhookPath.indexOf('?')
  if (qMarkIdx >= 0) {
    queryString = webhookPath.substring(qMarkIdx + 1)
    webhookPath = webhookPath.substring(0, qMarkIdx)
  }

  String[] segments = webhookPath.split('/')
  if (segments.length < 2) { return null }

  Map result = [dst: segments[0], cid: segments[1]]

  // Parse query parameters (e.g., tC=22.5&tF=72.5&battPct=85)
  if (queryString) {
    String[] pairs = queryString.split('&')
    for (String pair : pairs) {
      int eqIdx = pair.indexOf('=')
      if (eqIdx > 0) {
        result[pair.substring(0, eqIdx)] = pair.substring(eqIdx + 1)
      }
    }
  }

  return result
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Handles temperature, humidity, and piggybacked battery data.
 * TH sensor dst values (temperature, humidity) did not change between legacy and new
 * webhook formats, so the same cases handle both.
 *
 * @param params The parsed query parameters including dst and sensor value fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    case 'temperature':
      String scale = location.temperatureScale ?: 'F'
      BigDecimal temp = null
      if (scale == 'C' && params.tC != null) {
        temp = params.tC as BigDecimal
      } else if (params.tF != null) {
        temp = params.tF as BigDecimal
      } else if (params.tC != null) {
        // Fahrenheit hub but only Celsius available — convert
        temp = (params.tC as BigDecimal) * 9 / 5 + 32
      }
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
        logInfo("Temperature: ${temp}°${scale}")
      }
      break

    case 'humidity':
      if (params.rh != null) {
        BigDecimal humidity = params.rh as BigDecimal
        sendEvent(name: 'humidity', value: humidity, unit: '%',
          descriptionText: "Humidity is ${humidity}%")
        logInfo("Humidity: ${humidity}%")
      }
      break

    case 'ble':
      // Fallback: forward BLE data to app if handlePostWebhook intercept was missed
      logDebug('BLE relay received via routeWebhookParams, forwarding to app')
      parent?.handleBleRelay(device, params)
      return // don't update lastUpdated for relay-only traffic

    default:
      logDebug("routeWebhookParams: unhandled dst=${params.dst}")
  }

  // Battery data piggybacked on sensor webhooks via supplemental URL tokens
  if (params.battPct != null) {
    Integer batteryPct = params.battPct as Integer
    sendEvent(name: 'battery', value: batteryPct, unit: '%',
      descriptionText: "Battery is ${batteryPct}%")
    logInfo("Battery: ${batteryPct}%")
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Initializes the device driver. Called on install, settings update, and hub startup.
 * This is a battery-powered device — initialization only resets hub-side state.
 */
void initialize() {
  logDebug('initialize() called — battery device, hub-side reset only')
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 * This runs on the hub side and does not require the device to be awake.
 */
void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Refresh is a no-op for battery-powered devices.
 * The device sleeps most of the time and cannot be polled on demand.
 * Data updates automatically when the device wakes to send sensor reports.
 */
void refresh() {
  logDebug('refresh() called — battery device is asleep, data updates on next wake')
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝




// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
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
