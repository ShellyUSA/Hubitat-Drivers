metadata {
  definition (name: 'Shelly RGB Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'
    capability 'ColorControl'
      //RGB - STRING
      //color - STRING
      //colorName - STRING
      //hue - NUMBER
      //saturation - NUMBER, unit:%

    capability 'Refresh'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
