# Vibe

Cisco Ramon's power from *The Flash* uses dimensional vibrations for concussive blasts, walk-through breaches and glimpses of past events. The [Arrowverse Wiki](https://arrow.fandom.com/wiki/Cisco_Ramon#Powers_and_abilities) describes Cisco's original abilities. [DC's character profile](https://www.dc.com/characters/vibe) also describes his shockwaves and interdimensional travel.

## Core abilities

| Slot | Ability | In play |
| --- | --- | --- |
| 1 | **Vibe Blast** | Snap one palm forward and send a train of split blue wave fronts downrange. They damage and throw enemies in their path. Solid cover stops the blast. |
| 2 | **Breach** | Aim at clear ground. A seam opens in front of you and links to a second opening near your aim. Walk through either mouth to use it. Allies can cross the same link. Press again to close both mouths for free. |
| 3 | **Read the Vibe** | Reach toward a nearby person, mob or scene. Combat leaves 25-second echoes, replayed as flickering silhouettes and a trace of each impact. Revealed aggressors take more damage from your blast and lock. |
| 4 | **Breach Guard** | Open a moving iris in front of you. Arrows, TaCZ rounds and other incoming projectiles that cross the opening collapse into light. Press again to fire the stored impact for free, or hold Shift and press to close it. |
| 5 | **Frequency Lock** | A blue ring shows where interference will collapse. Enemies caught in the collapse are damaged and slowed. Affected speedsters lose their current run and cannot use Superspeed again until the interference clears. |

Breach travel requires an unobstructed opening and a safe, supported exit. You come out on the side you were heading for, turned by the angle between the two mouths, so walking back through the exit puts you behind the entry, where you started. Friendly players can step through voluntarily. The mod's story and rewind dimensions keep their own entrances and exits.

## Skills and classes

Every passive ties two moves together.

- **Concussion.** **Through the Seam** sends a blast that crosses one of your open Breaches on out of the other mouth. **Return Address** marks whoever fires a shot your guard catches. **Backscatter** makes each banked shot hit harder on Return Fire, and **Overflow** bursts a bank of three or more into interference where Return Fire stops. **Vibe Lance** is a narrow long-range shot, and **Reverb** opens with both palms.
- **Breaching.** **Slingshot** makes your next blast instant and 30% harder for three seconds after you cross your own Breach. **Wayfarer** and **Safe Horizon** give travelers Speed, Resistance and put out their fire. **Attune Frequency** saves the dimension, position and facing where you stand. After learning **World Frequency**, hold Shift and cast Breach to open a two-way route to that location. **Flashstep** folds a short distance in one motion.
- **Dimensional Sight.** **Residual Sense** marks anyone who hurts you for six seconds. **Deep Read** replays more hits and marks for longer. **Co-Vibe** lets allies see a replay with you. **Afterimage** makes a blast slow marked targets, and **Prism Lock** collapses a larger zone.
- The class abilities are **Crosswave** (Offense), **Mirror Breach** (Defense) and **Sanctuary Breach** (Support). Sanctuary extinguishes travelers and gives them Resistance. Catch 40 hostile projectiles with a guard for the Evolved trial.

## Look and feel

The palm winds up around a tightening pair of crescents. Each release sends a staggered train of blue fronts with pale edges and twisting filaments. Breaches stretch a vertical cut into a deep-blue iris, with a moving lip and inward-turning bands. They pinch shut into a flash. The guard's inner arcs brighten as it banks impacts. During a read, violet-blue silhouettes flicker into place in the order the hits happened. Frequency Lock's white indicator fills around the ring before the interference folds toward its centre. Each core move has a first- and third-person pose, with original synthesized cues for the wave, breach, snap, vision and collapse.

The awakening opens two flickering tears around the caster and tries to breach the endangered player or ally out of the fight. If the nearby path is blocked, the interference gives the ward space to escape.

Test the power with `/heroesevolved power give @s vibe` on a player without an assigned power.

- **Game tests:** `VibeGameTests` covers the five core moves and World Frequency; `VibeKitGameTests` covers every active
  skill, the class abilities, the passives that link two moves, the speedster rule and both outcomes of the awakening. Run
  them with the rest of the suite: `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
- **Photographs:** `./gradlew runClient --init-script tools/vibe-preview.init.gradle` casts every core move, active skill,
  class ability, Through the Seam, Overflow and the awakening, and saves 30 shots to `build/vibe-preview/screenshots/`.
