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

The base kit combines sustained flight, aimed fire constructs and Heat-powered attacks. Action IDs
and skill unlocks remain compatible with existing loadouts; their displayed names have changed.
These are the default keys, and each action can also be selected from the ability wheel.

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Cinder Spear** | Tap for a 32-block fire bolt; hold up to 1 s for a piercing spear. A full charge can spend 20 Heat for a wider strike. Hitting burning enemies can detonate them and scorch nearby enemies. Quick bolts build 12 Heat and deal 4 + level damage. Water, ice and snow produce steam. | 14 / 1.25 s |
| H | 1 | **Dragon's Breath** | Hold a sweeping flame cone for up to 5 s, pushing enemies back. Heat extends its reach from 6 to 10 blocks. Builds Heat and drains stamina while held; works during Flame Flight. | 12 / 4 s, then 3 stamina every 5 ticks |
| Z | 2 | **Firestorm** | Aim at ground within 16 blocks to place an 8 s, radius-4 fire circle; otherwise it forms at your feet. Burning enemies inside are drawn inward, and hostile projectiles burn up. Shift + Z begins a brief wind-up, then collapses it for 4 + level damage, spending 20 Heat for 4 extra damage. | 35 / 15 s |
| X | 3 | **Flame Flight** | Toggle flight with this key or double-tap Jump. Scroll for throttle, then hold Forward to cruise in a streamlined flying pose with banking. Release to brake into a hover; toggle again for a soft landing. While airborne, Shift + X spends 16 stamina to dive into a fiery impact. | 25 / 5 s to start; 6 stamina/s hovering, 10 stamina/s moving or boosting |
| V | 5 | **Sunfall** | Aim within 24 blocks and hold up to 3 s to form a miniature sun above the chosen spot. Release early for a smaller eruption, or let it erupt at full charge with a 10-block radius. All stored Heat fuels the blast. Blue Heat makes a hotter **Blue Sunfall**. Shift + V cancels. | 88 / 72 s |

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
  second of boost. The flame jets spool up over 8 ticks before reaching full thrust. Attacks build
  Heat, so fighting replenishes fuel for another burst of speed. Moving or boosting costs 10
  stamina per second; hovering costs 6.
- **Momentum (0–100)** builds from real forward travel at a cruise throttle. Faster settings and
  boosting build it faster; hovering lets it bleed away. Pushing into a wall does not build it.
  Momentum adds to afterburner speed and is spent on a wider, harder Meteor Dive.
- **X** ends flight; **Shift + X** while airborne starts **Meteor Dive**. Steer the descent toward
  enemies, then erupt on contact with terrain. The impact damages and ignites enemies without
  breaking terrain.
- Stopping flight, running out of stamina, or being quenched by water or rain gives **6 seconds
  of Slow Falling**. Death, logout, dimension changes and loss of the power clear flight state.
- **Pillar of Flame** reduces hover drain to 4 stamina per second and strengthens vertical thrust.

Open with Dragon's Breath to ignite a group and build Heat. Place Firestorm to draw the burning
enemies together, rise on Flame Flight, then pierce the group with Cinder Spear. Spend Heat to boost
into a new angle, finish the gathered enemies with Meteor Dive, or cast Sunfall over the chosen spot.
The Heat meter and flight controls appear on screen while using the power.

## Real fire

Pyrokinetic fire is ordinary vanilla fire. It spreads, burns wood and can burn down a base.

- Every fire block goes through the same checks as a normal block placement: build permission,
  spawn protection, claim and protection mods, world border, and loaded chunks.
- Whether fire spreads is still decided by the vanilla `doFireTick` gamerule.
- The Odessa vision and Temporal Snapshot protect their terrain from placed fire.
- **Rewrite the Moment** records the burned blocks, so a time traveller can undo a fire.
- Server owners can make all pyrokinetic fire cosmetic with `pyrokinesis.worldFire = false` in the
  common config. Creatures still burn, but no blocks catch.

## Skill tree (18 nodes)

The six active sub-abilities now have distinct gestures and fire compositions, plus mechanical
follow-through that makes them useful beside flight and the base kit. Existing purchases, point
costs and key assignments are preserved. The opening and mastery nodes still grant their original
stat bonuses; this refresh does not replace all eighteen nodes with new interactions. Only one
capstone can be learned at a time.

