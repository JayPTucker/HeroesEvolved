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

Mastery combines active playtime, exploration and real encounters. Existing Mastery and levels are kept when a world updates.

| Source | Default reward and limit |
| --- | --- |
| Active playtime | 12 Mastery per active minute. Moving, fighting, building and interacting count; standing still and casting into empty air do not keep the clock active. Movement and building only count when you also turn your view, so being carried round a water loop or a minecart track, or an autoclicker at a cobblestone generator, earns nothing. Offline time gives nothing. |
| Exploration | 30 Mastery for a new 128-block region, at most one per active minute. Returning to the same region gives nothing. Each dimension has its own regions. |
| Enemy defeats | 3 Mastery per 10 maximum health, with 50% more after recent power use. For each mob type in a 256-block region, the first six kills pay fully, the next six pay half, then kills and hit practice there give nothing. These limits survive death and relogging. |
| Boss defeats | At least 120 Mastery before the recent-power bonus, or four times the toughness reward if higher. Gifted bosses also include their rarity reward. The local encounter limit still applies. |
| Power practice | Hits and stamina spent share a budget of 6 Mastery per active minute. Hits earn one per two fresh damage; healing a captive cannot create fresh damage. Stamina earns half a point per point actually spent. |
| Rescues and story | Genuine rescues reward Mastery; each first-read story note gives 40. |

Spawner and trial-spawner mobs, spawn eggs, command summons, bred mobs, reinforcements and friendly summons give no combat Mastery. Ordinary mob farms cannot level you indefinitely: their encounter allowance runs out and waiting or relogging does not reset it. Explore and take on new encounters to keep progressing.

Held powers use the practice budget for the stamina they actually spend. Surge discounts reduce that reward too, and unlimited energy earns none. Active playtime replenishes practice and exploration allowances; spending stamina alone does not.

## Stamina

The stamina bar grows with Power Level: 100 at Level 1 and 4 more each level (136 at Level 10, 176 at Level 20). Leveling is a permanent way for every power to gain maximum stamina, and every move
costs 4% less for each level since it unlocked, up to 30% off. It recovers 3 a
second at Level 1, a little faster each level, and twice as fast out of combat; a move pauses recovery for 1.5 s.
Costs, cooldowns and unlock levels for every power are in [Stamina and balance](stamina-balance.md).
Overexertion spends the last fraction of stamina too; spending less than one point still starts the recovery pause.

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
| Sonic Resonance | Catch 40 enemies in the burst of a Resonant Note |
| Superspeed | Run 20 km (20,000 blocks) with Accelerate |
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

On the **Loadout** page of the hero menu, everything you can cast is listed with a tag for what it is
for (Offense, Defense, Support or Utility) and the key it is on. Select an ability, then click one of
the eight key slots beside the list to put it there; click its slot again, or right-click a slot, to
empty it. Your class ability (see [Classes](classes.md)) is bound the same way. An ability is on one
key at most; moving it puts the key it left back to its default, or leaves it empty if that default
is already somewhere else. Flight's double-jump always toggles flight, whatever is on key 1.

The top-left HUD shows the icon and current control for each bound ability. Locked abilities are
dimmed, cooldowns count down over the icon, and an active marker appears on abilities such as
Free Flight, Octopus Form and Still World. The controls use the current power's stamina color,
and power meters sit below the icons.

## The ability wheel

Open **Powers → Loadout → Wheel** to put abilities into eight fixed positions. Select an ability
from the list and click a position to assign it. Right-click a position to clear it. Select a
wheel ability and click **Tap R** to pin it. Your layout is saved for each power.

The wheel starts with your learned class ability and unlocked moves that have no assigned key.
Newly learned abilities can fill unused positions. You can also put a keyed ability on the wheel.

- **Hold R** to open it around the cursor. Flick toward an ability and **let go** to cast it.
  Letting go in the middle casts nothing.
- **Hold left click** on an ability to cast it and keep holding it (for channelled abilities such
  as beams and screams) until you let go of the mouse.
- **Tap R** to use the pinned ability. With no pin, it recasts your last wheel pick. The R tile
  on the HUD shows which move a tap will cast.
- Shift works as usual for overexerting. Overexertion fades in as a soft, pulsing red edge to your view. Other people can see a small nosebleed that follows your face and fades away as you recover.

The wheel shows each favorite's icon and cooldown. Highlight an ability to see its name, stamina
cost and rank. Like any open menu, it stops your movement while it is open.
