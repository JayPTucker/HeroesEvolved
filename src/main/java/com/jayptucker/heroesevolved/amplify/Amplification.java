package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.SuperchargedPayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Being amplified: the other side of Power Amplification. An ally zapped with red lightning is <b>supercharged</b>.
 *
 * <ul>
 *   <li>A <b>Surge</b> (15 s) makes everything they hit hit 25% harder, their powers cost 25% less stamina and recover
 *   25% faster, and their held powers (flights, stopped time, beams) drain half as fast. Their own power also gets its
 *   bigger version: each power asks {@link #surged} where it decides the thing being changed ({@code AmpTuned} lists
 *   them).</li>
 *   <li>An <b>Overcharge</b> (6 s, on top of a Surge) pushes their power "a thousand times": something it can't normally
 *   do, which each power asks {@link #overcharged} about, then a backlash when it ends.</li>
 * </ul>
 *
 * <p>This class is the one place that answers those questions, so other powers never touch the amplifier's state. A
 * {@link Effect} on the body shows the icon; {@link SuperchargedPayload} tells everyone watching, so the crackle is drawn
 * on any body ({@code AmpArt}).</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Amplification {
    public static final int SURGE_TICKS = 300, OVERCHARGE_TICKS = 120, TOO_SOON = 600;
    public static final float SURGE_DAMAGE = 1.25F, OVERCHARGE_DAMAGE = 1.5F, SURGE_COST = .75F, SURGE_COOLDOWN = .75F,
            SURGE_DRAIN = .5F;

    /** The icon a supercharged body wears. Milk doesn't wash red lightning out. */
    public static final class Effect extends MobEffect {
        public Effect() { super(MobEffectCategory.BENEFICIAL, 0xE0263A); }

        @Override public void fillEffectCures(java.util.Set<net.neoforged.neoforge.common.EffectCure> cures, MobEffectInstance instance) {}
    }

    /** One supercharged body. */
    static final class Boost {
        final LivingEntity target;
        ServerPlayer by;
        long start, until, overStart = -1, overUntil = -1;
        boolean over, safe, breakthrough;
        /** How hard the backlash bites: 1, or half for a partner who learned Not Too Much. */
        float backlash = 1;
        /** The power this body had when it was zapped (a player's, or a gifted mob's), or null. */
        ResourceLocation power;
        /** Scratch for each power's Overcharge (a run done, bolts left, a dome's centre). */
        int used, count;
        long mark;
        Vec3 at;
        int sentLevel = -1;

        Boost(LivingEntity target) { this.target = target; }

        boolean player() { return target instanceof ServerPlayer; }
    }

    static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    /** When each body was last overcharged, and how many times in a row too soon. */
    private static final Map<UUID, long[]> LAST_OVER = new HashMap<>();
    /** Backlash locks: a power that won't answer for a while (a telepath deafened by a thousand minds). */
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();
    /** Bodies an amplifier has overloaded: their next move misfires (Overcharge the Enemy). */
    private static final Map<UUID, Long> WILD = new HashMap<>();

    private record Lock(ResourceLocation power, long until) {}

    private Amplification() {}

    // ------------------------------------------------------------------
    // The questions other powers ask
    // ------------------------------------------------------------------

    /** Supercharged at all (a Surge, or an Overcharge, which includes one). */
    public static boolean surged(Entity entity) {
        Boost b = entity == null ? null : BOOSTS.get(entity.getUUID());
        return b != null && b.target == entity;
    }

    /** Overcharged right now: the "thousand times" moment. */
    public static boolean overcharged(Entity entity) {
        Boost b = entity == null ? null : BOOSTS.get(entity.getUUID());
        return b != null && b.target == entity && b.over;
    }

    /** 0 not supercharged, 1 a Surge, 2 an Overcharge. */
    public static int level(Entity entity) { return overcharged(entity) ? 2 : surged(entity) ? 1 : 0; }

    /** How much farther a supercharged power reaches (Fold Space, Read Mind, Technopathy's control). */
    public static double reach(Entity entity) { return surged(entity) ? 2 : 1; }

    /** The stamina a supercharged player pays for a move of {@code power}. */
    public static double cost(ServerPlayer player, ResourceLocation power) { return cost(player, power, null); }

    /** The stamina a supercharged player pays for {@code action} (an overcharged empath's Echoes are free). */
    public static double cost(ServerPlayer player, ResourceLocation power, ResourceLocation action) {
        if (!surged(player)) return 1;
        return overcharged(player) && echo(power, action) ? 0 : SURGE_COST;
    }

    /** How long a supercharged player's cooldowns run. */
    public static double cooldown(ServerPlayer player, ResourceLocation power) { return cooldown(player, power, null); }

    /** How long a supercharged player's cooldown for {@code action} runs (an overcharged empath's Echoes have none). */
    public static double cooldown(ServerPlayer player, ResourceLocation power, ResourceLocation action) {
        if (!surged(player)) return 1;
        return overcharged(player) && echo(power, action) ? 0 : SURGE_COOLDOWN;
    }

    /** One of Empathic Mimicry's Echo keys (not its tree's or classes' moves). */
    private static boolean echo(ResourceLocation power, ResourceLocation action) {
        return ModAbilities.MIMICRY_ID.equals(power) && action != null && action.getPath().startsWith("mimicry_echo_");
    }

    /** How fast a supercharged player's held powers drain. */
    public static double drain(ServerPlayer player) { return surged(player) ? SURGE_DRAIN : 1; }

    /** Breakthrough: a partner on a Surge uses moves one tier above what they've unlocked. */
    public static int tierBonus(ServerPlayer player) {
        Boost b = BOOSTS.get(player.getUUID());
        return b != null && b.target == player && b.breakthrough ? 1 : 0;
    }

    /** Vibe's overcharged breach overshoots, unless the amplifier has learned to dial it in. */
    public static Vec3 overshoot(ServerPlayer player, Vec3 aimed) {
        Boost b = BOOSTS.get(player.getUUID());
        if (b == null || !b.over || b.backlash < 1) return aimed;
        double angle = player.getRandom().nextDouble() * Math.PI * 2, distance = 12 + player.getRandom().nextDouble() * 18;
        return aimed.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    /**
     * Whether a backlash has switched this power off for now (a telepath deafened, a singer's voice gone, a shapeshifter
     * stuck): tells the player and returns true.
     */
    public static boolean locked(ServerPlayer player, ResourceLocation power) {
        Lock lock = LOCKS.get(player.getUUID());
        if (lock == null || !lock.power.equals(power)) return false;
        if (player.serverLevel().getGameTime() >= lock.until) { LOCKS.remove(player.getUUID()); return false; }
        player.displayClientMessage(Component.translatable("amp.heroesevolved.locked").withStyle(ChatFormatting.RED), true);
        return true;
    }

    static void lock(ServerPlayer player, ResourceLocation power, int ticks) {
        if (power == null || ticks <= 0) return;
        LOCKS.put(player.getUUID(), new Lock(power, player.serverLevel().getGameTime() + ticks));
    }

    /** Overcharge the Enemy: the body's next move goes wild. */
    static void markWild(LivingEntity target, int ticks) {
        WILD.put(target.getUUID(), target.level().getGameTime() + ticks);
    }

    /**
     * Asked by a power (a player's key, a gifted mob's move) as it is about to act. A body an amplifier overloaded
     * misfires instead: it hurts itself, its power lashes at whatever is round it, and the move is lost.
     */
    public static boolean misfire(LivingEntity body) {
        Long until = WILD.get(body.getUUID());
        if (until == null) return false;
        WILD.remove(body.getUUID());
        if (body.level().getGameTime() > until || !(body.level() instanceof ServerLevel level)) return false;
        AmpService.misfire(level, body);
        return true;
    }

    public static boolean wild(LivingEntity body) {
        Long until = WILD.get(body.getUUID());
        return until != null && body.level().getGameTime() <= until;
    }

    /** A power was used (a key pressed, a gifted mob's move landed): amplifiers near feel it as Charge. */
    public static void powerUsed(LivingEntity user, boolean ultimate) {
        if (user.level() instanceof ServerLevel level) AmpService.felt(level, user, ultimate ? 4 : 2);
    }

    // ------------------------------------------------------------------
    // Starting and ending
    // ------------------------------------------------------------------

    /** The power a body holds, for its Tuned upgrades: a player's own, a gifted mob's, or null (a pet). */
    static ResourceLocation powerOf(LivingEntity target) {
        if (target instanceof ServerPlayer player)
            return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                    .map(Map.Entry::getKey).orElse(null);
        return GiftedMobs.powerOf(target);
    }

    /** Surges {@code target} for {@code ticks} (refreshing a Surge already running; never shortening one). */
    static Boost surge(ServerPlayer by, LivingEntity target, int ticks) {
        long now = target.level().getGameTime();
        Boost b = BOOSTS.get(target.getUUID());
        boolean fresh = b == null || b.target != target;
        if (fresh) {
            b = new Boost(target);
            b.start = now;
            b.power = powerOf(target);
            BOOSTS.put(target.getUUID(), b);
        }
        b.by = by;
        b.until = Math.max(b.until, now + ticks);
        b.breakthrough = by != null && AmpSkills.has(by, AmpSkills.BREAKTHROUGH) && AmpService.isPartner(by, target);
        b.backlash = by != null && AmpSkills.has(by, AmpSkills.NOT_TOO_MUCH) && AmpService.isPartner(by, target) ? .5F : 1;
        if (fresh) AmpTuned.surgeStart(b);
        show(b, Piece.AMP_SURGE, 1);
        refresh(b);
        if (target instanceof ServerPlayer p) {
            p.displayClientMessage(Component.translatable("amp.heroesevolved.surged").withStyle(ChatFormatting.RED), true);
            com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
        }
        return b;
    }

    /**
     * Overcharges {@code target}: 6 s of their power "a thousand times", then a backlash (none when {@code safe}, as A
     * Thousand Times gives). Overcharging the same body again within 30 s hurts it.
     */
    static Boost overcharge(ServerPlayer by, LivingEntity target, boolean safe) {
        long now = target.level().getGameTime();
        Boost b = surge(by, target, SURGE_TICKS);
        long[] last = LAST_OVER.get(target.getUUID());
        if (!safe && last != null && now - last[0] < TOO_SOON) {
            last[1]++;
            float harm = (last[1] >= 2 ? 8 : 4) * b.backlash;
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().magic(), harm);
            show(b, Piece.AMP_BACKLASH, 1);
            if (target instanceof ServerPlayer p)
                p.displayClientMessage(Component.translatable("amp.heroesevolved.too_soon").withStyle(ChatFormatting.DARK_RED), true);
        } else if (last == null || now - last[0] >= TOO_SOON) last = new long[]{now, 0};
        if (last != null) { last[0] = now; LAST_OVER.put(target.getUUID(), last); }
        if (b.over) AmpTuned.overEnd(b, false);
        b.over = true;
        b.safe = safe;
        b.used = 0;
        b.count = 0;
        b.mark = 0;
        b.at = null;
        b.overStart = now;
        b.overUntil = now + OVERCHARGE_TICKS;
        b.until = Math.max(b.until, b.overUntil);
        show(b, Piece.AMP_OVERCHARGE, 1);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER.value(),
                SoundSource.PLAYERS, .7F, 1.6F);
        AmpTuned.overStart(b);
        refresh(b);
        if (target instanceof ServerPlayer p)
            p.displayClientMessage(Component.translatable("amp.heroesevolved.overcharged").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return b;
    }

    /** Ends a body's supercharge now (death, logout, a respec). */
    static void end(LivingEntity target) {
        Boost b = BOOSTS.remove(target.getUUID());
        if (b == null) return;
        if (b.over) AmpTuned.overEnd(b, false);
        target.removeEffect(ModItems.SUPERCHARGED);
        sync(b, 0, 0);
        if (target instanceof ServerPlayer p) com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
    }

    static void show(Boost b, Piece piece, float scale) {
        if (b.target.level() instanceof ServerLevel level)
            com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, piece, b.target.position(),
                    b.target.position().add(0, b.target.getBbHeight(), 0), Math.max(.6F, b.target.getBbHeight()) * scale, b.target);
    }

    /** Puts the icon on the body and tells everyone watching how strongly it is supercharged. */
    private static void refresh(Boost b) {
        long now = b.target.level().getGameTime();
        int level = b.over ? 2 : 1;
        int left = (int) Math.max(1, (b.over ? b.overUntil : b.until) - now);
        b.target.addEffect(new MobEffectInstance(ModItems.SUPERCHARGED, (int) Math.max(1, b.until - now), b.over ? 1 : 0, false, false, true));
        sync(b, level, left);
    }

    private static void sync(Boost b, int level, int ticks) {
        b.sentLevel = level;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(b.target, new SuperchargedPayload(b.target.getId(), level, ticks));
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!BOOSTS.isEmpty()) {
            for (Boost b : List.copyOf(BOOSTS.values())) {
                try {
                    step(b);
                } catch (RuntimeException broken) {
                    HeroesEvolved.LOGGER.error("A supercharge failed", broken);
                    end(b.target);
                }
            }
        }
        if (server.getTickCount() % 200 == 0) {
            long now = server.overworld().getGameTime();
            LOCKS.values().removeIf(l -> l.until < now - 20);
            LAST_OVER.values().removeIf(l -> now - l[0] > TOO_SOON * 2L);
        }
        AmpService.tick(server);
    }

    private static void step(Boost b) {
        LivingEntity target = b.target;
        if (target.isRemoved() || !target.isAlive()) { end(target); return; }
        long now = target.level().getGameTime();
        // Close Friends: a Surge runs at half speed while its amplifier stays within arm's reach.
        if (!b.over && b.by != null && !b.by.isRemoved() && AmpSkills.has(b.by, AmpSkills.CLOSE_FRIENDS)
                && b.by.level() == target.level() && b.by.distanceToSqr(target) <= 9 && now % 2 == 0) b.until++;
        if (b.over) {
            AmpTuned.overTick(b, now);
            if (now >= b.overUntil) {
                b.over = false;
                AmpTuned.overEnd(b, !b.safe);
                refresh(b);
            }
        }
        if (now >= b.until) { end(target); return; }
        if (now % 40 == 0) refresh(b);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /** Everything a supercharged body hits, hits harder. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void incoming(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && attacker != event.getEntity()) {
            Boost b = BOOSTS.get(living.getUUID());
            if (b != null && b.target == living)
                event.setAmount(event.getAmount() * (b.over ? OVERCHARGE_DAMAGE : SURGE_DAMAGE));
            // Mechanic: a gun Jumpstart charged hits a fifth harder, for its next 30 hits.
            var gun = living.getMainHandItem();
            var data = gun.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && data.contains(AmpService.GUN_ROUNDS)) {
                int left = data.copyTag().getInt(AmpService.GUN_ROUNDS);
                if (left > 0) {
                    event.setAmount(event.getAmount() * 1.2F);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, gun, tag -> {
                        if (left <= 1) tag.remove(AmpService.GUN_ROUNDS); else tag.putInt(AmpService.GUN_ROUNDS, left - 1);
                    });
                }
            }
        }
        AmpTuned.incoming(event);
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        Boost b = BOOSTS.get(event.getTarget().getUUID());
        if (b == null || b.target != event.getTarget()) return;
        long now = b.target.level().getGameTime();
        PacketDistributor.sendToPlayer(viewer, new SuperchargedPayload(b.target.getId(), b.over ? 2 : 1,
                (int) Math.max(1, (b.over ? b.overUntil : b.until) - now)));
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        end(event.getEntity());
        WILD.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        end(player);
        for (Boost b : List.copyOf(BOOSTS.values())) if (b.by == player) b.by = null;
        LOCKS.remove(player.getUUID());
        AmpService.forget(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AmpService.following(player)) { end(player); return; }
        // Followed through: the new world's client knows nothing of the boost yet.
        Boost b = BOOSTS.get(player.getUUID());
        if (b != null && b.target == player) refresh(b);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (Boost b : List.copyOf(BOOSTS.values())) end(b.target);
        BOOSTS.clear();
        LAST_OVER.clear();
        LOCKS.clear();
        WILD.clear();
        AmpService.clear();
    }

    /** For tests: forgets every supercharge, lock and overload without ending them. */
    public static void reset() {
        BOOSTS.clear();
        LAST_OVER.clear();
        LOCKS.clear();
        WILD.clear();
    }
}
