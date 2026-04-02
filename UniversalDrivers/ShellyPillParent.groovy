/**
 * Shelly Autoconf Pill Parent
 *
 * Dedicated parent driver for The Pill by Shelly (S3SN-0U53X) when it is used
 * as a configurable sensor / I/O platform rather than the narrow THL-style
 * illuminance profile.
 *
 * Supported child component types:
 *   - switch:N      -> optional SSR output children
 *   - input:N       -> configurable button / switch / analog children
 *   - temperature:N -> DS18B20 or DHT22 temperature children
 *   - humidity:N    -> DHT22 humidity children
 *   - voltmeter:N   -> analog voltage children
 *
 * BLE gateway mode remains on the dedicated BLU Gateway parent when the live
 * component map exposes blugw / blutrv components.
 */
import groovy.transform.CompileStatic
import groovy.transform.Field

metadata {
  definition(name: 'Shelly Autoconf Pill Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
  input name: 'input0Mode', type: 'enum', title: 'Input 0 Mode',
    options: ['button':'Button', 'switch':'Switch', 'analog':'Analog'],
    defaultValue: 'button', required: true
  input name: 'input1Mode', type: 'enum', title: 'Input 1 Mode',
    options: ['button':'Button', 'switch':'Switch'],
    defaultValue: 'button', required: true
  input name: 'input2Mode', type: 'enum', title: 'Input 2 Mode',
    options: ['button':'Button', 'switch':'Switch'],
    defaultValue: 'button', required: true
}

@Field static Boolean PARENT = true

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
  syncInputModes()
}

void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
}

void configure() {
  logDebug('configure() called')
  syncInputModes()
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.parentRefresh(device)
}

void reinitialize() {
  logDebug('reinitialize() called')
  parent?.reinitializeDevice(device)
}

/**
 * Pushes the currently selected input mode to each discovered input component.
 * Only inputs present in the current component list are configured.
 */
