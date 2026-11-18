package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.intro.Danger;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.network.VibeHologramPayload;
import com.jayptucker.heroesevolved.network.VibeVisionPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.jayptucker.heroesevolved.telepathy.MindVoiceRoutes;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vibe's uncontrollable visions, as Cisco has them on the show. At most once every
 * {@link HeroesEvolvedConfig.Common#vibeVisionMinutes} minutes: always when an ally more than {@link #ALLY_NEAREST}
 * blocks away (and within twelve chunks) drops below three hearts, and sometimes when a village there is in trouble or
 * about to be, the viber's sight flickers and leaves their body: for ten seconds they
 * drift there as a faint blue watcher (a spectator held on a leash round what they are shown), while a still double of
 * them stands where they were. A blow to the double tears them back at once, and the blow lands.
 * <p>
 * From Power Level {@link #HOLOGRAM_LEVEL}, any power key during a vision projects them there as a hologram that the
 * people near can see and, with Simple Voice Chat, hear ({@link #voices}); it can't touch anything. Afterwards, for
 * {@link #SPOT_TICKS}, a held Breach opens beside what they saw ({@link #spot}).
 */
public final class VibeVisions {
    /** Twelve chunks: how far a vision reaches, and the nearest village trouble it bothers with (closer, you can see it). */
    public static final int RANGE = 192, NEAREST = 16;
    /**
     * An ally only comes to a viber in a vision from farther than this, and only once they are below
     * {@link #DANGER_HEALTH} health (three hearts). Closer than this, you are close enough to help.
     */
    public static final int ALLY_NEAREST = 40;
    public static final float DANGER_HEALTH = 6;
    public static final int FLICKER = 16, LENGTH = 200, LEASH = 24, SPOT_TICKS = 600, CHECK = 40;
    /** The Power Level from which a viber can appear to the people in a vision. */
    public static final int HOLOGRAM_LEVEL = 12;
    /** How long a village incident being prepared counts as something a vision can see coming. */
    static final int FORESIGHT_TICKS = 20 * 45;
    /** Village trouble comes in a vision only some of the times it could; an ally in danger always does. */
    static final float CHANCE = .45F;
    static final String SAVE = "heroesevolved_vibe_vision", BODY_TAG = "heroesevolved_vibe_vision_body",
            NEXT = "heroesevolved_vibe_vision_next";

    /** Appended only: the client tints a vision by this ordinal. TOUCH is a vision of whoever owns what you touched. */
    public enum Kind { ALLY, VILLAGE, FUTURE, TOUCH }

    /** What a vision shows: someone in danger (followed while they move), a village in trouble, or one about to be. */
    record Sight(Kind kind, ServerLevel level, Vec3 at, @Nullable LivingEntity focus, Component what) {
        Vec3 center() {
            return focus != null && focus.isAlive() && focus.level() == level ? focus.position() : at;
        }
    }

    private static final class Vision {
        final ServerPlayer player;
        final Sight sight;
        final long started;
        final ServerLevel home;
        final Vec3 at;
        final float yaw, pitch;
        final GameType mode;
        final Set<ServerPlayer> seers = new HashSet<>();
        StoryActorEntity body;
        boolean inside, hologram;

        Vision(ServerPlayer player, Sight sight, long started) {
            this.player = player;
            this.sight = sight;
            this.started = started;
            home = player.serverLevel();
            at = player.position();
            yaw = player.getYRot();
            pitch = player.getXRot();
            mode = player.gameMode.getGameModeForPlayer();
        }

        long age() { return home.getGameTime() - started; }
    }

    private record Spot(VibeFrequencies.Frequency frequency, long until) {}
    private record Foreseen(ServerLevel level, BlockPos at, VillageIncidents.Kind kind, long until) {}
    /** A voice the hologram carries: to {@code listener}, as if from {@code position}. */
    public record Voice(UUID listener, Vec3 position, double range) {}

    private static final Map<UUID, Vision> VISIONS = new HashMap<>();
    private static final Map<UUID, Spot> SPOTS = new HashMap<>();
    private static final List<Foreseen> FORESEEN = new ArrayList<>();
    private static volatile Map<UUID, List<Voice>> voices = Map.of();
    /** True while a double is being added, before the vision holds it. */
    private static boolean spawning;

    private VibeVisions() {}

    // --- Seeing it coming ---

    /** A village incident is being prepared ({@code VillageIncidents.requestAt}): a vision may see it before it happens. */
    public static void foresee(ServerLevel level, BlockPos village, VillageIncidents.Kind kind) {
        FORESEEN.add(new Foreseen(level, village, kind, level.getGameTime() + FORESIGHT_TICKS));
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && VibeService.nativePower(player) && VibeService.active(player)
                && !player.isPassenger() && !player.isVehicle() && !PowerIntroService.isActive(player)
                && VibeBreaches.travelWorld(player.serverLevel()) && !VISIONS.containsKey(player.getUUID());
    }

    /** What this viber would be shown now, or null: an ally nearest to death first, then a village in trouble, then one about to be. */
    static @Nullable Sight find(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 me = player.position();
        List<LivingEntity> people = new ArrayList<>(level.players());
        people.addAll(level.getEntitiesOfClass(TamableAnimal.class, new AABB(me, me).inflate(RANGE), pet -> pet.isOwnedBy(player)));
        // The ally nearest to death, below three hearts and too far away to see.
        LivingEntity worst = null;
        float lowest = DANGER_HEALTH;
        for (LivingEntity other : people) {
            if (!Danger.ally(player, other) || far(me, other.position())
                    || me.distanceToSqr(other.position()) < (double) ALLY_NEAREST * ALLY_NEAREST) continue;
            if (other.getHealth() >= lowest) continue;
            worst = other;
            lowest = other.getHealth();
        }
        if (worst != null)
            return new Sight(Kind.ALLY, level, worst.position(), worst,
                    Component.translatable("vibe.heroesevolved.vision.ally", worst.getDisplayName(), bearing(me, worst.position())));
        for (VillagerMeltdown.Meltdown meltdown : VillagerMeltdown.all()) {
            LivingEntity villager = meltdown.villager();
            if (villager.level() != level || !villager.isAlive() || far(me, villager.position()) || near(me, villager.position())) continue;
            return new Sight(Kind.VILLAGE, level, villager.position(), villager,
                    Component.translatable("vibe.heroesevolved.vision.meltdown", bearing(me, villager.position())));
        }
        for (Renegades.Terror terror : Renegades.all()) {
            LivingEntity renegade = terror.renegade();
            if (renegade.level() != level || !renegade.isAlive() || far(me, renegade.position()) || near(me, renegade.position())) continue;
            return new Sight(Kind.VILLAGE, level, renegade.position(), renegade,
                    Component.translatable("vibe.heroesevolved.vision.renegade", bearing(me, renegade.position())));
        }
        Raid raid = level.getRaids().getNearbyRaid(player.blockPosition(), RANGE * RANGE);
        if (raid != null && !raid.isOver()) {
            Vec3 center = Vec3.atBottomCenterOf(raid.getCenter());
            if (!near(me, center))
                return new Sight(Kind.VILLAGE, level, center, null, Component.translatable("vibe.heroesevolved.vision.raid", bearing(me, center)));
        }
        for (Foreseen coming : FORESEEN) {
            Vec3 center = Vec3.atBottomCenterOf(coming.at());
            if (coming.level() != level || far(me, center) || near(me, center)) continue;
            String what = switch (coming.kind()) {
                case MELTDOWN -> "meltdown";
                case RENEGADE -> "renegade";
                default -> "awakening";
            };
            return new Sight(Kind.FUTURE, level, center, null,
                    Component.translatable("vibe.heroesevolved.vision.future." + what, bearing(me, center)));
        }
        return null;
    }

    private static boolean far(Vec3 me, Vec3 at) { return me.distanceToSqr(at) > (double) RANGE * RANGE; }
    private static boolean near(Vec3 me, Vec3 at) { return me.distanceToSqr(at) < (double) NEAREST * NEAREST; }

    /** "140 blocks north-east". */
    private static Component bearing(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int octant = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-d.x, d.z)) / 45F), 8);
        return Component.translatable("vibe.heroesevolved.vision.bearing", (int) d.horizontalDistance(),
                Component.translatable("vibe.heroesevolved.direction." + names[octant].replace('-', '_')));
    }

    // --- Touch: a vision of whoever a thing belongs to ---

    /** Who placed a personal block (in its block entity's saved data). */
    static final String PLACED_BY = "heroesevolved_placed_by";
    /** How far a hand reaches to touch something, and the wait between visions by touch. */
    public static final double TOUCH_REACH = 2.5;
    public static final int TOUCH_REST = 20 * 30;
    private static final Map<UUID, Long> TOUCHED = new HashMap<>();

    /** The blocks that keep a trace of whoever placed them: where someone sleeps, keeps their things and cooks. */
    public static boolean personal(BlockState state) {
        return state.is(BlockTags.BEDS) || state.is(BlockTags.SHULKER_BOXES) || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
    }

    /** A player placed a personal block: remember them on it (both halves of a bed). Called from the place event. */
    public static void placed(ServerLevel level, BlockPos pos, UUID by) {
        BlockState state = level.getBlockState(pos);
        if (!personal(state)) return;
        stamp(level, pos, by);
        if (state.getBlock() instanceof BedBlock) stamp(level, pos.relative(BedBlock.getConnectedDirection(state)), by);
    }

    private static void stamp(ServerLevel level, BlockPos pos, UUID by) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return;
        entity.getPersistentData().putUUID(PLACED_BY, by);
        entity.setChanged();
    }

    /** Whoever placed this block, if it is a personal block that remembers. */
    static @Nullable UUID placedBy(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!personal(state)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null && entity.getPersistentData().hasUUID(PLACED_BY)) return entity.getPersistentData().getUUID(PLACED_BY);
        if (state.getBlock() instanceof BedBlock) {
            BlockEntity other = level.getBlockEntity(pos.relative(BedBlock.getConnectedDirection(state)));
            if (other != null && other.getPersistentData().hasUUID(PLACED_BY)) return other.getPersistentData().getUUID(PLACED_BY);
        }
        return null;
    }

    private record Touched(UUID owner, Component what, Vec3 at) {}

    /** What the viber's hand is on, within {@link #TOUCH_REACH}: someone's tamed animal, or a personal block they placed. */
    private static @Nullable Touched touched(ServerPlayer player) {
        LivingEntity pet = com.jayptucker.heroesevolved.ability.Targeting.livingEntityInSight(player, TOUCH_REACH,
                entity -> entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null);
        if (pet instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
            return new Touched(owned.getOwnerUUID(), pet.getDisplayName(), pet.getBoundingBox().getCenter());
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(TOUCH_REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        UUID owner = placedBy(player.serverLevel(), hit.getBlockPos());
        return owner == null ? null
                : new Touched(owner, player.level().getBlockState(hit.getBlockPos()).getBlock().getName(), hit.getLocation());
    }

    /**
     * Read the Vibe with a hand on something that belongs to another player (their bed, chest or furnace, or their tamed
     * animal): a vision of that player, wherever they are in this world. True if the touch was such a thing (the read is
     * spent, even if its owner can't be seen); false to read the scene as usual.
     */
    public static boolean touch(ServerPlayer player) {
        Touched touched = touched(player);
        if (touched == null || touched.owner().equals(player.getUUID()) || !eligible(player)) return false;
        ArmPosePayload.show(player, ArmPose.VIBE_READ, 24);
        SetPiecePayload.send(player.serverLevel(), Piece.VIBE_TOUCH, VibeGeometry.hand(player, player.getLookAngle(), 1, false),
                touched.at(), 1, player);
        StyleOps.sound(player.serverLevel(), touched.at(), ModSounds.VIBE_ECHO.get(), .7F, .62F);
        long now = player.serverLevel().getGameTime();
        Long rested = TOUCHED.get(player.getUUID());
        if (rested != null && rested > now) {
            player.displayClientMessage(Component.translatable("vibe.heroesevolved.vision.touch_rest", (rested - now + 19) / 20), true);
            return true;
        }
        Player owner = player.serverLevel().getPlayerByUUID(touched.owner());
        if (!(owner instanceof ServerPlayer seen) || seen.isSpectator() || !seen.isAlive()) {
            player.displayClientMessage(Component.translatable("vibe.heroesevolved.vision.touch_lost", touched.what()), true);
            return true;
        }
        TOUCHED.put(player.getUUID(), now + TOUCH_REST);
        begin(player, new Sight(Kind.TOUCH, player.serverLevel(), seen.position(), seen, Component.translatable(
                "vibe.heroesevolved.vision.touch", touched.what(), seen.getDisplayName(), bearing(player.position(), seen.position()))));
        return true;
    }

    // --- The vision ---

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        FORESEEN.removeIf(coming -> coming.level().getServer() != server || coming.level().getGameTime() > coming.until());
        SPOTS.values().removeIf(spot -> spot.until() < now);
        for (Vision vision : List.copyOf(VISIONS.values())) if (vision.player.server == server) step(vision);
        publish();
        int minutes = HeroesEvolvedConfig.COMMON.vibeVisionMinutes.get();
        if (minutes <= 0 || now % CHECK != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (next(player) > now || !eligible(player)) continue;
            Sight sight = find(player);
            if (sight == null || sight.kind() != Kind.ALLY && player.getRandom().nextFloat() > CHANCE) continue;
            begin(player, sight);
            rest(player, now + minutes * 1200L);
        }
    }

    /**
     * When this viber may next have a vision (the overworld's game time). Saved on the player, so logging out and back in
     * never resets the wait.
     */
    static long next(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.contains(NEXT) ? persisted.getLong(NEXT) : Long.MIN_VALUE;
    }

    static void rest(ServerPlayer player, long until) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLong(NEXT, until);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Tests and previews: has the vision this viber would have now, ignoring the chance and the wait. */
    public static boolean trigger(ServerPlayer player) {
        if (!eligible(player)) return false;
        Sight sight = find(player);
        if (sight == null) return false;
        begin(player, sight);
        return true;
    }

    private static void begin(ServerPlayer player, Sight sight) {
        Vision vision = new Vision(player, sight, player.serverLevel().getGameTime());
        VISIONS.put(player.getUUID(), vision);
        VibeBreaches.dropPress(player);
        PacketDistributor.sendToPlayer(player, VibeVisionPayload.start(FLICKER, LENGTH, sight.kind().ordinal()));
        ArmPosePayload.show(player, ArmPose.VIBE_READ, FLICKER + 6);
        StyleOps.sound(player.serverLevel(), player.getEyePosition(), ModSounds.VIBE_ECHO.get(), .9F, .62F);
        player.sendSystemMessage(sight.what().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
    }

    private static void step(Vision vision) {
        ServerPlayer player = vision.player;
        long age = vision.age();
        if (player.isRemoved()) {
            end(vision);
            return;
        }
        if (!vision.inside) {
            if (age >= FLICKER && !enter(vision)) end(vision);
            return;
        }
        if (!player.isSpectator() || player.level() != vision.sight.level()) {
            end(vision);
            return;
        }
        // They are only watching: no riding a mob's eyes, and no drifting off from what they were shown.
        if (player.getCamera() != player) player.setCamera(player);
        Vec3 center = vision.sight.center();
        if (player.position().distanceToSqr(center) > (double) LEASH * LEASH) {
            Vec3 back = center.add(player.position().subtract(center).normalize().scale(LEASH - 1.5));
            player.teleportTo(vision.sight.level(), back.x, back.y, back.z, player.getYRot(), player.getXRot());
            player.absMoveTo(back.x, back.y, back.z, player.getYRot(), player.getXRot());
        }
        if ((vision.sight.kind() == Kind.ALLY || vision.sight.kind() == Kind.TOUCH) && vision.sight.focus() != null && age % 20 == 0)
            for (LivingEntity threat : Danger.threats(player, vision.sight.focus(), 12)) VibeSight.mark(player, threat, SPOT_TICKS);
        if (vision.hologram && age % 2 == 0) show(vision, true);
        if (age >= FLICKER + LENGTH) end(vision);
    }

    /**
     * Out of the body: a still double stands where the viber was, and they become a watcher, placed a few blocks from what
     * they are shown and looking at it.
     */
    private static boolean enter(Vision vision) {
        ServerPlayer player = vision.player;
        if (!player.isAlive() || player.isSpectator() || player.serverLevel() != vision.home || player.isPassenger()) return false;
        save(vision);
        spawning = true;
        try {
            vision.body = StoryActorEntity.spawn(vision.home, StoryActorEntity.Role.TRAVELER, vision.at, vision.yaw, BODY_TAG);
        } finally {
            spawning = false;
        }
        if (vision.body != null) {
            vision.body.doubleOf(player.getUUID(), player.getGameProfile().getName());
            vision.body.setInvulnerable(false);
            vision.body.setXRot(vision.pitch);
        }
        vision.inside = true;
        player.setGameMode(GameType.SPECTATOR);
        Vec3 center = vision.sight.center();
        Vec3 from = vision.at.subtract(center).multiply(1, 0, 1);
        from = from.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : from.normalize();
        boolean person = vision.sight.kind() == Kind.ALLY || vision.sight.kind() == Kind.TOUCH;
        double back = person ? 4.5 : 9;
        Vec3 eye = center.add(from.scale(back)).add(0, person ? 1.6 : 5, 0);
        Vec3 look = center.add(0, 1, 0).subtract(eye);
        float yaw = VibeGeometry.yaw(look), pitch = VibeGeometry.pitch(look);
        player.teleportTo(vision.sight.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        // A game-test player's connection ignores teleports.
        player.absMoveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        if (PlayerProgressionService.getPowerLevel(player) >= HOLOGRAM_LEVEL && vision.sight.kind() != Kind.FUTURE)
            player.sendSystemMessage(Component.translatable("vibe.heroesevolved.vision.hint").withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** Back into the body. Afterwards a held Breach can open beside what the vision showed. */
    private static void end(Vision vision) {
        ServerPlayer player = vision.player;
        if (VISIONS.get(player.getUUID()) == vision) VISIONS.remove(player.getUUID());
        if (vision.hologram) hologram(vision, false);
        if (vision.inside) {
            vision.inside = false;
            if (player.isSpectator()) player.setGameMode(vision.mode);
            player.teleportTo(vision.home, vision.at.x, vision.at.y, vision.at.z, vision.yaw, vision.pitch);
            player.absMoveTo(vision.at.x, vision.at.y, vision.at.z, vision.yaw, vision.pitch);
            player.resetFallDistance();
            player.setDeltaMovement(Vec3.ZERO);
            if (vision.body != null) vision.body.discard();
            clearSave(player);
            Vec3 center = vision.sight.center();
            Vec3 toward = vision.at.subtract(center).multiply(1, 0, 1);
            toward = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
            Vec3 feet = StyleOps.ground(vision.sight.level(), center.add(toward.scale(3)).add(0, 1.5, 0));
            SPOTS.put(player.getUUID(), new Spot(new VibeFrequencies.Frequency("", "", vision.sight.level().dimension().location(),
                    feet, VibeGeometry.yaw(toward.reverse())), player.serverLevel().getGameTime() + SPOT_TICKS));
            StyleOps.sound(vision.home, player.getEyePosition(), ModSounds.VIBE_SHUT.get(), .7F, .7F);
            if (PlayerProgressionService.getLevel(player) >= 2 && player.isAlive())
                player.sendSystemMessage(Component.translatable("vibe.heroesevolved.vision.after").withStyle(ChatFormatting.GRAY));
        }
        if (!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, VibeVisionPayload.end());
    }

    // --- The hologram ---

    /** True while a vision holds this player: their power keys go to {@link #press}, not their powers. */
    public static boolean watching(ServerPlayer player) { return VISIONS.containsKey(player.getUUID()); }

    /** A power key during a vision: appear to the people there, or fade from their sight. True if it did something. */
    public static boolean press(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        if (vision == null || !vision.inside) return false;
        if (vision.sight.kind() == Kind.FUTURE) {
            player.displayClientMessage(Component.translatable("vibe.heroesevolved.vision.not_yet"), true);
            return false;
        }
        if (PlayerProgressionService.getPowerLevel(player) < HOLOGRAM_LEVEL) {
            player.displayClientMessage(Component.translatable("vibe.heroesevolved.vision.watching", HOLOGRAM_LEVEL), true);
            return false;
        }
        hologram(vision, !vision.hologram);
        return true;
    }

    private static void hologram(Vision vision, boolean on) {
        vision.hologram = on;
        ServerPlayer player = vision.player;
        Vec3 feet = player.position();
        SetPiecePayload.send(vision.sight.level(), Piece.VIBE_PROJECT, feet, feet.add(player.getLookAngle()), on ? 1 : .5F, null);
        StyleOps.sound(vision.sight.level(), feet, on ? ModSounds.VIBE_BREACH.get() : ModSounds.VIBE_SHUT.get(), .6F, on ? 1.6F : 1.3F);
        player.displayClientMessage(Component.translatable(on ? "vibe.heroesevolved.vision.projected" : "vibe.heroesevolved.vision.faded"), true);
        show(vision, on);
    }

    /** Tells the players near the hologram where it stands (or that it is gone), and forgets those who left. */
    private static void show(Vision vision, boolean on) {
        ServerPlayer player = vision.player;
        var payload = new VibeHologramPayload(player.getUUID(), on, player.position(), player.getYHeadRot(), player.getXRot());
        Set<ServerPlayer> now = new HashSet<>();
        if (on) for (ServerPlayer viewer : vision.sight.level().players())
            if (viewer != player && !viewer.isSpectator() && viewer.distanceToSqr(player) < 64 * 64) now.add(viewer);
        for (ServerPlayer viewer : now) PacketDistributor.sendToPlayer(viewer, payload);
        var gone = new VibeHologramPayload(player.getUUID(), false, player.position(), 0, 0);
        for (ServerPlayer viewer : vision.seers) if (!now.contains(viewer) && !viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, gone);
        vision.seers.clear();
        vision.seers.addAll(now);
    }

    /** Voice paths for Simple Voice Chat's thread: each projecting viber to the players near their hologram. */
    private static void publish() {
        if (VISIONS.isEmpty()) {
            if (!voices.isEmpty()) voices = Map.of();
            return;
        }
        double range = MindVoiceRoutes.voiceDistance();
        Map<UUID, List<Voice>> next = new HashMap<>();
        for (Vision vision : VISIONS.values()) {
            if (!vision.inside || !vision.hologram) continue;
            ServerPlayer player = vision.player;
            Vec3 mouth = player.getEyePosition();
            List<Voice> routes = new ArrayList<>();
            for (ServerPlayer listener : vision.sight.level().players())
                if (listener != player && !listener.isSpectator() && listener.getEyePosition().distanceToSqr(mouth) < range * range)
                    routes.add(new Voice(listener.getUUID(), mouth, range));
            if (!routes.isEmpty()) next.put(player.getUUID(), List.copyOf(routes));
        }
        voices = next.isEmpty() ? Map.of() : Map.copyOf(next);
    }

    /** Who hears this speaker through their hologram (read on the voice thread). */
    public static List<Voice> voices(UUID speaker) { return voices.getOrDefault(speaker, List.of()); }

    // --- Afterwards ---

    /** Where the last vision was, for a held Breach: within {@link #SPOT_TICKS} of it ending, else null. */
    public static @Nullable VibeFrequencies.Frequency spot(ServerPlayer player) {
        Spot spot = SPOTS.get(player.getUUID());
        return spot == null || spot.until() < player.serverLevel().getGameTime() ? null : spot.frequency();
    }

    /** The double was struck: the viber is torn back into their body, and the blow lands on them. True if it was a double. */
    public static boolean struck(Entity entity, DamageSource source, float amount) {
        if (!entity.getTags().contains(BODY_TAG)) return false;
        for (Vision vision : List.copyOf(VISIONS.values())) {
            if (vision.body != entity) continue;
            end(vision);
            vision.player.displayClientMessage(Component.translatable("vibe.heroesevolved.vision.torn"), true);
            vision.player.hurt(source, amount);
            return true;
        }
        return true;
    }

    /** A double whose vision is over (a restart, an unloaded chunk) must never be left standing. */
    public static boolean orphan(Entity entity) {
        if (spawning || !entity.getTags().contains(BODY_TAG)) return false;
        for (Vision vision : VISIONS.values()) if (vision.body == entity) return false;
        return true;
    }

    // --- Saved, so a crash never leaves anyone a spectator ---

    private static void save(Vision vision) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", vision.home.dimension().location().toString());
        tag.putDouble("x", vision.at.x);
        tag.putDouble("y", vision.at.y);
        tag.putDouble("z", vision.at.z);
        tag.putFloat("yaw", vision.yaw);
        tag.putFloat("pitch", vision.pitch);
        tag.putInt("mode", vision.mode.getId());
        CompoundTag persisted = vision.player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(SAVE, tag);
        vision.player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void clearSave(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(SAVE)) return;
        persisted.remove(SAVE);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Logging in still marked as in a vision (the server stopped mid-vision): back to the body, as they were. */
    public static void login(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(SAVE) || VISIONS.containsKey(player.getUUID())) return;
        CompoundTag tag = persisted.getCompound(SAVE);
        clearSave(player);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ServerLevel level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) level = player.serverLevel();
        if (player.isSpectator()) player.setGameMode(GameType.byId(tag.getInt("mode")));
        double x = tag.getDouble("x"), y = tag.getDouble("y"), z = tag.getDouble("z");
        player.teleportTo(level, x, y, z, tag.getFloat("yaw"), tag.getFloat("pitch"));
        if (player.level() == level) player.absMoveTo(x, y, z, tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    public static void forget(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        if (vision != null) end(vision);
        SPOTS.remove(player.getUUID());
    }

    static void clear() {
        for (Vision vision : List.copyOf(VISIONS.values())) end(vision);
        VISIONS.clear();
        SPOTS.clear();
        TOUCHED.clear();
        FORESEEN.clear();
        voices = Map.of();
    }

    // --- For tests ---

    static boolean inside(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        return vision != null && vision.inside;
    }

    static @Nullable Kind kind(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        return vision == null ? null : vision.sight.kind();
    }

    static @Nullable StoryActorEntity body(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        return vision == null ? null : vision.body;
    }

    static boolean projecting(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        return vision != null && vision.hologram;
    }

    /** Ends a vision now (tests), as if its time were up. */
    static void wake(ServerPlayer player) {
        Vision vision = VISIONS.get(player.getUUID());
        if (vision != null) end(vision);
    }
}
