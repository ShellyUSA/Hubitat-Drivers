/**
 * Version: 1.0.0
 */

metadata {
  definition (name: 'Shelly Single Switch PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'VoltageMeasurement'

    //Attributes: voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    command 'resetEnergyMonitors'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level', options: ['trace':'Trace','debug':'Debug','info':'Info','warn':'Warning'], defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Capability Commands                                  ║
// ╚══════════════════════════════════════════════════════════════╝
void on() {
  logDebug("on() called")
  // Implement the logic to turn the switch on
  sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
}

void off() {
  logDebug("off() called")
  // Implement the logic to turn the switch off
  sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
}

void refresh() {
  logDebug("refresh() called")
  // Implement the logic to refresh the device state
}

void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  // Implement the logic to reset energy monitors
}

void initialize() {
  logDebug("initialize() called")
}

void configure() {
  logDebug("configure() called")
  if(!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Capability Commands                              ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                           ║
// ╚══════════════════════════════════════════════════════════════╝
void installed() {
  logDebug("installed() called")
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                       ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝
private Boolean shouldLogLevel(String messageLevel) {
  if(messageLevel == 'error') {
    return true
  } else if(messageLevel == 'warn') {
    return settings.logLevel == 'warn'
  } else if(messageLevel == 'info') {
    return ['warn', 'info'].contains(settings.logLevel)
  } else if(messageLevel == 'debug') {
    return ['warn', 'info', 'debug'].contains(settings.logLevel)
  } else if(messageLevel == 'trace') {
    return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel)
  }
}
void logError(message) {log.error "${loggingLabel()}: ${message}"}
void logWarn(message) { log.warn "${loggingLabel()}: ${message}"}
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    String prettyJson = prettyJson(message)
    logTrace(prettyJson)
  }
}

@CompileStatic
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

@CompileStatic
void logInfoJson(Map message) {
  String prettyJson = prettyJson(message)
  logInfo(prettyJson)
}

String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝




// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field

@Field static Boolean NOCHILDSWITCH = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