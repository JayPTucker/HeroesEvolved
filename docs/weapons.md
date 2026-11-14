# Power-themed weapons (TaCZ)

Heroes Evolved requires [TaCZ (Timeless and Classics Zero)](https://modrinth.com/mod/timeless-and-classics-zero)
for NeoForge 1.21.1 (the official 1.2.0 build). Its gun smith table makes four weapons that imitate the powers, a
little weaker than the real thing, and the Company's guards carry TaCZ guns.

All of them are crafted at TaCZ's **gun smith table**, in its usual tabs.

| Weapon | Tab | Materials | What it does |
| --- | --- | --- | --- |
| **Thermal Projector** (heat gun) | SMG | 20 iron, 12 copper, 4 blaze rods, 2 magma cream | A short stream of fire, about ten blocks long, at ten rounds a second. Every round that lands sets the target alight and keeps it burning a second longer, up to five seconds. It thaws anything frozen. |
| **Cryo Carbine** (cold gun) | Rifle | 24 iron, 8 packed ice, 2 blue ice, 2 diamonds | Frost bolts, one a time, accurate to forty blocks. Each bolt adds frost: the target is slower with every hit, and about the fifth freezes it solid for two seconds (one for a player). Bolts that hit water skin it with ice that melts again. |
| **Company Taser** | Pistol | 8 iron, 6 copper, 8 redstone, 2 yellow dye | One cartridge, eight blocks. A hit locks the target up: barely moving, no jumping and no landing blows for two and a half seconds (a second and a half for a player). |
| **EMP Grenade** (×2) | Explosives | 4 iron, 6 copper, 1 block of redstone, 2 gunpowder | Throw it (use). It sticks where it lands, beeps for a second, and pulses. |
| **Nullifier** (dampener pistol) | Pistol | 16 iron, 8 copper, 4 Helixite shards, 6 amethyst shards, 1 block of redstone | Dampener rounds, six to a magazine: whoever one hits loses their power for eight seconds. It is charged with Forge Energy, not loaded with ammo. See [ability-dampener.md](ability-dampener.md). |

Their ammunition is made at the same table, in the *Alternative projectiles* tab (the Nullifier makes its own rounds
from the RF in its battery):

| Ammo | Materials | Makes |
| --- | --- | --- |
| Fuel Cell | 1 iron, 1 blaze powder, 2 coal | 20 |
| Cryo Cell | 1 iron, 1 packed ice, 4 snowballs | 10 |
| Taser Cartridge | 2 copper, 2 redstone, 2 string | 4 |

The guns load, reload, aim and inspect like TaCZ's own: they borrow the shapes and animations of TaCZ's P90 (the
Thermal Projector), AUG (the Cryo Carbine) and CZ75 (the taser), painted in their own colours (soot and copper with
an orange fuel cell; white ceramic with a cyan cryo cell; safety yellow). They fire with a muffled report; the
Projector roars and the Carbine cracks like ice.

## How they compare with the powers

- **Heat gun vs Pyrokinesis.** The burn tops out at five seconds and there is no spreading fire, no Firestorm, no
  Heat to build. Like Pyrokinesis it undoes cold: a frozen target thaws at the first round.
- **Cold gun vs Cryokinesis.** The same frost: the same slowing, the same frost overlay, the same ice shell that
  shatters when hit. But a gun's freeze is shorter (two seconds for a creature, one for a player, against four and a
  bit from the power), and a Cryokinetic is immune to it, as to all frost.
- **Taser vs Electrokinesis.** One target, one shot, then a reload. An Electrokinetic shrugs off half of it.
- **EMP grenade vs the EMP.** The same pulse as Induced Radioactivity's EMP and Electrokinesis's Blackout
  (`GridDisruption.emp`), smaller: ten blocks for eight seconds instead of sixteen for ten. Redstone dies, and with it
  every lamp, door, piston and circuit it powers; Forge Energy machines are drained and stop; Create's shafts seize.
  Everything wakes as it was. Creatures caught in it (not the thrower or their friends) are dazzled for three seconds,
  and an Electrokinetic loses all their stored Charge.

TaCZ's default pack has no EMP grenade and no taser, so both are ours. The EMP grenade is a thrown item rather than a
TaCZ gun, crafted at the gun smith table through a `custom` recipe result.

Players are hit only where PvP is on, like every other power.

## Company agents

A Company guard in the present carries a TaCZ **Glock 17** and the **Company Taser** (see
[company-agents.md](company-agents.md)). At range he raises the pistol (the same red sight-line as the dart rifle)
and fires a pair, then waits two seconds; he changes magazines when he runs dry. Anyone who comes within seven blocks
gets the taser instead, then he swaps back to the pistol while it recharges for four seconds. A guard held by a taser
does nothing until it wears off. In the past (Night Three) guards keep their tranquilizer rifles: nobody may be killed
there.

## For builders and modders

- The guns are a TaCZ gun pack in our own jar: `data/heroesevolved/index/guns`, `data/heroesevolved/data/guns`,
  `data/heroesevolved/index/ammo`, `data/heroesevolved/recipe` (the gun smith table recipes) and
  `assets/heroesevolved/display`. Change numbers there; no code is needed.
- `weapons/PowerWeapons` holds the effects and the ids, `weapons/Stun` the taser's hold, `weapons/EmpGrenade` the
  grenade, and `weapons/TaczLink` is the only class that touches TaCZ.
- `PowerWeapons.gun(id)` makes a loaded TaCZ gun; `PowerWeapons.draw(mob)` and `PowerWeapons.fire(mob, at)` let any
  creature shoot one.
- The textures and icons are drawn by `tools/GenerateWeaponTextures.java` from the borrowed models' geometry (no
  pixel of TaCZ's own textures is used):

```bash
java tools/GenerateWeaponTextures.java
```

`WeaponGameTests` covers the pack loading with its recipes, heat, cold and taser rounds (called directly and through
real TaCZ shots), the EMP grenade killing redstone, and armed guards shooting at range and tasing up close. The
preview stages a firing range 1500 blocks from spawn:

```bash
./gradlew runClient --init-script tools/weapons-preview.init.gradle
```
