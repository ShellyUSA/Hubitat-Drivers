/*
 *  Shelly Smoke Device Handler
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
 * NOTES: In the Shelly MQTT Settings make sure - Clean Session is checked and Retain is UNchecked
 *        These settings are found via the web settings for the device
 *
 *  Changes:
 *  1.0.0 - Initial release
 *
 */

import groovy.json.JsonSlurper 
import java.util.GregorianCalendar

def setVersion(){
	state.Version = "1.0.0"
	state.InternalName = "ShellySmoke"
}

metadata {
  definition (name: "Shelly Smoke", namespace: "sgrayban", 
      author: "Scott Grayban", 
      importURL: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/Shelly-Smoke.groovy")
    {
        capability "Sensor"
        capability "Initialize"
        capability "SmokeDetector"
        capability "Battery"
        capability "VoltageMeasurement"
        
        attribute "DriverStatus", "string"
        attribute "mute", "string"
        attribute "cloud_connected", "string"
        attribute "StableFW_Update", "string"
        attribute "BetaFW_Update", "string"

        command "mute"
    }

  preferences {
      input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", 
          required: true, displayDuringSetup: true
      input name: "username", type: "text", title: "MQTT Username:", 
          description: "(blank if none)", required: false, displayDuringSetup: true
      input name: "password", type: "password", title: "MQTT Password:", 
          description: "(blank if none)", required: false, displayDuringSetup: true
      input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
          description: "Example Topic (shellyplussmoke-80646fd11718). Please don't use a #", 
          required: true, displayDuringSetup: true
      input name: "QOS", type: "text", title: "QOS Value:", required: false, 
        defaultValue: "1", displayDuringSetup: true
      input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
 }
}


def installed() {
    log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  if (logEnable) log.info "${payload}"
  def parser = new JsonSlurper()
  if (topic == "${settings?.topicSub}/status/smoke:0") {
      def pr_vals = parser.parseText(payload)

      smokestatus = pr_vals['alarm']
      
      if (smokestatus == false) {
          sendEvent(name: "smoke", value: "clear", displayed: true)
      } else {
          sendEvent(name: "smoke", value: "detected", displayed: true)
      }
          
      sendEvent(name: "mute", value: pr_vals['mute'], displayed: true)
  }
  if (topic == "${settings?.topicSub}/status/devicepower:0") {
      def pr_vals = parser.parseText(payload)

      sendEvent(name: "battery", value: pr_vals['battery']['percent'], unit:"%", displayed: true)
      sendEvent(name: "voltage", value: pr_vals['battery']['V'], displayed: true)
  }
  if (topic == "${settings?.topicSub}/status/wifi") {
      def pr_vals = parser.parseText(payload)

      state.sta_ip = pr_vals['sta_ip']
      state.ssid = pr_vals['ssid']
      updateDataValue("ShellyIP", state.sta_ip)
      updateDataValue("ShellySSID", state.ssid)
  }
  if (topic == "${settings?.topicSub}/status/sys") {
      def pr_vals = parser.parseText(payload)

      state.mac = pr_vals['mac']
      updateDataValue("MAC", state.mac)
      
      fw = pr_vals['available_updates']
      if(fw.containsKey("stable")) {
          sendEvent(name:  "StableFW_Update", value: "<font color='green'>Available</font>", isStateChange: true);
        }else
          if(!(fw.containsKey("stable"))) {
              sendEvent(name:  "StableFW_Update", value: "Current", isStateChange: true);
          }
        
        if(fw.containsKey("beta")) {
            sendEvent(name:  "BetaFW_Update", value: "<font color='red'>Available</font>", isStateChange: true);
        }else
            if(!(fw.containsKey("beta"))) {
                sendEvent(name:  "BetaFW_Update", value: "Current", isStateChange: true);
            }
  }
  if (topic == "${settings?.topicSub}/status/cloud") {
      def pr_vals = parser.parseText(payload)

        if (pr_vals['connected'] == true) {
            sendEvent(name: "cloud_connected", value: "<font color='green'>Connected</font>")
        } else {
            sendEvent(name: "cloud_connected", value: "<font color='red'>Not Connected</font>")
        }
  }

}

def mute(){
    log.info "Mute detector"
    def params = [uri: "http://${username}:${password}@${state.sta_ip}/rpc/Smoke.Mute?id=0"]
try {
    httpGet(params) {
// not needed as the only response is null
//        resp -> resp.headers.each {
//        logEnable "Response: ${it.name} : ${it.value}"
//        }
    } // End try
} catch (e) {
    log.error "something went wrong: $e"
}
} // End Mute

def updated() {
  if (logEnable) log.info "Updated..."
    initialize()
    unschedule()
    dbCleanUp()
    updateDataValue("model", "SNSN-0031Z")
    //updateDataValue("ShellyHostname", state.ShellyHostname)
    updateDataValue("ShellyIP", state.sta_ip)
    updateDataValue("ShellySSID", state.ssid)
    updateDataValue("manufacturer", "Allterco Robotics")
    updateDataValue("MAC", state.mac)
    state.temp_scale = location.getTemperatureScale()
    setVersion()
    version()
}

def uninstalled() {
  if (logEnable) log.info "Disconnecting from mqtt"
  interfaces.mqtt.disconnect()
}

def initialize() {
    if (logEnable) runIn(900,logsOff) // clears debugging after 900 secs 
    if (logEnable) log.info "Initalize..."
	try {
    def mqttInt = interfaces.mqtt
    //open connection
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(1000)
    log.info "Connection established"
    def topic = settings?.topicSub + "/status/#"
    mqttInt.subscribe(topic)
        if (logEnable) log.debug "Subscribed to: ${topic}"
  } catch(e) {
    if (logEnable) log.debug "Initialize error: ${e.message}"
  }
}


def mqttClientStatus(String status) {
  if (status.startsWith("Error")) {
    def restart = false
    if (! interfaces.mqtt.isConnected()) {
      log.warn "mqtt isConnected false"
      restart = true
    }  else if (status.contains("lost")) {
      log.warn "mqtt Connection lost detected"
      restart = true
    } else {
      log.warn "mqtt error: ${status}"
    }
    if (restart) {
      def i = 0
      while (i < 60) {
        // wait for a minute for things to settle out, server to restart, etc...
        pauseExecution(1000*60)
        initialize()
        if (interfaces.mqtt.isConnected()) {
          log.warn "mqtt reconnect success!"
          break
        }
        i = i + 1
      }
    }
  } else {
    if (logEnable) log.warn "mqtt OK: ${status}"
  }
}

def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (github CobraVmax) for his original code **************
def version(){
	updatecheck()
	schedule("0 0 18 1/1 * ? *", updatecheck) // Cron schedule
//	schedule("0 0/1 * 1/1 * ? *", updatecheck) // Test Cron schedule
}

private dbCleanUp() {
//	unschedule()
    state.remove("ps")
}

def updatecheck(){
    setVersion()
	 def paramsUD = [uri: "https://raw.githubusercontent.com/ShellyUSA/Hubitat-Drivers/master/resources/version.json", contentType: "application/json; charset=utf-8"]
	  try {
			httpGet(paramsUD) { respUD ->
				  if (debugParse) log.warn " Version Checking - Response Data: ${respUD.data}"
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
