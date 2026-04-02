/**
 * Shelly Autoconf Presence G4 Parent
 *
 * Parent driver for Shelly Presence G4.
 * Exposes aggregate occupancy and illuminance on the parent device and
 * creates one child device per PresenceZone component.
 *
 * Version: 1.0.0
 */

metadata {
  definition(name: 'Shelly Autoconf Presence G4 Parent', namespace: 'ShellyDeviceManager', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'MotionSensor'
    capability 'PresenceSensor'
    capability 'IlluminanceMeasurement'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'

    command 'reinitializeDevice'
    command 'tiltCalibrate'
    command 'startLiveTrack'

    attribute 'illumination', 'string'
    attribute 'numObjects', 'number'
    attribute 'lastUpdated', 'string'
  }
}

preferences {
  input name: 'logLevel', type: 'enum', title: 'Logging Level',
    options: ['trace':'Trace', 'debug':'Debug', 'info':'Info', 'warn':'Warning'],
    defaultValue: 'debug', required: true
}

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

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
  reconcileChildDevices()
  refresh()
}

void configure() {
  logDebug('configure() called')
  parent?.componentConfigure(device)
}

void refresh() {
  logDebug('refresh() called')
  parent?.parentRefresh(device)
}

void reinitializeDevice() {
  logDebug('reinitializeDevice() called')
  parent?.reinitializeDevice(device)
}

void tiltCalibrate() {
  logInfo('Starting tilt calibration')
  parent?.parentSendCommand(device, 'Presence.TiltCalibrate', [:])
}

void startLiveTrack() {
  logInfo('Starting live track window')
  parent?.parentSendCommand(device, 'Presence.LiveTrack', [:])
}

void componentPresenceZoneRefresh(def childDevice) {
  logDebug("componentPresenceZoneRefresh() called by ${childDevice?.displayName}")
  parent?.parentRefresh(device)
}

void reconcileChildDevices() {
  List<Integer> desiredZoneIds = getTrackedPresenceZoneIds()
  Set<String> desiredDnis = desiredZoneIds.collect { Integer zoneId ->
    buildZoneDni(device.deviceNetworkId, zoneId)
  } as Set<String>

  Set<String> existingDnis = [] as Set<String>
  getChildDevices()?.each { child -> existingDnis.add(child.deviceNetworkId) }

  existingDnis.each { String dni ->
    if (!desiredDnis.contains(dni)) {
      def child = getChildDevice(dni)
      if (child) {
        logInfo("Removing orphaned child: ${child.displayName} (${dni})")
        deleteChildDevice(dni)
      }
    }
  }

  desiredZoneIds.each { Integer zoneId ->
    String childDni = buildZoneDni(device.deviceNetworkId, zoneId)
    initializeZoneSnapshot(zoneId)
    if (getChildDevice(childDni)) { return }

    String label = zoneId == 200 ? "${device.displayName} Main Zone" : "${device.displayName} Zone ${zoneId}"
    try {
      def child = addChildDevice('ShellyDeviceManager', 'Shelly Autoconf Presence Zone', childDni, [name: label, label: label])
      child.updateDataValue('componentType', 'presencezone')
      child.updateDataValue('zoneId', zoneId.toString())
      logInfo("Created child: ${label}")
    } catch (Exception e) {
      logError("Failed to create child ${label}: ${e.message}")
    }
  }
}

void parse(String description) {
  try {
    Map msg = parseLanMessage(description)
    if (msg?.status != null) { return }

    checkAndUpdateSourceIp(msg)

    if (msg?.body) {
      handlePostWebhook(msg)
    }
  } catch (Exception e) {
    logError("Error parsing LAN message: ${e.message}")
  }
}

private void handlePostWebhook(Map msg) {
  try {
    Map json = new JsonSlurper().parseText(msg.body as String) as Map
    String dst = json?.dst?.toString()
    if (!dst) {
      logTrace('POST webhook: no dst in body')
      return
    }

    if (dst == 'presencezone') {
      Integer zoneId = readComponentId(json, 'cid')
      if (zoneId == null) {
        logDebug('POST webhook: presencezone event missing cid')
        return
      }
      applyZoneReport(zoneId, json)
      updateAggregateEvents()
      sendLastUpdatedEvent()
      return
    }

    if (dst == 'illuminance') {
      applyIlluminanceReport(json)
      sendLastUpdatedEvent()
      return
    }

    logDebug("Unhandled POST webhook dst=${dst}")
  } catch (Exception e) {
    logDebug("POST webhook parse error: ${e.message}")
  }
}

