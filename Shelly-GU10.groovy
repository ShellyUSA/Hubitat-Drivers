/**
 *  Shelly GU10 Device Handler
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
 *  Changes:
 *  1.0.1 - Change in color lighteffects code
 *        - Change how dbCleanUp works
 *  1.0.0 - Initial release
 *
 */

import groovy.transform.Field
import groovy.json.*
import hubitat.helper.ColorUtils

@Field static Map lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Flash"]

def setVersion(){
	state.Version = "1.0.1"
	state.InternalName = "ShellyGU10"
}

metadata {
	definition (
		name: "Shelly GU10",
		namespace: "sgrayban",
		author: "Scott Grayban",
		importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-GU10.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh" // refresh command
        capability "Switch"
        capability "Bulb"
        capability "Polling"
        capability "ChangeLevel"
        capability "SwitchLevel"
        capability "ColorControl"
        capability "PowerMeter"
        capability "ColorMode"

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
        command "CoolWhite"
        command "Daylight"
        command "CustomRGBColor", ["r","g","b"]
        command "setCustomEffect", [[name:"Effect to use (0=OFF)", type: "ENUM", description: "Effect to use (0=OFF)", constraints: ["0","1","2","3"]]]
        command "Effect", [[name:"Effect to use", type: "ENUM", description: "Effect to use", constraints: ["0","1","2","3"] ] ]
        command "UpdateDeviceFW" // ota?update=1

        attribute "switch", "string"
        attribute "colorMode", "string"
        attribute "HEX", "string"
        attribute "RGB", "string"
        attribute "hue", "number"
        attribute "saturation", "number"
        attribute "huelevel", "number"
        attribute "level", "number"
        attribute "effectName", "string"
        attribute "ColorLightEffects", "string"
        attribute "WhiteModeOnly", "tring"
        attribute "MAC", "string"
        attribute "FW_Update_Needed", "string"
        attribute "Cloud", "string"
        attribute "IP", "string"
        attribute "SSID", "string"
        attribute "Cloud_Connected", "string"
        attribute "WiFiSignal", "string"
        attribute "LastRefresh", "string"
    }

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]

    def Color_Mode = [:]
        Color_Mode << ["color" : "Color"]
        Color_Mode << ["white" : "White"]
        
 	input("ip", "string", title:"Shelly IP Address:", description:"EG; 192.168.0.100", defaultValue:"" , required: true, displayDuringSetup: true)
	input name: "username", type: "text", title: "Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Password:", description: "(blank if none)", required: false
	input ("ColorMode", "enum", title: "Colour Mode", options: Color_Mode, defaultValue: "white")
	input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "30 min")
	input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, //RK
			options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"] //RK
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true //RK
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: false
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs located</center>", description: "<center><a href='http://shelly-api-docs.shelly.cloud/'>[here]</a></center>"
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
    dbCleanUp()

    def le = new groovy.json.JsonBuilder(lightEffects)
    sendEvent(name:"ColorLightEffects",value:le)

    switch(ColorMode) {
      case "color" :
        setModeColor()
        if (txtEnable) log.info "Executing mode=${ColorMode}"
        break
      case "white" :
        setModeWhite()
        if (txtEnable) log.info "Executing mode=${ColorMode}"
        break
      default:
        setModeWhite()
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
		default:
			runEvery30Minutes(autorefresh)
	}
	if (txtEnable) log.info ("Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff)
    if (debugParse) runIn(1800,logsOff)
    
    state.lightEffects = [1:"Meteor Shower",2:"Gradual Change",3:"Flash"]
    sendEvent(name: "lightEffects", value: "${state.lightEffects}")
    state.WhiteModeOnly = ["WarmWhite, CoolWhite & Daylight"]
    sendEvent(name: "WhiteModeOnly", value:"${state.WhiteModeOnly}")
    
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)

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
        state.effect = obs1.lights.effect[0]
        state.cloud_connected = obs1.cloud.connected

        ison = obs1.lights.ison[0]
        colorMode = obs1.lights.mode[0]
        red = obs1.lights.red[0]
        green = obs1.lights.green[0]
        blue = obs1.lights.blue[0]
        rgbCode = "${red},${green},${blue}"
        
        sendEvent(name: "MAC", value: state.mac)
        sendEvent(name: "SSID", value: state.ssid)
        sendEvent(name: "level", value: obs1.lights.gain[0])
        sendEvent(name: "colorMode", value: colorMode)
        sendEvent(name: "IP", value: obs1.wifi_sta.ip)
        sendEvent(name: "power", value: obs1.meters.power[0])

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

        if (colorMode == "color") if (txtEnable) log.info "rgbCode = $red,$green,$blue,$white"
        if (colorMode == "color") sendEvent(name: "RGB", value: rgbCode)
        if (colorMode == "color") Hex = ColorUtils.rgbToHEX( [red, blue, green] )
        if (colorMode == "color") sendEvent(name: "HEX", value: Hex)
            
        hsvColors = ColorUtils.rgbToHSV([red.toInteger(), green.toInteger(), blue.toInteger()])
        sendEvent(name: "hue", value: hsvColors[0])
        sendEvent(name: "saturation", value: hsvColors[1])
        sendEvent(name: "huelevel", value: hsvColors[2])

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
        if (state.effect == 3) {
            if (txtEnable) log.info "sendEvent effect Breath"
            sendEvent(name: "effectName", value: "Breath")
        }
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

