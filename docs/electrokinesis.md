# Electrokinesis

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

## Look and sound

Indigo outlines frame blue channels with pale centers. A tap gathers at your palm before the forks
run from target to target. Live Current braids around the main arc, and Ball Lightning carries three
rotating arcs around its core. Rising streamers converge at each Thunderstrike marker before the
bolt drops. Ride the Lightning leaves a dim ground trace that flickers until it cools. Storm Surge
coils around your chest as you rise, then a pulse spreads out under successive thunderbolts.

Strikes and bursts briefly light nearby blocks. Wind-ups use an anchor hum, Live Current has a sharp
electrical buzz, and impacts use a lightning crack with distant thunder on larger strikes.

## Skill tree

**Arc Lance** reaches its listed 40-block range and forks to up to two exposed foes near its target.
**Static Screen** marks the area where it reflects projectiles. **Overcharge** fills Charge as well
as granting Speed II and Haste II. Circuit Thread, Ground Sink and Arc Lattice use the same blue
channels and brief pulses to show their conductors, discharge and crossing points.

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
