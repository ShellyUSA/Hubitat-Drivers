@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, Boolean> foundDevices = new java.util.concurrent.ConcurrentHashMap<String, Boolean>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()

definition(
    name: "Shelly mDNS Discovery",
    namespace: "ShellyUSA",
    author: "Daniel Winks",
    description: "Discover Shelly WiFi devices using Hubitat mDNS discovery",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true,
    singleThreaded: true
)

preferences {
    page(name: "mainPage", install: true, uninstall: true)
    page(name: "createDevicesPage")
}

/**
 * Renders the main discovery page for the Shelly mDNS Discovery app.
 * Initializes state variables, starts discovery if not running, and displays
 * the discovery timer, discovered devices list, and logging controls.
 *
 * @return Map containing the dynamic page definition
 */
Map mainPage() {
    if (!state.discoveredShellys) { state.discoveredShellys = [:] }
    if (!state.recentLogs) { state.recentLogs = [] }

    // Requirement: scanning should start when app page is opened.
    if (!state.discoveryRunning) {
        startDiscovery(true)
    }

    Integer remainingSecs = getRemainingDiscoverySeconds()

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        section() {
            if (state.discoveryRunning) {
                paragraph "<b><span class='app-state-${app.id}-discoveryTimer'>Discovery time remaining: ${remainingSecs} seconds</span></b>"
            } else {
                paragraph "<b>Discovery has stopped.</b>"
            }
            input 'btnExtendScan', 'button', title: 'Extend Scan (120s)', submitOnChange: true
        }

        section() {
            // Combine section label and device-count onto a single line
            paragraph "<b>Discovered Shelly Devices</b> <span class='app-state-${app.id}-shellyDiscoveredCount'>Found Devices (${state.discoveredShellys.size()}):</span>"

            // Device list remains below (updated via app-state binding)
            paragraph "<span class='app-state-${app.id}-shellyDiscovered'>${getFoundShellys() ?: 'No Shelly devices discovered yet.'}</span>"

            href "createDevicesPage", title: "Create Devices", description: "Select discovered Shelly devices to create"
        }

        section("Logging", hideable: true) {
                // Overall logging level (controls what gets written to Hubitat logs)
            List<String> levelOrder = ['trace','debug','info','warn','error','off']
            Map<String,String> levelLabels = [
                trace: 'Trace',
                debug: 'Debug',
                info:  'Info',
                warn:  'Warn',
                error: 'Error',
                off:   'Off'
            ]

            // Normalize overall level (handle stored value or label)
            String rawOverall = (settings?.logLevel ?: 'debug').toString()
            String overallLevel = levelOrder.contains(rawOverall.toLowerCase()) ? rawOverall.toLowerCase() : (levelLabels.find { k, v -> v.equalsIgnoreCase(rawOverall) }?.key ?: 'debug')

            Map<String,String> levelOptions = levelOrder.collectEntries { lvl -> [(levelLabels[lvl]): lvl] }
            input name: 'logLevel', type: 'enum', title: 'Overall logging level', options: levelOptions, defaultValue: overallLevel, submitOnChange: true

            // Display level (what appears in the app UI) — limited to "X and above" relative to overall
            int overallIdx = Math.max(0, levelOrder.indexOf(overallLevel))
            List<String> allowedDisplay = (overallIdx >= 0 && overallIdx < levelOrder.size()) ? levelOrder[overallIdx..-1] : levelOrder
            Map<String,String> displayOptions = allowedDisplay.collectEntries { lvl -> [(levelLabels[lvl]): lvl] }

            // Normalize stored display setting and enforce it is within allowedDisplay.
            // Check both settings and state — settings may be null briefly after removeSetting().
            String rawDisplay = settings?.displayLogLevel?.toString()
            if (!rawDisplay || rawDisplay == 'null') { rawDisplay = state.displayLogLevel?.toString() }
            String storedDisplay = rawDisplay ? (levelOrder.contains(rawDisplay.toLowerCase()) ? rawDisplay.toLowerCase() : (levelLabels.find { k, v -> v.equalsIgnoreCase(rawDisplay) }?.key)) : null

            String validatedDisplay = (storedDisplay && storedDisplay in allowedDisplay) ? storedDisplay : overallLevel

            // Only intervene when the stored value is OUT of the allowed list (more verbose → less verbose change)
            if (storedDisplay && !(storedDisplay in allowedDisplay)) {
                // Clear the stale setting so defaultValue takes effect on this render pass
                app.removeSetting('displayLogLevel')
                state.displayLogLevel = validatedDisplay
                pruneDisplayedLogs(validatedDisplay)
                // Persist the validated value AFTER the page finishes rendering
                state.pendingDisplayLevel = validatedDisplay
                runInMillis(200, 'applyPendingDisplayLevel')
            }

            input name: 'displayLogLevel', type: 'enum', title: 'App page display log level (X and above)', options: displayOptions, defaultValue: validatedDisplay, submitOnChange: true

            String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
            String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap; font-size:12px; line-height:1.2;'>${recentPayload}</pre>"
        }
    }
}

/**
 * Handles button click events from the app UI.
 * Processes actions for extending scan, creating devices, and getting device info.
 *
 * @param buttonName The name of the button that was clicked
 */
void appButtonHandler(String buttonName) {
    if (buttonName == 'btnExtendScan') { extendDiscovery(120) }

    if (buttonName == 'btnCreateDevices') {
        List<String> selected = settings?.selectedToCreate ?: []
        if (!(selected instanceof List) && selected) { selected = [selected] }
        int count = (selected instanceof List) ? selected.size() : 0
        List<String> labels = selected.collect { ip -> state.discoveredShellys[ip]?.name ?: ip }
        logDebug("Would create ${count} device(s): ${labels.join(', ')}")
        appendLog('info', "Would create ${count} device(s): ${labels.join(', ')}")
    }

    if (buttonName == 'btnGetDeviceInfo') {
        List<String> selected = settings?.selectedToCreate ?: []
        if (!(selected instanceof List) && selected) { selected = [selected] }
        if (!selected || selected.size() == 0) {
            appendLog('warn', 'Get Device Info: no devices selected')
            return
        }
        selected.each { String ipKey ->
            fetchAndStoreDeviceInfo(ipKey)
        }
    }
}

/**
 * Called when the app is first installed.
 * Initializes the app by calling {@link #initialize()}.
 */
void installed() { initialize() }

/**
 * Called when the app settings are updated.
 * Detects logging level changes, prunes displayed logs accordingly,
 * updates state with new settings, and reinitializes the app.
 */
void updated() {
    // Detect logging-level changes and prune displayed logs immediately
    String oldDisplay = state.displayLogLevel
    String oldOverall = state.logLevel

    String newOverall = settings?.logLevel ?: (oldOverall ?: 'debug')
    String newDisplay = settings?.displayLogLevel ?: newOverall

    if (oldDisplay != newDisplay || oldOverall != newOverall) {
        pruneDisplayedLogs(newDisplay)
    }

    state.displayLogLevel = newDisplay
    state.logLevel = newOverall

    unsubscribe()
    unschedule()
    initialize()
}

/**
 * Called when the app is uninstalled.
 * Stops discovery, unregisters mDNS listeners, and cleans up subscriptions and schedules.
 */
void uninstalled() {
    stopDiscovery()
    // Only unregister listeners when app is uninstalled
    try {
        unregisterMDNSListener('_shelly._tcp')
        unregisterMDNSListener('_http._tcp')
    } catch (Exception e) {
        logTrace("Could not unregister mDNS listeners: ${e.message}")
    }
    unsubscribe()
    unschedule()
}

/**
 * Renders the device creation page where users can select discovered Shelly devices to create.
 * Builds a list of available devices, tracks selected devices, and provides
 * buttons for creating devices and getting detailed device information.
 *
 * @return Map containing the dynamic page definition
 */
Map createDevicesPage() {
    // Build options from discoveredShellys (keyed by IP) — use LinkedHashMap to preserve order
    LinkedHashMap<String,String> options = [:] as LinkedHashMap
    state.discoveredShellys.each { ip, info ->
        String label = "${info?.name ?: 'Shelly'} (${info?.ipAddress ?: ip})"
        options["${ip}"] = label
    }

    // Normalize selection so we can count safely
    List<String> selected = settings?.selectedToCreate ?: []
    if (!(selected instanceof List) && selected) { selected = [selected] }
    Integer selectedCount = (selected instanceof List) ? selected.size() : 0

    // Build human-readable labels for the selected devices
    List<String> selectedLabels = []
    if (selected instanceof List && selected.size() > 0) {
        selectedLabels = selected.collect { ip -> options[ip] ?: (state.discoveredShellys[ip]?.name ?: ip) }
    }

    String willCreateText
    if (!selectedLabels || selectedLabels.size() == 0) {
        willCreateText = "Will create 0 devices"
    } else {
        willCreateText = "Will create ${selectedLabels.size()} device${selectedLabels.size() == 1 ? '' : 's'}:\n" + selectedLabels.join('\n')
    }

    // Send event for live on-screen feedback (app-state-... class)
    app.sendEvent(name: 'willCreateDevices', value: willCreateText)

    logDebug("createDevicesPage: options.size=${options.size()}, selected=${selected}, labels=${selectedLabels}")

    dynamicPage(name: "createDevicesPage", title: "Create Shelly Devices", install: false, uninstall: false) {
        section("Select devices to create") {
            // Always show the discovered list for visibility
            paragraph "<b>Discovered devices (live):</b>"
            paragraph getFoundShellys() ?: 'No Shelly devices discovered yet.'

            if (!options || options.size() == 0) {
                // nothing more to show
            } else {
                input name: 'selectedToCreate', type: 'enum', title: 'Available discovered Shelly devices', options: options, multiple: true, required: false, defaultValue: settings?.selectedToCreate, submitOnChange: true
                paragraph "<span class='app-state-${app.id}-willCreateDevices'>${willCreateText}</span>"
                input 'btnCreateDevices', 'button', title: 'Create Devices', submitOnChange: true
                input 'btnGetDeviceInfo', 'button', title: 'Get Device Info', submitOnChange: true
            }
        }

        section {
            href "mainPage", title: "Back to discovery", description: ""
        }

        section("Logging", hideable: true) {
            String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
            String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap; font-size:12px; line-height:1.2;'>${recentPayload}</pre>"
        }
    }
}

/**
 * Initializes the app state and sets up mDNS discovery.
 * Initializes state variables for discovered devices and logs,
 * mirrors logging settings to state, subscribes to system start events,
 * and registers mDNS listeners.
 */
void initialize() {
    if (!state.discoveredShellys) { state.discoveredShellys = [:] }
    if (!state.recentLogs) { state.recentLogs = [] }
    if (state.discoveryRunning == null) { state.discoveryRunning = false }

    // Ensure state mirrors current settings for logging
    state.logLevel = settings?.logLevel ?: (state.logLevel ?: 'debug')
    state.displayLogLevel = settings?.displayLogLevel ?: state.logLevel

    // mDNS listeners must be registered on system startup per Hubitat docs
    subscribe(location, 'systemStart', 'systemStartHandler')

    // Also register now in case hub has been up for a while
    startMdnsDiscovery()
}

/**
 * Starts the mDNS discovery process for Shelly devices.
 * Optionally clears previously discovered devices, sets discovery state to running,
 * registers mDNS listeners, and schedules discovery processing and timer updates.
 *
 * @param resetFound If true, clears the list of previously discovered devices
 */
void startDiscovery(Boolean resetFound = false) {
    if (resetFound) {
        state.discoveredShellys = [:]
    }

    state.discoveryRunning = true
    state.discoveryEndTime = now() + (getDiscoveryDurationSeconds() * 1000L)

    logDebug("startDiscovery: starting discovery for ${getDiscoveryDurationSeconds()} seconds")

    // Re-register listeners to trigger fresh mDNS queries on the network
    startMdnsDiscovery()

    unschedule('stopDiscovery')
    unschedule('updateDiscoveryTimer')
    unschedule('updateRecentLogs')
    unschedule('processMdnsDiscovery')
    runIn(getDiscoveryDurationSeconds(), 'stopDiscovery')
    runIn(1, 'updateDiscoveryTimer')
    runIn(1, 'updateRecentLogs')
    // Give the hub 10 seconds after listener registration to collect mDNS responses
    runIn(10, 'processMdnsDiscovery')
}

/**
 * Extends the discovery period by the specified number of seconds.
 * If discovery is stopped, restarts it without clearing discovered devices.
 * Updates the discovery end time and reschedules the stop task.
 *
 * @param seconds Number of seconds to extend the discovery period
 */
void extendDiscovery(Integer seconds) {
    if (!state.discoveryRunning) {
        // Sonos-style: if stopped, start again without clearing discovered list.
        state.discoveryRunning = true
        runIn(1, 'updateDiscoveryTimer')
        runIn(2, 'processMdnsDiscovery')
    }

    Long currentEnd = state.discoveryEndTime ? (state.discoveryEndTime as Long) : now()
    Long newEnd = Math.max(currentEnd, now()) + (seconds * 1000L)
    state.discoveryEndTime = newEnd
    Integer totalRemaining = (Integer)(((newEnd) - now()) / 1000L)

    unschedule('stopDiscovery')
    runIn(totalRemaining, 'stopDiscovery')
    appendLog('info', "Discovery extended by ${seconds} seconds")
}

/**
 * Registers mDNS listeners for Shelly device discovery.
 * Registers listeners for both _shelly._tcp and _http._tcp service types
 * to discover Shelly devices on the local network.
 */
void startMdnsDiscovery() {
    try {
        registerMDNSListener('_shelly._tcp')
        logDebug('Registered mDNS listener: _shelly._tcp')
    } catch (Exception e) {
        logWarn("mDNS listener registration failed for _shelly._tcp: ${e.message}")
    }
    try {
        registerMDNSListener('_http._tcp')
        logDebug('Registered mDNS listener: _http._tcp')
    } catch (Exception e) {
        logWarn("mDNS listener registration failed for _http._tcp: ${e.message}")
    }
}

/**
 * Handles system startup events.
 * Re-registers mDNS listeners when the Hubitat hub restarts,
 * as mDNS listeners must be registered on each system start.
 *
 * @param evt The system start event
 */
void systemStartHandler(evt) {
    logDebug('System start detected, registering mDNS listeners')
    startMdnsDiscovery()
}

/**
 * Stops the discovery process.
 * Sets discovery state to not running, clears the discovery end time,
 * and unschedules all discovery-related tasks. Note that mDNS listeners
 * remain active to allow data accumulation.
 */
void stopDiscovery() {
    state.discoveryRunning = false
    state.discoveryEndTime = null

    unschedule('processMdnsDiscovery')
    unschedule('updateDiscoveryTimer')
    unschedule('updateRecentLogs')
    unschedule('stopDiscovery')

    // Do NOT unregister mDNS listeners - keep them active so data accumulates
    logDebug('Discovery stopped (mDNS listeners remain active)')
}

/**
 * Updates the discovery timer display in real-time.
 * Sends an event to the UI showing remaining discovery time and reschedules
 * itself every second while discovery is active. Stops automatically when
 * the timer reaches zero or discovery is no longer running.
 */
void updateDiscoveryTimer() {
    if (!state.discoveryRunning || !state.discoveryEndTime) {
        return
    }
    Integer remainingSecs = getRemainingDiscoverySeconds()

    // Send event for real-time browser update
    app.sendEvent(name: 'discoveryTimer', value: "Discovery time remaining: ${remainingSecs} seconds")

    // Continue scheduling if time remaining
    if (remainingSecs > 0) {
        runIn(1, 'updateDiscoveryTimer')
    }
}

/**
 * Updates the recent logs display in the UI.
 * Retrieves the most recent 10 log entries from state, reverses them
 * (most recent first), and sends them to the UI via an app event.
 * Reschedules itself every second while discovery is running to provide
 * real-time log updates.
 */
void updateRecentLogs() {
    // Send the most recent 10 log lines to the browser for the app-state binding
    String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
    String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
    app.sendEvent(name: 'recentLogs', value: recentPayload)

    // Continue updating once per second while discovery is running
    if (state.discoveryRunning) {
        runIn(1, 'updateRecentLogs')
    }
}

