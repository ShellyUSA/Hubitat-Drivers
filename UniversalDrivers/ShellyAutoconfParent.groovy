/**
 * Shelly Autoconf Parent (Fallback)
 *
 * Minimal fallback parent driver for unknown or unsupported Shelly device patterns.
 * Used when device component configuration doesn't match any specific parent driver.
 *
 * Architecture:
 * - NO capabilities on parent (avoids claiming capabilities device doesn't have)
 * - Creates driver-level children based on components data value
 * - Parses LAN notifications locally and routes events to children
 * - All device capabilities exposed through children only
 *
 * This driver should ONLY be used as a fallback when:
 * - Device has an unusual/unknown component configuration
 * - No specific parent driver matches the device pattern
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
 * - Reconciles child devices based on components data value
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
 * Creates driver-level children for all components found in components data value.
 * Called from initialize() and when profile changes.
 */
private void reconcileChildDevices() {
  logDebug('reconcileChildDevices() called')

  String componentStr = device.getDataValue('components') ?: ''
  String pmStr = device.getDataValue('pmComponents') ?: ''

  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  Set<String> pmComponents = pmStr ? pmStr.split(',').toSet() : [] as Set
  List<String> components = componentStr.split(',').toList()

  // Build set of DNIs that SHOULD exist
  // EM (3-phase) expands to 3 children per component (one per phase a/b/c)
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    String baseType = comp.contains(':') ? comp.split(':')[0] : comp
    Integer compId = comp.contains(':') ? (comp.split(':')[1] as Integer) : 0
    if (baseType == 'em') {
      // 3 children per EM component: em-0, em-1, em-2 (for em:0)
      for (int i = 0; i < 3; i++) {
        desiredDnis.add("${device.deviceNetworkId}-em-${compId * 3 + i}".toString())
      }
    } else {
      desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}".toString())
    }
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
  List<String> phaseLabels = ['A', 'B', 'C']
  components.each { String comp ->
    String baseType = comp.contains(':') ? comp.split(':')[0] : comp
    Integer compId = comp.contains(':') ? (comp.split(':')[1] as Integer) : 0

    // EM (3-phase) creates 3 children per component
    if (baseType == 'em') {
      for (int phaseIdx = 0; phaseIdx < 3; phaseIdx++) {
        Integer childId = compId * 3 + phaseIdx
        String childDni = "${device.deviceNetworkId}-em-${childId}"
        if (existingDnis.contains(childDni)) { continue }

        String label = "${device.displayName} EM${compId} Phase ${phaseLabels[phaseIdx]}"
        Map childData = [componentType: 'em', emId: compId.toString(), phase: phaseLabels[phaseIdx].toLowerCase()]
        try {
          addChildDeviceHelper('ShellyUSA', 'Shelly Autoconf EM', childDni,
            [name: label, label: label])
          def child = getChildDeviceHelper(childDni)
          if (child) {
            childData.each { k, v -> childUpdateDataValueHelper(child, k, v) }
            logInfo("Created child: ${label}")
          }
        } catch (Exception e) {
          logError("Failed to create child ${label}: ${e.message}")
        }
      }
      return // skip the standard single-child path
    }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (existingDnis.contains(childDni)) { return } // already exists, leave it alone

    // Determine driver name
    String driverName = null
    Map childData = [componentType: baseType]

    switch (baseType) {
      case 'switch':
        Boolean hasPM = pmComponents.contains(comp)
        driverName = hasPM ? 'Shelly Autoconf Switch PM' : 'Shelly Autoconf Switch'
        childData.switchId = compId.toString()
        break

      case 'cover':
        Boolean hasPM = pmComponents.contains(comp)
        driverName = hasPM ? 'Shelly Autoconf Cover PM' : 'Shelly Autoconf Cover'
        childData.coverId = compId.toString()
        break

      case 'light':
        driverName = 'Shelly Autoconf Light'
        childData.lightId = compId.toString()
        break

      case 'input':
        driverName = 'Shelly Autoconf Input Button'
        childData.inputId = compId.toString()
        break

      case 'em1':
        driverName = 'Shelly Autoconf EM'
        childData.em1Id = compId.toString()
        break

      default:
        logWarn("Unknown component type: ${baseType}")
    }

    if (driverName) {
      String label = "${device.displayName} ${baseType.capitalize()} ${compId}"

      try {
        addChildDeviceHelper('ShellyUSA', driverName, childDni,
          [name: label, label: label])
        def child = getChildDeviceHelper(childDni)
        if (child) {
          childData.each { k, v -> childUpdateDataValueHelper(child, k, v) }
          // Note: addChildDevice triggers installed() → initialize() automatically
          logInfo("Created child: ${label}")
        }
      } catch (Exception e) {
        logError("Failed to create child ${label}: ${e.message}")
      }
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
    // Forward to parent app for structured trace logging (gate check ensures minimal overhead)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    // Skip HTTP responses (only process incoming requests)
    if (msg?.status != null) {
      logTrace("parse() skipping HTTP response (status=${msg.status})")
      return
    }

    // Try POST body first (script notifications with dst field)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        if (json?.dst && json?.result) {
          logDebug("POST notification dst=${json.dst}")
          logTrace("POST result: ${json.result}")
          routePostNotification(json.dst as String, json)
          return
        }
        logTrace("POST body parsed but no dst/result")
      } catch (Exception e) {
        // Body might be empty or not JSON — fall through to GET parsing
      }
    }

    // Try GET query parameters (webhook notifications)
    Map params = parseWebhookQueryParams(msg)
    if (params?.dst && params?.comp) {
      logDebug("GET webhook dst=${params.dst}, cid=${params.cid}")
      logTrace("Webhook params: ${params}")
      routeWebhookParams(params)
    } else {
      logTrace("parse() no dst/comp found in message, unable to route")
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
  String requestLine = null

  // Try parsed headers map first
  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
    logTrace("parseWebhookQueryParams: headers map search result: ${requestLine ? 'found' : 'not found'}")
  }

  // Fallback: parse raw header string for request line
  if (!requestLine && msg?.header) {
    String rawHeader = msg.header.toString()
    logTrace("parseWebhookQueryParams: trying raw header fallback")
    String[] lines = rawHeader.split('\n')
    for (String line : lines) {
      String trimmed = line.trim()
      if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
        requestLine = trimmed
        logTrace("parseWebhookQueryParams: found request line in raw header: ${requestLine}")
        break
      }
    }
  }

  if (!requestLine) {
    logTrace('parseWebhookQueryParams: no request line found in headers or raw header')
    return null
  }

  // Extract path from request line: "GET /webhook/switchmon/0 HTTP/1.1" -> "/webhook/switchmon/0"
  String pathAndQuery = requestLine.split(' ')[1]

  // Parse path segments: /webhook/<dst>/<cid>[?key=val&...]
  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    String queryString = null
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) {
      queryString = webhookPath.substring(qMarkIdx + 1)
      webhookPath = webhookPath.substring(0, qMarkIdx)
    }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      Map params = [dst: segments[0], cid: segments[1]]
      if (queryString) {
        queryString.split('&').each { String pair ->
          String[] kv = pair.split('=', 2)
          if (kv.length == 2) {
            params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
          }
        }
      }
      logTrace("parseWebhookQueryParams: parsed path params: ${params}")
      return params
    }
    logTrace("parseWebhookQueryParams: not enough path segments in '${pathAndQuery}'")
    return null
  }

  // Fallback: try query string parsing for backwards compatibility
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx >= 0) {
    Map params = [:]
    pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
      String[] kv = pair.split('=', 2)
      if (kv.length == 2) {
        params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
      }
    }
    logTrace("parseWebhookQueryParams: parsed query params: ${params}")
    return params
  }

  logTrace("parseWebhookQueryParams: no webhook path or query string in '${pathAndQuery}'")
  return null
}

