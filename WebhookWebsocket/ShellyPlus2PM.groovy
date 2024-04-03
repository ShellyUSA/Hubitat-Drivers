#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 2 PM (websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'resetEnergyMonitors'
    command 'getPrefsFromDevice'
  }
}

if(device != null) {preferences{}}

// =============================================================================
// Initialization
// =============================================================================
void initialize() {
  if(hasIpAddress()) {
    // getPrefsFromDevice()
    initializeWebsocketConnection()
  }
  if(getDeviceSettings().enablePowerMonitoring == null) { this.device.updateSetting('enablePowerMonitoring', true) }
  if(getDeviceSettings().resetMonitorsAtMidnight == null) { this.device.updateSetting('resetMonitorsAtMidnight', true) }
  if(getDeviceSettings().enableBluetooteGateway == null) { this.device.updateSetting('enableBluetooteGateway', true) }
}
// =============================================================================
// End Initialization
// =============================================================================



// =============================================================================
// Device Specific
// =============================================================================
@CompileStatic
void on() { postCommandSync(switchSetCommand(true)) }

@CompileStatic
void off() { postCommandSync(switchSetCommand(false)) }

void refresh() { refreshDeviceSpecificInfo() }

void refreshDeviceSpecificInfo() {
  switchGetConfig()
  shellyGetDeviceInfo(true)
  switchGetStatus()
}
// =============================================================================
// End Device Specific
// =============================================================================