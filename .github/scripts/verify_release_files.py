#!/usr/bin/env python3
"""Verify that all files referenced in packageManifest.json exist as release artifacts."""

import json
import sys
from pathlib import Path


def main():
    errors = []
    checked = 0

    for mf in Path('PackageManifests').rglob('packageManifest.json'):
        with open(mf) as f:
            data = json.load(f)

        for bundle in data.get('bundles', []):
            fn = bundle['location'].split('/')[-1]
            if not Path(fn).exists():
                errors.append(f"Missing bundle: {fn}")
            else:
                print(f"  OK bundle: {fn}")
            checked += 1

        for section in ('apps', 'drivers'):
            for entry in data.get(section, []):
                fn = entry['location'].split('/')[-1]
                if not Path(f'release-files/{fn}').exists():
                    errors.append(f"Missing {section}: {fn}")
                else:
                    print(f"  OK {section}: {fn}")
                checked += 1

    print(f"\nChecked {checked} files")
    if errors:
        print(f"\nERRORS ({len(errors)}):")
        for e in errors:
            print(f"  {e}")
        sys.exit(1)
    else:
        print("All manifest files verified!")


if __name__ == '__main__':
    main()
