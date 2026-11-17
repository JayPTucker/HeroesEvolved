package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TemporalSharingGameTests {
    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        Scene(GameTestHelper h) { this.h = h; }
        ServerPlayer player(ResourceLocation power, int tier, double x) {
            var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "time-guest"));
            player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))).add(x, 0, 0));
            if (power != null) player.setData(ModDataAttachments.PLAYER_ABILITIES,
                    PlayerAbilityData.empty().assignDormant(power).unlock(power));
            PlayerProgressionService.setLevel(player, tier);
            PlayerList list = h.getLevel().getServer().getPlayerList();
            TemporalSharingGameTests.<List<ServerPlayer>>field(list, "players").add(player);
            TemporalSharingGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
            players.add(player);
            return player;
        }
        @Override public void close() {
            for (var player : players) {
                TimeStopService.stop(player); TimeSlowService.stop(player); AdvancedTechniques.endPerception(player);
                TimeVeil.forget(player);
            }
            PlayerList list = h.getLevel().getServer().getPlayerList();
            for (var player : players) {
                TemporalSharingGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
                TemporalSharingGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
            }
            TemporalVoiceState.publish(h.getLevel().getServer());
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void rightClickSharesMovementSightAndVoiceUntilStopEnds(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var guest = scene.player(null, 1, 2);
            var other = scene.player(null, 1, -2);
            TimeStopService.start(owner, 5);
            h.assertTrue(TimeStopService.isStopped(guest) && TemporalVoiceState.frozen(guest.getUUID()), "Initially frozen and silent");
            var click = new PlayerInteractEvent.EntityInteract(owner, InteractionHand.MAIN_HAND, guest);
            NeoForge.EVENT_BUS.post(click);
            h.assertTrue(click.isCanceled() && TemporalSharing.shared(guest), "A real right-click event brings the guest in");
            h.assertTrue(!TimeStopService.isStopped(guest) && TimeStopService.isStopped(other), "Only the touched guest moves");
            h.assertTrue(TimeVeil.seesThrough(guest, owner.getUUID()) && !TimeVeil.seesThrough(other, guest.getUUID()),
                    "The guest sees the caster, and gets their own frozen stand-in for bystanders");
            h.assertTrue(!TemporalVoiceState.blocks(owner.getUUID(), guest.getUUID())
                    && !TemporalVoiceState.blocks(guest.getUUID(), owner.getUUID()), "They hear each other in both directions");
            h.assertTrue(TemporalVoiceState.blocks(guest.getUUID(), other.getUUID())
                    && TemporalVoiceState.blocks(other.getUUID(), owner.getUUID()), "Neither live speech direction leaks to frozen time");
            TimeStopService.stop(owner);
            h.assertTrue(!TemporalSharing.shared(guest) && !TimeVeil.veiled(guest) && !TimeStopService.isStopped(other), "All sharing ends with the field");
            h.assertTrue(!TemporalVoiceState.blocks(guest.getUUID(), other.getUUID()), "Ordinary voice resumes");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void slowTimeGuestsRegainFullMovementAndLocalRangeIsRespected(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var guest = scene.player(null, 1, 2);
            var far = scene.player(null, 1, 100);
            TimeSlowService.start(owner, 5);
            PlayerTimeSlow.update(guest); PlayerTimeSlow.update(far);
            h.assertTrue(PlayerTimeSlow.movementScale(guest) < 1, "The guest begins slowed");
            h.assertTrue(PlayerTimeSlow.movementScale(far) == 1 && !TemporalVoiceState.blocks(far.getUUID(), far.getUUID()),
                    "Local time doesn't change distant movement or voice");
            h.assertTrue(TemporalSharing.include(owner, guest) && PlayerTimeSlow.movementScale(guest) == 1, "Touch restores full movement immediately");
            TimeSlowService.stop(owner);
            h.assertTrue(!TemporalSharing.shared(guest) && PlayerTimeSlow.movementScale(guest) == 1, "No stale attributes or guest after release");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void speedsterSharingRequiresLevelTenAndAnActivePerception(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var speedster = scene.player(ModAbilities.SUPERSPEED_ID, 3, 0);
            var guest = scene.player(null, 1, 2);
            h.assertTrue(!TemporalSharing.include(speedster, guest), "A key activation is required first");
            AdvancedTechniques.startPerception(speedster);
            h.assertTrue(!TemporalSharing.include(speedster, guest), "Tier three can't share Perception");
            PlayerProgressionService.setLevel(speedster, 4);
            h.assertTrue(TemporalSharing.include(speedster, guest), "Power Level ten can share it");
            PlayerTimeSlow.update(guest);
            h.assertTrue(PlayerTimeSlow.movementScale(guest) == 1 && !TemporalVoiceState.blocks(guest.getUUID(), speedster.getUUID()),
                    "A speedster's guest moves and speaks at their pace");
            AdvancedTechniques.endPerception(speedster);
            h.assertTrue(!TemporalSharing.shared(guest) && !TimeVeil.veiled(guest), "Perception ending removes its guests");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void guestsCannotBeAddedAtRangeOrThroughWalls(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var guest = scene.player(null, 1, 5);
            TimeSlowService.start(owner, 5);
            h.assertTrue(!TemporalSharing.include(owner, guest), "No remote sharing");
            guest.setPos(owner.position().add(2, 0, 0));
            BlockPos wall = owner.blockPosition().offset(1, 0, 0);
            for (int y = 0; y < 3; y++) h.getLevel().setBlockAndUpdate(wall.above(y), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            h.assertTrue(!TemporalSharing.include(owner, guest), "A wall blocks the touch");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void losingThePowerReleasesGuestsAndOverlappingInvitationsStayIndependent(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var first = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var second = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 1);
            var guest = scene.player(null, 1, 2);
            TimeSlowService.start(first, 5); TimeSlowService.start(second, 5);
            h.assertTrue(TemporalSharing.include(first, guest) && TemporalSharing.include(second, guest), "Two travellers may invite the same guest");
            TimeSlowService.stop(first);
            h.assertTrue(TemporalSharing.shared(guest) && TimeVeil.veiled(guest) && PlayerTimeSlow.movementScale(guest) == 1, "One release preserves the other invitation");
            second.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
            TimeSlowService.tick(second.getServer()); TemporalSharing.tick(second.getServer());
            h.assertTrue(!TemporalSharing.shared(guest) && !TimeVeil.veiled(guest), "Power loss releases the last invitation");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void fadingVoiceDropsPitchReverberatesAndEndsInSilence(GameTestHelper h) {
        short[] tone = new short[TemporalVoice.HISTORY];
        for (int i = 0; i < tone.length; i++) tone[i] = (short) (12_000 * Math.sin(i * 2 * Math.PI * 440 / 48_000));
        short[] tail = TemporalVoice.tail(tone);
        int crossings = 0;
        for (int i = 1; i < 4_800; i++) if (tail[i] >= 0 && tail[i - 1] < 0) crossings++;
        h.assertTrue(crossings < 38 && crossings > 15, "The first tenth-second has a clearly lower pitch than the 440 Hz source");
        long early = 0, late = 0;
        for (int i = 0; i < 4_800; i++) { early += Math.abs(tail[i]); late += Math.abs(tail[96_000 + i]); }
        h.assertTrue(late > 0 && late < early / 4, "Reverb continues after the stretched speech, then fades");
        h.assertTrue(tail.length == 144_000 && tail[tail.length - 1] == 0, "Exactly three seconds, ending in silence");
        for (short sample : TemporalVoice.tail(new short[TemporalVoice.HISTORY])) h.assertTrue(sample == 0, "Silence creates no voice");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "time.sharing")
    public static void voicePluginGatesMicrophonesAndAllThreeOutgoingPacketKinds(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var guest = scene.player(null, 1, 2);
            TimeSlowService.start(owner, 5);
            var callbacks = new java.util.HashMap<Class<?>, java.util.TreeMap<Integer, java.util.function.Consumer<?>>>();
            new com.jayptucker.heroesevolved.telepathy.MindVoice().registerEvents(new de.maxhenkel.voicechat.api.events.EventRegistration() {
                @Override public <T extends de.maxhenkel.voicechat.api.events.Event> void registerEvent(Class<T> type, java.util.function.Consumer<T> callback, int priority) {
                    callbacks.computeIfAbsent(type, k -> new java.util.TreeMap<>(java.util.Comparator.reverseOrder())).put(priority, callback);
                }
            });
            var microphone = callbacks.get(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class);
            h.assertTrue(microphone.firstKey() > 0, "Frozen microphones are cancelled before Simple Radio's priority-zero callback");
            boolean[] cancelled = { false };
            var sender = connection(guest.getUUID());
            var mic = proxy(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, (method, args) -> switch (method) {
                case "getSenderConnection" -> sender;
                case "cancel" -> { cancelled[0] = true; yield null; }
                default -> null;
            });
            accept(microphone.firstEntry().getValue(), mic);
            h.assertTrue(cancelled[0], "A frozen microphone cannot feed any relay");
            for (Class<?> type : List.of(de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class,
                    de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent.class, de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent.class)) {
                cancelled[0] = false;
                Class<?> packetType = type == de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class ? de.maxhenkel.voicechat.api.packets.EntitySoundPacket.class
                        : type == de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent.class ? de.maxhenkel.voicechat.api.packets.LocationalSoundPacket.class
                        : de.maxhenkel.voicechat.api.packets.StaticSoundPacket.class;
                Object packet = proxy(packetType, (method, args) -> switch (method) {
                    case "getSender" -> owner.getUUID();
                    case "getOpusEncodedData" -> new byte[0];
                    default -> null;
                });
                Object event = proxy(type, (method, args) -> switch (method) {
                    case "getPacket" -> packet;
                    case "getReceiverConnection" -> connection(guest.getUUID());
                    case "isCancelled" -> cancelled[0];
                    case "cancel" -> { cancelled[0] = true; yield null; }
                    default -> null;
                });
                accept(callbacks.get(type).firstEntry().getValue(), event);
                h.assertTrue(cancelled[0], "Outgoing " + type.getSimpleName() + " cannot leak live speech");
            }
            h.assertTrue(TemporalVoiceState.blocks(UUID.randomUUID(), guest.getUUID())
                    && !TemporalVoiceState.blocks(UUID.randomUUID(), owner.getUUID()), "Radio output reaches only perceivers in changed time");
            TemporalSharing.include(owner, guest); cancelled[0] = false;
            accept(microphone.firstEntry().getValue(), mic);
            h.assertTrue(!cancelled[0], "A shared guest's microphone becomes audible immediately");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.mob_memory")
    public static void mobsAimAtTheRememberedBodyAndResumeTrackingAfterward(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var mob = net.minecraft.world.entity.EntityType.PIGLIN.create(h.getLevel());
            mob.setPos(owner.position().add(0, 0, 5));
            mob.setTarget(owner);
            mob.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, owner);
            TimeSlowService.start(owner, 5);
            var shadow = mob.getTarget();
            h.assertTrue(shadow != owner && shadow.position().equals(owner.position()), "AI sees a remembered body");
            var anchor = shadow.position(); owner.setPos(owner.position().add(4, 0, 0));
            h.assertTrue(mob.getTarget() == shadow && shadow.position().equals(anchor), "Moving never updates the remembered location");
            h.assertTrue(!mob.hasLineOfSight(owner) && !mob.canAttack(owner), "Sight and target acquisition cannot track the hidden player");
            mob.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, shadow);
            TimeSlowService.stop(owner); TemporalMobTime.brain(mob);
            h.assertTrue(mob.getTarget() == owner && mob.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).orElse(null) == owner,
                    "Both goal AI and brain memory resume tracking when time returns");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.mob_immunity")
    public static void matchingGiftedMobsKeepUpWhileOrdinaryMobsSlow(GameTestHelper h) {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var ordinary = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
            var gifted = net.minecraft.world.entity.EntityType.PILLAGER.create(h.getLevel()); // only villagers and illagers carry powers
            ordinary.setPos(owner.position().add(0, 0, 4)); gifted.setPos(ordinary.position());
            try {
                com.jayptucker.heroesevolved.gifted.GiftedMobs.bestow(gifted, ModAbilities.SUPERSPEED_ID,
                        com.jayptucker.heroesevolved.gifted.Rarity.RARE, true,
                        com.jayptucker.heroesevolved.gifted.Gift.Origin.ADMIN, null, "Fast watcher");
                ordinary.setTarget(owner); gifted.setTarget(owner);
                TimeSlowService.start(owner, 5);
                h.assertTrue(TemporalMobTime.interval(ordinary) > 1 && TemporalMobTime.interval(gifted) == 1, "Only the matching gift keeps its normal clock");
                h.assertTrue(gifted.getTarget() == owner && gifted.hasLineOfSight(owner), "The matching gift sees the real body");
                TimeStopService.start(owner, 5);
                h.assertTrue(TemporalMobTime.interval(ordinary) == 0 && !TimeStopService.shouldStopEntityTick(gifted), "Matching powers also escape the world stop");
                h.succeed();
            } finally { com.jayptucker.heroesevolved.gifted.GiftedMobs.cleanse(gifted); }
        }
    }

    @GameTest(template = "rewind_empty", batch = "time.mob_cast")
    public static void giftedWindupsAndRepeatedAttacksUseTheMobsClock(GameTestHelper h) throws Exception {
        try (var scene = new Scene(h)) {
            var owner = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            TimeSlowService.start(owner, 5);
            net.minecraft.world.entity.monster.Pillager caster;
            do {
                caster = net.minecraft.world.entity.EntityType.PILLAGER.create(h.getLevel());
                caster.setPos(owner.position().add(0, 0, 3));
            } while (TemporalMobTime.advances(caster));
            var victim = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(h.getLevel());
            victim.setPos(owner.position().add(0, 0, 5)); h.getLevel().addFreshEntity(victim);
            try {
                var g = com.jayptucker.heroesevolved.gifted.GiftedMobs.bestow(caster, ModAbilities.PYROKINESIS_ID,
                        com.jayptucker.heroesevolved.gifted.Rarity.RARE, true,
                        com.jayptucker.heroesevolved.gifted.Gift.Origin.ADMIN, null, "Slow caster");
                var move = com.jayptucker.heroesevolved.gifted.MobMoves.of(ModAbilities.PYROKINESIS_ID).stream().filter(m -> m.windup() >= 8).findFirst().orElseThrow();
                g.start(move, victim);
                var step = g.getClass().getDeclaredMethod("tick"); step.setAccessible(true);
                var age = g.getClass().getDeclaredField("castTicks"); age.setAccessible(true);
                step.invoke(g);
                h.assertTrue(age.getInt(g) == 0, "Skipped body ticks also skip cast pulses and repeat hits");
                TimeSlowService.stop(owner); step.invoke(g);
                h.assertTrue(age.getInt(g) == 1, "Normal time advances the windup once");
                TimeStopService.start(owner, 5);
                for (int i = 0; i < 10; i++) step.invoke(g);
                h.assertTrue(age.getInt(g) == 1, "Stopped time freezes the whole pending cast");
                h.succeed();
            } finally { com.jayptucker.heroesevolved.gifted.GiftedMobs.cleanse(caster); victim.discard(); }
        }
    }

    private static de.maxhenkel.voicechat.api.VoicechatConnection connection(UUID id) {
        var player = proxy(de.maxhenkel.voicechat.api.ServerPlayer.class, (method, args) -> method.equals("getUuid") ? id : null);
        return proxy(de.maxhenkel.voicechat.api.VoicechatConnection.class, (method, args) -> switch (method) {
            case "getPlayer" -> player;
            case "isConnected" -> true;
            default -> null;
        });
    }

    @GameTest(template = "rewind_empty", batch = "time.voice_audio", timeoutTicks = 100)
    public static void realOpusTailPlaysAfterPttStopsAndEncodesOnceForTwoListeners(GameTestHelper h) throws Exception {
        try (var scene = new Scene(h)) {
            var speaker = scene.player(ModAbilities.TIME_MANIPULATION_ID, 5, 0);
            var first = scene.player(null, 1, 2); var second = scene.player(null, 1, -2);
            var real = (de.maxhenkel.voicechat.api.VoicechatServerApi) Class.forName("de.maxhenkel.voicechat.plugins.impl.VoicechatServerApiImpl")
                    .getMethod("instance").invoke(null);
            Class<?> type = java.util.Arrays.stream(com.jayptucker.heroesevolved.telepathy.MindVoice.class.getDeclaredClasses())
                    .filter(candidate -> candidate.getSimpleName().equals("TemporalAudio")).findFirst().orElseThrow();
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true); Object audio = constructor.newInstance();
            var start = type.getDeclaredMethod("start", de.maxhenkel.voicechat.api.VoicechatServerApi.class); start.setAccessible(true);
            var tick = type.getDeclaredMethod("tick"); tick.setAccessible(true);
            var stop = type.getDeclaredMethod("stop"); stop.setAccessible(true);
            var outgoing = type.getDeclaredMethod("outgoing", de.maxhenkel.voicechat.api.events.SoundPacketEvent.class); outgoing.setAccessible(true);
            var receivers = Map.of(first.getUUID(), connection(first.getUUID()), second.getUUID(), connection(second.getUUID()));
            int[] encoded = { 0 }, sent = { 0 };
            var encoders = new ArrayList<de.maxhenkel.voicechat.api.opus.OpusEncoder>();
            UUID normalChannel = UUID.randomUUID();
            var api = proxy(de.maxhenkel.voicechat.api.VoicechatServerApi.class, (method, args) -> {
                switch (method) {
                    case "volumeCategoryBuilder": return real.volumeCategoryBuilder();
                    case "registerVolumeCategory":
                        // Use SVC's actual wire encoder: this catches category names that would disconnect a real client.
                        try {
                            Object category = args[0];
                            Class<?> packet = Class.forName("de.maxhenkel.voicechat.net.AddCategoryPacket");
                            Object registration = packet.getConstructor(category.getClass()).newInstance(category);
                            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                            try { packet.getMethod("toBytes", net.minecraft.network.FriendlyByteBuf.class).invoke(registration, buffer); }
                            finally { buffer.release(); }
                        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                        return null;
                    case "createDecoder": return real.createDecoder();
                    case "getConnectionOf": return receivers.get(args[0]);
                    case "createEncoder": {
                        var encoder = real.createEncoder((de.maxhenkel.voicechat.api.opus.OpusEncoderMode) args[0]); encoders.add(encoder);
                        return proxy(de.maxhenkel.voicechat.api.opus.OpusEncoder.class, (name, input) -> {
                            if (name.equals("encode")) { encoded[0]++; return encoder.encode((short[]) input[0]); }
                            if (name.equals("isClosed")) return encoder.isClosed();
                            if (name.equals("close")) encoder.close();
                            return null;
                        });
                    }
                    case "sendEntitySoundPacketTo": {
                        var packet = (de.maxhenkel.voicechat.api.packets.EntitySoundPacket) args[1];
                        h.assertTrue(!packet.getChannelId().equals(normalChannel) && packet.getOpusEncodedData().length > 0,
                                "The real Opus tail uses its own non-empty stream");
                        sent[0]++; return null;
                    }
                    default: return null;
                }
            });
            try {
                start.invoke(audio, api);
                var worker = type.getDeclaredField("worker"); worker.setAccessible(true);
                ((java.util.concurrent.ScheduledExecutorService) worker.get(audio)).shutdownNow();
                var source = real.createEncoder(de.maxhenkel.voicechat.api.opus.OpusEncoderMode.VOIP);
                try {
                    for (int frame = 0; frame < 12; frame++) {
                        short[] tone = new short[960];
                        for (int i = 0; i < tone.length; i++) tone[i] = (short) (10_000 * Math.sin((frame * 960 + i) * Math.PI * 880 / 48_000));
                        var packet = real.createPacket().entitySoundPacketBuilder().sender(speaker.getUUID()).entityUuid(speaker.getUUID())
                                .channelId(normalChannel).sequenceNumber(frame).distance(48).whispering(false).opusEncodedData(source.encode(tone)).build();
                        for (var receiver : receivers.values()) {
                            var event = proxy(de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, (method, args) -> switch (method) {
                                case "getPacket" -> packet;
                                case "getReceiverConnection" -> receiver;
                                default -> null;
                            });
                            outgoing.invoke(audio, event);
                        }
                    }
                } finally { source.close(); }
                // No microphone packets follow. The published time transition alone starts the tail.
                TimeSlowService.start(speaker, 5);
                for (int frame = 0; frame < 151; frame++) tick.invoke(audio);
                h.assertTrue(sent[0] == 300 && encoded[0] == 150, "150 frames reach each listener, encoded only once per frame");
                h.assertTrue(encoders.size() == 1 && encoders.getFirst().isClosed(), "The shared tail's encoder closes after its last listener");
                h.succeed();
            } finally { stop.invoke(audio); }
        }
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> call) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, args) -> {
                    Object result = call.apply(method.getName(), args);
                    return result == null && method.getReturnType() == boolean.class ? false : result;
                });
    }

    @SuppressWarnings("unchecked") private static void accept(java.util.function.Consumer<?> callback, Object event) {
        ((java.util.function.Consumer<Object>) callback).accept(event);
    }

    @SuppressWarnings("unchecked") private static <T> T field(PlayerList list, String name) {
        try { var field = PlayerList.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(list); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