/**
 * Processes mDNS discovery by querying for Shelly devices on the network.
 * Retrieves mDNS entries for both _shelly._tcp and _http._tcp service types,
 * filters for Shelly devices, extracts device information (name, IP, port, generation,
 * firmware version), and stores discovered devices in state. Updates the UI with
 * discovery results and reschedules itself periodically while discovery is active.
 * <p>
 * Only processes entries that appear to be Shelly devices (based on server name,
 * gen field, or app field) and have valid IPv4 addresses. Tracks the timestamp
 * of each discovery to enable aging and cleanup of stale entries.
 */
void processMdnsDiscovery() {
    if (!state.discoveryRunning) {
        logDebug('processMdnsDiscovery: discovery not running, returning')
        return
    }

    try {
        // getMDNSEntries requires the service type parameter per Hubitat docs
        List<Map<String,Object>> shellyEntries = getMDNSEntries('_shelly._tcp')
        List<Map<String,Object>> httpEntries = getMDNSEntries('_http._tcp')

        logDebug("processMdnsDiscovery: _shelly._tcp raw=${shellyEntries}, _http._tcp raw=${httpEntries}")
        logDebug("processMdnsDiscovery: _shelly._tcp returned ${shellyEntries?.size() ?: 0} entries, _http._tcp returned ${httpEntries?.size() ?: 0} entries")

        List allEntries = []
        if (shellyEntries) { allEntries.addAll(shellyEntries) }
        if (httpEntries) { allEntries.addAll(httpEntries) }

        if (!allEntries) {
            logDebug('processMdnsDiscovery: no mDNS entries found for either service type')
        } else {
            logDebug("processMdnsDiscovery: processing ${allEntries.size()} total mDNS entries")
            Integer beforeCount = state.discoveredShellys.size()
            allEntries.each { entry ->
                // Actual mDNS entry fields: server, port, ip4Addresses, ip6Addresses, gen, app, ver
                String server = (entry?.server ?: '') as String
                String ip4 = (entry?.ip4Addresses ?: '') as String
                Integer port = (entry?.port ?: 0) as Integer
                String gen = (entry?.gen ?: '') as String
                String deviceApp = (entry?.app ?: '') as String
                String ver = (entry?.ver ?: '') as String

                logDebug("mDNS entry: server=${server}, ip=${ip4}, port=${port}, gen=${gen}, app=${deviceApp}, ver=${ver}")

                // All entries from _shelly._tcp are Shelly devices
                // For _http._tcp entries, check if server name contains 'shelly'
                String serverLower = server.toLowerCase()
                Boolean looksShelly = serverLower.contains('shelly') || gen || deviceApp

                if (!looksShelly || !ip4) {
                    return
                }

                // Clean up server name (remove trailing dot and .local.)
                String deviceName = server.replaceAll(/\.local\.$/, '').replaceAll(/\.$/, '')

                String key = ip4
                Boolean isNewToState = !state.discoveredShellys.containsKey(key)
                Boolean alreadyLogged = foundDevices.containsKey(key)

                // Only log if this is a newly discovered device AND we haven't logged it yet this run
                if (isNewToState && !alreadyLogged) {
                    logDebug("Found NEW Shelly: ${deviceName} at ${ip4}:${port} (gen=${gen}, app=${deviceApp}, ver=${ver})")
                    foundDevices.put(key, true)
                }

                state.discoveredShellys[key] = [
                    name: deviceName ?: "Shelly ${ip4}",
                    ipAddress: ip4,
                    port: (port ?: 80),
                    gen: gen,
                    deviceApp: deviceApp,
                    ver: ver,
                    ts: now()
                ]
            }
            Integer afterCount = state.discoveredShellys.size()
            if (afterCount > beforeCount) {
                logDebug("Found ${afterCount - beforeCount} new Shelly device(s), total: ${afterCount}")
            }
            logDebug("Sending discovery events, total discovered: ${state.discoveredShellys.size()}")
            sendFoundShellyEvents()
        }
    } catch (Exception e) {
        logWarn("Error processing mDNS entries: ${e.message}")
    }

    if (state.discoveryRunning && getRemainingDiscoverySeconds() > 0) {
        runIn(getMdnsPollSeconds(), 'processMdnsDiscovery')
    }
}

/**
 * Formats the list of discovered Shelly devices for display in the UI.
 * Generates an HTML-formatted string with device information including name,
 * IP address, port, generation, application type, and firmware version.
 * Devices are sorted alphabetically by name.
 *
 * @return HTML-formatted string with one device per line separated by {@code <br/>} tags,
 *         or empty string if no devices have been discovered
 */
String getFoundShellys() {
    if (!state.discoveredShellys || state.discoveredShellys.size() == 0) { return '' }

    List<String> names = state.discoveredShellys.collect { k, v ->
        Map device = (Map)v
        String name = (device?.name ?: 'Shelly') as String
        String ip = (device?.ipAddress ?: 'n/a') as String
        Integer port = (device?.port ?: 80) as Integer
        String gen = (device?.gen ?: '') as String
        String deviceApp = (device?.deviceApp ?: '') as String
        String ver = (device?.ver ?: '') as String
        StringBuilder infoSb = new StringBuilder("${name} (${ip}:${port})")
        if (deviceApp || gen || ver) {
            List<String> extras = []
            if (deviceApp) { extras.add(deviceApp) }
            if (gen) { extras.add("Gen${gen}") }
            if (ver) { extras.add("v${ver}") }
            infoSb.append(' [').append(extras.join(', ')).append(']')
        }
        return infoSb.toString()
    }

    names.sort()

    // Return a single-line HTML string with explicit <br/> separators so there is no leading/trailing blank line
    return names.join('<br/>')
}

/**
 * Sends discovery events to update the UI with found devices.
 * Publishes two app events: one with the count of discovered devices
 * and another with the formatted list of all discovered devices.
 * These events are used by the UI to provide real-time feedback
 * during the discovery process.
 */
void sendFoundShellyEvents() {
    String countValue = "Found Devices (${state.discoveredShellys.size()}): "
    String devicesValue = getFoundShellys()
    logDebug("sendFoundShellyEvents: count='${countValue}', devices='${devicesValue}'")
    app.sendEvent(name: 'shellyDiscoveredCount', value: countValue)
    app.sendEvent(name: 'shellyDiscovered', value: devicesValue)
}

// ═══════════════════════════════════════════════════════════════
// Device Info / Config / Status Fetching (Gen2+ RPC over HTTP)
// ═══════════════════════════════════════════════════════════════

// Fetch comprehensive device info, config, and status for a discovered IP.
// Uses the existing command map helpers (shellyGetDeviceInfoCommand, etc.) and
// the shared `postCommandSync(command, uri)` helper to send JSON-RPC to a
// discovered device (targets an arbitrary IP instead of getBaseUriRpc()).
/**
 * Fetches comprehensive device information from a discovered Shelly device.
 * Queries the device for its info, configuration, and status via JSON-RPC commands
 * over HTTP. Stores all retrieved data in state for use in device creation and
 * driver determination. Also updates the UI with device details and invokes
 * driver determination logic.
 * <p>
 * Makes three RPC calls to the device:
 * <ul>
 *   <li>Shelly.GetDeviceInfo - device identity, model, firmware, auth status</li>
 *   <li>Shelly.GetConfig - device configuration settings</li>
 *   <li>Shelly.GetStatus - current device status including component states</li>
 * </ul>
 *
 * @param ipKey The IP address key identifying the device in discoveredShellys map
 */
private void fetchAndStoreDeviceInfo(String ipKey) {
    if (!ipKey) { return }
    Map device = state.discoveredShellys[ipKey]
    if (!device) {
        appendLog('warn', "Get Device Info: no discovered entry for ${ipKey}")
        return
    }

    String ip = (device.ipAddress ?: ipKey).toString()
    Integer port = (device.port ?: 80) as Integer
    String rpcUri = (port == 80) ? "http://${ip}/rpc" : "http://${ip}:${port}/rpc"

    logDebug("fetchAndStoreDeviceInfo: fetching from ${rpcUri}")
    appendLog('debug', "Getting device info from ${ip}")

    try {
        // Use shared helper: postCommandSync supports an optional URI parameter
        LinkedHashMap deviceInfoCmd = shellyGetDeviceInfoCommand(true, 'discovery')
        logDebug("fetchAndStoreDeviceInfo: sending Shelly.GetDeviceInfo command to ${rpcUri}")
        LinkedHashMap deviceInfoResp = postCommandSync(deviceInfoCmd, rpcUri)
        logDebug("fetchAndStoreDeviceInfo: received response: ${deviceInfoResp ? 'OK' : 'NULL'}")
        Map deviceInfo = (deviceInfoResp instanceof Map && deviceInfoResp.containsKey('result')) ? deviceInfoResp.result : deviceInfoResp
        if (!deviceInfo) {
            appendLog('warn', "No device info returned from ${ip} — device may be offline or not Gen2+")
            return
        }

        LinkedHashMap configCmd = shellyGetConfigCommand('discovery')
        LinkedHashMap deviceConfigResp = postCommandSync(configCmd, rpcUri)
        Map deviceConfig = (deviceConfigResp instanceof Map && deviceConfigResp.containsKey('result')) ? deviceConfigResp.result : deviceConfigResp

        LinkedHashMap statusCmd = shellyGetStatusCommand('discovery')
        LinkedHashMap deviceStatusResp = postCommandSync(statusCmd, rpcUri)
        Map deviceStatus = (deviceStatusResp instanceof Map && deviceStatusResp.containsKey('result')) ? deviceStatusResp.result : deviceStatusResp

        // Build a comprehensive merged record
        device.name = (deviceInfo.id ?: device.name ?: "Shelly ${ip}")
        if (deviceInfo.mac) { device.mac = deviceInfo.mac }
        if (deviceInfo.model) { device.model = deviceInfo.model }
        if (deviceInfo.app) { device.deviceApp = deviceInfo.app }
        if (deviceInfo.gen) { device.gen = deviceInfo.gen }
        if (deviceInfo.ver) { device.ver = deviceInfo.ver }
        if (deviceInfo.fw_id) { device.fw_id = deviceInfo.fw_id }
        if (deviceInfo.auth_en != null) { device.auth_en = deviceInfo.auth_en }
        if (deviceInfo.profile) { device.profile = deviceInfo.profile }

        // Store the full raw results for downstream device-creation logic
        device.deviceInfo = deviceInfo
        if (deviceConfig) { device.deviceConfig = deviceConfig }
        if (deviceStatus) { device.deviceStatus = deviceStatus }
        device.ts = now()

        state.discoveredShellys[ipKey] = device

        // Build a human-readable summary
        List<String> parts = []
        parts.add("model=${device.model ?: 'n/a'}")
        parts.add("gen=${device.gen ?: 'n/a'}")
        parts.add("ver=${device.ver ?: 'n/a'}")
        if (device.mac) { parts.add("mac=${device.mac}") }
        if (device.profile) { parts.add("profile=${device.profile}") }
        if (device.auth_en) { parts.add("auth=enabled") }
        if (deviceConfig) { parts.add("config=OK") }
        if (deviceStatus) { parts.add("status=OK") }
        appendLog('info', "Device info for ${device.name} (${ip}): ${parts.join(', ')}")

        logDebug("fetchAndStoreDeviceInfo: deviceInfo keys=${deviceInfo.keySet()}")
        if (deviceConfig) { logDebug("fetchAndStoreDeviceInfo: deviceConfig keys=${deviceConfig.keySet()}") }
        if (deviceStatus) { logDebug("fetchAndStoreDeviceInfo: deviceStatus keys=${deviceStatus.keySet()}") }


        sendFoundShellyEvents()
        determineDeviceDriver(deviceStatus)
    } catch (Exception e) {
        String errorMsg = e.message ?: e.toString() ?: e.class.simpleName
        appendLog('error', "Failed to get device info from ${ip}: ${errorMsg}")
        logDebug("fetchAndStoreDeviceInfo exception: ${e.class.name}: ${errorMsg}")
        if (e.stackTrace && e.stackTrace.length > 0) {
            logDebug("fetchAndStoreDeviceInfo stack trace (first 3 lines): ${e.stackTrace.take(3).join(' | ')}")
        }
    }
}

/**
 * Analyzes device status to determine the appropriate Hubitat driver.
 * Inspects the device status map for switches and inputs, logs the discovered
 * component counts, builds a list of Shelly components, and invokes driver
 * generation. Provides heuristic determination of device type based on the
 * combination of switches and inputs found.
 * <p>
 * Component detection:
 * <ul>
 *   <li>Switches: status keys starting with "switch"</li>
 *   <li>Inputs: status keys containing "input"</li>
 * </ul>
 * <p>
 * Device type heuristics:
 * <ul>
 *   <li>1 switch, 0 inputs: plug or basic relay</li>
 *   <li>1 switch, 1+ inputs: roller shutter or similar</li>
 *   <li>Multiple switches: multi-relay model</li>
 * </ul>
 *
 * @param deviceStatus Map containing the device status with component keys
 */
private void determineDeviceDriver(Map deviceStatus ) {
    // Placeholder for future device-type determination logic
    if (!deviceStatus) {
        logDebug("determineDeviceDriver: no deviceStatus provided, cannot determine capabilities")
        return
    }
    logDebug("determineDeviceDriver: deviceStatus keys=${deviceStatus?.keySet() ?: 'n/a'}")
    int switchesFound = deviceStatus.findAll { k, v -> k.toString().toLowerCase().startsWith('switch') }.size()
    int inputsFound = deviceStatus.findAll { k, v -> k.toString().toLowerCase().contains('input') }.size()

    // Log discovered device capabilities
    logInfo("Discovered device has switch(es): ${switchesFound}")
    logInfo("Discovered device has input(s): ${inputsFound}")

    // Build list of Shelly components found
    List<String> components = []
    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        if (key.startsWith('switch')) { components.add(k.toString()) }
        if (key.contains('input')) { components.add(k.toString()) }
    }

    // Generate driver for discovered components
    if (components.size() > 0) {
        String driverCode = generateHubitatDriver(components)
        logDebug("Generated driver code (${driverCode?.length() ?: 0} chars)")
    }

    if (switchesFound == 0 && inputsFound == 0) {
        logDebug("determineDeviceDriver: no switches or inputs found in status, cannot determine device type")
        return
    }
    if (switchesFound == 1 && inputsFound == 0) {
        logDebug("Device is likely a plug or basic relay device (1 switch, no inputs)")
        // Use ShellySingleSwitch or ShellySingleSwitchPM based on presence of power metering in status
    } else if (switchesFound == 1 && inputsFound >= 1) {
        logDebug("Device is likely a roller shutter or similar (1 switch, 1+ inputs)")
    } else if (switchesFound > 1) {
        logDebug("Device is likely a multi-relay model (multiple switches)")
    } else {
        logDebug("Device has an unexpected combination of switches and inputs, manual review may be needed")
    }

}

/**
 * Generates a Hubitat device driver based on discovered Shelly components.
 * Uses StringBuilder to construct the driver code incrementally. Currently
 * a placeholder implementation that will be expanded to generate complete
 * driver definitions with capabilities, attributes, commands, and component
 * handling logic.
 *
 * @param components List of component identifiers discovered in the device
 *                   (e.g., ["switch:switch:0", "input:input:0"])
 * @return String containing the generated driver code, currently a placeholder
 */
