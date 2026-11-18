# Meltdown hazards and protective suits: notes for agents

The meltdown field, friction burns, radiation deaths, the suits and the HUD gauges (`radiation/MeltdownField`, `speed/SpeedFriction`, `hazard/`, `HazardArt`, `gui/HudGauges`). Read the armor skill ([SKILL.md](../../.claude/skills/minecraft-armor/SKILL.md)) before changing anything worn. What the player sees: [protective-suits.md](../protective-suits.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from meltdown hazards and the protective suits

Meltdowns became dangerous to stand near and a speedster's top speeds dangerous to run at, with two suits to keep it off
(November 2026: `radiation/MeltdownField`, `radiation/RadiationDeaths`, `speed/SpeedFriction`, `hazard/`, art in
`client/effects/setpiece/HazardArt`, the peeling skin in `client/BurnClient`). What the player sees is in
[docs/protective-suits.md](../../docs/protective-suits.md).

- **One field for anything melting down.** `MeltdownField.tick(core, progress, held)` is called every tick by whatever is
  melting down (RadiationService's detonation loop, `VillagerMeltdown.tick`): shock waves, the lethal near-field and the
  decaying ground all scale from `progress`. A core that stops being ticked ends itself after five ticks. A new thing
  that melts down (a boss, a reactor block) is one call, not a copy.
- **A hazard is nobody's blow.** Meltdown heat and friction are their own damage types (`hazard/HazardDamage`) with no
  attacker when a player is the source, so PvP rules don't stop a co-op meltdown from killing friends (vanilla only
  gates damage whose causing entity is a player). A villager's meltdown names him, for the death message.
- **Suits are a question, not armor points.** `Suits.radiation(entity)` (a quarter a piece of either suit),
  `sealed`, `friction`, `frictionProof` and `meltdownProof` are the one place that decides protection; the damage code
  multiplies by `1 - shield` and calls `Suits.wear`. Ordinary armor is bypassed by the damage types' tags. A new hazard
  suit (a fire suit, a vacuum suit) is another `Suits.Kind`.
- **Survive, don't dodge.** Regeneration's "can't die of it" is a `LivingDamageEvent.Pre` clamp to health − 1 on the
  meltdown type (`MeltdownField.regrow`), so the hit still lands, burns and doses.
- **Radiation sickness reads suits too.** `RadiationSickness.immune` is true for a sealed wearer, so every dose anywhere
  respects the suits with no per-call change.
- **Telling deaths apart after the fact.** `RadiationDeaths.mark(entity)` records "radiation just hurt this"; the death
  hook counts a death as radiation if it was marked in the last half-second, sick, or hit by a radiation damage type.
  Mark wherever radiation hurts (the blast's wave and fallout, the sickness, the field) and every such death burns away.
- **Hiding a corpse while it breaks apart.** `RAD_DISINTEGRATE` is bound to the dying entity; `HazardArt.disintegrating`
  keeps it hidden while the piece plays *and for as long as it stays dead* (a dead player lies there until they respawn),
  and `BurnClient.hide` cancels `RenderLivingEvent.Pre` for it. The piece carries the body's height in its scale and its
  width in the target's distance, so any creature breaks into cubes of its own size (humanoids use body parts).
- **Repainting a player's own skin.** `BurnClient` reads a skin back from the GPU once (`NativeImage.downloadTexture`
  after binding it, sized with `GL_TEXTURE_WIDTH`), repaints a `DynamicTexture` copy in 24 steps of blotchy value noise
  (burnt pixels clear, a glowing then charred edge), and `mixin/BurnSkinMixin` returns it from
  `PlayerRenderer.getTextureLocation`. The player's translucent skin shader discards the cleared pixels. Use this for any
  effect that changes the skin itself (frost creeping, stone spreading).
- **Something under the skin.** `BurnClient.EmberLayer` draws the player's own model parts again, thinner
  (`ModelPart.xScale`/`zScale` 0.9, `yScale` 0.97, restored after), with its own texture and an `eyes` glow pass. Where
  the skin is whole it hides the layer by depth; where it is clear the layer shows.
- **A dyeable armor with an undyed overlay.** `ArmorMaterial.Layer(id, "", true)` plus `Layer(id, "_overlay", false)`,
  a default `DYED_COLOR` component on the items, the `minecraft:dyeable` item tag, and an item color handler on layer 0.
  The velocity suit's red is the dye; its white trim is the overlay, so a recoloured suit keeps its white, and the V cut
  through the chest plate's overlay shows the dye.
- **3D armor: read `.claude/skills/minecraft-armor/SKILL.md`** before making anything worn. Details (fins, plates,
  belts, bands, tanks, soles) are extra boxes added as children of the body parts in the suit's own model, textured from
  the armor texture's freed hat area.
- **Armor worn skin-tight.** Vanilla armor inflates its boxes by a whole pixel (half for leggings), far too bulky for a
  suit. `client/SuitModels` registers two layer definitions built from `HumanoidArmorModel.createBodyLayer` at a small
  `CubeDeformation` (head replaced with its own), bakes them lazily, and returns them from an `IClientItemExtensions`
  (`RegisterClientExtensionsEvent`); NeoForge copies the poses over. Keep each layer just past the player's own outer
  skin layer (hat 0.5, jacket, sleeves and pants 0.25), or skin pixels poke through, and the outer pieces just past the
  leggings.
- **A bench without a block entity.** The Suit Fabricator is a stonecutter-style menu over a `SimpleContainer`
  (`hazard/SuitFabricatorMenu`): patterns are picked with `clickMenuButton`, materials are matched in any order
  (`SuitPatterns`, ingredients by tag so `c:ingots/lead` from other mods works), and the screen is drawn with fills, so
  there is no GUI texture to maintain.
- **Pin what's left behind.** A runner who burns away keeps the ring's middle from the moment they broke
  (`SpeedContainment.Run.pinned`), so if the target is carried off or blows up, the burning body stays put. Anything
  that plays out over a target that can move should decide whether it follows or stays, and store the point if it stays.
- **A setting you scroll while a move runs.** The Cyclone's speed (`SpeedKit.cycloneSpeed`) is changed by a
  `CycloneSpeedInputPayload` from `client/ClientCyclone`, whose scroll handler runs at HIGH priority and only while the
  server's `CycloneStatusPayload` says a Cyclone is running, so it never fights the run-speed or flight scroll. The
  status payload also feeds the HUD ring. The move reads the setting each tick (vacuum up to 2, wind and the tornado
  from 3), so a change takes effect at once.
- **Compact HUD rings.** `gui/HudGauges` draws a row of small ring gauges (Momentum with run-speed pips, Overrun, heat,
  skin) as quads in `RenderType.gui()` (`arc`: a ring segment clockwise from the top), with the value in the middle at
  half or three-quarter scale and a tiny label or setting pips under it. Every power's readout is one (radiation Core,
  Resonance in a rainbow, Strain, a two-way Density dial and Breath, Regrowth and the downed timer, Flight, Momentum and
  Overrun, heat and skin): each adds `HudGauges.Gauge.ring(...)` (or `dial`) to one list, and `HudGauges.row` draws
  them, wrapping at the screen's edge. Words remain only for an instruction you need right now (giving up when down,
  steering a flight maneuver). Each ring
  only appears while it has something to say. Prefer a ring to another line of text and a bar when adding a readout.
  `uitest/HudGaugePreview` (flag `heroes.preview.gauges`) photographs every state with the readings set on the client. A value the server sends in small steps (the burn healing) looks still on the
  client, so "regrowing" is remembered for a moment after each step down (`BurnClient.regrowing`).
- **Friction from real movement.** `SpeedFriction` measures a runner's own displacement each tick (smoothed, ignoring
  jumps over 8 blocks so Blitz and teleports don't count) instead of reading run settings, so Momentum and any future
  speed source count the same. Fire would be smothered by the speedster's own running (`SpeedService.smother`), so
  friction burns with its damage type and particles rather than setting them alight.
- **Village counts that don't shrink.** A meltdown's village is gathered again every two seconds (near the bell, a
  home/job/meeting memory near it, or in the blast's reach), never shrinks, is saved with him, and the end count treats
  an unloaded villager as alive unless the death hook saw them die. Check `brain.hasMemoryValue` before `getMemory`.
- **A safe point outside a village.** `VillagerMeltdown.safePoint` reads the village's POIs (`PoiTypeTags.VILLAGE`) for
  its extent, then searches rings outward from just past it for open natural ground clear of every building and of the
  blast. Use it for any "meet here" marker that must not land on a roof.
- **Textures:** `py -3 tools/generate-suit-textures.py` draws lead, the bench, the materials, both suits (worn and
  icons) and the ember body.
