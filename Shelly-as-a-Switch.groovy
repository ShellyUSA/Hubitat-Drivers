/**
 *
 *  Shelly Switch Relay Driver
 *
 *  Copyright © 2018-2019 Scott Grayban
 *  Copyright © 2020 Allterco Robotics US
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 * Supported devices are:
 *   1/1PM/2/2.5/EM/Plug/PlugS/4Pro/EM3/SHPLG-U1
 *
 *  Changes:
 *  3.0.8 - Fixed missing directive for EM3 meters
 *  3.0.7 - Added external temperature and humidity attributes.
 *        - ctraf settings removed from EM3
 *  3.0.6 - Added VoltageMeasurement for the 4Pro input voltage
 *        - Fixed a issue where the ntp server and devicename would be deleted if the relay # was not 0
 *  3.0.5 - Added support for the Shelly Plug US
 *  3.0.4 - Added support for External Sensor Hat for the Shelly1 and 1PM
 *  3.0.3 - Added support for the ShellEM 3 phase
 *  3.0.2 - Fixed a bug with Shelly EM/4PRO to not set a relayname for the second EM channel
 *        - Fixed excessive logging chatter
 *        - Added NTP server preference
 *        - Added manual/poll for refreshing
 *  3.0.1 - Added switch to prevent(protect) accidental power off or on. Simular to the phone app pin code.
 *        - Added Mains or Battery for power source for the Shelly 1 and 2.5
 *        - Fixed the secure login username password (!!Note: Passwords must not contain these special characters &?)
 *        - Added code that will update Maxpower setting if changed via app or embedded webserver
 *        - Prevent any settings from being set, shown or retrieved until IP is set
 *        - Only show correct device settings *after* IP is saved since all settings do not apply to all shelly devices
 *        - More code for Plug and PlugS support
 *        - Added missing code to turn off JSON debugging after set time
 *        - You can now set the device name and relay channel name. EG; DeviceName can be a location/room and RelayName can be light, appliance or a power strip controler
 *        - Many thanks to Dawid Filo for his help in debuging the Plug(S) code
 *  3.0.0 - Fixed some bug regarding the getDataValue which would prevent the driver from working correctly
 *  2.0.9 - Added the ability to update device FW
 *  2.0.8 - RSSI value is definded as exelent, good or poor. The actual rssi reading is under state variables.
 *  2.0.7 - Added MaxPower to attributes
 *        - Added preference setting for max_power for relay channel
 *        - Removed getShellyAddress()
 *        - Added ctraf setting for Shelly EM Current transformer type
 *        - Changed powerTotal to energy attribute to match the capability "EnergyMeter"
 *        - Removed importURL
 *        - Code located at https://gitlab.borgnet.us:8443/sgrayban/shelly-drivers/tree/master/Drivers/Shelly
 *  2.0.6 - Added new tC/tF attributes
 *        - Added deviceType for device identification for current/future code
 *        - ShellyEM support
 *        - Added new capability VoltageMeasurement
 *        - ShellyPlug support
 *  2.0.5 - Code added for JSON debug switch
 *        - New attributes that can be used in RM
 *        - 1 call for API status now
 *        - Supports all relay channels 0-3
 *        - Supports username/password
 *        - Added the ability to reboot device
 *  2.0.4 - Code added for all Shelly relay switches
 *  2.0.3 - Changed operand for refresh rate in update()
 *  2.0.2 - Changed how the update check worked if refresh rate was set to No Selection
 *  2.0.1 - Modified code to allow install more then once - added version control
 *  2.0.0 - Removed more ST code and added auto refresh option and debugging info switches.
 *  1.0.0 - Initial port
 *
 */

import groovy.json.*
import groovy.transform.Field

def setVersion(){
	state.Version = "3.0.8"
	state.InternalName = "ShellyAsASwitch"
}

