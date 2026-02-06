/**
 * Version: 2.0.9
 */
library(
  name: 'ShellyUSA_Driver_Library',
  namespace: 'ShellyUSA',
  author: 'Daniel Winks',
  description: 'ShellyUSA Driver Library',
  importUrl: 'https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/WebhookWebsocket/ShellyUSA_Driver_Library.groovy'
)

/* #region Fields */
// MARK: Fields
@Field static Integer WS_CONNECT_INTERVAL = 600
@Field static List<Integer> WS_RECONNECT_BACKOFF_SEQUENCE = [3, 5, 10, 30, 60, 120, 300]
@Field static Integer WS_MAX_BACKOFF = 300
@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()
@Field static LinkedHashMap<String, LinkedHashMap> preferenceMap = [
  'switch_initial_state': [type: 'enum', title: 'State after power outage', options: ['off':'Power Off', 'on':'Power On', 'restore_last':'Previous State', 'match_input':'Match Input']],
  'switch_auto_off': [type: 'bool', title: 'Auto-OFF: after turning ON, turn OFF after a predefined time (in seconds)'],
  'switch_auto_off_delay': [type:'number', title: 'Auto-OFF Delay: delay before turning OFF'],
  'switch_auto_on': [type: 'bool', title: 'Auto-ON: after turning OFF, turn ON after a predefined time (in seconds)'],
  'switch_auto_on_delay': [type:'number', title: 'Auto-ON Delay: delay before turning ON'],
  'switch_in_mode': [type: 'enum', title: 'Mode of associated input', options: []],
  'autorecover_voltage_errors': [type: 'bool', title: 'Turn back ON after overvoltage if previously ON'],
  'current_limit': [type: 'number', title: 'Overcurrent protection in amperes'],
  'power_limit': [type: 'number', title: 'Overpower protection in watts'],
  'voltage_limit': [type: 'number', title: 'Volts, a limit that must be exceeded to trigger an overvoltage error'],
  'undervoltage_limit': [type: 'number', title: 'Volts, a limit that must be subceeded to trigger an undervoltage error'],
  'gen1_motion_sensitivity': [type: 'number', title: 'Motion sensitivity (1-256, lower is more sensitive)'],
  'gen1_motion_blind_time_minutes': [type: 'number', title: 'Motion cool down in minutes'],
  'gen1_tamper_sensitivity': [type: 'number', title: 'Tamper sensitivity (1-127, lower is more sensitive, 0 for disabled)'],
  'gen1_set_volume': [type: 'number', title: 'Speaker volume (1 (lowest) .. 11 (highest))'],
  'cover_maxtime_open': [type: 'number', title: 'Default timeout after which Cover will stop moving in open direction (0.1..300 in seconds)'],
  'cover_maxtime_close': [type: 'number', title: 'Default timeout after which Cover will stop moving in close direction (0.1..300 in seconds)'],
  'cover_initial_state': [type: 'enum', title: 'Defines Cover target state on power-on', options: ['open':'Cover will fully open', 'closed':'Cover will fully close', 'stopped':'Cover will not change its position']],
  'input_enable': [type: 'bool', title: 'When disabled, the input instance doesn\'t emit any events and reports status properties as null'],
  'input_invert': [type: 'bool', title: 'True if the logical state of the associated input is inverted, false otherwise. For the change to be applied, the physical switch has to be toggled once after invert is set. For type analog inverts percent range - 100% becomes 0% and 0% becomes 100%'],
  'input_type': [type: 'bool', title: 'True if the logical state of the associated input is inverted, false otherwise. For the change to be applied, the physical switch has to be toggled once after invert is set. For type analog inverts percent range - 100% becomes 0% and 0% becomes 100%'],
]
  // 'switch_in_mode': [type: 'enum', title: 'Mode of associated input', options: [momentary: 'momentary', follow: 'follow', flip: 'flip', detached: 'detached', cycle: 'cycle', activate: 'activate']]

@Field static ConcurrentHashMap<String, ArrayList<BigDecimal>> movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>()
@Field static String BLE_SHELLY_BLU = 'https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Bluetooth/ble-shelly-blu.js'

/* #endregion */
/* #region Capability Getters */
// MARK: Capability Getters
Boolean hasCapabilityBatteryGen1() { return HAS_BATTERY_GEN1 == true }
Boolean hasCapabilityLuxGen1() { return HAS_LUX_GEN1 == true }
Boolean hasCapabilityTempGen1() { return HAS_TEMP_GEN1 == true }
Boolean hasCapabilityHumGen1() { return HAS_HUM_GEN1 == true }
Boolean hasCapabilityMotionGen1() { return HAS_MOTION_GEN1 == true }
Boolean hasCapabilityFloodGen1() { return HAS_FLOOD_GEN1 == true }
Boolean hasNoChildrenNeeded() { return NOCHILDREN == true }
Boolean hasADCGen1() { return HAS_ADC_GEN1 == true }
Boolean hasPMGen1() { return HAS_PM_GEN1 == true }
Boolean hasExtTempGen1() { return HAS_EXT_TEMP_GEN1 == true }
Boolean hasExtHumGen1() { return HAS_EXT_HUM_GEN1 == true }

Integer getCoolTemp() { return COOLTEMP != null ? COOLTEMP : 6500 }
Integer getWarmTemp() { return WARMTEMP != null ? WARMTEMP : 3000 }

Boolean hasActionsToCreateList() { return ACTIONS_TO_CREATE != null }
List<String> getActionsToCreate() {
  if(hasActionsToCreateList() == true) { return ACTIONS_TO_CREATE }
  else {return []}
}
Boolean hasActionsToCreateEnabledTimesList() { return ACTIONS_TO_CREATE_ENABLED_TIMES != null }
List<String> getActionsToCreateEnabledTimes() {
  if(hasActionsToCreateEnabledTimesList() == true) { return ACTIONS_TO_CREATE_ENABLED_TIMES }
  else {return []}
}

Boolean deviceIsComponent() {return COMP == true}
Boolean deviceIsComponentInputSwitch() {return INPUTSWITCH == true}
Boolean deviceIsOverUnderSwitch() {return OVERUNDERSWITCH == true}

Boolean hasCapabilityBattery() { return device.hasCapability('Battery') == true }
Boolean hasCapabilityColorControl() { return device.hasCapability('ColorControl') == true }
Boolean hasCapabilityColorMode() { return device.hasCapability('ColorMode') == true }
Boolean hasCapabilityColorTemperature() { return device.hasCapability('ColorTemperature') == true }
Boolean hasCapabilityWhiteLevel() { return device.hasAttribute('whiteLevel') == true }
Boolean hasCapabilityLight() { return device.hasCapability('Light') == true }
Boolean hasCapabilitySwitch() { return device.hasCapability('Switch') == true }
Boolean hasCapabilityPresence() { return device.hasCapability('PresenceSensor') == true }
Boolean hasCapabilityValve() { return device.hasCapability('Valve') == true }
Boolean hasCapabilityCover() { return device.hasCapability('WindowShade') == true }
Boolean hasCapabilityThermostatHeatingSetpoint() { return device.hasCapability('ThermostatHeatingSetpoint') == true }
Boolean hasCapabilityCoverOrCoverChild() { return device.hasCapability('WindowShade') == true || getCoverChildren()?.size() > 0 }

Boolean hasCapabilityCurrentMeter() { return device.hasCapability('CurrentMeter') == true }
Boolean hasCapabilityPowerMeter() { return device.hasCapability('PowerMeter') == true }
Boolean hasCapabilityVoltageMeasurement() { return device.hasCapability('VoltageMeasurement') == true }
Boolean hasCapabilityEnergyMeter() { return device.hasCapability('EnergyMeter') == true }
Boolean hasCapabilityReturnedEnergyMeter() { return device.hasAttribute('returnedEnergy') == true }

Boolean deviceIsBluGateway() {return DEVICEISBLUGATEWAY == true}


@CompileStatic
LinkedHashMap getSwitchInModeOptions() {
  if(getSwitchChildrenCount() == 2 && getInputSwitchChildrenCount() == 2) {
    return [follow: 'follow', flip: 'flip', detached: 'detached', cycle: 'cycle', activate: 'activate']
  } else if(getSwitchChildrenCount() == 1 && getInputSwitchChildrenCount() == 1) {
    return [follow: 'follow', flip: 'flip', detached: 'detached', activate: 'activate']
  } else if(getSwitchChildrenCount() == 1 && getInputButtonChildrenCount() == 1) {
    return [momentary: 'momentary', detached: 'detached', activate: 'activate']
  }  else {
    return [momentary: 'momentary', follow: 'follow', flip: 'flip', detached: 'detached']
  }
}
/* #endregion */

