# Matter Assembly

Matter Assembly is **the engineer**: it folds amber crystal out of thin air and builds things that work for you.
Its constructs are real blocks you can stand on, hide behind and bounce shots off, and they all come apart again
without leaving anything behind. It never takes a block from your inventory.

## Matter

You carry **Matter**: amber crystal fragments orbiting your chest, two rings turning opposite ways. Everyone can see
how much you carry.

- **Every cell of every construct costs one Matter.** Shards cost none (they are paid in stamina).
- **It comes home.** Take a construct apart (Shift + any of your keys), or let it run its time out, and its
  fragments lift off and arc back into your orbit. A cell someone else knocks out is lost.
- **It regrows** one fragment every half second, once you have stopped building for a moment and a half.
- **You carry 24** (32 from Power Level 15).
- **Short of Matter, a construct builds as far as it can** rather than being refused, and Shard Lance always works,
  so the kit never locks.

## Core kit

The table uses ability tiers 1–5. These unlock at Power Levels 1, 3, 6, 10 and 15 respectively.

| Tier | Action | Stamina / cooldown | What it does |
| --- | --- | --- | --- |
| 1 | **Panel** | 8 / 1.5 s | A pane of crystal three wide and two high where you aim: standing on the ground, against a wall, or five blocks out in mid-air. **Look steeply down** and it is a three-by-three floor at the level you stand on, two blocks ahead (over a drop, it is your next step); look nearly straight down and it goes right under your feet. Its blueprint is drawn first, then its cells land one a tick. Your Shards ricochet off it. You keep three; a fourth takes the oldest apart. Lasts 20 s. 1 Matter a cell. |
| 1 | **Shard Lance** | 12 / 1 s | A crystal Shard forms at your hand and flies up to 64 blocks. It **ricochets off your own constructs twice**, a quarter harder after every bounce, and hits a foe a Launch Pad threw **half again as hard**. Damage starts at 8 + 1.2 per tier. No Matter. |
| 2 | **Skywalk** | 20 / 6 s | For eight seconds, crystal catches every step you take on air: a three-wide plate forms under your feet. Walk out over gaps, jump to climb, crouch to drop through. Each plate crumbles a moment after you leave it and its Matter comes home, so a long walk costs only what is under you. |
| 3 | **Launch Pad** | 19 / 5.75 s | A crystal pad where you aim on the ground (or hanging in the air when there is no ground in reach). **Friends and you** are thrown along the way you faced and land without fall damage; **foes** are thrown straight up and are **airborne** for two seconds (a reticle turns round them): your Shards hit them half again as hard and your turrets shoot them first. You keep two. Lasts 20 s. 4 Matter, returned when it ends. |
| 5 | **Sentinel Engine** | 70 / 52 s | Three crystal turrets rise round you. For twenty seconds each finds the nearest enemy it can see within 32 blocks, **airborne ones first**, and shoots Shards at it that ricochet off your constructs like yours. 6 Matter. |

**Shift plus any of your keys** takes all your constructs and pads apart and calls their Matter home. Drawbridge's
second cast retracts that crossing. Ordinary mining and explosions can break constructs but give no items (and
that Matter is lost).

### Combinations worth knowing

- **Trick shots.** A Panel at an angle turns a Shard round a corner, and every bounce adds damage. Bank Shot makes it
  four bounces and +35% each.
- **Juggle.** Launch Pad a foe, then hit it in the air: half again from every Shard, and your Sentinels switch to it.
- **Jump pads.** A pad hanging in the air catches you as you fall and throws you on. Chain it with Skywalk and Panel
  floors to cross what no one else can.
- **Kaleidoscope** (Ballistics) closes mirrors round a foe; a Shard fired in rattles round inside, up to eight
  bounces.

## Look and sound

Everything is one idea: **crystal folded out of thin air**, in amber.

