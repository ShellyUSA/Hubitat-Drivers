/**
 * Version: 2.0.7
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Temperature & Humidity Peripheral Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
