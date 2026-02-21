/**
 * Shelly Autoconf Plus Uni Parent
 *
 * Parent driver for the Shelly Plus Uni (Gen2) — a modular IoT device with
 * 2 open-drain switches with PM, 2 configurable digital inputs (button/switch/analog),
 * 1 dedicated pulse counter input, and optional peripheral support
 * (voltmeter/ADC, DS18B20 temperature, DHT22 temperature+humidity).
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates switch, input, and optional peripheral children in initialize()
 *   - Input:0 and Input:1 children adapt to the configured input mode (button/switch/analog)
 *   - Input:2 is always a pulse counter using the Input Count driver
 *   - Mode changes delete and recreate the input child with the correct driver
 *   - Parent receives LAN traffic, parses locally, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and power values (sum)
 *   - Button-mode input events are mirrored on the parent (button 1 = input:0, button 2 = input:1)
 *   - Commands: child → parent componentOn() → app parentSendCommand() → Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Plus Uni Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'PowerMeter'
    capability 'EnergyMeter'
    capability 'CurrentMeter'
    capability 'VoltageMeasurement'
    capability 'PushableButton'
    capability 'DoubleTapableButton'
    capability 'HoldableButton'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'switchAggregation', type: 'enum', title: 'Parent Switch State',
    options: ['anyOn':'Any Switch On → Parent On', 'allOn':'All Switches On → Parent On'],
    defaultValue: 'anyOn', required: true
  input name: 'inputAggregation', type: 'enum', title: 'Parent Button Events',
    options: ['any':'Any Input → Fire Event', 'all':'All Inputs → Fire Event'],
    defaultValue: 'any', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
  input name: 'input0Mode', type: 'enum', title: 'Input 0 Mode',
    options: ['button':'Button', 'switch':'Switch', 'analog':'Analog'],
    defaultValue: 'button', required: true
  input name: 'input1Mode', type: 'enum', title: 'Input 1 Mode',
    options: ['button':'Button', 'switch':'Switch', 'analog':'Analog'],
    defaultValue: 'button', required: true
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
  syncInputModes()
}

/**
 * Initializes the parent device: reconciles children, sets button count,
 * and updates aggregate switch state.
 */
void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
  sendEvent(name: 'numberOfButtons', value: 2)
  updateParentSwitchState()
}

