# Heroes Evolved

A Minecraft 1.21.1 NeoForge mod with unique player powers, shared-world story
events and evolving abilities. Build and run with Java 21.

## Skills and progression

Open the survival inventory and click **Skills**, next to the recipe book.
Every power has three branches. Quake, Cryokinesis and Electrokinesis now have
eighteen nodes each; the other trees have nine. Earn three points when your power awakens and two more per power level.
Spend them on your preferred build and equip learned skills to your existing
five ability keys. Purchases and loadouts persist across death and relogging.

All twelve powers have trees: Quake, Flight, Cryokinesis, Electrokinesis,
Telekinesis, Telepathy, Regeneration, Time Manipulation, Radiation, Sonic
Resonance, Superspeed and Precognition. Sonic voice attacks use recorded screams, including a positional
sustained scream that stops when you release its key.

See [the skill guide](docs/skill-trees.md) for the original optional skills,
point costs, prerequisites and loadout controls. The Powers menu also provides
a Skill Tree button and live ability costs and cooldowns.

See [Superspeed](docs/superspeed.md) for sprint controls, progression and the Enhanced Speed / Flash-inspired design.

See [A Future Unwritten](docs/a-future-unwritten.md) for the first playable expansion:
135 skill nodes, 45 optional actions, Precognition, shared reactions, prophetic
paintings, and the repeatable Hourglass Annex rescue operation. Find a cartographer
or share a prophetic canvas, then open **J** to read and follow the omen.

## Development

```powershell
.\gradlew.bat runClient
.\gradlew.bat build
.\gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
.\tools\verify-effect-geometry.ps1
python tools/verify-skill-content.py
```

Time Stop is a local ability field; it keeps the multiplayer server running.
The test suite is loaded separately and is excluded from the released mod JAR.
