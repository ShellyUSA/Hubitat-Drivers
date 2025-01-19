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

@Field static Boolean COMP = true
// =============================================================================
// Device Specific
// =============================================================================
void open() {parentPostCommandSync(coverOpenCommand(getIntegerDeviceSetting('coverId')))}

void close() {parentPostCommandSync(coverCloseCommand(getIntegerDeviceSetting('coverId')))}

void setPosition(BigDecimal position) {parentPostCommandSync(coverGoToPositionCommand(getIntegerDeviceSetting('coverId'), position as Integer))}

void startPositionChange(String direction) {
  if(direction == 'open') {open()}
  if(direction == 'close') {close()}
}

void stopPositionChange() {parentPostCommandSync(coverStopCommand(getIntegerDeviceSetting('coverId')))}
// =============================================================================
// End Device Specific
// =============================================================================