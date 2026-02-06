/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Blu Gateway (Websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean WS = true
@Field static Boolean NOCHILDREN = true
@Field static Boolean DEVICEISBLUGATEWAY = true
