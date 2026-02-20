/**
 * Shelly Autoconf BLU Gateway Parent
 *
 * Parent driver for the Shelly BLU Gateway Gen3 (S3GW-1DBT001).
 * The gateway bridges BLE devices (BLU TRV, BLU H&T, etc.) to WiFi/LAN.
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates BLU TRV children (blutrv:200, blutrv:201, etc.) in initialize()
 *   - Parent receives LAN traffic (webhooks), parses locally, routes to children
 *   - Commands: child → parent componentBluTrv*() → app sendBluTrvCommand() → gateway RPC
 *   - Status: app polls gateway → distributeStatus() → routes to children
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf BLU Gateway Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    command 'reinitializeDevice'
    command 'distributeStatus', [[name: 'statusJson', type: 'JSON', description: 'Device status as JSON']]
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}

import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field



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
}

/**
 * Initializes the BLU Gateway parent driver.
 * - Registers with parent app for management
 * - Reconciles child devices (creates TRV children for paired BLU TRVs)
 */
void initialize() {
  logDebug('Parent device initialized')
  parent?.componentInitialize(device)
  reconcileChildDevices()
}

/**
 * Configures the device driver settings.
 */
void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

/**
 * Refreshes device state by querying the parent app.
 * App will call distributeStatus() with the latest device status.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.parentRefresh(device)
}

/**
 * Triggers device reinitialization via the parent app.
 * Used when the device needs to be reconfigured (e.g., webhook reinstall).
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
 * Reconciles driver-level child devices against the components data value.
 * Creates BLU TRV children for each blutrv:NNN component and removes orphans.
 * BluTrv component IDs use the 200-299 range (not 0-based like standard components).
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logDebug('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType != 'blutrv') { return }
    desiredDnis.add("${device.deviceNetworkId}-blutrv-${compId}".toString())
  }

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children (exist but shouldn't)
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      com.hubitat.app.DeviceWrapper child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  // Create missing children (should exist but don't)
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType != 'blutrv') { return }

    String childDni = "${device.deviceNetworkId}-blutrv-${compId}"
    if (getChildDevice(childDni)) { return } // already exists

    String driverName = 'Shelly Autoconf BLU TRV'
    String label = "${device.displayName} TRV ${compId}"
    try {
      com.hubitat.app.DeviceWrapper child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', 'blutrv')
      child.updateDataValue('bluetrvId', compId.toString())
      logInfo("Created child: ${label} (${driverName})")
    } catch (Exception e) {
      logError("Failed to create child ${label}: ${e.message}")
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  BLU TRV Component Command Delegation                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the heating setpoint on a TRV child via the gateway.
 * Called by TRV component child driver.
 *
 * @param childDevice The TRV child device requesting the command
 * @param tempC Target temperature in Celsius
 */
void componentBluTrvSetTarget(com.hubitat.app.DeviceWrapper childDevice, BigDecimal tempC) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvSetTarget: ${tempC}°C on TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.SetTarget', [id: 0, target_C: tempC])
}

/**
 * Sets the valve position on a TRV child via the gateway.
 * Thermostat mode must be disabled on the TRV for direct position control.
 *
 * @param childDevice The TRV child device
 * @param position Valve position 0-100
 */
void componentBluTrvSetPosition(com.hubitat.app.DeviceWrapper childDevice, Integer position) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvSetPosition: pos=${position} on TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.SetPosition', [id: 0, pos: position])
}

/**
 * Activates boost mode on a TRV child via the gateway.
 *
 * @param childDevice The TRV child device
 * @param durationSecs Boost duration in seconds
 */
void componentBluTrvSetBoost(com.hubitat.app.DeviceWrapper childDevice, Integer durationSecs) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvSetBoost: ${durationSecs}s on TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.SetBoost', [id: 0, duration: durationSecs])
}

/**
 * Cancels active boost mode on a TRV child via the gateway.
 *
 * @param childDevice The TRV child device
 */
void componentBluTrvClearBoost(com.hubitat.app.DeviceWrapper childDevice) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvClearBoost: TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.ClearBoost', [id: 0])
}

