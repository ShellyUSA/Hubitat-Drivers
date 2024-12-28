#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Count Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'count', 'number'
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
void on() {logWarn('Cannot change state of an input on a Shelly device from Hubitat!')}
void off() {logWarn('Cannot change state of an input on a Shelly device from Hubitat!')}
// =============================================================================
// End Device Specific
// =============================================================================