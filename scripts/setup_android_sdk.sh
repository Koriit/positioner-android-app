#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

mkdir -p "$SDK_ROOT"

if [ ! -f "$SDK_ROOT/cmdline-tools/bin/sdkmanager" ]; then
  TMP_DIR="$(mktemp -d)"
  curl -sSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$TMP_DIR/tools.zip"
  unzip -q "$TMP_DIR/tools.zip" -d "$SDK_ROOT"
  rm -rf "$TMP_DIR"
fi

yes | "$SDK_ROOT/cmdline-tools/bin/sdkmanager" --sdk_root="$SDK_ROOT" --licenses &&
"$SDK_ROOT/cmdline-tools/bin/sdkmanager" --sdk_root="$SDK_ROOT" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"

cat > "$REPO_DIR/local.properties" <<EOL
sdk.dir=$SDK_ROOT
EOL

echo "Android SDK installed at $SDK_ROOT"
