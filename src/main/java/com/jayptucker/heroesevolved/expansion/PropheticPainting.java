package com.jayptucker.heroesevolved.expansion;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.*;

/** A real, locked map canvas: hold it, share it, or put it in a vanilla item frame. */
public final class PropheticPainting {
    private PropheticPainting() {}
    public static ItemStack canvas(ServerPlayer player) {
        var d = FacilityData.get(player.getServer());
        MapId id = d.mapId < 0 ? null : new MapId(d.mapId);
        if (id == null || player.getServer().overworld().getMapData(id) == null) {
            ItemStack stack = MapItem.create(player.getServer().overworld(), 0, 0, (byte) 0, false, false);
            id = stack.get(DataComponents.MAP_ID); d.mapId = id.id(); d.setDirty();
            var data = player.getServer().overworld().getMapData(id);
            player.getServer().overworld().setMapData(id, data.locked());
        }
        ItemStack result = new ItemStack(Items.FILLED_MAP); result.set(DataComponents.MAP_ID, id);
        result.set(DataComponents.CUSTOM_NAME, Component.literal("The Unfinished Hour"));
        CompoundTag tag = new CompoundTag(); tag.putBoolean("heroes_prophetic_canvas", true); result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        refresh(player.getServer()); return result;
    }
    public static boolean isCanvas(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.FILLED_MAP) && data != null && data.copyTag().getBoolean("heroes_prophetic_canvas");
    }
    public static void give(ServerPlayer p) {
        ItemStack stack = canvas(p);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
    public static void refresh(MinecraftServer server) {
        var d = FacilityData.get(server); if (d.mapId < 0) return;
        var map = server.overworld().getMapData(new MapId(d.mapId)); if (map == null) return;
        byte[] image = pixels(d.phase, d.stable, d.rescued);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) if (map.colors[x + y * 128] != image[x + y * 128]) map.setColor(x, y, image[x + y * 128]);
    }
    public static byte[] pixels(FacilityData.Phase phase, boolean stable, int rescued) {
        boolean saved = phase == FacilityData.Phase.SAVED;
        byte[] pixels = (saved ? REVISED : FORETOLD).clone();
        if (!saved && stable) for (int y = 38; y < 88; y++) for (int x = 45; x < 84; x++) pixels[x + y * 128] = REVISED[x + y * 128];
        if (!saved) for (int i = 0; i < 2; i++) if ((rescued & (1 << i)) != 0) {
            int left = i == 0 ? 13 : 88;
            for (int y = 43; y < 75; y++) for (int x = left; x < left + 27; x++) pixels[x + y * 128] = REVISED[x + y * 128];
        }
        if (phase == FacilityData.Phase.SCARRED) for (int y = 5; y < 122; y++) put(pixels, 58 + y / 9, y, color(MapColor.COLOR_BLACK));
        return pixels;
    }
    private static final byte[] FORETOLD = load("foretold"), REVISED = load("revised");
    private static byte[] load(String name) {
        try (var stream = PropheticPainting.class.getResourceAsStream("/assets/heroesevolved/paintings/" + name + ".map")) {
            if (stream == null) throw new IllegalStateException("Missing prophetic artwork " + name);
            byte[] bytes = stream.readAllBytes();
            if (bytes.length != 16384) throw new IllegalStateException("Invalid prophetic artwork size");
            return bytes;
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static byte color(MapColor color) { return color.getPackedId(MapColor.Brightness.NORMAL); }
    private static void put(byte[] pixels, int x, int y, byte color) { if (x >= 0 && x < 128 && y >= 0 && y < 128) pixels[x + y * 128] = color; }
    private static void rect(byte[] pixels, int x, int y, int w, int h, byte color) { for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) put(pixels, x + i, y + j, color); }
}
