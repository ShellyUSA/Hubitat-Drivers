#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Button 1 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'PushableButton' //numberOfButtons - NUMBER //pushed - NUMBER
    capability 'HoldableButton' //held - NUMBER
    attribute 'lastUpdated', 'string'
    command 'setDeviceActionsGen1'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean BUTTONS = 3
@Field static Boolean HAS_BATTERY_GEN1 = true
if(device != null) {preferences{}}