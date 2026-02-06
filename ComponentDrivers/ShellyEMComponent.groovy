/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly EM Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    attribute 'powerFactor', 'number'
    attribute 'apparentPower', 'number'
    attribute 'returnedEnergy', 'number' //unit:kWh
  }
}

@Field static Boolean COMP = true
