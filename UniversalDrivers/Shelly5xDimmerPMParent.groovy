/**
 * Shelly Autoconf 5x Dimmer PM Parent
 *
 * Parent driver for 5-channel dimmer Shelly devices with power monitoring.
 * Examples: Shelly Pro RGBWW PM (5-channel light profile)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 5 dimmer children as driver-level children in initialize()
 *   - Parent receives LAN traffic, parses locally, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and power values (sum)
 *   - Commands: child -> parent componentLightOn() -> app parentSendCommand() -> Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 5x Dimmer PM Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ChangeLevel'
    //Commands: startLevelChange(direction), stopLevelChange()

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
    command 'resetEnergyMonitors'
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'switchAggregation', type: 'enum', title: 'Parent Switch State',
    options: ['anyOn':'Any Dimmer On -> Parent On', 'allOn':'All Dimmers On -> Parent On'],
    defaultValue: 'anyOn', required: true
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
  updateParentSwitchState()
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
 * Creates children that should exist but don't, and removes orphaned children.
 * Expected: 5 dimmer children, 0-4 input children.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Count component types
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
    if (!['light', 'input'].contains(baseType)) { return }
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

    if (baseType == 'input' && inputCount <= 1) { return }
    if (!['light', 'input'].contains(baseType)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName
    if (baseType == 'light') {
      driverName = 'Shelly Autoconf Dimmer'
    } else if (baseType == 'input') {
      driverName = 'Shelly Autoconf Input Button'
    } else {
      return
    }

    String label = "${device.displayName} ${baseType.capitalize()} ${compId}"
    try {
      def child = addChildDevice('ShellyDeviceManager', driverName, childDni, [name: label, label: label])
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
    logDebug("GET webhook: no dst found")
  }
}

/**
 * Routes webhook notification to children.
 *
 * @param params The parsed webhook parameters
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer
  String baseType = (dst == 'powermon' && params.comp) ?
      (params.comp as String) : dstToComponentType(dst)

  List<Map> events = buildWebhookEvents(dst, params)
  if (!events) { return }

  boolean routeToChild = false
  if (baseType == 'light') {
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
      logDebug("Routed ${events.size()} webhook events to ${child.displayName}")
    }
  } else {
    events.each { Map evt -> sendEvent(evt) }
  }
}

/**
 * Maps a webhook dst parameter to its Shelly component type.
 *
 * @param dst The webhook destination
 * @return The Shelly component type
 */
private String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('light_')) { return 'light' }
  if (dst.startsWith('switch_')) { return 'switch' }
  switch (dst) {
    case 'lightmon': return 'light'
    default: return dst
  }
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Building                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Builds events from webhook parameters for light, input, and power monitoring.
 *
 * @param dst The webhook destination type
 * @param params The parsed webhook parameters
 * @return List of event maps
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'light_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Light turned on'])
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
      }
      break
    case 'light_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Light turned off'])
      break
    case 'light_change':
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
      }
      break

    case 'lightmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        events.add([name: 'switch', value: switchState])
      }
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
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
    case 'input_toggle':
      if (params.state) {
        events.add([name: 'switch', value: params.state as String, isStateChange: true])
      }
      break

    // Power monitoring
    case 'powermon':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V'])
      }
      if (params.current != null) {
        events.add([name: 'amperage', value: params.current as BigDecimal, unit: 'A'])
      }
      if (params.apower != null) {
        events.add([name: 'power', value: params.apower as BigDecimal, unit: 'W'])
      }
      if (params.aenergy != null) {
        BigDecimal energyKwh = (params.aenergy as BigDecimal) / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      }
      if (params.freq != null) {
        events.add([name: 'frequency', value: params.freq as BigDecimal, unit: 'Hz'])
      }
      break

    // Temperature (internal device temp)
    case 'temperature':
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
      BigDecimal temp = null
      if (scale == 'C' && params.tC != null) {
        temp = params.tC as BigDecimal
      } else if (params.tF != null) {
        temp = params.tF as BigDecimal
      } else if (params.tC != null) {
        temp = (params.tC as BigDecimal) * 9 / 5 + 32
      }
      if (temp != null) {
        events.add([name: 'temperature', value: temp, unit: "\u00B0${scale}"])
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

void componentLightOn(def childDevice) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentLightOn() from light ${lightId}")
  parent?.parentSendCommand(device, 'Light.Set', [id: lightId, on: true])
}

