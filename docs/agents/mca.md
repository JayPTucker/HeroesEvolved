# Minecraft Comes Alive: notes for agents

How Heroes Evolved works with MCA Reborn (NeoForge 1.21.1, pinned at 7.7.36 by Modrinth version ID `S2Ln2tIn`). What the
player sees: [minecraft-comes-alive.md](../minecraft-comes-alive.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## The rules

- **Never spawn a villager with `EntityType.VILLAGER.create` and `addFreshEntity`.** With MCA that call answers false, the
  object you hold is thrown away, and a stranger with a new UUID appears a tick later.
  - A scripted body (a story scene, anything held by reference and posed): `Villagers.actor(level)`, then add it as usual.
    It is always vanilla.
  - An ordinary person: build a vanilla villager, then `Villager in = Villagers.enter(level, v)`, and **use `in`**, which
    may be a different object with the same UUID. Set `NoAI` after `enter`: a no-AI villager counts as an actor.
- **`instanceof Villager` is already the right question.** MCA's humans (`mca:male_villager`, `mca:female_villager`)
  extend `Villager`, so finders, filters, tags and Jade's `Villager.class` provider all see them. Don't add a second
  "is this a villager" helper, and never compare `getType()` with `EntityType.VILLAGER`.
- **Read brain memories with `hasMemoryValue` first.** MCA has its own brain profile. It has every memory we write
  (`WALK_TARGET`, `LOOK_TARGET`, `HEARD_BELL_TIME`, `JOB_SITE` and the rest) but not, for example, `ANGRY_AT`, and
  `getMemory` throws on a module the brain doesn't have.
- **Only `compat/mca/McaLink` imports MCA.** It is built only when MCA is loaded (`Villagers.bridge`), and everything
  else goes through the `Villagers.Bridge` interface. Each call is guarded, and a failure logs once and switches the link off.

## How MCA really works (read from its source, checked against the jar)

- **The swap.** A mixin on `ServerLevel.addEntity` (and `ProtoChunk.addEntity` for worldgen) hands any plain `Villager` to
  `SpawnQueue` and answers false. The queue later discards it and spawns a human with a **new UUID**, copying only name,
  age, type, job, trades, tags and a few flags. Persistent data and attachments are lost.
- **Spawn reasons decide it.** A villager with no reason counts as `natural`, which is allowed by default along with
  `structure`. `Villager.finalizeSpawn` records the reason, so `McaLink.holdBack` finalizes with a quiet reason MCA isn't
  configured for. This works even when the link is switched off, because losing a story's cast is the worst failure.
- **`/mca-admin convertVanillaVillagers`** queues villagers already in the world. `McaLink.tick` runs before the levels,
  takes those back out of the queue by reflection, and either swaps them with everything kept or, if `Villagers.staysAsIs`
  says a scene or event holds them by reference, leaves them vanilla.
- **Right-clicks.** MCA answers `interactAt`, which the game tries before the plain use, so `EntityInteract` never fired
  on its humans: favours, markets, mimicry, touch powers. `McaLink.usedAt` posts a plain `EntityInteract` first and lets
  MCA's menu open only if nothing took it. Crouched, it passes the use on, so carrying gets it and MCA's `mobInteract`
  still trades. It doesn't post when MCA wouldn't answer, or the game's own event would make two.

## Reusable patterns

- **Swap a body through NBT** (`McaLink.swap`): save the old one, load it into the new one, then `finalizeSpawn` for what
  MCA generates (face, clothes, personality, parents, a name if there was none). Two things had to be handled: saved
  attributes carry vanilla's base values (keep the new body's bases, and the modifiers, which is how a gift's health
  survives), and vanilla's `random_spawn_bonus` modifier must be stripped, or `finalizeSpawn` throws on adding it twice.
- **Follow the other mod's settings.** `McaLink.become` asks MCA's `overwriteOriginalVillagers`, dimension blacklist and
  `shouldGetConverted`, so a pack that keeps some villagers vanilla keeps ours vanilla too.
- **A mod that changes the world is opt-in for dev runs.** Unlike JEI and Jade, MCA is only in `localRuntime` with `-Pmca`,
  because it replaces villagers in every test fixture that spawns one directly.

## Testing

- `./gradlew runGameTestServer --init-script tools/structure-gametest.init.gradle "-PtestPackages=compat,travel/TravellerBodiesGameTests.java" -Pmca`
  runs the compat tests with the real MCA. Without `-Pmca` the same tests check plain vanilla spawning.
- MCA's classes are reached in tests only through `McaTestCalls`, so the suite still loads without MCA.
- **The whole suite under `-Pmca` is not a meaningful check.** Most fixtures spawn with `EntityType.VILLAGER.create` and
  `addFreshEntity`, which MCA swallows. Test a system on an MCA body by bringing them in with `Villagers.enter`.
- Photographs: `./gradlew runClient --init-script tools/mca-preview.init.gradle -Pmca` (`uitest/McaPreview`) stands a
  story villager beside MCA humans and checks on the client what each one really is.
