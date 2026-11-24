# Player guidance and operator tools: notes for agents

Player instructions: [player-guidance.md](../player-guidance.md). Start at [AGENTS.md](../../AGENTS.md).
The existing event cancellation rules remain in [village-events.md](village-events.md), “Overloads”.

## Keep every feature discoverable

- Update the relevant `guide.heroesevolved.<topic>.*` text in `en_us.json` when adding or changing player-facing
  behaviour. `gui/screen/GuideScreen` is available through Powers and Journal before awakening. Its topics point
  toward Loadout descriptions, key bindings, journals, JEI/Jade, signs and conversations. Extend its topic/content
  list when needed; do not hide an essential input or prerequisite only in an external Markdown doc.
- `GuideScreen.text` resolves current key bindings when drawing. Include an unbound-key explanation when a feature
  needs one; the wheel preview caught “Hold unbound”, which now explains how to assign Ability Wheel instead.
  Avoid hard-coded default keys in tips and keep the inventory eclipse button as a way in if Powers is unbound.
- Add timely mechanical advice to `guidance/GuidanceData.Hint` and `PlayerGuidance` using existing synced gameplay
  state. Hint IDs are saved bits: append IDs, never reorder/reuse them. `GuidanceData.next` owns priority and repeat
  suppression; `GuidanceService` persists personal preferences and receipts in a copy-on-death attachment.
- A selected note is not yet a read note. `PlayerGuidance.Layers` records it only after drawing; menus, F1 and
  cancelled HUD layers must not consume it. The shared readability check defers notes around combat, narration,
  blackouts, flight and scripted dimensions. Keep the off switch and the quiet interval; story dialogue stays in chat.
- Boss/discovery clues can use `tools/survivor-journals.json`. Regenerate with
  `python tools/generate-survivor-journals.py`; `--check` detects stale loot. The generator adds an independent
  optional roll to ruin/ordinary Odessa supplies, preserving earlier signed story notes. These vanilla Book and
  Quills remain editable and grant no story skill points. Never hand-edit generated journal loot.

## Keep operator tools current

- `admin/AdminCommands` walks the server's registered Brigadier trees on refresh, including separately registered
  branches such as Mimicry. The Commands tab must not maintain a second hand-written list of command strings.
  Register new debug commands under `/heroesevolved`; add any genuinely new root to `AdminCommands.ROOTS` and test it.
  The browser filters by the operator's permissions and opens a template in vanilla chat. It does not execute on
  selection. Commands run as the operator, at their position; the sidebar player is not an implicit target.
- Use `AdminEvents` for an event's variants, scope, status and start/cancel lifecycle. Commands complement the
  structured menu. A new event must appear in both, and cancellation includes pending starts and saved resume queues.
  `AdminService` owns player operations and rechecks operator permission on every request. Append payload action
  enum entries, keep client/server payload versions in step, and return fresh state and useful feedback.
- Guidance itself has “Replay tips” and “Give civilian journal” under Debug. Journal delivery reads the actual
  loot table, so editing its content needs no duplicate admin catalogue. Resetting another player's history is an
  operator action, never a normal player's client preference request.

## Verification

- `tools/structure-gametest.init.gradle -PtestPackages=guidance` checks hints, persistence and actual journal loot.
- `tools/admin-gametest.init.gradle` checks operator protections, the command catalogue and admin actions.
- `tools/guidance-preview.init.gradle` photographs every topic, note, binding edge case and book page; `-Plive`
  checks login sync, rendered receipts and the real Tips button over an integrated server connection.
- `tools/admin-preview.init.gradle` clicks the real console, checks filtering and opens a command template without
  submitting it, at normal and compact GUI scales. Look at every changed screenshot. Run the whole gameplay suite
  after focused tests pass, and keep [player-guidance.md](../player-guidance.md) current.
