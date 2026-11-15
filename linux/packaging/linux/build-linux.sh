#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

echo "=== [1/4] Verifying toolchain ==="
java -version
chmod +x ./gradlew

echo "=== [2/4] Running unit tests (protocol, BlueZ, SDP, PipeWire/PulseAudio, backup, automation) ==="
./gradlew test --no-daemon --console=plain

echo "=== [3/4] Building portable Linux distributable image ==="
./gradlew createDistributable --no-daemon --console=plain

APP_DIR="$ROOT_DIR/build/compose/binaries/main/app/NuclearWearable"
echo "Portable app image ready at: $APP_DIR"

echo "=== [4/4] Building native Linux installers (.deb / .rpm / .AppImage when jpackage supports) ==="
if command -v dpkg-deb >/dev/null 2>&1 || command -v fakeroot >/dev/null 2>&1; then
    ./gradlew packageDeb --no-daemon --console=plain || echo "packageDeb skipped or failed (check jpackage/dpkg tools)"
fi

if command -v rpmbuild >/dev/null 2>&1; then
    ./gradlew packageRpm --no-daemon --console=plain || echo "packageRpm skipped or failed"
fi

echo "Done! Outputs in: $ROOT_DIR/build/compose/binaries/main/"
