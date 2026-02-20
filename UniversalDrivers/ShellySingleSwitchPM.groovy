/**
 * Shelly Autoconf Single Switch PM
 *
 * Pre-built standalone driver for single-switch Shelly devices with power monitoring.
 * Examples: Shelly 1PM, Shelly 1PM Mini, Shelly Plug, Shelly Plus Plug S
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Commands delegate to the parent app via componentOn/componentOff.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Switch PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

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
  input name: 'logLevel', type: 'enum', title: 'Logging Level', options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'], defaultValue: 'debug', required: true
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
  input name: 'defaultState', type: 'enum', title: 'Power-On Default State',
    options: ['restore':'Restore Last', 'off':'Off', 'on':'On'],
    defaultValue: 'restore', required: false
  input name: 'autoOffTime', type: 'decimal', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'autoOnTime', type: 'decimal', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Creates PLUGS_UI RGB child device if the Shelly has an LED indicator.
 */
void installed() {
  logDebug("installed() called")
  reconcilePlugsUiChild()
}

/**
 * Called when device settings are saved.
 * Pushes PM reporting interval to Shelly KVS, relays switch settings, and reconciles PLUGS_UI child.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  sendPmReportingIntervalToKVS()
  relaySwitchSettings()
  reconcilePlugsUiChild()
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
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Supports both new discrete dst values (switch_on, switch_off, input_toggle_on,
 * input_toggle_off) and legacy combined dst values (switchmon, input_toggle).
 *
 * @param params The parsed query parameters including dst and optional output/state fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    // New discrete switch webhooks — state is encoded in the dst name
    case 'switch_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
      logInfo('Switch state changed to: on')
      break
    case 'switch_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
      logInfo('Switch state changed to: off')
      break

    // Legacy combined switch webhook — state is in params.output
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}")
        logInfo("Switch state changed to: ${switchState}")
      }
      break

    // New discrete input toggle webhooks — state is encoded in the dst name
    case 'input_toggle_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on (input toggle)')
      logInfo('Switch state changed to: on (input toggle)')
      break
    case 'input_toggle_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off (input toggle)')
      logInfo('Switch state changed to: off (input toggle)')
      break

    // Legacy combined input toggle webhook — state is in params.state
    case 'input_toggle':
      if (params.state != null) {
        String switchState = params.state == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState} (input toggle)")
        logInfo("Switch state changed to: ${switchState} (input toggle)")
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
// ║  Switch Commands                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the switch on by delegating to the parent app.
 */
void on() {
  logDebug("on() called")
  parent?.componentOn(device)
}

/**
 * Turns the switch off by delegating to the parent app.
 */
void off() {
  logDebug("off() called")
  parent?.componentOff(device)
}

/**
 * Parses switch monitoring notifications from Shelly device.
 * Processes JSON with dst:"switchmon" and updates device state.
 * JSON format: [dst:switchmon, result:[switch:0:[id:0, output:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseSwitchmon(Map json) {
  logDebug("parseSwitchmon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseSwitchmon: No result data in JSON")
      return
    }

    // Iterate over switch entries (e.g., "switch:0")
    result.each { key, value ->
      if (key.toString().startsWith('switch:')) {
        if (value instanceof Map) {
          Integer switchId = value.id
          Boolean output = value.output

          if (output != null) {
            String switchState = output ? "on" : "off"
            logInfo("Switch ${switchId} state changed to: ${switchState}")
            sendEvent(name: "switch", value: switchState, descriptionText: "Switch turned ${switchState}")
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseSwitchmon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Resets energy monitoring counters by delegating to the parent app.
 */
void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Monitoring Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  PLUGS_UI LED Management                                     ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Ensures a PLUGS_UI RGB child device exists if this Shelly has an LED indicator.
 * Checks the 'hasPlugsUi' data value set during discovery. If the child doesn't
 * exist yet, creates it with default green color (matching Shelly factory default).
 */
