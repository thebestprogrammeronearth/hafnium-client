# Hafnium Client

Free and open-source Fabric mod for Minecraft Java **1.21.11**: a purple-and-black ClickGUI, HUD and world
utilities, and a permission-aware automation framework. Licensed under the [GPL-3.0](LICENSE).

Website: <https://thebestprogrammeronearth.github.io/hafnium-client/>

> Hafnium is not affiliated with or endorsed by Mojang or Microsoft.
>
> **Server rules still apply.** Many servers ban X-ray-style tools, macros and automated selling, whatever
> client you use. Hafnium blocks autonomous actions unless you host the world or the server explicitly allows them
> (see [Permissions](#permissions)), but it cannot know or enforce each server's rules, and it cannot protect you
> from bans. Check the rules before using a module on someone else's server.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11 and put
   [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
2. Download `hafnium-client-<version>.jar` from
   [Releases](https://github.com/thebestprogrammeronearth/hafnium-client/releases) (or build it, below) and put it in
   `mods` too.
3. Start the game and press **Right Shift**.

## What works

Implemented modules:

| Panel | Module | What it does |
|---|---|---|
| Client | ClickGUI, Fonts | Appearance of the menu (accent, opacity, blur, fonts). |
| Client | Watermark | HUD logo. |
| Client | Capabilities | HUD indicator: what Hafnium may do on the current server and why. On by default. |
| Client | Server Profile | Per-server rules you confirm yourself (see Permissions). |
| Client | SpotifyHUD | Now-playing overlay from Spotify's desktop media session. Linux only (MPRIS, needs Python 3 + PyGObject). |
| World | Observation | Memory limits for the world model. |
| World | Navigation | Bounded walking: route preview, explicit start, route debug. See [docs/navigation.md](docs/navigation.md). |
| World | Ancient Debris Finder | Outlines, tracers and waypoints for ancient debris **the server has already sent to your client**. It never predicts or reveals hidden blocks; on servers with anti-xray it finds little. |
| Misc | Donut Wheel | Radial menu of your own commands and waypoints. |
| Misc | Auto Sell | Sells whitelisted items with a sell command you configure per server; manual sales always ask first. |
| Automation | Operating Mode | Manual / Assist / Autonomous. |

Setup for the last four is in [docs/priority-modules.md](docs/priority-modules.md). Every other module in the GUI is
a **placeholder**: it has sample settings but does nothing, and its tooltip says so.

## Use
- **Right Shift** opens the ClickGUI (rebindable under Controls → Hafnium Client). **Esc** closes it.
- Left-click a module to toggle it, right-click for settings. Settings-only modules (chevron instead of a dot)
  open their settings on left-click. A red dot means the module failed; hover it for the error.
- Each toggleable module has a **Bind** setting; pressing that key in game toggles the module.
- **End** = emergency stop: cancels every task and drops to MANUAL.
- Drag panel headers to move panels; click a header to fold it. Moves are kept as offsets from the default slot,
  so the layout survives resizes and GUI-scale changes for the rest of the session.
- Type anywhere (or Ctrl+F) to search. **CONFIGS** opens the config manager.

## Architecture (`dev.hafnium.client`)
- `core/` — pure Java, no Minecraft imports, unit-tested:
  - `event/EventBus` — typed, priority-ordered, thread-safe subscribe/unsubscribe, per-handler failure isolation
    (failures are reported with the owning module), `Cancellable` events.
  - `module/Module` — unique id (`category.name`), settings, keybind, `onEnable(ModuleContext)` / `onDisable()`.
    Subscriptions made through `ModuleContext` are owned by the module and removed automatically.
  - `module/ModuleManager` — registry and lifecycle. All state changes run on the client thread (other threads are
    marshalled). A throwing `onEnable` or event handler puts the module in `FAILED` with the error recorded and
    undoes its subscriptions. Enabling a module never starts autonomous work on its own.
  - `setting/` — typed settings with ids, validation and change listeners.
  - `task/TaskScheduler` — tick-driven tasks with priorities, exclusive resources (preemption of interruptible
    holders), dependencies with cancel/failure cascades, per-attempt timeouts and deadlines, retries with backoff and
    a recovery hook, invalidation, shutdown. Background work only receives `ImmutableSnapshot`s.
  - `agent/AgentStateMachine` — IDLE/PLANNING/RUNNING/PAUSED/RECOVERING/COMPLETED/FAILED with a validated,
    extensible transition table, hooks, history and events.
  - `world/` — `WorldModel`/`DimensionModel` (tracked ores, containers, crops, hazards; chunk terrain; entities;
    stale caching; configurable memory limits), `WorldSnapshot` for workers, `WorldScope` for task invalidation.
  - `permission/` — `PermissionService` (see "Permissions" below). The scheduler sees it only through
    `PermissionGuard`; revocation, profile edits and mode changes stop affected tasks immediately.
  - `control/` — operating mode (MANUAL/ASSIST/AUTONOMOUS) and emergency stop (**End** key).
  - `HafniumCore` — the composition root that wires these together.
- `HafniumClient`, `world/WorldObserver` + `BlockClassifier`, `net/EnvironmentTracker`, `mixin/` — the Minecraft
  adapter: ticks, join/leave, chunk load/unload (plus periodic reconciliation for chunks the client drops silently),
  server-confirmed block updates, visible entities, HUD render and in-game keys.
- `modules/` — `BuiltinModules` (registration), `PlaceholderModule`, `client/` (implemented modules).
- `gui/` — ClickGUI (unchanged design); reads modules only through `ModuleManager`.

## Build and test

Needs JDK 21.

```
./gradlew build               # jar in build/libs/
./gradlew test                # core unit tests (JUnit 5, no game needed)
./gradlew runClientGameTest   # in-game tests: real GLFW-level input in a fresh singleplayer world
./gradlew runClient           # dev client
```
In-game test screenshots land in `build/run/clientGameTest/screenshots/`.

The in-game tests open a real game window. On a locked or headless desktop, run them inside a virtual display, for
example `kwin_wayland --virtual --xwayland --exit-with-session ./gradlew runClientGameTest` or `xvfb-run`.

## Permissions

Three categories:

| Category | Examples | Needs |
|---|---|---|
| Client-side features | HUD, rendering, world observation, path/navigation calculations, inventory analysis, combat analytics, planning, config | Nothing. Tasks declare no capability and run everywhere. |
| Gameplay assistance (`Autonomy.ASSISTED`) | movement, look, block interaction, inventory, mob combat, started and supervised by the player | Assist or Autonomous mode, plus one of: you host the world, the server's policy allows it, or your **Server Profile** for that server confirms its rules allow assistance (off by default). |
| Server-authorized automation (`Autonomy.AUTONOMOUS`, and player combat in any mode) | agents: combat, resource collection, building, exploration | Autonomous mode, plus you host the world or the server grants it on `hafnium:automation_policy`. Normal gameplay packets are not treated as permission. |

`OPERATOR_COMMANDS` / `CREATIVE_INVENTORY` also require the vanilla privilege the server assigned (op level 2 /
creative mode). If a server sends a policy, it is a full allow-list: anything it does not list is off. Profiles
and client settings can only restrict; they never authorize automation.

- **Operating Mode** (Automation panel): Manual / Assist / Autonomous. Every join and every emergency stop resets it to Manual.
- **Server Profile** (Client panel): per-server rules (stored in `config/hafnium/server-profiles.json`); outside multiplayer it edits the defaults for new servers.
- **Capabilities** (Client panel, on by default): HUD indicator showing Auto / Assist / Off per capability.
- Joining, grants, revocations and refusals are explained in chat (local only).

"Allowed" only means Hafnium will not block it. It says nothing about whether a server's rules or anticheat accept
it, and Hafnium cannot prevent penalties.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew test runClientGameTest` before opening a pull request,
and keep new features within the permission model above: no anticheat bypasses, detection evasion or features
that hide automation from servers.

## License

Hafnium Client is free software under the [GNU General Public License v3.0](LICENSE) (GPL-3.0-only). You may use,
change and share it; if you distribute a modified version, you must publish its source under the same license.

The Inter font is © The Inter Project Authors, under the SIL Open Font License 1.1
(`src/main/resources/assets/hafnium/inter-license.txt`).
