/**
 * Version: 2.1.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus Smoke', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'Configuration'
    capability 'Initialize'
    capability 'SmokeDetector' //smoke - ENUM ["clear", "tested", "detected"]
    attribute 'lastUpdated', 'string'
  }
}
