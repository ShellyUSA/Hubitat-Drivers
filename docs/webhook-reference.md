# Shelly Webhook Reference

This document describes how Shelly devices communicate with Hubitat via webhooks using URL token replacement.

## Overview

Shelly Gen2+ devices support **webhooks** — HTTP requests fired when device events occur. Each webhook URL can contain **token templates** that the Shelly firmware resolves at fire time, embedding real-time data directly in the URL query parameters.

### Communication Methods

| Data Type            | Method        | Format                                                     |
| -------------------- | ------------- | ---------------------------------------------------------- |
| Switch on/off        | Webhook (GET) | Query parameters with `output=true/false`                  |
| Cover state/position | Webhook (GET) | Query parameters with `state=` and `pos=`                  |
| Temperature          | Webhook (GET) | Query parameters with `tC=` and `tF=`                      |
| Humidity             | Webhook (GET) | Query parameters with `rh=`                                |
| Battery              | Webhook (GET) | Piggybacked on sensor webhooks via `battPct=` and `battV=` |
| Smoke/Illuminance    | Webhook (GET) | Query parameters                                           |
| Input buttons        | Webhook (GET) | dst only (push/double/long)                                |
| Power monitoring     | Script (POST) | JSON body from `powermonitoring.js`                        |
| Light status         | Script (POST) | JSON body from `lightstatus.js`                            |

## Webhook URL Format

```
http://<hub_ip>:39501?dst=<destination>&comp=${ev.component}&<event_params>[&<supplemental_params>]
```

### Parameters

- **dst** — Routing destination identifier (e.g., `switchmon`, `covermon`, `temperature`)
- **comp** — Shelly component that fired the event, resolved from `${ev.component}` (e.g., `switch:0`, `cover:0`)
- **Event params** — Event-specific data tokens resolved by Shelly firmware
- **Supplemental params** — Optional additional data (e.g., battery level) appended when device has matching components

## Webhook Events

### Switch Events

| Event        | dst         | URL Params     |
| ------------ | ----------- | -------------- |
| `switch.on`  | `switchmon` | `output=true`  |
| `switch.off` | `switchmon` | `output=false` |

**Example URL (as configured on device):**

```
http://192.168.1.100:39501?dst=switchmon&comp=${ev.component}&output=true
```

**Example received request:**

```
GET /?dst=switchmon&comp=switch%3A0&output=true HTTP/1.1
```

### Cover Events

| Event               | dst        | URL Params                                                 |
| ------------------- | ---------- | ---------------------------------------------------------- |
| `cover.open`        | `covermon` | `state=open&pos=${status["cover:CID"].current_pos}`        |
| `cover.closed`      | `covermon` | `state=closed&pos=${status["cover:CID"].current_pos}`      |
| `cover.stopped`     | `covermon` | `state=stopped&pos=${status["cover:CID"].current_pos}`     |
| `cover.opening`     | `covermon` | `state=opening&pos=${status["cover:CID"].current_pos}`     |
| `cover.closing`     | `covermon` | `state=closing&pos=${status["cover:CID"].current_pos}`     |
| `cover.calibrating` | `covermon` | `state=calibrating&pos=${status["cover:CID"].current_pos}` |

**Cover state mapping to Hubitat:**

| Shelly State  | Hubitat `windowShade` |
| ------------- | --------------------- |
| `open`        | `open`                |
| `closed`      | `closed`              |
| `opening`     | `opening`             |
| `closing`     | `closing`             |
| `stopped`     | `partially open`      |
| `calibrating` | `unknown`             |

### Sensor Events

| Event                | dst           | URL Params                |
| -------------------- | ------------- | ------------------------- |
| `temperature.change` | `temperature` | `tC=${ev.tC}&tF=${ev.tF}` |
| `humidity.change`    | `humidity`    | `rh=${ev.rh}`             |
| `illuminance.change` | `illuminance` | `lux=${ev.lux}`           |
| `smoke.alarm`        | `smoke`       | `alarm=true`              |

### Input Button Events

| Event                     | dst            | URL Params |
| ------------------------- | -------------- | ---------- |
| `input.button_push`       | `input_push`   | _(none)_   |
| `input.button_doublepush` | `input_double` | _(none)_   |
| `input.button_longpush`   | `input_long`   | _(none)_   |

## Supplemental Token Groups

Supplemental data is appended to webhook URLs when a device has specific components. This eliminates the need for separate RPC queries.

### Battery Data

**Condition:** Device has a `devicepower` component (battery-powered devices like Shelly H&T).

**Appended to ALL webhook URLs on qualifying devices:**

```
&battPct=${status["devicepower:0"].battery.percent}&battV=${status["devicepower:0"].battery.V}
```

**Example:** Temperature webhook on a battery-powered Shelly H&T:

```
http://192.168.1.100:39501?dst=temperature&comp=${ev.component}&tC=${ev.tC}&tF=${ev.tF}&battPct=${status["devicepower:0"].battery.percent}&battV=${status["devicepower:0"].battery.V}
```

**Received:**

```
GET /?dst=temperature&comp=temperature%3A0&tC=24.5&tF=76.1&battPct=85&battV=4.87 HTTP/1.1
```

This delivers temperature AND battery data in a single webhook, avoiding the need to query battery level via RPC while the device is briefly awake.

## Skipped Events

These Shelly webhook events are intentionally not configured (they don't map to useful Hubitat attributes):

- `ble.scan_result`
- `wifi.sta_connect`
- `wifi.sta_disconnect`
- `cloud.connected`
- `cloud.disconnected`

## URL Length

Shelly devices have a ~300 character limit for webhook URLs. The longest URL (temperature with battery tokens) is approximately 175 characters, well within limits.

## Configuration Schema

Webhook definitions are centralized in `UniversalDrivers/component_driver.json` under the `webhookDefinitions` key:

```json
{
  "webhookDefinitions": {
    "events": {
      "<shelly_event>": {
        "dst": "<routing_destination>",
        "name": "<webhook_name>",
        "urlParams": "<query_params_with_tokens>",
        "shellyComponent": "<component_type>"
      }
    },
    "supplementalTokenGroups": {
      "<group_name>": {
        "requiredComponent": "<component_type>",
        "urlParams": "<additional_query_params>"
      }
    },
    "skippedEvents": ["<event_name>", ...]
  }
}
```

### Field Descriptions

- **events.\*.dst** — Identifier used to route the webhook to the correct handler
- **events.\*.name** — Human-readable name stored on the Shelly device
- **events.\*.urlParams** — Query string tokens; `__CID__` is replaced with the component ID at build time
- **events.\*.shellyComponent** — Which Shelly component type this event belongs to
- **supplementalTokenGroups.\*.requiredComponent** — Component that must exist on the device for this group to be appended
- **supplementalTokenGroups.\*.urlParams** — Additional query params appended to all webhook URLs on qualifying devices
- **skippedEvents** — Events that are valid but intentionally not configured (won't generate "unknown event" warnings)

## Backward Compatibility

- **POST JSON body parsing is preserved** — Existing devices running `powermonitoring.js` and `lightstatus.js` scripts continue to work unchanged
- **Old scripts are cleaned up** — When webhooks are provisioned, obsolete `switchstatus` and `coverstatus` scripts are automatically removed from devices
- **`componentRequestBatteryLevel()` stub** — Kept as a no-op for drivers that still call it during the transition
