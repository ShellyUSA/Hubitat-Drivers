#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly BLE Gateway (Websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
