# Finishers

A finishing move on every power, in the spirit of Destiny 2's finishers: a foe near death starts to glisten, and one
key ends them with a short, showy move that belongs to your power alone.

## What's built (November 2026)

- **The shared engine** (`combat/Takedowns`).
  - The rules: the threshold, the glisten and its gold outline, the C key and Shift + C to switch finishers off and on
    (saved with the player), the request's checks, holding the foe, half damage and breaking on a heavy blow, the
    killing blow, the overkill splash, a 10-stamina refund, the shared cooldown and the side-view camera.
  - Balance: `finisher_<power>` in `tools/balance.py`, 8 stamina and 3 seconds.
- **Three finishers for every power (60).** Each power has its signature (Set B, except Telepathy's Lights Out from
  Set A) and two alternates. **Each press of C picks one of the three at random, never the one you used last**, so
  a fight doesn't end the same way twice. The alternates live in one class per power (`pyro/PyroFinishers` and so
  on) and give the signature's reward. A reward that depends on how the move began (Last Warmth's hot foe) needs
  the alternate to set the same `run.state`; Sonic's alternates spend no Resonance, so they give none back.

  | Power | Signature | Alternates | Alternates' class |
  |---|---|---|---|
  | Pyrokinesis | Dragon Coil | Phoenix Kick, Hearth | `pyro/PyroFinishers` |
  | Space-Time | Closed Loop | Stopped Clock, Unwound | `time/TimeFinishers` |
  | Telepathy | Lights Out | Pressure, Last Thought | `telepathy/TelepathyFinishers` |
  | Electrokinesis | Short Circuit | Lightning Rod, Overload | `electro/ElectroFinishers` |
  | Hydrokinesis | Breaker | Drowning Bubble, Riptide | `hydro/HydroFinishers` |
  | Cryokinesis | Last Warmth | Coffin, Icicle Ring | `cryo/CryoFinishers` |
  | Superspeed | Late Thunder | Hundred Fists, Flick | `speed/SpeedFinishers` |
  | Flight | Downburst | Vacuum Clap, Updraft | `flight/FlightFinishers` |
  | Quake | Counterbeat | Fault Line, Shatter Point | `quake/QuakeFinishers` |
  | Sonic Resonance | Encore | Shatter Note, Silence | `sonic/SonicFinishers` |
  | Telekinesis | Cut Strings | Crush, Toss | `telekinesis/KineticFinishers` |
  | Density | Six Feet | Phase Punch, Heavy Hand | `phasing/DensityFinishers` |
  | Invisibility | Hall of Mirrors | Bubble Crush, Lens Strike | `invisibility/ForceFinishers` |
  | Regeneration | Paid in Full | No Pain, Grip | `regeneration/RegenFinishers` |
  | Induced Radioactivity | Chain Reaction | Half-Life, Pocket Sun | `radiation/RadiationFinishers` |
  | Vibe | Off Frequency | Breach Drop, Point Blank | `vibe/VibeFinishers` |
  | Technopathy | Hard Reset | Kill Switch, Overclock | `technopathy/TechnoFinishers` |
  | Matter Assembly | Monument | Hammer, Shard Orbit | `assembly/AssemblyFinishers` |
  | Empathic Mimicry | Take It In (or the channeled power's three) | Echo, Borrowed Strength | `mimicry/MimicFinishers` |
  | Shapeshifting | Unmade | Apex Bite, Its Own Face | `shapeshift/ShapeFinishers` |

  What each looks like is in its power's own doc, under "Finishers".
- **The pick** (`Takedowns.choose`): the power's finishers that reach the foe and are available (Borrowed Strength
  only once an empath has kept a power with a finisher), minus the one used last (`LAST`, forgotten at logout). An
  empath channeling a power picks from that power's three. Every finisher of a power shares its balance entry.
- **You stay where you stood.** Breaker, Downburst and Late Thunder were reworked so you never leave your spot by
  more than about two blocks: Breaker's wave lifts you in place and carries you one block, Downburst rises from your
  own spot and dives back onto it (the air rolls on to the foe), and Late Thunder coils in place while its lane burns
  in behind you and dashes two blocks at most. The body moves along a path on your own client
  (`Takedowns.glide`), so it is smooth at any frame rate instead of jumping once a tick. Every alternate keeps you
  within that too: the ones that move something move the foe.
- **Last Order was built and dropped.** Telepathy's Set B finisher was built first, but it needs another angry
  mob nearby to turn the foe on, so in most fights it wasn't worth taking.
