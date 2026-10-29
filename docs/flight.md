# Flight — own the sky

Flight is an aerial momentum kit. Build airspeed in cruise, brake and redirect it with
Skyburst, catch someone with Skyhook, or spend your speed on a Meteor Drop or Tempest.
The existing double-tap Jump and mouse-wheel controls remain.

## Momentum

The HUD shows **Momentum (0–100)** beneath your selected speed. Actually travelling
forward at a faster wheel setting builds it: roughly 12–22 points per second,
depending on the selected speed. Pressing Forward into a wall does not count.
Hovering loses 4 points a second. Wind halos and a rising tone signal 50 and 100.

Skyburst, Meteor Drop, a Skyhook hurl and Tempest each spend the whole reserve once.
Their strength scales continuously with the amount spent, so you can make a quick
maneuver now or take another lap for a stronger one. Charging Skyburst preserves
what you have banked. Landing or ending Flight clears it; death, power removal,
suppression, immobilization, dimension changes and logout clear pending maneuvers.

## Actions

Keys below are the default slots; learned optional skills can replace them as before.

| Key | Level | Move | How it plays |
| --- | --- | --- | --- |
| G | 1 | **Free Flight** | Toggle a controlled hover. Double-tap Jump also toggles it. Scroll up/down while airborne to choose speed; hold Forward to travel. Each level unlocks another speed, up to five. |
| H | 1 | **Skyburst** | **Tap:** a short 0.6-second rising takeoff on the ground, or an immediate aerial dash. **Hold:** brake in the air as wind coils around you, then release along your new aim. Fully coils in 1.5 seconds; automatically releases at 2 seconds if a key release is lost. Momentum increases the burst. **Shift + H** cancels without spending Momentum. |
| Z | 3 | **Meteor Drop** | Requires being airborne. Hang inside a collapsing wind disc for 0.5 seconds, then accelerate downward. Aim to curve your descent. Hitting solid ground or a wall releases a pressure wave. More Momentum means a wider, harder impact. **Shift + Z** aborts. |
| X | 4 | **Skyhook** | Seize someone in sight within 7 blocks; grabbing from the ground starts a quick takeoff. Press again to hurl them along your aim, faster with Momentum. Allied throws receive Slow Falling. **Shift + X** releases them gently with Slow Falling, for free. |
| V | 5 | **Tempest** | A visible boundary and inward wind warn of the vortex for one second. Then you orbit its eye, lifting and spiralling enemies before the final outward burst. Aim to drift the eye across the battlefield. Momentum strengthens the pull and finale. **Shift + V** cancels without a damaging finale. |

A useful sequence: **Free Flight → wheel up → Forward → hold Skyburst → turn and release →
Skyhook → hurl → Meteor Drop.** For crowd control, bank Momentum, then steer a Tempest
through a group and follow its finale with another pass.

## Feel and effects

- Skyburst compresses wind inward, then peels it away in a directional corkscrew wake.
- Cruise eases into a streamlined body pose with a leading fist, a stabilizing arm,
  separated legs and gentle banking. The camera stays level. Wind pitch and volume follow speed.
- Meteor Drop has a readable hang, braced hands in first person, a pressure sheath during
  descent, and successive rolling fronts at impact. Nearby camera shake uses the existing comfort settings.
- Tempest has a ground boundary matching its configured radius, rising wind ribbons and
  cosmetic debris flecks. The warning has no damage or pull. Existing boot vapor persists behind you.
- The original recorded launch/boost sounds remain, joined by vanilla Breeze and wind-charge sounds.
- Wind you can see in daylight: Breeze gusts and small gusts, cloud and smoke puffs gathering into
  a coil or trailing a wake, dust spiralling up a Tempest, updrafts for Thermal Lance, and Meteor Drop
  and pressure waves that throw real dust and debris off the block they hit.
  Effects use the existing bounded particle system; vapor retains its separate pool.

## Purchasable skills

All eighteen nodes have been redesigned. The six active techniques can replace any of your
five ability keys. Purchases retain their saved IDs and point costs. Each branch has its own
play loop; opening and mastery nodes no longer grant blanket cost, damage or cooldown bonuses.
The normal one-capstone limit still applies.

### Sky Guard

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Featherlight** | Passive | 1 / 1 | Falls of up to 10 blocks deal no damage; larger falls deal half damage. |
| **Rescue Rush** | Active | 2 / 2 | Rush to a visible teammate or your tamed pet within 18 blocks, reset their fall and grant 8 seconds of Slow Falling plus two hearts of absorption. |
| **Tandem Lift** | Passive | 3 / 2 | Rescue Rush lifts both of you on an upward gust and banks 15 Momentum for your next maneuver. |
| **Wind Sanctuary** | Active | 2 / 2 | Aim at solid ground within 20 blocks to shelter a 5-block area for 6 seconds. Friends land softly; incoming hostile projectiles slow and turn outward once. Shift + key dismisses it. |
| **Safe Harbor** | Passive | 3 / 1 | Wind Sanctuary heals each friend one heart once per cast and grants Resistance I while they remain inside. |
| **Last-Second Save** | Capstone | 5 / 3 | Once every 45 seconds, catch yourself or a visible falling teammate within 18 blocks after a fall over 10 blocks. Negate the landing and grant 5 seconds of Slow Falling; bank 25 Momentum if you are flying. |

