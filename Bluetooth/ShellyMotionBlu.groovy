/**
 * Version: 2.0.8
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition(
    name: 'Shelly Motion (Blu)',
    namespace: 'ShellyUSA',
    author: 'Daniel Winks',
    component: true,
    importUrl:''
  ) {
    capability 'Battery' //battery - NUMBER, unit:%
    capability "MotionSensor" //motion - ENUM ["inactive", "active"]
    capability "IlluminanceMeasurement" //illuminance - NUMBER, unit:lx
    attribute 'lastUpdated', 'string'
  }
}
@Field static Boolean BLU = true
void deviceSpecificConfigure() { }

