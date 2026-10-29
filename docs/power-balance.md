# Power balance and Superspeed rework

This pass strengthens Superspeed's movement-to-combat loop and adjusts stamina costs, cooldowns, and the longest control effects across all eleven power sets. Existing worlds retain ownership, progression, purchased skills, and loadouts. Install the updated JAR on both clients and server.

## Superspeed

- Accelerate uses 3 stamina per running second. Movement starts at 4x at level one and reaches 8x at level five, before ordinary movement modifiers.
- Sustained running builds 100 Momentum. Charge adds up to 2x movement speed; stopping drains it. With at least 50 Momentum, your next direct melee hit consumes it for up to 12 extra damage against mobs, capped at 4 against players.
- Quickstep travels 14–30 blocks with a one-second base cooldown and collision checks.
- Lightning Flurry rushes a visible target within 12 blocks and delivers six strikes. Cover, escape, and incapacitation interrupt it. Its base damage against players is reduced to 60%.
- Whirlwind lasts five seconds and periodically damages enemies caught inside, alongside its existing displacement and projectile defense.
- Redline lasts twelve seconds, immediately fills Momentum, adds another 3x speed, and enables paid projectile dodges. Its running drain is 6 stamina per second; dodges still need space and stamina. Maximum level-five movement is 13x before other modifiers.
- Running leaves stronger amber ribbons, lightning, and translucent copies of your skin and running pose. Copies stay where they were captured, fade over 0.9 seconds, and support slim arms and enabled outer skin layers. Armor, capes, and held items are not copied.

The afterimages have a shared 48-copy cap, 64-block draw distance, frustum culling, and reduced emission on minimal particle settings. They create no entities or additional network packets. See [the Superspeed guide](superspeed.md) for controls and optional skills.

## Changes across the roster

Percentages below apply to configured base values, before skill discounts. Smaller cooldown percentages mean more frequent use.

| Power | Adjustments |
| --- | --- |
| Quake | Seismic Slam costs 85% stamina with 80% cooldown; Earthquake costs 85% with 65% cooldown. Sustained blast terrain pressure is retained. |
| Flight | Meteor Drop costs 80% with 75% cooldown; Tempest costs 85% with 75% cooldown. |
| Cryokinesis | Ice Wall costs 80% with 80% cooldown; Blizzard costs 80% with 60% cooldown. Players can be frozen for at most three seconds per freeze, followed by two seconds of thaw protection. Refreshing a dome cannot extend an existing player freeze indefinitely. Player shatter bonus is capped at six extra damage; mob freezing retains its stronger effect. |
| Electrokinesis | Chain Lightning has 120% cooldown. Thunderstrike costs 85% with 80% cooldown; Storm Surge costs 85% with 60% cooldown. |
| Telekinesis | Push costs 80%; Bullet Stop costs 80% with 70% cooldown; Storm costs 85% with 60% cooldown. |
| Telepathy | Read Mind costs 70% with 70% cooldown; Mind Link costs 75% with 60% cooldown; Puppet has 115% cooldown; Nightmare costs 80% with 70% cooldown. |
| Regeneration | Rapid Mending has 125% cooldown; Blood Gift costs 70% with 75% cooldown; Blood Bond (formerly Healing Aura) costs 80% with 60% cooldown. Second Life costs 40 with a 90-second cooldown. |
| Time Manipulation | Between Seconds costs 65% with 60% cooldown; Still World costs 60% with 35% cooldown; Rewrite the Moment costs 130 stamina (overexert with Shift) and recovers in 4 minutes at Level 4, 2½ at Level 5. Still World is capped at 6/8/10 seconds at levels 3/4/5, with at most 12 stored damage per player or 40 per mob. Lower configured durations remain lower. |
| Radiation | EMP costs 80% with 70% cooldown; Radiation Pulse costs 85% with 80% cooldown; Exploding Man has 75% cooldown. Nuclear blast damage and radius are retained. |
| Sonic Resonance | Sonic Burst and Sonic Scream cost 80% with 80% cooldown; Echo Sense costs 80% with 75% cooldown. |
| Superspeed | New base costs/cooldowns: Quickstep 10 / 1s, Flurry 22 / 5s, Whirlwind 30 / 9s, Redline 35 / 20s. Movement, Momentum, and effects changes are described above. |

Only listed actions change their cost or cooldown. Optional skills retain their existing prices and discounts. The bundled `data/heroesevolved/power_balance.json` table loads at startup; it is not a live datapack configuration. Values round upward, and configured zero remains zero. Activation and the HUD use the same calculation.

## Verification and remaining playtesting

- All 97 required gameplay tests pass, including momentum consumption, actual activation/HUD cost agreement, freeze recovery, stored-impact limits, power ownership, and existing cross-power regressions.
- Shared effect geometry, opacity decay, payloads, all 99 skill nodes, and 33 optional actions pass automated checks.
- A native Minecraft renderer preview verifies skin transparency and wide/slim running silhouettes. It is an isolated rendering fixture, not a multiplayer recording.
- All 14 baseline imported sound files retain their original hashes.

Live multiplayer testing is still needed to judge movement feel, visual density, terrain traversal, and competitive balance under server latency. The automated tests establish behavior and limits, not final competitive tuning.
