package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/**
 * Waterbending: fighting with the water you carry, built on Pyrokinesis's firebending.
 *
 * <p>An empty-handed left click throws the next strike of a combo: a lash of water flicked from the lead hand, a
 * backhand lash from the rear, then (from level 2) Ice Daggers, the carried water frozen into five blades thrown in a
 * fan. Crouching turns the click into a Water Wheel, a ring of water whirled round the body. Double-tapping left or
 * right is a Flow Step, a skate sideways on a sheet of water; back (level 2) is a Geyser Vault, a column bursting up
 * where you stood that throws you up and back and launches whoever was in front. A blow evaded during a dodge turns the
 * tide: the next lash snares the attacker and hauls them in.</p>
 *
 * <p>Every strike takes a little water; with none carried, it is pulled thin from the air and hits softer.</p>
 */
public final class HydroCombat {
    public enum Strike { LASH, BACKHAND, DAGGERS, WHEEL }

    /** Level from which the combo ends in Ice Daggers, crouching whirls a Water Wheel, and a back dodge vaults. */
    public static final int DAGGER_LEVEL = 2;
    static final int COMBO_WINDOW = 18, LASH_GAP = 5, DAGGER_RECOVERY = 12, WHEEL_RECOVERY = 14;
    static final int EVADE_TICKS = 8, CUSHION_TICKS = 40, LOADED_TICKS = 40, SLICK_TICKS = 60;

    private static final class Fighter {
        int step;
        long lastStrike = -1000, readyAt = -1000, dodgeReady = -1000, loadedUntil = -1000, slickUntil = -1000;
        UUID attacker;
        Vec3 slickFrom, slickTo;
        ServerPlayer player;
    }

    private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();

    public static final FightingStyles.Style STYLE = new FightingStyles.Style() {
        @Override public net.minecraft.resources.ResourceLocation power() {
            return com.jayptucker.heroesevolved.ability.registry.ModAbilities.HYDROKINESIS_ID;
        }
        @Override public int strike(ServerPlayer player) { return HydroCombat.strike(player); }
        @Override public boolean canStrike(ServerPlayer player, LivingEntity target) { return HydroService.hostile(player, target); }
        @Override public boolean dodge(ServerPlayer player, Dodge dodge) { return HydroCombat.dodge(player, dodge); }
        @Override public void counter(ServerPlayer player, DamageSource source) { turnTheTide(player, source); }
        @Override public void tick(MinecraftServer server) { HydroCombat.tick(server); }
        @Override public void forget(ServerPlayer player) { FIGHTERS.remove(player.getUUID()); }
        @Override public void clear() { FIGHTERS.clear(); }
    };

    private HydroCombat() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static Fighter fighter(ServerPlayer player) {
        Fighter fighter = FIGHTERS.computeIfAbsent(player.getUUID(), id -> new Fighter());
        fighter.player = player;
        return fighter;
    }

    static boolean canFight(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && FightingStyles.fights(player, STYLE) && HydroService.canBend(player)
                && !HydroService.riding(player) && !HydroService.storming(player) && !HydroSkills.jetting(player);
    }

    public static boolean isLoaded(ServerPlayer player) {
        Fighter fighter = FIGHTERS.get(player.getUUID());
        return fighter != null && now(player) < fighter.loadedUntil;
    }

    public static boolean isDodging(ServerPlayer player) { return StyleMotion.dashing(player); }

    public static Strike next(ServerPlayer player) {
        Fighter fighter = fighter(player);
        int level = PlayerProgressionService.getLevel(player);
        if (player.isCrouching() && level >= DAGGER_LEVEL) return Strike.WHEEL;
        int step = now(player) - fighter.lastStrike > COMBO_WINDOW ? 0 : fighter.step;
        return step == 2 && level >= DAGGER_LEVEL ? Strike.DAGGERS : step == 1 ? Strike.BACKHAND : Strike.LASH;
    }

    // ------------------------------------------------------------------
    // Strikes
    // ------------------------------------------------------------------

