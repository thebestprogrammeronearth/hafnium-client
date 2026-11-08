# Priority modules

The prior InventoryCore/agent roadmap is paused. These modules extend the existing
ClickGUI; enabling a module does not start autonomous gameplay. Bind each module's
named action keys in its expanded settings. Settings persist in
`config/hafnium/module-settings.json`; modules are not automatically enabled on
startup. Named configs in the older CONFIGS screen still use the existing
in-memory module-state snapshots.

## Ancient Debris Finder — World

Only reads ancient debris recorded by WorldModel from received chunk/block data.
There are no generated or predicted locations. Discovery queries run twice per
second, draw at most 256 nearby markers, and retain at most 10,000 discoveries.
Configure distance, outline width/color, tracers, distance indicators, stale
visibility, notifications, and the nearest-waypoint toggle key. Distances and
coordinates appear in a compact HUD list; 3D outlines and tracers use Minecraft's
depth-tested line renderer. Opaque terrain can occlude these lines.

Discoveries are partitioned by server/world and dimension and stored in
`debris.json`. Loaded observations are distinguished from stale saved locations.
A complete observed chunk can disprove a saved location; incomplete/truncated
world-model data does not. Reloaded discoveries start stale. A star marks a saved
waypoint; pressing the waypoint key toggles the nearest visible discovery.

## Donut Wheel — Misc

Bind **Open wheel**. Click an action to execute it; right-click to edit; drag one
occupied slot onto another to reorder; Shift-right-click deletes. Empty slots
open the editor. Eight slots per page, up to 64 actions. Favorites filters the
same stored actions. The editor uses JSON and supports clipboard Import/Export.
Custom icons are text/Unicode glyphs.

Example command action (change the command to match your server):

```json
{"label":"Home","icon":"⌂","command":"home {args}","confirm":true,"favorite":true}
```

`{args}` prompts for arguments. `confirm` adds an explicit command confirmation.
A waypoint action uses an empty command and coordinates:

```json
{"label":"Portal","icon":"★","command":"","confirm":false,"favorite":true,"dimension":"minecraft:the_nether","x":100,"y":64,"z":-200}
```

Selecting a waypoint activates its dimension-aware coordinate/distance HUD.
Wheel actions persist in `wheel.json`. No commands run merely by opening the wheel.

## Auto Sell — Misc

Bind **Configure server**, **Preview manual sale**, **Start or stop automation**,
and **Transaction history**. Profiles are stored per server in `sales.json`.
There is no default sell command: configure the actual server's item-and-quantity
syntax. The template must include both `{item}` and `{quantity}`. Example schema
(the command below is illustrative, not asserted to work on any particular server):

```json
{"command":"market sell {item} {quantity}","whitelist":["minecraft:cobblestone"],"blacklist":[],"prices":{"minecraft:cobblestone":0.5},"quantity":64}
```

Blacklist wins over whitelist. Every manual sale shows quantity, command, optional
estimated value, and confirmation. Damageable, enchanted, named, custom-data, and
container-bearing stacks are protected. If any stack of an eligible item ID is
protected, automation skips that entire ID because a server command might select
any matching stack. Manual protected-item confirmation is explicit, and the
inventory is checked again before sending.

Automation uses the existing scheduler's INVENTORY/CHAT locks and INVENTORY
capability in Autonomous mode. Hosting a world or an explicit server grant is
required; a client-side profile cannot authorize autonomous selling. Disconnect,
permission revocation, module disable, emergency stop, and profile edits stop the
task. Restart is manual. No detected inventory decrease within 40 ticks stops the
workflow rather than repeatedly submitting an ineffective command.

History records command submissions and observed inventory decreases, **not
confirmed sales or payments**. Other inventory activity could cause a decrease.
Prices are user-supplied estimates. The configured server command must actually
honor item IDs and quantities; this client cannot rewrite arbitrary server economy
semantics. Automated and manual sales should not be mixed while one is pending.

## SpotifyHUD — Client

Uses Spotify's Linux MPRIS session via `org.mpris.MediaPlayer2.spotify`, with Python
3 and PyGObject/Gio installed. No OAuth secrets or tokens are stored. Other operating
systems report that no backend is configured. Spotify must expose its desktop
media session on the same user's D-Bus session.

Shows track/artist, bounded album artwork, progress/timestamps and playing/paused
state. Configure scale, opacity, compact layout, artwork/progress visibility,
accent, auto-hide, and optional play/pause key. Bind **Drag HUD** to open its drag
editor, then Escape to return. Playback controls are used only when the session
advertises support.

Media queries and artwork fetching/decoding run on scheduler workers. Requests
have timeouts; artwork downloads are capped and limited to supported Spotify CDN
hosts. The render thread only uploads a 96×96 prepared image. Missing data,
unsupported control, absent Python/Gio, or a missing media session produce an
unavailable state; auto-hide can hide that state. No fake track is shown.

MPRIS reference: https://specifications.freedesktop.org/mpris/latest/Player_Interface.html

Tests distinguish the isolated MPRIS fixture from real Spotify playback. The
fixture tests metadata, timing units, pause controls and session disappearance;
it does not prove account-specific behavior in a running Spotify installation.
