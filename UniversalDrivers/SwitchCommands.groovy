// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands.                                            ║
// ╚══════════════════════════════════════════════════════════════╝
void on() {
  logDebug("on() called")
  // Forward command to parent app for execution
  parent?.componentOn(device)
}

void off() {
  logDebug("off() called")
  // Forward command to parent app for execution
  parent?.componentOff(device)
}

/**
 * Parses switch monitoring notifications from Shelly device.
 * Processes JSON with dst:"switchmon" and updates device state.
 * JSON format: [dst:switchmon, result:[switch:0:[id:0, output:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseSwitchmon(Map json) {
  logDebug("parseSwitchmon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseSwitchmon: No result data in JSON")
      return
    }

    // Iterate over switch entries (e.g., "switch:0", "switch:1", etc.)
    result.each { key, value ->
      if (key.toString().startsWith('switch:')) {
        if (value instanceof Map) {
          Integer switchId = value.id
          Boolean output = value.output

          if (output != null) {
            String switchState = output ? "on" : "off"
            logInfo("Switch ${switchId} state changed to: ${switchState}")

            // Send event to update device state
            sendEvent(name: "switch", value: switchState, descriptionText: "Switch turned ${switchState}")

            // TODO: For multi-switch devices, may need to send component events
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseSwitchmon exception: ${e.message}")
  }
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