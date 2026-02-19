/**
 * Shelly Autoconf EM - Component Driver
 *
 * Self-contained component driver for Shelly energy meter phase/channel components.
 * Receives per-phase or per-channel EM data pushed from the parent device.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf EM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'resetEnergyMonitors'
    attribute 'powerFactor', 'number'
    attribute 'reactivePower', 'number' //unit:VAR
    attribute 'energyReturned', 'number' //unit:kWh
    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true
  }
}

@Field static Boolean COMP = true

void installed() {
  logDebug('installed() called')
}

void updated() {
  logDebug('updated() called')
}

void refresh() {
  logDebug('refresh() called')
  parent?.refresh()
}

void resetEnergyMonitors() {
  logDebug('resetEnergyMonitors() called')
  parent?.componentResetEnergyMonitors(device)
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
