#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Pro 3', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean WS = true
