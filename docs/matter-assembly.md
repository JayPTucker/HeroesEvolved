# Matter Assembly

Matter Assembly treats the world as material. Matter tears up out of the ground (plates rip free all round,
leaving puffs of dust, and fly up in arcs into place with bright seams behind them) and folds into lean,
temporary constructs and physical Shards. It costs stamina, **never blocks from your inventory**, and every
construct uses as few cells as it can: the look comes from the assembly, not from a pile of blocks. It creates
real block collision; its projectiles travel and check every swept segment on the server.

## Core kit

The table uses ability tiers 1–5. These unlock at Power Levels 1, 3, 6, 10 and 15 respectively.

| Tier | Action | Energy / cooldown | What it does |
| --- | --- | --- | --- |
| 1 | Foldwall | 12 / 3 seconds | A three-wide, two-high shield (six cells) two blocks ahead. Buttressed Corners folds a wing back on each side. |
| 1 | Shard Lance | 14 / 1.5 seconds | Tears a Shard from the ground and fires it up to 64 blocks. It stops on terrain or its first valid enemy. Damage is 9.2–14 before ordinary damage rules. |
| 2 | Skywalk | 22 / 8 seconds | For eight seconds, matter catches every step you take on air: a three-wide plate forms under your feet whenever there is nothing there. Walk out over gaps, jump to climb, crouch to drop through. Each plate crumbles a couple of seconds after you leave it. |
| 3 | Compression Cage | 30 / 11 seconds | Targets a visible hostile up to 64 blocks away and closes a tight three-by-three cell round it, two high and roofed (25 cells). Skips occupied cells and lasts eight seconds. Allies, creative players, protected PvP targets and oversized creatures are rejected. |
| 5 | Sentinel Engine | 70 / 50 seconds | Three turrets rise round you, each a two-cell pillar crowned with a turning crystal head, a ring of light and a searching beam. For twenty seconds each finds the nearest enemy it can see within 32 blocks and shoots Shards at it (fourteen each, staggered round the ring), while you stay within 48 blocks. A turret whose head is broken stops. |

Shift plus a core ability key dismantles your shapes. Unstable Lattice can turn up to six dismantled cells into
outgoing Shards. Drawbridge's second cast retracts that crossing. Ordinary mining and explosions can break the
constructs but give no items.

## Choices in the skill tree

The three branches each have six nodes: six optional actives and three mutually exclusive capstones, using the
normal server-validated purchase and loadout system. Explicit IDs and mechanics are in the shared
[skill catalog](../src/main/resources/data/heroesevolved/skill_trees.json).

**Architecture:** Load-Bearing absorbs falls onto your constructs. Switchback folds a narrow staircase through
a right angle; the bend follows which side of the nearest compass direction you aim. Buttressed Corners adds
flanking wings. Drawbridge projects a narrow twenty-block crossing and can retract it. Snap Rails adds guard
posts along Switchback and Drawbridge. Living Blueprint rebuilds a broken Sentinel Engine turret cell every
half-second while you stay near.

**Ballistics:** Bank Shot permits one reflection from your constructed matter. Scattershot fans five
individually colliding Shards. Recall Angle redirects a banked Shard toward a nearby visible enemy. Faultline
bursts pillars up in a travelling line and launches nearby enemies upward. Unstable Lattice releases matter
during deliberate dismantling. Splinter Storm divides a direct Shard hit into up to two fragments aimed at
different visible enemies; fragments never divide again.

**Refuge:** Secret Threshold lets an empty hand open a two-block doorway in your matter, respecting build
permissions. Folded Shelter raises a snug, roofed three-by-three hut round you with a doorway behind. Clean Exit
opens the whole back wall for the team. Rescue Cradle builds a three-by-three floor two blocks below you or an
aimed ally; a non-team player must crouch to consent. Pressure Valve ejects a melee attacker when you are beside
your matter, once per two seconds. Second Foundation can turn a fatal hit into a one-health escape once per
minute by raising an emergency shelter round you; it needs space for at least four cells. It checks damage
after armor and potion reductions and accounts for absorption, so a survivable armored blow does not waste the
rescue. It is not immunity to later hits.

## Matter and world safety

Matter is drawn from the world, never from the player: no inventory block is read or taken, so nothing in the
inventory can be lost or duplicated by the power. The construct is a dedicated `heroesevolved:assembled_matter`
block with no loot table, no BlockItem, no block entity and an immovable piston reaction. Silk Touch,
explosions, cleanup and dismantling do not create resource outputs, so constructs cannot be cashed out into
materials.

Only strict air is eligible. No original block or fluid is converted, and the power cannot destroy a container
as a side effect. Occupied cells are skipped. Build-height, world-border, loaded-chunk, player build rights,
native interaction checks, claim break hooks and placement hooks apply to every cell, including repairs, Skywalk
plates and cells built later by a job. Jobs stop when authorization or PowerRules changes; borrowed casts use
the narrow Mimicry authorization gate. Enemy attacks honor teams, PvP, owned pets, creative/spectator players,
intangibility and intervening cover.

Every cell is recorded as a durable, per-dimension lease containing its coordinate, owner and expiry. Cleanup
removes only a still-matching construct state; later player edits are preserved. Unloaded chunks are never
force-loaded. Expired or cancelled unloaded leases retire from the capacity budget; their orphan blocks clean
themselves when their chunks load. A palette-filtered load check reschedules validation even if an interrupted
save lost the block's scheduled tick. Blocks also validate their lease every ten ticks, so a rewind-restored
construct without provenance deletes itself. A valid lease retains its original expiry through a rewind.

## Performance and lifecycle

Construction begins with at most four cells. Normal jobs place at most 24 cells each per server tick and share a global budget of 96; there are at most 32 jobs and two per owner. Every job has a finite coordinate list. The per-dimension lease cap is 2,048 cells, with 256 per owner, and expiry inspects at most 96 entries per dimension per tick. Owner counts are indexed, and repeated mining/rebuilding does not grow the inspection queue.

There are at most 128 active Shards globally and twelve launch Shards per owner. They move 2.8 blocks per tick, render a small effect every other tick and end at 64 blocks. Secondary fragments and sentinel volleys have hard caps. Sentinel Engine anchors its turrets to the original cast location. Logout, death, dimension changes, power removal and respecialization cancel runtime jobs and remove the owner's loaded geometry; unloaded geometry becomes orphaned and cleans itself on chunk load. Shutdown clears runtime objects. All sounds are vanilla events, preserving the mod's imported audio.

The awakening is a brief amber construction bloom and a visible-enemy repulsion burst. It creates no free blocks. The evolution trial counts forty successful Shard hits.

## Verification

`MatterAssemblyGameTests` exercises building from an empty inventory (stamina and cooldown only), an inventory left untouched, Skywalk catching a step on air, Sentinel Engine's three turrets shooting an enemy, physical wall collision and no loot, cancelled claim placement/refunds, Adventure restrictions and container preservation, lease serialization/expiry/orphan recovery, later-edit preservation, a purchased and equipped Switchback's actual geometry/cost/cooldown, forty-block swept projectile hits, cover/team safety, runtime cancellation after power loss, directservice purchase/tier/rewind gates, disabled PvP, Bank Shot, Drawbridge retraction, and actual armored-survival versus fatal-rescue damage. Test sources are isolated from the released mod by the existing GameTest init script.
