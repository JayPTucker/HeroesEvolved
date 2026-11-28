# Telepathy: notes for agents

The tree and the Simple Voice Chat integration (`telepathy/`, `TelepathyArt`, `MindVoice`). Read the voice-chat part before touching any audio that other players hear. What the player sees: [telepathy.md](../../telepathy.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Telepathy rework

Telepathy's tree (`telepathy/TelepathySkills`, art in `client/effects/setpiece/TelepathyArt`) was reworked in November
2026: eighteen interaction nodes, three of which carry real Simple Voice Chat voices, and 14 poses. What the player sees
is in [docs/telepathy.md](../../../docs/telepathy.md).

- **Carrying voice chat audio.** `telepathy/MindVoice` is the only class that imports the voice chat API; Simple Voice
  Chat finds it by its `@ForgeVoicechatPlugin` annotation (a public no-argument constructor is enough). For each
  `MicrophonePacketEvent` it looks up routes and re-sends the packet with `packet.staticSoundPacketBuilder()` (inside the
  head) or `locationalSoundPacketBuilder().position(...).distance(...)` (from a point). Like `RadioLink`, every call is
  in try/catch and sets a `broken` flag.
  - **Give relayed audio its own channel** (`channelId`, a UUID made from the speaker's), or it shares a decoder with the
    speaker's ordinary voice and garbles. Give it a volume category (`registerVolumeCategory` on server start) so
    players can turn it down.
  - **Never relay to someone who already hears the speaker**: same group, or within `getVoiceChatDistance()` in the same
    level. Respect closed groups (`Group.Type` not `OPEN`) unless the server opts in.
  - **Direction without position:** a locational packet placed a few blocks from the listener toward the speaker, nearer
    for louder, sounds like a distant voice from the right direction.
  - **Changing the sound.** Decode with `voice.createDecoder()`, change the 960 samples, encode with
    `createEncoder(OpusEncoderMode.VOIP)` and pass the bytes to the builder's `opusEncodedData`. Keep one decoder,
    effect and encoder per speaker (the codecs and the effect's echo carry state from frame to frame), process once per
    packet for every listener, and close them on `PlayerDisconnectedEvent`. The effect itself is a plain class
    (`telepathy/PsychicVoice`) so a game test can feed it a tone.
  - **Speaking so only one mind hears.** `event.cancel()` on a `MicrophonePacketEvent` stops the voice chat's own
    proximity and group broadcast; relay the packet yourself first. That is the inner voice (Shift + Mind Link).
- **Work routes out on the server thread, read them on the voice thread.** `MindVoiceRoutes.compute(...)` builds an
  immutable map each tick and `publish` swaps it into a `volatile` field; the voice thread only reads it and records who
  was heard in concurrent maps (`drain`, `speaking`). The rules have no voice chat imports, so game tests check them
  directly (`TelepathyReworkGameTests.farHearing...`).
- **Time also gates voice deliveries.** `time/TemporalVoiceState` publishes an immutable coverage/perception snapshot
  after the server's time services. `MindVoice` cancels frozen microphones before Simple Radio (priority 1000) and gates
  all three concrete outgoing packet events, including groups, telepathy, breaches and radio output. Its voice worker
  stretches only the last already-delivered fragment through `time/TemporalVoice` into three seconds of reverb; no new
  speech feeds the tail. Codecs, rolling buffers and tails are bounded and closed on disconnect/server stop. Category
  names, like Minecraft profile names, must fit 16 characters or a joining client disconnects on packet encoding.
- **Changing a voice for everyone: rewrite the microphone frame.** A player frozen solid speaks from inside the ice
  (`cryo/IcedVoice`, applied by `MindVoice.IceAudio`). `MicrophonePacket.setOpusEncodedData` replaces the bytes of the
  very `MicPacket` Simple Voice Chat then sends to proximity and groups, and Simple Radio reads `getOpusEncodedData` at
  priority 0, so one handler at priority 900 (after the time cancel at 1000) reaches the room, groups, radios, Far
  Hearing and breaches at once; never re-send the voice yourself to change it. Keep the clear bytes for the rest of the
  packet (`ICE.clear(speaker, data)`, matched by array identity) when a later handler should not hear the change
  (in-head telepathy: ice can't muffle a thought). Ramp the effect in and out rather than switching it, keep one
  decoder, effect and encoder per speaker, reset the codecs after a 300 ms pause, and hand the stream back to the
  speaker's own encoding only at a pause (`process` returns false), or listeners hear a click. The server publishes
  who is frozen once a tick (`IcedVoice.publish`, from `CryoService.tick`); the voice thread reads only that set.
  Tests: `CryoReworkGameTests.iceMufflesAVoice...` feeds tones to the plain effect; to hear it, render a WAV through
  `IcedVoice` from a scratch `main` on `build/classes/java/main` (it has no Minecraft imports).
- **Sharing fast time is one invitation.** Activate the field, then right-click a nearby player (`time/TemporalSharing`);
  Superspeed needs tier 4 (Power Level 10). It grants movement in that owner's field, a veil, temporal sight and voice
  access together; ending the field releases them, while another owner's invitation remains independent. The guest's
  gold lightning waist-ring is `SPEED_SHARED_TIME`, re-sent every eight ticks only to temporal perceivers. Photographs:
  `tools/temporal-preview.init.gradle`; focused rules/audio/event tests: `tools/temporal-gametest.init.gradle`.
- **Ask before you carry someone's voice.** Hive Link and Hive Mind take teammates or players who crouch to volunteer,
  never every nearby player (with PvP off, `StyleOps.isFriend` is true for everyone). A listened-to speaker gets a hint.
- **A talk-down everyone can see.** Persuasion's windup (`TelepathyGeometry.PERSUADE_TICKS`) is a public `TELE_PERSUADE`
  piece whose scale is its length, so the art closes exactly as it lands; breaking line of sight ends it. The ending
  piece (`TELE_WON` or `TELE_FRIGHT`) is bound to the same mind, so `superseded` stops the windup drawing at once.
- **A memory you can walk.** `telepathy/MindMemory` remembers every player, and other creatures near anyone who has
  learned Memory Walk, every half second for a minute (at most 256). A walked path goes to the walker as at most twenty
  private `TELE_MEMORY` segments, each delayed two ticks after the last so the trail unspools backward in time; the age
  and a "hurt here" flag ride in the scale. Memory Walk on the ground finds who passed through a place.
- **Private pieces for one viewer.** `TelepathyGeometry.showTo(viewer, ...)` sends a piece to one player: a heard voice's
  mark, a linked friend's halo, a voice inside your head. Use it for anything only one mind perceives.
- **Hooks into the core, not copies of it.** The tree reads the core where it already decides things:
  `SuggestionService.choose` asks `TelepathySkills.suggestionTicks`, `PossessionService.end` calls `released` (only when
  control had begun), `IllusionService` calls `fright` on contact, and `TelepathyFocus` gained `deepen` (add Focus to a
  mind without reading it) and `threads(p, min)`. The old Implanted Command and Shared Sight duplicated core actions;
  their slots now hold new moves.
- **Links outlast deaths and dimension changes.** `TelepathyService.stopAll` runs on death and dimension change, so a
  link that reaches anywhere is only left on logout (`TelepathySkills.leave`).
- **Voice tests stay close.** `TelepathyReworkGameTests` shrinks the voice chat range to five blocks (`nearVoices`) so
  "far apart" is twenty blocks; never send test players hundreds of blocks off.
- **A look-don't-touch inventory.** Mind's Eye (`telepathy/MindsEyeMenu`) is a `ChestMenu` over a `SimpleContainer` of
  *copies*, re-mirrored in `broadcastChanges`, with `clicked`, `quickMoveStack` and `canDragTo` all refusing. Use it for
  any read-only view of another inventory. A `FakePlayer` ignores `openMenu`, so tests build the menu directly.
- **Remembered chat is public chat only.** `TelepathySkills.said` records `ServerChatEvent` lines (twenty, thirty minutes,
  forgotten on logout); private messages and commands never reach it.
- **Photographs:** `tools/telepathy-kit-preview.init.gradle` (`uitest/TelepathyKitPreview`) shoots every pose and piece into
  `build/telepathy-kit-preview/screenshots/`; villagers stand in for the second mind.
