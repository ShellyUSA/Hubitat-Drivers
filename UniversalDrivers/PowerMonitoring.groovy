// ╔══════════════════════════════════════════════════════════════╗
// ║  Power Monitoring Commands                                   ║
// ╚══════════════════════════════════════════════════════════════╝

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
// ║  END Power Monitoring Commands                               ║
// ╚══════════════════════════════════════════════════════════════╝
