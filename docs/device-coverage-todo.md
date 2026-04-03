# Device Coverage TODO

Last reviewed: 2026-04-02

This backlog compares the current Shelly catalog in `shelly-product-catalog.md` against the active autoconf stack:

- `Apps/ShellyDeviceManager.groovy`
- `UniversalDrivers/`
- `UniversalDrivers/UniversalComponentDrivers/`
- `Scripts/`

Scope notes:

- Focus on current WiFi, BLE, and Ethernet Shelly devices that are relevant to the active autoconf stack.
- Exclude `Shelly Wave` / `Qubino Wave` Z-Wave products. They are outside the non-Z-Wave catalog and outside the current app/driver architecture.
- Exclude passive accessories unless they require driver or discovery changes.
- Do not treat devices already covered by generic component-based drivers as missing just because they lack a dedicated model-specific name.

## Confirmed Current Gaps

No currently confirmed missing-support items remain at the top of the backlog.

- `The Pill` now has dedicated model-aware routing for profiles beyond the existing THL-style illuminance slice, plus a dedicated configurable parent driver for that broader platform behavior.
- Remaining backlog items are now validation-heavy follow-up work rather than clear missing-support gaps.

## Likely Gaps That Need Validation

### 1. The Pill by Shelly live payload and mixed-mode validation

Why this still needs validation:

- `The Pill` profiles beyond the existing THL-style illuminance slice now route to `Shelly Autoconf Pill Parent` using the confirmed hardware model code `S3SN-0U53X` plus a best-effort `ShellyPill` app-name alias.
- The new parent covers the broader configurable platform surfaces that were previously missing from the active stack: `temperature:*`, `humidity:*`, `input:*`, `voltmeter:*`, and optional `switch:*` SSR outputs.
- The existing narrow H&T + illuminance slice is intentionally left on `Shelly Autoconf THL Sensor` for now so lux support is not regressed before an active-scope illuminance component-driver path exists.
- If live components expose `blugw` / `blutrv`, the app still defers to the existing BLU Gateway parent instead of forcing the Pill parent. That is the safest current behavior, but it still needs real hardware validation.

TODO:

- Capture real `Shelly.GetDeviceInfo`, `Shelly.GetStatus`, `Shelly.GetConfig`, and `Shelly.GetComponents` payloads for the official `S3SN-0U53X` hardware in its major modes.
- Confirm the live `Shelly.GetDeviceInfo.app` value instead of relying on the current best-effort `ShellyPill` alias.
- Validate exact input numbering and status payload shapes for digital-input, analog-input, and DHT22 / DS18B20 profiles.
- Verify SSR add-on output behavior and whether any PM-related fields are ever present on `switch:*`.
- Validate the `blugw` / BLE-gateway role on real hardware, especially if the device can mix local platform components with gateway responsibilities.
- Decide whether the existing THL / illuminance slice should eventually migrate into the dedicated Pill parent once an active-scope illuminance child-driver path exists.

### 2. Shelly Plug US Gen4 Illuminance Integration

Why this needs validation before being called supported:

- Official Shelly docs describe `Shelly Plug US Gen4` as more than a plain single-switch PM device.
- The active autoconf path should already cover the relay and PM behavior, but `illuminance:0` may be dropped by the current monolithic `Single Switch PM` driver path.
- `PLUGS_UI` support already exists, so the remaining question is whether the built-in light sensor needs a dedicated parent/child or monolithic sensor exposure path.

TODO:

- Capture a real `GetStatus` payload (or equivalent API dump) from `Shelly Plug US Gen4`.
- Confirm whether `illuminance:0` is always present and whether it changes frequently enough to justify webhook/script handling.
- Decide whether illuminance should live on the main driver or a child sensor device.
- Verify that LED-control child behavior (`PLUGS_UI`) still works if the device moves off the plain single-switch PM path.

### 3. Shelly EM Mini Gen4

