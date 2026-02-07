/**
 * Version: 2.16.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Duo WW CW', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Bulb'
    capability 'Light'
    capability 'ColorTemperature'  //colorName - STRING colorTemperature - NUMBER, unit:°K
  }
}

@Field static Boolean GEN1 = true
@Field static Integer COOLTEMP = 6500
@Field static Integer WARMTEMP = 2700
@Field static Boolean NOCHILDREN = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
]
