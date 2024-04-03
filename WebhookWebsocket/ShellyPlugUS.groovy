#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plug US (websocket)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
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
<<<<<<< Updated upstream
}

void configure() {
  if(getDeviceDataValue('ipAddress') == null || getDeviceDataValue('ipAddress') != getIpAddress()) {
    getPrefsFromDevice()
  } else if(getDeviceDataValue('ipAddress') == getIpAddress()) {
    sendPrefsToDevice()
  }
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

  if(getDeviceSettings().enableBluetooteGateway == true) {enableBluReportingToHE()}
  else if(getDeviceSettings().enableBluetooteGateway == false) {disableBluReportingToHE()}
  initializeWebsocketConnection()
}

void sendPrefsToDevice() {
  if(
    getDeviceSettings().initial_state != null &&
    getDeviceSettings().auto_on != null &&
    getDeviceSettings().auto_on_delay != null &&
    getDeviceSettings().auto_off != null &&
    getDeviceSettings().auto_off_delay != null &&
    getDeviceSettings().power_limit != null &&
    getDeviceSettings().voltage_limit != null &&
    getDeviceSettings().autorecover_voltage_errors != null &&
    getDeviceSettings().current_limit != null
  ) {
    switchSetConfig(
      getDeviceSettings().initial_state,
      getDeviceSettings().auto_on,
      getDeviceSettings().auto_on_delay,
      getDeviceSettings().auto_off,
      getDeviceSettings().auto_off_delay,
      getDeviceSettings().power_limit,
      getDeviceSettings().voltage_limit,
      getDeviceSettings().autorecover_voltage_errors,
      getDeviceSettings().current_limit
    )
  }
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
=======
>>>>>>> Stashed changes
}
// =============================================================================
// End Initialization
// =============================================================================



// =============================================================================
// Device Specific
// =============================================================================
<<<<<<< Updated upstream
void parse(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  processWebsocketMessagesAuth(json)
  processWebsocketMessagesPowerMonitoring(json, 'switch:0')
  processWebsocketMessagesConnectivity(json)
  processWebsocketMessagesBluetoothEvents(json)
  logJson(json)
}

=======
>>>>>>> Stashed changes
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