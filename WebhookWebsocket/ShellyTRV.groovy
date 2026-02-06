/**
 * Version: 2.0.5
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly TRV (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Refresh'
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    capability 'Valve' //valve - ENUM ['open', 'closed']
    capability 'ThermostatHeatingSetpoint' //heatingSetpoint - NUMBER, unit:°F || °C

    command 'setValvePosition', [[name: 'Position', type: 'NUMBER', description:"Position (0..100)", constraints:["NUMBER"]]]
    command 'setExternalTemperature', [[name: 'External Temperature Measurement', type: 'NUMBER']]
    attribute 'valvePosition', 'number'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_BATTERY_GEN1 = true
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_HUM_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'valve_open',
  'valve_close'
]
@Field static List<String> ACTIONS_TO_CREATE_ENABLED_TIMES = [
  'valve_open',
  'valve_close'
]