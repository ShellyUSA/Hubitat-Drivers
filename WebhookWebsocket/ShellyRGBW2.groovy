/**
 * Version: 2.0.6
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly RGBW2 (Gen1)', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'Bulb'
    capability 'Light'
    capability 'ColorControl'
      //RGB - STRING
      //color - STRING
      //colorName - STRING
      //hue - NUMBER
      //saturation - NUMBER, unit:%
    command 'setWhiteLevel', [
      [name:"White level*", type:"NUMBER", description:"White channel level (0 to 100)"]
    ]
    attribute 'whiteLevel', 'number'
  }
}

@Field static Boolean GEN1 = true
@Field static Boolean NOCHILDREN = true
@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url'
]
