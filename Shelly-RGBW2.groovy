/**
 *
 *  Shelly RGBW[2]/RGBW+ Device Handler
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
 *  TODO - Finish up code for White mode to control all 4 channels 0-3
 *
 *  Changes:
 *  1.5.6 - Changed Copyright to new company
 *        - Removed all white settings as Shelly uses 2 different FW for colour and white now
 *        - Added NTP server preference
 *        - You can now set the device name
 *        - Added manual or polling only refresh
 *        - Re-added capability "Switch"
 *  1.5.5 - Fixed the secure login username password (!!Note: Passwords must not contain these special characters &?)
 *  1.5.4 - Added code that will allow you to upgrade the device firmware and will auto-refresh in 30 seconds.
 *  1.5.2 - Removing old RGB <> HSV methods
 *        - Added donation link
 *        - Removed importURL because it can't be used anymore
 *        - New location for code
 *  1.5.1 - New attributes rgbCode, MAC, RGB, etc.....
 *        - ColourMap now works in dashboard and in device menu
 *        - Set colour now works in Alexa
 *        - New json parsing.. much cleaner since I can troll the entire tree in one shot
 *        - Refresh change
 *        - ColorMode moved to preferences
 *        - Setting colour via Alexa works.. in theory it should work in GH also
 *        - Device now works in RM
 *        - Support for username/password
 *  1.5.0 - Code for the future RGBW2+
 *        - Use of selecting lightEffects using static Map
 *          and setNextEffect setPreviousEffect commands
 *        - setLevel works in Dashboard
 *        - Predefined colours
 *        - 1 codebase for RGBW2 and RGBW2+ selectable by deviceType()
 *        - New refresh rate code (djgutheinz)
 *        - Change Level added. This feature will step up/down in light percentage with a command to stop the level change.
 *  1.0.0 - Initial release
 *
 */

import hubitat.helper.ColorUtils
import groovy.transform.Field
import groovy.json.*

def deviceType() { return "rgbw2" }
@Field static Map lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Flash"]

//def deviceType() { return "rgbw2Plus" } // Waiting for device 4(?) months
//@Field static Map lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Breath",4:"Flash",5:"Gradual On/Off",6:"Red/Green Change"]

//	==========================================================

def setVersion(){
	state.Version = "1.5.6"
	state.InternalName = "ShellyRGBW"
}

metadata {
	definition (
        name: "Shelly ${deviceType()}",
		namespace: "sgrayban",
		author: "Scott Grayban",
                importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-RGBW2.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh" // refresh command
        capability "Switch"
        capability "SwitchLevel"
        capability "Polling"
        capability "PowerMeter"
        capability "ChangeLevel"
        capability "Light"
        capability "LightEffects"
        //capability "Color Mode"
        capability "ColorControl"
        capability "SignalStrength"
        
        // Color shortcut commands
        command "Red"
        command "Blue"
        command "Green"
        command "White"
        command "Cyan"
        command "Magenta"
        command "Orange"
        command "Purple"
        command "Yellow"
        command "Pink"
        command "WarmWhite"
        command "SoftWhite"
        command "Daylight"
        command "ColdWhite"
        command "ClearBlueSky"
        command "RebootDevice"
        command "CustomRGBwColor", ["r","g","b","w"]
        command "UpdateDeviceFW" // ota?update=1

        if (deviceType == "rgbw2Plus") {
        command "setCustomEffect", [[name:"Effect to use (0=OFF)", type: "ENUM", description: "Effect to use (0=OFF)", constraints: ["0","1","2","3","4","5","6",]]]
        }
        else {
        command "setCustomEffect", [[name:"Effect to use (0=OFF)", type: "ENUM", description: "Effect to use (0=OFF)", constraints: ["0","1","2","3"]]]
        }

        attribute "colorMode", "string"
        attribute "hexCode", "string"
        attribute "effectName", "string"
        attribute "lightEffects", "string"
        attribute "overpower", "string"
        attribute "dcpower", "string"

        attribute "WiFiSignal", "string"
        attribute "MAC", "string"
        attribute "IP", "string"
        attribute "SSID", "string"
        attribute "FW_Update_Needed", "string"

        attribute "cloud", "string"
        attribute "power", "string"
        attribute "overpower", "string"

        attribute "RGBw", "string"
        attribute "RGB", "string"
        attribute "HEX", "string"
        attribute "HSV", "string"
        attribute "hue", "number"
        attribute "saturation", "number"
        attribute "huelevel", "number"
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
	}
}

