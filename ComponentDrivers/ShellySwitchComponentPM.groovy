#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Switch PM Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'resetEnergyMonitors'
  }
}

if(device != null) {preferences{}}
@Field static Boolean COMP = true

// =============================================================================
// Device Specific
// =============================================================================
void on() {parent?.postCommandSync(switchSetCommand(true, getDeviceDataValue('switchId') as Integer))}
void off() {parent?.postCommandSync(switchSetCommand(false, getDeviceDataValue('switchId') as Integer))}
void refresh() {parent?.postCommandSync(switchGetStatusCommand(getDeviceDataValue('switchId') as Integer))}
// =============================================================================
// End Device Specific
// =============================================================================