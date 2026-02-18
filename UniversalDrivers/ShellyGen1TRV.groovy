/**
 * Shelly Gen1 TRV
 *
 * Pre-built standalone driver for Gen 1 Shelly TRV (SHTRV-01).
 * Battery-powered thermostatic radiator valve that maintains a persistent
 * WiFi connection (always-awake) so it can receive heating commands.
 *
 * The app polls periodically to read temperature, valve position, battery,
 * and thermostat state. Action URL callbacks provide immediate notification
 * of valve open/close events.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 TRV', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'ThermostatHeatingSetpoint'
    //Attributes: heatingSetpoint - NUMBER; Commands: setHeatingSetpoint(temp)

    capability 'Valve'
    //Attributes: valve - ENUM ["open","closed"]; Commands: open(), close()

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    command 'setValvePosition', [[name: 'Position', type: 'NUMBER', description: 'Valve position (0-100)']]
    command 'setExternalTemperature', [[name: 'Temperature', type: 'NUMBER', description: 'External temperature reading']]
    command 'setBoostMinutes', [[name: 'Minutes', type: 'NUMBER', description: 'Boost duration in minutes (0 to cancel)']]
    command 'setScheduleEnabled', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable/disable schedule']]

    attribute 'valvePosition', 'number'
    attribute 'boostMinutes', 'number'
    attribute 'windowOpen', 'enum', ['true', 'false']
    attribute 'scheduleEnabled', 'enum', ['true', 'false']
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  // ── Thermostat Settings (synced to device) ──
  input name: 'temperatureOffset', type: 'decimal',
    title: 'Temperature offset (-5.0 to 5.0)',
    description: 'Calibration offset for internal temperature sensor. Synced to/from device.',
    range: '-5.0..5.0', required: false

  // ── Logging ──
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace': 'Trace', 'debug': 'Debug', 'info': 'Info', 'warn': 'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
  relayDeviceSettings()
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly TRV.
 * Handles action URL callbacks for valve open/close events.
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
 * GET Action Webhooks encode state in the path (e.g., /valve_open/0).
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
 * Routes Gen 1 action URL callbacks for TRV valve events.
 * On valve open/close, updates valve state and triggers a status poll
 * for current temperature and setpoint data.
 *
 * @param params Map with dst, cid, and optional key/value pairs
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'valve_open':
      sendEvent(name: 'valve', value: 'open', isStateChange: true,
        descriptionText: 'Valve opened')
      logInfo('Valve opened')
      parent?.componentRefresh(device)
      break

    case 'valve_close':
      sendEvent(name: 'valve', value: 'closed', isStateChange: true,
        descriptionText: 'Valve closed')
      logInfo('Valve closed')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Initializes the TRV driver with safe defaults and requests a status refresh.
 * TRV is always awake so refresh will succeed immediately.
 */
void initialize() {
  logDebug('initialize() called')
  sendEvent(name: 'valve', value: 'closed', descriptionText: 'Initialized as closed')
  sendEvent(name: 'valvePosition', value: 0, descriptionText: 'Initialized at position 0')
  parent?.componentRefresh(device)
}

/**
 * Configures the TRV driver. Sets default log level if missing and clears
 * the settings sync flag so next refresh re-reads settings from device.
 */
void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
  // Clear sync flag so next refresh re-reads settings from device
  device.removeDataValue('gen1SettingsSynced')
  parent?.componentRefresh(device)
}

/**
 * Refreshes the TRV state by requesting a status poll from the parent app.
 * TRV is always awake so data will update immediately.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Thermostat Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the TRV heating setpoint. Converts Fahrenheit to Celsius if
 * the hub is configured for Fahrenheit since the TRV API uses Celsius.
 *
 * @param temp Target temperature in the hub's configured scale
 */
void setHeatingSetpoint(BigDecimal temp) {
  logDebug("setHeatingSetpoint(${temp}) called")
  BigDecimal tempC = (location.temperatureScale == 'F') ? ((temp - 32) * 5 / 9) : temp
  parent?.componentSetTrvHeatingSetpoint(device, tempC)
}

/**
 * Opens the TRV valve fully (position 100).
 */
void open() {
  logDebug('open() called')
  parent?.componentSetTrvValvePosition(device, 100)
}

/**
 * Closes the TRV valve fully (position 0).
 */
void close() {
  logDebug('close() called')
  parent?.componentSetTrvValvePosition(device, 0)
}

