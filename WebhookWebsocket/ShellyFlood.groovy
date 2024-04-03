#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Flood Webhook', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    capability 'WaterSensor' //water - ENUM ["wet", "dry"]
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean GEN1 = true
<<<<<<< Updated upstream
// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() {configure()}
void configure() {
  this.device.setDeviceNetworkId(convertIPToHex(settings?.ipAddress))
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
}

@CompileStatic
void getPrefsFromDevice() {
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
  if(query[0] == 'report') {}
  else if(query[0] == 'flood_detected') {setFloodOn(true)}
  else if(query[0] == 'flood_gone') {setFloodOn(false)}
  setLastUpdated()
}

@CompileStatic
void getBatteryStatus() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('status')
  logJson(response)
  LinkedHashMap battery = (LinkedHashMap)response?.bat
  Integer percent = battery?.value as Integer
  if(percent != null){setBatteryPercent(percent)}
  BigDecimal temp = (BigDecimal)(((LinkedHashMap)response?.tmp)?.value)
  String tempUnits = (((LinkedHashMap)response?.tmp)?.units).toString()
  if(tempUnits == 'C') {
    if(temp != null){setTemperatureC(temp)}
  } else if(tempUnits == 'F') {
    if(temp != null){setTemperatureF(temp)}
  }
  Boolean flood = (Boolean)response?.flood
  if(flood != null){setFloodOn(flood)}
}
=======
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_FLOOD_GEN1 = true
// =============================================================================
// Initialize And Configure
// =============================================================================
@CompileStatic
void initialize() {configure()}

@CompileStatic
void configure() {allDevicesConfiguration()}

@CompileStatic
void parse(String raw) {parseGen1Message(raw)}
>>>>>>> Stashed changes

@CompileStatic
void setDeviceActionsGen1() {
  LinkedHashMap actions = getDeviceActionsGen1()
  logJson(actions)
  List<String> actionsToCreate = [
    'report_url',
    'flood_detected_url',
    'flood_gone_url'
  ]
  actions.each{k,v ->
    if(k in actionsToCreate) {
      String queryString = 'index=0&enabled=true'
      queryString += "&name=${k}".toString()
      queryString += "&urls[]=${getHubBaseUri()}/${((String)k).replace('_url','')}".toString()
      sendGen1Command('settings/actions', queryString)
    }
  }
}
<<<<<<< Updated upstream

=======
>>>>>>> Stashed changes
