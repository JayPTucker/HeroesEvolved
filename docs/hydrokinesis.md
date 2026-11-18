# Hydrokinesis

See [ranged attack distances](projectile-ranges.md) for the current projectile and beam ranges.

Water bent the way the sea's own children bend it in the old myths, and fought with the flowing forms of the Water
Tribes' benders: whips and waves, an armour of arms, a ride on a cresting wave, a waterspout you can steer. Water heals
you, carries you and makes you stronger; without it, you pull what you can out of the air.

## Passive: the water you carry

- You **carry water**, seen as a stream flowing round your waist: a short arc when you have little, a full loop and a
  second, tilted strand when you are full. Up to 100 (150 with Water Skin).
- It **fills** quickly while you stand or swim in water (10 a second), more slowly in rain (5 a second) and a little
  when open water is close by.
- **No water in the Nether.** The heat boils water off before it can gather, so Hydrokinesis does nothing there. Every
  move, waterbending strike, dodge and finisher **fizzles** instead (a hiss and a puff of steam from the palms, free:
  no stamina, no cooldown), held moves (Octopus Form, Wave Rider, Maelstrom, jets, spouts) end, and the water you
  carry boils away the moment you arrive. Steam keeps rising off you while you stay. Back in the Overworld or the End
  the reservoir fills as usual.
- Every move **spends some**. With too little carried, the move pulls its water thin out of the air instead: it is
  drawn paler and thinner, hits 30% softer, and the effort costs a little stamina. Standing in water you never run out.
- **The sea strengthens its own.** In water every move hits 25% harder, you heal 0.75 health a second and your
  stamina recovers 2 a second faster; you also breathe underwater and swim with Dolphin's Grace. In rain every move
  hits 10% harder, you heal 0.5 a second and stamina recovers 1 a second faster. Sea's Blessing doubles the healing.
