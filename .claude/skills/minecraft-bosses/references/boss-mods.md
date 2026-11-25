# Boss fights in other mods: what to learn from them

Research notes for designing our bosses: what each mod's fights actually do, as their wikis and pages described them in
November 2026 (sources at the end); details marked "about" were paraphrased there and may be off by a little. Use them
as the pattern, not as numbers to copy: our bosses are original, and our numbers come from playing them. **The design
method distilled from all of them is [fight-design.md](fight-design.md)**; read that first.

## L_Ender's Cataclysm

Eight bosses, each with its own structure, way in, phases, music and reward. What they have in common is what we copy.

### What every boss shares

| Pattern | How Cataclysm does it | What we take |
| --- | --- | --- |
| A structure per boss | a dungeon built for the fight: arena, guards, loot | an arena rebuilt every fight, in a pocket dimension |
| A way in the player chooses | an altar and an offering (Burning Ashes, an Abyssal Sacrifice), a key to a sealed door, waking a sleeper by hand, or walking up to it | the story brings the first fight; an item from the drop opens re-fights |
| Finding the next one | each structure is found by throwing an eye crafted from an Eye of Ender and materials, several of them from other bosses | the first-kill drop opens the next boss or area |
| Fighting again | five bosses leave a Boss Respawner; the rest take another offering or a timed recharge | a re-fight item; no second unique drop |
| Damage caps | 20 to 25 a hit and 13 to 20 a second, on 333 to 600 health | 6% of max health a hit, 4% a second |
| Range falloff | less damage past 12 to 38 blocks, none past one and a half times that | full within 16 blocks, none past 24 |
| Idle healing | 25 health a second with no target, paused by a melee hit for 4 to 30 seconds | 5% of max a second after 10 quiet seconds |
| A leash | 20 seconds with no target and more than 16 blocks from home sends it home | the same |
| Shields | heavy blows disable a raised shield for 3 to 10 seconds | sparingly, on a signature |
| Music | a theme per boss, and a 10% chance of its record as a drop | a synthesized theme per boss in phase loops |
| A bar | a boss bar; two bosses add a rage meter under it | phase colour and name; a second resource in the name |
| Block breaking | charges, beams and stomps break the arena (dungeon blocks resist) | allowed, because our arenas are rebuilt |

### The bosses

**Ignis** (a fire knight; the Burning Arena in the Nether). Summoned by placing Burning Ashes, dropped by the arena's two
guards, on an altar; the arena shakes for six seconds first. 450 health, armor 10. Phase two at two thirds: blue
flames, faster attacks, a ring of fire. Phase three at one third: the shield is gone for good and an ultimate (about a
four-second windup) unlocks. His shield blocks hits from the front; returning three of his abyss fireballs breaks it.
A roar stuns anyone close for three seconds. His brand strips armor. *Learn:* a shield that the player breaks by
turning the boss's own attack back; a phase that removes a defence.

**Netherite Monstrosity** (a lava giant; the Soul Forge). Asleep in a lava alcove; wakes when it sees you or is hit, and
takes two seconds to stand, open to damage. 600 health, armor 12. At 40% it goes berserk with a roar and breaks the
blocks round it. Moves read from the body: both fists lift before the ground smash, the shoulder lowers before a charge,
a bomb glows in its chest before a volley. Lava bombs if you stand far off. Its head takes 1.5 times damage; it drains
nearby lava to heal. *Learn:* telegraphs in the body, a weak spot, a reason not to kite it from far away.

**The Leviathan** (a sea serpent; the Sunken City). An offering on an altar in the deepest room; six seconds later the
ceiling breaks and it arrives. 400 health. Phase two at half: a ten-second transformation, homing orbs, a second music
theme. Roars come before its rush and its portals; hitting it during the rush's windup stuns it for about four
seconds. Mines and orbs fill the water as hazards. It takes no damage with its head out of water. A cleansing food from
a mini-boss elsewhere cures its curses. *Learn:* a roar as a universal "something big is coming"; hitting the windup as
the counter; hazards as minions; a reward from one fight that helps in another.

