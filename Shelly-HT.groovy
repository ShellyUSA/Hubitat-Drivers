/**
 *  Shelly MQTT Device Handler
 *
 *  Copyright 2019 Scott Grayban
 *
 * Please Note: This app is NOT released under any open-source license.
 * Please be sure to read the license agreement before installing this code.
 *
 * This software package is created and licensed by Scott Grayban.
 *
 * This software, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * You may not distribute any part of this software without the author's express permission
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forth
 * in the Software license agreement.
 * This agreement can be found on-line at: http://sgrayban.borgnet.online:8081/scotts-projects/software_License_Agreement.txt
 * 
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 * Scott Grayban has no formal or informal affiliations or relationships with Hubitat or Allterco Robotics Ltd.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *  3.1.2 - Corrected code for getting device settings and current status
 *  3.1.1 - Added code to pull settings and status when the device wakes up.
 *        - RSSI value is definded as exelent, good or poor. The actual rssi reading is under state variables.
 *  3.1.0 - Removed MQTT alpha code
 *        - New code location https://gitlab.borgnet.us:8443/sgrayban/shelly-drivers
 *  3.0.0 - Added flood/rain sensor device
 *        - Added MQTT connection check(15min) for use with RM notifications - attribute MQTTconnected
 *        - Renamed driver to Shelly MQTT since it can be expanded for other MQTT only devices
 *  2.2.1 - License Change
 *  2.1.2 - Added txtEnable
 *  2.1.0 - Simplified the subscription - only need the shelly deviceid now
 *  2.0.0 - First release of code
 *  1.0.0 - Initial port
 *
 */

metadata {
definition (
    name: "Shelly MQTT",
    namespace: "sgrayban",
    author: "Scott Grayban",
    importUrl: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-HT.groovy"
)

    {
        capability "Initialize"
        capability "Actuator"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "WaterSensor"

        attribute "LastUpdateTime", "string"
        attribute "temperature", "string"
        attribute "humidity", "string"
        attribute "battery", "string"
        attribute "flood", "string"
        attribute "wet", "string"
        attribute "MQTTconnected", "string"
        attribute "DeviceType", "string"
        attribute "Primary_SSID", "string"
        attribute "PrimaryIP", "number"
        attribute "Secondary_WiFi", "string"
        attribute "Secondary_SSID", "string"
        attribute "FW_Update_Needed", "string"
        attribute "MAC", "string"
        attribute "Cloud", "string"
        attribute "Cloud_Connected", "string"
        attribute "WiFiSignal", "string"

        command "initialize"
        command "ReconnectMQTT"
   }

preferences {
    def device = [:]
        device << ["ht" : "H & T"]
        device << ["flood" : "Flood"]
    
	input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true
	input name: "MQTTBrokerPort", type: "text", title: "MQTT Broker Port:", required: true
	input name: "MQTTUsername", type: "text", title: "MQTT Username:", description: "(blank if none)"
	input name: "MQTTPassword", type: "password", title: "MQTT Password:", description: "(blank if none)"
    input ("DeviceType", "enum", title: "Device Mode", options: device, defaultValue: "ht")
	input name: "shellyId", type: "text", title: "Your Shelly Device ID:", description: "EG; Y8AFD0", required: true
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "debugParse", type: "bool", title: "Enable JSON parse logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input("ip", "string", title:"Shelly IP Address", description:"Required to pull stats", defaultValue:"" , required: true)
	input name: "username", type: "text", title: "Shelly Username:", description: "(blank if none)", required: false
	input name: "password", type: "password", title: "Shelly Password:", description: "(blank if none)", required: false
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs located</center>", description: "<center><a href='http://shelly-api-docs.shelly.cloud/'>[here]</a></center>"
        }
}

def installed() {
    log.info "installed..."
}

def setVersion(){
	state.Version = "3.1.2"
	state.InternalName = "ShellyHTMQTT"
}

def parse(String description) {
	Date date = new Date(); 
	topic = interfaces.mqtt.parseMessage(description).topic
	topic = topic.substring(topic.lastIndexOf("/") + 1)
	payload = interfaces.mqtt.parseMessage(description).payload
	if (txtEnable) log.info topic
	if (txtEnable) log.info payload
    sendEvent(name: "${topic}", value: "${payload}", displayed: true)
	state."${topic}" = "${payload}"
    if (DeviceType == "flood") {
        if (state.flood == "false") sendEvent (name: "water", value: "dry")
        if (state.flood == "true") sendEvent (name: "water", value: "wet")
    } else {
        sendEvent (name: "water", value: "---")
    }
    
    if (state.act_reasons == '["sensor"]') {
        for (i = 0; i <1; i++) { // checks only once
            getSettings()
            getStatus()
            state.act_reasons = "0" // Now we change the reason to stop getting stats
        }
    }

    if (debugOutput) sendEvent(name: "Last_Payload_Received", value: "Topic: ${topic}: ${payload} - ${date.toString()}", displayed: true)
	state.LastUpdateTime = "${date.toString()}"
    sendEvent (name: "LastUpdateTime", value: state.LastUpdateTime)
}

def updated() {
    log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    log.warn "JSON parsing logging is: ${debugParse == true}"
    log.warn "Description Text logging is: ${txtEnable == true}"
    state.remove("Last_Update_Recieved")
    unschedule()
    runEvery15Minutes(status)
    log.info "MQTT connection check set to 15 Minutes"
    if (debugOutput) runIn(1800,logsOff)
	if (txtEnable) runIn(600,txtOff)
    if (debugParse) runIn(600,parseOff)
    state.DeviceType = "$DeviceType"
    dbCleanUp()
    version()
    ReconnectMQTT()
    initialize()
}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
    state.remove("MTQQbrokerURL")
    state.remove("shellyId")
}

