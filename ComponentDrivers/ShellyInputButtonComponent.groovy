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
// =============================================================================
// Device Specific
// =============================================================================
@CompileStatic
void push(Integer buttonNumber) {sendDeviceEvent([name: 'pushed', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void hold(Integer buttonNumber) {sendDeviceEvent([name: 'held', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void doubleTap(Integer buttonNumber) {sendDeviceEvent([name: 'doubleTapped', value: buttonNumber, isStateChange: true]) }

@CompileStatic
void deviceSpecificConfigure() {sendDeviceEvent([name: 'numberOfButtons', value: 1])}
// =============================================================================
// End Device Specific
// =============================================================================