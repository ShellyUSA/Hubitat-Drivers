---
applyTo: "ShellyDriverLibrary/**"
---

# Shared library instructions (ShellyDriverLibrary)

**CRITICAL**: `ShellyDriverLibrary/` contains the core implementation for ALL active drivers. Changes here affect every driver that includes the library.

---

## Architecture overview

**The library (`ShellyUSA.ShellyUSA_Driver_Library.groovy`) is the heart of the driver system.**

Individual drivers in `WebhookWebsocket/`, `ComponentDrivers/`, and `Bluetooth/` are intentionally minimal—they define metadata and device-specific overrides, then include the library via:

```groovy
#include ShellyUSA.ShellyUSA_Driver_Library
```

**What the library provides**:

- HTTP and websocket communication primitives
- Child device lifecycle management (creation, updates, deletion)
- Standard command implementations (on, off, refresh, setLevel, setColor, etc.)
- Preference handling via `preferenceMap`
- Logging wrappers with enable/disable toggles
- Status parsing and state updates
- WebSocket reconnection and heartbeat logic
- Device capability detection and initialization

**Design philosophy**:

- Drivers should **not** reimplement library functionality
- Common behavior lives in the library; unique behavior lives in drivers
- Changes must be backward-compatible or provide clear migration paths

---

## Rules for modifying the library

### 1. Impact assessment

**Before making changes, understand the scope**:

- Run: `grep -r "#include ShellyUSA.ShellyUSA_Driver_Library" --include="*.groovy"` to identify all affected drivers
- Changes here affect 30+ active drivers simultaneously
- Test with multiple driver types (switch, dimmer, sensor, cover) if possible

### 2. Backward compatibility

**NEVER introduce breaking changes without explicit user approval and migration plan**:

- Do not remove or rename public functions used by drivers
- Do not change function signatures (parameters, return types)
- Do not modify state/data storage keys without migration logic
- Do not change attribute names or event formats
- Prefer adding new functions over modifying existing ones

### 3. Additive changes

**When adding functionality**:

- Add new helper functions with clear, descriptive names
- Document parameters, return values, and usage with inline comments
- Provide usage examples in comments or PR description
- Add to logical sections (networking, logging, child management, etc.)
- Consider if the change belongs in the library or in individual drivers

### 4. Code style and documentation

**Make the library maintainable**:

- Use clear, self-documenting function names
- Add comprehensive inline comments for complex logic
- Group related functions together
- Mark internal/private helpers clearly (e.g., `// Internal use only`)
- Document any assumptions about device state or configuration

### 5. Testing requirements

**Library changes require extensive testing**:

- Test with at least 2-3 different driver types (e.g., switch, dimmer, sensor)
- Verify child device creation/management still works
- Test websocket connectivity and reconnection
- Test HTTP fallback for devices without websocket support
- Check logs for errors or warnings
- Verify backward compatibility with existing devices

---

## Key library components

### HTTP/WebSocket communication

- `parentPostCommandAsync()` — async HTTP POST
- `parentGetCommand()` — HTTP GET
- `parentWsConnect()` — establish websocket connection
- `parentWsSendMessage()` — send websocket message
- `parentWsReconnect()` — reconnection logic
- `webSocketStatus()` — connection monitoring

### Child device management

- `createChildSwitch()` — create switch child device
- `createChildSensor()` — create sensor child device
- `updateChildDevice()` — update child attributes
- `deleteChildDevices()` — cleanup orphaned children
- `getChildDevice()` — retrieve child by network ID

### Preference handling

- `preferenceMap` — centralized preference definitions
- Standard preferences: `ipAddress`, `deviceType`, logging toggles, refresh intervals
- Drivers can add device-specific preferences in their own `preferences {}` block

### Logging system

- `logDebug(msg)` — debug-level logging (respects `debugLogEnable`)
- `logTrace(msg)` — trace-level logging (respects `traceLogEnable`)
- `logInfo(msg)` — info-level logging (respects `logEnable`)
- `logWarn(msg)` — warning (always shown)
- `logError(msg)` — error (always shown)
- Auto-disable logic in `updated()` (30 min default)

### Command implementations

Standard commands implemented in library:

- `on()`, `off()` — switch control
- `setLevel(level, duration)` — dimming
- `setColor(colorMap)` — RGB/RGBW color
- `setColorTemperature(temp)` — white temperature
- `refresh()` — fetch current state
- `open()`, `close()`, `setPosition()` — cover/shade control

