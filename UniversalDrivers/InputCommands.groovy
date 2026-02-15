// ╔══════════════════════════════════════════════════════════════╗
// ║  Input Button Event Parsing                                  ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses input button push notifications from Shelly device.
 * Processes JSON with dst:"input_push" and sends a pushed event.
 * JSON format: [dst:input_push, result:[input:0:[id:0, state:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseInputPush(Map json) {
  logDebug("parseInputPush() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseInputPush: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('input:')) {
        if (value instanceof Map) {
          Integer inputId = value.id != null ? (value.id as Integer) : 0
          Integer buttonNumber = inputId + 1
          logInfo("Input ${inputId} button pushed (button ${buttonNumber})")
          sendEvent(name: "pushed", value: buttonNumber, isStateChange: true,
            descriptionText: "Button ${buttonNumber} was pushed")
        }
      }
    }
  } catch (Exception e) {
    logError("parseInputPush exception: ${e.message}")
  }
}

/**
 * Parses input button double-push notifications from Shelly device.
 * Processes JSON with dst:"input_double" and sends a doubleTapped event.
 * JSON format: [dst:input_double, result:[input:0:[id:0, state:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseInputDouble(Map json) {
  logDebug("parseInputDouble() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseInputDouble: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('input:')) {
        if (value instanceof Map) {
          Integer inputId = value.id != null ? (value.id as Integer) : 0
          Integer buttonNumber = inputId + 1
          logInfo("Input ${inputId} button double-pushed (button ${buttonNumber})")
          sendEvent(name: "doubleTapped", value: buttonNumber, isStateChange: true,
            descriptionText: "Button ${buttonNumber} was double-tapped")
        }
      }
    }
  } catch (Exception e) {
    logError("parseInputDouble exception: ${e.message}")
  }
}

/**
 * Parses input button long-push notifications from Shelly device.
 * Processes JSON with dst:"input_long" and sends a held event.
 * JSON format: [dst:input_long, result:[input:0:[id:0, state:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseInputLong(Map json) {
  logDebug("parseInputLong() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseInputLong: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('input:')) {
        if (value instanceof Map) {
          Integer inputId = value.id != null ? (value.id as Integer) : 0
          Integer buttonNumber = inputId + 1
          logInfo("Input ${inputId} button held (button ${buttonNumber})")
          sendEvent(name: "held", value: buttonNumber, isStateChange: true,
            descriptionText: "Button ${buttonNumber} was held")
        }
      }
    }
  } catch (Exception e) {
    logError("parseInputLong exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Input Button Event Parsing                              ║
// ╚══════════════════════════════════════════════════════════════╝
