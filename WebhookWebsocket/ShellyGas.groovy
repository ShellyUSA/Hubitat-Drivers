#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Gas', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Refresh'
    capability "GasDetector" //naturalGas - ENUM ["clear", "tested", "detected"]
    capability "Valve" //valve - ENUM ["open", "closed"]
    attribute 'lastUpdated', 'string'
    attribute 'ppm', 'number'
    attribute 'selfTestState', 'string'
    command 'selfTest'
    command 'mute'
    command 'unmute'
    command 'getPrefsFromDevice'

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
  if(
    getDeviceSettings().gen1_set_volume != null
  ) {
    String queryString = "set_volume=${getDeviceSettings().gen1_set_volume}".toString()
    sendGen1Command('settings', queryString)
  }
}

@CompileStatic
void getPrefsFromDevice() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('settings')
  logJson(response)
  LinkedHashMap prefs = [
    'gen1_set_volume': response?.set_volume as Integer
  ]
  logJson(prefs)
  setDevicePreferences(prefs)
}

@CompileStatic
void parse(String raw) {
  LinkedHashMap message = decodeLanMessage(raw)
  LinkedHashMap headers = message?.headers as LinkedHashMap
  logDebug("Message: ${message}")
  logDebug("Headers: ${headers}")
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  if(query[0] == 'alarm_mild') {setGasDetectedOn(true)}
  else if(query[0] == 'alarm_heavy') {setGasDetectedOn(true)}
  else if(query[0] == 'alarm_off') {setGasDetectedOn(false)}
  getStatus()
  setLastUpdated()
}

@CompileStatic
void refresh() {getStatus()}

@CompileStatic
void getStatus() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('status')
  logJson(response)
  LinkedHashMap concentration = (LinkedHashMap)response?.concentration
  Integer ppm = concentration?.ppm as Integer
  setGasPPM(ppm)
  LinkedHashMap gas_sensor = (LinkedHashMap)response?.gas_sensor
  String self_test_state = gas_sensor?.self_test_state.toString()
  getDevice().sendEvent(name: 'selfTestState', value: self_test_state)
}

@CompileStatic
void setDeviceActionsGen1() {
  LinkedHashMap actions = getDeviceActionsGen1()
  logJson(actions)
  actions.each{k,v ->
    String queryString = 'index=0&enabled=true'
    queryString += "&name=${k}".toString()
    queryString += "&urls[]=${getHubBaseUri()}/${((String)k).replace('_url','')}".toString()
    sendGen1Command('settings/actions', queryString)
  }
}

@CompileStatic
void selfTest() {sendGen1Command('/self_test')}

@CompileStatic
void mute() {sendGen1Command('/mute')}

@CompileStatic
void unmute() {sendGen1Command('/unmute')}