private void reconcilePlugsUiChild() {
  if (device.getDataValue('hasPlugsUi') != 'true') { return }

  String childDni = "${device.deviceNetworkId}-plugsui-rgb".toString()
  com.hubitat.app.DeviceWrapper existing = getChildDevice(childDni)
  if (existing) { return }

  logInfo("Creating PLUGS_UI RGB child device for LED control")
  try {
    com.hubitat.app.DeviceWrapper child = addChildDevice(
      'ShellyUSA', 'Shelly PLUGS_UI RGB', childDni,
      [name: 'Shelly PLUGS_UI RGB', label: "${device.displayName} LED"]
    )
    child.updateDataValue('plugsUiRgb', 'true')

    // Set default state: green at full brightness (Shelly factory default)
    // Compute RGB from hue=33 to stay consistent with stored state
    List<Integer> defaultRgb = hsvToPlugsUiRgb(33, 100)
    child.sendEvent(name: 'switch', value: 'on')
    child.sendEvent(name: 'level', value: 100, unit: '%')
    child.sendEvent(name: 'hue', value: 33)
    child.sendEvent(name: 'saturation', value: 100)
    child.sendEvent(name: 'colorMode', value: 'RGB')

    // Store default color state
    state.plugsUiRgb = defaultRgb
    state.plugsUiBrightness = 100

    // Send initial config to set LED to "switch" mode with default color
    Map config = buildPlugsUiColorConfig(defaultRgb, 100)
    parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)

    logInfo("PLUGS_UI RGB child created: ${child.displayName}")
  } catch (Exception e) {
    logError("Failed to create PLUGS_UI RGB child: ${e.message}")
  }
}

/**
 * Turns the PLUGS_UI LED on with the last-used color and brightness.
 * Sets leds.mode = "switch" with both on/off colors identical.
 *
 * @param childDevice The PLUGS_UI RGB child device
 */
void componentPlugsUiOn(com.hubitat.app.DeviceWrapper childDevice) {
  logDebug("componentPlugsUiOn() called")
  List<Integer> rgb = state.plugsUiRgb ?: [0, 100, 0]
  Integer brightness = state.plugsUiBrightness ?: 100

  Map config = buildPlugsUiColorConfig(rgb, brightness)
  parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)

  sendPlugsUiChildEvent(childDevice, [name: 'switch', value: 'on'])
}

/**
 * Turns the PLUGS_UI LED off by setting leds.mode = "off".
 * This completely disables the LED indicator.
 *
 * @param childDevice The PLUGS_UI RGB child device
 */
void componentPlugsUiOff(com.hubitat.app.DeviceWrapper childDevice) {
  logDebug("componentPlugsUiOff() called")
  Map config = [config: [leds: [mode: 'off']]]
  parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)

  sendPlugsUiChildEvent(childDevice, [name: 'switch', value: 'off'])
}

/**
 * Sets the PLUGS_UI LED color from an HSV color map.
 * Converts HSV to RGB (0-100 scale) and sends PLUGS_UI.SetConfig.
 * Both the on/off LED states are set identically.
 *
 * @param childDevice The PLUGS_UI RGB child device
 * @param colorMap Map with hue (0-100), saturation (0-100), and optionally level (0-100)
 */
void componentPlugsUiSetColor(com.hubitat.app.DeviceWrapper childDevice, Map colorMap) {
  Integer hue = colorMap.hue != null ? colorMap.hue as Integer : 0
  Integer saturation = colorMap.saturation != null ? colorMap.saturation as Integer : 100
  Integer level = colorMap.level != null ? colorMap.level as Integer : (state.plugsUiBrightness ?: 100)
  logDebug("componentPlugsUiSetColor() hue=${hue}, sat=${saturation}, level=${level}")

  // Convert HSV to pure RGB color (brightness handled separately)
  List<Integer> rgb = hsvToPlugsUiRgb(hue, saturation)

  // Store state
  state.plugsUiRgb = rgb
  state.plugsUiBrightness = level

  if (level == 0) {
    componentPlugsUiOff(childDevice)
    return
  }

  // Send config
  Map config = buildPlugsUiColorConfig(rgb, level)
  parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)

  // Update child device attributes
  sendPlugsUiChildEvent(childDevice, [name: 'hue', value: hue])
  sendPlugsUiChildEvent(childDevice, [name: 'saturation', value: saturation, unit: '%'])
  sendPlugsUiChildEvent(childDevice, [name: 'level', value: level, unit: '%'])
  sendPlugsUiChildEvent(childDevice, [name: 'switch', value: 'on'])
  sendPlugsUiChildEvent(childDevice, [name: 'colorMode', value: 'RGB'])

  // Build hex RGB string for the RGB attribute (0-255 scale)
  String hexR = String.format('%02X', Math.round(rgb[0] * 2.55) as Integer)
  String hexG = String.format('%02X', Math.round(rgb[1] * 2.55) as Integer)
  String hexB = String.format('%02X', Math.round(rgb[2] * 2.55) as Integer)
  sendPlugsUiChildEvent(childDevice, [name: 'RGB', value: "${hexR}${hexG}${hexB}"])
}