void configure() {
  logDebug('configure() called')
  syncInputModes()
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

/**
 * Pushes the current input mode settings to the Shelly device via RPC.
 * Input:0 and Input:1 can be configured as button, switch, or analog.
 * Input:2 is always a counter and is not configurable here.
 */
private void syncInputModes() {
  String mode0 = (settings?.input0Mode ?: 'button').toString()
  String mode1 = (settings?.input1Mode ?: 'button').toString()
  parent?.parentSendCommand(device, 'Input.SetConfig', [id: 0, config: [type: mode0]])
  parent?.parentSendCommand(device, 'Input.SetConfig', [id: 1, config: [type: mode1]])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Lifecycle                                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Maps a Plus Uni component type, ID, and current mode settings to its child driver name.
 *
 * @param baseType  The component type (switch, input, voltmeter, temperature, humidity)
 * @param compId    The component ID number
 * @param hasPm     Whether this component has power monitoring capability
 * @param mode0     Current mode for input:0 (button/switch/analog)
 * @param mode1     Current mode for input:1 (button/switch/analog)
 * @return The Hubitat driver name for this component, or null to skip child creation
 */
@CompileStatic
private static String getChildDriverNameForComponent(String baseType, Integer compId, Boolean hasPm, String mode0, String mode1) {
  switch (baseType) {
    case 'switch':
      return hasPm ? 'Shelly Autoconf Switch PM' : 'Shelly Autoconf Switch'
    case 'input':
      if (compId == 2) { return 'Shelly Autoconf Input Count' }
      String mode = (compId == 0) ? mode0 : mode1
      switch (mode) {
        case 'switch': return 'Shelly Autoconf Input Switch'
        case 'analog': return 'Shelly Autoconf Input Analog'
        default:       return 'Shelly Autoconf Input Button'
      }
    case 'voltmeter':   return 'Shelly Autoconf Voltmeter'
    case 'temperature': return 'Shelly Autoconf Temperature Peripheral'
    case 'humidity':    return 'Shelly Autoconf Humidity Peripheral'
    default:            return null
  }
}

/**
 * Reconciles driver-level child devices against the components data value.
 * Creates children that should exist but don't, removes orphaned children,
 * and recreates children whose driver has changed due to an input mode change.
 * The installed driver name is stored as a data value on each child to detect mismatches.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  String pmStr = device.getDataValue('pmComponents') ?: ''
  Set<String> pmSet = pmStr ? pmStr.split(',').collect { it.trim() }.toSet() : ([] as Set)

  String mode0 = (settings?.input0Mode ?: 'button').toString()
  String mode1 = (settings?.input1Mode ?: 'button').toString()

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Build desired DNI → driver name map
  Map<String, String> desiredDriverMap = [:]
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}".toString()
    String driverName = getChildDriverNameForComponent(baseType, compId, pmSet.contains(comp), mode0, mode1)
    if (driverName) {
      desiredDriverMap[childDni] = driverName
    }
  }

  // Get existing children
  Map<String, com.hubitat.app.DeviceWrapper> existingChildren = [:]
  getChildDevices()?.each { com.hubitat.app.DeviceWrapper child ->
    existingChildren[child.deviceNetworkId] = child
  }

  logDebug("Child reconciliation: desired=${desiredDriverMap.keySet()}, existing=${existingChildren.keySet()}")

  // Remove orphaned children and detect driver mismatches (input mode changes)
  existingChildren.each { String dni, com.hubitat.app.DeviceWrapper child ->
    if (!desiredDriverMap.containsKey(dni)) {
      logInfo("Removing orphaned child: ${child.displayName} (${dni})")
      deleteChildDevice(dni)
    } else {
      // Detect driver mismatch caused by input mode change — delete so it can be recreated
      String installedDriver = child.getDataValue('installedDriverName')
      String desiredDriver = desiredDriverMap[dni]
      if (installedDriver && installedDriver != desiredDriver) {
        logInfo("Input mode change detected for ${child.displayName}: was '${installedDriver}', should be '${desiredDriver}' — recreating")
        deleteChildDevice(dni)
      }
    }
  }

  // Create missing children
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}".toString()

    if (!desiredDriverMap.containsKey(childDni)) { return }
    if (getChildDevice(childDni)) { return }

    String driverName = desiredDriverMap[childDni]
    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      com.hubitat.app.DeviceWrapper child = addChildDevice('ShellyDeviceManager', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue("${baseType}Id", compId.toString())
      child.updateDataValue('installedDriverName', driverName)
      if (baseType == 'input' && driverName == 'Shelly Autoconf Input Button') {
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
    processWebhookAggregation(params)
    processWebhookPowerAggregation(params)
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

/**
 * Handles GET webhook notifications from Shelly Action Webhooks.
 * Parses path segments to extract dst and cid, then routes to handlers.
 *
 * @param msg The parsed LAN message map (no body)
 */
private void handleGetWebhook(Map msg) {
  Map params = parseWebhookPath(msg)
  if (params?.dst) {
    logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
    logTrace("GET webhook params: ${params}")
    routeWebhookNotification(params)
    processWebhookAggregation(params)
    processWebhookPowerAggregation(params)
  } else {
    logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}")
  }
}

/**
 * Routes webhook notification to the appropriate child device.
 * All Plus Uni components have dedicated children — always routes to child.
 *
 * @param params Parsed webhook parameters including dst and cid
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) {
    logTrace("routeWebhookNotification: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  Integer componentId = params.cid as Integer

  // For powermon, base type comes from the optional 'comp' param (script POSTs)
  String baseType = (dst == 'powermon' && params.comp) ?
      (params.comp as String) : dstToComponentType(dst)

  List<Map> events = buildWebhookEvents(dst, params)
  logTrace("buildWebhookEvents(dst=${dst}) → ${events?.size()} events: ${events}")
  if (!events) { return }

  // All Plus Uni components have child devices — always route to child
  String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
  com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
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
}

/**
 * Maps a webhook dst parameter to its Shelly component base type.
 *
 * @param dst The webhook destination string (e.g., 'switch_on', 'voltmeter', 'input_count')
 * @return The Shelly component base type (e.g., 'switch', 'voltmeter', 'input')
 */
@CompileStatic
private static String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('switch_')) { return 'switch' }
  switch (dst) {
    case 'powermon':    return 'switch'
    case 'voltmeter':   return 'voltmeter'
    case 'temperature': return 'temperature'
    case 'humidity':    return 'humidity'
    default:            return dst
  }
}

