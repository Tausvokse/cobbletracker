#!/bin/bash
# Build the mod, install it into the production Fabric test server, and launch it.
# (Native :fabric:runServer can't boot Cobblemon in Loom's dev runtime — see README.md.)
set -e
ROOT="D:/cobblemons_mods/CobbleTracker"
SRV="D:/tournament-engine/testserver"
JDK="C:/Users/parho/.jdks/ms-21.0.7/bin/java.exe"

VERSION=$(grep -E '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2)

echo ">> Building CobbleTracker $VERSION (fabric)..."
(cd "$ROOT" && ./gradlew :fabric:remapJar -q --console=plain)
cp "$ROOT/fabric/build/libs/cobbletracker-fabric-$VERSION.jar" "$SRV/mods/cobbletracker.jar"
echo ">> Installed jar. Launching server on :25599 (RCON :25575 / cbt123)..."
cd "$SRV" && exec "$JDK" -Xmx3G -jar fabric-server-launch.jar nogui
