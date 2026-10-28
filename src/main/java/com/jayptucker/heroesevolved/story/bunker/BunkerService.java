package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.PowerIntroPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The opening of the story: a new player wakes sealed in a Cryochamber in the Odessa stasis
 * bunker as an alarm sounds, and is released to discover what happened to the world.
 * Players who were already exploring before the bunker existed are told where it is instead.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class BunkerService {
    /** Ticks after login before the sequence starts, so the client has finished loading the world. */
    private static final int START_DELAY = 10, RELEASE = 110, END = 160;
    /** Anyone who has played this long already found their own way into the world. */
    private static final int VETERAN_PLAY_TICKS = 20 * 120;
    private static final Map<UUID, Integer> SESSIONS = new HashMap<>();
    private static final Set<UUID> HEARD_TICKING = new HashSet<>();

    private BunkerService() {}

    public static boolean isAwakening(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    /** Builds the bunker the first time it is needed. */
    public static BunkerData ensureBuilt(MinecraftServer server) {
        BunkerData data = BunkerData.get(server);
        if (!data.built) {
            data.origin = BunkerBuilder.build(server.overworld());
            data.built = true;
            data.layout = BunkerData.LAYOUT;
            data.setDirty();
            HeroesEvolved.LOGGER.info("Built the stasis bunker at {}", data.origin);
        }
        return data;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.getData(ModDataAttachments.BUNKER_AWOKEN)
                && com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.bunkerEnabled.get())
            SESSIONS.put(player.getUUID(), -START_DELAY);
    }

    /** Starts the awakening for {@code player} now. Returns false if they had already awoken. */
    public static boolean begin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        BunkerData data = ensureBuilt(server);
        ServerLevel overworld = server.overworld();
        int index = data.pod(player.getUUID(), BunkerBuilder.PODS);
        BlockPos base = data.origin.offset(BunkerBuilder.podOffset(index));
        if (!overworld.getBlockState(base).is(ModBlocks.CRYOCHAMBER.get())) BunkerBuilder.placePod(overworld, data.origin, index, true);
        if (player.level() != overworld) player.teleportTo(overworld, base.getX() + .5, base.getY(), base.getZ() + .5, 0, 0);
        CryoChamberService.enter(player, base, RELEASE + 5);
        SESSIONS.put(player.getUUID(), 0);
        return true;
    }

    private static boolean veteran(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > VETERAN_PLAY_TICKS
                || PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked()).isPresent();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) { SESSIONS.remove(id); continue; }
            int age = SESSIONS.merge(id, 1, Integer::sum);
            if (age == 0) {
                if (veteran(player)) {
                    SESSIONS.remove(id);
                    BunkerData data = ensureBuilt(server);
                    player.setData(ModDataAttachments.BUNKER_AWOKEN, true);
                    player.sendSystemMessage(Component.translatable("bunker.heroesevolved.rumor",
                            data.origin.getX(), data.origin.getZ()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    continue;
                }
                begin(player);
                SESSIONS.put(id, 1);
                age = 1;
            }
            if (age > 0) step(player, age);
        }
        if (server.overworld().getGameTime() % 20 == 0 && BunkerData.get(server).built) {
            BlockPos origin = BunkerData.get(server).origin;
            for (ServerPlayer player : server.overworld().players())
                if (BunkerBuilder.inCell(origin, player.blockPosition()) && HEARD_TICKING.add(player.getUUID()))
                    player.playNotifySound(ModSounds.SYLAR_WATCH.get(), SoundSource.AMBIENT, 0.5F, 1);
        }
    }

    private static void step(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (age == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false));
            ScreenTintPayload.send(player, 0xFF000000, 50, ScreenTintPayload.Style.HOLD);
            caption(player, "bunker_1");
        }
        if (age == 12) {
            player.playNotifySound(ModSounds.NUCLEAR_SIREN.get(), SoundSource.AMBIENT, 0.3F, 0.9F);
            ScreenTintPayload.send(player, 0x55FF1010, RELEASE - 10, ScreenTintPayload.Style.PULSE);
        }
        if (age >= 12 && age < RELEASE && age % 10 == 2)
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.2F, age % 20 == 2 ? 0.7F : 1.0F);
        if (age == 60) caption(player, "bunker_2");
        if (age == RELEASE) {
            CryoChamberService.release(player);
            player.setData(ModDataAttachments.BUNKER_AWOKEN, true);
            caption(player, "bunker_3");
            award(player);
        }
        if (age >= END) {
            SESSIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("bunker.heroesevolved.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static void caption(ServerPlayer player, String stage) {
        PacketDistributor.sendToPlayer(player, new PowerIntroPayload(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, stage), 0));
    }

    private static void award(ServerPlayer player) {
        AdvancementHolder advancement = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wake_up"));
        if (advancement != null) player.getAdvancements().award(advancement, "granted");
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
        HEARD_TICKING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { SESSIONS.clear(); HEARD_TICKING.clear(); }
}
