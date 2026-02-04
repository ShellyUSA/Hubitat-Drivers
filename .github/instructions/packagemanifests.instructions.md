---
applyTo: "PackageManifests/**"
---

# HPM / PackageManifests instructions

**Purpose**: Package manifests enable Hubitat Package Manager (HPM) to install and update drivers on user hubs.

---

## IMPORTANT: Active vs. Legacy code

**Only create/update manifests for active drivers**:

- Drivers in `WebhookWebsocket/` folder
- Drivers in `ComponentDrivers/` folder
- Drivers in `Bluetooth/` folder
- The shared `ShellyDriverLibrary/`

**DO NOT create manifests for legacy code**:

- Drivers in `PLUS/` folder
- Root-level `Shelly*.groovy` files
- Drivers in `contribs/` folder

Legacy drivers should not be distributed through HPM.

---

## Manifest structure

HPM manifests are JSON files that describe:

- Package metadata (name, author, description)
- Driver/app files to install
- Version information
- Compatibility requirements
- Update/release notes

**Key fields**:

```json
{
  "packageName": "Shelly Driver Name",
  "author": "Your Name",
  "version": "X.Y.Z",
  "minimumHEVersion": "2.2.0",
  "dateReleased": "2026-02-04",
  "drivers": [
    {
      "id": "unique-driver-id",
      "name": "Driver Display Name",
      "namespace": "ShellyUSA",
      "location": "https://raw.githubusercontent.com/.../Driver.groovy",
      "required": true
    }
  ],
  "releaseNotes": "Description of changes in this release"
}
```

---

## When to update manifests

**Update a manifest when**:

1. Releasing a new driver version with user-facing changes
2. Adding a new driver to an existing package
3. Changing driver file locations or URLs
4. Updating compatibility requirements (`minimumHEVersion`)
5. Including new library files

**Do NOT update for**:

- Changes to legacy drivers
- Internal code refactors with no functional changes
- Documentation-only updates

---

## Manifest update workflow

### 1. Identify the correct manifest file

```
PackageManifests/
  ShellyWebhookDrivers/
    manifest.json  // For WebhookWebsocket drivers
  ShellyComponents/
    manifest.json  // For ComponentDrivers
  ShellyBluetooth/
    manifest.json  // For Bluetooth drivers
```

### 2. Update version and date

```json
{
  "version": "2.3.1", // Match version in resources/version.json
  "dateReleased": "2026-02-04" // Use ISO format: YYYY-MM-DD
}
```

**Version number must match `resources/version.json`** for consistency.

### 3. Update driver entries

Ensure all active drivers in the package are listed:

```json
"drivers": [
  {
    "id": "shellyplus1pm-driver",
    "name": "Shelly Plus 1PM",
    "namespace": "ShellyUSA",
    "location": "https://raw.githubusercontent.com/user/repo/main/WebhookWebsocket/ShellyPlus1PM.groovy",
    "required": true,
    "version": "2.3.1"  // Individual driver version
  },
  {
    "id": "shelly-library",
    "name": "Shelly Driver Library",
    "namespace": "ShellyUSA",
    "location": "https://raw.githubusercontent.com/user/repo/main/ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy",
    "required": true,
    "version": "3.1.0"  // Library version
  }
]
```

**Critical**: Include the shared library in every manifest that uses it.

### 4. Add release notes

```json
"releaseNotes": "v2.3.1 - Fixed power reporting accuracy. Improved websocket reconnection logic."
```

**Release notes guidelines**:

- Keep concise (1-2 sentences)
- Focus on user-visible changes
- Match the tone of `UpdateInfo` in `resources/version.json`
- Use plain text (HTML not supported in HPM)

### 5. Verify URLs

Ensure all `location` URLs:

- Point to the correct branch (typically `main` or a release tag)
- Use `raw.githubusercontent.com` (not `github.com`)
- Are publicly accessible
- Point to the correct file path

**URL format**:

```
https://raw.githubusercontent.com/[owner]/[repo]/[branch-or-tag]/[path-to-file]
```

---

## Compatibility requirements

### Minimum Hubitat version

```json
"minimumHEVersion": "2.2.0"
```

Set conservatively:

- Use `2.2.0` for most drivers (stable feature set)
- Use `2.3.0+` if using newer API features
- Document any specific firmware requirements in driver README

### Device compatibility

Document compatible Shelly device models in:

- Manifest `description` field
- Driver metadata
- Repository README

---

## Library dependencies

**CRITICAL**: Every manifest using `ShellyDriverLibrary` must include it.

```json
"drivers": [
  {
    "id": "shelly-library",
    "name": "Shelly Driver Library",
    "namespace": "ShellyUSA",
    "location": "https://raw.githubusercontent.com/.../ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy",
    "required": true,
    "version": "3.1.0"
  },
  // ... individual drivers that include the library
]
```

**Why**: Individual drivers use `#include ShellyUSA.ShellyUSA_Driver_Library` and won't work without the library installed.

