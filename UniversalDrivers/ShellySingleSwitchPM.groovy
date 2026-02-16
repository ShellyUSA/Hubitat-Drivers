/**
 * Shelly Autoconf Single Switch PM
 *
 * Pre-built standalone driver for single-switch Shelly devices with power monitoring.
 * Examples: Shelly 1PM, Shelly 1PM Mini, Shelly Plug, Shelly Plus Plug S
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Commands delegate to the parent app via componentOn/componentOff.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Switch PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'VoltageMeasurement'
    //Attributes: voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    command 'resetEnergyMonitors'
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
 * Routes notifications to the appropriate handler based on the dst field.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  logTrace('parse() received message')

  try {
    Map msg = parseLanMessage(description)
    // Forward to parent app for structured trace logging (gate check ensures minimal overhead)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) {
      logTrace("parse() skipping HTTP response (status=${msg.status})")
      return
    }

    // Try POST JSON body first (script notifications like powermonitoring.js)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        String dst = json?.dst as String
        logDebug("POST notification dst=${dst}")
        logTrace("POST body: ${json}")

        if (dst == 'switchmon') { parseSwitchmon(json) }
        return
      } catch (Exception jsonEx) {
        // Body might be empty or not JSON — fall through to GET parsing
      }
    }

    // Try GET query parameters (webhook notifications with URL tokens)
    Map params = parseWebhookQueryParams(msg)
    if (params?.dst) {
      logDebug("GET webhook dst=${params.dst}")
      logTrace("Webhook params: ${params}")
      routeWebhookParams(params)
    } else {
      logTrace("parse() no dst found in message, unable to route")
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Parses query parameters from an incoming GET webhook request.
 *
 * @param msg The parsed LAN message map
 * @return Map of query parameter key-value pairs, or null if not parseable
 */
