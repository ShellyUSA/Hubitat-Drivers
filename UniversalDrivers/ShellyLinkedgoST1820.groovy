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
 * Mode: heat-only. ThermostatMode enum: ['heat', 'off'].
 *
 * Communication: WiFi, always-awake. Polling-only in v1 (no webhooks).
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Linkedgo ST1820', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'ThermostatHeatingSetpoint'
    capability 'TemperatureMeasurement'
    capability 'RelativeHumidityMeasurement'
    capability 'Switch'
    capability 'Refresh'
    capability 'Initialize'

    command 'setAntiFreeze', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable or disable anti-freeze protection']]
    command 'setChildLock', [[name: 'Enabled', type: 'ENUM', constraints: ['true', 'false'], description: 'Enable or disable physical button lock']]

    attribute 'thermostatMode', 'enum', ['heat', 'off']
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
  sendEvent(name: 'switch', value: 'off', descriptionText: 'Initialized')
  sendEvent(name: 'heatingSetpoint', value: defaultSetpoint, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'temperature', value: 0, unit: "°${scale}", descriptionText: 'Initialized')
  sendEvent(name: 'humidity', value: 0, unit: '%', descriptionText: 'Initialized')
  sendEvent(name: 'antiFreezeEnabled', value: 'false', descriptionText: 'Initialized')
  sendEvent(name: 'childLockEnabled', value: 'false', descriptionText: 'Initialized')
  sendEvent(name: 'lastUpdated', value: 'Never')
  parent?.componentRefresh(device)
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

/**
 * Ensures state.virtualMap (role -> instance ID) is populated by querying
 * Shelly.GetComponents via the parent app. Called lazily before any RPC
 * write that needs to look up an instance ID by role.
 */
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

/**
 * Ensures state.serviceConfig (temp_offset, temp_range, etc.) is populated.
 * Used to clamp setpoints to the device-supported range.
 */
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
 * Sets the heating setpoint. Converts F to C if hub is in °F (the device
 * stores Celsius internally) and clamps to the device-supported range
 * read from state.serviceConfig (default 5..30 °C if not yet populated).
 *
 * @param temp Target temperature in the hub's configured scale
 */
void setHeatingSetpoint(BigDecimal temp) {
  logDebug("setHeatingSetpoint(${temp}) called")
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
 * Turns the thermostat on (sets enable=true). Convenience for Switch capability.
 */
void on() {
  logDebug('on() called')
  setEnableState(true)
}

/**
 * Turns the thermostat off (sets enable=false).
 */
void off() {
  logDebug('off() called')
  setEnableState(false)
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
 * Resolves the instance ID for a Boolean role from state.virtualMap and
 * delegates to the parent dispatcher. Logs a warning if the role isn't
 * in the cache — the next refresh will repopulate.
 */
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Receives the full Shelly.GetStatus result map from the parent app's
 * componentRefresh dispatcher. Resolves each virtual-instance entry
 * (boolean:N, number:N, enum:N) to a role via state.virtualMap and
 * dispatches to the appropriate Hubitat attribute.
 *
 * @param status The complete Shelly.GetStatus result map
 */
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

  // Reverse map for instance-ID -> role lookup
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
      // Unknown virtual instance — likely stale virtualMap or a custom user-added component
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

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
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
  if (data.value == null) { return }

  switch (role) {
    case 'enable':
      Boolean enabled = data.value as Boolean
      sendEvent(name: 'thermostatMode', value: enabled ? 'heat' : 'off',
        descriptionText: "Thermostat mode is ${enabled ? 'heat' : 'off'}")
      sendEvent(name: 'switch', value: enabled ? 'on' : 'off',
        descriptionText: "Thermostat is ${enabled ? 'on' : 'off'}")
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
      BigDecimal temp = scaleTemp(data.value as BigDecimal, scale)
      sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
        descriptionText: "Temperature is ${temp}°${scale}")
      logInfo("Temperature: ${temp}°${scale}")
      break

    case 'target_temperature':
      BigDecimal temp = scaleTemp(data.value as BigDecimal, scale)
      sendEvent(name: 'heatingSetpoint', value: temp, unit: "°${scale}",
        descriptionText: "Heating setpoint is ${temp}°${scale}")
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
