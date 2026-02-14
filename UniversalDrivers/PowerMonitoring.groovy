// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands and Parsing                       ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses power monitoring notifications from Shelly device.
 * Processes JSON with dst:"powermon" and updates power/energy attributes.
 * JSON format: [dst:powermon, result:[switch:0:[aenergy:[total:76207], apower:0, current:0, id:0, voltage:120.8]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parsePowermon(Map json) {
  logDebug("parsePowermon() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parsePowermon: No result data in JSON")
      return
    }

    // Iterate over component entries (e.g., "switch:0", "em:0", etc.)
    result.each { key, value ->
      if (value instanceof Map) {
        Integer componentId = value.id

        // Extract power monitoring values
        if (value.voltage != null) {
          BigDecimal voltage = value.voltage as BigDecimal
          sendEvent(name: "voltage", value: voltage, unit: "V", descriptionText: "Voltage is ${voltage}V")
          logDebug("Voltage: ${voltage}V")
        }

        if (value.current != null) {
          BigDecimal current = value.current as BigDecimal
          sendEvent(name: "amperage", value: current, unit: "A", descriptionText: "Current is ${current}A")
          logDebug("Current: ${current}A")
        }

        if (value.apower != null) {
          BigDecimal power = value.apower as BigDecimal
          sendEvent(name: "power", value: power, unit: "W", descriptionText: "Power is ${power}W")
          logDebug("Power: ${power}W")
        }

        if (value.aenergy?.total != null) {
          BigDecimal energy = value.aenergy.total as BigDecimal
          sendEvent(name: "energy", value: energy, unit: "Wh", descriptionText: "Energy is ${energy}Wh")
          logDebug("Energy: ${energy}Wh")
        }

        logInfo("Component ${key} power monitoring updated: ${value.apower}W, ${value.voltage}V, ${value.current}A")

        // TODO: For multi-component devices, may need to send component events
      }
    }
  } catch (Exception e) {
    logError("parsePowermon exception: ${e.message}")
  }
}

/**
 * Resets energy monitoring counters.
 * Called when resetEnergyMonitors command is executed.
 */
void resetEnergyMonitors() {
  logDebug("resetEnergyMonitors() called")
  // Forward command to parent app for execution
  parent?.componentResetEnergyMonitors(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Power Monitoring Commands and Parsing                   ║
// ╚══════════════════════════════════════════════════════════════╝
