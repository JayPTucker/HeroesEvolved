# Vibe

See [ranged attack distances](projectile-ranges.md) for the current projectile and beam ranges.

Cisco Ramon's power from *The Flash* uses dimensional vibrations for concussive blasts, walk-through breaches and glimpses of past events. The [Arrowverse Wiki](https://arrow.fandom.com/wiki/Cisco_Ramon#Powers_and_abilities) describes Cisco's original abilities. [DC's character profile](https://www.dc.com/characters/vibe) also describes his shockwaves and interdimensional travel.

## Core abilities

| Slot | Ability | In play |
| --- | --- | --- |
| 1 | **Vibe Blast** | **Hold** it, as Cisco does: a steady, crackling blue blast pours from your palm to the first enemy or wall in its way. Every 6 ticks it hits that enemy (1.2 + 0.35 per tier, more if they've been read) and shoves them back. It drains 9 stamina a second until you let go or run out (it costs 4 to start). |
| 2 | **Breach** | **Hold** Breach, as Cisco does: a crackling vibe blast leaves your palm and a tear swirls slowly open at the end of it, angled to where you look and turning with you while it opens. It goes to the last frequency you used (or where you aim, if you haven't used one; hold Shift too to aim instead). **Tap** Breach to open the frequency wheel and pick a saved location or the centre for where you look. Both mouths show the world on the other side. Allies and mobs can walk through either mouth. Press again to close the link for free. |
| 3 | **Read the Vibe** | Reach toward a nearby person, mob or scene. Combat leaves 25-second echoes, replayed as ghosts of the real creatures: the attacker, see-through and cold violet, lunges at its victim, who flinches back flashing red, with a thread of light between them. Revealed aggressors get a blue ripple and take more damage from your blast and Rift Fall. With your hand on something of another player's (their bed, chest or furnace, or their tamed animal), you get a vision of them instead (see Visions). |
| 4 | **Breach Guard** | Open a moving iris in front of you. Arrows, TaCZ rounds and other incoming projectiles that cross the opening collapse into light. Press again to fire the stored impact for free, or hold Shift and press to close it. |
| 5 | **Rift Fall** | Throw both hands up and a blast from each palm tears a great breach open in the sky (up to 15 blocks up) over a zone you aim at, five blocks round. A ring on the ground fills for a second: time to get out. Then a tear opens under every enemy still in it (up to 12), they drop through, and one after another they plummet out of the sky breach and slam down: 6 + 2 per tier (30% more if read), slowed, and a speedster knocked out of phase until the interference clears. Bosses and anything too big for a breach (or frozen, or held in stopped time) take the slam where they stand. |

## Opening a breach

A breach from the Breach key is channelled, the way it looks on the show. Hold the key for a quarter of a second and
your arm locks out toward where you look: a blue blast crackles from your palm and, a couple of steps ahead, a tear
opens from a spark into a full iris, its rim spitting little arcs. It takes 30 ticks (1.5 seconds) at tier 1, three
fewer each tier, 18 at tier 5. While it opens it follows your look, yaw and pitch; a breach to a frequency tilts at
most 30° so you can always walk into it. You can let go once it has opened. Let go before that and it pinches shut,
and you get the stamina back and no cooldown starts. Picking a frequency on the wheel channels the breach too, but
you don't have to keep holding.

A held Breach goes to the last frequency a breach of yours went to, so a hold takes you home again. Shift + hold
always opens where you look. If the last frequency is out of reach from here (you are standing on it, or it is in a
world you can't reach), the hold opens where you look. For half a minute after a vision, a hold opens beside what the
vision showed instead (see below).

## Frequencies

A frequency is a place you have tuned, like a waypoint. It saves your dimension, position and facing. Once Breach
unlocks at Power Level 3, click an empty slot to save where you stand on solid, open ground. You start with one slot
and gain another at Levels 4, 8, 12, 16 and 20. The dimmed slot on the wheel shows the next unlock. **World Frequency**
adds two slots and lets a breach reach frequencies in other dimensions.

On the wheel, click a frequency to open a breach there, Shift-click to move it to where you stand, right-click to
forget it and press R over a slot to rename it. Moving a frequency keeps its custom name. Each slot shows its world,
its name (the biome it was tuned in until you name it)
and how far away it is. A breach to a frequency stays open for 15 seconds (an aimed one for 9) and costs Breach's
stamina plus 1 for every 40 blocks, up to 30 more, or 15 more to another world. A frequency is kept through death. The
single location Attune Frequency used to save becomes your first frequency.

A saved frequency opens an upright doorway. If the original spot is blocked, Breach looks up to three blocks
around it for a clear landing and keeps the saved position. A destination in unloaded chunks is prepared before
the cast spends stamina. Press Breach again to cancel while it is loading.

## Visions

Like Cisco, a viber gets visions they can't control: there is no key for them and no way to refuse one. The world
flickers and blurs around them when, within twelve chunks (192 blocks):

- **an ally is in danger:** a friendly player or their own pet more than 40 blocks away drops below three hearts. This
  always brings a vision (closer than 40 blocks, you're near enough to help, so it never does). The one nearest to
  death comes first.
- **a village is in trouble:** a villager melting down, a renegade turning on its village, or a raid. These bring a
  vision about half the times they could.
- **a village is about to be:** a village being prepared for an incident (an awakening, a renegade, a meltdown) shows
  in a vision before it starts, tinted a little violet. This is seeing the future.

After a vision, a viber can't have another for ten minutes (`vibeVisionMinutes` in the common config; 0 turns visions
off). The wait is saved on the player, so logging out and back in doesn't reset it.

A chat line says what and where ("Claire is in danger, 44 blocks south"). Then for ten seconds the viber leaves their
body: they drift as a spectator in a washed-out, cold blue world with soft bright edges, scan lines and the odd
stutter, kept within 24 blocks of what they are shown. A still double of them stands where they were. Anyone who
attacks the vision's ally is marked for half a minute, so Vibe Blast and Rift Fall hit them harder. A blow to the
double tears the viber back at once and lands on them. Their powers don't answer during a vision.

**Holograms.** From Power Level 12, pressing any power key during a vision (of an ally or a village in trouble now)
projects the viber there as a hologram: their own skin, cold blue and see-through, with a sheen of light running over
it, flickering like a bad signal and turning as they look. Players nearby can see it and, with Simple Voice Chat, hear
the viber's voice from it, with its own **Vibe Hologram** volume slider. It can't touch, block or hurt anything. Press
again to fade out.

**Visions by touch.** Cisco can also vibe someone by touching something of theirs. Use Read the Vibe with your hand on
(within 2.5 blocks of, and looking at) a bed, chest, trapped chest, barrel, ender chest, furnace, blast furnace, smoker
or shulker box another player placed, or an animal another player has tamed, and you get a vision of that player
wherever they are in this world, the same way as above (the double, the hologram from Level 12, the breach
afterwards). These blocks remember who placed them from now on; ones placed before this update don't. If the owner is
offline, in another world or a spectator, the touch spends the read and shows nobody. A vision by touch doesn't use the
ten-minute wait, but you can have only one every thirty seconds. Touching your own things reads the scene as usual.

When the vision ends the viber is back in their body. For the next thirty seconds, holding Breach opens a breach
straight to what they saw.

## Seeing through a breach

Each open mouth shows the destination's blocks, water, plants, lighting and biome colours. The view samples up to
30 blocks in every direction beyond the far mouth and fades into the destination's sky or haze near the edge.
Walk around a mouth and the view moves with you. Nearby players, creatures and other visible entities update live
with their movement and equipment, including at destinations outside your normal render distance. Terrain is
captured once when the destination chunks are ready.

Simple Voice Chat carries proximity speech both ways through an open Breach, including across dimensions. The
voice comes from your side of the opening and fades with the distance each person stands from their mouth. Whisper
range and private groups follow Simple Voice Chat's rules. Relayed voices have their own **Vibe Breach** volume slider.

Arrows and TaCZ rounds can fly through either mouth. The shot keeps its speed, pitch, and aim relative to the opening,
even when the exit faces another direction or opens in another dimension. Cover and targets in front of the entrance
still intercept a shot. On the far side, the projectile resumes its normal hit and damage rules. Glimpse lets you see
entities without letting shots or travelers through.

## Aiming a breach

Hold Shift and Breach (or hold Breach before you have used a frequency) to link to a point along your aim. The range starts at 30 blocks. At Power Level 10
and again at 15 it matches Space-Time Manipulation's Fold Space range (230 and 300 blocks with default settings)
through loaded terrain. Both mouths follow the pitch of your view. Aiming into open sky can put an exit in
midair. Aiming at the ground places the exit at a clear landing with room between the mouths.

Look steeply down (at least 60°) while standing to open the near mouth flat in the ground under your feet. You
drop through and fall out of a mouth that opens three blocks above clear ground near where you aimed. When the
ground you aimed at is right under you, the exit looks for room about twelve blocks ahead. Looking into the hole
shows the ground waiting below the far mouth. Allies and mobs who step onto the hole follow you down. Jump up through
the far mouth to come back out a step beside the hole. Nobody is taken again for a second and a half after a drop, so
nobody falls straight back. Blasts don't pass through floor breaches.

Breach travel requires room for the opening and the traveler. You come out on the side you were heading for,
turned by the angle between the two mouths. Mobs can cross if they fit. The mod's story and rewind
dimensions keep their own entrances and exits.

## Skills and classes

Every passive ties two moves together.

- **Concussion.** **Through the Seam** sends a blast that crosses one of your open Breaches on out of the other mouth. **Return Address** marks whoever fires a shot your guard catches. **Backscatter** makes each banked shot hit harder on Return Fire, and **Overflow** bursts a bank of three or more into interference where Return Fire stops. **Vibe Lance** is a narrow long-range shot, and **Reverb** opens with both palms.
- **Breaching.** **Slingshot** makes your next blast instant and 30% harder for three seconds after you cross your own Breach. **Wayfarer** and **Safe Horizon** give travelers Speed, Resistance and put out their fire. **Glimpse** opens a window to one of your frequencies for ten seconds: you can see the place through it, but nothing crosses (blasts included). **World Frequency** reaches frequencies in other dimensions and holds two more. **Flashstep** folds a short distance in one motion. **Rift Beast**, the signature ability under World Frequency, opens a breach to a hostile world and pulls a Nether beast through to fight for you for 30 to 50 seconds (longer at higher tiers, and tougher). Like a tamed wolf it follows you, goes for whatever you hit, whoever hits you or anything hunting you, and never turns on you or your friends; if it falls more than 22 blocks behind, it breaches back to your side. Press again to send it at whatever you look at (or, looking at nothing, back to heel); Shift-press sends it home. It never zombifies, drops nothing, and goes back through a breach when its time is up.
- **Dimensional Sight.** **Residual Sense** marks anyone who hurts you for six seconds. **Deep Read** replays more hits and marks for longer. **Co-Vibe** lets allies see a replay with you. **Afterimage** makes a blast slow marked targets, and **Prism Lock** collapses a zone of interference that damages and slows (and stops a speedster's run); **Fixed Point** holds a read enemy in Prism Lock or Rift Fall longer.
- The class abilities are **Crosswave** (Offense), **Mirror Breach** (Defense) and **Sanctuary Breach** (Support). Sanctuary extinguishes travelers and gives them Resistance. Catch 40 hostile projectiles with a guard for the Evolved trial.

## Breach Boxing (fighting style)

With an empty hand, a click throws the next strike and a double-tapped direction dodges, as in every fighting style
(see [fighting-styles.md](fighting-styles.md)). Cisco fights through the seams between places:

| Move | What happens |
| --- | --- |
| Jab (lead and rear) | A vibrating palm. The pulse hits the first foe within 4.5 blocks and rings on through them into one standing behind. |
| Breach Kick (finisher, from level 2) | Your foot goes into a small tear at your side and comes out of one behind a foe up to 7 blocks away, kicking them toward you. |
| Seam Sweep (crouch) | A palm on the ground cracks a seam six blocks forward; everyone on it is tripped up and slowed. |
| Breach Hop (left or right) | A tear snaps open at your side, you dive into it, and a tenth of a second later you shoot out of a second tear six blocks along, still moving that way. Blows can't land on you while you're between them. With no room for the hop, you blink 3.6 blocks instead. |
| Fold Back (back, from level 2) | You step 4 blocks back through a breach, and the spot you left bursts a moment later, throwing foes out. |
| Counter | A blow you dodge loads your next strike, which comes out of a tear at the attacker's back and throws them toward you. |

## Look and feel

A held Breach locks the casting arm straight out at the tear, shaking with the blast, the other arm drawn back to brace. The blast is a white core with two jagged blue bolts re-struck every other tick and small fronts riding it into the tear, which grows from a spark into the iris with arcs jumping round its rim and a swirl turning inside. The palm winds up around a tightening pair of crescents. Each release sends a staggered train of blue fronts, each a lens of pressed air with a bright rim and two split crescents, leaving a fading tube of rings along the path and twisting filaments. Breaches stretch a vertical cut into a deep-blue iris, with a moving lip and inward-turning bands. They pinch shut into a flash. The guard's inner arcs brighten as it banks impacts. During a read, the fighters come back as see-through ghosts of their own models, one blow after another. A held Vibe Blast is a white core inside three jagged bolts with wave fronts riding it and a splash where it lands. Rift Fall's ring fills white while hairline tears flicker across the floor and light rises from its rim; the sky breach crackles at its edge, both palms feed it, and each body falls trailing light to a flash on the ground. Each core move has a first- and third-person pose, with original synthesized cues for the wave, breach, snap, vision and collapse.

The awakening opens two flickering tears around the caster and tries to breach the endangered player or ally out of the fight. If the nearby path is blocked, the interference gives the ward space to escape.

Test the power with `/heroesevolved power give @s vibe` on a player without an assigned power.

- **Game tests:** `VibeGameTests` covers the five core moves and a breach to a Nether frequency; `VibeFrequencyGameTests`
  covers frequency slots, tuning, renaming, the wheel's free press, breaches and glimpses to a frequency, the terrain
  each mouth is sent and the view's turn; `VibeCombatGameTests` covers floor breaches, the Rift Beast and Breach Boxing;
  `VibeKitGameTests` covers every active
  skill, the class abilities, the passives that link two moves, the speedster rule and both outcomes of the awakening;
  `VibeVisionGameTests` covers the held breach (the slow channel, following the look, letting go early, the last
  frequency and Shift) and visions (an ally below three hearts more than 40 blocks off, touching another player's
  chest or tamed wolf, the double and being torn back, the hologram and its voice, only
  watching below Level 12, seeing a village's trouble before it starts, and waking in your body after a crash).
  `VibeGameTests` also covers the held blast (it stops at cover, hits on, drains and ends on release), reading a scene
  after the fighters are gone, and Rift Fall (the warning, the drop, the slam, a ravager slammed in place). Run
  them with the rest of the suite: `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`, or
  on their own with `tools/vibe-gametest.init.gradle`.
- **Photographs:** `./gradlew runClient --init-script tools/vibe-preview.init.gradle` casts every core move, active skill,
  class ability, Through the Seam, Overflow, the awakening, the frequency wheel and rename field, and the view through a
  breach to a walled garden (from behind, first person, at an angle, up close, looking back and through a glimpse). It saves shots to
  `build/vibe-preview/screenshots/`. `./gradlew runClient --init-script tools/vibe-vision-preview.init.gradle`
  photographs a held breach from the side, in first person and turning with the look, a vision from inside it, a
  hologram as the ally sees it, the held Vibe Blast, a read's ghosts, a Breach Hop, Rift Fall from warning to landing,
  and beds and chests standing behind a breach (which must not show through it), into
  `build/vibe-vision-preview/screenshots/`.

## Finishers: Off Frequency, Breach Drop, Point Blank

From Power Level 3, a foe low enough to finish glistens gold. Press **C** near it (within 4 blocks). Shift + C switches finishers off and on; the rules every finisher shares are in [finishers.md](finishers.md).

- **What you see.** Wave fronts run between two blue panes either side of the foe. It splits into three copies tinted blue, red and green like a bad signal, buzzing; they drift apart, you snap your fingers, and they blink out one at a time with a breach's pop. Nothing is left.
- **Marked foes** (Residual Sense, Return Address) within 12 blocks each take the overkill along a filament; with none marked it spills nearby.
- **Reward:** Breach Guard is ready again.

Each press of C plays one of three at random, never the one you used last. Off Frequency is described above; the two alternates give the same reward.

- **Breach Drop.** A breach opens flat under the foe; it falls through, and the breach snaps shut over it.
- **Point Blank.** Both palms on the foe, wave fronts gathering in them, and a Vibe Blast goes straight through it and out of its back.