private String generateHubitatDriver(List<String> components) {
    logDebug("generateHubitatDriver called with ${components?.size() ?: 0} components: ${components}")

    // Branch to fetch files from (change to 'master' for production)
    String branch = 'AutoConfScript'
    String baseUrl = "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/${branch}/UniversalDrivers"

    // Fetch component_driver.json from GitHub
    String componentJsonUrl = "${baseUrl}/component_driver.json"
    logDebug("Fetching capability definitions from: ${componentJsonUrl}")

    String jsonContent = downloadFile(componentJsonUrl)
    if (!jsonContent) {
        logError("Failed to fetch component_driver.json from GitHub")
        return null
    }

    // Parse the JSON to extract capability definitions
    Map componentData = null
    try {
        componentData = slurper.parseText(jsonContent) as Map
        logDebug("Successfully parsed component_driver.json with ${componentData?.capabilities?.size() ?: 0} capabilities")
    } catch (Exception e) {
        logError("Failed to parse component_driver.json: ${e.message}")
        return null
    }

    List<Map> capabilities = componentData?.capabilities as List<Map>
    if (!capabilities) {
        logError("No capabilities found in component_driver.json")
        return null
    }

    // Build the driver code using StringBuilder
    StringBuilder driver = new StringBuilder()

    // Build metadata section
    driver.append("metadata {\n")
    driver.append("  definition (")

    // Get driver metadata from JSON
    Map driverDef = componentData?.driver as Map
    String driverName = generateDriverName(components)

    if (driverDef) {
        driver.append("name: '${driverName}', ")
        driver.append("namespace: '${driverDef.namespace}', ")
        driver.append("author: '${driverDef.author}', ")
        driver.append("singleThreaded: ${driverDef.singleThreaded}, ")
        driver.append("importUrl: '${driverDef.importUrl ?: ''}'")
    } else {
        // Fallback if definition not found
        driver.append("name: '${driverName}', ")
        driver.append("namespace: 'ShellyUSA', ")
        driver.append("author: 'Daniel Winks', ")
        driver.append("singleThreaded: false, ")
        driver.append("importUrl: ''")
    }

    driver.append(") {\n")

    // Track which capabilities we've already added (to avoid duplicates)
    Set<String> addedCapabilities = new HashSet<>()

    // Add component-specific capabilities
    components.each { component ->
        // Extract base type from "switch:0" format
        String baseType = component.contains(':') ? component.split(':')[0] : component

        // Find matching capability by shellyComponent field
        Map capability = capabilities.find { cap ->
            cap.shellyComponent == baseType
        }

        if (capability) {
            String capId = capability.id

            // Only add if not already added
            if (!addedCapabilities.contains(capId)) {
                addedCapabilities.add(capId)

                // Add capability declaration
                driver.append("    capability '${capId}'\n")

                // Add attributes comments
                if (capability.attributes) {
                    capability.attributes.each { attr ->
                        String attrComment = "    //Attributes: ${attr.name} - ${attr.type.toUpperCase()}"
                        if (attr.values) {
                            attrComment += " ${attr.values}"
                        }
                        driver.append("${attrComment}\n")
                    }
                }

                // Add commands comments
                if (capability.commands) {
                    List<String> cmdSignatures = capability.commands.collect { cmd ->
                        if (cmd.arguments && cmd.arguments.size() > 0) {
                            String args = cmd.arguments.collect { arg -> arg.name }.join(', ')
                            return "${cmd.name}(${args})"
                        } else {
                            return "${cmd.name}()"
                        }
                    }
                    driver.append("    //Commands: ${cmdSignatures.join(', ')}\n")
                }

                driver.append("\n")
            }
        } else {
            logDebug("No capability mapping found for Shelly component: ${component} (base type: ${baseType})")
        }
    }

    // Always add standard capabilities
    driver.append("    capability 'Initialize'\n")
    driver.append("    //Commands: initialize()\n")
    driver.append("\n")

    driver.append("    capability 'Configuration'\n")
    driver.append("    //Commands: configure()\n")
    driver.append("\n")

    driver.append("    capability 'Refresh'\n")
    driver.append("    //Commands: refresh()\n")

    driver.append("  }\n")
    driver.append("}\n\n")

    // Add preferences section
    List<Map> preferences = componentData?.preferences as List<Map>
    logDebug("Preferences found in JSON: ${preferences?.size() ?: 0}")
    logDebug("Device specific preferences found: ${componentData?.deviceSpecificPreferences ? 'yes' : 'no'}")

    if (preferences) {
        logDebug("Adding preferences section to driver")
        driver.append("preferences {\n")

        // Add standard preferences (always included)
        preferences.each { pref ->
            driver.append("  input ")
            driver.append("name: '${pref.name}', ")
            driver.append("type: '${pref.type}', ")
            driver.append("title: '${pref.title}'")

            // Add options if present (for enum types)
            if (pref.options) {
                driver.append(", options: [")
                List<String> optionPairs = pref.options.collect { k, v -> "'${k}':'${v}'" }
                driver.append(optionPairs.join(','))
                driver.append("]")
            }

            // Add defaultValue if present
            if (pref.defaultValue != null) {
                driver.append(", defaultValue: '${pref.defaultValue}'")
            }

            // Add required if present
            if (pref.required != null) {
                driver.append(", required: ${pref.required}")
            }

            driver.append("\n")
        }

        // Add device-specific preferences
        Map deviceSpecificPreferences = componentData?.deviceSpecificPreferences as Map
        if (deviceSpecificPreferences) {
            // Track which component types we've already added preferences for
            Set<String> addedDevicePrefs = new HashSet<>()

            components.each { component ->
                // Extract base type from "switch:0" format
                String baseType = component.contains(':') ? component.split(':')[0] : component

                // Check if we have device-specific preferences for this type and haven't added them yet
                if (deviceSpecificPreferences[baseType] && !addedDevicePrefs.contains(baseType)) {
                    addedDevicePrefs.add(baseType)

                    List<Map> devicePrefs = deviceSpecificPreferences[baseType] as List<Map>
                    devicePrefs.each { pref ->
                        driver.append("  input ")
                        driver.append("name: '${pref.name}', ")
                        driver.append("type: '${pref.type}', ")
                        driver.append("title: '${pref.title}'")

                        // Add options if present (for enum types)
                        if (pref.options) {
                            driver.append(", options: [")
                            List<String> optionPairs = pref.options.collect { k, v -> "'${k}':'${v}'" }
                            driver.append(optionPairs.join(','))
                            driver.append("]")
                        }

                        // Add defaultValue if present
                        if (pref.defaultValue != null) {
                            driver.append(", defaultValue: '${pref.defaultValue}'")
                        }

                        // Add required if present
                        if (pref.required != null) {
                            driver.append(", required: ${pref.required}")
                        }

                        driver.append("\n")
                    }
                }
            }
        }

        logDebug("Closing preferences section")
        driver.append("}\n\n")
    } else {
        logDebug("No preferences found in JSON, skipping preferences section")
    }

    // Fetch and include Lifecycle.groovy
    String lifecycleUrl = "${baseUrl}/Lifecycle.groovy"
    logDebug("Fetching Lifecycle.groovy from: ${lifecycleUrl}")
    String lifecycleContent = downloadFile(lifecycleUrl)
    if (lifecycleContent) {
        driver.append(lifecycleContent)
        driver.append("\n")
        logDebug("Added Lifecycle.groovy content (${lifecycleContent.length()} chars)")
    } else {
        logWarn("Failed to fetch Lifecycle.groovy, driver may be incomplete")
    }

    // Collect command files from capabilities
    Set<String> commandFiles = new HashSet<>()

    // Add command files from matched capabilities
    components.each { component ->
        String baseType = component.contains(':') ? component.split(':')[0] : component
        Map capability = capabilities.find { cap -> cap.shellyComponent == baseType }
        if (capability && capability.commandFiles) {
            commandFiles.addAll(capability.commandFiles as List<String>)
        }
    }

    // Always include standard command files
    commandFiles.add("InitialIzeCommands.groovy")
    commandFiles.add("ConfigureCommands.groovy")
    commandFiles.add("RefreshCommand.groovy")

    logDebug("Command files to fetch: ${commandFiles}")

    // Fetch and append each command file
    commandFiles.each { String commandFile ->
        String commandUrl = "${baseUrl}/${commandFile}"
        logDebug("Fetching ${commandFile} from: ${commandUrl}")
        String commandContent = downloadFile(commandUrl)
        if (commandContent) {
            driver.append(commandContent)
            driver.append("\n")
            logDebug("Added ${commandFile} content (${commandContent.length()} chars)")
        } else {
            logWarn("Failed to fetch ${commandFile}")
        }
    }

    String driverCode = driver.toString()
    logInfo("Generated driver code:\n${driverCode}", true)
    return driverCode
}

/**
 * Generates a dynamic driver name based on the Shelly components.
 *
 * @param components List of Shelly components (e.g., ["switch:0", "switch:1"])
 * @return Generated driver name (e.g., "Shelly Single Switch", "Shelly 2x Switch")
 */
private String generateDriverName(List<String> components) {
    // Count component types
    Map<String, Integer> componentCounts = [:]
    components.each { component ->
        String baseType = component.contains(':') ? component.split(':')[0] : component
        componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1
    }

    // Generate name based on components
    if (componentCounts.size() == 1) {
        String type = componentCounts.keySet().first()
        Integer count = componentCounts[type]

        String typeName = type.capitalize()
        if (count == 1) {
            return "Shelly Single ${typeName}"
        } else {
            return "Shelly ${count}x ${typeName}"
        }
    } else {
        // Multiple component types
        return "Shelly Multi-Component Device"
    }
}

/**
 * Truncates long objects to a safe length for logging.
 * Prevents log spam and memory issues when logging large response bodies
 * or data structures.
 *
 * @param obj The object to convert to string and potentially truncate
 * @param maxLen Maximum length of the returned string (default: 240)
 * @return Truncated string representation with "..." appended if shortened,
 *         or empty string if obj is null
 */
private String truncateForLog(Object obj, Integer maxLen = 240) {
    if (!obj) { return '' }
    String s = obj.toString()
    if (s.length() <= maxLen) { return s }
    return s.substring(0, maxLen) + '...'
}

/**
 * Calculates the remaining discovery time in seconds.
 * Compares the discovery end time stored in state with the current time
 * to determine how many seconds remain before discovery automatically stops.
 *
 * @return Number of seconds remaining in the discovery period, or 0 if
 *         discovery is not running or end time is not set
 */
private Integer getRemainingDiscoverySeconds() {
    if (!state.discoveryEndTime) { return 0 }
    Long remainingMs = Math.max(0L, (state.discoveryEndTime as Long) - now())
    return (Integer)(remainingMs / 1000L)
}

/**
 * Returns the configured discovery duration.
 *
 * @return Discovery duration in seconds (default: 120)
 */
private Integer getDiscoveryDurationSeconds() { 120 }

/**
 * Returns the mDNS polling interval.
 *
 * @return Interval between mDNS queries in seconds (default: 5)
 */
private Integer getMdnsPollSeconds() { 5 }

/**
 * Applies a pending display log level change.
 * If a display level change was stored in state (typically during the updated()
 * lifecycle method), applies it to the app settings and clears the pending state.
 * This deferred application prevents infinite update loops when settings changes
 * trigger the updated() method.
 */
private void applyPendingDisplayLevel() {
    String pending = state.pendingDisplayLevel?.toString()
    if (pending) {
        app.updateSetting('displayLogLevel', [type: 'enum', value: pending])
        state.pendingDisplayLevel = null
    }
}

/**
 * Removes log entries below the specified display level threshold.
 * Parses each log entry to extract its level and filters out entries
 * with priority lower than the threshold. Updates the UI with the pruned
 * log list. Entries that cannot be parsed are kept by default.
 *
 * @param displayLevel The minimum log level to retain (trace, debug, info, warn, error)
 */
private void pruneDisplayedLogs(String displayLevel) {
    if (!state.recentLogs) { return }
    int threshold = levelPriority(displayLevel?.toString())
    List<String> kept = state.recentLogs.findAll { entry ->
        // Use inline (?i) for case-insensitive matching (Groovy doesn't accept trailing /i)
        java.util.regex.Matcher m = (entry =~ /(?i)-\s*(TRACE|DEBUG|INFO|WARN|ERROR):/)
        if (m.find()) {
            String lvl = m[0][1].toLowerCase()
            return levelPriority(lvl) >= threshold
        }
        // keep entries we can't parse
        return true
    }
    int removed = state.recentLogs.size() - kept.size()
    state.recentLogs = kept
    if (removed > 0) { logDebug("pruneDisplayedLogs: removed ${removed} entries below ${displayLevel}") }
    String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
    String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
    app.sendEvent(name: 'recentLogs', value: recentPayload)
}


/**
 * Appends a log message to the in-app log buffer.
 * Adds the message with timestamp and level to state if it meets the display
 * threshold, maintains a rolling buffer of the most recent 300 entries, and
 * sends the 10 most recent entries to the UI for real-time display.
 *
 * @param level The log level (trace, debug, info, warn, error)
 * @param msg The message to log
 */
private void appendLog(String level, String msg) {
    state.recentLogs = state.recentLogs ?: []

    // Only append messages that meet the app display threshold
    String displayLevel = (settings?.displayLogLevel ?: (settings?.logLevel ?: 'warn'))?.toString()
    if (levelPriority(level) >= levelPriority(displayLevel)) {
        state.recentLogs.add("${new Date().format('yyyy-MM-dd HH:mm:ss')} - ${level?.toUpperCase()}: ${msg}")
        if (state.recentLogs.size() > 300) {
            state.recentLogs = state.recentLogs[-300..-1]
        }

        // Push the most recent 10 lines to the app UI for live updates
        String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
        String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
        app.sendEvent(name: 'recentLogs', value: recentPayload)
    }
}

/**
 * Returns the ordered list of log levels from lowest to highest priority.
 *
 * @return List of log level names: trace, debug, info, warn, error, off
 */
private List<String> LOG_LEVELS() { ['trace','debug','info','warn','error','off'] }

/**
 * Determines the numeric priority of a log level.
 * Lower index values indicate lower priority (trace=0), higher index values
 * indicate higher priority (error=4). Used for filtering log messages.
 *
 * @param level The log level name (case-insensitive)
 * @return The priority index, or the debug level index if level is null or invalid
 */
private Integer levelPriority(String level) {
    if (!level) { return LOG_LEVELS().indexOf('debug') }
    int idx = LOG_LEVELS().indexOf(level.toString().toLowerCase())
    return idx >= 0 ? idx : LOG_LEVELS().indexOf('debug')
}

/**
 * Determines if a message at the given level should be logged to Hubitat logs.
 * Compares the message level against the configured logLevel setting.
 *
 * @param level The log level to check
 * @return true if the message should be logged, false otherwise
 */
private Boolean shouldLogOverall(String level) {
    return levelPriority(level) >= levelPriority(settings?.logLevel ?: 'debug')
}

/**
 * Determines if a message at the given level should be displayed in the app UI.
 * Compares the message level against the configured displayLogLevel setting
 * (or logLevel if displayLogLevel is not set).
 *
 * @param level The log level to check
 * @return true if the message should be displayed in the UI, false otherwise
 */
private Boolean shouldDisplay(String level) {
    return levelPriority(level) >= levelPriority(settings?.displayLogLevel ?: (settings?.logLevel ?: 'warn'))
}

/**
 * Logs a trace-level message.
 * Outputs to Hubitat logs if trace logging is enabled and adds to the
 * in-app log buffer if the display level allows it.
 *
 * @param msg The message to log
 */
private void logTrace(String msg) {
    if (!shouldLogOverall('trace')) { return }
    log.trace msg
    if (shouldDisplay('trace')) { appendLog('trace', msg) }
}

/**
 * Logs a debug-level message.
 * Outputs to Hubitat logs if debug logging is enabled and adds to the
 * in-app log buffer if the display level allows it.
 *
 * @param msg The message to log
 */
private void logDebug(String msg, Boolean prettyPrint = false) {
    if (!shouldLogOverall('debug')) { return }

    String formattedMsg = msg
    if (prettyPrint) {
        // Wrap in <pre> tags to preserve formatting in HTML display
        formattedMsg = "<pre>${msg}</pre>"
    }

    log.debug formattedMsg
    if (shouldDisplay('debug')) { appendLog('debug', formattedMsg) }
}

/**
 * Logs an info-level message.
 * Outputs to Hubitat logs if info logging is enabled and adds to the
 * in-app log buffer if the display level allows it.
 *
 * @param msg The message to log
 */
private void logInfo(String msg, Boolean prettyPrint = false) {
    if (!shouldLogOverall('info')) { return }

    String formattedMsg = msg
    if (prettyPrint) {
        // Wrap in <pre> tags to preserve formatting in HTML display
        formattedMsg = "<pre>${msg}</pre>"
    }

    log.info formattedMsg
    if (shouldDisplay('info')) { appendLog('info', formattedMsg) }
}

/**
 * Logs a warning-level message.
 * Outputs to Hubitat logs if warn logging is enabled and adds to the
 * in-app log buffer if the display level allows it.
 *
 * @param msg The message to log
 */
private void logWarn(String msg) {
    if (!shouldLogOverall('warn')) { return }
    log.warn msg
    if (shouldDisplay('warn')) { appendLog('warn', msg) }
}

/**
 * Logs an error-level message.
 * Outputs to Hubitat logs if error logging is enabled and adds to the
 * in-app log buffer if the display level allows it.
 *
 * @param msg The message to log
 */
