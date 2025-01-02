#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Switch Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
void on() {
  logWarn('Cannot change state of an input on a Shelly device from Hubitat!')
  sendEvent(name: 'switch', value: 'off', isStateChange: false)
  }
void off() {
  logWarn('Cannot change state of an input on a Shelly device from Hubitat!')
  sendEvent(name: 'switch', value: 'on', isStateChange: false)
  }
// =============================================================================
// End Device Specific
// =============================================================================