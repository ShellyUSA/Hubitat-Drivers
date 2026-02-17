# CLAUDE.md — Short agent guidance for Claude-style agents

Purpose: a concise set of rules for Claude-style agents working on this repo.

- Keep changes small, backward-compatible, and well-documented.
- Do not commit secrets or real device IPs.
- When changes require hardware testing, open a draft PR and add `needs-hardware-test` and `ai-generated` labels.
- Update `resources/version.json` and `UpdateInfo` for driver releases.

If you can't validate automatically, create a draft PR and request a human with access to a Hubitat test hub.

## Active Codebase Scope - CRITICAL

All future development work is scoped to:

- **App:** `Apps/ShellyDeviceManager.groovy`
- **Drivers:** `UniversalDrivers/` folder (all `.groovy` files and `component_driver.json`)
- **Scripts:** `Scripts/` folder (JavaScript files for Hubitat dashboard tiles)

**DO NOT modify or reference** `ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy`. This is **legacy code** that is no longer maintained. Do not use it as a reference for new work, do not update it, and do not port patterns from it into the active codebase.

## Gen 1 Shelly `report_url` — DO NOT USE

**NEVER create, enable, or reference `report_url` action URLs on Gen 1 Shelly devices.** The `report_url` mechanism appends sensor data as URL query parameters (e.g., `?hum=55&temp=22.5&id=shellyht-AABBCC`). Hubitat silently drops URL parameters from incoming HTTP requests on port 39501, so the sensor data never reaches the app. This makes `report_url` completely unusable.

This applies to ALL Gen 1 sensor types that support `report_url`: H&T (`SHHT-1`), Flood (`SHWT-1`), Door/Window (`SHDW-1`, `SHDW-2`), Button (`SHBTN-1`, `SHBTN-2`), and Motion (`SHMOS-01`, `SHMOS-02`).

Use other action URLs (e.g., `flood_detected_url`, `open_url`, `motion_on`) that send simple HTTP requests without query parameters. For periodic sensor data, use polling via `GET /status`.

## Hubitat App UI Pages

- Use `section()` without a title string for most sections. The `input` `title:` already labels the control — adding a section title like `section("Select Device")` creates redundant, cluttered text.
- Only use `section("Title")` when grouping multiple related controls that genuinely benefit from a heading (e.g., `section("Options", hideable: true)` or `section("Logging", hideable: true)`).
- The page-level `title:` in `dynamicPage(...)` is sufficient for identifying the page. Don't repeat it in section headers.

## HTML Tables and Interactive Buttons in App Pages

Hubitat app pages use `paragraph` to render raw HTML. Tables use Material Design Lite (`mdl-data-table`) which is built into the Hubitat UI.

### Table Architecture

A table consists of three parts returned as a single HTML string via `paragraph`:

1. **CSS styles** (`<style>` block) -- define table appearance
2. **Table markup** (`<table>` with `<thead>` and row `<tr>` elements) -- the data
3. **Optional JavaScript** (`<script>` block) -- for popup interactions or AJAX refresh

Separate these into distinct functions for maintainability:

```groovy
// In dynamicPage section:
paragraph displayTable()

// Orchestrator: combines CSS + JS + table markup
String displayTable() {
  // Process any pending state changes from button clicks first
  processPendingActions()
  String tableMarkup = renderTableMarkup()
  return loadTableCSS() + loadTableScript() + "<div id='table-wrapper'>${tableMarkup}</div>"
}

// Table markup only (also used by AJAX refresh endpoint)
String renderTableMarkup() {
  String str = "<div style='overflow-x:auto'><table class='mdl-data-table'>"
  str += "<thead><tr><th>Device</th><th>Status</th><th>Action</th></tr></thead>"
  devices.each { dev -> str += buildDeviceRow(dev) }
  str += "</table></div>"
  return str
}
```

### CSS Styling

Use `mdl-data-table` as the base class. Override default styles and add custom classes:

