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

| Branch | Optional action | Path |
| --- | --- | --- |
| Infiltrator | Through-Strike: Reach Through an enemy up to 40 blocks away | **Phase Carry** (through the wall ahead at Full Ghost with up to 3 friends), then Thicker Walls (walk through walls 4 blocks thicker), then **Ghost Network** (every wall you pass leaves you at Full Ghost, free of upkeep, for 2 s) |
| Density | Harden: Full Dense, free of upkeep, for 6 s | **Long Embed** (Embed from 16 blocks), then Deep Set (embeds last 2 s longer), then **Entombed** (your embedded enemies take damage every second) |
| Escape Artist | Fade Through: Full Ghost, free of upkeep, for 3 s | **Phase Fall** (drop through the floor to the open space below with up to 3 friends; Shift rises), then Deep Fall (32 blocks), then **Untouchable** (holding a notch costs half the stamina) |

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
- The HUD reads `DensityPayload` (the dial and Breath). The network protocol is now 43.
- If you log in inside a wall (after a crash while underground, say), you step out to the nearest
  open space.

## Verification

```bash
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
caffeinate -u -d -t 400 ./gradlew runClient --init-script tools/density-preview.init.gradle
```

`DensityGameTests` covers the dial, walking through walls (and being stopped by bedrock), Reach
Through on a body and into a chest behind a wall, Embed and its release, Sink and the uppercut,
a Full Dense landing, Hollow Ground and Bedrock Slam, and the trial.
