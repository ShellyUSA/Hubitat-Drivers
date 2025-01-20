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
@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()
@Field static LinkedHashMap<String, LinkedHashMap> preferenceMap = [
  'switch_initial_state': [type: 'enum', title: 'State after power outage', options: ['off':'Power Off', 'on':'Power On', 'restore_last':'Previous State', 'match_input':'Match Input']],
  'switch_auto_off': [type: 'bool', title: 'Auto-ON: after turning ON, turn OFF after a predefined time (in seconds)'],
  'switch_auto_off_delay': [type:'number', title: 'Auto-ON Delay: delay before turning OFF'],
  'switch_auto_on': [type: 'bool', title: 'Auto-OFF: after turning OFF, turn ON after a predefined time (in seconds)'],
  'switch_auto_on_delay': [type:'number', title: 'Auto-OFF Delay: delay before turning ON'],
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
Boolean hasExtTempGen1() { return HAS_EXT_TEMP_GEN1 == true }
Boolean hasExtHumGen1() { return HAS_EXT_HUM_GEN1 == true }

Boolean hasActionsToCreateList() { return ACTIONS_TO_CREATE != null }
List<String> getActionsToCreate() {
  if(hasActionsToCreateList() == true) { return ACTIONS_TO_CREATE }
  else {return []}
}

Boolean deviceIsComponent() {return COMP == true}

Boolean hasCapabilityBattery() { return device.hasCapability('Battery') == true }
Boolean hasCapabilitySwitch() { return device.hasCapability('Switch') == true }
Boolean hasCapabilityPresence() { return device.hasCapability('PresenceSensor') == true }
Boolean hasCapabilityValve() { return device.hasCapability('Valve') == true }

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
        if(v.type == 'enum') {
          input(name: k, required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: k, required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      } else if(deviceIsSwitch() && thisDeviceHasSetting(k.replace('switch_',''))) {
        if(v.type == 'enum') {
          input(name: k.replace('switch_',''), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: k.replace('switch_',''), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      } else if(deviceIsCover() && thisDeviceHasSetting(k.replace('cover_',''))) {
        if(v.type == 'enum') {
          input(name: k.replace('cover_',''), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue, options: v.options)
        } else {
          input(name: k.replace('cover_',''), required: v?.required ? v.required : false, title: v.title, type: v.type, defaultValue: v.defaultValue)
        }
      }
    }

    if(thisDeviceOrChildrenHasPowerMonitoring() == true) {
      input(name: 'enablePowerMonitoring', type:'bool', title: 'Enable Power Monitoring', required: false, defaultValue: true)
      input(name: 'resetMonitorsAtMidnight', type:'bool', title: 'Reset Total Energy At Midnight', required: false, defaultValue: true)
    }

    if(hasChildSwitches() == true) {
      input(name: 'parentSwitchStateMode', type: 'enum', title: 'Parent Switch State Mode', options: ['allOn':'On when all children on', 'anyOn':'On when any child on'])
    }

    if(hasBluGateway() == true && deviceIsComponent() == false) {
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

@CompileStatic
void refresh() {
  if(isGen1Device() == true) {
    logTrace('Refreshing status for gen1 device')
    refreshStatusGen1()
  } else {
    if(hasParent() == true) {
      // Switch refresh
      Integer switchId = getIntegerDeviceDataValue('switchId')
      if(switchId != null) {
        LinkedHashMap response = parentPostCommandSync(switchGetStatusCommand(switchId))
        processWebsocketMessagesPowerMonitoring(response)
      }
    } else {
      List<ChildDeviceWrapper> switchChildren = getSwitchChildren()
      switchChildren.each{child ->
        logDebug("Refreshing switch child...")
        Integer switchId = getChildDeviceIntegerDataValue(child, 'switchId')
        logDebug("Got child with switchId of ${switchId}")
        LinkedHashMap response = parentPostCommandSync(switchGetStatusCommand(switchId as Integer))
        processWebsocketMessagesPowerMonitoring(response)
      }
      List<ChildDeviceWrapper> inputSwitchChildren = getInputSwitchChildren()
      inputSwitchChildren.each{child ->
        logDebug("Refreshing input switch child...")
        Integer inputSwitchId = getChildDeviceIntegerDataValue(child, 'inputSwitchId')
        logDebug("Got child with switchId of ${inputSwitchId}")
        LinkedHashMap response = parentPostCommandSync(inputGetStatusCommand(inputSwitchId as Integer))
        processWebsocketMessagesPowerMonitoring(response)
      }
    }
  }
  tryRefreshDeviceSpecificInfo()
}

void tryRefreshDeviceSpecificInfo() {try{refreshDeviceSpecificInfo()} catch(ex) {}}

@CompileStatic
void getOrSetPrefs() {
  if(getDeviceDataValue('ipAddress') == null || getDeviceDataValue('ipAddress') != getIpAddress()) {
    logDebug('Detected newly added/changed IP address, getting preferences from device...')
    getPreferencesFromShellyDevice()
    refresh()
  } else if(getDeviceDataValue('ipAddress') == getIpAddress()) {
    logDebug('Device IP address not changed, sending preferences to device...')
    sendPreferencesToShellyDevice()
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

      logDebug("Found Switches: ${switches}")
      logDebug("Found Inputs: ${inputs}")
      logDebug("Found Covers: ${covers}")
      logDebug("Found Temperatures: ${temps}")
      logDebug("Found Humidites: ${hums}")

      if(switches?.size() > 0) {
        logDebug('Multiple switches found, running Switch.GetConfig for each...')
        switches.each{ swi ->
          Integer id = swi.tokenize(':')[1] as Integer
          logDebug("Running Switch.GetConfig for switch ID: ${id}")
          Map switchGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand(id))?.result
          if(switchGetConfigResult != null && switchGetConfigResult.size() > 0 && hasNoChildrenNeeded() == false) {
            logDebug('Creating child device for switch...')
            logDebug("Switch.GetConfig Result: ${prettyJson(switchGetConfigResult)}")
            Map<String, Object> switchStatus = postCommandSync(switchGetStatusCommand(id))
            logDebug("Switch Status: ${prettyJson(switchStatus)}")
            Map<String, Object> switchStatusResult = (LinkedHashMap<String, Object>)switchStatus?.result
            Boolean hasPM = ('apower' in switchStatusResult.keySet())
            ChildDeviceWrapper child = null
            if(hasPM == true) {
              child = createChildPmSwitch(id)
            } else {
              child = createChildSwitch(id)
            }
            if(child != null) {setChildDevicePreferences(switchGetConfigResult, child)}
          }
        }
      } else {
        logDebug('No switches found...')
      }

      if(inputs?.size() > 1) {
        logDebug('Multiple inputs found, running Input.GetConfig for each...')
        inputs?.each{ inp ->
          Integer id = inp.tokenize(':')[1] as Integer
          logDebug("Input ID: ${id}")
          Map inputGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(inputGetConfigCommand(id))?.result
          logDebug("Input.GetConfig Result: ${prettyJson(inputGetConfigResult)}")
          logDebug('Creating child device for input...')
          LinkedHashMap inputConfig = (LinkedHashMap)shellyGetConfigResult[inp]
          String inputType = (inputConfig?.type as String).capitalize()
          ChildDeviceWrapper child = createChildInput(id, inputType)
          // Map switchGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(switchGetConfigCommand(id))?.result
          if(inputGetConfigResult != null && inputGetConfigResult.size() > 0) {setChildDevicePreferences(inputGetConfigResult, child)}
        }


      } else if(inputs?.size() == 1) {

      } else {
        logDebug('No inputs found...')
      }

      if(covers?.size() > 0) {
        logDebug('Cover(s) found, running Cover.GetConfig for each...')
        covers?.each{ cov ->
          Integer id = cov.tokenize(':')[1] as Integer
          logDebug("Cover ID: ${id}")
          Map coverGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(coverGetConfigCommand(id))?.result
          logDebug("Cover.GetConfig Result: ${prettyJson(coverGetConfigResult)}")
          logDebug('Creating child device for cover...')
          ChildDeviceWrapper child = createChildCover(id)
          if(coverGetConfigResult != null && coverGetConfigResult.size() > 0) {setChildDevicePreferences(coverGetConfigResult, child)}
        }

      } else if(covers?.size() == 1) {

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
  LinkedHashMap prefs = [:]
  LinkedHashMap motion = (LinkedHashMap)gen1SettingsResponse?.motion
  if(motion != null) {
    prefs['gen1_motion_sensitivity'] = motion?.sensitivity as Integer
    prefs['gen1_motion_blind_time_minutes'] = motion?.blind_time_minutes as Integer
  }
  if(gen1SettingsResponse?.tamper_sensitivity != null) {
    prefs['gen1_tamper_sensitivity'] = gen1SettingsResponse?.tamper_sensitivity as Integer
  }
  if(gen1SettingsResponse?.set_volume != null) {prefs['gen1_set_volume'] = gen1SettingsResponse?.set_volume as Integer}
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
  if(relays != null && relays.size() > 0) {
    relays.eachWithIndex{ it, index ->
      createChildSwitch(index)
      if(((LinkedHashMap)it)?.btn_type in ['momentary', 'momentary_on_release', 'detached']) {
        createChildInput(index, "Button")
      } else  {
        removeChildInput(index, "Button")
      }
      createChildInput(index, "Switch")
    }
  }

  if(prefs.size() > 0) { setHubitatDevicePreferences(prefs) }
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
    setPower(0 as BigDecimal)
    setEnergy(0 as BigDecimal)
    unscheduleTask('switchResetCounters')
    if(wsShouldBeConnected() == false) {
      logDebug('Websocket connection no longer required, removing any WS connection checks and closing connection...')
      unscheduleTask('checkWebsocketConnection')
      wsClose()
    }
  }
}

/* #endregion */
/* #region Initialization */
@CompileStatic
void initialize() {
  if(hasIpAddress() == true) {
    runInRandomSeconds('getPreferencesFromShellyDevice')
  }
  if(thisDeviceOrChildrenHasPowerMonitoring() == true) {
    if(getDeviceSettings().enablePowerMonitoring == null) { setDeviceSetting('enablePowerMonitoring', true) }
    if(getDeviceSettings().resetMonitorsAtMidnight == null) { setDeviceSetting('resetMonitorsAtMidnight', true) }
  }else {
    removeDeviceSetting('enablePowerMonitoring')
    removeDeviceSetting('resetMonitorsAtMidnight')
  }
  if(hasParent() == false && isGen1Device() == false && hasIpAddress() == true) {
    LinkedHashMap shellyGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(shellyGetConfigCommand())?.result
    LinkedHashMap ble = (LinkedHashMap<String, Object>)shellyGetConfigResult?.ble
    Boolean hasBlu = ble?.observer != null
    if(hasBlu == true) { setDeviceDataValue('hasBluGateway', 'true') }
    if(hasBlu == true && getDeviceSettings().enableBluetoothGateway == null) {
      setDeviceSetting('enableBluetoothGateway', true)
    } else { removeDeviceSetting('enableBluetoothGateway') }
  }


  if(hasChildSwitches() == true) {
    if(getDeviceSettings().parentSwitchStateMode == null) { setDeviceSetting('parentSwitchStateMode', 'anyOn') }
  } else { removeDeviceSetting('parentSwitchStateMode') }

  if(hasIpAddress() == true) {initializeWebsocketConnectionIfNeeded()}

  if(hasPollingChildren() == true) {
    if(getDeviceSettings().gen1_status_polling_rate == null) { setDeviceSetting('gen1_status_polling_rate', 60) }
    runEveryCustomSeconds(getDeviceSettings().gen1_status_polling_rate as Integer, 'refresh')
  }
}

/* #endregion */
/* #region Configuration */
@CompileStatic
void configure() {
  if(hasParent() == false && isBlu() == false) {
    logDebug('Starting configuration for a non-child device...')
    String ipAddress = getIpAddress()
    if (ipAddress != null && ipAddress != '' && ipAddress ==~ /^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$/) {
      logDebug('Device has an IP address set in preferences, updating DNI if needed...')
      setIpAddress(ipAddress)
    } else {
      logDebug('Could not set device network ID because device settings does not have a valid IP address set.')
    }
    getOrSetPrefs()

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
    LinkedHashMap shellyGetConfigResult = (LinkedHashMap<String, Object>)parentPostCommandSync(shellyGetConfigCommand())?.result
    LinkedHashMap ble = (LinkedHashMap<String, Object>)shellyGetConfigResult?.ble
    Boolean hasBlu = ble?.observer != null
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
  ArrayList<BigDecimal> a = amperageAvgs(id)
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(a.size() == 0) {
    if(c != null) { c.sendEvent(name: 'amperage', value: value) }
    else { thisDevice().sendEvent(name: 'amperage', value: value) }
  }
  a.add(value)
  if(a.size() >= 10) {
    value = (((BigDecimal)a.sum()) / 10)
    value = value.setScale(1, BigDecimal.ROUND_HALF_UP)
    if(value == -1) {
      if(c != null) { c.sendEvent(name: 'amperage', value: null) }
      else { thisDevice().sendEvent(name: 'amperage', value: null) }
    }
    else if(value != null && value != getCurrent(id)) {
      if(c != null) { c.sendEvent(name: 'amperage', value: value) }
      else { thisDevice().sendEvent(name: 'amperage', value: value) }
    }
    a.removeAt(0)
  }
}
@CompileStatic
BigDecimal getCurrent(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('amperage', true) as BigDecimal }
  else { return thisDevice().currentValue('amperage', true) as BigDecimal }
}

@CompileStatic
void setPower(BigDecimal value, Integer id = 0) {
  ArrayList<BigDecimal> p = powerAvgs()
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(p.size() == 0) {
    if(c != null) { c.sendEvent(name: 'power', value: value) }
    else { thisDevice().sendEvent(name: 'power', value: value) }
  }
  p.add(value)
  if(p.size() >= 10) {
    value = (((BigDecimal)p.sum()) / 10)
    value = value.setScale(0, BigDecimal.ROUND_HALF_UP)
    if(value == -1) {
      if(c != null) { c.sendEvent(name: 'power', value: null) }
      else { thisDevice().sendEvent(name: 'power', value: null) }
    }
    else if(value != null && value != getPower()) {
      if(c != null) { c.sendEvent(name: 'power', value: value) }
      else { thisDevice().sendEvent(name: 'power', value: value) }
    }
    p.removeAt(0)
  }
}
@CompileStatic
BigDecimal getPower(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('power', true) as BigDecimal }
  else { return thisDevice().currentValue('power', true) as BigDecimal }
}



@CompileStatic
void setVoltage(BigDecimal value, Integer id = 0) {
  if(id == 100) { thisDevice().sendEvent(name: 'voltage', value: value) }
  if(hasADCGen1() == true) {
    ChildDeviceWrapper c = getVoltageChildById(id)
    if(c != null) { c.sendEvent(name: 'voltage', value: value) }
  }
  else {
    ArrayList<BigDecimal> v = voltageAvgs()
    ChildDeviceWrapper c = getSwitchChildById(id)
    if(v.size() == 0) {
      if(c != null) { c.sendEvent(name: 'voltage', value: value) }
      else { thisDevice().sendEvent(name: 'voltage', value: value) }
    }
    v.add(value)
    if(v.size() >= 10) {
      value = (((BigDecimal)v.sum()) / 10)
      value = value.setScale(0, BigDecimal.ROUND_HALF_UP)
      if(value == -1) {
        if(c != null) { c.sendEvent(name: 'voltage', value: null) }
        else { thisDevice().sendEvent(name: 'voltage', value: null) }
      }
      else if(value != null && value != getPower()) {
        if(c != null) { c.sendEvent(name: 'voltage', value: value) }
        else { thisDevice().sendEvent(name: 'voltage', value: value) }
      }
      v.removeAt(0)
    }
  }
}
@CompileStatic
BigDecimal getVoltage(Integer id = 0) {
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(c != null) { return getSwitchChildById(id).currentValue('voltage', true) as BigDecimal }
  else { return thisDevice().currentValue('voltage', true) as BigDecimal }
}

@CompileStatic
void setEnergy(BigDecimal value, Integer id = 0) {
  value = value.setScale(2, BigDecimal.ROUND_HALF_UP)
  ChildDeviceWrapper c = getSwitchChildById(id)
  if(value == -1) {
    if(c != null) { c.sendEvent(name: 'energy', value: null) }
    else { thisDevice().sendEvent(name: 'energy', value: null) }
  }
  else if(value != null && value != getEnergy(id)) {
    if(c != null) { c.sendEvent(name: 'energy', value: value) }
    else { thisDevice().sendEvent(name: 'energy', value: value) }
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
    id = getIntegerDeviceDataValue('switchId')
    switchResetCounters(id, "resetEnergyMonitor-switch${id}")
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    allChildren.each{child ->
      id = getChildDeviceIntegerDataValue(child, 'switchId')
      switchResetCounters(id, "resetEnergyMonitor-switch${id}")
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
  Event lastEvent = thisDevice().events([max: 1])[0]
  if(((unixTimeMillis() - lastEvent.getUnixTime()) / 1000) > (getDeviceSettings().presenceTimeout as Integer)) {
    sendDeviceEvent([name: 'presence', value: 'not present', isStateChange: false])
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
  sendDeviceEvent([name: 'illuminance', value: illuminance])
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

@CompileStatic
void setValvePosition(Boolean open, Integer valve = 0) {
  if(open == true) {
    sendDeviceEvent([name: 'valve', value: 'open'])
  } else {
    sendDeviceEvent([name: 'valve', value: 'closed'])
  }
}
/* #endregion */
/* #region Generic Getters and Setters */

DeviceWrapper thisDevice() { return this.device }
ArrayList<ChildDeviceWrapper> getThisDeviceChildren() { return getChildDevices() }

LinkedHashMap getDeviceSettings() { return this.settings }
LinkedHashMap getParentDeviceSettings() { return this.parent?.settings }
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
void sendChildDeviceEvent(Map properties, ChildDeviceWrapper child) {child.sendEvent(properties)}

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

@CompileStatic
Boolean deviceIsSwitch(DeviceWrapper dev) {return deviceHasDataValue('switchId', dev)}

@CompileStatic
Boolean deviceIsCover(DeviceWrapper dev) {return deviceHasDataValue('coverId', dev)}

@CompileStatic
Boolean deviceIsInputSwitch(DeviceWrapper dev) {return deviceHasDataValue('inputSwitchId', dev)}

@CompileStatic
Boolean deviceIsInputCount(DeviceWrapper dev) {return deviceHasDataValue('inputCountId', dev)}

@CompileStatic
Boolean deviceIsInputButton(DeviceWrapper dev) {return deviceHasDataValue('inputButtonId', dev)}

@CompileStatic
Boolean deviceIsInputAnalog(DeviceWrapper dev) {return deviceHasDataValue('inputAnalogId', dev)}

@CompileStatic
Boolean deviceIsInput(DeviceWrapper dev) {return deviceIsInputSwitch(dev) || deviceIsInputCount(dev) || deviceIsInputButton(dev) || deviceIsInputAnalog(dev)}

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
    return (anyChildHasDataValue('hasPM') || getDeviceDataValue('hasPM') == 'true')
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
          logWarn("V is ${v}")
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
void setValveState(String position, Integer id = 0) {
  if(position in ['open','closed']) {
    List<ChildDeviceWrapper> children = getValveChildren()
    if(children != null && children.size() > 0) {
      getValveChildById(id)?.sendEvent(name: 'valve', value: position)
    } else {
      thisDevice().sendEvent(name: 'valve', value: position)
    }
  }
}

@CompileStatic
void setSwitchState(Boolean on, Integer id = 0) {
  if(on != null) {
    List<ChildDeviceWrapper> children = getSwitchChildren()
    if(children != null && children.size() > 0) {
      getSwitchChildById(id)?.sendEvent(name: 'switch', value: on ? 'on' : 'off')
      //Create map of child states and set entry for this event in map.
      //Avoids race conditions from setting child state then immediately trying to retrieve it before it has a chance to settle.
      Map childStates = children.collectEntries{child -> [child.getDataValue('switchId') as Integer, child.currentValue('switch')] }
      childStates[id] = on ? 'on' : 'off'
      Boolean anyOn = childStates.any{k,v -> v == 'on'}
      Boolean allOn = childStates.every{k,v -> v == 'on'}
      String parentSwitchStateMode = getDeviceSettings().parentSwitchStateMode
      if(parentSwitchStateMode == 'anyOn') { thisDevice().sendEvent(name: 'switch', value: anyOn ? 'on' : 'off') }
      if(parentSwitchStateMode == 'allOn') { thisDevice().sendEvent(name: 'switch', value: allOn ? 'on' : 'off') }
    } else {
      thisDevice().sendEvent(name: 'switch', value: on ? 'on' : 'off')
    }
  }
}

@CompileStatic
void setGen1AdcSwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
    ChildDeviceWrapper child = getAdcSwitchChildById(id)
    if(child != null) { child.sendEvent(name: 'switch', value: value) }
  }
}

@CompileStatic
void setGen1TemperatureSwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
    ChildDeviceWrapper child = getTemperatureSwitchChildById(id)
    if(child != null) {
      child.sendEvent(name: 'switch', value: value)
    }
  }
}

@CompileStatic
void setGen1HumiditySwitchState(String value, Integer id) {
  if(value in ['on','off'] && id != null) {
    ChildDeviceWrapper child = getHumiditySwitchChildById(id)
    if(child != null) { child.sendEvent(name: 'switch', value: value) }
  }
}

@CompileStatic
Boolean getSwitchState() {
  return thisDevice().currentValue('switch', true) == 'on'
}

@CompileStatic
void componentSwitchOn() {
  if(isGen1Device() == true) {
    parentSendGen1CommandAsync("/relay/${getDeviceDataValue('switchId')}/?turn=on")
  } else {
    parentPostCommandAsync(switchSetCommand(true, getIntegerDeviceDataValue('switchId')))
  }
}

@CompileStatic
void componentSwitchOff() {
  if(isGen1Device() == true) {
    parentSendGen1CommandAsync("/relay/${getDeviceDataValue('switchId')}/?turn=off")
  } else {
    parentPostCommandAsync(switchSetCommand(false, getIntegerDeviceDataValue('switchId')))
  }
}

@CompileStatic
void setInputSwitchState(Boolean on, Integer id = 0) {
  if(on != null) {
    List<ChildDeviceWrapper> children = getInputSwitchChildren()
    if(children != null && children.size() > 0) {
      getInputSwitchChildById(id)?.sendEvent(name: 'switch', value: on ? 'on' : 'off')
    }
  }
}

@CompileStatic
void setInputCountState(Integer count, Integer id = 0) {
  logDebug("Sending count: ${count}")
  if(count != null) {
    List<ChildDeviceWrapper> children = getInputCountChildren()
    if(children != null && children.size() > 0) {
      getInputCountChildById(id)?.sendEvent(name: 'count', value: count)
    }
  }
}

@CompileStatic
void setLastUpdated() {
  thisDevice().sendEvent(name: 'lastUpdated', value: nowFormatted())
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
    "method" : "Cover.Close",
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
LinkedHashMap pm1GetConfigCommand(String src = 'pm1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1SetConfigCommand(Integer pm1Id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "pm1SetConfig",
    "method" : "PM1.SetConfig",
    "params" : [
      "id" : pm1Id,
      "config": []
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetStatusCommand(String src = 'pm1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetStatus",
    "params" : ["id" : 0]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1ResetCountersCommand(String src = 'pm1ResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.ResetCounters",
    "params" : ["id" : 0]
  ]
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

  try {processWebsocketMessagesAuth(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesAuth(): ${prettyJson(json)}")}

  try {processWebsocketMessagesPowerMonitoring(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesPowerMonitoring(): ${prettyJson(json)}")}

  try {processWebsocketMessagesConnectivity(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesConnectivity(): ${prettyJson(json)}")}

  try {processWebsocketMessagesBluetoothEvents(json)}
  catch(e) {logWarn("Encountered an issue ${e} with processWebsocketMessagesBluetoothEvents(): ${prettyJson(json)}")}
}

@CompileStatic
void processWebsocketMessagesConnectivity(LinkedHashMap json) {
  if(((String)json?.dst).startsWith('connectivityCheck-') && json?.result != null) {
    logTrace("Incoming WS JSON: ${json}")
    Long checkStarted = Long.valueOf(((String)json?.dst).split('-')[1])
    logDebug("Connectivity check started ${checkStarted}")
    if(checkStarted != null) {
      long seconds = unixTimeSeconds() - checkStarted
      if(seconds < 5) { setWebsocketStatus('open') }
      else { setWebsocketStatus('connection timed out') }
    } else { setWebsocketStatus('connection timed out') }
    if(((LinkedHashMap)json.result)?.auth_en != null) {
      setAuthIsEnabled((Boolean)(((LinkedHashMap)json.result)?.auth_en))
      shellyGetStatusWs('authCheck')
    }
  }
}

@CompileStatic
void processWebsocketMessagesAuth(LinkedHashMap json) {
  if(json?.error != null ) {
    logInfo(prettyJson(json))
    LinkedHashMap error = (LinkedHashMap)json.error
    if(error?.message != null && error?.code == 401) {
      processUnauthorizedMessage(error.message as String)
    }
  }
}

@CompileStatic
void processWebsocketMessagesPowerMonitoring(LinkedHashMap json, Integer id = 0) {
  // logDebug("Processing PM message...")

  String dst = json?.dst
  if(dst != null && dst != '') {
    try{
      if(json?.result != null && json?.result != '' && dst == 'switchGetStatus') {
        logWarn("Res: ${json?.result}")
        LinkedHashMap res = (LinkedHashMap)json.result
        id = res?.id as Integer
        if(res?.output != null && res?.output != '') {
          Boolean switchState = res.output as Boolean
          if(switchState != null) { setSwitchState(switchState, id) }
        }
      if(getDeviceSettings().enablePowerMonitoring != null && getDeviceSettings().enablePowerMonitoring == true) {
          if(res?.current != null && res?.current != '') {
            BigDecimal current =  (BigDecimal)res.current
            if(current != null) { setCurrent(current, id) }
          }

          if(res?.apower != null && res?.apower != '') {
            BigDecimal apower =  (BigDecimal)res.apower
            if(apower != null) { setPower(apower, id) }
          }

          if(res?.voltage != null && res?.voltage != '') {
            BigDecimal voltage =  (BigDecimal)res.voltage
            if(voltage != null) { setVoltage(voltage, id) }
          }

          if(res?.aenergy != null && res?.aenergy != '') {
            BigDecimal aenergy =  (BigDecimal)((LinkedHashMap)(res?.aenergy))?.total
            if(aenergy != null) { setEnergy(aenergy/1000, id) }
          }
        }
      }
    } catch(ex) {logWarn("Exception processing incoming switchGetStatus websocket message: ${ex}")}


    try{
      // logWarn("Res: ${json?.result}")
      if(json?.result != null && json?.result != '' && dst == 'inputGetStatus') {
        LinkedHashMap res = (LinkedHashMap)json.result
        id = res?.id as Integer
        if(res?.state != null && res?.state != '') {
          Boolean inputSwitchState = res.state as Boolean
          if(inputSwitchState != null) { setInputSwitchState(inputSwitchState, id) }
        }
      }
    } catch(ex) {logWarn("Exception processing incoming inputGetStatus websocket message: ${ex}")}

      // Process incoming messages for NotifyStatus
      try{
        if(json?.method == 'NotifyStatus' && json?.params != null && json?.params != '') {
          LinkedHashMap params = (LinkedHashMap<String, Object>)json.params
          if(params != null && params.any{k,v -> k.startsWith('switch')}) {
            String swName = params.keySet().find{it.startsWith('switch')}
            if(swName != null && swName != '') {
              id = swName.split(':')[1] as Integer
              LinkedHashMap sw = (LinkedHashMap)params[swName]

              if(sw?.output != null && sw?.output != '') {
                Boolean switchState = sw.output as Boolean
                if(switchState != null) { setSwitchState(switchState, id) }
              }

              if(sw?.current != null && sw?.current != '') {
                BigDecimal current =  (BigDecimal)sw.current
                if(current != null) { setCurrent(current, id) }
              }

              if(sw?.apower != null && sw?.apower != '') {
                BigDecimal apower =  (BigDecimal)sw.apower
                if(apower != null) { setPower(apower, id) }
              }

              if(sw?.aenergy != null && sw?.aenergy != '') {
                LinkedHashMap aenergyMap = (LinkedHashMap)sw?.aenergy
                if(aenergyMap?.total != null && aenergyMap?.total != '') {
                  BigDecimal aenergy =  (BigDecimal)aenergyMap?.total
                  if(aenergy != null) { setEnergy(aenergy/1000, id) }
                }
              }
            }
          } else if(params != null && params.any{k,v -> k.startsWith('input')}) {
            String inputName = params.keySet().find{it.startsWith('input')}
            logTrace("Processing input WS message for ${inputName}")
            if(inputName != null && inputName != '') {
              id = inputName.split(':')[1] as Integer
              LinkedHashMap inp = (LinkedHashMap)params[inputName]

              if(inp?.state != null && inp?.state != '') {
                Boolean inputState = inp.state as Boolean
                if(inputState != null) { setInputSwitchState(inputState, id) }
              }
              if(inp?.counts != null && inp?.counts != '') {
                Map counts = (LinkedHashMap)inp.counts
                if(counts?.total != null && counts?.total != '') {
                  Integer cTot = counts.total as Integer
                  if(cTot != null) { setInputCountState(cTot, id) }
                }
              }
            }
          } else if(params != null && params.any{k,v -> k.startsWith('voltmeter:100')}) {
            LinkedHashMap voltmeter = (LinkedHashMap)params["voltmeter:100"]
            BigDecimal voltage = (BigDecimal)voltmeter?.voltage
            logDebug("Sending ${voltage} volts")
            setVoltage(voltage, 100)
          }
        }
      } catch(ex) {logWarn("Exception processing NotifyStatus: ${ex}")}


      // } else if (json?.result != null && json?.result != '') {
      //   LinkedHashMap res = (LinkedHashMap)json.result
      //   Boolean switchState = res?.output
      //   if(switchState != null) { setSwitchState(switchState) }
      // }
  }
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
            Integer button = evtData?.button as Integer
            if(button < 4 && button > 0) {
              sendEventToShellyBluetoothHelper("shellyBLEButtonPushedEvents", button, address)
            } else if(button == 4) {
              sendEventToShellyBluetoothHelper("shellyBLEButtonHeldEvents", 1, address)
            } else if(button == 0) {
              sendEventToShellyBluetoothHelper("shellyBLEButtonPresenceEvents", 0, address)
            }
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
        }
      }
    }
  }
}

@CompileStatic
void getStatusGen1() {
  if(hasCapabilityBatteryGen1() == true) {
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
      setBatteryPercent(percent)
    }
    if(hasCapabilityLuxGen1() == true) {
      Integer lux = ((LinkedHashMap)json?.lux)?.value as Integer
      setIlluminance(lux)
    }
    if(hasCapabilityTempGen1() == true) {
      BigDecimal temp = (BigDecimal)(((LinkedHashMap)json?.tmp)?.value)
      String tempUnits = (((LinkedHashMap)json?.tmp)?.units).toString()
      if(tempUnits == 'C') {
        setTemperatureC(temp)
      } else if(tempUnits == 'F') {
        setTemperatureF(temp)
      }
    }
    if(hasCapabilityHumGen1() == true) {
      BigDecimal hum = (BigDecimal)(((LinkedHashMap)json?.hum)?.value)
      if(hum != null){setHumidityPercent(hum)}
    }
    if(hasCapabilityFloodGen1() == true) {
      Boolean flood = (Boolean)json?.flood
      if(flood != null){setFloodOn(flood)}
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
          String position = valve?.state
          logTrace("Valve status: ${position}")
          setValveState(position.startsWith('open') ? 'open' : 'closed')
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
        thisDevice().sendEvent(name: 'selfTestState', value: self_test_state)
      }
      if(gas_sensor?.alarm_state != null && thisDevice().hasAttribute('naturalGas')) {
        String alarm_state = gas_sensor?.alarm_state.toString()
        thisDevice().sendEvent(name: 'naturalGas', value: alarm_state in ['mild','heavy'] ? 'detected' : 'clear')
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
  }
}

@CompileStatic
void getStatusGen2() {
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
  }
}

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

  else if(query[0] == 'out_on') {setSwitchState(true, query[1] as Integer)}
  else if(query[0] == 'out_off') {setSwitchState(false, query[1] as Integer)}

  else if(query[0] == 'btn_on') {setInputSwitchState(true, query[1] as Integer)}
  else if(query[0] == 'btn_off') {setInputSwitchState(false, query[1] as Integer)}

  else if(query[0] == 'humidity.change') {setHumidityPercent(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tC') {setTemperatureC(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tF') {setTemperatureF(new BigDecimal(query[2]))}

  else if(query[0] == 'adc_over') {setGen1AdcSwitchState('on', query[1] as Integer)}
  else if(query[0] == 'adc_under') {setGen1AdcSwitchState('off', query[1] as Integer)}

  else if(query[0] == 'ext_temp_over') {setGen1TemperatureSwitchState('on', query[1] as Integer)}
  else if(query[0] == 'ext_temp_under') {setGen1TemperatureSwitchState('off', query[1] as Integer)}

  else if(query[0] == 'ext_hum_over') {setGen1HumiditySwitchState('on', query[1] as Integer)}
  else if(query[0] == 'ext_hum_under') {setGen1HumiditySwitchState('off', query[1] as Integer)}

  setLastUpdated()
}

@CompileStatic
void parseGen2Message(String raw) {
  logTrace("Raw gen2Message: ${raw}")
  getStatusGen2()
  LinkedHashMap message = decodeLanMessage(raw)
  logDebug("Received incoming message: ${prettyJson(message)}")
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
  setLastUpdated()
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
  parentPostCommandSync(command)
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
  LinkedHashMap<String, List> actions = getDeviceActionsGen1()
  logDebug("Gen 1 Actions: ${prettyJson(actions)}")
  actions.each{ k,v ->
    v.each{ m ->
      Integer index = ((Map)m)?.index as Integer
      String name = "${k}".toString()
      Boolean hasEnabledTimes = actionHasEnabledTimes(v)

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
Boolean actionHasEnabledTimes(List<LinkedHashMap> action) {
  if(action != null && action?.size() > 0) {
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

    logDebug("Found Switches: ${switches}")
    logDebug("Found Inputs: ${inputs}")
    logDebug("Found Covers: ${covers}")
    logDebug("Found Temperatures: ${temps}")

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
      Boolean hasTimes = actionHasEnabledTimes(v)
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
    String label = additionalId == null ? "${thisDevice().getLabel()} - Switch ${id}" : "${thisDevice().getLabel()} - ${additionalId} - Switch ${id}"
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
ChildDeviceWrapper createChildPmSwitch(Integer id) {
  String dni =  "${getThisDeviceDNI()}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly Switch PM Component'
    String label = "${thisDevice().getLabel()} - Switch ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('switchId',"${id}")
      child.updateDataValue('hasPM','true')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
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
ChildDeviceWrapper createChildCover(Integer id) {
  String driverName = "Shelly Cover Component"
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
Boolean hasInputButtonChildren() { return getInputButtonChildren().size() > 0 }

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
    logWarn("Exception: ${ex}")
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
  if(hasParent() == true) { parent?.postCommandAsync(command) }
  else { postCommandAsync(command) }
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
    String followOnCallback = data.callbackMethod
    logTrace("Follow On Callback: ${followOnCallback}")
    "${followOnCallback}"(response, data)
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
  if(message == 'failure: null' || message == 'failure: Connection reset') {
    setWebsocketStatus('closed')
  }
  else if(message == 'failure: connect timed out') { setWebsocketStatus('connect timed out')}
  else if(message == 'status: open') {
    setWebsocketStatus('open')
  } else {
    logWarn("Websocket Status Message: ${message}")
    setWebsocketStatus('unknown')
  }
  logTrace("Incoming Socket Status message: ${message}")
}

void wsConnect() {
  String uri = getWebSocketUri()
  if(uri != null && uri != '') {
    interfaces.webSocket.connect(uri, headers: [:], ignoreSSLIssues: true)
    unschedule('checkWebsocketConnection')
    runEveryCustomSeconds(WS_CONNECT_INTERVAL, 'checkWebsocketConnection')
  }
}

void sendWsMessage(String message) {
  if(getWebsocketIsConnected() == false) { wsConnect() }
  logTrace("Sending json command: ${message}")
  interfaces.webSocket.sendMessage(message)
}

void parentSendWsMessage(String message) {
  if(hasParent() == true) {
    parent?.sendWsMessage(message)
  } else {
    sendWsMessage(message)
  }
}

void initializeWebsocketConnection() {
  wsConnect()
}

void initializeWebsocketConnectionIfNeeded() {
  atomicState.remove('reconnectTimer')
  if(wsShouldBeConnected() == true && getWebsocketIsConnected() == false) {
    initializeWebsocketConnection()
    runIn(1, 'checkWebsocketConnection')
  } else {
    wsClose()
    setDeviceActionsGen2()
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

void setWebsocketStatus(String status) {
  logDebug("Websocket Status: ${status}")
  if(status != 'open') {
    if(wsShouldBeConnected() == true) {
      Integer t = getReconnectTimer()
      logDebug("Websocket not open, attempting to reconnect in ${t} seconds...")
      connectWebsocketAfterDelay(t)
    }
  }
  if(status == 'open') {
    logDebug('Websocket connection is open, cancelling any pending reconnection attempts...')
    unschedule('initializeWebsocketConnection')
    unschedule('initializeWebsocketConnectionIfNeeded')
    atomicState.remove('initInProgress')
    atomicState.remove('reconnectTimer')
    runIn(1, 'performAuthCheck')
  }
  setDeviceDataValue('websocketStatus', status)
}

Integer getReconnectTimer() {
  Integer t = 3
  if(atomicState.reconnectTimer == null) {
    atomicState.reconnectTimer = t
  } else {
    t = atomicState.reconnectTimer as Integer
    atomicState.reconnectTimer = 2*t <= 300 ? 2*t : 300
  }
  return t
}

@CompileStatic
Boolean getWebsocketIsConnected() { return getDeviceDataValue('websocketStatus') == 'open' }
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
  logInfo("Device authentication detected, setting authmap to ${map}")
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
/* #endregion */