#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus H&T (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability "Battery" //battery - NUMBER, unit:%
    capability "RelativeHumidityMeasurement" //humidity - NUMBER, unit:%rh
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean NOCHILDREN = true