private void logError(String msg) {
    if (!shouldLogOverall('error')) { return }
    log.error msg
    if (shouldDisplay('error')) { appendLog('error', msg) }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Command Maps                                              ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Command Maps */
// MARK: Command Maps
@CompileStatic
LinkedHashMap shellyGetDeviceInfoCommand(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetDeviceInfo",
    "params":["ident":fullInfo]
  ]
  return command
}

@CompileStatic
LinkedHashMap shellyGetConfigCommand(String src = 'shellyGetConfig') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetConfig",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap shellyGetStatusCommand(String src = 'shellyGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Shelly.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap sysGetStatusCommand(String src = 'sysGetStatus') {
  LinkedHashMap command = [
    "id":0,
    "src":src,
    "method":"Sys.GetStatus",
    "params":[:]
  ]
  return command
}

@CompileStatic
LinkedHashMap devicePowerGetStatusCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerGetStatus",
    "method" : "DevicePower.GetStatus",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetStatusCommand(Integer id = 0, src = 'switchGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetCommand(Boolean on, Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSet",
    "method" : "Switch.Set",
    "params" : [
      "id" : id,
      "on" : on
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchGetConfigCommand(Integer id = 0, String src = 'switchGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer switchId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : switchId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchSetConfigCommand(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit,
  Integer switchId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Switch.SetConfig",
    "params" : [
      "id" : switchId,
      "config": [
        "initial_state": initial_state,
        "auto_on": auto_on,
        "auto_on_delay": auto_on_delay,
        "auto_off": auto_off,
        "auto_off_delay": auto_off_delay,
        "power_limit": power_limit,
        "voltage_limit": voltage_limit,
        "autorecover_voltage_errors": autorecover_voltage_errors,
        "current_limit": current_limit
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap switchResetCountersCommand(Integer id = 0, String src = 'switchResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Switch.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightResetCountersCommand(Integer id = 0, String src = 'lightResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverResetCountersCommand(Integer id = 0, String src = 'coverResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGetConfigCommand(Integer id = 0, String src = 'coverGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverOpenCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverOpen",
    "method" : "Cover.Open",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverCloseCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverClose",
    "method" : "Cover.Close",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGoToPositionCommand(Integer id = 0, Integer pos) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverGoToPosition",
    "method" : "Cover.GoToPosition",
    "params" : [
      "id" : id,
      "pos" : pos
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverGetStatusCommand(Integer id = 0, src = 'coverGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverStopCommand(Integer id = 0, src = 'coverStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Cover.Stop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap coverSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer coverId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "coverSetConfig",
    "method" : "Cover.SetConfig",
    "params" : [
      "id" : coverId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap temperatureGetConfigCommand(Integer id = 0, String src = 'temperatureGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Temperature.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap temperatureGetStatusCommand(Integer id = 0, src = 'temperatureGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Temperature.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap humidityGetConfigCommand(Integer id = 0, String src = 'humidityGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Humidity.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap humidityGetStatusCommand(Integer id = 0, src = 'humidityGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Humidity.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputSetConfigCommandJson(
  Map jsonConfigToSend,
  Integer inputId = 0
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "inputSetConfig",
    "method" : "Input.SetConfig",
    "params" : [
      "id" : inputId,
      "config": jsonConfigToSend
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListSupportedCommand(String src = 'webhookListSupported') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.ListSupported",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookListCommand(String src = 'webhookList') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap webhookDeleteCommand(Integer id, String src = 'webhookDelete') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.Delete",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptListCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptList",
    "method" : "Script.List",
    "params" : []
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStopCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStop",
    "method" : "Script.Stop",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptStartCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptStart",
    "method" : "Script.Start",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptEnableCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptEnable",
    "method" : "Script.SetConfig",
    "params" : [
      "id": id,
      "config": ["enable": true]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptDeleteCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptDelete",
    "method" : "Script.Delete",
    "params" : ["id": id]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptCreateCommand() {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptCreate",
    "method" : "Script.Create",
    "params" : ["name": "HubitatBLEHelper"]
  ]
  return command
}

@CompileStatic
LinkedHashMap scriptPutCodeCommand(Integer id, String code, Boolean append = true) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptPutCode",
    "method" : "Script.PutCode",
    "params" : [
      "id": id,
      "code": code,
      "append": append
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetConfigCommand(Integer pm1Id = 0, String src = 'pm1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetConfig",
    "params" : [
      "id" : pm1Id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1SetConfigCommand(Integer pm1Id = 0, pm1Config = [], src = 'pm1SetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.SetConfig",
    "params" : [
      "id" : pm1Id,
      "config": pm1Config
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1GetStatusCommand(Integer pm1Id = 0, String src = 'pm1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.GetStatus",
    "params" : ["id" : pm1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap pm1ResetCountersCommand(Integer pm1Id = 0, String src = 'pm1ResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PM1.ResetCounters",
    "params" : ["id" : pm1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1GetConfigCommand(Integer em1Id = 0, String src = 'em1GetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.GetConfig",
    "params" : [
      "id" : em1Id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1SetConfigCommand(Integer em1Id = 0, em1Config = [], src = 'em1SetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.SetConfig",
    "params" : [
      "id" : em1Id,
      "config": em1Config
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1GetStatusCommand(Integer em1Id = 0, String src = 'em1GetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1.GetStatus",
    "params" : ["id" : em1Id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataSetConfigCommand(Integer id = 0, em1DataConfig = [], src = 'em1DataSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.SetConfig",
    "params" : [
      "id" : id,
      "config": em1DataConfig
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetConfigCommand(Integer id = 0, String src = 'em1DataGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetConfig",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetStatusCommand(Integer id = 0, String src = 'em1DataGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetStatus",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetRecordsCommand(Integer id = 0, Integer ts = 0, String src = 'em1DataGetRecords') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetRecords",
    "params" : [
      "id" : id,
      "ts" : ts
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetDataCommand(Integer id = 0, Integer ts = 0, Integer end_ts = 0, String src = 'em1DataGetData') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetRecords",
    "params" : [
      "id" : id,
      "ts" : ts,
      "end_ts" : end_ts
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DatDeleteAllDataCommand(Integer id = 0, String src = 'em1DatDeleteAllData') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.DeleteAllData",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataResetCountersCommand(Integer id = 0, String src = 'em1DataResetCounters') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.ResetCounters",
    "params" : ["id" : id]
  ]
  return command
}

@CompileStatic
LinkedHashMap em1DataGetNetEnergiesCommand(Integer id = 0, Integer ts = 0, Integer period = 300, Integer end_ts = null, String src = 'em1DataGetNetEnergies') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "EM1Data.GetNetEnergies",
    "params" : [
      "id" : id,
      "ts" : ts,
      "period" : period,
    ]
  ]
  if(end_ts != null) {command.params["end_ts"] = end_ts}
  return command
}

@CompileStatic
LinkedHashMap bleGetConfigCommand(String src = 'bleGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "BLE.GetConfig",
    "params" : [
      "id" : 0
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap bleSetConfigCommand(Boolean enable, Boolean rpcEnable, Boolean observerEnable) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "bleSetConfig",
    "method" : "BLE.SetConfig",
    "params" : [
      "id" : 0,
      "config": [
        "enable": enable,
        "rpc": rpcEnable,
        "observer": observerEnable
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputGetConfigCommand(Integer id = 0, String src = 'inputGetConfigCommand') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Input.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap inputGetStatusCommand(Integer id = 0, src = 'inputGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Input.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeGetConfigCommand(Integer id = 0, src = 'smokeGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeSetConfigCommand(Integer id = 0, String name = '', src = 'smokeSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.SetConfig",
    "params" : [
      "id" : id,
      "name" : name
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeGetStatusCommand(Integer id = 0, src = 'smokeGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap smokeMuteCommand(Integer id = 0, src = 'smokeMute') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Smoke.Mute",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap illuminanceGetConfigCommand(Integer id = 0, String src = 'illuminanceGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Illuminance.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap illuminanceSetConfigCommand(Integer id = 0, illuminanceConfig = [], String src = 'illuminanceSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Illuminance.SetConfig",
    "params" : [
      "id" : id,
      "config" : illuminanceConfig
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap illuminanceGetStatusCommand(Integer id = 0, String src = 'illuminanceGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Illuminance.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap plugsUiGetConfigCommand(String src = 'plugsUiGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PLUGS_UI.GetConfig",
    "params" : [:]
  ]
  return command
}

@CompileStatic
LinkedHashMap plugsUiSetConfigCommand(LinkedHashMap plugsUiConfig, String src = 'plugsUiSetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PLUGS_UI.SetConfig",
    "params" : [
      "config" : plugsUiConfig
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap plugsUiGetStatusCommand(String src = 'plugsUiGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "PLUGS_UI.GetStatus",
    "params" : [:]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightGetConfigCommand(Integer id = 0, src = 'lightGetConfig') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetConfigCommand(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Boolean nightModeEnable,
  Integer nightModeBrightness,
  Long current_limit,
  Integer id = 0,
  String src = 'lightSetConfig'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "switchSetConfig",
    "method" : "Light.SetConfig",
    "params" : [
      "id" : id,
      "config": [
        "initial_state": initial_state,
        "auto_on": auto_on,
        "auto_on_delay": auto_on_delay,
        "auto_off": auto_off,
        "auto_off_delay": auto_off_delay,
        "power_limit": power_limit,
        "voltage_limit": voltage_limit,
        "autorecover_voltage_errors": autorecover_voltage_errors,
        "current_limit": current_limit,
        "night_mode.enable": nightModeEnable,
        "night_mode.brightness": nightModeBrightness
      ]
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightGetStatusCommand(Integer id = 0, src = 'lightGetStatus') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetCommand(
  Integer id = 0,
  Boolean on = null,
  Integer brightness = null,
  Integer transitionDuration = null,
  Integer toggleAfter = null,
  Integer offset = null,
  src = 'lightSet'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(brightness != null && offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(on != null) {command.params["on"] = on}
  if(brightness != null) {command.params["brightness"] = brightness}
  if(transitionDuration != null) {command.params["transition_duration"] = transitionDuration}
  if(toggleAfter != null) {command.params["toggle_after"] = toggleAfter}
  if(offset != null && brightness == null) {command.params["offset"] = offset}
  return command
}

@CompileStatic
LinkedHashMap lightSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'lightSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  return command
}

@CompileStatic
LinkedHashMap lightToggleCommand(Integer id = 0, src = 'lightToggle') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Toggle",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap lightDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap lightDimStopCommand(Integer id = 0, src = 'lightDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap lightSetAllCommand(
  Integer id = 0,
  Boolean on = null,
  Integer brightness = null,
  Integer transitionDuration = null,
  Integer toggleAfter = null,
  Integer offset = null,
  src = 'lightSet'
) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Light.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(brightness != null && offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(on != null) {command.params["on"] = on}
  if(brightness != null) {command.params["brightness"] = brightness}
  if(transitionDuration != null) {command.params["transition_duration"] = transitionDuration}
  if(toggleAfter != null) {command.params["toggle_after"] = toggleAfter}
  if(offset != null && brightness == null) {command.params["offset"] = offset}
  return command
}

@CompileStatic
LinkedHashMap rgbwSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'rgbwSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.white != null && args?.offsetWhite != null) {
    logWarn('Cannot set both white and offsetWhite offset at same time, using white value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.rgb != null) {command.params["rgb"] = args?.rgb}
  if(args?.white != null) {command.params["white"] = args?.white}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  if(args?.offsetWhite != null && args?.white == null) {command.params["offset_white"] = args?.offsetWhite}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'rgbwDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbwDimStopCommand(Integer id = 0, src = 'rgbwDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGBW.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

@CompileStatic
LinkedHashMap rgbSetCommand(LinkedHashMap args) {
  Integer id = args?.id as Integer ?: 0
  String src = 'rgbSet'
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.Set",
    "params" : [
      "id" : id
    ]
  ]
  if(args?.brightness != null && args?.offset != null) {
    logWarn('Cannot set both brightness and brightness offset at same time, using brightness value')
  }
  if(args?.white != null && args?.offsetWhite != null) {
    logWarn('Cannot set both white and offsetWhite offset at same time, using white value')
  }
  if(args?.on != null) {command.params["on"] = args?.on}
  if(args?.brightness != null) {command.params["brightness"] = args?.brightness}
  if(args?.rgb != null) {command.params["rgb"] = args?.rgb}
  if(args?.white != null) {command.params["white"] = args?.white}
  if(args?.transitionDuration != null) {command.params["transition_duration"] = args?.transitionDuration}
  if(args?.toggleAfter != null) {command.params["toggle_after"] = args?.toggleAfter}
  if(args?.offset != null && args?.brightness == null) {command.params["offset"] = args?.offset}
  if(args?.offsetWhite != null && args?.white == null) {command.params["offset_white"] = args?.offsetWhite}
  return command
}

@CompileStatic
LinkedHashMap rgbDimUpCommand(Integer id = 0, Integer fadeRate = null, src = 'lightDimUp') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimUp",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbDimDownCommand(Integer id = 0, Integer fadeRate = null, src = 'rgbDimDown') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimDown",
    "params" : [
      "id" : id
    ]
  ]
  if(fadeRate != null) {command.params["fade_rate"] = fadeRate}
  return command
}

@CompileStatic
LinkedHashMap rgbDimStopCommand(Integer id = 0, src = 'rgbDimStop') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "RGB.DimStop",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  Command Execution                                           ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Command Execution */
// MARK: Command Execution
String shellyGetDeviceInfo(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
  if(src == 'connectivityCheck') {
    long seconds = unixTimeSeconds()
    src = "${src}-${seconds}"
  }
  Map command = shellyGetDeviceInfoCommand(fullInfo, src)
  // String json = JsonOutput.toJson(command)
  parentPostCommandSync(command)
}

// @CompileStatic
// String shellyGetDeviceInfoWs(Boolean fullInfo = false, String src = 'shellyGetDeviceInfo') {
//   if(src == 'connectivityCheck') {
//     long seconds = unixTimeSeconds()
//     src = "${src}-${seconds}"
//   }
//   Map command = shellyGetDeviceInfoCommand(fullInfo, src)
//   String json = JsonOutput.toJson(command)
//   parentSendWsMessage(json)
// }

@CompileStatic
String shellyGetStatus(String src = 'shellyGetStatus') {
  LinkedHashMap command = shellyGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

// @CompileStatic
// String shellyGetStatusWs(String src = 'shellyGetStatus') {
//   LinkedHashMap command = shellyGetStatusCommand(src)
//   if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
//   String json = JsonOutput.toJson(command)
//   parentSendWsMessage(json)
// }

@CompileStatic
String sysGetStatus(String src = 'sysGetStatus') {
  LinkedHashMap command = sysGetStatusCommand(src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchGetStatus() {
  LinkedHashMap command = switchGetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchSet(Boolean on) {
  LinkedHashMap command = switchSetCommand(on)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String switchGetConfig(Integer id = 0, String src = 'switchGetConfig') {
  LinkedHashMap command = switchGetConfigCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchSetConfig(
  String initial_state,
  Boolean auto_on,
  Long auto_on_delay,
  Boolean auto_off,
  Long auto_off_delay,
  Long power_limit,
  Long voltage_limit,
  Boolean autorecover_voltage_errors,
  Long current_limit
) {
  Map command = switchSetConfigCommand(initial_state, auto_on, auto_on_delay, auto_off, auto_off_delay, power_limit, voltage_limit, autorecover_voltage_errors, current_limit)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchSetConfigJson(Map jsonConfigToSend, Integer switchId = 0) {
  Map command = switchSetConfigCommandJson(jsonConfigToSend, switchId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void coverSetConfigJson(Map jsonConfigToSend, Integer coverId = 0) {
  Map command = coverSetConfigCommandJson(jsonConfigToSend, coverId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void inputSetConfigJson(Map jsonConfigToSend, Integer inputId = 0) {
  Map command = inputSetConfigCommandJson(jsonConfigToSend, inputId)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
void switchResetCounters(Integer id = 0, String src = 'switchResetCounters') {
  LinkedHashMap command = switchResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void lightResetCounters(Integer id = 0, String src = 'lightResetCounters') {
  LinkedHashMap command = lightResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void coverResetCounters(Integer id = 0, String src = 'coverResetCounters') {
  LinkedHashMap command = coverResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void em1DataResetCounters(Integer id = 0, String src = 'em1DataResetCounters') {
  LinkedHashMap command = em1DataResetCountersCommand(id, src)
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandAsync(command, 'resetCountersCallback')
}

@CompileStatic
void resetCountersCallback(AsyncResponse response, Map data = null) {
  logTrace('Processing reset counters callback')
  if(responseIsValid(response) == true) {
    Map json = (LinkedHashMap)response.getJson()
    logTrace("resetCountersCallback JSON: ${prettyJson(json)}")
  }
}

@CompileStatic
void scriptList() {
  LinkedHashMap command = scriptListCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}

@CompileStatic
String pm1GetStatus() {
  LinkedHashMap command = pm1GetStatusCommand()
  if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
  parentPostCommandSync(command)
}
/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  HTTP Methods                                                ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region HTTP Methods */
// MARK: HTTP Methods
LinkedHashMap postCommandSync(LinkedHashMap command, String uri = null) {
  LinkedHashMap json
  Map params = [uri: (uri ? uri : "${getBaseUriRpc()}")]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  params.timeout = 10
  logTrace("postCommandSync sending to ${params.uri}: ${prettyJson(params)}")
  try {
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    setAuthIsEnabled(false)
  } catch(HttpResponseException ex) {
    if(ex.getStatusCode() != 401) {
      logWarn("HTTP Exception (${ex.getStatusCode()}): ${ex.message ?: ex.toString()}")
      throw ex
    }
    setAuthIsEnabled(true)
    def authHeader = ex.getResponse()?.getAllHeaders()?.find{ it.getValue()?.contains('nonce')}
    if (!authHeader) {
      logError("Device requires authentication but no auth header found in response")
      throw new Exception("Authentication required but auth header missing")
    }
    String authToProcess = authHeader.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    try {
      if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
      params.body = command
      httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    } catch(HttpResponseException ex2) {
      logError("Auth failed a second time (${ex2.getStatusCode()}). Double check password correctness.")
      throw ex2
    }
  } catch(Exception ex) {
    logError("postCommandSync exception for ${params.uri}: ${ex.class.simpleName}: ${ex.message ?: ex.toString()}")
    throw ex
  }
  logTrace("postCommandSync returned from ${params.uri}: ${prettyJson(json)}")
  return json
}

LinkedHashMap parentPostCommandSync(LinkedHashMap command, String uri = null) {
  if(hasParent() == true) { return parent?.postCommandSync(command, uri) }
  else { return postCommandSync(command, uri) }
}

void parentPostCommandAsync(LinkedHashMap command, String callbackMethod = '') {
  if(hasParent() == true) { parent?.postCommandAsync(command, callbackMethod) }
  else { postCommandAsync(command, callbackMethod) }
}

void postCommandAsync(LinkedHashMap command, String callbackMethod = '') {
  LinkedHashMap json
  Map params = [uri: "${getBaseUriRpc()}"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  logTrace("postCommandAsync sending: ${prettyJson(params)}")
  asynchttpPost('postCommandAsyncCallback', params, [params:params, command:command, attempt:1, callbackMethod:callbackMethod])
  setAuthIsEnabled(false)
}

void postCommandAsyncCallback(AsyncResponse response, Map data = null) {
  logTrace("postCommandAsyncCallback has data: ${data}")
  if (response?.status == 401 && response?.getErrorMessage() == 'Unauthorized') {
    Map params = data.params
    Map command = data.command
    setAuthIsEnabled(true)
    // logWarn("Error headers: ${response?.getHeaders()}")
    String authToProcess = response?.getHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    if(authIsEnabled() == true && getAuth().size() > 0) {
      command.auth = getAuth()
      params.body = command
    }
    if(data?.attempt == 1) {
      asynchttpPost('postCommandAsyncCallback', params, [params:params, command:command, attempt:2, callbackMethod:data?.callbackMethod])
    } else {
      logError('Auth failed a second time. Double check password correctness.')
    }
  } else if(response?.status == 200) {
    String followOnCallback = data?.callbackMethod
    if(followOnCallback != null && followOnCallback != '') {
      logTrace("Follow On Callback: ${followOnCallback}")
      "${followOnCallback}"(response, data)
    }
  }
}

LinkedHashMap postSync(LinkedHashMap command) {
  LinkedHashMap json
  Map params = [uri: "${getBaseUriRpc()}"]
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  params.body = command
  logTrace("postCommandSync sending: ${prettyJson(params)}")
  try {
    httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    setAuthIsEnabled(false)
  } catch(HttpResponseException ex) {
    logWarn("Exception: ${ex}")
    setAuthIsEnabled(true)
    String authToProcess = ex.getResponse().getAllHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    try {
      if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
      httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    } catch(HttpResponseException ex2) {
      logError('Auth failed a second time. Double check password correctness.')
    }
  }
  logTrace("postCommandSync returned: ${prettyJson(json)}")
  return json
}

void jsonAsyncGet(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpGet(callbackMethod, params, data)
}

void jsonAsyncPost(String callbackMethod, Map params, Map data) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  asynchttpPost(callbackMethod, params, data)
}

LinkedHashMap jsonSyncGet(Map params) {
  params.contentType = 'application/json'
  params.requestContentType = 'application/json'
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data as LinkedHashMap }
    else { logError(resp.data) }
  }
}

@CompileStatic
Boolean responseIsValid(AsyncResponse response) {
  if (response?.status != 200 || response.hasError()) {
    if((hasCapabilityBattery() || hasCapabilityBatteryGen1()) && response.status == 408 ) {
      logInfo("Request returned HTTP status:${response.status}, error message: ${response.getErrorMessage()}")
      logInfo('This is due to the device being asleep. If you are attempting to add/configure a device, ensure it is awake and connected to WiFi before trying again...')
    } else if(response.status == 500 && response.getErrorData() == 'Conditions not correct!') {
      logInfo('Attempted to open valve while already open or attempted to close valve while already closed. Running refresh to pull in correct current state.')
      // refresh()
    } else {
      logError("Request returned HTTP status ${response.status}")
      logError("Request error message: ${response.getErrorMessage()}")
      try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorData()' method
      try{logError("Request ErrorJson: ${prettyJson(response.getErrorJson() as LinkedHashMap)}")} catch(Exception e){} //Empty catch to work around not having a 'hasErrorJson()' method
    }
  }
  if (response.hasError()) { return false } else { return true }
}

@CompileStatic
void sendShellyCommand(String command, String queryParams = null, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = queryParams ? "${getBaseUri()}/${command}${queryParams}".toString() : "${getBaseUri()}/${command}".toString()
  logTrace("sendShellyCommand: ${params}")
  jsonAsyncGet(callbackMethod, params, data)
}

@CompileStatic
void sendShellyJsonCommand(String command, Map json, String callbackMethod = 'shellyCommandCallback', Map data = null) {
  if(!command) {return}
  Map<String> params = [:]
  params.uri = "${getBaseUri()}/${command}".toString()
  params.body = json
  logTrace("sendShellyJsonCommand: ${params}")
  jsonAsyncPost(callbackMethod, params, data)
}

@CompileStatic
void shellyCommandCallback(AsyncResponse response, Map data = null) {
  if(!responseIsValid(response)) {return}
  logJson(response.getJson() as LinkedHashMap)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Bluetooth                                                   ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Bluetooth */
// MARK: Bluetooth
void enableBluReportingToHE() {
  enableBluetooth()
  LinkedHashMap s = getBleShellyBluId()
  if(s == null) {
    logDebug('HubitatBLEHelper script not found on device, creating script...')
    postCommandSync(scriptCreateCommand())
    s = getBleShellyBluId()
  }
  Integer id = s?.id as Integer
  if(id != null) {
    postCommandSync(scriptStopCommand(id))
    logDebug('Getting latest Shelly Bluetooth Helper script...')
    String js = getBleShellyBluJs()
    logDebug('Sending latest Shelly Bluetooth Helper to device...')
    postCommandSync(scriptPutCodeCommand(id, js, false))
    logDebug('Enabling Shelly Bluetooth HelperShelly Bluetooth Helper on device...')
    postCommandSync(scriptEnableCommand(id))
    logDebug('Starting Shelly Bluetooth Helper on device...')
    postCommandSync(scriptStartCommand(id))
    logDebug('Validating sucessful installation of Shelly Bluetooth Helper...')
    s = getBleShellyBluId()
    logDebug("Bluetooth Helper is ${s?.name == 'HubitatBLEHelper' ? 'installed' : 'not installed'}, ${s?.enable ? 'enabled' : 'disabled'}, and ${s?.running ? 'running' : 'not running'}")
    if(s?.name == 'HubitatBLEHelper' && s?.enable && s?.running) {
      logDebug('Sucessfully installed Shelly Bluetooth Helper on device...')
    } else {
      logWarn('Shelly Bluetooth Helper was not sucessfully installed on device!')
    }
  }
}

@CompileStatic
void disableBluReportingToHE() {
  LinkedHashMap s = getBleShellyBluId()
  Integer id = s?.id as Integer
  if(id != null) {
    logDebug('Removing HubitatBLEHelper from Shelly device...')
    postCommandSync(scriptDeleteCommand(id))
    logDebug('Disabling BLE Observer...')
    postCommandSync(bleSetConfigCommand(true, true, false))
  }
}

@CompileStatic
LinkedHashMap getBleShellyBluId() {
  logDebug('Getting index of HubitatBLEHelper script, if it exists on Shelly device...')
  LinkedHashMap json = postCommandSync(scriptListCommand())
  List<LinkedHashMap> scripts = (List<LinkedHashMap>)((LinkedHashMap)json?.result)?.scripts
  scripts.each{logDebug("Script found: ${prettyJson(it)}")}
  return scripts.find{it?.name == 'HubitatBLEHelper'}
}

String getBleShellyBluJs() {
  Map params = [uri: BLE_SHELLY_BLU]
  params.contentType = 'text/plain'
  params.requestContentType = 'text/plain'
  params.textParser = true
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) {
      StringWriter sw = new StringWriter()
      ((StringReader)resp.data).transferTo(sw);
      return sw.toString()
    }
    else { logError(resp.data) }
  }
}

void enableBluetooth() {
  logDebug('Enabling Bluetooth on Shelly device...')
  postCommandSync(bleSetConfigCommand(true, true, true))
}
/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  Child Devices                                               ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Child Devices */
// MARK: Child Devices
ChildDeviceWrapper createParentSwitch(String dni, Boolean isPm = false, String labelText = '', Boolean hasChildren = false) {
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = isPm == false ? 'Shelly Single Switch' : 'Shelly Single Switch PM'
    labelText = labelText != '' ? labelText : isPm == false ? 'Shelly Switch' : 'Shelly Switch PM'
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${labelText}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${labelText}"])
      child.updateDataValue('macAddress',"${dni}")
      child.updateDataValue('hasChildren',"${hasChildren}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}



ChildDeviceWrapper createChildSwitch(Integer id, String additionalId = null) {
  String dni = additionalId == null ? "${getThisDeviceDNI()}-switch${id}" : "${getThisDeviceDNI()}-${additionalId}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = additionalId == null ? 'Shelly Switch Component' : 'Shelly OverUnder Switch Component'
    String labelText = getAppLabel() != null ? "${getAppLabel()}" : "${driverName}"
    String label = additionalId == null ? "${labelText} - Switch ${id}" : "${labelText} - ${additionalId} - Switch ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('switchId',"${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildDimmer(Integer id) {
  String dni =  "${getThisDeviceDNI()}-dimmer${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly Dimmer Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - Dimmer ${id}" : "${driverName} - Dimmer ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('switchLevelId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildRGB(Integer id) {
  String dni =  "${getThisDeviceDNI()}-rgb${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly RGB Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - RGB ${id}" : "${driverName} - RGB ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('rgbId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildRGBW(Integer id) {
  String dni =  "${getThisDeviceDNI()}-rgbw${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly RGBW Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - RGBW ${id}" : "${driverName} - RGBW ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('rgbwId',"${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildPmSwitch(Integer id) {
  String dni =  "${getThisDeviceDNI()}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly Switch PM Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - Switch ${id}" : "${driverName} - Switch ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('switchId',"${id}")
  child.updateDataValue('hasPM','true')
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildEM(Integer id, String phase) {
  String dni =  "${getThisDeviceDNI()}-em${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly EM Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - EM${id} - phase ${phase}" : "${driverName} - EM${id} - phase ${phase}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  child.updateDataValue('apparentPowerId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('returnedEnergyId', "${id}")
  child.updateDataValue('emData', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildEM1(Integer id) {
  String dni =  "${getThisDeviceDNI()}-em${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = 'Shelly EM Component'
    String label = getAppLabel() != null ? "${getAppLabel()} - EM ${id}" : "${driverName} - EM ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  }
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  child.updateDataValue('apparentPowerId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('returnedEnergyId', "${id}")
  child.updateDataValue('em1Data', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildInput(Integer id, String inputType) {
  logDebug("Input type is: ${inputType}")
  String driverName = "Shelly Input ${inputType} Component"
  String dni = "${getThisDeviceDNI()}-input${inputType}${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Input ${inputType} ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("input${inputType}Id","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
void removeChildInput(Integer id, String inputType) {
  String dni = "${getThisDeviceDNI()}-input${inputType}${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if(child != null) { deleteChildByDNI(dni) }
}

@CompileStatic
ChildDeviceWrapper createChildCover(Integer id, String driverName = 'Shelly Cover Component') {
  String dni = "${getThisDeviceDNI()}-cover${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Cover ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("coverId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildPmCover(Integer id) {
  ChildDeviceWrapper child = createChildCover(id, 'Shelly Cover PM Component')
  child.updateDataValue('hasPM','true')
  child.updateDataValue('currentId', "${id}")
  child.updateDataValue('energyId', "${id}")
  child.updateDataValue('powerId', "${id}")
  child.updateDataValue('voltageId', "${id}")
  child.updateDataValue('frequencyId', "${id}")
  return child
}

@CompileStatic
ChildDeviceWrapper createChildTemperature(Integer id) {
  String driverName = "Shelly Temperature Peripheral Component"
  String dni = "${getThisDeviceDNI()}-temperature${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Temperature ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("temperatureId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildHumidity(Integer id) {
  String driverName = "Shelly Humidity Peripheral Component"
  String dni = "${getThisDeviceDNI()}-humidity${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Temperature ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("humidityId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildTemperatureHumidity(Integer id) {
  String driverName = "Shelly Temperature & Humidity Peripheral Component"
  String dni = "${getThisDeviceDNI()}-temperatureHumidity${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Temperature & Humidity${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue("temperatureId","${id}")
      child.updateDataValue("humidityId","${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildIlluminance(Integer id) {
  String driverName = "Generic Component Illuminance Sensor"
  String dni = "${getThisDeviceDNI()}-illuminance${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - Illuminance ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('illuminanceId',"${id}")
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildPlugsUiRGB() {
  String driverName = "Shelly RGB Component"
  String dni = "${getThisDeviceDNI()}-plugsui-rgb"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - LED RGB"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('plugsUiRgb','true')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildPlugsUiRGBOn() {
  String driverName = "Shelly RGB Component"
  String dni = "${getThisDeviceDNI()}-plugsui-rgb-on"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - LED RGB Power On"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('plugsUiRgb','true')
      child.updateDataValue('plugsUiRgbState','on')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildPlugsUiRGBOff() {
  String driverName = "Shelly RGB Component"
  String dni = "${getThisDeviceDNI()}-plugsui-rgb-off"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - LED RGB Power Off"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('plugsUiRgb','true')
      child.updateDataValue('plugsUiRgbState','off')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

@CompileStatic
ChildDeviceWrapper createChildVoltage(Integer id) {
  String driverName = "Shelly Polling Voltage Sensor Component"
  String dni = "${getThisDeviceDNI()}-adc${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String label = "${getAppLabel()} - ADC ${id}"
    logDebug("Child device does not exist, creating child device with DNI, Name, Label: ${dni}, ${driverName}, ${label}")
    try {
      child = addShellyDevice(driverName, dni, [name: "${driverName}", label: "${label}"])
      child.updateDataValue('adcId',"${id}")
      child.updateDataValue('polling','true')
      return child
    }
    catch (UnknownDeviceTypeException e) {logException("${driverName} driver not found")}
  } else { return child }
}

ChildDeviceWrapper addShellyDevice(String driverName, String dni, Map props) {
  return addChildDevice('ShellyUSA', driverName, dni, props)
}

ChildDeviceWrapper getShellyDevice(String dni) {return getChildDevice(dni)}

@CompileStatic
ChildDeviceWrapper getVoltageChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  // Prefer a child explicitly mapped to 'voltageId' (PM/EM children), but fall back to ADC polling child ('adcId').
  ChildDeviceWrapper byVoltageId = allChildren.find{ getChildDeviceIntegerDataValue(it,'voltageId') == id }
  if(byVoltageId != null) { return byVoltageId }
  return allChildren.find{ getChildDeviceIntegerDataValue(it,'adcId') == id }
}

@CompileStatic
ChildDeviceWrapper getFrequencyChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'frequencyId') == id}
}

@CompileStatic
ChildDeviceWrapper getApparentPowerChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  ChildDeviceWrapper byAppPowerId = allChildren.find{ getChildDeviceIntegerDataValue(it,'apparentPowerId') == id }
  if(byAppPowerId != null) { return byAppPowerId }
  // fallback to powerId for devices that expose a single power child
  return allChildren.find{ getChildDeviceIntegerDataValue(it,'powerId') == id }
}

@CompileStatic
ChildDeviceWrapper getCurrentChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  ChildDeviceWrapper byCurrentId = allChildren.find{ getChildDeviceIntegerDataValue(it,'currentId') == id }
  if(byCurrentId != null) { return byCurrentId }
  // fallback to powerId for components that map current/power to the same child
  ChildDeviceWrapper byPowerId = allChildren.find{ getChildDeviceIntegerDataValue(it,'powerId') == id }
  if(byPowerId != null) { return byPowerId }
  return null
}

@CompileStatic
ChildDeviceWrapper getReturnedEnergyChildById(Integer id) {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'returnedEnergyId') == id}
}

@CompileStatic
ChildDeviceWrapper getEnergyChildById(Integer id) {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'energyId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getEnergyChildren() {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasAttribute(it,'energy')}
}

@CompileStatic
List<ChildDeviceWrapper> getCoverChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'coverId')}
}

@CompileStatic
ChildDeviceWrapper getCoverChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'coverId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getValveChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'valveId')}
}

@CompileStatic
ChildDeviceWrapper getValveChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'valveId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getEnergySwitchChildren() {
  List<ChildDeviceWrapper> switchChildren = getSwitchChildren()
  return switchChildren.findAll{childHasAttribute(it,'energy')}
}

@CompileStatic
ChildDeviceWrapper getEnergySwitchChildById(Integer id) {
  List<ChildDeviceWrapper> energySwitchChildren = getEnergySwitchChildren()
  return energySwitchChildren.find{getChildDeviceIntegerDataValue(it,'switchId') == id}
}

@CompileStatic
Integer getSwitchChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'switchId')}.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'switchId')}.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getSwitchLevelChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'switchLevelId')}
}

@CompileStatic
ChildDeviceWrapper getSwitchLevelChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'switchLevelId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'switchId')}
}

@CompileStatic
ChildDeviceWrapper getSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'switchId') == id}
}

@CompileStatic
Integer getInputSwitchChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}?.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}?.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getInputSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getInputSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputSwitchId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getInputCountChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputCountId')}
}

@CompileStatic
ChildDeviceWrapper getInputCountChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputCountId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getInputAnalogChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputAnalogId')}
}

@CompileStatic
ChildDeviceWrapper getInputAnalogChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputAnalogId') == id}
}

@CompileStatic
Boolean hasInputButtonChildren() { return getInputButtonChildren().size() > 0 }

@CompileStatic
Integer getInputButtonChildrenCount() {
  if(hasParent() == true) {
    ArrayList<ChildDeviceWrapper> allChildren = getParentDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputButtonId')}?.size()
  } else {
    ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
    return allChildren.findAll{childHasDataValue(it,'inputButtonId')}?.size()
  }
}

@CompileStatic
List<ChildDeviceWrapper> getInputButtonChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'inputButtonId')}
}

@CompileStatic
ChildDeviceWrapper getInputButtonChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'inputButtonId') == id}
}

@CompileStatic
Boolean hasTemperatureChildren() { return getTemperatureChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getTemperatureChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'temperatureId')}
}

ChildDeviceWrapper getTemperatureChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'temperatureId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getTemperatureSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'temperatureSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getTemperatureSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'temperatureSwitchId') == id}
}

@CompileStatic
Boolean hasHumidityChildren() { return getHumidityChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getHumidityChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'humidityId')}
}

@CompileStatic
ChildDeviceWrapper getHumidityChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'humidityId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getHumiditySwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'humiditySwitchId')}
}

@CompileStatic
ChildDeviceWrapper getHumiditySwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'humiditySwitchId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getLightChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'lightId')}
}

@CompileStatic
ChildDeviceWrapper getLightChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'lightId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getRGBChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'rgbId')}
}

@CompileStatic
ChildDeviceWrapper getRGBChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'rgbId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getRGBWChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'rgbwId')}
}

@CompileStatic
ChildDeviceWrapper getRGBWChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'rgbwId') == id}
}

@CompileStatic
ChildDeviceWrapper getIlluminanceChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'illuminanceId') == id}
}

@CompileStatic
ChildDeviceWrapper getPlugsUiRgbChild() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceDataValue(it,'plugsUiRgb') == 'true'}
}

@CompileStatic
ChildDeviceWrapper getPlugsUiRgbOnChild() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceDataValue(it,'plugsUiRgbState') == 'on'}
}

@CompileStatic
ChildDeviceWrapper getPlugsUiRgbOffChild() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceDataValue(it,'plugsUiRgbState') == 'off'}
}

@CompileStatic
Boolean hasAdcChildren() { return getAdcChildren().size() > 0 }

@CompileStatic
List<ChildDeviceWrapper> getAdcChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'adcId')}
}

@CompileStatic
ChildDeviceWrapper getAdcChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'adcId') == id}
}

@CompileStatic
List<ChildDeviceWrapper> getAdcSwitchChildren() {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.findAll{childHasDataValue(it,'adcSwitchId')}
}

@CompileStatic
ChildDeviceWrapper getAdcSwitchChildById(Integer id) {
  ArrayList<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.find{getChildDeviceIntegerDataValue(it,'adcSwitchId') == id}
}
/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  Authentication                                              ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Authentication */
// MARK: Authentication
void processUnauthorizedMessage(String message) {
  LinkedHashMap json = (LinkedHashMap)slurper.parseText(message)
  setAuthMap(json)
}

@CompileStatic
String getPassword() { return getAppSettings().devicePassword as String }
LinkedHashMap getAuth() {
  LinkedHashMap authMap = getAuthMap()
  if(authMap == null || authMap.size() == 0) {return [:]}
  String realm = authMap['realm']
  String ha1 = "admin:${realm}:${getPassword()}".toString()
  Long nonce = Long.valueOf(authMap['nonce'].toString())
  String nc = (authMap['nc']).toString()
  Long cnonce = now()
  String ha2 = '6370ec69915103833b5222b368555393393f098bfbfbb59f47e0590af135f062'
  ha1 = sha256(ha1)
  String response = ha1 + ':' + nonce.toString() + ':' + nc + ':' + cnonce.toString() + ':' + 'auth'  + ':' + ha2
  response = sha256(response)
  String algorithm = authMap['algorithm'].toString()
  return [
    'realm':realm,
    'username':'admin',
    'nonce':nonce,
    'cnonce':cnonce,
    'response':response,
    'algorithm':algorithm
  ]
}

@CompileStatic
String sha256(String base) {
  MessageDigest digest = getMessageDigest()
  byte[] hash = digest.digest(base.getBytes("UTF-8"))
  StringBuffer hexString = new StringBuffer()
  for (int i = 0; i < hash.length; i++) {
    String hex = Integer.toHexString(0xff & hash[i])
    if(hex.length() == 1) hexString.append('0')
    hexString.append(hex);
  }
  return hexString.toString()
}

@CompileStatic
MessageDigest getMessageDigest() {
  if(messageDigests == null) { messageDigests = new ConcurrentHashMap<String, MessageDigest>() }
  if(!messageDigests.containsKey(getThisDeviceDNI())) { messageDigests[getThisDeviceDNI()] = MessageDigest.getInstance("SHA-256") }
  return messageDigests[getThisDeviceDNI()]
}

@CompileStatic
LinkedHashMap getAuthMap() {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  if(!authMaps.containsKey(getThisDeviceDNI())) { authMaps[getThisDeviceDNI()] = [:] }
  return authMaps[getThisDeviceDNI()]
}
@CompileStatic
void setAuthMap(LinkedHashMap map) {
  if(authMaps == null) { authMaps = new ConcurrentHashMap<String, LinkedHashMap>() }
  logTrace("Device authentication detected, setting authmap to ${map}")
  authMaps[getThisDeviceDNI()] = map
}

@CompileStatic
Boolean authIsEnabled() {
  // In app context, use state instead of device data values
  return getAppState('authEnabled') == true
}
@CompileStatic
void setAuthIsEnabled(Boolean auth) {
  // In app context, use state instead of device data values
  setAppState('authEnabled', auth)
}

String getAppState(String key) {
  return state[key]
}
void setAppState(String key, value) {
  state[key] = value
}

@CompileStatic
Boolean authIsEnabledGen1() {
  Boolean authEnabled = (
    getAppSettings()?.deviceUsername != null &&
    getAppSettings()?.devicePassword != null &&
    getAppSettings()?.deviceUsername != '' &&
    getAppSettings()?.devicePassword != ''
  )
  setAuthIsEnabled(authEnabled)
  return authEnabled
}

// @CompileStatic
// void performAuthCheck() { shellyGetStatusWs('authCheck') }

@CompileStatic
String getBasicAuthHeader() {
  if(getAppSettings()?.deviceUsername != null && getAppSettings()?.devicePassword != null) {
    return base64Encode("${getAppSettings().deviceUsername}:${getAppSettings().devicePassword}".toString())
  }
}
/* #endregion */
String prettyJson(Map jsonInput) {
  return JsonOutput.prettyPrint(JsonOutput.toJson(jsonInput))
}

String nowFormatted() {
  if(location.timeZone) {return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone)}
  else {return new Date().format('yyyy-MMM-dd h:mm:ss a')}
}

@CompileStatic
String runEveryCustomSecondsCronString(Integer seconds) {
  String currentSecond = new Date().format('ss')
  return "/${seconds} * * ? * * *"
}

@CompileStatic
String runEveryCustomMinutesCronString(Integer minutes) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  return "${currentSecond} /${minutes} * ? * * *"
}

@CompileStatic
String runEveryCustomHoursCronString(Integer hours) {
  String currentSecond = new Date().format('ss')
  String currentMinute = new Date().format('mm')
  String currentHour = new Date().format('H')
  return "${currentSecond} * /${hours} ? * * *"
}

void runEveryCustomSeconds(Integer seconds, String methodToRun) {
  if(seconds < 60) {
    schedule(runEveryCustomSecondsCronString(seconds as Integer), methodToRun)
  }
  if(seconds >= 60 && seconds < 3600) {
    String cron = runEveryCustomMinutesCronString((seconds/60) as Integer)
    schedule(cron, methodToRun)
  }
  if(seconds == 3600) {
    schedule(runEveryCustomHoursCronString((seconds/3600) as Integer), methodToRun)
  }
}

void runInRandomSeconds(String methodToRun, Integer seconds = 90) {
  if(seconds < 0 || seconds > 240) {
    logWarn('Seconds must be between 0 and 240')
  } else {
    Long r = new Long(new Random().nextInt(seconds))
    runIn(r as Long, methodToRun)
  }
}

void runInSeconds(String methodToRun, Integer seconds = 3) {
  if(seconds < 0 || seconds > 240) {
    logWarn('Seconds must be between 0 and 240')
  } else {
    runIn(seconds as Long, methodToRun)
  }
}

double nowDays() { return (now() / 86400000) }

long unixTimeMillis() { return (now()) }

@CompileStatic
Integer convertHexToInt(String hex) { Integer.parseInt(hex,16) }

@CompileStatic
String convertHexToIP(String hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

@CompileStatic
String convertIPToHex(String ipAddress) {
  List parts = ipAddress.tokenize('.')
  return String.format("%02X%02X%02X%02X", parts[0] as Integer, parts[1] as Integer, parts[2] as Integer, parts[3] as Integer)
}

@CompileStatic
BigDecimal wattMinuteToKWh(BigDecimal watts) {
  return (watts/60/1000).setScale(2, BigDecimal.ROUND_HALF_UP)
}

void clearAllStates() {
  state.clear()
  if (device) device.getCurrentStates().each { device.deleteCurrentState(it.name) }
}

@CompileStatic
void deleteChildDevices() {
  ArrayList<ChildDeviceWrapper> children = getThisDeviceChildren()
  children.each { child -> deleteChildByDNI(getChildDeviceNetworkId(child)) }
}



void deleteChildByDNI(String dni) {
  deleteChildDevice(dni)
}

BigDecimal cToF(BigDecimal val) { return celsiusToFahrenheit(val) }

BigDecimal fToC(BigDecimal val) { return fahrenheitToCelsius(val) }

@CompileStatic
Integer boundedLevel(Integer level, Integer min = 0, Integer max = 100) {
  if(level == null) {return null}
  return (Math.min(Math.max(level, min), max) as Integer)
}

@CompileStatic
String base64Encode(String toEncode) { return toEncode.bytes.encodeBase64().toString() }
/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports                                                     ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Imports */
// MARK: Imports
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovyx.net.http.HttpResponseException
import hubitat.device.HubResponse
import hubitat.scheduling.AsyncResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.StringReader
import java.io.StringWriter
/* #endregion */

// ╔══════════════════════════════════════════════════════════════╗
// ║  Device Properties, Settings & Helpers                       ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Device Properties, Settings & Helpers */
// MARK: Device Properties, Settings & Helpers
// ═══════════════════════════════════════════════════════════════
// App Context Helpers (non-static for app/device/parent access)
// ═══════════════════════════════════════════════════════════════

/** Helper to access app label (non-static to avoid compilation errors) */
private String getAppLabelHelper() {
  return app.getLabel() ?: app.label ?: 'Shelly Discovery'
}

/** Helper to access app ID (non-static to avoid compilation errors) */
private Long getAppIdHelper() {
  return app.id
}

/** Helper to send app events (non-static to avoid compilation errors) */
private void sendAppEventHelper(Map properties) {
  app.sendEvent(properties)
}

// ═══════════════════════════════════════════════════════════════
// Device/Child Operation Helpers (non-static for dynamic dispatch)
// ═══════════════════════════════════════════════════════════════

/** Helper for dev.updateSetting() calls */
private void deviceUpdateSettingHelper(DeviceWrapper dev, String name, Object value) {
  dev.updateSetting(name, value)
}

/** Helper for dev.getDataValue() calls */
private String deviceGetDataValueHelper(DeviceWrapper dev, String name) {
  return dev.getDataValue(name)
}

/** Helper for dev.updateDataValue() calls */
private void deviceUpdateDataValueHelper(DeviceWrapper dev, String name, String value) {
  dev.updateDataValue(name, value)
}

/** Helper for dev.hasCapability() calls */
private Boolean deviceHasCapabilityHelper(DeviceWrapper dev, String capability) {
  return dev.hasCapability(capability)
}

/** Helper for dev.hasAttribute() calls */
private Boolean deviceHasAttributeHelper(DeviceWrapper dev, String attribute) {
  return dev.hasAttribute(attribute)
}

/** Helper for child.sendEvent() calls */
private void childSendEventHelper(ChildDeviceWrapper child, Map properties) {
  child.sendEvent(properties)
}

/** Helper for child.updateDataValue() calls */
private void childUpdateDataValueHelper(ChildDeviceWrapper child, String name, String value) {
  child.updateDataValue(name, value)
}

/** Helper for child.hasAttribute() calls */
private Boolean childHasAttributeHelper(ChildDeviceWrapper child, String attribute) {
  return child.hasAttribute(attribute)
}

/** Helper for child.getDeviceDataValue() calls */
private String childGetDeviceDataValueHelper(ChildDeviceWrapper child, String name) {
  return child.getDeviceDataValue(name)
}

// ═══════════════════════════════════════════════════════════════
// Hubitat Built-in Method Helpers (non-static for dynamic dispatch)
// ═══════════════════════════════════════════════════════════════

/** Helper for schedule() calls */
private void scheduleHelper(String cronExpression, String handlerMethod) {
  schedule(cronExpression, handlerMethod)
}

/** Helper for unschedule() calls */
private void unscheduleHelper(String handlerMethod) {
  unschedule(handlerMethod)
}

/** Helper for getLocation() calls */
private Object getLocationHelper() {
  return getLocation()
}

/** Helper for parent property access */
private Object getParentHelper() {
  return parent
}

/** Helper for httpGet() calls */
private void httpGetHelper(Map params, Closure closure) {
  httpGet(params, closure)
}

/** Helper for httpPost() calls */
private void httpPostHelper(Map params, Closure closure) {
  httpPost(params, closure)
}

// ═══════════════════════════════════════════════════════════════
// App Context Functions (with @CompileStatic type safety)
// ═══════════════════════════════════════════════════════════════

/**
 * Gets the app's label for use in child device naming.
 * In app context, we use the app's label instead of a device label.
 *
 * @return The app's label, or 'Shelly Discovery' if not set
 */
@CompileStatic
String getAppLabel() {
  return getAppLabelHelper()
}

/**
 * Gets the base identifier for child device DNIs.
 * In app context, we use the app ID instead of a device DNI.
 * Child devices will have DNIs in the format: app-<appId>-<component>-<id>
 *
 * @return Base DNI string for this app's child devices
 */
@CompileStatic
String getBaseDNI() {
  return "app-${getAppIdHelper()}"
}

// Legacy function - kept for backward compatibility with driver code
// In app context, this doesn't apply, but some functions may still reference it
DeviceWrapper thisDevice() { return this.device }
ArrayList<ChildDeviceWrapper> getThisDeviceChildren() { return getChildDevices() }
ArrayList<ChildDeviceWrapper> getParentDeviceChildren() { return parent?.getChildDevices() }

LinkedHashMap getAppSettings() { return this.settings }
LinkedHashMap getParentDeviceSettings() { return this.parent?.settings }
LinkedHashMap getChildDeviceSettings(ChildDeviceWrapper child) { return child?.settings }
Boolean hasParent() { return parent != null }


@CompileStatic
Boolean thisAppHasSetting(String settingName) {
  Boolean hasSetting = getAppSettings().containsKey("${settingName}".toString())
  return hasSetting
}

@CompileStatic
Boolean getBooleanDeviceSetting(String settingName) {
  return thisAppHasSetting(settingName) ? getAppSettings()[settingName] as Boolean : null
}

@CompileStatic
String getStringDeviceSetting(String settingName) {
  return thisAppHasSetting(settingName) ? getAppSettings()[settingName] as String : null
}

@CompileStatic
BigDecimal getBigDecimalAppSetting(String settingName) {
  return thisAppHasSetting(settingName) ? getAppSettings()[settingName] as BigDecimal : null
}

@CompileStatic
BigDecimal getBigDecimalAppSettingAsCelcius(String settingName) {
  if(thisAppHasSetting(settingName)) {
    BigDecimal val = getAppSettings()[settingName]
    return isCelciusScale() == true ? val : fToC(val)
  } else { return null }
}

@CompileStatic
Integer getIntegerAppSetting(String settingName) {
  return thisAppHasSetting(settingName) ? getAppSettings()[settingName] as Integer : null
}

@CompileStatic
String getEnumAppSetting(String settingName) {
  return thisAppHasSetting(settingName) ? "${getAppSettings()[settingName]}".toString() : null
}


@CompileStatic
Boolean hasChildren() {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return (allChildren != null && allChildren.size() > 0)
}

@CompileStatic
/**
 * Gets the DNI base for child devices.
 * In app context, uses the app ID. This is called by legacy driver code
 * that was adapted for app use.
 *
 * @return Base DNI for child devices
 */
String getThisDeviceDNI() {
  // In app context, use app-based DNI instead of device DNI
  return getBaseDNI()
}

/**
 * Sets the device network ID.
 * In app context, this is not applicable (apps don't have DNIs).
 * Kept for backward compatibility with driver code but logs warning if called.
 *
 * @param newDni The new device network ID
 */
@CompileStatic
void setThisDeviceNetworkId(String newDni) {
  logWarn("setThisDeviceNetworkId() called in app context - apps don't have DNIs. Ignoring.")
}

String getMACFromIPAddress(String ipAddress) { return getMACFromIP(ipAddress) }

String getIpAddressFromHexAddress(String hexString) {
  Integer[] i = hubitat.helper.HexUtils.hexStringToIntArray(hexString)
  String ip = i.join('.')
  return ip
}

/**
 * Sends an event in app context.
 * In app context, this sends an app event instead of a device event.
 * For child device events, use sendChildDeviceEvent() instead.
 *
 * @param name Event name
 * @param value Event value
 * @param unit Optional unit
 * @param descriptionText Optional description
 * @param isStateChange Whether this is a state change
 */
@CompileStatic
void sendDeviceEvent(String name, Object value, String unit = null, String descriptionText = null, Boolean isStateChange = false) {
  // In app context, send app event instead of device event
  sendAppEventHelper([name: name, value: value, unit: unit, descriptionText: descriptionText, isStateChange: isStateChange])
}

/**
 * Sends an event in app context using a properties map.
 * In app context, this sends an app event instead of a device event.
 * For child device events, use sendChildDeviceEvent() instead.
 *
 * @param properties Map of event properties
 */
@CompileStatic
void sendDeviceEvent(Map properties) {
  // In app context, send app event instead of device event
  sendAppEventHelper(properties)
}

@CompileStatic
void sendChildDeviceEvent(Map properties, ChildDeviceWrapper child) {
  if(child != null) {
    childSendEventHelper(child, properties)
  }
}

@CompileStatic
Boolean hasExtTempGen1(String settingName) {return getAppSettings().containsKey(settingName) == true}

/**
 * Sets a device setting with Map options.
 * In app context, the device parameter is required (apps don't have device settings).
 *
 * @param name Setting name
 * @param options Setting options map
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, Map options, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, options)
}

/**
 * Sets a device setting with Long value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, Long value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Sets a device setting with Boolean value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, Boolean value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Sets a device setting with String value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, String value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Sets a device setting with Double value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, Double value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Sets a device setting with Date value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, Date value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Sets a device setting with List value.
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceSetting(String name, List value, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceSetting() called without device parameter in app context - ignoring")
    return
  }
  deviceUpdateSettingHelper(dev, name, value)
}

/**
 * Removes a device setting.
 * In app context, this is not applicable (apps use different settings management).
 * Kept for backward compatibility with driver code.
 *
 * @param name The setting name to remove
 */
@CompileStatic
void removeDeviceSetting(String name) {
  logWarn("removeDeviceSetting() called in app context - not applicable for apps. Use app.removeSetting() instead.")
}

/**
 * Sets the device network ID based on MAC address.
 * In app context, this is not applicable (apps don't have DNIs).
 * Kept for backward compatibility with driver code.
 *
 * @param ipAddress IP address to derive MAC from
 */
@CompileStatic
void setDeviceNetworkIdByMacAddress(String ipAddress) {
  logWarn("setDeviceNetworkIdByMacAddress() called in app context - not applicable for apps. Ignoring.")
}

@CompileStatic
void scheduleTask(String sched, String taskName) {
  scheduleHelper(sched, taskName)
}

@CompileStatic
void unscheduleTask(String taskName) {
  unscheduleHelper(taskName)
}

Boolean isCelciusScale() {
  return getLocationHelper().temperatureScale == 'C'
}

/**
 * Gets a device data value.
 * In app context, the device parameter is required (apps use state, not data values).
 *
 * @param dataValueName Name of the data value
 * @param dev Target device (required in app context)
 * @return Data value as string, or null if device not specified
 */
@CompileStatic
String getDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("getDeviceDataValue() called without device parameter in app context - returning null")
    return null
  }
  return deviceGetDataValueHelper(dev, dataValueName)
}

/**
 * Gets a device data value as Integer.
 * @param dev Target device (required in app context)
 */
@CompileStatic
Integer getIntegerDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("getIntegerDeviceDataValue() called without device parameter in app context - returning null")
    return null
  }
  return deviceGetDataValueHelper(dev, dataValueName) as Integer
}

/**
 * Gets a device data value as Boolean.
 * @param dev Target device (required in app context)
 */
@CompileStatic
Boolean getBooleanDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("getBooleanDeviceDataValue() called without device parameter in app context - returning false")
    return false
  }
  return deviceGetDataValueHelper(dev, dataValueName) == 'true'
}

@CompileStatic
Boolean childHasAttribute(ChildDeviceWrapper child, String attributeName) {
  return childHasAttributeHelper(child, attributeName)
}

String getParentDeviceDataValue(String dataValueName) {
  def parentObj = getParentHelper()
  return parentObj?.getDeviceDataValue(dataValueName)
}

@CompileStatic
Integer getChildDeviceIntegerDataValue(ChildDeviceWrapper child, String dataValueName) {
  return childGetDeviceDataValueHelper(child, dataValueName) as Integer
}

@CompileStatic
String getChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName) {
  return childGetDeviceDataValueHelper(child, dataValueName)
}

