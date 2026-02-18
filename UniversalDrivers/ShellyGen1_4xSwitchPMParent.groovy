/**
 * Shelly Gen1 4x Switch PM Parent
 *
 * Parent driver for the Shelly 4Pro (SHSW-44), a Gen 1 four-channel relay device
 * with per-channel power metering, 4 physical switch inputs, and device-level
 * voltage reporting.
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 4 switch PM children as driver-level children in initialize()
 *   - Parent receives Gen 1 action URL callbacks, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and power values (sum)
 *   - Device-level voltage reported on parent (from top-level /status field)
 *   - Commands: child -> parent componentOn() -> app Gen 1 REST endpoint
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 4x Switch PM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'PowerMeter'
    capability 'EnergyMeter'
    capability 'CurrentMeter'
    capability 'VoltageMeasurement'
    capability 'PushableButton'
    capability 'HoldableButton'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    attribute 'lastUpdated', 'string'
    attribute 'overpower', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'switchAggregation', type: 'enum', title: 'Parent Switch State',
    options: ['anyOn':'Any Switch On -> Parent On', 'allOn':'All Switches On -> Parent On'],
    defaultValue: 'anyOn', required: true
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
 * Initializes the parent device: creates/reconciles children, sets button count,
 * and updates aggregate state.
 */
void initialize() {
  logDebug('Parent device initialized')
  sendEvent(name: 'numberOfButtons', value: 4, descriptionText: '4 physical inputs')
  reconcileChildDevices()
  updateParentSwitchState()
}

void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

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
 * Expected: 4 switch PM children for the Shelly 4Pro.
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

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (!['switch'].contains(baseType)) { return }
    desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}".toString())
  }

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  // Create missing children
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer

    if (!['switch'].contains(baseType)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName = pmSet.contains(comp) ? 'Shelly Autoconf Switch PM' : 'Shelly Autoconf Switch'

    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue("${baseType}Id", compId.toString())
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
 * Parses incoming LAN messages from the Gen 1 Shelly device.
 * Gen 1 action URLs fire GET requests only — no POST from scripts.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    Map params = parseWebhookPath(msg)
    if (params?.dst) {
      logDebug("Action URL callback dst=${params.dst}, cid=${params.cid}")
      routeActionUrlCallback(params)
    } else {
      logDebug("No dst found in action URL callback — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
    }
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /switch_on/0).
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
 * Routes Gen 1 action URL callbacks to children and updates parent aggregates.
 * Handles switch on/off, input push/hold, and overpower events.
 *
 * Overpower: Gen 1 relays auto-shut off when overpower is detected. The callback
 * tells us which relay tripped, so we update the child's switch state accordingly.
 */
private void routeActionUrlCallback(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer

  // Build events based on dst
  List<Map> events = []
  switch (dst) {
    case 'switch_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Switch turned on'])
      break
    case 'switch_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Switch turned off'])
      break
    case 'over_power':
      // Relay auto-shutoff due to overpower — treat as switch off for the affected child
      events.add([name: 'switch', value: 'off', descriptionText: "Switch turned off (overpower on relay ${componentId})"])
      logWarn("Overpower detected on relay ${componentId} — relay auto-shutoff triggered")
      sendEvent(name: 'overpower', value: "relay ${componentId}", descriptionText: "Overpower on relay ${componentId}")
      break
    case 'input_short':
      Integer buttonNum = componentId + 1
      events.add([name: 'pushed', value: buttonNum, isStateChange: true, descriptionText: "Button ${buttonNum} was pushed"])
      break
    case 'input_long':
      Integer buttonNum = componentId + 1
      events.add([name: 'held', value: buttonNum, isStateChange: true, descriptionText: "Button ${buttonNum} was held"])
      break
    default:
      logDebug("routeActionUrlCallback: unhandled dst=${dst}")
      return
  }

  // Route switch/overpower events to children
  if (dst.startsWith('switch_') || dst == 'over_power') {
    String childDni = "${device.deviceNetworkId}-switch-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Routed ${events.size()} events to ${child.displayName}")
    }

    // Update parent aggregate switch state
    Map switchStates = state.switchStates ?: [:]
    switchStates["switch:${componentId}".toString()] = (dst == 'switch_on')
    state.switchStates = switchStates
    updateParentSwitchState()

    // Trigger refresh to get power data
    parent?.componentRefresh(device)
  } else if (dst.startsWith('input_')) {
    // Input events fire on parent (button events)
    events.each { Map evt -> sendEvent(evt) }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

void componentOn(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

void componentOff(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.componentRefresh(device)
}

/**
 * Relays switch settings from a child component to the app.
 *
 * @param childDevice The child device sending its settings
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateSwitchSettings(def childDevice, Map switchSettings) {
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

void on() {
  logDebug('Parent on() — turning on all switches')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
  }
}

void off() {
  logDebug('Parent off() — turning off all switches')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { child ->
    Integer switchId = child.getDataValue('switchId') as Integer
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
  }
}

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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Switch Commands and Aggregation                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled Gen 1 status to children and parent.
 * Called by app after polling GET /status on the Gen 1 device.
 *
 * Aggregates power, energy, and current across all 4 children onto the parent.
 * Reports device-level voltage from the top-level deviceVoltage key (Shelly 4Pro
 * reports supply voltage at the device level, not per-component).
 *
 * @param deviceStatus Map of normalized component statuses
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  Map switchStates = [:]
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('switch:') || !(v instanceof Map)) { return }

    Integer componentId = key.split(':')[1] as Integer
    Map data = v as Map

    // Build events for child
    List<Map> events = []
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      events.add([name: 'switch', value: switchState])
      switchStates[key] = data.output
    }
    if (data.apower != null) {
      events.add([name: 'power', value: data.apower as BigDecimal, unit: 'W'])
      totalPower += data.apower as BigDecimal
    }
    if (data.voltage != null) {
      BigDecimal compVoltage = data.voltage as BigDecimal
      events.add([name: 'voltage', value: compVoltage, unit: 'V'])
      if (compVoltage > maxVoltage) { maxVoltage = compVoltage }
    }
    if (data.current != null) {
      events.add([name: 'amperage', value: data.current as BigDecimal, unit: 'A'])
      totalCurrent += data.current as BigDecimal
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      totalEnergy += energyKwh
    }

    // Route to child
    String childDni = "${device.deviceNetworkId}-switch-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    }
  }

  // Update parent aggregates
  state.switchStates = switchStates
  updateParentSwitchState()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')

  // Device-level voltage (Shelly 4Pro reports supply voltage at the device level)
  // Falls back to max per-component voltage if device-level voltage is absent
  if (deviceStatus.containsKey('deviceVoltage')) {
    sendEvent(name: 'voltage', value: deviceStatus.deviceVoltage as BigDecimal, unit: 'V')
  } else if (maxVoltage > 0) {
    sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
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
