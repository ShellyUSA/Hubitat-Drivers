/**
 * Version: 2.0.5
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus Wall Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Light'
  }
}

@Field static Boolean WS = true
@Field static Boolean NOCHILDREN = true
@Field static Boolean DEVICEISBLUGATEWAY = true