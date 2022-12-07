/**
 *
 *  Shelly PLUS Dimmer Driver
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
 *
 *  Changes:
 *  1.0.1 - fixed channel number issues
 *  1.0.0 - Initial code
 *
 */

import groovy.json.*
import groovy.transform.Field

def setVersion(){
	state.Version = "1.0.1"
	state.InternalName = "ShellyPlusDimmer"
}

metadata {
	definition (
		name: "Shelly Plus Wall Dimmer",
		namespace: "ShellyUSA",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/PLUS/ShellyPlusDimmer.groovy"
		)
	{
        capability "Actuator"
        capability "Refresh"
        capability "Switch"
        capability "SwitchLevel"
        capability "Polling"
        capability "SignalStrength"
        
        attribute "StableFW_Update", "string"
        attribute "BetaFW_Update", "string"
        attribute "LastRefresh", "string"
        attribute "MAC", "string"
        attribute "Primary_IP", "string"
        attribute "Primary_SSID", "string"
        attribute "Secondary_IP", "string"
        attribute "Secondary_SSID", "string"
        attribute "WiFiSignal", "string"
        attribute "Cloud", "string"
        attribute "Cloud_Connected", "string"
        attribute "DeviceType", "string"
        attribute "DeviceName", "string"
        attribute "Eco_Mode", "string"
       
        command "RebootDevice"
        command "UpdateDeviceFW"
        command "initialize"
        //command "CheckForUpdate" // Only used for development
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
    input("refresh_Rate", "enum", title: "Device Refresh Rate", description:"<font color=red>!!WARNING!!</font><br>DO NOT USE if you have over 50 Shelly devices.", options: refreshRate, defaultValue: "manual")
    input "protect", "enum", title:"Prevent accidental off/on", defaultValue: true, options: [Yes:"Yes",No:"No"], required: true
        
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
    channel = "0"
    state.RelayChannel = channel

    version()
    refresh()
}

private dbCleanUp() {
    state.clear()
}

def refresh(){
    logDebug "Shelly Refresh called"
    getWiFi()
    getDeviceInfo()
    getLightStatus()
    getGetConfig()
    CheckForUpdate()
}

def getLightStatus(){
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Light.GetStatus?id=0"]
        
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        state.RelayChannel = channel

        ison = obs.output
        if (ison == true) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
        }
        sendEvent(name: "level", value: obs.brightness)
     
        updateDataValue("DeviceName", state.DeviceName)
        updateDataValue("RelayName", state.RelayName)
        updateDataValue("ShellyIP", state.ip)
        updateDataValue("ShellySSID", state.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)
//        updateDataValue("DeviceName", state.DeviceName)

} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    
} // End getLightStatus

    
def getWiFi(){
    logDebug "WiFi Status called"
    def params = [uri: "http://${username}:${password}@${ip}/rpc/wifi.GetStatus"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        state.rssi = obs.rssi
        state.ssid = obs.ssid
        state.ip = obs.sta_ip

        sendEvent(name: "Primary_SSID", value: state.ssid)
        sendEvent(name: "Primary_IP", value: state.ip)

/*
-30 dBm Excellent | -67 dBm     Good | -70 dBm  Poor | -80 dBm  Weak | -90 dBm  Dead
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
        
    } // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    
} // End Wifi Status

def getDeviceInfo(){

    logDebug "Sys Status called"
    //getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.GetDeviceInfo"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        state.mac = obs.mac
        state.app = obs.app
        state.model = obs.model
        
        updateDataValue("FW Version", obs.ver)
        updateDataValue("model", obs.model)
        updateDataValue("ShellyHostname", obs.id)
        updateDataValue("Device Type", obs.app)
        
    } // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    
} // End Device Info

def getGetConfig(){

    logDebug "Sys Status called"
    //getSettings()
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Sys.GetConfig"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"

        sendEvent(name: "Eco_Mode", value: obs.device.eco_mode)
        
    } // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
    
} // End sys get config

def CheckForUpdate() {
    if (txtEnable) log.info "Check Device FW"
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.CheckForUpdate"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        response = "${obs.toString()}"
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"
        
        if(response.contains("stable")) {
            sendEvent(name:  "StableFW_Update", value: "<font color='green'>Available</font>", isStateChange: true);
        }else
        if(!(response.contains("stable"))) {
            sendEvent(name:  "StableFW_Update", value: "Current", isStateChange: true);
        }
        
        if(response.contains("beta")) {
            sendEvent(name:  "BetaFW_Update", value: "<font color='red'>Available</font>", isStateChange: true);
        }else
        if(!(response.contains("beta"))) {
            sendEvent(name:  "BetaFW_Update", value: "Current", isStateChange: true);
        }
        
} // End try
        
} catch (e) {
        log.error "something went wrong: $e"
    }
}

//switch.on
def on() {
    if (protect == "No") {
        logDebug "Executing light on"
        sendSwitchCommand "/rpc/Light.Set?id=0&on=true"
        sendEvent(name: "switch", value: "on")
    }
    if (protect == "Yes") {
        sendEvent(name: "switch", value: "<font color='red'>LOCKED</font>")
        runIn(1, refresh)
    }
}

//switch.off
def off() {
    
    if (protect == "No") {
        logDebug "Executing light off"
        sendSwitchCommand "/rpc/Light.Set?id=0&on=false"
        sendEvent(name: "switch", value: "off")
    }
    if (protect == "Yes") {
        sendEvent(name: "switch", value: "<font color='red'>LOCKED</font>")
        runIn(1, refresh)
    }
}

def setLevel(percent) {
    sendSwitchCommand "/rpc/Light.Set?id=0&on=true&brightness=${percent}"
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
    httpGet(params) {
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
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.Reboot"]
try {
    httpGet(params) {
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
    def params = [uri: "http://${username}:${password}@${ip}/rpc/Shelly.Update?stage=stable"]
try {
    httpGet(params) {
        resp -> resp.headers.each {
        logDebug "Response: ${it.name} : ${it.value}"
    }
} // End try
        
} catch (e) {
        log.error "something went wrong: $e"
    }
    runIn(40,refresh)
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
