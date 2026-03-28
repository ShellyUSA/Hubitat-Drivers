# Device Settings Exposure TODO

Last reviewed: 2026-03-28

This backlog focuses on the active autoconf stack:

- `Apps/ShellyDeviceManager.groovy`
- `UniversalDrivers/`
- `UniversalDrivers/UniversalComponentDrivers/`

Scope rules:

- Focus on device settings that materially change behavior, safety, battery life, or automation usefulness.
- Treat a setting as missing when it is not exposed directly in the active Hubitat drivers, or when it is exposed only on some driver shapes and missing on equally common active driver shapes.
- Exclude legacy root drivers, `PLUS/`, Wi-Fi/cloud/admin settings, and one-off vendor diagnostics.

What is already covered well enough to keep out of this TODO:

- Basic switch power-on state and auto-on/auto-off timers
- Most Gen 2/3 dimmer settings in `ShellySingleDimmer.groovy` and `ShellySingleDimmerPM.groovy`
- PLUGS_UI LED color/night mode via `ShellyPlugsUiRGBComponent.groovy`
- WallDimmer `WD_UI` LED settings
- Hubitat-side aggregation choices and PM reporting interval preferences

## Very High Value

### 1. Extended switch configuration parity for standalone switch drivers

Current state:

- `UniversalDrivers/ShellySingleSwitch.groovy` exposes only `defaultState`, `autoOffTime`, and `autoOnTime`.
- `UniversalDrivers/ShellySingleSwitchPM.groovy` also stops at those relay basics, apart from the optional PLUGS_UI child toggle.
- The richer Gen 2/3 switch config surface is already exposed on child drivers in `UniversalDrivers/UniversalComponentDrivers/ShellySwitchComponent.groovy` and `ShellySwitchComponentPM.groovy`.
- The app already supports relay/apply/sync for the extended switch fields in `componentUpdateSwitchSettings()`, `applyGen2SwitchSettings()`, and `syncGen2ConfigToPreferences()`.

Missing settings to expose consistently:

- `in_mode`
- `in_locked`
- PM-capable devices: `power_limit`, `voltage_limit`, `undervoltage_limit`, `current_limit`
- `autorecover_voltage_errors`
- `reverse`

Why this is very high value:

- Applies to the most common Shelly relay and plug families.
- Most of the hard app-side plumbing already exists.
- Users currently get different configuration depth depending on whether a switch is installed as a standalone device or as a parent-child component.

### 2. Device-side cover configuration

Current state:

- `UniversalDrivers/ShellySingleCoverPMParent.groovy` exposes only logging and PM reporting interval.
- `UniversalDrivers/UniversalComponentDrivers/ShellyCoverComponentPM.groovy` exposes only a local `swapOpenClose` preference.
- The app already has `Cover.GetConfig` and `Cover.SetConfig` command builders, but the active cover drivers do not present a real settings surface for them.

Missing settings to expose directly:

- device-side direction / reverse-travel behavior
- input behavior for cover control
- travel or calibration-related behavior
- obstruction / protection / safety thresholds

Why this is very high value:

- Cover installs are unusually sensitive to direction, calibration, and safety tuning.
- Current users cannot manage these settings from Hubitat in the active cover drivers.
- `swapOpenClose` is only a Hubitat-side workaround, not a substitute for real cover config.

### 3. Environmental sensor thresholds and reporting cadence for active battery sensors

Current state:

- `UniversalDrivers/ShellyTHSensor.groovy` exposes only `tempOffset` and `humidityOffset`.
- `UniversalDrivers/ShellyFloodSensor.groovy` and `UniversalDrivers/ShellySmokeSensor.groovy` expose no direct device-behavior settings beyond logging.
- The app already contains `Temperature.GetConfig`, `Humidity.GetConfig`, `Illuminance.GetConfig`, and `Illuminance.SetConfig` helpers, but no active driver path currently turns those into user-facing preferences.

Missing settings to expose where the device components support them:

- report interval / wake interval
- temperature / humidity / illuminance thresholds or hysteresis
- calibration / offset parity across the non-legacy environmental sensor drivers

Why this is very high value:

- Directly affects battery life, event noise, and the usefulness of these devices in automations.
- These are common settings that users expect to be able to adjust without leaving Hubitat.

## High Value

### 4. Advanced input configuration beyond simple type selection

Current state:

- `UniversalDrivers/ShellyPlusUniParent.groovy` exposes only `input0Mode` and `input1Mode`.
- Input child drivers (`ShellyInputButtonComponent.groovy`, `ShellyInputSwitchComponent.groovy`, `ShellyInputAnalogComponent.groovy`, `ShellyInputCountComponent.groovy`) expose no device-side config beyond logging.
- The app already has generic `Input.GetConfig` and `Input.SetConfig` helpers, but current driver usage is limited to simple input type selection.

