"""Regression guards for detached physical input handling across Shelly drivers."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP = (ROOT / "Apps/ShellyDeviceManager.groovy").read_text(encoding="utf-8")
ROLLER_SHUTTER = (ROOT / "Shelly2-as-Roller-Shutter.groovy").read_text(encoding="utf-8")


class DetachedInputRegressionTests(unittest.TestCase):
    def test_discovery_uses_configured_switch_inputs_not_model_names(self):
        self.assertIn("private List<Integer> getSwitchInputIds", APP)
        self.assertIn("inputTypes[id] == 'switch'", APP)
        self.assertIn("getSwitchInputIds(ipKey, deviceStatus)", APP)
        self.assertIn("inputSwitchIds", APP)
        self.assertNotIn("detachedInputIds", APP)
        self.assertNotIn("isShelly1MiniGen4Info", APP)

    def test_switch_input_driver_is_installed_and_refresh_synced(self):
        self.assertIn("ShellyInputSwitchComponent.groovy", APP)
        self.assertIn("Shelly Autoconf Input Switch", APP)
        update = APP[APP.index("private void updateChildFromStatus"):]
        input_case = update[update.index("case 'input':"):]
        self.assertIn("statusData.state", input_case)
        self.assertIn("name: 'switch'", input_case)
        self.assertIn("Input Switch", input_case)

    def test_single_output_drivers_route_inputs_to_separate_children(self):
        drivers = (
            "ShellySingleSwitch.groovy",
            "ShellySingleSwitchPM.groovy",
            "ShellySingleDimmer.groovy",
            "ShellySingleDimmerPM.groovy",
            "ShellyDaliDimmer.groovy",
        )
        for filename in drivers:
            with self.subTest(driver=filename):
                source = (ROOT / "UniversalDrivers" / filename).read_text(encoding="utf-8")
                self.assertIn("inputSwitchIds", source)
                self.assertIn("updatePhysicalInput", source)
                self.assertIn("input_toggle_on", source)
                self.assertIn("input_toggle_off", source)
                self.assertIn("input_toggle", source)

    def test_autoconf_parent_creates_switch_children_for_switch_inputs(self):
        parent = (ROOT / "UniversalDrivers/ShellyAutoconfParent.groovy").read_text(encoding="utf-8")
        self.assertIn("inputSwitchIds", parent)
        self.assertIn("Shelly Autoconf Input Switch", parent)

    def test_multi_switch_parents_route_configured_switch_inputs_to_children(self):
        for filename in ("Shelly2xSwitchParent.groovy", "Shelly3xSwitchParent.groovy"):
            with self.subTest(driver=filename):
                source = (ROOT / "UniversalDrivers" / filename).read_text(encoding="utf-8")
                self.assertIn("isSwitchInput(componentId)", source)
                self.assertIn("Shelly Autoconf Input Switch", source)
                self.assertIn("v.state", source)


class RollerShutterRegressionTests(unittest.TestCase):
    def test_refresh_reports_open_window_shade_capability_value(self):
        self.assertIn('sendEvent(name: "windowShade", value: "open")', ROLLER_SHUTTER)
        self.assertNotIn('sendEvent(name: "windowShade", value: "on")', ROLLER_SHUTTER)


if __name__ == "__main__":
    unittest.main()
