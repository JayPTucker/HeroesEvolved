package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/** Telepathy's three Ultimates; {@code TelepathyArt} draws them from these beats. */
public final class TelepathyUltimates {
    private TelepathyUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(Mindbreak::new, 12);
    public static final Ultimate DEFENSE = Ultimate.defense(NotHere::new);
    public static final Ultimate SUPPORT = Ultimate.support(OneMind::new);

    private static final String POWER = "telepathy";

    /** A mob forgets who it was after: its target, its brain's attack target and its path. */
    public static void hush(Mob mob) {
        mob.setTarget(null);
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getNavigation().stop();
    }

    // ------------------------------------------------------------------
    // Offense: Mindbreak
    // ------------------------------------------------------------------

    /**
     * Two fingers to the temple and a thread of violet runs to the foe; the world goes dark round it. Its own fears rise
     * out of the dark (copies of itself, the colour of a bruise) and walk through it one after another while splinters of
     * memory circle its head. The telepath's hand closes, the splinters drive in, and a spike of light from the temple
     * breaks the mind: the foe drops, stunned, and the minds near it turn on one another.
     */
    public static final class Mindbreak extends Run {
        public static final int THREAD = 10, DARK = 12, FEAR = 20, FEARS = 3, EVERY = 16, CLOSE = 62, SPIKE = 82, TICKS = 120;
        public static final double FEAR_START = 3;

        public Mindbreak(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "psychic_storm", foe); }

        @Override protected int length() { return TICKS; }

