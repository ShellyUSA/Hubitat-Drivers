#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Humidity Peripheral Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