metadata {
	definition (
		name: "Shelly Switch Relay",
		namespace: "ShellyUSA",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-as-a-Switch.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Switch"
        capability "RelaySwitch"
        capability "Polling"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "SignalStrength"
        capability "PowerSource"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "VoltageMeasurement"
        
        attribute "FW_Update_Needed", "string"
        attribute "LastRefresh", "string"
        attribute "power", "number"
        attribute "overpower", "string"
        attribute "internal_tempC", "number"
        attribute "internal_tempF", "number"
        attribute "DeviceOverTemp", "string"
        attribute "MAC", "string"
        attribute "RelayChannel", "number"
        attribute "Primary_IP", "string"
        attribute "Primary_SSID", "string"
        attribute "Secondary_IP", "string"
        attribute "Secondary_SSID", "string"
        attribute "WiFiSignal", "string"
        attribute "Cloud", "string"
        attribute "Cloud_Connected", "string"
        attribute "energy", "number"
        attribute "DeviceType", "string"
        attribute "eMeter", "number"
        attribute "reactive", "number"
        attribute "MaxPower", "number"
        attribute "CircuitAmp", "string"
        attribute "LED_Output", "string"
        attribute "LED_NetworkStatus", "string"
        attribute "DeviceName", "string"
        attribute "RelayName", "string"
        attribute "NTPServer", "string"
        attribute "ext2_temperature", "number"
        attribute "ext2_humidity", "number"
        attribute "ext3_temperature", "number"
        attribute "ext3_humidity", "number"
        
        command "RebootDevice"
        command "UpdateDeviceFW" // ota?update=1
        //command "updatecheck" // Only used for development
        command "getSettings"
	}

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		refreshRate << ["manual" : "Manually or Polling Only"]

	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    if (ip == null) input("channel", "number", title:"Relay Channel", description:"0,1,2,or 3 :", defaultValue:"0" , required: true) // Show at device setup only

    if (ip != null) { // show settings *after* IP is set as some settings do not apply to all devices

    if (getDataValue("model") in ["SHEM","SHEM-3"]) channel = 0 // The EM devices only have 1 relay

	if (channel < 1) input name: "ntp_server", type: "text", title: "NTP time server:", description: "E.G. time.google.com or 192.168.0.59", defaultValue: "time.google.com", required: true

    // Only show for channel 0 since the device name is for the entire device
	if (channel < 1) input name: "devicename", type: "text", title: "Give your device a name:", description: "EG; Location/Room<br>NO SPACES in name", required: false
        
    if (!(getDataValue("model") in ["SHPLG-S","SHPLG-1","SHEM","SHEM-3","SHPLG-U1"])) { // The Plug devices do not offer a relay name
        input name: "relayname", type: "text", title: "Label your relay control:", description: "EG; Light/Appliance<br>NO SPACES in name", required: false
    }
    if (channel != 1 && getDataValue("model") in ["SHEM","SHEM-3"])
        input name: "relayname", type: "text", title: "Label your relay control:", description: "EG; Light/Appliance<br>NO SPACES in name", required: false

    input "protect", "enum", title:"Prevent accidental off/on", defaultValue: true, options: [Yes:"Yes",No:"No"], required: true
        
    if (!(getDataValue("model") in ["SHDM-1","SHSW-PM","SHSW-1","SHEM","SHEM-3"])) {
        input("channel", "number", title:"Relay Channel", description:"0,1,2,or 3 :", defaultValue:"0" , required: true)
    }
    if (getDataValue("model") in ["SHEM","SHEM-3"]) {
        input("eMeter", "number", title:"eMeter Channel", description:"0, 1 or 2 :", defaultValue:"0" , required: true)
    }
    if (getDataValue("model") == "SHEM") {
        input("ctraf", "number", title:"Current Amperage(ctraf)", description:"50 or 120:", defaultValue:"50" , required: true)
    }
    if (getDataValue("model") in ["SHSW-25","SHSW-1"]) {
        input("powersource", "enum", title:"Mains/Battery", description:"Shelly Power Source", defaultValue: true, options: [mains:"Mains",battery:"Battery"], required: true)
    }
    if (getDataValue("model") in ["SHSW-1","SHSW-PM"]) {
        input name: "external_sensors", type: "bool", title: "Use External Sensor?", defaultValue: false
    }
    if (getDataValue("model") in ["SHPLG-S","SHPLG-1","SHPLG-U1"]) {
        input("led_status", "enum", title:"LED indication for network status", description:"Enable/Disable", defaultValue: false, options: [false:"Enable",true:"Disable"])
        input("led_power", "enum", title:"LED indication for output status", description:"Enable/Disable", defaultValue: false, options: [false:"Enable",true:"Disable"])
        input("maxpower", "number", title:"Max Power", description:"Max power allowed for this relay is 2500W", defaultValue:"2500" , required: true)
        state.RelayName = "N/A"
        sendEvent(name: "RelayName", value: state.RelayName)
        updateDataValue("RelayName", state.RelayName)
    }
    if (!(getDataValue("model") in ["SHSW-1","SHEM","SHDM-1","SHPLG-S","SHPLG-1","SHPLG-U1"])) {
        input("maxpower", "number", title:"Max Power", description:"Max power allowed for this relay is 2300W", defaultValue:"2300" , required: true)
    }
    if (!(getDataValue("model") in ["SHEM","SHEM-3"]))
        input("refresh_Rate", "enum", title: "Device Refresh Rate", description:"<font color=red>!!WARNING!!</font><br>DO NOT USE if you have over 50 Shelly devices.", options: refreshRate, defaultValue: "manual")
    if (getDataValue("model") in ["SHEM","SHEM-3"]) input("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "1 min")
        input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"]
} // END IP check

    input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs are located</center>", 
        description: "<center><br><a href='http://shelly-api-docs.shelly.cloud/' title='shelly-api-docs.shelly.cloud' target='_blank'>[here]</a></center>"
	}
}

