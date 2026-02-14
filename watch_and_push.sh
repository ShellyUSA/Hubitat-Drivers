#!/bin/bash
# Watch ShellyMdnsDiscovery.groovy and auto-push on save

cd /Users/danielwinks/Code/Hubitat-Drivers

echo "Pushing initial version to Hubitat..."
python3 push_to_hubitat.py Apps/ShellyMdnsDiscovery.groovy
echo ""
echo "Watching Apps/ShellyMdnsDiscovery.groovy for changes..."
echo "Press Ctrl+C to stop"
echo ""

fswatch -o Apps/ShellyMdnsDiscovery.groovy | while read f; do
  echo ""
  echo "==> File changed, pushing to Hubitat..."
  python3 push_to_hubitat.py Apps/ShellyMdnsDiscovery.groovy
  echo "==> Watching for next change..."
done
