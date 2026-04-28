/**
 * Shelly Linkedgo ST802 (Smart HVAC Thermostat)
 *
 * Standalone Hubitat driver for the LinkedGo ST802 HVAC thermostat sold under
 * the Shelly Connected partner program. The device is built on the Shelly
 * Virtual Components framework: thermostat state is exposed as numbered
 * Boolean / Number / Enum virtual instances aggregated under service:0,
 * addressed by (owner, role) for Number/Enum and instance id for Boolean.
 *
 * Roles: enable, current_temperature, target_temperature, current_humidity,
 *        target_humidity, working_mode, fan_speed, anti_freeze
 *
 * Mode mapping: working_mode values heat/cool/auto/off map directly to
 * Hubitat ThermostatMode. Non-standard values (dry, fan_only, etc.) are
 * stored in the raw `workingMode` attribute and leave thermostatMode at its
 * last mapped value. When enable=false, thermostatMode is forced to 'off'
 * regardless of working_mode.
 *
 * Fan mapping: device fan_speed (auto/low/medium/high) maps to Hubitat
 * thermostatFanMode (auto/circulate/on) by collapsing low→circulate and
 * medium/high→on. Writes from Hubitat: auto→auto, circulate→low, on→medium.
 *
 * Communication: WiFi, always-awake. Polling-only in v1 (no webhooks).
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Linkedgo ST802', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Thermostat'
    capability 'ThermostatHeatingSetpoint'
    capability 'ThermostatCoolingSetpoint'
    capability 'ThermostatMode'
    capability 'ThermostatFanMode'
    capability 'ThermostatOperatingState'
    capability 'TemperatureMeasurement'
    capability 'RelativeHumidityMeasurement'
    capability 'Switch'
    capability 'Refresh'
    capability 'Initialize'

    command 'setAntiFreeze', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable or disable anti-freeze protection']]
    command 'setWorkingMode', [[name: 'Mode', type: 'STRING', description: 'Set device working mode directly (cool, dry, heat, auto, fan_only, etc.)']]
    command 'setTargetHumidity', [[name: 'Humidity', type: 'NUMBER', description: 'Target humidity percentage (40-75)']]

    attribute 'workingMode', 'string'
    attribute 'targetHumidity', 'number'
    attribute 'antiFreezeEnabled', 'enum', ['true', 'false']
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'antiFreeze', type: 'bool',
    title: 'Anti-Freeze Mode',
    description: 'Enable freeze protection. Heating is forced on below the device anti-freeze threshold even when the thermostat is disabled.',
    defaultValue: false, required: false

  input name: 'pollInterval', type: 'number',
    title: 'Polling Interval (seconds, 0 = disabled)',
    description: 'How often to poll for status updates. Minimum 10 seconds. 0 disables polling. Default 60.',
    defaultValue: 60, range: '0..3600', required: false

  input name: 'logLevel', type: 'enum',
    title: 'Logging Level',
    options: ['trace': 'Trace', 'debug': 'Debug', 'info': 'Info', 'warn': 'Warning'],
    defaultValue: 'info', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle                                            ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
  String scale = location.temperatureScale ?: 'F'
  BigDecimal defaultSetpoint = (scale == 'F') ? 68.0 : 20.0
  // Seed default attributes
  sendEvent(name: 'thermostatMode', value: 'off', descriptionText: 'Initialized')
  sendEvent(name: 'thermostatFanMode', value: 'auto', descriptionText: 'Initialized')
  sendEvent(name: 'thermostatOperatingState', value: 'idle', descriptionText: 'Initialized')
  sendEvent(name: 'workingMode', value: '', descriptionText: 'Initialized')
  sendEvent(name: 'switch', value: 'off', descriptionText: 'Initialized')
  sendEvent(name: 'heatingSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'coolingSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'temperature', value: 0, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'humidity', value: 0, unit: '%', descriptionText: 'Initialized')
  sendEvent(name: 'targetHumidity', value: 50, unit: '%', descriptionText: 'Initialized')
  sendEvent(name: 'antiFreezeEnabled', value: 'false', descriptionText: 'Initialized')
  sendEvent(name: 'lastUpdated', value: 'Never')
  // Advertise supported modes/fan-modes for dashboards
  sendEvent(name: 'supportedThermostatModes', value: '["heat","cool","auto","off"]')
  sendEvent(name: 'supportedThermostatFanModes', value: '["auto","circulate","on"]')
  parent?.componentRefresh(device)
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  state.remove('virtualMap')
  state.remove('serviceConfig')
  relayDeviceSettings()
  initialize()
}

void initialize() {
  logDebug('initialize() called')
  schedulePolling()
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

void scheduledPoll() {
  logDebug('scheduledPoll() triggered')
  parent?.componentRefresh(device)
}

private void schedulePolling() {
  unschedule('scheduledPoll')
  Integer interval = (settings?.pollInterval ?: 60) as Integer
  if (interval > 0) {
    if (interval < 10) { interval = 10 }
    String cronExpr
    if (interval < 60) {
      cronExpr = "0/${interval} * * ? * *"
    } else {
      Integer minutes = (Integer)(interval / 60)
      cronExpr = "0 0/${minutes} * ? * *"
    }
    schedule(cronExpr, 'scheduledPoll')
    logDebug("Polling scheduled every ${interval}s")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Virtual-Component Cache                                     ║
// ╚══════════════════════════════════════════════════════════════╝

private void ensureVirtualMap() {
  Map vm = state.virtualMap as Map
  if (vm && !vm.isEmpty()) { return }
  Map fetched = parent?.componentLinkedgoGetComponents(device) as Map
  if (fetched && !fetched.isEmpty()) {
    state.virtualMap = fetched
    logDebug("ensureVirtualMap populated: ${fetched}")
  } else {
    logWarn('ensureVirtualMap: parent returned empty map — RPC writes will be deferred')
  }
}

private void ensureServiceConfig() {
  if (state.serviceConfig) { return }
  Map fetched = parent?.componentLinkedgoGetServiceConfig(device) as Map
  if (fetched) {
    state.serviceConfig = fetched
    logDebug("ensureServiceConfig populated: ${fetched}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Virtual-Component Cache                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Thermostat Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the heating setpoint. The device has a single target_temperature
 * field — both setHeatingSetpoint and setCoolingSetpoint write to it.
 * The active working_mode determines whether the device interprets the
 * value as a heating or cooling target.
 */
