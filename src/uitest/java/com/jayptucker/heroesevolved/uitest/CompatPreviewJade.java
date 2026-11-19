package com.jayptucker.heroesevolved.uitest;

import net.minecraft.core.registries.BuiltInRegistries;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.impl.ObjectDataCenter;

/** The Jade half of {@link CompatPreview}: what Jade is showing right now. Only touched when Jade is loaded. */
final class CompatPreviewJade {
    private CompatPreviewJade() {}

    /** "block heroesevolved:ability_dampener", "entity minecraft:cow", or "none". */
    static String target() {
        Accessor<?> accessor = ObjectDataCenter.get();
        if (accessor instanceof BlockAccessor block)
            return "block " + BuiltInRegistries.BLOCK.getKey(block.getBlock()) + (block.getServerData().isEmpty() ? "" : " data=" + block.getServerData());
        if (accessor instanceof EntityAccessor entity)
            return "entity " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType()) + " \"" + entity.getEntity().getName().getString() + "\"";
        return "none";
    }
}
