import groovy.transform.Field

metadata {
  definition (name: 'Shelly Autoconf EM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Refresh'
    capability 'CurrentMeter' //amperage - NUMBER, unit:A
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'VoltageMeasurement' //voltage - NUMBER, unit:V //frequency - NUMBER, unit:Hz
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    attribute 'powerFactor', 'number'
    attribute 'reactivePower', 'number' //unit:VAR
    attribute 'energyReturned', 'number' //unit:kWh
    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true

void installed() { }

void updated() { }

void refresh() { parent?.refresh() }
