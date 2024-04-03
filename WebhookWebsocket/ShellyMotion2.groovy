#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Motion 2', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
<<<<<<< Updated upstream
=======
    capability 'Configuration'
>>>>>>> Stashed changes
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'MotionSensor' //motion - ENUM ['inactive', 'active']
    capability 'TamperAlert' //tamper - ENUM ['clear', 'detected']
    capability 'IlluminanceMeasurement' //illuminance - NUMBER, unit:lx
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
    command 'getPrefsFromDevice'
  }
}

@Field static Boolean GEN1 = true
<<<<<<< Updated upstream
// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() {configure()}
void configure() {
  this.device.setDeviceNetworkId(convertIPToHex(settings?.ipAddress))
  this.device.sendEvent(name: 'numberOfButtons', value: 3)
  setDeviceActionsGen1()

  if(getDeviceDataValue('ipAddress') == null || getDeviceDataValue('ipAddress') != getIpAddress()) {
    getPrefsFromDevice()
  } else if(getDeviceDataValue('ipAddress') == getIpAddress()) {
    sendPrefsToDevice()
  }
  setDeviceDataValue('ipAddress', getIpAddress())
}

@CompileStatic
void sendPrefsToDevice() {
  if(
    getDeviceSettings().gen1_motion_sensitivity != null &&
    getDeviceSettings().gen1_motion_blind_time_minutes != null &&
    getDeviceSettings().gen1_tamper_sensitivity != null
  ) {
    String queryString = "motion_sensitivity=${getDeviceSettings().gen1_motion_sensitivity}".toString()
    queryString += "&motion_blind_time_minutes${getDeviceSettings().gen1_motion_blind_time_minutes}".toString()
    queryString += "&tamper_sensitivity${getDeviceSettings().gen1_tamper_sensitivity}".toString()
    sendGen1Command('settings', queryString)
  }
}

@CompileStatic
void getPrefsFromDevice() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('settings')
  logJson(response)
  LinkedHashMap prefs = [
    'gen1_motion_sensitivity': ((LinkedHashMap)response?.motion)?.sensitivity as Integer,
    'gen1_motion_blind_time_minutes': ((LinkedHashMap)response?.motion)?.blind_time_minutes as Integer,
    'gen1_tamper_sensitivity': response?.tamper_sensitivity as Integer
  ]
  logJson(prefs)
  setDevicePreferences(prefs)
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
  BigDecimal temp = (BigDecimal)(((LinkedHashMap)response?.tmp)?.value)
  String tempUnits = (((LinkedHashMap)response?.tmp)?.units).toString()
  if(tempUnits == 'C') {
    setTemperatureC(temp)
  } else if(tempUnits == 'F') {
    setTemperatureF(temp)
  }
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
    if(k in actionsToCreate) {
      String queryString = 'index=0&enabled=true'
      queryString += "&name=${k}".toString()
      queryString += "&urls[0][url]=${getHubBaseUri()}/${((String)k).replace('_url','')}".toString()
      queryString += "&urls[0][int]=0000-0000"
      sendGen1Command('settings/actions', queryString)
    }
  }
}
=======
@Field static Boolean HAS_BATTERY_GEN1 = true
@Field static Boolean HAS_LUX_GEN1 = true
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_MOTION_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'motion_off',
  'motion_on',
  'tamper_alarm_off',
  'tamper_alarm_on',
  'bright_condition',
  'dark_condition',
  'twilight_condition'
]
>>>>>>> Stashed changes

