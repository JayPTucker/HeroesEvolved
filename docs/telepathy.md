# Telepathy — inside another mind

Telepathy now builds **Mind Threads** by holding Read Mind. Each target's Focus grows to 100;
60 permits a Puppet attempt. Up to four threads last thirty seconds after reading stops.
Puppet and illusions build Mental Strain; quiet time lets it fall. At 100, the telepath overloads
and their possession ends. Existing authored witness memories remain available through Read Mind.

The skill tree was reworked in November 2026 around those threads, Strain, possession and illusions,
Mind Link and three skills carry real voices over Simple Voice Chat: a telepath can link one friend or
a party so they hear each other from anywhere, speak into those minds with an inner voice nobody else
hears, hear players talking up to 160 blocks away, and speak inside the head of anyone they have read
deeply. Voices heard inside the head are distorted to sound like thoughts. See
[The skill tree](#the-skill-tree) and [Voices](#voices).

## Core controls

- **Read Mind:** hold to establish Focus, hear surface thoughts, and see a target's current prey.
  Release to stop reading. Fine violet filaments and an iris around the target show the connection.
  Two fingers go to the temple while the other hand reaches toward the mind.
  - **A player's power.** Read a player to 60 Focus and their mind gives up the power that has woken in
    it ("their power is Pyrokinesis"), once per thread. A dormant power, or none, reads as no power awake.
  - **Mind's Eye (Shift + Read Mind, tier 4).** Aimed at a player you've read all the way (100 Focus),
    or else the deepest-read player within 48 blocks, you look through their eyes: their inventory opens
    for you to see (pack, hotbar, armour and off hand, refreshed live) but never to touch, and the last
    twenty lines they said in public chat (within thirty minutes) come back to you with how long ago. It
    costs 10 Strain rather than stamina. They feel someone sifting through their memories. Private
    messages and commands are never remembered, and a player's lines are forgotten when they log out.
- **Suggestion:** choose Look There, Come Here, Leave, Forget Me, or Defend Me for an aimed mob.
  Look There directs it toward the point along your aim; Defend Me requires 60 Focus and makes it
  attack nearby mobs hunting you. Commands expire after ten seconds.
- **Mind Link:** aim at a willing player (a teammate, or anyone crouching to say yes) to link just
  the two of you, or cast at no one to link every willing mind within 32 blocks, a party. The link
  lasts two minutes (three at tier 4, four at tier 5), and linked minds **hear each other's voices
  inside their heads from anywhere in the world** (see [Voices](#voices)). **Shift + Mind Link** turns
  your **inner voice** on or off: with it on, only the minds you reach hear you, however close or far
  they are, and nobody in the room does. Cast again while aiming at a linked player to see through
  their eyes for five seconds. Cast at another target or location to share a visible mark with linked
  minds. Esc returns from shared sight. A Mind Link ends if the telepath dies or changes dimension.
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

## The skill tree

Eighteen nodes in three branches, each one changing a move or tying two together. There are no generic
stat nodes any more. Node IDs are unchanged, so earlier purchases carry over; the old Implanted Command
and Shared Sight copied the core's Suggestion and Mind Link, so their slots now hold Drop It and Far
Hearing.

| Branch | Node | What it does |
| --- | --- | --- |
| **Puppet Master** | Whispered Orders | A Suggestion to a mind you hold a 30+ thread to lasts 20 seconds, and deepens the thread by 20. |
| | **Drop It** (active) | Two fingers, one word, up to 20 blocks. A mob forgets its target and can't attack or shoot for 4 s (with a 60+ thread it drops its weapon); a player can't attack or use items for 2 s. Bosses are disarmed for 1 s and keep their weapons. |
| | Strings Attached | A possessed mob you let go fights for you for 15 s; a possessed player you let go is slowed and can't strike you for 3 s. |
| | **Persuasion** (level 4) | A two-second talk-down, up to 16 blocks: rose petals of thought close round the mind's head and everyone can see them. Keep it in sight to win it: a mob follows you and fights your enemies for 30 s, a villager gives Hero of the Village, a hostile player can't strike you for 6 s. A 60+ thread turns at once. |
| | Silver Tongue | 24 blocks and one second. Enemies within 8 blocks of a persuaded mob turn on it instead of you. |
| | Eden's Voice | Persuasion reaches every mind you hold a 30+ thread to (up to four more); a persuaded player can't strike you for 10 s. |
| **Mind Reader** | Open Book | Hits on a mind you've read land 20% harder at 30 Focus, 40% at 60. |
| | **Far Hearing** (active) | Fingers at both temples. For 30 s you hear every voice within 160 blocks (see [Voices](#voices)); with no one talking, things hunting within 48 blocks whisper where they are. Press again to stop. |
| | Eavesdropper | Far Hearing reaches 320 blocks, and facing a voice threads its speaker (up to 30 Focus). |
| | **Memory Walk** | A palm passed before the eyes. The mind's last 30 s show for you alone as footsteps that unspool backward in time, red where it was hurt, with a pale echo where the memory began, and you're told who hurt it. Aim at the ground instead to see the trails of up to four creatures that passed within 6 blocks. |
| | Deep Read | Memory Walk reaches back a minute and leaves a 40-Focus thread. |
| | Lost in Thought | An enemy you walk relives it: a mob wanders back along its own trail for 6 s and can't take a target; a player is slowed and their sight clouded for 4 s. |
| **Dreamweaver** | Shared Burden | While you share a Mind Link or Hive Link, Strain falls twice as fast and possession builds it half as fast. |
| | Dream Double (active) | Unchanged: a private clone, zombie, creeper, chest or shelter. The hands now weave while you choose, and you see the threads spin into the victim's head. |
| | Waking Fright | A mind that walks into or touches your illusion (Nightmare's double too) is thrown back, slowed and Shaken: your next hit on it deals 6 more. |
| | **Hive Link** | A Mind Link that lasts: up to six willing friends within 24 blocks (teammates, or anyone crouching to volunteer) for ten minutes, through deaths and other dimensions. Linked minds hear each other's voices from anywhere, see each other through walls, and see a small halo over each other's heads that swells while they speak and flares red below a third of their health. |
| | Shared Senses | Whatever hunts a linked mind glows for the whole link, and each of you is told what it is and where. |
| | Mind Network | Your voice reaches inside the head of every player you hold a 30+ thread to, anywhere in the world, and theirs comes back to you; no link or consent needed. With your inner voice on, only they hear you. |

The styles keep their mechanics and gain their own poses: Psychic Storm (hands at the temples thrown at
the point; each second in it threads a foe 10 Focus deeper), Blind Spot (a hand drawn across the face)
and Hive Mind (hands clasped, then spread; for its ten seconds, teammates and crouching volunteers in it
hear each other's voices from anywhere).

## Voices

Mind Link, Far Hearing, Hive Link, Mind Network and Hive Mind relay real Simple Voice Chat audio. The
server works out every route each tick and the voice chat thread sends each microphone packet on:

- **Far Hearing** plays a distant voice from a point a few blocks away in the speaker's direction:
  faint when you face away (6 to 13 blocks off, fainter the farther the speaker is) and clear when you
  turn toward it (3 blocks). A small murmur mark floats in the voice's direction, the speaker glows for
  you, and the action bar names up to three speakers with their distance and bearing. Whispers carry
  only 48 blocks.
- **Mind Link, Hive Link, Hive Mind and Mind Network** play the voice inside the listener's head, with
  no position, **distorted like a thought**: thinned to a narrower band, doubled by two slowly drifting
  copies a few milliseconds behind (a shimmer), given a short echo off the inside of the skull and a
  faint tremble, then softly limited. Each speaker has their own decoder, effect and encoder, so the
  stream stays smooth. The listener sees a thread curl round their own head and is told whose voice it is.
- A voice is only relayed when the two players can't already hear each other (beyond voice chat range
  or in another dimension), so nobody hears anyone twice.
- **The inner voice** (Shift + Mind Link) is the exception: the telepath's voice goes into every mind
  they reach (their Mind Link, their Hive Link, Mind Network's threaded players), however near, and
  the voice chat's ordinary broadcast of it is cancelled, so nobody in the room, in their voice chat
  group or listening with Far Hearing hears a word. It needs someone to reach, and switches itself off
  when the last link ends.
- Relayed voices use their own channel per speaker and their own volume slider, **Telepathy**, in the
  voice chat settings. Muting a player in voice chat mutes their relayed voice too.
- **Closed groups stay private.** A speaker in a normal or isolated voice chat group isn't relayed unless
  the server sets `telepathy.hearsGroups = true`. Two players in the same group are never relayed (they
  already hear each other). `telepathy.hearsVoices = false` turns all of it off.
- **Being listened to can be felt.** After about ten seconds of being heard by someone who isn't a friend,
  the speaker is told the back of their neck prickles (once per listening session).
- Nobody is linked without their say: Hive Link and Hive Mind's voices only take teammates and players
  who crouch to volunteer.

Only `telepathy/MindVoice` imports the voice chat API (it is found by Simple Voice Chat's
`@ForgeVoicechatPlugin` scan). If the API changes and a call fails, it logs once and relays stop;
everything else keeps working.

## Verification

`tools/telepathy-gametest.init.gradle` runs isolated tests for Focus requirements, level-based
resistance, challenge replay rejection, breaking free, interruption, real inventory routing,
borrowed power costs, private delivery, and cleanup, and (`TelepathyReworkGameTests`) every reworked
node: Drop It, the talk-down and breaking it with cover, Eden's Voice, Strings Attached, Whispered
Orders, Far Hearing's routes and marks, Memory Walk on a mind and a place, Lost in Thought, Open Book,
Hive Link's and Mind Network's routes, Shared Burden and Waking Fright, a one-to-one Mind Link's voices,
the inner voice (it reaches a linked friend standing beside you and nobody listening from afar) and the
distortion itself (changed, about as loud, silent after a pause). The voice routes are tested
without the voice chat itself (`MindVoiceRoutes` has no voice chat imports); relaying real audio between
two live clients has not been playtested. `tools/telepathy-kit-preview.init.gradle` photographs every
pose and TelepathyArt piece into `build/telepathy-kit-preview/screenshots/`. `tools/telepathy-preview.init.gradle`
photographs the clone, chest, shelter, selection menu, and resistance HUD in a separate preview
world. Neither test source set is packaged in the released mod.

The complete gameplay regression run passes 312 required tests, including nine Telepathy tests.
Visual previews were inspected in-game. A live client check also verified walking and landing
a melee attack through a possessed mob using the normal movement and attack keys. Forget Me
is tested against a mob that is already aggressive, including target reacquisition attempts.
Two simultaneous live player clients have not been
playtested together; possession timing and resistance balance should receive that practical check.
