@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
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

            paragraph "Recent log lines (most recent first):"
            String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap;'>${logs ?: 'No logs yet.'}</pre>"
        }
    }
}

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

void installed() { initialize() }

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
            paragraph "Recent log lines (most recent first):"
            String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap;'>${logs ?: 'No logs yet.'}</pre>"
        }
    }
}

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

void systemStartHandler(evt) {
    logDebug('System start detected, registering mDNS listeners')
    startMdnsDiscovery()
}

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

void updateDiscoveryTimer() {
    if (!state.discoveryRunning || !state.discoveryEndTime) {
        logDebug("updateDiscoveryTimer: not running, stopping timer updates")
        return
    }
    Integer remainingSecs = getRemainingDiscoverySeconds()
    logDebug("updateDiscoveryTimer: sending event with ${remainingSecs} seconds remaining")

    // Send event for real-time browser update
    app.sendEvent(name: 'discoveryTimer', value: "Discovery time remaining: ${remainingSecs} seconds")

    // Continue scheduling if time remaining
    if (remainingSecs > 0) {
        runIn(1, 'updateDiscoveryTimer')
    } else {
        logDebug("updateDiscoveryTimer: timer reached 0, stopping updates")
    }
}

void updateRecentLogs() {
    // Send the most recent 10 log lines to the browser for the app-state binding
    String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
    app.sendEvent(name: 'recentLogs', value: (logs ?: 'No logs yet.'))

    // Continue updating once per second while discovery is running
    if (state.discoveryRunning) {
        runIn(1, 'updateRecentLogs')
    }
}

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
                if (!state.discoveredShellys.containsKey(key)) {
                    logDebug("Found NEW Shelly: ${deviceName} at ${ip4}:${port} (gen=${gen}, app=${deviceApp}, ver=${ver})")
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
        String info = "${name} (${ip}:${port})"
        if (deviceApp || gen || ver) {
            List<String> extras = []
            if (deviceApp) { extras << deviceApp }
            if (gen) { extras << "Gen${gen}" }
            if (ver) { extras << "v${ver}" }
            info += " [${extras.join(', ')}]"
        }
        return info
    }

    names.sort()

    // Return a single-line HTML string with explicit <br/> separators so there is no leading/trailing blank line
    return names.join('<br/>')
}

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
// Follows the driver library pattern: POST JSON-RPC to /rpc, capture result
// in a variable outside the closure (return from inside httpPostJson closure
// only returns from the closure, not the outer method).
private void fetchAndStoreDeviceInfo(String ipKey) {
    if (!ipKey) { return }
    Map device = state.discoveredShellys[ipKey]
    if (!device) {
        appendLog('warn', "Get Device Info: no discovered entry for ${ipKey}")
        return
    }

    String ip = (device.ipAddress ?: ipKey).toString()
    Integer port = (device.port ?: 80) as Integer
    String baseUrl = (port == 80) ? "http://${ip}" : "http://${ip}:${port}"

    logDebug("fetchAndStoreDeviceInfo: fetching from ${baseUrl}")
    appendLog('debug', "Getting device info from ${ip}")

    try {
        // 1) Shelly.GetDeviceInfo — device identity (id, mac, model, gen, fw, app, auth, profile)
        Map deviceInfo = rpcCall(baseUrl, 'Shelly.GetDeviceInfo', [ident: true])
        if (!deviceInfo) {
            appendLog('warn', "No device info returned from ${ip} — device may be offline or not Gen2+")
            return
        }

        // 2) Shelly.GetConfig — full component configuration (switch, input, sys, wifi, etc.)
        Map deviceConfig = rpcCall(baseUrl, 'Shelly.GetConfig')

        // 3) Shelly.GetStatus — live status of all components (power, temperature, relay state, etc.)
        Map deviceStatus = rpcCall(baseUrl, 'Shelly.GetStatus')

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
        parts << "model=${device.model ?: 'n/a'}"
        parts << "gen=${device.gen ?: 'n/a'}"
        parts << "ver=${device.ver ?: 'n/a'}"
        if (device.mac) { parts << "mac=${device.mac}" }
        if (device.profile) { parts << "profile=${device.profile}" }
        if (device.auth_en) { parts << "auth=enabled" }
        if (deviceConfig) { parts << "config=OK" }
        if (deviceStatus) { parts << "status=OK" }
        appendLog('info', "Device info for ${device.name} (${ip}): ${parts.join(', ')}")

        logDebug("fetchAndStoreDeviceInfo: deviceInfo keys=${deviceInfo.keySet()}")
        if (deviceConfig) { logDebug("fetchAndStoreDeviceInfo: deviceConfig keys=${deviceConfig.keySet()}") }
        if (deviceStatus) { logDebug("fetchAndStoreDeviceInfo: deviceStatus keys=${deviceStatus.keySet()}") }

        sendFoundShellyEvents()
    } catch (Exception e) {
        appendLog('error', "Failed to get device info from ${ip}: ${e.message}")
        logDebug("fetchAndStoreDeviceInfo exception: ${e.class.simpleName}: ${e.message}")
    }
}

