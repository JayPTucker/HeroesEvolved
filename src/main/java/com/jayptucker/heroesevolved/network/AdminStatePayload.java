package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** Everything the admin menu displays, sent only to operators. */
public record AdminStatePayload(
        boolean openScreen,
        boolean eclipseActive,
        int maximumLevel,
        List<String> powers,
        List<PlayerEntry> players,
        World world,
        List<EventEntry> events,
        List<String> diagnostics,
        String feedback,
        List<String> commands
) implements CustomPacketPayload {
    public AdminStatePayload(boolean openScreen, boolean eclipseActive, int maximumLevel, List<String> powers,
                             List<PlayerEntry> players, World world, List<EventEntry> events, List<String> diagnostics, String feedback) {
        this(openScreen, eclipseActive, maximumLevel, powers, players, world, events, diagnostics, feedback, List.of());
    }
    public AdminStatePayload(boolean openScreen, boolean eclipseActive, int maximumLevel, List<String> powers,
                             List<PlayerEntry> players, World world) {
        this(openScreen, eclipseActive, maximumLevel, powers, players, world, List.of(), List.of(), "");
    }

    /** Server-owned catalogue: every event has explicit scope and a matching cancellation path. */
    public record EventEntry(String id, String title, boolean story, String scope, String status,
                             boolean active, boolean canStart, List<String> starts) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EventEntry> STREAM_CODEC = StreamCodec.of(
                (b, e) -> {
                    b.writeUtf(e.id(), 64); b.writeUtf(e.title(), 64); b.writeBoolean(e.story());
                    b.writeUtf(e.scope(), 128); b.writeUtf(e.status(), 1024);
                    b.writeBoolean(e.active()); b.writeBoolean(e.canStart());
                    b.writeCollection(e.starts(), (buf, s) -> buf.writeUtf(s, 64));
                }, b -> new EventEntry(b.readUtf(64), b.readUtf(64), b.readBoolean(), b.readUtf(128), b.readUtf(1024),
                        b.readBoolean(), b.readBoolean(), b.readList(buf -> buf.readUtf(64))));
    }
    /**
     * What is going on in the world, one line each, for the events tab. {@code flags} says which of the bosses and
     * events are running, so the menu can offer to end them.
     */
    public record World(String eclipse, String sylar, String village, String radio, int flags) {
        public static final int ECLIPSE = 1, TOTALITY = 2, SYLAR = 4, MELTDOWN = 16, RAID = 32, ON_AIR = 64, LAST_DAY = 128;
        public static final World NONE = new World("", "", "", "", 0);

        public boolean has(int flag) { return (flags & flag) != 0; }

        public static final StreamCodec<RegistryFriendlyByteBuf, World> STREAM_CODEC = StreamCodec.of(
                (buffer, w) -> {
                    buffer.writeUtf(w.eclipse(), 256);
                    buffer.writeUtf(w.sylar(), 256);
                    buffer.writeUtf(w.village(), 256);
                    buffer.writeUtf(w.radio(), 256);
                    buffer.writeVarInt(w.flags());
                },
                buffer -> new World(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256),
                        buffer.readUtf(256), buffer.readVarInt()));
    }

    public record PlayerEntry(
            UUID id,
            String name,
            String powerId,
            boolean awakened,
            int level,
            long mastery,
            long masteryForNextLevel,
            int energy,
            int maximumEnergy,
            boolean unlimitedEnergy,
            boolean godMode,
            /** Where they are, and how far the stories have got with them. */
            String whereabouts,
            String story
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerEntry>
                STREAM_CODEC = StreamCodec.of(
                        (buffer, entry) -> {
                            UUIDUtil.STREAM_CODEC.encode(buffer, entry.id());
                            buffer.writeUtf(entry.name(), 64);
                            buffer.writeUtf(entry.powerId(), 256);
                            buffer.writeBoolean(entry.awakened());
                            buffer.writeVarInt(entry.level());
                            buffer.writeVarLong(entry.mastery());
                            buffer.writeVarLong(entry.masteryForNextLevel());
                            buffer.writeVarInt(entry.energy());
                            buffer.writeVarInt(entry.maximumEnergy());
                            buffer.writeBoolean(entry.unlimitedEnergy());
                            buffer.writeBoolean(entry.godMode());
                            buffer.writeUtf(entry.whereabouts(), 128);
                            buffer.writeUtf(entry.story(), 256);
                        },
                        buffer -> new PlayerEntry(
                                UUIDUtil.STREAM_CODEC.decode(buffer),
                                buffer.readUtf(64),
                                buffer.readUtf(256),
                                buffer.readBoolean(),
                                buffer.readVarInt(),
                                buffer.readVarLong(),
                                buffer.readVarLong(),
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readBoolean(),
                                buffer.readBoolean(),
                                buffer.readUtf(128),
                                buffer.readUtf(256)
                        )
                );
    }

    public static final Type<AdminStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "admin_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AdminStatePayload
    > STREAM_CODEC = StreamCodec.of((b, s) -> {
        b.writeBoolean(s.openScreen()); b.writeBoolean(s.eclipseActive()); b.writeVarInt(s.maximumLevel());
        ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64)).encode(b, s.powers());
        PlayerEntry.STREAM_CODEC.apply(ByteBufCodecs.list(512)).encode(b, s.players());
        World.STREAM_CODEC.encode(b, s.world());
        EventEntry.STREAM_CODEC.apply(ByteBufCodecs.list(64)).encode(b, s.events());
        ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(64)).encode(b, s.diagnostics());
        b.writeUtf(s.feedback(), 1024);
        ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(1024)).encode(b, s.commands());
    }, b -> new AdminStatePayload(b.readBoolean(), b.readBoolean(), b.readVarInt(),
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64)).decode(b),
            PlayerEntry.STREAM_CODEC.apply(ByteBufCodecs.list(512)).decode(b), World.STREAM_CODEC.decode(b),
            EventEntry.STREAM_CODEC.apply(ByteBufCodecs.list(64)).decode(b),
            ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(64)).decode(b), b.readUtf(1024),
            ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(1024)).decode(b)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            AdminStatePayload payload,
            IPayloadContext context
    ) {
        ClientAdminState.update(payload);
    }
}
