metadata {
  definition (name: 'Shelly Autoconf Input Count', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Sensor'
    capability 'Refresh'

    attribute 'count', 'number'
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
