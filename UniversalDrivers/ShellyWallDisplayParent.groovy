/**
 * Shelly Autoconf Wall Display Parent
 *
 * Parent driver for Shelly Wall Display-family devices.
 * Supports the original Wall Display / Wall Display X2 sensor mix as well as the
 * newer X2i / XL variants that can omit temperature and humidity, and the X2i
 * 2-output power base that exposes multiple relay outputs.
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent handles sensor data directly
 *   - Switch children are created only when multiple switch outputs are present
 *   - Input children are created only if multiple inputs are present
 *   - Sensor data arrives via webhooks, switch commands via Shelly RPC
 *   - The Wall Display firmware reports physical buttons only as raw presses
 *     (webhook input.toggle_on / WebSocket single_push); double, triple, hold and
 *     release are classified on the hub
 *   - Optional local WebSocket (ws://<ip>/rpc) delivers the same events plus media
 *     state in real time; when connected, button webhooks are ignored (no duplicates)
 *
 * Authors: Daniel Winks (original), Ricardo Nogueira Garcia (WebSocket, gestures, media)
 *
 * Version: 1.1.0
 *
 * Changelog:
 *   1.1.0 (2026-10-02) - Ricardo Nogueira Garcia
 *     - Optional local WebSocket client with auto-reconnect and watchdog (default off)
 *     - Physical buttons: push / double / triple / hold / release classified on the hub
 *       from the firmware's single_push stream (autorepeat ~50 ms while held)
 *     - MotionSensor (radar motion:0) and media/volume/ringtone state
 *     - Touch gestures (swipe up/down, multi-touch) as the 'gesture' attribute and as
       parent buttons 1/2/3 (Button Controller friendly)
     - No lastUpdated event on button events (halves event writes per press)
 *     - Commands: screen on/off, screen brightness, Chime (ringtones), AudioVolume,
 *       media play/stop/next/previous
 *     - Webhook routes for motion_start/end and touch_* events; input_push and
       input_toggle_on feed the gesture classifier
     - Ignore webhook path values left unexpanded by the firmware (${...} battery
       tokens on mains-powered displays) instead of failing the whole message
 *   1.0.0 - Daniel Winks - initial release
 */

metadata {
  definition(name: 'Shelly Autoconf Wall Display Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'RelativeHumidityMeasurement'
    //Attributes: humidity - NUMBER

    capability 'IlluminanceMeasurement'
    //Attributes: illuminance - NUMBER

    capability 'MotionSensor'
    //Attributes: motion - ENUM ["active", "inactive"]

    capability 'PushableButton'
    capability 'DoubleTapableButton'
    capability 'HoldableButton'
    capability 'ReleasableButton'

    capability 'Chime'
    //Attributes: soundEffects - JSON_OBJECT, soundName - STRING, status - ENUM
    //Commands: playSound(soundnumber), stop()

    capability 'AudioVolume'
    //Attributes: mute - ENUM, volume - NUMBER
    //Commands: mute(), unmute(), setVolume(volumelevel), volumeUp(), volumeDown()

    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    command 'screenOn'
    command 'screenOff'
    command 'setScreenBrightness', [[name: 'level*', type: 'NUMBER', description: 'Screen brightness 0-100 (-1 = automatic)']]
    command 'mediaPlay'
    command 'mediaStop'
    command 'mediaNext'
    command 'mediaPrevious'
    command 'connectWebSocket'

    attribute 'lastUpdated', 'string'
    attribute 'temperatureStatus', 'string'
    attribute 'humidityStatus', 'string'
    attribute 'gesture', 'string'
    attribute 'webSocket', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'tempOffset', type: 'decimal', title: 'Temperature Offset', defaultValue: 0, range: '-10..10'
  input name: 'humidityOffset', type: 'decimal', title: 'Humidity Offset', defaultValue: 0, range: '-25..25'
  input name: 'enableWebSocket', type: 'bool', title: 'Real-time events via local WebSocket (optional)',
    description: 'Lower latency and lighter hub load while a button is held; also reports media/volume state. Webhooks are used when disabled.',
    defaultValue: false
  input name: 'tapWindowMs', type: 'number', title: 'Multi-tap window (ms)',
    description: 'Max gap between taps for double/triple tap; single push is reported after this delay',
    defaultValue: 500, range: '200..1500'
  input name: 'instantPush', type: 'bool', title: 'Report push immediately',
    description: 'Push fires on the first press with no delay; double/triple taps still fire their own events afterwards',
    defaultValue: false
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

void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
  if (settings?.enableWebSocket == true) {
    state.wsRetrySeconds = WS_RETRY_MIN_SECONDS
    wsConnect()
    runEvery5Minutes('wsWatchdog')
  } else {
    unschedule('wsWatchdog')
    unschedule('wsConnect')
    wsClose()
    sendEventIfChanged('webSocket', 'disabled')
  }
}

void uninstalled() {
  wsClose()
}

void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('refresh() called')
  if (!wsCall('Shelly.GetStatus', [:], 'fullStatus')) {
    parent?.parentRefresh(device)
  }
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
 * Creates switch children when more than 1 relay output is present, and input
 * children when more than 1 physical input is present.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found - skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }
  Integer switchCount = components.findAll { it.startsWith('switch:') }.size()
  Integer inputCount = components.findAll { it.startsWith('input:') }.size()

  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType == 'switch' && switchCount > 1) {
      desiredDnis.add("${device.deviceNetworkId}-switch-${compId}".toString())
    } else if (baseType == 'input' && inputCount > 1) {
      desiredDnis.add("${device.deviceNetworkId}-input-${compId}".toString())
    }
  }

  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer

    String driverName = null
    String label = null
    String childDni = null

    if (baseType == 'switch' && switchCount > 1) {
      driverName = 'Shelly Autoconf Switch'
      label = "${device.displayName} Switch ${compId}"
      childDni = "${device.deviceNetworkId}-switch-${compId}"
    } else if (baseType == 'input' && inputCount > 1) {
      driverName = 'Shelly Autoconf Input Button'
      label = "${device.displayName} Input ${compId}"
      childDni = "${device.deviceNetworkId}-input-${compId}"
    }

    if (!driverName || !childDni || getChildDevice(childDni)) { return }

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

  // Parent buttons: touch gestures (swipe up / swipe down / multi-touch), numbered
  // after the single input when inputs are not split into children
  Integer buttons = getGestureButtonOffset() + GESTURE_NAMES.size()
  if (device.currentValue('numberOfButtons')?.toString() != buttons.toString()) {
    sendEvent(name: 'numberOfButtons', value: buttons)
  }
}

