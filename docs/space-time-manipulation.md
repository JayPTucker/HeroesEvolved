# Space-Time Manipulation

Inspired by [Hiro Nakamura's powers](https://heroes.fandom.com/wiki/Hiro_Nakamura#Powers): spatial travel, carrying someone by touch, relative time, stopped time, reversal, and travel into the past. This is a gameplay adaptation; it does not simulate every possible past or future.

## Default controls

These are the default keys; rebinding and the inventory skill tree still work.

| Level | Key | Ability | Use |
| --- | --- | --- | --- |
| 1 | G | Fold Space | Blink to a visible surface or into open air. Cancels falling momentum. From level 2, Shift carries one nearby living companion if there is room. Players must be teammates or crouching. |
| 1 | H | Between Seconds | A slow-time bubble follows you. Entity ticks and projectile motion slow; the server clock keeps running. Shift + H ends it. |
| 3 | Z | Still World | Freeze entities in a fixed area. Your attacks accumulate and land when time resumes. Shift while starting includes one touching companion; Shift + Z while active resumes time for free. |
| 4 | X | Rewrite the Moment | Restore the recorded local timeline from fifteen seconds ago, including changed blocks, containers, and recorded entities. Return to your earlier position and recover lost health. It costs **130 stamina** (hold Shift to overexert when you're short) and recovers in **4 minutes at Level 4, 2½ minutes at Level 5**. While it's ready, a faint ghost of you, ringed in violet and visible only to you, stands where it would take you. |
| 5 | V | Walk the Past | Shift + V records an area; V visits the recorded past with nearby companions. V in the past returns without energy cost or an entry-cooldown delay. |

### Rewinding your own death

From Level 4, Rewrite the Moment can undo your death. For **fifteen seconds after you die**:

- The death screen shows **Rewind 15 seconds** with a countdown. Pressing it respawns you and, a tick
  later, rewinds: you're back where you stood fifteen seconds before you pressed it, alive, with the
  health, inventory and experience you had then. Or press **Respawn** and then the **Rewind key**
  while the fifteen seconds last; it does the same, even from another dimension.
- The longer you wait, the closer to your death you land: you always go back fifteen seconds from now.
- Everything you dropped when you died is un-made, as are the experience orbs. Drops sitting in a chunk
  that unloaded while you were away are refused when the chunk loads again, so nothing is duplicated.
  Nearby blocks, containers and creatures rewind as usual.
- It needs Rewind off cooldown when you die, and costs what the key does: 130 stamina and the full
  cooldown. A body just back from the dead rarely has that much stamina, so it overexerts (Weakness,
  Nausea and some damage) instead of failing. It is not offered during
  the time-travel story missions, and in hardcore it returns you to Survival.

Configured costs and cooldowns are adjusted by the shared balance multipliers; the HUD shows final values. Still World now lasts at most 6/8/10 seconds by level and stores at most 12 damage per player or 40 per mob. Ranges and level unlocks retain their configuration. Slow and Stop supersede one another. Entity freezing does not freeze redstone, scheduled block ticks, weather, or the entire multiplayer world.

## Optional skills

Learn and equip these through the inventory **Skills** button. Existing purchases retain their node IDs, points, and key assignments, with the new mechanics replacing the old ones.

| Branch | Skill | Base stamina / cooldown | Mechanic |
| --- | --- | --- | --- |
| Control | Selective Stasis | 22 / 8 seconds | Actually pause a visible living target within 40 blocks, for 4 seconds at level 2 through 5.5 seconds at level 5. One target per traveller; retargeting releases the previous one. Respects solid cover and PvP/team rules. |
| Force | Spatial Anchor | 15 / 3 seconds | Shift + key remembers safe ground. Normal activation teleports back within 512 blocks, in the same dimension. The destination survives relogging and death, but must still be loaded and safe. |
| Survival | Second Chance | 30 / 20 seconds | Undo five seconds of your own position, lost health, and acquired fire. Does not modify inventories or the world. Requires a fresh five-second history and consumes that history on use. |

The existing passive discounts and damage bonuses still apply. Remembering an anchor uses its normal skill cost/cooldown. Shift retains the mod's existing overexertion behavior when stamina is insufficient.

## Story: A Future Unwritten

The acquisition vision is now framed as a preventable future. Its compact captions lead into five durable advancements, visible in the vanilla Advancements screen:

1. **A Future Unwritten** — escape the future city's destruction.
2. **No One Left Behind** — carry a companion through space or share stopped time by touch.
3. **A Place to Return** — establish a spatial anchor.
4. **The Butterfly Effect** — successfully rewrite fifteen seconds.
5. **The Road Not Taken** — enter a recorded past.

Milestones can be earned out of order. Each gives one piece of guidance in chat; flags survive death and relogging. Existing witnesses of the future city receive its milestone on login. This adds a playable progression and persistent hooks, not a finished city-saving campaign.

Future missions can query `TemporalStoryService.has(player, milestone)` to branch dialogue, gate evidence, or choose a mission stage. Flags are independent from telepathy's existing memory sequence and do not reset it. Suggested continuation: a telepath identifies a survivor's memory, the traveller records the meeting place, and the group revisits that record to discover who triggered the disaster. Existing successful power activations still attract Sylar's attention.

## Effects and multiplayer behavior

- Time reads as clockwork made of light, drawn in the same glowing style as the lightning so it shows in daylight:
  - **Fold Space:** the air where you stood pinches shut behind a fading echo of you, a seam of light runs to where you land, and it opens there around a small clock.
  - **Between Seconds:** an amber clock face spreads under you and its hands crawl round. A ring marks the field's edge, and inside it sand hangs in the air around you, barely falling.
  - **Still World:** the same clock in cold violet with its hands stopped at ten past ten. Motes hang perfectly still around anyone inside.
  - **Rewrite the Moment:** violet trails run backwards from now to then, and ghosts of you appear along the path, newest first. You arrive as rings collapse onto you and the hands spin backwards.
  - **Remember This Moment:** a golden frame snaps out around you with a flash, like a photograph.
- Geometry is bounded and uses the shared detail setting/particle budget. No new audio files: imported Blink, Slow/Stop, and cinematic recordings are preserved.
- Time Stop and selective stasis also block movement and interaction packets. Frozen players cannot attack, use items, drop/swap items, or click inventory slots to bypass the effect. Spectators are excluded.
- Fields stop on death, logout, dimension change, suppression, or power removal. Frozen projectile velocity/gravity are restored; no permanent AI flags are written.
- Blink and anchor travel reject obstructed, fluid-filled, out-of-bounds, or unloaded destinations. Blink avoids generating chunks along its aimed path. Personal history is at most 25 samples per traveller.
- Fifteen-second rewinds retain the existing local recording limits and completeness checks. They do not promise recovery of unrecorded distant chunks. Large existing block-rewind transactions remain a separate performance concern.

## Verification

`gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle`

The suite includes anchor serialization/blocked arrivals, personal-state restoration without inventory changes, consumed history, real selective tick suspension, companion immunity, projectile release, open-air Blink/fall momentum, and idempotent story flags, alongside existing rewind and other-power regressions.

Run `tools/verify-effect-geometry.ps1`, `python tools/verify-skill-content.py`, and `gradlew.bat build` for geometry, content, and packaging checks. Multiplayer feel and the new visual composition still need an in-game playtest.
