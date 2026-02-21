/**
 * Shelly Autoconf BLU TRV - Component Driver
 *
 * Component child driver for Shelly BLU TRV (SBTRV-001AEU) paired via
 * a Shelly BLU Gateway Gen3. Delegates all commands to the gateway parent
 * driver, which relays them to the app for HTTP dispatch via BluTrv.Call RPC.
 *
 * This driver is purely passive — it never makes HTTP calls directly.
 * Status arrives via webhook events and poll-based distributeStatus()
 * from the gateway parent driver.
 *
 * Version: 1.0.0
 */
import groovy.transform.Field
import groovy.transform.CompileStatic

metadata {
  definition(name: 'Shelly Autoconf BLU TRV', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'ThermostatHeatingSetpoint'
    //Attributes: heatingSetpoint - NUMBER; Commands: setHeatingSetpoint(temp)

    capability 'Valve'
    //Attributes: valve - ENUM ["open","closed"]; Commands: open(), close()

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'Refresh'
    //Commands: refresh()

    command 'setValvePosition', [[name: 'Position', type: 'NUMBER', description: 'Valve position (0-100)']]
    command 'setExternalTemperature', [[name: 'Temperature', type: 'NUMBER', description: 'External temperature reading']]
    command 'setBoostMinutes', [[name: 'Minutes', type: 'NUMBER', description: 'Boost duration in minutes (0 to cancel)']]
    command 'calibrate'

    attribute 'valvePosition', 'number'
    attribute 'windowOpen', 'enum', ['true', 'false']
    attribute 'lastUpdated', 'string'
  }

  preferences {
    input name: 'logLevel', type: 'enum', title: 'Logging Level',
      options: ['warn': 'Warning', 'info': 'Info', 'debug': 'Debug', 'trace': 'Trace'],
      defaultValue: 'info', required: true
  }
}

@Field static Boolean COMP = true



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle                                            ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
}

void updated() {
  logDebug('updated() called')
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Thermostat Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sets the TRV heating setpoint. Converts Fahrenheit to Celsius if
 * the hub is configured for Fahrenheit since the TRV uses Celsius internally.
 *
 * @param temp Target temperature in the hub's configured scale
 */
void setHeatingSetpoint(BigDecimal temp) {
  logDebug("setHeatingSetpoint(${temp}) called")
  String scale = location.temperatureScale ?: 'F'
  BigDecimal tempC = (scale == 'F') ? fahrenheitToCelsius(temp) : temp
  parent?.componentBluTrvSetTarget(device, tempC)
}

/**
 * Opens the TRV valve fully (position 100).
 */
void open() {
  logDebug('open() called')
  parent?.componentBluTrvSetPosition(device, 100)
}

/**
 * Closes the TRV valve fully (position 0).
 */
void close() {
  logDebug('close() called')
  parent?.componentBluTrvSetPosition(device, 0)
}

/**
 * Sets the TRV valve position to a specific percentage.
 * Note: thermostat mode must be disabled on the TRV for direct position control.
 *
 * @param position Valve position from 0 (closed) to 100 (fully open)
 */
void setValvePosition(BigDecimal position) {
  logDebug("setValvePosition(${position}) called")
  parent?.componentBluTrvSetPosition(device, position.setScale(0, BigDecimal.ROUND_HALF_UP).intValue())
}

/**
 * Sends an external temperature reading to the TRV for more accurate
 * room temperature measurement. Converts F to C if needed.
 *
 * @param temp External temperature in the hub's configured scale
 */
void setExternalTemperature(BigDecimal temp) {
  logDebug("setExternalTemperature(${temp}) called")
  String scale = location.temperatureScale ?: 'F'
  BigDecimal tempC = (scale == 'F') ? fahrenheitToCelsius(temp) : temp
  parent?.componentBluTrvSetExternalTemp(device, tempC)
}

/**
 * Sets the TRV boost mode duration. The TRV will heat at maximum
 * output for the specified number of minutes. Pass 0 to cancel.
 *
 * @param minutes Boost duration in minutes (0 to cancel boost)
 */
void setBoostMinutes(BigDecimal minutes) {
  logDebug("setBoostMinutes(${minutes}) called")
  Integer durationSecs = (minutes as Integer) * 60
  if (durationSecs > 0) {
    parent?.componentBluTrvSetBoost(device, durationSecs)
  } else {
    parent?.componentBluTrvClearBoost(device)
  }
}

/**
 * Triggers valve motor calibration on the TRV.
 * The TRV will fully open and close to calibrate its position sensor.
 */
void calibrate() {
  logDebug('calibrate() called')
  parent?.componentBluTrvCalibrate(device)
}

/**
 * Refreshes the TRV state by requesting a status poll from the gateway.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentBluTrvRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Thermostat Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Helper Functions                                            ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Converts Fahrenheit to Celsius.
 *
 * @param tempF Temperature in Fahrenheit
 * @return Temperature in Celsius
 */
@CompileStatic
private static BigDecimal fahrenheitToCelsius(BigDecimal tempF) {
  return ((tempF - 32) * 5.0 / 9.0)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Helper Functions                                        ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

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

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝
