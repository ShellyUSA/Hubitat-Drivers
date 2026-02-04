---
applyTo: "**/*.md"
---

# Markdown & docs instructions

**Purpose**: Documentation files (`.md`) provide user-facing instructions, developer guidance, and repository information.

---

## IMPORTANT: Legacy vs. Active code

**When documenting code, focus on active drivers only**:

- `WebhookWebsocket/` folder — primary webhook/websocket drivers
- `ComponentDrivers/` folder — reusable components
- `Bluetooth/` folder — Bluetooth device drivers
- `ShellyDriverLibrary/` — shared library code

**Legacy code (minimal documentation needed)**:

- `PLUS/` folder — older Plus drivers (superseded by WebhookWebsocket)
- Root-level `Shelly*.groovy` files — original drivers (no longer actively maintained)
- `contribs/` folder — community contributions (not officially supported)

**Legacy documentation rule**: If mentioning legacy code, clearly label it as "Legacy (no longer actively maintained)" and direct users to the active alternatives.

---

## Primary documentation files

### `README.md` (repository root)

**The canonical user-facing installation and usage guide.**

**Contents**:

- Overview of Shelly driver support
- Installation instructions (HPM and manual)
- Supported devices (active drivers only)
- Basic configuration and usage
- Troubleshooting common issues
- Links to detailed docs

**Update README.md when**:

- Adding support for new device models
- Changing installation or configuration steps
- Modifying user-facing preferences or commands
- Changing HPM package structure
- Adding new requirements (firmware versions, hub versions, etc.)

### `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`

**AI agent guidance files** (already attached to this conversation).

Update when:

- Repository structure changes significantly
- Coding workflows or validation steps change
- New constraints or requirements are added

### `.github/instructions/*.md`

**Detailed instructions for AI agents** (this file is one of them).

Update when:

- Coding standards change
- New validation requirements are added
- Architecture or file organization changes

---

## Documentation style guidelines

### Writing for users

- Use clear, concise language
- Avoid technical jargon when possible
- Provide step-by-step instructions
- Include screenshots or examples where helpful
- Test instructions on a fresh setup when possible

### Writing for developers/agents

- Be precise and specific
- Use technical terminology accurately
- Provide code examples
- Explain _why_, not just _how_
- Include validation steps

### Markdown formatting

- Use proper heading hierarchy (`#`, `##`, `###`)
- Use code blocks with language tags: ` ```groovy `, ` ```bash `, ` ```json `
- Use inline code for: file paths, variable names, command names
- Use bold for: emphasis, important warnings
- Use lists for: steps, options, features
- Use links for: cross-references, external resources

---

## Security and privacy

**NEVER commit sensitive information in documentation**:

- Real IP addresses → Use `HUBITAT-IP-ADDRESS` or `192.168.1.XXX`
- API tokens → Use `API_TOKEN_PLACEHOLDER` or `YOUR_TOKEN_HERE`
- Passwords → Use `YOUR_PASSWORD` or `PASSWORD_PLACEHOLDER`
- Hub IDs → Use `HUB_ID_PLACEHOLDER`
- Personal device names → Use generic examples like `Living Room Switch`

**Example**:

```markdown
# ❌ WRONG

Navigate to: http://192.168.1.50/apps/api/12345/drivers
Token: abc123def456ghi789

# ✅ CORRECT

Navigate to: http://HUBITAT-IP-ADDRESS/apps/api/YOUR_APP_ID/drivers
Token: YOUR_API_TOKEN
```

---

## Architecture documentation

**Key concept for users and developers**: The bulk of driver logic lives in `ShellyDriverLibrary/`.

When documenting the architecture:

```markdown
## Architecture

This repository uses a shared library architecture:

