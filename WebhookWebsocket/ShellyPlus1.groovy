#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus 1', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

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
  if(getDeviceSettings().resetMonitorsAtMidnight == null) { this.device.updateSetting('resetMonitorsAtMidnight', true) }
  if(getDeviceSettings().enableBluetooteGateway == null) { this.device.updateSetting('enableBluetooteGateway', true) }
}

void configure() {
  getOrSetPrefs()
  setDeviceDataValue('ipAddress', getIpAddress())

  setSwitchState(postCommandSync(switchGetStatusCommand())?.output)
  switchGetStatus()

  if(getDeviceSettings().enableBluetooteGateway == true) {runIn(3, 'enableBluReportingToHE', [overwrite: true])}
  else if(getDeviceSettings().enableBluetooteGateway == false) {runIn(3, 'disableBluReportingToHE', [overwrite: true])}
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
