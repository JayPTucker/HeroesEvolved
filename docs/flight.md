# Flight — own the sky

Flight is an aerial momentum kit. Build airspeed in cruise, brake and redirect it with
Skyburst, catch someone with Skyhook, or spend your speed on a Meteor Drop or Tempest.
Double-tap Jump toggles Flight and the mouse wheel sets cruise speed. The highest setting
cruises at 3 blocks a tick by default. The pilot banks into turns, and releasing Forward
brakes toward a hover.

Unlocking Flight grants fall-damage immunity, even with flying toggled off or during an Eclipse.

## Momentum

The HUD shows **Momentum (0–100)** beneath your selected speed. Actually travelling
forward at a faster wheel setting builds it: roughly 12–22 points per second,
depending on the selected speed. Pressing Forward into a wall does not count.
Hovering loses 4 points a second. Wind halos and a rising tone signal 50 and 100.

Skyburst, Meteor Drop, a Skyhook hurl, Tempest and Vacuum Clap each spend the whole reserve once.
Their strength scales continuously with the amount spent, so you can make a quick
maneuver now or take another lap for a stronger one. Canceling Skyburst, Meteor Drop or
Vacuum Clap during preparation keeps the reserve. Landing or ending Flight clears it. Death, power removal,
suppression, immobilization, dimension changes and logout clear pending maneuvers.

## Actions

Keys below are the default slots; learned optional skills can replace them as before.

| Key | Level | Move | How it plays |
| --- | --- | --- | --- |
| G | 1 | **Free Flight** | Toggle a controlled hover. Double-tap Jump also toggles it. Scroll up/down while airborne to choose speed; hold Forward to travel. Each level unlocks another speed, up to five. |
| H | 1 | **Skyburst** | **Tap:** burst forward immediately, angling upward from the ground. **Hold:** brake as wind coils around you, then release along your new aim for a faster, longer dash. Fully coils in 1.5 seconds and automatically releases at 2 seconds. Momentum increases thrust and duration. Your selected speed carries into the next cruise. **Shift + H** cancels the charge without spending Momentum. |
| Z | 3 | **Meteor Drop** | Requires being airborne. Hang inside a collapsing wind disc for 0.5 seconds, then accelerate downward. Aim to curve your descent. Hitting solid ground or a wall releases a pressure wave. More Momentum means a wider, harder impact. **Shift + Z** aborts. |
| X | 4 | **Skyhook** | Seize someone in sight within 7 blocks; grabbing from the ground starts a quick takeoff. Press again to hurl them along your aim, faster with Momentum. Allied throws receive Slow Falling. **Shift + X** releases them gently with Slow Falling, for free. |
| V | 5 | **Tempest** | A tornado touches down on the ground ahead and gathers for one second. Then you descend or climb to the funnel and bank around it. It pulls exposed foes from within 20 blocks and carries them up the wall. The finale hurls captives out of the funnel. Look to steer it across the terrain. The camera pulls back to frame the storm. Momentum strengthens the storm's damage and final throw. **Shift + V** cancels. |

A useful sequence: **Free Flight → wheel up → Forward → hold Skyburst → turn and release →
Skyhook → hurl → Meteor Drop.** For crowd control, bank Momentum, then steer a Tempest
through a group and follow its finale with another pass.

## Feel and effects

- Flight uses translucent gray and ivory condensation with broken, tapered crests. In a hover, the pilot holds their
  arms apart and staggers their feet. At speed, a leading fist and a trailing arm tilt along the
  actual flight path. A bow of compressed air rides ahead and short trails peel off the boots.
- In Skyburst's wind-up, the hands draw inward as wind bands tighten. On release, an expanding
  ivory pressure front snaps open with a deep sonic boom and sheds a long condensation wake. The boom
  carries up to 192 blocks. Everyone within 64 blocks feels a brief tremor that weakens with distance.
  The dash sustains thrust before easing into cruise and stops short of obstacles. Skyhook traces the
  catch and holds cargo in your arms or overhead through flight, then sends a smaller collar with the throw.
- Meteor Drop starts in an upright brace, tips into the plunge and fans pressure fronts along
  the surface it strikes. Tempest draws its 20-block reach on the ground as bands spiral
  inward. Its warning gives targets one second to escape before the pull begins.
- Rescue Rush traces a winding path to the catch. Wind Sanctuary raises a shallow wind dome
  and marks hostile shots as it deflects them. Jetstream draws moving crests down a lane.
  Thermal Lance tightens a ground ring and opens it into a column. Vacuum Clap sweeps the
  hands together and reverses the incoming wind into an expanding front. Tempest Drill,
  Gale Wall and Formation Flight use the same wind shapes. Reduced particle settings keep
  the strongest cues.

## Purchasable skills

All eighteen nodes have been redesigned. The six active techniques can replace any of your
five ability keys. Purchases retain their saved IDs and point costs. Each branch has its own
play loop; opening and mastery nodes no longer grant blanket cost, damage or cooldown bonuses.
The normal one-capstone limit still applies.

