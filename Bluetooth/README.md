# Shelly Bluetooth (BLU) Device Drivers for Hubitat

This folder contains Hubitat device drivers for Shelly BLU (Bluetooth) devices. These drivers enable you to use Shelly's battery-powered Bluetooth devices with your Hubitat Elevation hub by using a **BLE Gateway "relay" feature** — a compatible Shelly WiFi device acts as a Bluetooth-to-WiFi bridge, relaying BLE data to Hubitat.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture: How the BLE Relay Works](#architecture-how-the-ble-relay-works)
3. [Supported Bluetooth Devices](#supported-bluetooth-devices)
4. [Prerequisites](#prerequisites)
5. [Complete Setup Guide](#complete-setup-guide)
   - [Part 1: Set Up the BLE Gateway Device (Relay)](#part-1-set-up-the-ble-gateway-device-relay)
   - [Part 2: Create Bluetooth Device Drivers in Hubitat](#part-2-create-bluetooth-device-drivers-in-hubitat)
   - [Part 3: Verify and Test](#part-3-verify-and-test)
6. [Troubleshooting](#troubleshooting)
7. [Important Notes](#important-notes)
8. [Advanced: Manual Script Installation](#advanced-manual-script-installation)

---

## Overview

Shelly BLU devices are battery-powered Bluetooth sensors and buttons that **do not connect directly to Hubitat**. Instead, they broadcast their data via Bluetooth Low Energy (BLE). To use these devices with Hubitat, you need a **BLE Gateway** — a compatible Shelly WiFi device (such as a Shelly Plus 1, Plus Plug, Plus 2PM, etc.) that acts as a relay:

1. The BLE Gateway device listens for Bluetooth broadcasts from nearby Shelly BLU devices.
2. It decodes the BLE data and forwards it to Hubitat via HTTP.
3. The `ShellyBluetoothHelper` app in Hubitat receives the data and distributes it to the appropriate Bluetooth device drivers.

This "relay" architecture allows battery-powered BLE devices to integrate with Hubitat without requiring a direct Bluetooth connection from the hub.

---

## Architecture: How the BLE Relay Works

### Components

1. **Shelly BLU Device (e.g., Button1 Blu, Door/Window Blu, H&T Blu)**
   - Battery-powered Bluetooth device
   - Broadcasts sensor data or button presses via BLE

2. **BLE Gateway Device (Relay)**
   - A Shelly Gen2+ WiFi device with Bluetooth capability (e.g., Shelly Plus 1, Plus 1 PM, Plus 2PM, Plus Plug US)
   - Runs a JavaScript script (`HubitatBLEHelper`) that:
     - Scans for BLE advertisements from Shelly BLU devices
     - Decodes BTHome-formatted sensor data
     - Sends HTTP POST requests to Hubitat with the decoded data

3. **Hubitat Hub**
   - Runs the `ShellyBluetoothHelper` app, which:
     - Listens for location events from BLE Gateway devices
     - Routes data to the appropriate Bluetooth device driver based on MAC address
   - Bluetooth device drivers create virtual devices in Hubitat (e.g., button, contact sensor, temperature/humidity sensor)

### Data Flow

```
Shelly BLU Device (BLE broadcast)
    ↓
BLE Gateway Device (WiFi Shelly running HubitatBLEHelper script)
    ↓
Hubitat Hub (ShellyBluetoothHelper app)
    ↓
Bluetooth Device Driver (virtual device with capabilities)
```

---

## Supported Bluetooth Devices

The following Shelly BLU devices are supported:

- **Shelly Button1 (Blu)** — Single button with push, double-tap, triple-tap, hold, and release detection
- **Shelly BLU Wall Switch 4** — Four-button wall switch
- **Shelly Door/Window (Blu)** — Contact sensor (open/close) with illuminance and tilt
- **Shelly H&T (Blu)** — Temperature and humidity sensor
- **Shelly Motion (Blu)** — Motion sensor with illuminance

**Note:** Additional BLU devices may be supported — check the `Bluetooth/` folder for available drivers.

---

## Prerequisites

Before you begin, ensure you have:

1. **A Shelly Gen2+ WiFi Device with Bluetooth Support**
   Compatible devices include:
   - Shelly Plus 1 / Plus 1 PM / Plus 1 PM Mini
   - Shelly Plus 2PM
   - Shelly Plus Plug US
   - Shelly Plus i4
   - Shelly Plus Uni
   - Any other Gen2+ Shelly device with BLE capability

   **Important:** The gateway device must be running firmware **1.0.0 or newer** and must support Bluetooth.

2. **One or More Shelly BLU Devices**
   - Shelly Button1 (Blu), Door/Window (Blu), H&T (Blu), Motion (Blu), etc.

3. **Hubitat Package Manager (HPM) Installed** _(Recommended)_
   - HPM makes driver installation easier.
   - If not using HPM, you can manually import driver code from the GitHub repository.

4. **Your Hubitat Hub on the Same Local Network as the Shelly Devices**

5. **ShellyBluetoothHelper App Installed in Hubitat**
   - This app is required to route BLE data to device drivers.

---

## Complete Setup Guide

### Part 1: Set Up the BLE Gateway Device (Relay)

The BLE Gateway device is the WiFi-connected Shelly device that will relay Bluetooth data to Hubitat.

---

#### Step 1.1: Install the Gateway Device in Hubitat

1. **Install Drivers via HPM (Recommended)**
   - Open Hubitat web UI → **Apps** → **Hubitat Package Manager**
   - Click **Install** → Search for `Shelly` or `ShellyUSA`
   - Select **Shelly Device Drivers** and follow prompts to install all drivers and apps.

   **OR**

   **Install Manually**
   - Go to: **Drivers Code** → **New Driver**
   - Navigate to [https://github.com/ShellyUSA/Hubitat-Drivers](https://github.com/ShellyUSA/Hubitat-Drivers)
   - Find the appropriate driver for your gateway device (e.g., `ShellyPlus1.groovy` in the `WebhookWebsocket/` folder)
   - Copy the driver code and paste it into the Hubitat driver editor
   - Click **Save**

2. **Create a Virtual Device for the Gateway**
   - Go to: **Devices** → **Add Device** → **Virtual**
   - Enter a device name (e.g., `Shelly Plus 1 - BLE Gateway`)
   - Click **Save Device**

3. **Select the Appropriate Driver**
   - From the device page, find the **Type** dropdown
   - Select the correct driver (e.g., `Shelly Plus 1 & Plus 1 Mini`)
   - Click **Save Device**

4. **Configure Device Preferences**
   - On the device page, scroll to **Preferences**
   - Enter the device's **IP Address** (find this in your router or Shelly app)
   - **Optional:** Enter **Device Username** and **Device Password** if authentication is enabled on your Shelly device
   - **Enable Bluetooth Gateway for Hubitat**: Set to **ON** (this is the critical setting!)
   - Click **Save Preferences**

---

#### Step 1.2: Run Configure to Install the BLE Script

When you save preferences with **Enable Bluetooth Gateway for Hubitat** turned on, the driver will automatically:

1. Check if the device supports Bluetooth.
2. Enable Bluetooth on the Shelly device.
3. Download the latest `HubitatBLEHelper` JavaScript from GitHub.
4. Create a script named `HubitatBLEHelper` on the Shelly device.
5. Upload the JavaScript code to the device.
6. Enable and start the script.

**To trigger this process:**

- Scroll to the bottom of the device page in Hubitat
- Click the **Configure** button

**Monitor the Logs:**

- Go to **Logs** in Hubitat
- You should see messages like:
  ```
  Bluetooth Gateway functionality enabled, configuring device for bluetooth reporting to Hubitat...
  Enabling Bluetooth on Shelly device...
  Getting index of HubitatBLEHelper script, if it exists on Shelly device...
  HubitatBLEHelper script not found on device, creating script...
  Getting latest Shelly Bluetooth Helper script...
  Sending latest Shelly Bluetooth Helper to device...
  Enabling Shelly Bluetooth Helper on device...
  Starting Shelly Bluetooth Helper on device...
  Validating successful installation of Shelly Bluetooth Helper...
  Bluetooth Helper is installed, enabled, and running
  Successfully installed Shelly Bluetooth Helper on device...
  ```

**If you see errors:**

- **"Payload Too Large" or HTTP 413 Error:**
  This means the script upload failed due to size. See [Troubleshooting](#troubleshooting) for manual installation steps.

- **"Script is installed but not running":**
  Manually enable and start the script via the Shelly web interface (see [Advanced: Manual Script Installation](#advanced-manual-script-installation)).

---

#### Step 1.3: Verify Script Installation (Optional)

1. Open a web browser and navigate to your Shelly device's IP address (e.g., `http://192.168.1.100`)
2. Log in if required
3. Go to **Scripts** (or **Settings** → **Scripts** depending on firmware version)
4. You should see a script named **`HubitatBLEHelper`**
5. Verify:
   - **Enabled**: ✅ (checkbox is checked)
   - **Running**: ✅ (green indicator or "Running" status)

If the script is missing or disabled, see [Advanced: Manual Script Installation](#advanced-manual-script-installation).

---

#### Step 1.4: Install the ShellyBluetoothHelper App

The `ShellyBluetoothHelper` app is required to route BLE data from the gateway device to individual Bluetooth device drivers.

1. **Install via HPM (Recommended)**
   - If you installed drivers via HPM, the app is already installed.

   **OR**

   **Install Manually**
   - Go to: **Apps Code** → **New App**
   - Navigate to [https://github.com/ShellyUSA/Hubitat-Drivers/blob/master/Bluetooth/ShellyBluetoothHelper.groovy](https://github.com/ShellyUSA/Hubitat-Drivers/blob/master/Bluetooth/ShellyBluetoothHelper.groovy)
   - Copy the code and paste it into the Hubitat app editor
   - Click **Save**

2. **Add an Instance of the App**
   - Go to: **Apps** → **Add User App**
   - Select **Shelly Bluetooth Helper**
   - Configure logging preferences (optional)
   - Click **Done**

**The app will now listen for BLE events and route them to the appropriate device drivers.**

---

### Part 2: Create Bluetooth Device Drivers in Hubitat

Now that your BLE Gateway is set up, you can create virtual devices for your Shelly BLU devices.

---

#### Step 2.1: Install Bluetooth Device Drivers

1. **Install via HPM (Recommended)**
   - If you installed the full driver package via HPM, all Bluetooth drivers are already available.

   **OR**

   **Install Manually for Each Device Type**
   - Go to: **Drivers Code** → **New Driver**
   - Navigate to [https://github.com/ShellyUSA/Hubitat-Drivers/tree/master/Bluetooth](https://github.com/ShellyUSA/Hubitat-Drivers/tree/master/Bluetooth)
   - Find the appropriate driver file (e.g., `ShellyButton1Blu.groovy`)
   - Copy the code and paste it into the Hubitat driver editor
   - Click **Save**
   - Repeat for each Bluetooth device type you own

---

#### Step 2.2: Find the MAC Address of Your BLU Device

Each BLU device has a unique MAC address. You need this to create the device in Hubitat.

**Method 1: Check the Device Label or Packaging**

- The MAC address is often printed on the device or box (e.g., `A4:C1:38:XX:XX:XX`)

**Method 2: Use the Shelly App**

- Open the Shelly app on your phone
- Add the BLU device to the app
- View device details to find the MAC address

**Method 3: Check Hubitat Logs**

- Press a button or trigger the BLU device
- Go to **Logs** in Hubitat
- Look for messages from the `ShellyBluetoothHelper` app like:
  ```
  No device found for DNI/MAC address: A4C138XXXXXX, received button device event...
  ```
- The MAC address will be in the log message

**Important:** Remove colons from the MAC address when creating the device (e.g., `A4:C1:38:XX:XX:XX` becomes `A4C138XXXXXX`).

---

#### Step 2.3: Create a Virtual Device for the BLU Device

1. **Create the Device**
   - Go to: **Devices** → **Add Device** → **Virtual**
   - Enter a device name (e.g., `Shelly Button Blu - Kitchen`)
   - Click **Save Device**

2. **Set the Device Network ID (DNI) to the MAC Address**
   - On the device page, find the **Device Network Id** field
   - Enter the MAC address **without colons or dashes** (e.g., `A4C138XXXXXX`)
   - **Critical:** The DNI must exactly match the MAC address (case-insensitive, but no punctuation)
   - Click **Save Device**

3. **Select the Appropriate Driver**
   - From the **Type** dropdown, select the correct driver for your BLU device:
     - `Shelly Button 1 (Blu)`
     - `Shelly BLU Wall Switch 4`
     - `Shelly Door/Window (Blu)`
     - `Shelly H&T (Blu)`
     - `Shelly Motion (Blu)`
   - Click **Save Device**

4. **Configure Device Preferences (Optional)**
   - Some drivers have a **Presence Timeout** setting (default 300 seconds / 5 minutes)
   - This determines how long the device is marked as "present" after receiving data
   - Adjust if needed and click **Save Preferences**

5. **Verify Driver Initialization**
   - Check the **Current States** section on the device page
   - You should see attributes initialized (e.g., `numberOfButtons`, `presence`, `battery`)

---

#### Step 2.4: Repeat for Each BLU Device

Repeat Step 2.3 for each Shelly BLU device you want to add:

- **Shelly Button1 Blu** → `ShellyButton1Blu` driver
  - DNI: MAC address without colons
  - Capabilities: `PushableButton`, `DoubleTapableButton`, `HoldableButton`, `ReleasableButton`, `Battery`, `PresenceSensor`

- **Shelly BLU Wall Switch 4** → `ShellyBluWallSwitch` driver
  - DNI: MAC address without colons
  - Capabilities: Four buttons with push/hold/double-tap/triple-tap, `Battery`, `PresenceSensor`

- **Shelly Door/Window Blu** → `ShellyDoorWindowBlu` driver
  - DNI: MAC address without colons
  - Capabilities: `ContactSensor`, `IlluminanceMeasurement`, `Battery`, tilt angle

- **Shelly H&T Blu** → `ShellyH&TBlu` driver
  - DNI: MAC address without colons
  - Capabilities: `TemperatureMeasurement`, `RelativeHumidityMeasurement`, `Battery`

- **Shelly Motion Blu** → `ShellyMotionBlu` driver
  - DNI: MAC address without colons
  - Capabilities: `MotionSensor`, `IlluminanceMeasurement`, `Battery`

---

### Part 3: Verify and Test

#### Step 3.1: Trigger the BLU Device

- **Button Device:** Press the button
- **Door/Window Sensor:** Open or close the door/window
- **Motion Sensor:** Wave your hand in front of the sensor
- **H&T Sensor:** Wait for the next temperature/humidity broadcast (typically every few minutes)

---

#### Step 3.2: Check Hubitat Logs

Go to **Logs** in Hubitat and look for:

1. **Messages from the BLE Gateway Device:**

   ```
   [Gateway Device Name]: Received BLE event from [MAC Address]
   ```

2. **Messages from ShellyBluetoothHelper App:**

   ```
   Shelly Bluetooth Helper: Received button device event for MAC: A4C138XXXXXX
   Shelly Bluetooth Helper: Received battery report for MAC: A4C138XXXXXX
   ```

3. **State Changes in the BLU Device:**
   - Go to the BLU device page in Hubitat
   - Check **Current States** for updated values (e.g., `pushed`, `battery`, `contact`, `temperature`)
   - Check **Events** for recent activity

---

#### Step 3.3: Use the Device in Automations

Once the device is reporting data, you can use it in **Rule Machine**, **Button Controllers**, or other Hubitat apps:

- Button presses, holds, double-taps, triple-taps
- Contact sensor open/closed events
- Motion active/inactive events
- Temperature/humidity readings
- Battery level monitoring

---

## Troubleshooting

### Problem: BLE Script Installation Fails with "Payload Too Large" Error

**Symptom:**
When running **Configure**, you see an error in the logs:

```
Exception: groovyx.net.http.ResponseParseException: status code: 413, reason phrase: Payload Too Large
```

**Cause:**
The JavaScript file is too large to be uploaded in a single HTTP request.

**Solution:**
Manually install the script via the Shelly web interface. See [Advanced: Manual Script Installation](#advanced-manual-script-installation).

---

### Problem: BLE Device Events Not Appearing in Hubitat

**Symptom:**
You trigger a BLU device (button press, door open, motion), but nothing happens in Hubitat.

**Checklist:**

1. **Verify BLE Gateway Script is Running**
   - Open the Shelly device web UI (e.g., `http://192.168.1.100`)
   - Go to **Scripts**
   - Verify `HubitatBLEHelper` is **Enabled** and **Running**
   - If not running, click **Enable** and **Start**

2. **Check Device Network ID (DNI) Matches MAC Address**
   - Go to the BLU device page in Hubitat
   - Verify the **Device Network Id** exactly matches the MAC address **without colons** (e.g., `A4C138XXXXXX`)
   - Case does not matter, but ensure there are no spaces, dashes, or colons

3. **Verify ShellyBluetoothHelper App is Installed and Running**
   - Go to **Apps** in Hubitat
   - Ensure **Shelly Bluetooth Helper** is listed and active
   - If not, add it via **Add User App**

4. **Check Logs for Errors**
   - Go to **Logs** and look for:
     - `No device found for DNI/MAC address: [MAC], received button device event...`
     - This means the MAC address doesn't match any device DNI in Hubitat

5. **Verify BLE Gateway Device is Within Range**
   - BLU devices have a range of approximately **10-30 feet** (depending on walls and interference)
   - Move the BLE Gateway closer to the BLU device if needed

6. **Replace Battery**
   - Low battery can cause intermittent BLE broadcasts
   - Check the battery level attribute in Hubitat

---

### Problem: Script is Installed But Not Running

**Symptom:**
The script appears in the Shelly web UI but shows as "Stopped" or "Disabled".

**Solution:**

1. Open the Shelly web UI (e.g., `http://192.168.1.100`)
2. Go to **Scripts**
3. Find the `HubitatBLEHelper` script
4. Click the **Enable** toggle (if not already enabled)
5. Click **Start** (or the play icon)
6. Verify the script status changes to **Running**

---

### Problem: Multiple Duplicate Events

**Symptom:**
Button presses or sensor events trigger multiple times in quick succession.

**Cause:**
BLE devices may broadcast the same event multiple times, or multiple BLE Gateway devices may be receiving the same broadcast.

**Solutions:**

1. **Check for Multiple Gateway Devices**
   - Ensure only **one** BLE Gateway device is running the `HubitatBLEHelper` script
   - If you have multiple gateway devices, disable BLE Gateway functionality on all but one

2. **Adjust Debounce Logic**
   - The `ShellyBluetoothHelper` app includes logic to prevent duplicate events within 1 second
   - If you still see duplicates, check Hubitat logs for unusual behavior

---

### Problem: "No Response" or Presence Sensor Shows "Not Present"

**Symptom:**
The BLU device's presence sensor always shows "not present" or the device stops responding.

**Cause:**
BLU devices are battery-powered and only broadcast data periodically or when triggered.

**Solutions:**

1. **Adjust Presence Timeout**
   - Go to the BLU device page in Hubitat
   - Set **Presence Timeout** to a higher value (e.g., 600 seconds / 10 minutes for infrequently-used devices)
   - Click **Save Preferences**

2. **Trigger the Device**
   - Press the button, open/close the door, or trigger motion
   - The presence sensor will update to "present"

3. **Battery Low**
   - Check the battery level attribute
   - Replace the battery if below 20%

---

## Important Notes

### Battery Life and BLE Broadcast Frequency

- **Shelly BLU devices are designed for long battery life** (typically 1-2 years depending on usage)
- They do **not** broadcast continuously — data is sent:
  - On button press, door open/close, motion detection (event-driven)
  - Periodically for sensors (e.g., every 2-5 minutes for temperature/humidity)
- The **Presence Sensor** capability is used to detect if a device has been silent for too long (indicating a dead battery or out-of-range condition)

### MAC Address Formatting

- **Critical:** The Device Network ID (DNI) in Hubitat **must** match the MAC address of the BLU device **without colons, dashes, or spaces**.
- Example:
  - MAC Address: `A4:C1:38:12:34:56`
  - DNI in Hubitat: `A4C13812345` (all caps, no punctuation)
- The `ShellyBluetoothHelper` app strips colons from MAC addresses before matching, so the DNI must also have no colons.

### Range Limitations

- **BLE range is typically 10-30 feet** (3-10 meters) depending on walls and interference.
- If a BLU device is out of range of all BLE Gateway devices, it will not report to Hubitat.
- **Solution:** Add additional BLE Gateway devices to extend coverage, or move the gateway closer to the BLU devices.

### Multiple BLE Gateway Devices

- You can have **multiple BLE Gateway devices** to extend BLE coverage.
- Each gateway device should run the `HubitatBLEHelper` script.
- The `ShellyBluetoothHelper` app includes debounce logic to prevent duplicate events if multiple gateways receive the same BLE broadcast.

### Firmware Requirements

- **BLE Gateway devices must be running Gen2+ firmware (1.0.0 or newer)**.
- Older Gen1 Shelly devices (e.g., Shelly 1, Shelly 2.5) **do not** support Bluetooth and cannot act as BLE Gateways.

---

## Advanced: Manual Script Installation

If automatic script installation fails (e.g., due to "Payload Too Large" errors), you can manually install the `HubitatBLEHelper` script.

### Step 1: Download the JavaScript File

1. Open [https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Bluetooth/ble-shelly-blu.js](https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Bluetooth/ble-shelly-blu.js) in your browser.
2. Copy the entire JavaScript code (Ctrl+A, Ctrl+C / Cmd+A, Cmd+C).

### Step 2: Access the Shelly Web UI

1. Open a web browser and navigate to your Shelly device's IP address (e.g., `http://192.168.1.100`).
2. Log in if required.

### Step 3: Create the Script

1. Go to **Scripts** (or **Settings** → **Scripts**).
2. Click **Add Script** or **Create New Script**.
3. Enter the script name: `HubitatBLEHelper`.
4. Paste the JavaScript code you copied in Step 1.
5. Click **Save**.

### Step 4: Modify the Script Configuration (Optional)

At the top of the JavaScript file, you'll see:

```javascript
const CONFIG = {
  // Specify the destination event where the decoded BLE data will be emitted
  eventName: "shelly-blu",

  // If this value is set to true, the scan will be active
  // If this value is set to false, the scan will be passive
  active: false,

  // When set to true, debug messages will be logged to the console
  debug: false,
};
```

**You can modify:**

- `active: false` — Set to `true` for active BLE scanning (drains BLU device batteries faster but may improve reliability).
- `debug: false` — Set to `true` to enable debug logging in the Shelly device console.

**Note:** You must modify the script code **before** pasting it into the Shelly web UI, or edit it directly in the web UI after pasting.

### Step 5: Enable and Start the Script

1. In the Shelly web UI, find the `HubitatBLEHelper` script in the scripts list.
2. Toggle **Enable** to **ON**.
3. Click **Start** (or the play icon).
4. Verify the status changes to **Running**.

### Step 6: Verify in Hubitat

1. Trigger a BLU device (button press, door open, motion).
2. Check **Logs** in Hubitat for events from the `ShellyBluetoothHelper` app and the BLU device driver.

---

## Summary: Quick Setup Checklist

- [ ] Install ShellyUSA drivers and apps via HPM (or manually)
- [ ] Create a virtual device for your BLE Gateway device (e.g., Shelly Plus 1)
- [ ] Select the appropriate driver (e.g., `Shelly Plus 1 & Plus 1 Mini`)
- [ ] Enter the gateway device's IP address in preferences
- [ ] Enable **Bluetooth Gateway for Hubitat** preference
- [ ] Click **Save Preferences** and then **Configure**
- [ ] Verify `HubitatBLEHelper` script is installed, enabled, and running on the Shelly device
- [ ] Install the **ShellyBluetoothHelper** app in Hubitat
- [ ] For each BLU device:
  - [ ] Create a virtual device
  - [ ] Set Device Network ID (DNI) to MAC address without colons (e.g., `A4C138XXXXXX`)
  - [ ] Select the appropriate driver (e.g., `Shelly Button 1 (Blu)`)
  - [ ] Trigger the device and verify events appear in Hubitat

---

## Support and Contributions

- **GitHub Repository:** [https://github.com/ShellyUSA/Hubitat-Drivers](https://github.com/ShellyUSA/Hubitat-Drivers)
- **Hubitat Community Forum:** [https://community.hubitat.com/](https://community.hubitat.com/)
- **Issues and Feature Requests:** Open an issue on the GitHub repository

---

**Enjoy your Shelly BLU devices with Hubitat!** 🎉