/**
 * Execute a Shelly Gen2+ JSON-RPC call over HTTP.
 * Tries POST /rpc first (full JSON-RPC envelope), falls back to GET /rpc/Method convenience path.
 * Uses the driver library pattern of capturing the response in a variable outside the closure.
 *
 * @param baseUrl  e.g. "http://192.168.1.41"
 * @param method   e.g. "Shelly.GetDeviceInfo"
 * @param params   optional params map, e.g. [ident: true]
 * @return         the "result" map from the RPC response, or null on failure
 */
private Map rpcCall(String baseUrl, String method, Map params = [:]) {
    Map result = null

    // --- Attempt 1: POST /rpc with JSON-RPC body (preferred, matches driver library pattern) ---
    try {
        Map rpcBody = [id: 0, src: 'hubitat-discovery', method: method]
        if (params && params.size() > 0) { rpcBody.params = params }

        Map httpParams = [
            uri: "${baseUrl}/rpc",
            body: rpcBody,
            contentType: 'application/json',
            requestContentType: 'application/json',
            timeout: 10
        ]

        httpPost(httpParams) { resp ->
            if (resp?.status == 200 && resp?.data) {
                // JSON-RPC response wraps the actual data in a "result" key
                result = (resp.data instanceof Map && resp.data.containsKey('result')) ? resp.data.result : resp.data
            }
        }
    } catch (Exception e) {
        logDebug("rpcCall POST ${method} failed for ${baseUrl}: ${e.class.simpleName}: ${e.message}")
    }

    if (result) { return result }

    // --- Attempt 2: GET /rpc/Method convenience path (result is returned directly, no wrapper) ---
    try {
        String getUri = "${baseUrl}/rpc/${method}"
        // Append query params for GET requests if needed
        if (params && params.size() > 0) {
            List<String> queryParts = params.collect { k, v -> "${k}=${v}" }
            getUri += "?${queryParts.join('&')}"
        }

        httpGet([uri: getUri, contentType: 'application/json', timeout: 10]) { resp ->
            if (resp?.status == 200 && resp?.data) {
                result = (resp.data instanceof Map) ? resp.data : null
            }
        }
    } catch (Exception e) {
        logDebug("rpcCall GET ${method} failed for ${baseUrl}: ${e.class.simpleName}: ${e.message}")
    }

    return result
}

// Small helper to truncate long response bodies for logs
private String truncateForLog(Object obj, Integer maxLen = 240) {
    if (!obj) { return '' }
    String s = obj.toString()
    if (s.length() <= maxLen) { return s }
    return s.substring(0, maxLen) + '...'
}

private Integer getRemainingDiscoverySeconds() {
    if (!state.discoveryEndTime) { return 0 }
    Long remainingMs = Math.max(0L, (state.discoveryEndTime as Long) - now())
    return (Integer)(remainingMs / 1000L)
}

private Integer getDiscoveryDurationSeconds() { 120 }
private Integer getMdnsPollSeconds() { 5 }

private void applyPendingDisplayLevel() {
    String pending = state.pendingDisplayLevel?.toString()
    if (pending) {
        app.updateSetting('displayLogLevel', [type: 'enum', value: pending])
        state.pendingDisplayLevel = null
    }
}

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
    app.sendEvent(name: 'recentLogs', value: (state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : 'No logs yet.'))
}


private void appendLog(String level, String msg) {
    state.recentLogs = state.recentLogs ?: []

    // Only append messages that meet the app display threshold
    String displayLevel = (settings?.displayLogLevel ?: (settings?.logLevel ?: 'warn'))?.toString()
    if (levelPriority(level) >= levelPriority(displayLevel)) {
        state.recentLogs << "${new Date().format('yyyy-MM-dd HH:mm:ss')} - ${level?.toUpperCase()}: ${msg}"
        if (state.recentLogs.size() > 300) {
            state.recentLogs = state.recentLogs[-300..-1]
        }

        // Push the most recent 10 lines to the app UI for live updates
        String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
        app.sendEvent(name: 'recentLogs', value: (logs ?: 'No logs yet.'))
    }
}

