/**
 * Shelly PLUGS_UI RGB - Component Driver
 *
 * Controls the RGB LED indicator on Shelly plug devices (Plug Gen4, Plus Plug S,
 * Plug S Gen3, Outdoor Plug S, AZ Plug) via PLUGS_UI.SetConfig RPC.
 *
 * Both the "on" and "off" LED states are set identically so the LED always shows
 * the same color regardless of the plug's switch state. Turning the RGB child "off"
 * in Hubitat disables the LED entirely (leds.mode = "off").
 *
 * PLUGS_UI has no queryable status (GetStatus returns {}) and no webhook events,
 * so this driver maintains its own state from Hubitat commands.
 *
 * Delegates commands to parent driver via componentPlugsUiXxx() pattern.
 */
import groovy.transform.CompileStatic
import groovy.transform.Field

metadata {
  definition(name: 'Shelly PLUGS_UI RGB', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light'         // switch - ENUM ["on", "off"]
    capability 'SwitchLevel'   // level - NUMBER, unit:%
    capability 'ColorControl'
      // RGB - STRING
      // color - STRING
      // colorName - STRING
      // hue - NUMBER
      // saturation - NUMBER, unit:%

    attribute 'colorMode', 'string'
    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true

    input name: 'enableNightMode', type: 'bool', title: 'Enable Night Mode',
      description: 'Reduce LED brightness during nighttime hours',
      defaultValue: false, required: false
    input name: 'nightModeBrightness', type: 'number', title: 'Night Mode Brightness (0-100)',
      defaultValue: 10, range: '0..100', required: false
    input name: 'nightModeStartTime', type: 'string', title: 'Night Mode Start Time (HH:MM)',
      defaultValue: '22:00', required: false
    input name: 'nightModeEndTime', type: 'string', title: 'Night Mode End Time (HH:MM)',
      defaultValue: '06:00', required: false
  }
}

@Field static Boolean COMP = true

void installed() {
  logDebug('installed() called')
}

/**
 * Called when device preferences are saved.
 * Sends updated night mode configuration to the parent driver.
 */
void updated() {
  logDebug('updated() called')
  Map nightModeConfig = [
    enable: settings.enableNightMode ?: false,
    brightness: settings.nightModeBrightness != null ? settings.nightModeBrightness as Integer : 10,
    startTime: settings.nightModeStartTime ?: '22:00',
    endTime: settings.nightModeEndTime ?: '06:00'
  ]
  parent?.componentPlugsUiSetNightMode(device, nightModeConfig)
}

/**
 * Turns the LED on with the last-used color and brightness.
 */
void on() {
  logDebug('on() called')
  parent?.componentPlugsUiOn(device)
}

/**
 * Turns the LED off (disables LED entirely via leds.mode = "off").
 */
void off() {
  logDebug('off() called')
  parent?.componentPlugsUiOff(device)
}

/**
 * Sets the LED brightness level.
 * Level 0 turns the LED off; any other level turns it on.
 *
 * @param level Brightness percentage (0-100)
 * @param duration Ignored (PLUGS_UI has no transition support)
 */
void setLevel(BigDecimal level, BigDecimal duration = null) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentPlugsUiSetLevel(device, level as Integer)
}

/**
 * Sets the LED color using hue, saturation, and level.
 *
 * @param colorMap Map with keys: hue (0-100), saturation (0-100), level (0-100)
 */
void setColor(Map colorMap) {
  logDebug("setColor(${colorMap}) called")
  parent?.componentPlugsUiSetColor(device, colorMap)
}

/**
 * Sets only the hue, preserving current saturation and level.
 *
 * @param hue Hue value (0-100)
 */
void setHue(BigDecimal hue) {
  logDebug("setHue(${hue}) called")
  Integer currentSat = device.currentValue('saturation') as Integer ?: 100
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: hue as Integer, saturation: currentSat, level: currentLevel])
}

/**
 * Sets only the saturation, preserving current hue and level.
 *
 * @param saturation Saturation value (0-100)
 */
void setSaturation(BigDecimal saturation) {
  logDebug("setSaturation(${saturation}) called")
  Integer currentHue = device.currentValue('hue') as Integer ?: 0
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: currentHue, saturation: saturation as Integer, level: currentLevel])
}

/**
 * No-op: PLUGS_UI has no queryable status (GetStatus returns {}).
 * State is maintained locally from Hubitat commands.
 */
void refresh() {
  logDebug('refresh() called — no-op for PLUGS_UI (no queryable status)')
}

// ═══════════════════════════════════════════════════════════════
// Logging Helpers
// ═══════════════════════════════════════════════════════════════

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
  return false
}
void logError(message) { log.error "${device.displayName}: ${message}" }
void logWarn(message) { log.warn "${device.displayName}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${device.displayName}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${device.displayName}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${device.displayName}: ${message}" } }
