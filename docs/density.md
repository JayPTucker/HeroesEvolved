# Density (D.L. Hawkins)

Density replaces the old Phasing kit. The power's id is still `heroesevolved:phasing`, so worlds that
already have it keep it. Just walking through walls was thin, and speedsters can now do that (see
Vibrate in [Superspeed](superspeed.md)). Density is about what speed can't do. You reach *inside*
things, you leave things *inside* the ground, and you can make yourself *heavy*.

## The Density dial

Your density is a dial with five notches, shown under your ability keys as a two-sided bar: Ghost to
the left of the middle, Dense to the right. Holding any notch other than Normal costs stamina:
1 every 6 ticks at a Full notch, 1 every 12 at a half notch, and half as often with Untouchable. When
you run out, the dial slips one notch back toward Normal.

| Notch | What it does |
| --- | --- |
| **Full Ghost** | Walk into a wall and you come out the other side, through 2 + tier/2 blocks. Blows, shots, falls, blasts and falling blocks pass through you, but your own blows don't land. You fall slowly. |
| **Ghost** | Walk through walls 1 block thick. Shots pass through you, blows do half damage, and so do yours. You fall gently and take half fall damage. |
| **Normal** | Normal. |
| **Dense** | Knockback resistance 0.6, +4 armor, punches +3, mining twice as fast, 10% slower. |
| **Full Dense** | No knockback at all, +8 armor and +4 toughness, punches +6, mining three times as fast, 25% slower, and you jump lower. A fall of more than 3 blocks becomes a **shockwave** instead of damage. |

At Ghost, crouch to lean on a wall instead of passing through it. Anything you're carrying comes
through the wall with you.

**Always:** you can't suffocate inside blocks, and a passage always ends in open space. Bedrock,
barriers and protected ground (spawn protection, and other places you may not touch) stop you.

## Abilities

| Key | Tier (Level) | Ability | What it does | Cost / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 (Level 1) | **Shift** | One notch toward Ghost. With Shift held, one notch toward Dense. | 4 / 0.3 s |
| H | 1 (Level 1) | **Reach Through** | Your arm sinks into the enemy you look at, within 4 + tier/2 blocks. It deals 5 + tier damage that ignores armor (+2 at Dense), and they can't heal for 5 seconds, not even from regeneration or a healer's power. Your hand comes out holding their off-hand item or a piece of their armor, which drops at their feet. Aimed at a wall instead, it reaches through up to two blocks and takes the first stack from the chest, barrel or furnace behind it, if you could open it yourself. | 16 / 3 s |
| Z | 2 (Level 3) | **Embed** | Phase the enemy you look at, within 6 blocks, halfway into the floor, then make them solid again. They stay stuck for 3 + tier seconds. They can still fight back, but they take 50% more damage, and a player who keeps moving against the ground, or a struggling mob, breaks free sooner. | 24 / 10 s |
| X | 3 (Level 6) | **Sink** | Drop into the ground and move through it. You feel the footsteps of everything walking above you as ripples (red for enemies). Underground you see only a few blocks of dark rock, so Sink can't be used to look for ores or bases. Your **Breath** lasts about 10 seconds (16 at tier 5). Press again to **surface**, with an uppercut (6 + tier, thrown up) for anyone standing over you. Coming out into a cave or off a cliff face ends the sink on its own. If you run out of Breath, the ground throws you out and you take 4 damage. | 20 / 2 s |
| V | 5 (Level 15) | **Hollow Ground** | The ground around you goes soft. Every enemy within 6 + tier blocks sinks chest-deep for 5 seconds and takes damage every second. **At Dense, Bedrock Slam instead**: a shockwave around you (8 + tier, thrown back) and Resistance III for 4 seconds. | 60 / 45 s |

**Evolved trial:** travel 2,000 blocks through solid matter, by walking through walls and by moving
underground.

## Skill tree

Every node changes a move or ties two of them together; none is a plain stat bonus. Node ids are unchanged from the
older tree, so anything already bought stays bought.

### Infiltrator (hands and walls)