private List<String> LOG_LEVELS() { ['trace','debug','info','warn','error','off'] }

private Integer levelPriority(String level) {
    if (!level) { return LOG_LEVELS().indexOf('debug') }
    int idx = LOG_LEVELS().indexOf(level.toString().toLowerCase())
    return idx >= 0 ? idx : LOG_LEVELS().indexOf('debug')
}

private Boolean shouldLogOverall(String level) {
    return levelPriority(level) >= levelPriority(settings?.logLevel ?: 'debug')
}

private Boolean shouldDisplay(String level) {
    return levelPriority(level) >= levelPriority(settings?.displayLogLevel ?: (settings?.logLevel ?: 'warn'))
}

private void logTrace(String msg) {
    if (!shouldLogOverall('trace')) { return }
    log.trace msg
    if (shouldDisplay('trace')) { appendLog('trace', msg) }
}

private void logDebug(String msg) {
    if (!shouldLogOverall('debug')) { return }
    log.debug msg
    if (shouldDisplay('debug')) { appendLog('debug', msg) }
}

private void logInfo(String msg) {
    if (!shouldLogOverall('info')) { return }
    log.info msg
    if (shouldDisplay('info')) { appendLog('info', msg) }
}

private void logWarn(String msg) {
    if (!shouldLogOverall('warn')) { return }
    log.warn msg
    if (shouldDisplay('warn')) { appendLog('warn', msg) }
}

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
LinkedHashMap postCommandSync(LinkedHashMap command) {
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
    if(ex.getStatusCode() != 401) {logWarn("Exception: ${ex}")}
    setAuthIsEnabled(true)
    String authToProcess = ex.getResponse().getAllHeaders().find{ it.getValue().contains('nonce')}.getValue().replace('Digest ', '')
    authToProcess = authToProcess.replace('qop=','"qop":').replace('realm=','"realm":').replace('nonce=','"nonce":').replace('algorithm=SHA-256','"algorithm":"SHA-256","nc":1')
    processUnauthorizedMessage("{${authToProcess}}".toString())
    try {
      if(authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
      params.body = command
      httpPost(params) { resp -> if(resp.getStatus() == 200) { json = resp.getData() } }
    } catch(HttpResponseException ex2) {
      logError('Auth failed a second time. Double check password correctness.')
    }
  }
  logTrace("postCommandSync returned: ${prettyJson(json)}")
  return json
}

