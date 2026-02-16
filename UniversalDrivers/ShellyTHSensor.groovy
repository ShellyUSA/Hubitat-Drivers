/**
 * Shelly Autoconf TH Sensor
 *
 * Pre-built standalone driver for Shelly temperature/humidity sensor devices.
 * Examples: Shelly H&T, Shelly Plus H&T
 *
 * This is a battery-powered sensor device that sleeps most of the time.
 * It wakes briefly to send temperature/humidity updates via webhooks.
 * When a sensor notification arrives, the driver also requests a battery
 * level update from the parent app while the device is awake.
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline. Sensor data arrives via parse() from Shelly webhook notifications.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf TH Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'RelativeHumidityMeasurement'
    //Attributes: humidity - NUMBER

    capability 'Battery'
    //Attributes: battery - NUMBER

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()
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
 * Battery-powered devices send temperature/humidity updates when they wake,
 * and battery level is requested opportunistically during those wake windows.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  logTrace('parse() received message')

  try {
    Map msg = parseLanMessage(description)
    logTrace("parse() msg keys: ${msg?.keySet()}, status=${msg?.status}, body=${msg?.body ? 'present' : 'null'}, headers=${msg?.headers ? 'present' : 'null'}, header=${msg?.header ? 'present' : 'null'}")
    if (msg?.headers) { logTrace("parse() headers map keys: ${msg.headers.keySet()}") }
    if (msg?.header) { logTrace("parse() raw header: ${msg.header}") }

    if (msg?.status != null) {
      logTrace("parse() skipping HTTP response (status=${msg.status})")
      return
    }

    // Try POST JSON body first (legacy script notifications)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        String dst = json?.dst as String
        logDebug("POST notification dst=${dst}")
        logTrace("POST body: ${json}")

        if (dst == 'temperature') { parseTemperature(json) }
        else if (dst == 'humidity') { parseHumidity(json) }
        else if (dst == 'battery') { parseBattery(json) }
        return
      } catch (Exception jsonEx) {
        // Body might be empty or not JSON — fall through to GET parsing
      }
    }

    // Try GET query parameters (webhook notifications with URL tokens)
    Map params = parseWebhookQueryParams(msg)
    if (params?.dst) {
      logDebug("GET webhook dst=${params.dst}")
      logTrace("Webhook params: ${params}")
      routeWebhookParams(params)
    } else {
      logTrace("parse() no dst found in message, unable to route")
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

/**
 * Parses query parameters from an incoming GET webhook request.
 *
 * @param msg The parsed LAN message map
 * @return Map of query parameter key-value pairs, or null if not parseable
 */
private Map parseWebhookQueryParams(Map msg) {
  String requestLine = null

  // Try parsed headers map first
  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
    logTrace("parseWebhookQueryParams: headers map search result: ${requestLine ? 'found' : 'not found'}")
  }

  // Fallback: parse raw header string for request line
  if (!requestLine && msg?.header) {
    String rawHeader = msg.header.toString()
    logTrace("parseWebhookQueryParams: trying raw header fallback")
    String[] lines = rawHeader.split('\n')
    for (String line : lines) {
      String trimmed = line.trim()
      if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
        requestLine = trimmed
        logTrace("parseWebhookQueryParams: found request line in raw header: ${requestLine}")
        break
      }
    }
  }

  if (!requestLine) {
    logTrace('parseWebhookQueryParams: no request line found in headers or raw header')
    return null
  }

  String pathAndQuery = requestLine.split(' ')[1]
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx < 0) { return null }

  Map params = [:]
  pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
    String[] kv = pair.split('=', 2)
    if (kv.length == 2) {
      params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
    }
  }
  logTrace("parseWebhookQueryParams: parsed params: ${params}")
  return params
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Handles temperature, humidity, and piggybacked battery data.
 *
 * @param params The parsed query parameters
 */
private void routeWebhookParams(Map params) {
  if (params.dst == 'temperature') {
    String scale = location.temperatureScale ?: 'F'
    BigDecimal temp = null
    if (scale == 'C' && params.tC) {
      temp = params.tC as BigDecimal
    } else if (params.tF) {
      temp = params.tF as BigDecimal
    }
    if (temp != null) {
      sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
        descriptionText: "Temperature is ${temp}°${scale}")
      logInfo("Temperature: ${temp}°${scale}")
    }
  }
  if (params.dst == 'humidity' && params.rh != null) {
    BigDecimal humidity = params.rh as BigDecimal
    sendEvent(name: 'humidity', value: humidity, unit: '%',
      descriptionText: "Humidity is ${humidity}%")
    logInfo("Humidity: ${humidity}%")
  }
  // Battery data piggybacked on sensor webhooks via supplemental URL tokens
  if (params.battPct != null) {
    Integer batteryPct = params.battPct as Integer
    sendEvent(name: 'battery', value: batteryPct, unit: '%',
      descriptionText: "Battery is ${batteryPct}%")
    logInfo("Battery: ${batteryPct}%")
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
// ║  Sensor Monitoring - Temperature, Humidity & Battery Parsing ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses temperature notifications from Shelly device.
 * Processes JSON with dst:"temperature" and updates the temperature attribute.
 * Also requests battery level from the parent app while the device is awake.
 * JSON format: [dst:temperature, result:[temperature:0:[id:0, tC:24.4, tF:75.9]]]
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

/**
 * Parses humidity notifications from Shelly device.
 * Processes JSON with dst:"humidity" and updates the humidity attribute.
 * Also requests battery level from the parent app while the device is awake.
 * JSON format: [dst:humidity, result:[humidity:0:[id:0, rh:73.7]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseHumidity(Map json) {
  logDebug("parseHumidity() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseHumidity: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map && value.rh != null) {
        BigDecimal humidity = value.rh as BigDecimal
        def currentHumidity = device.currentValue('humidity')
        if (currentHumidity == null || (currentHumidity as BigDecimal) != humidity) {
          sendEvent(name: "humidity", value: humidity, unit: "%",
            descriptionText: "Humidity is ${humidity}%")
          logInfo("Humidity: ${humidity}%")
        } else {
          logDebug("Humidity unchanged: ${humidity}%")
        }
      }
    }
  } catch (Exception e) {
    logError("parseHumidity exception: ${e.message}")
  }
}

/**
 * Parses battery/device power notifications from Shelly device.
 * Processes JSON with dst:"battery" and updates the battery attribute.
 * Only sends an event if the battery percentage has actually changed.
 * JSON format: [dst:battery, result:[devicepower:0:[id:0, battery:[V:4.87, percent:50], external:[present:false]]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseBattery(Map json) {
  logDebug("parseBattery() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseBattery: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        Map battery = value.battery
        if (battery?.percent != null) {
          Integer batteryPct = battery.percent as Integer
          def currentBattery = device.currentValue('battery')
          if (currentBattery == null || (currentBattery as Integer) != batteryPct) {
            sendEvent(name: "battery", value: batteryPct, unit: "%",
              descriptionText: "Battery is ${batteryPct}%")
            logInfo("Battery: ${batteryPct}%")
          } else {
            logDebug("Battery unchanged: ${batteryPct}%")
          }
        }
        if (battery?.V != null) {
          BigDecimal voltage = battery.V as BigDecimal
          logDebug("Battery voltage: ${voltage}V")
        }
      }
    }
  } catch (Exception e) {
    logError("parseBattery exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Sensor Monitoring                                       ║
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
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
