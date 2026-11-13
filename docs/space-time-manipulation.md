# Space-Time Manipulation

Inspired by [Hiro Nakamura's powers](https://heroes.fandom.com/wiki/Hiro_Nakamura#Powers): spatial travel, carrying someone by touch, relative time, stopped time, reversal, and travel into the past. This is a gameplay adaptation; it does not simulate every possible past or future.

Space-Time is always the first power a world sees: the first Eclipse gives it, dormant, to a random
player online at totality (see [Who gets a power](eclipse.md#who-gets-a-power)).

## Default controls

These are the default keys; rebinding and the inventory skill tree still work.

| Level | Key | Ability | Use |
| --- | --- | --- | --- |
| 1 | G | Fold Space | Blink to a visible surface or into open air. Cancels falling momentum. From level 2, Shift carries one nearby living companion if there is room. Players must be teammates or crouching. |
| 1 | H | Between Seconds | Other players move and recharge attacks more slowly across the server. A bubble follows you and slows nearby mobs and projectiles. Shift + H ends it. |
| 3 | Z | Still World | Freeze entities in a fixed area. Your attacks accumulate and land when time resumes. Shift while starting includes one touching companion; Shift + Z while active resumes time for free. |
| 4 | X | Rewrite the Moment | Restore the recorded timeline from fifteen seconds ago, including changed blocks, containers, and recorded entities, everywhere you have been in those seconds. Anyone who died in reach lives again. Everyone it reaches is carried back through their own fifteen seconds (see [Going back](#going-back)), even across a portal, and lands where they were. You recover lost health. It costs **130 stamina** (hold Shift to overexert when you're short) and recovers in **4 minutes at Level 4, 2½ minutes at Level 5**. While it's ready, a faint ghost of you, ringed in violet and visible only to you, stands where it would take you. |
| 5 | V | Walk the Past | Shift + V records an area; V visits the recorded past with nearby companions. V in the past returns without energy cost or an entry-cooldown delay. |

While **Between Seconds** or **Still World** is active, aim at a projectile affected by your field and
right-click within 7 blocks to catch it for 2 stamina. Pickupable arrows and thrown items go into your
inventory. TACZ bullets glow and keep moving in Between Seconds. Catching one recovers a matching
round of ammunition. A shotgun blast yields one shell. Shots still in flight resume their normal speed
when the field ends.

### Going back

A rewind (Rewrite the Moment, or a speedster's [Overrun](superspeed.md#overrun-outrunning-the-last-minute))
puts the world back at once, then sends back everyone it reaches, one by one:

- **Everywhere you have been.** Block changes, creatures that turned up, and items dropped are recorded
  within 64 blocks of where you are and of everywhere you have been in the time it reaches back. A block
  someone placed where you stood, after you had left, is taken away again.
- **Deaths are undone.** Your own (see below), and anyone else's that happened in reach: where you were
  then, where you are now, or anywhere along your way. They come back alive as they were at that moment,
  with what they carried, and what their death dropped (items and experience) is gone again. A player who
  is offline is put back when they next log in. In hardcore they play again instead of spectating.
- **The trip back.** Everyone the rewind reaches (you, anyone near where you are going back to, anyone
  whose death it undoes) is carried backward along the path they actually took, for up to one and a half
  seconds (three for Overrun), at most 6 blocks a tick. If that is not enough for the whole path, what is
  left is crossed in one cut at the end. Their view glides back along it under a violet edge, their
  controls do nothing, nothing can hurt them, and a violet trail streams along the way they came. Echoes
  of them appear along the path, newest first. Then they land as they were, with the flash and sound
  Rewind has always ended with.
- **Across worlds.** History is kept through portals. If the path went through one, the trip goes back
  through it into the other world and on to where they were. Someone who logs out mid-trip lands first.

### Rewinding your own death

From Level 4, Rewrite the Moment can undo your death. For **fifteen seconds after you die**:

- The death screen shows **Rewind 15 seconds** with a countdown. Pressing it respawns you and, a tick
  later, rewinds: you're back where you stood fifteen seconds before you pressed it, alive, with the
  health, inventory and experience you had then. Or press **Respawn** and then the **Rewind key**
  while the fifteen seconds last; it does the same, even from another dimension.
- The longer you wait, the closer to your death you land: you always go back fifteen seconds from now.
- Everything you dropped when you died is un-made, as are the experience orbs. Drops sitting in a chunk
  that unloaded while you were away are refused when the chunk loads again, so nothing is duplicated.
  Nearby blocks, containers and creatures rewind as usual.
- It needs Rewind off cooldown when you die, and costs what the key does: 130 stamina and the full
  cooldown. A body just back from the dead rarely has that much stamina, so it overexerts (Weakness,
  Nausea and some damage) instead of failing. It is not offered during
  the time-travel story missions, and in hardcore it returns you to Survival.

Configured costs and cooldowns are adjusted by the shared balance multipliers; the HUD shows final values. Still World now lasts at most 6/8/10 seconds by level and stores at most 12 damage per player or 40 per mob. Ranges and level unlocks retain their configuration. Slow and Stop supersede one another. Entity freezing does not freeze redstone, scheduled block ticks, weather, or the entire multiplayer world.

## Optional skills

Learn and equip these through the inventory **Skills** button. The tree was rebuilt in November 2026 around the kit
rather than stat bonuses: every node now changes a move or ties two together, and no generic cost, damage or cooldown
nodes remain. Node IDs, points and key assignments carry over from the old tree.

One rule runs through it: **anything you hold in stopped time stores your blows.** A creature in your Still World,
your Selective Stasis, your Stasis Seal or your Moment's Edge doesn't feel your hits until it is let go, and then
feels them all at once.

### Stillness (stopping things)

| Skill | Kind | What it does |
| --- | --- | --- |
| **Still Point** | Passive | While your Still World holds, Fold Space costs nothing and waits on no cooldown, three times. Hiro teleporting around a frozen room. |
| **Selective Stasis** | Active (22 stamina, 8 s) | Stop one creature you can see within 20 blocks for 4 to 5.5 seconds. Your blows on it wait and land together when time resumes. **Press again** to snap your fingers and let it go early (free). One creature at a time. |
| **Compound Interest** | Passive | Stored blows grow as they pile up: each adds a tenth to them all (up to 60% more), and a creature holds half again as much. |
| **Stasis Seal** | Technique (24 stamina, 10 s) | Frame a point up to 24 blocks away and seal it in stopped time for eight seconds. Shots that fly in hang there; enemies that walk in stop for two seconds (once each), and your blows on them wait. **Press again** to throw everything inside where you look (free). A seal that runs out lets its shots go on as they were. |
| **Held Volley** | Upgrade | Your own shots wait in the seal too, and it reaches five blocks from its centre instead of three. Fill it with arrows, then throw them all at once. |
| **Return to Sender** | Capstone | Shots you throw from the seal become yours, fly half again as fast and curve onto the enemy nearest your aim for two seconds. |

### Wayfinder (folding space)

| Skill | Kind | What it does |
| --- | --- | --- |
| **Behind You** | Passive | Fold Space aimed at an enemy lands you just behind it, facing it (round its side if its back is to a wall). Your next hit on it within two seconds lands 50% harder, or twice as hard if it is held in stopped time. |
| **Spatial Anchor** | Active (15 stamina, 3 s) | Shift + key plants a gold clock on safe ground. Press normally to return to it within 512 blocks in the same world, bringing up to three friends beside you (teammates, players crouching, your own pets), each in the same place beside you. The destination survives relogging and death, but must be loaded and clear. |
| **Snap Shut** | Passive | The hole Fold Space leaves behind snaps shut: enemies within 3 blocks of where you stood are dragged into it, take 3 damage and are slowed. The **Singularity** hangs from this node. |
| **Moment's Edge** | Technique (28 stamina, 12 s) | Time stops for a heartbeat. You fold from enemy to enemy, up to three within 12 blocks (nearest first, then nearest to that one), cutting each for 6 + level and landing behind the last. They feel every cut at once when time resumes. |
| **Long Edge** | Upgrade | Moment's Edge reaches 20 blocks and five enemies. |
| **Thousand Cuts** | Capstone | Moment's Edge cuts each enemy twice, and they stay stopped for two seconds after the last cut: every blow you add waits and lands with the rest. |

### Historian (undoing and hurrying time)

| Skill | Kind | What it does |
| --- | --- | --- |
| **Foreknowledge** | Passive | When you Rewrite the Moment or take a Second Chance, you remember who hurt you in the time you undid. For ten seconds a small violet clock marks each of them, seen only by you, and your hits on them land 30% harder. |
| **Second Chance** | Active (30 stamina, 20 s) | Turn your own clock back five seconds: where you stood, the health you lost and your fire. Inventories, blocks and everyone else stay as they are. Needs five seconds of history, and uses it up. |
| **Shared Hour** | Passive | Second Chance takes friends within 6 blocks (teammates, or players crouching) back through their own five seconds too: where they stood, the health they lost and their fire. |
| **Fast Forward** | Technique (20 stamina, 12 s) | Wind a creature's clock five seconds ahead (up to 20 blocks). An enemy suffers its next five seconds of burning, poison and wither at once, its effects run five seconds down, and it is left weary (Slowness II, Weakness); if you hold it in stopped time, its stored blows land now. A friend's harmful effects and fire run down instead, without hurting them, and the next five seconds of their Regeneration arrive at once. Aimed at nothing, crops, saplings and young animals within three blocks grow up. |
| **Wider Era** | Upgrade | Fast Forward also reaches every creature within 4 blocks of the one you aim at, each as friend or foe, and grows plants five blocks round you. |
| **Era's End** | Capstone | Fast Forward on an enemy brings back every blow you landed on it in the last five seconds, at half strength, all at once. |

### Combos worth learning

- **Still World → Still Point → Behind You.** Stop the room, fold behind each enemy for free, and hit each one twice as
  hard (they are held in stopped time). Every blow lands when the Still World ends.
- **Selective Stasis → hits → Fast Forward.** Pile blows on the frozen target (Compound Interest grows them), then Fast
  Forward it: the stored blows land now, along with its poison and burning.
- **Stasis Seal + Held Volley + Return to Sender.** Fire a quiver of arrows into your own seal, then throw them all at
  one enemy.
- **Second Chance + Foreknowledge + Shared Hour.** Pull your group out of a bad five seconds, then punish whoever caused it.

### Signature: Singularity

Wayfinder's signature ability hangs under **Accelerated Force Mastery**, after Spatial Anchor (Level 10, 3 points, 50 stamina, 35 s cooldown). Space folds to a point where you aim (up to 24 blocks, held clear of the surface).

- **For five seconds** a black core with a photon ring and a turning accretion disk (hot gold inside, violet outside) hangs in the air. Every enemy within 10 blocks is dragged into a spiralling orbit, faster the closer it falls; anything within 1.6 blocks of the core takes 3 + half your level every half second and is slowed. Hostile arrows and fireballs bend into it and vanish. Loose items and experience drift in. Dust is torn off the ground below (cosmetic only: no block is ever touched).
- **Then it collapses:** rings pinch to a point, a flash, and a shockwave throws everything within 8 blocks outward for 8 + level (less at the edge). What it swallowed is set at your feet.
- Bosses feel 15% of the pull, knockback resistance weakens it, allies feel none, and creatures held in Selective Stasis or a cryo cocoon stay where they are.

## Story: The Way Back

The acquisition vision is Odessa at 6:14 PM on the Last Day, centuries before [The Last Signal](the-past.md). The HUD reads **PAST · ODESSA, THE LAST DAY** there. Five durable advancements appear in the vanilla Advancements screen and in the Journal as **The Way Back**.

1. **The Last Day** — stand in Odessa at 6:14 PM and escape the light.
2. **No One Left Behind** — carry a companion through space or share stopped time by touch.
3. **A Place to Return** — establish a spatial anchor.
4. **The Butterfly Effect** — successfully rewrite fifteen seconds.
5. **The Road Not Taken** — enter a recorded past.

Milestones can be earned out of order and survive death and relogging. Existing witnesses of the city receive its milestone on login. The bunker receiver offers directions to the Overworld relay ruins.

Future missions can query `TemporalStoryService.has(player, milestone)` to branch dialogue, gate evidence, or choose a mission stage. Flags are independent from telepathy's existing memory sequence and do not reset it. Suggested continuation: a telepath identifies a survivor's memory, the traveller records the meeting place, and the group revisits that record to discover who triggered the disaster. Existing successful power activations still attract Sylar's attention.

## Effects and multiplayer behavior

- Time reads as clockwork made of light, drawn in the same glowing style as the lightning so it shows in daylight:
  - **Fold Space:** the air where you stood pinches shut behind a fading echo of you, a seam of light runs to where you land, and it opens there around a small clock.
  - **Between Seconds:** an amber clock face spreads under you and its hands crawl round. A ring marks the field's edge, and inside it sand hangs in the air around you, barely falling.
  - **Still World:** the same clock in cold violet with its hands stopped at ten past ten. Motes hang perfectly still around anyone inside.
  - **Rewrite the Moment:** violet trails run backwards from now to then, and ghosts of you appear along the path, newest first. You arrive as rings collapse onto you and the hands spin backwards.
  - **Remember This Moment:** a golden frame snaps out around you with a flash, like a photograph.
- The skill tree speaks the same grammar (drawn by `TimeArt`), and every skill has its own pose in third and first person:
  - **Selective Stasis:** you thrust a palm out, and a cold violet clock closes round the target: a face under it and a
    small one over its head, both stopped at ten past ten, three ice rings caging it that never turn, and motes hanging
    perfectly still. Pressing again is a snap of the fingers; the clock cracks outward, its hands race on, and a gold
    slash bursts from the target for every stored blow. Anything a seal or Moment's Edge holds wears the same cage.
  - **Stasis Seal:** both hands frame a point ahead, and a sphere of stopped time hangs there, faint as glass, with a
    clock round its equator and still motes inside. Thrown, it bursts along your aim, and three rings fly after what
    it held.
  - **Behind You** and **Moment's Edge:** a crescent of gold light, ticked like a clock's rim, opens across each enemy you
    cut; the blade is drawn from your opposite hip and slashed out. Seams of folded space join each fold, and an echo of
    you is left at every spot you leave.
  - **Snap Shut:** violet rings fall in on the hole you left, three spiral arms wind into it, a dark core shows where
    space is missing, and it closes in a flash.
  - **Spatial Anchor:** a palm pressed to the ground plants a gold clock face that settles into the earth, its hands
    showing the hour of the day; recalling draws a hand to your chest like a pocket watch and flicks it open.
  - **Second Chance:** a hand at your chest turns a crown backward; violet rings close in on you from wide to tight
    while the clock under you spins backward.
  - **Foreknowledge:** a small violet clock with backward-spinning hands floats over each remembered enemy, for your
    eyes only.
  - **Fast Forward:** a crown twisted forward and flicked at the target. An enemy gets an amber clock whose hands race,
    ring after ring of years climbing up its body and sand pouring down; a friend gets a gentler gold one; growth spreads
    as rings over a wide gold face on the ground.
- Geometry is bounded and uses the shared detail setting/particle budget. No new audio files: imported Blink, Slow/Stop, and cinematic recordings are preserved.
- Time Stop and selective stasis also block movement and interaction packets. Frozen players cannot attack, use items, drop/swap items, or click inventory slots to bypass the effect. Spectators are excluded.
- Fields stop on death, logout, dimension change, suppression, or power removal. Frozen projectile velocity/gravity are restored; no permanent AI flags are written.
- Blink and anchor travel reject obstructed, fluid-filled, out-of-bounds, or unloaded destinations. Blink avoids generating chunks along its aimed path. Personal history is at most 25 samples per traveller.
- Fifteen-second rewinds retain the existing local recording limits and completeness checks. They do not promise recovery of unrecorded distant chunks. Large existing block-rewind transactions remain a separate performance concern.

## Verification

`gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle`

`SpaceTimeSkillsGameTests` covers the skill tree: Still Point's free folds, stored and compounded blows under Selective
Stasis, the seal holding and throwing shots and creatures, Behind You, Snap Shut, Moment's Edge's delayed cuts, Fast
Forward with Era's End, Foreknowledge, Shared Hour and the anchor bringing a pet home. The older suite includes anchor serialization/blocked arrivals, personal-state restoration without inventory changes, consumed history, real selective tick suspension, companion immunity, projectile release, open-air Blink/fall momentum, and idempotent story flags, alongside existing rewind and other-power regressions.

`./gradlew runClient --init-script tools/time-skills-preview.init.gradle` photographs every skill into `build/time-skills-preview/screenshots/`.

Run `tools/verify-effect-geometry.ps1`, `python tools/verify-skill-content.py`, and `gradlew.bat build` for geometry, content, and packaging checks. Multiplayer feel and the new visual composition still need an in-game playtest.
