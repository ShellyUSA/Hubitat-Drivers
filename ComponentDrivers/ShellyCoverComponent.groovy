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
@CompileStatic
void open() {parentPostCommandSync(coverOpenCommand(getIntegerDeviceSetting('coverId')))}

@CompileStatic
void close() {parentPostCommandSync(coverCloseCommand(getIntegerDeviceSetting('coverId')))}

@CompileStatic
void setPosition(BigDecimal position) {parentPostCommandSync(coverGoToPositionCommand(getIntegerDeviceSetting('coverId'), position as Integer))}

@CompileStatic
void startPositionChange(String direction) {
  if(direction == 'open') {open()}
  if(direction == 'close') {close()}
}

@CompileStatic
void stopPositionChange() {parentPostCommandSync(coverStopCommand(getIntegerDeviceSetting('coverId')))}
// =============================================================================
// End Device Specific
// =============================================================================