def initialize() {
	log.info "initialize"
	if (txtEnable) log.info "initialize"
}

def installed() {
    log.debug "Installed"
    state.DeviceName = "NotSet"
    state.RelayName = "NotSet"
}

def uninstalled() {
    unschedule()
    log.debug "Uninstalled"
}

def updated() {
    if (txtEnable) log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    log.warn "Switch protection is: ${settings?.protect}"
    unschedule()
    dbCleanUp()
    
    if (ip != null) { // Don't set until IP is saved
    if (!(getDataValue("model") in ["SHSW-1","SHEM"])) {
        sendSwitchCommand "/settings/relay/${channel}?max_power=${maxpower}"
    }
    if (getDataValue("model") in ["SHPLG-1","SHPLG-S","SHPLG-U1"]) {
        logDebug "LED setting ${led_status} and ${led_power}"
        sendSwitchCommand "/settings?led_status_disable=${led_status}"
        sendSwitchCommand "/settings?led_power_disable=${led_power}"
    }
    if (channel < 1) sendSwitchCommand "/settings?sntp_server=${ntp_server}"
// Set device and relay name
    if (channel < 1) sendSwitchCommand "/settings?name=${devicename}"
    if (!(getDataValue("model") in ["SHPLG-S","SHPLG-1","SHPLG-U1"])) sendSwitchCommand "/settings/relay/${channel}?name=${relayname}"

    if (getDataValue("model") == "SHEM") sendSwitchCommand "/settings/relay/${channel}?ctraf_type=${ctraf_type}"
    }
    
    switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(autorefresh)
			break
		case "5 min" :
			runEvery5Minutes(autorefresh)
			break
		case "15 min" :
			runEvery15Minutes(autorefresh)
			break
		case "30 min" :
			runEvery30Minutes(autorefresh)
			break
		case "manual" :
			unschedule(autorefresh)
            log.info "Autorefresh disabled"
            break
	}
	if (txtEnable) log.info ("Auto Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff) //Off in 30 minutes
    if (debugParse) runIn(300,logsOff) //Off in 5 minutes
    
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)

    version()
    refresh()
    getSettings()
}

private dbCleanUp() {
	state.remove("version")
	state.remove("Version")
	state.remove("ShellyfwUpdate")
	state.remove("power")
	state.remove("overpower")
	state.remove("dcpower")
	state.remove("max_power")
	state.remove("internal_tempC")
	state.remove("Status")
    state.remove("max_power")
    state.remove("RelayName")
}

