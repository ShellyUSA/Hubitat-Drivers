/**
 * Shelly Gen1 Uni Parent
 *
 * Parent driver for the Shelly Uni (SHUNI-1) — a mains-powered Gen 1 modular
 * IoT device with 1 relay, 2 digital inputs, 1 ADC channel (0-30V), and
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
  definition(name: 'Shelly Gen1 Uni Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'TemperatureMeasurement'
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
    options: ['anyOn':'Any Switch On -> Parent On', 'allOn':'All Switches On -> Parent On'],
    defaultValue: 'anyOn', required: true
  input name: 'input0Mode', type: 'enum', title: 'Input 0 Mode',
    options: ['button':'Button (push/hold events)', 'switch':'Switch (on/off state)'],
    defaultValue: 'button', required: true
  input name: 'input1Mode', type: 'enum', title: 'Input 1 Mode',
    options: ['button':'Button (push/hold events)', 'switch':'Switch (on/off state)'],
    defaultValue: 'button', required: true
  input name: 'relayBtnType', type: 'enum', title: 'Relay Physical Button Type',
    options: ['toggle':'Toggle','momentary':'Momentary','momentary_on_release':'Momentary on Release','detached':'Detached','action':'Action'],
    defaultValue: 'toggle', required: false
  input name: 'relayBtnReverse', type: 'bool', title: 'Reverse Relay Button Logic',
    defaultValue: false, required: false
  input name: 'relayMaxPower', type: 'decimal', title: 'Relay Max Power (W, 0 = disabled)',
    defaultValue: 0, range: '0..3500', required: false
  input name: 'adcLowerLimit', type: 'decimal', title: 'ADC Lower Threshold (V)',
    range: '0.0..30.0', required: false
  input name: 'adcUpperLimit', type: 'decimal', title: 'ADC Upper Threshold (V)',
    range: '0.0..30.0', required: false
  input name: 'longpushTime', type: 'number', title: 'Long-Push Duration Threshold (ms)',
    defaultValue: 800, range: '200..5000', required: false
  input name: 'ledStatusDisable', type: 'bool', title: 'Disable Network Status LED',
    defaultValue: false, required: false
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
  syncInputModes()
  syncRelaySettings()
  syncAdcSettings()
  syncDeviceLevelSettings()
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
  syncInputModes()
  syncRelaySettings()
  syncAdcSettings()
  syncDeviceLevelSettings()
  reconcileChildDevices()
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
 * Maps a component type, ID, and current input mode settings to its child driver name.
 *
 * @param baseType The component type (switch, input, adc, temperature, humidity)
 * @param compId The component ID number
 * @param mode0 The current mode for input:0 ('button' or 'switch')
 * @param mode1 The current mode for input:1 ('button' or 'switch')
 * @return The Hubitat driver name for this component type
 */