void distributeStatus(Map deviceStatus) {
  if (!deviceStatus) { return }

  syncTrackedComponentsFromStatus(deviceStatus)

  deviceStatus.each { Object keyObj, Object valueObj ->
    String key = keyObj.toString()
    if (!key.contains(':') || !(valueObj instanceof Map)) { return }

    String baseType = key.split(':')[0]
    Integer componentId = parseComponentId(key)
    Map statusData = valueObj as Map

    if (baseType == 'presencezone' && componentId != null) {
      applyZoneReport(componentId, statusData)
    } else if (baseType == 'illuminance') {
      applyIlluminanceReport(statusData)
    }
  }

  updateAggregateEvents()
  sendLastUpdatedEvent()
}

private void applyZoneReport(Integer zoneId, Map payload) {
  ensureZoneTracked(zoneId)

  Map zoneSnapshot = initializeZoneSnapshot(zoneId)
  if (payload.containsKey('state')) {
    zoneSnapshot.state = coerceBoolean(payload.state)
  }
  if (payload.containsKey('numObjects')) {
    zoneSnapshot.numObjects = coerceInteger(payload.numObjects)
  } else if (payload.containsKey('num_objects')) {
    zoneSnapshot.numObjects = coerceInteger(payload.num_objects)
  }

  String childDni = buildZoneDni(device.deviceNetworkId, zoneId)
  def child = getChildDevice(childDni)
  if (!child) {
    logDebug("No Presence Zone child found for ${childDni}")
    return
  }

  if (zoneSnapshot.state != null) {
    String presenceValue = zoneSnapshot.state ? 'present' : 'not present'
    child.sendEvent(name: 'presence', value: presenceValue,
      descriptionText: "${child.displayName} is ${presenceValue}")
  }

  Boolean motionActive = deriveMotionActive(coerceBoolean(zoneSnapshot.state), coerceInteger(zoneSnapshot.numObjects))
  child.sendEvent(name: 'motion', value: motionActive ? 'active' : 'inactive',
    descriptionText: "${child.displayName} motion is ${motionActive ? 'active' : 'inactive'}")

  if (zoneSnapshot.numObjects != null) {
    Integer numObjects = coerceInteger(zoneSnapshot.numObjects)
    child.sendEvent(name: 'numObjects', value: numObjects,
      descriptionText: "${child.displayName} objects detected: ${numObjects}")
  }

  child.sendEvent(name: 'lastUpdated', value: formatTimestamp())
}

private void applyIlluminanceReport(Map payload) {
  if (payload.containsKey('lux')) {
    Integer lux = coerceInteger(payload.lux)
    if (lux != null) {
      sendEvent(name: 'illuminance', value: lux, unit: 'lux', descriptionText: "Illuminance is ${lux} lux")
    }
  }

  if (payload.illumination != null) {
    sendEvent(name: 'illumination', value: payload.illumination.toString(),
      descriptionText: "Illumination is ${payload.illumination}")
  }
}

// The parent represents whole-device occupancy, so it rolls up all known zones:
// presence = any zone present, motion = any zone with active objects, numObjects = max zone count.
private void updateAggregateEvents() {
  Map zoneCache = getZoneStatusCache()
  Boolean anyPresent = false
  Boolean anyMotion = false
  Integer maxObjects = 0

  zoneCache.each { String zoneId, Map zoneSnapshot ->
    Boolean zonePresent = zoneSnapshot?.state instanceof Boolean ? (zoneSnapshot.state as Boolean) : null
    Integer zoneObjects = coerceInteger(zoneSnapshot?.numObjects)

    if (zonePresent == true) {
      anyPresent = true
    }
    if (deriveMotionActive(zonePresent, zoneObjects)) {
      anyMotion = true
    }
    if (zoneObjects != null && zoneObjects > maxObjects) {
      maxObjects = zoneObjects
    }
  }

  sendEvent(name: 'presence', value: anyPresent ? 'present' : 'not present',
    descriptionText: "${device.displayName} is ${anyPresent ? 'present' : 'not present'}")
  sendEvent(name: 'motion', value: anyMotion ? 'active' : 'inactive',
    descriptionText: "${device.displayName} motion is ${anyMotion ? 'active' : 'inactive'}")
  sendEvent(name: 'numObjects', value: maxObjects,
    descriptionText: "${device.displayName} max objects detected: ${maxObjects}")
}

private void sendLastUpdatedEvent() {
  sendEvent(name: 'lastUpdated', value: formatTimestamp())
}

private void ensureZoneTracked(Integer zoneId) {
  String zoneComponent = "presencezone:${zoneId}"
  List<String> components = getTrackedComponents()
  if (!components.contains(zoneComponent)) {
    components.add(zoneComponent)
    device.updateDataValue('components', normalizeTrackedComponents(components).join(','))
    reconcileChildDevices()
  }
  initializeZoneSnapshot(zoneId)
}

