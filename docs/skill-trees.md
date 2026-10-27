# Skill trees and Sonic Resonance

Open your survival inventory and click **Skills**, beside the recipe book and
above the inventory slots. The Powers menu also has a **Skill Tree** button.
The button follows the inventory when opening or closing the recipe book.

The original trees use the format below. Quake, Cryokinesis and Electrokinesis now
have eighteen nodes across two pages, and Precognition is the twelfth power. See
[A Future Unwritten](a-future-unwritten.md) for new techniques, capstones, respecialization
and discovery points. Every original
branch contains an opening passive, an optional active skill, and a mastery
passive. Existing power skills keep their normal level unlocks.

You receive **3 points at awakening**, then **2 per power level**. Existing
characters receive the points corresponding to their current level. Level five
provides eleven points; completing every branch would cost fifteen, so the tree
supports different builds. Purchases and assignments survive death and relogging.
There is no automatic refund or free reset when changing levels.

| Branch | Opening node | Active node | Mastery node |
| --- | --- | --- | --- |
| Control | 1 point; 10% lower activation stamina | 2 points; requires parent and level 2 | 2 points; requires parent and level 4; another 15% lower activation stamina |
| Force | 1 point; 10% higher outgoing attack damage | 2 points; requires parent and level 2 | 2 points; requires parent and level 4; another 15% higher outgoing attack damage |
| Survival | 1 point; 10% shorter ability cooldowns | 2 points; requires parent and level 2 | 2 points; requires parent and level 4; another 15% shorter cooldowns |

Activation discounts do not reduce ongoing energy drains. Force boosts outgoing
attacks, including ordinary attacks, while the assigned power is awakened; the
bonus is suppressed during an Eclipse. Zero-cost and zero-cooldown actions stay
free. Other costs round up to at least one.

| Power | Control skill | Force skill | Survival skill |
| --- | --- | --- | --- |
| Quake | Seismic Lance | Aftershock | Fault Step |
| Flight | Slipstream Dash | Air Brake | Updraft |
| Cryokinesis | Ice Lance | Frost Armor | Cold Snap |
| Electrokinesis | Arc Lance | Static Screen | Overcharge |
| Telekinesis | Kinetic Lance | Gravity Well | Levitation Step |
| Telepathy | Psychic Silence | Shared Focus | Terror Pulse |
| Regeneration | Cauterize | Adrenal Rush | Transfusion |
| Space-Time Manipulation | Selective Stasis | Spatial Anchor | Second Chance |
| Radiation | Gamma Lance | Lead Skin | Decontaminate |
| Sonic Resonance | Focused Shriek | Dissonant Wave | Resonant Guard |
| Superspeed | Reflex Focus | Momentum Strike | Slipstream Rescue |

Select a node to inspect its description, requirements, cost and base cooldown.
Use **Spend points** to learn it. Select a learned active skill, then use an
**Equip** button to assign it to one of the five existing ability keys. The
buttons use your actual key bindings. A sub-ability occupies one key at a time.
Selecting another kind of node changes the buttons to **Reset**, restoring the
original skill for that key. Moving a skill preserves its cooldown and releases
the skill it replaces, preventing abandoned held beams or screams.

The new targeted skills reach up to forty blocks and respect solid cover. Cold
attacks use the existing real freeze state. Defensive screens reflect incoming
projectiles; Gravity Well pulls targets over time. Searches cap affected targets,
and the optional skills do not scan or destroy terrain. Shared defensive buffs
apply to the caster and nearby scoreboard teammates, avoiding enemy buffs in PvP.

See [Space-Time Manipulation](space-time-manipulation.md) for the redesigned time skills, controls, and story progression.

## Sonic Resonance

The five original actions are fully named and described in the Powers menu:
Sonic Cry, Resonant Burst, Echo Sense, Sonic Vault and Canary Scream. Voice attacks
use the short reference cry the user selected around 1:40 in the linked clip.
Echo Sense keeps its quieter resonance cue. The continuous held scream follows
the caster and fades out over 100 milliseconds on release; incapacitation, death,
dimension change, logout or a missing server heartbeat. It lasts at most five
seconds and drains twelve extra stamina per second.

The user chose the actual [reference cry](https://www.youtube.com/watch?v=UviuiYaAGQA&t=100)
after rejecting the synthesized version. The held asset is a 5.60-second excerpt
from 1:40.55 to 1:46.15, and shorter attacks use shorter cuts of that excerpt.
Only mono conversion, constant gain, short edge fades and Vorbis encoding are
applied. Pitch and speed are unchanged, and the sound does not loop. The player's
five-second channel and server heartbeats still control its lifetime. Source
attribution is included in `sonic_credits.txt`; the previous CC0-vocal attribution
does not apply to this replacement. Existing imported recordings are preserved.
Run `python tools/prepare-sonic-reference.py --source FILE` with the locally
supplied reference and NumPy, SoundFile and FFmpeg (or imageio-ffmpeg).

## Verification

Run the gameplay suite with
`gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
Run mesh and packet checks with `tools/verify-effect-geometry.ps1` and catalog/audio
checks with `python tools/verify-skill-content.py`.

For actual Minecraft-rendered layout previews, run
`gradlew.bat runClient --init-script tools/skill-ui-preview.init.gradle`.
The isolated client saves normal, locked and learned skill states at GUI scales
two and three in `build/skill-ui-preview-world/screenshots/`, then exits. This
fixture is excluded from the released JAR.
