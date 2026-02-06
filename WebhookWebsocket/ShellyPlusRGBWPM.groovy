/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus RGBW PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh

    command 'resetEnergyMonitors'
  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true

void deviceSpecificConfigure() {
  Integer index = 0
  setDeviceDataValue('hasPM','true')
  setDeviceDataValue('currentId', "${index}")
  setDeviceDataValue('energyId', "${index}")
  setDeviceDataValue('powerId', "${index}")
  setDeviceDataValue('voltageId', "${index}")
}