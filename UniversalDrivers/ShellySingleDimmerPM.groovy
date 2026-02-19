/**
 * Shelly Autoconf Single Dimmer PM
 *
 * Pre-built standalone driver for single-dimmer Shelly Gen 2/3 devices with
 * power monitoring.
 * Examples: Shelly Dimmer Gen3 (S3DM-0010WW), Shelly Plus Wall Dimmer,
 *           Shelly Dimmer 0/1-10V PM Gen3 (S3DM-0A101WW)
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Commands delegate to the parent app via componentLightOn/Off,
 * componentSetLevel, and componentStartLevelChange/stopLevelChange.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Dimmer PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'SwitchLevel'
    //Attributes: level - NUMBER, unit:%
    //Commands: setLevel(level, duration)

    capability 'ChangeLevel'
    //Commands: startLevelChange(direction), stopLevelChange()

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
    //Attributes: voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    command 'resetEnergyMonitors'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
  input name: 'defaultState', type: 'enum', title: 'Power-On Default State',
    options: ['restore':'Restore Last', 'off':'Off', 'on':'On'],
    defaultValue: 'restore', required: false
  input name: 'autoOffTime', type: 'decimal', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'autoOnTime', type: 'decimal', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'transitionDuration', type: 'decimal', title: 'Default Transition Duration (seconds, 0 = instant)',
    defaultValue: 0, range: '0..300', required: false
  input name: 'minBrightnessOnToggle', type: 'number', title: 'Minimum Brightness On Toggle (0-100)',
    defaultValue: 0, range: '0..100', required: false
  input name: 'nightModeEnable', type: 'bool', title: 'Enable Night Mode Brightness Cap',
    defaultValue: false, required: false
  input name: 'nightModeBrightness', type: 'number', title: 'Night Mode Brightness Limit (0-100)',
    defaultValue: 50, range: '0..100', required: false
  input name: 'buttonFadeRate', type: 'number', title: 'Button Fade Rate (1=slow, 5=fast)',
    defaultValue: 3, range: '1..5', required: false
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug("installed() called")
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration,
 * then relays light settings and PM reporting interval to the device.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
  sendPmReportingIntervalToKVS()
  relayLightSettings()
}

/**
 * Gathers light/dimmer settings and sends them to the parent app for relay
 * to the Shelly device via Light.SetConfig RPC.
 */
