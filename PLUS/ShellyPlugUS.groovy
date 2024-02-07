metadata {
  definition (name: 'Shelly Plug US (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: true, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'test'
  }
}

if (device != null) {
  preferences {
    preferenceMap.each{ k,v ->
      if(settings.containsKey(k)) {
        if(v.type == 'enum') {
          input(name: "${k}".toString(), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: "${k}".toString(), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      }
    }
    if(hasPowerMonitoring()) {
      input(name: 'enablePowerMonitoring', type:'bool', title: 'Enable Power Monitoring', required: false, defaultValue: true)
      input(name: 'resetMonitorsAtMidnight', type:'bool', title: 'Reset Total Energy At Midnight', required: false, defaultValue: true)
    }

    input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
    input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: true
    input 'traceLogEnable', 'bool', title: 'Enable trace logging (warning: causes high hub load)', required: false, defaultValue: false
    input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: false
  }
}


void test() {
  logDebug('Testing...')
  processUnauthorizedMessage()
}

// =============================================================================
// Fields
// =============================================================================
@Field static Integer WS_CONNECT_INTERVAL = 600
@Field static ConcurrentHashMap<String, Instant> connectivityCheckInstants = new java.util.concurrent.ConcurrentHashMap<String, Instant>()
@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, LinkedHashMap> commandMap = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()
@Field static LinkedHashMap preferenceMap = [
  ipAddress: [type: 'string', title: 'IP Address', required: true, defaultValue: '192.168.33.1'],
  initial_state: [type: 'enum', title: 'State after power outage', options: ['off':'Power Off', 'on':'Power On', 'restore_last':'Previous State']],
  auto_off: [type: 'bool', title: 'After turning ON, turn OFF after a predefined time (in seconds)'],
  auto_off_delay: [type:'number', title: 'Delay before turning OFF'],
  auto_on: [type: 'bool', title: 'After turning OFF, turn ON after a predefined time (in seconds)'],
  auto_on_delay: [type:'number', title: 'Delay before turning ON'],
  autorecover_voltage_errors: [type: 'bool', title: 'Turn back ON after overvoltage if previously ON'],
  current_limit: [type: 'number', title: 'Overcurrent protection in amperes'],
  power_limit: [type: 'number', title: 'Overpower protection in watts'],
  voltage_limit: [type: 'number', title: 'Overvoltage protection in volts'],
]
@Field static List powerMonitoringDevices = [
  "SNPL-00116US"
]
@Field static ConcurrentHashMap<String, ArrayList<BigDecimal>> movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>()

// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Getters and Setters
// =============================================================================
DeviceWrapper getDevice() { return this.device }
String getDeviceDNI() { return this.device.getDeviceNetworkId() }
LinkedHashMap getSettings() { return this.settings }
Boolean hasPowerMonitoring() {
  return this.device.getDataValue('model') in powerMonitoringDevices
}

@CompileStatic
MessageDigest getMessageDigest() {
  if(messageDigests == null) { messageDigests = new ConcurrentHashMap<String, MessageDigest>() }
  if(!messageDigests.containsKey(getDeviceDNI())) { messageDigests[getDeviceDNI()] = MessageDigest.getInstance("SHA-256") }
  return messageDigests[getDeviceDNI()]
}

@CompileStatic
LinkedHashMap getAuthMap() {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!authMaps.containsKey(getDeviceDNI())) { authMaps[getDeviceDNI()] = [:] }
  return authMaps[getDeviceDNI()]
}
@CompileStatic
void setAuthMap(LinkedHashMap map) {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  authMaps[getDeviceDNI()] = map
}

@CompileStatic
LinkedHashMap getCommandMap() {
  if(commandQueue == null) { commandQueue = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!commandQueue.containsKey(getDeviceDNI())) { commandQueue[getDeviceDNI()] = new LinkedHashMap() }
  return commandQueue[getDeviceDNI()]
}
@CompileStatic
void addCommand(LinkedHashMap command) {
  LinkedHashMap map = getCommandMap()
  map[getDeviceDNI()](command)
}

@CompileStatic
String powerAvg() {"${getDeviceDNI()}power".toString()}
@CompileStatic
ArrayList<BigDecimal> powerAvgs() {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(powerAvg())) { movingAvgs[powerAvg()] = new ArrayList<BigDecimal>() }
  return movingAvgs[powerAvg()]
}
@CompileStatic clearPowerAvgs() {
  movingAvgs[powerAvg()] = new ArrayList<BigDecimal>()
}

