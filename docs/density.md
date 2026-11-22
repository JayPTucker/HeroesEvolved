# Density (earthbending)

Density is earthbending. The power's id is still `heroesevolved:phasing`, and its keys keep their action ids, so worlds,
loadouts and the balance table that already have it keep it. It used to be D.L. Hawkins's phasing (walking through
walls, a hand inside a body); a speedster's Vibrate still walks through walls, so Density gave that up and became the
power that moves the ground itself. Quake is still its own power: Quake is invisible force (waves, vibrations,
fissures); Density is solid rock you can see coming, block, stand on and get crushed by.

## Where it comes from: Hung Gar

Earthbending in *Avatar* is choreographed from **Hung Gar**, a Southern Chinese kung fu style from the Shaolin
tiger-crane tradition (Toph fights in Southern Praying Mantis instead). Five of its ideas became rules here:

| Hung Gar | In the game |
| --- | --- |
| **Horse stance** (*sei ping ma*): low and planted; the power comes up from the ground | **Root** and **Horse Stance**. Standing planted on earth makes every move stronger; in the air you're weak. |
| **Tiger claw**: grip and tear | Every attack tears rock out of the ground first, then strikes with it: lift, then hit. |
| **Iron wire and bridge arms**: hardened forearms | Stone Skin and stone gauntlets. You stand and take a blow rather than leap away. |
| **Neutral jing** ("wait and listen", from Bumi and Toph) | Counters that reward standing still and reading the blow (Neutral Jing, Wait and Listen). |
| **Substance**: earth is the stubborn element | Rock is a solid object: real pillars and walls you can stand on, boulders you can see coming. |

## Root and footing

- **Footing.** Rock comes from natural ground under you or where you aim: stone, deepslate, dirt, grass, sand, gravel,
  clay, mud, terracotta, sandstone, ores, netherrack, basalt, blackstone, end stone, obsidian and so on (the
  `heroesevolved:earth` block tag, which a modpack can add to). Planks, glass, wool and metal are not earth (metal is,
  with Metalbending).
  - With no earth underfoot (in the air, on a wooden deck), **Rock Strike** still works but throws a pebble at 60%
    and costs 4 more stamina; the other keys need earth where they act ("There's no earth there to move").
- **Root** fills while you stand planted on earth, over two seconds (one in Horse Stance; half as fast while you walk).
  At full Root everything you throw, raise or slam hits a quarter harder. It drains quickly once you leave the ground.
  The HUD shows it as a brown ring (jade once full; it reads STANCE in Horse Stance), with Breath beside it while you
  tunnel.
- **Material.** A rock is made of the block it came from, and looks it. With **Know Your Stone** each kind hits its own
  way (below). In the Nether, rock comes up as **molten** magma and sets what it hits on fire.
- You can't suffocate in the earth, and the walls and pillars you raise put back exactly what was there when they
  crumble (they are temporary blocks, and are restored after a crash). Mining one, or blowing it up, crumbles it and
  drops nothing.

## Abilities

