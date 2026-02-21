/**
 * Shelly Gen1 RGBW2 Color (SHRGBW2)
 *
 * Pre-built standalone driver for the Gen 1 Shelly RGBW2 in color mode.
 * Controls an RGBW LED strip with RGB color channels, a white channel, and
 * gain-based brightness. Does not support color temperature (no CT mode).
 *
 * The RGBW2 uses the {@code /color/0} endpoint for commands, unlike bulbs which
 * use {@code /light/0}. This driver delegates to dedicated parent app component
 * functions that route to the correct endpoint.
 *
 * Commands delegate to the parent app which routes to Gen 1 REST endpoints.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 RGBW2 Color', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ColorControl'
    //Attributes: hue - NUMBER, saturation - NUMBER, color - STRING, RGB - STRING
    //Commands: setColor(colorMap), setHue(hue), setSaturation(saturation)

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    attribute 'colorName', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true

  input name: 'defaultState', type: 'enum', title: 'Power Restore State',
    options: ['on':'On', 'off':'Off', 'last':'Last State'],
    defaultValue: 'last', required: false

  input name: 'autoOnTime', type: 'number', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false

  input name: 'autoOffTime', type: 'number', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
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
  syncColorSettings()
  initialize()
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly RGBW2.
 * Gen 1 action URLs fire GET requests only.
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
 * Routes Gen 1 action URL callbacks for RGBW2 color events.
 * After state change, triggers a refresh to get full color data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'light_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Light turned on')
      logInfo('RGBW2 state changed to: on')
      parent?.componentRefresh(device)
      break
    case 'light_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Light turned off')
      logInfo('RGBW2 state changed to: off')
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

void initialize() {
  logDebug('initialize() called')
}

void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch and Level Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the RGBW2 on via the /color/0 endpoint.
 */
void on() {
  logDebug('on() called')
  parent?.componentColorOn(device)
}

/**
 * Turns the RGBW2 off via the /color/0 endpoint.
 */
void off() {
  logDebug('off() called')
  parent?.componentColorOff(device)
}

/**
 * Sets the RGBW2 brightness (gain) level.
 * In color mode, the RGBW2 uses 'gain' for brightness control.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentSetColorGain(device, level as Integer, duration as Integer)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch and Level Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Color Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the RGBW2 color using a Hubitat HSV color map.
 * Converts to RGB and sends via the /color/0 endpoint with white=0.
 *
 * @param colorMap Map with keys: hue (0-100), saturation (0-100), level (0-100)
 */
void setColor(Map colorMap) {
  logDebug("setColor(${colorMap}) called")
  if (colorMap == null) { return }
  parent?.componentSetColor(device, colorMap)
}

/**
 * Sets the RGBW2 hue, preserving current saturation and level.
 *
 * @param hue Hue value (0-100)
 */
void setHue(BigDecimal hue) {
  logDebug("setHue(${hue}) called")
  Integer currentSat = device.currentValue('saturation') as Integer ?: 100
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: hue, saturation: currentSat, level: currentLevel])
}

/**
 * Sets the RGBW2 saturation, preserving current hue and level.
 *
 * @param saturation Saturation value (0-100)
 */
void setSaturation(BigDecimal saturation) {
  logDebug("setSaturation(${saturation}) called")
  Integer currentHue = device.currentValue('hue') as Integer ?: 0
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: currentHue, saturation: saturation, level: currentLevel])
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Color Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the RGBW2 device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles switch state, RGB color values, gain (brightness), and power monitoring.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  status.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('light:') || !(v instanceof Map)) { return }

    Map data = v as Map

    // Switch state
    if (data.output != null) {
      String switchState = data.output ? 'on' : 'off'
      sendEvent(name: 'switch', value: switchState, descriptionText: "Light turned ${switchState}")
    }

    // Gain is the brightness control in color mode (0-100)
    if (data.gain != null) {
      Integer level = data.gain as Integer
      sendEvent(name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%")
    }

    // Convert RGB to HSV for Hubitat color attributes
    if (data.red != null && data.green != null && data.blue != null) {
      Integer r = data.red as Integer
      Integer g = data.green as Integer
      Integer b = data.blue as Integer

      List hsv = hubitat.helper.ColorUtils.rgbToHSV([r, g, b])
      Integer hue = Math.round(hsv[0] as Double) as Integer
      Integer saturation = Math.round(hsv[1] as Double) as Integer

      sendEvent(name: 'hue', value: hue, descriptionText: "Hue is ${hue}")
      sendEvent(name: 'saturation', value: saturation, descriptionText: "Saturation is ${saturation}")

      String hexColor = String.format('#%02X%02X%02X', r, g, b)
      sendEvent(name: 'color', value: hexColor, descriptionText: "Color is ${hexColor}")

      String rgbString = "${r},${g},${b}"
      sendEvent(name: 'RGB', value: rgbString)

      String colorName = colorNameFromHue(hue)
      sendEvent(name: 'colorName', value: colorName, descriptionText: "Color name is ${colorName}")
    }

    // Power monitoring
    if (data.apower != null) {
      sendEvent(name: 'power', value: data.apower as BigDecimal, unit: 'W')
    }
    if (data.aenergy?.total != null) {
      BigDecimal energyKwh = (data.aenergy.total as BigDecimal) / 1000.0
      sendEvent(name: 'energy', value: energyKwh, unit: 'kWh')
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Settings Sync                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Syncs driver preferences to the Shelly device via parent app.
 * Called from updated() when preferences change.
 * Uses /settings/color/0 endpoint for RGBW2 color mode settings.
 */
private void syncColorSettings() {
  Map colorSettings = [:]
  if (settings.defaultState != null) { colorSettings.defaultState = settings.defaultState }
  if (settings.autoOnTime != null) { colorSettings.autoOnTime = settings.autoOnTime }
  if (settings.autoOffTime != null) { colorSettings.autoOffTime = settings.autoOffTime }
  if (colorSettings) {
    parent?.componentUpdateColorSettings(device, colorSettings)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Settings Sync                                           ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Color Name Helpers                                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns a human-readable color name based on hue value.
 *
 * @param hue Hue value (0-100 Hubitat scale)
 * @return Color name string
 */
@CompileStatic
static String colorNameFromHue(Integer hue) {
  if (hue == null) { return 'Unknown' }
  // Map 0-100 hue to 0-360 for standard color wheel
  Integer hueDeg = Math.round(hue * 3.6f) as Integer
  if (hueDeg < 15)  { return 'Red' }
  if (hueDeg < 45)  { return 'Orange' }
  if (hueDeg < 75)  { return 'Yellow' }
  if (hueDeg < 105) { return 'Chartreuse' }
  if (hueDeg < 135) { return 'Green' }
  if (hueDeg < 165) { return 'Spring' }
  if (hueDeg < 195) { return 'Cyan' }
  if (hueDeg < 225) { return 'Azure' }
  if (hueDeg < 255) { return 'Blue' }
  if (hueDeg < 285) { return 'Violet' }
  if (hueDeg < 315) { return 'Magenta' }
  if (hueDeg < 345) { return 'Rose' }
  return 'Red'
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Color Name Helpers                                      ║
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