/**
 * Parses webhook GET request path to extract dst, cid, and key/value pairs.
 * Falls back to raw header string if parsed headers Map lacks the request line.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst, cid and optional key/value pairs, or null if not parseable
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
 * Builds Hubitat event maps from webhook parameters for all Plus Uni components.
 * Handles switch (on/off), input (button/toggle/analog/count), voltmeter,
 * temperature peripheral, humidity peripheral, and power monitoring events.
 *
 * @param dst    The webhook destination identifier (e.g., 'switch_on', 'input_count', 'voltmeter')
 * @param params The parsed webhook path parameters (key/value String pairs)
 * @return List of Hubitat event maps to send to the routed child or parent device
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    // Switch state events
    case 'switch_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Switch turned on'])
      break
    case 'switch_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Switch turned off'])
      break

    // Input button events (button-mode inputs)
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

    // Input toggle events (switch-mode inputs)
    case 'input_toggle_on':
      events.add([name: 'switch', value: 'on', isStateChange: true, descriptionText: 'Input toggled on'])
      break
    case 'input_toggle_off':
      events.add([name: 'switch', value: 'off', isStateChange: true, descriptionText: 'Input toggled off'])
      break

    // Input analog events (analog-mode inputs)
    case 'input_analog':
      if (params.percent != null) {
        events.add([name: 'analogValue', value: params.percent as BigDecimal, unit: '%',
          descriptionText: "Analog value: ${params.percent}%"])
      }
      break

    // Pulse counter events (input:2 is always count type)
    case 'input_count':
      if (params.total != null) {
        events.add([name: 'count', value: params.total as Long,
          descriptionText: "Count total: ${params.total}"])
      }
      if (params.freq != null) {
        events.add([name: 'freq', value: params.freq as BigDecimal, unit: 'Hz',
          descriptionText: "Pulse frequency: ${params.freq} Hz"])
      }
      break

    // Voltmeter events (optional analog_in peripheral)
    case 'voltmeter':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V',
          descriptionText: "Voltage: ${params.voltage}V"])
      }
      break

    // Temperature peripheral events (DS18B20)
    case 'temperature':
      String scale = location?.temperatureScale ?: 'F'
      if (scale == 'C' && params.tC != null) {
        events.add([name: 'temperature', value: params.tC as BigDecimal, unit: '°C',
          descriptionText: "Temperature: ${params.tC}°C"])
      } else if (params.tF != null) {
        events.add([name: 'temperature', value: params.tF as BigDecimal, unit: '°F',
          descriptionText: "Temperature: ${params.tF}°F"])
      } else if (params.tC != null) {
        BigDecimal tF = (params.tC as BigDecimal) * 9 / 5 + 32
        events.add([name: 'temperature', value: tF, unit: '°F',
          descriptionText: "Temperature: ${tF}°F"])
      }
      break

    // Humidity peripheral events (DHT22)
    case 'humidity':
      if (params.rh != null) {
        events.add([name: 'humidity', value: params.rh as BigDecimal, unit: '%',
          descriptionText: "Humidity: ${params.rh}%"])
      }
      break

    // Power monitoring events (routed to switch child via powermon dst)
    case 'powermon':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V',
          descriptionText: "Voltage: ${params.voltage}V"])
      }
      if (params.current != null) {
        events.add([name: 'amperage', value: params.current as BigDecimal, unit: 'A',
          descriptionText: "Current: ${params.current}A"])
      }
      if (params.apower != null) {
        events.add([name: 'power', value: params.apower as BigDecimal, unit: 'W',
          descriptionText: "Power: ${params.apower}W"])
      }
      if (params.aenergy != null) {
        BigDecimal energyKwh = (params.aenergy as BigDecimal) / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy: ${energyKwh}kWh"])
      }
      break
  }

  if (params.battPct != null) {
    events.add([name: 'battery', value: params.battPct as Integer, unit: '%'])
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
 * Handles on() command from child switch.
 *
 * @param childDevice The switch child requesting on
 */