---

## Validation before commit

### 1. JSON syntax validation

```bash
# Validate JSON
jq empty PackageManifests/*/manifest.json

# Pretty-print
jq . PackageManifests/ShellyWebhookDrivers/manifest.json
```

### 2. URL verification

```bash
# Test each driver location URL
curl -I "https://raw.githubusercontent.com/.../Driver.groovy"
# Should return 200 OK
```

### 3. Version consistency check

Ensure versions match between:

- `resources/version.json`
- Package manifest
- Driver metadata (in `.groovy` file)

### 4. Content review checklist

- [ ] All active drivers for this package are included
- [ ] Library is included if drivers use it
- [ ] All URLs are valid and accessible
- [ ] Version numbers match `resources/version.json`
- [ ] Date is current (ISO format)
- [ ] Release notes are user-friendly
- [ ] `minimumHEVersion` is accurate
- [ ] No legacy drivers are included
- [ ] JSON is valid and well-formatted

---

## Testing package installation

**Manifests cannot be fully validated without a Hubitat hub.**

If you cannot test installation:

1. Open a draft PR with `needs-hardware-test` label
2. Request a reviewer with access to a test hub
3. Provide testing instructions:
   - Which manifest to test
   - Expected drivers to be installed
   - Any configuration steps
   - How to verify installation succeeded

**Testing steps (requires Hubitat hub)**:

1. Open HPM on test hub
2. Choose "Install" → "From URL"
3. Enter manifest URL (raw GitHub URL)
4. Verify all expected drivers appear in installation list
5. Complete installation
6. Check `Drivers Code` for installed drivers
7. Create test device and verify driver works

---

## Common scenarios

### Scenario 1: Releasing a bug fix for one driver

```json
// Update package version
"version": "2.0.1",  // was 2.0.0
"dateReleased": "2026-02-04",

// Update specific driver version
"drivers": [
  {
    "id": "shellyplus1pm-driver",
    "version": "2.3.1",  // was 2.3.0
    // ... other fields unchanged
  },
  // Other drivers remain at their current versions
],

"releaseNotes": "v2.0.1 - Fixed power reporting in ShellyPlus1PM driver"
```

### Scenario 2: Library update affecting all drivers

```json
// Update package version
"version": "3.0.0",  // was 2.9.5
"dateReleased": "2026-02-04",

// Update library version
"drivers": [
  {
    "id": "shelly-library",
    "version": "3.1.0",  // was 3.0.5
    // ...
  },
  // Update all driver versions (they inherit library changes)
  {
    "id": "shellyplus1pm-driver",
    "version": "2.3.1",  // was 2.3.0
    // ...
  },
  {
    "id": "shellyplus2pm-driver",
    "version": "2.1.3",  // was 2.1.2
    // ...
  }
],

"releaseNotes": "v3.0.0 - Library updated to v3.1.0 with improved reliability. All drivers updated to use new library version."
```

### Scenario 3: Adding a new driver to existing package

```json
// Bump package minor version (new feature = new driver)
"version": "2.1.0",  // was 2.0.5
"dateReleased": "2026-02-04",

"drivers": [
  // Existing drivers...
  {
    "id": "shellynewmodel-driver",
    "name": "Shelly New Model",
    "namespace": "ShellyUSA",
    "location": "https://raw.githubusercontent.com/.../ShellyNewModel.groovy",
    "required": false,  // Optional if not needed by all users
    "version": "1.0.0"  // New driver starts at 1.0.0
  }
],

"releaseNotes": "v2.1.0 - Added support for Shelly New Model device"
```

---

## Integration with version.json

**Always update both files together**:

1. Update `resources/version.json` first (see `version.instructions.md`)
2. Update package manifest with matching version numbers
3. Commit both files in the same commit/PR

**Version sync example**:

```json
// resources/version.json
{
  "versions": {
    "Driver": {
      "ShellyPlus1PM": "2.3.1",
      "ShellyUSA_Driver_Library": "3.1.0"
    }
  }
}

// PackageManifests/.../manifest.json
{
  "drivers": [
    {
      "id": "shellyplus1pm-driver",
      "version": "2.3.1"  // Must match
    },
    {
      "id": "shelly-library",
      "version": "3.1.0"  // Must match
    }
  ]
}
```

---

## Summary checklist

- [ ] Am I updating a manifest for active drivers (not legacy)?
- [ ] Have I included the shared library if drivers depend on it?
- [ ] Do version numbers match `resources/version.json`?
- [ ] Is the release date current (ISO format)?
- [ ] Are all driver `location` URLs valid and accessible?
- [ ] Is `minimumHEVersion` accurate?
- [ ] Have I added user-friendly release notes?
- [ ] Is the JSON valid (tested with `jq`)?
- [ ] Have I requested hardware testing if I can't test HPM installation?
- [ ] Have I committed `version.json` and manifest together?
