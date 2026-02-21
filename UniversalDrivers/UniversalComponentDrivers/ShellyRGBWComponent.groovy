/**
 * Shelly Autoconf RGBW - Component Driver
 *
 * Self-contained component driver for Shelly RGBW (color + white channel) components.
 * Delegates commands to parent driver via componentRGBXxx() pattern.
 * Adds white channel control over the RGB-only driver.
 * Used as a child device in multi-component parent-child architecture.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf RGBW', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'
    capability 'ColorControl'
      //RGB - STRING
      //color - STRING
      //colorName - STRING
      //hue - NUMBER
      //saturation - NUMBER, unit:%
    command 'setWhiteLevel', [
      [name:"White level*", type:"NUMBER", description:"White channel level (0 to 100)"]
    ]
    capability 'Refresh'
    attribute 'lastUpdated', 'string'
    attribute 'whiteLevel', 'number'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true
    input name: 'transitionDuration', type: 'number', title: 'Default Transition (ms, 0=instant)',
      defaultValue: 0, required: false
  }
}

@Field static Boolean COMP = true

void installed() {
  logDebug('installed() called')
  initialize()
}

void updated() {
  logDebug('updated() called')
  initialize()
}

void initialize() {
  logDebug('initialize() called')
}

void on() {
  logDebug('on() called')
  parent?.componentRGBOn(device)
}

void off() {
  logDebug('off() called')
  parent?.componentRGBOff(device)
}

void setLevel(BigDecimal level, BigDecimal duration = null) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentRGBSetLevel(device, level as Integer, duration != null ? (duration * 1000) as Integer : null)
}

void setColor(Map colorMap) {
  logDebug("setColor(${colorMap}) called")
  parent?.componentRGBSetColor(device, colorMap)
}

void setHue(BigDecimal hue) {
  logDebug("setHue(${hue}) called")
  Integer currentSat = device.currentValue('saturation') as Integer ?: 100
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: hue as Integer, saturation: currentSat, level: currentLevel])
}

void setSaturation(BigDecimal saturation) {
  logDebug("setSaturation(${saturation}) called")
  Integer currentHue = device.currentValue('hue') as Integer ?: 0
  Integer currentLevel = device.currentValue('level') as Integer ?: 100
  setColor([hue: currentHue, saturation: saturation as Integer, level: currentLevel])
}

void setWhiteLevel(BigDecimal level) {
  logDebug("setWhiteLevel(${level}) called")
  parent?.componentRGBSetWhiteLevel(device, level as Integer)
}

void startLevelChange(String direction) {
  logDebug("startLevelChange(${direction}) called")
  parent?.componentStartLevelChange(device, direction)
}

void stopLevelChange() {
  logDebug('stopLevelChange() called')
  parent?.componentStopLevelChange(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ═══════════════════════════════════════════════════════════════
// Logging Helpers
// ═══════════════════════════════════════════════════════════════
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
