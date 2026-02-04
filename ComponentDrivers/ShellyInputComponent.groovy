/**
 * Version: 2.0.2
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Button Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PushableButton' //numberOfButtons - NUMBER, pushed - NUMBER
    capability 'DoubleTapableButton' //doubleTapped - NUMBER
    capability 'HoldableButton' //held - NUMBER
    capability 'Refresh'
    command 'tripleTap'
    attribute 'tripleTapped', 'number'
    capability 'Sensor'

    attribute 'count', 'number'

  }
}

@Field static Boolean COMP = true
@Field static Integer BUTTONS = 1

