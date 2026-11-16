# First-acquisition power intros

Every power has a distinct animated introduction when first unlocked.
Grants, admin replacements, the Formula and natural dormant awakenings use the
same acquisition hook. Assigning a dormant power alone does not run an intro.
Existing unlocked non-time powers do not suddenly fire destructive intros on login.

A natural awakening only happens when someone is about to die of something the power
can do something about: the owner themself, or an ally near them (see `eclipse.md`).
That person is the intro's **ward**, and every release is built to save them. Each
release first grants the owner, and the ward, a second wind (at least 40% health, six
seconds of Resistance II, fire out, air back, fall forgotten), then ends the danger in
the power's own way. "Threats" below are monsters near the ward, anything targeting
the ward or the owner, and whoever last hurt either of them, another player included.

## What wakes each power

| Danger | What counts | Who answers it |
| --- | --- | --- |
| A lost fight | In a fight (the last ten seconds) and under two hearts, or a blow that would take them there | Every power (Flight only under open sky) |
| A blast | A creeper with its fuse more than 40% burnt, or primed TNT with two seconds or less left, whose blast (worked out the vanilla way: distance, cover, armor, difficulty) would take them under two hearts | Every power |
| A fall | Falling, with the landing half a second away and hard enough to take them under two hearts | Flight, Telekinesis, Cryokinesis, Matter Assembly, Hydrokinesis, Regeneration, Space-Time |
| Fire | In lava and under six hearts, or burning and under three | Cryokinesis, Hydrokinesis, Pyrokinesis, Regeneration, Space-Time |
| Drowning | Out of air underwater and under four hearts | Hydrokinesis, Regeneration, Space-Time |
| Anything else | A player under two hearts who was hurt by anything in the last ten seconds | Space-Time only |

A power that can't help doesn't wake: an electrokinetic watching a friend fall sleeps on,
and so does a speedster whose friend is in lava. Any blow from a danger a power answers
that would take someone under the line wakes the power before it lands, and is capped
so it can't kill. Space-Time answers allies up to 20 blocks away; reaches for the rest
are below and in `eclipse.md`.

## The moment it wakes

Nobody decides to awaken. While the power charges it has the owner, not the other way
round: they are rooted where they stand (Radiation's thirty-second burn lets them
stagger), the heartbeat quickens toward the release, and the ground under them shakes
harder as it comes. In a danger awakening the edges of sight pulse red, and the danger
itself is held off so the release still lands on it (`Danger.hold`):

- every threat within twelve blocks of the owner and the ward is dragged to a crawl
  (Slowness VI, Weakness III);
- creeper fuses stop short of going off and burn back down, and primed TNT stops counting;
- shots in flight at either of them slow;
- someone falling is caught in mid-air and hangs there until the power lets go.

After the power's own release, the danger itself is answered (`intro/PerilRelease`):

| Danger | How it ends |
| --- | --- |
| A blast | Telekinesis throws creepers and TNT at the sky. Cryokinesis locks creepers in ice with their fuse run back and snuffs TNT. Sonic Resonance screams them too far away to matter. Phasing lets the blast go through. Superspeed and Flight carry the ward out and let it go off behind them. Every other power runs the creepers' fuses back and shoves TNT clear with time added. |
| A fall | Telekinesis holds them and lets them down slowly. Cryokinesis lays powder snow where they were going to land, Hydrokinesis a pool, Matter Assembly a copper platform under their feet. Flight swoops in and carries them. |
| Fire | Cryokinesis and Hydrokinesis turn the lava within four blocks to obsidian (cobblestone where it flowed), put out the fire, and stand the ward on top. Pyrokinesis gives a minute of fire immunity ("it does not burn you") and lifts them out of lava; Regeneration gives ten seconds of it. |
| Drowning | Hydrokinesis gives water breathing and Dolphin's Grace and carries them up; Regeneration a breath and ten seconds of water breathing. |

## After the burst: the surge

A fight isn't over because the release is. When a power woke for a lost fight (not a rescue,
a blast, a fall, fire or water), it keeps fighting through its owner for about four seconds
once the intro ends (`intro/Surge`). The owner only watches: their body turns to each attacker
in turn, the edges of sight pulse red, and the power's own moves come out.

- **Powers with a fighting style** (Pyrokinesis, Electrokinesis, Hydrokinesis, Quake,
  Cryokinesis, Phasing, Invisibility, Telekinesis) lunge across to the nearest
  attacker within 24 blocks and throw that style's real combo at them, three strikes, then turn
  to the next: firebending's jabs and kicks, a boxer's sparks, waterbending's whips, Pressure
  Palm, the Ice Skater, Ghost Fist, Force Palm, the Puppeteer.
- **The rest fire their own kit**, one move at each attacker in turn: Sonic Resonance four
  tapped Cries, Telepathy a Nightmare and two Suggestions, Matter Assembly three Shard Lances and
  a Cage, Technopathy an Intercept and a Tripwire, Shapeshifting a Decoy and a Reflection.
- Stamina is topped up while it lasts, and nothing is paid or put on cooldown. When it lets go,
  an action-bar line says so and control comes back. Superspeed, Flight and Vibe (whose releases
  carry the ward out), Regeneration (whose Reckless Brawler pays in blood, right after the heal), Radiation, Empathic Mimicry and Space-Time have no surge.

## Space-Time: time stops

A dormant traveller does not open with the Odessa vision when someone is dying. When
anyone within 20 blocks (or the traveller) is under two hearts and still being hurt, or a
creeper or TNT is about to kill someone, **time stops** round the one in danger for eight
seconds (`intro/TimeAwakening`, `TimeStopService.awaken`). The field is centred on them,
whatever the traveller's level; everything in it hangs where it is, fuses included, and
only the traveller can move. Blows they land on frozen things are stored and land when it
ends, as in Still World. A traveller who was falling hangs in the air while it lasts.

