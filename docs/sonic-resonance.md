# Sonic Resonance

Two singers in one power. **Emma Coolidge** lost her hearing and began to *see* sound, in colour, and
her music drew people to it. **Black Canary**'s voice is a weapon. Sonic Resonance is both: you see
every sound around you, you can lure with a note, and you can scream.

## Sound Sight and Resonance (always on)

**Sound Sight.** Every sound the game plays near you (within 32 blocks) blooms where it was made, in a
colour that says what made it. Only you see it.

| Colour | Made by |
| --- | --- |
| Red and coral | Monsters |
| Green | Animals |
| Violet | Players |
| Gold | Blocks and footsteps (a footstep is a faint ring on the ground) |
| Blue | The weather and the world |
| Every colour, cycling | Music from a jukebox |

It works through walls, since sound does, so you can follow a zombie around a corner, and an
invisible player's footsteps give them away. Loud sounds bloom larger: a hurt or a break more than a
step, an explosion most of all.

**Resonance** (0 to 100, shown in rainbow under your ability keys):

- **Builds** from sonic hits that land (4 per enemy a Cry hits), shots your Wall of Sound turns back,
  and any explosion within 16 blocks (+15).
- **Fades** slowly after ten quiet seconds.
- **Spent** by a harmonic Resonant Note (30) and by Crescendo (all of it).

## Abilities

| Key | Tier (Level) | Ability | What it does |
| --- | --- | --- | --- |
| G | 1 (Level 1) | **Canary Cry** | **Tap** for a focused cry: damage and shove enemies in a narrow cone up to 44 blocks, and shatter glass in its path. **Hold** for the sustained scream, up to five seconds. It drains 6 extra stamina every half second. Your own cry **pushes you backward**: a shove for a tap, a steady push for the scream. **Crouch to brace**. In the air there is nothing to brace on, so it always pushes. No arms: it is only a scream. |
| H | 2 (Level 3) | **Resonant Note** | Set a humming note where you aim, up to 20 blocks away. It plays a slow phrase for eight seconds. Hostile mobs within 16 blocks forget whoever they were hunting and walk to it, and they cannot pick a new target while it plays. **Press again**, or let it end, to break it on everything gathered there: 5 + 1.5 per tier damage, knocked away and slowed, glass, ice and leaves shattered. With 30 Resonance the burst is **harmonic**: three blocks wider and half again as hard. |
| Z | 3 (Level 6) | **Wall of Sound** | A five-block-wide wall of standing sound waves just ahead, for six and a half seconds (seven and a half at tier 5). Shots that reach it turn back the way they came and become yours. Enemies in it are pushed away and hurt every half second. Shift + key drops it. |
| X | 4 (Level 10) | **Sonic Vault** | A cry at the ground behind you throws you away from your aim. Your next landing within ten seconds takes no damage. A real fall lands on a **shockwave** that hits and throws back everything within four blocks. |
| V | 5 (Level 15) | **Crescendo** | **Hold** to build, up to three seconds: colour circles you and the note climbs. **Let go** (or reach three seconds) to release one wave in a wide cone. It reaches 16 to 32 blocks and deals 6 + up to 10 for building, + 1 per 10 Resonance, + tier. It spends all your Resonance, shatters glass, ice and leaves along its path, and leaves everything slowed and weakened. |

The skill tree is unchanged: Focused Shriek, Shatter Note, Dissonant Wave, Echo Map, Resonant Guard,
Quiet Zone and their upgrades.

## Sound

The cry, the burst and the held scream are original synthesized sounds; no recordings are used.
Each layers a chorused, formant-shaped shriek (Canary) over a bowed cello-like drone (Emma), with a
pressure blast at the onset and glassy partials ringing out after. Rebuild them with
`python3 tools/generate-sonic-audio.py`.

## Evolved trial

**Catch 40 enemies in the burst of a Resonant Note** (up to 8 per burst count).

## What changed from the old kit

- *Sonic Cry* and *Canary Scream* are one key now: tap for the cry, hold for the scream.
- *Resonant Burst* is gone. The outward burst still plays when the power awakens, and returns in
  Sonic Vault's landing.
- *Echo Sense* is replaced by Sound Sight, which is always on.
- New: Resonant Note, Wall of Sound, Crescendo, Resonance, and the HUD bar.

## Verification

`SonicGameTests` cover the following:

- Tap versus hold on the Cry.
- The Note's lure, target lock, burst and trial progress.
- The harmonic burst.
- The wall reflecting a shot.
- The landing shockwave.
- Crescendo's direction, damage and Resonance.
- The automatic release at three seconds.
- The trial.

For the in-game look, run:

```
gradlew runClient --init-script tools/sonic-preview.init.gradle
```

It photographs Sound Sight and each power at night into `build/sonic-preview/screenshots/`.
