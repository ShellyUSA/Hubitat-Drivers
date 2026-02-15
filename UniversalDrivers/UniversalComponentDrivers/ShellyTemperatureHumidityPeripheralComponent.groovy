metadata {
  definition (name: 'Shelly Autoconf Temperature & Humidity Peripheral', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'RelativeHumidityMeasurement' //humidity - NUMBER, unit:%rh
    capability 'TemperatureMeasurement' //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