@CompileStatic
String amperageAvg() {"${getDeviceDNI()}amperage".toString()}
@CompileStatic
ArrayList<BigDecimal> amperageAvgs() {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(amperageAvg())) { movingAvgs[amperageAvg()] = new ArrayList<BigDecimal>() }
  return movingAvgs[amperageAvg()]
}
@CompileStatic clearAmperageAvgs() {
  movingAvgs[amperageAvg()] = new ArrayList<BigDecimal>()
}

String getAuthString() {}

@CompileStatic
String getWebSocketUri() {
  return "ws://${getSettings()?.ipAddress}/rpc"
}


void setWebsocketStatus(String status) {
  logTrace("setWebsocketStatus: ${status}")
  if(status != 'open') {reconnectWebsocketAfterDelay(300)}
  if(status == 'open') {
    unschedule('initializeWebsocketConnection')
    connectivityCheckInstants.remove("${getDeviceDNI()}".toString())
    runIn(5, 'getStatusesAfterWsConnect')
  }
  this.device.updateDataValue('websocketStatus', status)
}
Boolean getWebsocketIsConnected() {
  return this.device.getDataValue('websocketStatus') == 'open'
}

@CompileStatic
void setSwitchState(Boolean on) {
  if(on != null && on != getSwitchState()) { getDevice().sendEvent(name: 'switch', value: on ? 'on' : 'off')}
}
@CompileStatic
Boolean getSwitchState() {
  return getDevice().currentValue('switch', true) == 'on'
}

@CompileStatic
void setCurrent(BigDecimal value) {
  ArrayList<BigDecimal> a = amperageAvgs()
  a.add(value)
  if(a.size() >= 10) {
    value = ((BigDecimal)a.sum()) / 10
    value = value.setScale(1, BigDecimal.ROUND_HALF_UP)
    if(value == -1) { getDevice().sendEvent(name: 'amperage', value: null) }
    else if(value != null && value != getCurrent()) { getDevice().sendEvent(name: 'amperage', value: value)}
    clearAmperageAvgs()
  }
}
@CompileStatic
BigDecimal getCurrent() {
  return getDevice().currentValue('amperage', true) as BigDecimal
}

@CompileStatic
void setPower(BigDecimal value) {
  ArrayList<BigDecimal> p = powerAvgs()
  p.add(value)
  if(p.size() >= 10) {
    value = ((BigDecimal)p.sum()) / 10
    value = value.setScale(0, BigDecimal.ROUND_HALF_UP)
    if(value == -1) { getDevice().sendEvent(name: 'power', value: null) }
    else if(value != null && value != getPower()) { getDevice().sendEvent(name: 'power', value: value)}
    clearPowerAvgs()
  }
}
@CompileStatic
BigDecimal getPower() {
  return getDevice().currentValue('power', true) as BigDecimal
}

@CompileStatic
void setEnergy(BigDecimal value) {
  value = value.setScale(1, BigDecimal.ROUND_HALF_UP)
  if(value == -1) { getDevice().sendEvent(name: 'energy', value: null) }
  else if(value != null && value != getEnergy()) { getDevice().sendEvent(name: 'energy', value: value)}
}
@CompileStatic
BigDecimal getEnergy() {
  return getDevice().currentValue('energy', true) as BigDecimal
}

