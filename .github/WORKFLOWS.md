# GitHub Actions Release Workflows

This repository includes automated GitHub Actions workflows for managing releases of Shelly drivers and applications.

## Active vs Legacy Code

**Important:** These workflows only handle the following active folders:

- ✅ `WebhookWebsocket/` - Webhook and Websocket-based drivers (44 files)
- ✅ `Bluetooth/` - Bluetooth device drivers and helper app (7 files)
- ✅ `ComponentDrivers/` - Component drivers for parent devices (18 files)
- ✅ `ShellyDriverLibrary/` - Shared library code
- ✅ `PackageManifests/` - HPM package manifests

**Legacy code excluded from workflows:**

- ❌ `PLUS/` - Legacy Plus series drivers (not maintained)
- ❌ `resources/version.json` - Legacy version tracking (DEPRECATED)
- ❌ Root-level `Shelly*.groovy` files - Legacy standalone drivers

**Version Management:**

- Each driver file has a version comment block at the top: `/** Version: 2.0.0 */`
- Versions are updated automatically during GHA pipeline runs
- Developers do NOT manually update versions during development
- All version updates and commits happen during release workflow execution

If you need to release legacy drivers, manual releases are required.

---

## Available Workflows

### 1. Release Shelly Drivers (`release-shelly-drivers.yml`)

**Purpose:** Create versioned releases of Shelly drivers with automated version bumping, changelog generation, and GitHub Release creation.

**When to Use:**

- When you want to create a new release of Shelly drivers
- To tag a version with release notes
- To distribute driver files via GitHub Releases

**How to Run:**

1. Navigate to **Actions** tab in GitHub
2. Select **"Release Shelly Drivers"** workflow
3. Click **"Run workflow"**
4. Fill in the required inputs:
   - **Version Increment**: Choose `patch` (e.g., 3.0.10 → 3.0.11) or `minor` (e.g., 3.0.10 → 3.1.0)
   - **Release Notes**: Brief description of changes (e.g., "Fixed bug in switch component")
   - **Driver Category**: Select which drivers to release:
     - `all` - All active drivers (WebhookWebsocket, Bluetooth, ComponentDrivers)
     - `WebhookWebsocket` - Webhook/Websocket drivers only
     - `Bluetooth` - Bluetooth drivers only
     - `ComponentDrivers` - Component drivers only
5. Click **"Run workflow"**

**What It Does:**

- ✅ Reads current version from driver file version comments
- ✅ Calculates new version number based on increment type
- ✅ **Updates version comments in all selected driver files**
- ✅ **Creates versioned copies of all driver files** (e.g., `ShellyPlus1PM-v2.0.1.groovy`)
- ✅ Triggers library bundle workflow to create versioned bundle
- ✅ Generates changelog from git commits
- ✅ Creates git tag (e.g., `v2.0.1`)
- ✅ **Commits updated version comments back to repository**
- ✅ **Creates GitHub Release with versioned driver files**
- ✅ Attempts to create GitHub Discussion announcement

**Output:**

- New git tag: `v{version}`
- **GitHub Release with versioned driver files as assets**
- Updated version comments in all driver files (committed to repo)
- Changelog in release notes

**Important:** All driver files in releases are versioned. Package manifests will point to these versioned release assets for HPM installation.

---

### 2. Build Shelly Driver Library Bundle (`ShellyUSA_Driver_Library.yml`)

**Purpose:** Automatically create a ZIP bundle of the Shelly Driver Library whenever library files are updated.

**When to Use:**

- Automatically triggered when `ShellyDriverLibrary/ShellyUSA.ShellyUSA_Driver_Library.groovy` is modified
- Can be manually triggered if needed

**How to Run (Manual):**

1. Navigate to **Actions** tab
2. Select **"Build Shelly Driver Library Bundle"**
3. Click **"Run workflow"**
   **What It Does:**

- ✅ Reads library version from library file's version comment
- ✅ **Creates versioned ZIP bundle** (e.g., `ShellyUSA_Driver_Library-v2.0.0.zip`)
- ✅ Also creates unversioned bundle for backward compatibility
- ✅ Verifies bundle contents
- ✅ Commits bundles to repository root

**Output:**

