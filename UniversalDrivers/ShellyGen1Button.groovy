/**
 * Shelly Gen1 Button
 *
 * Pre-built standalone driver for Gen 1 Shelly Button (Button1, Button2).
 * Battery-powered device that sleeps most of the time.
 * Fires shortpush_url / longpush_url action URLs on button press.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Button', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PushableButton'
    //Attributes: pushed - NUMBER, numberOfButtons - NUMBER
    //Commands: push(buttonNumber)

    capability 'HoldableButton'
    //Attributes: held - NUMBER

    capability 'DoubleTapableButton'
    //Attributes: doubleTapped - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    attribute 'lastUpdated', 'string'
    attribute 'voltage', 'number'
    attribute 'powerSource', 'string'  // "battery" or "usb"
  }
}

preferences {
  // -- Button Behavior (synced to device) --
  input name: 'longpushDurationMs', type: 'number',
    title: 'Long push duration (ms)',
    description: 'Hold duration to trigger long push (800\u20133000 ms). Synced to/from device.',
    range: '800..3000', required: false
  input name: 'multipushTimeBetweenPushesMs', type: 'number',
    title: 'Multi-push interval (ms)',
    description: 'Max time between pushes for multi-push detection (200\u20132000 ms). Synced to/from device.',
    range: '200..2000', required: false

  // -- Device Configuration (synced to device) --
  input name: 'ledStatusDisable', type: 'bool',
    title: 'Disable LED status indicator',
    description: 'Turn off the device status LED. Synced to/from device.',
    defaultValue: false, required: false
  input name: 'remainAwake', type: 'bool',
    title: 'Remain awake (heavy battery drain)',
    description: 'Keep device always awake instead of sleeping. Synced to/from device.',
    defaultValue: false, required: false

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
 * Sets button count and default log level.
 */
void installed() {
  logDebug('installed() called')
  sendEvent(name: 'numberOfButtons', value: 1)
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Called when device settings are saved.
 * Sets button count, default log level, and relays settings to the device.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  sendEvent(name: 'numberOfButtons', value: 1)
  if (!settings.logLevel) {
    device.updateSetting('logLevel', 'debug')
  }
  relayDeviceSettings()
}

/**
 * Re-initializes the device by clearing the settings sync flag
 * so the next refresh re-reads configuration from the physical device.
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
 * Gathers device-side settings and sends them to the parent app for
 * relay to the Shelly device via GET /settings.
 * Only sends settings that have been configured (non-null).
 */
