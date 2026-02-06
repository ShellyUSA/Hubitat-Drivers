/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Pro 3EM400', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    attribute 'returnedEnergy', 'number' //unit:kWh
    attribute 'apparentPower', 'number'
    command 'resetEnergyMonitors'
  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true
