/**
 * Version: 2.17.4
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 0-10v Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Light'

  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true

void deviceSpecificConfigure() {
  Integer index = 0
  setDeviceDataValue('switchId', "${index}")
  setDeviceDataValue('lightId', "${index}")
  setDeviceDataValue('switchLevelId', "${index}")
}
