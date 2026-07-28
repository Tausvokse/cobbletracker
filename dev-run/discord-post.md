Ready-to-paste Discord version of DESCRIPTION.md, split into messages under Discord's
2000-character limit. Post them in order in the new channel. Screenshots to attach are
listed at the bottom.

=============================== MESSAGE 1 ===============================
# CobbleTracker
**A free Cobblemon companion mod that tells you *where* the good spawns are — and shows you, in the world itself.**

Rare spawns in Cobblemon are easy to miss. CobbleTracker watches every spawn on the server, announces the ones that matter, raises a beacon-style light column over them, drops a waypoint on your minimap, and keeps a browsable history of everything that has appeared — so a Legendary that spawned while you were in a cave is still findable.

Minecraft **1.21.1** · **Fabric** and **NeoForge** · Cobblemon **1.7.0** · Java 21
**Completely free.**

=============================== MESSAGE 2 ===============================
## 🔦 In-world spawn beam
A beacon-style column of light stands over a tracked Pokémon for as long as it is in your loaded chunks — the in-world "it's right here" marker instead of a wall of coordinates in chat.
• **Presence-based** — appears whenever you're near, comes back if you fly off and return
• **Removed the instant it's over** — caught, defeated or despawned. No ghost beams
• **Tier-coloured**, so a Legendary reads differently from a Shiny at a glance
• Configurable radius (`0` = auto, matches your render distance), grace period and height

## 🗺️ Minimap integration — Xaero's, VoxelMap, JourneyMap
Every announcement can carry a clickable **Create Waypoint** link. Click it and the spawn lands on your minimap.
• Your client tells the server which minimap mods you have, and the server only emits those formats. **No minimap mod required** — clients without one still get the beam
• Waypoints can inherit the beam's tier colour
• Any format can be switched off server-wide
• Clicking a card in the history GUI drops **a past spawn** on your minimap too

=============================== MESSAGE 3 ===============================
## 📢 Spawn announcements
Per-category and fully rewritable.
• **MiniMessage formatting** — `<gold>`, `<bold>`, `<#RRGGBB>`, `<gradient:#a:#b>`. Legacy `&`-codes still work
• Placeholders: `%species%`, `%biome%`, `%world%`, `%x%`, `%y%`, `%z%`, `%waypoint%`
• Optional on-screen **title/subtitle** and a configurable **sound** with volume and pitch
• Broadcast server-wide, or only within N blocks — per category
• `hide-exact-position` rounds the beam and waypoint to the chunk centre, so announcements never hand out pinpoint coordinates

## 📖 Browsable spawn history
`/cobbletracker` (or `/ct`, `/last`, `/ll`) opens a code-drawn GUI listing everything that has spawned.
• Sidebar tabs for **ALL**, every tracker category, and **STATS**
• Scrollable **sprite cards** with species, biome, coordinates and time
• Cycle the sort: **Newest · Distance · Species · Tier · Caught**
• Caught spawns are marked with who caught them
• History **survives restarts**, with a per-category cap that evicts the oldest first
• `/lastlegend` prints a chat rundown of recent Legendaries — **works without the client mod**

## 🏆 Top Hunters leaderboard
The STATS tab ranks players by captures as a bar chart. Bragging rights, built in.

=============================== MESSAGE 4 ===============================
## 🎯 Personal hunt mode — press `\`
Your own tracker, entirely client-side, no server support needed.
• Press `\` to open **Track a Pokémon** and search the full Cobblemon species list
• Click any species to toggle hunting it — hunt as many as you like
• Matches near you get an **amber beam**, plus **directional arrows on your HUD** with live distances
• **Beam: ON/OFF** toggle and a **Clear** button right in the screen

## 🧩 Tracker categories you define
Ships with **Legends, Shinies, Bosses and Ultra Beasts** — but they're just config. Add or remove a block and every subsystem (announcements, history, stats, spawner, GUI sidebar) picks it up on the next `/ct reload`.

Each category has a matcher spec, combined with spaces for AND:
```
isLegendary:true    isShiny:true      isMythical:true
isUltraBeast:true   isBoss:true       species:<id>
label:<x>           aspect:<x>        level:>50
```
Plus a tier colour, a history cap, a species blacklist and an optional dimension whitelist.

=============================== MESSAGE 5 ===============================
## ⚙️ Optional built-in spawner
Want to *guarantee* rare spawns instead of waiting on luck? Each category can run its own scheduler (off by default):
• Attempt **interval** and per-attempt **chance**
• **Distribute among players** — one server-wide roll per interval picks a random eligible anchor, so spawn rates don't balloon as your player count grows
• A **per-player cooldown** so the same person doesn't get every spawn
• Min/max **distance** from the anchor, a **level** range or fixed level, a forced **shiny** flag, a custom **species pool**
• Respects the category's dimension whitelist

## 🎨 Six built-in themes
Every screen is drawn in code, not from a texture atlas — themes are instant and never clash with your resource pack. Switch with `/ct theme <name>`:
**pokedex** (default) · dark · light · blue · midnight · forest

## 🛠️ In-game admin panel
`/ct admin` opens a live settings panel for operators — toggle the beam, tune radius, grace period and height, switch minimap formats on or off, flip title display and position-hiding. No file editing, no restart.

## 📝 Built to be configured
• **YAML** for everything you edit by hand — `config.yml` and `announcements.yml`, heavily commented
• `/ct reload` hot-reloads both
• Machine data lives separately in `tracker.json`
• English and Russian localisation included

=============================== MESSAGE 6 ===============================
## Commands
```
/cobbletracker · /ct · /last · /ll   Open the spawn history GUI
/lastlegend                          Chat rundown of recent Legendaries (no client mod needed)
/ct theme <name>                     Switch GUI theme
/ct waypoint <id>                    Drop a stored spawn on your minimap
/ct reload                           Reload the YAML config          (op)
/ct admin                            Live settings panel             (op)
/ct fakehit                          Fire a test spawn               (op)
```

## Installing
Drop the jar in `mods/` alongside **Cobblemon**, **Fabric API** / NeoForge, **Fabric Language Kotlin** and **Architectury API**.

Install it on the **server** for announcements, history and `/lastlegend`. Add it on the **client** too to unlock the beam, the history GUI, minimap waypoints and personal hunt mode.

=============================== SCREENSHOTS ===============================
Attach these to the last message (all from D:\tournament-engine\testclients\client1\screenshots\):

  2026-07-28_10.01.00.png  — red Legendary beam over a wild Rayquaza + announcement
  2026-07-28_10.01.06.png  — "Track a Pokémon" species search screen
  2026-07-28_10.01.18.png  — hunt mode: amber beam + HUD arrows with distances
  2026-07-28_10.01.24.png  — Ultra Beast announcement, purple beam beside the amber hunt beam
  2026-07-28_10.01.33.png  — Shiny Aerodactyl announcement with a green beam
  2026-07-28_10.02.28.png  — hunt search filtered to a single species
  2026-07-28_10.03.07.png  — announcement feed: spawn, despawn, defeat and capture lines
