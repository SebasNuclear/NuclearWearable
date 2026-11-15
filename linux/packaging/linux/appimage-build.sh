#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIST_DIR="$ROOT_DIR/build/compose/binaries/main/app/NuclearWearable"
STAGE_DIR="$ROOT_DIR/build/appimage/NuclearWearable.AppDir"
OUT_DIR="$ROOT_DIR/build/compose/binaries/main/appimage"

cd "$ROOT_DIR"
chmod +x ./gradlew
./gradlew createDistributable --no-daemon --console=plain

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/usr/bin" "$STAGE_DIR/usr/lib" "$STAGE_DIR/usr/share/applications" "$STAGE_DIR/usr/share/icons/hicolor/512x512/apps" "$OUT_DIR"

cp -a "$DIST_DIR/." "$STAGE_DIR/usr/"
cp "$ROOT_DIR/src/main/resources/drawable/ic_app_icon.png" "$STAGE_DIR/nuclearwearable.png"
cp "$ROOT_DIR/src/main/resources/drawable/ic_app_icon.png" "$STAGE_DIR/usr/share/icons/hicolor/512x512/apps/nuclearwearable.png"
cp "$ROOT_DIR/packaging/linux/nuclearwearable.desktop" "$STAGE_DIR/nuclearwearable.desktop"
sed -i 's|^Exec=.*|Exec=NuclearWearable|' "$STAGE_DIR/nuclearwearable.desktop"
cp "$STAGE_DIR/nuclearwearable.desktop" "$STAGE_DIR/usr/share/applications/nuclearwearable.desktop"

cat > "$STAGE_DIR/AppRun" <<'EOF'
#!/usr/bin/env bash
HERE="$(cd "$(dirname "$0")" && pwd)"
exec "$HERE/usr/bin/NuclearWearable" "$@"
EOF
chmod +x "$STAGE_DIR/AppRun"

if command -v appimagetool >/dev/null 2>&1; then
    ARCH="$(uname -m)"
    appimagetool "$STAGE_DIR" "$OUT_DIR/NuclearWearable-1.1.0-${ARCH}.AppImage"
    echo "AppImage created in: $OUT_DIR"
else
    echo "AppDir prepared at: $STAGE_DIR"
    echo "Install 'appimagetool' and run: appimagetool \"$STAGE_DIR\" \"$OUT_DIR/NuclearWearable-1.1.0-\$(uname -m).AppImage\""
fi
