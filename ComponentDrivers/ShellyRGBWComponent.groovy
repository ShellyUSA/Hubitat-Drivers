/**
 * Version: 2.0.8
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly RGBW Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'
    capability 'ColorControl'
      //RGB - STRING
      //color - STRING
      //colorName - STRING
      //hue - NUMBER
      //saturation - NUMBER, unit:%
    command 'setWhiteLevel', [
      [name:"White level*", type:"NUMBER", description:"White channel level (0 to 100)"]
    ]
    capability 'Refresh'
    attribute 'lastUpdated', 'string'
    attribute 'whiteLevel', 'number'
  }
}

@Field static Boolean COMP = true
