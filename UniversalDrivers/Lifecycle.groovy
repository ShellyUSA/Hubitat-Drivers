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

    // Try POST JSON body first (script notifications like powermonitoring.js)
    if (msg?.body) {
      try {
        def json = new groovy.json.JsonSlurper().parseText(msg.body)
        String dst = json?.dst as String
        logDebug("POST notification dst=${dst}")
        logTrace("POST body: ${json}")

        if (dst == 'switchmon') { parseSwitchmon(json) }
        else if (dst == 'powermon') { parsePowermon(json) }
        else if (dst == 'covermon') { parseCovermon(json) }
        else if (dst == 'lightmon') { parseLightmon(json) }
        else if (dst == 'temperature') { parseTemperature(json) }
        else if (dst == 'humidity') { parseHumidity(json) }
        else if (dst == 'battery') { parseBattery(json) }
        else if (dst == 'smoke') { parseSmoke(json) }
        else if (dst == 'illuminance') { parseIlluminance(json) }
        else if (dst == 'input_push') { parseInputPush(json) }
        else if (dst == 'input_double') { parseInputDouble(json) }
        else if (dst == 'input_long') { parseInputLong(json) }
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