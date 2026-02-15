metadata {
  definition (name: 'Shelly Autoconf Humidity Peripheral', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
