package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TimeIntroClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Reliable scene cues, independent of particle settings or distant entity tracking. {@code players}: with
 * FREEZE, the entity ids of the visitors whose power stops and rewinds time, so each client can draw it on them.
 */
public record TimeIntroPayload(int stage, java.util.List<Integer> players) implements CustomPacketPayload {
    public TimeIntroPayload(int stage) { this(stage, java.util.List.of()); }

    public static final int ARRIVAL = 0, DETONATION = 1, RETURN = 2, BLAST_SOUND = 3, CANCEL = 4;
    /** The avenue at 6:14: arriving at 6:10, the light, and coming home with everyone, someone or no one. */
    public static final int AVENUE_ARRIVAL = 5, AVENUE_LIGHT = 6, AVENUE_ALL = 7, AVENUE_SOME = 8, AVENUE_NONE = 9;
    /** The vision: Sprague's glow noticed, the sirens, and time stopping a few blocks from the wave. */
    public static final int GLOW = 10, SIREN = 11, FREEZE = 12;
    /** Arriving in the other places in the past, for the instruction line: Night Three and the Hourglass Annex. */
    public static final int NIGHT_THREE = 13, ANNEX = 14;
    /** Waking in the stasis bunker: the pod alarm, from the moment the power comes back until the lid opens. */
    public static final int BUNKER_ALARM = 15;
    /** The sleeper stepped out of the pod: the alarm stops, and with POWER the main breaker closes as well. */
    public static final int BUNKER_POWER = 16, BUNKER_QUIET = 17;
    /** Someone else is waking: the alarm as heard out in the bunker, until a BUNKER_POWER or BUNKER_QUIET. */
    public static final int BUNKER_HALL_ALARM = 18;
    public static final Type<TimeIntroPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "time_intro"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeIntroPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeVarInt(value.stage);
                buf.writeVarInt(value.players.size());
                value.players.forEach(buf::writeVarInt);
            }, buf -> {
                int stage = buf.readVarInt();
                int count = buf.readVarInt();
                java.util.List<Integer> players = new java.util.ArrayList<>(Math.min(count, 16));
                for (int i = 0; i < count; i++) players.add(buf.readVarInt());
                return new TimeIntroPayload(stage, java.util.List.copyOf(players));
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TimeIntroPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TimeIntroClient.cue(payload.stage, payload.players));
    }
}
