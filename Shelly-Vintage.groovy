/**
 *  Shelly Vintage Device Handler
 *
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
 *  Changes:
 *  1.0.0 - Initial release
 *
 */

import groovy.transform.Field
import groovy.json.*

metadata {
	definition (
		name: "Shelly Vintage",
		namespace: "sgrayban",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-Vintage.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh" // refresh command
        capability "Switch"
        capability "Bulb"
        capability "Polling"
        capability "Change Level"
        capability "Switch Level"
        capability "PowerMeter"
        capability "EnergyMeter"

        command "RebootDevice"
        command "UpdateDeviceFW" // ota?update=1

        attribute "switch", "string"
        attribute "level", "number"
        attribute "MAC", "string"
        attribute "FW_Update_Needed", "string"
        attribute "Cloud", "string"
        attribute "IP", "string"
        attribute "SSID", "string"
        attribute "Cloud_Connected", "string"
        attribute "WiFiSignal", "string"
        attribute "LastRefresh", "string"
        attribute "DeviceName", "string"
        attribute "NTPServer", "string"
    }

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]

 	input("ip", "string", title:"Shelly IP Address:", description:"EG; 192.168.0.100", defaultValue:"" , required: true, displayDuringSetup: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    input name: "ntp_server", type: "text", title: "NTP time server:", description: "E.G. time.google.com or 192.168.0.59", defaultValue: "time.google.com", required: true
	input name: "devicename", type: "text", title: "Give your device a name:", description: "EG; Location/Room<br>NO SPACES in name", required: false
    input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "30 min")
	input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, //RK
			options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"] //RK
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true //RK
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: false
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs located</center>", description: "<center><a href='http://shelly-api-docs.shelly.cloud/' target='_blank'>[here]</a></center>"
	}
}

def ping() {
	if (txtEnable) log.info "ping"
	poll()
}

def initialize() {
	log.info "initialize"
}

def installed() {
    log.debug "Installed"
    state.DeviceName = "NotSet"
}

def uninstalled() {
    log.debug "Uninstalled"
    unschedule()
}

def updated() {
    log.debug "Updated"
    log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    unschedule()

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
		default:
			runEvery30Minutes(autorefresh)
	}
	if (txtEnable) log.info ("Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff)
    if (debugParse) runIn(1800,logsOff)
    
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)

    sendSwitchCommand "/settings?sntp_server=${ntp_server}"
// Set device and relay name
    sendSwitchCommand "/settings?name=${devicename}"

    dbCleanUp()
    version()
    refresh()
}

