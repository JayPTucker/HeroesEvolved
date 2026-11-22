# Fighting styles

Eleven powers fight hand to hand on the same controls. Pyrokinesis (firebending), Electrokinesis (the lightning style) and
Hydrokinesis (waterbending) have their own pages: [pyrokinesis.md](pyrokinesis.md#firebending-fists-feet-and-dodges),
[electrokinesis.md](electrokinesis.md) and [hydrokinesis.md](hydrokinesis.md#waterbending-the-fighting-style). This
page covers Superspeed, Quake, Cryokinesis, Density, Regeneration, Invisibility and
Telekinesis. [Vibe](vibe.md) also uses these controls. Every strike and counter is reviewed in
[Stamina and balance](stamina-balance.md#every-damage-review), including repeated hits and burning.

## The controls

- **Strike:** left-click with an empty hand (clicking a block still digs it; holding an item fights the ordinary way).
  Clicks within about a second make a combo: the **lead** hand, the **rear** hand, and from Level 2 a **finisher** on the
  third click.
- **Low move:** crouch and click (Level 2).
- **Dodge:** double-tap A or D. From Level 2, double-tapping S is the style's **back dodge**.
- **Evasion:** for the first 8 ticks of a dodge, blows from creatures, players and projectiles miss you, and your
  landing is cushioned for two seconds.
- **Counter:** the first blow a dodge evades loads the next strike. Click within two seconds and the rear hand throws
  the style's counter strike for free.
- **Cost:** 2 stamina a strike, 5 for a finisher or low move, 8 a dodge (11 for the back dodge), 18 ticks between
  dodges. The numbers are under `fightingStyles` in the common config; the client config's `fightingStyleStrikes` and
  `fightingStyleDodges` turn the controls off.
- **Mimicry:** Peter fights in the style of whatever power he is channelling, near or kept.

## Blur Boxing (Superspeed)

| Move | What happens |
| --- | --- |
| **Jab** (lead, rear) | Three blows too fast to see, each from a slightly different angle with an afterimage at each, 4.5 blocks. |
| **Blitz** (finisher) | A streak of gold closes the gap to the foe in front (up to 9 blocks) and you skid to a stop square in front of them as the blow lands (0.8 + 0.2 per level) and drives them back. You never end up behind them. |
| **Skid Sweep** (low) | A slide in under them that takes their legs (0.6 + 0.2 per level) and slows them. |
| **Skid Turn** (left, right) | You're thrown sideways at speed, about five blocks, crouched low and leaning away from the slide with your trailing hand flat on the ground, sliding the last stretch to a stop. |
| **Skid Stop** (back) | You're thrown backward, turn side-on and drop to one knee with your lead hand dragging on the ground in front, sliding to a halt about six blocks back (eight and a half from level 2). |
| **Too Slow** (counter) | Six blows from afterimages circling the attacker. |

Every dodge moves your real body; nothing teleports. Most of the ground goes in the first few ticks and the rest is a
short slide, with dust and sparks thrown up where your hand drags, a gold streak while you're fast, and Superspeed's
stop gust when you halt (two scorched skid marks behind you, dust billowing on). The evasion window and counter are the
same as every style's.

## Pressure Palm (Quake)

Daisy doesn't punch; she pushes the air. Every palm adds Strain, and a palm on someone frozen solid shatters the ice for
double.

| Move | What happens |
| --- | --- |
| **Palm** (lead, rear) | An open palm drives a short tube of pressure rings into the foe in front, 5 blocks, harder the more Strain she carries. |
| **Double Palm** (finisher) | Both palms at once: a wide shove (6 blocks, 38° each side) that sends a crowd flying. |
| **Ground Punch** (low) | A fist into the ground; a small fault cracks open round her (4 blocks) and throws everyone up. |
| **Air Kick** (side) | She kicks off the air itself, leaving a ring where she pushed. |
| **Recoil Leap** (back) | A shockwave fired forward throws her back. |
| **Rebound** (counter) | The evaded blow goes straight back into the attacker, and her next palm is heavier. |

## Ice Skater (Cryokinesis)

| Move | What happens |
| --- | --- |
| **Frost Jab** (lead, rear) | An ice-cased fist that chills, 4.5 blocks. A Soaked foe freezes solid at once. |
| **Ice Uppercut** (finisher) | A spike of ice drives up under the foe in front (6 blocks) and throws them. |
| **Frost Ring** (low) | A ring of frost pressed into the ground slows everyone within 4 blocks. |
| **Skate** (side) | You skate sideways on a strip of glittering ice that lingers for three seconds and chills any foe standing on it. |
| **Spin Glide** (back) | A spinning backward glide that throws frost as it turns. |
| **Cold Snap** (counter) | The attacker freezes solid, and the next jab shatters them for double. |

## Tiger and Crane (Density)

Density's earthbending, after Hung Gar. Every blow pulls rock up out of the ground into the hand, and an earthbender
doesn't hop out of the way: the side "dodge" is the ground carrying you, braced. Every blow is a quarter harder at full
Root (see [density.md](density.md#root-and-footing)).

| Move | What happens |
| --- | --- |
| **Tiger Claw** (lead) | A raking claw with stone closing over the fist, 4 blocks. |
| **Gauntlet Claw** (rear) | The rear fist lands in a full stone gauntlet: 40% harder and a bigger shove. |
| **Twin Slab** (finisher) | Both palms drive a slab of ground out ahead into everything in front of you (5 blocks), knocking it back. |
| **Horse Stomp** (low) | A ring of stone spikes stabs up round you (3.5 blocks): hurt, slowed and bounced. |
| **Earth Slide** (side) | The ground carries you sideways, upright and braced: a blow taken in it lands at half strength. |
| **Sink and Surface** (back) | You sink into the floor and come up a few blocks away; blows miss you under it. |
| **Neutral Jing** (counter) | The first blow taken in an Earth Slide: a pillar answers the attacker at once, and the next claw goes to them, half again as hard. |

## Reckless Brawler (Regeneration)

Claire can't stay hurt, so she doesn't fight like someone who can. Every swing costs her a point of health (never below
two), and she hits up to half again as hard the more hurt she already is. A broken arm can't swing. While she is
Indestructible, Reckless adds to every blow.

| Move | What happens |
| --- | --- |
| **Hook / Haymaker** (lead, rear) | A heavy swing, 4 blocks, 0.8 + 0.2 per level times her rage. |
| **Headbutt** (finisher) | The foe reels: weak, slowed and dizzy for a few seconds. It costs her two health. |
| **Tackle** (low) | Shoulder down, she charges; the first foe in 5 blocks is bowled over and slowed. |
| **Brace** (any direction) | She doesn't dodge. For most of a second, every blow she takes lands at half strength. |
| **Payback** (counter) | The first braced blow loads her next swing with 80% of everything she took during the brace. |

## Force Palm (Invisibility)

While invisible, the lens is the only thing an enemy sees of the blow.

| Move | What happens |
| --- | --- |
| **Lens Jab** (lead, rear) | A small field lens on the knuckles flashes and cracks on impact, 5 blocks. |
| **Force Burst** (finisher) | A fan of lenses thrown from both palms (6.5 blocks, 40° each side) hurls everything in front. |
| **Field Slam** (low) | A dome of force snaps out along the ground, 4 blocks. |
| **Shield Slide** (side) | You slide behind a pane raised on the side the blow would come from. |
| **Bounce** (back) | A pane under your feet throws you up and over backward like a trampoline. |
| **Pressure** (counter) | The evaded blow is stored in the lens; the next jab lets it go as five shards. |

## Puppeteer (Telekinesis)

A telekinetic never lays a hand on anyone.

| Move | What happens |
| --- | --- |
| **Swat** (lead, rear) | A flick of the hand and the foe in sight, out to 12 blocks, is batted sideways, left then right. Anyone they are batted into is hurt too. |
| **Lift and Slam** (finisher) | The foe is lifted into the air and slammed down a moment later. |
| **Repulse** (low) | Everyone within 4.5 blocks is pushed away. |
| **Shove** (side) | You shove yourself sideways. |
| **Float Back** (back) | You float up and back. |
| **Grip** (counter) | The attacker hangs helpless in the air for a second and a half, and the next swat throws them twice as hard. |

## For developers

- Each style is one class on `combat/ComboStyle`, which holds the combo, the dodge, the counter and the costs; a style
  writes only its moves (`throwMove`, `dodgeMove`, `onCounter`, optionally `tickFighter`, `evadeTicks` and `braced`).
  `combat/StyleHits` has the shared aiming, hurting and shoving. See AGENTS.md, "Reusable patterns from the shared
  fighting styles".
- Tests: `combat/FightingStylesGameTests`. Photographs: `tools/fighting-preview.init.gradle` (flag
  `heroes.preview.fighting`) shoots every style into `build/fighting-preview/screenshots/`. With `-Pinput` it instead presses the real keys for all ten
  styles (three clicks, a double-tapped right and back) and prints a PASS or FAIL for each.