LinkedHashMap parentPostCommandSync(LinkedHashMap command) {
  if(hasParent() == true) { return parent?.postCommandSync(command) }
  else { return postCommandSync(command) }
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
ChildDeviceWrapper createChildSwitch(Integer id, String additionalId = null) {
  String dni = additionalId == null ? "${getThisDeviceDNI()}-switch${id}" : "${getThisDeviceDNI()}-${additionalId}-switch${id}"
  ChildDeviceWrapper child = getShellyDevice(dni)
  if (child == null) {
    String driverName = additionalId == null ? 'Shelly Switch Component' : 'Shelly OverUnder Switch Component'
    String labelText = thisDevice().getLabel() != null ? "${thisDevice().getLabel()}" : "${driverName}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - Dimmer ${id}" : "${driverName} - Dimmer ${id}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - RGB ${id}" : "${driverName} - RGB ${id}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - RGBW ${id}" : "${driverName} - RGBW ${id}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - Switch ${id}" : "${driverName} - Switch ${id}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - EM${id} - phase ${phase}" : "${driverName} - EM${id} - phase ${phase}"
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
    String label = thisDevice().getLabel() != null ? "${thisDevice().getLabel()} - EM ${id}" : "${driverName} - EM ${id}"
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
    String label = "${thisDevice().getLabel()} - Input ${inputType} ${id}"
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
    String label = "${thisDevice().getLabel()} - Cover ${id}"
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
    String label = "${thisDevice().getLabel()} - Temperature ${id}"
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
    String label = "${thisDevice().getLabel()} - Temperature ${id}"
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
    String label = "${thisDevice().getLabel()} - Temperature & Humidity${id}"
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
    String label = "${thisDevice().getLabel()} - Illuminance ${id}"
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
    String label = "${thisDevice().getLabel()} - LED RGB"
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
    String label = "${thisDevice().getLabel()} - LED RGB Power On"
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
    String label = "${thisDevice().getLabel()} - LED RGB Power Off"
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
    String label = "${thisDevice().getLabel()} - ADC ${id}"
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
String getPassword() { return getDeviceSettings().devicePassword as String }
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
  return thisDevice().getDataValue('auth') == 'true'
}
@CompileStatic
void setAuthIsEnabled(Boolean auth) {
  thisDevice().updateDataValue('auth', auth.toString())
}

@CompileStatic
Boolean authIsEnabledGen1() {
  Boolean authEnabled = (
    getDeviceSettings()?.deviceUsername != null &&
    getDeviceSettings()?.devicePassword != null &&
    getDeviceSettings()?.deviceUsername != '' &&
    getDeviceSettings()?.devicePassword != ''
  )
  setAuthIsEnabled(authEnabled)
  return authEnabled
}

// @CompileStatic
// void performAuthCheck() { shellyGetStatusWs('authCheck') }

@CompileStatic
String getBasicAuthHeader() {
  if(getDeviceSettings()?.deviceUsername != null && getDeviceSettings()?.devicePassword != null) {
    return base64Encode("${getDeviceSettings().deviceUsername}:${getDeviceSettings().devicePassword}".toString())
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
DeviceWrapper thisDevice() { return this.device }
ArrayList<ChildDeviceWrapper> getThisDeviceChildren() { return getChildDevices() }
ArrayList<ChildDeviceWrapper> getParentDeviceChildren() { return parent?.getChildDevices() }

LinkedHashMap getDeviceSettings() { return this.settings }
LinkedHashMap getParentDeviceSettings() { return this.parent?.settings }
LinkedHashMap getChildDeviceSettings(ChildDeviceWrapper child) { return child?.settings }
Boolean hasParent() { return parent != null }


@CompileStatic
Boolean thisDeviceHasSetting(String settingName) {
  Boolean hasSetting = getDeviceSettings().containsKey("${settingName}".toString())
  return hasSetting
}

@CompileStatic
Boolean getBooleanDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as Boolean : null
}

@CompileStatic
String getStringDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as String : null
}

@CompileStatic
BigDecimal getBigDecimalDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as BigDecimal : null
}

@CompileStatic
BigDecimal getBigDecimalDeviceSettingAsCelcius(String settingName) {
  if(thisDeviceHasSetting(settingName)) {
    BigDecimal val = getDeviceSettings()[settingName]
    return isCelciusScale() == true ? val : fToC(val)
  } else { return null }
}

@CompileStatic
Integer getIntegerDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? getDeviceSettings()[settingName] as Integer : null
}

@CompileStatic
String getEnumDeviceSetting(String settingName) {
  return thisDeviceHasSetting(settingName) ? "${getDeviceSettings()[settingName]}".toString() : null
}


@CompileStatic
Boolean hasChildren() {
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return (allChildren != null && allChildren.size() > 0)
}

@CompileStatic
String getThisDeviceDNI() { return thisDevice().getDeviceNetworkId() }

@CompileStatic
void setThisDeviceNetworkId(String newDni) { thisDevice().setDeviceNetworkId(newDni) }

String getMACFromIPAddress(String ipAddress) { return getMACFromIP(ipAddress) }

String getIpAddressFromHexAddress(String hexString) {
  Integer[] i = hubitat.helper.HexUtils.hexStringToIntArray(hexString)
  String ip = i.join('.')
  return ip
}

@CompileStatic
void sendDeviceEvent(String name, Object value, String unit = null, String descriptionText = null, Boolean isStateChange = false) {
  thisDevice().sendEvent([name: name, value: value, unit: unit, descriptionText: descriptionText, isStateChange: isStateChange])
}

@CompileStatic
void sendDeviceEvent(Map properties) {thisDevice().sendEvent(properties)}

@CompileStatic
void sendChildDeviceEvent(Map properties, ChildDeviceWrapper child) {if(child != null){child.sendEvent(properties)}}

@CompileStatic
Boolean hasExtTempGen1(String settingName) {return getDeviceSettings().containsKey(settingName) == true}

@CompileStatic
void setDeviceSetting(String name, Map options, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, options)
}

void setDeviceSetting(String name, Long value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Boolean value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, String value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Double value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, Date value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}

void setDeviceSetting(String name, List value, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  dev.updateSetting(name, value)
}



void removeDeviceSetting(String name) {thisDevice().removeSetting(name)}

@CompileStatic
void setDeviceNetworkIdByMacAddress(String ipAddress) {
  String oldDni = getThisDeviceDNI()
  String newDni = getMACFromIPAddress(ipAddress)
  if(oldDni != newDni) {
    logDebug("Current DNI does not match device MAC address... Setting device network ID to ${newDni}")
    setThisDeviceNetworkId(newDni)
  } else {
    logTrace('Device DNI does not need updated, moving on...')
  }
}

