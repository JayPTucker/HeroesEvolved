# Induced Radioactivity

A reactor you run hot. Induced Radioactivity is heat, a radiation sickness that spreads through a crowd,
electromagnetic pulses and, at the top, a nuclear blast. Design source:
[Radiation Emission](https://heroes.fandom.com/wiki/Radiation_Emission). The numbers below are game design
choices.

## Core: the hotter you run, the stronger you are

Core (the stored "stress") builds as you use your power and as you are hurt. It is fuel as well as a danger.
The HUD shows a **Core** bar under your ability list, with notches at each threshold.

| Core | State | What it does |
| --- | --- | --- |
| 0–39 | Stable | Normal strength. |
| 40–79 | **Hot** | Radiation powers deal **25% more damage**, and Radiant Heat sets what it hits alight. A Geiger counter crackles around you. |
| 60+ | **Glowing** | You glow orange, and radiation leaks onto **enemies** within 3 blocks (never allies). |
| 80–99 | **Critical** | **50% more damage**; Radiant Heat and a Fission Leap slam give two stages of sickness, and the heat burns longer. |
| 100 | Meltdown | A three-second warning: vent (Meltdown Beam, EMP or Critical Mass) or containment fails. |

**Radiation Sickness** is a stacking dose in five stages: each second it burns for half a heart per stage
(armor does not help, and the undead are not spared) and saps attack strength. Whoever holds Induced
Radioactivity is immune to it. A creature that dies while sick **bursts**, damaging and dosing everything
around it, so radiation spreads through a crowd as a chain reaction.

## Abilities

| Key | Level | Action | What it does |
| --- | --- | --- | --- |
| G | 1 | **Radiant Heat** | A white-hot **cone** from your palms burns, doses and **throws back** up to six enemies within 8 blocks, harder the closer they stand. Once you run Hot it sets them alight (longer when Critical). With nothing in reach, the heat works on terrain: it boils source water, thaws ice and melts iron doors, trapdoors or bars (permissions and protection events apply). +6 Core. |
| H | 2 | **Fission Leap** | A blast under your feet **launches you** about ten blocks up and forward; the shock where you left throws and doses enemies within 3.5 blocks. **Press again in the air to slam** down along your aim (always at least a steep dive): you land in a bigger blast (4 + level / 2 blocks) that throws and doses everything around and leaves the ground **contaminated for 8 seconds**, so anyone who walks in later keeps getting dosed. The second press is free. You take no fall damage from a leap. +8 Core for each blast. |
| Z | 3 | **EMP** | Kills redstone within 16 blocks for 10 seconds and powered machines too: Forge Energy machines (Mekanism, Thermal...) are drained and stop, and Create's shafts seize (see [Powers and machines](machines.md)). Its front **shoves enemies within 10 blocks away**, slowed, weakened and revealed. It **vents 25 Core**: your panic button. |
| X | 4 | **Meltdown Beam** | **Hold** to pour Core out through a thick, piercing heat beam (48-block reach) that you sweep with your aim. Each pulse vents 3 Core and hits everything on the line harder the more Core is left; it ends when you let go, run cool, or after five seconds. Needs 10 Core to start. **Shift** stops it at once. |
| V | 5 | **Critical Mass** | You **rise off the ground**, glowing brighter, for **three seconds**, then detonate: the nuclear blast below. The charge is long on purpose, so others can stop you: Shift cancels it, and **taking 8 damage while charging breaks it**. An involuntary meltdown cannot be cancelled or broken. |

A good sequence: **Radiant Heat to build Core → Fission Leap into a crowd → Meltdown Beam from the air →
slam → EMP to vent** (or let the Core climb and finish with Critical Mass).

Action IDs are unchanged (`radiation_pulse` is Fission Leap, `radiation_meltdown_beam` the beam,
`radiation_exploding_man` Critical Mass), so saved loadouts, balance and config entries still apply.

## Skill tree

Three branches of six, built around the kit: every node changes how a move plays or ties two moves
together, and none is a flat stat bonus. Purchases from the older tree carry over (the node IDs are
unchanged). Only one capstone can be learned at a time.

| Branch | Passive | Active | Passive | Technique | Upgrade | Capstone |
| --- | --- | --- | --- | --- | --- | --- |
| **Meltdown** (heat and the beam) | **Searing Palms**: Radiant Heat reaches 11 blocks and throws harder; three or more enemies in one cone build twice the Core. | **Gamma Lance**: a lance pierces everything on a line up to 40 blocks for 8 + level and a stage of sickness. With 15 Core it spends it and hits twice as hard with two stages. | **Focused Beam**: Meltdown Beam deals 30% more damage. | **Heat Vent**: dump up to 30 Core into a roaring 9-block cone that burns harder the more you vent, ignites and throws. | **Chain Vent**: every sick enemy Heat Vent hits bursts as if it had died, spreading the sickness. | **Supercritical**: while Critical, every enemy you kill bursts in a small blast and feeds you 5 Core. |
| **Reactor** (running Core) | **Hot Start**: Core cools half as fast while you rest. | **Lead Skin**: Resistance II for 8 seconds, and melee attackers are dosed. | **Safe Venting**: EMP vents 40 Core and turns half into stamina; each Meltdown Beam pulse restores a point of stamina. | **Reactor Core**: turn up to 20 Core into stamina and give friends within 6 blocks a burst. | **Power Transfer**: up to 40 Core, and friends you power dig faster. | **Breeder Reactor**: once a minute, reaching 100 Core gives ten seconds of **Overdrive** (radiation hits twice as hard) instead of failing. Vent before it ends. |
| **Fallout** (the leap and contaminated ground) | **Fallout Boots**: Fission Leap's launch contaminates the ground you left for 5 seconds. | **Decontaminate**: purge harmful effects and flames from you and friends within 8 blocks, then Fire Resistance. | **Second Leap**: Fission Leap can launch once more in mid-air before you slam. | **Glowing Path**: for 8 seconds you leave contaminated ground wherever you walk. | **Half-Life**: your contaminated ground lasts twice as long and doses two stages at a time. | **Catastrophe**: a slam into ground you contaminated sets it off, 60% wider and half again as strong, leaving a bigger patch. |

Builds to try: **Fallout** for a leap-and-slam brawler (Fallout Boots → Second Leap → slam into your own
patch for a Catastrophe); **Meltdown** for running Critical and chaining kills (Heat Vent the sick, then
Supercritical bursts); **Reactor** to live at 100 Core in Overdrive and vent it into stamina.

## Look and feel

Every move is drawn in 3D on every nearby client (`client/effects/setpiece/RadiationArt`), in three colours:
**heat** is orange going white-hot, **sickness** is a sickly yellow-green, and the **EMP** is a cold electric blue.

- **Radiant Heat:** the palms flare white, rings of heat race out and widen into the cone one after another,
  rays of white heat flicker along it, embers are carried on, and thin haze peels off its end.
- **Fission Leap:** each blast is the same shape at two sizes: a white flash, a hot shock ring racing over the
  ground with a green ring of sickness behind it, heat cracks running out, a ball of fire boiling up into smoke,
  dust rolling out and stones thrown. The launch adds a column of thrust up your path; the slam adds a pillar of
  light where you struck and a green pool where the ground is contaminated.
- **Contaminated ground:** a sickly glow with a slowly turning edge, motes rising out of it and bubbles of light
  popping on it.
- **EMP:** a pale blue shell bursts out of you, a ring races over the ground to the pulse's edge with current
  crackling along it, and arcs reach up from you in the first moments.
- **Meltdown Beam:** a wide orange glow, a hot body and a white core, rings of heat running along it, and
  smoke where it lands.
- **Critical Mass:** you swell with light as you rise, rings of heat collapse in on you faster and faster, the
  ground under you cracks and glows, and dust lifts off it. Then the blast below.

## Awakening and stress

The first unlocked acquisition of the power starts a thirty-second uncontrolled
charge. Ability inputs and direct attempts to vent or calm down cannot stop it.
Damage and death protection last through the intro's release. Every half-second,
creatures within eight blocks receive radiation sickness and direct radiation
damage, including undead creatures that resist poison. The owner is excluded.
The charge ends in the nuclear blast, then settles with forty percent stress.
The existing acquisition history prevents removal/regrant from repeating it.
Started intros retain the existing interruption behavior on logout, dimension
change, power removal or Eclipse suppression.

Afterward, actual health damage adds three stress per point, capped at one
hundred. Actions also raise stress. After five seconds without injury, it cools
at one point per second. At sixty percent radiation leaks onto nearby enemies.
At one hundred, a three-second warning gives a chance to vent, followed by a
three-second involuntary meltdown. Stress is serialized with the player;
disconnecting does not erase it. Full stress rearms the warning when play resumes.
Death or power removal resets it. The first-intro protection expires normally:
ordinary attacks can kill an experienced user.

## Glow and audio

An additive, full-bright orange emission mask follows the player's actual
animated model, including slim skins, clothing layers and first-person hands.
The underlying skin stays intact. RGB intensity rises with stress and pulses
gently; charging starts hot and grows brighter. Four dedicated effect kinds
provide heat shimmer, expanding shockwave rings, a rising plume, EMP ripples
and drifting fallout ash.

The original four-second reactor loop combines bass harmonics and a modulated
whine. A moving sound instance raises pitch and volume throughout the charge,
including for observers. Tracking updates initialize late observers; completion,
cancellation, disconnect and dimension changes stop the sound. Identity-checked,
short-lived client state prevents stale sounds or glow after entity ID reuse.
Existing Flight, Time Manipulation and city-intro sound assets are preserved.
Rebuild the original assets with `tools/generate-nuclear-assets.py`.

## Nuclear blast and fallout

The first awakening creates a forty-eight-block spherical blast (ninety-six
blocks across). Later detonations retain a twenty-four-block radius. The first
wave deals 120 health damage at its center, tapering to 32 at its boundary;
later waves deal 80 down to 20, before armor and other normal reductions.
The damage/visual front reaches its radius in five ticks; terrain work begins
after that front and catches up independently. All blasts share a server-wide
limit of 512 block removals and 8,192 scanned positions per tick, with a soft
four-millisecond terrain budget. Expensive individual block callbacks can exceed
that time allowance. A streaming slice cursor clears outward at foot/ground height before deepening the crater
without allocating or sorting the entire sphere when detonation begins. Dense
craters therefore take longer to finish; queued terrain work expires after
three minutes. Silent block writes avoid separate break particles and sounds
for every destroyed block while retaining normal chunk updates. It vaporizes
ordinary blocks, preserves a small footing, ignores fluids and unbreakable
blocks, and respects build permissions, world borders, spawn protection and
canceled block-break events. It never deliberately loads chunks and does not
alter the future-city or Temporal Snapshot dimensions. Normal world writes
remain visible to Time Rewind. Containers use normal removal behavior.

A radioactive volume remains at the detonation's original location for sixty
seconds, damaging creatures that enter after the wave. It is dimension-local
and expires without leaving permanent poison blocks. The owner is immune to
their own wave and fallout, without the old one-health penalty. Fallout from
other sources is still dangerous. Blast/EMP work queues are bounded and cleared
on server shutdown; fallout does not move with a departing owner.

During both the thirty-second awakening and later three-second detonation
charges, nearby ordinary blocks crack, emit smoke, and eventually crumble.
The decay footprint expands with the charge, up to eight blocks for awakening
and six for later charges. At most twenty-four block cracks are active per
owner. It retains the footing, skips containers and fluids, respects protected
blocks, and clears its crack animations on cancellation and cleanup.

All players in the same dimension within three hundred blocks receive ground
shake, including those exactly on the boundary. It lasts six seconds for the
awakening and four seconds for later detonations, with distance falloff.
A blast is centred on the ground under the caster, who has risen a few blocks while charging.
The explosion is drawn as the Odessa blast from the Time Manipulation vision,
scaled to the detonation (`MiniBlast`, see [Classes](classes.md#how-they-are-drawn)). It plays fast: the
flash and fireball snap out in a few ticks and the shock and its dust race out at once, while the mushroom
cloud still rises and rolls over at its own pace:
the white flash, the fireball rolling up into a mushroom cap on a stem of dust,
the dust thrown up at its foot, a pale condensation dome racing out with the
shock and a ring of dust rolling past the blast radius and settling. The billows
are real geometry lit by the sun and their own heat; the cloud thins away over
its last five seconds. The awakening blast (radius 48) is twice the size of a
later one (radius 24). Anyone arriving late sees it at the age it has reached.
The fast ground shockwave remains. Radiation's Offense class ability,
**Ground Zero**, throws a small version of the same blast. Independent packets
reach observers within four hundred blocks, refresh their age, and initialize
players entering the area. Clouds cool into smoke, last thirty seconds and
fade over their final five seconds. Rendering remains visible on Minimal
particles and does not require tracking the owner or changing render settings.

## Unique storyline roles

Every registered power has one permanent owner in the world's persistent
ownership registry. Dormant assignments reserve it immediately; awakening,
admin grants and replacement commands all enforce the same rule. Ownership
survives disconnect, death, power removal and server restart. An original owner
can regain their role, while another player cannot claim it. Eclipse rolls
select only powers available to that player. With all registered roles claimed,
additional players remain unassigned rather than receiving duplicates.

On upgrading an existing world, offline player files are scanned before new
assignments. Existing ownership records take precedence; otherwise duplicate
legacy claims are resolved deterministically by player-file UUID order. A
conflicting player receives an unclaimed dormant role on login if one remains.
The awakening also tells its owner that the heat that burned Odessa has found
a new body, and tells nearby witnesses who have progressed the Telepathy/city storyline.
Worlds that already kept Gabriel Gray from the light in the earlier Avenue episode retain that
outcome. Sylar cannot copy Induced Radioactivity in those worlds. See [Sylar](sylar.md#taking-your-power).

## Verification

`RadiationGameTests` also covers the kit: the cone hits and throws what is in front and not behind,
Critical Core hits harder and burns, doses stack and the caster is immune, a sick creature's death spreads
the sickness, Fission Leap's launch throws and doses what is close and cushions the caster, a slam lands
in a blast that leaves contaminated ground dosing latecomers, the held beam drains Core and stops on
release, a hard enough hit breaks a voluntary charge, and the tree: Gamma Lance pierces a line and hits twice as hard
with Core to spend, Chain Vent bursts the sick, Fallout Boots, Second Leap and Catastrophe change the leap,
Breeder Reactor runs in Overdrive instead of melting down, and Supercritical kills burst and feed Core, and EMP vents Core, staggers enemies and shoves them away. Photograph every power with
`./gradlew runClient --init-script tools/radiation-preview.init.gradle`
(screenshots in `build/radiation-preview/screenshots/`, including the blast frame by frame as
`radiation-8-blast-*.png`).

The isolated server suite checks the complete first awakening, lethal damage
protection and its expiration, uncancellable first charge, harmful radiation
against undead, a real forty-block terrain hit, protected blocks and bedrock,
injury stress, involuntary meltdown, self-immunity, fallout entry/boundary/expiry,
redstone shutdown/recovery, water/ice/metal heat interactions, voluntary
cancellation and retained stress on disconnect.
Existing Cryokinesis, ranged reach, rewind and storyline tests remain included.
Additional checks cover charge decay and cancellation, heavy wave damage,
actual shake delivery at 250 and 300 blocks, exclusion at 300.01, independent
cloud delivery, exclusive dormant/admin claims, saved ownership, offline
migration and roster exhaustion. Fixtures run in separate batches because the
production role ownership applies to test players too.

Performance regression checks cover simultaneous dense craters sharing the
same removal budget, gradual completion without per-block break events, and
cloud heartbeats retaining their original coordinates after the owner moves.
Silent crater writes also restore through Time Rewind, including container
contents without duplicate drops. The complete isolated suite has 42 tests.
The geometry check verifies all crater voxels are visited exactly once at both
blast radii, along with finite smoke geometry, UVs, colors, camera angles,
detail levels and the per-cloud vertex limit. These checks do not measure
client frame rate or guarantee the cost of third-party block callbacks.

`tools/verify-effect-geometry.ps1` also checks wide/slim body and hand meshes,
orange brightness scaling, all forty-eight effect codecs and radiation state
start/stop packets. Asset generation verifies finite audio samples and decoded
headroom. An in-game visual/audio playtest is still needed for subjective glow,
shader compatibility and sound balance.


Camera-anchor correction: the cloud and city cinematic submit camera-relative
world vertices without applying the event view matrix a second time. The active
shader supplies that rotation. Geometry regression checks cover camera movement,
yaw and pitch; server cloud heartbeats retain the original detonation center.
The ground dust skirt reaches full spread in about 1.4 seconds. Terrain advances
outward in complete horizontal slices before progressing deeper, under the shared
512-break / 8,192-scan / soft 4 ms tick budget. Dense craters still deepen gradually.
