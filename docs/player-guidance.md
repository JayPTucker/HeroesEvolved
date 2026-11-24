# Finding your own way

Open the hero menu with **P** (or your rebound Powers key), or the eclipse button in your inventory, and choose
**Guide**. It is available before awakening and throughout the story. **J** opens the Journal for your current
story leads and discovered records.

The Guide explains things a player cannot easily infer from the world: assigning learned abilities, unbound
keys, the wheel, stamina, Mastery, fighting controls, carrying people, equipment, machine recipes and the powered
End portal. Its instructions show your current key bindings. **Key bindings…** opens the binding list directly.
Scroll, use the arrow buttons, or use Up/Down and Page Up/Page Down to read; Left/Right changes topic.
Growing stronger also shows your own Evolved trial and progress after awakening.

**The Hungry Cage** explains the Wither's Power Level 16 entry requirement, solo play, attack chains, three-hit
stance breaks, jumpable pressure, seven-second anchor feeds and the return breach. It can be read before entering.

Following leads also describes the [warning from a future self](future-self-warning.md): a flickering breach can
return a day later with a visitor's warning. It needs no input or owned power and waits while its recipient is unavailable.

## Occasional field notes

A small card introduces useful controls and explains an unusual rule when you encounter it. Each note appears once
for your character in that world. Notes wait while you are in a menu, hiding the HUD, fighting, flying, in the
scripted dimensions, or recovering from a scene. They do not pause the game. There is a quiet interval between notes.
Low-stamina advice waits until you can safely read it.

Use **Tips: On/Off** in Guide to switch them off. The preference and the notes already shown survive death,
reconnecting and server restarts. The Guide remains available with tips off; enabling tips again does not repeat
ones you already saw.

## Words left behind

Ruined houses and Odessa's ordinary supply caches have an additional 40% chance to contain one of six civilian
journals. These are vanilla **Book and Quills**, with a named cover, the writer's name and three short entries. The
last sentence was never finished. They can be kept, shared, written in or signed like other books. Already opened
containers are not refilled.

| Journal | Writer | What their account can help you notice |
| --- | --- | --- |
| A Watch at Dusk | Miriam Vale | The people on a watch list, and glass that lights before a collector acts |
| Glass That Fed It | Elias Venn | The Wither's breach, shelter and feeding anchors |
| The Eye That Waited | Nessa Ward | A portal offering that waits while someone catches their breath |
| Under a Violet Sky | Owen Pike | Crystal feeds, grounding plates, updrafts and shelter in the End |
| The Bell Kept Ringing | Sela Moss | Fixed warnings, a gifted enemy's recovery, evacuation and the emergency band |
| Nine Quiet Footsteps | Arun Bellamy | A moving silence around a Company escort, and the cost of drawing attention |

These observations leave room to experiment. They do not reveal the finale's twist or require a particular power.
They are ordinary civilian accounts and do not grant story-note skill points. The existing signed story notes and
their rewards remain available alongside them.

## Operator tools

Operators can open the admin console with **O** (or their rebound admin key), or `/heroesevolved admin`.
Story includes **Wither: Containment Failure**: open a breach near the selected player, enter an eligible fighter,
advance the encounter's phase, read its live status or cancel and return every fighter safely.
Debug includes **Replay tips** for the selected player and **Give civilian journal** for a random recovered notebook.
Story includes **Future-self warning** with omen, immediate warning, live status, cancellation and reset for the selected
player. Cancel all events also clears saved warnings for offline players.
The **Commands** tab searches the live server's registered mod commands, including village projects, traces,
blueprints and Mimicry tools. Select a template to open it in chat, replace its argument placeholders and submit it
when ready. These commands use the operator's location and explicit arguments; selecting a player in the sidebar
does not silently change a command's target. New commands appear automatically on refresh.

## Checking this feature

Run `gradlew.bat runGameTestServer --init-script tools/structure-gametest.init.gradle -PtestPackages=guidance` for
hint selection, personal preferences, persistence, invalid receipts and real loot generation. Run
`gradlew.bat runClient --init-script tools/guidance-preview.init.gradle` for the Guide at two GUI scales, every
field note, rebound/unbound keys and every journal page. Photos land in `build/guidance-preview/screenshots/`.
Add `-Plive` for an actual login, HUD delivery and Tips-button check against an integrated server; its photographs
land in `build/guidance-live-preview/screenshots/`.

Journal text lives in `tools/survivor-journals.json`; run `python tools/generate-survivor-journals.py` after editing
it and `python tools/generate-survivor-journals.py --check` to check the generated loot. The generator adds a separate
optional roll to existing supplies and story loot. It does not change structures or refill containers.
