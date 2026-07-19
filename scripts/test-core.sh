#!/usr/bin/env bash
# Run core tests locally in a single JVM via the JUnit ConsoleLauncher.
#
# Why not `gradle :core:test`? Gradle forks test-worker JVMs that connect
# back over random loopback ports — which the tenant sandbox blocks
# (empirically confirmed: workers die with ConnectException). CI runs the
# normal Gradle test task; this script is the sandbox-local loop.
#
# Compiles test classes via Gradle (in-process), then executes in this JVM.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :core:testClasses --console=plain -q

CACHE=~/.gradle/caches/modules-2
cp_entry() { find $CACHE -name "$1" | head -1; }

CONSOLE=${JUNIT_CONSOLE_JAR:-/tmp/junit-platform-console-standalone.jar}
if [ ! -f "$CONSOLE" ]; then
  V=$(curl -s 'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/maven-metadata.xml' \
    | grep -oE '<release>[^<]+' | cut -d'>' -f2)
  curl -s -o "$CONSOLE" \
    "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$V/junit-platform-console-standalone-$V.jar"
fi

exec java -jar "$CONSOLE" execute \
  --class-path "core/build/classes/kotlin/main:core/build/classes/kotlin/test:$(cp_entry 'kotlin-test-junit5-*.jar'):$(cp_entry 'kotlin-test-2*.jar'):$(cp_entry 'kotlin-stdlib-2*.jar'):$(cp_entry 'gson-2*.jar')" \
  --scan-class-path "$@"