| Move | What you see |
| --- | --- |
| Matter | Fragments in two tilted rings round the chest, turning opposite ways; the ring thins and grows one fragment at a time. |
| Panel | Both hands frame the shape. A thin blueprint of every cell appears; fragments arc from your hand into it; each cell snaps solid with a bright seam as it lands; a sheen sweeps across the finished pane. The cells are clear amber glass with a bright rim. |
| Shard Lance | A flick of the hand; a crystal turning end over end with an amber tail. Every ricochet flashes on the face it hit, sprays chips and spreads a ring; the Shard grows brighter with each bounce. |
| Launch Pad | Both palms pressed down, then flicked up. Fragments rain into a plate; two rings lock and chevrons run out along the throw. A throw bursts a ring and a column of light; a thrown foe wears a turning reticle. |
| Skywalk | Arms out for balance; the air firms into a ring under your feet, and each plate's cells flash as they hold. |
| Taking apart | An open hand drawn back to the chest; the fragments lift off and arc home into the orbit, each with a small glint. |
| Sentinel Engine | Both arms swept up; three pillars, each crowned by a turning crystal head with a searching beam. |

Sounds are amethyst and glass, pitched up: a crystalline snap for each Panel, a resonant ring and a whip for a
Shard, rising pings for each ricochet, a wind-charge burst for a throw, and chimes as Matter comes home.

## Choices in the skill tree

The three branches each have six nodes: six optional actives and three mutually exclusive capstones, using the
normal server-validated purchase and loadout system. Explicit IDs and mechanics are in the shared
[skill catalog](../src/main/resources/data/heroesevolved/skill_trees.json).

**Architecture:** Load-Bearing absorbs falls onto your constructs. Switchback folds a narrow staircase through
a right angle; the bend follows which side of the nearest compass direction you aim. Buttressed Corners gives a
standing Panel a wing folded back at each end. Drawbridge projects a narrow twenty-block crossing and can retract it. Snap Rails adds guard
posts along Switchback and Drawbridge. Living Blueprint rebuilds a broken Sentinel Engine turret cell every
half-second while you stay near.

**Ballistics:** Bank Shot raises your ricochets from two to four, at +35% a bounce instead of +25%. Scattershot fans
five individually colliding Shards. Recall Angle turns a Shard toward a visible enemy near where it bounced.
**Kaleidoscope** (it kept Faultline's place) snaps a ring of crystal mirrors, two high and open to the sky, round
the foe you aim at for seven seconds; your Shards ricochet off it up to eight times. Unstable Lattice throws up to
six cells as Shards when you take constructs apart (that Matter is spent, not returned). Splinter Storm divides a
direct Shard hit into up to two fragments aimed at different visible enemies; fragments never divide again.

**Refuge:** Secret Threshold lets an empty hand open a two-block doorway in your matter, respecting build
permissions. Folded Shelter raises a snug, roofed three-by-three hut round you with a doorway behind. Clean Exit
opens the whole back wall for the team. Rescue Cradle builds a three-by-three floor two blocks below you or an
aimed ally; a non-team player must crouch to consent. Pressure Valve ejects a melee attacker when you are beside
your matter, once per two seconds. Second Foundation can turn a fatal hit into a one-health escape once per
minute by raising an emergency shelter round you; it needs space for at least four cells. It checks damage
after armor and potion reductions and accounts for absorption, so a survivable armored blow does not waste the
rescue. It is not immunity to later hits.

## Matter and world safety

Matter is folded out of thin air, never taken from the player: no inventory block is read or taken, so nothing in
the inventory can be lost or duplicated by the power. The construct is a dedicated `heroesevolved:assembled_matter`
block with no loot table, no BlockItem, no block entity and an immovable piston reaction. Silk Touch,
explosions, cleanup and dismantling do not create resource outputs, so constructs cannot be cashed out into
materials.

Only strict air is eligible. No original block or fluid is converted, and the power cannot destroy a container
as a side effect. Occupied cells are skipped. Build-height, world-border, loaded-chunk, player build rights,
native interaction checks, claim break hooks and placement hooks apply to every cell, including repairs, Skywalk
plates and cells built later by a job. Jobs stop when authorization or PowerRules changes; borrowed casts use
the narrow Mimicry authorization gate. Enemy attacks honor teams, PvP, owned pets, creative/spectator players,
intangibility and intervening cover.

Every cell is recorded as a durable, per-dimension lease containing its coordinate, owner and expiry. Cleanup
removes only a still-matching construct state; later player edits are preserved. Unloaded chunks are never
force-loaded. Expired or cancelled unloaded leases retire from the capacity budget; their orphan blocks clean
themselves when their chunks load. A palette-filtered load check reschedules validation even if an interrupted
save lost the block's scheduled tick. Blocks also validate their lease every ten ticks, so a rewind-restored
construct without provenance deletes itself. A valid lease retains its original expiry through a rewind.

## Performance and lifecycle

Construction begins with at most four cells. Normal jobs place at most 24 cells each per server tick and share a global budget of 96; there are at most 32 jobs and two per owner. Every job has a finite coordinate list. The per-dimension lease cap is 2,048 cells, with 256 per owner, and expiry inspects at most 96 entries per dimension per tick. Owner counts are indexed, and repeated mining/rebuilding does not grow the inspection queue.

There are at most 128 active Shards globally and twelve launch Shards per owner. They move 2.8 blocks per tick, render a small effect every other tick and end at 64 blocks. Secondary fragments and sentinel volleys have hard caps. Sentinel Engine anchors its turrets to the original cast location. Logout, death, dimension changes, power removal and respecialization cancel runtime jobs and remove the owner's loaded geometry; unloaded geometry becomes orphaned and cleans itself on chunk load. Shutdown clears runtime objects. All sounds are vanilla events, preserving the mod's imported audio.

The awakening is a brief amber construction bloom and a visible-enemy repulsion burst. It creates no free blocks. The evolution trial counts forty successful Shard hits.

## Finishers: Monument, Hammer, Shard Orbit

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md).

