package com.jayptucker.heroesevolved.shapeshift;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * One shape a shapeshifter can wear: a creature's type, or for a person, {@link EntityType#PLAYER} with their profile
 * (id, name and skin). Two forms are the same when their {@link #key()}s match: the type id for a creature, and
 * {@code player:<uuid>} for a person, so copying two people gives two forms.
 */
public record ShapeshiftForm(EntityType<?> type, Optional<GameProfile> profile) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapeshiftForm> STREAM_CODEC = StreamCodec.of(
            (b, f) -> { b.writeUtf(ShapeshiftForms.id(f.type).toString()); ByteBufCodecs.optional(ByteBufCodecs.GAME_PROFILE).encode(b, f.profile); },
            b -> {
                EntityType<?> type = ShapeshiftForms.type(b.readUtf());
                Optional<GameProfile> profile = ByteBufCodecs.optional(ByteBufCodecs.GAME_PROFILE).decode(b);
                return new ShapeshiftForm(type == null ? EntityType.PIG : type, profile);
            });

    public static ShapeshiftForm of(EntityType<?> type) { return new ShapeshiftForm(type, Optional.empty()); }

    /** A person's form: a copy of their profile, keeping only what a body needs (id, name, skin). */
    public static ShapeshiftForm of(GameProfile person) {
        GameProfile copy = new GameProfile(person.getId(), person.getName());
        for (Property texture : person.getProperties().get("textures")) copy.getProperties().put("textures", texture);
        return new ShapeshiftForm(EntityType.PLAYER, Optional.of(copy));
    }

    public boolean person() { return profile.isPresent(); }

    public @Nullable GameProfile personOrNull() { return profile.orElse(null); }

    /** What makes two forms the same shape. */
    public String key() {
        return profile.map(p -> "player:" + p.getId()).orElseGet(() -> ShapeshiftForms.id(type).toString());
    }

    /** The name to show for this form: the person's name, or the creature's. */
    public String name() {
        return profile.map(GameProfile::getName).orElseGet(() -> type.getDescription().getString());
    }

    public net.minecraft.network.chat.Component description() {
        return profile.<net.minecraft.network.chat.Component>map(p -> net.minecraft.network.chat.Component.literal(p.getName()))
                .orElseGet(type::getDescription);
    }

    // ---- Saving ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", ShapeshiftForms.id(type).toString());
        profile.ifPresent(p -> {
            tag.putUUID("id", p.getId());
            tag.putString("name", p.getName());
            for (Property texture : p.getProperties().get("textures")) {
                tag.putString("skin", texture.value());
                if (texture.signature() != null) tag.putString("signature", texture.signature());
            }
        });
        return tag;
    }

    public static @Nullable ShapeshiftForm load(CompoundTag tag) {
        EntityType<?> type = ShapeshiftForms.type(tag.getString("type"));
        if (type == null) return null;
        if (!tag.hasUUID("id")) return type == EntityType.PLAYER ? null : of(type);
        GameProfile profile = new GameProfile(tag.getUUID("id"), tag.getString("name"));
        if (tag.contains("skin"))
            profile.getProperties().put("textures", new Property("textures", tag.getString("skin"),
                    tag.contains("signature") ? tag.getString("signature") : null));
        return new ShapeshiftForm(EntityType.PLAYER, Optional.of(profile));
    }

    /** True if this is {@code player}'s own shape. */
    public boolean isOf(Player player) { return profile.map(p -> p.getId().equals(player.getUUID())).orElse(false); }

    public @Nullable UUID personId() { return profile.map(GameProfile::getId).orElse(null); }
}
