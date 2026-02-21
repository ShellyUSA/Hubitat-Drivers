/**
 * Shelly Gen1 Single Cover PM Parent
 *
 * Parent driver for Gen 1 single-cover Shelly devices with power monitoring.
 * Examples: Shelly 2.5 (roller mode)
 *
 * Architecture:
 * - Creates zero children (single cover handled directly on parent device)
 * - Parses Gen 1 action URL callbacks (GET only, no POST scripts)
 * - Delegates commands to parent app via componentOpen/componentClose/etc.
 * - Power monitoring and position data obtained via polling (no scripts on Gen 1)
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Single Cover PM Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade'
    //Attributes: windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
    //            position - NUMBER, unit:%
    //Commands: open(), close(), setPosition(position)

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'VoltageMeasurement'
    //Attributes: voltage - NUMBER, unit:V

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    command 'stopPositionChange'
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

@Field static Boolean NOCHILDCOVER = true

// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 */
void installed() {
  logDebug('Parent device installed')
  initialize()
}

/**
 * Called when device settings are saved.
 */
void updated() {
  logDebug('Parent device updated')
  initialize()
}

/**
 * Initializes the parent device driver.
 */
void initialize() {
  logDebug('Parent device initialized')
}

/**
 * Configures the device driver settings.
 */
void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

/**
 * Refreshes the device state by querying the parent app.
 * App will poll the Gen 1 device via GET /status and call distributeStatus().
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

/**
 * Triggers device reinitialization via the parent app.
 */
void reinitializeDevice() {
  logDebug('reinitializeDevice() called')
  parent?.reinitializeDevice(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  LAN Message Parsing and Event Routing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses incoming LAN messages from the Gen 1 Shelly device.
 * Gen 1 action URLs fire GET requests only — no POST body from scripts.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }
    checkAndUpdateSourceIp(msg)

    Map params = parseWebhookPath(msg)
    if (params?.dst) {
      logDebug("Action URL callback dst=${params.dst}, cid=${params.cid}")
      routeActionUrlCallback(params)
    } else {
      logDebug("No dst found in action URL callback — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Parses webhook GET request path to extract dst and cid from URL segments.
 * GET Action Webhooks encode state in the path (e.g., /cover_open/0).
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

/**
 * Routes Gen 1 action URL callbacks to appropriate event handlers.
 * Gen 1 cover action URLs: roller_open_url, roller_close_url, roller_stop_url.
 * After handling state change, triggers a refresh to get position/power data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'cover_open':
      sendEvent(name: 'windowShade', value: 'open', descriptionText: 'Window shade is open')
      logInfo('Cover state changed to: open')
      parent?.componentRefresh(device)
      break
    case 'cover_close':
      sendEvent(name: 'windowShade', value: 'closed', descriptionText: 'Window shade is closed')
      logInfo('Cover state changed to: closed')
      parent?.componentRefresh(device)
      break
    case 'cover_stop':
      sendEvent(name: 'windowShade', value: 'partially open', descriptionText: 'Window shade stopped')
      logInfo('Cover state changed to: partially open (stopped)')
      parent?.componentRefresh(device)
      break
    case 'cover_opening':
      sendEvent(name: 'windowShade', value: 'opening', descriptionText: 'Window shade is opening')
      logInfo('Cover state changed to: opening')
      break
    case 'cover_closing':
      sendEvent(name: 'windowShade', value: 'closing', descriptionText: 'Window shade is closing')
      logInfo('Cover state changed to: closing')
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END LAN Message Parsing and Event Routing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Cover Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the window shade / cover.
 * Delegates to parent app which sends GET /roller/0?go=open.
 */
void open() {
  logDebug('open() called')
  parent?.componentOpen(device)
}

/**
 * Closes the window shade / cover.
 * Delegates to parent app which sends GET /roller/0?go=close.
 */
void close() {
  logDebug('close() called')
  parent?.componentClose(device)
}

/**
 * Sets the cover position to a specific value.
 * Delegates to parent app which sends GET /roller/0?go=to_pos&roller_pos={pos}.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.componentSetPosition(device, position as Integer)
}

/**
 * Stops any in-progress cover movement.
 * Delegates to parent app which sends GET /roller/0?go=stop.
 */
void stopPositionChange() {
  logDebug('stopPositionChange() called')
  parent?.componentStop(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Cover Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles cover state, position, and power monitoring values.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  status.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('cover:') || !(v instanceof Map)) { return }

    Map data = v as Map

    // Cover state
    if (data.state != null) {
      String shadeState = mapCoverState(data.state.toString())
      sendEvent(name: 'windowShade', value: shadeState, descriptionText: "Window shade is ${shadeState}")
      logInfo("Cover state: ${shadeState}")
    }

    // Position
    if (data.current_pos != null) {
      Integer position = data.current_pos as Integer
      sendEvent(name: 'position', value: position, unit: '%', descriptionText: "Position is ${position}%")
      logDebug("Cover position: ${position}%")
    }

    // Power monitoring
    if (data.apower != null) {
      sendEvent(name: 'power', value: data.apower as BigDecimal, unit: 'W')
    }
    if (data.voltage != null) {
      sendEvent(name: 'voltage', value: data.voltage as BigDecimal, unit: 'V')
    }
    if (data.current != null) {
      sendEvent(name: 'amperage', value: data.current as BigDecimal, unit: 'A')
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      sendEvent(name: 'energy', value: energyKwh, unit: 'kWh')
    }
  }
}

/**
 * Maps a Gen 1 roller state string to a Hubitat windowShade value.
 *
 * @param coverState The Gen 1 roller state (open, close, stop)
 * @return The Hubitat windowShade state string
 */
@CompileStatic
private String mapCoverState(String coverState) {
  switch (coverState) {
    case 'open': return 'open'
    case 'close': return 'closed'
    case 'closed': return 'closed'
    case 'stop': return 'partially open'
    case 'stopped': return 'partially open'
    case 'opening': return 'opening'
    case 'closing': return 'closing'
    default: return 'unknown'
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
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
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝
