var WIKI = window.WIKI || {};

WIKI.en = {
subtitle: "Wiki · v1.1.0",
searchPlaceholder: "Search…",
noResults: "Nothing matches.",
copy: "Copy",
copied: "Copied",
sections: [

/* ============================ GETTING STARTED ============================ */
{
group: "Getting started", id: "overview", title: "Overview",
lede: "What CobbleTracker does, and the one thing about Cobblemon that explains why half of it exists.",
body: `
<p>CobbleTracker watches every Pok&eacute;mon that enters a server level, decides whether it belongs to
one of your <b>tracker categories</b>, and then announces it, raises a beam over it, offers a minimap
waypoint and files it in a browsable history.</p>

<p>It also spawns legendaries &mdash; because <b>Cobblemon does not</b>.</p>

<div class="note"><b>The fact that shapes everything</b>
<p>Vanilla Cobblemon 1.7 ships <b>zero</b> legendary entries in its world spawn pool. There are 816 spawn
files, numbered by Pok&eacute;dex number, and not one is a legendary. The rarity buckets are only
<code>common</code>, <code>uncommon</code>, <code>rare</code> and <code>ultra-rare</code> &mdash; there is no
"legendary" bucket. Out of the box, legendaries reach a world <b>only</b> through shrines or
<code>/pokespawn</code>.</p></div>

<p>So CobbleTracker ships a <a href="#legendary">legendary director</a>. And because plenty of servers
already solve this with a datapack, the director <b>stands down automatically</b> when it detects one.</p>

<h3>The moving parts</h3>
<table>
<tr><th>Piece</th><th>Where it runs</th><th>What it needs</th></tr>
<tr><td>Announcements, history, legendary director, command hooks, Discord</td><td>Server</td><td>Nothing on the client</td></tr>
<tr><td>Spawn beam, history GUI, minimap waypoints, hunt mode</td><td>Client</td><td>The mod on both sides</td></tr>
<tr><td><code>/checklegendary</code>, <code>/lastlegend</code>, <code>/ct notify</code></td><td>Server</td><td>Plain chat &mdash; works for vanilla clients</td></tr>
</table>

<h3>Requirements</h3>
<ul>
<li>Minecraft <b>1.21.1</b></li>
<li><b>Fabric</b> (+ Fabric API, Fabric Language Kotlin) or <b>NeoForge</b></li>
<li><b>Cobblemon 1.7.x</b> &mdash; built and tested against 1.7.3</li>
<li><b>Architectury API</b></li>
<li>Java 21</li>
</ul>
`
},

{
group: "Getting started", id: "install", title: "Installation",
lede: "Server-side is enough for most of it. The client half unlocks the visuals.",
body: `
<h3>Server</h3>
<p>Drop the jar in <code>mods/</code> alongside Cobblemon, Architectury API and your loader's API.
On first start, CobbleTracker writes three commented config files:</p>
<pre><code>config/cobbletracker/
  config.yml          server rules + tracker categories
  announcements.yml   how each category is announced
  legendaries.yml     rules for the built-in legendary director</code></pre>

<h3>Client</h3>
<p>Same jar, same folder. Without it, a player still gets chat announcements and every chat-based
command; with it they also get the beam, the history GUI, minimap waypoints and hunt mode.</p>

<div class="tip"><b>Mixed servers are fine</b>
<p>The server never assumes a client has the mod. It learns on join, and only sends packets to clients
that announced themselves.</p></div>

<h3>Verifying it loaded</h3>
<pre><code>[Server thread/INFO]: CobbleTracker ready - 4 tracker categories loaded
[Server thread/INFO]: Spawner for 'legendaries' is active (mode AUTO)</code></pre>
<p>The second line is the director telling you it found no legendary spawn data and has taken the job.
If a datapack provides them, it says <code>standing down</code> instead.</p>
`
},

{
group: "Getting started", id: "quickstart", title: "Quick start",
lede: "Five minutes to a working setup.",
body: `
<h3>1. See what you have</h3>
<pre><code>/ct                 open the history GUI
/checklegendary     what can spawn where you stand, and when
/ct admin           live settings panel (op)</code></pre>

<h3>2. Fire a test spawn</h3>
<pre><code>/ct fakehit</code></pre>
<p>Spawns a real shiny Pikachu six blocks away and lets it run the whole chain &mdash; announcement, beam,
minimap link, history entry, and the catch/faint/despawn report. If something is misconfigured, this is
where you see it.</p>

<h3>3. Force a legendary</h3>
<pre><code>/spawnlegendary</code></pre>
<p>Spends one attempt immediately. It may politely refuse &mdash; that is the feature working, not a
failure. Run <code>/checklegendary</code> to see what the spot allows.</p>

<h3>4. Edit and reload</h3>
<pre><code>/ct reload</code></pre>
<p>Re-reads all three YAML files. If one won't parse, the running configuration is <b>kept</b> and you
get a message pointing at the log &mdash; a stray comma never takes your trackers down.</p>

<h3>5. Add your own category</h3>
<p>Two blocks, one in each file, keyed identically. See <a href="#recipes">Recipes</a> for ready-made ones.</p>
`
},

/* ============================== REFERENCE =============================== */
{
group: "Reference", id: "commands", title: "Commands",
lede: "Everything, with which ones work without the client mod.",
body: `
<table>
<tr><th>Command</th><th>Does</th><th>Default</th><th>Vanilla client?</th></tr>
<tr><td><code>/cobbletracker</code> <code>/ct</code> <code>/last</code> <code>/ll</code></td><td>Open the spawn history GUI</td><td>everyone</td><td>no</td></tr>
<tr><td><code>/lastlegend</code></td><td>Recent legendaries &mdash; GUI on the Legends tab, or a chat rundown</td><td>everyone</td><td><b>yes</b> (falls back to chat)</td></tr>
<tr><td><code>/checklegendary</code></td><td>What can spawn here now, with odds and a countdown</td><td>everyone</td><td><b>yes</b></td></tr>
<tr><td><code>/spawnlegendary</code></td><td>Spend one legendary attempt immediately</td><td>op</td><td><b>yes</b></td></tr>
<tr><td><code>/ct notify</code></td><td>List your per-category settings</td><td>everyone</td><td><b>yes</b></td></tr>
<tr><td><code>/ct notify &lt;cat&gt; on|off</code></td><td>Silence a category for yourself</td><td>everyone</td><td><b>yes</b></td></tr>
<tr><td><code>/ct notify &lt;cat&gt; sound on|off</code></td><td>Keep the message, drop the sound</td><td>everyone</td><td><b>yes</b></td></tr>
<tr><td><code>/ct notify &lt;cat&gt; radius &lt;n&gt;</code></td><td>Narrow it to your own radius</td><td>everyone</td><td><b>yes</b></td></tr>
<tr><td><code>/ct theme &lt;name&gt;</code></td><td>Switch GUI theme</td><td>everyone</td><td>no</td></tr>
<tr><td><code>/ct waypoint &lt;id&gt;</code></td><td>Drop a stored spawn on your minimap</td><td>everyone</td><td>no</td></tr>
<tr><td><code>/ct reload</code></td><td>Reload the YAML config</td><td>op</td><td><b>yes</b></td></tr>
<tr><td><code>/ct admin</code></td><td>Live settings panel</td><td>op</td><td>no</td></tr>
<tr><td><code>/ct fakehit</code></td><td>Spawn a test shiny Pikachu</td><td>op</td><td><b>yes</b></td></tr>
</table>

<div class="note"><b>Why some are chat-only by design</b>
<p><code>/checklegendary</code> and <code>/ct notify</code> print plain chat rather than opening a screen.
On most servers the majority of players have no client mod, and a feature they cannot reach is not a
feature.</p></div>
`
},

{
group: "Reference", id: "permissions", title: "Permissions & LuckPerms",
lede: "Nodes are used automatically when LuckPerms is present; operator levels otherwise.",
body: `
<p>CobbleTracker has <b>no compile-time dependency</b> on LuckPerms. It looks for the API at runtime and
uses it if it is there. Nothing to install, nothing to configure.</p>

<table>
<tr><th>Node</th><th>Command</th><th>Fallback</th></tr>
<tr><td><code>cobbletracker.command.gui</code></td><td><code>/ct</code>, <code>/last</code>, <code>/ll</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.lastlegend</code></td><td><code>/lastlegend</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.checklegendary</code></td><td><code>/checklegendary</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.notify</code></td><td><code>/ct notify</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.waypoint</code></td><td><code>/ct waypoint</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.theme</code></td><td><code>/ct theme</code></td><td>everyone</td></tr>
<tr><td><code>cobbletracker.command.spawnlegendary</code></td><td><code>/spawnlegendary</code></td><td>op (level 2)</td></tr>
<tr><td><code>cobbletracker.command.reload</code></td><td><code>/ct reload</code></td><td>op (level 2)</td></tr>
<tr><td><code>cobbletracker.command.admin</code></td><td><code>/ct admin</code></td><td>op (level 2)</td></tr>
<tr><td><code>cobbletracker.command.fakehit</code></td><td><code>/ct fakehit</code></td><td>op (level 2)</td></tr>
</table>

<h3>Three states, not two</h3>
<ul>
<li><b>Set true</b> &rarr; allowed, even without op.</li>
<li><b>Set false</b> &rarr; denied, <b>even to an operator</b>. That is the point of running a permissions plugin.</li>
<li><b>Undefined</b> &rarr; falls back to the operator level in the table. It does <b>not</b> mean "deny".</li>
</ul>

<div class="warn"><b>Why undefined must mean fallback</b>
<p>If an unset node denied, installing LuckPerms would instantly lock every player out of <code>/ct</code>
until an admin granted ten nodes by hand. Falling back means installing LuckPerms changes nothing until
you actually set something.</p></div>

<h3>Examples</h3>
<pre><code>/lp group default permission set cobbletracker.command.gui true
/lp group vip permission set cobbletracker.command.spawnlegendary true
/lp user Steve permission set cobbletracker.command.notify false</code></pre>

<p>Console and command blocks are judged on the vanilla level only &mdash; nodes are about players, and the
console has to keep working.</p>
`
},

{
group: "Reference", id: "config", title: "config.yml",
lede: "Server rules and the tracker categories that drive everything else.",
body: `
<h3>general-settings</h3>
<pre><code>general-settings:
  chat-prefix: "&lt;bold&gt;&lt;gradient:#6C5CE7:#38BDF8&gt;[CobbleTracker]&lt;/gradient&gt;&lt;/bold&gt; "
  hide-exact-position: false
  show-title: false</code></pre>
<table>
<tr><th>Key</th><th>Meaning</th></tr>
<tr><td><code>chat-prefix</code></td><td>MiniMessage prefix on every CobbleTracker line. Legacy <code>&amp;</code>-codes work too.</td></tr>
<tr><td><code>hide-exact-position</code></td><td><code>true</code> rounds every coordinate to the chunk centre &mdash; chat, beam, waypoint, history GUI <b>and</b> Discord. Operators still see exact positions in the GUI.</td></tr>
<tr><td><code>show-title</code></td><td>Also flash the title/subtitle from <code>announcements.yml</code> on screen.</td></tr>
</table>

<h3>beam</h3>
<pre><code>beam:
  enabled: true
  radius: 0            # 0 = auto (your render distance)
  duration-seconds: 600
  height: 512</code></pre>
<div class="note"><b>Not in <code>/ct admin</code>, on purpose</b>
<p>Each spawn carries its beam settings to the client in its own packet at the moment it happens.
An edit in a live panel could therefore only ever affect the <i>next</i> spawn, while appearing to do
nothing to what was on screen. These live here and apply on <code>/ct reload</code>.</p></div>

<h3>minimap</h3>
<pre><code>minimap:
  enabled: true
  xaero: true
  voxelmap: true
  journeymap: true
  use-beam-color: true</code></pre>

<h3>hunt</h3>
<pre><code>hunt:
  enabled: true</code></pre>
<p>Server's say over the client-side <code>\\</code> menu. Set <code>false</code> on a server where finding rare
spawns is meant to be the challenge: the menu refuses to open and hunt beams stay dark. A player's
saved species list is <b>kept</b>, and works again on a server that allows it.</p>

<h3>trackers</h3>
<pre><code>trackers:
  legendaries:
    name: "Legends"              # GUI label
    spec: "isLegendary:true"     # see the Spec language page
    color: "#FF3333"             # tier colour: beam, pill, waypoint
    max-stored: 10               # history cap; oldest evicted first. 0 = announce but keep nothing
    blacklist: "magikarp"        # comma list, never recorded
    enabled: true
    dimensions: ""               # comma list of dimension ids; empty = everywhere
    on-spawn-commands: []
    on-catch-commands: []
    spawn:
      enabled: true
      mode: auto                 # auto | always | never
      context-aware: true
      interval-ticks: 36000      # 20 ticks = 1 second
      chance: 0.30
      distribute-among-players: true
      player-cooldown-ticks: 108000
      min-distance: 32
      max-distance: 80
      level: "55-75"             # range or a single number
      shiny: false
      species-pool: ""           # empty = derive from the category</code></pre>

<div class="tip"><b>Order matters</b>
<p>Categories are checked <b>top to bottom, first match wins</b>. A shiny starter lands in whichever of
<code>shinies</code> / <code>starters</code> is higher. Reorder, or narrow with a negation:
<code>spec: "label:starter !isShiny"</code>.</p></div>
`
},

{
group: "Reference", id: "announcements", title: "announcements.yml",
lede: "How each category is broadcast. Keys must match the category ids exactly.",
body: `
<pre><code>notifications:
  legendaries:
    enabled: true
    title: "&lt;gold&gt;&lt;bold&gt;LEGENDARY SPAWN&lt;/bold&gt;&lt;/gold&gt;"
    subtitle: "&lt;aqua&gt;%species%&lt;/aqua&gt; appeared in the &lt;green&gt;%biome%&lt;/green&gt;!"
    chat: "&lt;white&gt;A wild&lt;/white&gt; &lt;aqua&gt;%species%&lt;/aqua&gt; &lt;gray&gt;at&lt;/gray&gt; %waypoint%"
    actionbar: ""
    sound: "cobblemon:pc.on"
    sound-volume: 1.0
    sound-pitch: 1.0
    waypoint: true
    play-to-all: true
    broadcast-radius: 0
    discord-webhook: ""
    discord-template: "**%species%** in %biome% at %x%, %y%, %z%"</code></pre>

<div class="warn"><b>A category with no block here is silent</b>
<p>An unknown id resolves to "disabled", which switches off the chat line <b>and the beam</b>. If you add
a category and it records to history but never announces, this is why.</p></div>

<h3>Channels</h3>
<table>
<tr><th>Key</th><th>Notes</th></tr>
<tr><td><code>chat</code></td><td>The main line. Supports the clickable <code>%waypoint%</code>.</td></tr>
<tr><td><code>title</code> / <code>subtitle</code></td><td>Only shown when <code>show-title: true</code> in config.yml.</td></tr>
<tr><td><code>actionbar</code></td><td>Above the hotbar. <b>Independent</b> of <code>show-title</code> &mdash; the quiet option. Not clickable, so <code>%waypoint%</code> renders as plain coordinates.</td></tr>
<tr><td><code>sound</code></td><td>Any sound id. <code>sound-volume: 0</code> is a deliberate mute, not "default".</td></tr>
<tr><td><code>discord-webhook</code></td><td>See <a href="#discord">Discord</a>.</td></tr>
</table>

<h3>Placeholders</h3>
<p><code>%species%</code> <code>%biome%</code> <code>%world%</code> <code>%x%</code> <code>%y%</code>
<code>%z%</code> <code>%waypoint%</code></p>

<h3>Reach</h3>
<table>
<tr><th><code>play-to-all</code></th><th><code>broadcast-radius</code></th><th>Result</th></tr>
<tr><td>true</td><td>0</td><td>Whole server</td></tr>
<tr><td>true / false</td><td>&gt; 0</td><td>Only players within N blocks, same dimension</td></tr>
<tr><td>false</td><td>0</td><td>Implicit 128-block radius</td></tr>
</table>

<div class="note"><b>Radius is enforced everywhere</b>
<p>A radius-limited category also limits its <i>history</i>: its past spawns only appear in the GUI of
players near them. The GUI never becomes the way around a deliberately local announcement.</p></div>

<h3>Formatting</h3>
<p>MiniMessage: <code>&lt;gold&gt;</code>, <code>&lt;bold&gt;</code>, <code>&lt;#RRGGBB&gt;</code>,
<code>&lt;gradient:#a:#b&gt;&hellip;&lt;/gradient&gt;</code>. Legacy <code>&amp;6&amp;l</code> codes are converted
automatically.</p>
`
},

{
group: "Reference", id: "legendaries-yml", title: "legendaries.yml",
lede: "Where and when the built-in director may place each legendary.",
body: `
<p>Used <b>only</b> while the director is actually running. Under <code>mode: auto</code> it is ignored the
moment a legendary spawn datapack is detected.</p>

<pre><code>legendaries:
  - species: "cobblemon:rayquaza"
    weight: 1.0
    biomes: ["#cobblemon:is_peak", "#cobblemon:is_mountain"]
    time: day
    isRaining: false
    minY: 120

  - species: "cobblemon:lugia"
    biomes: ["#cobblemon:is_ocean"]
    time: night

  - species: "cobblemon:zapdos"
    biomes: ["#cobblemon:is_highlands", "#cobblemon:is_plains"]
    isThundering: true</code></pre>

<table>
<tr><th>Key</th><th>Meaning</th></tr>
<tr><td><code>species</code></td><td>Namespaced id. Required.</td></tr>
<tr><td><code>weight</code></td><td>Relative likelihood among the rules that fit. Default 1.0.</td></tr>
<tr><td><code>biomes</code></td><td>Biome ids and <code>#namespace:tag</code> tags. Empty = anywhere.</td></tr>
<tr><td><code>dimensions</code></td><td>Dimension ids. Empty = any.</td></tr>
<tr><td><code>time</code></td><td><code>day</code>, <code>night</code> or <code>any</code>.</td></tr>
<tr><td><code>isRaining</code> / <code>isThundering</code></td><td><code>true</code>, <code>false</code>, or omit for "don't care".</td></tr>
<tr><td><code>minY</code> / <code>maxY</code></td><td>Height band for the spawn position.</td></tr>
</table>

<div class="tip"><b>Same vocabulary as Cobblemon</b>
<p>These keys deliberately mirror Cobblemon's own spawn-condition JSON, so a biome tag you already use in
a spawn file works here unchanged. Cobblemon ships 55 biome tags: <code>#cobblemon:is_ocean</code>,
<code>is_mountain</code>, <code>is_desert</code>, <code>is_cave</code>, <code>is_freezing</code>,
<code>is_jungle</code>, <code>is_magical</code>, <code>is_deep_dark</code>&hellip;</p></div>

<div class="warn"><b>Only implemented species can spawn</b>
<p>Cobblemon 1.7.3 implements <b>16</b> legendaries: articuno, ho-oh, latias, latios, lugia, mewtwo,
moltres, rayquaza, regice, regidrago, regieleki, regigigas, regirock, registeel, xerneas, zapdos
(plus 2 mythicals and 2 ultra beasts). A rule naming anything else simply never fires.</p></div>

<p>A typo in <code>isRaining</code> is <b>reported in the log</b> rather than silently becoming "don't care" &mdash;
a rule meant for rain quietly firing in sunshine is hard to notice otherwise.</p>
`
},

{
group: "Reference", id: "spec", title: "Spec language",
lede: "The matcher every category is built on. One line decides what a category is.",
body: `
<p>A spec is a space-separated list of tokens, all <b>AND</b>-ed together.</p>
<pre><code>spec: "isShiny:true level:>50"</code></pre>

<h3>Tokens about the Pok&eacute;mon</h3>
<table>
<tr><th>Token</th><th>Example</th><th>Notes</th></tr>
<tr><td><code>isShiny</code></td><td><code>isShiny</code> or <code>isShiny:true</code></td><td>A bare token means true</td></tr>
<tr><td><code>isLegendary</code></td><td><code>isLegendary:true</code></td><td>Species label</td></tr>
<tr><td><code>isMythical</code></td><td><code>isMythical:true</code></td><td>Species label</td></tr>
<tr><td><code>isUltraBeast</code></td><td><code>isUltraBeast:true</code></td><td>Species label</td></tr>
<tr><td><code>isBoss</code></td><td><code>isBoss:true</code></td><td>Label or aspect</td></tr>
<tr><td><code>species</code></td><td><code>species:rayquaza</code></td><td>Bare name or namespaced id</td></tr>
<tr><td><code>label</code></td><td><code>label:starter</code></td><td>See <a href="#labels">Labels</a></td></tr>
<tr><td><code>aspect</code></td><td><code>aspect:alolan</code></td><td>Instance aspects &mdash; the way to catch regional forms</td></tr>
<tr><td><code>form</code></td><td><code>form:alola</code></td><td>Form name, lower-cased</td></tr>
<tr><td><code>gender</code></td><td><code>gender:female</code></td><td><code>male</code> / <code>female</code> / <code>genderless</code></td></tr>
<tr><td><code>nature</code></td><td><code>nature:adamant</code></td><td>Path only, no namespace</td></tr>
<tr><td><code>ability</code></td><td><code>ability:levitate</code></td><td>Ability id, lower-cased</td></tr>
<tr><td><code>level</code></td><td><code>level:&gt;50</code></td><td>Supports <code>&gt;</code> <code>&lt;</code> <code>&gt;=</code> <code>&lt;=</code> comparisons</td></tr>
<tr><td><code>perfectIvs</code></td><td><code>perfectIvs:&gt;=4</code></td><td>How many IVs are at 31</td></tr>
<tr><td><code>ivs</code></td><td><code>ivs:&gt;=150</code></td><td>Sum of all six, max 186</td></tr>
<tr><td><code>scale</code></td><td><code>scale:&gt;1.15</code></td><td>Size modifier; 1.0 is standard</td></tr>
</table>

<h3>Tokens about the circumstances</h3>
<table>
<tr><th>Token</th><th>Example</th><th>Notes</th></tr>
<tr><td><code>biome</code></td><td><code>biome:jungle</code></td><td>Bare name = any namespace; <code>minecraft:jungle</code> = exactly that one</td></tr>
<tr><td><code>dimension</code></td><td><code>dimension:minecraft:the_nether</code></td><td></td></tr>
<tr><td><code>time</code></td><td><code>time:night</code></td><td><code>day</code> or <code>night</code></td></tr>
<tr><td><code>weather</code></td><td><code>weather:thunder</code></td><td><code>rain</code>, <code>thunder</code> or <code>clear</code></td></tr>
<tr><td><code>y</code></td><td><code>y:&lt;0</code></td><td>Spawn height</td></tr>
</table>

<h3>Comparison</h3>
<p>Numeric tokens take <code>&gt;</code>, <code>&lt;</code>, <code>&gt;=</code>, <code>&lt;=</code> or a bare
number for equality. Equality is compared with a small tolerance, so <code>scale:1.15</code> matches a
Pok&eacute;mon whose float scale is 1.15.</p>

<h3>Operators</h3>
<p>Prefix a token with <b><code>!</code></b> to negate it, and use <b><code>|</code></b> inside a value for
<i>or</i>:</p>
<pre><code>spec: "isShiny !species:magikarp"
spec: "isLegendary species:rayquaza|lugia"
spec: "level:&gt;50|&lt;10"
spec: "isShiny time:night biome:swamp"</code></pre>

<div class="note"><b>Both operators work on every token</b>
<p>They are applied when the spec is compiled, not inside individual handlers, so any token &mdash; including
ones added in future versions &mdash; supports them.</p></div>

<div class="warn"><b>Errors are loud, not silent</b>
<p>An unknown token, an unreadable boolean (<code>isShiny:maybe</code>) or an empty alternative
(<code>a||b</code>) makes the category match <b>nothing</b> and writes the reason to the log. It never
silently matches everything.</p></div>

<h3>Interaction with the built-in spawner</h3>
<p>The spawner will not place something its own category would reject. If you write a spec the spawner
cannot satisfy &mdash; <code>isBoss:true</code>, for instance &mdash; it warns <b>once</b> and places nothing.
A negated token like <code>!isShiny</code> is treated as "no instruction", so <code>spawn.shiny</code>
stays in charge.</p>
`
},

{
group: "Reference", id: "labels", title: "Cobblemon labels",
lede: "What label: can actually match, measured from Cobblemon 1.7.3 — including the trap.",
body: `
<p>Counts are <b>implemented species</b> in Cobblemon 1.7.3 (851 in total).</p>

<table>
<tr><th>Label</th><th>Species</th><th>Use</th></tr>
<tr><td><code>gen1</code></td><td>151</td><td rowspan="9">Generation groups</td></tr>
<tr><td><code>gen2</code></td><td>96</td></tr>
<tr><td><code>gen3</code></td><td>119</td></tr>
<tr><td><code>gen4</code></td><td>85</td></tr>
<tr><td><code>gen5</code></td><td>135</td></tr>
<tr><td><code>gen6</code></td><td>63</td></tr>
<tr><td><code>gen7</code></td><td>54</td></tr>
<tr><td><code>gen8</code></td><td>67</td></tr>
<tr><td><code>gen9</code></td><td>75</td></tr>
<tr><td><code>starter</code></td><td>27</td><td>Base-form starters only</td></tr>
<tr><td><code>legendary</code></td><td>16</td><td>Tracked by default category</td></tr>
<tr><td><code>fossil</code></td><td>21</td><td>Revived fossil species</td></tr>
<tr><td><code>baby</code></td><td>19</td><td>Pre-evolution baby forms</td></tr>
<tr><td><code>powerhouse</code></td><td>9</td><td>Pseudo-legendaries</td></tr>
<tr><td><code>restricted</code></td><td>5</td><td>Box legendaries</td></tr>
<tr><td><code>mythical</code></td><td>2</td><td>Event-exclusive mythicals</td></tr>
<tr><td><code>ultra_beast</code></td><td>2</td><td>Ultra Space entities</td></tr>
<tr><td><code>paradox</code></td><td>2</td><td>Past/future paradox forms</td></tr>
<tr><td><code>kantonian_form</code></td><td>37</td><td rowspan="3">Base regional variants</td></tr>
<tr><td><code>unovan_form</code></td><td>14</td></tr>
<tr><td><code>johtonian_form</code></td><td>9</td></tr>
</table>

<div class="warn"><b>The trap: <code>label:</code> reads the <i>species</i>, not the individual</b>
<p><code>mega</code>, <code>gmax</code>, <code>hisuian_form</code> and <code>galarian_form</code> are declared on
<b>forms</b>, not on the species. <code>label:</code> matches the species' own labels, so
<code>label:mega</code> and <code>label:hisuian_form</code> match <b>nothing</b>.</p>
<p>An Alolan Vulpix, for instance, still carries the species labels <code>gen1, kantonian_form</code>. What
identifies it as Alolan is its <b>aspect</b>.</p></div>

<div class="tip"><b>Use <code>aspect:</code> or <code>form:</code> for variants</b>
<pre><code>spec: "aspect:alolan"     # any Alolan form
spec: "form:alola"        # same, matched by form name
spec: "aspect:hisuian"
spec: "aspect:galarian"</code></pre></div>

<h3>Finding labels yourself</h3>
<p>Cobblemon's species files live in its jar at
<code>data/cobblemon/species/generationN/&lt;name&gt;.json</code>. The top-level <code>labels</code> array is what
<code>label:</code> sees; anything under <code>forms[]</code> is not.</p>
`
},

/* ============================== FEATURES ================================ */
{
group: "Features", id: "legendary", title: "Legendary director",
lede: "How CobbleTracker decides when, where and which — and when it gets out of the way.",
body: `
<h3>Why it exists</h3>
<p>Vanilla Cobblemon has no legendary spawn data at all. Without a datapack or this director, legendaries
only come from shrines.</p>

<h3>The three modes</h3>
<table>
<tr><th>Mode</th><th>Behaviour</th></tr>
<tr><td><code>auto</code> <i>(default)</i></td><td>Runs only while Cobblemon's live spawn pool has <b>no</b> legendary entries. The moment a datapack adds some, the director stands down.</td></tr>
<tr><td><code>always</code></td><td>Runs regardless. Use if you deliberately want both sources.</td></tr>
<tr><td><code>never</code></td><td>Dormant. The config stays, nothing spawns.</td></tr>
</table>

<p>The decision is written to the log whenever it changes, so it is never a silent no-op:</p>
<pre><code>Spawner for 'legendaries' is active (mode AUTO)
Spawner for 'legendaries' is standing down: Cobblemon's own spawn data
  already covers this tier (mode 'auto')</code></pre>

<h3>How a spawn is chosen</h3>
<ol>
<li>Every <code>interval-ticks</code>, one <code>chance</code> roll for the whole server (with
<code>distribute-among-players</code>), then a random eligible player becomes the <b>anchor</b>.</li>
<li>A position is picked between <code>min-distance</code> and <code>max-distance</code> from the anchor.</li>
<li><b>The rules are judged at that position</b>, not at the player &mdash; the two are routinely in different
biomes. Several positions are tried before giving up.</li>
<li>Among the rules that fit, one is picked by <code>weight</code>.</li>
<li>The built Pok&eacute;mon must still satisfy the category's own spec before it is placed.</li>
</ol>

<div class="tip"><b>"Nothing fits" is a feature</b>
<p>If no rule matches where the spawn would land, nothing is placed. A Regice in a desert would technically
be a legendary spawn, and would also make the whole system look arbitrary.</p></div>

<h3>Fairness</h3>
<ul>
<li><code>distribute-among-players: true</code> &mdash; one roll for the server, so the rate does not balloon
with player count. Set <code>false</code> for an independent roll per player.</li>
<li><code>player-cooldown-ticks</code> &mdash; a player who just anchored a spawn is skipped for this long, so
the same person does not get every one.</li>
</ul>

<h3>Tuning the rate</h3>
<p>Expected spawns per hour &asymp; <code>3600 / (interval-ticks / 20) &times; chance</code>.</p>
<table>
<tr><th>Feel</th><th>interval-ticks</th><th>chance</th><th>Roughly</th></tr>
<tr><td>Event-grade</td><td>72000 (1 h)</td><td>0.15</td><td>one per ~7 hours</td></tr>
<tr><td>Default</td><td>36000 (30 min)</td><td>0.30</td><td>one per ~1.7 hours</td></tr>
<tr><td>Busy server</td><td>18000 (15 min)</td><td>0.40</td><td>one per ~40 minutes</td></tr>
</table>
`
},

{
group: "Features", id: "checklegendary", title: "/checklegendary",
lede: "Reads Cobblemon's live spawn pool — so it tells the truth about whatever you have installed.",
body: `
<pre><code>Legendary spawns - overworld, plains, night, thunderstorm
  &#10004; Zapdos       ultra-rare &middot; ~0.02% per attempt
  &#10004; Regieleki    ultra-rare &middot; ~0.02% per attempt
  6 other legendaries need a different biome, time or weather.
  Next attempt: 12m 30s &middot; chance 30%</code></pre>

<p>It does <b>not</b> parse spawn JSON itself. It reads the registry Cobblemon has already loaded, merged
and validated &mdash; so <b>any</b> datapack works, in any namespace.</p>

<h3>What it evaluates</h3>
<p>Biome (tags included), time of day, weather, dimension, height, light level, sky visibility and moon
phase, per individual condition, exactly as Cobblemon's own spawner does. Anticonditions invert.</p>

<div class="note"><b>"(some conditions unchecked)"</b>
<p>Structures, markers and slime chunks need a real spawn position to evaluate and cannot be judged from
a command. When an entry depends on one, the report says so rather than pretending certainty.</p></div>

<h3>Two different reports</h3>
<ul>
<li><b>Pool has legendary entries</b> &rarr; lists them with an approximate per-attempt chance.</li>
<li><b>Pool is empty</b> &rarr; says so, then lists the director's own rules that fit where you stand.</li>
</ul>

<h3>Also a diagnostic</h3>
<p>If you installed a legendary datapack and this still says <i>"Cobblemon has no legendary spawns
loaded"</i>, the datapack did not take. The usual cause is a datapack written for an older Cobblemon:
1.7 renamed the spawn key <code>context</code> to <code>spawnablePositionType</code>, and entries using the
old name are rejected at load.</p>

<h3>About the odds</h3>
<p>The figure is bucket share &times; the entry's weight among everything eligible at that spot. It is an
approximation &mdash; the real spawner also weighs position types and samples several positions per attempt &mdash;
but it is built from live data, not invented.</p>
`
},

{
group: "Features", id: "beam", title: "Spawn beam",
lede: "Presence-based, not a timer.",
body: `
<p>A beacon-style column stands over a tracked Pok&eacute;mon while it is in your loaded chunks, coloured by
the category's tier colour.</p>

<h3>How the grace works</h3>
<p><code>duration-seconds</code> is a <b>keep-alive</b>, not a lifetime. Every frame the Pok&eacute;mon is
visible, the deadline is pushed out. So the beam:</p>
<ul>
<li>shows whenever you are near,</li>
<li>survives you flying off and coming back within the window,</li>
<li>lapses only after it has been out of range for the whole window,</li>
<li>and disappears <b>at once</b> on a real catch, faint or despawn &mdash; not after the grace.</li>
</ul>

<h3>Reconnecting</h3>
<p>Beams are re-sent when you join, so relogging does not leave a legendary standing there unmarked.
The re-send obeys the same rules the original announcement did &mdash; a category you silenced stays silent.</p>

<h3>Radius</h3>
<p><code>radius: 0</code> means "your render distance", which is the furthest a Pok&eacute;mon exists on your
client anyway. A fixed number caps it tighter.</p>

<div class="note"><b>No ghost beams</b>
<p>The server distinguishes a real despawn from a chunk simply unloading because everyone flew away.
It only reports "despawned" if a player was near enough to witness it &mdash; otherwise it keeps watching, and
the beam returns when someone comes back.</p></div>

<h3>Client toggle</h3>
<p>The hunt screen has a <b>Beam: ON/OFF</b> button. It is local to that client and never reaches the
server.</p>
`
},

{
group: "Features", id: "minimap", title: "Minimap waypoints",
lede: "Xaero's, VoxelMap and JourneyMap — only the ones you actually have.",
body: `
<p>Announcements can carry a clickable <b>Create Waypoint</b> link. Clicking it drops the spawn on your
minimap. Clicking a card in the history GUI does the same for a <b>past</b> spawn.</p>

<h3>How detection works</h3>
<p>Your client tells the server which minimap mods it has when it joins. The server only ever emits those
formats. No minimap mod is required &mdash; without one you still get the beam, and clicking the link tells you
plainly that nothing is installed.</p>

<p>Waypoints are emitted through each mod's own stable chat-token format, so CobbleTracker has no compile
dependency on any of them.</p>

<div class="tip"><b><code>/ct admin</code> shows the truth</b>
<p>Each minimap row is marked <i>(not installed)</i> and shown off when <b>you</b> don't have that mod.
A format switched on server-side that nobody has installed is a toggle that does nothing, and reading it
as "waypoints work" is the wrong conclusion.</p></div>

<p><code>use-beam-color: true</code> colours the marker to match the tier. Xaero's honours the colour;
the VoxelMap/JourneyMap share format uses its own default.</p>
`
},

{
group: "Features", id: "notify", title: "Player notification settings",
lede: "So an operator never has to switch a category off for everyone.",
body: `
<p>Every player controls their own notifications, per category, from the <b>NOTIFY</b> tab in the GUI or
<code>/ct notify</code> in chat.</p>

<pre><code>/ct notify                          list your settings
/ct notify shinies off              silence shinies for yourself
/ct notify shinies sound off        keep the message, drop the sound
/ct notify legendaries radius 300   only within 300 blocks of you</code></pre>

<h3>What "off" covers</h3>
<p>Everything for that category: chat, sound, title, action bar, the beam, and the catch/faint/despawn
report. There is a single choke point that decides who a spawn reaches, so nothing leaks around it.</p>

<h3>Radius only ever narrows</h3>
<p>If the server broadcasts a category to everyone and you set <code>radius 300</code>, you get only nearby
ones. If the server already limits it to 200 blocks and you set 5000, you still get 200. A personal
preference cannot be used to see a deliberately local announcement from further away.</p>

<h3>Independence</h3>
<p>The three settings do not interfere. Muting the sound on a category you had silenced does not switch it
back on, and turning a category back on keeps the radius you set earlier.</p>

<p>Settings persist in <code>prefs.json</code> and default to everything on. Only non-default entries are
stored, so an untouched player costs nothing.</p>
`
},

{
group: "Features", id: "hunt", title: "Personal hunt mode",
lede: "Client-side tracking for whatever you're after. Press \\.",
body: `
<p>Press <code>\\</code> to open <b>Track a Pok&eacute;mon</b>, search the full species list and click to toggle
what you are hunting. Matching Pok&eacute;mon near you get an amber beam plus directional arrows in the
corner of your HUD with live distances.</p>

<ul>
<li>Entirely client-side &mdash; it scans entities your client already has, so there is no server round-trip
and nothing to configure.</li>
<li>Your list <b>persists between sessions</b> in <code>hunt.json</code>.</li>
<li><b>Shift-click a card</b> in the history GUI to start hunting that species &mdash; the history is where you
notice "that keeps spawning and I keep missing it".</li>
<li>Hunted species are tinted in the history list, so it doubles as "what am I already tracking".</li>
</ul>

<h3>Turning it off server-side</h3>
<pre><code>hunt:
  enabled: false</code></pre>
<p>On a server where finding rare spawns is meant to be the challenge, this is a legitimate thing to
disable. The <code>\\</code> menu then refuses to open and hunt beams stay dark.</p>

<div class="note"><b>It is a request, not enforcement</b>
<p>Hunting works off data the client already has, so this is a rule the client honours rather than
something the server can technically prevent. The player's species list is kept either way, and works
again on a server that allows it.</p></div>

<p>The key is a normal Minecraft keybind &mdash; rebind or clear it in <b>Options &rarr; Controls</b> under the
CobbleTracker category.</p>
`
},

{
group: "Features", id: "hooks", title: "Command hooks",
lede: "Rewards, economy and permissions with no extra dependency.",
body: `
<pre><code>trackers:
  legendaries:
    on-spawn-commands:
      - "say A legendary %species% appeared in %biome%!"
    on-catch-commands:
      - "give %player% minecraft:diamond 3"
      - "lp user %player% permission set someperk.node true"</code></pre>

<table>
<tr><th>Placeholder</th><th>Value</th></tr>
<tr><td><code>%player%</code></td><td>The catcher. Empty on <code>on-spawn-commands</code> &mdash; a spawn belongs to nobody yet.</td></tr>
<tr><td><code>%species%</code></td><td>Display name</td></tr>
<tr><td><code>%category%</code></td><td>Category id</td></tr>
<tr><td><code>%world%</code> <code>%biome%</code></td><td>Namespaced ids</td></tr>
<tr><td><code>%x%</code> <code>%y%</code> <code>%z%</code></td><td>Spawn position</td></tr>
</table>

<p>Commands run as the server console at permission level 2, <b>positioned at the spawn</b> &mdash; so
<code>~ ~ ~</code> and <code>execute</code> behave as you would expect.</p>

<div class="warn"><b>Editing config.yml is equivalent to operator access</b>
<p>Anyone who can edit this file can run any command as the console. Treat write access accordingly.</p></div>

<h3>Safety</h3>
<ul>
<li>A <code>%player%</code> or <code>%species%</code> that is not a plain identifier causes the command to be
<b>skipped and logged</b>, rather than pasted into a command line. There is no quoting that makes an
arbitrary string safe there.</li>
<li>A hook that causes another tracked spawn will not fire hooks again &mdash; without that guard,
<code>on-spawn-commands: ["pokespawn rayquaza"]</code> on the legendary category is an infinite loop.</li>
<li>At most 32 commands per event; the rest are skipped with a log line.</li>
<li>One broken command never costs the others.</li>
</ul>
`
},

{
group: "Features", id: "discord", title: "Discord webhooks",
lede: "Per category, off the server thread.",
body: `
<pre><code>notifications:
  legendaries:
    discord-webhook: "https://discord.com/api/webhooks/&hellip;"
    discord-template: "**%species%** spawned in %biome% at %x%, %y%, %z%"</code></pre>

<p>Plain Discord markdown, not MiniMessage. Placeholders: <code>%species%</code>, <code>%category%</code>,
<code>%biome%</code>, <code>%world%</code>, <code>%x%</code>, <code>%y%</code>, <code>%z%</code>.</p>

<h3>How it behaves under stress</h3>
<ul>
<li>Posted from a dedicated thread with a bounded queue &mdash; a slow or unreachable Discord never costs tick
time.</li>
<li>If the queue fills, the oldest messages are dropped and the log says so periodically.</li>
<li>The webhook URL is a credential and is <b>never written to the log</b>, not even on failure.</li>
<li>Only <code>https://</code> URLs are accepted.</li>
</ul>

<div class="note"><b>Privacy is preserved</b>
<p>Coordinates go through the same rounding as chat, so <code>hide-exact-position: true</code> applies to
Discord too. The webhook cannot become a way around it.</p></div>

<h3>Common patterns</h3>
<p>A public channel for legendaries and a staff channel for everything: give each category its own webhook.
Leave <code>discord-webhook: ""</code> on the ones you do not want posted.</p>
`
},

/* =============================== GUIDES ================================= */
{
group: "Guides", id: "recipes", title: "Recipes",
lede: "Ready-made categories and setups, including the ones people ask for that aren't obvious.",
body: `
<p>Each recipe is a <code>trackers:</code> block. <b>Every one also needs a matching block in
<code>announcements.yml</code> under the same key</b>, or it records silently. Order matters &mdash; first
match wins.</p>

<div class="recipe">
<h4>Starters</h4>
<p class="why">Cobblemon already labels them, so this is a one-liner.</p>
<pre><code>  starters:
    name: "Starters"
    spec: "label:starter"
    color: "#4ADE80"
    max-stored: 15</code></pre>
<p><b>Catch:</b> the label is on the <b>27 base forms only</b> &mdash; Ivysaur, Venusaur and Charizard do not
have it. For wild spawns that is usually what you want.</p>
</div>

<div class="recipe">
<h4>Pseudo-legendaries</h4>
<p class="why">No label exists for these, so the line is listed by hand.</p>
<pre><code>  pseudolegends:
    name: "Pseudo-Legends"
    spec: "species:dratini|larvitar|bagon|beldum|gible|deino|goomy|jangmoo|dreepy|frigibax"
    color: "#A78BFA"
    max-stored: 10</code></pre>
<p><b>Catch:</b> the ids have <b>no hyphens</b> &mdash; <code>jangmoo</code>, not <code>jangmo-o</code>. The
Frigibax line is not implemented in 1.7.3 yet; leaving it in is harmless. Add the final evolutions if a
wild Dragonite should shout too:
<code>&hellip;|dragonite|tyranitar|salamence|metagross|garchomp|hydreigon|goodra|kommoo|dragapult</code>.</p>
</div>

<div class="recipe">
<h4>Regional forms (Alolan, Hisuian, Galarian&hellip;)</h4>
<p class="why">The obvious <code>label:</code> approach does not work here.</p>
<pre><code>  regionals:
    name: "Regional Forms"
    spec: "aspect:alolan|hisuian|galarian|paldean"
    color: "#F472B6"</code></pre>
<p><b>Why:</b> <code>hisuian_form</code> and <code>galarian_form</code> are declared on <b>forms</b>, while
<code>label:</code> reads the <b>species</b>. An Alolan Vulpix still carries the species labels
<code>gen1, kantonian_form</code>. Its <b>aspect</b> is what says "alolan".</p>
</div>

<div class="recipe">
<h4>Perfect-IV alerts</h4>
<p class="why">The reason most players actually chase a spawn.</p>
<pre><code>  flawless:
    name: "Flawless"
    spec: "perfectIvs:&gt;=5"
    color: "#FBBF24"
    max-stored: 20</code></pre>
<p>Use <code>ivs:&gt;=150</code> instead for "generally excellent" rather than "five maxed".</p>
</div>

<div class="recipe">
<h4>Giants and runts</h4>
<p class="why">Size hunting is a whole collecting game of its own.</p>
<pre><code>  xxl:
    name: "XXL"
    spec: "scale:&gt;1.2"
    color: "#F97316"
  xxs:
    name: "XXS"
    spec: "scale:&lt;0.8"
    color: "#60A5FA"</code></pre>
</div>

<div class="recipe">
<h4>Competitive natures</h4>
<pre><code>  perfectmons:
    name: "Battle-Ready"
    spec: "perfectIvs:&gt;=4 nature:adamant|jolly|modest|timid"
    color: "#22D3EE"</code></pre>
</div>

<div class="recipe">
<h4>A Gen-1-only nostalgia server</h4>
<pre><code>  kanto:
    name: "Kanto"
    spec: "label:gen1"
    color: "#EF4444"
    max-stored: 30</code></pre>
<p>Any of <code>gen1</code>&hellip;<code>gen9</code> works. <code>gen8a</code> is the Legends: Arceus set.</p>
</div>

<div class="recipe">
<h4>Babies and fossils</h4>
<pre><code>  babies:
    name: "Babies"
    spec: "label:baby"
    color: "#FDA4AF"
  fossils:
    name: "Fossils"
    spec: "label:fossil"
    color: "#A16207"</code></pre>
</div>

<div class="recipe">
<h4>Night-only spooky category</h4>
<p class="why">Context tokens turn a category into an event.</p>
<pre><code>  midnight:
    name: "Midnight"
    spec: "time:night biome:swamp|dark_forest"
    color: "#7C3AED"</code></pre>
</div>

<div class="recipe">
<h4>Storm chasers</h4>
<pre><code>  stormborn:
    name: "Stormborn"
    spec: "weather:thunder !isShiny"
    color: "#FACC15"</code></pre>
</div>

<div class="recipe">
<h4>Deep-underground finds</h4>
<pre><code>  deepdark:
    name: "The Deep"
    spec: "y:&lt;0 biome:deep_dark"
    color: "#334155"</code></pre>
</div>

<div class="recipe">
<h4>Nether / End only</h4>
<p class="why">Two ways to do it &mdash; they are not the same.</p>
<pre><code>  nether:
    name: "Nether"
    spec: "isLegendary"
    dimensions: "minecraft:the_nether"    # hard filter, also limits the spawner</code></pre>
<p>Or as part of the spec, so it composes with the other tokens:</p>
<pre><code>    spec: "isShiny dimension:minecraft:the_end"</code></pre>
<p><b>Difference:</b> <code>dimensions:</code> is a category-level gate that the <b>built-in spawner also
obeys</b> &mdash; a Nether-only category never places anything in the Overworld.
<code>dimension:</code> in the spec only affects matching.</p>
</div>

<div class="recipe">
<h4>Shinies, but not the ones nobody cares about</h4>
<pre><code>  shinies:
    name: "Shinies"
    spec: "isShiny !species:magikarp|zubat|caterpie"
    color: "#55FF55"</code></pre>
<p>The <code>blacklist:</code> key does the same thing, and also stops the spawner from producing them.
Use the spec when you want the exclusion to compose with other tokens.</p>
</div>

<div class="recipe">
<h4>Reward the catch</h4>
<pre><code>  legendaries:
    on-catch-commands:
      - "give %player% cobblemon:rare_candy 5"
      - "say %player% caught a %species%!"
      - "eco give %player% 5000"</code></pre>
<p>Works with any plugin or mod that has a console command. No integration needed.</p>
</div>

<div class="recipe">
<h4>Public Discord for legendaries, staff channel for everything</h4>
<pre><code>notifications:
  legendaries:
    discord-webhook: "https://discord.com/api/webhooks/&lt;public&gt;"
    discord-template: "&#128142; **%species%** in %biome%!"
  shinies:
    discord-webhook: "https://discord.com/api/webhooks/&lt;staff&gt;"
    discord-template: "%species% at %x% %y% %z%"</code></pre>
</div>

<div class="recipe">
<h4>A quiet server</h4>
<p class="why">Some communities hate chat spam but still want to know.</p>
<pre><code>  shinies:
    chat: ""
    actionbar: "&lt;gold&gt;%species% nearby&lt;/gold&gt;"
    sound-volume: 0</code></pre>
<p>The action bar is independent of <code>show-title</code>, so this gives a cue with no chat line, no title
card and no sound.</p>
</div>

<div class="recipe">
<h4>PvP server: never hand out coordinates</h4>
<pre><code>general-settings:
  hide-exact-position: true</code></pre>
<p>Rounds every coordinate to the chunk centre across chat, beam, waypoint, history GUI and Discord.
Operators still see exact positions in the GUI so they can moderate.</p>
<p>Pair it with a radius so only nearby players hear at all:</p>
<pre><code>    play-to-all: false
    broadcast-radius: 150</code></pre>
</div>

<div class="recipe">
<h4>Event weekend: crank the legendaries</h4>
<pre><code>    spawn:
      interval-ticks: 12000     # every 10 minutes
      chance: 0.6
      player-cooldown-ticks: 0</code></pre>
<p><code>/ct reload</code> applies it live. Set it back on Monday.</p>
</div>

<div class="recipe">
<h4>You already run a legendary datapack</h4>
<p class="why">Do nothing.</p>
<pre><code>    spawn:
      mode: auto     # the default</code></pre>
<p>The director detects the datapack and stands down. <code>/checklegendary</code> then reports the
datapack's real entries and odds. If you want <b>both</b> sources, set <code>mode: always</code>.</p>
</div>

<div class="recipe">
<h4>Announce everything rare, one category</h4>
<pre><code>  rare:
    name: "Rare"
    spec: "isLegendary|isMythical"
    color: "#FF3333"</code></pre>
<p><b>Careful:</b> <code>|</code> works inside a token's <i>value</i>, not between tokens. The line above is
<b>not</b> valid. Use two categories, or a label the species share.</p>
</div>

<div class="recipe">
<h4>Split shiny starters from ordinary ones</h4>
<p class="why">Because first match wins, and you may want the opposite of the default.</p>
<pre><code>  shinystarters:
    name: "Shiny Starters"
    spec: "label:starter isShiny"
    color: "#FDE047"
  starters:
    name: "Starters"
    spec: "label:starter !isShiny"
    color: "#4ADE80"</code></pre>
<p>Put both <b>above</b> the general <code>shinies</code> category.</p>
</div>
`
},

{
group: "Guides", id: "troubleshooting", title: "Troubleshooting",
lede: "Symptom first.",
body: `
<h3>My new category records to history but never announces</h3>
<p>It has no block in <code>announcements.yml</code>. An unknown id resolves to "disabled", which also
switches off the beam. The key must match the category id exactly.</p>

<h3>A category matches nothing</h3>
<p>Check the log at startup or after <code>/ct reload</code>:</p>
<pre><code>Tracker 'x' has an unusable spec ("..."): unknown spec token 'isfluffy'
  - it will not match anything until fixed</code></pre>
<p>Common causes: a typo'd token, an unreadable boolean (<code>isShiny:maybe</code>), an empty alternative
(<code>a||b</code>), or <code>label:mega</code> / <code>label:hisuian_form</code> &mdash; those are form-level
labels, see <a href="#labels">Labels</a>.</p>

<h3><code>/ct reload</code> says it kept the previous settings</h3>
<p>One of the YAML files did not parse. The running configuration is deliberately left alone. The log
names the line. A frequent cause is the <code>trackers:</code> block being indented one level too far &mdash;
that is still valid YAML, it just makes every tracker vanish, which is exactly what this check exists to
catch.</p>

<h3>Beam settings seem to do nothing</h3>
<p>They are read when a spawn happens. Change them in <code>config.yml</code>, <code>/ct reload</code>, then
trigger a <b>new</b> spawn. They are not in <code>/ct admin</code> for this reason.</p>

<h3>The beam vanished when I flew away</h3>
<p>Expected while it is out of range beyond the grace window. If the Pok&eacute;mon itself is gone, that is
Cobblemon's despawner, not CobbleTracker &mdash; wild Pok&eacute;mon despawn when no player is near. When
testing across a relog, raise <code>despawnerMinAgeTicks</code> in
<code>config/cobblemon/main.json</code>, or you will be testing the despawner.</p>

<h3>/checklegendary says no legendary spawns loaded, but I installed a datapack</h3>
<p>The datapack did not take. Most often it targets an older Cobblemon: 1.7 renamed the spawn key
<code>context</code> to <code>spawnablePositionType</code>, and entries using the old name are rejected.
Check the server log at startup for Cobblemon's own errors.</p>

<h3>/spawnlegendary refuses</h3>
<p>No rule fits where the spawn would land. That is the context system working. Run
<code>/checklegendary</code> to see what the spot allows, or move to a matching biome / wait for the
weather.</p>

<h3>Waypoint link says no minimap detected</h3>
<p>The client did not advertise a supported minimap. Check it is installed client-side, and that the
format is not switched off server-side in <code>minimap:</code>.</p>

<h3>Everyone lost access to /ct after installing LuckPerms</h3>
<p>That should not happen &mdash; an undefined node falls back to the default. If it did, a node was explicitly
set to <code>false</code> somewhere in the inheritance chain. Check with
<code>/lp user &lt;name&gt; permission check cobbletracker.command.gui</code>.</p>

<h3>The Discord webhook posts nothing</h3>
<p>Check the log for <code>Discord webhook post failed</code>. The URL is never logged, so verify it by
hand. It must be <code>https://</code>. Note that the category's own <code>enabled</code> must be true.</p>
`
},

{
group: "Guides", id: "files", title: "Data & files",
lede: "What lives where, and what is safe to delete.",
body: `
<table>
<tr><th>File</th><th>Side</th><th>Edit by hand?</th><th>Contents</th></tr>
<tr><td><code>config/cobbletracker/config.yml</code></td><td>server</td><td>yes</td><td>Server rules, tracker categories, spawner, hooks</td></tr>
<tr><td><code>config/cobbletracker/announcements.yml</code></td><td>server</td><td>yes</td><td>Per-category announcement text, sound, Discord</td></tr>
<tr><td><code>config/cobbletracker/legendaries.yml</code></td><td>server</td><td>yes</td><td>Director rules</td></tr>
<tr><td><code>config/cobbletracker/tracker.json</code></td><td>server</td><td>no</td><td>Spawn history</td></tr>
<tr><td><code>config/cobbletracker/prefs.json</code></td><td>server</td><td>no</td><td>Per-player notification settings</td></tr>
<tr><td><code>config/cobbletracker/hunt.json</code></td><td><b>client</b></td><td>no</td><td>Your hunted species list</td></tr>
</table>

<h3>Deleting things</h3>
<ul>
<li>Delete a YAML file and it is regenerated with commented defaults on next start.</li>
<li>Delete <code>tracker.json</code> to wipe history. Nothing else depends on it.</li>
<li>Delete <code>prefs.json</code> to reset every player to "everything on".</li>
</ul>

<h3>Resilience</h3>
<ul>
<li>JSON files are written to a temp file and moved into place, so a crash mid-write leaves the previous
copy intact.</li>
<li>A corrupt <code>tracker.json</code> logs an error and starts empty rather than taking the server down.</li>
<li>YAML is parsed with a restricted reader &mdash; a config file can never instantiate arbitrary classes.</li>
<li>Records under a category you removed from the config are <b>kept</b>, not deleted. They simply are not
shown. Put the category back and its history returns.</li>
</ul>

<h3>Migrating from 1.0.0</h3>
<p>Nothing to do. An old <code>config.yml</code> loads as-is, including the removed <code>kind: block</code>
key &mdash; and such a category now actually works, where before it was silently ignored. Old history entries
have no Pok&eacute;mon details, so their cards simply omit that line.</p>
`
}

]};