def Red() {
    if (txtEnable) log.info "Executing colour red"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=0&blue=0&turn=on"
}
def Green() {
    if (txtEnable) log.info "Executing colour green"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=0&green=128&blue=0&turn=on"
}
def Blue() {
    if (txtEnable) log.info "Executing colour blue"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=0&green=0&blue=255&turn=on"
}
def White() {
    if (txtEnable) log.info "Executing colour white"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=255&blue=255&turn=on"
}
def Cyan() {
    if (txtEnable) log.info "Executing colour cyan"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=0&green=255&blue=255&turn=on"
}
def Magenta() {
    if (txtEnable) log.info "Executing colour magenta"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=0&blue=33&turn=on"
}
def Orange() {
    if (txtEnable) log.info "Executing colour orange"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=102&blue=0&turn=on"
}
def Purple() {
    if (txtEnable) log.info "Executing colour purple"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=170&green=0&blue=255&turn=on"
}
def Yellow() {
    if (txtEnable) log.info "Executing colour yellow"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=255&blue=0&turn=on"
}
def Pink() {
    if (txtEnable) log.info "Executing colour pink"
    sendSwitchCommand "/light/0?turn=on&gain=100&effect=0&red=255&green=20&blue=147&turn=on"
}

// White mode colours
// http://www.westinghouselighting.com/color-temperature.aspx
// http://planetpixelemporium.com/tutorialpages/light.html
// temp is in Kevin
def WarmWhite() {
   if (txtEnable) log.info "Executing colour warmWhite"
   if (colorMode == "white") sendSwitchCommand "/light/0?turn=on&white=0&temp=3000"
}
def CoolWhite() {
    if (txtEnable) log.info "Executing colour coolWhite"
    if (colorMode == "white") sendSwitchCommand "/light/0?turn=on&white=50&temp=4500"
}
def Daylight() {
    if (txtEnable) log.info "Executing colour daylight"
    if (colorMode == "white") sendSwitchCommand "/light/0?turn=on&white=100&temp=6500"
}

// switch.CustomRGBwColor
def CustomRGBColor(r,g,b) {
    if (txtEnable) log.info "Executing Custom Color Red:${r} Green:${g} Blue:${b}"
    if (w == null) {
        sendSwitchCommand "/light/0?turn=on&gain=100&red=${r}&green=${g}&blue=${b}"
    } else {
        sendSwitchCommand "/light/0?turn=on&gain=100&red=${r}&green=${g}&blue=${b}"
    }
}

def setHue(value)
{
    refresh()
    setColor([hue: value, saturation: device.currentValue("saturation").toInteger(), level: device.currentValue("huelevel").toInteger()])
}

def setSaturation(value)
{
    refresh()
    setColor([hue: device.currentValue("hue").toInteger(), saturation: value, level: device.currentValue("huelevel").toInteger()])
}

def setColor( parameters ){
    logDebug "Color set to ${parameters}"
    
	sendEvent(name: "hue", value: parameters.hue)
	sendEvent(name: "saturation", value: parameters.saturation)
	sendEvent(name: "huelevel", value: parameters.level)    
	rgbColors = ColorUtils.hsvToRGB( [parameters.hue, parameters.saturation, parameters.level] )
    r = rgbColors[0].toInteger()
    g = rgbColors[1].toInteger()
    b = rgbColors[2].toInteger()
    def Hex = ColorUtils.rgbToHEX([r, g, b])
    if (txtEnable) log.info "Red: ${r},Green:${g},Blue:${b}"
    if (txtEnable) log.info "HEX: ${HEX}"
    CustomRGBColor(r,g,b)
}

//switch.effect
def Effect(number) {
    if (txtEnable) log.info "Executing colour white"
    sendSwitchCommand "/light/0?effect=${number}"
}

//switch.mode
def Mode(string) {
    if (txtEnable) log.info "Executing colour mode"
    sendSwitchCommand "/settings?mode=${string}"
}

//switch.gain
def setLevel() {
    if (txtEnable) log.info "Executing Brightness Level"
    sendLevelSwitchCommand "turn=on&effect=0&gain=${number}"
}

//switch.level
//  TODO: Need to add rate and transition to Shelly FW
def setLevel(percent, transTime) {
    if (ColorMode == "white") {
        sendLevelCommand "turn=on&brightness=${percent}"
        if (txtEnable) log.info ("White Mode setLevel(x): transition time = ${transTime}")
    } else
        sendLevelCommand "turn=on&gain=${percent}"
        if (txtEnable) log.info ("Color Mode setLevel(x): transition time = ${transTime}")
}

def setRateLevel(percentage, rate) {
	if (percentage < 0 || percentage > 100) {
		log.warn ("$device.name $device.label: Entered brightness is not from 0...100")
		return
	}
//	percentage = percentage.toInteger()
    if (ColorMode == "white") {
        sendLevelCommand "turn=on&brightness=${percentage}"
        if (txtEnable) log.info ("White Mode setLevel(x,x): rate = ${rate} // percentage = ${percentage}")
    } else
        sendLevelCommand "turn=on&gain=${percentage}"
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

def setModeWhite() {
    if (txtEnable) log.info "color mode white"
    def params = [uri: "http://${username}:${password}@${ip}/settings?mode=white"]
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

def setModeColor() {
    if (txtEnable) log.info "color mode color"
    def params = [uri: "http://${username}:${password}@${ip}/settings?mode=color"]
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
    state.clear()
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
				  if (txtEnable) log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
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
