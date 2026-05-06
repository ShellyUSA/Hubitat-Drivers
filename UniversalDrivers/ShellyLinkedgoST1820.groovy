/**
 * Shelly Linkedgo ST1820 (Smart Floor Heating Thermostat)
 *
 * Standalone Hubitat driver for the LinkedGo ST1820 floor-heating thermostat
 * sold under the Shelly Connected partner program. The device is built on the
 * Shelly Virtual Components framework: thermostat state is exposed as numbered
 * Boolean / Number virtual instances aggregated under service:0, addressed by
 * (owner, role) for Number and instance id for Boolean.
 *
 * Roles: enable, current_temperature, target_temperature, current_humidity,
 *        anti_freeze, child_lock
 *
 * Mode: heat-only. ThermostatMode enum: ['heat', 'off']. The device exposes
 * the full Hubitat 'Thermostat' composite capability for compatibility with
 * heating schedule apps; cool/auto/fan/emergency-heat commands are accepted
 * with a warning and treated as no-ops or aliased to heat/off as appropriate.
 *
 * Switch semantics: 'switch' tracks the thermostat's enable/sleep state
 * (on=enabled, off=disabled). on()/off() toggle the enable state, matching
 * the device's physical power button. The device's heat-output relay is not
 * exposed via the Virtual Components RPC and is intentionally NOT inferred
 * here — drivers that need PWM output from a relay-state proxy should
 * compute it externally based on temperature/setpoint deltas.
 *
 * thermostatOperatingState (heating / idle) IS inferred from current vs.
 * target temperature using the device's temp_hysteresis dead-band, since
 * the Hubitat Thermostat composite capability requires this attribute and
 * dashboards use it for tile coloring. Polling lag means the inferred
 * operating state can trail the device's actual relay by up to one poll.
 *
 * Communication: WiFi, always-awake. Polling-only in v1 (no webhooks).
 *
 * Version: 1.1.0
 */

