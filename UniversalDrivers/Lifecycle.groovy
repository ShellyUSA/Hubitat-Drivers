// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Lifecycle and Configuration                           ║
// ╚══════════════════════════════════════════════════════════════╝
void installed() {
  logDebug("installed() called")
  initialize()
}

void updated() {
  logDebug("updated() called with settings: ${settings}")
  initialize()
}

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
          // TODO: Process parsed JSON and update device attributes
        } catch (Exception jsonEx) {
          logWarn("Could not parse JSON body: ${jsonEx.message}")
        }
      } else if (msg.status != 200) {
        logWarn("HTTP error response: ${msg.status}")
      }
    } else {
      // This is an incoming HTTP request from Shelly device (webhook/notification)
      logDebug("Received incoming request from Shelly device")

      // Try POST JSON body first (script notifications like powermonitoring.js)
      if (msg.body) {
        try {
          def json = new groovy.json.JsonSlurper().parseText(msg.body)
          logDebug("Request body JSON: ${json}")

          // Process event data based on destination type
          if (json?.dst == "switchmon") {
            parseSwitchmon(json)
          } else if (json?.dst == "powermon") {
            parsePowermon(json)
          } else if (json?.dst == "covermon") {
            parseCovermon(json)
          } else if (json?.dst == "lightmon") {
            parseLightmon(json)
          } else if (json?.dst == "temperature") {
            parseTemperature(json)
          } else if (json?.dst == "humidity") {
            parseHumidity(json)
          } else if (json?.dst == "battery") {
            parseBattery(json)
          } else if (json?.dst == "smoke") {
            parseSmoke(json)
          } else if (json?.dst == "illuminance") {
            parseIlluminance(json)
          } else if (json?.dst == "input_push") {
            parseInputPush(json)
          } else if (json?.dst == "input_double") {
            parseInputDouble(json)
          } else if (json?.dst == "input_long") {
            parseInputLong(json)
          }
          return
        } catch (Exception jsonEx) {
          // Body might be empty or not JSON — fall through to GET parsing
        }
      }

      // Try GET query parameters (webhook notifications with URL tokens)
      Map params = parseWebhookQueryParams(msg)
      if (params?.dst) {
        routeWebhookParams(params)
      }
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
  if (!msg?.headers) { return null }
  String requestLine = msg.headers?.keySet()?.find { key ->
    key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
  }
  if (!requestLine) { return null }
  String pathAndQuery = requestLine.toString().split(' ')[1]
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx < 0) { return null }
  Map params = [:]
  pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
    String[] kv = pair.split('=', 2)
    if (kv.length == 2) {
      params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
    }
  }
  return params
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Handles all event types: switch, cover, temperature, humidity, battery,
 * smoke, illuminance, and input button events.
 *
 * @param params The parsed query parameters
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}")
        logInfo("Switch state changed to: ${switchState}")
      }
      break

    case 'covermon':
      if (params.state != null) {
        String shadeState
        switch (params.state) {
          case 'open': shadeState = 'open'; break
          case 'closed': shadeState = 'closed'; break
          case 'opening': shadeState = 'opening'; break
          case 'closing': shadeState = 'closing'; break
          case 'stopped': shadeState = 'partially open'; break
          case 'calibrating': shadeState = 'unknown'; break
          default: shadeState = 'unknown'
        }
        sendEvent(name: 'windowShade', value: shadeState,
          descriptionText: "Window shade is ${shadeState}")
        logInfo("Cover state changed to: ${shadeState}")
      }
      if (params.pos != null) {
        Integer position = params.pos as Integer
        sendEvent(name: 'position', value: position, unit: '%',
          descriptionText: "Position is ${position}%")
      }
      break

    case 'temperature':
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
      break

    case 'humidity':
      if (params.rh != null) {
        BigDecimal humidity = params.rh as BigDecimal
        sendEvent(name: 'humidity', value: humidity, unit: '%',
          descriptionText: "Humidity is ${humidity}%")
        logInfo("Humidity: ${humidity}%")
      }
      break

    case 'smoke':
      if (params.alarm != null) {
        String smokeState = params.alarm == 'true' ? 'detected' : 'clear'
        sendEvent(name: 'smoke', value: smokeState,
          descriptionText: "Smoke ${smokeState}")
        logInfo("Smoke: ${smokeState}")
      }
      break

    case 'illuminance':
      if (params.lux != null) {
        Integer lux = params.lux as Integer
        sendEvent(name: 'illuminance', value: lux, unit: 'lux',
          descriptionText: "Illuminance is ${lux} lux")
        logInfo("Illuminance: ${lux} lux")
      }
      break

    case 'input_push':
      sendEvent(name: 'pushed', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was pushed')
      break

    case 'input_double':
      sendEvent(name: 'doubleTapped', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was double-tapped')
      break

    case 'input_long':
      sendEvent(name: 'held', value: 1, isStateChange: true,
        descriptionText: 'Button 1 was held')
      break
  }

  // Battery data piggybacked on any webhook via supplemental URL tokens
  if (params.battPct != null) {
    Integer batteryPct = params.battPct as Integer
    sendEvent(name: 'battery', value: batteryPct, unit: '%',
      descriptionText: "Battery is ${batteryPct}%")
    logInfo("Battery: ${batteryPct}%")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                       ║
// ╚══════════════════════════════════════════════════════════════╝