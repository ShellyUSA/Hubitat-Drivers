/**
 *
 *  Shelly RGBW[2] White 4 Channel Device Handler
 *  Raw code is located at https://gitlab.borgnet.us:8443/sgrayban/shelly-drivers/raw/master/Drivers/Shelly/Shelly-RGBW2.groovy
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
 *  1.0.0 - Initial release
 *
 */

import hubitat.helper.ColorUtils
import groovy.transform.Field
import groovy.json.*

//	==========================================================

def setVersion(){
	state.Version = "1.0.0"
	state.InternalName = "ShellyRGBWhite"
}

metadata {
	definition (
        name: "Shelly RGBW White",
		namespace: "sgrayban",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-RGBW-White.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh" // refresh command
        capability "Switch"
        capability "SwitchLevel"
        capability "Polling"
        capability "PowerMeter"
        capability "Light"
        capability "SignalStrength"
        
        command "CH0On"
        command "CH1On"
        command "CH2On"
        command "CH3On"
        command "CH0Off"
        command "CH1Off"
        command "CH2Off"
        command "CH3Off"
        command "setLevel1", ["Level"]
        command "setLevel2", ["Level"]
        command "setLevel3", ["Level"]

        command "RebootDevice"
        command "UpdateDeviceFW" // ota?update=1

        attribute "colorMode", "string"
        attribute "overpower", "string"
        attribute "dcpower", "string"

        attribute "WiFiSignal", "string"
        attribute "MAC", "string"
        attribute "IP", "string"
        attribute "SSID", "string"
        attribute "FW_Update_Needed", "string"

        attribute "CH0Level", "string"
        attribute "CH1Level", "string"
        attribute "CH2Level", "string"
        attribute "CH3Level", "string"
        
        attribute "CH0", "string"
        attribute "CH1", "string"
        attribute "CH2", "string"
        attribute "CH3", "string"

        attribute "cloud", "string"
        attribute "power", "string"
        attribute "overpower", "string"
        attribute "level", "number"
        attribute "cloud_connected", "string"
        attribute "DeviceType", "string"
        attribute "LastRefresh", "string"
        attribute "DriverStatus", "string"
        attribute "DeviceName", "string"
        attribute "NTPServer", "string"
    }

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
		refreshRate << ["manual" : "Manually or Polling Only"]

    def power_Type = [:]
        power_Type << ["24" : "24v"]
        power_Type << ["12" : "12v"]

	input("ip", "string", title:"Shelly IP Address:", description:"EG; 192.168.0.100", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    if (ip != null) {
        input name: "ntp_server", type: "text", title: "NTP time server:", description: "E.G. time.google.com or 192.168.0.59", defaultValue: "time.google.com", required: true
        input name: "devicename", type: "text", title: "Give your device a name:", description: "EG; Location/Room<br>NO SPACES in name", required: false
        input ("powerType", "enum", title: "DC Power voltage:", options: power_Type, defaultValue: true, required: true)
        input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "manual")
        input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true,
            options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"] //RK
    }
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs located</center>", description: "<center><a href='http://shelly-api-docs.shelly.cloud/' target='_blank'>[here]</a></center>"
    
	input name: "ch0Enabled", type: "bool", title: "Channel 0 enabled?", defaultValue: true
	input name: "ch1Enabled", type: "bool", title: "Channel 1 enabled?", defaultValue: true
	input name: "ch2Enabled", type: "bool", title: "Channel 2 enabled?", defaultValue: true
	input name: "ch3Enabled", type: "bool", title: "Channel 3 enabled?", defaultValue: true
	}
}

def installed() {
    log.debug "Installed ${device.id}"
    state.DeviceName = "NotSet"
}

def uninstalled() {
    log.debug "Uninstalled"
    unschedule()
}

def initialize() {
	log.info "initialize"
}

