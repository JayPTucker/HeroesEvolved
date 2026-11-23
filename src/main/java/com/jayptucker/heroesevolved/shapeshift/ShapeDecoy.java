package com.jayptucker.heroesevolved.shapeshift;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Decoy: the empty shell a shapeshifter sheds where they stand (docs/shapeshifting.md, "Core abilities"). It wears the
 * shape its maker wore (their own face if they wore none), stands still, and draws the mobs that were hunting them. The
 * first blow bursts it into plates; otherwise it crumbles after six seconds. It is never saved and can't be summoned.
 * Clients learn its shape from the spawn data, so a viewer who arrives late still sees the right body.
 */
public final class ShapeDecoy extends PathfinderMob implements IEntityWithComplexSpawn {
    public static final int LIFE = 120;

    private @Nullable ShapeshiftForm form;
    private @Nullable UUID owner;
    private int life = LIFE;
    /** How far it walks each tick (a Doppelganger), or null to stand still. */
    private @Nullable net.minecraft.world.phys.Vec3 walk;
    /** Whether its burst confuses everything near it (Hollow Crowd), not just whoever struck it. */
    private boolean wide;
    private boolean speedster;
    private net.minecraft.world.phys.Vec3 speedCenter;
    private int lane;
    public boolean speedster() { return speedster; }
    /** The same targetable double, running as a speed mirage instead of standing as a shed shell. */
    public void setupSpeed(Player player, int ticks, int lane) {
        setup(ShapeshiftForm.of(player.getGameProfile()), player, ticks, null, false);
        speedster = true; speedCenter = player.position(); this.lane = lane;
    }

    private void runMirage() {
        double phase = lane * Math.PI * 2 / 3 + tickCount * .24;
        var goal = speedCenter.add(Math.cos(phase) * (3 + lane), 0, Math.sin(phase) * (3 + lane));
        var desired = goal.subtract(position()).multiply(1, 0, 1);
        if (desired.lengthSqr() < .001) return;
        desired = desired.normalize().scale(Math.min(1.1, desired.length()));
        var next = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, desired);
        var step = next.subtract(position());
        if (step.horizontalDistanceSqr() < .01) { lane = (lane + 1) % 3; return; }
        walk = step;
        step();
        float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        setYRot(yaw); setYBodyRot(yaw); setYHeadRot(yaw);
        if (tickCount % 3 == 0) com.jayptucker.heroesevolved.effects.AbilityEffects.send(
                (net.minecraft.server.level.ServerLevel) level(),
                com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.SPEED_TRAIL,
                position().subtract(step), position(), .6);
    }
    /** On the client: the body drawn for it, made on first draw (see {@code ShapeDecoyRenderer}). */
    public @Nullable Object standIn;

    public ShapeDecoy(EntityType<? extends ShapeDecoy> type, Level level) {
        super(type, level);
        setNoAi(true);
        setSilent(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    void setup(ShapeshiftForm form, Player owner, int life, @Nullable net.minecraft.world.phys.Vec3 walk, boolean wide) {
        this.form = form;
        this.owner = owner.getUUID();
        this.life = life;
        this.walk = walk;
        this.wide = wide;
        refreshDimensions();
    }

    public boolean wide() { return wide; }

    public boolean walking() { return walk != null; }

    /** One step of a Doppelganger's walk: on along the ground, up a block where it must, stopping at a wall. */
    private void step() {
        var box = getBoundingBox();
        var next = position().add(walk);
        if (!level().noCollision(this, box.move(walk))) {
            if (level().noCollision(this, box.move(walk).move(0, 1.05, 0))) next = next.add(0, 1.05, 0);
            else { walk = null; return; }
        }
        setPos(next.x, next.y, next.z);
        for (int i = 0; i < 3 && level().noCollision(this, getBoundingBox().move(0, -.25, 0)); i++) setPos(getX(), getY() - .25, getZ());
    }

    public @Nullable ShapeshiftForm form() { return form; }

    public @Nullable UUID owner() { return owner; }

    public int life() { return life; }

    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return form == null ? super.getDefaultDimensions(pose) : MorphBodies.dimensions(form.type(), Pose.STANDING);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (standIn instanceof net.minecraft.world.entity.LivingEntity body) {
                double dx = getX() - xo, dz = getZ() - zo;
                body.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4, 1), .4F);
            }
            return;
        }
        if (speedster) runMirage(); else if (walk != null) step();
        if (--life <= 0 || owner == null) ShapeshiftTricks.burst(this, null);
    }

    /** Any blow from something bursts the shell; nothing else (falling, fire, suffocation) touches it. */
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() == null && source.getDirectEntity() == null) return false;
        ShapeshiftTricks.burst(this, source.getEntity() != null ? source.getEntity() : source.getDirectEntity());
        return true;
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity entity) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean shouldBeSaved() { return false; }

    @Override public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        ByteBufCodecs.optional(ShapeshiftForm.STREAM_CODEC).encode(buffer, Optional.ofNullable(form));
        buffer.writeVarInt(life);
        buffer.writeBoolean(speedster);
    }

    @Override public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        form = ByteBufCodecs.optional(ShapeshiftForm.STREAM_CODEC).decode(buffer).orElse(null);
        life = buffer.readVarInt();
        speedster = buffer.readBoolean();
        refreshDimensions();
    }
}
