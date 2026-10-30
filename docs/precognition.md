# Precognition

Inspired by Isaac Mendez: see what is coming, paint it, and change it.

Everything Precognition shows is a real prediction made from the world as it is: a projectile's
flight simulated with the same drag and gravity the game uses, a creeper's fuse, a bow being drawn
at you, a mob's planned path. It never guesses what a player will decide.

## Danger Sense and Clarity (always on)

Foreseen harm is painted in the world, for your eyes only:

- **A red line** traces a shot that will hit you, from where it is to where it lands, with an X at
  the impact point. A wall between the shot and you means nothing is coming, so nothing is drawn.
- **A red ring** on the ground shows a creeper's blast before it goes off.
- **Red dashes** mark a bow or crossbow being drawn at you.
- **Sylar** glows for you whenever he is hunting you within 64 blocks.

Danger Sense reaches 16 blocks at tier 1 and 32 at tier 5 (half again during Premonition).

Each foreseen harm that **misses you** changes the future and earns **Clarity**: 8 for a shot, 12
for a blast. A white-gold cross marks where it would have landed. Clarity goes up to 100, fades
slowly after ten quiet seconds, and is shown under your ability keys. It powers Paint the Future
and The Future Is Written.

## Abilities

| Key | Tier (Level) | Ability | What it does |
| --- | --- | --- | --- |
| G | 1 (Level 1) | **Premonition** | For eight seconds, Danger Sense reaches half again as far. Every enemy within 24 blocks shows a gold ghost of where it is about to be (along its real path). Mobs that chose you glow, and invisible players nearby are revealed. |
| H | 2 (Level 3) | **Paint the Future** | A short painting trance (you barely move). **With an enemy in view**, you paint their ending: a canvas hangs over them for fifteen seconds. Everyone on your side hurts them 20% more. At a quarter of their health the painting comes true and they fall. Clarity raises that line: up to 50 is spent, for up to half health. Players and very strong foes (over 100 health, bosses, Sylar) are frozen in the moment instead. **With no one in view**, you paint what is coming for you (see the story section below). |
| Z | 3 (Level 6) | **Saw It Coming** | For a second and a half, the next blow misses. You are already a step aside, leaving a white afterimage where you stood. The attacker is **Exposed**: your next hit on it deals 50% more. You gain 20 Clarity, and a perfect read is ready again in a second. |
| X | 4 (Level 10) | **Fate Thread** | For eight seconds, cross out one lethal outcome for a friend in view within twelve blocks, or yourself with Shift. A red thread joins you. They are left at one heart and cannot be spared again for thirty seconds. Saving someone gives 30 Clarity and Mastery. |
| V | 5 (Level 15) | **The Future Is Written** | Spend all your Clarity to live two seconds ahead of everyone: four seconds, plus one for every ten Clarity (up to fourteen). Every blow is sidestepped (6 stamina each) and its attacker Exposed. Shots that would hit you are crossed out of the air. Your hits deal 30% more, and every enemy shows its ghost. |

The skill tree's Oracle, Painter and Fatekeeper branches are unchanged. Object Reading, Red Margin
(cross out three enemies' next attacks), Omen Relay (lend nearby teammates your Danger Sense for a
while), and the Foresight, Find the Path and Shared Omen techniques all still work.

## The story

- **Awakening** still paints *The Unfinished Hour*, the canvas that opens the Hourglass Annex
  rescue.
- **Paint the Future with no one in view** paints the most pressing future:
  1. The Annex, while it is unresolved: a new copy of the canvas.
  2. Otherwise, **Sylar**, if he is due to find you within twenty minutes. You learn roughly when
     he will come. When he does, his first strike on you misses, because you painted it.
  3. Otherwise, *what is near*: sixteen seconds of Premonition.

## Evolved trial

**Change the future 50 times.** Every foreseen harm that misses counts: a shot, a blast, a blow
read by Saw It Coming, a Red Margin cross-out, a Fate Thread rescue.

## What changed from the old kit

- *Paint the Unfinished Hour* became **Paint the Future**. It still paints the Annex canvas, and
  now also paints enemies' endings and foretells Sylar.
- *Sever Intent* is gone from the base kit. Its effect lives on in the Red Margin skill.
- *Convergence* (bending projectiles to a point) is replaced by **The Future Is Written**.
- New: **Saw It Coming**, Danger Sense, Clarity, Exposed, the painted-ending execute, and the HUD
  bar.

## Verification

`PrecognitionGameTests` cover the following:

- Danger Sense foreseeing a shot, and a miss earning Clarity and trial progress.
- A wall cancelling a threat.
- An arcing arrow's prediction matching its real flight.
- Saw It Coming dodging, Exposing and amplifying.
- Painted endings and the Clarity threshold.
- The painting trance.
- The Future Is Written.
- The trial.

For the in-game look, run:

```
gradlew runClient --init-script tools/precog-preview.init.gradle
```

It photographs each power at night against real mobs into `build/precog-preview/screenshots/`.
