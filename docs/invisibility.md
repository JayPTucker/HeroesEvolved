# Invisibility — light bent and made solid

Invisibility bends light. Bent round a body, it hides you, your friends and the things you touch. Bent hard enough, it turns
solid: **force fields** that block, carry, hold, crush and burst. Every move shares one picture, **a lens of
clear light**, and the moves feed each other: blocked hits charge the Push, the Push sends bubbles rolling, a Nova
punishes anything you are holding and breaks your constructs into shards.

The action IDs and all eighteen skill IDs are kept from the earlier kits, so saved loadouts, purchases,
`power_balance.json` and config keys carry over. Only names, descriptions and mechanics changed.

## Base actions

These are the default keys; learned active skills can replace them as usual.

| Key | Level | Action (ID) | What it does | Stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Fade** (`invisibility_fade`) | Toggle invisibility. Friends (allies, crouching players) and your tamed pets within 3 blocks fade with you while they stay close. Press again or Shift + key to reappear. | 6 / 0.5 s, then 2 a second plus 1 a second per friend sharing it |
| H | 1 | **Lens Shield → Force Push** (`invisibility_silent_step`) | A curved lens of force follows your aim for 3 seconds. It stops projectiles reaching it and cuts harm from the front by 70%; each hit it takes stores a **Pressure** (up to 6). **Press again** (free) to throw it: a wall of force travels 10 blocks at 2.6 blocks a tick, dealing 4 + level + 2 per Pressure and throwing foes harder for each Pressure. Walls stop it. | 14 / 4 s |
| Z | 2 | **Force Bridge** (`invisibility_unseen_strike`) | A walkable span of force, three blocks wide and up to 12 long, runs along the compass direction you face and rises or falls with your look (up to one block a step). Cast while falling, it starts under your feet. It stops where a wall blocks your headroom. You and your friends on it step up its one-block stairs without jumping. Lasts 10 seconds; you can keep two. | 16 / 4 s |
| X | 3 | **Bubble → Crush** (`invisibility_shroud`) | **Aimed at a foe** (up to 20 blocks): seal it in a sphere for 5 seconds. It is held where it was sealed and cannot hurt anyone outside it. **Press again** (free) to crush every foe you hold: 6 + 2 × level + 1.5 per second held. **Aimed at a friend or at nothing**: shelter yourself and up to five friends and pets within 3.5 blocks for 6 seconds; projectiles stop at the shells and other harm from outside is halved. | 25 / 10 s |
| V | 5 | **Force Nova** (`invisibility_ghost_protocol`) | One second of visible wind-up (shells close round you; a ring marks the 12-block reach on the ground), then a burst: up to 12 + 2 × level damage, less toward the edge, and a throw outward. Foes held in bubbles take **double** and are released. Hostile projectiles in range vanish. Your bridges and barriers break into shards that cut foes beside them (4 + level). Then you vanish for 3 seconds. Walls shelter what is behind them. | 90 / 90 s |

### How the moves play together

- **Shield, then Push.** Hold the lens up while they shoot at you, then return their fire as a Push that
  hits harder for every arrow you caught.
- **Bubble, then Push.** A Push that meets a held bubble sends it rolling at 1.15 blocks a tick for over a
  second. It bowls over every foe in its path (5 + level each) and pops against the first wall, hurting its
  passenger.
- **Bubble, then Nova.** Seal the most dangerous foe first; the Nova hits it twice as hard.
- **Bridges and barriers are ammunition.** Build them where the fight will be: the Nova turns them into shrapnel.
- **Fade, then strike.** Force moves are attacks: casting one while faded flickers you visible (unless you
  learn Unseen Force).

## Exposure and counterplay

Fade keeps the Exposure system. Movement adds Exposure, sprinting much more than walking, and less as you
level; standing still or crouching lets it settle. At 85 the refraction breaks and you flicker visible for a
second.

- Attacks and force moves reveal their caster briefly. Damage taken flickers the victim visible without
  ending the Fade. Water, rain and fire give you away.
- Echo Sense, Tremor Sense and the other sensing powers still pierce concealment (and also reveal hidden pets
  and objects).