metadata {
  definition(name: 'Shelly Linkedgo ST1820', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Thermostat'
    capability 'ThermostatHeatingSetpoint'
    capability 'ThermostatMode'
    capability 'ThermostatOperatingState'
    capability 'TemperatureMeasurement'
    capability 'RelativeHumidityMeasurement'
    capability 'Switch'
    capability 'Refresh'
    capability 'Initialize'

    command 'setAntiFreeze', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable or disable anti-freeze protection']]
    command 'setChildLock', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable or disable physical button lock']]

    attribute 'antiFreezeEnabled', 'enum', ['true', 'false']
    attribute 'childLockEnabled', 'enum', ['true', 'false']
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'antiFreeze', type: 'bool',
    title: 'Anti-Freeze Mode',
    description: 'Enable freeze protection. Heating is forced on below the device anti-freeze threshold even when the thermostat is disabled.',
    defaultValue: false, required: false

  input name: 'childLock', type: 'bool',
    title: 'Child Lock',
    description: 'Disable the physical buttons on the device to prevent accidental changes.',
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
  // Seed default attribute values so the device page doesn't show "Loading..."
  String scale = location.temperatureScale ?: 'F'
  BigDecimal defaultSetpoint = (scale == 'F') ? 68.0 : 20.0
  sendEvent(name: 'thermostatMode', value: 'off', descriptionText: 'Initialized')
  sendEvent(name: 'thermostatOperatingState', value: 'idle', descriptionText: 'Initialized')
  sendEvent(name: 'thermostatFanMode', value: 'auto', descriptionText: 'Initialized')
  sendEvent(name: 'switch', value: 'off', descriptionText: 'Initialized')
  sendEvent(name: 'heatingSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'coolingSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'thermostatSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'temperature', value: 0, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'humidity', value: 0, unit: '%', descriptionText: 'Initialized')
  sendEvent(name: 'antiFreezeEnabled', value: 'false', descriptionText: 'Initialized')
  sendEvent(name: 'childLockEnabled', value: 'false', descriptionText: 'Initialized')
  sendEvent(name: 'lastUpdated', value: 'Never')
  // Advertise supported modes for dashboards / heating-schedule apps. Only
  // 'heat' and 'off' are real modes for this device; the fan list is a stub
  // because Thermostat composite requires the attribute even though the
  // device has no fan.
  sendEvent(name: 'supportedThermostatModes', value: '["heat","off"]')
  sendEvent(name: 'supportedThermostatFanModes', value: '["auto"]')
  fetchAndDistribute()
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  // Re-fetch the virtualMap and serviceConfig in case device firmware changed
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
 * Shelly.GetComponents via the parent app. Called lazily before any RPC
 * write that needs to look up a component key by role.
 *
 * Caches written by older versions of this driver stored bare Integer ids; if
 * such a legacy cache is detected it is cleared so the next fetch repopulates
 * with the type-aware "type:id" format. Without this, paired Tuya components
 * (e.g. boolean:202 enable + number:202 target_temperature) would clobber each
 * other in the reverse lookup used by distributeStatus.
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

/**
 * Ensures state.serviceConfig (temp_offset, temp_range, etc.) is populated.
 * Used to clamp setpoints to the device-supported range.
 */
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
 * Sets the heating setpoint. Converts F to C if hub is in °F (the device
 * stores Celsius internally) and clamps to the device-supported range
 * read from state.serviceConfig (default 5..30 °C if not yet populated).
 *
 * @param temp Target temperature in the hub's configured scale
 */
void setHeatingSetpoint(BigDecimal temp) {
  logDebug("setHeatingSetpoint(${temp}) called")
  logTrace("setHeatingSetpoint: input=${temp}, scale=${location.temperatureScale}")
  ensureServiceConfig()
  BigDecimal tempC = (location.temperatureScale == 'F') ? ((temp - 32) * 5.0 / 9.0) : temp
  BigDecimal minC = readTempRangeMin()
  BigDecimal maxC = readTempRangeMax()
  logTrace("setHeatingSetpoint: tempC=${tempC}, range=[${minC}..${maxC}]")
  if (tempC < minC) { tempC = minC }
  if (tempC > maxC) { tempC = maxC }
  tempC = tempC.setScale(1, BigDecimal.ROUND_HALF_UP)
  logTrace("setHeatingSetpoint: clamped tempC=${tempC}, sending Number.Set")
  parent?.componentLinkedgoSetNumber(device, 'target_temperature', tempC)
  runIn(2, 'refresh', [overwrite: true])
}

/**
 * Sets the Hubitat ThermostatMode. Maps:
 *   off  -> Boolean.Set enable=false (puts the device into sleep mode)
 *   heat -> Boolean.Set enable=true  (wakes the device into heating mode)
 * Any other mode is logged as a warning and treated as 'heat' since this is
 * a heat-only floor-heating thermostat.
 *
 * @param mode One of 'heat' or 'off'
 */
void setThermostatMode(String mode) {
  logDebug("setThermostatMode(${mode}) called")
  if (mode == 'off') {
    setEnableState(false)
    return
  }
  if (mode != 'heat') {
    logWarn("setThermostatMode: '${mode}' not supported by ST1820 (heat-only); treating as 'heat'")
  }
  setEnableState(true)
}

void heat() { setThermostatMode('heat') }

/**
 * Switch capability. on()/off() control the thermostat's enable state
 * (wake/sleep), matching the device's physical power button. This is
 * intentionally NOT symmetric with the 'switch' attribute — the attribute
 * reflects the inferred heat-output relay state, while these commands toggle
 * whether the thermostat is awake and operating at all.
 */
void on() {
  logDebug('on() called')
  setEnableState(true)
}

void off() {
  logDebug('off() called')
  setEnableState(false)
}

// ─── Heat-only stubs for the Thermostat composite capability ──────────
// The ST1820 has no cooling, no fan, and no scheduling RPC. These
// commands are required by the 'Thermostat' capability contract but
// are accepted as no-ops with a warning so heating-schedule apps can
// drive the device without crashing on unsupported method calls.

void cool() {
  logWarn('cool() not supported by ST1820 (heat-only); ignoring')
}

void auto() {
  logWarn('auto() not supported by ST1820 (heat-only); treating as heat()')
  heat()
}

void emergencyHeat() {
  logWarn('emergencyHeat() not supported by ST1820; treating as heat()')
  heat()
}

void setCoolingSetpoint(BigDecimal temp) {
  logWarn("setCoolingSetpoint(${temp}) not supported by ST1820 (heat-only); ignoring")
}

void setThermostatFanMode(String fanmode) {
  logWarn("setThermostatFanMode(${fanmode}) not supported by ST1820 (no fan); ignoring")
}

void fanAuto()      { setThermostatFanMode('auto') }
void fanCirculate() { setThermostatFanMode('circulate') }
void fanOn()        { setThermostatFanMode('on') }

void setSchedule(schedule) {
  logWarn("setSchedule(${schedule}) not implemented for ST1820")
}

/**
 * Enables or disables anti-freeze protection.
 * Accepts 'true' or 'false' as a String (matches Hubitat ENUM input).
 */
void setAntiFreeze(String enabled) {
  logDebug("setAntiFreeze(${enabled}) called")
  Boolean value = (enabled == 'true')
  setBooleanRole('anti_freeze', value)
  // Keep preference toggle in sync with rule-driven changes
  device.updateSetting('antiFreeze', [type: 'bool', value: value])
}

/**
 * Enables or disables the physical button lock.
 */
void setChildLock(String enabled) {
  logDebug("setChildLock(${enabled}) called")
  Boolean value = (enabled == 'true')
  setBooleanRole('child_lock', value)
  device.updateSetting('childLock', [type: 'bool', value: value])
}

/**
 * Pushes preference values (anti_freeze, child_lock) to the device.
 * Called from updated() after the user saves preferences.
 */
private void relayDeviceSettings() {
  if (settings?.antiFreeze != null) {
    setBooleanRole('anti_freeze', settings.antiFreeze as Boolean)
  }
  if (settings?.childLock != null) {
    setBooleanRole('child_lock', settings.childLock as Boolean)
  }
}

/**
 * Resolves the component key ("boolean:NNN") for a Boolean role from
 * state.virtualMap, extracts the numeric instance id, and delegates to the
 * parent dispatcher. Traces (rather than warns) if the role isn't in the
 * cache — the next refresh will repopulate. Warns if the role resolves to
 * a non-boolean component (a schema-flexibility safeguard).
 */
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

/**
 * Convenience wrapper for setting the 'enable' boolean role.
 */
private void setEnableState(Boolean value) {
  setBooleanRole('enable', value)
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
 * Falls back to 0.5 °C if the service config hasn't been fetched yet or
 * the field is missing — typical floor-heating thermostat default.
 */
private BigDecimal readTempHysteresisC() {
  Map sc = state.serviceConfig as Map
  Object hyst = sc?.temp_hysteresis
  if (hyst instanceof Number) {
    return (hyst as BigDecimal)
  }
  return 0.5G
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Receives the full Shelly.GetStatus result map (passed in by fetchAndDistribute()
 * after componentLinkedgoFetchStatus returns). Resolves each virtual-instance
 * entry (boolean:N, number:N, enum:N) to a role via state.virtualMap and
 * dispatches to the appropriate Hubitat attribute.
 *
 * @param status The complete Shelly.GetStatus result map
 */
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
        // Unknown virtual instance — likely stale virtualMap or a custom user-added component
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

    // 'thermostatOperatingState' depends on the combined view of enable +
    // current_temperature + target_temperature, so it's computed once after
    // all roles for this poll have been processed. 'switch' is updated
    // directly inside handleRoleUpdate(case 'enable') and tracks the
    // device's enable/sleep state.
    updateOperatingState()

    sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
  } catch (Exception e) {
    logError("distributeStatus internal exception: ${e.message}")
    e.printStackTrace()
  }
}

/**
 * Dispatches a role update to the matching Hubitat attribute. Numeric
 * values in temperature roles are scale-converted before sendEvent.
 *
 * @param role The virtual component role (e.g. 'enable', 'target_temperature')
 * @param data The status map for the instance ({ value, source, last_update_ts })
 * @param scale The hub temperature scale ('C' or 'F')
 */
private void handleRoleUpdate(String role, Map data, String scale) {
  logTrace("handleRoleUpdate: role=${role}, value=${data.value}, scale=${scale}")
  if (data.value == null) { return }

  switch (role) {
    case 'enable':
      Boolean enabled = data.value as Boolean
      // Cache in state so updateOperatingState() can read enable synchronously
      // without going through device.currentValue('switch'), which may not yet
      // reflect the sendEvent below if the event is still queued in the same
      // execution pass.
      state.enabled = enabled
      sendEvent(name: 'switch', value: enabled ? 'on' : 'off',
        descriptionText: "Thermostat is ${enabled ? 'on' : 'off'}")
      sendEvent(name: 'thermostatMode', value: enabled ? 'heat' : 'off',
        descriptionText: "Thermostat mode is ${enabled ? 'heat' : 'off'}")
      logInfo("Thermostat ${enabled ? 'on' : 'off'}")
      break

    case 'anti_freeze':
      String afState = (data.value as Boolean) ? 'true' : 'false'
      sendEvent(name: 'antiFreezeEnabled', value: afState,
        descriptionText: "Anti-freeze: ${afState}")
      break

    case 'child_lock':
      String clState = (data.value as Boolean) ? 'true' : 'false'
      sendEvent(name: 'childLockEnabled', value: clState,
        descriptionText: "Child lock: ${clState}")
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
      // Mirror to coolingSetpoint and thermostatSetpoint so heating-schedule
      // apps that read either field via the Thermostat composite see the
      // correct value. This is heat-only — they all hold the same setpoint.
      sendEvent(name: 'coolingSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Cooling setpoint is ${temp}°${scale}")
      sendEvent(name: 'thermostatSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Setpoint is ${temp}°${scale}")
      logInfo("Heating setpoint: ${temp}°${scale}")
      break

    case 'current_humidity':
      BigDecimal rh = (data.value as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
      sendEvent(name: 'humidity', value: rh.intValue(), unit: '%',
        descriptionText: "Humidity is ${rh.intValue()}%")
      break

    default:
      logTrace("handleRoleUpdate: unhandled role '${role}'")
  }
}

/**
 * Computes 'thermostatOperatingState' from the cached enable state and the
 * current/target temperatures (in °C). The device does not expose its
 * heat-output relay through the Virtual Components RPC — the underlying
 * Tuya DP 163 (3=on, 4=off) is internal — so the inferred state mirrors
 * the same hysteresis-band logic the device firmware uses to drive the
 * relay:
 *
 *   - enable=false     : idle
 *   - current < target - hysteresis/2  : heating
 *   - current > target + hysteresis/2  : idle
 *   - within dead band : hold previous (avoids false flips on noise)
 *
 * 'switch' is NOT updated here — it tracks the device's enable/sleep state
 * directly via handleRoleUpdate(case 'enable'). This attribute exists
 * primarily to satisfy the Hubitat Thermostat composite capability
 * contract; polling lag means the inferred operating state can trail the
 * device's actual relay by up to one poll interval.
 */
private void updateOperatingState() {
  // Read from state.enabled (set in handleRoleUpdate case 'enable') rather
  // than device.currentValue('switch'). The 'switch' event is fired by
  // sendEvent earlier in the same distributeStatus() pass, and Hubitat's
  // event queue may not have committed it yet when currentValue() is
  // called from this method.
  Boolean enabled = state.enabled ? true : false
  BigDecimal currentC = state.lastCurrentTempC as BigDecimal
  BigDecimal targetC = state.lastTargetTempC as BigDecimal
  String currentOp = device.currentValue('thermostatOperatingState')?.toString()

  String newOp
  if (!enabled) {
    newOp = 'idle'
  } else if (currentC == null || targetC == null) {
    // First poll before both values are cached — hold previous (default idle).
    newOp = currentOp ?: 'idle'
  } else {
    BigDecimal halfHyst = readTempHysteresisC() / 2.0G
    BigDecimal lowerBound = targetC - halfHyst
    BigDecimal upperBound = targetC + halfHyst
    if (currentC < lowerBound) {
      newOp = 'heating'
    } else if (currentC > upperBound) {
      newOp = 'idle'
    } else {
      // In the hysteresis dead-band — hold previous to mirror the relay's
      // own behaviour (it doesn't flip in this band either).
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

/**
 * Currently a no-op trace-level logger. Webhook subscription is deferred
 * to v2 because the generic boolean.change/number.change/enum.change events
 * fire for every virtual component without identifying the role in the dst.
 */
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