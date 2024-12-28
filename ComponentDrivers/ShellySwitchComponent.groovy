#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Switch Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
void on() {parentPostCommandSync(switchSetCommand(true, getDeviceDataValue('switchId') as Integer))}
void off() {parentPostCommandSync(switchSetCommand(false, getDeviceDataValue('switchId') as Integer))}
// =============================================================================
// End Device Specific
// =============================================================================