```groovy
String loadTableCSS() {
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

    /* Section separators */
    th.section-border, td.section-border { border-right: 1px solid gray !important; }
    td.group-border-bottom { border-bottom: 1px solid gray !important; }

    /* Device name links */
    .device-link a { color: #2196F3; text-decoration: none; font-weight: 500; }
    .device-link a:hover { text-decoration: underline; }
  </style>"""
}
```

### Table Markup and Row Building

**Device links** -- link to the Hubitat device page:
```groovy
String devLink = "<a href='/device/edit/${dev.id}' target='_blank' title='Open ${dev}'>${dev}</a>"
```

**Rowspan** -- merge cells vertically when a device has multiple schedule rows:
```groovy
int scheduleCount = deviceSchedules.size()
String deviceCell = "<td class='device-link' rowspan='${scheduleCount}'>${devLink}</td>"
// Only include deviceCell in the first <tr> for that device
```

**Conditional cell styling:**
```groovy
String statusColor = dev.currentSwitch == "on" ? "#4CAF50" : "#F44336"
str += "<td style='color:${statusColor}'>${dev.currentSwitch}</td>"
```

**Tooltips** -- add `title` attribute to any element:
```groovy
str += "<td title='Click to toggle device state'>${stateButton}</td>"
```

**Cell class builder** -- dynamically assemble CSS classes per cell:
```groovy
def buildCellAttr = { boolean highlight, boolean rightBorder, boolean bottomBorder ->
  List<String> classes = []
  if (highlight) classes << "group-highlight"
  if (rightBorder) classes << "section-border"
  if (bottomBorder) classes << "group-border-bottom"
  return classes ? "class='${classes.join(' ')}'" : ""
}
// Usage: str += "<td ${buildCellAttr(true, false, isLastRow)}>...</td>"
```

### Icons (Iconify)

