@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, Boolean> foundDevices = new java.util.concurrent.ConcurrentHashMap<String, Boolean>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()

// IMPORTANT: When bumping the version in definition() below, also update APP_VERSION.
// These two values MUST match. APP_VERSION is used at runtime to embed the version
// into generated drivers and to detect app updates for automatic driver regeneration.
@Field static final String APP_VERSION = "1.0.9"

// GitHub repository and branch used for fetching resources (scripts, component definitions, auto-updates).
@Field static final String GITHUB_REPO = 'ShellyUSA/Hubitat-Drivers'
@Field static final String GITHUB_BRANCH = 'master'

// Script names (as they appear on the Shelly device) that are managed by this app.
// Only these scripts will be considered for automatic removal.
@Field static final List<String> MANAGED_SCRIPT_NAMES = [
    'switchstatus',
    'powermonitoring',
    'HubitatBLEHelper'
]

definition(
    name: "Shelly Device Manager",
    namespace: "ShellyUSA",
    author: "Daniel Winks",
    description: "Discover, configure, and manage Shelly WiFi devices on Hubitat",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true,
    singleThreaded: true,
    version: "1.0.9"
)

preferences {
    page(name: "mainPage", install: true, uninstall: true)
    page(name: "createDevicesPage")
    page(name: "deviceConfigPage")
}

/**
 * Renders the main discovery page for the Shelly Device Manager app.
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

    dynamicPage(name: "mainPage", title: "Shelly Device Manager v${APP_VERSION}", install: true, uninstall: true) {
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
            paragraph "<pre class='app-state-${app.id}-shellyDiscovered' style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>${getFoundShellys() ?: 'No Shelly devices discovered yet.'}</pre>"

            href "createDevicesPage", title: "Create Devices", description: "Select discovered Shelly devices to create"
            href "deviceConfigPage", title: "Device Configuration", description: "Configure installed Shelly devices"
        }

        section("Options", hideable: true) {
            input name: 'enableAutoUpdate', type: 'bool', title: 'Enable auto-update',
                description: 'Automatically checks for and installs app updates from GitHub daily at 3AM.',
                defaultValue: true, submitOnChange: true
            input name: 'enableAggressiveUpdate', type: 'bool', title: 'Aggressive auto-update (dev)',
                description: 'Checks GitHub every minute for code changes on the current branch and auto-updates. For development use.',
                defaultValue: false, submitOnChange: true
            input name: 'enableWatchdog', type: 'bool', title: 'Enable IP address watchdog',
                description: 'Periodically scans for device IP changes via mDNS and automatically updates child devices. Also triggers a scan when a device command fails.',
                defaultValue: true, submitOnChange: true
        }

        section("Driver Management", hideable: true, hidden: true) {
            input name: 'rebuildOnUpdate', type: 'bool', title: 'Rebuild drivers when app is updated',
                description: 'Automatically rebuilds in-use drivers and removes unused ones whenever the app version changes.',
                defaultValue: true, submitOnChange: true

            Map allDrivers = state.autoDrivers ?: [:]
            if (allDrivers.isEmpty()) {
                paragraph "No auto-generated drivers are currently tracked."
            } else {
                // Count actual child devices per driver
                def childDevices = getChildDevices() ?: []
                paragraph "<b>${allDrivers.size()}</b> auto-generated driver(s) tracked (app v${getAppVersion()}):"
                allDrivers.each { key, info ->
                    Integer deviceCount = childDevices.count { it.typeName == info.name }
                    paragraph "  - ${info.name} (${deviceCount} device(s))"
                }
            }

            if (state.driverRebuildInProgress) {
                paragraph "<b>Rebuild in progress...</b> (${(state.driverRebuildQueue?.size() ?: 0)} remaining)"
            } else {
                input 'btnForceRebuildDrivers', 'button', title: 'Force Rebuild All Drivers', submitOnChange: true
            }
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
            String storedDisplay = rawDisplay ? (levelOrder.contains(rawDisplay.toLowerCase()) ? rawDisplay.toLowerCase() : (levelLabels.find { k, v -> v.equalsIgnoreCase(rawOverall) }?.key)) : null

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

        if (count == 0) {
            appendLog('warn', 'Create Devices: no devices selected')
            return
        }

        logDebug("Creating ${count} device(s)")
        appendLog('info', "Creating ${count} device(s)...")

        selected.each { String ipKey ->
            createShellyDevice(ipKey)
        }
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

    if (buttonName == 'btnRemoveDevices') {
        List<String> selected = settings?.selectedToRemove ?: []
        if (!(selected instanceof List) && selected) { selected = [selected] }
        if (!selected || selected.size() == 0) {
            appendLog('warn', 'Remove Devices: no devices selected')
            return
        }

        selected.each { String dni ->
            def device = getChildDevice(dni)
            if (device) {
                String name = device.displayName
                deleteChildDevice(dni)
                logInfo("Removed device: ${name} (${dni})")
                appendLog('info', "Removed: ${name}")
            }
        }
        app.removeSetting('selectedToRemove')
    }

    if (buttonName == 'btnForceRebuildDrivers') {
        Map allDrivers = state.autoDrivers ?: [:]
        if (allDrivers.isEmpty()) {
            appendLog('warn', 'No auto-generated drivers to rebuild')
            return
        }
        logInfo("Manual force rebuild of all drivers requested")
        appendLog('info', "Force rebuilding ${allDrivers.size()} driver(s)...")

        // Update the stored version to current before rebuilding
        state.lastAutoconfVersion = getAppVersion()
        rebuildAllTrackedDrivers()
    }

    if (buttonName == 'btnInstallScripts') {
        String selectedDni = settings?.selectedConfigDevice as String
        if (!selectedDni) {
            appendLog('warn', 'Install Scripts: no device selected')
            return
        }
        def device = getChildDevice(selectedDni)
        if (!device) {
            appendLog('warn', "Install Scripts: device not found for DNI ${selectedDni}")
            return
        }
        String ip = device.getDataValue('ipAddress')
        if (!ip) {
            appendLog('warn', "Install Scripts: no IP address for device ${device.displayName}")
            return
        }
        installRequiredScripts(ip)
    }

    if (buttonName == 'btnRemoveNonRequiredScripts') {
        String selectedDni = settings?.selectedConfigDevice as String
        if (!selectedDni) {
            appendLog('warn', 'Remove Scripts: no device selected')
            return
        }
        def device = getChildDevice(selectedDni)
        if (!device) {
            appendLog('warn', "Remove Scripts: device not found for DNI ${selectedDni}")
            return
        }
        String ip = device.getDataValue('ipAddress')
        if (!ip) {
            appendLog('warn', "Remove Scripts: no IP address for device ${device.displayName}")
            return
        }
        removeNonRequiredScripts(ip)
    }

    if (buttonName == 'btnEnableStartScripts') {
        String selectedDni = settings?.selectedConfigDevice as String
        if (!selectedDni) {
            appendLog('warn', 'Enable Scripts: no device selected')
            return
        }
        def device = getChildDevice(selectedDni)
        if (!device) {
            appendLog('warn', "Enable Scripts: device not found for DNI ${selectedDni}")
            return
        }
        String ip = device.getDataValue('ipAddress')
        if (!ip) {
            appendLog('warn', "Enable Scripts: no IP address for device ${device.displayName}")
            return
        }
        enableAndStartRequiredScripts(ip)
    }

    if (buttonName == 'btnInstallActions') {
        String selectedDni = settings?.selectedConfigDevice as String
        if (!selectedDni) {
            appendLog('warn', 'Install Actions: no device selected')
            return
        }
        def device = getChildDevice(selectedDni)
        if (!device) {
            appendLog('warn', "Install Actions: device not found for DNI ${selectedDni}")
            return
        }
        String ip = device.getDataValue('ipAddress')
        if (!ip) {
            appendLog('warn', "Install Actions: no IP address for device ${device.displayName}")
            return
        }
        installRequiredActions(ip)
    }
}

/**
 * Creates a Hubitat device for a discovered Shelly device.
 * Retrieves device information, determines the appropriate driver,
 * and creates a child device with the generated driver code.
 *
 * @param ipKey The IP address key of the device to create
 */