- **Changes to Set B as built** (each power's doc has the details):
  - **Dragon Coil** gives only Heat. The loaded fist's orb lingered in the hand after the move, so it was dropped.
  - **Late Thunder** has no freeze-frame on impact. At 100 Momentum, instead of skipping the run-up, the cone
    carries the overkill down the line behind the foe.
  - **Downburst** gives Skyburst back ready, rather than an instant charge.
  - **Cut Strings** doesn't fire held Bullet Catch shots, and the dance is turns, a jerk and a hop rather than
    posed limbs.
  - **Six Feet** keeps you on the surface: you blink behind the foe rather than sinking through the floor.
  - **Monument** fires its three Shards at the nearest foes at once, rather than leaving them orbiting for later
    Shard Lances.
  - **Take It In** gives nothing extra against a foe without a power.
  - **Unmade** doesn't turn you into the foe first.
- **Differences from the plan:**
  - **Unlocks.** All three of a power's finishers open at Power Level 3 (the plan put Set B at 10, beside Set A).
  - **No choosing.** Instead of picking one finisher per power on the Powers page, the key picks at random among all
    three each time.
  - **Bosses.** Bosses, the Warden, the elder guardian and story actors are in the `heroesevolved:unfinishable`
    entity tag and never glisten, except at a boss's own phase thresholds (Sylar; see "Bosses" below).
  - **Who glistens.** Monsters, creatures that are fighting (vanilla's aggressive flag), anything hunting you, and
    players where PvP is on.
  - **Sound.** Each finisher opens with its power's `HEAVY` cue and layers vanilla sounds; the kill lands with a
    shared deep thump. Bespoke synthesized cues are still to come.
- **Testing.**
  - Game tests: `combat/TakedownGameTests` (`everyFinisherFinishesALowPillager` runs all sixty in turn, each forced
    with `Takedowns.request(player, id, finisher)`; `theKeyPicksADifferentFinisherEachTime` checks the pick and that
    every power has three; `borrowedStrengthNeedsAKeptPower`).
  - Photographs: `./gradlew runClient --init-script tools/finisher-preview.init.gradle` (add `-Ponly=telepathy` to
    shoot one power, or `-Ponly=phoenix_kick,hearth` for finisher ids) saves each finisher's beats from its own camera
    and from behind into `build/finisher-preview/screenshots/`. Borrowed Strength isn't photographed (it needs a kept
    power).

Every power has two finishers: **Set A** (the first draft) and **Set B** (a second, deeper draft that ties each
finisher to its power's resource and gives something back). The player picks one per power; see "Choosing a
finisher".

**A word clash to settle.** The fighting styles already call their third combo strike a "finisher" (Blitz, Double
Palm, Headbutt and the rest, in [fighting-styles.md](fighting-styles.md)). The key and the menus here say
**Finisher**, as Destiny does, so in code this system is `combat/Takedowns`. The combo's third strike could be
renamed "combo ender" in its docs and tooltips so players never confuse the two.

## How it works for every power

### When a foe can be finished

- **The threshold.** A creature can be finished when its health is at or below `max(4, 20% of max health)`,
  capped at 12 health, so a zombie glistens at 4 and a ravager (100) at 12.
- **Who can't be finished.**
  - Bosses (the Wither, the Ender Dragon, the Warden) can't be finished; Sylar only at his thresholds (see "Bosses" below).
  - Players only where PvP is allowed, at 20% of max health.
  - Allies and pets, by the shared friend rule (`StyleOps.isFriend`).
  - Anything already held by a Still World, a cage or a bubble *can* be finished. Being set up for it is half the
    fun.

### The glisten

- **What you see.** A finishable foe within 12 blocks gets a pale gold shimmer on its outline, and a slow
  glint that runs up its body once a second.
  - It looks the same for every power, so players learn one signal.
  - Only players who own a power with a finisher see it.
  - It is drawn on the client from the foe's synced health and max health: no packet per mob.
- **Aiming.** When the crosshair is on a glistening foe within 4 blocks (6 for ranged finishers), the shimmer
  brightens and a small key prompt (`[C] Finish`) appears under the crosshair.
- **Setting.** `ClientVisualSettings.finisherGlisten` turns the shimmer off for players who find it noisy;
  the prompt still shows.

### The key

- **Default key:** `C` (vanilla's "save toolbar", rarely used). Rebindable as **Finisher** in Controls.
- **Toggle:** `Shift + C` turns finishers on or off, so a player who would rather keep mashing isn't pulled into
  an animation by accident. The state is shown on the stamina bar as a small mark and saved per player.
- **Press with no glistening foe in reach:** nothing happens and nothing is spent.

### What a finisher does

- **Damage.** It deals 1.5 times the foe's finish threshold. That always kills a foe at the threshold; the rest
  ("overkill") spills onto other hostiles within 4 blocks as the power's own kind of hit. It goes through each power's
  own damage helper (`SonicService.hit`, `ElectroService.shock`, `PyroService.burn` and the rest), so statuses and
  cross-power rules (Soaked, Ringing, Frost) still apply.
- **Length.** Between 0.8 and 1.6 seconds.
  - The foe is held in place for the whole of it (the same anchor a Force bubble uses).
  - You take half damage while it plays.
  - You can't be finished yourself while it plays.
- **Interruptions.** A blow over 6 damage, a stun or a power dampener stops a finisher partway: the foe falls free
  and the cost is spent. Being hit is the counterplay.
- **Cost.** Each power's finisher is a balance entry `finisher_<power>` in `tools/balance.py` (start: 8 stamina,
  3 s cooldown).
  - A kill refunds 10 stamina, so finishing is the efficient way to end a fight, not a tax.
  - The cooldown is shared across every finisher, so Mimicry can't chain borrowed ones.
- **Camera.** Third-person finishers pull the camera back to a fixed three-quarter view for their length, as
  `TempestCamera` does for the tornado, and return it. A setting keeps the camera in first person, where each
  finisher has its own first-person arm version.
- **Sound.** Every finisher gets a dedicated cue in `PowerSounds` (`finisher_<power>`), louder than a normal move,
  plus a shared low "thud" at the moment of the kill so the ending lands the same way for every power.

### Bosses

- Sylar glistens in the last 6% of his health above each of his phase thresholds (two thirds and one third), and in
  his last tenth, instead of at low health.
- A finisher there plays in full, but doesn't kill: it takes 15% of his health (through his damage caps), forces the
  next phase at once, and leaves him reeling for five seconds once its transition is over.
- In his last tenth the finisher is the killing blow (if Claire's case still stands, her gift still gets him back up
  once, unless the case breaks).
- Built on one hook in the engine, `Takedowns.PhaseFinishable`: a boss says when it glistens (a synced flag, so the
  client's glisten agrees) and what a finish does to it; the finisher's own blow is replaced by that.

### Mimicry and Shapeshifting

- Peter uses the finisher of whichever power he is channelling (`FightingStyles.of` already resolves this). With
  nothing channelled he has his own (below).
- A shapeshifter's finisher changes with the shape worn (below).

### Choosing a finisher

- **As built: random.** Each press picks one of the power's three at random, never the last one used (see "What's
  built"). The plan below (a pick saved per power) is kept for reference.
- **Unlocks (plan).** Each power's Set A finisher unlocks at Power Level 3 and its Set B finisher at Power Level 10.
- **Picking (plan).** The Powers page gets a **Finisher** row with both cards, each with a looping preview of the
  move (the same stand-in models the form wheel draws). The pick is saved per power.
- **Balance.** All of a power's finishers share one balance entry, `finisher_<power>`, so none is the cheap choice.

## Set A: the first finishers

Each entry says what the player sees, which existing primitives and poses draw it, and any special rule. The art
should come from each power's own art class: a finisher is that power's language at its loudest, not a new
style.

### Pyrokinesis: **Dragon's Descent**

- **What happens.** You kick off the ground with both boot jets, flip over the foe, and drive a flaming axe kick down
  through them. The fire rolls out across the ground in a ring and leaves a scorched star on the floor.
- **Art.**
  - The flip is `PyroArt.crescent` in the vertical plane, from the flip kick.
  - The jets are the Flame Flight boot jets.
  - The ring is the Firestorm footprint, at a quarter of its size, flashing once.
- **Pose.** The backflip from `FightingStylePoseRenderer`, held at the top for two ticks, then a snap down into a
  one-legged kneel.
- **Rule.** With blue fire, the whole move is blue and the scorch is soul fire for a second. It places no real fire.

### Electrokinesis: **Lightning Rod**

- **What happens.**
  1. You grab the foe by the collar.
  2. You thrust your other hand to the sky, and a bolt comes down into it.
  3. You channel the bolt through your body and out of the gripping hand.
  4. The foe locks rigid, lit from inside, and is thrown back on a final crack.
- **Art.**
  - The sky bolt is `ElectroArt.fork` from far above.
  - The body channel is a `braid` down your arm.
  - The foe is an `ElectroArt.sketch` overlaid on its body, flaring and burning out.
- **Pose.** The `ELECTRO_CALL` pose on one arm and `ELECTRO_TOUCH` on the other.
- **Rule.** A Soaked foe sends the overkill as a chain to two more foes, as Chain Lightning does.

### Hydrokinesis: **Deep Hold**

- **What happens.**
  1. The water round your waist pours off and wraps the foe in a sphere.
  2. You close your fist, and the sphere crushes inward.
  3. You open your hand: the water bursts outward in a crown of spray and falls as rain round you both.
- **Art.**
  - The sphere is `HydroArt.sheet` closed into a ball.
  - The crush and burst use `foam` lips.
  - The rain is `spray`.
- **Pose.** `WATER_CALL` sweeping into a closing fist.
- **Rule.** It refills your reservoir to half if it was lower. The foe is left Soaked if it somehow survives (a boss).

### Cryokinesis: **Shatterpoint**

- **What happens.**
  1. You touch the foe's chest, and it freezes solid from that point outward in a visible creeping front.
  2. You step back and snap your fingers.
  3. The statue bursts into tumbling ice shards that slide across the ground and melt.
- **Art.** Reuse the Crystal Prison shell and the shatter burst. Run the freeze as a front across the body over
  eight ticks rather than all at once.
- **Pose.** A palm forward, then a snap at shoulder height.
- **Rule.** It counts as a deliberate shatter, so Shatter payoff skills trigger on it.

### Superspeed: **Thousand Hits**

- **What happens.**
  1. Time seems to slow round the foe.
  2. A ring of your afterimages surrounds them, each landing a blow from a different angle, faster and faster.
  3. You reappear where you started, already turning away, and the foe collapses a beat later.
- **Art.**
  - `SpeedAfterimages.ring` places eight images at once, then `at` places single images strike by strike.
  - Gold `LightningRenderer` ribbons trace every path.
  - A small `SPEED_MIRAGE`-style crowd pulses at the end.
- **Pose.** Your body is hidden while the afterimages fight (`TimeVeil`-style), then shown mid-turn.
- **Rule.** It is the fastest finisher (0.8 s), so it is the hardest to interrupt.

### Flight: **Skyfall**

- **What happens.**
  1. You grab the foe and blast straight up 20 blocks in a Skyburst.
  2. You flip at the top.
  3. You drive them back down into the ground at full speed. The impact sends out a ring of condensation and dust.
- **Art.** `FLIGHT_SKYBURST` going up, `FLIGHT_IMPACT` at the bottom, and a `spiral` at the apex for the flip.
- **Pose.** `FLIGHT_BRACE` going up, `FLIGHT_ROLL` at the apex and `FLIGHT_BRACE` going down.
- **Rules.**
  - The foe rides as cargo, using the existing carry anchor.
  - Every tick is swept, so under a low ceiling it is a short rise and a slam.
  - It needs open sky above, or it plays the short version.

### Quake: **Closing Seam**

- **What happens.**
  1. You slam both fists into the ground in front of the foe.
  2. The ground splits under them, and they drop waist-deep into the crack.
  3. You clap, and the two sides slam shut on them with a ring of dust and a camera shake.
- **Art.** `QuakeArt` fissures opening and closing, and a closing shockwave ring.
- **Pose.** A two-fisted slam, then the clap from the Shatter follow-up.
- **Rule.** The foe disappears into the ground (no corpse), as Density's entomb does. It breaks no real blocks.

### Sonic Resonance: **Resonant Collapse**

- **What happens.**
  1. You hum a single note into the foe's chest from close range.
  2. Rings of colour stack on their body, faster and higher.
  3. You stop. In the silence the foe holds still for a beat and then comes apart in one last glassy chord, with a
     visible shatter front.
- **Art.**
  - `SonicArt.front` rings stacking on the body.
  - `glyph`s orbiting, sliding through the `tone(k)` blend.
  - The finish is a single wide `front` leaving the body.
- **Pose.** A cupped hand at the mouth, as in the scream, then a flat palm.
- **Rule.** A Ringing foe gives a longer chord, and the overkill passes on as Ringing.

### Telekinesis: **Crush Grip**

- **What happens.**
  1. You raise one hand, and the foe rises off the ground, twisting.
  2. You close the hand slowly, and the foe is pressed in from all sides.
  3. You throw the hand down, and the foe is slammed into the ground and stays there.
- **Art.** `STYLE_TK_GRASP` (the closing fingers) at full size round the foe, then `STYLE_TK_PALM` on the slam.
- **Pose.** The Puppeteer's lift, a closing fist, and a downward throw.
- **Rule.** Nearby loose items and projectiles are pulled into the slam as part of the overkill splash.

### Telepathy: **Lights Out** (built)

- **What happens.**
  1. You touch two fingers to your temple and stare.
  2. The foe freezes and its head bows as psychic halos close round it.
  3. The lights go out. Everyone nearby hears a whisper in chat ("...quiet now.").
- **As built** (`telepathy/LightsOut`, art in `TelepathyArt`):
  - A thread runs from your temple and an iris opens round the foe's mind, and its eyes go pale.
  - Two halos close (one at the crown, one round the chest) with thought-waves falling in, and a hush ripples over
    the ground.
  - The iris shuts like an eyelid behind a dark disc.
  - Mobs can't kneel, so the head bows instead.
  - Details are in [telepathy.md](telepathy.md).
- **Pose.** Two fingers to the temple, the other hand outstretched (Read Mind's `TELE_READ`).
- **Rules.**
  - Ranged (6 blocks) and no contact: it is the only finisher that doesn't touch the foe, which suits a telepath.
  - Hostiles within 8 blocks lose their target for a moment.
  - The overkill spills as the same quiet blow.

### Space-Time Manipulation: **Ten Past Ten**

- **What happens.**
  1. Time stops round the foe: a violet clock face opens behind them.
  2. You fold behind them three times, each time leaving a cut that doesn't land yet.
  3. The clock's hands snap to ten past ten, time resumes, and all three cuts land at once.
- **Art.** `TimeArt.face` stopped (violet and ice), the Moment's Edge folds and the `TIME_SNAP` piece on the resume.
- **Pose.** The fold pose for each cut, ending turned away from the foe.
- **Rule.** It is built on Moment's Edge's sequence (hold, fold, store, release): it stores the cuts and lands them
  together, through the normal `TimeStopService.storeImpact` path.

### Density (Phasing): **Inside Out**

- **What happens.**
  1. You phase your arm into the foe's chest.
  2. Your arm turns solid inside them, with a heavy ripple.
  3. You pull it out, and the foe drops.
- **Art.** `DensityArt` ripples running from the entry point, with the Ambush hand on your fist.
- **Pose.** A straight thrust, then a short twist and a slow pull.
- **Rule.** It ignores armor and shields. It is the one finisher that works on a foe behind a thin wall, through the
  wall (Reach Through's reach).

### Invisibility (Force Fields): **Pressure Point**

- **What happens.**
  1. You fade from sight.
  2. A force bubble snaps shut round the foe and shrinks.
  3. The bubble cracks, and you reappear behind them, palm out, as it bursts into tumbling shards.
- **Art.** `ForceArt.cage` shrinking, `stress cracks` across it and a burst of `facet`s.
- **Pose.** Hidden, then `FORCE_PUSH` from behind.
- **Rule.** If you were already faded, the bubble closes without you appearing at all.

### Regeneration: **Bloody Knuckles**

- **What happens.**
  1. You ignore the foe's last swing, which visibly hits you, and grab their arm.
  2. You headbutt them, then land a haymaker.
  3. Your split knuckles and brow seal shut on screen as they fall.
- **Art.** `RegenArt` blood in the soft pass on each blow, with `gash` lines healing closed on your hands.
- **Pose.** The Reckless Brawler's haymaker, with a head snap before it.
- **Rule.** You take a small scripted hit (2 health) at the start and heal 6 at the end. It is the only finisher that
  heals.

### Induced Radioactivity: **Half-Life**

- **What happens.**
  1. You grab the foe with a glowing hand.
  2. Green-white light floods through their body, shown as an X-ray outline of the skeleton.
  3. They decay into ash and a small rising mushroom of dust.
- **Art.** `RadiationArt.blast` at a tiny size round the foe, and a small mushroom from `MiniBlast`'s cloud scaled
  down.
- **Pose.** A grip on the shoulder, then the other hand pulled back and released.
- **Rule.** It leaves a small `RAD_ZONE` (2 blocks, 3 s) where the foe stood, which doses other foes that walk in.

### Vibe: **Wrong Side**

- **What happens.**
  1. You open a Breach right under the foe; they drop through it.
  2. A second Breach opens above, and they drop out of it onto the ground.
  3. You close both with a clap, and the vibration rattles them apart.
- **Art.** Two `VibeArt` breaches, the crossing ellipse from `VibeGeometry.crossing`, and a Vibe Blast ring on the
  clap.
- **Pose.** Both hands apart opening the breaches, then the clap.
- **Rule.** It has no real dimension change. The foe is moved with the same anchor the other finishers use.

### Technopathy: **Grounded**

- **What happens.**
  1. You snap your fingers.
  2. Every enrolled node within 16 blocks lights up, and a line of current runs along the circuit to the foe's feet.
  3. Cables of light whip up from the ground and bind them.
  4. The circuit closes on them with a loud relay click.
- **Art.** Tripwire's visible lines and lit block edges along the path. With no circuit enrolled, a single short line
  runs from your hand.
- **Pose.** A finger snap, then a palm turned down.
- **Rule.** With a big enrolled circuit it looks spectacular (every lamp in range flickers), which rewards the
  power's setup play.

### Matter Assembly: **Iron Maiden**

- **What happens.**
  1. Plates tear up from the ground all round the foe and fly in, in arcs.
  2. They fold shut into a tall closed pillar round the foe.
  3. You make a fist, and the pillar compresses into a single Shard.
  4. The Shard falls and crumbles.
- **Art.** The Compression Cage's assembly (plates flying in with bright seams behind them), then a fast shrink to a
  Shard.
- **Pose.** Both hands pulling up from the ground, then one fist closing.
- **Rule.** The pillar is drawn only, not real blocks, so it can't trap anyone else.

### Empathic Mimicry: **Echo Chamber**

- **What happens.**
  1. With nothing channelled, coloured threads run out to every power you remember.
  2. One echo of each strikes the foe in turn, each in its own colour.
  3. A final white flash plays as they resolve together.
- **Art.** `MimicArt.thread` per remembered power, using `MimicColors`, with a small flash per hit in that power's
  colour.
- **Pose.** Arms wide, then both hands thrust forward.
- **Rule.** With a power channelled, you use that power's finisher instead (as the fighting styles do).

### Shapeshifting: **Apex Instinct**

- **What happens.** The finisher depends on the shape you are wearing.
  - **Beast** (wolf, ravager, warden, and other big creatures): a pounce and pin, then a bite.
  - **Small or flying creatures:** a flurry of strikes from every side.
  - **Person:** you take on the foe's own face for a moment, step in, and they collapse.
  - **No shape:** your arm grows plated into a blade-like limb for one strike.
- **Art.** `ShapeInstinctArt` `streak` and `around`, and for the plated arm `ShapeArt.hull` brass plates that close
  over it and fall away.
- **Pose.** Taken from the form's own Instinct move (`InstinctMove.of`).
- **Rule.** Finishing while disguised adds no Suspicion: no one is left to tell.

## Set B: the second finishers

Set B goes deeper into each kit. Every entry has the same parts:

- **Why it fits:** the power's own idea that the move is built on.
- **Beats:** what happens, with tick timings (20 ticks = 1 second).
- **Twist:** how the power's resource or state changes it (Heat, Charge, Strain, Momentum, Core and the rest).
- **Reward:** what it gives back. This is Destiny's finisher mods built in, and it is what makes finishing a choice
  rather than a flourish.
- **Drawn with:** the existing art, poses and rules it reuses, and anything new it needs.

All the shared rules above still apply (the threshold, the hold, half damage, interrupts, the overkill splash).

### At a glance

| Power | Set B finisher | Twist | Reward |
|---|---|---|---|
| Pyrokinesis | Dragon Coil | 70+ Heat: blue dragon, tail lashes a second foe | +20 Heat |
| Electrokinesis | Short Circuit | 100 Charge: the foe's lightning sketch stays as a trap | +25 Charge |
| Hydrokinesis | Breaker | In water or rain: double size, foam drags foes | Reservoir refilled |
| Cryokinesis | Last Warmth | Hot foes give a bigger ember and snuff a Pyro | Heat-theft stamina refund |
| Superspeed | Late Thunder | At 100 Momentum: no run-up | Momentum set to 60 |
| Flight | Downburst | Momentum widens the blast ring | Next Skyburst charges instantly |
| Quake | Counterbeat | At 80+ Strain: a safe vent | −50 Strain |
| Sonic Resonance | Encore | Each 20 Resonance adds a pass | Half the Resonance back |
| Telekinesis | Cut Strings | Held shots fire as the strings snap | Grip ready again |
| Telepathy | Last Order | No other foe near: it kneels to you | Focus on every mind near |
| Space-Time | Closed Loop | In stopped or slowed time: three of you | Fold Space ready again |
| Density | Six Feet | At Dense: the hammer roots nearby foes | Ambush primed |
| Invisibility | Hall of Mirrors | Unseen Constructs hides the panes from foes | 3 s of free Fade |
| Regeneration | Paid in Full | The punch adds what you suffered | +30 Regrowth |
| Induced Radioactivity | Chain Reaction | During a Meltdown warning: an emergency vent | −25 Core |
| Vibe | Off Frequency | Marked foes are hit by the copies | Breach Guard ready again |
| Technopathy | Hard Reset | Domino Engine runs the ripple as recoil | Circuit enrolment renewed |
| Matter Assembly | Monument | (none) | Three free Shards |
| Empathic Mimicry | Take It In | Powered foes are imprinted at once | A kept set, or attunement |
| Shapeshifting | Unmade | In a kin shape, no Suspicion | The foe's form, unstudied |

### Pyrokinesis: **Dragon Coil**

- **Why it fits.** Firebending is the mod's movement bar. Three kicks in a row, then the fire they left becomes one
  creature.
- **Beats.**
  1. (0–6) A spinning hook kick lays a low crescent of flame round the foe's legs.
  2. (6–12) A turning back kick lays a second crescent at chest height.
  3. (12–18) A jumping roundhouse lays a third at head height.
  4. (18–28) The three crescents join tail to head into a coiled dragon round the foe. Its head rears over them and
     bites down, the coil flashes, and it unravels into embers. You land in a low stance, one palm forward.
- **Twist.** At 70 Heat or more, the dragon is blue with white eyes, and its tail lashes the nearest other foe
  (that is the overkill).
- **Reward.** +20 Heat. (A loaded rear fist was tried too, but its orb lingered in the hand after the move.)
- **Drawn with.**
  - Three `PyroArt.crescent`s at three heights.
  - A new `coil`: the crescent's flame tongues laid along a helix, with a head built from `billows`.
  - Whole-body turns in `FightingStylePoseRenderer`: a spin, a twist, a jump kick and a landing.
- **Sound.** Three whooshes rising in pitch, then a roar made from the flame cue pitched down.

### Electrokinesis: **Short Circuit**

- **Why it fits.** Elle is playful and cruel, and lightning already draws bodies as sketches (Arc Step, Flashback).
  Here the foe's own sketch is pulled out of it.
- **Beats.**
  1. (0–4) You tap two fingers on the foe's forehead. A corona snaps on at that point.
  2. (4–14) Sparks crawl outward over the body and trace it as an `ElectroArt.sketch` that brightens while the foe
     twitches.
  3. (14–20) You blow on your fingertips. The sketch tears loose and rises out of the body like a ghost of light.
  4. (20–24) It earths: it snaps down into the ground as a bolt and burns a scorch star. The body drops.
- **Twist.** At 100 Charge the sketch doesn't earth. It stays standing as Flashback's static for 3 seconds and
  bursts on the first foe that touches it: a trap in the shape of the foe you just killed.
- **Reward.** +25 Charge.
- **Drawn with.** `ElectroArt.sketch` laid over the foe's model (scaled to its box), `corona`, `fork` and the
  Grounding Strike's scorch star. Pose: `ELECTRO_POINT` with two fingers, then a hand to the mouth.
- **Sound.** A tick, a rising buzz, a soft breath, a crack.

### Hydrokinesis: **Breaker**

- **Why it fits.** Wave Rider and Crashing Wave in one move: you ride the wave that ends them.
- **Beats.**
  1. (0–8) A small wave rises under your feet and carries you two blocks back and up, its lip curling behind your
     heels.
  2. (8–18) It grows into a full Crashing Wave over the foe, and you ride along its crest.
  3. (18–24) The lip throws forward onto the foe, and you ride it down.
  4. (24–30) The wave breaks into foam that runs out across the ground. You step off it into a kneel.
- **Twist.** In water or rain the wave is twice the size, and its foam drags other foes 3 blocks with it (the
  overkill). Underwater it becomes a whirl round the foe instead of a wave.
- **Reward.** The wave goes back into you: your reservoir is refilled.
- **Drawn with.** `HydroArt.wave` along `HydroGeometry.waveFront` and `waveFloor`, so it climbs steps; `foam` and
  `spray`; the `WATER_SURF` stance and its roll.

### Cryokinesis: **Last Warmth**

- **Why it fits.** Cryokinesis is heat absorption. This finisher shows it: the foe's warmth leaves them as a visible
  ember.
- **Beats.**
  1. (0–6) You hold an open palm a block from the foe's chest. Frost breath curls from their mouth.
  2. (6–16) A small ember is drawn out of their chest along a glowing thread to your palm. Frost spreads over them
     from the edges inward as it leaves.
  3. (16–20) You close your hand on the ember. It goes out with a hiss of steam between your fingers.
  4. (20–26) The foe, white with rime, tips over and breaks into snow and small chips of ice.
- **Twist.** Hot foes (blazes, magma cubes, striders, a Pyrokinetic player) give up a big flame instead of an ember.
  It doubles the refund, and a Pyro's flight jets and Breath go out (the existing snuff rule). On a radioactive
  player it also drains 30 Core.
- **Reward.** It counts as a Shatter-payoff heat theft, so it refunds stamina.
- **Drawn with.** The ember is the only warm colour in the kit, a deliberate contrast. The Crystal Prison frost front
  runs across the body, then snow particles. Pose: a palm out, then a fist at chest height.

### Superspeed: **Late Thunder**

- **Why it fits.** Momentum 100 already breaks the sound barrier. In this finisher, the boom arrives after the hit.
- **Beats.**
  1. (0–6) You blur 10 blocks back along a lane in a smear of afterimages.
  2. (6–12) You run in. Gold lightning runs over you, and white rings pile up at your shoulders.
  3. (12–15) The punch lands. **Hit-stop:** for three ticks the foe, the afterimages and the dust all hold still.
  4. (15–24) The cone of white rings bursts out behind the foe, and they are thrown in a straight line. The boom
     plays six ticks later, from the point of impact.
- **Twist.** If you already have 100 Momentum, there is no run-up: the punch lands at once (14 ticks in all).
- **Reward.** Momentum is set to 60.
- **Drawn with.** `SpeedAfterimages.along` and the existing sound-barrier cone. The hit-stop happens only on clients:
  for those three ticks, interpolation is frozen for the foe and the afterimages. The server doesn't pause.

### Flight: **Downburst**

- **Why it fits.** The air is Flight's weapon. You never touch the foe: the air you push does.
- **Beats.**
  1. (0–8) You shoot straight up 10 blocks, with a ring of vapour at your feet.
  2. (8–12) You hang and flip head-down.
  3. (12–18) You dive with a pressure front ahead of you and stop a block above the foe.
  4. (18–28) The air keeps going. It flattens the foe, blows a ring of dust and grass flat outward, and you hover
     over the ring with your arms out.
- **Twist.** Momentum from cruising widens the ring (3 blocks plus a block per 25 Momentum), and the overkill lands
  in that ring. Under a low ceiling the rise is shorter.
- **Reward.** Your next Skyburst within 5 seconds charges instantly.
- **Drawn with.** `FLIGHT_SKYBURST` fronts turned downward, `arc` sheets for the flattened ring, and `FLIGHT_BRACE`
  pointing down, then the hover pose. Every move is swept.

### Quake: **Counterbeat**

- **Why it fits.** Daisy feels heartbeats (Read the Pulse). Here she answers one.
- **Beats.**
  1. (0–6) A palm flat on the foe's chest. A double-pulse ring shows their heartbeat.
  2. (6–18) The pulses come faster, and your forearm shakes in time with them.
  3. (18–22) One counter-pulse from your palm: rings closing inward.
  4. (22–26) Silence. One flat ring spreads out over the ground, and no more pulses come. The foe sags.
- **Twist.** It spends Strain instead of adding it. At 80 Strain or more it vents 50 safely, and your forearms
  visibly stop shaking. The heartbeat plays louder the higher your Strain.
- **Reward.** −50 Strain: a way out of the red zone that also ends a fight.
- **Drawn with.** The heartbeat piece and Resonate's shivering rings from `QuakeArt`, with a ring closing inward.
- **Sound.** Heartbeats speeding up, one deep thump, then a thin high ringing over silence.

### Sonic Resonance: **Encore**

- **Why it fits.** Feedback Loop already amplifies a Cry through your own constructs. Encore bounces one between
  your Note and your hand until the foe gives out.
- **Beats.**
  1. (0–4) You flick a Resonant Note into the air behind the foe.
  2. (4–10) A short point-blank scream goes through the foe and hits the Note.
  3. (10–26) The Note throws it back through the foe to your raised palm, and you throw it back again. Each pass is
     faster, in the next `tone(k)` colour: rose, amber, mint, azure, violet.
  4. (26–30) On the last pass the Note breaks harmonic, and the foe comes apart with it.
- **Twist.** Each 20 Resonance spent adds a pass and a colour. At full Resonance, the last pass is a Crescendo that
  Rings every foe within 6 blocks (the overkill).
- **Reward.** Half the Resonance spent comes back.
- **Drawn with.** `SonicArt.front` running between two points (the same geometry as `onLine`), `staff` and
  `glyph`s. Poses: a flick, the scream, then a palm up to catch.

### Telekinesis: **Cut Strings**

- **Why it fits.** The Puppeteer style, taken literally.
- **Beats.**
  1. (0–6) You raise one hand with fingers spread. Thin lines run from each fingertip to the foe's limbs and head,
     and small fingers close at each end.
  2. (6–20) You lift the foe off the ground and make it dance: two jerky steps, an arm raised, a bow, while your
     fingers play.
  3. (20–24) You draw a finger across the strings, and they snap one by one.
  4. (24–28) The foe drops in a heap like a dropped puppet.
- **Twist.** A mob holding a weapon drops it, and the weapon floats to your hand (Disarm's rule; players keep their
  gear). If Bullet Catch is holding shots, they fire into the foe as the strings snap.
- **Reward.** Grip is ready again (its cooldown resets).
- **Drawn with.** A new swaying `string` line, with `STYLE_TK_GRASP` at small size on each end. The dance is a
  client-side pose override, so it works on humanoid models (zombies, skeletons, illagers, players). Other bodies
  bob and turn on the strings instead.

### Telepathy: **Last Order** (built, then dropped for Lights Out)

It only works well with another angry mob nearby to turn the foe on; alone, the foe just kneels.

- **Why it fits.** Puppet is the core move. In this finisher, the foe ends the fight for you.
- **Beats.**
  1. (0–8) Two fingers to your temple. Threads run from your head to theirs, and their eyes go pale.
  2. (8–20) The foe turns, walks to the nearest other hostile and strikes it. That blow is the overkill.
  3. (20–30) The foe kneels, drops and stays down. Its last surface thought appears in chat as Read Mind would show
     it ("...so hungry...", "...the captain will...", "...ssss...").
- **Twist.** With no other hostile within 8 blocks, the foe kneels to you instead, and its thought is "...sorry...".
  A player foe (where PvP is allowed) sees your view for the beat, as Puppet shows.
- **Reward.** Focus on every mind within 12 blocks, so your next Suggestion lands without a read.
- **Drawn with.** `TelepathyArt` threads and the halo, and Puppet's control for the walk (the move ends early if the
  path is blocked).

### Space-Time Manipulation: **Closed Loop**

- **Why it fits.** The traveller meets themselves. The mod already draws a player's double (`Role.TRAVELER`,
  `doubleOf`, the `TimeVeil` stand-in).
- **Beats.**
  1. (0–6) You fold out: gone in a violet blink.
  2. (6–10) Two of you fold in at once, one on each side of the foe: you, and yourself from two seconds from now,
     with a faint amber clock at the shoulder.
  3. (10–16) You both strike at the same moment.
  4. (16–28) The later you nods, checks a watch and folds back into the past to become the first you. A clock face
     behind the foe stops spinning. You are left standing alone.
- **Twist.** During a Still World or Between Seconds there are three of you. With Between Seconds, the third comes
  from the past, its clock running backward.
- **Reward.** Fold Space is ready again.
- **Drawn with.** The client-side stand-in in your own skin, `TimeArt.face`, the fold's blink, and a new
  watch-check pose for the double.

### Density (Phasing): **Six Feet**

- **Why it fits.** Sink, Grasp from Below, Uproot and Entombed in one string: the combos the tree teaches, played
  out as a single move.
- **Beats.**
  1. (0–6) You go Ghost and sink into the floor, leaving ripples.
  2. (6–14) Stone fingers rise round the foe's ankles and pull it in to the waist.
  3. (14–22) You burst up behind it at Full Dense, stone plates on your forearms, and bring a two-fisted hammer
     down on its head.
  4. (22–30) It is driven under. Slabs fold over the spot like a closing hand and sink flat, leaving only a ring of
     crumbs.
- **Twist.** If your dial is at Dense when you start, the hammer splits the ground in a star (Bedrock Slam's look)
  and roots every foe within 4 blocks. No real blocks break.
- **Reward.** Your Ambush hand is primed.
- **Drawn with.** `DensityArt` ripples, Grasp from Below's fingers, `slab`, Entombed's closing slabs, and Harden's
  forearm plates. The foe is moved with `DensityService`'s embed and hidden under the surface
  (`surfaceOf` places the slabs).

### Invisibility (Force Fields): **Hall of Mirrors**

- **Why it fits.** Light bent and made solid: the foe can't tell which of you is real.
- **Beats.**
  1. (0–4) You fade.
  2. (4–12) Six panes rise round the foe in a ring, each showing a refracted you.
  3. (12–20) The foe turns from pane to pane while each reflection strikes the air.
  4. (20–28) The real you steps out of the pane behind it with a Force Palm. All six panes shatter inward into
     tumbling facets.
- **Twist.** Friends see the panes as clear glass and see where you really are. With Unseen Constructs learned,
  other foes don't see the panes at all, only the shards.
- **Reward.** 3 seconds of Fade afterwards, free, to get away.
- **Drawn with.** `ForceArt.pane` and `facet`. Each reflection is a snapshot of your skin and pose (as
  `SpeedAfterimages` takes) drawn behind its pane.

### Regeneration: **Paid in Full**

- **Why it fits.** Claire takes the hit on purpose. Unlike the Reckless Brawler's Headbutt, this is about what she
  has suffered, not about brawling.
- **Beats.**
  1. (0–6) You open your arms. The foe's last blow lands on you, plainly.
  2. (6–14) You look down at the wound and back up. It closes from the bone out, as a limb grows back.
  3. (14–22) You grab the foe and land one punch. A red ring runs down your arm into them: every wound you healed in
     the last ten seconds.
  4. (22–26) The foe drops. Your knuckles split and close again.
- **Twist.** The punch adds a quarter of the damage you took in the last ten seconds (up to 20) to the overkill.
  "Severed limbs: Fade only" also removes the blood here.
- **Reward.** +30 Regrowth.
- **Drawn with.** `RegenArt` blood in the soft pass and `gash` lines closing, and the regrowth look from severed
  limbs on the wound. The scripted blow costs 2 health, never more.

### Induced Radioactivity: **Chain Reaction**

- **Why it fits.** Fission: one bead of light splits until it can't be held.
- **Beats.**
  1. (0–4) You flick a bead of white light into the foe's chest.
  2. (4–14) It splits inside them into two, four, eight, then sixteen motes, visible as the body glows from within.
  3. (14–18) A contained flash. The foe's outline holds for a moment, drawn in light.
  4. (18–24) The spare motes fly to the nearest foes (the overkill) and split again on each in smaller flashes.
- **Twist.** During a Meltdown warning, finishing vents 40 Core, which is enough to stop the warning. A low foe
  becomes an emergency exit.
- **Reward.** −25 Core.
- **Drawn with.** `RadiationArt` motes and flash at small size, with the split timed on powers of two. It leaves
  no `RAD_ZONE` (Set A's Half-Life does).

### Vibe: **Off Frequency**

- **Why it fits.** Cisco shakes things out of tune with this world.
- **Beats.**
  1. (0–6) Your palms go either side of the foe, not touching, with wave fronts running between them.
  2. (6–14) The foe splits into three offset copies, tinted blue, red and green like a bad signal, buzzing.
  3. (14–20) The copies drift apart.
  4. (20–24) You snap your fingers, and they blink out one at a time, each with a breach's pop. Nothing is left.
- **Twist.** Foes marked by Residual Sense or Return Address each have one of the copies flung into them (the
  overkill).
- **Reward.** Breach Guard is ready again.
- **Drawn with.** Three client-side copies of the foe's model with tinted overlays, and `VibeArt`'s seam pop for
  each blink.

### Technopathy: **Hard Reset**

- **Why it fits.** The finisher is as big as the circuit you built: setup play pays off.
- **Beats.**
  1. (0–6) You put a palm on the nearest metal or redstone. If you have a circuit, it lights.
  2. (6–18) A ripple runs through every enrolled control within 16 blocks toward the foe: lamps flicker, doors and
     trapdoors bang, levers flip. These are real block states, put back after 2 seconds.
  3. (18–24) The last node fires a Tripwire line taut through the foe.
  4. (24–30) Every lamp goes out at once, then comes back on. The foe is down.
- **Twist.** With no circuit, the line runs from your own hand, short and plain. With Domino Engine learned, the
  ripple is a real recoil running node to node, and it hits other foes along the way.
- **Reward.** Your circuit's enrolment is renewed for two minutes.
- **Drawn with.** Remote Hand's rules (enrolled controls only, only where you may edit) and the restore pattern from
  `CompanyOmens` (put back only if the block is still the same kind). The Tripwire line and lit block edges draw it.

### Matter Assembly: **Monument**

- **Why it fits.** Matter folds into shapes. This one is a statue of the foe, and then it's material again.
- **Beats.**
  1. (0–8) Plates tear up all round and fly onto the foe, joining at bright seams and freezing it in its last pose.
  2. (8–16) A plinth rises under it and lifts the stone figure half a block.
  3. (16–24) It holds: a statue of the foe on a plinth.
  4. (24–30) It crumbles from the top down into dust on the wind, and three Shards spin out of the dust to orbit you.
- **Reward.** The three Shards are free Shard Lances for 10 seconds.
- **Drawn with.** The Compression Cage's assembly (plates flying in with seams behind them), and the foe's model
  drawn in a stone tint as a client-side stand-in. Nothing is placed, so nobody else is trapped.

### Empathic Mimicry: **Take It In**

- **Why it fits.** Peter feels everyone. He takes in the foe's last moment.
- **Beats.**
  1. (0–8) Your hand goes to your heart, and gold threads sag between the foe and your chest.
  2. (8–16) The threads draw tight. For a moment your screen takes the foe's colour (red for a monster, their
     power's `MimicColors` colour for someone powered).
  3. (16–22) The threads snap straight, and a pulse runs into your chest.
  4. (22–26) The foe goes still, and you open your hand outward.
- **Twist.** A powered foe (a gifted villager, a player with a power, Sylar at his end) is imprinted at
  once: their power becomes a kept set, no touch needed.
- **Reward.** That kept set; otherwise a jump in attunement to any powered person within 10 blocks.
- **Drawn with.** Attunement's threads and the "taking a set" pulse from `MimicArt`, thinned near the camera.

### Shapeshifting: **Unmade**

- **Why it fits.** The shapeshifter takes a shape apart.
- **Beats.**
  1. (0–6) You become the foe for a moment, face to face like a mirror.
  2. (6–14) Your plates flip from slate to brass from the face outward, and you are yourself again. At the same
     moment the foe's plates start to flip the other way.
  3. (14–22) Its plates come loose one by one and fall, tumbling, with nothing underneath.
  4. (22–28) The last plate hits the ground and sinks away.
- **Twist.** Finishing in a shape the foe's kin accept raises no Suspicion: the others saw one of their own.
- **Reward.** The foe's form joins your library without studying it.
- **Drawn with.** `ShapeArt.hull` on both bodies (the morph's two fronts, run in opposite directions),
  `ShapeInstinctArt.loose` for the falling plates, and the morph's covered swap so the foe's body is hidden as the
  plates fall.

## Duets (later)

Destiny has nothing like this, but the mod is built on powers working together (speedster containment, Soaked,
steam). Two players with different powers who finish the same foe within half a second of each other could play a
shared ending instead of two. Four to start:

- **Live Water** (Hydrokinesis + Electrokinesis): the water sphere closes, and the bolt goes into it. The overkill
  chains through every Soaked foe in range.
- **Thermal Shock** (Pyrokinesis + Cryokinesis): fire from one side and frost from the other meet in the foe, which
  bursts in steam and shards.
- **Air Drop** (Flight + anyone): the flyer takes the foe up, and the other player's finisher plays in mid-air at
  the top.
- **Comet** (Telekinesis + Pyrokinesis): the foe is held up and set alight, then thrown down in a burning arc.

Duets need both players' finishers to start on the same tick, so the server waits up to 10 ticks after the first
press for a second one before playing a solo finisher.

## Drawing finishers with Photon (proposal)

Photon 2 (LowDragMC's VFX editor, with LDLib2 and KilaGraph) can author the parts of a finisher that the mod's
`Canvas` set pieces do poorly. Nothing here is decided: adding it is a dependency choice (see below).

### Does it fit this mod?

Yes, on versions.

| | Needs | This mod |
|---|---|---|
| Photon 2.2.11 (CurseForge file 9489406) | Minecraft 1.21.1, NeoForge 21.0+ | Minecraft 1.21.1, NeoForge 21.1.242 |
| LDLib2 2.2.46.a (file 9495128) | NeoForge 21.1.216+ | 21.1.242 |

- All three jars are already in the local Gradle cache, from the other mod project.
- That project already proves the workflow end to end (`ConvergenceFxLibrary`, `ConvergencePhoton`, its autotest).

### What Photon would draw, and what stays as it is

- **Photon is for:**
  - real particles with physics and collision (embers, snow, ice chips, dust, spray, shards, crumbs);
  - trails that follow a moving hand or foot;
  - beams between two points;
  - dynamic lights;
  - Kila materials (dissolve, refraction, fresnel rims);
  - post effects (a flash, an RGB split, a desaturation, a vignette pulse).
- **What stays as it is:**
  - Every gameplay rule stays on the server: the hold, damage, overkill, rewards and Hard Reset's block flips.
  - Poses and whole-body turns stay in `PowerArmPoses` and `FightingStylePoseRenderer`.
  - Model copies of the foe stay entity rendering: the statue, the double, the reflections and the colour-shifted
    copies.
  - The existing power primitives (`PyroArt.crescent`, `ElectroArt.sketch`, `TimeArt.face` and the rest) stay as
    they are, so each finisher keeps speaking its power's own shape language.
- **In short:** Photon adds the layer on top (the particles, light, materials and screen), and the code draws the
  shapes.

Where it would help most:

| Finisher | What Photon adds |
|---|---|
| Dragon Coil, Dragon's Descent | Ember particles shed by the coil, a trail on the kicking foot, a warm light that moves with the fire at night |
| Last Warmth, Shatterpoint | Snow and ice chips that fall and settle on the ground (collision), steam from the closing hand |
| Breaker, Deep Hold | Spray and foam that land, a fresnel rim on the water sphere |
| Late Thunder | The hit-stop flash and a short screen shock, for the finisher's player only |
| Short Circuit, Lightning Rod, Hard Reset, Take It In, Encore | Beams between confirmed endpoints, with a light at each end |
| Chain Reaction, Half-Life | The contained flash as a dynamic light, the X-ray glow as a material |
| Off Frequency | A real RGB split on screen for the beat, and a dissolve on each blink |
| Hall of Mirrors, Pressure Point | Refraction on the panes and the bubble |
| Monument, Unmade, Six Feet | A dissolve from the top down, dust that drifts on the wind, crumbs |
| Closed Loop, Ten Past Ten | A short desaturation while time is stopped, scoped to nearby viewers |

### The workflow

The editor is a GUI inside Minecraft, and the skill forbids desktop control, so Claude can't drive it. Instead:

1. **Authoring in code.** Claude writes each effect as Java in a client-only `client/fx/FinisherFxLibrary`, as
   `ConvergenceFxLibrary` does.
2. **Export.** A Gradle property makes a client run save each effect through Photon's own serializers. The
   editable `.fxproj` goes under `art/photon/finishers/`, and the runtime `.fx` under
   `assets/heroesevolved/fx/finishers/`. Nothing is hand-written NBT.
3. **Tuning by eye.** You open the `.fxproj` with `/photon_editor` and adjust it by eye: timing, colours, counts.
   Then you re-export.
4. **Playback and photographs.** Claude plays it from a client-only `client/fx/FinisherPhoton`, starting from the
   finisher's set piece. The usual preview driver (`tools/finisher-preview.init.gradle`) then photographs it from
   first person, third person and the side.

### The dependency choice

- **Required, client side.**
  - Photon, LDLib2 and KilaGraph go in `neoforge.mods.toml` as required client mods, as TaCZ and the voice mods are
    already required.
  - This is simplest: one art path.
  - Players must install three more mods. Check whether servers need LDLib2 (it declares `side = "BOTH"`) before
    shipping.
- **Optional.**
  - Every class that imports Photon is reached only behind a `ModList` check.
  - Without Photon, finishers draw only their `Canvas` shapes and lose the extra layer.
  - This needs a test run without Photon on the classpath, and some effects drawn twice.
- **Server side either way.** Photon imports must never load on a dedicated or game-test server. The game-test
  suite (`runGameTestServer`) must pass with no Photon code reached.

### Before using the skill here

The `photon-editor` skill lives in the other project and points at that project's files.

- It names the `placeholdermod` namespace, `.claude/skills/ability-animation-design` and `verification/decisions.md`.
  This mod's equivalents are `heroesevolved`, [animation-guide.md](animation-guide.md) and AGENTS.md.
- Copy it into `.claude/skills/photon-editor/` here, and rewrite `references/version-and-project.md` for this mod.
- Its inspector script needs Python 3.11 or newer. On this machine `python` is only the Windows Store stub. The jar
  metadata above was read straight from the jars, as the skill allows.

## Building the rest

The engine and a finisher for every power are done; the patterns for adding more are in AGENTS.md ("Reusable patterns
from finishers").

- **Done.**
  - `combat/Takedowns` (the engine) and `Takedowns.Finisher` (one object per finisher).
  - The payloads: `FinisherPayload` (the key), `FinisherStatePayload` (which finisher you have, whether it's on, PvP,
    reach) and `FinisherRunPayload` (start and end, for the camera).
  - `client/FinisherClient`: the glisten, the prompt, the key and the camera.
  - A `FINISH_*` piece per finisher in its power's art class (Technopathy's in the new `CircuitArt`), and
    `TakedownArt`'s live glints.
  - `BodyCopies`: a body drawn in stone, as tinted copies, sunk, greyed to ash, worn by someone else, or hidden, driven
    by the pieces bound to it (Monument, Off Frequency, Hall of Mirrors, Unmade, Unwound, Half-Life, Breach Drop, Heavy
    Hand, Echo, Its Own Face, Apex Bite).
  - Two alternates per power and the random pick.
  - Balance in `tools/balance.py`, tests in `TakedownGameTests`, and photographs with `tools/finisher-preview.init.gradle`.
- **Still to do.**
  - **A way to choose** (the Powers page row from the plan), if random turns out not to be enough.
  - **Bespoke synthesized sounds** per finisher.
  - **Pose overrides on the foe** (Cut Strings' dance on posed limbs) and **client hit-stop** (Late Thunder's freeze).
  - **Duets.**
