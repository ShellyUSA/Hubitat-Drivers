/**
 * Shelly Autoconf 2x Switch PM Parent
 *
 * Parent driver for dual-switch Shelly devices with power monitoring.
 * Examples: Shelly 2PM, Shelly Plus 2PM
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 2 switch PM children as driver-level children in initialize()
 *   - Parent receives LAN traffic, parses locally, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and power values (sum)
 *   - Commands: child → parent componentOn() → app parentSendCommand() → Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 2x Switch PM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
}

/**
 * Initializes the parent device: creates/reconciles children, updates aggregate state.
 */
void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
  updateParentSwitchState()
}

void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
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
 * Expected: 2 switch PM children, 0-2 input children.
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
    if (!['switch', 'input'].contains(baseType)) { return }
    desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}")
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
    if (!['switch', 'input'].contains(baseType)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return } // already exists, leave it alone

    String driverName
    if (baseType == 'switch') {
      driverName = pmSet.contains(comp) ? 'Shelly Autoconf Switch PM' : 'Shelly Autoconf Switch'
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
      // Note: addChildDevice triggers installed() → initialize() automatically
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
 */
void parse(String description) {
  logTrace('parse() received message')

  try {
    Map msg = parseLanMessage(description)
    logTrace("parse() msg keys: ${msg?.keySet()}, status=${msg?.status}, body=${msg?.body ? 'present' : 'null'}, headers=${msg?.headers ? 'present' : 'null'}")
    if (msg?.status != null) {
      logTrace("parse() skipping HTTP response (status=${msg.status})")
      return
    }

    // Try POST body (script notifications)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        String dst = json?.dst
        Map result = json?.result as Map

        if (result && dst) {
          logDebug("POST notification dst=${dst}")
          logTrace("POST result keys: ${result.keySet()}, data: ${result}")
          routePostNotification(dst, result)
          processAggregation(dst, json)
          return
        }
        logTrace("POST body parsed but no dst/result: dst=${dst}, result=${result}")
      } catch (Exception jsonEx) {
        logDebug('Body not JSON, trying GET params')
      }
    }

    // Try GET query params (webhooks)
    Map params = parseWebhookQueryParams(msg)
    if (params?.dst) {
      logDebug("GET webhook dst=${params.dst}, comp=${params.comp}")
      logTrace("Webhook params: ${params}")
      routeWebhookNotification(params)
      processWebhookAggregation(params)
    } else {
      logTrace("parse() no dst found in message, unable to route")
    }
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

/**
 * Routes POST notification to children and updates aggregates.
 */
private void routePostNotification(String dst, Map result) {
  result.each { key, value ->
    if (!(value instanceof Map)) { return }
    String keyStr = key.toString()
    if (!keyStr.contains(':')) { return }

    String baseType = keyStr.split(':')[0]
    Integer componentId = keyStr.split(':')[1] as Integer

    List<Map> events = buildComponentEvents(dst, baseType, value as Map)
    logTrace("buildComponentEvents(dst=${dst}, type=${baseType}) → ${events.size()} events: ${events}")
    if (!events) { return }

    // Determine if this should route to a child
    boolean routeToChild = false
    if (baseType == 'switch') {
      routeToChild = true // switches always have children
    } else if (baseType == 'input') {
      // Check input count
      String componentStr = device.getDataValue('components') ?: ''
      Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
      routeToChild = (inputCount > 1)
    }

    if (routeToChild) {
      String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt ->
          logTrace("Sending event to ${child.displayName}: ${evt}")
          child.sendEvent(evt)
        }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        logDebug("Routed ${events.size()} events to ${child.displayName}")
      } else {
        logDebug("No child device found for DNI: ${childDni}")
      }
    } else {
      // Handle on parent (single input case)
      events.each { Map evt -> sendEvent(evt) }
    }
  }
}

/**
 * Routes webhook notification to children.
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  String comp = params.comp
  if (!comp || !comp.contains(':')) {
    logTrace("routeWebhookNotification: no valid comp param (comp=${comp})")
    return
  }

  String baseType = comp.split(':')[0]
  Integer componentId = comp.split(':')[1] as Integer

  List<Map> events = buildWebhookEvents(dst, params)
  logTrace("buildWebhookEvents(dst=${dst}) → ${events.size()} events: ${events}")
  if (!events) { return }

  boolean routeToChild = false
  if (baseType == 'switch') {
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
 * Parses webhook GET query parameters.
 */
private Map parseWebhookQueryParams(Map msg) {
  if (!msg?.headers) { return null }

  String requestLine = null
  msg.headers.each { key, value ->
    String keyStr = key.toString()
    if (keyStr.startsWith('GET ') || keyStr.startsWith('POST ')) {
      requestLine = keyStr
    }
  }
  if (!requestLine) { return null }

  String pathAndQuery = requestLine.split(' ')[1]
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx < 0) { return null }

  String queryString = pathAndQuery.substring(qIdx + 1)
  Map params = [:]
  queryString.split('&').each { String pair ->
    String[] kv = pair.split('=', 2)
    if (kv.length == 2) {
      params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
    }
  }
  return params
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Building                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Builds events from POST notification data.
 */