def refresh(){
    if (ip != null) { // Don't set until IP is saved
    logDebug "Shelly Status called"
    getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/status"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        state.powerSource = settings?.powersource
        sendEvent(name: "powerSource", value: state.powerSource)
        state.RelayChannel = channel
        sendEvent(name: "RelayChannel", value: state.RelayChannel)
        
        if (obs.temperature != null) sendEvent(name: "internal_tempC", value: obs.temperature)
        if (obs.tmp != null) {
            sendEvent(name: "internal_tempC", unit: "C", value: obs.tmp.tC)
            sendEvent(name: "internal_tempF", unit: "F", value: obs.tmp.tF)
        }
        if (obs.overtemperature != null) sendEvent(name: "DeviceOverTemp", value: obs.overtemperature)
        
        if (obs.wifi_sta != null) {
        state.rssi = obs.wifi_sta.rssi
        state.ssid = obs.wifi_sta.ssid
        state.ip = obs.wifi_sta.ip
        sendEvent(name: "Primary_SSID", value: state.ssid)
        sendEvent(name: "Primary_IP", value: state.ip)
        }
        
/*
-30 dBm	Excellent | -67 dBm	Good | -70 dBm	Poor | -80 dBm	Weak | -90 dBm	Dead
*/
        signal = state.rssi
        if (signal <= 0 && signal >= -70) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Excellent</font>", isStateChange: true);
        } else
        if (signal < -70 && signal >= -80) {
            sendEvent(name:  "WiFiSignal", value: "<font color='green'>Good</font>", isStateChange: true);
        } else
        if (signal < -80 && signal >= -90) {
            sendEvent(name: "WiFiSignal", value: "<font color='yellow'>Poor</font>", isStateChange: true);
        } else 
        if (signal < -90 && signal >= -100) {
            sendEvent(name: "WiFiSignal", value: "<font color='red'>Weak</font>", isStateChange: true);
        }

        state.mac = obs.mac
        sendEvent(name: "MAC", value: state.mac)
        sendEvent(name: "rssi", value: state.rssi)
        
// Shelly EM emeters
        if (state.DeviceType == "SHEM" || state.DeviceType == "SHEM-3") {
        if (eMeter == 0 )sendEvent(name: "power", value: obs.emeters.power[0])
        if (eMeter == 0 )sendEvent(name: "voltage", value: obs.emeters.voltage[0])
        if (eMeter == 0 )sendEvent(name: "reactive", value: obs.emeters.reactive[0])
        if (eMeter == 0 )sendEvent(name: "energy", value: obs.emeters.total[0])

        if (eMeter == 1 )sendEvent(name: "power", value: obs.emeters.power[1])
        if (eMeter == 1 )sendEvent(name: "voltage", value: obs.emeters.voltage[1])
        if (eMeter == 1 )sendEvent(name: "reactive", value: obs.emeters.reactive[1])
        if (eMeter == 1 )sendEvent(name: "energy", value: obs.emeters.total[1])

        if (state.DeviceType == "SHEM-3") {
        if (eMeter == 2 )sendEvent(name: "power", value: obs.emeters.power[2])
        if (eMeter == 2 )sendEvent(name: "voltage", value: obs.emeters.voltage[2])
        if (eMeter == 2 )sendEvent(name: "reactive", value: obs.emeters.reactive[2])
        if (eMeter == 2 )sendEvent(name: "energy", value: obs.emeters.total[2])
        }
        state.eMeter = eMeter
        sendEvent(name: "eMeter", value: state.eMeter)
        }

// Device FW Updates
        state.has_update = obs.has_update
        if (state.has_update == true) {
            if (txtEnable) log.info "sendEvent NEW SHELLY FIRMWARE"
            sendEvent(name: "FW_Update_Needed", value: "<font color='red'>FIRMWARE Update Required</font>")
        }
        
        if (state.has_update == false) {
            if (txtEnable) log.info "sendEvent Device FW is current"
            sendEvent(name: "FW_Update_Needed", value: "<font color='green'>Device FW is current</font>")
        }

// Cloud
        state.cloud = obs.cloud.enabled
        if (state.cloud == true) {
            sendEvent(name: "Cloud", value: "<font color='green'>Enabled</font>")
        } else {
            sendEvent(name: "Cloud", value: "<font color='red'>Disabled</font>")
        }
        
        state.cloudConnected = obs.cloud.connected
        if (state.cloudConnected == true) {
            sendEvent(name: "Cloud_Connected", value: "<font color='green'>Connected</font>")
        } else {
            sendEvent(name: "Cloud_Connected", value: "<font color='red'>Not Connected</font>")
        }
        
// Relays
        if (obs.relays != null) {
        if (channel ==0) ison = obs.relays.ison[0]
        if (channel ==1) ison = obs.relays.ison[1]
        if (channel ==2) ison = obs.relays.ison[2]
        if (channel ==3) ison = obs.relays.ison[3]
        if (ison == true) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
        }
        }
