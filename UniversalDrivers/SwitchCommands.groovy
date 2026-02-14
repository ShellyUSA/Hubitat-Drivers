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
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