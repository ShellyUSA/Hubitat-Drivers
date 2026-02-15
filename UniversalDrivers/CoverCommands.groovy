// ╔══════════════════════════════════════════════════════════════╗
// ║  Cover Commands                                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Opens the window shade / cover.
 * Delegates to parent app's componentOpen handler.
 */
void open() {
  logDebug("open() called")
  parent?.componentOpen(device)
}

/**
 * Closes the window shade / cover.
 * Delegates to parent app's componentClose handler.
 */
void close() {
  logDebug("close() called")
  parent?.componentClose(device)
}

/**
 * Sets the cover position to a specific value.
 * Delegates to parent app's componentSetPosition handler.
 *
 * @param position Target position (0 = closed, 100 = open)
 */
void setPosition(BigDecimal position) {
  logDebug("setPosition(${position}) called")
  parent?.componentSetPosition(device, position as Integer)
}

/**
 * Stops any in-progress cover movement.
 * Delegates to parent app's componentStop handler.
 */
void stopPositionChange() {
  logDebug("stopPositionChange() called")
  parent?.componentStop(device)
}

/**
 * Parses cover monitoring notifications from Shelly device.
 * Processes JSON with dst:"covermon" and updates windowShade + position attributes.
 * JSON format: [dst:covermon, result:[cover:0:[id:0, state:open, current_pos:100, apower:0, voltage:120.8, current:0]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseCovermon(Map json) {
  logDebug("parseCovermon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseCovermon: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (key.toString().startsWith('cover:')) {
        if (value instanceof Map) {
          Integer coverId = value.id
          String shellyState = value.state

          // Map Shelly cover state to Hubitat WindowShade values
          if (shellyState != null) {
            String shadeState
            switch (shellyState) {
              case 'open':
                shadeState = 'open'
                break
              case 'closed':
                shadeState = 'closed'
                break
              case 'opening':
                shadeState = 'opening'
                break
              case 'closing':
                shadeState = 'closing'
                break
              case 'stopped':
                shadeState = 'partially open'
                break
              case 'calibrating':
                shadeState = 'unknown'
                break
              default:
                shadeState = 'unknown'
            }
            logInfo("Cover ${coverId} state changed to: ${shadeState}")
            sendEvent(name: "windowShade", value: shadeState,
              descriptionText: "Window shade is ${shadeState}")
          }

          // Update position attribute
          if (value.current_pos != null) {
            Integer position = value.current_pos as Integer
            sendEvent(name: "position", value: position, unit: "%",
              descriptionText: "Position is ${position}%")
            logDebug("Cover ${coverId} position: ${position}%")
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseCovermon exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Cover Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝
