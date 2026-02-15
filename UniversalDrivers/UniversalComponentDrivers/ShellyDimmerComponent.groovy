metadata {
  definition (name: 'Shelly Autoconf Dimmer', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Light' //switch - ENUM ["on", "off"]
    capability 'SwitchLevel' //level - NUMBER, unit:%
    capability 'ChangeLevel'

    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
