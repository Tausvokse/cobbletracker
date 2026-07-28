Technical Specification: CobbleTracker (Complete Version)Document Version: 1.0 FinalTarget Engine: Minecraft 1.21.1Dependencies: Cobblemon, NeoForge, Fabric (Multi-loader architecture), Architectury API (Optional/Recommended for networking)Role: Business Analysis & Technical Requirements Document1. Product OverviewCobbleTracker is a comprehensive client-server utility mod for the Cobblemon ecosystem. It merges real-time entity spatial tracking with a persistent, historical spawn logging system. The mod transitions away from traditional Minecraft inventory-based UIs, utilizing a modern, custom drawable Graphical User Interface (GUI) and a robust event-driven notification system.The primary goal is to provide players with a high-fidelity tracking experience for rare Pokémon (Legendaries, Shinies, Bosses) and specific blocks (Loot Chests), while providing server administrators absolute control over data visibility, performance impact, and economy balance.2. Core Functional Requirements2.1 Real-Time Radar HUD (Client & Server)Entity Detection: The server must scan for predefined entities and blocks within a configurable radius of the player.Directional HUD: The client HUD must display a visual arrow pointing toward the target, dynamically updating based on the player's camera yaw/pitch (interpolated for smooth rotation).Data Display: The HUD must show the entity's species name, distance in blocks, and a color-coded background or text matching the entity tier.Client Toggles: Players must be able to toggle the HUD visibility and filter specific search targets via a dedicated hotkey menu (Default: \).Server Authority: To prevent x-ray exploits, the server must only transmit packet updates for entities that fall within the server-defined maximum search radius.2.2 Global Spawn Announcement SystemTrigger Event: Intercepts CobblemonEvents.POKEMON_ENTITY_SPAWN to broadcast high-value spawns.Multi-Channel Broadcasting: Supports on-screen action-bar toasts, large central screen titles, global chat messages with interactive (clickable) coordinates, and localized audio cues.Radius Filtering: Announcements can be configured to broadcast globally to the entire server or locally to players within a specific block radius.2.3 Custom Drawable GUI (Spawn History)Architecture: Replaces chest-based interfaces with a custom client-side rendering engine utilizing net.minecraft.client.gui.DrawContext.Visual Components: * Sidebar navigation with tabs (All, Legends, Shinies, Bosses).Scrollable viewport containing "Spawn Cards."Rendered 2D/3D sprites of the Pokémon using PokemonEntity.getPokemon().getForm().getSprite() instead of static item icons.Data Synchronization: Opens via command (/cobbletracker). The client requests data (C2SOpenTrackerPacket), and the server responds with a compressed NBT payload (S2SSyncHistoryPacket) read directly from tracker.json.3. Commands and PermissionsCommandAliasesDescriptionPermission NodeDefault Access/cobbletracker/ct, /lastOpens the Spawn History GUI.cobbletracker.command.guiAll Players/cobbletracker reload/ct reloadReloads all configuration files from disk.cobbletracker.command.reloadAdmin (OP)/cobbletracker admin/ct adminOpens the server-side radar configuration UI.cobbletracker.command.adminAdmin (OP)/cobbletracker fakehit/ct fakehitGenerates a fake HUD radar ping for testing.cobbletracker.command.fakehitAdmin (OP)4. System Configurations & Data StructuresAll configuration files must be generated in the config/cobbletracker/ directory upon first launch.4.1 config.ymlGoverns global server rules and tracker category definitions.YAMLgeneral-settings:
  server-radar-enabled: true
  max-search-radius: 150
  update-rate-ticks: 20
  allow-exact-position: false
  allow-exact-name: true

radar-colors:
  legendary: "#FF3333"
  shiny: "#55FF55"
  boss: "#3333FF"
  loot-chest: "#FFFF55"
  user-search: "#FFAA00"

trackers:
  legendaries:
    name: "legends"
    spec: "isLegendary:true"
    max-stored: 10
    blacklist: "mewtwo, rayquaza"
  shinies:
    name: "shinies"
    spec: "isShiny:true"
    max-stored: 15
    blacklist: "magikarp, caterpie"
  bosses:
    name: "bosses"
    spec: "isBoss:true"
    max-stored: 5
    blacklist: ""
  ultrabeasts:
    name: "ultrabeasts"
    spec: "isUltraBeast:true"
    max-stored: 5
    blacklist: ""