| Branch | Active skill | Technique → upgrade → capstone |
| --- | --- | --- |
| **Kindler** (−10% / −25% stamina) | **Ember Lash**: a tether lashes a visible enemy within 40 blocks for 6 + level damage and five seconds of burning, then reels them toward you for 0.4 s. It releases at three blocks or when cover breaks the connection. | **Ember Mine**: hide up to two embers on aimed ground for 40 s. Each takes 0.4 s to arm; an approaching enemy triggers a public 0.4 s warning before the eruption → **Long Fuse**: three mines, radius 4 instead of 2.5 → **Wildfire**: eruptions leave real fire and set off nearby mines. |
| **Blue Flame** (+10% / +25% damage) | **Heat Mantle**: rotating flame plates surround you for 8 s, granting Resistance and a burning aura. They consume hostile projectiles within three blocks, feeding 2 Heat per projectile, capped at 8 per quarter-second. Allied shots pass through. | **Blue Lance**: the initial 24-block piercing jet deals blue damage, then leaves a fixed drilling seam for 0.6 s. Its pulses deal 2.5 damage every four ticks, to up to eight enemies per pulse; solid cover stops it → **White Heat**: blue starts at 60 Heat → **Flint's Fury**: Sunfall is always blue and three blocks wider. |
| **Phoenix** (−10% / −25% cooldowns) | **Cauterizing Flame**: seal an injured or afflicted friend's wounds in sight within eight blocks, or your own. Heal 4 + level, clear harmful effects and extinguish flames. With 20 Heat available, spend it for four absorption health lasting 5 s. | **Fire Shepherd**: draw in up to 32 nearby fires, restoring stamina and health plus 2 Heat per fire, capped at 40 Heat. Extinguish burning friends within five blocks and heal them for 2 → **Thaw**: also unfreeze and clear chill from nearby friends → **Pillar of Flame**: stronger vertical flight thrust and reduced hover drain. |

Mines remain private while dormant; their warning and eruption become visible to everyone when
triggered. Blue Lance's drilling seam stays on its original line rather than following a changing
aim. Ember Lash visibly reels its target instead of ending with a single shove.

The animation pass gives the base attacks deliberate gather, sweep, release and recovery gestures.
The sub-abilities use a curling lash, rotating mantle plates, a warm cauterizing spiral, an ember
arming/warning pattern, a focused drilling lance and converging fire trails for Fire Shepherd.

Fire Shepherd makes the pyrokinetic a firefighter as well as a source of fire: reclaim ground
flames to refuel the next boost, and draw fire off a burning companion before returning to combat.

### Signature: Phoenix

The Phoenix branch's signature ability hangs under **Warm Blood Mastery** (Level 10, 3 points,
55 stamina, 30 s cooldown). The fire takes wing.

A firebird tears itself out of your chest and climbs: a white-hot body, wings whose leading edges
burn brightest with tongues of flame trailing off them as flight feathers, a crest, and three long
plumes of tail that ripple as it flies, shedding embers. It wheels once over your head, then hunts
the nearest enemy it can see within 24 blocks. Each dive strikes for 7 + level fire damage, sets the
target burning for six seconds, throws it along the dive and lights real fire where it stood (subject
to the same fire rules as the rest of the kit). Anything else it brushes past on the way is singed.
It pulls up and dives at the next one: three in all, four from Level 15.

When the hunt is over it comes home and folds back into you, healing 2 health plus 2 per strike and
leaving you **Fire Resistant** for ten seconds. Water smothers it; it cannot be released while you are
in water, and it burns out if it flies into some. It is a point with a heading drawn on clients from
one small message a tick, not an entity, and it never breaks blocks.

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

The full game-test suite passes all **335 required tests**, including charged-spearing through
enemies while stopping at walls, remote Sunfall targeting, Firestorm attraction, flight steering,
boost costs, hover braking and level-dependent instability, permission cleanup, level and immobilization gates, and Meteor Dive
collision and impact. Mimicry's borrowed Cinder Spear follows press-and-release input. Additional tests cover Ember
Lash reeling, Heat Mantle fuel and allied projectiles, friendly cauterization and Heat shields,
Ember Mine warning and escape, Blue Lance's lingering seam, and Fire Shepherd rescues.

The isolated live-client preview checks the real flight key, wheel-selected cruise, pitched
climbing, banking, real-travel Momentum, wheel braking, steady level-five hover and gentle
level-three drift. It also checks double-tap Jump, Cinder Spear hits and Shift + flight-key
Meteor Dive impact, and captures the poses, flame trails, Cinder Spear, Sunfall and Meteor Dive. Run it with
`./gradlew runClient --offline --init-script tools/pyro-preview.init.gradle` under Java 21.
