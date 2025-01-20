#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Switch Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
@CompileStatic
void on() {componentSwitchOn()}

@CompileStatic
void off() {componentSwitchOff()}
// =============================================================================
// End Device Specific
// =============================================================================