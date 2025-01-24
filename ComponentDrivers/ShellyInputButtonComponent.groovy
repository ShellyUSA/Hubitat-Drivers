#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Input Button Component', namespace: 'ShellyUSA', author: 'Daniel Winks', singleThreaded: false, importUrl: '') {
    capability 'PushableButton' //numberOfButtons - NUMBER, pushed - NUMBER
    capability 'DoubleTapableButton' //doubleTapped - NUMBER
    capability 'HoldableButton' //held - NUMBER
    capability 'Refresh'

    attribute 'lastUpdated', 'string'
  }
}

@Field static Boolean COMP = true
@Field static Integer BUTTONS = 1

