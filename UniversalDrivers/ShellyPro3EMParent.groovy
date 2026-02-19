/**
 * Shelly Autoconf EM Parent
 *
 * Dedicated parent driver for Shelly energy meter devices (Gen 2+).
 * Supports two layouts:
 *   - 3-phase (Pro 3EM): em:0 with phase-prefixed fields (a_voltage, b_voltage, c_voltage)
 *   - Per-channel (EM Gen3, Pro EM-50): em1:0 + em1:1, each with standard fields (voltage, current)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates EM children in initialize() (3 phases for em:0, N channels for em1:N)
 *   - Contactor relay (switch:100 or switch:0) controlled directly on parent via Switch capability
 *   - Internal device temperature exposed on parent via TemperatureMeasurement
 *   - Parent receives LAN traffic, parses locally, routes EM data to children
 *   - Parent aggregates total power/energy/current across all phases/channels
 *   - Commands: parent on()/off() -> app parentSendCommand() -> Shelly RPC Switch.Set
 *
 * Data Sources:
 *   - POST webhooks from powermonitoring.js (per-phase/channel EM data)
 *   - GET webhooks from Shelly native events (switch_on/switch_off for contactor)
 *   - Shelly.GetStatus via refresh (em:0/em1:N, emdata:0/em1data:N, switch:N, temperature:0)
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf EM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'                 // Contactor relay (switch:100)
    capability 'TemperatureMeasurement' // Internal device temp (temperature:0)
    capability 'PowerMeter'             // Aggregated active power (W)
    capability 'EnergyMeter'            // Aggregated total energy (kWh)
    capability 'CurrentMeter'           // Aggregated current (A)
    capability 'VoltageMeasurement'     // Max voltage across phases (V)
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    attribute 'lastUpdated', 'string'
    attribute 'powerFactor', 'number'       // Weighted-average PF
    attribute 'reactivePower', 'number'     // Sum reactive power (VAR)
    attribute 'energyReturned', 'number'    // Sum returned energy (kWh)
    attribute 'frequency', 'number'         // Line frequency (Hz)
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
 * Initializes the parent device: creates/reconciles 3-phase EM children.
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
 * Determines the EM layout from component data values.
 * Returns 'em' for 3-phase (Pro 3EM with em:0) or 'em1' for per-channel (EM Gen3 with em1:N).
 *
 * @return 'em' or 'em1'
 */
private String getEmLayout() {
  String componentStr = device.getDataValue('components') ?: ''
  if (componentStr.contains('em1:')) { return 'em1' }
  return 'em'
}

/**
 * Returns the count of em1 channels from the components data value.
 *
 * @return Number of em1:N components found
 */
private Integer getEm1ChannelCount() {
  String componentStr = device.getDataValue('components') ?: ''
  return componentStr.split(',').findAll { it.trim().startsWith('em1:') }.size()
}

/**
 * Reconciles driver-level child devices for EM channels.
 * For 3-phase (em:0): creates 3 EM children (Phase A, B, C).
 * For per-channel (em1:N): creates N EM children (Channel 0, 1, ...).
 * No switch children -- the contactor is controlled directly on the parent.
 */
