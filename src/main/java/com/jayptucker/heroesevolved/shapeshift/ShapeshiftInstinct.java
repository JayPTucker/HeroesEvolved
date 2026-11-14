package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Instinct key: whatever the form worn does best (docs/shapeshifting.md, "Instinct for each form"). In your own
 * body it is Lunge, an arm that lengthens into a reaching strike. Each move pays its own stamina and keeps its own
 * cooldown. Moves that play out over time (a held grab, a pounce or charge, a warden's windup, a guardian's gaze, a
 * blaze's three fireballs) are ticked here.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ShapeshiftInstinct {
    /** A creature held in a zombie's grab: re-anchored every tick until it lets go. */
    private record Hold(ServerPlayer owner, LivingEntity target, Vec3 anchor, long until) {}

    /** A pounce or a charge carrying the shapeshifter forward, striking what it meets. */
    private static final class Dash {
        final ServerPlayer player; final InstinctMove move; final Vec3 velocity; final long start, until; final float damage;
        final Set<UUID> struck = new HashSet<>();
        Dash(ServerPlayer player, InstinctMove move, Vec3 velocity, long start, long until, float damage) {
            this.player = player; this.move = move; this.velocity = velocity; this.start = start; this.until = until; this.damage = damage;
        }
    }

    private record Later(long at, ServerPlayer player, Runnable action) {}

    /** The copied person's pets answer to a shapeshifter wearing that person's face until this time. */
    private record Familiar(UUID person, long until) {}

    private static final Map<UUID, EnumMap<InstinctMove, Long>> READY = new HashMap<>();
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final Map<UUID, Familiar> FAMILIARS = new HashMap<>();
    private static final List<Later> LATER = new ArrayList<>();

    private ShapeshiftInstinct() {}

    /** The move the Instinct key makes for this player right now. */
    public static InstinctMove move(ServerPlayer player) {
        if (ShapeshiftSkills.apex(player)) return InstinctMove.SLAM;
        InstinctMove borrowed = ShapeshiftSkills.chimeraMove(player);
        if (borrowed != null) return borrowed;
        return InstinctMove.of(ShapeshiftService.worn(player), ShapeshiftService.example(player));
    }

    /** A press of the Instinct key. False (with a message) if the move is resting or there is no stamina for it. */
    public static boolean use(ServerPlayer player) {
        InstinctMove move = move(player);
        long now = player.serverLevel().getGameTime();
        var ready = READY.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(InstinctMove.class));
        Long at = ready.get(move);
        if (at != null && now < at) { tell(player, "instinct_wait", Math.max(1, (at - now + 19) / 20)); return false; }
        int cost = ShapeshiftSkills.instinctCost(player, move.cost);
        if (!PlayerEnergyService.tryConsume(player, cost)) { tell(player, "no_stamina", cost); return false; }
        // Ambush Shift: the first Instinct straight after a change hits half again as hard.
        bonus = ShapeshiftSkills.ambush(player);
        boolean done;
        try { done = perform(player, move); } finally { bonus = 1; }
        if (done) ready.put(move, now + move.cooldown);
        else PlayerEnergyService.restore(player, cost);
        return done;
    }

    /** Runs a move without paying for it (game tests and previews drive moves this way). */
    static boolean perform(ServerPlayer player, InstinctMove move) {
        return switch (move) {
            case LUNGE -> lunge(player);
            case STRIKE -> strike(player);
            case GRAB -> grab(player);
            case BONE -> bone(player);
            case WEB -> web(player);
            case DETONATE -> detonate(player);
            case BLINK -> blink(player);
            case FIREBALLS -> fireballs(player);
            case POUNCE -> pounce(player);
            case BUCK -> buck(player);
            case FLING -> fling(player);
            case CHARGE -> charge(player);
            case HEX -> hex(player);
            case FANGS -> fangs(player);
            case SONIC -> sonic(player);
            case GAZE -> gaze(player);
            case SKULLS -> skulls(player);
            case FAMILIAR -> familiar(player);
            case SLAM -> slam(player);
        };
    }

    // ---- Helpers ----

    private static int tier(ServerPlayer player) { return PlayerProgressionService.getLevel(player); }

    /** The form's own attack damage (with its modifier), never less than a fist. */
    private static float attack(ServerPlayer player) {
        LivingEntity example = ShapeshiftService.example(player);
        double own = example == null ? player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : ShapeshiftForms.body(example).attack();
        return (float) Math.max(2, own);
    }

    /** Anything alive that isn't a friend (yourself, allies, your pets, and players when PvP is off). */
    static boolean hostile(ServerPlayer player, Entity e) {
        return e instanceof LivingEntity living && e != player && living.isAlive() && !living.isSpectator()
                && !StyleOps.isFriend(player, living);
    }

    private static @Nullable LivingEntity inSight(ServerPlayer player, double reach) {
        return Targeting.livingEntityInSight(player, reach, e -> hostile(player, e));
    }

    /** Where a look of {@code reach} ends: at the first block, or its full length. */
    private static Vec3 reach(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(reach));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Ambush Shift's factor for the move being made now (1 otherwise). */
    private static float bonus = 1;

    private static void hit(ServerPlayer player, LivingEntity target, float damage, Vec3 push) {
        target.hurt(player.damageSources().mobAttack(player), damage * bonus);
        knock(target, push);
    }

    /** Pushes a creature (or a player, whose own client must be told). */
    static void knock(LivingEntity target, Vec3 push) {
        double resist = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 v = push.scale(Math.max(0, 1 - resist));
        if (v.lengthSqr() < 1.0E-6) return;
        target.setDeltaMovement(target.getDeltaMovement().add(v));
        target.hurtMarked = true;
        if (target instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static Vec3 chest(Entity e) { return e.position().add(0, e.getBbHeight() * .6, 0); }

    /** The piece every move but Lunge shows: bound to the shapeshifter, with the move in its scale. */
    private static void show(ServerPlayer player, InstinctMove move, Vec3 from, Vec3 to) {
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_INSTINCT, from, to, move.ordinal(), player);
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static void later(ServerPlayer player, int ticks, Runnable action) {
        LATER.add(new Later(player.serverLevel().getGameTime() + ticks, player, action));
    }

    // ---- The moves ----

    /** Your own arm lengthens into a reaching strike: 4 blocks, 6 + tier. */
    static boolean lunge(ServerPlayer player) {
        double lungeReach = ShapeshiftSkills.lungeReach(player);
        LivingEntity target = inSight(player, lungeReach);
        Vec3 shoulder = ShapeshiftGeometry.shoulder(player, 1);
        Vec3 end = target != null ? target.getBoundingBox().clip(shoulder, chest(target)).orElse(chest(target))
                : reach(player, lungeReach);
        if (target != null) hit(player, target, 6 + tier(player), player.getLookAngle().scale(.45).add(0, .12, 0));
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_LUNGE, shoulder, end, target != null ? 1 : 0, player);
        ArmPosePayload.show(player, ArmPose.SHIFT_LUNGE, 9);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, .8F, 1.5F);
        sound(player, SoundEvents.ARMOR_EQUIP_CHAIN.value(), .9F, 1.8F);
        if (target != null) sound(player, SoundEvents.PLAYER_ATTACK_STRONG, .9F, .8F);
        return true;
    }

    /** A form without a move of its own strikes with its own blow. */
    static boolean strike(ServerPlayer player) {
        LivingEntity target = inSight(player, 3.5);
        Vec3 end = target != null ? chest(target) : reach(player, 3);
        if (target != null) hit(player, target, attack(player) * 1.1F, player.getLookAngle().scale(.4));
        show(player, InstinctMove.STRIKE, chest(player), end);
        sound(player, target != null ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, .9F, .9F);
        return true;
    }

    /** A zombie's grab: the creature is held where it stands for two seconds. */
    static boolean grab(ServerPlayer player) {
        LivingEntity target = inSight(player, 3);
        if (target == null) { tell(player, "instinct_none"); return false; }
        long now = player.serverLevel().getGameTime();
        HOLDS.put(target.getUUID(), new Hold(player, target, target.position(), now + ShapeshiftGeometry.GRAB_TICKS));
        hit(player, target, attack(player) * .5F, Vec3.ZERO);
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_HOLD, chest(player), target.position(), 1, target);
        show(player, InstinctMove.GRAB, chest(player), chest(target));
        sound(player, SoundEvents.ZOMBIE_AMBIENT, 1, .8F);
        sound(player, SoundEvents.ARMOR_EQUIP_CHAIN.value(), .8F, .7F);
        return true;
    }

    /** A skeleton's bone shot: an arrow with no bow and no ammunition. */
    static boolean bone(ServerPlayer player) {
        Arrow arrow = new Arrow(player.serverLevel(), player, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(2 + tier(player) * .25);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.8F, 1);
        player.serverLevel().addFreshEntity(arrow);
        show(player, InstinctMove.BONE, player.getEyePosition().add(0, -.2, 0), player.getEyePosition().add(player.getLookAngle().scale(2)));
        sound(player, SoundEvents.SKELETON_SHOOT, 1, 1.1F);
        return true;
    }

    /** A spider's spit: a cobweb where it lands, gone again after five seconds. */
    static boolean web(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = inSight(player, 12);
        BlockPos at;
        if (target != null) at = target.blockPosition();
        else {
            Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(12));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            at = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(end) : hit.getBlockPos().relative(hit.getDirection());
        }
        boolean placed = TemporaryBlocks.place(level, at, Blocks.COBWEB.defaultBlockState(), 100, player);
        if (!placed && target == null) { tell(player, "instinct_none"); return false; }
        show(player, InstinctMove.WEB, player.getEyePosition().add(0, -.25, 0), Vec3.atCenterOf(at));
        sound(player, SoundEvents.SPIDER_AMBIENT, .8F, 1.4F);
        level.playSound(null, at, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1, .8F);
        return true;
    }

    /** A creeper's blast, which throws you back into your own body. It breaks no blocks. */
    static boolean detonate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 centre = player.position().add(0, player.getBbHeight() / 2, 0);
        float power = 10 + 2 * tier(player);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(4.5), e -> hostile(player, e))) {
            double d = victim.position().add(0, victim.getBbHeight() / 2, 0).distanceTo(centre);
            if (d > 4.5) continue;
            float falloff = (float) (1 - d / 5.5);
            victim.hurt(player.damageSources().explosion(player, player), power * falloff);
            knock(victim, victim.position().subtract(centre).normalize().scale(1.4 * falloff).add(0, .35 * falloff, 0));
        }
        SetPiecePayload.send(level, Piece.SHIFT_INSTINCT, centre, centre, InstinctMove.DETONATE.ordinal(), null);
        // A puff of smoke as seasoning; the plates are the blast.
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 2, .6, .4, .6, 0);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3, .9F);
        ShapeshiftService.detonated(player);
        return true;
    }

    /** An enderman's blink: up to 12 blocks along the look, to the farthest point the body fits. */
    static boolean blink(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        double far = eye.distanceTo(reach(player, 12));
        Vec3 from = player.position();
        for (double d = far - .4; d >= 1.5; d -= .25) {
            Vec3 feet = eye.add(look.scale(d)).subtract(0, player.getBbHeight() * .5, 0);
            AABB box = player.getBoundingBox().move(feet.subtract(from)).deflate(1.0E-3);
            if (!player.level().noCollision(player, box)) continue;
            player.teleportTo(feet.x, feet.y, feet.z);
            player.resetFallDistance();
            show(player, InstinctMove.BLINK, from, feet);
            player.serverLevel().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1);
            sound(player, SoundEvents.ENDERMAN_TELEPORT, 1, 1.2F);
            return true;
        }
        tell(player, "no_room");
        return false;
    }

    /** A blaze's volley: three fireballs a fifth of a second apart (a ghast's form throws the same). */
    static boolean fireballs(ServerPlayer player) {
        for (int i = 0; i < 3; i++) later(player, i * 4, () -> {
            Vec3 look = player.getLookAngle();
            SmallFireball ball = new SmallFireball(player.serverLevel(), player, look.scale(1.2));
            Vec3 at = player.getEyePosition().add(look.scale(.8)).add(0, -.15, 0);
            ball.setPos(at.x, at.y, at.z);
            player.serverLevel().addFreshEntity(ball);
            show(player, InstinctMove.FIREBALLS, at, at.add(look.scale(2)));
            sound(player, SoundEvents.BLAZE_SHOOT, 1, 1);
        });
        return true;
    }

    /** A wolf's pounce: a leap at the creature in sight (or straight ahead), biting the first thing it lands on. */
    static boolean pounce(ServerPlayer player) {
        LivingEntity target = inSight(player, 10);
        Vec3 dir = flat(target != null ? target.position().subtract(player.position()) : player.getLookAngle());
        double speed = target != null ? Math.min(1.3, .45 + player.distanceTo(target) * .11) : 1.15;
        Vec3 velocity = dir.scale(speed).add(0, .46, 0);
        long now = player.serverLevel().getGameTime();
        dash(player, new Dash(player, InstinctMove.POUNCE, velocity, now, now + 18, attack(player) * 1.5F), true);
        show(player, InstinctMove.POUNCE, player.position(), player.position().add(dir.scale(speed * 5)));
        sound(player, SoundEvents.WOLF_GROWL, 1, 1.1F);
        return true;
    }

    /** A horse's buck: a kick that throws back everything just in front. */
    static boolean buck(ServerPlayer player) {
        Vec3 dir = flat(player.getLookAngle());
        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.5),
                e -> hostile(player, e))) {
            Vec3 to = victim.position().subtract(player.position());
            if (to.horizontalDistance() > 3.5 || flat(to).dot(dir) < .3) continue;
            hit(player, victim, attack(player) + 2, flat(to).scale(1.5).add(0, .45, 0));
        }
        show(player, InstinctMove.BUCK, player.position(), player.position().add(dir.scale(3)));
        sound(player, SoundEvents.HORSE_ANGRY, 1, 1);
        return true;
    }

    /** An iron golem's fling: the creature in reach is struck and thrown into the air. */
    static boolean fling(ServerPlayer player) {
        LivingEntity target = inSight(player, 3.5);
        if (target == null) { tell(player, "instinct_none"); return false; }
        target.hurt(player.damageSources().mobAttack(player), attack(player));
        knock(target, flat(target.position().subtract(player.position())).scale(.25).add(0, 1.1, 0));
        show(player, InstinctMove.FLING, target.position(), target.position().add(0, 3, 0));
        sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1, 1);
        return true;
    }

    /** A ravager's roar and charge: everything close is thrown back and slowed, then you charge straight ahead. */
    static boolean charge(ServerPlayer player) {
        for (LivingEntity victim : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5),
                e -> hostile(player, e))) {
            Vec3 away = flat(victim.position().subtract(player.position()));
            knock(victim, away.scale(1.2).add(0, .3, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
        }
        Vec3 dir = flat(player.getLookAngle());
        long now = player.serverLevel().getGameTime();
        dash(player, new Dash(player, InstinctMove.CHARGE, dir.scale(1.1), now, now + 12, attack(player)), false);
        show(player, InstinctMove.CHARGE, player.position(), player.position().add(dir.scale(10)));
        sound(player, SoundEvents.RAVAGER_ROAR, 1.5F, 1);
        return true;
    }

    /** A witch's hex: a splash of slowness. */
    static boolean hex(ServerPlayer player) {
        ThrownPotion potion = new ThrownPotion(player.serverLevel(), player);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SLOWNESS));
        potion.shootFromRotation(player, player.getXRot() - 20, player.getYRot(), 0, .75F, 1);
        player.serverLevel().addFreshEntity(potion);
        show(player, InstinctMove.HEX, chest(player), chest(player).add(player.getLookAngle().scale(2)));
        sound(player, SoundEvents.WITCH_THROW, 1, 1);
        return true;
    }

    /** An evoker's fangs: twelve snapping in a line along the look, one after another. */
    static boolean fangs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = flat(player.getLookAngle());
        float yaw = (float) Math.atan2(dir.z, dir.x);
        int made = 0;
        for (int i = 0; i < 12; i++) {
            Vec3 at = player.position().add(dir.scale(1.25 * (i + 1)));
            Double ground = ground(level, at);
            if (ground == null) continue;
            level.addFreshEntity(new EvokerFangs(level, at.x, ground, at.z, yaw, i, player));
            made++;
        }
        if (made == 0) { tell(player, "instinct_none"); return false; }
        show(player, InstinctMove.FANGS, chest(player), player.position().add(dir.scale(15)));
        sound(player, SoundEvents.EVOKER_CAST_SPELL, 1, 1);
        return true;
    }

    /** The top of the ground near {@code at}, within a couple of blocks up or down, as an evoker's fangs find it. */
    private static @Nullable Double ground(ServerLevel level, Vec3 at) {
        BlockPos start = BlockPos.containing(at.x, at.y + 2, at.z);
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos pos = start.below(dy), below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return (double) pos.getY();
        }
        return null;
    }

    /** A warden's sonic boom: half a second's charge, then a blast along the look that passes through walls. */
    static boolean sonic(ServerPlayer player) {
        Vec3 from = player.getEyePosition().add(0, -.3, 0), dir = player.getLookAngle();
        Vec3 to = from.add(dir.scale(ShapeshiftGeometry.SONIC_RANGE));
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_SONIC, from, to, 1, player);
        sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 2, 1);
        later(player, ShapeshiftGeometry.SONIC_WINDUP, () -> {
            ServerLevel level = player.serverLevel();
            Vec3 start = player.getEyePosition().add(0, -.3, 0), end = start.add(dir.scale(ShapeshiftGeometry.SONIC_RANGE));
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.5), e -> hostile(player, e))) {
                if (distanceToSegment(victim.getBoundingBox().getCenter(), start, end) > 1.4 + victim.getBbWidth() / 2) continue;
                victim.hurt(player.damageSources().sonicBoom(player), 10 + tier(player));
                knock(victim, dir.scale(1.4).add(0, .3, 0));
            }
            for (int i = 1; i <= ShapeshiftGeometry.SONIC_RANGE; i++) {
                Vec3 p = start.add(dir.scale(i));
                level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3, 1);
        });
        return true;
    }

    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** A guardian's gaze: a beam that holds on its target for a second, then strikes (an elder guardian's also weakens). */
    static boolean gaze(ServerPlayer player) {
        LivingEntity target = inSight(player, ShapeshiftGeometry.GAZE_RANGE);
        if (target == null) { tell(player, "instinct_none"); return false; }
        SetPiecePayload.send(player.serverLevel(), Piece.SHIFT_GAZE, player.getEyePosition(), chest(target), 1, target);
        sound(player, SoundEvents.GUARDIAN_ATTACK, 1, 1);
        boolean elder = ShapeshiftService.example(player) instanceof ElderGuardian;
        later(player, ShapeshiftGeometry.GAZE_WINDUP, () -> {
            if (!target.isAlive() || target.distanceTo(player) > ShapeshiftGeometry.GAZE_RANGE + 4 || !player.hasLineOfSight(target)) return;
            target.hurt(player.damageSources().indirectMagic(player, player), 6 + tier(player));
            if (elder) target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 2));
        });
        return true;
    }

    /** A wither's skull. */
    static boolean skulls(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        WitherSkull skull = new WitherSkull(player.serverLevel(), player, look);
        Vec3 at = player.getEyePosition().add(look.scale(1.2));
        skull.setPos(at.x, at.y, at.z);
        player.serverLevel().addFreshEntity(skull);
        show(player, InstinctMove.SKULLS, at, at.add(look.scale(2)));
        sound(player, SoundEvents.WITHER_SHOOT, 1, 1);
        return true;
    }

    /** In a person's face, their tamed animals answer to you for ten seconds: they go after whatever you strike. */
    static boolean familiar(ServerPlayer player) {
        ShapeshiftForm form = ShapeshiftService.worn(player);
        if (form == null || !form.person()) return false;
        UUID person = form.personId();
        List<TamableAnimal> pets = pets(player, person);
        if (pets.isEmpty()) { tell(player, "instinct_no_pets", form.name()); return false; }
        FAMILIARS.put(player.getUUID(), new Familiar(person, player.serverLevel().getGameTime() + ShapeshiftSkills.familiarTicks(player)));
        for (TamableAnimal pet : pets) show(player, InstinctMove.FAMILIAR, chest(player), chest(pet));
        ArmPosePayload.show(player, ArmPose.SHIFT_TOUCH, 10);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1, .8F);
        return true;
    }

    private static List<TamableAnimal> pets(ServerPlayer player, UUID person) {
        return player.serverLevel().getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(16),
                pet -> pet.isTame() && person.equals(pet.getOwnerUUID()) && pet.isAlive());
    }

    // ---- What plays out over time ----

    private static void dash(ServerPlayer player, Dash dash, boolean once) {
        DASHES.put(player.getUUID(), dash);
        player.setDeltaMovement(dash.velocity);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!LATER.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
                Later later = it.next();
                if (later.player.isRemoved() || !later.player.isAlive()) { it.remove(); continue; }
                if (later.player.serverLevel().getGameTime() >= later.at) { due.add(later); it.remove(); }
            }
            for (Later later : due) later.action.run();
        }
        for (Iterator<Hold> it = HOLDS.values().iterator(); it.hasNext(); ) {
            Hold hold = it.next();
            LivingEntity t = hold.target;
            if (!t.isAlive() || hold.owner.isRemoved() || !hold.owner.isAlive() || t.level() != hold.owner.level()
                    || t.level().getGameTime() >= hold.until || t.distanceTo(hold.owner) > 6) { it.remove(); continue; }
            Vec3 a = hold.anchor;
            if (t.position().distanceToSqr(a) > .0025) {
                if (t instanceof ServerPlayer sp) sp.connection.teleport(a.x, a.y, a.z, sp.getYRot(), sp.getXRot());
                else t.teleportTo(a.x, a.y, a.z);
            }
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            if (t instanceof Mob mob) mob.getNavigation().stop();
        }
        for (Iterator<Dash> it = DASHES.values().iterator(); it.hasNext(); ) {
            Dash dash = it.next();
            ServerPlayer p = dash.player;
            long t = p.serverLevel().getGameTime();
            if (p.isRemoved() || !p.isAlive() || t >= dash.until) { it.remove(); continue; }
            if (dash.move == InstinctMove.CHARGE) {
                if (p.horizontalCollision && t > dash.start + 1) { it.remove(); continue; }
                // Re-sent each tick, or ground friction eats the charge in two.
                p.setDeltaMovement(dash.velocity.x, p.getDeltaMovement().y, dash.velocity.z);
                p.connection.send(new ClientboundSetEntityMotionPacket(p));
            } else if (t > dash.start + 3 && p.onGround()) { it.remove(); continue; }
            boolean landed = false;
            for (LivingEntity victim : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(.7),
                    e -> hostile(p, e) && !dash.struck.contains(e.getUUID()))) {
                dash.struck.add(victim.getUUID());
                hit(p, victim, dash.damage, flat(dash.velocity).scale(dash.move == InstinctMove.CHARGE ? 1.5 : .6).add(0, .35, 0));
                p.serverLevel().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        dash.move == InstinctMove.CHARGE ? SoundEvents.RAVAGER_ATTACK : SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1, 1.2F);
                landed = true;
            }
            if (landed && dash.move == InstinctMove.POUNCE) it.remove();
        }
        FAMILIARS.values().removeIf(f -> f.until < now - 400);
    }

    /** Forgets everything a player had playing (a death, a logout, the power taken away). */
    /** Lets go of anything holding {@code held} in a zombie's grab (Shed). */
    static void release(LivingEntity held) { HOLDS.remove(held.getUUID()); }

    /** Apex Form's slam: everything within five blocks is hurt and thrown up and out. */
    static boolean slam(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 feet = player.position();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(5), e -> hostile(player, e)
                && !(e instanceof ShapeDecoy) && e.distanceToSqr(player) <= 36)) {
            Vec3 away = victim.position().subtract(feet).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
            hit(player, victim, 10 + 2 * tier(player), away.scale(.9).add(0, .7, 0));
        }
        show(player, InstinctMove.SLAM, feet, feet.add(player.getLookAngle()));
        ArmPosePayload.show(player, ArmPose.SHIFT_SLAM, 12);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.1F, .6F);
        sound(player, SoundEvents.RAVAGER_STEP, 1.2F, .5F);
        com.jayptucker.heroesevolved.network.CameraShakePayload.sendAround(level, feet, 32, .8F, 14);
        return true;
    }

    public static void forget(ServerPlayer player) {
        DASHES.remove(player.getUUID());
        FAMILIARS.remove(player.getUUID());
        HOLDS.values().removeIf(h -> h.owner == player);
        LATER.removeIf(l -> l.player == player);
    }

    /** Whether {@code target} is held in someone's grab now (for tests and other powers). */
    public static boolean held(LivingEntity target) { return HOLDS.containsKey(target.getUUID()); }

    // ---- Events ----

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    /** While Familiar lasts, the copied person's pets go after whatever you strike. */
    @SubscribeEvent public static void struck(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        Familiar familiar = FAMILIARS.get(attacker.getUUID());
        LivingEntity victim = event.getEntity();
        if (familiar == null || attacker.serverLevel().getGameTime() > familiar.until) return;
        if (victim instanceof TamableAnimal pet && familiar.person.equals(pet.getOwnerUUID())) return;
        if (victim.getUUID().equals(familiar.person)) return;
        for (TamableAnimal pet : pets(attacker, familiar.person)) if (!pet.isOrderedToSit()) pet.setTarget(victim);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { forget(player); READY.remove(player.getUUID()); }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        READY.clear(); HOLDS.clear(); DASHES.clear(); FAMILIARS.clear(); LATER.clear();
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("shapeshift.heroesevolved." + key, args), true);
    }
}
