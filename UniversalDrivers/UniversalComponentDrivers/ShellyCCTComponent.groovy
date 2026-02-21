/**
 * Shelly Autoconf CCT - Component Driver
 *
 * Self-contained component driver for Shelly CCT (color temperature) components.
 * Delegates commands to parent via componentXxx() pattern.
 * Used as a child device in multi-component parent-child architecture (e.g., 2x CCT parent).
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf CCT', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'
    capability 'ColorTemperature'
    //Attributes: colorTemperature - NUMBER, colorName - STRING
    //Commands: setColorTemperature(colortemperature, level, transitionTime)
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
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
  parent?.componentCctOn(device)
}

void off() {
  logDebug('off() called')
  parent?.componentCctOff(device)
}

void setLevel(BigDecimal level, BigDecimal duration = null) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentCctSetLevel(device, level as Integer, duration != null ? (duration * 1000) as Integer : null)
}

void setColorTemperature(BigDecimal colorTemp, BigDecimal level = null, BigDecimal transitionTime = null) {
  logDebug("setColorTemperature(${colorTemp}, ${level}, ${transitionTime}) called")
  parent?.componentSetColorTemperature(device, colorTemp as Integer, level != null ? level as Integer : null, transitionTime)
}

void startLevelChange(String direction) {
  logDebug("startLevelChange(${direction}) called")
  parent?.componentCctStartLevelChange(device, direction)
}

void stopLevelChange() {
  logDebug('stopLevelChange() called')
  parent?.componentCctStopLevelChange(device)
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
