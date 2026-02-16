/**
 * Shelly Autoconf Single Cover PM
 *
 * Pre-built standalone driver for single-cover Shelly devices with power monitoring.
 * Examples: Shelly Plus 2PM (cover mode), Shelly Pro 2PM (cover mode)
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Commands delegate to the parent app via componentOpen/componentClose/etc.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Cover PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade'
    //Attributes: windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
    //            position - NUMBER
    //Commands: open(), close(), setPosition(position)

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
    //Attributes: voltage - NUMBER, unit:V

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    command 'resetEnergyMonitors'
    command 'stopPositionChange'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level', options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'], defaultValue: 'debug', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
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
  sendPmReportingIntervalToKVS()
}

/**
 * Parses incoming LAN messages from the Shelly device.
 * Routes notifications to the appropriate handler based on the dst field.
 *
 * @param description Raw LAN message description string from Hubitat
 */
/**
 * Parses incoming LAN messages from the Shelly device.
 * POST requests (from Shelly scripts) carry data in the JSON body.
 * GET requests (from Shelly Action Webhooks) carry state in the URL path.
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
    logTrace('GET webhook: no dst found, unable to route')
  }
}

/**
 * Parses query parameters from an incoming GET webhook request.
 *
 * @param msg The parsed LAN message map
 * @return Map of query parameter key-value pairs, or null if not parseable
 */
/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /webhook/cover_open/0).
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst and cid keys, or null if not parseable
 */
private Map parseWebhookQueryParams(Map msg) {
  String requestLine = null

  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
  }

  if (!requestLine) { return null }

  String pathAndQuery = requestLine.split(' ')[1]

  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      return [dst: segments[0], cid: segments[1]]
    }
  }

  return null
}

/**
 * Maps a Shelly cover state string to a Hubitat windowShade value.
 *
 * @param coverState The Shelly cover state
 * @return The Hubitat windowShade state string
 */
private String mapCoverState(String coverState) {
  switch (coverState) {
    case 'open': return 'open'
    case 'closed': return 'closed'
    case 'opening': return 'opening'
    case 'closing': return 'closing'
    case 'stopped': return 'partially open'
    case 'calibrating': return 'unknown'
    default: return 'unknown'
  }
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Supports both new discrete dst values (cover_open, cover_closed, cover_opening,
 * cover_closing, cover_stopped, cover_calibrating, input_toggle_on, input_toggle_off)
 * and legacy combined dst values (covermon, input_toggle).
 *
 * @param params The parsed query parameters including dst and optional state/pos fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    // New discrete cover webhooks — state is encoded in the dst name
    case 'cover_open':
      sendEvent(name: 'windowShade', value: 'open', descriptionText: 'Window shade is open')
      logInfo('Cover state changed to: open')
      if (params.pos != null) {
        Integer position = params.pos as Integer
        sendEvent(name: 'position', value: position, unit: '%',
          descriptionText: "Position is ${position}%")
      }
      break
    case 'cover_closed':
      sendEvent(name: 'windowShade', value: 'closed', descriptionText: 'Window shade is closed')
      logInfo('Cover state changed to: closed')
      if (params.pos != null) {
        Integer position = params.pos as Integer
        sendEvent(name: 'position', value: position, unit: '%',
          descriptionText: "Position is ${position}%")
      }
      break
    case 'cover_opening':
      sendEvent(name: 'windowShade', value: 'opening', descriptionText: 'Window shade is opening')
      logInfo('Cover state changed to: opening')
      break
    case 'cover_closing':
      sendEvent(name: 'windowShade', value: 'closing', descriptionText: 'Window shade is closing')
      logInfo('Cover state changed to: closing')
      break
    case 'cover_stopped':
      sendEvent(name: 'windowShade', value: 'partially open', descriptionText: 'Window shade is partially open')
      logInfo('Cover state changed to: partially open')
      if (params.pos != null) {
        Integer position = params.pos as Integer
        sendEvent(name: 'position', value: position, unit: '%',
          descriptionText: "Position is ${position}%")
      }
      break
    case 'cover_calibrating':
      sendEvent(name: 'windowShade', value: 'unknown', descriptionText: 'Window shade is calibrating')
      logInfo('Cover state changed to: unknown (calibrating)')
      break

    // Legacy combined cover webhook — state is in params.state
    case 'covermon':
      if (params.state != null) {
        String shadeState = mapCoverState(params.state as String)
        sendEvent(name: 'windowShade', value: shadeState,
          descriptionText: "Window shade is ${shadeState}")
        logInfo("Cover state changed to: ${shadeState}")
      }
      if (params.pos != null) {
        Integer position = params.pos as Integer
        sendEvent(name: 'position', value: position, unit: '%',
          descriptionText: "Position is ${position}%")
      }
      break

    // New discrete input toggle webhooks
    case 'input_toggle_on':
      logInfo('Input toggle on received')
      break
    case 'input_toggle_off':
      logInfo('Input toggle off received')
      break

    // Legacy combined input toggle webhook — state is in params.state
    case 'input_toggle':
      if (params.state != null) {
        logInfo("Input toggle state: ${params.state}")
      }
      break

    // Temperature webhook (unchanged)
    case 'temperature':
      String scale = location.temperatureScale ?: 'F'
      BigDecimal temp = null
      if (scale == 'C' && params.tC) {
        temp = params.tC as BigDecimal
      } else if (params.tF) {
        temp = params.tF as BigDecimal
      }
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
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
  sendPmReportingIntervalToKVS()
}

