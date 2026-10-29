# Power Levels, keys and the ability wheel

## Power Level 1 to 20

Your power grows from Level 1 to 20 in five stages, as it does in the show:

| Stage | Levels | Core abilities unlocked |
| --- | --- | --- |
| Manifesting | 1 to 4 | first ability at 1, second at 3 |
| Controlled | 5 to 9 | third at 6 |
| Practiced | 10 to 14 | fourth at 10 |
| Mastered | 15 to 19 | fifth at 15 |
| Evolved | 20 | needs the power's Evolved trial |

Levels unlock the core kit. **Skill points** buy the 18-node trees: 3 at awakening, then 4 every
three levels (21 at Level 15, 27 at 19, 28 Evolved). Up to 8 more come from **story notes** (below).
Completing every branch still costs more than you can earn, so builds stay different. One capstone
per build, and **RESPEC** still refunds everything.

### What "Level N" means in older docs

The power guides were written when there were five levels. Their "Level 1" to "Level 5" are now
**tiers**, which start at Power Level **1, 3, 6, 10 and 15**. Damage formulas that say "level" still
use the tier, so a formula's numbers at old "Level 5" are what you get from Power Level 15 on.

### Existing worlds

Nothing needs to be done. The Mastery curve is built so each tier's starting level needs exactly the
Mastery the old level did, so an old Level 5 player loads in at **Level 15** with every ability,
every skill they bought, and their key setup. They get the larger point total (21 instead of 11) to
spend on top.

## Mastery

Mastery comes from using your power for something:

- **Hitting enemies** (hostile mobs, or other players) within three seconds of using your power: one
  Mastery per two damage, up to 6, at most twice a second.
- **Defeating enemies**: scaled by the foe's toughness, half again more if you used your power in the
  last three seconds, with diminishing returns for farming one mob type.
- **Saving someone**: Blood Gift on a badly hurt ally, Second Life, taking a bonded friend's wound,
  Flight's rescue catch.
- **Story notes**: 40 each.
- **Stamina spent** still counts, at half a point per point (config `powerUseMasteryPerEnergy`).
- A small trickle for active, non-AFK play.

## Ability ranks

Each ability ranks up the more you use it successfully: Rank II after 25 uses (10% shorter
cooldown), Rank III after 75 (20% shorter cooldown and 10% less stamina). Ranks are counted per
power and survive death. The Abilities card shows the rank and the uses toward the next.

## Evolved trials

Level 20 needs the Mastery **and** a feat:

| Power | Trial |
| --- | --- |
| Regeneration | Get back up 5 times after being put down |
| Radiation | Cancel your own Nuclear Detonation 3 times at Critical Core |
| Time Manipulation | Undo your own death with Rewrite the Moment |
| Electrokinesis | Unleash 25 Overcharged powers |
| Every other power | Defeat 60 enemies within three seconds of using your power |

Trial progress counts from awakening, in either order: finish the trial early and you Evolve the
moment you reach the Mastery.

## Story notes

The logs, letters and notes hidden in the world's structures (the bunker, the Bennet house, Isaac's
studio, the ruins) count the first time you pick one up or read it on a lectern: a skill point (up
to 8) and 40 Mastery. A book you signed yourself never counts.

## Keys

There are **eight ability keys**. Keys 1 to 5 start on G, H, Z, X and V with your five core
abilities; keys 6 to 8 start unbound (set them in Options > Controls under Heroes Evolved). Any
ability you can cast, core or learned, can go on any key, or on none.

On the Abilities page, select an ability and use the eight key chips at the bottom of its card:
click a chip to put the ability on that key, click its lit chip to take it off. An ability is on one
key at most; moving it puts the key it left back to its default, or leaves it empty if that default
is already somewhere else. Flight's double-jump always toggles flight, whatever is on key 1.

## The ability wheel

Everything you can cast is also on the **ability wheel**:

- **Hold R** to open it around the cursor. Flick toward an ability and **let go** to cast it.
  Letting go in the middle casts nothing.
- **Hold left click** on an ability to cast it and keep holding it (for channelled abilities such
  as beams and screams) until you let go of the mouse.
- **Tap R** to cast the last ability you chose from the wheel again.
- Shift works as usual for overexerting.

The wheel shows each ability's icon, its key, its cooldown sweep, rank pips, and the level that
unlocks it. Like any open menu, it stops your movement while it is open.
