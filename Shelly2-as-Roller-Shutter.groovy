/**
 *
 *  Shelly 2/2.5 as Roller Shutter Driver Handler
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
 *  2.0.2 - Fixed secure login bug
 *  2.0.1 - Fixed a bug with autorefresh
 *        - Added Mains or Battery for power source
 *  2.0.0 - Tons of code re-work.
 *        - setPosition works
 *        - Thanks to Remus for his help in debugging the final code !!!
 *  1.0.2 - Beta code added
 *  1.0.1 - Changed debug calls
 *        - Added Calibrate Command
 *        - Added warning if device was still in relay mode instead of roller
 *  1.0.0 - Initial port
 *
 */
import groovy.json.*
import groovy.transform.Field

metadata {
	definition (
		name: "Shelly 2/2.5 as Roller Shutter",
		namespace: "sgrayban",
		author: "Scott Grayban",
		importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly2-as-Roller-Shutter.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Switch Level" // attribute: level (integer, setter: setLevel), command setLevel(level)
        capability "Switch"
        capability "Window Shade" // windowShade.value ( closed, closing, open, opening, partially open, unknown ), methods: close(), open(), setPosition()
        capability "Polling"
        capability "PowerSource"
        capability "SignalStrength"
        
        command "stop"
//        command "calibrate" // Needs to be done via the phone app
        command "UpdateDeviceFW" // ota?update=1
        
        attribute "mode", "string"
        attribute "level","number"
        attribute "switch","string"
        attribute "windowShade","string"
        attribute "obstacle_power","string"
        attribute "safety_action","string"
        attribute "state","string"
        attribute "obstacle_mode","string"
        attribute "obstacle_action","string"
        attribute "stop_reason","string"
        attribute "safety_switch","string"
        attribute "safety_mode","string"
        attribute "safety_allowed_on_trigger","string"
        attribute "obstacle_delay","string"
        attribute "power","number"
        attribute "WiFiSignal","string"
        attribute "IP","number"
        attribute "SSID","string"
        attribute "MAC","string"
        attribute "DeviceType","string"
        attribute "Secondary_SSID","string"
	}

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]

	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: false)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
	input("powersource", "enum", title:"Mains/Battery", description:"Shelly Power Source", defaultValue: true, options: [mains:"Mains",battery:"Battery"], required: true)
	input("preset", "number", title: "Pre-defined position (1-100)", defaultValue: 50, required: false)
	input("closedif", "number", title: "Closed if at most (1-100)", defaultvalue: 5, required: false)
	input("openif", "number", title: "Open if at least (1-100)", defaultvalue: 85, required: false)
    input("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: true, required: true)
	input("locale", "enum", title: "Choose refresh date format", defaultValue: true, options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"], required: true)
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs are located</center>", 
        description: "<center><br><a href='http://shelly-api-docs.shelly.cloud/' title='shelly-api-docs.shelly.cloud' target='_blank'>[here]</a></center>", required: false
	}
}

def initialize() {
	log.info "initialize"
	if (txtEnable) log.info "initialize"
}

def installed() {
    log.debug "Installed"
}

def uninstalled() {
    unschedule()
    log.debug "Uninstalled"
}

// App Version   *********************************************************************************
def setVersion(){
	state.Version = "2.0.2"
	state.InternalName = "ShellyAsARoller"
}

def updated() {
    log.debug "Updated"
    log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    log.warn "Debug Parse logging is: ${debugParse == true}"
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
	if (txtEnable) log.info ("Auto Refresh set for every ${refresh_Rate} minute(s).")

    logDebug runIn(1800,logsOff)
    logJSON runIn(1800,logsOff)
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    
    version()
    refresh()
}

def refresh(){
    getSettings()
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

        state.powerSource = settings?.powersource
        sendEvent(name: "powerSource", value: state.powerSource)
        state.current_pos = obs1.rollers[0].current_pos
        
// Under /status
        sendEvent(name: "level", value: state.current_pos)
        sendEvent(name: "stop_reason", value: obs1.rollers[0].stop_reason)

// End Status

    if ( state.current_pos < closedif ) {
        if (txtEnable) log.info "CreateEvent closed"
        sendEvent(name: "windowShade", value: "closed")
        sendEvent(name: "switch", value: "off")
    } else
        if ( state.current_pos > openif ) {
        if (txtEnable) log.info "CreateEvent open"
        sendEvent(name: "windowShade", value: "on")
        sendEvent(name: "switch", value: "on")
    } else {
        if (txtEnable) log.info "CreateEvent Partially open"
        sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
    }
       // def each state 
        state.rssi = obs1.wifi_sta.rssi
        state.ssid = obs1.wifi_sta.ssid
        state.mac = obs1.mac
        state.has_update = obs1.has_update
        state.cloud = obs1.cloud.enabled
        state.cloud_connected = obs1.cloud.connected

        sendEvent(name: "MAC", value: state.mac)
        sendEvent(name: "SSID", value: state.ssid)
        sendEvent(name: "IP", value: obs1.wifi_sta.ip)

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
        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", obs1.wifi_sta.ip)
        updateDataValue("ShellySSID", state.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)

} // End try
        
    } catch (e) {
        log.error "something went wrong: $e"
    }
    
} // End refresh

