# Induced Radioactivity: notes for agents

Fission Leap, Critical Mass, the blasts and the eighteen-node tree (`radiation/`, art in `RadiationArt`). The meltdown field and suits are in [hazards-and-suits.md](../hazards-and-suits.md). What the player sees: [induced-radioactivity.md](../../induced-radioactivity.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Induced Radioactivity rework

- **One hand, read by both sides.** `RadiationGeometry` (`hand`, `offHand`, `cupped`, `core`) is where light leaves the body; the server
  (`RadiationService.hand`) and `RadiationArt` both read it, so the heat leaves the hand you see, first person
  included. The power has its own poses (`ArmPose.RAD_CUP`, `RAD_PALMS`, `RAD_FLARE`, `RAD_GRIP`, `RAD_RAISE`,
  `RAD_POINT`, drawn in `client/RadiationPoses`, dispatched from `PowerArmPoses` and `FirstPersonArmGeometry`) instead
  of borrowing Pyrokinesis's and Electrokinesis's.
- **Old effect kinds forward to the new pieces.** Gifted mobs, villagers, the facility and the story still send
  `RADIATION_*` and `NUCLEAR_*` kinds; `client/effects/RadiationEffects` and `NuclearEffects` start the matching
  `RAD_*` piece on the client (`RAD_HEAT`, `RAD_BEAM`, `RAD_PULSE`, `RAD_ZONE`, `RAD_DOSE`, `RAD_CHAIN`, `RAD_EMP`,
  `RAD_LEAP`, `RAD_FALLOUT`), so nothing radioactive draws particles any more. Copy this (it is `TelekinesisEffects`'
  pattern) whenever a power's art is redrawn: never leave the old kinds drawing the old way.

The radiation power (`radiation/`, art in `client/effects/setpiece/RadiationArt`) was reworked in November 2026
to be more fun: a mobility move, punchier hits, faster blasts. What the player sees is in
[docs/induced-radioactivity.md](../../../docs/induced-radioactivity.md).

- **A press-again follow-up that is free.** Fission Leap launches on the first press and slams on the second.
  The second press uses `SimpleAction.builder(...).followUp(...)`, which the use service checks *before*
  cooldown and stamina, so the slam costs nothing and isn't blocked by the launch's cooldown. Use this for any
  two-stage move: a dash then a strike, a throw then a detonation.
- **Short server-driven hops use the vanilla motion packet.** Under 3.9 blocks a tick (the leap is 1.2 up,
  the slam 2.6), `setDeltaMovement` plus `ClientboundSetEntityMotionPacket` is enough. Re-send the slam
  velocity every tick so drag doesn't slow it. Over 3.9, see Skyburst's payload in [flight.md](flight.md).
- **Land detection needs a fallback.** The slam lands on `onGround()`, `verticalCollision`, water or lava,
  *or* when its time runs out (`SLAM_MAX`). Without the timeout, a test player (or a player whose packets
  lag) would hang in the slam forever.
- **Cushion falls for a window, not an instant.** `RadiationLeap.cushioned` stays true for ten ticks after
  landing, and a `LivingFallEvent` hook zeroes the fall. The server can process the landing packet a tick
  after it ticks the move.
- **One blast helper for every size.** `RadiationLeap.blast(player, at, radius, damage, stages, throwSpeed)`
  hurts, doses and throws everything in range, harder near the middle, with knockback resistance respected.
  The launch, slam and other blasts all call it with different numbers. The art does the same:
  `RadiationArt.blast(...)` draws the launch and the slam as one shape at two sizes.
- **Keep action IDs; change names.** Fission Leap kept the old `radiation_pulse` ID, and Critical Mass kept
  `radiation_exploding_man`, so saved loadouts, `power_balance.json`, config keys and `ArmPoseActions` all
  still match. Only `en_us.json` names changed.
- **Long charges must be stoppable.** Critical Mass charges for three seconds on purpose: other players need
  time to stop it. Shift cancels it, and damage taken during a voluntary charge adds up until it breaks
  (`RadiationService.CHARGE_BREAK_DAMAGE`). An involuntary meltdown can't be stopped. Give any big ultimate a
  wind-up that opponents can see and answer.
- **Fast blast, slow cloud.** The explosion's flash, fireball and shock now arrive within a few ticks
  (`MiniBlast`: fireball time constant 3, dust and condensation 2.2), while the mushroom cloud still rises and
  rolls over at its own pace. Impact should be instant; the aftermath can linger.
- **Centre ground effects on the ground.** A caster who rose while charging still blows a crater at their
  feet's ground: `NuclearFallout.groundUnder(owner)` raycasts down a few blocks.
- **Replace the old effect when you add a new one.** The first EMP rework drew its new set piece *and* the
  old `AbilityEffects` rings, which showed up as thick grey tubes. When a set piece takes over a visual,
  remove the old `AbilityEffects.send` for it.
- **A held effect re-sent as short pieces.** The Meltdown Beam sends a four-tick `RAD_BEAM` piece every two
  ticks; each copy draws at about half strength so the two overlapping copies add up to one. Contaminated
  ground does the same with `RAD_ZONE` (twelve ticks, re-sent every ten, fading in and out over two).
- **A skill tree of interactions, not stats.** Its eighteen nodes (`radiation/RadiationSkills`) each change a move
  or tie two together: Chain Vent bursts the sick, Fallout Boots and Catastrophe turn the leap's ground into a
  combo, Breeder Reactor trades a meltdown for Overdrive. To do this for another power:
  - add the power to the no-stat-bonus list in `SkillTreeService.bonus`, or the positional nodes (`_0_0`,
    `_0_2`, …) keep granting generic cost, damage and cooldown bonuses on top of their new meaning;
  - route the three implicit branch actives (`SkillActionService.canUse/activate`) and the three named
    techniques (`AdvancedTechniques` or `ExpansionSkills`) to the power's own class;
  - keep the node IDs so purchases and prerequisites carry over, and check `name`/`description` against
    `en_us.json` with `python tools/verify-skill-content.py`.