- **Soaked.** Your water drenches what it hits (a status with a drop icon, six seconds). A soaked creature can't stay
  on fire, takes **half again as much from lightning** (Electrokinesis), turns **part of every burn into steam**
  (Pyrokinesis: the burn is blunted and doesn't catch, and the soaking boils off), and can be frozen solid by Ice
  Daggers with Brine Edge. A soaked pyrokinetic loses their Heat.

## Actions

| Key | Level | Action | What happens | Stamina / cooldown |
| --- | --- | --- | --- | --- |
| G | 1 | **Water Whip** | A whip of water cracks out from your hand along your aim, up to 10 blocks: 4 + level damage to the first enemy, who is driven back and soaked. Missing, it splashes out fires where it lands. **Shift** snares instead: the whip coils round the first enemy's waist and hauls them to you. | 10 / 0.7 s |
| H | 1 | **Crashing Wave** | You heave a wave up out of the ground and roll it 32 blocks forward, following the ground. It carries away whatever it catches (5 + level), swallows arrows, puts out fires and hardens lava (where you may build). | 26 / 6 s |
| Z | 2 | **Octopus Form** | Water wraps round you in a clear shell with eight arms. For up to ten seconds (water drains while it lasts) two arms lash at the nearest enemies within 5 blocks every 0.4 s, shots that come near are swallowed, and blows against you lose a third. **Press again** to throw every arm out at once (6 + level, everyone within 6 blocks hurled away); **Shift** to let it fall. | 30 / 14 s |
| X | 3 | **Wave Rider** | A cresting wave rises under your feet and you surf it for six seconds, steering with your look: faster on water, climbing single steps, knocking enemies in the way aside and soaking them. **Press again** to leap off the crest; the landing is cushioned and splashes everyone near (4 + level / 2). **Shift** steps off. | 22 / 8 s |
| V | 5 | **Maelstrom** | A whirlpool opens where you look (up to 28 blocks) and, after a second and a half, rises as a **waterspout** for eight seconds. It drifts toward wherever you look, drags enemies in, whirls them up its wall and drowns them. When it ends, or when you press **Shift**, it comes down as a **Deluge**: everything it held is slammed to the ground (8 + level) and a ring of water runs out. | 95 / 75 s |

## Waterbending: the fighting style

With an empty hand, a hydrokinetic fights with the water they carry, built on Pyrokinesis's firebending (see
[pyrokinesis.md](pyrokinesis.md#firebending-fists-feet-and-dodges)). Clicking a block still digs it; holding an item
fights the ordinary way. Each strike takes a little water; with none, the water is pulled thin and hits softer.

### Strikes (left-click with an empty hand)

| Strike | Level | What happens | Stamina |
| --- | --- | --- | --- |
| **Lash** | 1 | The lead hand flicks a lash of water up from the hip and out along your aim, 7 blocks: 2 + 0.4 × level damage, and it soaks. | 2 |
| **Backhand** | 1 | The rear hand sweeps a lash across from the far shoulder. Lashes alternate while you keep clicking. | 2 |
| **Ice Daggers** | 2 | The third click of a combo. Both hands draw back and thrust out, freezing the water into five daggers thrown in a fan, 40 blocks: 1.5 + 0.3 × level each, and they slow. (Three daggers when pulled from the air.) | 5 |
| **Water Wheel** | 2 | **Crouch and click.** A ring of water whirled round your waist in one full turn, 4 blocks: everyone in reach is knocked away and soaked, and nearby fires go out. | 5 |

### Dodges (double-tap a direction)

| Dodge | Level | What happens | Stamina |
| --- | --- | --- | --- |
| **Flow Step** | 1 | **Double-tap left or right.** You skate about 5 blocks sideways on a sheet of water, low and fast, throwing a fan of spray. | 8 |
| **Geyser Vault** | 2 | **Double-tap back.** A geyser bursts up where you stand and throws you up and over backward. Anyone on it or just in front is launched with the column (3 + 0.6 × level). At level 1, back is a plain Flow Step. | 11 |

- **Evasion:** for the first 8 ticks of a dodge, blows from creatures, players and projectiles miss you. Your landing is
  cushioned for two seconds.
- **Turn the Tide:** the first blow you evade gives you a little water and sets water swirling in your rear hand. Your
  next click within two seconds is a free backhand that snares the one who threw it, wherever they are within 16
  blocks, and hauls them in at 60% more damage.

The controls can be switched off with `fightingStyleStrikes` and `fightingStyleDodges` in the client config (shared with
the other fighting styles). Every number is under `hydrokinesis` in the common config.

## Look and feel

Hydrokinesis has one visual idea: **living water**. Everything is glassy, sun-lit water, deep sea-blue in its body and
teal where it thins, with a white foam lip wherever it curls or breaks and spray thrown in arcs that fall. It is drawn as
water, not light: only thin glints sparkle.

| Colour | Role |
| --- | --- |
| `0B3A5C` deep | depths, the whirlpool's eye, shadow |
| `1F78B4` sea | the body of the water |
| `3CC2D6` teal | thin water, ripples |
| `9EE8F2` shallow | glints, healing glow |
| `F2FBFF` foam | foam lips, spray, mist |
| `CDEFFF` ice | Ice Daggers |

- **Water Whip** unrolls from the hand in an arc, a pulse of water running ahead to the tip, straightens as it cracks
  (a flash and a little crown of splash at the tip), then snaps back into the hand. A snare cinches a coil round its
  catch with a taut line back to you.
- **Crashing Wave** is a curling sheet that rises from the ground behind, crests and throws its lip forward in a line of
  foam. Its edges trail behind its middle, it climbs and drops with the ground, a wet sheen spreads behind it, spray
  tears off the crest, and at the end it collapses forward into foam.
- **Octopus Form** is a clear, rippling shell with eight arms reaching out of it, each curling and swaying on its own
  beat. Lashing arms shoot out to their targets and recoil with a splash. In your own first-person view the shell is
  left out (you are inside it) and you see the arms reaching round you.
- **Wave Rider** carries you on a small curling wave whose lip rises behind your heels, spray fans off its edges and a
  white wake trails behind. You stand sideways like a surfer, leaning into turns.
- **Maelstrom** opens with foam spirals winding faster and faster into a darkening eye, rings closing on it as a warning.
  The waterspout is the same tornado Superspeed and Flight raise, in water's colours: a funnel of spinning sea-blue
  water and white spray, foam bands spiralling up its wall, a crown of spray at its foot. The Deluge drops a column of
  water and runs a breaking ring out over the ground.
- **The carried water** flows round your waist all the time, longer and thicker the more you carry.
- **Waterbending** keeps the same shapes small: lashes bow out to their hand's side, daggers are spinning blades of ice
  that shatter into glints, the wheel is a ring with a foaming leading edge flung outward at the end of its turn, a
  Flow Step leaves a wet sheet along the ground, and a vault is a geyser column with a crown of spray.

The body moves with the water. Every gesture swings from the shoulder in an arc and follows through, never a straight
jab. The whip arm is drawn up over the shoulder and cracked down while the body winds away and turns through. A heave
scoops low, leans back, then drives forward. The octopus holds the arms out, turning slow circles with the arms of
water. Lashes flick up from the hip, daggers draw back to the shoulders and thrust, the wheel spins the whole body once
on a low stance, and the vault is a backflip. Calls raise both arms and circle them overhead.

## Skill tree (18 nodes)

**Tides** (offense)

| Node | Level | What it does |
| --- | --- | --- |
| Undertow | 1 | Water Whip's crack drags its target a step toward you instead of away, and every whip hits Soaked enemies 25% harder. |
| **Geyser** (active) | 2 | Mark a spot up to 22 blocks away; rings close in on it and bubbles break the surface, then a column of water bursts up, throwing everyone on it skyward (6 + level). A Crashing Wave that rolls over the mark sets it off at once. |
| Riptide | 2 | Crashing Wave carries everything it catches all the way to the end of its run and slams them down there. |
| Brine Edge | 3 | Ice Daggers freeze Soaked enemies solid for a second, and a lash that strikes a frozen enemy hits twice as hard. |
| **Pressure Jet** (active) | 4 | A thin, hard jet of water from both palms for two seconds, braced and trembling: it hurts and shoves whatever it touches and puts out fire. Shift stops it. |
| Tsunami Heart | 5 | Standing in water, your Crashing Wave rises twice as wide and tall, and Maelstrom's Deluge sends a wave rolling out in every direction. |

**Currents** (movement and defense)

| Node | Level | What it does |
| --- | --- | --- |
| Slipstream | 1 | Flow Steps leave a slick of water for three seconds that slows and soaks enemies on it, and your next lash reaches half again as far. |
| **Tide Wall** (active) | 2 | A bowed wall of standing water two blocks ahead for five seconds, foam along its top, water running down its face. It swallows shots, soaks and slows enemies who walk through it, and a Water Whip cast through it is twice as thick and reaches farther. |
| Child of the Sea | 2 | See clearly underwater, Octopus Form's arms reach twice as far in water, and Wave Rider runs half again as fast and twice as long on water. |
| Return Current | 3 | Shots your Octopus Form, Tide Wall or Tidal Dome swallow are flung back at whoever fired them. |
| **Water Spout** (active) | 4 | Ride a spiralling pillar of water up to eight blocks high for six seconds, drifting where you look and knocking enemies from its foot. Press again to crash down in a splash (5 + level). |
| Endless Tide | 5 | Wave Rider refills your water as you ride, and leaping off its crest lands as a Crashing Wave rolling on the way you rode. |

**Healing** (support)

| Node | Level | What it does |
| --- | --- | --- |
| Sea's Blessing | 1 | Water and rain heal you twice as fast, and each second in water washes away one harmful effect. |
| **Healing Water** (active) | 2 | Water glows in your cupped hands, arcs to a friend you aim at within six blocks (or stays with you) and spirals up their body: 6 + level healing over three seconds, and one harmful effect washed away at once. |
| Water Skin | 2 | You carry half again as much water (150), and come back from death with it full. |
| Soothing Tide | 3 | Healing Water spreads to every friend within four blocks of the one you heal, at half strength. |
| **Rain Call** (active) | 4 | A dark cloud gathers over you for twenty seconds, ten blocks across, raining: friends under it heal and stop burning, enemies are Soaked, fires go out and your water fills. |
| Spirit Water | 5 | Once every five minutes, with at least 30 water carried, a killing blow is turned aside: the water closes your wounds, leaving you at 8 health, and is spent. |

### Classes

| Class ability | Role | What it does |
| --- | --- | --- |
| **Tsunami** | Offense | A great wave nine blocks wide rolls 48 blocks ahead, carrying everything before it (10 + 2 × level). |
| **Tidal Dome** | Defense | A dome of clear water over you for six seconds, ripples running round it and foam at its foot. It swallows shots from outside; friends inside gain Resistance and enemies inside are slowed and Soaked. |
| **Healing Spring** | Support | A clear pool wells up at your feet with a little fountain, light lifting off it, for eight seconds: friends standing in it heal and lose their fire, poison and wither. |

## Awakening

Hydrokinesis wakes when you or a friend nearby is in danger, like the other powers. A whirlpool opens at your feet as
the water gathers; then three streams of water spiral up round you, a bubble of water closes over the one you are
saving, and the water is flung outward, throwing back everyone who was on you and drenching them. You wake with your
water full.

**Evolution trial:** carry 60 enemies away on your waves.

## Finishers: Breaker, Drowning Bubble, Riptide

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md).

- **What you see.** A wave rises under your feet and lifts you where you stand, about two and a half blocks up. It curls over with you on its crest and throws its lip out in an arc onto the foe, carrying you one block forward and setting you down. It breaks into a ring of foam that runs out over the ground, with a burst of spray. You never leave your spot by more than a block and a half, and the rise and fall are smooth (your own client moves you).
- **In water or rain** the wave is nearly twice the size and its foam throws other foes harder.
- **Reward:** your reservoir is refilled.

Each press of C plays one of three at random, never the one you used last. Breaker is described above; the two alternates give the same reward.

- **Drowning Bubble.** A ball of water is drawn up from your hand and closes over the foe's head; bubbles stream up through it while it struggles. It drops, and the water bursts away in a splash.
- **Riptide.** A water whip wraps the foe's waist and swings it once round you through the air on the end of it, trailing spray, and slams it into the ground where it stood. A wall in the way stops the swing.

## Verification

- Game tests: `HydroGameTests` (the reservoir, every core move, waterbending, the tree's actives, the classes and
  Spirit Water) and `ElectroCombatGameTests.lightningHitsTheSoakedHarder`.
- Photographs: `./gradlew runClient --init-script tools/hydro-preview.init.gradle` saves every move into
  `build/hydro-preview/screenshots/`.
