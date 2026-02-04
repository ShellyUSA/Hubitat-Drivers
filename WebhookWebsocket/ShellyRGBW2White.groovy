/**
 * Version: 2.0.2
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly RGBW2 White (Gen1)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
  }
}

@Field static Boolean GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url',
  'out_on_url',
  'out_off_url',
  'out_on_url',
  'out_off_url',
  'out_on_url',
  'out_off_url',
  'out_on_url',
  'out_off_url'
]
