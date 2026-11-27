# Ranged attacks

Ranged attacks use actual block distances from `ability/ProjectileRanges.java`. Narrow shots reach farther than broad
waves, and sustained freezing/control stays closer. These distances are **not** doubled by `AbilityRanges.ranged`.
Damage, stamina, cooldowns, melee reach and explosion radii keep their own balance.

| Attack | Reach (blocks) |
| --- | ---: |
| Fire bolt / charged Cinder Spear / Counterflame fireball | 80 |
| Blue Lance | 96 |
| Quake Shockwave / Double Tap / held Quake Blast | 40 |
| Held Ice Blast | 24 |
| Frost Bolt | 48 |
| Crystal Lance | 64 |
| Frost Armor's shard fan | 24 |
| Chain Lightning's first target | 64 |
| Live Current | 24 |
| Ball Lightning | 64 travelled |
| Arc Lance | 80 |
| Vibe Blast / Return Fire | 48 |
| Vibe Lance | 72 |
| Reverb / Crosswave | 40 |
| Waterbending's Ice Daggers | 40 |
| Crashing Wave / Tsunami | 32 / 48 from the wave's leading starting position |
| Force Push / Hard Push | 24 / 32 |
| Meltdown Beam / Gamma Lance | 64 / 80 |
| Focused Shriek | 80 |

Travelling shots stop on cover and expire when their distance is spent. Ball Lightning counts the length of its
curving path, rather than relying only on a timer. Its lifetime allows the full trip; swept collision finds the first
body before a wall instead of checking only the next position. Crystal Lances and armor shards also use the shared
nearest-body sweep, and a casting hand pressed through cover cannot spawn its lance beyond it.

Fire and frost check the same range and liquids for creatures and terrain. Fire quenches on water before it can hit a
creature behind the water. Quake, force waves and radiation beams check cover before hurting a body at the edges of
their paths. Vibe waves keep their travel speed at long range, with enough visual lifetime to reach the destination;
Force Push's visual also lasts for the full upgraded trip.

Short-range fighting moves, breath cones, grabs, placed notes, traps and area effects retain their roles. The four
TaCZ weapons keep their existing ballistic lifetimes and roles (short heat stream and taser, longer cryo and dampener
rounds); vanilla arrows, thrown items and shapeshifted creatures' vanilla projectiles keep their native physics.

## Checking

`./gradlew runGameTestServer --init-script tools/projectile-gametest.init.gradle` runs focused long-lane damage,
cover, water, nearest-hit and range-expiry regressions. The normal full game-test init script includes them too.

`./gradlew runClient --init-script tools/projectile-preview.init.gradle` stages fire, Quake, a Crystal Lance and Vibe
in one loaded overworld lane. It writes side and first-person photographs to `build/projectile-preview/screenshots/`
and logs `PROJECTILE_SCENE` (dimension and floor) and `PROJECTILE_HIT` (distant target health).