// Power Meters
        if (obs.meters != null) {
        if (channel ==0) power = obs.meters.power[0]
        if (channel ==1) power = obs.meters.power[1]
        if (channel ==2) power = obs.meters.power[2]
        if (channel ==3) power = obs.meters.power[3]
        if (power != null) sendEvent(name: "power", unit: "W", value: power)

// Power Totals
        if (channel ==0) powerTotal = obs.meters.total[0]
        if (channel ==1) powerTotal = obs.meters.total[1]
        if (channel ==2) powerTotal = obs.meters.total[2]
        if (channel ==3) powerTotal = obs.meters.total[3]
        if (powerTotal != null) sendEvent(name: "energy", unit: "W", value: powerTotal)
        }

// Over Power
        if (obs.relays != null) {
        if (channel ==0) overpower = obs.relays.overpower[0]
        if (channel ==1) overpower = obs.relays.overpower[1]
        if (channel ==2) overpower = obs.relays.overpower[2]
        if (channel ==3) overpower = obs.relays.overpower[3]
        if (overpower != null) sendEvent(name: "overpower", value: overpower)
        }

// These devices don't offer the
// battery or mains option
// so the default is set to mains
        if (getDataValue("model") in ["SHEM","SHEM-3"]) {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHSW-44") {
            state.powerSource = "mains"
            sendEvent(name: "voltage", value: obs.voltage)
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHSW-PM") {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHSW-21") {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHPLG-1") {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHPLG-S") {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }
        if (getDataValue("model") == "SHPLG-U1") {
            state.powerSource = "mains"
            sendEvent(name: "powerSource", value: "mains")
        }

// Externel Sensors Shelly 1 and 1PM only
        if (external_sensors) {
            if (obs.ext_sensors != null) {
                t_unit = obs.ext_sensors.temperature_unit
                state.temperature_unit = t_unit

                if (obs.ext_temperature['0'] != null) sendEvent(name: "temperature", unit: t_unit, value: obs.ext_temperature['0']."t${t_unit}")
                if (obs.ext_humidity['0'] != null) sendEvent(name: "humidity", value: obs.ext_humidity['0'].hum)

                if (obs.ext_temperature['1'] != null) sendEvent(name: "ext2_temperature", unit: t_unit, value: obs.ext_temperature['1']."t${t_unit}")
                if (obs.ext_humidity['1'] != null) sendEvent(name: "ext2_humidity", value: obs.ext_humidity['1'].hum)

                if (obs.ext_temperature['2'] != null) sendEvent(name: "ext3_temperature", unit: t_unit, value: obs.ext_temperature['2']."t${t_unit}")
                if (obs.ext_humidity['2'] != null) sendEvent(name: "ext3_humidity", value: obs.ext_humidity['2'].hum)
            }
        }
} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    } // End if !==ip      
} // End Refresh Status


