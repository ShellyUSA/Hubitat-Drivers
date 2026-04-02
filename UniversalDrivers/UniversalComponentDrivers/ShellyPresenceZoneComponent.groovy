/**
 * Shelly Autoconf Presence Zone - Component Driver
 *
 * Passive child driver for Shelly Presence G4 zones.
 * Receives all state from the Presence G4 parent driver.
 *
 * Version: 1.0.0
 */

import groovy.transform.Field

metadata {
  definition(name: 'Shelly Autoconf Presence Zone', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'MotionSensor'
    capability 'PresenceSensor'
    capability 'Refresh'

    attribute 'numObjects', 'number'
    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn': 'Warning', 'info': 'Info', 'debug': 'Debug', 'trace': 'Trace'],
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
  parent?.componentPresenceZoneRefresh(device)
}

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
