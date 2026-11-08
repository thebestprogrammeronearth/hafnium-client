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

### World & base finding (roadmap priority)

| Hafnium roadmap | Anubis module(s) | Status |
|---|---|---|
| Region Map | Region Map (+ `RegionMapRenderer`, `DonutGoliathMapData`, Goliath picker) | Port |
| Base ESP | Storage ESP, Block ESP, Spawner Notifier, Spawner Nametags, Hole ESP | Port (Storage ESP without its anti-xray leak path) |
| Base Finder | Sus Chunk Finder, Better Sus Chunk, Chunk Scanner, Light Debug | Port / **Partial**: Sus Chunk Finder's amethyst signal uses `HiddenAmethyst` (anti-xray bypass); Chunk Scanner's advanced mode uses `seedscan` seed prediction (allowed, labelled as prediction) |
| WorldScanner | Chunk Scanner, Block Notifier, Xray (received blocks only) | Port |
| Waypoints | Waypoints | Port |
| Netherite Finder | Netherite Finder | **Excluded**: its only data source is `HiddenDebrisScanner`, which reads debris that anti-xray hid from the chunk palette. Hafnium's Ancient Debris Finder stays. |
| Elytra Finder | none | Hafnium-only (to build) |
| Auto Base Hunt | Media Base Finder (DonutSMP goliath lookup), Goliath RTP | Gated |

### Utility & HUD

| Hafnium | Anubis | Status |
|---|---|---|
| SpotifyHUD | Spotify HUD (synced lyrics via lrclib, Linux/Windows/macOS backends, media keys) | **Replace** after verification |
| HUD Editor | HUD (+ `HudEditorScreen`, `HudLayoutStore`, `HudPanel`) | Port |
| Farm FPS Optimizer | Performance, No Render | Port |
| Radar, Hotkeys, Discord Presence | same | Port |
| QoL | Auto Reconnect, Weather Notifier, Chat Macro, Coord Snapper, Name Protect, No Inv Leak, Anti Ghost Block, Skin Change, Slow Swing, Freecam, Freelook, Zoom | Port |
| InventoryCore | Auto Eat, Legit Auto Tool, Auto Totem, Auto Inventory Totem, Hover Totem, Elytra Swap | Gated (assisted inventory) |
| Auto Sell, Economy Assistant | Auto Sell (/sell GUI), AH Sell, AH Sniper, Order AH Trader, Order Dropper, Panic Pay, Tp Macro | Gated (autonomous inventory/chat); Hafnium's command-template Auto Sell kept for non-Donut servers |
| Donut Wheel | none | Hafnium-only |

### Render & visuals

ESP, Glow ESP, Tracers, Nametags, Item ESP, Fullbright, Time Changer, Custom FOV, Custom Crosshair, Pearl
Trajectory, Trajectories, Low Health Warning, Xray, Block Overlay, Color Grading, Custom Glint, Hand Pose,
Swing Animation, Motion Blur, Atmosphere, Jump Circles, Hit Particles, Particles, Totem Spirit, Target ESP, Hand
Aura, Floating Relics, 3D Hand, 3D Character, CS Knife → **Port** (client-side rendering).

### Combat & movement

Aim Assist, Kill Aura, Triggerbot, Crystal Aura/Macro/Optimizer, Anchor Aura/Macro, Auto City, Auto Trap, Mace,
Shield Breaker, Spear Swap, W Tap, Jump Reset, Key Throw, FastXP, Friend Guard, Crystal/Sword PvP Bot, Tpahere
Kill Bot → **Gated** (`COMBAT_PLAYERS` is authorized-only). Fly, Speed, Spider, Jesus, Boat Fly, Boat Noclip, Click
TP, Entity Speed/Control, Elytra Fly, Air Stuck, Donut NoFall, No Jump Delay, Trident Boost/Util, Auto Walk/Jump,
Sneak, Sprint, Knockback Delay, Anti Trap, Auto Relog, Auto Clicker, Fast Use, Spam → **Gated**.

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
