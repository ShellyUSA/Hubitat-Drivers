"""Regression guards for Shelly 1 Mini Gen4 detached-input integration."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP = (ROOT / "Apps/ShellyDeviceManager.groovy").read_text()
RELAY = (ROOT / "UniversalDrivers/ShellySingleSwitch.groovy").read_text()


class DetachedInputRegressionTests(unittest.TestCase):
    def test_mini_gen4_keeps_relay_device_and_adds_input_child(self):
        self.assertIn("dataMap.detachedInputIds", APP)
        self.assertIn("existingDevice.initialize()", APP)
        self.assertIn("${device.deviceNetworkId}-input-switch-${inputId}", RELAY)

    def test_physical_input_has_a_dedicated_switch_child(self):
        self.assertIn("ShellyInputSwitchComponent.groovy", APP)
        self.assertIn("Shelly Autoconf Input Switch", RELAY)
        self.assertIn("child.sendEvent(name: 'switch', value: value", RELAY)

    def test_authoritative_refresh_updates_input_child_not_relay(self):
        input_case = APP[APP.index("case 'input':", APP.index("private void updateChildFromStatus")):]
        self.assertIn("statusData.state", input_case)
        self.assertIn("name: 'switch'", input_case)
        self.assertIn("detachedInputIds.split(',')", APP)

    def test_webhook_input_toggle_routes_to_input_component(self):
        self.assertIn("case 'input_toggle_on':", RELAY)
        self.assertIn("case 'input_toggle_off':", RELAY)
        self.assertIn("case 'input_toggle':", RELAY)
        self.assertIn("updatePhysicalInput(params, 'on')", RELAY)

    def test_standalone_relay_drivers_ignore_physical_input_notifications(self):
        route = RELAY[RELAY.index("private void routeWebhookParams"):]
        self.assertIn("updatePhysicalInput(params, 'on')", route)
        self.assertNotIn("Switch turned on (input toggle)", route)
        pm = (ROOT / "UniversalDrivers/ShellySingleSwitchPM.groovy").read_text()
        self.assertIn("Ignoring physical input", pm[pm.index("private void routeWebhookParams"):])


if __name__ == "__main__":
    unittest.main()
