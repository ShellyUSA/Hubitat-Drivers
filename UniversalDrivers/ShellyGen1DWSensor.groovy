/**
 * Shelly Gen1 DW Sensor
 *
 * Pre-built standalone driver for Gen 1 Shelly Door/Window sensor (DW1, DW2).
 * Battery-powered device that sleeps most of the time.
 * Fires open_url / close_url action URLs on contact state change.
 * Fires vibration_url on vibration detection.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 DW Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'ContactSensor'
    //Attributes: contact - ENUM ["open", "closed"]

    capability 'AccelerationSensor'
    //Attributes: acceleration - ENUM ["active", "inactive"]

    capability 'Battery'
    //Attributes: battery - NUMBER, unit:%

    capability 'TemperatureMeasurement'
    //Attributes: temperature - NUMBER

    capability 'IlluminanceMeasurement'
    //Attributes: illuminance - NUMBER, unit:lux

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
    attribute 'tilt', 'number'
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
 * Routes Gen 1 action URL callbacks for door/window sensor.
 * Contact events update contact attribute immediately.
 * Vibration events update acceleration attribute.
 * All events also trigger a refresh for full data.
 */
private void routeActionUrlCallback(Map params) {
  switch (params.dst) {
    case 'contact_open':
      sendEvent(name: 'contact', value: 'open', descriptionText: 'Contact opened')
      logInfo('Contact opened')
      parent?.componentRefresh(device)
      break
    case 'contact_close':
      sendEvent(name: 'contact', value: 'closed', descriptionText: 'Contact closed')
      logInfo('Contact closed')
      parent?.componentRefresh(device)
      break

    case 'vibration':
      sendEvent(name: 'acceleration', value: 'active', isStateChange: true,
        descriptionText: 'Vibration detected')
      logInfo('Vibration detected')
      // Auto-clear acceleration after 5 seconds
      runIn(5, 'clearAcceleration')
      parent?.componentRefresh(device)
      break

    case 'sensor_report':
      logInfo('Sensor wake-up report received — requesting status poll')
      parent?.componentRefresh(device)
      break

    default:
      logDebug("routeActionUrlCallback: unhandled dst=${params.dst}")
  }
}

/**
 * Clears the acceleration sensor back to inactive after vibration event.
 */
void clearAcceleration() {
  sendEvent(name: 'acceleration', value: 'inactive', descriptionText: 'Vibration cleared')
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

void refresh() {
  logDebug('refresh() called — note: battery device may be asleep')
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

    if (key.startsWith('contact:')) {
      if (data.open != null) {
        String contactState = data.open ? 'open' : 'closed'
        sendEvent(name: 'contact', value: contactState, descriptionText: "Contact is ${contactState}")
        logInfo("Contact: ${contactState}")
      }
    } else if (key.startsWith('temperature:')) {
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal :
        (data.tF != null ? data.tF as BigDecimal : null)
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "°${scale}",
          descriptionText: "Temperature is ${temp}°${scale}")
      }
    } else if (key.startsWith('lux:')) {
      if (data.value != null) {
        Integer lux = data.value as Integer
        sendEvent(name: 'illuminance', value: lux, unit: 'lux',
          descriptionText: "Illuminance is ${lux} lux")
      }
    } else if (key.startsWith('tilt:')) {
      if (data.value != null) {
        Integer tilt = data.value as Integer
        sendEvent(name: 'tilt', value: tilt, descriptionText: "Tilt angle is ${tilt}°")
      }
    } else if (key.startsWith('devicepower:')) {
      if (data.battery != null) {
        Integer battery = data.battery as Integer
        sendEvent(name: 'battery', value: battery, unit: '%',
          descriptionText: "Battery is ${battery}%")
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