void componentLightOff(def childDevice) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentLightOff() from light ${lightId}")
  parent?.parentSendCommand(device, 'Light.Set', [id: lightId, on: false])
}

void componentSetLevel(def childDevice, Integer level, Integer transitionMs = null) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentSetLevel(${level}, ${transitionMs}) from light ${lightId}")
  Map params = [id: lightId, on: level > 0, brightness: level]
  if (transitionMs != null) { params.transition_duration = (transitionMs / 1000.0) as BigDecimal }
  parent?.parentSendCommand(device, 'Light.Set', params)
}

void componentStartLevelChange(def childDevice, String direction) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentStartLevelChange(${direction}) from light ${lightId}")
  String dimMethod = direction == 'up' ? 'Light.DimUp' : 'Light.DimDown'
  parent?.parentSendCommand(device, dimMethod, [id: lightId])
}

void componentStopLevelChange(def childDevice) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentStopLevelChange() from light ${lightId}")
  parent?.parentSendCommand(device, 'Light.DimStop', [id: lightId])
}

void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

void componentResetEnergyMonitors(def childDevice) {
  Integer lightId = childDevice.getDataValue('lightId') as Integer
  logDebug("componentResetEnergyMonitors() from light ${lightId}")
  parent?.parentSendCommand(device, 'Light.ResetCounters', [id: lightId, type: ['aenergy']])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Light Commands and Aggregation                        ║
// ╚══════════════════════════════════════════════════════════════╝

void on() {
  logDebug('Parent on() — turning on all dimmers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    parent?.parentSendCommand(device, 'Light.Set', [id: lightId, on: true])
  }
}

void off() {
  logDebug('Parent off() — turning off all dimmers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    parent?.parentSendCommand(device, 'Light.Set', [id: lightId, on: false])
  }
}

void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("Parent setLevel(${level}, ${duration}) — setting all dimmers")
  Integer durationMs = duration != null ? (duration * 1000) as Integer : null
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    Map params = [id: lightId, on: level > 0, brightness: level as Integer]
    if (durationMs != null) { params.transition_duration = (durationMs / 1000.0) as BigDecimal }
    parent?.parentSendCommand(device, 'Light.Set', params)
  }
}

void startLevelChange(String direction) {
  logDebug("Parent startLevelChange(${direction}) — all dimmers")
  String dimMethod = direction == 'up' ? 'Light.DimUp' : 'Light.DimDown'
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    parent?.parentSendCommand(device, dimMethod, [id: lightId])
  }
}

void stopLevelChange() {
  logDebug('Parent stopLevelChange() — all dimmers')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    parent?.parentSendCommand(device, 'Light.DimStop', [id: lightId])
  }
}

void resetEnergyMonitors() {
  logDebug('resetEnergyMonitors() called')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-light-') }.each { child ->
    Integer lightId = child.getDataValue('lightId') as Integer
    parent?.parentSendCommand(device, 'Light.ResetCounters', [id: lightId, type: ['aenergy']])
  }
}

/**
 * Processes light state aggregation from webhook notifications.
 *
 * @param params The parsed webhook parameters
 */
private void processWebhookAggregation(Map params) {
  String dst = params.dst
  if (!dst) { return }

  Boolean lightState = null
  Integer brightness = null
  switch (dst) {
    case 'light_on':
      lightState = true
      if (params.brightness != null) { brightness = params.brightness as Integer }
      break
    case 'light_off':
      lightState = false
      break
    case 'light_change':
      if (params.brightness != null) { brightness = params.brightness as Integer }
      break
    case 'lightmon':
      if (params.output != null) { lightState = (params.output == 'true') }
      if (params.brightness != null) { brightness = params.brightness as Integer }
      break
  }

  if (lightState != null) {
    String cid = params.cid as String
    Map lightStates = state.lightStates ?: [:]
    lightStates[cid] = lightState
    state.lightStates = lightStates
    updateParentSwitchState()
  }

  if (brightness != null) {
    String cid = params.cid as String
    Map lightLevels = state.lightLevels ?: [:]
    lightLevels[cid] = brightness
    state.lightLevels = lightLevels
    updateParentLevel()
  }

  // Input aggregation (single input -> parent button events)
  if (['input_push', 'input_double', 'input_long'].contains(dst)) {
    String componentStr = device.getDataValue('components') ?: ''
    Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
    if (inputCount <= 1) {
      String eventName = dst == 'input_push' ? 'pushed' : (dst == 'input_double' ? 'doubleTapped' : 'held')
      sendEvent(name: 'numberOfButtons', value: 1)
      sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button was ${eventName}")
    }
  }
}