### Sky Guard

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Featherlight** | Passive | 1 / 1 | Cushions visible teammates within 18 blocks. Falls of up to 10 blocks deal no damage. Larger falls deal half damage. |
| **Rescue Rush** | Active | 2 / 2 | Rush to a visible teammate or your tamed pet within 18 blocks, reset their fall and grant 8 seconds of Slow Falling plus two hearts of absorption. |
| **Tandem Lift** | Passive | 3 / 2 | Rescue Rush lifts both of you on an upward gust and banks 15 Momentum for your next maneuver. |
| **Wind Sanctuary** | Active | 2 / 2 | Aim at solid ground within 20 blocks to shelter a 5-block area for 6 seconds. Friends land softly; incoming hostile projectiles slow and turn outward once. Shift + key dismisses it. |
| **Safe Harbor** | Passive | 3 / 1 | Wind Sanctuary heals each friend one heart once per cast and grants Resistance I while they remain inside. |
| **Last-Second Save** | Capstone | 5 / 3 | Once every 45 seconds, catch a visible teammate within 18 blocks after a fall over 10 blocks. Negate their landing damage and grant 5 seconds of Slow Falling. Bank 25 Momentum if you are flying. |

### Jetstream

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Ram Air** | Passive | 1 / 1 | Build Momentum 30% faster during real forward flight at a faster wheel setting. |
| **Barrel Roll** | Active | 2 / 2 | Carry your entry speed into a 0.6-second corkscrew. The first hostile projectile you deflect banks 15 Momentum. Shift + key rolls left. Solid walls stop the roll. |
| **Vector Control** | Passive | 3 / 2 | Skyburst fully coils in 0.9 seconds instead of 1.5, letting you redirect your airspeed sooner. |
| **Jetstream** | Active | 2 / 2 | Lay a 12-block wind lane for 8 seconds. Friends gain Speed II, Jump Boost and Slow Falling; enemies are carried along it. Solid terrain clips its reach. Shift + key dismisses it. |
| **Draft Rider** | Passive | 3 / 1 | Jetstream reaches 20 blocks. While you are flying, the first friend other than you to ride a current banks 20 Momentum for you, once per cast. |
| **Slipstream Echo** | Capstone | 5 / 3 | A Skyburst that spends at least 50 Momentum leaves an 8-block wind lane for 4 seconds, letting friends follow your launch. |

### Stormbreaker

| Skill | Type | Level / points | Effect |
| --- | --- | --- | --- |
| **Pressure Tap** | Passive | 1 / 1 | A successful airborne melee hit during Flight banks 5 Momentum, at most once every half second. Power damage and friendly hits do not count. |
| **Thermal Lance** | Active | 2 / 2 | Aim at solid ground within 20 blocks. After 0.4 seconds, a 3.5-block-radius updraft damages and lifts enemies and cushions allies. Fly into your own column to gain a lift and 20 Momentum once per cast. Shift + key cancels the warning. |
| **Rising Pressure** | Passive | 3 / 2 | Thermal Lance sends a second lifting pulse 0.4 seconds after the first, holding enemies aloft for your follow-up. |
| **Vacuum Clap** | Active | 2 / 2 | Draw visible enemies inward for 0.4 seconds, then clap out a 6-block pressure wave. Release spends stored Momentum for a harder burst. Shift + key cancels before release and keeps Momentum. |
| **Eye of the Storm** | Passive | 3 / 1 | Vacuum Clap reaches 9 blocks and brakes incoming hostile projectiles during its wind-up. |
| **Sonic Bloom** | Capstone | 5 / 3 | Meteor Drop and Vacuum Clap echo once after 0.6 seconds: a second pressure wave deals half damage within 90% of the original radius. |

Projectile defenses leave friendly shots alone and act once per incoming projectile per cast.
Walls clip movement, shelter and damage.

Try **Thermal Lance → Rising Pressure → Meteor Drop → Sonic Bloom** for an aerial crowd-control
build, or **Rescue Rush → Tandem Lift → Wind Sanctuary** to bring a friend back safely.
**Ram Air → charged Skyburst → Slipstream Echo → Jetstream** turns your flight path into a
route the team can follow.

## Rules and tuning

Meteor Drop and Tempest respect solid cover and allies. They never break blocks or start fires.
Maneuvers cannot overwrite each other. The landing cushion from a dive expires after the maneuver.
A canceled wind-up keeps its original stamina cost and cooldown; releasing
Skyburst never charges a second activation cost. Running out of stamina ends sustained flight
with its existing safe-landing behavior.

New config defaults: Skyburst costs 18 stamina with a 3-second cooldown; normal/fast flight
cost 4/9 stamina a second. Tempest costs 55 with a 30-second cooldown, a 20-block pull reach,
an 8-block base funnel setting, a 6.5-block minimum orbit and 5 active seconds after the warning.
Shared balance tuning applies, so displayed costs and cooldowns can be lower. Existing server configs retain
their saved values. Skyburst starts at 2.5 times the configured forward speed and peaks at 10 blocks
per tick with default settings and a full charge and Momentum reserve. Its thrust lasts 20–29 ticks.
The old ascent-duration entry remains for config compatibility.

## Verification and preview

```bash
./gradlew build
./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle
python3 tools/verify-skill-content.py
./gradlew runClient --init-script tools/flight-preview.init.gradle
```

The preview creates a separate flat world and writes staged screenshots to
`build/flight-preview/screenshots/`. Flight GameTests cover Momentum, Skyburst range and wall stops, Skyhook attachment, input
release/cost/cooldown, cancellation, dive wind-up, collision/cover and fall-protection expiry.
The existing four wheel-control tests continue to cover level caps, braking and resets.
`FlightSkillGameTests` additionally checks purchases and save compatibility, all six optional
techniques, linked passive upgrades, projectile/team/cover rules, cancellation and respec cleanup.
