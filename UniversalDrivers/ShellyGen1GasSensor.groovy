/**
 * Shelly Gen1 Gas Sensor (SHGS-1)
 *
 * Pre-built standalone driver for the Gen 1 Shelly Gas sensor.
 * Mains-powered device that is always reachable via HTTP (no sleep behavior).
 *
 * Monitors LPG/natural gas concentration (PPM) and triggers alarms at mild/heavy
 * levels. Optionally controls a wired valve for gas pipe shutoff.
 *
 * Fires action URL callbacks on:
 *   - Gas alarm mild / heavy / off (alarm_mild_url / alarm_heavy_url / alarm_off_url)
 *
 * Commands:
 *   - Valve open/close (via /valve/0?go=open|close)
 *   - Self-test (via /self_test)
 *   - Mute/unmute active alarm (via /mute, /unmute)
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Gas Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'GasDetector'
    //Attributes: naturalGas - ENUM ["clear", "tested", "detected"]

    capability 'Valve'
    //Attributes: valve - ENUM ["open", "closed"]
    //Commands: open(), close()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'gasConcentration', 'number'
    attribute 'alarmLevel', 'string'
    attribute 'sensorState', 'string'
    attribute 'selfTestState', 'string'
    attribute 'valveState', 'string'
    attribute 'lastUpdated', 'string'

    command 'selfTest'
    command 'mute'
    command 'unmute'
  }
}

@Field static Boolean NOCHILDSWITCH = true

preferences {
  // -- Valve Configuration (synced from device on first refresh) --
  input name: 'valveDefaultState', type: 'enum', title: 'Valve default state',
    description: 'Default valve state after power loss. Synced from device.',
    options: ['closed':'Closed', 'opened':'Opened'], required: false

  // -- Logging --
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Sets default log level if not already configured.
 */
void installed() {
  logDebug('installed() called')
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Called when device settings are saved.
 * Gas sensor has no user-configurable device-side settings.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Re-reads configuration from the physical device by clearing the sync flag.
 * The next refresh will re-sync device settings to driver preferences.
 */
void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
  device.removeDataValue('gen1SettingsSynced')
  parent?.componentRefresh(device)
}

/**
 * Refreshes the device state. Gas sensor is mains-powered and always reachable.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

/**
 * Initiates a gas sensor self-test via the parent app.
 * Self-test state will update on the next status poll.
 */
void selfTest() {
  logInfo('selfTest() called')
  parent?.componentGasSelfTest(device)
}

/**
 * Mutes the active gas alarm via the parent app.
 */
void mute() {
  logInfo('mute() called')
  parent?.componentGasMute(device)
}

/**
 * Unmutes the gas alarm via the parent app.
 */
void unmute() {
  logInfo('unmute() called')
  parent?.componentGasUnmute(device)
}

/**
 * Opens the gas valve via the parent app.
 */
void open() {
  logInfo('open() called — opening gas valve')
  parent?.componentGasValveOpen(device)
}

/**
 * Closes the gas valve via the parent app.
 */
void close() {
  logInfo('close() called — closing gas valve')
  parent?.componentGasValveClose(device)
}

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
 * GET Action Webhooks encode state in the path (e.g., /gas_alarm_mild/0).
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
 * Routes Gen 1 action URL callbacks for gas sensor.
 * Gas alarm events update naturalGas and alarmLevel attributes immediately.
 * All events trigger a refresh to get full status (PPM, valve state, sensor state).
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'gas_alarm_mild':
      sendEvent(name: 'naturalGas', value: 'detected', descriptionText: 'Gas alarm: mild level detected')
      sendEvent(name: 'alarmLevel', value: 'mild', descriptionText: 'Alarm level: mild')
      logWarn('Gas alarm: mild level detected!')
      parent?.componentRefresh(device)
      break
    case 'gas_alarm_heavy':
      sendEvent(name: 'naturalGas', value: 'detected', descriptionText: 'Gas alarm: heavy level detected')
      sendEvent(name: 'alarmLevel', value: 'heavy', descriptionText: 'Alarm level: heavy')
      logWarn('Gas alarm: HEAVY level detected!')
      parent?.componentRefresh(device)
      break
    case 'gas_alarm_off':
      sendEvent(name: 'naturalGas', value: 'clear', descriptionText: 'Gas alarm cleared')
      sendEvent(name: 'alarmLevel', value: 'none', descriptionText: 'Alarm level: none')
      logInfo('Gas alarm cleared')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
      return  // Don't update lastUpdated for unhandled callbacks
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝






// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 *
 * Handles two component types:
 *   - gas:0 — alarm_state, ppm, sensor_state, self_test_state
 *   - valve:0 — valve state (opened/closed/not_connected/etc.)
 *
 * Alarm state mapping (Shelly → Hubitat GasDetector):
 *   none    → clear    | mild  → detected
 *   heavy   → detected | test  → tested
 *   unknown → clear
 *
 * Valve state mapping (Shelly → Hubitat Valve):
 *   opened/opening             → open
 *   closed/closing/checking    → closed
 *   not_connected/failure      → (valveState only, no valve event)
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    if (key.startsWith('gas:')) {
      // Alarm state → naturalGas + alarmLevel
      if (data.alarm_state != null) {
        String alarmState = data.alarm_state.toString()
        String naturalGas
        switch (alarmState) {
          case 'none':    naturalGas = 'clear'; break
          case 'mild':    naturalGas = 'detected'; break
          case 'heavy':   naturalGas = 'detected'; break
          case 'test':    naturalGas = 'tested'; break
          case 'unknown': naturalGas = 'clear'; break
          default:        naturalGas = 'clear'; break
        }
        sendEvent(name: 'naturalGas', value: naturalGas,
          descriptionText: "Gas sensor is ${naturalGas}")
        sendEvent(name: 'alarmLevel', value: alarmState,
          descriptionText: "Alarm level: ${alarmState}")
        logInfo("Gas: naturalGas=${naturalGas}, alarmLevel=${alarmState}")
      }

      // Gas concentration (PPM)
      if (data.ppm != null) {
        Integer ppm = data.ppm as Integer
        sendEvent(name: 'gasConcentration', value: ppm, unit: 'ppm',
          descriptionText: "Gas concentration is ${ppm} ppm")
        logInfo("Gas concentration: ${ppm} ppm")
      }

      // Sensor operational state
      if (data.sensor_state != null) {
        String sensorState = data.sensor_state.toString()
        sendEvent(name: 'sensorState', value: sensorState,
          descriptionText: "Sensor state: ${sensorState}")
        logInfo("Sensor state: ${sensorState}")
      }

      // Self-test state
      if (data.self_test_state != null) {
        String selfTestState = data.self_test_state.toString()
        sendEvent(name: 'selfTestState', value: selfTestState,
          descriptionText: "Self-test state: ${selfTestState}")
        logInfo("Self-test state: ${selfTestState}")
      }

    } else if (key.startsWith('valve:')) {
      // Valve state → valve attribute + valveState
      if (data.state != null) {
        String rawState = data.state.toString()
        sendEvent(name: 'valveState', value: rawState,
          descriptionText: "Valve state: ${rawState}")
        logInfo("Valve state: ${rawState}")

        // Map to Hubitat Valve attribute (only for actionable states)
        String valveAttr = null
        switch (rawState) {
          case 'opened':
          case 'opening':
            valveAttr = 'open'
            break
          case 'closed':
          case 'closing':
          case 'checking':
            valveAttr = 'closed'
            break
          // not_connected, failure — no valve event, only valveState updated
        }
        if (valveAttr) {
          sendEvent(name: 'valve', value: valveAttr,
            descriptionText: "Valve is ${valveAttr}")
          logInfo("Valve: ${valveAttr}")
        }
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
