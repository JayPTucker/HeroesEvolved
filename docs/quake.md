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

| Key | Tier (Level) | Ability | What it does |
| --- | --- | --- | --- |
| G | 1 (Level 1) | **Shockwave** | A palm blast: a tube of pressure rings that hurls what it hits, turns arrows back on the archer and shatters glass. In midair, or aimed down, the recoil launches you the other way and cushions your next landing. |
| H | 2 (Level 3) | **Resonate** (new) | Find the frequency of the enemy you look at, within 16 blocks, and shake it from the inside for 3 seconds: (1.5 + tier/2) × Strain damage every half second, through any armor. Its armor shakes itself apart, losing durability every half second. A mob staggers about, can't keep its footing and loses its target; a player is left dizzy. |
| Z | 3 (Level 6) | **Fault Line** (new) | Punch the ground and a crack races out ahead of you for 10 + 2 × tier blocks. It erupts under every enemy on its way: (5 + tier) × Strain damage, and they are thrown up. A wall stops it, and so does a drop. **Aimed straight down, or used in midair**, it is the Seismic Slam ring instead, plunging first if you're in the air. |
| X | 4 (Level 10) | **Quake Blast** | Hold for a two-handed beam that drains stamina. Held on a wall, it cracks the blocks it hits until they break (see below). Jump or aim down and it carries you: Daisy's flight. |
| V | 5 (Level 15) | **Earthquake** | Shake the ground for seconds, then release an aftershock. Three fissures crack outward from you, and the softer ground along them gives way. Shift ends it early. |

**Passive Tremor Sense** (it used to take a key): crouch still for a second and you feel every heartbeat
within 20 blocks. Creatures glow through walls for you alone for 5 seconds, and a pulse marks each
heart, red where it is hostile or hurt. It can be used again after 6 seconds.

Vibration still cushions your landings, and a long fall ends in a small shock ring.

**Evolved trial:** "Push through it." Land 40 quake hits while at 80 Strain or more.

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

Dark strokes (cracks, soot) used to be invisible, because the ribbon renderer adds light and black
adds nothing. They are now drawn in a second, normally blended pass, which also brings back
Density's dark cracks.

## Sylar

Sylar's Quake now has two moves. The Ground Pound ring now ends in an eruption of rings and stone,
and his new **Shockwave** move is Daisy's palm blast: a ring tube, and you are thrown back hard. See
[Sylar](sylar.md).

## Technical notes

- `QuakeKit` holds Strain, Resonate, Fault Line and passive Tremor Sense. `QuakeService` keeps the
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
```

`QuakeKitGameTests` covers Strain and the fracture (including the lock on her powers), rest, Resonate
(damage through armor, armor wear, staggering), a Fault Line hitting enemies on its line but not one
beside it and being stopped by a wall, and the trial. `QuakeBlockBreakGameTests` covers a held Quake
Blast cracking a stone wall, then breaking it (with a drop) and boring on; letting go early breaking
nothing while the cracks fade; bedrock, a barrier and reinforced deepslate never cracking, nor stone
for a player who may not build; harder blocks cracking more slowly; and an Earthquake's fissures
breaking ground only on the fissures. The preview captures Shockwave, Resonate, the Fault Line racing
and erupting, 92 Strain, the fracture, Tremor Sense through a wall, Quake Blast flight, Quake Blast
cracking and breaking a stone wall and the cracks fading after release, and an Earthquake's fissures,
under `build/quake-preview/screenshots/`.
