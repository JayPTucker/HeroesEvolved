# Minecraft Comes Alive

Heroes Evolved works with **Minecraft Comes Alive** (MCA Reborn for NeoForge 1.21.1), the mod that turns villagers into
people with names, faces, families and homes. MCA is optional: the mod plays the same without it.

## What changes when MCA is installed

- **MCA's people are villagers to every power and event.** They can carry a gift, wake as guardians or turn renegade,
  melt down, overload, be carried to safety, be questioned by the Company, and be counted and remembered by their village.
- **People the mod brings in arrive as MCA's people.** Caravan drovers, travelling healers, refugee families and the
  newcomers who move into a village's new houses get an MCA face, clothes and name, and keep any gift they carry.
- **Story scenes keep their own cast.** The people of the Last Day, the Avenue, the bunker's sleepers, the Last Signal
  and the facility's captives stay ordinary villagers, so every scene plays as written. They look like vanilla villagers
  even when the rest of your world is human.
- **Your MCA settings are respected.** If MCA is set not to replace villagers, in a dimension or a share of villages, the
  people Heroes Evolved brings in there stay vanilla too.

## Using a villager

| You do this | What happens |
| --- | --- |
| Use them | MCA's menu opens, as usual |
| Use them holding what a guardian asked for | The favour is handed over, and the menu stays shut |
| Use them with a power that acts on touch | The power acts first, and the menu stays shut |
| Crouch and use them, with a power that can carry | You pick them up |
| Crouch and use them, otherwise | You trade with them |

## Adding MCA to an existing world

Villagers already in your world stay vanilla until an admin runs MCA's own command:

```
/mca-admin convertVanillaVillagers <radius>
```

Anyone it converts keeps their UUID, job, trades, reputation and gift. Anyone busy at that moment stays as they are, so
nothing in progress breaks: story casts, travellers, Company prisoners, couriers, and anyone being carried, melting down
or at the heart of a storm. Run the command again once they are free.
