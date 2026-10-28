# Pyrokinesis and Helixite

Character sources: [Meredith Gordon](https://heroes.fandom.com/wiki/Meredith_Gordon),
[Flint Gordon](https://heroes.fandom.com/wiki/Flint_Gordon) and
[Pyrokinesis](https://heroes.fandom.com/wiki/Pyrokinesis). Meredith's orange fire is the base
power. Flint's hotter blue flame is its high-end state. Numbers are configurable starting values.

Pyrokinesis is the thirteenth unique power. It uses the normal one-owner rule, Eclipse assignment,
awakening, mastery and skill points.

## Passive

- **Fire can't hurt you.** Burning, fire blocks and fireballs do nothing. From **level 3**, lava
  can't hurt you either.
- **Burning Touch (level 2):** your punches set targets on fire.
- **Heat (0–100):** using fire builds Heat. It starts cooling three seconds after you stop.
  At **80 Heat** your flames turn **blue** and deal 50% more damage. Water and rain reset Heat to zero.
- **Fuel and weakness:** standing in fire or lava restores 3 stamina per second. Water and rain cost
  2 stamina per second. Cold damage, including Cryokinesis, hurts you 50% more.
- **Contained fire:** crouch while casting and creatures still burn, but no fire blocks are placed.

## Actions

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Flare** | A fire bolt up to 32 blocks. It burns the first enemy for 4 + level damage (4 s of fire), or lights the block it hits. Water, ice and snow turn it into steam. | 14 / 1.25 s |
| H | 1 | **Flame Palm** | Hold for a 6-block flamethrower cone, up to 5 s. Each half second it uses 6 stamina and deals 1.5 + 0.3 × level damage. It lights blocks it touches. Release, or Shift, to stop. | 12 / 4 s |
| Z | 2 | **Ring of Fire** | A ring of real fire, radius 4, for 8 s. Enemies crossing it burn, and incoming projectiles burn up. | 35 / 15 s |
| X | 3 | **Backdraft** | A fire-propelled leap forward and up. It scorches enemies beside you and cushions your landing. | 25 / 5 s |
| V | 5 | **Inferno** | Charges for 3 s, then a 10-block fire nova that deals up to 12 + 2 × level damage and lights the ground. At blue Heat it becomes a **Blue Inferno** with 18 + 2 × level damage. Shift cancels the charge. | 88 / 72 s (after the shared ultimate balance pass) |

## Real fire

Pyrokinetic fire is ordinary vanilla fire. It spreads, burns wood and can burn down a base.

- Every fire block goes through the same checks as a normal block placement: build permission,
  spawn protection, claim and protection mods, world border, and loaded chunks.
- Whether fire spreads is still decided by the vanilla `doFireTick` gamerule.
- No fire is placed in the future city, a Temporal Snapshot or the Hourglass Annex.
- **Rewrite the Moment** records the burned blocks, so a time traveller can undo a fire.
- Server owners can make all pyrokinetic fire cosmetic with `pyrokinesis.worldFire = false` in the
  common config. Creatures still burn, but no blocks catch.

## Skill tree (18 nodes)

Each branch opens with a stat node, then an active skill and a mastery node, then a new technique,
an upgrade and a capstone. Only one capstone can be learned at a time.

| Branch | Active skill | Technique → upgrade → capstone |
| --- | --- | --- |
| **Kindler** (−10% / −25% stamina) | **Ember Lash**: whip fire at a target up to 40 blocks away, 6 + level damage, and pull them slightly | **Ember Mine**: hide an ember only you can see, which erupts when an enemy steps near (two at once, 40 s) → **Long Fuse**: three mines, bigger eruptions → **Wildfire**: eruptions leave real fire and set off your other mines |
| **Blue Flame** (+10% / +25% damage) | **Heat Mantle**: 8 s of Resistance and a burning aura that also burns up projectiles | **Blue Lance**: a piercing 24-block blue jet, always at blue damage → **White Heat**: blue starts at 60 Heat → **Flint's Fury**: Inferno is always blue and 3 blocks wider |
| **Phoenix** (−10% / −25% cooldowns) | **Cauterizing Flame**: heal 4 + level, clear harmful effects | **Fire Shepherd**: draw up to 32 nearby fires into your hands and put them out, restoring stamina and health → **Thaw**: also unfreezes friends → **Pillar of Flame**: Backdraft launches almost twice as high, then you drift down |

Fire Shepherd makes the pyrokinetic the best firefighter on the server, including for fires they
started themselves.

## Reactions

| Reaction | How | Result |
| --- | --- | --- |
| **Thermal Shock** | Any pyrokinetic hit on an enemy a Cryokinetic has frozen solid | Uses up the freeze. A steam explosion deals 10 damage (4 to players) and blinds anything within 3 blocks. |
| **Steam Veil** | Flare or Flame Palm hits water, ice or snow, including Cryo's ice walls and paths | A steam cloud for 6 s. Mobs can't target anyone inside it, and mobs inside can't see out. |
| **Firestorm** | A Flight **Cyclone** or Superspeed **Whirlwind** passes through a Ring of Fire | The vortex catches fire for 5 s, igniting and damaging everything nearby. |

Each reaction gives two training points the first time you discover it, the same as the existing reactions.

## Awakening

*Something is burning*: for 2.5 seconds your hands smoulder while a flame helix rises around you.
Then **FLASHPOINT**: two rings of real fire burst outward, at radii 5 and 8, and nearby enemies are
thrown back and set alight. You're protected throughout, and the intro only ever plays once.

Sylar can learn Pyrokinesis. His copied version throws fire bolts that set you alight.

## Helixite

Helixite is a new crystal ore and the mineral the [Formula](formula.md) laboratory is built from:
two shards for the Centrifuge, one for the Synthesis Station, and one for each batch of Unstable Compound.

- **Where:** the Overworld, **Y −64 to 0**. Veins are most common around **Y −32**, nearly always
  in deepslate.
- **What it looks like:** teal-and-violet crystal clusters. The ore **glows faintly** (light
  level 3), so you can spot it in a dark cave.
- **Mining:** iron pickaxe or better. Drops one **Helixite Shard** plus experience. Fortune adds
  more shards; Silk Touch gives the ore block.
- **How rare:** two vein attempts per chunk, of up to four blocks, and veins exposed to air are
  often discarded. That works out to **about a quarter as common as diamonds**. An hour of
  deepslate strip-mining should find roughly 6–12 shards.

That rarity is deliberate. Building the two lab blocks should feel like a real milestone, and each
Formula should cost something, without needing an endgame grind.

Regenerate the textures with `python3 tools/generate-helixite-textures.py`.
