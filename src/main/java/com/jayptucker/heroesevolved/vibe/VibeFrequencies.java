package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.FrequencyChoicePayload;
import com.jayptucker.heroesevolved.network.FrequencyListPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A viber's frequencies: places they have tuned (breach waypoints), each a world, a spot and a facing. Breach opens a
 * link to any of them from the frequency wheel; Glimpse opens a window to one. A viber holds one at Power Level 1 and
 * one more every four levels (six at Level 20), and World Frequency adds two and reaches other dimensions.
 * Saved in the player's persisted data, so they survive death.
 */
public final class VibeFrequencies {
    static final String KEY = "heroesevolved_vibe_frequencies", OLD_ANCHOR = "heroesevolved_vibe_anchor";
    public static final int MOST = 8, LEVEL_STEP = 4, WORLD_BONUS = 2;
    /** Breach costs a little more stamina the farther it reaches: one per {@code BLOCKS_PER_STAMINA}, capped. */
    static final int BLOCKS_PER_STAMINA = 40, MOST_TRAVEL = 30, WORLD_TRAVEL = 15;
    private static final int LOAD_TICKS = 200;

    public record Frequency(String name, String biome, ResourceLocation dimension, Vec3 feet, float yaw) {
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("name", name);
            tag.putString("biome", biome);
            tag.putString("dimension", dimension.toString());
            tag.putDouble("x", feet.x);
            tag.putDouble("y", feet.y);
            tag.putDouble("z", feet.z);
            tag.putFloat("yaw", yaw);
            return tag;
        }

