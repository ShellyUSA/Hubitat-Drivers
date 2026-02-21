/**
 * Shelly Autoconf Switch - Component Driver
 *
 * Self-contained component driver for Shelly switch components.
 * Delegates commands to parent app via componentXxx() pattern.
 * Used as a child device in multi-component parent-child architecture.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf Switch', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true
    input name: 'defaultState', type: 'enum', title: 'Power-On Default State',
      options: ['restore':'Restore Last', 'off':'Off', 'on':'On'],
      defaultValue: 'restore', required: false
    input name: 'autoOffTime', type: 'decimal', title: 'Auto-Off Timer (seconds, 0 = disabled)',
      defaultValue: 0, range: '0..86400', required: false
    input name: 'autoOnTime', type: 'decimal', title: 'Auto-On Timer (seconds, 0 = disabled)',
      defaultValue: 0, range: '0..86400', required: false
    input name: 'in_mode', type: 'enum', title: 'Input Button Mode',
      options: ['momentary':'Momentary', 'follow':'Follow', 'flip':'Flip', 'detached':'Detached', 'cycle':'Cycle', 'activate':'Activate'],
      required: false
    input name: 'in_locked', type: 'bool', title: 'Lock Physical Input (disable manual control)',
      defaultValue: false, required: false
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
  relaySwitchSettings()
}

void initialize() {
  logDebug('initialize() called')
}

/**
 * Gathers switch settings and sends them to the parent for relay to the device.
 */
private void relaySwitchSettings() {
  Map switchSettings = [:]
  if (settings.defaultState != null) { switchSettings.defaultState = settings.defaultState as String }
  if (settings.autoOffTime != null) { switchSettings.autoOffTime = settings.autoOffTime as BigDecimal }
  if (settings.autoOnTime != null) { switchSettings.autoOnTime = settings.autoOnTime as BigDecimal }
  if (settings.in_mode != null) { switchSettings.in_mode = settings.in_mode as String }
  if (settings.in_locked != null) { switchSettings.in_locked = settings.in_locked as Boolean }
  if (switchSettings) {
    logDebug("Relaying switch settings to parent: ${switchSettings}")
    parent?.componentUpdateSwitchSettings(device, switchSettings)
  }
}

void on() {
  logDebug('on() called')
  parent?.componentOn(device)
}

void off() {
  logDebug('off() called')
  parent?.componentOff(device)
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
