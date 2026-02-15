metadata {
  definition (name: 'Shelly Autoconf Temperature Peripheral', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
