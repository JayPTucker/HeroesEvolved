# Skill trees and Sonic Resonance

Open your survival inventory and click **Skills** (the eclipse beside the recipe book), or press
**P**. Both open the **hero menu**. Your power, level and unspent points sit along the top, your
Mastery and stamina along the bottom, and five tabs run under the title (Tab and Shift+Tab switch
between the first three):

- **Loadout**: everything you can cast (your five core abilities, your class ability and the skills
  you've learned), each tagged Offense, Defense, Support or Utility, beside your eight ability keys.
  Select an ability, then click a key slot to put it there; right-click a slot to empty it.
  Everything is also on the **ability wheel** (hold R). See
  [Power Levels, keys and the ability wheel](progression.md).
- **Skill Tree**: the three branches, each drawn as its real tree: the root on top forks into an
  upgrade path and a technique path that ends in the capstone. Connectors light up as you learn.
  Click a hex to see it in the card on the right; skills you can learn now pulse. Click **LEARN**
  (or click the selected hex again) to spend the points. **RESPEC** refunds everything; it needs
  ten seconds out of combat.
- **Class**: choose how you play your power, Offense, Defense or Support. See [Classes](classes.md).
- **Journal** and **Settings** open the story journal and the effects settings.

Flight has a fully redesigned eighteen-node tree: see [Flight](flight.md#purchasable-skills) for Sky Guard, Jetstream and Stormbreaker. Its passives change specific mechanics instead of granting the generic stat bonuses below.

Telekinesis also replaces all eighteen nodes with interaction-based progression. See [Telekinesis](telekinesis.md) for physical fragments, aimed fields, projectile counterattacks and the Fine Manipulation, Kinetic Arsenal and Bastion branches. Its old blanket stat bonuses no longer apply.

Cryokinesis uses Sculptor, Glacier and Winterbinder branches. Its roots and masteries change
constructed ice, Glacier Ride and Frost/shatter interactions instead of applying the generic
stat bonuses below. See [Cryokinesis](cryokinesis.md) for the complete connected kit.

Every tree now has eighteen nodes: the original nine below, plus a technique, an
upgrade and a capstone in each branch (see [the new powers guide](new-powers.md)).

**Signature abilities** sit on top of the eighteen: one showpiece active hung under a branch's Mastery
node, at Level 10 for 3 points. They are not capstones, so taking one never blocks your capstone.
Mark a node `"signature": true` in `skill_trees.json` (it must be active). There are three so far:
[Blackout](electrokinesis.md#signature-blackout) (Electrokinesis, Groundkeeper),
[Singularity](space-time-manipulation.md#signature-singularity) (Time Manipulation, Wayfinder) and
[Phoenix](pyrokinesis.md#signature-phoenix) (Pyrokinesis, Phoenix). To see their effects (and the machine
short-out, charge and siphon effects) at night from a fixed camera, run
`./gradlew runClient --init-script tools/signature-preview.init.gradle`; screenshots land in
`build/signature-preview/screenshots/`. Gameplay checks are in `SignatureAbilityGameTests` and `GridGameTests`.
See
[A Future Unwritten](a-future-unwritten.md) for new techniques, capstones, respecialization
and discovery points. Every original
branch contains an opening passive, an optional active skill, and a mastery
passive. Existing power skills keep their normal level unlocks.

You receive **3 points at awakening**, then **4 every three Power Levels** (21 at Level 15,
28 Evolved), plus up to 8 from story notes; see [progression](progression.md). Completing every
branch costs more than that, so the tree supports different builds. Purchases and assignments
survive death and relogging. The "level" requirements in the table below are tiers: tier 2 is
Power Level 3, tier 4 is Level 10.

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
| Flight | Rescue Rush | Barrel Roll | Thermal Lance |
| Cryokinesis | Crystal Lance | Frost Armor | Cold Snap |
| Electrokinesis | Arc Lance | Static Screen | Overcharge |
| Telekinesis | Vector Lance | Gravity Well | Vector Step |
| Telepathy | Psychic Silence | Shared Focus | Terror Pulse |
| Regeneration | Cauterize | Adrenal Rush | Transfusion |
| Space-Time Manipulation | Selective Stasis | Spatial Anchor | Second Chance |
| Radiation | Gamma Lance | Lead Skin | Decontaminate |
| Sonic Resonance | Focused Shriek | Dissonant Wave | Resonant Guard |
| Superspeed | Reflex Focus | Momentum Strike | Slipstream Rescue |

The screen is laid out like the vanilla Advancements screen. The tree grows left to
right from your power's emblem. Drag to pan (the mouse wheel scrolls; Shift + wheel
scrolls sideways). Passive nodes have square frames, active skills rounded frames and
capstones spiked frames; learned nodes and their connecting lines turn gold, nodes you
can afford pulse, and locked nodes are dimmed. Hover a node for its full details.
A tree can set an optional `"icon"` item id per node in `skill_trees.json`.

Click a node to select it; its description appears in the card. Use **LEARN** to learn it.
The key chips use your actual key bindings, and an ability occupies one key at a time. Moving an
ability preserves its cooldown and releases whatever was held on the key, preventing abandoned
held beams or screams.

The new targeted skills reach up to forty blocks and respect solid cover. Cold
attacks use the existing real freeze state. Defensive screens reflect incoming
projectiles; Gravity Well pulls targets over time. Searches cap affected targets,
and the optional skills do not scan or destroy terrain. Shared defensive buffs
apply to the caster and nearby scoreboard teammates, avoiding enemy buffs in PvP.

See [Space-Time Manipulation](space-time-manipulation.md) for the redesigned time skills, controls, and story progression.

## Sonic Resonance

Sonic Resonance was reworked around Emma Coolidge and Black Canary. See
[Sonic Resonance](sonic-resonance.md) for the following:

- Sound Sight and Resonance.
- Canary Cry: tap for the cry, hold for the scream.
- Resonant Note, Wall of Sound, Sonic Vault and Crescendo.

The Cry, the burst and the held scream are original synthesized sounds (no recordings). Each is a
chorused, formant-shaped shriek over a bowed cello-like drone, with a pressure blast and glassy high
partials. Rebuild them with `python3 tools/generate-sonic-audio.py` (needs numpy and soundfile in
`build/audio-tools`). The held scream follows the caster and fades out over 100 milliseconds when it
ends: on release, incapacitation, death, dimension change, logout or a missing server heartbeat. It
lasts at most five seconds.

## Verification

Run the gameplay suite with
`gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
Run mesh and packet checks with `tools/verify-effect-geometry.ps1` and catalog/audio
checks with `python tools/verify-skill-content.py`.

For actual Minecraft-rendered layout previews, run
`gradlew.bat runClient --init-script tools/skill-ui-preview.init.gradle`.
The isolated client saves the Abilities page with nothing learned, with one branch partly learned,
and with a wider build, at GUI scales two and three, then the eight key chips and the ability wheel,
in `build/skill-ui-preview-world/screenshots/`, then exits. This
fixture is excluded from the released JAR.
