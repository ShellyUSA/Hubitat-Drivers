/**
 * Shelly Gen1 EM Parent
 *
 * Parent driver for Gen 1 energy meter Shelly devices.
 * Examples: Shelly EM (2 channels), Shelly 3EM (3 channels)
 *
 * Architecture:
 *   - App creates parent device with component data values
 *   - Parent creates EM children per channel in initialize()
 *   - Relay (contactor) is controlled directly on the parent via Switch capability
 *   - All EM data comes via polling (Gen 1 EM has no useful action URLs)
 *   - Parent aggregates total power/energy across channels
 *
 * Version: 1.1.0
 */

metadata {
  definition(name: 'Shelly Gen1 EM Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'TemperatureMeasurement'
    capability 'PowerMeter'
    capability 'EnergyMeter'
    capability 'CurrentMeter'
    capability 'VoltageMeasurement'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitialize'
    attribute 'lastUpdated', 'string'
    attribute 'powerFactor', 'number'
    attribute 'reactivePower', 'number'
    attribute 'energyReturned', 'number'
  }
}

preferences {
  // -- Relay Settings (synced to device via /settings/relay/0) --
  input name: 'defaultState', type: 'enum', title: 'Relay Power-On Default State',
    options: ['off':'Off', 'on':'On', 'restore':'Restore Last'],
    defaultValue: 'off', required: false
  input name: 'autoOffTime', type: 'decimal', title: 'Auto-Off Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false
  input name: 'autoOnTime', type: 'decimal', title: 'Auto-On Timer (seconds, 0 = disabled)',
    defaultValue: 0, range: '0..86400', required: false

  // -- Device Configuration (synced to device via /settings) --
  input name: 'ledStatusDisable', type: 'bool', title: 'Disable LED status indicator',
    defaultValue: false, required: false

  // -- Logging --
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

/**
 * Handles preference updates. Re-initializes and pushes relay/device settings
 * to the physical device via the parent app.
 */
void updated() {
  logDebug('Parent device updated')
  initialize()
  relaySwitchSettings()
  relayDeviceSettings()
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
// ║  Switch Commands (Relay/Contactor Control)                    ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Turns on relay:0 (contactor) via the parent app.
 */
void on() {
  logDebug('on() called')
  parent?.componentOn(device)
}

/**
 * Turns off relay:0 (contactor) via the parent app.
 */
void off() {
  logDebug('off() called')
  parent?.componentOff(device)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Switch Commands                                          ║
// ╚══════════════════════════════════════════════════════════════╝



// ╔══════════════════════════════════════════════════════════════╗
// ║  Settings Write-Back                                          ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Gathers relay switch settings and sends them to the parent app
 * for relay to the device via /settings/relay/0.
 */
private void relaySwitchSettings() {
  Map switchSettings = [:]
  if (settings.defaultState != null) { switchSettings.defaultState = settings.defaultState as String }
  if (settings.autoOffTime != null) { switchSettings.autoOffTime = settings.autoOffTime as BigDecimal }
  if (settings.autoOnTime != null) { switchSettings.autoOnTime = settings.autoOnTime as BigDecimal }
  if (switchSettings) {
    logDebug("Relaying switch settings to parent: ${switchSettings}")
    parent?.componentUpdateSwitchSettings(device, switchSettings)
  }
}

/**
 * Gathers device-level settings (LED control) and sends them to the parent app
 * for relay to the device via /settings.
 */
private void relayDeviceSettings() {
  Map deviceSettings = [:]
  if (settings.ledStatusDisable != null) {
    deviceSettings.led_status_disable = settings.ledStatusDisable ? 'true' : 'false'
  }
  if (deviceSettings) {
    logDebug("Relaying device settings to parent: ${deviceSettings}")
    parent?.componentUpdateGen1Settings(device, deviceSettings)
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Settings Write-Back                                      ║
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

    String driverName = 'Shelly Autoconf EM'
    String label = "${device.displayName} Channel ${compId}"
    try {
      def child = addChildDevice('ShellyDeviceManager', driverName, childDni, [name: label, label: label])
      child.updateDataValue('componentType', baseType)
      child.updateDataValue('emId', compId.toString())
      // Propagate parent data values for forward compatibility
      String ip = device.getDataValue('ipAddress') ?: ''
      if (ip) { child.updateDataValue('ipAddress', ip) }
      String gen1Type = device.getDataValue('gen1Type') ?: ''
      if (gen1Type) { child.updateDataValue('gen1Type', gen1Type) }
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
    checkAndUpdateSourceIp(msg)
    logDebug("Received LAN message (EM devices use polling for data)")
  } catch (Exception e) {
    logDebug("parse() error: ${e.message}")
  }
}

/**
 * Converts a hex-encoded IP address string to dotted-decimal format.
 *
 * @param hex The 8-character hex string (e.g., "C0A80164")
 * @return Dotted-decimal IP (e.g., "192.168.1.100"), or null if invalid
 */
@CompileStatic
private static String convertHexToIP(String hex) {
  if (!hex || hex.length() != 8) { return null }
  return [Integer.parseInt(hex[0..1], 16),
          Integer.parseInt(hex[2..3], 16),
          Integer.parseInt(hex[4..5], 16),
          Integer.parseInt(hex[6..7], 16)].join('.')
}

/**
 * Checks the source IP of an incoming LAN message against the stored device IP.
 * If different, updates the device data value and notifies the parent app.
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 */
private void checkAndUpdateSourceIp(Map msg) {
  String hexIp = msg?.ip
  if (!hexIp) { return }
  String sourceIp = convertHexToIP(hexIp)
  if (!sourceIp) { return }
  String storedIp = device.getDataValue('ipAddress')
  if (!storedIp || sourceIp == storedIp) { return }
  logWarn("Device IP changed: ${storedIp} -> ${sourceIp}")
  device.updateDataValue('ipAddress', sourceIp)
  parent?.componentNotifyIpChanged(device, storedIp, sourceIp)
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
 * Handles em:N channels (routed to children), switch:N relay state,
 * and temperature:N internal device temperature.
 *
 * @param deviceStatus Map of normalized component statuses (em:0, em:1, switch:0, temperature:0, etc.)
 */
void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  BigDecimal totalPower = 0
  BigDecimal totalEnergy = 0
  BigDecimal totalCurrent = 0
  BigDecimal totalReactivePower = 0
  BigDecimal totalReturnedEnergy = 0
  BigDecimal maxVoltage = 0
  BigDecimal weightedPfSum = 0
  BigDecimal pfWeightDenom = 0

  deviceStatus.each { k, v ->
    String key = k.toString()
    if (!(v instanceof Map)) { return }
    Map data = v as Map

    // EM channel data → route to children and aggregate on parent
    if (key.startsWith('em:')) {
      Integer componentId = key.split(':')[1] as Integer

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
      if (data.pf != null) {
        BigDecimal pf = data.pf as BigDecimal
        events.add([name: 'powerFactor', value: pf])
        // Weight power factor by absolute active power for meaningful average
        BigDecimal absPower = (data.act_power != null) ? Math.abs(data.act_power as BigDecimal) : 0
        weightedPfSum += pf * absPower
        pfWeightDenom += absPower
      }
      if (data.reactive != null) {
        BigDecimal reactive = data.reactive as BigDecimal
        events.add([name: 'reactivePower', value: reactive, unit: 'VAR'])
        totalReactivePower += reactive
      }
      if (data.total_act_ret_energy != null) {
        BigDecimal retKwh = (data.total_act_ret_energy as BigDecimal) / 1000.0
        events.add([name: 'energyReturned', value: retKwh, unit: 'kWh'])
        totalReturnedEnergy += retKwh
      }

      // Route to child
      String childDni = "${device.deviceNetworkId}-em-${componentId}"
      def child = getChildDevice(childDni)
      if (child) {
        events.each { Map evt -> child.sendEvent(evt) }
        child.sendEvent(name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss'))
      }
    }

    // Relay state → parent switch attribute
    if (key.startsWith('switch:')) {
      String switchState = data.output ? 'on' : 'off'
      sendEvent(name: 'switch', value: switchState, descriptionText: "Relay is ${switchState}")
    }

    // Internal device temperature → parent temperature attribute
    if (key.startsWith('temperature:') && !key.contains(':100')) {
      String scale = location?.temperatureScale ?: 'F'
      BigDecimal temp = (scale == 'C' && data.tC != null) ? data.tC as BigDecimal : data.tF as BigDecimal
      if (temp != null) {
        sendEvent(name: 'temperature', value: temp, unit: "\u00B0${scale}")
      }
    }
  }

  // Parent aggregate totals
  sendEvent(name: 'power', value: totalPower, unit: 'W')
  sendEvent(name: 'energy', value: totalEnergy, unit: 'kWh')
  sendEvent(name: 'amperage', value: totalCurrent, unit: 'A')
  sendEvent(name: 'voltage', value: maxVoltage, unit: 'V')
  sendEvent(name: 'reactivePower', value: totalReactivePower, unit: 'VAR')
  sendEvent(name: 'energyReturned', value: totalReturnedEnergy, unit: 'kWh')

  // Weighted average power factor (weighted by absolute active power per channel)
  if (pfWeightDenom > 0) {
    BigDecimal avgPf = weightedPfSum / pfWeightDenom
    sendEvent(name: 'powerFactor', value: avgPf.setScale(2, BigDecimal.ROUND_HALF_UP))
  }

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
