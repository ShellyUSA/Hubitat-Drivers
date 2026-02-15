/**
 * Shelly Autoconf 2x Switch Parent
 *
 * Pre-built parent driver for dual-switch Shelly devices without power monitoring.
 *
 * This is a parent device in a parent-child architecture. It receives all LAN
 * traffic (webhooks, script notifications) and forwards them to the parent app
 * for routing to child devices. It also processes notifications locally to
 * maintain an aggregate switch state and fire aggregate input button events.
 *
 * Child devices handle the per-component capabilities:
 *   - Shelly Autoconf Switch (for switch:0, switch:1)
 *   - Shelly Autoconf Input Button (for input:0, input:1)
 *
 * The parent provides aggregate on/off control (turns all switches on or off)
 * and configurable aggregation logic for switch state and input events.
 *
 * This driver is installed directly by ShellyDeviceManager, bypassing the modular
 * assembly pipeline.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf 2x Switch Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    //Attributes: switch - ENUM ["on", "off"]
    //Commands: on(), off()

    capability 'PushableButton'
    //Attributes: pushed - NUMBER, numberOfButtons - NUMBER

    capability 'DoubleTapableButton'
    //Attributes: doubleTapped - NUMBER

    capability 'HoldableButton'
    //Attributes: held - NUMBER

    capability 'Initialize'
    //Commands: initialize()

    capability 'Configuration'
    //Commands: configure()

    capability 'Refresh'
    //Commands: refresh()

    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
  input name: 'switchAggregation', type: 'enum', title: 'Parent Switch State',
    options: ['anyOn':'Any Switch On → Parent On', 'allOn':'All Switches On → Parent On'],
    defaultValue: 'anyOn', required: true
  input name: 'inputAggregation', type: 'enum', title: 'Parent Button Events',
    options: ['any':'Any Input → Fire Event', 'all':'All Inputs → Fire Event'],
    defaultValue: 'any', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Device Lifecycle                                     ║
// ║  Receives LAN traffic and forwards to the parent app for     ║
// ║  child routing. Also processes locally for aggregate state.   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Called when driver is first installed on a device.
 * Delegates to initialize() for initial setup.
 */
void installed() {
  logDebug('Parent device installed')
  initialize()
}

/**
 * Called when device settings are saved.
 * Delegates to initialize() to apply updated configuration.
 */
void updated() {
  logDebug('Parent device updated')
  initialize()
}

/**
 * Initializes the parent device by delegating to the parent app.
 * Also recomputes aggregate switch state in case the aggregation
 * preference was changed.
 */
void initialize() {
  logDebug('Parent device initialized')
  parent?.componentInitialize(device)
  updateParentSwitchState()
}

/**
 * Configures the parent device by delegating to the parent app.
 */
void configure() {
  logDebug('Parent device configure() called')
  parent?.componentConfigure(device)
}

/**
 * Refreshes the parent device state by delegating to the parent app.
 */
void refresh() {
  logDebug('Parent device refresh() called')
  parent?.componentRefresh(device)
}

/**
 * Receives all LAN messages (webhooks, script notifications).
 * Forwards the raw description to the parent app for routing to children,
 * then processes locally for parent-level aggregate state updates.
 *
 * @param description The raw LAN message description
 */