4.2 announcements.ymlGoverns the event-driven notification system.YAMLnotifications:
  legendary:
    enabled: true
    title: "&6&lLEGENDARY SPAWN"
    subtitle: "&b%species% &fhas appeared in the &a%biome%!"
    chat: "&8[&6CobbleTracker&8] &fA wild &b%species% &fwas spotted at &7%x%, %y%, %z%."
    sound: "cobbletracker:notification.legendary_spawn"
    play-to-all: true
    broadcast-radius: 0
  shiny:
    enabled: true
    title: ""
    subtitle: "&eA Shiny %species% has spawned nearby!"
    chat: "&8[&6CobbleTracker&8] &e✨ A Shiny %species% spawned in your area!"
    sound: "cobbletracker:notification.shiny_spawn"
    play-to-all: false
    broadcast-radius: 200
  boss:
    enabled: true
    title: "&4&lBOSS DETECTED"
    subtitle: "&cPrepare for battle against %species%!"
    chat: "&8[&6CobbleTracker&8] &cA Boss &4%species% &cwas found at &7%x%, %y%, %z%."
    sound: "minecraft:entity.ender_dragon.growl"
    play-to-all: true
    broadcast-radius: 0
4.3 ui_layout.jsonControls the coordinates, dimensions, and styling of the custom drawable GUI elements.JSON{
  "window": {
    "width": 400,
    "height": 250,
    "texture": "cobbletracker:textures/gui/main_bg.png",
    "close_button_x": 380,
    "close_button_y": 5
  },
  "colors": {
    "background": "#121212",
    "sidebar_bg": "#1E1E1E",
    "accent_primary": "#6C5CE7",
    "accent_secondary": "#A29BFE",
    "text_title": "#FFFFFF",
    "text_body": "#CCCCCC",
    "text_highlight": "#FDCB6E"
  },
  "sidebar": {
    "width": 100,
    "tab_height": 30,
    "tabs": [
      "ALL",
      "LEGENDS",
      "SHINIES",
      "BOSSES"
    ]
  },
  "spawn_card": {
    "width": 280,
    "height": 50,
    "spacing": 8,
    "sprite_offset_x": 10,
    "sprite_offset_y": 5,
    "text_offset_x": 60,
    "text_offset_y": 10,
    "show_model_sprite": true
  }
}
4.4 tracker.jsonActs as the persistent local database for recorded spawns. It dynamically updates as new entities spawn or are captured.JSON{
  "legends": {
    "c5b2a9e1-3d7f-4b1a-8c9e-2f5a6b7c8d9e": {
      "species": "Rayquaza",
      "spawn-time": 1716382910,
      "world": "minecraft:overworld",
      "x": 1450,
      "y": 120,
      "z": -400,
      "biome": "minecraft:extreme_hills",
      "caught": false,
      "catcher-uuid": null,
      "catcher-name": null
    },
    "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d": {
      "species": "Lugia",
      "spawn-time": 1716379000,
      "world": "minecraft:overworld",
      "x": -2000,
      "y": 64,
      "z": 1500,
      "biome": "minecraft:deep_ocean",
      "caught": true,
      "catcher-uuid": "f8a9b0c1-d2e3-4f5a-6b7c-8d9e0f1a2b3c",
      "catcher-name": "PokeMaster99"
    }
  },
  "shinies": {
    "e9d8c7b6-a5f4-3e2d-1c0b-9a8b7c6d5e4f": {
      "species": "Pikachu",
      "spawn-time": 1716382500,
      "world": "minecraft:overworld",
      "x": 100,
      "y": 70,
      "z": 250,
      "biome": "minecraft:forest",
      "caught": true,
      "catcher-uuid": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
      "catcher-name": "AshK"
    },
    "d4a3b2c1-e5f6-4a3b-2c1d-0e9f8a7b6c5d": {
      "species": "Charizard",
      "spawn-time": 1716380100,
      "world": "minecraft:the_nether",
      "x": 45,
      "y": 80,
      "z": 112,
      "biome": "minecraft:basalt_deltas",
      "caught": false,
      "catcher-uuid": null,
      "catcher-name": null
    }
  },
  "bosses": {
    "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d": {
      "species": "Snorlax",
      "spawn-time": 1716375000,
      "world": "minecraft:overworld",
      "x": 500,
      "y": 64,
      "z": 500,
      "biome": "minecraft:plains",
      "caught": false,
      "catcher-uuid": null,
      "catcher-name": null
    }
  }
}
5. Network Protocol & PerformanceSpatial Hashing: Server-side radar sweeps must use Minecraft's native chunk caching or an asynchronous spatial hash map to prevent main-thread TPS degradation. Sweeps occur exactly once every update-rate-ticks.Coordinate Obfuscation: If allow-exact-position is false, the server must round the broadcasted coordinates to the center of the nearest chunk ($x \pm 8$, $z \pm 8$) prior to packet serialization.State Management: The GUI system must employ a client-side cache for S2SSyncHistoryPacket. Scrolling and tab-switching must manipulate the cached array rather than requesting new packets from the server.