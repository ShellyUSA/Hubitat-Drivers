metadata {
  definition (name: 'Shelly Autoconf Polling Voltage Sensor', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
@Field static Boolean HAS_ADC_GEN1 = true
