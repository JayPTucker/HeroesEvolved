# Awakenings and danger: notes for agents

How a power wakes in danger and saves the person in it (`intro/Danger`, `intro/SpeedRescue`, `intro/FlightRescue`, `intro/WardRelease`, `intro/PerilRelease`). A new power must add itself to `Danger` and `PerilRelease`. What the player sees: [power-intros.md](../power-intros.md), [eclipse.md](../eclipse.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from the rescue awakenings

Natural awakenings answer danger to the owner *or a nearby ally*, and every release saves that person, the
**ward** (`intro/Danger`, `intro/SpeedRescue`, `intro/FlightRescue`, `intro/WardRelease`). What the player sees is in
[docs/power-intros.md](../../docs/power-intros.md) and [docs/eclipse.md](../../docs/eclipse.md).

- **Who counts as an ally, and who is a threat.** `Danger.ally(owner, other)` (friendly players and own pets, via
  `StyleOps.isFriend`) and `Danger.threats(owner, ward, radius)` (monsters near the ward, anything targeting either,
  and whoever last hurt either). Use these for anything else that protects someone.
- **"Outside" is a heightmap check.** `Danger.openSky(entity)` compares the eye block with
  `MOTION_BLOCKING_NO_LEAVES`, so tree cover still counts as outside. Sky light would fail under leaves.
- **Damage to anyone can wake someone else's power.** `AwakeningService.interceptAllyDamage` runs on every
  `LivingDamageEvent.Pre` and caps the blow; `recordCombat` now tracks every living thing, pruned past 2048 entries.
- **Hand the ward through the unlock.** `awakenFromAdrenaline` puts the ward in `PENDING_WARD` around
  `awakenAssigned`, and `PowerIntroService.acquired` reads it with `pendingWard`, so no ability's `onAwaken` changed.
- **A release that moves people is a `Rescue`.** The session ticks it and waits for it to finish, and `cancel` calls
  `stop()` so a carry is always set down. Shield the ward with `shieldWard` while it runs.
- **Routes through caves are a bounded breadth-first search** over cells the body fits in and can push off from (a
  floor or a wall), cached per run (`SpeedRescue.search`). It gives up at 16,000 cells and falls back to the best
  cell it reached. Walk the route by teleporting a few blocks a tick; the client can't follow a twisting tunnel by
  velocity alone.
- **Powers are blocked during an intro.** `PowerRules.canAct` is false while `PowerIntroService.isActive`, so
  `FlightDynamicsService` forgets any burst. Intro movement sends `SkyburstMotionPayload` itself instead.
- **Each power wakes for the dangers it can answer.** `Danger.Peril` (FIGHT, BLAST, FALL, FIRE, DROWNING, LOW) and
  `Danger.answers(power, peril)` are the one table: every power answers a fight and a blast, only some a fall, lava or
  water, and only Space-Time "under two hearts for any reason". `Danger.of(source, victim)` classifies a blow for the
  damage intercepts; `Danger.hazard`, `fallDamage` (the vanilla landing worked out ahead) and `blastDamage` (vanilla's
  explosion formula with cover, armor and difficulty) find the danger before it lands. A new power adds itself to the
  sets in `Danger` and a case in `intro/PerilRelease`.
- **Hold the danger while the power charges.** `Danger.hold` runs every charge tick of a danger awakening: threats
  crawl, creeper fuses are capped short of going off (`mixin/CreeperSwellAccess`), TNT stops counting, shots slow and a
  fall hangs in mid-air. Without it a three-second charge lets the creeper go off first. Any power-up that takes time to
  arrive (a summon, a long cast) can borrow it.
- **A power can fight on its own.** `intro/Surge` drives a player's fighting style (`Style.strike`) or kit actions
  (`AbilityAction.activate` with a level-5 context) for a few seconds, facing each target in turn. Use it for anything
  that should act through a player (a berserk state, a possession).
- **Turning a player without moving them.** `connection.teleport(x, y, z, yaw, pitch, Set.of(X, Y, Z))` still takes
  the player's *absolute* position: the server moves them to `x, y, z` and only the packet is relative. Passing zeros
  (as the first surge did) teleports the player to the world's origin. Pass `getX(), getY(), getZ()`.
- **A synced state must be cleared after the change, not before.** `HydroService.forget` runs inside `onRevoked`,
  while the power is still theirs, so its sync still carried the water and the octopus arms stayed drawn on the next
  power. `HydroService.powerChanged` runs from `PlayerAbilityService.saveData` and `MimicryService.sync`, after the
  change; the client drops a bender whose state comes back empty.
- **Watching awakenings live:** `tools/awakening-preview.init.gradle` (`uitest/AwakeningPreview`, flag
  `heroes.preview.awakening`) plays every power's awakening and every danger in one run and closes itself.
  `-Ponly=cryokinesis,quake` keeps some; `-Psound` keeps the sound on (every other preview is silent).
- **Hold back one system's start for another.** Space-Time's danger awakening stops time (`intro/TimeAwakening`)
  before the vision: `TimeAwakening.arm` runs before the unlock so `PowerIntroService.acquired` and the ability's
  `onAwaken` see `claims(player)` and wait; `end` starts the vision. A logout during the stop leaves the vision to
  `TimeIntroService.login`, which already re-runs an unseen intro.
