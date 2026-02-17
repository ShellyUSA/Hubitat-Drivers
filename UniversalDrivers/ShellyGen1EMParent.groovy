/**
 * Shelly Gen1 EM Parent
 *
 * Parent driver for Gen 1 energy meter Shelly devices.
 * Examples: Shelly EM (2 channels), Shelly 3EM (3 channels)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates EM children per channel in initialize()
 *   - All data comes via polling (Gen 1 EM has no useful action URLs)
 *   - Parent aggregates total power/energy across channels
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Gen1 EM Parent', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PowerMeter'
    capability 'EnergyMeter'
    capability 'CurrentMeter'
    capability 'VoltageMeasurement'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}



// ╔══════════════════════════════════════════════════════════════╗
// ║  Lifecycle                                                    ║
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
  reconcileChildDevices()
}

void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.componentRefresh(device)
}

void reinitialize() {
  logDebug('reinitialize() called')
  parent?.reinitializeDevice(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Lifecycle                                                ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Device Management                                      ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Reconciles driver-level child devices for EM channels.
 * Expected: 2 children (Shelly EM) or 3 children (Shelly 3EM).
 */
void reconcileChildDevices() {
  String componentStr = device.getDataValue('components')
  if (!componentStr) {
    logWarn('No components data value found — skipping child reconciliation')
    return
  }

  List<String> components = componentStr.split(',').collect { it.trim() }

  // Build set of DNIs that SHOULD exist
  Set<String> desiredDnis = [] as Set
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer
    if (baseType != 'em') { return }
    desiredDnis.add("${device.deviceNetworkId}-${baseType}-${compId}".toString())
  }

  // Build set of DNIs that currently exist
  Set<String> existingDnis = [] as Set
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  logDebug("Child reconciliation: desired=${desiredDnis}, existing=${existingDnis}")

  // Remove orphaned children
  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  // Create missing children
  components.each { String comp ->
    if (!comp.contains(':')) { return }
    String baseType = comp.split(':')[0]
    Integer compId = comp.split(':')[1] as Integer

    if (baseType != 'em') { return }

    String childDni = "${device.deviceNetworkId}-${baseType}-${compId}"
    if (getChildDevice(childDni)) { return }

    String driverName = 'Shelly Autoconf Switch PM'
    String label = "${device.displayName} Channel ${compId}"
    try {
      def child = addChildDevice('ShellyUSA', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue("emId", compId.toString())
      logInfo("Created child: ${label} (${driverName})")
    } catch (Exception e) {
      logError("Failed to create child ${label}: ${e.message}")
    }
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Child Device Management                                  ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Event Routing (parse)                                        ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses incoming LAN messages. Gen 1 EM has no useful action URLs,
 * so this mainly handles unexpected callbacks gracefully.
 */
void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (shouldLogLevel('trace')) { parent?.componentLogParsedMessage(device, msg) }
    if (msg?.status != null) { return }
    logDebug("Received LAN message (EM devices use polling for data)")
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Event Routing                                            ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Status Distribution (Refresh)                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes polled Gen 1 status to EM children and parent aggregate.
 * Called by app after polling GET /status on the Gen 1 device.
 *
 * @param deviceStatus Map of normalized component statuses (em:0, em:1, etc.)
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal maxVoltage = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!key.startsWith('em:') || !(v instanceof Map)) { return }

    Integer componentId = key.split(':')[1] as Integer
    Map data = v as Map

    List<Map> events = []
    if (data.act_power != null) {
      BigDecimal power = data.act_power as BigDecimal
      events.add([name: 'power', value: power, unit: 'W'])
      totalPower += power
    }
    if (data.voltage != null) {
      BigDecimal voltage = data.voltage as BigDecimal
      events.add([name: 'voltage', value: voltage, unit: 'V'])
      if (voltage > maxVoltage) { maxVoltage = voltage }
    }
    if (data.current != null) {
      BigDecimal current = data.current as BigDecimal
      events.add([name: 'amperage', value: current, unit: 'A'])
      totalCurrent += current
    }
    if (data.total_act_energy != null) {
      BigDecimal energyKwh = (data.total_act_energy as BigDecimal) / 1000.0
      events.add([name: 'energy', value: energyKwh, unit: 'kWh'])
      totalEnergy += energyKwh
    }

    // Route to child
    String childDni = "${device.deviceNetworkId}-em-${componentId}"
    def child = getChildDevice(childDni)
    if (child) {
      events.each { Map evt -> child.sendEvent(evt) }
      child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
    }
  }

  // Parent aggregate
  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Status Distribution                                      ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                              ║
// ╚══════════════════════════════════════════════════════════════╝

String loggingLabel() {
  return "${device.displayName}"
}

private Boolean shouldLogLevel(String messageLevel) {
  if (messageLevel == 'error') { return true }
  else if (messageLevel == 'warn') { return ['warn', 'info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'info') { return ['info', 'debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'debug') { return ['debug', 'trace'].contains(settings.logLevel) }
  else if (messageLevel == 'trace') { return settings.logLevel == 'trace' }
  return false
}

void logError(message) { log.error "${loggingLabel()}: ${message}" }
void logWarn(message) { log.warn "${loggingLabel()}: ${message}" }
void logInfo(message) { if (shouldLogLevel('info')) { log.info "${loggingLabel()}: ${message}" } }
void logDebug(message) { if (shouldLogLevel('debug')) { log.debug "${loggingLabel()}: ${message}" } }
void logTrace(message) { if (shouldLogLevel('trace')) { log.trace "${loggingLabel()}: ${message}" } }

@CompileStatic
void logJson(Map message) {
  if (shouldLogLevel('trace')) {
    logTrace(JsonOutput.prettyPrint(JsonOutput.toJson(message)))
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Logging Helpers                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports And Fields                                           ║
// ╚══════════════════════════════════════════════════════════════╝
import groovy.transform.CompileStatic
import groovy.json.JsonOutput
import groovy.transform.Field
// ╔══════════════════════════════════════════════════════════════╗
// ║  END Imports And Fields                                       ║
// ╚══════════════════════════════════════════════════════════════╝