// Get shelly device type
def getSettings(){
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
        if (state.DeviceType == "SHPLG-1") sendEvent(name: "DeviceType", value: "Shelly Plug")
        if (state.DeviceType == "SHPLG-S") sendEvent(name: "DeviceType", value: "Shelly PlugS")

        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", obsSettings.wifi_sta.ip)
        updateDataValue("ShellySSID", obsSettings.wifi_sta.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")
        updateDataValue("MAC", state.mac)

        if (obsSettings.wifi_sta1 != null) {
        state.rssi = obsSettings.wifi_sta1.rssi
        state.Secondary_ssid = obsSettings.wifi_sta1.ssid
        state.Secondary_IP = obsSettings.wifi_sta1.ip
        if (obsSettings.wifi_sta1.enabled == true) sendEvent(name: "Secondary_SSID", value: state.Secondary_ssid)
        if (state.Secondary_IP != null) sendEvent(name: "Secondary_IP", value: state.Secondary_IP)
        sendEvent(name: "WiFiSignal", value: state.rssi)
        }

        state.mode = obsSettings.mode
        state.ShellyHostname = obsSettings.device.hostname
        
// Under /settings
        if (state.mode == "relay" ) {
            sendEvent(name: "mode", value: "!!CHANGE DEVICE TO ROLLER!!")
        } else {
            sendEvent(name: "mode", value: state.mode)
        }
    
        sendEvent(name: "power", value: obsSettings.rollers[0].power)
        sendEvent(name: "safety_switch", value: obsSettings.rollers[0].safety_switch)
        sendEvent(name: "safety_mode", value: obsSettings.rollers[0].safety_mode)
        sendEvent(name: "safety_action", value: obsSettings.rollers[0].safety_action)
        sendEvent(name: "safety_allowed_on_trigger", value: obsSettings.rollers[0].safety_allowed_on_trigger)
        sendEvent(name: "state", value: obsSettings.rollers[0].state)
        sendEvent(name: "obstacle_mode", value: obsSettings.rollers[0].obstacle_mode)
        sendEvent(name: "obstacle_action", value: obsSettings.rollers[0].obstacle_action)
        sendEvent(name: "obstacle_power", value: obsSettings.rollers[0].obstacle_power)
        sendEvent(name: "obstacle_delay", value: obsSettings.rollers[0].obstacle_delay)
// End Settings

} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
} // End Refresh Status


def open() {
    if (txtEnable) log.info "Executing 'open'"
    sendCommand "/roller/0?go=open"
}

def close() {
    if (txtEnable) log.info "Executing 'close'"
    sendCommand "/roller/0?go=close"
}

def on() {
    if (txtEnable) log.info "Executing open"
    open()
}

def off() {
    if (txtEnable) log.info "Executing close"
    close()
}

def setLevel(value, duration = null) {
    if (txtEnable) log.info "Executing setLevel value with $value"
    sendCommand "/roller/0?go=to_pos&roller_pos="+value
}

def setPosition(position) {
    if (txtEnable) log.info "Executing 'setPosition'"
//    setLevel(value)
    sendCommand "/roller/0?go=to_pos&roller_pos="+position
}

def stop() {
    if (txtEnable) log.info "Executing stop()"
    sendCommand "/roller/0?go=stop"
}

def calibrate() {
    if (txtEnable) log.info "Executing calibrate"
    sendCommand "/roller/0?calibrate"
}

def ping() {
    if (txtEnable) log.info "Ping"
	poll()
}

def UpdateDeviceFW() {
    sendCommand "/ota?update=1"
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

def poll() {
	if (locale == "UK") {
	logDebug log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	logDebug log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'" //RK
	refresh()
}

def sendCommand(action) {
    if (txtEnable) log.info "Calling $action"
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
    runIn(25, refresh)
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
				  state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
				  state.author = (respUD.data.author)
				  state.icon = (respUD.data.icon)
				  if(newVer == "NLS"){
					   state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
					   log.warn "** This driver is no longer supported by $state.author **"      
				  }           
				  else if(newVer == "BETA"){
					   state.Status = "<b>** THIS IS BETA CODE  **</b>"       
					   log.warn "** BETA CODE **"      
				  }           
				  else if(currentVer < newVer){
					   state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
					   log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
					   log.warn "** $state.UpdateInfo **"
				 } 
				 else if(currentVer > newVer){
					   state.Status = "<b>You are using a Test version of this Driver (Version: $newVerRaw)</b>"
				 }
				 else{ 
					 state.Status = "Current"
					 log.info "You are using the current version of this driver"
				 }
			} // httpGet
	  } // try

	  catch (e) {
		   log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
	  }

	  if(state.Status == "Current"){
		   state.UpdateInfo = "Up to date"
		   sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }
	  else {
		   sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }

	  sendEvent(name: "DriverAuthor", value: "sgrayban")
	  sendEvent(name: "DriverVersion", value: state.Version)
}
