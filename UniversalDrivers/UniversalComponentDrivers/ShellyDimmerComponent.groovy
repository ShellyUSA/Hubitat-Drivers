/**
 * Shelly Autoconf Dimmer - Component Driver
 *
 * Self-contained component driver for Shelly light/dimmer components.
 * Delegates commands to parent app via componentXxx() pattern.
 * Used as a child device in multi-component parent-child architecture.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'
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
  parent?.componentLightOn(device)
}

void off() {
  logDebug('off() called')
  parent?.componentLightOff(device)
}

void setLevel(BigDecimal level, BigDecimal duration = null) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentSetLevel(device, level as Integer, duration != null ? (duration * 1000) as Integer : null)
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
  else if (messageLevel == 'warn') { return settings.logLevel == 'warn' }
  else if (messageLevel == 'info') { return ['warn', 'info'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['warn', 'info', 'debug'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  return false
}
void logError(message) { log.error "${device.displayName}: ${message}" }
void logWarn(message) { log.warn "${device.displayName}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${device.displayName}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${device.displayName}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${device.displayName}: ${message}" } }
