# A Future Unwritten — first playable expansion

This release adds Precognition, deeper Quake/Cryokinesis/Electrokinesis trees, shared power reactions, and the Hourglass Annex rescue operation. The complete roster now contains twelve unique powers, 135 skill nodes, and 45 optional active skills. The larger campaign and the other proposed new powers remain future roadmap work.

## Start playing

1. Open **Skills** in the inventory. Quake, Cryokinesis, and Electrokinesis now have a second page. Use the arrow buttons or mouse wheel to reach it.
2. A Precognitive's awakening produces **The Unfinished Hour**, a real map painting. Other players can obtain the first canvas by interacting with an adult cartographer. Established telepathic clues or the future-city vision also let you read the omen through the Journal.
3. Open the **Journal** with **J**. **Read omen** records the clue and gives the first canvas once. **Follow omen** enters the annex after its short construction finishes. Someone receiving a shared canvas can use it or read it in the Journal to discover the operation.
4. Open the partition, cool the copper relay, and escort both named captives to the yellow exit. Their cells are beyond the partition. Stay within ten blocks to guide them, and click a captive for instructions.
5. **Leave annex** in the Journal, or use the exit lever, to return to the saved entry location. Return remains available after the operation ends.

The manual route is **west isolator, east isolator, then the orange relay valve**. Quake techniques can release the partition; electrical techniques can overload its relay connection; cold techniques can stabilize the relay. Powers are alternatives to equipment. No particular power owner is required online.

The operation has two minutes of active visitor time. Its timer pauses when all participants are away and survives restarts. A successful outcome requires both captives rescued and the relay cooled. A casualty or timeout leaves a scarred outcome. Once everyone inside has left, another attempt rebuilds the annex. Offline participants retain their original return tickets across replay. The site accepts at most eight visitors at a time.

## Build choices and progression

Existing node IDs, point history, and loadouts remain valid. Each expanded tree has eighteen nodes: the original nine plus three new techniques, three behavior upgrades, and three capstones. The named advanced technique in each branch requires its opening node, rather than requiring every old statistical upgrade.

Only one capstone can be learned in a power's build. **Respecialize** refunds all purchases and restores the original actions, while retaining earned points and action cooldowns. Taking or dealing damage prevents immediate respecialization; wait ten seconds after combat. The screen shows the server's purchase result rather than assuming a click succeeded.

Discovering each shared reaction for the first time, or saving the annex for the first time, grants two training points to the current power. Training is capped at six extra points per power. Discovery flags persist across restart and replay, so repeating an operation or changing a build cannot farm the same reward.

## Expanded trees

### Quake

- **Fault Script:** write a twelve-step fault along supported ground. The visible marks erupt after two seconds, hitting each enemy once. Short Fuse reduces the delay to one second; Undertow sends a return wave after the first pass.
- **Counterforce:** brace for five seconds, absorbing half of incoming damage into stored force. Cast again to release a forward counterwave, or let stored force release at expiry. Counter Balance increases absorption to 70%. Stolen Thunder increases the release limit and converts stored impact into recoil opposite your aim, allowing an escape or launch.
- **Surgical Fracture:** open a small incision in aimed ordinary material without hurting nearby entities. It initially examines six blocks with hardness at most two. Wider Incision examines twelve. Surgical Silence allows hardness four and avoids adding fresh Sylar attention for that precise technique. Containers and protected terrain are excluded.

### Cryokinesis

- **Rime Loom:** weave a sixteen-block temporary ice path. Double Weave makes it three blocks wide. Suspension Stitch reaches twenty-four blocks and makes ascending stairs when aimed upward. The ice lasts twelve seconds and uses existing terrain restoration.
- **Stillheart Cocoon:** preserve yourself or a friendly creature; players must be teammates or crouching to consent. The occupant cannot act, takes 80% less ordinary damage, and heals when the shell opens. Players are held for three seconds, other creatures for six. Shift targets yourself. Sterile Ice removes harmful effects. Second Spring increases occupant healing to six and gives visible friends within five blocks a small recovery pulse. It does not prevent administrative or other invulnerability-bypassing deaths.
- **Brittle Constellation:** connect up to four visible enemies near the aimed area and briefly freeze them. Hitting a linked target while it is frozen sends damage through the other links. Long Winter extends the link window; another cold attack can re-prime a thawed link. Shattered Sky increases the fracture and re-crystallizes surviving mobs. Player thaw protection still applies.

### Electrokinesis

