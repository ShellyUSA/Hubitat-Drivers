/**
 * Shelly Gen1 Bulb (SHBLB-1)
 *
 * Pre-built standalone driver for the Gen 1 Shelly Bulb RGBW device.
 * Supports two operating modes:
 * <ul>
 *   <li><b>Color mode</b>: RGB + white channel with gain-based brightness</li>
 *   <li><b>White mode</b>: Color temperature (3000–6500K) with brightness</li>
 * </ul>
 *
 * Commands delegate to the parent app which routes to Gen 1 REST endpoints.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 Bulb', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ColorControl'
    //Attributes: hue - NUMBER, saturation - NUMBER, color - STRING, RGB - STRING
    //Commands: setColor(colorMap), setHue(hue), setSaturation(saturation)

    capability 'ColorTemperature'
    //Attributes: colorTemperature - NUMBER
    //Commands: setColorTemperature(colorTemperature, level, transitionTime)

    capability 'ColorMode'
    //Attributes: colorMode - ENUM ["CT", "RGB"]

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

  input name: 'colorTempMin', type: 'number', title: 'Minimum Color Temperature (K)',
    defaultValue: 3000, range: '2700..6500', required: false

  input name: 'colorTempMax', type: 'number', title: 'Maximum Color Temperature (K)',
    defaultValue: 6500, range: '2700..6500', required: false
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
  syncLightSettings()
  initialize()
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly Bulb.
 * Gen 1 action URLs fire GET requests only.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

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
 * GET Action Webhooks encode state in the path (e.g., /light_on/0).
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
 * Routes Gen 1 action URL callbacks for bulb events.
 * After state change, triggers a refresh to get full light data.
 *
 * @param params Map with dst and cid from the action URL path
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'light_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Light turned on')
      logInfo('Light state changed to: on')
      parent?.componentRefresh(device)
      break
    case 'light_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Light turned off')
      logInfo('Light state changed to: off')
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
 * Turns the bulb on by delegating to the parent app via /light/ endpoint.
 */
void on() {
  logDebug('on() called')
  parent?.componentLightOn(device)
}

/**
 * Turns the bulb off by delegating to the parent app via /light/ endpoint.
 */
void off() {
  logDebug('off() called')
  parent?.componentLightOff(device)
}

/**
 * Sets the bulb brightness level.
 * In color mode this adjusts gain; in white mode it adjusts brightness.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentSetLevel(device, level as Integer, duration as Integer)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch and Level Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Color and Color Temperature Commands                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the bulb color using a Hubitat HSV color map.
 * Switches the bulb to color mode and sends RGB + gain values.
 *
 * @param colorMap Map with keys: hue (0-100), saturation (0-100), level (0-100)
 */
void setColor(Map colorMap) {
  logDebug("setColor(${colorMap}) called")
  if (colorMap == null) { return }
  parent?.componentSetColor(device, colorMap)
}

/**
 * Sets the bulb hue, preserving current saturation and level.
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
 * Sets the bulb saturation, preserving current hue and level.
 *
 * @param saturation Saturation value (0-100)
 */
void setSaturation(BigDecimal saturation) {
  logDebug("setSaturation(${saturation}) called")
  Integer currentHue = device.currentValue('hue') as Integer ?: 0
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: currentHue, saturation: saturation, level: currentLevel])
}

/**
 * Sets the bulb to white mode at the specified color temperature.
 * Optionally sets brightness level and transition time.
 *
 * @param colorTemp Color temperature in Kelvin (e.g. 3000-6500)
 * @param level Optional brightness level (0-100)
 * @param transitionTime Optional transition time in seconds
 */
void setColorTemperature(BigDecimal colorTemp, BigDecimal level = null, BigDecimal transitionTime = null) {
  logDebug("setColorTemperature(${colorTemp}, ${level}, ${transitionTime}) called")
  if (colorTemp == null) { return }

  // Clamp to configured range
  Integer ctMin = (settings.colorTempMin as Integer) ?: 3000
  Integer ctMax = (settings.colorTempMax as Integer) ?: 6500
  BigDecimal clampedTemp = Math.max(ctMin, Math.min(ctMax, colorTemp.intValue()))

  parent?.componentSetColorTemperature(device, clampedTemp, level, transitionTime)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Color and Color Temperature Commands                    ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the bulb device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 * Handles light state, brightness, color/CT mode, RGB values, and power monitoring.
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

    // Color mode detection
    String shellyMode = data.mode?.toString()
    if (shellyMode == 'color') {
      sendEvent(name: 'colorMode', value: 'RGB', descriptionText: 'Color mode: RGB')
      distributeColorModeStatus(data)
    } else if (shellyMode == 'white') {
      sendEvent(name: 'colorMode', value: 'CT', descriptionText: 'Color mode: CT')
      distributeWhiteModeStatus(data)
    } else {
      // Fallback: treat as white mode if no mode specified
      if (data.brightness != null) {
        Integer level = data.brightness as Integer
        sendEvent(name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%")
      }
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

/**
 * Distributes status when bulb is in color (RGB) mode.
 * Converts Shelly RGB (0-255) to Hubitat HSV (hue 0-100, saturation 0-100).
 *
 * @param data Normalized light component data map
 */
private void distributeColorModeStatus(Map data) {
  // In color mode, Shelly uses 'gain' for brightness (0-100)
  if (data.gain != null) {
    Integer level = data.gain as Integer
    sendEvent(name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%")
  }

  // Convert RGB to HSV for Hubitat
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
}

/**
 * Distributes status when bulb is in white (CT) mode.
 *
 * @param data Normalized light component data map
 */
private void distributeWhiteModeStatus(Map data) {
  // In white mode, Shelly uses 'brightness' for level (0-100)
  if (data.brightness != null) {
    Integer level = data.brightness as Integer
    sendEvent(name: 'level', value: level, unit: '%', descriptionText: "Level is ${level}%")
  }

  // Color temperature
  if (data.temp != null) {
    Integer ct = data.temp as Integer
    sendEvent(name: 'colorTemperature', value: ct, unit: 'K', descriptionText: "Color temperature is ${ct}K")

    String ctName = colorNameFromTemp(ct)
    sendEvent(name: 'colorName', value: ctName, descriptionText: "Color name is ${ctName}")
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
 */
private void syncLightSettings() {
  Map lightSettings = [:]
  if (settings.defaultState != null) { lightSettings.defaultState = settings.defaultState }
  if (settings.autoOnTime != null) { lightSettings.autoOnTime = settings.autoOnTime }
  if (settings.autoOffTime != null) { lightSettings.autoOffTime = settings.autoOffTime }
  if (lightSettings) {
    parent?.componentUpdateLightSettings(device, lightSettings)
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

/**
 * Returns a human-readable color name based on color temperature.
 *
 * @param temp Color temperature in Kelvin
 * @return Color temperature name string
 */
@CompileStatic
static String colorNameFromTemp(Integer temp) {
  if (temp == null) { return 'Unknown' }
  if (temp < 3000) { return 'Warm White' }
  if (temp < 4000) { return 'Soft White' }
  if (temp < 5000) { return 'Neutral White' }
  if (temp < 5500) { return 'Daylight' }
  return 'Cool White'
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
