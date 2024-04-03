#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly BLE Gateway', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}

// =============================================================================
// Initialization
// =============================================================================
void initialize() {
  if(hasIpAddress()) {
    atomicState.initInProgress = true
    initializeWebsocketConnection()
  }
  if(getDeviceSettings().enableBluetooteGateway == null) { this.device.updateSetting('enableBluetooteGateway', true) }
  configure()
}

void configure() {
  if(getDeviceSettings().enableBluetooteGateway == true) {enableBluReportingToHE()}
  else if(getDeviceSettings().enableBluetooteGateway == false) {disableBluReportingToHE()}
}
// =============================================================================
// End Initialization
// =============================================================================



// =============================================================================
// Device Specific
// =============================================================================
void parse(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  processWebsocketMessagesAuth(json)
  processWebsocketMessagesConnectivity(json)
  processWebsocketMessagesBluetoothEvents(json)
  logJson(json)
  setLastUpdated()
}
// =============================================================================
// End Device Specific
// =============================================================================