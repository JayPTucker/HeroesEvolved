# Space-Time Manipulation

Inspired by [Hiro Nakamura's powers](https://heroes.fandom.com/wiki/Hiro_Nakamura#Powers): spatial travel, carrying someone by touch, relative time, stopped time, reversal, and travel into the past. This is a gameplay adaptation; it does not simulate every possible past or future.

## Default controls

These are the default keys; rebinding and the inventory skill tree still work.

| Level | Key | Ability | Use |
| --- | --- | --- | --- |
| 1 | G | Fold Space | Blink to a visible surface or into open air. Cancels falling momentum. From level 2, Shift carries one nearby living companion if there is room. Players must be teammates or crouching. |
| 1 | H | Between Seconds | A slow-time bubble follows you. Entity ticks and projectile motion slow; the server clock keeps running. Shift + H ends it. |
| 3 | Z | Still World | Freeze entities in a fixed area. Your attacks accumulate and land when time resumes. Shift while starting includes one touching companion; Shift + Z while active resumes time for free. |
| 4 | X | Rewrite the Moment | Restore the recorded local timeline from fifteen seconds ago, including changed blocks, containers, and recorded entities. Return to your earlier position and recover lost health. |
| 5 | V | Walk the Past | Shift + V records an area; V visits the recorded past with nearby companions. V in the past returns without energy cost or an entry-cooldown delay. |

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

- Golden clock-face marks, violet expanding rings, and interlocking arcs distinguish time fields from generic magic bursts. Teleports collapse and reform at their actual endpoints; rewinds retain reverse path ribbons.
- Geometry is bounded and uses the shared detail setting/particle budget. No new audio files: imported Blink, Slow/Stop, and cinematic recordings are preserved.
- Time Stop and selective stasis also block movement and interaction packets. Frozen players cannot attack, use items, drop/swap items, or click inventory slots to bypass the effect. Spectators are excluded.
- Fields stop on death, logout, dimension change, suppression, or power removal. Frozen projectile velocity/gravity are restored; no permanent AI flags are written.
- Blink and anchor travel reject obstructed, fluid-filled, out-of-bounds, or unloaded destinations. Blink avoids generating chunks along its aimed path. Personal history is at most 25 samples per traveller.
- Fifteen-second rewinds retain the existing local recording limits and completeness checks. They do not promise recovery of unrecorded distant chunks. Large existing block-rewind transactions remain a separate performance concern.

## Verification

`gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle`

The suite includes anchor serialization/blocked arrivals, personal-state restoration without inventory changes, consumed history, real selective tick suspension, companion immunity, projectile release, open-air Blink/fall momentum, and idempotent story flags, alongside existing rewind and other-power regressions.

Run `tools/verify-effect-geometry.ps1`, `python tools/verify-skill-content.py`, and `gradlew.bat build` for geometry, content, and packaging checks. Multiplayer feel and the new visual composition still need an in-game playtest.
