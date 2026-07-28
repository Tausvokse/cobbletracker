#!/bin/bash
# Build the mod, install it into the production Fabric client, regenerate the launch args, and start
# the client (auto-connects to localhost:25599). See README.md for why native :fabric:runClient can't
# boot Cobblemon in Loom's dev runtime.
set -e
ROOT="D:/cobblemons_mods/CobbleTracker"
CLIENT="D:/tournament-engine/testclients/client1"
JDK="C:/Users/parho/.jdks/ms-21.0.7/bin/java.exe"

VERSION=$(grep -E '^mod_version=' "$ROOT/gradle.properties" | cut -d= -f2)

echo ">> Building CobbleTracker $VERSION (fabric)..."
(cd "$ROOT" && ./gradlew :fabric:remapJar -q --console=plain)
cp "$ROOT/fabric/build/libs/cobbletracker-fabric-$VERSION.jar" "$CLIENT/mods/cobbletracker.jar"
echo ">> Regenerating client launch args..."
bash "$ROOT/dev-run/build_client_args.sh" >/dev/null
echo ">> Launching client (connecting to localhost:25599)..."
cd "$CLIENT" && exec "$JDK" "@$ROOT/dev-run/client.args"
