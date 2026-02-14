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
    logDebug("Parsed LAN message: status=${msg?.status}, headers=${msg?.headers?.keySet()}")

    if (msg?.status == 200) {
      if (msg.body) {
        try {
          def json = new groovy.json.JsonSlurper().parseText(msg.body)
          logDebug("Parsed JSON body: ${json}")
          // TODO: Process parsed JSON and update device attributes
        } catch (Exception jsonEx) {
          logWarn("Could not parse JSON body: ${jsonEx.message}")
        }
      }
    } else {
      logWarn("HTTP error response: ${msg?.status}")
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                       ║
// ╚══════════════════════════════════════════════════════════════╝