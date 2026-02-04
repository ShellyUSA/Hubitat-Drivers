/**
 * Version: 2.0.1
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Motion 2 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'MotionSensor' //motion - ENUM ['inactive', 'active']
    capability 'TamperAlert' //tamper - ENUM ['clear', 'detected']
    capability 'IlluminanceMeasurement' //illuminance - NUMBER, unit:lx
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_BATTERY_GEN1 = true
@Field static Boolean HAS_LUX_GEN1 = true
@Field static Boolean HAS_TEMP_GEN1 = true
@Field static Boolean HAS_MOTION_GEN1 = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'motion_off',
  'motion_on',
  'tamper_alarm_off',
  'tamper_alarm_on',
  'bright_condition',
  'dark_condition',
  'twilight_condition'
]
