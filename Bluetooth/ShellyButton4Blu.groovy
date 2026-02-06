/**
 * Version: 2.0.5
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly Button 4 (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PushableButton' //numberOfButtons - NUMBER //pushed - NUMBER
    capability 'DoubleTapableButton' //doubleTapped - NUMBER
    capability 'HoldableButton' //held - NUMBER
    capability 'ReleasableButton' //released - NUMBER
    capability 'PresenceSensor' //presence - ENUM ["present", "not present"]

    command 'tripleTap', ['number']
    attribute 'tripleTapped', 'number'
    attribute 'lastUpdated', 'string'
  }
}
@Field static Boolean BLU = true
@Field static Integer BUTTONS = 4

@CompileStatic
void deviceSpecificConfigure() {
  if(getDeviceSettings().presenceTimeout == null) {setDeviceSetting('presenceTimeout', [type: 'number', value: 300])}
  sendDeviceEvent([name: 'numberOfButtons', value: 4])
  runEveryCustomSeconds(60, 'checkPresence')
}

