#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly Door/Window (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability "ContactSensor" //contact - ENUM ["closed", "open"]
    capability "IlluminanceMeasurement" //illuminance - NUMBER, unit:lx
    attribute 'lastUpdated', 'string'
    attribute 'tilt', 'number'
  }
}
@Field static Boolean BLU = true
void initialize() { configure() }
void configure() {
  this.device.setDeviceNetworkId(getDeviceSettings().macAddress.replace(':','').toUpperCase())
  getDevice().updateSetting('macAddress', [type: 'string', value: getDeviceSettings().macAddress.replace(':','').toUpperCase()])
  this.device.sendEvent(name: 'numberOfButtons', value: 3)
}
