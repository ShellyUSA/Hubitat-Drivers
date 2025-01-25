#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Pro 4 PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'resetEnergyMonitors'
  }
}

@Field static Boolean WS = true
