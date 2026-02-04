---
applyTo: "**/*.groovy"
---

# Groovy driver instructions (applies to all `*.groovy` files)

Scope: This file applies to all driver and library Groovy files in the repository. It contains repository-specific style and validation rules for coding agents.

---

## IMPORTANT: Legacy vs. Active Code

**DO NOT MODIFY legacy code unless explicitly requested by the user.**

Legacy code (not actively maintained):

- `PLUS/` folder — older Plus drivers (superseded by WebhookWebsocket implementations)
- Root-level `Shelly*.groovy` files (e.g., `Shelly-as-a-Switch.groovy`, `Shelly-Bulb.groovy`, etc.)
- `contribs/` folder — community contributions, not actively maintained

Active code (primary maintenance focus):

- `WebhookWebsocket/` folder — modern webhook/websocket-based drivers
- `ComponentDrivers/` folder — reusable component drivers
- `Bluetooth/` folder — Bluetooth device drivers
- `ShellyDriverLibrary/` folder — shared library code (see below)

---

## Architecture: ShellyDriverLibrary

**The bulk of driver logic resides in `ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy`.**

Individual driver files (in `WebhookWebsocket/`, `ComponentDrivers/`, `Bluetooth/`) are intentionally minimal. They:

1. Define driver-specific metadata (name, namespace, capabilities, attributes, commands)
2. Include the shared library via `#include ShellyUSA.ShellyUSA_Driver_Library`
3. Override or extend specific functions when device-specific behavior is needed

**Key principle**: When adding features or fixing bugs, prefer modifying the shared library if the change applies to multiple drivers. Only add driver-specific code when the behavior is unique to that device model.

**Library structure**:

- HTTP/websocket communication helpers
- Child device creation and management (switches, sensors, etc.)
- Preference handling via `preferenceMap`
- Logging wrappers (`logDebug`, `logTrace`, etc.)
- Common command implementations (on, off, refresh, setLevel, etc.)

---

## Guidelines for Active Code

### Metadata and includes

- Always preserve the `metadata { definition(...) }` block
- Never remove or modify `#include` lines (e.g., `#include ShellyUSA.ShellyUSA_Driver_Library`)
- Keep `importUrl` accurate if files move or are renamed
- Update `version` in metadata when making changes

### Capabilities and attributes

- Keep public capabilities and attribute names unchanged unless absolutely necessary
- Prefer additive changes (new attributes/commands) over breaking changes
- Document any breaking changes clearly in PR and `resources/version.json`

### Using the shared library

- **Always** use helper functions from `ShellyDriverLibrary/` for:
  - Networking: `parentPostCommandAsync`, `parentGetCommand`, websocket management
  - Child devices: `createChildSwitch`, `createChildSensor`, etc.
  - Preferences: use `preferenceMap` entries for consistent handling
  - Logging: `logDebug`, `logTrace`, `logInfo`, `logWarn`, `logError`
- Do **not** duplicate library logic in individual drivers
- If you need to modify shared behavior, update the library (see `library.instructions.md`)

### Logging conventions

- Use preference toggles: `logEnable`, `debugLogEnable`, `traceLogEnable`
- Use wrapper functions: `logDebug()`, `logTrace()`, etc.
- **Never enable trace logging by default** (performance impact)
- Add `updated()` auto-disable logic for debug/trace logs (typically 30 minutes)

### Preferences

- Use `preferenceMap` entries when possible (defined in shared library)
- Maintain consistent key naming across drivers
- Group related preferences logically
- Include helpful descriptions and valid ranges
- Document preference changes in `UpdateInfo`

### Code style

- Add inline comments for non-obvious logic
- Use meaningful variable and function names
- Keep functions focused and single-purpose
- Use `@CompileStatic` **only after testing on Hubitat** (dynamic Groovy features may break)

### Device-specific overrides

When a driver needs custom behavior:

```groovy
// Override library function for device-specific behavior
void refresh() {
    // Device-specific refresh logic
    logDebug "Custom refresh for ShellyModelX"
    // Call parent/library implementation if needed
    super.refresh()
}
```

---

## Validation steps (manual)

1. **Static check**: Run `groovyc <filename>.groovy` to check for syntax errors (optional, requires local Groovy installation)
2. **Hub upload**: Paste the `.groovy` file into Hubitat web UI:
   - Navigate to: `Drivers Code` → `New Driver` → paste code → `Save`
3. **Device creation/update**:
   - Create a new test device or update an existing one to use the modified driver
   - Set required preferences (e.g., `ipAddress`, `deviceType`)
   - Click `Save Preferences`
4. **Functional test**:
   - Run `refresh` command and check device `Current States`
   - Test main commands (on/off, setLevel, etc.)
   - Monitor `Logs` for errors, warnings, or unexpected behavior
   - Verify child devices are created/updated correctly (if applicable)
5. **Log verification**:
   - Enable debug logging in preferences
   - Check for expected log messages
   - Verify no error stack traces appear

---

## When to open a draft PR / request human review

**Always open a draft PR with `needs-hardware-test` and `ai-generated` labels if**:

- The change requires physical Shelly hardware to test
- Websocket behavior changes (requires active device connection)
- Firmware-specific behavior (requires specific device firmware version)
- Child device creation/deletion logic changes

**Request human review if**:

- Modifying public capabilities, attributes, or commands (breaking changes)
- Changing persistent data storage keys or state management
- Removing or renaming functions used by multiple drivers
- Large refactors spanning multiple files

**Include in PR**:

- Short title: `driver: <DriverName> — <one-line summary>` or `fix: <DriverName> — <one-line summary>`
- Detailed testing steps with expected results
- Device model/firmware version requirements
- Updated `resources/version.json` (if driver behavior changed)
- Updated `PackageManifests/*` (if packaging changed)
- No committed secrets (use placeholders like `HUBITAT-IP-ADDRESS`)

---

## Common patterns in active drivers

### Minimal driver structure

```groovy
metadata {
    definition(name: "Shelly Device Name", namespace: "ShellyUSA", author: "...") {
        capability "Switch"
        // Other capabilities...
    }
}

#include ShellyUSA.ShellyUSA_Driver_Library

// Override only device-specific functions
// Most logic comes from the library
```

### Adding device-specific preferences

```groovy
preferences {
    input name: "deviceSpecificPref", type: "bool", title: "Enable Feature X", defaultValue: false
    // Standard preferences come from library's preferenceMap
}
```

### WebSocket-based drivers

- Use library helpers: `parentWsConnect()`, `parentWsSendMessage()`, etc.
- Implement `parse()` for websocket message handling
- Follow existing patterns in `WebhookWebsocket/` drivers

---

## Summary checklist for agents

- [ ] Am I modifying active code (not legacy)?
- [ ] Does this change belong in the shared library or a specific driver?
- [ ] Have I preserved all `#include` lines and metadata?
- [ ] Am I using library helpers instead of duplicating code?
- [ ] Have I followed logging conventions (no trace by default)?
- [ ] Is this change backward-compatible?
- [ ] Have I updated `resources/version.json` if needed?
- [ ] Can this be validated without hardware, or do I need a draft PR?
- [ ] Have I included detailed testing steps?
- [ ] Are there no committed secrets or real IPs?
