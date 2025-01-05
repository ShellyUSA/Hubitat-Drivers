#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Temperature & Humidity Peripheral Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true