private List<Map> buildComponentEvents(String dst, String baseType, Map data) {
  List<Map> events = []

  switch (dst) {
    case 'switchmon':
      if (data.output != null) {
        String switchState = data.output ? 'on' : 'off'
        events.add([name: 'switch', value: switchState, descriptionText: "Switch turned ${switchState}"])
      }
      break

    case 'powermon':
      if (data.voltage != null) {
        events.add([name: 'voltage', value: data.voltage as BigDecimal, unit: 'V'])
      }
      if (data.current != null) {
        events.add([name: 'amperage', value: data.current as BigDecimal, unit: 'A'])
      }
      if (data.apower != null) {
        events.add([name: 'power', value: data.apower as BigDecimal, unit: 'W'])
      }
      if (data.aenergy?.total != null) {
        BigDecimal energyWh = data.aenergy.total as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      }
      break

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
    case 'input_toggle':
      if (data.state != null) {
        String toggleState = data.state ? 'on' : 'off'
        events.add([name: 'switch', value: toggleState, isStateChange: true, descriptionText: "Input toggled ${toggleState}"])
      }
      break
  }

  return events
}

/**
 * Builds events from webhook GET parameters.
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        events.add([name: 'switch', value: switchState, descriptionText: "Switch turned ${switchState}"])
      }
      break

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
    case 'input_toggle':
      if (params.state) {
        String toggleState = params.state as String
        events.add([name: 'switch', value: toggleState, isStateChange: true, descriptionText: "Input toggled ${toggleState}"])
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
 */
void componentOn(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

/**
 * Handles off() command from child switch.
 */
void componentOff(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

/**
 * Handles refresh() command from child.
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

/**
 * Handles resetEnergyMonitors() command from child.
 */
void componentResetEnergyMonitors(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentResetEnergyMonitors() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.ResetCounters', [id: switchId, type: ['aenergy']])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Switch Commands and Aggregation                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on all switches.
 */
void on() {
  logDebug('Parent on() — turning on all switches')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
  }
}

/**
 * Turns off all switches.
 */
void off() {
  logDebug('Parent off() — turning off all switches')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
  }
}

/**
 * Processes aggregation from POST notifications.
 */
private void processAggregation(String dst, Map json) {
  if (dst == 'switchmon') { parseSwitchmon(json) }
  else if (dst == 'powermon') { parsePowermon(json) }
  else if (dst == 'input_push') { handleInputEvent(json, 'pushed') }
  else if (dst == 'input_double') { handleInputEvent(json, 'doubleTapped') }
  else if (dst == 'input_long') { handleInputEvent(json, 'held') }
}

/**
 * Parses switchmon notification and updates aggregate switch state.
 */
void parseSwitchmon(Map json) {
  Map result = json?.result
  if (!result) { return }

  Map switchStates = state.switchStates ?: [:]
  result.each { key, value ->
    if (key.toString().startsWith('switch:') && value instanceof Map && value.output != null) {
      switchStates[key.toString()] = value.output
    }
  }
  state.switchStates = switchStates
  updateParentSwitchState()
}

/**
 * Updates aggregate switch state based on preference.
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
 * Parses powermon notification and updates aggregate power values.
 */
void parsePowermon(Map json) {
  Map result = json?.result
  if (!result) { return }

  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0
  Integer count = 0

  result.each { key, value ->
    if (key.toString().startsWith('switch:') && value instanceof Map) {
      if (value.apower != null) {
        totalPower += value.apower as BigDecimal
        count++
      }
      if (value.aenergy?.total != null) {
        totalEnergy += (value.aenergy.total as BigDecimal) / 1000
      }
      if (value.current != null) {
        totalCurrent += value.current as BigDecimal
      }
      if (value.voltage != null) {
        BigDecimal v = value.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
    }
  }

  if (count > 0) {
    sendEvent(name: 'power', value: totalPower, unit: 'W', descriptionText: "Total power: ${totalPower}W")
    sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh', descriptionText: "Total energy: ${totalEnergy}kWh")
    sendEvent(name: 'amperage', value: totalCurrent, unit: 'A', descriptionText: "Total current: ${totalCurrent}A")
    sendEvent(name: 'voltage', value: maxVoltage, unit: 'V', descriptionText: "Voltage: ${maxVoltage}V")
    logDebug("Aggregate power: ${totalPower}W, energy: ${totalEnergy}kWh, current: ${totalCurrent}A, voltage: ${maxVoltage}V")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Switch Commands and Aggregation                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Input Button Event Aggregation                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Processes input event aggregation (for single input handled on parent).
 */
void handleInputEvent(Map json, String eventName) {
  String componentStr = device.getDataValue('components') ?: ''
  Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()

  // If multiple inputs, they have children - no parent aggregation needed
  if (inputCount > 1) { return }

  // Single input: fire event on parent
  Map result = json?.result
  if (!result) { return }

  result.each { key, value ->
    if (key.toString().startsWith('input:') && value instanceof Map) {
      sendEvent(name: 'numberOfButtons', value: 1)
      sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button was ${eventName}")
      logInfo("Parent button ${eventName}")
    }
  }
}

/**
 * Processes webhook input aggregation.
 */
private void processWebhookAggregation(Map params) {
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
// ║  END Input Button Event Aggregation                           ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes Shelly.GetStatus response to children and parent.
 * Called by app's parentRefresh() after querying device.
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
    if (baseType == 'switch') {
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
 */
private List<Map> buildStatusEvents(String componentType, Map statusData) {
  List<Map> events = []

  if (componentType == 'switch') {
    if (statusData.output != null) {
      String switchState = statusData.output ? 'on' : 'off'
      events.add([name: 'switch', value: switchState])
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
 */
private void updateAggregatesFromStatus(Map deviceStatus) {
  Map switchStates = [:]
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('switch:') && v instanceof Map) {
      if (v.output != null) {
        switchStates[key] = v.output
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

  state.switchStates = switchStates
  updateParentSwitchState()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
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
