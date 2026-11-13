# First-acquisition power intros

All eighteen powers have a distinct animated introduction when first unlocked.
Grants, admin replacements, the Formula and natural dormant awakenings use the
same acquisition hook. Assigning a dormant power alone does not run an intro.
Existing unlocked non-time powers do not suddenly fire destructive intros on login.

A natural awakening only happens when someone is about to lose a fight: the owner
themself, or an ally near them (see `eclipse.md`). That person is the intro's
**ward**, and every release is built to save them. Each release first grants the
owner, and the ward, a second wind (at least 40% health and six seconds of
Resistance II), then ends the ward's fight in the power's own way. "Threats" below
are monsters near the ward, anything targeting the ward or the owner, and whoever
last hurt either of them, another player included.

The ward is shielded from the moment the power wakes until two seconds after its
release (or rescue run) ends, and gets one cushioned fall. While the power charges,
a thread runs from the owner to the ward so everyone can see who it is for. The
release caption changes to a rescue line (`intro.heroesevolved.<power>.rescue`).

## Rescues

**Superspeed** answers allies up to 48 blocks away. Its charge is short (under a
second) and slows every threat to a crawl. The runner then crosses to the ward
along a searched route, picks them up (or tows them close behind when a low tunnel
or their size won't allow a carry), and runs them out:

- Underground or indoors, it takes the shortest route to open sky, running up walls
  where it has to. A sealed cave settles for the reachable point furthest from the
  threats.
- Out in the open, it runs about 96 blocks across the surface, away from the
  threats, turning round lava, fire, walls and cliffs and over water (it keeps going
  up to 48 more blocks to stop on land). It stops early at the edge of loaded ground.
- At the end the ward is set down beside the runner, who gets ten seconds of Speed III.

The run moves 3.2 blocks a tick by teleport along the planned route, drawn with the
speedster's trail (`intro/SpeedRescue`).

**Flight** answers allies up to 64 blocks away, and only out under open sky: the owner
must have no solid block above them (leaves don't count), and so must the ward. In a
cave, the power strains toward the sky (an action-bar line) and stays asleep; a grant
or the Formula underground waits until its owner steps outside. After a one-second
charge the flyer swoops to the ward at 4.5 blocks a tick, takes them in a Skyhook carry,
and climbs straight away from the fight at Skyburst speed (8.5 blocks a tick easing to
1.25, rising 1.6 for each block outward, about 58°). The downdraft throws the ward's attackers
clear. The flyer ends in a normal flight session, still carrying the ward, who has slow
falling in case they're let go (`intro/FlightRescue`). With no ally to fetch, the flyer
makes the same climb alone.

Every other power plays its release where its owner stands, as below, and also reaches
across to an ally ward (`intro/WardRelease`):

| Power | What it does for an ally |
| --- | --- |
| Invisibility | The ward fades for five seconds; everything attacking them loses them. |
| Phasing | The ward goes thin for five seconds (no harm reaches them) and the ripple shoves attackers away. |
| Empathic Mimicry | Their attackers are drained (Weakness II) and the strength flows into the ward (Strength, Regeneration II). |
| Telepathy | A psychic shout into every mind attacking them: lost target, slowed, weakened and confused. |
| Cryokinesis | Everything within nine blocks of them freezes solid for six seconds. |
| Radiation | The thirty-second heat spares friends and pets, and burns the ward's attackers too. |
| Sonic Resonance | A wall of sound hurls their attackers back and turns incoming shots around. |
| Pyrokinesis | The ring of fire rises round the ward instead. |
| Quake | A fault line runs to the ward and erupts under their attackers. The owner's terrain fracture is skipped if the ward stands within fourteen blocks of it. |
| Electrokinesis | Lightning strikes up to eight of their attackers. |
| Telekinesis | Their attackers are flung away and the ward is lifted out and brought to the owner. |
| Regeneration | The ward is healed in full and cleansed. |
| Technopathy | A diagnostic lock slows their attackers, and arrows in flight at the ward drop. |
| Matter Assembly | Attackers are shoved back, then a copper lattice rises round the ward for eight seconds. |
| Time Manipulation | The ward is shielded for the whole vision; on a completed return they get the second wind and the monsters round them fall a heartbeat behind. |

## Each power's release

| Power | Charge | Release |
| --- | --- | --- |
| Superspeed | Under a second of golden sparks while every threat slows to a crawl. | The rescue run above: out of any cave and far across the surface, carrying the ward. |
| Invisibility | Two seconds of the owner's outline dissolving into violet and silver strips. | A shattering optic burst. The owner fades for five seconds, every threat targeting them loses them, and they get a burst of speed to slip away. |
| Phasing | Two seconds of ghostly strands as the floor turns thin. | Concentric density ripples. Five seconds of full intangibility, and the ripple shoves threats away. |
| Empathic Mimicry | Two and a half seconds of gold threads to every heartbeat nearby. | A nearby power is remembered if one exists; threats within eight blocks are weakened and pushed back while their strength drains into the owner (Strength I). |
| Telepathy | Three seconds of growing whispers while nearby minds light up. | Every mind within 24 blocks glows; hostile minds within twelve lose their target and are slowed, weakened and confused. |
| Cryokinesis | Three seconds of frost creeping outward and snow settling. | A crown of ice spikes and the large awakening cold dome freezes everything around the owner. |
| Radiation | Thirty seconds of uncontrolled heat, during which the owner is shielded and the air around them is lethal. | A nuclear shockwave; the ground stays radioactive. |
| Sonic Resonance | Two seconds of vibrating air. | A full-strength sonic burst. |
| Pyrokinesis | Two and a half seconds of smoke and heat. | A ring of fire bursts outward, burning and throwing back threats within eight blocks. |
| Quake | Three seconds of increasing tremor, inward pressure rings, and energy gathering around both hands. | A spherical terrain fracture advances to twelve blocks over two seconds, with three pressure fronts, radial streaks, block fragments, debris, a sonic boom and camera shake. |
| Flight | One second of wind gathering underfoot and spiralling up the body (only under open sky). | The airlift above: a swoop to the ward, a Skyhook carry and a straight climb away at Skyburst speed, ending in real flight. |
| Electrokinesis | Two and a half seconds of electrical arcs feeding a rotating blue/white corona. | A central lightning strike, three surrounding follow-up strikes, expanding electrical rings and a discharge that repels and damages nearby enemies. |
| Telekinesis | Two seconds of inward motes and a concentrating violet field. | Nearby mobs and loose items briefly levitate, cosmetic stones rise from the ground, then the lifted objects are pushed outward. Living targets receive a brief slow-fall effect on release. |
| Regeneration | Two and a half seconds of wounds healing amid red-to-gold particles. | Full health, harmful-status removal, extinguished fire, and a healing bloom reaching non-hostile living entities within eight blocks. |
| Time Manipulation | A walkable vision of Odessa in its last half-minute. The owner is shielded while the city is prepared. | Sprague's glow, the sirens and the panicking crowd, a 3D fireball and mushroom cloud, and a shock wave that wrecks the streets on its way to you; a few blocks short, time stops and the whole explosion runs backwards. See `time-manipulation-intro.md`. On a completed return home the owner gets the second wind, and nearby monsters lose their target and are left a heartbeat behind (heavily slowed and weakened for five seconds). |
| Technopathy | Cyan diagnostic routes resolve around the owner while nearby mechanisms begin to make sense. | A circuit scan and brief hostile-projectile rejection pulse; no free network mastery or Evolved trial progress. |
| Matter Assembly | Amber construction lines and fragments assemble around the owner's hands. | A defensive lattice and a recoil pulse give the owner room to act. Paid construction starts with the actual ability keys. |

Each new composition has its own charge and release particle kinds. Primary
cues remain visible on Minimal particles; richer filaments, motes and layered
fronts scale with the particle setting. The shared 768-particle cap remains.
The existing compact caption layout is reused. Captions stay up as long as their words take to read (about fourteen characters a second, on a dark band), and one that arrives while another is still being read waits its turn. Existing flight/time audio and
the city intro assets are unchanged; other intros use vanilla sound cues and
replay the existing New Beginning cue for their owner.

`PowerIntroHistory` persists one marker per non-time power, including across
death and power reassignment. It is saved before the first charge effect, so
relogging, removing and regranting, or calling the natural awakening callback
twice cannot repeatedly trigger terrain destruction or free healing. Time
retains its existing persistent marker. Interrupted started intros are consumed.

Owners are protected during the sequence (except from `/kill`) and regular ability
actions wait until it finishes. One subsequent fall is cushioned, with a thirty-second expiry.
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

The isolated server suite includes the rescues (`RescueGameTests`: a speedster
running an ally out, a flyer carrying one into the sky, Flight staying asleep under a
roof, and allies out of reach or still standing going unanswered), first-grant and dormant-unlock routing,
duplicate callback handling, Quake charge timing, actual stone destruction,
radius limits, bedrock, footing, canceled block breaks, persisted history,
one-time regrants, interrupted charges, regeneration and nearby healing, real
Flight Skyburst, non-burning cinematic lightning, and actual telekinetic item
levitation without item duplication or changed gravity flags. Existing rewind
and city-trip tests run alongside them. Client geometry checks exercise finite
mesh output, bounds, UV/color validity, expiry and all effect payload codecs.
These checks do not replace an in-game viewing/listening pass for the new intros.
