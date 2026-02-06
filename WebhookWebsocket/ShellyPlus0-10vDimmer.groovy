/**
 * Version: 2.0.5
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 0-10v Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true
