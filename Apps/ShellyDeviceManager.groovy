@Field static ConcurrentHashMap<String, MessageDigest> messageDigests = new java.util.concurrent.ConcurrentHashMap<String, MessageDigest>()
@Field static ConcurrentHashMap<String, LinkedHashMap> authMaps = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
@Field static ConcurrentHashMap<String, Boolean> foundDevices = new java.util.concurrent.ConcurrentHashMap<String, Boolean>()
@Field static groovy.json.JsonSlurper slurper = new groovy.json.JsonSlurper()

// IMPORTANT: When bumping the version in definition() below, also update APP_VERSION.
// These two values MUST match. APP_VERSION is used at runtime to embed the version
// into prebuilt drivers and to detect app updates for automatic driver updates.
@Field static final String APP_VERSION = "1.0.34"

// GitHub repository and branch used for fetching resources (scripts, component definitions, auto-updates).
@Field static final String GITHUB_REPO = 'ShellyUSA/Hubitat-Drivers'
@Field static final String GITHUB_BRANCH = 'master'

// Model-specific driver overrides for Gen 1 devices that need dedicated drivers
// instead of the generic generated driver name. Checked before generateDriverName().
@Field static final Map<String, String> GEN1_MODEL_DRIVER_OVERRIDE = [
    'SHPLG-1':  'Shelly Gen1 Plug',
    'SHPLG-S':  'Shelly Gen1 Plug S',
    'SHPLG-U1': 'Shelly Gen1 Plug S',
    'SHBLB-1':  'Shelly Gen1 Bulb',
    'SHCB-1':   'Shelly Gen1 Bulb',
    'SHVIN-1':  'Shelly Gen1 Single Dimmer',
    'SHBDUO-1': 'Shelly Gen1 Duo',
    'SHGS-1':   'Shelly Gen1 Gas Sensor',
    'SHSN-1':   'Shelly Gen1 Sense',
    'SHUNI-1':  'Shelly Gen1 Uni Parent',
    // SHRGBW2 intentionally excluded — requires dynamic mode detection (color vs white)
]

// Pre-built driver files committed to the repo. Maps generateDriverName() output to GitHub path.
// New device types should be added here as prebuilt .groovy files.
@Field static final Map<String, String> PREBUILT_DRIVERS = [
    // Single-component standalone drivers
    'Shelly Autoconf Single Switch': 'UniversalDrivers/ShellySingleSwitch.groovy',
    'Shelly Autoconf Single Switch PM': 'UniversalDrivers/ShellySingleSwitchPM.groovy',
    'Shelly Autoconf TH Sensor': 'UniversalDrivers/ShellyTHSensor.groovy',

    // Multi-component parent drivers (create driver-level children)
    'Shelly Autoconf 2x Switch Parent': 'UniversalDrivers/Shelly2xSwitchParent.groovy',
    'Shelly Autoconf 2x Switch PM Parent': 'UniversalDrivers/Shelly2xSwitchPMParent.groovy',
    'Shelly Autoconf Single Cover PM Parent': 'UniversalDrivers/ShellySingleCoverPMParent.groovy',
    'Shelly Autoconf 4x Input Parent': 'UniversalDrivers/Shelly4xInputParent.groovy',

    // Fallback parent driver for unknown/unsupported patterns
    'Shelly Autoconf Parent': 'UniversalDrivers/ShellyAutoconfParent.groovy',

    // Gen 1 single-component standalone drivers
    'Shelly Gen1 Single Switch': 'UniversalDrivers/ShellyGen1SingleSwitch.groovy',
    'Shelly Gen1 Single Switch PM': 'UniversalDrivers/ShellyGen1SingleSwitchPM.groovy',
    'Shelly Gen1 Single Switch Input': 'UniversalDrivers/ShellyGen1SingleSwitchInput.groovy',
    'Shelly Gen1 Single Switch Input PM': 'UniversalDrivers/ShellyGen1SingleSwitchInputPM.groovy',
    'Shelly Gen1 Plug': 'UniversalDrivers/ShellyGen1Plug.groovy',
    'Shelly Gen1 Plug S': 'UniversalDrivers/ShellyGen1PlugS.groovy',
    'Shelly Gen1 Single Dimmer': 'UniversalDrivers/ShellyGen1SingleDimmer.groovy',
    'Shelly Gen1 Bulb': 'UniversalDrivers/ShellyGen1Bulb.groovy',
    'Shelly Gen1 Duo': 'UniversalDrivers/ShellyGen1Duo.groovy',
    'Shelly Gen1 RGBW2 Color': 'UniversalDrivers/ShellyGen1RGBW2Color.groovy',
    'Shelly Gen1 RGBW2 White Parent': 'UniversalDrivers/ShellyGen1RGBW2WhiteParent.groovy',
    'Shelly Gen1 White Channel': 'UniversalDrivers/ShellyGen1WhiteChannel.groovy',
    'Shelly Gen1 TH Sensor': 'UniversalDrivers/ShellyGen1THSensor.groovy',
    'Shelly Gen1 Flood Sensor': 'UniversalDrivers/ShellyGen1FloodSensor.groovy',
    'Shelly Gen1 Smoke Sensor': 'UniversalDrivers/ShellyGen1SmokeSensor.groovy',
    'Shelly Gen1 Gas Sensor': 'UniversalDrivers/ShellyGen1GasSensor.groovy',
    'Shelly Gen1 DW Sensor': 'UniversalDrivers/ShellyGen1DWSensor.groovy',
    'Shelly Gen1 Button': 'UniversalDrivers/ShellyGen1Button.groovy',
    'Shelly Gen1 Motion Sensor': 'UniversalDrivers/ShellyGen1MotionSensor.groovy',
    'Shelly Gen1 Sense': 'UniversalDrivers/ShellyGen1Sense.groovy',
    'Shelly Gen1 TRV': 'UniversalDrivers/ShellyGen1TRV.groovy',

    // Gen 1 multi-component parent drivers
    'Shelly Gen1 4x Switch PM Parent': 'UniversalDrivers/ShellyGen1_4xSwitchPMParent.groovy',
    'Shelly Gen1 2x Switch PM Parent': 'UniversalDrivers/ShellyGen1_2xSwitchPMParent.groovy',
    'Shelly Gen1 2x Switch Parent': 'UniversalDrivers/ShellyGen1_2xSwitchParent.groovy',
    'Shelly Gen1 Single Cover PM Parent': 'UniversalDrivers/ShellyGen1SingleCoverPMParent.groovy',
    'Shelly Gen1 3x Input Parent': 'UniversalDrivers/ShellyGen1_3xInputParent.groovy',
    'Shelly Gen1 EM Parent': 'UniversalDrivers/ShellyGen1EMParent.groovy',
    'Shelly Gen1 Uni Parent': 'UniversalDrivers/ShellyGen1UniParent.groovy',

    // BLE device drivers
    'Shelly BLU Button1': 'UniversalDrivers/ShellyBluButton1.groovy',
    'Shelly BLU Button4': 'UniversalDrivers/ShellyBluButton4.groovy',
    'Shelly BLU DoorWindow': 'UniversalDrivers/ShellyBluDoorWindow.groovy',
    'Shelly BLU HT': 'UniversalDrivers/ShellyBluHT.groovy',
    'Shelly BLU Motion': 'UniversalDrivers/ShellyBluMotion.groovy',
    'Shelly BLU WallSwitch4': 'UniversalDrivers/ShellyBluWallSwitch.groovy',
]

/**
 * Maps Shelly BLU device model codes (from BLE local_name) to driver information.
 * Used to auto-detect device type from BLE advertisements and select the correct driver.
 */
@Field static final Map<String, Map<String, String>> BLE_MODEL_TO_DRIVER = [
    'SBBT-002C':   [driverName: 'Shelly BLU Button1',     friendlyModel: 'Shelly BLU Button 1',       modelCode: 'SBBT-002C'],
    'SBBT-004CEU': [driverName: 'Shelly BLU WallSwitch4', friendlyModel: 'Shelly BLU Wall Switch 4',  modelCode: 'SBBT-004CEU'],
    'SBBT-004CUS': [driverName: 'Shelly BLU Button4',     friendlyModel: 'Shelly BLU RC Button 4',    modelCode: 'SBBT-004CUS'],
    'SBDW-002C':   [driverName: 'Shelly BLU DoorWindow',  friendlyModel: 'Shelly BLU Door/Window',    modelCode: 'SBDW-002C'],
    'SBHT-003C':   [driverName: 'Shelly BLU HT',          friendlyModel: 'Shelly BLU H&T',            modelCode: 'SBHT-003C'],
    'SBHT-103C':   [driverName: null,                      friendlyModel: 'Shelly BLU H&T Display ZB', modelCode: 'SBHT-103C'],
    'SBMO-003Z':   [driverName: 'Shelly BLU Motion',      friendlyModel: 'Shelly BLU Motion',         modelCode: 'SBMO-003Z'],
    'SBRC-005B':   [driverName: null,                      friendlyModel: 'Shelly BLU Remote',         modelCode: 'SBRC-005B'],
    'SBTR-001AEU': [driverName: null,                      friendlyModel: 'Shelly BLU TRV',            modelCode: 'SBTR-001AEU'],
    'SBWS-90CM':   [driverName: null,                      friendlyModel: 'Shelly BLU Weather Station', modelCode: 'SBWS-90CM'],
]

/**
 * Maps Shelly BLE numeric model IDs (from manufacturer data block type 0x0B or BTHome device_type_id 0xF0)
 * to driver information. These numeric IDs are more reliable than local_name strings for identification.
 */
@Field static final Map<Integer, Map<String, String>> BLE_MODEL_ID_TO_DRIVER = [
    0x0001: [driverName: 'Shelly BLU Button1',     friendlyModel: 'Shelly BLU Button 1',       modelCode: 'SBBT-002C'],
    0x0002: [driverName: 'Shelly BLU DoorWindow',  friendlyModel: 'Shelly BLU Door/Window',    modelCode: 'SBDW-002C'],
    0x0003: [driverName: 'Shelly BLU HT',          friendlyModel: 'Shelly BLU H&T',            modelCode: 'SBHT-003C'],
    0x0005: [driverName: 'Shelly BLU Motion',       friendlyModel: 'Shelly BLU Motion',         modelCode: 'SBMO-003Z'],
    0x0006: [driverName: 'Shelly BLU WallSwitch4', friendlyModel: 'Shelly BLU Wall Switch 4',  modelCode: 'SBBT-004CEU'],
    0x0007: [driverName: 'Shelly BLU Button4',     friendlyModel: 'Shelly BLU RC Button 4',    modelCode: 'SBBT-004CUS'],
    0x0008: [driverName: null,                      friendlyModel: 'Shelly BLU TRV',            modelCode: 'SBTR-001AEU'],
    0x0009: [driverName: null,                      friendlyModel: 'Shelly BLU Remote',         modelCode: 'SBRC-005B'],
    0x000B: [driverName: null,                      friendlyModel: 'Shelly BLU Weather Station', modelCode: 'SBWS-90CM'],
    0x000C: [driverName: null,                      friendlyModel: 'Shelly BLU H&T Display ZB', modelCode: 'SBHT-103C'],
]

// Script names (as they appear on the Shelly device) that are managed by this app.
// Only these scripts will be considered for automatic removal.
@Field static final List<String> MANAGED_SCRIPT_NAMES = [
    'switchstatus',
    'powermonitoring',
    'coverstatus',
    'lightstatus',
    'HubitatBLEHelper'
]

// ═══════════════════════════════════════════════════════════════
// Gen 1 Device Identification Constants
// ═══════════════════════════════════════════════════════════════

/**
 * Maps Gen 1 Shelly device type codes (from {@code /shelly} API) to friendly model names.
 * The type code is returned in the {@code type} field of the Gen 1 {@code /shelly} endpoint.
 */
@Field static final Map<String, String> GEN1_TYPE_TO_MODEL = [
    'SHSW-1':    'Shelly 1',
    'SHSW-PM':   'Shelly 1PM',
    'SHSW-L':    'Shelly 1L',
    'SHSW-21':   'Shelly 2',
    'SHSW-25':   'Shelly 2.5',
    'SHSW-44':   'Shelly 4Pro',
    'SHPLG-1':   'Shelly Plug',
    'SHPLG-S':   'Shelly Plug S',
    'SHPLG-U1':  'Shelly Plug US',
    'SHDM-1':    'Shelly Dimmer',
    'SHDM-2':    'Shelly Dimmer 2',
    'SHEM':      'Shelly EM',
    'SHEM-3':    'Shelly 3EM',
    'SHBLB-1':   'Shelly Bulb',
    'SHVIN-1':   'Shelly Vintage',
    'SHBDUO-1':  'Shelly Duo',
    'SHCB-1':    'Shelly Bulb RGBW',
    'SHRGBW2':   'Shelly RGBW2',
    'SHHT-1':    'Shelly H&T',
    'SHWT-1':    'Shelly Flood',
    'SHDW-1':    'Shelly Door/Window',
    'SHDW-2':    'Shelly Door/Window 2',
    'SHMOS-01':  'Shelly Motion',
    'SHMOS-02':  'Shelly Motion 2',
    'SHBTN-1':   'Shelly Button 1',
    'SHBTN-2':   'Shelly Button 1 v2',
    'SHGS-1':    'Shelly Gas',
    'SHIX3-1':   'Shelly i3',
    'SHUNI-1':   'Shelly Uni',
    'SHTRV-01':  'Shelly TRV',
    'SHSM-01':   'Shelly Smoke',
    'SHSN-1':    'Shelly Sense',
]

/** Gen 1 type codes that are battery-powered. All battery devices are excluded from
 *  periodic polling via {@link #isBatteryPoweredDevice}. Devices that are also
 *  unreachable most of the time (truly sleepy) are identified by
 *  {@link #isSleepyBatteryDevice} — SHMOS-*, SHSN-1 and SHTRV-01 are always-awake
 *  and NOT sleepy, even though they run on battery. */
@Field static final Set<String> GEN1_BATTERY_TYPES = [
    'SHHT-1', 'SHWT-1', 'SHDW-1', 'SHDW-2',
    'SHMOS-01', 'SHMOS-02', 'SHBTN-1', 'SHBTN-2',
    'SHTRV-01', 'SHSM-01', 'SHSN-1',
] as Set<String>

/**
 * Maps Gen 1 mDNS hostname prefixes to device type codes.
 * Gen 1 hostnames follow the pattern {@code <prefix>-<MAC>}, where MAC is 6 or 12 hex chars,
 * e.g. {@code shellyht-AABBCC} or {@code shelly1-AABBCCDDEEFF}.
 * Used to identify Gen 1 devices from mDNS alone (critical for sleeping battery devices).
 */
@Field static final Map<String, String> GEN1_HOSTNAME_TO_TYPE = [
    'shelly1':          'SHSW-1',
    'shelly1pm':        'SHSW-PM',
    'shelly1l':         'SHSW-L',
    'shellyswitch':     'SHSW-21',
    'shellyswitch25':   'SHSW-25',
    'shelly4pro':       'SHSW-44',
    'shellyplug':       'SHPLG-1',
    'shellyplug-s':     'SHPLG-S',
    'shellyplug-u1':    'SHPLG-U1',
    'shellydimmer':     'SHDM-1',
    'shellydimmer2':    'SHDM-2',
    'shellyem':         'SHEM',
    'shellyem3':        'SHEM-3',
    'shellybulb':       'SHBLB-1',
    'shellyvintage':    'SHVIN-1',
    'shellybulbduo':    'SHBDUO-1',
    'shellycolorbulb':  'SHCB-1',
    'shellyrgbw2':      'SHRGBW2',
    'shellyht':         'SHHT-1',
    'shellyflood':      'SHWT-1',
    'shellydw':         'SHDW-1',
    'shellydw2':        'SHDW-2',
    'shellymotion':     'SHMOS-01',
    'shellymotion2':    'SHMOS-02',
    'shellybutton1':    'SHBTN-1',
    'shellybutton2':    'SHBTN-2',
    'shellygas':        'SHGS-1',
    'shellyix3':        'SHIX3-1',
    'shellyuni':        'SHUNI-1',
    'shellytrv':        'SHTRV-01',
    'shellysmoke':      'SHSM-01',
    'shellysense':      'SHSN-1',
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
    version: "1.0.34"
)

preferences {
    page(name: "mainPage", install: true, uninstall: true)
}

/**
 * Renders the main page for the Shelly Device Manager app.
 * Consolidates discovery, device configuration table, and settings onto a single page.
 * Devices appear in the config table as they are discovered via SSR updates.
 *
 * @return Map containing the dynamic page definition
 */
Map mainPage() {
    if (!state.discoveredShellys) { state.discoveredShellys = [:] }
    if (!state.recentLogs) { state.recentLogs = [] }

    // Clean up orphan settings from removed pages
    app.removeSetting('selectedToCreate')
    app.removeSetting('selectedToRemove')
    app.removeSetting('selectedConfigDevice')

    // Requirement: scanning should start (or restart) when app page is opened.
    Integer remainingSecs = getRemainingDiscoverySeconds()
    if (!state.discoveryRunning || remainingSecs <= 0) {
        startDiscovery(false)
        remainingSecs = getRemainingDiscoverySeconds()
    }

    // Apply pending WiFi label edit if the user has typed a new label
    String editIp = state.pendingLabelEdit as String
    if (editIp && settings?.editLabelValue != null) {
        String newLabel = (settings.editLabelValue as String)?.trim()
        if (newLabel) {
            def device = findChildDeviceByIp(editIp)
            if (device) {
                device.setLabel(newLabel)
                logInfo("Updated label for ${editIp} to '${newLabel}'")
                appendLog('info', "Renamed ${editIp} to '${newLabel}'")
            }
        }
        state.remove('pendingLabelEdit')
        app.removeSetting('editLabelValue')
    }

    // Apply pending BLE label edit if the user has typed a new label
    String editBleMac = state.pendingBleLabelEdit as String
    if (editBleMac && settings?.editBleLabelValue != null) {
        String newBleLabel = (settings.editBleLabelValue as String)?.trim()
        if (newBleLabel) {
            def bleDevice = getChildDevice(editBleMac)
            if (bleDevice) {
                bleDevice.setLabel(newBleLabel)
                logInfo("Updated BLE label for ${editBleMac} to '${newBleLabel}'")
                appendLog('info', "Renamed BLE ${editBleMac} to '${newBleLabel}'")
                // Update cached label in discovery state
                Map discoveredBle = state.discoveredBleDevices ?: [:]
                String macKey = editBleMac.toString()
                Map bleEntry = discoveredBle[macKey] as Map
                if (bleEntry) {
                    bleEntry.hubDeviceLabel = newBleLabel
                    discoveredBle[macKey] = bleEntry
                    state.discoveredBleDevices = discoveredBle
                }
            }
        }
        state.remove('pendingBleLabelEdit')
        app.removeSetting('editBleLabelValue')
    }

    // Purge unknown BLE devices from discovery state on page load.
    // Keeps entries that resolve to a known model OR have an existing child device.
    Map discoveredBle = state.discoveredBleDevices as Map ?: [:]
    if (discoveredBle) {
        List<String> toRemove = []
        discoveredBle.each { String macKey, bleVal ->
            Map bleEntry = bleVal as Map
            Integer entryModelId = bleEntry.modelId != null ? bleEntry.modelId as Integer : null
            String entryModel = (bleEntry.model ?: '') as String
            Map<String, String> info = resolveBleDriverInfo(entryModelId, entryModel)
            Boolean hasChild = getChildDevice(macKey) != null
            if (!info && !hasChild) { toRemove.add(macKey) }
        }
        if (toRemove) {
            toRemove.each { String key -> discoveredBle.remove(key) }
            state.discoveredBleDevices = discoveredBle
            logInfo("Purged ${toRemove.size()} unknown BLE device(s) from discovery state")
        }
    }

    dynamicPage(name: "mainPage", title: "Shelly Device Manager v${APP_VERSION}", install: true, uninstall: true) {
        section() {
            if (state.discoveryRunning) {
                Long endTimeMs = state.discoveryEndTime as Long
                paragraph """<b><span id='discovery-timer'>Discovery time remaining: ${remainingSecs} seconds</span></b>
<script>
(function(){
  var endTime = ${endTimeMs};
  var el = document.getElementById('discovery-timer');
  if (!el || !endTime) return;
  function update() {
    var remaining = Math.max(0, Math.round((endTime - Date.now()) / 1000));
    el.textContent = remaining > 0
      ? 'Discovery time remaining: ' + remaining + ' seconds'
      : 'Discovery has finished.';
    if (remaining <= 0) clearInterval(timer);
  }
  var timer = setInterval(update, 1000);
  update();
})();
</script>"""
            } else {
                paragraph "<b>Discovery has stopped.</b>"
            }
            input 'btnExtendScan', 'button', title: 'Extend Scan (120s)', submitOnChange: true
            if (!state.discoveryRunning && !(state.discoveredShellys as Map)?.findAll { String k, v -> v }) {
                paragraph "<b style='color:#FF9800'>No devices discovered.</b> If this is a new installation, a hub reboot is required before mDNS discovery will find devices. Go to <i>Settings > Reboot Hub</i>, then reopen this app."
            }
        }

        // Delete confirmation (shown when user clicks delete on a device)
        if (state.pendingDeleteIp) {
            String deleteIp = state.pendingDeleteIp as String
            def deleteDevice = findChildDeviceByIp(deleteIp)
            String deleteName = deleteDevice ? deleteDevice.displayName : deleteIp
            section() {
                paragraph "<b style='color:#F44336'>Are you sure you want to remove '${deleteName}' (${deleteIp})?</b>"
                input 'btnConfirmDelete', 'button', title: 'Yes, Remove Device', submitOnChange: true
                input 'btnCancelDelete', 'button', title: 'Cancel', submitOnChange: true
            }
        }

        // Label editing input (shown when user clicks a label in the table)
        if (state.pendingLabelEdit) {
            String labelIp = state.pendingLabelEdit as String
            def labelDevice = findChildDeviceByIp(labelIp)
            String currentLabel = labelDevice ? (labelDevice.label ?: labelDevice.displayName) : labelIp
            section() {
                paragraph "<b>Editing label for device at ${labelIp}</b>"
                input name: 'editLabelValue', type: 'text', title: "New label (current: ${currentLabel})",
                    defaultValue: currentLabel, required: false, submitOnChange: true
                input 'btnCancelLabelEdit', 'button', title: 'Cancel', submitOnChange: true
            }
        }

        section() {
            input 'btnRefreshAllStatus', 'button', title: 'Refresh All Status', submitOnChange: true
            paragraph displayDeviceConfigTable()
        }

        // BLE label editing input (shown when user clicks a label in the BLE table)
        if (state.pendingBleLabelEdit) {
            String labelMac = state.pendingBleLabelEdit as String
            def labelDevice = getChildDevice(labelMac)
            String currentLabel = labelDevice ? (labelDevice.label ?: labelDevice.displayName) : labelMac
            section() {
                paragraph "<b>Editing label for BLE device ${labelMac}</b>"
                input name: 'editBleLabelValue', type: 'text', title: "New label (current: ${currentLabel})",
                    defaultValue: currentLabel, required: false, submitOnChange: true
                input 'btnCancelBleLabelEdit', 'button', title: 'Cancel', submitOnChange: true
            }
        }

        // BLE device delete confirmation
        if (state.pendingBleDeleteMac) {
            String deleteMac = state.pendingBleDeleteMac as String
            Map bleInfo = (state.discoveredBleDevices ?: [:])[deleteMac] as Map
            String deleteName = bleInfo?.hubDeviceName ?: bleInfo?.friendlyModel ?: deleteMac
            section() {
                paragraph "<b style='color:#F44336'>Are you sure you want to remove BLE device '${deleteName}' (${deleteMac})?</b>"
                input 'btnConfirmBleDelete', 'button', title: 'Yes, Remove BLE Device', submitOnChange: true
                input 'btnCancelBleDelete', 'button', title: 'Cancel', submitOnChange: true
            }
        }

        section() {
            paragraph displayBleDeviceTable()
        }

        section("Options", hideable: true) {
            input name: 'enableAutoUpdate', type: 'bool', title: 'Enable auto-update',
                description: 'Automatically checks for and installs app updates from GitHub daily at 3AM.',
                defaultValue: true, submitOnChange: true
            input name: 'enableWatchdog', type: 'bool', title: 'Enable IP address watchdog',
                description: 'Periodically scans for device IP changes via mDNS and automatically updates child devices. Also triggers a scan when a device command fails.',
                defaultValue: true, submitOnChange: true
            input name: 'gen1PollInterval', type: 'enum', title: 'Gen 1 device poll interval',
                description: 'How often to poll Gen 1 devices for power monitoring and state updates. Gen 1 devices do not support real-time power/energy reporting.',
                options: ['30':'30 seconds', '60':'1 minute', '120':'2 minutes', '300':'5 minutes', '600':'10 minutes'],
                defaultValue: '60', submitOnChange: true
            input name: 'devicePassword', type: 'password', title: 'Device password',
                description: 'Password for Shelly devices with authentication enabled. Used for Gen 2/3 digest auth and Gen 1 Basic Auth (username is always "admin").',
                required: false
        }

        section("Driver Management", hideable: true, hidden: true) {
            input name: 'rebuildOnUpdate', type: 'bool', title: 'Update drivers when app is updated',
                description: 'Automatically updates pre-built driver versions when the app version changes.',
                defaultValue: true, submitOnChange: true

            String driverMgmtHtml = renderDriverManagementHtml()
            paragraph "<span class='ssr-app-state-${app.id}-driverRebuildStatus'>${driverMgmtHtml}</span>"
            input 'btnForceRebuildDrivers', 'button', title: 'Force Update All Drivers', submitOnChange: true
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

            paragraph renderRecentLogsHtml()
        }
    }
}

/**
 * Handles button click events from the app UI.
 * Processes actions for discovery, driver management, and device configuration table buttons.
 *
 * @param buttonName The name of the button that was clicked
 */
void appButtonHandler(String buttonName) {
    if (buttonName == 'btnExtendScan') { extendDiscovery(120) }

    if (buttonName == 'btnForceRebuildDrivers') {
        Map allDrivers = state.autoDrivers ?: [:]
        if (allDrivers.isEmpty()) {
            appendLog('warn', 'No tracked drivers to update')
            return
        }
        logInfo("Manual force update of all drivers requested")
        appendLog('info', "Updating ${allDrivers.size()} driver(s)...")
        state.lastAutoconfVersion = getAppVersion()
        reinstallAllPrebuiltDrivers()
    }

    // === Device Configuration Table Buttons ===

    if (buttonName == 'btnRefreshAllStatus') {
        runIn(1, 'refreshAllDeviceStatusAsync')
        appendLog('info', 'Queued status refresh for all devices')
    }

    if (buttonName.startsWith('createDev|')) {
        String targetIp = buttonName.minus('createDev|')
        logInfo("Creating device for ${targetIp} via config table")
        createShellyDevice(targetIp)
        // Refresh cache entry after creation; defer SSR so state persists first
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('dniConflict|')) {
        String targetIp = buttonName.minus('dniConflict|')
        Map deviceInfo = state.discoveredShellys?.get(targetIp) as Map
        String mac = deviceInfo?.mac?.toString() ?: ''
        String conflictDni = mac ?: "shelly-${targetIp.replaceAll('\\.', '-')}".toString()
        appendLog('warn', "Cannot create device at ${targetIp}: a device with DNI '${conflictDni}' already exists on this hub. Remove or change the existing device's DNI first.")
    }

    if (buttonName.startsWith('removeDev|')) {
        String targetIp = buttonName.minus('removeDev|')
        state.pendingDeleteIp = targetIp
    }

    if (buttonName == 'btnConfirmDelete') {
        String targetIp = state.pendingDeleteIp as String
        if (targetIp) {
            logInfo("Removing device for ${targetIp} via config table")
            removeDeviceByIp(targetIp)
            buildDeviceStatusCacheEntry(targetIp)
            runInMillis(500, 'fireConfigTableSSR')
        }
        state.remove('pendingDeleteIp')
    }

    if (buttonName == 'btnCancelDelete') {
        state.remove('pendingDeleteIp')
    }

    if (buttonName.startsWith('installScripts|')) {
        String targetIp = buttonName.minus('installScripts|')
        logInfo("Installing scripts for ${targetIp} via config table")
        installRequiredScriptsForIp(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('enableScripts|')) {
        String targetIp = buttonName.minus('enableScripts|')
        logInfo("Enabling scripts for ${targetIp} via config table")
        enableAndStartRequiredScriptsForIp(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('installWebhooks|')) {
        String targetIp = buttonName.minus('installWebhooks|')
        logInfo("Installing webhooks for ${targetIp} via config table")
        installRequiredActionsForIp(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('installActionUrls|')) {
        String targetIp = buttonName.minus('installActionUrls|')
        logInfo("Installing Gen 1 action URLs for ${targetIp} via config table")
        installGen1ActionUrls(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('reinitDev|')) {
        String targetIp = buttonName.minus('reinitDev|')
        reinitializeDevice(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }

    if (buttonName.startsWith('editLabel|')) {
        String targetIp = buttonName.minus('editLabel|')
        state.pendingLabelEdit = targetIp
        app.removeSetting('editLabelValue')
    }

    if (buttonName == 'btnCancelLabelEdit') {
        state.remove('pendingLabelEdit')
        app.removeSetting('editLabelValue')
    }

    // === BLE Device Table Buttons ===

    if (buttonName.startsWith('editBleLabel|')) {
        String targetMac = buttonName.minus('editBleLabel|')
        state.pendingBleLabelEdit = targetMac
        app.removeSetting('editBleLabelValue')
    }

    if (buttonName == 'btnCancelBleLabelEdit') {
        state.remove('pendingBleLabelEdit')
        app.removeSetting('editBleLabelValue')
    }

    if (buttonName.startsWith('createBle|')) {
        String mac = buttonName.minus('createBle|')
        logInfo("Creating BLE device for MAC ${mac} via BLE table")
        createBleDevice(mac)
        runInMillis(500, 'fireBleTableSSR')
    }

    if (buttonName.startsWith('removeBle|')) {
        String mac = buttonName.minus('removeBle|')
        state.pendingBleDeleteMac = mac
    }

    if (buttonName == 'btnConfirmBleDelete') {
        String mac = state.pendingBleDeleteMac as String
        if (mac) {
            logInfo("Removing BLE device for MAC ${mac}")
            removeBleDevice(mac)
            runInMillis(500, 'fireBleTableSSR')
        }
        state.remove('pendingBleDeleteMac')
    }

    if (buttonName == 'btnCancelBleDelete') {
        state.remove('pendingBleDeleteMac')
    }

    if (buttonName.startsWith('toggleBleGw|')) {
        String targetIp = buttonName.minus('toggleBleGw|')
        logInfo("Toggling BLE gateway for ${targetIp}")
        toggleBleGateway(targetIp)
        buildDeviceStatusCacheEntry(targetIp)
        runInMillis(500, 'fireConfigTableSSR')
    }
}

/**
 * Creates a Hubitat device for a discovered Shelly device.
 * Retrieves device information, determines the appropriate prebuilt driver,
 * and creates a child device.
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
            appendLog('error', "Failed to create device for ${ipKey}: device may be offline")
            return
        }
    }

    // Get the generated driver name
    String driverName = deviceInfo.generatedDriverName
    if (!driverName) {
        logError("No driver available for ${ipKey}. Device may not have supported components.")
        appendLog('error', "Failed to create device for ${ipKey}: no driver available")
        return
    }

    // Branch: multi-component devices use parent-child architecture
    Boolean needsParentChild = deviceInfo.needsParentChild ?: false
    if (needsParentChild) {
        createMultiComponentDevice(ipKey, deviceInfo, driverName)
        return
    }

    // Monolithic device creation (single-component path)
    createMonolithicDevice(ipKey, deviceInfo, driverName)
}

/**
 * Creates a monolithic (single-component) Shelly device.
 * This is the original device creation path for devices with a single actuator.
 *
 * @param ipKey The device IP address
 * @param deviceInfo The discovered device information
 * @param driverName The generated driver name
 */
private void createMonolithicDevice(String ipKey, Map deviceInfo, String driverName) {
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
    String shellyGen = (deviceInfo.gen ?: '2').toString()
    Map dataMap = [
            ipAddress: ipKey,
            shellyModel: deviceInfo.model ?: 'Unknown',
            shellyId: deviceInfo.id ?: dni,
            shellyMac: deviceInfo.mac ?: '',
            shellyGen: shellyGen
    ]
    if (deviceInfo.gen1Type) {
        dataMap.gen1Type = deviceInfo.gen1Type.toString()
    }
    Map deviceProps = [
        name: deviceLabel,
        label: deviceLabel,
        data: dataMap
    ]

    logInfo("════════════════════════════════════════════════════════════")
    logInfo("  STARTING DEVICE CREATION: ${deviceLabel}")
    logInfo("════════════════════════════════════════════════════════════")
    logInfo("  DNI: ${dni}")
    logInfo("  Driver: ${driverName}")
    logInfo("  IP: ${ipKey}")
    logInfo("  Model: ${deviceInfo.model}")
    logInfo("  MAC: ${deviceInfo.mac}")
    logInfo("  Generation: ${shellyGen}")

    // Ensure the driver is installed on the hub before attempting to create the device
    if (!ensureDriverInstalled(driverName, deviceInfo)) {
        logError("Cannot create device '${deviceLabel}': driver '${driverName}' is not installed on the hub")
        appendLog('error', "Failed to create ${deviceLabel}: driver not installed")
        return
    }

    try {
        def childDevice = addChildDevice('ShellyUSA', driverName, dni, deviceProps)
        state.remove('hubDnisCachedAt') // Invalidate DNI cache after device creation

        logInfo("Created device: ${deviceLabel} using driver ${driverName}")
        appendLog('info', "Created: ${deviceLabel} (${driverName})")

        // Track this device against its driver
        associateDeviceWithDriver(driverName, 'ShellyUSA', dni)

        // Store device component config for later reference (config page, capability checks)
        storeDeviceConfig(dni, deviceInfo, driverName)

        // Set device attributes
        childDevice.updateSetting('ipAddress', ipKey)
        childDevice.initialize()

        // Install scripts and webhooks on the Shelly device
        reinitializeDevice(ipKey)

        logInfo("════════════════════════════════════════════════════════════")
        logInfo("  ✓ DEVICE CREATION COMPLETE: ${deviceLabel}")
        logInfo("════════════════════════════════════════════════════════════")

    } catch (Exception e) {
        logError("════════════════════════════════════════════════════════════")
        logError("  ✗ DEVICE CREATION FAILED: ${deviceLabel} — ${e.message}")
        logError("════════════════════════════════════════════════════════════")
        appendLog('error', "Failed to create ${deviceLabel}: ${e.message}")
    }
}

/**
 * Creates a multi-component Shelly device using parent-child architecture.
 * Creates one parent device (LAN traffic collector) plus separate child devices
 * per component (switch:0, switch:1, input:0, input:1, etc.).
 *
 * @param ipKey The device IP address
 * @param deviceInfo The discovered device information
 * @param parentDriverName The generated parent driver name
 */
private void createMultiComponentDevice(String ipKey, Map deviceInfo, String parentDriverName) {
    String mac = deviceInfo.mac ?: "shelly-${ipKey.replaceAll('\\.', '-')}"
    String parentDni = mac
    String baseLabel = deviceInfo.name ?: "Shelly ${ipKey}"
    Map deviceStatus = deviceInfo.deviceStatus ?: [:]
    Map<String, Boolean> componentPowerMonitoring = (deviceInfo.componentPowerMonitoring ?: [:]) as Map<String, Boolean>

    // Check if parent device already exists
    def existingParent = getChildDevice(parentDni)
    if (existingParent) {
        logWarn("Parent device already exists: ${existingParent.displayName} (${parentDni})")
        appendLog('warn', "Parent device already exists: ${existingParent.displayName}")
        return
    }

    logInfo("════════════════════════════════════════════════════════════")
    logInfo("  STARTING DEVICE CREATION: ${baseLabel}")
    logInfo("════════════════════════════════════════════════════════════")
    logInfo("  Parent DNI: ${parentDni}")
    logInfo("  Parent Driver: ${parentDriverName}")
    logInfo("  IP: ${ipKey}")
    logInfo("  Model: ${deviceInfo.model}")
    logInfo("  MAC: ${deviceInfo.mac}")
    logInfo("  Components: ${deviceStatus.keySet()}")

    // Step 1: Install required component drivers
    installComponentDriversForDevice(deviceInfo)

    // Step 2: Ensure parent driver is installed on the hub
    if (!ensureDriverInstalled(parentDriverName, deviceInfo)) {
        logError("Cannot create parent device '${baseLabel}': driver '${parentDriverName}' is not installed on the hub")
        appendLog('error', "Failed to create ${baseLabel}: parent driver not installed")
        return
    }

    // Step 3: Create parent device
    String shellyGen = (deviceInfo.gen ?: '2').toString()
    Map dataMap = [
            ipAddress: ipKey,
            shellyModel: deviceInfo.model ?: 'Unknown',
            shellyId: deviceInfo.id ?: parentDni,
            shellyMac: deviceInfo.mac ?: '',
            shellyGen: shellyGen,
            isParentDevice: 'true'
    ]
    if (deviceInfo.gen1Type) {
        dataMap.gen1Type = deviceInfo.gen1Type.toString()
    }
    Map parentProps = [
        name: baseLabel,
        label: baseLabel,
        data: dataMap
    ]

    try {
        def parentDevice = addChildDevice('ShellyUSA', parentDriverName, parentDni, parentProps)
        state.remove('hubDnisCachedAt') // Invalidate DNI cache after device creation
        logInfo("Created parent device: ${baseLabel} using driver ${parentDriverName}")
        appendLog('info', "Created parent: ${baseLabel} (${parentDriverName})")

        // Track parent device against its driver
        associateDeviceWithDriver(parentDriverName, 'ShellyUSA', parentDni)

        // Step 4: Set components and pmComponents data values on parent
        // The parent driver will read these to create driver-level children
        List<String> components = []
        List<String> pmComponents = []
        Set<String> childComponentTypes = ['switch', 'cover', 'light', 'white', 'input', 'em', 'adc', 'temperature', 'humidity'] as Set

        deviceStatus.each { k, v ->
            String key = k.toString()
            String baseType = key.contains(':') ? key.split(':')[0] : key
            if (childComponentTypes.contains(baseType)) {
                components.add(key)
                if (componentPowerMonitoring[key]) {
                    pmComponents.add(key)
                }
            }
        }

        // Store component lists on parent device as data values
        String componentStr = components.join(',')
        String pmComponentStr = pmComponents.join(',')
        parentDevice.updateDataValue('components', componentStr)
        if (pmComponentStr) {
            parentDevice.updateDataValue('pmComponents', pmComponentStr)
        }

        // For EM parent drivers: set switchId for relay control (relay:0 is on the parent)
        if (parentDriverName.contains('EM Parent')) {
            parentDevice.updateDataValue('switchId', '0')
        }

        logInfo("  Components: ${componentStr}")
        if (pmComponentStr) {
            logInfo("  PM Components: ${pmComponentStr}")
        }

        // Store device config (no child DNIs — parent manages driver-level children)
        storeDeviceConfig(parentDni, deviceInfo, parentDriverName, true, [])

        // Initialize parent driver (triggers driver-level child creation)
        parentDevice.updateSetting('ipAddress', ipKey)
        parentDevice.initialize()

        // Install scripts and webhooks on the Shelly device
        reinitializeDevice(ipKey)

        logInfo("════════════════════════════════════════════════════════════")
        logInfo("  ✓ DEVICE CREATION COMPLETE: ${baseLabel}")
        logInfo("════════════════════════════════════════════════════════════")
        appendLog('info', "Created parent device ${baseLabel} with ${components.size()} components")

    } catch (Exception e) {
        logError("════════════════════════════════════════════════════════════")
        logError("  ✗ DEVICE CREATION FAILED: ${baseLabel} — ${e.message}")
        logError("════════════════════════════════════════════════════════════")
        appendLog('error', "Failed to create multi-component ${baseLabel}: ${e.message}")
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

// ═══════════════════════════════════════════════════════════════
// ║  Device Configuration Table                                 ║
// ╚═══════════════════════════════════════════════════════════════

/**
 * Orchestrator that combines CSS, Iconify script, SSR wrapper, and table markup
 * into a single HTML string for the device configuration table.
 *
 * @return Complete HTML string for the config table
 */
private String displayDeviceConfigTable() {
    ensureDeviceStatusCache()
    String tableMarkup = renderDeviceConfigTableMarkup()
    return loadConfigTableCSS() + loadConfigTableScript() +
        "<span class='ssr-app-state-${getAppIdHelper()}-configTable' id='config-table'>" +
        "<div id='config-table-wrapper'>${tableMarkup}</div></span>"
}

/**
 * Returns CSS styles for the device configuration table.
 * Uses MDL data table as base with status color classes.
 *
 * @return HTML style block
 */
@CompileStatic
private String loadConfigTableCSS() {
    return """<style>
    .mdl-data-table {
        width: 100%;
        border-collapse: collapse;
        border: 1px solid #E0E0E0;
        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        border-radius: 4px;
        overflow: hidden;
    }
    .mdl-data-table thead { background-color: #F5F5F5; }
    .mdl-data-table th {
        font-size: 14px !important;
        font-weight: 500;
        color: #424242;
        padding: 8px !important;
        text-align: center;
        border-bottom: 2px solid #E0E0E0;
        border-right: 1px solid #E0E0E0;
    }
    .mdl-data-table td {
        font-size: 14px !important;
        padding: 6px 4px !important;
        text-align: center;
        border-bottom: 1px solid #EEEEEE;
        border-right: 1px solid #EEEEEE;
    }
    .mdl-data-table tbody tr:hover { background-color: inherit !important; }
    .device-link a { color: #2196F3; text-decoration: none; font-weight: 500; }
    .device-link a:hover { text-decoration: underline; }
    .status-ok { color: #4CAF50; font-weight: 500; }
    .status-error { color: #F44336; font-weight: 500; }
    .status-na { color: #9E9E9E; }
    .status-stale { color: #FF9800; }
    .status-pending { color: #9E9E9E; font-style: italic; }
</style>"""
}

/**
 * Returns the Iconify script tag for icon support in the table.
 *
 * @return HTML script tag
 */
@CompileStatic
private String loadConfigTableScript() {
    return "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
}

/**
 * Renders the device configuration table markup from the status cache.
 * Merges discovered devices with created devices for a complete list.
 *
 * @return HTML table markup string
 */
private String renderDeviceConfigTableMarkup() {
    List<Map> deviceList = buildDeviceList()
    if (deviceList.size() == 0) {
        return "<p>No devices discovered yet. Discovery is running...</p>"
    }

    // Sort: created devices first, then by name
    deviceList.sort { Map a, Map b ->
        if (a.isCreated != b.isCreated) { return a.isCreated ? -1 : 1 }
        String nameA = ((a.hubDeviceName ?: a.shellyName) as String).toLowerCase()
        String nameB = ((b.hubDeviceName ?: b.shellyName) as String).toLowerCase()
        return nameA <=> nameB
    }

    StringBuilder str = new StringBuilder()
    str.append("<div style='overflow-x:auto'><table class='mdl-data-table'>")
    str.append("<thead><tr>")
    str.append("<th>Action</th>")
    str.append("<th>WiFi Device</th>")
    str.append("<th>Label</th>")
    str.append("<th>IP</th>")
    str.append("<th>Scripts Installed</th>")
    str.append("<th>Scripts Active</th>")
    str.append("<th>Webhooks Created</th>")
    str.append("<th>Webhooks Enabled</th>")
    str.append("<th>BLE GW</th>")
    str.append("<th>Reinit</th>")
    str.append("</tr></thead><tbody>")

    deviceList.each { Map entry -> str.append(buildDeviceRow(entry)) }

    str.append("</tbody></table></div>")
    return str.toString()
}

/**
 * Builds a unified list of devices from discovered Shellys and created child devices.
 * Reads status from the device status cache for each entry.
 *
 * @return List of device entry maps with status information
 */
private List<Map> buildDeviceList() {
    List<Map> result = []
    Map discoveredShellys = state.discoveredShellys ?: [:]
    Map cache = state.deviceStatusCache ?: [:]
    def childDevices = getChildDevices() ?: []

    // Build lookup of child devices by IP
    Map<String, Object> childByIp = [:]
    childDevices.each { dev ->
        String ip = dev.getDataValue('ipAddress')
        if (ip) { childByIp[ip] = dev }
    }

    // Build hub-wide DNI set for conflict detection (cached with 60s TTL)
    Long dniCacheAge = state.hubDnisCachedAt ? (now() - (state.hubDnisCachedAt as Long)) : Long.MAX_VALUE
    if (dniCacheAge > 60_000L || !state.hubDnisCached) {
        state.hubDnisCached = getHubDeviceDnis() as List<String>
        state.hubDnisCachedAt = now()
    }
    Set<String> hubDnis = (state.hubDnisCached ?: []).toSet()
    Set<String> ownChildDnis = childDevices.collect { it.deviceNetworkId.toString() }.toSet()

    // Build set of IPs already processed
    Set<String> processedIps = [] as Set

    // First pass: iterate discovered Shellys
    discoveredShellys.each { ipKey, info ->
        String ip = ipKey.toString()
        processedIps.add(ip)
        Map cached = cache[ip] as Map
        Map entry = cached ?: buildMinimalCacheEntry(ip, info as Map)

        // Check if a child device exists for this IP
        def dev = childByIp[ip]
        if (dev) {
            entry.isCreated = true
            entry.hubDeviceId = dev.id
            entry.hubDeviceDni = dev.deviceNetworkId
            entry.hubDeviceName = dev.displayName
            entry.hubDeviceLabel = dev.label ?: dev.displayName
            entry.hasDniConflict = false
        } else {
            // Device doesn't exist - clear stale cached data
            entry.isCreated = false
            entry.hubDeviceId = null
            entry.hubDeviceDni = null
            entry.hubDeviceName = null
            entry.hubDeviceLabel = null

            // Check if the would-be DNI is already in use by a non-child device
            String mac = (info as Map)?.mac?.toString() ?: ''
            String wouldBeDni = mac ?: "shelly-${ip.replaceAll('\\.', '-')}".toString()
            entry.hasDniConflict = hubDnis.contains(wouldBeDni) && !ownChildDnis.contains(wouldBeDni)
            entry.conflictDni = entry.hasDniConflict ? wouldBeDni : null
        }
        result.add(entry)
    }

    // Second pass: add child devices not found in discovery
    childDevices.each { dev ->
        String ip = dev.getDataValue('ipAddress')
        if (ip && !processedIps.contains(ip)) {
            processedIps.add(ip)
            Map cached = cache[ip] as Map
            if (cached) {
                cached.isCreated = true
                cached.hubDeviceId = dev.id
                cached.hubDeviceDni = dev.deviceNetworkId
                cached.hubDeviceName = dev.displayName
                cached.hubDeviceLabel = dev.label ?: dev.displayName
                result.add(cached)
            } else {
                result.add([
                    shellyName: dev.displayName,
                    ip: ip,
                    mac: dev.getDataValue('shellyMac') ?: '',
                    isCreated: true,
                    isGen1: isGen1Device(dev),
                    deviceGen: getDeviceGen(dev),
                    hubDeviceId: dev.id,
                    hubDeviceDni: dev.deviceNetworkId,
                    hubDeviceName: dev.displayName,
                    hubDeviceLabel: dev.label ?: dev.displayName,
                    isBatteryDevice: isBatteryPoweredDevice(dev),
                    isReachable: null,
                    requiredScriptCount: null,
                    installedScriptCount: null,
                    activeScriptCount: null,
                    requiredWebhookCount: null,
                    createdWebhookCount: null,
                    enabledWebhookCount: null,
                    lastRefreshed: null
                ])
            }
        }
    }

    return result
}

/**
 * Builds a minimal cache entry from discovery data without making any RPC calls.
 *
 * @param ip The device IP address
 * @param info The discovered device info map
 * @return Map with basic device information and null status fields
 */
@CompileStatic
private Map buildMinimalCacheEntry(String ip, Map info) {
    return [
        shellyName: (info?.name ?: "Shelly ${ip}") as String,
        ip: ip,
        mac: (info?.mac ?: '') as String,
        model: (info?.model ?: 'Unknown') as String,
        isCreated: false,
        isGen1: (info?.gen?.toString() == '1') as Boolean,
        deviceGen: (info?.gen?.toString() ?: '2') as String,
        hubDeviceDni: null,
        hubDeviceName: null,
        hubDeviceId: null,
        isBatteryDevice: (info?.isBatteryDevice ?: false) as Boolean,
        isReachable: null,
        requiredScriptCount: null,
        installedScriptCount: null,
        activeScriptCount: null,
        requiredWebhookCount: null,
        createdWebhookCount: null,
        enabledWebhookCount: null,
        lastRefreshed: null
    ]
}

/**
 * Builds a single table row for a device entry.
 *
 * @param entry Map containing device status information
 * @return HTML string for one table row
 */
private String buildDeviceRow(Map entry) {
    StringBuilder str = new StringBuilder()
    str.append("<tr>")

    String ip = entry.ip as String
    Boolean isCreated = entry.isCreated as Boolean
    Boolean isBattery = entry.isBatteryDevice as Boolean
    Long lastRefreshed = entry.lastRefreshed as Long
    Boolean isStale = lastRefreshed == null

    // Column 1: Action button (create, remove, or DNI conflict warning)
    if (isCreated) {
        String deleteIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px'></iconify-icon>"
        str.append("<td>${buttonLink("removeDev|${ip}", deleteIcon, '#F44336', '20px')}</td>")
    } else if (entry.hasDniConflict == true) {
        String conflictIcon = "<iconify-icon icon='material-symbols:cancel' style='font-size:20px'></iconify-icon>"
        str.append("<td title='DNI conflict — another device already uses this MAC'>${buttonLink("dniConflict|${ip}", conflictIcon, '#F44336', '20px')}</td>")
    } else {
        String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded' style='font-size:20px'></iconify-icon>"
        str.append("<td>${buttonLink("createDev|${ip}", addIcon, '#4CAF50', '20px')}</td>")
    }

    // Column 2: Device name (linked if created) with generation badge
    Boolean isGen1 = entry.isGen1 as Boolean
    String genLabel = isGen1 ? 'Gen 1' : "Gen${entry.deviceGen ?: '2'}+"
    String genColor = isGen1 ? '#FF9800' : '#1976D2'
    String genBadge = " <span style='font-size:10px;background:${genColor};color:white;padding:1px 4px;border-radius:3px;vertical-align:middle'>${genLabel}</span>"
    if (isCreated && entry.hubDeviceId) {
        String devLink = "<a href='/device/edit/${entry.hubDeviceId}' target='_blank' title='${entry.hubDeviceName}'>${entry.hubDeviceName}</a>"
        str.append("<td class='device-link'>${devLink}${genBadge}</td>")
    } else {
        str.append("<td>${entry.shellyName ?: 'Unknown'}${genBadge}</td>")
    }

    // Column 2: Device label (click to edit for created devices)
    if (isCreated && entry.hubDeviceId) {
        String currentLabel = (entry.hubDeviceLabel ?: entry.hubDeviceName ?: '') as String
        String editIcon = "<iconify-icon icon='material-symbols:edit' style='font-size:14px;vertical-align:middle;margin-left:4px'></iconify-icon>"
        String editBtn = buttonLink("editLabel|${ip}", "${currentLabel} ${editIcon}", "#424242", "14px")
        str.append("<td>${editBtn}</td>")
    } else {
        str.append("<td class='status-na'>&ndash;</td>")
    }

    // Column 3: IP
    str.append("<td>${ip}</td>")

    // Columns 4-7: Script and webhook status
    if (!isCreated) {
        // Not created — show dashes for all status columns
        str.append("<td class='status-na'>&ndash;</td>")
        str.append("<td class='status-na'>&ndash;</td>")
        str.append("<td class='status-na'>&ndash;</td>")
        str.append("<td class='status-na'>&ndash;</td>")
    } else if (isGen1 || isBattery) {
        // Gen 1 and battery devices have no scripts — show n/a
        str.append("<td class='status-na'>n/a</td>")
        str.append("<td class='status-na'>n/a</td>")
        str.append(buildWebhookCells(entry, isStale, ip))
    } else {
        str.append(buildScriptCells(entry, isStale, ip))
        str.append(buildWebhookCells(entry, isStale, ip))
    }

    // Column 9: BLE Gateway toggle
    String gen = (entry.isGen1 as Boolean) ? '1' : '2'
    if (isCreated) {
        str.append("<td>${renderBleGatewayCell(ip, gen, isBattery)}</td>")
    } else {
        str.append("<td class='status-na'>&ndash;</td>")
    }

    // Column 10: Reinit button
    if (isCreated) {
        String reinitIcon = "<iconify-icon icon='material-symbols:refresh' style='font-size:18px'></iconify-icon>"
        str.append("<td>${buttonLink("reinitDev|${ip}", reinitIcon, '#1A77C9', '18px')}</td>")
    } else {
        str.append("<td class='status-na'>&ndash;</td>")
    }

    str.append("</tr>")
    return str.toString()
}

/**
 * Builds the script installed and active table cells for a device row.
 *
 * @param entry The device status cache entry
 * @param isStale Whether the data is stale (never refreshed)
 * @param ip The device IP address
 * @return HTML string for two table cells (scripts installed, scripts active)
 */
@CompileStatic
private String buildScriptCells(Map entry, Boolean isStale, String ip) {
    StringBuilder str = new StringBuilder()
    Integer required = entry.requiredScriptCount as Integer
    Integer installed = entry.installedScriptCount as Integer
    Integer active = entry.activeScriptCount as Integer

    if (required == null || installed == null) {
        // Not yet checked
        String prefix = isStale ? '?' : ''
        String cssClass = isStale ? 'status-stale' : 'status-pending'
        str.append("<td class='${cssClass}'>${prefix}&ndash;</td>")
        str.append("<td class='${cssClass}'>${prefix}&ndash;</td>")
        return str.toString()
    }

    // Scripts installed
    if (installed >= required) {
        str.append("<td class='status-ok'>${installed}/${required}</td>")
    } else {
        String installBtn = buttonLink("installScripts|${ip}",
            "<iconify-icon icon='material-symbols:download' style='font-size:16px'></iconify-icon>", "#1A77C9", "16px")
        str.append("<td class='status-error'>${installed}/${required} ${installBtn}</td>")
    }

    // Scripts active
    if (active == null) { active = 0 }
    if (active >= required) {
        str.append("<td class='status-ok'>${active}/${required}</td>")
    } else {
        String enableBtn = buttonLink("enableScripts|${ip}",
            "<iconify-icon icon='material-symbols:play-arrow' style='font-size:16px'></iconify-icon>", "#1A77C9", "16px")
        str.append("<td class='status-error'>${active}/${required} ${enableBtn}</td>")
    }

    return str.toString()
}

/**
 * Builds the webhook created and enabled table cells for a device row.
 *
 * @param entry The device status cache entry
 * @param isStale Whether the data is stale (never refreshed)
 * @param ip The device IP address
 * @return HTML string for two table cells (webhooks created, webhooks enabled)
 */
@CompileStatic
private String buildWebhookCells(Map entry, Boolean isStale, String ip) {
    StringBuilder str = new StringBuilder()
    Boolean isGen1 = entry.isGen1 as Boolean
    Integer required = entry.requiredWebhookCount as Integer
    Integer created = entry.createdWebhookCount as Integer
    Integer enabled = entry.enabledWebhookCount as Integer

    // Gen 1 devices: show action URL status from pre-computed created/enabled counts
    if (isGen1) {
        if (required == null || required == 0) {
            str.append("<td class='status-na'>n/a</td>")
            str.append("<td class='status-na'>n/a</td>")
        } else {
            if (created == null) { created = 0 }
            if (enabled == null) { enabled = 0 }
            if (created >= required) {
                str.append("<td class='status-ok'>${created}/${required}</td>")
                str.append("<td class='status-ok'>${enabled}/${required}</td>")
            } else {
                String installBtn = buttonLink("installActionUrls|${ip}",
                    "<iconify-icon icon='material-symbols:download' style='font-size:16px'></iconify-icon>", "#1A77C9", "16px")
                str.append("<td class='status-error'>${created}/${required} ${installBtn}</td>")
                str.append("<td class='status-error'>${enabled}/${required}</td>")
            }
        }
        return str.toString()
    }

    if (required == null || created == null) {
        String prefix = isStale ? '?' : ''
        String cssClass = isStale ? 'status-stale' : 'status-pending'
        str.append("<td class='${cssClass}'>${prefix}&ndash;</td>")
        str.append("<td class='${cssClass}'>${prefix}&ndash;</td>")
        return str.toString()
    }

    // Webhooks created
    if (created >= required) {
        str.append("<td class='status-ok'>${created}/${required}</td>")
    } else {
        String installBtn = buttonLink("installWebhooks|${ip}",
            "<iconify-icon icon='material-symbols:download' style='font-size:16px'></iconify-icon>", "#1A77C9", "16px")
        str.append("<td class='status-error'>${created}/${required} ${installBtn}</td>")
    }

    // Webhooks enabled
    if (enabled == null) { enabled = 0 }
    if (enabled >= required) {
        str.append("<td class='status-ok'>${enabled}/${required}</td>")
    } else {
        str.append("<td class='status-error'>${enabled}/${required}</td>")
    }

    return str.toString()
}

/**
 * Creates a clickable inline button that triggers appButtonHandler on click.
 *
 * @param btnName The button name passed to appButtonHandler
 * @param linkText The display text or HTML for the button
 * @param color CSS color for the button text
 * @param font CSS font-size for the button text
 * @return HTML string for the inline button
 */
@CompileStatic
private String buttonLink(String btnName, String linkText, String color = "#1A77C9", String font = "15px") {
    "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div>" +
    "<div style='display:inline-block'><div class='submitOnChange' onclick='buttonClick(this)' " +
    "style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
    "<input type='hidden' name='settings[${btnName}]' value=''>"
}

/**
 * Ensures the device status cache exists. Does NOT make any RPC calls.
 * Initializes from discovered devices and child devices with null status fields.
 */
private void ensureDeviceStatusCache() {
    if (state.deviceStatusCache != null) { return }
    Map cache = [:]
    Map discoveredShellys = state.discoveredShellys ?: [:]

    discoveredShellys.each { ipKey, info ->
        String ip = ipKey.toString()
        cache[ip] = buildMinimalCacheEntry(ip, info as Map)
    }

    // Add child devices not in discovery
    def childDevices = getChildDevices() ?: []
    childDevices.each { dev ->
        String ip = dev.getDataValue('ipAddress')
        if (ip && !cache.containsKey(ip)) {
            cache[ip] = [
                shellyName: dev.displayName,
                ip: ip,
                mac: dev.getDataValue('shellyMac') ?: '',
                isCreated: true,
                hubDeviceDni: dev.deviceNetworkId,
                hubDeviceName: dev.displayName,
                hubDeviceId: dev.id,
                isBatteryDevice: isBatteryPoweredDevice(dev),
                isReachable: null,
                requiredScriptCount: null,
                installedScriptCount: null,
                activeScriptCount: null,
                requiredWebhookCount: null,
                createdWebhookCount: null,
                enabledWebhookCount: null,
                lastRefreshed: null
            ]
        }
    }

    state.deviceStatusCache = cache
}

/**
 * Returns the set of all known device IPs from discovery and child devices.
 *
 * @return Set of IP address strings
 */
private Set<String> getAllKnownDeviceIps() {
    Set<String> ips = [] as Set
    Map discoveredShellys = state.discoveredShellys ?: [:]
    discoveredShellys.each { ipKey, info -> ips.add(ipKey.toString()) }

    def childDevices = getChildDevices() ?: []
    childDevices.each { dev ->
        String ip = dev.getDataValue('ipAddress')
        if (ip) { ips.add(ip) }
    }
    return ips
}

/**
 * Queries a single device via RPC and updates the status cache.
 * Checks script installation/active counts and webhook created/enabled counts.
 *
 * @param ip The device IP address
 * @return The updated cache entry map
 */
private Map buildDeviceStatusCacheEntry(String ip) {
    Map discoveredShellys = state.discoveredShellys ?: [:]
    Map info = discoveredShellys[ip] as Map
    Map cache = state.deviceStatusCache ?: [:]
    Map entry = cache[ip] as Map ?: buildMinimalCacheEntry(ip, info ?: [:])

    // Update basic info from discovery
    if (info) {
        entry.shellyName = (info.name ?: "Shelly ${ip}") as String
        entry.mac = (info.mac ?: '') as String
        entry.model = (info.model ?: 'Unknown') as String
    }

    // Find the Hubitat child device for this IP
    def childDevice = findChildDeviceByIp(ip)
    entry.isCreated = (childDevice != null)
    if (childDevice) {
        entry.hubDeviceDni = childDevice.deviceNetworkId
        entry.hubDeviceName = childDevice.displayName
        entry.hubDeviceId = childDevice.id
        entry.isBatteryDevice = isBatteryPoweredDevice(childDevice)
    } else {
        // Device was deleted outside the app - clean up stale config entries
        cleanupStaleDeviceConfig(ip)
        entry.hubDeviceDni = null
        entry.hubDeviceName = null
        entry.hubDeviceId = null
        entry.isBatteryDevice = false
    }

    // Check reachability
    Boolean isBattery = entry.isBatteryDevice as Boolean
    Boolean isGen1 = isGen1DeviceByIp(ip)
    entry.isGen1 = isGen1
    entry.deviceGen = isGen1 ? '1' : (info?.gen?.toString() ?: (childDevice ? getDeviceGen(childDevice) : '2'))

    if (isBattery) {
        entry.isReachable = isGen1 ? isGen1DeviceReachable(ip) : isDeviceReachable(ip)
    } else if (isGen1) {
        // Gen 1 non-battery: check reachability via /shelly endpoint
        entry.isReachable = isGen1DeviceReachable(ip)
    } else {
        Map deviceStatus = queryDeviceStatus(ip)
        entry.isReachable = (deviceStatus != null)
    }

    Boolean reachable = entry.isReachable as Boolean

    // Script status — Gen 1 devices have no scripts
    if (isGen1) {
        entry.requiredScriptCount = -1
        entry.installedScriptCount = -1
        entry.activeScriptCount = -1
    } else if (childDevice && !isBattery && reachable) {
        Set<String> requiredScripts = getRequiredScriptsForDevice(childDevice)
        Set<String> requiredNames = requiredScripts.collect { stripJsExtension(it as String) } as Set<String>
        List<Map> installedScripts = listDeviceScripts(ip)

        entry.requiredScriptCount = requiredNames.size()
        if (installedScripts != null) {
            List<String> installedNames = installedScripts.collect { (it.name ?: '') as String }
            Integer matchCount = 0
            Integer activeCount = 0
            requiredNames.each { String reqName ->
                if (installedNames.contains(reqName)) { matchCount++ }
                Map script = installedScripts.find { (it.name ?: '') == reqName }
                if (script && (script.enable as Boolean) && (script.running as Boolean)) {
                    activeCount++
                }
            }
            entry.installedScriptCount = matchCount
            entry.activeScriptCount = activeCount
        }
    } else if (isBattery) {
        entry.requiredScriptCount = -1
        entry.installedScriptCount = -1
        entry.activeScriptCount = -1
    }

    // Webhook / action URL status (skip for uncreated devices)
    if (childDevice && isGen1) {
        // Gen 1: count required action URLs (uses state.discoveredShellys, no network call)
        List<Map> gen1Actions = getGen1RequiredActionUrls(ip)
        entry.requiredWebhookCount = gen1Actions.size()
        // Check gen1ActionUrlsInstalled flag from device config
        String dni = childDevice.deviceNetworkId
        Map config = state.deviceConfigs?.get(dni) as Map
        if (config?.gen1ActionUrlsInstalled == true) {
            entry.createdWebhookCount = gen1Actions.size()
            entry.enabledWebhookCount = gen1Actions.size()
        } else {
            entry.createdWebhookCount = 0
            entry.enabledWebhookCount = 0
        }
    } else if (childDevice && reachable) {
        // Gen 2/3: use RPC webhook list
        List<Map> requiredActions = getRequiredActionsForDevice(childDevice, reachable)
        entry.requiredWebhookCount = requiredActions.size()

        List<Map> installedHooks = listDeviceWebhooks(ip)
        if (installedHooks != null) {
            String hubIp = getLocationHelper()?.hub?.localIP ?: ''
            Integer createdCount = 0
            Integer enabledCount = 0
            requiredActions.each { Map action ->
                Map hook = installedHooks.find { Map h ->
                    h.event == action.event && (h.cid as Integer) == (action.cid as Integer)
                }
                if (hook) {
                    createdCount++
                    List<String> urls = hook.urls as List<String>
                    Boolean isEnabled = hook.enable as Boolean
                    if (urls?.any { it?.contains(hubIp) } && isEnabled) {
                        enabledCount++
                    }
                }
            }
            entry.createdWebhookCount = createdCount
            entry.enabledWebhookCount = enabledCount
        }
    }

    entry.lastRefreshed = now()

    // Persist to state
    cache[ip] = entry
    state.deviceStatusCache = cache
    return entry
}

/**
 * Finds a child device by its IP address data value.
 *
 * @param ip The IP address to search for
 * @return The matching child device, or null if not found
 */
private def findChildDeviceByIp(String ip) {
    def childDevices = getChildDevices() ?: []
    return childDevices.find { it.getDataValue('ipAddress') == ip }
}

/**
 * Queries the Hubitat hub for all existing device network IDs.
 * Uses the internal /hub2/devicesList endpoint with cookie authentication,
 * following the same pattern as {@link #getAppCodeId}.
 *
 * @return Set of all device network IDs currently on the hub, or empty set on failure
 */
private Set<String> getHubDeviceDnis() {
    Set<String> dnis = [] as Set
    try {
        String cookie = login()
        if (!cookie) { return dnis }
        httpGet([
            uri: 'http://127.0.0.1:8080',
            path: '/hub2/devicesList',
            headers: ['Cookie': cookie],
            timeout: 10
        ]) { resp ->
            if (resp?.status == 200 && resp.data) {
                resp.data.each { devId, devData ->
                    if (devData instanceof Map && devData.deviceNetworkId) {
                        dnis.add(devData.deviceNetworkId.toString())
                    }
                }
            }
        }
    } catch (Exception e) {
        logWarn("getHubDeviceDnis failed — conflict detection unavailable: ${e.message}")
    }
    return dnis
}

/**
 * Asynchronously refreshes the status cache for all known devices.
 * Called via runIn() after the Refresh All Status button is clicked.
 * Fires an SSR event after each device to provide incremental table updates.
 */
void refreshAllDeviceStatusAsync() {
    logInfo("Refreshing status for all devices...")
    appendLog('info', "Refreshing all device status...")
    Set<String> allIps = getAllKnownDeviceIps()
    allIps.each { String ip ->
        try {
            buildDeviceStatusCacheEntry(ip)
        } catch (Exception ex) {
            logError("Failed to refresh status for ${ip}: ${ex.message}")
        }
    }
    // Fire single SSR update after all devices are refreshed
    sendEvent(name: 'configTable', value: 'refreshAll')
    logInfo("Status refresh complete for ${allIps.size()} device(s)")
    appendLog('info', "Status refresh complete for ${allIps.size()} device(s)")
}

/**
 * Fully reinitializes a created device: re-downloads and updates all drivers
 * from GitHub, re-queries its physical state, pushes all required scripts,
 * starts them, installs all required webhooks, and calls the driver's
 * initialize() method.
 * Useful after firmware updates, factory resets, or architecture transitions.
 *
 * @param ipAddress The IP address of the Shelly device to reinitialize
 */
void reinitializeDevice(String ipAddress) {
    def childDevice = findChildDeviceByIp(ipAddress)
    if (!childDevice) {
        logError("reinitializeDevice: no child device found for ${ipAddress}")
        appendLog('error', "Reinit failed: no device for ${ipAddress}")
        return
    }

    logInfo("Reinitializing device at ${ipAddress}")
    appendLog('info', "Reinitializing device at ${ipAddress}...")

    // Step 1: Update parent and component drivers from GitHub
    updateDriversForDevice(childDevice)

    // Step 2: Re-query device info and status from physical device
    fetchAndStoreDeviceInfo(ipAddress)

    if (isGen1Device(childDevice)) {
        // Gen 1: no scripts, configure action URLs instead of webhooks
        logInfo("Gen 1 device — skipping scripts, configuring action URLs")
        installGen1ActionUrls(ipAddress)
    } else {
        // Gen 2/3: install scripts and webhooks
        // Step 3: Install any missing required scripts
        installRequiredScriptsForIp(ipAddress)

        // Step 4: Enable and start all required scripts
        enableAndStartRequiredScriptsForIp(ipAddress)

        // Step 5: Install/update all required webhooks (also removes obsolete scripts)
        installRequiredActionsForIp(ipAddress)
    }

    // Step 6: Call the driver's initialize() to reset driver state
    childDevice.initialize()

    logInfo("Reinitialization complete for ${ipAddress}")
    appendLog('info', "Reinitialization complete for ${ipAddress}")
}

/**
 * Downloads and installs the latest parent driver and component drivers
 * from GitHub for a specific device. Uses the stored device config to
 * determine which drivers are needed.
 *
 * @param childDevice The parent/standalone device to update drivers for
 */
private void updateDriversForDevice(def childDevice) {
    String dni = childDevice.deviceNetworkId
    Map config = state.deviceConfigs?.get(dni) as Map
    String version = getAppVersion()

    // Determine base driver name (strip version suffix)
    String driverNameRaw = config?.driverName ?: childDevice.typeName ?: ''
    String baseName = driverNameRaw.replaceAll(/\s+v\d+(\.\d+)*$/, '')

    if (!baseName) {
        logWarn("updateDriversForDevice: cannot determine driver name for ${dni}")
        return
    }

    // Update parent/standalone driver
    if (PREBUILT_DRIVERS.containsKey(baseName)) {
        logInfo("Updating parent driver '${baseName}' from GitHub...")
        appendLog('info', "Updating driver: ${baseName}")
        List<String> components = (config?.components ?: []) as List<String>
        Map<String, Boolean> pmMap = (config?.componentPowerMonitoring ?: [:]) as Map<String, Boolean>
        installPrebuiltDriver(baseName, components, pmMap, version)
    } else {
        logDebug("updateDriversForDevice: '${baseName}' not in PREBUILT_DRIVERS, skipping parent driver update")
    }

    // Update component (child) drivers if this is a parent-child device
    if (config?.isParentChild) {
        logInfo("Updating component drivers for ${childDevice.displayName}...")
        updateComponentDriversForDevice(config)
    }
}

/**
 * Downloads and installs the latest component drivers from GitHub for a
 * parent-child device. Always re-downloads to ensure latest code.
 * Infers power monitoring from the parent driver name when per-component
 * PM data is not available in the stored config.
 *
 * @param config The stored device config containing component information
 */
private void updateComponentDriversForDevice(Map config) {
    List<String> componentTypes = (config?.componentTypes ?: []) as List<String>

    // Infer PM from the parent driver name (e.g. "Shelly Autoconf 2x Switch PM Parent")
    String driverName = (config?.driverName ?: '') as String
    Boolean parentHasPM = driverName.contains(' PM ')

    Set<String> updatedDrivers = [] as Set

    componentTypes.each { String baseType ->
        if (!['switch', 'cover', 'light', 'input', 'adc', 'temperature', 'humidity'].contains(baseType)) { return }

        String compDriverName = getComponentDriverName(baseType, parentHasPM)
        if (!compDriverName || updatedDrivers.contains(compDriverName)) { return }

        updatedDrivers.add(compDriverName)

        String fileName = getComponentDriverFileName(baseType, parentHasPM)
        if (fileName) {
            logInfo("Updating component driver '${compDriverName}' from GitHub...")
            fetchAndInstallComponentDriver(fileName, compDriverName)
        }
    }
}

/**
 * Installs required scripts on a device identified by IP address.
 * Wrapper around the script installation logic that finds the device by IP
 * instead of relying on the selectedConfigDevice setting.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void installRequiredScriptsForIp(String ipAddress) {
    def device = findChildDeviceByIp(ipAddress)
    if (!device) {
        logError("installRequiredScriptsForIp: no child device found for ${ipAddress}")
        return
    }

    Set<String> requiredScripts = getRequiredScriptsForDevice(device)
    if (requiredScripts.size() == 0) {
        logInfo("No required scripts for device at ${ipAddress}")
        appendLog('info', "No required scripts for ${device.displayName}")
        return
    }

    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) {
        logError("Cannot read scripts from device at ${ipAddress}")
        appendLog('error', "Cannot read scripts from ${device.displayName}")
        return
    }
    String branch = GITHUB_BRANCH
    String baseUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/Scripts"
    String uri = "http://${ipAddress}/rpc"
    Integer installed = 0

    Integer updated = 0
    Boolean hasAuth = authIsEnabled() == true && getAuth().size() > 0

    requiredScripts.each { String scriptFile ->
        String scriptName = stripJsExtension(scriptFile)

        // Download latest script code from GitHub
        String scriptCode = downloadFile("${baseUrl}/${scriptFile}")
        if (!scriptCode) {
            logError("Failed to download ${scriptFile} from GitHub")
            appendLog('error', "Failed to download ${scriptFile}")
            return
        }

        // Check if script already exists on device
        Map existingScript = installedScripts.find { (it.name ?: '') == scriptName }

        try {
            Integer scriptId
            if (existingScript) {
                // Update existing script: stop → putCode → enable → start
                scriptId = existingScript.id as Integer
                logInfo("Updating script '${scriptName}' (id: ${scriptId}) on ${ipAddress}...")
                appendLog('info', "Updating ${scriptName} on ${device.displayName}...")

                LinkedHashMap stopCmd = scriptStopCommand(scriptId)
                if (hasAuth) { stopCmd.auth = getAuth() }
                postCommandSync(stopCmd, uri)
            } else {
                // Create new script
                logInfo("Installing script '${scriptName}' on ${ipAddress}...")
                appendLog('info', "Installing ${scriptName} on ${device.displayName}...")

                LinkedHashMap createCmd = scriptCreateCommand(scriptName)
                if (hasAuth) { createCmd.auth = getAuth() }
                LinkedHashMap createResult = postCommandSync(createCmd, uri)
                scriptId = createResult?.result?.id as Integer

                if (scriptId == null) {
                    logError("Failed to create script '${scriptName}' on device")
                    appendLog('error', "Failed to create ${scriptName}")
                    return
                }
            }

            uploadScriptInChunks(scriptId, scriptCode, uri, hasAuth)

            LinkedHashMap enableCmd = scriptEnableCommand(scriptId)
            if (hasAuth) { enableCmd.auth = getAuth() }
            postCommandSync(enableCmd, uri)

            LinkedHashMap startCmd = scriptStartCommand(scriptId)
            if (hasAuth) { startCmd.auth = getAuth() }
            postCommandSync(startCmd, uri)

            String action = existingScript ? 'Updated' : 'Installed'
            logInfo("Successfully ${action.toLowerCase()} and started '${scriptName}' (id: ${scriptId})")
            appendLog('info', "${action} ${scriptName} on ${device.displayName}")
            installed++
            if (existingScript) { updated++ }
        } catch (Exception ex) {
            String action = existingScript ? 'update' : 'install'
            logError("Failed to ${action} script '${scriptName}': ${ex.message}")
            appendLog('error', "Failed to ${action} ${scriptName}: ${ex.message}")
        }
    }

    Integer newlyInstalled = installed - updated
    logInfo("Script installation complete: ${newlyInstalled} installed, ${updated} updated on ${ipAddress}")
    appendLog('info', "Script installation complete: ${newlyInstalled} installed, ${updated} updated on ${device.displayName}")

    // Write Hubitat IP to KVS after installing/updating scripts
    if (installed > 0) {
        writeHubitatIpToKVS(ipAddress)
    }
}

/**
 * Enables and starts required scripts on a device identified by IP address.
 * Wrapper that finds the device by IP instead of relying on selectedConfigDevice.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void enableAndStartRequiredScriptsForIp(String ipAddress) {
    def device = findChildDeviceByIp(ipAddress)
    if (!device) {
        logError("enableAndStartRequiredScriptsForIp: no child device found for ${ipAddress}")
        return
    }

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

    // Write Hubitat IP to KVS after enabling/starting scripts
    if (fixed > 0) {
        writeHubitatIpToKVS(ipAddress)
    }
}

/**
 * Installs required webhook actions on a device identified by IP address.
 * Wrapper that finds the device by IP instead of relying on selectedConfigDevice.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void installRequiredActionsForIp(String ipAddress) {
    // Gen 1 devices use action URLs — delegate to Gen 1-specific function
    if (isGen1DeviceByIp(ipAddress)) {
        installGen1ActionUrls(ipAddress)
        return
    }
    def device = findChildDeviceByIp(ipAddress)
    if (!device) {
        logError("installRequiredActionsForIp: no child device found for ${ipAddress}")
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
    String baseUrl = "http://${hubIp}:39501"
    String uri = "http://${ipAddress}/rpc"
    Integer installed = 0

    requiredActions.each { Map action ->
        String event = action.event as String
        String name = action.name as String
        Integer cid = action.cid as Integer
        String urlParams = action.urlParams as String ?: ''

        // Build webhook URL with all data encoded as path segments (no query params)
        // Hubitat strips everything after ? on port 39501, so we use /key/value path pairs
        // Format: /<dst>/<cid>[/<key>/<value>...]
        String hookUrl = "${baseUrl}/${action.dst}/${cid}"
        if (urlParams) { hookUrl += "/${urlParams}" }

        Map existing = existingHooks.find { Map h ->
            h.event == event && (h.cid as Integer) == cid
        }

        if (existing) {
            List<String> urls = existing.urls as List<String>
            Boolean isEnabled = existing.enable as Boolean
            String existingName = existing.name as String
            Boolean nameNeedsUpdate = !existingName?.startsWith('hubitat_sdm_')
            logDebug("Existing webhook check: name='${existingName}', urls=${urls}, enabled=${isEnabled}, nameNeedsUpdate=${nameNeedsUpdate}")
            if (urls?.any { it?.contains(hubIp) } && isEnabled && !nameNeedsUpdate) {
                // Check if URL needs updating (must exactly match the expected hookUrl)
                Boolean urlNeedsUpdate = !urls?.any { String u -> u == hookUrl }
                logDebug("URL format check: urlNeedsUpdate=${urlNeedsUpdate}, target hookUrl=${hookUrl}")
                if (!urlNeedsUpdate) {
                    logDebug("Webhook '${name}' already configured for ${event} cid=${cid}")
                    return
                }
            }
            logInfo("Updating webhook '${name}' for ${event} cid=${cid} -> ${hookUrl}")
            LinkedHashMap updateCmd = webhookUpdateCommand(existing.id as Integer, name, [hookUrl])
            if (authIsEnabled() == true && getAuth().size() > 0) { updateCmd.auth = getAuth() }
            postCommandSync(updateCmd, uri)
            installed++
            return
        }

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

    // Clean up obsolete webhooks that shouldn't exist
    removeObsoleteWebhooks(ipAddress, device, requiredActions)

    // Clean up obsolete scripts that are now replaced by webhooks
    removeObsoleteScripts(ipAddress, device)
}

/**
 * Clears all existing action URLs on a Gen 1 Shelly device before reinstalling.
 * This prevents stale URLs from previous integrations or manual configurations from
 * persisting alongside newly installed ones. Clears URLs across two categories:
 * <ol>
 *   <li><b>Component settings</b> — relay, roller, light, and input URL params
 *       (e.g., {@code out_on_url}, {@code roller_open_url})</li>
 *   <li><b>Actions endpoint</b> — sensor/button action arrays via {@code /settings/actions}.
 *       URL clearing and disabling are sent as separate requests to avoid firmware issues
 *       where a combined request skips the URL clear.</li>
 * </ol>
 *
 * @param ipAddress The IP address of the Gen 1 Shelly device
 */
private void clearGen1ActionUrls(String ipAddress) {
    Map deviceInfo = state.discoveredShellys?.get(ipAddress)
    if (!deviceInfo) {
        logError("clearGen1ActionUrls: no device info found for ${ipAddress}")
        return
    }

    String typeCode = deviceInfo.gen1Type?.toString() ?: ''
    Map deviceStatus = deviceInfo.deviceStatus as Map ?: [:]
    Integer cleared = 0

    // ── Step 1: Clear component-level URL params ──
    // Relay components: clear all known relay URL params
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('switch:')) {
            Integer cid = key.split(':')[1] as Integer
            Map result = sendGen1Setting(ipAddress, "settings/relay/${cid}", [
                out_on_url: '', out_off_url: '',
                btn_on_url: '', btn_off_url: '',
                shortpush_url: '', longpush_url: '',
                over_power_url: ''
            ])
            if (result != null) { cleared++ }
        }
    }

    // Roller/cover components
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('cover:')) {
            Integer cid = key.split(':')[1] as Integer
            Map result = sendGen1Setting(ipAddress, "settings/roller/${cid}", [
                roller_open_url: '', roller_close_url: '', roller_stop_url: ''
            ])
            if (result != null) { cleared++ }
        }
    }

    // Light/dimmer components (RGBW2 uses /settings/color/ instead of /settings/light/)
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('light:')) {
            Integer cid = key.split(':')[1] as Integer
            String settingsPath = (typeCode == 'SHRGBW2') ? "settings/color/${cid}" : "settings/light/${cid}"
            Map result = sendGen1Setting(ipAddress, settingsPath, [
                out_on_url: '', out_off_url: '',
                shortpush_url: '', longpush_url: ''
            ])
            if (result != null) { cleared++ }
        }
    }

    // White channel components (RGBW2 white mode)
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('white:')) {
            Integer cid = key.split(':')[1] as Integer
            Map result = sendGen1Setting(ipAddress, "settings/white/${cid}", [
                out_on_url: '', out_off_url: ''
            ])
            if (result != null) { cleared++ }
        }
    }

    // Input components (Shelly i3)
    if (typeCode == 'SHIX3-1') {
        deviceStatus.each { k, v ->
            String key = k.toString()
            if (key.startsWith('input:')) {
                Integer cid = key.split(':')[1] as Integer
                Map result = sendGen1Setting(ipAddress, "settings/input/${cid}", [
                    shortpush_url: '', longpush_url: '', double_shortpush_url: ''
                ])
                if (result != null) { cleared++ }
            }
        }
    }

    // ── Step 2: Clear /settings/actions entries (sensors, buttons) ──
    // Response format: {"actions": {"open_url": [{"index": 0, "urls": [...], ...}], ...}}
    // Split into two requests per action: clear URLs first, then disable.
    // Sending both in one request can cause firmware to skip the URL clear.
    Map actionsResponse = sendGen1Get(ipAddress, 'settings/actions')
    Map actionsMap = actionsResponse?.get('actions') as Map
    if (actionsMap) {
        actionsMap.each { actionName, actionData ->
            if (actionData instanceof List) {
                ((List) actionData).eachWithIndex { entry, idx ->
                    if (entry instanceof Map) {
                        String idxStr = idx.toString()
                        String name = actionName.toString()
                        // Step 2a: Clear URLs (separate request)
                        sendGen1Setting(ipAddress, 'settings/actions', [
                            index: idxStr, name: name, 'urls[]': ''
                        ])
                        // Step 2b: Disable action (separate request)
                        sendGen1Setting(ipAddress, 'settings/actions', [
                            index: idxStr, name: name, enabled: 'false'
                        ])
                        cleared++
                    }
                }
            }
        }
    }

    // ── Step 3: Clear report_url on /settings (set directly, not via /settings/actions) ──
    if (typeCode == 'SHHT-1') {
        Map result = sendGen1Setting(ipAddress, 'settings', [report_url: ''])
        if (result != null) { cleared++ }
    }

    def childDevice = findChildDeviceByIp(ipAddress)
    String deviceName = childDevice?.displayName ?: ipAddress
    logInfo("Cleared ${cleared} Gen 1 action URL group(s) on ${deviceName}")
    appendLog('info', "Cleared ${cleared} Gen 1 action URL group(s) on ${deviceName}")
}

/**
 * Configures Gen 1 action URLs on a device to point to the Hubitat hub for event delivery.
 * Gen 1 devices use HTTP GET "action URLs" instead of Gen 2/3 webhooks.
 * Each action URL fires a GET request to the hub when the corresponding event occurs.
 * <p>
 * Clears all existing action URLs first via {@link #clearGen1ActionUrls(String)} to prevent
 * stale URLs from previous integrations persisting alongside new ones.
 * <p>
 * For relay/roller/light devices, action URLs are set via component settings endpoints
 * (e.g., {@code /settings/relay/0?out_on_url=http://...}).
 * For sensor devices, action URLs are set via the {@code /settings/actions} endpoint
 * or direct properties on {@code /settings}.
 *
 * @param ipAddress The IP address of the Gen 1 Shelly device
 */
private void installGen1ActionUrls(String ipAddress) {
    Map deviceInfo = state.discoveredShellys?.get(ipAddress)
    if (!deviceInfo) {
        logError("installGen1ActionUrls: no device info found for ${ipAddress}")
        return
    }

    String hubIp = location.hub.localIP
    String baseCallbackUrl = "http://${hubIp}:39501"

    List<Map> requiredActions = getGen1RequiredActionUrls(ipAddress)
    if (!requiredActions) {
        logDebug("installGen1ActionUrls: no action URLs required for ${ipAddress}")
        return
    }

    // Clear all existing action URLs before reinstalling to remove stale entries
    clearGen1ActionUrls(ipAddress)

    def childDevice = findChildDeviceByIp(ipAddress)
    Integer installed = 0
    Integer failed = 0

    requiredActions.each { Map action ->
        String callbackUrl = "${baseCallbackUrl}/${action.dst}/${action.cid}"
        Map result = null

        if (action.configType == 'actions') {
            // Sensor/input action URLs via /settings/actions endpoint (array-based)
            result = sendGen1Setting(ipAddress, 'settings/actions', [
                index: (action.actionIndex ?: 0).toString(),
                name: action.param.toString(),
                enabled: 'true',
                'urls[]': callbackUrl
            ])
        } else {
            // Component action URLs: set directly on settings endpoint
            result = sendGen1Setting(ipAddress, action.endpoint.toString(),
                [(action.param.toString()): callbackUrl])
        }

        if (result != null) {
            installed++
            logDebug("Configured ${action.name} on ${ipAddress}: ${callbackUrl}")
        } else {
            failed++
            logWarn("Failed to configure ${action.name} on ${ipAddress}")
        }
    }

    String deviceName = childDevice?.displayName ?: ipAddress
    logInfo("Gen 1 action URL provisioning: ${installed}/${requiredActions.size()} configured on ${deviceName}" +
            (failed > 0 ? " (${failed} failed)" : ''))
    appendLog('info', "Gen 1 action URLs: ${installed}/${requiredActions.size()} on ${deviceName}")

    // Track provisioning state for battery devices
    if (childDevice) {
        String dni = childDevice.deviceNetworkId
        Map config = state.deviceConfigs?.get(dni) as Map
        if (config) {
            config.gen1ActionUrlsInstalled = (failed == 0)
            state.deviceConfigs[dni] = config
        }
    }
}

/**
 * Attempts to install Gen 1 action URLs on a battery device when it wakes up.
 * Battery devices are normally asleep, so action URLs can only be configured during
 * the brief wake-up window when the device fires a report/event callback.
 * Checks if action URLs were previously installed; if not, attempts installation now.
 *
 * @param ipAddress The IP address of the Gen 1 battery device
 */
private void attemptGen1ActionUrlInstallOnWake(String ipAddress) {
    def childDevice = findChildDeviceByIp(ipAddress)
    if (!childDevice) { return }

    String dni = childDevice.deviceNetworkId
    Map config = state.deviceConfigs?.get(dni) as Map
    if (!config) { return }

    // Skip if action URLs were already successfully installed
    if (config.gen1ActionUrlsInstalled == true) { return }

    logInfo("Battery device ${childDevice.displayName} is awake — attempting action URL installation")
    installGen1ActionUrls(ipAddress)
}

/**
 * Returns the list of required Gen 1 action URLs for a device based on its type and components.
 * Each action URL definition includes:
 * <ul>
 *   <li>{@code endpoint} — The settings endpoint path (e.g., {@code settings/relay/0})</li>
 *   <li>{@code param} — The parameter name (e.g., {@code out_on_url})</li>
 *   <li>{@code dst} — Destination identifier in the webhook URL path</li>
 *   <li>{@code cid} — Component ID</li>
 *   <li>{@code name} — Human-readable name for logging</li>
 *   <li>{@code configType} — {@code 'component'} for direct settings or {@code 'actions'} for /settings/actions</li>
 * </ul>
 *
 * @param ipAddress The IP address of the Gen 1 Shelly device
 * @return List of action URL definition maps, or empty list if device info not found
 */
private List<Map> getGen1RequiredActionUrls(String ipAddress) {
    Map deviceInfo = state.discoveredShellys?.get(ipAddress)
    if (!deviceInfo) { return [] }

    String typeCode = deviceInfo.gen1Type?.toString() ?: ''
    Map deviceStatus = deviceInfo.deviceStatus as Map ?: [:]
    List<Map> actions = []

    // Relay switch action URLs (component settings endpoint)
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('switch:')) {
            Integer cid = key.split(':')[1] as Integer
            actions.add([endpoint: "settings/relay/${cid}", param: 'out_on_url',
                dst: 'switch_on', cid: cid, name: "Relay ${cid} On", configType: 'component'])
            actions.add([endpoint: "settings/relay/${cid}", param: 'out_off_url',
                dst: 'switch_off', cid: cid, name: "Relay ${cid} Off", configType: 'component'])
            actions.add([endpoint: "settings/relay/${cid}", param: 'over_power_url',
                dst: 'over_power', cid: cid, name: "Relay ${cid} Over Power", configType: 'component'])
        }
    }

    // Cover/roller action URLs (component settings endpoint)
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('cover:')) {
            Integer cid = key.split(':')[1] as Integer
            actions.add([endpoint: "settings/roller/${cid}", param: 'roller_open_url',
                dst: 'cover_open', cid: cid, name: "Roller ${cid} Open", configType: 'component'])
            actions.add([endpoint: "settings/roller/${cid}", param: 'roller_close_url',
                dst: 'cover_close', cid: cid, name: "Roller ${cid} Close", configType: 'component'])
            actions.add([endpoint: "settings/roller/${cid}", param: 'roller_stop_url',
                dst: 'cover_stop', cid: cid, name: "Roller ${cid} Stop", configType: 'component'])
        }
    }

    // Light/dimmer action URLs (component settings endpoint)
    // RGBW2 in color mode uses /settings/color/{cid} instead of /settings/light/{cid}
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('light:')) {
            Integer cid = key.split(':')[1] as Integer
            String settingsPath = (typeCode == 'SHRGBW2') ? "settings/color/${cid}" : "settings/light/${cid}"
            actions.add([endpoint: settingsPath, param: 'out_on_url',
                dst: 'light_on', cid: cid, name: "Light ${cid} On", configType: 'component'])
            actions.add([endpoint: settingsPath, param: 'out_off_url',
                dst: 'light_off', cid: cid, name: "Light ${cid} Off", configType: 'component'])
        }
    }

    // White channel action URLs (RGBW2 in white mode)
    deviceStatus.each { k, v ->
        String key = k.toString()
        if (key.startsWith('white:')) {
            Integer cid = key.split(':')[1] as Integer
            actions.add([endpoint: "settings/white/${cid}", param: 'out_on_url',
                dst: 'white_on', cid: cid, name: "White ${cid} On", configType: 'component'])
            actions.add([endpoint: "settings/white/${cid}", param: 'out_off_url',
                dst: 'white_off', cid: cid, name: "White ${cid} Off", configType: 'component'])
        }
    }

    // Input action URLs for switch devices with inputs (SHSW-1, SHSW-PM, etc.)
    // For switch devices, input push URLs are configured on the relay endpoint (settings/relay/{cid})
    Boolean hasSwitches = deviceStatus.keySet().any { Object k -> k.toString().startsWith('switch:') }
    if (hasSwitches) {
        deviceStatus.each { k, v ->
            String key = k.toString()
            if (key.startsWith('input:')) {
                Integer cid = key.split(':')[1] as Integer
                actions.add([endpoint: "settings/relay/${cid}", param: 'shortpush_url',
                    dst: 'input_short', cid: cid, name: "Input ${cid} Short Push", configType: 'component'])
                actions.add([endpoint: "settings/relay/${cid}", param: 'longpush_url',
                    dst: 'input_long', cid: cid, name: "Input ${cid} Long Push", configType: 'component'])
            }
        }
    }

    // Shelly i3 input action URLs (pure input device — uses settings/input endpoint)
    if (typeCode == 'SHIX3-1') {
        deviceStatus.each { k, v ->
            String key = k.toString()
            if (key.startsWith('input:')) {
                Integer cid = key.split(':')[1] as Integer
                actions.add([endpoint: "settings/input/${cid}", param: 'shortpush_url',
                    dst: 'input_short', cid: cid, name: "Input ${cid} Short Push", configType: 'component'])
                actions.add([endpoint: "settings/input/${cid}", param: 'longpush_url',
                    dst: 'input_long', cid: cid, name: "Input ${cid} Long Push", configType: 'component'])
                actions.add([endpoint: "settings/input/${cid}", param: 'double_shortpush_url',
                    dst: 'input_double', cid: cid, name: "Input ${cid} Double Push", configType: 'component'])
            }
        }
    }

    // Sensor action URLs (battery devices, mains-powered sensors, and Uni)
    if (GEN1_BATTERY_TYPES.contains(typeCode) || typeCode == 'SHGS-1' || typeCode == 'SHUNI-1') {
        actions.addAll(getGen1SensorActionUrls(typeCode))
    }

    return actions
}

/**
 * Returns sensor-specific action URL definitions for Gen 1 battery-powered devices.
 * Sensors use the /settings/actions endpoint for event-driven URLs.
 * <p>
 * Note: {@code report_url} is generally unusable because it passes URL parameters
 * that Hubitat silently drops. However, for H&T (SHHT-1), {@code report_url} is used
 * as a <b>wake-up trigger only</b> — the query-parameter data is ignored, but the bare
 * GET request arrives at Hubitat, allowing the app to immediately poll {@code /status}
 * for the actual sensor data while the device is briefly awake.
 *
 * @param typeCode The Gen 1 device type code (e.g., {@code SHHT-1}, {@code SHWT-1})
 * @return List of action URL definition maps for the sensor type
 */
private List<Map> getGen1SensorActionUrls(String typeCode) {
    List<Map> actions = []

    switch (typeCode) {
        case 'SHHT-1':  // H&T temperature/humidity sensor
            // report_url: set on /settings directly (not /settings/actions).
            // Query params are stripped by Hubitat, but the bare GET arrives as
            // dst=sensor_report, triggering an immediate /status poll while the device is awake.
            actions.add([endpoint: 'settings', param: 'report_url',
                dst: 'sensor_report', cid: 0, name: 'Sensor Report', configType: 'component'])
            // Threshold URLs via /settings/actions (array-based)
            actions.add([endpoint: 'settings/actions', param: 'over_temp_url',
                dst: 'temp_over', cid: 0, name: 'Temperature Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_temp_url',
                dst: 'temp_under', cid: 0, name: 'Temperature Under', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_hum_url',
                dst: 'hum_over', cid: 0, name: 'Humidity Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_hum_url',
                dst: 'hum_under', cid: 0, name: 'Humidity Under', configType: 'actions', actionIndex: 0])
            break

        case 'SHWT-1':  // Flood sensor — actions array
            actions.add([endpoint: 'settings/actions', param: 'flood_detected_url',
                dst: 'flood_detected', cid: 0, name: 'Flood Detected', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'flood_gone_url',
                dst: 'flood_gone', cid: 0, name: 'Flood Gone', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_temp_url',
                dst: 'temp_over', cid: 0, name: 'Temperature Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_temp_url',
                dst: 'temp_under', cid: 0, name: 'Temperature Under', configType: 'actions', actionIndex: 0])
            break

        case 'SHDW-1':  // Door/Window v1 — actions array
            actions.add([endpoint: 'settings/actions', param: 'open_url',
                dst: 'contact_open', cid: 0, name: 'Contact Open', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'close_url',
                dst: 'contact_close', cid: 0, name: 'Contact Close', configType: 'actions', actionIndex: 0])
            break

        case 'SHDW-2':  // Door/Window v2 — actions array, plus vibration and lux thresholds
            actions.add([endpoint: 'settings/actions', param: 'open_url',
                dst: 'contact_open', cid: 0, name: 'Contact Open', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'close_url',
                dst: 'contact_close', cid: 0, name: 'Contact Close', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'vibration_url',
                dst: 'vibration', cid: 0, name: 'Vibration', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'dark_url',
                dst: 'lux_dark', cid: 0, name: 'Dark', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'twilight_url',
                dst: 'lux_twilight', cid: 0, name: 'Twilight', configType: 'actions', actionIndex: 0])
            break

        case 'SHBTN-1':  // Button v1
        case 'SHBTN-2':  // Button v2
            actions.add([endpoint: 'settings/actions', param: 'shortpush_url',
                dst: 'input_short', cid: 0, name: 'Short Push', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'longpush_url',
                dst: 'input_long', cid: 0, name: 'Long Push', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'double_shortpush_url',
                dst: 'input_double', cid: 0, name: 'Double Push', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'triple_shortpush_url',
                dst: 'input_triple', cid: 0, name: 'Triple Push', configType: 'actions', actionIndex: 0])
            break

        case 'SHMOS-01':  // Motion v1
        case 'SHMOS-02':  // Motion v2
            actions.add([endpoint: 'settings/actions', param: 'motion_on',
                dst: 'motion_on', cid: 0, name: 'Motion On', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'motion_off',
                dst: 'motion_off', cid: 0, name: 'Motion Off', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'tamper_alarm_on',
                dst: 'tamper_alarm_on', cid: 0, name: 'Tamper On', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'tamper_alarm_off',
                dst: 'tamper_alarm_off', cid: 0, name: 'Tamper Off', configType: 'actions', actionIndex: 0])
            break

        case 'SHSN-1':  // Shelly Sense — motion + threshold URLs under /settings/actions
            actions.add([endpoint: 'settings/actions', param: 'motion_on',
                dst: 'motion_on', cid: 0, name: 'Motion On', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'motion_off',
                dst: 'motion_off', cid: 0, name: 'Motion Off', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_temp_url',
                dst: 'temp_over', cid: 0, name: 'Temperature Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_temp_url',
                dst: 'temp_under', cid: 0, name: 'Temperature Under', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_hum_url',
                dst: 'hum_over', cid: 0, name: 'Humidity Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_hum_url',
                dst: 'hum_under', cid: 0, name: 'Humidity Under', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_lux_url',
                dst: 'lux_over', cid: 0, name: 'Lux Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_lux_url',
                dst: 'lux_under', cid: 0, name: 'Lux Under', configType: 'actions', actionIndex: 0])
            break

        case 'SHTRV-01':  // TRV — valve open/close
            actions.add([endpoint: 'settings/actions', param: 'valve_open',
                dst: 'valve_open', cid: 0, name: 'Valve Open', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'valve_close',
                dst: 'valve_close', cid: 0, name: 'Valve Close', configType: 'actions', actionIndex: 0])
            break

        case 'SHSM-01':  // Smoke sensor — all URLs under /settings/actions
            // NOTE: report_url assumed to be in /settings/actions (needs hardware verification;
            // H&T uses /settings directly for report_url, but Smoke may differ)
            actions.add([endpoint: 'settings/actions', param: 'report_url',
                dst: 'sensor_report', cid: 0, name: 'Sensor Report', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'smoke_detected_url',
                dst: 'smoke_detected', cid: 0, name: 'Smoke Detected', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'smoke_cleared_url',
                dst: 'smoke_cleared', cid: 0, name: 'Smoke Cleared', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'over_temp_url',
                dst: 'temp_over', cid: 0, name: 'Temperature Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'under_temp_url',
                dst: 'temp_under', cid: 0, name: 'Temperature Under', configType: 'actions', actionIndex: 0])
            break

        case 'SHUNI-1':  // Shelly Uni — ADC + external sensor threshold URLs
            actions.add([endpoint: 'settings/actions', param: 'adc_over_url',
                dst: 'adc_over', cid: 0, name: 'ADC Over Threshold', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'adc_under_url',
                dst: 'adc_under', cid: 0, name: 'ADC Under Threshold', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'ext_temp_over_url',
                dst: 'ext_temp_over', cid: 0, name: 'Ext Temp Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'ext_temp_under_url',
                dst: 'ext_temp_under', cid: 0, name: 'Ext Temp Under', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'ext_hum_over_url',
                dst: 'ext_hum_over', cid: 0, name: 'Ext Humidity Over', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'ext_hum_under_url',
                dst: 'ext_hum_under', cid: 0, name: 'Ext Humidity Under', configType: 'actions', actionIndex: 0])
            break

        case 'SHGS-1':  // Gas sensor — alarm URLs under /settings/actions
            actions.add([endpoint: 'settings/actions', param: 'alarm_mild_url',
                dst: 'gas_alarm_mild', cid: 0, name: 'Gas Alarm Mild', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'alarm_heavy_url',
                dst: 'gas_alarm_heavy', cid: 0, name: 'Gas Alarm Heavy', configType: 'actions', actionIndex: 0])
            actions.add([endpoint: 'settings/actions', param: 'alarm_off_url',
                dst: 'gas_alarm_off', cid: 0, name: 'Gas Alarm Off', configType: 'actions', actionIndex: 0])
            break

        default:
            logDebug("getGen1SensorActionUrls: no action URL definitions for sensor type ${typeCode}")
    }

    return actions
}

/**
 * Removes webhooks that shouldn't exist on the device. Only removes webhooks
 * managed by Hubitat ('hubitat_sdm_' or legacy 'hubitat.' prefix) that are
 * not in the required actions list.
 * This cleans up webhooks from old configurations (e.g., power monitoring
 * webhooks when the device now uses powermonitoring.js script).
 *
 * @param ipAddress The IP address of the Shelly device
 * @param device The Hubitat device object for logging
 * @param requiredActions List of webhook actions that should exist
 */
private void removeObsoleteWebhooks(String ipAddress, def device, List<Map> requiredActions) {
    List<Map> installedHooks = listDeviceWebhooks(ipAddress)
    if (installedHooks == null) { return }

    String uri = "http://${ipAddress}/rpc"
    Integer removed = 0

    // Build set of required webhook identifiers (event + cid)
    Set<String> requiredHookIds = [] as Set
    requiredActions.each { Map action ->
        String hookId = "${action.event}:${action.cid}"
        requiredHookIds.add(hookId)
    }

    installedHooks.each { Map hook ->
        String name = hook.name as String
        String event = hook.event as String
        Integer cid = hook.cid as Integer
        Integer hookId = hook.id as Integer

        // Only remove webhooks managed by Hubitat (hubitat_sdm_ or legacy hubitat. prefix)
        if (!name?.startsWith('hubitat_sdm_') && !name?.startsWith('hubitat.')) { return }

        // Check if this webhook should exist
        String identifier = "${event}:${cid}"
        if (!requiredHookIds.contains(identifier)) {
            logInfo("Removing obsolete webhook '${name}' (${event} cid=${cid}) from ${device.displayName}")
            appendLog('info', "Removing obsolete webhook ${name} from ${device.displayName}")
            try {
                LinkedHashMap deleteCmd = webhookDeleteCommand(hookId)
                if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
                postCommandSync(deleteCmd, uri)
                removed++
            } catch (Exception ex) {
                logDebug("Could not remove obsolete webhook '${name}': ${ex.message}")
            }
        }
    }

    if (removed > 0) {
        logInfo("Removed ${removed} obsolete webhook(s) from ${ipAddress}")
    }
}

/**
 * Removes scripts that have been superseded by webhook-based notifications.
 * Switches and covers now use webhooks with URL token replacement instead
 * of custom scripts. This cleans up old switchstatus/coverstatus scripts.
 *
 * @param ipAddress The IP address of the Shelly device
 * @param device The Hubitat device object for logging
 */
private void removeObsoleteScripts(String ipAddress, def device) {
    List<String> obsoleteScriptNames = ['switchstatus', 'coverstatus']

    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) { return }

    String uri = "http://${ipAddress}/rpc"

    installedScripts.each { Map script ->
        String name = script.name as String
        Integer scriptId = script.id as Integer
        if (obsoleteScriptNames.contains(name) && scriptId != null) {
            logInfo("Removing obsolete script '${name}' (id: ${scriptId}) from ${device.displayName} — replaced by webhooks")
            appendLog('info', "Removing obsolete ${name} from ${device.displayName}")
            try {
                LinkedHashMap deleteCmd = scriptDeleteCommand(scriptId)
                if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
                postCommandSync(deleteCmd, uri)
            } catch (Exception ex) {
                logDebug("Could not remove obsolete script '${name}': ${ex.message}")
            }
        }
    }

    // Check if all managed scripts are now gone — if so, remove hubitat_sdm_ip from KVS
    checkAndRemoveKvsIfNoScripts(ipAddress)
}

/**
 * Performs comprehensive cleanup of all Hubitat-managed resources on a Shelly device.
 * Removes all webhooks, scripts, and KVS entries that were created by this app.
 *
 * @param ipAddress The IP address of the Shelly device
 * @param deviceName The device name for logging
 */
private void cleanupShellyDevice(String ipAddress, String deviceName) {
    logInfo("Starting comprehensive cleanup of ${deviceName} at ${ipAddress}")
    appendLog('info', "Cleaning up ${deviceName}")

    String uri = "http://${ipAddress}/rpc"
    Integer totalRemoved = 0

    // Step 1: Remove all Hubitat-managed webhooks
    try {
        List<Map> installedHooks = listDeviceWebhooks(ipAddress)
        if (installedHooks) {
            Integer webhooksRemoved = 0
            installedHooks.each { Map hook ->
                String hookName = hook.name as String
                Integer hookId = hook.id as Integer
                if ((hookName?.startsWith('hubitat_sdm_') || hookName?.startsWith('hubitat.')) && hookId != null) {
                    try {
                        LinkedHashMap deleteCmd = webhookDeleteCommand(hookId)
                        if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
                        postCommandSync(deleteCmd, uri)
                        webhooksRemoved++
                        logDebug("Removed webhook '${hookName}' (id: ${hookId})")
                    } catch (Exception ex) {
                        logDebug("Could not remove webhook '${hookName}': ${ex.message}")
                    }
                }
            }
            if (webhooksRemoved > 0) {
                logInfo("Removed ${webhooksRemoved} webhook(s) from ${deviceName}")
                totalRemoved += webhooksRemoved
            }
        }
    } catch (Exception ex) {
        logDebug("Could not list webhooks for cleanup: ${ex.message}")
    }

    // Step 2: Remove all managed scripts
    try {
        List<Map> installedScripts = listDeviceScripts(ipAddress)
        if (installedScripts) {
            Integer scriptsRemoved = 0
            installedScripts.each { Map script ->
                String scriptName = script.name as String
                Integer scriptId = script.id as Integer
                if (MANAGED_SCRIPT_NAMES.contains(scriptName) && scriptId != null) {
                    try {
                        LinkedHashMap deleteCmd = scriptDeleteCommand(scriptId)
                        if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
                        postCommandSync(deleteCmd, uri)
                        scriptsRemoved++
                        logDebug("Removed script '${scriptName}' (id: ${scriptId})")
                    } catch (Exception ex) {
                        logDebug("Could not remove script '${scriptName}': ${ex.message}")
                    }
                }
            }
            if (scriptsRemoved > 0) {
                logInfo("Removed ${scriptsRemoved} script(s) from ${deviceName}")
                totalRemoved += scriptsRemoved
            }
        }
    } catch (Exception ex) {
        logDebug("Could not list scripts for cleanup: ${ex.message}")
    }

    // Step 3: Remove all Hubitat KVS entries
    try {
        List<String> hubitatKvsKeys = ['hubitat_sdm_ip', 'hubitat_sdm_pm_ri']
        Integer kvsRemoved = 0
        hubitatKvsKeys.each { String key ->
            try {
                LinkedHashMap deleteCmd = kvsDeleteCommand(key)
                if (authIsEnabled() == true && getAuth().size() > 0) { deleteCmd.auth = getAuth() }
                LinkedHashMap response = postCommandSync(deleteCmd, uri)
                // KVS.Delete succeeds even if key doesn't exist, so we count it
                kvsRemoved++
                logDebug("Removed KVS entry '${key}'")
            } catch (Exception ex) {
                logDebug("Could not remove KVS entry '${key}': ${ex.message}")
            }
        }
        if (kvsRemoved > 0) {
            logInfo("Removed ${kvsRemoved} KVS entry(ies) from ${deviceName}")
            totalRemoved += kvsRemoved
        }
    } catch (Exception ex) {
        logDebug("Could not remove KVS entries: ${ex.message}")
    }

    if (totalRemoved > 0) {
        logInfo("✓ Cleanup complete: removed ${totalRemoved} resource(s) from ${deviceName}")
        appendLog('info', "Cleanup complete: ${totalRemoved} resource(s) removed")
    } else {
        logInfo("No Hubitat resources found on ${deviceName} to clean up")
    }
}

/**
 * Removes a created Shelly device and its children (if parent-child).
 * Cleans up device configs and updates the status cache.
 * Performs comprehensive cleanup of all Hubitat-managed resources on the Shelly device.
 *
 * @param ip The IP address of the device to remove
 */
private void removeDeviceByIp(String ip) {
    def device = findChildDeviceByIp(ip)
    if (!device) {
        logWarn("removeDeviceByIp: no child device found for ${ip}")
        return
    }

    String dni = device.deviceNetworkId
    String name = device.displayName
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = deviceConfigs[dni] as Map

    // Step 1: Clean up all Hubitat resources on the Shelly device before removing from Hubitat
    // Gen 1 devices don't support RPC — skip webhook/script/KVS cleanup
    if (config?.gen?.toString() != '1') {
        cleanupShellyDevice(ip, name)
    } else {
        logInfo("No Hubitat resources to clean up on Gen 1 device ${name}")
    }

    // Step 2: Remove child devices (if parent-child architecture)
    if (config?.isParentChild && config?.childDnis) {
        List<String> childDnis = config.childDnis as List<String>
        childDnis.each { String childDni ->
            def childDev = getChildDevice(childDni)
            if (childDev) {
                String childName = childDev.displayName
                deleteChildDevice(childDni)
                logInfo("Removed child device: ${childName} (${childDni})")
                appendLog('info', "Removed child: ${childName}")
            }
        }
    }

    // Step 3: Remove the device from Hubitat
    deleteChildDevice(dni)
    state.remove('hubDnisCachedAt') // Invalidate DNI cache after device removal
    logInfo("Removed device: ${name} (${dni})")
    appendLog('info', "Removed: ${name}")

    // Step 4: Clean up device config from state
    deviceConfigs.remove(dni)
    state.deviceConfigs = deviceConfigs

    // Step 5: Update status cache to reflect device removal
    Map cache = state.deviceStatusCache ?: [:]
    if (cache[ip]) {
        Map entry = cache[ip] as Map
        entry.isCreated = false
        entry.hubDeviceDni = null
        entry.hubDeviceName = null
        entry.hubDeviceId = null
        entry.requiredScriptCount = null
        entry.installedScriptCount = null
        entry.activeScriptCount = null
        entry.requiredWebhookCount = null
        entry.createdWebhookCount = null
        entry.enabledWebhookCount = null
        entry.lastRefreshed = null
        cache[ip] = entry
        state.deviceStatusCache = cache
    }
}

/**
 * Cleans up stale device config entries for devices that were deleted outside the app.
 * Scans state.deviceConfigs and removes any entries where the device no longer exists in Hubitat.
 *
 * @param ip The IP address of the device to check
 */
private void cleanupStaleDeviceConfig(String ip) {
    Map deviceConfigs = state.deviceConfigs ?: [:]
    if (deviceConfigs.isEmpty()) { return }

    // Find all DNIs that reference this IP
    List<String> staleDnis = []
    List<com.hubitat.app.DeviceWrapper> allChildren = getChildDevices() ?: []
    Set<String> existingDnis = allChildren.collect { it.deviceNetworkId } as Set

    deviceConfigs.each { String dni, Map config ->
        // If the DNI is in our config but not in Hubitat's device list, it's stale
        if (!existingDnis.contains(dni)) {
            staleDnis.add(dni)
        }
    }

    // Remove stale entries
    if (staleDnis.size() > 0) {
        staleDnis.each { String dni ->
            logInfo("Removing stale device config for ${dni} (device no longer exists in Hubitat)")
            deviceConfigs.remove(dni)
        }
        state.deviceConfigs = deviceConfigs
        logInfo("Cleaned up ${staleDnis.size()} stale device config(s)")
    }
}

/**
 * Stores device component configuration in app state for later reference.
 * Extracts component types from the device status and stores them keyed by DNI.
 * Used by {@link #isBatteryPoweredDevice}, {@link #isSleepyBatteryDevice},
 * and the device config page to make
 * intelligent decisions without needing to contact the device.
 *
 * @param dni The device network ID
 * @param deviceInfo The discovered device info map (from state.discoveredShellys)
 * @param driverName The assigned driver name
 * @param isParentChild Whether this device uses parent-child architecture
 * @param childDnis List of child device DNIs (only for parent-child devices)
 */
private void storeDeviceConfig(String dni, Map deviceInfo, String driverName, Boolean isParentChild = false, List<String> childDnis = []) {
    Map deviceConfigs = state.deviceConfigs ?: [:]

    // Extract component types from device status keys
    Map deviceStatus = deviceInfo.deviceStatus ?: [:]
    Set<String> componentTypes = []
    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        String baseType = key.contains(':') ? key.split(':')[0] : key
        componentTypes.add(baseType)
    }

    Map config = [
        driverName: driverName,
        model: deviceInfo.model ?: 'Unknown',
        gen: deviceInfo.gen,
        componentTypes: componentTypes as List<String>,
        hasBattery: componentTypes.contains('devicepower'),
        hasScript: componentTypes.contains('script'),
        hasBthome: componentTypes.contains('bthome'),
        hasSwitch: componentTypes.any { it.startsWith('switch') },
        hasCover: componentTypes.contains('cover'),
        hasLight: componentTypes.contains('light'),
        hasSmoke: componentTypes.contains('smoke'),
        hasInput: componentTypes.contains('input'),
        hasTemperature: componentTypes.contains('temperature'),
        hasHumidity: componentTypes.contains('humidity'),
        hasFlood: componentTypes.contains('flood'),
        hasContact: componentTypes.contains('contact'),
        hasMotion: componentTypes.contains('motion'),
        hasThermostat: componentTypes.contains('thermostat'),
        supportedWebhookEvents: (deviceInfo.supportedWebhookEvents ?: []) as List<String>,
        storedAt: now()
    ]

    // Add parent-child metadata if applicable
    if (isParentChild) {
        config.isParentChild = true
        config.childDnis = childDnis
    }

    // Gen 1 devices: track action URL installation state
    if (deviceInfo.gen?.toString() == '1') {
        config.gen1ActionUrlsInstalled = false
    }

    deviceConfigs[dni] = config
    state.deviceConfigs = deviceConfigs
    logDebug("Stored device config for ${dni}: ${config}")
}

/**
 * Determines if a child device is battery-powered.
 * Battery devices should never be periodically polled — status updates come
 * exclusively from on-device webhook pushes to Hubitat. Polling drains battery
 * life even on always-awake devices like Motion sensors and Sense.
 *
 * <p>Use this for polling exclusion. For wake-up queuing decisions (whether the
 * device needs deferred action URL / settings installation), use
 * {@link #isSleepyBatteryDevice} instead.
 *
 * @param childDevice The child device to check
 * @return true if the device is battery-powered
 */
private Boolean isBatteryPoweredDevice(def childDevice) {
    if (!childDevice) { return false }

    String dni = childDevice.deviceNetworkId
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = deviceConfigs[dni] as Map

    if (config) {
        return config.hasBattery && !config.hasBthome && !config.hasScript
    }

    // Fallback: check driver name for battery patterns (Gen 1 and Gen 2)
    String typeName = childDevice.typeName ?: ''
    return typeName.contains('TH Sensor') || typeName.contains('Temperature Sensor') ||
           typeName.contains('Humidity Sensor') || typeName.contains('Battery Device') ||
           typeName.contains('Flood Sensor') || typeName.contains('DW Sensor') ||
           typeName.contains('Gen1 Button') || typeName.contains('Smoke Sensor') ||
           typeName.contains('Motion Sensor') || typeName.contains('Gen1 Sense')
}

/**
 * Determines if a child device is a sleepy battery device that is usually
 * unreachable via HTTP. Sleepy devices wake briefly to send action URL callbacks,
 * then go back to sleep. Configuration changes (action URLs, settings) must be
 * queued and applied during the next wake-up window.
 *
 * <p>Always-awake battery devices (Motion sensors, Sense) are NOT sleepy —
 * they can receive settings pushes at any time. But they should still not be
 * polled; use {@link #isBatteryPoweredDevice} for polling exclusion.
 *
 * @param childDevice The child device to check
 * @return true if the device is a sleepy (usually unreachable) battery device
 */
private Boolean isSleepyBatteryDevice(def childDevice) {
    if (!childDevice) { return false }

    String dni = childDevice.deviceNetworkId
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = deviceConfigs[dni] as Map

    if (config) {
        return config.hasBattery && !config.hasBthome && !config.hasScript && !config.hasThermostat && !config.hasMotion
    }

    // Fallback: check driver name for sleepy-only patterns (excludes Motion/Sense which are always-awake)
    String typeName = childDevice.typeName ?: ''
    return typeName.contains('TH Sensor') || typeName.contains('Temperature Sensor') ||
           typeName.contains('Humidity Sensor') || typeName.contains('Battery Device') ||
           typeName.contains('Flood Sensor') || typeName.contains('DW Sensor') ||
           typeName.contains('Gen1 Button') || typeName.contains('Smoke Sensor')
}

/**
 * Probes a battery device for its current sensor state while it is awake.
 * Queries Shelly.GetStatus for temperature, humidity, and battery values,
 * then sends events to the child device and caches the state in deviceConfigs
 * for display when the device goes back to sleep.
 *
 * @param childDevice The child device to update
 * @param ip The device IP address
 */
private void probeBatteryDeviceState(def childDevice, String ip) {
    try {
        Map deviceStatus = queryDeviceStatus(ip)
        if (!deviceStatus) { return }

        String dni = childDevice.deviceNetworkId
        String scale = location.temperatureScale ?: 'F'
        Map cachedState = [:]

        // Extract temperature
        deviceStatus.each { k, v ->
            String key = k.toString().toLowerCase()
            if (key.startsWith('temperature:') && v instanceof Map) {
                BigDecimal tempC = v.tC != null ? v.tC as BigDecimal : null
                BigDecimal tempF = v.tF != null ? v.tF as BigDecimal : null
                BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC)
                if (temp != null) {
                    String unit = "\u00B0${scale}"
                    childDevice.sendEvent(name: 'temperature', value: temp, unit: unit,
                        descriptionText: "Temperature is ${temp}${unit}")
                    cachedState.temperature = "${temp}${unit}"
                }
            }
            if (key.startsWith('humidity:') && v instanceof Map && v.rh != null) {
                BigDecimal humidity = v.rh as BigDecimal
                childDevice.sendEvent(name: 'humidity', value: humidity, unit: '%',
                    descriptionText: "Humidity is ${humidity}%")
                cachedState.humidity = "${humidity}%"
            }
            if (key.startsWith('devicepower:') && v instanceof Map && v.battery?.percent != null) {
                Integer batteryPct = v.battery.percent as Integer
                childDevice.sendEvent(name: 'battery', value: batteryPct, unit: '%',
                    descriptionText: "Battery is ${batteryPct}%")
                cachedState.battery = "${batteryPct}%"
            }
        }

        // Cache the probed state for display when asleep
        if (cachedState) {
            Map deviceConfigs = state.deviceConfigs ?: [:]
            Map config = deviceConfigs[dni] as Map
            if (config) {
                config.lastProbedState = cachedState
                config.lastProbedAt = now()
                deviceConfigs[dni] = config
                state.deviceConfigs = deviceConfigs
            }
        }

        logDebug("Probed battery device state for ${childDevice.displayName}: ${cachedState}")
    } catch (Exception e) {
        logDebug("Failed to probe battery device state: ${e.message}")
    }
}

/**
 * Retrieves the list of scripts installed on a Shelly device.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return List of script maps containing name, enable, running status, or null on failure
 */
/**
 * Retrieves the list of scripts installed on a Shelly device.
 * Retries up to 3 times on timeout/connection errors before giving up.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return List of script maps, empty list if none, or null on failure
 */
List<Map> listDeviceScripts(String ipAddress) {
    Integer maxAttempts = 3
    String uri = "http://${ipAddress}/rpc"
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            LinkedHashMap command = scriptListCommand()
            if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
            LinkedHashMap json = postCommandSync(command, uri)
            if (json?.result?.scripts) {
                return json.result.scripts as List<Map>
            }
            return []
        } catch (Exception ex) {
            Boolean isTimeout = ex.message?.contains('timed out') || ex.message?.contains('unreachable') || ex.message?.contains('No route')
            if (isTimeout && attempt < maxAttempts) {
                logDebug("listDeviceScripts attempt ${attempt}/${maxAttempts} failed for ${ipAddress}: ${ex.message} — retrying")
                continue
            }
            if (isTimeout) {
                logDebug("Device at ${ipAddress} is unreachable after ${maxAttempts} attempts: ${ex.message}")
            } else {
                logError("Failed to list scripts for ${ipAddress}: ${ex.message}")
            }
            return null
        }
    }
    return null
}

/**
 * Renders the driver rebuild status HTML for the main page.
 * Shows progress when rebuilding, or a completion message otherwise.
 *
 * @return HTML string for the rebuild status
 */
/**
 * Renders the driver management section HTML, including tracked
 * driver list with device counts and update status. Used both for
 * initial page render and SSR updates.
 *
 * @return HTML string for the driver management section
 */
private String renderDriverManagementHtml() {
    StringBuilder sb = new StringBuilder()
    String currentVersion = getAppVersion()
    Map allDrivers = state.autoDrivers ?: [:]

    if (allDrivers.isEmpty()) {
        sb.append("No drivers are currently tracked.<br>")
    } else {
        def childDevices = getChildDevices() ?: []
        sb.append("<b>${allDrivers.size()}</b> driver(s) tracked (app v${currentVersion}):<br>")
        allDrivers.each { key, info ->
            Integer deviceCount = childDevices.count { it.typeName == info.name }
            String versionTag = (info.version && info.version != currentVersion) ? " <i>(outdated: v${info.version})</i>" : ''
            sb.append("&nbsp;&nbsp;- ${info.name} (${deviceCount} device(s))${versionTag}<br>")
        }
    }

    sb.append('<br>')
    List<String> outdated = []
    allDrivers.each { key, info ->
        if (info.version && info.version != currentVersion) {
            outdated.add(info.name as String)
        }
    }
    if (outdated.size() > 0) {
        sb.append("<b>${outdated.size()} driver(s) need updating.</b>")
    } else if (!allDrivers.isEmpty()) {
        sb.append("<b>All drivers are up to date (v${currentVersion}).</b>")
    }

    return sb.toString()
}

/**
 * Renders the battery device status message HTML, including last known
 * sensor values when the device is asleep.
 *
 * @param isReachable Whether the device is currently reachable
 * @param childDevice The child device (optional, for reading last known values)
 * @return HTML string for the battery device status
 */
private String renderBatteryDeviceStatus(Boolean isReachable, def childDevice = null) {
    StringBuilder sb = new StringBuilder()

    if (isReachable) {
        sb.append("<b>This is a battery-powered device and it is currently awake.</b>")

        // Show current state from device attributes (just updated by probeBatteryDeviceState)
        if (childDevice) {
            List<String> current = buildSensorStateList(childDevice)
            if (current.size() > 0) {
                sb.append("<br><br><b>Current state:</b><br>")
                sb.append(current.join('<br>'))
            }
        }
    } else {
        sb.append("<b>This is a battery-powered device and it is currently asleep.</b><br>")
        sb.append("It can only be reached when it is awake (briefly, when sending sensor updates).<br>")
        sb.append("Webhooks can be configured while the device is awake, or via the Shelly app/web UI.")

        // Show last known sensor values if available
        if (childDevice) {
            List<String> lastKnown = buildSensorStateList(childDevice)
            if (lastKnown.size() > 0) {
                sb.append("<br><br><b>Last known state:</b><br>")
                sb.append(lastKnown.join('<br>'))
            }
        }
    }

    return sb.toString()
}

/**
 * Builds a list of formatted sensor state strings from a device's current attributes.
 *
 * @param childDevice The child device to read attributes from
 * @return List of formatted strings like "Temperature: 24.4°F"
 */
private List<String> buildSensorStateList(def childDevice) {
    List<String> values = []
    def temp = childDevice.currentValue('temperature')
    if (temp != null) {
        String scale = location.temperatureScale ?: 'F'
        values.add("Temperature: ${temp}\u00B0${scale}")
    }
    def humidity = childDevice.currentValue('humidity')
    if (humidity != null) { values.add("Humidity: ${humidity}%") }
    def battery = childDevice.currentValue('battery')
    if (battery != null) { values.add("Battery: ${battery}%") }
    return values
}

/**
 * Renders webhook status HTML for the device config page.
 * Probes the device for installed webhooks and compares against required actions.
 *
 * @param device The child device
 * @param ip The device IP address
 * @param requiredActions List of required webhook action maps
 * @param deviceIsReachable Whether the device is currently reachable
 * @return HTML string showing webhook status
 */
private String renderWebhookStatusHtml(def device, String ip, List<Map> requiredActions, Boolean deviceIsReachable) {
    String dni = device.deviceNetworkId

    if (!deviceIsReachable) {
        // Show last known webhook status from stored config
        Map deviceConfigs = state.deviceConfigs ?: [:]
        Map config = deviceConfigs[dni] as Map
        List<Map> cachedStatus = config?.lastWebhookStatus as List<Map>

        StringBuilder sb = new StringBuilder()
        if (cachedStatus) {
            sb.append("<b>Last known webhook status</b> (device is asleep):<br>")
            sb.append("<pre style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>")
            cachedStatus.each { Map entry ->
                sb.append("${entry.name} (${entry.event} cid:${entry.cid}) — ${entry.status}\n")
            }
            sb.append("</pre>")
        } else {
            sb.append("<b>Device is currently asleep.</b><br>")
            sb.append("Webhook status has not been checked yet. Wake the device to check.")
        }
        return sb.toString().trim()
    }

    List<Map> installedHooks = listDeviceWebhooks(ip)
    String hubIp = location.hub.localIP
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

    // Cache the webhook status for display when device is asleep
    List<Map> webhookStatusCache = requiredActions.collect { Map action ->
        Boolean isOk = okActions.contains(action)
        [name: action.name, event: action.event, cid: action.cid, status: isOk ? 'configured' : 'MISSING']
    }
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = deviceConfigs[dni] as Map
    if (config) {
        config.lastWebhookStatus = webhookStatusCache
        deviceConfigs[dni] = config
        state.deviceConfigs = deviceConfigs
    }

    StringBuilder sb = new StringBuilder()
    sb.append("<pre style='white-space:pre-wrap; font-size:14px; line-height:1.4;'>")
    requiredActions.each { Map action ->
        Boolean isOk = okActions.contains(action)
        String status = isOk ? 'configured' : 'MISSING'
        sb.append("${action.name} (${action.event} cid:${action.cid}) — ${status}\n")
    }
    sb.append("</pre>")

    if (missingActions.size() > 0) {
        sb.append("<b>${missingActions.size()} action(s) need to be configured.</b>")
    } else {
        sb.append("<b>All required actions are configured.</b>")
    }

    return sb.toString().trim()
}

/**
 * Fires a deferred SSR config table update event.
 * Called via {@code runInMillis(500, 'fireConfigTableSSR')} from button handlers
 * to ensure state is persisted before the SSR callback reads it.
 * Using bare {@code sendEvent()} triggers the SSR callback in {@link #processServerSideRender}.
 */
void fireConfigTableSSR() {
    sendEvent(name: 'configTable', value: 'update')
}

/**
 * Handles SSR (Server-Side Render) callbacks from Hubitat.
 * Called when a device event occurs and an SSR-tagged HTML element on the
 * currently displayed app page matches the event. Returns updated HTML
 * to replace the element's content.
 *
 * @param event Map containing event fields plus elementId
 * @return HTML string to replace the element content
 */
String processServerSideRender(Map event) {
    // logDebug("processServerSideRender called: ${event}")

    String elementId = event.elementId ?: ''
    String eventName = event.name ?: ''

    // App-level events
    if (eventName == 'configTable') {
        ensureDeviceStatusCache()
        return "<div id='config-table-wrapper'>${renderDeviceConfigTableMarkup()}</div>"
    }

    if (eventName == 'driverRebuildStatus') {
        return renderDriverManagementHtml()
    }

    if (eventName == 'bleTable') {
        return "<div id='ble-table-wrapper'>${renderBleTableMarkup()}</div>"
    }

    // Device-level events
    Integer deviceId = event.deviceId as Integer
    def childDevice = getChildDevices()?.find { (it.id as Integer) == deviceId }
    if (!childDevice) {
        logDebug("SSR: no child device found for deviceId ${deviceId}")
        return ''
    }

    String ip = childDevice.getDataValue('ipAddress')
    if (!ip) { return '' }

    // Re-probe the device — it's awake if we got an event
    Boolean deviceIsReachable = false
    Map deviceStatus = queryDeviceStatus(ip)
    if (deviceStatus) { deviceIsReachable = true }

    // Determine what to render based on the element
    if (elementId?.contains('webhook-status')) {
        List<Map> requiredActions = getRequiredActionsForDevice(childDevice, deviceIsReachable)
        return renderWebhookStatusHtml(childDevice, ip, requiredActions, deviceIsReachable)
    }

    // Default: render battery device status
    return renderBatteryDeviceStatus(deviceIsReachable, childDevice)
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
        if (ex.message?.contains('unreachable') || ex.message?.contains('timed out') || ex.message?.contains('No route')) {
            logDebug("Device at ${ipAddress} is unreachable (may be asleep): ${ex.message}")
        } else {
            logError("Failed to list webhooks for ${ipAddress}: ${ex.message}")
        }
        return null
    }
}

/**
 * Queries a Shelly device for its supported webhook event types via
 * the Webhook.ListSupported RPC method. Returns a list of event type
 * strings (e.g., "temperature.change", "switch.on").
 *
 * @param ipAddress The IP address of the Shelly device
 * @return List of supported event type strings, or null on failure
 */
List<String> listSupportedWebhookEvents(String ipAddress) {
    try {
        String uri = "http://${ipAddress}/rpc"
        LinkedHashMap command = webhookListSupportedCommand()
        if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
        LinkedHashMap json = postCommandSync(command, uri)
        if (json?.result?.types) {
            List<String> events = (json.result.types as Map).keySet().collect { it.toString() }
            logDebug("Supported webhook events for ${ipAddress}: ${events}")
            return events
        }
        return []
    } catch (Exception ex) {
        if (ex.message?.contains('unreachable') || ex.message?.contains('timed out') || ex.message?.contains('No route')) {
            logDebug("Device at ${ipAddress} is unreachable (may be asleep): ${ex.message}")
        } else {
            logError("Failed to list supported webhook events for ${ipAddress}: ${ex.message}")
        }
        return null
    }
}

/**
 * Queries input configurations for a device to determine their types.
 * Returns a map of input CID to input type (e.g., "button", "switch", "analog").
 *
 * @param ipAddress The IP address of the Shelly device
 * @param inputCids List of input component IDs to query
 * @return Map of CID to input type, or empty map if query fails
 */
private Map<Integer, String> getInputTypes(String ipAddress, List<Integer> inputCids) {
    Map<Integer, String> inputTypes = [:]
    if (!inputCids) { return inputTypes }

    String uri = "http://${ipAddress}/rpc"
    inputCids.each { Integer cid ->
        try {
            LinkedHashMap command = inputGetConfigCommand(cid)
            if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
            LinkedHashMap json = postCommandSync(command, uri)
            if (json?.result?.type) {
                inputTypes[cid] = json.result.type as String
                logDebug("Input ${cid} type: ${inputTypes[cid]}")
            }
        } catch (Exception ex) {
            logDebug("Failed to get input ${cid} config: ${ex.message}")
        }
    }
    return inputTypes
}

/**
 * Determines if an input webhook event is applicable to a given input type.
 *
 * @param event The webhook event name (e.g., "input.button_push")
 * @param inputType The input type (e.g., "button", "switch", "analog")
 * @return true if the event is applicable to this input type
 */
@CompileStatic
private Boolean isInputEventApplicable(String event, String inputType) {
    if (!event.startsWith('input.')) { return true }

    // Button events require button-type inputs
    if (event.contains('button_')) {
        return inputType == 'button'
    }

    // Toggle events require switch-type inputs
    if (event.contains('toggle_')) {
        return inputType == 'switch'
    }

    // Analog events require analog-type inputs
    if (event.contains('analog_')) {
        return inputType == 'analog'
    }

    return true
}

/**
 * Determines which webhook actions are required for a device by querying its
 * status and cross-referencing with component_driver.json capability definitions.
 * When the device is known to be unreachable (e.g., sleeping battery device),
 * skips live HTTP calls and uses cached data exclusively.
 *
 * @param device The child device to check
 * @param deviceIsReachable Whether the device is currently reachable; when false,
 *        skips live HTTP calls to avoid blocking on timeouts
 * @return List of required action maps, each with keys: event, name, dst, cid
 */
/**
 * Determines the required webhook actions for a device using centralized
 * webhookDefinitions from component_driver.json. Matches device component
 * types against event definitions and appends supplemental token groups
 * (e.g., battery data piggybacked on sensor webhooks).
 *
 * @param device The child device to check
 * @param deviceIsReachable Whether the device can be queried live
 * @return List of action maps with keys: event, name, dst, cid, urlParams
 */
List<Map> getRequiredActionsForDevice(def device, Boolean deviceIsReachable = true) {
    List<Map> requiredActions = []

    String ip = device.getDataValue('ipAddress')
    if (!ip) {
        logDebug("getRequiredActionsForDevice: no IP for ${device.displayName}")
        return requiredActions
    }

    // Get supported webhook events — try live query first, fall back to stored config
    List<String> supportedEvents = deviceIsReachable ? listSupportedWebhookEvents(ip) : null
    String dni = device.deviceNetworkId
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = deviceConfigs[dni] as Map

    // If stored config is missing, try to populate it from discovery data
    if (!config) {
        Map discoveredShellys = state.discoveredShellys ?: [:]
        Map discoveryData = discoveredShellys[ip]
        if (discoveryData?.deviceStatus) {
            storeDeviceConfig(dni, discoveryData, device.typeName ?: '')
            deviceConfigs = state.deviceConfigs ?: [:]
            config = deviceConfigs[dni] as Map
            logDebug("Populated missing device config for ${device.displayName} from discovery data")
        }
    }

    if (supportedEvents == null && config?.supportedWebhookEvents) {
        supportedEvents = config.supportedWebhookEvents as List<String>
        logDebug("Using stored supported webhook events for ${device.displayName}: ${supportedEvents}")
    }

    Map deviceStatus = deviceIsReachable ? queryDeviceStatus(ip) : null
    if (!deviceStatus) {
        // Fall back to stored component types for sleepy devices
        if (config?.componentTypes) {
            logDebug("Using stored component types for ${device.displayName}")
            deviceStatus = [:]
            (config.componentTypes as List<String>).each { String ct ->
                deviceStatus[ct + ':0'] = [id: 0]
            }
        } else {
            // Last resort: check discovery data directly
            Map discoveredShellys = state.discoveredShellys ?: [:]
            Map discoveryData = discoveredShellys[ip]
            if (discoveryData?.deviceStatus) {
                deviceStatus = discoveryData.deviceStatus as Map
                logDebug("Using discovery data status for ${device.displayName}")
            } else {
                logDebug("getRequiredActionsForDevice: no status available for ${device.displayName}")
                return requiredActions
            }
        }
    }

    // Fetch webhook definitions from component_driver.json
    Map webhookDefs = fetchWebhookDefinitions()

    if (webhookDefs?.events) {
        // New path: use centralized webhookDefinitions
        requiredActions = buildActionsFromWebhookDefs(webhookDefs, deviceStatus, supportedEvents, device)
    } else {
        // Fallback: use per-capability requiredActions (for backward compat with older JSON)
        logDebug("getRequiredActionsForDevice: webhookDefinitions not available, falling back to per-capability requiredActions")
        requiredActions = buildActionsFromCapabilities(deviceStatus, supportedEvents, device)
    }

    logDebug("Required actions for ${device.displayName}: ${requiredActions}")
    return requiredActions
}

/**
 * Builds webhook action list from centralized webhookDefinitions section.
 * Includes supplemental token groups (e.g., battery data on sensor webhooks)
 * and logs unknown events the device supports.
 *
 * @param webhookDefs The webhookDefinitions map from component_driver.json
 * @param deviceStatus The device status map (component keys to status maps)
 * @param supportedEvents List of supported webhook events, or null if unknown
 * @param device The device for logging context
 * @return List of action maps with keys: event, name, dst, cid, urlParams
 */
private List<Map> buildActionsFromWebhookDefs(Map webhookDefs, Map deviceStatus, List<String> supportedEvents, def device) {
    List<Map> requiredActions = []

    // Determine which component types the device has
    Set<String> deviceComponentTypes = [] as Set
    List<Integer> inputCids = []
    deviceStatus.each { k, v ->
        String baseType = k.toString().split(':')[0]
        deviceComponentTypes.add(baseType)
        if (baseType == 'input' && k.toString().contains(':')) {
            try {
                Integer cid = k.toString().split(':')[1] as Integer
                inputCids.add(cid)
            } catch (Exception ignored) {}
        }
    }

    // Check if device uses powermonitoring.js script (if so, don't create power webhooks)
    Set<String> requiredScripts = getRequiredScriptsForDevice(device)
    Boolean usesPowerScript = requiredScripts.any { it.toLowerCase().contains('powermonitoring') }
    if (usesPowerScript) {
        logDebug("Device uses powermonitoring.js script - will skip power webhook events")
    }

    // Query input configurations to determine their types (button, switch, analog)
    Map<Integer, String> inputTypes = [:]
    if (inputCids) {
        String ip = device.getDataValue('ipAddress')
        if (ip) {
            inputTypes = getInputTypes(ip, inputCids)
        }
    }

    // Build supplemental URL path segments (e.g., battery tokens for devices with devicepower)
    List<String> supplementalParts = []
    if (webhookDefs.supplementalTokenGroups) {
        (webhookDefs.supplementalTokenGroups as Map).each { String groupName, Map group ->
            if (deviceComponentTypes.contains(group.requiredComponent as String)) {
                supplementalParts.add(group.urlParams as String)
            }
        }
    }

    // Build required actions from webhookDefinitions events
    (webhookDefs.events as Map).each { String event, Map eventDef ->
        String shellyComponent = eventDef.shellyComponent as String
        if (!deviceComponentTypes.contains(shellyComponent)) { return }

        // Find matching component IDs for this event type
        deviceStatus.each { k, v ->
            String key = k.toString()
            String baseType = key.contains(':') ? key.split(':')[0] : key
            if (baseType != shellyComponent) { return }
            Integer cid = 0
            if (key.contains(':')) {
                try { cid = key.split(':')[1] as Integer } catch (Exception ignored) {}
            }

            // Filter 1: Only include events the device firmware supports
            if (supportedEvents != null && !supportedEvents.contains(event)) {
                logDebug("Skipping unsupported webhook event '${event}' for ${device.displayName}")
                return
            }

            // Filter 2: Skip power monitoring events if device uses powermonitoring.js script
            if (usesPowerScript && (event.contains('active_power_change') || event.contains('active_power_measurement'))) {
                logDebug("Skipping power event '${event}' - device uses powermonitoring.js script")
                return
            }

            // Filter 3: For input events, check if the event is applicable to this input's type
            if (shellyComponent == 'input' && inputTypes.containsKey(cid)) {
                String inputType = inputTypes[cid]
                if (!isInputEventApplicable(event, inputType)) {
                    logDebug("Skipping input event '${event}' for input ${cid} (type: ${inputType})")
                    return
                }
            }

            // Event passed all filters - add it to required actions
            String urlParams = (eventDef.urlParams as String).replace('__CID__', cid.toString())
            List<String> allParts = []
            if (urlParams) { allParts.add(urlParams) }
            allParts.addAll(supplementalParts)
            urlParams = allParts.join('/')

            requiredActions.add([
                event    : event,
                name     : eventDef.name,
                dst      : eventDef.dst,
                cid      : cid,
                urlParams: urlParams
            ])
        }
    }

    // Log unknown events the device supports that we don't have definitions for
    if (supportedEvents) {
        Set<String> knownEvents = (webhookDefs.events as Map).keySet()
        List<String> skippedEvents = (webhookDefs.skippedEvents ?: []) as List<String>
        supportedEvents.each { String event ->
            if (!knownEvents.contains(event) && !skippedEvents.contains(event)) {
                logInfo("Unknown webhook event '${event}' available on ${device.displayName} — not configured in webhookDefinitions")
            }
        }
    }

    return requiredActions
}

/**
 * Fallback: builds webhook action list from per-capability requiredActions.
 * Used when the remote component_driver.json does not yet contain the
 * centralized webhookDefinitions section.
 *
 * @param deviceStatus The device status map
 * @param supportedEvents List of supported webhook events, or null if unknown
 * @param device The device for logging context
 * @return List of action maps with keys: event, name, dst, cid
 */
private List<Map> buildActionsFromCapabilities(Map deviceStatus, List<String> supportedEvents, def device) {
    List<Map> requiredActions = []

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
                String eventName = action.event as String
                if (supportedEvents == null || supportedEvents.contains(eventName)) {
                    requiredActions.add([
                        event: eventName,
                        name : action.name,
                        dst  : action.dst,
                        cid  : cid
                    ])
                } else {
                    logDebug("Skipping unsupported webhook event '${eventName}' for ${device.displayName}")
                }
            }
        }
    }

    return requiredActions
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
        // Covers both standard fields (voltage, current, apower, aenergy) used by pm1/switch/cover
        // and EM-specific fields (a_voltage, a_current, a_act_power) used by em/em1 components
        if (v instanceof Map) {
            Map statusMap = v as Map
            if (statusMap.voltage != null || statusMap.current != null ||
                    statusMap.apower != null || statusMap.aenergy != null ||
                    statusMap.a_voltage != null || statusMap.a_current != null ||
                    statusMap.a_act_power != null || statusMap.act_power != null) {
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
        if (ex.message?.contains('unreachable') || ex.message?.contains('timed out') || ex.message?.contains('No route')) {
            logDebug("Device at ${ipAddress} is unreachable: ${ex.message}")
        } else {
            logError("Failed to query device status for ${ipAddress}: ${ex.message}")
        }
        return null
    }
}

/**
 * Fetches the webhookDefinitions section from component_driver.json on GitHub.
 * Contains centralized webhook event-to-URL mappings, supplemental token groups,
 * and skipped event lists.
 *
 * @return Map with keys: events, supplementalTokenGroups, skippedEvents; or null on failure
 */
private Map fetchWebhookDefinitions() {
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
        return componentData?.webhookDefinitions as Map
    } catch (Exception e) {
        logError("Failed to parse component_driver.json webhookDefinitions: ${e.message}")
        return null
    }
}

/**
 * Fast reachability check for a Shelly device using Shelly.GetDeviceInfo with
 * a short timeout. Used on the config page to quickly determine if a battery
 * device is awake without blocking the page for the full 10s default timeout.
 *
 * @param ipAddress The IP address of the Shelly device
 * @return true if the device responds, false otherwise
 */
private Boolean isDeviceReachable(String ipAddress) {
    try {
        Map params = [
            uri: "http://${ipAddress}/rpc",
            contentType: 'application/json',
            requestContentType: 'application/json',
            body: [id: 0, src: 'reachabilityCheck', method: 'Shelly.GetDeviceInfo'],
            timeout: 2
        ]
        Boolean reachable = false
        httpPost(params) { resp ->
            if (resp.getStatus() == 200) { reachable = true }
        }
        return reachable
    } catch (Exception ex) {
        return false
    }
}

/**
 * Checks if a Gen 1 Shelly device is reachable by querying its {@code /shelly} endpoint.
 *
 * @param ipAddress The device IP address
 * @return true if the device responds to HTTP GET /shelly
 */
private Boolean isGen1DeviceReachable(String ipAddress) {
    try {
        Boolean reachable = false
        httpGetHelper([uri: "http://${ipAddress}/shelly", timeout: 2, contentType: 'application/json']) { resp ->
            if (resp?.status == 200) { reachable = true }
        }
        return reachable
    } catch (Exception ex) {
        return false
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
 * Initializes the app state and sets up mDNS discovery.
 * Initializes state variables for discovered devices and logs,
 * mirrors logging settings to state, subscribes to system start events,
 * and registers mDNS listeners.
 */
void initialize() {
    if (!state.discoveredShellys) { state.discoveredShellys = [:] }
    if (!state.recentLogs) { state.recentLogs = [] }
    // Trim oversized log buffer from previous versions (was 300, now capped at 50)
    if (state.recentLogs instanceof List && state.recentLogs.size() > 50) {
        state.recentLogs = state.recentLogs[-50..-1]
    }
    if (state.discoveryRunning == null) { state.discoveryRunning = false }

    // BLE state initialization
    if (!state.discoveredBleDevices) { state.discoveredBleDevices = [:] }
    if (!state.recentBlePids) { state.recentBlePids = [:] }
    if (!state.bleGateways) { state.bleGateways = [] }

    // Ensure state mirrors current settings for logging
    state.logLevel = settings?.logLevel ?: (state.logLevel ?: 'debug')
    state.displayLogLevel = settings?.displayLogLevel ?: state.logLevel

    // mDNS listeners must be registered on system startup per Hubitat docs
    subscribe(location, 'systemStart', 'systemStartHandler')

    // Also register now in case hub has been up for a while
    startMdnsDiscovery()

    // Clean up stale state from previous versions
    state.remove('driverRebuildInProgress')
    state.remove('driverRebuildQueue')
    state.remove('driverRebuildCurrentKey')
    state.remove('driverRebuildErrors')
    state.remove('pendingDeviceCreations')
    state.remove('discoveryDriverQueue')
    state.remove('discoveryDriverInProgress')
    state.remove('driverGeneration')

    // Clean up stale driver tracking entries from old app versions
    pruneStaleDriverTracking()

    // Check for app version change and trigger driver update
    String currentVersion = getAppVersion()
    String lastVersion = state.lastAutoconfVersion

    if (lastVersion == null) {
        // First install: store version, no update needed
        state.lastAutoconfVersion = currentVersion
        logInfo("First install detected, storing app version: ${currentVersion}")
    } else if (lastVersion != currentVersion) {
        state.lastAutoconfVersion = currentVersion
        if (settings?.rebuildOnUpdate != false) {
            logInfo("App version changed from ${lastVersion} to ${currentVersion}, updating drivers")
            reinstallAllPrebuiltDrivers()
        } else {
            logInfo("App version changed from ${lastVersion} to ${currentVersion} (driver update disabled)")
        }
    } else {
        // Even if app version hasn't changed, check if any tracked drivers are outdated
        Map allDrivers = state.autoDrivers ?: [:]
        Boolean hasOutdated = allDrivers.any { key, info -> info.version != currentVersion }
        if (hasOutdated && settings?.rebuildOnUpdate != false) {
            logInfo("Found outdated drivers at app version ${currentVersion}, triggering update")
            reinstallAllPrebuiltDrivers()
        } else {
            logDebug("App version unchanged (${currentVersion}), no driver update needed")
        }
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

    // Schedule Gen 1 device polling if any Gen 1 devices exist
    scheduleGen1Polling()

    // Schedule BLE presence check every 5 minutes (only when BLE is active)
    if (state.bleGateways || state.discoveredBleDevices) {
        schedule('0 */5 * ? * *', 'checkBlePresence')
    } else {
        unschedule('checkBlePresence')
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
    unschedule('processMdnsDiscovery')
    runIn(getDiscoveryDurationSeconds(), 'stopDiscovery')
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
 * Registers mDNS listener for Shelly device discovery.
 * Registers a listener for {@code _http._tcp} which captures all Shelly devices
 * (both Gen1 and Gen2+) on the local network.
 */
void startMdnsDiscovery() {
    try {
        registerMDNSListener('_http._tcp')
        logTrace('Registered mDNS listener: _http._tcp')
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
    logTrace('System start detected, registering mDNS listeners')
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
    unschedule('stopDiscovery')
    unschedule('fireFoundShellyEventsIfPending')
    state.remove('pendingFoundShellyEvent')

    // Fire one final SSR update so the table reflects all devices found
    sendFoundShellyEvents()

    // Do NOT unregister mDNS listeners - keep them active so data accumulates
    logTrace('Discovery stopped (mDNS listeners remain active)')
}

/**
 * No-op retained for backward compatibility with any pending {@code runIn()} schedules.
 * Timer countdown is now handled by client-side JavaScript, and log display
 * updates on page re-renders. No {@code sendEvent()} calls are needed.
 */
void updateDiscoveryUI() {
    // Intentionally empty — timer is client-side JS, logs update on page re-render
}

/**
 * Processes mDNS discovery by querying for Shelly devices on the network.
 * Retrieves mDNS entries for {@code _http._tcp}, filters for Shelly devices,
 * extracts device information (name, IP, port, generation, firmware version),
 * and stores discovered devices in state. Updates the UI with discovery results
 * and reschedules itself periodically while discovery is active.
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
        List<Map<String,Object>> allEntries = getMDNSEntries('_http._tcp') ?: []
        logTrace("processMdnsDiscovery: _http._tcp returned ${allEntries.size()} entries")

        if (!allEntries) {
            logTrace('processMdnsDiscovery: no mDNS entries found')
        } else {
            logTrace("processMdnsDiscovery: processing ${allEntries.size()} total mDNS entries")
            Integer beforeCount = state.discoveredShellys.size()
            allEntries.each { entry ->
                // Actual mDNS entry fields: server, port, ip4Addresses, ip6Addresses, gen, app, ver
                String server = (entry?.server ?: '') as String
                Integer port = (entry?.port ?: 0) as Integer
                String gen = (entry?.gen ?: '') as String
                String deviceApp = (entry?.app ?: '') as String
                String ver = (entry?.ver ?: '') as String

                // Defensive parsing: ip4Addresses may be String or List<String> depending on service type
                Object rawIp4 = entry?.ip4Addresses
                logTrace("mDNS ip4Addresses isList=${rawIp4 instanceof List}, value=${rawIp4}")
                String ip4 = ''
                if (rawIp4 instanceof List) {
                    ip4 = rawIp4.find { it && !it.toString().contains(':') }?.toString() ?: ''
                } else if (rawIp4) {
                    ip4 = rawIp4.toString().replaceAll(/[\[\]]/, '').trim()
                }

                logTrace("mDNS entry: server=${server}, ip=${ip4}, port=${port}, gen=${gen}, app=${deviceApp}, ver=${ver}")

                // All entries from _shelly._tcp are Shelly devices
                // For _http._tcp entries, check if server name contains 'shelly'
                String serverLower = server.toLowerCase()
                Boolean looksShelly = serverLower.contains('shelly') || gen || deviceApp

                if (!looksShelly || !ip4) {
                    return
                }

                // Clean up server name (remove trailing dot and .local.)
                String deviceName = stripMdnsDomainSuffix(server)

                String key = ip4
                Boolean isNewToState = !state.discoveredShellys.containsKey(key)
                Boolean alreadyLogged = foundDevices.containsKey(key)

                // Capture existing entry BEFORE overwrite so we can check identification status
                Map existingEntry = isNewToState ? null : (state.discoveredShellys[key] as Map)

                // Only log if this is a newly discovered device AND we haven't logged it yet this run
                if (isNewToState && !alreadyLogged) {
                    logDebug("Found NEW Shelly: ${deviceName} at ${ip4}:${port} (gen=${gen}, app=${deviceApp}, ver=${ver})")
                    foundDevices.put(key, true)
                }

                Map deviceEntry = [
                    name: deviceName ?: "Shelly ${ip4}",
                    ipAddress: ip4,
                    port: (port ?: 80),
                    gen: gen,
                    deviceApp: deviceApp,
                    ver: ver,
                    ts: now()
                ]

                // Gen1 devices advertise under _http._tcp with no gen/app TXT records.
                // Extract type from hostname for immediate identification; leave mac null
                // so the /shelly probe still fires to get the REAL MAC address.
                if (isLikelyGen1Device(gen, deviceApp, deviceName)) {
                    String gen1Type = extractGen1TypeFromHostname(deviceName)
                    if (gen1Type) {
                        String typeKey = gen1Type.toString()
                        deviceEntry.gen = '1'
                        deviceEntry.gen1Type = typeKey
                        deviceEntry.model = GEN1_TYPE_TO_MODEL.get(typeKey) ?: typeKey
                        deviceEntry.isBatteryDevice = GEN1_BATTERY_TYPES.contains(typeKey)
                        // NOTE: Do NOT set mac from hostname — leave it null so the /shelly probe
                        // at fetchAndStoreDeviceInfo() fires to get the REAL MAC address.
                    } else {
                        // Hostname didn't match known Gen1 patterns (custom name) — still mark as Gen1
                        deviceEntry.gen = '1'
                    }
                }

                // Preserve enriched fields from prior /shelly probe or REST/RPC fetch
                if (existingEntry) {
                    for (String field : ['mac', 'model', 'gen1Type', 'isBatteryDevice', 'deviceInfo',
                                         'deviceConfig', 'deviceStatus', 'gen1Settings', 'gen1Status',
                                         'auth_en', 'fw_id', 'profile', 'supportedWebhookEvents']) {
                        if (existingEntry[field] != null && !deviceEntry.containsKey(field)) {
                            deviceEntry[field] = existingEntry[field]
                        }
                    }
                    // Preserve gen from prior enrichment if mDNS TXT didn't provide one
                    if (!deviceEntry.gen && existingEntry.gen) {
                        deviceEntry.gen = existingEntry.gen
                    }
                }

                state.discoveredShellys[key] = deviceEntry

                // Schedule async device info fetch for new or still-unidentified devices.
                // Re-queuing unidentified devices handles sleepy battery devices that were
                // unreachable on a previous attempt but may now be awake.
                Boolean needsIdentification = !isNewToState && (!existingEntry?.model || existingEntry?.model == 'Unknown')
                if (isNewToState || needsIdentification) {
                    scheduleAsyncDeviceInfoFetch(key)
                }
            }
            Integer afterCount = state.discoveredShellys.size()
            if (afterCount > beforeCount) {
                logDebug("Found ${afterCount - beforeCount} new device(s), total: ${afterCount}")
                debounceSendFoundShellyEvents()
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
 * Schedules an SSR table update after a short debounce delay.
 * Multiple rapid calls collapse into a single {@link #sendFoundShellyEvents()} call,
 * preventing Hubitat's per-app rate limit from being exceeded during discovery
 * when many async device fetches complete in rapid succession.
 */
private void debounceSendFoundShellyEvents() {
    state.pendingFoundShellyEvent = true
    runIn(3, 'fireFoundShellyEventsIfPending')
}

/**
 * Fires the debounced SSR event if one is pending.
 * Called by {@code runIn()} after the debounce delay set by
 * {@link #debounceSendFoundShellyEvents()}.
 */
void fireFoundShellyEventsIfPending() {
    if (state.pendingFoundShellyEvent) {
        state.pendingFoundShellyEvent = false
        sendFoundShellyEvents()
    }
}

/**
 * Fires an SSR event to update the device configuration table on the main page.
 * Merges newly discovered devices into the existing cache without destroying entries
 * that already have detailed status data (script/webhook counts from RPC queries).
 * Uses bare {@code sendEvent()} to trigger the SSR callback in {@link #processServerSideRender}.
 *
 * <p><b>Note:</b> During discovery, prefer {@link #debounceSendFoundShellyEvents()} to avoid
 * exceeding Hubitat's per-app event rate limit.</p>
 */
void sendFoundShellyEvents() {
    Map cache = state.deviceStatusCache ?: [:]
    Map discoveredShellys = state.discoveredShellys ?: [:]
    discoveredShellys.each { ipKey, info ->
        String ip = ipKey.toString()
        Map infoMap = info as Map
        if (!cache.containsKey(ip)) {
            cache[ip] = buildMinimalCacheEntry(ip, infoMap)
        } else {
            // Update existing cache entries with enriched data from async fetches
            Map existing = cache[ip] as Map
            if (infoMap.mac && (!existing.mac || existing.mac == '')) {
                existing.mac = infoMap.mac.toString()
            }
            if (infoMap.model && (!existing.model || existing.model == 'Unknown')) {
                existing.model = infoMap.model.toString()
            }
            if (infoMap.isBatteryDevice == true && existing.isBatteryDevice != true) {
                existing.isBatteryDevice = true
            }
            cache[ip] = existing
        }
    }
    state.deviceStatusCache = cache
    sendEvent(name: 'configTable', value: 'discovery')
}

// ═══════════════════════════════════════════════════════════════
// IP Address Watchdog (mDNS-based IP change detection)
// ═══════════════════════════════════════════════════════════════

/**
 * Strips domain suffixes from an mDNS server name, returning just the hostname.
 * Handles standard {@code .local.} as well as custom domains (e.g. {@code .winks.casa}).
 * mDNS hostnames for Shelly devices never contain dots, so everything from
 * the first dot onward is a domain suffix.
 *
 * @param serverName The raw mDNS server name (e.g. {@code shellymotion2-2c1165cb0429.winks.casa})
 * @return The bare hostname (e.g. {@code shellymotion2-2c1165cb0429}), or the input unchanged if no dot found
 */
@CompileStatic
static String stripMdnsDomainSuffix(String serverName) {
    if (!serverName) { return serverName }
    Integer dotIndex = serverName.indexOf('.')
    return (dotIndex > 0) ? serverName.substring(0, dotIndex) : serverName
}


/**
 * Determines whether a discovered device is likely a Gen 1 Shelly based on mDNS TXT fields.
 * Gen 1 devices register under {@code _http._tcp} without {@code gen} or {@code app} TXT records,
 * but their hostname contains "shelly".
 *
 * @param gen The gen TXT record value (empty for Gen 1)
 * @param deviceApp The app TXT record value (empty for Gen 1)
 * @param serverName The mDNS server/hostname
 * @return true if the device appears to be Gen 1
 */
@CompileStatic
static Boolean isLikelyGen1Device(String gen, String deviceApp, String serverName) {
    return !gen && !deviceApp && serverName?.toLowerCase()?.contains('shelly')
}

/**
 * Extracts the Gen 1 device type code from an mDNS hostname.
 * Gen 1 hostnames follow the pattern {@code <model-prefix>-<MAC>},
 * where MAC is either 6 hex chars (3 bytes) or 12 hex chars (6 bytes).
 * Examples: {@code shellyht-AABBCC}, {@code shelly1pm-AABBCCDDEEFF},
 * {@code shellyplug-s-AABBCC}.
 *
 * @param hostname The mDNS hostname (with or without .local. suffix)
 * @return The Gen 1 type code (e.g. {@code SHHT-1}), or null if not recognized
 */
@CompileStatic
static String extractGen1TypeFromHostname(String hostname) {
    if (!hostname) { return null }
    String lower = stripMdnsDomainSuffix(hostname).toLowerCase()
    Integer lastDash = lower.lastIndexOf('-')
    if (lastDash < 0 || lastDash >= lower.length() - 1) { return null }
    String maybeMac = lower.substring(lastDash + 1)
    if ((maybeMac.length() == 6 || maybeMac.length() == 12) && maybeMac.matches(/^[0-9a-f]+$/)) {
        String prefix = lower.substring(0, lastDash)
        return GEN1_HOSTNAME_TO_TYPE.get(prefix)
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
        logTrace("watchdogScan: skipping, last scan was ${(now() - lastScan) / 1000} seconds ago (cooldown 300s)")
        return
    }
    state.lastWatchdogScan = now()
    logTrace('watchdogScan: starting mDNS scan for IP changes')

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
    logTrace('watchdogProcessResults: checking for IP changes')

    try {
        List<Map<String, Object>> shellyEntries = getMDNSEntries('_shelly._tcp')
        List<Map<String, Object>> httpEntries = getMDNSEntries('_http._tcp')

        List allEntries = []
        if (shellyEntries) { allEntries.addAll(shellyEntries) }
        if (httpEntries) { allEntries.addAll(httpEntries) }

        if (!allEntries) {
            logTrace('watchdogProcessResults: no mDNS entries found')
            return
        }

        // Build hostname→MAC lookup from discoveredShellys (authoritative MAC from /shelly probe)
        Map<String, String> hostnameToMac = [:]
        (state.discoveredShellys as Map)?.each { String ip, Object val ->
            Map entry = val as Map
            String name = stripMdnsDomainSuffix(entry.name?.toString() ?: '')
            String entryMac = entry.mac?.toString()
            if (name && entryMac) { hostnameToMac[name] = entryMac }
        }

        Integer updatedCount = 0
        allEntries.each { entry ->
            String server = (entry?.server ?: '') as String

            // Defensive parsing: ip4Addresses may be String or List<String>
            Object rawIp4 = entry?.ip4Addresses
            String ip4 = ''
            if (rawIp4 instanceof List) {
                ip4 = rawIp4.find { it && !it.toString().contains(':') }?.toString() ?: ''
            } else if (rawIp4) {
                ip4 = rawIp4.toString().replaceAll(/[\[\]]/, '').trim()
            }
            if (!server || !ip4) { return }

            String hostname = stripMdnsDomainSuffix(server)
            String mac = hostnameToMac[hostname]
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
            logTrace('watchdogProcessResults: all device IPs are current')
        }
    } catch (Exception e) {
        logWarn("watchdogProcessResults: error processing mDNS entries: ${e.message}")
    }
}

// ═══════════════════════════════════════════════════════════════
// Device Info / Config / Status Fetching (Gen2+ RPC over HTTP)
// ═══════════════════════════════════════════════════════════════

/**
 * Sends a JSON-RPC command to a Shelly device with retry logic.
 * Retries up to 3 times with increasing delays (2s, 4s) between attempts.
 * Logs failures at debug level for retries and error level only on final failure.
 *
 * @param command The JSON-RPC command map to send
 * @param uri The target device RPC URI
 * @param commandName Human-readable command name for logging
 * @param maxRetries Maximum number of attempts (default 3)
 * @return The response LinkedHashMap, or null if all attempts failed
 */
private LinkedHashMap postCommandSyncWithRetry(LinkedHashMap command, String uri, String commandName, int maxRetries = 3) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            LinkedHashMap result = postCommandSync(command, uri)
            if (attempt > 1) {
                logDebug("${commandName} succeeded on attempt ${attempt} for ${uri}")
            }
            return result
        } catch (Exception e) {
            if (attempt < maxRetries) {
                int delaySecs = attempt * 2
                logDebug("${commandName} attempt ${attempt}/${maxRetries} failed for ${uri}: ${e.message} — retrying in ${delaySecs}s")
                pauseExecution(delaySecs * 1000)
            } else {
                logError("${commandName} failed after ${maxRetries} attempts for ${uri}: ${e.message}")
            }
        }
    }
    return null
}

// ═══════════════════════════════════════════════════════════════
// Gen 1 REST HTTP Communication
// ═══════════════════════════════════════════════════════════════

/**
 * Sends an HTTP GET request to a Gen 1 Shelly device and returns the parsed JSON response.
 * Handles HTTP Basic Auth when the device has authentication enabled.
 * Includes retry logic with increasing delays between attempts.
 *
 * @param ipAddress The device IP address
 * @param path The URL path (e.g. {@code "relay/0"}, {@code "settings"}, {@code "status"})
 * @param queryParams Optional query parameters (e.g. {@code [turn: "on"]})
 * @param maxRetries Maximum number of attempts (default 2)
 * @return The parsed JSON response map, or null if all attempts failed
 */
private Map sendGen1Get(String ipAddress, String path, Map queryParams = [:], int maxRetries = 2) {
    String queryString = queryParams.collect { k, v -> "${k}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join('&')
    String uri = "http://${ipAddress}/${path}"
    if (queryString) { uri += "?${queryString}" }

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            Map result = null
            Map params = [uri: uri, timeout: 10, contentType: 'application/json']

            // Add Basic Auth if device password is configured
            // Gen 1 devices always use username 'admin'
            if (authIsEnabledGen1()) {
                String credentials = "admin:${getAppSettings()?.devicePassword}".toString()
                String encoded = credentials.bytes.encodeBase64().toString()
                params.headers = ['Authorization': "Basic ${encoded}"]
            }

            httpGetHelper(params) { resp ->
                if (resp?.status == 200 && resp.data) {
                    result = resp.data as Map
                }
            }
            if (attempt > 1) {
                logDebug("Gen 1 GET ${path} succeeded on attempt ${attempt} for ${ipAddress}")
            }
            return result
        } catch (Exception e) {
            if (attempt < maxRetries) {
                int delaySecs = attempt * 2
                logDebug("Gen 1 GET ${path} attempt ${attempt}/${maxRetries} failed for ${ipAddress}: ${e.message} — retrying in ${delaySecs}s")
                pauseExecution(delaySecs * 1000)
            } else {
                logError("Gen 1 GET ${path} failed after ${maxRetries} attempts for ${ipAddress}: ${e.message}")
            }
        }
    }
    return null
}

/**
 * Sends an HTTP GET request to configure a Gen 1 Shelly device setting.
 * Gen 1 settings are configured via GET requests with query parameters
 * (e.g. {@code /settings/relay/0?out_on_url=http://...}).
 *
 * @param ipAddress The device IP address
 * @param path The settings URL path (e.g. {@code "settings/relay/0"})
 * @param params Settings parameters as key-value pairs
 * @return The parsed JSON response map, or null on failure
 */
private Map sendGen1Setting(String ipAddress, String path, Map params = [:]) {
    return sendGen1Get(ipAddress, path, params, 2)
}

// ═══════════════════════════════════════════════════════════════
// Gen 1 Status Polling
// ═══════════════════════════════════════════════════════════════

/**
 * Polls a single Gen 1 device for current status and distributes data to its driver.
 * Queries {@code GET /status} on the device, normalizes the response to the internal
 * component format, and calls {@code distributeStatus()} on the driver.
 *
 * @param ipAddress The IP address of the Gen 1 Shelly device
 */
private void pollGen1DeviceStatus(String ipAddress) {
    Map deviceInfo = state.discoveredShellys?.get(ipAddress)
    if (!deviceInfo) {
        logDebug("pollGen1DeviceStatus: no device info for ${ipAddress}")
        return
    }

    String typeCode = deviceInfo.gen1Type?.toString() ?: ''
    Map gen1Settings = deviceInfo.gen1Settings as Map ?: [:]

    // Query device status
    Map gen1Status = sendGen1Get(ipAddress, 'status', [:], 1)
    if (!gen1Status) {
        logDebug("pollGen1DeviceStatus: device at ${ipAddress} did not respond")
        return
    }

    // Normalize to internal component format
    Map normalizedStatus = normalizeGen1Status(gen1Status, gen1Settings, typeCode)
    if (!normalizedStatus) {
        logDebug("pollGen1DeviceStatus: empty normalized status for ${ipAddress}")
        return
    }

    // Find the child device and distribute status
    def childDevice = findChildDeviceByIp(ipAddress)
    if (childDevice) {
        try {
            childDevice.distributeStatus(normalizedStatus)
        } catch (Exception e) {
            logError("pollGen1DeviceStatus: error distributing status to ${childDevice.displayName}: ${e.message}")
        }
    }
}

/**
 * Syncs Gen 1 Motion sensor configuration to driver preferences.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * Only applies to SHMOS-01 and SHMOS-02 devices.
 *
 * @param ipAddress The device IP address
 * @param childDevice The child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (avoids race with backfill)
 */
private void syncGen1MotionSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice) { return }
    if (gen1Type != 'SHMOS-01' && gen1Type != 'SHMOS-02') { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1MotionSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1MotionSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        // Motion settings are nested under 'motion' object in the response
        Map motionSettings = gen1Settings.motion as Map ?: [:]

        if (motionSettings.sensitivity != null) {
            childDevice.updateSetting('motionSensitivity',
                [type: 'number', value: motionSettings.sensitivity as Integer])
        }
        if (motionSettings.blind_time_minutes != null) {
            childDevice.updateSetting('motionBlindTimeMinutes',
                [type: 'number', value: motionSettings.blind_time_minutes as Integer])
        }
        if (gen1Settings.tamper_sensitivity != null) {
            childDevice.updateSetting('tamperSensitivity',
                [type: 'number', value: gen1Settings.tamper_sensitivity as Integer])
        }
        if (gen1Settings.led_status_disable != null) {
            childDevice.updateSetting('ledStatusDisable',
                [type: 'bool', value: gen1Settings.led_status_disable as Boolean])
        }
        if (gen1Settings.sleep_time != null) {
            childDevice.updateSetting('sleepTime',
                [type: 'number', value: gen1Settings.sleep_time as Integer])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Motion settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1MotionSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs device-side motion/sensor settings to driver preferences on first refresh.
 * Reads /settings from the Shelly Sense and populates motion sensitivity and
 * blind time preferences with the device values.
 * The Sense is always awake, so live fetch should always succeed.
 *
 * @param ipAddress The Sense device's IP address
 * @param childDevice The Sense child device
 * @param gen1Type The Gen 1 type code (must be SHSN-1)
 */
private void syncGen1SenseSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || gen1Type != 'SHSN-1') { return }

    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (!gen1Settings) {
            logDebug("syncGen1SenseSettings: no settings available for ${ipAddress}")
            return
        }
    }

    try {
        Map motionSettings = gen1Settings.motion as Map ?: [:]
        if (motionSettings.sensitivity != null) {
            childDevice.updateSetting('motionSensitivity',
                [type: 'number', value: motionSettings.sensitivity as Integer])
        }
        if (motionSettings.blind_time_minutes != null) {
            childDevice.updateSetting('motionBlindTimeMinutes',
                [type: 'number', value: motionSettings.blind_time_minutes as Integer])
        }
        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Sense settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1SenseSettings: failed for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs device-side thermostat settings to driver preferences on first refresh.
 * Reads /settings/thermostats/0 from the TRV and populates the driver's
 * temperatureOffset preference with the device value.
 *
 * @param ipAddress The TRV's IP address
 * @param childDevice The TRV child device
 * @param gen1Type The Gen 1 type code (must be SHTRV-01)
 */
private void syncGen1TrvSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice) { return }
    if (gen1Type != 'SHTRV-01') { return }

    // TRV is always awake, so live fetch should succeed
    Map trvSettings = sendGen1Get(ipAddress, 'settings/thermostats/0', [:], 2)
    if (!trvSettings) {
        logDebug("syncGen1TrvSettings: could not read settings from ${ipAddress}")
        return
    }

    try {
        if (trvSettings.temperature_offset != null) {
            childDevice.updateSetting('temperatureOffset',
                [type: 'decimal', value: trvSettings.temperature_offset as BigDecimal])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 TRV settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1TrvSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 Button device configuration to driver preferences on first refresh.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * Only applies to SHBTN-1 and SHBTN-2 devices.
 *
 * @param ipAddress The Button device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (SHBTN-1 or SHBTN-2)
 */
private void syncGen1ButtonSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice) { return }
    if (gen1Type != 'SHBTN-1' && gen1Type != 'SHBTN-2') { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1ButtonSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1ButtonSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        if (gen1Settings.longpush_duration_ms != null) {
            childDevice.updateSetting('longpushDurationMs',
                [type: 'number', value: gen1Settings.longpush_duration_ms as Integer])
        }
        if (gen1Settings.multipush_time_between_pushes_ms != null) {
            childDevice.updateSetting('multipushTimeBetweenPushesMs',
                [type: 'number', value: gen1Settings.multipush_time_between_pushes_ms as Integer])
        }
        if (gen1Settings.led_status_disable != null) {
            childDevice.updateSetting('ledStatusDisable',
                [type: 'bool', value: gen1Settings.led_status_disable.toString() == 'true'])
        }
        if (gen1Settings.remain_awake != null) {
            childDevice.updateSetting('remainAwake',
                [type: 'bool', value: gen1Settings.remain_awake.toString() == 'true'])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Button settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1ButtonSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 Flood sensor configuration to driver preferences on first refresh.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * Only applies to SHWT-1 devices.
 *
 * @param ipAddress The Flood device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (must be SHWT-1)
 */
private void syncGen1FloodSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || gen1Type != 'SHWT-1') { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1FloodSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1FloodSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        if (gen1Settings.temperature_offset != null) {
            childDevice.updateSetting('temperatureOffset',
                [type: 'decimal', value: gen1Settings.temperature_offset as BigDecimal])
        }
        if (gen1Settings.temperature_threshold != null) {
            childDevice.updateSetting('temperatureThreshold',
                [type: 'decimal', value: gen1Settings.temperature_threshold as BigDecimal])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Flood settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1FloodSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 Door/Window sensor configuration to driver preferences on first refresh.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * SHDW-2 has additional settings (dark/twilight thresholds, vibration sensitivity,
 * temperature offset/threshold, lux wakeup) that SHDW-1 lacks.
 *
 * @param ipAddress The DW device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (must be SHDW-1 or SHDW-2)
 */
private void syncGen1DWSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || (gen1Type != 'SHDW-1' && gen1Type != 'SHDW-2')) { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1DWSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1DWSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        // SHDW-2 only settings
        if (gen1Type == 'SHDW-2') {
            if (gen1Settings.dark_threshold != null) {
                childDevice.updateSetting('darkThreshold',
                    [type: 'number', value: gen1Settings.dark_threshold as Integer])
            }
            if (gen1Settings.twilight_threshold != null) {
                childDevice.updateSetting('twilightThreshold',
                    [type: 'number', value: gen1Settings.twilight_threshold as Integer])
            }
            if (gen1Settings.vibration_sensitivity != null) {
                childDevice.updateSetting('vibrationSensitivity',
                    [type: 'enum', value: (gen1Settings.vibration_sensitivity as Integer).toString()])
            }
            if (gen1Settings.temperature_offset != null) {
                childDevice.updateSetting('temperatureOffset',
                    [type: 'decimal', value: gen1Settings.temperature_offset as BigDecimal])
            }
            if (gen1Settings.temperature_threshold != null) {
                childDevice.updateSetting('temperatureThreshold',
                    [type: 'decimal', value: gen1Settings.temperature_threshold as BigDecimal])
            }
            if (gen1Settings.lux_wakeup_enable != null) {
                childDevice.updateSetting('luxWakeupEnable',
                    [type: 'bool', value: gen1Settings.lux_wakeup_enable == true || gen1Settings.lux_wakeup_enable == 1])
            }
        }
        // Both SHDW-1 and SHDW-2
        if (gen1Settings.led_status_disable != null) {
            childDevice.updateSetting('ledStatusDisable',
                [type: 'bool', value: gen1Settings.led_status_disable == true])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 DW settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1DWSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 Smoke sensor configuration to driver preferences on first refresh.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * Only applies to SHSM-01 devices.
 *
 * @param ipAddress The Smoke device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (must be SHSM-01)
 */
private void syncGen1SmokeSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || gen1Type != 'SHSM-01') { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1SmokeSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1SmokeSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        if (gen1Settings.temperature_offset != null) {
            childDevice.updateSetting('temperatureOffset',
                [type: 'decimal', value: gen1Settings.temperature_offset as BigDecimal])
        }
        if (gen1Settings.temperature_threshold != null) {
            childDevice.updateSetting('temperatureThreshold',
                [type: 'decimal', value: gen1Settings.temperature_threshold as BigDecimal])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Smoke settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1SmokeSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 Gas sensor configuration to driver preferences on first refresh.
 * The Gas sensor is mains-powered and always reachable, so live fetch should
 * always succeed. Falls back to cached settings from discovery if needed.
 * Currently syncs valve default state if a valve is connected.
 * Only applies to SHGS-1 devices.
 *
 * @param ipAddress The Gas sensor device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (must be SHGS-1)
 */
private void syncGen1GasSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || gen1Type != 'SHGS-1') { return }

    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (!gen1Settings) {
            logDebug("syncGen1GasSettings: no settings available for ${ipAddress}")
            return
        }
    }

    try {
        // Sync valve default state if valve is present
        List valves = gen1Settings.valves as List
        if (valves && valves.size() > 0) {
            Map valveSettings = valves[0] as Map ?: [:]
            if (valveSettings.default_state != null) {
                childDevice.updateSetting('valveDefaultState',
                    [type: 'enum', value: valveSettings.default_state.toString()])
            }
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 Gas settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1GasSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Syncs Gen 1 H&T device configuration to driver preferences on first refresh.
 * Attempts a live {@code GET /settings} first; if the device is asleep,
 * falls back to the cached {@code gen1Settings} from discovery in
 * {@code state.discoveredShellys}. Sets {@code gen1SettingsSynced}
 * data value on success so subsequent refreshes skip the extra HTTP call.
 * Only applies to SHHT-1 devices.
 *
 * @param ipAddress The H&T device's IP address
 * @param childDevice The Hubitat child device whose preferences to sync
 * @param gen1Type The resolved Gen 1 type code (must be SHHT-1)
 */
private void syncGen1HTSettings(String ipAddress, def childDevice, String gen1Type) {
    if (!childDevice || gen1Type != 'SHHT-1') { return }

    // Try live fetch first; fall back to cached discovery data if device is asleep
    Map gen1Settings = sendGen1Get(ipAddress, 'settings', [:], 1)
    if (!gen1Settings) {
        Map deviceInfo = state.discoveredShellys?.get(ipAddress)
        gen1Settings = deviceInfo?.gen1Settings as Map
        if (gen1Settings) {
            logDebug("syncGen1HTSettings: device asleep, using cached settings from discovery")
        } else {
            logDebug("syncGen1HTSettings: device asleep and no cached settings for ${ipAddress}")
            return
        }
    }

    try {
        if (gen1Settings.temperature_offset != null) {
            childDevice.updateSetting('temperatureOffset',
                [type: 'decimal', value: gen1Settings.temperature_offset as BigDecimal])
        }
        if (gen1Settings.humidity_offset != null) {
            childDevice.updateSetting('humidityOffset',
                [type: 'decimal', value: gen1Settings.humidity_offset as BigDecimal])
        }
        if (gen1Settings.temperature_threshold != null) {
            childDevice.updateSetting('temperatureThreshold',
                [type: 'decimal', value: gen1Settings.temperature_threshold as BigDecimal])
        }
        if (gen1Settings.humidity_threshold != null) {
            childDevice.updateSetting('humidityThreshold',
                [type: 'decimal', value: gen1Settings.humidity_threshold as BigDecimal])
        }

        childDevice.updateDataValue('gen1SettingsSynced', 'true')
        logDebug("Synced Gen 1 H&T settings from device at ${ipAddress}")
    } catch (Exception e) {
        logWarn("syncGen1HTSettings: failed to sync settings for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Polls all non-battery Gen 1 devices for current status.
 * Called on a schedule based on the user-configured Gen 1 polling interval.
 * Battery devices are skipped (they sleep and are polled on wake-up via action URL callbacks).
 */
void pollGen1Devices() {
    List childDevices = getChildDevices() ?: []
    List gen1Devices = childDevices.findAll { isGen1Device(it) }

    if (!gen1Devices) { return }

    Integer polled = 0
    gen1Devices.each { def dev ->
        String ip = dev.getDataValue('ipAddress')
        if (!ip) { return }

        // Skip battery devices — status comes from webhook pushes, not polling
        if (isBatteryPoweredDevice(dev)) { return }

        pollGen1DeviceStatus(ip)
        polled++
    }

    if (polled > 0) {
        logDebug("pollGen1Devices: polled ${polled} Gen 1 device(s)")
    }
}

/**
 * Schedules periodic Gen 1 device polling based on user-configured interval.
 * Only schedules if Gen 1 non-battery devices exist. Unschedules if no Gen 1 devices found.
 */
private void scheduleGen1Polling() {
    List childDevices = getChildDevices() ?: []
    Boolean hasGen1NonBattery = childDevices.any { isGen1Device(it) && !isBatteryPoweredDevice(it) }

    if (hasGen1NonBattery) {
        Integer intervalSec = (settings?.gen1PollInterval ?: '60') as Integer
        String cronExpr
        if (intervalSec < 60) {
            cronExpr = "0/${intervalSec} * * ? * *"
        } else {
            Integer intervalMin = intervalSec / 60 as Integer
            cronExpr = "0 0/${intervalMin} * ? * *"
        }
        schedule(cronExpr, 'pollGen1Devices')
        logDebug("Gen 1 polling scheduled every ${intervalSec}s")
    } else {
        unschedule('pollGen1Devices')
    }
}

// ═══════════════════════════════════════════════════════════════
// Generation Detection Helpers
// ═══════════════════════════════════════════════════════════════

/**
 * Checks if a child device is a Gen 1 Shelly based on its stored data value.
 *
 * @param childDevice The Hubitat child device to check
 * @return true if the device is Gen 1
 */
private Boolean isGen1Device(def childDevice) {
    return childDevice?.getDataValue('shellyGen') == '1'
}

/**
 * Checks if a device at a given IP address is Gen 1 based on discovery data.
 *
 * @param ipAddress The device IP address
 * @return true if the device at this IP is Gen 1
 */
private Boolean isGen1DeviceByIp(String ipAddress) {
    Map device = state.discoveredShellys?.get(ipAddress)
    return device?.gen?.toString() == '1'
}

/**
 * Returns the Shelly generation string for a child device.
 *
 * @param childDevice The Hubitat child device
 * @return The generation string ({@code "1"}, {@code "2"}, {@code "3"}), or {@code "2"} as default
 */
private String getDeviceGen(def childDevice) {
    return childDevice?.getDataValue('shellyGen') ?: '2'
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Async Device Info Fetching                                   ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Schedules an asynchronous device info fetch for a discovered device.
 * This prevents blocking the app page load while waiting for sleepy battery devices
 * that may timeout during discovery. Uses staggered delays to avoid overwhelming
 * the network with simultaneous requests.
 *
 * @param ipKey The IP address key identifying the device in discoveredShellys map
 */
private void scheduleAsyncDeviceInfoFetch(String ipKey) {
    if (!ipKey) { return }

    // Initialize async fetch queue if needed
    if (!state.asyncFetchQueue) {
        state.asyncFetchQueue = [:] as Map
    }

    // Check if this IP is already queued or in progress
    if (state.asyncFetchQueue[ipKey]) {
        logDebug("Async fetch already queued for ${ipKey}")
        return
    }

    // Mark as queued
    state.asyncFetchQueue[ipKey] = [
        status: 'queued',
        queuedAt: now(),
        attempts: 0
    ]

    // Calculate staggered delay (100ms per queued device to avoid network flooding)
    Integer queueSize = state.asyncFetchQueue.size()
    Integer delayMs = 100 + (queueSize * 100)

    logDebug("Scheduling async device info fetch for ${ipKey} in ${delayMs}ms")
    runInMillis(delayMs, 'processAsyncDeviceInfoFetch', [data: [ipKey: ipKey]])
}

/**
 * Callback handler for async device info fetch.
 * Called by runInMillis after scheduled delay. Fetches device info in the background
 * and updates discovery state. If the fetch fails due to timeout (sleepy device),
 * marks it as unreachable but doesn't retry to avoid blocking.
 *
 * @param data Map containing ipKey
 */
void processAsyncDeviceInfoFetch(Map data) {
    String ipKey = data?.ipKey
    if (!ipKey) { return }

    logDebug("Processing async device info fetch for ${ipKey}")

    // Update queue status
    Map queueEntry = state.asyncFetchQueue[ipKey] as Map
    if (queueEntry) {
        queueEntry.status = 'in_progress'
        queueEntry.startedAt = now()
        state.asyncFetchQueue[ipKey] = queueEntry
    }

    try {
        // Attempt to fetch device info (with retries built into fetchAndStoreDeviceInfo)
        fetchAndStoreDeviceInfo(ipKey)

        // Mark as completed
        if (queueEntry) {
            queueEntry.status = 'completed'
            queueEntry.completedAt = now()
            state.asyncFetchQueue[ipKey] = queueEntry
        }

        logDebug("Async fetch completed for ${ipKey}")

    } catch (Exception e) {
        // Log but don't block on errors (likely sleepy device or network timeout)
        logDebug("Async fetch failed for ${ipKey}: ${e.message}")

        if (queueEntry) {
            queueEntry.status = 'failed'
            queueEntry.failedAt = now()
            queueEntry.error = e.message
            state.asyncFetchQueue[ipKey] = queueEntry
        }
    }

    // Clean up old queue entries (keep last 50)
    cleanupAsyncFetchQueue()
}

/**
 * Cleans up old async fetch queue entries to prevent state bloat.
 * Keeps only the 50 most recent entries.
 */
private void cleanupAsyncFetchQueue() {
    Map queue = state.asyncFetchQueue as Map
    if (!queue || queue.size() <= 50) { return }

    // Sort by timestamp (most recent first)
    List<Map.Entry> entries = queue.entrySet().toList()
    entries.sort { Map.Entry a, Map.Entry b ->
        Long aTime = (a.value as Map)?.queuedAt as Long ?: 0L
        Long bTime = (b.value as Map)?.queuedAt as Long ?: 0L
        return bTime <=> aTime  // Descending (newest first)
    }

    // Keep only the 50 most recent
    Map cleaned = [:] as Map
    entries.take(50).each { Map.Entry entry ->
        cleaned[entry.key] = entry.value
    }

    state.asyncFetchQueue = cleaned
    logDebug("Cleaned async fetch queue: kept ${cleaned.size()} entries")
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Async Device Info Fetching                               ║
// ╚══════════════════════════════════════════════════════════════╝

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

    // Lightweight /shelly probe — works for both Gen1 and Gen2+, no auth required.
    // Determines generation and provides MAC/model for routing.
    // Also re-probe when gen='1' was set heuristically (no MAC yet) to self-correct
    // if the device turns out to be Gen2+ once it responds.
    if (!device.gen || (device.gen?.toString() == '1' && !device.mac)) {
        Map shellyProbe = null
        try {
            httpGetHelper([uri: "http://${ip}/shelly", timeout: 5, contentType: 'application/json']) { resp ->
                if (resp?.status == 200 && resp.data) { shellyProbe = resp.data as Map }
            }
        } catch (Exception e) {
            logDebug("fetchAndStoreDeviceInfo: /shelly probe failed for ${ip}: ${e.message}")
        }

        if (shellyProbe) {
            if (shellyProbe.gen) {
                // Gen 2+: has 'gen' field
                device.gen = shellyProbe.gen.toString()
            } else if (shellyProbe.type) {
                // Gen 1: has 'type' but no 'gen'
                device.gen = '1'
            }
            if (shellyProbe.mac) { device.mac = shellyProbe.mac.toString().toUpperCase() }
            state.discoveredShellys[ipKey] = device
        }
    }

    // Route based on generation determined by /shelly probe or mDNS TXT records
    if (device.gen?.toString() == '1') {
        logDebug("fetchAndStoreDeviceInfo: Gen 1 device at ${ip}, using REST API")
        appendLog('debug', "Getting Gen 1 device info from ${ip}")
        if (fetchGen1DeviceInfo(ipKey, device)) {
            debounceSendFoundShellyEvents()
        }
        return
    }

    String rpcUri = (port == 80) ? "http://${ip}/rpc" : "http://${ip}:${port}/rpc"

    logDebug("fetchAndStoreDeviceInfo: fetching from ${rpcUri}")
    appendLog('debug', "Getting device info from ${ip}")

    try {
        // Query device info, config, and status with retry logic
        LinkedHashMap deviceInfoCmd = shellyGetDeviceInfoCommand(true, 'discovery')
        LinkedHashMap deviceInfoResp = postCommandSyncWithRetry(deviceInfoCmd, rpcUri, "Shelly.GetDeviceInfo")
        Map deviceInfo = (deviceInfoResp instanceof Map && deviceInfoResp.containsKey('result')) ? deviceInfoResp.result : deviceInfoResp
        if (!deviceInfo) {
            appendLog('warn', "No device info returned from ${ip} — device may be offline or not Gen2+")
            return
        }

        LinkedHashMap configCmd = shellyGetConfigCommand('discovery')
        LinkedHashMap deviceConfigResp = postCommandSyncWithRetry(configCmd, rpcUri, "Shelly.GetConfig")
        Map deviceConfig = (deviceConfigResp instanceof Map && deviceConfigResp.containsKey('result')) ? deviceConfigResp.result : deviceConfigResp

        LinkedHashMap statusCmd = shellyGetStatusCommand('discovery')
        LinkedHashMap deviceStatusResp = postCommandSyncWithRetry(statusCmd, rpcUri, "Shelly.GetStatus")
        Map deviceStatus = (deviceStatusResp instanceof Map && deviceStatusResp.containsKey('result')) ? deviceStatusResp.result : deviceStatusResp

        // Query supported webhook events for filtering required actions
        LinkedHashMap webhookSupportedCmd = webhookListSupportedCommand('discovery')
        LinkedHashMap webhookSupportedResp = postCommandSyncWithRetry(webhookSupportedCmd, rpcUri, "Webhook.ListSupported")
        List<String> supportedWebhookEvents = []
        if (webhookSupportedResp?.result?.types) {
            supportedWebhookEvents = (webhookSupportedResp.result.types as Map).keySet().collect { it.toString() }
            logDebug("fetchAndStoreDeviceInfo: supported webhook events: ${supportedWebhookEvents}")
        }
        device.supportedWebhookEvents = supportedWebhookEvents

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


        debounceSendFoundShellyEvents()
        determineDeviceDriver(deviceStatus, ipKey)
    } catch (Exception e) {
        String errorMsg = e.message ?: e.toString() ?: e.class.simpleName
        logDebug("fetchAndStoreDeviceInfo exception: ${e.class.name}: ${errorMsg}")
        if (e.stackTrace && e.stackTrace.length > 0) {
            logDebug("fetchAndStoreDeviceInfo stack trace (first 3 lines): ${e.stackTrace.take(3).join(' | ')}")
        }

        // Gen 1 fallback: if RPC failed, try Gen 1 REST API
        if (isLikelyGen1Device(device.gen?.toString() ?: '', device.deviceApp?.toString() ?: '', device.name?.toString() ?: '')) {
            // Persist gen='1' before attempting fetch — sleepy devices may not respond,
            // but the badge should still display correctly on the Discovery page.
            device.gen = '1'
            state.discoveredShellys[ipKey] = device
            logDebug("fetchAndStoreDeviceInfo: RPC failed for ${ip}, trying Gen 1 REST API")
            if (fetchGen1DeviceInfo(ipKey, device)) {
                debounceSendFoundShellyEvents()
                return
            }

            // Last resort: hostname-based identification for sleeping/unreachable Gen 1 devices
            String deviceName = (device.name ?: '').toString()
            String gen1Type = extractGen1TypeFromHostname(deviceName)
            if (gen1Type && !device.model) {
                String typeKey = gen1Type.toString()
                device.gen = '1'
                device.gen1Type = typeKey
                device.model = GEN1_TYPE_TO_MODEL.get(typeKey) ?: typeKey
                device.isBatteryDevice = GEN1_BATTERY_TYPES.contains(typeKey)
                // NOTE: Do NOT set mac from hostname — mac comes from /shelly probe only

                // Infer components for battery devices so driver can be determined
                if (device.isBatteryDevice) {
                    Map syntheticStatus = inferGen1BatteryComponents(typeKey)
                    if (syntheticStatus) {
                        device.deviceStatus = syntheticStatus
                    }
                }

                state.discoveredShellys[ipKey] = device
                appendLog('info', "Gen 1 identified from hostname (device unreachable): ${deviceName} -> ${device.model}")

                if (device.deviceStatus) {
                    determineDeviceDriver(device.deviceStatus, ipKey)
                    debounceSendFoundShellyEvents()
                }
                return
            }
        }

        appendLog('error', "Failed to get device info from ${ip}: ${errorMsg}")
    }
}

/**
 * Fetches device information from a Gen 1 Shelly device via its REST API.
 * Queries three endpoints in sequence:
 * <ol>
 *   <li>{@code /shelly} — device identity (no auth required)</li>
 *   <li>{@code /settings} — device configuration including mode (relay/roller)</li>
 *   <li>{@code /status} — current component states, power, temperature</li>
 * </ol>
 * After fetching, normalizes the Gen 1 status into internal component format
 * and calls {@link #determineDeviceDriver} to select the appropriate driver.
 *
 * @param ipKey The IP address key in discoveredShellys
 * @param device The mutable device map from discoveredShellys
 * @return true if Gen 1 info was successfully fetched, false otherwise
 */
private Boolean fetchGen1DeviceInfo(String ipKey, Map device) {
    String ip = (device.ipAddress ?: ipKey).toString()
    logDebug("fetchGen1DeviceInfo: querying http://${ip}/shelly")

    try {
        // Step 1: /shelly — device identity (always unauthenticated)
        Map shellyInfo = null
        httpGetHelper([uri: "http://${ip}/shelly", timeout: 5, contentType: 'application/json']) { resp ->
            if (resp?.status == 200 && resp.data) {
                shellyInfo = resp.data as Map
            }
        }

        if (!shellyInfo) {
            logDebug("fetchGen1DeviceInfo: no response from ${ip}")
            return false
        }

        // If response contains 'gen' field, it's actually Gen 2+ — not Gen 1
        if (shellyInfo.gen) {
            logDebug("fetchGen1DeviceInfo: ${ip} returned gen=${shellyInfo.gen}, not a Gen 1 device")
            return false
        }

        // Gen 1 /shelly response: {"type":"SHSW-1","mac":"AABBCCDDEEFF","auth":true,"fw":"...","discoverable":true}
        String typeCode = shellyInfo.type?.toString()
        if (!typeCode) {
            logDebug("fetchGen1DeviceInfo: ${ip} has no type field in /shelly response")
            return false
        }

        device.gen = '1'
        device.gen1Type = typeCode
        device.model = GEN1_TYPE_TO_MODEL.get(typeCode) ?: typeCode
        device.isBatteryDevice = GEN1_BATTERY_TYPES.contains(typeCode)
        if (shellyInfo.mac) { device.mac = shellyInfo.mac.toString().toUpperCase() }
        if (shellyInfo.fw) { device.ver = shellyInfo.fw.toString() }
        if (shellyInfo.auth != null) { device.auth_en = shellyInfo.auth }

        // Step 2: /settings — device configuration (may require auth)
        Map gen1Settings = sendGen1Get(ip, 'settings', [:], 1)
        if (!gen1Settings) {
            logDebug("fetchGen1DeviceInfo: /settings query returned no data for ${ip}")
        }

        // Step 3: /status — current device state (may require auth)
        Map gen1Status = null
        if (!device.isBatteryDevice) {
            // Battery devices are usually asleep — skip /status to avoid timeout
            gen1Status = sendGen1Get(ip, 'status', [:], 1)
            if (!gen1Status) {
                logDebug("fetchGen1DeviceInfo: /status query returned no data for ${ip}")
            }
        }

        // Store raw Gen 1 data for downstream use
        if (gen1Settings) { device.gen1Settings = gen1Settings }
        if (gen1Status) { device.gen1Status = gen1Status }

        // Normalize Gen 1 status into internal component format (switch:0, cover:0, etc.)
        if (gen1Status || gen1Settings) {
            Map normalizedStatus = normalizeGen1Status(gen1Status ?: [:], gen1Settings ?: [:], typeCode)
            if (normalizedStatus) {
                device.deviceStatus = normalizedStatus
                logDebug("fetchGen1DeviceInfo: normalized status for ${ip}: ${normalizedStatus.keySet()}")
            }
        }

        // Battery devices may have been asleep during discovery — infer components from type code
        if (!device.deviceStatus && device.isBatteryDevice) {
            Map syntheticStatus = inferGen1BatteryComponents(typeCode)
            if (syntheticStatus) {
                device.deviceStatus = syntheticStatus
                logDebug("fetchGen1DeviceInfo: inferred battery components for ${ip}: ${syntheticStatus.keySet()}")
            }
        }

        device.ts = now()
        state.discoveredShellys[ipKey] = device

        // Update the cache entry
        Map cache = state.deviceStatusCache ?: [:]
        if (cache.containsKey(ip)) {
            Map cacheEntry = cache[ip] as Map
            if (device.mac) { cacheEntry.mac = device.mac.toString() }
            if (device.model) { cacheEntry.model = device.model.toString() }
            cacheEntry.isBatteryDevice = device.isBatteryDevice ?: false
            cache[ip] = cacheEntry
            state.deviceStatusCache = cache
        }

        appendLog('info', "Gen 1 device info from ${ip}: ${device.model} (${typeCode}), mac=${device.mac ?: 'n/a'}, fw=${device.ver ?: 'n/a'}")
        logDebug("fetchGen1DeviceInfo: success for ${ip}: shellyInfo=${shellyInfo}, settings=${gen1Settings != null}, status=${gen1Status != null}")

        // Determine driver from normalized status
        if (device.deviceStatus) {
            determineDeviceDriver(device.deviceStatus, ipKey)
        }

        return true
    } catch (Exception e) {
        logDebug("fetchGen1DeviceInfo: failed for ${ip}: ${e.message}")
        return false
    }
}

/**
 * Infers the expected components for a Gen 1 battery device from its type code.
 * Battery devices are usually asleep during discovery, so we can't query their
 * actual status. Instead, we create a synthetic component map based on the known
 * capabilities of each device type.
 *
 * @param typeCode The Gen 1 device type code (e.g., "SHHT-1", "SHWT-1")
 * @return Synthetic normalized status map with expected component keys, or null if unknown
 */
@CompileStatic
static Map inferGen1BatteryComponents(String typeCode) {
    switch (typeCode) {
        case 'SHHT-1':
            return [
                'temperature:0': [:],
                'humidity:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHWT-1':  // Shelly Flood (type code is SHWT-1, not SHFLOOD)
            return [
                'flood:0': [:],
                'temperature:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHDW-1':
            return [
                'contact:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHDW-2':
            return [
                'contact:0': [:],
                'lux:0': [:],
                'tilt:0': [:],
                'temperature:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHBTN-1':
        case 'SHBTN-2':
            return [
                'input:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHMOS-01':  // Motion sensors also report temperature from internal sensor
        case 'SHMOS-02':
            return [
                'motion:0': [:],
                'lux:0': [:],
                'tamper:0': [:],
                'temperature:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHTRV-01':  // TRV — thermostat, temperature, battery
            return [
                'thermostat:0': [:],
                'temperature:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHSM-01':  // Shelly Smoke
            return [
                'smoke:0': [:],
                'temperature:0': [:],
                'devicepower:0': [:]
            ]
        case 'SHSN-1':  // Shelly Sense — motion, temp, humidity, lux, battery
            return [
                'motion:0': [:],
                'temperature:0': [:],
                'humidity:0': [:],
                'lux:0': [:],
                'devicepower:0': [:]
            ]
        default:
            return null
    }
}

/**
 * Normalizes a Gen 1 device status into the internal component format used by Gen 2+.
 * Converts Gen 1 array-based status keys ({@code relays[]}, {@code rollers[]}, etc.)
 * into colon-delimited component keys ({@code switch:0}, {@code cover:0}, etc.) that
 * {@link #determineDeviceDriver} expects.
 * <p>
 * Also injects power monitoring flags when {@code meters[]} or {@code emeters[]} are present.
 *
 * @param gen1Status The raw Gen 1 {@code /status} response
 * @param gen1Settings The raw Gen 1 {@code /settings} response (used for mode detection)
 * @param typeCode The Gen 1 device type code (e.g. {@code SHSW-25})
 * @return A map with Gen 2-style component keys, or empty map if no components found
 */
static Map normalizeGen1Status(Map gen1Status, Map gen1Settings, String typeCode) {
    Map normalized = [:]

    // Detect relay vs roller mode (Shelly 2, 2.5)
    String mode = gen1Settings?.mode?.toString() ?: 'relay'

    // Relays → switch:N (only if not in roller mode)
    List relays = gen1Status?.relays as List
    if (relays && mode != 'roller') {
        for (int i = 0; i < relays.size(); i++) {
            Map relayData = relays[i] as Map ?: [:]
            Map switchStatus = [output: relayData.ison ?: false]
            // Check for inline power data
            if (relayData.containsKey('power')) {
                switchStatus.apower = relayData.power
            }
            normalized["switch:${i}".toString()] = switchStatus
        }
    }

    // Rollers → cover:N (only if in roller mode)
    List rollers = gen1Status?.rollers as List
    if (rollers && mode == 'roller') {
        for (int i = 0; i < rollers.size(); i++) {
            Map rollerData = rollers[i] as Map ?: [:]
            normalized["cover:${i}".toString()] = [
                state: rollerData.state ?: 'stop',
                current_pos: rollerData.current_pos
            ]
        }
    }

    // Lights → light:N or white:N depending on device mode
    // RGBW2 in white mode: normalize lights as white:N (commands use /white/ endpoint)
    List lights = gen1Status?.lights as List
    if (lights) {
        Boolean isWhiteMode = (typeCode == 'SHRGBW2' && mode == 'white')
        String lightPrefix = isWhiteMode ? 'white' : 'light'

        for (int i = 0; i < lights.size(); i++) {
            Map lightData = lights[i] as Map ?: [:]
            Map lightMap = [
                output: lightData.ison ?: false,
                brightness: lightData.brightness ?: 0
            ]
            // Color mode fields (present on bulbs/RGBW devices in color mode)
            if (!isWhiteMode) {
                if (lightData.containsKey('mode'))  { lightMap.mode = lightData.mode }
                // Fallback: RGBW2 reports mode at top level of status, not inside lights[]
                if (!lightMap.containsKey('mode') && gen1Status.containsKey('mode')) {
                    lightMap.mode = gen1Status.mode
                }
                if (lightData.containsKey('red'))    { lightMap.red = lightData.red }
                if (lightData.containsKey('green'))  { lightMap.green = lightData.green }
                if (lightData.containsKey('blue'))   { lightMap.blue = lightData.blue }
                if (lightData.containsKey('white'))  { lightMap.white = lightData.white }
                if (lightData.containsKey('gain'))   { lightMap.gain = lightData.gain }
                if (lightData.containsKey('temp'))   { lightMap.temp = lightData.temp }
                if (lightData.containsKey('effect')) { lightMap.effect = lightData.effect }
            }
            normalized["${lightPrefix}:${i}".toString()] = lightMap
        }
    }

    // Inputs → input:N
    List inputs = gen1Status?.inputs as List
    if (inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            Map inputData = inputs[i] as Map ?: [:]
            normalized["input:${i}".toString()] = [
                state: inputData.input ?: false
            ]
        }
    }

    // ADC channels → adc:N (Shelly Uni)
    List adcs = gen1Status?.adcs as List
    if (adcs) {
        for (int i = 0; i < adcs.size(); i++) {
            Map adcData = adcs[i] as Map ?: [:]
            if (adcData.containsKey('voltage')) {
                normalized["adc:${i}".toString()] = [voltage: adcData.voltage]
            }
        }
    }

    // Meters → inject power monitoring data onto associated components
    List meters = gen1Status?.meters as List
    if (meters) {
        for (int i = 0; i < meters.size(); i++) {
            Map meterData = meters[i] as Map ?: [:]
            // Associate with switch, cover, light, or white component at same index
            String switchKey = "switch:${i}".toString()
            String coverKey = "cover:${i}".toString()
            String lightKey = "light:${i}".toString()
            String whiteKey = "white:${i}".toString()
            // Gen 1 meter.total is in Watt-minutes; convert to Wh for consistency with Gen 2
            BigDecimal totalWh = meterData.total != null ? (meterData.total as BigDecimal) / 60.0 : null
            if (normalized.containsKey(switchKey)) {
                Map switchMap = normalized[switchKey] as Map
                switchMap.apower = meterData.power
                if (totalWh != null) { switchMap.aenergy = [total: totalWh] }
                switchMap.voltage = meterData.voltage
                // Compute current from power and voltage (Gen 1 meters don't report current directly)
                if (meterData.power != null && meterData.voltage != null) {
                    BigDecimal v = meterData.voltage as BigDecimal
                    if (v > 0) {
                        switchMap.current = ((meterData.power as BigDecimal) / v).setScale(3, BigDecimal.ROUND_HALF_UP)
                    }
                }
            } else if (normalized.containsKey(coverKey)) {
                Map coverMap = normalized[coverKey] as Map
                coverMap.apower = meterData.power
                if (totalWh != null) { coverMap.aenergy = [total: totalWh] }
            } else if (normalized.containsKey(lightKey)) {
                Map lightMap = normalized[lightKey] as Map
                lightMap.apower = meterData.power
                if (totalWh != null) { lightMap.aenergy = [total: totalWh] }
            } else if (normalized.containsKey(whiteKey)) {
                Map whiteMap = normalized[whiteKey] as Map
                whiteMap.apower = meterData.power
                if (totalWh != null) { whiteMap.aenergy = [total: totalWh] }
            }
        }
    }

    // Device-level input voltage (Shelly 4Pro reports supply voltage at top level)
    if (gen1Status?.containsKey('voltage')) {
        normalized['deviceVoltage'] = gen1Status.voltage
    }

    // Energy meters → em:N (Shelly EM, 3EM)
    List emeters = gen1Status?.emeters as List
    if (emeters) {
        for (int i = 0; i < emeters.size(); i++) {
            Map emData = emeters[i] as Map ?: [:]
            normalized["em:${i}".toString()] = [
                act_power: emData.power,
                voltage: emData.voltage,
                current: emData.current,
                pf: emData.pf,
                reactive: emData.reactive,
                total_act_energy: emData.total,
                total_act_ret_energy: emData.total_returned
            ]
        }
    }

    // External temperature sensors (add-ons)
    Map extTemp = gen1Status?.ext_temperature as Map
    if (extTemp) {
        extTemp.each { key, value ->
            Map sensorData = value as Map ?: [:]
            Integer sensorIndex = 100 + (key.toString().isInteger() ? key.toString().toInteger() : 0)
            BigDecimal tC = sensorData.tC as BigDecimal
            BigDecimal tF = sensorData.tF != null ? sensorData.tF as BigDecimal : (tC != null ? (tC * 9.0 / 5.0) + 32.0 : null)
            normalized["temperature:${sensorIndex}".toString()] = [tC: tC, tF: tF]
        }
    }

    // External humidity sensors (add-ons)
    Map extHum = gen1Status?.ext_humidity as Map
    if (extHum) {
        extHum.each { key, value ->
            Map sensorData = value as Map ?: [:]
            Integer sensorIndex = 100 + (key.toString().isInteger() ? key.toString().toInteger() : 0)
            normalized["humidity:${sensorIndex}".toString()] = [value: sensorData.hum]
        }
    }

    // Internal device temperature (Shelly 1PM, 2.5, H&T, etc.)
    // Gen 1 tmp field: {value: X, units: "C"/"F", tC: X, tF: X}
    if (gen1Status?.containsKey('temperature') || gen1Status?.containsKey('tmp')) {
        Map tmpData = gen1Status?.tmp as Map
        Map tempMap = [:]
        if (tmpData?.tC != null) {
            tempMap.tC = tmpData.tC
        } else if (tmpData?.value != null && tmpData?.units == 'C') {
            tempMap.tC = tmpData.value
        } else if (gen1Status?.temperature != null) {
            tempMap.tC = gen1Status.temperature
        }
        if (tmpData?.tF != null) {
            tempMap.tF = tmpData.tF
        } else if (tmpData?.value != null && tmpData?.units == 'F') {
            tempMap.tF = tmpData.value
        } else if (tempMap.tC != null) {
            // Compute tF from tC for driver compatibility
            tempMap.tF = ((tempMap.tC as BigDecimal) * 9.0 / 5.0) + 32.0
        }
        if (tempMap) {
            normalized['temperature:0'] = tempMap
        }
    }

    // Humidity (Shelly H&T)
    if (gen1Status?.containsKey('hum')) {
        Map humData = gen1Status?.hum as Map
        if (humData?.value != null) {
            normalized['humidity:0'] = [value: humData.value]
        }
    }

    // Thermostats → thermostat:N (Shelly TRV)
    List thermostats = gen1Status?.thermostats as List
    if (thermostats) {
        for (int i = 0; i < thermostats.size(); i++) {
            Map trvData = thermostats[i] as Map ?: [:]
            Map targetT = trvData.target_t as Map ?: [:]
            BigDecimal targetTempC = targetT.value != null ? targetT.value as BigDecimal : null
            normalized["thermostat:${i}".toString()] = [
                pos: trvData.pos,
                target_t_enabled: targetT.enabled,
                target_t: targetTempC,
                schedule: trvData.schedule,
                schedule_profile: trvData.schedule_profile,
                boost_minutes: trvData.boost_minutes,
                window_open: trvData.window_open
            ]
        }
    }

    // Battery → devicepower:0
    if (gen1Status?.containsKey('bat')) {
        Map batData = gen1Status?.bat as Map
        if (batData?.value != null) {
            Map powerData = [battery: batData.value]
            if (batData?.voltage != null) {
                powerData.voltage = batData.voltage
            }
            // Charger status is a top-level field (Button1/Button2 USB charging)
            if (gen1Status?.containsKey('charger')) {
                powerData.charger = gen1Status.charger as Boolean
            }
            // H&T uses 'charging' field (not 'charger' like Button)
            if (gen1Status?.containsKey('charging')) {
                powerData.charger = gen1Status.charging as Boolean
            }
            normalized['devicepower:0'] = powerData
        }
    }

    // Fallback: top-level `battery` integer (Smoke SHSM-01 uses this instead of `bat` Map)
    if (!normalized.containsKey('devicepower:0') && gen1Status?.containsKey('battery')) {
        def batteryVal = gen1Status.battery
        if (batteryVal instanceof Number) {
            Map powerData = [battery: batteryVal as Integer]
            if (gen1Status?.containsKey('charger')) {
                powerData.charger = gen1Status.charger as Boolean
            }
            normalized['devicepower:0'] = powerData
        }
    }

    // Flood sensor
    if (gen1Status?.containsKey('flood')) {
        normalized['flood:0'] = [flood: gen1Status.flood]
    }

    // Smoke sensor (Shelly Smoke SHSM-01) — guarded by typeCode to avoid
    // collision if other Gen 1 devices ever return a top-level `alarm` field
    if (typeCode == 'SHSM-01' && gen1Status?.containsKey('alarm')) {
        normalized['smoke:0'] = [alarm: gen1Status.alarm]
    }

    // Gas sensor (Shelly Gas SHGS-1)
    if (typeCode == 'SHGS-1') {
        Map gasSensor = gen1Status?.gas_sensor as Map
        Map concentration = gen1Status?.concentration as Map
        if (gasSensor || concentration) {
            Map gasMap = [:]
            if (gasSensor?.alarm_state != null) { gasMap.alarm_state = gasSensor.alarm_state }
            if (gasSensor?.sensor_state != null) { gasMap.sensor_state = gasSensor.sensor_state }
            if (gasSensor?.self_test_state != null) { gasMap.self_test_state = gasSensor.self_test_state }
            if (concentration?.ppm != null) { gasMap.ppm = concentration.ppm }
            if (concentration?.is_valid != null) { gasMap.is_valid = concentration.is_valid }
            normalized['gas:0'] = gasMap
        }

        // Valve state
        List valves = gen1Status?.valves as List
        if (valves && valves.size() > 0) {
            Map valveData = valves[0] as Map ?: [:]
            normalized['valve:0'] = [state: valveData.state ?: 'not_connected']
        }
    }

    // Door/Window sensor — 'sensor' field contains state: "open" or "close"
    Map sensorData = gen1Status?.sensor as Map
    if (sensorData?.containsKey('state')) {
        normalized['contact:0'] = [open: sensorData.state == 'open']
    }

    // Motion sensor (Shelly Motion, Motion 2) — nested under 'sensor' object
    if (sensorData?.containsKey('motion')) {
        normalized['motion:0'] = [
            motion: sensorData.motion,
            active: sensorData.active
        ]
    }

    // Shelly Sense (SHSN-1): top-level 'motion' boolean (not inside 'sensor' object)
    if (typeCode == 'SHSN-1' && gen1Status?.containsKey('motion') && !normalized.containsKey('motion:0')) {
        normalized['motion:0'] = [motion: gen1Status.motion as Boolean]
    }

    // Vibration / tamper sensor (Motion sensors)
    if (sensorData?.containsKey('vibration')) {
        normalized['tamper:0'] = [vibration: sensorData.vibration]
    }

    // Tilt sensor (DW2)
    if (sensorData?.containsKey('tilt')) {
        normalized['tilt:0'] = [value: sensorData.tilt]
    }

    // Illuminance sensor (DW2)
    Map luxData = gen1Status?.lux as Map
    if (luxData?.containsKey('value')) {
        normalized['lux:0'] = [value: luxData.value]
    }

    return normalized
}

/**
 * Analyzes device status to determine the appropriate Hubitat driver.
 * Inspects the device status map for all supported component types, builds a
 * component list, and invokes driver generation (with caching and tracking).
 * <p>
 * Supported component types:
 * <ul>
 *   <li>Switches: status keys starting with "switch"</li>
 *   <li>Inputs: status keys containing "input"</li>
 *   <li>Temperature: status keys starting with "temperature"</li>
 *   <li>Humidity: status keys starting with "humidity"</li>
 *   <li>DevicePower: status keys starting with "devicepower" (battery)</li>
 * </ul>
 * <p>
 * Uses prebuilt drivers from {@code PREBUILT_DRIVERS} map. If a device's components
 * don't match any prebuilt driver, a warning is logged.
 *
 * @param deviceStatus Map containing the device status with component keys
 * @param ipKey Optional IP key for the discovered device entry
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

    // Comprehensive set of recognized Shelly component types
    Set<String> recognizedTypes = ['switch', 'cover', 'light', 'white', 'input', 'pm1', 'em', 'em1',
        'smoke', 'gas', 'temperature', 'humidity', 'devicepower', 'illuminance', 'voltmeter',
        'flood', 'contact', 'lux', 'tilt', 'motion', 'valve', 'thermostat', 'adc'] as Set

    deviceStatus.each { k, v ->
        String key = k.toString().toLowerCase()
        String baseType = key.contains(':') ? key.split(':')[0] : key

        if (!recognizedTypes.contains(baseType)) { return }

        components.add(k.toString())

        // Check if this component has power monitoring
        // em, em1, pm1 are inherently power monitoring components
        Boolean hasPM = (baseType == 'em' || baseType == 'em1' || baseType == 'pm1')
        if (!hasPM && v instanceof Map && (baseType == 'switch' || baseType == 'cover')) {
            hasPM = v.voltage != null || v.current != null || v.power != null ||
                    v.apower != null || v.aenergy != null
        }
        componentPowerMonitoring[k.toString()] = hasPM

        if (hasPM) {
            logInfo("Component ${k} has power monitoring capabilities")
        }
        if (baseType != 'switch' && baseType != 'input') {
            logInfo("Found ${baseType} component: ${k}")
        }
    }

    // Detect multi-component devices needing parent-child architecture
    // A device needs parent-child if it has 2+ instances of any single actuator type
    Set<String> actuatorComponentTypes = ['switch', 'cover', 'light', 'white', 'em'] as Set
    Map<String, Integer> actuatorCounts = [:]
    components.each { String comp ->
        String baseType = comp.contains(':') ? comp.split(':')[0] : comp
        if (actuatorComponentTypes.contains(baseType)) {
            actuatorCounts[baseType] = (actuatorCounts[baseType] ?: 0) + 1
        }
    }
    Boolean needsParentChild = actuatorCounts.any { String k, Integer v -> v > 1 }

    if (needsParentChild) {
        logInfo("Multi-component device detected: ${actuatorCounts} — using parent-child architecture")
    }

    // Store multi-component detection results on the discovered device entry
    if (ipKey && state.discoveredShellys[ipKey]) {
        state.discoveredShellys[ipKey].needsParentChild = needsParentChild
        state.discoveredShellys[ipKey].actuatorCounts = actuatorCounts
        state.discoveredShellys[ipKey].components = components
        state.discoveredShellys[ipKey].componentPowerMonitoring = componentPowerMonitoring
    }

    // Determine driver name for discovered components and install prebuilt driver
    if (components.size() > 0) {
        Boolean isParent = needsParentChild
        Boolean isGen1 = ipKey ? (state.discoveredShellys[ipKey]?.gen?.toString() == '1') : false

        // Model-specific driver override for Gen 1 devices (e.g., Plugs)
        String gen1TypeCode = ipKey ? state.discoveredShellys[ipKey]?.gen1Type?.toString() : null
        String driverName
        if (gen1TypeCode && GEN1_MODEL_DRIVER_OVERRIDE.containsKey(gen1TypeCode)) {
            driverName = GEN1_MODEL_DRIVER_OVERRIDE[gen1TypeCode]
        } else if (gen1TypeCode == 'SHRGBW2') {
            // RGBW2 mode-based driver selection (color vs white firmware mode)
            Map gen1Settings = ipKey ? state.discoveredShellys[ipKey]?.gen1Settings as Map : null
            String rgbw2Mode = gen1Settings?.mode?.toString() ?: 'color'
            driverName = (rgbw2Mode == 'white') ?
                'Shelly Gen1 RGBW2 White Parent' :
                'Shelly Gen1 RGBW2 Color'
        } else {
            driverName = generateDriverName(components, componentPowerMonitoring, isParent, isGen1)
        }
        String version = getAppVersion()
        String driverNameWithVersion = "${driverName} v${version}"

        if (PREBUILT_DRIVERS.containsKey(driverName)) {
            // Prebuilt driver exists — install it (idempotent, skips if already installed)
            installPrebuiltDriver(driverName, components, componentPowerMonitoring, version)

            // White parent needs its child driver installed too
            if (driverName == 'Shelly Gen1 RGBW2 White Parent') {
                installPrebuiltDriver('Shelly Gen1 White Channel', components, componentPowerMonitoring, version)
            }

            // Store the driver name on the discovered device entry
            if (ipKey && state.discoveredShellys[ipKey]) {
                state.discoveredShellys[ipKey].generatedDriverName = driverNameWithVersion
            }
        } else {
            logWarn("No prebuilt driver for '${driverName}' (components: ${components}). " +
                    "Add this type to PREBUILT_DRIVERS to support it.")
        }
    }

    // Log device type heuristics
    if (switchesFound == 1 && inputsFound == 0) {
        logDebug("Device is likely a plug or basic relay device (1 switch, no inputs)")
    } else if (switchesFound == 1 && inputsFound >= 1) {
        logDebug("Device is likely a roller shutter or similar (1 switch, 1+ inputs)")
    } else if (switchesFound > 1) {
        logDebug("Device is likely a multi-relay model (multiple switches)")
    } else if (switchesFound == 0 && components.size() > 0) {
        // Sensor-only or input-only device (e.g., Gen1 Motion Sensor has input:0 + lux + temp + battery)
        logDebug("Device is a sensor/input-only device (no switches, ${components.size()} components)")
    } else if (switchesFound == 0 && inputsFound == 0) {
        logDebug("determineDeviceDriver: no recognized components found in device status")
    } else {
        logDebug("Device has an unexpected combination of switches and inputs, manual review may be needed")
    }
}

/**
 * Generates a dynamic driver name based on the Shelly components.
 * <p>
 * Categorizes components into actuators (switch, cover, light) and sensors
 * (temperature, humidity). The {@code devicepower} component adds Battery
 * capability but does not affect the driver name.
 * <p>
 * When actuators are present, the actuator type drives naming — sensor
 * components (e.g., internal temperature monitors on switch/cover devices)
 * are treated as supplementary and handled by the prebuilt driver.
 * <p>
 * Naming rules:
 * <ul>
 *   <li>Actuator present: "Shelly Autoconf Single Switch [PM]", "Shelly Autoconf Single Cover PM", etc.</li>
 *   <li>Sensor-only: "Shelly Autoconf TH Sensor" (temp+humidity), "Shelly Autoconf Temperature Sensor", etc.</li>
 *   <li>Multiple actuator types: "Shelly Autoconf Multi-Component Device [PM]"</li>
 * </ul>
 *
 * @param components List of Shelly components (e.g., ["switch:0", "temperature:0"])
 * @param componentPowerMonitoring Map of component names to power monitoring flags
 * @param isParent Whether this device needs a parent driver (multi-component with children)
 * @param isGen1 Whether this is a Gen 1 device (uses "Shelly Gen1" prefix instead of "Shelly Autoconf")
 * @return Generated driver name
 */
private String generateDriverName(List<String> components, Map<String, Boolean> componentPowerMonitoring = [:], Boolean isParent = false, Boolean isGen1 = false) {
    Map<String, Integer> componentCounts = [:]
    Boolean hasPowerMonitoring = false

    // Gen 1 vs Gen 2+ prefix
    String prefix = isGen1 ? "Shelly Gen1" : "Shelly Autoconf"

    // Sensor and actuator type sets
    Set<String> sensorTypes = ['temperature', 'humidity', 'illuminance', 'smoke', 'voltmeter'] as Set
    Set<String> supportTypes = ['devicepower', 'input', 'pm1'] as Set
    Set<String> actuatorTypes = ['switch', 'cover', 'light'] as Set
    // Gen 1 additional component types
    Set<String> gen1SensorTypes = ['flood', 'em', 'contact', 'lux'] as Set

    components.each { component ->
        String baseType = component.contains(':') ? component.split(':')[0] : component
        componentCounts[baseType] = (componentCounts[baseType] ?: 0) + 1

        if (componentPowerMonitoring[component]) {
            hasPowerMonitoring = true
        }
    }

    // Separate component types
    Set<String> foundActuators = componentCounts.keySet().findAll { actuatorTypes.contains(it) } as Set
    Set<String> foundSensors = componentCounts.keySet().findAll { sensorTypes.contains(it) } as Set
    Boolean hasBattery = componentCounts.containsKey('devicepower')
    Boolean hasFlood = componentCounts.containsKey('flood')
    Boolean hasEM = componentCounts.containsKey('em')
    String pmSuffix = hasPowerMonitoring ? " PM" : ""
    String parentSuffix = isParent ? " Parent" : ""

    // Gen 1 special types: TRV (thermostat component)
    Boolean hasThermostat = componentCounts.containsKey('thermostat')
    if (isGen1 && hasThermostat) {
        return "${prefix} TRV"
    }

    // Gen 1 special types: Flood sensor
    if (isGen1 && hasFlood) {
        return "${prefix} Flood Sensor"
    }

    // Gen 1 special types: Door/Window sensor
    Boolean hasContact = componentCounts.containsKey('contact')
    if (isGen1 && hasContact) {
        return "${prefix} DW Sensor"
    }

    // Input count needed by multiple checks below
    Integer inputCount = componentCounts['input'] ?: 0

    // Gen 1 special types: Motion sensor (lux + battery, no actuators)
    Boolean hasLux = componentCounts.containsKey('lux')
    if (isGen1 && hasLux && hasBattery && foundActuators.size() == 0) {
        return "${prefix} Motion Sensor"
    }

    // Gen 1 special types: Button (single input + battery, no actuators)
    if (isGen1 && inputCount == 1 && hasBattery && foundActuators.size() == 0) {
        return "${prefix} Button"
    }

    // Gen 1 special types: Energy meter (EM, 3EM)
    // Always returns the same driver name — the driver dynamically creates
    // children based on the components data value (2 for EM, 3 for 3EM).
    if (isGen1 && hasEM) {
        return "${prefix} EM Parent"
    }

    // Special case: Input-only devices (no actuators, only inputs)
    if (foundActuators.size() == 0 && inputCount > 1) {
        return "${prefix} ${inputCount}x Input Parent"
    }

    // When actuators are present, classify by actuator type
    // (sensor components like temperature are supplementary — handled by the driver)
    if (foundActuators.size() > 0) {
        // Filter to only actuator counts for naming
        Map<String, Integer> actuatorCounts = componentCounts.findAll { k, v -> actuatorTypes.contains(k) }

        if (actuatorCounts.size() == 1) {
            String type = actuatorCounts.keySet().first()
            Integer count = actuatorCounts[type]
            // Map Shelly component types to friendly driver names
            Map<String, String> typeNameMap = [
                'switch': 'Switch',
                'cover': 'Cover',
                'light': 'Dimmer'
            ]
            String typeName = typeNameMap[type] ?: type.capitalize()

            // Covers always use Parent architecture (supports profile changes)
            // Switches with count > 1 use Parent architecture
            Boolean needsParent = (type == 'cover') || (count > 1) || isParent

            if (count == 1) {
                if (needsParent) {
                    return "${prefix} Single ${typeName}${pmSuffix} Parent"
                } else {
                    // Gen 1 devices with inputs get a separate driver with button support
                    if (isGen1 && inputCount > 0) {
                        return "${prefix} Single ${typeName} Input${pmSuffix}"
                    }
                    return "${prefix} Single ${typeName}${pmSuffix}"
                }
            } else {
                return "${prefix} ${count}x ${typeName}${pmSuffix} Parent"
            }
        } else {
            // Multiple actuator types - use fallback parent
            return "${prefix} Parent"
        }
    }

    // Sensor-only device
    if (foundSensors.size() > 0) {
        Boolean hasTemp = foundSensors.contains('temperature')
        Boolean hasHumidity = foundSensors.contains('humidity')

        if (hasTemp && hasHumidity) {
            return "${prefix} TH Sensor${parentSuffix}"
        } else if (foundSensors.contains('smoke')) {
            return "${prefix} Smoke Sensor${parentSuffix}"
        } else if (hasTemp) {
            return "${prefix} Temperature Sensor${parentSuffix}"
        } else if (hasHumidity) {
            return "${prefix} Humidity Sensor${parentSuffix}"
        } else if (foundSensors.contains('illuminance')) {
            return "${prefix} Illuminance Sensor${parentSuffix}"
        } else if (foundSensors.contains('voltmeter')) {
            return "${prefix} Voltmeter${parentSuffix}"
        } else {
            // Other sensor types
            String sensorName = foundSensors.first().capitalize()
            return "${prefix} ${sensorName} Sensor${parentSuffix}"
        }
    }

    // Only support components (devicepower only, no sensors or actuators)
    if (hasBattery) {
        return "${prefix} Battery Device${parentSuffix}"
    }

    // Fallback for unknown patterns - use generic parent if multi-component
    if (isParent || components.size() > 1) {
        return "${prefix} Parent"
    }

    return "${prefix} Unknown Device"
}

/**
 * Downloads and installs a pre-built driver from the GitHub repository.
 * Looks up the driver name in PREBUILT_DRIVERS, downloads the .groovy file,
 * installs it on the hub, and registers it in the tracking system.
 *
 * @param driverName The base driver name (e.g., "Shelly Autoconf Single Switch")
 * @param components List of component identifiers for tracking registration
 * @param componentPowerMonitoring Map of component power monitoring flags
 * @param version The current app version string
 * @return true if install succeeded, false otherwise
 */
private Boolean installPrebuiltDriver(String driverName, List<String> components, Map<String, Boolean> componentPowerMonitoring, String version) {
    String repoPath = PREBUILT_DRIVERS[driverName]
    if (!repoPath) {
        logDebug("installPrebuiltDriver: no pre-built driver found for '${driverName}'")
        return false
    }

    String rawUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${GITHUB_BRANCH}/${repoPath}"
    logInfo("Downloading pre-built driver from: ${rawUrl}")

    String sourceCode = downloadFile(rawUrl)
    if (!sourceCode) {
        logError("installPrebuiltDriver: failed to download pre-built driver from ${rawUrl}")
        return false
    }

    // Patch the driver name in source to include version suffix, consistent with generated drivers.
    // Pre-built source has e.g. name: 'Shelly Autoconf Single Switch PM' — we append ' v1.0.33'.
    String driverNameWithVersion = "${driverName} v${version}"
    sourceCode = sourceCode.replaceFirst(/(name:\s*')${java.util.regex.Pattern.quote(driverName)}'/, "\$1${driverNameWithVersion}'")

    logInfo("Downloaded pre-built driver for ${driverNameWithVersion} (${sourceCode.length()} chars)")
    Boolean success = installDriver(sourceCode)
    if (!success) {
        logError("installPrebuiltDriver: installDriver failed for ${driverNameWithVersion}")
        return false
    }

    registerAutoDriver(driverNameWithVersion, 'ShellyUSA', version, components, componentPowerMonitoring)

    return true
}

/**
 * Installs a driver on the hub by posting the source code.
 * Updates an existing driver if one with the same name/namespace exists,
 * otherwise creates a new driver entry.
 *
 * @param sourceCode The complete driver source code to install
 * @return true if the driver was installed/updated successfully, false otherwise
 */
private Boolean installDriver(String sourceCode) {
    logInfo("Installing/updating generated driver...")
    Boolean success = false

    try {
        // Extract driver name from source code
        def nameMatch = (sourceCode =~ /name:\s*['"]([^'"]+)['"]/)
        if (!nameMatch.find()) {
            logError("Could not extract driver name from source code")
            return false
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
                        success = true
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
                    success = true
                } else {
                    logError("✗ HTTP error ${resp?.status}")
                }
            }
        }

    } catch (Exception e) {
        logError("Error installing driver: ${e.message}")
        return false
    }
    return success
}

/**
 * Checks whether a driver with the given name and namespace is installed on the hub.
 *
 * @param driverName The exact driver name to look for (including version suffix)
 * @param namespace The driver namespace (e.g., 'ShellyUSA')
 * @return true if the driver exists on the hub
 */
private Boolean isDriverOnHub(String driverName, String namespace) {
    Boolean found = false
    try {
        httpGet([uri: "http://127.0.0.1:8080", path: '/device/drivers', contentType: 'application/json', timeout: 5000]) { resp ->
            if (resp?.status == 200) {
                found = resp.data?.drivers?.any { d ->
                    d.type == 'usr' && d?.namespace == namespace && d?.name == driverName
                } ?: false
            }
        }
    } catch (Exception e) {
        logError("Error checking hub drivers: ${e.message}")
    }
    return found
}

/**
 * Ensures a driver is installed on the hub before device creation.
 * Checks if the driver exists, and if not, attempts to install via
 * the prebuilt driver path. Waits briefly after installation for the hub to process.
 *
 * @param driverName The versioned driver name (e.g., "Shelly Autoconf Single Switch PM v1.0.34")
 * @param deviceInfo The discovered device information map
 * @return true if the driver is confirmed on the hub, false otherwise
 */
private Boolean ensureDriverInstalled(String driverName, Map deviceInfo) {
    String namespace = 'ShellyUSA'
    if (isDriverOnHub(driverName, namespace)) {
        logDebug("Driver '${driverName}' confirmed on hub")
        return true
    }

    logInfo("Driver '${driverName}' not found on hub, attempting to install...")

    String baseName = driverName.replaceAll(/\s+v\d+(\.\d+)*$/, '')
    String version = getAppVersion()
    List<String> components = (deviceInfo.components ?: []) as List<String>
    Map<String, Boolean> pmMap = (deviceInfo.componentPowerMonitoring ?: [:]) as Map<String, Boolean>

    Boolean installed = false
    if (PREBUILT_DRIVERS.containsKey(baseName)) {
        installed = installPrebuiltDriver(baseName, components, pmMap, version)
    } else {
        logWarn("No prebuilt driver for '${baseName}'. Add to PREBUILT_DRIVERS to support this device type.")
    }

    if (!installed) {
        logError("Driver installation failed for '${driverName}'")
        return false
    }

    pauseExecution(2000)
    Boolean confirmed = isDriverOnHub(driverName, namespace)
    if (!confirmed) {
        logError("Driver '${driverName}' still not found on hub after installation")
    }
    return confirmed
}

// ═══════════════════════════════════════════════════════════════
// ║  Component Driver Installation (Parent-Child Architecture)  ║
// ═══════════════════════════════════════════════════════════════

/**
 * Maps a Shelly component type to its component driver file name.
 *
 * @param componentType The Shelly component type (switch, cover, light, input)
 * @param hasPowerMonitoring Whether this component has power monitoring
 * @return The component driver file name (e.g., "ShellySwitchComponentPM.groovy")
 */
private String getComponentDriverFileName(String componentType, Boolean hasPowerMonitoring = false) {
    Map<String, Map<String, String>> driverMap = [
        'switch': [default: 'ShellySwitchComponent.groovy', pm: 'ShellySwitchComponentPM.groovy'],
        'cover': [default: 'ShellyCoverComponent.groovy', pm: 'ShellyCoverComponentPM.groovy'],
        'light': [default: 'ShellyDimmerComponent.groovy'],
        'input': [default: 'ShellyInputButtonComponent.groovy'],
        'em': [default: 'ShellyEMComponent.groovy'],
        'adc': [default: 'ShellyPollingVoltageSensorComponent.groovy'],
        'temperature': [default: 'ShellyTemperaturePeripheralComponent.groovy'],
        'humidity': [default: 'ShellyHumidityPeripheralComponent.groovy']
    ]

    Map<String, String> typeMap = driverMap[componentType]
    if (!typeMap) { return null }

    if (hasPowerMonitoring && typeMap.pm) {
        return typeMap.pm
    }
    return typeMap['default']
}

/**
 * Gets the Hubitat driver name for a component driver file.
 * Extracts the name from the driver metadata definition.
 *
 * @param componentType The Shelly component type (switch, cover, light, input)
 * @param hasPowerMonitoring Whether this component has power monitoring
 * @return The driver name as defined in the component driver's metadata
 */
private String getComponentDriverName(String componentType, Boolean hasPowerMonitoring = false) {
    Map<String, Map<String, String>> nameMap = [
        'switch': [default: 'Shelly Autoconf Switch', pm: 'Shelly Autoconf Switch PM'],
        'cover': [default: 'Shelly Autoconf Cover', pm: 'Shelly Autoconf Cover PM'],
        'light': [default: 'Shelly Autoconf Dimmer'],
        'input': [default: 'Shelly Autoconf Input Button'],
        'em': [default: 'Shelly Autoconf EM'],
        'adc': [default: 'Shelly Autoconf Polling Voltage Sensor'],
        'temperature': [default: 'Shelly Autoconf Temperature Peripheral'],
        'humidity': [default: 'Shelly Autoconf Humidity Peripheral']
    ]

    Map<String, String> typeMap = nameMap[componentType]
    if (!typeMap) { return null }

    if (hasPowerMonitoring && typeMap.pm) {
        return typeMap.pm
    }
    return typeMap['default']
}

/**
 * Checks if a component driver is already installed on the hub.
 *
 * @param driverName The driver name to check for
 * @return true if the driver is already installed
 */
private Boolean isComponentDriverInstalled(String driverName) {
    Boolean found = false
    try {
        Map driverParams = [
            uri: "http://127.0.0.1:8080",
            path: '/device/drivers',
            contentType: 'application/json',
            timeout: 5000
        ]

        httpGet(driverParams) { resp ->
            if (resp?.status == 200) {
                found = resp.data?.drivers?.any { driver ->
                    driver.type == 'usr' &&
                    driver?.namespace == 'ShellyUSA' &&
                    driver?.name == driverName
                } ?: false
            }
        }
    } catch (Exception e) {
        logError("Error checking for component driver ${driverName}: ${e.message}")
    }
    return found
}

/**
 * Fetches a component driver source file from GitHub and installs it on the hub.
 *
 * @param fileName The component driver file name (e.g., "ShellySwitchComponentPM.groovy")
 * @param driverName The expected driver name for tracking
 */
private void fetchAndInstallComponentDriver(String fileName, String driverName) {
    String baseUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${GITHUB_BRANCH}/UniversalDrivers/UniversalComponentDrivers"
    String fileUrl = "${baseUrl}/${fileName}?v=${now()}"

    logInfo("Fetching component driver from GitHub: ${fileName}")

    String sourceCode = downloadFile(fileUrl)
    if (!sourceCode) {
        logError("Failed to fetch component driver from GitHub: ${fileName}")
        return
    }

    installDriver(sourceCode)

    // Register in autoDrivers tracking as a component driver
    String version = getAppVersion()
    String key = "ShellyUSA.${driverName}"
    initializeDriverTracking()
    state.autoDrivers[key] = [
        name: driverName,
        namespace: 'ShellyUSA',
        version: version,
        isComponentDriver: true,
        installedAt: now(),
        lastUpdated: now(),
        devicesUsing: []
    ]

    logInfo("Installed component driver: ${driverName}")
}

/**
 * Installs all required component drivers for a multi-component device.
 * Determines which component drivers are needed based on the device's components,
 * checks if they're already installed, and fetches/installs any missing ones.
 *
 * @param deviceInfo The discovered device information map
 */
private void installComponentDriversForDevice(Map deviceInfo) {
    Map deviceStatus = deviceInfo.deviceStatus ?: [:]
    Map<String, Boolean> componentPowerMonitoring = (deviceInfo.componentPowerMonitoring ?: [:]) as Map<String, Boolean>

    Set<String> installedDrivers = [] as Set

    deviceStatus.each { k, v ->
        String key = k.toString()
        String baseType = key.contains(':') ? key.split(':')[0] : key
        if (!['switch', 'cover', 'light', 'input', 'em', 'adc', 'temperature', 'humidity'].contains(baseType)) { return }

        Boolean hasPM = componentPowerMonitoring[key] ?: false
        String driverName = getComponentDriverName(baseType, hasPM)
        if (!driverName || installedDrivers.contains(driverName)) { return }

        installedDrivers.add(driverName)

        if (!isComponentDriverInstalled(driverName)) {
            String fileName = getComponentDriverFileName(baseType, hasPM)
            if (fileName) {
                fetchAndInstallComponentDriver(fileName, driverName)
            }
        } else {
            logDebug("Component driver already installed: ${driverName}")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ║  END Component Driver Installation                          ║
// ═══════════════════════════════════════════════════════════════

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
    // Logs display updates on next page re-render (no sendEvent needed)
}


/**
 * Renders the recent log lines as an HTML {@code <pre>} block.
 * Used both for initial page render and SSR updates (piggybacked on
 * the {@code configTable} event to avoid additional {@code sendEvent()} calls).
 *
 * @return HTML string containing the formatted log lines
 */
private String renderRecentLogsHtml() {
    String logs = state.recentLogs ? state.recentLogs.reverse().take(10).join('\n') : ''
    String recentPayload = "Recent log lines (most recent first):\n" + (logs ?: 'No logs yet.')
    return "<pre style='white-space:pre-wrap; font-size:12px; line-height:1.2;'>${recentPayload}</pre>"
}

/**
 * Appends a log message to the in-app log buffer.
 * Adds the message with timestamp and level to state if it meets the display
 * threshold and maintains a rolling buffer of the most recent 50 entries.
 * Logs update in real time via SSR, piggybacked on the {@code configTable} event.
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
        if (state.recentLogs.size() > 50) {
            state.recentLogs = state.recentLogs[-50..-1]
        }
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
 * @param name Webhook name (e.g., "hubitat_sdm_temperature")
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
 * @param name Webhook name (must use hubitat_sdm_ prefix)
 * @param urls Updated list of URLs
 * @param enable Whether the webhook should be enabled
 * @param src Source identifier for the RPC call
 * @return LinkedHashMap containing the Webhook.Update RPC command
 */
@CompileStatic
LinkedHashMap webhookUpdateCommand(Integer id, String name, List<String> urls, Boolean enable = true, String src = 'webhookUpdate') {
  LinkedHashMap command = [
    "id" : 0,
    "src" : src,
    "method" : "Webhook.Update",
    "params" : [
      "id"     : id,
      "name"   : name,
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
 * Uploads script code to a Shelly device in chunks to avoid 413 Payload Too Large errors.
 * Shelly Script.PutCode has a per-request size limit; this sends the code in 768-byte chunks.
 * First chunk uses append=false to overwrite, subsequent chunks use append=true.
 * Includes inter-chunk delay and response validation for reliable transfers.
 *
 * @param scriptId The script ID on the device
 * @param code The full script source code
 * @param uri The device RPC URI (e.g. http://192.168.1.x/rpc)
 * @param hasAuth Whether authentication is required
 */
private void uploadScriptInChunks(Integer scriptId, String code, String uri, Boolean hasAuth) {
  Integer chunkSize = 768
  Integer offset = 0
  Integer total = code.length()
  Boolean first = true
  Integer chunkNum = 0

  while (offset < total) {
    Integer end = Math.min(offset + chunkSize, total) as Integer
    String chunk = code.substring(offset, end)
    LinkedHashMap putCmd = scriptPutCodeCommand(scriptId, chunk, !first)
    if (hasAuth) { putCmd.auth = getAuth() }

    LinkedHashMap result = postCommandSync(putCmd, uri)

    // Check for Shelly RPC-level error in the response
    if (result?.error) {
      String errMsg = "Script upload failed on chunk ${chunkNum} (offset ${offset}): ${result.error}"
      logError(errMsg)
      throw new Exception(errMsg)
    }

    chunkNum++
    first = false
    offset = end

    // Brief pause between chunks to let the device flush writes
    if (offset < total) { pauseExecution(150) }
  }
  logDebug("Uploaded script id=${scriptId} in ${chunkNum} chunks (${total} bytes)")
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

// ╔══════════════════════════════════════════════════════════════╗
// ║  KVS Management                                               ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Writes the Hubitat IP address to the Shelly device's KVS (Key-Value Store).
 * This allows Shelly scripts to dynamically retrieve the Hubitat IP instead of
 * hardcoding it in script source code.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void writeHubitatIpToKVS(String ipAddress) {
    String hubitatIp = location.hub.localIP
    if (!hubitatIp) {
        logWarn("writeHubitatIpToKVS: could not determine Hubitat IP")
        return
    }

    logDebug("Writing Hubitat IP (${hubitatIp}) to KVS on ${ipAddress}")
    String uri = "http://${ipAddress}/rpc"

    LinkedHashMap command = kvsSetCommand('hubitat_sdm_ip', hubitatIp)
    if (authIsEnabled() == true && getAuth().size() > 0) {
        command.auth = getAuth()
    }

    LinkedHashMap response = postCommandSync(command, uri)
    if (response?.error) {
        logError("Failed to write hubitat_sdm_ip to KVS on ${ipAddress}: ${response.error}")
    } else {
        logDebug("Successfully wrote hubitat_sdm_ip=${hubitatIp} to KVS on ${ipAddress}")
    }
}

/**
 * Writes a KVS key-value pair to the Shelly device on behalf of a driver.
 * Called by UniversalDrivers that don't have direct RPC access.
 *
 * @param parentDevice The parent device requesting the KVS write
 * @param key The KVS key to set
 * @param value The value to store
 */
void componentWriteKvsToDevice(def parentDevice, String key, Object value) {
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("componentWriteKvsToDevice: no IP for ${parentDevice.displayName}")
        return
    }
    logDebug("Writing KVS key '${key}'=${value} to ${ipAddress}")
    String uri = "http://${ipAddress}/rpc"
    LinkedHashMap command = kvsSetCommand(key, value)
    if (authIsEnabled() == true && getAuth().size() > 0) { command.auth = getAuth() }
    LinkedHashMap response = postCommandSync(command, uri)
    if (response?.error) {
        logError("Failed to write KVS key '${key}' on ${ipAddress}: ${response.error}")
    } else {
        logDebug("Successfully wrote KVS '${key}'=${value} on ${ipAddress}")
    }
}

/**
 * Removes a KVS entry from the Shelly device.
 * Ignores "not found" errors (code -113) since the key may not exist.
 *
 * @param ipAddress The IP address of the Shelly device
 * @param key The KVS key to remove
 */
private void removeKvsEntry(String ipAddress, String key) {
    logDebug("Removing '${key}' from KVS on ${ipAddress}")
    String uri = "http://${ipAddress}/rpc"

    LinkedHashMap command = kvsDeleteCommand(key)
    if (authIsEnabled() == true && getAuth().size() > 0) {
        command.auth = getAuth()
    }

    LinkedHashMap response = postCommandSync(command, uri)
    if (response?.error) {
        // Ignore "not found" errors — key might not exist
        if (response.error.code != -113) {
            logDebug("Could not remove '${key}' from KVS on ${ipAddress}: ${response.error}")
        }
    } else {
        logDebug("Successfully removed '${key}' from KVS on ${ipAddress}")
    }
}

/**
 * Removes the Hubitat IP address from the Shelly device's KVS.
 * Called when all scripts are removed from the device or when the device is deleted.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void removeHubitatIpFromKVS(String ipAddress) {
    removeKvsEntry(ipAddress, 'hubitat_sdm_ip')
}

/**
 * Checks if all managed scripts have been removed from the device.
 * If so, removes all Hubitat KVS entries since they are no longer needed.
 *
 * @param ipAddress The IP address of the Shelly device
 */
private void checkAndRemoveKvsIfNoScripts(String ipAddress) {
    List<Map> installedScripts = listDeviceScripts(ipAddress)
    if (installedScripts == null) { return }

    // Check if any managed scripts still exist
    Boolean hasAnyManagedScript = installedScripts.any { Map script ->
        String name = script.name as String
        MANAGED_SCRIPT_NAMES.contains(name)
    }

    if (!hasAnyManagedScript) {
        logDebug("No managed scripts remain on ${ipAddress} — removing Hubitat KVS entries")
        removeKvsEntry(ipAddress, 'hubitat_sdm_ip')
        removeKvsEntry(ipAddress, 'hubitat_sdm_pm_ri')
    }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END KVS Management                                           ║
// ╚══════════════════════════════════════════════════════════════╝

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

// ═══════════════════════════════════════════════════════════════
// ║  Bluetooth (BLE) Device Support                              ║
// ═══════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────
// BLE Data Reception & Deduplication
// ─────────────────────────────────────────────────────────────

/**
 * Receives BLE relay data from a WiFi gateway driver.
 * Called by Gen 2+ drivers when they receive a POST with dst='ble'.
 * Deduplicates by pid+mac, updates discovery state, and routes events to child devices.
 *
 * @param gatewayDevice The gateway device that received the BLE advertisement
 * @param bleData Map of decoded BTHome fields (mac, pid, model, battery, temperature, etc.)
 */
void handleBleRelay(def gatewayDevice, Map bleData) {
    String mac = bleData?.mac?.toString()?.toUpperCase()
    if (!mac) {
        logDebug('handleBleRelay: no MAC in BLE data')
        return
    }

    Integer pid = bleData.pid != null ? bleData.pid as Integer : -1
    String model = bleData.model?.toString() ?: ''
    Integer modelId = bleData.modelId != null ? bleData.modelId as Integer : null
    Integer rssi = bleData.rssi != null ? bleData.rssi as Integer : null
    String gatewayName = gatewayDevice?.displayName ?: 'Unknown gateway'

    logTrace("handleBleRelay: mac=${mac} pid=${pid} model=${model} modelId=${modelId} rssi=${rssi} gateway=${gatewayName}")

    // Dedup by pid per MAC
    if (isBlePidDuplicate(mac, pid)) {
        logTrace("handleBleRelay: duplicate pid ${pid} for ${mac}, skipping")
        return
    }

    // Update discovery state
    updateBleDiscoveryState(mac, model, modelId, rssi, gatewayName, bleData)

    // Route events to child device (if created)
    routeBleEventToChild(mac, bleData)

    // Throttle BLE table SSR updates to avoid exceeding hub event rate limits.
    // At most once per 10 seconds — BLE advertisements arrive frequently.
    Long lastBleUpdate = (state.lastBleTableUpdate ?: 0) as Long
    if (now() - lastBleUpdate > 10000) {
        sendEvent(name: 'bleTable', value: 'update')
        state.lastBleTableUpdate = now()
    }
}

/**
 * Checks if a BLE packet ID has already been processed for a given MAC.
 * Maintains a ring buffer of the last 10 pids per MAC in state.recentBlePids.
 *
 * @param mac The BLE device MAC address
 * @param pid The packet ID to check
 * @return true if this pid was already seen for this MAC
 */
private Boolean isBlePidDuplicate(String mac, Integer pid) {
    Map recentPids = state.recentBlePids ?: [:]
    String macKey = mac.toString()
    List<Integer> pids = (recentPids[macKey] ?: []) as List<Integer>

    if (pids.contains(pid)) {
        return true
    }

    // Add to ring buffer (keep last 10)
    pids.add(pid)
    if (pids.size() > 10) {
        pids = pids[-10..-1]
    }

    recentPids[macKey] = pids
    state.recentBlePids = recentPids
    return false
}

// ─────────────────────────────────────────────────────────────
// BLE Model Resolution
// ─────────────────────────────────────────────────────────────

/**
 * Resolves BLE driver information using multiple identification layers.
 * Priority: numeric model ID (from manufacturer data / BTHome) → string model code (from local_name).
 *
 * @param modelId Numeric model ID from manufacturer data or BTHome device_type_id (may be null)
 * @param model String model code from BLE local_name (e.g., 'SBHT-003C', may be null/empty)
 * @return Map with driverName, friendlyModel, and modelCode keys, or null if unresolved
 */
@CompileStatic
private static Map<String, String> resolveBleDriverInfo(Integer modelId, String model) {
    if (modelId != null) {
        Map<String, String> info = BLE_MODEL_ID_TO_DRIVER[modelId]
        if (info) { return info }
        // BTHome device_type_id uses format (version << 8) | model_id (e.g., 0x0206 = v2 + model 6)
        // Strip version byte and retry with base model ID
        Integer baseId = modelId & 0xFF
        if (baseId != modelId) {
            info = BLE_MODEL_ID_TO_DRIVER[baseId]
            if (info) { return info }
        }
    }
    if (model) {
        Map<String, String> info = BLE_MODEL_TO_DRIVER[model]
        if (info) { return info }
    }
    return null
}

/**
 * Infers a Shelly BLE model ID from the BTHome data fields when explicit identification
 * (manufacturer data, device_type_id, local_name) is unavailable.
 * Some devices (e.g., RC Button 4) never include identification metadata in their
 * advertisements, but their BTHome data structure is a reliable fingerprint.
 *
 * @param bleData Decoded BTHome data map (e.g., [battery: 100, button: [0,0,0,1]])
 * @return Inferred numeric model ID, or null if data shape is unrecognizable
 */
private static Integer inferBleModelFromData(Map bleData) {
    def buttonData = bleData?.button
    if (buttonData instanceof List && ((List) buttonData).size() == 4) { return 0x0007 }
    if (buttonData != null) { return 0x0001 }
    if (bleData?.temperature != null && bleData?.humidity != null) { return 0x0003 }
    if (bleData?.containsKey('motion')) { return 0x0005 }
    if (bleData?.containsKey('window')) { return 0x0002 }
    return null
}

// ─────────────────────────────────────────────────────────────
// BLE Discovery State Management
// ─────────────────────────────────────────────────────────────

/**
 * Updates the BLE discovery state for a device.
 * Tracks discovered BLE devices with their model, RSSI, last seen time,
 * gateway info, and whether a Hubitat child device has been created.
 * Uses multi-layer model resolution: numeric model ID → string model code → existing entry data.
 *
 * @param mac BLE device MAC address (uppercase, no colons)
 * @param model BLE device model code from local_name (e.g., 'SBHT-003C')
 * @param modelId Numeric model ID from manufacturer data or BTHome device_type_id (may be null)
 * @param rssi Signal strength in dBm
 * @param gatewayName Display name of the WiFi gateway device
 * @param bleData Full BLE data map (for extracting battery, etc.)
 */
private void updateBleDiscoveryState(String mac, String model, Integer modelId, Integer rssi, String gatewayName, Map bleData) {
    Map discoveredBle = state.discoveredBleDevices ?: [:]
    String macKey = mac.toString()

    Map entry = (discoveredBle[macKey] ?: [:]) as Map
    entry.mac = mac
    if (model) { entry.model = model }
    if (modelId != null) { entry.modelId = modelId }

    // Resolve driver info using accumulated entry data (sticky — persists across packets)
    // Cast needed: state round-trips through JSON serialization, so types are not preserved
    Integer effectiveModelId = (entry.modelId != null) ? entry.modelId as Integer : null
    String effectiveModel = (entry.model ?: '') as String
    Map<String, String> driverInfo = resolveBleDriverInfo(effectiveModelId, effectiveModel)

    // Layer 4: Infer model from BTHome data shape when explicit identification is unavailable.
    // Some devices (e.g., RC Button 4) never include model metadata in advertisements.
    if (!driverInfo) {
        Integer inferredId = inferBleModelFromData(bleData)
        if (inferredId != null) {
            driverInfo = resolveBleDriverInfo(inferredId, null)
            if (driverInfo) {
                logDebug("BLE discovery: inferred model ID 0x${String.format('%04X', inferredId)} (${driverInfo.friendlyModel}) for ${mac} from BTHome data shape")
                entry.modelId = inferredId
            }
        }
    }

    if (driverInfo) {
        entry.friendlyModel = driverInfo.friendlyModel
        entry.driverName = driverInfo.driverName
        if (driverInfo.modelCode) { entry.modelCode = driverInfo.modelCode }
    }

    // Check if child device exists
    def child = getChildDevice(mac)
    Boolean isCreated = (child != null)

    // Skip unknown devices that don't have a child device already created.
    // Only known Shelly BLE models (resolved via model ID or model code) are tracked for discovery.
    if (!driverInfo && !isCreated) {
        logInfo("BLE discovery: ignoring unknown device ${mac} (model=${model}, modelId=${modelId})")
        // Remove stale entry if previously stored
        if (discoveredBle.containsKey(macKey)) {
            discoveredBle.remove(macKey)
            state.discoveredBleDevices = discoveredBle
        }
        return
    }

    if (rssi != null) { entry.rssi = rssi }
    if (bleData.battery != null) { entry.battery = bleData.battery as Integer }
    entry.lastSeen = now()
    entry.lastGateway = gatewayName

    entry.isCreated = isCreated
    if (child) {
        entry.hubDeviceId = child.id
        entry.hubDeviceName = child.displayName
        entry.hubDeviceLabel = child.label ?: child.displayName
    }

    discoveredBle[macKey] = entry
    state.discoveredBleDevices = discoveredBle
}

// ─────────────────────────────────────────────────────────────
// BLE Device Creation & Removal
// ─────────────────────────────────────────────────────────────

/**
 * Creates a Hubitat child device for a discovered BLE device.
 * Installs the appropriate prebuilt driver and creates the child with DNI = MAC.
 *
 * @param mac BLE device MAC address (used as DNI)
 */
private void createBleDevice(String mac) {
    String macKey = mac.toString()
    Map discoveredBle = state.discoveredBleDevices ?: [:]
    Map bleInfo = discoveredBle[macKey] as Map

    if (!bleInfo) {
        logError("createBleDevice: no BLE info found for MAC ${mac}")
        appendLog('error', "Failed to create BLE device: no info for ${mac}")
        return
    }

    Integer modelId = bleInfo.modelId != null ? bleInfo.modelId as Integer : null
    String model = (bleInfo.model ?: '') as String
    Map<String, String> driverInfo = resolveBleDriverInfo(modelId, model)

    if (!driverInfo) {
        logError("createBleDevice: unknown model '${model}' (modelId=${modelId}) for MAC ${mac}")
        appendLog('error', "Failed to create BLE device: unknown model for ${mac}")
        return
    }

    String driverName = driverInfo.driverName
    String friendlyModel = driverInfo.friendlyModel
    String bleModel = (driverInfo.modelCode ?: model) as String
    String driverNameWithVersion = "${driverName} v${APP_VERSION}".toString()

    // Check if device already exists
    def existing = getChildDevice(mac)
    if (existing) {
        logWarn("BLE device already exists: ${existing.displayName} (${mac})")
        appendLog('warn', "BLE device already exists: ${existing.displayName}")
        return
    }

    // Install the driver
    if (!installPrebuiltDriver(driverName, [], [:], APP_VERSION)) {
        logError("createBleDevice: failed to install driver '${driverName}'")
        appendLog('error', "Failed to install BLE driver: ${driverName}")
        return
    }

    // Brief pause for driver registration
    pauseExecution(2000)

    String deviceLabel = "${friendlyModel} ${mac[-4..-1]}"
    Map deviceProps = [
        name: deviceLabel,
        label: deviceLabel,
        data: [
            bleMac: mac,
            bleModel: bleModel,
            shellyGen: 'ble'
        ]
    ]

    try {
        def childDevice = addChildDevice('ShellyUSA', driverNameWithVersion, mac, deviceProps)
        logInfo("Created BLE device: ${deviceLabel} using driver ${driverNameWithVersion}")
        appendLog('info', "Created BLE device: ${deviceLabel}")

        // Track driver
        associateDeviceWithDriver(driverNameWithVersion, 'ShellyUSA', mac)

        // Store config
        Map deviceConfigs = state.deviceConfigs ?: [:]
        deviceConfigs[macKey] = [
            driverName: driverNameWithVersion,
            model: bleModel,
            friendlyModel: friendlyModel,
            gen: 'ble',
            isBleDevice: true,
            storedAt: now(),
        ]
        state.deviceConfigs = deviceConfigs

        // Update discovery state
        bleInfo.isCreated = true
        bleInfo.hubDeviceId = childDevice.id
        bleInfo.hubDeviceName = deviceLabel
        bleInfo.hubDeviceLabel = deviceLabel
        discoveredBle[macKey] = bleInfo
        state.discoveredBleDevices = discoveredBle

        childDevice.initialize()

    } catch (Exception e) {
        logError("createBleDevice: failed to create ${deviceLabel} — ${e.message}")
        appendLog('error', "Failed to create BLE device ${deviceLabel}: ${e.message}")
    }
}

/**
 * Removes a Hubitat child device for a BLE device.
 *
 * @param mac BLE device MAC address (the DNI)
 */
private void removeBleDevice(String mac) {
    String macKey = mac.toString()

    try {
        deleteChildDevice(mac)
        logInfo("Removed BLE device: ${mac}")
        appendLog('info', "Removed BLE device: ${mac}")

        // Clean up driver tracking only after successful delete
        Map deviceConfigs = state.deviceConfigs ?: [:]
        Map config = deviceConfigs[macKey] as Map
        if (config?.driverName) {
            String driverKey = "ShellyUSA.${config.driverName}".toString()
            Map allDrivers = state.autoDrivers ?: [:]
            Map driverEntry = allDrivers[driverKey] as Map
            if (driverEntry?.devicesUsing instanceof List) {
                (driverEntry.devicesUsing as List).remove(macKey)
                allDrivers[driverKey] = driverEntry
                state.autoDrivers = allDrivers
            }
        }

        // Clean up device config
        deviceConfigs.remove(macKey)
        state.deviceConfigs = deviceConfigs

        // Update discovery state
        Map discoveredBle = state.discoveredBleDevices ?: [:]
        Map bleInfo = discoveredBle[macKey] as Map
        if (bleInfo) {
            bleInfo.isCreated = false
            bleInfo.hubDeviceId = null
            bleInfo.hubDeviceName = null
            bleInfo.hubDeviceLabel = null
            discoveredBle[macKey] = bleInfo
            state.discoveredBleDevices = discoveredBle
        }
    } catch (Exception e) {
        logError("removeBleDevice: failed to delete ${mac} — ${e.message}")
        appendLog('error', "Failed to remove BLE device ${mac}: ${e.message}")
    }
}

// ─────────────────────────────────────────────────────────────
// BLE Event Routing to Child Devices
// ─────────────────────────────────────────────────────────────

/**
 * Routes decoded BLE data to a Hubitat child device as events.
 * Converts BTHome fields to standard Hubitat attributes.
 *
 * @param mac BLE device MAC address (the child DNI)
 * @param bleData Map of decoded BTHome fields
 */
private void routeBleEventToChild(String mac, Map bleData) {
    def child = getChildDevice(mac)
    if (!child) { return }

    List<Map> events = buildBleEvents(bleData)

    events.each { Map evt ->
        childSendEventHelper(child, evt)
    }

    // Always send presence and timestamp
    childSendEventHelper(child, [name: 'presence', value: 'present', descriptionText: 'BLE advertisement received'])
    childSendEventHelper(child, [name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss')])

    // Track last contact time for presence management
    String macKey = mac.toString()
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Map config = (deviceConfigs[macKey] ?: [:]) as Map
    config.lastBleContact = now()
    deviceConfigs[macKey] = config
    state.deviceConfigs = deviceConfigs

    logTrace("routeBleEventToChild: sent ${events.size()} events to ${child.displayName}")
}

/**
 * Converts BLE data fields to Hubitat event maps.
 * Handles temperature unit conversion, motion/contact states,
 * and multi-button BTHome encoding.
 *
 * @param bleData Map of decoded BTHome fields
 * @return List of event maps suitable for sendEvent()
 */
private List<Map> buildBleEvents(Map bleData) {
    List<Map> events = []

    // Battery
    if (bleData.battery != null) {
        Integer battery = bleData.battery as Integer
        events.add([name: 'battery', value: battery, unit: '%',
            descriptionText: "Battery is ${battery}%"])
    }

    // Temperature (BTHome sends Celsius; convert if hub uses Fahrenheit)
    if (bleData.temperature != null) {
        BigDecimal tempC = bleData.temperature as BigDecimal
        String scale = getTemperatureScale()
        BigDecimal temp = (scale == 'F') ? cToF(tempC) : tempC
        temp = temp.setScale(1, BigDecimal.ROUND_HALF_UP)
        events.add([name: 'temperature', value: temp, unit: "°${scale}",
            descriptionText: "Temperature is ${temp}°${scale}"])
    }

    // Humidity
    if (bleData.humidity != null) {
        Integer humidity = Math.round(bleData.humidity as BigDecimal) as Integer
        events.add([name: 'humidity', value: humidity, unit: '%rh',
            descriptionText: "Humidity is ${humidity}%"])
    }

    // Illuminance
    if (bleData.illuminance != null) {
        Integer lux = Math.round(bleData.illuminance as BigDecimal) as Integer
        events.add([name: 'illuminance', value: lux, unit: 'lux',
            descriptionText: "Illuminance is ${lux} lux"])
    }

    // Motion (0=inactive, 1=active)
    if (bleData.motion != null) {
        String motionVal = (bleData.motion as Integer) == 1 ? 'active' : 'inactive'
        events.add([name: 'motion', value: motionVal,
            descriptionText: "Motion is ${motionVal}"])
    }

    // Window/Door contact (0=closed, 1=open)
    if (bleData.window != null) {
        String contactVal = (bleData.window as Integer) == 1 ? 'open' : 'closed'
        events.add([name: 'contact', value: contactVal,
            descriptionText: "Contact is ${contactVal}"])
    }

    // Rotation (tilt angle)
    if (bleData.rotation != null) {
        BigDecimal tilt = bleData.rotation as BigDecimal
        events.add([name: 'tilt', value: tilt, unit: '°',
            descriptionText: "Tilt is ${tilt}°"])
    }

    // Button events (BTHome: 1=push, 2=double, 3=triple, 4+=held, 254=released)
    if (bleData.button != null) {
        if (bleData.button instanceof List) {
            // Multi-button device: array index = button number (0-based → 1-based)
            List buttonList = bleData.button as List
            buttonList.eachWithIndex { btnVal, Integer idx ->
                Integer buttonNum = idx + 1
                Integer action = btnVal as Integer
                Map buttonEvent = buildButtonEvent(action, buttonNum)
                if (buttonEvent) { events.add(buttonEvent) }
            }
        } else {
            // Single button device
            Integer action = bleData.button as Integer
            Map buttonEvent = buildButtonEvent(action, 1)
            if (buttonEvent) { events.add(buttonEvent) }
        }
    }

    return events
}

/**
 * Converts a BTHome button action code to a Hubitat button event.
 * BTHome codes: 1=push, 2=double-tap, 3=triple-tap, 4/32+=held, 254=released, 0=none.
 *
 * @param action The BTHome button action code
 * @param buttonNum The button number (1-based)
 * @return Event map or null if no action (action == 0)
 */
private Map buildButtonEvent(Integer action, Integer buttonNum) {
    switch (action) {
        case 0:
            return null // No action
        case 1:
            return [name: 'pushed', value: buttonNum, isStateChange: true,
                descriptionText: "Button ${buttonNum} pushed"]
        case 2:
            return [name: 'doubleTapped', value: buttonNum, isStateChange: true,
                descriptionText: "Button ${buttonNum} double-tapped"]
        case 3:
            return [name: 'tripleTapped', value: buttonNum, isStateChange: true,
                descriptionText: "Button ${buttonNum} triple-tapped"]
        case 254:
            return [name: 'released', value: buttonNum, isStateChange: true,
                descriptionText: "Button ${buttonNum} released"]
        default:
            // 4+ = long press / held
            return [name: 'held', value: buttonNum, isStateChange: true,
                descriptionText: "Button ${buttonNum} held"]
    }
}

/**
 * Helper to get the hub's temperature scale.
 * Non-static because getLocation() is dynamic.
 *
 * @return 'F' or 'C'
 */
private String getTemperatureScale() {
    return getLocationHelper()?.temperatureScale ?: 'F'
}

// ─────────────────────────────────────────────────────────────
// BLE Presence Management
// ─────────────────────────────────────────────────────────────

/**
 * Checks all BLE devices for presence timeout.
 * Called on a 5-minute schedule. If a device hasn't been heard from
 * within its presenceTimeout setting, marks it as 'not present'.
 */
void checkBlePresence() {
    Map deviceConfigs = state.deviceConfigs ?: [:]
    Boolean anyChanged = false

    deviceConfigs.each { String key, configVal ->
        Map config = configVal as Map
        if (config?.isBleDevice != true) { return }

        Long lastContact = config.lastBleContact as Long ?: 0L
        if (lastContact == 0L) { return }

        def child = getChildDevice(key)
        if (!child) { return }

        // Get presenceTimeout from device settings (default 60 minutes)
        Integer timeoutMinutes = 60
        try {
            def deviceTimeout = child.getSetting('presenceTimeout')
            if (deviceTimeout != null) { timeoutMinutes = deviceTimeout as Integer }
        } catch (Exception e) {
            logDebug("checkBlePresence: getSetting failed for ${child.displayName}, using default ${timeoutMinutes}min")
        }

        Long timeoutMs = timeoutMinutes * 60L * 1000L
        Long elapsed = now() - lastContact

        if (elapsed > timeoutMs) {
            String currentPresence = child.currentValue('presence')
            if (currentPresence != 'not present') {
                childSendEventHelper(child, [name: 'presence', value: 'not present',
                    descriptionText: "No BLE data for ${timeoutMinutes} minutes"])
                logInfo("BLE device ${child.displayName} marked as not present (no data for ${timeoutMinutes} min)")
                anyChanged = true
            }
        }
    }

    if (anyChanged) {
        sendEvent(name: 'bleTable', value: 'presence')
    }
}

// ─────────────────────────────────────────────────────────────
// BLE Gateway Toggle
// ─────────────────────────────────────────────────────────────

/**
 * Toggles BLE gateway mode on a WiFi Shelly device.
 * Enables or disables the HubitatBLEHelper script and BLE scanning.
 *
 * @param ip The IP address of the WiFi gateway device
 */
private void toggleBleGateway(String ip) {
    List bleGateways = (state.bleGateways ?: []) as List
    if (bleGateways.contains(ip)) {
        disableBleGateway(ip)
        bleGateways.remove(ip)
        appendLog('info', "BLE gateway disabled on ${ip}")
    } else {
        if (enableBleGateway(ip)) {
            bleGateways.add(ip)
            appendLog('info', "BLE gateway enabled on ${ip}")
        }
    }
    state.bleGateways = bleGateways
}

/**
 * Enables BLE gateway mode on a device.
 * Enables Bluetooth, downloads and installs the HubitatBLEHelper script,
 * and writes the hub IP to device KVS.
 *
 * @param ip The IP address of the Shelly device
 */
private Boolean enableBleGateway(String ip) {
    String uri = "http://${ip}/rpc"
    Boolean hasAuth = authIsEnabled() == true && getAuth().size() > 0

    // Step 1: Enable Bluetooth with observer
    logInfo("Enabling Bluetooth on ${ip}...")
    LinkedHashMap bleCmd = bleSetConfigCommand(true, true, true)
    if (hasAuth) { bleCmd.auth = getAuth() }
    postCommandSync(bleCmd, uri)

    // Step 2: Download BLE helper script from GitHub
    String branch = GITHUB_BRANCH
    String scriptUrl = "https://raw.githubusercontent.com/${GITHUB_REPO}/${branch}/Scripts/HubitatBLEHelper.js"
    String scriptCode = downloadFile(scriptUrl)
    if (!scriptCode) {
        logError("enableBleGateway: failed to download HubitatBLEHelper.js from GitHub")
        appendLog('error', "Failed to download BLE script for ${ip}")
        return false
    }

    // Step 3: Check if script exists, create or update
    List<Map> installedScripts = listDeviceScripts(ip)
    Map existingScript = installedScripts?.find { (it.name ?: '') == 'HubitatBLEHelper' }

    Integer scriptId
    if (existingScript) {
        scriptId = existingScript.id as Integer
        logInfo("Updating HubitatBLEHelper script (id: ${scriptId}) on ${ip}...")

        LinkedHashMap stopCmd = scriptStopCommand(scriptId)
        if (hasAuth) { stopCmd.auth = getAuth() }
        postCommandSync(stopCmd, uri)
    } else {
        logInfo("Creating HubitatBLEHelper script on ${ip}...")
        LinkedHashMap createCmd = scriptCreateCommand('HubitatBLEHelper')
        if (hasAuth) { createCmd.auth = getAuth() }
        LinkedHashMap createResult = postCommandSync(createCmd, uri)
        scriptId = ((createResult?.result as Map)?.id ?: (createResult?.id)) as Integer
        if (scriptId == null) {
            logError("enableBleGateway: failed to create script on ${ip}")
            appendLog('error', "Failed to create BLE script on ${ip}")
            return false
        }
    }

    // Step 4: Upload script code (chunked)
    try {
        uploadScriptInChunks(scriptId, scriptCode, uri, hasAuth)
    } catch (Exception e) {
        logError("enableBleGateway: script upload failed on ${ip} — ${e.message}")
        appendLog('error', "Failed to upload BLE script to ${ip}: ${e.message}")
        return false
    }

    // Step 5: Enable and start
    LinkedHashMap enableCmd = scriptEnableCommand(scriptId)
    if (hasAuth) { enableCmd.auth = getAuth() }
    postCommandSync(enableCmd, uri)

    LinkedHashMap startCmd = scriptStartCommand(scriptId)
    if (hasAuth) { startCmd.auth = getAuth() }
    postCommandSync(startCmd, uri)

    // Step 6: Write hub IP to KVS
    writeHubitatIpToKVS(ip)

    logInfo("BLE gateway enabled on ${ip}")
    return true
}

/**
 * Disables BLE gateway mode on a device.
 * Removes the HubitatBLEHelper script and disables the BLE observer.
 *
 * @param ip The IP address of the Shelly device
 */
private void disableBleGateway(String ip) {
    String uri = "http://${ip}/rpc"
    Boolean hasAuth = authIsEnabled() == true && getAuth().size() > 0

    List<Map> installedScripts = listDeviceScripts(ip)
    Map existingScript = installedScripts?.find { (it.name ?: '') == 'HubitatBLEHelper' }

    if (existingScript) {
        Integer scriptId = existingScript.id as Integer
        logInfo("Removing HubitatBLEHelper (id: ${scriptId}) from ${ip}...")

        LinkedHashMap deleteCmd = scriptDeleteCommand(scriptId)
        if (hasAuth) { deleteCmd.auth = getAuth() }
        postCommandSync(deleteCmd, uri)
    }

    // Disable BLE observer but keep BLE enabled for RPC
    logInfo("Disabling BLE observer on ${ip}...")
    LinkedHashMap bleCmd = bleSetConfigCommand(true, true, false)
    if (hasAuth) { bleCmd.auth = getAuth() }
    postCommandSync(bleCmd, uri)

    logInfo("BLE gateway disabled on ${ip}")
}

/**
 * Checks if a device IP has BLE gateway mode enabled.
 *
 * @param ip The device IP address
 * @return true if BLE gateway is enabled
 */
private Boolean isBleGatewayEnabled(String ip) {
    List bleGateways = (state.bleGateways ?: []) as List
    return bleGateways.contains(ip)
}

// ─────────────────────────────────────────────────────────────
// BLE Device Table UI
// ─────────────────────────────────────────────────────────────

/**
 * Renders the BLE device discovery/management table.
 * Uses SSR for live updates when new BLE advertisements arrive.
 *
 * @return HTML string with SSR wrapper, CSS, and table markup
 */
private String displayBleDeviceTable() {
    String tableMarkup = renderBleTableMarkup()
    return "<span class='ssr-app-state-${getAppIdHelper()}-bleTable' id='ble-table'>" +
        "<div id='ble-table-wrapper'>${tableMarkup}</div></span>"
}

/**
 * Fires an SSR update for the BLE device table.
 */
void fireBleTableSSR() {
    sendEvent(name: 'bleTable', value: 'update')
}

/**
 * Renders the BLE device table markup.
 * Shows discovered and created BLE devices with status, battery, RSSI, and actions.
 *
 * @return HTML table markup string
 */
private String renderBleTableMarkup() {
    Map discoveredBle = state.discoveredBleDevices ?: [:]
    if (discoveredBle.size() == 0) {
        return "<p style='color:#9E9E9E'>No BLE devices discovered. Enable BLE gateway mode on WiFi devices (via the BLE GW column in the table above) to start receiving BLE advertisements.</p>"
    }

    // Filter to known devices only and refresh cached display fields from current model maps.
    // Stale entries (stored before model map updates) get their friendlyModel/driverName refreshed here.
    List<Map> deviceList = []
    Boolean stateChanged = false
    discoveredBle.each { String macKey, bleVal ->
        Map entry = bleVal as Map
        Integer entryModelId = entry.modelId != null ? entry.modelId as Integer : null
        String entryModel = (entry.model ?: '') as String
        Map<String, String> driverInfo = resolveBleDriverInfo(entryModelId, entryModel)
        Boolean isCreated = entry.isCreated ?: false
        if (!driverInfo && !isCreated) { return }
        // Refresh cached display fields from resolved driver info
        if (driverInfo) {
            if (entry.friendlyModel != driverInfo.friendlyModel ||
                entry.driverName != driverInfo.driverName ||
                (driverInfo.modelCode && entry.modelCode != driverInfo.modelCode)) {
                entry.friendlyModel = driverInfo.friendlyModel
                entry.driverName = driverInfo.driverName
                if (driverInfo.modelCode) { entry.modelCode = driverInfo.modelCode }
                discoveredBle[macKey] = entry
                stateChanged = true
            }
        }
        deviceList.add(entry)
    }
    if (stateChanged) { state.discoveredBleDevices = discoveredBle }

    if (deviceList.size() == 0) {
        return "<p style='color:#9E9E9E'>No BLE devices discovered. Enable BLE gateway mode on WiFi devices (via the BLE GW column in the table above) to start receiving BLE advertisements.</p>"
    }

    // Sort: created devices first, then by friendly model, then by MAC
    deviceList.sort { Map a, Map b ->
        if (a.isCreated != b.isCreated) { return a.isCreated ? -1 : 1 }
        String nameA = ((a.friendlyModel ?: a.mac) as String).toLowerCase()
        String nameB = ((b.friendlyModel ?: b.mac) as String).toLowerCase()
        return nameA <=> nameB
    }

    StringBuilder str = new StringBuilder()
    str.append("<div style='overflow-x:auto'><table class='mdl-data-table'>")
    str.append("<thead><tr>")
    str.append("<th>Action</th>")
    str.append("<th>Bluetooth Device</th>")
    str.append("<th>Label</th>")
    str.append("<th>MAC</th>")
    str.append("<th>RSSI</th>")
    str.append("<th>Battery</th>")
    str.append("<th>Last Seen</th>")
    str.append("<th>Gateway</th>")
    str.append("</tr></thead><tbody>")

    deviceList.each { Map entry ->
        String mac = entry.mac as String
        String model = (entry.modelCode ?: entry.model ?: '') as String
        String friendlyModel = (entry.friendlyModel as String) ?:
            model ?:
            (entry.modelId != null ? "ID:0x${String.format('%04X', entry.modelId as Integer)}" : mac)
        Integer rssi = entry.rssi as Integer
        Integer battery = entry.battery as Integer
        Long lastSeen = entry.lastSeen as Long ?: 0L
        String gateway = entry.lastGateway ?: '—'
        Boolean isCreated = entry.isCreated ?: false

        // Device name column
        String deviceCell
        if (isCreated && entry.hubDeviceId) {
            String devLink = "<a href='/device/edit/${entry.hubDeviceId}' target='_blank'>${entry.hubDeviceName ?: friendlyModel}</a>"
            deviceCell = "<td class='device-link'>${devLink}</td>"
        } else {
            deviceCell = "<td>${friendlyModel}</td>"
        }

        // RSSI color
        String rssiCell
        if (rssi != null) {
            String rssiColor = rssi > -60 ? '#4CAF50' : (rssi > -80 ? '#FF9800' : '#F44336')
            rssiCell = "<td style='color:${rssiColor}'>${rssi} dBm</td>"
        } else {
            rssiCell = "<td class='status-na'>—</td>"
        }

        // Battery color
        String batteryCell
        if (battery != null) {
            String battColor = battery > 20 ? '#4CAF50' : (battery > 10 ? '#FF9800' : '#F44336')
            batteryCell = "<td style='color:${battColor}'>${battery}%</td>"
        } else {
            batteryCell = "<td class='status-na'>—</td>"
        }

        // Last seen
        String lastSeenStr = '—'
        if (lastSeen > 0) {
            Long elapsedMs = now() - lastSeen
            Long elapsedMin = (Long)(elapsedMs / 60000L)
            if (elapsedMin < 1) { lastSeenStr = 'just now' }
            else if (elapsedMin < 60) { lastSeenStr = "${elapsedMin}m ago" }
            else {
                Long hours = (Long)(elapsedMin / 60L)
                if (hours < 24) { lastSeenStr = "${hours}h ago" }
                else { lastSeenStr = "${(Long)(hours / 24L)}d ago" }
            }
        }

        // Action button
        String actionCell
        if (isCreated) {
            String deleteIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px'></iconify-icon>"
            actionCell = "<td>${buttonLink("removeBle|${mac}".toString(), deleteIcon, '#F44336', '20px')}</td>"
        } else if (entry.driverName) {
            String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded' style='font-size:20px'></iconify-icon>"
            actionCell = "<td>${buttonLink("createBle|${mac}".toString(), addIcon, '#4CAF50', '20px')}</td>"
        } else {
            actionCell = "<td class='status-na' title='Unknown model — no driver available'>—</td>"
        }

        // Label column (click to edit for created devices)
        String labelCell
        if (isCreated && entry.hubDeviceId) {
            String currentLabel = (entry.hubDeviceLabel ?: entry.hubDeviceName ?: '') as String
            String editIcon = "<iconify-icon icon='material-symbols:edit' style='font-size:14px;vertical-align:middle;margin-left:4px'></iconify-icon>"
            String editBtn = buttonLink("editBleLabel|${mac}".toString(), "${currentLabel} ${editIcon}", '#424242', '14px')
            labelCell = "<td>${editBtn}</td>"
        } else {
            labelCell = "<td class='status-na'>&ndash;</td>"
        }

        str.append("<tr>")
        str.append(actionCell)
        str.append(deviceCell)
        str.append(labelCell)
        str.append("<td style='font-family:monospace;font-size:12px'>${mac}</td>")
        str.append(rssiCell)
        str.append(batteryCell)
        str.append("<td>${lastSeenStr}</td>")
        str.append("<td>${gateway}</td>")
        str.append("</tr>")
    }

    str.append("</tbody></table></div>")
    return str.toString()
}

// ─────────────────────────────────────────────────────────────
// BLE Gateway Column in WiFi Config Table
// ─────────────────────────────────────────────────────────────

/**
 * Renders the BLE gateway toggle cell for a device row in the config table.
 * Shows a Bluetooth icon (blue=enabled, gray=disabled) for Gen 2+ non-battery devices.
 * Gen 1 and battery devices show "n/a".
 *
 * @param ip The device IP address
 * @param gen The device generation ('1', '2', '3', 'ble')
 * @param isBattery Whether the device is battery-powered
 * @return HTML table cell content
 */
private String renderBleGatewayCell(String ip, String gen, Boolean isBattery) {
    if (gen == '1' || gen == 'ble' || isBattery) {
        return "<span class='status-na'>n/a</span>"
    }

    Boolean enabled = isBleGatewayEnabled(ip)
    String icon = enabled ?
        "<iconify-icon icon='material-symbols:bluetooth' style='font-size:20px'></iconify-icon>" :
        "<iconify-icon icon='material-symbols:bluetooth-disabled' style='font-size:20px'></iconify-icon>"
    String color = enabled ? '#2196F3' : '#9E9E9E'

    return buttonLink("toggleBleGw|${ip}".toString(), icon, color, '20px')
}

// ─────────────────────────────────────────────────────────────
// Legacy Bluetooth Functions (kept for backward compatibility)
// ─────────────────────────────────────────────────────────────

/**
 * Enables Bluetooth on the device (BLE + RPC + observer).
 * Used internally by enableBleGateway.
 */
void enableBluetooth() {
  logDebug('Enabling Bluetooth on Shelly device...')
  postCommandSync(bleSetConfigCommand(true, true, true))
}

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
    String driverName = additionalId == null ? 'Shelly Autoconf Switch' : 'Shelly Autoconf OverUnder Switch'
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
    String driverName = 'Shelly Autoconf Dimmer'
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
    String driverName = 'Shelly Autoconf RGB'
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
    String driverName = 'Shelly Autoconf RGBW'
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
    String driverName = 'Shelly Autoconf Switch PM'
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
    String driverName = 'Shelly Autoconf EM'
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
    String driverName = 'Shelly Autoconf EM'
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
  String driverName = "Shelly Autoconf Input ${inputType}"
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
ChildDeviceWrapper createChildCover(Integer id, String driverName = 'Shelly Autoconf Cover') {
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
  ChildDeviceWrapper child = createChildCover(id, 'Shelly Autoconf Cover PM')
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
  String driverName = "Shelly Autoconf Temperature Peripheral"
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
  String driverName = "Shelly Autoconf Humidity Peripheral"
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
  String driverName = "Shelly Autoconf Temperature & Humidity Peripheral"
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
  String driverName = "Shelly Autoconf RGB"
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
  String driverName = "Shelly Autoconf RGB"
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
  String driverName = "Shelly Autoconf RGB"
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
  String driverName = "Shelly Autoconf Polling Voltage Sensor"
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

/**
 * Checks whether Gen 1 Basic Auth credentials are available.
 * Gen 1 devices use HTTP Basic Auth with username 'admin' and the
 * app-configured device password (same password used for Gen 2 digest auth).
 *
 * @return true if a device password is configured
 */
@CompileStatic
Boolean authIsEnabledGen1() {
  String password = getAppSettings()?.devicePassword?.toString()
  return password != null && password != ''
}

// @CompileStatic
// void performAuthCheck() { shellyGetStatusWs('authCheck') }

/**
 * Returns a Base64-encoded Basic Auth header value for Gen 1 devices.
 * Gen 1 devices always use username 'admin' with the app-configured password.
 *
 * @return Base64-encoded "admin:password" string, or null if no password configured
 */
@CompileStatic
String getBasicAuthHeader() {
  String password = getAppSettings()?.devicePassword?.toString()
  if (password != null && password != '') {
    return base64Encode("admin:${password}".toString())
  }
  return null
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
// ║  Driver Auto-Update                                          ║
// ╚══════════════════════════════════════════════════════════════╝
/* #region Driver Auto-Update */

/**
 * Reinstalls all tracked prebuilt drivers with the current app version.
 * Called on app version change to update driver version suffixes.
 */
private void reinstallAllPrebuiltDrivers() {
    initializeDriverTracking()

    String version = getAppVersion()
    Map allDrivers = state.autoDrivers ?: [:]
    if (allDrivers.isEmpty()) {
        logInfo("No tracked drivers to update")
        return
    }

    logInfo("Updating ${allDrivers.size()} driver(s) to v${version}...")
    appendLog('info', "Updating ${allDrivers.size()} driver(s) to v${version}...")

    int updated = 0
    int errors = 0
    allDrivers.each { key, info ->
        String baseName = (info.name ?: '').replaceAll(/\s+v\d+(\.\d+)*$/, '')
        Boolean isComponent = info.isComponentDriver ?: false

        if (isComponent) {
            logDebug("Skipping component driver update: ${key} (version-independent)")
            return
        }

        if (PREBUILT_DRIVERS.containsKey(baseName)) {
            List<String> components = (info.components ?: []) as List<String>
            Map<String, Boolean> pmMap = (info.componentPowerMonitoring ?: [:]) as Map<String, Boolean>
            Boolean success = installPrebuiltDriver(baseName, components, pmMap, version)
            if (success) { updated++ } else { errors++ }
        } else {
            logWarn("No prebuilt driver for '${baseName}' — cannot update. Add to PREBUILT_DRIVERS.")
            errors++
        }
    }

    logInfo("Driver update complete: ${updated} updated, ${errors} error(s)")
    appendLog('info', "Driver update complete: ${updated} updated, ${errors} error(s)")

    // Fire app event to trigger SSR update on main page
    sendEvent(name: 'driverRebuildStatus', value: 'complete')
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

        // Get the current version of the app code (required for update)
        String appVersion = getAppCodeVersion(cookie, appCodeId)
        if (!appVersion) {
            logWarn("Auto-update: could not retrieve app code version, proceeding without it")
            appVersion = ""
        }

        // HPM-style update: post id + version + source to /app/ajax/update
        Map updateParams = [
            uri: "http://127.0.0.1:8080",
            path: '/app/ajax/update',
            requestContentType: 'application/x-www-form-urlencoded',
            headers: [
                'Cookie': cookie,
                'Connection': 'keep-alive'
            ],
            body: [id: appCodeId, version: appVersion, source: sourceCode],
            timeout: 420
        ]

        Boolean result = false
        httpPost(updateParams) { resp ->
            if (resp?.data?.status == 'success') {
                logInfo("Auto-update: app code updated successfully")
                result = true
            } else if (resp?.status == 200) {
                logInfo("Auto-update: app code update returned HTTP 200")
                result = true
            } else {
                logError("Auto-update: update failed - HTTP ${resp?.status}")
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
        // HPM-style: /hub2/userAppTypes returns JSON with id, name, namespace
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: '/hub2/userAppTypes',
            headers: ['Cookie': cookie],
            timeout: 30
        ]

        Integer codeId = null
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data) {
                def appEntry = resp.data.find { it.namespace == 'ShellyUSA' && (it.name == 'Shelly Device Manager' || it.name == 'Shelly mDNS Discovery') }
                if (appEntry) {
                    codeId = appEntry.id as Integer
                    logDebug("Found app code ID: ${codeId}")
                } else {
                    logError("getAppCodeId: could not find ShellyUSA app in /hub2/userAppTypes (${resp.data.size()} entries)")
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
 * Gets the internal version counter of this app's code from the hub.
 * This is the hub's internal revision counter (not the semantic version),
 * required by {@code /app/ajax/update} to apply updates.
 *
 * @param cookie The authentication cookie
 * @param appCodeId The app code ID
 * @return The internal version counter string, or null if not found
 */
private String getAppCodeVersion(String cookie, Integer appCodeId) {
    try {
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: "/app/ajax/code",
            query: [id: appCodeId],
            headers: ['Cookie': cookie],
            timeout: 30
        ]

        String codeVersion = null
        httpGet(params) { resp ->
            if (resp?.status == 200 && resp.data?.version != null) {
                codeVersion = resp.data.version.toString()
                logDebug("App code internal version: ${codeVersion}")
            }
        }
        return codeVersion
    } catch (Exception e) {
        logError("Failed to get app code version: ${e.message}")
        return null
    }
}

/**
 * Gets the installed semantic version string of this app's code from the hub
 * by parsing the source code.
 *
 * @param cookie The authentication cookie
 * @param appCodeId The app code ID
 * @return The semantic version string, or null if not found
 */
private String getInstalledAppVersion(String cookie, Integer appCodeId) {
    try {
        Map params = [
            uri: "http://127.0.0.1:8080",
            path: "/app/ajax/code",
            query: [id: appCodeId],
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
 * Removes stale driver tracking entries from {@code state.autoDrivers}.
 * For each base driver name (e.g., "Shelly Autoconf Single Switch PM"),
 * keeps only the entry with the highest version and removes older ones.
 * This prevents accumulation of old-version entries across app updates.
 */
private void pruneStaleDriverTracking() {
    Map autoDrivers = state.autoDrivers
    if (!autoDrivers || autoDrivers.size() <= 1) { return }

    // Group entries by base driver name (without version suffix)
    Map<String, List<String>> baseNameGroups = [:]
    autoDrivers.each { key, value ->
        String baseName = key.toString().replaceAll(/\s+v\d+(\.\d+)*$/, '')
        if (!baseNameGroups[baseName]) { baseNameGroups[baseName] = [] }
        baseNameGroups[baseName].add(key.toString())
    }

    // For each group with multiple entries, keep only the latest version
    int removed = 0
    baseNameGroups.each { baseName, keys ->
        if (keys.size() <= 1) { return }

        // Sort by version descending — extract version from key
        keys.sort { a, b ->
            def matchA = (a =~ /v(\d+(\.\d+)*)$/)
            def matchB = (b =~ /v(\d+(\.\d+)*)$/)
            String vA = matchA.find() ? matchA[0][1] : '0'
            String vB = matchB.find() ? matchB[0][1] : '0'
            List<Integer> partsA = vA.tokenize('.').collect { String s -> s as Integer }
            List<Integer> partsB = vB.tokenize('.').collect { String s -> s as Integer }
            // Compare version parts descending
            for (int i = 0; i < Math.max(partsA.size(), partsB.size()); i++) {
                int pA = i < partsA.size() ? partsA[i] : 0
                int pB = i < partsB.size() ? partsB[i] : 0
                if (pA != pB) { return pB <=> pA }
            }
            return 0
        }

        // Keep the first (highest version), remove the rest
        keys.drop(1).each { String oldKey ->
            logInfo("Pruning stale driver tracking entry: ${oldKey}")
            autoDrivers.remove(oldKey)
            removed++
        }
    }

    if (removed > 0) {
        state.autoDrivers = autoDrivers
        logInfo("Pruned ${removed} stale driver tracking entry(s)")
    }
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

  // Remove old version entries for the same base driver name
  // The base name is the driver name without the version suffix (e.g., "Shelly Autoconf Single Switch PM")
  String baseName = driverName.replaceAll(/\s+v\d+(\.\d+)*$/, '')
  List<String> keysToRemove = []
  state.autoDrivers.each { k, v ->
    if (k != key && k.toString().contains(baseName)) {
      keysToRemove.add(k.toString())
    }
  }
  keysToRemove.each { String oldKey ->
    logDebug("Removing old driver tracking entry: ${oldKey}")
    state.autoDrivers.remove(oldKey)
  }

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
 * Handles on() command from a parent device to turn on all child switches.
 * Finds all child devices with componentType "switch" under the given parent
 * and sends an on command to each.
 *
 * @param parentDevice The parent device requesting all switches on
 */
void componentOnAll(def parentDevice) {
  logDebug("componentOnAll() called from parent: ${parentDevice.displayName}")
  String parentDni = parentDevice.deviceNetworkId
  getChildDevices()?.each { child ->
    if (child.deviceNetworkId.startsWith("${parentDni}-switch-")) {
      sendSwitchCommand(child, true)
    }
  }
}

/**
 * Handles off() command from a parent device to turn off all child switches.
 * Finds all child devices with componentType "switch" under the given parent
 * and sends an off command to each.
 *
 * @param parentDevice The parent device requesting all switches off
 */
void componentOffAll(def parentDevice) {
  logDebug("componentOffAll() called from parent: ${parentDevice.displayName}")
  String parentDni = parentDevice.deviceNetworkId
  getChildDevices()?.each { child ->
    if (child.deviceNetworkId.startsWith("${parentDni}-switch-")) {
      sendSwitchCommand(child, false)
    }
  }
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

    // Gen 1: GET /relay/{id}?turn=on|off
    if (isGen1Device(childDevice)) {
      Integer switchId = extractComponentId(childDevice, 'switchId')
      logDebug("sendSwitchCommand: Gen 1 relay/${switchId}?turn=${action}")
      sendGen1Get(ipAddress, "relay/${switchId}", [turn: action])
      return
    }

    // Gen 2/3: JSON-RPC Switch.Set
    String rpcUri = "http://${ipAddress}/rpc"
    Integer switchId = extractComponentId(childDevice, 'switchId')
    logDebug("sendSwitchCommand: sending ${action} command to ${rpcUri} (switch:${switchId})")

    LinkedHashMap command = switchSetCommand(onState, switchId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("sendSwitchCommand: response from ${ipAddress}: ${response}")

  } catch (Exception e) {
    logError("sendSwitchCommand(${action}) exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Backward-compatible stub for battery level requests.
 * Battery data is now delivered via webhook URL tokens piggybacked on
 * temperature/humidity webhooks. This stub is kept so existing child
 * drivers that call {@code parent?.componentRequestBatteryLevel(device)}
 * won't throw errors during the transition.
 *
 * @param childDevice The child device requesting battery level (ignored)
 * @deprecated Battery data now arrives via webhook URL supplemental tokens
 */
void componentRequestBatteryLevel(def childDevice) {
    logDebug("componentRequestBatteryLevel: no-op (battery data now delivered via webhook tokens)")
}

/**
 * Extracts a numeric component ID from a child device's data values.
 * Looks for a data value with the specified key name. Falls back to 0
 * for backward compatibility with single-component devices.
 *
 * @param childDevice The child device to extract the component ID from
 * @param dataKey The data value key name (e.g., 'switchId', 'coverId', 'lightId')
 * @return The component ID, or 0 if not found
 */
private Integer extractComponentId(def childDevice, String dataKey) {
  String idValue = childDevice.getDataValue(dataKey)
  if (idValue != null) {
    try {
      return idValue as Integer
    } catch (Exception e) {
      logDebug("extractComponentId: could not parse ${dataKey}='${idValue}' as Integer, defaulting to 0")
    }
  }
  return 0
}

// ═══════════════════════════════════════════════════════════════
// Cover Component Handlers
// ═══════════════════════════════════════════════════════════════

/**
 * Handles open() command from child cover component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentOpen(def childDevice) {
  logDebug("componentOpen() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentOpen: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /roller/{id}?go=open
    if (isGen1Device(childDevice)) {
      Integer coverId = extractComponentId(childDevice, 'coverId')
      logDebug("componentOpen: Gen 1 roller/${coverId}?go=open")
      sendGen1Get(ipAddress, "roller/${coverId}", [go: 'open'])
      return
    }

    // Gen 2/3: JSON-RPC Cover.Open
    String rpcUri = "http://${ipAddress}/rpc"
    Integer coverId = extractComponentId(childDevice, 'coverId')
    LinkedHashMap command = coverOpenCommand(coverId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentOpen: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentOpen exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles close() command from child cover component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentClose(def childDevice) {
  logDebug("componentClose() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentClose: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /roller/{id}?go=close
    if (isGen1Device(childDevice)) {
      Integer coverId = extractComponentId(childDevice, 'coverId')
      logDebug("componentClose: Gen 1 roller/${coverId}?go=close")
      sendGen1Get(ipAddress, "roller/${coverId}", [go: 'close'])
      return
    }

    // Gen 2/3: JSON-RPC Cover.Close
    String rpcUri = "http://${ipAddress}/rpc"
    Integer coverId = extractComponentId(childDevice, 'coverId')
    LinkedHashMap command = coverCloseCommand(coverId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentClose: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentClose exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles setPosition() command from child cover component devices.
 *
 * @param childDevice The child device that sent the command
 * @param position Target position (0 = closed, 100 = open)
 */
void componentSetPosition(def childDevice, Integer position) {
  logDebug("componentSetPosition(${position}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentSetPosition: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /roller/{id}?go=to_pos&roller_pos={pos}
    if (isGen1Device(childDevice)) {
      Integer coverId = extractComponentId(childDevice, 'coverId')
      logDebug("componentSetPosition: Gen 1 roller/${coverId}?go=to_pos&roller_pos=${position}")
      sendGen1Get(ipAddress, "roller/${coverId}", [go: 'to_pos', roller_pos: position.toString()])
      return
    }

    // Gen 2/3: JSON-RPC Cover.GoToPosition
    String rpcUri = "http://${ipAddress}/rpc"
    Integer coverId = extractComponentId(childDevice, 'coverId')
    LinkedHashMap command = coverGoToPositionCommand(coverId, position)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentSetPosition: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentSetPosition exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles stopPositionChange() command from child cover component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentStop(def childDevice) {
  logDebug("componentStop() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentStop: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /roller/{id}?go=stop
    if (isGen1Device(childDevice)) {
      Integer coverId = extractComponentId(childDevice, 'coverId')
      logDebug("componentStop: Gen 1 roller/${coverId}?go=stop")
      sendGen1Get(ipAddress, "roller/${coverId}", [go: 'stop'])
      return
    }

    // Gen 2/3: JSON-RPC Cover.Stop
    String rpcUri = "http://${ipAddress}/rpc"
    Integer coverId = extractComponentId(childDevice, 'coverId')
    LinkedHashMap command = coverStopCommand(coverId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentStop: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentStop exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

// ═══════════════════════════════════════════════════════════════
// Light Component Handlers
// ═══════════════════════════════════════════════════════════════

/**
 * Handles on() command from child light component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentLightOn(def childDevice) {
  logDebug("componentLightOn() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentLightOn: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /light/{id}?turn=on
    if (isGen1Device(childDevice)) {
      Integer lightId = extractComponentId(childDevice, 'lightId')
      logDebug("componentLightOn: Gen 1 light/${lightId}?turn=on")
      sendGen1Get(ipAddress, "light/${lightId}", [turn: 'on'])
      return
    }

    // Gen 2/3: JSON-RPC Light.Set
    String rpcUri = "http://${ipAddress}/rpc"
    Integer lightId = extractComponentId(childDevice, 'lightId')
    LinkedHashMap command = lightSetCommand(lightId, true)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentLightOn: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentLightOn exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles off() command from child light component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentLightOff(def childDevice) {
  logDebug("componentLightOff() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentLightOff: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /light/{id}?turn=off
    if (isGen1Device(childDevice)) {
      Integer lightId = extractComponentId(childDevice, 'lightId')
      logDebug("componentLightOff: Gen 1 light/${lightId}?turn=off")
      sendGen1Get(ipAddress, "light/${lightId}", [turn: 'off'])
      return
    }

    // Gen 2/3: JSON-RPC Light.Set
    String rpcUri = "http://${ipAddress}/rpc"
    Integer lightId = extractComponentId(childDevice, 'lightId')
    LinkedHashMap command = lightSetCommand(lightId, false)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentLightOff: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentLightOff exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles setLevel() command from child light component devices.
 *
 * @param childDevice The child device that sent the command
 * @param level Brightness level (0 to 100)
 * @param transitionMs Optional transition duration in milliseconds
 */
void componentSetLevel(def childDevice, Integer level, Integer transitionMs = null) {
  logDebug("componentSetLevel(${level}, ${transitionMs}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentSetLevel: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /light/{id}?turn=on&brightness={level}
    if (isGen1Device(childDevice)) {
      Integer lightId = extractComponentId(childDevice, 'lightId')
      String turnAction = level > 0 ? 'on' : 'off'
      Map params = [turn: turnAction, brightness: level.toString()]
      if (transitionMs != null) {
        params.transition = (transitionMs / 1000).intValue().toString()
      }
      logDebug("componentSetLevel: Gen 1 light/${lightId}?turn=${turnAction}&brightness=${level}")
      sendGen1Get(ipAddress, "light/${lightId}", params)
      return
    }

    // Gen 2/3: JSON-RPC Light.Set
    String rpcUri = "http://${ipAddress}/rpc"
    Integer lightId = extractComponentId(childDevice, 'lightId')
    Boolean turnOn = level > 0
    LinkedHashMap command = lightSetCommand(lightId, turnOn, level, transitionMs)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentSetLevel: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentSetLevel exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles setColor() command from child light component devices.
 * Converts Hubitat HSV color map to Shelly RGB values and switches to color mode.
 *
 * @param childDevice The child device that sent the command
 * @param colorMap Map with keys: hue (0-100), saturation (0-100), level (0-100)
 */
void componentSetColor(def childDevice, Map colorMap) {
  logDebug("componentSetColor(${colorMap}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentSetColor: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: Convert HSV to RGB and send to the appropriate endpoint
    if (isGen1Device(childDevice)) {
      Integer lightId = extractComponentId(childDevice, 'lightId')
      // Convert Hubitat HSV (hue 0-100, sat 0-100) to full-brightness RGB (0-255).
      // Pass 100 as value so RGB preserves pure hue/saturation; gain handles brightness separately.
      Integer gainLevel = colorMap.level != null ? (colorMap.level as Integer) : 100
      List rgb = hubitat.helper.ColorUtils.hsvToRGB([colorMap.hue as Float, colorMap.saturation as Float, 100.0f])
      Map params = [
          turn: 'on',
          red: rgb[0].toString(), green: rgb[1].toString(), blue: rgb[2].toString(),
          white: '0', gain: gainLevel.toString()
      ]

      // RGBW2 uses /color/{id} endpoint; bulbs use /light/{id} with mode=color
      String gen1Type = childDevice.getDataValue('gen1Type')
      if (gen1Type == 'SHRGBW2') {
        logDebug("componentSetColor: Gen 1 color/${lightId} params=${params}")
        sendGen1Get(ipAddress, "color/${lightId}", params)
      } else {
        params.mode = 'color'
        logDebug("componentSetColor: Gen 1 light/${lightId} params=${params}")
        sendGen1Get(ipAddress, "light/${lightId}", params)
      }
      return
    }

    // Gen 2/3 color support can be added here later
    logWarn("componentSetColor: Gen 2/3 color control not yet implemented")
  } catch (Exception e) {
    logError("componentSetColor exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles setColorTemperature() command from child light component devices.
 * Switches the device to white mode at the specified color temperature.
 *
 * @param childDevice The child device that sent the command
 * @param colorTemp Color temperature in Kelvin
 * @param level Optional brightness level (0-100)
 * @param transitionSecs Optional transition time in seconds
 */
void componentSetColorTemperature(def childDevice, BigDecimal colorTemp, BigDecimal level = null, BigDecimal transitionSecs = null) {
  logDebug("componentSetColorTemperature(${colorTemp}, ${level}, ${transitionSecs}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentSetColorTemperature: No IP address found for device ${childDevice.displayName}")
      return
    }

    // Gen 1: GET /light/{id}?turn=on&mode=white&temp=CT[&brightness=LEVEL][&transition=MS]
    if (isGen1Device(childDevice)) {
      Integer lightId = extractComponentId(childDevice, 'lightId')
      Map params = [turn: 'on', mode: 'white', temp: colorTemp.intValue().toString()]
      if (level != null) { params.brightness = level.intValue().toString() }
      if (transitionSecs != null) { params.transition = (transitionSecs * 1000).intValue().toString() }
      logDebug("componentSetColorTemperature: Gen 1 light/${lightId} params=${params}")
      sendGen1Get(ipAddress, "light/${lightId}", params)
      return
    }

    // Gen 2/3 CT support can be added here later
    logWarn("componentSetColorTemperature: Gen 2/3 color temperature control not yet implemented")
  } catch (Exception e) {
    logError("componentSetColorTemperature exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles on() command from RGBW2 color mode devices via /color/{id} endpoint.
 *
 * @param childDevice The child device that sent the command
 */
void componentColorOn(def childDevice) {
  logDebug("componentColorOn() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentColorOn: No IP address found for device ${childDevice.displayName}")
      return
    }
    Integer lightId = extractComponentId(childDevice, 'lightId')
    logDebug("componentColorOn: Gen 1 color/${lightId}?turn=on")
    sendGen1Get(ipAddress, "color/${lightId}", [turn: 'on'])
  } catch (Exception e) {
    logError("componentColorOn exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles off() command from RGBW2 color mode devices via /color/{id} endpoint.
 *
 * @param childDevice The child device that sent the command
 */
void componentColorOff(def childDevice) {
  logDebug("componentColorOff() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentColorOff: No IP address found for device ${childDevice.displayName}")
      return
    }
    Integer lightId = extractComponentId(childDevice, 'lightId')
    logDebug("componentColorOff: Gen 1 color/${lightId}?turn=off")
    sendGen1Get(ipAddress, "color/${lightId}", [turn: 'off'])
  } catch (Exception e) {
    logError("componentColorOff exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles setLevel() command from RGBW2 color mode devices.
 * Uses 'gain' parameter on the /color/{id} endpoint for brightness control.
 *
 * @param childDevice The child device that sent the command
 * @param gain Brightness gain level (0-100)
 * @param transitionMs Optional transition duration in milliseconds
 */
void componentSetColorGain(def childDevice, Integer gain, Integer transitionMs = null) {
  logDebug("componentSetColorGain(${gain}, ${transitionMs}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentSetColorGain: No IP address found for device ${childDevice.displayName}")
      return
    }
    Integer lightId = extractComponentId(childDevice, 'lightId')
    String turnAction = gain > 0 ? 'on' : 'off'
    Map params = [turn: turnAction, gain: gain.toString()]
    if (transitionMs != null && transitionMs > 0) {
      params.transition = transitionMs.toString()
    }
    logDebug("componentSetColorGain: Gen 1 color/${lightId} params=${params}")
    sendGen1Get(ipAddress, "color/${lightId}", params)
  } catch (Exception e) {
    logError("componentSetColorGain exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles startLevelChange() command from child light component devices.
 *
 * @param childDevice The child device that sent the command
 * @param direction Direction of level change ("up" or "down")
 */
void componentStartLevelChange(def childDevice, String direction) {
  logDebug("componentStartLevelChange(${direction}) called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentStartLevelChange: No IP address found for device ${childDevice.displayName}")
      return
    }
    String rpcUri = "http://${ipAddress}/rpc"
    Integer lightId = extractComponentId(childDevice, 'lightId')
    LinkedHashMap command = (direction == 'up') ? lightDimUpCommand(lightId) : lightDimDownCommand(lightId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentStartLevelChange: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentStartLevelChange exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

/**
 * Handles stopLevelChange() command from child light component devices.
 *
 * @param childDevice The child device that sent the command
 */
void componentStopLevelChange(def childDevice) {
  logDebug("componentStopLevelChange() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentStopLevelChange: No IP address found for device ${childDevice.displayName}")
      return
    }
    String rpcUri = "http://${ipAddress}/rpc"
    Integer lightId = extractComponentId(childDevice, 'lightId')
    LinkedHashMap command = lightDimStopCommand(lightId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentStopLevelChange: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentStopLevelChange exception for ${childDevice.displayName}: ${e.message}")
    if (settings?.enableWatchdog != false) { watchdogScan() }
  }
}

// ═══════════════════════════════════════════════════════════════
// Energy Monitor Reset Handler
// ═══════════════════════════════════════════════════════════════

/**
 * Handles resetEnergyMonitors() command from child devices with power monitoring.
 * Sends a Switch.ResetCounters RPC call to reset the energy counters.
 *
 * @param childDevice The child device that sent the command
 */
void componentResetEnergyMonitors(def childDevice) {
  logDebug("componentResetEnergyMonitors() called from device: ${childDevice.displayName}")
  try {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
      logError("componentResetEnergyMonitors: No IP address found for device ${childDevice.displayName}")
      return
    }
    String rpcUri = "http://${ipAddress}/rpc"
    Integer switchId = extractComponentId(childDevice, 'switchId')
    LinkedHashMap command = [
      "id"     : 0,
      "src"    : "resetCounters",
      "method" : "Switch.ResetCounters",
      "params" : ["id": switchId, "type": ["aenergy"]]
    ]
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logDebug("componentResetEnergyMonitors: response from ${ipAddress}: ${response}")
  } catch (Exception e) {
    logError("componentResetEnergyMonitors exception for ${childDevice.displayName}: ${e.message}")
  }
}

// ═══════════════════════════════════════════════════════════════
// Gas Sensor Command Handlers (SHGS-1)
// ═══════════════════════════════════════════════════════════════

/**
 * Opens the gas valve on a Gen 1 Shelly Gas sensor.
 * Sends GET /valve/0?go=open to the device.
 *
 * @param childDevice The child device requesting valve open
 */
void componentGasValveOpen(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentGasValveOpen: no IP for ${childDevice.displayName}"); return }
    logDebug("componentGasValveOpen: opening valve at ${ipAddress}")
    sendGen1Get(ipAddress, 'valve/0', [go: 'open'])
}

/**
 * Closes the gas valve on a Gen 1 Shelly Gas sensor.
 * Sends GET /valve/0?go=close to the device.
 *
 * @param childDevice The child device requesting valve close
 */
void componentGasValveClose(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentGasValveClose: no IP for ${childDevice.displayName}"); return }
    logDebug("componentGasValveClose: closing valve at ${ipAddress}")
    sendGen1Get(ipAddress, 'valve/0', [go: 'close'])
}

/**
 * Initiates a self-test on the Gen 1 Shelly Gas sensor.
 * Sends GET /self_test to the device.
 *
 * @param childDevice The child device requesting self-test
 */
void componentGasSelfTest(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentGasSelfTest: no IP for ${childDevice.displayName}"); return }
    logDebug("componentGasSelfTest: starting self-test at ${ipAddress}")
    sendGen1Get(ipAddress, 'self_test')
}

/**
 * Mutes the active gas alarm on the Gen 1 Shelly Gas sensor.
 * Sends GET /mute to the device.
 *
 * @param childDevice The child device requesting mute
 */
void componentGasMute(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentGasMute: no IP for ${childDevice.displayName}"); return }
    logDebug("componentGasMute: muting alarm at ${ipAddress}")
    sendGen1Get(ipAddress, 'mute')
}

/**
 * Unmutes the gas alarm on the Gen 1 Shelly Gas sensor.
 * Sends GET /unmute to the device.
 *
 * @param childDevice The child device requesting unmute
 */
void componentGasUnmute(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentGasUnmute: no IP for ${childDevice.displayName}"); return }
    logDebug("componentGasUnmute: unmuting alarm at ${ipAddress}")
    sendGen1Get(ipAddress, 'unmute')
}

// ═══════════════════════════════════════════════════════════════
// Shelly Sense IR Blaster Commands (SHSN-1)
// ═══════════════════════════════════════════════════════════════

/**
 * Emits an IR code in Pronto hex format via the Shelly Sense IR blaster.
 * Sends {@code GET /ir/emit?type=pronto_hex&code=<hex>} to the device.
 *
 * @param childDevice The Sense child device
 * @param prontoHex The Pronto hex string to transmit
 */
void componentSenseEmitIR(def childDevice, String prontoHex) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentSenseEmitIR: no IP for ${childDevice.displayName}"); return }
    logDebug("componentSenseEmitIR: emitting Pronto hex at ${ipAddress}")
    sendGen1Get(ipAddress, 'ir/emit', [type: 'pronto_hex', code: prontoHex])
}

/**
 * Emits a previously stored IR code by its ID.
 * Sends {@code GET /ir/emit?type=stored&id=<id>} to the device.
 *
 * @param childDevice The Sense child device
 * @param codeId The numeric ID of the stored code to emit
 */
void componentSenseEmitStoredIR(def childDevice, Integer codeId) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentSenseEmitStoredIR: no IP for ${childDevice.displayName}"); return }
    logDebug("componentSenseEmitStoredIR: emitting stored code ID=${codeId} at ${ipAddress}")
    sendGen1Get(ipAddress, 'ir/emit', [type: 'stored', id: codeId.toString()])
}

/**
 * Retrieves the list of stored IR codes from the Shelly Sense.
 * {@code /ir/list} returns a JSON array (not a Map), so we use {@code httpGetHelper}
 * directly instead of {@code sendGen1Get} which expects a Map response.
 * Results are published as the {@code irCodes} attribute on the child device.
 *
 * @param childDevice The Sense child device
 */
void componentSenseListIRCodes(def childDevice) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentSenseListIRCodes: no IP for ${childDevice.displayName}"); return }
    logDebug("componentSenseListIRCodes: listing codes at ${ipAddress}")
    try {
        String uri = "http://${ipAddress}/ir/list"
        Map params = [uri: uri, timeout: 10, contentType: 'application/json']
        if (authIsEnabledGen1()) {
            String credentials = "admin:${getAppSettings()?.devicePassword}".toString()
            String encoded = credentials.bytes.encodeBase64().toString()
            params.headers = ['Authorization': "Basic ${encoded}"]
        }
        httpGetHelper(params) { resp ->
            if (resp?.status == 200 && resp.data) {
                String codesJson = JsonOutput.toJson(resp.data)
                childSendEventHelper(childDevice, [name: 'irCodes', value: codesJson,
                    descriptionText: 'IR code list updated'])
                logInfo("IR codes list updated for ${childDevice.displayName}")
            }
        }
    } catch (Exception e) {
        logError("componentSenseListIRCodes: failed for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Stores a new IR code on the Shelly Sense in Pronto hex format.
 * Sends {@code GET /ir/add?type=pronto_hex&code=<hex>&name=<name>} to the device.
 * Automatically refreshes the IR code list after storing.
 *
 * @param childDevice The Sense child device
 * @param prontoHex The Pronto hex string to store
 * @param codeName A human-readable name for the stored code
 */
void componentSenseAddIRCode(def childDevice, String prontoHex, String codeName) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentSenseAddIRCode: no IP for ${childDevice.displayName}"); return }
    logDebug("componentSenseAddIRCode: adding '${codeName}' at ${ipAddress}")
    Map result = sendGen1Get(ipAddress, 'ir/add', [type: 'pronto_hex', code: prontoHex, name: codeName])
    if (result != null) {
        logInfo("IR code '${codeName}' added to ${childDevice.displayName}")
        componentSenseListIRCodes(childDevice)
    }
}

/**
 * Removes a stored IR code from the Shelly Sense by its ID.
 * Sends {@code GET /ir/remove?id=<id>} to the device.
 * Automatically refreshes the IR code list after removal.
 *
 * @param childDevice The Sense child device
 * @param codeId The numeric ID of the stored code to remove
 */
void componentSenseRemoveIRCode(def childDevice, Integer codeId) {
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) { logError("componentSenseRemoveIRCode: no IP for ${childDevice.displayName}"); return }
    logDebug("componentSenseRemoveIRCode: removing code ID=${codeId} at ${ipAddress}")
    Map result = sendGen1Get(ipAddress, 'ir/remove', [id: codeId.toString()])
    if (result != null) {
        logInfo("IR code ID=${codeId} removed from ${childDevice.displayName}")
        componentSenseListIRCodes(childDevice)
    }
}

/* #endregion Component Device Command Handlers */

// ═══════════════════════════════════════════════════════════════
// Parent-Child Parse Routing & Refresh
// ═══════════════════════════════════════════════════════════════

/**
 * Parses incoming LAN messages forwarded from parent devices.
 * POST requests (from Shelly scripts) carry data in the JSON body.
 * GET requests (from Shelly Action Webhooks) carry state in the URL path.
 *
 * @param parentDevice The parent device that received the LAN message
 * @param description Raw LAN message description string from Hubitat
 */
void componentParse(def parentDevice, String description) {
    logDebug("componentParse() called from parent: ${parentDevice.displayName}")

    try {
        Map msg = parseLanMessage(description)

        if (msg?.status != null) { return }

        String parentDni = parentDevice.deviceNetworkId

        if (msg?.body) {
            handlePostWebhook(parentDni, msg)
        } else {
            handleGetWebhook(parentDni, msg)
        }
    } catch (Exception e) {
        logError("componentParse exception: ${e.message}")
    }
}

/**
 * Handles POST webhook notifications from Shelly scripts.
 * Parses JSON body and routes to webhook params handler.
 *
 * @param parentDni The parent device network ID
 * @param msg The parsed LAN message map containing a JSON body
 */
private void handlePostWebhook(String parentDni, Map msg) {
    try {
        Map json = slurper.parseText(msg.body) as Map
        String dst = json?.dst?.toString()
        if (!dst) { logDebug('componentParse: POST webhook has no dst in body'); return }

        Map params = [:]
        json.each { k, v -> if (v != null) { params[k.toString()] = v.toString() } }

        logDebug("componentParse: POST webhook dst=${dst}, cid=${params.cid}")
        processWebhookParams(parentDni, params)
    } catch (Exception e) {
        logDebug("componentParse: POST webhook parse error: ${e.message}")
    }
}

/**
 * Handles GET webhook notifications from Shelly Action Webhooks.
 *
 * @param parentDni The parent device network ID
 * @param msg The parsed LAN message map (no body)
 */
private void handleGetWebhook(String parentDni, Map msg) {
    Map params = parseWebhookPath(msg)
    if (params?.dst) {
        logDebug("componentParse: GET webhook dst=${params.dst}, cid=${params.cid}")
        processWebhookParams(parentDni, params)
    } else {
        logDebug("componentParse: no actionable data in message — headers keys: ${msg?.headers?.keySet()}, raw header present: ${msg?.header != null}")
    }
}

/**
 * Logs detailed parsed LAN message information for webhook debugging from child devices.
 * Called by child drivers when trace logging is enabled in the driver.
 * Outputs structured trace logs showing HTTP request details, headers,
 * URL path components, parameters, and body content with clear visual separation.
 *
 * @param device The child device calling this method
 * @param msg The parsed LAN message map from parseLanMessage()
 */
void componentLogParsedMessage(DeviceWrapper device, Map msg) {
    // Early exit if app trace logging is disabled (driver already checked its own setting)
    if (!(settings.logEnable == true && settings.traceLogEnable == true)) { return }

    if (!msg) {
        logTrace("[${device.displayName}] componentLogParsedMessage: msg is null")
        return
    }

    String deviceLabel = device.displayName

    // Determine message type
    String messageType = "Unknown"
    if (msg?.status != null) {
        messageType = "HTTP Response (status=${msg.status})"
    } else if (msg?.body) {
        messageType = "POST with JSON Body"
    } else {
        messageType = "GET Webhook"
    }

    // Extract request line from headers
    String requestLine = null
    String method = "UNKNOWN"
    String path = "/"
    String version = "HTTP/1.1"

    if (msg?.headers) {
        // Find request line in headers map (first key that starts with HTTP method)
        requestLine = msg.headers.keySet()?.find { key ->
            key.toString().startsWith('GET ') ||
            key.toString().startsWith('POST ') ||
            key.toString().startsWith('PUT ') ||
            key.toString().startsWith('DELETE ')
        }?.toString()
    }

    // Fallback to raw header string if headers map didn't have it
    if (!requestLine && msg?.header) {
        String rawHeader = msg.header.toString()
        String[] lines = rawHeader.split('\n')
        for (String line : lines) {
            String trimmed = line.trim()
            if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
                requestLine = trimmed
                break
            }
        }
    }

    // Parse request line
    if (requestLine) {
        String[] parts = requestLine.split(' ')
        if (parts.length >= 1) method = parts[0]
        if (parts.length >= 2) path = parts[1]
        if (parts.length >= 3) version = parts[2]
    }

    // Parse path components
    List<String> pathComponents = path.tokenize('/')

    // Parse URL parameters from path (for /dst/cid pattern)
    Map params = [:]
    if (pathComponents.size() >= 1) {
        // pathComponents = ['dst', 'cid', ...]
        params.dst = pathComponents[0]
        if (pathComponents.size() >= 2) params.cid = pathComponents[1]
    }

    // Check for query string parameters
    Integer qIdx = path.indexOf('?')
    if (qIdx >= 0) {
        path.substring(qIdx + 1).split('&').each { String pair ->
            String[] kv = pair.split('=', 2)
            if (kv.length == 2) {
                params[URLDecoder.decode(kv[0], 'UTF-8')] = URLDecoder.decode(kv[1], 'UTF-8')
            }
        }
    }

    // Analyze body
    String body = msg?.body as String
    Boolean hasBody = (body != null && body != '')
    Boolean isJson = false
    if (hasBody) {
        String trimmedBody = body.trim()
        isJson = (trimmedBody.startsWith('{') || trimmedBody.startsWith('['))
    }

    // Output structured logging with device label prefix
    logTrace("[${deviceLabel}] " + "=" * 79)
    logTrace("[${deviceLabel}] PARSED LAN MESSAGE [${messageType}]")
    logTrace("[${deviceLabel}] " + "=" * 79)

    logTrace("[${deviceLabel}] --- HTTP Request Line ---")
    logTrace("[${deviceLabel}] Method:  ${method}")
    logTrace("[${deviceLabel}] Path:    ${path}")
    logTrace("[${deviceLabel}] Version: ${version}")

    logTrace("[${deviceLabel}] --- HTTP Headers ---")
    if (msg?.headers) {
        msg.headers.each { Object key, Object value ->
            String keyStr = key.toString()
            if (keyStr != requestLine) {  // Skip the request line itself
                String headerValue = value != null ? value.toString() : "[null]"
                logTrace("[${deviceLabel}] ${keyStr.padRight(20)}: ${headerValue}")
            }
        }
    } else {
        logTrace("[${deviceLabel}] [no headers map]")
    }

    logTrace("[${deviceLabel}] --- URL Path Components ---")
    pathComponents.eachWithIndex { String component, Integer index ->
        logTrace("[${deviceLabel}] [${index}] ${component}")
    }

    logTrace("[${deviceLabel}] --- URL Parameters ---")
    if (params.size() > 0) {
        params.each { key, value ->
            logTrace("[${deviceLabel}] ${key}: ${value}")
        }
    } else if (hasBody) {
        logTrace("[${deviceLabel}] [none - JSON body present]")
    } else {
        logTrace("[${deviceLabel}] [none]")
    }

    logTrace("[${deviceLabel}] --- HTTP Body ---")
    logTrace("[${deviceLabel}] Present: ${hasBody ? 'Yes' : 'No'}")
    logTrace("[${deviceLabel}] JSON:    ${hasBody ? (isJson ? 'Yes' : 'No') : 'N/A'}")
    if (hasBody) {
        logTrace("[${deviceLabel}] Content:")
        logTrace("[${deviceLabel}] ${body}")
    } else {
        logTrace("[${deviceLabel}] Content: [empty]")
    }

    logTrace("[${deviceLabel}] " + "=" * 79)
}

/**
 * Called by device drivers when they detect the source IP of an incoming
 * LAN message differs from the stored device IP. Updates the discoveredShellys
 * cache to reflect the new IP address.
 *
 * @param childDevice The device that detected the IP change
 * @param oldIp The previous IP address (dotted-decimal)
 * @param newIp The new IP address (dotted-decimal)
 */
void componentNotifyIpChanged(DeviceWrapper childDevice, String oldIp, String newIp) {
    if (!oldIp || !newIp || oldIp == newIp) { return }
    String deviceName = childDevice?.displayName ?: 'Unknown'
    logInfo("componentNotifyIpChanged: ${deviceName} IP changed: ${oldIp} -> ${newIp}")
    childDevice.updateSetting('ipAddress', newIp)
    if (state.discoveredShellys?.containsKey(oldIp)) {
        Map deviceEntry = state.discoveredShellys.remove(oldIp) as Map
        deviceEntry.ipAddress = newIp
        deviceEntry.ts = now()
        if (state.discoveredShellys?.containsKey(newIp)) {
            logWarn("componentNotifyIpChanged: new IP ${newIp} already exists in discoveredShellys — overwriting")
        }
        state.discoveredShellys[newIp] = deviceEntry
    }
}

/**
 * Routes a script notification to child devices.
 * Iterates result entries and sends events to the matching child device.
 *
 * @param parentDni The parent device network ID
 * @param dst The notification destination type
 * @param result The result map from the script notification
 */
private void routeScriptNotification(String parentDni, String dst, Map result) {
    result.each { String key, Object value ->
        if (!(value instanceof Map)) { return }

        String baseType = key.contains(':') ? key.split(':')[0] : key
        Integer componentId = key.contains(':') ? (key.split(':')[1] as Integer) : 0

        String childType = mapDstToComponentType(dst, baseType)
        if (!childType) { return }

        String childDni = "${parentDni}-${childType}-${componentId}"
        def child = getChildDevice(childDni)

        if (child) {
            List<Map> events = buildComponentEvents(dst, baseType, value as Map)
            events.each { Map evt ->
                childSendEventHelper(child, evt)
            }
            childSendEventHelper(child, [name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss')])
            logDebug("routeScriptNotification: sent ${events.size()} events to ${child.displayName}")
        } else {
            logDebug("routeScriptNotification: no child device found for DNI ${childDni}")
        }
    }
}

/**
 * Parses webhook GET request path to extract routing and data from URL path segments.
 * All data is encoded as path segments: /dst/cid[/key1/val1/key2/val2...].
 *
 * @param msg The parsed LAN message map from parseLanMessage()
 * @return Map with dst, cid, and any parsed data keys, or null if not parseable
 */
@CompileStatic
private Map parseWebhookPath(Map msg) {
    String requestLine = null

    // Primary: search parsed headers Map for request line
    if (msg?.headers) {
        requestLine = ((Map)msg.headers).keySet()?.find { Object key ->
            key.toString().startsWith('GET ') || key.toString().startsWith('POST ')
        }?.toString()
    }

    // Fallback: parse raw header string (singular msg.header)
    if (!requestLine && msg?.header) {
        String rawHeader = msg.header.toString()
        String[] lines = rawHeader.split('\n')
        for (String line : lines) {
            String trimmed = line.trim()
            if (trimmed.startsWith('GET ') || trimmed.startsWith('POST ')) {
                requestLine = trimmed
                break
            }
        }
    }

    if (!requestLine) { return null }

    String[] requestParts = requestLine.split(' ')
    if (requestParts.length < 2) { return null }
    String pathAndQuery = requestParts[1]

    // Strip leading slash
    String webhookPath = pathAndQuery.startsWith('/') ? pathAndQuery.substring(1) : pathAndQuery
    if (!webhookPath) { return null }

    // Defensive: strip query string if somehow present
    int qMarkIdx = webhookPath.indexOf('?')
    if (qMarkIdx >= 0) { webhookPath = webhookPath.substring(0, qMarkIdx) }

    String[] segments = webhookPath.split('/')
    if (segments.length < 2) { return null }

    Map result = [dst: segments[0], cid: segments[1]]

    // Parse key/value pairs from remaining path segments
    for (int i = 2; i + 1 < segments.length; i += 2) {
        result[segments[i]] = segments[i + 1]
    }

    return result
}

/**
 * Processes parsed webhook GET query parameters and routes events to the
 * appropriate child device. Handles the comp parameter (e.g., "switch:0")
 * to determine child device DNI.
 *
 * @param parentDni The parent device network ID
 * @param params The parsed query parameters map
 */
private void processWebhookParams(String parentDni, Map params) {
    String dst = params.dst
    if (!dst || params.cid == null) {
        logDebug("processWebhookParams: missing dst or cid parameter")
        return
    }

    Integer componentId = params.cid as Integer
    String baseType = dstToComponentType(dst)
    String childType = mapDstToComponentType(dst, baseType)
    if (!childType) { return }

    String childDni = "${parentDni}-${childType}-${componentId}"
    def child = getChildDevice(childDni)
    if (!child) {
        logDebug("processWebhookParams: no child device for DNI ${childDni}")
        return
    }

    List<Map> events = buildWebhookEvents(dst, params)
    events.each { Map evt ->
        childSendEventHelper(child, evt)
    }
    childSendEventHelper(child, [name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss')])
    logDebug("processWebhookParams: sent ${events.size()} events to ${child.displayName}")
}

/**
 * Builds Hubitat events from webhook GET query parameters.
 * Translates flat query params into event Maps suitable for sendEvent().
 * Also handles supplemental data like battery level piggybacked on sensor webhooks.
 *
 * @param dst The webhook destination type (switchmon, covermon, temperature, etc.)
 * @param params The parsed query parameters
 * @return List of event maps to send to the child device
 */
private List<Map> buildWebhookEvents(String dst, Map params) {
    List<Map> events = []

    switch (dst) {
        // New discrete switch webhooks
        case 'switch_on':
            events.add([name: 'switch', value: 'on', descriptionText: 'Switch turned on'])
            break
        case 'switch_off':
            events.add([name: 'switch', value: 'off', descriptionText: 'Switch turned off'])
            break
        case 'switchmon':  // legacy
            if (params.output != null) {
                String switchState = params.output == 'true' ? 'on' : 'off'
                events.add([name: 'switch', value: switchState,
                    descriptionText: "Switch turned ${switchState}"])
            }
            break

        // New discrete cover webhooks (pos still comes via template query param)
        case 'cover_open':
            events.add([name: 'windowShade', value: 'open', descriptionText: 'Window shade is open'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'cover_closed':
            events.add([name: 'windowShade', value: 'closed', descriptionText: 'Window shade is closed'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'cover_stopped':
            events.add([name: 'windowShade', value: 'partially open', descriptionText: 'Window shade is partially open'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'cover_opening':
            events.add([name: 'windowShade', value: 'opening', descriptionText: 'Window shade is opening'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'cover_closing':
            events.add([name: 'windowShade', value: 'closing', descriptionText: 'Window shade is closing'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'cover_calibrating':
            events.add([name: 'windowShade', value: 'unknown', descriptionText: 'Window shade is calibrating'])
            if (params.pos != null) { events.add([name: 'position', value: params.pos as Integer, unit: '%']) }
            break
        case 'covermon':  // legacy
            if (params.state != null) {
                String shadeState
                switch (params.state) {
                    case 'open': shadeState = 'open'; break
                    case 'closed': shadeState = 'closed'; break
                    case 'opening': shadeState = 'opening'; break
                    case 'closing': shadeState = 'closing'; break
                    case 'stopped': shadeState = 'partially open'; break
                    case 'calibrating': shadeState = 'unknown'; break
                    default: shadeState = 'unknown'
                }
                events.add([name: 'windowShade', value: shadeState,
                    descriptionText: "Window shade is ${shadeState}"])
            }
            if (params.pos != null) {
                events.add([name: 'position', value: params.pos as Integer,
                    unit: '%', descriptionText: "Position is ${params.pos}%"])
            }
            break

        case 'temperature':
            String scale = getLocationHelper()?.temperatureScale ?: 'F'
            BigDecimal temp = null
            if (scale == 'C' && params.tC) {
                temp = params.tC as BigDecimal
            } else if (params.tF) {
                temp = params.tF as BigDecimal
            }
            if (temp != null) {
                events.add([name: 'temperature', value: temp,
                    unit: "°${scale}", descriptionText: "Temperature is ${temp}°${scale}"])
            }
            break

        case 'humidity':
            if (params.rh != null) {
                events.add([name: 'humidity', value: params.rh as BigDecimal,
                    unit: '%', descriptionText: "Humidity is ${params.rh}%"])
            }
            break

        case 'input_push':
            events.add([name: 'pushed', value: 1, isStateChange: true,
                descriptionText: 'Button 1 was pushed'])
            break
        case 'input_double':
            events.add([name: 'doubleTapped', value: 1, isStateChange: true,
                descriptionText: 'Button 1 was double-tapped'])
            break
        case 'input_long':
            events.add([name: 'held', value: 1, isStateChange: true,
                descriptionText: 'Button 1 was held'])
            break
        case 'input_triple':
            events.add([name: 'pushed', value: 3, isStateChange: true,
                descriptionText: 'Button 1 was triple-pushed'])
            break

        // New discrete input toggle webhooks
        case 'input_toggle_on':
            events.add([name: 'switch', value: 'on', isStateChange: true,
                descriptionText: 'Input toggled on'])
            break
        case 'input_toggle_off':
            events.add([name: 'switch', value: 'off', isStateChange: true,
                descriptionText: 'Input toggled off'])
            break
        case 'input_toggle':  // legacy
            if (params.state != null) {
                String toggleState = params.state as String
                events.add([name: 'switch', value: toggleState, isStateChange: true,
                    descriptionText: "Input toggled ${toggleState}"])
            }
            break

        // New discrete smoke webhook
        case 'smoke_alarm':
            events.add([name: 'smoke', value: 'detected', descriptionText: 'Smoke detected'])
            break
        case 'smoke':  // legacy
            if (params.alarm != null) {
                String smokeState = params.alarm == 'true' ? 'detected' : 'clear'
                events.add([name: 'smoke', value: smokeState,
                    descriptionText: "Smoke ${smokeState}"])
            }
            break

        case 'illuminance':
            if (params.lux != null) {
                events.add([name: 'illuminance', value: params.lux as Integer,
                    unit: 'lux', descriptionText: "Illuminance is ${params.lux} lux"])
            }
            break

        // Gas alarm webhooks (Gen 1 Shelly Gas SHGS-1)
        case 'gas_alarm_mild':
            events.add([name: 'naturalGas', value: 'detected', descriptionText: 'Gas alarm: mild level detected'])
            break
        case 'gas_alarm_heavy':
            events.add([name: 'naturalGas', value: 'detected', descriptionText: 'Gas alarm: heavy level detected'])
            break
        case 'gas_alarm_off':
            events.add([name: 'naturalGas', value: 'clear', descriptionText: 'Gas alarm cleared'])
            break
    }

    // Battery data piggybacked on any webhook (from supplemental token groups)
    if (params.battPct != null) {
        events.add([name: 'battery', value: params.battPct as Integer,
            unit: '%', descriptionText: "Battery is ${params.battPct}%"])
    }

    return events
}

/**
 * Maps a Shelly notification dst type to the component type used in child DNIs.
 *
 * @param dst The notification destination type (switchmon, powermon, covermon, etc.)
 * @param baseType The base component type from the result key (switch, cover, light, input)
 * @return The component type string for child DNI construction, or null if not mappable
 */
/**
 * Maps a webhook dst parameter to its Shelly component type.
 */
private String dstToComponentType(String dst) {
    if (dst.startsWith('input_')) { return 'input' }
    if (dst.startsWith('switch_')) { return 'switch' }
    if (dst.startsWith('cover_')) { return 'cover' }
    if (dst.startsWith('smoke_')) { return 'smoke' }
    if (dst.startsWith('gas_')) { return 'gas' }
    switch (dst) {
        case 'switchmon': return 'switch'  // legacy
        case 'covermon': return 'cover'    // legacy
        default: return dst
    }
}

private String mapDstToComponentType(String dst, String baseType) {
    // Prefix-based matching for new discrete dst values
    if (dst.startsWith('switch_')) { return 'switch' }
    if (dst.startsWith('cover_')) { return 'cover' }
    if (dst.startsWith('input_')) { return 'input' }
    if (dst.startsWith('smoke_')) { return 'smoke' }
    if (dst.startsWith('gas_')) { return 'gas' }

    // Legacy and passthrough dst types
    switch (dst) {
        case 'switchmon':
            return 'switch'
        case 'powermon':
            return baseType
        case 'covermon':
            return 'cover'
        case 'lightmon':
            return 'light'
        case 'temperature':
        case 'humidity':
        case 'battery':
        case 'smoke':
        case 'illuminance':
            return baseType
        default:
            logDebug("mapDstToComponentType: unknown dst type '${dst}'")
            return null
    }
}

/**
 * Builds a list of Hubitat events from a Shelly notification component entry.
 * Translates Shelly JSON data into event Maps suitable for sendEvent().
 *
 * @param dst The notification destination type
 * @param baseType The base component type
 * @param data The component data map from the notification
 * @return List of event maps to send to the child device
 */
private List<Map> buildComponentEvents(String dst, String baseType, Map data) {
    List<Map> events = []

    switch (dst) {
        case 'switchmon':
            if (data.output != null) {
                String switchState = data.output ? 'on' : 'off'
                events.add([name: 'switch', value: switchState,
                    descriptionText: "Switch turned ${switchState}"])
            }
            break

        case 'powermon':
            if (data.voltage != null) {
                events.add([name: 'voltage', value: data.voltage as BigDecimal,
                    unit: 'V', descriptionText: "Voltage is ${data.voltage}V"])
            }
            if (data.current != null) {
                events.add([name: 'amperage', value: data.current as BigDecimal,
                    unit: 'A', descriptionText: "Current is ${data.current}A"])
            }
            if (data.apower != null) {
                events.add([name: 'power', value: data.apower as BigDecimal,
                    unit: 'W', descriptionText: "Power is ${data.apower}W"])
            }
            if (data.aenergy?.total != null) {
                BigDecimal energyWh = data.aenergy.total as BigDecimal
                BigDecimal energyKwh = energyWh / 1000
                events.add([name: 'energy', value: energyKwh,
                    unit: 'kWh', descriptionText: "Energy is ${energyKwh}kWh"])
            }
            break

        case 'covermon':
            if (data.state != null) {
                String shadeState
                switch (data.state) {
                    case 'open': shadeState = 'open'; break
                    case 'closed': shadeState = 'closed'; break
                    case 'opening': shadeState = 'opening'; break
                    case 'closing': shadeState = 'closing'; break
                    case 'stopped': shadeState = 'partially open'; break
                    default: shadeState = 'unknown'
                }
                events.add([name: 'windowShade', value: shadeState,
                    descriptionText: "Window shade is ${shadeState}"])
            }
            if (data.current_pos != null) {
                events.add([name: 'position', value: data.current_pos as Integer,
                    unit: '%', descriptionText: "Position is ${data.current_pos}%"])
            }
            // Cover power monitoring
            if (data.voltage != null) {
                events.add([name: 'voltage', value: data.voltage as BigDecimal,
                    unit: 'V', descriptionText: "Voltage is ${data.voltage}V"])
            }
            if (data.current != null) {
                events.add([name: 'amperage', value: data.current as BigDecimal,
                    unit: 'A', descriptionText: "Current is ${data.current}A"])
            }
            if (data.apower != null) {
                events.add([name: 'power', value: data.apower as BigDecimal,
                    unit: 'W', descriptionText: "Power is ${data.apower}W"])
            }
            break

        case 'lightmon':
            if (data.output != null) {
                String switchState = data.output ? 'on' : 'off'
                events.add([name: 'switch', value: switchState,
                    descriptionText: "Switch turned ${switchState}"])
            }
            if (data.brightness != null) {
                events.add([name: 'level', value: data.brightness as Integer,
                    unit: '%', descriptionText: "Level is ${data.brightness}%"])
            }
            break

        case 'input_push':
            if (data.id != null) {
                Integer buttonNumber = 1 // Each child input has one button
                events.add([name: 'pushed', value: buttonNumber, isStateChange: true,
                    descriptionText: "Button ${buttonNumber} was pushed"])
            }
            break

        case 'input_double':
            if (data.id != null) {
                Integer buttonNumber = 1
                events.add([name: 'doubleTapped', value: buttonNumber, isStateChange: true,
                    descriptionText: "Button ${buttonNumber} was double-tapped"])
            }
            break

        case 'input_long':
            if (data.id != null) {
                Integer buttonNumber = 1
                events.add([name: 'held', value: buttonNumber, isStateChange: true,
                    descriptionText: "Button ${buttonNumber} was held"])
            }
            break
    }

    return events
}

/**
 * Handles refresh() command from parent or child devices.
 * Queries Shelly.GetStatus and distributes current state to all child devices.
 *
 * @param childDevice The device that requested refresh (parent or child)
 */
void componentRefresh(def childDevice) {
    logDebug("componentRefresh() called from device: ${childDevice.displayName}")

    try {
        String ipAddress = childDevice.getDataValue('ipAddress')
        if (!ipAddress) {
            logError("componentRefresh: No IP address found for device ${childDevice.displayName}")
            return
        }

        // Gen 1 devices use REST polling instead of RPC status query
        if (isGen1Device(childDevice)) {
            // Backfill gen1Type data value for devices created before this field was stored
            String resolvedGen1Type = childDevice.getDataValue('gen1Type') ?: ''
            if (!resolvedGen1Type) {
                Map deviceInfo = state.discoveredShellys?.get(ipAddress)
                if (deviceInfo?.gen1Type) {
                    resolvedGen1Type = deviceInfo.gen1Type.toString()
                    childDevice.updateDataValue('gen1Type', resolvedGen1Type)
                }
            }
            // For battery devices: attempt pending action URL installation and settings on wake-up
            if (isSleepyBatteryDevice(childDevice)) {
                attemptGen1ActionUrlInstallOnWake(ipAddress)
                applyPendingGen1Settings(childDevice, ipAddress)
            }
            pollGen1DeviceStatus(ipAddress)
            if (childDevice.getDataValue('isParentDevice') == 'true') {
                String typeName = childDevice.typeName ?: ''
                if (typeName.contains('EM Parent')) {
                    // EM parent: relay is on the parent device itself, not children
                    syncSwitchConfigToDriver(childDevice, ipAddress)
                } else {
                    syncSwitchConfigForParentChildren(childDevice.deviceNetworkId, ipAddress)
                }
            } else {
                syncSwitchConfigToDriver(childDevice, ipAddress)
            }
            // Sync device-side settings to driver preferences (once after creation)
            if (!childDevice.getDataValue('gen1SettingsSynced')) {
                syncGen1MotionSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1TrvSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1ButtonSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1FloodSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1SmokeSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1GasSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1HTSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1SenseSettings(ipAddress, childDevice, resolvedGen1Type)
                syncGen1DWSettings(ipAddress, childDevice, resolvedGen1Type)
            }
            return
        }

        // Gen 2/3: Determine if this is a parent device or a child device
        String parentDni = childDevice.getDataValue('parentDni') ?: childDevice.deviceNetworkId
        Boolean isParent = childDevice.getDataValue('isParentDevice') == 'true'

        // Query full device status via RPC
        Map deviceStatus = queryDeviceStatus(ipAddress)
        if (!deviceStatus) {
            logWarn("componentRefresh: Could not query device status for ${ipAddress}")
            return
        }

        if (isParent) {
            // Parent refresh: distribute status to all children
            distributeStatusToChildren(parentDni, deviceStatus)
            syncSwitchConfigForParentChildren(parentDni, ipAddress)
        } else {
            // Single child refresh: only update this child
            String componentType = childDevice.getDataValue('componentType')
            String idDataKey = "${componentType}Id"
            Integer componentId = extractComponentId(childDevice, idDataKey)
            String componentKey = "${componentType}:${componentId}"

            if (deviceStatus[componentKey] instanceof Map) {
                Map componentData = deviceStatus[componentKey] as Map
                updateChildFromStatus(childDevice, componentType, componentData)
            }
            syncSwitchConfigToDriver(childDevice, ipAddress)
        }
    } catch (Exception e) {
        logError("componentRefresh exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Handles initialize() command from parent devices.
 * Sets up scripts and webhooks on the Shelly device.
 *
 * @param parentDevice The parent device that is initializing
 */
void componentInitialize(def parentDevice) {
    logDebug("componentInitialize() called from parent: ${parentDevice.displayName}")
    // Script and webhook installation is handled by the existing
    // installRequiredScripts and installRequiredActions functions
    // which are triggered during device discovery/creation
}

/**
 * Handles configure() command from parent devices.
 *
 * @param parentDevice The parent device to configure
 */
void componentConfigure(def parentDevice) {
    logDebug("componentConfigure() called from parent: ${parentDevice.displayName}")
    // Configuration is handled during device creation.
    // Gen 1 Motion settings sync is triggered via componentRefresh
    // when the gen1SettingsSynced flag is absent (cleared by driver configure()).
}

/**
 * Receives device configuration settings from a Gen 1 driver and sends
 * them to the physical device via GET /settings?key=value.
 *
 * @param childDevice The child device relaying its settings
 * @param settingsMap Map of Gen 1 /settings query parameters
 */
void componentUpdateGen1Settings(def childDevice, Map settingsMap) {
    if (!childDevice || !settingsMap) { return }
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("componentUpdateGen1Settings: no IP for ${childDevice.displayName}")
        return
    }

    if (isSleepyBatteryDevice(childDevice)) {
        // Sleepy devices: try immediately, queue on failure for next wake-up
        Map result = sendGen1Setting(ipAddress, 'settings', settingsMap)
        if (result != null) {
            logInfo("Gen 1 settings applied to ${childDevice.displayName}")
            clearPendingGen1Settings(childDevice.deviceNetworkId)
        } else {
            logInfo("Device ${childDevice.displayName} is asleep — queuing settings for next wake-up")
            queueGen1Settings(childDevice.deviceNetworkId, settingsMap)
        }
        return
    }

    logInfo("Sending Gen 1 settings to ${childDevice.displayName} at ${ipAddress}: ${settingsMap}")
    Map result = sendGen1Setting(ipAddress, 'settings', settingsMap)
    if (result != null) {
        logInfo("Gen 1 settings applied to ${childDevice.displayName}")
    } else {
        logWarn("Failed to apply Gen 1 settings to ${childDevice.displayName} — device may be unreachable")
    }
}

/**
 * Queues Gen 1 device settings for delivery on next wake-up.
 * Merges new settings with any already-queued settings for the same device.
 *
 * @param dni The device network ID
 * @param settingsMap Map of Gen 1 /settings query parameters to queue
 */
private void queueGen1Settings(String dni, Map settingsMap) {
    if (!state.pendingGen1Settings) { state.pendingGen1Settings = [:] }
    Map existing = state.pendingGen1Settings[dni] as Map ?: [:]
    existing.putAll(settingsMap)
    state.pendingGen1Settings[dni] = existing
    logDebug("Queued Gen 1 settings for ${dni}: ${existing}")
}

/**
 * Clears any pending Gen 1 settings for a device after successful delivery.
 *
 * @param dni The device network ID
 */
private void clearPendingGen1Settings(String dni) {
    if (state.pendingGen1Settings?.containsKey(dni)) {
        state.pendingGen1Settings.remove(dni)
    }
}

/**
 * Applies any queued Gen 1 settings to a device that has just woken up.
 * Called during {@link #componentRefresh(def)} for sleepy battery devices.
 * On success, clears the queue; on failure, retains for the next wake-up.
 *
 * @param childDevice The child device that just woke up
 * @param ipAddress The device's IP address
 */
private void applyPendingGen1Settings(def childDevice, String ipAddress) {
    if (!childDevice || !ipAddress) { return }
    String dni = childDevice.deviceNetworkId
    Map pending = state.pendingGen1Settings?.get(dni) as Map
    if (!pending) { return }

    logInfo("Applying queued settings to ${childDevice.displayName}: ${pending}")
    Map result = sendGen1Setting(ipAddress, 'settings', pending)
    if (result != null) {
        logInfo("Queued Gen 1 settings applied to ${childDevice.displayName}")
        clearPendingGen1Settings(dni)
    } else {
        logWarn("Failed to apply queued settings to ${childDevice.displayName} — will retry next wake-up")
    }
}

// ═══════════════════════════════════════════════════════════════
// TRV Component Handlers (Shelly Gen 1 TRV)
// ═══════════════════════════════════════════════════════════════

/**
 * Sets TRV heating setpoint via GET /thermostats/0.
 * Always sends target_t_enabled=1 to ensure thermostat control mode is active.
 *
 * @param childDevice The TRV child device
 * @param tempC Target temperature in Celsius
 */
void componentSetTrvHeatingSetpoint(def childDevice, BigDecimal tempC) {
    try {
        String ip = childDevice.getDataValue('ipAddress')
        if (!ip) { logError("componentSetTrvHeatingSetpoint: no IP for ${childDevice.displayName}"); return }
        logDebug("componentSetTrvHeatingSetpoint: setting ${tempC}°C on ${childDevice.displayName}")
        sendGen1Get(ip, 'thermostats/0', [target_t_enabled: '1', target_t: tempC.toString()])
    } catch (Exception e) {
        logError("componentSetTrvHeatingSetpoint exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Sets TRV valve position (0-100) via GET /thermostats/0.
 *
 * @param childDevice The TRV child device
 * @param position Valve position percentage (0 = closed, 100 = fully open)
 */
void componentSetTrvValvePosition(def childDevice, Integer position) {
    try {
        String ip = childDevice.getDataValue('ipAddress')
        if (!ip) { logError("componentSetTrvValvePosition: no IP for ${childDevice.displayName}"); return }
        logDebug("componentSetTrvValvePosition: setting pos=${position} on ${childDevice.displayName}")
        sendGen1Get(ip, 'thermostats/0', [pos: position.toString()])
    } catch (Exception e) {
        logError("componentSetTrvValvePosition exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Sends an external temperature reading to the TRV via GET /ext_t.
 * Helps the TRV make more accurate heating decisions using a remote sensor.
 *
 * @param childDevice The TRV child device
 * @param tempC External temperature in Celsius
 */
void componentSetTrvExternalTemp(def childDevice, BigDecimal tempC) {
    try {
        String ip = childDevice.getDataValue('ipAddress')
        if (!ip) { logError("componentSetTrvExternalTemp: no IP for ${childDevice.displayName}"); return }
        logDebug("componentSetTrvExternalTemp: sending ${tempC}°C to ${childDevice.displayName}")
        sendGen1Get(ip, 'ext_t', [temp: tempC.toString()])
    } catch (Exception e) {
        logError("componentSetTrvExternalTemp exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Sets TRV boost mode duration via GET /thermostats/0.
 * Boost heats at maximum output for the specified duration.
 *
 * @param childDevice The TRV child device
 * @param minutes Boost duration in minutes (0 to cancel)
 */
void componentSetTrvBoostMinutes(def childDevice, Integer minutes) {
    try {
        String ip = childDevice.getDataValue('ipAddress')
        if (!ip) { logError("componentSetTrvBoostMinutes: no IP for ${childDevice.displayName}"); return }
        logDebug("componentSetTrvBoostMinutes: setting ${minutes} minutes on ${childDevice.displayName}")
        sendGen1Get(ip, 'thermostats/0', [boost_minutes: minutes.toString()])
    } catch (Exception e) {
        logError("componentSetTrvBoostMinutes exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Enables or disables the TRV's internal schedule via GET /settings/thermostats/0.
 *
 * @param childDevice The TRV child device
 * @param enabled true to enable, false to disable
 */
void componentSetTrvScheduleEnabled(def childDevice, Boolean enabled) {
    try {
        String ip = childDevice.getDataValue('ipAddress')
        if (!ip) { logError("componentSetTrvScheduleEnabled: no IP for ${childDevice.displayName}"); return }
        logDebug("componentSetTrvScheduleEnabled: setting schedule=${enabled} on ${childDevice.displayName}")
        sendGen1Setting(ip, 'settings/thermostats/0', [schedule: enabled ? '1' : '0'])
    } catch (Exception e) {
        logError("componentSetTrvScheduleEnabled exception for ${childDevice.displayName}: ${e.message}")
    }
}

/**
 * Receives TRV-specific settings from the driver and sends them to the
 * physical device via GET /settings/thermostats/0.
 *
 * @param childDevice The TRV child device relaying its settings
 * @param settingsMap Map of thermostat settings parameters
 */
void componentUpdateGen1ThermostatSettings(def childDevice, Map settingsMap) {
    if (!childDevice || !settingsMap) { return }
    String ip = childDevice.getDataValue('ipAddress')
    if (!ip) {
        logError("componentUpdateGen1ThermostatSettings: no IP for ${childDevice.displayName}")
        return
    }
    logInfo("Sending Gen 1 TRV settings to ${childDevice.displayName} at ${ip}: ${settingsMap}")
    Map result = sendGen1Setting(ip, 'settings/thermostats/0', settingsMap)
    if (result != null) {
        logInfo("Gen 1 TRV settings applied to ${childDevice.displayName}")
    } else {
        logWarn("Failed to apply Gen 1 TRV settings to ${childDevice.displayName} — device may be unreachable")
    }
}

// ═══════════════════════════════════════════════════════════════
// Light Settings (default state, auto-on, auto-off for bulbs/dimmers)
// ═══════════════════════════════════════════════════════════════

/**
 * Receives light settings from a standalone light/bulb driver.
 * Applies settings to Gen 1 devices via GET /settings/light/{id}.
 *
 * @param childDevice The standalone light/bulb device
 * @param lightSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateLightSettings(def childDevice, Map lightSettings) {
    if (!childDevice || !lightSettings) { return }
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("componentUpdateLightSettings: no IP for ${childDevice.displayName}")
        return
    }

    if (isGen1Device(childDevice)) {
        Integer lightId = extractComponentId(childDevice, 'lightId')
        Map params = [:]
        if (lightSettings.defaultState != null) {
            params.default_state = lightSettings.defaultState as String
        }
        if (lightSettings.autoOffTime != null) {
            params.auto_off = (lightSettings.autoOffTime as BigDecimal).toString()
        }
        if (lightSettings.autoOnTime != null) {
            params.auto_on = (lightSettings.autoOnTime as BigDecimal).toString()
        }
        if (!params) { return }

        Map result = sendGen1Setting(ipAddress, "settings/light/${lightId}", params)
        if (result != null) {
            logInfo("Applied Gen1 light settings to ${childDevice.displayName}: ${params}")
        }
    }
    // Gen 2/3 light settings can be added here later
}

// ═══════════════════════════════════════════════════════════════
// Color Settings (default state, auto-on, auto-off for RGBW2 color mode)
// ═══════════════════════════════════════════════════════════════

/**
 * Receives color settings from an RGBW2 color mode driver.
 * Applies settings to Gen 1 devices via GET /settings/color/{id}.
 *
 * @param childDevice The standalone RGBW2 color device
 * @param colorSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateColorSettings(def childDevice, Map colorSettings) {
    if (!childDevice || !colorSettings) { return }
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("componentUpdateColorSettings: no IP for ${childDevice.displayName}")
        return
    }

    if (isGen1Device(childDevice)) {
        Integer lightId = extractComponentId(childDevice, 'lightId')
        Map params = [:]
        if (colorSettings.defaultState != null) {
            params.default_state = colorSettings.defaultState as String
        }
        if (colorSettings.autoOffTime != null) {
            params.auto_off = (colorSettings.autoOffTime as BigDecimal).toString()
        }
        if (colorSettings.autoOnTime != null) {
            params.auto_on = (colorSettings.autoOnTime as BigDecimal).toString()
        }
        if (!params) { return }

        Map result = sendGen1Setting(ipAddress, "settings/color/${lightId}", params)
        if (result != null) {
            logInfo("Applied Gen1 color settings to ${childDevice.displayName}: ${params}")
        }
    }
    // Gen 2/3 color settings can be added here later
}

// ═══════════════════════════════════════════════════════════════
// Switch Settings (default state, auto-on, auto-off)
// ═══════════════════════════════════════════════════════════════

/**
 * Receives switch settings from a standalone switch driver.
 * Routes to the appropriate Gen1 or Gen2+ settings application method.
 *
 * @param childDevice The standalone switch device
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void componentUpdateSwitchSettings(def childDevice, Map switchSettings) {
    if (!childDevice || !switchSettings) { return }
    String ipAddress = childDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("componentUpdateSwitchSettings: no IP for ${childDevice.displayName}")
        return
    }
    Integer switchId = extractComponentId(childDevice, 'switchId')
    if (isGen1Device(childDevice)) {
        applyGen1SwitchSettings(ipAddress, childDevice, switchId, switchSettings)
    } else {
        applyGen2SwitchSettings(ipAddress, childDevice, switchId, switchSettings)
    }
}

/**
 * Receives switch settings from a parent driver on behalf of a child component.
 *
 * @param parentDevice The parent device
 * @param switchId The switch component ID
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void parentUpdateSwitchSettings(def parentDevice, Integer switchId, Map switchSettings) {
    if (!parentDevice || !switchSettings) { return }
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("parentUpdateSwitchSettings: no IP for ${parentDevice.displayName}")
        return
    }
    if (isGen1Device(parentDevice)) {
        applyGen1SwitchSettings(ipAddress, parentDevice, switchId, switchSettings)
    } else {
        applyGen2SwitchSettings(ipAddress, parentDevice, switchId, switchSettings)
    }
}

/**
 * Receives white channel settings from a parent driver on behalf of a child component.
 * Applies settings to the Gen 1 {@code /settings/white/{id}} endpoint.
 *
 * @param parentDevice The parent device
 * @param whiteId The white channel component ID (0-3)
 * @param whiteSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
void parentUpdateWhiteSettings(def parentDevice, Integer whiteId, Map whiteSettings) {
    if (!parentDevice || !whiteSettings) { return }
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("parentUpdateWhiteSettings: no IP for ${parentDevice.displayName}")
        return
    }

    Map params = [:]
    if (whiteSettings.defaultState != null) {
        params.default_state = whiteSettings.defaultState.toString()
    }
    if (whiteSettings.autoOffTime != null) {
        params.auto_off = (whiteSettings.autoOffTime as BigDecimal).toString()
    }
    if (whiteSettings.autoOnTime != null) {
        params.auto_on = (whiteSettings.autoOnTime as BigDecimal).toString()
    }
    if (!params) { return }

    Map result = sendGen1Setting(ipAddress, "settings/white/${whiteId}", params)
    if (result != null) {
        logInfo("Applied Gen1 white settings to ${parentDevice.displayName} channel ${whiteId}: ${params}")
    }
}

/**
 * Applies switch settings to a Gen 2+ device via Switch.SetConfig RPC.
 *
 * @param ipAddress The device IP address
 * @param device The Hubitat device
 * @param switchId The switch component ID
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
private void applyGen2SwitchSettings(String ipAddress, def device, Integer switchId, Map switchSettings) {
    Map config = [:]
    if (switchSettings.defaultState != null) {
        String state = switchSettings.defaultState as String
        config.initial_state = (state == 'restore') ? 'restore_last' : state
    }
    if (switchSettings.autoOffTime != null) {
        BigDecimal seconds = switchSettings.autoOffTime as BigDecimal
        config.auto_off = (seconds > 0)
        config.auto_off_delay = seconds
    }
    if (switchSettings.autoOnTime != null) {
        BigDecimal seconds = switchSettings.autoOnTime as BigDecimal
        config.auto_on = (seconds > 0)
        config.auto_on_delay = seconds
    }
    if (!config) { return }

    String rpcUri = "http://${ipAddress}/rpc"
    LinkedHashMap command = switchSetConfigCommandJson(config, switchId)
    LinkedHashMap response = postCommandSync(command, rpcUri)
    logInfo("Applied Gen2+ switch config to ${device.displayName} switch:${switchId}: ${config}")
}

/**
 * Applies switch settings to a Gen 1 device via GET /settings/relay/N.
 *
 * @param ipAddress The device IP address
 * @param device The Hubitat device
 * @param switchId The relay index
 * @param switchSettings Map with keys: defaultState, autoOffTime, autoOnTime
 */
private void applyGen1SwitchSettings(String ipAddress, def device, Integer switchId, Map switchSettings) {
    Map params = [:]
    if (switchSettings.defaultState != null) {
        String rawState = switchSettings.defaultState as String
        params.default_state = (rawState == 'restore') ? 'last' : rawState
    }
    if (switchSettings.autoOffTime != null) {
        params.auto_off = (switchSettings.autoOffTime as BigDecimal).toString()
    }
    if (switchSettings.autoOnTime != null) {
        params.auto_on = (switchSettings.autoOnTime as BigDecimal).toString()
    }
    if (switchSettings.btn_type != null) {
        params.btn_type = switchSettings.btn_type as String
    }
    if (!params) { return }

    Map result = sendGen1Setting(ipAddress, "settings/relay/${switchId}", params)
    if (result != null) {
        logInfo("Applied Gen1 switch settings to ${device.displayName} relay:${switchId}: ${params}")
    } else {
        logWarn("Failed to apply Gen1 switch settings to ${device.displayName} relay:${switchId}")
    }
}

/**
 * Reads switch config from a device and updates the target driver's preferences.
 * Only applies to switch-type devices.
 *
 * @param targetDevice The Hubitat device whose preferences should be updated
 * @param ipAddress The device IP address
 */
private void syncSwitchConfigToDriver(def targetDevice, String ipAddress) {
    String componentType = targetDevice.getDataValue('componentType')
    if (componentType != null && componentType != 'switch') { return }

    // For standalone devices (componentType is null), check stored config
    // to avoid querying settings/relay on devices without switches (TRV, Motion, etc.)
    if (componentType == null) {
        String typeName = targetDevice.typeName ?: ''
        // EM Parent always has relay:0 even though config.hasSwitch is false (no switch: components)
        Boolean isEmParent = typeName.contains('EM Parent')
        String dni = targetDevice.deviceNetworkId
        Map config = (state.deviceConfigs ?: [:])[dni] as Map
        if (config) {
            if (!config.hasSwitch && !isEmParent) { return }
        } else {
            // Config not yet stored (first refresh during device creation).
            // Check driver name to skip non-switch devices.
            if (!typeName.contains('Switch') && !typeName.contains('Dimmer') && !typeName.contains('Plug') && !isEmParent) { return }
        }
    }

    Integer switchId = extractComponentId(targetDevice, 'switchId')

    if (isGen1Device(targetDevice)) {
        Map relaySettings = sendGen1Get(ipAddress, "settings/relay/${switchId}", [:])
        if (relaySettings) { syncGen1ConfigToPreferences(targetDevice, relaySettings) }

        // Sync device-level settings (LED control) for Plug and EM Parent drivers
        String typeName = targetDevice.typeName ?: ''
        if (typeName.contains('Plug') || typeName.contains('EM Parent')) {
            Map deviceSettings = sendGen1Get(ipAddress, 'settings', [:])
            if (deviceSettings) { syncGen1DeviceSettingsToPreferences(targetDevice, deviceSettings) }
        }
    } else {
        String rpcUri = "http://${ipAddress}/rpc"
        LinkedHashMap command = switchGetConfigCommand(switchId, 'syncSwitchConfig')
        LinkedHashMap response = postCommandSync(command, rpcUri)
        if (response) { syncGen2ConfigToPreferences(targetDevice, response) }
    }
}

/**
 * Syncs switch config to preferences for all switch children of a parent device.
 *
 * @param parentDni The parent device network ID
 * @param ipAddress The device IP address
 */
private void syncSwitchConfigForParentChildren(String parentDni, String ipAddress) {
    getChildDevices()?.each { child ->
        if (child.deviceNetworkId.startsWith("${parentDni}-switch-")) {
            syncSwitchConfigToDriver(child, ipAddress)
        }
    }
}

/**
 * Maps Gen 2+ Switch.GetConfig response to Hubitat driver preferences.
 *
 * @param targetDevice The device whose preferences to update
 * @param config The Switch.GetConfig response map
 */
private void syncGen2ConfigToPreferences(def targetDevice, Map config) {
    if (config.initial_state) {
        String val = (config.initial_state == 'restore_last') ? 'restore' : config.initial_state.toString()
        deviceUpdateSettingHelper(targetDevice, 'defaultState', [type: 'enum', value: val])
    }
    if (config.auto_off_delay != null) {
        BigDecimal offTime = (config.auto_off == true) ? (config.auto_off_delay as BigDecimal) : 0
        deviceUpdateSettingHelper(targetDevice, 'autoOffTime', [type: 'decimal', value: offTime])
    }
    if (config.auto_on_delay != null) {
        BigDecimal onTime = (config.auto_on == true) ? (config.auto_on_delay as BigDecimal) : 0
        deviceUpdateSettingHelper(targetDevice, 'autoOnTime', [type: 'decimal', value: onTime])
    }
}

/**
 * Maps Gen 1 /settings/relay/N response to Hubitat driver preferences.
 *
 * @param targetDevice The device whose preferences to update
 * @param relaySettings The Gen 1 relay settings response map
 */
private void syncGen1ConfigToPreferences(def targetDevice, Map relaySettings) {
    if (relaySettings.default_state) {
        String rawState = relaySettings.default_state.toString()
        String mappedState = (rawState == 'last') ? 'restore' : rawState
        deviceUpdateSettingHelper(targetDevice, 'defaultState', [type: 'enum', value: mappedState])
    }
    if (relaySettings.auto_off != null) {
        deviceUpdateSettingHelper(targetDevice, 'autoOffTime', [type: 'decimal', value: relaySettings.auto_off])
    }
    if (relaySettings.auto_on != null) {
        deviceUpdateSettingHelper(targetDevice, 'autoOnTime', [type: 'decimal', value: relaySettings.auto_on])
    }
    if (relaySettings.btn_type) {
        deviceUpdateSettingHelper(targetDevice, 'buttonType', [type: 'enum', value: relaySettings.btn_type.toString()])
    }
}

/**
 * Maps Gen 1 device-level /settings response to Hubitat driver preferences.
 * Used for Plug-specific settings like LED control that live at the device level
 * rather than per-relay.
 *
 * @param targetDevice The device whose preferences to update
 * @param deviceSettings The Gen 1 /settings response map
 */
private void syncGen1DeviceSettingsToPreferences(def targetDevice, Map deviceSettings) {
    if (deviceSettings.led_status_disable != null) {
        deviceUpdateSettingHelper(targetDevice, 'ledStatusDisable',
            [type: 'bool', value: deviceSettings.led_status_disable.toString() == 'true'])
    }
    if (deviceSettings.led_power_disable != null) {
        deviceUpdateSettingHelper(targetDevice, 'ledPowerDisable',
            [type: 'bool', value: deviceSettings.led_power_disable.toString() == 'true'])
    }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  Parent Driver Support Methods                                ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Sends a Shelly RPC command on behalf of a parent device.
 * Called by parent drivers when child devices issue commands.
 * Uses the app's centralized HTTP infrastructure with error handling and watchdog.
 *
 * @param parentDevice The parent device requesting the command
 * @param method The Shelly RPC method name (e.g., 'Switch.Set', 'Cover.Open')
 * @param params The RPC method parameters map
 */
void parentSendCommand(def parentDevice, String method, Map params) {
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("parentSendCommand: no IP for ${parentDevice.displayName}")
        return
    }

    logDebug("parentSendCommand from ${parentDevice.displayName}: ${method} with params ${params}")

    // Gen 1: route standard RPC method names to Gen 1 REST endpoints
    if (isGen1Device(parentDevice)) {
        Integer componentId = params?.id != null ? params.id as Integer : 0
        switch (method) {
            case 'Switch.Set':
                String action = params?.on ? 'on' : 'off'
                sendGen1Get(ipAddress, "relay/${componentId}", [turn: action])
                break
            case 'Cover.Open':
                sendGen1Get(ipAddress, "roller/${componentId}", [go: 'open'])
                break
            case 'Cover.Close':
                sendGen1Get(ipAddress, "roller/${componentId}", [go: 'close'])
                break
            case 'Cover.GoToPosition':
                sendGen1Get(ipAddress, "roller/${componentId}", [go: 'to_pos', roller_pos: (params?.pos ?: 0).toString()])
                break
            case 'Cover.Stop':
                sendGen1Get(ipAddress, "roller/${componentId}", [go: 'stop'])
                break
            case 'Light.Set':
                String turnAction = params?.on ? 'on' : 'off'
                Map lightParams = [turn: turnAction]
                if (params?.brightness != null) { lightParams.brightness = params.brightness.toString() }
                sendGen1Get(ipAddress, "light/${componentId}", lightParams)
                break
            case 'White.Set':
                String whiteAction = params?.on ? 'on' : 'off'
                sendGen1Get(ipAddress, "white/${componentId}", [turn: whiteAction])
                break
            case 'White.SetLevel':
                Integer brightness = params?.brightness != null ? params.brightness as Integer : 0
                String whiteTurn = brightness > 0 ? 'on' : 'off'
                Map whiteParams = [turn: whiteTurn, brightness: brightness.toString()]
                if (params?.transitionMs != null && (params.transitionMs as Integer) > 0) {
                    whiteParams.transition = params.transitionMs.toString()
                }
                sendGen1Get(ipAddress, "white/${componentId}", whiteParams)
                break
            default:
                logWarn("parentSendCommand: unsupported Gen 1 method '${method}'")
        }
        return
    }

    // Gen 2/3: standard JSON-RPC
    String rpcUri = "http://${ipAddress}/rpc"
    LinkedHashMap command = [
        id: 0,
        src: 'hubitat',
        method: method,
        params: params
    ]

    LinkedHashMap response = postCommandSync(command, rpcUri)

    if (response?.error) {
        logError("parentSendCommand RPC error: ${response.error}")
    } else {
        logDebug("parentSendCommand success: ${method} → ${response}")
    }
}

/**
 * Refreshes device status on behalf of a parent device.
 * Queries Shelly.GetStatus and sends the status back to the parent via distributeStatus().
 *
 * @param parentDevice The parent device requesting refresh
 */
void parentRefresh(def parentDevice) {
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("parentRefresh: no IP for ${parentDevice.displayName}")
        return
    }

    logDebug("parentRefresh called for ${parentDevice.displayName} (${ipAddress})")

    // Query device status
    Map deviceStatus = queryDeviceStatus(ipAddress)
    if (!deviceStatus) {
        logWarn("parentRefresh: no status returned for ${ipAddress}")
        return
    }

    // Send status to parent driver via distributeStatus() callback
    try {
        parentDevice.distributeStatus(deviceStatus)
        logDebug("Sent status to ${parentDevice.displayName}")
    } catch (Exception e) {
        logError("Failed to distribute status to ${parentDevice.displayName}: ${e.message}")
    }
}

/**
 * Reinitializes a parent device: re-queries physical state, reinstalls scripts/webhooks,
 * and calls the device's initialize() method.
 * Overload that accepts a device object (called by parent drivers via parent?.reinitializeDevice(device)).
 *
 * @param parentDevice The parent device to reinitialize
 */
void reinitializeDevice(def parentDevice) {
    String ipAddress = parentDevice.getDataValue('ipAddress')
    if (!ipAddress) {
        logError("reinitializeDevice: no IP for ${parentDevice.displayName}")
        return
    }

    logInfo("Reinitializing parent device ${parentDevice.displayName} via driver request")
    reinitializeDevice(ipAddress)
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Parent Driver Support Methods                            ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Distributes a full Shelly.GetStatus result to all child devices of a parent.
 *
 * @param parentDni The parent device network ID
 * @param deviceStatus The full device status map from Shelly.GetStatus
 */
private void distributeStatusToChildren(String parentDni, Map deviceStatus) {
    Set<String> childComponentTypes = ['switch', 'cover', 'light', 'white', 'input'] as Set

    deviceStatus.each { k, v ->
        String key = k.toString()
        String baseType = key.contains(':') ? key.split(':')[0] : key
        if (!childComponentTypes.contains(baseType)) { return }
        if (!(v instanceof Map)) { return }

        Integer componentId = key.contains(':') ? (key.split(':')[1] as Integer) : 0
        String childDni = "${parentDni}-${baseType}-${componentId}"
        def child = getChildDevice(childDni)

        if (child) {
            updateChildFromStatus(child, baseType, v as Map)
        }
    }
}

/**
 * Updates a child device's attributes from a Shelly component status map.
 *
 * @param child The child device to update
 * @param componentType The component type (switch, cover, light, input)
 * @param statusData The status data map for this component
 */
private void updateChildFromStatus(def child, String componentType, Map statusData) {
    List<Map> events = []

    switch (componentType) {
        case 'switch':
            if (statusData.output != null) {
                String switchState = statusData.output ? 'on' : 'off'
                events.add([name: 'switch', value: switchState,
                    descriptionText: "Switch is ${switchState}"])
            }
            // Power monitoring values
            if (statusData.voltage != null) {
                events.add([name: 'voltage', value: statusData.voltage as BigDecimal,
                    unit: 'V', descriptionText: "Voltage is ${statusData.voltage}V"])
            }
            if (statusData.current != null) {
                events.add([name: 'amperage', value: statusData.current as BigDecimal,
                    unit: 'A', descriptionText: "Current is ${statusData.current}A"])
            }
            if (statusData.apower != null) {
                events.add([name: 'power', value: statusData.apower as BigDecimal,
                    unit: 'W', descriptionText: "Power is ${statusData.apower}W"])
            }
            if (statusData.aenergy?.total != null) {
                BigDecimal energyWh = statusData.aenergy.total as BigDecimal
                BigDecimal energyKwh = energyWh / 1000
                events.add([name: 'energy', value: energyKwh,
                    unit: 'kWh', descriptionText: "Energy is ${energyKwh}kWh"])
            }
            break

        case 'cover':
            if (statusData.state != null) {
                String shadeState
                switch (statusData.state) {
                    case 'open': shadeState = 'open'; break
                    case 'closed': shadeState = 'closed'; break
                    case 'opening': shadeState = 'opening'; break
                    case 'closing': shadeState = 'closing'; break
                    case 'stopped': shadeState = 'partially open'; break
                    default: shadeState = 'unknown'
                }
                events.add([name: 'windowShade', value: shadeState,
                    descriptionText: "Window shade is ${shadeState}"])
            }
            if (statusData.current_pos != null) {
                events.add([name: 'position', value: statusData.current_pos as Integer,
                    unit: '%', descriptionText: "Position is ${statusData.current_pos}%"])
            }
            break

        case 'light':
            if (statusData.output != null) {
                String switchState = statusData.output ? 'on' : 'off'
                events.add([name: 'switch', value: switchState,
                    descriptionText: "Switch is ${switchState}"])
            }
            if (statusData.brightness != null) {
                events.add([name: 'level', value: statusData.brightness as Integer,
                    unit: '%', descriptionText: "Level is ${statusData.brightness}%"])
            }
            break

        case 'input':
            // Input components don't have persistent state to refresh
            break
    }

    events.each { Map evt ->
        childSendEventHelper(child, evt)
    }

    if (events.size() > 0) {
        childSendEventHelper(child, [name: 'lastUpdated', value: new Date().format('yyyy-MM-dd HH:mm:ss')])
    }
}

// ═══════════════════════════════════════════════════════════════
// END Parent-Child Parse Routing & Refresh
// ═══════════════════════════════════════════════════════════════