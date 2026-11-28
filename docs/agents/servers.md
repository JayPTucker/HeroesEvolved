# Servers, hosted worlds and other mods: notes for agents

How the mod stays playable on a dedicated server, in a world hosted for friends (LAN, Essential's Host World), and next
to big client mods such as Essential, and the checks that prove it. Written from the November 2026 compatibility pass.

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Three ways the game runs

| Setup | Who runs the server | What can break that single-player hides |
| --- | --- | --- |
| Single-player | the player's own game (an integrated server in the same JVM) | nothing: packets are handed over as objects, both halves share static state |
| Hosted world (Open to LAN, Essential's Host World, e4mc) | the host's game; friends join over the network | everything below for the guests, and server code reading the host's client state |
| Dedicated server | a server install with no client classes at all | everything below, plus any server path that loads a client class crashes the server |

The mod is required on both sides (the payload registrar's version string in `ModPayloads` must match),
as are TaCZ, Simple Voice Chat and Simple Radio. Essential, JEI and Jade are optional.

## Dedicated servers: never load a client class

- **A development server still has the client classes; a real one doesn't.** NeoForge 21.1's dev runs (including the
  game-test server) use the joined jar and do not hide `net.minecraft.client`, so code that touches `Minecraft`, a
  renderer or a screen on a server path passes every test and then crashes a real server with
  `NoClassDefFoundError`. **`tools/client-class-tracer.gradle` closes that gap:** it attaches `tools/ClientClassTracer.java`
  as a Java agent, logs the first load of every client class with the stack that loaded it, and fails the task if a
  Minecraft client class, or a NeoForge client class reached from our code, was loaded. `rewind-gametest.init.gradle`
  applies it, so every full suite (and GitHub) checks this; so does the multiplayer check's server. The log is
  `client-class-loads.txt` in the run's game directory.
- **Allowed on servers:** NeoForge loads `net.minecraft.client.server.LanServerPinger` itself (its dedicated server
  advertises to the LAN and ships that class), plus a few of its own `net.neoforged.neoforge.client` classes. The
  tracer's `shippedOnServers` list names the first; add to it only for loads with no `com.jayptucker` frame.
- **Where client code lives:** `client/`, `gui/`, `render/`, `input/`, `particles/`, `entity/client/`, the mixin
  config's `client` list, `@EventBusSubscriber(value = Dist.CLIENT)` classes, and the body of a `playToClient` handler
  that calls a static method on one of those (`BurnPayload.handle` → `client.BurnClient.set`). The first full-suite run
  with the tracer (1,343 tests) loaded no client class, and a static sweep found no subscriber touching client code
  without `Dist.CLIENT`.
- **The verifier trap.** A common class is checked when it links, method by method. `Player p = Minecraft.getInstance().player`
  in a common class makes the verifier load `LocalPlayer` to prove it is a `Player`, so the class fails to link on a
  server even if that line never runs. Keep every client type inside client classes; common code calls them through a
  static method with plain arguments.
- **Configs.** `ClientVisualSettings` is a CLIENT config: never read it on a server path (it is never loaded there).
  `HeroesEvolvedConfig` is COMMON and is not synced, so the client must never read gameplay numbers from it; client
  code reads only constants (`FlightService.TEMPEST_*`) and synced state. The one client read today,
  `flightTrailIntervalTicks` in `FlightVaporClient`, is cosmetic. Tables both sides need (`power_balance.json`,
  `skill_trees.json`, blueprints, the End rituals) are read from the jar with `getResourceAsStream`, not from a data
  pack, so a remote client has them too.

## The floating kick: powers that hold a player up

- **Vanilla kicks a player who floats about four seconds** on a server with `allow-flight=false` (the default): "Flying
  is not enabled on this server". Only creative flight, Levitation, an elytra, a riptide, riding, or zero gravity
  exempt them. Single-player never checks, so this was invisible until the multiplayer check: the server kicked its
  player while Induced Radioactivity's moves held them aloft.
- **`ability/Airborne.excused(player)` is the one question,** asked by `mixin/ServerFloatingMixin` on the two floating
  reads in `ServerGamePacketListenerImpl.tick` (a false answer takes vanilla's own grounded branch, which resets the
  count). A player is excused while the server is moving them (`hurtMarked`), a stamina drain is running, time around
  them is stopped or slowed, and for `GRACE_TICKS` (five seconds) after any of those or after a move
  (`AbilityUseService.activate`/`release`, `ComboStyle.pay` and `PlayerEnergyService.pause` stamp it). A new way to
  hold a player in the air adds a branch to `held`, or calls `Airborne.moved` while it holds them.
- **Powers that are flight** (Flight, Flame Flight, Telekinesis, a winged morph) grant `NeoForgeMod.CREATIVE_FLIGHT` or
  `mayfly` instead, which vanilla already accepts. Keep doing that for anything the player steers through the air.
- Admins never need to turn on `allow-flight` for the mod; a client flying on its own, with no power behind it, is
  still kicked. `AirborneGameTests` checks both.

## Packets are only encoded off single-player

- **Single-player and game tests never encode a packet.** The integrated server's connection is in memory and passes
  payload objects straight across, and test players use fake connections. A codec that drops a field, a list over its
  `ByteBufCodecs.list(n)` cap, a registry-bound codec read before registries sync, or a payload over the size limits
  (about 1 MiB to a client, 32 KiB to the server) only fails over a real network: on a server, or for a guest in a
  hosted world. The client is then disconnected with a decoder error.
- **The multiplayer check is the test for that** (below). Run it after adding or changing payloads, and bump the
  registrar's version string when the set of payloads changes.
- **A serverbound handler must check its sender.** Anyone on a server can send any payload with any values.
  `AdminService.handle` refuses non-admins; follow that pattern for anything that changes another player or the world.

## Hosted worlds (LAN, Essential)

- A hosted world is an integrated server that guests reach over the network, so guests see everything a dedicated
  server's players see. The host's client and the server share one JVM, which hides two mistakes that only hurt
  guests: **server logic reading client state** (a `Client*` class, `Minecraft.getInstance()`) applies the host's view
  to everyone, and **client code reading the integrated server** (`getSingleplayerServer()`, `ServerLifecycleHooks`)
  shows guests nothing. Neither exists today (a sweep found client state read only by `Dist.CLIENT` handlers and
  payload handlers, and `ServerLifecycleHooks` only in a prologue debug helper). Keep it that way.
- Essential's Host World publishes the integrated server like Open to LAN; nothing in the mod asks whether a server is
  single-player, published or dedicated, which is right: decide by players, not by server type.

## Essential

Essential (essential.gg) is a client mod many players run: cosmetics and emotes on the player, a rebuilt title and
pause menu, screenshots, friends and world hosting. It has a NeoForge 1.21.1 build, and its source is public
(`SparkUniverse/Essential-Mod` on GitHub; read it with `gh api`, its mixins are in `src/main/java/gg/essential/mixins`).

- **Its menus.** On the title and pause screens Essential adds proxy buttons and lays its sidebar out around the vanilla
  buttons it finds by label (`menu.singleplayer`, `menu.multiplayer`), reading their positions live
  (`VanillaButtonConstraint`). `TitleMenu.arrange` moves those same buttons by translation key, and Essential's layout
  follows them. `TitleButtonMixin` restyles only the vanilla keys it knows, so Essential's own proxy buttons keep their
  look.
- **Its player rendering.** Essential mixes into `PlayerRenderer`, the armor and held-item layers and the skin, as we
  do (`BurnSkinMixin`, `SeveredArmorMixin`, `SeveredHandItemMixin`, `PlayerModelMixin`). Ours are `@Inject`s at HEAD,
  TAIL or RETURN, which every other mod's injections leave in place.
- **Checking it:** `tools/client-mods-check.init.gradle` with `-PclientMods=<path to the Essential jar>
  -PcheckMods=essential` photographs the title screen (with and without whatever notice Essential opens), a power in
  use from the front and in first person, the pause menu and the Powers screen. The jar Modrinth serves
  (`Essential_<version>_neoforge_1-21-1.jar`) is Essential's loader with the mod inside; it goes in the mods folder as
  is. Nothing downloads it for you.
- **What the November 2026 run showed** (Essential 1.5.2, offline dev account): it loads with our mixins and logs no
  warning against `heroesevolved.mixins.json`; our title, eclipse sky and restyled buttons stay centred with Essential's
  character preview on the left and its sidebar on the right; its pause menu, our HUD, Pyrokinesis's fire and the
  Powers screen all draw. An offline account makes Essential open an "Account authentication failed" notice over the
  title screen (Escape closes it). Not covered without a signed-in account: cosmetics, emotes and Host World itself
  (its server side is the hosted-world case above).

## Other mods' mixins

- **Never `@Redirect`.** Two mods redirecting the same call is a hard failure, and with `"defaultRequire": 1` our
  injector failing to find its target crashes the game at launch. Use MixinExtras (bundled with NeoForge), whose
  injectors chain with each other and with other mods' redirects:
  - skip a void call: `@WrapWithCondition` returning false (`TitleScreenMixin` hides the logo and splash);
  - change a call: `@WrapOperation` and call `original` (`TitleIconButtonMixin`, `TemporalParticleEngineMixin`,
    `EndCrystalBattleMixin`);
  - change a value read: `@ModifyExpressionValue` (`TemporalWeatherMixin`'s tick counter); an argument: `@ModifyArg`
    (`EndFightBattleMixin`).
  The November 2026 pass replaced all eight `@Redirect`s this way.
- **`@Overwrite` is the same problem, worse.** We have none; keep it so.
- HEAD, TAIL and RETURN `@Inject`s are safe; an `INVOKE`/`FIELD` target is where another mod can get there first.

## The checks

| Command | What it proves |
| --- | --- |
| `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle` | every test, plus no client class loaded on the server (the tracer) |
| `./gradlew runServer --init-script tools/multiplayer-check.init.gradle`, then (once it logs `Done`) `./gradlew runClient --init-script tools/multiplayer-check.init.gradle` | a real dedicated server and a real client: the server (`uitest/MultiplayerCheckServer`) gives the joined player every power in turn and fires each base action at three still zombies, then runs an Eclipse; every packet crosses the network. Fails on any disconnect, a step that throws, or a client class on the server. Report: `build/multiplayer-check/server/multiplayer-check-server.txt`; photographs: `build/multiplayer-check/client/screenshots` |
| `./gradlew runClient --init-script tools/client-mods-check.init.gradle -PclientMods=... -PcheckMods=...` | the mod next to client mods you supply (Essential) |

- **A fresh game folder opens the accessibility onboarding screen, not the title screen.** A driver that waits for
  `TitleScreen` waits forever; `MultiplayerCheckClient` connects from whatever screen is up, and `ClientModsCheck`
  moves on to the title screen.
- **Two Gradle builds at once are fine** here: the server run and the client run each configure the same source set,
  and the second finds it compiled.
