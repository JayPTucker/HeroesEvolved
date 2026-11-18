# Shapeshifting: notes for agents

Forms, the body, Instinct, tricks, Suspicion and the tree (`shapeshift/`, `MorphClient`, `ShapeArt`). It also has the checklist for wiring a new power's tree and classes (see "Wiring a new power's tree and classes"). What the player sees: [shapeshifting.md](../../shapeshifting.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from Shapeshifting

Shapeshifting (`shapeshift/`, stand-ins in `client/MorphClient`, art in `client/effects/setpiece/ShapeArt`,
`ShapeInstinctArt` and `ShapeTrickArt`) was built in November 2026, in phases 1 to 6 of
[docs/shapeshifting.md](../../../docs/shapeshifting.md); phase 7 is polish. It is rollable (not in `IN_DEVELOPMENT`).

- **The kit and where it lives.**
  - **Keys:** Morph (study, the form wheel, revert), Instinct (each form's own attack), Decoy, Reflection and Nightmare.
  - **Classes and their jobs:**
    - `ShapeshiftService`: forms, the body, kin and the shape history.
    - `ShapeshiftForms`: level bands, sizes, kin and the body a form gives.
    - `ShapeshiftTraits` and `ShapeshiftTraitService`: gifts and weaknesses.
    - `ShapeshiftHands`: what a form without hands can't do.
    - `ShapeshiftInstinct` and `InstinctMove`: Instinct.
    - `ShapeshiftTricks` and `ShapeDecoy`: Decoy, Reflection, Nightmare, Borrowed Face, the Doppelganger, cowed and
      turned creatures, and the awakening.
    - `ShapeshiftSuspicion`: Suspicion, exposure and the readers who see through a disguise.
    - `ShapeshiftChat`: speaking as someone.
    - `ShapeshiftSkills`: the 18-node tree, Apex Form and the three classes.
  - **Tests:** `ShapeshiftGameTests`, `ShapeshiftTricksGameTests` and `ShapeshiftSkillsGameTests`.
- **One art language.** Every piece is plates that flip from slate to brass. `ShapeArt` holds the primitives (`hull`,
  `scale`, `face`) and the palette (`SHADOW 1F2A3D`, `SLATE 3E6F86`, `SHEEN 8FCFC6`, `BRASS C29A5E`, `HIGHLIGHT F1ECE2`).
  `ShapeInstinctArt` adds `around` (a ring of plates about any axis), `streak`, `seamRing` and `loose` (a tumbling
  plate), shared with `ShapeTrickArt`. New moves reuse pieces where they can: Hybrid is `SHIFT_TOUCH` on yourself,
  Chimera `SHIFT_STUDY`, Many Hands a string of `SHIFT_LUNGE`s, Apex Form's growth a `SHIFT_DECOY_BURST`.

- **A power still being built.** Put it in `ModAbilities.IN_DEVELOPMENT` (empty now). `ModAbilities.rollable` keeps it
  out of every random pick (the Eclipse, the Formula, gifted villagers, retired-power swaps) while admins can still give
  it, and `ClassAbilityGameTests` and `SkillTreeGameTests` don't expect a tree or classes from it yet. It still needs a
  `power_balance.json` entry for every action, an intro `Spec` in `PowerIntroService`, a `MimicColors` colour and a
  `PowerIcons` entry.
- **Changing a player's hitbox.** `MorphBodies` answers `EntityEvent.Size` on both sides from two maps (server and client
  are kept apart because an integrated server shares entity ids with its client). Call `refreshDimensions()` after a
  change on each side; the client also re-checks every tick in case the form arrived before the body did.
- **Drawing a player as something else.** Cancel `RenderPlayerEvent.Pre` and draw a client-only stand-in entity (never
  added to the level) with the same renderer call, copying facing, head, swing, crouch and hurt flash each frame and
  the walk cycle each tick (`WalkAnimationState.update`). Check `UnseenClient.hidden` first.
- **Mobs that ignore someone.** Clear the target in `LivingChangeTargetEvent` (as Invisibility does). For mobs already
  fighting, erase `ATTACK_TARGET` and `ANGRY_AT` from the brain too, and check `hasMemoryValue` before `getMemory`:
  fetching a memory a brain doesn't register throws.
