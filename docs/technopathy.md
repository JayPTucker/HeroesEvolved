# Technopathy

Technopathy turns a prepared physical circuit into battlefield machinery. It does not provide a lightning beam, spawn turrets, steal inventories, accelerate a laboratory's recipe, or grant machine ownership. An operator can open an evacuation corridor, arrange a delayed bank of real switches, winch an intruder into a prepared line, and turn hostile arrows into stored recoil.

## Core controls

| Power Level | Action ID | Name | Energy | Cooldown | Behavior |
|---|---|---|---:|---:|---|
| 1 | `technopathy_handshake` | Handshake | 8 | 1 second | Aim at copper, iron, a supported redstone device or a laboratory machine. Enrol up to 24 physically connected nodes for two minutes. Shift clears your circuit. |
| 1 | `technopathy_remote_hand` | Remote Hand | 5 | 0.6 seconds | Aim at an enrolled lever, button, door, fence gate, trapdoor or copper bulb and operate its real state. Vanilla redstone and scheduled button release still work. |
| 3 | `technopathy_tripwire` | Tripwire | 20 | 3.5 seconds | Aim at an enrolled node. A visible line connects it to the furthest clear enrolled node within 24 blocks. One hostile crossing takes physical recoil damage, a brief stumble and knockback. Up to three lines. Shift clears them. |
| 6 | `technopathy_intercept` | Intercept | 30 | 12 seconds | Aim at a relay. A five-block field above it diverts incoming arrows from hostile living shooters for ten seconds. It preserves the projectile and its normal damage. Shift ends it. |
| 15 | `technopathy_machine_city` | Machine City | 85 | 60 seconds | For twelve seconds, up to eight connected relays intercept projectiles and deliver one recoil trap to each hostile intruder. The district stays where it was built. Shift ends it. |

Core tiers correspond to Power Levels 1, 3, 6, 10 and 15. This kit has two actions at Level 1 and saves its final core action for Level 15; Level 10 opens the second active skill in every branch. The Level 20 Evolved trial is to intercept **40 hostile arrows** with the power. Scanning machines and the awakening pulse do not count.

Power suppression and immobilization stop the network. Mimicry can borrow the first three core actions through its Echoes without taking unique ownership. Optional skills must be learned in the Technopathy tree.

## Physical circuits

Copper blocks and grates, iron blocks and bars, chains, lightning rods, redstone wire, repeaters, comparators, observers, pistons, dispensers, hoppers, redstone lamps/blocks, supported controls, the centrifuge and synthesis station can relay Handshake, and so can **powered machines from other mods**: anything storing Forge Energy (Mekanism, Thermal and the rest) and Create's kinetic blocks. Only the six explicit control families above can be changed. **Kill Switch** aimed at an enrolled powered machine shuts it down for five seconds (it stops ticking, or Create's shafts stop turning), then it wakes exactly as it was; see [Powers and machines](machines.md). There is no inventory/menu access or generic block-entity/NBT mutation. Handshake follows cardinally adjacent blocks; **Air Gap** permits a single genuinely empty block between conductors.

Place conductor blocks in a floor with clear space above them for useful tripwire/Winchline/defense anchors. A wall between an anchor and its target blocks that effect. Networks are tied to their starting block, limited to a 32-block graph radius and an 80-block owner tether, and re-read once per second. Sever a cable or remove the root to break the district. Power loss, suppression, death, logout, a dimension change, a new Handshake and server shutdown discard the session.

## The skill tree

The complete 18-node, three-branch tree is in the shared [skill catalog](../src/main/resources/data/heroesevolved/skill_trees.json) and the inventory Abilities page. Each branch contains two optional active abilities, three mechanical passives and one exclusive capstone.

- **Operator:** Air Gap connects separated conductors. Batch Instruction operates six real controls at once; Shift sends OFF/CLOSED. Safety Interlock prevents closing on a living body. Delayed Instruction queues a toggle three seconds ahead. Echo Instruction unwinds it two seconds later. The Clockwork capstone turns that instruction into a staggered sequence across six devices.
- **Sentinel:** Motion Sensor catches fast crossings between checks. Sentry Seal is a single-use proximity trap that lasts thirty seconds. Interrupt Request interrupts item use and sprinting. Kill Switch temporarily turns off a controller and safely restores it. Black Box converts an actual interception into a second charge on your next tripwire. The Airlock capstone temporarily closes nearby real doors when a trap catches an intruder.
- **Kinetic Logic:** Flywheel stores intercepted arrow momentum for the next trap's recoil. Winchline draws a hostile body towards a relay. Counterweight adds a Shift mode that winches you along your prepared route. Domino Engine sends a mechanical pulse through twelve nodes. Recoil Circuit makes that pulse push along the links. The Return to Sender capstone redirects clear shots back at their hostile shooter with your damage attribution.

## Protection and performance

Every enrolled node checks `WorldMutationPolicy`; each actual operation checks it again, plus the normal right-click protection event. Both halves of a door are checked before opening/closing. Closing a device never encloses its operator; Safety Interlock extends that check to all living bodies. Temporary commands remember only one device boolean, restore only that same block in the expected state, and cancel on intervening changes. No item contents or old block snapshots are restored.

Projectile interception excludes ownerless arrows, allied shooters, tridents and stationary/in-ground arrows. It is limited to 12 arrows per check and 64 unique arrows per network lifetime. Only actual hostile arrow interceptions advance the Evolved trial; visual scans, toggles and the awakening pulse do not. Living effects enforce server PvP/team rules, tamed-owner safety and line of sight from their physical relay.

There are no chunk tickets, no spawned helper entities and no persistent movement/AI flags. Hard ceilings are 64 sessions, 24 nodes, 160 graph visits per refresh, three wires, 18 queued/temporary commands, twelve Domino steps, eight Machine City anchors and sixteen candidate living bodies per local effect. Effects send a few world-anchored circuit links and cage refreshes at ten- or twenty-tick intervals. Existing imported sounds are unchanged; Technopathy uses quiet vanilla chimes/clicks and one vanilla awakening cue.
