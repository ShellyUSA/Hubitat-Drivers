#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Button 1', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PushableButton' //numberOfButtons - NUMBER //pushed - NUMBER
    capability 'HoldableButton' //held - NUMBER
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
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  if(query[0] == 'shortpush') {setPushedButton(1)}
  else if(query[0] == 'double_shortpush') {setPushedButton(2)}
  else if(query[0] == 'triple_shortpush') {setPushedButton(3)}
  else if(query[0] == 'longpush') {setHeldButton(1)}
  setLastUpdated()
}

@CompileStatic
void getBatteryStatus() {
  LinkedHashMap response = (LinkedHashMap)sendGen1Command('status')
  logJson(response)
  LinkedHashMap battery = (LinkedHashMap)response?.bat
  Integer percent = battery?.value as Integer
  setBatteryPercent(percent)
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