@CompileStatic
Boolean childHasDataValue(ChildDeviceWrapper child, String dataValueName) {
  return getChildDeviceDataValue(child, dataValueName) != null
}

@CompileStatic
void setChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName, String valueToSet) {
  childUpdateDataValueHelper(child, dataValueName, valueToSet)
}

/**
 * Checks if a device has a specific data value.
 * In app context, the device parameter is required.
 *
 * @param dataValueName Name of the data value to check
 * @param dev Target device (required in app context)
 * @return true if the data value exists, false otherwise
 */
@CompileStatic
Boolean deviceHasDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("deviceHasDataValue() called without device parameter in app context - returning false")
    return false
  }
  return getDeviceDataValue(dataValueName, dev) != null
}

@CompileStatic
Boolean anyChildHasDataValue(String dataValueName) {
  if(hasParent() == true) {return false}
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.any{childHasDataValue(it, dataValueName)}
}

/**
 * Sets a data value on a device.
 * In app context, you must specify which child device to update.
 * Apps themselves don't have data values (use state instead).
 *
 * @param dataValueName Name of the data value
 * @param valueToSet Value to set
 * @param dev Target device (required in app context)
 */
@CompileStatic
void setDeviceDataValue(String dataValueName, String valueToSet, DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("setDeviceDataValue() called without device parameter in app context - ignoring. Use child device parameter.")
    return
  }
  deviceUpdateDataValueHelper(dev, dataValueName, valueToSet)
}

