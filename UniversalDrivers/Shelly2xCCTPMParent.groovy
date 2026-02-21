/**
 * Shelly Autoconf 2x CCT PM Parent
 *
 * Parent driver for dual-CCT Shelly devices with power monitoring.
 * Examples: Shelly Pro RGBWW PM (CCTX2 profile — two independent CCT channels)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 2 CCT children as driver-level children in initialize()
 *   - Parent receives LAN traffic, parses locally, routes to children
 *   - Parent aggregates switch state (anyOn/allOn) and power values (sum)
 *   - Commands: child -> parent componentCctOn() -> app parentSendCommand() -> Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 2x CCT PM Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ChangeLevel'
    //Commands: startLevelChange(direction), stopLevelChange()

    capability 'ColorTemperature'
    //Attributes: colorTemperature - NUMBER, colorName - STRING
    //Commands: setColorTemperature(colortemperature, level, transitionTime)

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
    capability 'Configuration'
    capability 'Refresh'

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
    options: ['anyOn':'Any CCT On -> Parent On', 'allOn':'All CCTs On -> Parent On'],
    defaultValue: 'anyOn', required: true
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

private void sendPmReportingIntervalToKVS() {
  Integer interval = settings?.pmReportingInterval != null ? settings.pmReportingInterval as Integer : 60
  parent?.componentWriteKvsToDevice(device, 'hubitat_sdm_pm_ri', interval)
}

void refresh() {
  logDebug('refresh() called')
  parent?.parentRefresh(device)
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

void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  Map<String, Integer> componentCounts = [:]
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1
  }

  Integer inputCount = componentCounts['input'] ?: 0

  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType == 'input' && inputCount <= 1) { return }
    if (!['cct', 'input'].contains(baseType)) { return }
    desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}".toString())
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

    if (baseType == 'input' && inputCount <= 1) { return }
    if (!['cct', 'input'].contains(baseType)) { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName
    if (baseType == 'cct') {
      driverName = 'Shelly Autoconf CCT'
    } else if (baseType == 'input') {
      driverName = 'Shelly Autoconf Input Button'
    } else {
      return
    }

    String label = "${device.displayName} ${baseType.toUpperCase()} ${compId}"
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

private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer

  String baseType = (dst == 'powermon' && params.comp) ?
      (params.comp as String) : dstToComponentType(dst)

  List<Map> events = buildWebhookEvents(dst, params)
  if (!events) { return }

  boolean routeToChild = false
  if (baseType == 'cct') {
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

private String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('cct_')) { return 'cct' }
  if (dst.startsWith('switch_')) { return 'switch' }
  switch (dst) {
    case 'cctmon': return 'cct'
    default: return dst
  }
}

private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'cct_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'CCT light turned on'])
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
      }
      if (params.ct != null) {
        Integer ct = params.ct as Integer
        events.add([name: 'colorTemperature', value: ct, unit: 'K'])
        events.add([name: 'colorName', value: colorNameFromTemp(ct)])
      }
      break
    case 'cct_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'CCT light turned off'])
      break
    case 'cct_change':
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
      }
      if (params.ct != null) {
        Integer ct = params.ct as Integer
        events.add([name: 'colorTemperature', value: ct, unit: 'K'])
        events.add([name: 'colorName', value: colorNameFromTemp(ct)])
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

    case 'powermon':
      if (params.voltage != null) { events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V']) }
      if (params.current != null) { events.add([name: 'amperage', value: params.current as BigDecimal, unit: 'A']) }
      if (params.apower != null) { events.add([name: 'power', value: params.apower as BigDecimal, unit: 'W']) }
      if (params.aenergy != null) {
        BigDecimal energyKwh = (params.aenergy as BigDecimal) / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      }
      break

    case 'temperature':
      String scale = getLocationHelper()?.temperatureScale ?: 'F'
      BigDecimal temp = null
      if (scale == 'C' && params.tC != null) { temp = params.tC as BigDecimal }
      else if (params.tF != null) { temp = params.tF as BigDecimal }
      else if (params.tC != null) { temp = (params.tC as BigDecimal) * 9 / 5 + 32 }
      if (temp != null) {
        events.add([name: 'temperature', value: temp, unit: "°${scale}"])
      }
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
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

void componentCctOn(def childDevice) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentCctOn() from CCT ${cctId}")
  parent?.parentSendCommand(device, 'CCT.Set', [id: cctId, on: true])
}

void componentCctOff(def childDevice) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentCctOff() from CCT ${cctId}")
  parent?.parentSendCommand(device, 'CCT.Set', [id: cctId, on: false])
}

void componentCctSetLevel(def childDevice, Integer level, Integer transitionMs = null) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentCctSetLevel(${level}, ${transitionMs}) from CCT ${cctId}")
  Map params = [id: cctId, on: level > 0, brightness: level]
  if (transitionMs != null) { params.transition_duration = (transitionMs / 1000.0) as BigDecimal }
  parent?.parentSendCommand(device, 'CCT.Set', params)
}

void componentSetColorTemperature(def childDevice, Integer colorTemp, Integer level = null, BigDecimal transitionTime = null) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentSetColorTemperature(${colorTemp}, ${level}, ${transitionTime}) from CCT ${cctId}")
  Map params = [id: cctId, on: true, ct: colorTemp]
  if (level != null) { params.brightness = level }
  if (transitionTime != null && transitionTime > 0) { params.transition_duration = transitionTime }
  parent?.parentSendCommand(device, 'CCT.Set', params)
}

void componentCctStartLevelChange(def childDevice, String direction) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentCctStartLevelChange(${direction}) from CCT ${cctId}")
  String dimMethod = direction == 'up' ? 'CCT.DimUp' : 'CCT.DimDown'
  parent?.parentSendCommand(device, dimMethod, [id: cctId])
}

void componentCctStopLevelChange(def childDevice) {
  Integer cctId = childDevice.getDataValue('cctId') as Integer
  logDebug("componentCctStopLevelChange() from CCT ${cctId}")
  parent?.parentSendCommand(device, 'CCT.DimStop', [id: cctId])
}

void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent CCT Commands and Aggregation                          ║
// ╚══════════════════════════════════════════════════════════════╝

void on() {
  logDebug('Parent on() — turning on all CCT channels')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    parent?.parentSendCommand(device, 'CCT.Set', [id: cctId, on: true])
  }
}

void off() {
  logDebug('Parent off() — turning off all CCT channels')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    parent?.parentSendCommand(device, 'CCT.Set', [id: cctId, on: false])
  }
}

void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("Parent setLevel(${level}, ${duration}) — setting all CCT channels")
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    Map params = [id: cctId, on: level > 0, brightness: level as Integer]
    if (duration > 0) { params.transition_duration = duration }
    parent?.parentSendCommand(device, 'CCT.Set', params)
  }
}

void setColorTemperature(BigDecimal colorTemp, BigDecimal level = null, BigDecimal transitionTime = null) {
  logDebug("Parent setColorTemperature(${colorTemp}) — setting all CCT channels")
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    Map params = [id: cctId, on: true, ct: colorTemp as Integer]
    if (level != null) { params.brightness = level as Integer }
    if (transitionTime != null && transitionTime > 0) { params.transition_duration = transitionTime }
    parent?.parentSendCommand(device, 'CCT.Set', params)
  }
}

void startLevelChange(String direction) {
  logDebug("Parent startLevelChange(${direction})")
  String dimMethod = direction == 'up' ? 'CCT.DimUp' : 'CCT.DimDown'
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    parent?.parentSendCommand(device, dimMethod, [id: cctId])
  }
}

void stopLevelChange() {
  logDebug('Parent stopLevelChange()')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    parent?.parentSendCommand(device, 'CCT.DimStop', [id: cctId])
  }
}

void resetEnergyMonitors() {
  logDebug('resetEnergyMonitors() called')
  getChildDevices()?.findAll { it.deviceNetworkId.contains('-cct-') }.each { child ->
    Integer cctId = child.getDataValue('cctId') as Integer
    parent?.parentSendCommand(device, 'CCT.ResetCounters', [id: cctId, type: ['aenergy']])
  }
}

private void processWebhookAggregation(Map params) {
  String dst = params.dst
  if (!dst) { return }

  Boolean cctState = null
  Integer brightness = null
  switch (dst) {
    case 'cct_on':
      cctState = true
      if (params.brightness != null) { brightness = params.brightness as Integer }
      break
    case 'cct_off':
      cctState = false
      break
    case 'cct_change':
      if (params.brightness != null) { brightness = params.brightness as Integer }
      break
  }

  if (cctState != null) {
    String cid = params.cid as String
    Map cctStates = state.cctStates ?: [:]
    cctStates[cid] = cctState
    state.cctStates = cctStates
    updateParentSwitchState()
  }

  if (brightness != null) {
    String cid = params.cid as String
    Map cctLevels = state.cctLevels ?: [:]
    cctLevels[cid] = brightness
    state.cctLevels = cctLevels
    updateParentLevel()
  }
}

private void updateParentSwitchState() {
  Map cctStates = state.cctStates ?: [:]
  if (cctStates.isEmpty()) { return }

  String mode = settings.switchAggregation ?: 'anyOn'
  Boolean parentOn = (mode == 'allOn') ?
    cctStates.values().every { it == true } :
    cctStates.values().any { it == true }

  String newState = parentOn ? 'on' : 'off'
  if (device.currentValue('switch') != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Parent CCT is ${newState}")
  }
}

private void updateParentLevel() {
  Map cctLevels = state.cctLevels ?: [:]
  if (cctLevels.isEmpty()) { return }
  Integer maxLevel = cctLevels.values().collect { it as Integer }.max()
  sendEvent(name: 'level', value: maxLevel, unit: '%')
}

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

  BigDecimal totalPower = 0, totalEnergy = 0, totalCurrent = 0, maxVoltage = 0
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
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent CCT Commands and Aggregation                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.contains(':') || !(v instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Integer componentId = key.split(':')[1] as Integer

    List<Map> events = buildStatusEvents(baseType, v as Map)
    if (!events) { return }

    boolean routeToChild = (baseType == 'cct')
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

private List<Map> buildStatusEvents(String componentType, Map statusData) {
  List<Map> events = []

  if (componentType == 'cct') {
    if (statusData.output != null) {
      String switchState = statusData.output ? 'on' : 'off'
      events.add([name: 'switch', value: switchState])
    }
    if (statusData.brightness != null) {
      events.add([name: 'level', value: statusData.brightness as Integer, unit: '%'])
    }
    if (statusData.ct != null) {
      Integer ct = statusData.ct as Integer
      events.add([name: 'colorTemperature', value: ct, unit: 'K'])
      events.add([name: 'colorName', value: colorNameFromTemp(ct)])
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

private void updateAggregatesFromStatus(Map deviceStatus) {
  Map cctStates = [:]
  Map cctLevels = [:]
  BigDecimal totalPower = 0, totalEnergy = 0, totalCurrent = 0, maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (key.startsWith('cct:') && v instanceof Map) {
      if (v.output != null) { cctStates[key] = v.output }
      if (v.brightness != null) { cctLevels[key] = v.brightness as Integer }
      if (v.apower != null) { totalPower += v.apower as BigDecimal }
      if (v.aenergy?.total != null) { totalEnergy += (v.aenergy.total as BigDecimal) / 1000 }
      if (v.current != null) { totalCurrent += v.current as BigDecimal }
      if (v.voltage != null) {
        BigDecimal volt = v.voltage as BigDecimal
        if (volt > maxVoltage) { maxVoltage = volt }
      }
    }
  }

  state.cctStates = cctStates
  state.cctLevels = cctLevels
  updateParentSwitchState()
  updateParentLevel()

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions                                              ║
// ╚══════════════════════════════════════════════════════════════╝

private Object getLocationHelper() {
  return location
}

/**
 * Returns a human-readable color name from a color temperature in Kelvin.
 *
 * @param kelvin The color temperature in Kelvin
 * @return A color name string
 */
@CompileStatic
private static String colorNameFromTemp(Integer kelvin) {
  if (kelvin <= 2000) return 'Candlelight'
  if (kelvin <= 2700) return 'Warm White'
  if (kelvin <= 3500) return 'Soft White'
  if (kelvin <= 4100) return 'Neutral White'
  if (kelvin <= 5000) return 'Cool White'
  if (kelvin <= 6500) return 'Daylight'
  return 'Overcast'
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Helper Functions                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                              ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() { return "${device.displayName}" }

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
