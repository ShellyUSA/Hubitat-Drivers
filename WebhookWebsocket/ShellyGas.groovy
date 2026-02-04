/**
 * Version: 2.0.0
 */
#include ShellyUSA.ShellyUSA_Driver_Library

metadata {
  definition (name: 'Shelly Gas', namespace: 'ShellyUSA', author: 'Daniel Winks', importUrl: '') {
    capability 'Initialize'
    capability 'Configuration'
    capability 'Refresh'
    capability 'GasDetector' //naturalGas - ENUM ['clear', 'tested', 'detected']
    capability 'Valve' //valve - ENUM ['open', 'closed']
    attribute 'lastUpdated', 'string'
    attribute 'ppm', 'number'
    attribute 'selfTestState', 'string'
    command 'selfTest'
    command 'mute'
    command 'unmute'
  }
}

@Field static Boolean GEN1 = true

@CompileStatic
void selfTest() {sendGen1CommandAsync('self_test')}

@CompileStatic
void mute() {sendGen1CommandAsync('mute')}

@CompileStatic
void unmute() {sendGen1CommandAsync('unmute')}