private void syncTrackedComponentsFromStatus(Map deviceStatus) {
  List<String> desiredComponents = []
  deviceStatus.each { Object keyObj, Object valueObj ->
    String key = keyObj.toString()
    String baseType = key.contains(':') ? key.split(':')[0] : key
    if (baseType == 'presencezone' || baseType == 'illuminance') {
      desiredComponents.add(key)
    }
  }

  List<String> normalizedDesired = normalizeTrackedComponents(desiredComponents)
  List<String> normalizedCurrent = normalizeTrackedComponents(getTrackedComponents())
  if (normalizedDesired == normalizedCurrent) { return }

  device.updateDataValue('components', normalizedDesired.join(','))
  pruneZoneSnapshots(normalizedDesired.findAll { String comp -> comp.startsWith('presencezone:') }.collect { String comp ->
    parseComponentId(comp)
  }.findAll { Integer zoneId -> zoneId != null } as Set<Integer>)
  reconcileChildDevices()
}

private List<String> getTrackedComponents() {
  String componentStr = device.getDataValue('components') ?: ''
  if (!componentStr) { return [] }
  return componentStr.split(',').collect { it.trim() }.findAll { it }
}

private List<Integer> getTrackedPresenceZoneIds() {
  return getTrackedComponents()
    .findAll { String component -> component.startsWith('presencezone:') }
    .collect { String component -> parseComponentId(component) }
    .findAll { Integer zoneId -> zoneId != null }
    .sort()
}

private List<String> normalizeTrackedComponents(List<String> components) {
  List<String> normalized = components.findAll { it != null }
    .collect { it.trim() }
    .findAll { it }
    .unique()

  normalized.sort { String left, String right ->
    String leftType = left.contains(':') ? left.split(':')[0] : left
    String rightType = right.contains(':') ? right.split(':')[0] : right
    if (leftType == rightType) {
      return (parseComponentId(left) ?: 0) <=> (parseComponentId(right) ?: 0)
    }
    return leftType <=> rightType
  }

  return normalized
}

private Map getZoneStatusCache() {
  if (!(state.zoneStatusCache instanceof Map)) {
    state.zoneStatusCache = [:]
  }
  return state.zoneStatusCache as Map
}

private Map initializeZoneSnapshot(Integer zoneId) {
  Map zoneCache = getZoneStatusCache()
  String zoneKey = zoneId.toString()
  if (!(zoneCache[zoneKey] instanceof Map)) {
    zoneCache[zoneKey] = [state: null, numObjects: null]
  }
  return zoneCache[zoneKey] as Map
}

private void pruneZoneSnapshots(Set<Integer> validZoneIds) {
  Map zoneCache = getZoneStatusCache()
  List<String> staleKeys = zoneCache.keySet().findAll { String zoneKey ->
    !(zoneKey.isInteger() && validZoneIds.contains(zoneKey as Integer))
  } as List<String>
  staleKeys.each { String zoneKey -> zoneCache.remove(zoneKey) }
}

@CompileStatic
private static Integer parseComponentId(String componentKey) {
  if (!componentKey?.contains(':')) { return null }
  String[] parts = componentKey.split(':')
  if (parts.length < 2) { return null }
  try {
    return Integer.parseInt(parts[1])
  } catch (Exception ignored) {
    return null
  }
}

@CompileStatic
private static Integer readComponentId(Map payload, String key) {
  return payload?.containsKey(key) ? coerceInteger(payload[key]) : null
}

@CompileStatic
private static Integer coerceInteger(Object value) {
  if (value == null) { return null }
  if (value instanceof Number) { return ((Number)value).intValue() }
  try {
    return Integer.parseInt(value.toString())
  } catch (Exception ignored) {
    return null
  }
}

@CompileStatic
private static Boolean coerceBoolean(Object value) {
  if (value == null) { return null }
  if (value instanceof Boolean) { return value as Boolean }
  String stringValue = value.toString()
  if (stringValue == 'true') { return true }
  if (stringValue == 'false') { return false }
  return null
}

@CompileStatic
private static Boolean deriveMotionActive(Boolean stateValue, Integer numObjects) {
  if (numObjects != null) { return numObjects > 0 }
  if (stateValue != null) { return stateValue }
  return false
}

@CompileStatic
private static String buildZoneDni(String parentDni, Integer zoneId) {
  return "${parentDni}-presencezone-${zoneId}".toString()
}

private String formatTimestamp() {
  return new Date().format('yyyy-MM-dd HH:mm:ss')
}

@CompileStatic
private static String convertHexToIP(String hex) {
  if (!hex || hex.length() != 8) { return null }
  return [Integer.parseInt(hex[0..1], 16),
          Integer.parseInt(hex[2..3], 16),
          Integer.parseInt(hex[4..5], 16),
          Integer.parseInt(hex[6..7], 16)].join('.')
}

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
