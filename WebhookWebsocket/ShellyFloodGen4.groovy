/**
 * Version: 2.16.2
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Flood Gen4 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'Configuration'
    capability 'WaterSensor' //water - ENUM ["wet", "dry"]
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean NOCHILDREN = true
