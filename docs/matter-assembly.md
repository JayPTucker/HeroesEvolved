# Matter Assembly

Matter Assembly turns ordinary inventory supplies into temporary, playable architecture. The player chooses a firing angle, builds a crossing, seals an enemy in a cell, opens a hidden door in their own cover, or folds their fortress back into a Shard attack. It creates real block collision; its projectiles travel and check every swept segment on the server.

## Core kit

The table uses ability tiers 1–5. These unlock at Power Levels 1, 3, 6, 10 and 15 respectively, so the two opening moves are available together and Cathedral arrives at Power Level 15.

| Tier | Action | Energy / cooldown | What it does |
| --- | --- | --- | --- |
| 1 | Foldwall | 12 / 3 seconds | Builds a five-wide, three-high wall three blocks forward. Buttressed Corners adds return wings. |
| 1 | Shard Lance | 14 / 1.5 seconds | Consumes three blocks to fire a physical Shard up to 64 blocks. It stops on terrain or its first valid enemy. Damage is 9.2–14 before ordinary damage rules. |
| 2 | Causeway | 22 / 8 seconds | Grows a three-wide, twelve-block crossing from the caster's feet. Look upward to climb, downward to descend; it rises or falls once every two forward cells. Existing terrain is skipped. |
| 3 | Compression Cage | 30 / 11 seconds | Targets a visible hostile up to 64 blocks away. Builds a hollow five-wide enclosure with a roof, skips occupied cells and lasts eight seconds. Allies, creative players, protected PvP targets and oversized creatures are rejected. |
| 5 | Cathedral Engine | 70 / 50 seconds | Builds a crenellated fortress with a rear gate around the original cast coordinates. Its shell lasts fifteen seconds and fires up to four defensive Shards at visible nearby enemies while its owner remains within 32 blocks. |

Shift plus a core ability key dismantles the owner's shapes without returning their input materials. Unstable Lattice can turn up to six dismantled cells into outgoing Shards. Drawbridge's second paid cast retracts that particular crossing. Ordinary mining and explosions can break the constructs but give no items.

## Choices in the skill tree

The three branches each have six nodes. There are six optional actives and three mutually exclusive capstones, using the normal server-validated purchase and loadout system. Explicit IDs and mechanics are in the shared [skill catalog](../src/main/resources/data/heroesevolved/skill_trees.json).

**Architecture:** Load-Bearing absorbs falls onto the owner's constructs. Switchback bends a real staircase through a right angle; the direction of the bend follows which side of the nearest compass direction the player aims. Buttressed Corners adds flanking cover. Drawbridge projects a narrow twenty-block crossing and can retract it. Snap Rails adds paid guard posts to Causeway and Switchback. Living Blueprint repairs empty holes in Cathedral one cell per half-second, consuming a new block for every repair.

**Ballistics:** Bank Shot permits one reflection from the owner's constructed matter. Scattershot fires five individually colliding Shards for five input blocks. Recall Angle redirects a banked Shard toward a nearby visible enemy. Faultline grows a sequence of paid ground pillars and launches nearby enemies upward. Unstable Lattice releases already-paid matter during deliberate dismantling. Splinter Storm divides a direct Shard hit into up to two small fragments aimed at different visible enemies; fragments never divide again.

**Refuge:** Secret Threshold lets an empty hand open a two-block doorway in the owner's matter, respecting build permissions. Folded Shelter raises a roofed refuge with a rear doorway. Clean Exit widens that doorway for the team. Rescue Cradle builds a three-by-three floor two blocks below the caster or an aimed ally; a non-team player must crouch to consent. Pressure Valve ejects a melee attacker when the owner is beside their constructed matter, once per two seconds. Second Foundation can turn a fatal hit into a one-health escape once per minute by spending inventory supplies on an emergency shelter; it needs at least twelve spare blocks and four successful placements. It checks damage after armor and potion reductions and accounts for absorption, so a survivable armored blow does not waste the rescue. It is not a free resupply or immunity to later hits.