// Get shelly device type
def getSettings(){
    if (ip != null) { // Don't set until IP is saved
    logDebug "Get Shelly Settings"
    def paramsSettings = [uri: "http://${username}:${password}@${ip}/settings"]

try {
    httpGet(paramsSettings) {
        respSettings -> respSettings.headers.each {
        logJSON "ResponseSettings: ${it.name} : ${it.value}"
    }
        obsSettings = respSettings.data

        logJSON "params: ${paramsSettings}"
        logJSON "response contentType: ${respSettings.contentType}"
	    logJSON "response data: ${respSettings.data}"

        state.DeviceType = obsSettings.device.type
        if (state.DeviceType == "SHSW-1") sendEvent(name: "DeviceType", value: "Shelly 1")
        if (state.DeviceType == "SHSW-PM") sendEvent(name: "DeviceType", value: "Shelly 1PM")
        if (state.DeviceType == "SHSW-21") sendEvent(name: "DeviceType", value: "Shelly 2")
        if (state.DeviceType == "SHSW-25") sendEvent(name: "DeviceType", value: "Shelly 2.5")
        if (state.DeviceType == "SHSW-44") sendEvent(name: "DeviceType", value: "Shelly 4Pro")
        if (state.DeviceType == "SHEM") sendEvent(name: "DeviceType", value: "Shelly EM")
        if (state.DeviceType == "SHEM-3") sendEvent(name: "DeviceType", value: "Shelly EM3")
        if (state.DeviceType == "SHPLG-1") sendEvent(name: "DeviceType", value: "Shelly Plug")
        if (state.DeviceType == "SHPLG-S") sendEvent(name: "DeviceType", value: "Shelly PlugS")
        if (state.DeviceType == "SHPLG-U1") sendEvent(name: "DeviceType", value: "Shelly Plug US")


        state.ShellyHostname = obsSettings.device.hostname
        state.sntp_server = obsSettings.sntp.server
        sendEvent(name: "NTPServer", value: state.sntp_server)

        // Plug and PlugS have network and output LED indicator lights
        if (obsSettings.led_status_disable != null) {
            if (obsSettings.led_status_disable == false) {
                sendEvent(name: "LED_NetworkStatus", value: "<font color='green'>Enabled</font>")
            } else {
                sendEvent(name: "LED_NetworkStatus", value: "<font color='red'>Disabled</font>")
            }
            if (obsSettings.led_power_disable == false) {
                sendEvent(name: "LED_Output", value: "<font color='green'>Enabled</font>")
            } else {
                sendEvent(name: "LED_Output", value: "<font color='red'>Disabled</font>")
            }
        }

//Get Device name
       if (obsSettings.name != "NotSet") {
           state.DeviceName = obsSettings.name
           sendEvent(name: "DeviceName", value: state.DeviceName)
           updateDataValue("DeviceName", state.DeviceName)
           if (txtEnable) log.info "DeviceName is ${obsSettings.name}"
       } else if (obsSettings.name != null) {
           state.DeviceName = "NotSet"
           sendEvent(name: "DeviceName", value: state.DeviceName)
           if (txtEnable) log.info "DeviceName is ${obsSettings.name}"
       }
//Get Relay name
        if (getDataValue("model") != "SHPLG-S" && getDataValue("model") != "SHPLG-1" && getDataValue("model") != "SHPLG-U1" && getDataValue("model") != "SHEM") {
            if (obsSettings.relays != null) {
                if (channel == 0) relay_name = obsSettings.relays.name[0]
                if (channel == 1) relay_name = obsSettings.relays.name[1]
                if (channel == 2) relay_name = obsSettings.relays.name[2]
                if (channel == 3) relay_name = obsSettings.relays.name[3]
                if (relay_name != null) {
                    state.RelayName = relay_name
                    sendEvent(name: "RelayName", value: state.RelayName)
                    if (txtEnable) log.info "RelayName is ${relay_name}"
                } else {
                    state.RelayName = "NotSet"
                    sendEvent(name: "RelayName", value: state.RelayName)
                    if (txtEnable) log.info "RelayName is ${relay_name}"
                }
                updateDataValue("RelayName", state.RelayName)
            }
        } // The Plug devices do not offer a relay name

        if (getDataValue("model") == "SHEM") {
            state.RelayName = obsSettings.relays.name[0]
            sendEvent(name: "RelayName", value: state.RelayName)
            updateDataValue("RelayName", state.RelayName)
        }
        
        if (obsSettings.wifi_sta1 != null) {
        state.rssi = obsSettings.wifi_sta1.rssi
        state.Secondary_ssid = obsSettings.wifi_sta1.ssid
        state.Secondary_IP = obsSettings.wifi_sta1.ip
            if (obsSettings.wifi_sta1.enabled == true) sendEvent(name: "Secondary_SSID", value: state.Secondary_ssid)
            if (state.Secondary_IP != null) sendEvent(name: "Secondary_IP", value: state.Secondary_IP)
        }
        // Max watt setting
        if (obsSettings.relays.max_power != null) {
            if (channel ==0) max_power = obsSettings.relays.max_power[0]
            if (channel ==1) max_power = obsSettings.relays.max_power[1]
            if (channel ==2) max_power = obsSettings.relays.max_power[2]
            if (channel ==3) max_power = obsSettings.relays.max_power[3]
            if (max_power == null) max_power = 0

            if (txtEnable) log.info "Max power is set to ${max_power} Watts"
            sendEvent(name: "MaxPower", unit: "W", value: max_power)
            state.max_power = max_power
            device.updateSetting("maxpower",[value: max_power, type:"number"])
        }
        // Circuit Amperage Setting ShellyEM ONLY
        if (getDataValue("model") == "SHEM") {
            if (channel ==0) ctraf_type = obsSettings.emeters.ctraf_type[0]
            if (channel ==1) ctraf_type = obsSettings.emeters.ctraf_type[1]

            if (txtEnable) log.info "Circuit Amperage is set to ${ctraf} Amps"
            sendEvent(name: "CircuitAmp", unit: "amp" , value: "${ctraf}")
            state.ctraf_type = ctraf_type
        }
        
        logDebug "updating data values"
        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", state.ip)
        updateDataValue("ShellySSID", obsSettings.wifi_sta.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)
        updateDataValue("DeviceName", state.DeviceName)

} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    } // End if !==ip      
} // End Refresh Status


//switch.on
def on() {
    if (protect == "No") {
        logDebug "Executing switch.on"
        sendSwitchCommand "/relay/${channel}?turn=on"
    }
    if (protect == "Yes") {
        sendEvent(name: "switch", value: "<font color='red'>LOCKED</font>")
        runIn(1, refresh)
    }
}

//switch.off
def off() {
    if (protect == "No") {
        logDebug "Executing switch.off"
        sendSwitchCommand "/relay/${channel}?turn=off"
    }
    if (protect == "Yes") {
        sendEvent(name: "switch", value: "<font color='red'>LOCKED</font>")
        runIn(1, refresh)
    }
}

def ping() {
	logDebug "ping"
	poll()
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def autorefresh() {
    if (locale == "UK") {
	logDebug "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'auto refresh'" //RK

    refresh()
}

private logJSON(msg) {
	if (settings?.debugParse || settings?.debugParse == null) {
	log.info "$msg"
	}
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "$msg"
	}
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	logDebug "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'" //RK
	refresh()
}

def sendSwitchCommand(action) {
    if (txtEnable) log.info "Calling ${action}"
    def params = [uri: "http://${username}:${password}@${ip}/${action}"]
try {
    httpPost(params) {
        resp -> resp.headers.each {
        logDebug "Response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "something went wrong: $e"
    }
    runIn(2, refresh)
}

def RebootDevice() {
    if (txtEnable) log.info "Rebooting Device"
    def params = [uri: "http://${username}:${password}@${ip}/reboot"]
try {
    httpPost(params) {
        resp -> resp.headers.each {
        logDebug "Response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "something went wrong: $e"
    }
    runIn(15,refresh)
}

def UpdateDeviceFW() {
    if (txtEnable) log.info "Updating Device FW"
    def params = [uri: "http://${username}:${password}@${ip}/ota?update=1"]
try {
    httpPost(params) {
        resp -> resp.headers.each {
        logDebug "Response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "something went wrong: $e"
    }
    runIn(30,refresh)
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (github CobraVmax) for his original code **************
def version(){
	updatecheck()
	schedule("0 0 18 1/1 * ? *", updatecheck) // Cron schedule
//	schedule("0 0/1 * 1/1 * ? *", updatecheck) // Test Cron schedule
}

def updatecheck(){
	setVersion()
    def paramsUD = [uri: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/resources/version.json", contentType: "application/json; charset=utf-8"]
    try {
        httpGet(paramsUD) { respUD ->
            if (debugParse) log.debug " Version Checking - Response Data: ${respUD.data}"
            def copyrightRead = (respUD.data.copyright)
            state.Copyright = copyrightRead
            def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
            def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
            def currentVer = state.Version.replace(".", "")
            state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
            state.author = (respUD.data.author)
            state.icon = (respUD.data.icon)
            if(newVer == "NLS"){
                state.DriverStatus = "<b>** This driver is no longer supported by $state.author  **</b>"
                log.warn "** This driver is no longer supported by $state.author **"
            } else
                if(newVer == "BETA"){
                state.Status = "<b>** THIS IS BETA CODE  **</b>"
                log.warn "** BETA CODE **"
            } else
                if(currentVer < newVer){
                state.DriverStatus = "<b>New Version Available (Version: $newVerRaw)</b>"
                log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
                log.warn "** $state.UpdateInfo **"
            } else
                if(currentVer > newVer){
                state.DriverStatus = "<b>You are using a Test version of this Driver (Version: $state.Version)</b>"
            } else {
                state.DriverStatus = "Current"
                log.info "You are using the current version of this driver"
            }
        } // httpGet
    } // try

    catch (e) {
        log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
    }
    if(state.DriverStatus == "Current"){
        state.UpdateInfo = "Up to date"
        sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
        sendEvent(name: "DriverStatus", value: state.DriverStatus)
    } else {
        sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
        sendEvent(name: "DriverStatus", value: state.DriverStatus)
    }

    sendEvent(name: "DriverAuthor", value: "sgrayban")
    sendEvent(name: "DriverVersion", value: state.Version)
}
