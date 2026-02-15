/**
 * Shelly Autoconf Single Cover PM
 *
 * Pre-built standalone driver for single-cover Shelly devices with power monitoring.
 * Examples: Shelly Plus 2PM (cover mode), Shelly Pro 2PM (cover mode)
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Commands delegate to the parent app via componentOpen/componentClose/etc.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Single Cover PM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'WindowShade'
    //Attributes: windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
    //            position - NUMBER
    //Commands: open(), close(), setPosition(position)

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    capability 'CurrentMeter'
    //Attributes: amperage - NUMBER, unit:A

    capability 'PowerMeter'
    //Attributes: power - NUMBER, unit:W

    capability 'VoltageMeasurement'
    //Attributes: voltage - NUMBER, unit:V

    capability 'EnergyMeter'
    //Attributes: energy - NUMBER, unit:kWh

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    command 'resetEnergyMonitors'
    command 'stopPositionChange'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level', options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'], defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug("installed() called")
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration.
 */
void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
}

/**
 * Parses incoming LAN messages from the Shelly device.
 * Routes notifications to the appropriate handler based on the dst field.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  logDebug("parse() called with description: ${description}")

  try {
    Map msg = parseLanMessage(description)

    // Decode headers if they're base64 encoded
    if (msg?.header && !msg?.headers) {
      try {
        String decodedHeaders = new String(msg.header.decodeBase64())
        logDebug("Decoded headers:\n${decodedHeaders}")
      } catch (Exception e) {
        logDebug("Could not decode headers: ${e.message}")
      }
    }

    // Check if this is an incoming request (status is null) or a response (status has value)
    if (msg?.status != null) {
      // This is an HTTP response
      logDebug("Parsed HTTP response: status=${msg.status}")

      if (msg.status == 200 && msg.body) {
        try {
          def json = new groovy.json.JsonSlurper().parseText(msg.body)
          logDebug("Parsed JSON body: ${json}")
        } catch (Exception jsonEx) {
          logWarn("Could not parse JSON body: ${jsonEx.message}")
        }
      } else if (msg.status != 200) {
        logWarn("HTTP error response: ${msg.status}")
      }
    } else {
      // This is an incoming HTTP request from Shelly device (webhook/notification)
      logDebug("Received incoming request from Shelly device")

      if (msg.body) {
        try {
          def json = new groovy.json.JsonSlurper().parseText(msg.body)
          logDebug("Request body JSON: ${json}")

          // Route to handler based on destination type
          if (json?.dst == "covermon") {
            parseCovermon(json)
          } else if (json?.dst == "powermon") {
            parsePowermon(json)
          } else if (json?.dst == "temperature") {
            parseTemperature(json)
          }
        } catch (Exception jsonEx) {
          // Body might be empty or not JSON
        }
      }
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Initializes the device driver. Called on install and settings update.
 */
void initialize() {
  logDebug("initialize() called")
}

/**
 * Configures the device driver settings.
 * Sets default log level if not already configured.
 */
