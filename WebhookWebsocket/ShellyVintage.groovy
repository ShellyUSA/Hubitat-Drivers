/**
 * Version: 2.0.6
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Vintage (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Bulb'
    capability 'Light'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean NOCHILDREN = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
]
