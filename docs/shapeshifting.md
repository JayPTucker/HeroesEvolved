# Shapeshifting (design draft)

> **Status: phases 1 to 6 of 7 built (November 2026).** Every part of the power is in: Morph, the look, people's
> shapes and chat, each form's traits and Instinct, Decoy, Reflection, Nightmare, Suspicion and its clues, and (phase
> 6) the skill tree, signature, classes, Evolved trial and awakening. Phase 7 is polish. The numbers are starting
> points for playtesting. Open questions are at the end.
>
> Shapeshifting left `ModAbilities.IN_DEVELOPMENT` when its tree and classes landed, so the Eclipse, the Formula,
> gifted villagers and retired-power swaps can now hand it out. Give it with `/heroesevolved power give @s
> shapeshifting`. It is checked by `ShapeshiftGameTests`, `ShapeshiftTricksGameTests` and `ShapeshiftSkillsGameTests`.
>
> **What works now:**
> - **Morph:** press while looking at a creature within 6 blocks to study it for a second and take its shape.
>   Press while looking at nothing to change back (free).
> - **The form wheel:** Shift + Morph opens a wheel of every remembered shape, each drawn as a small turning model.
>   Click one to take it at once (no study), click the centre to go back to your own body, or press Escape.
> - **Touch:** right-click a creature or a person with an empty hand to learn their shape without taking it. The
>   click still does what it normally does, so a villager still opens its trades.
> - **People (Level 6):** study or touch another player to learn their face. You get their skin and name tag and
>   a plain player's body, but your own armour and held items still show. Nobody can copy an operator, and you
>   can't copy yourself. Remembered faces keep their skin through death and logging out.
> - **Speaking as them:** see [Speaking as someone else](#speaking-as-someone-else).
> - **The look:** see [Look and feel](#look-and-feel); what is built is marked there.
> - **Traits:** every trait and weakness in the [Traits table](#what-a-form-gives-you) works, from a spider's
>   wall climbing and a wolf's nose to a zombie burning in daylight and an enderman hurt by water.
> - **Hands:** forms without hands can't use items, place blocks or open containers. They dig at the bare-hand
>   rate and fight with the form's own blow.
> - **Instinct (Level 3):** the second key is your form's own attack, as in the [Instinct table](#core-abilities).
>   It is Lunge in your own body and Familiar in a person's. Each move pays its own stamina and keeps its own
>   cooldown, so switching forms never waits on another form's attack. Every move is drawn in the plate language.
> - **Decoy (Level 6), Reflection (Level 10), Nightmare (Level 15):** as in the [core abilities](#core-abilities).
>   - **Decoy:** the shell is a real body, so mobs really chase it and hit it. It wears your shape (your own face
>     if you wear none), with a seam split down its back, the one clue it is empty.
>   - **Nightmare:** each player sees their own version. A foe sees a warden rear up and roar where you stand;
>     anyone else sees only the plates rising round you.
>   - **Confused and frightened:** a creature the Decoy or Reflection confuses, or a Nightmare frightens, can't pick
>     a target and its blows land on nothing; a frightened one also runs.
> - **Suspicion:** the eye under your crosshair fills as you draw notice, as described in
>   [Blending in](#blending-in). In a person's shape, "your kind" is any other player who can see you.
> - **Clues:** all of those listed under [Clues anyone can check](#clues-anyone-can-check) work, with Sound Sight's
>   clue as described there.
> - **Skills and classes:** see [Skills](#skills-eighteen-nodes) and [Class abilities](#class-abilities). Decoy,
>   Reflection and Nightmare raise Suspicion when your kind sees them; the tree's skills and the classes don't yet.
> - **Already here:** a dampener round, a Suppressor or an Eclipse drops the form at once.

Shapeshifting is the infiltration power the [expansion roadmap](expansion-roadmap.md#shapeshifting-infiltrator--mimic--identity)
asks for. In *Heroes* it belongs to James Martin, and later Sylar. You study a creature or a person, ripple
into their shape, and walk among their kind. Mobs of your form's kind leave you alone, and you take their
body: its size, strength, speed and weaknesses. Bigger and deadlier forms open at higher levels. Wearing
another player's face lets you speak as them in chat, with safeguards (see [Speaking as someone else](#speaking-as-someone-else)).

The transformation is the power's signature. A wave of small overlapping plates rolls up the body, and the
new shape grows into place behind it.

Power ID: `shapeshifting`. Package `shapeshift/`, art in `client/effects/setpiece/ShapeArt`.

## The rules

### Forms

A **form** is a shape you have learned: an entity type plus, for a player, their name and skin. You learn a
form by **studying** it, either by touching it with an empty hand or by watching it for a moment (see Morph
below). Learned forms go into your **library**, saved in `Player.PERSISTED_NBT_TAG` so they survive death.
The library holds 3 forms, plus 2 per tier, up to 15 when Evolved. When it is full, learning a new form
forgets the oldest one you haven't used.

Your level decides which forms you can learn:

| Power Level | Forms | Examples |
| --- | --- | --- |
| 1 | Passive animals | chicken, rabbit, cat, fox, pig, sheep, cow, wolf, fish |
| 3 | Common hostiles and neutrals, villagers, mounts | zombie family, skeleton family, spider, creeper, small slime, bee, horse, llama |
| 6 | **Players**, illagers, and odd bodies | any player, pillager, vindicator, enderman, piglin, blaze, guardian, phantom |
| 10 | Large forms | iron golem, ravager, evoker, witch, hoglin, piglin brute, ghast |
| 15 | Minibosses and story people you have met | warden, elder guardian, Company agents |
| 20 (Evolved) | Bosses | wither; Sylar and Elle (looks only, never their powers) |

The Ender Dragon is left out because it is built from several separate parts.

Mobs from other mods are sorted by the same tests: max health, whether they are hostile, their size, and
whether they have a boss bar. Anything with a boss bar or more than 100 max health needs Evolved.

**Forms copy bodies, never powers.** Taking the shape of a powered player, a gifted villager or Sylar copies
how they look and nothing else. Copying powers is Empathic Mimicry's job.

### What a form gives you

Morphing applies attribute modifiers with the id `heroesevolved:shapeshift_*`. Reverting, dying, logging out
or changing dimension removes them all.

| Taken from the form | How |
| --- | --- |
| Size and hitbox | The form's `EntityDimensions` through `EntityEvent.Size`; the eye height follows. You need room to change: a form that wouldn't fit fails with "No room", the same idea as a Breach's exit check. |
| Max health | The form's max health, capped by tier (20 + 10 per tier, 100 when Evolved). Your health keeps the same fraction, so you can't heal by changing shape. |
| Attack damage, armor, toughness, knockback resistance | The form's values. |
| Movement and jump speed | The form's speed relative to its own kind, mapped onto a player's range and clamped to 0.06–0.16. |
| Step height, gravity, safe fall distance, swimming, breath | Copied where the form has them: a spider's step, a chicken's light fall, a fish's water speed and breath. |

**Traits** are the extras a form brings. The table below is a starting list.

| Form | Traits | Weakness |
| --- | --- | --- |
| Spider | climbs walls, sees in the dark | — |
| Chicken, bat, parrot | slow fall; bats and parrots flutter (slow flight, Level 6+) | tiny health |
| Fish, dolphin, axolotl, guardian, drowned | fast swimming, breathes water | fish and guardians run out of air on land |
| Zombie, skeleton, drowned | undead: instant damage heals you and healing potions hurt you | burns in sunlight, takes extra damage from Smite |
| Blaze, strider, magma cube | fire immunity | water and snowballs hurt you |
| Enderman | cannot be hit by projectiles | water hurts you |
| Iron golem, ravager | knockback immunity | slow |
| Cat | creepers keep their distance | — |
| Wolf | tracks scent: hurt creatures glow for you within 16 blocks | — |
| Warden | senses vibrations: moving creatures are outlined within 20 blocks | almost blind |
| Wither, ghast, phantom | flight | — |

The weaknesses matter. They keep a strong form from always being the best one.

**Hands.** Humanoid forms (players, villagers, illagers, piglins, zombies, skeletons, endermen, witches) keep
their hands: you can use items, place blocks and open containers. Other forms can't. They attack with the
form's own blow and break blocks at the bare-hand rate.

**Cost.** Morphing costs stamina by size: 8 for small and human-sized forms, 16 for large ones, 30 for boss
forms. Small and human-sized forms cost nothing to hold. Large forms drain 1 stamina a second and boss forms
3, so a giant form never lasts long. When your stamina runs out, you revert.

### Blending in

Mobs ignore you while you wear the shape of their kind:

- **Kin groups.** Every zombie variant treats you as one of them if you are any zombie, and the same goes for
  skeleton variants, raiders (illagers and witches) and piglins. Players treated as villagers are also
  ignored by raiders, but iron golems defend you.
- **The hook.** This runs in `LivingChangeTargetEvent`, the same hook as Invisibility's `target` handler.
- **No mid-fight escapes.** A mob already fighting you keeps fighting unless you change shape out of its sight.
  Breaking line of sight for two seconds is enough (Blend In shortens this).

**Suspicion** is a meter from 0 to 100 that you see as an eye filling on the HUD. These raise it:

- attacking a creature of your form's kind (+40);
- using a move your form couldn't make, such as a skill or class ability, where your kind can see you (+25);
- hurting anything while your kind can see you (+15);
- in a player form, a telepath or Vibe reading you (to 100 at once).

It falls by 5 a second while none of your kind can see you.

At 100 you are **exposed**: the plates shed and you drop back into your own body (`SHIFT_EXPOSED`). You glow
for five seconds, every mob of that kind within 16 blocks targets you, and Morph is locked for ten seconds.
A single blow worth more than 30% of your max health also breaks the form, so hitting a shapeshifter hard
always works.

### Clues anyone can check

The roadmap asks for disguises that leave a mystery players can solve, so every disguise leaves clues:

- **The player list never lies.** A copied player's name still appears only once in the Tab list, and so
  does yours.
- **Your voice is still yours.** Simple Voice Chat carries your own voice until you learn Familiar Voice
  (which hides your voice rather than imitating theirs).
- **Gear.** In a player form, your own armour and held items show until you learn Perfect Copy.
- **Animals notice.** Tamed wolves and cats growl and stare at a disguised player within 8 blocks.
- **Powers that see through it:**
  - Telepathy's Read Mind names whoever is under the shape and exposes them (Mind Link doesn't, yet).
  - Vibe's Read the Vibe, touching a disguised player, sees through and exposes them.
  - Empathic Mimicry's Sense Powers reads "Shapeshifting" under the borrowed face (it reads the real player).
  - Sonic's Sound Sight colours a disguised player's sounds violet, as a player's, whatever shape they wear.
- **The Ability Dampener** (a live field or a Nullifier round) forces you back into your own body
  (`Dampening.dampened`, checked each tick).
- **Death messages** name the real killer.

## Core abilities

| Slot | Level | Ability | In play |
| --- | --- | --- | --- |
| 1 | 1 | **Morph** | Look at a creature or player within 6 blocks and press: you study it for one second (the target sees plates ripple across its own outline), then ripple into its form. Press while looking at nothing to return to your own body. Shift + press opens the **form wheel** to pick a remembered form. Touching a creature with an empty hand teaches its form instantly. |
| 2 | 3 | **Instinct** | Your form's own attack (see the table below). In your own body it is **Lunge**: your arm lengthens into a reaching strike, 4 blocks, 6 + tier. |
| 3 | 6 | **Decoy** | Shed an empty shell of your current shape where you stand. It holds for six seconds and draws every mob that was hunting you. When it is hit it bursts into plates, and the first mob to hit it is left confused for two seconds. |
| 4 | 10 | **Reflection** | Within three seconds of being hit, become your attacker. Mobs of its kind nearby forget you, and the attacker staggers for a second, face to face with itself. |
| 5 | 15 | **Nightmare** | For one breath, each enemy within 12 blocks sees the thing it fears most: a creeper sees a cat, a skeleton a wolf, a villager a zombie, a player a warden. Each viewer gets their own private piece, so everyone sees something different. Enemies are frightened for five seconds: they run and can't attack. Then you return to the form you had. |

Instinct for each form (any mob not listed falls back to a melee strike at its own attack damage):

| Form | Instinct |
| --- | --- |
| Zombie | **Grab**: hold a target in place for two seconds |
| Skeleton | **Bone Shot**: an arrow with no bow or ammo |
| Spider | **Web Spit**: a cobweb that fades after five seconds |
| Creeper | **Detonate**: a blast that drops you back into your own body. It only breaks blocks with mobGriefing on *and* Volatile learned. |
| Enderman | **Blink**: up to 12 blocks, swept |
| Blaze | **Three Fireballs** |
| Wolf | **Pounce** |
| Horse | **Buck**, or carry a friend: they can ride you |
| Iron golem | **Fling**: throws the target into the air |
| Ravager | **Charge and Roar** |
| Witch | **Hex Flask**: a slowness splash |
| Evoker | **Fangs** in a line |
| Warden | **Sonic Boom**: goes through walls, 10 + tier |
| Elder guardian | **Gaze**: a beam that leaves mining fatigue |
| Wither | **Skulls**, which wither |
| Player | **Familiar**: for ten seconds that player's tamed pets obey you and their teammates' defences treat you as them |

## Speaking as someone else

In a player form, what you type in public chat appears under that player's name. This is the power's most
fun trick and its easiest one to abuse, so it has firm limits:

- **Public chat only.** `/msg`, `/tell`, team chat and commands always go out under your real name.
- **No posing as admins.** You can't take the form of an operator (permission level 2+), so nobody can fake
  an admin announcement.
- **The real player always knows.** The person you are copying sees your lines marked "not you," and a quiet
  action-bar notice tells them someone is speaking as them. It doesn't say who unless they are a telepath.
- **Some viewers can see who sent it.** Each viewer gets their own copy of the line, so the copied player,
  operators, and a telepath who has read you get a small mark. Hovering over the name shows "Spoken by
  `<real name>`."
- **Everything is logged.** The server log records each line with the real sender.
- **Admins can switch it off.** The server config `shapeshiftChatImpersonation` turns it off on a server.
  Player forms still work; chat then uses your real name.

**Built in phase 3** (`shapeshift/ShapeshiftChat`). `ServerChatEvent` is cancelled and the line is sent again
to each player as a system message styled like chat, because signed chat can't carry another player's name.
What each viewer sees:

- **Everyone:** `<Name> message`, with the copied player's name. Hovering the name shows the copied player, as
  ordinary chat does, and clicking it starts a `/tell` to them.
- **The copied player:** the same line ending in a grey "(not you)". At most every ten seconds a notice on their
  action bar says "Someone is speaking in your shape". If they own Telepathy, it says who.
- **Operators, and a telepath with a thread on the speaker's mind:** a brass `*` after the name. Hovering it
  shows "Spoken by `<real name>`".
- **The speaker:** the same `*`, showing "You spoke as `<name>`".

Other details:

- The server log records each line as `[Shapeshifting] <real name> (<uuid>) spoke as <name>: <text>`.
- If the copied player has become an operator since you learned their face, your lines go out under your own name.
- The switch is `shapeshifting.chatImpersonation` in `heroesevolved-common.toml`, and it is on by default.
- Your double's name tag shows the copied name to everyone else. You don't see it yourself, as with your own name
  tag.
- The Tab list is untouched. Death messages name the real player.

## Skills (eighteen nodes)

The tree has three branches: Infiltrator, Beast and Identity. As in the other reworks, each passive changes a core
move where it already decides something (`shapeshift/ShapeshiftSkills`), and Shapeshifting is on
`SkillTreeService.bonus`'s no-stat-bonus list. Positions 1 and 4 of each branch are the active skills; 5 is the
capstone.

**Infiltrator: never be noticed.**
- **Blend In** (root): Suspicion rises 30% slower, and hunters lose you after one second out of sight instead of two.
- **Quick Change** (active): switch at once to the shape you wore before this one (or your own body), with no study.
  Every mob hunting you loses you as you change.
- **Second Skin** (mastery): a blow must be worth half your form's health to break it, not 30%. Whoever you reflect is
  set upon by its own kind for five seconds.
- **Hollow Crowd**: your Decoys stand for twelve seconds, and their burst confuses every foe within four blocks, not
  just the striker.
- **Shed** (active): slip out of a zombie's grab, a cobweb, Slowness or a mount. You leave a Decoy where you stood and
  step three blocks back.
- **Faceless** (capstone): among three or more of your shape's kind, Suspicion can't rise, and shots from farther than
  four blocks miss you.

**Beast: fight in the shapes you take.**
- **Natural Weapons** (root): Instinct costs 30% less stamina, and Lunge reaches two blocks farther.
- **Hybrid** (active): keep your own body for twenty seconds but take the gifts of the last creature you wore: its
  fire immunity, its flight, its breath under water, its slow fall. It never takes a weakness, or wall climbing and
  the glowing senses, which only a client draws.
- **Apex** (mastery): your health cap rises by 20, and large and boss shapes cost half as much stamina to hold.
- **Ambush Shift**: the first Instinct within two seconds of changing shape hits 50% harder. In mid-air, studying takes
  no time at all.
- **Chimera** (active): for twenty seconds your Instinct is the attack of the shape you wore before this one.
- **Pack Leader** (capstone): in a creature's shape, every wolf near you joins in on whatever you strike, and as a
  raider so does every raider.
- **Signature, Apex Form** (tier 4, 3 points, under Apex): grow to two and a half times your shape's size for twelve
  seconds, with a boss's health (up to 100) and twice its strength. Your Instinct becomes a ground slam that throws
  everything within five blocks (10 + 2 per tier). The growth uses the player's `SCALE` attribute, so it reaches every
  client by itself.

**Identity: become a particular person.**
- **Perfect Copy** (root): in a person's shape, your own armour and what you hold no longer show.
- **Borrowed Face** (active): touch a creature and swap looks for eight seconds. You take its shape (free), it wears
  yours (drawn as your double on every client), and its own kind turn on it.
- **Practised Manner** (mastery): in a person's shape, people watching you raise Suspicion half as fast, and it fades
  twice as fast.
- **Long Con**: you can study a person from 12 blocks away, not 6.
- **Doppelganger** (active): a copy of your shape walks on ahead for eight seconds. Every mob hunting you follows it
  instead, and it bursts on the first blow.
- **Social Engineering** (capstone): taking a person's shape calls their pets to you at once (Familiar), and Familiar
  lasts thirty seconds.

**What changed from the first draft:**
- **Familiar Voice** (masking your voice chat) became Practised Manner. Masking would mean rebroadcasting audio from
  Simple Voice Chat's own thread, which can't safely look up who is near. Your voice is still a clue.
- **Perfect Copy** hides your gear rather than copying the other person's, since their gear isn't known once they log
  off.
- **The Doppelganger** walks but doesn't speak. **Shed** frees you from what the server can see holding you, not
  from other powers' holds.
- **Chimera** became an active skill and **Pack Leader** the capstone, so each branch has two actives.

## Class abilities

| Class | Ability |
| --- | --- |
| Offense | **Many Hands**: plated arms lash out from you to strike up to five foes within 8 blocks, one after another, for 5 + tier each. |
| Defense | **Plate Mail**: scale armour locks over your torso for eight seconds. Shots skid off, you take 30% less damage, and the first blow sheds a Decoy. |
| Support | **Masquerade**: you and up to four friends within 10 blocks take one creature's shape for twenty seconds: the one you wear, the newest you remember, or the nearest you can see. Its kind take you all for their own. |

**Evolved trial:** spend 30 minutes in total in disguise among your shape's own kind (within 8 blocks of one). Time in
a person's shape doesn't count.

**Awakening:** the owner ripples into the nearest threat's shape. Every threat loses the owner and the ward and turns
on the nearest of its own kind for five seconds; a threat with none of its kind near stands confused.

## Look and feel

Follow [the animation guide](animation-guide.md). There is one art class, `ShapeArt`, with three primitives
and one palette.

**Built in phase 2:**

- **The change.** The shell follows the body's outline: legs, hips, shoulders, neck and head for an upright body,
  a low mound for a beast. Its plates are curved, pointed scales, slate on the face and brass on the back, darker
  where they tuck under the row above and paler where they turn from your eye. They unfurl at a brass seam that
  climbs the old body from the feet, each swinging down from brass to slate and snapping shut with a little
  overshoot. When the seam passes the crown the shell clicks shut. A bristling wave then runs up it, catching the
  light, as it bulges into the new body's size. Then a second seam climbs from the feet: the plates lift open
  (brass showing), tear loose, tumble on their own axes and drop away, rattling, and the new shape is left standing.
  A normal change takes 18 ticks; one to or from a big body takes 26. Going back to your own body runs the same
  way from the head down.
- **Your own change in first person.** The shell becomes a wider curtain round you that rises to just under your
  eyes, so you see the brass backs of the plates close round you and then scatter outward.
- **Studying.** Two fingers at the temple, the other arm folded under the elbow. A thin thread runs from your eye
  to the creature, and a band of ghostly plates scans up it twice, leaving a faint shell where it has passed; as
  the study ends the whole outline flashes brass. Only you and the creature (if it is a player) see this.
- **Touching.** An open palm on the creature. Plates ripple out over it from your hand, flip and fade, with a
  chime.
- **The sweep.** As the plates start to climb, one palm is raised before the face and drawn down over it, in third
  and first person.
- **First person.** A humanoid form (zombie, skeleton, piglin…) shows its own bare arm instead of yours, and power
  gestures are made with it. A form without hands shows nothing at all. Your own camera is inside the shell, so
  you don't see the plates yourself.
- **Particle settings.** Minimal uses fewer, larger plates; All adds a glint on each plate as it turns.

The design below is the full plan; `SHIFT_EXPOSED`, the decoy, Nightmare and the class pieces come with their
phases.

- **Primitives:**
  - `scales`: a band of small curved plates that overlap like roof tiles. Each plate flips from its dark
    face to its bright underside as the front passes.
  - `ripple`: a front that hugs the body's box and rolls from the feet up, or out from a touching hand. It
    eases on `1 − (1 − p)³`.
  - `shed`: plates peeling off a body and tumbling down as they fade.
- **Palette** (iridescent, not neon): `1F2A3D` shadow, `3E6F86` slate-teal, `8FCFC6` sheen, `C29A5E` brass
  underside, `F1ECE2` highlight.
- **The morph** (18 ticks, 26 for big forms): built as above. It isn't a set piece: `MorphStatePayload` carries
  an `animate` flag, `MorphClient` keeps a `Transition` for each change, and `ShapeArt.drawLive` draws it. The
  timeline (`COVERED`, `SWAP`, `SHED`) is in `ShapeshiftGeometry`, so the plates and the body swap agree.
- **Other pieces:**
  - The revert is the same transition run from the head down.
  - `SHIFT_EXPOSED`: a hard shed with a dark crack.
  - `SHIFT_STUDY`: a private trace of plates around the outline, visible to the target as well (built).
  - `SHIFT_TOUCH`: plates rippling out from a touching hand (built).
  - `SHIFT_DECOY`: the decoy shell, and its burst.
  - `SHIFT_NIGHTMARE`: sent to each viewer separately.
  - `SHIFT_LUNGE`
  - `SHIFT_PLATES`: Plate Mail.
  - `SHIFT_MANY`: Many Hands.
- **Poses** (third person in `PowerArmPoses`, first person in `FirstPersonArmGeometry`):
  - `SHIFT_SWEEP`: one palm drawn down over the face, which starts the morph (built).
  - `SHIFT_STUDY`: two fingers at the temple, the other arm folded under the elbow (built).
  - `SHIFT_TOUCH`: palm out (built).
  - `SHIFT_LUNGE`: the arm stretches.
  - `SHIFT_SHED`: shoulders roll back.
  - `SHIFT_NIGHTMARE`: arms flung wide.
- **Drawing the form.** `client/MorphClient` cancels `RenderPlayerEvent.Pre` for a morphed player, as
  `UnseenClient` does. It draws a client-only stand-in entity of the form's type instead, copying the
  limb swing, head and body yaw, crouch and ground state each frame. Player forms draw a player model with
  the copied skin, the way `SpeedAfterimages` draws a real skin. In first person, a form's own arm replaces
  yours, and forms without hands show nothing.

## How it is built

- **State.** Each change sends one small `MorphStatePayload` (player id, form type, optional profile, phase
  flags) to everyone tracking the player. Like `FlightVisualStatePayload`, nothing is sent each tick.
  `ClientMorphState` stamps the game time so the ripple knows how far along it is.
- **Classes.** In `shapeshift/`:
  - `ShapeshiftService`: state, attributes, dimensions, revert.
  - `ShapeshiftForms`: tiers, the classification of modded mobs, the trait table.
  - `ShapeshiftSuspicion`
  - `ShapeshiftChat`
  - `ShapeshiftSkills`
  - `ShapeshiftGeometry`: shared numbers, so the server and the art agree.

  The ability itself is `ability/types/ShapeshiftingAbility`.
- **Things to check while building:**
  - `PowerRules.canAct` during an intro, and the Eclipse revert.
  - `MimicryService.imprintOf` should show a shapeshifter's real power.
  - `Danger.ally` and `StyleOps.isFriend` must use the real player, never the copied one.
- **Build order:**
  1. State, attributes, dimensions and being ignored by your kind, drawn with a plain stand-in mob.
  2. Rendering, the ripple, poses and the form wheel. *(Built.)*
  3. Player forms and chat with every safeguard. *(Built.)*
  4. Instinct for each form.
  5. Suspicion and the clues.
  6. Tree, class abilities, trial and awakening.
  7. Tests and previews.
- **Game tests** (`ShapeshiftGameTests`):
  - Level gates for forms.
  - Attribute modifiers are applied and removed on revert, death and dimension change.
  - Health keeps its fraction.
  - "No room" fails.
  - Kin ignore you, and a mob already fighting you doesn't.
  - Suspicion builds to exposure.
  - A heavy blow breaks the form.
  - Dampening forces a revert.
  - Chat rules: never an operator's form, `/msg` uses the real name, the copied player is told, and the
    config switch works. The test server never allows PvP, so call the rule directly.
- **Previews:** `tools/shapeshift-preview.init.gradle` (`uitest/ShapeshiftPreview`, flag
  `heroes.preview.shapeshift`) shoots the morph at five points of its ripple, every form tier, each piece and
  pose, front, back and first person.

## Open questions

1. **Chat by default.** Should `shapeshiftChatImpersonation` default to on, or off with servers opting in?
   This draft defaults to on.
2. **The Ender Dragon.** Leave it out, or add a special smaller dragon form at Evolved?
3. **Hostile forms in PvP.** With PvP off, should a player in a zombie form be fair game for other players?
   This draft says no: it is still a player.
4. **Breaking blocks in beast forms.** Bare-hand speed, or allow a few forms (ravager, wither) to break
   blocks faster?
