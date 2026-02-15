// ╔══════════════════════════════════════════════════════════════╗
// ║  Light / Dimmer Commands                                     ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns the light on.
 * Delegates to parent app's componentLightOn handler.
 */
void on() {
  logDebug("on() called")
  parent?.componentLightOn(device)
}

/**
 * Turns the light off.
 * Delegates to parent app's componentLightOff handler.
 */
void off() {
  logDebug("off() called")
  parent?.componentLightOff(device)
}

/**
 * Sets the light brightness level.
 * Delegates to parent app's componentSetLevel handler.
 *
 * @param level Brightness level (0 to 100)
 * @param duration Optional transition duration in seconds
 */
void setLevel(BigDecimal level, BigDecimal duration = null) {
  logDebug("setLevel(${level}, ${duration}) called")
  parent?.componentSetLevel(device, level as Integer, duration != null ? (duration * 1000) as Integer : null)
}

/**
 * Starts a gradual level change in the specified direction.
 * Delegates to parent app's componentStartLevelChange handler.
 *
 * @param direction Direction of level change ("up" or "down")
 */
void startLevelChange(String direction) {
  logDebug("startLevelChange(${direction}) called")
  parent?.componentStartLevelChange(device, direction)
}

/**
 * Stops an in-progress level change.
 * Delegates to parent app's componentStopLevelChange handler.
 */
void stopLevelChange() {
  logDebug("stopLevelChange() called")
  parent?.componentStopLevelChange(device)
}

/**
 * Parses light monitoring notifications from Shelly device.
 * Processes JSON with dst:"lightmon" and updates switch + level attributes.
 * JSON format: [dst:lightmon, result:[light:0:[id:0, output:true, brightness:75]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseLightmon(Map json) {
  logDebug("parseLightmon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseLightmon: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('light:')) {
        if (value instanceof Map) {
          Integer lightId = value.id
          Boolean output = value.output

          if (output != null) {
            String switchState = output ? "on" : "off"
            logInfo("Light ${lightId} state changed to: ${switchState}")
            sendEvent(name: "switch", value: switchState,
              descriptionText: "Switch turned ${switchState}")
          }

          if (value.brightness != null) {
            Integer brightness = value.brightness as Integer
            sendEvent(name: "level", value: brightness, unit: "%",
              descriptionText: "Level is ${brightness}%")
            logDebug("Light ${lightId} brightness: ${brightness}%")
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseLightmon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Light / Dimmer Commands                                 ║
// ╚══════════════════════════════════════════════════════════════╝
