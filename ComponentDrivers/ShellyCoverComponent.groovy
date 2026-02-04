/**
 * Version: 2.0.1
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Cover Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'WindowShade' //windowShade - ENUM ['opening', 'partially open', 'closed', 'open', 'closing', 'unknown'] //position - NUMBER, unit:%
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
