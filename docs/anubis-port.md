# Anubis → Hafnium integration map

Anubis (by 4ldenz, GPL-3.0-only, <https://github.com/4ldenz/Anubis-client>) is the primary implementation reference
for overlapping modules. Hafnium is also GPL-3.0-only, so its code can be reused: ported files keep a header naming
Anubis and its author, and the README credits it.

**Source used:** the source repository is not public (404), and no source tree was supplied, so the reference is the
signed release jar `Anubis_Client_1.0.jar` (jarsigner-verified, signer "CN=Anubis Client"), remapped from
intermediary to Mojang names with Loom's mappings and decompiled with Vineflower into `reference/anubis/`
(git-ignored; 589 client classes + 150 mixins). Decompiled code has lost comments and some local variable names.
Both projects target Minecraft 1.21.11 with Fabric, so the code is version-compatible; mixin targets are written
with intermediary names and must be rewritten for Hafnium's Mojang-named build.

## What has to be bridged

| Anubis | Hafnium equivalent | Work |
|---|---|---|
| `ClientModule` + `ModuleRegistry` | `Module` + `ModuleManager` | Each module is adapted onto Hafnium's `Module`; no second registry. |
| `@EventHandler` events (`TickEvent`, `Render3DEvent`, `Render2DEvent`, `Packet*Event`, `BlockUpdateEvent`, `KeyEvent`, …) | `EventBus` | Add the missing events (world render, packets, block updates) with Hafnium mixins and post them on the bus. |
| Settings: Boolean, Number, Mode, Color, Key | Bool, Number, Mode, Color, Keybind | Direct mapping. |
| Settings: String, StringList, BlockColorList, ItemColorList, Action | missing | Add the setting types **and** ClickGUI editors for them. |
| 3D rendering: `WorldQuads`, `TracerEngine`, `FoglessLines`, `BlockBoxHelper`, `BlockClusterGeometry`, `ShaderOverlay`, `esp/*` | `render/DebrisOverlay` (simple lines) | Port Anubis's render layer as Hafnium's shared 3D layer. |
| HUD overlays drawn with Dear ImGui (`HudOverlayProvider`, `imgui-java` 1.90) | `HudRenderEvent` + `GuiRenderUtil` | Bundle imgui-java and Anubis's ImGui HUD layer **for overlays only**; the ClickGUI stays Hafnium's. |
| 148 mixins | 2 mixins | Port the ones each module needs, retargeted to Mojang names. |
| Automation (macros, bots, movement) | permission model (`Capability`, `Autonomy`, scheduler) | Wrap as tasks with the right capabilities, so they run where Hafnium allows automation. |

## Module map

Status: **Port** = adapt the Anubis implementation · **Replace** = Anubis replaces an existing Hafnium module ·
**Gated** = port behind the permission model · **Excluded** = server-protection bypass or detection evasion (not
ported) · **Partial** = essential part depends on excluded code (reported below).

### World & base finding

| Hafnium module | Anubis source | Status |
|---|---|---|
| Region Map | Region Map + `RegionMapRenderer`, `DonutGoliaths` | **Done** (drawn with Hafnium's renderer; Goliath picker not ported) |
| Storage ESP | Storage ESP | **Done** without its anti-xray leak path (chunk-packet and block-entity-packet reads) |
| Block ESP | Block ESP | **Done** |
| Spawner Notifier, Spawner Nametags | same | **Done** (labels drawn with Hafnium's renderer) |
| Base Finder | Sus Chunk Finder ("Classic") | **Done, partial**: "ANBS+ Scan", the amethyst count from server light (`HiddenAmethyst`, `GeodeGlowScan`, `ServerBlockLight`) and "Detect Mined Clusters" read anti-xray-hidden blocks and are not ported |
| WorldScanner | Chunk Scanner + `seedscan` | **Done** (Advanced Scanner compares visible blocks with the seed's terrain; it never shows predicted ores) |
| Waypoints | Waypoints | **Done** ("Add Here" bind takes keyboard keys, not mouse buttons) |
| Netherite Finder | Netherite Finder | **Excluded**: its only data source is `HiddenDebrisScanner` (anti-xray palette leak). Ancient Debris Finder stays. |
| Elytra Finder | none | **Done**, Hafnium-only |
| Auto Base Hunt | none suitable (Media Base Finder / Goliath RTP are DonutSMP lookups) | **Done**, Hafnium-only, autonomous task behind the permission layer |
| (not on Hafnium's list) | Better Sus Chunk, Block Notifier, Hole ESP, Light Debug | Not ported |

### Utility & HUD

| Hafnium module | Anubis source | Status |
|---|---|---|
| HUD Editor | HUD editor (ImGui) | **Done**, Hafnium's own editor and layout store (Anubis's edits its ImGui HUD, which Hafnium does not have) |
| Farm FPS Optimizer | No Render (+ Performance) | **Done**: all ten No Render switches, plus Hafnium's farm culling |
| Schematic Preview | none | **Done**, Hafnium-only (.nbt, .schem, .litematic) |
| Economy Assistant | none (Anubis's AH/order bots automate trading) | **Done**, Hafnium-only, read-only |
| InventoryCore | none suitable (Anubis's are combat helpers) | **Done**, Hafnium-only, assisted tasks |
| SpotifyHUD | Spotify HUD (lyrics, Win/macOS) | Hafnium's kept; lyrics/backends not ported yet |
| Auto Sell, Donut Wheel | — | Hafnium's own |

### Not ported: generic client modules

Hafnium only ships the modules on its own list (the tables above). Anubis's generic client modules are not
ported: entity ESP/tracers/nametags, fullbright, visual effects, combat (kill aura, aim assist, crystal/anchor
macros, triggerbot, …), movement (fly, speed, Jesus, spider, step, elytra fly, …) and QoL extras (freecam, zoom,
auto reconnect, …). Hafnium's own placeholder entries for these were removed as well.

### Excluded: server-protection bypass or detection evasion

| Anubis module / code | Why |
|---|---|
| Player Bypass (`util/playerbypass`, `playerbypass2`) | Finds players the server hides, from light data and ticking chunks |
| Amethyst Bypass (`HiddenAmethyst`, `AmethystLight`, `GeodeGlowScan`) | Reveals geodes hidden by anti-xray |
| Netherite Finder (`util/netherite/HiddenDebrisScanner`) | Reads anti-xray-hidden debris from the chunk palette |
| Block Entity Debug (`BlockEntityLeakTracker`, `LightLeakScanner`) | Renders block entities that anti-xray tries to hide |
| Storage ESP "bypass" packet path | Same leak; the loaded-chunk Storage ESP is ported |
| Fake Lag | Delays packets so the server's view of you lags (anticheat evasion) |
| Anti Vanish, Staff List, Staff Live | Detecting hidden staff / avoiding moderation |

## Progress

Updated as modules land; each entry lists what was verified in-game.

### 1. Foundation + Block ESP (done)

Ported infrastructure, now shared by every later ESP/world module:

- **Render layer:** `anubis/esp/BlockLayer` (cluster merging, outlines/boxes, reveal animation, distance fade, tracers),
  `EspStyle`, `util/BlockClusterGeometry`, `BlockBoxHelper`, `TracerEngine`, `FoglessLines`, `WorldQuads`,
  `ShaderOverlay`. Hafnium mixin `mixin/anubis/LevelRendererMixin` posts `event/WorldRenderEvent` (Anubis
  "Render3DEvent") with the frustum, and draws collected shapes at the end of the level render.
- **Scanning:** `anubis/esp/ChunkScanner` (loaded chunks, nearest first, incremental under a per-tick budget),
  `util/PerformanceBudget`, `TickWorkBudget`; `event/BlockUpdateEvent` from `ClientLevelMixin`.
- **Settings:** `anubis/setting/Setting` (a Hafnium setting carrying a JSON document, saved as a diff from the
  defaults), `BlockColorListSetting`; Hafnium settings gained `visibleWhen`, `onChange`, `getFloat`/`getInt`, `argb`,
  `is`. ClickGUI: `BlockListComponent` row + `BlockListScreen` editor (search, select, colour, tracer).
- **Modules:** Block ESP (replaces the BlockESP placeholder), Performance (stats overlay drawn with Hafnium's text
  renderer instead of ImGui; same content).

Deviations from Anubis, each verified in-game:
- Gizmos emitted from the world-render hook are not drawn by vanilla 1.21.11 in Hafnium's setup, so
  `ShaderOverlay.capture()` (Anubis: only with Iris) is always used. A gametest counts the drawn pixels.
- Hafnium marks a module enabled after `onEnable` returns (Anubis: before), fixed in Performance.
- `PerformanceBudget.beginTick()` runs from Fabric's START_CLIENT_TICK (Anubis: its own Minecraft mixin).

Verified (AnubisPortGameTest): selected blocks tracked and others ignored, touching blocks merged, broken blocks
removed, outlines/boxes/tracers visible on screen, selection survives save/load, editor search and click selection,
budgeted rescan finds newly selected blocks, disabling clears everything.

### 2. Base ESP, base finding, maps, HUD editor (done)

Ported: Storage ESP, Spawner Notifier, Spawner Nametags, No Render (as Farm FPS Optimizer), Sus Chunk Finder (as Base
Finder), Chunk Scanner + `seedscan` (as WorldScanner), Waypoints, Region Map, `ModuleChat`, `ChallengeToast`,
`WorldLabel`/`WorldText`, `StringListSetting`, `DonutGoliaths`, the spawner accessor and ten No Render mixins.
New shared pieces: `event/ChunkLoadEvent` (stands in for Anubis's chunk-packet-applied event), `hud/HudLayout` +
`LayoutElement` (every overlay is movable), `StringSetting`, `ActionSetting`, list and text editors in the ClickGUI.

Deviations from Anubis:
- ImGui text (world labels, Region Map, stats) is drawn with Hafnium's renderer: same content, layout and colours
  (Anubis's ABGR colours converted), different font.
- `ModuleChat` checks no per-module notification flag itself; ported modules that had "Notifications" gate their
  own lines.
- Anubis's settings versioning (`settingsVersion`/`migrateSettings`) is dropped (no old Hafnium configs to migrate).
- Two decompiler artefacts in `seedscan` were rebuilt from bytecode (`SeedWorldgen.emptyLevel`'s proxy handler, a
  duplicated variable name in `OreSpec`).

Verified (AnubisPortGameTest, PlannedModulesGameTest): storage types and per-type toggles, block-update add/remove,
spawner announcement text and nametag labels, farm culling counted per frame, Base Finder flags full bee nests,
WorldScanner flags a refilled shaft through a diorite blob, Region Map panel and region lookup, Waypoints Add Here and
on-screen placement, HUD Editor drag with centre snapping, scroll resize and right-click reset; Schematic Preview loads
all three formats and tracks missing/wrong/correct and rotation; Economy Assistant inventory value (with shulker
contents), sidebar balance, session profit, chat income/spending and tooltip prices; InventoryCore sort and refill
(and refusal in Manual mode); Auto Base Hunt refuses in Manual and walks the spiral in Autonomous; Elytra Finder finds
a ship, its elytra, routes it, sends it to Waypoints and marks it looted when the frame is emptied.