private void createShellyDevice(String ipKey) {
    logInfo("Creating device for ${ipKey}")

    // Get device info from state
    Map deviceInfo = state.discoveredShellys[ipKey]
    if (!deviceInfo) {
        logError("No device info found for ${ipKey}")
        appendLog('error', "Failed to create device: no info for ${ipKey}")
        return
    }

    // Get device status — auto-fetch if not already available
    Map deviceStatus = deviceInfo.deviceStatus
    if (!deviceStatus || !deviceInfo.generatedDriverName) {
        logInfo("Device info/driver not yet available for ${ipKey} — fetching now...")
        appendLog('info', "Fetching device info for ${ipKey}...")
        fetchAndStoreDeviceInfo(ipKey)

        // Re-read state after fetch
        deviceInfo = state.discoveredShellys[ipKey]
        deviceStatus = deviceInfo?.deviceStatus
        if (!deviceStatus) {
            logError("Could not retrieve device status for ${ipKey}. Device may be offline.")
            appendLog('error', "Failed to create device for ${ipKey}: device may be offline or not Gen2+")
            return
        }
    }

    // Get the generated driver name
    String driverName = deviceInfo.generatedDriverName
    if (!driverName) {
        // Check if async driver generation is in progress (GitHub fetch)
        Map genContext = atomicState.currentDriverGeneration
        if (genContext?.ipKey == ipKey) {
            logWarn("Driver generation in progress for ${ipKey} — please wait and try again.")
            appendLog('warn', "Driver for ${ipKey} is being generated. Please wait a moment and try again.")
        } else {
            logError("No driver could be generated for ${ipKey}. Device may not have supported components.")
            appendLog('error', "Failed to create device for ${ipKey}: no driver generated")
        }
        return
    }

    // Create device network ID (DNI) - use MAC address if available, otherwise IP
    String dni = deviceInfo.mac ?: "shelly-${ipKey.replaceAll('\\.', '-')}"

    // Create device label (user-friendly name)
    String deviceLabel = deviceInfo.name ?: "Shelly ${ipKey}"

    // Check if device already exists
    def existingDevice = getChildDevice(dni)
    if (existingDevice) {
        logWarn("Device already exists: ${existingDevice.displayName} (${dni})")
        appendLog('warn', "Device already exists: ${existingDevice.displayName}")
        return
    }

    // Prepare device properties
    Map deviceProps = [
        name: deviceLabel,
        label: deviceLabel,
        data: [
            ipAddress: ipKey,
            shellyModel: deviceInfo.model ?: 'Unknown',
            shellyId: deviceInfo.id ?: dni,
            shellyMac: deviceInfo.mac ?: ''
        ]
    ]

    logInfo("=== Device Creation Details ===")
    logInfo("  DNI: ${dni}")
    logInfo("  Driver: ${driverName}")
    logInfo("  Label: ${deviceLabel}")
    logInfo("  IP: ${ipKey}")
    logInfo("  Model: ${deviceInfo.model}")
    logInfo("  MAC: ${deviceInfo.mac}")
    logInfo("  Properties: ${deviceProps}")

    try {
        def childDevice = addChildDevice('ShellyUSA', driverName, dni, deviceProps)

        logInfo("Created device: ${deviceLabel} using driver ${driverName}")
        appendLog('info', "Created: ${deviceLabel} (${driverName})")

        // Track this device against its driver
        associateDeviceWithDriver(driverName, 'ShellyUSA', dni)

        // Set device attributes
        childDevice.updateSetting('ipAddress', ipKey)
        childDevice.initialize()

        logInfo("✓ Device initialized successfully")

    } catch (Exception e) {
        logError("Failed to create device: ${e.message}")
        appendLog('error', "Failed to create ${deviceLabel}: ${e.message}")
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
    // Unregister mDNS listeners — try no-arg form first (some firmware versions),
    // then per-service-type form as fallback
    try {
        unregisterMDNSListener()
    } catch (Exception ignored) {
        try {
            unregisterMDNSListener('_shelly._tcp')
            unregisterMDNSListener('_http._tcp')
        } catch (Exception e2) {
            logTrace("Could not unregister mDNS listeners: ${e2.message}")
        }
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
    // Build options from discoveredShellys, excluding already-created devices
    LinkedHashMap<String,String> options = [:] as LinkedHashMap
    List<String> alreadyCreatedLabels = []

    // Pre-build a set of IPs that already have child devices (for robust matching)
    def childDevices = getChildDevices() ?: []
    Set<String> childDeviceIps = childDevices.collect { it.getDataValue('ipAddress') }.findAll { it }.toSet()
    Set<String> childDeviceDnis = childDevices.collect { it.deviceNetworkId }.toSet()

    state.discoveredShellys.each { ip, info ->
        String mac = info.mac ? " [${info.mac}]" : ""
        String label = "${info?.name ?: 'Shelly'} (${info?.ipAddress ?: ip})${mac}"
        // Check if device already exists by DNI (MAC or IP-based) or by IP data value
        String dni = info.mac ?: "shelly-${ip.toString().replaceAll('\\.', '-')}"
        String ipStr = ip.toString()
        Boolean alreadyCreated = childDeviceDnis.contains(dni) || childDeviceIps.contains(ipStr)
        if (alreadyCreated) {
            alreadyCreatedLabels.add(label)
        } else {
            options["${ip}"] = label
        }
    }

    // Normalize selection and filter out already-created devices
    List<String> selected = settings?.selectedToCreate ?: []
    if (!(selected instanceof List) && selected) { selected = [selected] }
    selected = selected.findAll { options.containsKey(it) }
    Integer selectedCount = selected.size()

    logDebug("createDevicesPage: options.size=${options.size()}, selected=${selected}, alreadyCreated=${alreadyCreatedLabels.size()}")

    // Build remove options from existing child devices (reuse childDevices from above)
    LinkedHashMap<String,String> removeOptions = [:] as LinkedHashMap
    childDevices.sort { it.displayName }.each { device ->
        String mac = device.getDataValue('shellyMac') ? " [${device.getDataValue('shellyMac')}]" : ""
        removeOptions[device.deviceNetworkId] = "${device.displayName} (${device.getDataValue('ipAddress') ?: 'n/a'})${mac}"
    }

    dynamicPage(name: "createDevicesPage", title: "Manage Shelly Devices", install: false, uninstall: false) {
        section() {
            if (!options || options.size() == 0) {
                if (alreadyCreatedLabels.size() > 0) {
                    paragraph "All discovered devices have already been created."
                } else {
                    paragraph "No devices available to create. Run discovery first."
                }
            } else {
                input name: 'selectedToCreate', type: 'enum', title: 'Select devices to create', options: options, multiple: true, required: false, submitOnChange: true
                input 'btnCreateDevices', 'button', title: 'Create Devices', submitOnChange: true
                input 'btnGetDeviceInfo', 'button', title: 'Get Device Info', submitOnChange: true
            }
        }

        section() {
            if (!removeOptions || removeOptions.size() == 0) {
                paragraph "No devices to remove."
            } else {
                input name: 'selectedToRemove', type: 'enum', title: 'Select devices to remove', options: removeOptions, multiple: true, required: false, submitOnChange: true
                input 'btnRemoveDevices', 'button', title: 'Remove Devices', submitOnChange: true
            }
        }

        section {
            href "mainPage", title: "Back to main page", description: ""
        }
    }
}

/**
 * Renders the device configuration page for managing scripts on installed Shelly devices.
 * Shows installed scripts, required scripts based on the device's capabilities, and
 * highlights any missing scripts that need to be installed.
 *
 * @return Map containing the dynamic page definition
 */
Map deviceConfigPage() {
    // Build single-select options from installed child devices
    LinkedHashMap<String,String> deviceOptions = [:] as LinkedHashMap
    def childDevices = getChildDevices() ?: []
    childDevices.sort { it.displayName }.each { device ->
        String ip = device.getDataValue('ipAddress') ?: 'n/a'
        deviceOptions[device.deviceNetworkId] = "${device.displayName} (${ip})"
    }

    dynamicPage(name: "deviceConfigPage", title: "Device Configuration", install: false, uninstall: false) {
        section() {
            if (!deviceOptions || deviceOptions.size() == 0) {
                paragraph "No installed devices found. Create devices first."
            } else {
                input name: 'selectedConfigDevice', type: 'enum', title: 'Select a device to configure',
                    options: deviceOptions, multiple: false, required: false, submitOnChange: true
            }
        }

        String selectedDni = settings?.selectedConfigDevice as String
        if (selectedDni) {
            def selectedDevice = getChildDevice(selectedDni)
            if (selectedDevice) {
                String ip = selectedDevice.getDataValue('ipAddress')
                if (ip) {
                    // Fetch installed scripts from device
                    List<Map> installedScripts = listDeviceScripts(ip)
                    List<String> installedScriptNames = []
                    if (installedScripts != null) {
                        installedScriptNames = installedScripts.collect { (it.name ?: '') as String }
                    }

                    // Determine required scripts from component_driver.json
                    Set<String> requiredScriptNames = getRequiredScriptsForDevice(selectedDevice)

                    // Compute missing scripts (required but not installed)
                    Set<String> requiredNames = requiredScriptNames.collect { stripJsExtension(it) } as Set<String>
                    List<String> missingScripts = requiredScriptNames.findAll { String req ->
                        !installedScriptNames.any { it == stripJsExtension(req) }
                    } as List<String>

                    // Compute removable scripts (installed, managed by us, but not required)
                    List<String> removableScripts = installedScriptNames.findAll { String name ->
                        MANAGED_SCRIPT_NAMES.contains(name) && !requiredNames.contains(name)
                    } as List<String>

                    section("Installed Scripts") {
                        if (installedScripts == null) {
                            paragraph "Unable to retrieve scripts from device."
                        } else if (installedScripts.size() == 0) {
                            paragraph "No scripts installed on this device."
                        } else {
                            StringBuilder sb = new StringBuilder()
                            installedScripts.each { Map script ->
                                String name = script.name ?: 'unnamed'
                                Boolean enabled = script.enable as Boolean
                                Boolean running = script.running as Boolean
                                Boolean isRequired = requiredNames.contains(name)
                                Boolean isManaged = MANAGED_SCRIPT_NAMES.contains(name)
                                String status = "${enabled ? 'enabled' : 'disabled'}, ${running ? 'running' : 'stopped'}"
                                String tag = isRequired ? '' : (isManaged ? ' (not required)' : '')
                                sb.append("${name} — ${status}${tag}\n")
                            }
                            paragraph "<pre style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>${sb.toString().trim()}</pre>"

                            if (removableScripts.size() > 0) {
                                paragraph "<b>${removableScripts.size()} removable script(s):</b> ${removableScripts.join(', ')}"
                                input 'btnRemoveNonRequiredScripts', 'button', title: 'Remove Non-Required Script(s)', submitOnChange: true
                            }
                        }
                    }

                    // Compute required scripts that are installed but not fully active
                    List<String> inactiveScripts = []
                    if (installedScripts != null) {
                        requiredNames.each { String reqName ->
                            Map script = installedScripts.find { (it.name ?: '') == reqName }
                            if (script && (!(script.enable as Boolean) || !(script.running as Boolean))) {
                                inactiveScripts.add(reqName)
                            }
                        }
                    }

                    section("Required Scripts") {
                        if (requiredScriptNames.size() == 0) {
                            paragraph "No scripts required for this device's capabilities."
                        } else {
                            StringBuilder sb = new StringBuilder()
                            requiredScriptNames.each { String scriptFile ->
                                String scriptName = stripJsExtension(scriptFile)
                                Map script = installedScripts?.find { (it.name ?: '') == scriptName }
                                if (!script) {
                                    sb.append("${scriptFile} — MISSING\n")
                                } else {
                                    Boolean enabled = script.enable as Boolean
                                    Boolean running = script.running as Boolean
                                    if (enabled && running) {
                                        sb.append("${scriptFile} — installed, enabled, running\n")
                                    } else {
                                        List<String> issues = []
                                        if (!enabled) { issues.add('not enabled') }
                                        if (!running) { issues.add('not running') }
                                        sb.append("${scriptFile} — installed, ${issues.join(', ')}\n")
                                    }
                                }
                            }
                            paragraph "<pre style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>${sb.toString().trim()}</pre>"

                            if (missingScripts.size() > 0) {
                                paragraph "<b>${missingScripts.size()} missing script(s):</b> ${missingScripts.join(', ')}"
                                input 'btnInstallScripts', 'button', title: 'Install Missing Script(s)', submitOnChange: true
                            }
                            if (inactiveScripts.size() > 0) {
                                paragraph "<b>${inactiveScripts.size()} inactive script(s):</b> ${inactiveScripts.join(', ')}"
                                input 'btnEnableStartScripts', 'button', title: 'Enable & Start Script(s)', submitOnChange: true
                            }
                            if (missingScripts.size() == 0 && inactiveScripts.size() == 0) {
                                paragraph "<b>All required scripts are installed and running.</b>"
                            }
                        }
                    }

                    // Actions (webhooks) section
                    List<Map> requiredActions = getRequiredActionsForDevice(selectedDevice)
                    List<Map> installedHooks = listDeviceWebhooks(ip)
                    String hubIp = location.hub.localIP

                    if (requiredActions.size() > 0) {
                        // Determine which actions are missing or misconfigured
                        List<Map> missingActions = []
                        List<Map> okActions = []
                        requiredActions.each { Map action ->
                            Map hook = installedHooks?.find { Map h ->
                                h.event == action.event && (h.cid as Integer) == (action.cid as Integer)
                            }
                            if (hook) {
                                List<String> urls = hook.urls as List<String>
                                Boolean enabled = hook.enable as Boolean
                                if (urls?.any { it?.contains(hubIp) } && enabled) {
                                    okActions.add(action)
                                } else {
                                    missingActions.add(action)
                                }
                            } else {
                                missingActions.add(action)
                            }
                        }

                        section("Required Actions (Webhooks)") {
                            StringBuilder sb = new StringBuilder()
                            requiredActions.each { Map action ->
                                Boolean isOk = okActions.contains(action)
                                String status = isOk ? 'configured' : 'MISSING'
                                sb.append("${action.name} (${action.event} cid:${action.cid}) — ${status}\n")
                            }
                            paragraph "<pre style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>${sb.toString().trim()}</pre>"

                            if (missingActions.size() > 0) {
                                paragraph "<b>${missingActions.size()} action(s) need to be configured.</b>"
                                input 'btnInstallActions', 'button', title: 'Install Missing Action(s)', submitOnChange: true
                            } else {
                                paragraph "<b>All required actions are configured.</b>"
                            }
                        }
                    }
                } else {
                    section() {
                        paragraph "Selected device has no IP address configured."
                    }
                }
            }
        }

        section {
            href "mainPage", title: "Back to main page", description: ""
        }
    }
}

/**
 * Retrieves the list of scripts installed on a Shelly device.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return List of script maps containing name, enable, running status, or null on failure
 */
List<Map> listDeviceScripts(String ipAddress) {
    try {
        String uri = "http://${ipAddress}/rpc"
        LinkedHashMap command = scriptListCommand()
        if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
        LinkedHashMap json = postCommandSync(command, uri)
        if (json?.result?.scripts) {
            return json.result.scripts as List<Map>
        }
        return []
    } catch (Exception ex) {
        logError("Failed to list scripts for ${ipAddress}: ${ex.message}")
        return null
    }
}

/**
 * Retrieves the list of webhooks installed on a Shelly device.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return List of webhook maps containing id, cid, event, name, enable, urls, or null on failure
 */
List<Map> listDeviceWebhooks(String ipAddress) {
    try {
        String uri = "http://${ipAddress}/rpc"
        LinkedHashMap command = webhookListCommand()
        if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
        LinkedHashMap json = postCommandSync(command, uri)
        if (json?.result?.hooks) {
            return json.result.hooks as List<Map>
        }
        return []
    } catch (Exception ex) {
        logError("Failed to list webhooks for ${ipAddress}: ${ex.message}")
        return null
    }
}

/**
 * Determines which webhook actions are required for a device by querying its
 * Shelly.GetStatus response and cross-referencing with component_driver.json.
 * Returns a list of action maps (event, name, dst, cid) for capabilities that
 * define requiredActions.
 *
 * @param device The child device to check
 * @return List of required action maps, each with keys: event, name, dst, cid
 */
List<Map> getRequiredActionsForDevice(def device) {
    List<Map> requiredActions = []

    String ip = device.getDataValue('ipAddress')
    if (!ip) {
        logDebug("getRequiredActionsForDevice: no IP for ${device.displayName}")
        return requiredActions
    }

    Map deviceStatus = queryDeviceStatus(ip)
    if (!deviceStatus) {
        logDebug("getRequiredActionsForDevice: could not query status for ${device.displayName}")
        return requiredActions
    }

    List<Map> capabilities = fetchCapabilityDefinitions()
    if (!capabilities) { return requiredActions }

    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        String baseType = key.contains(':') ? key.split(':')[0] : key
        Integer cid = 0
        if (key.contains(':')) {
            try { cid = key.split(':')[1] as Integer } catch (Exception ignored) {}
        }

        Map capability = capabilities.find { cap -> cap.shellyComponent == baseType }
        if (capability?.requiredActions) {
            (capability.requiredActions as List<Map>).each { Map action ->
                requiredActions.add([
                    event: action.event,
                    name : action.name,
                    dst  : action.dst,
                    cid  : cid
                ])
            }
        }
    }

    logDebug("Required actions for ${device.displayName}: ${requiredActions}")
    return requiredActions
}

/**
 * Installs required webhook actions on a Shelly device. Creates webhooks
 * for each required action that isn't already configured, pointing to
 * the Hubitat hub on port 39501.
 *
 * @param ipAddress The IP address of the Shelly device
 */
void installRequiredActions(String ipAddress) {
    String selectedDni = settings?.selectedConfigDevice as String
    def device = selectedDni ? getChildDevice(selectedDni) : null
    if (!device) {
        logError("installRequiredActions: no device found")
        return
    }

    List<Map> requiredActions = getRequiredActionsForDevice(device)
    if (!requiredActions) {
        logInfo("No actions required for this device")
        return
    }

    List<Map> existingHooks = listDeviceWebhooks(ipAddress)
    if (existingHooks == null) {
        logError("Could not retrieve existing webhooks from ${ipAddress}")
        return
    }

    String hubIp = location.hub.localIP
    String hookUrl = "http://${hubIp}:39501"
    String uri = "http://${ipAddress}/rpc"
    Integer installed = 0

    requiredActions.each { Map action ->
        String event = action.event as String
        String name = action.name as String
        Integer cid = action.cid as Integer

        // Check if a webhook for this event+cid already exists
        Map existing = existingHooks.find { Map h ->
            h.event == event && (h.cid as Integer) == cid
        }

        if (existing) {
            // Verify it points to the right URL and is enabled
            List<String> urls = existing.urls as List<String>
            Boolean enabled = existing.enable as Boolean
            if (urls?.any { it?.contains(hubIp) } && enabled) {
                logDebug("Webhook '${name}' already configured for ${event} cid=${cid}")
                return
            }
            // Update existing webhook with correct URL
            logInfo("Updating webhook '${name}' for ${event} cid=${cid}")
            LinkedHashMap updateCmd = webhookUpdateCommand(existing.id as Integer, [hookUrl])
            if (authIsEnabled() == true && getAuth().size() > 0) { updateCmd.auth = getAuth() }
            postCommandSync(updateCmd, uri)
            installed++
            return
        }

        // Create new webhook
        logInfo("Creating webhook '${name}' for ${event} cid=${cid} -> ${hookUrl}")
        appendLog('info', "Creating webhook ${name} on ${device.displayName}")
        LinkedHashMap createCmd = webhookCreateCommand(cid, event, name, [hookUrl])
        if (authIsEnabled() == true && getAuth().size() > 0) { createCmd.auth = getAuth() }
        LinkedHashMap result = postCommandSync(createCmd, uri)

        if (result?.result?.id != null) {
            logInfo("Webhook '${name}' created (id: ${result.result.id})")
            installed++
        } else {
            logError("Failed to create webhook '${name}': ${result}")
        }
    }

    logInfo("Action provisioning complete: ${installed} webhook(s) installed/updated on ${ipAddress}")
    appendLog('info', "Action provisioning: ${installed} webhook(s) on ${device.displayName}")
}

/**
 * Determines which scripts are required for a device by querying its actual
 * Shelly.GetStatus response and cross-referencing with component_driver.json
 * from GitHub. Matches component types via the shellyComponent field and detects
 * power monitoring fields to include PM-related capability scripts.
 *
 * @param device The child device to check
 * @return Set of required script filenames (e.g., ["switchstatus.js", "powermonitoring.js"])
 */
Set<String> getRequiredScriptsForDevice(def device) {
    Set<String> requiredScripts = [] as Set

    String ip = device.getDataValue('ipAddress')
    if (!ip) {
        logDebug("getRequiredScriptsForDevice: no IP for ${device.displayName}")
        return requiredScripts
    }

    // Query the device's actual status to discover its components
    Map deviceStatus = queryDeviceStatus(ip)
    if (!deviceStatus) {
        logDebug("getRequiredScriptsForDevice: could not query status for ${device.displayName}")
        return requiredScripts
    }

    // Fetch and parse component_driver.json from GitHub
    List<Map> capabilities = fetchCapabilityDefinitions()
    if (!capabilities) { return requiredScripts }

    // Walk status keys to find components and detect power monitoring
    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        String baseType = key.contains(':') ? key.split(':')[0] : key

        // Find matching capability by shellyComponent field
        Map capability = capabilities.find { cap -> cap.shellyComponent == baseType }
        if (capability?.requiredScripts) {
            requiredScripts.addAll(capability.requiredScripts as List<String>)
        }

        // Check for power monitoring fields on this component
        if (v instanceof Map) {
            Map statusMap = v as Map
            if (statusMap.voltage != null || statusMap.current != null ||
                    statusMap.apower != null || statusMap.aenergy != null) {
                ['PowerMeter', 'EnergyMeter', 'CurrentMeter', 'VoltageMeasurement'].each { String capId ->
                    Map pmCap = capabilities.find { cap -> cap.id == capId }
                    if (pmCap?.requiredScripts) {
                        requiredScripts.addAll(pmCap.requiredScripts as List<String>)
                    }
                }
            }
        }
    }

    logDebug("Required scripts for ${device.displayName}: ${requiredScripts}")
    return requiredScripts
}

