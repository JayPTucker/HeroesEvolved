# Superspeed

A new unique ability set (`heroesevolved:superspeed`) combining the movement and reaction theme of [Heroes' Enhanced Speed](https://heroes.fandom.com/wiki/Enhanced_Speed) with Flash-inspired golden lightning, rapid strikes, and a running whirlwind. Enhanced Speed informs the contact rescue, brief wall traversal, and resistance to slowed time while already moving. This is a bounded Minecraft adaptation, not literal intercontinental or faster-than-light travel.

## Core abilities

| Default key | Unlock | Ability | Behavior | Base cost / cooldown |
| --- | --- | --- | --- | --- |
| G | Level 1 | Accelerate | Toggle on, then use normal sprint controls to accelerate. Walk or crouch for precision; G again or Shift + G stops it. | Free toggle; 3 stamina per running second |
| H | Level 1 | Quickstep | Dash horizontally along your gaze, 14 blocks at level 1 through 30 at level 5. Stops before obstructions. | 10 / 1 second |
| Z | Level 2 | Lightning Flurry | Six rapid strikes over about half a second after a collision-checked rush to a visible enemy within 12 blocks. Each hit deals 2 + floor(level / 2) base damage. Escaping, cover, or incapacitating the attacker interrupts it. | 22 / 5 seconds |
| X | Level 4 | Whirlwind | Five-second vortex with a 6-block radius; spins and damages enemies and pushes hostile projectiles outward. Leaving its 8-block tether ends it. No terrain damage. | 30 / 9 seconds |
| V | Level 5 | Redline | Engage Accelerate with an extra burst of speed for twelve seconds, immediately filling Momentum and enabling projectile dodges. Then return to regular Accelerate. Shift + V cancels. | 35 / 20 seconds; 6 stamina per running second during Redline |

Sprint movement uses native synced attributes. The starting movement multiplier ranges from 4x at level 1 to 8x at level 5. Running builds Momentum, adding up to another 2x. Redline adds 3x, for a level-five peak of 13x. At least 50 Momentum empowers the next direct attack, consuming the charge for up to 12 bonus damage against mobs or 4 against players. The flurry deals 60% base damage against players. Other ordinary movement modifiers still apply. Camera expansion is capped at 1.12x to avoid extreme speed-induced distortion. Sprinting adds half a block of step height for ordinary terrain.

At **level 3**, sprinting with Accelerate supports your feet on the water surface using matching client/server collision. This includes flowing water and water plants. Release sprint, crouch, or disable Accelerate to sink normally. It does not lift submerged swimmers, support lava running, or catch long falls. At **level 4**, sprinting into a wall provides up to thirty ticks of upward assistance; it resets after returning to clear ground. Steep falls are not caught. Movement still benefits from in-game feel testing across terrain and server latency.

While **Accelerate is enabled**, edible food is consumed **four times faster**, even when standing still. A normal 32-tick meal takes 8 ticks (0.4 seconds). Nutrition, item consumption, and food effects stay normal. Disabling Accelerate restores normal eating speed immediately, including during a meal. Bows, potions, milk, and other non-food uses are unaffected.

## Skill tree

Three branches, nine nodes, using the existing points, mastery bonuses and loadout controls:

| Branch | Optional skill | Behavior | Base cost / cooldown |
| --- | --- | --- | --- |
| Efficient Stride / Control | Reflex Focus | Six seconds of reactive projectile dodging. Requires a clear sideways path and 3 additional stamina per dodge; at most one dodge each second. Does not block melee, explosions, falls or arbitrary damage. | 20 / 8 seconds |
| Kinetic Force / Force | Momentum Strike | Rush a visible target up to 40 blocks away through a clear path, ending within striking distance and dealing 6 + level damage. | 28 / 9 seconds |
| Quick Recovery / Survival | Slipstream Rescue | Carry one nearby villager, friendly creature, teammate, or crouching player up to twelve blocks ahead. Preserves their offset and sweeps both bodies before moving either. | 24 / 6 seconds |

Learn and equip these with the inventory **Skills** button. Passive discounts apply to initial activation costs and cooldowns, not ongoing running or dodge costs. Running earns Mastery for actual stamina spent. Each running interval is paid upfront so toggling cannot avoid the drain; walking does not consume running stamina.

## Power interactions and awakening

- Only one player can own Superspeed, through the existing persistent ownership system. It participates in the normal assignment pool and admin menus.
- The awakening has compact captions, rising amber arcs, a wind-ring release, and a non-damaging push to nearby enemies. It records completion through the existing intro history and does not forcibly launch the player into terrain.
- An actively sprinting, moving speedster can move through Between Seconds and Still World. Standing still does not confer immunity. Selective Stasis remains a separate counter. Being frozen solid, suppression, death, riding, elytra flight, dimension changes, logout or power removal ends enhanced running.
- Sylar notices successful activations normally. Once he learns Superspeed, his copied-power attack can rush through a checked path and strike at close range.
- No additional time-travel capability is granted; the time power retains its role. No unrestricted phasing through protected buildings or bedrock.

## Safety and performance

Dash and rescue paths check body-sized boxes every quarter block, up to forty blocks per action. They reject unloaded chunks, obstructed space, lava, fire and world boundaries. Horizontal bursts preserve downward velocity and accumulated fall distance. Normal running uses ordinary Minecraft collisions.

Running trails send at most ten bounded segment descriptions per second per moving runner. Clients render amber ribbons, short lightning branches and pressure rings using the existing particle/detail budget. Afterimages capture the actual skin and running limb pose at fixed world coordinates, fading from 42% opacity over 18 ticks. Slim/wide arms and enabled skin layers are supported; armor, capes and held items are not duplicated. At most 48 ghosts are retained across the scene, with lower emission at minimal particles. No extra entities or afterimage network packets are created.

A whirlwind considers at most 32 affected entities per update. Transient movement modifiers and runtime combat state are removed on cleanup. Existing imported recordings remain unchanged; new speed cues use vanilla wind and sweep sounds.

Both client and server need the updated mod: effect protocol version is now **26**.

## Verification

Run `gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle` for gameplay tests. Superspeed coverage includes native stride changes and cleanup, wall-safe bursts, preserved falling momentum, energy exhaustion and rapid-toggle protection, stopped-time/cold interactions, paid projectile dodges, contact rescue, momentum strikes, interrupted flurries and unique ownership.

`tools/verify-effect-geometry.ps1` checks the shared geometry and effect payloads; `python tools/verify-skill-content.py` checks all eleven trees, 99 nodes and 33 optional actions. `gradlew.bat build` produces the installable JAR. These checks do not replace a multiplayer visual/movement playtest.
