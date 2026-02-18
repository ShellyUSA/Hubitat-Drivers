/**
 * Shelly Gen1 RGBW2 White Parent (SHRGBW2 in white firmware mode)
 *
 * Parent driver for the Shelly RGBW2 when configured in white mode.
 * White mode exposes 4 independent dimmer channels ({@code /white/0} through
 * {@code /white/3}), each with on/off, brightness (0-100), and per-channel
 * power metering.
 *
 * Architecture:
 *   - App creates parent device with component data values (white:0..white:3)
 *   - Parent creates 4 white channel children as driver-level children in initialize()
 *   - Parent receives Gen 1 action URL callbacks, routes to children
 *   - Parent aggregates switch state (anyOn/allOn), level (max/avg), and power (sum)
 *   - Commands: child -> parent componentWhiteOn() -> app Gen 1 REST /white/{id}
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 RGBW2 White Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'SwitchLevel'
    capability 'PowerMeter'
    capability 'EnergyMeter'
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
    options: ['anyOn':'Any Channel On -> Parent On', 'allOn':'All Channels On -> Parent On'],
    defaultValue: 'anyOn', required: true
  input name: 'levelAggregation', type: 'enum', title: 'Parent Level Aggregation',
    options: ['max':'Maximum of Channels', 'avg':'Average of Channels'],
    defaultValue: 'max', required: true
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
 * Initializes the parent device: creates/reconciles children and
 * updates aggregate state.
 */
void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
  updateParentSwitchState()
  updateParentLevel()
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
 * Expected: 4 white channel children for the RGBW2 in white mode.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType != 'white') { return }
    desiredDnis.add("${device.deviceNetworkId}-white-${compId}".toString())
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

    if (baseType != 'white') { return }

    String childDni = "${device.deviceNetworkId}-white-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName = 'Shelly Gen1 White Channel'
    String label = "${device.displayName} White ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', 'white')
      child.updateDataValue('whiteId', compId.toString())
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
 * Parses incoming LAN messages from the Gen 1 Shelly RGBW2.
 * Gen 1 action URLs fire GET requests only — no POST from scripts.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }
    checkAndUpdateSourceIp(msg)

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
 * GET Action Webhooks encode state in the path (e.g., /white_on/0).
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

/**
 * Routes Gen 1 action URL callbacks to children and updates parent aggregates.
 * Handles white_on/white_off events for each of the 4 white channels.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer

  // Build events based on dst
  List<Map> events = []
  switch (dst) {
    case 'white_on':
      events.add([name: 'switch', value: 'on', descriptionText: "White channel ${componentId} turned on"])
      break
    case 'white_off':
      events.add([name: 'switch', value: 'off', descriptionText: "White channel ${componentId} turned off"])
      break
    default:
      logDebug("routeActionUrlCallback: unhandled dst=${dst}")
      return
  }

  // Route switch events to children
  if (dst.startsWith('white_')) {
    String childDni = "${device.deviceNetworkId}-white-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Routed ${events.size()} events to ${child.displayName}")
    }

    // Update parent aggregate switch state
    Map channelStates = state.channelStates ?: [:]
    channelStates["white:${componentId}".toString()] = (dst == 'white_on')
    state.channelStates = channelStates
    updateParentSwitchState()

    // Trigger refresh to get brightness and power data
    parent?.componentRefresh(device)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on a white channel via parent app REST command.
 *
 * @param childDevice The child white channel device
 */
void componentWhiteOn(def childDevice) {
  Integer whiteId = childDevice.getDataValue('whiteId') as Integer
  logDebug("componentWhiteOn() from white ${whiteId}")
  parent?.parentSendCommand(device, 'White.Set', [id: whiteId, on: true])
}

/**
 * Turns off a white channel via parent app REST command.
 *
 * @param childDevice The child white channel device
 */
void componentWhiteOff(def childDevice) {
  Integer whiteId = childDevice.getDataValue('whiteId') as Integer
  logDebug("componentWhiteOff() from white ${whiteId}")
  parent?.parentSendCommand(device, 'White.Set', [id: whiteId, on: false])
}

/**
 * Sets brightness on a white channel via parent app REST command.
 *
 * @param childDevice The child white channel device
 * @param level Brightness level (0-100)
 * @param transitionMs Transition time in milliseconds (optional)
 */
void componentSetWhiteLevel(def childDevice, Integer level, Integer transitionMs = null) {
  Integer whiteId = childDevice.getDataValue('whiteId') as Integer
  logDebug("componentSetWhiteLevel() from white ${whiteId}: level=${level}, transition=${transitionMs}ms")
  parent?.parentSendCommand(device, 'White.SetLevel', [id: whiteId, brightness: level, transitionMs: transitionMs])
}

