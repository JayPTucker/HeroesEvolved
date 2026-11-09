# Powers and machines (Forge Energy, Mekanism, Thermal, Create)

Powers that work with electricity reach real machines, not only redstone. Nothing here depends on
another mod: Heroes Evolved talks to machines through NeoForge's own **Forge Energy** capability (FE, the
same thing RF/"redstone flux" became), so it works with Mekanism, Thermal Expansion, Powah, Flux Networks,
Immersive Engineering, Create Crafts & Additions and any other mod whose blocks store FE. Create's kinetic
blocks run on rotation instead of electricity, so they get their own handling.

The code is `grid/GridDisruption.java`. Two small mixins do the rest: `BlockEntityStallMixin` skips the
tick of a machine inside a pulse, and `CreateKineticSeizeMixin` makes a Create block inside a pulse report
zero speed. The Create mixin is `@Pseudo` with `require = 0`, so it does nothing when Create is not installed.

## What counts as a machine

- Any block entity that exposes the Forge Energy capability on any side (batteries, cables, generators,
  machines).
- Any Create kinetic block entity (shafts, gearboxes, belts, presses, mixers, drills, fans...). It is found
  by class name, so Create is never needed to build or run this mod.

Plain blocks, inventories and anything without stored energy are never touched.

## Electromagnetic pulses

**Radiation's Electromagnetic Pulse** (16 blocks, 10 s) and **Electrokinesis's Blackout** (20 blocks, 12 s)
open a field. While it lasts:

| Inside the field | What happens |
| --- | --- |
| Redstone | Puts out no signal (unchanged from before). Lamps go dark, pistons drop, circuits stop. |
| FE storage | Drained, up to `maxDrainPerMachine` (250,000 FE by default) per machine. Generators that refill are drained again every half second. The drained energy is destroyed. |
| FE machines | Stop ticking, so consumers that refuse extraction (most processing machines) still stop working. They wake exactly as they were when the field ends: nothing is replaced and no block data is changed. |
| Create | Shafts, belts and machines read zero speed: presses stop pressing, belts stop moving, and the shafts visibly stop turning for nearby players. They spin back up when the field ends. |
| Carried items | Energy-holding items (jetpacks, powered armor, energy tools) carried by hostile creatures, and by players you could harm under the server's PvP rules, are drained. Your own and your allies' gear is safe. |

Up to 24 affected machines short out visibly (crawling arcs, sparks, a puff of smoke) when a pulse hits,
and a few keep sparking while they are dead.

## Lightning charges machines

Electrokinesis's lightning pushes power *into* anything that stores FE:

| Action | Charge |
| --- | --- |
| Chain Lightning, tapped at a block | 4,000 FE (`lightningCharge`) |
| Live Current held on a machine | 2,000 FE five times a second (10,000 FE/s) |
| Ball Lightning bursting beside machines | 8,000 FE into up to four nearby |
| Thunderstrike landing on one | 40,000 FE |
| Storm Surge | 20,000 FE into every machine in its radius (up to 16), each with a visible arc |

A machine inside an active pulse takes no charge: the field eats the current.

## Technopathy

- **Handshake** enrols powered machines (FE or Create) as relays, alongside copper, iron and redstone.
- **Kill Switch** aimed at an enrolled machine shuts it down for five seconds, then it wakes as it was.

## Protection and limits

Every drained or stopped machine passes the same claim/protection check as block edits
(`WorldMutationPolicy`), so claim mods and spawn protection keep a base safe. Charging only needs the
normal interaction permission. Pulses never place, break or replace blocks and never open inventories.
Bounds: 128 machines per pulse, 32 fields at once, 64 single-machine shutdowns, drains capped per machine.

## Server settings

`config/heroesevolved-common.toml`, section `[grid]`:

| Key | Default | Meaning |
| --- | --- | --- |
| `affectMachines` | `true` | Pulses drain and stop FE machines; lightning charges them. |
| `affectCreate` | `true` | Pulses seize Create's kinetic blocks. |
| `affectCarriedItems` | `true` | Pulses drain energy items carried by enemies. |
| `maxDrainPerMachine` | `250000` | Most FE one pulse takes from a single machine. |
| `lightningCharge` | `4000` | FE per Chain Lightning bolt; the other numbers above scale from it. `0` turns charging off. |

## Testing

`src/gametest/java/com/jayptucker/heroesevolved/grid/GridGameTests.java` gives furnaces, barrels and
compasses a real Forge Energy capability **in the test world only** (`TestMachines`) and checks draining,
the per-machine cap, stalling and waking, lightning charging, Storm Surge, carried items, Technopathy and
redstone. Create's seizing is not covered by automated tests (Create is not a dependency); try it in a
dev instance with Create installed.
