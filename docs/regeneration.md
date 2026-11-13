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

Every action has its own arm pose in third and first person, and three of them have a free second press.

| Key | Level | Action | Behavior | Base stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | Rapid Mending | **Pushes every arrow and stinger out of you first** (the arrows drop at your feet, and each gives 4 Regrowth). Then heal 4 + 2 per level instantly, plus one per 8 Regrowth, spending all of it. Healing past full health becomes up to four hearts of Absorption. Clears fire and toxins, and regrows missing limbs in a second. *Pose:* hands pressed to the wound, then flexed out. | unchanged |
| H | 2 | Blood Gift | **Tap:** heal someone up to 12 blocks away for 6 + 2 per level, plus one per 10 Regrowth, with Regeneration II. It burns the undead. The cut costs you 3 health and gives 6 Regrowth. **Hold:** **Blood Stream**, a steady flow for up to four seconds: every half second they heal 2 + level/2 (+1 per 25 Regrowth) and lose a toxin, and it costs you 1 health and gives you 1 Regrowth. It stops if they leave your sight. With no one in reach (and no syringe), the key costs nothing. Holding an empty syringe still bottles the blood for the Formula. *Pose:* a palm offered; held, the other hand grips the forearm. | unchanged |
| Z | 3 | Indestructible | For ten seconds: no fall damage or knockback, Resistance, **Reckless** punches (3 + level harder; each breaks your hand a little for 1 health and 2 Regrowth) and **bullets come back out** (projectiles do half damage, arrows drop at your feet). **Press again** for **Freefall**, once per Indestructible: you throw yourself about seven blocks up and forward, and come down on whatever is below. The landing hits everything within 3 + a quarter of the fall (up to 7 blocks) for 4 + the fall (up to 16) and throws it back, and every block fallen is a point of Regrowth. *Pose:* chest out, arms spread low; flung up for the leap; fists down for the landing. | unchanged |
| X | 4 | Blood Bond | For ten seconds, friends within 8 blocks heal with you each second and the undead burn, and you take **35% of every wound your friends suffer**. **Press again** for **Pay It Back**: the bond ends, and everyone still in it heals by the wounds you took (plus 2 per level, up to 20 between them), and you gain twice what you took as Regrowth. *Pose:* arms opened to draw them in; hands pulled to the chest, then thrown open. | unchanged |
| V | 5 | Second Life | Aim at a friend within 12 blocks: your blood heals them to full, and for one minute their next killing blow leaves them standing at 2 hearts with Regeneration III. Costs 50 Regrowth, 6 of your health and 40 stamina; 90-second cooldown. With no friend in sight, the key tells you how long you'd be down if killed. *Pose:* a hand pressed into them, the other on your own elbow. | 40 / 90 s |

## Skill tree

The tree is built around the kit rather than stat bonuses: every node changes a move or ties two together.
Node IDs are unchanged, so earlier purchases carry over.

### Martyr (other people's pain)

| Node | What it does |
| --- | --- |
| **Shared Blood** | Friends you heal with Blood Gift, Blood Stream, Bloodletting or Second Life are bonded to you for six seconds, as if by Blood Bond: you take 35% of their wounds. |
| **Bloodletting** (active, was Cauterize) | Cut your palm and fling a fan of blood six blocks ahead. Friends it splashes heal 4 + level and lose harmful effects (4 Regrowth each); the undead burn for the same. Costs 2 health. |
| **Scar Tissue** | Every wound you take for someone else (Blood Bond, Shared Blood, Take the Wound, Martyr) gives twice the Regrowth. |
| **Take the Wound** (technique) | A palm on a friend's wound draws it into you: heal them up to 8 and take half. |
| **Martyr's Grace** | You take only a quarter. |
| **Martyr** | Once every two minutes, take a killing blow meant for a teammate within eight blocks. |

### Adaptation (a body that learns)

| Node | What it does |
| --- | --- |
| **Reckless Abandon** | Reckless punches throw what they hit, and each broken hand gives 4 Regrowth instead of 2. |
| **Adrenal Rush** (active) | Spend up to 40 Regrowth: heal 4 plus one per 5 spent, with Speed II, Haste and Jump Boost for 8 seconds. |
| **Callus** | Adaptation remembers the last three kinds of harm at once. |
| **Adaptation** (technique) | For thirty seconds, each repeat of the same kind of harm hurts 40% less. |
| **Thick Skin** | Repeated harm is cut by 60%. |
| **Perfect Adaptation** | Fire and wither can no longer hurt you. |

