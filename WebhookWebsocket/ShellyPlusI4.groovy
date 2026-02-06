/**
 * Version: 2.0.7
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus I4', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true
