/**
 * Shelly Gen1 Uni Parent
 *
 * Parent driver for the Shelly Uni (SHUNI-1) — a mains-powered Gen 1 modular
 * IoT device with 2 relays, 2 digital inputs, 1 ADC channel (0-30V), and
 * optional DS18B20 temperature / DHT22 humidity sensor add-ons.
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates switch, input, ADC, and optional sensor children in initialize()
 *   - Internal device temperature (temperature:0) stays on the parent
 *   - Parent receives Gen 1 action URL callbacks, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and mirrors ADC voltage
 *   - Commands: child -> parent componentOn() -> app Gen 1 REST endpoint
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Uni Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'TemperatureMeasurement'
    capability 'VoltageMeasurement'
    capability 'PushableButton'
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
 * Maps a component type and ID to its child driver name.
 *
 * @param baseType The component type (switch, input, adc, temperature, humidity)
 * @param compId The component ID number
 * @return The Hubitat driver name for this component type
 */
@CompileStatic
private static String getChildDriverName(String baseType, Integer compId) {
  switch (baseType) {
    case 'switch': return 'Shelly Autoconf Switch'
    case 'input': return 'Shelly Autoconf Input Button'
    case 'adc': return 'Shelly Autoconf Polling Voltage Sensor'
    case 'temperature': return 'Shelly Autoconf Temperature Peripheral'
    case 'humidity': return 'Shelly Autoconf Humidity Peripheral'
    default: return null
  }
}

/**
 * Returns the data value key name for a given component type.
 * Each child stores its component ID under a type-specific data value key.
 *
 * @param baseType The component type
 * @return The data value key name (e.g., 'switchId', 'inputId')
 */
@CompileStatic
private static String getComponentIdKey(String baseType) {
  return "${baseType}Id".toString()
}

/**
 * Determines whether a component should be created as a child device.
 * Internal device temperature (temperature:0) stays on the parent.
 *
 * @param baseType The component type
 * @param compId The component ID
 * @return true if this component should have a child device
 */
@CompileStatic
private static Boolean shouldCreateChild(String baseType, Integer compId) {
  Set<String> childTypes = ['switch', 'input', 'adc', 'temperature', 'humidity'] as Set
  if (!childTypes.contains(baseType)) { return false }
  // temperature:0 is the internal device temp — stays on parent
  if (baseType == 'temperature' && compId == 0) { return false }
  return true
}

/**
 * Reconciles driver-level child devices against the components data value.
 * Creates switch, input, ADC, and optional temperature/humidity peripheral children.
 * Skips temperature:0 (internal device temp — displayed on parent).
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
    if (!shouldCreateChild(baseType, compId)) { return }
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

    if (!shouldCreateChild(baseType, compId)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName = getChildDriverName(baseType, compId)
    if (!driverName) { return }

    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue(getComponentIdKey(baseType), compId.toString())
      // Input buttons need numberOfButtons set
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
 * Parses incoming LAN messages from the Gen 1 Shelly Uni.
 * Routes action URL callbacks to children and parent aggregates.
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
 * Handles switch on/off, input short/long push, and sensor threshold alerts.
 *
 * @param params Map with dst, cid, and optional key/value pairs from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer

  switch (dst) {
    // Relay switch events
    case 'switch_on':
    case 'switch_off':
      Boolean isOn = (dst == 'switch_on')
      List<Map> switchEvents = [[name: 'switch', value: isOn ? 'on' : 'off',
        descriptionText: "Switch turned ${isOn ? 'on' : 'off'}"]]

      String switchDni = "${device.deviceNetworkId}-switch-${componentId}"
      def switchChild = getChildDevice(switchDni)
      if (switchChild) {
        switchEvents.each { Map evt -> switchChild.sendEvent(evt) }
        switchChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        logDebug("Routed switch event to ${switchChild.displayName}")
      }

      // Update parent aggregate switch state
      Map switchStates = state.switchStates ?: [:]
      switchStates["switch:${componentId}".toString()] = isOn
      state.switchStates = switchStates
      updateParentSwitchState()
      break

    // Input button events
    case 'input_short':
    case 'input_long':
      String eventName = (dst == 'input_short') ? 'pushed' : 'held'
      Integer parentButton = componentId + 1
      Map inputEvent = [name: eventName, value: 1, isStateChange: true,
        descriptionText: "Button was ${eventName == 'pushed' ? 'pushed' : 'held'}"]

      // Route to child input device
      String inputDni = "${device.deviceNetworkId}-input-${componentId}"
      def inputChild = getChildDevice(inputDni)
      if (inputChild) {
        inputChild.sendEvent(inputEvent)
        inputChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        logDebug("Routed input event to ${inputChild.displayName}")
      }

      // Mirror on parent
      sendEvent(name: 'numberOfButtons', value: 2)
      sendEvent(name: eventName, value: parentButton, isStateChange: true,
        descriptionText: "Button ${parentButton} was ${eventName == 'pushed' ? 'pushed' : 'held'}")
      break

    // Over-power condition on relay
    case 'over_power':
      logWarn("Over-power condition on switch ${componentId}")
      refresh()
      break

    // Sensor threshold alerts — trigger a refresh to get current values
    case 'adc_over':
    case 'adc_under':
    case 'ext_temp_over':
    case 'ext_temp_under':
    case 'ext_hum_over':
    case 'ext_hum_under':
      logInfo("Sensor threshold alert: ${dst} (cid=${componentId})")
      refresh()
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on a relay child via the parent app.
 *
 * @param childDevice The switch child device requesting on
 */
