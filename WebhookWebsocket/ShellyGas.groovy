#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Gas', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability "GasDetector" //naturalGas - ENUM ["clear", "tested", "detected"]
    capability "Valve" //valve - ENUM ["open", "closed"]
    attribute 'lastUpdated', 'string'
    attribute 'ppm', 'number'
    attribute 'selfTestState', 'string'
    command 'selfTest'
    command 'mute'
    command 'unmute'
  }
}

if(device != null) {preferences{}}
@Field static Boolean GEN1 = true
// =============================================================================
// Initialize And Configure
// =============================================================================
@CompileStatic
void initialize() {configure()}

@CompileStatic
void configure() {allDevicesConfiguration()}

@CompileStatic
void parse(String raw) {parseGen1Message(raw)}

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