# Ultimates (the class abilities)

Every class ability is an **Ultimate**: 60 of them, three per power (Offense, Defense, Support). The player doc is
[docs/classes.md](../classes.md). They replaced the old class casts (`StyleCasts`, the `StyleRuntime` wards and zones,
`ElementArt`/`MotionArt`/`MindArt`) in November 2026; the node and action IDs were kept, only names and descriptions
changed.

## How one is built

- **The engine is `combat/Ultimates`.** A class node's `mechanic` routes through `UltimateTable.build()` to an
  `Ultimate(kind, factory, reach)`. `StyleSkills` asks `Ultimates.canUse` and calls `Ultimates.cast`. A run is a
  `Ultimates.Run` (a `Signatures.Run` under the key `"ultimate"`), so it ticks on the server tick with its
  `ServerPlayer` kept and is cancelled on death, logout, a dimension change or dampening by the same code the
  signatures use.
- **One class per power holds all three** (`<Power>Ultimates`: `SpeedUltimates`, `PyroUltimates`, ...) next to the
  power's other code. Each run keeps its beats as public constants (`GRAB`, `IMPACT`, `TICKS`) and its geometry as
  static methods (`AroundTheWorld.height(t)`, `GrandDesign.part(i)`), which the power's own art class reads. The server
  and the drawing share their numbers.
- **Its art is in the power's own art class** (`<Power>Art`, cases in `PieceArt`), in that power's palette, on pieces
  named `<POWER>_ULT_*`. A piece longer than a second is re-sent once a second with the second in its seed (`-1`, or a
  top byte of `0xFF` for earth pieces, marks the end burst), and the tail fades with
  `Math.clamp((ticks - t) / 2 + .3, 0, 1)`, so a late joiner or a dropped packet catches up. Shared shapes are in
  `client/effects/setpiece/UltArt`.
- **Offense is a cinematic on a held foe.** `Ultimates.pick` takes the foe under the crosshair within the Ultimate's
  reach (else the nearest in a 40-degree cone, in clear sight); with none, the cast is refused and costs nothing. The
  foe is held: AI off, the `heroesevolved_ultimate_held` mark set (a mob loaded with the mark and no run holding it gets
  its mind back in `Ultimates.joined`), placed each tick with `place`/`lift`/`slam`, and let go with `release`. The
  caster is immune to the held foe and takes half from everyone else while it plays. Move the caster with
  `glide`/`still`/`hover`/`blink` (swept through `SpeedService`, so never into a wall) and hide bodies with
  `hide`/`hideFoe` (`BodyCopies`' `ULT_HIDE`).
- **The camera is keyframed.** `cam().key(tick, f, s, u, lookF, lookS, lookU)` in the run's frame (forward, right, up
  from where it began), `watch`/`follow` to track a body, `until(tick)` to hand back. `cinema(cam)` sends one
  `UltimateCameraPayload`; `client/UltimateCamera` eases between keys, clips to walls, draws the letterbox bars and
  holds the player's input; `TempestCamera` asks it after a finisher and before a tornado. Defense and Support open
  with `heroShot(ticks, height)` and then play live. The client setting `ultimateCamera` turns it off.
- **Defense and Support act through hooks:** `incoming` (change or cancel a blow on anyone; check the victim is yours),
  `lethal` (the damage that should land instead of a killing blow), and `Deflecting` for shots that never fly as an
  entity: TaCZ rounds ask `Ultimates.deflect(body, bullet)` in `TaczLink`, and the run's `turn(shot)` answers it.
- **Never place blocks.** Rock, ice, walls and cages are drawn; the server only hurts, moves and protects.
- **Sound:** a press of a class node makes no cue (`PowerSounds.press` returns null for a style node); the run's
  `cue()` plays the power's `HEAVY` as it opens (`cue(true)` for a stealthy one, Not Here).

## Numbers

- **Price by role, in `tools/balance.py`'s `ULTIMATES`:** Offense 58 stamina and 150 s, Defense 50 and 120 s, Support
  52 and 135 s. `normalize_skills` writes them to every class node (design and balanced values the same).
- **The damage review counts the wait.** Each second of an Ultimate's cooldown is worth `ULTIMATE_PREMIUM` (0.2)
  stamina of effect, and an Offense Ultimate's hold is its control (its length in seconds). That puts the held foe's
  damage at about 20 to 25, with a 4 to 9 splash on anyone near the finish (counted as two bystanders at 40%). Six were
  raised to reach it (Grand Design, Glacier Fall, Stratosphere, Everything at Once, Mindbreak, Ten Past Ten). Defense
  and Support are utility reviews; their retaliation and end bursts are conditional riders.

## Checking one

- **Game tests:** `skills/ClassAbilityGameTests` (every power has Offense, Defense and Support Ultimates, each waits
  at least two minutes, one class at a time, the perks) and `combat/UltimateGameTests`, a `@GameTestGenerator` with one
  batch per Ultimate (their blasts reach the arena next door). Offense: takes the foe in front, holds it, lands 10 or
  more, gives the mind back, leaves the caster out of walls and near where they began. Defense: softens a 6-point blow,
  or survives a killing blow (`LETHAL`), or stops a shot (`SHOTS`), or loses a hunter (`LOST`). Support: leaves a hurt
  caster better off. Each is then stopped early with `Ultimates.stop` and checked clean.
- **Photographs:** `./gradlew runClient --init-script tools/ultimate-preview.init.gradle` (`-Ponly=sunfall,phase_blur`
  or a power's id) stages each one with a pillager, a vindicator and a tamed wolf, photographs it through its own
  camera and from the side, and prints `ULTIMATE PASS/FAIL` lines into `build/ultimate-preview/`.
- **Admin:** `/heroesevolved ultimate cast <player> <mechanic>`, `stop <player>` and `list` (`AdminCommands`), and the
  Ultimates rows of the admin console's events page.

## Reusable patterns

- **The first lap ring read as a thin white line.** Around the World's lap is drawn 58 blocks out; at that distance a
  ribbon needs to be wide and lit along its whole length, and the night tint must be strong enough to make it read.
  Draw far shapes at the size they will be seen at, and photograph them from the Ultimate's own camera.
- **A helper named like a base-class method calls the wrong one.** `KineticUltimates.held()` hid `Run.held()`; it is
  `caught()` now. Check `Run`'s helper names before naming your own.
- **A piece enum entry is an ordinal on the wire.** Adding or removing `Piece` entries changes the protocol: bump it in
  `ModPayloads`.
