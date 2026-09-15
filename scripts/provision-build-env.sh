#!/usr/bin/env bash
# One-time build-environment provisioning for this machine (Linux).
# Installs JDK 17 (Temurin) + Android SDK cmdline-tools/platform/build-tools.
set -euo pipefail

TOOLS="$HOME/tools"
mkdir -p "$TOOLS"
cd "$TOOLS"

if [ ! -x "$TOOLS/jdk17/bin/java" ]; then
  echo "[1/3] Downloading Temurin JDK 17..."
  curl -sL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p jdk17
  tar xzf jdk17.tar.gz -C jdk17 --strip-components=1
  rm jdk17.tar.gz
fi
echo "JDK: $($TOOLS/jdk17/bin/java -version 2>&1 | head -1)"

export JAVA_HOME="$TOOLS/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -d "$TOOLS/android-sdk/platforms/android-35" ]; then
  echo "[2/3] Downloading Android cmdline-tools..."
  curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$TOOLS/android-sdk/cmdline-tools"
  unzip -qo cmdtools.zip -d "$TOOLS/android-sdk/cmdline-tools"
  rm -f cmdtools.zip
  [ -d "$TOOLS/android-sdk/cmdline-tools/latest" ] || mv "$TOOLS/android-sdk/cmdline-tools/cmdline-tools" "$TOOLS/android-sdk/cmdline-tools/latest"

  echo "[3/3] Installing SDK packages (platform-35, build-tools, platform-tools)..."
  yes | "$TOOLS/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$TOOLS/android-sdk" --licenses >/dev/null || true
  "$TOOLS/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$TOOLS/android-sdk" \
    "platform-tools" "platforms;android-35" "build-tools;34.0.0"
fi

echo "PROVISIONING_DONE"
