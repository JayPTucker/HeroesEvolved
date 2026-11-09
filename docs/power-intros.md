# First-acquisition power intros

All eighteen powers have a distinct animated introduction when first unlocked.
Grants, admin replacements, the Formula and natural dormant awakenings use the
same acquisition hook. Assigning a dormant power alone does not run an intro.
Existing unlocked non-time powers do not suddenly fire destructive intros on login.

A natural awakening only happens at low health in a fight (see `eclipse.md`), so
every release is built to save its owner. Each release first grants a second
wind (at least 40% health and six seconds of Resistance II), then ends the fight
in the power's own way. "Threats" below are monsters, anything targeting the
owner, and whoever last hurt them, another player included.

| Power | Charge | Release |
| --- | --- | --- |
| Precognition | Two and a half seconds of gold rings at the owner's feet while every nearby threat glows, with a gold ghost of where it is about to lunge. | A red ring of foresight. Threats within twelve blocks lose their target, stumble back, and are slowed and weakened; the first painting and a free Premonition follow. |
| Superspeed | Two seconds of golden sparks. | Threats are thrown back and slowed to a crawl, and the owner gets ten seconds of Speed III. |
| Invisibility | Two seconds of the owner's outline dissolving into violet and silver strips. | A shattering optic burst. The owner fades for five seconds, every threat targeting them loses them, and they get a burst of speed to slip away. |
| Phasing | Two seconds of ghostly strands as the floor turns thin. | Concentric density ripples. Five seconds of full intangibility, and the ripple shoves threats away. |
| Empathic Mimicry | Two and a half seconds of gold threads to every heartbeat nearby. | A nearby power is remembered if one exists; threats within eight blocks are weakened and pushed back while their strength drains into the owner (Strength I). |
| Telepathy | Three seconds of growing whispers while nearby minds light up. | Every mind within 24 blocks glows; hostile minds within twelve lose their target and are slowed, weakened and confused. |
| Cryokinesis | Three seconds of frost creeping outward and snow settling. | A crown of ice spikes and the large awakening cold dome freezes everything around the owner. |
| Radiation | Thirty seconds of uncontrolled heat, during which the owner is shielded and the air around them is lethal. | A nuclear shockwave; the ground stays radioactive. |
| Sonic Resonance | Two seconds of vibrating air. | A full-strength sonic burst. |
| Pyrokinesis | Two and a half seconds of smoke and heat. | A ring of fire bursts outward, burning and throwing back threats within eight blocks. |
| Quake | Three seconds of increasing tremor, inward pressure rings, and energy gathering around both hands. | A spherical terrain fracture advances to twelve blocks over two seconds, with three pressure fronts, radial streaks, block fragments, debris, a sonic boom and camera shake. |
| Flight | One and a half seconds of wind gathering underfoot and spiralling up the body. | The existing real Flight Skyburst fires through a rising column of wind rings and ribbons. Nearby enemies are pushed back. |
| Electrokinesis | Two and a half seconds of electrical arcs feeding a rotating blue/white corona. | A central lightning strike, three surrounding follow-up strikes, expanding electrical rings and a discharge that repels and damages nearby enemies. |
| Telekinesis | Two seconds of inward motes and a concentrating violet field. | Nearby mobs and loose items briefly levitate, cosmetic stones rise from the ground, then the lifted objects are pushed outward. Living targets receive a brief slow-fall effect on release. |
| Regeneration | Two and a half seconds of wounds healing amid red-to-gold particles. | Full health, harmful-status removal, extinguished fire, and a healing bloom reaching non-hostile living entities within eight blocks. |
| Time Manipulation | The existing city vision. The owner is shielded while the city is prepared. | The siren, panicked crowds, nuclear detonation and group escape remain intact; see `time-manipulation-intro.md`. On a completed return home the owner gets the second wind, and nearby monsters lose their target and are left a heartbeat behind (heavily slowed and weakened for five seconds). |
| Technopathy | Cyan diagnostic routes resolve around the owner while nearby mechanisms begin to make sense. | A circuit scan and brief hostile-projectile rejection pulse; no free network mastery or Evolved trial progress. |
| Matter Assembly | Amber construction lines and fragments assemble around the owner's hands. | A defensive lattice and a recoil pulse give the owner room to act. Paid construction starts with the actual ability keys. |

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
Eclipse suppression (totality only) remains authoritative. Power removal, logout, death,
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
