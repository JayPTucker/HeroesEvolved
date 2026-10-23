# Cryokinesis effects and ranged reach

Flash Freeze now applies its contact damage before encasing the target. Cryo
damage is marked for the duration of the hit so Frost Bolt, spikes and Blizzard
cannot immediately shatter their own ice or accidentally trigger Cold Touch.
Physical hits still shatter the shell for bonus damage.

Chill accumulates, slows movement with a transient attribute modifier, and
reaches a full freeze at the creature's vanilla freezing threshold. Unrefreshed
chill starts fading after two seconds. Full freezes pause mob simulation,
including attacks, creeper fuses and animations, without saving a NoAI flag.
Players are held in place and cannot activate abilities while frozen. Bosses
only chill; creatures with vanilla cold immunity resist freezing. Fire thaws
the ice. Expiration, power removal, death, logout and dimension changes clean
up the state and movement penalty.

The client receives identity-checked shell updates for tracked creatures,
including when a player starts tracking an already frozen creature. Shells
use translucent faceted ice with a closed crown and bright fractures. Frost
Bolt, growing ice, chill, freeze, shatter and the Cryokinesis awakening each
have dedicated crystal and rime compositions.

Blizzard now creates a visible twenty-block-radius cold dome that follows its
caster. The first awakening releases a larger twenty-four-block-radius dome,
anchored at its release position for eight seconds. Both immediately freeze
nearby passive/hostile creatures and eligible players; they refresh freezing
inside the volume every half second. The caster is excluded, existing cold
immunities remain, bosses only chill, and creative/spectator players are skipped.
Mobs thaw within four seconds after leaving the cold; players within two.
Physical hits still shatter ice, fire can thaw it, and Blizzard's own cold damage
does not shatter its victims. Frozen mobs retain their original AI flags.

The cold region is a hemisphere above the caster with six blocks of penetration
below its base. A translucent frosted shell shows the boundary. Pale blue fog
reduces visibility to eight blocks inside it, and camera-local wind-driven snow
keeps the larger volume visibly dense. Graphics settings reduce snow density and
mesh detail; no graphics or render-distance preferences are changed. The shell
uses 384 or 1,024 vertices per dome, with a four-visible-dome limit, distance and
frustum culling. It uses the existing shader view transform exactly once.

A streaming outward sweep temporarily converts exposed solid surfaces and water
to ice. Containers, unbreakable/protected/occupied blocks and plant-supporting
surfaces remain intact. Every changed block remembers its original state and
properties. A player edit after freezing is preserved during thaw. Repeated
frosting extends expiry while retaining the first original state. Terrain stays
icy until roughly four seconds after the cold volume expires; loaded terrain
then restores in batches. Unloaded chunks are not forced; restoration resumes
when they load. Server shutdown restores all remaining temporary blocks.

All domes share a maximum of sixty-four column scans and a soft two-millisecond
terrain allowance per tick. Thaw shares a ninety-six-write, soft two-millisecond
allowance and suppresses per-block particle bursts for dome terrain. Temporary
blocks retain the global four-thousand-block cap. This avoids freezing/thawing
the whole area in a single expensive tick. The previous server snow/ash spam is
replaced by client-generated storm ribbons, snow and the hemisphere mesh.

## Ranged abilities

`AbilityRanges.ranged` doubles authored base reach. `Targeting` applies it
centrally to aimed living-entity selection, preserving wall obstruction.
Beam endpoints and independent projectile limits use the same multiplier.

| Ability | Previous reach | New reach |
| --- | --- | --- |
| Frost Bolt | 20 | 40 |
| Flash Freeze | 6 | 12 |
| Glacial Spikes | 12 + level | 2 × (12 + level) |
| Quake Shockwave | 6 + 2 × level | 12 + 4 × level |
| Held Quake Blast | 10 + level | 20 + 2 × level |
| Chain Lightning / subsequent jumps | 16 / 5 | 32 / 10 |
| Thunderstrike | 48 | 96 |
| Ball Lightning at speed 0.55 | 50 ticks / 27.5 blocks | 100 ticks / 55 blocks |
| Ride the Lightning | 10 + level | 20 + 2 × level |
| Telekinetic Push / Grip acquisition | 8 / 16 | 16 / 32 |
| Grip maintenance cutoff | 24 | 48 |
| Read Mind / Suggestion / Puppet / Nightmare | 24 / 16 / 20 / 20 | 48 / 32 / 40 / 40 |
| Blood Gift / Radiation Touch / Skyward Grab | 5 / 5 / 6 | 10 / 10 / 12 |
| Controlled Emission (formerly Meltdown Beam) | 24 | 48 |
| Time Blink | Configured per-level distance | 2 × configured distance |

Telekinetic thrown-impact tracking also lasts twice as long. Area radii,
damage, costs and cooldowns were preserved by the reach adjustment. Quake beam geometry now covers the longer endpoints.
The subsequent Ted Sprague redesign replaces Radiation's old mimicry and stolen-projectile skills with EMP.
The Telepathy story fragments and future-city hooks are preserved.

## Verification

Run the production build, the isolated server suite, and the particle checker:

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
.\tools\verify-effect-geometry.ps1
```

The server suite checks full-freeze duration, shatter damage, fuse suspension,
thaw/resume, chill buildup and cleanup, Blizzard damage and boundaries, long
range hits and wall blocking, long-range Grip maintenance, orb travel and
frozen-villager story progression, alongside the existing rewind and intro
regressions. Test sources and their flat-world fixture stay out of the mod jar.
The particle checker covers finite geometry, culling bounds and packet codecs;
fog appearance and visual density still need an in-game visual playtest.


The expanded dome checks cover immediate passive/hostile/boundary freezes, the
actual first-acquisition sequence at twenty-three blocks, automatic mob and
terrain thaw, original block properties and water restoration, container contents,
player edits and gradual restoration of a large patch. The complete server suite
has sixty-four tests. Mesh checks validate both radii, near/far camera positions,
geometry budgets and matching cold boundaries. Imported sound files are unchanged.