void setDevicePreferences(Map preferences) {
  preferences.each{ k,v ->
    if(preferenceMap.containsKey(k)) {
      if(preferenceMap[k].type == 'enum') {
        device.updateSetting(k,[type:'enum', value: v])
      } else if(preferenceMap[k].type == 'number') {
        device.updateSetting(k,[type:'number', value: v as Integer])
      } else {
        device.updateSetting(k,v)
      }
    }
  }
}

void setDeviceInfo(Map info) {
  if(info?.model) { this.device.updateDataValue('model', info.model)}
  if(info?.gen) { this.device.updateDataValue('gen', info.gen.toString())}
  if(info?.ver) { this.device.updateDataValue('ver', info.ver)}
}
// =============================================================================
// End Getters and Setters
// =============================================================================



// =============================================================================
// Initialization
// =============================================================================
void initialize() {
  Map defaultPrefs = [
    ipAddress: '192.168.3.226',
  ]
  setDevicePreferences(defaultPrefs)
  if(!settings.enablePowerMonitoring) {
    setCurrent(-1)
    setPower(-1)
    setEnergy(-1)
  }
  ConcurrentHashMap<String, ArrayList<BigDecimal>> movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>()
  movingAvgs[powerAvg()] = new ArrayList<BigDecimal>()

  // processPreferences()
  runIn(3, 'initializeWebsocketConnection')
}

void processPreferences() {
  switchSetConfig(
    settings.initial_state,
    settings.auto_on,
    settings.auto_on_delay,
    settings.auto_off,
    settings.auto_off_delay,
    settings.power_limit,
    settings.voltage_limit,
    settings.autorecover_voltage_errors,
    settings.current_limit
  )
}

void configure() {
  if(settings.resetMonitorsAtMidnight) {
    schedule('0 0 0 * * ?', 'switchResetCounters')
  } else {
    unschedule('switchResetCounters')
  }
}



// =============================================================================
// End Initialization
// =============================================================================

// =============================================================================
// Device Commands
// =============================================================================
@CompileStatic
void on() { switchSet(true) }

@CompileStatic
void off() { switchSet(false) }

void refresh() {

}
// =============================================================================
// End Device Commands
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
void parse(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  processWebsocketMessages(json)
  logJson(json)
}

@CompileStatic
void processWebsocketMessages(LinkedHashMap json) {
  LinkedHashMap params = (LinkedHashMap)json?.params
  if(json?.method == 'NotifyStatus' || json?.dst == 'switchGetStatus') {
    if(params && params.containsKey('switch:0')) {
      LinkedHashMap switch0 = (LinkedHashMap)params['switch:0']

      Boolean switchState = switch0?.output
      if(switchState != null) { setSwitchState(switchState) }

      if(getSettings().enablePowerMonitoring) {
        BigDecimal current =  (BigDecimal)switch0?.current
        if(current != null) { setCurrent(current) }

        BigDecimal apower =  (BigDecimal)switch0?.apower
        if(apower != null) { setPower(apower) }

        BigDecimal aenergy =  (BigDecimal)((LinkedHashMap)(switch0?.aenergy))?.total
        if(aenergy != null) { setEnergy(aenergy/1000) }
      }

    } else if (json?.result != null) {
      Boolean switchState = ((LinkedHashMap)json.result)?.output
      if(switchState != null) { setSwitchState(switchState) }
    }
  }

  if(json?.dst == 'switchGetConfig' && json?.result != null) {
    setDevicePreferences((LinkedHashMap)json.result)
  }

  if(json?.dst == 'shellyGetDeviceInfo' && json?.result != null) {
    setDeviceInfo((LinkedHashMap)json.result)
  }

  if(json?.dst == 'connectivityCheck' && json?.result != null) {
    Instant checkStarted = connectivityCheckInstants[getDeviceDNI()]
    if(checkStarted != null) {
      Duration dur = Duration.between(checkStarted, Instant.now())
      long seconds = dur.getSeconds()
      if(seconds < 5) { setWebsocketStatus('open') }
      else { setWebsocketStatus('connection timed out') }
    } else { setWebsocketStatus('connection timed out') }
  }

  if(json?.error != null && ((LinkedHashMap)json?.error)?.message != null && ((LinkedHashMap)json?.error)?.code == 401) {
    processUnauthorizedMessage(((LinkedHashMap)json?.error).message as String)
  }
}