Missing settings to expose where supported:

- inversion / active-low vs active-high behavior
- per-input enable/disable or lock behavior
- button-specific behavior tuning
- analog and counter thresholds / scaling / conditioning

Why this is high value:

- Input-heavy devices are often used for dry contacts, buttons, reed sensors, and analog sources.
- Users currently have very limited control beyond selecting `button`, `switch`, or `analog`.

### 5. Metering component configuration for PM1 / EM1 / EM1Data devices

Current state:

- `UniversalDrivers/ShellyPMMonitor.groovy`, `ShellyPro3EMParent.groovy`, and the EM child drivers expose telemetry and counter reset, but not direct device config.
- The app already includes `PM1.SetConfig`, `EM1.SetConfig`, and `EM1Data.SetConfig` builders.

Missing settings to expose where the component config supports them:

- channel-specific PM / EM configuration
- EM data / history behavior from `EM1Data`
- returned-energy or net-energy behavior that is configurable at the device level

Why this is high value:

- These devices are usually installed precisely because detailed measurement matters.
- The RPC surface already suggests there is more configurable behavior than the current drivers expose.

### 6. Consistent calibration parity across active sensor families

Current state:

- Some Gen 1 drivers expose offsets and thresholds richly.
- Equivalent active Gen 2+/Gen 4 sensor drivers often expose none, or only a subset such as temperature/humidity offset.

Missing settings to normalize where supported:

- temperature offset parity on flood/smoke-adjacent sensor drivers
- humidity / illuminance calibration parity on active environmental drivers

Why this is high value:

- Small calibration adjustments materially change automation behavior.
- Current behavior differs more by driver history than by user value.

## Medium Value

### 7. BLE service-role configuration on devices that can act as gateways

Current state:

- The app uses `BLE.SetConfig` during provisioning for BLU gateway scenarios.
- The installed drivers do not give users a direct way to inspect or control BLE enablement, RPC availability, or observer mode.

Missing settings:

- BLE enabled
- BLE RPC enabled
- BLE observer enabled

Why this is medium value:

- Useful for troubleshooting and niche topologies.
- Less important than relay, cover, and sensor behavior for most users.

### 8. DevicePower / battery configuration surfaces

Current state:

- The app has a `DevicePower.SetConfig` builder.
- No active drivers expose battery-component config directly.

Missing settings to investigate and expose where supported:

- battery reporting behavior
- battery-related thresholds or wake policies

Why this is medium value:

- Potentially useful on battery-heavy installations.
- The exact config surface appears device-specific and needs confirmation before implementation.

### 9. Display and indicator behavior outside the already-supported UI components

Current state:

- PLUGS_UI and WallDimmer `WD_UI` are already covered.
- There is no broader active-driver settings surface for display or indicator behavior on other supported device families.

Missing settings to expose where supported:

- screen brightness / timeout / idle behavior
- non-PLUGS indicator LED modes

Why this is medium value:

- Useful quality-of-life tuning.
- Usually secondary to relay, cover, and sensor behavior.

## Low Value

### 10. Cosmetic component naming and similar low-impact config fields

Current state:

- Some RPC helpers imply component-level config exists.
- The active drivers generally do not expose cosmetic naming/config fields directly.

Missing settings:

- component `name` fields or similar cosmetic metadata where Shelly exposes them

Why this is low value:

- Low impact on automations compared with behavior, thresholds, and safety settings.

### 11. Low-level metering and history maintenance knobs

Current state:

- Reset counters is exposed.
- Deeper history/maintenance configuration is not surfaced in the active drivers.

Missing settings to expose only if real user demand appears:

- retention / archive / purge-related controls where `EM1Data` supports them
- other maintenance-only settings that do not materially change normal runtime behavior

Why this is low value:

- Helpful for diagnostics, but weak first-pass value compared with the items above.

## Recommended Implementation Order

1. Add standalone switch parity with the existing component switch drivers.
2. Expose real cover config from the existing `Cover.GetConfig` / `Cover.SetConfig` plumbing.
3. Add sensor interval / threshold preferences for the active environmental battery drivers.
4. Expand input configuration beyond simple mode selection.
5. Surface PM / EM component config only after the higher-value behavioral settings land.

## Cross-Cutting Notes

- Several of the highest-value gaps are not missing API support; they are missing driver preference surfaces.
- Sleeping devices will need the same deferred/pending-command approach already used elsewhere in the app when new battery-device settings are added.
- Switch settings currently show a clear parity gap between standalone drivers and parent-child component drivers; this is the fastest place to win back a lot of value.
