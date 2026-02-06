/**
 * Version: 2.0.8
 */
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
  clearAllStates()
  runEvery30Minutes('configure')
  subscribe(location, "shellyBLEButtonDeviceEvents", "shellyButtonDeviceEventsHandler")
  subscribe(location, "shellyBLEBatteryEvents", "shellyBatteryEventsHandler")
  subscribe(location, "shellyBLEIlluminanceEvents", "shellyBLEIlluminanceEventsHandler")
  subscribe(location, "shellyBLERotationEvents", "shellyBLERotationEventsHandler")
  subscribe(location, "shellyBLEWindowEvents", "shellyBLEWindowEventsHandler")
  subscribe(location, "shellyBLEMotionEvents", "shellyBLEMotionEventsHandler")
  subscribe(location, "shellyBLETemperatureEvents", "shellyBLETemperatureEventsHandler")
  subscribe(location, "shellyBLEHumidityEvents", "shellyBLEHumidityEventsHandler")
}

void shellyButtonDeviceEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  List<Integer> buttons = evt.getValue().replace('[','').replace(']','').tokenize(',')
  if(!buttonDevicePushedRecently(macAddress, buttons)) {
    try{
      buttons.eachWithIndex{ button, buttonNumber ->
        b = button as Integer
        if(b == 0 && buttonNumber == 0) {sendEvent(macAddress, [name: 'presence', value: 'present', isStateChange: true])}
        else if(b == 1) {sendEvent(macAddress, [name: 'pushed', value: new BigDecimal(buttonNumber+1), isStateChange: true])}
        else if(b == 2) {sendEvent(macAddress, [name: 'doubleTapped', value: new BigDecimal(buttonNumber+1), isStateChange: true])}
        else if(b == 3) {sendEvent(macAddress, [name: 'tripleTapped', value: new BigDecimal(buttonNumber+1), isStateChange: true])}
        else if(b == 4) {sendEvent(macAddress, [name: 'released', value: new BigDecimal(buttonNumber+1), isStateChange: true])}
        else if(b > 32) {sendEvent(macAddress, [name: 'held', value: new BigDecimal(buttonNumber+1), isStateChange: true])}
      }
    } catch(e) {
      logWarn("No device found for DNI/MAC address: ${macAddress}, received button device event...")
    }
  } else {
    runIn(1, 'removeMacFromRecentlyPushedButtonDeviceList', [data:[mac: macAddress]])
  }
}

Boolean buttonDevicePushedRecently(String macAddress, List buttons) {
  if(state.buttonDevicePushedRecently == null) {
    state.buttonDevicePushedRecently = new LinkedHashMap<String, List>()
  }
  if(state.buttonDevicePushedRecently.containsKey(macAddress) && state.buttonDevicePushedRecently[macAddress] == buttons) {
    return true
  } else {
    LinkedHashMap<String, List> b = state.buttonDevicePushedRecently
    b[macAddress] = buttons
    state.buttonDevicePushedRecently = b
    runIn(1, 'removeMacFromRecentlyPushedButtonDeviceList', [data:[mac: macAddress]])
    return false
  }
}

void removeMacFromRecentlyPushedButtonDeviceList(Map data = null) {
  if(data != null && data.mac != null && state.buttonDevicePushedRecently != null) {
    LinkedHashMap<String, List> b = state.buttonDevicePushedRecently
    b.remove(data.mac)
    state.buttonDevicePushedRecently = b
  }
}

void shellyBatteryEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'battery', value: evt.getValue() as Integer, unit: '%'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}, received battery report event...")
  }
}

void shellyBLEIlluminanceEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'illuminance', value: evt.getValue() as Integer, unit: 'lx'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}, received illuminance event...")
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
    logWarn("No device found for DNI/MAC address: ${macAddress}, received window/door event...")
  }
}

void shellyBLEMotionEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'motion', value: (evt.getValue() as Integer) == 0 ? 'inactive' : 'active'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}, received motion event...")
  }
}

void shellyBLETemperatureEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    if(isCelciusScale() == true) {
      sendEvent(macAddress, [name: 'temperature', value: (evt.getValue() as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP), unit: '°C'])
    } else {
      sendEvent(macAddress, [name: 'temperature', value: cToF(evt.getValue() as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP), unit: '°F'])
    }

  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}, received temperature event...")
  }
}

void shellyBLEHumidityEventsHandler(Event evt) {
  String macAddress = evt.getData().toUpperCase().replace(':','')
  try{
    sendEvent(macAddress, [name: 'humidity', value: evt.getValue(), unit: '%'])
  } catch(e) {
    logWarn("No device found for DNI/MAC address: ${macAddress}, received humidity event...")
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

Boolean isCelciusScale() { getLocation().temperatureScale == 'C' }
BigDecimal cToF(BigDecimal val) { return celsiusToFahrenheit(val) }
BigDecimal fToC(BigDecimal val) { return fahrenheitToCelsius(val) }

void clearAllStates() {
  state.clear()
  if (device) device.getCurrentStates().each { device.deleteCurrentState(it.name) }
}
// =============================================================================
// End Formatters, Custom 'RunEvery', and other helpers
// =============================================================================