// =============================================================================
// End Parse
// =============================================================================



// =============================================================================
// Auth
// =============================================================================
@CompileStatic
void processUnauthorizedMessage(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  setAuthMap(json)
}
//"{\"auth_type\": \"digest\", \"nonce\": 1707272138, \"nc\": 1, \"realm\": \"shellyplugus-c049ef8b3a44\", \"algorithm\": \"SHA-256\"}"

// =============================================================================
// End Auth
// =============================================================================



// =============================================================================
// Websocket Connection
// =============================================================================
void webSocketStatus(String message) {
  if(message == 'failure: null' || message == 'failure: Connection reset') {
    setWebsocketStatus('closed')
  }
  else if(message == 'failure: connect timed out') { setWebsocketStatus('connect timed out')}
  else if(message == 'status: open') {
    setWebsocketStatus('open')
  } else {
    logWarn("Websocket Status Message: ${message}")
    setWebsocketStatus('unknown')
  }
  logTrace("Socket Status: ${message}")
}


void getStatusesAfterWsConnect() {
  shellyGetDeviceInfo()
  sysGetStatus()
  switchGetStatus()
  switchGetConfig()
}

void wsConnect() {
  interfaces.webSocket.connect(getWebSocketUri(), headers: [:], ignoreSSLIssues: true)
  unschedule('checkWebsocketConnection')
  runIn(WS_CONNECT_INTERVAL, 'checkWebsocketConnection')
}

