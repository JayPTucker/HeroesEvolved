# Power Amplification

Power Amplification is "sidekick juice": red lightning that makes other people's powers far stronger. It is based on
Ando Masahashi from *Heroes*, whose power the [Heroes Wiki](https://heroes.fandom.com/wiki/Supercharging) calls
Supercharging, or red lightning. The kit builds new ideas from what he does in the show; it is not a copy. The code is in
`amplify/` (art in `client/effects/setpiece/AmpArt`, poses in `client/AmpPoses`).

## What the show gives us

| In the show | In the kit |
| --- | --- |
| He gets the power from the formula, and at first only shoots red sparks from his hand. | The Formula can grant it. The awakening is sparks with no control. |
| A zap makes Matt's telepathy "a thousand times" stronger: he hears every thought in New York and can't shut it out. | **Overcharge**: a power pushed this far does something new, with a cost the ally can't avoid. |
| Supercharged, Daphne runs so fast she goes a few seconds back in time. | An overcharged speedster runs three seconds into the past. |
| Rachel's teleport reaches Florida; she warns him not to overdo it "so we don't end up on the moon". | An overcharged breach **overshoots**. Partners learn to dial it in. |
| He boosts Hiro to carry dozens of people at once. | An overcharged fold takes every friend near. |
| In a possible future, his supercharge kills Hiro. | Overcharging the same ally again too soon hurts them. |
| He forms a ball of red energy between his hands, then throws it as a blast that knocks out a man in body armor. | **Red Lightning**: a ball grown between cupped hands, thrown as a bolt or a beam, through armor. |
| He knocks out a whole van of agents at once. | **Van Full**: a burst that is strongest in a closed space, and knocks out rather than kills. |
| He overloads electronic locks and blasts doors open. | **Jumpstart** and **Doorbuster**. |
| He shocks Hiro's scrambled mind back to normal. | **Shock Therapy** jolts an ally out of mind control and stuns. |
| A deleted scene has him wondering if he could make a plane fly faster. | **Jumpstart** works on machines and mounts. |
| He shows the sparks off, and passes them off as a holy "sign". | **Sparks for Show**: villagers are impressed, weak monsters think twice. |
| Drugged, his hands spark on their own. | Above 80 Charge the hands spark on their own. |
| Nobody knows if he could supercharge himself, or a passive power like Claire's healing. | He can't amplify himself. Passive powers *can* be amplified: an overcharged Regenerator heals the friends round them. |

## Charge

Charge is red energy held in the hands: a red ring on the HUD (0 to 100, a notch at 50). It drifts up to 25 on its own
(one a second), grows by 2 whenever a power is used within 16 blocks (4 for an ultimate), friend or foe, gifted mobs
included, and grows fastest while a ball is held for Red Lightning. With nothing happening near for ten seconds, it falls
back toward 25. Red Lightning, Van Full and an Overcharge spend it.

Above 80 the hands spark on their own: now and then a small arc jumps to the nearest creature. It is harmless, but it
gives you away.

## Core abilities

| Key | Unlocks | Ability | In play |
| --- | --- | --- | --- |
| 1 | Level 1 | **Supercharge** | Zap an ally within arm's reach (your partner from across a room, with Partner). A tap is a **Surge** (15 s). Held two seconds, it is an **Overcharge** (6 s on top of a Surge), which spends 50 Charge; without 50, a held press is a Surge. |
| 2 | Level 1 | **Red Lightning** | Hold to grow a ball between cupped hands; let go to throw it. A release under a second is a **bolt** (4 + tier, up to 3 more with Charge) at the first foe along the aim, 6 blocks at tier 1, 14 at tier 5. Held longer, a **beam** (6 + 1.5 x tier + Charge/10) through everything in a line, spending all the Charge. The ball lets go on its own after four seconds. It goes through armor, knocks down and stuns, and leaves a foe at half a heart rather than killing it. |
| 3 | Level 3 | **Shock Therapy** | A jolt to the ally you look at within 2.5 blocks, or yourself: clears confusion, slowness, blindness, darkness, weakness and mining fatigue, breaks a telepath's command or possession, calms fright and confusion from a shapeshifter, and stands a downed Regenerator straight up. |
| 4 | Level 6 | **Jumpstart** | Zap a block within five blocks: doors (iron too), trapdoors and gates swing, levers and buttons fire, furnaces, smokers and blast furnaces run four times as fast for 30 seconds, energy stores take 4000 FE. Riding, your mount runs twice as fast for ten seconds. |
| 5 | Level 15 | **A Thousand Times** | Both arms raised for two seconds (6 damage taken breaks it), then every ally within ten blocks is overcharged at once, with no backlash. Afterwards your Charge is gone and you can't Supercharge for 20 seconds. |

### A Surge

For 15 seconds, everything the ally hits hits 25% harder; their powers cost 25% less stamina and come off cooldown 25%
faster; their held powers (flights, Still World, beams, toggles) drain half as fast; and their own power gets its bigger
version (below). A pet or a friendly gifted villager can be surged too: it hits harder, and a gifted mob casts twice as
fast. Two amplifiers never stack on one ally: the Surge is refreshed, not doubled.

### An Overcharge

Six seconds of the ally's power doing something it can't normally do, then a backlash. Everything they hit hits half
again as hard while it lasts. Overcharging the same body again within 30 seconds hurts it (2 hearts, 4 on a third time).
An overcharged pet or gifted mob runs faster.

### Never yourself

Supercharge, Hold Hands and A Thousand Times never touch the amplifier, and an empath who copies the power can't
amplify themselves either. Shock Therapy and Jumpstart aren't amplification, so they work on you. An Ability Dampener
switches it all off, as it does every power.

## Every power, supercharged

| Power | Surge | Overcharge, then its backlash |
| --- | --- | --- |
| Superspeed | Momentum fills at once. | **Daphne's run:** a second of sprinting and you come out three seconds in the past, where you stood, with the health you had then. *Backlash:* the friction catches up (a burst of friction heat). |
| Space-Time Manipulation | Fold Space reaches twice as far. | **Brave New World:** the next fold takes every friend within eight blocks with you, in a ring round where you land. *Backlash:* everyone lands dazed. |
| Telepathy | Read Mind and every telepathic aim reach twice as far. | **A thousand voices:** every creature within 96 blocks is outlined in red through walls, for you alone. *Backlash:* your telepathy won't answer for four seconds. |
| Vibe | Breaches stay open twice as long and cost no distance stamina. | **To the moon:** a breach can reach any frequency in any world. *Backlash:* it overshoots, opening 12 to 30 blocks from where it should (unless nothing fits out there). |
| Flight | Skyburst is fully charged on the first press. | **The sound barrier, three times:** a Skyburst lasts three times as long. *Backlash:* your head spins. |
| Pyrokinesis | Your fire burns blue without the Heat for it, and Heat builds twice as fast. | **White fire:** burns hit half again as hard and soaking can't blunt them, and snow and ice melt round you. *Backlash:* it singes you. |
| Electrokinesis | Chain Lightning jumps once more. | **The storm:** once a second, a red-and-blue bolt falls from the sky on a foe within 12 blocks (six in all). *Backlash:* the last one finds you. |
| Hydrokinesis | Moves cost no water, and the sea's boost goes with you wherever you stand. | **From the air itself:** the reservoir refills every second, even in a desert. *Backlash:* you end up Soaked and slowed. |
| Cryokinesis | Freezing something solid lasts half again as long. | **Flash freeze:** every foe within six blocks freezes solid at once. *Backlash:* frostbite: frozen and slowed. |
| Quake | Strain builds half as fast. | **Aftershock:** no Strain at all, and every move hits as hard as at full Strain. *Backlash:* all the Strain comes due at once (it fills to 90). |
| Sonic Resonance | Resonance builds twice as fast, and every Cry leaves its targets Ringing. | **Glass-breaker:** Resonance stays full, and a Cry shatters glass, ice and soft stone where it lands. *Backlash:* your voice gives out for four seconds. |
| Telekinesis | A Grip holds twice as long. | **Everything at once:** every foe in front, within ten blocks, rises off the ground for the Overcharge. *Backlash:* a nosebleed while it holds. |
| Density | Stone Skin lasts twice as long. | **Shelter of ground:** friends within four blocks of you take no harm. *Backlash:* your legs turn to stone and drag (slowed). |
| Invisibility (force fields) | A Lens Shield holds twice the Pressure. | **A dome for everyone:** a dome six blocks across over where you stood; friends inside take no harm from outside it. *Backlash:* it takes all your stamina. |
| Regeneration | Rapid Mending heals half again as much. | **The passive answered:** your healing spills over to friends within four blocks. *Backlash:* your Regrowth is spent. |
| Induced Radioactivity | The Core fills half again as fast: amplifying a radioactive friend is a risk. | **The Core pours out:** every second, foes within eight blocks are dosed and ten Core vents. *Backlash:* 40 Core floods back. |
| Technopathy | Control reaches twice as far. | **The whole grid answers:** every furnace and battery within 32 blocks is jumpstarted. *Backlash:* a third of them stall. |
| Matter Assembly | Every Shard Lance volley fires one more shard. | **Shard orbit:** a shard flies at the nearest foe every half second. *Backlash:* you're worn out (weakness). |
| Empathic Mimicry | Attunement is instant. | **Free echoes:** your Echoes cost nothing and have no cooldown. *Backlash:* they go quiet for eight seconds. |
| Shapeshifting | Suspicion builds half as fast. | **Apex now:** you grow half again in size and hit half again as hard. *Backlash:* Shapeshifting won't answer for six seconds. |

## Skill tree

Three lanes of six. In each lane the 2nd and 5th are active skills and the 6th is the capstone.

### Sidekick (making a friend stronger)

1. **Close Friends.** A Surge runs at half speed, lasting about twice as long, while you stay within three blocks.
2. **Partner** *(active).* Bond with the ally you look at, within 16 blocks, until you choose another. You can
   Supercharge your partner from across a room (16 blocks, in sight).
3. **Not Too Much.** Your partner's backlashes are halved, overcharging them too soon hurts half as much, and their
   overcharged breaches never overshoot.
4. **Along for the Ride.** When your supercharged partner folds, breaches, dashes or teleports away while you stand
   within eight blocks, you go with them (to another world too).
5. **Hold Hands** *(active).* Everyone touching your partner, a chain of friends each within 2.5 blocks of the last (up to
   eight), shares their Surge for as long as it has left.
6. **Breakthrough** *(capstone).* A partner on your Surge can use moves one tier above what they've unlocked.

### Red Lightning (fighting alone)

1. **Bigger Ball.** The ball grows twice as fast once you hold 50 Charge, and bolts and beams reach four blocks farther.
2. **Van Full** *(active).* A burst that hits everyone within five blocks (6 + tier, up to 3 more with Charge), double
   where you're closed in (most rays from your eyes meet a block within five). It leaves foes at half a heart, stunned
   for five seconds.
3. **Opposite Current.** With 20 Charge or more, lightning aimed at you (an Electrokinetic's, Elle's, a storm's) is
   halved, and the half you don't take becomes Charge.
4. **Burn Through.** Red lightning breaks the constructs in its path (ice walls, force barriers and bridges) and hits a
   player behind a Lens Shield twice as hard.
5. **Overcharge the Enemy** *(active).* Pour red energy into a powered foe within 16 blocks. Its next move goes wild: the
   move is lost, the caster takes 4, and a gifted mob's power lashes its own side. Sylar and Elle are stalled instead.
6. **The Second Coming** *(capstone).* Stop holding back: red lightning, Van Full and Lightning Cannon hit twice as hard
   and kill, and a supercharged friend in a beam's path takes it too.

### Fixer (useful anywhere)

1. **Steady Hands.** Shock Therapy reaches four blocks, and clears a dampener's hold and fire.
2. **Burnout** *(active).* A powered foe within 12 blocks is dampened for four seconds. Aimed at an Ability Dampener's
   projector, it blows the breaker for a minute.
3. **Sparks for Show.** Crouch with an empty hand (and 10 Charge): sparks dance between your fingers. Villagers who see
   it trade more cheaply with you (once each per ten minutes); a creature hunting you with under 10 health hesitates.
4. **Mechanic.** Jumpstart lasts twice as long, and charges a TaCZ gun in your hand: its next 30 hits strike a fifth
   harder.
5. **Doorbuster** *(active).* Blast a door, gate or trapdoor within eight blocks off its hinges; whoever stands within
   2.5 blocks of it is knocked back and stunned for three seconds.
6. **Grounded** *(capstone).* A fifth of the damage powered foes deal you becomes Charge, and your hands never spark on
   their own.

## Classes

- **Got Your Back** (support). A dash to your partner's side, or the nearest friend, pet or friendly villager within
  16 blocks, and a Surge on arrival. With nobody to reach, a six-block dash ahead with your next red lightning at full
  strength.
- **Lightning Cannon** (offense). Two seconds with cupped hands, then a beam three blocks wide and 18 long (10 + 2 x tier)
  that knocks down everything in its path. While you have this class, every Supercharge you give arms your next red
  lightning at full strength.
- **Lights Back On** (defense). Shock Therapy on every friend within eight blocks (you too), and every furnace and battery
  there jumpstarted.

## Finishers: Sidekick Juice, Red Wave, Shock Therapy Reversed

Each press of C plays one at random, never the one you used last. All three give 40 Charge back.

- **Sidekick Juice** (signature). A palm flat on the foe's chest; red light pours in until it shines through, and the foe
  bursts in a flash.
- **Red Wave.** The ball between your cupped hands, then both hands forward: a beam that drives the foe back across the
  ground and drops it.
- **Shock Therapy, Reversed.** Two jolts at the temples; the foe stiffens, sparks run over it, and it falls.

## Awakening

The Formula can grant it. A natural awakening answers a fight or a blast. With someone else in danger, you grab their
shoulder and their power overcharges, with no backlash. Either way, red sparks burst out of both hands with no control,
throwing every threat back, and your Charge fills.

## Gifted mobs

An amplifier villager or illager is a support mini-boss. **Red Bolt** knocks a foe down; **Supercharge** surges every
gifted mob on its side within ten blocks (they hit harder and cast twice as fast), or bolts its target if it is alone; its
signature, **Red Storm**, is a burst that stuns everything near and overcharges the nearest of its side, which may
backlash. Take it down first.

## Look and feel

- **Palette:** shadow `2A0A10`, deep `8E1424`, red `E0263A`, hot `FF6A5A`, core `FFE6DE`. The red body of every stroke is
  drawn in the SOFT pass (normal blending, so it stays red at noon); only the hot rim and pale core glow.
- **One primitive:** a forked bolt (`AmpArt.fork`), used by every piece, plus a crackle that climbs a body, a ring and a
  ball. The ball grows live between an amplifier's cupped hands.
- **A supercharged body wears a red energy field,** like a charged creeper's blue one (`client/SuperchargeField`): the
  creeper's swirling texture, turned deep red on the client, scrolls over a copy of the body's model drawn a pixel
  larger. A Surge glows at a little over half strength; an Overcharge glows full and swirls twice as fast, with a ring at
  the feet that tightens as its six seconds run out. The field eases in when the boost lands and out as it ends. Players
  wear an inflated copy of their own model (wide or slim arms); pets and gifted mobs wear their own model a size larger;
  a shapeshifter's field follows the form they wear.
- **Poses** (third and first person): a hand thrust at an ally (zap), cupped hands (held), a throw, both hands forward for
  the beam, fingertips at the temples, a palm on a block, both arms raised (held), and open hands showing sparks (held).
- **Sound:** vanilla sounds for now (beacon hums, lightning cracks). Power Amplification has no synthesized cue palette in
  `PowerSounds` yet.
- **Icons:** the skill and key icons are designed in `tools/ability-icon-designs.json`, but the atlas hasn't been
  regenerated (it needs Pillow), so menus show the power's emblem (redstone) for them until it is.

## Testing and photographs

- `amplify/AmplificationGameTests`: a Surge's damage, cost, cooldown and drain; never yourself and no stacking; too-soon
  hurts and a backlash locks; a hold without Charge is a Surge; red lightning knocks out (and kills with The Second
  Coming); Shock Therapy and Steady Hands; Jumpstart opens an iron door; Burnout and a misfire; Van Full's closed room;
  A Thousand Times without backlash; Daphne's run; Brave New World; Hydro, Quake and Vibe's Tuned lines; Hold Hands and
  Breakthrough. The finisher tests run all three finishers.
- **Every power boosted by us.** One test per power, `boosted.<power>` (generated from `AmplificationGameTests.BOOSTED`,
  each in its own batch): the ally is Surged (a quarter more damage, cost and cooldown a quarter less, drains halved,
  aims twice as far, plus the power's own Surge line where a test can see it), then Overcharged (its Overcharge line),
  and once the Overcharge ends, its backlash. `everyPowerIsBoostedByTheTest` fails if a new power is missing from the list.
- **All of it in one command, about two minutes:**
  `./gradlew runGameTestServer --init-script tools/amp-gametest.init.gradle` runs the amplify tests, the skill, class and
  balance checks every power must pass, and the finisher tests. It ends in `All N required tests passed :)` and BUILD
  SUCCESSFUL, or names each failure.
- `./gradlew runClient --init-script tools/amp-preview.init.gradle` photographs the kit and every power overcharged
  (`amp-boost-<power>-t3/t30.png`) into `build/amp-preview/screenshots/`, then closes. `"-Ponly=boost"` shoots only the
  boosted powers; `"-Ponly=boost-quake,storm"` picks scenes by name. It ends in `AMP_PREVIEW_RESULT PASS` and BUILD
  SUCCESSFUL when every scene ran and every photo was saved. `tools/finisher-preview.init.gradle
  "-Ponly=power_amplification"` photographs the finishers.