/**
 * Queries a Shelly device's full status via Shelly.GetStatus RPC.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return Map of status keys and values, or null on failure
 */
private Map queryDeviceStatus(String ipAddress) {
    try {
        String uri = "http://${ipAddress}/rpc"
        LinkedHashMap command = shellyGetStatusCommand('deviceConfig')
        if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
        LinkedHashMap json = postCommandSync(command, uri)
        return json?.result as Map
    } catch (Exception ex) {
        logError("Failed to query device status for ${ipAddress}: ${ex.message}")
        return null
    }
}

/**
 * Fetches and parses the component_driver.json capability definitions from GitHub.
 *
 * @return List of capability maps, or null on failure
 */
private List<Map> fetchCapabilityDefinitions() {
    String branch = GITHUB_BRANCH
    String baseUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/UniversalDrivers"
    String componentJsonUrl = "${baseUrl}/component_driver.json"

    String jsonContent = downloadFile(componentJsonUrl)
    if (!jsonContent) {
        logError("Failed to fetch component_driver.json from GitHub")
        return null
    }

    try {
        Map componentData = slurper.parseText(jsonContent) as Map
        return componentData?.capabilities as List<Map>
    } catch (Exception e) {
        logError("Failed to parse component_driver.json: ${e.message}")
        return null
    }
}

/**
 * Strips the .js extension from a script filename to get the script name
 * as it appears on the Shelly device.
 *
 * @param filename The script filename (e.g., "switchstatus.js")
 * @return The script name without extension (e.g., "switchstatus")
 */
@CompileStatic
private String stripJsExtension(String filename) {
    if (filename?.endsWith('.js')) {
        return filename.substring(0, filename.length() - 3)
    }
    return filename
}

/**
 * Installs missing required scripts on a Shelly device.
 * Downloads each missing script from GitHub, creates it on the device,
 * uploads the code, enables it, and starts it.
 *
 * @param ipAddress The IP address of the Shelly device
 */
void installRequiredScripts(String ipAddress) {
    String selectedDni = settings?.selectedConfigDevice as String
    if (!selectedDni) { return }
    def device = getChildDevice(selectedDni)
    if (!device) { return }

    Set<String> requiredScripts = getRequiredScriptsForDevice(device)
    if (requiredScripts.size() == 0) {
        logInfo("No required scripts for device at ${ipAddress}")
        appendLog('info', "No required scripts for ${device.displayName}")
        return
    }

    // Get currently installed scripts
    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) {
        logError("Cannot read scripts from device at ${ipAddress}")
        appendLog('error', "Cannot read scripts from ${device.displayName}")
        return
    }
    List<String> installedNames = installedScripts.collect { (it.name ?: '') as String }

    String branch = GITHUB_BRANCH
    String baseUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/Scripts"
    String uri = "http://${ipAddress}/rpc"

    Integer installed = 0
    requiredScripts.each { String scriptFile ->
        String scriptName = stripJsExtension(scriptFile)

        // Skip if already installed
        if (installedNames.any { it == scriptName }) {
            logDebug("Script '${scriptName}' already installed on ${ipAddress}")
            return
        }

        logInfo("Installing script '${scriptName}' on ${ipAddress}...")
        appendLog('info', "Installing ${scriptName} on ${device.displayName}...")

        // Download script source from GitHub
        String scriptCode = downloadFile("${baseUrl}/${scriptFile}")
        if (!scriptCode) {
            logError("Failed to download ${scriptFile} from GitHub")
            appendLog('error', "Failed to download ${scriptFile}")
            return
        }

        try {
            // Create script on device
            LinkedHashMap createCmd = scriptCreateCommand(scriptName)
            if (authIsEnabled() == true && getAuth().size() > 0) { createCmd.auth = getAuth() }
            LinkedHashMap createResult = postCommandSync(createCmd, uri)
            Integer scriptId = createResult?.result?.id as Integer

            if (scriptId == null) {
                logError("Failed to create script '${scriptName}' on device")
                appendLog('error', "Failed to create ${scriptName}")
                return
            }

            // Upload code
            LinkedHashMap putCmd = scriptPutCodeCommand(scriptId, scriptCode, false)
            if (authIsEnabled() == true && getAuth().size() > 0) { putCmd.auth = getAuth() }
            postCommandSync(putCmd, uri)

            // Enable script
            LinkedHashMap enableCmd = scriptEnableCommand(scriptId)
            if (authIsEnabled() == true && getAuth().size() > 0) { enableCmd.auth = getAuth() }
            postCommandSync(enableCmd, uri)

            // Start script
            LinkedHashMap startCmd = scriptStartCommand(scriptId)
            if (authIsEnabled() == true && getAuth().size() > 0) { startCmd.auth = getAuth() }
            postCommandSync(startCmd, uri)

            logInfo("Successfully installed and started '${scriptName}' (id: ${scriptId})")
            appendLog('info', "Installed ${scriptName} on ${device.displayName}")
            installed++
        } catch (Exception ex) {
            logError("Failed to install script '${scriptName}': ${ex.message}")
            appendLog('error', "Failed to install ${scriptName}: ${ex.message}")
        }
    }

    logInfo("Script installation complete: ${installed} script(s) installed on ${ipAddress}")
    appendLog('info', "Script installation complete: ${installed} installed on ${device.displayName}")
}

/**
 * Removes managed scripts that are not required for a device's capabilities.
 * Only removes scripts whose names appear in MANAGED_SCRIPT_NAMES to avoid
 * deleting user-created or third-party scripts. Stops each script before deletion.
 *
 * @param ipAddress The IP address of the Shelly device
 */
void removeNonRequiredScripts(String ipAddress) {
    String selectedDni = settings?.selectedConfigDevice as String
    if (!selectedDni) { return }
    def device = getChildDevice(selectedDni)
    if (!device) { return }

    Set<String> requiredScripts = getRequiredScriptsForDevice(device)
    Set<String> requiredNames = requiredScripts.collect { stripJsExtension(it) } as Set<String>

    // Get currently installed scripts (need full details including id)
    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) {
        logError("Cannot read scripts from device at ${ipAddress}")
        appendLog('error', "Cannot read scripts from ${device.displayName}")
        return
    }

    String uri = "http://${ipAddress}/rpc"
    Integer removed = 0

    installedScripts.each { Map script ->
        String name = script.name as String
        Integer scriptId = script.id as Integer

        // Only remove scripts we manage, and only if not required
        if (!MANAGED_SCRIPT_NAMES.contains(name)) { return }
        if (requiredNames.contains(name)) { return }
        if (scriptId == null) { return }

        logInfo("Removing non-required script '${name}' (id: ${scriptId}) from ${ipAddress}...")
        appendLog('info', "Removing ${name} from ${device.displayName}...")

        try {
            // Stop the script first if running
            if (script.running) {
                LinkedHashMap stopCmd = scriptStopCommand(scriptId)
                if (authIsEnabled() == true && getAuth().size() > 0) { stopCmd.auth = getAuth() }
                postCommandSync(stopCmd, uri)
            }

            // Delete the script
            LinkedHashMap deleteCmd = scriptDeleteCommand(scriptId)
            if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
            postCommandSync(deleteCmd, uri)

            logInfo("Removed script '${name}' (id: ${scriptId})")
            appendLog('info', "Removed ${name} from ${device.displayName}")
            removed++
        } catch (Exception ex) {
            logError("Failed to remove script '${name}': ${ex.message}")
            appendLog('error', "Failed to remove ${name}: ${ex.message}")
        }
    }

    logInfo("Script removal complete: ${removed} script(s) removed from ${ipAddress}")
    appendLog('info', "Script removal complete: ${removed} removed from ${device.displayName}")
}