- **ShellyDriverLibrary/** contains the core driver implementation
  - HTTP/WebSocket communication
  - Child device management
  - Standard commands (on, off, refresh, setLevel, etc.)
  - Logging and preference handling

- **Individual drivers** (WebhookWebsocket/, ComponentDrivers/, Bluetooth/) are minimal:
  - Define device metadata (capabilities, attributes)
  - Include the shared library: `#include ShellyUSA.ShellyUSA_Driver_Library`
  - Override functions only when device-specific behavior is needed

This design:

- Reduces code duplication
- Ensures consistent behavior across drivers
- Makes bug fixes and features available to all drivers simultaneously
```

---

## Updating README.md

### Supported devices section

**Keep this list current with active drivers.**

Format:

```markdown
## Supported Devices

### WebSocket/Webhook Drivers (Actively Maintained)

- Shelly Plus 1
- Shelly Plus 1PM
- Shelly Plus 2PM
- Shelly Plus Dimmer
- [Full list...]

### Component Drivers

- Shelly Switch Component
- Shelly Dimmer Component
- [Full list...]

### Bluetooth Drivers

- Shelly BLU Button1
- Shelly BLU Door/Window
- [Full list...]

### Legacy Drivers (No Longer Actively Maintained)

Older drivers in `PLUS/` and root directory are still available but not recommended for new installations. Use the WebSocket/Webhook drivers above for current functionality.
```

### Installation section

**Keep HPM and manual installation instructions accurate.**

Test installation steps:

1. Follow your own instructions on a test hub
2. Note any missing steps or unclear instructions
3. Update documentation
4. Include version requirements if applicable

Format:

```markdown
## Installation

### Via Hubitat Package Manager (Recommended)

1. Open **Hubitat Package Manager** on your hub
2. Select **Install** → **Search by Keywords**
3. Search for "Shelly"
4. Select the appropriate package:
   - "Shelly WebSocket Drivers" — for Plus/Gen2 devices
   - "Shelly Bluetooth Drivers" — for BLU devices
5. Click **Install**
6. Wait for installation to complete

### Manual Installation

1. Navigate to **Drivers Code** in your Hubitat hub
2. Click **New Driver**
3. Copy the driver code from:
   - Library: `ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy`
   - Driver: `WebhookWebsocket/ShellyPlus1PM.groovy` (or your desired driver)
4. Paste into the editor and click **Save**
5. **Important**: Install the library first, then individual drivers
6. Repeat for each driver you need
```

### Configuration section

Document common preferences and their purpose:

```markdown
## Configuration

After installing drivers, configure each device:

1. Create a new device: **Devices** → **Add Virtual Device**
2. Set **Type** to your Shelly driver
3. Click **Save Device**
4. Configure preferences:
   - **IP Address**: Your Shelly device's IP (e.g., `192.168.1.100`)
   - **Device Type**: Select your specific model
   - **Refresh Interval**: How often to poll device (default: 60 seconds)
   - **Enable Logging**: Turn on for troubleshooting, off for normal use
5. Click **Save Preferences**
6. Click **Refresh** to fetch current state
```

---

## Changelog and release notes

Changelog information lives in two places:

1. **`resources/version.json`** — structured version and UpdateInfo (see `version.instructions.md`)
2. **GitHub Releases** — user-facing release notes (optional, but recommended for major releases)

When creating a GitHub release:

- Match version number to `resources/version.json`
- Summarize changes from `UpdateInfo` entries
- Group changes by category: New Features, Bug Fixes, Breaking Changes
- Include upgrade instructions for breaking changes
- Link to relevant PRs or issues

---

## Cross-referencing

**Link to related documentation**:

```markdown
For detailed coding guidelines, see:

- [Agent guidance](AGENTS.md)
- [Groovy coding instructions](.github/instructions/groovy.instructions.md)
- [Library modification guide](.github/instructions/library.instructions.md)

For version management:

- [Version tracking](.github/instructions/version.instructions.md)
- [Package manifests](.github/instructions/packagemanifests.instructions.md)
```

**Use relative links** for files within the repository.

---

## Troubleshooting documentation

Include common issues and solutions:

```markdown
## Troubleshooting

### Device not responding

1. Verify the device IP address is correct in preferences
2. Ensure the device is powered on and connected to your network
3. Check Hubitat hub can reach the device: **Logs** → look for connection errors
4. Try clicking **Refresh** to re-establish connection

### WebSocket connection issues

1. Enable debug logging in driver preferences
2. Check logs for "WebSocket connected" message
3. Verify device firmware is up-to-date (Shelly app)
4. Try disabling and re-enabling WebSocket in device preferences

### Library not found error

1. Ensure `ShellyUSA_Driver_Library` is installed first
2. Check the library name matches exactly in driver code
3. Re-save the driver after installing the library
```

---

## Documentation validation

### Before committing docs:

1. **Spell check**: Use VS Code spell checker or similar
2. **Link check**: Verify all internal links work
3. **Code block syntax**: Ensure all code blocks have language tags
4. **Markdown validation**: Use a linter (e.g., `markdownlint`)
5. **Test instructions**: Follow your own steps to ensure accuracy
6. **Security scan**: Grep for accidentally committed secrets:
   ```bash
   grep -r "192\.168\." --include="*.md"
   grep -r "password" --include="*.md" -i
   grep -r "token" --include="*.md" -i
   ```

### Markdown linting (optional)

```bash
# Install markdownlint-cli
npm install -g markdownlint-cli

# Lint all markdown files
markdownlint '**/*.md' --ignore node_modules
```

---

## Integration with code changes

**When making code changes, update docs if**:

- Adding/removing user-facing features
- Changing installation steps
- Modifying configuration preferences
- Adding new device support
- Changing API or command structure
- Introducing breaking changes

**Include in PR**:

- Updated README.md (if user-facing impact)
- Updated `resources/version.json` (see `version.instructions.md`)
- Testing steps in PR description
- Note doc changes in PR description

---

## Summary checklist

- [ ] Have I focused documentation on active code (not legacy)?
- [ ] Have I clearly labeled any legacy references?
- [ ] Are all IP addresses and tokens placeholders?
- [ ] Are installation steps tested and accurate?
- [ ] Have I used proper Markdown formatting?
- [ ] Are code blocks tagged with language?
- [ ] Do all internal links work?
- [ ] Have I updated README.md for user-facing changes?
- [ ] Have I updated version.json if releasing changes?
- [ ] Have I run a security scan for accidental secrets?
- [ ] Is the documentation clear and concise?
