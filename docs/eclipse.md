# The Eclipse

The Eclipse is how powers enter the world. It is a total solar eclipse that falls on every tenth
Minecraft day (`eclipse.intervalDays`, default 10), with totality centred on noon.

## Calendar and moon

A solar eclipse can only happen at a new moon, so the Overworld's lunar month is stretched to the
Eclipse interval. Eclipse day (days 10, 20, 30... counting the first day as day 1) is always a new
moon. The moon waxes afterwards, is full halfway between Eclipses, and wanes back to new. The moon
phase drives moon rendering, slime spawning and moon brightness exactly as in vanilla. Dimensions
with a fixed time (the Nether, the End, the city) are unchanged.

The schedule follows the day clock, not a tick counter. Sleeping skips nights but not Eclipse days.
A server that starts mid-Eclipse joins it in progress, so the peak still lands at noon. Winding
time back with `/time set` lets an earlier Eclipse day fire again; a day never hosts two.

## Phases

| Phase | Default length | What happens |
| --- | --- | --- |
| Approach | 3 min (`approachTicks`) | A dark moon slides steadily onto the sun. Daylight barely changes until the last sliver. |
| Totality | 3.5 min (`totalityTicks`) | The umbra sweeps over everyone outside, the sky goes near-night and the stars come out. A pearly corona and pink prominences ring the black disc, with a diamond-ring flare at each end. **Dormant powers are handed out, and every power is suppressed.** |
| Departure | 3 min (`departureTicks`) | The moon slides off the other side and daylight returns. Powers work again. |

The shadow works through the lightmap: anything lit by the sky darkens, while torch-lit
interiors keep their light. Players with open sky above them also get a screen-space shadow
whose edge visibly sweeps across the view as totality arrives and leaves. Fog dims with the sky
so the horizon does not glow. Rain hides the moon along with the sun, as vanilla hides the sun.

## Who gets a power

At the start of totality every online player without a power gets one roll
(`dormantAbilityChance`, default 40%) for a random unclaimed power. A player who joins during
totality rolls on login. Each Eclipse allows one roll per player, even across reconnects. The
chosen are told only that "something in your blood answers the dark."

## Adrenaline: when a dormant power wakes

A dormant power does not wake on its own. It waits for adrenaline: its owner must be **in a
fight** (hit by, or hitting, a living attacker within the last `awakening.combatWindowSeconds`,
default 10) **and** below `awakening.healthThreshold` (default 4 health, two hearts).

- As a fight drags a dormant owner's health down, a heartbeat starts and quickens.
- A blow in a fight that would take them below the threshold wakes the power before it lands, and
  is capped so it cannot kill, even through absorption hearts.
- The awakened owner is shielded until their power's intro takes over, and the intro shields them
  for its whole length.
- Every intro release grants a second wind (at least 40% health, Resistance II for six seconds)
  and then ends the fight its own way. See `power-intros.md`.
- Low health from a fall, starvation or the like is not a fight, and wakes nothing.
- Nothing wakes during totality.

The Formula and admin awakenings skip the adrenaline requirement.

## Commands

```text
/heroesevolved eclipse start      # full Eclipse from first contact
/heroesevolved eclipse totality   # skip straight to totality
/heroesevolved eclipse stop
/heroesevolved eclipse status     # current phase and time left, or the next Eclipse day
```

The admin screen's Start Eclipse button starts a full Eclipse. A manual Eclipse started at
night still runs, but the sun, and the moon in front of it, are below the horizon.