/**
 * Refreshes the entire device status (all channels).
 *
 * @param childDevice The child device requesting refresh
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.componentRefresh(device)
}

/**
 * Relays white channel settings from a child to the app.
 *
 * @param childDevice The child device sending its settings
 * @param whiteSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateWhiteSettings(def childDevice, Map whiteSettings) {
  Integer whiteId = childDevice.getDataValue('whiteId')?.toInteger() ?: 0
  logDebug("componentUpdateWhiteSettings() from white ${whiteId}: ${whiteSettings}")
  parent?.parentUpdateWhiteSettings(device, whiteId, whiteSettings)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Switch/Level Commands and Aggregation                 ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on all white channels.
 */
void on() {
  logDebug('Parent on() — turning on all white channels')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-white-') }.each { child ->
    Integer whiteId = child.getDataValue('whiteId') as Integer
    parent?.parentSendCommand(device, 'White.Set', [id: whiteId, on: true])
  }
}

/**
 * Turns off all white channels.
 */
void off() {
  logDebug('Parent off() — turning off all white channels')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-white-') }.each { child ->
    Integer whiteId = child.getDataValue('whiteId') as Integer
    parent?.parentSendCommand(device, 'White.Set', [id: whiteId, on: false])
  }
}

/**
 * Sets brightness on all white channels.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("Parent setLevel(${level}, ${duration}) — setting all white channels")
  Integer transitionMs = duration > 0 ? (duration * 1000) as Integer : null
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-white-') }.each { child ->
    Integer whiteId = child.getDataValue('whiteId') as Integer
    parent?.parentSendCommand(device, 'White.SetLevel', [id: whiteId, brightness: level as Integer, transitionMs: transitionMs])
  }
}

/**
 * Updates the parent switch state based on child channel states.
 * Supports anyOn (parent on if any channel on) and allOn (parent on only if all channels on).
 */
private void updateParentSwitchState() {
  Map channelStates = state.channelStates ?: [:]
  if (channelStates.isEmpty()) { return }

  String mode = settings.switchAggregation ?: 'anyOn'
  Boolean parentOn = (mode == 'allOn') ?
    channelStates.values().every { it == true } :
    channelStates.values().any { it == true }

  String newState = parentOn ? 'on' : 'off'
  if (device.currentValue('switch') != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Parent switch is ${newState}")
    logInfo("Parent switch: ${newState} (mode: ${mode})")
  }
}

/**
 * Updates the parent level based on child channel levels.
 * Supports max (highest channel level) and avg (average of channel levels).
 */
private void updateParentLevel() {
  Map channelLevels = state.channelLevels ?: [:]
  if (channelLevels.isEmpty()) { return }

  String mode = settings.levelAggregation ?: 'max'
  List<Integer> levels = channelLevels.values().collect { it as Integer }

  Integer parentLevel
  if (mode == 'avg') {
    parentLevel = levels ? (levels.sum() / levels.size()) as Integer : 0
  } else {
    parentLevel = levels ? levels.max() : 0
  }

  if (device.currentValue('level') as Integer != parentLevel) {
    sendEvent(name: 'level', value: parentLevel, unit: '%', descriptionText: "Parent level is ${parentLevel}%")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Switch/Level Commands and Aggregation             ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled Gen 1 status to children and parent.
 * Called by app after polling GET /status on the Gen 1 device.
 *
 * Aggregates power and energy across all 4 children onto the parent.
 * Updates parent switch and level aggregation.
 *
 * @param deviceStatus Map of normalized component statuses (white:0..white:3)
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  Map channelStates = [:]
  Map channelLevels = [:]
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('white:') || !(v instanceof Map)) { return }

    Integer componentId = key.split(':')[1] as Integer
    Map data = v as Map

    // Build events for child
    List<Map> events = []
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      events.add([name: 'switch', value: switchState])
      channelStates[key] = data.output
    }
    if (data.brightness != null) {
      Integer level = data.brightness as Integer
      events.add([name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%"])
      channelLevels[key] = level
    }
    if (data.apower != null) {
      events.add([name: 'power', value: data.apower as BigDecimal, unit: 'W'])
      totalPower += data.apower as BigDecimal
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      totalEnergy += energyKwh
    }

    // Route to child
    String childDni = "${device.deviceNetworkId}-white-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    }
  }

  // Update parent aggregates
  state.channelStates = channelStates
  state.channelLevels = channelLevels
  updateParentSwitchState()
  updateParentLevel()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
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

@Field static Boolean NOCHILDSWITCH = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                       ║
// ╚══════════════════════════════════════════════════════════════╝
