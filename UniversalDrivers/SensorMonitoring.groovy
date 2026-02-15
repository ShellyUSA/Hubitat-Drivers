// ╔══════════════════════════════════════════════════════════════╗
// ║  Sensor Monitoring - Temperature, Humidity & Battery Parsing ║
// ╚══════════════════════════════════════════════════════════════╝

/**
 * Parses temperature notifications from Shelly device.
 * Processes JSON with dst:"temperature" and updates the temperature attribute.
 * Battery data is now delivered via webhook URL supplemental tokens.
 * JSON format: [dst:temperature, result:[temperature:0:[id:0, tC:24.4, tF:75.9]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseTemperature(Map json) {
  logDebug("parseTemperature() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseTemperature: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        BigDecimal tempC = value.tC != null ? value.tC as BigDecimal : null
        BigDecimal tempF = value.tF != null ? value.tF as BigDecimal : null

        if (tempC != null || tempF != null) {
          // Use hub's temperature scale preference
          String scale = location.temperatureScale ?: 'F'
          BigDecimal temp = (scale == 'C') ? tempC : (tempF ?: tempC)
          String unit = "\u00B0${scale}"

          if (temp != null) {
            def currentTemp = device.currentValue('temperature')
            if (currentTemp == null || (currentTemp as BigDecimal) != temp) {
              sendEvent(name: "temperature", value: temp, unit: unit,
                descriptionText: "Temperature is ${temp}${unit}")
              logInfo("Temperature: ${temp}${unit}")
            } else {
              logDebug("Temperature unchanged: ${temp}${unit}")
            }
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseTemperature exception: ${e.message}")
  }
}

/**
 * Parses humidity notifications from Shelly device.
 * Processes JSON with dst:"humidity" and updates the humidity attribute.
 * Battery data is now delivered via webhook URL supplemental tokens.
 * JSON format: [dst:humidity, result:[humidity:0:[id:0, rh:73.7]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseHumidity(Map json) {
  logDebug("parseHumidity() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseHumidity: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map && value.rh != null) {
        BigDecimal humidity = value.rh as BigDecimal
        def currentHumidity = device.currentValue('humidity')
        if (currentHumidity == null || (currentHumidity as BigDecimal) != humidity) {
          sendEvent(name: "humidity", value: humidity, unit: "%",
            descriptionText: "Humidity is ${humidity}%")
          logInfo("Humidity: ${humidity}%")
        } else {
          logDebug("Humidity unchanged: ${humidity}%")
        }
      }
    }
  } catch (Exception e) {
    logError("parseHumidity exception: ${e.message}")
  }
}

/**
 * Parses battery/device power notifications from Shelly device.
 * Processes JSON with dst:"battery" and updates the battery attribute.
 * Only sends an event if the battery percentage has actually changed.
 * JSON format: [dst:battery, result:[devicepower:0:[id:0, battery:[V:4.87, percent:50], external:[present:false]]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseBattery(Map json) {
  logDebug("parseBattery() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseBattery: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        Map battery = value.battery
        if (battery?.percent != null) {
          Integer batteryPct = battery.percent as Integer
          def currentBattery = device.currentValue('battery')
          if (currentBattery == null || (currentBattery as Integer) != batteryPct) {
            sendEvent(name: "battery", value: batteryPct, unit: "%",
              descriptionText: "Battery is ${batteryPct}%")
            logInfo("Battery: ${batteryPct}%")
          } else {
            logDebug("Battery unchanged: ${batteryPct}%")
          }
        }
        if (battery?.V != null) {
          BigDecimal voltage = battery.V as BigDecimal
          logDebug("Battery voltage: ${voltage}V")
        }
      }
    }
  } catch (Exception e) {
    logError("parseBattery exception: ${e.message}")
  }
}

/**
 * Parses smoke alarm notifications from Shelly device.
 * Processes JSON with dst:"smoke" and updates the smoke attribute.
 * JSON format: [dst:smoke, result:[smoke:0:[id:0, alarm:true]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseSmoke(Map json) {
  logDebug("parseSmoke() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseSmoke: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map) {
        Boolean alarm = value.alarm
        if (alarm != null) {
          String smokeState = alarm ? "detected" : "clear"
          def currentSmoke = device.currentValue('smoke')
          if (currentSmoke == null || currentSmoke != smokeState) {
            sendEvent(name: "smoke", value: smokeState,
              descriptionText: "Smoke ${smokeState}")
            logInfo("Smoke: ${smokeState}")
          } else {
            logDebug("Smoke unchanged: ${smokeState}")
          }
        }
      }
    }
  } catch (Exception e) {
    logError("parseSmoke exception: ${e.message}")
  }
}

/**
 * Parses illuminance notifications from Shelly device.
 * Processes JSON with dst:"illuminance" and updates the illuminance attribute.
 * JSON format: [dst:illuminance, result:[illuminance:0:[id:0, lux:350]]]
 *
 * @param json The parsed JSON notification from the Shelly device
 */
void parseIlluminance(Map json) {
  logDebug("parseIlluminance() called with: ${json}")

  try {
    Map result = json?.result
    if (!result) {
      logWarn("parseIlluminance: No result data in JSON")
      return
    }

    result.each { key, value ->
      if (value instanceof Map && value.lux != null) {
        Integer lux = value.lux as Integer
        def currentLux = device.currentValue('illuminance')
        if (currentLux == null || (currentLux as Integer) != lux) {
          sendEvent(name: "illuminance", value: lux, unit: "lux",
            descriptionText: "Illuminance is ${lux} lux")
          logInfo("Illuminance: ${lux} lux")
        } else {
          logDebug("Illuminance unchanged: ${lux} lux")
        }
      }
    }
  } catch (Exception e) {
    logError("parseIlluminance exception: ${e.message}")
  }
}

// ╔══════════════════════════════════════════════════════════════╗
// ║  END Sensor Monitoring                                       ║
// ╚══════════════════════════════════════════════════════════════╝
