metadata {
  definition (name: 'Shelly Autoconf Input Switch', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
@Field static Boolean INPUTSWITCH = true
