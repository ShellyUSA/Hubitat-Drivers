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
  processWebsocketMessages(json)
  logJson(json)
  setLastUpdated()
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