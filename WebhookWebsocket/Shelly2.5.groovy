/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly 2.5 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
  }
}

@Field static Boolean GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url',
  'roller_open_url',
  'roller_close_url',
  'roller_stop_url'
]