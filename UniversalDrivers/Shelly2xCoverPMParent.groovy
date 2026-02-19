/**
 * Shelly Autoconf 2x Cover PM Parent
 *
 * Parent driver for dual-cover Shelly devices with power monitoring.
 * Examples: Shelly Pro Dual Cover PM
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 2 cover PM children as driver-level children in initialize()
 *   - Parent receives LAN traffic, parses locally, routes to children
 *   - Parent aggregates cover state (open if any child open) and power values (sum)
 *   - Commands: child -> parent componentOpen() -> app parentSendCommand() -> Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 2x Cover PM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade'
    //Attributes: windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
    //            position - NUMBER, unit:%
    //Commands: open(), close(), setPosition(position)

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'VoltageMeasurement'
    //Attributes: voltage - NUMBER, unit:V

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'PushableButton'
    capability 'DoubleTapableButton'
    capability 'HoldableButton'

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    command 'reinitialize'
    command 'stopPositionChange'
    command 'resetEnergyMonitors'
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'shadeAggregation', type: 'enum', title: 'Parent Shade State',
    options: ['anyOpen':'Any Cover Open -> Parent Open', 'allOpen':'All Covers Open -> Parent Open'],
    defaultValue: 'anyOpen', required: true
  input name: 'inputAggregation', type: 'enum', title: 'Parent Button Events',
    options: ['any':'Any Input -> Fire Event', 'all':'All Inputs -> Fire Event'],
    defaultValue: 'any', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Lifecycle                                                    ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('Parent device installed')
  initialize()
}

void updated() {
  logDebug('Parent device updated')
  initialize()
  sendPmReportingIntervalToKVS()
}

/**
 * Initializes the parent device: creates/reconciles children, updates aggregate state.
 */
void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
  updateParentCoverState()
}

