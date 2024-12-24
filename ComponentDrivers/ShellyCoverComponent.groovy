#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Cover Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'WindowShade' //windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"] //position - NUMBER, unit:%
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
void open() {
  parentPostCommandSync(coverOpenCommand(getDeviceDataValue('switchId') as Integer))
}

void close() {
  parentPostCommandSync(coverCloseCommand(getDeviceDataValue('switchId') as Integer))
}

void setPosition(BigDecimal position) {
  parentPostCommandSync(coverGoToPositionCommand(getDeviceDataValue('switchId') as Integer, position as Integer))
}

void startPositionChange(String direction) {}

void stopPositionChange() {}
// =============================================================================
// End Device Specific
// =============================================================================