#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Button Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'PushableButton'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
void on() {parentPostCommandSync(switchSetCommand(true, getDeviceDataValue('inputSwitchId') as Integer))}
void off() {parentPostCommandSync(switchSetCommand(false, getDeviceDataValue('inputSwitchId') as Integer))}
// =============================================================================
// End Device Specific
// =============================================================================