def poll() {
	if (locale == "UK") {
	if (txtEnable) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (txtEnable) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'"
    refresh()
}

def updated() {
    log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    log.warn "JSON parsing logging is: ${debugParse == true}"
    log.warn "Description Text logging is: ${txtEnable == true}"
    unschedule()

    switch(powerType) {
      case "24" :
        sendSwitchCommand "/settings?dcpower=true"
        if (txtEnable) log.info "Executing dcpower=true"
        break
      case "12" :
        sendSwitchCommand "/settings?dcpower=false"
        if (txtEnable) log.info "Executing dcpower=false"
        break
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
		case "manual" :
			unschedule(autorefresh)
            log.info "Autorefresh disabled"
            break
	}

    if (txtEnable) log.info ("Refresh set for every ${refresh_Rate} minute(s).")
    if (debugOutput) runIn(1800,logsOff)
	if (txtEnable) runIn(600,txtOff)
    if (debugParse) runIn(600,parseOff)
    
    state.colorMode = "${ColorMode}"
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    
    sendSwitchCommand "/settings?sntp_server=${ntp_server}"
    sendSwitchCommand "/settings?name=${devicename}"

    dbCleanUp()
    version()
    refresh()
}

def dbCleanUp() {
	state.remove("rssi")
	state.remove("MAC")
	state.remove("IP")
  	state.remove("SSID")
    state.remove("FW_Update")
    state.remove("cloud")
    state.remove("power")
    state.remove("overpower")
    state.remove("switch")
    state.remove("mode")
    state.remove("level")
    state.remove("has_update")
    state.remove("Status")
    state.remove("UpdateInfo")
    state.remove("colorMode")
}
def ping() {
	if (txtEnable) log.info "ping"
	refresh()
}

def refresh() {
    PollShellySettings()
    PollShellyStatus()
    PollShellySettings()
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def parseOff(){
	log.warn "Json logging auto disabled..."
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def txtOff(){
	log.warn "Description text logging auto disabled..."
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def parse(description) {
    runIn(2, refresh)
}

def PollShellyStatus(){
 logDebug "Shelly Status called"
    def paramsStatus = [uri: "http://${username}:${password}@${ip}/status"]
try {
    httpGet(paramsStatus) {
        respStatus -> respStatus.headers.each {
        logJSON "ResponseStatus: ${it.name} : ${it.value}"
    }
        obsStatus = respStatus.data

        logJSON "paramsStatus: ${paramsStatus}"
        logJSON "responseStatus contentType: ${respStatus.contentType}"
	    logJSON  "responseStatus data: ${respStatus.data}"

       // def each state 
        state.rssi = obsStatus.wifi_sta.rssi
        state.ssid = obsStatus.wifi_sta.ssid
        state.mac = obsStatus.mac
        state.has_update = obsStatus.has_update
        state.effect = obsStatus.lights.effect[0]

        ison = obsStatus.lights.ison[0]
        power = obsStatus.meters.power[0]
        mode = obsStatus.mode
        
//        def idstr = state.mac
//        deviceid = idstr.substring(6,6)
//        sendEvent(name: "DeviceID", value: deviceid)
        
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

        sendEvent(name: "rssi", value: state.rssi)
        sendEvent(name: "MAC", value: state.mac)
        sendEvent(name: "SSID", value: state.ssid)
        sendEvent(name: "power", value: power)
        
        sendEvent(name: "CH0Level", value: obsStatus.lights.brightness[0])
        sendEvent(name: "CH1Level", value: obsStatus.lights.brightness[1])
        sendEvent(name: "CH2Level", value: obsStatus.lights.brightness[2])
        sendEvent(name: "CH3Level", value: obsStatus.lights.brightness[3])
        
        sendEvent(name: "overpower", value: obsStatus.lights.overpower[0])
        sendEvent(name: "colorMode", value: mode)

        if (ison == true) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
        }

        if (obsStatus.lights.ison[0] == true) {
            sendEvent(name: "CH0", value: "on")
        } else {
            sendEvent(name: "CH0", value: "off")
        }
        if (obsStatus.lights.ison[1] == true) {
            sendEvent(name: "CH1", value: "on")
        } else {
            sendEvent(name: "CH1", value: "off")
        }
        if (obsStatus.lights.ison[2] == true) {
            sendEvent(name: "CH2", value: "on")
        } else {
            sendEvent(name: "CH2", value: "off")
        }
        if (obsStatus.lights.ison[3] == true) {
            sendEvent(name: "CH3", value: "on")
        } else {
            sendEvent(name: "CH3", value: "off")
        }

        if (obsStatus.cloud.enabled == true) {
            sendEvent(name: "cloud", value: "<font color='green'>Enabled</font>")
        } else {
            sendEvent(name: "cloud", value: "<font color='red'>Disabled</font>")
        }

        if (obsStatus.cloud.connected == true) {
            sendEvent(name: "cloud_connected", value: "<font color='green'>Connected</font>")
        } else {
            sendEvent(name: "cloud_connected", value: "<font color='red'>Not Connected</font>")
        }
// FW Updates
   if (state.has_update == true) {
        if (txtEnable) log.info "sendEvent NEW SHELLY FIRMWARE"
        sendEvent(name: "FW_Update_Needed", value: "<font color='red'>FIRMWARE Update Required</font>")
    }
   if (state.has_update == false) {
        if (txtEnable) log.info "sendEvent Device FW is current"
        sendEvent(name: "FW_Update_Needed", value: "<font color='green'>Device FW is current</font>")
    }

} // End try
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
    
} // End PollShellyStatus

def PollShellySettings(){
 logDebug "Shelly Settings called"
    def paramsSettings = [uri: "http://${username}:${password}@${ip}/settings"]
try {
    httpGet(paramsSettings) {
        respSettings -> respSettings.headers.each {
        logJSON "ResponseSettings: ${it.name} : ${it.value}"
    }
        obsSettings = respSettings.data

        logJSON "paramsSettings: ${paramsSettings}"
        logJSON "responseSettings contentType: ${respSettings.contentType}"
	    logJSON "responseSettings data: ${respSettings.data}"

        state.DeviceType = obsSettings.device.type
        state.ShellyHostname = obsSettings.device.hostname
        state.sntp_server = obsSettings.sntp.server
        sendEvent(name: "NTPServer", value: state.sntp_server)
        
        sendEvent(name: "DeviceType", value: state.DeviceType)
        
        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", obsSettings.wifi_sta.ip)
        updateDataValue("ShellySSID", obsSettings.wifi_sta.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)


// dcpower
    if (obsSettings.dcpower == 0) {
        if (txtEnable) log.info "sendEvent dcpower=12v"
        sendEvent(name: "dcpower", value: "12v")
    }
    if (obsSettings.dcpower == 1) {
        if (txtEnable) log.info "sendEvent dcpower=24v"
        sendEvent(name: "dcpower", value: "24v")
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
        updateDataValue("DeviceName", state.DeviceName)

    } // End try
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
    
}// End PollShellySettings


//	Device Commands
//switch.on
def on() {
    if (txtEnable) log.info "Executing switch.on"
    if (ch0Enabled) CH0On()
    if (ch1Enabled) CH1On()
    if (ch2Enabled) CH2On()
    if (ch3Enabled) CH3On()
}

def CH0On() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/0?turn=on"
}
def CH1On() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/1?turn=on"
}
def CH2On() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/2?turn=on"
}
def CH3On() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/3?turn=on"
}

//switch.off
def off() {
    if (txtEnable) log.info "Executing switch.off"
    if (ch0Enabled) CH0Off()
    if (ch1Enabled) CH1Off()
    if (ch2Enabled) CH2Off()
    if (ch3Enabled) CH3Off()
}

def CH0Off() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/0?turn=off"
}
def CH1Off() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/1?turn=off"
}
def CH2Off() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/2?turn=off"
}
def CH3Off() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/white/3?turn=off"
}

