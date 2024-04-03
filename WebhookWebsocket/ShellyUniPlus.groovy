#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Uni Plus', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Sensor'
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability "RelativeHumidityMeasurement" //humidity - NUMBER, unit:%rh
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C

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
  if(getDeviceSettings().enableBluetooteGateway == null) { this.device.updateSetting('enableBluetooteGateway', true) }
}

void configure() {
  getOrSetPrefs()
  setDeviceDataValue('ipAddress', getIpAddress())

  // setSwitchState(postCommandSync(switchGetStatusCommand())?.output)
  // switchGetStatus()

  createChildSwitches()

  // if(getDeviceSettings().enableBluetooteGateway == true) {enableBluReportingToHE()}
  // else if(getDeviceSettings().enableBluetooteGateway == false) {disableBluReportingToHE()}
  initializeWebsocketConnection()
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

  try {processWebsocketMessagesPowerMonitoring(json)}
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