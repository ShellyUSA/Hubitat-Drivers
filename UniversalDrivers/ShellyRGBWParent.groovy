/**
 * Shelly Autoconf RGBW Parent
 *
 * Parent driver for RGBW/RGB Shelly devices.
 * Examples: Shelly Plus RGBW PM (rgb/rgbw profiles)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates input children in initialize() (inputs only; RGB/RGBW component
 *     is controlled directly on the parent via ColorControl + SwitchLevel)
 *   - Parent receives LAN traffic, parses locally, routes input events to children
 *   - Commands: parent on()/off()/setColor() -> app parentSendCommand() -> Shelly RPC
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single RGBW PM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ChangeLevel'
    //Commands: startLevelChange(direction), stopLevelChange()

    capability 'ColorControl'
    //Attributes: color - STRING, hue - NUMBER, saturation - NUMBER, RGB - STRING
    //Commands: setColor(colorMap), setHue(hue), setSaturation(saturation)

    capability 'ColorMode'
    //Attributes: colorMode - ENUM ["CT", "RGB"]

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'VoltageMeasurement'
    //Attributes: voltage - NUMBER, unit:V

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
 * Initializes the parent device: creates/reconciles input children.
 */
void initialize() {
  logDebug('Parent device initialized')
  reconcileChildDevices()
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
 * Only creates input children (2+ inputs). RGB/RGBW component is on the parent.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Count input components
  Integer inputCount = components.findAll { it.startsWith('input:') }.size()

  // Build set of DNIs that SHOULD exist (only inputs when > 1)
  Set<String> desiredDnis = [] as Set
  if (inputCount > 1) {
    components.each { String comp ->
      if (!comp.contains(':')) { return }
      String baseType = comp.split(':')[0]
      Integer compId = comp.split(':')[1] as Integer
      if (baseType == 'input') {
        desiredDnis.add("${device.deviceNetworkId}-input-${compId}".toString())
      }
    }
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

  // Create missing input children
  if (inputCount > 1) {
    components.each { String comp ->
      if (!comp.contains(':')) { return }
      String baseType = comp.split(':')[0]
      Integer compId = comp.split(':')[1] as Integer
      if (baseType != 'input') { return }

      String childDni = "${device.deviceNetworkId}-input-${compId}"
      if (getChildDevice(childDni)) { return }

      String label = "${device.displayName} Input ${compId}"
      try {
        def child = addChildDevice('ShellyUSA', 'Shelly Autoconf Input Button', childDni, [name: label, label: label])
        child.updateDataValue('componentType', 'input')
        child.updateDataValue('inputId', compId.toString())
        child.sendEvent(name: 'numberOfButtons', value: 1)
        logInfo("Created child: ${label} (Shelly Autoconf Input Button)")
      } catch (Exception e) {
        logError("Failed to create child ${label}: ${e.message}")
      }
    }
  }

  // Set button count on parent for single input
  if (inputCount == 1) {
    sendEvent(name: 'numberOfButtons', value: 1)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  RGBW Commands                                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Looks up the RGB/RGBW component from the device's components data value.
 *
 * @return Map with baseType ('rgb' or 'rgbw'), compId (Integer), and rpcMethod (String), or null if not found
 */
private Map getRgbComponent() {
  String componentStr = device.getDataValue('components') ?: ''
  String rgbComp = componentStr.split(',').find { it.startsWith('rgb:') || it.startsWith('rgbw:') }
  if (!rgbComp) { return null }
  String baseType = rgbComp.split(':')[0]
  Integer compId = rgbComp.split(':')[1] as Integer
  String rpcMethod = baseType == 'rgbw' ? 'RGBW.Set' : 'RGB.Set'
  return [baseType: baseType, compId: compId, rpcMethod: rpcMethod]
}

/**
 * Turns on the RGBW light via Shelly RPC.
 */
void on() {
  logDebug('on() called')
  Map comp = getRgbComponent()
  if (!comp) { logWarn('No rgb/rgbw component found'); return }
  parent?.parentSendCommand(device, comp.rpcMethod as String, [id: comp.compId, on: true])
}

/**
 * Turns off the RGBW light via Shelly RPC.
 */
void off() {
  logDebug('off() called')
  Map comp = getRgbComponent()
  if (!comp) { logWarn('No rgb/rgbw component found'); return }
  parent?.parentSendCommand(device, comp.rpcMethod as String, [id: comp.compId, on: false])
}

/**
 * Sets brightness level on the RGBW light.
 *
 * @param level Target brightness (0-100)
 * @param duration Optional transition time in seconds
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  Map comp = getRgbComponent()
  if (!comp) { logWarn('No rgb/rgbw component found'); return }
  Map params = [id: comp.compId, on: level > 0, brightness: level as Integer]
  if (duration > 0) { params.transition_duration = duration }
  parent?.parentSendCommand(device, comp.rpcMethod as String, params)
}

/**
 * Starts a gradual level change.
 *
 * @param direction Either "up" or "down"
 */
void startLevelChange(String direction) {
  logDebug("startLevelChange(${direction}) called")
  Integer currentLevel = device.currentValue('level') as Integer ?: 50
  Integer targetLevel = (direction == 'up') ? 100 : 0
  setLevel(targetLevel as BigDecimal, 5)
}

/**
 * Stops a gradual level change.
 */
void stopLevelChange() {
  logDebug('stopLevelChange() called')
  // Stop by setting current brightness (no transition)
  Integer currentLevel = device.currentValue('level') as Integer ?: 50
  setLevel(currentLevel as BigDecimal)
}

/**
 * Sets the color of the RGBW light.
 *
 * @param colorMap Map with hue (0-100), saturation (0-100), level (0-100)
 */
void setColor(Map colorMap) {
  logDebug("setColor(${colorMap}) called")
  Map comp = getRgbComponent()
  if (!comp) { logWarn('No rgb/rgbw component found'); return }

  // Convert HSV to RGB
  Integer hue = colorMap.hue as Integer ?: 0
  Integer saturation = colorMap.saturation as Integer ?: 100
  Integer level = colorMap.level as Integer ?: 100

  List<Integer> rgb = hsvToRgb(hue, saturation, level)
  Map params = [id: comp.compId, on: true, red: rgb[0], green: rgb[1], blue: rgb[2]]
  if (comp.baseType == 'rgbw' && colorMap.white != null) {
    params.white = colorMap.white as Integer
  }
  parent?.parentSendCommand(device, comp.rpcMethod as String, params)

  sendEvent(name: 'hue', value: hue)
  sendEvent(name: 'saturation', value: saturation)
  sendEvent(name: 'level', value: level, unit: '%')
  sendEvent(name: 'colorMode', value: 'RGB')
  String hexColor = String.format('#%02x%02x%02x', rgb[0], rgb[1], rgb[2])
  sendEvent(name: 'color', value: "{\"hue\":${hue},\"saturation\":${saturation},\"level\":${level}}")
  sendEvent(name: 'RGB', value: hexColor)
}

/**
 * Sets the hue of the RGBW light.
 *
 * @param hue Target hue (0-100)
 */
void setHue(BigDecimal hue) {
  setColor([hue: hue, saturation: device.currentValue('saturation') ?: 100, level: device.currentValue('level') ?: 100])
}

/**
 * Sets the saturation of the RGBW light.
 *
 * @param saturation Target saturation (0-100)
 */
void setSaturation(BigDecimal saturation) {
  setColor([hue: device.currentValue('hue') ?: 0, saturation: saturation, level: device.currentValue('level') ?: 100])
}

/**
 * Converts HSV (Hubitat 0-100 range) to RGB (0-255 range).
 *
 * @param hue Hue (0-100)
 * @param saturation Saturation (0-100)
 * @param value Brightness/level (0-100)
 * @return List of [red, green, blue] values (0-255)
 */
@CompileStatic
private static List<Integer> hsvToRgb(Integer hue, Integer saturation, Integer value) {
  float h = (hue / 100.0f) * 360.0f
  float s = saturation / 100.0f
  float v = value / 100.0f

  float c = v * s
  float x = c * (1.0f - Math.abs((h / 60.0f) % 2.0f - 1.0f))
  float m = v - c

  float r, g, b
  if (h < 60)       { r = c; g = x; b = 0 }
  else if (h < 120) { r = x; g = c; b = 0 }
  else if (h < 180) { r = 0; g = c; b = x }
  else if (h < 240) { r = 0; g = x; b = c }
  else if (h < 300) { r = x; g = 0; b = c }
  else              { r = c; g = 0; b = x }

  return [(int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255)]
}

/**
 * Resets energy monitoring counters.
 */
void resetEnergyMonitors() {
  logDebug('resetEnergyMonitors() called')
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END RGBW Commands                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Handles refresh() command from child.
 *
 * @param childDevice The child device requesting refresh
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() from ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Routing (parse)                                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Receives LAN messages, parses locally, routes to children or updates parent.
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
  } else {
    logDebug("GET webhook: no dst found")
  }
}

/**
 * Routes webhook notification to parent or children.
 *
 * @param params The parsed webhook parameters
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  List<Map> events = buildWebhookEvents(dst, params)
  if (!events) { return }

  // Input events -> route to children if > 1 input
  if (dst.startsWith('input_')) {
    String componentStr = device.getDataValue('components') ?: ''
    Integer inputCount = componentStr.split(',').findAll { it.startsWith('input:') }.size()
    if (inputCount > 1) {
      Integer componentId = params.cid as Integer
      String childDni = "${device.deviceNetworkId}-input-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt -> child.sendEvent(evt) }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
      return
    }
  }

  // Everything else -> parent
  events.each { Map evt -> sendEvent(evt) }
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Builds events from webhook parameters.
 *
 * @param dst The webhook destination type
 * @param params The webhook parameters map
 * @return List of event maps
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'rgb_on':
    case 'rgbw_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Light turned on'])
      if (params.brightness != null) {
        events.add([name: 'level', value: params.brightness as Integer, unit: '%'])
      }
      break
    case 'rgb_off':
    case 'rgbw_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Light turned off'])
      break
    case 'rgb_change':
    case 'rgbw_change':
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
      break
  }

  return events
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
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes Shelly.GetStatus response to parent attributes.
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
    Map statusData = v as Map

    if (baseType == 'rgb' || baseType == 'rgbw') {
      if (statusData.output != null) {
        String switchState = statusData.output ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState)
      }
      if (statusData.brightness != null) {
        sendEvent(name: 'level', value: statusData.brightness as Integer, unit: '%')
      }
      if (statusData.voltage != null) {
        sendEvent(name: 'voltage', value: statusData.voltage as BigDecimal, unit: 'V')
      }
      if (statusData.current != null) {
        sendEvent(name: 'amperage', value: statusData.current as BigDecimal, unit: 'A')
      }
      if (statusData.apower != null) {
        sendEvent(name: 'power', value: statusData.apower as BigDecimal, unit: 'W')
      }
      if (statusData.aenergy?.total != null) {
        BigDecimal energyKwh = (statusData.aenergy.total as BigDecimal) / 1000
        sendEvent(name: 'energy', value: energyKwh, unit: 'kWh')
      }
    }
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
