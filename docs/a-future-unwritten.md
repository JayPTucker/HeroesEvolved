# A Future Unwritten

This expansion introduced deeper Quake, Cryokinesis and Electrokinesis trees, shared power reactions, and the original Hourglass Annex rescue. [The Last Signal](the-past.md) is the current campaign. The skill techniques and reactions below are still playable.

## Build choices and progression

Existing node IDs, point history, and loadouts remain valid. Each expanded tree has eighteen nodes: the original nine plus three new techniques, three behavior upgrades, and three capstones. The named advanced technique in each branch requires its opening node, rather than requiring every old statistical upgrade.

Only one capstone can be learned in a power's build. **Respecialize** refunds all purchases and restores the original actions, while retaining earned points and action cooldowns. Taking or dealing damage prevents immediate respecialization; wait ten seconds after combat. The screen shows the server's purchase result rather than assuming a click succeeded.

Discovering each shared reaction for the first time grants two training points to the current power. Training is capped at six extra points per power. Discovery flags persist across restart and replay, so repeating an operation or changing a build cannot farm the same reward.

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

## The paintings

Precognition, the power that painted these canvases, was removed from the game in November 2026. The bunker's receiver now hands over The Last Signal canvas the first time it records the call. The artwork for the original Annex vision now also depicts the sleepers and hot copper relay of The Last Signal. Copies of the current painting share an identity and update when the sleepers are safe. Older *Unfinished Hour* canvases keep their own map identity. Artwork sources and provenance are in the [artwork notes](artwork/README.md).

## Verification

The gameplay suite covers expanded prerequisites, capstone exclusivity, respecialization, bounded circuits, consumed reactions, delayed damage, cocoon restrictions, locked paintings and one-use intention cancellation. The Annex fixture checks the historical mission in isolation.

Useful commands:

```powershell
.\gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
.\gradlew.bat runClient --init-script tools/skill-ui-preview.init.gradle
.\tools\verify-effect-geometry.ps1
python tools/verify-skill-content.py
.\gradlew.bat build
```

The preview fixtures and high-resolution artwork sources are excluded from the mod JAR.
