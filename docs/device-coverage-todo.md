# Device Coverage TODO

Last reviewed: 2026-03-28

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

### 1. Shelly Wall Display X2i / Wall Display XL

Why this is a gap:

- Current wall display detection in `Apps/ShellyDeviceManager.groovy` assumes a `switch + temperature + humidity + illuminance` device shape.
- `UniversalDrivers/ShellyWallDisplayParent.groovy` is tailored to the older Wall Display sensor mix.
- Current Shelly docs describe newer Wall Display models with materially different hardware:
  - `Wall Display X2i` adds interchangeable power bases, including a 2-output base.
  - `Wall Display XL` adds 4 physical buttons.
  - `X2i` and `XL` are not documented as direct matches for the original driver assumptions.

TODO:

- Capture real `GetStatus` / component lists from `Wall Display X2i` and `Wall Display XL`.
- Extend device classification so newer wall display models do not depend on the original temp/humidity/lux profile.
- Update or split the parent driver to support:
  - 1-output and 2-output `X2i` power-base variants.
  - `XL` front-button events if the local API exposes them cleanly.
  - illuminance-only variants if temperature and humidity are absent.
- Verify that webhook and child-button behavior still works after the model split.

### 2. The Pill by Shelly

Why this is a gap:

- The current Shelly catalog lists `The Pill` as a live Gen3 product.
- There is no explicit discovery mapping, driver selection branch, or parent driver for it in the active codebase.
- Existing BLU gateway support is TRV-centric and is not a good fit for a mixed sensor/I/O platform device.

TODO:

- Inspect the real component and mode model exposed by `The Pill`.
- Decide whether it should be handled as a configurable parent platform rather than a single fixed driver.
- Support the major documented modes:
  - `DS18B20` temperature probes
  - `DHT22` temperature + humidity
  - analog voltage / `0-30V` add-on mode
  - digital I/O
  - SSR add-on outputs
  - BLE gateway role
- Add any new component-driver install paths required by the selected design.

### 3. Shelly BLU Distance

Why this is a gap:

- The current Shelly catalog lists `Shelly BLU Distance` as a current BLU device.
- There is no BLE model mapping for it in `Apps/ShellyDeviceManager.groovy`.
- There is no dedicated driver or BLE event handling for distance/level reporting in the active stack.

TODO:

- Add the BLU model-code / model-ID mapping once verified from official docs or live advertisements.
- Define the Hubitat-facing attribute model:
  - `Battery`
  - custom distance attribute such as `distanceMm` or `distanceCm`
  - optional derived occupancy or level state based on thresholds
- Parse the device's BLE advertisement or gateway-relayed payload shape.
- Add a dedicated driver and document common use cases such as tank level, parking, and obstacle detection.

## Likely Gaps That Need Validation

### 4. Shelly DALI Dimmer Gen3

Why this needs validation before being called supported:

- Official Shelly docs describe a clean `light:0` + `input:*` + `dali` device model.
- The generic dimmer path may partially work, but the active repo does not include any explicit `dali` handling, diagnostics, or model-specific discovery logic.
- No active driver names, scripts, or discovery branches mention `DALI` today.

TODO:

- Test discovery using a real device or captured API payloads.
- If `dali` is exposed as a distinct component, teach `determineDeviceDriver()` and driver installation logic to recognize it.
- Expose DALI bus diagnostics that are useful in Hubitat, such as gear count and bus error state.
- Keep scope to Shelly's documented broadcast-dimmer behavior. Do not assume per-ballast child devices.

### 5. BLU Variant Mapping Audit

Why this needs validation:

- The current BLU model tables cover the main `Button1`, `Button4`/remote, `Door/Window`, `H&T`, `Motion`, `Wall Switch 4`, and `TRV` shapes.
- Current Shelly docs list additional BLU variants that are not explicitly mapped in the active app, including product variants such as `Button Tough 1`, `Wall Switch 4 ZB`, and newer remote-control variants.
- Some of these may only need alias model mappings to existing drivers, while others may require new event parsing.

TODO:

- Audit current Shelly BLU model codes against `BLE_MODEL_TO_DRIVER` and `BLE_MODEL_ID_TO_DRIVER`.
- Add alias mappings where the payload shape already matches an existing driver.
- Create new drivers only where the payload or capability model is genuinely new.
- Keep `Shelly BLU TRV` out of this gap list. It is already supported through the BLU Gateway path.

## Validation-Only Items

These are not currently counted as missing coverage, but they should be revisited when hardware or API dumps are available:

- `Wall Display X2` may already be close to the existing wall display path, but its actual component map still needs to be verified.
- Gen3/Gen4 relays, PM devices, covers, and multi-relay products appear to be broadly covered by the generic component-based driver logic.
- `Power Strip 4 Gen4`, `Flood Gen4`, and `Leak Sensor Cable` already have active code paths and should not be treated as gaps.

## Explicitly Out Of Scope For This TODO

- `Shelly Wave` and `Qubino Wave` Z-Wave devices
- `LoRa` add-on support
- passive accessories that do not add a new local device or component model on their own
