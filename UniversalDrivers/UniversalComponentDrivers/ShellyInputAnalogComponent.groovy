metadata {
  definition (name: 'Shelly Autoconf Input Analog', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'analogValue', 'number'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