/**
 * Sets the PLUGS_UI LED brightness level.
 * Level 0 turns the LED off; any other level turns it on with the stored color.
 *
 * @param childDevice The PLUGS_UI RGB child device
 * @param level Brightness percentage (0-100)
 */
void componentPlugsUiSetLevel(com.hubitat.app.DeviceWrapper childDevice, Integer level) {
  logDebug("componentPlugsUiSetLevel() level=${level}")
  if (level <= 0) {
    componentPlugsUiOff(childDevice)
    sendPlugsUiChildEvent(childDevice, [name: 'level', value: 0, unit: '%'])
    return
  }

  state.plugsUiBrightness = level
  List<Integer> rgb = state.plugsUiRgb ?: [0, 100, 0]

  Map config = buildPlugsUiColorConfig(rgb, level)
  parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)

  sendPlugsUiChildEvent(childDevice, [name: 'level', value: level, unit: '%'])
  sendPlugsUiChildEvent(childDevice, [name: 'switch', value: 'on'])
}

/**
 * Configures the PLUGS_UI night mode settings.
 * Sends a partial PLUGS_UI.SetConfig with only the night_mode section.
 *
 * @param childDevice The PLUGS_UI RGB child device
 * @param nightModeConfig Map with enable, brightness, startTime, endTime
 */
void componentPlugsUiSetNightMode(com.hubitat.app.DeviceWrapper childDevice, Map nightModeConfig) {
  logDebug("componentPlugsUiSetNightMode() config=${nightModeConfig}")
  Map config = [config: [leds: [night_mode: [
    enable: nightModeConfig.enable ?: false,
    brightness: nightModeConfig.brightness != null ? nightModeConfig.brightness as Integer : 10,
    active_between: [nightModeConfig.startTime ?: '22:00', nightModeConfig.endTime ?: '06:00']
  ]]]]
  parent?.parentSendCommand(device, 'PLUGS_UI.SetConfig', config)
}

/**
 * Converts Hubitat HSV (hue 0-100, saturation 0-100) to RGB on 0-100 scale.
 * Computed at full value (V=1.0) to produce the pure color; brightness is
 * handled separately by the PLUGS_UI brightness parameter.
 *
 * @param hue Hue value (0-100, where 0=red, 33=green, 67=blue)
 * @param saturation Saturation value (0-100, 0=white, 100=full color)
 * @return List of [red, green, blue] each 0-100
 */
@CompileStatic
private static List<Integer> hsvToPlugsUiRgb(Integer hue, Integer saturation) {
  BigDecimal h = (hue * 3.6)         // 0-100 -> 0-360
  BigDecimal s = saturation / 100.0   // 0-100 -> 0-1
  BigDecimal v = 1.0                  // Full brightness (separate from PLUGS_UI brightness)

  BigDecimal c = v * s
  BigDecimal x = c * (1 - ((h / 60) % 2 - 1).abs())
  BigDecimal m = v - c

  BigDecimal r1, g1, b1
  if (h < 60)       { r1 = c; g1 = x; b1 = 0 }
  else if (h < 120) { r1 = x; g1 = c; b1 = 0 }
  else if (h < 180) { r1 = 0; g1 = c; b1 = x }
  else if (h < 240) { r1 = 0; g1 = x; b1 = c }
  else if (h < 300) { r1 = x; g1 = 0; b1 = c }
  else              { r1 = c; g1 = 0; b1 = x }

  return [
    Math.round((r1 + m) * 100) as Integer,
    Math.round((g1 + m) * 100) as Integer,
    Math.round((b1 + m) * 100) as Integer
  ]
}

/**
 * Builds the nested config map for PLUGS_UI.SetConfig.
 * Sets leds.mode = "switch" with both on/off colors identical so the LED
 * shows the same color regardless of the plug's switch state.
 *
 * @param rgb List of [red, green, blue] values (0-100)
 * @param brightness Brightness percentage (0-100)
 * @return Map suitable as PLUGS_UI.SetConfig params
 */
@CompileStatic
private static Map buildPlugsUiColorConfig(List<Integer> rgb, Integer brightness) {
  Map colorEntry = [rgb: rgb, brightness: brightness]
  return [config: [leds: [
    mode: 'switch',
    colors: ['switch:0': [on: colorEntry, off: colorEntry]]
  ]]]
}

/**
 * Sends an event to the PLUGS_UI RGB child device.
 *
 * @param childDevice The child device to send the event to
 * @param event The event map (name, value, unit, etc.)
 */
private void sendPlugsUiChildEvent(com.hubitat.app.DeviceWrapper childDevice, Map event) {
  childDevice.sendEvent(event)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END PLUGS_UI LED Management                                 ║
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