def parseOff(){
	log.warn "Json logging auto disabled."
	device.updateSetting("debugParse",[value:"false",type:"bool"])
}

def txtOff(){
    log.warn "Description Text logging disabled."
    device.updateSetting("txtEnable",[value:"false",type:"bool"])
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

def ReconnectMQTT() {
    log.info "Reconnecting MQTT"
    uninstalled()
    initialize()
}

def uninstalled() {
    log.info "Disconnecting from MQTT"
    interfaces.mqtt.disconnect()
}

def status() {
    sendEvent (name: "MQTTconnected", value: "${interfaces.mqtt.isConnected() == true}")
    if (txtEnable) "MQTT connected is: ${interfaces.mqtt.isConnected() == true}"
}

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
        state.ShellyHostname = obsSettings.device.hostname

        if (state.DeviceType == "SHHT-1") sendEvent(name: "DeviceType", value: "Shelly H&T")
        if (state.DeviceType == "SHWT-1") sendEvent(name: "DeviceType", value: "Shelly Flood")

        updateDataValue("model", state.DeviceType)
        updateDataValue("ShellyHostname", state.ShellyHostname)
        updateDataValue("ShellyIP", obsSettings.wifi_sta.ip)
        updateDataValue("ShellySSID", obsSettings.wifi_sta.ssid)
        updateDataValue("manufacturer", "Allterco Robotics")

        sendEvent(name: "Primary_SSID", value: obsSettings.wifi_sta.ssid)
        sendEvent(name: "PrimaryIP", value: obsSettings.wifi_sta.ip)
        sendEvent(name: "Secondary_WiFi", value: obsSettings.wifi_sta1.enabled)
        sendEvent(name: "Secondary_SSID", value: obsSettings.wifi_sta1.ssid)

        
} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
} // End Refresh Status

def getStatus(){
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
        
        state.mac = obs.mac
        sendEvent(name: "MAC", value: state.mac)
        updateDataValue("MAC", state.mac)
        state.rssi = obs.wifi_sta.rssi
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
            sendEvent(name: "Cloud_Connected", value: "<font color='green'>Connected</font>")
        } else {
            sendEvent(name: "Cloud_Connected", value: "<font color='red'>Not Connected</font>")
        }
} // End try
       } catch (e) {
           log.error "something went wrong: $e"
       }
} // End Refresh Status

def initialize() {
	try {
        mqttbroker = "tcp://" + settings?.MQTTBroker + ":" + settings?.MQTTBrokerPort
        interfaces.mqtt.connect(mqttbroker, "hubitat" + settings?.shellyId, settings?.MQTTUsername,settings?.MQTTPassword)
        if (debugOutput) {
            state.MTQQbrokerURL= "${mqttbroker}"
            state.shellyId = "${shellyId}"
        } else {
            state.remove("MTQQbrokerURL")
            state.remove("shellyId")
        }
        //give it a chance to start
        pauseExecution(1000)
		logDebug "Subscribed to: ${"shellies/shelly" + settings?.DeviceType + "-" + settings?.shellyId + "/sensor/#"}"
        interfaces.mqtt.subscribe("shellies/shelly" + settings?.DeviceType + "-" + settings?.shellyId + "/sensor/#")

    } catch(e) {
        log.info "MQTT Initialize error: ${e.message}"
    }
}

def mqttClientStatus(String status) {
    log.info "MQTT ${status}"
}

private dbCleanUp() {
    state.remove("UpdateInfo")
    state.remove("DriverUpdateInfo")
    state.remove("DeviceUpdateInfo")
    state.remove("Current")
    state.remove("InternalName")
    state.remove("version")
    state.remove("Version")
    state.remove("MTQQbrokerURL")
    state.remove("Last_Update_Recieved")
    state.remove("Last_Update")
    state.remove("flood")
    state.remove("water")
    state.remove("DeviceType")
    state.remove("devType")
    state.remove("MQTTconnected")
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
				  logJSON " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
				  def copyrightRead = (respUD.data.copyright)
				  state.Copyright = copyrightRead
				  def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
				  def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
				  def currentVer = state.Version.replace(".", "")
				  state.DeviceUpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
				  state.author = (respUD.data.author)
				  state.icon = (respUD.data.icon)
				  if(newVer == "NLS"){
					   state.Status = "<b>** This device handler is no longer supported by $state.author  **</b>"       
					   log.warn "** This driver is no longer supported by $state.author **"      
				  }           
				  else if(currentVer < newVer){
					   state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
					   log.warn "** There is a newer version of this device handler available  (Version: $newVerRaw) **"
					   log.warn "** $state.DeviceUpdateInfo **"
				 } 
				 else if(currentVer > newVer){
					   state.Status = "<b>You are using a Test version of this device handler $state.Version (Stable Version: $newVerRaw)</b>"
				 }
				 else{ 
					 state.Status = "Current"
					 log.info "You are using the current version of this device handler"
				 }
			} // httpGet
	  } // try

	  catch (e) {
		   log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
	  }

	  if(state.Status == "Current"){
		   state.DeviceUpdateInfo = "Up to date"
		   sendEvent(name: "DriverUpdate", value: state.DeviceUpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }
	  else {
		   sendEvent(name: "DriverUpdate", value: state.DeviceUpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }

	  sendEvent(name: "DriverAuthor", value: "sgrayban")
	  sendEvent(name: "DriverVersion", value: state.Version)
}
