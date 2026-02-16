/**
 * Version: 2.16.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Pro EM', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
    capability 'Switch'
    attribute 'returnedEnergy', 'number' //unit:kWh
    command 'resetEnergyMonitors'
  }
}

preferences {
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
}

@Field static Boolean WS = true
@Field static Boolean DEVICEISBLUGATEWAY = true

void deviceSpecificConfigure() {
  sendPmReportingIntervalToKVS()
}

private void sendPmReportingIntervalToKVS() {
  Integer interval = settings?.pmReportingInterval != null ? settings.pmReportingInterval as Integer : 60
  logDebug("Sending PM reporting interval (${interval}s) to device KVS as 'pm_ri'")
  LinkedHashMap command = [
    "id" : 0, "src" : "kvsSet", "method" : "KVS.Set",
    "params" : ["key": "pm_ri", "value": interval]
  ]
  postCommandSync(command)
}
