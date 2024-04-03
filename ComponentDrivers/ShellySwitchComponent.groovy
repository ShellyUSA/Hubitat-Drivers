#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Switch Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Switch'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Initialization
// =============================================================================
@CompileStatic
void initialize() {
  Integer switchId = getDeviceDataValue('switchId') as Integer
  logDebug("Switch ID : ${switchId}")
  getDeviceCapabilities()
  getPrefsFromDevice()
}

@CompileStatic
void getDeviceCapabilities() {
  Map result = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand())?.result
  if(result != null && result.size() > 0) {
    setDeviceDataValue('capabilities', result.keySet().join(','))
  }
}
// =============================================================================
// End Initialization
// =============================================================================



// =============================================================================
// Device Specific
// =============================================================================
void on() {parentPostCommandSync(switchSetCommand(true, getDeviceDataValue('switchId') as Integer))}
void off() {parentPostCommandSync(switchSetCommand(false, getDeviceDataValue('switchId') as Integer))}
void refresh() {parentPostCommandSync(switchGetStatusCommand(getDeviceDataValue('switchId') as Integer))}
// =============================================================================
// End Device Specific
// =============================================================================