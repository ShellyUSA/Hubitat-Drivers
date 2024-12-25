#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 1', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}




@CompileStatic
void on() { postCommandSync(switchSetCommand(true)) }

@CompileStatic
void off() { postCommandSync(switchSetCommand(false)) }

void refreshDeviceSpecificInfo() {
  switchGetConfig('switchGetConfig-refreshDeviceSpecificInfo')
  shellyGetDeviceInfo(true)
  switchGetStatus()
}
