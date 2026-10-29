# Regeneration

Character source: [Claire Bennet](https://heroes.fandom.com/wiki/Claire_Bennet) and
[Rapid Cellular Regeneration](https://heroes.fandom.com/wiki/Rapid_Cellular_Regeneration). The
cheerleader who jumps off buildings, pulls the glass out of her own hand, and keeps getting back up.
Her blood heals others; it brought Nathan back. Numbers are configurable starting values.

## Regrowth

Every hit you take becomes **Regrowth (0–100)**: two points per point of real damage, plus a little
from your own Blood Gift cuts and reckless punches. It is stored healing, and the HUD shows it as a
crimson bar under your ability list (the notch marks what a Second Life costs).

- **Passive healing burns it.** Each regeneration tick burns up to 10 Regrowth for 2 extra healing,
  so after a beating your wounds close fast, not at the usual trickle.
- **Your powers spend it.** Rapid Mending spends all of it, Blood Gift is stronger with it, and
  Second Life costs 50.
- **It shortens everything:** a limb grows back 40% faster for 10 Regrowth, and going down
  spends all of it to cut up to 40% off the time on the ground.

## Passive

- **Falls hurt half as much** (none at all while Indestructible).
- **Toxins** (poison, wither, hunger, blindness, darkness) are purged from Level 2, as before.

## Unkillable

At any level, a lethal blow puts you **down** instead of killing you. This replaces the old
once-per-ten-minutes Level 5 Resurrection.

| Level | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Time on the ground | 90 s | 60 s | 40 s | 25 s | 12 s |

- Your body lies on its back and you can't move, act or use powers. The camera drops to the ground,
  the screen darkens, and the HUD counts down ("DOWN • up in 0:45"). Standing, the HUD shows how
  long you'd be down if killed now.
- A body on the ground can't be hurt any further, and monsters think you're dead and walk away.
- Missing limbs grow back while you lie there, finishing just before you get up. You get up with
  at least three hearts and Regeneration II, and a heartbeat.
- **No death loops:** if you went down in lava, underwater or inside blocks, you're moved to the
  nearest safe ground (or home, if there is none) before you stand.
- **Giving up:** after five seconds down, hold **Sneak** for three seconds to wake up at your bed
  (or world spawn), whole, without losing anything.
- Logging out on the ground keeps the rest of the wait for when you come back.
- **What still truly kills you:** the void and `/kill`, a time paradox, **Sylar** (in the show, he's
  the one who got her), and having your blood forcibly drawn: while **Drained**, you can die.

## Severed limbs

A **blade hit of 7+ damage** (swords and axes) or an **explosion** of 7+ takes the limb nearest the
hit: the side it came from, an arm if it came from above your hips. Only the Regeneration holder
loses limbs.

- **Without an arm** you can't attack, use items or mine with that hand, and it holds nothing.
- **Without a leg** you move 40% slower and can't sprint or jump well; without both, you crawl at 20%.
- **It grows back from the bone out:** a raw stump for a second, then the bone pushes out to full
  length (with a knob at the elbow or knee), muscle wraps it from the joint down, and the skin closes
  over it. It takes 12 seconds at Level 1 down to 4 at Level 5; Rapid Mending regrows it in a second.
- Sounds follow the stages: a bone creak, a wet squelch, a slide as the skin closes.
- Armour on a missing limb is hidden too, and first person shows the arm gone.
- **Prefer less gore?** Effects Settings → **Severed limbs: Fade only** makes a missing limb just
  fade out and back in. It changes only what you see.

## Actions

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | Rapid Mending | Heal 4 + 2 per level instantly, plus one per 8 Regrowth, spending all of it. Healing past full health becomes up to four hearts of Absorption. Clears fire and toxins, and regrows missing limbs in a second. | unchanged |
| H | 2 | Blood Gift | Heal someone up to 12 blocks away (was 10) for 6 + 2 per level, plus one per 10 Regrowth, with Regeneration II. It burns the undead. The cut costs you 3 health and gives 6 Regrowth. Holding an empty syringe still bottles the blood for the Formula. | unchanged |
| Z | 3 | Indestructible | For ten seconds: no fall damage or knockback, Resistance, and now **Reckless**: punches hit 3 + level harder and break your own hand a little (1 health, 2 Regrowth). **Bullets come back out:** projectiles do half damage, and arrows push out of the wound and drop at your feet. | unchanged |
| X | 4 | Blood Bond | Replaces Healing Aura. For ten seconds, friends within 8 blocks heal with you each second and the undead burn, as before, and you take **35% of every wound your friends suffer**. Monsters and players you are fighting are never bonded. | unchanged |
| V | 5 | Second Life | Aim at a friend within 12 blocks: your blood heals them to full, and for one minute their next killing blow leaves them standing at 2 hearts with Regeneration III. Costs 50 Regrowth, 6 of your health and 40 stamina; 90-second cooldown. With no friend in sight, the key tells you how long you'd be down if killed. | 40 / 90 s |

## Look and sound

Blood moves the wrong way: drops are drawn back into the body as wounds close, crimson and gold
strands climb the body during Rapid Mending, a soft thread of blood arcs to whoever you heal, a
heartbeat pulses out of someone given a Second Life, and coming back from death throws heartbeat
rings across the ground. Indestructible punches crack white with red sparks, and an ejected arrow
leaves a glint and a spray. All glows use the same additive ribbons as the lightning.
Sounds are vanilla: the bone-block snap for broken hands, the Warden's heartbeat for a Second Life.

## Previewing and tests

```bash
./gradlew runClient --init-script tools/regeneration-preview.init.gradle
./gradlew runClient --init-script tools/body-preview.init.gradle
```

photograph every power at dusk, and each stage of a limb growing back plus the downed body, in
`build/regeneration-preview/screenshots/` and `build/body-preview/screenshots/`. `RegenerationGameTests`
checks that wounds become Regrowth and Mending spends it (with overflow Absorption), that Blood Gift
reaches eleven blocks and feeds Regrowth, Reckless punches and ejected arrows, Blood Bond's shared
wounds, that Second Life saves a friend exactly once, and halved falls. It also checks that an axe
takes a limb that grows back, that a missing arm can't swing and a missing leg slows, that Rapid
Mending regrows limbs, that a lethal blow downs her (untouchable, ignored by monsters) and she gets
back up, the recovery times, that the void and a drained body still kill, that she's moved out of
lava, and that holding Sneak gives up.