### Recovery (getting back up)

| Node | What it does |
| --- | --- |
| **Back on Your Feet** | Getting back up from being down throws a heartbeat that knocks back everything within 5 blocks, and you rise with Strength for 10 seconds. |
| **Play Dead** (active, was Transfusion) | Drop as if dead for up to 4 seconds: you lie on your back, monsters within 16 blocks lose you and can't pick you again, and you heal 1 every half second. **Press again** (free) to get up: your next melee hit within 3 seconds lands 6 harder. Swinging also gets you up. |
| **Quick Recovery** | Your time on the ground is a third shorter, and Rapid Mending is ready the moment you get up. |
| **Revive** (technique) | Both hands on a friend's chest, pumping: pull a friend below 30% health back to 60%, with Regeneration II. |
| **Field Medic** | Revive reaches 16 blocks. |
| **Second Wind** | Revive also clears harmful effects and grants strong absorption. |

The three styles keep their effects and have their own poses: **Vital Burst** (a fist beaten against the chest),
**Undying** (arms out, palms open, daring it) and **Lifebloom** (palms raised).

## Look and sound

Blood moves the wrong way: drops are drawn back into the body as wounds close, crimson and gold
strands climb the body during Rapid Mending, a soft thread of blood arcs to whoever you heal, a
heartbeat pulses out of someone given a Second Life, and coming back from death throws heartbeat
rings across the ground. Indestructible punches crack white with red sparks, and an ejected arrow
leaves a glint and a spray. All glows use the same additive ribbons as the lightning.
Sounds are vanilla: the bone-block snap for broken hands, the Warden's heartbeat for a Second Life.

The rework's pieces are drawn by `client/effects/setpiece/RegenArt` in the same palette, from four primitives:
a sagging **strand** of blood (Blood Stream), **drops** sliding along it, a lub-dub **beat** ring (Pay It Back, a Freefall
landing, Revive) and an **ecg** heart-monitor line (above a body playing dead, flat at first then beating again over a
friend being revived). Blood is drawn with normal blending so it reads as blood, not light; its wet highlight glows. The
stream fades where it passes the camera. Bloodletting flings a fan of drops that splash where they land; a Freefall
landing cracks the ground dark red.

## For developers

- `regeneration/RegenSkills` holds Blood Stream, Freefall, the three branch actives (Bloodletting, Adrenal Rush, Play
  Dead) and the passive hooks; `RegenerationService` keeps Mending, Blood Gift, Indestructible, Blood Bond (now with
  Pay It Back and Shared Blood) and Second Life.
- Play Dead lies the body down by marking it "downed" in `BodyStatePayload` (`BodyService.state`), which every client's
  `BodyVisuals` already draws; the server ignores the give-up key unless she is really down.
- Branch skills get a free second press through `SkillCatalog`'s `followUp` (Regeneration's Play Dead, as Cryokinesis).
- Regeneration is on the no-stat-bonus list in `SkillTreeService.bonus`.

## Previewing and tests

```bash
./gradlew runClient --init-script tools/regen-kit-preview.init.gradle
./gradlew runClient --init-script tools/regeneration-preview.init.gradle
./gradlew runClient --init-script tools/body-preview.init.gradle
```

photograph every power at dusk, and each stage of a limb growing back plus the downed body, in
`build/regeneration-preview/screenshots/` and `build/body-preview/screenshots/`. `RegenReworkGameTests` checks Mending pushing arrows out, Blood Stream and the tapped gift, Freefall, Pay It Back, Shared Blood, Scar Tissue, Bloodletting, Adrenal Rush, Play Dead and its ambush, and Quick Recovery with Back on Your Feet. The kit preview photographs every action, follow-up, skill and style into `build/regen-kit-preview/screenshots/`. `RegenerationGameTests`
checks that wounds become Regrowth and Mending spends it (with overflow Absorption), that Blood Gift
reaches eleven blocks and feeds Regrowth, Reckless punches and ejected arrows, Blood Bond's shared
wounds, that Second Life saves a friend exactly once, and halved falls. It also checks that an axe
takes a limb that grows back, that a missing arm can't swing and a missing leg slows, that Rapid
Mending regrows limbs, that a lethal blow downs her (untouchable, ignored by monsters) and she gets
back up, the recovery times, that the void and a drained body still kill, that she's moved out of
lava, and that holding Sneak gives up.
