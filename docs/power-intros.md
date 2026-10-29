# First-acquisition power intros

All six powers now have a distinct introduction when first unlocked. Grants,
admin replacements, and natural dormant awakenings use the same acquisition
hook. Assigning a dormant power alone does not run an intro. Existing unlocked
non-time powers do not suddenly fire destructive intros on login.

| Power | Charge | Release |
| --- | --- | --- |
| Quake | Three seconds of increasing tremor, inward pressure rings, and energy gathering around both hands. | A spherical terrain fracture advances to twelve blocks over two seconds, with three pressure fronts, radial streaks, block fragments, debris, a sonic boom and camera shake. |
| Flight | One and a half seconds of wind gathering underfoot and spiralling up the body. | The existing real Flight Skyburst fires through a rising column of wind rings and ribbons. Nearby enemies are pushed back. |
| Electrokinesis | Two and a half seconds of electrical arcs feeding a rotating blue/white corona. | A central lightning strike, three surrounding follow-up strikes, expanding electrical rings and a discharge that repels and damages nearby enemies. |
| Telekinesis | Two seconds of inward motes and a concentrating violet field. | Nearby mobs and loose items briefly levitate, cosmetic stones rise from the ground, then the lifted objects are pushed outward. Living targets receive a brief slow-fall effect on release. |
| Regeneration | Two and a half seconds of wounds healing amid red-to-gold particles. | Full health, harmful-status removal, extinguished fire, and a healing bloom reaching non-hostile living entities within eight blocks. |
| Time Manipulation | The existing city vision. | The siren, panicked crowds, nuclear detonation and group escape remain intact; see `time-manipulation-intro.md`. |

Each new composition has its own charge and release particle kinds. Primary
cues remain visible on Minimal particles; richer filaments, motes and layered
fronts scale with the particle setting. The shared 768-particle cap remains.
The existing compact caption layout is reused. Existing flight/time audio and
the city intro assets are unchanged; other intros use vanilla sound cues and
replay the existing New Beginning cue for their owner.

`PowerIntroHistory` persists one marker per non-time power, including across
death and power reassignment. It is saved before the first charge effect, so
relogging, removing and regranting, or calling the natural awakening callback
twice cannot repeatedly trigger terrain destruction or free healing. Time
retains its existing persistent marker. Interrupted started intros are consumed.

Owners are protected during the sequence and regular ability actions wait
until it finishes. One subsequent fall is cushioned, with a thirty-second expiry.
Eclipse suppression remains authoritative. Power removal, logout, death,
dimension travel, and server shutdown cancel active sequences and clear poses.
An intro acquired while visiting the city waits until the visit ends.

## Quake terrain behaviour

This is actual block destruction, not a cosmetic explosion. The advancing sphere
processes at most 768 candidate positions per server tick and does not load new
chunks. It breaks ordinary blocks, drops their normal block/container contents,
and leaves a small footing below the owner. Blocks with negative destroy speed,
fluids, spawn protection, world borders, and canceled NeoForge block-break events
are respected. Direct destruction is limited to twelve blocks. Normal neighbour
updates, unsupported blocks and flowing fluids can react afterward.

The city and Temporal Snapshot dimensions do not permit this terrain fracture.
Destruction goes through the normal world block API, so existing Time Rewind
journals can record it. Intros do not charge a new activation cost; Flight Skyburst
enters the existing flight session, with its normal subsequent flight upkeep.

## Verification

```text
./gradlew.bat build
./gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
powershell -File tools/verify-effect-geometry.ps1
```

The isolated server suite includes first-grant and dormant-unlock routing,
duplicate callback handling, Quake charge timing, actual stone destruction,
radius limits, bedrock, footing, canceled block breaks, persisted history,
one-time regrants, interrupted charges, regeneration and nearby healing, real
Flight Skyburst, non-burning cinematic lightning, and actual telekinetic item
levitation without item duplication or changed gravity flags. Existing rewind
and city-trip tests run alongside them. Client geometry checks exercise finite
mesh output, bounds, UV/color validity, expiry and all effect payload codecs.
These checks do not replace an in-game viewing/listening pass for the new intros.