| Key | Tier (Level) | Ability | What it does |
| --- | --- | --- | --- |
| G | 1 (Level 1) | **Horse Stance** | Drop low and set your feet: no knockback, +6 armor and +2 toughness, +4 to your punches, 35% slower, a lower jump, and Root fills twice as fast. Press again to stand. Holding it costs stamina (3.3 a second); run out and you stand up. A fall in stance lands as a **shockwave** instead of damage. **Shift + G: Seismic Stomp.** One stomp, and for four seconds every footstep within 24 blocks comes up through the ground to you as a ripple, through walls (red for enemies). Anything flying or swimming makes no footsteps. |
| H | 1 (Level 1) | **Rock Strike** | A stomp tears a stone up out of the ground in front of you and it hangs at your chest. **Hold the key** and it grows into a boulder over most of a second. **Press again** to punch it at what you look at (it allows for the drop and finds an enemy in sight): 6 + level damage for a stone, nearly twice that for a boulder, plus a throw. The punch is free and waits on no cooldown. Unthrown, it drops after a couple of seconds. A boulder rolls on through the first body it hits. |
| Z | 2 (Level 3) | **Earth Shackle** | Stone climbs the legs of the enemy you look at (within 8 blocks, standing on earth) and holds it in the ground for 3 + level seconds, half again on mud or clay or if it is Soaked. It can still fight but takes 50% more damage, and struggles free sooner if it fights the ground. **Press again** at it: **Crush**, 4 + level damage and a second and a half longer, free. |
| X | 3 (Level 6) | **Pillar** | A column of stone punches out of the earth you aim at (within 20 blocks), out of a floor or out of a wall, a block a tick (looking at a foe, it rises under the foe's feet): 3 blocks (4 from tier 3, 5 at tier 5), 4 + level damage to what it hits, and it throws it. Aimed at your own feet, it throws **you** up, and the landing hurts nothing. **Shift + X: Wall.** A wall five wide and three high rises from the ground two blocks in front of you in three ticks, and stands ten seconds. Shots stop on it. **Press X again facing it** (free): the **shove** drives it forward eight blocks as a battering ram, climbing a step or dropping with the ground, 5 + level to everything it hits. |
| V | 5 (Level 15) | **Rising Mountain** | The ground cracks in a ring where you aim (up to 24 blocks, radius 3.5; looking at a foe, centred under it) while a jade arc fills round it like a dial: three quarters of a second to get off it. Then the disc tears free and rises up to seven blocks **while you hold the key**, with everyone standing on it. Let go (or hold it two and a half seconds at the top) and it **slams** down: 8 + 2 per level to everyone on it, slowed, and a shockwave round the rim for everyone near. Bosses stay on the ground: they take the slam but never ride the disc. |

**Evolved trial:** move 2,000 blocks of earth: every block a rock flies (a quarter of it), every block of a pillar or wall
raised or shoved, every block tunnelled or surfed.

## Fighting style: Tiger and Crane

With an empty hand you fight in Hung Gar's style on the shared controls (see
[fighting-styles.md](fighting-styles.md#tiger-and-crane-density)). Every blow pulls rock up out of the ground into the
hand, and an earthbender doesn't hop out of the way: the ground carries them.

| Move | What happens |
| --- | --- |
| **Tiger Claw** (lead) | A raking claw with stone closing over the fist, 4 blocks. |
| **Gauntlet Claw** (rear) | The rear fist lands in a full stone gauntlet: 40% harder and a bigger shove. |
| **Twin Slab** (finisher, from level 2) | Both palms drive forward and a slab of ground leaps out ahead into everything in front of you (5 blocks), knocking it back. |
| **Horse Stomp** (low, crouching) | A stamp, and a ring of stone spikes stabs up round you (3.5 blocks): hurt, slowed and bounced. |
| **Earth Slide** (left, right) | The ground under your feet carries you sideways, body upright, **braced**: a blow taken in the slide lands at half strength. |
| **Sink and Surface** (back, from level 2) | Down into the floor and up a few blocks away; blows miss you while you're under. |
| **Neutral Jing** (counter) | The first blow you take in an Earth Slide: a pillar answers the attacker at once (if they stand on earth within 10 blocks), and your next claw goes to them, half again as hard. |

Every Tiger and Crane blow is a quarter harder at full Root.

## Skill tree

Every node changes a move or ties two together; none is a plain stat bonus. Node ids are unchanged from the older tree,
so anything already bought stays bought (each slot now holds its earthbending node).

### Boulder (Hung Gar's tiger, and Bolin)

| Node | Kind | What it does |
| --- | --- | --- |
| **Stacked Stones** | Passive | Rock Strike lifts three stone discs instead of one stone. Each press throws the next disc, free, for a little over half a stone's damage. |
| **Earth Gauntlet** | Active | A glove of stone flies at the enemy you look at within 18 blocks: 4 + level, and it grabs them. With a wall within four blocks behind them it **pins** them to it for two and a half seconds; otherwise it **drags** them back to you. A boss takes the hit, but the glove breaks on it instead of gripping it. |
| **Know Your Stone** | Passive | Sand blinds for two seconds; gravel bursts off your fist as three pellets; deepslate, basalt and obsidian hit 40% harder but fly slower; mud and clay slow what they hit. |
| **Avalanche** | Active | Five boulders tear out of the ground in a line ahead of you, one after another, and roll on over the ground for 16 blocks: 0.8 + 0.2 per level to everything in the way, thrown forward and up. |
| **Shatterpoint** | Passive | A rock that breaks against a wall bursts into a fan of five stone shards that fly back off it, each about a third of the rock. |
| **Metalbending** | Capstone | Iron, copper and gold count as earth to stand on and bend; iron doors and trapdoors open to your bare hand; your rocks hit an armored foe 1 harder for each piece of armor it wears. |

### Mountain (the root)

| Node | Kind | What it does |
| --- | --- | --- |
| **Sei Ping Ma** | Passive | Root fills half again as fast, and in Horse Stance it never drains, not even in the air. |
| **Stone Skin → Rebound** | Active | Plates of ground climb onto you and hold Horse Stance for six seconds, free, banking the weight of every blow you take. Press again: it all comes back out of the ground as a shockwave (radius 3 to 6, 4 to 18 damage). If Stone Skin put you in Horse Stance, you stand back up when it ends. If you were already in stance, you stay in it. |
| **Pile Driver** | Passive | In Horse Stance a punch drives a standing enemy knee-deep for a second and a half (each enemy once every 8 s), and your shockwaves (a stance landing, Rebound, the mountain's rim) root what they hit instead of throwing it. |
| **Quicksand** | Active | Press down on a point up to 16 blocks away (or under the foe you look at): after half a second's warning ring the earth there turns to quicksand for three seconds, and every enemy in it, or who walks in, sinks waist-deep for two. |
| **Set in Stone** | Passive | Enemies you hold in the ground can't struggle free, and each blow you land on one in stance holds it a second longer (up to four). |
| **Entombed** | Capstone | Press Earth Shackle again at an enemy you have already crushed: the ground closes right over it for three seconds (damage every second), then throws it back up. |

### Listener (Toph)

| Node | Kind | What it does |
| --- | --- | --- |
| **Seismic Sense** | Passive | Stand still in Horse Stance and the ground tells you who walks on it: every footstep within 20 blocks shows as a ripple, through walls, once a second. |
| **Tunnel** | Active | Drop into the ground and move through it, feeling the footsteps above. Breath runs out in about ten seconds (sixteen at tier 5); run out and the ground throws you out, hurt. Press it again (free, whatever its cooldown) or press Horse Stance to surface, with an uppercut (3 + level, thrown up) for anyone standing over you. Coming out into a cave or off a cliff ends it on its own. |
| **Grasp from Below** | Passive | Rock Strike while you tunnel grabs the nearest enemy standing above you by the ankles: waist-deep for two seconds, no healing. |
| **Earth Surf** | Active | A wave of ground carries you along at speed for six seconds and climbs a block at a step; anyone in your way is bowled over for 3 + half your level. |
| **Uproot** | Passive | Surfacing from a tunnel under an enemy you hold in the ground tears it out: the uppercut deals double and throws twice as high. Anyone you surface under can't heal for two seconds. |
| **Wait and Listen** | Capstone | Horse Stance costs half as much to hold. Stand still in it for a second and a half and you are **listening**: the next blow aimed at you never lands, a pillar answers it under the attacker, your Root fills, and your next blow lands half again as hard (once every ten seconds). |

### Combos

- **Shackle → Crush → Entombed.** Hold them, squeeze them, then close the ground over them.
- **Pillar under a foe, then Rock Strike in the air.** A thrown foe can't dodge a boulder.
- **Wall, then shove it into a Shackled foe** (it can't step out of the way), or into an Earth Gauntlet pin.
- **Stone Skin → Rebound with Pile Driver.** Soak a crowd's blows, then root all of them at once, then Rising Mountain
  them.
- **Wait and Listen → Rock Strike.** The heard blow fills your Root, and the boulder you throw next lands half again as
  hard on top of it.
- **Tunnel → Grasp from Below → Uproot.** Pull an enemy down from underneath, then surface under it.

## Classes

| Role | Class ability |
| --- | --- |
| Offense | **Pillar Barrage**: a fan of stone columns erupts round a point up to 20 blocks away; foes within 3.5 blocks take 7 + tier and are thrown into the air. |
| Defense | **Unmoving Mountain**: Horse Stance, free, for 8 seconds, set so deep that nothing moves you and you take far less damage, but you are slow. When it ends you stand back up, unless you were already in Horse Stance. |
| Support | **Earth Tent**: a dome of rock closes over you and the friends beside you for 8 seconds. Shots stop on it, and those inside take less harm. Break a block of it to leave early. |

## Finishers: Six Feet, Rock Clap, Heavy Hand

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches
finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md). Each press plays one of
three at random, never the one you used last, and every one fills your Root.

- **Six Feet.** You stamp; stone fingers rise round the foe's ankles and pull it into the ground to the knees; you slide
  round behind it and bring a two-fisted hammer down on its head; it is driven under and slabs fold over the spot like a
  closing hand. Started in Horse Stance, the ground splits in a star and every foe within 4 blocks is rooted for three
  seconds. No real blocks break.
- **Rock Clap.** Both palms rise and two slabs of the ground stand up on either side of the foe; the palms clap and so
  do the slabs, and they break apart in chunks the colour of the ground.
- **Heavy Hand.** You drop into stance, stone gathering on your forearm, and one overhead blow drives the foe down into a
  crater: a star of cracks and a ring of slabs that fold in and seal it.

## Look and feel

One idea runs through every move: **the ground is mass, and it moves in chunks.**

- **Real rock.** Lifted stones, discs, boulders, Avalanche's rollers, the gauntlet and the lifted mountain are real
  blocks in flight (block displays), made of the block they came from: sand comes up as sandstone, grass as dirt, ore as
  plain stone. Pillars, walls and the tent are real blocks you can stand on.
- **Dark cracks** split the ground wherever it gives (drawn with normal blending so they read in daylight), and **dust**
  rolls off everything that moves. Chips and slabs are lit lumps coloured like the ground they came from.
- **A thin jade seam** (Earth Kingdom green) glows only where the bender's own will is in the ground: the stance
  settling, the Rising Mountain's dial, a seismic pulse running out, the listening threads under your feet.
- **The body is Hung Gar.** Horse Stance sits low with the feet wide, a bridge arm out and a fist chambered at the hip;
  the stomp raises a knee and stamps; the punch turns the shoulders behind it; the pillar is two palms thrust up; the
  wall is forearms raised edge-on; the shove is two palms driven forward; the mountain is held up with straining arms and
  dropped with both fists; the slide stays upright and braced over a low palm; Earth Surf rides side-on. Every pose has
  its first-person version, with the hands at the edges of the view.

## Sylar and gifted mobs

When Sylar has taken Density, he still fights with D.L. Hawkins's original phasing (**Reach Through** and **Embed**),
and so do gifted villagers and illagers who carry it: see [Sylar](sylar.md) and [gifted mobs](gifted-mobs.md).

## Technical notes

- Horse Stance is native attribute modifiers (knockback, armor, attack, speed, jump, fall damage), so the client moves
  exactly the way the server expects. Root, the stance and Breath go to the HUD in `DensityPayload`.
- Tunnelling uses a zero-strength attribute marker (`density_sunk` on gravity) that both sides read; `SunkPlayerMixin`
  turns off collision for that player. Bedrock, barriers and protected ground still stop you.
- Footing, Root, materials and every number are in `phasing/EarthGeometry`; thrown rock is `phasing/Stones`; pillars,
  walls, the tent and the mountain are `phasing/Earthworks`; the style is `phasing/EarthStyle`; the tree is
  `phasing/DensitySkills`; the drawing is `client/effects/setpiece/EarthArt` (and `DensityArt` for the pieces kept from
  the older tree).

## Verification

```bash
./gradlew runGameTestServer --init-script tools/density-gametest.init.gradle   # Density, the styles and the tree catalog
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle    # everything
./gradlew runClient --init-script tools/earth-preview.init.gradle              # photographs
```

`DensityGameTests` covers Horse Stance and its drain, Root on earth and in the air, footing on planks, Rock Strike (the
lift, the free punch, the damage), the boulder and the pebble, Earth Shackle and Crush, a pillar that carries and throws a
foe and crumbles back, a wall and its shove, the Rising Mountain's lift and slam, tunnelling, a stance landing and the
trial. `DensitySkillsGameTests` covers every lane: Stacked Stones, Know Your Stone, the Earth Gauntlet's pin, Avalanche,
Shatterpoint, Metalbending's footing, Sei Ping Ma, Stone Skin and Rebound with Pile Driver, a stance punch, Quicksand,
Set in Stone and Entombed, Seismic Stomp, Grasp from Below and Uproot, Earth Surf and Wait and Listen. The preview
photographs every move from behind, the side, the front and first person into `build/earth-preview/screenshots/`.