void configure() {
  logDebug('configure() called')
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

void refresh() {
  logDebug('refresh() called')
  parent?.parentRefresh(device)
}

/**
 * Triggers full reinitialization through the app (handles profile changes).
 */
void reinitialize() {
  logDebug('reinitialize() called')
  parent?.reinitializeDevice(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Lifecycle                                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level child devices against the components data value.
 * Creates children that should exist but don't, and removes orphaned children
 * that exist but shouldn't. Children that already exist correctly are left untouched.
 * Expected: 2 cover PM children, 0-4 input children.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  String pmStr = device.getDataValue('pmComponents') ?: ''
  Set<String> pmSet = pmStr ? pmStr.split(',').collect { it.trim() }.toSet() : ([] as Set)

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Count component types to determine input handling
  Map<String, Integer> componentCounts = [:]
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1
  }

  Integer inputCount = componentCounts['input'] ?: 0

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType == 'input' && inputCount <= 1) { return }
    if (!['cover', 'input'].contains(baseType)) { return }
    desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}".toString())
  }

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children (exist but shouldn't)
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
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

    if (baseType == 'input' && inputCount <= 1) { return }
    if (!['cover', 'input'].contains(baseType)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return } // already exists, leave it alone

    String driverName
    if (baseType == 'cover') {
      driverName = pmSet.contains(comp) ? 'Shelly Autoconf Cover PM' : 'Shelly Autoconf Cover'
    } else if (baseType == 'input') {
      driverName = 'Shelly Autoconf Input Button'
    } else {
      return
    }

    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue("${baseType}Id", compId.toString())
      if (baseType == 'input') {
        child.sendEvent(name: 'numberOfButtons', value: 1)
      }
      logInfo("Created child: ${label} (${driverName})")
    } catch (Exception e) {
      logError("Failed to create child ${label}: ${e.message}")
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Routing (parse)                                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Receives LAN messages, parses locally, routes to children, updates aggregates.
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
    checkAndUpdateSourceIp(msg)

    if (msg?.body) {
      handlePostWebhook(msg)
    } else {
      handleGetWebhook(msg)
    }
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

/**
 * Handles POST webhook notifications from Shelly scripts.
 * Parses JSON body and routes to webhook notification handlers.
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
    routeWebhookNotification(params)
    processWebhookCoverAggregation(params)
    processWebhookInputAggregation(params)
    processWebhookPowerAggregation(params)
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
  Map params = parseWebhookPath(msg)
  if (params?.dst) {
    logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
    logTrace("GET webhook params: ${params}")
    routeWebhookNotification(params)
    processWebhookCoverAggregation(params)
    processWebhookInputAggregation(params)
    processWebhookPowerAggregation(params)
  } else {
    logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
  }
}

/**
 * Routes webhook notification to children.
 *
 * @param params The parsed webhook parameters including dst, cid, and event data
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) {
    logTrace("routeWebhookNotification: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  Integer componentId = params.cid as Integer

  // For powermon, component type comes from the 'comp' query param
  String baseType = (dst == 'powermon' && params.comp) ?
      (params.comp as String) : dstToComponentType(dst)

  List<Map> events = buildWebhookEvents(dst, params)
  logTrace("buildWebhookEvents(dst=${dst}) -> ${events.size()} events: ${events}")
  if (!events) { return }

  boolean routeToChild = false
  if (baseType == 'cover') {
    routeToChild = true
  } else if (baseType == 'input') {
    String componentStr = device.getDataValue('components') ?: ''
    Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
    routeToChild = (inputCount > 1)
  }

  if (routeToChild) {
    String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt ->
        logTrace("Sending webhook event to ${child.displayName}: ${evt}")
        child.sendEvent(evt)
      }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Routed ${events.size()} webhook events to ${child.displayName}")
    } else {
      logDebug("No child device found for DNI: ${childDni}")
    }
  } else {
    events.each { Map evt -> sendEvent(evt) }
  }
}

/**
 * Maps a webhook dst parameter to its Shelly component type.
 *
 * @param dst The webhook destination (e.g., 'cover_open', 'input_toggle')
 * @return The Shelly component type (e.g., 'cover', 'input')
 */
private String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('switch_')) { return 'switch' }
  if (dst.startsWith('cover_')) { return 'cover' }
  switch (dst) {
    case 'covermon': return 'cover'
    default: return dst
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
 * If different, updates the device data value and notifies the parent app.
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Building                                               ║
// ╚══════════════════════════════════════════════════════════════╝

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
 * Builds events from webhook GET parameters for cover, input, and power monitoring.
 *
 * @param dst The webhook destination type
 * @param params The parsed webhook parameters
 * @return List of event maps to send to child or parent
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    // Discrete cover webhooks — state is encoded in the dst name
    case 'cover_open':
      events.add([name: 'windowShade', value: 'open', descriptionText: 'Window shade is open'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%"])
      }
      break
    case 'cover_closed':
      events.add([name: 'windowShade', value: 'closed', descriptionText: 'Window shade is closed'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%"])
      }
      break
    case 'cover_opening':
      events.add([name: 'windowShade', value: 'opening', descriptionText: 'Window shade is opening'])
      break
    case 'cover_closing':
      events.add([name: 'windowShade', value: 'closing', descriptionText: 'Window shade is closing'])
      break
    case 'cover_stopped':
      events.add([name: 'windowShade', value: 'partially open', descriptionText: 'Window shade is partially open'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%"])
      }
      break
    case 'cover_calibrating':
      events.add([name: 'windowShade', value: 'unknown', descriptionText: 'Window shade is calibrating'])
      break

    // Legacy combined cover webhook — state is in params.state
    case 'covermon':
      if (params.state != null) {
        String shadeState = mapCoverState(params.state as String)
        events.add([name: 'windowShade', value: shadeState,
          descriptionText: "Window shade is ${shadeState}"])
      }
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%"])
      }
      break

    // Input webhooks
    case 'input_push':
      events.add([name: 'pushed', value: 1, isStateChange: true, descriptionText: 'Button 1 was pushed'])
      break
    case 'input_double':
      events.add([name: 'doubleTapped', value: 1, isStateChange: true, descriptionText: 'Button 1 was double-tapped'])
      break
    case 'input_long':
      events.add([name: 'held', value: 1, isStateChange: true, descriptionText: 'Button 1 was held'])
      break
    case 'input_triple':
      events.add([name: 'pushed', value: 3, isStateChange: true, descriptionText: 'Button 1 was triple-pushed'])
      break
    case 'input_toggle_on':
      events.add([name: 'switch', value: 'on', isStateChange: true, descriptionText: 'Input toggled on'])
      break
    case 'input_toggle_off':
      events.add([name: 'switch', value: 'off', isStateChange: true, descriptionText: 'Input toggled off'])
      break

    // Power monitoring via GET (from powermonitoring.js)
    case 'powermon':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V',
          descriptionText: "Voltage is ${params.voltage}V"])
      }
      if (params.current != null) {
        events.add([name: 'amperage', value: params.current as BigDecimal, unit: 'A',
          descriptionText: "Current is ${params.current}A"])
      }
      if (params.apower != null) {
        events.add([name: 'power', value: params.apower as BigDecimal, unit: 'W',
          descriptionText: "Power is ${params.apower}W"])
      }
      if (params.aenergy != null) {
        BigDecimal energyWh = params.aenergy as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy is ${energyKwh}kWh"])
      }
      if (params.freq != null) {
        events.add([name: 'frequency', value: params.freq as BigDecimal, unit: 'Hz',
          descriptionText: "Frequency is ${params.freq}Hz"])
      }
      break

    // Temperature webhook
    case 'temperature':
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
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
        events.add([name: 'temperature', value: temp, unit: "\u00B0${scale}",
          descriptionText: "Temperature is ${temp}\u00B0${scale}"])
      }
      break
  }

  return events
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Building                                           ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Handles open() command from child cover.
 *
 * @param childDevice The child device requesting the command
 */