/**
 * First parent button number used for touch gestures minus one. Gestures are
 * buttons 1-3 when inputs have their own child devices; otherwise they follow
 * the input button numbers used on the parent.
 *
 * @return Offset added to the gesture index (1-based)
 */
private Integer getGestureButtonOffset() {
  Integer inputCount = getComponentIds('input').size()
  return inputCount > 1 ? 0 : inputCount
}

/**
 * Reports a touch gesture both as the 'gesture' attribute (for rules on the
 * attribute) and as a parent button press (for Button Controller):
 * swipe up = 1, swipe down = 2, multi-touch = 3 (plus offset).
 *
 * @param eventKey Firmware event / webhook dst, e.g. 'touch_swipe_up'
 */
private void emitGesture(String eventKey) {
  String gestureName = GESTURE_NAMES[eventKey]
  if (!gestureName) { return }
  Integer button = getGestureButtonOffset() + (GESTURE_NAMES.keySet() as List).indexOf(eventKey) + 1
  sendEvent(name: 'gesture', value: gestureName, isStateChange: true, descriptionText: "Touch gesture: ${gestureName}")
  sendEvent(name: 'pushed', value: button, isStateChange: true, descriptionText: "Button ${button} (${gestureName}) was pushed")
  logInfo("Gesture ${gestureName} -> button ${button}")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

private List<Integer> getComponentIds(String componentType) {
  String componentStr = device.getDataValue('components') ?: ''
  if (!componentStr) { return [] }
  return componentStr.split(',')
    .collect { it.trim() }
    .findAll { it.startsWith("${componentType}:") }
    .collect { String comp -> comp.split(':')[1] as Integer }
}

private Boolean isMultiSwitchDevice() {
  return getComponentIds('switch').size() > 1
}

private void updateParentSwitchState() {
  Map switchStates = state.switchStates ?: [:]
  if (switchStates.isEmpty()) { return }

  String newState = switchStates.values().any { it == true } ? 'on' : 'off'
  if (device.currentValue('switch') != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Wall display switch is ${newState}")
  }
}

private void setSwitchState(Integer switchId, Boolean isOn) {
  if (switchId == null) { return }
  Map switchStates = state.switchStates ?: [:]
  switchStates["switch:${switchId}".toString()] = isOn
  state.switchStates = switchStates
  updateParentSwitchState()
}

private void refreshSwitchStatesFromStatus(Map deviceStatus) {
  Map switchStates = [:]
  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('switch:') && v instanceof Map && v.output != null) {
      switchStates[key] = v.output
    }
  }
  state.switchStates = switchStates
  updateParentSwitchState()
}

private Boolean hasMissingSensorDriverError(Map statusData) {
  List errors = statusData?.errors instanceof List ? statusData.errors as List : []
  return errors.any { Object err -> err?.toString() == 'Sensor driver missing from firmware' }
}

private Boolean hasUsableTemperature(Map statusData) {
  if (!statusData || hasMissingSensorDriverError(statusData)) { return false }

  BigDecimal tempC = statusData.tC != null ? statusData.tC as BigDecimal : null
  BigDecimal tempF = statusData.tF != null ? statusData.tF as BigDecimal : null
  if (tempC != null && tempC <= -100) { return false }
  if (tempF != null && tempF <= -148) { return false }
  return tempC != null || tempF != null
}

private Boolean hasUsableHumidity(Map statusData) {
  if (!statusData || hasMissingSensorDriverError(statusData)) { return false }

  BigDecimal humidity = statusData.rh != null ? statusData.rh as BigDecimal : null
  return humidity != null && humidity >= 0
}

