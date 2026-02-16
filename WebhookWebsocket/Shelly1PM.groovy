/**
 * Version: 2.16.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly 1PM (Webhook)', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'Switch'
    capability 'PowerMeter' //power - NUMBER, unit:W
    capability 'EnergyMeter' //energy - NUMBER, unit:kWh
  }
}

preferences {
  input name: 'pmReportingInterval', type: 'number', title: 'Power Monitoring Reporting Interval (seconds)',
    required: false, defaultValue: 60, range: '5..3600'
}

@Field static Boolean GEN1 = true
@Field static Boolean HAS_PM_GEN1 = true
@Field static Boolean NOCHILDREN = true

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

@Field static List<String> ACTIONS_TO_CREATE = [
  'out_on_url',
  'out_off_url',
  'btn_on_url',
  'btn_off_url',
  'longpush_url',
  'shortpush_url'
]