/**
 * Updates aggregate switch state based on preference.
 */
private void updateParentSwitchState() {
  Map lightStates = state.lightStates ?: [:]
  if (lightStates.isEmpty()) { return }

  String mode = settings.switchAggregation ?: 'anyOn'
  Boolean parentOn = (mode == 'allOn') ?
    lightStates.values().every { it == true } :
    lightStates.values().any { it == true }

  String newState = parentOn ? 'on' : 'off'
  if (device.currentValue('switch') != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Parent light is ${newState}")
    logInfo("Parent switch: ${newState} (mode: ${mode})")
  }
}

/**
 * Updates aggregate brightness level as maximum of all dimmer levels.
 */
private void updateParentLevel() {
  Map lightLevels = state.lightLevels ?: [:]
  if (lightLevels.isEmpty()) { return }

  Integer maxLevel = lightLevels.values().collect { it as Integer }.max()
  sendEvent(name: 'level', value: maxLevel, unit: '%',
    descriptionText: "Parent brightness is ${maxLevel}%")
}

/**
 * Processes webhook power aggregation for parent device.
 *
 * @param params The parsed webhook parameters
 */
private void processWebhookPowerAggregation(Map params) {
  String dst = params.dst
  if (dst != 'powermon') { return }

  String cid = params.cid as String
  Map powerStates = state.powerStates ?: [:]

  Map compPower = [:]
  if (params.voltage != null) { compPower.voltage = params.voltage as BigDecimal }
  if (params.current != null) { compPower.current = params.current as BigDecimal }
  if (params.apower != null) { compPower.apower = params.apower as BigDecimal }
  if (params.aenergy != null) { compPower.aenergy = params.aenergy as BigDecimal }
  powerStates[cid] = compPower
  state.powerStates = powerStates

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

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  logDebug("Aggregate power: ${totalPower}W, energy: ${totalEnergy}kWh, current: ${totalCurrent}A, voltage: ${maxVoltage}V")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Light Commands and Aggregation                    ║
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

    boolean routeToChild = false
    if (baseType == 'light') {
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

  updateAggregatesFromStatus(deviceStatus)
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Builds events from Shelly.GetStatus component data.
 *
 * @param componentType The component type
 * @param statusData The status data map
 * @return List of event maps
 */
private List<Map> buildStatusEvents(String componentType, Map statusData) {
  List<Map> events = []

  if (componentType == 'light') {
    if (statusData.output != null) {
      events.add([name: 'switch', value: statusData.output ? 'on' : 'off'])
    }
    if (statusData.brightness != null) {
      events.add([name: 'level', value: statusData.brightness as Integer, unit: '%'])
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
  Map lightStates = [:]
  Map lightLevels = [:]
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('light:') && v instanceof Map) {
      if (v.output != null) { lightStates[key] = v.output }
      if (v.brightness != null) { lightLevels[key] = v.brightness as Integer }
      if (v.apower != null) { totalPower += v.apower as BigDecimal }
      if (v.aenergy?.total != null) { totalEnergy += (v.aenergy.total as BigDecimal) / 1000 }
      if (v.current != null) { totalCurrent += v.current as BigDecimal }
      if (v.voltage != null) {
        BigDecimal volt = v.voltage as BigDecimal
        if (volt > maxVoltage) { maxVoltage = volt }
      }
    }
  }

  state.lightStates = lightStates
  state.lightLevels = lightLevels
  updateParentSwitchState()
  updateParentLevel()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')

  // Temperature from device internal sensor
  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('temperature:') && v instanceof Map) {
      BigDecimal tempC = v.tC != null ? v.tC as BigDecimal : null
      BigDecimal tempF = v.tF != null ? v.tF as BigDecimal : null
      if (tempC != null || tempF != null) {
        String scale = getLocationHelper()?.temperatureScale ?: 'F'
        BigDecimal temp
        if (scale == 'C') { temp = tempC }
        else if (tempF != null) { temp = tempF }
        else if (tempC != null) { temp = tempC * 9 / 5 + 32 }
        if (temp != null) {
          sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
        }
      }
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Non-static helper to get location.
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
