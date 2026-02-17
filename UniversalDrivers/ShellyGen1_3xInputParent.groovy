/**
 * Shelly Gen1 3x Input Parent
 *
 * Parent driver for Gen 1 triple-input Shelly devices.
 * Examples: Shelly i3
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates 3 input button children in initialize()
 *   - Parent receives Gen 1 action URL callbacks, routes to children
 *   - Commands: child -> parent -> app
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 3x Input Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
  sendEvent(name: 'numberOfButtons', value: 3)
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
 * Expected: 3 input button children.
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
    if (baseType != 'input') { return }
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

    if (baseType != 'input') { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName = 'Shelly Autoconf Input Button'
    String label = "${device.displayName} Input ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue("${baseType}Id", compId.toString())
      child.sendEvent(name: 'numberOfButtons', value: 1)
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
 * Parses incoming LAN messages from the Gen 1 Shelly i3.
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    Map params = parseWebhookQueryParams(msg)
    if (params?.dst) {
      logDebug("Action URL callback dst=${params.dst}, cid=${params.cid}")
      routeActionUrlCallback(params)
    } else {
      logTrace('No dst found in action URL callback')
    }
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

private Map parseWebhookQueryParams(Map msg) {
  String requestLine = null

  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
  }

  if (!requestLine) { return null }

  String pathAndQuery = requestLine.split(' ')[1]

  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      return [dst: segments[0], cid: segments[1]]
    }
  }

  return null
}

/**
 * Routes Gen 1 action URL callbacks to input children.
 */
private void routeActionUrlCallback(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) { return }

  Integer componentId = params.cid as Integer

  List<Map> events = []
  switch (dst) {
    case 'input_short':
      events.add([name: 'pushed', value: 1, isStateChange: true, descriptionText: 'Button was pushed'])
      break
    case 'input_long':
      events.add([name: 'held', value: 1, isStateChange: true, descriptionText: 'Button was held'])
      break
    case 'input_double':
      events.add([name: 'doubleTapped', value: 1, isStateChange: true, descriptionText: 'Button was double-tapped'])
      break
    default:
      logDebug("routeActionUrlCallback: unhandled dst=${dst}")
      return
  }

  // Route to child input device
  String childDni = "${device.deviceNetworkId}-input-${componentId}"
  def child = getChildDevice(childDni)
  if (child) {
    events.each { Map evt -> child.sendEvent(evt) }
    child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    logDebug("Routed input event to ${child.displayName}")
  }

  // Also fire on parent for aggregation
  sendEvent(name: 'numberOfButtons', value: 3)
  String eventName = (dst == 'input_short') ? 'pushed' :
    (dst == 'input_long') ? 'held' : 'doubleTapped'
  sendEvent(name: eventName, value: componentId + 1, isStateChange: true,
    descriptionText: "Button ${componentId + 1} was ${eventName}")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled Gen 1 status to children.
 * Shelly i3 inputs only have on/off state — no complex data.
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }
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
  else if (messageLevel == 'warn') { return settings.logLevel == 'warn' }
  else if (messageLevel == 'info') { return ['warn', 'info'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['warn', 'info', 'debug'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
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
