#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Analog Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================

// =============================================================================
// End Device Specific
// =============================================================================