void componentOpen(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentOpen() from cover ${coverId}")
  parent?.parentSendCommand(device, 'Cover.Open', [id: coverId])
}

/**
 * Handles close() command from child cover.
 *
 * @param childDevice The child device requesting the command
 */
void componentClose(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentClose() from cover ${coverId}")
  parent?.parentSendCommand(device, 'Cover.Close', [id: coverId])
}

/**
 * Handles setPosition() command from child cover.
 *
 * @param childDevice The child device requesting the command
 * @param position Target position (0-100)
 */
void componentSetPosition(def childDevice, Integer position) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentSetPosition(${position}) from cover ${coverId}")
  parent?.parentSendCommand(device, 'Cover.GoToPosition', [id: coverId, pos: position])
}

/**
 * Handles stopPositionChange() command from child cover.
 *
 * @param childDevice The child device requesting the command
 */
void componentStop(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentStop() from cover ${coverId}")
  parent?.parentSendCommand(device, 'Cover.Stop', [id: coverId])
}

/**
 * Handles refresh() command from child.
 *
 * @param childDevice The child device requesting refresh
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

/**
 * Handles resetEnergyMonitors() command from child.
 *
 * @param childDevice The child device requesting reset
 */
void componentResetEnergyMonitors(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentResetEnergyMonitors() from cover ${coverId}")
  parent?.parentSendCommand(device, 'Cover.ResetCounters', [id: coverId, type: ['aenergy']])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Cover Commands and Aggregation                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens all covers.
 */
void open() {
  logDebug('Parent open() — opening all covers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cover-') }.each { child ->
    Integer coverId = child.getDataValue('coverId') as Integer
    parent?.parentSendCommand(device, 'Cover.Open', [id: coverId])
  }
}

/**
 * Closes all covers.
 */
void close() {
  logDebug('Parent close() — closing all covers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cover-') }.each { child ->
    Integer coverId = child.getDataValue('coverId') as Integer
    parent?.parentSendCommand(device, 'Cover.Close', [id: coverId])
  }
}

/**
 * Sets position on all covers.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("Parent setPosition(${position}) — setting all covers")
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cover-') }.each { child ->
    Integer coverId = child.getDataValue('coverId') as Integer
    parent?.parentSendCommand(device, 'Cover.GoToPosition', [id: coverId, pos: position as Integer])
  }
}

/**
 * Stops all in-progress cover movements.
 */
void stopPositionChange() {
  logDebug('Parent stopPositionChange() — stopping all covers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cover-') }.each { child ->
    Integer coverId = child.getDataValue('coverId') as Integer
    parent?.parentSendCommand(device, 'Cover.Stop', [id: coverId])
  }
}

/**
 * Resets energy monitoring counters for all covers.
 */
void resetEnergyMonitors() {
  logDebug('resetEnergyMonitors() called')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cover-') }.each { child ->
    Integer coverId = child.getDataValue('coverId') as Integer
    parent?.parentSendCommand(device, 'Cover.ResetCounters', [id: coverId, type: ['aenergy']])
  }
}

/**
 * Processes cover state aggregation from webhook notifications.
 * Stores per-component cover state and recalculates parent aggregate.
 *
 * @param params The parsed webhook parameters
 */
