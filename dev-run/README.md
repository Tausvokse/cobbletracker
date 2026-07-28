# Running CobbleTracker locally

## Why not `:fabric:runClient` / `:neoforge:runServer` etc.?

Cobblemon **cannot boot in Loom's dev runtime** on this toolchain — this is an upstream
Cobblemon + Architectury-Loom limitation, **not** a CobbleTracker bug:

- **Fabric dev** (`:fabric:runClient` / `:fabric:runServer`) crashes at Cobblemon init with
  `ClassNotFoundException: net.minecraft.class_2960`. Cobblemon's `SpeciesAdditions` uses
  `kotlin-reflect` on every `Species` property; kotlin-reflect reads the class's Kotlin `@Metadata`,
  which Loom remaps the *bytecode* of but **not** the metadata strings — so it still resolves the
  intermediary name `class_2960`, which doesn't exist in a named dev run. Verified that the standard
  fix (applying `org.jetbrains.kotlin.jvm` to trigger Loom's metadata remapper) does **not** remap the
  Cobblemon jar's metadata in `architectury-loom 1.10.455`, and neither does forcing the intermediary
  stubs onto the classpath.
- **NeoForge dev** crashes with `IncompatibleClassChangeError: PokemonEntity overrides final method
  LivingEntity.getDimensions(Pose)` — a binary mismatch between the Cobblemon-neoforge jar and the
  Loom-provided Minecraft patch.

So CobbleTracker is tested against a **production** (intermediary) install instead, where Cobblemon
boots normally. Everything — the beam, the GUI, the spawn engine, announcements and the commands — has
been verified that way.

## The working way to run it (production / intermediary runtime)

These scripts install the freshly-built jar into a real Fabric+Cobblemon install and launch it. They
reuse the test install on this machine (`D:\tournament-engine\testserver` and `testclients\client1`),
which already has Minecraft, Fabric, Cobblemon, fabric-api, fabric-language-kotlin and architectury.

```bash
# Server → localhost:25599 (RCON on :25575, password cbt123)
bash dev-run/run-server.sh

# Client → auto-connects to localhost:25599 as "Tester"
bash dev-run/run-client.sh
```

`run-client.sh` regenerates `dev-run/client.args` (a production Fabric launch: obfuscated MC client jar
from Loom's cache + intermediary mappings + cached assets/natives/libraries + the production mods).

## Driving the game headlessly (how the automated verification was done)

With RCON enabled you can trigger any player-facing flow from the console, e.g.:

```bash
python dev-run/rcon.py localhost 25575 cbt123 "execute as Tester run cobbletracker"           # open GUI
python dev-run/rcon.py localhost 25575 cbt123 "execute as Tester run cobbletracker fakehit"   # test spawn
python dev-run/rcon.py localhost 25575 cbt123 "execute as Tester run lastlegend"              # chat report
python dev-run/rcon.py localhost 25575 cbt123 "execute at Tester run pokespawn rayquaza"      # announce + beam
```

To exercise the spawn engine + announcements, set `trackers.legendaries.spawn.enabled: true` (and a
low `interval-ticks`) in `config/cobbletracker/config.yml`, then `execute as Tester run cobbletracker reload`.
