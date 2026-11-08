# Navigation implementation

Navigation uses a dedicated immutable `CollisionSnapshot`; it does not change the
existing `WorldSnapshot` or `WorldModel`. `CollisionCapture` runs on the client
thread, copies the player's collision-context voxel shapes into detached boxes,
and excludes unloaded chunks and cells outside build height or the world border.
Captures are limited to 64 cells per axis and 65,536 cells total. Missing cells are
unknown, never inferred to be air. Fluids and known hazardous blocks are excluded.

`PathPlanner` performs bounded A* on workers via the existing `TaskContext.async`.
It checks swept player volumes, not only destination occupancy. Walking supports
flat surfaces and steps up/down up to 0.6 blocks. Slab and fence geometry retain
fractional heights. Stair geometry is preserved, but the conservative cell-center
graph may reject traversable stairs or narrow partial-block surfaces. Swimming,
jumping, ladders, doors, parkour, flight and block modification are not supported.
No route is reported when this walking graph cannot connect the endpoints.

`NavigationCore` is owned by `HafniumCore`. Its planning-only API needs no gameplay
permission. It caches up to sixteen routes for identical immutable snapshots and
requests. Server-confirmed block updates, chunk changes, dimension changes and
disconnect invalidate its generation. Invalidation is currently conservative and
global, so changes outside a route can also trigger recalculation. Silent chunk
unloads are checked by the movement driver as well as the existing observer.
World-border changes are checked by the movement driver before execution.

`NavigationTask` is separate execution: it declares MOVEMENT and LOOK capabilities
and scheduler locks. It releases input on suspension, cancellation, completion,
failure and permission revocation. It replans after invalidation, resumption or
60 ticks without a cell/height change, with an eight-plan limit and diagnostics.
Its planner cancellation token is explicit because CompletableFuture cancellation
alone does not guarantee worker interruption. Routes remain spatially bounded;
long trips require a future higher-level route coordinator.

In ClickGUI > World > Navigation, set target offsets and bind Preview key and
Start key. Enable the module, close the GUI, then explicitly press the chosen key.
Preview is client-local. Start uses ASSISTED authorization and requires Assist or
Autonomous mode plus permission. Enabling never starts navigation. End cancels via
the existing emergency stop. Route debug shows a small top-down waypoint view.
Disabling the module cancels its active request. Module settings retain the
project's current in-memory configuration behavior.

InventoryCore and the advanced agents have not been implemented by this change.
