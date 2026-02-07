/**
 * Version: 2.1.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Bulb RGBW', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Bulb'
    capability 'Light'
    capability 'ColorTemperature'  //colorName - STRING colorTemperature - NUMBER, unit:°K
    capability 'ColorMode'  //colorMode - ENUM ["CT", "RGB", "EFFECTS"]
    capability 'ColorControl'
      //RGB - STRING
      //color - STRING
      //colorName - STRING
      //hue - NUMBER
      //saturation - NUMBER, unit:%
  }
}

@Field static Boolean GEN1 = true
@Field static Integer COOLTEMP = 6500
@Field static Integer WARMTEMP = 3000
@Field static Boolean NOCHILDREN = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
]
