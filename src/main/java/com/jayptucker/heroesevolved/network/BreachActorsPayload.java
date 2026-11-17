package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.mixin.BreachEntityDataAccess;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Current far-side entities for one breach mouth. Only the vanilla spawn/appearance data is copied. */
public record BreachActorsPayload(int view, List<Actor> actors) implements CustomPacketPayload {
    public static final int LIMIT = 20, UPDATE_TICKS = 4;
    private static final int MAX_SPAWN_BYTES = 4096;
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    public record Actor(ClientboundAddEntityPacket spawn, ClientboundSetEntityDataPacket appearance,
                        @Nullable GameProfile profile, List<ItemStack> equipment, byte[] extraSpawn,
                        float bodyYaw, float headYaw, float walk, int age) {
        public static Actor capture(ServerLevel level, Entity entity) {
            var tracker = new ServerEntity(level, entity, entity.getType().updateInterval(), false, packet -> {});
            var items = ((BreachEntityDataAccess) entity.getEntityData()).heroes$itemsById();
            List<SynchedEntityData.DataValue<?>> data = new ArrayList<>(items.length);
            for (var item : items) data.add(item.value());
            var equipment = new ArrayList<ItemStack>();
            float body = entity.getYRot(), walk = 0;
            if (entity instanceof LivingEntity living) {
                for (var slot : SLOTS) equipment.add(living.getItemBySlot(slot).copy());
                body = living.yBodyRot;
                walk = living.walkAnimation.speed();
            }
            GameProfile profile = null;
            if (entity instanceof ServerPlayer player) {
                GameProfile original = player.getGameProfile();
                profile = new GameProfile(original.getId(), original.getName());
                profile.getProperties().putAll("textures", original.getProperties().get("textures"));
            }
            var packet = entity.getAddEntityPacket(tracker);
            var spawn = packet instanceof ClientboundAddEntityPacket add ? add : new ClientboundAddEntityPacket(entity, tracker);
            return new Actor(spawn, new ClientboundSetEntityDataPacket(entity.getId(), data),
                    profile, equipment, writeSpawn(level, entity), body, entity.getYHeadRot(), walk, entity.tickCount);
        }

        private static byte[] writeSpawn(ServerLevel level, Entity entity) {
            if (!(entity instanceof IEntityWithComplexSpawn complex)) return new byte[0];
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                complex.writeSpawnData(buffer);
                int bytes = buffer.readableBytes();
                if (bytes > MAX_SPAWN_BYTES) return new byte[0];
                byte[] data = new byte[bytes];
                buffer.readBytes(data);
                return data;
            } finally {
                buffer.release();
            }
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> EQUIPMENT =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS.length));
    private static final StreamCodec<RegistryFriendlyByteBuf, Actor> ACTOR = StreamCodec.of((buf, actor) -> {
        ClientboundAddEntityPacket.STREAM_CODEC.encode(buf, actor.spawn);
        ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buf, actor.appearance);
        buf.writeBoolean(actor.profile != null);
        if (actor.profile != null) ByteBufCodecs.GAME_PROFILE.encode(buf, actor.profile);
        EQUIPMENT.encode(buf, actor.equipment);
        buf.writeByteArray(actor.extraSpawn);
        buf.writeFloat(actor.bodyYaw);
        buf.writeFloat(actor.headYaw);
        buf.writeFloat(actor.walk);
        buf.writeVarInt(actor.age);
    }, buf -> new Actor(ClientboundAddEntityPacket.STREAM_CODEC.decode(buf), ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buf),
            buf.readBoolean() ? ByteBufCodecs.GAME_PROFILE.decode(buf) : null, EQUIPMENT.decode(buf), buf.readByteArray(MAX_SPAWN_BYTES),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Actor>> ACTORS = ACTOR.apply(ByteBufCodecs.list(LIMIT));

    public static final Type<BreachActorsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "breach_actors"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreachActorsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BreachActorsPayload::view, ACTORS, BreachActorsPayload::actors, BreachActorsPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BreachActorsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.BreachViewClient.acceptActors(payload));
    }
}
