# Quake (Daisy Johnson)

Quake is based on Daisy "Quake" Johnson from *Agents of S.H.I.E.L.D.*: vibration, and what it costs
her body. Her powers make her arms ache, she learns to push through it, and if she pushes too far the
bones in her arms crack. The rework keeps the palm blast, the beam she flies on and the earthquake. It
adds **Strain**, two new abilities, passive Tremor Sense and a new look.

## Strain

Strain (0 to 100) is a bar under your ability keys. Every quake builds it:

| Power | Strain |
| --- | --- |
| Shockwave | +8 |
| Resonate | +12 |
| Fault Line (or Seismic Slam) | +16 |
| Quake Blast | +3 every half second held |
| Earthquake | +35 |

- **The harder you push, the harder you hit.** Every Quake power deals 1 + Strain/200 times its normal
  damage and force, so up to 50% more at the edge.
- **50, Pushing it:** the bar turns amber.
- **80, Breaking:** your forearms visibly shake and pressure leaks from your palms. The bar flickers
  red. Hits landed from here count toward the Evolved trial.
- **100, Fracture:** the bones in your arms crack. You take 3 damage, your powers lock for 8 seconds,
  you are left with Weakness II and Mining Fatigue II for 13 seconds, any beam or earthquake you're
  holding stops, and Strain drops to 40. The HUD says "Arms fractured" until they set.
- **Rest:** 2 seconds after your last quake, Strain falls by 2 a second, or 5 a second while
  crouching.

Running out of stamina (overexertion) still cracks your arms too, as before.

## Abilities

Every move has its own arm pose in third and first person, and three of them have a free second press.

| Key | Tier (Level) | Ability | What it does |
| --- | --- | --- | --- |
| G | 1 (Level 1) | **Shockwave** | A palm blast: a tube of pressure rings that hurls what it hits, **shakes bullets coming at you to dust**, **sets off explosives** it meets, turns arrows back on the archer and shatters glass (see below). In midair, or aimed down, the recoil launches you the other way and cushions your next landing. **Press again** within 0.7 seconds for **Double Tap**: the other palm answers with a lighter blast (60%, half the Strain). Free, once per Shockwave. |
| H | 2 (Level 3) | **Resonate** | Find the frequency of the enemy you look at, within 16 blocks, and shake it from the inside for 3 seconds: (1.5 + tier/2) × Strain damage every half second, through any armor. Its armor shakes itself apart, losing durability every half second. A mob staggers about, can't keep its footing and loses its target; a player is left dizzy. **Press again** for **Shatter**: the hand closes and the frequency collapses. Everything it had left lands at once, half again as hard (+2), its armor takes a heavy rattle and it is thrown away from you. Free. |
| Z | 3 (Level 6) | **Fault Line** | Punch the ground and a crack races out ahead of you for 10 + 2 × tier blocks. It erupts under every enemy on its way: (5 + tier) × Strain damage, and they are thrown up. A wall stops it, and so does a drop. **Press again** while it races for **Upheaval**: both palms sweep up and the rest of its line erupts at once, 80% as hard and throwing higher. Free. **Aimed straight down, or used in midair**, it is the Seismic Slam ring instead, plunging first if you're in the air. |
| X | 4 (Level 10) | **Quake Blast** | Hold for a two-handed beam that drains stamina. It **shakes bullets coming at you to dust every tick** and sets off explosives along it. Held on a wall, it cracks the blocks it hits until they break (see below). Jump or aim down and it carries you: Daisy's flight (palms thrust down and back). |
| V | 5 (Level 15) | **Earthquake** | Shake the ground for seconds, then release an aftershock. Three fissures crack outward from you, and the softer ground along them gives way. **Press again** (or Shift) to bring both fists down and release the aftershock early. |

**Passive Tremor Sense:** crouch still for a second and you feel every heartbeat within 20 blocks, a palm laid
on the ground. Creatures glow through walls for you alone for 5 seconds, and a pulse marks each heart, red where
it is hostile or hurt. It can be used again after 6 seconds.

Vibration still cushions your landings, and a long fall ends in a small shock ring.

**Evolved trial:** "Push through it." Land 40 quake hits while at 80 Strain or more.

## Bullets and explosives

Daisy shakes bullets apart in the air and sets off what was meant to blow up on her. Shockwave, Double Tap, the
Quake Blast beam and the Vibration Shell all do this:

- **A bullet coming at you** (any TaCZ round) shakes to dust where the wave meets it: a ring of warped air and a
  puff of grit. Bullets flying away from you, like a friend's shots, are left alone. The beam checks every tick
  and a little to either side, and it looks one tick ahead, so even fast rounds are caught.
- **Explosives go off where the wave meets them**, whichever way they're going: TaCZ explosive rounds (at their own
  radius), primed TNT and TNT minecarts (radius 4), a ghast's fireball and a wither skull (radius 1), and end
  crystals. The blast is credited to whoever fired it. A creeper's fuse is lit.
- **Other shots** (arrows, tridents, snowballs) are turned back the way the wave pushes and become yours.