/**
 * Sends the PM reporting interval setting to the device KVS via the parent app.
 */
private void sendPmReportingIntervalToKVS() {
  Integer interval = settings?.pmReportingInterval != null ? settings.pmReportingInterval as Integer : 60
  parent?.componentWriteKvsToDevice(device, 'hubitat_sdm_pm_ri', interval)
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
// ║  Cover Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the window shade / cover.
 * Delegates to parent app's componentOpen handler.
 */
void open() {
  logDebug("open() called")
  parent?.componentOpen(device)
}

/**
 * Closes the window shade / cover.
 * Delegates to parent app's componentClose handler.
 */
void close() {
  logDebug("close() called")
  parent?.componentClose(device)
}

/**
 * Sets the cover position to a specific value.
 * Delegates to parent app's componentSetPosition handler.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.componentSetPosition(device, position as Integer)
}

/**
 * Stops any in-progress cover movement.
 * Delegates to parent app's componentStop handler.
 */
void stopPositionChange() {
  logDebug("stopPositionChange() called")
  parent?.componentStop(device)
}

/**
 * Parses cover monitoring notifications from Shelly device.
 * Processes JSON with dst:"covermon" and updates windowShade, position, and
 * inline power monitoring attributes (voltage, current, power, energy).
 * JSON format: [dst:covermon, result:[cover:0:[id:0, state:open, current_pos:100, apower:0, voltage:120.8, current:0, aenergy:[total:1234]]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseCovermon(Map json) {
  logDebug("parseCovermon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseCovermon: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('cover:')) {
        if (value instanceof Map) {
          Integer coverId = value.id
          String shellyState = value.state

          // Map Shelly cover state to Hubitat WindowShade values
          if (shellyState != null) {
            String shadeState
            switch (shellyState) {
              case 'open':
                shadeState = 'open'
                break
              case 'closed':
                shadeState = 'closed'
                break
              case 'opening':
                shadeState = 'opening'
                break
              case 'closing':
                shadeState = 'closing'
                break
              case 'stopped':
                shadeState = 'partially open'
                break
              case 'calibrating':
                shadeState = 'unknown'
                break
              default:
                shadeState = 'unknown'
            }
            logInfo("Cover ${coverId} state changed to: ${shadeState}")
            sendEvent(name: "windowShade", value: shadeState,
              descriptionText: "Window shade is ${shadeState}")
          }

          // Update position attribute
          if (value.current_pos != null) {
            Integer position = value.current_pos as Integer
            sendEvent(name: "position", value: position, unit: "%",
              descriptionText: "Position is ${position}%")
            logDebug("Cover ${coverId} position: ${position}%")
          }

          // Extract inline power monitoring values
          if (value.voltage != null) {
            BigDecimal voltage = value.voltage as BigDecimal
            sendEvent(name: "voltage", value: voltage, unit: "V", descriptionText: "Voltage is ${voltage}V")
            logDebug("Voltage: ${voltage}V")
          }

          if (value.current != null) {
            BigDecimal current = value.current as BigDecimal
            sendEvent(name: "amperage", value: current, unit: "A", descriptionText: "Current is ${current}A")
            logDebug("Current: ${current}A")
          }

          if (value.apower != null) {
            BigDecimal power = value.apower as BigDecimal
            sendEvent(name: "power", value: power, unit: "W", descriptionText: "Power is ${power}W")
            logDebug("Power: ${power}W")
          }

          if (value.aenergy?.total != null) {
            BigDecimal energyWh = value.aenergy.total as BigDecimal
            BigDecimal energyKwh = energyWh / 1000
            sendEvent(name: "energy", value: energyKwh, unit: "kWh", descriptionText: "Energy is ${energyKwh}kWh")
            logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
          }

          logInfo("Cover ${coverId} updated: state=${shellyState}, pos=${value.current_pos}, power=${value.apower}W")
        }
      }
    }
  } catch (Exception e) {
    logError("parseCovermon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Cover Commands                                          ║
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
// ║  Temperature Monitoring                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses temperature notifications from Shelly device.
 * Handles internal device temperature sensors (temperature:100, temperature:101).
 * JSON format: [dst:temperature, result:[temperature:100:[id:100, tC:42.3, tF:108.1]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseTemperature(Map json) {
  logDebug("parseTemperature() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseTemperature: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        BigDecimal tempC = value.tC != null ? value.tC as BigDecimal : null
        BigDecimal tempF = value.tF != null ? value.tF as BigDecimal : null

        if (tempC != null || tempF != null) {
          // Use hub's temperature scale preference
          String scale = location.temperatureScale ?: 'F'
          BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC)
          String unit = "\u00B0${scale}"

          if (temp != null) {
            def currentTemp = device.currentValue('temperature')
            if (currentTemp == null || (currentTemp as BigDecimal) != temp) {
              sendEvent(name: "temperature", value: temp, unit: unit,
                descriptionText: "Temperature is ${temp}${unit}")
              logInfo("Temperature: ${temp}${unit}")
            } else {
              logDebug("Temperature unchanged: ${temp}${unit}")
            }
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseTemperature exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Temperature Monitoring                                  ║
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

@Field static Boolean NOCHILDCOVER = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
