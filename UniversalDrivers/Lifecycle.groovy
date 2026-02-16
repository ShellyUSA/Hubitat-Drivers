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

  // Extract path from request line: "GET /webhook/switchmon/0 HTTP/1.1" -> "/webhook/switchmon/0"
  String pathAndQuery = requestLine.split(' ')[1]

  // Parse path segments: /webhook/<dst>/<cid>[?key=val&...]
  if (pathAndQuery.startsWith('/webhook/')) {
    String webhookPath = pathAndQuery.substring('/webhook/'.length())
    String queryString = null
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) {
      queryString = webhookPath.substring(qMarkIdx + 1)
      webhookPath = webhookPath.substring(0, qMarkIdx)
    }
    String[] segments = webhookPath.split('/')
    if (segments.length >= 2) {
      Map params = [dst: segments[0], cid: segments[1]]
      if (queryString) {
        queryString.split('&').each { String pair ->
          String[] kv = pair.split('=', 2)
          if (kv.length == 2) {
            params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
          }
        }
      }
      logTrace("parseWebhookQueryParams: parsed path params: ${params}")
      return params
    }
    logTrace("parseWebhookQueryParams: not enough path segments in '${pathAndQuery}'")
    return null
  }

  // Fallback: try query string parsing for backwards compatibility
  int qIdx = pathAndQuery.indexOf('?')
  if (qIdx >= 0) {
    Map params = [:]
    pathAndQuery.substring(qIdx + 1).split('&').each { String pair ->
      String[] kv = pair.split('=', 2)
      if (kv.length == 2) {
        params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
      }
    }
    logTrace("parseWebhookQueryParams: parsed query params: ${params}")
    return params
  }

  logTrace("parseWebhookQueryParams: no webhook path or query string in '${pathAndQuery}'")
  return null
}

/**
 * Routes parsed webhook GET query parameters to appropriate event handlers.
 * Handles all event types: switch, cover, temperature, humidity, battery,
 * smoke, illuminance, and input button/toggle events.
 *
 * Supports both new discrete dst values (e.g., switch_on, cover_open,
 * input_toggle_on, smoke_alarm) and legacy combined dst values (e.g.,
 * switchmon, covermon, input_toggle, smoke) with parameter-based state.
 *
 * @param params The parsed query parameters including dst and optional state fields
 */
private void routeWebhookParams(Map params) {
  switch (params.dst) {
    // Discrete switch webhooks
    case 'switch_on':
      sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
      logInfo('Switch state changed to: on')
      break
    case 'switch_off':
      sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
      logInfo('Switch state changed to: off')
      break

    // Legacy combined switch webhook -- state is in params.output
    case 'switchmon':
      if (params.output != null) {
        String switchState = params.output == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: switchState,
          descriptionText: "Switch turned ${switchState}")
        logInfo("Switch state changed to: ${switchState}")
      }
      break

    // Discrete cover webhooks
    case 'cover_open':
      sendEvent(name: 'windowShade', value: 'open',
        descriptionText: 'Window shade is open')
      logInfo('Cover state changed to: open')
      if (params.pos != null) {
        sendEvent(name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%")
      }
      break
    case 'cover_closed':
      sendEvent(name: 'windowShade', value: 'closed',
        descriptionText: 'Window shade is closed')
      logInfo('Cover state changed to: closed')
      if (params.pos != null) {
        sendEvent(name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%")
      }
      break
    case 'cover_stopped':
      sendEvent(name: 'windowShade', value: 'partially open',
        descriptionText: 'Window shade is partially open')
      logInfo('Cover state changed to: partially open')
      if (params.pos != null) {
        sendEvent(name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%")
      }
      break
    case 'cover_opening':
      sendEvent(name: 'windowShade', value: 'opening',
        descriptionText: 'Window shade is opening')
      logInfo('Cover state changed to: opening')
      if (params.pos != null) {
        sendEvent(name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%")
      }
      break
    case 'cover_closing':
      sendEvent(name: 'windowShade', value: 'closing',
        descriptionText: 'Window shade is closing')
      logInfo('Cover state changed to: closing')
      if (params.pos != null) {
        sendEvent(name: 'position', value: params.pos as Integer, unit: '%',
          descriptionText: "Position is ${params.pos}%")
      }
      break
    case 'cover_calibrating':
      sendEvent(name: 'windowShade', value: 'unknown',
        descriptionText: 'Window shade is calibrating')
      logInfo('Cover state changed to: calibrating')
      break

    // Legacy combined cover webhook -- state is in params.state
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

    // Discrete smoke webhook
    case 'smoke_alarm':
      sendEvent(name: 'smoke', value: 'detected',
        descriptionText: 'Smoke detected')
      logInfo('Smoke: detected')
      break

    // Legacy smoke webhook -- state is in params.alarm
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

    // Discrete input toggle webhooks
    case 'input_toggle_on':
      sendEvent(name: 'switch', value: 'on',
        descriptionText: 'Switch turned on (input toggle)')
      logInfo('Switch state changed to: on (input toggle)')
      break
    case 'input_toggle_off':
      sendEvent(name: 'switch', value: 'off',
        descriptionText: 'Switch turned off (input toggle)')
      logInfo('Switch state changed to: off (input toggle)')
      break

    // Legacy combined input toggle webhook -- state is in params.state
    case 'input_toggle':
      if (params.state != null) {
        String inputState = params.state == 'true' ? 'on' : 'off'
        sendEvent(name: 'switch', value: inputState,
          descriptionText: "Switch turned ${inputState} (input toggle)")
        logInfo("Switch state changed to: ${inputState} (input toggle)")
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

    // Power monitoring via GET (from powermonitoring.js)
    case 'powermon':
      if (params.voltage != null) {
        BigDecimal voltage = params.voltage as BigDecimal
        sendEvent(name: 'voltage', value: voltage, unit: 'V',
          descriptionText: "Voltage is ${voltage}V")
        logDebug("Voltage: ${voltage}V")
      }
      if (params.current != null) {
        BigDecimal current = params.current as BigDecimal
        sendEvent(name: 'amperage', value: current, unit: 'A',
          descriptionText: "Current is ${current}A")
        logDebug("Current: ${current}A")
      }
      if (params.apower != null) {
        BigDecimal power = params.apower as BigDecimal
        sendEvent(name: 'power', value: power, unit: 'W',
          descriptionText: "Power is ${power}W")
        logDebug("Power: ${power}W")
      }
      if (params.aenergy != null) {
        BigDecimal energyWh = params.aenergy as BigDecimal
        BigDecimal energyKwh = energyWh / 1000
        sendEvent(name: 'energy', value: energyKwh, unit: 'kWh',
          descriptionText: "Energy is ${energyKwh}kWh")
        logDebug("Energy: ${energyKwh}kWh (${energyWh}Wh from device)")
      }
      if (params.freq != null) {
        BigDecimal freq = params.freq as BigDecimal
        sendEvent(name: 'frequency', value: freq, unit: 'Hz',
          descriptionText: "Frequency is ${freq}Hz")
      }
      logInfo("Power monitoring updated: ${params.apower ?: 0}W, ${params.voltage ?: 0}V, ${params.current ?: 0}A")
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