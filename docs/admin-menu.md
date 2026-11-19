# The admin menu

`/heroesevolved admin` opens the operator tools (permission level 2, the same as the other `/heroesevolved`
commands). The server checks permission again for every button, logs each one to the console, and shows the result
above the hotbar.

Online players are listed on the left, with you first. Scroll the list with the mouse wheel; hover a name to see that
player's power, level and position. The chosen player is who the buttons act on, in both tabs.

## Player

The player's card shows:

- their power, and whether it is awake;
- their level, with a bar of Mastery toward the next one;
- their energy, with a bar;
- where they are;
- how far the stories have got with them (Sylar's chapter, and Company visits and wins).

| Row | Buttons |
| --- | --- |
| Power | Pick a power with the arrows, then **Give** it (awakened), give it **Dormant** (unawakened), or **Remove** theirs. |
| Level | Step down or up, jump to 1, 10, 15 or 20; **Awaken** a dormant power with its awakening scene. |
| Mastery | +100, +1k, +10k, or **Reset** to Level 1. |
| Energy | **Refill**, or turn **Unlimited** energy on and off. |
| Tools | Reset **Cooldowns**, **Full Heal**, or **Go To** them (teleports you). |
| Admin | Toggle **God** mode, **Bring here** (teleport them to your position and dimension), or **To cryopod** (below). |

God mode prevents all damage and death, including void damage. It is saved through relogs and respawns, and can be
turned off in the same menu. Enabled players have a gold outline visible to everyone and gold names in the player list.
God mode leaves game mode, stamina costs, cooldowns and scoreboard teams alone.

**To cryopod** is for a new world's prologue, the Last Day. It jumps the whole day to everyone being sealed in
their cryopods: the vault shuts, Claire is in Pod 14, and the Helix Ward's sleeper is drawn as usual. Then it plays on:
the blast, "103 years later", and the waking in the bunker. Press it while the city is still being built (the black
screen on a new world) and the day starts at the pods. It acts on everyone in the Last Day, not only the chosen player,
and is greyed out when there is no day left to skip. `/heroesevolved prologue jump pods` does the same.

**Dormant** replaces the existing power without playing an awakening. It can awaken naturally later, or with **Awaken**.
Both grant buttons respect unique power ownership: they cannot take a power another player still holds.

## World & Events

Each section has a status line and its controls. Its dot is lit while it is going on. Hover a status line that is
cut short to read all of it. Buttons that can't do anything right now are greyed out.

| Section | Buttons | Acts on |
| --- | --- | --- |
| **Eclipse** | **Start** (from first contact), **Totality** (straight to it), **Stop** | the world |
| **Sylar** (the Watchmaker) | **Stalk** (he finds their trail), **Hunt** (he comes now; the fight is in his clock shop), **Dismiss** | the chosen player |
| **Elle** (Elle Bishop) | **Omens** (a day of flickering lights before she comes), **Arrive** (she comes now), **Dismiss** | the chosen player |
| **Village** | **Meltdown** (one minute) or **20 s**, in the villager nearest them; **Raid** on the nearest village; **End** every meltdown | the chosen player's surroundings (64 blocks) |
| **Radio** | **Give tuned radio**: a walkie-talkie already on 101.10 FM | the chosen player |

Use **Village incidents** for the newer village events:

| Control | Result |
| --- | --- |
| **Awakening** | A gifted villager awakens naturally, potentially becoming a renegade. |
| **Renegade** | A hostile gifted villager begins besieging its village. |
| **Guardian** | A friendly gifted villager awakens to protect its village. |
| **Meltdown** | Find and load a village, then begin a meltdown using the configured fuse. |
| **Start raid** / **Stop raid** | Start at the nearest village; stop the raid at the chosen player's position. Surviving raiders remain. |

Incidents search around the chosen player within the server's configured incident range (640 blocks by default).
They begin once the village and its villagers load. The village status includes the number of queued incidents;
**Refresh** updates the status. **Main events** returns to the original controls.

Sylar and Elle refuse a player who can't take part. The player needs:

- an awakened power;
- Survival or Adventure mode (not Creative), in the Overworld, on a difficulty above Peaceful;
- to be outside the power intro and the vision of the past;
- for it not to be totality.

The message above the hotbar says which of these failed.

The same things have commands: `/heroesevolved eclipse …`, `/heroesevolved sylar …`, `/heroesevolved elle …` and
`/heroesevolved eclipse meltdown [seconds]`. See [The Eclipse](eclipse.md), [Sylar](sylar.md),
[Elle Bishop](elle-bishop.md) and [The emergency band](emergency-band.md).