@CompileStatic
String getChildDeviceNetworkId(ChildDeviceWrapper child) {
  return child.getDeviceNetworkId()
}

@CompileStatic
String getBaseUri() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}"
  } else {
    return "http://${getAppSettings().ipAddress}"
  }
}

@CompileStatic
String getBaseUriRpc() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}/rpc"
  } else {
    return "http://${getAppSettings().ipAddress}/rpc"
  }
}

String getHubBaseUri() {
  return "http://${location.hub.localIP}:39501"
}

@CompileStatic
Long unixTimeSeconds() {
  return Instant.now().getEpochSecond()
}

@CompileStatic
String getWebSocketUri() {
  if(getAppSettings()?.ipAddress != null && getAppSettings()?.ipAddress != '') {return "ws://${getAppSettings()?.ipAddress}/rpc"}
  else {return null}
}
@CompileStatic
Boolean hasWebsocketUri() {
  return (getWebSocketUri() != null && getWebSocketUri().length() > 6)
}

@CompileStatic
Boolean hasIpAddress() {
  Boolean hasIpAddress = (getAppSettings()?.ipAddress != null && getAppSettings()?.ipAddress != '' && ((String)getAppSettings()?.ipAddress).length() > 6)
  return hasIpAddress
}

@CompileStatic
String getIpAddress() {
  if(hasIpAddress()) {return getStringDeviceSetting('ipAddress')} else {return null}
}

