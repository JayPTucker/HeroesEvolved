package com.jayptucker.heroesevolved.shapeshift;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
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
 *
 * <p>It belongs to its maker as a pet does ({@link OwnableEntity}), so every "is this a foe" question that spares your own
 * pets spares your own copies too: your own blows and blasts never burst them.</p>
 *
 * <p>Superspeed borrows it twice: a Speed Mirage's running copies ({@link #setupSpeed}), and Between Heartbeats'
 * illusions ({@link #setupIllusion}), which the Ultimate's run drives every tick and which take a few blows before they
 * break.</p>
 */
public final class ShapeDecoy extends PathfinderMob implements IEntityWithComplexSpawn, OwnableEntity {
    public static final int LIFE = 120;

    private @Nullable ShapeshiftForm form;
    private @Nullable UUID owner;
    /** The body its maker had when it was made (server only; see {@link #getOwner}). */
    private @Nullable Player maker;
    private int life = LIFE;
    /** How far it walks each tick (a Doppelganger), or null to stand still. */
    private @Nullable net.minecraft.world.phys.Vec3 walk;
    /** Whether its burst confuses everything near it (Hollow Crowd), not just whoever struck it. */
    private boolean wide;
    private boolean speedster;
    private net.minecraft.world.phys.Vec3 speedCenter;
    private int lane;
    /** An illusion moved by its maker's run, not by itself (Between Heartbeats). */
    private boolean driven;
    /** Its maker's entity id, so clients can dress the copy in their armour and held items (-1 if unknown). */
    private int ownerId = -1;
    /** What a blow on a driven illusion does (on the server): its run decides whether it dodges or breaks. */
    private @Nullable java.util.function.Predicate<net.minecraft.world.entity.Entity> struck;
    public boolean speedster() { return speedster; }
    public boolean driven() { return driven; }
    public int ownerId() { return ownerId; }
    /** The same targetable double, running as a speed mirage instead of standing as a shed shell. */
    public void setupSpeed(Player player, int ticks, int lane) {
        setup(ShapeshiftForm.of(player.getGameProfile()), player, ticks, null, false);
        speedster = true; speedCenter = player.position(); this.lane = lane;
        ownerId = player.getId();
    }

    /**
     * An illusion of a speedster (Between Heartbeats): their own face, armour and held items, moved each tick by the run
     * through {@link #runToward}. A blow asks {@code struck} (with the striker) instead of bursting it at once. It still
     * bursts by itself when {@code ticks} run out, in case its run is lost.
     */
    public void setupIllusion(Player player, int ticks, int lane, java.util.function.Predicate<net.minecraft.world.entity.Entity> struck) {
        setup(ShapeshiftForm.of(player.getGameProfile()), player, ticks, null, false);
        speedster = true; driven = true; speedCenter = player.position(); this.lane = lane;
        ownerId = player.getId();
        this.struck = struck;
    }

    /**
     * One running step of a driven illusion by {@code displacement} (level; a few blocks at most): along the ground,
     * up a block where it must, down slopes, never into a wall, fire or lava. Returns where it got to.
     */
    public net.minecraft.world.phys.Vec3 runToward(net.minecraft.world.phys.Vec3 displacement) {
        var flat = new net.minecraft.world.phys.Vec3(displacement.x, 0, displacement.z);
        var start = position();
        if (flat.lengthSqr() < 1.0E-6) { settle(); return position(); }
        var next = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, flat);
        if (next.distanceToSqr(start) < flat.lengthSqr() * .36) {
            // Blocked low: try the same stride a block up (a step, a fence, a ledge).
            var up = start.add(0, 1.05, 0);
            if (com.jayptucker.heroesevolved.speed.SpeedService.safe(this, up)) {
                var over = com.jayptucker.heroesevolved.speed.SpeedService.sweptFrom(this, up, flat, p -> true, true);
                if (over.distanceToSqr(up) > next.distanceToSqr(start)) next = over;
            }
        }
        setPos(next.x, next.y, next.z);
        settle();
        var step = position().subtract(start);
        if (step.horizontalDistanceSqr() > 1.0E-4) face((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
        return position();
    }

    /** Turns the whole body (and head) to {@code yaw}. */
    public void face(float yaw) { setYRot(yaw); setYBodyRot(yaw); setYHeadRot(yaw); }

    /** Drops onto the ground under it, up to three blocks a tick. */
    private void settle() {
        for (int i = 0; i < 12 && level().noCollision(this, getBoundingBox().move(0, -.25, 0)); i++) setPos(getX(), getY() - .25, getZ());
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
        this.maker = owner;
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

    @Override public @Nullable UUID getOwnerUUID() { return owner; }

    /**
     * Its maker: the body it was made from while that body is still in play (a game-test or another mod's fake player is
     * in no player list), else whoever in the level has its maker's UUID.
     */
    @Override public @Nullable net.minecraft.world.entity.LivingEntity getOwner() {
        if (maker != null && !maker.isRemoved()) return maker;
        return OwnableEntity.super.getOwner();
    }

    public int life() { return life; }

    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return form == null ? super.getDefaultDimensions(pose) : MorphBodies.dimensions(form.type(), Pose.STANDING);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // A struck or striking copy swings like its maker (the swing reaches clients as an animate packet).
            updateSwingTime();
            if (standIn instanceof net.minecraft.world.entity.LivingEntity body) {
                double dx = getX() - xo, dz = getZ() - zo;
                body.walkAnimation.update(Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4, 1), .4F);
            }
            return;
        }
        if (speedster && !driven) runMirage(); else if (walk != null) step();
        if (--life <= 0 || owner == null) ShapeshiftTricks.burst(this, null);
    }

    /** Any blow from something bursts the shell; nothing else (falling, fire, suffocation) touches it. */
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || isRemoved()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() == null && source.getDirectEntity() == null) return false;
        var by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        // An illusion's run decides: a step aside, or the last blow it can take.
        if (driven && struck != null) { struck.test(by); return true; }
        ShapeshiftTricks.burst(this, by);
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
        buffer.writeBoolean(driven);
        buffer.writeVarInt(ownerId + 1);
    }

    @Override public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        form = ByteBufCodecs.optional(ShapeshiftForm.STREAM_CODEC).decode(buffer).orElse(null);
        life = buffer.readVarInt();
        speedster = buffer.readBoolean();
        driven = buffer.readBoolean();
        ownerId = buffer.readVarInt() - 1;
        refreshDimensions();
    }
}