/**
 * Sends an external temperature reading to a TRV child via the gateway.
 *
 * @param childDevice The TRV child device
 * @param tempC External temperature in Celsius
 */
void componentBluTrvSetExternalTemp(com.hubitat.app.DeviceWrapper childDevice, BigDecimal tempC) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvSetExternalTemp: ${tempC}°C on TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.SetExternalTemperature', [id: 0, temp_C: tempC])
}

/**
 * Triggers valve motor calibration on a TRV child via the gateway.
 *
 * @param childDevice The TRV child device
 */
void componentBluTrvCalibrate(com.hubitat.app.DeviceWrapper childDevice) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvCalibrate: TRV ${compId}")
  parent?.sendBluTrvCommand(device, compId, 'TRV.Calibrate', [id: 0])
}

/**
 * Refreshes status for a specific TRV child via the gateway.
 * Delegates to the app which polls BluTrv.GetStatus.
 *
 * @param childDevice The TRV child device
 */
void componentBluTrvRefresh(com.hubitat.app.DeviceWrapper childDevice) {
  Integer compId = childDevice.getDataValue('bluetrvId') as Integer
  logDebug("componentBluTrvRefresh: TRV ${compId}")
  parent?.componentBluTrvRefresh(device, compId)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END BLU TRV Component Command Delegation                    ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  LAN Message Parsing and Event Routing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses incoming LAN messages from the Shelly BLU Gateway.
 * Routes webhook notifications to appropriate TRV children.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }
    checkAndUpdateSourceIp(msg)

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
 *
 * @param msg The parsed LAN message map containing a JSON body
 */
private void handlePostWebhook(Map msg) {
  try {
    Object json = new groovy.json.JsonSlurper().parseText(msg.body)

    // Batched BLE reports: JSON array of messages from concurrency-controlled script
    if (json instanceof List) {
      List jsonList = (List) json
      int forwarded = 0
      jsonList.each { Object item ->
        if (item instanceof Map) {
          parent?.handleBleRelay(device, (Map) item)
          forwarded++
        } else {
          logTrace("BLE batch: skipping non-Map item")
        }
      }
      logDebug("BLE batch received (${jsonList.size()} reports, ${forwarded} forwarded)")
      return
    }

    // Single message (backward compat / non-BLE webhooks)
    if (!(json instanceof Map)) { logTrace('POST webhook: unexpected body type'); return }
    Map jsonMap = (Map) json
    String dst = jsonMap?.dst?.toString()
    if (!dst) { logTrace('POST webhook: no dst in body'); return }

    // BLE relay: forward to app for BLE device processing
    if (dst == 'ble') {
      logDebug('BLE relay received, forwarding to app')
      parent?.handleBleRelay(device, jsonMap)
      return
    }

    Map params = [:]
    jsonMap.each { k, v -> if (v != null) { params[k.toString()] = v.toString() } }

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
  try {
    Map params = parseWebhookPath(msg)
    if (params?.dst) {
      logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
      logTrace("GET webhook params: ${params}")
      routeWebhookParams(params)
    } else {
      logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
    }
  } catch (Exception e) {
    logDebug("GET webhook parse error: ${e.message}")
  }
}

/**
 * Routes webhook parameters to the appropriate TRV child device.
 * Handles blutrv_temperature_change and blutrv_position_change events.
 *
 * @param params Parsed webhook parameters including dst, cid, and data fields
 */
private void routeWebhookParams(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) {
    logTrace("routeWebhookParams: missing dst or cid")
    return
  }

  Integer componentId = params.cid as Integer

  if (dst.startsWith('blutrv_')) {
    String childDni = "${device.deviceNetworkId}-blutrv-${componentId}"
    com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
    if (!child) {
      logDebug("No TRV child found for DNI: ${childDni}")
      return
    }

    List<Map> events = buildBluTrvWebhookEvents(dst, params)
    events.each { Map evt ->
      logTrace("Sending event to ${child.displayName}: ${evt}")
      child.sendEvent(evt)
    }
    child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    logDebug("Routed ${events.size()} events to ${child.displayName}")
  } else if (dst == 'ble') {
    logDebug('BLE relay via routeWebhookParams, forwarding to app')
    parent?.handleBleRelay(device, params)
  } else {
    logDebug("routeWebhookParams: unhandled dst=${dst}")
  }
}

