# The Ability Dampener and the Nullifier

Two pieces of Primatech hardware that switch powers off. Both run on **Forge Energy (RF/FE)**, the energy every
tech mod shares: Mekanism, Thermal, Powah, Flux Networks, Create Crafts & Additions and the rest. Heroes Evolved
makes none of its own, so plug them into whatever powers your base. An Electrokinetic's lightning also charges the
projector (Chain Lightning, Live Current, Storm Surge), as it does any machine.

## The Ability Dampener (block)

A slate-grey pylon. While it has power it holds a sphere round itself in which **no power works**: abilities,
sustained powers (flight, a held stream) and passives that follow the usual rules all stop the moment someone steps in,
and start again when they step out. It reaches through walls, like the Company's Suppressor. It affects everyone,
including whoever built it.

| | |
| --- | --- |
| Radius | 4, 8 (default), 12 or 16 blocks |
| Upkeep | 6 FE a tick per block of radius: 24, 48, 72 or 96 FE/t |
| Battery | 500,000 FE (about 8½ minutes at 8 blocks). Takes up to 20,000 FE and gives up to 24,000 FE per transfer |
| Spin-up | One second from off to a full field. The dome grows over it; powers still work until it is full |

- **Use** (empty hand): read its charge and step the radius.
- **Sneak and use** (empty hand): switch it off or on.
- **A redstone signal** switches it off while it lasts (wire it to a lever, a door, a daylight sensor...).
- **A comparator** reads how full its battery is.
- **Broken**, it keeps its stored charge in the item.
- Inside the field you see a faint slate lattice over the sphere and rings sliding in across the ground toward the
  pylon. Crossing the edge tells you so ("The air goes dead...").

Craft it at a crafting table:

```
I H I     I = iron ingot          H = Helixite shard
A R A     A = amethyst shard      R = block of redstone
I B I     B = block of copper
```

### Beating one

- **Cut its power**, or put a redstone signal on it.
- **An EMP** (Induced Radioactivity's EMP, Electrokinesis's Blackout, an EMP grenade) drains its battery and stalls
  it: the field drops at once and stays down while the pulse lasts.
- **Break it.** It needs an iron pickaxe.
- Stand outside the radius. Powers aimed into the field from outside still work; only the people inside lose theirs.

## The Nullifier (TaCZ gun)

A Primatech pistol (TaCZ's Desert Eagle frame, repainted slate with a pale battery cell) that fires **dampener
rounds**. Whoever a round hits loses their power: **8 seconds** for a player, 10 for anything else. A second hit
refreshes the time, it doesn't stack. Milk doesn't wash it out. A round also breaks Sylar's hold and cuts short Sylar's
or Elle Bishop's windup, and leaves them with only their fists (or her footwork) until it wears off. An Empathic Mimic
finds nothing to copy in a dampened body.

It does almost no damage (2). It is for shutting a power off, not for killing.

| | |
| --- | --- |
| Magazine | 6 rounds, semi-automatic |
| Range | about 50 blocks |
| Battery | 24 rounds, 4,000 FE each (96,000 FE full) |

### Charging it

The Nullifier isn't loaded with ammunition items: **its rounds are made from the energy in its own battery**, and a
reload pulls them out of the battery (the HUD counts them). Dampener rounds can't be crafted. To charge it:

- **Use an Ability Dampener with it** (with a gun in hand, use TaCZ's *Interact With Gun* key, **O** by default):
  up to six rounds a use, taken from the projector's battery.
- **Sneak and use any other FE machine or battery** with it (Interact key while sneaking): it drinks what it can.
- **Put it in any RF charger**: a Mekanism chargepad or energy cube, a Flux Point, a Powah energizer... The gun is a
  Forge Energy item to every mod.

Its tooltip shows the battery. An EMP drains it, as it drains any battery a foe carries.

Craft it at TaCZ's gun smith table, *Pistol* tab: 16 iron, 8 copper, 4 Helixite shards, 6 amethyst shards and a block
of redstone.

## For builders and modders

- `dampener/Dampening.dampened(entity)` is the one question: inside a live field, or struck by a round. `PowerRules`
  asks it for players; Sylar, Elle and `MimicryService.imprintOf` ask it too.
- `dampener/AbilityDampenerBlockEntity` holds the battery (exposed on every side through NeoForge's
  `Capabilities.EnergyStorage.BLOCK`) and pays the upkeep each tick. The field is live only while it has paid this tick
  or the last, so a stall (an EMP) drops it with no extra bookkeeping.
- `dampener/Nullifier` is the gun's battery. It uses TaCZ's own internal reserve ("dummy ammo") as the store of
  rounds, so TaCZ reloads from it; part-made rounds are kept in the stack's custom data. The gun's Forge Energy
  capability is registered on TaCZ's gun item and answers only for the Nullifier.
- Numbers live in `dampener/DampenerGeometry`; the gun's handling in
  `data/heroesevolved/data/guns/dampener_gun_data.json`.
- `DampenerArt` draws the field, the shot, the hit, a dampened body's collar and the gun charging.
- Textures: `java tools/GenerateDampenerTextures.java` (block and effect icon) and
  `java tools/GenerateWeaponTextures.java` (the gun and its round).
- Tests: `DampenerGameTests`. Photographs (`uitest/DampenerPreview`, saved to `build/dampener-preview/screenshots/`):

```bash
./gradlew runClient --init-script tools/dampener-preview.init.gradle
```