### State management

- `parseStatus(data)` — parse device status responses
- `updateState(attr, value)` — update device attributes
- `sendEvent(map)` — send events to Hubitat

---

## Validation workflow for library changes

### 1. Static validation

```bash
# Syntax check (requires local groovyc)
groovyc ShellyUSA.ShellyUSA_Driver_Library.groovy
```

### 2. Identify affected drivers

```bash
# Find all drivers using the library
grep -r "#include ShellyUSA.ShellyUSA_Driver_Library" WebhookWebsocket/ ComponentDrivers/ Bluetooth/ --include="*.groovy"
```

### 3. Test with representative drivers

Pick at least 2-3 drivers from different categories:

- **Switch**: `ShellyPlus1.groovy` or `ShellyPlus1PM.groovy`
- **Dimmer**: `ShellyPlusDimmer.groovy` or `ShellyPlus0-10vDimmer.groovy`
- **Sensor**: `ShellyH&T.groovy` or `ShellyMotion2.groovy`
- **Cover**: Component drivers if applicable

For each test driver:

1. Upload to Hubitat (`Drivers Code` → `New Driver`)
2. Create/update test device
3. Configure preferences and save
4. Test core commands (on/off, refresh, etc.)
5. Verify child devices (if applicable)
6. Check logs for errors
7. Test websocket connection (if applicable)

### 4. Smoke test checklist

- [ ] Device initializes correctly
- [ ] Commands execute without errors
- [ ] State updates appear in `Current States`
- [ ] Logging respects enable/disable preferences
- [ ] Child devices create/update correctly
- [ ] WebSocket connects and stays connected (where applicable)
- [ ] HTTP fallback works when websocket unavailable
- [ ] No unexpected error logs or stack traces

---

## When changes require human review

**ALWAYS open a draft PR with `needs-human-review` and `ai-generated` labels if**:

- Changing or removing any public function
- Modifying function signatures (parameters, return types)
- Changing websocket or HTTP communication patterns
- Altering child device creation/management logic
- Modifying state storage or data persistence
- Large refactors (>100 lines changed)

**Include in the PR**:

- Clear description of what changed and why
- List of affected drivers (from grep above)
- Testing performed (which drivers, what commands)
- Expected impact on existing devices
- Migration instructions (if any)
- Updated `resources/version.json` with library version bump
- Detailed testing steps for reviewer

---

## Common modification patterns

### Adding a new helper function

```groovy
/**
 * Brief description of what this does
 * @param param1 Description of first parameter
 * @param param2 Description of second parameter
 * @return Description of return value
 */
void newHelperFunction(String param1, Integer param2) {
    logTrace "newHelperFunction called with ${param1}, ${param2}"
    // Implementation
}
```

### Extending existing functionality

```groovy
// Add optional parameter with default value (backward compatible)
void existingFunction(String param1, Boolean param2 = false) {
    // Original logic
    if (param2) {
        // New optional behavior
    }
}
```

### Adding preference support

```groovy
// In library, add to preferenceMap
preferenceMap = [
    // Existing preferences...
    newPreference: [
        name: "newPreference",
        type: "bool",
        title: "Enable New Feature",
        description: "Description of what this does",
        defaultValue: false
    ]
]
```

---

## File organization

### `ShellyUSA.ShellyUSA_Driver_Library.groovy`

Main library file with all shared code.

### `install.txt` and `update.txt`

Metadata files for Hubitat Package Manager (HPM):

- Update these if library file location or name changes
- Keep version information synchronized with `resources/version.json`

### `importUrl`

Ensure the `importUrl` in library metadata points to the correct GitHub raw URL.

---

## Summary checklist for library changes

- [ ] Have I assessed the impact on all drivers using this library?
- [ ] Is this change backward-compatible?
- [ ] Have I added clear inline documentation?
- [ ] Have I tested with 2-3 different driver types?
- [ ] Have I verified child device functionality?
- [ ] Have I checked websocket connectivity?
- [ ] Have I updated `resources/version.json`?
- [ ] Have I updated `install.txt`/`update.txt` if needed?
- [ ] Is there a draft PR with `needs-human-review` label?
- [ ] Have I provided detailed testing steps for reviewers?
- [ ] Are there no committed secrets or test IPs?
