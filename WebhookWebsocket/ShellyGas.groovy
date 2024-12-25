#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Gas', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'GasDetector' //naturalGas - ENUM ['clear', 'tested', 'detected']
    capability 'Valve' //valve - ENUM ['open', 'closed']
    attribute 'lastUpdated', 'string'
    attribute 'ppm', 'number'
    attribute 'selfTestState', 'string'
    command 'selfTest'
    command 'mute'
    command 'unmute'
  }
}

@Field static Boolean GEN1 = true
if(device != null) {preferences{}}

@CompileStatic
void refreshDeviceSpecificInfo() {
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
void selfTest() {sendGen1Command('/self_test')}

@CompileStatic
void mute() {sendGen1Command('/mute')}

@CompileStatic
void unmute() {sendGen1Command('/unmute')}