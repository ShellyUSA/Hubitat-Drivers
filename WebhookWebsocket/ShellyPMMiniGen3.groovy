#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly PM Mini Gen 2&3 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'enableBluetooth'
    command 'resetEnergyMonitors'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean NOCHILDREN = true