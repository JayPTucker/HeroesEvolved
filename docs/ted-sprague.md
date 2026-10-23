# Ted Sprague: induced radioactivity

Design sources: [Ted Sprague](https://heroes.fandom.com/wiki/Ted_Sprague) and
[Radiation Emission](https://heroes.fandom.com/wiki/Radiation_Emission). The
adaptation centers on heat, toxic radiation, bright emissions, EMPs, injury-driven
loss of control and immunity to his own emissions. Peter's borrowed-power
mechanics have been removed from this power. The Telepathy future-city storyline
is unchanged. The numeric ranges, timers and protections below are game design
choices, including the requested protected first awakening.

| Level | Action | Behavior |
| --- | --- | --- |
| 1 | Radiant Heat | Burns and irradiates a creature up to ten blocks away. Aiming at terrain boils source water, thaws ice, and melts iron doors, trapdoors or bars. Terrain permissions and protection events apply. |
| 2 | Radiation Pulse | Nearby creatures suffer radiation sickness and direct damage. Shift uses bright emission instead, blinding without poisoning them. |
| 3 | Electromagnetic Pulse | Suppresses weak and direct redstone signals within sixteen blocks for ten seconds, then refreshes circuits. Lamps, doors and circuits recover; block entities and inventories are never replaced. |
| 4 | Controlled Emission | Vents accumulated stress through a focused heat beam, retaining the extended forty-eight-block reach. |
| 5 | Nuclear Detonation | Three-second voluntary charge. Shift cancels it while control remains. Once an involuntary meltdown begins, Shift cannot cancel it. |

## Awakening and stress

The first unlocked Radiation acquisition starts a thirty-second uncontrolled
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
at one point per second. At sixty percent radiation leaks onto nearby creatures.
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
The damage/visual front reaches its radius in eight ticks; terrain work begins
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
The mushroom cloud has an incandescent core, a connected rising stem and a
broad cap of billowing smoke. A custom soft smoke texture replaces the old
overlapping sphere meshes, using at most 256 vertices per cloud, fewer at a
distance or on Minimal particles. Up to four clouds can render at once. The
awakening cloud is scaled to 1.6 times the normal cloud's size. Its origin stays
1.5 blocks above the exact original detonation position, even when the owner
moves. The duplicate particle column and sphere-based dust pass are removed;
the fast ground shockwave remains. The Time Manipulation cinematic retains its
existing cloud geometry. Rebuild the smoke sprite with
`tools/generate-nuclear-smoke.py`. Independent packets
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
Radiation's awakening also recognizes the Glowing Man visions for its owner
and nearby witnesses who have progressed the Telepathy/city storyline.

## Verification

The isolated server suite checks the complete first awakening, lethal damage
protection and its expiration, uncancellable first charge, harmful radiation
against undead, a real forty-block terrain hit, protected blocks and bedrock,
injury stress, involuntary meltdown, self-immunity, fallout entry/boundary/expiry,
redstone shutdown/recovery, water/ice/metal heat interactions, bright versus
toxic emissions, voluntary cancellation and retained stress on disconnect.
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
