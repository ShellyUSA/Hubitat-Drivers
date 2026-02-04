/**
 * Version: 2.0.2
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Cover PM Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'WindowShade' //windowShade - ENUM ['opening', 'partially open', 'closed', 'open', 'closing', 'unknown'] //position - NUMBER, unit:%
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
