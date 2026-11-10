package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Company agent: a man in a dark suit who stands a post, challenges strangers, and fires
 * tranquilizer darts. Darts are aimed, not instant: a guard stops, raises the rifle, and a thin red
 * line shows where it is pointed for most of a second before it fires. Break his line of sight, or be
 * moving fast, and it misses.
 *
 * <p>The {@link Role#SUPPRESSOR} carries no rifle. He keeps a {@link SuppressorField}: near him nobody's
 * power answers, a player's or Gabriel Gray's. Up close he still hits like a man who has done this before.</p>
 *
 * <p>An agent in the past is {@linkplain #nonLethal() non-lethal}: you can't kill anyone there, so a blow
 * that would kill puts him on the floor, out cold, for the rest of the night instead. Agents fire on
 * any Sylar tagged {@link #LOOSE_TAG}: Gray out of his cell.</p>
 */
public class CompanyAgentEntity extends PathfinderMob {
    public enum Role { GUARD, SUPPRESSOR }

    public static final String LOOSE_TAG = "heroesevolved_loose";
    public static final int AIM_TICKS = 16, SHOT_COOLDOWN = 40;
    public static final double DART_RANGE = 18, CLOSE = 2.5;
    private static final int SHOUT_EVERY = 200;
    private static final Vector3f SIGHT = new Vector3f(0.95F, 0.12F, 0.1F);
    private static final Vector3f QUIET = new Vector3f(0.52F, 0.5F, 0.6F);

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(CompanyAgentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(CompanyAgentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OUT_COLD = SynchedEntityData.defineId(CompanyAgentEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos post;
    private boolean nonLethal;
    private final Map<UUID, Integer> shouted = new HashMap<>();

    public CompanyAgentEntity(EntityType<? extends CompanyAgentEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 16).add(Attributes.ARMOR, 2);
    }

    /**
     * An agent at his post, facing {@code yaw}, not yet added to the world (tag him first if a scene needs
     * to). A non-lethal agent goes down instead of dying.
     */
    public static CompanyAgentEntity create(EntityType<? extends CompanyAgentEntity> type, ServerLevel level, Role role,
                                            Vec3 at, float yaw, boolean nonLethal) {
        CompanyAgentEntity agent = type.create(level);
        if (agent == null) return null;
        agent.moveTo(at.x, at.y, at.z, yaw, 0);
        agent.setYHeadRot(yaw);
        agent.setYBodyRot(yaw);
        agent.setRole(role);
        agent.guard(BlockPos.containing(at));
        agent.nonLethal = nonLethal;
        return agent;
    }

    /** A summoned agent (or one from a spawn egg) gets his rifle and stands watch where he appeared. */
    @Override public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType type,
            net.minecraft.world.entity.SpawnGroupData data) {
        setRole(role());
        if (post == null) guard(blockPosition());
        return super.finalizeSpawn(level, difficulty, type, data);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(AIMING, false);
        builder.define(OUT_COLD, false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DartGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CompanyAgentEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, SylarEntity.class, 5, true, false,
                target -> target.getTags().contains(LOOSE_TAG)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 5, true, false, this::wouldStop));
    }

    // ------------------------------------------------------------------
    // Who he is
    // ------------------------------------------------------------------

    public Role role() { return Role.values()[Math.clamp(entityData.get(ROLE), 0, Role.values().length - 1)]; }

    public void setRole(Role role) {
        entityData.set(ROLE, role.ordinal());
        setItemSlot(EquipmentSlot.MAINHAND, role == Role.GUARD ? new ItemStack(Items.CROSSBOW) : ItemStack.EMPTY);
        setDropChance(EquipmentSlot.MAINHAND, 0);
    }

    /** Where he stands watch; he won't chase anyone far from it. */
    public void guard(BlockPos post) {
        this.post = post.immutable();
        restrictTo(post, role() == Role.SUPPRESSOR ? 6 : 10);
    }

    public BlockPos post() { return post; }
    public boolean aiming() { return entityData.get(AIMING); }
    public boolean outCold() { return entityData.get(OUT_COLD); }
    public boolean nonLethal() { return nonLethal; }
    public void setNonLethal(boolean nonLethal) { this.nonLethal = nonLethal; }

    /** Strangers are stopped; anyone in creative or spectating, or just put under, is left alone. */
    private boolean wouldStop(LivingEntity target) {
        return target instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator() && !Tranquilizer.spared(player);
    }

    // ------------------------------------------------------------------
    // Going down
    // ------------------------------------------------------------------

    @Override public boolean hurt(DamageSource source, float amount) {
        if (outCold()) return false;
        if (nonLethal && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && amount >= getHealth() + getAbsorptionAmount()) {
            super.hurt(source, Math.max(0, getHealth() - 1));
            knockOut();
            return true;
        }
        return super.hurt(source, amount);
    }

    /** Out cold for the rest of the night: on the floor, no orders, no field, and nothing more can hurt him. */
    public void knockOut() {
        if (outCold()) return;
        settle();
        setHealth(Math.max(1, getHealth()));
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 1.0F, 0.8F);
            if (role() == Role.SUPPRESSOR) for (ServerPlayer player : level.players())
                if (player.distanceToSqr(this) <= 24 * 24) player.sendSystemMessage(Component.literal(
                        "The man in the gray suit goes down. The quiet around him lifts, and your power comes back.").withStyle(ChatFormatting.AQUA));
        }
    }

    private void settle() {
        entityData.set(OUT_COLD, true);
        entityData.set(AIMING, false);
        setTarget(null);
        getNavigation().stop();
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        SuppressorField.update(this, false);
    }

    @Override public boolean isPushable() { return !outCold() && super.isPushable(); }

    @Override public void remove(RemovalReason reason) {
        super.remove(reason);
        SuppressorField.update(this, false);
    }

    @Override public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (role() == Role.SUPPRESSOR) {
            boolean awake = isAlive() && !outCold();
            SuppressorField.update(this, awake);
            if (awake && tickCount % 10 == 0) quietRing(level);
        }
        shouted.values().removeIf(until -> until < tickCount);
    }

    /** The edge of the quiet: a slow ring of grey motes on the floor at the field's radius. */
    private void quietRing(ServerLevel level) {
        DustParticleOptions dust = new DustParticleOptions(QUIET, 1.3F);
        double turn = tickCount * 0.02;
        for (int i = 0; i < 16; i++) {
            double angle = turn + i * Math.PI * 2 / 16;
            level.sendParticles(dust, getX() + Math.cos(angle) * SuppressorField.RADIUS, getY() + 0.15,
                    getZ() + Math.sin(angle) * SuppressorField.RADIUS, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // The rifle
    // ------------------------------------------------------------------

    /** The guard says something the first time he turns on someone (and now and then after). */
    private void shout(LivingEntity target) {
        if (!(level() instanceof ServerLevel level) || shouted.containsKey(target.getUUID())) return;
        shouted.put(target.getUUID(), tickCount + SHOUT_EVERY);
        String line = target instanceof SylarEntity
                ? (role() == Role.GUARD ? "Guard: Gray's out of his cell! Put him down!" : "The man in the gray suit turns to face Gray.")
                : role() == Role.GUARD ? "Guard: Stop right there! Nobody's supposed to be down here!"
                : "The man in the gray suit doesn't say anything. He just watches you come.";
        for (ServerPlayer player : level.players())
            if (player.distanceToSqr(this) <= 20 * 20) player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    /** True if a target this fast across the ground is likely to make a dart miss. */
    static boolean dodges(LivingEntity target, double roll) {
        double dx = target.getX() - target.xo, dz = target.getZ() - target.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        return speed > 0.5 ? roll < 0.85 : speed > 0.2 && roll < 0.45;
    }

    private void fire(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 0.9F, 1.4F);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (dodges(target, getRandom().nextDouble())) {
            Vec3 past = target.getBoundingBox().getCenter().add(target.getX() - target.xo, 0.3, target.getZ() - target.zo);
            Tranquilizer.trail(level, getEyePosition().subtract(0, 0.2, 0), past);
            return;
        }
        Tranquilizer.dart(this, target);
    }

    /** Aim, wait, fire. Moves to regain sight of the target, and stops to shoot. */
    final class DartGoal extends Goal {
        private int aim, cooldown;

        DartGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        private boolean usable() {
            LivingEntity target = getTarget();
            return role() == Role.GUARD && !outCold() && target != null && target.isAlive()
                    && distanceToSqr(target) > CLOSE * CLOSE && distanceToSqr(target) <= (DART_RANGE + 8) * (DART_RANGE + 8);
        }

        @Override public boolean canUse() { return usable(); }
        @Override public boolean canContinueToUse() { return usable(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { aim = 0; }
        @Override public void stop() { aim = 0; entityData.set(AIMING, false); }

        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (cooldown > 0) cooldown--;
            boolean sees = getSensing().hasLineOfSight(target);
            if (!sees || distanceToSqr(target) > DART_RANGE * DART_RANGE) {
                aim = 0;
                entityData.set(AIMING, false);
                getNavigation().moveTo(target, 1.0);
                return;
            }
            getNavigation().stop();
            if (cooldown > 0) return;
            if (aim == 0) {
                shout(target);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.HOSTILE, 0.7F, 1.3F);
            }
            entityData.set(AIMING, true);
            aim++;
            if (aim % 3 == 1 && level() instanceof ServerLevel level) sight(level, target);
            if (aim >= AIM_TICKS) {
                fire(target);
                aim = 0;
                cooldown = SHOT_COOLDOWN;
                entityData.set(AIMING, false);
            }
        }

        /** The telegraph: a short red line along the barrel toward whoever he is aiming at. */
        private void sight(ServerLevel level, LivingEntity target) {
            Vec3 from = getEyePosition().subtract(0, 0.2, 0);
            Vec3 to = target.getBoundingBox().getCenter();
            Vec3 step = to.subtract(from);
            double length = step.length();
            DustParticleOptions dust = new DustParticleOptions(SIGHT, 0.5F);
            for (double d = 1; d < Math.min(length, 12); d += 0.75) {
                Vec3 at = from.add(step.scale(d / length));
                level.sendParticles(dust, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("role", role().name());
        if (post != null) tag.putLong("post", post.asLong());
        tag.putBoolean("non_lethal", nonLethal);
        tag.putBoolean("out_cold", outCold());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Role role = Role.GUARD;
        try { role = Role.valueOf(tag.getString("role")); } catch (IllegalArgumentException ignored) {}
        setRole(role);
        guard(tag.contains("post") ? BlockPos.of(tag.getLong("post")) : blockPosition());
        nonLethal = tag.getBoolean("non_lethal");
        if (tag.getBoolean("out_cold")) settle();
    }
}
