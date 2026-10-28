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
  one that hits a **copper bulb** toggles it, just as a real strike would.

## Actions

| Key | Level | Action | Behavior | Overcharged | Base stamina / cooldown |
| --- | --- | --- | --- | --- | --- |
| G | 1 | Chain Lightning | **Tap:** a forked bolt that leaps between foes, one more leap per level. **Hold:** Live Current, a continuous stream (up to 4 s, 15 stamina a second) that locks its target in place. | Two extra leaps, 40% more damage | 18 / 1.25 s |
| H | 2 | Ball Lightning | A crackling orb that curves toward foes, lashes the nearest one every few ticks and bursts on contact. **Shift + H** bursts it early. | Lashes two foes; a bigger, harder burst | 25 / 3 s |
| Z | 3 | Thunderstrike | Mark the spot you aim at: a warning ring and rising streamers, then lightning strikes 0.8 s later. Creepers charge, pigs turn, as with real lightning, but nothing catches fire. Striking water shocks everyone in it within 8 blocks. | Three strikes | 45 / 10 s |
| X | 4 | Ride the Lightning | Become a bolt and reappear down the line, following the ground, shocking what you pass. The path stays live for 3 s and shocks foes who step on it. | 60% farther | 30 / 5 s |
| V | 5 | Storm Surge | Gather the storm for 1.5 s (you lift off your feet), then strike everything within 10 blocks (16 in a storm) and knock it back. Spends all your Charge: +1 damage per 10. **Shift + V** cancels the wind-up. | (always spends the Charge) | 110 / 120 s |

Overexerting (casting without enough stamina) loses control: the current lashes everyone around you
and leaves you slowed.

## Look and sound

Bolts are built fresh every time from a fractal path with forks that taper away. They are drawn
the way vanilla lightning is (additive, untextured): a soft blue glow, a bright body and a white-hot
core, struck two or three times in a row so they flicker. Strikes and bursts briefly place an
invisible light block, so lightning really lights up a dark cave or the bunker; it is removed a few
ticks later and never replaces a block. The sounds are vanilla: the lightning crack, thunder, the
guardian's whine for Live Current, the respawn anchor for wind-ups and the copper bulb's snap.

## Skill tree

The tree is unchanged except **Overcharge** (Storm Geometry), which now also fills your Charge, so
your next power is empowered.

## Previewing

```bash
./gradlew runClient --init-script tools/electro-preview.init.gradle
```

casts every power at dusk against still targets and saves screenshots in
`build/electro-preview/screenshots/`. The gameplay checks are in
`src/gametest/java/com/jayptucker/heroesevolved/electro/ElectroGameTests.java`.