private BigDecimal getWebhookTemperatureValue(Map params, String scale) {
  BigDecimal tempC = params.tC != null ? params.tC as BigDecimal : null
  BigDecimal tempF = params.tF != null ? params.tF as BigDecimal : null
  if (tempC != null && tempC <= -100) { return null }
  if (tempF != null && tempF <= -148) { return null }

  if (scale == 'C' && tempC != null) { return tempC }
  if (scale == 'C' && tempF != null) { return (tempF - 32) * 5 / 9 }
  if (tempF != null) { return tempF }
  if (tempC != null) { return tempC * 9 / 5 + 32 }
  return null
}

private void updateSensorAvailability(String attributeName, String newStatus) {
  if (device.currentValue(attributeName) != newStatus) {
    sendEvent(name: attributeName, value: newStatus)
  }
}

void on() {
  logDebug('on() called')
  List<Integer> switchIds = getComponentIds('switch')
  if (!switchIds) { logWarn('No switch component found'); return }
  switchIds.each { Integer switchId ->
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
  }
}

void off() {
  logDebug('off() called')
  List<Integer> switchIds = getComponentIds('switch')
  if (!switchIds) { logWarn('No switch component found'); return }
  switchIds.each { Integer switchId ->
    parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

void componentOn(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId')?.toInteger()
  logDebug("componentOn() from switch ${switchId}")
  if (switchId == null) { return }
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

void componentOff(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId')?.toInteger()
  logDebug("componentOff() from switch ${switchId}")
  if (switchId == null) { return }
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

void componentUpdateSwitchSettings(def childDevice, Map switchSettings) {
  Integer switchId = childDevice.getDataValue('switchId')?.toInteger()
  logDebug("componentUpdateSwitchSettings() from switch ${switchId}: ${switchSettings}")
  if (switchId == null) { return }
  parent?.parentUpdateSwitchSettings(device, switchId, switchSettings)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Routing (parse)                                        ║
// ╚══════════════════════════════════════════════════════════════╝

void parse(String description) {
  if (description?.startsWith('{')) {
    handleWebSocketMessage(description)
    return
  }
  try {
    Map msg = parseLanMessage(description)
    if (msg?.status != null) { return }

    // Fast BLE relay path: skip IP check, JSON parsing, and logging
    if (msg?.body != null) {
      String body = msg.body as String
      if (body.startsWith('{"dst":"ble"')) {
        parent?.handleBleRelayRaw(device, body)
        return
      }
    }

    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }
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

private void handlePostWebhook(Map msg) {
  try {
    Map json = new groovy.json.JsonSlurper().parseText(msg.body) as Map
    String dst = json?.dst?.toString()
    if (!dst) { logTrace('POST webhook: no dst in body'); return }

    Map params = [:]
    json.each { k, v -> if (v != null) { params[k.toString()] = v.toString() } }

    logDebug("POST webhook dst=${dst}, cid=${params.cid}")
    logTrace("POST webhook params: ${params}")
    routeWebhookNotification(params)
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

private void handleGetWebhook(Map msg) {
  Map params = parseWebhookPath(msg)
  if (params?.dst) {
    logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
    logTrace("GET webhook params: ${params}")
    routeWebhookNotification(params)
  } else {
    logDebug("GET webhook: no dst found")
  }
}

private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  // Touch gestures -> 'gesture' attribute + parent button events
  if (GESTURE_NAMES.containsKey(dst)) {
    emitGesture(dst)
    return
  }

  // Physical button presses: the Wall Display only reports raw presses (toggle_on),
  // so they go through the hub-side gesture classifier. When the WebSocket is
  // connected it already delivers the same presses, so the webhook is ignored.
  if (dst == 'input_push' || dst == 'input_toggle_on') {
    if (isWebSocketOpen()) {
      logTrace("Ignoring ${dst} webhook for input ${params.cid} (WebSocket active)")
    } else {
      onInputPress(params.cid as Integer)
    }
    return
  }

  String nowStr = new Date().format('yyyy-MM-dd HH:mm:ss')
  List<Map> events = buildWebhookEvents(dst, params)
  if (!events) {
    if (dst == 'temperature') {
      updateSensorAvailability('temperatureStatus', 'unavailable')
      sendEvent(name: 'lastUpdated', value: nowStr)
    } else if (dst == 'humidity') {
      updateSensorAvailability('humidityStatus', 'unavailable')
      sendEvent(name: 'lastUpdated', value: nowStr)
    }
    return
  }

  if (dst == 'temperature') {
    updateSensorAvailability('temperatureStatus', 'ok')
  } else if (dst == 'humidity') {
    updateSensorAvailability('humidityStatus', 'ok')
  }

  Integer componentId = params.cid as Integer

  if (dst.startsWith('switch_') && isMultiSwitchDevice()) {
    String childDni = "${device.deviceNetworkId}-switch-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: nowStr)
      Map switchEvent = events.find { Map evt -> evt.name == 'switch' } as Map
      if (switchEvent?.value != null) {
        setSwitchState(componentId, switchEvent.value.toString() == 'on')
      }
      sendEvent(name: 'lastUpdated', value: nowStr)
      return
    }
  }

  // Input events -> route to children if > 1 input
  if (dst.startsWith('input_')) {
    Integer inputCount = getComponentIds('input').size()
    if (inputCount > 1) {
      String childDni = "${device.deviceNetworkId}-input-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt -> child.sendEvent(evt) }
        child.sendEvent(name: 'lastUpdated', value: nowStr)
      }
      return
    }
  }

  // Everything else -> parent
  events.each { Map evt -> sendEvent(evt) }
  if (dst.startsWith('switch_')) {
    Map switchEvent = events.find { Map evt -> evt.name == 'switch' } as Map
    if (switchEvent?.value != null) {
      setSwitchState(componentId, switchEvent.value.toString() == 'on')
    }
  }
  sendEvent(name: 'lastUpdated', value: nowStr)
}

private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'switch_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Switch turned on'])
      break
    case 'switch_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Switch turned off'])
      break

    case 'temperature':
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
      BigDecimal temp = getWebhookTemperatureValue(params, scale)
      if (temp != null) {
        BigDecimal offset = settings?.tempOffset != null ? settings.tempOffset as BigDecimal : 0
        temp = temp + offset
        events.add([name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}"])
      }
      break

    case 'humidity':
      if (params.rh != null && (params.rh as BigDecimal) >= 0) {
        BigDecimal humidity = params.rh as BigDecimal
        BigDecimal offset = settings?.humidityOffset != null ? settings.humidityOffset as BigDecimal : 0
        humidity = humidity + offset
        events.add([name: 'humidity', value: humidity, unit: '%',
          descriptionText: "Humidity is ${humidity}%"])
      }
      break

    case 'illuminance':
      if (params.lux != null) {
        events.add([name: 'illuminance', value: params.lux as Integer,
          unit: 'lux', descriptionText: "Illuminance is ${params.lux} lux"])
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

    // Radar and touch webhooks (Wall Display XL)
    case 'motion_start':
      events.add([name: 'motion', value: 'active', descriptionText: 'Motion detected'])
      break
    case 'motion_end':
      events.add([name: 'motion', value: 'inactive', descriptionText: 'Motion cleared'])
      break
  }

  return events
}

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
    // Skip values the firmware left unexpanded (e.g. battery tokens on mains-powered displays)
    if (segments[i + 1].contains('${') || segments[i + 1].contains('%24%7B')) { continue }
    result[segments[i]] = segments[i + 1]
  }

  return result
}

