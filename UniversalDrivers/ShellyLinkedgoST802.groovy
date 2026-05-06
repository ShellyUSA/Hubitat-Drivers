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
 * Switch semantics: 'switch' tracks the thermostat's enable/sleep state
 * (on=enabled, off=disabled). on()/off() toggle the enable state, matching
 * the device's physical power button. The device's heat/cool/fan output
 * relays are not exposed via the Virtual Components RPC and are
 * intentionally NOT inferred here.
 *
 * thermostatOperatingState (heating / cooling / fan only / idle) IS
 * inferred from current vs. target temperature using the device's
 * temp_hysteresis dead-band, since the Hubitat Thermostat composite
 * capability requires this attribute and dashboards use it for tile
 * coloring. Polling lag means the inferred state can trail the device's
 * actual relays by up to one poll interval.
 *
 * Communication: WiFi, always-awake. Polling-only in v1 (no webhooks).
 *
 * Version: 1.1.0
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
  fetchAndDistribute()
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
  fetchAndDistribute()
}

void scheduledPoll() {
  logDebug('scheduledPoll() triggered')
  fetchAndDistribute()
}

/**
 * Owns the polling control flow: queries the device via the parent app's
 * Linkedgo-specific fetch helper, logs the response shape in device context
 * (where this driver's logs live), and dispatches to distributeStatus().
 *
 * This intentionally bypasses the parent's generic componentRefresh()
 * dispatcher — that path is designed for switch/cover/light multi-component
 * devices and obscures failures in app-context logs.
 */