private void relayLightSettings() {
  Map lightSettings = [:]
  if (settings.defaultState != null) { lightSettings.defaultState = settings.defaultState as String }
  if (settings.autoOffTime != null) { lightSettings.autoOffTime = settings.autoOffTime as BigDecimal }
  if (settings.autoOnTime != null) { lightSettings.autoOnTime = settings.autoOnTime as BigDecimal }
  if (settings.transitionDuration != null) { lightSettings.transitionDuration = settings.transitionDuration as BigDecimal }
  if (settings.minBrightnessOnToggle != null) { lightSettings.minBrightnessOnToggle = settings.minBrightnessOnToggle as Integer }
  if (settings.nightModeEnable != null) { lightSettings.nightModeEnable = settings.nightModeEnable as Boolean }
  if (settings.nightModeBrightness != null) { lightSettings.nightModeBrightness = settings.nightModeBrightness as Integer }
  if (settings.buttonFadeRate != null) { lightSettings.buttonFadeRate = settings.buttonFadeRate as Integer }
  if (lightSettings) {
    logDebug("Relaying light settings to parent: ${lightSettings}")
    parent?.componentUpdateLightSettings(device, lightSettings)
  }
}

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
    checkAndUpdateSourceIp(msg)

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
 * Routes parsed webhook parameters to appropriate event handlers.
 * Supports both discrete dst values (light_on, light_off, input_toggle_on,
 * input_toggle_off) and combined dst values (lightmon, input_toggle).
 *
 * @param params The parsed parameters including dst and optional output/brightness fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    // Discrete light webhooks — state is encoded in the dst name
    case 'light_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Light turned on')
      logInfo('Light state changed to: on')
      break
    case 'light_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Light turned off')
      logInfo('Light state changed to: off')
      break

    // Combined light monitoring webhook — output + brightness in params
    case 'lightmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Light turned ${switchState}")
        logInfo("Light state changed to: ${switchState}")
      }
      if (params.brightness != null) {
        Integer level = params.brightness as Integer
        sendEvent(name: 'level', value: level, unit: '%',
          descriptionText: "Brightness is ${level}%")
        logInfo("Brightness level changed to: ${level}%")
      }
      break

    // Discrete input toggle webhooks — state is encoded in the dst name
    case 'input_toggle_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Light turned on (input toggle)')
      logInfo('Light state changed to: on (input toggle)')
      break
    case 'input_toggle_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Light turned off (input toggle)')
      logInfo('Light state changed to: off (input toggle)')
      break

    // Legacy combined input toggle webhook — state is in params.state
    case 'input_toggle':
      if (params.state != null) {
        String switchState = params.state == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Light turned ${switchState} (input toggle)")
        logInfo("Light state changed to: ${switchState} (input toggle)")
      }
      break

    // Power monitoring via GET (from powermonitoring.js)
    case 'powermon':
      if (params.voltage != null) {
        BigDecimal voltage = params.voltage as BigDecimal
        sendEvent(name: 'voltage', value: voltage, unit: 'V',
          descriptionText: "Voltage is ${voltage}V")
        logDebug("Voltage: ${voltage}V")
      }
      if (params.current != null) {
        BigDecimal current = params.current as BigDecimal
        sendEvent(name: 'amperage', value: current, unit: 'A',
          descriptionText: "Current is ${current}A")
        logDebug("Current: ${current}A")
      }
      if (params.apower != null) {
        BigDecimal power = params.apower as BigDecimal
        sendEvent(name: 'power', value: power, unit: 'W',
          descriptionText: "Power is ${power}W")
        logDebug("Power: ${power}W")
      }
      if (params.aenergy != null) {
        BigDecimal energyWh = params.aenergy as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        sendEvent(name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy is ${energyKwh}kWh")
        logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
      }
      if (params.freq != null) {
        BigDecimal freq = params.freq as BigDecimal
        sendEvent(name: 'frequency', value: freq, unit: 'Hz',
          descriptionText: "Frequency is ${freq}Hz")
      }
      logInfo("Power monitoring updated: ${params.apower ?: 0}W, ${params.voltage ?: 0}V, ${params.current ?: 0}A")
      break

    case 'ble':
      // Fallback: forward BLE data to app if handlePostWebhook intercept was missed
      logDebug('BLE relay received via routeWebhookParams, forwarding to app')
      parent?.handleBleRelay(device, params)
      break

    default:
      logDebug("routeWebhookParams: unhandled dst=${params.dst}")
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
  logDebug("initialize() called")
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 */
void configure() {
  logDebug("configure() called")
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
  sendPmReportingIntervalToKVS()
}

/**
 * Sends the PM reporting interval setting to the device KVS via the parent app.
 */
private void sendPmReportingIntervalToKVS() {
  Integer interval = settings?.pmReportingInterval != null ? settings.pmReportingInterval as Integer : 60
  parent?.componentWriteKvsToDevice(device, 'hubitat_sdm_pm_ri', interval)
}

/**
 * Refreshes the device state by querying the parent app.
 */
void refresh() {
  logDebug("refresh() called")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Light and Level Commands                                    ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the light on by delegating to the parent app.
 */
void on() {
  logDebug("on() called")
  parent?.componentLightOn(device)
}

/**
 * Turns the light off by delegating to the parent app.
 */
void off() {
  logDebug("off() called")
  parent?.componentLightOff(device)
}

/**
 * Sets the dimmer brightness level with optional transition duration.
 * Delegates to parent app which sends Light.Set RPC with brightness and
 * transition parameters.
 *
 * @param level Brightness level (0-100)
 * @param duration Transition time in seconds (optional, default 0)
 */
void setLevel(BigDecimal level, BigDecimal duration = 0) {
  logDebug("setLevel(${level}, ${duration}) called")
  Integer durationMs = duration != null ? (duration * 1000) as Integer : null
  parent?.componentSetLevel(device, level as Integer, durationMs)
}

/**
 * Starts a gradual level change in the specified direction.
 * Delegates to parent app which sends Light.DimUp or Light.DimDown RPC.
 *
 * @param direction Either "up" or "down"
 */
void startLevelChange(String direction) {
  logDebug("startLevelChange(${direction}) called")
  parent?.componentStartLevelChange(device, direction)
}

/**
 * Stops an in-progress level change.
 * Delegates to parent app which sends Light.DimStop RPC.
 */
void stopLevelChange() {
  logDebug("stopLevelChange() called")
  parent?.componentStopLevelChange(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Light and Level Commands                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Resets energy monitoring counters by delegating to the parent app.
 * Calls Light.ResetCounters RPC on the Shelly device.
 */
void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Monitoring Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
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

/**
 * Formats a Map as pretty-printed JSON.
 *
 * @param jsonInput The map to format
 * @return Pretty-printed JSON string
 */
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
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
