/**
 * Version: 2.0.6
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Analog Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'analogValue', 'number'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
