/**
 * Version: 2.0.1
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly H&T (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C
    attribute 'lastUpdated', 'string'
  }
}
@Field static Boolean BLU = true
void deviceSpecificConfigure() { }
