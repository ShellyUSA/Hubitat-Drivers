// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Device Lifecycle                                     ║
// ║  Minimal lifecycle for multi-component parent devices.       ║
// ║  All LAN traffic is forwarded to the parent app for routing  ║
// ║  to the appropriate child device.                            ║
// ╚══════════════════════════════════════════════════════════════╝
void installed() {
  logDebug('Parent device installed')
  initialize()
}

void updated() {
  logDebug('Parent device updated')
  initialize()
}

void initialize() {
  logDebug('Parent device initialized')
  parent?.componentInitialize(device)
}

void configure() {
  logDebug('Parent device configure() called')
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('Parent device refresh() called')
  parent?.componentRefresh(device)
}

/**
 * Receives all LAN messages on port 39501 (webhooks, script notifications).
 * Forwards the raw description to the parent app for routing to children.
 *
 * @param description The raw LAN message description
 */
void parse(String description) {
  logTrace("Parent parse() received message")
  parent?.componentParse(device, description)
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Device Lifecycle                                 ║
// ╚══════════════════════════════════════════════════════════════╝