def refresh(){
 logDebug "Shelly Status called"
    def params1 = [uri: "http://${username}:${password}@${ip}/status"]

try {
    httpGet(params1) {
        resp1 -> resp1.headers.each {
        logJSON "Response1: ${it.name} : ${it.value}"
    }
        obs1 = resp1.data
       
        logJSON "params1: ${params1}"
        logJSON "response1 contentType: ${resp1.contentType}"
	    logJSON  "response1 data: ${resp1.data}"

       // def each state 
        state.rssi = obs1.wifi_sta.rssi
        state.ssid = obs1.wifi_sta.ssid
        state.mac = obs1.mac
        state.has_update = obs1.has_update
        state.cloud = obs1.cloud.enabled
        state.cloud_connected = obs1.cloud.connected

        ison = obs1.lights.ison[0]
        
        sendEvent(name: "MAC", value: state.mac)
        sendEvent(name: "SSID", value: state.ssid)
        sendEvent(name: "level", value: obs1.lights.brightness[0])
        sendEvent(name: "IP", value: obs1.wifi_sta.ip)
        sendEvent(name: "power", value: obs1.meters.power[0])
        sendEvent(name: "energy", value: obs1.meters.total[0])

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

        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", obs1.wifi_sta.ip)
        updateDataValue("ShellySSID", state.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)

        if (ison == true) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
        }

        if (cloud == enabled) {
            sendEvent(name: "Cloud", value: "<font color='green'>Enabled</font>")
        } else {
            sendEvent(name: "Cloud", value: "<font color='red'>Disabled</font>")
        }

        state.cloudConnected = obs1.cloud.connected
        if (state.cloudConnected == true) {
            sendEvent(name: "Cloud_Connected", value: "<font color='green'>Connected</font>")
        } else {
            sendEvent(name: "Cloud_Connected", value: "<font color='red'>Not Connected</font>")
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
    
} // End refresh

def getSettings(){
 logDebug "Shelly Settings called"
    def params = [uri: "http://${username}:${password}@${ip}/settings"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
       
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        state.DeviceType = obs.device.type
        state.ShellyHostname = obs.device.hostname
        state.sntp_server = obs.sntp.server
        sendEvent(name: "NTPServer", value: state.sntp_server)

//Get Device name
       if (obs.name != "NotSet") {
           state.DeviceName = obs.name
           sendEvent(name: "DeviceName", value: state.DeviceName)
           updateDataValue("DeviceName", state.DeviceName)
           if (txtEnable) log.info "DeviceName is ${obs.name}"
       } else if (obs.name != null) {
           state.DeviceName = "NotSet"
           sendEvent(name: "DeviceName", value: state.DeviceName)
           if (txtEnable) log.info "DeviceName is ${obs.name}"
       }
} // End try
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
    
} // End refresh

//switch.on
def on() {
    if (txtEnable) log.info "Executing switch.on"
    sendSwitchCommand "/light/0?turn=on"
}

//switch.off
def off() {
    if (txtEnable) log.info "Executing switch.off"
    sendSwitchCommand "/light/0?turn=off"
}

//switch.level
//  TODO: Need to add rate and transition to Shelly FW
def setLevel(percent) {
        sendLevelCommand "turn=on&brightness=${percent}"
        if (txtEnable) log.info ("setLevel ${percent}")
}

def setRateLevel(percentage, rate) {
	if (percentage < 0 || percentage > 100) {
		log.warn ("$device.name $device.label: Entered brightness is not from 0...100")
		return
	}
        sendLevelCommand "turn=on&brightness=${percentage}"
        if (txtEnable) log.info ("setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
}
// End Direct Device Commands

def startLevelChange(direction) {
	if (txtEnable) log.info ("startLevelChange: direction = ${direction}")
	if (direction == "up") {
		levelUp()
	} else {
		levelDown()
	}
}

def stopLevelChange() {
	if (txtEnable) log.info ("stopLevelChange")
	unschedule(levelUp)
	unschedule(levelDown)
}

def levelUp() {
	def newLevel = device.currentValue("level").toInteger() + 2
	if (newLevel > 101) { return }
	if (newLevel > 100) { newLevel = 100 }
	setRateLevel(newLevel, 0)
	runInMillis(500, levelUp)
}

def levelDown() {
	def newLevel = device.currentValue("level").toInteger() - 2
	if (newLevel < -1) { return }
	else if (newLevel <= 0) { off() }
	else {
		setRateLevel(newLevel, 0)
		runInMillis(500, levelDown)
	}
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def autorefresh() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'auto refresh'" //RK
    getSettings()
    refresh()
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

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'" //RK
    dbCleanUp()
    refresh()
}

def sendSwitchCommand(action) {
    if (txtEnable) log.info "Calling ${action}"
	def path = path
	def body = body 
	def headers = [:]
    if (username != null) {
        headers.put("HOST", "${username}:${password}@${ip}")
    } else {
        headers.put("HOST", "${ip}")
    }
	headers.put("Content-Type", "application/x-www-form-urlencoded")
    runIn(2, refresh)

	try {
		def hubAction = new hubitat.device.HubAction(
			method: method,
			path: action,
			body: body,
			headers: headers
			)
		logDebug hubAction
		return hubAction
	}
	catch (Exception e) {
        logDebug "sendSwitchCommand hit exception ${e} on ${hubAction}"
	}
}

def sendLevelCommand(action) {
    if (txtEnable) log.info "Setting Level ${action}"
    if (username != null) {
        host = "${username}:${password}@${ip}"
    } else {
        host = "${ip}"
    }
    sendHubCommand(new hubitat.device.HubAction(
      method: "POST",
      path: "/light/0",
      body: action,
      headers: [
        HOST: host,
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
    refresh()
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
    if (username != null) {
        host = "${username}:${password}@${ip}"
    } else {
        host = "${ip}"
    }
	sendEvent(name: "FW_Update_Needed", value: "Please Wait..Updating FW.")
    sendHubCommand(new hubitat.device.HubAction(
      method: "POST",
      path: "/ota?update=1",
      body: action,
      headers: [
        HOST: host,
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
    runIn(30,refresh)
}

def parse(description) {
    return
}

private dbCleanUp() {
//	unschedule()
    state.remove("UpdateInfo")
    state.remove("Current")
	state.remove("InternalName")
	state.remove("version")
	state.remove("Version")
	state.remove("Status")
	state.remove("effect")
    state.remove("powerSource")
}

// App Version   *********************************************************************************
def setVersion(){
	state.Version = "1.0.0"
	state.InternalName = "ShellyVintage"
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (github CobraVmax) for his original code **************
def version(){
	updatecheck()
	schedule("0 0 18 1/1 * ? *", updatecheck) // Cron schedule
//	schedule("0 0/1 * 1/1 * ? *", updatecheck) // Test Cron schedule
}

def updatecheck(){
    setVersion()
	 def paramsUD = [uri: "http://sgrayban.borgnet.online:8081/scotts-projects/version.json"]
	  try {
			httpGet(paramsUD) { respUD ->
				  if (txtEnable) log.warn " Version Checking - Response Data: ${respUD.data}"
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
				  }           
				  else if(currentVer < newVer){
					   state.DriverStatus = "<b>New Version Available (Version: $newVerRaw)</b>"
					   log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
					   log.warn "** $state.UpdateInfo **"
				 } 
				 else if(currentVer > newVer){
					   state.DriverStatus = "<b>You are using a Test version of this Driver (Version: $newVerRaw)</b>"
				 }
				 else{ 
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
	  }
	  else {
		   sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.DriverStatus)
	  }

	  sendEvent(name: "DriverAuthor", value: "sgrayban")
	  sendEvent(name: "DriverVersion", value: state.Version)
}
