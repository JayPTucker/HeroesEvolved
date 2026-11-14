package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.network.SuspicionPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suspicion: how close a disguise is to being seen through (docs/shapeshifting.md, "Blending in"). It rises when the
 * wearer acts out of character where their kind can see, and falls while none of their kind is looking. At 100 the
 * disguise is <b>exposed</b>: the plates burst off, the wearer glows, every one of that kind nearby turns on them, and
 * Morph rests for ten seconds. A blow worth 30% of the form's health breaks the form too, more briefly. A telepath's
 * read or a Read the Vibe sees through any disguise at once.
 *
 * <p>For a person's shape, "their kind" is the people around: any other player who can see the wearer.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftSuspicion {
    public static final float FULL = 100, KIN_HURT = 40, POWER = 25, HURT_SEEN = 15, DECAY_PER_SECOND = 5;
    /** How far a watcher can be and still notice, and how far an exposure carries. */
    public static final double WATCH_RANGE = 16;
    /** How long Morph rests after an exposure, and after a heavy blow broke the form. */
    public static final int EXPOSED_LOCK = 200, BROKEN_LOCK = 60, EXPOSED_GLOW = 100;
    /** A single blow worth this share of the form's max health breaks it. */
    public static final float HEAVY_BLOW = .3F;
    /** Tamed wolves and cats within this many blocks notice a disguise that isn't their owner's. */
    public static final double PET_RANGE = 8;

    private static final Map<UUID, Long> LOCKED = new HashMap<>();
    private static final Map<UUID, Float> SENT = new HashMap<>();
    private static final Map<UUID, Long> LAST_GROWL = new HashMap<>();

    private ShapeshiftSuspicion() {}

    /** Suspicion now (0 in your own body). */
    public static float value(ServerPlayer player) {
        ShapeshiftService.Morph m = ShapeshiftService.wearing(player);
        return m == null ? 0 : m.suspicion;
    }

    /** Whether Morph is resting after the wearer was seen through or broken. */
    public static boolean locked(ServerPlayer player) {
        Long until = LOCKED.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    static void tellLocked(ServerPlayer player) {
        long left = LOCKED.getOrDefault(player.getUUID(), 0L) - player.serverLevel().getGameTime();
        ShapeshiftService.say(player, "exposed_wait", Math.max(1, (left + 19) / 20));
    }

    /** Is anyone of the worn form's kind looking at the wearer? */
    public static boolean watched(ServerPlayer player) {
        ShapeshiftService.Morph m = ShapeshiftService.wearing(player);
        if (m == null) return false;
        AABB around = player.getBoundingBox().inflate(WATCH_RANGE);
        if (m.form.person())
            return !player.serverLevel().getEntitiesOfClass(Player.class, around,
                    p -> p != player && !p.isSpectator() && p.isAlive() && p.hasLineOfSight(player)).isEmpty();
        return !player.serverLevel().getEntitiesOfClass(Mob.class, around,
                mob -> mob.isAlive() && m.kin.equals(ShapeshiftForms.kin(mob)) && mob.hasLineOfSight(player)).isEmpty();
    }

    /** Adds suspicion; at 100 the disguise is exposed. */
    public static void raise(ServerPlayer player, float amount) {
        ShapeshiftService.Morph m = ShapeshiftService.wearing(player);
        if (m == null || amount <= 0) return;
        amount = ShapeshiftSkills.suspicionGain(player, amount, m.form.person());
        if (amount <= 0) return;
        m.suspicion = Math.min(FULL, m.suspicion + amount);
        if (m.suspicion >= FULL) expose(player);
        else sync(player, true);
    }

    /** A move the worn form couldn't make (Decoy, Reflection, Nightmare), seen by its kind. */
    public static void power(ServerPlayer player) {
        if (watched(player)) raise(player, POWER);
    }

    /**
     * Seen through: the plates burst off, the wearer glows, every one of the form's kind within 16 blocks turns on them,
     * and Morph rests for ten seconds.
     */
    public static void expose(ServerPlayer player) {
        ShapeshiftService.Morph m = ShapeshiftService.wearing(player);
        if (m == null) return;
        String kin = m.kin;
        boolean person = m.form.person();
        ShapeshiftService.breakForm(player, "exposed");
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, EXPOSED_GLOW, 0, false, false));
        LOCKED.put(player.getUUID(), player.serverLevel().getGameTime() + EXPOSED_LOCK);
        if (!person)
            for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE),
                    mob -> mob.isAlive() && kin.equals(ShapeshiftForms.kin(mob))))
                mob.setTarget(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE,
                SoundSource.PLAYERS, .5F, 1.6F);
    }

    /**
     * A telepath's read or a Read the Vibe landed on {@code target}: if it wears a shape, the reader learns who is under it
     * and the disguise is exposed. True if it did wear one.
     */
    public static boolean read(ServerPlayer reader, LivingEntity target) {
        if (!(target instanceof ServerPlayer worn) || worn == reader || !ShapeshiftService.morphed(worn)) return false;
        reader.displayClientMessage(Component.translatable("shapeshift.heroesevolved.seen_through", worn.getGameProfile().getName()), false);
        PrivateGlow.glow(reader, worn, EXPOSED_GLOW);
        ShapeshiftService.say(worn, "read_through");
        expose(worn);
        return true;
    }

    static void reset(ServerPlayer player) {
        ShapeshiftService.Morph m = ShapeshiftService.wearing(player);
        if (m != null) m.suspicion = 0;
        sync(player, true);
    }

    /** Falls while unwatched; pets that aren't the wearer's notice the disguise. Called from the worn form's tick. */
    static void tick(ServerPlayer player, ShapeshiftService.Morph m, long now) {
        if (now % 5 == 0) {
            if (m.suspicion > 0 && !watched(player))
                m.suspicion = Math.max(0, m.suspicion - DECAY_PER_SECOND / 4 * ShapeshiftSkills.decay(player, m.form.person()));
            sync(player, false);
        }
        if (now % 20 == 0) pets(player, now);
    }

    /** Tamed wolves and cats know a disguise isn't what it looks like: they stare, and growl or hiss now and then. */
    private static void pets(ServerPlayer player, long now) {
        for (TamableAnimal pet : player.serverLevel().getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(PET_RANGE),
                a -> (a instanceof Wolf || a instanceof Cat) && a.isTame() && !a.isOwnedBy(player) && a.isAlive() && a.hasLineOfSight(player))) {
            pet.getLookControl().setLookAt(player, 30, 30);
            Long last = LAST_GROWL.get(pet.getUUID());
            if (last != null && now - last < 60) continue;
            LAST_GROWL.put(pet.getUUID(), now);
            pet.playSound(pet instanceof Wolf ? SoundEvents.WOLF_GROWL : SoundEvents.CAT_HISS, .8F, 1);
        }
        if (LAST_GROWL.size() > 512) LAST_GROWL.clear();
    }

    private static void sync(ServerPlayer player, boolean force) {
        float now = value(player);
        Float sent = SENT.get(player.getUUID());
        if (!force && sent != null && Math.abs(sent - now) < 1 && !(now == 0 && sent != 0)) return;
        SENT.put(player.getUUID(), now);
        PacketDistributor.sendToPlayer(player, new SuspicionPayload(now));
    }

    static void leave(ServerPlayer player) {
        LOCKED.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }

    static void clear() {
        LOCKED.clear();
        SENT.clear();
        LAST_GROWL.clear();
    }

    // ---- Events ----

    /** Hurting one of your own kind costs 40; hurting anything else while your kind is watching costs 15. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getEntity() == attacker) return;
        ShapeshiftService.Morph m = ShapeshiftService.wearing(attacker);
        if (m == null || event.getEntity() instanceof ShapeDecoy) return;
        if (m.kin.equals(ShapeshiftForms.kin(event.getEntity()))) raise(attacker, KIN_HURT);
        else if (watched(attacker)) raise(attacker, HURT_SEEN);
    }

    /** A blow worth 30% of the form's health breaks it. */
    @SubscribeEvent
    public static void heavy(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) blow(player, event.getNewDamage());
    }

    /** A blow of {@code damage} has landed on {@code player}: true if it was heavy enough to break the form. */
    static boolean blow(ServerPlayer player, float damage) {
        if (!player.isAlive() || !ShapeshiftService.morphed(player) || damage < ShapeshiftSkills.heavyBlow(player) * player.getMaxHealth()) return false;
        ShapeshiftService.breakForm(player, "broken");
        LOCKED.put(player.getUUID(), player.serverLevel().getGameTime() + BROKEN_LOCK);
        return true;
    }
}