void setHeatingSetpoint(BigDecimal temp) {
  logDebug("setHeatingSetpoint(${temp}) called")
  writeTargetTemperature(temp)
}

/**
 * Sets the cooling setpoint. See setHeatingSetpoint — both write the same
 * device field.
 */
void setCoolingSetpoint(BigDecimal temp) {
  logDebug("setCoolingSetpoint(${temp}) called")
  writeTargetTemperature(temp)
}

private void writeTargetTemperature(BigDecimal temp) {
  ensureServiceConfig()
  BigDecimal tempC = (location.temperatureScale == 'F') ? ((temp - 32) * 5.0 / 9.0) : temp
  BigDecimal minC = readTempRangeMin()
  BigDecimal maxC = readTempRangeMax()
  if (tempC < minC) { tempC = minC }
  if (tempC > maxC) { tempC = maxC }
  tempC = tempC.setScale(1, BigDecimal.ROUND_HALF_UP)
  parent?.componentLinkedgoSetNumber(device, 'target_temperature', tempC)
}

/**
 * Sets the Hubitat ThermostatMode. Maps:
 *   off  -> Boolean.Set enable=false
 *   heat -> Enum.Set working_mode=heat then Boolean.Set enable=true
 *   cool -> Enum.Set working_mode=cool then Boolean.Set enable=true
 *   auto -> Enum.Set working_mode=auto then Boolean.Set enable=true
 * Order matters: working_mode is set first so the device doesn't briefly
 * heat/cool in the wrong mode.
 *
 * @param mode One of 'off', 'heat', 'cool', 'auto'
 */