## Material conservation and world safety

Each successfully placed cell consumes exactly one ordinary inventory block, including additional wings, rails and repairs. Shard Lance consumes three; Scattershot consumes five. Material selection uses the main player inventory, never a remote chest, a backpack, a block entity or a capability-backed container. Only unmodified stacks of these vanilla items qualify:

- Stone, cobblestone, deepslate, cobbled deepslate, granite, diorite, andesite, tuff.
- Dirt, coarse dirt, sandstone, red sandstone, netherrack, blackstone, basalt.
- Oak, spruce, birch, jungle, acacia, dark oak, mangrove and cherry planks.

Ores, gems, metals, containers, sand/gravel, concrete powder, logs and personalized/component-bearing stacks are excluded. The construct is a dedicated `heroesevolved:assembled_matter` block with no loot table, no BlockItem, no block entity and an immovable piston reaction. Silk Touch, explosions, cleanup and dismantling do not create resource outputs. A cancelled placement returns only its one reserved input block.

Only strict air is eligible. No original block or fluid is converted, and the power cannot create valuable matter or destroy a container as a side effect. Occupied cells are skipped. Build-height, world-border, loaded-chunk, player build rights, native interaction checks, claim break hooks and placement hooks apply to every cell, including repairs and cells built later by a job. Jobs stop when authorization or PowerRules changes; borrowed casts use the narrow Mimicry authorization gate. Enemy attacks honor teams, PvP, owned pets, creative/spectator players, intangibility and intervening cover.

Every cell is recorded as a durable, per-dimension lease containing its coordinate, owner and expiry. Cleanup removes only a still-matching construct state; later player edits are preserved. Unloaded chunks are never force-loaded. Expired or cancelled unloaded leases retire from the capacity budget; their orphan blocks clean themselves when their chunks load. A palette-filtered load check reschedules validation even if an interrupted save lost the block's scheduled tick. Blocks also validate their lease every ten ticks, so a rewind-restored construct without provenance deletes itself. A valid lease retains its original expiry through a rewind; no resource refund is generated. Rewind may restore the inventory's historical contents according to the time power's normal rules, but generated matter cannot be cashed out into extra permanent materials.

## Performance and lifecycle

Construction begins with at most four cells. Normal jobs place at most 24 cells each per server tick and share a global budget of 96; there are at most 32 jobs and two per owner. Every job has a finite coordinate list. The per-dimension lease cap is 2,048 cells, with 256 per owner, and expiry inspects at most 96 entries per dimension per tick. Owner counts are indexed, and repeated mining/rebuilding does not grow the inspection queue.

There are at most 128 active Shards globally and twelve paid launch Shards per owner. They move 2.8 blocks per tick, render a small effect every other tick and end at 64 blocks. Secondary fragments and fortress volleys have hard caps. Cathedral anchors its effects and firing point to the original cast location. Logout, death, dimension changes, power removal and respecialization cancel runtime jobs and remove the owner's loaded geometry; unloaded geometry becomes orphaned and cleans itself on chunk load. Shutdown clears runtime objects. All sounds are vanilla events, preserving the mod's imported audio.

The awakening is a brief amber construction bloom and a visible-enemy repulsion burst. It creates no free blocks. The evolution trial counts forty successful paid Shard hits.

## Verification

`MatterAssemblyGameTests` exercises real inventory payment, no-material action rejection without energy/cooldown loss, whitelist/component restrictions, physical wall collision and no loot, cancelled claim placement/refunds, Adventure restrictions and container preservation, lease serialization/expiry/orphan recovery, later-edit preservation, a purchased and equipped Switchback's actual geometry/cost/cooldown, forty-block swept projectile hits, cover/team safety, runtime cancellation after power loss, directservice purchase/tier/rewind gates, disabled PvP, Bank Shot, Drawbridge retraction, and actual armored-survival versus fatal-rescue damage. Test sources are isolated from the released mod by the existing GameTest init script.
