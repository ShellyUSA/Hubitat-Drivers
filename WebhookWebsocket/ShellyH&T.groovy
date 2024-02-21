#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly H&T Webhook', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability "Battery" //battery - NUMBER, unit:%
    capability "RelativeHumidityMeasurement" //humidity - NUMBER, unit:%rh
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
  BigDecimal temp = (BigDecimal)(((LinkedHashMap)response?.tmp)?.value)
  String tempUnits = (((LinkedHashMap)response?.tmp)?.units).toString()
  if(tempUnits == 'C') {
    setTemperatureC(temp)
  } else if(tempUnits == 'F') {
    setTemperatureF(temp)
  }
  BigDecimal hum = (BigDecimal)(((LinkedHashMap)response?.hum)?.value)
  setHumidityPercent(hum)

}

@CompileStatic
void setDeviceActionsGen1() {
  LinkedHashMap actions = getDeviceActionsGen1()
  logJson(actions)
  List<String> actionsToCreate = [
    'report_url'
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

