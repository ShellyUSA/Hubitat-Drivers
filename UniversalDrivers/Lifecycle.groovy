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

      // Extract request path from headers
      def headersList = msg.headers?.keySet()
      def requestLine = headersList?.find { it.startsWith('GET ') || it.startsWith('POST ') }

      if (requestLine) {
        logInfo("Shelly event: ${requestLine}")

        // Parse the request path to determine event type
        // Example: "GET /switch.active_power_measurement/apower/0/0 HTTP/1.1"
        if (requestLine.contains('/switch.')) {
          // Switch event notification
          logDebug("Switch state change notification received")
          // TODO: Query device for current state
        }
      }

      if (msg.body) {
        try {
          def json = new groovy.json.JsonSlurper().parseText(msg.body)
          logDebug("Request body JSON: ${json}")

          // Process event data based on destination type
          if (json?.dst == "switchmon") {
            parseSwitchmon(json)
          } else if (json?.dst == "powermon") {
            parsePowermon(json)
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
// ║  END Driver Lifecycle and Configuration                       ║
// ╚══════════════════════════════════════════════════════════════╝