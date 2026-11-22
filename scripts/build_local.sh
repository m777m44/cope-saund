#!/usr/bin/env bash
# Builds a release APK locally. Requires JDK 17 + Android SDK.
# Signing: create signing/keystore.properties from signing/keystore.properties.example
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f signing/keystore.properties ]; then
  echo "No signing/keystore.properties - building unsigned APK"
fi
gradle :app:assembleRelease
find app/build/outputs/apk/release -name '*.apk'
