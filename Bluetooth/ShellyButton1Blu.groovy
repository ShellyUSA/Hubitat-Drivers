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

@CompileStatic
void deviceSpecificConfigure() {
  if(getDeviceSettings().presenceTimeout == null) {setDeviceSetting('presenceTimeout', [type: 'number', value: 300])}
  thisDevice().sendEvent(name: 'numberOfButtons', value: 3)
  runEveryCustomSeconds(60, 'checkPresence')
}