**Ancient Remnant** (a sand beast; the Cursed Pyramid). Woken by using a necklace found in the pyramid on it; four
invulnerable seconds to stand. 450 health. Phase two at half: a roar, then slow regeneration and doubled stomps. Arrows
and tridents only hurt it for six seconds after each charge, and its charge needs a full rage meter that fills when its
attacks miss. Its sandstorm reverses the player's controls. The boss bar shows the rage meter. *Learn:* ranged damage as
a window, not a constant; a meter that tells the player when the big move comes; standing on top of it dodges the tail.

**Ender Guardian** (an armored golem; the Ruined Citadel in the End). Starts when you walk up to its altar. 333 health,
armor 20. Phase two at half: its helmet breaks, so arrows and tridents work from then on, armor drops, new moves. A
gravity punch pulls you in; a vortex catches anyone three or more blocks above it. Immune to fire, falling, drowning and
suffocation. *Learn:* a phase that changes the counterplay; a move that punishes pillaring.

**The Harbinger** (a war machine; the Ancient Factory). Dormant until fed a Nether Star. 390 health. It alternates
missile and laser modes every 15 seconds, announced by a tone. Each charge or volley adds overload; the third forces a
Death Laser with an audible windup that you survive behind solid wall. Only the factory's EMPs stun it (five seconds),
and an EMP re-arms only when the Death Laser hits it. It deflects arrows in phase two and explodes when it dies. *Learn:*
the arena's machines as the only stun; a counter the player must make the boss spend; a death scene.

**Maledictus** (a cursed warrior; the Frosted Prison). Behind a sealed door opened by a key dropped by a guard
elsewhere; it rises from a tombstone that recharges in about a minute. 420 health. Phases at half and a quarter add
dashes and sweeps. A rage meter adds damage for each hit it lands and drains when it misses; halberds are marked on the
floor before they fall; its grab is announced by a short roar. It smashes the blocks it stands in after being hit.
*Learn:* floor marks for falling attacks; a meter that rewards dodging; a key from one place opening another.