        /** How far fear {@code i} is from the foe at {@code t} (it walks in, through, and is gone). */
        public static float fearDistance(int i, float t) {
            float start = FEAR - 6 + i * EVERY;
            return (float) (FEAR_START * (1 - Math.min(1, Math.max(0, (t - start) / 8))));
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.TEMPLE, SPIKE);
            piece(Piece.TELE_ULT_MIND, origin, base, foe.getBbHeight());
            piece(Piece.TELE_ULT_FEARS, foe.position(), foe.position(), foe.getBbHeight(), foe, 0);
            sound(player.position(), SoundEvents.BEACON_DEACTIVATE, 1, .5F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.2, 1.6, 1.7, d, 0, 1.6)
                    .key(DARK + 6, d - 3.2, 1.8, 1.8, d, 0, 1.6)
                    .key(FEAR + EVERY * 2, d - 2.6, -2.4, 1.4, d, 0, 1.8)
                    .key(CLOSE, -1.4, 1.2, 1.8, d, 0, 1.6)
                    .key(SPIKE + 2, d * .5, 3.5, 2.4, d * .5, 0, 1.6)
                    .key(TICKS - 14, d - 5, 5, 3.5, d, 0, .8)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == DARK) {
                if (player.connection != null) ScreenTintPayload.send(player, 0x80241638, SPIKE - DARK, ScreenTintPayload.Style.VIGNETTE);
                sound(foeCentre(), SoundEvents.AMBIENT_CAVE.value(), 1, .6F);
            }
            for (int i = 0; i < FEARS; i++) if (age == FEAR + i * EVERY) {
                zap(foe, 2);
                Takedowns.flinch(foe);
                sound(foeCentre(), SoundEvents.WARDEN_HEARTBEAT, 1.4F, .5F + i * .2F);
                sound(foeCentre(), SoundEvents.SCULK_SHRIEKER_SHRIEK, .5F, 1.4F);
            }
            if (age == CLOSE) {
                pose(ArmPose.TELE_COMMAND, SPIKE - CLOSE + 6);
                sound(player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, .5F);
            }
            if (age == SPIKE) {
                zap(foe, 16);
                StyleOps.effect(foe, MobEffects.MOVEMENT_SLOWDOWN, 70, 6);
                StyleOps.effect(foe, MobEffects.WEAKNESS, 70, 2);
                if (foe instanceof Mob mob) hush(mob);
                List<LivingEntity> near = others(foeAt(), 6);
                for (LivingEntity t : near) {
                    zap(t, 4);
                    StyleOps.effect(t, MobEffects.CONFUSION, 100, 0);
                    if (t instanceof Mob mob && !Signatures.boss(mob)) {
                        LivingEntity other = near.stream().filter(o -> o != t).findFirst().orElse(null);
                        if (other != null) mob.setTarget(other);
                    }
                }
                sound(foeCentre(), SoundEvents.GLASS_BREAK, 1.6F, .5F);
                sound(foeCentre(), SoundEvents.WARDEN_SONIC_BOOM, .6F, 1.8F);
                shake(foeAt(), 16, .35F, 8);
                release();
            }
        }
    }

    // ------------------------------------------------------------------
    // Defense: Not Here
    // ------------------------------------------------------------------

    /**
     * For ten seconds the telepath is simply not there to anyone hunting them within twenty-four blocks: they lose
     * track, and three copies of the telepath walk off in different directions, which they follow instead. The telepath
     * is unseen while it lasts. When it ends the copies come apart.
     */
    public static final class NotHere extends Run {
        public static final int TICKS = 200, INTRO = 22;
        public static final double RANGE = 24, WALK = .09;
        private final List<Mob> fooled = new ArrayList<>();

        public NotHere(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "blind_spot", null); }

        @Override protected int length() { return TICKS; }

        /** Where copy {@code i} of three has walked to {@code t} ticks in, from where the telepath stood. */
        public static Vec3 double_(Vec3 origin, Vec3 facing, int i, float t) {
            double a = Math.atan2(facing.z, facing.x) + (i - 1) * 2.1 + Math.PI;
            return origin.add(Math.cos(a) * WALK * t, 0, Math.sin(a) * WALK * t);
        }

        @Override protected void open() {
            cue(true);
            pose(ArmPose.TEMPLE, 24);
            heroShot(INTRO, .5);
            // The body itself is not drawn while it lasts (the doubles' piece hides it), so nobody sees where they really are.
            piece(Piece.TELE_ULT_DOUBLES, origin, origin.add(facing), 1, player, 0);
            sound(player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, .7F);
        }

        @Override protected void beat(int age) {
            if (age % 5 != 0) return;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> m.getTarget() == player
                    || m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player)) {
                hush(mob);
                if (!fooled.contains(mob)) fooled.add(mob);
            }
            for (int i = 0; i < fooled.size(); i++) {
                Mob mob = fooled.get(i);
                if (!mob.isAlive() || Signatures.boss(mob)) continue;
                Vec3 to = double_(origin, facing, i % 3, age);
                mob.getNavigation().moveTo(to.x, to.y, to.z, 1);
            }
        }

        @Override protected void finish() {
            for (int i = 0; i < 3; i++) {
                Vec3 at = double_(origin, facing, i, TICKS);
                piece(Piece.TELE_ULT_POP, at, at, 1, null, i);
            }
            sound(player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1, 1.4F);
        }

        public List<Mob> fooled() { return fooled; }
    }

    // ------------------------------------------------------------------
    // Support: One Mind
    // ------------------------------------------------------------------

    /**
     * Up to eight friends within fourteen blocks become one mind for fifteen seconds. Every wound is shared out evenly
     * among them; every foe within twenty-four blocks is lit up for all of them to see; and together they think faster,
     * hit harder and mend.
     */
    public static final class OneMind extends Run {
        public static final int TICKS = 300, INTRO = 22, MAX = 8;
        public static final double RANGE = 14;
        private final List<LivingEntity> linked = new ArrayList<>();
        private boolean sharing;

        public OneMind(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "hive_mind", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.TELE_HIVE, 24);
            heroShot(INTRO, 1);
            List<LivingEntity> all = friends(RANGE);
            linked.addAll(all.subList(0, Math.min(MAX, all.size())));
            for (LivingEntity f : linked) {
                StyleOps.effect(f, MobEffects.DIG_SPEED, TICKS, 1);
                StyleOps.effect(f, MobEffects.DAMAGE_BOOST, TICKS, 0);
                StyleOps.effect(f, MobEffects.REGENERATION, TICKS, 0);
                piece(Piece.TELE_ULT_LINK, player.position(), f.position(), f.getBbHeight(), f, 0);
            }
            // One mind, one voice: the linked players hear each other from anywhere while it lasts.
            TelepathySkills.linkVoices(player, linked, TICKS);
            sound(player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, .8F);
        }

        @Override protected void beat(int age) {
            if (age % 20 == 0 && age < TICKS) {
                for (LivingEntity t : StyleOps.foes(player, player.position(), 24)) StyleOps.effect(t, MobEffects.GLOWING, 30, 0);
                if (age % 60 == 0) for (LivingEntity f : linked) if (f.isAlive() && f != player)
                    piece(Piece.TELE_ULT_LINK, player.position(), f.position(), f.getBbHeight(), f, age / 60);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (sharing || !linked.contains(victim) || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            List<LivingEntity> others = linked.stream().filter(e -> e != victim && e.isAlive() && !e.isRemoved() && e.level() == victim.level()
                    && e.distanceToSqr(victim) < 32 * 32).toList();
            if (others.isEmpty()) return;
            float share = event.getAmount() / (others.size() + 1);
            event.setAmount(share);
            sharing = true;
            try {
                for (LivingEntity o : others) {
                    o.invulnerableTime = 0;
                    o.hurt(victim.damageSources().magic(), share);
                }
            } finally { sharing = false; }
        }

        public List<LivingEntity> linked() { return linked; }
    }
}