@CompileStatic
private static String getChildDriverName(String baseType, Integer compId, String mode0, String mode1) {
  switch (baseType) {
    case 'switch': return 'Shelly Autoconf Switch'
    case 'input':
      String mode = (compId == 0) ? mode0 : mode1
      return (mode == 'switch') ? 'Shelly Autoconf Input Switch' : 'Shelly Autoconf Input Button'
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
 * <p>
 * Input children are created with either 'Shelly Autoconf Input Button' or
 * 'Shelly Autoconf Input Switch' depending on the input0Mode/input1Mode preferences.
 * When the mode changes, the existing child is deleted and recreated with the correct driver.
 * The installed driver name is stored as a data value on each input child to detect mismatches.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  String mode0 = (settings?.input0Mode ?: 'button').toString()
  String mode1 = (settings?.input1Mode ?: 'button').toString()

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Build desired DNI → driver name map
  Map<String, String> desiredDriverMap = [:]
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (!shouldCreateChild(baseType, compId)) { return }
    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}".toString()
    String driverName = getChildDriverName(baseType, compId, mode0, mode1)
    if (driverName) { desiredDriverMap[childDni] = driverName }
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

  // Create missing children (use live getChildDevice() check to avoid stale-map issues after deletion)
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer

    if (!shouldCreateChild(baseType, compId)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}".toString()
    if (!desiredDriverMap.containsKey(childDni)) { return }
    if (getChildDevice(childDni)) { return }

    String driverName = desiredDriverMap[childDni]
    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      com.hubitat.app.DeviceWrapper child = addChildDevice('ShellyDeviceManager', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue(getComponentIdKey(baseType), compId.toString())
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
      com.hubitat.app.DeviceWrapper switchChild = getChildDevice(switchDni)
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
      com.hubitat.app.DeviceWrapper inputChild = getChildDevice(inputDni)
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

    // Input double-tap events
    case 'input_double':
      String doubleDni = "${device.deviceNetworkId}-input-${componentId}"
      com.hubitat.app.DeviceWrapper doubleChild = getChildDevice(doubleDni)
      if (doubleChild) {
        doubleChild.sendEvent(name: 'doubleTapped', value: 1, isStateChange: true,
          descriptionText: 'Button was double tapped')
        doubleChild.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
        logDebug("Routed double-tap event to ${doubleChild.displayName}")
      }
      sendEvent(name: 'numberOfButtons', value: 2)
      sendEvent(name: 'doubleTapped', value: componentId + 1, isStateChange: true,
        descriptionText: "Button ${componentId + 1} was double tapped")
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
void componentOn(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

/**
 * Turns off a relay child via the parent app.
 *
 * @param childDevice The switch child device requesting off
 */
void componentOff(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

/**
 * Refreshes the device status via the parent app.
 *
 * @param childDevice The child device requesting a refresh
 */
void componentRefresh(com.hubitat.app.DeviceWrapper childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.componentRefresh(device)
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
 * Turns on all relay children and optimistically updates parent switch state.
 */
void on() {
  logDebug('Parent on() — turning on all switches')
  Map newStates = state.switchStates ?: [:]
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { com.hubitat.app.DeviceWrapper child ->
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
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-switch-') }.each { com.hubitat.app.DeviceWrapper child ->
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
      com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
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
        com.hubitat.app.DeviceWrapper adcChild = getChildDevice(adcDni)
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
      BigDecimal temp = null
      if (scale == 'C' && data.tC != null) {
        temp = data.tC as BigDecimal
      } else if (data.tF != null) {
        temp = data.tF as BigDecimal
      } else if (data.tC != null) {
        temp = (data.tC as BigDecimal) * 9 / 5 + 32
      }
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
      }
    }

    // External temperature sensors → peripheral children
    if (key.startsWith('temperature:') && key != 'temperature:0') {
      Integer sensorId = key.split(':')[1] as Integer
      String tempDni = "${device.deviceNetworkId}-temperature-${sensorId}"
      com.hubitat.app.DeviceWrapper tempChild = getChildDevice(tempDni)
      if (tempChild) {
        String scale = location?.temperatureScale ?: 'F'
        BigDecimal temp = null
        if (scale == 'C' && data.tC != null) {
          temp = data.tC as BigDecimal
        } else if (data.tF != null) {
          temp = data.tF as BigDecimal
        } else if (data.tC != null) {
          temp = (data.tC as BigDecimal) * 9 / 5 + 32
        }
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
      com.hubitat.app.DeviceWrapper humChild = getChildDevice(humDni)
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
// ║  Settings Sync                                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Pushes input_type to the device for input:0 and input:1.
 * Maps Hubitat preference values ('button'/'switch') to Gen 1 API input types.
 * Called during updated() and configure() when input mode preferences change.
 */
private void syncInputModes() {
  String mode0 = (settings?.input0Mode ?: 'button').toString()
  String mode1 = (settings?.input1Mode ?: 'button').toString()
  parent?.parentSetGen1InputTypes(device, [0: mode0, 1: mode1])
}

/**
 * Pushes relay btn_type, btn_reverse, and max_power settings to the device.
 * Called during updated() and configure() when relay preference settings change.
 */
private void syncRelaySettings() {
  Map relaySettings = [:]
  if (settings?.relayBtnType != null) { relaySettings.btn_type = settings.relayBtnType.toString() }
  if (settings?.relayBtnReverse != null) { relaySettings.btn_reverse = settings.relayBtnReverse ? 1 : 0 }
  if (settings?.relayMaxPower != null) { relaySettings.max_power = settings.relayMaxPower }
  if (relaySettings) { parent?.parentUpdateSwitchSettings(device, 0, relaySettings) }
}

/**
 * Pushes ADC lower_limit and upper_limit threshold settings to the device.
 * Called during updated() and configure() when ADC threshold preferences change.
 */
private void syncAdcSettings() {
  Map adcSettings = [:]
  if (settings?.adcLowerLimit != null) { adcSettings.lower_limit = settings.adcLowerLimit }
  if (settings?.adcUpperLimit != null) { adcSettings.upper_limit = settings.adcUpperLimit }
  if (adcSettings) { parent?.parentApplyGen1AdcSettings(device, adcSettings) }
}

/**
 * Pushes device-level settings (longpush_time, led_status_disable) to the device.
 * Called during updated() and configure() when device-level preferences change.
 */
private void syncDeviceLevelSettings() {
  Map deviceSettings = [:]
  if (settings?.longpushTime != null) { deviceSettings.longpush_time = settings.longpushTime.toString() }
  if (settings?.ledStatusDisable != null) { deviceSettings.led_status_disable = settings.ledStatusDisable.toString() }
  if (deviceSettings) { parent?.componentUpdateGen1Settings(device, deviceSettings) }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Settings Sync                                            ║
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
