


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
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Driver Lifecycle and Configuration                       ║
// ╚══════════════════════════════════════════════════════════════╝