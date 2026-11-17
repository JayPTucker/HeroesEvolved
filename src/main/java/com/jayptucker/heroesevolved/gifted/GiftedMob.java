package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.GiftedMobPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One gifted mob while it is loaded: whom it is fighting, what it is winding up, what it has done lately. Its gift
 * itself is saved on the mob ({@link Gift}); this is only the moment-to-moment state, made fresh when it loads.
 * <p>
 * Every move is read before it lands (the Cataclysm rule): a windup with a pose, a telegraph and a sound, and a heavy
 * enough blow during it breaks the cast and staggers it. Epic and Mythic mobs turn "unbound" at half health: a burst, a
 * roar, faster casting, their signature move and followers.
 */
public final class GiftedMob {
    /** Flags the client draws from. */
    public static final int EVIL = 1, CASTING = 2, UNBOUND = 4, SPENT = 8, BOSS = 16, STAGGERED = 32;

    final Mob mob;
    Gift gift;
    MobMoves.Move casting;
    int castTicks;
    UUID castTarget;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    int readyAt;
    private Vec3 mark;
    UUID pinned, held;
    int pinnedUntil, heldUntil;
    Vec3 heldAt;
    UUID flurryTarget;
    int flurryLeft, flurryNext;
    float flurryDamage;
    boolean unbound;
    int spentUntil, staggerUntil, lastCombat = -1000, lastDamaged = -1000;
    /** Empathic Mimicry: the power it copied from whoever it is fighting. */
    ResourceLocation copied;
    UUID copiedFrom;
    private final List<Later> later = new ArrayList<>();
    ServerBossEvent bar;
    private int sentFlags = -1, sentPose = -1, sentPower = -1;
    /** Shapeshifting's Apex Form: grown until then. */
    int grownUntil;

    private record Later(int at, Runnable run) {}

    GiftedMob(Mob mob, Gift gift) {
        this.mob = mob;
        this.gift = gift;
        this.readyAt = mob.tickCount + 40;
    }

    public Mob mob() { return mob; }
    public Gift gift() { return gift; }
    public boolean evil() { return gift.evil(); }
    public ServerLevel level() { return (ServerLevel) mob.level(); }
    public int now() { return mob.tickCount; }
    public boolean unbound() { return unbound; }
    public boolean casting() { return casting != null; }
    public String castingId() { return casting == null ? "" : casting.id(); }

    // ------------------------------------------------------------------
    // What moves hand back to the mob
    // ------------------------------------------------------------------

    void mark(Vec3 at) { mark = at; }
    Vec3 mark() { return mark != null ? mark : mob.position(); }

    void pin(LivingEntity target, int ticks) { pinned = target.getUUID(); pinnedUntil = now() + ticks; }

    void hold(LivingEntity target, int ticks) { held = target.getUUID(); heldAt = target.position(); heldUntil = now() + ticks; }

    void flurry(LivingEntity target, int blows, float damage) {
        flurryTarget = target.getUUID(); flurryLeft = blows; flurryNext = now(); flurryDamage = damage;
    }

    void later(int ticks, Runnable run) { later.add(new Later(now() + ticks, run)); }

    /** Worn out after a signature move: it takes more damage for a while (the window to punish it). */
    void spent(int ticks) { spentUntil = now() + ticks; sync(); }

    public boolean spentNow() { return spentUntil > now(); }
    public boolean staggered() { return staggerUntil > now(); }

    /** The damage multiplier for its moves: its rarity, the world's difficulty, more when unbound. */
    float strength() {
        float difficulty = switch (mob.level().getDifficulty()) { case PEACEFUL, EASY -> .75F; case HARD -> 1.25F; default -> 1F; };
        return gift.rarity().damage * difficulty * (unbound ? 1.15F : 1);
    }