private void syncInputModes() {
  List<Integer> inputIds = getInputComponentIds()
  if (!inputIds) { return }

  inputIds.sort().each { Integer inputId ->
    String mode = getConfiguredInputMode(inputId)
    logDebug("Syncing input:${inputId} mode to '${mode}'")
    parent?.parentSendCommand(device, 'Input.SetConfig', [id: inputId, config: [type: mode]])
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level children to match the component list provided by the app.
 * Recreates input children when a mode change requires a different child driver.
 */
void reconcileChildDevices() {
  List<String> components = getInstalledComponents()
  if (!components) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  Set<String> pmComponents = getPowerMonitoredComponents()
  Map<String, String> desiredDriverMap = [:]

  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}".toString()
    String inputMode = (baseType == 'input') ? getConfiguredInputMode(compId) : null
    String driverName = getChildDriverNameForComponent(baseType, compId, pmComponents.contains(comp), inputMode)
    if (driverName) {
      desiredDriverMap[childDni] = driverName
    }
  }

  Map<String, com.hubitat.app.DeviceWrapper> existingChildren = [:]
  getChildDevices()?.each { com.hubitat.app.DeviceWrapper child ->
    existingChildren[child.deviceNetworkId] = child
  }

  logDebug("Child reconciliation: desired=${desiredDriverMap.keySet()}, existing=${existingChildren.keySet()}")

  existingChildren.each { String dni, com.hubitat.app.DeviceWrapper child ->
    if (!desiredDriverMap.containsKey(dni)) {
      logInfo("Removing orphaned child: ${child.displayName} (${dni})")
      deleteChildDevice(dni)
      return
    }

    String desiredDriver = desiredDriverMap[dni]
    String installedDriver = child.getDataValue('installedDriverName')
    if (installedDriver && installedDriver != desiredDriver) {
      logInfo("Driver change detected for ${child.displayName}: was '${installedDriver}', should be '${desiredDriver}' — recreating")
      deleteChildDevice(dni)
    }
  }

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

@CompileStatic
private static String getChildDriverNameForComponent(String baseType, Integer compId, Boolean hasPm, String inputMode) {
  switch (baseType) {
    case 'switch':
      return hasPm ? 'Shelly Autoconf Switch PM' : 'Shelly Autoconf Switch'
    case 'input':
      switch (inputMode) {
        case 'switch': return 'Shelly Autoconf Input Switch'
        case 'analog': return (compId == 0) ? 'Shelly Autoconf Input Analog' : 'Shelly Autoconf Input Button'
        default:       return 'Shelly Autoconf Input Button'
      }
    case 'temperature':
      return 'Shelly Autoconf Temperature Peripheral'
    case 'humidity':
      return 'Shelly Autoconf Humidity Peripheral'
    case 'voltmeter':
      return 'Shelly Autoconf Voltmeter'
    default:
      return null
  }
}

private List<String> getInstalledComponents() {
  String componentStr = device.getDataValue('components') ?: ''
  return componentStr ? componentStr.split(',').collect { it.trim() }.findAll { it } : []
}

private Set<String> getPowerMonitoredComponents() {
  String pmStr = device.getDataValue('pmComponents') ?: ''
  return pmStr ? pmStr.split(',').collect { it.trim() }.findAll { it }.toSet() : ([] as Set)
}

private List<Integer> getInputComponentIds() {
  List<Integer> inputIds = []
  getInstalledComponents().each { String comp ->
    if (!comp.startsWith('input:')) { return }
    inputIds.add(comp.split(':')[1] as Integer)
  }
  return inputIds
}

private String getConfiguredInputMode(Integer inputId) {
  switch (inputId) {
    case 0: return (settings?.input0Mode ?: 'button').toString()
    case 1: return (settings?.input1Mode ?: 'button').toString()
    case 2: return (settings?.input2Mode ?: 'button').toString()
    default: return 'button'
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Routing (parse)                                        ║
// ╚══════════════════════════════════════════════════════════════╝

void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (msg?.status != null) { return }

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
    if (!dst) {
      logTrace('POST webhook: no dst in body')
      return
    }

    Map params = [:]
    json.each { Object key, Object value ->
      if (value != null) { params[key.toString()] = value.toString() }
    }

    if (dst == 'ble') {
      parent?.handleBleRelay(device, params)
      return
    }

    logDebug("POST webhook dst=${dst}, cid=${params.cid}")
    logTrace("POST webhook params: ${params}")
    routeWebhookNotification(params)
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

private void handleGetWebhook(Map msg) {
  Map params = parseWebhookPath(msg)
  if (!params?.dst) {
    logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}")
    return
  }

  if (params.dst == 'ble') {
    parent?.handleBleRelay(device, params)
    return
  }

  logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
  logTrace("GET webhook params: ${params}")
  routeWebhookNotification(params)
}

private void routeWebhookNotification(Map params) {
  String dst = params.dst as String
  if (!dst || params.cid == null) {
    logTrace("routeWebhookNotification: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  Integer componentId = params.cid as Integer
  String baseType = (dst == 'powermon' && params.comp) ? (params.comp as String) : dstToComponentType(dst)
  List<Map> events = buildWebhookEvents(dst, params)
  if (!events) { return }

  String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
  com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
  if (!child) {
    logDebug("No child device found for DNI: ${childDni}")
    return
  }

  events.each { Map evt ->
    logTrace("Sending webhook event to ${child.displayName}: ${evt}")
    child.sendEvent(evt)
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
  logDebug("Routed ${events.size()} webhook events to ${child.displayName}")
}

@CompileStatic
private static String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('switch_')) { return 'switch' }
  switch (dst) {
    case 'powermon':    return 'switch'
    case 'switchmon':   return 'switch'
    case 'voltmeter':   return 'voltmeter'
    case 'temperature': return 'temperature'
    case 'humidity':    return 'humidity'
    default:            return dst
  }
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
    case 'input_toggle_on':
      events.add([name: 'switch', value: 'on', isStateChange: true, descriptionText: 'Input toggled on'])
      break
    case 'input_toggle_off':
      events.add([name: 'switch', value: 'off', isStateChange: true, descriptionText: 'Input toggled off'])
      break
    case 'input_analog':
      if (params.percent != null) {
        events.add([name: 'analogValue', value: params.percent as BigDecimal, unit: '%',
          descriptionText: "Analog value: ${params.percent}%"])
      }
      break

    case 'voltmeter':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V',
          descriptionText: "Voltage: ${params.voltage}V"])
      }
      break

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

    case 'humidity':
      if (params.rh != null) {
        events.add([name: 'humidity', value: params.rh as BigDecimal, unit: '%',
          descriptionText: "Humidity: ${params.rh}%"])
      }
      break

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
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

void componentOn(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

void componentOff(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

void componentRefresh(com.hubitat.app.DeviceWrapper childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

void componentResetEnergyMonitors(com.hubitat.app.DeviceWrapper childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentResetEnergyMonitors() from switch ${switchId}")
  parent?.parentSendCommand(device, 'Switch.ResetCounters', [id: switchId, type: ['aenergy']])
}

void componentUpdateSwitchSettings(com.hubitat.app.DeviceWrapper childDevice, Map switchSettings) {
  Integer switchId = childDevice.getDataValue('switchId')?.toInteger() ?: 0
  logDebug("componentUpdateSwitchSettings() from switch ${switchId}: ${switchSettings}")
  parent?.parentUpdateSwitchSettings(device, switchId, switchSettings)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes a full Shelly.GetStatus payload to the appropriate children.
 * Unlike the older Plus Uni parent, this also hydrates input switch / analog
 * children from status when the current component data includes those fields.
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  deviceStatus.each { Object keyObj, Object value ->
    if (!(value instanceof Map)) { return }

    String key = keyObj.toString()
    Map data = value as Map
    if (!key.contains(':')) { return }

    String baseType = key.split(':')[0]
    Integer componentId = key.split(':')[1] as Integer
    String childDni = "${device.deviceNetworkId}-${baseType}-${componentId}"
    com.hubitat.app.DeviceWrapper child = getChildDevice(childDni)
    if (!child) { return }

    switch (baseType) {
      case 'switch':
        routeSwitchStatus(child, data)
        break
      case 'input':
        routeInputStatus(child, componentId, data)
        break
      case 'voltmeter':
        routeVoltmeterStatus(child, data)
        break
      case 'temperature':
        routeTemperatureStatus(child, data)
        break
      case 'humidity':
        routeHumidityStatus(child, data)
        break
    }
  }

  sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private void routeSwitchStatus(com.hubitat.app.DeviceWrapper child, Map data) {
  if (data.output != null) {
    child.sendEvent(name: 'switch', value: truthyState(data.output) ? 'on' : 'off')
  }
  if (data.voltage != null) { child.sendEvent(name: 'voltage', value: data.voltage as BigDecimal, unit: 'V') }
  if (data.current != null) { child.sendEvent(name: 'amperage', value: data.current as BigDecimal, unit: 'A') }
  if (data.apower != null) { child.sendEvent(name: 'power', value: data.apower as BigDecimal, unit: 'W') }
  if (data.aenergy?.total != null) {
    child.sendEvent(name: 'energy', value: (data.aenergy.total as BigDecimal) / 1000, unit: 'kWh')
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private void routeInputStatus(com.hubitat.app.DeviceWrapper child, Integer componentId, Map data) {
  String inputMode = getConfiguredInputMode(componentId)
  if (inputMode == 'switch' && data.state != null) {
    child.sendEvent(name: 'switch', value: truthyState(data.state) ? 'on' : 'off')
  } else if (inputMode == 'analog') {
    BigDecimal analogPercent = extractInputPercent(data)
    if (analogPercent != null) {
      child.sendEvent(name: 'analogValue', value: analogPercent, unit: '%')
    }
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private void routeVoltmeterStatus(com.hubitat.app.DeviceWrapper child, Map data) {
  if (data.voltage != null) {
    child.sendEvent(name: 'voltage', value: data.voltage as BigDecimal, unit: 'V')
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private void routeTemperatureStatus(com.hubitat.app.DeviceWrapper child, Map data) {
  String scale = location?.temperatureScale ?: 'F'
  BigDecimal temp = null
  if (scale == 'C' && data.tC != null) {
    temp = data.tC as BigDecimal
  } else if (data.tF != null) {
    temp = data.tF as BigDecimal
  } else if (data.tC != null) {
    temp = ((data.tC as BigDecimal) * 9 / 5) + 32
  }
  if (temp != null) {
    child.sendEvent(name: 'temperature', value: temp, unit: "°${scale}")
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private void routeHumidityStatus(com.hubitat.app.DeviceWrapper child, Map data) {
  Object humidityValue = data.value != null ? data.value : data.rh
  if (humidityValue != null) {
    child.sendEvent(name: 'humidity', value: humidityValue as BigDecimal, unit: '%')
  }
  child.sendEvent(name: 'lastUpdated', value: currentTimestamp())
}

private static BigDecimal extractInputPercent(Map data) {
  Object analogValue = data.percent != null ? data.percent :
      (data.xpercent != null ? data.xpercent : data.value)
  return analogValue != null ? analogValue as BigDecimal : null
}

private static Boolean truthyState(Object value) {
  if (value instanceof Boolean) { return value as Boolean }
  if (value instanceof Number) { return (value as Number).intValue() != 0 }
  String text = value?.toString()?.toLowerCase()
  return ['true', '1', 'on', 'open', 'active'].contains(text)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Helpers                                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses a path-segment webhook URL of the form /dst/cid/key/value/...
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

private static String currentTimestamp() {
  return new Date().format('yyyy-MM-dd HH:mm:ss')
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                              ║
// ╚══════════════════════════════════════════════════════════════╝

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}

void logError(message) { log.error "${device.displayName}: ${message}" }
void logWarn(message) { log.warn "${device.displayName}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${device.displayName}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${device.displayName}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${device.displayName}: ${message}" } }
