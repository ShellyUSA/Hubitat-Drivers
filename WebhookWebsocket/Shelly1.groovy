/**
 * Version: 2.16.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly 1', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean NOCHILDSWITCH = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url'
]