**Scylla** (a storm queen; the Acropolis, high over a warm ocean). Woken by hand. 390 health. Phases at two thirds and a
third each start with a storm ritual (weather turns, she can't be hurt), then add homing serpents, spears from the
clouds and lightning under the player. Hits from the front build a parry that ends in a block and a break-away; hits from
the side and back don't. Guardians in her temple boost her if left alive. *Learn:* reward flanking; clear the room's
helpers first.

### Cataclysm's mini-bosses and guards

- **Guards that drop the boss's key** (Ignited Revenants drop Burning Ashes; Aptrgangr drops the Strange Key): exploring
  the structure is part of the fight.
- **Guards with a single weak spot** (the Revenant's frontal guard cracks only under heavy axe hits; the Kobolediator and
  Wadjet deflect arrows from the front): each guard teaches one rule.
- **Machines with a counter** (the factory's Prowler and Watchers die to EMPs, and a hit Watcher alerts the rest).
- **Mobs that start asleep and immune** (the Kobolediator, the Wadjet) until a player comes close: the room is safe to
  read before it wakes.
- **New spawns after a first kill** (after the Leviathan, Coral Golems appear in deep oceans): beating a boss changes
  the world.

## Mowzie's Mobs

- **The Ferrous Wroughtnaut** shrugs off almost every blow. After its overhead slam its axe sticks in the floor, its
  back opens, and only a hit there hurts it; when it pulls the axe free the window closes and a counter follows. One
  readable window, earned by baiting the big move, is the whole fight. Sylar's cracked watch is our version.

## Bosses'Rise

[Bosses'Rise](https://modrinth.com/mod/bossesrise) (BlockFactoryStudio; NeoForge 1.21.1, Forge 1.20.1): five Souls-like
bosses, each in its own structure in the world, found by exploring (no summon item); they never heal or despawn when you
leave. The user named it as a bar in November 2026, when the Wither was "too easy". Its own guide suggests diamond armour,
a shield, food and golden apples, and fighting them in order of health.

- **A dodge roll is part of the player's kit**: about 6 blocks, invulnerable for the first half, 2 charges each
  recharging in 2 s, ground only; each piece of its boss armour and its shield add a charge (up to 7).
- **Sirok, the Sandworm** (200 health, 20 armour): circles 40 to 55 blocks out under the sand and is hittable only above
  it (after a dive or a bite). A 5x5 marker warns a dive. 25 damage within 3 s damages a segment, which then takes +30%;
  segments heal on the next dive. A screech opens each stretch at 75, 50 and 25%; an earthquake of sand columns joins
  at 50%.
- **Skor, the Yeti** (250 health): woken by breaking the ice he is frozen in, then 10 s breaking out, immune until his
  bar shows. The heaviest melee (Ground Smash about 21, spikes up to 20 blocks); an ice cube that pulls anyone past 32
  blocks; a leap when he can't walk to you. Immune for a moment after every hit (spamming is wasted). At 50% he freezes
  everything within 32 blocks in a scene and enrages; under 20% a snowstorm barrage. Arrows deal 15%, tridents 30%. Dies
  as an ice statue that shatters on the next hit.
- **Ashlord, the Infernal Dragon** (250 health, 15 armour): reached by climbing a tower of guards; a shield-cooldown
  guard punishes blocking. Four phases: on the wall (breath and fireball volleys; bows answer it), on the ground below 85%
  (claw, bite, tail), in flight at 50%, and a skeletal form at 25% after a scene. Long death animation. Its armour set
  adds dodge charges and resists fire.
- **Helvar, the Underworld Knight** (280 health, 10 armour, 13 a swing): a sword fighter whose every swing hurts all
  round him, with an answer at each range (Thrust past 10 blocks, Jump Spin past 15, fans of 5 to 9 Sword Waves).
  A soul-wisp shield blocks ordinary hits; it breaks when his **sword sticks** in the floor for about 3.5 s (after three
  heavy swings), or on a **glowing weak mark** shown for 2.5 s after his heavy attacks (double damage, skips the
  shield). Second life: he falls at 0, walks home and rises at full health after a 22 s resurrection; health gates at
  75, 50 and 25% stop the crossing hit's damage at the gate. Last stand: he kneels shieldless for 35 s and any hit kills.
  His sword fires his Sword Waves.
- **Nerakyss, the Kraken** (500 health, 20 armour): on a ship at sea, started by killing its three pirates, in a
  thunderstorm. Phase one is 8 tentacles (two wrap the ship's cannons to disable them); phase two it surfaces. **The
  ship's cannons do five times the damage**, every other projectile a quarter; every 100 damage knocks it down for 15 s,
  a melee window. Leaving the ship sends a runaway tentacle after you. Its trident hits everything within 5 blocks and
  pulls them together.
- **Every boss has its own boss bar, built from its own shapes** (its gallery): a great sword, a pair of dragon wings
  with a head emblem, icy claws with the Yeti's face. Ours: `client/WitherBossBar`.
- **Short scenes, but too long here**: an invulnerable opening and a scene at each phase (Helvar's is about 19 s, his
  resurrection about 22 s).

## Bosses of Mass Destruction

Four bosses, each a sequence of objectives (the [minecraft-guides wiki](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/)).
Every ground attack starts with a glow; distance decides the move; each quarter (75/50/25%) adds a mechanic; it heals
with no target only up to the top of its current quarter, so progress is never lost.

- **The Night Lich** (300 health): summoned by four Soul Stars (one per 50 undead kills) on four altars. Keeps 15 to 30
  blocks away, picks a move about every 4 s: TNT-like comets, 5-missile ice volleys (9 each, Slowness III; a shield
  blocks them but still slows), a rune circle that hatches a phantom 2 s later, and a teleport when you close in, break
  line of sight or look away. At each quarter: six comets, four patterned volleys (lines, a cross, an X), nine phantoms.
  Keeps the night while it lives.
- **The Obsidilith** (300 health, armour 14, 24 with no target): an Eye of Ender on an altar. Never walks. Burst (within
  6 blocks, 16 damage, launches you; leave the 7-block circle), Wave (a line of eruptions; step sideways), Spike (three
  strikes where you're heading; change direction), Anvil (teleports 24 above you, falls; leave the circle). Shields
  block none of them; the launches make falls the real danger. **At each quarter it raises up to four obsidian runes
  linked to it by particle lines, and takes no damage while any stands** (a diamond pickaxe breaks them). On death it
  builds a 16-block pillar with the loot on top.
- **The Nether Gauntlet** (250 health): woken by breaking a carved block in its sealed pillar. **Only the eye in its palm
  takes damage**, and the eye is closed during its punches and open through its 3 s tracking laser (12 a hit, stopped by
  any block). New moves at 85% (laser), 70% (a burning spin punch) and 50% (blindness on everyone in 64 blocks, 2.5 s
  after an eye appears over their heads; milk clears it). Never uses a move twice in a row. Explodes on death and veins
  of Ancient Debris spread through the rock.
- **The Void Blossom** (350 health): rooted, released by a spawner when you come near. Spikes where it expects you
  (purple particles gather a second before; change direction), three rings of spikes out to 21 blocks, spore circles
  (7 blocks, erupt 1.5 s later, poison), petal-blade sweeps (the only thing a shield blocks), thorns that hurt melee on
  the stem. **At each quarter it grows 8 flowers that heal it**; from 50% some are caged in bramble walls open on top.

## Wither Reincarnated

[Wither Reincarnated](https://www.curseforge.com/minecraft/mc-mods/wither-reincarnated) (NeoForge 1.21.1, Forge
1.20.1) rebuilds the vanilla Wither because "it only has one attack and no interesting movement patterns, and the fight
requires very little skill". What it adds: a more intimidating model and sounds; permanent night while it lives; **a
first phase where it destroys the world rather than chasing players** (its gallery shows a burning landscape); a
beacon-like laser "you'll be vaporized by without sufficient cover"; **a charge that a raised shield blocks, stunning
it**; flying up to rain skulls on the ground; **dangerous skulls the player can deflect back for extra damage**; bigger,
burning skull explosions; a more violent final phase; circling its target instead of hovering over it; most mobs fleeing
it; and **possessing nearby undead**, who follow and defend it (they get glowing eyes). The page gives no numbers.

## Beyond Bosses 2

[Beyond Bosses 2](https://www.curseforge.com/minecraft/mc-mods/beyond-bosses-2) rebuilds the Ender Dragon fight and the
stronghold. The central island becomes a planet with the outer islands as its rings. Phase one: stomp, tail whip and
smack as "super attacks", with a bite only while those cool down. Phase two: it flies round the towers throwing
fireballs; destroying every crystal makes it land in the centre, knocking players off to fight it on the ground. The
last phase adds a dive, a fireball barrage and a downward breath. At half health it **looks damaged and speeds up**; at
a quarter it slows and weakens. A 10% chance each day sends it raiding the Overworld. It dies in purple flames.

## Epic Fight: Bosses of Miracle and the Epic Fight mod

[Bosses of Miracle](https://www.curseforge.com/minecraft/mc-mods/epic-fight-bosses-of-miracle) (Forge 1.20.1) adds three
Souls-like duellists on the Epic Fight combat mod: Kenji the Blade Master (found in plains; drops his uchigatana),
Azrath the Berserk (a Void Compass opens a portal to his dimension), and the Overlord Kenshin (an altar ritual with a
nether star, an echo shard and eyes of ender). Its page gives no movesets. What it builds on is
[Epic Fight](https://epicfight-docs.readthedocs.io/)'s animation-driven combat:

- Dodges are a roll or a step in four directions, invulnerable to physical hits while they run, costing 4 and 3
  stamina; heavier armour costs more and slows them.
- Guarding stuns the blocker briefly and costs more stamina for each block in a row (a 0.6 to 1.2 multiplier); a
  timed parry avoids that penalty; Impact Guard takes 80% off explosions, fire, projectiles and magic.
- Stuns come in tiers (short for normal hits, long for special attacks, knockdown for slams), lengthened by the
  attacker's **impact** and shortened by the defender's **stun armour** and weight; **armour negation** is the share
  of damage armour can't stop.
- Hit areas follow the weapon's animation (several colliders interpolated between poses), and mobs choose attacks by
  weighted behaviours with conditions (distance, angle, eye height, health), cooldowns, and whether a move can be
  interrupted.

## Souls-like Bosses

[Souls-like Bosses](https://www.curseforge.com/minecraft/mc-mods/souls-like-bosses) (Forge, Fabric and Quilt 1.20.1):
Dark Souls-inspired bosses (the Nightlord, the Abyss Watchers, the Soul of Cinder, Melkor, Lothric) in arenas generated
by biome (the Tree of Light in oceans, the Catacombs of Carthus in the Deep Dark, the Kiln of the First Flame in
mountains, Lothric Castle in plains, Melkor's Keep in soul sand valleys), unlocked one after another. Its configuration
shows what it treats as the dials: each boss's health (600 for the Nightlord, 400 for the Soul of Cinder and the Abyss
Watchers in its example), **combo length**, a difficulty from Easy to Hardcore, keep-inventory on death, and an ability
cooldown. The Abyss Watchers are a melee fight where more Watchers join as it goes on. The pages give no movesets.

## Apotheosis

- **Bosses as a layer over the whole world**: a small chance that a spawn is a named, graded boss with better gear and
  affixes, wherever the player already is. No structure, no summon. Our gifted mobs are this layer
  ([gifted-mobs.md](gifted-mobs.md)): rarity, generated names, an announcement, a guaranteed prize.

## Sources

- [Cataclysm wiki index](https://www.minecraft-guides.com/wiki/cataclysm/), and its pages for
  [Ignis](https://www.minecraft-guides.com/wiki/cataclysm/burning-arena/ignis/),
  [the Netherite Monstrosity](https://www.minecraft-guides.com/wiki/cataclysm/soul-forge/netherite-monstrosity/),
  [the Leviathan](https://www.minecraft-guides.com/mod/cataclysm/wiki/the-leviathan/),
  [the Ancient Remnant](https://www.minecraft-guides.com/mod/cataclysm/wiki/ancient-remnant/),
  [the Ender Guardian](https://www.minecraft-guides.com/mod/cataclysm/wiki/ender-guardian/),
  [the Harbinger](https://www.minecraft-guides.com/mod/cataclysm/wiki/the-harbinger/),
  [Maledictus](https://www.minecraft-guides.com/mod/cataclysm/wiki/maledictus/),
  [Scylla](https://www.minecraft-guides.com/mod/cataclysm/wiki/scylla/), and the guards
  ([Ignited Revenant](https://www.minecraft-guides.com/mod/cataclysm/wiki/ignited-revenant/),
  [Kobolediator](https://www.minecraft-guides.com/mod/cataclysm/wiki/kobolediator/),
  [Wadjet](https://www.minecraft-guides.com/mod/cataclysm/wiki/wadjet/),
  [Aptrgangr](https://www.minecraft-guides.com/mod/cataclysm/wiki/aptrgangr/),
  [the Prowler](https://www.minecraft-guides.com/mod/cataclysm/wiki/the-prowler/),
  [the Watcher](https://www.minecraft-guides.com/mod/cataclysm/wiki/the-watcher/),
  [Coral Golem](https://www.minecraft-guides.com/mod/cataclysm/wiki/coral-golem/)).
- [Ferrous Wroughtnaut](https://www.minecraft-guides.com/mod/mowzies-mobs/wiki/ferrous-wroughtnaut/) (Mowzie's Mobs).
- [Apotheosis's bosses](https://www.minecraft-guides.com/mod/apotheosis/wiki/apothic-bosses/).
- [Bosses'Rise guide](https://www.minecraft-guides.com/mod/bossesrise/), with its pages for
  [Helvar](https://www.minecraft-guides.com/mod/bossesrise/wiki/helvar-the-underworld-knight/) and
  [Nerakyss](https://www.minecraft-guides.com/mod/bossesrise/wiki/nerakyss-the-kraken/).
- [Bosses of Mass Destruction wiki](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/), with its pages
  for the [Night Lich](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/night-lich/), the
  [Obsidilith](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/obsidilith/), the
  [Nether Gauntlet](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/nether-gauntlet/) and the
  [Void Blossom](https://www.minecraft-guides.com/mod/bosses-of-mass-destruction/wiki/void-blossom/).
- [Wither Reincarnated](https://www.curseforge.com/minecraft/mc-mods/wither-reincarnated),
  [Beyond Bosses 2](https://www.curseforge.com/minecraft/mc-mods/beyond-bosses-2),
  [Epic Fight: Bosses of Miracle](https://www.curseforge.com/minecraft/mc-mods/epic-fight-bosses-of-miracle),
  [Souls-like Bosses](https://www.curseforge.com/minecraft/mc-mods/souls-like-bosses) and the
  [Epic Fight documentation](https://epicfight-docs.readthedocs.io/) (read November 2026).
- [Bosses'Rise on Modrinth](https://modrinth.com/mod/bossesrise) and [its gallery](https://modrinth.com/mod/bossesrise/gallery)
  (description and screenshots, read November 2026).
