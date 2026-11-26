# How to make the best boss fights

The design method for every boss and mini-boss: what the best Minecraft boss mods and the action games they learn from
have in common, turned into rules for this mod. The user set the goal in November 2026: **good animations, and fight
scenes the abilities are actually useful for, to where you'll need them to beat these monsters.** Read this before
writing a boss brief ([boss-brief.md](boss-brief.md)); the facts behind it, mod by mod, are in
[boss-mods.md](boss-mods.md), and the lessons from our own bosses (the Wither, the Living Seal, Sylar) are in
[built-bosses.md](built-bosses.md).

Sources studied: L_Ender's Cataclysm, Bosses'Rise, Bosses of Mass Destruction, Wither Reincarnated, Beyond Bosses 2,
Epic Fight: Bosses of Miracle and the Epic Fight combat mod, Souls-like Bosses, Mowzie's Mobs, Apotheosis; Dark Souls 3's
attack anatomy, a combat designer's boss tips, and Zelda's "the dungeon's item beats the boss" (links at the end).

## 1. What the best fights share

| Pattern | Who does it | For us |
| --- | --- | --- |
| **The fight is a sequence of objectives, not a health bar.** Invulnerability is gated, and each gate is a job | Obsidilith's runes, the Gauntlet's eye, the Void Blossom's healing flowers and bramble cages, Helvar's soul-wisp shield, the Kraken's tentacles and cannons | Every phase has two or three jobs, and powers are the best way to do them (section 2) |
| **Every attack is read before it lands,** by the body and by a mark on the ground, with a sound | all of them; Bosses of Mass Destruction starts every ground attack with a glow | the shared geometry rule; section 3 |
| **Distance decides the move.** A ring of attacks for every range, so there is no safe distance | Obsidilith (Burst within 6, Spike past 6), Void Blossom (its worst attacks only inside 21), Helvar (Thrust past 10, Jump Spin past 15, Sword Waves at range) | an answer for close, mid, far and up high (the Living Seal's lesson) |
| **Thresholds add a mechanic, not just numbers.** 75/50/25%, or 85/70/50 | Bosses of Mass Destruction (a new attack or objective at each quarter), Bosses'Rise (Helvar's health gates, Skor's enrage at 50%) | each phase changes the room, the moves and what the player must do |
| **Windows are earned and read from the body** | Helvar's sword sticks in the floor for 3.5 s after three heavy swings; a glowing mark on him for 2.5 s after heavy attacks; the Kraken is knocked down for 15 s per 100 damage of cannon fire | stance breaks, crashes and backfires, shown on the model |
| **The room is a weapon** | the Kraken's cannons (5x damage), Obsidilith's pillars, the Gauntlet's basalt cover from its laser, Wither Reincarnated's laser you hide from | the arena's features are the counterplay; powers use them better |
| **Hits hurt, and tanking loses** | Bosses'Rise heavy hits 13 to 21 before armour; Obsidilith 16 on Normal, 24 on Hard | Souls-like numbers (section 4) |
| **A short invulnerable scene at the start and at phase changes** | Bosses'Rise (cutscenes at every phase; Ashlord's skeletal fourth phase; Helvar's resurrection), Skor's freezing enrage | 1 to 4 s, and the player's view shows the change |
| **The death changes the world** | the Gauntlet explodes and veins of Ancient Debris spread through the rock; the Obsidilith builds a pillar with the loot on top; Skor becomes an ice statue that shatters; the dragon bursts in purple flame | a death scene in the boss's idea, then the reward |
| **The reward carries the boss's moves** | Helvar's sword fires his Sword Waves; Skor's gauntlet raises his ice spikes; the Kraken's trident pulls enemies together | a first-kill drop that lets a power do something of the boss's |
| **A place, with a way in** | structures with guards, keys and vaults (Bosses'Rise, Cataclysm), altars and offerings (Bosses of Mass Destruction), a boss woken by breaking its ice (Skor) | the story brings the first fight; an item reopens it |
| **Its own look in the HUD and the world** | Bosses'Rise's themed boss bars; eternal night while the Night Lich or Wither Reincarnated's Wither lives; the Kraken's thunderstorm | a boss bar built from its shapes (`client/WitherBossBar`), and the sky or weather in its idea |

## 2. Powers must matter

Every player in this mod has one power, and a boss is where it should prove itself. The rule: **a boss is not beaten
with a sword and armour alone, and every one of the 20 powers can beat it its own way.** Zelda's bosses are the model and
the warning: the dungeon's item beats the boss (Phantom Ganon is knocked down by its own returned lightning; Stallord is
fought on sand with the Spinner and then ridden up to on a pillar), but when every boss is one repeated item sequence,
the fight becomes a lock and key. So we use locks, many keys, and skill on top.

### Locks: what the boss asks of you

A **lock** is a mechanic the boss puts in front of the player. Each lock has several power answers, each different in
feel, and one slow answer anyone can use. Build every phase from two or three locks.

| Lock | What it looks like | Power answers (by role) | The slow answer anyone has | Seen in |
| --- | --- | --- | --- | --- |
| **Out of reach** | it flies, burrows, surfaces only sometimes, teleports away, stands on a wall | **reach it**: Flight, Superspeed, Pyrokinesis's jets, Matter Assembly's Skywalk, Space-Time's Fold Space, Vibe's Breach Hop; **bring it down**: Telekinesis, Density, Quake, a tether; **hit it there**: Electrokinesis, Sonic, Hydrokinesis, Radiation's beam, Telekinesis's Vector Lance | wait for its landing or surfacing window | Sirok (hittable only above sand), Ashlord's wall phase, Beyond Bosses' flying dragon, our Living Seal |
| **Shielded until something breaks** | runes, pylons, wisps, a shell, healing flowers, a machine feeding it | **break it**: Quake, Density, Sonic (crystal and glass), Pyrokinesis (ice and wood), Telekinesis (tear it out); **switch it off**: Electrokinesis (an EMP, or Kill Switch on a machine feeding it); **sever it**: Telepathy (a mind link), Space-Time (freeze the feed) | mine it, or bounce its own projectile into it | Obsidilith's runes (a diamond pickaxe), the Void Blossom's flowers, Helvar's wisps, our anchor pylons |
| **An attack you cannot block or outrun on foot** | a room-wide blast, a beam, a wave | **hold it**: Invisibility, Telekinesis, Matter Assembly, Cryokinesis, Sonic shields (`NuclearShields`); **dodge it**: Superspeed, Pyrokinesis, Vibe, Flight; **stop it**: Space-Time; **outlast it**: Regeneration | cover the arena provides | Wither Reincarnated's beacon laser, the Gauntlet's laser, our Event Horizon and the death blast |
| **A storm of projectiles** | skulls, missiles, fireballs, sword waves | **send them back**: Superspeed's Wind Arms, Telekinesis's Bullet Catch, Space-Time (catch them in slowed or stopped time), Electrokinesis's Static Screen with Return to Sender (arrows and bullets); **wall them**: Matter Assembly's Foldwall, the shield powers | a shield, and moving | Wither Reincarnated's deflectable skulls (extra damage back), the Night Lich's volleys, our Hollow Rain |
| **Too many bodies** | minions, possessed undead, guards | **clear**: Quake, Sonic, Pyrokinesis, Radiation's fallout; **hold**: Cryokinesis, Density, Telekinesis; **turn**: Telepathy, Matter Assembly's Sentinel Engine; **reel**: Electrokinesis's Winchline | weapons | Wither Reincarnated's possessed undead, the Kraken's pirates, our Bone Brood |
| **The room's own weapon** | cannons, pylons, breakers, a crane | **use it from afar**: Electrokinesis (power it, or work its levers with Remote Hand), Telekinesis (load or throw its ammunition); **get to it under fire**: the movement powers | walk to it and use it by hand | the Kraken's cannons (5x damage, knock it down), Elle's breakers (retired), our anchor pylons |
| **An element or a state** | it burns, is wet, is armoured in ice, regrows, is unseen | Hydrokinesis douses a fire boss and its burning floor; Cryokinesis freezes a flooded arena into floor or makes a wet hide brittle; Electrokinesis on wet stuns; Pyrokinesis melts an ice shell; Sonic shatters crystal armour; Radiation stops a regenerating boss healing; Power Amplification makes an ally's answer bigger | weapons, slowly | Skor's ice, the Void Blossom's regrowth, Ashlord's fire (fire resistance stops the burning, not the breath's hits) |
| **A grab, a curse, a mind attack** | held, poisoned, withered, blinded, turned | Telepathy breaks a mind attack; Regeneration, Hydrokinesis's spring and Lifebloom clear poison and wither; Space-Time rewinds the moment; a friend's power frees you | milk, potions, a friend's hit | the Gauntlet's Blindness (milk clears it), Helvar's guards' Wither II, the Night Lich's slowness |
| **Something hidden** | a weak point behind it, the next move, a disguise | Invisibility drops its aggro to reach its back; Telepathy's Read Mind already shows a target's prey: on a boss, its next move a beat early (a tell only telepaths see); Empathic Mimicry senses and copies its power to use against it; Shapeshifting passes its guards or fits where it can't | patience and learning it | the Gauntlet's eye (open only during its laser), Helvar's weak mark |

Names in the table are moves that exist today (Wind Arms, Bullet Catch, Skywalk, Foldwall, Fold Space, Breach Hop,
Intercept, Read Mind, the `NuclearShields` kinds). The rest say what that power is for; build them as the boss's own
answers, in the one table described below, and add them to the power's agent notes.

### Rules for locks

- **Every lock has at least three power answers in different roles, plus the slow answer.** A solo player with any
  power, or a party of any powers, can win. Write the lock table into the boss's brief, one row per lock and phase.
- **Every power has a strong answer in every phase.** Fill a 21-row table (power × phase): no power sits a whole phase
  out. If a power has nothing, add a lock it answers or give it a special answer on the boss (see "What each power does
  to them" in the brief).
- **A power answer is the best way, never the only way,** and it pays off big: the Kraken's cannon does five times the
  damage of anything else; Helvar's weak mark doubles damage and skips his shield; our anchor backfire takes a tenth of
  the Wither's health. The slow answer should be clearly worse (slower, riskier, smaller), not impossible.
- **Plain weapons are the fallback, not the plan.** On a boss built around powers, plain melee and arrows do less
  (Skor takes 15% from arrows; the Kraken a quarter from projectiles that aren't cannonballs), power hits do full
  damage, and an answered lock opens a window where everything does more. Never zero: a player with no stamina left
  must still be able to finish a fight.
- **Show the lock and its keys.** A lock reads as interactive ("clearly usable or clearly unusable": players will try
  anything that looks usable). When the player's own power answers a lock, tell them once: a glint or tint on the lock
  only they see, a one-time tip, a line in the boss's voice ("Fire? You think fire hurts bone?").
- **Don't lock a boss to one power,** unless the boss is that power's own trial. Don't lock it to a vanilla tool the
  player may not carry (Bosses of Mass Destruction gates its runes on a diamond pickaxe): give that lock power answers.
- **One question, one place.** Which power answers which lock is one table in code: `combat/BossLocks` (built for the
  Wither, November 2026), the way `intro/Danger.answers` says which danger a power can save someone from. The fight,
  the art (who sees the glint), the Guide and the tests all read it. A new boss picks its locks from `BossLocks.Lock`,
  makes its lock targets `BossLocks.Lockable` entities, and adds a key to the table only where a power really does
  something new; its notes are in [built-bosses.md](built-bosses.md) ("Powers answer locks through one table").
- **Use the fighting styles.** Our movement (Pyrokinesis's jet-step evasion, the shared `combat/FightingStyles` dodges
  and counters) must work against bosses: time melee windups so a dodge on the mark fits, let a perfect dodge or a
  shield block earn a counter (Wither Reincarnated: a charge blocked by a shield stuns the Wither), and make a boss's
  heavy blows something a fighting style answers.
- **Test it.** A game test walks every power through every lock and checks it has an answer; a preview photographs the
  answers that are drawn.

## 3. Animation: how a boss move should look

### The five phases of an attack (Dark Souls 3)

| Phase | What it is | Its rule |
| --- | --- | --- |
| **Opening pose** | idle to a readable pose: the tell | a silhouette that says which attack is coming, and shows its weight; different attacks need different poses, or the same pose with different timing plus a different sound or light |
| **Attack signal** | the pose moving toward the strike; not yet harmful | signal plus strike at least **340 ms (7 ticks)**, more for a move the player hasn't learned |
| **Strike** | the hit is live | fast (2 to 4 frames in Dark Souls; 1 to 3 ticks here), snappy, physics bent to feel instant; the hit area a little bigger than the limb so a strike that looks like it hit, hits |
| **End pose** | the strike held | at least **170 ms (4 ticks)** before a window so the player can see it ended; at least **240 ms (5 ticks)** if it is a bait to draw a swing; skip it inside a combo |
| **Return** | back to idle | the window: usually the longest phase; its length is the difficulty |

- **The tell, the window and the numbers.** A combat designer's rule: a tell of **1 to 1.5 s** reads best, and past
  about **2 s** players start counting instead of reacting. A window should allow **at least one full combo plus the
  time to reach the boss**. People react in about a quarter of a second (seeing ~100 ms, deciding ~70, moving ~70),
  and in multiplayer the server's warning arrives a ping later, so keep every reaction window here at **8 ticks or
  more**, and area marks at 12 or more.
- **Feints are fair when the tell is honest.** A delayed strike punishes the expected reaction, not the correct one:
  the mark still fills exactly to the hit, so watching it always works (our Wither's feints, synced as
  `ACTION_WINDUP`). Mix them with straight attacks; never make one unreactable; always leave counterplay. A long roar
  that looks like an area attack but is really a heal or buff is a good trick: it rewards the player who stays close.
- **Escalate by density and speed, not by randomness.** Patterns ramp up each phase; a failure should always be
  explainable. Reads must not overlap: two attacks with a similar pose and sound "muddy the reads".

### Making it feel heavy

- **Exaggerate.** "However much exaggeration you think is too much, it is not enough for a boss." Big wind-ups, big
  key poses, a body that leans into its motion and lags with its weight (tails, heads, chains following through).
- **The body travels** (root motion): a charge moves the real body over ticks; a slam lifts and drops it
  (`WitherMoves.tickMotion`). A boss that blinks to the end of its move looks cheap.
- **Contact feedback:** a short freeze on a heavy hit (hit-stop: 2 to 4 ticks of held pose), camera shake scaled to
  the hit, the victim thrown or knocked down, debris and dust where it lands, and a layered sound (whoosh, impact,
  debris). Many players learn a boss's rhythm by its sounds: give every move its own.
- **Its state is on its body.** A guard up or down, a weak point that glows (Helvar's mark for 2.5 s), an eye that opens
  only during its laser (the Gauntlet), a sword stuck in the floor, a damaged look at half health (Beyond Bosses' dragon),
  a new form for the last phase (Ashlord's skeleton), a stagger that reads as a stagger. Players watch the boss, not
  its health bar.
- **Stun has tiers.** Epic Fight separates short stuns (every normal hit), long ones (special attacks), knockdowns (a
  slam) and its weapons carry "impact" (how long they stun) against a defender's "stun armour". For a boss: ordinary
  hits never stun it; its poise (our stance) breaks for a stagger; a few designed moments knock it down (a crash, a
  backfire, the Kraken's cannon knockdown); and its own hits knock players down only when the move is a slam.
- **Scenes.** An entrance, a short scene at each phase change, and a death animation before the loot (Ashlord's long
  death, the Gauntlet's 2.5 s then its explosion). Keep them short: Bosses'Rise's 19 to 22 s scenes stop the fight cold;
  ours run 1 to 4 s and give the player their view back.

### How to build it here

A hierarchical model whose bones end at the boss's geometry anchors, posed by keyframe curves (`k(t, ...)`) on synced
action, age and windup, with emissive layers that change by phase (`entity/client/RiftWitherModel`,
`RiftWitherRenderer`); set pieces drawn as geometry, never particle clouds
([docs/animation-guide.md](../../../../docs/animation-guide.md)); real floor blocks for shocks (`WitherFloorWave`); and a
preview that photographs every move's tell, strike and window, plus a frame sequence of one move.

## 4. Difficulty: Souls-like numbers

| | Sources | Our default |
| --- | --- | --- |
| Health | 200 to 500 base (Bosses'Rise), 250 to 350 (Bosses of Mass Destruction), plus a second life (Helvar) | 400 to 800 for a story boss, scaled by party; caps set the minimum fight length (aim for 40 s of perfect damage) |
| A heavy hit | 13 to 21 before armour (Bosses'Rise), 16 on Normal and 24 on Hard (Obsidilith) | 11 to 22 before armour, +15% a phase; a quarter to most of an unarmoured player's health |
| Windows | 2.5 s (Helvar's mark), 3.5 s (his stuck sword), 15 s knockdown per 100 cannon damage (the Kraken), 35 s last stand (Helvar kneels) | a stagger 1.8 to 2.4 s; a crash 2.5 s; a slump after a chain under 2 s |
| Spam control | immune for a moment after every hit (Skor); damage gates where the crossing hit stops at the gate (Helvar) | per-hit and per-second caps; gates at phase thresholds |
| Modded gear | armour-piercing attacks | caps as fractions of its health (a flawless fight still lasts 40 s); part of each of its blows pierces armour; heavy blows take a share of the target's maximum health |
| Its health back | heals only to the top of its current quarter (Bosses of Mass Destruction), never heals when you leave (Bosses'Rise) | idle healing with no target; consider the quarter rule, so progress is never lost |

## 5. Stopping cheese

- **Distance has an answer:** something reaches every ring, including straight up (Cataclysm's Ender Guardian pulls down
  climbers; our tether).
- **Hiding has an answer:** the Night Lich teleports when line of sight breaks or when you look away; armour rises when
  it has no target (Bosses of Mass Destruction); a boss that can't reach you leaps (Skor's Leap Smash).
- **Leaving has a cost:** the Kraken sends a tentacle after anyone who leaves the ship; our breach shelter refuses damage
  from inside it.
- **Spamming has a cost:** immunity after each hit, caps, gates; a weak point that is the only place hits count (the
  Gauntlet's eye) makes aim matter.
- **Traps and towers:** the arena is rebuilt; the boss breaks out of blocks; projectiles a boss is weak to come from the
  room, not from a farm.

## 6. Place, progression and reward

- **A region, not a room:** a structure with its own guards (whose drops are the key), vaults, a door, and the boss in
  the middle (Bosses'Rise's Underworld Arena, Cataclysm's dungeons). Guards should teach the boss's language.
- **An order:** weaker bosses first (Bosses'Rise suggests by health), each first kill opening the next.
- **The drop carries the boss's move** for the player's power: Helvar's sword fires his waves. For us, a first-kill
  item that lets any power do one thing the boss did (a Bone Brood charm, a breach seed that opens a short tear).
- **Credit the party:** Bosses of Mass Destruction drops only if a player hit it recently; give every fighter their
  share instead.

## 7. Mistakes these mods show

- **One attack and no movement**: the vanilla Wither, the reason Wither Reincarnated exists ("it only has one attack and
  no interesting movement patterns, and the fight requires very little skill").
- **A fight won by tanking or spamming**: answered by real damage, short windows, caps and gates.
- **A lock only one tool opens**, or a power that has nothing to do in a phase.
- **A tell that lies, or a tell too short to react to**: players stop trusting every tell.
- **Interactive-looking scenery that does nothing** (players kept turning a crane wheel that only worked once the boss
  was down).
- **Long scenes** that take control away for twenty seconds.
- **Phases that only add numbers.**

## 8. Before you write code

1. What is the one idea, and what does each lock look like in it?
2. For each phase: which two or three locks, and for each lock, which powers answer it, how well, and what the slow
   answer is? Fill the 21-row power × phase table; every power has a strong answer in every phase.
3. For each move: opening pose, signal, strike, end pose and window in ticks; which range it covers; its sound; how
   it differs from every other move at a glance.
4. Which windows are earned, and what does each look like on the body?
5. What does the death do to the world, and what does the drop let a power do?
6. How is it cheesed, and what answers each?

## Sources

- Mods: see [boss-mods.md](boss-mods.md) and its sources.
- [Anatomy of an Enemy Attack in Dark Souls 3](https://www.gamedeveloper.com/game-platforms/anatomy-of-an-enemy-attack-in-dark-souls-3)
  (Jerry Zhang, 2022): the five phases and their minimum lengths.
- [Boss Design: Tips From A Combat Designer](https://gamedeveloper.com/design/opinion-boss-design---tips-from-a-combat-designer)
  (Mike Birkhead, 2011): clean reads, 1 to 1.5 s tells, windows of a full combo, exaggeration, staging.
- [The Art of the Fakeout](https://www.wayline.io/blog/the-art-of-the-fakeout-deceptive-combat-design) (2025): fair feints.
- [Boss phases: readable, learnable, beatable](https://molbv.github.io/blog/2025-07-08-boss-phases.html) (2025):
  escalate by density and speed.
- [How To Design Your Ultimate Dungeon Boss](https://www.zeldadungeon.net/how-to-design-your-ultimate-dungeon-boss/)
  (2017): the dungeon item in the boss's mechanics (Phantom Ganon, Stallord).
- [Epic Fight documentation](https://epicfight-docs.readthedocs.io/): dodges, guards, parries, stun tiers, impact and
  stun armour.
