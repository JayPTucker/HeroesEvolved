# Warning from a future self

Sometimes the present fails to hold. A narrow violet breach flickers behind someone on quiet, open ground. A distant,
broken cry leaks through, with a low thunderclap, before the opening fails.

One Minecraft day later, a larger breach opens behind that same person. Their view turns smoothly toward it for a
moment. Someone wearing their own skin reaches an arm through and calls out a fragmented warning about Sylar and the
Ender Dragon. White beams catch the visitor and drag them back through. The tear pinches shut with a flash and a boom
audible up to ten chunks away. Nearby players can see and hear the encounter too.

There is no key to press, power to own, reward to collect or choice that can lock the story. The visitor's words are a
clue. The Guide's **Following leads** topic explains how to recognize the encounter without explaining the finale.

| Moment | What happens |
| --- | --- |
| Omen | Three attempts to open a violet seam over roughly four seconds, distant cries and thunder |
| Wait | One Minecraft day (24,000 ticks); sleeping can advance the calendar |
| Arrival | A breach four to six blocks behind its recipient; a brief, smooth turn of their own head |
| Warning | Their double reaches out and gives three short, original chat lines over roughly nine seconds |
| Withdrawal | Light draws the struggling visitor back; the breach snaps shut at twelve seconds |
| Aftermath | The double, view control and chunk ticket clear; natural replay is suppressed for this person |

The world makes its first random selection after two days of running time and considers one new eligible player each
game day after that. Creative and spectator players, sleepers, passengers, people in water, fire or recent combat,
and people in an awakening, pod or prologue are excluded. The opening needs loaded, unobstructed space behind them.
It waits rather than cutting a hole in terrain. It requires the Overworld and no particular stage of power progression.

The waiting encounter survives death, logout and server restarts. If its recipient is unavailable, busy or unsafe when
it comes due, it waits for a suitable moment. An interrupted warning is replayed when they are available again. Walking
away remains possible. With daylight frozen or turned backward, one day of game ticks also makes the warning due.

Operators can use **Admin → Story → Future-self warning** to start the omen, play the warning immediately, inspect its
current state, cancel it or reset the record. Cancel consumes the saved encounter so it cannot resume later; reset
permits another natural selection. **Cancel all events** includes waiting recipients who are offline.

The same tools are available under `/heroesevolved futurewarning omen|now|status|cancel|reset <player>` and require
operator permission. A refused start reports that the player is busy or unsafe, or that the space behind them is blocked;
try standing on dry, open Overworld ground.

Run the gameplay tests with `gradlew.bat runGameTestServer --init-script tools/structure-gametest.init.gradle
-PtestPackages=story/future`. Run the photographed encounter and real admin-button checks with `gradlew.bat runClient
--init-script tools/future-warning-preview.init.gradle`; photographs land in `build/future-warning-preview/screenshots/`.
