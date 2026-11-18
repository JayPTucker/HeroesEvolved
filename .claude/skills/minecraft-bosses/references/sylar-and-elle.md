# Sylar and Elle: the two built bosses

Lessons from the Watchmaker (`story/sylar/`) and Primatech (`story/elle/`) fights. What the player sees: [sylar.md](../../../../docs/sylar.md), [elle-bishop.md](../../../../docs/elle-bishop.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../../AGENTS.md).

## Reusable patterns from the Watchmaker (Sylar's boss fight)

Sylar's fight became a boss fight in his own arena in November 2026 (`story/sylar/WatchmakerArena`, `WatchmakerMoves`,
art in `client/effects/setpiece/WatchmakerArt`). What the player sees is in [docs/sylar.md](../../../../docs/sylar.md). Use these
for the next boss.

- **An arena is a pocket dimension rebuilt every fight.** `WatchmakerArena.enter` builds the `sylar_shop` blueprint
  in `heroesevolved:watchmaker_shop`, saves each player's way home, moves them in, and spawns a new boss there (a mob
  changing dimension becomes a new entity, so make a fresh one and update whoever holds the old reference).
  `close(server, delay)` sends everyone home. Whatever the fight broke (ripped floor, shattered cases, barred doors)
  is whole again next time.
- **Moving players in and out must not end the encounter.** `PlayerChangedDimensionEvent` normally ends a Sylar
  encounter; `WatchmakerArena.moving(player)` marks the fight's own teleports so the handler skips them.
- **Keep an arena's chunks force-loaded once built.** A freshly loaded chunk in a pocket dimension takes about three
  seconds before its entities tick, so a boss spawned there stands frozen. The shop's chunks stay forced from the first
  build on (the game saves that).
- **The game-test world lists its dimensions by hand.** `src/gametest/resources/data/minecraft/worldgen/world_preset/flat.json`
  defines every dimension the test server has; add a new one there or `server.getLevel(...)` returns null in tests.
- **Phases from health thresholds, each changing the room.** `SylarEntity.PHASE_TWO`/`PHASE_THREE`/`CHASE_AT`. A phase
  changes the arena, not just numbers: the doors bar, the clocks speed up, and at a tenth he runs down an alley.
- **Every telegraph is the same shape.** A dial whose hand reaches twelve when the move lands; each move's windup is
  its marker piece's `ticks`. Store anything a move needs later (a ring's centre) in its own field: his next move's
  `mark()` overwrote the rings' centre once and dragged them across the room.
- **No fists during a windup.** `tickCast` clears the melee target, or `MeleeAttackGoal` keeps punching while the
  telegraph runs.
- **A counterplay window.** He is `exposed()` while winding up or holding someone. A charged hit (a full attack meter,
  read in `AttackEntityEvent` before the swing resets it; a crit arrow; or 8+ damage) then cracks his watch and
  staggers him. Read the swing record on every hit, or one swing carries over to the next.
- **Taking a power for a fight.** `WatchmakerArena.stolen(player)` is checked in `PowerRules.canActWithoutPreservation`;
  `close` clears it, so the power always comes back.
- **Walls rise after the shove.** `TemporaryBlocks.place` skips cells with an entity in them, so the ice wall rises
  two ticks after it pushes people off its line (`SylarEntity.raiseWall`), or it has a hole where the target stood.

## Reusable patterns from Elle Bishop (the Company's boss)

Elle's fight (`story/elle/`, art in `client/effects/setpiece/ElleArt`) added foreshadowing before a boss and an arena
whose machines are the counterplay. What the player sees is in [docs/elle-bishop.md](../../../../docs/elle-bishop.md).

- **Foreshadowing that puts everything back.** `CompanyOmens` changes only block states that it can restore (lamp and
  bulb `LIT`, furnace `LIT`, door and trapdoor `OPEN`) and queues each restoration as a timed step. A restoration runs
  only if the block is still the same kind of block, and `settle()` restores everything on server stop.
- **Never probe with `WorldMutationPolicy.canEdit` for something that breaks nothing.** It posts a `BreakEvent`, which
  records temporal echoes and mirrors breaks into snapshots. The omens use a side-effect-free check
  (`mayBuild`, world border, `mayInteract`) and call `GridDisruption.stall` with a null source.
- **Grid helpers for story events.** `GridDisruption.jam(level, center, radius, ticks)` is a redstone-only field:
  nothing is drained and nothing stalls. A misfire uses it for a second, and the arrival uses it to put out every light
  around the player. `stall(null, ...)` stops a machine with no actor.
- **Poll the arena's switches; don't listen for clicks.** Each tick, `PrimatechArena.pollBanks` reads every breaker
  lever and `GridDisruption.jammed`. So a lever pulled by hand, a broken lever, or a player's Blackout or EMP over a
  bank all count the same. A boss reads the result (a bit mask); it never handles the input itself.
- **One geometry class, checked against the blueprint.** `PrimatechGeometry` holds bank, conduit and mat positions for
  the fight and the drawing. The blueprint is drafted to the same numbers, and a game test compares its `@breaker:N`,
  `@conduit:N` and `@mat` markers with the class.
- **Send state for drawing through synced entity data, not `scale`.** The client clamps a set piece's `scale` to 64.
  The arena piece is bound to Elle and reads her synced `BANKS` mask and phase instead.
- **Cut a travelling piece short with `superseded`.** Ball lightning's `ELLE_ORB` stops drawing when an `ELLE_RING`
  bound to the same entity arrives (the burst), so a ball that hits someone early doesn't roll on.
- **A scene stand-in.** The arrival spawns an `ElleEntity` with `scene(true)` and no AI: no bar, no fight, invulnerable.
  It is discarded when the player is taken.
- **Freshly forced pocket chunks hide their entities.** For a few seconds after the arena is first built, a player
  standing in a chunk that isn't entity-ticking yet is invisible to `getEntitiesOfClass`. Hazards miss them, and so
  do tests. Tests wait for every corner of the room (`ElleGameTests.ready`), not just the boss's chunk.
- **A move's end mustn't shorten a longer hold-off.** `finish()` uses `Math.max` on the global cooldown, so a
  preview's or test's `holdOff` survives the moves it triggers.

## Where they stand against the skill's defaults (November 2026)

Both fights were built before the defaults in [SKILL.md](../SKILL.md). Bringing them up to it is open work; this is the
list.

| Default | Sylar (the Watchmaker) | Elle (Primatech) |
| --- | --- | --- |
| In the `c:bosses` tag | no (carrying and Telepathy name him by class) | no (only `unfinishable` names her), so Cryokinesis can freeze her solid and Density can embed her |
| Per-hit and per-second caps | none | none (her bank shield takes 10% off per live bank instead) |
| Range falloff | none | none |
| Idle healing and leash | Regeneration heals him if he knows it; outside the shop he hunts | the arena closes after ten minutes |
| Party scaling | health grows with powers he has taken (80 + 12 each, up to 240), not with the party | a fixed 200 |
| Death scene | none: he falls and the shop closes | none: a line, and everyone is sent home |
| Theme music | none | none |
| A drop that opens the next thing | Sylar's Watch (a tool, not a key) | Elle's Capacitor (a tool, not a key) |
| Re-fight item | none: the story ladder brings more hunts | none: the Company returns every seven days |
| Finishers at phase thresholds | planned | planned |