void configure() {
  logDebug("configure() called")
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Refreshes the device state by querying the parent app.
 */
void refresh() {
  logDebug("refresh() called")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Cover Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the window shade / cover.
 * Delegates to parent app's componentOpen handler.
 */
void open() {
  logDebug("open() called")
  parent?.componentOpen(device)
}

/**
 * Closes the window shade / cover.
 * Delegates to parent app's componentClose handler.
 */
void close() {
  logDebug("close() called")
  parent?.componentClose(device)
}

/**
 * Sets the cover position to a specific value.
 * Delegates to parent app's componentSetPosition handler.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.componentSetPosition(device, position as Integer)
}

/**
 * Stops any in-progress cover movement.
 * Delegates to parent app's componentStop handler.
 */
void stopPositionChange() {
  logDebug("stopPositionChange() called")
  parent?.componentStop(device)
}

/**
 * Parses cover monitoring notifications from Shelly device.
 * Processes JSON with dst:"covermon" and updates windowShade, position, and
 * inline power monitoring attributes (voltage, current, power, energy).
 * JSON format: [dst:covermon, result:[cover:0:[id:0, state:open, current_pos:100, apower:0, voltage:120.8, current:0, aenergy:[total:1234]]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseCovermon(Map json) {
  logDebug("parseCovermon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseCovermon: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('cover:')) {
        if (value instanceof Map) {
          Integer coverId = value.id
          String shellyState = value.state

          // Map Shelly cover state to Hubitat WindowShade values
          if (shellyState != null) {
            String shadeState
            switch (shellyState) {
              case 'open':
                shadeState = 'open'
                break
              case 'closed':
                shadeState = 'closed'
                break
              case 'opening':
                shadeState = 'opening'
                break
              case 'closing':
                shadeState = 'closing'
                break
              case 'stopped':
                shadeState = 'partially open'
                break
              case 'calibrating':
                shadeState = 'unknown'
                break
              default:
                shadeState = 'unknown'
            }
            logInfo("Cover ${coverId} state changed to: ${shadeState}")
            sendEvent(name: "windowShade", value: shadeState,
              descriptionText: "Window shade is ${shadeState}")
          }

          // Update position attribute
          if (value.current_pos != null) {
            Integer position = value.current_pos as Integer
            sendEvent(name: "position", value: position, unit: "%",
              descriptionText: "Position is ${position}%")
            logDebug("Cover ${coverId} position: ${position}%")
          }

          // Extract inline power monitoring values
          if (value.voltage != null) {
            BigDecimal voltage = value.voltage as BigDecimal
            sendEvent(name: "voltage", value: voltage, unit: "V", descriptionText: "Voltage is ${voltage}V")
            logDebug("Voltage: ${voltage}V")
          }

          if (value.current != null) {
            BigDecimal current = value.current as BigDecimal
            sendEvent(name: "amperage", value: current, unit: "A", descriptionText: "Current is ${current}A")
            logDebug("Current: ${current}A")
          }

          if (value.apower != null) {
            BigDecimal power = value.apower as BigDecimal
            sendEvent(name: "power", value: power, unit: "W", descriptionText: "Power is ${power}W")
            logDebug("Power: ${power}W")
          }

          if (value.aenergy?.total != null) {
            BigDecimal energyWh = value.aenergy.total as BigDecimal
            BigDecimal energyKwh = energyWh / 1000
            sendEvent(name: "energy", value: energyKwh, unit: "kWh", descriptionText: "Energy is ${energyKwh}kWh")
            logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
          }

          logInfo("Cover ${coverId} updated: state=${shellyState}, pos=${value.current_pos}, power=${value.apower}W")
        }
      }
    }
  } catch (Exception e) {
    logError("parseCovermon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Cover Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands and Parsing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses power monitoring notifications from Shelly device.
 * Processes JSON with dst:"powermon" and updates power/energy attributes.
 * JSON format: [dst:powermon, result:[cover:0:[aenergy:[total:76207], apower:0, current:0, id:0, voltage:120.8]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parsePowermon(Map json) {
  logDebug("parsePowermon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parsePowermon: No result data in JSON")
      return
    }

    // Iterate over component entries (e.g., "cover:0", etc.)
    result.each { key, value ->
      if (value instanceof Map) {
        Integer componentId = value.id

        // Extract power monitoring values
        if (value.voltage != null) {
          BigDecimal voltage = value.voltage as BigDecimal
          sendEvent(name: "voltage", value: voltage, unit: "V", descriptionText: "Voltage is ${voltage}V")
          logDebug("Voltage: ${voltage}V")
        }

        if (value.current != null) {
          BigDecimal current = value.current as BigDecimal
          sendEvent(name: "amperage", value: current, unit: "A", descriptionText: "Current is ${current}A")
          logDebug("Current: ${current}A")
        }

        if (value.apower != null) {
          BigDecimal power = value.apower as BigDecimal
          sendEvent(name: "power", value: power, unit: "W", descriptionText: "Power is ${power}W")
          logDebug("Power: ${power}W")
        }

        if (value.aenergy?.total != null) {
          BigDecimal energyWh = value.aenergy.total as BigDecimal
          BigDecimal energyKwh = energyWh / 1000
          sendEvent(name: "energy", value: energyKwh, unit: "kWh", descriptionText: "Energy is ${energyKwh}kWh")
          logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
        }

        logInfo("Component ${key} power monitoring updated: ${value.apower}W, ${value.voltage}V, ${value.current}A")
      }
    }
  } catch (Exception e) {
    logError("parsePowermon exception: ${e.message}")
  }
}

/**
 * Resets energy monitoring counters by delegating to the parent app.
 */
void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Monitoring Commands and Parsing                   ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Temperature Monitoring                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses temperature notifications from Shelly device.
 * Handles internal device temperature sensors (temperature:100, temperature:101).
 * JSON format: [dst:temperature, result:[temperature:100:[id:100, tC:42.3, tF:108.1]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseTemperature(Map json) {
  logDebug("parseTemperature() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseTemperature: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        BigDecimal tempC = value.tC != null ? value.tC as BigDecimal : null
        BigDecimal tempF = value.tF != null ? value.tF as BigDecimal : null

        if (tempC != null || tempF != null) {
          // Use hub's temperature scale preference
          String scale = location.temperatureScale ?: 'F'
          BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC)
          String unit = "\u00B0${scale}"

          if (temp != null) {
            def currentTemp = device.currentValue('temperature')
            if (currentTemp == null || (currentTemp as BigDecimal) != temp) {
              sendEvent(name: "temperature", value: temp, unit: unit,
                descriptionText: "Temperature is ${temp}${unit}")
              logInfo("Temperature: ${temp}${unit}")
            } else {
              logDebug("Temperature unchanged: ${temp}${unit}")
            }
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseTemperature exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Temperature Monitoring                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') {
    return true
  } else if (messageLevel == 'warn') {
    return settings.logLevel == 'warn'
  } else if (messageLevel == 'info') {
    return ['warn', 'info'].contains(settings.logLevel)
  } else if (messageLevel == 'debug') {
    return ['warn', 'info', 'debug'].contains(settings.logLevel)
  } else if (messageLevel == 'trace') {
    return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel)
  }
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
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

/**
 * Formats a Map as pretty-printed JSON.
 *
 * @param jsonInput The map to format
 * @return Pretty-printed JSON string
 */
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
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

@Field static Boolean NOCHILDCOVER = true
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
