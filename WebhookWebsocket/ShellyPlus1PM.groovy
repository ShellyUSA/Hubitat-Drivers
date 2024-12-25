#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 1 PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh

    command 'resetEnergyMonitors'
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
    getPrefsFromDevice()
    initializeWebsocketConnection()
  }
  if(getDeviceSettings().enablePowerMonitoring == null) { this.device.updateSetting('enablePowerMonitoring', true) }
  if(getDeviceSettings().resetMonitorsAtMidnight == null) { this.device.updateSetting('resetMonitorsAtMidnight', true) }
  if(getDeviceSettings().enableBluetoothGateway == null) { this.device.updateSetting('enableBluetoothGateway', true) }
}

void configure() {
  getOrSetPrefs()
  setDeviceDataValue('ipAddress', getIpAddress())

  if(getDeviceSettings().resetMonitorsAtMidnight != null && getDeviceSettings().resetMonitorsAtMidnight == true) {
    schedule('0 0 0 * * ?', 'switchResetCounters')
  } else {
    unschedule('switchResetCounters')
  }

  if(getDeviceSettings().enablePowerMonitoring == false) {
    setCurrent(-1)
    setPower(-1)
    setEnergy(-1)
  }

  setSwitchState(postCommandSync(switchGetStatusCommand())?.output)
  switchGetStatus()

  if(getDeviceSettings().enableBluetoothGateway == true) {enableBluReportingToHE()}
  else if(getDeviceSettings().enableBluetoothGateway == false) {disableBluReportingToHE()}
  initializeWebsocketConnection()
}

void getPrefsFromDevice() {
  Map switchConfig = postCommandSync(switchGetConfigCommand())
  if(switchConfig != null && switchConfig?.result != null) {
    setDevicePreferences(switchConfig.result)
  }

  Map deviceInfo = postCommandSync(shellyGetDeviceInfoCommand())
  if(deviceInfo != null && deviceInfo?.result != null) {
    setDeviceInfo(deviceInfo.result)
  }
}
// =============================================================================
// End Initialization
// =============================================================================



// =============================================================================
// Custom Commands
// =============================================================================
@CompileStatic
void updatePreferencesFromDevice() {
  Map json = postCommandSync(switchGetConfigCommand())
  LinkedHashMap result = (json != null && json?.result != null) ? json.result : null
  if(result != null) {setDevicePreferences(result)}
}

@CompileStatic
void updateDeviceWithPreferences() {
  sendPrefsToDevice()
}
// =============================================================================
// End Custom Commands
// =============================================================================



// =============================================================================
// Device Specific
// =============================================================================
void parse(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)

  try {processWebsocketMessagesAuth(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesAuth(): ${prettyJson(json)}")}

  try {processWebsocketMessagesPowerMonitoring(json, 'switch:0')}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesPowerMonitoring(): ${prettyJson(json)}")}

  try {processWebsocketMessagesConnectivity(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesConnectivity(): ${prettyJson(json)}")}

  try {processWebsocketMessagesBluetoothEvents(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesBluetoothEvents(): ${prettyJson(json)}")}

  logJson(json)
}

@CompileStatic
void on() { postCommandSync(switchSetCommand(true)) }

@CompileStatic
void off() { postCommandSync(switchSetCommand(false)) }

void refresh() { refreshDeviceSpecificInfo() }

void refreshDeviceSpecificInfo() {
  switchGetConfig('switchGetConfig-refreshDeviceSpecificInfo')
  shellyGetDeviceInfo(true)
  switchGetStatus()
}
// =============================================================================
// End Device Specific
// =============================================================================