@CompileStatic setIpAddress(String ipAddress, Boolean updateSetting = false) {
  setThisDeviceNetworkId(getMACFromIPAddress(ipAddress))
  setDeviceDataValue('ipAddress', ipAddress)
  if(updateSetting == true) {
    logDebug("Incoming webhook IP address doesn't match what is currently set in driver preferences, updating preference value...")
    setDeviceSetting('ipAddress', ipAddress)
  }
}
/* #endregion */


// ╔══════════════════════════════════════════════════════════════╗
// ║  Capability & Device Type Checks                             ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Capability & Device Type Checks */
// MARK: Capability & Device Type Checks
// MARK: Capability Getters
Boolean hasCapabilityBatteryGen1() { return HAS_BATTERY_GEN1 == true }
Boolean hasCapabilityLuxGen1() { return HAS_LUX_GEN1 == true }
Boolean hasCapabilityTempGen1() { return HAS_TEMP_GEN1 == true }
Boolean hasCapabilityHumGen1() { return HAS_HUM_GEN1 == true }
Boolean hasCapabilityMotionGen1() { return HAS_MOTION_GEN1 == true }
Boolean hasCapabilityFloodGen1() { return HAS_FLOOD_GEN1 == true }
Boolean hasNoChildSwitch() { return NOCHILDSWITCH == true }
Boolean hasNoChildInput() { return NOCHILDINPUT == true }
Boolean hasNoChildCover() { return NOCHILDCOVER == true }
Boolean hasNoChildTemp() { return NOCHILDTEMP == true }
Boolean hasNoChildHumidity() { return NOCHILDHUMIDITY == true }
Boolean hasNoChildLight() { return NOCHILDLIGHT == true }
Boolean hasNoChildRgb() { return NOCHILDRGB == true }
Boolean hasNoChildRgbw() { return NOCHILDRGBW == true }
Boolean hasNoChildIlluminance() { return NOCHILDILLUMINANCE == true }
Boolean hasNoChildPm1() { return NOCHILDPM1 == true }
Boolean hasADCGen1() { return HAS_ADC_GEN1 == true }
Boolean hasPMGen1() { return HAS_PM_GEN1 == true }
Boolean hasExtTempGen1() { return HAS_EXT_TEMP_GEN1 == true }
Boolean hasExtHumGen1() { return HAS_EXT_HUM_GEN1 == true }

Integer getCoolTemp() { return COOLTEMP != null ? COOLTEMP : 6500 }
Integer getWarmTemp() { return WARMTEMP != null ? WARMTEMP : 3000 }

Boolean hasActionsToCreateList() { return ACTIONS_TO_CREATE != null }
List<String> getActionsToCreate() {
  if(hasActionsToCreateList() == true) { return ACTIONS_TO_CREATE }
  else {return []}
}
Boolean hasActionsToCreateEnabledTimesList() { return ACTIONS_TO_CREATE_ENABLED_TIMES != null }
List<String> getActionsToCreateEnabledTimes() {
  if(hasActionsToCreateEnabledTimesList() == true) { return ACTIONS_TO_CREATE_ENABLED_TIMES }
  else {return []}
}

Boolean deviceIsComponent() {return COMP == true}
Boolean deviceIsComponentInputSwitch() {return INPUTSWITCH == true}
Boolean deviceIsOverUnderSwitch() {return OVERUNDERSWITCH == true}

/**
 * Checks if a device has a specific capability.
 * In app context, you must specify which device to check.
 *
 * @param dev The device to check (required in app context)
 * @return true if device has the capability
 */
@CompileStatic
Boolean hasCapabilityBattery(DeviceWrapper dev = null) {
  if(dev == null) {
    logWarn("hasCapabilityBattery() called without device parameter in app context - returning false")
    return false
  }
  return deviceHasCapabilityHelper(dev, 'Battery') == true
}

@CompileStatic
Boolean hasCapabilityColorControl(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityColorControl() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'ColorControl') == true
}

@CompileStatic
Boolean hasCapabilityColorMode(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityColorMode() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'ColorMode') == true
}

@CompileStatic
Boolean hasCapabilityColorTemperature(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityColorTemperature() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'ColorTemperature') == true
}

@CompileStatic
Boolean hasCapabilityWhiteLevel(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityWhiteLevel() requires device parameter in app context"); return false }
  return deviceHasAttributeHelper(dev, 'whiteLevel') == true
}

