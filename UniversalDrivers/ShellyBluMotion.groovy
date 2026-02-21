/**
 * Shelly BLU Motion
 *
 * Standalone driver for Shelly BLU Motion (SBMO-003Z) Bluetooth motion sensor.
 * Reports motion state, illuminance, and battery level. Receives events from
 * the Shelly Device Manager app via BLE gateway relay.
 *
 * NOTE: This driver receives events exclusively from the Shelly Device Manager app
 * via childSendEventHelper(). There is no direct communication with the BLE device.
 * See ShellyDeviceManager.groovy: routeBleEventToChild() and buildBleEvents().
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly BLU Motion', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'MotionSensor'
    //Attributes: motion - ENUM ["active", "inactive"]

    capability 'IlluminanceMeasurement'
    //Attributes: illuminance - NUMBER, unit:lux

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'PresenceSensor'
    //Attributes: presence - ENUM ["present", "not present"]

    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'presenceTimeout', type: 'number', title: 'Presence timeout (minutes)',
    description: 'Mark not present if no BLE data received within this period',
    defaultValue: 60, required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
