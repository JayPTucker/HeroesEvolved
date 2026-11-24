# Boss fights in other mods: what to learn from them

Research notes for designing our bosses. Numbers are L_Ender's Cataclysm for Minecraft 1.21.1 as the community wiki
described it in November 2026 (sources at the end); details marked "about" were paraphrased there and may be off by a
little. Use them as the pattern, not as numbers to copy: our bosses are original, and our numbers come from playing
them.

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

[Bosses'Rise](https://modrinth.com/mod/bossesrise) (BlockFactoryStudio; NeoForge 1.21.1, Forge 1.20.1) brings five
Souls-like bosses (the Infernal Dragon, the Underworld Knight, the Yeti, the Kraken and more), each in its own hand-built
structure generated across the world, with multi-phase movesets, its own equipment and new mobs round each boss's
region. The user named it as a bar in November 2026, when the Wither was "too easy". What to take from it:

- **Souls-like means you win by reading, not tanking.** Hits that hurt (a quarter to a half of a player's health),
  short recoveries, combos that punish a panicked dodge, and windows that are earned. A boss a player can out-heal or
  out-trade isn't a Souls-like boss, however good it looks.
- **Every boss has its own boss bar, built from its own shapes** (from its gallery): the Underworld Knight's bar is a
  great sword; the Infernal Dragon's sits between two dragon wings with its head as the emblem; the Yeti's has icy
  claws at each end and its face over the middle. The bar is the one piece of a boss the player watches all fight, so
  it carries the boss's look. Ours: `client/WitherBossBar` (a bone bar with the three skulls as its emblem).
- **A region, not just a room.** Its arenas are structures in the world with their own creatures round them: a place to
  find, learn and come back to.

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
- [Bosses'Rise on Modrinth](https://modrinth.com/mod/bossesrise) and [its gallery](https://modrinth.com/mod/bossesrise/gallery)
  (description and screenshots, read November 2026).