void sendWsMessage(String message) {
  if(!getWebsocketIsConnected()) { wsConnect() }
  logTrace("Sending json command: ${message}")
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() {
  wsClose()
  wsConnect()
}

void checkWebsocketConnection() {
  connectivityCheckInstants[getDeviceDNI()] = Instant.now()
  shellyGetDeviceInfo(false, 'connectivityCheck')
}
void reconnectWebsocketAfterDelay(Integer delay = 15) {
  runIn(delay, 'initializeWebsocketConnection', [overwrite: true])
}
void wsClose() { interfaces.webSocket.close() }
// =============================================================================
// End Websocket Connection
// =============================================================================



// =============================================================================
// Websocket Commands
// =============================================================================
@CompileStatic
String shellyGetDeviceInfo(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  Map command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetDeviceInfo",
    "params":["ident":fullInfo]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}
// <ha1> + ":" + <nonce> + ":" + <nc> + ":" + <cnonce> + ":" + "auth" + ":" + <ha2>
// 74b332dd2b2d7f56ffaba12aa7733b398ee0778e6b29d76c1e86046aff73c0b9:" + 1707065217+ ":" + <nc> + ":" + 123414913 + ":" + "auth" + ":" + 6370ec69915103833b5222b368555393393f098bfbfbb59f47e0590af135f062

@CompileStatic
String sysGetStatus() {
  Map command = [
    "id":0,
    "src":"sysGetStatus",
    "method":"Sys.GetStatus",
    "params":[:]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}



@CompileStatic
String switchGetStatus() {
  Map command = [
    "id" : 0,
    "src" : "switchGetStatus",
    "method" : "Switch.GetStatus",
    "params" : [
      "id" : 0
    ]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchSet(Boolean on) {
  Map command = [
    "id" : 0,
    "src" : "switchSet",
    "method" : "Switch.Set",
    "params" : [
      "id" : 0,
      "on" : on
    ]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchGetConfig() {
  Map command = [
    "id" : 0,
    "src" : "switchGetConfig",
    "method" : "Switch.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchSetConfig(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit
) {
  Map command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : 0,
      "config": [
        "initial_state": initial_state,
        "auto_on": auto_on,
        "auto_on_delay": auto_on_delay,
        "auto_off": auto_off,
        "auto_off_delay": auto_off_delay,
        "power_limit": power_limit,
        "voltage_limit": voltage_limit,
        "autorecover_voltage_errors": autorecover_voltage_errors,
        "current_limit": current_limit
      ]
    ]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchResetCounters() {
  Map command = [
    "id" : 0,
    "src" : "switchResetCounters",
    "method" : "Switch.ResetCounters",
    "params" : ["id" : 0]
  ]
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}



// =============================================================================
// End Websocket Commands
// =============================================================================



// /////////////////////////////////////////////////////////////////////////////
// Library Code
// /////////////////////////////////////////////////////////////////////////////
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
import hubitat.scheduling.AsyncResponse
import hubitat.device.HubResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.time.Instant
import java.time.Duration
import java.security.MessageDigest

void jsonAsyncGet(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpGet(callbackMethod, params, data)
}

void jsonAsyncPost(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpPost(callbackMethod, params, data)
}

LinkedHashMap jsonSyncGet(Map params) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data as LinkedHashMap }
    else { logError(resp.data) }
  }
}

void clearAllStates() {
  state.clear()
  if (device) device.getCurrentStates().each { device.deleteCurrentState(it.name) }
}

void deleteChildDevices() {
  List<ChildDeviceWrapper> children = getChildDevices()
  children.each { child ->
    deleteChildDevice(child.getDeviceNetworkId())
  }
}

void installed() {
  logDebug('Installed...')
  try {
    initialize()
  } catch(e) {
    logWarn("No initialize() method defined or initialize() resulted in error: ${e}")
  }

  if (settings.logEnable) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable) { runIn(1800, 'debugLogsOff') }
  if (settings.traceLogEnable) { runIn(1800, 'traceLogsOff') }
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
    logWarn("No configure() method defined or configure() resulted in error: ${e}")
  }
}

@CompileStatic
Boolean responseIsValid(AsyncResponse response) {
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorData()' method
    try{logError("Request ErrorJson: ${prettyJson(response.getErrorJson() as LinkedHashMap)}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorJson()' method
  }
  if (response.hasError()) { return false } else { return true }
}

void logException(message) {
  if (settings.logEnable) {
    if(device) log.exception "${device.label ?: device.name }: ${message}"
    if(app) log.exception "${app.label ?: app.name }: ${message}"
  }
}

void logError(message) {
  if (settings.logEnable) {
    if(device) log.error "${device.label ?: device.name }: ${message}"
    if(app) log.error "${app.label ?: app.name }: ${message}"
  }
}

void logWarn(message) {
  if (settings.logEnable) {
    if(device) log.warn "${device.label ?: device.name }: ${message}"
    if(app) log.warn "${app.label ?: app.name }: ${message}"
  }
}

void logInfo(message) {
  if (settings.logEnable) {
    if(device) log.info "${device.label ?: device.name }: ${message}"
    if(app) log.info "${app.label ?: app.name }: ${message}"
  }
}

void logDebug(message) {
  if (settings.logEnable && settings.debugLogEnable) {
    if(device) log.debug "${device.label ?: device.name }: ${message}"
    if(app) log.debug "${app.label ?: app.name }: ${message}"
  }
}

void logTrace(message) {
  if (settings.logEnable && settings.traceLogEnable) {
    if(device) log.trace "${device.label ?: device.name }: ${message}"
    if(app) log.trace "${app.label ?: app.name }: ${message}"
  }
}

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

@CompileStatic
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

@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

String nowFormatted() {
  if(location.timeZone) return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone)
  else                  return new Date().format('yyyy-MMM-dd h:mm:ss a')
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

double nowDays() {
  return (now() / 86400000)
}

@CompileStatic
Integer convertHexToInt(String hex) { Integer.parseInt(hex,16) }

@CompileStatic
String convertHexToIP(String hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

@CompileStatic
String convertIPToHex(String ipAddress) {
  List parts = ipAddress.tokenize('.')
  return String.format("%02X%02X%02X%02X", parts[0] as Integer, parts[1] as Integer, parts[2] as Integer, parts[3] as Integer)
}