- **Circuit Thread:** trace a bounded network of connected copper, iron blocks/bars, chains, and lightning rods. Touching enemies are struck once per cast. Live Wire widens the contact radius. Copper Choir bridges one air block between aligned conductors. Each cast visits at most forty-eight conductors, with a bounded search.
- **Ground Sink:** establish a field for eight seconds. Visible combat effects identify it. Nearby friendly targets shed 40% of incoming projectile damage into stored charge. Cast again to discharge it. Insulated Roots extends the field to twelve seconds; Faraday Garden increases absorption to 65%. The charge has a fixed limit.
- **Arc Lattice:** place up to three aimed anchors and connect them with electrical tripwires. Each crossing enemy is hit once per placement. Anchors must be within twenty-four blocks of the previous one. Closed Circuit extends their lifetime. Storm Geometry joins the third anchor back to the first, closing the triangle and strengthening the crossing hit.

## Shared reactions

- **Brittle Fan:** Quake hits a frozen enemy, consumes the frozen setup, and sends ice fragments through a small forward fan behind them.
- **Rime Circuit:** electricity consumes a frozen target's setup to arc to up to three nearby visible enemies.
- **Seam Chorus:** a Sonic Cry marks the wall it strikes with temporary resonance. A sustained Quake Blast fractures those marked blocks faster. The first successful use records the discovery.

Reactions have target limits and a short recovery interval. Secondary reaction hits cannot trigger another reaction recursively. Ordinary PvP, team, and cover rules still apply to the damaging searches.

## Precognition

Precognition was reworked: see [Precognition](precognition.md) for Danger Sense and Clarity,
Premonition, Paint the Future, Saw It Coming, Fate Thread and The Future Is Written. Awakening
still paints the Unfinished Hour, and Paint the Future with no one in view paints it again while the
Annex is unresolved.

The optional Oracle/Red Margin/Shared Fate branches add **Object Reading**, **Red Margin**, and **Omen Relay**. They reveal the operation and nearby villagers, mark three enemies' intentions, or share threat warnings with scoreboard teammates.

Precognition participates in normal exclusive ownership and Eclipse assignment. Its vision observes a planned incident and existing combat intentions. The mod does not calculate arbitrary future decisions by players. Sylar can learn it; copied foresight gives him an occasional collision-checked projectile dodge.

## Paintings and outcomes

The original artwork shows two people behind glass and a dangerous copper heart. Cooling the relay changes the reactor's region on the shared canvas. Rescues revise the holding cells. A completed rescue replaces the painting with its matching prevented-disaster outcome; a failed operation leaves a dark scar. Replays return it to an unresolved future. Copies share the same saved map identity and update with it.

The two images ship as 128×128 vanilla-map palette data, totaling 32 KiB before compression. Image conversion happens during authoring, not on the server. Artwork sources, prompts, and built-in imagegen provenance are in [artwork notes](artwork/README.md).

## Performance and boundaries

The annex is an isolated dimension with a fixed footprint. Construction changes at most 256 blocks per server tick and never overwrites a player settlement. It waits for the captive chunks' entities to load before starting the rescue clock. It does not force distant Overworld chunks. Temporary routes use the existing ice recovery system. The reactor and door edits are small mission-owned changes.

Technique fields have bounded lifetimes and searches. Reactions consume their setup and cannot recurse. Cosmetic geometry uses the existing client budgets. The expansion adds no imported sound replacements.

The annex records story outcomes and discoveries independently of combat rewind. Rewinding a local fight does not refund a completed discovery reward or reset the operation's durable outcome. This release does not implement the whole city-saving campaign, factions, Technopathy, Density Manipulation, Shapeshifting, or Matter Assembly. Those remain in [the expansion roadmap](expansion-roadmap.md).

## Verification

The gameplay suite covers legacy saves, expanded prerequisites, capstone exclusivity, respecialization, bounded circuits, consumed reactions, delayed damage, cocoon restrictions, locked paintings, incident persistence, the full manual rescue route, safe return, and one-use intention cancellation, alongside existing regressions. Native client fixtures render the expanded tree pages and both map-palette artworks. A separate integrated-world fixture verifies ordinary world loading, real-player entry, reactor cooling, and return.

Useful commands:

```powershell
.\gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
.\gradlew.bat runClient --init-script tools/skill-ui-preview.init.gradle
.\gradlew.bat runClient --init-script tools/unwritten-world-preview.init.gradle
.\tools\verify-effect-geometry.ps1
python tools/verify-skill-content.py
.\gradlew.bat build
```

The preview fixtures and high-resolution artwork sources are excluded from the mod JAR. Both clients and server must use the updated mod; the network protocol is 26. Live multiplayer testing is still needed for subjective balance, mission pacing, and performance under concurrent large powers.