void scheduleTask(String sched, String taskName) {schedule(sched, taskName)}
void unscheduleTask(String taskName) { unschedule(taskName) }

Boolean isCelciusScale() { getLocation().temperatureScale == 'C' }

String getDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName)
}

Integer getIntegerDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName) as Integer
}

Boolean getBooleanDeviceDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  return dev.getDataValue(dataValueName) == 'true'
}


Boolean childHasAttribute(ChildDeviceWrapper child, String attributeName) {
  return child.hasAttribute(attributeName)
}

String getParentDeviceDataValue(String dataValueName) {
  return parent?.getDeviceDataValue(dataValueName)
}

Integer getChildDeviceIntegerDataValue(ChildDeviceWrapper child, String dataValueName) {
  return child.getDeviceDataValue(dataValueName) as Integer
}

String getChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName) {
  return child.getDeviceDataValue(dataValueName)
}

@CompileStatic
Boolean childHasDataValue(ChildDeviceWrapper child, String dataValueName) {
  return getChildDeviceDataValue(child, dataValueName) != null
}

void setChildDeviceDataValue(ChildDeviceWrapper child, String dataValueName, String valueToSet) {
  child.updateDataValue(dataValueName, valueToSet)
}

@CompileStatic
Boolean deviceHasDataValue(String dataValueName, DeviceWrapper dev = null) {
  if(dev == null) {dev = thisDevice()}
  getDeviceDataValue(dataValueName, dev) != null
}

@CompileStatic
Boolean anyChildHasDataValue(String dataValueName) {
  if(hasParent() == true) {return false}
  List<ChildDeviceWrapper> allChildren = getThisDeviceChildren()
  return allChildren.any{childHasDataValue(it, dataValueName)}
}

void setDeviceDataValue(String dataValueName, String valueToSet) {
  thisDevice().updateDataValue(dataValueName, valueToSet)
}

String getChildDeviceNetworkId(ChildDeviceWrapper child) {
  return child.getDeviceNetworkId()
}

@CompileStatic
String getBaseUri() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}"
  } else {
    return "http://${getDeviceSettings().ipAddress}"
  }
}

@CompileStatic
String getBaseUriRpc() {
  if(hasParent() == true) {
    return "http://${getParentDeviceSettings().ipAddress}/rpc"
  } else {
    return "http://${getDeviceSettings().ipAddress}/rpc"
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
  if(getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '') {return "ws://${getDeviceSettings()?.ipAddress}/rpc"}
  else {return null}
}
@CompileStatic
Boolean hasWebsocketUri() {
  return (getWebSocketUri() != null && getWebSocketUri().length() > 6)
}

@CompileStatic
Boolean hasIpAddress() {
  Boolean hasIpAddress = (getDeviceSettings()?.ipAddress != null && getDeviceSettings()?.ipAddress != '' && ((String)getDeviceSettings()?.ipAddress).length() > 6)
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

Boolean hasCapabilityBattery() { return device.hasCapability('Battery') == true }
Boolean hasCapabilityColorControl() { return device.hasCapability('ColorControl') == true }
Boolean hasCapabilityColorMode() { return device.hasCapability('ColorMode') == true }
Boolean hasCapabilityColorTemperature() { return device.hasCapability('ColorTemperature') == true }
Boolean hasCapabilityWhiteLevel() { return device.hasAttribute('whiteLevel') == true }
Boolean hasCapabilityLight() { return device.hasCapability('Light') == true }
Boolean hasCapabilitySwitch() { return device.hasCapability('Switch') == true }
Boolean hasCapabilityPresence() { return device.hasCapability('PresenceSensor') == true }
Boolean hasCapabilityValve() { return device.hasCapability('Valve') == true }
Boolean hasCapabilityCover() { return device.hasCapability('WindowShade') == true }
Boolean hasCapabilityThermostatHeatingSetpoint() { return device.hasCapability('ThermostatHeatingSetpoint') == true }
Boolean hasCapabilityCoverOrCoverChild() { return device.hasCapability('WindowShade') == true || getCoverChildren()?.size() > 0 }

Boolean hasCapabilityCurrentMeter() { return device.hasCapability('CurrentMeter') == true }
Boolean hasCapabilityPowerMeter() { return device.hasCapability('PowerMeter') == true }
Boolean hasCapabilityVoltageMeasurement() { return device.hasCapability('VoltageMeasurement') == true }
Boolean hasCapabilityEnergyMeter() { return device.hasCapability('EnergyMeter') == true }
Boolean hasCapabilityReturnedEnergyMeter() { return device.hasAttribute('returnedEnergy') == true }

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