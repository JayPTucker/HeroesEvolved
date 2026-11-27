# Telekinesis: notes for agents

The far hand (`telekinesis/`, art in `client/effects/setpiece/KineticArt`, numbers and places in
`telekinesis/KineticGeometry`, poses in `client/KineticPoses`): a hand of bent air that does at a distance what the
caster's hand does. Reworked 26 November 2026 from a particle-era kit (pale `PowerParticle` arcs, vanilla block-display
fragments) that also overlapped Matter Assembly (an orbit of fragments and volleys), Invisibility (force bubbles and
shells) and Electrokinesis (a second Remote Hand). What the player sees: [telekinesis.md](../../telekinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## The idea, and what it may not do

- **Only real things move.** Creatures, items, shots, primed TNT, carts, boats, falling blocks. Telekinesis never conjures
  matter (that is Matter Assembly), never raises a wall or shell (Invisibility) and never tears up the ground (Density).
  A new move that needs "ammunition" takes it from the world or from bodies, never from nothing.
- **Every move is a hand shape.** The pose (`KineticPoses`) and the far hand (`KineticArt.hand`, `claw`) make the same
  gesture: a palm for Shove and Pin, a claw for Grip and Skyfall's haul, cupped hands for Bullet Catch, a pinch for
  Disarm, palms up for Levitate. Pick the gesture first.

## The kit, and the IDs it kept

| ID (kept) | Now | Notes |
| --- | --- | --- |
| `telekinesis_push` | **Shove** | Hits land as the drawn palm reaches each body (`KineticGeometry.shoveArrival`, a per-target countdown in `TelekinesisService.tickShoves`) |
| `telekinesis_grip` | **Grip** | Takes any loose thing too (`TelekinesisService.loose`); the hold follows the look's pitch; a fast hold bats what it sweeps (`swingInto`); a throw adds the recent swing speed (`Grip.whip`) |
| `telekinesis_bullet_stop` | Bullet Catch | Shots hang in two arcs either side of the view (`shotSlot`), eased into place |
| `telekinesis_disarm` | **Disarm + fling** | A free `followUp` flings the plucked weapon (a `Missile` hitting for `weaponDamage`); until then it hangs beside the off hand so it isn't picked up |
| `telekinesis_storm` | Telekinetic Storm | The gather touches nobody: it is the warning |
| `telekinesis_0_0` | Soft Landing | Adds the fall catch (`catchFall`, 20 s per telekinetic) |
| `telekinesis_0_1` | **Pin** (was Vector Lance) | Level drive (a slant meets the floor at once), landed at the swept position |
| `telekinesis_0_2` | Pinned Together | Carried foes are found along the body's path, with line of sight from the body |
| `telekinesis_remote_hand` | **Beckon** | Renamed so it no longer shares Electrokinesis's Remote Hand name |
| `telekinesis_1_0` | Bowling | The `Thrown` tracker: a moving body hurts what it hits for 70% |
| `telekinesis_1_1` | Round Up (was Gravity Well) | Same mechanics, two cupped hands |
| `telekinesis_1_2` | Carom (was Crossfire) | `TelekinesisService.rebound`, once per throw |
| `telekinesis_kinetic_volley` | **Juggle** | Pops on the server tick; `five_fragments` = Five at Once; `iron_rain` = Spike, a free `followUp` |
| `telekinesis_2_1`, `2_0`, `2_2` | Vector Step, Grounding, Second Step | Second Step is a `followUp` while airborne within 40 ticks |
| `telekinesis_force_bubble` | **Levitate** | Creative-flight attribute modifier + slow flying speed, a `StaminaDrain`, a height cap over the ground |
| `telekinesis_wider_bubble`, `bastion` | Raise the Party, Still Air | Still Air is a passive Bullet Catch (`TelekinesisService.stillAir`) that ends with the hover |
| `telekinesis_skyfall` | **Skyfall** (signature) | Haul, hang, drop on one curve (`KineticGeometry.skyfallLift`); the ring fills on `skyfallWarning` |
| classes `implosion`, `debris_halo`, `gathering_hand` | Clench, Warding Hands, Gathering Hand | Mechanics unchanged; drawn by `KINETIC_CLENCH`, `KINETIC_WARD`, `KINETIC_GATHER` |

## Reusable patterns

- **A blow that lands when the drawing reaches it.** A cone used to hit everything at once while its palm was still
  flying. Each target now gets a countdown from `KineticGeometry.shoveArrival(distance, reach)`, the same function whose
  inverse (`shoveTravel`) places the drawn palm, ticked by the service (so tests drive it with `TelekinesisService.tick`).
  Copy this for any travelling wave whose hits should follow it.
- **Swinging what you hold.** The hold point follows yaw and pitch (`KineticGeometry.holdPoint`), the held body chases it
  at up to 2.2 blocks a tick through `SpeedService.swept`, and the last four speeds are kept: anything the swept box
  crosses above 0.55 is batted, and a throw adds `WHIP_BONUS` times the best of them. The client needs nothing extra:
  `KineticArt.grip` smears streaks from the bound body's own motion.
- **Throws aim at the crosshair's target, not along it.** A body held off to the side and thrown "along the look" flies
  parallel to the crosshair and misses close foes. `TelekinesisService.aimAt` takes the foe under the crosshair first.
- **An impact is its own blow.** A slam or wall hit right after a pop or swing lost the earlier hit's damage to vanilla
  invulnerability. `tickImpacts` zeroes `invulnerableTime` first, as `StyleHits.hurt` does.
- **A thing waiting for a second press must not be picked up.** A plucked weapon reached the caster and vanished into the
  inventory before the fling. It now keeps a pickup delay and hangs beside the off hand (`tickPlucked`) until flung or
  the window closes.
- **Old effect kinds speak the new language.** Gifted mobs still send `TK_*` kinds; `client/effects/TelekinesisEffects`
  starts the matching `KINETIC_*` piece on the client (`SetPieceClient.play`), so every telekinetic mob draws the far
  hand with no change to `MobMoves`.
- **A hand that reads at noon.** The first hand was a neon outline and looked like wireframe. `KineticArt.hand` fills a
  rounded palm with a triangle fan (plum in the soft pass, orchid brighter at the rim in the glow pass) and draws fingers
  as glass (`finger`: a dark soft body, an orchid body, a blush line, a faint core). Reuse it for any hand of light.
- **First person stays clear.** The cupped hands fade to 55% for their own caster in first person and drop the lens,
  and caught shots hang in two arcs at the edges of the view.

## Tests and photographs

- `TelekinesisBaseGameTests` (the core keys) and `TelekinesisFarHandGameTests` (the tree, Skyfall, swinging and
  throwing, Disarm's fling, Levitate, the catch, cleanup):
  `./gradlew runGameTestServer --init-script tools/structure-gametest.init.gradle -PtestPackages=telekinesis`.
  The test area is twelve blocks across: a foe placed farther out sits in ground that doesn't tick, and a thrown thing
  freezes at its edge.
- `tools/telekinesis-preview.init.gradle` (`uitest/TelekinesisPreview`, flag `heroes.preview.telekinesis`) shoots 30
  scenes with checks into `build/telekinesis-preview/screenshots/`. The first scenes after loading can show a black sky;
  the Shove is shot last for that reason.

## Admin

`/heroesevolved telekinesis status|release [targets]` (`telekinesis/KineticCommands`).
