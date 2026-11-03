# Cryokinesis — shape the winter

Cryokinesis connects **Frost buildup**, solid ice construction, Glacier Ride and deliberate
shatters. Its walls, ramps and skating ribbon are real three-dimensional temporary terrain.
Branching veins, translucent shells and growing spires show the state of the cold before an
impact, rather than relying on a cloud of particles to imply an obstacle.

The five base action IDs and all eighteen purchased skill IDs stay compatible with existing
saves and loadouts. Their displayed names and controls have been redesigned.

## Frost and counterplay

Cold slows a target before enough accumulated Frost forms a brittle shell. Frost uses the
existing chill system, including vanilla frost overlay, shivering and a transient movement
penalty. It starts settling after cold is no longer applied. Bosses chill rather than being
held indefinitely, and cold-immune creatures resist the freeze.

- Rime Stream builds Frost continuously while held and stops when released.
- Crystal Prison primes an unprepared target with Frost; prepared cold forms a solid shell.
- Physical hits can shatter frozen targets. The caster can deliberately shatter their own
  prepared prison with its follow-up key press.
- Frozen players can press **Jump or Attack** to crack the shell. Accepted presses are rate
  limited, and stronger casters require more deliberate struggles. An on-screen fracture
  display and visible cracks show progress.
- Heat thaws the ice, and a teammate can break a prison with a strike. A short thaw grace
  prevents a player being immediately locked into another freeze.
- The caster and allies are excluded from the deliberate hostile shatter burst.

## Base actions

These are the default keys; optional learned skills can replace them as usual.

| Key | Level | Action | Behavior |
| --- | --- | --- | --- |
| G | 1 | **Rime Stream** | Hold to sweep cold from the hands. Frost branches across surfaces and accumulates on enemies. Release to stop the channel. |
| H | 2 | **Glacial Sculpt** | Aim at a clear surface to grow a wall of solid ice. **Shift + H** makes a ramp. Aiming at an owned construct reshapes it for eight stamina, with a two-second settling time, even while the new-wall cast is recovering. |
| Z | 3 | **Glacier Ride** | Toggle skating on a growing ice ribbon. Look and movement keys steer; Space shapes a rising ramp at higher levels, Shift brakes, and Sprint accelerates. Press again to step off safely. |
| X | 4 | **Crystal Prison** | Concentrate cold around a target within twelve blocks. An unprepared cast adds Frost; prepared Frost forms a prison. Press again to shatter that shell, with a short hostile burst around it. |
| V | 5 | **Whiteout** | Aim a growing winter field. A one-second wind-up precedes Frost, snow and rising ice spires. **Shift + V** deliberately collapses the prepared ice and frozen enemies after the wind-up. |

Glacier Ride is not unrestricted flight. It begins on a supported surface, crosses limited gaps
and makes bounded rising ramps. The temporary ribbon stays under the skater and can widen into
a route for companions. Solid obstructions, exhausted stamina, loss of the power and invalid
terrain end the ride safely. Ride controls and speed are displayed on screen.

A combat sequence is **Rime Stream → Glacial Sculpt → Glacier Ride → Crystal Prison → shatter**.
Whiteout prepares a larger battlefield: cold links the ice, the field grows, and the final
collapse spends what has been built.

## Three skill branches

The branches unlock interactions; their roots and masteries no longer silently apply the
old blanket cost, damage or cooldown bonuses.

| Branch | Opening → active → mastery | Technique → upgrade → capstone |
| --- | --- | --- |
| **Sculptor** | **Architectural Memory:** longer-lived sculpted ice → **Crystal Lance:** forge and launch a physical spear → **Fracture Lines:** collapsing constructed ice spreads Frost | **Rime Loom:** grow and guide a physical path → **Double Weave:** wider path and skating ribbon → **Suspension Stitch:** longer weave with bounded rising stairs |
| **Glacier** | **Sure Footing:** steadier skating → **Frost Armor:** six consumable plates and a countershot → **Cold Wake:** skating chills nearby enemies | **Stillheart Cocoon:** store impacts in a protective shell → **Sterile Ice:** cleanse harmful effects → **Second Spring:** stronger opening recovery and nearby healing |
| **Winterbinder** | **Hoarfrost:** faster stream buildup → **Cold Snap:** expanding cold front → **Echoing Fracture:** deliberate prison shatters spread Frost | **Brittle Constellation:** share Frost through crystal stars → **Long Winter:** longer network → **Shattered Sky:** stronger staged fractures that refreeze prepared surviving mobs |

### Six active sub-abilities

These are separate learned actions on the wheel and can be bound to any ability key. Their
existing purchase IDs remain compatible with saved characters.

- **Crystal Lance:** Forge a faceted spear for 0.6 seconds, adjusting aim during the wind-up.
  It then flies up to forty blocks with actual swept collision. Its impact builds Frost;
  prepared targets freeze and frozen targets fracture, splashing cold to nearby visible foes.
  A wall or another body can intercept the spear after it has been launched.
