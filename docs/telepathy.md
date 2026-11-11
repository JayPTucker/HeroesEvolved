# Telepathy — inside another mind

Telepathy now builds **Mind Threads** by holding Read Mind. Each target's Focus grows to 100;
60 permits a Puppet attempt. Up to four threads last thirty seconds after reading stops.
Puppet and illusions build Mental Strain; quiet time lets it fall. At 100, the telepath overloads
and their possession ends. Existing authored witness memories remain available through Read Mind.

## Core controls

- **Read Mind:** hold to establish Focus, hear surface thoughts, and see a target's current prey.
  Release to stop reading. Fine violet filaments and an iris around the target show the connection.
- **Suggestion:** choose Look There, Come Here, Leave, Forget Me, or Defend Me for an aimed mob.
  Look There directs it toward the point along your aim; Defend Me requires 60 Focus and makes it
  attack nearby mobs hunting you. Commands expire after ten seconds.
- **Mind Link:** nearby teammates and crouching volunteers join a link. Cast again while aiming at
  a linked player to see through their eyes for five seconds. Cast at another target or location
  to share a visible mark with linked minds. Esc returns from shared sight.
- **Puppet:** aim at a deeply read mob or player. A short windup precedes control. The telepath's
  body stays in place; the camera moves to the target. Use normal movement, mouse, attacks,
  inventory, item interaction, and ability keys. Esc releases control.
- **Nightmare:** connected players see an approaching double of the telepath; affected mobs flee.
  Private whispers and violet edges accompany the illusion. The old unavoidable damage over time
  and blindness have been removed so the false scene remains visible.

## Fighting possession

The victim receives a **fresh arrow-key prompt** and a resistance bar, starting during windup.
Press the indicated arrow once; holding it or replaying a previous prompt adds no progress.
Correct presses fill the bar. A wrong arrow removes five points. Each answer changes the prompt;
the server accepts at most one every five ticks.

Resistance per correct answer is `22 + 2 × (victim Power Level − telepath Power Level)`, bounded
between 9 and 40. This compares the actual 1–20 Power Levels. A lower-level telepath is much easier
to resist. The victim can still move during windup. Reaching 100 breaks the connection, including
before the takeover finishes. After release, twenty seconds of resistance prevents immediate
re-possession.

Windup takes 2 seconds at tier 4 and 1.5 seconds at tier 5. Control lasts at most 5.4 or 6 seconds,
respectively. A hit of at least one heart to the telepath interrupts the connection. Death,
logout, a dimension change, power suppression, freezing, excessive distance, or power removal also
ends it. Creative/spectator players and the vanilla dragon/wither cannot be possessed.
Player possession and hostile illusions respect server PvP and team rules.

## The real body and inventory

Possession forwards ordinary item, inventory, combat, and block interactions through the victim's
server handler. Their real inventory and equipped loadout are shown to the controller. Their
abilities spend their own stamina, resources, and cooldowns; items consumed, moved, or dropped
remain changed. Inventory contents are never copied between players on the server. Cursor items
are returned through the normal menu-close behavior when possession ends.

Held ability inputs are released on exit, and forward-flight input is cleared. Persistent toggles
retain their normal native behavior. Creative permissions and account settings do not transfer.
Mounted targets are excluded at entry. Mobs can be moved and made to
perform their native melee attack; they do not gain a player inventory or player power loadout.

## Speaking through a possessed player

While a telepath controls another player's body, the controller's chat is said by the body: everyone
sees it under the possessed player's name. The line goes out unsigned (the controller's
secure-chat signature only vouches for the controller), and the server log records the real author.

Commands run as the possessed player, from their position, at no more than the lower of the two
players' permission levels, so possessing an op never grants op, and an op controller is held to
the victim's level. Command feedback reaches both players. Signed commands such as `/me` and
`/msg` keep the controller's signature but show the possessed player's name.

The possessed player watches their own chat box fill: a small lilac line where their chat input
would be shows exactly what the controller is typing, live, and clears when the line is sent, the
chat box is closed, or control ends. Mobs have no voice, so chat and commands from a telepath
possessing a mob stay the telepath's own.

## Private illusions

Learn **Dream Double** in the Dreamweaver branch (existing `telepathy_2_1` purchases are preserved).
Aim at a player and choose:

1. A moving copy of the telepath, using their skin and equipment.
2. An approaching zombie.
3. An approaching creeper.
4. A false chest.
5. A small false shelter made of cobblestone and oak.

The scene appears ahead of the affected player. Moving apparitions approach the place where the
victim stood when the illusion was created. Only that player receives it. Others see the unchanged
world. Touching or attempting to interact with it dissolves it into violet fragments and a chime.
An illusion lasts eight seconds, and each victim can have one at a time. Nightmare uses a shorter
six-second double. These are render-only scenes: no server entity, collision, container, or block
is created. A false shelter is a fixed small layout, not a copy of an arbitrary building.

## Verification

`tools/telepathy-gametest.init.gradle` runs isolated tests for Focus requirements, level-based
resistance, challenge replay rejection, breaking free, interruption, real inventory routing,
borrowed power costs, private delivery, and cleanup. `tools/telepathy-preview.init.gradle`
photographs the clone, chest, shelter, selection menu, and resistance HUD in a separate preview
world. Neither test source set is packaged in the released mod.

The complete gameplay regression run passes 312 required tests, including nine Telepathy tests.
Visual previews were inspected in-game. A live client check also verified walking and landing
a melee attack through a possessed mob using the normal movement and attack keys. Forget Me
is tested against a mob that is already aggressive, including target reacquisition attempts.
Two simultaneous live player clients have not been
playtested together; possession timing and resistance balance should receive that practical check.
