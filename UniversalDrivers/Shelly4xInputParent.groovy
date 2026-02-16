/**
 * Shelly Autoconf 4x Input Parent
 *
 * Parent driver for Shelly devices with 4 input components.
 * Examples: Shelly Plus I4, Shelly Plus I4 DC
 *
 * Architecture:
 * - Creates 4 input button children (Input 0, Input 1, Input 2, Input 3)
 * - Parses LAN notifications locally and routes events to children
 * - No commands (inputs are event-only)
 * - No parent capabilities (all events go to children)
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 4x Input Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    command 'reinitializeDevice'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}

import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static Boolean NOINPUTCHILDREN = false

// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug('Parent device installed')
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration.
 */
void updated() {
  logDebug('Parent device updated')
  initialize()
}

/**
 * Initializes the parent device driver.
 * - Registers with parent app for management
 * - Reconciles child devices (creates 4 input button children)
 */
void initialize() {
  logDebug('Parent device initialized')
  parent?.componentInitialize(device)
  reconcileChildDevices()
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 */
void configure() {
  logDebug('Parent device configure() called')
  parent?.componentConfigure(device)
}

/**
 * Refreshes the device state by querying the parent app.
 * App will call distributeStatus() with the latest device status.
 */
void refresh() {
  logDebug('Parent device refresh() called')
  parent?.parentRefresh(device)
}

/**
 * Triggers device reinitialization via the parent app.
 * Used when the device needs to be reconfigured (e.g., profile change, webhook reinstall).
 */
void reinitializeDevice() {
  logDebug('reinitializeDevice() called')
  parent?.reinitializeDevice(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                     ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level child devices against the components data value.
 * Creates missing children and removes orphaned children that exist but shouldn't.
 * Children that already exist correctly are left untouched.
 * Creates input button children for each input component found.
 * Called from initialize() and when profile changes.
 */
private void reconcileChildDevices() {
  logDebug('reconcileChildDevices() called')

  String componentStr = device.getDataValue('components') ?: ''
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  // Build component counts
  Map<String, Integer> componentCounts = [:]
  componentStr.split(',').each { String comp ->
    String baseType = comp.contains(':') ? comp.split(':')[0] : comp
    componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1
  }

  Integer inputCount = componentCounts['input'] ?: 0

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  for (int i = 0; i < inputCount; i++) {
    desiredDnis.add("${device.deviceNetworkId}-input-${i}")
  }

  // Build set of DNIs that currently exist
  List<com.hubitat.app.DeviceWrapper> existingChildren = getChildDevicesHelper()
  Set<String> existingDnis = existingChildren.collect { it.deviceNetworkId } as Set

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children (exist but shouldn't)
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      logInfo("Removing orphaned child: ${dni}")
      deleteChildDeviceHelper(dni)
    }
  }

  // Create missing children (should exist but don't)
  for (int i = 0; i < inputCount; i++) {
    String childDni = "${device.deviceNetworkId}-input-${i}"
    if (existingDnis.contains(childDni)) { continue } // already exists, leave it alone

    String label = "${device.displayName} Input ${i}"
    Map childData = [componentType: 'input', inputId: i.toString()]

    try {
      addChildDeviceHelper('ShellyUSA', 'Shelly Autoconf Input Button', childDni,
        [name: label, label: label])
      def child = getChildDeviceHelper(childDni)
      if (child) {
        childData.each { k, v -> childUpdateDataValueHelper(child, k, v) }
        // Note: addChildDevice triggers installed() → initialize() automatically
        logInfo("Created input child: ${label}")
      }
    } catch (Exception e) {
      logError("Failed to create input child ${label}: ${e.message}")
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  LAN Message Parsing and Event Routing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses incoming LAN messages from the Shelly device.
 * Routes POST notifications (script) and GET notifications (webhook) to children.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  logTrace('Parent parse() received message')

  try {
    Map msg = parseLanMessage(description)

    // Skip HTTP responses (only process incoming requests)
    if (msg?.status != null) { return }

    // Try POST body first (script notifications with dst field)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        if (json?.dst && json?.result) {
          routePostNotification(json.dst as String, json)
          return
        }
      } catch (Exception e) {
        // Body might be empty or not JSON — fall through to GET parsing
      }
    }

    // Try GET query parameters (webhook notifications)
    Map params = parseWebhookQueryParams(msg)
    if (params?.dst && params?.comp) {
      routeWebhookParams(params)
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Parses query parameters from an incoming GET webhook request.
 *
 * @param msg The parsed LAN message map
 * @return Map of query parameter key-value pairs, or null if not parseable
 */
@CompileStatic
private Map parseWebhookQueryParams(Map msg) {
  if (!msg?.headers) { return null }

  String requestLine = msg.headers?.keySet()?.find { key ->
    key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
  }

  if (!requestLine) { return null }

  String pathAndQuery = requestLine.toString().split(' ')[1]
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx < 0) { return null }

  Map params = [:]
  pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
    String[] kv = pair.split('=', 2)
    if (kv.length == 2) {
      params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
    }
  }
  return params
}

/**
 * Routes POST script notifications to appropriate children based on component ID.
 *
 * @param dst Destination type (input_push, input_double, input_long)
 * @param json Full JSON payload from script notification
 */
private void routePostNotification(String dst, Map json) {
  logDebug("routePostNotification: dst=${dst}")

  Map result = json?.result
  if (!result) {
    logWarn('routePostNotification: No result data in JSON')
    return
  }

  // Route input events to children
  if (dst.startsWith('input_')) {
    result.each { key, value ->
      if (key.toString().startsWith('input:')) {
        String comp = key.toString() // e.g., "input:0"
        String baseType = comp.split(':')[0]
        Integer compId = (comp.split(':')[1] as Integer)

        String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
        def child = getChildDeviceHelper(childDni)

        if (child) {
          List<Map> events = buildComponentEvents(dst, baseType, value as Map)
          events.each { evt ->
            childSendEventHelper(child, evt)
            logDebug("Sent event to ${child.displayName}: ${evt}")
          }
        } else {
          logWarn("No child found for ${comp}")
        }
      }
    }
  }
}

/**
 * Routes webhook GET query parameters to appropriate children.
 *
 * @param params The parsed query parameters including comp (e.g., "input:0")
 */
private void routeWebhookParams(Map params) {
  String comp = params.comp as String
  String dst = params.dst as String
  logDebug("routeWebhookParams: comp=${comp}, dst=${dst}")

  if (!comp || !dst) { return }

  String baseType = comp.split(':')[0]
  Integer compId = (comp.split(':')[1] as Integer)

  String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
  def child = getChildDeviceHelper(childDni)

  if (child) {
    List<Map> events = buildWebhookEvents(dst, params)
    events.each { evt ->
      childSendEventHelper(child, evt)
      logDebug("Sent webhook event to ${child.displayName}: ${evt}")
    }
  } else {
    logWarn("No child found for ${comp}")
  }
}

/**
 * Distributes status from Shelly.GetStatus query to children.
 * Called by parent app after refresh() or during periodic status updates.
 *
 * @param status Map of component statuses from Shelly.GetStatus
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")

  // Input components don't have status - they're event-only
  // Nothing to distribute for input-only devices
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END LAN Message Parsing and Event Routing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Building                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Builds events from POST notification data.
 *
 * @param dst Destination type (input_push, input_double, input_long)
 * @param baseType Component base type (input)
 * @param data Component data map
 * @return List of event maps to send to child
 */
@CompileStatic
private List<Map> buildComponentEvents(String dst, String baseType, Map data) {
  List<Map> events = []

  switch (dst) {
    case 'input_push':
      events.add([name: 'pushed', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was pushed'])
      break
    case 'input_double':
      events.add([name: 'doubleTapped', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was double-tapped'])
      break
    case 'input_long':
      events.add([name: 'held', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was held'])
      break
  }

  // Battery level (for battery-powered input devices)
  if (data?.battPct != null) {
    events.add([name: 'battery', value: data.battPct as Integer, unit: '%'])
  }

  return events
}

/**
 * Builds events from webhook GET parameters.
 *
 * @param dst Destination type (input_push, input_double, input_long)
 * @param params Query parameters from webhook
 * @return List of event maps to send to child
 */
@CompileStatic
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'input_push':
      events.add([name: 'pushed', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was pushed'])
      break
    case 'input_double':
      events.add([name: 'doubleTapped', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was double-tapped'])
      break
    case 'input_long':
      events.add([name: 'held', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was held'])
      break
  }

  // Battery level (for battery-powered input devices)
  if (params?.battPct != null) {
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

/**
 * Handles refresh() command from child input.
 * Delegates to parent app's parentRefresh handler.
 *
 * @param childDevice The child device requesting refresh
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() called by ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Component Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions (Non-Static Wrappers)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Non-static helper to get child devices.
 * Required because getChildDevices() is a dynamic Hubitat method.
 *
 * @return List of child device wrappers
 */
private List<com.hubitat.app.DeviceWrapper> getChildDevicesHelper() {
  return getChildDevices()
}

/**
 * Non-static helper to get a single child device.
 * Required because getChildDevice() is a dynamic Hubitat method.
 *
 * @param dni Device network ID
 * @return Child device wrapper or null
 */
private Object getChildDeviceHelper(String dni) {
  return getChildDevice(dni)
}

/**
 * Non-static helper to add a child device.
 * Required because addChildDevice() is a dynamic Hubitat method.
 *
 * @param namespace Driver namespace
 * @param typeName Driver type name
 * @param dni Device network ID
 * @param properties Device properties map
 */
private void addChildDeviceHelper(String namespace, String typeName, String dni, Map properties) {
  addChildDevice(namespace, typeName, dni, properties)
}

/**
 * Non-static helper to delete a child device.
 * Required because deleteChildDevice() is a dynamic Hubitat method.
 *
 * @param dni Device network ID of child to delete
 */
private void deleteChildDeviceHelper(String dni) {
  deleteChildDevice(dni)
}

/**
 * Non-static helper to update a child device's data value.
 * Required because updateDataValue() is a dynamic method.
 *
 * @param child The child device
 * @param key Data value key
 * @param value Data value
 */
private void childUpdateDataValueHelper(def child, String key, String value) {
  child.updateDataValue(key, value)
}

/**
 * Non-static helper to initialize a child device.
 * Required because initialize() is a dynamic method.
 *
 * @param child The child device
 */
private void childInitializeHelper(def child) {
  child.initialize()
}

/**
 * Non-static helper to send event to child device.
 * Required because sendEvent() is a dynamic method.
 *
 * @param child The child device
 * @param evt Event map
 */
private void childSendEventHelper(def child, Map evt) {
  child.sendEvent(evt)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Helper Functions                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
@CompileStatic
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
@CompileStatic
private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') {
    return true
  } else if (messageLevel == 'warn') {
    return settings.logLevel == 'warn'
  } else if (messageLevel == 'info') {
    return ['warn', 'info'].contains(settings.logLevel)
  } else if (messageLevel == 'debug') {
    return ['warn', 'info', 'debug'].contains(settings.logLevel)
  } else if (messageLevel == 'trace') {
    return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel)
  }
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${obj?.getClass()?.name}")
}

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    String prettyJson = prettyJson(message)
    logTrace(prettyJson)
  }
}

@CompileStatic
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

@CompileStatic
void logInfoJson(Map message) {
  String prettyJson = prettyJson(message)
  logInfo(prettyJson)
}

/**
 * Formats a Map as pretty-printed JSON.
 *
 * @param jsonInput The map to format
 * @return Pretty-printed JSON string
 */
@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝
