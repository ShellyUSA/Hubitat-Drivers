/**
 * Version: 2.0.6
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Flood (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    capability 'WaterSensor' //water - ENUM ["wet", "dry"]
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_FLOOD_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'report_url',
  'flood_detected_url',
  'flood_gone_url'
]
