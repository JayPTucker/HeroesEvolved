# Company agents

The Company's agents first appeared in the earlier Night Three mission. They remain available as
`heroesevolved:company_agent` entities. The look, lines and numbers are original game adaptations.

## Guards

A guard stands a post and stays near it, going no more than ten blocks after anyone. He turns on any
stranger he can see within sixteen blocks (anyone in survival or adventure), says so once, and shoots.

- **The shot is aimed, not instant.** He stops, raises the rifle to his shoulder, and a thin red line
  runs from the barrel toward you for most of a second (16 ticks). Then he fires. Two seconds later he
  can aim again.
- **Break his line of sight** while he is aiming and the shot never comes: he walks to find you
  again. **Move fast** and it may miss: at a sprint about half of his darts miss, and at anything
  faster than half a block a tick (Superspeed, fast Flight) most of them do.
- **Up close** (within two and a half blocks) he puts the rifle down and hits you with it.

### Tranquilizer darts

Each dart that lands adds a dose of **Tranquilized** for ten seconds, and each dose makes you slower.
Another dart before it wears off deepens it. The **third dose puts you under**:

| Where you are | Going under |
| --- | --- |
| In the past (Night Three) | You are thrown back to the present, as lethal harm would. |
| Anywhere else | Blind and barely able to move for six seconds, weak for twelve. Agents leave you alone for ten seconds afterwards. |

Nobody dies of a dart. In the original Night Three encounter, Gray's telekinesis deflects darts
unless he is staggered, busy opening a pod, or inside the Suppressor's field.

## The Suppressor

The man in the gray suit carries no rifle and stands with his hands folded. Within **nine blocks** of
him **no power answers**, a player's or Gray's, through walls, as during the Eclipse. A slow ring of
grey motes on the floor marks the edge of his quiet, and trying to use a power inside it says why. Up
close he still hits you. Put him down and the quiet lifts at once.

## In the past

An agent in the past is **non-lethal**: nobody from the present can kill anyone there. A blow that would
kill him puts him on the floor instead, out cold for the rest of the night. Out cold, he gives no
orders, takes no more blows, and his field (if he is the Suppressor) is gone. In the present, agents
are as mortal as anyone.

## Using them

```text
/summon heroesevolved:company_agent
/summon heroesevolved:company_agent ~ ~ ~ {role:"SUPPRESSOR"}
```

summon a guard and a Suppressor, standing watch where they appear. From code, `CompanyAgentEntity.create(type, level, role, position, yaw, nonLethal)`
returns an agent at his post that hasn't been added to the world yet, so a scene can tag him first.
Agents fire on any Sylar tagged `heroesevolved_loose`. `Tranquilizer.onGoingUnder` lets a place decide
what going under means there, and `Tranquilizer.onDeflect` lets a target turn darts aside.

The Company also sends [Elle Bishop](elle-bishop.md) for awakened players, with a day of omens before she comes.

The skins and the effect icon are drawn by `tools/GenerateCompanyTextures.java`:

```bash
java tools/GenerateCompanyTextures.java
```

`CompanyAgentGameTests` covers a guard seeing, aiming at and darting a stranger, darts deepening and the
third putting you under, fast movement making darts miss, agents going down instead of dying in the
past, and the Suppressor's quiet.
