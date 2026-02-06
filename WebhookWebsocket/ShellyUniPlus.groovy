/**
 * Version: 2.0.8
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus Uni (Websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Sensor'
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
  }
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true
