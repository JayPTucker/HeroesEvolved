# Heroes Evolved

A Minecraft 1.21.1 NeoForge mod with unique player powers, shared-world story
events and evolving abilities. Build and run with Java 21.

## Skills and progression

Open the survival inventory and click **Skills**, next to the recipe book.
Every power has three named branches of six nodes: eighteen per power, 288 in all.
The Abilities page is also the skill tree: each branch is drawn as its real tree, and you learn skills and put them on keys right there.
Powers grow from Level 1 to 20 (Manifesting, Controlled, Practiced, Mastered, and Evolved after a
per-power trial). Levels unlock the core kit and give skill points; story notes hidden in the world
give more. Put any ability on any of eight keys, or cast anything from the **ability wheel** (hold R).
Abilities rank up with use. Purchases and loadouts persist across death and relogging. See
[Power Levels, keys and the ability wheel](docs/progression.md).

All sixteen powers have trees: Quake, Flight, Cryokinesis, Electrokinesis,
Telekinesis, Telepathy, Regeneration, Time Manipulation, Radiation, Sonic
Resonance, Superspeed, Precognition, Pyrokinesis, Invisibility, Phasing and
Empathic Mimicry. Sonic voice attacks use recorded screams, including a positional
sustained scream that stops when you release its key.

See [the skill guide](docs/skill-trees.md) for the original optional skills,
point costs, prerequisites and loadout controls.

See [Superspeed](docs/superspeed.md) for sprint controls, progression and the Enhanced Speed / Flash-inspired design.

See [A Future Unwritten](docs/a-future-unwritten.md) for the first playable expansion:
135 skill nodes, 45 optional actions, Precognition, shared reactions, prophetic
paintings, and the repeatable Hourglass Annex rescue operation. Find a cartographer
or share a prophetic canvas, then open **J** to read and follow the omen.

See [Flight](docs/flight.md) for the rebuilt aerial kit and all eighteen purchasable skills: Momentum, Skyburst, Meteor Drop, Skyhook, Tempest, plus the Sky Guard, Jetstream and Stormbreaker builds.

See [Regeneration](docs/regeneration.md) for Claire Bennet's reworked kit: she can't be killed (she goes down and gets back up), limbs grow back from the bone out, and Regrowth, Blood Bond and Second Life.

See [Ted Sprague](docs/ted-sprague.md) for the reworked Radiation kit: a Core that makes you stronger as it runs hot, a spreading Radiation Sickness, and a held heat beam.

See [Electrokinesis](docs/electrokinesis.md) for the rebuilt lightning kit: Charge, tap-or-hold Chain Lightning, and the delayed Thunderstrike.

See [Telekinesis](docs/telekinesis.md) for the rebuilt physical force kit and all eighteen skills: orbiting fragments, staggered volleys, Iron Rain, precise grips, projectile catches and aimed force fields. [Power workshop](docs/power-workshop.md) records proposed directions for Phasing, Sonic, Telepathy and Mimicry.

See [Pyrokinesis and Helixite](docs/pyrokinesis.md) for the fire power, its real-fire rules,
reactions with Cryokinesis, Flight and Superspeed, and the new Helixite crystal ore.
New players wake sealed in a Cryochamber in a stasis bunker beneath world spawn,
where the world's past waits to be read. See [The Past](docs/the-past.md).

See [Invisibility, Phasing and Empathic Mimicry](docs/new-powers.md) for the three newest
powers and the techniques added to every original tree.

See [The Formula](docs/formula.md) for awakening unclaimed powers without waiting for an
Eclipse: draw Regeneration blood (donated or forced), spin it in a Centrifuge, synthesize
it in a Synthesis Station built from Helixite, then inject it.

See [Carrying](docs/carrying.md) for carrying mobs and players, safe placement, and the shared movement/visibility rules.

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
