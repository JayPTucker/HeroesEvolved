# Pyrokinesis and Helixite

Character sources: [Meredith Gordon](https://heroes.fandom.com/wiki/Meredith_Gordon),
[Flint Gordon](https://heroes.fandom.com/wiki/Flint_Gordon) and
[Pyrokinesis](https://heroes.fandom.com/wiki/Pyrokinesis). Meredith's orange fire is the base
power. Flint's hotter blue flame is its high-end state. Numbers are configurable starting values.

Pyrokinesis is the thirteenth unique power. It uses the normal one-owner rule, Eclipse assignment,
awakening, mastery and skill points.

## Passive

- **Fire can't hurt you.** Burning, fire blocks and fireballs do nothing. From **level 3**, lava
  can't hurt you either.
- **Burning Touch (level 2):** your punches set targets on fire.
- **Heat (0–100):** using fire builds Heat. It starts cooling three seconds after you stop.
  At **80 Heat** your flames turn **blue** and deal 50% more damage. Water and rain reset Heat to zero.
- **Fuel and weakness:** standing in fire or lava restores 3 stamina per second. Water and rain cost
  2 stamina per second. Cold damage, including Cryokinesis, hurts you 50% more.
- **Contained fire:** crouch while casting and creatures still burn, but no fire blocks are placed.

## Actions

The base kit combines sustained flight, aimed fire constructs and Heat-powered attacks. Action IDs,
skill purchases and equipped loadouts carry over.
These are the default keys, and each action can also be selected from the ability wheel.

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Cinder Spear** | Tap for a 32-block fire bolt or hold up to 1 s for a piercing spear. A full charge can spend 20 Heat to widen the strike. Burning enemies can detonate and scorch others nearby. A fully charged spear also primes armed Ember Mines along its path. | 14 / 1.25 s |
| H | 1 | **Dragon's Breath** | Hold a sweeping flame cone for up to 5 s. It pushes enemies back and builds Heat, extending its reach from 6 to 10 blocks. Aim at your Firestorm to feed it. Works during Flame Flight. | 12 / 4 s, then 3 stamina every 5 ticks |
| Z | 2 | **Firestorm** | Mark the ground for 0.6 s, then raise a radius-4 fire circle for 8 s. It draws in burning foes and consumes exposed hostile shots. Breath widens it to 5.5 blocks and makes it burn faster. Shift + Z collapses the circle after a short warning. Heat and a fanned circle strengthen the blast. | 35 / 15 s |
| X | 3 | **Flame Flight** | Toggle flight with this key or double-tap Jump. Scroll for throttle, then hold Forward to cruise in a streamlined flying pose with banking. Release to brake into a hover; toggle again for a soft landing. While airborne, Shift + X spends 16 stamina to dive into a fiery impact. | 25 / 5 s to start; 6 stamina/s hovering, 10 stamina/s moving or boosting |
| V | 5 | **Sunfall** | Hold up to 3 s to grow a miniature sun above the locked target. Release to drop it onto the marked area. Longer charges widen the impact up to 10 blocks. Stored Heat fuels the blast, and blue Heat makes it hotter. Shift + V cancels the charge. | 75 / 48 s with default config and shared balance |

## Flame Flight and Heat

- **X or double-tap Jump** toggles Flame Flight. Double-tap Jump always selects the base flight
  action even if a learned technique occupies X.
- **Scroll the mouse wheel** to choose throttle, like Flight. Level 3 unlocks settings 0–2,
  level 4 unlocks 0–3, and level 5 unlocks 0–4. Setting 0 allows slow positioning; release movement to hover. Flight starts at setting 1.
- **WASD** steers relative to your view. Hold Forward to cruise along your aim, including climbs
  and dives. Release movement to brake into a hover. Cruise eases into a streamlined flying pose
  and banks smoothly through turns while the camera stays level.
- **Space** rises; **Shift** descends. Flames stream from both hands and feet in hover and cruise.
- The on-screen throttle, Momentum and Heat readouts show your selected speed and reserves.
- **Hover control improves with level:** level 3 gently sways and bobs, level 4 has less drift,
  and level 5 holds steady. The drift is smooth and fades while steering.
- **Sprint while holding Forward** ignites the afterburner, paying **8 Heat up front** for each
  second of boost. It reaches full thrust in 5 ticks and can approach 5.8 blocks per tick at maximum
  throttle with banked Momentum. Attacks build
  Heat, so fighting replenishes fuel for another burst of speed. Moving or boosting costs 10
  stamina per second; hovering costs 6.
- **Momentum (0–100)** builds from real forward travel at a cruise throttle. Faster settings and
  boosting build it faster; hovering lets it bleed away. Pushing into a wall does not build it.
  Momentum adds to afterburner speed and is spent on a wider, harder Meteor Dive.
- **X** ends flight. **Shift + X** while airborne starts **Meteor Dive**. Hang for 0.4 s as flame folds
  inward, then steer the accelerating plunge. On contact, flame washes out in a circle and nearby
  players feel the impact. Exposed enemies burn and get thrown clear.
- Stopping flight, running out of stamina, or being quenched by water or rain gives **6 seconds
  of Slow Falling**. Death, logout, dimension changes and loss of the power clear flight state.
- **Pillar of Flame** reduces hover drain to 4 stamina per second and strengthens vertical thrust.

Place Firestorm, then feed it with Dragon's Breath to gather a wider group. Collapse the fed circle
or take off, afterburn around the group and dive back into it. For a ranged setup, plant Ember Mines
along a route and light them from a distance with Blue Lance or a fully charged Cinder Spear.

## Feel and effects

- Orange fire rolls through red, orange and gold. At blue Heat the same tongues have indigo edges
  and lighter blue centers. Cinder Spear gathers at the casting hand, then a fireball pulls a curling
  trail out along the aim. Dragon's Breath rolls outward in overlapping tongues of flame.
- Firestorm marks its footprint before raising a turning wall of fire. Collapse folds the wall inward,
  then throws flame back across its footprint. Sunfall burns above the warning ring, accelerates
  into it, then sends a column and a wave across the marked area.
- In flight the flame streams follow your palms and boots as you bank. The afterburner opens a longer
  exhaust and leaves a fading wake along your path. Meteor Dive gathers a collar of flame before it
  releases. The flight camera stays level.
- Ember Lash curls from hand to target as it reels. The Mantle turns around the caster, and mines mark
  their blast radius when triggered. Cauterizing Flame climbs around its recipient. Fire Shepherd draws
  streams from fires and rescued allies into the caster's palm. The Phoenix folds its wings on a dive.

## Real fire

Orange flames use normal Minecraft fire placement. Blue flames leave vanilla Soul Fire wherever
regular fire could stay. Soul Fire deals twice the contact damage of regular fire and does not spread.

- Every fire block goes through the same checks as a normal block placement: build permission,
  spawn protection, claim and protection mods, world border, and loaded chunks.
- Regular fire can spread and burn wood according to the vanilla `doFireTick` gamerule.
- The Odessa vision and Temporal Snapshot protect their terrain from placed fire.
- **Rewrite the Moment** records the burned blocks, so a time traveller can undo a fire.
- Server owners can make all pyrokinetic fire cosmetic with `pyrokinesis.worldFire = false` in the
  common config. Creatures still burn, but no blocks catch.

## Skill tree (18 nodes)

The six active techniques use distinct gestures and interact with Heat and the base kit. The opening
and mastery nodes now improve specific maneuvers. Only one capstone can be learned at a time.

| Branch | Active skill | Technique → upgrade → capstone |
| --- | --- | --- |
| **Kindler** | **Ember Lash** sets a foe burning and reels them toward you until they reach three blocks or break cover. **Kindling** grants 4 extra Heat when a Cinder Spear hits. **Flashpoint** refunds 8 Heat per cast that detonates a burning enemy. | **Ember Mine** hides two embers. Foes, charged Cinder Spear and Blue Lance can prime them. **Long Fuse** raises the limit to three and radius to four. **Wildfire** leaves real fire and primes nearby mines in sight, each with its own warning. |
| **Blue Flame** | **Heat Mantle** grants Resistance and burns exposed foes and hostile projectiles. Each consumed shot feeds 2 Heat, capped at 8 per pulse. **Stoke** grants 2 extra Heat per successful Breath pulse. **Furnace** doubles the time a blue Breath keeps Firestorm fanned. | **Blue Lance** pierces 24 blocks and leaves a brief damaging seam. It primes armed mines along its path. **White Heat** lowers the blue threshold to 60. **Flint's Fury** makes Sunfall blue and three blocks wider. |
| **Phoenix** | **Cauterizing Flame** heals, cleanses and extinguishes a friend in sight or yourself. Spend Heat for absorption. **Warm Blood** generates 5 Heat per second in fire or lava. **Hearthkeeper** halves the absorption cost and banks Heat when Fire Shepherd rescues burning friends. | **Fire Shepherd** extinguishes nearby fires for stamina, health and Heat, and helps burning friends. **Thaw** clears nearby friends' cold effects. **Pillar of Flame** strengthens vertical flight thrust and reduces hover drain. |

Mines remain private while dormant; their warning and eruption become visible to everyone when
triggered. Blue Lance's drilling seam stays on its original line rather than following a changing
aim. Ember Lash visibly reels its target instead of ending with a single shove.

### Signature: Phoenix

The Phoenix branch's signature ability hangs under **Hearthkeeper** (3 points,
55 stamina, 30 s cooldown). The fire takes wing.

A firebird tears itself out of your chest and climbs: a white-hot body, wings whose leading edges
burn brightest with tongues of flame trailing off them as flight feathers, a crest, and three long
plumes of tail that ripple as it flies, shedding embers. It wheels once over your head, then hunts
enemies it can see within 24 blocks, favoring burning targets. Each dive strikes for 7 + level fire damage, sets the
target burning for six seconds, throws it along the dive and lights real fire where it stood (subject
to the same fire rules as the rest of the kit). Anything else it brushes past on the way is singed.
It pulls up and dives at the next one: three in all, or four at tier 5. Cast at blue Heat for blue
wings and stronger dives.

When it returns, it heals 2 health plus 2 per strike, gives 5 Heat plus 5 per strike and grants
ten seconds of Fire Resistance. Water smothers it and solid cover can interrupt its hunt.

## Reactions

| Reaction | How | Result |
| --- | --- | --- |
| **Thermal Shock** | Any pyrokinetic hit on an enemy a Cryokinetic has frozen solid | Uses up the freeze. A steam explosion deals 10 damage (4 to players) and blinds anything within 3 blocks. |
| **Steam Veil** | Cinder Spear or Dragon's Breath hits water, ice or snow, including Cryo's ice walls and paths | A steam cloud for 6 s. Mobs can't target anyone inside it, and mobs inside can't see out. |
| **Firestorm** | A Flight **Tempest** or Superspeed **Whirlwind** passes through a Firestorm circle | The vortex catches fire for 5 s, igniting and damaging everything nearby. |

Each reaction gives two training points the first time you discover it, the same as the existing reactions.

## Awakening

*Something is burning*: for 2.5 seconds your hands smoulder while a flame helix rises around you.
Then **FLASHPOINT**: two rings of real fire burst outward, at radii 5 and 8, and nearby enemies are
thrown back and set alight. You're protected throughout, and the intro only ever plays once.

Sylar can learn Pyrokinesis. His copied version throws fire bolts that set you alight.

## Helixite

Helixite is a new crystal ore and the mineral the [Formula](formula.md) laboratory is built from:
two shards for the Centrifuge, one for the Synthesis Station, and one for each batch of Unstable Compound.

- **Where:** the Overworld, **Y −64 to 0**. Veins are most common around **Y −32**, nearly always
  in deepslate.
- **What it looks like:** teal-and-violet crystal clusters. The ore **glows faintly** (light
  level 3), so you can spot it in a dark cave.
- **Mining:** iron pickaxe or better. Drops one **Helixite Shard** plus experience. Fortune adds
  more shards; Silk Touch gives the ore block.
- **How rare:** two vein attempts per chunk, of up to four blocks, and veins exposed to air are
  often discarded. That works out to **about a quarter as common as diamonds**. An hour of
  deepslate strip-mining should find roughly 6–12 shards.

That rarity is deliberate. Building the two lab blocks should feel like a real milestone, and each
Formula should cost something, without needing an endgame grind.

Regenerate the textures with `python3 tools/generate-helixite-textures.py`.

## Verification

Game tests cover aimed casts, charge cancellation, the Firestorm warning and Breath interaction,
Sunfall's locked impact, and the collision sweep for Meteor Dive. They also check flight permissions,
fuel, input expiry, braking, support effects and the mine warning.

The isolated live-client preview checks the real flight key, wheel-selected cruise, pitched
climbing, banking, real-travel Momentum, wheel braking, steady level-five hover and gentle
level-three drift. It also checks double-tap Jump, Cinder Spear hits and Shift + flight-key
Meteor Dive impact, and captures the poses, flame trails, Cinder Spear, Sunfall and Meteor Dive. Run it with
`./gradlew runClient --offline --init-script tools/pyro-preview.init.gradle` under Java 21.