- A mob that loses you searches your last known position. Within two blocks it can still notice you.
- A foe in a bubble can still be hit by your friends; it simply cannot move or hit back.
- Force Nova's second-long wind-up and its ground ring tell everyone nearby to get out of the way.

## Eighteen-node skill tree

Three branches of interactions, with no blanket stat bonuses (Invisibility is on the no-stat-bonus list in
`SkillTreeService.bonus`). Branch actives route to `invisibility/InvisibilitySkills`.

| Branch | Opening → active → mastery | Technique → upgrade → capstone |
| --- | --- | --- |
| **Veil** (unseen people and things) | **Quiet Light** (`_0_0`): walking builds half the Exposure, and friends sharing your Fade build none → **Vanishing Touch** (`_0_1`): the friend, pet, villager or object under your aim (up to 16 blocks: a boat, a cart, a dropped item, an armour stand) is invisible for 20 s; hunters lose a hidden creature → **Unseen Constructs** (`_0_2`): your shield, bridges, barriers, bubbles and disc are drawn only for you and your friends | **Vanishing Squad** (`smoke_and_mirrors`): you and every friend, pet and companion within 8 blocks fade for 10 s → **Long Vanish** (`lingering_mirage`): Touch reaches 24 blocks for 40 s, Squad 12 blocks for 20 s → **Unseen Force** (`assassin`): force moves cast while fully unseen hit 40% harder and don't reveal you |
| **Aegis** (shields and things to stand on) | **Wide Lens** (`_1_0`): the shield is half again as wide and lasts 5 s → **Force Barrier** (`_1_1`): a solid wall 7 wide and 4 tall where you aim (20 blocks) for 10 s → **Safety Net** (`_1_2`): a pad of force catches a fall of 6+ blocks, once every 10 s | **Force Disc** (`pickpocket`): ride a disc that glides where you look for 8 s; crouch to step off and drift down → **Steady Disc** (`light_fingers`): the disc lasts 16 s and bridges 20 s → **Rebound** (`phantom`): projectiles your shield, Push, barrier or shelter stops fly back at whoever fired them |
| **Pressure** (blasts and containment) | **Hard Push** (`_2_0`): Push reaches 14 blocks and throws half again as hard → **Pinpoint** (`_2_1`): a bead of force at a foe up to 24 blocks: 6 + level damage and it pops them into the air → **Tight Seal** (`_2_2`): bubbles hold 8 s and crush for 50% more | **Kinetic Shell** (`cloak_field`): seal up to 4 foes within 6 blocks of where you aim for 3 s; one Crush closes them all → **Pressure Chain** (`wider_cloak`): a crushed bubble bursts onto foes within 4 blocks for half the crush damage → **Implosion** (`claudes_lesson`): the Nova's wind-up pulls foes within 16 blocks inward and seals the six nearest, so the burst strikes them all double |

Good combinations: **Pinpoint → Bubble** holds a foe in mid-air; **Kinetic Shell → Push** rolls one bubble into
the others; **Unseen Constructs + Force Barrier** builds a wall your foes cannot see; **Vanishing Squad →
Implosion** brings the whole team in unseen for the finisher.

## What the player sees

One idea runs through every move: **a lens of bent light**. Every field is a clear, faintly blue sheet or shell
with a bright rim where you see it edge-on and a fine triangular facet lattice that flashes where it is
struck. All of it is drawn by `client/effects/setpiece/ForceArt` from four shared shapes: `lens` (a bowed round
sheet), `pane` (a flat sheet with facets and lit edges), `cage` (a sphere wrapped in six great circles, the
geodesic bubble) and `facet` (a tumbling triangle shard). Palette: `9CC8FF` field, `E8F4FF` edge, `3F6FD8` deep
halo, `B6A2FF` violet sheen, `1E2A44` stress cracks (drawn with normal blending).

- **Fade**: the refracted silhouette peels away (the existing optical effects), and your own hands become
  faint contours. Pets beside you go with you.
- **Lens Shield**: the palms rise and a bowed lens opens in front of them, held up by two threads of light from
  the hands. Each Pressure adds a violet ring inside the rim; a hit makes a facet ripple where it lands.
- **Force Push**: a white flash at the palms, then the lens flies forward and widens, with two fading ghosts
  behind it and a wake of pressure rings.
