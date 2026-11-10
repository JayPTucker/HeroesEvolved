package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Company's tranquilizer darts. Each dart deepens one dose (slower with each), and a third dose
 * within its ten seconds puts a person under. Nobody dies of a dart; what "under" means depends on
 * where you are. In the past it throws you back to the present; anywhere else you black out where you
 * stand for a few seconds. A sedated Gabriel Gray is how the Company kept him below.
 */
public final class Tranquilizer extends MobEffect {
    /** How long one dose lasts; another dart before it wears off deepens it. */
    public static final int DOSE_TICKS = 200;
    /** Doses that put a player under. */
    public static final int PLAYER_LIMIT = 3;
    private static final Vector3f DART = new Vector3f(0.82F, 0.9F, 1.0F);

    /** How long agents leave someone alone after putting them under. */
    private static final int MERCY_TICKS = 200;
    /** Places that handle a player going under themselves (the past sends them home). Returns true if handled. */
    private static Predicate<ServerPlayer> underHandler = player -> false;
    private static final Map<UUID, Long> SPARED = new HashMap<>();
    /** Targets that turn a dart aside right now (Gray's telekinesis, when nothing has him busy). */
    private static Predicate<LivingEntity> deflector = target -> false;

    public Tranquilizer() {
        super(MobEffectCategory.HARMFUL, 0x9FC6E0);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "effect.tranquilized"), -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static void onGoingUnder(Predicate<ServerPlayer> handler) { underHandler = handler; }
    public static void onDeflect(Predicate<LivingEntity> test) { deflector = test; }

    /** True for a little while after a player goes under: agents don't keep shooting someone on the floor. */
    public static boolean spared(ServerPlayer player) {
        Long until = SPARED.get(player.getUUID());
        if (until == null) return false;
        if (until > player.serverLevel().getGameTime()) return true;
        SPARED.remove(player.getUUID());
        return false;
    }

    /** Doses in {@code target}'s blood right now: 0 for none. */
    public static int doses(LivingEntity target) {
        MobEffectInstance dose = target.getEffect(ModItems.TRANQUILIZED);
        return dose == null ? 0 : dose.getAmplifier() + 1;
    }

    /** A dart flies at {@code target}. Returns the target's doses afterwards (unchanged if it was turned aside). */
    public static int dart(LivingEntity shooter, LivingEntity target) {
        if (deflector.test(target)) {
            if (target.level() instanceof ServerLevel level) {
                Vec3 from = shooter.getEyePosition().subtract(0, 0.2, 0), at = target.getBoundingBox().getCenter();
                trail(level, from, at.add(from.subtract(at).normalize().scale(0.9)));
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.7F, 1.8F);
            }
            return doses(target);
        }
        int doses = Math.min(9, doses(target) + 1);
        target.addEffect(new MobEffectInstance(ModItems.TRANQUILIZED, DOSE_TICKS, doses - 1, false, true, true), shooter);
        if (target.level() instanceof ServerLevel level) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 0.6F, 1.7F);
            trail(level, shooter.getEyePosition().subtract(0, 0.2, 0), target.getBoundingBox().getCenter());
        }
        if (target instanceof ServerPlayer player) {
            ScreenTintPayload.send(player, 0x309FC6E0, 12, ScreenTintPayload.Style.EDGE);
            if (doses >= PLAYER_LIMIT) goUnder(player);
            else player.displayClientMessage(Component.literal(doses == 1
                    ? "A dart in your arm. Your legs feel heavy."
                    : "Another dart. One more and you're going under.").withStyle(ChatFormatting.AQUA), true);
        }
        return doses;
    }

    /** The dart's flight: a thin pale streak from the barrel to where it landed. */
    public static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 2));
        DustParticleOptions dust = new DustParticleOptions(DART, 0.6F);
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Three doses: the world goes dark. Where you are decides what happens next. */
    public static void goUnder(ServerPlayer player) {
        player.removeEffect(ModItems.TRANQUILIZED);
        if (underHandler.test(player)) return;
        SPARED.put(player.getUUID(), player.serverLevel().getGameTime() + MERCY_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 5, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 1, false, false));
        ScreenTintPayload.send(player, 0xE0000000, 100, ScreenTintPayload.Style.HOLD);
        player.sendSystemMessage(Component.literal("The third dart. The floor tilts up to meet you, and the lights go out.")
                .withStyle(ChatFormatting.AQUA));
    }
}