- Updated `ShellyUSA_Driver_Library-v{version}.zip` in repository root
- Updated `ShellyUSA_Driver_Library.zip` (unversioned) in repository root

**Note:** The versioned bundle name matches the library file's version comment.

- Updated `ShellyUSA_Driver_Library.zip` (unversioned) in repository root

**Note:** The versioned bundle name matches the `ShellyUSA_Driver_Library` version in `resources/version.json`.
**Output:**

- Updated `ShellyUSA_Driver_Library.zip` in repository root

---

### 3. Update Package Manifests (`update-package-manifests.yml`)

**Purpose:** Update Hubitat Package Manager (HPM) manifest files with new version information and release notes.

**When to Use:**

- After creating a new release
- When updating version information for HPM users
- To update release notes visible in HPM

**How to Run:**

1. Navigate to **Actions** tab
2. Select **"Update Package Manifests"**
3. Click **"Run workflow"**
4. Fill in the required inputs:
   - **Version**: Version number (e.g., `2.15.0`)
   - **Release Notes**: Brief description for HPM users
5. Click **"Run workflow"**

**Wh**Updates all driver/app/bundle URLs to point to versioned GitHub release assets\*\*

- Format: `https://github.com/ShellyUSA/Hubitat-Drivers/releases/download/v{version}/{filename}-v{version}.groovy`
- Bundle: `https://github.com/ShellyUSA/Hubitat-Drivers/releases/download/v{version}/ShellyUSA_Driver_Library-v{lib_version}.zip`
- ✅ Validates JSON structure
- ✅ Commits updated manifests

**Output:**

- Updated `PackageManifests/**/packageManifest.json` files
- All URLs point to specific release assets (not master branch)

**Important:** This workflow ensures HPM installs drivers from the specific GitHub release, providing version stability for users.

- ✅ Commits updated manifests

**Output:**

- Updated `PackageManifests/**/packageManifest.json` files

---

## Typical Release Process

Here's the recommended workflow for creating a new release:

### Step 1: Prepare Your Changes

```bash
# Make your code changes
git checkout -b feature/my-changes
# ... make changes to driver files ...
git add .
git commit -m "Fix: Description of changes"
git push origin feature/my-changes
```

### Step 2: Merge to Master

- Create Pull Request
- Review changes
- Merge to `master` branch

### Step 3: Create Release

1. Run **"Release Shelly Drivers"** workflow
   - Choose version increment (patch or minor)
   - Enter descriptive release notes
   - Select driver category or "all"
   - Wait for completion (~3-5 minutes)
   - **Workflow creates versioned driver files** (e.g., `ShellyPlus1PM-v3.0.11.groovy`)

2. Verify the Release
   - Check GitHub Releases page
   - **Verify versioned files are attached to release**
   - Download and test driver files if needed

### Step 4: Update HPM Manifests

1. Run **"Update Package Manifests"** workflow
   - Use same version from Step 3
   - Enter HPM-friendly release notes
   - Wait for completion (~1 minute)
   - **Workflow updates all URLs to point to release assets**

2. Verify HPM Manifests
   - Check `PackageManifests/` directory
   - **Verify version and URLs point to GitHub release downloads**
   - URLs should look like: `https://github.com/ShellyUSA/Hubitat-Drivers/releases/download/v{version}/{filename}-v{version}.groovy`

3. Test HPM Installation
   - On a test Hubitat hub, try installing via HPM
   - Verify correct files are downloaded from release

---

## Versioned Files & Bundle

### Version Management Strategy

**All versions are managed via comment blocks at the top of driver files:**

```groovy
/**
 * Version: 2.0.0
 */
```

- Versions start at `2.0.0` for all active drivers
- Versions are **automatically updated** during GHA pipeline runs
- Developers **DO NOT** manually update versions during development
- Version updates and commits happen during release workflow execution

### Driver Files

All driver files in GitHub Releases are versioned with the release version:

- Format: `{DriverName}-v{version}.groovy`
- Example: `ShellyPlus1PM-v2.0.1.groovy`
- HPM downloads these versioned files from release assets
- **ALL** files from WebhookWebsocket, Bluetooth, and ComponentDrivers folders are included

### Library Bundle

The Shelly Driver Library bundle is also versioned:

