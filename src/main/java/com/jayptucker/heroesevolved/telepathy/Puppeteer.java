package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puppeteer (Telepathy, Puppet Master signature): strings of thought drop from the telepath's fingers onto up to four
 * foes within 14 blocks, and for six seconds each one turns on the nearest other foe instead of on her or her friends
 * (one with nobody to fight hangs still). When the time is up, or on a second press, the strings are cut: each puppet
 * takes 3 + tier and is left reeling (slow and weak for two seconds). Bosses can't be strung. The art
 * ({@code TelepathyArt.strings}, {@code TelepathyArt.snip}) is re-sent while the strings hold.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Puppeteer {
    public static final String ID = "puppeteer";
    public static final int HOLD = 120, MAX = 4, REFRESH = 10;
    public static final double RANGE = 14, FIGHT_RANGE = 16;
    /** Every strung mob and who holds its strings. */
    private static final Map<UUID, ServerPlayer> HELD = new HashMap<>();

    private Puppeteer() {}

    private static List<Mob> candidates(ServerPlayer p) {
        List<Mob> out = new ArrayList<>();
        for (LivingEntity t : Signatures.foes(p, p.getEyePosition(), RANGE, true))
            if (t instanceof Mob mob && !Signatures.boss(mob) && out.size() < MAX) out.add(mob);
        return out;
    }

    public static boolean canUse(ServerPlayer p) {
        if (!candidates(p).isEmpty()) return true;
        p.displayClientMessage(Component.literal("No one in reach to string.").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    public static boolean activate(ServerPlayer p, int tier) {
        List<Mob> puppets = candidates(p);
        if (puppets.isEmpty()) return false;
        Run run = new Run(p, tier, puppets);
        if (!Signatures.start(ID, run)) return false;
        for (Mob m : puppets) HELD.put(m.getUUID(), p);
        run.pull();
        run.level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4F, .6F);
        run.level.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1, .8F);
        p.displayClientMessage(Component.literal("Dance.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** Whether this mob is on someone's strings (and whose). */
    public static ServerPlayer holder(LivingEntity mob) { return HELD.get(mob.getUUID()); }

    static final class Run extends Signatures.Run {
        final List<Mob> puppets;

        Run(ServerPlayer player, int tier, List<Mob> puppets) { super(player, tier); this.puppets = new ArrayList<>(puppets); }

        @Override protected boolean tick() {
            puppets.removeIf(m -> {
                boolean gone = !m.isAlive() || m.level() != level;
                if (gone) HELD.remove(m.getUUID());
                return gone;
            });
            if (puppets.isEmpty()) { finish(false); return false; }
            if (age % REFRESH == 0) pull();
            if (age >= HOLD) { cut(); return false; }
            return true;
        }

        @Override protected boolean press() {
            cut();
            Signatures.end(ID, player);
            return true;
        }

        @Override protected void cancel() { finish(false); }

        /** Each puppet is turned on the nearest other foe; one with nobody to fight hangs still. */
        void pull() {
            for (Mob m : puppets) {
                Mob prey = level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(FIGHT_RANGE),
                                o -> o != m && o.isAlive() && ExpansionSkills.hostile(player, o))
                        .stream().min(Comparator.comparingDouble(o -> o.distanceToSqr(m))).orElse(null);
                m.setTarget(prey);
                if (prey == null) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, REFRESH + 4, 5, false, false), player);
                SetPiecePayload.sendSeeded(level, Piece.TELEPATHY_STRINGS, m.position(), player.position(), m.getBbHeight(), m, 0, player.getId());
            }
            ArmPosePayload.show(player, ArmPose.TELE_PUPPET, REFRESH + 6);
        }

        /** The strings are cut: each puppet is hurt and left reeling. */
        void cut() {
            float damage = 3 + tier;
            for (Mob m : puppets) {
                if (!m.isAlive()) continue;
                Signatures.hit(m, player.damageSources().indirectMagic(player, player), damage);
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), player);
                SetPiecePayload.sendSeeded(level, Piece.TELEPATHY_SNIP, m.position(), player.position(), m.getBbHeight(), m, 0, player.getId());
            }
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.2F, 1.4F);
            finish(true);
        }

        private void finish(boolean cut) {
            for (Mob m : puppets) {
                HELD.remove(m.getUUID());
                if (!cut && m.isAlive() && m.getTarget() != null && ExpansionSkills.hostile(player, m.getTarget())) m.setTarget(null);
            }
        }
    }

    /** A puppet may not turn on the one holding its strings, or her friends, while they hold. */
    @SubscribeEvent public static void retarget(LivingChangeTargetEvent event) {
        ServerPlayer holder = HELD.get(event.getEntity().getUUID());
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (holder != null && next != null && !ExpansionSkills.hostile(holder, next)) event.setCanceled(true);
    }

    /** For tests: the mobs this caster has strung (empty when none). */
    public static List<Mob> puppets(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run == null ? List.of() : List.copyOf(run.puppets);
    }
}
