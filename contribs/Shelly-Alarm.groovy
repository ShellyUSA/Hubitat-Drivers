/**
 *
 *  Shelly Alarm Driver
 *
 *  Copyright © 2018-2019 Scott Grayban
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
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 *
 * Shelly Alarm works off a Shelly 1 or Shelly 1PM
 * It is based on the project by Jason Churchward --> http://blog.smarterhome.club/2019/09/07/shelly1strobealarm/
 *
 *  Changes:
 *  1.0.2 - Presense sensor added
 *  1.0.1 - Added code that will allow you to upgrade the device firmware and will auto-refresh in 30 seconds.
 *  1.0.0 - Initial port of Shelly Alarm
 *
 */

import groovy.json.*
import groovy.transform.Field

def setVersion(){
        state.Version = "1.0.2"
        state.InternalName = "ShellyAlarm"
}

metadata {
	definition (
		name: "Shelly Alarm",
		namespace: "sgrayban",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/contribs/Shelly-Alarm.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Switch"
        capability "Polling"
        capability "Alarm"
        capability "PresenceSensor"

        attribute "switch", "string"
        attribute "FW_Update_Needed", "string"
        attribute "LastRefresh", "string"
        attribute "internal_tempC", "string"
        attribute "MAC", "string"
        attribute "Primary_IP", "string"
        attribute "Primary_SSID", "string"
        attribute "Cloud", "string"
        attribute "Cloud_Connected", "string"
        attribute "alarm", "string"
        attribute "status", "string"
        attribute "WiFiSignal", "string"
        attribute "Secondary_IP", "string"
        attribute "Secondary_SSID", "string"
        attribute "DeviceType", "string"
        
        command "RebootDevice"
        command "UpdateDeviceFW" // ota?update=1
	}


	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: false, displayDuringSetup: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
    input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "30 min")
	input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"]
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: false
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs are located</center>", 
        description: "<center><br><a href='http://shelly-api-docs.shelly.cloud/' title='shelly-api-docs.shelly.cloud' target='_blank'>[here]</a></center>", 
        required: false
	}
}

def initialize() {
	log.info "initialize"
}

def installed() {
    log.debug "Installed"
}

def uninstalled() {
    log.debug "Uninstalled"
    unschedule()
}

def updated() {
    if (txtEnable) log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
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
		default:
			runEvery30Minutes(autorefresh)
	}
	if (txtEnable) log.info ("Auto Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff)
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    
    version()
    getSettings()
    refresh()
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
}

// We dont use this anymore 
// but it's still called so
// keep it until I fix the
// http POST calls.
def parse(description) {
    return
}

def refresh(){
    getSettings()
    logDebug "Shelly Status called"
    def params = [uri: "http://${username}:${password}@${ip}/status"]

try {
    httpGet(params) {
        resp -> resp.headers.each {
        logJSON "Response: ${it.name} : ${it.value}"
    }
        obs = resp.data
        if(logSet == false){  
       
        logJSON "params: ${params}"
        logJSON "response contentType: ${resp.contentType}"
	    logJSON "response data: ${resp.data}"
        }

        sendEvent(name: "presence", value: "present")
        if (obs.temperature != null) sendEvent(name: "internal_tempC", value: obs.temperature)
        if (obs.overtemperature != null) sendEvent(name: "DeviceOverTemp", value: obs.overtemperature)
        
        ison = obs.relays.ison[0]
        
        if (ison == true) {
            sendEvent(name: "switch", value: "on")
            sendEvent(name: "status", value: "on")
            sendEvent(name: "alarm", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
            sendEvent(name: "status", value: "off")
            sendEvent(name: "alarm", value: "off")
        }
        
        if (obs.wifi_sta != null) {
        state.rssi = obs.wifi_sta.rssi
        state.ssid = obs.wifi_sta.ssid
        state.ip = obs.wifi_sta.ip
        sendEvent(name: "Primary_SSID", value: state.ssid)
        sendEvent(name: "Primary_IP", value: state.ip)
        }
        
        state.mac = obs.mac
        sendEvent(name: "MAC", value: state.mac)

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
            sendEvent(name: "Cloud_Connected", value: "<font color='green'>Enabled</font>")
        } else {
            sendEvent(name: "Cloud_Connected", value: "<font color='red'>Not Connected</font>")
        }
        
} // End try
       } catch (e) {
         log.error "something went wrong: $e"
         sendEvent(name: "presence", value: "not present")
       }
} // End Refresh Status


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

        state.ShellyHostname = obsSettings.device.hostname
        
} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
} // End Refresh Status

//Alarm on
def on() {
    logDebug "Executing switch.on"
    sendSwitchCommand "turn=on"
}

def strobe() {
    logDebug "Executing strobe"
    on()
}

def both() {
    logDebug "Executing strobe"
    on()
}

def siren() {
    logDebug "Executing strobe"
    on()
}

//Alarm off
def off() {
    logDebug "Executing switch.off"
    sendSwitchCommand "turn=off"
}

def ping() {
	logDebug "ping"
	poll()
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
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
    if (txtEnable) log.info "Calling /relay/ with $action"
    if (username != null) {
        host = "${username}:${password}@${ip}"
    } else {
        host = "${ip}"
    }
    sendHubCommand(new hubitat.device.HubAction (
      method: "POST",
      path: "/relay/0",
      body: action,
      headers: [HOST: host, "Content-Type": "application/x-www-form-urlencoded"]
    ))
    runIn(1, refresh)
}


