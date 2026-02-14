// ╔══════════════════════════════════════════════════════════════╗
// ║  Configure Commands                                           ║
// ╚══════════════════════════════════════════════════════════════╝
void configure() {
  logDebug("configure() called")
  if(!settings.logLevel) {
    logWarn("No log level set, defaulting to 'debug'")
    device.updateSetting('logLevel', 'debug')
  }
}
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Configure Commands                                       ║
// ╚══════════════════════════════════════════════════════════════╝