# JEI and Jade: notes for agents

How Heroes Evolved works with the two optional "what is this" mods the modpack ships: JEI (recipes and items) and
Jade (the overlay naming what you look at). What the player sees: [jei-and-jade.md](../jei-and-jade.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## When you add anything JEI or Jade can see

Every new item, block, block entity, entity, mob, machine, recipe or disguise goes through this list in the same
change. Players see JEI and Jade before they read any doc, so a gap here shows on day one.

**Items and blocks**

- [ ] A name in `en_us.json` (`item.heroesevolved.*`, `block.heroesevolved.*`), even for a block with no item: JEI
  and Jade both show it. `entity.heroesevolved.*` for every entity type, internal ones included.
- [ ] In the creative tab (`ModItems.TAB`, or the area's `tab(...)` like `SuitItems.tab`), unless it can't be had.
- [ ] Can't be had (a tab icon, an admin-only or story-only item)? Add it to
  `data/c/tags/item/hidden_from_recipe_viewers.json`. A block with no item needs nothing: no viewer can list it.
- [ ] Found rather than crafted (an ore, a boss drop, something a power or a scene gives)? Add an info page in
  `HeroesJeiPlugin.registerRecipes` (`info(...)`) and its `jei.heroesevolved.info.*` line.
- [ ] Several stacks of one item that mean different things (a component that changes what it is, not just a charge)?
  Register a subtype interpreter, or JEI shows only the first.

**Recipes and machines**

- [ ] Crafting, smelting, blasting, stonecutting and TaCZ workbench recipes in `data/heroesevolved/recipe/` show by
  themselves; nothing to add.
- [ ] A machine or bench with its own rules gets a category in `JeiCategories` built **from the machine's own rule**
  (its patterns list or its `result(...)` method; never retype the recipe), a catalyst, a click area on its screen's
  arrow, and a transfer handler if its input slots take items in order.
- [ ] A screen that draws outside its `imageWidth` × `imageHeight` (a side panel, a tab strip) needs
  `IGuiContainerHandler.getGuiExtraAreas`, or JEI's list covers it.
- [ ] A machine with stored energy, items or progress: energy and inventories show in Jade by themselves through
  NeoForge capabilities; progress needs a `registerProgress` entry (`JadeProviders.LabProgress`); anything else worth
  knowing at a glance (on/off, radius, mode) is a provider like `JadeProviders.Dampener`.

**Entities and mobs**

- [ ] Purely visual or internal (a marker, an anchor, a thrown effect)? Make it not pickable and add a `hideTarget` in
  `JadeClient.register`. Never hide a vanilla type we borrow (`BlockDebris` is the vanilla block display).
- [ ] Named characters (bosses, story actors) need a real name (`getName` or a custom name), never the type key.
- [ ] Anything that changes how an entity **looks** on a client (a disguise, a stand-in body, hiding it, drawing it
  somewhere else) must be answered by `MorphClient.seenAs`, `UnseenClient.concealed` or `TimeVeilClient.standOn`, the
  questions Jade asks. Add a branch to one of them; never a second copy in the Jade code. Otherwise Jade names the
  real entity and gives the power away.
- [ ] A mob with a state players should read at a glance (a power, a phase, a countdown) can get a line through an
  `IEntityComponentProvider` in `JadeClient`, read from what the client already has, with its
  `config.jade.plugin_heroesevolved.*` name.

**Keys**

- [ ] A new key binding uses `ModKeyMappings.PLAY`, so it never fires in a menu where JEI's keys work.

**Check it**

- [ ] Add a scene to `uitest/CompatPreview` for anything Jade should name differently (a check through
  `CompatPreviewJade.target()`), run `tools/compat-preview.init.gradle`, and look at the shots.

## How they are wired

- **Optional, compile-only.** `build.gradle` compiles against JEI's API (`mezz.jei:jei-1.21.1-{common,neoforge}-api`
  from `maven.blamejared.com`) and Jade's jar (Modrinth maven, pinned by version ID like the other mods), and puts both
  in `localRuntime`, so every dev run, preview and game-test server has them. Versions are in `gradle.properties`
  (`jei_version`, `jade_version`). `-PnoJei` and `-PnoJade` leave one out, to check the mod without it. Neither is in
  `neoforge.mods.toml`: the mod needs neither.
- **JEI's mod jar needs `net.mezzdev.config`** from the same maven; the repository filter includes that group, or the
  runtime classpath fails to resolve.
- **One package per mod.** Only `compat/jei` imports JEI and only `compat/jade` imports Jade, the same rule as
  `TaczLink` and `RadioLink`. Each is entered through its plugin annotation (`@JeiPlugin`, `@WailaPlugin`), which the
  other mod finds by scanning; nothing else in the mod names those classes, so without JEI or Jade they are never
  loaded. Every registration step runs in `guard(...)`, which catches `Throwable` (an API change shows up as a
  `LinkageError`), logs once and skips that step; Jade's runtime callbacks catch and switch themselves off the same way.
- **Jade's common half runs on a dedicated server.** `HeroesJadePlugin.register` (server data and progress providers)
  must not touch client classes; everything that does lives in `JadeClient`, called only from `registerClient`.

## JEI

- **Vanilla-type recipes show by themselves**, and TaCZ ships its own JEI plugin (`com.tacz.guns.compat.jei`) that
  shows every `tacz:gun_smith_table_crafting` recipe, ours included, in one category per TaCZ table (uids like
  `tacz:gun_smith_table/tacz_ammo_workbench`: the EMP grenade lands at the Ammo Assembly Table). Nothing to add for
  either; find TaCZ's category through a recipe's output, never by a guessed uid.
- **Our three machines** are categories in `JeiCategories`: the Suit Fabricator (from `SuitPatterns.all()`, a tag
  ingredient cycling through every item that counts), the Centrifuge (from `CentrifugeBlockEntity`'s rule and
  `SPIN_TICKS`) and the Synthesis Station (each brew's result from `SynthesisStationBlockEntity.result`). **Read the
  machine's own rule; never retype a recipe**, so JEI can't show something the machine wouldn't make.
- **Catalysts, click areas and the + button.** Each machine's arrow opens its category; the fabricator and centrifuge
  take ingredients through JEI's basic transfer handler. Mind the slot order: the fabricator's menu is inputs 0-5,
  result 6, player 7-42; the lab menus add their machine slots before the player's.
- **Hide with the tag, not code.** `data/c/tags/item/hidden_from_recipe_viewers.json` (the convention tag that JEI,
  EMI and REI read) hides the Eclipse Emblem. Blocks with no item (frost constructs, assembled matter) never reach a
  recipe viewer, so need nothing.
- **Info pages** (`jei.heroesevolved.info.*`) cover what is found rather than crafted: ores, blood, the boss drops,
  the EMP grenade's table, and how the fabricator is used.
- **Our screens draw inside their own bounds**, so JEI's list never covers them and they need no exclusion areas. A
  screen that draws outside `imageWidth` (a side panel) must add a `IGuiContainerHandler.getGuiExtraAreas`.

## Jade

- **Name what the eye sees.** A ray-trace callback (`JadeClient.seen`) swaps Jade's target before anything is drawn:
  a body this client can't see (`UnseenClient.concealed`: Fade, Vanishing Touch, the time veil) is looked through to
  the block behind it; a veiled player's still stand-in (`TimeVeilClient.standOn`) is named as them; a shapeshifter, a
  creature wearing a Borrowed Face and a Decoy are named as the body drawn for them (`MorphClient.seenAs`), with
  `serverConnected(false)` so no server data about the real body is asked for. Any other mod that names what you look at
  should ask the same two questions, not copy them.
- **Hidden types.** Carry anchors, ghost blocks and quake debris are `hideTarget` (they aren't pickable either).
  `BlockDebris` is the vanilla block display, which is never pickable, so Jade looks straight through flying rocks; a
  type that is shared with other mods must not be hidden by type.
- **Extras.** A gifted mob's power and rarity (`ClientGifted` state, the power name from `MimicColors.power`), a
  meltdown warning (the `VILLAGE_MELTDOWN` piece bound to the villager), the dampener's field (block state plus a
  server data provider for radius and upkeep; Jade draws the FE bar itself from the energy capability), the lab
  machines' progress (`registerProgress` with the same UID on both sides), and ice as the frost construct's icon.
- **Config names** for every provider UID are `config.jade.plugin_heroesevolved.<path>` in `en_us.json`.

## Keys

- **Our keys are in-game only** (`ModKeyMappings.PLAY`, an `IKeyConflictContext` active with no screen open or in
  our own hold-to-open wheels). R (the wheel) shares its key with JEI's R (show recipe) and O (admin) with JEI's
  Ctrl+O; NeoForge's `KeyMappingLookup` only hands a press to mappings whose context is active, so ours never fire in
  a menu and JEI's never in the world. (`ClientKeyEvents` also skips our keys while a screen is open; the context
  makes it hold for every path.) **NeoForge's Controls screen marks any two bindings on one key red whatever their
  contexts** (`KeyMapping.same` falls back to comparing keys), so a red R there is a warning, not a clash: TaCZ's
  reload is on R too. NeoForge's `isDown()` answers false outside the context, which is why the wheels count as inside
  it (`AbilityWheelScreen.keyDown` reads a scan-code key through `isDown`). A new key uses `PLAY`.

## Checking it

- `tools/compat-preview.init.gradle` (`uitest/CompatPreview`, flag `heroes.preview.compat`) photographs Jade on the
  dampener, a spinning centrifuge, a gifted pillager, a pig this client is told wears a cow's face, a hidden
  sheep, a meltdown and a flying rock, and checks what Jade really targets through Jade's `ObjectDataCenter.get()`;
  then JEI's list, our three categories, an info page, the EMP grenade at TaCZ's table, the fabricator beside
  the list, and that no key of ours is live where a JEI key on the same key is. The JEI and Jade calls live in
  `CompatPreviewJei` and `CompatPreviewJade`, touched only when the mod is loaded, so `-PnoJei -PnoJade` runs the same
  scenes without them.
- **Stage in the overworld.** The first run staged in the Last Day's dimension (a new player starts there) and Jade
  targeted nothing in every scene; the driver now skips the prologue and releases the player first, as
  `HydroPreview` does.
- **Keep a meltdown far from other subjects.** Even on a 20-minute fuse a melting villager's heat kills whatever
  stands a few blocks away and burns the walls; a hidden villager and then a hidden sheep beside it vanished from the
  client, which made Jade's look-through pass for the wrong reason. The preview now checks the subject is on the
  client before trusting the shot, and stands the meltdown 40 blocks off.
- The game-test server loads both mods too, so the full suite covers a server with them; run a focused suite with
  `-PnoJei -PnoJade` for one without.
- Not photographed: a time-veiled player's stand-in and a shapeshifted player (each needs a second player; the code
  path is the same `seenAs` and `standOn` the preview's creature cases use).