private void fetchAndDistribute() {
  // Wrap the parent call in a try so a transient app-side exception (parent
  // torn down during auto-update, RPC timeout, sandbox dispatch oddity)
  // doesn't crash the scheduled poll. The poll will retry on the next
  // cron tick. componentLinkedgoFetchStatus has its own try/catch but we
  // also defend at this seam in case the issue is upstream of that wrapper
  // (e.g., Hubitat's app-method dispatch itself failing).
  Map status = null
  try {
    status = parent?.componentLinkedgoFetchStatus(device) as Map
  } catch (Exception e) {
    logWarn("fetchAndDistribute: parent call threw exception (${e.message ?: '(no message)'}); will retry on next poll")
    return
  }
  Integer keyCount = status?.size() ?: 0
  logTrace("fetchAndDistribute: parent returned ${keyCount} status keys: ${status?.keySet()}")
  if (!status) {
    logWarn('fetchAndDistribute: parent returned null/empty status — device may be unreachable')
    return
  }
  distributeStatus(status)
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

/**
 * Ensures state.virtualMap (role -> "type:id" string) is populated by querying
 * Shelly.GetComponents via the parent app. Caches written by older versions
 * of this driver stored bare Integer ids; if a legacy cache is detected it
 * is cleared so the next fetch repopulates with the type-aware "type:id"
 * format. Without this, paired Tuya components (e.g. boolean:202 enable +
 * number:202 target_temperature) would clobber each other in the reverse
 * lookup used by distributeStatus.
 */
private void ensureVirtualMap() {
  Map vm = state.virtualMap as Map
  if (vm && !vm.isEmpty() && vm.values().every { it instanceof Integer }) {
    logDebug('ensureVirtualMap: legacy Integer cache detected, clearing for re-fetch in type:id format')
    state.remove('virtualMap')
    vm = null
  }
  if (vm && !vm.isEmpty()) {
    logTrace("ensureVirtualMap: cache hit (${vm})")
    return
  }
  Map fetched = parent?.componentLinkedgoGetComponents(device) as Map
  logTrace("ensureVirtualMap: parent returned ${fetched}")
  if (fetched && !fetched.isEmpty()) {
    state.virtualMap = fetched
    logDebug("ensureVirtualMap populated: ${fetched}")
  } else {
    logTrace('ensureVirtualMap: parent returned empty map — RPC writes will be deferred')
  }
}

private void ensureServiceConfig() {
  if (state.serviceConfig) {
    logTrace("ensureServiceConfig: cache hit")
    return
  }
  Map fetched = parent?.componentLinkedgoGetServiceConfig(device) as Map
  logTrace("ensureServiceConfig: parent returned ${fetched}")
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
  logTrace("writeTargetTemperature: input=${temp}, scale=${location.temperatureScale}")
  ensureServiceConfig()
  BigDecimal tempC = (location.temperatureScale == 'F') ? ((temp - 32) * 5.0 / 9.0) : temp
  BigDecimal minC = readTempRangeMin()
  BigDecimal maxC = readTempRangeMax()
  logTrace("writeTargetTemperature: tempC=${tempC}, range=[${minC}..${maxC}]")
  if (tempC < minC) { tempC = minC }
  if (tempC > maxC) { tempC = maxC }
  tempC = tempC.setScale(1, BigDecimal.ROUND_HALF_UP)
  logTrace("writeTargetTemperature: clamped tempC=${tempC}, sending Number.Set")
  parent?.componentLinkedgoSetNumber(device, 'target_temperature', tempC)
  runIn(2, 'refresh', [overwrite: true])
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
  // setBooleanRole already schedules a refresh; runIn[overwrite:true] coalesces
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
  runIn(2, 'refresh', [overwrite: true])
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
  runIn(2, 'refresh', [overwrite: true])
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
  runIn(2, 'refresh', [overwrite: true])
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
  String compKey = vm[role] as String
  logTrace("setBooleanRole: role=${role}, value=${value}, resolved compKey=${compKey}, full virtualMap=${vm}")
  if (!compKey) {
    logTrace("setBooleanRole: no component key for '${role}' — virtualMap not yet populated; refresh and retry")
    return
  }
  String[] parts = compKey.split(':')
  if (parts.length != 2 || parts[0] != 'boolean') {
    logWarn("setBooleanRole: '${role}' resolves to ${compKey}, expected a boolean component — skipping write")
    return
  }
  Integer id = parts[1] as Integer
  parent?.componentLinkedgoSetBoolean(device, id, role, value)
  runIn(2, 'refresh', [overwrite: true])
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

/**
 * Returns the device's relay-control hysteresis in °C. Used by
 * updateOperatingState() to define the dead band around the setpoint
 * where the inferred operating state holds its previous value.
 *
 * Falls back to 1.0 °C if the service config hasn't been fetched yet or
 * the field is missing — typical HVAC default. (Floor heating uses ~0.5 °C;
 * HVAC blowers use a wider band to avoid short-cycling the compressor.)
 */
private BigDecimal readTempHysteresisC() {
  Map sc = state.serviceConfig as Map
  Object hyst = sc?.temp_hysteresis
  if (hyst instanceof Number) {
    return (hyst as BigDecimal)
  }
  return 1.0G
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

void distributeStatus(Map status) {
  try {
    if (shouldLogLevel('trace')) {
      logJson([distributeStatus_input: status])
    }
    if (!status) { return }
    ensureVirtualMap()
    ensureServiceConfig()

    Map<String, String> virtualMap = (state.virtualMap ?: [:]) as Map<String, String>
    logTrace("distributeStatus: virtualMap=${virtualMap}, serviceConfig keys=${(state.serviceConfig as Map)?.keySet()}")
    if (virtualMap.isEmpty()) {
      logTrace('distributeStatus: virtualMap empty — cannot route updates this cycle')
      sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      return
    }

    // Reverse map for "type:id" -> role lookup. Keying by the full component
    // key (not just the numeric id) is essential for Tuya-bridged devices that
    // expose paired boolean/number components sharing a numeric id.
    Map<String, String> reverseMap = [:]
    virtualMap.each { String role, Object compKey ->
      reverseMap[compKey.toString()] = role
    }
    logTrace("distributeStatus: reverseMap=${reverseMap}")

    String scale = location.temperatureScale ?: 'F'
    Boolean staleDetected = false

    status.each { k, v ->
      String key = k.toString()
      if (!(v instanceof Map)) { return }
      Map data = v as Map
      if (!key.startsWith('boolean:') && !key.startsWith('number:') && !key.startsWith('enum:')) { return }

      String role = reverseMap[key]
      logTrace("distributeStatus iter: key=${key}, resolved role=${role}, data=${data}")
      if (!role) {
        try {
          Integer idNum = key.split(':')[1] as Integer
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

    // thermostatMode (from enable + workingMode) and thermostatOperatingState
    // are derived from multiple roles, so compute them once after the
    // iteration is done. 'switch' is updated directly inside
    // handleRoleUpdate(case 'enable') and tracks the device's enable/sleep
    // state.
    deriveMode()
    updateOperatingState()

    sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
  } catch (Exception e) {
    logError("distributeStatus internal exception: ${e.message}")
    e.printStackTrace()
  }
}

private void handleRoleUpdate(String role, Map data, String scale) {
  logTrace("handleRoleUpdate: role=${role}, value=${data.value}, scale=${scale}")
  if (data.value == null) { return }

  switch (role) {
    case 'enable':
      Boolean enabled = data.value as Boolean
      // Cache in state so deriveMode() and updateOperatingState() can read
      // enable synchronously without going through device.currentValue('switch'),
      // which may not yet reflect the sendEvent below if the event is still
      // queued in the same execution pass.
      state.enabled = enabled
      sendEvent(name: 'switch', value: enabled ? 'on' : 'off',
        descriptionText: "Thermostat is ${enabled ? 'on' : 'off'}")
      // thermostatMode is derived from enable + working_mode together at the
      // end of the iteration via deriveMode(), so order-of-arrival doesn't matter.
      logInfo("Thermostat ${enabled ? 'on' : 'off'}")
      break

    case 'anti_freeze':
      String afState = (data.value as Boolean) ? 'true' : 'false'
      sendEvent(name: 'antiFreezeEnabled', value: afState,
        descriptionText: "Anti-freeze: ${afState}")
      break

    case 'current_temperature':
      // Cache raw °C for the operating-state inference (which compares
      // against target in °C using the device's hysteresis).
      state.lastCurrentTempC = data.value
      BigDecimal temp = scaleTemp(data.value as BigDecimal, scale)
      sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
        descriptionText: "Temperature is ${temp}°${scale}")
      logInfo("Temperature: ${temp}°${scale}")
      break

    case 'target_temperature':
      state.lastTargetTempC = data.value
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
 * Derives Hubitat thermostatMode from the cached enable state and the
 * latest workingMode attribute. enable=false forces 'off'; enable=true
 * maps the working_mode value (heat/cool/auto pass through; dry/fan_only
 * leave thermostatMode at its previous value).
 */
private void deriveMode() {
  Boolean enabled = state.enabled ? true : false
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
 * Computes 'thermostatOperatingState' from the cached enable state, working
 * mode, and current/target temperatures (in °C).
 *
 * The device does not expose its heat/cool relay outputs through the
 * Virtual Components RPC, so the state is inferred using the same
 * hysteresis-band logic the device firmware uses to drive the relays:
 *
 *   - enable=false  : idle
 *   - working_mode=fan_only : fan only
 *   - heat mode, current < target - hysteresis/2 : heating
 *   - cool mode, current > target + hysteresis/2 : cooling
 *   - auto mode follows whichever side of the band the room is on
 *   - within dead band : hold previous (avoids false flips on noise)
 *
 * 'switch' is NOT updated here — it tracks the device's enable/sleep state
 * directly via handleRoleUpdate(case 'enable'). This attribute exists
 * primarily to satisfy the Hubitat Thermostat composite capability
 * contract; polling lag means the inferred operating state can trail the
 * device's actual relays by up to one poll interval. The dead band reads
 * from temp_hysteresis when present and falls back to a 1.0 °C HVAC-typical
 * default when the device firmware doesn't expose it.
 */
private void updateOperatingState() {
  Boolean enabled = state.enabled ? true : false
  String mode = device.currentValue('thermostatMode')?.toString()
  String workingMode = device.currentValue('workingMode')?.toString()
  BigDecimal currentC = state.lastCurrentTempC as BigDecimal
  BigDecimal targetC = state.lastTargetTempC as BigDecimal
  String currentOp = device.currentValue('thermostatOperatingState')?.toString()

  String newOp
  if (!enabled || mode == 'off') {
    newOp = 'idle'
  } else if (workingMode == 'fan_only') {
    newOp = 'fan only'
  } else if (currentC == null || targetC == null) {
    // Temps not yet cached — hold previous (default idle).
    newOp = currentOp ?: 'idle'
  } else {
    BigDecimal halfHyst = readTempHysteresisC() / 2.0G
    BigDecimal lowerBound = targetC - halfHyst
    BigDecimal upperBound = targetC + halfHyst

    if (mode == 'heat') {
      if (currentC < lowerBound)      { newOp = 'heating' }
      else if (currentC > upperBound) { newOp = 'idle' }
      else                            { newOp = currentOp ?: 'idle' }
    } else if (mode == 'cool') {
      if (currentC > upperBound)      { newOp = 'cooling' }
      else if (currentC < lowerBound) { newOp = 'idle' }
      else                            { newOp = currentOp ?: 'idle' }
    } else if (mode == 'auto') {
      if (currentC < lowerBound)      { newOp = 'heating' }
      else if (currentC > upperBound) { newOp = 'cooling' }
      else                            { newOp = currentOp ?: 'idle' }
    } else {
      // dry / unknown — leave previous (rare path; safest default).
      newOp = currentOp ?: 'idle'
    }
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