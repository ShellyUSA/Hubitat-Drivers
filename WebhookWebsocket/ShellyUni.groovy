/**
 * Version: 2.0.3
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Uni (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Refresh'
    command 'setDeviceActionsGen1'
    command 'getPreferencesFromShellyDeviceGen1'
    command 'deleteHubitatWebhooksGen1'
    command 'deleteChildDevices'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_ADC_GEN1 = true
@Field static Boolean HAS_EXT_TEMP_GEN1 = true
@Field static Boolean HAS_EXT_HUM_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url',
  'adc_over_url',
  'adc_under_url',
  'ext_temp_over_url',
  'ext_temp_under_url',
  'ext_hum_over_url',
  'ext_hum_under_url'
]