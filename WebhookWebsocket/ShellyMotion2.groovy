#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Motion 2', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability "Battery" //battery - NUMBER, unit:%
    capability "MotionSensor" //motion - ENUM ["inactive", "active"]
    capability "TamperAlert" //tamper - ENUM ["clear", "detected"]
    capability "IlluminanceMeasurement" //illuminance - NUMBER, unit:lx
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean GEN1 = true
// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() {configure()}
void configure() {
  this.device.setDeviceNetworkId(convertIPToHex(settings?.ipAddress))
  this.device.sendEvent(name: 'numberOfButtons', value: 3)
  setDeviceActionsGen1()
}

@CompileStatic
void parse(String raw) {
  getBatteryStatus()
  LinkedHashMap message = decodeLanMessage(raw)
  LinkedHashMap headers = message?.headers as LinkedHashMap
  logDebug("Message: ${message}")
  logDebug("Headers: ${headers}")
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  if(query[0] == 'motion_on') {setMotionOn(true)}
  else if(query[0] == 'motion_off') {setMotionOn(false)}
  else if(query[0] == 'tamper_alarm_on') {setTamperOn(true)}
  else if(query[0] == 'tamper_alarm_off') {setTamperOn(false)}
  setLastUpdated()
}

@CompileStatic
void getBatteryStatus() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('status')
  logJson(response)
  LinkedHashMap battery = (LinkedHashMap)response?.bat
  Integer percent = battery?.value as Integer
  setBatteryPercent(percent)
  Integer lux = ((LinkedHashMap)response?.lux)?.value as Integer
  setIlluminance(lux)
}

@CompileStatic
void setDeviceActionsGen1() {
  LinkedHashMap actions = getDeviceActionsGen1()
  logJson(actions)
  List<String> actionsToCreate = [
    'motion_off',
    'motion_on',
    'tamper_alarm_off',
    'tamper_alarm_on',
    'bright_condition',
    'dark_condition',
    'twilight_condition'
  ]
  actions.each{k,v ->
    if(!k in actionsToCreate) {return}
    String queryString = 'index=0&enabled=true'
    queryString += "&name=${k}".toString()
    queryString += "&urls[0][url]=${getHubBaseUri()}/${((String)k).replace('_url','')}".toString()
    queryString += "&urls[0][int]=0000-0000"
    sendGen1Command('settings/actions', queryString)
  }
}