@CompileStatic
private static String convertHexToIP(String hex) {
  if (!hex || hex.length() != 8) { return null }
  return [Integer.parseInt(hex[0..1], 16),
          Integer.parseInt(hex[2..3], 16),
          Integer.parseInt(hex[4..5], 16),
          Integer.parseInt(hex[6..7], 16)].join('.')
}

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
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  Boolean multiSwitch = isMultiSwitchDevice()
  List<Integer> temperatureComponents = getComponentIds('temperature')
  List<Integer> humidityComponents = getComponentIds('humidity')
  String temperatureStatus = temperatureComponents ? 'unavailable' : 'not present'
  String humidityStatus = humidityComponents ? 'unavailable' : 'not present'

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.contains(':') || !(v instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Integer componentId = key.split(':')[1] as Integer
    Map statusData = v as Map

    if (baseType == 'switch') {
      if (statusData.output != null) {
        String switchState = statusData.output ? 'on' : 'off'
        if (multiSwitch) {
          String childDni = "${device.deviceNetworkId}-switch-${componentId}"
          def child = getChildDevice(childDni)
          if (child) {
            child.sendEvent(name: 'switch', value: switchState)
            child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
          }
        } else {
          sendEvent(name: 'switch', value: switchState)
        }
      }
    } else if (baseType == 'temperature') {
      BigDecimal tempC = statusData.tC != null ? statusData.tC as BigDecimal : null
      BigDecimal tempF = statusData.tF != null ? statusData.tF as BigDecimal : null
      if (hasUsableTemperature(statusData)) {
        String scale = getLocationHelper()?.temperatureScale ?: 'F'
        BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC * 9 / 5 + 32)
        BigDecimal offset = settings?.tempOffset != null ? settings.tempOffset as BigDecimal : 0
        temp = temp + offset
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}")
        temperatureStatus = 'ok'
      }
    } else if (baseType == 'humidity') {
      if (hasUsableHumidity(statusData)) {
        BigDecimal humidity = statusData.rh as BigDecimal
        BigDecimal offset = settings?.humidityOffset != null ? settings.humidityOffset as BigDecimal : 0
        humidity = humidity + offset
        sendEvent(name: 'humidity', value: humidity, unit: '%')
        humidityStatus = 'ok'
      }
    } else if (baseType == 'illuminance') {
      if (statusData.lux != null) {
        sendEvent(name: 'illuminance', value: statusData.lux as Integer, unit: 'lux')
      }
    }
  }

  updateSensorAvailability('temperatureStatus', temperatureStatus)
  updateSensorAvailability('humidityStatus', humidityStatus)
  refreshSwitchStatesFromStatus(deviceStatus)
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  WebSocket (real-time events)                                 ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the local RPC WebSocket (ws://<ip>/rpc). The device starts pushing
 * NotifyStatus / NotifyEvent frames to this client after the first request
 * that carries a 'src', which is sent from webSocketStatus() on open.
 */
void wsConnect() {
  if (settings?.enableWebSocket != true) { return }
  String ip = device.getDataValue('ipAddress')
  if (!ip) {
    logWarn('wsConnect: no ipAddress data value')
    return
  }
  try {
    interfaces.webSocket.close()
  } catch (Exception ignored) { }
  try {
    logDebug("Connecting WebSocket to ws://${ip}/rpc")
    interfaces.webSocket.connect("ws://${ip}/rpc", pingInterval: WS_PING_SECONDS)
  } catch (Exception e) {
    logWarn("WebSocket connect failed: ${e.message}")
    wsScheduleReconnect()
  }
}

/** Manual command: forces a reconnect. */
void connectWebSocket() {
  state.wsRetrySeconds = WS_RETRY_MIN_SECONDS
  wsConnect()
}

/**
 * Connection state is tracked in memory: device.currentValue() can still return
 * the previous value right after sendEvent() in the same execution.
 *
 * @return true if the WebSocket is enabled and currently open
 */
private Boolean isWebSocketOpen() {
  return settings?.enableWebSocket == true && wsOpen.get(device.id.toString()) == true
}

private void wsClose() {
  wsOpen.put(device.id.toString(), false)
  try {
    interfaces.webSocket.close()
  } catch (Exception ignored) { }
}

/**
 * Hubitat WebSocket status callback.
 *
 * @param message 'status: open', 'status: closing' or 'failure: <reason>'
 */
void webSocketStatus(String message) {
  logDebug("webSocketStatus: ${message}")
  if (message?.startsWith('status: open')) {
    wsOpen.put(device.id.toString(), true)
    state.wsRetrySeconds = WS_RETRY_MIN_SECONDS
    state.wsLastMessage = now()
    sendEventIfChanged('webSocket', 'connected')
    logInfo('WebSocket connected')
    wsCall('Shelly.GetStatus', [:], 'fullStatus')
    wsCall('Media.List', [type: 'ringtone'], 'ringtones')
  } else if (message?.startsWith('status: closing') || message?.startsWith('failure')) {
    wsOpen.put(device.id.toString(), false)
    if (settings?.enableWebSocket != true) {
      // Expected close after the preference was turned off
      sendEventIfChanged('webSocket', 'disabled')
      return
    }
    sendEventIfChanged('webSocket', 'disconnected')
    if (settings?.enableWebSocket == true) {
      logWarn("WebSocket ${message} - reconnecting in ${state.wsRetrySeconds ?: WS_RETRY_MIN_SECONDS}s")
      wsScheduleReconnect()
    }
  }
}

private void wsScheduleReconnect() {
  Integer delay = (state.wsRetrySeconds ?: WS_RETRY_MIN_SECONDS) as Integer
  runIn(delay, 'wsConnect')
  state.wsRetrySeconds = Math.min(delay * 2, WS_RETRY_MAX_SECONDS)
}

/**
 * Runs every 5 minutes. The Wall Display pushes an illuminance status every
 * minute, so a silent socket for longer than WS_STALE_MS is treated as dead.
 */
void wsWatchdog() {
  if (settings?.enableWebSocket != true) { return }
  Long last = (state.wsLastMessage ?: 0L) as Long
  if (now() - last > WS_STALE_MS) {
    logWarn('WebSocket silent for too long - reconnecting')
    sendEventIfChanged('webSocket', 'reconnecting')
    wsConnect()
  }
}

/**
 * Sends an RPC request over the WebSocket.
 *
 * @param method RPC method name
 * @param params RPC parameters
 * @param purpose Tag used to route the response ('' to ignore it)
 * @return true if the request was sent, false if the socket is not connected
 */
private Boolean wsCall(String method, Map params, String purpose = '') {
  if (!isWebSocketOpen()) { return false }
  Integer id = nextRpcId()
  if (purpose) { pendingRpc.put("${device.id}:${id}".toString(), purpose) }
  Map req = [id: id, src: "hubitat-${device.id}".toString(), method: method, params: params ?: [:]]
  try {
    interfaces.webSocket.sendMessage(JsonOutput.toJson(req))
    logTrace("WS -> ${method} ${params}")
    return true
  } catch (Exception e) {
    logWarn("WebSocket send failed (${method}): ${e.message}")
    pendingRpc.remove("${device.id}:${id}".toString())
    return false
  }
}

/**
 * Sends an RPC command over the WebSocket, falling back to the app (HTTP RPC)
 * when the socket is unavailable.
 */
private void sendRpcCommand(String method, Map params) {
  if (!wsCall(method, params, 'command')) {
    parent?.parentSendCommand(device, method, params)
  }
}

@CompileStatic
private static Integer nextRpcId() {
  return (Integer) (rpcCounter.incrementAndGet() % 1000000) + 1
}

/**
 * Handles one WebSocket text frame.
 *
 * @param text JSON frame from the device
 */
private void handleWebSocketMessage(String text) {
  state.wsLastMessage = now()
  Map msg
  try {
    msg = new groovy.json.JsonSlurper().parseText(text) as Map
  } catch (Exception e) {
    logDebug("WS: unparseable frame: ${e.message}")
    return
  }
  String method = msg.method?.toString()
  Map params = (msg.params instanceof Map) ? msg.params as Map : [:]

  if (method == 'NotifyEvent') {
    (params.events ?: []).each { Object e -> if (e instanceof Map) { handleDeviceEvent(e as Map) } }
  } else if (method == 'NotifyStatus' || method == 'NotifyFullStatus') {
    applyStatusDelta(params)
  } else if (msg.id != null && (msg.containsKey('result') || msg.containsKey('error'))) {
    handleRpcResponse(msg)
  }
}

private void handleRpcResponse(Map msg) {
  String purpose = pendingRpc.remove("${device.id}:${msg.id}".toString())
  if (msg.error) {
    logWarn("RPC error (${purpose ?: 'unknown'}): ${msg.error}")
    return
  }
  Map result = (msg.result instanceof Map) ? msg.result as Map : [:]
  switch (purpose) {
    case 'fullStatus':
      distributeStatus(result)
      applyStatusDelta(result.findAll { k, v -> k.toString().startsWith('motion') || k.toString() == 'media' })
      break
    case 'ringtones':
      updateRingtoneList(result)
      break
  }
}

/**
 * Applies a partial (NotifyStatus) or full status map without touching
 * components that are absent from the map.
 */
private void applyStatusDelta(Map status) {
  Boolean changed = false
  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map
    if (key.startsWith('motion:') && data.motion != null) {
      changed |= sendEventIfChanged('motion', data.motion ? 'active' : 'inactive')
    } else if (key.startsWith('illuminance:') && data.lux != null) {
      changed |= sendEventIfChanged('illuminance', data.lux as Integer, 'lux')
    } else if (key.startsWith('switch:') && data.output != null) {
      changed |= applySwitchDelta(key.split(':')[1] as Integer, data.output as Boolean)
    } else if (key == 'media') {
      changed |= applyMediaStatus(data)
    } else if (key.startsWith('temperature:') && hasUsableTemperature(data)) {
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
      BigDecimal temp = getWebhookTemperatureValue([tC: data.tC, tF: data.tF], scale)
      if (temp != null) {
        BigDecimal offset = settings?.tempOffset != null ? settings.tempOffset as BigDecimal : 0
        sendEvent(name: 'temperature', value: temp + offset, unit: "°${scale}")
        updateSensorAvailability('temperatureStatus', 'ok')
        changed = true
      }
    } else if (key.startsWith('humidity:') && hasUsableHumidity(data)) {
      BigDecimal offset = settings?.humidityOffset != null ? settings.humidityOffset as BigDecimal : 0
      sendEvent(name: 'humidity', value: (data.rh as BigDecimal) + offset, unit: '%')
      updateSensorAvailability('humidityStatus', 'ok')
      changed = true
    }
  }
  if (changed) { sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss')) }
}

/**
 * Applies a single relay state change (multi-switch children or parent).
 *
 * @param switchId Shelly switch id
 * @param isOn New output state
 * @return true if an event was sent
 */
private Boolean applySwitchDelta(Integer switchId, Boolean isOn) {
  String switchState = isOn ? 'on' : 'off'
  Boolean changed = true
  if (isMultiSwitchDevice()) {
    def child = getChildDevice("${device.deviceNetworkId}-switch-${switchId}")
    if (child) {
      child.sendEvent(name: 'switch', value: switchState)
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    }
  } else {
    changed = sendEventIfChanged('switch', switchState)
  }
  setSwitchState(switchId, isOn)
  return changed
}

/**
 * Handles one entry of a NotifyEvent frame.
 *
 * @param e Event map, e.g. [component: 'input', id: 1, event: 'single_push']
 */
private void handleDeviceEvent(Map e) {
  String component = e.component?.toString() ?: ''
  String event = e.event?.toString() ?: ''
  Integer id = e.id != null ? e.id as Integer : (component.contains(':') ? component.split(':')[1] as Integer : 0)
  String baseType = component.contains(':') ? component.split(':')[0] : component
  logTrace("WS event ${component} id=${id} ${event}")

  if (GESTURE_NAMES.containsKey(event)) {
    emitGesture(event)
    return
  }
  if (baseType != 'input') { return }

  switch (event) {
    case 'single_push':
      onInputPress(id)
      break
    case 'double_push':
      emitButtonEvent(id, 'doubleTapped')
      break
    case 'triple_push':
      emitButtonEvent(id, 'tripleTapped')
      break
    case 'long_push':
      emitButtonEvent(id, 'held')
      break
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END WebSocket                                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Button Gesture Classification                                ║
// ╚══════════════════════════════════════════════════════════════╝
//
// The Wall Display reports every physical button press as one 'single_push'
// and, while a button is held, repeats 'single_push' roughly every 50 ms after
// an initial ~370 ms delay (keyboard-style autorepeat). Taps of a double or
// triple tap arrive 150-450 ms apart. Classification:
//   - two presses closer than HOLD_REPEAT_MS            -> held (once)
//   - no repeat for RELEASE_GAP_MS while held            -> released
//   - otherwise count presses until tapWindowMs of quiet -> pushed / doubleTapped / tripleTapped

private void onInputPress(Integer inputId) {
  String key = "${device.id}:${inputId}".toString()
  Long t = now()
  Map g = gestureStates.get(key)
  if (g != null && t >= (g.deadline as Long)) {
    // A previous gesture is overdue (tick delayed): finalize it before starting a new one
    finalizeGesture(inputId, g)
    gestureStates.remove(key)
    g = null
  }
  if (g == null) { g = [count: 0, last: 0L, holding: false, pushedEmitted: false, deadline: 0L] }
  Long dt = t - (g.last as Long)
  Integer tapWindow = getTapWindowMs()

  if (g.holding) {
    g.last = t
    g.deadline = t + RELEASE_GAP_MS
  } else if ((g.count as Integer) > 0 && dt <= HOLD_REPEAT_MS) {
    g.holding = true
    g.count = 0
    g.last = t
    g.deadline = t + RELEASE_GAP_MS
    emitButtonEvent(inputId, 'held')
  } else {
    g.count = (dt <= tapWindow) ? (g.count as Integer) + 1 : 1
    g.last = t
    g.deadline = t + tapWindow
    if (settings?.instantPush && g.count == 1) {
      emitButtonEvent(inputId, 'pushed')
      g.pushedEmitted = true
    }
  }
  gestureStates.put(key, g)
  scheduleGestureTick()
}

/** Finalizes every gesture of this device whose quiet period has elapsed. */
void gestureTick() {
  Long t = now()
  String prefix = "${device.id}:".toString()
  gestureStates.keySet().findAll { String k -> k.startsWith(prefix) }.each { String key ->
    Map g = gestureStates.get(key)
    if (g == null || t < (g.deadline as Long)) { return }
    Integer inputId = key.substring(prefix.length()) as Integer
    gestureStates.remove(key)
    finalizeGesture(inputId, g)
  }
  scheduleGestureTick()
}

/**
 * Emits the final event of a completed gesture.
 *
 * @param inputId Shelly input id
 * @param g Gesture state map
 */
private void finalizeGesture(Integer inputId, Map g) {
  if (g.holding) {
    emitButtonEvent(inputId, 'released')
    return
  }
  Integer count = g.count as Integer
  if (count == 1 && !g.pushedEmitted) { emitButtonEvent(inputId, 'pushed') }
  else if (count == 2) { emitButtonEvent(inputId, 'doubleTapped') }
  else if (count >= 3) { emitButtonEvent(inputId, 'tripleTapped') }
}

private void scheduleGestureTick() {
  String prefix = "${device.id}:".toString()
  List<Long> deadlines = gestureStates.findAll { k, v -> k.startsWith(prefix) }.collect { k, v -> v.deadline as Long }
  if (!deadlines) { return }
  Long delay = Math.max(20L, deadlines.min() - now() + 5L)
  runInMillis(delay, 'gestureTick', [overwrite: true])
}

private Integer getTapWindowMs() {
  Integer v = settings?.tapWindowMs != null ? settings.tapWindowMs as Integer : 500
  return Math.max(200, Math.min(1500, v))
}

/**
 * Sends a button event to the matching Input child (or to the parent as
 * button 1 when the device has a single input).
 *
 * @param inputId Shelly input id
 * @param eventName pushed | doubleTapped | tripleTapped | held | released
 */
private void emitButtonEvent(Integer inputId, String eventName) {
  String verb = BUTTON_EVENT_VERBS[eventName] ?: eventName
  def child = getChildDevice("${device.deviceNetworkId}-input-${inputId}")
  // No lastUpdated here: button events are already timestamped state changes
  if (child) {
    child.sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button 1 was ${verb}")
  } else {
    // Single input on the parent is button 1 (gestures follow as 2-4)
    sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button 1 was ${verb}")
  }
  logInfo("Input ${inputId}: ${eventName}")
}

// Commands required by the button capabilities (virtual presses from Hubitat)
void push(BigDecimal button) { emitButtonEvent(button as Integer, 'pushed') }
void doubleTap(BigDecimal button) { emitButtonEvent(button as Integer, 'doubleTapped') }
void hold(BigDecimal button) { emitButtonEvent(button as Integer, 'held') }
void release(BigDecimal button) { emitButtonEvent(button as Integer, 'released') }

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Button Gesture Classification                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Screen, Media and Chime                                      ║
// ╚══════════════════════════════════════════════════════════════╝

void screenOn() { sendRpcCommand('Ui.Screen.Set', [on: true]) }
void screenOff() { sendRpcCommand('Ui.Screen.Set', [on: false]) }

/**
 * Sets the screen brightness.
 *
 * @param level 0-100, or -1 for automatic brightness
 */
void setScreenBrightness(BigDecimal level) {
  Integer lvl = level as Integer
  Map brightness = (lvl < 0) ? [auto: true] : [auto: false, level: Math.max(0, Math.min(100, lvl))]
  sendRpcCommand('Ui.SetConfig', [config: [brightness: brightness]])
}

void mediaPlay() { sendRpcCommand('Media.MediaPlayer.Play', [:]) }
void mediaStop() { sendRpcCommand('Media.MediaPlayer.Stop', [:]) }
void mediaNext() { sendRpcCommand('Media.MediaPlayer.Next', [:]) }
void mediaPrevious() { sendRpcCommand('Media.MediaPlayer.Previous', [:]) }

/**
 * Chime: plays a ringtone stored on the display.
 *
 * @param soundnumber Ringtone id (see the soundEffects attribute)
 */
void playSound(BigDecimal soundnumber) {
  Integer id = soundnumber as Integer
  Map sounds = state.ringtones ?: [:]
  sendEvent(name: 'soundName', value: sounds[id.toString()] ?: "Ringtone ${id}")
  sendRpcCommand('Media.MediaPlayer.PlayRingtone', [id: id])
}

void stop() { mediaStop() }

private void updateRingtoneList(Map result) {
  Map sounds = [:]
  (result.list ?: result.items ?: []).each { Object item ->
    if (item instanceof Map && item.id != null) {
      sounds[item.id.toString()] = (item.name ?: item.title ?: "Ringtone ${item.id}").toString()
    }
  }
  if (sounds) {
    state.ringtones = sounds
    sendEventIfChanged('soundEffects', JsonOutput.toJson(sounds))
  }
}

// AudioVolume: Hubitat 0-100, Wall Display 0-10
void setVolume(BigDecimal volumelevel) {
  Integer v = Math.max(0, Math.min(100, volumelevel as Integer))
  sendRpcCommand('Media.SetVolume', [volume: Math.round(v / 10.0) as Integer])
}
void volumeUp() { sendRpcCommand('Media.IncreaseVolume', [:]) }
void volumeDown() { sendRpcCommand('Media.DecreaseVolume', [:]) }
void mute() {
  Integer current = (device.currentValue('volume') ?: 0) as Integer
  if (current > 0) { state.volumeBeforeMute = current }
  sendRpcCommand('Media.SetVolume', [volume: 0])
}
void unmute() {
  Integer restore = (state.volumeBeforeMute ?: 50) as Integer
  setVolume(restore as BigDecimal)
}

private Boolean applyMediaStatus(Map media) {
  Map playback = (media.playback instanceof Map) ? media.playback as Map : [:]
  Boolean changed = false
  if (playback.volume != null) {
    Integer vol = (playback.volume as Integer) * 10
    changed |= sendEventIfChanged('volume', vol, '%')
    changed |= sendEventIfChanged('mute', vol == 0 ? 'muted' : 'unmuted')
  }
  if (playback.enable != null) {
    changed |= sendEventIfChanged('status', playback.enable ? 'playing' : 'stopped')
  }
  return changed
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Screen, Media and Chime                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions                                              ║
// ╚══════════════════════════════════════════════════════════════╝

private Object getLocationHelper() {
  return location
}

/**
 * Sends an event only when the attribute value actually changes.
 *
 * @return true if an event was sent
 */
private Boolean sendEventIfChanged(String name, Object value, String unit = null) {
  if (device.currentValue(name)?.toString() == value?.toString()) { return false }
  Map evt = [name: name, value: value]
  if (unit) { evt.unit = unit }
  sendEvent(evt)
  return true
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Field static final Integer WS_PING_SECONDS = 30
@Field static final Integer WS_RETRY_MIN_SECONDS = 5
@Field static final Integer WS_RETRY_MAX_SECONDS = 300
@Field static final Long WS_STALE_MS = 300000L
@Field static final Long HOLD_REPEAT_MS = 150L
@Field static final Long RELEASE_GAP_MS = 300L

@Field static final Map<String, String> GESTURE_NAMES = [
  'touch_swipe_up': 'swipeUp',
  'touch_swipe_down': 'swipeDown',
  'touch_multi_touch': 'multiTouch'
]
@Field static final Map<String, String> BUTTON_EVENT_VERBS = [
  'pushed': 'pushed', 'doubleTapped': 'double-tapped', 'tripleTapped': 'triple-tapped',
  'held': 'held', 'released': 'released'
]

/** Per-input gesture state, keyed "deviceId:inputId" (in-memory; resets on hub reboot). */
@Field static ConcurrentHashMap<String, Map> gestureStates = new ConcurrentHashMap<String, Map>()
/** Outstanding WebSocket RPC requests, keyed "deviceId:rpcId" -> purpose. */
@Field static ConcurrentHashMap<String, String> pendingRpc = new ConcurrentHashMap<String, String>()
@Field static AtomicInteger rpcCounter = new AtomicInteger(0)
/** WebSocket open flag per device id. */
@Field static ConcurrentHashMap<String, Boolean> wsOpen = new ConcurrentHashMap<String, Boolean>()
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                       ║
// ╚══════════════════════════════════════════════════════════════╝
