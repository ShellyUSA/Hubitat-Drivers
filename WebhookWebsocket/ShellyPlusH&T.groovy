/**
 * Version: 2.0.4
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus H&T Gen 2&3 (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean NOCHILDREN = true