private void processWebhookCoverAggregation(Map params) {
  String dst = params.dst
  if (!dst) { return }

  // Only process cover-related webhooks
  String coverState = null
  Integer position = null
  switch (dst) {
    case 'cover_open': coverState = 'open'; break
    case 'cover_closed': coverState = 'closed'; break
    case 'cover_opening': coverState = 'opening'; break
    case 'cover_closing': coverState = 'closing'; break
    case 'cover_stopped': coverState = 'partially open'; break
    case 'cover_calibrating': coverState = 'unknown'; break
    case 'covermon':
      if (params.state != null) { coverState = mapCoverState(params.state as String) }
      break
    default: return // not a cover webhook
  }

  if (coverState == null) { return }

  String cid = params.cid as String
  Map coverStates = state.coverStates ?: [:]
  coverStates[cid] = coverState
  state.coverStates = coverStates

  if (params.pos != null) {
    position = params.pos as Integer
    Map coverPositions = state.coverPositions ?: [:]
    coverPositions[cid] = position
    state.coverPositions = coverPositions
  }

  updateParentCoverState()
}

/**
 * Updates aggregate cover state based on preference.
 * "anyOpen" mode: parent is "open" if any child is open/opening/partially open.
 * "allOpen" mode: parent is "open" only if all children are fully open.
 */
private void updateParentCoverState() {
  Map coverStates = state.coverStates ?: [:]
  if (coverStates.isEmpty()) { return }

  String mode = settings.shadeAggregation ?: 'anyOpen'
  Set<String> openStates = ['open', 'opening', 'partially open'] as Set

  String newState
  if (mode == 'allOpen') {
    Boolean allOpen = coverStates.values().every { it == 'open' }
    newState = allOpen ? 'open' : 'closed'
  } else {
    Boolean anyOpen = coverStates.values().any { openStates.contains(it) }
    newState = anyOpen ? 'open' : 'closed'
  }

  if (device.currentValue('windowShade') != newState) {
    sendEvent(name: 'windowShade', value: newState, descriptionText: "Parent shade is ${newState}")
    logInfo("Parent shade: ${newState} (mode: ${mode})")
  }

  // Aggregate position as average of all cover positions
  Map coverPositions = state.coverPositions ?: [:]
  if (!coverPositions.isEmpty()) {
    Integer avgPosition = (coverPositions.values().sum { it as Integer } / coverPositions.size()) as Integer
    sendEvent(name: 'position', value: avgPosition, unit: '%',
      descriptionText: "Average position is ${avgPosition}%")
  }
}

/**
 * Processes webhook power aggregation for parent device.
 * Stores per-component power in state and re-aggregates totals across all components.
 *
 * @param params The parsed webhook query parameters including power values
 */