/* #region Preferences */
// MARK: Preferences
if (device != null) {
  preferences {
    if(BLU == null && COMP == null) {
      input 'ipAddress', 'string', title: 'IP Address', required: true, defaultValue: ''
      if(GEN1 != null && GEN1 == true) {
        input(name: 'deviceUsername', type: 'string', title: 'Device Username (if enabled on device)', required: false, defaultValue: 'admin')
      }
      input(name: 'devicePassword', type: 'password', title: 'Device Password (if enabled on device)', required: false, defaultValue: '')
    } else if(BLU == true) {
      input(name: 'macAddress', type: 'string', title: 'MAC Address', required: true, defaultValue: '')
    }

    preferenceMap.each{ String k,LinkedHashMap v ->
      if(thisDeviceHasSetting(k) ) {
        if(v.type == 'enum' && k == 'switch_in_mode') {
          input(name: k, required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: getSwitchInModeOptions())
        } else if(v.type == 'enum') {
          input(name: k, required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: k, required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      }
    }

    if(thisDeviceOrChildrenHasPowerMonitoring() == true) {
      input(name: 'enablePowerMonitoring', type:'bool', title: 'Enable Power Monitoring', required: false, defaultValue: true)
      input(name: 'resetMonitorsAtMidnight', type:'bool', title: 'Reset Total Energy At Midnight', required: false, defaultValue: true)
    }

    if(hasChildSwitches() == true) {
      input(name: 'parentSwitchStateMode', type: 'enum', title: 'Parent Switch State Mode', options: ['allOn':'On when all children on', 'anyOn':'On when any child on'], defaultValue: 'anyOn')
    }

    if(deviceIsBluGateway() == true && deviceIsComponent() == false) {
      input(name: 'enableBluetoothGateway', type:'bool', title: 'Enable Bluetooth Gateway for Hubitat', required: false, defaultValue: true)
    }

    if(hasCapabilityPresence() == true) {
      input(name: 'presenceTimeout', type: 'number', title: 'Presence Timeout (minimum 300 seconds)', required: true, defaultValue: 300)
    }

    if(hasADCGen1() == true && deviceIsComponent() == false) {
      input(name: 'gen1_create_child_switch_dev_adc', type:'bool', title: 'Create child switch for adc over/under reports', required: false, defaultValue: false)
      input(name: 'gen1_adc_over_value', type: 'number', title: 'ADC value, in mV, over which to trigger adc_over_url', required: true, defaultValue: 1000)
      input(name: 'gen1_adc_under_value', type: 'number', title: 'ADC value, in mV, under which to trigger adc_under_url', required: true, defaultValue: 1000)
      input(name: 'gen1_adc_voltage_polling_child', type:'bool', title: 'Create child voltage device for ADC sensor, only supports polling on Gen 1 Shelly Devices', required: false, defaultValue: false)
    }
    if(hasPollingChildren() == true) {
      input(name: 'gen1_status_polling_rate', type: 'number', title: 'Polling rate for sensor child devices, in seconds, 0 to disable polling', required: true, defaultValue: 60)
    }
    if(hasExtTempGen1() == true) {
      input(name: 'gen1_create_child_switch_dev_temp', type:'bool', title: 'Create child switch for external temperature sensor over/under reports', required: false, defaultValue: false)
      input(name: 'gen1_ext_temp_over_value', type:'number', title: 'Temperature value over which to trigger ext_temp_over_url', required: true, defaultValue: 50)
      input(name: 'gen1_ext_temp_under_value', type:'number', title: 'Temperature value over which to trigger ext_temp_under_url', required: true, defaultValue: 50)
      input(name: 'gen1_temp_polling_child', type:'bool', title: 'Create child temperature device for temperature sensor(s), only supports polling on Gen 1 Shelly Devices', required: false, defaultValue: false)
    }
    if(hasExtHumGen1() == true) {
      input(name: 'gen1_create_child_switch_dev_humidity', type:'bool', title: 'Create child switch for external humidity sensor over/under reports', required: false, defaultValue: false)
      input(name: 'gen1_ext_hum_over_value', type:'number', title: 'Humidity value over which to trigger ext_hum_over_url', required: true, defaultValue: 50)
      input(name: 'gen1_ext_hum_under_value', type:'number', title: 'Humidity value over which to trigger ext_hum_under_url', required: true, defaultValue: 50)
      input(name: 'gen1_humidity_polling_child', type:'bool', title: 'Create child humidity device for humidity sensor, only supports polling on Gen 1 Shelly Devices. Will create combined temp&humidity if both are enabled.', required: false, defaultValue: false)
    }
    if(hasCapabilityCover() == true) {
      input(name: 'cover_invert_status', type:'bool', title: 'Invert open/closed state. If enabled open=0, 100=closed.', required: true, defaultValue: false)
    }
    if(hasCapabilityThermostatHeatingSetpoint() == true) {
      input(name: 'trv_temperature_offset', type:'number', title: 'Temperature offset', required: true, defaultValue: 0)
      input(name: 'trv_target_t_auto', type:'bool', title: 'Automatic temperature control', required: true, defaultValue: true)
      input(name: 'trv_ext_t_enabled', type:'bool', title: 'If temperature correction from external temp sensor is enabled', required: true, defaultValue: true)
      input(name: 'trv_display_brightness', type:'enum', title: 'Display brightness', required: true, options: [1:'1', 2:'2', 3:'3', 4:'4', 5:'5', 6:'6', 7:'7'])
      input(name: 'trv_temp_units', type:'enum', title: 'Temperature Units', required: true, options: ['C':'Celcius', 'F':'Fahrenheit'])
    }

    input(name: 'logEnable', type: 'bool', title: 'Enable Logging', required: false, defaultValue: true)
    input(name: 'debugLogEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: true)
    input(name: 'traceLogEnable', type: 'bool', title: 'Enable trace logging (warning: causes high hub load)', required: false, defaultValue: false)
    input(name: 'descriptionTextEnable', type: 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: false)
  }
}

@CompileStatic
void getDeviceCapabilities() {
  Map shellyConfig = (LinkedHashMap<String, Object>)parentPostCommandSync(shellyGetConfigCommand())?.result
  Map result = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand())?.result
  logDebug("Capabilities: ${result.keySet()}")
  if(result != null && result.size() > 0) {
    // setDeviceDataValue('capabilities', result.keySet().join(','))
  }
}

// MARK: Refresh
@CompileStatic
void refresh() {
  if(isGen1Device() == true) {
    logTrace('Refreshing status for gen1 device')
    refreshStatusGen1()
  } else {
    if(getWebsocketIsConnected() == true) {parentSendWsCommand(shellyGetStatusCommand())}
    else {parentPostCommandAsync(shellyGetStatusCommand(), 'getStatusGen2Callback')}
  }
  tryRefreshDeviceSpecificInfo()
}

void tryRefreshDeviceSpecificInfo() {try{refreshDeviceSpecificInfo()} catch(ex) {}}


/* #endregion */
/* #region Initialization */
// MARK: Initialize
@CompileStatic
void initialize() {
  // Skip getting preferences right after a reboot. With multiple Shelly this causes a flood of network traffic
  if(hasIpAddress() == true && uptime() > 60) {getPreferencesFromShellyDevice()}
  initializeSettingsToDefaults()
  if(hasIpAddress() == true) {
    initializeWebsocketConnectionIfNeeded()
    refresh()
  }
}

@CompileStatic
void initializeSettingsToDefaults() {
  if(thisDeviceOrChildrenHasPowerMonitoring() == true) {
    if(getDeviceSettings().enablePowerMonitoring == null) { setDeviceSetting('enablePowerMonitoring', true) }
    if(getDeviceSettings().resetMonitorsAtMidnight == null) { setDeviceSetting('resetMonitorsAtMidnight', true) }
  } else {
    removeDeviceSetting('enablePowerMonitoring')
    removeDeviceSetting('resetMonitorsAtMidnight')
  }

  if(hasParent() == false && isGen1Device() == false && hasIpAddress() == true) {
    // Battery devices will never have Blu gateway, and if 'hasBluGateway' has already been set, there's no need to check again...
    if(hasCapabilityBattery() == false && getDeviceDataValue('hasBluGateway') == null) {
      if(deviceIsBluGateway() == true && getDeviceSettings().enableBluetoothGateway == null) {
        setDeviceSetting('enableBluetoothGateway', true)
      } else { removeDeviceSetting('enableBluetoothGateway') }
    }
  }

  if(hasChildSwitches() == true) {
    if(getDeviceSettings().parentSwitchStateMode == null) {setDeviceSetting('parentSwitchStateMode', 'anyOn')}
  } else {
    removeDeviceSetting('parentSwitchStateMode')
  }

  if(hasPollingChildren() == true) {
    if(getDeviceSettings().gen1_status_polling_rate == null) { setDeviceSetting('gen1_status_polling_rate', 60) }
    runEveryCustomSeconds(getDeviceSettings().gen1_status_polling_rate as Integer, 'refresh')
  }

  // Schedule daily websocket connection stats logging at midnight
  if(hasParent() == false && wsShouldBeConnected() == true) {
    scheduleTask('0 0 0 * * ?', 'logWebsocketConnectionStats')
  }

  logTrace('Finished initializing settings to defaults')
}

@CompileStatic
void configureNightlyPowerMonitoringReset() {
  logDebug('Power monitoring device detected...')
  if(getBooleanDeviceSetting('enablePowerMonitoring') == true) {
    logDebug('Power monitoring is enabled in device preferences...')
    if(getBooleanDeviceSetting('resetMonitorsAtMidnight') == true) {
      logDebug('Nightly power monitoring reset is enabled in device preferences, creating nightly reset task...')
      scheduleTask('0 0 0 * * ?', 'switchResetCounters')
    } else {
      logDebug('Nightly power monitoring reset is disabled in device preferences, removing nightly reset task...')
      unscheduleTask('switchResetCounters')
    }
  } else {
    logDebug('Power monitoring is disabled in device preferences, setting current, power, and energy to zero...')
    logDebug('Hubitat does not allow for dynamically enabling/disabling device capabilities. Disabling power monitoring in device drivers require setting attribute to zero and not processing incoming power monitoring data.')
    setCurrent(0 as BigDecimal)
    setPowerAttribute(0 as BigDecimal)
    setEnergyAttribute(0 as BigDecimal)
    setFrequency(0 as BigDecimal)
    unscheduleTask('switchResetCounters')
    if(wsShouldBeConnected() == false) {
      logDebug('Websocket connection no longer required, removing any WS connection checks and closing connection...')
      unscheduleTask('checkWebsocketConnection')
      wsClose()
    }
  }
}

void parentGetPreferencesFromShellyDevice() {
  if(hasParent() == true) {parent?.getPreferencesFromShellyDevice()}
  else {getPreferencesFromShellyDevice()}
}

@CompileStatic
Boolean checkDeviceForPM(Integer id) {
  if(isGen1Device() == true) {
    return hasPMGen1()
  } else {
    Map<String, Object> switchStatus = postCommandSync(switchGetStatusCommand(id))
    logTrace("Switch Status: ${prettyJson(switchStatus)}")
    Map<String, Object> switchStatusResult = (LinkedHashMap<String, Object>)switchStatus?.result
    return ('apower' in switchStatusResult.keySet())
  }
}

@CompileStatic
void getPreferencesFromShellyDevice() {
  logDebug('Getting device info...')
  Map shellyResults = (LinkedHashMap<String, Object>)sendGen1Command('shelly')
  logDebug("Shelly Device Info Result: ${prettyJson(shellyResults)}")
  if(shellyResults != null && shellyResults.size() > 0) {
    setDeviceInfo(shellyResults)
    Integer gen = shellyResults?.gen as Integer
    if(gen != null && gen > 1) {
      logDebug('Device is generation 2 or newer... Getting current config from device...')
      Map shellyGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(shellyGetConfigCommand())?.result
      logDebug("Shelly.GetConfig Result: ${prettyJson(shellyGetConfigResult)}")

      Set<String> switches = shellyGetConfigResult.keySet().findAll{it.startsWith('switch')}
      Set<String> inputs = shellyGetConfigResult.keySet().findAll{it.startsWith('input')}
      Set<String> covers = shellyGetConfigResult.keySet().findAll{it.startsWith('cover')}
      Set<String> temps = shellyGetConfigResult.keySet().findAll{it.startsWith('temperature')}
      Set<String> hums = shellyGetConfigResult.keySet().findAll{it.startsWith('humidity')}
      Set<String> pm1s = shellyGetConfigResult.keySet().findAll{it.startsWith('pm1')}
      Set<String> em1s = shellyGetConfigResult.keySet().findAll{it.startsWith('em1:')}
      Set<String> ems = shellyGetConfigResult.keySet().findAll{it.startsWith('em:')}
      Set<String> lights = shellyGetConfigResult.keySet().findAll{it.startsWith('light')}
      Set<String> rgbs = shellyGetConfigResult.keySet().findAll{it.startsWith('rgb:')}
      Set<String> rgbws = shellyGetConfigResult.keySet().findAll{it.startsWith('rgbw')}

      logDebug("Found Switches: ${switches}")
      logDebug("Found Inputs: ${inputs}")
      logDebug("Found Covers: ${covers}")
      logDebug("Found Temperatures: ${temps}")
      logDebug("Found Humidites: ${hums}")
      logDebug("Found PM1s: ${pm1s}")
      logDebug("Found EM1s: ${em1s}")
      logDebug("Found EMs: ${ems}")
      logDebug("Found Lights: ${lights}")
      logDebug("Found RGBs: ${rgbs}")
      logDebug("Found RGBWs: ${rgbws}")

      if(switches?.size() > 0) {
        logDebug('One or more switches found, running Switch.GetConfig for each...')
        switches.each{ swi ->
          Integer id = swi.tokenize(':')[1] as Integer
          Boolean hasPM = checkDeviceForPM(id)
          logDebug("Running Switch.GetConfig for switch ID: ${id}")
          Map switchGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand(id))?.result
          if(switchGetConfigResult != null && switchGetConfigResult?.size() > 0 && hasNoChildrenNeeded() == false) {
            logDebug('Creating child device for switch...')
            logTrace("Switch.GetConfig Result: ${prettyJson(switchGetConfigResult)}")
            ChildDeviceWrapper child = null
            if(hasPM == true) {
              child = createChildPmSwitch(id)
            } else {
              child = createChildSwitch(id)
            }
            if(child != null) {setChildDevicePreferences(switchGetConfigResult, child)}
          } else {
            setDeviceDataValue('switchId', "${id}")
            if(hasPM == true) {
              setDeviceDataValue('hasPM','true')
              setDeviceDataValue('currentId', "${id}")
              setDeviceDataValue('energyId', "${id}")
              setDeviceDataValue('powerId', "${id}")
              setDeviceDataValue('voltageId', "${id}")
              setDeviceDataValue('frequencyId', "${id}")
            }
          }
        }
      } else {
        logDebug('No switches found...')
      }

      if(inputs?.size() > 0) {
        logDebug('One or more inputs found, running Input.GetConfig for each...')
        if(hasNoChildrenNeeded() == false) {
          inputs?.each{ inp ->
            Integer id = inp.tokenize(':')[1] as Integer
            logDebug("Input ID: ${id}")
            Map inputGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(inputGetConfigCommand(id))?.result
            logTrace("Input.GetConfig Result: ${prettyJson(inputGetConfigResult)}")
            logDebug('Creating child device for input...')
            LinkedHashMap inputConfig = (LinkedHashMap)shellyGetConfigResult[inp]
            String inputType = (inputConfig?.type as String).capitalize()
            ChildDeviceWrapper child = createChildInput(id, inputType)
            // Map switchGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand(id))?.result
            if(inputGetConfigResult != null && inputGetConfigResult.size() > 0) {setChildDevicePreferences(inputGetConfigResult, child)}
          }
        }
      } else {
        logDebug('No inputs found...')
      }

      if(covers?.size() > 0) {
        logDebug('Cover(s) found, running Cover.GetConfig for each...')
        if(hasNoChildrenNeeded() == false) {
          covers?.each{ cov ->
            Integer id = cov.tokenize(':')[1] as Integer
            logDebug("Cover ID: ${id}")
            Map coverGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(coverGetConfigCommand(id))?.result
            Map<String, Object> coverStatus = postCommandSync(coverGetStatusCommand(id))
            logTrace("Cover Status: ${prettyJson(coverStatus)}")
            Map<String, Object> coverStatusResult = (LinkedHashMap<String, Object>)coverStatus?.result
            Boolean hasPM = ('apower' in coverStatusResult.keySet())
            logTrace("Cover.GetConfig Result: ${prettyJson(coverGetConfigResult)}")
            logDebug('Creating child device for cover...')
            ChildDeviceWrapper child = null
            if(coverGetConfigResult != null && coverGetConfigResult.size() > 0) {setChildDevicePreferences(coverGetConfigResult, child)}
            if(hasPM == true) {
              child = createChildPmCover(id)
            } else {
              child = createChildCover(id)
            }
          }
        }
      } else {
        logDebug('No covers found...')
      }

      if(temps?.size() > 1 && hasNoChildrenNeeded() == false) {
        logDebug('Temperature(s) found, running Temperature.GetConfig for each...')
        temps?.each{ temp ->
          Integer id = temp.tokenize(':')[1] as Integer
          logDebug("Temperature ID: ${id}")
          Map tempGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(temperatureGetConfigCommand(id))?.result
          logDebug("Temperature.GetConfig Result: ${prettyJson(tempGetConfigResult)}")
          logDebug('Creating child device for temperature...')
          ChildDeviceWrapper child = createChildTemperature(id)
          if(tempGetConfigResult != null && tempGetConfigResult.size() > 0) {setChildDevicePreferences(tempGetConfigResult, child)}
        }
      } else if(temps?.size() == 1 && hums.size() == 1) {
        temps?.each{ temp ->
          Integer id = temp.tokenize(':')[1] as Integer
          logDebug("Temperature ID: ${id}")
          Map tempGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(temperatureGetConfigCommand(id))?.result
          Map humGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(humidityGetConfigCommand(id))?.result
          logDebug("Temperature.GetConfig Result: ${prettyJson(tempGetConfigResult)}")
          logDebug('Creating child device for temperature and humidity...')
          logDebug("Humidity.GetConfig Result: ${prettyJson(humGetConfigResult)}")
          ChildDeviceWrapper child = createChildTemperatureHumidity(id)
          if(tempGetConfigResult != null && tempGetConfigResult.size() > 0) {setChildDevicePreferences(tempGetConfigResult, child)}
          if(humGetConfigResult != null && humGetConfigResult.size() > 0) {setChildDevicePreferences(humGetConfigResult, child)}
        }
      } else if(temps?.size() == 1 && hums.size() == 1 && hasNoChildrenNeeded() == true) {
        Integer id = temps[0].tokenize(':')[1] as Integer
        setDeviceDataValue('temperatureId', "${id}")
        setDeviceDataValue('humidityId', "${id}")
      }

      if(pm1s?.size() == 1 && hasNoChildrenNeeded() == true) {
        Integer id = pm1s[0].tokenize(':')[1] as Integer
        logDebug('PM1 found, configuring device IDs')
        setDeviceDataValue('currentId', "${id}")
        setDeviceDataValue('powerId', "${id}")
        setDeviceDataValue('voltageId', "${id}")
        setDeviceDataValue('frequencyId', "${id}")
      }

      if(em1s?.size() > 0) {
        em1s.each{ em1 ->
          Integer id = em1.tokenize(':')[1] as Integer
          logTrace("EM1 ID: ${id}")
          String k = "em1:${id}".toString()
          logTrace("Shelly Config Result Key: ${k}")
          LinkedHashMap<String, Object> em1Config = (LinkedHashMap<String, Object>)shellyGetConfigResult[k]
          logTrace("EM1Config: ${prettyJson(em1Config)}")
          logDebug("Creating child device for em1:${id}...")
          ChildDeviceWrapper child = createChildEM1(id)
        }
      }

      if(ems?.size() > 0) {
        ems.each{ em ->
          Integer id = em.tokenize(':')[1] as Integer
          logTrace("EM ID: ${id}")
          String k = "em:${id}".toString()
          logTrace("Shelly Config Result Key: ${k}")
          LinkedHashMap<String, Object> emConfig = (LinkedHashMap<String, Object>)shellyGetConfigResult[k]
          logTrace("EMConfig: ${prettyJson(emConfig)}")
          logDebug("Creating child devices for em:${id}...")
          ['a','b','c'].eachWithIndex{ phase, index ->
            ChildDeviceWrapper child = createChildEM(index, phase)
          }
        }
      }

      if(lights?.size() > 0) {
        logDebug('One or more lights found...')
        lights?.each{ light ->
          Integer id = light.tokenize(':')[1] as Integer
          logTrace("Light ID: ${id}")
          if(hasNoChildrenNeeded() == false) {
            LinkedHashMap<String, Object> lightConfig = (LinkedHashMap<String, Object>)shellyGetConfigResult[light]
            logTrace("LightConfig: ${prettyJson(lightConfig)}")
            logDebug("Creating child device for light:${id}...")
            ChildDeviceWrapper child = createChildDimmer(id)
          } else {
            setDeviceDataValue('switchLevelId', "${id}")
          }
        }
      } else {
        logDebug('No lights found...')
      }

      if(rgbs?.size() > 0) {
        logDebug('One or more RGBs found...')
        if(hasNoChildrenNeeded() == false) {
          rgbs?.each{ rgb ->
            Integer id = rgb.tokenize(':')[1] as Integer
            logTrace("RGB ID: ${id}")
            LinkedHashMap<String, Object> rgbConfig = (LinkedHashMap<String, Object>)shellyGetConfigResult[rgb]
            logTrace("RGBConfig: ${prettyJson(rgbConfig)}")
            logDebug("Creating child device for rgb:${id}...")
            ChildDeviceWrapper child = createChildRGB(id)
          }
        }
      } else {
        logDebug('No RGBs found...')
      }

      if(rgbws?.size() > 0) {
        logDebug('One or more RGBWs found...')
        if(hasNoChildrenNeeded() == false) {
          rgbws?.each{ rgbw ->
            Integer id = rgbw.tokenize(':')[1] as Integer
            logTrace("RGBW ID: ${id}")
            LinkedHashMap<String, Object> rgbwConfig = (LinkedHashMap<String, Object>)shellyGetConfigResult[rgbw]
            logTrace("RGBWConfig: ${prettyJson(rgbwConfig)}")
            logDebug("Creating child device for rgbw:${id}...")
            ChildDeviceWrapper child = createChildRGBW(id)
          }
        }
      } else {
        logDebug('No RGBWs found...')
      }
    } else {
      getPreferencesFromShellyDeviceGen1()
    }
  }
}

@CompileStatic
void getPreferencesFromShellyDeviceGen1() {
  LinkedHashMap gen1SettingsResponse = (LinkedHashMap)sendGen1Command('settings')
  logTrace("Gen 1 Settings ${prettyJson(gen1SettingsResponse)}")
  // Get preferences from Shelly
  LinkedHashMap prefs = [:]
  LinkedHashMap motion = (LinkedHashMap)gen1SettingsResponse?.motion
  LinkedHashMap display = (LinkedHashMap)gen1SettingsResponse?.display
  List<LinkedHashMap> thermostats = (List<LinkedHashMap>)gen1SettingsResponse?.thermostats
  if(motion != null) {
    prefs['gen1_motion_sensitivity'] = motion?.sensitivity as Integer
    prefs['gen1_motion_blind_time_minutes'] = motion?.blind_time_minutes as Integer
  }
  if(gen1SettingsResponse?.tamper_sensitivity != null) {
    prefs['gen1_tamper_sensitivity'] = gen1SettingsResponse?.tamper_sensitivity as Integer
  }
  if(gen1SettingsResponse?.set_volume != null) {prefs['gen1_set_volume'] = gen1SettingsResponse?.set_volume as Integer}
  if(display != null) {
    if(display?.brightness != null) {prefs['trv_display_brightness'] = display?.brightness as Integer}
  }
  if(thermostats != null) {
    thermostats.eachWithIndex{ tstat, index ->
      LinkedHashMap target_t = (LinkedHashMap)tstat?.target_t
      if(target_t?.enabled != null) {prefs['trv_target_t_auto'] = target_t?.enabled as Boolean}
      if(target_t?.units != null) {prefs['trv_temp_units'] = target_t?.units}

      LinkedHashMap ext_t = (LinkedHashMap)tstat?.ext_t
      if(ext_t?.enabled != null) {prefs['trv_ext_t_enabled'] = ext_t?.enabled as Boolean}

      if(tstat?.temperature_offset != null) {prefs['trv_temperature_offset'] = tstat?.temperature_offset as BigDecimal}
    }
  }

  if(prefs.size() > 0) { setHubitatDevicePreferences(prefs) }



  //Process ADC(s)
  List adcs = (List)gen1SettingsResponse?.adcs
  if(adcs != null && adcs.size() > 0) {
    if(getDeviceSettings()?.gen1_adc_voltage_polling_child == true) {
      adcs.eachWithIndex{ it, index ->
        logTrace("Creating polling child for ADC:${index}")
        createChildVoltage(index)
      }
    }
    if(getDeviceSettings()?.gen1_create_child_switch_dev_adc == true) {
      adcs.eachWithIndex{ it, index ->
        logTrace("Creating switch child for ADC:${index}")
        ChildDeviceWrapper child = createChildSwitch(index, "ADC ${index}".toString())
        child.updateDataValue('adcSwitchId',"${index}")
      }
    }
  }

  //Process Temp Sensors
  LinkedHashMap temps = (LinkedHashMap)gen1SettingsResponse?.ext_temperature
  //Get humidity, if present, to determine if ext sensor is single DHT22 or 1-3 DS18B20
  LinkedHashMap hums = (LinkedHashMap)gen1SettingsResponse?.ext_humidity
  Boolean isDHT22 = hums?.size() > 0
  if(temps != null && temps?.size() > 0) {
    if(getBooleanDeviceSetting('gen1_temp_polling_child') == true) {
      if(isDHT22 == true && getBooleanDeviceSetting('gen1_humidity_polling_child') == true) {
        temps.eachWithIndex{ it, index ->
          logTrace("Creating polling child for Temperature&Humidity:${index}")
          createChildTemperatureHumidity(index)
        }
      } else {
        temps.eachWithIndex{ it, index ->
          logTrace("Creating polling child for Temperature:${index}")
          createChildTemperature(index)
        }
      }
    }
    if(getBooleanDeviceSetting('gen1_create_child_switch_dev_temp') == true) {
      temps.eachWithIndex{ it, index ->
        logTrace("Creating switch child for Temperature:${index}")
        ChildDeviceWrapper child = createChildSwitch(index, "Temperature ${index}".toString())
        child.updateDataValue('temperatureSwitchId',"${index}")
      }
    }
    if(getBooleanDeviceSetting('gen1_humidity_polling_child') == true && getBooleanDeviceSetting('gen1_temp_polling_child') == false) {
      if(isDHT22 == true) {
        hums.eachWithIndex{ it, index ->
          logTrace("Creating humidity sensor child for Humidity:${index}")
          ChildDeviceWrapper child = createChildHumidity(index)
        }
      }
    }
    if(getBooleanDeviceSetting('gen1_create_child_switch_dev_humidity') == true) {
      hums.eachWithIndex{ it, index ->
        logTrace("Creating switch child for Humidity:${index}")
        ChildDeviceWrapper child = createChildSwitch(index, "Humidity ${index}".toString())
        child.updateDataValue('humiditySwitchId',"${index}")
      }
    }
  }

  List relays = (List)gen1SettingsResponse?.relays
  String mode = gen1SettingsResponse?.mode
  if(relays != null && relays.size() > 0) {
    relays.eachWithIndex{ it, index ->
      if(mode == 'roller') {
        if(index == 0) {createChildCover(index)}
      } else if(relays.size() == 1 && hasNoChildrenNeeded() == true && hasCapabilitySwitch() == true) {
        setDeviceDataValue('switchId', "${index}")
        if(hasPMGen1() == true) {
          setDeviceDataValue('hasPM','true')
          setDeviceDataValue('currentId', "${index}")
          setDeviceDataValue('energyId', "${index}")
          setDeviceDataValue('powerId', "${index}")
          setDeviceDataValue('voltageId', "${index}")
          setDeviceDataValue('frequencyId', "${index}")
        }
      } else {
        createChildSwitch(index)
      }

      if(((LinkedHashMap)it)?.btn_type in ['momentary', 'momentary_on_release', 'detached']) {
        createChildInput(index, "Button")
      } else {
        createChildInput(index, "Switch")
      }
    }
  }

  List lights = (List)gen1SettingsResponse?.lights
  if(lights != null && lights.size() > 0 && hasNoChildrenNeeded() == true) {
    lights.eachWithIndex{ it, index ->
      setDeviceDataValue('switchId', "${index}")
      setDeviceDataValue('lightId', "${index}")
      setDeviceDataValue('switchLevelId', "${index}")
      if(hasCapabilityColorControl() == true) {
        setDeviceDataValue('rgbwId', "${index}")
        setDeviceDataValue('lightMode', mode)
      }
      if(hasCapabilityColorTemperature() == true) {
        setDeviceDataValue('ctId', "${index}")
      }
    }
  } else if(lights != null && lights.size() > 0 && hasNoChildrenNeeded() == false) {
    lights.eachWithIndex{ it, index ->
      ChildDeviceWrapper child = createChildDimmer(index)
      setChildDeviceDataValue(child, 'switchId', "${index}")
      setChildDeviceDataValue(child, 'lightId', "${index}")
      setChildDeviceDataValue(child, 'switchLevelId', "${index}")
    }
  }

  LinkedHashMap gen1StatusResponse = (LinkedHashMap)sendGen1Command('status')

  List inputs = (List)gen1StatusResponse?.inputs
  if(inputs != null && inputs.size() > 0) {
    inputs.eachWithIndex{ it, index ->
      createChildInput(index, "Button")
      createChildInput(index, "Switch")
    }
  }

  refresh()
}

/* #endregion */
/* #region Configuration */
// MARK: Configure
@CompileStatic
void configure() {
  if(hasParent() == false && isBlu() == false) {
    logDebug('Starting configuration for a non-child device...')
    String ipAddress = getIpAddress()
    if (ipAddress != null && ipAddress != '' && ipAddress ==~ /^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$/) {
      logDebug('Device has an IP address set in preferences, updating DNI if needed...')
      String deviceDataIpAddress = getDeviceDataValue('ipAddress')
      setThisDeviceNetworkId(getMACFromIPAddress(ipAddress))
      if(deviceDataIpAddress == null || deviceDataIpAddress == '' || deviceDataIpAddress != ipAddress) {
        logDebug('Detected newly added/changed IP address, getting preferences from device...')
        setIpAddress(ipAddress)
        getPreferencesFromShellyDevice()
        refresh()
      } else {
        logDebug('Device IP address not changed, sending preferences to device...')
        sendPreferencesToShellyDevice()
      }
    } else {
      logDebug('Could not set device network ID because device settings does not have a valid IP address set.')
    }

    if(isGen1Device() == true) {
      try {setDeviceActionsGen1()}
      catch(e) {logDebug("No device actions configured. Encountered error :${e}")}
    } else if(wsShouldBeConnected() == false) {
      try {setDeviceActionsGen2()}
      catch(e) {logDebug("No device actions configured. Encountered error :${e}")}
    } else {
      connectWebsocketAfterDelay(3)
    }
  } else if(isBlu() == false) {
    logDebug('Starting configuration for child device...')
    sendPreferencesToShellyDevice()
  } else if(isBlu() == true) {
    String mac = getStringDeviceSetting('macAddress')
    mac =  mac.replace(':','').toUpperCase()
    setThisDeviceNetworkId(mac)
    setDeviceDataValue('macAddress', mac)
  }

  if(hasParent() == false && isGen1Device() == false && hasIpAddress() == true) {
    Boolean hasBlu = deviceIsBluGateway()
    logDebug("HasBlue: ${hasBlu}")
    if(hasBlu == true) { setDeviceDataValue('hasBluGateway', 'true') }
    logDebug("Enabled: ${getBooleanDeviceSetting('enableBluetoothGateway')}")
    // Enable or disable BLE gateway functionality
    if(hasBlu == true && (getBooleanDeviceSetting('enableBluetoothGateway') == true || getDeviceSettings().enableBluetoothGateway == null)) {
      if(getDeviceSettings().enableBluetoothGateway == null) {setDeviceSetting('enableBluetoothGateway', true)}
      logDebug('Bluetooth Gateway functionality enabled, configuring device for bluetooth reporting to Hubitat...')
      enableBluReportingToHE()
      connectWebsocketAfterDelay(3)
    }
    else if(hasBlu == true && getBooleanDeviceSetting('enableBluetoothGateway') == false) {
      logDebug('Bluetooth Gateway functionality disabled, configuring device to no longer have bluetooth reporting to Hubitat...')
      disableBluReportingToHE()
    } else { logDebug('Device does not support Bluetooth gateway')}
  }



  if(hasButtons() == true) {sendDeviceEvent([name: 'numberOfButtons', value: getNumberOfButtons()])}

  if(getDeviceSettings().presenceTimeout != null && (getDeviceSettings().presenceTimeout as Integer) < 300) {
    setDeviceSetting('presenceTimeout', [type: 'number', value: 300])
  }
  tryDeviceSpecificConfigure()
  if(thisDeviceOrChildrenHasPowerMonitoring() == true) { configureNightlyPowerMonitoringReset() }

  if(hasPollingChildren() == true) {
    if(getDeviceSettings().gen1_status_polling_rate == 0 ) {
      unscheduleTask('refresh')
    } else {
      runEveryCustomSeconds(getDeviceSettings().gen1_status_polling_rate as Integer, 'refresh')
    }
  }
  refresh()
}

void tryDeviceSpecificConfigure() {try{deviceSpecificConfigure()} catch(ex) {}}


@CompileStatic
void sendPreferencesToShellyDevice() {
  // Switch settings
  Integer coverId = getIntegerDeviceDataValue('coverId')
  Integer inputButtonId = getIntegerDeviceDataValue('inputButtonId')
  Integer inputCountId = getIntegerDeviceDataValue('inputCountId')
  Integer inputSwitchId = getIntegerDeviceDataValue('inputSwitchId')
  Integer switchId = getIntegerDeviceDataValue('switchId')

  LinkedHashMap newSettings = getDeviceSettings()
  logDebug("New settings: ${prettyJson(newSettings)}")

  if(isGen1Device() == false) {
    if(switchId != null) {
      Map curSettings = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand(switchId))?.result
      if(curSettings != null) { curSettings.remove('id') }
      Map<String, Object> toSend = [:]
      curSettings.each{ String k,v ->
        String cKey = "switch_${k}"
        def newVal = newSettings.containsKey(k) ? newSettings[k] : newSettings[cKey]
        logTrace("Current setting for ${k}: ${v} -> New: ${newVal}")
        toSend[k] = newVal
      }
      toSend = toSend.findAll{ it.value!=null }
      logDebug("To send: ${prettyJson(toSend)}")
      logDebug("Sending new settings to device for switch...")
      switchSetConfigJson(toSend, switchId)
    }
    if(coverId != null) {
      Map curSettings = (LinkedHashMap<String, Object>)parentPostCommandSync(coverGetConfigCommand(coverId))?.result
      if(curSettings != null) { curSettings.remove('id') }
      Map<String, Object> toSend = [:]
      curSettings.each{ String k,v ->
        String cKey = "cover_${k}"
        def newVal = newSettings.containsKey(k) ? newSettings[k] : newSettings[cKey]
        logTrace("Current setting for ${k}: ${v} -> New: ${newVal}")
        toSend[k] = newVal
      }
      toSend = toSend.findAll{ it.value!=null }
      logDebug("To send: ${prettyJson(toSend)}")
      logDebug("Sending new settings to device for cover...")
      coverSetConfigJson(toSend, coverId)
    }
    if(inputButtonId != null || inputCountId != null || inputSwitchId != null) {
      Integer id = 0
      if(inputButtonId != null) {id = inputButtonId}
      else if(inputCountId != null) {id = inputCountId}
      else if(inputSwitchId != null) {id = inputSwitchId}
      Map curSettings = (LinkedHashMap<String, Object>)parentPostCommandSync(inputGetConfigCommand(id))?.result
      if(curSettings != null) { curSettings.remove('id') }
      Map<String, Object> toSend = [:]
      curSettings.each{ String k,v ->
        String cKey = "input_${k}"
        def newVal = newSettings.containsKey(k) ? newSettings[k] : newSettings[cKey]
        logTrace("Current setting for ${k}: ${v} -> New: ${newVal}")
        toSend[k] = newVal
      }
      toSend = toSend.findAll{ it.value!=null }
      logDebug("To send: ${prettyJson(toSend)}")
      logDebug("Sending new settings to device for input...")
      inputSetConfigJson(toSend, id)
    }
  }

  if(getDeviceSettings().gen1_set_volume != null) {
    String queryString = "set_volume=${getDeviceSettings().gen1_set_volume}".toString()
    sendGen1Command('settings', queryString)
  }

  if(
    getDeviceSettings().gen1_motion_sensitivity != null &&
    getDeviceSettings().gen1_motion_blind_time_minutes != null &&
    getDeviceSettings().gen1_tamper_sensitivity != null
  ) {
    String queryString = "motion_sensitivity=${getDeviceSettings().gen1_motion_sensitivity}".toString()
    queryString += "&motion_blind_time_minutes${getDeviceSettings().gen1_motion_blind_time_minutes}".toString()
    queryString += "&tamper_sensitivity${getDeviceSettings().gen1_tamper_sensitivity}".toString()
    sendGen1Command('settings', queryString)
  }

  if(getDeviceSettings().trv_temperature_offset != null) {
    String queryString = "temperature_offset=${getDeviceSettings().trv_temperature_offset}".toString()
    sendGen1Command('settings', queryString)
  }
  if(getDeviceSettings().trv_target_t_auto != null) {
    String queryString = "target_t_enabled=${getDeviceSettings().trv_target_t_auto == true ? 1 : 0}".toString()
    sendGen1Command('settings/thermostat/0', queryString)
  }
  if(getDeviceSettings().trv_ext_t_enabled != null) {
    String queryString = "ext_t_enabled=${getDeviceSettings().trv_ext_t_enabled == true ? 1 : 0}".toString()
    sendGen1Command('settings/thermostat/0', queryString)
  }
  if(getDeviceSettings().trv_temp_units != null) {
    String u = "${getDeviceSettings().trv_temp_units}".toString()[0]
    String queryString = "temperature_unit=${u}".toString()
    sendGen1Command('settings', queryString)
  }

  runInSeconds('parentGetPreferencesFromShellyDevice', 6)
}
/* #endregion */
/* #region Power Monitoring Getters and Setters */
@CompileStatic
String powerAvg(Integer id = 0) {"${getThisDeviceDNI()}-${id}power".toString()}
@CompileStatic
ArrayList<BigDecimal> powerAvgs(Integer id = 0) {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(powerAvg(id))) { movingAvgs[powerAvg(id)] = new ArrayList<BigDecimal>() }
  return movingAvgs[powerAvg(id)]
}
@CompileStatic clearPowerAvgs(Integer id = 0) {
  movingAvgs[powerAvg(id)] = new ArrayList<BigDecimal>()
}

@CompileStatic
String amperageAvg(Integer id = 0) {"${getThisDeviceDNI()}-${id}amperage".toString()}
@CompileStatic
ArrayList<BigDecimal> amperageAvgs(Integer id = 0) {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(amperageAvg(id))) { movingAvgs[amperageAvg(id)] = new ArrayList<BigDecimal>() }
  return movingAvgs[amperageAvg(id)]
}
@CompileStatic clearAmperageAvgs(Integer id = 0) {
  movingAvgs[amperageAvg(id)] = new ArrayList<BigDecimal>()
}

@CompileStatic
String voltageAvg(Integer id = 0) {"${getThisDeviceDNI()}-${id}voltage".toString()}
@CompileStatic
ArrayList<BigDecimal> voltageAvgs(Integer id = 0) {
  if(movingAvgs == null) { movingAvgs = new java.util.concurrent.ConcurrentHashMap<String, ArrayList<BigDecimal>>() }
  if(!movingAvgs.containsKey(voltageAvg(id))) { movingAvgs[voltageAvg(id)] = new ArrayList<BigDecimal>() }
  return movingAvgs[voltageAvg(id)]
}
@CompileStatic clearVoltageAvgs(Integer id = 0) {
  movingAvgs[voltageAvg(id)] = new ArrayList<BigDecimal>()
}

@CompileStatic
void setCurrent(BigDecimal value, Integer id = 0) {
  if(getIntegerDeviceDataValue('currentId') == id || id == null) {
    sendDeviceEvent([name: 'amperage', value: value, unit:'A'])
  } else {
    ChildDeviceWrapper c = getCurrentChildById(id)
    if(c != null) { sendChildDeviceEvent([name: 'amperage', value: value, unit:'A'], c) }
  }
}
@CompileStatic
BigDecimal getCurrent(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('amperage', true) as BigDecimal }
  else { return thisDevice().currentValue('amperage', true) as BigDecimal }
}

@CompileStatic
void setApparentPowerAttribute(BigDecimal value, Integer id = 0) {
  if(getIntegerDeviceDataValue('apparentPowerId') == id || id == null) {sendDeviceEvent([name: 'apparentPower', value: value, unit:'VA'])}
  else {
    ChildDeviceWrapper c = getApparentPowerChildById(id)
    if(c != null) { sendChildDeviceEvent([name: 'apparentPower', value: value, unit:'VA'], c) }
  }
}

@CompileStatic
void setPowerAttribute(BigDecimal value, Integer id = 0) {
  if(getIntegerDeviceDataValue('currentId') == id || id == null) {sendDeviceEvent([name: 'power', value: value, unit:'W'])}
  else {
    ChildDeviceWrapper c = getCurrentChildById(id)
    if(c != null) { sendChildDeviceEvent([name: 'power', value: value, unit:'W'], c) }
  }
}
@CompileStatic
BigDecimal getPower(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('power', true) as BigDecimal }
  else { return thisDevice().currentValue('power', true) as BigDecimal }
}

