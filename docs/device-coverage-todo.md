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

### 1. The Pill by Shelly multi-mode platform support

Why this is a gap:

- The current Shelly catalog lists `The Pill` as a live Gen3 product.
- The current repo already has partial narrow coverage for the H&T + illuminance slice via `Shelly Autoconf THL Sensor`, but that does not cover the broader configurable platform.
- There is still no explicit discovery mapping or configurable parent for the multi-mode `The Pill` topology (`temperature:*`, `humidity:*`, `input:*`, `voltmeter:*`, optional SSR outputs, and optional BLE-gateway behavior).
- Existing BLU gateway support is TRV-centric and is not a good fit for a mixed sensor/I/O platform device.

TODO:

- Capture real `Shelly.GetDeviceInfo`, `Shelly.GetStatus`, `Shelly.GetConfig`, and `Shelly.GetComponents` payloads for the official `S3SN-0U53X` hardware in its major modes.
- Route by a confirmed live model/app identifier instead of relying on catalog naming alone.
- Decide whether it should be handled as a configurable parent platform rather than a single fixed driver.
- Support the major documented modes:
  - `DS18B20` temperature probes
  - `DHT22` temperature + humidity
  - analog voltage / `0-30V` add-on mode
  - digital I/O
  - SSR add-on outputs
  - BLE gateway role
- Add any new component-driver install paths required by the selected design.

## Likely Gaps That Need Validation

### 2. Shelly DALI Dimmer Gen3

Why this needs validation before being called supported:

- Official Shelly docs describe a clean `light:0` + `input:*` + `dali` device model.
- The generic dimmer path may partially work, but the active repo does not include any explicit `dali` handling, diagnostics, or model-specific discovery logic.
- No active driver names, scripts, or discovery branches mention `DALI` today.

TODO:

- Test discovery using a real device or captured API payloads.
- If `dali` is exposed as a distinct component, teach `determineDeviceDriver()` and driver installation logic to recognize it.
- Expose DALI bus diagnostics that are useful in Hubitat, such as gear count and bus error state.
- Keep scope to Shelly's documented broadcast-dimmer behavior. Do not assume per-ballast child devices.

### 3. BLU Variant Mapping Audit

Why this needs validation:

- The current BLU model tables cover the main `Button1`, `Button4`/remote, `Door/Window`, `H&T`, `Motion`, `Wall Switch 4`, and `TRV` shapes.
- Current Shelly docs list additional BLU variants that are not explicitly mapped in the active app, including product variants such as `Button Tough 1`, `Wall Switch 4 ZB`, and newer remote-control variants.
- Some of these may only need alias model mappings to existing drivers, while others may require new event parsing.

TODO:

- Audit current Shelly BLU model codes against `BLE_MODEL_TO_DRIVER` and `BLE_MODEL_ID_TO_DRIVER`.
- Add alias mappings where the payload shape already matches an existing driver.
- Create new drivers only where the payload or capability model is genuinely new.
- Keep `Shelly BLU TRV` out of this gap list. It is already supported through the BLU Gateway path.

### 4. Shelly Plug US Gen4 Illuminance Integration

Why this needs validation before being called supported:

- Official Shelly docs describe `Shelly Plug US Gen4` as more than a plain single-switch PM device.
- The active autoconf path should already cover the relay and PM behavior, but `illuminance:0` may be dropped by the current monolithic `Single Switch PM` driver path.
- `PLUGS_UI` support already exists, so the remaining question is whether the built-in light sensor needs a dedicated parent/child or monolithic sensor exposure path.

TODO:

- Capture a real `GetStatus` payload (or equivalent API dump) from `Shelly Plug US Gen4`.
- Confirm whether `illuminance:0` is always present and whether it changes frequently enough to justify webhook/script handling.
- Decide whether illuminance should live on the main driver or a child sensor device.
- Verify that LED-control child behavior (`PLUGS_UI`) still works if the device moves off the plain single-switch PM path.

### 5. Shelly EM Mini Gen4

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
- `Wall Display X2i` and `Wall Display XL` now have dedicated model-specific routing to the Wall Display parent, and the parent now tolerates illuminance-only variants plus the X2i 2-output power base.
- Remaining wall-display validation: capture real `GetStatus` / component payloads for `SAWD-5A1XX10EU0` and `SAWD-3A1XE10EU2`, and confirm whether the XL front-panel buttons expose local API events at all.
- `Wall Display X2` may still be close to the legacy wall-display path, but its current firmware component map should be verified when hardware is available.
- `Shelly Dimmer Gen4 EU/US` appears to fit the existing single-dimmer path and should not be treated as a gap without contrary hardware evidence.
- `Shelly 1 Gen4`, `Shelly 1PM Gen4`, and `Shelly 2PM Gen4` now have dedicated model-specific routing in the active app while reusing the proven switch/cover implementations.
- `Power Strip 4 Gen4`, `Flood Gen4`, and `Leak Sensor Cable` already have active code paths and should not be treated as gaps.

## Explicitly Out Of Scope For This TODO

- `Shelly Wave` and `Qubino Wave` Z-Wave devices
- `LoRa` add-on support
- passive accessories that do not add a new local device or component model on their own