    /** Throws the next strike. Returns 1 if thrown, 0 if it's too soon, and -1 if no water can be bent at all. */
    public static int strike(ServerPlayer player) {
        if (!player.getMainHandItem().isEmpty() || !canFight(player)) return -1;
        long now = now(player);
        Fighter fighter = fighter(player);
        if (now < fighter.readyAt) return 0;
        Strike move = next(player);
        boolean loaded = now < fighter.loadedUntil && move != Strike.WHEEL;
        int cost = loaded ? 0 : move == Strike.LASH || move == Strike.BACKHAND
                ? com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy("hydrokinesis", "strike", COMMON.hydroStrikeEnergyCost.get()) : com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy("hydrokinesis", "finisher", COMMON.hydroFlourishEnergyCost.get());
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("hydro.heroesevolved.too_tired"), true);
                return -1;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        // A loaded lash is always the backhand, even when the combo was due the daggers.
        if (loaded && move == Strike.DAGGERS) move = Strike.BACKHAND;
        switch (move) {
            case LASH -> lash(player, fighter, level, false, loaded);
            case BACKHAND -> lash(player, fighter, level, true, loaded);
            case DAGGERS -> daggers(player, level);
            case WHEEL -> wheel(player, level);
        }
        if (loaded) { fighter.loadedUntil = -1000; fighter.attacker = null; }
        fighter.lastStrike = now;
        fighter.step = move == Strike.DAGGERS || move == Strike.WHEEL ? 0 : (fighter.step + 1) % (level >= DAGGER_LEVEL ? 3 : 2);
        fighter.readyAt = now + switch (move) {
            case DAGGERS -> DAGGER_RECOVERY;
            case WHEEL -> WHEEL_RECOVERY;
            default -> LASH_GAP;
        };
        return 1;
    }

    /**
     * A lash of water flicked from one hand to the first foe near the aim. Loaded (Turn the Tide), it snares the foe
     * that was evaded, wherever they are within sixteen blocks, and hauls them in. Slipstream lengthens the first lash
     * thrown from a Flow Step's slick; Brine Edge shatters a frozen foe for double.
     */
    private static void lash(ServerPlayer player, Fighter fighter, int level, boolean rear, boolean loaded) {
        ServerLevel world = player.serverLevel();
        HydroService.Draw draw = HydroService.draw(player, loaded ? 0 : 1);
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        boolean slick = SkillTreeService.has(player, "hydro_1_0") && now(player) < fighter.slickUntil;
        double reach = HydroGeometry.LASH_REACH * (slick ? 1.5 : 1);
        LivingEntity target = loaded ? attacker(player, fighter) : null;
        if (target != null) reach = Math.max(reach, eye.distanceTo(target.getBoundingBox().getCenter()) + .5);
        var hit = world.clip(new ClipContext(eye, eye.add(aim.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (target == null) target = HydroService.firstInTube(player, eye, aim, eye.distanceTo(hit.getLocation()), .5);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : hit.getLocation();
        if (target != null) {
            float damage = (2 + .4F * level) * draw.power() * (loaded ? 1.6F : 1);
            if (SkillTreeService.has(player, "hydro_brine_edge") && CryoService.isFrozen(target)) damage *= 2;
            HydroService.hit(player, target, damage);
            if (loaded) {
                Vec3 toward = player.position().subtract(target.position());
                HydroService.push(target, toward.normalize().scale(Math.min(1.6, .3 + toward.length() * .2)).add(0, .35, 0));
                HydroGeometry.show(world, Piece.HYDRO_SNARE, HydroGeometry.hand(player, 1, rear), target.position(),
                        target.getBbHeight(), target);
            } else {
                HydroService.push(target, aim.scale(.25).add(0, .06, 0).add(target.getDeltaMovement().scale(.4)));
            }
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            HydroService.douse(player, hit.getLocation(), 0);
        }
        if (slick) fighter.slickUntil = -1000;
        HydroGeometry.show(world, Piece.HYDRO_LASH, HydroGeometry.hand(player, 1, rear), end,
                (target != null ? 1 : .6) + (loaded ? 4 : 0), draw.thin(), player);
        ArmPosePayload.show(player, rear ? ArmPose.WATER_BACKHAND : ArmPose.WATER_LASH, 9);
        player.swing(InteractionHand.MAIN_HAND, false);
        HydroService.sound(player, SoundEvents.PLAYER_SPLASH, .45F, rear ? 1.55F : 1.75F);
        HydroService.sound(player, SoundEvents.TRIDENT_THROW.value(), .4F, rear ? 1.6F : 1.85F);
    }

    private static LivingEntity attacker(ServerPlayer player, Fighter fighter) {
        if (fighter.attacker == null) return null;
        var entity = player.serverLevel().getEntity(fighter.attacker);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.distanceToSqr(player) > 16 * 16
                || !HydroService.exposed(player, player.getEyePosition(), living.getBoundingBox().getCenter())) return null;
        return living;
    }

    /** The carried water frozen into five daggers and thrown in a fan; Brine Edge freezes a soaked foe solid. */
    private static void daggers(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        HydroService.Draw draw = HydroService.draw(player, 5);
        Vec3 eye = player.getEyePosition(), aim = player.getLookAngle().normalize();
        Vec3 hands = HydroGeometry.hand(player, 1, true).lerp(HydroGeometry.hand(player, 1, false), .5);
        boolean brine = SkillTreeService.has(player, "hydro_brine_edge");
        float damage = (1.5F + .3F * level) * draw.power();
        int count = draw.thin() ? 3 : HydroGeometry.DAGGERS;
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians((i - (count - 1) / 2.0) * HydroGeometry.DAGGER_SPREAD);
            Vec3 dir = new Vec3(aim.x * Math.cos(angle) - aim.z * Math.sin(angle), aim.y,
                    aim.x * Math.sin(angle) + aim.z * Math.cos(angle)).normalize();
            var hit = world.clip(new ClipContext(eye, eye.add(dir.scale(HydroGeometry.DAGGER_REACH)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            LivingEntity target = HydroService.firstInTube(player, eye, dir, eye.distanceTo(hit.getLocation()), .35);
            Vec3 end = target != null ? target.getBoundingBox().getCenter() : hit.getLocation();
            if (target != null) {
                boolean soaked = HydroService.soaked(target);
                HydroService.hit(player, target, damage);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                if (brine && soaked) CryoService.freezeSolid(target, 20, player);
            }
            HydroGeometry.show(world, Piece.HYDRO_DAGGER, hands, end, target != null ? 1 : .6, draw.thin(), player);
        }
        ArmPosePayload.show(player, ArmPose.WATER_DAGGERS, 12);
        HydroService.sound(player, SoundEvents.GLASS_BREAK, .5F, 1.8F);
        HydroService.sound(player, SoundEvents.TRIDENT_THROW.value(), .7F, 1.3F);
        HydroService.sound(player, SoundEvents.PLAYER_HURT_FREEZE, .4F, 1.6F);
    }

    /** A ring of water whirled round the waist in one full turn: everyone in reach is knocked away and drenched. */
    private static void wheel(ServerPlayer player, int level) {
        ServerLevel world = player.serverLevel();
        HydroService.Draw draw = HydroService.draw(player, 5);
        Vec3 feet = player.position();
        double radius = HydroGeometry.WHEEL_RADIUS;
        float damage = (2.5F + .5F * level) * draw.power();
        int struck = 0;
        for (LivingEntity target : HydroService.enemies(player, feet.add(0, .8, 0), radius + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.6
                    || target.position().subtract(feet).horizontalDistance() > radius + target.getBbWidth() * .5
                    || !HydroService.exposed(player, feet.add(0, .9, 0), target.getBoundingBox().getCenter())) continue;
            HydroService.hit(player, target, damage);
            Vec3 out = target.position().subtract(feet).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-4 ? HydroGeometry.level(player.getLookAngle(), player.getYRot()) : out.normalize();
            HydroService.push(target, out.scale(.65).add(0, .32, 0));
            if (++struck >= 12) break;
        }
        HydroService.douse(player, feet.add(0, .5, 0), 2);
        HydroGeometry.show(world, Piece.HYDRO_WHEEL, feet, feet, radius, draw.thin(), player);
        ArmPosePayload.show(player, ArmPose.WATER_WHEEL, 14);
        HydroService.sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, .8F, .9F);
        HydroService.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, .8F, .7F);
    }

    // ------------------------------------------------------------------
    // Dodges
    // ------------------------------------------------------------------

    /** A double-tapped direction: a Flow Step to the side, or (from level 2) a Geyser Vault back. */
    public static boolean dodge(ServerPlayer player, Dodge dodge) {
        if (!canFight(player) || player.isPassenger() || player.isFallFlying() || StyleMotion.dashing(player)) return false;
        Fighter fighter = fighter(player);
        long now = now(player);
        if (now < fighter.dodgeReady) return false;
        int level = Math.max(1, PlayerProgressionService.getLevel(player));
        boolean vault = dodge == Dodge.BACK && level >= DAGGER_LEVEL;
        int cost = com.jayptucker.heroesevolved.ability.PowerBalance.styleEnergy("hydrokinesis", vault ? "back_dodge" : "dodge", COMMON.hydroDodgeEnergyCost.get() + (vault ? 3 : 0));
        if (cost > 0) {
            if (!PlayerEnergyService.tryConsume(player, cost)) {
                player.displayClientMessage(Component.translatable("hydro.heroesevolved.too_tired"), true);
                return false;
            }
            MasteryService.awardEnergySpent(player, cost);
        }
        Vec3 forward = HydroGeometry.level(player.getLookAngle(), player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 direction = switch (dodge) {
            case LEFT -> right.scale(-1);
            case RIGHT -> right;
            case BACK -> forward.scale(-1);
        };
        Vec3 from = player.position();
        ServerLevel world = player.serverLevel();
        if (vault) {
            StyleMotion.dash(player, direction.scale(.58), .9, 1, HydroGeometry.VAULT_FLIP, true, false);
            geyser(player, level, from, forward);
        } else {
            // A skate, not a hop: low and fast, carried on a sheet of water.
            StyleMotion.dash(player, direction.scale(1.05), player.onGround() ? .1 : .04, HydroGeometry.STEP_TICKS,
                    HydroGeometry.STEP_TICKS + 2, false, false);
            Vec3 to = from.add(direction.scale(4.6));
            if (SkillTreeService.has(player, "hydro_1_0")) {
                fighter.slickUntil = now + SLICK_TICKS;
                fighter.slickFrom = from;
                fighter.slickTo = to;
            }
            HydroGeometry.show(world, Piece.HYDRO_FLOW, from, to, 1, player);
        }
        StyleMotion.evade(player, EVADE_TICKS);
        StyleMotion.cushion(player, CUSHION_TICKS);
        fighter.dodgeReady = now + com.jayptucker.heroesevolved.ability.PowerBalance.styleCooldown("hydrokinesis", "dodge", COMMON.hydroDodgeCooldownTicks.get());
        ArmPosePayload.show(player, vault ? ArmPose.WATER_VAULT : ArmPose.WATER_FLOW, vault ? HydroGeometry.VAULT_FLIP + 2 : 10);
        HydroService.sound(player, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, .7F, vault ? .8F : 1.3F);
        return true;
    }

    /** A column of water bursts up where the vault began: whoever is on it or just in front goes up with it. */
    private static void geyser(ServerPlayer player, int level, Vec3 from, Vec3 forward) {
        Vec3 center = from.add(forward.scale(.8));
        float damage = 3 + .6F * level;
        for (LivingEntity target : HydroService.enemies(player, center.add(0, .8, 0), HydroGeometry.VAULT_RADIUS + 1)) {
            if (target.position().subtract(center).horizontalDistance() > HydroGeometry.VAULT_RADIUS + target.getBbWidth() * .5
                    || Math.abs(target.getY() - from.y) > 2) continue;
            HydroService.hit(player, target, damage);
            HydroService.push(target, forward.scale(.25).add(0, .9, 0));
        }
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_VAULT, from, from.add(forward), HydroGeometry.VAULT_RADIUS, player);
        HydroService.sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1, 1.2F);
        CameraShakePayload.sendAround(player.serverLevel(), from, 8, .12F, 4);
    }

    /** A blow passed through the water: the next lash snares the one who threw it. */
    private static void turnTheTide(ServerPlayer player, DamageSource source) {
        Fighter fighter = fighter(player);
        long now = now(player);
        fighter.loadedUntil = now + LOADED_TICKS;
        fighter.attacker = source.getEntity() instanceof LivingEntity attacker && attacker != player ? attacker.getUUID() : null;
        fighter.step = 1;
        fighter.lastStrike = now;
        fighter.readyAt = Math.min(fighter.readyAt, now);
        HydroService.fill(player, 6);
        HydroGeometry.show(player.serverLevel(), Piece.HYDRO_UNDERTOW, player.position(), player.position(), 1, player);
        HydroService.sound(player, SoundEvents.PLAYER_SPLASH, .8F, 1.6F);
        HydroService.sound(player, SoundEvents.CONDUIT_AMBIENT_SHORT, .6F, 1.8F);
        player.displayClientMessage(Component.translatable("hydro.heroesevolved.turn_the_tide").withStyle(ChatFormatting.AQUA), true);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 5 != 0) return;
        for (Fighter fighter : List.copyOf(FIGHTERS.values())) {
            ServerPlayer player = fighter.player;
            if (player == null || !player.isAlive()) continue;
            if (now < fighter.loadedUntil) {
                Vec3 hand = HydroGeometry.hand(player, 1, true);
                HydroGeometry.show(player.serverLevel(), Piece.HYDRO_LOADED, hand, hand, 1, player);
            }
            if (now < fighter.slickUntil && fighter.slickFrom != null && now % 10 == 0) {
                Vec3 a = fighter.slickFrom, b = fighter.slickTo, ab = b.subtract(a);
                for (LivingEntity target : HydroService.enemies(player, a.lerp(b, .5), ab.length() * .5 + 1.5)) {
                    double t = Math.clamp(target.position().subtract(a).dot(ab) / Math.max(1e-4, ab.lengthSqr()), 0, 1);
                    if (target.position().distanceTo(a.add(ab.scale(t))) > 1.2) continue;
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                    HydroService.soak(target, 40);
                }
            }
        }
    }
}