/**
 * Builds Hubitat events from BLU TRV webhook data.
 *
 * @param dst The webhook destination (e.g., blutrv_temperature_change)
 * @param params The webhook parameters with key/value data
 * @return List of event maps to send to the TRV child
 */
private List<Map> buildBluTrvWebhookEvents(String dst, Map params) {
  List<Map> events = []
  String scale = getLocationHelper()?.temperatureScale ?: 'F'

  // Current temperature
  if (params.tC != null && params.tC != 'null') {
    BigDecimal tempC = params.tC as BigDecimal
    BigDecimal temp = (scale == 'F') ? celsiusToFahrenheit(tempC) : tempC
    temp = temp.setScale(1, BigDecimal.ROUND_HALF_UP)
    events.add([name: 'temperature', value: temp, unit: "°${scale}",
      descriptionText: "Temperature is ${temp}°${scale}"])
  }

  // Target temperature (heating setpoint)
  if (params.target != null && params.target != 'null') {
    BigDecimal targetC = params.target as BigDecimal
    BigDecimal target = (scale == 'F') ? celsiusToFahrenheit(targetC) : targetC
    target = target.setScale(1, BigDecimal.ROUND_HALF_UP)
    events.add([name: 'heatingSetpoint', value: target, unit: "°${scale}",
      descriptionText: "Heating setpoint is ${target}°${scale}"])
  }

  // Valve position
  if (params.pos != null && params.pos != 'null') {
    Integer pos = params.pos as Integer
    events.add([name: 'valvePosition', value: pos,
      descriptionText: "Valve position is ${pos}%"])
    String valveState = pos > 0 ? 'open' : 'closed'
    events.add([name: 'valve', value: valveState,
      descriptionText: "Valve is ${valveState}"])
  }

  // Battery percentage
  if (params.batt != null && params.batt != 'null') {
    Integer battery = params.batt as Integer
    events.add([name: 'battery', value: battery, unit: '%',
      descriptionText: "Battery is ${battery}%"])
  }

  return events
}

/**
 * Converts Celsius to Fahrenheit.
 *
 * @param tempC Temperature in Celsius
 * @return Temperature in Fahrenheit
 */