- **Attributes from a form.** Read them from one cached example per type (`ShapeshiftForms.example`), add transient
  modifiers with the `heroesevolved:shapeshift_*` ids, and keep health as a fraction across every change.
- **Swapping a body under cover (phase 2).** A model can't be clipped mid-render, so the change hides the swap: solid
  plates (drawn in the SOLID pass, which writes depth) close over the old body, the stand-in is swapped while the shell
  is shut, and the plates fall away from the new one. `MorphClient.Transition` holds both ends; `ShapeshiftGeometry`'s
  `COVERED`, `SWAP` and `SHED` are the one timeline the renderer and `ShapeArt` both read. Keep one stand-in per form
  per player during a change, or the old and new bodies fight over one and are re-created every frame.
- **Animate a state change, not a resync.** `MorphStatePayload.animate` is true only when the form really changes; a
  viewer who starts tracking you gets the state without the ripple.
- **A hull for any body.** `ShapeArt.hull` lays rows of hinged scales on ellipses that follow a body's outline (an
  upright profile and a beast's mound, blended by height over width). Each scale is a lit fan drawn with `Canvas.v`
  (package-private, so per-vertex colours work): a slate front and a brass back a hair apart, so a plate flipped past
  90° really shows its underside, darker at the hinge and paler at glancing angles. Studying, touching and the morph
  are three ways of driving the same hull (a scan band, a distance from the hand, two fronts).
- **Effects round your own body in first person.** Hiding them leaves the player seeing nothing; drawing them at body
  size fills the screen. Draw a wider version (a curtain about a block out) and cull it just below the eye. Measure
  from the current eye, which already sits at the new body's height when the hitbox changes at the start.
- **A form's own arm in first person.** NeoForge's `RenderArmEvent` gives the empty-hand pose already transformed: cancel
  it and render the form's `HumanoidModel` arm (`resetPose()` first) with its texture. Cancel `RenderHandEvent` at HIGH
  priority to show nothing for a handless form. `FirstPersonArmEvents` asks `MorphClient.formArm` so power gestures use
  the form's arm too.
- **Models on a GUI wheel.** `InventoryScreen.renderEntityInInventory` draws any cached, unspawned entity; scale it by
  its largest dimension and translate by half its height. The form wheel (`gui/screen/FormWheelScreen`) is opened by the
  server (`FormLibraryPayload`), answered with `MorphChoicePayload`, and the server throttles picks (`CHOICE_GAP`).
- **A form is a type plus an optional profile (phase 3).** `ShapeshiftForm` keys a creature by its type id and a person by
  `player:<uuid>`, saves a person's id, name and skin texture, and goes over the network with `ByteBufCodecs.GAME_PROFILE`.
  Compare forms by `key()`, never by type: two people are both `EntityType.PLAYER`. The library still reads phase 1's
  list of bare type ids.
- **Drawing someone as another player.** `MorphClient.Double` is a client-only `RemotePlayer` with the copied profile.
  It takes the skin from the client's `PlayerInfo` if the person is online, otherwise from
  `SkinManager.getInsecureSkin`, and its customisation byte is set so the hat and sleeves show. It takes its owner's
  entity id, so arm poses keyed by id follow it, and so the `RenderPlayerEvent` handler must skip `Double`s or it
  draws them recursively. Hide your own double's name tag with `RenderNameTagEvent.setCanRender(TriState.FALSE)`.
- **Speaking as someone.** Cancel `ServerChatEvent` and send each viewer a system message built from
  `chat.type.text`, with the name's `SHOW_ENTITY` hover and `/tell` click copied from vanilla, so a bystander can't
  tell it apart. Commands never reach `ServerChatEvent`, so `/msg` stays honest for free.
- **Photograph a change by its progress, not by tick.** The preview's `watch(name, points...)` shoots on the render frame
  where `Transition.progress` passes each point, so the shots land on the same moments whatever the server's timing.