void setThermostatMode(String mode) {
  logDebug("setThermostatMode(${mode}) called")
  if (mode == 'off') {
    setBooleanRole('enable', false)
    return
  }
  String deviceMode = mode  // 'heat', 'cool', 'auto' map 1:1 to device working_mode
  if (!(mode in ['heat', 'cool', 'auto'])) {
    logWarn("setThermostatMode: '${mode}' is not a Hubitat-standard mode; passing as-is to device working_mode")
  }
  parent?.componentLinkedgoSetEnum(device, 'working_mode', deviceMode)
  setBooleanRole('enable', true)
}

void heat() { setThermostatMode('heat') }
void cool() { setThermostatMode('cool') }
void auto() { setThermostatMode('auto') }
void off() { setThermostatMode('off') }

/**
 * Hubitat Thermostat capability includes emergencyHeat() but the device
 * has no emergency-heat concept — fall back to regular heat with a warn.
 */
void emergencyHeat() {
  logWarn('emergencyHeat() not supported by ST802; treating as heat()')
  heat()
}

/**
 * Sets the Hubitat ThermostatFanMode. Maps to device fan_speed:
 *   auto      -> auto
 *   on        -> medium
 *   circulate -> low
 * The device's 'high' speed has no Hubitat equivalent — use setWorkingMode-style
 * setFanSpeed via a custom call if needed (not exposed in v1).
 */
void setThermostatFanMode(String fanmode) {
  logDebug("setThermostatFanMode(${fanmode}) called")
  String deviceSpeed
  switch (fanmode) {
    case 'auto':      deviceSpeed = 'auto'; break
    case 'on':        deviceSpeed = 'medium'; break
    case 'circulate': deviceSpeed = 'low'; break
    default:
      logWarn("setThermostatFanMode: unmapped fan mode '${fanmode}', defaulting to auto")
      deviceSpeed = 'auto'
  }
  parent?.componentLinkedgoSetEnum(device, 'fan_speed', deviceSpeed)
}

void fanAuto() { setThermostatFanMode('auto') }
void fanCirculate() { setThermostatFanMode('circulate') }
void fanOn() { setThermostatFanMode('on') }

/**
 * Schedule support is deferred to a later version.
 */
void setSchedule(schedule) {
  logWarn("setSchedule(${schedule}) not implemented for ST802")
}

/**
 * Sets the device working_mode directly without mode mapping. Useful for
 * non-Hubitat-standard modes like 'dry' and 'fan_only'.
 *
 * @param mode Any device-supported working_mode enum value
 */
void setWorkingMode(String mode) {
  logDebug("setWorkingMode(${mode}) called")
  parent?.componentLinkedgoSetEnum(device, 'working_mode', mode)
  // Optimistic update — distributeStatus will confirm on next poll
  sendEvent(name: 'workingMode', value: mode, descriptionText: "Working mode set to ${mode}")
}

/**
 * Sets the target humidity (40-75% per device spec).
 */
void setTargetHumidity(BigDecimal rh) {
  logDebug("setTargetHumidity(${rh}) called")
  Integer value = rh.setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
  if (value < 40) { value = 40 }
  if (value > 75) { value = 75 }
  parent?.componentLinkedgoSetNumber(device, 'target_humidity', value)
}

/**
 * Switch capability: on() / off() — convenience wrappers for enable.
 */
void on() {
  logDebug('on() called')
  setBooleanRole('enable', true)
}

// off() is already declared above as part of ThermostatMode — it routes
// to setThermostatMode('off') which writes enable=false. Switch.off() and
// Thermostat.off() share semantics here.

void setAntiFreeze(String enabled) {
  logDebug("setAntiFreeze(${enabled}) called")
  Boolean value = (enabled == 'true')
  setBooleanRole('anti_freeze', value)
  device.updateSetting('antiFreeze', [type: 'bool', value: value])
}

private void relayDeviceSettings() {
  if (settings?.antiFreeze != null) {
    setBooleanRole('anti_freeze', settings.antiFreeze as Boolean)
  }
}

private void setBooleanRole(String role, Boolean value) {
  ensureVirtualMap()
  Map vm = (state.virtualMap ?: [:]) as Map
  Integer id = vm[role] as Integer
  if (id == null) {
    logWarn("setBooleanRole: no instance ID for '${role}' — virtualMap not yet populated; refresh and retry")
    return
  }
  parent?.componentLinkedgoSetBoolean(device, id, role, value)
}

