/**
 * Version: 2.1.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly 1PM (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_PM_GEN1 = true
@Field static Boolean NOCHILDREN = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url'
]