/*
    if (getDataValue("deviceType") == "SHRGBW2") {
        def lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Flash"]
    } else {
        def lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Breath",4:"Flash",5:"Gradual On/Off",6:"Red/Green Change"]
    }
*/

def installed() {
    def le = new groovy.json.JsonBuilder(lightEffects)
    sendEvent(name:"lightEffects",value:le)
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
    
    if (devType == "rgbw2Plus") {
    state.lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Breath",4:"Flash",5:"Gradual On/Off",6:"Red/Green Change"]
    }
    else {
    state.lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Flash"]
    }
    sendEvent(name: "lightEffects", value: "${state.lightEffects}" )
    if (txtEnable) log.info ("state.lightEffects: ${state.lightEffects}" )

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
    state.remove("RGBw")
    state.remove("RGB")
    state.remove("level")
    state.remove("lightEffect")
    state.remove("red")
    state.remove("green")
    state.remove("blue")
    state.remove("white")
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
        red = obsStatus.lights.red[0]
        green = obsStatus.lights.green[0]
        blue = obsStatus.lights.blue[0]
        rgbwCode = "${red},${green},${blue},${white}"
        rgbCode = "${red},${green},${blue}"
        
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
        sendEvent(name: "level", value: obsStatus.lights.gain[0])
        sendEvent(name: "overpower", value: obsStatus.lights.overpower[0])
        sendEvent(name: "colorMode", value: mode)
        sendEvent(name: "RGBw", value: rgbwCode)
        if (txtEnable) log.info "rgbCode = $red,$green,$blue,$white"
        sendEvent(name: "RGB", value: rgbCode)
        Hex = ColorUtils.rgbToHEX( [red, blue, green] )
        sendEvent(name: "HEX", value: Hex)
        
        hsvColors = ColorUtils.rgbToHSV([red.toInteger(), green.toInteger(), blue.toInteger()])
        sendEvent(name: "HSV", value: hsvColors)
        sendEvent(name: "hue", value: hsvColors[0])
        sendEvent(name: "saturation", value: hsvColors[1])
        sendEvent(name: "huelevel", value: hsvColors[2])

        if (ison == true) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
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

// send Effects
    if (state.effect == 0) {
        if (txtEnable) log.info "sendEvent effect None"
        sendEvent(name: "effectName", value: "Disabled")
    }
    if (state.effect == 1) {
        if (txtEnable) log.info "sendEvent effect Meteor shower"
        sendEvent(name: "effectName", value: "Meteor Shower")
    }
    if (state.effect == 2) {
        if (txtEnable) log.info "sendEvent effect Gradual change"
        sendEvent(name: "effectName", value: "Gradual Change")
    }
// RGBW2 only has 1-3 effects plus 0 which is off
// RGBW2+ has 6 effects plus 0 which is off
    if (state.effect == 3) {
    if (devType == "rgbw2Plus") {
            if (txtEnable) log.info "sendEvent effect Breath"
            sendEvent(name: "effectName", value: "Breath")
    } else {
            if (txtEnable) log.info "sendEvent effect Flash"
            sendEvent(name: "effectName", value: "Flash")
        }
    }

    if (devType == "rgbw2Plus") {
    if (state.effect == 4) {
        if (txtEnable) log.info "sendEvent effect Flash"
        sendEvent(name: "effectName", value: "Flash")
    }
    if (state.effect == 5) {
        if (txtEnable) log.info "sendEvent effect Gradual On/Off"
        sendEvent(name: "effectName", value: "Gradual On/Off")
    }
    if (state.effect == 6) {
        if (txtEnable) log.info  "sendEvent effect Red/Green Change"
        sendEvent(name: "effectName", value: "Red/Green Change")
    }
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
    sendSwitchCommand "/color/0?turn=on"
}

//switch.off
def off() {
    if (txtEnable) log.info "Executing switch.off"
    sendSwitchCommand "/color/0?turn=off"
}

// Colours
def Red() {
    if (txtEnable) log.info "Executing colour red"
    sendSwitchCommand "/color/0?effect=0&red=255&green=0&blue=0&white=0"
}
def Green() {
    if (txtEnable) log.info "Executing colour green"
    sendSwitchCommand "/color/0?effect=0&red=0&green=128&blue=0&white=0"
}
def Blue() {
    if (txtEnable) log.info "Executing colour blue"
    sendSwitchCommand "/color/0?effect=0&red=0&green=0&blue=255&white=0"
}
def White() {
    if (txtEnable) log.info "Executing colour white"
    sendSwitchCommand "/color/0?effect=0&red=255&green=255&blue=255&white=255"
}
def Cyan() {
    if (txtEnable) log.info "Executing colour cyan"
    sendSwitchCommand "/color/0?effect=0&red=0&green=255&blue=255&white=0"
}
def Magenta() {
    if (txtEnable) log.info "Executing colour magenta"
    sendSwitchCommand "/color/0?effect=0&red=255&green=0&blue=33&white=0"
}
def Orange() {
    if (txtEnable) log.info "Executing colour orange"
    sendSwitchCommand "/color/0?effect=0&red=255&green=102&blue=0&white=0"
}
def Purple() {
    if (txtEnable) log.info "Executing colour purple"
    sendSwitchCommand "/color/0?effect=0&red=170&green=0&blue=255&white=0"
}
def Yellow() {
    if (txtEnable) log.info "Executing colour yellow"
    sendSwitchCommand "/color/0?effect=0&red=255&green=255&blue=0&white=0"
}
def Pink() {
    if (txtEnable) log.info "Executing colour pink"
    sendSwitchCommand "/color/0?effect=0&red=255&green=20&blue=147&white=0"
}

// White mode colours
// http://planetpixelemporium.com/tutorialpages/color.html
def WarmWhite() {
   if (txtEnable) log.info "Executing colour warmWhite"
   sendSwitchCommand "/color/0?effect=0&red=255&green=154&blue=30&white=50"
}
def SoftWhite() {
    if (txtEnable) log.info "Executing colour softWhite"
    sendSwitchCommand "/color/0?effect=0&red=255&green=147&blue=41&white=25"
}
def ColdWhite() {
    if (txtEnable) log.info "Executing colour ColdWhite"
    sendSwitchCommand "/color/0?effect=0&red=255&green=255&blue=255&white=0"
}
def Daylight() {
    if (txtEnable) log.info "Executing colour daylight"
    sendSwitchCommand "/color/0?effect=0&red=255&green=255&blue=251&white=250"
}
def ClearBlueSky() {
    if (txtEnable) log.info "Executing colour daylight"
    sendSwitchCommand "/color/0?effect=0&red=64&green=156&blue=255&white=100"
}

// switch.CustomRGBwColor
def CustomRGBwColor(r,g,b,w=null) {
    if (txtEnable) log.info "Executing Custom Color Red:${r} Green:${g} Blue:${b} White=${w}"
    if (w == null) {
        sendSwitchCommand "/color/0?red=${r}&green=${g}&blue=${b}&white=0"
    } else {
        sendSwitchCommand "/color/0?red=${r}&green=${g}&blue=${b}&white=${w}"
    }
    
    hsvColors = ColorUtils.rgbToHSV([r.toInteger(), g.toInteger(), b.toInteger()])
    sendEvent(name: "HSV", value: hsvColors)
    sendEvent(name: "hue", value: hsvColors[0])
    sendEvent(name: "saturation", value: hsvColors[1])
    sendEvent(name: "huelevel", value: hsvColors[2])
}

def setHue(value)
{
    PollShellyStatus()
    setColor([hue: value, saturation: device.currentValue("saturation").toInteger(), level: device.currentValue("huelevel").toInteger()])
}

def setSaturation(value)
{
    PollShellyStatus()
    setColor([hue: device.currentValue("hue").toInteger(), saturation: value, level: device.currentValue("huelevel").toInteger()])
}

def setColor(parameters){
    logDebug "Color set to ${parameters}"
    
	sendEvent(name: "hue", value: parameters.hue)
	sendEvent(name: "saturation", value: parameters.saturation)
	sendEvent(name: "huelevel", value: parameters.level)
	rgbColors = ColorUtils.hsvToRGB( [parameters.hue, parameters.saturation, parameters.level] )
    r = rgbColors[0].toInteger()
    g = rgbColors[1].toInteger()
    b = rgbColors[2].toInteger()
    w = 0
    if (txtEnable) log.info "Red: ${r},Green:${g},Blue:${b}"
    CustomRGBwColor(r,g,b,w)
}

//switch.effect
def setCustomEffect(effectnumber) {
    if (txtEnable) log.info "Executing setEffect ${effectnumber}"
    sendSwitchCommand "/color/0?effect=${effectnumber}"
}

def setEffect(String effect){
    def id = lightEffects.find{ it.value == effect }
    if (id) setEffect(id.key)
    refresh()
}

def setEffect(id){
    def descriptionText
    def efSelect = lightEffects."${id}"
    descriptionText = "${device.displayName}, effect was was set to ${efSelect}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name:"effectName", value:efSelect, descriptionText:descriptionText)
    descriptionText = "${device.displayName}//, colorMode is EFFECTS"
    state.crntEffectId = id
    //device specific code here
    sendSwitchCommand "/color/0?effect=${id}"
    refresh()
}

def setNextEffect(){
    def currentEffect = state.crntEffectId ?: 0
    currentEffect++
    if (devType == "rgbw2Plus") {
    if (currentEffect >= 6) currentEffect = 1
    }
    else {
    if (currentEffect >= 4) currentEffect = 1
    }
    setEffect(currentEffect)
    refresh()
}

def setPreviousEffect(){
    def currentEffect = state.crntEffectId ?: 2
    currentEffect--
    if (devType == "rgbw2Plus") {
    if (currentEffect < 1) currentEffect = 6 - 1
    }
    else {
    if (currentEffect < 1) currentEffect = 4 - 1
    }
    setEffect(currentEffect)
    refresh()
}

//switch.level
def setLevel(percent) {
    if (colorMode == "white") {
        sendSwitchCommand "/white/0?turn=on&brightness=${percent}"
        if (txtEnable) log.info ("White Mode setLevel ${percent}")
    } else
        sendSwitchCommand "/color/0?turn=on&gain=${percent}&white=${percent}"
        if (txtEnable) log.info ("Color Mode setLevel ${percent}")
}

def setLevel(percentage, rate) {
	if (percentage < 0 || percentage > 100) {
		log.warn ("$device.name $device.label: Whoa there buddy.... entered level is not from 0...100")
		return
	}
	percentage = percentage.toInteger()
    if (colorMode == "white") {
        sendSwitchCommand "/white/0?turn=on&brightness=${percentage}"
        if (txtEnable) log.info ("White Mode setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
    } else
        sendSwitchCommand "/color/0?turn=on&gain=${percentage}&white=${percent}"
        if (txtEnable) log.info ("Color Mode setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
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
    runIn(1, refresh)
	if (newLevel > 101) { return }
	if (newLevel > 100) { newLevel = 100 }
	setLevel(newLevel, 0)
	runInMillis(500, levelUp)
}

def levelDown() {
	def newLevel = device.currentValue("level").toInteger() - 2
    runIn(1, refresh)
	if (newLevel < -1) { return }
	else if (newLevel <= 0) { off() }
	else {
		setLevel(newLevel, 0)
		runInMillis(500, levelDown)
	}
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
	 def paramsUD = [uri: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/resources/version.json"]
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