Hubitat pages can load [Iconify](https://iconify.design/) for icons. Load the script once in your CSS/JS block:

```groovy
String loadTableScript() {
  return "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
}
```

Use icons inline in table cells:
```groovy
// Checkbox states
String checked = "<iconify-icon icon='material-symbols:check-box'></iconify-icon>"
String unchecked = "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>"

// Status icons
String onIcon = "<iconify-icon icon='material-symbols:lightbulb'></iconify-icon>"
String offIcon = "<iconify-icon icon='material-symbols:lightbulb-outline'></iconify-icon>"

// Action icons
String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded'></iconify-icon>"
String resetIcon = "<iconify-icon icon='bx:reset'></iconify-icon>"

// Use inside buttonLink:
buttonLink("toggle|${dev.id}", dev.currentSwitch == "on" ? checked : unchecked, dev.currentSwitch == "on" ? "green" : "black", "23px")
```

### Interactive Buttons (buttonLink)

Use `buttonLink()` to create clickable elements that trigger `appButtonHandler()`:

```groovy
String buttonLink(String btnName, String linkText, String color = "#1A77C9", String font = "15px") {
  "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div>" +
  "<div><div class='submitOnChange' onclick='buttonClick(this)' " +
  "style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
  "<input type='hidden' name='settings[${btnName}]' value=''>"
}
```

**How it works:**
1. Renders a clickable `<div>` with hidden form inputs
2. `submitOnChange` class + `buttonClick(this)` submits the page form on click
3. Hubitat calls `appButtonHandler(btn)` where `btn` is the `btnName`
4. The page re-renders, calling `displayTable()` again

### appButtonHandler and Two-Phase Commit Pattern

Encode context into button names using delimiters (e.g., `|`), then parse in the handler. Use a **two-phase commit**: `appButtonHandler` stores intent in `state`, then `displayTable()` applies the change on the next render.

```groovy
// Phase 1: Store intent
void appButtonHandler(String btn) {
  if (btn == "refresh") { /* direct action */ }
  else if (btn.startsWith("toggle|")) state.pendingToggle = btn.minus("toggle|")
  else if (btn.startsWith("remove|")) state.pendingRemove = btn.minus("remove|")
  else if (btn.startsWith("checked|")) state.pendingCheck = btn.minus("checked|")
  else if (btn.startsWith("unchecked|")) state.pendingUncheck = btn.minus("unchecked|")
}

// Phase 2: Apply changes (called at start of displayTable)
void processPendingActions() {
  if (state.pendingToggle) {
    String deviceId = state.pendingToggle
    state.devices[deviceId].enabled = !state.devices[deviceId].enabled
    state.remove("pendingToggle")
  }
  if (state.pendingRemove) {
    state.devices.remove(state.pendingRemove)
    state.remove("pendingRemove")
  }
  // ... etc
}
```

**Why two-phase?** `appButtonHandler` runs before the page renders. Storing intent first, then applying during `displayTable()`, ensures the table always reflects the latest state.

### AJAX Table Refresh (No Full Page Reload)

For popup-based interactions (time pickers, text inputs), use OAuth API endpoints + JavaScript to update the table without a Hubitat page re-render:

**1. Enable OAuth and create API endpoints:**
```groovy
// In definition():
definition(name: "My App", ..., oauth: true)

// In preferences:
mappings { path("/getTable") { action: [GET: "getTableEndpoint"] } }

// Endpoint returns table markup only (no CSS/JS wrapper):
def getTableEndpoint() {
  render contentType: "text/html", data: renderTableMarkup()
}
```

**2. JavaScript refresh function:**
```groovy
String loadTableScript() {
  return """<script>
    function refreshTable() {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', '/apps/api/${app.id}/getTable?access_token=${state.accessToken}&_=' + Date.now(), true);
      xhr.onreadystatechange = function() {
        if (xhr.readyState === 4 && xhr.status === 200) {
          var wrapper = document.getElementById('table-wrapper');
          if (wrapper) {
            wrapper.innerHTML = xhr.responseText;
            if (window.Iconify && typeof window.Iconify.scan === 'function') {
              window.Iconify.scan(wrapper);
            }
          }
        }
      };
      xhr.send();
    }
  </script>"""
}
```

**3. Popup-triggering buttonLink variant:**
```groovy
// For buttons that open a JS popup instead of submitting the form:
String popupButtonLink(String action, String deviceId, String linkText, String color = "#2196F3") {
  """<span role="button" onclick='event.preventDefault(); ${action}Popup("${deviceId}"); return false;' """ +
  """style='color:${color};cursor:pointer;font-weight:500'>$linkText</span>"""
}
```

### Programmatic Page Submit

Force a page re-submit from within a `dynamicPage` (e.g., after state changes via `submitOnChange` input):
```groovy
paragraph "<script>{changeSubmit(this)}</script>"
```

### Complete Minimal Example

```groovy
def mainPage() {
  dynamicPage(name: "mainPage", title: "Device Monitor", install: true, uninstall: true) {
    section {
      input "devices", "capability.switch", title: "Select Devices", multiple: true, submitOnChange: true
      if (devices) { paragraph displayTable() }
    }
  }
}

String displayTable() {
  return loadTableCSS() + renderTableMarkup()
}

String loadTableCSS() {
  return """<style>
    .mdl-data-table { width:100%; border-collapse:collapse; border:1px solid #E0E0E0; }
    .mdl-data-table th { padding:8px; text-align:center; border-bottom:2px solid #E0E0E0; }
    .mdl-data-table td { padding:6px 4px; text-align:center; border-bottom:1px solid #EEE; }
    .mdl-data-table tbody tr:hover { background-color:inherit !important; }
  </style>"""
}

String renderTableMarkup() {
  String str = "<div style='overflow-x:auto'><table class='mdl-data-table'>"
  str += "<thead><tr><th>Device</th><th>State</th><th>Action</th></tr></thead>"
  devices.sort { it.displayName.toLowerCase() }.each { dev ->
    String link = "<a href='/device/edit/${dev.id}' target='_blank'>${dev}</a>"
    String color = dev.currentSwitch == "on" ? "#4CAF50" : "#F44336"
    String action = buttonLink("reset|${dev.id}", "Reset", "purple")
    str += "<tr><td>${link}</td><td style='color:${color}'>${dev.currentSwitch}</td><td>${action}</td></tr>"
  }
  str += "</table></div>"
  return str
}

String buttonLink(String btnName, String linkText, String color = "#1A77C9", String font = "15px") {
  "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div>" +
  "<div><div class='submitOnChange' onclick='buttonClick(this)' style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
  "<input type='hidden' name='settings[${btnName}]' value=''>"
}

void appButtonHandler(String btn) {
  if (btn.startsWith("reset|")) {
    String deviceId = btn.minus("reset|")
    // perform reset action
  }
}
```

## Code Quality and Type Safety

### @CompileStatic Usage - CRITICAL

**ALWAYS maximize use of `@CompileStatic` annotation** for compile-time type checking and performance.

**Rules:**
1. **NEVER remove `@CompileStatic`** from existing functions during edits
2. **ALWAYS add `@CompileStatic`** to new functions unless they access dynamic properties
3. When editing a function that lacks `@CompileStatic`, add it if possible
4. Use helper/wrapper functions for dynamic property access

**Dynamic Properties and Methods That Cannot Be Used in @CompileStatic Functions:**

**Dynamic Properties:**
- `app` - The Hubitat app instance (in app context)
- `device` - The Hubitat device instance (in device driver context)
- `parent` - Parent app/device reference
- `settings` - App/device settings (use `this.settings` or wrap in helper)
- `state` - App/device state (use `this.state` or wrap in helper)

**Hubitat Built-in Methods (require helpers):**
- `httpGet()`, `httpPost()` - HTTP operations
- `schedule()`, `unschedule()` - Scheduling methods
- `getLocation()` - Location access
- `getChildDevices()`, `getChildDevice()` - May work but wrap if errors occur
- `sendEvent()` - For devices and SSR-triggering app events (client-side-only app events use `app.sendEvent()` in helper)

**Device/Child Wrapper Methods (require helpers):**
- `dev.hasCapability()`, `dev.hasAttribute()`
- `dev.updateSetting()`, `dev.getDataValue()`, `dev.updateDataValue()`
- `child.sendEvent()`, `child.updateDataValue()`, `child.hasAttribute()`
- Any method called on DeviceWrapper or ChildDeviceWrapper objects

**Pattern: Use Non-Static Helper Functions**

When you need to access dynamic properties in a `@CompileStatic` function:

```groovy
// ✗ WRONG - This will fail compilation
@CompileStatic
void someFunction() {
  app.sendEvent([name: 'foo', value: 'bar'])  // ERROR: app is undeclared
}

// ✓ CORRECT - Use a helper function
private void sendAppEventHelper(Map properties) {
  app.sendEvent(properties)  // No @CompileStatic here
}

@CompileStatic
void someFunction() {
  sendAppEventHelper([name: 'foo', value: 'bar'])  // Works!
}
```

**Pattern: Use Wrappers for Device/Child Operations**

```groovy
// ✗ WRONG - Direct device method calls in @CompileStatic
@CompileStatic
void setDeviceSetting(String name, String value, DeviceWrapper dev) {
  dev.updateSetting(name, value)  // May fail in @CompileStatic
}

// ✓ CORRECT - Use non-static wrapper
private void updateDeviceSettingHelper(DeviceWrapper dev, String name, String value) {
  dev.updateSetting(name, value)  // No @CompileStatic
}

@CompileStatic
void setDeviceSetting(String name, String value, DeviceWrapper dev) {
  updateDeviceSettingHelper(dev, name, value)  // Works!
}
```

**When Editing Functions:**
1. Check if the function has `@CompileStatic` - if so, PRESERVE IT
2. Check if your edits introduce any of the above dynamic properties/methods
3. If yes, extract those calls to a helper function (see existing helpers)
4. Never remove `@CompileStatic` to "fix" compilation - use helpers instead
5. **Exception**: Omit `@CompileStatic` from functions that use:
   - `httpGet`/`httpPost` with closures (inherently dynamic)
   - `state` property extensively (dynamic map access)
   - Helper return values where you need to call methods on them (Object return type issue)

**Existing Helpers Available:**
```groovy
// App context
getAppLabelHelper(), getAppIdHelper(), sendAppEventHelper()

// Device operations
deviceUpdateSettingHelper(), deviceGetDataValueHelper(),
deviceUpdateDataValueHelper(), deviceHasCapabilityHelper(),
deviceHasAttributeHelper()

// Child device operations
childSendEventHelper(), childUpdateDataValueHelper(),
childHasAttributeHelper(), childGetDeviceDataValueHelper()

// Hubitat built-ins
scheduleHelper(), unscheduleHelper(), getLocationHelper(),
getParentHelper(), httpGetHelper(), httpPostHelper()
```

Use these existing helpers when possible instead of creating new ones.

**When NOT to Use @CompileStatic:**

❌ Functions using `state` extensively (dynamic map)
❌ Functions with `httpGet`/`httpPost` and closures
❌ Functions calling methods on helper return values (Object type)
❌ Functions with complex dynamic property access

**Examples:**
```groovy
// ✗ DON'T use @CompileStatic - uses state extensively
private void initializeDriverTracking() {
  if(!state.autoDrivers) {
    state.autoDrivers = [:]
  }
}

// ✗ DON'T use @CompileStatic - calls method on Object return
Boolean isCelciusScale() {
  return getLocationHelper().temperatureScale == 'C'
}

// ✓ DO use @CompileStatic - simple wrapper with no dynamic access
@CompileStatic
void scheduleTask(String sched, String taskName) {
  scheduleHelper(sched, taskName)
}
```

**Benefits of @CompileStatic:**
- Compile-time type checking catches errors early
- Better IDE autocomplete and refactoring
- Improved runtime performance
- Clearer code contracts with explicit types

## Dynamic Page Updates (SSR) - CRITICAL

Hubitat firmware 2.3.7.114+ supports dynamic HTML updates on app pages without full re-renders.

**NEVER use `refreshInterval` or any form of page refresh/reload.** Always use dynamic updates (client-side or SSR) to update page content. Page refreshes cause poor UX (flickering, scroll position loss, input focus loss) and unnecessary server load.

There are two methods:

### Method 1: Client-Side Updates (Simple Value Display)

Add a CSS class to an HTML element to automatically update its text content when an event fires:

```groovy
// Device attribute: updates when device attribute changes
paragraph "<span class='device-current-state-${deviceId}-temperature'>Loading...</span>"

// App event: updates when app.sendEvent fires
paragraph "<span class='app-state-${app.id}-discoveryTimer'>Waiting...</span>"

// Hub event:
paragraph "<span class='hub-state-${eventName}'>...</span>"

// Location event:
paragraph "<span class='location-state-${eventName}'>...</span>"
```

The browser replaces the element's text content with the event value. Good for simple text like timers, counters, and sensor readings.

**To fire client-side app events:**
```groovy
app.sendEvent(name: 'myEventName', value: 'someTextValue')
```

### Method 2: Server-Side Rendering (SSR) (Complex HTML)

Use the `ssr-` prefix to trigger a server-side callback that returns replacement HTML:

```groovy
// In the dynamicPage section:
paragraph "<span class='ssr-app-state-${app.id}-myEvent'>${initialHtml}</span>"

// SSR handler function (must be named exactly this):
String processServerSideRender(Map event) {
    String elementId = event.elementId ?: ''
    String eventName = event.name ?: ''

    if (eventName == 'myEvent') {
        return renderMySection()
    }
    return ''
}
```

**CRITICAL: `sendEvent()` vs `app.sendEvent()` for SSR:**
- `sendEvent()` (bare) — triggers **both** `ssr-app-state-` (SSR callback) **and** `app-state-` (client-side)
- `app.sendEvent()` — triggers **only** `app-state-` (client-side), does **NOT** trigger SSR callbacks

```groovy
// ✓ CORRECT — triggers SSR callback via processServerSideRender
sendEvent(name: 'myEvent', value: 'someValue')

// ✗ WRONG for SSR — only triggers client-side app-state- updates
app.sendEvent(name: 'myEvent', value: 'someValue')
```

**Key points:**
- The `processServerSideRender(Map event)` function must exist in the app
- `event` contains standard event fields plus `elementId`
- Return HTML string to replace element content (uses innerHTML)
- Initial content is NOT auto-populated — set it explicitly in the page
- Use `id` attribute to distinguish multiple SSR elements on the same page
- Works with device events (`ssr-device-current-state-`) and app events (`ssr-app-state-`)

## Clean As You Code - CRITICAL

**ALWAYS improve code quality while making changes.** Never leave code worse than you found it.

### When Editing Any Function:

**1. Check for Missing @CompileStatic**
- If the function lacks `@CompileStatic` and doesn't use dynamic properties, ADD IT
- Extract dynamic calls to helpers if needed to enable `@CompileStatic`

**2. Check for Missing JavaDoc**
- If the function lacks documentation, ADD IT
- Follow JavaDoc best practices (not the old generateHubitatDriver example)
- Include `@param`, `@return`, and clear descriptions

**3. Refactor Poor Code Patterns**
- Long functions (>50 lines): Extract logical sections to helper methods
- Duplicate code: Extract to shared functions
- Magic numbers: Replace with named constants or settings
- Complex conditionals: Simplify or extract to well-named predicates
- Unclear variable names: Rename to be descriptive

**4. Fix Code Organization**
- Function in wrong section: MOVE IT to the appropriate section
- Related functions scattered: GROUP THEM together
- Poor section organization: REORGANIZE and use clear section headers

**Examples of Organization Sections:**
```groovy
// ═══════════════════════════════════════════════════════════════
// ║  Discovery & Device Management                              ║
// ╚═══════════════════════════════════════════════════════════════╝

// ═══════════════════════════════════════════════════════════════
// ║  Child Device Creation                                       ║
// ╚═══════════════════════════════════════════════════════════════╝

// ═══════════════════════════════════════════════════════════════
// ║  Driver Management & Version Tracking                        ║
// ╚═══════════════════════════════════════════════════════════════╝

// ═══════════════════════════════════════════════════════════════
// ║  Helper Functions                                            ║
// ╚═══════════════════════════════════════════════════════════════╝
```

**5. Consistency Checks**
- Inconsistent naming: Make it consistent
- Inconsistent formatting: Fix indentation and spacing
- Inconsistent error handling: Standardize approach

### What NOT to Do:

❌ Don't refactor unrelated code in the same edit
❌ Don't over-engineer simple functions
❌ Don't add unnecessary abstractions
❌ Don't change working logic unless fixing a bug
❌ Don't reorganize the entire file when editing one function

### What TO Do:

✅ Fix issues in the function you're editing
✅ Add @CompileStatic if missing
✅ Add JavaDoc if missing
✅ Move the function if it's in the wrong section
✅ Extract complex logic to helpers with clear names
✅ Improve variable/function names for clarity
✅ Remove commented-out code (unless it's explanatory)
✅ Fix obvious bugs or issues you notice

### Examples:

**Before:**
```groovy
// No docs, no @CompileStatic, unclear name, magic number
void doThing(x) {
  if(x > 5) { log.debug "too big" }
}
```

**After (cleaning while editing):**
```groovy
/**
 * Validates that a device ID is within acceptable range.
 *
 * @param deviceId The device ID to validate
 * @return true if valid, false otherwise
 */
@CompileStatic
Boolean isValidDeviceId(Integer deviceId) {
  Integer MAX_DEVICE_ID = 5
  if(deviceId > MAX_DEVICE_ID) {
    logDebug("Device ID ${deviceId} exceeds maximum ${MAX_DEVICE_ID}")
    return false
  }
  return true
}
```

### Function Movement Example:

If you're editing a driver management function and notice it's located in the middle of discovery functions:

1. Move it to the "Driver Management & Version Tracking" section
2. Keep related functions together
3. Update any section comments if needed

**The goal: Leave the codebase cleaner than you found it, one function at a time.**
