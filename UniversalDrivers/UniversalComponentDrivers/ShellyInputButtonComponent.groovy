/**
 * Shelly Autoconf Input Button - Component Driver
 *
 * Self-contained component driver for Shelly input/button components.
 * Physical events are sent by the parent; the button commands below only
 * emit the matching event locally (virtual press from Hubitat rules/dashboards).
 * Used as a child device in multi-component parent-child architecture.
 *
 * Authors: Daniel Winks (original), Ricardo Nogueira Garcia (button commands)
 *
 * Changelog:
 *   1.1.0 (2026-10-02) - Ricardo Nogueira Garcia
 *     - Implement push/doubleTap/hold/release commands (previously declared by the
 *       capabilities but missing, causing MissingMethodException from the device
 *       page and Maker API); add ReleasableButton
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf Input Button', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PushableButton' //numberOfButtons - NUMBER, pushed - NUMBER
    capability 'DoubleTapableButton' //doubleTapped - NUMBER
    capability 'HoldableButton' //held - NUMBER
    capability 'ReleasableButton' //released - NUMBER
    capability 'Refresh'
    command 'tripleTap'
    attribute 'tripleTapped', 'number'
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

void push(BigDecimal button = 1) { emitButton('pushed', 'pushed', button) }
void doubleTap(BigDecimal button = 1) { emitButton('doubleTapped', 'double-tapped', button) }
void hold(BigDecimal button = 1) { emitButton('held', 'held', button) }
void release(BigDecimal button = 1) { emitButton('released', 'released', button) }
void tripleTap(BigDecimal button = 1) { emitButton('tripleTapped', 'triple-tapped', button) }

/**
 * Emits a virtual button event (always button 1: each child is one input).
 *
 * @param eventName Attribute name (pushed, doubleTapped, held, released, tripleTapped)
 * @param verb Text used in the event description
 * @param button Requested button number (ignored; children expose a single button)
 */
private void emitButton(String eventName, String verb, BigDecimal button) {
  logDebug("${eventName} (button ${button}) called")
  sendEvent(name: eventName, value: 1, isStateChange: true, descriptionText: "Button 1 was ${verb}")
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