@CompileStatic
Boolean hasCapabilityLight(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityLight() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'Light') == true
}

@CompileStatic
Boolean hasCapabilitySwitch(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilitySwitch() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'Switch') == true
}

@CompileStatic
Boolean hasCapabilityPresence(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityPresence() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'PresenceSensor') == true
}

@CompileStatic
Boolean hasCapabilityValve(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityValve() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'Valve') == true
}

@CompileStatic
Boolean hasCapabilityCover(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityCover() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'WindowShade') == true
}

@CompileStatic
Boolean hasCapabilityThermostatHeatingSetpoint(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityThermostatHeatingSetpoint() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'ThermostatHeatingSetpoint') == true
}

@CompileStatic
Boolean hasCapabilityCoverOrCoverChild(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityCoverOrCoverChild() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'WindowShade') == true || getCoverChildren()?.size() > 0
}

@CompileStatic
Boolean hasCapabilityCurrentMeter(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityCurrentMeter() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'CurrentMeter') == true
}

@CompileStatic
Boolean hasCapabilityPowerMeter(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityPowerMeter() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'PowerMeter') == true
}

@CompileStatic
Boolean hasCapabilityVoltageMeasurement(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityVoltageMeasurement() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'VoltageMeasurement') == true
}

@CompileStatic
Boolean hasCapabilityEnergyMeter(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityEnergyMeter() requires device parameter in app context"); return false }
  return deviceHasCapabilityHelper(dev, 'EnergyMeter') == true
}

@CompileStatic
Boolean hasCapabilityReturnedEnergyMeter(DeviceWrapper dev = null) {
  if(dev == null) { logWarn("hasCapabilityReturnedEnergyMeter() requires device parameter in app context"); return false }
  return deviceHasAttributeHelper(dev, 'returnedEnergy') == true
}

Boolean deviceIsBluGateway() {return DEVICEISBLUGATEWAY == true}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Imports                                                     ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Imports */
// MARK: Imports
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import com.hubitat.app.InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper
import com.hubitat.hub.domain.Event
import com.hubitat.hub.domain.Location
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovyx.net.http.HttpResponseException
import hubitat.device.HubResponse
import hubitat.scheduling.AsyncResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.StringReader
import java.io.StringWriter
/* #endregion */


// ╔══════════════════════════════════════════════════════════════╗
// ║  Logging Helpers                                             ║
// ╚══════════════════════════════════════════════════════════════╝
void logException(message) {log.error "${loggingLabel()}: ${message}"}
// void logError(message) {log.error "${loggingLabel()}: ${message}"}
// void logWarn(message) {log.warn "${loggingLabel()}: ${message}"}
// void logInfo(message) {if (settings.logEnable == true) {log.info "${loggingLabel()}: ${message}"}}
// void logDebug(message) {if (settings.logEnable == true && settings.debugLogEnable) {log.debug "${loggingLabel()}: ${message}"}}
// void logTrace(message) {if (settings.logEnable == true && settings.traceLogEnable) {log.trace "${loggingLabel()}: ${message}"}}

void logClass(obj) {
  logInfo("Object Class Name: ${getObjectClassName(obj)}")
}

void logJson(Map message) {
  if (settings.logEnable && settings.traceLogEnable) {
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  Driver Management & Version Tracking                       ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Driver Management */
// MARK: Driver Management

/**
 * Authenticates with the Hubitat hub to obtain a session cookie.
 * Reuses existing cookie from state if available. The cookie is required
 * for all driver management operations via the hub's internal API.
 *
 * @return Session cookie string, or null if authentication fails
 */
private String login() {
  // If we already have a valid cookie, try to reuse it
  if(state.hubCookie) {
    logDebug("Reusing existing cookie")
    return state.hubCookie
  }

  try {
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/login',
      requestContentType: 'application/x-www-form-urlencoded',
      body: [
        username: '',
        password: '',
        submit: 'Login'
      ],
      followRedirects: false,
      textParser: true,
      timeout: 15
    ]

    String cookie = null

    httpPost(params) { resp ->
      logDebug("Login response status: ${resp?.status}")
      if(resp?.status == 200 || resp?.status == 302) {
        def setCookieHeader = resp.headers['Set-Cookie']
        if(setCookieHeader) {
          String cookieValue = setCookieHeader.value ?: setCookieHeader.toString()
          cookie = cookieValue.split(';')[0]
          state.hubCookie = cookie  // Store for reuse
          logDebug("Got cookie: ${cookie?.take(20)}...")
        } else {
          logWarn("No Set-Cookie header in login response")
        }
      } else {
        logWarn("Unexpected login status: ${resp?.status}")
      }
    }

    if(!cookie) {
      logWarn("Failed to get authentication cookie")
    }
    return cookie
  } catch(Exception e) {
    logError("Login error: ${e.message}")
    return null
  }
}

/**
 * Retrieves all user-installed drivers from the hub.
 *
 * @return List of driver maps with id, name, and namespace properties
 */
private List getDriverList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15
    ]

    List drivers = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data?.drivers) {
        // Filter to only user drivers
        drivers = resp.data.drivers.findAll { it.type == 'usr' }
      }
    }

    return drivers
  } catch(Exception e) {
    logError("Error getting driver list: ${e.message}")
    return []
  }
}

/**
 * Gets the installed version of a specific driver from the hub.
 * Retrieves the driver's source code and extracts the version string
 * from the driver definition metadata.
 *
 * @param driverName The driver name to search for
 * @param namespace The driver namespace (e.g., 'ShellyUSA')
 * @return Version string extracted from driver source, or null if not found
 */
private String getInstalledDriverVersion(String driverName, String namespace) {
  String foundVersion = null

  try {
    String cookie = login()
    if(!cookie) {
      logWarn('Failed to authenticate with hub')
      return null
    }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        logDebug("Driver list response received, checking for ${driverName} in namespace ${namespace}")

        def userDrivers = resp.data?.drivers?.findAll { it.type == 'usr' }
        logDebug("Found ${userDrivers?.size()} user drivers")

        def driver = resp.data?.drivers?.find {
          it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
        }

        if(driver && driver.id) {
          Integer driverId = driver.id
          logDebug("Found driver ${driverName}, getting source code...")

          Map codeParams = [
            uri: "http://127.0.0.1:8080",
            path: '/driver/ajax/code',
            headers: [Cookie: cookie],
            query: [id: driverId]
          ]

          httpGet(codeParams) { codeResp ->
            if(codeResp?.status == 200 && codeResp.data?.source) {
              String source = codeResp.data.source
              def matcher = (source =~ /version:\s*['"]([^'"]+)['"]/)
              if(matcher.find()) {
                foundVersion = matcher.group(1)
                logDebug("Extracted version ${foundVersion} from ${driverName}")
              } else {
                logWarn("Could not find version pattern in source for ${driverName}")
              }
            } else {
              logWarn("Failed to get source code for ${driverName}, status: ${codeResp?.status}")
            }
          }
        } else if(!driver) {
          logDebug("Driver not found in hub: ${driverName} (${namespace})")
        }
      } else {
        logWarn("Failed to get driver list, status: ${resp?.status}")
      }
    }
  } catch(Exception e) {
    logWarn("Error getting version for ${driverName}: ${e.message}")
  }

  return foundVersion
}

/**
 * Gets the hub's internal version number for a driver.
 * This is different from the semantic version in the driver metadata and
 * is used by the hub for tracking updates.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @return Hub's internal version string, or null if not found
 */
private String getDriverVersionForUpdate(String driverName, String namespace) {
  String hubVersion = null

  try {
    String cookie = login()
    if(!cookie) { return null }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      def driver = resp.data?.drivers?.find {
        it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
      }

      if(driver?.id) {
        Map codeParams = [
          uri: "http://127.0.0.1:8080",
          path: '/driver/ajax/code',
          headers: [Cookie: cookie],
          query: [id: driver.id]
        ]

        httpGet(codeParams) { codeResp ->
          if(codeResp?.status == 200 && codeResp.data?.version) {
            hubVersion = codeResp.data.version.toString()
            logDebug("Got hub version ${hubVersion} for ${driverName}")
          }
        }
      }
    }
  } catch(Exception e) {
    logWarn("Error getting hub version for ${driverName}: ${e.message}")
  }

  return hubVersion
}

/**
 * Creates or updates a driver on the hub.
 * If the driver doesn't exist, creates it. If it exists, updates the source code.
 * Uses the hub's internal API endpoints for driver management.
 *
 * @param driverName The name of the driver
 * @param namespace The driver namespace (e.g., 'ShellyUSA')
 * @param sourceCode Complete driver source code
 * @param newVersionForLogging Version string for logging purposes
 * @return true if successful, false otherwise
 */
private Boolean updateDriver(String driverName, String namespace, String sourceCode, String newVersionForLogging) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    List allDrivers = getDriverList()
    Map targetDriver = allDrivers.find { driver ->
      driver.name == driverName && driver.namespace == namespace
    }

    if(!targetDriver) {
      // Driver doesn't exist - create it
      logInfo("Driver not found, creating new driver: ${namespace}.${driverName}")

      Map createParams = [
        uri: "http://127.0.0.1:8080",
        path: '/driver/save',
        requestContentType: 'application/x-www-form-urlencoded',
        headers: [
          'Cookie': cookie
        ],
        body: [
          id: '',
          version: '',
          create: '',
          source: sourceCode
        ],
        timeout: 300,
        ignoreSSLIssues: true
      ]

      Boolean result = false
      httpPost(createParams) { resp ->
        if(resp.headers?.Location != null) {
          String newId = resp.headers.Location.replaceAll("https?://127.0.0.1:(?:8080|8443)/driver/editor/", "")
          logInfo("Successfully created driver ${driverName} with id ${newId}")
          result = true
        } else {
          logError("Driver ${driverName} creation failed - no Location header")
          result = false
        }
      }
      return result
    }

    // Driver exists - update it
    logInfo("Updating existing driver ${driverName} (id: ${targetDriver.id})")

    String currentHubVersion = getDriverVersionForUpdate(driverName, namespace) ?: ''
    logDebug("Updating driver ${driverName}: hub version=${currentHubVersion}, new version=${newVersionForLogging}")

    Map updateParams = [
      uri: "http://127.0.0.1:8080",
      path: '/driver/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Cookie': cookie
      ],
      body: [
        id: targetDriver.id,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(updateParams) { resp ->
      logDebug("Driver update response: ${resp.data}")
      if(resp.data?.status == 'success') {
        logInfo("Successfully updated driver ${driverName}")
        result = true
      } else {
        logError("Driver ${driverName} update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating/creating driver ${driverName}: ${e.message}")
    return false
  }
}

/**
 * Downloads a file from a URL.
 *
 * @param uri The URL to download from
 * @return File contents as a string, or null on error
 */
private String downloadFile(String uri) {
  try {
    Map params = [
      uri: uri,
      contentType: 'text/plain',
      timeout: 30
    ]

    String fileContent = null

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        fileContent = resp.data.text
      }
    }

    return fileContent
  } catch(Exception e) {
    logError("Error downloading file from ${uri}: ${e.message}")
    return null
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Auto-Generated Driver Version Tracking                     ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Driver Version Tracking */
// MARK: Driver Version Tracking

/**
 * Initializes the auto-generated driver tracking state.
 * Creates the state structure if it doesn't exist to track installed
 * drivers, their versions, and which devices are using them.
 */
private void initializeDriverTracking() {
  if(!state.autoDrivers) {
    state.autoDrivers = [:]
  }
  logDebug("Driver tracking initialized, currently tracking ${state.autoDrivers.size()} drivers")
}

/**
 * Registers an auto-generated driver in the tracking system.
 * Stores the driver name, namespace, version, components it supports,
 * and timestamp of installation/update.
 *
 * @param driverName The name of the generated driver
 * @param namespace The driver namespace (e.g., 'ShellyUSA')
 * @param version The semantic version of the driver
 * @param components List of Shelly components this driver supports
 */
private void registerAutoDriver(String driverName, String namespace, String version, List<String> components) {
  initializeDriverTracking()

  String key = "${namespace}.${driverName}"
  state.autoDrivers[key] = [
    name: driverName,
    namespace: namespace,
    version: version,
    components: components,
    installedAt: now(),
    lastUpdated: now(),
    devicesUsing: []
  ]

  logInfo("Registered auto-generated driver: ${key} v${version} with components: ${components}")
}

/**
 * Associates a device with an auto-generated driver.
 * Tracks which devices are using which auto-generated drivers to enable
 * proper version management and cleanup.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @param deviceDNI The device network ID using this driver
 */
private void associateDeviceWithDriver(String driverName, String namespace, String deviceDNI) {
  initializeDriverTracking()

  String key = "${namespace}.${driverName}"
  if(!state.autoDrivers[key]) {
    logWarn("Cannot associate device ${deviceDNI} with unknown driver ${key}")
    return
  }

  if(!state.autoDrivers[key].devicesUsing) {
    state.autoDrivers[key].devicesUsing = []
  }

  if(!state.autoDrivers[key].devicesUsing.contains(deviceDNI)) {
    state.autoDrivers[key].devicesUsing.add(deviceDNI)
    logDebug("Associated device ${deviceDNI} with driver ${key}")
  }
}

/**
 * Removes a device association from an auto-generated driver.
 * Called when a device is deleted or switches to a different driver.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @param deviceDNI The device network ID to disassociate
 */
private void disassociateDeviceFromDriver(String driverName, String namespace, String deviceDNI) {
  initializeDriverTracking()

  String key = "${namespace}.${driverName}"
  if(state.autoDrivers[key]?.devicesUsing) {
    state.autoDrivers[key].devicesUsing.remove(deviceDNI)
    logDebug("Disassociated device ${deviceDNI} from driver ${key}")

    // If no devices are using this driver anymore, we could optionally clean it up
    if(state.autoDrivers[key].devicesUsing.size() == 0) {
      logInfo("Driver ${key} is no longer in use by any devices")
    }
  }
}

/**
 * Gets the version of a tracked auto-generated driver.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @return Version string, or null if driver is not tracked
 */
private String getTrackedDriverVersion(String driverName, String namespace) {
  initializeDriverTracking()
  String key = "${namespace}.${driverName}"
  return state.autoDrivers[key]?.version
}

/**
 * Updates the version of a tracked auto-generated driver.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @param newVersion The new version string
 */
private void updateTrackedDriverVersion(String driverName, String namespace, String newVersion) {
  initializeDriverTracking()
  String key = "${namespace}.${driverName}"

  if(state.autoDrivers[key]) {
    String oldVersion = state.autoDrivers[key].version
    state.autoDrivers[key].version = newVersion
    state.autoDrivers[key].lastUpdated = now()
    logInfo("Updated driver ${key} version from ${oldVersion} to ${newVersion}")
  }
}

/**
 * Gets all devices using a specific auto-generated driver.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @return List of device DNIs using this driver
 */
private List<String> getDevicesUsingDriver(String driverName, String namespace) {
  initializeDriverTracking()
  String key = "${namespace}.${driverName}"
  return state.autoDrivers[key]?.devicesUsing ?: []
}

/**
 * Gets a summary of all tracked auto-generated drivers.
 *
 * @return Map of driver keys to their tracking information
 */
private Map getAllTrackedDrivers() {
  initializeDriverTracking()
  return state.autoDrivers
}

/**
 * Checks if an auto-generated driver needs to be updated.
 * Compares the installed version on the hub with the tracked version
 * in app state to detect version mismatches.
 *
 * @param driverName The driver name
 * @param namespace The driver namespace
 * @return true if the driver needs updating, false otherwise
 */
private Boolean driverNeedsUpdate(String driverName, String namespace) {
  String trackedVersion = getTrackedDriverVersion(driverName, namespace)
  if(!trackedVersion) {
    logDebug("Driver ${namespace}.${driverName} is not tracked")
    return false
  }

  String installedVersion = getInstalledDriverVersion(driverName, namespace)
  if(!installedVersion) {
    logWarn("Driver ${namespace}.${driverName} is tracked but not installed on hub")
    return true
  }

  Boolean needsUpdate = trackedVersion != installedVersion
  if(needsUpdate) {
    logInfo("Driver ${namespace}.${driverName} version mismatch: tracked=${trackedVersion}, installed=${installedVersion}")
  }

  return needsUpdate
}

/* #endregion Driver Version Tracking */