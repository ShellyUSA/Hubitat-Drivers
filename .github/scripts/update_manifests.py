#!/usr/bin/env python3
"""Update package manifest files with release URLs for a given version."""

import json
import re
import sys
from pathlib import Path


def strip_version_suffix(filename):
    """Remove -vX.Y.Z suffix from a filename (before extension)."""
    return re.sub(r'-v\d+\.\d+\.\d+(?=\.(groovy|zip)$)', '', filename)


def sanitize_filename(filename):
    """Replace & and %26 with . to match GitHub Release asset name mangling."""
    return filename.replace('%26', '.').replace('&', '.')


def main():
    if len(sys.argv) < 2:
        print("Usage: update_manifests.py <new_version>")
        sys.exit(1)

    new_version = sys.argv[1]
    release_base_url = (
        f'https://github.com/ShellyUSA/Hubitat-Drivers/releases/download/v{new_version}'
    )

    manifest_dir = Path('PackageManifests')
    manifest_files = list(manifest_dir.rglob('packageManifest.json'))
    if not manifest_files:
        print("No package manifest files found!")
        sys.exit(1)

    for manifest_path in manifest_files:
        print(f"Updating {manifest_path}...")
        with open(manifest_path, 'r') as f:
            data = json.load(f)

        data['version'] = new_version
        data['releaseNotes'] = (
            f'https://github.com/ShellyUSA/Hubitat-Drivers/releases/tag/v{new_version}'
        )

        if 'bundles' in data:
            for bundle in data['bundles']:
                if 'location' in bundle:
                    bundle['location'] = (
                        f'{release_base_url}/ShellyUSA_Driver_Library-v{new_version}.zip'
                    )
                    print(f'  bundle: {bundle["location"]}')

        for section in ('apps', 'drivers'):
            if section in data:
                for entry in data[section]:
                    if 'location' in entry:
                        old_filename = entry['location'].split('/')[-1]
                        clean = strip_version_suffix(old_filename)
                        clean = sanitize_filename(clean)
                        name_part, ext = clean.rsplit('.', 1)
                        new_filename = f'{name_part}-v{new_version}.{ext}'
                        entry['location'] = f'{release_base_url}/{new_filename}'
                        print(f'  {section}: {old_filename} -> {new_filename}')

        with open(manifest_path, 'w') as f:
            json.dump(data, f, indent=2)
            f.write('\n')

        print(f"Updated {manifest_path} to v{new_version}")


if __name__ == '__main__':
    main()