void parse(String description) {
  logTrace("Parent parse() received message")

  // Forward to app for child device routing
  parent?.componentParse(device, description)

  // Process locally for parent-level aggregation
  try {
    Map msg = parseLanMessage(description)
    if (msg?.status != null) { return }
    if (!msg?.body) { return }

    def json = new groovy.json.JsonSlurper().parseText(msg.body)
    String dst = json?.dst

    if (dst == 'switchmon') { parseSwitchmon(json) }
    else if (dst == 'input_push') { handleInputEvent(json, 'pushed') }
    else if (dst == 'input_double') { handleInputEvent(json, 'doubleTapped') }
    else if (dst == 'input_long') { handleInputEvent(json, 'held') }
  } catch (Exception e) {
    logDebug("Parent parse local processing error: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Device Lifecycle                                 ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Switch Commands and Aggregation                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns ON all child switch devices by delegating to the parent app.
 */
void on() {
  logDebug('Parent on() called — turning on all child switches')
  parent?.componentOnAll(device)
}

/**
 * Turns OFF all child switch devices by delegating to the parent app.
 */
void off() {
  logDebug('Parent off() called — turning off all child switches')
  parent?.componentOffAll(device)
}

/**
 * Parses switch monitoring notifications and updates the aggregate
 * parent switch state based on the switchAggregation preference.
 *
 * @param json The parsed JSON notification with dst:"switchmon"
 */
void parseSwitchmon(Map json) {
  logDebug("parseSwitchmon() called with: ${json}")

  Map result = json?.result
  if (!result) { return }

  // Update tracked switch states from the notification
  Map switchStates = state.switchStates ?: [:]
  result.each { key, value ->
    if (key.toString().startsWith('switch:') && value instanceof Map && value.output != null) {
      switchStates[key.toString()] = value.output
    }
  }
  state.switchStates = switchStates

  updateParentSwitchState()
}

/**
 * Computes and sends the aggregate parent switch state based on
 * tracked child switch states and the switchAggregation preference.
 * <ul>
 *   <li>anyOn: parent is "on" if any child switch is on</li>
 *   <li>allOn: parent is "on" only when all child switches are on</li>
 * </ul>
 */
private void updateParentSwitchState() {
  Map switchStates = state.switchStates ?: [:]
  if (switchStates.isEmpty()) { return }

  String mode = settings.switchAggregation ?: 'anyOn'
  Boolean parentOn
  if (mode == 'allOn') {
    parentOn = switchStates.values().every { it == true }
  } else {
    parentOn = switchStates.values().any { it == true }
  }

  String newState = parentOn ? 'on' : 'off'
  String currentState = device.currentValue('switch')
  if (currentState != newState) {
    sendEvent(name: 'switch', value: newState, descriptionText: "Parent switch is ${newState}")
    logInfo("Parent switch state: ${newState} (mode: ${mode})")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands and Aggregation                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Input Button Event Aggregation                              ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Processes an input button event and fires on the parent based on
 * the inputAggregation preference.
 * <ul>
 *   <li>any: fires immediately when any child input fires, with the
 *       input's button number (input:0 → button 1, input:1 → button 2)</li>
 *   <li>all: fires only when all known inputs have fired the same event
 *       type within a 2-second window, with button number 1</li>
 * </ul>
 *
 * @param json The parsed JSON notification
 * @param eventName The Hubitat event name: "pushed", "doubleTapped", or "held"
 */
void handleInputEvent(Map json, String eventName) {
  Map result = json?.result
  if (!result) { return }

  String mode = settings.inputAggregation ?: 'any'

  result.each { key, value ->
    if (key.toString().startsWith('input:') && value instanceof Map) {
      Integer inputId = value.id != null ? (value.id as Integer) : 0

      // Track known input count and update numberOfButtons
      Integer knownInputs = state.knownInputCount ?: 0
      if (inputId + 1 > knownInputs) {
        state.knownInputCount = inputId + 1
        sendEvent(name: 'numberOfButtons', value: inputId + 1)
      }

      if (mode == 'any') {
        Integer buttonNumber = inputId + 1
        sendEvent(name: eventName, value: buttonNumber, isStateChange: true,
          descriptionText: "Button ${buttonNumber} was ${eventName}")
        logInfo("Parent ${eventName}: button ${buttonNumber}")
      } else {
        trackAndFireIfAllInputs(eventName, key.toString())
      }
    }
  }
}

/**
 * Tracks an input event for "all" aggregation mode. Records the event
 * with a timestamp, then checks if all known inputs have fired the same
 * event type within a 2-second window. Fires on the parent only when
 * the threshold is met.
 *
 * @param eventName The event type ("pushed", "doubleTapped", "held")
 * @param inputKey The input component key (e.g., "input:0")
 */
private void trackAndFireIfAllInputs(String eventName, String inputKey) {
  Map pending = state.pendingInputEvents ?: [:]
  Map eventPending = (pending[eventName] ?: [:]) as Map
  eventPending[inputKey] = now()
  pending[eventName] = eventPending
  state.pendingInputEvents = pending

  // Check if all known inputs have fired within the time window
  Integer knownInputs = state.knownInputCount ?: 0
  if (knownInputs < 2) { return }

  Long cutoff = now() - 2000
  Integer recentCount = 0
  eventPending.each { k, v ->
    if (v instanceof Number && ((Number)v).toLong() > cutoff) { recentCount++ }
  }

  if (recentCount >= knownInputs) {
    sendEvent(name: eventName, value: 1, isStateChange: true,
      descriptionText: "All inputs ${eventName}")
    logInfo("Parent ${eventName}: all ${knownInputs} inputs fired")

    // Clear tracking for this event type
    pending.remove(eventName)
    state.pendingInputEvents = pending
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Input Button Event Aggregation                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Returns the display label used in log messages.
 *
 * @return The device display name
 */
String loggingLabel() {
  return "${device.displayName}"
}

/**
 * Determines whether a log message at the given level should be emitted.
 *
 * @param messageLevel The level of the log message (error, warn, info, debug, trace)
 * @return true if the message should be logged
 */
private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') {
    return true
  } else if (messageLevel == 'warn') {
    return settings.logLevel == 'warn'
  } else if (messageLevel == 'info') {
    return ['warn', 'info'].contains(settings.logLevel)
  } else if (messageLevel == 'debug') {
    return ['warn', 'info', 'debug'].contains(settings.logLevel)
  } else if (messageLevel == 'trace') {
    return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel)
  }
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    String prettyJson = prettyJson(message)
    logTrace(prettyJson)
  }
}

@CompileStatic
void logErrorJson(Map message) {
  logError(prettyJson(message))
}

@CompileStatic
void logInfoJson(Map message) {
  String prettyJson = prettyJson(message)
  logInfo(prettyJson)
}

/**
 * Formats a Map as pretty-printed JSON.
 *
 * @param jsonInput The map to format
 * @return Pretty-printed JSON string
 */
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                         ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                          ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                      ║
// ╚══════════════════════════════════════════════════════════════╝
