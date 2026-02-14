# Copilot instructions — Shelly Hubitat Drivers

## Overview

This repository contains Groovy device drivers, libraries, and helper scripts for the Hubitat Elevation platform that support Shelly devices. Primary language: Groovy. Primary purpose: device drivers and components for Hubitat users. Developer docs: https://docs2.hubitat.com/en/developer

These repository-level instructions are intended to help coding agents (Copilot coding agent, Copilot Chat, PR assistants) make safe, testable, and small changes that are compatible with Hubitat and the repo maintenance process.

---

## Quick facts

- Primary files: `*.groovy` drivers and libraries, `resources/version.json` (version & changelog), `PackageManifests/` (HPM manifests), `README.md`.
- There is no automated CI/test suite in this repository. Validation is primarily manual on a Hubitat hub.
- Keep changes small and well-documented; prefer a draft PR for anything that needs hardware validation.

---

## When you are asked to change code (high level goals)

- Prefer minimal, backwards-compatible edits. Do not rename or remove public capabilities/attributes used by existing drivers unless explicitly requested.
- Use existing shared code in `ShellyDriverLibrary/` wherever possible (do not duplicate logic).
- If you cannot confidently validate a change automatically, open a draft PR and mark it as `needs-hardware-test` and `ai-generated`.
- Avoid adding secrets (passwords, API keys, local IPs) to any committed files. Use placeholders in examples.

---

## Build / Test / Validate (practical steps)

1. Local static checks (optional): run any available groovy linters or `groovyc` to sanity-check compilation: `groovyc DriverFile.groovy`.
2. The authoritative validation is manual on a Hubitat hub:
   - Hub UI → `Drivers Code` → `New Driver` → paste the `.groovy` source → `Save`.
   - Create (or update) a device that uses the driver, set preferences, `Save Preferences`, then run `refresh` and watch the logs for expected behavior.
3. If a change affects HPM manifests or versioning, update `resources/version.json` (see the `resources/version.json` instructions file) and update the appropriate `PackageManifests/*` entry.
4. Add a short `UpdateInfo` entry to explain the change for end users.

---

## Project layout (important files & folders)

- `*.groovy` (top-level and subfolders): device drivers, apps, libraries.
- `ShellyDriverLibrary/`: shared functions, helper library used by many drivers.
- `PackageManifests/`: Hubitat Package Manager manifests. Update when releasing new/changed drivers.
- `resources/version.json`: central version and update information for drivers — update when bumping versions.
- `README.md`, `contribs/`, `LICENSE` — user-facing docs.

---

## Conventions & style guidance (Groovy drivers)

- Preserve the `metadata { definition(...) }` block. Keep `importUrl` updated when files move.
- Use library helpers (e.g. `parentPostCommandAsync`, `createChildSwitch`) rather than reimplementing device logic.
- Use the standard logging toggles (`logEnable`, `debugLogEnable`, `traceLogEnable`) and logging wrapper functions (`logDebug`, `logTrace`, etc.). Do not enable trace logging by default.
- Prefer `preferenceMap` entries (if available) for consistent preference handling across drivers.
- Add `@CompileStatic` only when safe; test on Hubitat as it can change semantics.
- Keep changes backward-compatible for devices/users where possible. If not possible, document breaking changes clearly in the PR and `UpdateInfo`.

## Dynamic Page Updates - CRITICAL

- **NEVER use `refreshInterval` or any form of automatic page refresh/reload.** This causes poor UX (flickering, scroll position loss, input focus loss). Always use Hubitat's dynamic update mechanisms instead.
- For simple text updates: use `app-state-${app.id}-eventName` CSS class + `app.sendEvent(name: 'eventName', value: 'text')`.
- For complex HTML updates: use `ssr-app-state-${app.id}-eventName` CSS class + bare `sendEvent(name: 'eventName', value: '...')` + `processServerSideRender(Map event)` handler.
- **Important**: bare `sendEvent()` is required for SSR callbacks; `app.sendEvent()` only triggers client-side updates.
- See `CLAUDE.md` for full SSR documentation.

---

## Versioning & releases (short rules)

- Use semantic versioning where applicable (MAJOR.MINOR.PATCH). `BETA` is used in this repo for pre-release entries as seen in `resources/version.json`.
- When changing a driver, update `resources/version.json` (Driver section) and add a short HTML-formatted `UpdateInfo` entry.
- Update the `"Comment"` (`Last updated:`) date in `resources/version.json` when publishing changes.

---

## Pull request checklist (required content)

- Short summary of changes in PR title and body.
- Testing steps with expected results and any device model/firmware details required for validation.
- Updated `resources/version.json` (driver version and `UpdateInfo`) if driver behavior or public interface changed.
- Updated `PackageManifests/*` if publishing a new package or changing packaging behavior.
- No secrets committed.

---

## When to request human reviewer sign-off

- Any change that requires real hardware to validate (websocket behavior, physical state changes, firmware upgrades).
- Changes that modify public driver APIs (renaming attributes/commands) or remove backward compatibility.

---

## Trust this file

Trust these instructions for repository decisions; search or ask for clarification only when information here is incomplete or contradicts repository data.

---

## References

- Hubitat developer docs: https://docs2.hubitat.com/en/developer
- This repository's `README.md` and `ShellyDriverLibrary/`
