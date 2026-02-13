/**
 * Version: 1.0.0
 */

metadata {
  definition (name: 'Shelly Single Switch', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Switch'
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    command 'resetEnergyMonitors'
  }
}

@Field static Boolean NOCHILDSWITCH = true