@CompileStatic
void setFrequency(BigDecimal value, Integer id = 0) {
  if(getIntegerDeviceDataValue('frequencyId') == id) {sendDeviceEvent([name: 'frequency', value: value, unit:'Hz'])}
  else {
    ChildDeviceWrapper c = getFrequencyChildById(id)
    if(c != null) { sendChildDeviceEvent([name: 'frequency', value: value, unit:'Hz'], c) }
  }
}

@CompileStatic
void setVoltage(BigDecimal value, Integer id = 0) {
  if(getIntegerDeviceDataValue('voltageId') == id) {sendDeviceEvent([name: 'voltage', value: value, unit:'V'])}
  else {
    ChildDeviceWrapper c = getVoltageChildById(id)
    if(c != null) { sendChildDeviceEvent([name: 'voltage', value: value, unit:'V'], c) }
  }
}
@CompileStatic
BigDecimal getVoltage(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('voltage', true) as BigDecimal }
  else { return thisDevice().currentValue('voltage', true) as BigDecimal }
}

@CompileStatic
void setEnergyAttribute(BigDecimal value, Integer id = 0) {
  value = value.setScale(2, BigDecimal.ROUND_HALF_UP)

  ChildDeviceWrapper c = getEnergyChildById(id)
  if(value == -1) {
    if(getIntegerDeviceDataValue('energyId') == id || id == null) {
      logTrace("Setting energy to 'null'")
      sendDeviceEvent([name: 'energy', value: null, unit:'kWh'])
    } else if(c != null) {
      logTrace("Setting energy to 'null' on ${c}")
      sendChildDeviceEvent([name: 'energy', value: null, unit:'kWh'], c)
    }
  }
  else if(value != null) {
    if(getIntegerDeviceDataValue('energyId') == id || id == null) {
      logTrace("Setting energy to ${value}")
      sendDeviceEvent([name: 'energy', value: value, unit:'kWh'])
    } else if(c != null) {
      logTrace("Setting energy to ${value} on ${c}")
      sendChildDeviceEvent([name: 'energy', value: value, unit:'kWh'], c)
      updateParentEnergyTotal()
    }
  }
}

@CompileStatic
void updateParentEnergyTotal() {
  if(hasChildren() == true) {
    List<ChildDeviceWrapper> energyChildren = getThisDeviceChildren().findAll{it.currentValue('energy') != null}
    List<BigDecimal> childEnergies = []
    if(energyChildren != null && energyChildren?.size() > 0) {
      childEnergies = energyChildren.collect{it.currentValue('energy') as BigDecimal}
    }
    sendDeviceEvent([name: 'energy', value: childEnergies.sum() as BigDecimal, unit:'kWh'])
  }
}

@CompileStatic
void setReturnedEnergyAttribute(BigDecimal value, Integer id = 0) {
  value = value.setScale(2, BigDecimal.ROUND_HALF_UP)

  ChildDeviceWrapper c = getReturnedEnergyChildById(id)
  if(value == -1) {
    if(getIntegerDeviceDataValue('energyId') == id || id == null) {
      logTrace("Setting returnedEnergy to 'null'")
      sendDeviceEvent([name: 'returnedEnergy', value: null, unit:'kWh'])
    } else if(c != null) {
      logTrace("Setting returnedEnergy to 'null' on ${c}")
      sendChildDeviceEvent([name: 'returnedEnergy', value: null, unit:'kWh'], c)
    }
  }
  else if(value != null) {
    if(getIntegerDeviceDataValue('energyId') == id || id == null) {
      logTrace("Setting returnedEnergy to ${value}")
      sendDeviceEvent([name: 'returnedEnergy', value: value, unit:'kWh'])
    } else if(c != null) {
      logTrace("Setting returnedEnergy to ${value} on ${c}")
      sendChildDeviceEvent([name: 'returnedEnergy', value: value, unit:'kWh'], c)
      updateParentReturnedEnergyTotal()
    }
  }
}

@CompileStatic
void updateParentReturnedEnergyTotal() {
  if(hasChildren() == true) {
    List<ChildDeviceWrapper> returnedEnergyChildren = getThisDeviceChildren().findAll{it.currentValue('returnedEnergy') != null}
    List<BigDecimal> childReturnedEnergies = []
    if(returnedEnergyChildren != null && returnedEnergyChildren?.size() > 0) {
      childReturnedEnergies = returnedEnergyChildren.collect{it.currentValue('returnedEnergy') as BigDecimal}
    }
    sendDeviceEvent([name: 'returnedEnergy', value: childReturnedEnergies.sum() as BigDecimal, unit:'kWh'])
  }
}

@CompileStatic
BigDecimal getEnergy(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('energy', true) as BigDecimal }
  else { return thisDevice().currentValue('energy', true) as BigDecimal }
}

@CompileStatic
void resetEnergyMonitors(Integer id = 0) {
  if(hasParent() == true) {
    id = getIntegerDeviceDataValue('energyId')
    if(id != null) {
      switchResetCounters(id, "resetEnergyMonitor-switch${id}")
      lightResetCounters(id, "resetEnergyMonitor-light${id}")
      coverResetCounters(id, "resetEnergyMonitor-cover${id}")
      em1DataResetCounters(id, "resetEnergyMonitor-em1data${id}")
    }
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    allChildren.each{child ->
      logWarn("Child ${child}")
      id = getChildDeviceIntegerDataValue(child, 'energyId')
      if(id != null) {
        switchResetCounters(id, "resetEnergyMonitor-switch${id}")
        lightResetCounters(id, "resetEnergyMonitor-light${id}")
        coverResetCounters(id, "resetEnergyMonitor-cover${id}")
        em1DataResetCounters(id, "resetEnergyMonitor-em1data${id}")
      }
    }
  }
}

/* #endregion */
/* #region Device Getters and Setters */
@CompileStatic
void setBatteryPercent(Integer percent) {
  sendDeviceEvent([name: 'battery', value: percent, unit: '%'])
}

@CompileStatic
void checkPresence() {
  List<Event> ev = thisDevice().events([max: 1])
  if(ev?.size() > 0) {
    Event lastEvent = ev[0]
    logTrace("Last event: ${lastEvent.getUnixTime()}")
    if(lastEvent != null) {
      if(((unixTimeMillis() - lastEvent.getUnixTime()) / 1000) > (getDeviceSettings()?.presenceTimeout as Integer)) {
        sendDeviceEvent([name: 'presence', value: 'not present', isStateChange: false])
      } else {
        sendDeviceEvent([name: 'presence', value: 'present', isStateChange: false])
      }
    }
  }
}

@CompileStatic
void setLevelAttribute(Integer level, ChildDeviceWrapper child = null) {
  if(child != null) {sendChildDeviceEvent([name: 'level', value: level, unit: '%'], child)}
  else {sendDeviceEvent([name: 'level', value: level, unit: '%'])}
}

@CompileStatic
void setSwitchAttribute(String switchState, ChildDeviceWrapper child = null) {
  if(child != null) {sendChildDeviceEvent([name: 'switch', value: switchState], child)}
  else {sendDeviceEvent([name: 'switch', value: switchState])}
}

@CompileStatic
void setWhiteLevelAttribute(Integer whiteLevel, ChildDeviceWrapper child = null) {
  if(child != null) {sendChildDeviceEvent([name: 'whiteLevel', value: whiteLevel, unit: '%'], child)}
  else {sendDeviceEvent([name: 'whiteLevel', value: whiteLevel, unit: '%'])}
}

@CompileStatic
void setColorTemperatureAttribute(Integer colorTemperature, ChildDeviceWrapper child = null) {
  if(child != null) {sendChildDeviceEvent([name: 'colorTemperature', value: colorTemperature, unit: '°K'], child)}
  else {sendDeviceEvent([name: 'colorTemperature', value: colorTemperature, unit: '°K'])}
}

void setColorModeAttribute(String colorMode) {
  if(colorMode in ['CT', 'RGB', 'EFFECTS']) {
    sendDeviceEvent([name: 'colorMode', value: colorMode])
  }
}

void setRGBAttribute(Integer red, Integer green, Integer blue, ChildDeviceWrapper child = null) {
  if(child != null) {sendChildDeviceEvent([name: 'RGB', value: [red, green, blue].toString()], child)}
  else {sendDeviceEvent([name: 'RGB', value: [red, green, blue].toString()])}
}

void setColorAttribute(Integer red, Integer green, Integer blue, ChildDeviceWrapper child = null) {
  String rgb = hubitat.helper.ColorUtils.rgbToHEX([red,green,blue])
  if(child != null) {sendChildDeviceEvent([name: 'color', value: rgb], child)}
  else {sendDeviceEvent([name: 'color', value: rgb])}
}

void setColorNameAttribute(Integer red, Integer green, Integer blue, ChildDeviceWrapper child = null) {
  List hsv = hubitat.helper.ColorUtils.rgbToHSV([red,green,blue])
  String colorName = convertHueToGenericColorName(hsv[0], hsv[1])
  if(child != null) {sendChildDeviceEvent([name: 'colorName', value: colorName], child)}
  else {sendDeviceEvent([name: 'colorName', value: colorName])}
}

void setHueSaturationAttributes(Integer red, Integer green, Integer blue, ChildDeviceWrapper child = null) {
  List hsv = hubitat.helper.ColorUtils.rgbToHSV([red,green,blue])
  Integer hue = hsv[0] as Integer
  Integer saturation = hsv[1] as Integer
  if(child != null) {
    sendChildDeviceEvent([name: 'hue', value: hue], child)
    sendChildDeviceEvent([name: 'saturation', value: saturation, unit: '%'], child)
  } else {
    sendDeviceEvent([name: 'hue', value: hue])
    sendDeviceEvent([name: 'saturation', value: saturation, unit: '%'])
  }
}

@CompileStatic
void setHumidityPercent(BigDecimal percent, Integer id = 0) {
  if(hasHumidityChildren() == true) {
    sendChildDeviceEvent([name: 'humidity', value: percent.setScale(1, BigDecimal.ROUND_HALF_UP), unit: '%'], getHumidityChildById(id))
  } else {
    sendDeviceEvent([name: 'humidity', value: percent.setScale(1, BigDecimal.ROUND_HALF_UP), unit: '%'])
  }
}

@CompileStatic
void setTemperatureC(BigDecimal tempC, Integer id = 0) {
  BigDecimal v = isCelciusScale() ? tempC.setScale(1, BigDecimal.ROUND_HALF_UP) : cToF(tempC).setScale(1, BigDecimal.ROUND_HALF_UP)
  if(hasTemperatureChildren() == true) {
    sendChildDeviceEvent([name: 'temperature', value: v, unit: '°C'], getTemperatureChildById(id))
  } else {
    sendDeviceEvent([name: 'temperature', value: v, unit: '°C'])
  }
}

@CompileStatic
void setTemperatureF(BigDecimal tempF, Integer id = 0) {
  BigDecimal v = isCelciusScale() ? fToC(tempF).setScale(1, BigDecimal.ROUND_HALF_UP) : tempF.setScale(1, BigDecimal.ROUND_HALF_UP)
  if(hasTemperatureChildren()) {
    sendChildDeviceEvent([name: 'temperature', value: v, unit: '°F'], getTemperatureChildById(id))
  } else {
    sendDeviceEvent([name: 'temperature', value: v, unit: '°F'])
  }
}

@CompileStatic
void setPushedButton(Integer buttonPushed, Integer id = 0) {
  if(id == null) { id = 0 }
  if(hasInputButtonChildren()) {
    sendChildDeviceEvent([name: 'pushed', value: buttonPushed, isStateChange: true], getInputButtonChildById(id))
  } else {
    sendDeviceEvent([name: 'pushed', value: buttonPushed, isStateChange: true])
  }
}

@CompileStatic
void setHeldButton(Integer buttonHeld, Integer id = 0) {
  if(hasInputButtonChildren()) {
    sendChildDeviceEvent([name: 'held', value: buttonHeld, isStateChange: true], getInputButtonChildById(id))
  } else {
    sendDeviceEvent([name: 'held', value: buttonHeld, isStateChange: true])
  }

}

@CompileStatic
void setMotionOn(Boolean motion) {
  if(motion == true) {
    sendDeviceEvent([name: 'motion', value: 'active'])
  } else {
    sendDeviceEvent([name: 'motion', value: 'inactive'])
  }
}

@CompileStatic
void setTamperOn(Boolean tamper) {
  if(tamper == true) {
    sendDeviceEvent([name: 'tamper', value: 'detected'])
  } else {
    sendDeviceEvent([name: 'tamper', value: 'clear'])
  }
}

@CompileStatic
void setFloodOn(Boolean tamper) {
  if(tamper == true) {
    sendDeviceEvent([name: 'water', value: 'wet'])
  } else {
    sendDeviceEvent([name: 'water', value: 'dry'])
  }
}

@CompileStatic
void setIlluminance(Integer illuminance) {
  sendDeviceEvent([name: 'illuminance', value: illuminance, unit:'lx'])
}

@CompileStatic
void setGasDetectedOn(Boolean tamper) {
  if(tamper == true) {
    sendDeviceEvent([name: 'naturalGas', value: 'detected'])
  } else {
    sendDeviceEvent([name: 'naturalGas', value: 'clear'])
  }
}

@CompileStatic
void setGasPPM(Integer ppm) {
  sendDeviceEvent([name: 'ppm', value: ppm])
}

/* #endregion */
/* #region Generic Getters and Setters */

DeviceWrapper thisDevice() { return this.device }
ArrayList<ChildDeviceWrapper> getThisDeviceChildren() { return getChildDevices() }
ArrayList<ChildDeviceWrapper> getParentDeviceChildren() { return parent?.getChildDevices() }

LinkedHashMap getDeviceSettings() { return this.settings }
LinkedHashMap getParentDeviceSettings() { return this.parent?.settings }
LinkedHashMap getChildDeviceSettings(ChildDeviceWrapper child) { return child?.settings }
Boolean hasParent() { return parent != null }


@CompileStatic
Boolean thisDeviceHasSetting(String settingName) {
  Boolean hasSetting = getDeviceSettings().containsKey("${settingName}".toString())
  return hasSetting
}

@CompileStatic
Boolean getBooleanDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as Boolean : null
}

@CompileStatic
String getStringDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as String : null
}

@CompileStatic
BigDecimal getBigDecimalDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as BigDecimal : null
}

@CompileStatic
BigDecimal getBigDecimalDeviceSettingAsCelcius(String settingName) {
  if(thisDeviceHasSetting(settingName)) {
    BigDecimal val = getDeviceSettings()[settingName]
    return isCelciusScale() == true ? val : fToC(val)
  } else { return null }
}

@CompileStatic
Integer getIntegerDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as Integer : null
}

@CompileStatic
String getEnumDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? "${getDeviceSettings()[settingName]}".toString() : null
}


@CompileStatic
Boolean hasChildren() {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return (allChildren != null && allChildren.size() > 0)
}

@CompileStatic
String getThisDeviceDNI() { return thisDevice().getDeviceNetworkId() }

@CompileStatic
void setThisDeviceNetworkId(String newDni) { thisDevice().setDeviceNetworkId(newDni) }

String getMACFromIPAddress(String ipAddress) { return getMACFromIP(ipAddress) }

String getIpAddressFromHexAddress(String hexString) {
  Integer[] i = hubitat.helper.HexUtils.hexStringToIntArray(hexString)
  String ip = i.join('.')
  return ip
}

@CompileStatic
void sendDeviceEvent(String name, Object value, String unit = null, String descriptionText = null, Boolean isStateChange = false) {
  thisDevice().sendEvent([name: name, value: value, unit: unit, descriptionText: descriptionText, isStateChange: isStateChange])
}

@CompileStatic
void sendDeviceEvent(Map properties) {thisDevice().sendEvent(properties)}

@CompileStatic
void sendChildDeviceEvent(Map properties, ChildDeviceWrapper child) {if(child != null){child.sendEvent(properties)}}

@CompileStatic
Boolean hasExtTempGen1(String settingName) {return getDeviceSettings().containsKey(settingName) == true}

@CompileStatic
void setDeviceSetting(String name, Map options, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, options)
}

void setDeviceSetting(String name, Long value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Boolean value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, String value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Double value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Date value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, List value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}



void removeDeviceSetting(String name) {thisDevice().removeSetting(name)}

@CompileStatic
void setDeviceNetworkIdByMacAddress(String ipAddress) {
  String oldDni = getThisDeviceDNI()
  String newDni = getMACFromIPAddress(ipAddress)
  if(oldDni != newDni) {
    logDebug("Current DNI does not match device MAC address... Setting device network ID to ${newDni}")
    setThisDeviceNetworkId(newDni)
  } else {
    logTrace('Device DNI does not need updated, moving on...')
  }
}

void scheduleTask(String sched, String taskName) {schedule(sched, taskName)}
void unscheduleTask(String taskName) { unschedule(taskName) }

Boolean isCelciusScale() { getLocation().temperatureScale == 'C' }

String getDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName)
}

Integer getIntegerDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName) as Integer
}

Boolean getBooleanDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName) == 'true'
}

@CompileStatic
Boolean deviceIsSwitch(DeviceWrapper dev) {return deviceHasDataValue('switchId', dev)}

@CompileStatic
Boolean deviceIsDimmer(DeviceWrapper dev) {return deviceHasDataValue('switchLevelId', dev)}

@CompileStatic
Boolean deviceIsRGB(DeviceWrapper dev) {return deviceHasDataValue('rgbId', dev)}

@CompileStatic
Boolean deviceIsRGBW(DeviceWrapper dev) {return deviceHasDataValue('rgbwId', dev)}

@CompileStatic
Boolean deviceIsCover(DeviceWrapper dev) {return deviceHasDataValue('coverId', dev)}

@CompileStatic
Boolean deviceIsInputSwitch(DeviceWrapper dev) {return deviceHasDataValue('inputSwitchId', dev) || deviceIsComponentInputSwitch() == true}

@CompileStatic
Boolean deviceIsInputCount(DeviceWrapper dev) {return deviceHasDataValue('inputCountId', dev)}

@CompileStatic
Boolean deviceIsInputButton(DeviceWrapper dev) {return deviceHasDataValue('inputButtonId', dev)}

@CompileStatic
Boolean deviceIsInputAnalog(DeviceWrapper dev) {return deviceHasDataValue('inputAnalogId', dev)}

@CompileStatic
Boolean deviceIsInput(DeviceWrapper dev) {return deviceIsInputSwitch(dev) || deviceIsInputCount(dev) || deviceIsInputButton(dev) || deviceIsInputAnalog(dev)}

Boolean childHasAttribute(ChildDeviceWrapper child, String attributeName) {
  return child.hasAttribute(attributeName)
}

String getParentDeviceDataValue(String dataValueName) {
  return parent?.getDeviceDataValue(dataValueName)
}

Integer getChildDeviceIntegerDataValue(ChildDeviceWrapper child, String dataValueName) {
  return child.getDeviceDataValue(dataValueName) as Integer
}

String getChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName) {
  return child.getDeviceDataValue(dataValueName)
}

@CompileStatic
Boolean childHasDataValue(ChildDeviceWrapper child, String dataValueName) {
  return getChildDeviceDataValue(child, dataValueName) != null
}

void setChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName, String valueToSet) {
  child.updateDataValue(dataValueName, valueToSet)
}

@CompileStatic
Boolean deviceHasDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  getDeviceDataValue(dataValueName, dev) != null
}

@CompileStatic
Boolean anyChildHasDataValue(String dataValueName) {
  if(hasParent() == true) {return false}
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.any{childHasDataValue(it, dataValueName)}
}

void setDeviceDataValue(String dataValueName, String valueToSet) {
  thisDevice().updateDataValue(dataValueName, valueToSet)
}

String getChildDeviceNetworkId(ChildDeviceWrapper child) {
  return child.getDeviceNetworkId()
}

@CompileStatic
Boolean thisDeviceHasPowerMonitoring() {return getDeviceDataValue('hasPM') == 'true'}

@CompileStatic
Boolean thisDeviceOrChildrenHasPowerMonitoring() {
  if(hasChildren() == false) {return getDeviceDataValue('hasPM') == 'true'}
  else {
    return (anyChildHasDataValue('hasPM') || getDeviceDataValue('hasPM') == 'true' || hasCapabilityEnergyMeter() == true)
  }
}

@CompileStatic
Boolean hasChildSwitches() { anyChildHasDataValue('switchId') }

@CompileStatic
Boolean hasPollingChildren() { anyChildHasDataValue('polling') }

@CompileStatic
Boolean hasBluGateway() {
  return getDeviceDataValue('hasBluGateway') != null
}

@CompileStatic
String getBaseUri() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}"
  } else {
    return "http://${getDeviceSettings().ipAddress}"
  }
}

@CompileStatic
String getBaseUriRpc() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}/rpc"
  } else {
    return "http://${getDeviceSettings().ipAddress}/rpc"
  }
}

String getHubBaseUri() {
  return "http://${location.hub.localIP}:39501"
}

@CompileStatic
Long unixTimeSeconds() {
  return Instant.now().getEpochSecond()
}

@CompileStatic
String getWebSocketUri() {
  if(getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '') {return "ws://${getDeviceSettings()?.ipAddress}/rpc"}
  else {return null}
}
@CompileStatic
Boolean hasWebsocketUri() {
  return (getWebSocketUri() != null && getWebSocketUri().length() > 6)
}

@CompileStatic
Boolean hasIpAddress() {
  Boolean hasIpAddress = (getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '' && ((String)getDeviceSettings()?.ipAddress).length() > 6)
  return hasIpAddress
}

@CompileStatic
String getIpAddress() {
  if(hasIpAddress()) {return getStringDeviceSetting('ipAddress')} else {return null}
}

@CompileStatic setIpAddress(String ipAddress, Boolean updateSetting = false) {
  setThisDeviceNetworkId(getMACFromIPAddress(ipAddress))
  setDeviceDataValue('ipAddress', ipAddress)
  if(updateSetting == true) {
    logDebug("Incoming webhook IP address doesn't match what is currently set in driver preferences, updating preference value...")
    setDeviceSetting('ipAddress', ipAddress)
  }
}

@CompileStatic
void setDeviceInfo(Map info) {
  String model = ''
  String gen = ''
  String ver = ''

  if(info?.model != null) { model = info?.model }
  if(info?.type != null) { model = info?.type }
  if(info?.gen != null) {gen = info?.gen.toString()} else {gen = '1'}
  if(info?.ver != null ) {
    ver = info?.ver
  } else {
    ver = ((("${info?.fw}".tokenize('/')[1]).tokenize('@')[0]).tokenize('-')[0]).replace('v','')
  }
  logDebug("Setting device info: model=${model}, gen=${gen}, ver=${ver}")
  if(model != null && model != '') { setDeviceDataValue('model', model)}
  if(gen != null && gen != '') { setDeviceDataValue('gen', gen)}
  if(ver != null && ver != '') { setDeviceDataValue('ver', ver)}
}

@CompileStatic
void setHubitatDevicePreferences(LinkedHashMap<String, Object> preferences, DeviceWrapper dev = null) {
  logDebug("Setting device preferences from ${prettyJson(preferences)}")
  Integer switchId = getIntegerDeviceDataValue('switchId', dev)
  Boolean isSwitch = deviceIsSwitch(dev)
  Boolean isCover = deviceIsCover(dev)
  Boolean isInput = deviceIsInput(dev)
  preferences.each{ String k, Object v ->
    String c = "cover_${k}".toString()
    String i = "input_${k}".toString()
    String s = "switch_${k}".toString()
    if(preferenceMap.containsKey(k) == true) {
      String type = preferenceMap[k].type as String
      if(type == 'enum') {
        setDeviceSetting(k, [type:'enum', value: v], dev)
      } else if(type == 'number') {
        setDeviceSetting(k, [type:'number', value: v as Integer], dev)
      } else {
        setDeviceSetting(k, [type: preferenceMap[k].type, value: v], dev)
      }
    } else if(isSwitch == true && preferenceMap.containsKey(s) == true) {
        if(preferenceMap[s].type == 'enum') {
          setDeviceSetting(s, [type:'enum', value: v], dev)
        } else if(preferenceMap[s].type == 'number') {
          setDeviceSetting(s, [type:'number', value: v as Integer], dev)
        } else {
          setDeviceSetting(s, [type: preferenceMap[s].type, value: v], dev)
        }
    } else if(isCover == true && preferenceMap.containsKey(c) == true) {
        if(preferenceMap[c].type == 'enum') {
          setDeviceSetting(c, [type:'enum', value: v], dev)
        } else if(preferenceMap[c].type == 'number') {
          setDeviceSetting(c, [type:'number', value: v as Integer], dev)
        } else {
          setDeviceSetting(c, [type: preferenceMap[c].type, value: v], dev)
        }
    } else if(isInput == true && preferenceMap.containsKey(i) == true) {
        if(preferenceMap[i].type == 'enum') {
          setDeviceSetting(i, [type:'enum', value: v], dev)
        } else if(preferenceMap[i].type == 'number') {
          setDeviceSetting(i, [type:'number', value: v as Integer], dev)
        } else {
          setDeviceSetting(i, [type: preferenceMap[i].type, value: v], dev)
        }
    } else if(dev == null && k.startsWith('trv_')) {
      if(k == 'trv_temperature_offset') {setDeviceSetting(k, [type:'number', value: v as BigDecimal])}
      if(k in ['trv_target_t_auto', 'trv_ext_t_enabled']) {setDeviceSetting(k, [type:'bool', value: v])}
      if(k in ['trv_display_brightness', 'trv_temp_units']) {setDeviceSetting(k, [type:'enum', value: "${v}".toString()])}
    } else if(k == 'id' || k == 'name') {
      logTrace("Skipping settings as configuration of ${k} from Hubitat, please configure from Shelly device web UI if needed.")
    } else if(isInput == true && (k in ['type', 'factory_reset'])) {
        logTrace("Skipping settings as configuration of ${k} from Hubitat, please configure from Shelly device web UI if needed.")
    } else if(isCover == true && k in ['type','factory_reset','in_mode','invert_directions','motor','obstruction_detection','safety_switch','swap_inputs']) {
        logTrace("Skipping settings as configuration of ${k} from Hubitat, please configure from Shelly device web UI if needed.")
    } else {
      logWarn("Preference retrieved from child device (${k}) does not have config available in device driver. ")
    }
  }
}