private void relayDeviceSettings() {
  Map settingsMap = [:]
  if (settings.longpushDurationMs != null) {
    settingsMap.longpush_duration_ms = (settings.longpushDurationMs as Integer).toString()
  }
  if (settings.multipushTimeBetweenPushesMs != null) {
    settingsMap.multipush_time_between_pushes_ms = (settings.multipushTimeBetweenPushesMs as Integer).toString()
  }
  if (settings.ledStatusDisable != null) {
    settingsMap.led_status_disable = settings.ledStatusDisable ? 'true' : 'false'
  }
  if (settings.remainAwake != null) {
    settingsMap.remain_awake = settings.remainAwake ? 'true' : 'false'
  }
  if (settingsMap) {
    logDebug("Relaying device settings to parent: ${settingsMap}")
    parent?.componentUpdateGen1Settings(device, settingsMap)
  }
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
 * GET Action Webhooks encode state in the path (e.g., /button_press/0).
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
 * Routes Gen 1 action URL callbacks for button events.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'input_short':
      sendEvent(name: 'pushed', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was pushed')
      logInfo('Button pushed')
      break
    case 'input_long':
      sendEvent(name: 'held', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was held')
      logInfo('Button held')
      break
    case 'input_double':
      sendEvent(name: 'doubleTapped', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was double-tapped')
      logInfo('Button double-tapped')
      break
    case 'input_triple':
      sendEvent(name: 'pushed', value: 3, isStateChange: true,
        descriptionText: 'Button 1 was triple-pushed')
      logInfo('Button triple-pushed')
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))

  // Device is awake right now — poll battery if stale
  pollBatteryIfStale()
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Button Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sends push command programmatically.
 *
 * @param buttonNumber The button number to push (always 1 for single button)
 */
void push(BigDecimal buttonNumber) {
  sendEvent(name: 'pushed', value: buttonNumber as Integer, isStateChange: true,
    descriptionText: "Button ${buttonNumber} was pushed")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Button Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
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

    if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
        logInfo("Battery: ${battery}%")
        state.lastBatteryUpdate = now()
      }
      if (data.voltage != null) {
        BigDecimal voltage = data.voltage as BigDecimal
        sendEvent(name: 'voltage', value: voltage, unit: 'V',
          descriptionText: "Battery voltage is ${voltage}V")
        logInfo("Voltage: ${voltage}V")
      }
      if (data.charger != null) {
        String source = data.charger ? 'usb' : 'battery'
        sendEvent(name: 'powerSource', value: source,
          descriptionText: "Power source is ${source}")
        logInfo("Power source: ${source}")
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Battery Polling on Wake                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/** Minimum interval between battery polls (24 hours in milliseconds). */
@Field static final Long BATTERY_POLL_INTERVAL_MS = 24 * 60 * 60 * 1000L

/**
 * Polls the device for battery status if the last update is stale (>24 hours).
 * Called immediately after processing a button webhook while the device is still awake.
 * Gen1 Button devices sleep most of the time — this brief wake window after a button
 * press is the only opportunity to query {@code GET /status} for battery data.
 */
private void pollBatteryIfStale() {
  Long lastPoll = state.lastBatteryUpdate as Long ?: 0L
  if (now() - lastPoll < BATTERY_POLL_INTERVAL_MS) {
    logTrace("Battery poll skipped — last update ${((now() - lastPoll) / 3600000).setScale(1, BigDecimal.ROUND_HALF_UP)}h ago")
    return
  }

  String ipAddress = device.getDataValue('ipAddress')
  if (!ipAddress) {
    logDebug('pollBatteryIfStale: no IP address stored for device')
    return
  }

  logDebug("Polling battery status from ${ipAddress}/status")
  // Mark optimistically to prevent duplicate polls from rapid button presses
  state.lastBatteryUpdate = now()
  asynchttpGet('handleBatteryPollResponse', [uri: "http://${ipAddress}/status"])
}

/**
 * Async callback for the battery status poll.
 * Parses the response and updates battery events.
 *
 * @param response The async HTTP response
 * @param data Optional callback data (unused)
 */
void handleBatteryPollResponse(hubitat.scheduling.AsyncResponse response, Map data) {
  if (response.status != 200) {
    logDebug("handleBatteryPollResponse: HTTP ${response.status} — device may have gone back to sleep")
    return
  }
  try {
    Map statusData = new groovy.json.JsonSlurper().parseText(response.data) as Map
    parseBatteryStatus(statusData)
  } catch (Exception e) {
    logDebug("handleBatteryPollResponse: parse error — ${e.message}")
  }
}

/**
 * Parses battery data from a raw Gen 1 {@code /status} response.
 * Extracts {@code bat.value}, {@code bat.voltage}, and {@code charger} fields.
 *
 * @param statusData The raw Gen 1 /status response map
 */
private void parseBatteryStatus(Map statusData) {
  Map batData = statusData?.bat as Map
  if (batData?.value != null) {
    Integer battery = batData.value as Integer
    sendEvent(name: 'battery', value: battery, unit: '%',
      descriptionText: "Battery is ${battery}%")
    logInfo("Battery: ${battery}%")
  }
  if (batData?.voltage != null) {
    BigDecimal voltage = batData.voltage as BigDecimal
    sendEvent(name: 'voltage', value: voltage, unit: 'V',
      descriptionText: "Battery voltage is ${voltage}V")
    logInfo("Voltage: ${voltage}V")
  }
  if (statusData?.containsKey('charger')) {
    String source = statusData.charger ? 'usb' : 'battery'
    sendEvent(name: 'powerSource', value: source,
      descriptionText: "Power source is ${source}")
    logInfo("Power source: ${source}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Battery Polling on Wake                                  ║
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
