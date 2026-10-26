package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One description per effect; the client builds the geometry locally. */
public record AbilityEffectPayload(Kind kind, Vec3 origin, Vec3 target, float scale)
        implements CustomPacketPayload {
    public enum Kind {
        QUAKE_BEAM, QUAKE_IMPACT, QUAKE_PULSE, TREMOR_SCAN, QUAKE_PLUNGE,
        ELECTRIC_ARC, ELECTRIC_NOVA, PSYCHIC_PUSH, PSYCHIC_BIND, PSYCHIC_FIELD,
        TEMPORAL_BURST, TEMPORAL_FIELD, REGENERATION, BLOOD_THREAD,
        FLIGHT_BURST, FLIGHT_CYCLONE,
        QUAKE_CHANNEL, QUAKE_CONTACT, ELECTRIC_ORB, ELECTRIC_IMPACT,
        PSYCHIC_TETHER, PSYCHIC_IMPACT, HEALING_AURA, TEMPORAL_REWIND,
        DIVE_TRAIL, DIVE_IMPACT,
        QUAKE_AWAKEN_CHARGE, QUAKE_AWAKEN_RELEASE,
        FLIGHT_AWAKEN_CHARGE, FLIGHT_AWAKEN_RELEASE,
        ELECTRIC_AWAKEN_CHARGE, ELECTRIC_AWAKEN_RELEASE,
        PSYCHIC_AWAKEN_CHARGE, PSYCHIC_AWAKEN_RELEASE,
        REGEN_AWAKEN_CHARGE, REGEN_AWAKEN_RELEASE,
        CRYO_BOLT, CRYO_CHILL, CRYO_FREEZE, CRYO_SHATTER, CRYO_ICE_GROWTH,
        CRYO_BLIZZARD, CRYO_AWAKEN_CHARGE, CRYO_AWAKEN_RELEASE,
        NUCLEAR_CHARGE, NUCLEAR_SHOCKWAVE, NUCLEAR_FALLOUT, NUCLEAR_EMP,
        NUCLEAR_RAY, MIND_LINK, MIND_PULSE, SONIC_WAVE, SONIC_BURST, SONIC_SENSE,
        SPEED_TRAIL, SPEED_BURST, SPEED_IMPACT, SPEED_VORTEX
    }

    public static final Type<AbilityEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "ability_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityEffectPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeEnum(payload.kind);
                buffer.writeVec3(payload.origin);
                buffer.writeVec3(payload.target);
                buffer.writeFloat(payload.scale);
            }, buffer -> new AbilityEffectPayload(buffer.readEnum(Kind.class),
                    buffer.readVec3(), buffer.readVec3(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AbilityEffectRenderer.play(payload));
    }
}
