---
applyTo: "resources/version.json"
---

# `resources/version.json` (versioning & update notes) rules

**Purpose**: This file tracks version numbers and user-facing update information for all active drivers and the shared library.

---

## IMPORTANT: Active vs. Legacy code

**Only update entries for active drivers** (WebhookWebsocket/, ComponentDrivers/, Bluetooth/, and ShellyDriverLibrary/).

**DO NOT add or update entries for legacy code**:

- Drivers in `PLUS/` folder
- Root-level `Shelly*.groovy` files
- Drivers in `contribs/` folder

Legacy drivers are not actively maintained and should not appear in release notes or version tracking.

---

## File structure

```json
{
  "Comment": "Last updated: YYYY-MM-DD",
  "versions": {
    "Driver": {
      "DriverName": "X.Y.Z",
      "AnotherDriver": "X.Y.Z",
      "ShellyUSA_Driver_Library": "X.Y.Z"
    }
  },
  "UpdateInfo": {
    "Driver": {
      "DriverName": "Brief HTML-formatted update note<br>Second line if needed",
      "ShellyUSA_Driver_Library": "Library update description"
    }
  }
}
```

---

## Versioning scheme

### Semantic versioning (MAJOR.MINOR.PATCH)

Use for stable releases:

- **MAJOR** (X.0.0): Breaking changes, major rewrites, API changes
- **MINOR** (0.X.0): New features, capabilities, or commands (backward-compatible)
- **PATCH** (0.0.X): Bug fixes, small improvements (backward-compatible)

### Beta/pre-release versioning

Existing `BETA` entries are acceptable:

- Format: `X.Y.Z-BETA` or `X.Y.Z.BETA`
- Use for pre-release testing or experimental features

---

## When to update this file

**Update `resources/version.json` when**:

1. Changing driver behavior (commands, attributes, state management)
2. Adding new features or capabilities to a driver
3. Fixing bugs that affect user experience
4. Modifying the shared library (`ShellyDriverLibrary/`)
5. Releasing a driver update through HPM

**Do NOT update for**:

- Code cleanup with no functional changes
- Comment or documentation updates only
- Changes to legacy drivers (PLUS/, root Shelly\*.groovy, contribs/)

---

## Update workflow

### 1. Determine which drivers are affected

- Individual driver change: Update that driver's entry
- Library change: Update `ShellyUSA_Driver_Library` entry
- Library change also affects drivers: Update library AND all affected driver entries

### 2. Update version numbers

```json
"versions": {
  "Driver": {
    "ShellyPlus1PM": "2.3.1",  // Was 2.3.0, bug fix → patch bump
    "ShellyUSA_Driver_Library": "3.1.0"  // Was 3.0.5, new feature → minor bump
  }
}
```

### 3. Add UpdateInfo entries

Format: Short HTML string with `<br>` for line breaks

```json
"UpdateInfo": {
  "Driver": {
    "ShellyPlus1PM": "Fixed power reporting accuracy<br>Improved websocket reconnection logic",
    "ShellyUSA_Driver_Library": "Added support for device-level power metering<br>Improved child device lifecycle management"
  }
}
```

**UpdateInfo guidelines**:

- Write for end users (non-technical language)
- Focus on user-visible changes (new features, bug fixes, behavior changes)
- Keep to 1-3 bullets maximum
- Use `<br>` to separate bullets (do NOT use `<ul>` or `<li>`)
- Omit internal implementation details
- Explain impact: "Fixed X" or "Added Y" rather than "Changed function Z"

### 4. Update the Comment field

```json
{
  "Comment": "Last updated: 2026-02-04"
  // ...
}
```

Use ISO date format: `YYYY-MM-DD`

---

## Common scenarios

### Scenario 1: Single driver bug fix

```json
// Before
"ShellyPlus1PM": "2.3.0"

// After
"ShellyPlus1PM": "2.3.1"
"UpdateInfo": {
  "Driver": {
    "ShellyPlus1PM": "Fixed energy reporting not updating after power cycle"
  }
}
```

### Scenario 2: Library enhancement affecting multiple drivers

```json
// Library gets minor bump (new feature)
"ShellyUSA_Driver_Library": "3.1.0"  // was 3.0.5

// Affected drivers get patch bump (they inherit the feature)
"ShellyPlus1PM": "2.3.1",  // was 2.3.0
"ShellyPlus2PM": "2.1.3",  // was 2.1.2
"ShellyPlusDimmer": "1.8.1"  // was 1.8.0

"UpdateInfo": {
  "Driver": {
    "ShellyUSA_Driver_Library": "Added automatic retry for failed HTTP commands<br>Improved websocket heartbeat reliability",
    "ShellyPlus1PM": "Updated to use library v3.1.0 (improved reliability)",
    "ShellyPlus2PM": "Updated to use library v3.1.0 (improved reliability)",
    "ShellyPlusDimmer": "Updated to use library v3.1.0 (improved reliability)"
  }
}
```

### Scenario 3: New driver capability

```json
// Minor version bump (new feature)
"ShellyPlusDimmer": "1.9.0"  // was 1.8.0

"UpdateInfo": {
  "Driver": {
    "ShellyPlusDimmer": "Added color temperature control support<br>Added presets for warm/cool white"
  }
}
```

---

## Driver naming conventions

Driver names in `version.json` should match their actual filename (without `.groovy` extension):

**Correct**:

```json
"ShellyPlus1PM": "2.3.0",
"ShellyPlusDimmer": "1.8.0",
"ShellyBluGateway": "1.2.0"
```

**Incorrect**:

```json
"Shelly Plus 1PM": "2.3.0",  // Wrong: spaces
"shellyplus1pm": "2.3.0",    // Wrong: case
"ShellyPlus1PM.groovy": "2.3.0"  // Wrong: includes extension
```

---

## Validation before commit

### 1. JSON syntax validation

```bash
# Validate JSON syntax
jq empty resources/version.json

# Or use an online validator: https://jsonlint.com/
```

### 2. Formatting check

- Maintain consistent indentation (2 or 4 spaces, match existing file)
- Sort driver entries alphabetically (helpful but not required)
- Ensure no trailing commas

### 3. Content review

- [ ] Version numbers follow semantic versioning
- [ ] UpdateInfo is user-friendly (no technical jargon)
- [ ] Comment date is current
- [ ] Only active drivers are included (no PLUS/, root, or contribs/ entries)
- [ ] No sensitive information (IPs, tokens, passwords)

---

## Integration with HPM (Hubitat Package Manager)

When drivers are released through HPM:

1. Update `resources/version.json` as described above
2. Update corresponding `PackageManifests/*.json` file
3. Ensure version numbers match between `version.json` and manifest
4. See `packagemanifests.instructions.md` for manifest-specific rules

---

## Summary checklist

- [ ] Am I updating an active driver (not legacy)?
- [ ] Have I bumped the version number appropriately?
- [ ] Have I added user-friendly UpdateInfo?
- [ ] Have I updated the Comment date?
- [ ] Is the JSON valid (tested with `jq` or validator)?
- [ ] Have I updated affected drivers if this is a library change?
- [ ] Do version numbers match any updated HPM manifests?
- [ ] Have I removed any legacy driver entries if they were present?
