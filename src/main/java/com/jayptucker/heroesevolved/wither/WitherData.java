package com.jayptucker.heroesevolved.wither;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A durable entrance and everyone's way home; a restart resets the fight, never a player's belongings. */
public final class WitherData extends SavedData {
    public record Home(ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}
    public Home source;
    private final Map<UUID,Home> homes=new HashMap<>();
    private final Map<UUID,List<CompoundTag>> items=new HashMap<>();
    private final Set<UUID> winners=new HashSet<>();
    public static WitherData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WitherData::new,WitherData::load),"heroesevolved_wither");
    }
    public void source(Home value) { source=value; setDirty(); }
    public Home home(UUID id) { return homes.get(id); }
    public void home(UUID id,Home home) { homes.put(id,home); setDirty(); }
    public void clearHome(UUID id) { homes.remove(id); setDirty(); }
    public boolean win(UUID id) { boolean first=winners.add(id); setDirty(); return first; }
    public void stash(UUID id,ItemStack stack,HolderLookup.Provider access) {
        if (!stack.isEmpty()) { items.computeIfAbsent(id,k->new ArrayList<>()).add((CompoundTag)stack.save(access)); setDirty(); }
    }
    public List<ItemStack> take(UUID id,HolderLookup.Provider access) {
        var tags=items.remove(id); setDirty();
        return tags==null?List.of():tags.stream().map(t->ItemStack.parse(access,t)).flatMap(Optional::stream).toList();
    }
    private static CompoundTag encode(Home h) {
        var t=new CompoundTag(); t.putString("dimension",h.dimension.location().toString());
        t.putDouble("x",h.at.x);t.putDouble("y",h.at.y);t.putDouble("z",h.at.z);
        t.putFloat("yaw",h.yaw);t.putFloat("pitch",h.pitch);return t;
    }
    private static Home decode(CompoundTag t) {
        var id=ResourceLocation.tryParse(t.getString("dimension"));
        var dim=id==null?Level.OVERWORLD:ResourceKey.create(Registries.DIMENSION,id);
        return new Home(dim,new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z")),t.getFloat("yaw"),t.getFloat("pitch"));
    }
    public static WitherData load(CompoundTag tag,HolderLookup.Provider access) {
        var data=new WitherData();
        if(tag.contains("source",Tag.TAG_COMPOUND))data.source=decode(tag.getCompound("source"));
        for(Tag raw:tag.getList("homes",Tag.TAG_COMPOUND)) {var t=(CompoundTag)raw;if(t.hasUUID("player"))data.homes.put(t.getUUID("player"),decode(t));}
        for(Tag raw:tag.getList("items",Tag.TAG_COMPOUND)) {var t=(CompoundTag)raw;if(t.hasUUID("player")) {
            List<CompoundTag> list=new ArrayList<>();for(Tag stack:t.getList("stacks",Tag.TAG_COMPOUND))list.add((CompoundTag)stack);
            data.items.put(t.getUUID("player"),list);
        }}
        for(Tag raw:tag.getList("winners",Tag.TAG_COMPOUND)) {var t=(CompoundTag)raw;if(t.hasUUID("player"))data.winners.add(t.getUUID("player"));}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider access) {
        if(source!=null)tag.put("source",encode(source));
        var hs=new ListTag();homes.forEach((id,h)->{var t=encode(h);t.putUUID("player",id);hs.add(t);});tag.put("homes",hs);
        var its=new ListTag();items.forEach((id,stacks)->{var t=new CompoundTag();t.putUUID("player",id);var l=new ListTag();l.addAll(stacks);t.put("stacks",l);its.add(t);});tag.put("items",its);
        var ws=new ListTag();winners.forEach(id->{var t=new CompoundTag();t.putUUID("player",id);ws.add(t);});tag.put("winners",ws);
        return tag;
    }
}