- **What you see.** Plates tear up all round and fly in arcs onto the foe with seams trailing, and it turns to stone in its last pose. A plinth rises under the statue; it holds, then crumbles from the top into falling chips and dust. Nothing is placed, so nobody else is trapped.
- **Reward:** three Shards spin out of the dust and fly at the three nearest foes (or straight ahead), free.

Each press of C plays one of three at random, never the one you used last. Monument is described above; the two alternates give the same reward.

- **Hammer.** Plates tear up from the ground and gather over your head into a giant hammer, and you bring it down on the foe in an arc; it breaks apart into plates and dust. Nothing is placed.
- **Shard Orbit.** Crystal shards rise round the foe and orbit it in a closing ring, faster and tighter, then all drive in at once and fall as chips.

## Verification

`MatterAssemblyGameTests` (24 tests) exercises building from an empty inventory (stamina, cooldown and Matter
only), an inventory left untouched, a Panel landing cell by cell with real collision, no loot and its Matter coming
home (a mined cell's is lost), building as far as Matter allows and Matter regrowing, a fourth Panel taking the
oldest apart, Shards ricocheting off your own Panel a quarter harder, a Launch Pad throwing a friend along (no fall
damage) and a foe up (airborne), Kaleidoscope's open mirror ring, Skywalk catching a step on air, Sentinel Engine's
turrets, cancelled claim placement, build restrictions and containers, lease expiry and rewind orphans, later player
edits, Switchback and Drawbridge, Shard cover and allies, losing authorization mid-build, PvP, and Second Foundation.

`./gradlew runClient --init-script tools/assembly-preview.init.gradle` photographs the whole kit into
`build/assembly-preview/screenshots/` (25 shots: the orbit, a Panel from blueprint to sheen and in first person, a
floor over a drop, a ricochet into a pillager, a Launch Pad laid, held and throwing a foe and the caster,
Kaleidoscope, Sentinel Engine, Skywalk, Matter flying home, and minimal particles).

Admins: `/heroesevolved matter status|fill|set <amount>|clear [targets]` reports or sets an assembler's Matter and
takes their constructs apart.
