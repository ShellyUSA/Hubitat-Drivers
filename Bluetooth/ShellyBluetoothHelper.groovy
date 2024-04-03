definition(
  name: 'Shelly Bluetooth Helper',
  namespace: 'ShellyUSA',
  author: 'Daniel Winks',
  category: 'HTTP',
  description: '',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: true,
  iconX3Url: '',
  singleThreaded: false,
  importUrl: ''
)

preferences { page (name: 'mainPage', title: 'Shelly Bluetooth Helper')}

Map mainPage() {
  dynamicPage(
    name: 'mainPage', title: '<h1>Shelly Bluetooth Helper</h1>', install: true, uninstall: true) {
    section('Logging') {
      input ('logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true)
      input ('debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false)
    }
    section() {label title: 'Enter a name for this app instance', required: false}
  }
}

void initialize() {configure()}
void configure() {
  runEvery30Minutes('configure')
  subscribe(location, "shellyBLEButtonPushedEvents", "shellyButtonPushedEventsHandler")
  subscribe(location, "shellyBLEButtonHeldEvents", "shellyButtonHeldEventsHandler")
  subscribe(location, "shellyBLEBatteryEvents", "shellyBatteryEventsHandler")
  subscribe(location, "shellyBLEIlluminanceEvents", "shellyBLEIlluminanceEventsHandler")
  subscribe(location, "shellyBLERotationEvents", "shellyBLERotationEventsHandler")
  subscribe(location, "shellyBLEWindowEvents", "shellyBLEWindowEventsHandler")
  subscribe(location, "shellyBLEMotionEvents", "shellyBLEMotionEventsHandler")
  subscribe(location, "shellyBLEButtonPresenceEvents", "shellyBLEButtonPresenceEventsHandler")
}

void shellyButtonPushedEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  if(!buttonPushedRecently(macAddress)) {
    try{
      sendEvent(macAddress, [name: 'pushed', value: evt.getValue() as Integer, isStateChange: true])
    } catch(e) {
      logWarn("No device found for DNI/MAC address: ${macAddress}")
    }
  } else {
    runIn(1, 'removeMacFromRecentlyPushedList', [data:[mac: macAddress]])
  }
}

Boolean buttonPushedRecently(String macAddress) {
  if(state.buttonPushedRecently == null) {
    state.buttonPushedRecently = new ArrayList<String>()
  }
  if(state.buttonPushedRecently.contains(macAddress)) {
    return true
  } else {
    ArrayList<String> b = state.buttonPushedRecently
    b.add(macAddress)
    state.buttonPushedRecently = b
    runIn(1, 'removeMacFromRecentlyPushedList', [data:[mac: macAddress]])
    return false
  }
}

void removeMacFromRecentlyPushedList(Map data = null) {
  if(data != null && data.mac != null && state.buttonPushedRecently != null) {
    ArrayList<String> b = state.buttonPushedRecently
    b.remove(data.mac)
    state.buttonPushedRecently = b
  }
}

void shellyButtonHeldEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  if(!buttonHeldRecently(macAddress)) {
    try{
      sendEvent(macAddress, [name: 'held', value: evt.getValue() as Integer, isStateChange: true])
    } catch(e) {
      logWarn("No device found for DNI/MAC address: ${macAddress}")
    }
  } else {
    runIn(1, 'removeMacFromRecentlyHeldList', [data:[mac: macAddress]])
  }
}

Boolean buttonHeldRecently(String macAddress) {
  if(state.buttonHeldRecently == null) {
    state.buttonHeldRecently = new ArrayList<String>()
  }
  if(state.buttonHeldRecently.contains(macAddress)) {
    return true
  } else {
    ArrayList<String> b = state.buttonHeldRecently
    b.add(macAddress)
    state.buttonHeldRecently = b
    runIn(1, 'removeMacFromRecentlyHeldList', [data:[mac: macAddress]])
    return false
  }
}

void removeMacFromRecentlyHeldList(Map data = null) {
  if(data != null && data.mac != null && state.buttonHeldRecently != null) {
    ArrayList<String> b = state.buttonHeldRecently
    b.remove(data.mac)
    state.buttonHeldRecently = b
  }
}

void shellyBatteryEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'battery', value: evt.getValue() as Integer])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}

void shellyBLEIlluminanceEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'illuminance', value: evt.getValue() as Integer])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}

void shellyBLERotationEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'tilt', value: evt.getValue()])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}

void shellyBLEWindowEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'contact', value: (evt.getValue() as Integer) == 0 ? 'closed' : 'open'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}

void shellyBLEMotionEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'motion', value: (evt.getValue() as Integer) == 0 ? 'inactive' : 'active'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}

void shellyBLEButtonPresenceEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'presence', value: 'present', isStateChange: true])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}")
  }
}
// =============================================================================
// Logging Helpers
// =============================================================================
String loggingLabel() {
  if(device) {return "${device.label ?: device.name }"}
  if(app) {return "${app.label ?: app.name }"}
}

void logException(message) {if (settings.logEnable == true) {log.exception "${loggingLabel()}: ${message}"}}
void logError(message) {if (settings.logEnable == true) {log.error "${loggingLabel()}: ${message}"}}
void logWarn(message) {if (settings.logEnable == true) {log.warn "${loggingLabel()}: ${message}"}}
void logInfo(message) {if (settings.logEnable == true) {log.info "${loggingLabel()}: ${message}"}}
void logDebug(message) {if (settings.logEnable == true) {log.debug "${loggingLabel()}: ${message}"}}
void logTrace(message) {if (settings.logEnable == true) {log.trace "${loggingLabel()}: ${message}"}}

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

void logJson(Map message) {
  if (settings.logEnable && settings.traceLogEnable) {
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

void logsOff() {
  if (device) {
    logWarn("Logging disabled for ${device}")
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Logging disabled for ${app}")
    app.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
}

void debugLogsOff() {
  if (device) {
    logWarn("Debug logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Debug logging disabled for ${app}")
    app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}

void traceLogsOff() {
  if (device) {
    logWarn("Trace logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Trace logging disabled for ${app}")
    app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}
// =============================================================================
// End Logging Helpers
// =============================================================================



// =============================================================================
// Imports
// =============================================================================
import com.hubitat.hub.domain.Event
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
// =============================================================================
// End Imports
// =============================================================================



// =============================================================================
// Installed, Updated, Uninstalled
// =============================================================================
void installed() {
  logDebug('Installed...')
  try {
    initialize()
  } catch(e) {
    logWarn("No initialize() method defined or initialize() resulted in error: ${e}")
  }

  if (settings.logEnable == true) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable == true) { runIn(1800, 'debugLogsOff') }
  if (settings.traceLogEnable == true) { runIn(1800, 'traceLogsOff') }
}

void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
  deleteChildDevices()
}

void updated() {
  logDebug('Updated...')
  try { configure() }
  catch(e) {
    if(e.toString().startsWith('java.net.NoRouteToHostException') || e.toString().startsWith('org.apache.http.conn.ConnectTimeoutException')) {
      logWarn('Could not initialize/configure device. Device could not be contacted. Please check IP address and/or password (if auth enabled). If device is battery powered, ensure device is awake immediately prior to clicking on "Initialize" or "Save Preferences".')
    } else {
      logWarn("No configure() method defined or configure() resulted in error: ${e}")
    }
  }
}
// =============================================================================
// End Installed, Updated, Uninstalled
// =============================================================================



// =============================================================================
// Formatters, Custom 'RunEvery', and other helpers
// =============================================================================
@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

String nowFormatted() {
  if(location.timeZone) {return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone)}
  else {return new Date().format('yyyy-MMM-dd h:mm:ss a')}
}

@CompileStatic
String runEveryCustomSeconds(Integer minutes) {
  String currentSecond = new Date().format('ss')
  return "${currentSecond} /${minutes} * * * ?"
}

@CompileStatic
String runEveryCustomMinutes(Integer minutes) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  return "${currentSecond} ${currentMinute}/${minutes} * * * ?"
}

@CompileStatic
String runEveryCustomHours(Integer hours) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  String currentHour = new Date().format('H')
  return "${currentSecond} ${currentMinute} ${currentHour}/${hours} * * ?"
}
// =============================================================================
// End Formatters, Custom 'RunEvery', and other helpers
// =============================================================================