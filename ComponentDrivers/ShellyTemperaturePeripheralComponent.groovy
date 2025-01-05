#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Temperature Peripheral Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