## Skill tree

The tree is built around the kit rather than stat bonuses: every node changes a move or ties two together.
Node IDs are unchanged, so earlier purchases carry over.

### Faultwright (the ground)

| Node | What it does |
| --- | --- |
| **Live Fault** | A Fault Line (or Upheaval) that runs under an enemy you are Resonating breaks the resonance at once, with Shatter's blow, and throws them twice as high. |
| **Seismic Lance** (active) | An uppercut, and a moment later a column of earth bursts up under one enemy within 24 blocks (a ring marks its feet first): 7 + level, thrown straight up. An enemy already in the air is smashed back down instead, for double. |
| **Deep Roots** | Earthquake's fissures run half again as far, and Fault Lines climb one-block steps and cross deeper drops instead of stopping. |
| **Fault Script** (technique) | Trail a hand along the ground and write a twelve-step fault ahead of you. It glows brighter until it erupts two seconds later, hitting each enemy once. A Shockwave fired along it sets it off at once. |
| **Short Fuse** | Fault Script erupts after one second. |
| **Undertow** | The written fault returns for a second pass, dragging what it hits back toward you. |

### Counterforce (taking hits apart)

| Node | What it does |
| --- | --- |
| **Pressure Skin** | While you are braced, holding Quake Blast or inside your Vibration Shell, a shot or bullet that reaches you shakes apart on your skin instead (+4 Strain). |
| **Vibration Shell** (active) | Arms crossed, then flung out: the air round you shakes into a shell 3 blocks across for 4 seconds. Bullets that reach it shake apart, explosives go off outside it, other shots turn back, and enemies who push in are thrown out. |
| **Kinetic Sink** | Every bullet, shot or explosive your blasts and shell break, turn back or set off bleeds 3 Strain away. |
| **Counterforce** (technique) | Forearms crossed: brace for five seconds, absorbing half of incoming damage into a counterwave. Cast again to release it as a double-palm push, or **throw a Shockwave to pour it out through the blast**. |
| **Counter Balance** | Counterforce absorbs 70%. |
| **Stolen Thunder** | The counterwave can release up to 24, and stored force kicks you opposite your aim. |

### Seamfinder (precision)

| Node | What it does |
| --- | --- |
| **Sympathetic Frequency** | A Resonate that runs its full course jumps once to the nearest enemy within 6 blocks for another second and a half. |
| **Pressure Step** (active) | Palms flung back at the air behind you: you shoot about eight blocks along your aim, and a ring goes off where you left, hitting and shoving back anyone there. The landing is cushioned. |
| **Read the Pulse** | Every hostile heartbeat Tremor Sense lets you feel is marked for five seconds: your next quake on it lands 25% harder. |
| **Surgical Fracture** (technique) | A palm on a wall up to 24 blocks away opens a small seam, cracks running out from your hand. Hardness 2 or less; containers are spared. |
| **Wider Incision** | Surgical Fracture examines twelve blocks. |
| **Master Incision** | Surgical Fracture reaches hardness 4 and adds no fresh Sylar attention. |

The three styles keep their effects and have their own poses: **Fissure Maw** (fists driven down and pulled
apart), **Stone Rampart** (palms swept up) and **Bedrock Stance** (a wide, rooted stance).

## Breaking blocks

Quake's held powers grind blocks down slowly, with vanilla's mining cracks:

- **Quake Blast:** the block the beam hits cracks further the longer you hold it, until it breaks and
  drops what it would if mined. The blocks around the impact crack too, more slowly the farther they
  are from it. Hold on and the beam bores through and slowly widens the hole.
- **Earthquake:** three fissures run out from you, from 3 to 12 blocks away. Over the first half of the
  quake the cracks spread outward along them, and the ground on them breaks as it gives way. The ground
  next to you holds.
- **Letting go** (or the Earthquake ending) stops the cracking. Nothing breaks after that: the cracks
  hold for a moment, then fade out over about two seconds.
- **Harder blocks take longer.** At full strength a block takes 1 + 2 × hardness seconds: dirt 2,
  stone 4, deepslate 7. Obsidian takes over 100, longer than a beam can be held. Strain makes the
  beam hit harder, so it cracks faster too. A seam found by Seamfinder gives three times as fast.
- The beam spares unbreakable blocks, fluids, and the authored controls, supports, gates and pods of
  the relay station. The Odessa vision and Temporal Snapshot also protect their terrain.
- **The world's rules still apply.** Cracks only open where you could build (not in adventure mode,
  outside the world border or under spawn protection), and each break goes through the block-break
  event, so claim and protection mods can still refuse it. A refused block stops cracking. Blocks break
  through the normal level calls, so a time rewind puts them back.
- **Bounded:** one player's powers work at most 48 blocks a tick (the beam 9), keep at most 96 cracks
  open, and break at most 3 blocks a tick (12 across the server).
- The `breaksBlocks` config option (default on) turns this off.

## Look and feel

Daisy's vibrations are drawn as clear, shimmering **pressure rings**, the way the air warps around her
hands on screen:

- **Blasts:** a tube of rings opens out from the palm, each a moment after the last so you can see the
  wave travel. Two thin strands of shaken air run along it, and a ring pops where it lands.
- **Resonate:** a single trembling line from your hand, then rings shivering up and down the target's
  body.
- **Fault Line:** a dark jagged crack tears across the ground, with dust thrown up along it. Each
  enemy it reaches is caught in a burst of rings, stone and debris.
- **Strain:** at 80 and above your forearms shake; at the very edge they flash red.
- **Fracture:** red and white cracks run up both arms, with a red flash.
- **Heartbeats:** a double pulse at each heart you feel.
- **Bullets shaken apart:** a quick ring of warped air where the round was, and a puff of dust and grit drifting on.
- **Shatter:** rings squeeze in on the body, then break outward with dark cracks and grit.
- **Seismic Lance:** a column of rings and tumbling slabs bursting up, cracks spreading round its foot.
- **Vibration Shell:** a dome of faint, wobbling rings. **Counterforce:** rings drawn in to the crossed forearms,
  brighter the more is stored. **Pressure Step:** rings left hanging along the path. **Fault Script:** a hot seam in
  the ground that brightens until it answers. **Surgical Fracture:** a ring on the wall and cracks branching out
  from the palm.

The new pieces are drawn by `client/effects/setpiece/QuakeArt` from three primitives (`pressure`, `crack`, `grit`)
in the blasts' own palette, so they match the existing `QuakeEffects` rings.

Dark strokes (cracks, soot) used to be invisible, because the ribbon renderer adds light and black
adds nothing. They are now drawn in a second, normally blended pass, which also brings back
Density's dark cracks.

## Sylar

Sylar's Quake now has two moves. The Ground Pound ring now ends in an eruption of rings and stone,
and his new **Shockwave** move is Daisy's palm blast: a ring tube, and you are thrown back hard. See
[Sylar](sylar.md).

## Technical notes

- `QuakeKinetics` decides what a wave does to a bullet, explosive or other shot; `QuakeService.catchShots` runs it
  along a blast's line (and the beam every tick). TaCZ is reached only through `weapons/TaczLink`
  (`PowerWeapons.isBullet` and `bulletExplosion`, which reads the round's private explosion fields by reflection).
- `QuakeSkills` holds Seismic Lance, the Vibration Shell, Pressure Step and the passive hooks (Pressure Skin, Read the
  Pulse); `QuakeGeometry` holds the shared numbers and the palm anchor.
- `QuakeKit` holds Strain, Resonate (and Shatter), Fault Line (and Upheaval) and passive Tremor Sense. `QuakeService` keeps the
  blast, beam, slam, earthquake and debris, and now multiplies its damage by Strain.
- `QuakeBlockPressure` keeps each player's cracks. The beam and the Earthquake's fissures call
  `work` each tick for the blocks they hit; `tick` (run at the end of `QuakeService.tick`) breaks the
  blocks that gave way and fades the rest. `QuakeService.stopQuakeBlast` calls `release`, so the cracks
  fade at once; `stopAll` calls `clear`. The overlays use negative breaker ids, so they never collide
  with an entity's own mining cracks. The block-break event is posted only at the actual break, never to
  probe, because temporal echoes record every one.
- The HUD reads `StrainPayload` (Strain and whether your arms are fractured). The network protocol is
  now 44.
- Action ids: `quake_resonate` and `quake_fault_line` replace `quake_tremor_sense` and
  `quake_seismic_slam`.

## Verification

```bash
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
caffeinate -u -d -t 400 ./gradlew runClient --init-script tools/quake-preview.init.gradle
./gradlew runClient --init-script tools/quake-kit-preview.init.gradle
```

`QuakeReworkGameTests` covers bullets shaken to dust (and one flying away left alone), explosives set off (an explosive round, primed TNT, a creeper's fuse), the beam's every-tick screen, Double Tap, Shatter, Upheaval, the Vibration Shell, Seismic Lance, Pressure Step, Pressure Skin, Kinetic Sink and Live Fault. `QuakeKitGameTests` covers Strain and the fracture (including the lock on her powers), rest, Resonate
(damage through armor, armor wear, staggering), a Fault Line hitting enemies on its line but not one
beside it and being stopped by a wall, and the trial. `QuakeBlockBreakGameTests` covers a held Quake
Blast cracking a stone wall, then breaking it (with a drop) and boring on; letting go early breaking
nothing while the cracks fade; bedrock, a barrier and reinforced deepslate never cracking, nor stone
for a player who may not build; harder blocks cracking more slowly; and an Earthquake's fissures
breaking ground only on the fissures. The preview captures Shockwave, Resonate, the Fault Line racing
and erupting, 92 Strain, the fracture, Tremor Sense through a wall, Quake Blast flight, Quake Blast
cracking and breaking a stone wall and the cracks fading after release, and an Earthquake's fissures,
under `build/quake-preview/screenshots/`. The kit preview photographs every move, follow-up, skill and style (behind, in front and first person) under `build/quake-kit-preview/screenshots/`.
