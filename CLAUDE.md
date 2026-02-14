# CLAUDE.md — Short agent guidance for Claude-style agents

Purpose: a concise set of rules for Claude-style agents working on this repo.

- Keep changes small, backward-compatible, and well-documented.
- Do not commit secrets or real device IPs.
- When changes require hardware testing, open a draft PR and add `needs-hardware-test` and `ai-generated` labels.
- Update `resources/version.json` and `UpdateInfo` for driver releases.

If you can't validate automatically, create a draft PR and request a human with access to a Hubitat test hub.

## Hubitat App UI Pages

- Use `section()` without a title string for most sections. The `input` `title:` already labels the control — adding a section title like `section("Select Device")` creates redundant, cluttered text.
- Only use `section("Title")` when grouping multiple related controls that genuinely benefit from a heading (e.g., `section("Options", hideable: true)` or `section("Logging", hideable: true)`).
- The page-level `title:` in `dynamicPage(...)` is sufficient for identifying the page. Don't repeat it in section headers.

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
- `sendEvent()` - For devices (apps use `app.sendEvent()` in helper)

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

## Dynamic Page Updates (SSR)

Hubitat firmware 2.3.7.114+ supports dynamic HTML updates on app pages without full re-renders. There are two methods:

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

The browser replaces the element's text content with the event value. Good for simple text like timers and sensor readings.

### Method 2: Server-Side Rendering (SSR) (Complex HTML)

Use the `ssr-` prefix to trigger a server-side callback that returns replacement HTML:

```groovy
// In the dynamicPage section:
paragraph "<span class='ssr-device-current-state-${deviceId}-temperature' id='my-element'>${initialHtml}</span>"

// SSR handler function (must be named exactly this):
String processServerSideRender(Map event) {
    String elementId = event.elementId ?: ''
    String eventName = event.name ?: ''
    Integer deviceId = event.deviceId as Integer

    // Return HTML to replace the element's content
    if (elementId?.contains('my-element')) {
        return renderMySection()
    }
    return ''
}
```

**Key points:**
- The `processServerSideRender(Map event)` function must exist in the app
- `event` contains standard event fields plus `elementId`
- Return HTML string to replace element content
- Initial content is NOT auto-populated — set it explicitly in the page
- Use sparingly — each tagged element triggers a server call on events
- Use `id` attribute to distinguish multiple SSR elements
- Works with both device events and app events (`ssr-app-state-${app.id}-eventName`)

**To trigger SSR updates from app code:**
```groovy
app.sendEvent(name: 'myEventName', value: 'someValue')
```

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
