


// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands.                                            ║
// ╚══════════════════════════════════════════════════════════════╝
void on() {
  logDebug("on() called")
  // Implement the logic to turn the switch on
  sendEvent(name: 'switch', value: 'on', descriptionText: 'Switch turned on')
}

void off() {
  logDebug("off() called")
  // Implement the logic to turn the switch off
  sendEvent(name: 'switch', value: 'off', descriptionText: 'Switch turned off')
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                         ║
// ╚══════════════════════════════════════════════════════════════╝