@CompileStatic
void setChildDevicePreferences(LinkedHashMap<String, Object> preferences, ChildDeviceWrapper child) {
  setHubitatDevicePreferences(preferences, child as DeviceWrapper)
}

@CompileStatic
void setCoverState(String value, Integer id = 0) {
  logTrace("Setting cover state to ${value}")
  if(value in ['opening', 'partially open', 'closed', 'open', 'closing', 'unknown']) {
    List<ChildDeviceWrapper> children = getCoverChildren()
    if(children != null && children.size() > 0) {
      sendChildDeviceEvent([name: 'windowShade', value: value], getCoverChildById(id))
    } else {
      sendDeviceEvent([name: 'windowShade', value: value])
    }
  }
}

void setChildCoverPosition(Integer position, ChildDeviceWrapper child) {child?.setCoverPosition(boundedLevel(position))}

@CompileStatic
void setCoverPosition(Integer position, Integer id = 0) {
  logTrace("Setting cover position to ${position}")
  List<ChildDeviceWrapper> children = getCoverChildren()
  if(children != null && children.size() > 0) {
    setChildCoverPosition(position, getCoverChildById(id))
  } else {
    Boolean invert = getBooleanDeviceSetting('cover_invert_status')
    sendDeviceEvent([name: 'position', value: position])
  }
  if(position == 0 && getBooleanDeviceSetting('cover_invert_status') == false) {
    setCoverState('closed', id)
  } else if(position == 0 && getBooleanDeviceSetting('cover_invert_status') == true) {
    setCoverState('open', id)
  } else if(position == 100 && getBooleanDeviceSetting('cover_invert_status') == false) {
    setCoverState('open', id)
  } else if(position == 100 && getBooleanDeviceSetting('cover_invert_status') == true) {
    setCoverState('closed', id)
  }
}

@CompileStatic
void setValveState(String position, Integer id = 0) {
  if(position in ['open','closed']) {
    List<ChildDeviceWrapper> children = getValveChildren()
    if(children != null && children.size() > 0) {
      sendChildDeviceEvent([name: 'valve', value: position], getValveChildById(id))
    } else {
      sendDeviceEvent([name: 'valve', value: position])
    }
  }
}

@CompileStatic
void setValvePositionState(Integer level, Integer id = 0) {
  if(level != null) {sendDeviceEvent([name: 'valvePosition', value: level, unit: '%'])}
}

@CompileStatic
void setSwitchLevelAttribute(Integer level, Integer id = 0) {
  if(level != null) {
    if(getDeviceDataValue('switchLevelId') == null && getDeviceDataValue('switchLevelId') != id){
      List<ChildDeviceWrapper> children = getSwitchLevelChildren()
      if(children != null && children.size() > 0) {
        sendChildDeviceEvent([name: 'level', value: level], getSwitchLevelChildById(id))
      }
    } else {
      sendDeviceEvent([name: 'level', value: level])
    }

  }
}

@CompileStatic
void setSwitchState(Boolean on, Integer id = 0) {
  if(on != null) {
    List<ChildDeviceWrapper> children = getSwitchChildren()
    if(children != null && children.size() > 0) {
      sendChildDeviceEvent([name: 'switch', value: on ? 'on' : 'off'], getSwitchChildById(id))
      //Create map of child states and set entry for this event in map.
      //Avoids race conditions from setting child state then immediately trying to retrieve it before it has a chance to settle.
      Map childStates = children.collectEntries{child -> [child.getDataValue('switchId') as Integer, child.currentValue('switch')] }
      childStates[id] = on ? 'on' : 'off'
      Boolean anyOn = childStates.any{k,v -> v == 'on'}
      Boolean allOn = childStates.every{k,v -> v == 'on'}
      String parentSwitchStateMode = getDeviceSettings().parentSwitchStateMode
      if(parentSwitchStateMode == null || parentSwitchStateMode == '') {initializeSettingsToDefaults()}
      if(parentSwitchStateMode == 'anyOn') { sendDeviceEvent([name: 'switch', value: anyOn ? 'on' : 'off']) }
      if(parentSwitchStateMode == 'allOn') { sendDeviceEvent([name: 'switch', value: allOn ? 'on' : 'off']) }
    } else {
      sendDeviceEvent([name: 'switch', value: on ? 'on' : 'off'])
    }
  }
}

@CompileStatic
void setGen1AdcSwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
    sendChildDeviceEvent([name: 'switch', value: value], getAdcSwitchChildById(id))
  }
}

@CompileStatic
void setGen1TemperatureSwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
    sendChildDeviceEvent([name: 'switch', value: value], getTemperatureSwitchChildById(id))
  }
}

@CompileStatic
void setGen1HumiditySwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
  sendChildDeviceEvent([name: 'switch', value: value], getHumiditySwitchChildById(id)) }
}

@CompileStatic
Boolean getSwitchState() {
  return thisDevice().currentValue('switch', true) == 'on'
}

@CompileStatic
void setSmokeState(String value, Integer id = 0) {
  logTrace("Setting smoke detector state to ${value}")
  if(value in ['clear', 'tested', 'detected']) {
    sendDeviceEvent([name: 'smoke', value: value])
  }
}

@CompileStatic
void startLevelChange(String direction) {
  if(direction == 'up') {
    if(deviceIsDimmer(thisDevice())) {
      parentPostCommandAsync(lightDimUpCommand(getIntegerDeviceDataValue('switchLevelId')))
    } else if(deviceIsRGB(thisDevice())) {
      parentPostCommandAsync(rgbDimUpCommand(getIntegerDeviceDataValue('rgbId')))
    } else if(deviceIsRGBW(thisDevice())) {
      parentPostCommandAsync(rgbwDimUpCommand(getIntegerDeviceDataValue('rgbwId')))
    }
  } else if(direction == 'down') {
    if(deviceIsDimmer(thisDevice())) {
      parentPostCommandAsync(lightDimDownCommand(getIntegerDeviceDataValue('switchLevelId')))
    } else if(deviceIsRGB(thisDevice())) {
      parentPostCommandAsync(rgbDimDownCommand(getIntegerDeviceDataValue('rgbId')))
    } else if(deviceIsRGBW(thisDevice())) {
      parentPostCommandAsync(rgbwDimDownCommand(getIntegerDeviceDataValue('rgbwId')))
    }
  }
}

@CompileStatic
void stopLevelChange() {
  if(deviceIsDimmer(thisDevice())) {
    parentPostCommandAsync(lightDimStopCommand(getIntegerDeviceDataValue('switchLevelId')))
  } else if(deviceIsRGB(thisDevice())) {
    parentPostCommandAsync(rgbDimStopCommand(getIntegerDeviceDataValue('rgbId')))
  } else if(deviceIsRGBW(thisDevice())) {
    parentPostCommandAsync(rgbwDimStopCommand(getIntegerDeviceDataValue('rgbwId')))
  }
}

@CompileStatic
void setColor(Map hls) {
  List rgbColors = hlsMapToRGB(hls)
  Integer r = rgbColors[0] as Integer
  Integer g = rgbColors[1] as Integer
  Integer b = rgbColors[2] as Integer
  if(isGen1Device() == true) {
    parentSendGen1CommandAsync("light/${getDeviceDataValue('rgbwId')}/?turn=on&mode=color&red=${r}&green=${g}&blue=${b}", null, 'gen1LightCallback')
  } else {
    if(deviceIsRGB(thisDevice())) {
      parentPostCommandAsync(rgbSetCommand([id: getIntegerDeviceDataValue('rgbId'), on: true, rgb: rgbColors]))
    } else if(deviceIsRGBW(thisDevice())) {
      parentPostCommandAsync(rgbwSetCommand([id: getIntegerDeviceDataValue('rgbwId'), on: true, rgb: rgbColors]))
    }
  }
}

List hlsMapToRGB(Map hls) {return hubitat.helper.ColorUtils.hsvToRGB([hls.hue,hls.saturation,hls.level])}

// void setColorTemperature(BigDecimal colorTemp) {setColorTemperature(colorTemp, null, 0)}
// void setColorTemperature(BigDecimal colorTemp, BigDecimal level) {setColorTemperature(colorTemp, level, 0)}

@CompileStatic
void setColorTemperature(BigDecimal colorTemp, BigDecimal level = null, BigDecimal transition = 0) {
  if(isGen1Device() == true) {
    Integer id = getDeviceDataValue('ctId') as Integer
    Integer boundedTemp = 0
    if(transition != 0) {transition = transition * 1000}
    Integer boundedTransition = boundedLevel(transition as Integer, 0, 5000)
    boundedTemp = boundedLevel(colorTemp as Integer, getWarmTemp(), getCoolTemp())
    if(level == null) {
      parentSendGen1CommandAsync("light/${id}/?turn=on&mode=white&temp=${boundedTemp}&transition=${boundedTransition}", null, 'gen1LightCallback')
    } else {
      Integer l = boundedLevel(level as Integer)
      parentSendGen1CommandAsync("light/${id}/?turn=on&mode=white&temp=${boundedTemp}&transition=${boundedTransition}&brightness=${l}", null, 'gen1LightCallback')
    }
  }
}

@CompileStatic
void setWhiteLevel(BigDecimal level) {
  Integer l = (boundedLevel(level as Integer) / 100 * 255).toInteger()
  if(isGen1Device() == true) {
    if(deviceIsRGB(thisDevice())) {
      Integer id = getDeviceDataValue('rgbId') as Integer
      parentSendGen1CommandAsync("color/${id}/?isOn=true&white=${l}", null, 'gen1LightCallback')
    } else if(deviceIsRGBW(thisDevice())) {
      Integer id = getDeviceDataValue('rgbwId') as Integer
      parentSendGen1CommandAsync("color/${id}/?isOn=true&white=${l}", null, 'gen1LightCallback')
    }
  } else {
    if(deviceIsRGB(thisDevice())) {
      parentPostCommandAsync(rgbSetCommand([id: getIntegerDeviceDataValue('rgbId'), on: true, white: l]))
    } else if(deviceIsRGBW(thisDevice())) {
      parentPostCommandAsync(rgbwSetCommand([id: getIntegerDeviceDataValue('rgbwId'), on: true, white: l]))
    }
  }
}

void on() {
  if(isGen1Device() == true) {
    String action = hasCapabilityLight() == true ? 'light' : 'relay'
    if(hasChildSwitches() == true) {
      getSwitchChildren().each{ child ->
        Integer id = getChildDeviceDataValue(child, 'switchId') as Integer
        parentSendGen1CommandAsync("${action}/${id}/?turn=on")
      }
    } else {
      parentSendGen1CommandAsync("${action}/${getDeviceDataValue('switchId')}/?turn=on")
    }
  } else {
    if(deviceIsRGB(thisDevice()) == true) {
      parentPostCommandAsync(rgbSetCommand([id: getIntegerDeviceDataValue('rgbId'), on: true]))
    } else if(deviceIsRGBW(thisDevice()) == true) {
      parentPostCommandAsync(rgbwSetCommand([id: getIntegerDeviceDataValue('rgbwId'), on: true]))
    } else if(deviceIsDimmer(thisDevice()) == true) {
      parentPostCommandAsync(lightSetCommand(getIntegerDeviceDataValue('switchLevelId'), true))
    } else if(deviceIsInputSwitch(thisDevice()) == true) {
      logWarn('Cannot change state of an input on a Shelly device from Hubitat!')
      sendDeviceEvent([name: 'switch', value: 'off', isStateChange: false])
    } else if(deviceIsOverUnderSwitch() == true) {
      logWarn('Cannot change state of an OverUnder on a Shelly device from Hubitat!')
      sendDeviceEvent([name: 'switch', value: 'off', isStateChange: false])
    } else if(hasChildSwitches() == true) {
      getSwitchChildren().each{it.on()}
    } else {
      parentPostCommandAsync(switchSetCommand(true, getIntegerDeviceDataValue('switchId')))
    }
  }
}

void off() {
  if(isGen1Device() == true) {
    String action = hasCapabilityLight() == true ? 'light' : 'relay'
    if(hasChildSwitches() == true) {
      getSwitchChildren().each{ child ->
        Integer id = getChildDeviceDataValue(child, 'switchId') as Integer
        parentSendGen1CommandAsync("${action}/${id}/?turn=off")
      }
    } else {
      parentSendGen1CommandAsync("${action}/${getDeviceDataValue('switchId')}/?turn=off")
    }
  } else {
    if(deviceIsRGB(thisDevice()) == true) {
      parentPostCommandAsync(rgbSetCommand([id: getIntegerDeviceDataValue('rgbId'), on: false]))
    } else if(deviceIsRGBW(thisDevice()) == true) {
      parentPostCommandAsync(rgbwSetCommand([id: getIntegerDeviceDataValue('rgbwId'), on: false]))
    } else if(deviceIsDimmer(thisDevice()) == true) {
      parentPostCommandAsync(lightSetCommand(getIntegerDeviceDataValue('switchLevelId'), false))
    } else if(deviceIsInputSwitch(thisDevice()) == true) {
      logWarn('Cannot change state of an input on a Shelly device from Hubitat!')
      sendDeviceEvent([name: 'switch', value: 'on', isStateChange: false])
    } else if(deviceIsOverUnderSwitch() == true) {
      logWarn('Cannot change state of an OverUnder on a Shelly device from Hubitat!')
      sendDeviceEvent([name: 'switch', value: 'on', isStateChange: false])
    } else if(hasChildSwitches()) {
      getSwitchChildren().each{it.off()}
    } else {
      parentPostCommandAsync(switchSetCommand(false, getIntegerDeviceDataValue('switchId')))
    }
  }
}

void setLevel(BigDecimal level) {setLevel(level, null)}

@CompileStatic
void setLevel(BigDecimal level, BigDecimal duration) {
  Integer l = boundedLevel(level as Integer)
  if(isGen1Device() == true) {
    if(duration == null) {duration = 500}
    Integer d = boundedLevel(duration as Integer, 0, 5000)
    parentSendGen1CommandAsync("light/${getDeviceDataValue('switchLevelId')}/?turn=on&brightness=${l}&transition=${d}", null, 'gen1LightCallback')
  } else {
    if(deviceIsRGB(thisDevice()) == true) {
      if(duration == null) {
        parentPostCommandAsync(rgbSetCommand(id: getIntegerDeviceDataValue('rgbId'), brightness: l))
      } else {
        Integer d = boundedLevel(duration as Integer, 0, 900)
        parentPostCommandAsync(rgbSetCommand(id: getIntegerDeviceDataValue('rgbId'), brightness: l, transitionDuration: d))
      }
    } else if(deviceIsRGBW(thisDevice()) == true) {
      if(duration == null) {
        parentPostCommandAsync(rgbwSetCommand(id: getIntegerDeviceDataValue('rgbwId'), brightness: l))
      } else {
        Integer d = boundedLevel(duration as Integer, 0, 900)
        parentPostCommandAsync(rgbwSetCommand(id: getIntegerDeviceDataValue('rgbwId'), brightness: l, transitionDuration: d))
      }
    } else if(deviceIsDimmer(thisDevice()) == true) {
      if(duration == null) {
        parentPostCommandAsync(lightSetCommand(id: getIntegerDeviceDataValue('switchLevelId'), brightness: l))
      } else {
        Integer d = boundedLevel(duration as Integer, 0, 900)
        parentPostCommandAsync(lightSetCommand(id: getIntegerDeviceDataValue('switchLevelId'), brightness: l, transitionDuration: d))
      }
    }
  }
}