| Node | Kind | What it does |
| --- | --- | --- |
| **Through the Wall** | Passive | Reach Through finds an enemy behind up to two blocks of wall, and a hand that comes out of a wall lands 3 harder. |
| **Pass Through** | Active (22 / 8 s) | Run straight through the enemy you look at within 10 blocks and come out behind them, facing them: 4 + level damage that ignores armor, no healing for 3 s, slowed for 2. |
| **Inside Knowledge** | Passive | A body your hand has been inside (Reach Through, Pass Through, Fade Through, Grasp from Below, Uproot) can't heal for twice as long, and for that time your Ghost punches on it land at full strength, even at Full Ghost. |
| **Phase Carry** | Active (26 / 10 s) | Through the wall ahead with up to three friends; for 3 s afterward, shots pass straight through them. |
| **Ambush** | Passive | Stepping out of a wall primes your hand for 3 s: your next Reach Through is free, waits on no cooldown and lands 4 harder. |
| **Ghost Network** | Capstone | Every wall you pass stays open for 6 s: friends, pets and villagers who walk into either side come out of the other. You come out at Full Ghost, free, for 2 s. |

### Density (weight and the ground)

| Node | Kind | What it does |
| --- | --- | --- |
| **Pile Driver** | Passive | At Full Dense, a punch drives a standing enemy knee-deep for 1.5 s (each enemy once every 8 s). |
| **Harden → Rebound** | Active (20 / 15 s) | Full Dense, free, for 6 s, banking the weight of every blow you take. Press again: it all comes back out of the ground as a shockwave (radius 3 to 6, 4 to 18 damage). |
| **Crater Landing** | Passive | Your shockwaves (a Full Dense landing, Bedrock Slam, Rebound) root the enemies they hit knee-deep for 2 s instead of throwing them. |
| **Sinkhole** | Active (28 / 15 s) | Press down on a point up to 16 blocks away: after a half-second warning ring, the ground there goes soft for 3 s and every enemy in it, or who walks in, sinks waist-deep for 2. |
| **Set in Stone** | Passive | Enemies you embed can't struggle free, and each blow you land on one at Dense holds it a second longer (up to four). |
| **Entombed** | Capstone | Press Embed at an enemy you already hold: the ground closes right over them for 3 s (damage every second), then throws them back up. |

### Escape Artist (Ghost and underground)

| Node | Kind | What it does |
| --- | --- | --- |
| **Uproot** | Passive | Surfacing under an enemy you hold in the ground tears them out: the uppercut deals double and throws them twice as high. Anyone you surface under can't heal for 2 s. |
| **Fade Through** | Active (18 / 12 s) | 3 s at Full Ghost, free. Every enemy you drift through takes 3 + half your level, is slowed, and can't heal for 2 s. |
| **Grasp from Below** | Passive | Reach Through while sunk grabs the nearest enemy standing above you by the ankles: waist-deep for 2 s, no healing. |
| **Phase Fall** | Active (22 / 10 s) | Through the floor to the open space below with up to three friends (Shift rises). |
| **Deep Fall** | Passive | Phase Fall reaches 32 blocks, and dropping through at Dense lands like a fall from that height: a shockwave. |
| **Untouchable** | Capstone | Holding a notch drains stamina half as fast, and a blow that would take you below a third of your health passes through you as you slip to Full Ghost for 2 s (once every 30 s). |

### Combos

- **Sink → Grasp from Below → Uproot.** Pull an enemy down from underneath, then surface under them and tear them out
  for a double uppercut.
- **Pile Driver → Set in Stone → Entombed.** Punch at Full Dense to root them, keep them held with every blow, then
  close the ground over them.
- **Harden → Rebound with Crater Landing.** Soak a crowd's blows, then root all of them at once; follow with
  Entombed or a Reach Through (a held enemy takes 50% more).
- **Walls.** Phase Carry or a Ghost walk through a wall primes Ambush; with Through the Wall, the primed hand can go
  straight back through the wall you came out of. Ghost Network keeps the wall open for whoever follows you.
- **Ghost hands.** Reach Through or Pass Through, then go Full Ghost: shots and blows pass through you, but your fists
  still land on the body you know (Inside Knowledge).

## Look and feel

Everything about Density is drawn on surfaces. Where you pass into something, its face ripples like
water: teal rings open one after another on both sides of a wall, around your waist as you sink, and
on the chest a hand reaches into.

