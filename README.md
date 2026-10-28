# CobbleTracker 1.2.1

![CobbleTracker — rare spawns, clear signal](https://raw.githubusercontent.com/Tausvokse/cobbletracker/main/docs/assets/cobbletracker-header.png)

<p align="center"><strong>Find the rare spawn. Follow its beam. Make the next encounter your own.</strong></p>

<p align="center">Minecraft <strong>1.21.1</strong> · <strong>Fabric / NeoForge</strong> · Java <strong>21</strong> · Cobblemon <strong>1.7–1.8.2</strong></p>

CobbleTracker brings rare encounters into view with coloured beams, automatic minimap points, spawn history and personal hunts. Server owners can define their own categories, notifications and scheduled Pokémon spawns — including native biome, weather, time and structure conditions.

**1.2.1 hotfix:** Legacy legendary placement now supports the actual surface height of snow layers, slabs, carpet, farmland and dirt paths. It measures the vertical limit from the player’s exact feet Y and retries other suitable positions if the selected Pokémon cannot fit, without changing that species or bypassing its conditions.

[Read the wiki](https://taushop.me/cobbletracker/) · [Report an issue](https://github.com/Tausvokse/cobbletracker/issues)

![Find the rare ones](https://raw.githubusercontent.com/Tausvokse/cobbletracker/main/docs/assets/cobbletracker-find.png)

## A signal you can follow

- **Coloured spawn beams** mark tracked wild Pokémon in your loaded chunks. Configure their range, height and out-of-range grace period.
- **Automatic temporary minimap points** appear in the beam colour, follow the Pokémon and clear when tracking ends. No chat click or permanent waypoint import is needed.
- **Xaero’s Minimap, JourneyMap and VoxelMap integrations** use compatible, separately installed client minimap mods. CobbleTracker itself does not draw a minimap. VoxelMap’s available loader support differs; see the wiki.
- **Spawn history** keeps encounter details, locations and capture outcomes. Browse it with `/ct`; use `/lastlegend` for legendary history, with a chat fallback when the client GUI is unavailable.

![You control the spawns](https://raw.githubusercontent.com/Tausvokse/cobbletracker/main/docs/assets/cobbletracker-spawns.png)

## Your Pokémon. Your spawn rules.

Add species files in `config/cobbletracker/spawns/` and use native Cobblemon spawn entries:

- **`replace`** removes that species’ upstream natural Cobblemon/datapack spawns, including matching herd members, while the custom species and group are enabled.
- **`append`** keeps those natural entries and adds your scheduled rules.
- Define **biomes, dimensions, day/night, rain, thunder, structures, height and other supported native conditions**, including anticonditions and version-compatible presets.
- Give a group **one shared server roll and timer**. The legendary group defaults to **35% per 72000 server ticks** — approximately one hour at 20 TPS. The bundled Mew example starts disabled.
- Weight eligible **player/rule pairs**. Mew with weight 1 near one player and Latias with weight 2 near another give conditional shares of **33.33% / 66.67%**. More eligible blocks do not multiply a rule’s weight.

`/checkspawn legendary` shows the aggregate species shares, highest first, separately from the shared roll chance and countdown. These shares total 100% after a successful roll; current conditions must still permit placement.

Commands, breeding and direct scripted spawns remain available with `replace`. Disabling/removing a replacement or switching to `append` restores the upstream entries on a successful reload.

**Keeping an existing setup?** The legacy category scheduler and `legendaries.yml` remain available. `mode: auto` checks actual natural tier coverage; removing that line still means AUTO. Use `always` when you deliberately want the legacy scheduler to run alongside the natural pool. `/checklegendary` identifies the active spawning source.

**Legacy spawn height limit.** Set `max-vertical-distance: 16` inside a category's `spawn:` block to keep placement within 16 blocks above or below the player’s exact feet Y, including fractional surfaces. This is also the default for older configs. The legacy spawner searches nearby cave or surface positions and skips the attempt if none fit; it does not fall back to a distant surface height. Species JSON and upstream natural spawns use Cobblemon's own placement settings.

![Play your way](https://raw.githubusercontent.com/Tausvokse/cobbletracker/main/docs/assets/cobbletracker-players.png)

## Useful information, on your terms

**Independent notification controls.** Mute chat, titles, actionbar or sound without disabling your beams and minimap points. The category master switch still turns off that category for you.

```text
/ct notify mythicals chat off
/ct notify mythicals title off
/ct notify mythicals actionbar off
/ct notify mythicals sound off
```

**Chat coordinates are off by default.** Server owners can enable `general-settings.show-spawn-location-in-chat`. Nearby live entity indicators still show their targets; coordinate rounding is not a way to conceal a loaded Pokémon.

**Personal hunt mode.** Press `\` by default, or check Minecraft’s Controls, to choose species to highlight among nearby loaded wild Pokémon. Your hunt list persists between sessions. This highlights encounters; it does not create them. Server policy can disable hunting.

**Categories that fit your server.** Legendary, Mythic, Ancient, Ultra-Beast, Shiny, Boss, Starter and regional-form categories are configurable. Add species, label, form, level, IV, biome and other matcher conditions. Categories classify existing Pokémon; they do not add new species to Cobblemon.

**A little more for your community.** Optional modules include boss health bars, temporary catch protection, server-first Hall of Fame records, capture statistics, spawn/catch command hooks and asynchronous Discord webhook notifications.

**Placeholders for your displays.** Feed cached roll chances, countdowns, conditional species shares and recent species into compatible chat, TAB or sidebar mods. Optional integrations cover Fabric Text Placeholder API, NeoForge Forge PlaceholderAPI and direct TAB callbacks. Your display mod must support the selected integration.

<details>
<summary><strong>Commands at a glance</strong></summary>

| Command | Purpose |
| --- | --- |
| `/ct` | Open spawn history; client mod required for the GUI |
| `/lastlegend` | Recent legendary history / chat fallback |
| `/checkspawn <group>` | Aggregate conditional species shares and group timer |
| `/checklegendary` | Inspect the active legendary spawning source |
| `/spawnlegendary` | Operator attempt using the active JSON group or legacy director |
| `/ct notify` | Personal notification channels and radius |
| `/ct hof` | Server-first capture records |
| `/ct theme <name>` | Choose a GUI theme |
| `/ct reload` | Operator configuration reload |
| `/ct admin` | Operator temporary live settings |

LuckPerms nodes are used when available; otherwise vanilla permission levels apply. The admin panel changes live values, while `/ct reload` reapplies the configuration on disk.

</details>

![Install and explore](https://raw.githubusercontent.com/Tausvokse/cobbletracker/main/docs/assets/cobbletracker-install.png)

## Installation

1. Use **Minecraft 1.21.1** and **Java 21**. Choose the JAR for **Fabric** or **NeoForge**.
2. Install **Cobblemon** and its required loader/Kotlin dependencies. Fabric also requires **Fabric API**. CobbleTracker has no Architectury API runtime requirement.
3. Install CobbleTracker on the **server** for tracking, commands and server modules. Add it on the **client** for beams, the history GUI, automatic map points and hunt mode. Singleplayer uses both parts in the same installation.
4. Add a compatible client minimap if you want map points. Configure `config/cobbletracker/` and run `/ct reload` after edits. Rejected reloads retain the working configuration.

Compatibility checks cover Cobblemon **1.7.0, 1.7.3, 1.8.0, 1.8.1 and 1.8.2** on both loaders, with gameplay/provider checks scoped separately. Match every dependency to Minecraft 1.21.1 and its loader; not every combination of minimap, renderer, shader or addon is interchangeable.

**Start here:** [Installation & quick start](https://taushop.me/cobbletracker/#quickstart) · [Custom species spawns](https://taushop.me/cobbletracker/#scheduled-spawns) · [Placeholders](https://taushop.me/cobbletracker/#placeholders) · [Troubleshooting](https://taushop.me/cobbletracker/#troubleshooting)

If you would like to support development: [PayPal](https://www.paypal.com/ncp/payment/XWSP342YCQ52G).

## Updating

Stop the server/game and back up the world and `config/cobbletracker/`. Replace the old CobbleTracker JAR with the matching 1.2.1 loader build, keeping one version in `mods/`. Update clients using CobbleTracker too. Keep existing YAML, history and player data. Restart, then check `/checklegendary` and `/ct`. Use `/ct reload` after configuration edits.

## Build from source

Use JDK 21. Run `./gradlew build` on Linux/macOS or `gradlew.bat build` on Windows. Final loader JARs are written to `fabric/build/libs/` and `neoforge/build/libs/`. The `common` module contains the Java domain logic; `mod` contains shared game integration.
