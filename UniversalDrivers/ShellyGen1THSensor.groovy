/**
 * Shelly Gen1 TH Sensor
 *
 * Pre-built standalone driver for Gen 1 Shelly H&T (temperature/humidity sensor).
 * Battery-powered device that sleeps most of the time and wakes briefly to send reports.
 *
 * Gen 1 H&T fires a report_url action URL on wake, then the app polls GET /status
 * to read full sensor data (temperature, humidity, battery) before the device sleeps again.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 TH Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'RelativeHumidityMeasurement'
    //Attributes: humidity - NUMBER, unit:%

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                          ║
// ╚══════════════════════════════════════════════════════════════╝

void installed() {
  logDebug('installed() called')
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
}

/**
 * Parses incoming LAN messages from the Gen 1 Shelly H&T.
 * On wake, the device fires report_url which triggers the app to poll full status.
 *
 * @param description Raw LAN message description string from Hubitat
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }

    if (msg?.status != null) { return }

    Map params = parseWebhookQueryParams(msg)
    if (params?.dst) {
      logDebug("Action URL callback dst=${params.dst}, cid=${params.cid}")
      routeActionUrlCallback(params)
    } else {
      logTrace('No dst found in action URL callback')
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

private Map parseWebhookQueryParams(Map msg) {
  String requestLine = null

  if (msg?.headers) {
    requestLine = msg.headers.keySet()?.find { key ->
      key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
    }?.toString()
  }

  if (!requestLine) { return null }

  String pathAndQuery = requestLine.split(' ')[1]

  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      return [dst: segments[0], cid: segments[1]]
    }
  }

  return null
}

/**
 * Routes Gen 1 action URL callbacks for H&T sensor.
 * On sensor_report, triggers immediate refresh to read data before device sleeps.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'sensor_report':
      logInfo('Sensor wake-up report received — requesting status poll')
      parent?.componentRefresh(device)
      break

    case 'temp_over':
      logWarn('Over-temperature threshold exceeded')
      parent?.componentRefresh(device)
      break
    case 'temp_under':
      logWarn('Under-temperature threshold exceeded')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Initialize / Configure / Refresh Commands                   ║
// ╚══════════════════════════════════════════════════════════════╝

void initialize() {
  logDebug('initialize() called')
}

void configure() {
  logDebug('configure() called')
  if (!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}

/**
 * Refreshes the device state. Note: battery devices may be asleep.
 * Data will update on next wake-up report.
 */
void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Initialize / Configure / Refresh Commands               ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution                                         ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled status data to the device.
 * Called by the parent app after polling GET /status on the Gen 1 device.
 *
 * @param status Map of normalized component statuses
 */
void distributeStatus(Map status) {
  logDebug("distributeStatus() called with: ${status}")
  if (!status) { return }

  String scale = location.temperatureScale ?: 'F'

  status.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
        logInfo("Temperature: ${temp}°${scale}")
      }
    } else if (key.startsWith('humidity:')) {
      if (data.value != null) {
        BigDecimal humidity = data.value as BigDecimal
        sendEvent(name: 'humidity', value: humidity, unit: '%',
          descriptionText: "Humidity is ${humidity}%")
        logInfo("Humidity: ${humidity}%")
      }
    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
        logInfo("Battery: ${battery}%")
      }
    }
  }

  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                     ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return settings.logLevel == 'warn' }
  else if (messageLevel == 'info') { return ['warn', 'info'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['warn', 'info', 'debug'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
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
