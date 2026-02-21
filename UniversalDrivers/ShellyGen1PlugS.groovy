/**
 * Shelly Gen1 Plug S
 *
 * Pre-built standalone driver for the Gen 1 Shelly Plug S (SHPLG-S, SHPLG-U1).
 * Provides switch control, power monitoring (power, voltage, energy, computed amperage),
 * internal device temperature measurement, and LED control preferences.
 * Gen 1 devices use HTTP REST action URLs instead of Gen 2 webhooks/scripts.
 * Power monitoring data is obtained via polling (no powermonitoring.js on Gen 1).
 * Commands delegate to the parent app which routes to Gen 1 REST endpoints.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Plug S', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

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

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER, unit:°F/°C
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'defaultState', type: 'enum', title: 'Power-On Default State',
    options: ['restore':'Restore Last', 'off':'Off', 'on':'On'],
    defaultValue: 'restore', required: false
  input name: 'autoOffTime', type: 'decimal', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'autoOnTime', type: 'decimal', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'ledStatusDisable', type: 'bool', title: 'Disable Status LED',
    description: 'Turn off the blue status LED on the Plug S.', defaultValue: false, required: false
  input name: 'ledPowerDisable', type: 'bool', title: 'Disable Power LED',
    description: 'Turn off the red power indicator LED on the Plug S.', defaultValue: false, required: false
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug('installed() called')
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration,
 * then relays switch and device-level settings to the parent app.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
  relaySwitchSettings()
  relayDeviceSettings()
}

/**
 * Gathers switch settings and sends them to the parent app for relay to the device.
 */
private void relaySwitchSettings() {
  Map switchSettings = [:]
  if (settings.defaultState != null) { switchSettings.defaultState = settings.defaultState as String }
  if (settings.autoOffTime != null) { switchSettings.autoOffTime = settings.autoOffTime as BigDecimal }
  if (settings.autoOnTime != null) { switchSettings.autoOnTime = settings.autoOnTime as BigDecimal }
  if (switchSettings) {
    logDebug("Relaying switch settings to parent: ${switchSettings}")
    parent?.componentUpdateSwitchSettings(device, switchSettings)
  }
}

/**
 * Relays device-level settings (LED control) to the parent app for the Gen 1 /settings endpoint.
 * Uses the existing componentUpdateGen1Settings() method in the parent app.
 */
private void relayDeviceSettings() {
  Map deviceSettings = [:]
  if (settings.ledStatusDisable != null) {
    deviceSettings.led_status_disable = settings.ledStatusDisable ? 'true' : 'false'
  }
  if (settings.ledPowerDisable != null) {
    deviceSettings.led_power_disable = settings.ledPowerDisable ? 'true' : 'false'
  }
  if (deviceSettings) {
    logDebug("Relaying device settings to parent: ${deviceSettings}")
    parent?.componentUpdateGen1Settings(device, deviceSettings)
  }
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly device.
 * Gen 1 action URLs fire GET requests with state encoded in the URL path.
 * No POST body handling — Gen 1 devices have no scripting support.
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
 * GET Action Webhooks encode state in the path (e.g., /switch_on/0).
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
 * After handling state change, triggers a refresh to get power monitoring data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'switch_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
      logInfo('Switch state changed to: on')
      parent?.componentRefresh(device)
      break
    case 'switch_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
      logInfo('Switch state changed to: off')
      parent?.componentRefresh(device)
      break

    case 'over_power':
      logWarn('Over-power condition detected!')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Initializes the device driver. Called on install and settings update.
 */
void initialize() {
  logDebug('initialize() called')
}

/**
 * Configures the device driver settings.
 */
void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Refreshes the device state by querying the parent app.
 * App will poll the Gen 1 device via GET /status and send events back.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the switch on by delegating to the parent app.
 */
void on() {
  logDebug('on() called')
  parent?.componentOn(device)
}

/**
 * Turns the switch off by delegating to the parent app.
 */
void off() {
  logDebug('off() called')
  parent?.componentOff(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles switch state, power monitoring values, and internal device temperature.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  // Process switch and power monitoring components
  status.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('switch:') || !(v instanceof Map)) { return }

    Map data = v as Map
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      sendEvent(name: 'switch', value: switchState, descriptionText: "Switch turned ${switchState}")
      logInfo("Switch state: ${switchState}")
    }
    if (data.apower != null) {
      BigDecimal power = data.apower as BigDecimal
      sendEvent(name: 'power', value: power, unit: 'W', descriptionText: "Power is ${power}W")
    }
    if (data.voltage != null) {
      BigDecimal voltage = data.voltage as BigDecimal
      sendEvent(name: 'voltage', value: voltage, unit: 'V', descriptionText: "Voltage is ${voltage}V")
    }
    if (data.current != null) {
      BigDecimal current = data.current as BigDecimal
      sendEvent(name: 'amperage', value: current, unit: 'A', descriptionText: "Current is ${current}A")
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      sendEvent(name: 'energy', value: energyKwh, unit: 'kWh', descriptionText: "Energy is ${energyKwh}kWh")
    }
  }

  // Process internal device temperature (Plug S has an internal temp sensor)
  status.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('temperature:') || !(v instanceof Map)) { return }

    Map data = v as Map
    BigDecimal tempC = data.tC != null ? data.tC as BigDecimal : null
    BigDecimal tempF = data.tF != null ? data.tF as BigDecimal : null
    if (tempC != null || tempF != null) {
      Boolean useCelsius = parent?.isCelciusScale() ?: false
      BigDecimal temp = useCelsius ? tempC : (tempF ?: ((tempC * 9.0 / 5.0) + 32.0))
      String unit = useCelsius ? '°C' : '°F'
      temp = temp.setScale(1, BigDecimal.ROUND_HALF_UP)
      sendEvent(name: 'temperature', value: temp, unit: unit,
        descriptionText: "Internal temperature is ${temp}${unit}")
      logInfo("Internal temperature: ${temp}${unit}")
    }
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



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static Boolean NOCHILDSWITCH = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
