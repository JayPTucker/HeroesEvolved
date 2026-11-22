# Electrokinesis

See [ranged attack distances](projectile-ranges.md) for the current projectile and beam ranges.

Character source: [Elle Bishop](https://heroes.fandom.com/wiki/Elle_Bishop) and
[Electric Manipulation](https://heroes.fandom.com/wiki/Electric_Manipulation). Elle's blue, crackling
current is the look; her weakness to water is the drawback. Numbers are configurable starting values.

## Passive

- **Charge (0–100):** static builds in you as you fight. Each shock adds 3 (2 per Live Current
  pulse), sprinting adds 2 a second, and standing in a thunderstorm adds 3 a second. It starts
  draining eight seconds after you stop.
  - **Charged (50+):** your body crackles with visible arcs, Chain Lightning leaps one extra time,
    and Static Touch hits 2 harder.
  - **Overcharged (100):** your next power is **empowered** and spends the whole Charge (see the table).
- **Lightning feeds you.** Lightning never hurts you: it restores 30 stamina and fills your Charge.
  Thunderstorms also speed up stamina regeneration.
- **Static Touch (level 2):** each punch you can pay 4 stamina for shocks and briefly stuns its target.
- **Water turns on you.** Stepping into water with 20+ Charge dumps all of it at once: it shocks
  enemies in the water around you, and you. Using power while in water also hurts you.
- **Machines:** a bolt or stream that hits a **lightning rod** makes it give off a redstone pulse, and
  one that hits a **copper bulb** toggles it, just as a real strike would. Lightning that lands on anything
  storing **Forge Energy** (a Mekanism cube, a Thermal cell, any FE/RF machine) charges it: Chain Lightning
  4,000 FE, Live Current 10,000 FE a second, Thunderstrike 40,000 FE, and Storm Surge arcs 20,000 FE into
  every machine around you. See [Powers and machines](machines.md).

## Actions

| Key | Level | Action | Behavior | Overcharged | Base stamina / cooldown |
| --- | --- | --- | --- | --- | --- |
| G | 1 | Chain Lightning | **Tap** to fork a bolt through foes or prime your own Ball Lightning. **Hold** to use Live Current for up to 4 seconds at 15 stamina per second, locking the target in place. | Two extra leaps and 40% more damage on a tap. Live Current deals 25% more damage. | 18 / 1.25 s |
| H | 2 | Ball Lightning | Send an orb that curves toward exposed foes, lashes them in flight and bursts on contact. **Shift + H** bursts it early. Hit it with Chain Lightning to stop it in place and trigger a wider burst after a brief warning. | Lashes two foes and deals a larger burst. | 25 / 3 s |
| Z | 3 | Thunderstrike | Mark an impact with a warning ring and rising streamers for 0.8 seconds. Creepers charge and pigs transform on exposed impacts. Striking water shocks exposed foes in water within 8 blocks. | Three separately marked strikes. | 45 / 10 s |
| X | 4 | Ride the Lightning | Blink in the direction you face, clear short ledges and shock foes close to the route. The ground you crossed stays live for 3 seconds. | 60% farther. | 30 / 5 s |
| V | 5 | Storm Surge | Lift off and gather charge for 1.5 seconds inside a marked perimeter, then release through exposed foes within 10 blocks or 16 in a thunderstorm. Spending 10 Charge adds 1 damage. **Shift + V** cancels the wind-up. | Spends all Charge on release. | 110 / 120 s |

Overexerting leaves you slowed and lashes exposed foes nearby. Solid cover blocks combat arcs.

## Fighting style: lightning

With an empty hand, Elle fights up close with the current in her fists, the way a firebender fights with fire
(see [pyrokinesis.md](pyrokinesis.md#firebending-fists-feet-and-dodges)). Clicking a block still digs it; holding an
item fights the ordinary way.

### Strikes (left-click with an empty hand)

| Strike | Level | What happens | Stamina |
| --- | --- | --- | --- |
| **Spark Jab** | 1 | The lead fist snaps out and a spark leaps from the knuckles. Lightning finds the nearest body within about a block of your aim, out to 12 blocks: 0.8 + 0.2 × level damage (+1 when Charged), and a jolt. With nothing to hit, it cracks against a block or dies in the air. | 2 |
| **Spark Cross** | 1 | The same from the rear fist. Sparks alternate jab, cross, jab while you keep clicking. | 2 |
| **Thunderclap** | 2 | The third click of a combo. Both palms swing in and slam together in front of your chest: a fan of forks blows across a 6-block cone, 0.8 + 0.2 × level damage, throwing foes back and locking their legs for 1.5 s. **At full Charge** it spends it all: 9 blocks and 60% more damage. | 5 |
| **Grounding Strike** | 2 | **Crouch and click.** You drop to one knee and drive your palm into the floor: a ring of current runs out 4.5 blocks along the ground, all round you, jolting and slowing everyone on it. | 5 |

- Every hit feeds Charge like the rest of the kit.
- Fighting with wet hands still sends the current back through you. A target that is wet, or **Soaked** by a
  hydrokinetic, takes half again as much.
- Out of stamina, the click lands as a plain punch on whatever you were aiming at.

### Dodges (double-tap a direction)

| Dodge | Level | What happens | Stamina |
| --- | --- | --- | --- |
| **Arc Step** | 1 | **Double-tap left or right.** You flash about 5 blocks sideways as a bolt. A full-size electrical facade of your body hangs where you stood for a moment, joined to you by a flickering arc. | 8 |
| **Flashback** | 2 | **Double-tap back.** You snap 6 blocks backward and leave a charge of static where you stood: your charged electrical facade crackles harder as a warning ring closes in on it, then bursts 0.6 s later, jolting and freezing anyone standing in it. At level 1, back is a plain Arc Step. | 11 |

- **Evasion:** for the first 8 ticks of a dodge, blows from creatures, players and projectiles miss you. Your landing is
  cushioned for a moment after.
- **Ground Out:** the first blow you evade gives 15 Charge and grounds the attacker. A corona snaps round you and your
  rear fist crackles; your next click within two seconds is a free cross whose spark runs through that attacker,
  wherever they are within 16 blocks, at 60% more damage, then leaps on to up to three foes near them.

### How it looks

Every move is built from the kit's own lightning (indigo halo, blue channel, white core):

- **Sparks** flash a corona at the knuckles, race out in two ticks and keep flickering with return strokes. A ring blows
  back off a body they hit; a miss splits and dies in the air.
- **Thunderclap** brings two small coronas together at the palms, a white flash and a ring blown off the hands, then a
  fan of forks opening across the cone with a jagged leading edge.
- **Grounding Strike** drives a bolt down through the palm, burns a dark scorch star into the floor and sends the
  ground pulse out.
- **Arc Step and Flashback** leave a full-size, three-dimensional electrical facade behind: a solid-shaped head,
  torso, arms and legs wrapped in scrolling blue-white charge, like the field around a charged creeper. The body
  stays frozen at the departure point while the field crawls over its surface, then fades away. Flashback holds it
  until the static bursts. Short Circuit uses the same charged facade; none of these effects draw line skeletons.
  Ground rings pop at both ends of the dodge. Minimal particles keeps the main field and omits the outer sheen.

The body moves too. Sparks are boxer's punches from a high guard, snapped out and straight back with the shoulders
turning into each one. The clap leans into the swing. The slam drops you to one knee. A sideways flash leans along its
line with both arms flung back, and a Flashback throws the body back with the arms swept forward.

The controls can be switched off with `fightingStyleStrikes` and `fightingStyleDodges` in the client config. Stamina costs
and the dodge cooldown are under `electrokinesis` in the common config.

## Look and sound

Indigo outlines frame blue channels with pale centers. A tap gathers at your palm before the forks
run from target to target. Live Current braids around the main arc, and Ball Lightning carries three
rotating arcs around its core. Rising streamers converge at each Thunderstrike marker before the
bolt drops. Ride the Lightning leaves a dim ground trace that flickers until it cools. Storm Surge
coils around your chest as you rise, then a pulse spreads out under successive thunderbolts.

Strikes and bursts briefly light nearby blocks. Wind-ups use an anchor hum, Live Current has a sharp
electrical buzz, and impacts use a lightning crack with distant thunder on larger strikes.

Each base power has its own arm pose. Chain Lightning draws the hand back and snaps it forward, Live Current
holds it out trembling, Thunderstrike raises it to the sky, and Storm Surge coils the arms in, then flings them wide.
**Ball Lightning** cups both palms round the forming orb in front of the chest and pushes it out from between them.
**Ride the Lightning** whips both arms straight back past the hips as the body becomes the bolt.

## Skill tree

**Arc Lance** reaches its listed 40-block range and forks to up to two exposed foes near its target.
**Static Screen** marks the area where it reflects projectiles. **Overcharge** fills Charge as well
as granting Speed II and Haste II.

How each skill looks:

| Skill | What you see |
| --- | --- |
| Arc Lance | The casting arm locks straight, braced by the other hand, and kicks up with the recoil. The shot is one dead-straight beam, white at the core, with a coil of ions spinning down it and rings blown off the palm; as it fades it breaks back into ordinary forked lightning, which then leaps to the nearby foes. |
| Static Screen | The arms fling wide and a ring of struts rises round you, crowned by a broken ring. |
| Overcharge | Fists clench at your sides and shake as a band of current climbs your body from the feet to the crown, then the arms flex out as it flares off your chest in a ring. |
| Circuit Thread | A flat palm laid toward the metal, trembling. Current crawls from it into the first conductor, then from block to block, and each block's edges light up as it arrives. |
| Ground Sink | A hand raised overhead and driven down at the ground: a strike into the floor, and a rod of current left standing in grounding rings, its tip glowing brighter as it stores projectile energy. Cast again and the rod blows, forking into everything nearby. |
| Arc Lattice | A two-finger flick sends a spark to the surface, where a small three-pronged pylon is planted. Anchors are joined by taut, slightly sagging wires with a bead of light running along them. |
| Blackout | See below. |

The styles have their own pieces too: Tesla Spire's mast and zaps, Faraday Cage's struts, and Jumpstart's heart rings.

### Signature: Blackout

Groundkeeper's signature ability hangs under **High Voltage Mastery** (Level 10, 3 points, 45 stamina,
30 s cooldown). Elle drinks the grid.

1. **The siphon (1 s).** She lifts her arms and every powered thing within 12 blocks throws an arc into her
   chest: Forge Energy machines, Create machinery, redstone blocks, lit lamps and live wire. The arcs come
   faster as the pull builds, with motes of light running along them into her.
2. **The pulse.** Forks race along a blue ring out to 20 blocks with a band of shadow close behind it.
   For 12 seconds redstone gives no signal, FE machines are drained and stop, Create's
   shafts seize, and energy items enemies carry are drained (see [Powers and machines](machines.md)).
   Enemies within 12 blocks are shocked (5 + level), slowed and plunged into **Darkness**.
3. **The payoff.** The pulse gives 20 Charge, plus 1 per 2,000 FE drained, plus 3 per machine (up to 10).
   Drink a big enough grid and you come out of it Overcharged.

## Previewing

```bash
./gradlew runClient --init-script tools/electro-preview.init.gradle
```

casts the base powers, the Ball Lightning combo, Blackout and Static Screen, and saves screenshots in
`build/electro-preview/screenshots/`. The gameplay checks are in
`src/gametest/java/com/jayptucker/heroesevolved/electro/ElectroGameTests.java`.

```bash
./gradlew runClient --init-script tools/lightning-preview.init.gradle
```

photographs the fighting style (sparks, the Thunderclap, Grounding Strike, Arc Step, Flashback, Ground Out and first
person) into `build/lightning-preview/screenshots/`; its checks are in `ElectroCombatGameTests.java`.

## Finishers: Short Circuit, Lightning Rod, Overload

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md).

- **What you see.** You tap two fingers on the foe's brow and a corona snaps on there. Sparks crawl over it and trace its body in lightning, brighter and twitching. You blow on your fingertips: the traced figure tears loose and rises out of it, then earths into the ground as a bolt and burns a scorch star.
- **At 100 Charge** the figure is left standing as a trap for about three seconds, and bursts on the first foe that comes close.
- **Reward:** 25 Charge.

Each press of C plays one of three at random, never the one you used last. Short Circuit is described above; the two alternates give the same reward.

- **Lightning Rod.** One hand up to the sky, the other toward the foe. Static climbs its body while a dark knot of charge gathers overhead and a warning ring closes at its feet. A bolt comes down through it and on into your open palm, and a scorch star is left on the ground.
- **Overload.** You grab the foe and pump current in. A corona swells round it, brighter and brighter, with forks crawling from it into the ground and its flinches coming faster, until it pops in a white flash and a ground pulse.