        static @Nullable Frequency load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            Vec3 feet = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            float yaw = tag.getFloat("yaw");
            if (dimension == null || !Double.isFinite(feet.lengthSqr()) || !Float.isFinite(yaw)) return null;
            return new Frequency(tag.getString("name"), tag.getString("biome"), dimension, feet, yaw);
        }

        Frequency named(String to) { return new Frequency(to, biome, dimension, feet, yaw); }
    }

    /** A pick from the wheel, waiting for the press it starts (in the same tick) to pay for it. */
    private record Pick(int slot, boolean glimpse, long at) {}

    private record Pending(ServerPlayer player, ServerLevel origin, ServerLevel target, Frequency frequency,
                           int slot, boolean glimpse, long expires) {
        void ticket(boolean open) {
            VibeBreaches.ticket(target, new ChunkPos(BlockPos.containing(frequency.feet())), player.getUUID(), open);
        }
    }

    private static final Map<UUID, Pick> PICKS = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Which wheel each viber last opened, so marking or renaming reopens the same one. */
    private static final Map<UUID, Integer> MODES = new HashMap<>();

    private VibeFrequencies() {}

    static boolean worlds(ServerPlayer player) { return SkillTreeService.has(player, "vibe_world_frequency"); }

    /** How many frequencies this viber can hold now. */
    public static int slots(ServerPlayer player) {
        return slots(PlayerProgressionService.getPowerLevel(player), worlds(player));
    }

    public static int slots(int powerLevel, boolean worlds) {
        return Math.min(MOST, 1 + Math.max(0, powerLevel) / LEVEL_STEP + (worlds ? WORLD_BONUS : 0));
    }

    /** The Power Level that adds the next slot, or 0 once no level adds one. */
    static int nextLevel(int powerLevel) {
        int next = (Math.max(0, powerLevel) / LEVEL_STEP + 1) * LEVEL_STEP;
        return next > PowerLevels.MAX ? 0 : next;
    }

    /** Every slot up to {@link #MOST}; empty slots are null. Moves the old single Attune Frequency anchor into slot 1. */
    public static Frequency[] all(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        Frequency[] out = new Frequency[MOST];
        ListTag list = persisted.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getInt("slot");
            if (slot >= 0 && slot < MOST && tag.contains("dimension")) out[slot] = Frequency.load(tag);
        }
        if (persisted.contains(OLD_ANCHOR)) {
            Frequency old = Frequency.load(persisted.getCompound(OLD_ANCHOR));
            if (old != null && out[0] == null) out[0] = old;
            persisted.remove(OLD_ANCHOR);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            save(player, out);
        }
        return out;
    }

    public static @Nullable Frequency get(ServerPlayer player, int slot) {
        return slot < 0 || slot >= MOST ? null : all(player)[slot];
    }

    private static void save(ServerPlayer player, Frequency[] slots) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = new ListTag();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            CompoundTag tag = slots[i].save();
            tag.putInt("slot", i);
            list.add(tag);
        }
        persisted.put(KEY, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Tunes the spot where the viber stands into {@code slot}. */
    public static boolean mark(ServerPlayer player, int slot) {
        if (!VibeService.nativePower(player) || !VibeService.active(player)) return false;
        if (slot < 0 || slot >= slots(player)) return VibeBreaches.fail(player, "slot_locked");
        VibeBreaches.Mouth mouth = VibeBreaches.markable(player);
        if (mouth == null) return false;
        Frequency[] all = all(player);
        BlockPos at = player.blockPosition();
        String biome = player.serverLevel().getBiome(at).unwrapKey()
                .map(key -> "biome." + key.location().getNamespace() + "." + key.location().getPath()).orElse("");
        String name = all[slot] == null ? "" : all[slot].name();
        all[slot] = new Frequency(name, biome, mouth.level().dimension().location(), mouth.feet(), player.getYRot());
        save(player, all);
        ArmPosePayload.show(player, ArmPose.VIBE_READ, 18);
        VibeGeometry.privatePiece(player, Piece.VIBE_BREACH_ARRIVE, mouth.center(), mouth.center().add(mouth.direction()), 1, -1, 0);
        StyleOps.sound(mouth.level(), mouth.center(), SoundEvents.LODESTONE_COMPASS_LOCK, 1, 1.4F);
        player.displayClientMessage(Component.translatable("vibe.heroesevolved.attuned", slot + 1,
                at.getX(), at.getY(), at.getZ()), true);
        return true;
    }

    public static boolean forget(ServerPlayer player, int slot) {
        Frequency[] all = all(player);
        if (slot < 0 || slot >= MOST || all[slot] == null) return false;
        all[slot] = null;
        save(player, all);
        return true;
    }

    public static boolean rename(ServerPlayer player, int slot, String name) {
        Frequency[] all = all(player);
        if (slot < 0 || slot >= MOST || all[slot] == null) return false;
        String clean = name.replaceAll("[\\p{Cntrl}§]", "").strip();
        all[slot] = all[slot].named(clean.length() > 32 ? clean.substring(0, 32) : clean);
        save(player, all);
        return true;
    }

    /** The world a frequency is in, if it exists and lets a breach in. */
    static @Nullable ServerLevel world(ServerPlayer player, Frequency frequency) {
        ServerLevel world = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, frequency.dimension()));
        return world == null || !VibeBreaches.travelWorld(world) ? null : world;
    }

    /** Why a breach can't reach this frequency now, or null if it can. */
    static @Nullable String refusal(ServerPlayer player, @Nullable Frequency frequency) {
        if (frequency == null) return "no_frequency";
        ServerLevel world = world(player, frequency);
        if (world == null) return "restricted";
        if (world != player.serverLevel() && !worlds(player)) return "other_world";
        BlockPos feet = BlockPos.containing(frequency.feet());
        if (!world.getWorldBorder().isWithinBounds(feet) || feet.getY() < world.getMinBuildHeight()
                || feet.getY() + 3 >= world.getMaxBuildHeight()) return "no_exit";
        if (world == player.serverLevel() && frequency.feet().distanceToSqr(player.position()) < 25) return "at_frequency";
        return null;
    }

    /** Extra stamina for a breach to {@code slot}: one per 40 blocks (up to 30), or 15 to another world. */
    static int travel(ServerPlayer player, Frequency frequency) {
        ServerLevel world = world(player, frequency);
        if (world == null) return 0;
        if (world != player.serverLevel()) return WORLD_TRAVEL;
        return Math.min(MOST_TRAVEL, (int) (Math.sqrt(frequency.feet().distanceToSqr(player.position())) / BLOCKS_PER_STAMINA));
    }

    // --- The wheel ---

    /** Opens the frequency wheel: Breach's plain press, or Glimpse. Only a native viber has frequencies to show. */
    public static boolean wheel(ServerPlayer player, int mode) {
        if (!VibeService.nativePower(player) || !VibeService.ready(player)) return false;
        MODES.put(player.getUUID(), mode);
        Frequency[] all = all(player);
        List<FrequencyListPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < MOST; i++) {
            Frequency frequency = all[i];
            if (frequency == null) continue;
            ServerLevel world = world(player, frequency);
            boolean here = world == player.serverLevel();
            int distance = here ? (int) Math.sqrt(frequency.feet().distanceToSqr(player.position())) : -1;
            entries.add(new FrequencyListPayload.Entry(i, frequency.name(), frequency.biome(), frequency.dimension().toString(),
                    Mth.floor(frequency.feet().x), Mth.floor(frequency.feet().y), Mth.floor(frequency.feet().z), distance,
                    refusal(player, frequency) == null));
        }
        int level = PlayerProgressionService.getPowerLevel(player);
        PacketDistributor.sendToPlayer(player, new FrequencyListPayload(mode, entries, slots(player),
                slots(PowerLevels.MAX, true), nextLevel(level), level, worlds(player), VibeBreaches.aimRange(player)));
        ArmPosePayload.show(player, ArmPose.VIBE_READ, 10);
        return true;
    }

    /** A click on the wheel. Breaches and glimpses go through the normal press, so they pay and cool down as one. */
    public static void choose(ServerPlayer player, int op, int slot, String name) {
        if (!VibeService.nativePower(player) || !VibeService.ready(player)) return;
        if (!(op == FrequencyChoicePayload.BREACH && slot == -1) && (slot < 0 || slot >= slots(player))) {
            VibeBreaches.fail(player, "slot_locked");
            return;
        }
        switch (op) {
            case FrequencyChoicePayload.BREACH, FrequencyChoicePayload.GLIMPSE ->
                    chooseDestination(player, slot, op == FrequencyChoicePayload.GLIMPSE);
            case FrequencyChoicePayload.MARK -> { if (mark(player, slot)) wheel(player, MODES.getOrDefault(player.getUUID(), 0)); }
            case FrequencyChoicePayload.FORGET -> { if (forget(player, slot)) wheel(player, MODES.getOrDefault(player.getUUID(), 0)); }
            case FrequencyChoicePayload.RENAME -> { if (rename(player, slot, name)) wheel(player, MODES.getOrDefault(player.getUUID(), 0)); }
            default -> { }
        }
    }

    private static void chooseDestination(ServerPlayer player, int slot, boolean glimpse) {
        if (VibeBreaches.isOpen(player)) {
            VibeBreaches.fail(player, "already_open");
            return;
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending != null && pending.slot() == slot && pending.glimpse() == glimpse
                && pending.frequency().equals(get(player, slot))) return;
        cancelPending(player);
        activatePick(player, slot, glimpse);
    }

    static void prepare(ServerPlayer player, ServerLevel target, Frequency frequency, int slot, boolean glimpse) {
        Pending pending = new Pending(player, player.serverLevel(), target, frequency, slot, glimpse,
                player.server.overworld().getGameTime() + LOAD_TICKS);
        PENDING.put(player.getUUID(), pending);
        pending.ticket(true);
        player.displayClientMessage(Component.translatable("vibe.heroesevolved.loading_frequency"), true);
    }

    static boolean loaded(ServerLevel level, Frequency frequency) {
        BlockPos base = BlockPos.containing(frequency.feet());
        return level.hasChunksAt(base.offset(-5, -2, -5), base.offset(5, 4, 5));
    }

    private static void activatePick(ServerPlayer player, int slot, boolean glimpse) {
        PICKS.put(player.getUUID(), new Pick(slot, glimpse, player.serverLevel().getGameTime()));
        try {
            PowerFeedback.report(player, AbilityUseService.activateAction(player,
                    glimpse ? "vibe_1_1" : Loadout.base(AbilitySlot.SECONDARY), false));
        } finally {
            PICKS.remove(player.getUUID());
        }
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Pending pending : List.copyOf(PENDING.values())) {
            ServerPlayer player = pending.player();
            if (player.server != server) continue;
            if (player.serverLevel() != pending.origin() || !VibeService.ready(player)) {
                cancelPending(player);
            } else if (pending.slot() >= slots(player) || !pending.frequency().equals(get(player, pending.slot()))
                    || now >= pending.expires()) {
                cancelPending(player);
                VibeBreaches.fail(player, "no_exit");
            } else if (loaded(pending.target(), pending.frequency())) {
                cancelPending(player);
                activatePick(player, pending.slot(), pending.glimpse());
            }
        }
    }

    static boolean cancelPending(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) return false;
        pending.ticket(false);
        return true;
    }

    /** The wheel's pick this press is for: a slot, or -1 for where the viber looks. Null for a press from the key. */
    static @Nullable Integer picked(ServerPlayer player, boolean glimpse) {
        Pick pick = PICKS.get(player.getUUID());
        if (pick == null || pick.glimpse() != glimpse || pick.at() != player.serverLevel().getGameTime()) return null;
        return pick.slot();
    }

    static void forget(ServerPlayer player) {
        PICKS.remove(player.getUUID());
        cancelPending(player);
        MODES.remove(player.getUUID());
    }

    static void clear() {
        PICKS.clear();
        PENDING.values().forEach(pending -> pending.ticket(false));
        PENDING.clear();
        MODES.clear();
    }
}
