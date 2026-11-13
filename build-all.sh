#!/usr/bin/env bash
# Builds Price Share for every supported Minecraft version into dist/.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf dist && mkdir -p dist
for mc in 1.21.11 26.1 26.2 26.3; do
  echo "== Minecraft $mc"
  ./gradlew clean build -Pmc="$mc" --console=plain -q
  cp build/libs/priceshare-*+mc"$mc".jar dist/
done
ls -l dist