- **Many moves on one key (phase 4).** Instinct is one action whose move comes from the form (`InstinctMove.of`).
  Each move carries its own stamina cost and cooldown, kept per move, and its ordinal rides in the `SHIFT_INSTINCT`
  piece's scale (append new moves at the end). Traits (`ShapeshiftTraits`) and hand rules (`ShapeshiftHands`) are
  read from a living example of the form, cached per side, so a modded mob gets the traits of the vanilla class it
  extends and the client never predicts a use the server refuses.
- **A first-person effect from your arm starts at the hand you see.** A Lunge drawn from the shoulder sat on the
  crosshair. `ShapeInstinctArt.viewHand` puts the start low and to the side of the camera, as `ElectroArt.hand`
  does, and the tip is drawn thinner, with no seam ring, so the target stays visible.
- **A held creature's bands must pinch, not flare.** One row of plates hanging from a seam reads as a skirt from
  any camera above it. The grab draws two rows per band, one hanging and one standing, both tipped in at the
  points (a negative hinge angle in `ShapeArt.scale`), so each band reads as a cuff.
- **Side views of a move.** `TempestCamera.fix(at, lookAt)` holds a still camera for preview drivers (clear it
  with nulls). Keep the game in a third-person mode so the player's body is still drawn.
- **A body for mobs to chase (phase 5).** Decoy is a real, unsaved `PathfinderMob` (`ShapeDecoy`, `.noSave().noSummon()`)
  with no AI, so mobs can target it and hit it. `IEntityWithComplexSpawn` sends its shape in the spawn data, so a viewer
  who arrives late still sees the right body, and its renderer draws the same stand-in a morph uses. It cancels damage
  with no entity behind it (falls, fire) and bursts on the first blow.
- **A set piece's scale is clamped to 0.05–64 on the client.** A flag of 0 or -1 arrives as 0.05. Nightmare's viewer
  flag is 2 (a foe, who sees a warden) or 1 (anyone else); the first version used 0 and -1 and showed everyone the warden.
- **`SimpleAction.builder(path, unlock)` takes a tier (1–5), not a Power Level.** Passing 6, 10 or 15 locks the key
  forever (the HUD shows "[Level 15]"). Instinct had the same mistake (tier 3, Level 6) until phase 5.
- **A status that stops creatures attacking.** `ShapeshiftTricks` keeps one "cowed" map for confused and frightened
  creatures: a `LivingChangeTargetEvent` handler clears their target, a `LivingIncomingDamageEvent` handler at HIGH
  priority cancels their blows, and the tick moves frightened ones away with `DefaultRandomPos.getPosAway`.
- **Seen through by other powers.** `ShapeshiftSuspicion.read(reader, target)` is the one call: Telepathy's Read Mind and
  Vibe's Read the Vibe make it. The next power that should see through a disguise adds one line.
- **Growing a player (phase 6).** Apex Form puts a transient modifier on `Attributes.SCALE`: it syncs to every client by
  itself and resizes the hitbox. Two things must follow it: `MorphBodies`' size event multiplies the form's own size by
  `getScale()`, and `MorphClient` scales the pose stack before drawing the stand-in. Call `refreshDimensions()` after
  changing it, or the hitbox waits for the next tick (and a test FakePlayer never ticks).
