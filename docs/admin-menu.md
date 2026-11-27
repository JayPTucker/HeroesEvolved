# The admin console

Press the admin key (O by default), or run `/heroesevolved admin`. Permission level 2 is required. Every button is
checked again on the server and every change is logged. The result stays at the bottom of the console; hover it to read
a long message. State refreshes every two seconds, and **Refresh** updates it immediately.

Choose an online player on the left. Their name stays in the footer so it is clear who receives a power, travels, or
becomes the centre of an event. Scroll the player list independently from the page. The console fits smaller windows
and larger GUI scales: scroll the main page, or use Page Up / Page Down, to reach its remaining controls.

## Players

The player card shows their current power and whether it is awake, Power Level, Mastery, stamina, dimension and position.

| Section | Controls |
| --- | --- |
| Power assignment | Choose a power with the arrows. **Give awake** replaces their power already awakened; **Dormant** assigns it without a scene; **Remove** takes it away. Both grant options respect unique ownership. |
| Progression | Adjust Power Level by one or jump to 1, 10, 15 or 20. Grant 100, 1,000 or 10,000 Mastery. **Awaken** starts a dormant power's scene. **Reset progress** returns them to Level 1 with zero Mastery. |
| Recovery and travel | Refill stamina, restore health and food, teleport to the player or bring them to you, including across dimensions. |

## Events

Pick an event on the left, then use its controls on the right. An asterisk and **ACTIVE / QUEUED** identify a running
event or a queued start. The card gives its current status and states whether cancellation affects the selected village
or the whole server. Scroll the event list to find more entries on a small screen; scroll the details to reach controls.

| Event | Manual start options | Cancel |
| --- | --- | --- |
| Eclipse | First contact or immediate totality | Ends the server's Eclipse and restores powers. |
| Villager meltdown | 60 seconds, 20 seconds, or find a village using the configured incident range | Ends all live meltdowns and queued meltdown starts; clears the delayed Eclipse meltdown. |
| Village awakenings | Random gift, hostile renegade or friendly awakening | Stops all sieges, in-progress turns and queued awakenings. Existing guardians and surviving hostile mobs remain. |
| Overload storms | Wildfire, Supercell or Blizzard in a nearby villager; **Find village** queues a random storm farther away | Ends all overloads and queued overload starts without declaring a victory or failure. |
| Starfall shower | Start the shower in the Overworld, whatever the time | Stops the shower, live meteors and queued great meteors. A naturally scheduled shower stays off for the rest of that night; future nights still work. |
| Falling meteors | Falling star nearby, great meteor at the selected player's location, or find a village for a great meteor | Cancels all falling and queued meteors. To stop further scheduled stars too, cancel the Starfall shower. |
| Class Ultimates | Cast the selected player's class Ultimate (no stamina or wait; Offense still needs a foe in front of them), or ready it again | Stops the selected player's Ultimate, or every Ultimate playing when no player is selected; held foes are let go. Also `/heroesevolved ultimate list`, `cast [player] [mechanic]`, `stop [player]` and `reset [player]`. |
| Village raid | Starts at a loaded village bell within 64 blocks of the selected player | Stops the raid at their position or nearby bell. Surviving raiders remain. |
| Company operations | Investigator, prisoner convoy or home raid | Recalls all operations, releases prisoners and clears queued investigations. Attention and future automatic scheduling remain. |

Nearby meltdown and overload starts need an eligible grown villager within 64 blocks. Village incident searches prefer
a loaded bell nearby, then search within the configured incident range (640 blocks by default). Queued incidents begin
when the village and its villagers are loaded; cancelling them releases their loading tickets before they can start.
Company convoys need a suitable loaded village and prisoner. Refused starts explain why in the footer.

## Story

| Scene | Controls |
| --- | --- |
| Sylar's Collection | Start a fight for the selected player and nearby companions; cancel sends every fighter home. **Advance story** moves the prisoner/list story one step. **Give Repair Ticket** gives the rematch item. |
| The Last Day | Enrol the selected player, or cancel their participation and wake them in the bunker. **Jump all to pods** skips the shared day to everyone being sealed in their cryopods, then plays the blast and waking. It also works while the city is being prepared. |
| Hourglass Annex | **Prepare annex**, then **Enter annex** once its construction is ready. Cancel returns the selected visitor. |
| The avenue | Enter the playable avenue mission; cancel returns all runners. Operator entry bypasses the usual story unlock. |
| Night Three | Enter the past bunker; cancel returns all runners. Operator entry bypasses the usual story unlock. |
| Odessa vision | Replay the awakening vision for the selected player without changing their power. It waits for the city to be available. Cancel returns all vision viewers and stops queued visions and gathers. |

Scene availability still matters: a player already in another scene, a full encounter, a city still preparing, or a
disabled Sylar fight can refuse entry. Cancellation uses each scene's normal return path.

## Debug

Live diagnostics show the selected dimension, server tick and clocks, queued incidents, active storms, meltdowns,
sieges, turning villagers, meteors, Company operations, distress calls, radio connection and story status.

| Tool | Result |
| --- | --- |
| God mode | Toggles complete protection from damage and death, including the void. Saved through relogs and respawns; gold outline and player-list name while enabled. |
| Free stamina | Toggles unlimited stamina for the selected player. |
| Reset cooldowns | Clears their ability cooldowns. |
| Cancel awakening | Cancels their awakening; time visions have separate controls under Story. |
| Give tuned radio | Gives a walkie-talkie on 101.10 FM. |
| Company attention | Clear, Noticed, Watched or Hunted presets for the selected player. Cancel current operations separately under Events. |
| Cancel all events | Server-wide cleanup: world events, queued incidents, Company operations, vanilla raids (including villages with nobody nearby), active stories and awakenings. |

Cancellation does not reverse terrain damage, remove established guardians or surviving raiders, or disable future
automatic event scheduling. Village growth and expansion tools are managed separately.