@CompileStatic
void push(BigDecimal buttonNumber) {sendDeviceEvent([name: 'pushed', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void hold(BigDecimal buttonNumber) {sendDeviceEvent([name: 'held', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void doubleTap(BigDecimal buttonNumber) {sendDeviceEvent([name: 'doubleTapped', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void tripleTap(BigDecimal buttonNumber) {sendDeviceEvent([name: 'tripleTapped', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void setInputSwitchState(Boolean on, Integer id = 0) {
  logTrace("Setting inputSwitch:${id} to ${on ? 'on' : 'off'}")
  if(on != null) {sendChildDeviceEvent([name: 'switch', value: on ? 'on' : 'off'], getInputSwitchChildById(id))}
}

@CompileStatic
void setInputCountState(Integer count, Integer id = 0) {
  if(count != null) {sendChildDeviceEvent([name: 'count', value: count], getInputCountChildById(id))}
}

@CompileStatic
void setInputAnalogState(BigDecimal value, Integer id = 0) {
  if(value != null) {sendChildDeviceEvent([name: 'analogValue', value: value, unit: '%'], getInputAnalogChildById(id))}
}

@CompileStatic
void setLastUpdated() {
  if(hasCapabilityBattery() == true) {sendDeviceEvent([name: 'lastUpdated', value: nowFormatted()])}
}

//Rollers
@CompileStatic
void open() {
  if(hasCapabilityValve() == true && hasCapabilityThermostatHeatingSetpoint() == true) {
    if(isGen1Device() == true) { parentSendGen1CommandAsync("thermostat/0/?pos=100") }
  } else if(hasCapabilityValve() == true) {
    if(isGen1Device() == true) {
      parentSendGen1CommandAsync('valve/0?go=open')
    } else {
      //TODO with Gen2+ Valves
    }
  } else if(hasCapabilityCoverOrCoverChild() == true) {
    if(isGen1Device() == true) {
      parentSendGen1CommandAsync("/roller/${getDeviceDataValue('coverId')}/?go=open")
    } else {
      parentPostCommandSync(coverOpenCommand(getIntegerDeviceDataValue('coverId')))
    }
  }
}

@CompileStatic
void close() {
  if(hasCapabilityValve() == true && hasCapabilityThermostatHeatingSetpoint() == true) {
    if(isGen1Device() == true) { parentSendGen1CommandAsync("thermostat/0/?pos=0")}
  } else if(hasCapabilityValve() == true) {
    if(isGen1Device() == true) {
      parentSendGen1CommandAsync('valve/0?go=close')
    } else {
      //TODO with Gen2+ Valves
    }
  } else if(hasCapabilityCoverOrCoverChild() == true) {
    if(isGen1Device() == true) {
      parentSendGen1CommandAsync("/roller/${getDeviceDataValue('coverId')}/?go=close")
    } else {
      parentPostCommandSync(coverCloseCommand(getIntegerDeviceDataValue('coverId')))
    }
  }
}

@CompileStatic
void setPosition(BigDecimal position) {
  if(isGen1Device() == true) {
    parentSendGen1CommandAsync("/roller/${getDeviceDataValue('coverId')}/?go=to_pos&roller_pos=${position as Integer}")
  } else {
    parentPostCommandSync(coverGoToPositionCommand(getIntegerDeviceDataValue('coverId'), position as Integer))
  }
}

@CompileStatic
void startPositionChange(String direction) {
  if(direction == 'open') {open()}
  if(direction == 'close') {close()}
}

@CompileStatic
void stopPositionChange() {
  if(isGen1Device() == true) {
    parentSendGen1CommandAsync("/roller/${getDeviceDataValue('coverId')}/?go=stop")
  } else {
    parentPostCommandSync(coverStopCommand(getIntegerDeviceDataValue('coverId')))
  }
}

//TRV
@CompileStatic
void setValvePosition(BigDecimal position) {
  parentSendGen1CommandAsync("thermostat/0/?pos=${position as Integer}")
}

@CompileStatic
void setExternalTemperature(BigDecimal temperature) {
  String units = getEnumDeviceSetting('trv_temp_units')
  if(isCelciusScale() == false && units == 'C') {
    temperature = fToC(temperature)
  } else if(isCelciusScale() == true && units == 'F') {
    temperature = cToF(temperature)
  }
  temperature = temperature.setScale(1, BigDecimal.ROUND_HALF_UP)
  parentSendGen1CommandAsync("ext_t?temp=${temperature}")
}

@CompileStatic
void setHeatingSetpoint(BigDecimal temperature) {
  String units = getEnumDeviceSetting('trv_temp_units')
  if(isCelciusScale() == false && units == 'C') {
    temperature = fToC(temperature)
  } else if(isCelciusScale() == true && units == 'F') {
    temperature = cToF(temperature)
  }
  temperature = temperature.setScale(1, BigDecimal.ROUND_HALF_UP)
  parentSendGen1CommandAsync("thermostat/0?target_t_enabled=1&target_t=${temperature}")
}

void sendEventToShellyBluetoothHelper(String loc, Object value, String dni) {
  sendLocationEvent(name:loc, value:value, data:dni)
}

Boolean isGen1Device() {
  if(hasParent() == false) {return GEN1 == true}
  else return parent.isGen1Device()
}

Boolean isBlu() {BLU != null && BLU == true}
Boolean hasWebsocket() {return WS != null && WS == true}
Boolean hasButtons() {return BUTTONS != null}
Integer getNumberOfButtons() {return hasButtons() == true ? BUTTONS as Integer : 0}

@CompileStatic
Boolean wsShouldBeConnected() {
  if(hasWebsocket() == true && hasIpAddress() == true) {
    Boolean bluGatewayEnabled = getBooleanDeviceSetting('enableBluetoothGateway') == true
    Boolean powerMonitoringEnabled = getBooleanDeviceSetting('enablePowerMonitoring') == true
    return bluGatewayEnabled || powerMonitoringEnabled
  } else { return false }
}

/* #endregion */
/* #region Command Maps */
@CompileStatic
LinkedHashMap shellyGetDeviceInfoCommand(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetDeviceInfo",
    "params":["ident":fullInfo]
  ]
  return command
}

@CompileStatic
LinkedHashMap shellyGetConfigCommand(String src = 'shellyGetConfig') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetConfig",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap shellyGetStatusCommand(String src = 'shellyGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap sysGetStatusCommand(String src = 'sysGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Sys.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap devicePowerGetStatusCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerGetStatus",
    "method" : "DevicePower.GetStatus",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetStatusCommand(Integer id = 0, src = 'switchGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetCommand(Boolean on, Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSet",
    "method" : "Switch.Set",
    "params" : [
      "id" : id,
      "on" : on
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetConfigCommand(Integer id = 0, String src = 'switchGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer switchId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : switchId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetConfigCommand(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit,
  Integer switchId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : switchId,
      "config": [
        "initial_state": initial_state,
        "auto_on": auto_on,
        "auto_on_delay": auto_on_delay,
        "auto_off": auto_off,
        "auto_off_delay": auto_off_delay,
        "power_limit": power_limit,
        "voltage_limit": voltage_limit,
        "autorecover_voltage_errors": autorecover_voltage_errors,
        "current_limit": current_limit
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchResetCountersCommand(Integer id = 0, String src = 'switchResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightResetCountersCommand(Integer id = 0, String src = 'lightResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverResetCountersCommand(Integer id = 0, String src = 'coverResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGetConfigCommand(Integer id = 0, String src = 'coverGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverOpenCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverOpen",
    "method" : "Cover.Open",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverCloseCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverClose",
    "method" : "Cover.Close",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGoToPositionCommand(Integer id = 0, Integer pos) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverGoToPosition",
    "method" : "Cover.GoToPosition",
    "params" : [
      "id" : id,
      "pos" : pos
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGetStatusCommand(Integer id = 0, src = 'coverGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverStopCommand(Integer id = 0, src = 'coverStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.Stop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer coverId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverSetConfig",
    "method" : "Cover.SetConfig",
    "params" : [
      "id" : coverId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap temperatureGetConfigCommand(Integer id = 0, String src = 'temperatureGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Temperature.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap temperatureGetStatusCommand(Integer id = 0, src = 'temperatureGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Temperature.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap humidityGetConfigCommand(Integer id = 0, String src = 'humidityGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Humidity.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap humidityGetStatusCommand(Integer id = 0, src = 'humidityGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Humidity.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer inputId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "inputSetConfig",
    "method" : "Input.SetConfig",
    "params" : [
      "id" : inputId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListSupportedCommand(String src = 'webhookListSupported') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.ListSupported",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListCommand(String src = 'webhookList') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookDeleteCommand(Integer id, String src = 'webhookDelete') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.Delete",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptListCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptList",
    "method" : "Script.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStopCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStop",
    "method" : "Script.Stop",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStartCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStart",
    "method" : "Script.Start",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptEnableCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptEnable",
    "method" : "Script.SetConfig",
    "params" : [
      "id": id,
      "config": ["enable": true]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptDeleteCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptDelete",
    "method" : "Script.Delete",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptCreateCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptCreate",
    "method" : "Script.Create",
    "params" : ["name": "HubitatBLEHelper"]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptPutCodeCommand(Integer id, String code, Boolean append = true) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptPutCode",
    "method" : "Script.PutCode",
    "params" : [
      "id": id,
      "code": code,
      "append": append
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetConfigCommand(Integer pm1Id = 0, String src = 'pm1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetConfig",
    "params" : [
      "id" : pm1Id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1SetConfigCommand(Integer pm1Id = 0, pm1Config = [], src = 'pm1SetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.SetConfig",
    "params" : [
      "id" : pm1Id,
      "config": pm1Config
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetStatusCommand(Integer pm1Id = 0, String src = 'pm1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetStatus",
    "params" : ["id" : pm1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1ResetCountersCommand(Integer pm1Id = 0, String src = 'pm1ResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.ResetCounters",
    "params" : ["id" : pm1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1GetConfigCommand(Integer em1Id = 0, String src = 'em1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.GetConfig",
    "params" : [
      "id" : em1Id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1SetConfigCommand(Integer em1Id = 0, em1Config = [], src = 'em1SetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.SetConfig",
    "params" : [
      "id" : em1Id,
      "config": em1Config
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1GetStatusCommand(Integer em1Id = 0, String src = 'em1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.GetStatus",
    "params" : ["id" : em1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataSetConfigCommand(Integer id = 0, em1DataConfig = [], src = 'em1DataSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.SetConfig",
    "params" : [
      "id" : id,
      "config": em1DataConfig
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetConfigCommand(Integer id = 0, String src = 'em1DataGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetConfig",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetStatusCommand(Integer id = 0, String src = 'em1DataGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetStatus",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetRecordsCommand(Integer id = 0, Integer ts = 0, String src = 'em1DataGetRecords') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetRecords",
    "params" : [
      "id" : id,
      "ts" : ts
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetDataCommand(Integer id = 0, Integer ts = 0, Integer end_ts = 0, String src = 'em1DataGetData') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetRecords",
    "params" : [
      "id" : id,
      "ts" : ts,
      "end_ts" : end_ts
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DatDeleteAllDataCommand(Integer id = 0, String src = 'em1DatDeleteAllData') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.DeleteAllData",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataResetCountersCommand(Integer id = 0, String src = 'em1DataResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetNetEnergiesCommand(Integer id = 0, Integer ts = 0, Integer period = 300, Integer end_ts = null, String src = 'em1DataGetNetEnergies') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetNetEnergies",
    "params" : [
      "id" : id,
      "ts" : ts,
      "period" : period,
    ]
  ]
  if(end_ts != null) {command.params["end_ts"] = end_ts}
  return command
}

@CompileStatic
LinkedHashMap bleGetConfigCommand(String src = 'bleGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "BLE.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap bleSetConfigCommand(Boolean enable, Boolean rpcEnable, Boolean observerEnable) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "bleSetConfig",
    "method" : "BLE.SetConfig",
    "params" : [
      "id" : 0,
      "config": [
        "enable": enable,
        "rpc": rpcEnable,
        "observer": observerEnable
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputGetConfigCommand(Integer id = 0, String src = 'inputGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Input.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputGetStatusCommand(Integer id = 0, src = 'inputGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Input.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeGetConfigCommand(Integer id = 0, src = 'smokeGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeSetConfigCommand(Integer id = 0, String name = '', src = 'smokeSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.SetConfig",
    "params" : [
      "id" : id,
      "name" : name
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeGetStatusCommand(Integer id = 0, src = 'smokeGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeMuteCommand(Integer id = 0, src = 'smokeMute') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.Mute",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightGetConfigCommand(Integer id = 0, src = 'lightGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetConfigCommand(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Boolean nightModeEnable,
  Integer nightModeBrightness,
  Long current_limit,
  Integer id = 0,
  String src = 'lightSetConfig'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Light.SetConfig",
    "params" : [
      "id" : id,
      "config": [
        "initial_state": initial_state,
        "auto_on": auto_on,
        "auto_on_delay": auto_on_delay,
        "auto_off": auto_off,
        "auto_off_delay": auto_off_delay,
        "power_limit": power_limit,
        "voltage_limit": voltage_limit,
        "autorecover_voltage_errors": autorecover_voltage_errors,
        "current_limit": current_limit,
        "night_mode.enable": nightModeEnable,
        "night_mode.brightness": nightModeBrightness
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightGetStatusCommand(Integer id = 0, src = 'lightGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetCommand(
  Integer id = 0,
  Boolean on = null,
  Integer brightness = null,
  Integer transitionDuration = null,
  Integer toggleAfter = null,
  Integer offset = null,
  src = 'lightSet'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(brightness != null && offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(on != null) {command.params["on"] = on}
  if(brightness != null) {command.params["brightness"] = brightness}
  if(transitionDuration != null) {command.params["transition_duration"] = transitionDuration}
  if(toggleAfter != null) {command.params["toggle_after"] = toggleAfter}
  if(offset != null && brightness == null) {command.params["offset"] = offset}
  return command
}

@CompileStatic
LinkedHashMap lightSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'lightSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  return command
}

@CompileStatic
LinkedHashMap lightToggleCommand(Integer id = 0, src = 'lightToggle') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Toggle",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap lightDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap lightDimStopCommand(Integer id = 0, src = 'lightDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetAllCommand(
  Integer id = 0,
  Boolean on = null,
  Integer brightness = null,
  Integer transitionDuration = null,
  Integer toggleAfter = null,
  Integer offset = null,
  src = 'lightSet'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(brightness != null && offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(on != null) {command.params["on"] = on}
  if(brightness != null) {command.params["brightness"] = brightness}
  if(transitionDuration != null) {command.params["transition_duration"] = transitionDuration}
  if(toggleAfter != null) {command.params["toggle_after"] = toggleAfter}
  if(offset != null && brightness == null) {command.params["offset"] = offset}
  return command
}

@CompileStatic
LinkedHashMap rgbwSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'rgbwSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.white != null && args?.offsetWhite != null) {
    logWarn('Cannot set both white and offsetWhite offset at same time, using white value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.rgb != null) {command.params["rgb"] = args?.rgb}
  if(args?.white != null) {command.params["white"] = args?.white}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  if(args?.offsetWhite != null && args?.white == null) {command.params["offset_white"] = args?.offsetWhite}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'rgbwDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimStopCommand(Integer id = 0, src = 'rgbwDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap rgbSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'rgbSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.white != null && args?.offsetWhite != null) {
    logWarn('Cannot set both white and offsetWhite offset at same time, using white value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.rgb != null) {command.params["rgb"] = args?.rgb}
  if(args?.white != null) {command.params["white"] = args?.white}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  if(args?.offsetWhite != null && args?.white == null) {command.params["offset_white"] = args?.offsetWhite}
  return command
}

@CompileStatic
LinkedHashMap rgbDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'rgbDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbDimStopCommand(Integer id = 0, src = 'rgbDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

/* #endregion */
/* #region Parse */
@CompileStatic
void parse(String raw) {
  if(raw == null || raw == '') {return}
  if(raw.startsWith("{")) {parseWebsocketMessage(raw)}
  else {
    if(isGen1Device() == true) {parseGen1Message(raw)}
    else {parseGen2Message(raw)}
  }
}

@CompileStatic
void parseWebsocketMessage(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  logTrace("Incoming WS message: ${prettyJson(json)}")

  try {processGen2JsonMessage(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processGen2JsonMessage(): ${prettyJson(json)}")}

  try {processWebsocketMessagesAuth(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesAuth(): ${prettyJson(json)}")}

  try {processWebsocketMessagesConnectivity(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesConnectivity(): ${prettyJson(json)}")}

  try {processWebsocketMessagesBluetoothEvents(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesBluetoothEvents(): ${prettyJson(json)}")}
}

@CompileStatic
void processWebsocketMessagesConnectivity(LinkedHashMap json) {
  if(((String)json?.dst).startsWith('connectivityCheck-') && json?.result != null) {
    Long checkStarted = Long.valueOf(((String)json?.dst).split('-')[1])
    logDebug("Connectivity check started ${checkStarted}")
    if(checkStarted != null) {
      long seconds = unixTimeSeconds() - checkStarted
      if(seconds < 5) { setWebsocketStatus('open', 'connectivity check passed') }
      else { setWebsocketStatus('connection timed out', "response took ${seconds} seconds") }
    } else { setWebsocketStatus('connection timed out', 'no timestamp in check') }
    if(((LinkedHashMap)json.result)?.auth_en != null) {
      setAuthIsEnabled((Boolean)(((LinkedHashMap)json.result)?.auth_en))
      shellyGetStatusWs('authCheck')
    }
  }
}

@CompileStatic
void processWebsocketMessagesAuth(LinkedHashMap json) {
  if(json?.error != null ) {
    logTrace(prettyJson(json))
    LinkedHashMap error = (LinkedHashMap)json.error
    if(error?.message != null && error?.code == 401) {
      processUnauthorizedMessage(error.message as String)
    }
  }
}

@CompileStatic
void processGen2JsonMessage(LinkedHashMap jsonInput) {
  logTrace("Processing Gen2 Json Message: ${prettyJson(jsonInput)}")
  String dst = jsonInput?.dst
  LinkedHashMap<String, Object> json = [:]
  if(dst != null && dst != '') {
    if(jsonInput?.result != null && jsonInput?.result != '') {
      // json[dst] = (LinkedHashMap<String, Object>)jsonInput?.result
      json = (LinkedHashMap<String, Object>)jsonInput?.result
    } else if(jsonInput?.params != null && jsonInput?.params != '') {
      json = (LinkedHashMap<String, Object>)jsonInput?.params
    }
  }
  processGen2JsonMessageBody(json)
}

@CompileStatic
void processGen2JsonMessageBody(LinkedHashMap<String, Object> json, Integer id = null) {
  logTrace("Processing Gen2 Json Message Body: ${prettyJson(json)}")
  json.each{ k,v ->
    // Switches
    if(k.startsWith('switch')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      if(update?.output != null) {
        Boolean switchState = update?.output as Boolean
        if(switchState != null) { setSwitchState(switchState, id) }
      }
      if(getBooleanDeviceSetting('enablePowerMonitoring') == true) {
        if(update?.current != null && update?.current != '') {
          BigDecimal current =  (BigDecimal)update.current
          if(current != null) { setCurrent(current, id) }
        }

        if(update?.apower != null && update?.apower != '') {
          BigDecimal apower =  (BigDecimal)update.apower
          if(apower != null) { setPowerAttribute(apower, id) }
        }

        if(update?.voltage != null && update?.voltage != '') {
          BigDecimal voltage =  (BigDecimal)update.voltage
          if(voltage != null) { setVoltage(voltage, id) }
        }

        if(update?.freq != null && update?.freq != '') {
          BigDecimal freq =  (BigDecimal)update.freq
          if(freq != null) { setFrequency(freq, id) }
        }

        if(update?.aenergy != null && update?.aenergy != '') {
          LinkedHashMap aenergyMap = (LinkedHashMap)update?.aenergy
          BigDecimal aenergy =  (BigDecimal)aenergyMap?.total
          if(aenergy != null) { setEnergyAttribute(aenergy/1000, id) }
        }
      }
    }

    if(k.startsWith('cover')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      if(update?.current_pos != null) {
        Integer current_pos = update?.current_pos as Integer
        if(current_pos != null) { setCoverPosition(boundedLevel(current_pos), id) }
      }
      if(update?.state != null) {
        String coverState = update?.state as String
        if(coverState != null) {
          if(coverState == 'opening') {setCoverState(coverState, id)}
          else if(coverState == 'closing') {setCoverState(coverState, id)}
        }
      }
      if(getBooleanDeviceSetting('enablePowerMonitoring') == true) {
        if(update?.current != null && update?.current != '') {
          BigDecimal current =  (BigDecimal)update.current
          if(current != null) { setCurrent(current, id) }
        }

        if(update?.apower != null && update?.apower != '') {
          BigDecimal apower =  (BigDecimal)update.apower
          if(apower != null) { setPowerAttribute(apower, id) }
        }

        if(update?.voltage != null && update?.voltage != '') {
          BigDecimal voltage =  (BigDecimal)update.voltage
          if(voltage != null) { setVoltage(voltage, id) }
        }

        if(update?.freq != null && update?.freq != '') {
          BigDecimal freq =  (BigDecimal)update.freq
          if(freq != null) { setFrequency(freq, id) }
        }

        if(update?.aenergy != null && update?.aenergy != '') {
          LinkedHashMap aenergyMap = (LinkedHashMap)update?.aenergy
          BigDecimal aenergy =  (BigDecimal)aenergyMap?.total
          if(aenergy != null) { setEnergyAttribute(aenergy/1000, id) }
        }
      }
    }

    if(k.startsWith('pm1')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      if(update?.current != null && update?.current != '') {
        BigDecimal current =  (BigDecimal)update.current
        if(current != null) {setCurrent(current, id)}
      }

      if(update?.apower != null && update?.apower != '') {
        BigDecimal apower =  (BigDecimal)update.apower
        if(apower != null) {setPowerAttribute(apower, id)}
      }

      if(update?.voltage != null && update?.voltage != '') {
        BigDecimal voltage =  (BigDecimal)update.voltage
        if(voltage != null) {setVoltage(voltage, id)}
      }

      if(update?.aenergy != null && update?.aenergy != '') {
        LinkedHashMap aenergyMap = (LinkedHashMap)update?.aenergy
        BigDecimal aenergy =  (BigDecimal)aenergyMap?.total
        if(aenergy != null) {setEnergyAttribute(aenergy/1000, id)}
      }

      if(update?.freq != null && update?.freq != '') {
        BigDecimal freq =  (BigDecimal)update.freq
        if(freq != null) {setFrequency(freq, id)}
      }
    }

    if(k.startsWith('em1')) {
      LinkedHashMap update = (LinkedHashMap)v
      logTrace("Processing EM1 update: ${prettyJson(update)}")
      id = update?.id as Integer
      if(update?.current != null && update?.current != '') {
        BigDecimal current =  (BigDecimal)update.current
        if(current != null) {setCurrent(current, id)}
      }

      if(update?.act_power != null && update?.act_power != '') {
        BigDecimal act_power =  (BigDecimal)update.act_power
        if(act_power != null) {setPowerAttribute(act_power, id)}
      }

      if(update?.aprt_power != null && update?.aprt_power != '') {
        BigDecimal aprt_power =  (BigDecimal)update.aprt_power
        if(aprt_power != null) {setApparentPowerAttribute(aprt_power, id)}
      }

      if(update?.voltage != null && update?.voltage != '') {
        BigDecimal voltage =  (BigDecimal)update.voltage
        if(voltage != null) {setVoltage(voltage, id)}
      }

      if(update?.freq != null && update?.freq != '') {
        BigDecimal freq =  (BigDecimal)update.freq
        if(freq != null) {setFrequency(freq, id)}
      }

      if(update?.total_act_energy != null && update?.total_act_energy != '') {
        BigDecimal total_act_energy =  (BigDecimal)update?.total_act_energy
        if(total_act_energy != null) {setEnergyAttribute(total_act_energy/1000, id)}
      }

      if(update?.total_act_ret_energy != null && update?.total_act_ret_energy != '') {
        BigDecimal total_act_ret_energy =  (BigDecimal)update?.total_act_ret_energy
        if(total_act_ret_energy != null) {setReturnedEnergyAttribute(total_act_ret_energy/1000, id)}
      }
    }

    if(k.startsWith('em:') || k.startsWith('emdata:')) {
      LinkedHashMap update = (LinkedHashMap)v
      logTrace("Processing EM update: ${prettyJson(update)}")
      id = update?.id as Integer

      if(update?.a_current != null && update?.a_current != '') {
        BigDecimal current =  (BigDecimal)update.a_current
        if(current != null) {setCurrent(current, 0)}
      }
      if(update?.b_current != null && update?.b_current != '') {
        BigDecimal current =  (BigDecimal)update.b_current
        if(current != null) {setCurrent(current, 1)}
      }
      if(update?.c_current != null && update?.c_current != '') {
        BigDecimal current =  (BigDecimal)update.c_current
        if(current != null) {setCurrent(current, 2)}
      }
      if(update?.total_current != null && update?.total_current != '') {
        BigDecimal current =  (BigDecimal)update.total_current
        if(current != null) {setCurrent(current, null)}
      }


      if(update?.a_act_power != null && update?.a_act_power != '') {
        BigDecimal act_power =  (BigDecimal)update.a_act_power
        if(act_power != null) {setPowerAttribute(act_power, 0)}
      }
      if(update?.b_act_power != null && update?.b_act_power != '') {
        BigDecimal act_power =  (BigDecimal)update.b_act_power
        if(act_power != null) {setPowerAttribute(act_power, 1)}
      }
      if(update?.c_act_power != null && update?.c_act_power != '') {
        BigDecimal act_power =  (BigDecimal)update.c_act_power
        if(act_power != null) {setPowerAttribute(act_power, 2)}
      }
      if(update?.total_act_power != null && update?.total_act_power != '') {
        BigDecimal act_power =  (BigDecimal)update.total_act_power
        if(act_power != null) {setPowerAttribute(act_power, null)}
      }

      if(update?.a_aprt_power != null && update?.a_aprt_power != '') {
        BigDecimal aprt_power =  (BigDecimal)update.a_aprt_power
        if(aprt_power != null) {setApparentPowerAttribute(aprt_power, 0)}
      }
      if(update?.b_aprt_power != null && update?.b_aprt_power != '') {
        BigDecimal aprt_power =  (BigDecimal)update.b_aprt_power
        if(aprt_power != null) {setApparentPowerAttribute(aprt_power, 1)}
      }
      if(update?.c_aprt_power != null && update?.c_aprt_power != '') {
        BigDecimal aprt_power =  (BigDecimal)update.c_aprt_power
        if(aprt_power != null) {setApparentPowerAttribute(aprt_power, 2)}
      }
      if(update?.total_aprt_power != null && update?.total_aprt_power != '') {
        BigDecimal aprt_power =  (BigDecimal)update.total_aprt_power
        if(aprt_power != null) {setApparentPowerAttribute(aprt_power, null)}
      }

      if(update?.a_voltage != null && update?.a_voltage != '') {
        BigDecimal voltage =  (BigDecimal)update.a_voltage
        if(voltage != null) {setVoltage(voltage, 0)}
      }
      if(update?.b_voltage != null && update?.b_voltage != '') {
        BigDecimal voltage =  (BigDecimal)update.b_voltage
        if(voltage != null) {setVoltage(voltage, 1)}
      }
      if(update?.c_voltage != null && update?.c_voltage != '') {
        BigDecimal voltage =  (BigDecimal)update.c_voltage
        if(voltage != null) {setVoltage(voltage, 2)}
      }

      if(update?.a_freq != null && update?.a_freq != '') {
        BigDecimal freq =  (BigDecimal)update.a_freq
        if(freq != null) {setFrequency(freq, 0)}
      }
      if(update?.b_freq != null && update?.b_freq != '') {
        BigDecimal freq =  (BigDecimal)update.b_freq
        if(freq != null) {setFrequency(freq, 1)}
      }
      if(update?.c_freq != null && update?.c_freq != '') {
        BigDecimal freq =  (BigDecimal)update.c_freq
        if(freq != null) {setFrequency(freq, 2)}
      }

      if(update?.a_total_act_energy != null && update?.a_total_act_energy != '') {
        BigDecimal total_act_energy =  (BigDecimal)update?.a_total_act_energy
        if(total_act_energy != null) {setEnergyAttribute(total_act_energy/1000, 0)}
      }
      if(update?.b_total_act_energy != null && update?.b_total_act_energy != '') {
        BigDecimal total_act_energy =  (BigDecimal)update?.b_total_act_energy
        if(total_act_energy != null) {setEnergyAttribute(total_act_energy/1000, 1)}
      }
      if(update?.c_total_act_energy != null && update?.c_total_act_energy != '') {
        BigDecimal total_act_energy =  (BigDecimal)update?.c_total_act_energy
        if(total_act_energy != null) {setEnergyAttribute(total_act_energy/1000, 2)}
      }
      if(update?.total_act != null && update?.total_act != '') {
        BigDecimal total_act_energy =  (BigDecimal)update?.total_act
        if(total_act_energy != null) {setEnergyAttribute(total_act_energy/1000, null)}
      }

      if(update?.a_total_act_ret_energy != null && update?.a_total_act_ret_energy != '') {
        BigDecimal total_act_ret_energy =  (BigDecimal)update?.a_total_act_ret_energy
        if(total_act_ret_energy != null) {setReturnedEnergyAttribute(total_act_ret_energy/1000, 0)}
      }
      if(update?.b_total_act_ret_energy != null && update?.b_total_act_ret_energy != '') {
        BigDecimal total_act_ret_energy =  (BigDecimal)update?.b_total_act_ret_energy
        if(total_act_ret_energy != null) {setReturnedEnergyAttribute(total_act_ret_energy/1000, 1)}
      }
      if(update?.c_total_act_ret_energy != null && update?.c_total_act_ret_energy != '') {
        BigDecimal total_act_ret_energy =  (BigDecimal)update?.c_total_act_ret_energy
        if(total_act_ret_energy != null) {setReturnedEnergyAttribute(total_act_ret_energy/1000, 2)}
      }
      if(update?.total_act_ret != null && update?.total_act_ret != '') {
        BigDecimal total_act_ret_energy =  (BigDecimal)update?.total_act_ret
        if(total_act_ret_energy != null) {setReturnedEnergyAttribute(total_act_ret_energy/1000, null)}
      }
    }

    // Inputs
    if(k.startsWith('input')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer

      if(update?.state != null) {
        Boolean inputSwitchState = update?.state as Boolean
        if(inputSwitchState != null) {setInputSwitchState(inputSwitchState, id)}
      }

      if(update?.percent != null) {
        BigDecimal percent =  (BigDecimal)update.percent
        if(percent != null) {setInputAnalogState(percent, id)}
      }

      if(update?.counts != null) {
        Map counts = (LinkedHashMap)update?.counts
        if(counts?.total != null && counts?.size() > 0) {
          Integer cTot = counts.total as Integer
          if(cTot != null) { setInputCountState(cTot, id) }
        }
      }
    }

    // Temperatures
    if(k.startsWith('temperature')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer

      if(update?.tC != null) {
        BigDecimal tempC =  (BigDecimal)update.tC
        if(tempC != null) {setTemperatureC(tempC, id)}
      }
    }

    // Humidities
    if(k.startsWith('humidity')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      if(update?.rh != null) {
        BigDecimal rh = (BigDecimal)update.rh
        if(rh != null) {setHumidityPercent(rh, id)}
      }
    }

    // Lights
    if(k.startsWith('light')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      if(update?.brightness != null) {
        Integer brightness = update?.brightness as Integer
        setSwitchLevelAttribute(brightness, id)
      }
      if(update?.output != null) {
        Boolean switchState = update?.output as Boolean
        if(switchState != null) { setSwitchState(switchState, id) }
      }
    }

    if(k.startsWith('rgb')) {
      LinkedHashMap update = (LinkedHashMap)v
      id = update?.id as Integer
      ChildDeviceWrapper child = getRGBChildById(id)
      if(child == null && k.startsWith('rgbw')) {child = getRGBWChildById(id)}
      if(update?.brightness != null) {
        Integer brightness = update?.brightness as Integer
        if(brightness != null) {setLevelAttribute(brightness, child)}
      }
      if(update?.output != null) {
        Boolean output = update?.output as Boolean
        if(output != null) {
          String switchState = output == true ? 'on' : 'off'
          setSwitchAttribute(switchState, child)
        }
      }
      if(update?.rgb != null) {
        List<Integer> rgb = (List<Integer>)update?.rgb
        if(rgb != null) {
          setRGBAttribute(rgb[0], rgb[1], rgb[2], child)
          setColorAttribute(rgb[0], rgb[1], rgb[2], child)
          setColorNameAttribute(rgb[0], rgb[1], rgb[2], child)
          setHueSaturationAttributes(rgb[0], rgb[1], rgb[2], child)
        }
      }
      if(update?.white != null) {
        Integer white = update?.white as Integer
        white = (white / 2.55) as Integer
        if(white != null) {setWhiteLevelAttribute(white, child)}
      }
      if(update?.aenergy != null) {
        LinkedHashMap ae = (LinkedHashMap)update.aenergy
        if(ae?.total != null) {
          BigDecimal e = (BigDecimal)ae?.total
          setEnergyAttribute(e/1000, id)
        }
      }
      if(update?.apower != null) {
        BigDecimal power = (BigDecimal)update?.apower
        setPowerAttribute(power)
      }
      if(update?.current != null) {
        BigDecimal current = (BigDecimal)update?.current
        setCurrent(current)
      }
      if(update?.voltage != null) {
        BigDecimal voltage = (BigDecimal)update?.voltage
        setVoltage(voltage)
      }
      logTrace("Update: ${prettyJson(update)}")
    }
  }
  setLastUpdated()
}

@CompileStatic
void processWebsocketMessagesBluetoothEvents(LinkedHashMap json) {
  LinkedHashMap params = (LinkedHashMap)json?.params
  String src = ((String)json?.src).split('-')[0]
  if(json?.method == 'NotifyEvent') {
    if(params != null && params.containsKey('events')) {
      List<LinkedHashMap> events = (List<LinkedHashMap>)params.events
      events.each{ event ->
        LinkedHashMap evtData = (LinkedHashMap)event?.data
        logTrace("BTHome Event Data: ${prettyJson(evtData)}")
        if(evtData != null) {
          String address = (String)evtData?.address
          if(address != null && address != '' && evtData?.button != null) {
            address = address.replace(':','')
            List<Integer> buttons = []
            if(getClassName(evtData?.button) == 'java.util.ArrayList') {
              buttons = evtData?.button as ArrayList<Integer>
            } else {
              buttons = [evtData?.button as Integer]
            }
            sendEventToShellyBluetoothHelper("shellyBLEButtonDeviceEvents", buttons, address)
          }
          if(address != null && address != '' && evtData?.battery != null) {
            sendEventToShellyBluetoothHelper("shellyBLEBatteryEvents", evtData?.battery as Integer, address)
          }
          if(address != null && address != '' && evtData?.illuminance != null) {
            sendEventToShellyBluetoothHelper("shellyBLEIlluminanceEvents", evtData?.illuminance as Integer, address)
          }
          if(address != null && address != '' && evtData?.rotation != null) {
            sendEventToShellyBluetoothHelper("shellyBLERotationEvents", evtData?.rotation as BigDecimal, address)
          }
          if(address != null && address != '' && evtData?.rotation != null) {
            sendEventToShellyBluetoothHelper("shellyBLEWindowEvents", evtData?.window as Integer, address)
          }
          if(address != null && address != '' && evtData?.motion != null) {
            sendEventToShellyBluetoothHelper("shellyBLEMotionEvents", evtData?.motion as Integer, address)
          }
          if(address != null && address != '' && evtData?.temperature != null) {
            sendEventToShellyBluetoothHelper("shellyBLETemperatureEvents", evtData?.temperature as BigDecimal, address)
          }
          if(address != null && address != '' && evtData?.humidity != null) {
            sendEventToShellyBluetoothHelper("shellyBLEHumidityEvents", evtData?.humidity as Integer, address)
          }
        }
      }
    }
  }
}

@CompileStatic
void getStatusGen1(Boolean force = false) {
  if(hasCapabilityBatteryGen1() == true || force == true) {
    logTrace('Sending Gen1 Device Status Request...')
    sendGen1CommandAsync('status', null, 'getStatusGen1Callback')
  }
}

@CompileStatic
void refreshStatusGen1() {
  logTrace('Sending Gen1 Device Status Request...')
  parentSendGen1CommandAsync('status', null, 'getStatusGen1Callback')
}

// MARK: Gen1 Status Callback
@CompileStatic
void getStatusGen1Callback(AsyncResponse response, Map data = null) {
  logTrace('Processing gen1 status callback')
  if(responseIsValid(response) == true) {
    Map json = (LinkedHashMap)response.getJson()
    logTrace("getStatusGen1Callback JSON: ${prettyJson(json)}")
    if(hasCapabilityBatteryGen1() == true) {
      LinkedHashMap battery = (LinkedHashMap)json?.bat
      Integer percent = battery?.value as Integer
      if(percent != null) {setBatteryPercent(percent)}
    }
    if(hasCapabilityLuxGen1() == true) {
      Integer lux = ((LinkedHashMap)json?.lux)?.value as Integer
      if(lux != null ) {setIlluminance(lux)}
    }
    if(hasCapabilityTempGen1() == true) {
      BigDecimal temp = (BigDecimal)(((LinkedHashMap)json?.tmp)?.value)
      String tempUnits = (((LinkedHashMap)json?.tmp)?.units).toString()
      if(tempUnits == 'C' && temp != null) {
        setTemperatureC(temp)
      } else if(tempUnits == 'F' && temp != null) {
        setTemperatureF(temp)
      }
    }
    if(hasCapabilityHumGen1() == true) {
      BigDecimal hum = (BigDecimal)(((LinkedHashMap)json?.hum)?.value)
      if(hum != null) {setHumidityPercent(hum)}
    }
    if(hasCapabilityFloodGen1() == true) {
      Boolean flood = (Boolean)json?.flood
      if(flood != null) {setFloodOn(flood)}
    }
    if(hasCapabilitySwitch() == true || hasChildSwitches() == true) {
      List<LinkedHashMap> relays = (List<LinkedHashMap>)json?.relays
      if(relays != null) {
        relays.eachWithIndex{ relay, index ->
          logTrace("Relay status: ${relay}")
          if(relay?.ison != null) {
            setSwitchState(relay?.ison as Boolean, index as Integer)
          }
        }
      }
    }
    if(hasCapabilityValve() == true) {
      List<LinkedHashMap<String, String>> valves = (List<LinkedHashMap<String, String>>)json?.valves
      if(valves?.size() > 0) {
        valves.eachWithIndex{ valve, index ->
          String valveState = valve?.state
          logTrace("Valve status: ${valveState}")
          setValveState(valveState.startsWith('open') ? 'open' : 'closed')
        }
      }
      List<LinkedHashMap<String, String>> thermostats = (List<LinkedHashMap<String, String>>)json?.thermostats
      if(thermostats?.size() > 0) {
        thermostats.eachWithIndex{ tstat, index ->
          BigDecimal position = tstat?.pos as BigDecimal
          logTrace("Valve status: ${position}")
          setValvePositionState(position as Integer)
        }
      }
    }
    if(hasCapabilityCoverOrCoverChild() == true) {
      List<LinkedHashMap<String, Object>> rollers = (List<LinkedHashMap<String, Object>>)json?.rollers
      if(rollers?.size() > 0) {
        rollers.eachWithIndex{ roller, index ->
          Integer position = roller?.current_pos as Integer
          logTrace("Cover position from Shelly: ${position}")
          setCoverPosition(boundedLevel(position))
        }
      }
    }
    if(thisDevice().hasAttribute('ppm')) {
      LinkedHashMap concentration = (LinkedHashMap)json?.concentration
      if(concentration?.ppm != null) {setGasPPM(concentration?.ppm as Integer)}
    }
    if(thisDevice().hasAttribute('selfTestState') || thisDevice().hasAttribute('naturalGas')) {
      LinkedHashMap gas_sensor = (LinkedHashMap)json?.gas_sensor
      if(gas_sensor?.self_test_state != null && thisDevice().hasAttribute('selfTestState')) {
        String self_test_state = gas_sensor?.self_test_state.toString()
        sendDeviceEvent([name: 'selfTestState', value: self_test_state])
      }
      if(gas_sensor?.alarm_state != null && thisDevice().hasAttribute('naturalGas')) {
        String alarm_state = gas_sensor?.alarm_state.toString()
        sendDeviceEvent([name: 'naturalGas', value: alarm_state in ['mild','heavy'] ? 'detected' : 'clear'])
      }
    }
    if(hasPollingChildren() == true) {
      if(json?.ext_temperature != null) {
        LinkedHashMap temps = (LinkedHashMap)json.ext_temperature
        temps.each{ k,v ->
          LinkedHashMap val = (LinkedHashMap)v
          Integer id = k as Integer
          BigDecimal tC = (BigDecimal)val?.tC
          BigDecimal tF = (BigDecimal)val?.tF
          if(tC != null) {
            setTemperatureC(tC, id)
            ChildDeviceWrapper tSwitch = getTemperatureSwitchChildById(id)
            if(tSwitch != null) {
              BigDecimal overT = getBigDecimalDeviceSettingAsCelcius('gen1_ext_temp_over_value')
              BigDecimal underT = getBigDecimalDeviceSettingAsCelcius('gen1_ext_temp_under_value')
              Boolean isOn = tC > overT
              if(isOn == true) {setGen1TemperatureSwitchState('on', id)}
              Boolean isOff = tC < underT
              if(isOff == true) {setGen1TemperatureSwitchState('off', id)}
            }
          }
        }
      }
      if(json?.ext_humidity != null) {
        LinkedHashMap hums = (LinkedHashMap)json.ext_humidity
        hums.each{ k,v ->
          LinkedHashMap val = (LinkedHashMap)v
          Integer id = k as Integer
          BigDecimal hum = (BigDecimal)val?.hum
          if(hum != null) {
            setHumidityPercent(hum, k as Integer)
            ChildDeviceWrapper hSwitch = getHumiditySwitchChildById(id)
            if(hSwitch != null) {
              BigDecimal overH = getBigDecimalDeviceSettingAsCelcius('gen1_ext_hum_over_value')
              BigDecimal underH = getBigDecimalDeviceSettingAsCelcius('gen1_ext_hum_under_value')
              Boolean isOn = hum > overH
              if(isOn == true) {setGen1HumiditySwitchState('on', id)}
              Boolean isOff = hum < underH
              if(isOff == true) {setGen1HumiditySwitchState('off', id)}
            }
          }
        }
      }
      if(json?.adcs != null) {
        List<LinkedHashMap> adcs = (List<LinkedHashMap>)json.adcs
        adcs.eachWithIndex{ adc, id ->
          BigDecimal volts = (BigDecimal)adc?.voltage
          if(volts != null) {
            setVoltage(volts, id)
            ChildDeviceWrapper adcSwitch = getAdcSwitchChildById(id)
            if(adcSwitch != null) {
              BigDecimal overA = getBigDecimalDeviceSettingAsCelcius('gen1_adc_over_value')
              BigDecimal underA = getBigDecimalDeviceSettingAsCelcius('gen1_adc_under_value')
              Boolean isOn = volts > overA
              if(isOn == true) {setGen1AdcSwitchState('on', id)}
              Boolean isOff = volts < underA
              if(isOff == true) {setGen1AdcSwitchState('off', id)}
            }
          }
        }
      }
    }
    if(json?.lights != null) {
      List<LinkedHashMap> lights = (List<LinkedHashMap>)json?.lights
      lights.eachWithIndex{ light, index ->
        processGen1LightStatus(light, index)
      }
    }
    if(json?.thermostats != null) {
      List<LinkedHashMap> thermostats = (List<LinkedHashMap>)json?.thermostats
      thermostats.eachWithIndex{ tstat, index ->
        if(tstat?.pos != null) {setValvePositionState(tstat.pos as Integer)}
        if(tstat?.tmp != null) {
          LinkedHashMap tmp = (LinkedHashMap)tstat.tmp
          if(tmp?.units != null && tmp?.value != null) {
            if(isCelciusScale() == true && tmp?.units == 'C') {
              setTemperatureC(tmp?.value as BigDecimal)
            } else if(isCelciusScale() == false && tmp?.units == 'C') {
              setTemperatureF(cToF(tmp?.value as BigDecimal))
            } else if(isCelciusScale() == true && tmp?.units == 'F') {
              setTemperatureC(fToC(tmp?.value as BigDecimal))
            } else if(isCelciusScale() == false && tmp?.units == 'F') {
              setTemperatureF(tmp?.value as BigDecimal)
            }
          }
        }
      }
    }
    if(json?.meters != null) {
      List<LinkedHashMap> meters = (List<LinkedHashMap>)json?.meters
      meters.eachWithIndex{ meter, index ->
        if(meter?.power != null) {setPowerAttribute((BigDecimal)meter.power)}
        if(meter?.total != null) {setEnergyAttribute(wattMinuteToKWh((BigDecimal)meter.total))}
      }
    }
  }
}

@CompileStatic
void gen1LightCallback(AsyncResponse response, Map data = null) {
  logTrace('Processing gen1 light callback')
  if(responseIsValid(response) == true) {
    Map json = (LinkedHashMap)response.getJson()
    processGen1LightStatus(json)
  }
}

@CompileStatic
void processGen1LightStatus(Map json, Integer index = 0) {
  logTrace("gen1LightCallback JSON: ${prettyJson(json)}")
  if(json?.red != null && json?.green != null && json?.blue != null) {
    setColorAttribute(json.red as Integer, json.green as Integer, json.blue as Integer)
    setRGBAttribute(json.red as Integer, json.green as Integer, json.blue as Integer)
    setColorNameAttribute(json.red as Integer, json.green as Integer, json.blue as Integer)
    setHueSaturationAttributes(json.red as Integer, json.green as Integer, json.blue as Integer)
  }
  if(hasCapabilityColorMode() == true) {
    if(json?.mode == 'color') {setColorModeAttribute('RGB')}
    if(json?.mode == 'white') {setColorModeAttribute('CT')}
    if(json?.mode != null) {setDeviceDataValue('lightMode', "${json.mode}")}
  }
  Integer brightness = json?.brightness as Integer
  if(brightness != null) {setSwitchLevelAttribute(brightness, index)}
  Boolean isOn = json?.ison as Boolean
  if(isOn != null) {setSwitchState(isOn)}
  Integer whiteLevel = json?.white as Integer
  if(whiteLevel != null && hasCapabilityWhiteLevel() == true) {
    whiteLevel = (Integer)(whiteLevel/255)
    setWhiteLevelAttribute(whiteLevel)
  }
  Integer colorTemp = json?.temp as Integer
  if(colorTemp != null && hasCapabilityColorTemperature() == true) {
    setColorTemperatureAttribute(colorTemp)
  }
}

@CompileStatic
void getBatteryStatusGen2() {
  if(hasCapabilityBattery()) {
    postCommandAsync(devicePowerGetStatusCommand(), 'getStatusGen2Callback')
  }
}

@CompileStatic
void getStatusGen2Callback(AsyncResponse response, Map data = null) {
  logTrace('Processing gen2+ status callback')
  if(responseIsValid(response) == true) {
    Map json = (LinkedHashMap)response.getJson()
    logTrace("getStatusGen2Callback JSON: ${prettyJson(json)}")
    LinkedHashMap result = (LinkedHashMap)json?.result
    LinkedHashMap battery = (LinkedHashMap)result?.battery
    Integer percent = battery?.percent as Integer
    if(percent != null) {setBatteryPercent(percent)}
    processGen2JsonMessage(json)
  }
}

// MARK: Parse Gen1 Webhook
@CompileStatic
void parseGen1Message(String raw) {
  getStatusGen1()
  LinkedHashMap message = decodeLanMessage(raw)
  String ip = getIpAddressFromHexAddress(message?.ip as String)
  if(ip != getIpAddress()) { setIpAddress(ip, true) }
  LinkedHashMap headers = message?.headers as LinkedHashMap
  logTrace("Gen1 Webhook Message: ${message}")
  logTrace("Gen1 Webhook Headers: ${headers}")
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  if(query[0] == 'report') {}

  else if(query[0] == 'motion_on') {setMotionOn(true)}
  else if(query[0] == 'motion_off') {setMotionOn(false)}
  else if(query[0] == 'tamper_alarm_on') {setTamperOn(true)}
  else if(query[0] == 'tamper_alarm_off') {setTamperOn(false)}

  else if(query[0] == 'alarm_mild') {setGasDetectedOn(true)}
  else if(query[0] == 'alarm_heavy') {setGasDetectedOn(true)}
  else if(query[0] == 'alarm_off') {setGasDetectedOn(false)}

  else if(query[0] == 'shortpush') {setPushedButton(1, query[1] as Integer)}
  else if(query[0] == 'double_shortpush') {setPushedButton(2, query[1] as Integer)}
  else if(query[0] == 'triple_shortpush') {setPushedButton(3, query[1] as Integer)}
  else if(query[0] == 'longpush') {setHeldButton(1)}

  else if(query[0] == 'flood_detected') {setFloodOn(true)}
  else if(query[0] == 'flood_gone') {setFloodOn(false)}

  else if(query[0] == 'out_on') {
    setSwitchState(true, query[1] as Integer)
    if(hasCapabilityLight() == true || hasChildSwitches() == true) {getStatusGen1(true)}
  }
  else if(query[0] == 'out_off') {
    setSwitchState(false, query[1] as Integer)
    if(hasCapabilityLight() == true || hasChildSwitches() == true) {getStatusGen1(true)}
  }

  else if(query[0] == 'btn_on') {
    setInputSwitchState(true, query[1] as Integer)
  }
  else if(query[0] == 'btn_off') {
    setInputSwitchState(false, query[1] as Integer)
  }

  else if(query[0] == 'btn1_on') {
    setInputSwitchState(true, 0 as Integer)
  }
  else if(query[0] == 'btn1_off') {
    setInputSwitchState(false, 0 as Integer)
  }

  else if(query[0] == 'btn2_on') {
    setInputSwitchState(true, 1 as Integer)
  }
  else if(query[0] == 'btn2_off') {
    setInputSwitchState(false, 1 as Integer)
  }

  else if(query[0] == 'btn1_shortpush') {setPushedButton(1, 0 as Integer)}
  else if(query[0] == 'btn1_longpush') {setHeldButton(1, 0 as Integer)}

  else if(query[0] == 'btn2_shortpush') {setPushedButton(1, 1 as Integer)}
  else if(query[0] == 'btn2_longpush') {setHeldButton(1, 1 as Integer)}

  else if(query[0] == 'humidity.change') {setHumidityPercent(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tC') {setTemperatureC(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tF') {setTemperatureF(new BigDecimal(query[2]))}

  else if(query[0] == 'adc_over') {setGen1AdcSwitchState('on', query[1] as Integer)}
  else if(query[0] == 'adc_under') {setGen1AdcSwitchState('off', query[1] as Integer)}

  else if(query[0] == 'ext_temp_over') {setGen1TemperatureSwitchState('on', query[1] as Integer)}
  else if(query[0] == 'ext_temp_under') {setGen1TemperatureSwitchState('off', query[1] as Integer)}

  else if(query[0] == 'ext_hum_over') {setGen1HumiditySwitchState('on', query[1] as Integer)}
  else if(query[0] == 'ext_hum_under') {setGen1HumiditySwitchState('off', query[1] as Integer)}

  else if(query[0] == 'pm1.apower_change'  && query.size() == 4) {setPowerAttribute(new BigDecimal(query[2]), query[3] as Integer)}
  else if(query[0] == 'pm1.current_change' && query.size() == 4) {setCurrent(new BigDecimal(query[2]), query[3] as Integer)}
  else if(query[0] == 'pm1.voltage_change' && query.size() == 4) {setVoltage(new BigDecimal(query[2]), query[3] as Integer)}

  else if(query[0] == 'roller_open') {setCoverState('opening', query[1] as Integer)}
  else if(query[0] == 'roller_stop') {getStatusGen1(true)}
  else if(query[0] == 'roller_close') {setCoverState('closing', query[1] as Integer)}

  setLastUpdated()
}



@CompileStatic
void parseGen2Message(String raw) {
  logTrace("Raw gen2Message: ${raw}")
  getBatteryStatusGen2()
  LinkedHashMap message = decodeLanMessage(raw)
  logTrace("Received incoming message: ${prettyJson(message)}")
  LinkedHashMap headers = message?.headers as LinkedHashMap
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  logTrace("Incoming query ${query}")
  Integer id = 0
  if(query.size() > 3) { id = query[3] as Integer}
  if(query[0] == 'report') {}
  else if(query[0] == 'humidity.change' && query[1] == 'rh') {setHumidityPercent(new BigDecimal(query[2]), id)}
  else if(query[0] == 'temperature.change' && query[1] == 'tC') {setTemperatureC(new BigDecimal(query[2]), id)}
  else if(query[0] == 'temperature.change' && query[1] == 'tF') {setTemperatureF(new BigDecimal(query[2]), id)}
  else if(query[0].startsWith('switch.o')) {
    String command = query[0]
    id = query[1] as Integer
    setSwitchState(command.toString() == 'switch.on', id)
  }
  else if(query[0].startsWith('input.toggle')) {
    String command = query[0]
    id = query[1] as Integer
    setInputSwitchState(command.toString() == 'input.toggle_on', id)
  }
  else if(query[0].startsWith('input.analog_measurement') && query[1].startsWith('percent')) {
    String command = query[0]
    id = query[3] as Integer
    setInputAnalogState(new BigDecimal(query[2]), id)
  }
  else if(query[0] in ['cover.opening', 'cover.closing']) {
    String command = query[0]
    id = query[1] as Integer
    setCoverState(query[0].replace('cover.',''), id) //['opening', 'partially open', 'closed', 'open', 'closing', 'unknown']
  }
  else if(query[0] in ['cover.open', 'cover.closed']) {
    String command = query[0]
    id = query[1] as Integer
    setCoverState(query[0].replace('cover.',''), id) //['opening', 'partially open', 'closed', 'open', 'closing', 'unknown']
    getStatusGen2()
    runInSeconds('getStatusGen2', 30)
  }
  else if(query[0] in ['cover.stopped']) {
    String command = query[0]
    id = query[1] as Integer
    setCoverState('partially open', id) //['opening', 'partially open', 'closed', 'open', 'closing', 'unknown']
    getStatusGen2()
    runInSeconds('getStatusGen2', 30)
  }
  else if(query[0] == 'smoke.alarm')      {setSmokeState('detected', query[1] as Integer)}
  else if(query[0] == 'smoke.alarm_off')  {setSmokeState('clear', query[1] as Integer)}
  else if(query[0] == 'smoke.alarm_test') {setSmokeState('tested', query[1] as Integer)}
  else if(query[0].startsWith('light.o')) {
    String command = query[0]
    id = query[1] as Integer
    setSwitchState(command.toString() == 'light.on', id)
    getStatusGen2()
  }
  setLastUpdated()
}

@CompileStatic
void getStatusGen2() {
  parentPostCommandAsync(shellyGetStatusCommand(), 'getStatusGen2Callback')
}

/* #endregion */
/* #region Websocket Commands */
@CompileStatic
String shellyGetDeviceInfo(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  if(src == 'connectivityCheck') {
    long seconds = unixTimeSeconds()
    src = "${src}-${seconds}"
  }
  Map command = shellyGetDeviceInfoCommand(fullInfo, src)
  // String json = JsonOutput.toJson(command)
  parentPostCommandSync(command)
}

@CompileStatic
String shellyGetDeviceInfoWs(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  if(src == 'connectivityCheck') {
    long seconds = unixTimeSeconds()
    src = "${src}-${seconds}"
  }
  Map command = shellyGetDeviceInfoCommand(fullInfo, src)
  String json = JsonOutput.toJson(command)
  parentSendWsMessage(json)
}

@CompileStatic
String shellyGetStatus(String src = 'shellyGetStatus') {
  LinkedHashMap command = shellyGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String shellyGetStatusWs(String src = 'shellyGetStatus') {
  LinkedHashMap command = shellyGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  String json = JsonOutput.toJson(command)
  parentSendWsMessage(json)
}

@CompileStatic
String sysGetStatus(String src = 'sysGetStatus') {
  LinkedHashMap command = sysGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchGetStatus() {
  LinkedHashMap command = switchGetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchSet(Boolean on) {
  LinkedHashMap command = switchSetCommand(on)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchGetConfig(Integer id = 0, String src = 'switchGetConfig') {
  LinkedHashMap command = switchGetConfigCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchSetConfig(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit
) {
  Map command = switchSetConfigCommand(initial_state, auto_on, auto_on_delay, auto_off, auto_off_delay, power_limit, voltage_limit, autorecover_voltage_errors, current_limit)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchSetConfigJson(Map jsonConfigToSend, Integer switchId = 0) {
  Map command = switchSetConfigCommandJson(jsonConfigToSend, switchId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void coverSetConfigJson(Map jsonConfigToSend, Integer coverId = 0) {
  Map command = coverSetConfigCommandJson(jsonConfigToSend, coverId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void inputSetConfigJson(Map jsonConfigToSend, Integer inputId = 0) {
  Map command = inputSetConfigCommandJson(jsonConfigToSend, inputId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchResetCounters(Integer id = 0, String src = 'switchResetCounters') {
  LinkedHashMap command = switchResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void lightResetCounters(Integer id = 0, String src = 'lightResetCounters') {
  LinkedHashMap command = lightResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void coverResetCounters(Integer id = 0, String src = 'coverResetCounters') {
  LinkedHashMap command = coverResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void em1DataResetCounters(Integer id = 0, String src = 'em1DataResetCounters') {
  LinkedHashMap command = em1DataResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void resetCountersCallback(AsyncResponse response, Map data = null) {
  logTrace('Processing reset counters callback')
  if(responseIsValid(response) == true) {
    Map json = (LinkedHashMap)response.getJson()
    logTrace("resetCountersCallback JSON: ${prettyJson(json)}")
  }
}

@CompileStatic
void scriptList() {
  LinkedHashMap command = scriptListCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String pm1GetStatus() {
  LinkedHashMap command = pm1GetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}
/* #endregion */
/* #region Webhook Helpers */
@CompileStatic
void setDeviceActionsGen1() {
  logTrace('Setting device actions for gen 1 device...')
  LinkedHashMap<String, List> actions = getDeviceActionsGen1()
  logDebug("Gen 1 Actions: ${prettyJson(actions)}")
  actions.each{ k,v ->
    v.each{ m ->
      Integer index = ((Map)m)?.index as Integer
      String name = "${k}".toString()
      Boolean hasEnabledTimes = actionHasEnabledTimes(k,v)

      Boolean create = false
      String additionalParams = ''
      LinkedHashMap statusResult = null
      if((hasActionsToCreateList() == true && k in getActionsToCreate()) ||  hasActionsToCreateList() == false) {
        if(k in ['adc_over_url','adc_under_url']) {
          create = getBooleanDeviceSetting('gen1_create_child_switch_dev_adc')
          additionalParams = k == 'adc_over_url' ? "&adc_over_value=${getDeviceSettings().gen1_adc_over_value}" : "&adc_under_value=${getDeviceSettings().gen1_adc_under_value}"
        }
        else if(k in ['ext_hum_over_url','ext_hum_under_url']) {
          create = getBooleanDeviceSetting('gen1_create_child_switch_dev_humidity')
          additionalParams = k == 'ext_hum_over_url' ? "&ext_hum_over_value=${getDeviceSettings().gen1_ext_hum_over_value}" : "&ext_hum_under_value=${getDeviceSettings().gen1_ext_hum_under_value}"
        }
        else if(k in ['ext_temp_over_url','ext_temp_under_url']) {
          create = getBooleanDeviceSetting('gen1_create_child_switch_dev_temp')
          if(statusResult == null) {statusResult = (LinkedHashMap<String, Object>)sendGen1Command('status')}
          LinkedHashMap extSensors = (LinkedHashMap)statusResult?.ext_sensors
          Boolean shellySetToC = extSensors?.temperature_unit == 'C'
          BigDecimal overT = getBigDecimalDeviceSetting('gen1_ext_temp_over_value')
          BigDecimal underT = getBigDecimalDeviceSetting('gen1_ext_temp_under_value')
          logTrace("Raw gen1_ext_temp_over_value gen1_ext_temp_under_value ${overT} ${underT}")
          if(isCelciusScale() == true && shellySetToC == false) {
            overT = cToF(overT)
            underT = cToF(underT)
          } else if(isCelciusScale() == false && shellySetToC == true) {
            overT = fToC(overT)
            underT = fToC(underT)
          }
          logTrace("Adjusted gen1_ext_temp_over_value gen1_ext_temp_under_value ${overT} ${underT}")
          additionalParams = k == 'ext_temp_over_url' ? "&ext_temp_over_value=${overT}" : "&ext_temp_under_value=${underT}"
        }
        else {create = true}
      }
      if(create == true) {createHubitatWebhookGen1(index, name, hasEnabledTimes, additionalParams)}
      else {deleteHubitatWebhookGen1(index, name, hasEnabledTimes)}
    }
  }
}

@CompileStatic
Boolean actionHasEnabledTimes(String actionName, List<LinkedHashMap> action) {
  if(hasActionsToCreateEnabledTimesList() == true && actionName in getActionsToCreateEnabledTimes()) {
    return true
  } else if(action != null && action?.size() > 0) {
    Map a = action[0]
    if(a?.urls != null) {
      List urls = (List)a.urls
      if(getClassName(urls[0]) == 'groovy.json.internal.LazyMap') {
        Map url = (Map)urls[0]
        if(url?.int != null) { return true }
      }
    }
  }
  return false
}

String getClassName(Object obj) {return getObjectClassName(obj)}

@CompileStatic
void setDeviceActionsGen2() {
  logTrace('Setting device actions for gen2+ device...')
  LinkedHashMap supported = getSupportedWebhooks()
  if(supported?.result != null) {
    supported = (LinkedHashMap)supported.result
    logDebug("Got supported webhooks: ${prettyJson(supported)}")
  }
  LinkedHashMap types = (LinkedHashMap)supported?.types

  LinkedHashMap currentWebhooksResult = getCurrentWebhooks()

  List<LinkedHashMap> currentWebhooks = []
  if(currentWebhooksResult?.result != null) {
    LinkedHashMap result = (LinkedHashMap)currentWebhooksResult.result
    if(result != null && result.size() > 0 && result?.hooks != null) {
      currentWebhooks = (List<LinkedHashMap>)result.hooks
      logDebug("Got current webhooks: ${prettyJson(result)}")
    }
    logDebug("Current webhooks count: ${currentWebhooks.size()}")
  }
  if(types != null) {
    logDebug("Got supported webhook types: ${prettyJson(types)}")
    Map shellyGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(shellyGetConfigCommand())?.result
    logDebug("Shelly.GetConfig Result: ${prettyJson(shellyGetConfigResult)}")

    Set<String> switches = shellyGetConfigResult.keySet().findAll{it.startsWith('switch')}
    Set<String> inputs = shellyGetConfigResult.keySet().findAll{it.startsWith('input')}
    Set<String> covers = shellyGetConfigResult.keySet().findAll{it.startsWith('cover')}
    Set<String> temps = shellyGetConfigResult.keySet().findAll{it.startsWith('temperature')}
    Set<String> hums = shellyGetConfigResult.keySet().findAll{it.startsWith('humidity')}
    Set<String> pm1s = shellyGetConfigResult.keySet().findAll{it.startsWith('pm1')}
    Set<String> smokes = shellyGetConfigResult.keySet().findAll{it.startsWith('smoke')}
    Set<String> lights = shellyGetConfigResult.keySet().findAll{it.startsWith('light')}
    Set<String> rgbs = shellyGetConfigResult.keySet().findAll{it.startsWith('rgb:')}
    Set<String> rgbws = shellyGetConfigResult.keySet().findAll{it.startsWith('rgbw')}

    logDebug("Found Switches: ${switches}")
    logDebug("Found Inputs: ${inputs}")
    logDebug("Found Covers: ${covers}")
    logDebug("Found Temperatures: ${temps}")
    logDebug("Found Humdities: ${hums}")
    logDebug("Found PM1s: ${pm1s}")
    logDebug("Found Smokes: ${smokes}")
    logDebug("Found Lights: ${lights}")
    logDebug("Found RGBs: ${rgbs}")
    logDebug("Found RGBWs: ${rgbws}")

    // LinkedHashMap inputConfig = (LinkedHashMap)shellyGetConfigResult[inp]
    // String inputType = (inputConfig?.type as String).capitalize()

    types.each{k,v ->
      String type = k.toString()
      LinkedHashMap val = (LinkedHashMap)v
      List<LinkedHashMap> attrs = []
      if(val != null && val.size() > 0) {
        attrs = ((LinkedHashMap)val).attrs as List<LinkedHashMap>
      }
      logDebug("Processing type: ${type} with value: ${prettyJson(val)}")


      if(type.startsWith('input')) {
        inputs.each{ inp ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[inp]
          String inputType = (conf?.type as String).capitalize()
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for input:${cid}, type: ${inputType}, config: ${prettyJson(conf)}...")
          logDebug("Type is: ${type} inputType is: ${inputType}")
          if(type.contains('button') && inputType == 'Button') {
            logDebug("Processing ${name}...")
            processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
          } else if(type.contains('toggle') && inputType == 'Switch') {
            logDebug("Processing ${name}...")
            processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
          } else if(type.contains('analog') && inputType == 'Analog') {
            logDebug("Processing ${name}...")
            processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
          }
        }
      } else if(type.startsWith('switch')) {
        switches.each{ sw ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[sw]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for switch:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('temperature.change')) {
        temps.each{ t ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[t]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for temperature:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('humidity.change')) {
        hums.each{ h ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[h]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for humidity:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('pm1')) {
        pm1s.each{ pm1 ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[pm1]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for pm1:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('cover')) {
        covers.each{ cover ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[cover]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for cover:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('smoke')) {
        smokes.each{ smoke ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[smoke]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for smoke:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('light')) {
        lights.each{ light ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[light]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for light:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('rgb:')) {
        rgbs.each{ rgb ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[rgb]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for rgb:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      } else if(type.startsWith('rgbw')) {
        rgbws.each{ rgbw ->
          LinkedHashMap conf = (LinkedHashMap)shellyGetConfigResult[rgbw]
          Integer cid = conf?.id as Integer
          String name = "hubitat.${type}".toString()
          logDebug("Processing webhook for rgbw:${cid}...")
          processWebhookCreateOrUpdate(name, cid, currentWebhooks, type, attrs)
        }
      }
    }
  }
}

@CompileStatic
void processWebhookCreateOrUpdate(String name, Integer cid, List<LinkedHashMap> currentWebhooks, String type, List<LinkedHashMap> attrs = []) {
  if(currentWebhooks != null || currentWebhooks.size() > 0) {
    logTrace("Current Webhooks: ${currentWebhooks}")
    LinkedHashMap currentWebhook = [:]
    currentWebhooks.each{ hook ->
      if(hook?.name == "${name}.${cid}".toString()) {currentWebhook = hook}
    }
    logDebug("Webhook name: ${name}, found current webhook:${currentWebhook}")
    if(attrs.size() > 0) {
      logDebug('Webhook has attrs, processing each to set webhoook...')
      attrs.each{
        String event = it.name.toString()
        currentWebhooks.each{ hook ->
          if(hook?.name == "${name}.${event}.${cid}".toString()) {currentWebhook = hook}
        }
        logDebug("Current Webhook: ${currentWebhook}")
        if(event == 'tF') {
          logTrace('Skipping webhook creating for F changes, no need to send both C and F to Hubitat')
        } else {
          webhookCreateOrUpdate(type, event, cid, currentWebhook)
        }
      }
    } else {
      webhookCreateOrUpdate(type, null, cid, currentWebhook)
    }
  } else {
    logDebug("Creating new webhook for ${name}:${cid}...")
    if(attrs.size() > 0) {
      attrs.each{
        String event = it.name.toString()
        webhookCreateOrUpdate(type, event, cid, null)
      }
    } else {
      webhookCreateOrUpdate(type, null, cid, null)
    }
  }
}



@CompileStatic
LinkedHashMap<String,List> getCurrentWebhooks() {
  return postCommandSync(webhookListCommand())
}

@CompileStatic
LinkedHashMap getSupportedWebhooks() {
  return postCommandSync(webhookListSupportedCommand())
}

@CompileStatic
void webhookCreate(String type, String event, Integer cid) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookCreate",
    "method" : "Webhook.Create",
    "params" : [
      "cid": cid,
      "enable": true,
      "name": "hubitat.${type}.${event}.${cid}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${event}/\${ev.${event}}/${cid}".toString()]
    ]
  ]
  postCommandSync(command)
}

@CompileStatic
void webhookCreateNoEvent(String type, Integer cid) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "webhookCreate",
    "method" : "Webhook.Create",
    "params" : [
      "cid": cid,
      "enable": true,
      "name": "hubitat.${type}.${cid}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${cid}".toString()]
    ]
  ]
  postCommandSync(command)
}

@CompileStatic
void webhookUpdate(String type, String event, Integer id, Integer cid) {
  LinkedHashMap command = [
    "id" : id,
    "src" : "webhookUpdate",
    "method" : "Webhook.Update",
    "params" : [
      "id": id,
      "cid": cid,
      "enable": true,
      "name": "hubitat.${type}.${event}.${cid}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${event}/\${ev.${event}}/${cid}".toString()]
    ]
  ]
  postCommandSync(command)
}

@CompileStatic
void webhookUpdateNoEvent(String type, Integer id, Integer cid) {
  LinkedHashMap command = [
    "id" : id,
    "src" : "webhookUpdate",
    "method" : "Webhook.Update",
    "params" : [
      "id": id,
      "cid": cid,
      "enable": true,
      "name": "hubitat.${type}.${cid}".toString(),
      "event": "${type}".toString(),
      "urls": ["${getHubBaseUri()}/${type}/${cid}".toString()]
    ]
  ]
  postCommandSync(command)
}

@CompileStatic
void webhookCreateOrUpdate(String type, String event, Integer cid, LinkedHashMap currentWebhook) {
  logDebug("Webhook Create or Update called with type: ${type}, event ${event}, CID: ${cid}, currentWebhook: ${currentWebhook}")
  if(currentWebhook != null && currentWebhook.size() > 0) {
    Integer id = (currentWebhook?.id) as Integer
    if(id != null) {
      if(event != null && event != '') { webhookUpdate(type, event, id, cid) }
      else { webhookUpdateNoEvent(type, id, cid) }
    } else {
      if(event != null && event != '') { webhookCreate(type, event, cid) }
      else { webhookCreateNoEvent(type, cid) }
    }
  } else {
      if(event != null && event != '') { webhookCreate(type, event, cid) }
      else { webhookCreateNoEvent(type, cid) }
    }
}

@CompileStatic
void deleteHubitatWebhooks() {
  LinkedHashMap result = (LinkedHashMap)((getCurrentWebhooks())?.result)
  List<LinkedHashMap> currentWebhooks = (List<LinkedHashMap>)result?.hooks
  currentWebhooks.each{ webhook ->
    String name = (webhook?.name).toString()
    logDebug("Deleting webhook named: ${name}")
    if(name.startsWith('hubitat.')) {
      LinkedHashMap command = webhookDeleteCommand(webhook?.id as Integer)
      postCommandSync(command)
    }
  }
}

@CompileStatic
void deleteHubitatWebhooksGen1() {
  LinkedHashMap<String, List> actions = getDeviceActionsGen1()
  logDebug("Gen 1 Actions: ${prettyJson(actions)}...")
  actions.each{ k,v ->
    v.each{ m ->
      Integer index = ((Map)m)?.index as Integer
      String queryString = "index=${index}&enabled=false".toString()
      queryString += "&name=${k}".toString()
      Boolean hasTimes = actionHasEnabledTimes(k,v)
      if(hasTimes) {
        queryString += "&urls[0][url]=".toString()
        queryString += "&urls[0][int]=".toString()
      } else {
        queryString += "&urls[]=".toString()
      }
      sendGen1Command('settings/actions', queryString)
    }
  }
}

@CompileStatic
void createHubitatWebhookGen1(Integer index, String name, Boolean hasEnabledTimes, String additionalParams = '') {
  logDebug("Creating Gen 1 Actions: ${name}_${index}")
  String queryString = "index=${index}&enabled=true&name=${name}".toString()
  name = name.replace('_url','')
  if(hasEnabledTimes) {
    queryString += "&urls[0][url]=${getHubBaseUri()}/${name}/${index}".toString()
    queryString += "&urls[0][int]=0000-0000".toString()
  } else {
    queryString += "&urls[]=${getHubBaseUri()}/${name}/${index}".toString()
  }
  if(additionalParams != null && additionalParams != '') {
    queryString += additionalParams
  }
  sendGen1Command('settings/actions', queryString)
}

@CompileStatic
void deleteHubitatWebhookGen1(Integer index, String name, Boolean hasEnabledTimes) {
  logDebug("Deleting Gen 1 Actions: ${name}_${index}, if it exists...")
  String queryString = "index=${index}&enabled=false&name=${name}".toString()
  if(hasEnabledTimes) {
    queryString += "&urls[0][url]=".toString()
    queryString += "&urls[0][int]=".toString()
  } else {
    queryString += "&urls[]=".toString()
  }
  sendGen1Command('settings/actions', queryString)
}

LinkedHashMap decodeLanMessage(String message) {
  return parseLanMessage(message)
}
/* #endregion */
/* #region Bluetooth */
@CompileStatic
void enableBluReportingToHE() {
  enableBluetooth()
  LinkedHashMap s = getBleShellyBluId()
  if(s == null) {
    logDebug('HubitatBLEHelper script not found on device, creating script...')
    postCommandSync(scriptCreateCommand())
    s = getBleShellyBluId()
  }
  Integer id = s?.id as Integer
  if(id != null) {
    postCommandSync(scriptStopCommand(id))
    logDebug('Getting latest Shelly Bluetooth Helper script...')
    String js = getBleShellyBluJs()
    logDebug('Sending latest Shelly Bluetooth Helper to device...')
    postCommandSync(scriptPutCodeCommand(id, js, false))
    logDebug('Enabling Shelly Bluetooth HelperShelly Bluetooth Helper on device...')
    postCommandSync(scriptEnableCommand(id))
    logDebug('Starting Shelly Bluetooth Helper on device...')
    postCommandSync(scriptStartCommand(id))
    logDebug('Validating sucessful installation of Shelly Bluetooth Helper...')
    s = getBleShellyBluId()
    logDebug("Bluetooth Helper is ${s?.name == 'HubitatBLEHelper' ? 'installed' : 'not installed'}, ${s?.enable ? 'enabled' : 'disabled'}, and ${s?.running ? 'running' : 'not running'}")
    if(s?.name == 'HubitatBLEHelper' && s?.enable && s?.running) {
      logDebug('Sucessfully installed Shelly Bluetooth Helper on device...')
    } else {
      logWarn('Shelly Bluetooth Helper was not sucessfully installed on device!')
    }
  }
}

@CompileStatic
void disableBluReportingToHE() {
  LinkedHashMap s = getBleShellyBluId()
  Integer id = s?.id as Integer
  if(id != null) {
    logDebug('Removing HubitatBLEHelper from Shelly device...')
    postCommandSync(scriptDeleteCommand(id))
    logDebug('Disabling BLE Observer...')
    postCommandSync(bleSetConfigCommand(true, true, false))
  }
}

@CompileStatic
LinkedHashMap getBleShellyBluId() {
  logDebug('Getting index of HubitatBLEHelper script, if it exists on Shelly device...')
  LinkedHashMap json = postCommandSync(scriptListCommand())
  List<LinkedHashMap> scripts = (List<LinkedHashMap>)((LinkedHashMap)json?.result)?.scripts
  scripts.each{logDebug("Script found: ${prettyJson(it)}")}
  return scripts.find{it?.name == 'HubitatBLEHelper'}
}

String getBleShellyBluJs() {
  Map params = [uri: BLE_SHELLY_BLU]
  params.contentType = 'text/plain'
  params.requestContentType = 'text/plain'
  params.textParser = true
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) {
      StringWriter sw = new StringWriter()
      ((StringReader)resp.data).transferTo(sw);
      return sw.toString()
    }
    else { logError(resp.data) }
  }
}

void enableBluetooth() {
  logDebug('Enabling Bluetooth on Shelly device...')
  postCommandSync(bleSetConfigCommand(true, true, true))
}
/* #endregion */
/* #region Child Devices */
@CompileStatic
ChildDeviceWrapper createChildSwitch(Integer id, String additionalId = null) {
  String dni = additionalId == null ? "${getThisDeviceDNI()}-switch${id}" : "${getThisDeviceDNI()}-${additionalId}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = additionalId == null ? 'Shelly Switch Component' : 'Shelly OverUnder Switch Component'
    String labelText = thisDevice().getLabel() != null ? "${thisDevice().getLabel()}" : "${driverName}"
    String label = additionalId == null ? "${labelText} - Switch ${id}" : "${labelText} - ${additionalId} - Switch ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('switchId',"${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildDimmer(Integer id) {
  String dni =  "${getThisDeviceDNI()}-dimmer${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly Dimmer Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - Dimmer ${id}" : "${driverName} - Dimmer ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('switchLevelId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildRGB(Integer id) {
  String dni =  "${getThisDeviceDNI()}-rgb${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly RGB Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - RGB ${id}" : "${driverName} - RGB ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('rgbId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildRGBW(Integer id) {
  String dni =  "${getThisDeviceDNI()}-rgbw${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly RGBW Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - RGBW ${id}" : "${driverName} - RGBW ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('rgbwId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildPmSwitch(Integer id) {
  String dni =  "${getThisDeviceDNI()}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly Switch PM Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - Switch ${id}" : "${driverName} - Switch ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('switchId',"${id}")
  child.updateDataValue('hasPM','true')
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildEM(Integer id, String phase) {
  String dni =  "${getThisDeviceDNI()}-em${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly EM Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - EM${id} - phase ${phase}" : "${driverName} - EM${id} - phase ${phase}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  child.updateDataValue('apparentPowerId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('returnedEnergyId', "${id}")
  child.updateDataValue('emData', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildEM1(Integer id) {
  String dni =  "${getThisDeviceDNI()}-em${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly EM Component'
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - EM ${id}" : "${driverName} - EM ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  child.updateDataValue('apparentPowerId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('returnedEnergyId', "${id}")
  child.updateDataValue('em1Data', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildInput(Integer id, String inputType) {
  logDebug("Input type is: ${inputType}")
  String driverName = "Shelly Input ${inputType} Component"
  String dni = "${getThisDeviceDNI()}-input${inputType}${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - Input ${inputType} ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("input${inputType}Id","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
void removeChildInput(Integer id, String inputType) {
  String dni = "${getThisDeviceDNI()}-input${inputType}${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if(child != null) { deleteChildByDNI(dni) }
}

@CompileStatic
ChildDeviceWrapper createChildCover(Integer id, String driverName = 'Shelly Cover Component') {
  String dni = "${getThisDeviceDNI()}-cover${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - Cover ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("coverId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildPmCover(Integer id) {
  ChildDeviceWrapper child = createChildCover(id, 'Shelly Cover PM Component')
  child.updateDataValue('hasPM','true')
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildTemperature(Integer id) {
  String driverName = "Shelly Temperature Peripheral Component"
  String dni = "${getThisDeviceDNI()}-temperature${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - Temperature ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("temperatureId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildHumidity(Integer id) {
  String driverName = "Shelly Humidity Peripheral Component"
  String dni = "${getThisDeviceDNI()}-humidity${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - Temperature ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("humidityId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildTemperatureHumidity(Integer id) {
  String driverName = "Shelly Temperature & Humidity Peripheral Component"
  String dni = "${getThisDeviceDNI()}-temperatureHumidity${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - Temperature & Humidity${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("temperatureId","${id}")
      child.updateDataValue("humidityId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildVoltage(Integer id) {
  String driverName = "Shelly Polling Voltage Sensor Component"
  String dni = "${getThisDeviceDNI()}-adc${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${thisDevice().getLabel()} - ADC ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('adcId',"${id}")
      child.updateDataValue('polling','true')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

ChildDeviceWrapper addShellyDevice(String driverName, String dni, Map props) {
  return addChildDevice('ShellyUSA', driverName, dni, props)
}

ChildDeviceWrapper getShellyDevice(String dni) {return getChildDevice(dni)}

@CompileStatic
ChildDeviceWrapper getVoltageChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'adcId') == id}
}

@CompileStatic
ChildDeviceWrapper getFrequencyChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'frequencyId') == id}
}

@CompileStatic
ChildDeviceWrapper getApparentPowerChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'apparentPowerId') == id}
}

@CompileStatic
ChildDeviceWrapper getCurrentChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'currentId') == id}
}

@CompileStatic
ChildDeviceWrapper getReturnedEnergyChildById(Integer id) {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'returnedEnergyId') == id}
}

@CompileStatic
ChildDeviceWrapper getEnergyChildById(Integer id) {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'energyId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getEnergyChildren() {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasAttribute(it,'energy')}
}

@CompileStatic
List<ChildDeviceWrapper> getCoverChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'coverId')}
}

@CompileStatic
ChildDeviceWrapper getCoverChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'coverId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getValveChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'valveId')}
}

@CompileStatic
ChildDeviceWrapper getValveChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'valveId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getEnergySwitchChildren() {
  List<ChildDeviceWrapper> switchChildren = getSwitchChildren()
  return switchChildren.findAll{childHasAttribute(it,'energy')}
}

@CompileStatic
ChildDeviceWrapper getEnergySwitchChildById(Integer id) {
  List<ChildDeviceWrapper> energySwitchChildren = getEnergySwitchChildren()
  return energySwitchChildren.find{getChildDeviceIntegerDataValue(it,'switchId') == id}
}

@CompileStatic
Integer getSwitchChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'switchId')}.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'switchId')}.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getSwitchLevelChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'switchLevelId')}
}

@CompileStatic
ChildDeviceWrapper getSwitchLevelChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'switchLevelId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'switchId')}
}

@CompileStatic
ChildDeviceWrapper getSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'switchId') == id}
}

@CompileStatic
Integer getInputSwitchChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}?.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}?.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getInputSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getInputSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputSwitchId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getInputCountChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputCountId')}
}

@CompileStatic
ChildDeviceWrapper getInputCountChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputCountId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getInputAnalogChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputAnalogId')}
}

@CompileStatic
ChildDeviceWrapper getInputAnalogChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputAnalogId') == id}
}

@CompileStatic
Boolean hasInputButtonChildren() { return getInputButtonChildren().size() > 0 }

@CompileStatic
Integer getInputButtonChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputButtonId')}?.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputButtonId')}?.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getInputButtonChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputButtonId')}
}

@CompileStatic
ChildDeviceWrapper getInputButtonChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputButtonId') == id}
}

@CompileStatic
Boolean hasTemperatureChildren() { return getTemperatureChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getTemperatureChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'temperatureId')}
}

ChildDeviceWrapper getTemperatureChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'temperatureId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getTemperatureSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'temperatureSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getTemperatureSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'temperatureSwitchId') == id}
}

@CompileStatic
Boolean hasHumidityChildren() { return getHumidityChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getHumidityChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'humidityId')}
}

@CompileStatic
ChildDeviceWrapper getHumidityChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'humidityId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getHumiditySwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'humiditySwitchId')}
}

@CompileStatic
ChildDeviceWrapper getHumiditySwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'humiditySwitchId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getLightChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'lightId')}
}

@CompileStatic
ChildDeviceWrapper getLightChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'lightId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getRGBChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'rgbId')}
}

@CompileStatic
ChildDeviceWrapper getRGBChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'rgbId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getRGBWChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'rgbwId')}
}

@CompileStatic
ChildDeviceWrapper getRGBWChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'rgbwId') == id}
}

@CompileStatic
Boolean hasAdcChildren() { return getAdcChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getAdcChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'adcId')}
}

@CompileStatic
ChildDeviceWrapper getAdcChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'adcId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getAdcSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'adcSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getAdcSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'adcSwitchId') == id}
}
/* #endregion */
/* #region HTTP Methods */
LinkedHashMap postCommandSync(LinkedHashMap command) {
  LinkedHashMap json
  Map params = [uri: "${getBaseUriRpc()}"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  logTrace("postCommandSync sending: ${prettyJson(params)}")
  try {
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    setAuthIsEnabled(false)
  } catch(HttpResponseException ex) {
    if(ex.getStatusCode() != 401) {logWarn("Exception: ${ex}")}
    setAuthIsEnabled(true)
    String authToProcess = ex.getResponse().getAllHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    try {
      if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
      params.body = command
      httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    } catch(HttpResponseException ex2) {
      logError('Auth failed a second time. Double check password correctness.')
    }
  }
  logTrace("postCommandSync returned: ${prettyJson(json)}")
  return json
}

LinkedHashMap parentPostCommandSync(LinkedHashMap command) {
  if(hasParent() == true) { return parent?.postCommandSync(command) }
  else { return postCommandSync(command) }
}

void parentPostCommandAsync(LinkedHashMap command, String callbackMethod = '') {
  if(hasParent() == true) { parent?.postCommandAsync(command, callbackMethod) }
  else { postCommandAsync(command, callbackMethod) }
}

void postCommandAsync(LinkedHashMap command, String callbackMethod = '') {
  LinkedHashMap json
  Map params = [uri: "${getBaseUriRpc()}"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  logTrace("postCommandAsync sending: ${prettyJson(params)}")
  asynchttpPost('postCommandAsyncCallback', params, [params:params, command:command, attempt:1, callbackMethod:callbackMethod])
  setAuthIsEnabled(false)
}

void postCommandAsyncCallback(AsyncResponse response, Map data = null) {
  logTrace("postCommandAsyncCallback has data: ${data}")
  if (response?.status == 401 && response?.getErrorMessage() == 'Unauthorized') {
    Map params = data.params
    Map command = data.command
    setAuthIsEnabled(true)
    // logWarn("Error headers: ${response?.getHeaders()}")
    String authToProcess = response?.getHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    if(authIsEnabled() == true && getAuth().size() > 0) {
      command.auth = getAuth()
      params.body = command
    }
    if(data?.attempt == 1) {
      asynchttpPost('postCommandAsyncCallback', params, [params:params, command:command, attempt:2, callbackMethod:data?.callbackMethod])
    } else {
      logError('Auth failed a second time. Double check password correctness.')
    }
  } else if(response?.status == 200) {
    String followOnCallback = data?.callbackMethod
    if(followOnCallback != null && followOnCallback != '') {
      logTrace("Follow On Callback: ${followOnCallback}")
      "${followOnCallback}"(response, data)
    }
  }
}

LinkedHashMap postSync(LinkedHashMap command) {
  LinkedHashMap json
  Map params = [uri: "${getBaseUriRpc()}"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  logTrace("postCommandSync sending: ${prettyJson(params)}")
  try {
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    setAuthIsEnabled(false)
  } catch(HttpResponseException ex) {
    logWarn("Exception: ${ex}")
    setAuthIsEnabled(true)
    String authToProcess = ex.getResponse().getAllHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    try {
      if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
      httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    } catch(HttpResponseException ex2) {
      logError('Auth failed a second time. Double check password correctness.')
    }
  }
  logTrace("postCommandSync returned: ${prettyJson(json)}")
  return json
}

void jsonAsyncGet(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpGet(callbackMethod, params, data)
}

void jsonAsyncPost(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpPost(callbackMethod, params, data)
}

LinkedHashMap jsonSyncGet(Map params) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data as LinkedHashMap }
    else { logError(resp.data) }
  }
}

@CompileStatic
Boolean responseIsValid(AsyncResponse response) {
  if (response?.status != 200 || response.hasError()) {
    if((hasCapabilityBattery() || hasCapabilityBatteryGen1()) && response.status == 408 ) {
      logInfo("Request returned HTTP status:${response.status}, error message: ${response.getErrorMessage()}")
      logInfo('This is due to the device being asleep. If you are attempting to add/configure a device, ensure it is awake and connected to WiFi before trying again...')
    } else if(response.status == 500 && response.getErrorData() == 'Conditions not correct!') {
      logInfo('Attempted to open valve while already open or attempted to close valve while already closed. Running refresh to pull in correct current state.')
      refresh()
    } else {
      logError("Request returned HTTP status ${response.status}")
      logError("Request error message: ${response.getErrorMessage()}")
      try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorData()' method
      try{logError("Request ErrorJson: ${prettyJson(response.getErrorJson() as LinkedHashMap)}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorJson()' method
    }
  }
  if (response.hasError()) { return false } else { return true }
}

@CompileStatic
void sendShellyCommand(String command, String queryParams = null, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = queryParams ? "${getBaseUri()}/${command}${queryParams}".toString() : "${getBaseUri()}/${command}".toString()
  logTrace("sendShellyCommand: ${params}")
  jsonAsyncGet(callbackMethod, params, data)
}

@CompileStatic
void sendShellyJsonCommand(String command, Map json, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = "${getBaseUri()}/${command}".toString()
  params.body = json
  logTrace("sendShellyJsonCommand: ${params}")
  jsonAsyncPost(callbackMethod, params, data)
}

@CompileStatic
void shellyCommandCallback(AsyncResponse response, Map data = null) {
  if(!responseIsValid(response)) {return}
  logJson(response.getJson() as LinkedHashMap)
}

LinkedHashMap sendGen1Command(String command, String queryString = null) {
  LinkedHashMap json
  LinkedHashMap errorJson
  LinkedHashMap params = [:]
  if(queryString != null && queryString != '') {
    params.uri = "${getBaseUri()}/${command}?${queryString}".toString()
  } else {
    params.uri = "${getBaseUri()}/${command}".toString()
  }
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  if(authIsEnabledGen1() == true) {
    params.headers = ['Authorization': "Basic ${getBasicAuthHeader()}"]
  }
  logTrace("sendGen1Command sending: ${prettyJson(params)}")
  try{
    httpGet(params) { resp ->
      if(resp.getStatus() == 200) {
        json = resp.getData()
      }
    }
  } catch (ex) { logWarn(ex) }
  return json
}

void sendGen1CommandAsync(String command, String queryString = null, String callbackMethod = 'getStatusGen1Callback') {
  LinkedHashMap params = [:]
  if(queryString != null && queryString != '') {
    params.uri = "${getBaseUri()}/${command}?${queryString}".toString()
  } else {
    params.uri = "${getBaseUri()}/${command}".toString()
  }
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  if(authIsEnabledGen1() == true) {
    params.headers = ['Authorization': "Basic ${getBasicAuthHeader()}"]
  }
  logTrace("sendGen1CommandAsync sending: ${prettyJson(params)}")
  asynchttpGet(callbackMethod, params)
}

void parentSendGen1CommandAsync(String command, String queryString = null, String callbackMethod = 'getStatusGen1Callback') {
  if(hasParent() == true) {parent.sendGen1CommandAsync(command, queryString, callbackMethod)}
  else {sendGen1CommandAsync(command, queryString, callbackMethod)}
}

LinkedHashMap<String, List> getDeviceActionsGen1() {
  LinkedHashMap json
  String command = 'settings/actions'
  LinkedHashMap params = [uri: "${getBaseUri()}/${command}".toString()]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  if(authIsEnabledGen1() == true) {
    params.headers = ['Authorization': "Basic ${getBasicAuthHeader()}"]
  }
  httpGet(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
  if(json?.actions != null) {json = json.actions}
  return json
}
/* #endregion */
/* #region Websocket Connection */
void webSocketStatus(String message) {
  logTrace("Incoming Socket Status message: ${message}")

  // Handle different failure types with specific status codes
  if(message == 'status: open') {
    setWebsocketStatus('open')
  } else if(message == 'failure: null') {
    setWebsocketStatus('closed', 'connection lost (null)')
  } else if(message == 'failure: Connection reset') {
    setWebsocketStatus('closed', 'connection reset by peer')
  } else if(message == 'failure: connect timed out') {
    setWebsocketStatus('connect timed out', 'connection attempt timed out')
  } else if(message.startsWith('failure: ')) {
    String reason = message.replace('failure: ', '')
    setWebsocketStatus('closed', reason)
  } else {
    logWarn("Unexpected websocket status message: ${message}")
    setWebsocketStatus('unknown', message)
  }
}

void wsConnect() {
  // Prevent concurrent connection attempts
  if(atomicState.connectionInProgress == true) {
    logDebug('Connection attempt already in progress, skipping duplicate request')
    return
  }

  String uri = getWebSocketUri()
  if(uri != null && uri != '') {
    atomicState.connectionInProgress = true
    atomicState.lastConnectionAttempt = now()

    try {
      logDebug("Attempting websocket connection to ${uri}")
      interfaces.webSocket.connect(uri, headers: [:], ignoreSSLIssues: true)
      unschedule('checkWebsocketConnection')
      runEveryCustomSeconds(WS_CONNECT_INTERVAL, 'checkWebsocketConnection')
    } catch(Exception e) {
      logError("Exception during websocket connection attempt: ${e}")
      atomicState.connectionInProgress = false
      setWebsocketStatus('failed', "exception: ${e.message}")
    }
  } else {
    logWarn('Cannot connect websocket: invalid or empty URI')
  }
}

void sendWsMessage(String message) {
  if(getWebsocketIsConnected() == false) {
    logDebug('Websocket not connected, attempting to connect before sending message')
    wsConnect()
    // Queue message to send after connection establishes
    atomicState.pendingWsMessage = message
    return
  }

  try {
    logTrace("Sending json message via websocket: ${message}")
    interfaces.webSocket.sendMessage(message)
    atomicState.remove('pendingWsMessage')
  } catch(Exception e) {
    logError("Failed to send websocket message: ${e}")
    setWebsocketStatus('failed', "send failed: ${e.message}")
    // Store message for retry after reconnection
    atomicState.pendingWsMessage = message
  }
}

void parentSendWsMessage(String message) {
  if(hasParent() == true) {
    parent?.sendWsMessage(message)
  } else {
    sendWsMessage(message)
  }
}

@CompileStatic
void parentSendWsCommand(LinkedHashMap command) {
  String json = JsonOutput.toJson(command)
  parentSendWsMessage(json)
}

void initializeWebsocketConnection() {
  wsConnect()
}

void initializeWebsocketConnectionIfNeeded() {
  // Reset reconnection state when explicitly initializing
  atomicState.remove('reconnectTimer')
  atomicState.remove('reconnectAttempt')
  atomicState.remove('consecutiveFailures')
  atomicState.remove('connectionInProgress')

  if(wsShouldBeConnected() == true) {
    initializeWebsocketConnection()
    runIn(2, 'checkWebsocketConnection')
  } else {
    if(isGen1Device() == false) {
      wsClose()
      setDeviceActionsGen2()
    }
  }
}

@CompileStatic
void checkWebsocketConnection() {
  logDebug('Sending connectivityCheck websocket command...')
  shellyGetDeviceInfoWs(false, 'connectivityCheck')
}

void connectWebsocketAfterDelay(Integer delay = 15) {
  runIn(delay, 'initializeWebsocketConnectionIfNeeded', [overwrite: true])
}

void wsClose() { interfaces.webSocket.close() }

void setWebsocketStatus(String status, String reason = null) {
  String prevStatus = getDeviceDataValue('websocketStatus')
  String logMsg = "Websocket Status: ${status}"
  if(reason) { logMsg += " (${reason})" }

  if(status != 'open') {
    atomicState.connectionInProgress = false

    if(wsShouldBeConnected() == true) {
      // Track consecutive failures for monitoring
      Integer failures = (atomicState.consecutiveFailures ?: 0) as Integer
      atomicState.consecutiveFailures = failures + 1

      Integer delay = getReconnectDelay()

      // Log at appropriate level based on delay/severity
      if(delay >= 30) {
        logError("${logMsg} - Reconnect attempt #${atomicState.consecutiveFailures} scheduled in ${delay} seconds")
      } else {
        logDebug("${logMsg} - Reconnect attempt #${atomicState.consecutiveFailures} scheduled in ${delay} seconds")
      }

      scheduleReconnect(delay)
    } else {
      logDebug(logMsg)
    }
  }

  if(status == 'open') {
    atomicState.connectionInProgress = false
    Long connectionTime = now()
    Long lastAttempt = atomicState.lastConnectionAttempt ?: connectionTime
    Long connectDuration = connectionTime - lastAttempt

    // Reset reconnection tracking on successful connection
    Integer previousFailures = atomicState.consecutiveFailures ?: 0
    atomicState.consecutiveFailures = 0
    atomicState.remove('reconnectAttempt')
    atomicState.lastSuccessfulConnection = connectionTime
    atomicState.connectionStartTime = connectionTime

    // Log successful connection with timing info
    if(previousFailures > 0) {
      logDebug("Websocket connection established after ${previousFailures} failed attempts (${connectDuration}ms to connect)")
    } else {
      logDebug("Websocket connection established (${connectDuration}ms to connect)")
    }

    // Cancel any pending reconnection attempts
    unschedule('initializeWebsocketConnection')
    unschedule('initializeWebsocketConnectionIfNeeded')
    atomicState.remove('initInProgress')

    // Send any pending messages
    if(atomicState.pendingWsMessage) {
      String pendingMsg = atomicState.pendingWsMessage
      atomicState.remove('pendingWsMessage')
      runIn(1, 'sendPendingWsMessage', [data: [message: pendingMsg]])
    }

    // Perform authentication check
    runIn(1, 'performAuthCheck')
  }

  setDeviceDataValue('websocketStatus', status)
  if(prevStatus != status) {
    setDeviceDataValue('websocketStatusChanged', new Date().toString())
  }
}

void sendPendingWsMessage(Map data) {
  if(data?.message) {
    logDebug('Sending previously queued websocket message')
    sendWsMessage(data.message as String)
  }
}

Integer getReconnectDelay() {
  Integer attempt = (atomicState.reconnectAttempt ?: 0) as Integer
  Integer delay

  if(attempt < WS_RECONNECT_BACKOFF_SEQUENCE.size()) {
    delay = WS_RECONNECT_BACKOFF_SEQUENCE[attempt]
  } else {
    delay = WS_MAX_BACKOFF
  }

  // Increment attempt counter for next time
  atomicState.reconnectAttempt = attempt + 1

  return delay
}

void scheduleReconnect(Integer delaySeconds) {
  // Cancel any existing reconnection attempts to avoid duplicates
  unschedule('initializeWebsocketConnectionIfNeeded')
  unschedule('initializeWebsocketConnection')

  // Schedule the reconnection attempt
  runIn(delaySeconds, 'initializeWebsocketConnectionIfNeeded', [overwrite: true])
}

@CompileStatic
Boolean getWebsocketIsConnected() { return getDeviceDataValue('websocketStatus') == 'open' }

void logWebsocketConnectionStats() {
  if(wsShouldBeConnected() == false) { return }

  String status = getDeviceDataValue('websocketStatus') ?: 'unknown'
  Integer consecutiveFailures = atomicState.consecutiveFailures ?: 0
  Long lastSuccess = atomicState.lastSuccessfulConnection
  Long connectionStart = atomicState.connectionStartTime

  StringBuilder stats = new StringBuilder('Daily Websocket Connection Stats: ')
  stats.append("Status=${status}")

  if(status == 'open' && connectionStart) {
    Long uptime = now() - connectionStart
    Long uptimeMinutes = uptime / 60000
    Long uptimeHours = uptimeMinutes / 60
    stats.append(", Uptime=${uptimeHours}h ${uptimeMinutes % 60}m")
  }

  if(lastSuccess) {
    Long timeSinceSuccess = now() - lastSuccess
    Long minutesSince = timeSinceSuccess / 60000
    stats.append(", Last Success=${minutesSince} minutes ago")
  }

  if(consecutiveFailures > 0) {
    stats.append(", Consecutive Failures=${consecutiveFailures}")
  }

  logDebug(stats.toString())
}
/* #endregion */
/* #region Authentication */
@CompileStatic
void processUnauthorizedMessage(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  setAuthMap(json)
}

@CompileStatic
String getPassword() { return getDeviceSettings().devicePassword as String }
LinkedHashMap getAuth() {
  LinkedHashMap authMap = getAuthMap()
  if(authMap == null || authMap.size() == 0) {return [:]}
  String realm = authMap['realm']
  String ha1 = "admin:${realm}:${getPassword()}".toString()
  Long nonce = Long.valueOf(authMap['nonce'].toString())
  String nc = (authMap['nc']).toString()
  Long cnonce = now()
  String ha2 = '6370ec69915103833b5222b368555393393f098bfbfbb59f47e0590af135f062'
  ha1 = sha256(ha1)
  String response = ha1 + ':' + nonce.toString() + ':' + nc + ':' + cnonce.toString() + ':' + 'auth'  + ':' + ha2
  response = sha256(response)
  String algorithm = authMap['algorithm'].toString()
  return [
    'realm':realm,
    'username':'admin',
    'nonce':nonce,
    'cnonce':cnonce,
    'response':response,
    'algorithm':algorithm
  ]
}

@CompileStatic
String sha256(String base) {
  MessageDigest digest = getMessageDigest()
  byte[] hash = digest.digest(base.getBytes("UTF-8"))
  StringBuffer hexString = new StringBuffer()
  for (int i = 0; i < hash.length; i++) {
    String hex = Integer.toHexString(0xff & hash[i])
    if(hex.length() == 1) hexString.append('0')
    hexString.append(hex);
  }
  return hexString.toString()
}

@CompileStatic
MessageDigest getMessageDigest() {
  if(messageDigests == null) { messageDigests = new ConcurrentHashMap<String, MessageDigest>() }
  if(!messageDigests.containsKey(getThisDeviceDNI())) { messageDigests[getThisDeviceDNI()] = MessageDigest.getInstance("SHA-256") }
  return messageDigests[getThisDeviceDNI()]
}

@CompileStatic
LinkedHashMap getAuthMap() {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!authMaps.containsKey(getThisDeviceDNI())) { authMaps[getThisDeviceDNI()] = [:] }
  return authMaps[getThisDeviceDNI()]
}
@CompileStatic
void setAuthMap(LinkedHashMap map) {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  logTrace("Device authentication detected, setting authmap to ${map}")
  authMaps[getThisDeviceDNI()] = map
}

@CompileStatic
Boolean authIsEnabled() {
  return thisDevice().getDataValue('auth') == 'true'
}
@CompileStatic
void setAuthIsEnabled(Boolean auth) {
  thisDevice().updateDataValue('auth', auth.toString())
}

@CompileStatic
Boolean authIsEnabledGen1() {
  Boolean authEnabled = (
    getDeviceSettings()?.deviceUsername != null &&
    getDeviceSettings()?.devicePassword != null &&
    getDeviceSettings()?.deviceUsername != '' &&
    getDeviceSettings()?.devicePassword != ''
  )
  setAuthIsEnabled(authEnabled)
  return authEnabled
}

@CompileStatic
void performAuthCheck() { shellyGetStatusWs('authCheck') }

@CompileStatic
String getBasicAuthHeader() {
  if(getDeviceSettings()?.deviceUsername != null && getDeviceSettings()?.devicePassword != null) {
    return base64Encode("${getDeviceSettings().deviceUsername}:${getDeviceSettings().devicePassword}".toString())
  }
}
/* #endregion */
/* #region Logging Helpers */
String loggingLabel() {
  if(device) {return "${device.label ?: device.name }"}
  if(app) {return "${app.label ?: app.name }"}
}

void logException(message) {log.error "${loggingLabel()}: ${message}"}
void logError(message) {log.error "${loggingLabel()}: ${message}"}
void logWarn(message) {log.warn "${loggingLabel()}: ${message}"}
void logInfo(message) {if (settings.logEnable == true) {log.info "${loggingLabel()}: ${message}"}}
void logDebug(message) {if (settings.logEnable == true && settings.debugLogEnable) {log.debug "${loggingLabel()}: ${message}"}}
void logTrace(message) {if (settings.logEnable == true && settings.traceLogEnable) {log.trace "${loggingLabel()}: ${message}"}}

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

void logJson(Map message) {
  if (settings.logEnable && settings.traceLogEnable) {
    String prettyJson = prettyJson(message)
    logTrace(prettyJson)
  }
}

@CompileStatic
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

@CompileStatic
void logInfoJson(Map message) {
  String prettyJson = prettyJson(message)
  logInfo(prettyJson)
}

void logsOff() {
  if (device) {
    logWarn("Logging disabled for ${device}")
    device.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Logging disabled for ${app}")
    app.updateSetting('logEnable', [value: 'false', type: 'bool'] )
  }
}

void debugLogsOff() {
  if (device) {
    logWarn("Debug logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Debug logging disabled for ${app}")
    app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}

void traceLogsOff() {
  if (device) {
    logWarn("Trace logging disabled for ${device}")
    device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
  if (app) {
    logWarn("Trace logging disabled for ${app}")
    app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'] )
  }
}
/* #endregion */
/* #region Formatters, Custom 'Run Every', and other helpers */
@CompileStatic
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

String nowFormatted() {
  if(location.timeZone) {return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone)}
  else {return new Date().format('yyyy-MMM-dd h:mm:ss a')}
}

@CompileStatic
String runEveryCustomSecondsCronString(Integer seconds) {
  String currentSecond = new Date().format('ss')
  return "/${seconds} * * ? * * *"
}

@CompileStatic
String runEveryCustomMinutesCronString(Integer minutes) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  return "${currentSecond} /${minutes} * ? * * *"
}

@CompileStatic
String runEveryCustomHoursCronString(Integer hours) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  String currentHour = new Date().format('H')
  return "${currentSecond} * /${hours} ? * * *"
}

void runEveryCustomSeconds(Integer seconds, String methodToRun) {
  if(seconds < 60) {
    schedule(runEveryCustomSecondsCronString(seconds as Integer), methodToRun)
  }
  if(seconds >= 60 && seconds < 3600) {
    String cron = runEveryCustomMinutesCronString((seconds/60) as Integer)
    schedule(cron, methodToRun)
  }
  if(seconds == 3600) {
    schedule(runEveryCustomHoursCronString((seconds/3600) as Integer), methodToRun)
  }
}

void runInRandomSeconds(String methodToRun, Integer seconds = 90) {
  if(seconds < 0 || seconds > 240) {
    logWarn('Seconds must be between 0 and 240')
  } else {
    Long r = new Long(new Random().nextInt(seconds))
    runIn(r as Long, methodToRun)
  }
}

void runInSeconds(String methodToRun, Integer seconds = 3) {
  if(seconds < 0 || seconds > 240) {
    logWarn('Seconds must be between 0 and 240')
  } else {
    runIn(seconds as Long, methodToRun)
  }
}

double nowDays() { return (now() / 86400000) }

long unixTimeMillis() { return (now()) }

@CompileStatic
Integer convertHexToInt(String hex) { Integer.parseInt(hex,16) }

@CompileStatic
String convertHexToIP(String hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

@CompileStatic
String convertIPToHex(String ipAddress) {
  List parts = ipAddress.tokenize('.')
  return String.format("%02X%02X%02X%02X", parts[0] as Integer, parts[1] as Integer, parts[2] as Integer, parts[3] as Integer)
}

@CompileStatic
BigDecimal wattMinuteToKWh(BigDecimal watts) {
  return (watts/60/1000).setScale(2, BigDecimal.ROUND_HALF_UP)
}

void clearAllStates() {
  state.clear()
  if (device) device.getCurrentStates().each { device.deleteCurrentState(it.name) }
}

@CompileStatic
void deleteChildDevices() {
  ArrayList<ChildDeviceWrapper> children = getThisDeviceChildren()
  children.each { child -> deleteChildByDNI(getChildDeviceNetworkId(child)) }
}



void deleteChildByDNI(String dni) {
  deleteChildDevice(dni)
}

BigDecimal cToF(BigDecimal val) { return celsiusToFahrenheit(val) }

BigDecimal fToC(BigDecimal val) { return fahrenheitToCelsius(val) }

@CompileStatic
Integer boundedLevel(Integer level, Integer min = 0, Integer max = 100) {
  if(level == null) {return null}
  return (Math.min(Math.max(level, min), max) as Integer)
}

@CompileStatic
String base64Encode(String toEncode) { return toEncode.bytes.encodeBase64().toString() }
/* #endregion */
/* #region Imports */
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovyx.net.http.HttpResponseException
import hubitat.device.HubResponse
import hubitat.scheduling.AsyncResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.StringReader
import java.io.StringWriter
/* #endregion */
/* #region Installed, Updated, Uninstalled */
void installed() {
  logDebug('Installed...')
  try {
    initialize()
  } catch(e) {
    logWarn("No initialize() method defined or initialize() resulted in error: ${e}")
  }

  if (settings.logEnable == true) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable == true) { runIn(1800, 'debugLogsOff') }
  if (settings.traceLogEnable == true) { runIn(1800, 'traceLogsOff') }
}

void uninstalled() {
  logDebug('Uninstalled...')
  unschedule()
  deleteChildDevices()
}

void updated() {
  logDebug('Device preferences saved, running configure()...')
  try { configure() }
  catch(e) {
    if(e.toString().startsWith('java.net.NoRouteToHostException') || e.toString().startsWith('org.apache.http.conn.ConnectTimeoutException')) {
      logWarn('Could not initialize/configure device. Device could not be contacted. Please check IP address and/or password (if auth enabled). If device is battery powered, ensure device is awake immediately prior to clicking on "Initialize" or "Save Preferences".')
    } else {
      if(e.toString().contains('A device with the same device network ID exists, Please use a different DNI')) {
        logWarn('Another device has already been configured using the same IP address. Please verify correct IP Address is being used.')
      } else { logWarn("No configure() method defined or configure() resulted in error: ${e}") }
    }
  }
}

@CompileStatic
Boolean notRecentlyBooted() {return uptime() > 60}

BigInteger uptime() {
  logTrace("Uptime: ${getLocation().getHub().uptime}s")
  return getLocation().getHub().uptime
}
/* #endregion */