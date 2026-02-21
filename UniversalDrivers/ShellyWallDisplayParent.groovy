/**
 * Shelly Autoconf Wall Display Parent
 *
 * Parent driver for the Shelly Wall Display device.
 * Combines a relay switch with environmental sensors (temperature, humidity, illuminance)
 * and input button capabilities.
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent handles switch control and sensor data directly
 *   - Input children created only if multiple inputs present
 *   - Sensor data arrives via webhooks, switch commands via Shelly RPC
 *
 * Version: 1.0.0
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
  input name: 'tempOffset', type: 'decimal', title: 'Temperature Offset', defaultValue: 0, range: '-10..10'
  input name: 'humidityOffset', type: 'decimal', title: 'Humidity Offset', defaultValue: 0, range: '-25..25'
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
}

void configure() {
  logDebug('configure() called')
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Lifecycle                                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level child devices against the components data value.
 * Only creates input children when more than 1 input is present.
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }
  Integer inputCount = components.findAll { it.startsWith('input:') }.size()

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
        def child = addChildDevice('ShellyDeviceManager', 'Shelly Autoconf Input Button', childDni, [name: label, label: label])
        child.updateDataValue('componentType', 'input')
        child.updateDataValue('inputId', compId.toString())
        child.sendEvent(name: 'numberOfButtons', value: 1)
        logInfo("Created child: ${label} (Shelly Autoconf Input Button)")
      } catch (Exception e) {
        logError("Failed to create child ${label}: ${e.message}")
      }
    }
  }

  if (inputCount == 1) {
    sendEvent(name: 'numberOfButtons', value: 1)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Finds the switch component from the device's components data value.
 *
 * @return Map with compId (Integer), or null if not found
 */
private Map getSwitchComponent() {
  String componentStr = device.getDataValue('components') ?: ''
  String switchComp = componentStr.split(',').find { it.startsWith('switch:') }
  if (!switchComp) { return null }
  Integer compId = switchComp.split(':')[1] as Integer
  return [compId: compId]
}

void on() {
  logDebug('on() called')
  Map comp = getSwitchComponent()
  if (!comp) { logWarn('No switch component found'); return }
  parent?.parentSendCommand(device, 'Switch.Set', [id: comp.compId, on: true])
}

void off() {
  logDebug('off() called')
  Map comp = getSwitchComponent()
  if (!comp) { logWarn('No switch component found'); return }
  parent?.parentSendCommand(device, 'Switch.Set', [id: comp.compId, on: false])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

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
      BigDecimal temp = null
      if (scale == 'C' && params.tC != null) {
        temp = params.tC as BigDecimal
      } else if (params.tF != null) {
        temp = params.tF as BigDecimal
      } else if (params.tC != null) {
        temp = (params.tC as BigDecimal) * 9 / 5 + 32
      }
      if (temp != null) {
        BigDecimal offset = settings?.tempOffset != null ? settings.tempOffset as BigDecimal : 0
        temp = temp + offset
        events.add([name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}"])
      }
      break

    case 'humidity':
      if (params.rh != null) {
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
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.contains(':') || !(v instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Map statusData = v as Map

    if (baseType == 'switch') {
      if (statusData.output != null) {
        String switchState = statusData.output ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState)
      }
    } else if (baseType == 'temperature') {
      BigDecimal tempC = statusData.tC != null ? statusData.tC as BigDecimal : null
      BigDecimal tempF = statusData.tF != null ? statusData.tF as BigDecimal : null
      if (tempC != null || tempF != null) {
        String scale = getLocationHelper()?.temperatureScale ?: 'F'
        BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC * 9 / 5 + 32)
        BigDecimal offset = settings?.tempOffset != null ? settings.tempOffset as BigDecimal : 0
        temp = temp + offset
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}")
      }
    } else if (baseType == 'humidity') {
      if (statusData.rh != null) {
        BigDecimal humidity = statusData.rh as BigDecimal
        BigDecimal offset = settings?.humidityOffset != null ? settings.humidityOffset as BigDecimal : 0
        humidity = humidity + offset
        sendEvent(name: 'humidity', value: humidity, unit: '%')
      }
    } else if (baseType == 'illuminance') {
      if (statusData.lux != null) {
        sendEvent(name: 'illuminance', value: statusData.lux as Integer, unit: 'lux')
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
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
