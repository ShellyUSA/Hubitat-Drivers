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
