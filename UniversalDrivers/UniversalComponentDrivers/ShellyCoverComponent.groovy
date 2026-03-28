/**
 * Shelly Autoconf Cover - Component Driver
 *
 * Self-contained component driver for Shelly cover/shade components.
 * Delegates commands to parent app via componentXxx() pattern.
 * Used as a child device in multi-component parent-child architecture.
 */
import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf Cover', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade' //windowShade - ENUM ['opening', 'partially open', 'closed', 'open', 'closing', 'unknown'] //position - NUMBER, unit:%
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn':'Warning', 'info':'Info', 'debug':'Debug', 'trace':'Trace'],
      defaultValue: 'info', required: true
    input name: 'invert_directions', type: 'bool', title: 'Reverse Motor Direction on Device',
      defaultValue: false, required: false
    input name: 'in_mode', type: 'enum', title: 'Physical Input Mode',
      options: ['single':'Single', 'dual':'Dual', 'detached':'Detached'], required: false
    input name: 'in_locked', type: 'bool', title: 'Lock Physical Inputs',
      defaultValue: false, required: false
    input name: 'swap_inputs', type: 'bool', title: 'Swap Open and Close Inputs',
      defaultValue: false, required: false
    input name: 'maxtime_open', type: 'decimal', title: 'Max Travel Time Open (seconds)',
      required: false
    input name: 'maxtime_close', type: 'decimal', title: 'Max Travel Time Close (seconds)',
      required: false
    input name: 'motor_idle_power_thr', type: 'decimal', title: 'Motor Idle Power Threshold (W)',
      required: false
    input name: 'motor_idle_confirm_period', type: 'decimal', title: 'Motor Idle Confirm Delay (seconds)',
      required: false
    input name: 'maintenance_mode', type: 'bool', title: 'Maintenance Mode (disable movement)',
      defaultValue: false, required: false
    input name: 'obstruction_enable', type: 'bool', title: 'Obstruction Detection Enabled',
      defaultValue: false, required: false
    input name: 'obstruction_direction', type: 'enum', title: 'Obstruction Detection Direction',
      options: ['open':'Open', 'close':'Close', 'both':'Both'], required: false
    input name: 'obstruction_action', type: 'enum', title: 'Obstruction Detection Action',
      options: ['stop':'Stop', 'reverse':'Reverse'], required: false
    input name: 'obstruction_power_thr', type: 'decimal', title: 'Obstruction Power Threshold (W)',
      required: false
    input name: 'obstruction_holdoff', type: 'decimal', title: 'Obstruction Detection Holdoff (seconds)',
      required: false
    input name: 'safety_switch_enable', type: 'bool', title: 'Safety Switch Enabled',
      defaultValue: false, required: false
    input name: 'safety_switch_direction', type: 'enum', title: 'Safety Switch Direction',
      options: ['open':'Open', 'close':'Close', 'both':'Both'], required: false
    input name: 'safety_switch_action', type: 'enum', title: 'Safety Switch Action',
      options: ['stop':'Stop', 'reverse':'Reverse', 'pause':'Pause'], required: false
    input name: 'safety_switch_allowed_move', type: 'enum', title: 'Safety Switch Allowed Movement',
      options: ['none':'No movement while engaged', 'reverse':'Reverse only'], required: false
  }
}

@Field static Boolean COMP = true

void installed() {
  logDebug('installed() called')
  initialize()
}

void updated() {
  logDebug('updated() called')
  initialize()
  relayCoverSettings()
}

void initialize() {
  logDebug('initialize() called')
}

/**
 * Gathers cover settings and sends them to the parent driver.
 */
private void relayCoverSettings() {
  Map coverSettings = [:]
  if (settings.invert_directions != null) { coverSettings.invert_directions = settings.invert_directions as Boolean }
  if (settings.in_mode != null) { coverSettings.in_mode = settings.in_mode as String }
  if (settings.in_locked != null) { coverSettings.in_locked = settings.in_locked as Boolean }
  if (settings.swap_inputs != null) { coverSettings.swap_inputs = settings.swap_inputs as Boolean }
  if (settings.maxtime_open != null) { coverSettings.maxtime_open = settings.maxtime_open as BigDecimal }
  if (settings.maxtime_close != null) { coverSettings.maxtime_close = settings.maxtime_close as BigDecimal }
  if (settings.motor_idle_power_thr != null) { coverSettings.motor_idle_power_thr = settings.motor_idle_power_thr as BigDecimal }
  if (settings.motor_idle_confirm_period != null) { coverSettings.motor_idle_confirm_period = settings.motor_idle_confirm_period as BigDecimal }
  if (settings.maintenance_mode != null) { coverSettings.maintenance_mode = settings.maintenance_mode as Boolean }
  if (settings.obstruction_enable != null) { coverSettings.obstruction_enable = settings.obstruction_enable as Boolean }
  if (settings.obstruction_direction != null) { coverSettings.obstruction_direction = settings.obstruction_direction as String }
  if (settings.obstruction_action != null) { coverSettings.obstruction_action = settings.obstruction_action as String }
  if (settings.obstruction_power_thr != null) { coverSettings.obstruction_power_thr = settings.obstruction_power_thr as BigDecimal }
  if (settings.obstruction_holdoff != null) { coverSettings.obstruction_holdoff = settings.obstruction_holdoff as BigDecimal }
  if (settings.safety_switch_enable != null) { coverSettings.safety_switch_enable = settings.safety_switch_enable as Boolean }
  if (settings.safety_switch_direction != null) { coverSettings.safety_switch_direction = settings.safety_switch_direction as String }
  if (settings.safety_switch_action != null) { coverSettings.safety_switch_action = settings.safety_switch_action as String }
  if (settings.safety_switch_allowed_move != null) { coverSettings.safety_switch_allowed_move = settings.safety_switch_allowed_move as String }
  if (!coverSettings.isEmpty()) {
    logDebug("Relaying cover settings to parent: ${coverSettings}")
    parent?.componentUpdateCoverSettings(device, coverSettings)
  }
}

void open() {
  logDebug('open() called')
  parent?.componentOpen(device)
}

void close() {
  logDebug('close() called')
  parent?.componentClose(device)
}

void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.componentSetPosition(device, position as Integer)
}

void stopPositionChange() {
  logDebug('stopPositionChange() called')
  parent?.componentStop(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ═══════════════════════════════════════════════════════════════
// Logging Helpers
// ═══════════════════════════════════════════════════════════════
private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}
void logError(message) { log.error "${device.displayName}: ${message}" }
void logWarn(message) { log.warn "${device.displayName}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${device.displayName}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${device.displayName}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${device.displayName}: ${message}" } }
