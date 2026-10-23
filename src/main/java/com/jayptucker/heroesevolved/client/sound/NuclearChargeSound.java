package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.client.effects.RadiationVisuals;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

/** A moving reactor hum whose whine and volume rise throughout the charge. */
public final class NuclearChargeSound extends AbstractTickableSoundInstance {
    private final ClientLevel world;
    private final int entityId;
    private final UUID uuid;
    public NuclearChargeSound(ClientLevel world, int entityId, UUID uuid) {
        super(ModSounds.NUCLEAR_CHARGE.get(), SoundSource.PLAYERS, RandomSource.create());
        this.world = world; this.entityId = entityId; this.uuid = uuid;
        looping = true; delay = 0; volume = 0.15F;
        tick();
    }
    public void end() { stop(); }
    @Override public void tick() {
        Entity entity = world.getEntity(entityId);
        float charge = RadiationVisuals.charge(entityId, uuid);
        if (Minecraft.getInstance().level != world || entity == null || !entity.getUUID().equals(uuid)
                || charge < 0 || !entity.isAlive()) { stop(); return; }
        x = entity.getX(); y = entity.getY() + 1; z = entity.getZ();
        relative = entity == Minecraft.getInstance().player;
        if (relative) { x = 0; y = 0; z = 0; }
        volume = 0.3F + charge * 0.65F;
        pitch = 0.65F + charge * 1.0F;
    }
}