- **Force Bridge and Force Barrier**: a bright seam runs along the span (or rises up the wall) and leaves the
  faceted panes behind; rails glow along the bridge's edges and risers mark each step. The panes are exactly
  the solid cells (temporary `minecraft:barrier` blocks, which are invisible on their own).
- **Bubble**: a geodesic cage with a violet sheen closes round the foe and turns slowly; a shelter is larger,
  paler and without the sheen. **Crush**: the cage shrinks to a third of its size as dark stress cracks spread
  over it, then pops into tumbling facets. A **rolling** bubble spins about its direction of travel.
- **Force Nova**: shells close in round the body while the reach is drawn on the ground; then a flash, a
  geodesic shell racing out to 12 blocks with a violet one behind it, a three-layer shock ring on the ground and
  facets drifting away. Implosion adds violet spokes running inward along the ground.
- **Vanishing Touch**: a cage closes round the target until even its rim is gone. **Vanishing Squad**: a pale
  dome sweeps out to its reach.
- **Pinpoint**: a bead with a three-layer streak, then a small lens pop. **Force Disc**: a faceted disc under
  the feet shedding rings below. **Safety Net**: a lens that dips under the landing and ripples out.

Arm poses (third and first person): `FORCE_SHIELD` (palms up behind the lens), `FORCE_PUSH` (draw back and
thrust), `FORCE_BUILD` (a low sweep), `FORCE_BUBBLE` (one hand aimed, sealing), `FORCE_CRUSH` (hands closing,
trembling) and `FORCE_NOVA` (arms gathered, then flung wide). Fade, Touch and Squad use the older optical
poses. The shield, bridges, bubbles and disc are re-sent as short set pieces every ten ticks while they last, each
fading in and out over two ticks.

Sounds are re-pitched vanilla sounds: amethyst resonance and beacon tones for fields forming, the wind-charge
burst for pushes and pops, glass breaking for bubbles bursting.

## Implementation notes

- `invisibility/InvisibilityService` owns Fade, Exposure, shared Fades, hidden creatures and objects
  (`hide`/`unhide`, sent with `UnseenPayload`) and Vanishing Squad. Non-living hidden things are skipped by
  `mixin/UnseenObjectMixin`.
- `invisibility/ForceFieldService` owns every field: shields, travelling Push waves, constructs (cells placed
  with `cryo/TemporaryBlocks`, restored without effects when the construct ends), bubbles (hold, crush, roll,
  shelter), the Nova, the disc, Pinpoint, Kinetic Shell and Safety Net.
- `invisibility/ForceGeometry` holds the shapes shared by the server and the art: the shield's centre, radius
  and sag, the Push's speed and reach, bubble radii, the crush curve and the bridge's cell layout.
- `invisibility/InvisibilitySkills` holds the node IDs and routes the six tree actives.
- The old Mirage decoys (`OpticalDecoyService`, its client and two payloads) were removed; the network protocol
  version was bumped.

## Cleanup

Death, logout, dimension changes, power removal, suppression and immobilization end Fade, shields, waves,
constructs, bubbles the player made or is held in, the Nova wind-up and the disc. Constructs restore exactly
what was there. Hidden creatures and objects return when their time ends, when hurt or set alight, or when
a sensing power reveals them. A pre-existing vanilla Invisibility potion is restored when Fade ends.

## Verification

`InvisibilityGameTests` covers Fade, Exposure, reveals, shared Fades, potion restoring, a borrowed Fade, the
shield stopping arrows and storing Pressure, the Push hurting and throwing, Rebound, the bridge (solid, three
lanes, stopped by a wall, a step-up for those on it, restored exactly), the barrier, the bubble holding and silencing a foe, the crush,
the roll, the shelter halving harm and stopping arrows, the Nova's wind-up and double damage, Vanishing Touch,
Vanishing Squad, the disc, Safety Net, Pinpoint, Kinetic Shell, Pressure Chain, Implosion, Unseen Force and
saved node identities. The full suite runs with
`./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.

The preview `./gradlew runClient --init-script tools/invisibility-preview.init.gradle` casts every base action
and tree ability 600+ blocks from spawn and saves screenshots to `build/invisibility-preview/screenshots/`.