private void processWebhookPowerAggregation(Map params) {
  String dst = params.dst
  if (dst != 'powermon') { return }

  String cid = params.cid as String
  Map powerStates = state.powerStates ?: [:]

  // Store per-component power data
  Map compPower = [:]
  if (params.voltage != null) { compPower.voltage = params.voltage as BigDecimal }
  if (params.current != null) { compPower.current = params.current as BigDecimal }
  if (params.apower != null) { compPower.apower = params.apower as BigDecimal }
  if (params.aenergy != null) { compPower.aenergy = params.aenergy as BigDecimal }
  powerStates[cid] = compPower
  state.powerStates = powerStates

  // Re-aggregate totals across all components
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  powerStates.each { key, value ->
    if (value instanceof Map) {
      if (value.apower != null) { totalPower += value.apower as BigDecimal }
      if (value.aenergy != null) { totalEnergy += (value.aenergy as BigDecimal) / 1000 }
      if (value.current != null) { totalCurrent += value.current as BigDecimal }
      if (value.voltage != null) {
        BigDecimal v = value.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
    }
  }

  sendEvent(name: 'power', value: totalPower, unit: 'W', descriptionText: "Total power: ${totalPower}W")
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh', descriptionText: "Total energy: ${totalEnergy}kWh")
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A', descriptionText: "Total current: ${totalCurrent}A")
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V', descriptionText: "Voltage: ${maxVoltage}V")
  logDebug("Aggregate power: ${totalPower}W, energy: ${totalEnergy}kWh, current: ${totalCurrent}A, voltage: ${maxVoltage}V")
}

/**
 * Processes webhook input aggregation for parent device.
 * Fires button events on parent when there is a single input (no child).
 *
 * @param params The parsed webhook parameters
 */
private void processWebhookInputAggregation(Map params) {
  String dst = params.dst
  if (!['input_push', 'input_double', 'input_long'].contains(dst)) { return }

  String componentStr = device.getDataValue('components') ?: ''
  Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
  if (inputCount > 1) { return } // children handle it

  String eventName = dst == 'input_push' ? 'pushed' : (dst == 'input_double' ? 'doubleTapped' : 'held')
  sendEvent(name: 'numberOfButtons', value: 1)
  sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button was ${eventName}")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Cover Commands and Aggregation                    ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes Shelly.GetStatus response to children and parent.
 * Called by app's parentRefresh() after querying device.
 *
 * @param deviceStatus Map of component statuses from Shelly.GetStatus
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.contains(':') || !(v instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Integer componentId = key.split(':')[1] as Integer

    List<Map> events = buildStatusEvents(baseType, v as Map)
    if (!events) { return }

    // Determine routing
    boolean routeToChild = false
    if (baseType == 'cover') {
      routeToChild = true
    } else if (baseType == 'input') {
      String componentStr = device.getDataValue('components') ?: ''
      Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
      routeToChild = (inputCount > 1)
    }

    if (routeToChild) {
      String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt -> child.sendEvent(evt) }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    } else {
      events.each { Map evt -> sendEvent(evt) }
    }
  }

  // Update aggregates
  updateAggregatesFromStatus(deviceStatus)
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Builds events from Shelly.GetStatus component data.
 *
 * @param componentType The component type (cover, input, etc.)
 * @param statusData The status data map for this component
 * @return List of event maps
 */
private List<Map> buildStatusEvents(String componentType, Map statusData) {
  List<Map> events = []

  if (componentType == 'cover') {
    if (statusData.state != null) {
      String shadeState = mapCoverState(statusData.state as String)
      events.add([name: 'windowShade', value: shadeState])
    }
    if (statusData.current_pos != null) {
      events.add([name: 'position', value: statusData.current_pos as Integer, unit: '%'])
    }
    if (statusData.voltage != null) {
      events.add([name: 'voltage', value: statusData.voltage as BigDecimal, unit: 'V'])
    }
    if (statusData.current != null) {
      events.add([name: 'amperage', value: statusData.current as BigDecimal, unit: 'A'])
    }
    if (statusData.apower != null) {
      events.add([name: 'power', value: statusData.apower as BigDecimal, unit: 'W'])
    }
    if (statusData.aenergy?.total != null) {
      BigDecimal energyKwh = (statusData.aenergy.total as BigDecimal) / 1000
      events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
    }
  }

  return events
}

/**
 * Updates parent aggregates from status data.
 *
 * @param deviceStatus The full device status map
 */
private void updateAggregatesFromStatus(Map deviceStatus) {
  Map coverStates = [:]
  Map coverPositions = [:]
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('cover:') && v instanceof Map) {
      if (v.state != null) {
        coverStates[key] = mapCoverState(v.state as String)
      }
      if (v.current_pos != null) {
        coverPositions[key] = v.current_pos as Integer
      }
      if (v.apower != null) {
        totalPower += v.apower as BigDecimal
      }
      if (v.aenergy?.total != null) {
        totalEnergy += (v.aenergy.total as BigDecimal) / 1000
      }
      if (v.current != null) {
        totalCurrent += v.current as BigDecimal
      }
      if (v.voltage != null) {
        BigDecimal volt = v.voltage as BigDecimal
        if (volt > maxVoltage) { maxVoltage = volt }
      }
    }
  }

  state.coverStates = coverStates
  state.coverPositions = coverPositions
  updateParentCoverState()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')

  // Temperature from device internal sensor (if present)
  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('temperature:') && v instanceof Map) {
      BigDecimal tempC = v.tC != null ? v.tC as BigDecimal : null
      BigDecimal tempF = v.tF != null ? v.tF as BigDecimal : null
      if (tempC != null || tempF != null) {
        String scale = getLocationHelper()?.temperatureScale ?: 'F'
        BigDecimal temp
        if (scale == 'C') {
          temp = tempC
        } else if (tempF != null) {
          temp = tempF
        } else if (tempC != null) {
          temp = tempC * 9 / 5 + 32
        }
        if (temp != null) {
          sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}",
            descriptionText: "Temperature is ${temp}\u00B0${scale}")
        }
      }
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions (Non-Static Wrappers)                       ║
// ╚══════════════════════════════════════════════════════════════╝

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
// ║  END Helper Functions                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                              ║
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

String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                           ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                       ║
// ╚══════════════════════════════════════════════════════════════╝