Why this needs validation before being called supported:

- The active stack likely maps `Shelly EM Mini Gen4` onto the existing EM parent path.
- Current repo evidence is still indirect; there is not yet a verified local payload capture confirming the component names, phase shape, and any Gen4-specific diagnostics.
- This looks more like a validation gap than a known implementation gap, but it should stay on the backlog until a real device or authoritative API dump confirms the assumption.

TODO:

- Capture real `GetStatus` / component data from `Shelly EM Mini Gen4`.
- Verify whether it exposes `em`, `em1`, or another power-monitoring component shape.
- Confirm that the existing EM parent and child component drivers expose the most useful Gen4 metrics without additional model-specific handling.

## Validation-Only Items

These are not currently counted as missing coverage, but they should be revisited when hardware or API dumps are available:

- `Shelly BLU Distance` now has dedicated BLU model routing for `SBDI-003E` / `0x000A`, forwards BTHome `0x40` distance measurements, and uses a dedicated driver exposing `distanceMm` plus battery/presence.
- `Shelly BLU Remote Control ZB` now has dedicated BLU model routing for `SBRC-005B` / `0x0009`, uses its own standalone driver, forwards raw `channel` / `dimmer` values plus raw `rotation1..3`, and treats the repeated button objects as raw button slots `1..2` without guessing left/right semantics.
- `Shelly DALI Dimmer Gen3` now has dedicated routing for the confirmed `S3DM-0A1WW` hardware plus explicit `dali` component recognition, uses a dedicated standalone `Shelly Autoconf DALI Dimmer` driver, and exposes DALI gear-count/error/scan diagnostics plus scan and ping commands on the main dimmer device.
- `The Pill` now has dedicated routing to `Shelly Autoconf Pill Parent` for profiles beyond the existing THL-style illuminance slice, while that illuminance slice is intentionally preserved on `Shelly Autoconf THL Sensor` until live payloads and an illuminance child path are validated.
- `Wall Display X2i` and `Wall Display XL` now have dedicated model-specific routing to the Wall Display parent, and the parent now tolerates illuminance-only variants plus the X2i 2-output power base.
- Remaining wall-display validation: capture real `GetStatus` / component payloads for `SAWD-5A1XX10EU0` and `SAWD-3A1XE10EU2`, and confirm whether the XL front-panel buttons expose local API events at all.
- `Wall Display X2` may still be close to the legacy wall-display path, but its current firmware component map should be verified when hardware is available.
- Remaining DALI validation: capture a live `Shelly.GetDeviceInfo.app` value before adding an app-name override, confirm whether internal temperature is exposed in `light:0` status at all, and validate exact detached/button-mode webhook payloads on real hardware.
- Remaining BLU Remote Control ZB validation: capture live BLE advertisements to confirm whether raw channel values are `0..3` or `1..4`, whether the two repeated button objects map to a stable physical ordering, whether the three repeated rotation values have stable axis semantics, and whether accelerometer / magic-wand behavior needs additional attributes beyond the conservative raw baseline.
- `Shelly Dimmer Gen4 EU/US` appears to fit the existing single-dimmer path and should not be treated as a gap without contrary hardware evidence.
- `Shelly 1 Gen4`, `Shelly 1PM Gen4`, and `Shelly 2PM Gen4` now have dedicated model-specific routing in the active app while reusing the proven switch/cover implementations.
- `Power Strip 4 Gen4` now has dedicated model-aware routing to the shared `4x Switch PM Parent` path plus refresh-backed `POWERSTRIP_UI` config sync, so it should not be treated as a gap without contrary live payload evidence.
- `Flood Gen4` and `Leak Sensor Cable` already have active code paths and should not be treated as gaps.

## Explicitly Out Of Scope For This TODO

- `Shelly Wave` and `Qubino Wave` Z-Wave devices
- `LoRa` add-on support
- passive accessories that do not add a new local device or component model on their own
