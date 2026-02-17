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
    desiredDnis.add("${device.deviceNetworkId}-input-${i}".toString())
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
 * Routes script notifications and webhook notifications to children.
 *
 * @param description Raw LAN message description string from Hubitat
 */
/**
 * Parses incoming LAN messages from the Shelly device.
 * POST requests (from Shelly scripts) carry data in the JSON body.
 * GET requests (from Shelly Action Webhooks) carry state in the URL path.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    if (msg?.body) {
      handlePostWebhook(msg)
    } else {
      handleGetWebhook(msg)
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Handles POST webhook notifications from Shelly scripts.
 * Parses JSON body and routes to webhook params handler.
 *
 * @param msg The parsed LAN message map containing a JSON body
 */
private void handlePostWebhook(Map msg) {
  try {
    Map json = new groovy.json.JsonSlurper().parseText(msg.body) as Map
    String dst = json?.dst?.toString()
    if (!dst) { logTrace('POST webhook: no dst in body'); return }

    // BLE relay: forward to app for BLE device processing
    if (dst == 'ble') {
      logDebug('BLE relay received, forwarding to app')
      parent?.handleBleRelay(device, json)
      return
    }

    Map params = [:]
    json.each { k, v -> if (v != null) { params[k.toString()] = v.toString() } }

    logDebug("POST webhook dst=${dst}, cid=${params.cid}")
    logTrace("POST webhook params: ${params}")
    routeWebhookParams(params)
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
    routeWebhookParams(params)
  } else {
    logDebug("GET webhook: no dst found — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
  }
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /input_toggle_on/0).
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
 * Routes script notifications to appropriate children based on component ID.
 *
 * @param dst Destination type (input_push, input_double, input_long)
 * @param json Full JSON payload from script notification
 */
private void routeScriptNotification(String dst, Map json) {
  logDebug("routeScriptNotification: dst=${dst}")

  Map result = json?.result
  if (!result) {
    logWarn('routeScriptNotification: No result data in JSON')
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
  String dst = params.dst as String
  if (!dst || params.cid == null) {
    logTrace("routeWebhookParams: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  // Fallback: forward BLE data to app if handlePostWebhook intercept was missed
  if (dst == 'ble') {
    logDebug('BLE relay received via routeWebhookParams, forwarding to app')
    parent?.handleBleRelay(device, params)
    return
  }

  Integer compId = params.cid as Integer
  String baseType = dstToComponentType(dst)
  logDebug("routeWebhookParams: dst=${dst}, baseType=${baseType}, cid=${compId}")

  String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
  def child = getChildDeviceHelper(childDni)

  if (child) {
    List<Map> events = buildWebhookEvents(dst, params)
    events.each { evt ->
      childSendEventHelper(child, evt)
      logDebug("Sent webhook event to ${child.displayName}: ${evt}")
    }
  } else {
    logWarn("No child found for input:${compId}")
  }
}

/**
 * Maps a webhook dst parameter to its Shelly component type.
 * Supports both prefix-based matching (e.g., input_push, switch_on)
 * and legacy dst values (switchmon, covermon).
 *
 * @param dst The webhook destination string
 * @return The Shelly component type (input, switch, cover, smoke, or the raw dst)
 */
private String dstToComponentType(String dst) {
  if (dst.startsWith('input_')) { return 'input' }
  if (dst.startsWith('switch_')) { return 'switch' }
  if (dst.startsWith('cover_')) { return 'cover' }
  if (dst.startsWith('smoke_')) { return 'smoke' }
  switch (dst) {
    case 'switchmon': return 'switch'  // legacy
    case 'covermon': return 'cover'    // legacy
    default: return dst
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
 * Builds events from script notification data.
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
 * @param dst Destination type (input_push, input_double, input_long, input_toggle_on, input_toggle_off, input_toggle)
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
    case 'input_toggle_on':
      events.add([name: 'switch', value: 'on', isStateChange: true,
        descriptionText: 'Input toggled on'])
      break
    case 'input_toggle_off':
      events.add([name: 'switch', value: 'off', isStateChange: true,
        descriptionText: 'Input toggled off'])
      break
    case 'input_toggle':
      // Legacy: determine state from params
      String toggleState = params?.state as String
      if (toggleState) {
        events.add([name: 'switch', value: toggleState, isStateChange: true,
          descriptionText: "Input toggled ${toggleState}"])
      }
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
