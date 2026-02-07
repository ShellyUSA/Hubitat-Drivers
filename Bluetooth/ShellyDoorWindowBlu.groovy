/**
 * Version: 2.1.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly Door/Window (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability "ContactSensor" //contact - ENUM ["closed", "open"]
    capability "IlluminanceMeasurement" //illuminance - NUMBER, unit:lx
    attribute 'lastUpdated', 'string'
    attribute 'tilt', 'number'
  }
}
@Field static Boolean BLU = true
void deviceSpecificConfigure() { }