### Jetstream

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Ram Air** | Passive | 1 / 1 | Build Momentum 30% faster during real forward flight at a faster wheel setting. |
| **Barrel Roll** | Active | 2 / 2 | While airborne, corkscrew forward and right for 0.6 seconds, deflecting incoming hostile projectiles nearby. Shift + key rolls left. Solid walls stop the roll. |
| **Vector Control** | Passive | 3 / 2 | Skyburst fully coils in 0.9 seconds instead of 1.5, letting you redirect your airspeed sooner. |
| **Jetstream** | Active | 2 / 2 | Lay a 12-block wind lane for 8 seconds. Friends gain Speed II, Jump Boost and Slow Falling; enemies are carried along it. Solid terrain clips its reach. Shift + key dismisses it. |
| **Draft Rider** | Passive | 3 / 1 | Jetstream reaches 20 blocks. While you are flying, the first friend other than you to ride a current banks 20 Momentum for you, once per cast. |
| **Slipstream Echo** | Capstone | 5 / 3 | A Skyburst that spends at least 50 Momentum leaves an 8-block wind lane for 4 seconds, letting friends follow your launch. |

### Stormbreaker

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Pressure Tap** | Passive | 1 / 1 | A successful airborne melee hit during Flight banks 5 Momentum, at most once every half second. Power damage and friendly hits do not count. |
| **Thermal Lance** | Active | 2 / 2 | Aim at solid ground within 20 blocks. A warning circle gathers for 0.4 seconds, then a 3.5-block updraft damages and lifts enemies. Friends in it land softly. Shift + key cancels the warning. |
| **Rising Pressure** | Passive | 3 / 2 | Thermal Lance sends a second lifting pulse 0.4 seconds after the first, holding enemies aloft for your follow-up. |
| **Vacuum Clap** | Active | 2 / 2 | Draw visible enemies inward for 0.4 seconds, then clap out a 6-block pressure wave. Spend stored Momentum for a harder burst. Shift + key cancels before release. |
| **Eye of the Storm** | Passive | 3 / 1 | Vacuum Clap reaches 9 blocks and brakes incoming hostile projectiles during its wind-up. |
| **Sonic Bloom** | Capstone | 5 / 3 | Meteor Drop and Vacuum Clap echo once after 0.6 seconds: a second pressure wave deals half damage within 90% of the original radius. |

The six active skills use dedicated effects: a rescue tether, a sheltering wind dome, a body
corkscrew, a directional current, a ground warning followed by rising thermals, and an inward
vacuum followed by a pressure burst. Projectile defenses leave friendly shots alone and act
once per incoming projectile per cast. Walls clip movement, shelter and damage.

Try **Thermal Lance → Rising Pressure → Meteor Drop → Sonic Bloom** for an aerial crowd-control
build, or **Rescue Rush → Tandem Lift → Wind Sanctuary** to bring a friend back safely.
**Ram Air → charged Skyburst → Slipstream Echo → Jetstream** turns your flight path into a
route the team can follow.

## Rules and tuning

Meteor Drop and Tempest respect solid cover and allies. They never break blocks or start fires.
Maneuvers cannot overwrite each other. Fall cushioning is temporary and cannot be saved as
permanent immunity. A canceled wind-up keeps its original stamina cost and cooldown; releasing
Skyburst never charges a second activation cost. Running out of stamina ends sustained flight
with its existing safe-landing behavior.

New config defaults: Skyburst costs 18 stamina with a 3-second cooldown; normal/fast flight
cost 4/9 stamina a second. Tempest costs 55 with a 30-second cooldown, an 8-block pull radius,
a 6.5-block orbit and 5 active seconds after the warning. Shared balance tuning applies, so displayed costs and cooldowns can be lower. Existing server configs retain
their saved values. The old ascent-duration entry remains for config compatibility;
Skyburst uses its new twelve-tick takeoff. Action IDs and skill-tree/loadout saves are unchanged.

## Verification and preview

```bash
./gradlew build
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
python3 tools/verify-skill-content.py
./gradlew runClient --init-script tools/flight-preview.init.gradle
```

The preview creates a separate flat world and writes staged screenshots to
`build/flight-preview/screenshots/`. Twelve new Flight GameTests cover Momentum, input
release/cost/cooldown, cancellation, dive wind-up, collision/cover and fall-protection expiry.
The existing four wheel-control tests continue to cover level caps, braking and resets.
`FlightSkillGameTests` additionally checks purchases and save compatibility, all six optional
techniques, linked passive upgrades, projectile/team/cover rules, cancellation and respec cleanup.