- Format: `ShellyUSA_Driver_Library-v{version}.zip`
- Example: `ShellyUSA_Driver_Library-v2.0.0.zip`
- Library version matches driver version (they're released together)
- Version is read from library file's version comment

### Why Versioned Files?

1. **Stability:** Users get exactly the version they expect
2. **Rollback:** Easy to download previous versions from old releases
3. **Testing:** Can compare different versions side-by-side
4. **HPM Integration:** HPM can track which version is installed
5. **No Manual Version Management:** Developers focus on code, not version bookkeeping

---

## Version Numbering

This repository follows semantic versioning: `MAJOR.MINOR.PATCH`

- **PATCH** (e.g., 2.0.0 → 2.0.1): Bug fixes, minor improvements, no breaking changes
- **MINOR** (e.g., 2.0.1 → 2.1.0): New features, enhancements, backward compatible
- **MAJOR** (e.g., 2.1.0 → 3.0.0): Breaking changes (manual update required)

**Recommendation:**

- Use `patch` for most releases
- Use `minor` when adding new devices or significant features
- Manually update for `major` version changes

---

## Troubleshooting

### Workflow Failed: "Version already exists"

**Solution:** The git tag already exists. Either:

- Delete the tag: `git tag -d v3.0.11 && git push origin :refs/tags/v3.0.11`
- Increment to next version

### Workflow Failed: "Library bundle workflow timeout"

**Solution:** The library bundle workflow may still be running:

- Check Actions tab for bundle workflow status
- Wait for completion and re-run release workflow
- Or continue - release will work without bundle update

### Package Manifest URLs Wrong

**Solution:** Run "Update Package Manifests" workflow again:

- URLs should point to **GitHub release assets**: `https://github.com/ShellyUSA/Hubitat-Drivers/releases/download/v{version}/{filename}-v{version}.groovy`
- **Not** to master branch: ~~`https://raw.githubusercontent.com/.../master/...`~~
- This ensures users get specific versioned files, not latest code from master

### Discussion Creation Failed

**Solution:** This is optional and won't block release:

- Check if "Announcements" discussion category exists
- Verify repository has Discussions enabled
- Manually create discussion if needed

---

## Manual Release (Alternative Method)

If workflows fail or you need manual control:

```bash
# 1. Update version in files
# Edit driver files, update version: '3.0.11'
# Edit resources/version.json

# 2. Commit changes
git add .
git commit -m "Release version 3.0.11"
git push origin master

# 3. Create tag
git tag -a v3.0.11 -m "Release 3.0.11"
git push origin v3.0.11

# 4. Create GitHub Release manually
# Go to Releases → Draft new release
# Select tag v3.0.11
# Add release notes
# Attach driver files
# Publish release
```

---

## Workflow Permissions

These workflows require the following GitHub permissions:

- ✅ **contents: write** - To commit files and create tags
- ✅ **actions: write** - To trigger other workflows

Permissions are automatically granted via `GITHUB_TOKEN`.

---

## Support

For issues with workflows:

1. Check the Actions tab for detailed error logs
2. Review this documentation
3. Check repository instructions: `AGENTS.md` and `.github/copilot-instructions.md`
4. Open an issue on GitHub

---

## Best Practices

1. **Test Before Release**
   - Test drivers on Hubitat hub before releasing
   - Use draft PR for validation

2. **Write Clear Release Notes**
   - Be specific about what changed
   - Mention device models affected
   - Note any breaking changes

3. **Use Semantic Versioning**
   - Follow MAJOR.MINOR.PATCH convention
   - Increment appropriately for change type

4. **Keep Library Updated**
   - Library bundle auto-updates when files change
   - Verify bundle after library changes

5. **Verify HPM Manifests**
   - Test installation via HPM after updating manifests
   - Ensure URLs are correct and accessible

---

## Workflow Files

- `.github/workflows/release-shelly-drivers.yml` - Main release workflow (active folders only)
- `.github/workflows/ShellyUSA_Driver_Library.yml` - Library bundle workflow
- `.github/workflows/update-package-manifests.yml` - HPM manifest update workflow (active folders only)

**Scope:** All workflows are configured to only handle active, maintained code. Legacy drivers are excluded.