/**
 * Enables and starts all required scripts on a Shelly device that are installed
 * but not currently enabled or running. Uses Script.SetConfig to enable (which
 * also sets the script to start on boot) and Script.Start to run it.
 *
 * @param ipAddress The IP address of the Shelly device
 */
void enableAndStartRequiredScripts(String ipAddress) {
    String selectedDni = settings?.selectedConfigDevice as String
    if (!selectedDni) { return }
    def device = getChildDevice(selectedDni)
    if (!device) { return }

    Set<String> requiredScripts = getRequiredScriptsForDevice(device)
    Set<String> requiredNames = requiredScripts.collect { stripJsExtension(it) } as Set<String>

    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) {
        logError("Cannot read scripts from device at ${ipAddress}")
        appendLog('error', "Cannot read scripts from ${device.displayName}")
        return
    }

    String uri = "http://${ipAddress}/rpc"
    Integer fixed = 0

    installedScripts.each { Map script ->
        String name = script.name as String
        Integer scriptId = script.id as Integer
        Boolean enabled = script.enable as Boolean
        Boolean running = script.running as Boolean

        if (!requiredNames.contains(name)) { return }
        if (scriptId == null) { return }
        if (enabled && running) { return }

        logInfo("Enabling and starting script '${name}' (id: ${scriptId}) on ${ipAddress}...")
        appendLog('info', "Enabling ${name} on ${device.displayName}...")

        try {
            if (!enabled) {
                LinkedHashMap enableCmd = scriptEnableCommand(scriptId)
                if (authIsEnabled() == true && getAuth().size() > 0) { enableCmd.auth = getAuth() }
                postCommandSync(enableCmd, uri)
            }

            if (!running) {
                LinkedHashMap startCmd = scriptStartCommand(scriptId)
                if (authIsEnabled() == true && getAuth().size() > 0) { startCmd.auth = getAuth() }
                postCommandSync(startCmd, uri)
            }

            logInfo("Script '${name}' is now enabled and running")
            appendLog('info', "Enabled and started ${name} on ${device.displayName}")
            fixed++
        } catch (Exception ex) {
            logError("Failed to enable/start script '${name}': ${ex.message}")
            appendLog('error', "Failed to enable ${name}: ${ex.message}")
        }
    }

    logInfo("Enable/start complete: ${fixed} script(s) fixed on ${ipAddress}")
    appendLog('info', "Enable/start complete: ${fixed} fixed on ${device.displayName}")
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

    // Check for interrupted rebuild (hub may have restarted mid-rebuild)
    if (state.driverRebuildInProgress) {
        logWarn("Detected interrupted driver rebuild (hub may have restarted)")
        appendLog('warn', "Resuming interrupted driver rebuild...")

        // Re-add the current driver to the front of the queue if it was in progress
        String currentKey = state.driverRebuildCurrentKey
        List<String> queue = state.driverRebuildQueue ?: []
        if (currentKey && !queue.contains(currentKey)) {
            queue.add(0, currentKey)
            state.driverRebuildQueue = queue
        }

        // Reset flag and resume after hub has fully initialized
        state.driverRebuildInProgress = false
        runIn(30, 'rebuildAllTrackedDrivers')
        return
    }

    // Check for app version change and trigger driver regeneration
    String currentVersion = getAppVersion()
    String lastVersion = state.lastAutoconfVersion

    if (lastVersion == null) {
        // First install: store version, no rebuild needed
        state.lastAutoconfVersion = currentVersion
        logInfo("First install detected, storing app version: ${currentVersion}")
    } else if (lastVersion != currentVersion) {
        state.lastAutoconfVersion = currentVersion
        if (settings?.rebuildOnUpdate != false) {
            logInfo("App version changed from ${lastVersion} to ${currentVersion}, rebuilding drivers and cleaning up unused")
            rebuildAllTrackedDrivers()
        } else {
            logInfo("App version changed from ${lastVersion} to ${currentVersion} (driver rebuild disabled)")
        }
    } else {
        logDebug("App version unchanged (${currentVersion}), no driver regeneration needed")
    }

    // Schedule periodic watchdog to detect IP address changes via mDNS
    if (settings?.enableWatchdog != false) {
        schedule('0 */15 * ? * *', 'watchdogScan')
    }

    // Schedule daily auto-update check at 3AM
    if (settings?.enableAutoUpdate != false) {
        schedule('0 0 3 ? * *', 'checkForAppUpdate')
        logDebug("App auto-update scheduled for 3AM daily")
    } else {
        unschedule('checkForAppUpdate')
    }

    // Aggressive auto-update: check every minute for branch changes
    if (settings?.enableAggressiveUpdate == true) {
        schedule('0 * * ? * *', 'aggressiveUpdateCheck')
        logInfo("Aggressive auto-update enabled (every 60s from branch)")
    } else {
        unschedule('aggressiveUpdateCheck')
    }
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

                // Queue newly discovered devices for automatic driver check
                if (isNewToState) {
                    List<String> queue = state.discoveryDriverQueue ?: []
                    if (!queue.contains(key)) {
                        queue.add(key)
                        state.discoveryDriverQueue = queue
                    }
                }
            }
            Integer afterCount = state.discoveredShellys.size()
            if (afterCount > beforeCount) {
                logDebug("Found ${afterCount - beforeCount} new Shelly device(s), total: ${afterCount}")
            }
            logDebug("Sending discovery events, total discovered: ${state.discoveredShellys.size()}")
            sendFoundShellyEvents()

            // Start processing discovery driver queue if not already running
            List<String> pendingQueue = state.discoveryDriverQueue ?: []
            if (pendingQueue.size() > 0 && !state.discoveryDriverInProgress && !state.driverRebuildInProgress) {
                runIn(2, 'processNextDiscoveryDriver')
            }
        }
    } catch (Exception e) {
        logWarn("Error processing mDNS entries: ${e.message}")
    }

    if (state.discoveryRunning && getRemainingDiscoverySeconds() > 0) {
        runIn(getMdnsPollSeconds(), 'processMdnsDiscovery')
    }
}

/**
 * Processes the next device in the discovery driver queue.
 * Fetches device info and triggers driver determination, which will either
 * install from cache, skip (driver already exists), or generate from GitHub.
 * Processes devices sequentially with delays to avoid hammering the network.
 */
void processNextDiscoveryDriver() {
    List<String> queue = state.discoveryDriverQueue ?: []
    if (queue.isEmpty()) {
        state.discoveryDriverInProgress = false
        logDebug("Discovery driver queue is empty, processing complete")
        return
    }

    // Don't run if a rebuild is in progress (shared driver generation pipeline)
    if (state.driverRebuildInProgress) {
        logDebug("Driver rebuild in progress, deferring discovery queue processing")
        runIn(30, 'processNextDiscoveryDriver')
        return
    }

    state.discoveryDriverInProgress = true
    String ipKey = queue.remove(0)
    state.discoveryDriverQueue = queue

    logInfo("Processing discovery driver queue: fetching info for ${ipKey} (${queue.size()} remaining)")

    // fetchAndStoreDeviceInfo queries the device and triggers determineDeviceDriver,
    // which will either skip (driver exists), install from cache, or generate (new driver needed)
    try {
        fetchAndStoreDeviceInfo(ipKey)
    } catch (Exception e) {
        logWarn("Discovery queue: error processing ${ipKey}: ${e.message}")
    }

    // Check if async driver generation was started for this device.
    // If so, completeDriverGeneration will schedule the next queue item when done.
    // If not (driver already existed, installed from cache, or error), schedule next here.
    Map genContext = atomicState.currentDriverGeneration
    Boolean asyncGenStarted = (genContext != null && genContext.ipKey == ipKey)

    if (asyncGenStarted) {
        logDebug("Discovery queue: async driver generation in progress for ${ipKey}, waiting for completion callback")
    } else {
        scheduleNextDiscoveryDriver()
    }
}

/**
 * Schedules the next device in the discovery driver queue for processing.
 * If the queue is empty, clears the in-progress flag.
 */