void componentOn(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

/**
 * Turns off a relay child via the parent app.
 *
 * @param childDevice The switch child device requesting off
 */
void componentOff(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

/**
 * Refreshes the device status via the parent app.
 *
 * @param childDevice The child device requesting a refresh
 */
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

/**
 * Turns on all relay children and optimistically updates parent switch state.
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
 * Turns off all relay children and optimistically updates parent switch state.
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Switch Commands and Aggregation                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled Gen 1 status to children and parent.
 * Called by app after polling GET /status on the Gen 1 device.
 * <p>
 * Routes:
 * <ul>
 *   <li>{@code switch:N} → switch child (on/off) + parent aggregate</li>
 *   <li>{@code input:N} → ignored (inputs are event-driven via action URLs)</li>
 *   <li>{@code adc:N} → ADC child (voltage) + parent voltage attribute</li>
 *   <li>{@code temperature:0} → parent temperature attribute (internal device temp)</li>
 *   <li>{@code temperature:100+} → temperature peripheral child</li>
 *   <li>{@code humidity:100+} → humidity peripheral child</li>
 * </ul>
 *
 * @param deviceStatus Map of normalized component statuses
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  Map switchStates = state.switchStates ?: [:]

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    // Switch (relay) data → route to child and track for aggregation
    if (key.startsWith('switch:')) {
      Integer componentId = key.split(':')[1] as Integer

      List<Map> events = []
      if (data.output != null) {
        String switchState = data.output ? 'on' : 'off'
        events.add([name: 'switch', value: switchState])
        switchStates[key] = data.output
      }

      String childDni = "${device.deviceNetworkId}-switch-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt -> child.sendEvent(evt) }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }

    // ADC data → route to child and mirror on parent
    if (key.startsWith('adc:')) {
      Integer componentId = key.split(':')[1] as Integer

      if (data.voltage != null) {
        BigDecimal voltage = data.voltage as BigDecimal

        // Update ADC child
        String adcDni = "${device.deviceNetworkId}-adc-${componentId}"
        def adcChild = getChildDevice(adcDni)
        if (adcChild) {
          adcChild.sendEvent(name: 'voltage', value: voltage, unit: 'V')
          adcChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        }

        // Mirror on parent
        sendEvent(name: 'voltage', value: voltage, unit: 'V')
      }
    }

    // Internal device temperature → parent attribute
    if (key == 'temperature:0') {
      String scale = location?.temperatureScale ?: 'F'
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal : data.tF as BigDecimal
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
      }
    }

    // External temperature sensors → peripheral children
    if (key.startsWith('temperature:') && key != 'temperature:0') {
      Integer sensorId = key.split(':')[1] as Integer
      String tempDni = "${device.deviceNetworkId}-temperature-${sensorId}"
      def tempChild = getChildDevice(tempDni)
      if (tempChild) {
        String scale = location?.temperatureScale ?: 'F'
        BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal : data.tF as BigDecimal
        if (temp != null) {
          tempChild.sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
          tempChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        }
      }
    }

    // External humidity sensors → peripheral children
    if (key.startsWith('humidity:')) {
      Integer sensorId = key.split(':')[1] as Integer
      String humDni = "${device.deviceNetworkId}-humidity-${sensorId}"
      def humChild = getChildDevice(humDni)
      if (humChild) {
        if (data.value != null) {
          humChild.sendEvent(name: 'humidity', value: data.value as BigDecimal, unit: '%')
          humChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        }
      }
    }
  }

  // Update parent aggregates
  state.switchStates = switchStates
  updateParentSwitchState()

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