void reconcileChildDevices() {
  String layout = getEmLayout()

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set

  if (layout == 'em') {
    // 3-phase layout: always 3 phase children
    for (int i = 0; i < 3; i++) {
      desiredDnis.add("${device.deviceNetworkId}-em-${i}".toString())
    }
  } else {
    // Per-channel layout: one child per em1:N component
    Integer channelCount = getEm1ChannelCount()
    for (int i = 0; i < channelCount; i++) {
      desiredDnis.add("${device.deviceNetworkId}-em1-${i}".toString())
    }
  }

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation (layout=${layout}): desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children (exist but shouldn't)
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  if (layout == 'em') {
    // Create 3-phase children (Phase A, B, C)
    List<String> phaseLabels = ['A', 'B', 'C']
    for (int i = 0; i < 3; i++) {
      String childDni = "${device.deviceNetworkId}-em-${i}"
      if (getChildDevice(childDni)) { continue }

      String label = "${device.displayName} Phase ${phaseLabels[i]}"
      try {
        def child = addChildDevice('ShellyUSA', 'Shelly Autoconf EM', childDni, [name: label, label: label])
        child.updateDataValue('componentType', 'em')
        child.updateDataValue('emId', '0')
        child.updateDataValue('phase', phaseLabels[i].toLowerCase())
        logInfo("Created child: ${label} (Shelly Autoconf EM)")
      } catch (Exception e) {
        logError("Failed to create child ${label}: ${e.message}")
      }
    }
  } else {
    // Create per-channel children (Channel 0, 1, ...)
    Integer channelCount = getEm1ChannelCount()
    for (int i = 0; i < channelCount; i++) {
      String childDni = "${device.deviceNetworkId}-em1-${i}"
      if (getChildDevice(childDni)) { continue }

      String label = "${device.displayName} Channel ${i}"
      try {
        def child = addChildDevice('ShellyUSA', 'Shelly Autoconf EM', childDni, [name: label, label: label])
        child.updateDataValue('componentType', 'em1')
        child.updateDataValue('em1Id', i.toString())
        logInfo("Created child: ${label} (Shelly Autoconf EM)")
      } catch (Exception e) {
        logError("Failed to create child ${label}: ${e.message}")
      }
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands (Contactor Control)                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on the contactor relay (switch:100) via Shelly RPC.
 */
void on() {
  logDebug('on() -- turning on contactor (switch:100)')
  parent?.parentSendCommand(device, 'Switch.Set', [id: 100, on: true])
}

/**
 * Turns off the contactor relay (switch:100) via Shelly RPC.
 */
void off() {
  logDebug('off() -- turning off contactor (switch:100)')
  parent?.parentSendCommand(device, 'Switch.Set', [id: 100, on: false])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Component Commands (called by EM children)                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Handles refresh() command from any EM child.
 * Delegates to parent app's parentRefresh handler.
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
 * Receives LAN messages, parses locally, routes to children or parent.
 * POST requests (from powermonitoring.js) carry per-phase EM data in JSON body.
 * GET requests (from Shelly native webhooks) carry switch state in URL path.
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
 * Handles POST webhook notifications from powermonitoring.js.
 * Parses JSON body, routes per-phase EM data to children, and aggregates on parent.
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
    routeWebhookNotification(params)
    processWebhookPowerAggregation(params)
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

/**
 * Handles GET webhook notifications from Shelly native action webhooks.
 * Parses path segments to extract dst and cid, then routes to handlers.
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
    logDebug("GET webhook: no dst found -- headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
  }
}

/**
 * Routes webhook notification to EM children or updates parent directly.
 * - powermon with comp=em + phase -> EM child (Phase A/B/C)
 * - switch_on/switch_off -> parent switch attribute (contactor)
 *
 * @param params The parsed webhook parameters including dst, cid, and data
 */
private void routeWebhookNotification(Map params) {
  String dst = params.dst
  if (!dst || params.cid == null) {
    logTrace("routeWebhookNotification: missing dst or cid (dst=${dst}, cid=${params.cid})")
    return
  }

  List<Map> events = buildWebhookEvents(dst, params)
  logTrace("buildWebhookEvents(dst=${dst}) -> ${events.size()} events: ${events}")
  if (!events) { return }

  // Power monitoring with EM phase data -> route to EM child
  if (dst == 'powermon' && params.comp == 'em' && params.phase) {
    Map<String, Integer> phaseMap = [a: 0, b: 1, c: 2]
    Integer phaseIdx = phaseMap[params.phase as String]
    if (phaseIdx == null) {
      logWarn("Unknown phase '${params.phase}' in powermon webhook, ignoring")
      return
    }
    String childDni = "${device.deviceNetworkId}-em-${phaseIdx}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt ->
        logTrace("Sending webhook event to ${child.displayName}: ${evt}")
        child.sendEvent(evt)
      }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Routed ${events.size()} webhook events to ${child.displayName}")
    } else {
      logDebug("No child device found for DNI: ${childDni}")
    }
  } else if (dst == 'powermon' && params.comp == 'em1') {
    // Per-channel EM1 power monitoring -> route to em1 child
    Integer channelId = params.cid as Integer
    String childDni = "${device.deviceNetworkId}-em1-${channelId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt ->
        logTrace("Sending webhook event to ${child.displayName}: ${evt}")
        child.sendEvent(evt)
      }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Routed ${events.size()} webhook events to ${child.displayName}")
    } else {
      logDebug("No child device found for DNI: ${childDni}")
    }
  } else if (dst == 'switch_on' || dst == 'switch_off') {
    // Switch events -> parent directly (contactor)
    events.each { Map evt -> sendEvent(evt) }
    sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
  } else {
    logTrace("routeWebhookNotification: unhandled dst=${dst}, ignoring")
  }
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /switch_on/100).
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
 * If different, updates the device data value and notifies the parent app.
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
 * Builds events from webhook parameters.
 * Handles powermon (EM data), switch_on/switch_off (contactor), and other events.
 *
 * @param dst The webhook destination type
 * @param params The webhook parameters map
 * @return List of event maps
 */
@CompileStatic
private List<Map> buildWebhookEvents(String dst, Map params) {
  List<Map> events = []

  switch (dst) {
    case 'switch_on':
      events.add([name: 'switch', value: 'on', descriptionText: 'Contactor turned on'])
      break
    case 'switch_off':
      events.add([name: 'switch', value: 'off', descriptionText: 'Contactor turned off'])
      break

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

  return events
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Building                                           ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes Shelly.GetStatus response to EM children and parent.
 * Called by app's parentRefresh() after querying device.
 *
 * Pro 3EM status structure:
 *   em:0 -> a_voltage, a_current, a_act_power, a_pf, a_freq, b_*, c_*, total_*
 *   emdata:0 -> a_total_act_energy, a_total_act_ret_energy, b_*, c_*
 *   switch:100 -> output (contactor state)
 *   temperature:0 -> tC, tF (internal device temperature)
 *
 * @param deviceStatus Map of component statuses from Shelly.GetStatus
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  String layout = getEmLayout()

  if (layout == 'em') {
    // 3-phase layout: em:0 with phase-prefixed fields
    Map emData = deviceStatus['em:0'] as Map
    Map emdataData = deviceStatus['emdata:0'] as Map

    if (emData) {
      distributeEmToChildren(emData, emdataData)
    }
    if (emData || emdataData) {
      updateAggregatesFromStatus(emData, emdataData)
    }
  } else {
    // Per-channel layout: em1:N with standard fields
    distributeEm1ToChildren(deviceStatus)
    updateAggregatesFromEm1Status(deviceStatus)
  }

  // Contactor state -> parent switch attribute (check switch:100 first, then switch:0)
  Map switchData = (deviceStatus['switch:100'] ?: deviceStatus['switch:0']) as Map
  if (switchData?.output != null) {
    String switchState = switchData.output ? 'on' : 'off'
    sendEvent(name: 'switch', value: switchState, descriptionText: "Contactor is ${switchState}")
  }

  // Internal temperature -> parent temperature attribute
  Map tempData = deviceStatus['temperature:0'] as Map
  if (tempData) {
    String scale = location?.temperatureScale ?: 'F'
    BigDecimal temp = (scale == 'C' && tempData.tC != null) ? tempData.tC as BigDecimal : tempData.tF as BigDecimal
    if (temp != null) {
      sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

/**
 * Distributes per-phase EM data from em:0 and emdata:0 to children.
 * Parses phase-prefixed fields (a_voltage, b_current, c_act_power, etc.)
 * and sends individual events to each phase child.
 *
 * @param emData The em:0 component status map
 * @param emdataData The emdata:0 component status map (may be null)
 */
private void distributeEmToChildren(Map emData, Map emdataData) {
  List<String> phases = ['a', 'b', 'c']

  phases.eachWithIndex { String phase, int idx ->
    String childDni = "${device.deviceNetworkId}-em-${idx}"
    def child = getChildDevice(childDni)
    if (!child) { return }

    List<Map> events = []

    // GString keys don't hash-equal String keys in Maps from JsonSlurper —
    // always call .toString() on interpolated map keys to ensure correct lookup.

    // Active power from em:0
    String actPowerKey = "${phase}_act_power".toString()
    if (emData[actPowerKey] != null) {
      BigDecimal power = emData[actPowerKey] as BigDecimal
      events.add([name: 'power', value: power, unit: 'W'])
    }

    // Voltage from em:0
    String voltageKey = "${phase}_voltage".toString()
    if (emData[voltageKey] != null) {
      BigDecimal voltage = emData[voltageKey] as BigDecimal
      events.add([name: 'voltage', value: voltage, unit: 'V'])
    }

    // Current from em:0
    String currentKey = "${phase}_current".toString()
    if (emData[currentKey] != null) {
      BigDecimal current = emData[currentKey] as BigDecimal
      events.add([name: 'amperage', value: current, unit: 'A'])
    }

    // Power factor from em:0
    String pfKey = "${phase}_pf".toString()
    if (emData[pfKey] != null) {
      BigDecimal pf = emData[pfKey] as BigDecimal
      events.add([name: 'powerFactor', value: pf])
    }

    // Total active energy from emdata:0
    String energyKey = "${phase}_total_act_energy".toString()
    if (emdataData && emdataData[energyKey] != null) {
      BigDecimal energyWh = emdataData[energyKey] as BigDecimal
      BigDecimal energyKwh = energyWh / 1000.0
      events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
    }

    // Returned energy from emdata:0
    String retEnergyKey = "${phase}_total_act_ret_energy".toString()
    if (emdataData && emdataData[retEnergyKey] != null) {
      BigDecimal retWh = emdataData[retEnergyKey] as BigDecimal
      BigDecimal retKwh = retWh / 1000.0
      events.add([name: 'energyReturned', value: retKwh, unit: 'kWh'])
    }

    if (events) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Distributed ${events.size()} status events to ${child.displayName}")
    }
  }
}

/**
 * Distributes per-channel em1:N data from Shelly.GetStatus to children.
 * Each em1:N component has standard fields (voltage, current, act_power, etc.)
 * without phase prefixes.
 *
 * @param deviceStatus The full device status map from Shelly.GetStatus
 */
private void distributeEm1ToChildren(Map deviceStatus) {
  Integer channelCount = getEm1ChannelCount()

  for (int i = 0; i < channelCount; i++) {
    String em1Key = "em1:${i}".toString()
    String em1dataKey = "em1data:${i}".toString()
    Map em1Data = deviceStatus[em1Key] as Map
    Map em1dataData = deviceStatus[em1dataKey] as Map

    String childDni = "${device.deviceNetworkId}-em1-${i}"
    def child = getChildDevice(childDni)
    if (!child) { continue }

    List<Map> events = []

    if (em1Data) {
      if (em1Data.act_power != null) {
        events.add([name: 'power', value: em1Data.act_power as BigDecimal, unit: 'W'])
      }
      if (em1Data.voltage != null) {
        events.add([name: 'voltage', value: em1Data.voltage as BigDecimal, unit: 'V'])
      }
      if (em1Data.current != null) {
        events.add([name: 'amperage', value: em1Data.current as BigDecimal, unit: 'A'])
      }
      if (em1Data.pf != null) {
        events.add([name: 'powerFactor', value: em1Data.pf as BigDecimal])
      }
      if (em1Data.freq != null) {
        events.add([name: 'frequency', value: em1Data.freq as BigDecimal, unit: 'Hz'])
      }
    }

    if (em1dataData) {
      if (em1dataData.total_act_energy != null) {
        BigDecimal energyKwh = (em1dataData.total_act_energy as BigDecimal) / 1000.0
        events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      }
      if (em1dataData.total_act_ret_energy != null) {
        BigDecimal retKwh = (em1dataData.total_act_ret_energy as BigDecimal) / 1000.0
        events.add([name: 'energyReturned', value: retKwh, unit: 'kWh'])
      }
    }

    if (events) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      logDebug("Distributed ${events.size()} status events to ${child.displayName}")
    }
  }
}

/**
 * Updates parent aggregates from em1:N status data.
 * Sums power/current/energy across all em1 channels.
 *
 * @param deviceStatus The full device status map from Shelly.GetStatus
 */
private void updateAggregatesFromEm1Status(Map deviceStatus) {
  Integer channelCount = getEm1ChannelCount()

  BigDecimal totalPower = 0
  BigDecimal totalCurrent = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalReturnedEnergy = 0
  BigDecimal maxVoltage = 0
  BigDecimal frequency = 0

  for (int i = 0; i < channelCount; i++) {
    String em1Key = "em1:${i}".toString()
    String em1dataKey = "em1data:${i}".toString()
    Map em1Data = deviceStatus[em1Key] as Map
    Map em1dataData = deviceStatus[em1dataKey] as Map

    if (em1Data) {
      if (em1Data.act_power != null) { totalPower += em1Data.act_power as BigDecimal }
      if (em1Data.current != null) { totalCurrent += em1Data.current as BigDecimal }
      if (em1Data.voltage != null) {
        BigDecimal v = em1Data.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
      if (em1Data.freq != null) { frequency = em1Data.freq as BigDecimal }
    }

    if (em1dataData) {
      if (em1dataData.total_act_energy != null) {
        totalEnergy += (em1dataData.total_act_energy as BigDecimal) / 1000.0
      }
      if (em1dataData.total_act_ret_energy != null) {
        totalReturnedEnergy += (em1dataData.total_act_ret_energy as BigDecimal) / 1000.0
      }
    }
  }

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  sendEvent(name: 'energyReturned', value: totalReturnedEnergy, unit: 'kWh')
  if (frequency > 0) {
    sendEvent(name: 'frequency', value: frequency, unit: 'Hz')
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Aggregation                                            ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Processes webhook power aggregation for parent device.
 * Stores per-phase power in state and re-aggregates totals across all 3 phases.
 *
 * @param params The parsed webhook parameters including power values
 */
private void processWebhookPowerAggregation(Map params) {
  String dst = params.dst
  if (dst != 'powermon') { return }
  String comp = params.comp as String
  if (comp != 'em' && comp != 'em1') { return }

  // For em layout, key by phase; for em1 layout, key by channel id
  String stateKey = (comp == 'em') ? (params.phase as String) : (params.cid as String)
  if (!stateKey) { return }

  Map powerStates = state.powerStates ?: [:]

  // Store per-phase/channel power data
  Map phasePower = [:]
  if (params.voltage != null) { phasePower.voltage = params.voltage as BigDecimal }
  if (params.current != null) { phasePower.current = params.current as BigDecimal }
  if (params.apower != null) { phasePower.apower = params.apower as BigDecimal }
  if (params.aenergy != null) { phasePower.aenergy = params.aenergy as BigDecimal }
  if (params.freq != null) { phasePower.freq = params.freq as BigDecimal }
  powerStates[stateKey] = phasePower
  state.powerStates = powerStates

  // Re-aggregate totals across all phases
  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0
  BigDecimal lastFreq = 0

  powerStates.each { key, value ->
    if (value instanceof Map) {
      if (value.apower != null) { totalPower += value.apower as BigDecimal }
      if (value.aenergy != null) { totalEnergy += (value.aenergy as BigDecimal) / 1000 }
      if (value.current != null) { totalCurrent += value.current as BigDecimal }
      if (value.voltage != null) {
        BigDecimal v = value.voltage as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
      // Frequency is per-system, not per-phase — any phase value is valid
      if (value.freq != null) { lastFreq = value.freq as BigDecimal }
    }
  }

  sendEvent(name: 'power', value: totalPower, unit: 'W', descriptionText: "Total power: ${totalPower}W")
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh', descriptionText: "Total energy: ${totalEnergy}kWh")
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A', descriptionText: "Total current: ${totalCurrent}A")
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V', descriptionText: "Voltage: ${maxVoltage}V")
  if (lastFreq > 0) {
    sendEvent(name: 'frequency', value: lastFreq, unit: 'Hz', descriptionText: "Frequency: ${lastFreq}Hz")
  }
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
  logDebug("Aggregate power: ${totalPower}W, energy: ${totalEnergy}kWh, current: ${totalCurrent}A, voltage: ${maxVoltage}V")
}

/**
 * Updates parent aggregates from Shelly.GetStatus data.
 * Computes: sum power/current/energy, max voltage, weighted-average PF,
 * sum reactive power, sum returned energy, frequency.
 *
 * @param emData The em:0 component status map (may be null)
 * @param emdataData The emdata:0 component status map (may be null)
 */
private void updateAggregatesFromStatus(Map emData, Map emdataData) {
  List<String> phases = ['a', 'b', 'c']

  BigDecimal totalPower = 0
  BigDecimal totalCurrent = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalReturnedEnergy = 0
  BigDecimal totalReactivePower = 0
  BigDecimal maxVoltage = 0
  BigDecimal weightedPfSum = 0
  BigDecimal pfWeightDenom = 0
  BigDecimal frequency = 0

  // GString keys don't hash-equal String keys — use .toString() for Map lookups
  if (emData) {
    phases.each { String phase ->
      String actPowerKey = "${phase}_act_power".toString()
      String pfKey = "${phase}_pf".toString()
      String currentKey = "${phase}_current".toString()
      String voltageKey = "${phase}_voltage".toString()
      String freqKey = "${phase}_freq".toString()
      String aprtPowerKey = "${phase}_aprt_power".toString()

      if (emData[actPowerKey] != null) {
        BigDecimal power = emData[actPowerKey] as BigDecimal
        totalPower += power

        // Weight power factor by absolute active power
        if (emData[pfKey] != null) {
          BigDecimal pf = emData[pfKey] as BigDecimal
          BigDecimal absPower = Math.abs(power)
          weightedPfSum += pf * absPower
          pfWeightDenom += absPower
        }
      }
      if (emData[currentKey] != null) {
        totalCurrent += emData[currentKey] as BigDecimal
      }
      if (emData[voltageKey] != null) {
        BigDecimal v = emData[voltageKey] as BigDecimal
        if (v > maxVoltage) { maxVoltage = v }
      }
      // Frequency is per-system, not per-phase — any phase value is valid
      if (emData[freqKey] != null) {
        frequency = emData[freqKey] as BigDecimal
      }

      // Reactive power: sqrt(apparent^2 - active^2)
      if (emData[aprtPowerKey] != null && emData[actPowerKey] != null) {
        BigDecimal apparent = emData[aprtPowerKey] as BigDecimal
        BigDecimal active = emData[actPowerKey] as BigDecimal
        BigDecimal reactiveSquared = (apparent * apparent) - (active * active)
        if (reactiveSquared > 0) {
          totalReactivePower += Math.sqrt(reactiveSquared.doubleValue()) as BigDecimal
        }
      }
    }
  }

  if (emdataData) {
    phases.each { String phase ->
      String energyKey = "${phase}_total_act_energy".toString()
      String retEnergyKey = "${phase}_total_act_ret_energy".toString()

      if (emdataData[energyKey] != null) {
        totalEnergy += (emdataData[energyKey] as BigDecimal) / 1000.0
      }
      if (emdataData[retEnergyKey] != null) {
        totalReturnedEnergy += (emdataData[retEnergyKey] as BigDecimal) / 1000.0
      }
    }
  }

  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  sendEvent(name: 'reactivePower', value: totalReactivePower.setScale(2, BigDecimal.ROUND_HALF_UP), unit: 'VAR')
  sendEvent(name: 'energyReturned', value: totalReturnedEnergy, unit: 'kWh')

  if (pfWeightDenom > 0) {
    BigDecimal avgPf = weightedPfSum / pfWeightDenom
    sendEvent(name: 'powerFactor', value: avgPf.setScale(2, BigDecimal.ROUND_HALF_UP))
  }
  if (frequency > 0) {
    sendEvent(name: 'frequency', value: frequency, unit: 'Hz')
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Aggregation                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Settings Write-Back                                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sends the PM reporting interval setting to the device KVS via the parent app.
 */
private void sendPmReportingIntervalToKVS() {
  Integer interval = settings?.pmReportingInterval != null ? settings.pmReportingInterval as Integer : 60
  parent?.componentWriteKvsToDevice(device, 'hubitat_sdm_pm_ri', interval)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Settings Write-Back                                      ║
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

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
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

String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
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