@CompileStatic
private static BigDecimal celsiusToFahrenheit(BigDecimal tempC) {
  return (tempC * 9.0 / 5.0) + 32.0
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst and cid keys, or null if not parseable
 */
@CompileStatic
private Map parseWebhookPath(Map msg) {
  String requestLine = null

  if (msg?.headers) {
    requestLine = ((Map)msg.headers).keySet()?.find { Object key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
  }

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

  String webhookPath = pathAndQuery.startsWith('/') ? pathAndQuery.substring(1) : pathAndQuery
  if (!webhookPath) { return null }

  int qMarkIdx = webhookPath.indexOf('?')
  if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }

  String[] segments = webhookPath.split('/')
  if (segments.length < 2) { return null }

  Map result = [dst: segments[0], cid: segments[1]]

  for (int i = 2; i + 1 < segments.length; i += 2) {
    result[segments[i]] = segments[i + 1]
  }

  return result
}

/**
 * Command entry point for distributeStatus — accepts JSON string.
 * Hubitat requires methods called from apps to be declared as commands.
 * Parses JSON and delegates to the Map overload.
 *
 * @param statusJson JSON string of component statuses
 */
void distributeStatus(String statusJson) {
  logInfo("distributeStatus(String) command called")
  if (!statusJson) { return }
  try {
    Map status = new groovy.json.JsonSlurper().parseText(statusJson) as Map
    distributeStatus(status)
  } catch (Exception e) {
    logError("distributeStatus JSON parse error: ${e.message}")
  }
}

/**
 * Distributes status from Shelly.GetStatus query to TRV child devices.
 * Called by parent app (via command dispatch) or internally from driver code.
 * The command declaration enables the app to call this method;
 * Groovy's method overloading dispatches Map args here directly.
 *
 * @param status Map of component statuses from Shelly.GetStatus
 */
void distributeStatus(Map status) {
  logInfo("distributeStatus(Map) called with keys: ${status?.keySet()}")
  if (!status) { return }

  String scale = getLocationHelper()?.temperatureScale ?: 'F'

  status.each { k, v ->
    String key = k.toString()
    logInfo("distributeStatus: processing key='${key}', isMap=${v instanceof Map}")
    if (!key.contains(':') || !(v instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Integer componentId = key.split(':')[1] as Integer

    if (baseType == 'blutrv') {
      String childDni = "${device.deviceNetworkId}-blutrv-${componentId}"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      logInfo("distributeStatus: looking for child DNI='${childDni}', found=${child != null}")
      if (child) {
        distributeBluTrvStatus(child, v as Map, scale)
      } else {
        logWarn("No TRV child for component ID ${componentId}, DNI: ${childDni}")
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Maps BluTrv.GetStatus response fields to Hubitat events for a TRV child.
 *
 * @param child The TRV child device
 * @param data The blutrv status data map
 * @param scale Temperature scale ('F' or 'C')
 */
private void distributeBluTrvStatus(com.hubitat.app.DeviceWrapper child, Map data, String scale) {
  logInfo("distributeBluTrvStatus: child=${child.displayName}, scale=${scale}, data keys=${data?.keySet()}, data=${data}")

  // Current temperature
  if (data.current_C != null && data.current_C != 'null') {
    BigDecimal tempC = data.current_C as BigDecimal
    BigDecimal temp = (scale == 'F') ? celsiusToFahrenheit(tempC) : tempC
    temp = temp.setScale(1, BigDecimal.ROUND_HALF_UP)
    child.sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
      descriptionText: "Temperature is ${temp}°${scale}")
    logInfo("TRV ${child.displayName}: temperature ${temp}°${scale}")
  }

  // Target temperature (heating setpoint)
  if (data.target_C != null && data.target_C != 'null') {
    BigDecimal targetC = data.target_C as BigDecimal
    BigDecimal target = (scale == 'F') ? celsiusToFahrenheit(targetC) : targetC
    target = target.setScale(1, BigDecimal.ROUND_HALF_UP)
    child.sendEvent(name: 'heatingSetpoint', value: target, unit: "°${scale}",
      descriptionText: "Heating setpoint is ${target}°${scale}")
    logInfo("TRV ${child.displayName}: setpoint ${target}°${scale}")
  }

  // Valve position
  if (data.pos != null && data.pos != 'null') {
    Integer pos = data.pos as Integer
    child.sendEvent(name: 'valvePosition', value: pos,
      descriptionText: "Valve position is ${pos}%")
    String valveState = pos > 0 ? 'open' : 'closed'
    child.sendEvent(name: 'valve', value: valveState,
      descriptionText: "Valve is ${valveState}")
    logInfo("TRV ${child.displayName}: valve ${valveState} (${pos}%)")
  }

  // Battery
  if (data.battery != null && data.battery != 'null') {
    Integer battery = data.battery as Integer
    child.sendEvent(name: 'battery', value: battery, unit: '%',
      descriptionText: "Battery is ${battery}%")
    logDebug("TRV ${child.displayName}: battery ${battery}%")
  }

  // Window open detection
  if (data.window_open != null) {
    String windowState = data.window_open ? 'true' : 'false'
    child.sendEvent(name: 'windowOpen', value: windowState,
      descriptionText: "Window open: ${windowState}")
  }

  // Error conditions
  if (data.errors instanceof List) {
    (data.errors as List).each { String error ->
      logWarn("TRV ${child.displayName} error: ${error}")
    }
  }

  child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END LAN Message Parsing and Event Routing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions (Non-Static Wrappers)                      ║
// ╚══════════════════════════════════════════════════════════════╝

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
 * Non-static helper to get location.
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
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message
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
