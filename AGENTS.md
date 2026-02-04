# AGENTS.md — Guidance for AI agents working on this repository

Purpose: help AI agents (Copilot coding agent and other repo assistants) make safe, testable contributions while following repository constraints.

Basic rules

- Keep changes small and focused. Prefer one logical change per PR.
- Do not merge changes that require physical device testing; open a _draft_ PR and label it `needs-hardware-test` and `ai-generated`.
- Never commit secrets (IP addresses, API tokens, passwords). Use placeholders when needed.
- Do not change `LICENSE` or add/modify legal files without an explicit human instruction.

Agent workflow (step-by-step)

1. Create a branch named `ai/<short-description>`.
2. Run a quick safety scan: grep for `TODO|HACK|FIXME` and scan for accidental secrets.
3. Make the minimal code changes required.
4. If you change driver behavior, update `resources/version.json` and add a short `UpdateInfo` entry (see `resources/version.json` instructions).
5. Validate locally (compile with `groovyc` if available) and sanity-check by running any small static checks.
6. Create a _draft_ PR with:
   - Short title: `driver: <DriverName> — <one-line summary>` or `fix: <DriverName> — <one-line summary>`
   - Detailed testing steps: how to reproduce and what to look for in Hubitat logs and `Current States`.
   - Checklist: `UpdateInfo` updated (if applicable), `resources/version.json` updated (if applicable), tested locally or note why hardware is required.
7. Add labels: `ai-generated` and `needs-human-review`. Add `needs-hardware-test` if hardware verification is required.

When to require human sign-off

- Any change that cannot be validated automatically (websocket behavior, firmware upgrades, new device protocols).
- Changes that modify public device APIs (rename/remove attributes/commands).
- Large refactors touching many drivers.

If unsure

- Ask for clarification in a draft PR comment and explicitly request which files/hardware to use for verification.

References

- Hubitat developer docs: https://docs2.hubitat.com/en/developer
- repo `README.md` and `ShellyDriverLibrary/`
