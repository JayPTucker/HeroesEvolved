# Heroes Evolved

A Minecraft 1.21.1 NeoForge mod with unique player powers, shared-world story
events and evolving abilities. Build and run with Java 21.

## Skills and progression

Open the survival inventory and click **Skills**, next to the recipe book.
Every power has three named branches of six nodes: eighteen per power, 288 in all.
The skill screen is laid out like the Advancements screen. Earn three points when your power awakens and two more per power level.
Spend them on your preferred build and equip learned skills to your existing
five ability keys. Purchases and loadouts persist across death and relogging.

All sixteen powers have trees: Quake, Flight, Cryokinesis, Electrokinesis,
Telekinesis, Telepathy, Regeneration, Time Manipulation, Radiation, Sonic
Resonance, Superspeed, Precognition, Pyrokinesis, Invisibility, Phasing and
Empathic Mimicry. Sonic voice attacks use recorded screams, including a positional
sustained scream that stops when you release its key.

See [the skill guide](docs/skill-trees.md) for the original optional skills,
point costs, prerequisites and loadout controls. The Powers menu also provides
a Skill Tree button and live ability costs and cooldowns.

See [Superspeed](docs/superspeed.md) for sprint controls, progression and the Enhanced Speed / Flash-inspired design.

See [A Future Unwritten](docs/a-future-unwritten.md) for the first playable expansion:
135 skill nodes, 45 optional actions, Precognition, shared reactions, prophetic
paintings, and the repeatable Hourglass Annex rescue operation. Find a cartographer
or share a prophetic canvas, then open **J** to read and follow the omen.

See [Pyrokinesis and Helixite](docs/pyrokinesis.md) for the fire power, its real-fire rules,
reactions with Cryokinesis, Flight and Superspeed, and the new Helixite crystal ore.
New players wake sealed in a Cryochamber in a stasis bunker beneath world spawn,
where the world's past waits to be read. See [The Past](docs/the-past.md).

See [Invisibility, Phasing and Empathic Mimicry](docs/new-powers.md) for the three newest
powers and the techniques added to every original tree.

See [The Formula](docs/formula.md) for awakening unclaimed powers without waiting for an
Eclipse: draw Regeneration blood (donated or forced), spin it in a Centrifuge, synthesize
it in a Synthesis Station built from Helixite, then inject it.

## Building structures

Scenery is drawn as text **blueprints** in `src/main/resources/data/heroesevolved/blueprints/`
and placed by the mod. `tools/blueprint.py` validates, renders and imports them (including
`.nbt` files saved with a vanilla structure block), and `tools/blueprint-preview.init.gradle`
photographs one in-game. Claude Code picks up the workflow and style guide automatically from
the project skill in `.claude/skills/minecraft-building/`.

## Development

macOS / Linux (set `JAVA_HOME` to a Java 21 JDK first):

```bash
./gradlew runClient
./gradlew build
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
python3 tools/verify-skill-content.py
python3 tools/blueprint.py validate src/main/resources/data/heroesevolved/blueprints/*.blueprint
./gradlew runClient --init-script tools/blueprint-preview.init.gradle -Pblueprint=bunker_chamber
```

Windows:

```powershell
.\gradlew.bat runClient
.\gradlew.bat build
.\gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
.\tools\verify-effect-geometry.ps1
python tools/verify-skill-content.py
```

Time Stop is a local ability field; it keeps the multiplayer server running.
The test suite is loaded separately and is excluded from the released mod JAR.
