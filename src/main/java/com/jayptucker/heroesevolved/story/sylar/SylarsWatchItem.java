package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.time.RewindTravel;
import com.jayptucker.heroesevolved.time.TimeRewindPlayers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Sylar's Watch, what he leaves behind when he falls. Once a life it takes you back five seconds: where you stood and
 * how hurt you were. While you carry it, it keeps the last five seconds of you (every other tick); using it sends you
 * back along that path the way Rewrite the Moment does, then it won't wind again until you have died.
 */
public final class SylarsWatchItem extends Item {
    public static final int REWIND_TICKS = 100, STEP = 2, TRAVEL_TICKS = 20;
    /** The death count (plus one) when the watch was last used. Once per life: a new death winds it again. */
    public static final String USED = "heroesevolved_sylars_watch_life";

    record Moment(long time, ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch, float health, int fire) {}

    private static final Map<UUID, Deque<Moment>> TRACKS = new HashMap<>();

    public SylarsWatchItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer user)) return InteractionResultHolder.sidedSuccess(stack, true);
        return rewind(user) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    private static int deaths(ServerPlayer player) { return player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)); }

    /** Wound for this life (not used since the player last died). */
    public static boolean wound(ServerPlayer player) { return player.getPersistentData().getInt(USED) != deaths(player) + 1; }

    /** Takes {@code player} back up to five seconds. False (with a line saying why) when it can't. */
    public static boolean rewind(ServerPlayer player) {
        if (!wound(player)) { tell(player, "item.heroesevolved.sylars_watch.spent"); return false; }
        long now = player.getServer().overworld().getGameTime();
        Deque<Moment> track = TRACKS.get(player.getUUID());
        // Newest first, in this world only, as far back as five seconds.
        List<Moment> past = new ArrayList<>();
        if (track != null) for (Iterator<Moment> it = track.descendingIterator(); it.hasNext(); ) {
            Moment moment = it.next();
            if (!moment.dimension().equals(player.level().dimension()) || now - moment.time() > REWIND_TICKS) break;
            past.add(moment);
        }
        if (past.size() < 2 || now - past.getLast().time() < STEP * 2) { tell(player, "item.heroesevolved.sylars_watch.empty"); return false; }
        player.getPersistentData().putInt(USED, deaths(player) + 1);
        Moment then = past.getLast();
        TRACKS.remove(player.getUUID());
        List<TimeRewindPlayers.Point> path = past.stream()
                .map(m -> new TimeRewindPlayers.Point(m.time(), m.dimension(), m.at(), m.yaw(), m.pitch())).toList();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(),
                SoundSource.PLAYERS, 1.0F, 1.6F);
        RewindTravel.send(player, path, TRAVEL_TICKS, back -> {
            if (back == null || !back.isAlive()) return;
            if (back.level().dimension().equals(then.dimension()) && back.position().distanceToSqr(then.at()) > 0.01)
                back.teleportTo(back.serverLevel(), then.at().x, then.at().y, then.at().z, then.yaw(), then.pitch());
            back.setHealth(Math.max(1, Math.min(back.getMaxHealth(), then.health())));
            back.setRemainingFireTicks(then.fire());
            back.fallDistance = 0;
            back.setDeltaMovement(Vec3.ZERO);
            AbilityEffects.send(back.serverLevel(), Kind.TIME_REWIND_ARRIVE, back.position(), back.getEyePosition(), 1.0);
            back.serverLevel().playSound(null, back.getX(), back.getY(), back.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
            tell(back, "item.heroesevolved.sylars_watch.used");
        });
        return true;
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
    }

    /** Keeps five seconds of every player carrying a watch. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % STEP != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || !player.getInventory().hasAnyMatching(stack -> stack.is(ModItems.SYLARS_WATCH.get()))) {
                TRACKS.remove(player.getUUID()); continue;
            }
            record(player, now);
        }
        TRACKS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Remembers where {@code player} is now (for the watch; tests call it directly). */
    public static void record(ServerPlayer player, long now) {
        Deque<Moment> track = TRACKS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (!track.isEmpty() && track.getLast().time() >= now) track.removeLast();
        track.addLast(new Moment(now, player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                player.getHealth(), player.getRemainingFireTicks()));
        while (!track.isEmpty() && now - track.getFirst().time() > REWIND_TICKS + STEP) track.removeFirst();
    }

    public static void forget(UUID player) { TRACKS.remove(player); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.heroesevolved.sylars_watch.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroesevolved.sylars_watch.tooltip2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

}