    /** The power its moves come from now: its own, or (a mimic) whatever it copied from its foe. */
    ResourceLocation movePower() {
        return gift.power().equals(ModAbilities.MIMICRY_ID) ? (copied != null ? copied : ModAbilities.TELEKINESIS_ID) : gift.power();
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    void tick() {
        if (!com.jayptucker.heroesevolved.time.TemporalMobTime.advances(mob)) return;
        com.jayptucker.heroesevolved.time.TemporalMobTime.acting(mob, this::tickInTime);
    }

    private void tickInTime() {
        int now = now();
        runLater(now);
        tickPin(now);
        tickHold(now);
        tickFlurry(now);
        if (grownUntil > 0 && now >= grownUntil) { grownUntil = 0; MobMoves.shrink(mob); }
        passives(now);
        if (now % 10 == 0) { tickBar(); sync(); }
        if (com.jayptucker.heroesevolved.dampener.Dampening.dampened(mob)) {
            if (casting != null) cancelCast(true);
            return;
        }
        if (com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(mob) || com.jayptucker.heroesevolved.time.TimeStopService.isStopped(mob)) return;
        if (staggered()) { mob.getNavigation().stop(); return; }
        if (gift.rarity().boss() && !unbound && mob.getHealth() <= mob.getMaxHealth() / 2) unbind();
        LivingEntity target = target();
        if (casting != null) { tickCast(); return; }
        if (target == null) { idle(now); return; }
        lastCombat = now;
        if (gift.power().equals(ModAbilities.MIMICRY_ID)) copy(target);
        if (now >= readyAt) choose(target);
    }

    private void runLater(int now) {
        if (later.isEmpty()) return;
        List<Later> due = new ArrayList<>();
        for (Iterator<Later> it = later.iterator(); it.hasNext(); ) {
            Later l = it.next();
            if (now >= l.at) { due.add(l); it.remove(); }
        }
        for (Later l : due) {
            try { l.run.run(); } catch (RuntimeException e) { com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.warn("Gifted move step failed", e); }
        }
    }

    private void passives(int now) {
        // Regeneration heals as it fights; everyone else heals once the fight has been over a while (or, for a
        // renegade, once it has walked home), so leaving and coming back doesn't win a fight.
        if (gift.power().equals(ModAbilities.REGENERATION_ID) && now % 30 == 0 && mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(1 + gift.rarity().ordinal() * .5F);
            if (now % 120 == 0) AbilityEffects.send(level(), Kind.REGEN_MEND, mob.position(), mob.getEyePosition(), .8);
        }
        if (now - lastCombat > 200 && now - lastDamaged > 200 && now % 10 == 0 && mob.getHealth() < mob.getMaxHealth()) mob.heal(1);
    }

    /** No foe in sight: a renegade walks home; anyone else does whatever it was doing. */
    private void idle(int now) {
        if (gift.home() == null || mob instanceof Villager || now % 40 != 0) return;
        if (mob.blockPosition().distSqr(gift.home()) > 28 * 28) mob.getNavigation().moveTo(gift.home().getX() + .5, gift.home().getY(), gift.home().getZ() + .5, 1.0);
    }

    // ------------------------------------------------------------------
    // Whom it fights
    // ------------------------------------------------------------------

    /** Its foe now: the mob's own target if it is a foe, otherwise the nearest foe it can see. */
    LivingEntity target() {
        LivingEntity own = mob instanceof AbstractVillager ? null : mob.getTarget();
        if (own != null && !MobMoves.foe(this, own)) {
            // A renegade between kills leaves the villagers be; its own AI would chase them anyway.
            if (own instanceof AbstractVillager) mob.setTarget(null);
            own = null;
        }
        if (own != null && own.distanceToSqr(mob) <= 28 * 28) return own;
        double reach = evil() ? 20 : 16;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity t : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(reach),
                t -> MobMoves.foe(this, t) && mob.hasLineOfSight(t))) {
            // Players first, for anyone hostile; guardians go for whatever is closest to their village's people.
            double score = mob.distanceToSqr(t) - (evil() && t instanceof Player ? 400 : 0);
            if (score < bestScore) { bestScore = score; best = t; }
        }
        if (best != null && !(mob instanceof AbstractVillager) && mob.getTarget() == null) mob.setTarget(best);
        return best;
    }

    /** A mimic copies the power of whoever it fights. */
    private void copy(LivingEntity target) {
        if (target.getUUID().equals(copiedFrom)) return;
        ResourceLocation power = GiftedMobs.powerOf(target);
        if (power == null || power.equals(ModAbilities.MIMICRY_ID) || MobMoves.of(power).isEmpty()) power = ModAbilities.TELEKINESIS_ID;
        copiedFrom = target.getUUID();
        if (power.equals(copied)) return;
        copied = power;
        SetPiecePayload.send(level(), Piece.MIMIC_IMPRINT, target.getBoundingBox().getCenter(), mob.getBoundingBox().getCenter(),
                MimicColors.index(power.getPath()) + .99F, mob);
        level().playSound(null, mob.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1, 1.3F);
        sync();
    }

    // ------------------------------------------------------------------
    // Casting
    // ------------------------------------------------------------------

    private void choose(LivingEntity target) {
        double distance = Math.sqrt(mob.distanceToSqr(target));
        boolean sight = mob.hasLineOfSight(target);
        List<MobMoves.Move> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (MobMoves.Move move : MobMoves.known(movePower(), gift.rarity(), unbound)) {
            if (cooldowns.getOrDefault(move.id(), 0) > now() || distance < move.minRange() || distance > move.maxRange()
                    || move.needsSight() && !sight) continue;
            int weight = move.signature() ? 3 : 2;
            options.add(move); weights.add(weight); total += weight;
        }
        if (options.isEmpty()) return;
        int roll = mob.getRandom().nextInt(total);
        MobMoves.Move move = options.getLast();
        for (int i = 0; i < options.size(); i++) { roll -= weights.get(i); if (roll < 0) { move = options.get(i); break; } }
        start(move, target);
    }

    /** Starts winding {@code move} up at {@code target} (tests and commands call it directly). */
    public void start(MobMoves.Move move, LivingEntity target) {
        casting = move; castTicks = 0; castTarget = target.getUUID();
        mob.getNavigation().stop();
        face(target);
        try {
            move.telegraph().run(this, level(), target, strength());
        } catch (RuntimeException e) {
            com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.warn("Gifted move {} failed to start", move.id(), e);
        }
        sync();
    }

    /** Runs {@code move} at {@code target} from telegraph to landing in one go (tests, previews). */
    public void land(MobMoves.Move move, LivingEntity target) {
        float p = strength();
        move.telegraph().run(this, level(), target, p);
        move.pulse().run(this, level(), target, p);
        move.execute().run(this, level(), target, p);
    }

    private void tickCast() {
        LivingEntity target = level().getEntity(castTarget) instanceof LivingEntity t && t.isAlive() ? t
                : com.jayptucker.heroesevolved.time.TimeVeil.shadow(castTarget);
        target = com.jayptucker.heroesevolved.time.TemporalMobTime.target(mob, target);
        if (target == null) { cancelCast(false); return; }
        mob.getNavigation().stop();
        face(target);
        castTicks++;
        if (castTicks % 4 == 0) casting.pulse().run(this, level(), target, strength());
        if (castTicks < casting.windup()) return;
        MobMoves.Move move = casting;
        casting = null; castTicks = 0;
        cooldowns.put(move.id(), now() + Math.round(move.cooldown() * gift.rarity().cooldown * (unbound ? .7F : 1)));
        readyAt = now() + Math.round((move.signature() ? 50 : 36) * gift.rarity().cooldown * (unbound ? .65F : 1));
        if (!move.needsSight() || mob.hasLineOfSight(target)) {
            try {
                move.execute().run(this, level(), target, strength());
            } catch (RuntimeException e) {
                com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.warn("Gifted move {} failed", move.id(), e);
            }
        }
        if (move.signature()) spent(60);
        sync();
    }

    /** Ends a windup. {@code broken}: something broke it (a heavy blow, a dampener), so it staggers and tells. */
    void cancelCast(boolean broken) {
        if (casting == null) return;
        cooldowns.put(casting.id(), now() + casting.cooldown() / 2);
        casting = null; castTicks = 0;
        readyAt = now() + 30;
        if (broken) {
            staggerUntil = now() + 30;
            mob.getNavigation().stop();
            AbilityEffects.send(level(), Kind.PSYCHIC_IMPACT, mob.getEyePosition(), mob.getEyePosition().add(0, .5, 0), 1.2);
            level().playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1, .7F);
        }
        sync();
    }

    private void face(LivingEntity target) {
        mob.getLookControl().setLookAt(target, 60, 60);
        Vec3 to = target.position().subtract(mob.position());
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.setYBodyRot(yaw);
    }

    /** A blow landed on it: a heavy one breaks a windup; a beaten mob turns unbound. */
    void struck(float amount, Entity attacker) {
        lastDamaged = now();
        int threshold = 6 + gift.rarity().ordinal() * 2;
        if (casting != null && amount >= threshold) {
            cancelCast(true);
            if (attacker instanceof ServerPlayer p)
                p.displayClientMessage(Component.literal("You broke " + gift.name() + "'s cast!").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // ------------------------------------------------------------------
    // Holding things
    // ------------------------------------------------------------------

    private void tickPin(int now) {
        if (pinned == null) return;
        if (now >= pinnedUntil || !(level().getEntity(pinned) instanceof LivingEntity t) || !t.isAlive()) { pinned = null; return; }
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
        MobMoves.push(t, new Vec3(0, t.onGround() ? .12 : 0, 0));
        t.fallDistance = 0;
        if (now % 5 == 0) AbilityEffects.send(level(), Kind.TK_GRIP, MobMoves.hand(mob), t.getBoundingBox().getCenter(), .9);
    }

    private void tickHold(int now) {
        if (held == null) return;
        if (now >= heldUntil || !(level().getEntity(held) instanceof LivingEntity t) || !t.isAlive()) { held = null; return; }
        if (t.position().distanceToSqr(heldAt) > .04) {
            if (t instanceof ServerPlayer p) p.connection.teleport(heldAt.x, heldAt.y, heldAt.z, p.getYRot(), p.getXRot());
            else t.teleportTo(heldAt.x, heldAt.y, heldAt.z);
        }
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0;
    }

    private void tickFlurry(int now) {
        if (flurryLeft <= 0 || now < flurryNext) return;
        if (!(level().getEntity(flurryTarget) instanceof LivingEntity t) || !t.isAlive() || mob.distanceToSqr(t) > 25) { flurryLeft = 0; return; }
        MobMoves.hurt(this, t, flurryDamage);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        AbilityEffects.send(level(), Kind.SPEED_IMPACT, mob.getEyePosition(), t.getBoundingBox().getCenter(), 1);
        flurryLeft--; flurryNext = now + 3;
    }

    // ------------------------------------------------------------------
    // Unbound: the phase change
    // ------------------------------------------------------------------

    /** Half its health gone: it stops holding back. Everyone near sees and hears it. */
    void unbind() {
        unbound = true;
        if (casting != null) cancelCast(false);
        readyAt = now() + 20;
        ServerLevel level = level();
        Vec3 at = mob.position();
        SetPiecePayload.send(level, Piece.GIFT_UNBOUND, at, at.add(0, 1, 0), MimicColors.index(movePower().getPath()) + .5F, mob);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4F, 1.3F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, .8F);
        mob.removeAllEffects();
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false, false));
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(mob) <= 48 * 48)
                p.displayClientMessage(Component.literal(gift.name() + " is unbound!").withStyle(gift.rarity().color, ChatFormatting.BOLD), true);
        if (evil()) for (int i = 0; i < gift.rarity().followers; i++) follower(i);
        sync();
    }

    /** A raider who answers the unbound mob's call, and fights at its side. */
    private void follower(int i) {
        ServerLevel level = level();
        EntityType<? extends Mob> type = i % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER;
        Mob follower = type.create(level);
        if (follower == null) return;
        double a = (i + 1) * 2.3;
        Vec3 at = mob.position().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
        if (!level.noCollision(follower, follower.getType().getDimensions().makeBoundingBox(at))) at = mob.position();
        follower.moveTo(at.x, at.y, at.z, mob.getYRot(), 0);
        follower.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        follower.getPersistentData().putBoolean(GiftedMobs.FOLLOWER, true);
        follower.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        if (mob.getTarget() != null) follower.setTarget(mob.getTarget());
        level.addFreshEntity(follower);
        AbilityEffects.send(level, Kind.TEMPORAL_BURST, at, at.add(0, 1, 0), 1.2);
    }

    // ------------------------------------------------------------------
    // Boss bar and the client's picture
    // ------------------------------------------------------------------

    /** Mini-bosses and renegades get a boss bar for players close enough to fight them. */
    private void tickBar() {
        boolean wants = gift.rarity().boss() || gift.origin() == Gift.Origin.RENEGADE;
        if (!wants || !evil()) { if (bar != null) { bar.removeAllPlayers(); bar = null; } return; }
        if (bar == null) {
            bar = new ServerBossEvent(Component.empty(), gift.rarity().bar, BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(false);
        }
        Component name = Component.literal(gift.name()).withStyle(gift.rarity().color)
                .append(Component.literal("  " + gift.rarity().title + " " + GiftNames.powerName(movePower())).withStyle(ChatFormatting.GRAY));
        String extra = Renegades.barSuffix(mob);
        if (extra != null) name = name.copy().append(Component.literal("  " + extra).withStyle(ChatFormatting.WHITE));
        if (unbound) name = name.copy().append(Component.literal("  UNBOUND").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        bar.setName(name);
        bar.setProgress(Math.clamp(mob.getHealth() / mob.getMaxHealth(), 0, 1));
        for (ServerPlayer p : List.copyOf(bar.getPlayers()))
            if (p.isRemoved() || p.level() != mob.level() || p.distanceToSqr(mob) > 56 * 56) bar.removePlayer(p);
        for (ServerPlayer p : level().players())
            if (!p.isSpectator() && p.distanceToSqr(mob) <= 48 * 48) bar.addPlayer(p);
    }

    int flags() {
        int f = 0;
        if (evil()) f |= EVIL;
        if (casting != null) f |= CASTING;
        if (unbound) f |= UNBOUND;
        if (spentNow()) f |= SPENT;
        if (gift.rarity().boss()) f |= BOSS;
        if (staggered()) f |= STAGGERED;
        return f;
    }

    /** Sends what the client draws, if it changed. */
    void sync() {
        int flags = flags(), pose = casting != null ? casting.pose().ordinal() : 0, power = MimicColors.index(movePower().getPath());
        if (flags == sentFlags && pose == sentPose && power == sentPower) return;
        sentFlags = flags; sentPose = pose; sentPower = power;
        PacketDistributor.sendToPlayersTrackingEntity(mob, payload());
    }

    GiftedMobPayload payload() {
        return new GiftedMobPayload(mob.getId(), MimicColors.index(movePower().getPath()), gift.rarity().ordinal(), flags(),
                casting != null ? casting.pose().ordinal() : 0, casting != null ? casting.windup() : 0);
    }

    /** It is gone (dead, unloaded, cleansed): the bar comes down and the client forgets it. */
    void forget() {
        if (bar != null) { bar.removeAllPlayers(); bar = null; }
        if (mob.level() instanceof ServerLevel)
            PacketDistributor.sendToPlayersTrackingEntity(mob, new GiftedMobPayload(mob.getId(), 0, 0, -1, 0, 0));
    }

    /** Players near it (for announcements). */
    List<ServerPlayer> near(double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level().players()) if (!p.isSpectator() && p.distanceToSqr(mob) <= radius * radius) out.add(p);
        return out;
    }

    AABB box(double r) { return mob.getBoundingBox().inflate(r); }
}
