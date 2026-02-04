/**
 * Version: 2.0.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Count Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'count', 'number'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
