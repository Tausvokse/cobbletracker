#!/bin/bash
set -e
RC="D:/cobblemons_mods/CobbleTracker/fabric/.gradle/loom-cache/remapClasspath.txt"
FL="D:/tournament-engine/testclients/fabriclibs"
MCJAR="C:/Users/parho/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar"
CLIENT="D:/tournament-engine/testclients/client1"
ASSETS="C:/Users/parho/.gradle/caches/fabric-loom/assets"
SCRATCH="D:/cobblemons_mods/CobbleTracker/dev-run"
OUT="$SCRATCH/client.args"

# 1) MC libraries from loom's resolved remap classpath — exclude the loom MC jar, all fabric
#    runtime pieces (added from fabriclibs), and the mod jars (loaded from gameDir/mods).
libs=$(tr ';' '\n' < "$RC" | grep -iE '\.jar$' \
  | grep -viE 'minecraftMaven|minecraft-merged|minecraft-clientonly|minecraft-common' \
  | grep -viE 'net\.fabricmc|net[/\\]fabricmc|dev\.architectury|dev[/\\]architectury|com\.cobblemon|com[/\\]cobblemon|loom-cache')

# 2) LWJGL Windows natives (x64)
natives=$(find "C:/Users/parho/.gradle/caches/modules-2/files-2.1/org.lwjgl" -iname "*natives-windows.jar" 2>/dev/null | grep -v arm)

# 3) Fabric runtime pieces (loader + intermediary mappings + mixin + ASM)
fabricrt="$FL/fabric-loader-0.17.3.jar
$FL/intermediary-1.21.1.jar
$FL/sponge-mixin-0.16.5+mixin.0.8.7.jar
$FL/asm-9.9.jar
$FL/asm-analysis-9.9.jar
$FL/asm-commons-9.9.jar
$FL/asm-tree-9.9.jar
$FL/asm-util-9.9.jar"

# Build classpath: forward slashes only (java @argfile treats backslash as escape), ';' separator.
cp=$( { printf '%s\n' "$libs"; printf '%s\n' "$natives"; printf '%s\n' "$fabricrt"; printf '%s\n' "$MCJAR"; } \
  | grep -E '\.jar$' | sed 's#\\#/#g' | paste -sd ';' )

mcjar_fs=$(printf '%s' "$MCJAR" | sed 's#\\#/#g')
client_fs=$(printf '%s' "$CLIENT" | sed 's#\\#/#g')
assets_fs=$(printf '%s' "$ASSETS" | sed 's#\\#/#g')

cat > "$OUT" <<EOF
-Xmx3G
-Dfabric.gameJarPath=$mcjar_fs
-Dlog4j2.formatMsgNoLookups=true
-cp
$cp
net.fabricmc.loader.impl.launch.knot.KnotClient
--gameDir
$client_fs
--assetsDir
$assets_fs
--assetIndex
1.21.1-17
--accessToken
0
--version
1.21.1
--username
Tester
--userType
legacy
--width
1100
--height
720
--quickPlayMultiplayer
localhost:25599
EOF

echo "=== argfile written: $OUT ==="
echo "classpath entries: $(printf '%s' "$cp" | tr ';' '\n' | grep -c .)"
echo "--- key jars present? ---"
printf '%s' "$cp" | tr ';' '\n' | grep -iE "lwjgl-glfw-3.3.3.jar|lwjgl-3.3.3-natives-windows.jar|gson|guava|log4j-core|netty-common|fabric-loader|intermediary-1.21.1|sponge-mixin|minecraft-client.jar|fastutil|joml|authlib" | sed 's#.*/##' | sort -u
echo "--- any leftover mod/loom jars (should be empty) ---"
printf '%s' "$cp" | tr ';' '\n' | grep -iE "cobblemon|architectury|fabric-api|minecraft-merged|fabric-language-kotlin" | head
