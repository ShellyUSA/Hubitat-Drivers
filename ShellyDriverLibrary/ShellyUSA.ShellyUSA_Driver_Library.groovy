library(
  name: 'ShellyUSA_Driver_Library',
  namespace: 'ShellyUSA',
  author: 'Daniel Winks',
  description: 'ShellyUSA Driver Library',
  importUrl: 'https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/WebhookWebsocket/ShellyUSA_Driver_Library.groovy'
)
// =============================================================================
// Imports
// =============================================================================
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovyx.net.http.HttpResponseException
import hubitat.device.HubResponse
import hubitat.scheduling.AsyncResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.StringReader
import java.io.StringWriter
// =============================================================================
// End Imports
// =============================================================================



// =============================================================================
// Preferences
// =============================================================================
if (device != null) {
  preferences {
    if(BLU == null) {
      input 'ipAddress', 'string', title: 'IP Address', required: true, defaultValue: ''
      if(GEN1 != null && GEN1 == true) {
        input 'deviceUsername', 'string', title: 'Device Username (if enabled on device)', required: false, defaultValue: 'admin'
      }
      input 'devicePassword', 'password', title: 'Device Password (if enabled on device)', required: false, defaultValue: ''
    } else {
      input 'macAddress', 'string', title: 'MAC Address', required: true, defaultValue: ''
    }

    preferenceMap.each{ k,v ->
      if(getDeviceSettings().containsKey(k)) {
        if(v.type == 'enum') {
          input(name: "${k}".toString(), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: "${k}".toString(), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      }
    }

    if(hasPowerMonitoring() == true) {
      input(name: 'enablePowerMonitoring', type:'bool', title: 'Enable Power Monitoring', required: false, defaultValue: true)
      input(name: 'resetMonitorsAtMidnight', type:'bool', title: 'Reset Total Energy At Midnight', required: false, defaultValue: true)
    }

    if(hasBluGateway() == true) {
      input(name: 'enableBluetooteGateway', type:'bool', title: 'Enable Bluetooth Gateway for Hubitat', required: false, defaultValue: true)
    }

    if(getDevice().hasCapability('PresenceSensor')) {
      input 'presenceTimeout', 'number', title: 'Presence Timeout (minimum 300 seconds)', required: true, defaultValue: 300
    }

    input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
    input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: true
    input 'traceLogEnable', 'bool', title: 'Enable trace logging (warning: causes high hub load)', required: false, defaultValue: false
    input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: false
  }
}
// =============================================================================
// End Preferences
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
// Fields
// =============================================================================
@Field static Integer WS_CONNECT_INTERVAL = 600
@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()
@Field static LinkedHashMap preferenceMap = [
  initial_state: [type: 'enum', title: 'State after power outage', options: ['off':'Power Off', 'on':'Power On', 'restore_last':'Previous State']],
  auto_off: [type: 'bool', title: 'Auto-ON: after turning ON, turn OFF after a predefined time (in seconds)'],
  auto_off_delay: [type:'number', title: 'Auto-ON Delay: delay before turning OFF'],
  auto_on: [type: 'bool', title: 'Auto-OFF: after turning OFF, turn ON after a predefined time (in seconds)'],
  auto_on_delay: [type:'number', title: 'Auto-OFF Delay: delay before turning ON'],
  autorecover_voltage_errors: [type: 'bool', title: 'Turn back ON after overvoltage if previously ON'],
  current_limit: [type: 'number', title: 'Overcurrent protection in amperes'],
  power_limit: [type: 'number', title: 'Overpower protection in watts'],
  voltage_limit: [type: 'number', title: 'Overvoltage protection in volts'],
  gen1_motion_sensitivity: [type: 'number', title: 'Motion sensitivity (1-256, lower is more sensitive)'],
  gen1_motion_blind_time_minutes: [type: 'number', title: 'Motion cool down in minutes'],
  gen1_tamper_sensitivity: [type: 'number', title: 'Tamper sensitivity (1-127, lower is more sensitive, 0 for disabled)'],
  gen1_set_volume: [type: 'number', title: 'Speaker volume (1 (lowest) .. 11 (highest))']
]
@Field static List powerMonitoringDevices = [
  'SNPL-00116US',
  'S3PM-001PCEU16',
  'SNSW-001P16EU',
  'SNSW-001P15UL'
]

@Field static List bluGatewayDevices = [
  'SNPL-00116US',
  'SNGW-BT01',
  'S3PM-001PCEU16',
  'SNSW-001P16EU',
  'SNSW-001P15UL'
]
@Field static List gen1Devices = [

]
@Field static ConcurrentHashMap<String, ArrayList<BigDecimal>> movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>()
@Field static String BLE_SHELLY_BLU = 'https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Bluetooth/ble-shelly-blu.js'
// =============================================================================
// End Fields
// =============================================================================



// =============================================================================
// Command Maps
// =============================================================================
@CompileStatic
LinkedHashMap shellyGetDeviceInfoCommand(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetDeviceInfo",
    "params":["ident":fullInfo]
  ]
  return command
}

@CompileStatic
LinkedHashMap shellyGetStatusCommand(String src = 'shellyGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap sysGetStatusCommand(String src = 'sysGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Sys.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap devicePowerGetStatusCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerGetStatus",
    "method" : "DevicePower.GetStatus",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetStatusCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchGetStatus",
    "method" : "Switch.GetStatus",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetCommand(Boolean on) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSet",
    "method" : "Switch.Set",
    "params" : [
      "id" : 0,
      "on" : on
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetConfigCommand(String src = 'switchGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetConfigCommand(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit,
  Integer switchId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : switchId,
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
  return command
}

@CompileStatic
LinkedHashMap switchResetCountersCommand(String src = 'switchResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchResetCounters",
    "method" : "Switch.ResetCounters",
    "params" : ["id" : 0]
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListSupportedCommand(String src = 'switchResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookListSupported",
    "method" : "Webhook.ListSupported",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListCommand(String src = 'switchResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookList",
    "method" : "Webhook.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptListCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptList",
    "method" : "Script.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStopCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStop",
    "method" : "Script.Stop",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStartCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStart",
    "method" : "Script.Start",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptEnableCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptEnable",
    "method" : "Script.SetConfig",
    "params" : [
      "id": id,
      "config": ["enable": true]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptDeleteCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptDelete",
    "method" : "Script.Delete",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptCreateCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptCreate",
    "method" : "Script.Create",
    "params" : ["name": "HubitatBLEHelper"]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptPutCodeCommand(Integer id, String code, Boolean append = true) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptPutCode",
    "method" : "Script.PutCode",
    "params" : [
      "id": id,
      "code": code,
      "append": append
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetConfigCommand(String src = 'pm1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1SetConfigCommand(Integer pm1Id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "pm1SetConfig",
    "method" : "PM1.SetConfig",
    "params" : [
      "id" : pm1Id,
      "config": []
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetStatusCommand(String src = 'pm1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetStatus",
    "params" : ["id" : 0]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1ResetCountersCommand(String src = 'pm1ResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.ResetCounters",
    "params" : ["id" : 0]
  ]
  return command
}


@CompileStatic
LinkedHashMap bleGetConfigCommand(String src = 'bleGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "BLE.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap bleSetConfigCommand(Boolean enable, Boolean rpcEnable, Boolean observerEnable) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "bleSetConfig",
    "method" : "BLE.SetConfig",
    "params" : [
      "id" : 0,
      "config": [
        "enable": enable,
        "rpc": rpcEnable,
        "observer": observerEnable
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputGetConfigCommand(String src = 'inputGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Input.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

// =============================================================================
// End Command Maps
// =============================================================================



// =============================================================================
// Parse
// =============================================================================
@CompileStatic
void processWebsocketMessagesConnectivity(LinkedHashMap json) {
  if(((String)json?.dst).startsWith('connectivityCheck-') && json?.result != null) {
    logDebug("JSON: ${json}")
    Long checkStarted = Long.valueOf(((String)json?.dst).split('-')[1])
    logDebug("Check started ${checkStarted}")
    if(checkStarted != null) {
      long seconds = unixTimeSeconds() - checkStarted
      if(seconds < 5) { setWebsocketStatus('open') }
      else { setWebsocketStatus('connection timed out') }
    } else { setWebsocketStatus('connection timed out') }
    if(((LinkedHashMap)json.result)?.auth_en != null) {
      setAuthIsEnabled((Boolean)(((LinkedHashMap)json.result)?.auth_en))
      shellyGetStatus('authCheck')
    }
  }
}

@CompileStatic
void processWebsocketMessagesAuth(LinkedHashMap json) {
  if(json?.error != null ) {
    logInfo(prettyJson(json))
    LinkedHashMap error = (LinkedHashMap)json.error
    if(error?.message != null && error?.code == 401) {
      processUnauthorizedMessage(error.message as String)
    }
  }
}

@CompileStatic
void processWebsocketMessagesPowerMonitoring(LinkedHashMap json, String switchName = null) {
  String dst = json?.dst
  if(dst != null && dst != '' && dst.contains('GetStatus') || (dst.contains('authCheck')) && json?.method != 'NotifyStatus') {
    if(getDeviceSettings().enablePowerMonitoring != null && getDeviceSettings().enablePowerMonitoring == true) {
      if(json?.result != null) {
        LinkedHashMap res = (LinkedHashMap)json.result
        BigDecimal current =  (BigDecimal)res?.current
        if(current != null) { setCurrent(current) }
        BigDecimal apower =  (BigDecimal)res?.apower
        if(apower != null) { setPower(apower) }
        BigDecimal voltage =  (BigDecimal)res?.voltage
        if(voltage != null) { setVoltage(voltage) }
        BigDecimal aenergy =  (BigDecimal)((LinkedHashMap)(res?.aenergy))?.total
        if(aenergy != null) { setEnergy(aenergy/1000) }
      }
    }
  }

  if(json?.method == 'NotifyStatus') {
    LinkedHashMap params = (LinkedHashMap)json?.params
    if(params != null && params.containsKey(switchName)) {
      LinkedHashMap sw = (LinkedHashMap)params[switchName]
      Boolean switchState = sw?.output
      if(switchState != null) { setSwitchState(switchState) }
      if(getDeviceSettings().enablePowerMonitoring != null && getDeviceSettings().enablePowerMonitoring == true) {
        BigDecimal current =  (BigDecimal)sw?.current
        if(current != null) { setCurrent(current) }
        BigDecimal apower =  (BigDecimal)sw?.apower
        if(apower != null) { setPower(apower) }
        BigDecimal aenergy =  (BigDecimal)((LinkedHashMap)(sw?.aenergy))?.total
        if(aenergy != null) { setEnergy(aenergy/1000) }
      }
    } else if (json?.result != null) {
      Boolean switchState = ((LinkedHashMap)json.result)?.output
      if(switchState != null) { setSwitchState(switchState) }
    }
  }
}

@CompileStatic
void processWebsocketMessagesBluetoothEvents(LinkedHashMap json) {
  LinkedHashMap params = (LinkedHashMap)json?.params
  String src = ((String)json?.src).split('-')[0]
  if(json?.method == 'NotifyEvent') {
    if(params != null && params.containsKey('events')) {
      List<LinkedHashMap> events = (List<LinkedHashMap>)params.events
      events.each{ event ->
        LinkedHashMap evtData = (LinkedHashMap)event?.data
        String address = (String)evtData?.address
        address = address.replace(':','')
        if(address != null && address != '' && evtData?.button != null) {
          Integer button = evtData?.button as Integer
          if(button < 4 && button > 0) {
            sendEventToShellyBluetoothHelper("shellyBLEButtonPushedEvents", button, address)
          } else if(button == 4) {
            sendEventToShellyBluetoothHelper("shellyBLEButtonHeldEvents", 1, address)
          } else if(button == 0) {
            sendEventToShellyBluetoothHelper("shellyBLEButtonPresenceEvents", 0, address)
          }
        }
        if(address != null && address != '' && evtData?.battery != null) {
          sendEventToShellyBluetoothHelper("shellyBLEBatteryEvents", evtData?.battery as Integer, address)
        }
        if(address != null && address != '' && evtData?.illuminance != null) {
          sendEventToShellyBluetoothHelper("shellyBLEIlluminanceEvents", evtData?.illuminance as Integer, address)
        }
        if(address != null && address != '' && evtData?.rotation != null) {
          sendEventToShellyBluetoothHelper("shellyBLERotationEvents", evtData?.rotation as BigDecimal, address)
        }
        if(address != null && address != '' && evtData?.rotation != null) {
          sendEventToShellyBluetoothHelper("shellyBLEWindowEvents", evtData?.window as Integer, address)
        }
        if(address != null && address != '' && evtData?.motion != null) {
          sendEventToShellyBluetoothHelper("shellyBLEMotionEvents", evtData?.motion as Integer, address)
        }
      }
    }
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

@CompileStatic
String getPassword() { return getDeviceSettings().devicePassword as String }
LinkedHashMap getAuth() {
  LinkedHashMap authMap = getAuthMap()
  if(authMap == null || authMap.size() == 0) {return [:]}
  String realm = authMap['realm']
  String ha1 = "admin:${realm}:${getPassword()}".toString()
  Long nonce = Long.valueOf(authMap['nonce'].toString())
  String nc = (authMap['nc']).toString()
  Long cnonce = now()
  String ha2 = '6370ec69915103833b5222b368555393393f098bfbfbb59f47e0590af135f062'
  ha1 = sha256(ha1)
  String response = ha1 + ':' + nonce.toString() + ':' + nc + ':' + cnonce.toString() + ':' + 'auth'  + ':' + ha2
  response = sha256(response)
  String algorithm = authMap['algorithm'].toString()
  return [
    'realm':realm,
    'username':'admin',
    'nonce':nonce,
    'cnonce':cnonce,
    'response':response,
    'algorithm':algorithm
  ]
}

@CompileStatic
String sha256(String base) {
  MessageDigest digest = getMessageDigest()
  byte[] hash = digest.digest(base.getBytes("UTF-8"))
  StringBuffer hexString = new StringBuffer()
  for (int i = 0; i < hash.length; i++) {
    String hex = Integer.toHexString(0xff & hash[i])
    if(hex.length() == 1) hexString.append('0')
    hexString.append(hex);
  }
  return hexString.toString()
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
  logInfo("Device authentication detected, setting authmap to ${map}")
  authMaps[getDeviceDNI()] = map
}

@CompileStatic
Boolean authIsEnabled() {
  return getDevice().getDataValue('auth') == 'true'
}
@CompileStatic
void setAuthIsEnabled(Boolean auth) {
  getDevice().updateDataValue('auth', auth.toString())
}

@CompileStatic
Boolean authIsEnabledGen1() {
  return (
    getDeviceSettings()?.deviceUsername != null &&
    getDeviceSettings()?.devicePassword != null &&
    getDeviceSettings()?.deviceUsername != '' &&
    getDeviceSettings()?.devicePassword != ''
  )
}

@CompileStatic
void performAuthCheck() {
  shellyGetStatus('authCheck')
}

@CompileStatic
String getBasicAuthHeader() {
  if(getDeviceSettings()?.deviceUsername != null && getDeviceSettings()?.devicePassword != null) {
    return base64Encode("${getDeviceSettings().deviceUsername}:${getDeviceSettings().devicePassword}".toString())
  }
}
// =============================================================================
// End Auth
// =============================================================================



// =============================================================================
// HTTP Methods
// =============================================================================
LinkedHashMap postCommandSync(LinkedHashMap command) {
  LinkedHashMap json
  Map params = [uri: "${getBaseUri()}/rpc"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = JsonOutput.toJson(command)
  logTrace("postCommandSync sending: ${prettyJson(params)}")
  try {
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
  } catch(HttpResponseException ex) {
    setAuthIsEnabled(true)
    String authToProcess = ex.getResponse().getAllHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
  }
  try {
    if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
    params.body = command
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
  } catch(HttpResponseException ex) {
    logError('Auth failed a second time. Double check password correctness.')
  }
  logTrace("postCommandSync returned: ${prettyJson(json)}")
  return json
}

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

@CompileStatic
void sendShellyCommand(String command, String queryParams = null, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = queryParams ? "${getBaseUri()}/${command}${queryParams}".toString() : "${getBaseUri()}/${command}".toString()
  logTrace("sendShellyCommand: ${params}")
  jsonAsyncGet(callbackMethod, params, data)
}

@CompileStatic
void sendShellyJsonCommand(String command, Map json, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = "${getBaseUri()}/${command}".toString()
  params.body = json
  logTrace("sendShellyJsonCommand: ${params}")
  jsonAsyncPost(callbackMethod, params, data)
}

@CompileStatic
void shellyCommandCallback(AsyncResponse response, Map data = null) {
  if(!responseIsValid(response)) {return}
  logJson(response.getJson() as LinkedHashMap)
}

LinkedHashMap sendGen1Command(String command, String queryString = null) {
  LinkedHashMap json
  LinkedHashMap params = [:]
  if(queryString != null && queryString != '') {
    params.uri = "${getBaseUri()}/${command}?${queryString}".toString()
  } else {
    params.uri = "${getBaseUri()}/${command}".toString()
  }
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  if(authIsEnabledGen1() == true) {
    params.headers = ['Authorization': "Basic ${getBasicAuthHeader()}"]
  }
  logTrace("sendGen1Command sending: ${prettyJson(params)}")
  httpGet(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
  return json
}

LinkedHashMap getDeviceActionsGen1() {
  LinkedHashMap json
  String command = 'settings/actions'
  LinkedHashMap params = [uri: "${getBaseUri()}/${command}".toString()]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  if(authIsEnabledGen1() == true) {
    params.headers = ['Authorization': "Basic ${getBasicAuthHeader()}"]
  }
  httpGet(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
  if(json?.actions != null) {json = json.actions}
  return json
}
// =============================================================================
// End HTTP Methods
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

void wsConnect() {
  String uri = getWebSocketUri()
  if(uri != null && uri != '') {
    interfaces.webSocket.connect(uri, headers: [:], ignoreSSLIssues: true)
    unschedule('checkWebsocketConnection')
    runIn(WS_CONNECT_INTERVAL, 'checkWebsocketConnection')
  }
}

void sendWsMessage(String message) {
  if(getWebsocketIsConnected() == false) { wsConnect() }
  logDebug("Sending json command: ${message}")
  interfaces.webSocket.sendMessage(message)
}

void initializeWebsocketConnection() {
  wsClose()
  wsConnect()
}

@CompileStatic
void checkWebsocketConnection() {
  shellyGetDeviceInfo(false, 'connectivityCheck')
  shellyGetStatus('authCheck')
}

void reconnectWebsocketAfterDelay(Integer delay = 15) {
  runIn(delay, 'initializeWebsocketConnection', [overwrite: true])
}

void wsClose() { interfaces.webSocket.close() }

void setWebsocketStatus(String status) {
  logDebug("setWebsocketStatus: ${status}")
  if(status != 'open') {reconnectWebsocketAfterDelay(300)}
  if(status == 'open') {
    unschedule('initializeWebsocketConnection')
    if(atomicState.initInProgress == true) {
      atomicState.remove('initInProgress')
      configure()
    }
    runIn(1, 'performAuthCheck')
  }
  this.device.updateDataValue('websocketStatus', status)
}
Boolean getWebsocketIsConnected() {
  return this.device.getDataValue('websocketStatus') == 'open'
}
// =============================================================================
// End Websocket Connection
// =============================================================================



// =============================================================================
// Websocket Commands
// =============================================================================
@CompileStatic
String shellyGetDeviceInfo(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  if(src == 'connectivityCheck') {
    long seconds = unixTimeSeconds()
    src = "${src}-${seconds}"
  }
  Map command = shellyGetDeviceInfoCommand(fullInfo, src)
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String shellyGetStatus(String src = 'shellyGetStatus') {
  LinkedHashMap command = shellyGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String sysGetStatus(String src = 'sysGetStatus') {
  LinkedHashMap command = sysGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchGetStatus() {
  LinkedHashMap command = switchGetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchSet(Boolean on) {
  LinkedHashMap command = switchSetCommand(on)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String switchGetConfig(String src = 'switchGetConfig') {
  LinkedHashMap command = switchGetConfigCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
void switchSetConfig(
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
  Map command = switchSetConfigCommand(initial_state, auto_on, auto_on_delay, auto_off, auto_off_delay, power_limit, voltage_limit, autorecover_voltage_errors, current_limit)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
void switchResetCounters(String src = 'switchResetCounters') {
  LinkedHashMap command = switchResetCountersCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
void scriptList() {
  LinkedHashMap command = scriptListCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}

@CompileStatic
String pm1GetStatus() {
  LinkedHashMap command = pm1GetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  sendWsMessage(json)
}
// =============================================================================
// End Websocket Commands
// =============================================================================



// =============================================================================
// Webhook Helpers
// =============================================================================
@CompileStatic
void setDeviceWebhooks() {
  LinkedHashMap supported = getSupportedWebhooks()
  if(supported?.result != null) {supported = (LinkedHashMap)supported.result}
  LinkedHashMap types = (LinkedHashMap)supported?.types
  LinkedHashMap currentWebhooks = getCurrentWebhooks()
  if(currentWebhooks?.result != null) {currentWebhooks = (LinkedHashMap)currentWebhooks.result}
  List<LinkedHashMap> hooks = (List<LinkedHashMap>)currentWebhooks?.hooks
  if(types != null) {
    types.each{k,v ->
      String type = k.toString()
      List<LinkedHashMap> attrs = ((LinkedHashMap)v).attrs as List<LinkedHashMap>
      attrs.each{
        String event = it.name.toString()
        String name = "hubitat.${type}.${event}".toString()
        if(hooks != null) {
          LinkedHashMap currentWebhook = hooks.find{it.name == name}
          if(currentWebhook != null) {
            webhookUpdate(type, event, (currentWebhook?.id).toString())
          } else {
            webhookCreate(type, event)
          }
        } else {
          webhookCreate(type, event)
        }
      }
    }
  }
}


@CompileStatic
LinkedHashMap<String,List> getCurrentWebhooks() {
  return postCommandSync(webhookListCommand())
}

@CompileStatic
LinkedHashMap getSupportedWebhooks() {
  return postCommandSync(webhookListSupportedCommand())
}

@CompileStatic
void webhookCreate(String type, String event) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookCreate",
    "method" : "Webhook.Create",
    "params" : [
      "cid": 0,
      "enable": true,
      "name": "hubitat.${type}.${event}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${event}/\${ev.${event}}".toString()]
    ]
  ]
  postCommandSync(command)
}

@CompileStatic
void webhookUpdate(String type, String event, String id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookUpdate",
    "method" : "Webhook.Update",
    "params" : [
      "id": id as Integer,
      "enable": true,
      "name": "hubitat.${type}.${event}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${event}/\${ev.${event}}".toString()]
    ]
  ]
  postCommandSync(command)
}

LinkedHashMap decodeLanMessage(String message) {
  return parseLanMessage(message)
}
// =============================================================================
// End Webhook Helpers
// =============================================================================



// =============================================================================
// Bluetooth
// =============================================================================
@CompileStatic
void enableBluReportingToHE() {
  enableBluetooth()
  LinkedHashMap s = getBleShellyBluId()
  if(s == null) {
    postCommandSync(scriptCreateCommand())
    s = getBleShellyBluId()
  }
  Integer id = s?.id as Integer
  if(id != null) {
    postCommandSync(scriptStopCommand(id))
    String js = getBleShellyBluJs()
    postCommandSync(scriptPutCodeCommand(id, js, false))
    postCommandSync(scriptEnableCommand(id))
    postCommandSync(scriptStartCommand(id))
  }
}

@CompileStatic
void disableBluReportingToHE() {
  LinkedHashMap s = getBleShellyBluId()
  Integer id = s?.id as Integer
  if(id != null) {
    postCommandSync(scriptDeleteCommand(id))
  }
}

@CompileStatic
LinkedHashMap getBleShellyBluId() {
  LinkedHashMap json = postCommandSync(scriptListCommand())
  List<LinkedHashMap> scripts = (List<LinkedHashMap>)((LinkedHashMap)json?.result)?.scripts
  return scripts.find{it?.name == 'HubitatBLEHelper'}
}

String getBleShellyBluJs() {
  Map params = [uri: BLE_SHELLY_BLU]
  params.contentType = 'text/plain'
  params.requestContentType = 'text/plain'
  params.textParser = true
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) {
      StringWriter sw = new StringWriter()
      ((StringReader)resp.data).transferTo(sw);
      return sw.toString()
    }
    else { logError(resp.data) }
  }
}

void enableBluetooth() {
  postCommandSync(bleSetConfigCommand(true, true, true))
  postCommandSync(bleSetConfigCommand(true, true, true))
}

// =============================================================================
// End Bluetooth
// =============================================================================

// =============================================================================
// Getters and Setters
// =============================================================================
// /////////////////////////////////////////////////////////////////////////////
// Power Monitoring Getters and Setters
// /////////////////////////////////////////////////////////////////////////////
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

@CompileStatic
String voltageAvg() {"${getDeviceDNI()}voltage".toString()}
@CompileStatic
ArrayList<BigDecimal> voltageAvgs() {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(voltageAvg())) { movingAvgs[voltageAvg()] = new ArrayList<BigDecimal>() }
  return movingAvgs[voltageAvg()]
}
@CompileStatic clearVoltageAvgs() {
  movingAvgs[voltageAvg()] = new ArrayList<BigDecimal>()
}

@CompileStatic
void setCurrent(BigDecimal value) {
  ArrayList<BigDecimal> a = amperageAvgs()
  if(a.size() == 0) {
    getDevice().sendEvent(name: 'amperage', value: value)
  }
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
  if(p.size() == 0) {
    getDevice().sendEvent(name: 'power', value: value)
  }
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
void setVoltage(BigDecimal value) {
  ArrayList<BigDecimal> v = voltageAvgs()
  if(v.size() == 0) {
    getDevice().sendEvent(name: 'voltage', value: value)
  }
  v.add(value)
  if(v.size() >= 10) {
    value = ((BigDecimal)v.sum()) / 10
    value = value.setScale(0, BigDecimal.ROUND_HALF_UP)
    if(value == -1) { getDevice().sendEvent(name: 'voltage', value: null) }
    else if(value != null && value != getPower()) { getDevice().sendEvent(name: 'voltage', value: value)}
    clearPowerAvgs()
  }
}
@CompileStatic
BigDecimal getVoltage() {
  return getDevice().currentValue('voltage', true) as BigDecimal
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

@CompileStatic
void resetEnergyMonitors() {
  switchResetCounters()
}

@CompileStatic
void setBatteryPercent(Integer percent) {
  getDevice().sendEvent(name: 'battery', value: percent)
}

@CompileStatic
void setHumidityPercent(BigDecimal percent) {
  getDevice().sendEvent(name: 'humidity', value: percent.setScale(1, BigDecimal.ROUND_HALF_UP))
}

@CompileStatic
void setTemperatureC(BigDecimal tempC) {
  if(isCelciusScale()) {
    getDevice().sendEvent(name: 'temperature', value: tempC.setScale(1, BigDecimal.ROUND_HALF_UP))
  } else {
    getDevice().sendEvent(name: 'temperature', value: cToF(tempC).setScale(1, BigDecimal.ROUND_HALF_UP))
  }
}

@CompileStatic
void setTemperatureF(BigDecimal tempF) {
  if(!isCelciusScale()) {
    getDevice().sendEvent(name: 'temperature', value: tempF.setScale(1, BigDecimal.ROUND_HALF_UP))
  } else {
    getDevice().sendEvent(name: 'temperature', value: fToC(tempF).setScale(1, BigDecimal.ROUND_HALF_UP))
  }
}

@CompileStatic
void setPushedButton(Integer buttonPushed) {
  getDevice().sendEvent(name: 'pushed', value: buttonPushed, , isStateChange: true)
}

@CompileStatic
void setHeldButton(Integer buttonHeld) {
  getDevice().sendEvent(name: 'held', value: buttonHeld, isStateChange: true)
}

@CompileStatic
void setMotionOn(Boolean motion) {
  if(motion == true) {
    getDevice().sendEvent(name: 'motion', value: 'active')
  } else {
    getDevice().sendEvent(name: 'motion', value: 'inactive')
  }
}

@CompileStatic
void setTamperOn(Boolean tamper) {
  if(tamper == true) {
    getDevice().sendEvent(name: 'tamper', value: 'detected')
  } else {
    getDevice().sendEvent(name: 'tamper', value: 'clear')
  }
}

@CompileStatic
void setFloodOn(Boolean tamper) {
  if(tamper == true) {
    getDevice().sendEvent(name: 'water', value: 'wet')
  } else {
    getDevice().sendEvent(name: 'water', value: 'dry')
  }
}

@CompileStatic
void setIlluminance(Integer illuminance) {
  getDevice().sendEvent(name: 'illuminance', value: illuminance)
}

@CompileStatic
void setGasDetectedOn(Boolean tamper) {
  if(tamper == true) {
    getDevice().sendEvent(name: 'naturalGas', value: 'detected')
  } else {
    getDevice().sendEvent(name: 'naturalGas', value: 'clear')
  }
}

@CompileStatic
void setGasPPM(Integer ppm) {
  getDevice().sendEvent(name: 'ppm', value: ppm)
}

@CompileStatic
void setValvePosition(Boolean open, Integer valve = 0) {
  if(open == true) {
    getDevice().sendEvent(name: 'valve', value: 'open')
  } else {
    getDevice().sendEvent(name: 'valve', value: 'closed')
  }
}

// /////////////////////////////////////////////////////////////////////////////
// End Power Monitoring Getters and Setters
// /////////////////////////////////////////////////////////////////////////////

// /////////////////////////////////////////////////////////////////////////////
// Generic Getters and Setters
// /////////////////////////////////////////////////////////////////////////////
DeviceWrapper getDevice() { return this.device }

LinkedHashMap getDeviceSettings() { return this.settings }

String getDeviceDNI() { return this.device.getDeviceNetworkId() }

Boolean isCelciusScale() {
  getLocation().temperatureScale == 'C'
}

String getDeviceDataValue(String dataValueName) {
  return this.device.getDataValue(dataValueName)
}

void setDeviceDataValue(String dataValueName, String valueToSet) {
  this.device.updateDataValue(dataValueName, valueToSet)
}


Boolean hasPowerMonitoring() {
  return this.device.getDataValue('model') in powerMonitoringDevices
}

Boolean hasBluGateway() {
  return this.device.getDataValue('model') in bluGatewayDevices
}

String getBaseUri() {
  String ipBase = settings.ipAddress
  return "http://${ipBase}"
}
String getBaseUriRpc() {
  String ipBase = settings.ipAddress
  return "http://${ipBase}/rpc"
}

String getHubBaseUri() {
  return "http://${location.hub.localIP}:39501"
}

@CompileStatic
Long unixTimeSeconds() {
  return Instant.now().getEpochSecond()
}

@CompileStatic
String getWebSocketUri() {
  if(getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '') {return "ws://${getDeviceSettings()?.ipAddress}/rpc"}
  else {return null}
}
@CompileStatic
Boolean hasWebsocketUri() {
  return (getWebSocketUri() != null && getWebSocketUri().length() > 6)
}

@CompileStatic
Boolean hasIpAddress() {
  return (getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '' && ((String)getDeviceSettings()?.ipAddress).length() > 6)
}

@CompileStatic
String getIpAddress() {
  if(hasIpAddress()) {return getDeviceSettings().ipAddress} else {return null}
}

void setDeviceInfo(Map info) {
  if(info?.model) { this.device.updateDataValue('model', info.model)}
  if(info?.gen) { this.device.updateDataValue('gen', info.gen.toString())}
  if(info?.ver) { this.device.updateDataValue('ver', info.ver)}
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

@CompileStatic
void setSwitchState(Boolean on) {
  if(on != null && on != getSwitchState()) { getDevice().sendEvent(name: 'switch', value: on ? 'on' : 'off')}
}
@CompileStatic
Boolean getSwitchState() {
  return getDevice().currentValue('switch', true) == 'on'
}

@CompileStatic
void setLastUpdated() {
  getDevice().sendEvent(name: 'lastUpdated', value: nowFormatted())
}

void sendEventToShellyBluetoothHelper(String loc, Object value, String dni) {
  sendLocationEvent(name:loc, value:value, data:dni)
}
// /////////////////////////////////////////////////////////////////////////////
// End Generic Getters and Setters
// /////////////////////////////////////////////////////////////////////////////
// =============================================================================
// End Getters and Setters
// =============================================================================



// =============================================================================
// Logging Helpers
// =============================================================================
String loggingLabel() {
  if(device) {return "${device.label ?: device.name }"}
  if(app) {return "${app.label ?: app.name }"}
}

void logException(message) {log.exception "${loggingLabel()}: ${message}"}
void logError(message) {log.error "${loggingLabel()}: ${message}"}
void logWarn(message) {log.warn "${loggingLabel()}: ${message}"}
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

double nowDays() {
  return (now() / 86400000)
}

long unixTimeMillis() {
  return (now())
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

BigDecimal cToF(BigDecimal val) {
  return celsiusToFahrenheit(val)
}

BigDecimal fToC(BigDecimal val) {
  return fahrenheitToCelsius(val)
}

@CompileStatic
String base64Encode(String toEncode) {
  return toEncode.bytes.encodeBase64().toString()
}
// =============================================================================
// End Formatters, Custom 'RunEvery', and other helpers
// =============================================================================