private void scheduleNextDiscoveryDriver() {
    List<String> remaining = state.discoveryDriverQueue ?: []
    if (!remaining.isEmpty()) {
        runIn(5, 'processNextDiscoveryDriver')
    } else {
        state.discoveryDriverInProgress = false
        logDebug("Discovery driver queue complete")
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

    return names.join('\n')
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
// IP Address Watchdog (mDNS-based IP change detection)
// ═══════════════════════════════════════════════════════════════

/**
 * Extracts the MAC address from an mDNS server name.
 * Shelly mDNS server names follow the pattern {@code ShellyModel-AABBCCDDEEFF}
 * where the last segment after the final hyphen is a 12-character hex MAC address.
 *
 * @param serverName The mDNS server name (e.g. {@code ShellyPlugUS-C049EF8B3A44})
 * @return The uppercase MAC address string, or null if the name does not contain a valid MAC
 */
@CompileStatic
static String extractMacFromMdnsName(String serverName) {
    if (!serverName) { return null }
    // Remove trailing .local. or trailing dot
    String cleaned = serverName.replaceAll(/\.local\.$/, '').replaceAll(/\.$/, '')
    Integer lastDash = cleaned.lastIndexOf('-')
    if (lastDash < 0 || lastDash >= cleaned.length() - 1) { return null }
    String candidate = cleaned.substring(lastDash + 1).toUpperCase()
    if (candidate.length() == 12 && candidate.matches(/^[0-9A-F]{12}$/)) {
        return candidate
    }
    return null
}

/**
 * Performs an mDNS-based watchdog scan to detect IP address changes for child devices.
 * Respects a 5-minute cooldown between scans to avoid excessive network traffic.
 * Re-registers mDNS listeners and schedules result processing after a 10-second delay.
 * <p>
 * Called periodically (every 15 minutes) and on command failure in
 * {@link #sendSwitchCommand(Object, Boolean)}.
 */
void watchdogScan() {
    Long lastScan = state.lastWatchdogScan ?: 0L
    if (now() - lastScan < 300000) {
        logDebug("watchdogScan: skipping, last scan was ${(now() - lastScan) / 1000} seconds ago (cooldown 300s)")
        return
    }
    state.lastWatchdogScan = now()
    logDebug('watchdogScan: starting mDNS scan for IP changes')

    // Re-register listeners to trigger fresh mDNS queries on the network
    startMdnsDiscovery()

    // Wait 10 seconds for mDNS responses to accumulate before processing
    runIn(10, 'watchdogProcessResults')
}

/**
 * Processes mDNS results collected after a watchdog scan.
 * Compares discovered IP addresses against stored child device IPs and updates
 * any that have changed. Also updates {@code state.discoveredShellys} entries.
 */
void watchdogProcessResults() {
    logDebug('watchdogProcessResults: checking for IP changes')

    try {
        List<Map<String, Object>> shellyEntries = getMDNSEntries('_shelly._tcp')
        List<Map<String, Object>> httpEntries = getMDNSEntries('_http._tcp')

        List allEntries = []
        if (shellyEntries) { allEntries.addAll(shellyEntries) }
        if (httpEntries) { allEntries.addAll(httpEntries) }

        if (!allEntries) {
            logDebug('watchdogProcessResults: no mDNS entries found')
            return
        }

        Integer updatedCount = 0
        allEntries.each { entry ->
            String server = (entry?.server ?: '') as String
            String ip4 = (entry?.ip4Addresses ?: '') as String
            if (!server || !ip4) { return }

            String mac = extractMacFromMdnsName(server)
            if (!mac) { return }

            // Check if we have a child device with this MAC as DNI
            def child = getChildDevice(mac)
            if (!child) { return }

            String currentIp = child.getDataValue('ipAddress')
            if (currentIp && currentIp != ip4) {
                logInfo("watchdogProcessResults: IP changed for ${child.displayName} (${mac}): ${currentIp} -> ${ip4}")
                child.updateDataValue('ipAddress', ip4)
                updatedCount++

                // Also update discoveredShellys if the old IP is a key
                if (state.discoveredShellys?.containsKey(currentIp)) {
                    Map deviceEntry = state.discoveredShellys.remove(currentIp) as Map
                    deviceEntry.ipAddress = ip4
                    deviceEntry.ts = now()
                    state.discoveredShellys[ip4] = deviceEntry
                }
            }
        }

        if (updatedCount > 0) {
            logInfo("watchdogProcessResults: updated ${updatedCount} device IP(s)")
        } else {
            logDebug('watchdogProcessResults: all device IPs are current')
        }
    } catch (Exception e) {
        logWarn("watchdogProcessResults: error processing mDNS entries: ${e.message}")
    }
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
        determineDeviceDriver(deviceStatus, ipKey)
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
private void determineDeviceDriver(Map deviceStatus, String ipKey = null) {
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
    Map<String, Boolean> componentPowerMonitoring = [:]

    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        if (key.startsWith('switch')) {
            components.add(k.toString())
            // Check if this switch has power monitoring
            Boolean hasPM = false
            if (v instanceof Map) {
                // Check for power monitoring fields: voltage, current, power, apower, energy
                hasPM = v.voltage != null || v.current != null || v.power != null ||
                        v.apower != null || v.aenergy != null
            }
            componentPowerMonitoring[k.toString()] = hasPM
            if (hasPM) {
                logInfo("Switch ${k} has power monitoring capabilities")
            }
        }
        if (key.contains('input')) {
            components.add(k.toString())
            componentPowerMonitoring[k.toString()] = false
        }
    }

    // Generate driver for discovered components
    if (components.size() > 0) {
        String driverName = generateDriverName(components, componentPowerMonitoring)
        String version = getAppVersion()
        String driverNameWithVersion = "${driverName} v${version}"

        // Check if this driver is already installed and up to date
        Map allDrivers = state.autoDrivers ?: [:]
        String trackingKey = "ShellyUSA.${driverNameWithVersion}"
        Map existingEntry = allDrivers[trackingKey] as Map
        if (existingEntry && existingEntry.version == version) {
            logInfo("Driver already exists and is up to date: ${driverNameWithVersion} — skipping generation")
            // Store the driver name on the discovered device entry
            if (ipKey && state.discoveredShellys[ipKey]) {
                state.discoveredShellys[ipKey].generatedDriverName = driverNameWithVersion
            }
            return
        }

        // Check file manager cache for a previously generated driver
        String cachedSource = loadDriverFromCache(driverName)
        if (cachedSource) {
            // Verify the cached version matches current app version
            def versionMatch = (cachedSource =~ /version:\s*['"]([^'"]+)['"]/)
            String cachedVersion = versionMatch.find() ? versionMatch.group(1) : null
            if (cachedVersion == version) {
                logInfo("Found cached driver source for ${driverName} (v${cachedVersion}) — installing from cache")
                installDriver(cachedSource)

                // Register in tracking and store on discovered device
                registerAutoDriver(driverNameWithVersion, 'ShellyUSA', version, components, componentPowerMonitoring)
                if (ipKey && state.discoveredShellys[ipKey]) {
                    state.discoveredShellys[ipKey].generatedDriverName = driverNameWithVersion
                }
                return
            } else {
                logDebug("Cached driver version (${cachedVersion}) does not match app version (${version}) — regenerating")
            }
        }

        // No match found — fall through to full GitHub-based generation
        logInfo("Generating driver from GitHub: ${driverNameWithVersion}")

        // Store context for async callback to use
        atomicState.currentDriverGeneration = [
            ipKey: ipKey,
            components: components,
            componentPowerMonitoring: componentPowerMonitoring
        ]

        String driverCode = generateHubitatDriver(components, componentPowerMonitoring)
        logDebug("Generated driver code (${driverCode?.length() ?: 0} chars)")
    }

    if (switchesFound == 0 && inputsFound == 0) {
        logDebug("determineDeviceDriver: no switches or inputs found in status, cannot determine device type")
        return
    }
    if (switchesFound == 1 && inputsFound == 0) {
        logDebug("Device is likely a plug or basic relay device (1 switch, no inputs)")
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
private String generateHubitatDriver(List<String> components, Map<String, Boolean> componentPowerMonitoring = [:]) {
    logDebug("generateHubitatDriver called with ${components?.size() ?: 0} components: ${components}")
    logDebug("Power monitoring components: ${componentPowerMonitoring.findAll { k, v -> v }}")

    // Branch to fetch files from (change to 'master' for production)
    String branch = GITHUB_BRANCH
    String baseUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/UniversalDrivers"

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
    String driverName = generateDriverName(components, componentPowerMonitoring)
    String version = getAppVersion()
    String driverNameWithVersion = "${driverName} v${version}"

    if (driverDef) {
        driver.append("name: '${driverNameWithVersion}', ")
        driver.append("namespace: '${driverDef.namespace}', ")
        driver.append("author: '${driverDef.author}', ")
        driver.append("singleThreaded: ${driverDef.singleThreaded}, ")
        driver.append("importUrl: '${driverDef.importUrl ?: ''}'")
    } else {
        // Fallback if definition not found
        driver.append("name: '${driverNameWithVersion}', ")
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

                // If this is a switch with power monitoring, add PM capabilities
                if (baseType == 'switch' && componentPowerMonitoring[component]) {
                    logDebug("Adding power monitoring capabilities for ${component}")

                    // Add power monitoring capabilities
                    ['CurrentMeter', 'PowerMeter', 'VoltageMeasurement', 'EnergyMeter'].each { pmCapId ->
                        if (!addedCapabilities.contains(pmCapId)) {
                            addedCapabilities.add(pmCapId)

                            Map pmCap = capabilities.find { cap -> cap.id == pmCapId }
                            if (pmCap) {
                                driver.append("    capability '${pmCapId}'\n")

                                // Add attributes comments
                                if (pmCap.attributes) {
                                    pmCap.attributes.each { attr ->
                                        String attrComment = "    //Attributes: ${attr.name} - ${attr.type.toUpperCase()}"
                                        driver.append("${attrComment}\n")
                                    }
                                }

                                // Add commands comments
                                if (pmCap.commands) {
                                    List<String> cmdSignatures = pmCap.commands.collect { cmd ->
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
                        }
                    }
                }
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

    // Initialize async operation tracking
    initializeAsyncDriverGeneration()

    // Collect all files to fetch
    Set<String> filesToFetch = new HashSet<>()
    filesToFetch.add("Lifecycle.groovy")

    // Add command files from matched capabilities
    components.each { component ->
        String baseType = component.contains(':') ? component.split(':')[0] : component
        Map capability = capabilities.find { cap -> cap.shellyComponent == baseType }
        if (capability && capability.commandFiles) {
            filesToFetch.addAll(capability.commandFiles as List<String>)
        }
    }

    // Always include standard command files
    filesToFetch.add("InitializeCommands.groovy")
    filesToFetch.add("ConfigureCommands.groovy")
    filesToFetch.add("RefreshCommand.groovy")
    filesToFetch.add("Helpers.groovy")

    // Include PowerMonitoring.groovy if any component has power monitoring
    Boolean hasPowerMonitoring = componentPowerMonitoring.any { k, v -> v }
    if (hasPowerMonitoring) {
        filesToFetch.add("PowerMonitoring.groovy")
        logDebug("Including PowerMonitoring.groovy for power monitoring capabilities")
    }

    logDebug("Files to fetch asynchronously: ${filesToFetch}")

    // Store partial driver and files list in atomicState for completion callback
    // IMPORTANT: Set inFlight to total count BEFORE launching any requests to avoid race condition
    Map current = atomicState.driverGeneration ?: [inFlight: 0, errors: 0, files: [:]]
    current.partialDriver = driver.toString()
    current.filesToFetch = filesToFetch as List<String>
    current.inFlight = filesToFetch.size()
    atomicState.driverGeneration = current

    // Launch all async fetch operations (inFlight already set, don't increment in fetchFileAsync)
    // Add timestamp to bust GitHub CDN cache
    Long cacheBuster = now()
    filesToFetch.each { String fileName ->
        String fileUrl = "${baseUrl}/${fileName}?v=${cacheBuster}"
        fetchFileAsync(fileUrl, fileName)
    }

    // Return immediately - completeDriverGeneration() will be called when all fetches finish
    logDebug("Async fetches launched, waiting for callbacks to complete")
    return null
}

/**
 * Generates a dynamic driver name based on the Shelly components.
 *
 * @param components List of Shelly components (e.g., ["switch:0", "switch:1"])
 * @return Generated driver name (e.g., "Shelly Single Switch", "Shelly 2x Switch")
 */
private String generateDriverName(List<String> components, Map<String, Boolean> componentPowerMonitoring = [:]) {
    // Count component types
    Map<String, Integer> componentCounts = [:]
    Boolean hasPowerMonitoring = false

    components.each { component ->
        String baseType = component.contains(':') ? component.split(':')[0] : component
        componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1

        // Check if any component has power monitoring
        if (componentPowerMonitoring[component]) {
            hasPowerMonitoring = true
        }
    }

    // Generate name based on components
    String pmSuffix = hasPowerMonitoring ? " PM" : ""

    if (componentCounts.size() == 1) {
        String type = componentCounts.keySet().first()
        Integer count = componentCounts[type]

        String typeName = type.capitalize()
        if (count == 1) {
            return "Shelly Autoconf Single ${typeName}${pmSuffix}"
        } else {
            return "Shelly Autoconf ${count}x ${typeName}${pmSuffix}"
        }
    } else {
        // Multiple component types
        return "Shelly Autoconf Multi-Component Device${pmSuffix}"
    }
}

/**
 * Converts a driver base name to a file manager filename for caching.
 * Strips the "Shelly Autoconf" prefix, converts to lowercase kebab-case,
 * and prepends the {@code shellyautoconf_} prefix with a {@code .groovy} extension.
 *
 * <p>Example: {@code "Shelly Autoconf Single Switch PM"} → {@code "shellyautoconf_single-switch-pm.groovy"}</p>
 *
 * @param driverBaseName The driver base name (without version suffix)
 * @return The file manager filename for caching
 */
@CompileStatic
static String driverNameToFileName(String driverBaseName) {
    String slug = driverBaseName
        .replaceAll(/^Shelly Autoconf\s*/, '')
        .trim()
        .toLowerCase()
        .replaceAll(/\s+/, '-')
        .replaceAll(/[^a-z0-9\-]/, '')
    return "shellyautoconf_${slug}.groovy"
}

/**
 * Saves generated driver source code to Hubitat's file manager for caching.
 * Overwrites any existing cached version. This avoids expensive GitHub fetches
 * on subsequent driver installations for the same device type.
 *
 * @param driverBaseName The driver base name (without version suffix)
 * @param sourceCode The complete driver source code to cache
 */
private void saveDriverToCache(String driverBaseName, String sourceCode) {
    String fileName = driverNameToFileName(driverBaseName)
    try {
        deleteHubFileHelper(fileName)
    } catch (Exception ignored) {
        // File may not exist yet — safe to ignore
    }
    try {
        uploadHubFileHelper(fileName, sourceCode.getBytes('UTF-8'))
        logInfo("Cached driver source to file manager: ${fileName}")
    } catch (Exception e) {
        logWarn("Failed to cache driver source to file manager: ${e.message}")
    }
}

/**
 * Loads cached driver source code from Hubitat's file manager.
 * Returns {@code null} if the file does not exist (e.g., user deleted it).
 *
 * @param driverBaseName The driver base name (without version suffix)
 * @return The cached driver source code, or {@code null} if not found
 */
private String loadDriverFromCache(String driverBaseName) {
    String fileName = driverNameToFileName(driverBaseName)
    try {
        byte[] bytes = downloadHubFileHelper(fileName)
        if (bytes != null && bytes.length > 0) {
            return new String(bytes, 'UTF-8')
        }
    } catch (Exception e) {
        logDebug("No cached driver found in file manager for ${fileName}: ${e.message}")
    }
    return null
}

/**
 * Deletes a cached driver source file from Hubitat's file manager.
 * Silently ignores errors if the file does not exist.
 *
 * @param driverBaseName The driver base name (without version suffix)
 */
private void deleteDriverFromCache(String driverBaseName) {
    String fileName = driverNameToFileName(driverBaseName)
    try {
        deleteHubFileHelper(fileName)
        logDebug("Deleted cached driver from file manager: ${fileName}")
    } catch (Exception e) {
        logDebug("Could not delete cached driver ${fileName}: ${e.message}")
    }
}

/**
 * Initializes atomicState for async driver generation operations.
 * Sets up tracking for in-flight requests, errors, and fetched file contents.
 */
private void initializeAsyncDriverGeneration() {
    atomicState.driverGeneration = [
        inFlight: 0,
        errors: 0,
        files: [:],
        partialDriver: '',
        filesToFetch: []
    ]
}

/**
 * Fetches a file asynchronously from a URL and stores it in atomicState.
 * Increments in-flight counter and uses asynchttpGet with callback.
 *
 * @param url The URL to fetch from
 * @param key The key to store the file content under in atomicState
 */
private void fetchFileAsync(String url, String key) {
    logDebug("Async fetch started: ${key} from ${url}")

    Map params = [
        uri: url,
        contentType: 'text/plain',
        timeout: 15000  // 15 seconds in milliseconds
    ]

    asynchttpGet('fetchFileCallback', params, [key: key])
}

/**
 * Callback for async file fetch operations.
 * Stores fetched content in atomicState, decrements in-flight counter,
 * and increments error counter if the fetch failed.
 *
 * @param response The HTTP response object
 * @param data Map containing the key for storing the result
 */
void fetchFileCallback(response, data) {
    String key = data.key
    Map current = atomicState.driverGeneration ?: [inFlight: 0, errors: 0, files: [:]]

    try {
        if (response?.status == 200) {
            // response.data is already a String when contentType is 'text/plain'
            String content = response.data
            if (content) {
                current.files[key] = content
                logDebug("Async fetch completed: ${key} (${content.length()} chars)")
            } else {
                current.errors = (current.errors ?: 0) + 1
                logWarn("Async fetch failed: ${key} - empty content")
            }
        } else {
            current.errors = (current.errors ?: 0) + 1
            logWarn("Async fetch failed: ${key} - status ${response?.status}")
        }
    } catch (Exception e) {
        current.errors = (current.errors ?: 0) + 1
        logError("Async fetch error: ${key} - ${e.message}")
    } finally {
        current.inFlight = (current.inFlight ?: 1) - 1
        atomicState.driverGeneration = current
        logDebug("In-flight requests: ${current.inFlight}, Errors: ${current.errors}")

        // If all fetches complete, assemble the driver
        if (current.inFlight == 0) {
            completeDriverGeneration()
        }
    }
}

/**
 * Waits for all async file fetch operations to complete.
 * Polls atomicState every 500ms checking if in-flight count is 0.
 *
 * @param timeoutSeconds Maximum time to wait before timing out
 * @return true if all requests completed, false if timeout occurred
 */
private Boolean waitForAsyncCompletion(Integer timeoutSeconds = 30) {
    Integer maxIterations = timeoutSeconds * 2  // Check every 500ms
    Integer iteration = 0

    logDebug("Waiting for async operations to complete (timeout: ${timeoutSeconds}s)")

    while (iteration < maxIterations) {
        Map current = atomicState.driverGeneration ?: [inFlight: 0, errors: 0, files: [:]]
        Integer inFlight = current.inFlight ?: 0

        if (inFlight == 0) {
            logDebug("All async operations completed after ${iteration * 500}ms")
            return true
        }

        pauseExecution(500)
        iteration++

        // Log progress every 5 seconds
        if (iteration % 10 == 0) {
            logDebug("Still waiting... In-flight: ${inFlight}, Iteration: ${iteration}/${maxIterations}")
        }
    }

    logError("Async operations timed out after ${timeoutSeconds} seconds")
    return false
}

/**
 * Completes driver generation after all async file fetches finish.
 * Called by fetchFileCallback when inFlight reaches 0.
 * Assembles the complete driver from partial driver + fetched files.
 */
private void completeDriverGeneration() {
    Map results = atomicState.driverGeneration ?: [inFlight: 0, errors: 0, files: [:]]

    // Check for errors
    if (results.errors > 0) {
        logError("Driver generation failed: ${results.errors} file(s) failed to fetch")

        // If part of rebuild queue, record error and continue to next driver
        if (state.driverRebuildInProgress) {
            String rebuildKey = state.driverRebuildCurrentKey
            List<Map> errors = state.driverRebuildErrors ?: []
            errors.add([key: rebuildKey ?: 'unknown', error: "${results.errors} file fetch failures"])
            state.driverRebuildErrors = errors
            runIn(5, 'processNextDriverRebuild')
        }
        // If part of discovery queue, continue to next device despite error
        if (state.discoveryDriverInProgress && !state.driverRebuildInProgress) {
            atomicState.remove('currentDriverGeneration')
            scheduleNextDiscoveryDriver()
        }
        return
    }

    logDebug("All async fetches completed, assembling driver")

    // Start with partial driver (metadata + preferences)
    StringBuilder driver = new StringBuilder(results.partialDriver ?: '')
    List<String> filesToFetch = results.filesToFetch ?: []

    // Append fetched files in correct order
    // 1. Lifecycle first
    if (results.files['Lifecycle.groovy']) {
        String lifecycleContent = results.files['Lifecycle.groovy']
        logInfo("Adding Lifecycle.groovy (${lifecycleContent?.length() ?: 0} chars)")
        logDebug("Lifecycle.groovy contains parse(): ${lifecycleContent?.contains('void parse(') ?: false}")
        driver.append(lifecycleContent)
        driver.append("\n")
        logDebug("Added Lifecycle.groovy")
    } else {
        logError("Lifecycle.groovy was not fetched!")
    }

    // 2. Standard command files
    ['InitializeCommands.groovy', 'ConfigureCommands.groovy', 'RefreshCommand.groovy'].each { String fileName ->
        if (results.files[fileName]) {
            driver.append(results.files[fileName])
            driver.append("\n")
            logDebug("Added ${fileName}")
        }
    }

    // 3. Component-specific command files
    filesToFetch.each { String fileName ->
        if (fileName != 'Lifecycle.groovy' &&
            fileName != 'InitializeCommands.groovy' &&
            fileName != 'ConfigureCommands.groovy' &&
            fileName != 'RefreshCommand.groovy' &&
            fileName != 'Helpers.groovy') {
            if (results.files[fileName]) {
                driver.append(results.files[fileName])
                driver.append("\n")
                logDebug("Added ${fileName}")
            }
        }
    }

    // 4. Helpers.groovy last
    if (results.files['Helpers.groovy']) {
        driver.append(results.files['Helpers.groovy'])
        driver.append("\n")
        logDebug("Added Helpers.groovy")
    }

    String driverCode = driver.toString()
    logInfo("Generated driver code:\n${driverCode}", true)

    // Cache the assembled driver source to file manager before installing
    Map genContext = atomicState.currentDriverGeneration
    if (genContext) {
        List<String> comps = genContext.components as List<String>
        Map<String, Boolean> pmMap = (genContext.componentPowerMonitoring ?: [:]) as Map<String, Boolean>
        String driverName = generateDriverName(comps, pmMap)
        saveDriverToCache(driverName, driverCode)
    }

    // Install the generated driver
    installDriver(driverCode)

    // Post-install: register driver and handle context
    if (genContext) {
        List<String> comps = genContext.components as List<String>
        Map<String, Boolean> pmMap = (genContext.componentPowerMonitoring ?: [:]) as Map<String, Boolean>
        String driverName = generateDriverName(comps, pmMap)
        String version = getAppVersion()
        String driverNameWithVersion = "${driverName} v${version}"

        if (genContext.isRebuild) {
            // Rebuild mode: update tracking only, no device creation context needed
            logInfo("Rebuild complete: driver installed as ${driverNameWithVersion}")
        } else if (genContext.ipKey) {
            // New device mode: store driver name for device creation
            if (state.discoveredShellys[genContext.ipKey]) {
                state.discoveredShellys[genContext.ipKey].generatedDriverName = driverNameWithVersion
                logInfo("Stored driver name for ${genContext.ipKey}: ${driverNameWithVersion}")
            }
        }

        // Register/update in tracking system with full metadata
        registerAutoDriver(driverNameWithVersion, 'ShellyUSA', version, comps, pmMap)

        // Clear the generation context
        atomicState.remove('currentDriverGeneration')
    }

    // Clean up any duplicate drivers
    logInfo("Cleaning up duplicate drivers...")
    pauseExecution(1000)  // Give hub time to process
    cleanupDuplicateDrivers()

    // List all installed Shelly Autoconf drivers after installation
    logInfo("Checking installed drivers after cleanup...")
    pauseExecution(500)
    listAutoconfDrivers()

    // If part of rebuild queue, schedule the next driver
    if (state.driverRebuildInProgress) {
        logInfo("Rebuild: scheduling next driver in queue")
        runIn(5, 'processNextDriverRebuild')
    }

    // If part of discovery queue, schedule next device
    if (state.discoveryDriverInProgress && !state.driverRebuildInProgress) {
        scheduleNextDiscoveryDriver()
    }
}

/**
 * Cleans up unused Shelly Autoconf drivers created by this app.
 * Deletes any auto-generated driver that has no devices currently using it.
 */
private void cleanupDuplicateDrivers() {
    logInfo("Checking for unused Shelly Autoconf drivers...")

    try {
        Map driverParams = [
            uri: "http://127.0.0.1:8080",
            path: '/device/drivers',
            contentType: 'application/json',
            timeout: 5000
        ]

        httpGet(driverParams) { driverResp ->
            if (driverResp?.status != 200) { return }

            def autoconfDrivers = driverResp.data?.drivers?.findAll { driver ->
                driver.type == 'usr' &&
                driver?.namespace == 'ShellyUSA' &&
                driver?.name?.toString()?.startsWith("Shelly Autoconf")
            }

            if (!autoconfDrivers) {
                logDebug("No Shelly Autoconf drivers found")
                return
            }

            // Build set of driver names actually in use by child devices
            def childDevices = getChildDevices() ?: []
            Set<String> inUseDriverNames = childDevices.collect { it.typeName }.toSet()

            int removed = 0
            autoconfDrivers.each { driver ->
                String name = driver.name?.toString()
                if (!inUseDriverNames.contains(name)) {
                    logInfo("Removing unused driver: ${name} (ID: ${driver.id})")
                    if (deleteDriver(driver.id as Integer)) {
                        // Also remove the cached source file from file manager
                        String baseName = name?.replaceAll(/\s+v\d+(\.\d+)*$/, '')
                        if (baseName) { deleteDriverFromCache(baseName) }
                        removed++
                    }
                }
            }

            if (removed > 0) {
                logInfo("Removed ${removed} unused Shelly Autoconf driver(s)")
            } else {
                logDebug("No unused Shelly Autoconf drivers to remove")
            }
        }
    } catch (Exception e) {
        logError("Error cleaning up drivers: ${e.message}")
    }
}

/**
 * Deletes a driver by ID from the hub.
 *
 * @param driverId The driver ID to delete
 * @return true if successful, false otherwise
 */
private Boolean deleteDriver(Integer driverId) {
    try {
        // Use same endpoint as Sonos app
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: "/driver/editor/deleteJson/${driverId}",
            timeout: 10,
            ignoreSSLIssues: true
        ]

        Boolean result = false
        httpGet(params) { resp ->
            if (resp?.data?.status == true) {
                result = true
            } else {
                logWarn("Failed to delete driver ${driverId}: ${resp?.data}")
            }
        }
        return result
    } catch (Exception e) {
        logError("Error deleting driver ${driverId}: ${e.message}")
        return false
    }
}

private void listAutoconfDrivers() {
    logInfo("Checking for installed Shelly Autoconf drivers...")

    try {
        // Get all drivers
        Map driverParams = [
            uri: "http://127.0.0.1:8080",
            path: '/device/drivers',
            contentType: 'application/json',
            timeout: 5000
        ]

        httpGet(driverParams) { driverResp ->
            if (driverResp?.status == 200) {
                // Filter for user-installed Shelly Autoconf drivers
                def autoconfDrivers = driverResp.data?.drivers?.findAll { driver ->
                    driver.type == 'usr' &&
                    driver?.namespace == 'ShellyUSA' &&
                    driver?.name?.startsWith("Shelly Autoconf")
                }

                if (autoconfDrivers.size() > 0) {
                    // Count actual child devices per driver using Hubitat's child device API
                    def childDevices = getChildDevices() ?: []

                    logInfo("Found ${autoconfDrivers.size()} installed Shelly Autoconf driver(s):")
                    autoconfDrivers.each { driver ->
                        def deviceCount = childDevices.count { it.typeName == driver.name }
                        def inUseStatus = deviceCount > 0 ? "IN USE by ${deviceCount} device(s)" : "NOT IN USE"
                        logInfo("  - ${driver.name} (ID: ${driver.id}) - ${inUseStatus}")
                    }
                } else {
                    logInfo("No Shelly Autoconf drivers installed")
                }
            } else {
                logWarn("Failed to get driver list: HTTP ${driverResp?.status}")
            }
        }
    } catch (Exception e) {
        logError("Error querying installed drivers: ${e.message}")
    }
}

/**
 * Installs a driver on the hub by posting the source code.
 * Creates a new driver entry if it doesn't exist.
 *
 * @param sourceCode The complete driver source code to install
 */
private void installDriver(String sourceCode) {
    logInfo("Installing/updating generated driver...")

    try {
        // Extract driver name from source code
        def nameMatch = (sourceCode =~ /name:\s*['"]([^'"]+)['"]/)
        if (!nameMatch.find()) {
            logError("Could not extract driver name from source code")
            return
        }
        String driverName = nameMatch.group(1)
        String namespace = "ShellyUSA"

        // Strip version suffix (e.g. "Shelly Autoconf Single Switch PM v1.0.5" -> "Shelly Autoconf Single Switch PM")
        // so we can match against any previously installed version of the same driver
        String baseName = driverName.replaceAll(/\s+v\d+(\.\d+)*$/, '')
        logDebug("Looking for existing driver: ${driverName} (${namespace}), base name: ${baseName}")

        // Check if driver already exists (match by base name to find older versions)
        Map driverParams = [
            uri: "http://127.0.0.1:8080",
            path: '/device/drivers',
            contentType: 'application/json',
            timeout: 5000
        ]

        def existingDriver = null
        httpGet(driverParams) { resp ->
            if (resp?.status == 200) {
                existingDriver = resp.data?.drivers?.find { driver ->
                    driver.type == 'usr' &&
                    driver?.namespace == namespace &&
                    (driver?.name == driverName || driver?.name?.toString()?.replaceAll(/\s+v\d+(\.\d+)*$/, '') == baseName)
                }
            }
        }

        // URL-encode the source code
        String encodedSource = java.net.URLEncoder.encode(sourceCode, 'UTF-8')

        if (existingDriver) {
            // Update existing driver
            logInfo("Driver already exists (ID: ${existingDriver.id}), updating...")

            String body = "id=${existingDriver.id}&version=${existingDriver.version ?: 1}&source=${encodedSource}"

            Map params = [
                uri: "http://127.0.0.1:8080",
                path: '/driver/ajax/update',
                headers: [
                    'Content-Type': 'application/x-www-form-urlencoded'
                ],
                body: body,
                timeout: 30,
                requestContentType: 'application/x-www-form-urlencoded'
            ]

            httpPost(params) { resp ->
                if (resp?.status == 200 && resp?.data) {
                    def result = resp.data
                    if (result?.status == 'success') {
                        logInfo("✓ Driver updated successfully!")
                        logInfo("  Driver ID: ${existingDriver.id}")
                        logInfo("  New version: ${result.version}")
                    } else {
                        logError("✗ Driver update failed: ${result?.errorMessage ?: 'Unknown error'}")
                    }
                } else {
                    logError("✗ HTTP error ${resp?.status}")
                }
            }
        } else {
            // Create new driver
            logInfo("Driver does not exist, creating new...")

            String body = "id=&version=1&source=${encodedSource}&create=Create"

            Map params = [
                uri: "http://127.0.0.1:8080",
                path: '/driver/save',
                headers: [
                    'Content-Type': 'application/x-www-form-urlencoded'
                ],
                body: body,
                timeout: 30,
                requestContentType: 'application/x-www-form-urlencoded'
            ]

            httpPost(params) { resp ->
                if (resp?.status == 200 || resp?.status == 302) {
                    logInfo("✓ Driver created successfully!")
                } else {
                    logError("✗ HTTP error ${resp?.status}")
                }
            }
        }
    } catch (Exception e) {
        logError("Error installing driver: ${e.message}")
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

/**
 * Builds a DevicePower.GetStatus RPC command map.
 *
 * @param id The device power component ID (default 0)
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap devicePowerGetStatusCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerGetStatus",
    "method" : "DevicePower.GetStatus",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

/**
 * Builds a DevicePower.GetConfig RPC command map.
 *
 * @param id The device power component ID (default 0)
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap devicePowerGetConfigCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerGetConfig",
    "method" : "DevicePower.GetConfig",
    "params" : [
      "id" : id
    ]
  ]
  return command
}

/**
 * Builds a DevicePower.SetConfig RPC command map.
 *
 * @param id The device power component ID (default 0)
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap devicePowerSetConfigCommand(Integer id = 0) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "devicePowerSetConfig",
    "method" : "DevicePower.SetConfig",
    "params" : [
      "id" : id,
      "config" : [:]
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

/**
 * Builds a Webhook.Create RPC command to register a webhook on a Shelly device.
 *
 * @param cid Component ID (e.g., 0)
 * @param event Event name (e.g., "temperature.change")
 * @param name Webhook name (e.g., "hubitat_temperature")
 * @param urls List of URLs to call when the event fires
 * @param src Source identifier for the RPC call
 * @return LinkedHashMap containing the Webhook.Create RPC command
 */
@CompileStatic
LinkedHashMap webhookCreateCommand(Integer cid, String event, String name, List<String> urls, String src = 'webhookCreate') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.Create",
    "params" : [
      "cid"    : cid,
      "enable" : true,
      "event"  : event,
      "name"   : name,
      "urls"   : urls
    ]
  ]
  return command
}

/**
 * Builds a Webhook.Update RPC command to update an existing webhook on a Shelly device.
 *
 * @param id Webhook ID to update
 * @param urls Updated list of URLs
 * @param enable Whether the webhook should be enabled
 * @param src Source identifier for the RPC call
 * @return LinkedHashMap containing the Webhook.Update RPC command
 */
@CompileStatic
LinkedHashMap webhookUpdateCommand(Integer id, List<String> urls, Boolean enable = true, String src = 'webhookUpdate') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.Update",
    "params" : [
      "id"     : id,
      "enable" : enable,
      "urls"   : urls
    ]
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

/**
 * Builds a Script.Create RPC command map.
 *
 * @param name The name for the new script
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap scriptCreateCommand(String name) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptCreate",
    "method" : "Script.Create",
    "params" : ["name": name]
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

/**
 * Builds a Script.GetCode RPC command map.
 *
 * @param id The script ID
 * @param offset Byte offset to start reading from (0 = beginning)
 * @param len Number of bytes to read (0 = read all)
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap scriptGetCodeCommand(Integer id, Integer offset = 0, Integer len = 0) {
  LinkedHashMap params = ["id": id, "offset": offset] as LinkedHashMap
  if (len > 0) { params.put("len", len) }
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptGetCode",
    "method" : "Script.GetCode",
    "params" : params
  ]
  return command
}

/**
 * Builds a Script.Eval RPC command map to evaluate code in a running script's context.
 *
 * @param id The script ID (script must be running)
 * @param code The code to evaluate
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap scriptEvalCommand(Integer id, String code) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptEval",
    "method" : "Script.Eval",
    "params" : ["id": id, "code": code]
  ]
  return command
}

/**
 * Builds a Script.GetConfig RPC command map.
 *
 * @param id The script ID
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap scriptGetConfigCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptGetConfig",
    "method" : "Script.GetConfig",
    "params" : ["id": id]
  ]
  return command
}

/**
 * Builds a Script.GetStatus RPC command map.
 *
 * @param id The script ID
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap scriptGetStatusCommand(Integer id) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "scriptGetStatus",
    "method" : "Script.GetStatus",
    "params" : ["id": id]
  ]
  return command
}

// ═══════════════════════════════════════════════════════════════
// ║  KVS (Key-Value Store) Command Maps                         ║
// ╚═══════════════════════════════════════════════════════════════╝

/**
 * Builds a KVS.Set RPC command map to store a key-value pair.
 *
 * @param key The key to set
 * @param value The value to store
 * @param etag Optional ETag for conditional update
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap kvsSetCommand(String key, Object value, String etag = null) {
  LinkedHashMap params = ["key": key, "value": value] as LinkedHashMap
  if (etag != null) { params.put("etag", etag) }
  LinkedHashMap command = [
    "id" : 0,
    "src" : "kvsSet",
    "method" : "KVS.Set",
    "params" : params
  ]
  return command
}

/**
 * Builds a KVS.Get RPC command map to retrieve a value by key.
 *
 * @param key The key to retrieve
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap kvsGetCommand(String key) {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "kvsGet",
    "method" : "KVS.Get",
    "params" : ["key": key]
  ]
  return command
}

/**
 * Builds a KVS.GetMany RPC command map to retrieve multiple key-value pairs.
 *
 * @param match Glob pattern to match keys (default '*' for all)
 * @param offset Optional offset for pagination
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap kvsGetManyCommand(String match = '*', Integer offset = null) {
  LinkedHashMap params = ["match": match] as LinkedHashMap
  if (offset != null) { params.put("offset", offset) }
  LinkedHashMap command = [
    "id" : 0,
    "src" : "kvsGetMany",
    "method" : "KVS.GetMany",
    "params" : params
  ]
  return command
}

/**
 * Builds a KVS.List RPC command map to list keys in the store.
 *
 * @param match Glob pattern to match keys (default '*' for all)
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap kvsListCommand(String match = '*') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : "kvsList",
    "method" : "KVS.List",
    "params" : ["match": match]
  ]
  return command
}

/**
 * Builds a KVS.Delete RPC command map to remove a key-value pair.
 *
 * @param key The key to delete
 * @param etag Optional ETag for conditional delete
 * @return LinkedHashMap containing the RPC command
 */
@CompileStatic
LinkedHashMap kvsDeleteCommand(String key, String etag = null) {
  LinkedHashMap params = ["key": key] as LinkedHashMap
  if (etag != null) { params.put("etag", etag) }
  LinkedHashMap command = [
    "id" : 0,
    "src" : "kvsDelete",
    "method" : "KVS.Delete",
    "params" : params
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
    postCommandSync(scriptCreateCommand('HubitatBLEHelper'))
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

/** Helper for uploadHubFile() calls (file manager upload) */
private void uploadHubFileHelper(String fileName, byte[] bytes) {
  uploadHubFile(fileName, bytes)
}

/** Helper for downloadHubFile() calls (file manager download) */
private byte[] downloadHubFileHelper(String fileName) {
  return downloadHubFile(fileName)
}

/** Helper for deleteHubFile() calls (file manager delete) */
private void deleteHubFileHelper(String fileName) {
  deleteHubFile(fileName)
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
// ║  Driver Auto-Update / Rebuild Queue                          ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Driver Auto-Update */

/**
 * Initiates serial rebuild of all tracked auto-generated drivers.
 * Builds a queue from state.autoDrivers and processes one driver at a time.
 * Each driver regeneration is async (fetches files from GitHub), so the queue
 * uses runIn() callbacks chained from completeDriverGeneration().
 */
private void rebuildAllTrackedDrivers() {
    initializeDriverTracking()

    Map allDrivers = state.autoDrivers ?: [:]
    if (allDrivers.isEmpty()) {
        logInfo("No tracked drivers to rebuild")
        return
    }

    // Prevent concurrent rebuilds
    if (state.driverRebuildInProgress) {
        logWarn("Driver rebuild already in progress, skipping")
        return
    }

    // Build queue of driver keys
    List<String> queue = allDrivers.keySet() as List<String>
    state.driverRebuildQueue = queue
    state.driverRebuildInProgress = true
    state.driverRebuildCurrentKey = null
    state.driverRebuildErrors = []

    logInfo("Starting serial rebuild of ${queue.size()} tracked driver(s): ${queue}")
    appendLog('info', "Rebuilding ${queue.size()} auto-generated driver(s)...")

    // Start processing the first item after a short delay
    runIn(2, 'processNextDriverRebuild')
}

/**
 * Processes one driver from the rebuild queue.
 * Pops the next driver key, reads its stored metadata, and triggers
 * async driver regeneration. completeDriverGeneration() will chain
 * back to this method when done.
 */
void processNextDriverRebuild() {
    List<String> queue = state.driverRebuildQueue ?: []

    if (queue.isEmpty()) {
        finishDriverRebuild()
        return
    }

    // Pop the first item
    String key = queue.remove(0)
    state.driverRebuildQueue = queue
    state.driverRebuildCurrentKey = key

    Map driverInfo = state.autoDrivers[key]
    if (!driverInfo) {
        logWarn("Driver ${key} not found in tracking data, skipping")
        runIn(1, 'processNextDriverRebuild')
        return
    }

    logInfo("Rebuilding driver: ${key} (${queue.size()} remaining)")
    appendLog('info', "Rebuilding: ${driverInfo.name}")

    try {
        List<String> components = driverInfo.components as List<String>
        Map<String, Boolean> pmMap = (driverInfo.componentPowerMonitoring ?: [:]) as Map<String, Boolean>

        if (!components || components.isEmpty()) {
            logWarn("Driver ${key} has no components metadata, skipping")
            List<Map> errors = state.driverRebuildErrors ?: []
            errors.add([key: key, error: "No components metadata stored"])
            state.driverRebuildErrors = errors
            runIn(1, 'processNextDriverRebuild')
            return
        }

        // Store context for the async completion callback
        atomicState.currentDriverGeneration = [
            ipKey: null,
            components: components,
            componentPowerMonitoring: pmMap,
            isRebuild: true,
            rebuildKey: key
        ]

        // Async: will call completeDriverGeneration() when all fetches finish,
        // which chains back to processNextDriverRebuild()
        generateHubitatDriver(components, pmMap)

    } catch (Exception e) {
        logError("Failed to rebuild driver ${key}: ${e.message}")
        List<Map> errors = state.driverRebuildErrors ?: []
        errors.add([key: key, error: e.message])
        state.driverRebuildErrors = errors

        // Continue with next driver despite the error
        runIn(2, 'processNextDriverRebuild')
    }
}

/**
 * Called when the rebuild queue is fully processed.
 * Logs a summary of successes and failures, then cleans up queue state.
 */
private void finishDriverRebuild() {
    state.driverRebuildInProgress = false
    state.driverRebuildCurrentKey = null

    List<Map> errors = state.driverRebuildErrors ?: []

    if (errors.isEmpty()) {
        logInfo("All tracked drivers rebuilt successfully")
        appendLog('info', "All drivers rebuilt successfully")
    } else {
        logWarn("Driver rebuild completed with ${errors.size()} error(s):")
        errors.each { err ->
            logWarn("  - ${err.key}: ${err.error}")
        }
        appendLog('warn', "Driver rebuild finished with ${errors.size()} error(s)")
    }

    // Clean up queue state
    state.remove('driverRebuildQueue')
    state.remove('driverRebuildErrors')
    state.remove('driverRebuildCurrentKey')
}

/* #endregion Driver Auto-Update */

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
private String getAppVersion() { return APP_VERSION }

// ═══════════════════════════════════════════════════════════════
// ║  App Auto-Update                                            ║
// ╚═══════════════════════════════════════════════════════════════╝

/**
 * Aggressive dev-mode auto-update. Downloads the app source from the current
 * branch every minute, compares a SHA-256 hash to the last applied update,
 * and installs the new code if it changed.
 */
void aggressiveUpdateCheck() {
    String branch = GITHUB_BRANCH
    String url = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/Apps/ShellyDeviceManager.groovy"

    String source = downloadFile(url)
    if (!source) {
        logDebug("Aggressive update: failed to download source from ${branch}")
        return
    }

    String hash = java.security.MessageDigest.getInstance('SHA-256').digest(source.getBytes('UTF-8')).encodeHex().toString()
    if (hash == state.lastAggressiveHash) {
        return // no change
    }

    logInfo("Aggressive update: source changed on ${branch}, updating app code...")
    Boolean success = updateAppCode(source)
    if (success) {
        state.lastAggressiveHash = hash
        logInfo("Aggressive update: app code updated from ${branch}")
        appendLog('info', "Dev auto-update applied from ${branch}")
    } else {
        logError("Aggressive update: failed to apply update")
    }
}

/**
 * Checks for a newer version of this app on GitHub Releases and updates
 * the installed app code if a newer version is available. Scheduled daily
 * at 3AM when auto-update is enabled.
 */
void checkForAppUpdate() {
    logInfo("Checking for app updates...")

    String latestVersion = getLatestGitHubReleaseVersion()
    if (!latestVersion) {
        logDebug("Could not determine latest release version from GitHub")
        return
    }

    String currentVersion = getAppVersion()
    if (!isNewerVersion(latestVersion, currentVersion)) {
        logDebug("App is up to date (current: ${currentVersion}, latest: ${latestVersion})")
        return
    }

    logInfo("App update available: ${currentVersion} → ${latestVersion}")
    appendLog('info', "App update available: ${currentVersion} → ${latestVersion}")

    // Download the latest app source from the release tag
    String tag = "app-v${latestVersion}"
    String newSource = downloadFile(
        "https://raw.githubusercontent.com/${GITHUB_REPO}/${tag}/Apps/ShellyDeviceManager.groovy"
    )
    if (!newSource) {
        logError("Failed to download app source for version ${latestVersion}")
        appendLog('error', "Auto-update failed: could not download source")
        return
    }

    Boolean success = updateAppCode(newSource)
    if (success) {
        logInfo("App updated to version ${latestVersion}")
        appendLog('info', "App updated to ${latestVersion}")
    } else {
        logError("Failed to update app code to ${latestVersion}")
        appendLog('error', "Auto-update failed: could not apply update")
    }
}

/**
 * Queries the GitHub Releases API for the latest app-specific release.
 * Filters for releases tagged with the {@code app-v} prefix to avoid
 * collisions with driver releases (which use plain {@code v} prefix).
 *
 * @return The version string (e.g., "1.1.0") from the latest app release, or null on failure
 */
private String getLatestGitHubReleaseVersion() {
    try {
        Map params = [
            uri: "https://api.github.com",
            path: "/repos/${GITHUB_REPO}/releases",
            query: [per_page: 20],
            contentType: 'application/json',
            timeout: 15
        ]

        String version = null
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data) {
                for (release in resp.data) {
                    String tag = release.tag_name as String
                    if (tag?.startsWith('app-v')) {
                        version = tag.substring(5) // strip 'app-v' prefix
                        break
                    }
                }
            }
        }
        return version
    } catch (Exception e) {
        logError("Failed to check GitHub releases: ${e.message}")
        return null
    }
}

/**
 * Compares two semantic version strings to determine if the candidate
 * is newer than the current version. Strips a leading 'v' if present.
 *
 * @param candidate The candidate version (e.g., "v1.1.0" or "1.1.0")
 * @param current The current version (e.g., "1.0.0")
 * @return true if candidate is newer than current
 */
@CompileStatic
private Boolean isNewerVersion(String candidate, String current) {
    String c = candidate.startsWith('v') ? candidate.substring(1) : candidate
    String r = current.startsWith('v') ? current.substring(1) : current

    List<Integer> candidateParts = c.tokenize('.').collect { it as Integer }
    List<Integer> currentParts = r.tokenize('.').collect { it as Integer }

    // Pad to equal length
    while (candidateParts.size() < currentParts.size()) { candidateParts.add(0) }
    while (currentParts.size() < candidateParts.size()) { currentParts.add(0) }

    for (int i = 0; i < candidateParts.size(); i++) {
        if (candidateParts[i] > currentParts[i]) { return true }
        if (candidateParts[i] < currentParts[i]) { return false }
    }
    return false
}

/**
 * Updates this app's source code on the Hubitat hub via the local API.
 * Lists installed apps to find this app's code ID, then posts the new source.
 *
 * @param sourceCode The new app source code to install
 * @return true if the update succeeded
 */
private Boolean updateAppCode(String sourceCode) {
    try {
        String cookie = login()
        if (!cookie) {
            logError("Auto-update: failed to authenticate with hub")
            return false
        }

        // Find this app's code ID by listing installed apps
        Integer appCodeId = getAppCodeId(cookie)
        if (!appCodeId) {
            logError("Auto-update: could not find app code ID")
            return false
        }

        // Get the current app code version for the update request
        String currentHubVersion = getInstalledAppVersion(cookie, appCodeId)

        Map updateParams = [
            uri: "http://127.0.0.1:8080",
            path: '/app/ajax/update',
            contentType: 'application/json',
            requestContentType: 'application/x-www-form-urlencoded',
            headers: ['Cookie': cookie],
            body: [
                id: appCodeId,
                version: currentHubVersion ?: '',
                source: sourceCode
            ],
            timeout: 300,
            ignoreSSLIssues: true
        ]

        Boolean result = false
        httpPost(updateParams) { resp ->
            if (resp.data?.status == 'success') {
                logInfo("Auto-update: app code updated successfully")
                result = true
            } else {
                logError("Auto-update: update failed - response: ${resp.data}")
            }
        }
        return result
    } catch (Exception e) {
        logError("Auto-update error: ${e.message}")
        return false
    }
}

/**
 * Finds this app's code ID by listing installed apps from the Hubitat local API.
 *
 * @param cookie The authentication cookie
 * @return The app code ID, or null if not found
 */
private Integer getAppCodeId(String cookie) {
    try {
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: '/app/list',
            textParser: true,
            headers: ['Cookie': cookie],
            timeout: 15
        ]

        Integer codeId = null
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data) {
                // /app/list returns HTML; parse with regex to find our app's code ID
                // Links are formatted as: /app/edit/sources?id=123
                // followed by the app name in the same row
                String html = resp.data.text
                // Match rows containing our app name and extract the ID from the link
                def matcher = (html =~ /app\/edit\/sources\?id=(\d+)["'][^>]*>[^<]*(?:Shelly Device Manager|Shelly mDNS Discovery)/)
                if (!matcher.find()) {
                    // Try a broader pattern: find the ID near our app name
                    matcher = (html =~ /id=(\d+)[\s\S]{0,300}?(?:Shelly Device Manager|Shelly mDNS Discovery)/)
                }
                if (matcher.find()) {
                    codeId = matcher.group(1) as Integer
                    logDebug("Found app code ID: ${codeId}")
                } else {
                    logError("Could not find app code entry in /app/list HTML response")
                }
            }
        }
        return codeId
    } catch (Exception e) {
        logError("Failed to list apps: ${e.message}")
        return null
    }
}

/**
 * Gets the installed version string of this app's code from the hub.
 *
 * @param cookie The authentication cookie
 * @param appCodeId The app code ID
 * @return The version string, or null if not found
 */
private String getInstalledAppVersion(String cookie, Integer appCodeId) {
    try {
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: "/app/ajax/code",
            query: [id: appCodeId],
            contentType: 'application/json',
            headers: ['Cookie': cookie],
            timeout: 15
        ]

        String foundVersion = null
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data?.source) {
                def matcher = (resp.data.source =~ /version:\s*['"]([^'"]+)['"]/)
                if (matcher.find()) {
                    foundVersion = matcher.group(1)
                }
            }
        }
        return foundVersion
    } catch (Exception e) {
        logError("Failed to get installed app version: ${e.message}")
        return null
    }
}

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
 * @param componentPowerMonitoring Map of component names to power monitoring capability
 */
private void registerAutoDriver(String driverName, String namespace, String version, List<String> components, Map<String, Boolean> componentPowerMonitoring = [:]) {
  initializeDriverTracking()

  String key = "${namespace}.${driverName}"

  // Preserve existing devicesUsing list if updating an existing entry
  List<String> existingDevices = state.autoDrivers[key]?.devicesUsing ?: []

  state.autoDrivers[key] = [
    name: driverName,
    namespace: namespace,
    version: version,
    components: components,
    componentPowerMonitoring: componentPowerMonitoring,
    installedAt: state.autoDrivers[key]?.installedAt ?: now(),
    lastUpdated: now(),
    devicesUsing: existingDevices
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

// ═══════════════════════════════════════════════════════════════
// Component Device Command Handlers
// ═══════════════════════════════════════════════════════════════
/* #region Component Device Command Handlers */

/**
 * Handles on() command from child switch component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentOn(def childDevice) {
  sendSwitchCommand(childDevice, true)
}

/**
 * Handles off() command from child switch component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentOff(def childDevice) {
  sendSwitchCommand(childDevice, false)
}

/**
 * Sends a Switch.Set command to a Shelly device.
 * Extracts the device IP address and sends the command via JSON-RPC.
 *
 * @param childDevice The child device to control
 * @param onState true to turn on, false to turn off
 */
private void sendSwitchCommand(def childDevice, Boolean onState) {
  String action = onState ? 'on' : 'off'
  logDebug("sendSwitchCommand(${action}) called from device: ${childDevice.displayName}")

  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("sendSwitchCommand: No IP address found for device ${childDevice.displayName}")
      return
    }

    String rpcUri = "http://${ipAddress}/rpc"
    logDebug("sendSwitchCommand: sending ${action} command to ${rpcUri}")

    // Assuming switch ID 0 for now - may need to handle multiple switches per device later
    LinkedHashMap command = switchSetCommand(onState, 0)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("sendSwitchCommand: response from ${ipAddress}: ${response}")

  } catch (Exception e) {
    logError("sendSwitchCommand(${action}) exception for ${childDevice.displayName}: ${e.message}")
    // Trigger watchdog scan to detect possible IP change (respects 5-min cooldown)
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/* #endregion Component Device Command Handlers */