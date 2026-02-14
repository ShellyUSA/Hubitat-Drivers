#!/usr/bin/env python3
"""
Push Hubitat drivers and apps to the hub.
Usage: python push_to_hubitat.py <file_path>
"""

import sys
import requests
from pathlib import Path
from urllib.parse import urlencode

# Configuration
HUBITAT_IP = "192.168.1.4"
TIMEOUT_MS = 60000
BASE_URL = f"http://{HUBITAT_IP}"

# Code IDs (version is automatically fetched from Hubitat)
CODE_IDS = {
    "Apps/ShellyMdnsDiscovery.groovy": {
        "type": "app",
        "id": "268"
    },
    # Add more mappings as needed
    # "UniversalDrivers/ShellySingleSwitch.groovy": {
    #     "type": "driver",
    #     "id": "123"
    # }
}


def get_code_info(file_path):
    """Determine code type and ID from file path."""
    # Normalize path - handle both absolute and relative paths
    file_path_obj = Path(file_path)
    if file_path_obj.is_absolute():
        try:
            rel_path = str(file_path_obj.relative_to(Path.cwd()))
        except ValueError:
            # If can't make relative, just use the filename portion
            rel_path = file_path
    else:
        rel_path = file_path

    if rel_path in CODE_IDS:
        return CODE_IDS[rel_path]

    # Try to infer type from path
    if "Apps/" in rel_path or "apps/" in rel_path:
        code_type = "app"
    elif "UniversalDrivers/" in rel_path or "Drivers/" in rel_path or "drivers/" in rel_path:
        code_type = "driver"
    else:
        raise ValueError(f"Cannot determine code type for {rel_path}. Please add to CODE_IDS.")

    raise ValueError(f"No ID mapping found for {rel_path}. Please add to CODE_IDS.")


def get_current_version(code_type, code_id):
    """Fetch current version from Hubitat."""
    url = f"{BASE_URL}/{code_type}/ajax/code"
    params = {"id": code_id}
    headers = {"Accept": "application/json"}

    try:
        response = requests.get(url, params=params, headers=headers, timeout=10)
        if response.status_code == 200:
            data = response.json()
            return data.get("version", "1")
    except:
        pass
    return "1"


def push_code(file_path):
    """Push code file to Hubitat hub."""
    # Read source code
    try:
        with open(file_path, 'r') as f:
            source_code = f.read()
    except FileNotFoundError:
        print(f"Error: File not found: {file_path}")
        return False

    # Get code info
    try:
        code_info = get_code_info(file_path)
    except ValueError as e:
        print(f"Error: {e}")
        return False

    code_type = code_info["type"]
    code_id = code_info["id"]

    # Always fetch current version from Hubitat
    version = get_current_version(code_type, code_id)

    # Prepare request
    url = f"{BASE_URL}/{code_type}/ajax/update"

    # URL-encode the body
    body = {
        "id": code_id,
        "version": version,
        "source": source_code
    }

    headers = {
        "Accept": "application/json",
        "Content-Type": "application/x-www-form-urlencoded"
    }

    # Send request
    print(f"Pushing {code_type} to Hubitat hub at {HUBITAT_IP}...")
    print(f"  Type: {code_type}")
    print(f"  ID: {code_id}")
    print(f"  File: {file_path}")
    print(f"  Size: {len(source_code)} bytes")

    try:
        response = requests.post(
            url,
            data=urlencode(body),
            headers=headers,
            timeout=TIMEOUT_MS / 1000  # Convert to seconds
        )

        # Check response
        if response.status_code == 200:
            result = response.json()
            if result.get("status") == "success":
                print(f"✓ Successfully pushed to Hubitat!")
                print(f"  New version: {result.get('version', 'unknown')}")
                return True
            else:
                print(f"✗ Push failed: {result.get('errorMessage', 'Unknown error')}")
                return False
        else:
            print(f"✗ HTTP error {response.status_code}: {response.text}")
            return False

    except requests.exceptions.Timeout:
        print(f"✗ Request timed out after {TIMEOUT_MS}ms")
        return False
    except requests.exceptions.RequestException as e:
        print(f"✗ Request error: {e}")
        return False


def main():
    if len(sys.argv) != 2:
        print("Usage: python push_to_hubitat.py <file_path>")
        print("\nConfigured files:")
        for path in CODE_IDS.keys():
            print(f"  - {path}")
        sys.exit(1)

    file_path = sys.argv[1]
    success = push_code(file_path)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