def RebootDevice() {
    if (txtEnable) log.info "Rebooting Device"
    if (username != null) {
        host = "${username}:${password}@${ip}"
    } else {
        host = "${ip}"
    }
    sendHubCommand(new hubitat.device.HubAction(
      method: "POST",
      path: "/reboot",
      body: action,
      headers: [HOST: host, "Content-Type": "application/x-www-form-urlencoded"]
    ))
    runIn(10,refresh)
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
				  if (debugParse) log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
				  def copyrightRead = (respUD.data.copyright)
				  state.Copyright = copyrightRead
				  def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
				  def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
				  def currentVer = state.Version.replace(".", "")
				  state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
				  state.author = (respUD.data.author)
				  state.icon = "<img src='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACgAAAAoCAMAAAC7IEhfAAAC9FBMVEUdFwIoGQM4JgE5NABELwBJMwBOMwBEOwBKOQJNOAJSNgBNQABXOwBKQgBUPQBRPwBPRABVQgBZQQBcRABZRgBiQgBTSgBXSQBoRABiSABfSgBgSwBeTgBXUgBbUABqSgBuSQBoTQBbVgFfVAFmUQFuTQNjUwFqUwB4TABuUgBnVgBkWABhWgB0UgB4UQBtWwFzWAFmXwFqXQF9VgB4WgCDVQBvYQB+WgBzYAB3XgBsZAB8XgCEWgGJWQJzZQB4ZAGLWwCIXQBxaQJ2aAR4aQB1bAB9aQCBZwCFZQCNYgCRYACLZQB9bgCUYwONZwKCbQGKaQJ7cQGHbAKQaQCWaQCDcwCIcQCBdgCNcQCRbwCeagGHdgCibQCKeQKOdwOHewKOfACWeACadgCicwCmcQCTewCMfwCgdgCTgQCXfwCgewKRhAGtdgCdfgKzdgCkfgChgQCtewCZhgCdhACpfgCSigCYigCxfwC1fQCeigCnhgCxhADBfACdjgKthgS7ggCojQCljwCikgC6hgDCggCflQCwjgCilwDJgwS7jAOolwG5kAC1kgSylQDPiADFjwCsnACzmwDQjQDFlADNkQKyoAC+mgDDmAK7nQLGmgC+nwDSlQCxpQXXkwDckgDEnwC2qADSmgC7qADglQLBpwDRnwDGpQDNogDkmADZnwDdnQC/rAK+sADHrADMqgDErwDlnwDUqADtnQHGtwDTsQH1oADbrQPkqQDMtgPkrgDxqADMvADQugD2pwDatgDisgDetQDqsADwrQDZuwD/qADSwQDWvwD8rAD1sQD6sADwtgD/rwDgwQDnvwDuvAD7tgD/tAD3uQDgxwD2vQHlxgD+uQLZzQDrxQD/uwDgzQTd0AD/vwDvyADsywDpzgD6xgD+xADl0QD/xgD0zADxzwD6ywD+ygDp1QDv0wDn2AD/zAD40AP/zAb20wD/0ADz1wDv2gD90wD/1gD13gD53AD+2gD34QD/3QL05AP+4AD/4gD85QC9W0cbAAAACXBIWXMAAC4jAAAuIwF4pT92AAAAB3RJTUUH4wwICSkZc7wKxgAAAdpJREFUGBmdwV1rUmEAwPH/d+pLdLO77rvpoogMYuRi1S7mxdwummmRRgiicxS4DoozECeItuIMxGyzrIMKqQjZybLBuum8PL6c9aiw34/zHty9ykLFZkf/+7urVqPMU9Q/Y9tMqWFm0o+ZoqQhHlcSIc7Tkzj4D/xAIvFdxUHHYc3DcgxLtptlooODC4N/FZumMZJRkVhHaJYQVGQ8jOgIVUaSEST2dCypBMKfs+EREjqWPII26PdaSBQrmMIIX3qN+j4Tt25gy1QwHYDHgyH4/vA1E296wyo2DdNDvxeJl7VW//Q6ll+YFC8yu+V6+ye2Lob0VgCpd7UPW9gKwNM4swQQQgUgB6wwn+qDfBjDNebKAQXm2wBCObhXZYHHoABqFCHodyPz/IqCIZvBluo1PnqQCaxiKCGc9tu1F0i4Y5hK2J6cDXqNAP97ROwyhmwEW+n0m8LExn0Ml1xAENOnCjK7b8uHCAlMN3Uk1sq1elvBlsfSRGK/3uoNfNhOsCR1RlyMHQ2GKkIOW7OCsMLEHUa+MqJnEAKYlpj2rMqYpiKsw8oSDhpTKt0IlmWvG6cuToXCSSJuuB1kWqTDTOkdxirHzLGTy0V9m75XpZK+zQLRwg9tb5sL+QdvDc0dp1ZEdQAAAABJRU5ErkJggg=='>"
				  if(newVer == "NLS"){
					   state.DriverStatus = "<b>** This driver is no longer supported by $state.author  **</b>"       
					   log.warn "** This driver is no longer supported by $state.author **"      
				  }           
				  else if(newVer == "BETA"){
					   state.Status = "<b>** THIS IS BETA CODE  **</b>"       
					   log.warn "** BETA CODE **"      
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