private Map parseWebhookQueryParams(Map msg) {
  String requestLine = null

  // Try parsed headers map first
  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
    logTrace("parseWebhookQueryParams: headers map search result: ${requestLine ? 'found' : 'not found'}")
  }

  // Fallback: parse raw header string for request line
  if (!requestLine && msg?.header) {
    String rawHeader = msg.header.toString()
    logTrace("parseWebhookQueryParams: trying raw header fallback")
    String[] lines = rawHeader.split('\n')
    for (String line : lines) {
      String trimmed = line.trim()
      if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
        requestLine = trimmed
        logTrace("parseWebhookQueryParams: found request line in raw header: ${requestLine}")
        break
      }
    }
  }

  if (!requestLine) {
    logTrace('parseWebhookQueryParams: no request line found in headers or raw header')
    return null
  }

  // Extract path from request line: "GET /webhook/switchmon/0 HTTP/1.1" -> "/webhook/switchmon/0"
  String pathAndQuery = requestLine.split(' ')[1]

  // Parse path segments: /webhook/<dst>/<cid>[?key=val&...]
  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    String queryString = null
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) {
      queryString = webhookPath.substring(qMarkIdx + 1)
      webhookPath = webhookPath.substring(0, qMarkIdx)
    }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      Map params = [dst: segments[0], cid: segments[1]]
      if (queryString) {
        queryString.split('&').each { String pair ->
          String[] kv = pair.split('=', 2)
          if (kv.length == 2) {
            params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
          }
        }
      }
      logTrace("parseWebhookQueryParams: parsed path params: ${params}")
      return params
    }
    logTrace("parseWebhookQueryParams: not enough path segments in '${pathAndQuery}'")
    return null
  }

  // Fallback: try query string parsing for backwards compatibility
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx >= 0) {
    Map params = [:]
    pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
      String[] kv = pair.split('=', 2)
      if (kv.length == 2) {
        params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
      }
    }
    logTrace("parseWebhookQueryParams: parsed query params: ${params}")
    return params
  }

  logTrace("parseWebhookQueryParams: no webhook path or query string in '${pathAndQuery}'")
  return null
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Supports both new discrete dst values (switch_on, switch_off, input_toggle_on,
 * input_toggle_off) and legacy combined dst values (switchmon, input_toggle).
 *
 * @param params The parsed query parameters including dst and optional output/state fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    // New discrete switch webhooks — state is encoded in the dst name
    case 'switch_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
      logInfo('Switch state changed to: on')
      break
    case 'switch_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
      logInfo('Switch state changed to: off')
      break

    // Legacy combined switch webhook — state is in params.output
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}")
        logInfo("Switch state changed to: ${switchState}")
      }
      break

    // New discrete input toggle webhooks — state is encoded in the dst name
    case 'input_toggle_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on (input toggle)')
      logInfo('Switch state changed to: on (input toggle)')
      break
    case 'input_toggle_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off (input toggle)')
      logInfo('Switch state changed to: off (input toggle)')
      break

    // Legacy combined input toggle webhook — state is in params.state
    case 'input_toggle':
      if (params.state != null) {
        String switchState = params.state == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState} (input toggle)")
        logInfo("Switch state changed to: ${switchState} (input toggle)")
      }
      break

    // Power monitoring via GET (from powermonitoring.js)
    case 'powermon':
      if (params.voltage != null) {
        BigDecimal voltage = params.voltage as BigDecimal
        sendEvent(name: 'voltage', value: voltage, unit: 'V',
          descriptionText: "Voltage is ${voltage}V")
        logDebug("Voltage: ${voltage}V")
      }
      if (params.current != null) {
        BigDecimal current = params.current as BigDecimal
        sendEvent(name: 'amperage', value: current, unit: 'A',
          descriptionText: "Current is ${current}A")
        logDebug("Current: ${current}A")
      }
      if (params.apower != null) {
        BigDecimal power = params.apower as BigDecimal
        sendEvent(name: 'power', value: power, unit: 'W',
          descriptionText: "Power is ${power}W")
        logDebug("Power: ${power}W")
      }
      if (params.aenergy != null) {
        BigDecimal energyWh = params.aenergy as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        sendEvent(name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy is ${energyKwh}kWh")
        logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
      }
      if (params.freq != null) {
        BigDecimal freq = params.freq as BigDecimal
        sendEvent(name: 'frequency', value: freq, unit: 'Hz',
          descriptionText: "Frequency is ${freq}Hz")
      }
      logInfo("Power monitoring updated: ${params.apower ?: 0}W, ${params.voltage ?: 0}V, ${params.current ?: 0}A")
      break

    default:
      logDebug("routeWebhookParams: unhandled dst=${params.dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Initializes the device driver. Called on install and settings update.
 */
void initialize() {
  logDebug("initialize() called")
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 */
void configure() {
  logDebug("configure() called")
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Refreshes the device state by querying the parent app.
 */
void refresh() {
  logDebug("refresh() called")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the switch on by delegating to the parent app.
 */
void on() {
  logDebug("on() called")
  parent?.componentOn(device)
}

/**
 * Turns the switch off by delegating to the parent app.
 */
void off() {
  logDebug("off() called")
  parent?.componentOff(device)
}

/**
 * Parses switch monitoring notifications from Shelly device.
 * Processes JSON with dst:"switchmon" and updates device state.
 * JSON format: [dst:switchmon, result:[switch:0:[id:0, output:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseSwitchmon(Map json) {
  logDebug("parseSwitchmon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseSwitchmon: No result data in JSON")
      return
    }

    // Iterate over switch entries (e.g., "switch:0")
    result.each { key, value ->
      if (key.toString().startsWith('switch:')) {
        if (value instanceof Map) {
          Integer switchId = value.id
          Boolean output = value.output

          if (output != null) {
            String switchState = output ? "on" : "off"
            logInfo("Switch ${switchId} state changed to: ${switchState}")
            sendEvent(name: "switch", value: switchState, descriptionText: "Switch turned ${switchState}")
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseSwitchmon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Resets energy monitoring counters by delegating to the parent app.
 */
void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Monitoring Commands                               ║
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
  if (messageLevel == 'error') {
    return true
  } else if (messageLevel == 'warn') {
    return settings.logLevel == 'warn'
  } else if (messageLevel == 'info') {
    return ['warn', 'info'].contains(settings.logLevel)
  } else if (messageLevel == 'debug') {
    return ['warn', 'info', 'debug'].contains(settings.logLevel)
  } else if (messageLevel == 'trace') {
    return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel)
  }
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    String prettyJson = prettyJson(message)
    logTrace(prettyJson)
  }
}

@CompileStatic
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

@CompileStatic
void logInfoJson(Map message) {
  String prettyJson = prettyJson(message)
  logInfo(prettyJson)
}

/**
 * Formats a Map as pretty-printed JSON.
 *
 * @param jsonInput The map to format
 * @return Pretty-printed JSON string
 */
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
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