/**
 * Sets the TRV valve position to a specific percentage.
 *
 * @param position Valve position from 0 (closed) to 100 (fully open)
 */
void setValvePosition(BigDecimal position) {
  logDebug("setValvePosition(${position}) called")
  parent?.componentSetTrvValvePosition(device, position.setScale(0, BigDecimal.ROUND_HALF_UP).intValue())
}

/**
 * Sends an external temperature reading to the TRV for more accurate
 * room temperature measurement. Converts F to C if needed.
 *
 * @param temp External temperature in the hub's configured scale
 */
void setExternalTemperature(BigDecimal temp) {
  logDebug("setExternalTemperature(${temp}) called")
  BigDecimal tempC = (location.temperatureScale == 'F') ? ((temp - 32) * 5 / 9) : temp
  parent?.componentSetTrvExternalTemp(device, tempC)
}

/**
 * Sets the TRV boost mode duration. The TRV will heat at maximum
 * output for the specified number of minutes.
 *
 * @param minutes Boost duration in minutes (0 to cancel boost)
 */
void setBoostMinutes(BigDecimal minutes) {
  logDebug("setBoostMinutes(${minutes}) called")
  parent?.componentSetTrvBoostMinutes(device, minutes as Integer)
}

/**
 * Enables or disables the TRV's internal heating schedule.
 *
 * @param enabled 'true' to enable, 'false' to disable
 */
void setScheduleEnabled(String enabled) {
  logDebug("setScheduleEnabled(${enabled}) called")
  parent?.componentSetTrvScheduleEnabled(device, enabled == 'true')
}

/**
 * Gathers TRV-specific settings and sends them to the parent app for
 * relay to the Shelly device via GET /settings/thermostats/0.
 * Only sends settings that have been configured (non-null).
 */
private void relayDeviceSettings() {
  Map settingsMap = [:]
  if (settings.temperatureOffset != null) {
    settingsMap.temperature_offset = (settings.temperatureOffset as BigDecimal).toString()
  }
  if (settingsMap) {
    logDebug("Relaying TRV settings to parent: ${settingsMap}")
    parent?.componentUpdateGen1ThermostatSettings(device, settingsMap)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the TRV device attributes.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles thermostat state, temperature, valve position, and battery.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  String scale = location.temperatureScale ?: 'F'

  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    if (key.startsWith('thermostat:')) {
      // Valve position
      if (data.pos != null) {
        Integer pos = data.pos as Integer
        sendEvent(name: 'valvePosition', value: pos,
          descriptionText: "Valve position is ${pos}%")
        String valveState = pos > 0 ? 'open' : 'closed'
        sendEvent(name: 'valve', value: valveState,
          descriptionText: "Valve is ${valveState}")
        logInfo("Valve position: ${pos}%, state: ${valveState}")
      }

      // Heating setpoint (target temperature)
      if (data.target_t != null) {
        BigDecimal setpointC = data.target_t as BigDecimal
        BigDecimal setpoint = (scale == 'F') ? (setpointC * 9.0 / 5.0) + 32.0 : setpointC
        setpoint = setpoint.setScale(1, BigDecimal.ROUND_HALF_UP)
        sendEvent(name: 'heatingSetpoint', value: setpoint, unit: "°${scale}",
          descriptionText: "Heating setpoint is ${setpoint}°${scale}")
        logInfo("Heating setpoint: ${setpoint}°${scale}")
      }

      // Boost minutes remaining
      if (data.boost_minutes != null) {
        Integer boost = data.boost_minutes as Integer
        sendEvent(name: 'boostMinutes', value: boost,
          descriptionText: "Boost minutes remaining: ${boost}")
        if (boost > 0) { logInfo("Boost active: ${boost} minutes remaining") }
      }

      // Window open detection
      if (data.window_open != null) {
        String windowState = data.window_open ? 'true' : 'false'
        sendEvent(name: 'windowOpen', value: windowState,
          descriptionText: "Window open: ${windowState}")
      }

      // Schedule enabled
      if (data.schedule != null) {
        String schedState = data.schedule ? 'true' : 'false'
        sendEvent(name: 'scheduleEnabled', value: schedState,
          descriptionText: "Schedule enabled: ${schedState}")
      }

    } else if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
        logInfo("Temperature: ${temp}°${scale}")
      }

    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
        logInfo("Battery: ${battery}%")
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
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



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