- **Frost Armor:** Six thick plates orbit for up to eight seconds. Each catches one hostile
  projectile or absorbs part of a physical impact, then breaks. Fire melts two plates and
  receives less protection. Caught impacts store cold. **Press again** to spend the remaining
  plates as an aimed ten-block shard fan; stored cold strengthens those shots. Exhausted
  armor leaves no lingering Resistance or Fire Resistance potion shield.
- **Cold Snap:** A raised ice front expands across seven blocks over 0.6 seconds. Each enemy
  meets it once. Fresh enemies gain Frost, prepared targets freeze, and frozen targets
  shatter. The front respects cover, giving its position and timing a real combat role.
- **Rime Loom:** A temporary path grows one physical segment every three ticks. **Aim and
  press again while it grows** to bend its remaining course. Double Weave makes it three
  blocks wide; Suspension Stitch extends it to twenty-four segments and permits bounded
  rising stairs. Each segment remembers its original terrain and lasts twelve seconds.
- **Stillheart Cocoon:** Shelter yourself or a friendly/consenting creature in a layered
  pressure-bearing shell. It catches up to 80% of ordinary damage within a finite reservoir;
  an exhausted shell opens. **Press again** to open early, recover health and spend stored
  impacts in a Frost pulse that leaves allies unharmed. Players remain inside for at most
  three seconds and mobs six. Shift targets the caster. Stored pressure strengthens recovery;
  Second Spring also heals nearby visible friends. Revocation or invalid cleanup grants no
  healing. A self-preserved caster can open only this cocoon; other powers stay locked.
- **Brittle Constellation:** Mark up to four visible enemies with rotating crystal stars.
  Formation adds a little Frost rather than an instant solid freeze. Further Frost applied
  to one star shares a bounded amount with its visible partners. Physically shattering a
  frozen star consumes the network and sends a staged fracture along its links, one hop
  every four ticks. Cover breaks the travelling chain, and it cannot recursively trigger
  itself. Long Winter extends the network to ten seconds; Shattered Sky's stronger fractures
  briefly refreeze prepared surviving mobs.

Only one capstone may be learned at a time.

## Three-dimensional ice and restoration

Sculpt walls, ramps and the ride ribbon use real collision blocks. Placement does not replace
containers, permanent obstacles or occupied space, and the caster's build permissions and
protection hooks apply. Ice is created and removed under bounded work budgets.

Each temporary block remembers its exact original state. Melting restores that state unless a
player has deliberately changed the block since placement. Repeated frosting extends the expiry
while preserving the first original state. The chunk stores recovery information, so unloading
or restarting does not turn a temporary bridge into permanent terrain. Unloaded chunks are not
forced; recovery resumes when they load. Server shutdown restores loaded temporary ice.

The shared system retains its four-thousand-block cap, ninety-six-write thaw budget and soft
two-millisecond allowance. Large patches melt gradually rather than restoring every block in a
single expensive tick.

## Effects and controls

- Hand-driven rime streams trace branching veins and crystallize into layered shells.
- Sculpted walls and ramps grow in visible stages, with physical depth and collision.
- Glacier Ride bends a continuous ice ribbon through turns; the body banks and the stance
  follows the skates. Frost and fragments trail the route.
- Prison cracks become more pronounced as a trapped player fights the shell.
- Shattered ice breaks into tumbling fragments that dissolve into snow.
- Whiteout expands before its cold takes hold, with growing spires, linked Frost veins and a
  readable collapse. Reduced particle settings retain the important boundaries and growth.

## Compatibility and verification

The underlying freeze, shatter and thaw services remain available for existing story moments,
awakening, power reactions and borrowed actions. Legacy Blizzard and the larger awakening dome
still use their bounded terrain sweep and restoration system; the new Whiteout is the base
ultimate rather than a renaming of that old instant dome.

The isolated Cryo rework tests cover held Frost buildup/release, solid build-permitted
construction, prepared prison/shatter, player struggle rate limits, heat thaw, Whiteout wind-up
and collapse, physical skating and its rising ceiling, safe quick-reshape dispatch, gravity
cleanup, and preserved eighteen-node identities. Dedicated combat/technique tests also cover
physical lance travel and interception, finite armor and explicit countershots, conditional
Cold Snap, guided construction, pressure recovery, safe self-cocoon input, shared Frost and
staged nonrecursive crystal fractures.
The existing freeze, terrain recovery, reaction, story and compatibility regressions remain.

Run the live visual/control preview with Java 21:

```sh
./gradlew runClient --offline --init-script tools/cryo-preview.init.gradle
```

It uses actual survival controls and captures Rime Stream, oblique views of solid walls and
ramps, the riding ribbon and carve, prison/shatter, the stages of Whiteout, and a trapped
player breaking free with Jump and Attack. The preview and GameTest code are excluded from
the release JAR.

The six optional skills have their own live control and mesh preview:

```sh
./gradlew runClient --offline --init-script tools/cryo-skills-preview.init.gradle
```

It photographs forging and flying lances, armor plate loss/countershots, the travelling cold
front, a guided rising weave, pressure-bearing cocoon release and the staged crystal network.
