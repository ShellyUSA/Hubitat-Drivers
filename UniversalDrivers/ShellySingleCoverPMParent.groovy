/**
 * Shelly Autoconf Single Cover PM Parent
 *
 * Parent driver for single-cover Shelly devices with power monitoring.
 * Examples: Shelly Plus 2PM (cover mode), Shelly Pro 2PM (cover mode)
 *
 * Architecture:
 * - Creates zero children (single cover handled directly on parent device)
 * - Parses LAN notifications locally and sends events to self
 * - Delegates commands to parent app via parentSendCommand()
 * - Supports profile changes (e.g., switching between cover and switch mode)
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Cover PM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade'
    //Attributes: windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
    //            position - NUMBER, unit:%
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
    command 'reinitializeDevice'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
}

import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static Boolean NOCHILDCOVER = true

// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug('Parent device installed')
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration.
 */
void updated() {
  logDebug('Parent device updated')
  initialize()
  sendPmReportingIntervalToKVS()
}

/**
 * Initializes the parent device driver.
 * - Registers with parent app for management
 * - Reconciles child devices (creates zero children for single cover)
 */
void initialize() {
  logDebug('Parent device initialized')
  parent?.componentInitialize(device)
  reconcileChildDevices()
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 */
void configure() {
  logDebug('Parent device configure() called')
  parent?.componentConfigure(device)
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
 * App will call distributeStatus() with the latest device status.
 */
void refresh() {
  logDebug('Parent device refresh() called')
  parent?.parentRefresh(device)
}

/**
 * Triggers device reinitialization via the parent app.
 * Used when the device needs to be reconfigured (e.g., profile change, webhook reinstall).
 */
void reinitializeDevice() {
  logDebug('reinitializeDevice() called')
  parent?.reinitializeDevice(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                     ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level child devices for single cover device.
 * Creates missing children and removes orphaned children that exist but shouldn't.
 * Children that already exist correctly are left untouched.
 * For single cover devices, no children are desired — all events handled on parent.
 * Called from initialize() and when profile changes.
 */
@CompileStatic
private void reconcileChildDevices() {
  logDebug('reconcileChildDevices() called')

  // Single cover device - no children needed; desired set is empty
  Set<String> desiredDnis = [] as Set

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  List<com.hubitat.app.DeviceWrapper> existingChildren = getChildDevicesHelper()
  existingChildren?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children (exist but shouldn't)
  existingDnis.each { String dni ->
    logInfo("Removing orphaned child: ${dni}")
    deleteChildDeviceHelper(dni)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  LAN Message Parsing and Event Routing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses incoming LAN messages from the Shelly device.
 * Routes script notifications and webhook notifications to appropriate handlers.
 * Sends events directly to parent device (no children for single cover).
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
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /cover_open/0).
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
 * Routes script notifications to appropriate parsers based on dst field.
 * Sends events directly to parent device (no children for single cover).
 *
 * @param dst Destination type (covermon, powermon, temperature)
 * @param json Full JSON payload from script notification
 */
private void routeScriptNotification(String dst, Map json) {
  logDebug("routeScriptNotification: dst=${dst}")

  switch (dst) {
    case 'covermon':
      parseCovermon(json)
      break
    case 'temperature':
      parseTemperature(json)
      break
    default:
      logDebug("Unknown dst type: ${dst}")
  }
}

/**
 * Routes webhook GET query parameters to appropriate event handlers.
 * Sends events directly to parent device (no children for single cover).
 * Supports both new discrete dst values (cover_open, cover_closed, cover_opening,
 * cover_closing, cover_stopped, cover_calibrating, input_toggle_on, input_toggle_off)
 * and legacy combined dst values (covermon, input_toggle).
 *
 * @param params The parsed query parameters including dst and optional state/pos fields
 */
private void routeWebhookParams(Map params) {
  logDebug("routeWebhookParams: dst=${params.dst}")

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
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
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

    case 'ble':
      // Fallback: forward BLE data to app if handlePostWebhook intercept was missed
      logDebug('BLE relay received via routeWebhookParams, forwarding to app')
      parent?.handleBleRelay(device, params)
      break

    default:
      logDebug("routeWebhookParams: unhandled dst=${params.dst}")
  }
}

/**
 * Distributes status from Shelly.GetStatus query to parent device.
 * Called by parent app after refresh() or during periodic status updates.
 *
 * @param status Map of component statuses from Shelly.GetStatus
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")

  // Send status as synthetic covermon notification
  if (status) {
    Map syntheticJson = [dst: 'covermon', result: status]
    parseCovermon(syntheticJson)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END LAN Message Parsing and Event Routing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Cover Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the window shade / cover.
 * Delegates to parent app's parentSendCommand with Cover.Open method.
 */
void open() {
  logDebug('open() called')
  parent?.parentSendCommand(device, 'Cover.Open', [id: 0])
}

/**
 * Closes the window shade / cover.
 * Delegates to parent app's parentSendCommand with Cover.Close method.
 */
void close() {
  logDebug('close() called')
  parent?.parentSendCommand(device, 'Cover.Close', [id: 0])
}

/**
 * Sets the cover position to a specific value.
 * Delegates to parent app's parentSendCommand with Cover.GoToPosition method.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.parentSendCommand(device, 'Cover.GoToPosition', [id: 0, pos: position as Integer])
}

/**
 * Stops any in-progress cover movement.
 * Delegates to parent app's parentSendCommand with Cover.Stop method.
 */
void stopPositionChange() {
  logDebug('stopPositionChange() called')
  parent?.parentSendCommand(device, 'Cover.Stop', [id: 0])
}

/**
 * Maps a Shelly cover state string to a Hubitat windowShade value.
 *
 * @param coverState The Shelly cover state
 * @return The Hubitat windowShade state string
 */
@CompileStatic
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
      logWarn('parseCovermon: No result data in JSON')
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('cover:')) {
        if (value instanceof Map) {
          Integer coverId = value.id
          String shellyState = value.state

          // Map Shelly cover state to Hubitat WindowShade values
          if (shellyState != null) {
            String shadeState = mapCoverState(shellyState)
            logInfo("Cover ${coverId} state changed to: ${shadeState}")
            sendEvent(name: 'windowShade', value: shadeState,
              descriptionText: "Window shade is ${shadeState}")
          }

          // Update position attribute
          if (value.current_pos != null) {
            Integer position = value.current_pos as Integer
            sendEvent(name: 'position', value: position, unit: '%',
              descriptionText: "Position is ${position}%")
            logDebug("Cover ${coverId} position: ${position}%")
          }

          // Extract inline power monitoring values
          if (value.voltage != null) {
            BigDecimal voltage = value.voltage as BigDecimal
            sendEvent(name: 'voltage', value: voltage, unit: 'V',
              descriptionText: "Voltage is ${voltage}V")
            logDebug("Voltage: ${voltage}V")
          }

          if (value.current != null) {
            BigDecimal current = value.current as BigDecimal
            sendEvent(name: 'amperage', value: current, unit: 'A',
              descriptionText: "Current is ${current}A")
            logDebug("Current: ${current}A")
          }

          if (value.apower != null) {
            BigDecimal power = value.apower as BigDecimal
            sendEvent(name: 'power', value: power, unit: 'W',
              descriptionText: "Power is ${power}W")
            logDebug("Power: ${power}W")
          }

          if (value.aenergy?.total != null) {
            BigDecimal energyWh = value.aenergy.total as BigDecimal
            BigDecimal energyKwh = energyWh / 1000
            sendEvent(name: 'energy', value: energyKwh, unit: 'kWh',
              descriptionText: "Energy is ${energyKwh}kWh")
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
  logDebug('resetEnergyMonitors() called')
  parent?.parentSendCommand(device, 'Switch.ResetCounters', [id: 0, type: ['aenergy']])
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
      logWarn('parseTemperature: No result data in JSON')
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        BigDecimal tempC = value.tC != null ? value.tC as BigDecimal : null
        BigDecimal tempF = value.tF != null ? value.tF as BigDecimal : null

        if (tempC != null || tempF != null) {
          // Use hub's temperature scale preference
          String scale = getLocationHelper()?.temperatureScale ?: 'F'
          BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC)
          String unit = "\u00B0${scale}"

          if (temp != null) {
            def currentTemp = device.currentValue('temperature')
            if (currentTemp == null || (currentTemp as BigDecimal) != temp) {
              sendEvent(name: 'temperature', value: temp, unit: unit,
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
// ║  Helper Functions (Non-Static Wrappers)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Non-static helper to get child devices.
 * Required because getChildDevices() is a dynamic Hubitat method.
 *
 * @return List of child device wrappers
 */
private List<com.hubitat.app.DeviceWrapper> getChildDevicesHelper() {
  return getChildDevices()
}

/**
 * Non-static helper to delete a child device.
 * Required because deleteChildDevice() is a dynamic Hubitat method.
 *
 * @param dni Device network ID of child to delete
 */
private void deleteChildDeviceHelper(String dni) {
  deleteChildDevice(dni)
}

/**
 * Non-static helper to get location.
 * Required because location is a dynamic Hubitat property.
 *
 * @return Location object
 */
private Object getLocationHelper() {
  return location
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Helper Functions                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
@CompileStatic
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
@CompileStatic
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
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${obj?.getClass()?.name}")
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
@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝
