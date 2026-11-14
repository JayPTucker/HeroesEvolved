package com.jayptucker.heroesevolved.story.actor;

import com.jayptucker.heroesevolved.effects.SpeechBubble;
import com.jayptucker.heroesevolved.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * Someone from the story who acts in a scene: Noah Bennet, Claire, Peter Petrelli, Ted Sprague. Never saved, never
 * hurt, no will of their own: the scene moves them ({@link #walkTo}, {@link #face}), poses them ({@link #pose(Pose)})
 * and puts words in their mouths ({@link #say}). Their look comes from their {@link Role}, which every client sees.
 */
public final class StoryActorEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(StoryActorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(StoryActorEntity.class, EntityDataSerializers.INT);
    /** Whose skin a {@link Role#TRAVELER} wears: the player it is a double of. */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> PLAYER =
            SynchedEntityData.defineId(StoryActorEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public enum Role {
        NOAH("Noah Bennet", ChatFormatting.GRAY), CLAIRE("Claire Bennet", ChatFormatting.GOLD),
        PETER("Peter Petrelli", ChatFormatting.DARK_AQUA), TED("Ted Sprague", ChatFormatting.RED),
        SYLAR("Sylar", ChatFormatting.DARK_RED),
        /** The vision's own time traveler, come back for Peter: a player's double, their skin and their name. */
        TRAVELER("Time Traveler", ChatFormatting.LIGHT_PURPLE);

        public final String display;
        public final ChatFormatting color;
        Role(String display, ChatFormatting color) { this.display = display; this.color = color; }
    }

    /**
     * IDLE stands; PLEAD reaches out with both hands; SHOVE pushes both palms forward; AIM raises a straight right arm;
     * REACH holds someone by the shoulders; KNEEL is down on one knee, doubled over; GLOW is Ted's: arms out, shaking;
     * HAND_ON_GLASS lays one palm flat in front of him. In the air: HOVER holds still with the legs hanging; FLY lies along
     * the line of flight, fist first; STRIKE throws a hand out at someone (a punch, a bolt, a telekinetic grip); TUMBLE is
     * thrown end over end.
     */
    public enum Pose { IDLE, PLEAD, SHOVE, AIM, REACH, KNEEL, GLOW, HAND_ON_GLASS, HOVER, FLY, STRIKE, TUMBLE }

    private Vec3 walkTarget;
    private double walkSpeed = 0.5;
    private net.minecraft.world.entity.Display.TextDisplay bubble;
    private int bubbleUntil;

    public StoryActorEntity(EntityType<? extends StoryActorEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    /** Puts a new actor in the world, facing {@code yaw}, tagged so the scene that made it can find and remove it. */
    public static StoryActorEntity spawn(ServerLevel level, Role role, Vec3 at, float yaw, String tag) {
        StoryActorEntity actor = ModEntities.STORY_ACTOR.get().create(level);
        if (actor == null) return null;
        actor.role(role);
        actor.moveTo(at.x, at.y, at.z, yaw, 0);
        actor.setYHeadRot(yaw);
        actor.setYBodyRot(yaw);
        actor.addTag(tag);
        actor.addTag(TAG);
        return level.addFreshEntity(actor) ? actor : null;
    }

    /** On every actor, whatever scene made it. */
    public static final String TAG = "heroesevolved_story_actor";

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(POSE, 0);
        builder.define(PLAYER, java.util.Optional.empty());
    }

    @Override protected void registerGoals() { }

    public Role role() { return Role.values()[Math.clamp(entityData.get(ROLE), 0, Role.values().length - 1)]; }
    public void role(Role role) {
        entityData.set(ROLE, role.ordinal());
        setCustomName(Component.literal(role.display));
        // Noah's glasses are a real pair on his face (the head item layer draws them), not pixels on his skin.
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, role == Role.NOAH
                ? new net.minecraft.world.item.ItemStack(com.jayptucker.heroesevolved.registry.ModItems.HORN_RIMMED_GLASSES.get())
                : net.minecraft.world.item.ItemStack.EMPTY);
        setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0);

    }
    /**
     * Makes this actor a double of {@code player}: their skin (looked up by each client) and their name, shown over the
     * head and used when it speaks. For {@link Role#TRAVELER}.
     */
    public void doubleOf(java.util.UUID player, String name) {
        entityData.set(PLAYER, java.util.Optional.of(player));
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
    }

    /** The player this actor is a double of, if any. */
    public java.util.Optional<java.util.UUID> doubleOf() { return entityData.get(PLAYER); }

    /** The name it goes by in chat and over its head: a double's own name, otherwise its role's. */
    public String displayName() {
        return role() == Role.TRAVELER && getCustomName() != null ? getCustomName().getString() : role().display;
    }

    public Pose pose() { return Pose.values()[Math.clamp(entityData.get(POSE), 0, Pose.values().length - 1)]; }
    public void pose(Pose pose) { entityData.set(POSE, pose.ordinal()); }

    /** Walks to {@code at} on the ground, at a walk ({@code 0.5}) or a hurry ({@code 0.8}). */
    public void walkTo(Vec3 at, double speed) {
        walkTarget = at;
        walkSpeed = speed;
        getNavigation().moveTo(at.x, at.y, at.z, speed);
    }

    public boolean arrived() { return walkTarget == null || position().distanceToSqr(walkTarget) < 0.5; }

    /**
     * In the air, at {@code at}: the scene moves a flying actor every tick along its own path (SkyFight), and the actor
     * turns to its line of flight (pitch included, which the renderer leans into) or, if barely moving, to {@code look}.
     */
    public void glide(Vec3 at, Vec3 look) {
        Vec3 step = at.subtract(position());
        setNoGravity(true);
        noPhysics = true;
        walkTarget = null;
        getNavigation().stop();
        setPos(at);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        Vec3 aim = step.lengthSqr() > 0.04 ? step : look == null ? null : look.subtract(at);
        if (aim == null || aim.lengthSqr() < 1e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
        setYRot(yaw); setYBodyRot(yaw); setYHeadRot(yaw);
        setXRot(step.lengthSqr() > 0.04 ? pitch : 0);
    }

    /** Back on the ground after flying: gravity again, and upright. */
    public void land() {
        setNoGravity(false);
        noPhysics = false;
        setXRot(0);
    }

    /** Seen from far off: the sky fight is watched from the far end of the avenue (time/intro/SkyFight). */
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    /** Turns body and head toward {@code at} at once. */
    public void face(Vec3 at) {
        Vec3 d = at.subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        getLookControl().setLookAt(at.x, at.y, at.z, 60, 60);
    }

    /** Puts the actor exactly here, as when someone is pushed into a pod. */
    public void place(Vec3 at, float yaw) {
        getNavigation().stop();
        walkTarget = null;
        teleportTo(at.x, at.y, at.z);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    /**
     * Speaks a line: in chat to {@code listeners}, as {@code <Name> line}, and over the actor's head for anyone
     * nearby. {@code shout} makes the bubble bold and red.
     */
    public void say(String line, boolean shout, Collection<ServerPlayer> listeners, String bubbleTag) {
        Component chat = Component.literal("<").append(Component.literal(displayName()).withStyle(role().color))
                .append(Component.literal("> " + line));
        for (ServerPlayer listener : listeners) listener.sendSystemMessage(chat);
    }

    @Override public void remove(RemovalReason reason) {
        if (bubble != null) bubble.discard();
        super.remove(reason);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (bubble != null && (tickCount >= bubbleUntil || bubble.isRemoved())) { bubble.discard(); bubble = null; }
        if (walkTarget == null) return;
        if (arrived()) { getNavigation().stop(); walkTarget = null; return; }
        // A path can be dropped when a door shuts or a player stands in the way: ask again.
        if (getNavigation().isDone() && tickCount % 10 == 0) getNavigation().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, walkSpeed);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean shouldShowName() { return role() == Role.TRAVELER; }
    @Override public boolean isAlliedTo(Entity other) { return other instanceof Player || super.isAlliedTo(other); }
}
