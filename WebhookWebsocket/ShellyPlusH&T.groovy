#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Plus H&T Webhook', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability "Battery" //battery - NUMBER, unit:%
    capability "RelativeHumidityMeasurement" //humidity - NUMBER, unit:%rh
    capability "TemperatureMeasurement" //temperature - NUMBER, unit:°F || °C

    attribute 'lastUpdated', 'string'
  }
}

if(device != null) {preferences{}}

// =============================================================================
// Initialize And Configure
// =============================================================================
void initialize() {configure()}
void configure() {
  this.device.setDeviceNetworkId(convertIPToHex(settings?.ipAddress))
  setDeviceWebhooks()
}

@CompileStatic
void parse(String raw) {
  getBatteryStatus()
  LinkedHashMap message = decodeLanMessage(raw)
  LinkedHashMap headers = message?.headers as LinkedHashMap
  List<String> res = ((String)headers.keySet()[0]).tokenize(' ')
  List<String> query = ((String)res[1]).tokenize('/')
  if(query[0] == 'humidity.change') {setHumidityPercent(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tC' && isCelciusScale()) {setTemperatureC(new BigDecimal(query[2]))}
  else if(query[0] == 'temperature.change' && query[1] == 'tF' && !isCelciusScale()) {setTemperatureF(new BigDecimal(query[2]))}
  setLastUpdated()
}

@CompileStatic
void getBatteryStatus() {
  LinkedHashMap response = (LinkedHashMap)postCommandSync(devicePowerGetStatusCommand())
  LinkedHashMap result = (LinkedHashMap)response?.result
  LinkedHashMap battery = (LinkedHashMap)result?.battery
  Integer percent = battery?.percent as Integer
  setBatteryPercent(percent)
}