void componentOn(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

/**
 * Handles off() command from child switch.
 *
 * @param childDevice The switch child requesting off
 */
void componentOff(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

/**
 * Handles refresh() command from any child.
 *
 * @param childDevice The child requesting a refresh
 */
void componentRefresh(com.hubitat.app.DeviceWrapper childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

/**
 * Handles resetEnergyMonitors() from switch PM child.
 *
 * @param childDevice The switch PM child requesting energy counter reset
 */
void componentResetEnergyMonitors(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentResetEnergyMonitors() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.ResetCounters', [id: switchId, type: ['aenergy']])
}

/**
 * Relays switch settings from a child component to the app.
 *
 * @param childDevice The child device sending its settings
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateSwitchSettings(com.hubitat.app.DeviceWrapper childDevice, Map switchSettings) {
  Integer switchId = childDevice.getDataValue('switchId')?.toInteger() ?: 0
  logDebug("componentUpdateSwitchSettings() from switch ${switchId}: ${switchSettings}")
  parent?.parentUpdateSwitchSettings(device, switchId, switchSettings)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Switch Commands and Aggregation                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on all switch children and optimistically updates parent switch state.
 */
void on() {
  logDebug('Parent on() — turning on all switches')
  Map newStates = state.switchStates ?: [:]
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
    newStates["switch:${switchId}".toString()] = true
  }
  state.switchStates = newStates
  updateParentSwitchState()
}

/**
 * Turns off all switch children and optimistically updates parent switch state.
 */
void off() {
  logDebug('Parent off() — turning off all switches')
  Map newStates = state.switchStates ?: [:]
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
    newStates["switch:${switchId}".toString()] = false
  }
  state.switchStates = newStates
  updateParentSwitchState()
}

/**
 * Updates the parent switch state based on child switch states.
 * Uses anyOn or allOn aggregation based on user preference.
 */
private void updateParentSwitchState() {
  Map switchStates = state.switchStates ?: [:]
  if (switchStates.isEmpty()) { return }

  String mode = settings.switchAggregation ?: 'anyOn'
  Boolean parentOn = (mode == 'allOn') ?
    switchStates.values().every { it == true } :
    switchStates.values().any { it == true }

  String newState = parentOn ? 'on' : 'off'
  if (device.currentValue('switch') != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Parent switch is ${newState}")
    logInfo("Parent switch: ${newState} (mode: ${mode})")
  }
}

/**
 * Processes webhook aggregation: tracks switch states and mirrors button events on parent.
 * Switch on/off events update the switchStates map for aggregate recalculation.
 * Button events from button-mode inputs are mirrored on the parent device
 * (input:0 → parent button 1, input:1 → parent button 2).
 *
 * @param params The parsed webhook parameters
 */
private void processWebhookAggregation(Map params) {
  String dst = params.dst
  Integer componentId = params.cid != null ? params.cid as Integer : -1

  // Track switch state for parent aggregate
  if (dst == 'switch_on' || dst == 'switch_off') {
    Map switchStates = state.switchStates ?: [:]
    switchStates["switch:${componentId}".toString()] = (dst == 'switch_on')
    state.switchStates = switchStates
    updateParentSwitchState()
    return
  }

  // Mirror button events on parent for button-mode inputs (input:0 and input:1 only)
  if (!['input_push', 'input_double', 'input_long', 'input_triple'].contains(dst)) { return }
  if (componentId < 0 || componentId > 1) { return }

  // Only mirror if the input is configured as button mode
  String mode = componentId == 0 ? (settings?.input0Mode ?: 'button').toString() :
                                   (settings?.input1Mode ?: 'button').toString()
  if (mode != 'button') { return }

  Integer parentButton = componentId + 1  // input:0 → button 1, input:1 → button 2
  String eventName = dst == 'input_push'   ? 'pushed'      :
                     dst == 'input_double' ? 'doubleTapped' :
                     dst == 'input_long'   ? 'held'         : 'pushed'
  Integer buttonValue = parentButton

  sendEvent(name: 'numberOfButtons', value: 2)
  sendEvent(name: eventName, value: buttonValue, isStateChange: true,
    descriptionText: "Input ${componentId} ${eventName}")
  logInfo("Parent button event: ${eventName} button ${buttonValue}")
}

/**
 * Processes webhook power monitoring data and updates parent PM aggregate attributes.
 * Stores per-switch power in state and re-sums across all switches.
 *
 * @param params The parsed webhook parameters including power values
 */
private void processWebhookPowerAggregation(Map params) {
  String dst = params.dst
  if (dst != 'powermon') { return }

  String cid = params.cid as String
  Map powerStates = state.powerStates ?: [:]

  Map compPower = [:]
  if (params.voltage != null) { compPower.voltage = params.voltage as BigDecimal }
  if (params.current != null) { compPower.current = params.current as BigDecimal }
  if (params.apower != null)  { compPower.apower  = params.apower  as BigDecimal }
  if (params.aenergy != null) { compPower.aenergy = params.aenergy as BigDecimal }
  powerStates[cid] = compPower
  state.powerStates = powerStates

  BigDecimal totalPower   = 0
  BigDecimal totalEnergy  = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage   = 0

  powerStates.each { key, value ->
    if (value instanceof Map) {
      if (value.apower  != null) { totalPower   += value.apower  as BigDecimal }
      if (value.aenergy != null) { totalEnergy  += (value.aenergy as BigDecimal) / 1000 }
      if (value.current != null) { totalCurrent += value.current as BigDecimal }
      if (value.voltage != null) {
        BigDecimal v = value.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
    }
  }

  sendEvent(name: 'power',    value: totalPower,   unit: 'W',   descriptionText: "Total power: ${totalPower}W")
  sendEvent(name: 'energy',   value: totalEnergy,  unit: 'kWh', descriptionText: "Total energy: ${totalEnergy}kWh")
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A',   descriptionText: "Total current: ${totalCurrent}A")
  sendEvent(name: 'voltage',  value: maxVoltage,   unit: 'V',   descriptionText: "Voltage: ${maxVoltage}V")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Switch Commands and Aggregation                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes Shelly.GetStatus response to children and updates parent aggregates.
 * Called by the app's parentRefresh() after querying the device.
 *
 * Routes:
 *   switch:N      → switch child (on/off, voltage, current, power, energy) + parent PM aggregate
 *   input:2       → input count child (count, freq) — input:0/1 are event-driven via webhooks
 *   voltmeter:N   → voltmeter child (voltage)
 *   temperature:N → temperature peripheral child (tC or tF)
 *   humidity:N    → humidity peripheral child (relative humidity)
 *
 * @param deviceStatus Map of component key → status data from Shelly.GetStatus
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  Map switchStates  = state.switchStates  ?: [:]
  Map powerStates   = state.powerStates   ?: [:]

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    // Switch (relay) data → route to switch child + track for aggregation
    if (key.startsWith('switch:')) {
      Integer componentId = key.split(':')[1] as Integer
      String childDni = "${device.deviceNetworkId}-switch-${componentId}"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      if (child) {
        if (data.output != null) {
          child.sendEvent(name: 'switch', value: (data.output ? 'on' : 'off'))
          switchStates[key] = data.output
        }
        if (data.voltage != null) { child.sendEvent(name: 'voltage',  value: data.voltage as BigDecimal,                   unit: 'V')   }
        if (data.current != null) { child.sendEvent(name: 'amperage', value: data.current as BigDecimal,                   unit: 'A')   }
        if (data.apower  != null) { child.sendEvent(name: 'power',    value: data.apower  as BigDecimal,                   unit: 'W')   }
        if (data.aenergy?.total != null) {
          child.sendEvent(name: 'energy', value: (data.aenergy.total as BigDecimal) / 1000, unit: 'kWh')
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))

        // Track per-switch power for parent aggregate
        Map compPower = [:]
        if (data.voltage != null)       { compPower.voltage = data.voltage       as BigDecimal }
        if (data.current != null)       { compPower.current = data.current       as BigDecimal }
        if (data.apower  != null)       { compPower.apower  = data.apower        as BigDecimal }
        if (data.aenergy?.total != null){ compPower.aenergy = data.aenergy.total as BigDecimal }
        powerStates[componentId.toString()] = compPower
      }
    }

    // Input count data (input:2) → route to input count child
    if (key == 'input:2') {
      String childDni = "${device.deviceNetworkId}-input-2"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      if (child) {
        if (data.counts?.total != null) {
          child.sendEvent(name: 'count', value: data.counts.total as Long,
            descriptionText: "Count total: ${data.counts.total}")
        }
        if (data.freq != null) {
          child.sendEvent(name: 'freq', value: data.freq as BigDecimal, unit: 'Hz',
            descriptionText: "Pulse frequency: ${data.freq} Hz")
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }

    // Voltmeter data (optional analog_in peripheral) → route to voltmeter child
    if (key.startsWith('voltmeter:')) {
      Integer componentId = key.split(':')[1] as Integer
      String childDni = "${device.deviceNetworkId}-voltmeter-${componentId}"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      if (child) {
        if (data.voltage != null) {
          child.sendEvent(name: 'voltage', value: data.voltage as BigDecimal, unit: 'V')
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }

    // Temperature peripheral data (DS18B20) → route to temperature child
    if (key.startsWith('temperature:')) {
      Integer componentId = key.split(':')[1] as Integer
      String childDni = "${device.deviceNetworkId}-temperature-${componentId}"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      if (child) {
        String scale = location?.temperatureScale ?: 'F'
        BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
                          data.tF != null ? data.tF as BigDecimal : null
        if (temp != null) {
          child.sendEvent(name: 'temperature', value: temp, unit: "°${scale}")
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }

    // Humidity peripheral data (DHT22) → route to humidity child
    if (key.startsWith('humidity:')) {
      Integer componentId = key.split(':')[1] as Integer
      String childDni = "${device.deviceNetworkId}-humidity-${componentId}"
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
      if (child) {
        if (data.value != null) {
          child.sendEvent(name: 'humidity', value: data.value as BigDecimal, unit: '%')
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }
  }

  // Update parent aggregates
  state.switchStates = switchStates
  state.powerStates  = powerStates
  updateParentSwitchState()
  updateParentPmFromPowerStates()
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Recalculates parent PM aggregate attributes from stored per-switch power data.
 */
private void updateParentPmFromPowerStates() {
  Map powerStates = state.powerStates ?: [:]
  BigDecimal totalPower   = 0
  BigDecimal totalEnergy  = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage   = 0

  powerStates.each { key, value ->
    if (value instanceof Map) {
      if (value.apower  != null) { totalPower   += value.apower  as BigDecimal }
      if (value.aenergy != null) { totalEnergy  += (value.aenergy as BigDecimal) / 1000 }
      if (value.current != null) { totalCurrent += value.current as BigDecimal }
      if (value.voltage != null) {
        BigDecimal v = value.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
    }
  }

  sendEvent(name: 'power',    value: totalPower,   unit: 'W')
  sendEvent(name: 'energy',   value: totalEnergy,  unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage',  value: maxVoltage,   unit: 'V')
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
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
void logWarn(message)  { log.warn  "${loggingLabel()}: ${message}" }
void logInfo(message)  { if (shouldLogLevel('info'))  { log.info  "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    logTrace(JsonOutput.prettyPrint(JsonOutput.toJson(message)))
  }
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