/**
 * Routes POST script notifications to appropriate children based on component ID.
 *
 * @param dst Destination type (switchmon, powermon, covermon, input_push, etc.)
 * @param json Full JSON payload from script notification
 */
private void routePostNotification(String dst, Map json) {
  logDebug("routePostNotification: dst=${dst}")

  Map result = json?.result
  if (!result) {
    logWarn('routePostNotification: No result data in JSON')
    return
  }

  // Iterate over all result entries and route to appropriate children
  result.each { key, value ->
    if (key.toString().contains(':')) {
      String comp = key.toString() // e.g., "switch:0", "cover:0", "input:0"
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
        logDebug("No child found for ${comp} (dst=${dst})")
      }
    }
  }
}

/**
 * Routes webhook GET query parameters to appropriate children.
 * For EM (3-phase) powermon, the phase param (a/b/c) maps to child index
 * within the EM component: em:0 phase a → em-0, phase b → em-1, phase c → em-2.
 *
 * @param params The parsed query parameters including dst, cid, and optionally comp/phase
 */
private void routeWebhookParams(Map params) {
  String dst = params.dst as String
  if (!dst || params.cid == null) {
    logTrace("routeWebhookParams: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  Integer compId = params.cid as Integer

  // For powermon, component type comes from the 'comp' query param (e.g., 'switch', 'cover', 'em', 'em1')
  String baseType = (dst == 'powermon' && params.comp) ?
      (params.comp as String) : dstToComponentType(dst)

  // EM 3-phase: map phase letter to child index (a→0, b→1, c→2)
  if (dst == 'powermon' && baseType == 'em' && params.phase) {
    Map phaseMap = [a: 0, b: 1, c: 2]
    Integer phaseIdx = phaseMap[params.phase as String] ?: 0
    compId = compId * 3 + phaseIdx
  }

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
    logDebug("routeWebhookParams: no child found for DNI ${childDni}")
  }
}

/**
 * Maps a webhook dst parameter to its Shelly component type.
 * Handles both prefix-based matching (e.g., switch_on, cover_open, smoke_alarm)
 * and legacy monitor-style dst values (switchmon, covermon).
 *
 * @param dst The webhook destination string
 * @return The Shelly component type (switch, cover, input, smoke, etc.)
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

  if (!status) { return }

  // Iterate over status entries and send synthetic notifications to children
  status.each { key, value ->
    if (key.toString().contains(':') && value instanceof Map) {
      String comp = key.toString()
      String baseType = comp.split(':')[0]
      Integer compId = (comp.split(':')[1] as Integer)

      String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
      def child = getChildDeviceHelper(childDni)

      if (child) {
        // Determine appropriate dst based on component type
        String dst = baseType == 'switch' ? 'switchmon' : baseType == 'cover' ? 'covermon' : null
        if (dst) {
          List<Map> events = buildComponentEvents(dst, baseType, value as Map)
          events.each { evt ->
            childSendEventHelper(child, evt)
            logDebug("Sent status to ${child.displayName}: ${evt}")
          }
        }
      }
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END LAN Message Parsing and Event Routing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Building                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Builds events from POST notification or status data.
 *
 * @param dst Destination type (switchmon, powermon, covermon, input_push, etc.)
 * @param baseType Component base type (switch, cover, light, input)
 * @param data Component data map
 * @return List of event maps to send to child
 */
@CompileStatic
private List<Map> buildComponentEvents(String dst, String baseType, Map data) {
  List<Map> events = []

  switch (dst) {
    case 'switchmon':
      if (data.output != null) {
        String switchState = data.output ? 'on' : 'off'
        events.add([name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}"])
      }
      break

    case 'covermon':
      if (data.state != null) {
        String shadeState = mapCoverState(data.state as String)
        events.add([name: 'windowShade', value: shadeState,
          descriptionText: "Window shade is ${shadeState}"])
      }
      if (data.current_pos != null) {
        events.add([name: 'position', value: data.current_pos as Integer, unit: '%'])
      }
      // Inline power monitoring for covers
      if (data.voltage != null) {
        events.add([name: 'voltage', value: data.voltage as BigDecimal, unit: 'V'])
      }
      if (data.current != null) {
        events.add([name: 'amperage', value: data.current as BigDecimal, unit: 'A'])
      }
      if (data.apower != null) {
        events.add([name: 'power', value: data.apower as BigDecimal, unit: 'W'])
      }
      if (data.aenergy?.total != null) {
        BigDecimal energyWh = data.aenergy.total as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      }
      break

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

  // Battery level (for battery-powered devices)
  if (data?.battPct != null) {
    events.add([name: 'battery', value: data.battPct as Integer, unit: '%'])
  }

  return events
}

/**
 * Builds events from webhook GET parameters.
 * Handles both new prefix-based dst values (switch_on, cover_open, etc.)
 * and legacy monitor-style dst values (switchmon, covermon) for backwards compatibility.
 *
 * @param dst Destination type (e.g., switch_on, switch_off, switchmon, cover_open, covermon, etc.)
 * @param params Query parameters from webhook
 * @return List of event maps to send to child
 */
@CompileStatic
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    // New prefix-based switch events (no params.output check needed)
    case 'switch_on':
      events.add([name: 'switch', value: 'on',
        descriptionText: 'Switch turned on'])
      break
    case 'switch_off':
      events.add([name: 'switch', value: 'off',
        descriptionText: 'Switch turned off'])
      break

    // Legacy switch monitor (uses params.output)
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        events.add([name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}"])
      }
      break

    // New prefix-based cover events (state derived from dst)
    case 'cover_open':
      events.add([name: 'windowShade', value: 'open',
        descriptionText: 'Window shade is open'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break
    case 'cover_closed':
      events.add([name: 'windowShade', value: 'closed',
        descriptionText: 'Window shade is closed'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break
    case 'cover_stopped':
      events.add([name: 'windowShade', value: 'partially open',
        descriptionText: 'Window shade is partially open'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break
    case 'cover_opening':
      events.add([name: 'windowShade', value: 'opening',
        descriptionText: 'Window shade is opening'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break
    case 'cover_closing':
      events.add([name: 'windowShade', value: 'closing',
        descriptionText: 'Window shade is closing'])
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break
    case 'cover_calibrating':
      events.add([name: 'windowShade', value: 'unknown',
        descriptionText: 'Window shade is calibrating'])
      break

    // Legacy cover monitor (uses params.state)
    case 'covermon':
      if (params.state != null) {
        String shadeState = mapCoverState(params.state as String)
        events.add([name: 'windowShade', value: shadeState,
          descriptionText: "Window shade is ${shadeState}"])
      }
      if (params.pos != null) {
        events.add([name: 'position', value: params.pos as Integer, unit: '%'])
      }
      break

    // New prefix-based input toggle events (state derived from dst)
    case 'input_toggle_on':
      events.add([name: 'switch', value: 'on',
        descriptionText: 'Input toggled on'])
      break
    case 'input_toggle_off':
      events.add([name: 'switch', value: 'off',
        descriptionText: 'Input toggled off'])
      break

    // Legacy input toggle (uses params.state)
    case 'input_toggle':
      if (params.state != null) {
        String inputState = params.state == 'true' ? 'on' : 'off'
        events.add([name: 'switch', value: inputState,
          descriptionText: "Input toggled ${inputState}"])
      }
      break

    // Button events (unchanged)
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

    // New prefix-based smoke event
    case 'smoke_alarm':
      events.add([name: 'smoke', value: 'detected',
        descriptionText: 'Smoke detected'])
      break

    // Legacy smoke (uses params for state)
    case 'smoke':
      events.add([name: 'smoke', value: 'detected',
        descriptionText: 'Smoke detected'])
      break

    // Power monitoring via GET (from powermonitoring.js)
    case 'powermon':
      if (params.voltage != null) {
        events.add([name: 'voltage', value: params.voltage as BigDecimal, unit: 'V',
          descriptionText: "Voltage is ${params.voltage}V"])
      }
      if (params.current != null) {
        events.add([name: 'amperage', value: params.current as BigDecimal, unit: 'A',
          descriptionText: "Current is ${params.current}A"])
      }
      if (params.apower != null) {
        events.add([name: 'power', value: params.apower as BigDecimal, unit: 'W',
          descriptionText: "Power is ${params.apower}W"])
      }
      if (params.aenergy != null) {
        BigDecimal energyWh = params.aenergy as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        events.add([name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy is ${energyKwh}kWh"])
      }
      if (params.freq != null) {
        events.add([name: 'frequency', value: params.freq as BigDecimal, unit: 'Hz',
          descriptionText: "Frequency is ${params.freq}Hz"])
      }
      break
  }

  // Battery level
  if (params?.battPct != null) {
    events.add([name: 'battery', value: params.battPct as Integer, unit: '%'])
  }

  return events
}

/**
 * Maps a Shelly cover state string to a Hubitat windowShade value.
 *
 * @param coverState The Shelly cover state
 * @return The Hubitat windowShade state string
 */
@CompileStatic
private String mapCoverState(String coverState) {
  switch (coverState) {
    case 'open': return 'open'
    case 'closed': return 'closed'
    case 'opening': return 'opening'
    case 'closing': return 'closing'
    case 'stopped': return 'partially open'
    case 'calibrating': return 'unknown'
    default: return 'unknown'
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Building                                           ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by children)                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Handles on() command from child switch.
 * Delegates to parent app's parentSendCommand with Switch.Set method.
 *
 * @param childDevice The child device requesting the command
 */
void componentOn(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOn() called by ${childDevice.displayName} (switch ${switchId})")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: true])
}

/**
 * Handles off() command from child switch.
 * Delegates to parent app's parentSendCommand with Switch.Set method.
 *
 * @param childDevice The child device requesting the command
 */
void componentOff(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentOff() called by ${childDevice.displayName} (switch ${switchId})")
  parent?.parentSendCommand(device, 'Switch.Set', [id: switchId, on: false])
}

/**
 * Handles open() command from child cover.
 * Delegates to parent app's parentSendCommand with Cover.Open method.
 *
 * @param childDevice The child device requesting the command
 */
void componentOpen(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentOpen() called by ${childDevice.displayName} (cover ${coverId})")
  parent?.parentSendCommand(device, 'Cover.Open', [id: coverId])
}

/**
 * Handles close() command from child cover.
 * Delegates to parent app's parentSendCommand with Cover.Close method.
 *
 * @param childDevice The child device requesting the command
 */
void componentClose(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentClose() called by ${childDevice.displayName} (cover ${coverId})")
  parent?.parentSendCommand(device, 'Cover.Close', [id: coverId])
}

/**
 * Handles setPosition() command from child cover.
 * Delegates to parent app's parentSendCommand with Cover.GoToPosition method.
 *
 * @param childDevice The child device requesting the command
 * @param position Target position (0-100)
 */
void componentSetPosition(def childDevice, Integer position) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentSetPosition(${position}) called by ${childDevice.displayName} (cover ${coverId})")
  parent?.parentSendCommand(device, 'Cover.GoToPosition', [id: coverId, pos: position])
}

/**
 * Handles stopPositionChange() command from child cover.
 * Delegates to parent app's parentSendCommand with Cover.Stop method.
 *
 * @param childDevice The child device requesting the command
 */
void componentStop(def childDevice) {
  Integer coverId = childDevice.getDataValue('coverId') as Integer
  logDebug("componentStop() called by ${childDevice.displayName} (cover ${coverId})")
  parent?.parentSendCommand(device, 'Cover.Stop', [id: coverId])
}

/**
 * Handles refresh() command from any child.
 * Delegates to parent app's parentRefresh handler.
 *
 * @param childDevice The child device requesting refresh
 */
void componentRefresh(def childDevice) {
  logDebug("componentRefresh() called by ${childDevice.displayName}")
  parent?.parentRefresh(device)
}

/**
 * Handles resetEnergyMonitors() command from child with power monitoring.
 * Delegates to parent app's parentSendCommand with Switch.ResetCounters method.
 *
 * @param childDevice The child device requesting the reset
 */
void componentResetEnergyMonitors(def childDevice) {
  Integer switchId = childDevice.getDataValue('switchId') as Integer
  logDebug("componentResetEnergyMonitors() called by ${childDevice.displayName} (switch ${switchId})")
  parent?.parentSendCommand(device, 'Switch.ResetCounters', [id: switchId, type: ['aenergy']])
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