- **At Ghost**, a pale, wavering outline flickers around your body.
- **At Dense**, cracks spread in the ground under your feet and stone dust rises.
- **An embedded enemy** has a ring of broken stone around its waist, with block crumbs.
- **Hollow Ground** rolls ripples out across the ground.
- **Bedrock Slam** splits the ground in a star.

New poses: **Reach** (the arm driven out low, the other drawn back) and **Sink** (arms out and down
at the sides).

The skill tree is drawn by `DensityArt` in the same grammar, from four primitives: a ripple across a surface, a
standing body-sized oval ripple, a solid slab of stone and a crack with pale light in it.

- **Pass Through**: the body's surface ripples where you went through it, with a pale wake along your line.
- **Ambush**: teal water clings to the primed hand in turning rings (only you see it), then the arm drives in.
- **Ghost Network**: the wall's faces keep rippling outward like a pond while it is open; brighter for Phase Carry.
- **Harden**: plates of stone over the chest, shoulders and forearms, cracks from the feet, and pale light in the seams
  that brightens with the weight banked. **Rebound** throws slabs up round a snapping shock ring.
- **Pile Driver / Crater Landing**: a collar of broken stone round the feet.
- **Sinkhole**: a warning ring whose face fills like a dial, full as the ground goes soft; then a dark pool with rings
  drifting inward and spiral arms.
- **Entombed**: slabs fold in over the body like a closing hand and sink back into the ground.
- **Uproot**: stone shards burst up round the body. **Grasp from Below**: stone fingers rise and close round the ankles.
- **Fade Through**: the body thins to pale strands with fainter shells trailing behind it.
- **Phase Fall**: ripples on the floor you sink into and the ceiling (or floor) you come out of.
- **Untouchable**: the blow passes through a pale shell left where you stood, which breaks into rings.

Each skill has its own pose in third and first person: a shoulder-first **lunge** (Pass Through), an **usher** (Phase
Carry), a low **brace** with clenched fists (Harden, re-sent while it lasts), a **burst** (Rebound), a palm **pressed**
down (Sinkhole), both hands **closing** (Entombed), a loose **fade**, arms up for the **drop**, a hand thrust up to
**grasp**, and a twisting **slip**.

## Sylar

When Sylar has taken Density, he uses **Reach Through** (a hand through your armor, and no healing
for 4 seconds) and **Embed** (the floor swallows you to the waist). A wall between you still doesn't
stop him for long: it ripples as he walks out of it near you. See [Sylar](sylar.md).

## Technical notes

- Every notch's effects are native attribute modifiers (gravity, fall damage, armor, knockback, speed,
  mining speed, jump), so the client moves exactly the way the server expects.
- Moving underground uses a zero-strength attribute marker (`density_sunk` on gravity) that both
  sides read. The `SunkPlayerMixin` then turns off collision for that player, the way spectator mode
  does, so the client neither pushes you out of the rock nor gets corrected by the server. Bedrock,
  barriers and protected ground are still checked on the server, which sends you back if you enter them.
- The HUD reads `DensityPayload` (the dial and Breath).
- The tree lives in `phasing/DensitySkills`; its numbers (and those `DensityArt` draws with) are in
  `phasing/DensityGeometry`.
- If you log in inside a wall (after a crash while underground, say), you step out to the nearest
  open space.

## Verification

```bash
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
caffeinate -u -d -t 400 ./gradlew runClient --init-script tools/density-preview.init.gradle
caffeinate -u -d -t 600 ./gradlew runClient --init-script tools/density-skills-preview.init.gradle
```

`DensityGameTests` covers the dial, walking through walls (and being stopped by bedrock), Reach
Through on a body and into a chest behind a wall, Embed and its release, Sink and the uppercut,
a Full Dense landing, Hollow Ground and Bedrock Slam, and the trial. `DensitySkillsGameTests` covers the tree:
Pass Through with Inside Knowledge, Through the Wall, Ambush, Ghost Network, Phase Carry, Harden and Rebound with Crater
Landing, Pile Driver into Entombed, Set in Stone, Sinkhole, Uproot, Fade Through, Grasp from Below, Deep Fall and
Untouchable. The skills preview photographs every skill into `build/density-skills-preview/screenshots/`.
