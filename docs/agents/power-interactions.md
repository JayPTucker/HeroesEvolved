# Powers working together: notes for agents

The first power that acts on another (a speedster containing a radioactive player's blast). Read it before making any two powers interact. What the player sees: [superspeed.md](../superspeed.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from powers working together (speedster containment)

A speedster can run rings round a radioactive player to cool them or hold in their blast
(`speed/SpeedContainment`; docs in [docs/superspeed.md](../../docs/superspeed.md)). It is the first power that acts
on another, and these are the patterns to reuse for the next one.

- **One key, two meanings by context.** The Cyclone key starts a containment run when a hot radioactive player is
  in reach (`SuperspeedAbility.CycloneOrArms.activate` tries `SpeedContainment.start` first), and a Cyclone
  otherwise. Its release goes to whichever is running.
- **The other power exposes a small API, not its internals.** `RadiationService.critical(player)`,
  `hold(player)` (push the warning and the charge back a tick) and `defuse(player, core)` are all the speedster
  needs; it never touches the radiation maps directly.
- **Reuse the drawing.** The run is a Cyclone view with a new style (`SpeedCyclonePayload.CONTAIN`): the client's
  runner, trail and wind come for free, a faster lap is one multiplier in `SpeedCyclone.View.angle`, and
  `SpeedArt.containment` draws only what is new on top.
- **Cost that can kill, by tier.** Strain is paid in stamina first and health after
  (`staminaPerHeat` / `healthPerHeat`, kinder at tier 5), with fractional costs carried over in a debt.
  The killing blow is checked *before* it lands, so the death can be special.
- **A special death.** A custom damage type (`data/heroesevolved/damage_type/disintegrated.json`, its death
  messages in `en_us.json`, and the `bypasses_*` damage tags so armor and Resistance don't blunt it) gives the death
  its own message. The `SPEED_DISINTEGRATE` set piece draws the body breaking into cubes, and
  `SpeedArt.disintegrating(id)` hides the real body and corpse while it plays (checked in `SpeedCyclone.hide`).
- **A defence another power provides.** Shield-making powers survive a nuclear blast at tier 4+
  (`radiation/NuclearShields`): the blast asks `NuclearShields.brace(target, center, damage)` before it hurts
  anyone, and the fallout skips anyone still `shielded`. Keep the list of which powers count (a map of power ID to
  shield look) in one place, so adding a power is one line.
- **Hold a death back for its animation.** A beaten speedster isn't killed on the spot: the run enters a failing
  phase (`SpeedContainment.disintegrate` → `burning`), the runner is made invulnerable, the client is told with a
  new view style (`SpeedCyclonePayload.CONTAIN_FAIL`) so the stand-in keeps running but slows to a stop over the burn, the
  disintegration piece follows the runner's view on the ring (cubes break off where the runner was when they let go;
  the body is hidden once `SpeedArt.consumed`), and the kill lands when the animation ends. Make sure nothing (a key release) can end the phase early and leave the player invulnerable.
- **PvP gates player-caused damage.** A blast, beam or anything else a player causes can't hurt other players
  where PvP is off, and the game-test server always has PvP off (`setPvpAllowed(true)` is ignored there). Test
  player-on-player rules by calling the rule directly (as the shield test calls `NuclearShields.brace`).
