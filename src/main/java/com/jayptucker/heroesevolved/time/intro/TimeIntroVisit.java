package com.jayptucker.heroesevolved.time.intro;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Persisted before travelling, so a disconnect or server restart cannot strand a visitor. */
public record TimeIntroVisit(boolean pending, ResourceLocation dimension,
                             double x, double y, double z, float yaw, float pitch) {
    public static final Codec<TimeIntroVisit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("pending").forGetter(TimeIntroVisit::pending),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(TimeIntroVisit::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(TimeIntroVisit::x),
            Codec.DOUBLE.fieldOf("y").forGetter(TimeIntroVisit::y),
            Codec.DOUBLE.fieldOf("z").forGetter(TimeIntroVisit::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(TimeIntroVisit::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(TimeIntroVisit::pitch)
    ).apply(i, TimeIntroVisit::new));

    public static TimeIntroVisit empty() {
        return new TimeIntroVisit(false, ResourceLocation.withDefaultNamespace("overworld"),
                0, 64, 0, 0, 0);
    }

    public static TimeIntroVisit capture(ServerPlayer player) {
        return new TimeIntroVisit(true, player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
