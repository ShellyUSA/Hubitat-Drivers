#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly Button 1 (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PushableButton' //numberOfButtons - NUMBER //pushed - NUMBER
    capability 'HoldableButton' //held - NUMBER
    capability 'PresenceSensor' //presence - ENUM ["present", "not present"]
    attribute 'lastUpdated', 'string'

  }
}
@Field static Boolean BLU = true
void deviceSpecificConfigure() {
  if(getDeviceSettings().presenceTimeout == null) {getDevice().updateSetting('presenceTimeout', [type: 'number', value: 300])}
  this.device.sendEvent(name: 'numberOfButtons', value: 3)
  runEveryCustomSeconds(60, 'checkPresence')
}

@CompileStatic
void checkPresence() {
  Event lastEvent = getDevice().events([max: 1])[0]
  if(((unixTimeMillis() - lastEvent.getUnixTime()) / 1000) > (getDeviceSettings().presenceTimeout as Integer)) {
    getDevice().sendEvent([name: 'presence', value: 'not present', isStateChange: false])
  }
}