- **Drawing a creature as someone else.** Borrowed Face sends a `MorphStatePayload` for the creature's id;
  `MorphClient.renderCreature` cancels `RenderLivingEvent.Pre` and draws the stand-in (a person's `Double`), and the
  StartTracking hook sends it to late viewers. Creatures keep their own hitbox.
- **Two target rules on one event.** The kin rule (`ShapeshiftService.target`, normal priority) clears a target to
  null; the "turned" rule (`ShapeshiftTricks.target`, low priority) must check its own map before returning early on a
  null target, or the kin rule wins.
- **Leaving the in-development list.** Removing a power from `ModAbilities.IN_DEVELOPMENT` makes it rollable and puts
  it under the catalog tests: add it to `SkillTreeGameTests.POWERS` (and its skip list if it has its own skill tests)
  and to `tools/verify-skill-content.py`'s expected set.
- **Wiring a new power's tree and classes.**
  - **The tree:** the power's entry in `skill_trees.json` (three lanes of six nodes; positions 1 and 4 active, 5 the
    capstone; a signature appended to a lane with `"requires"` set to that lane's mastery, `_1_2`).
  - **Routing:** a line in `SkillCatalog.Node.action` routes the tree to the power's own class. A case in `StyleCasts`
    (`canUse` and `cast`) routes the classes.
  - **Bookkeeping:**
    - an entry in `SkillTreeService.bonus`'s no-stat-bonus list, and a `forget` call where that class handles a respec;
    - an entry in `EvolutionTrials`;
    - a `case` in `PowerIntroService`'s release for the awakening.
  - **Names:** must match `en_us.json`; check with `python tools/verify-skill-content.py`.
- **Every class ability must cast alone.** `ClassAbilityGameTests.everyClassAbilityCasts` casts each one with only a
  pillager near. Masquerade, which needs friends, now includes the caster and falls back to the nearest creature seen.
- **A short history of shapes.** `ShapeshiftService` remembers the last eight shapes worn (`previous`, `history`,
  `lastCreature`) and when each change happened (`lastChange`). Quick Change, Hybrid, Chimera, Masquerade and Ambush
  Shift all read it; record it wherever a form starts or ends (`morph` and `end` both call `remember`).
- **Borrowing gifts without a shape.** Hybrid's traits come back from `ShapeshiftService.traits(player)` when no shape
  is worn, so every damage and fire hook that already asks about traits works for it unchanged. Its own tick calls
  `ShapeshiftTraitService.tick`. Gifts a client draws (wall climbing, the glowing senses) are left out: the client
  only knows worn shapes.
- **A bonus for one move.** Ambush Shift sets `ShapeshiftInstinct.bonus` around `perform` in `use`, and `hit` multiplies
  by it, so the first Instinct after a change hits harder without every move knowing.
- **Suspicion on the HUD.** `SuspicionPayload` (server to the wearer, only when the value moves a point or reaches 0)
  feeds `gui/SuspicionHudOverlay`, an almond eye under the crosshair that fills brass and flashes red past 70.
  Exposure locks Morph (`ShapeshiftSuspicion.locked`, checked in `study` and `morph`) and bursts the shape off with
  `breakForm`, which sends `SHIFT_EXPOSED` and no ripple (`animate` false).
- **One viewer sees something else.** Nightmare sends each player their own `SHIFT_NIGHTMARE` piece bound to the
  shapeshifter. `SetPieceClient.boundTo(piece, entity)` finds it, and `MorphClient` draws a roaring warden stand-in
  (`roarAnimationState`) in that player's place only on a foe's client.
- **A walking shell.** The Doppelganger is a `ShapeDecoy` with a walk vector: it steps along the ground on the server
  (up a block where it must, stopping at a wall). On the client, `ShapeDecoy.tick` advances its stand-in's walk cycle
  from its own movement.
- **Morph flags.** `MorphStatePayload.flags` carries per-wearer drawing rules (`PERFECT_COPY` hides your own gear in a
  person's shape). Add a bit here before adding a payload.
- **Voice chat stays on the voice thread.** Masking a speaker's voice would mean re-broadcasting audio to whoever is
  near, but `MicrophonePacketEvent` runs off the server thread, where the level can't safely be searched (Telepathy only
  reads routes published from the server tick). The planned Familiar Voice became Practised Manner instead.
- **Chat as someone is a server choice.** `HeroesEvolvedConfig.COMMON.shapeshiftChatImpersonation` turns it off. Nobody
  may copy an operator (`ShapeshiftService.operator`), the copied person is told, and operators and telepaths who have
  read the speaker see who really spoke (`ShapeshiftChat.seesThrough`).
- **Photographs:** `tools/shapeshift-preview.init.gradle` (`uitest/ShapeshiftPreview`, flag `heroes.preview.shapeshift`)
  shoots every phase into `build/shapeshift-preview/screenshots/`.
  - To photograph what only a foe sees, the driver sends the foe's private piece to the local player.
  - A front camera behind a standing mob ends up inside it: move the player clear first.
  - Fixed cameras (`side()`) only see what is staged in front of them.
