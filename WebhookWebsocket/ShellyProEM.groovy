/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Pro EM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    capability 'Switch'
    attribute 'returnedEnergy', 'number' //unit:kWh
    command 'resetEnergyMonitors'
  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true
