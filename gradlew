#!/usr/bin/env sh
# Lightweight Gradle bootstrapper for this project. Downloads the pinned distribution once.
set -eu
GRADLE_VERSION="9.5.0"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_ROOT="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/aetherwave"
GRADLE_BIN="$DIST_ROOT/gradle-${GRADLE_VERSION}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_ROOT"
  ARCHIVE="$DIST_ROOT/gradle-${GRADLE_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" --output "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "curl or wget is required to download Gradle ${GRADLE_VERSION}." >&2
    exit 1
  fi
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to extract Gradle." >&2; exit 1; }
  unzip -q -o "$ARCHIVE" -d "$DIST_ROOT"
  rm -f "$ARCHIVE"
fi
exec "$GRADLE_BIN" "$@"
