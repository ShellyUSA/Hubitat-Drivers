#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Dimmer 2 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
  }
}

@Field static Boolean GEN1 = true
@Field static Integer BUTTONS = 1
@Field static List<String> ACTIONS_TO_CREATE = [
  'btn1_on_url',
  'btn1_off_url',
  'btn1_longpush_url',
  'btn1_shortpush_url',
  'btn2_on_url',
  'btn2_off_url',
  'btn2_longpush_url',
  'btn2_shortpush_url',
  'out_on_url',
  'out_off_url',
]
