#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly PM Mini Gen 3 (websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    command 'enableBluetooth'
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
<<<<<<< Updated upstream
  if(getDeviceDataValue('ipAddress') == null || getDeviceDataValue('ipAddress') != getIpAddress()) {
    getPrefsFromDevice()
  } else if(getDeviceDataValue('ipAddress') == getIpAddress()) {
    sendPrefsToDevice()
  }
=======
  getOrSetPrefs()
>>>>>>> Stashed changes
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

  if(getDeviceSettings().enableBluetoothGateway == true) {enableBluReportingToHE()}
  else if(getDeviceSettings().enableBluetoothGateway == false) {disableBluReportingToHE()}
  initializeWebsocketConnection()
}

<<<<<<< Updated upstream
void sendPrefsToDevice() {
}
=======
>>>>>>> Stashed changes

void getPrefsFromDevice() {
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
<<<<<<< Updated upstream
  processWebsocketMessagesAuth(json)
  processWebsocketMessagesPowerMonitoring(json, 'pm1:0')
  processWebsocketMessagesConnectivity(json)
  processWebsocketMessagesBluetoothEvents(json)
=======

  try {processWebsocketMessagesAuth(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesAuth(): ${prettyJson(json)}")}

  try {processWebsocketMessagesPowerMonitoring(json, 'pm1:0')}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesPowerMonitoring(): ${prettyJson(json)}")}

  try {processWebsocketMessagesConnectivity(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesConnectivity(): ${prettyJson(json)}")}

  try {processWebsocketMessagesBluetoothEvents(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesBluetoothEvents(): ${prettyJson(json)}")}

>>>>>>> Stashed changes
  logJson(json)
}

void refresh() { refreshDeviceSpecificInfo() }

void refreshDeviceSpecificInfo() {
  shellyGetDeviceInfo(true)
  pm1GetStatus()
}
// =============================================================================
// End Device Specific
// =============================================================================