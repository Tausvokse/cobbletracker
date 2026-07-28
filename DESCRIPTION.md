# CobbleTracker

**A free Cobblemon companion mod that tells you *where* the good spawns are — and shows you, in the world itself.**

Rare spawns in Cobblemon are easy to miss. CobbleTracker watches every spawn on the server, announces the ones that matter, raises a beacon-style light column over them, drops a waypoint on your minimap, and keeps a browsable history of everything that has appeared — so a Legendary that spawned while you were in a cave is still findable.

Minecraft **1.21.1** · **Fabric** and **NeoForge** · Cobblemon **1.7.0** · Java 21
**Completely free.**

---

## In-world spawn beam

A beacon-style column of light stands over a tracked Pokémon for as long as it is in your loaded chunks — the in-world "it's right here" marker instead of a wall of coordinates in chat.

- **Presence-based.** It appears whenever you're near, and comes back if you fly off and return.
- **Removed the instant it's over** — caught, defeated, or despawned. No ghost beams.
- **Tier-coloured** so you can tell a Legendary from a Shiny at a glance.
- Configurable radius (`0` = auto, matches your render distance), grace period, and column height.

## Minimap integration — Xaero's, VoxelMap, JourneyMap

Every announcement can carry a clickable **Create Waypoint** link. Click it and the spawn lands on your minimap.

- Your client tells the server which minimap mods you actually have, and the server only ever emits those formats. **No minimap mod is required** — clients without one just get the beam.
- Waypoints can inherit the beam's tier colour.
- Any format can be switched off server-wide.
- Clicking a card in the history GUI drops **a past spawn** on your minimap too.

## Spawn announcements

Per-category announcements, fully rewritable.

- **MiniMessage formatting** — `<gold>`, `<bold>`, `<#RRGGBB>`, `<gradient:#a:#b>`. Legacy `&`-codes still work.
- Placeholders: `%species%`, `%biome%`, `%world%`, `%x%`, `%y%`, `%z%`, and `%waypoint%` for the clickable link.
- Optional on-screen **title/subtitle**, and a configurable **sound** with volume and pitch.
- Broadcast to the whole server, or only to players within N blocks — per category.
- `hide-exact-position` rounds the beam and waypoint to the chunk centre, so announcements never hand out pinpoint coordinates.

## Browsable spawn history

`/cobbletracker` (or `/ct`, `/last`, `/ll`) opens a code-drawn GUI listing everything that has spawned.

- A sidebar tab for **ALL**, one per tracker category, and **STATS**.
- Scrollable **sprite cards** showing species, biome, coordinates and time.
- Cycle the sort: **Newest · Distance · Species · Tier · Caught**.
- Caught spawns are marked with who caught them.
- History **survives restarts**, with a per-category cap that evicts the oldest first.
- `/lastlegend` prints a plain-chat rundown of recent Legendary spawns — **works without the client mod installed.**

## Top Hunters leaderboard

The STATS tab ranks players by captures, drawn as a bar chart. Bragging rights, built in.

## Personal hunt mode — press `\`

Your own tracker, entirely client-side, no server support needed.

- Press `\` to open **Track a Pokémon** and search the full Cobblemon species list.
- Click any species to toggle hunting it — hunt as many as you like.
- Matching Pokémon near you get an **amber beam**, plus **directional arrows in the corner of your HUD** with live distances to the nearest ones.
- A **Beam: ON/OFF** toggle and a **Clear** button, right in the screen.

## Tracker categories you define

CobbleTracker ships with **Legends, Shinies, Bosses and Ultra Beasts** — but they're just config. Add or remove a block and every subsystem (announcements, history, stats, the spawner, the GUI sidebar) picks it up on the next `/ct reload`.

Each category has a **matcher spec**, combined with spaces for AND:

```
isLegendary:true      isShiny:true       isMythical:true
isUltraBeast:true     isBoss:true        species:<id>
label:<x>             aspect:<x>         level:>50
```

Plus a tier colour, a history cap, a species blacklist, and an optional dimension whitelist.

## Optional built-in spawner

Want to *guarantee* rare spawns instead of waiting on luck? Each category can run its own scheduler (off by default):

- Attempt **interval** and per-attempt **chance**.
- **Distribute among players** — one server-wide roll per interval picks a random eligible player as the anchor, so the spawn rate doesn't balloon as your player count grows.
- A **per-player cooldown** so the same person doesn't get every spawn.
- Min/max **distance** from the anchor, a **level** range or fixed level, a forced **shiny** flag, and a custom **species pool**.
- Respects the category's dimension whitelist — a Nether-only category never places anything in the Overworld.

## Six built-in themes

Every screen is drawn in code, not from a texture atlas — so themes are instant and never clash with your resource pack. Switch with `/ct theme <name>`:

**pokedex** (default) · dark · light · blue · midnight · forest

## In-game admin panel

`/ct admin` opens a live settings panel for operators — toggle the beam, tune its radius, grace period and height, switch minimap formats on or off, flip title display and position-hiding. No file editing, no restart.

## Built to be configured

- **YAML** for everything you edit by hand — `config.yml` and `announcements.yml`, heavily commented.
- `/ct reload` hot-reloads both.
- Machine data lives separately in `tracker.json`.
- English and Russian localisation included.

---

## Commands

| Command | What it does |
|---|---|
| `/cobbletracker` · `/ct` · `/last` · `/ll` | Open the spawn history GUI |
| `/lastlegend` | Chat rundown of recent Legendary spawns (no client mod needed) |
| `/ct theme <name>` | Switch GUI theme |
| `/ct waypoint <id>` | Drop a stored spawn on your minimap |
| `/ct reload` | Reload the YAML config *(op)* |
| `/ct admin` | Live settings panel *(op)* |
| `/ct fakehit` | Fire a test spawn *(op)* |

## Installing

Drop the jar in `mods/` alongside **Cobblemon**, **Fabric API** / NeoForge, **Fabric Language Kotlin** and **Architectury API**.

Install it on the **server** for announcements, history and `/lastlegend`. Add it on the **client** as well to unlock the beam, the history GUI, minimap waypoints and personal hunt mode.