When time starts again, the creepers' fuses have run back to nothing, primed TNT has four
seconds again, monsters round the ward have lost their target and fall a heartbeat behind
(Slowness VII and Weakness II for five seconds), and the owner and ward get their second
wind (with fire immunity or water breathing if that was the danger). Then the vision
starts, as it always has. The reflexive time slow that a grant or the Formula opens with
is skipped. Logging out during the stop ends it; the vision then waits for the next login.

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
| Telepathy | A psychic shout into every mind attacking them: lost target, slowed, weakened and confused, and each one turned on the nearest of the others. |
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
| Time Manipulation | Time stops round the ward first (above); they are shielded through the stop and the whole vision, and on a completed return they get the second wind again and the monsters round them fall a heartbeat behind. |

## Each power's release

| Power | Charge | Release |
| --- | --- | --- |
| Superspeed | Under a second of golden sparks while every threat slows to a crawl. | The rescue run above: out of any cave and far across the surface, carrying the ward. |
| Invisibility | Two seconds of the owner's outline dissolving into violet and silver strips. | A shattering optic burst. The owner fades for five seconds, every threat targeting them loses them, a burst of force shoves everything within six blocks away (3 damage), and they get Speed II to slip away. |
| Phasing | Two seconds of ghostly strands as the floor turns thin. | Concentric density ripples. Five seconds of full intangibility, and the ripple shoves threats away. |
| Empathic Mimicry | Two and a half seconds of gold threads to every heartbeat nearby. | A nearby power is remembered if one exists; threats within eight blocks are weakened and pushed back while their strength drains into the owner (Strength I). |
| Telepathy | Three seconds of growing whispers while nearby minds light up. | Every mind within 24 blocks glows; hostile minds within twelve lose their target, are slowed, weakened and confused, and turn on the nearest of each other. |
| Cryokinesis | Three seconds of frost creeping outward and snow settling. | A crown of ice spikes and the large awakening cold dome freezes everything around the owner. |
| Radiation | Thirty seconds of uncontrolled heat, during which the owner is shielded and the air around them is lethal. | A nuclear shockwave; the ground stays radioactive. |
| Sonic Resonance | Two seconds of vibrating air. | A full-strength sonic burst that throws everything within fourteen blocks back and turns incoming shots; threats within eight take 6 damage and reel (Nausea). |
| Pyrokinesis | Two and a half seconds of smoke and heat. | A ring of fire bursts outward, burning and throwing back threats within eight blocks. |
| Quake | Three seconds of increasing tremor, inward pressure rings, and energy gathering around both hands. | A spherical terrain fracture advances to twelve blocks over two seconds, with three pressure fronts, radial streaks, block fragments, debris, a sonic boom and camera shake. |
| Flight | One second of wind gathering underfoot and spiralling up the body (only under open sky). | The airlift above: a swoop to the ward, a Skyhook carry and a straight climb away at Skyburst speed, ending in real flight. |
| Electrokinesis | Two and a half seconds of electrical arcs feeding a rotating blue/white corona. | A central lightning strike, three surrounding follow-up strikes, a bolt into each of up to eight threats within twelve blocks (6 damage each), expanding electrical rings and a discharge that repels and damages nearby enemies. |
| Telekinesis | Two seconds of inward motes and a concentrating violet field. | Nearby mobs and loose items briefly levitate, cosmetic stones rise from the ground, then everything is pushed outward: threats are hurled hard (5 damage), everything else is set aside with a brief slow fall. Primed TNT is lifted too. |
| Regeneration | Two and a half seconds of wounds healing amid red-to-gold particles. | Full health, harmful-status removal, extinguished fire, and a healing bloom reaching non-hostile living entities within eight blocks. |
| Time Manipulation | In a danger awakening, eight seconds of stopped time first (above). Then a walkable vision of Odessa in its last half-minute. The owner is shielded while the city is prepared. | Sprague's glow, the sirens and the panicking crowd, a 3D fireball and mushroom cloud, and a shock wave that wrecks the streets on its way to you; a few blocks short, time stops and the whole explosion runs backwards. See `time-manipulation-intro.md`. On a completed return home the owner gets the second wind, and nearby monsters lose their target and are left a heartbeat behind (heavily slowed and weakened for five seconds). |
| Technopathy | Cyan diagnostic routes resolve around the owner while nearby mechanisms begin to make sense. | A circuit scan and hostile-projectile rejection pulse, and a diagnostic lock on every threat within twelve blocks (Slowness III, glowing for the owner) with shots in flight dropped; no free network mastery or Evolved trial progress. |
| Matter Assembly | Amber construction lines and fragments assemble around the owner's hands. | A recoil pulse shoves threats back, then a copper lattice rises round the owner for eight seconds. Paid construction starts with the actual ability keys. |

Each new composition has its own charge and release particle kinds. Primary
cues remain visible on Minimal particles; richer filaments, motes and layered
fronts scale with the particle setting. The shared 768-particle cap remains.
Captions go to chat, one line each with the subtitle in grey, and a caption repeated within ten seconds is skipped; nothing is drawn over the scene. Existing flight/time audio and
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

The isolated server suite includes the dangers (`PerilGameTests`: a creeper about to blow stops time and holds its fuse, Space-Time answers a player under two hearts that regeneration ignores, a telekinetic catches a deadly fall that electrokinesis ignores, a cryokinetic turns the lava round a burning ally to obsidian), the rescues (`RescueGameTests`: a speedster
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
