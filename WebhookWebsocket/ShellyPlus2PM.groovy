#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 2 PM (Websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'

    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'resetEnergyMonitors'
    command 'getPreferencesFromShellyDevice'
    command 'deleteChildDevices'
  }
}

if(device != null) {preferences{}}
@Field static Boolean WS = true

// =============================================================================
// Device Specific
// =============================================================================
@CompileStatic
void on() { postCommandSync(switchSetCommand(true)) }

@CompileStatic
void off() { postCommandSync(switchSetCommand(false)) }

void refreshDeviceSpecificInfo() {
  switchGetConfig()
  shellyGetDeviceInfo(true)
  switchGetStatus()
}
// =============================================================================
// End Device Specific
// =============================================================================