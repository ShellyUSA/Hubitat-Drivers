/**
 * Version: 2.0.2
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly H&T (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_BATTERY_GEN1 = true
@Field static Boolean HAS_LUX_GEN1 = true
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_HUM_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'report_url'
]