private BigDecimal readTempRangeMin() {
  Map sc = state.serviceConfig as Map
  if (sc?.temp_range instanceof List && (sc.temp_range as List).size() >= 1) {
    return (sc.temp_range as List)[0] as BigDecimal
  }
  return 5.0G
}

private BigDecimal readTempRangeMax() {
  Map sc = state.serviceConfig as Map
  if (sc?.temp_range instanceof List && (sc.temp_range as List).size() >= 2) {
    return (sc.temp_range as List)[1] as BigDecimal
  }
  return 30.0G
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

void distributeStatus(Map status) {
  if (!status) { return }
  ensureVirtualMap()
  ensureServiceConfig()

  Map<String, Integer> virtualMap = (state.virtualMap ?: [:]) as Map<String, Integer>
  if (virtualMap.isEmpty()) {
    logWarn('distributeStatus: virtualMap empty — cannot route updates this cycle')
    sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    return
  }

  Map<String, String> reverseMap = [:]
  virtualMap.each { String role, Object id ->
    reverseMap[id.toString()] = role
  }

  String scale = location.temperatureScale ?: 'F'
  Boolean staleDetected = false

  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map
    if (!key.startsWith('boolean:') && !key.startsWith('number:') && !key.startsWith('enum:')) { return }

    String[] parts = key.split(':')
    if (parts.length != 2) { return }
    String idStr = parts[1]
    String role = reverseMap[idStr]
    if (!role) {
      try {
        Integer idNum = idStr as Integer
        if (idNum >= 200) { staleDetected = true }
      } catch (NumberFormatException ignored) {}
      logTrace("distributeStatus: no role mapping for ${key}; skipping")
      return
    }

    handleRoleUpdate(role, data, scale)
  }

  if (staleDetected) {
    logDebug('distributeStatus: stale virtualMap detected — clearing cache for next refresh')
    state.remove('virtualMap')
  }

  // Both thermostatMode (from enable + workingMode) and thermostatOperatingState
  // are derived from multiple roles, so compute them after iteration is done
  deriveMode()
  updateOperatingState(scale)

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

private void handleRoleUpdate(String role, Map data, String scale) {
  if (data.value == null) { return }

  switch (role) {
    case 'enable':
      Boolean enabled = data.value as Boolean
      sendEvent(name: 'switch', value: enabled ? 'on' : 'off',
        descriptionText: "Thermostat is ${enabled ? 'on' : 'off'}")
      // thermostatMode is derived from enable + working_mode together at the end
      // of the iteration via deriveMode(), so order-of-arrival doesn't matter
      logInfo("Thermostat ${enabled ? 'on' : 'off'}")
      break

    case 'anti_freeze':
      String afState = (data.value as Boolean) ? 'true' : 'false'
      sendEvent(name: 'antiFreezeEnabled', value: afState,
        descriptionText: "Anti-freeze: ${afState}")
      break

    case 'current_temperature':
      BigDecimal temp = scaleTemp(data.value as BigDecimal, scale)
      sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
        descriptionText: "Temperature is ${temp}°${scale}")
      logInfo("Temperature: ${temp}°${scale}")
      break

    case 'target_temperature':
      BigDecimal temp = scaleTemp(data.value as BigDecimal, scale)
      sendEvent(name: 'heatingSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Heating setpoint is ${temp}°${scale}")
      sendEvent(name: 'coolingSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Cooling setpoint is ${temp}°${scale}")
      sendEvent(name: 'thermostatSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Setpoint is ${temp}°${scale}")
      logInfo("Setpoint: ${temp}°${scale}")
      break

    case 'current_humidity':
      BigDecimal rh = (data.value as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
      sendEvent(name: 'humidity', value: rh.intValue(), unit: '%',
        descriptionText: "Humidity is ${rh.intValue()}%")
      break

    case 'target_humidity':
      BigDecimal rh = (data.value as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
      sendEvent(name: 'targetHumidity', value: rh.intValue(), unit: '%',
        descriptionText: "Target humidity is ${rh.intValue()}%")
      break

    case 'working_mode':
      String wm = data.value.toString()
      sendEvent(name: 'workingMode', value: wm,
        descriptionText: "Working mode: ${wm}")
      logInfo("Working mode: ${wm}")
      break

    case 'fan_speed':
      String fs = data.value.toString()
      String hubFanMode
      switch (fs) {
        case 'auto':   hubFanMode = 'auto'; break
        case 'low':    hubFanMode = 'circulate'; break
        case 'medium':
        case 'high':   hubFanMode = 'on'; break
        default:       hubFanMode = 'auto'
      }
      sendEvent(name: 'thermostatFanMode', value: hubFanMode,
        descriptionText: "Fan mode: ${hubFanMode} (device: ${fs})")
      break

    default:
      logTrace("handleRoleUpdate: unhandled role '${role}'")
  }
}

/**
 * Derives Hubitat thermostatMode from the latest switch (enable) value
 * and workingMode attribute. enable=false forces 'off'; enable=true
 * maps the working_mode value (heat/cool/auto pass through; dry/fan_only
 * leave thermostatMode at its previous value).
 */
private void deriveMode() {
  String enabledStr = device.currentValue('switch')
  Boolean enabled = (enabledStr == 'on')
  String workingMode = device.currentValue('workingMode')?.toString()
  String currentMode = device.currentValue('thermostatMode')?.toString()
  String newMode

  if (!enabled) {
    newMode = 'off'
  } else if (workingMode in ['heat', 'cool', 'auto']) {
    newMode = workingMode
  } else {
    // dry, fan_only, or unknown — leave thermostatMode unchanged
    newMode = currentMode ?: 'off'
  }

  if (newMode != currentMode) {
    sendEvent(name: 'thermostatMode', value: newMode,
      descriptionText: "Thermostat mode: ${newMode}")
  }
}

/**
 * Recomputes thermostatOperatingState from current attribute values.
 * Heuristic since the device's documented spec doesn't expose a direct
 * output / operating-state field.
 */
private void updateOperatingState(String scale) {
  String mode = device.currentValue('thermostatMode')?.toString()
  String workingMode = device.currentValue('workingMode')?.toString()
  String enabledStr = device.currentValue('switch')
  Boolean enabled = (enabledStr == 'on')
  BigDecimal temperature = device.currentValue('temperature') as BigDecimal
  BigDecimal setpoint = device.currentValue('thermostatSetpoint') as BigDecimal
  String currentOp = device.currentValue('thermostatOperatingState')?.toString()

  String newOp = 'idle'
  if (!enabled || mode == 'off') {
    newOp = 'idle'
  } else if (workingMode == 'fan_only') {
    newOp = 'fan only'
  } else if (mode == 'heat' && temperature != null && setpoint != null && temperature < setpoint) {
    newOp = 'heating'
  } else if (mode == 'cool' && temperature != null && setpoint != null && temperature > setpoint) {
    newOp = 'cooling'
  } else if (mode == 'auto' && temperature != null && setpoint != null) {
    if (temperature < setpoint) { newOp = 'heating' }
    else if (temperature > setpoint) { newOp = 'cooling' }
    else { newOp = 'idle' }
  }

  if (newOp != currentOp) {
    sendEvent(name: 'thermostatOperatingState', value: newOp,
      descriptionText: "Operating state: ${newOp}")
  }
}

private BigDecimal scaleTemp(BigDecimal tempC, String scale) {
  if (tempC == null) { return null }
  BigDecimal converted = (scale == 'F') ? (tempC * 9.0 / 5.0 + 32.0) : tempC
  return converted.setScale(1, BigDecimal.ROUND_HALF_UP)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  parse() — webhook callback handler (v2 placeholder)         ║
// ╚══════════════════════════════════════════════════════════════╝

void parse(String description) {
  if (shouldLogLevel('trace')) {
    try {
      Map msg = parseLanMessage(description)
      logTrace("parse received LAN message keys: ${msg?.keySet()}")
    } catch (Exception e) {
      logTrace("parse exception (ignored): ${e.message}")
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END parse()                                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    logTrace(JsonOutput.prettyPrint(JsonOutput.toJson(message)))
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