//switch.level
def setLevel(percent, duration) {
    // some automations make the call with a duration
    setLevel(percent)
}

def setLevel(percent) {
    if (txtEnable) log.info "Executing setLevel"
    if (ch0Enabled) setLevel0(percent)
    if (ch1Enabled) setLevel1(percent)
    if (ch2Enabled) setLevel2(percent)
    if (ch3Enabled) setLevel3(percent)
}

def setLevel0(percent) {
    sendSwitchCommand "/white/0?turn=on&brightness=${percent}"
}

def setLevel1(percent) {
    sendSwitchCommand "/white/1?turn=on&brightness=${percent}"
}

def setLevel2(percent) {
    sendSwitchCommand "/white/2?turn=on&brightness=${percent}"
}

def setLevel3(percent) {
    sendSwitchCommand "/white/3?turn=on&brightness=${percent}"
}

def autorefresh() {
	if (locale == "UK") {
	if (txtEnable) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (txtEnable) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing ${refresh_Rate} minute(s) AUTO REFRESH" //RK
    refresh()
}

// handle commands
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
    runIn(10,refresh)
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

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "$msg"
	}
}

private logJSON(msg) {
	if (settings?.debugParse || settings?.debugParse == null) {
	log.info "$msg"
	}
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
				  logJSON " Version Checking - Response Data: ${respUD.data}"
				  def copyrightRead = (respUD.data.copyright)
				  state.Copyright = copyrightRead
				  def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
				  def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
				  def currentVer = state.Version.replace(".", "")
				  state.DriverUpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
				  state.author = (respUD.data.author)
				  state.icon = (respUD.data.icon)
				  if(newVer == "NLS"){
					 state.DriverStatus = "<b>** This driver is no longer supported by $state.author  **</b>"       
					 log.warn "** This driver is no longer supported by $state.author **"      
				  }           
				  else if(currentVer < newVer){
					 state.DriverStatus = "<b>New Version Available (Version: $newVerRaw)</b>"
					 log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
					 log.warn "** $state.DriverUpdateInfo **"
				 } 
				 else if(currentVer > newVer){
					 state.DriverStatus = "<b>You are using a Test version of this Driver $state.Version (Stable Version: $newVerRaw)</b>"
				 } else { 
					 state.DriverStatus = "Current"
					 log.info "You are using the current version of this driver"
				 }
			} // httpGet
	  } // try

	  catch (e) {
		   log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
	  }

	  if(state.Status == "Current"){
		   state.DriverUpdateInfo = "Up to date"
	  }
	  else {
		   sendEvent(name: "DriverUpdate", value: state.DriverUpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.DriverStatus)
	  }
}
