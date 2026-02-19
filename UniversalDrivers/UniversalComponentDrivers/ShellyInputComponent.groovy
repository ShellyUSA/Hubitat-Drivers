/**
 * Shelly Autoconf Input - Component Driver
 *
 * Generic input component driver with button and count capabilities.
 * Combines push/hold events with sensor count tracking.
 * Used as a child device in multi-component parent-child architecture.
 *
 * Note: For button-only inputs, prefer ShellyInputButtonComponent.
 * For count-only inputs, prefer ShellyInputCountComponent.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf Input', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PushableButton'
    capability 'DoubleTapableButton'
    capability 'HoldableButton'
    capability 'Sensor'
    capability 'Refresh'
    command 'tripleTap'
    attribute 'tripleTapped', 'number'
    attribute 'count', 'number'
    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true
  }
}

@Field static Boolean COMP = true
@Field static Integer BUTTONS = 1

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
  sendEvent(name: 'numberOfButtons', value: BUTTONS)
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

void tripleTap() {
  logDebug('tripleTap() called')
  sendEvent(name: 'tripleTapped', value: 1, isStateChange: true,
    descriptionText: 'Button 1 was triple-tapped')
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
