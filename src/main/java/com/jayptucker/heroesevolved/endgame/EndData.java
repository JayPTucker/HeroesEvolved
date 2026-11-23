package com.jayptucker.heroesevolved.endgame;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The checkpoint, captured experience, restored scenery and belongings outlive every runtime boss entity. */
public final class EndData extends SavedData {
    public static final String NAME="heroesevolved_living_seal";
    public record Home(ResourceKey<Level> dimension,Vec3 at,float yaw,float pitch){}
    public record Change(BlockState before,BlockState after){}
    public int stage,checkpoint,deathTick,pendingXP,expectedXP;
    public boolean completed,paid;
    public UUID dragon;
    public float floor=64;
    public Vec3 refuge=new Vec3(76.5,64,.5);
    public final List<Vec3> anchors=new ArrayList<>(),crystals=new ArrayList<>();
    public final Map<BlockPos,Change> scenery=new LinkedHashMap<>();
    public final Set<Integer> xpTicks=new HashSet<>();
    public final Set<UUID> participants=new LinkedHashSet<>(),winners=new HashSet<>();
    private final Map<UUID,Home> homes=new HashMap<>();
    private final Map<UUID,List<CompoundTag>> items=new HashMap<>();
    public static EndData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(EndData::new,EndData::load),NAME);}
    public void changed(){setDirty();}
    public void home(UUID id,Home home){homes.put(id,home);setDirty();}
    public Home home(UUID id){return homes.get(id);}
    public void forgetHome(UUID id){homes.remove(id);setDirty();}
    public boolean win(UUID id){boolean first=winners.add(id);setDirty();return first;}
    public void stash(UUID id,ItemStack stack,HolderLookup.Provider access){
        if(!stack.isEmpty()){items.computeIfAbsent(id,k->new ArrayList<>()).add((CompoundTag)stack.save(access));setDirty();}
    }
    public List<ItemStack> take(UUID id,HolderLookup.Provider access){var tags=items.remove(id);setDirty();return tags==null?List.of():tags.stream().map(t->ItemStack.parse(access,t)).flatMap(Optional::stream).toList();}
    public static CompoundTag vector(Vec3 v){var t=new CompoundTag();t.putDouble("x",v.x);t.putDouble("y",v.y);t.putDouble("z",v.z);return t;}
    public static Vec3 vector(CompoundTag t){return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));}
    private static ListTag vectors(List<Vec3> list){var tags=new ListTag();for(Vec3 v:list)tags.add(vector(v));return tags;}
    private static void vectors(CompoundTag t,String key,List<Vec3> list){for(Tag raw:t.getList(key,Tag.TAG_COMPOUND))list.add(vector((CompoundTag)raw));}
    public static EndData load(CompoundTag t,HolderLookup.Provider access){
        var d=new EndData();d.stage=t.getInt("stage");d.checkpoint=t.getInt("checkpoint");d.deathTick=t.getInt("deathTick");d.pendingXP=t.getInt("xp");d.expectedXP=t.getInt("expectedXP");
        d.completed=t.getBoolean("completed");d.paid=t.getBoolean("paid");if(t.hasUUID("dragon"))d.dragon=t.getUUID("dragon");
        if(t.contains("floor"))d.floor=t.getFloat("floor");if(t.contains("refuge"))d.refuge=vector(t.getCompound("refuge"));
        vectors(t,"anchors",d.anchors);vectors(t,"crystals",d.crystals);
        for(int tick:t.getIntArray("xpTicks"))d.xpTicks.add(tick);
        for(Tag raw:t.getList("people",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;if(p.hasUUID("id")){UUID id=p.getUUID("id");if(p.getBoolean("winner"))d.winners.add(id);if(p.getBoolean("participant"))d.participants.add(id);}}
        for(Tag raw:t.getList("homes",Tag.TAG_COMPOUND)){var h=(CompoundTag)raw;if(h.hasUUID("id")){
            var dim=ResourceLocation.tryParse(h.getString("dimension"));d.homes.put(h.getUUID("id"),new Home(dim==null?Level.OVERWORLD:ResourceKey.create(Registries.DIMENSION,dim),vector(h),h.getFloat("yaw"),h.getFloat("pitch")));
        }}
        for(Tag raw:t.getList("items",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;if(p.hasUUID("id")){List<CompoundTag> list=new ArrayList<>();for(Tag stack:p.getList("stacks",Tag.TAG_COMPOUND))list.add((CompoundTag)stack);d.items.put(p.getUUID("id"),list);}}
        for(Tag raw:t.getList("scenery",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;d.scenery.put(BlockPos.of(p.getLong("pos")),new Change(NbtUtils.readBlockState(access.lookupOrThrow(Registries.BLOCK),p.getCompound("before")),NbtUtils.readBlockState(access.lookupOrThrow(Registries.BLOCK),p.getCompound("after"))));}
        return d;
    }
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider access){
        t.putInt("stage",stage);t.putInt("checkpoint",checkpoint);t.putInt("deathTick",deathTick);t.putInt("xp",pendingXP);t.putInt("expectedXP",expectedXP);t.putBoolean("completed",completed);t.putBoolean("paid",paid);
        if(dragon!=null)t.putUUID("dragon",dragon);t.putFloat("floor",floor);t.put("refuge",vector(refuge));t.put("anchors",vectors(anchors));t.put("crystals",vectors(crystals));t.putIntArray("xpTicks",xpTicks.stream().mapToInt(Integer::intValue).toArray());
        var people=new ListTag();Set<UUID> ids=new HashSet<>(winners);ids.addAll(participants);for(UUID id:ids){var p=new CompoundTag();p.putUUID("id",id);p.putBoolean("winner",winners.contains(id));p.putBoolean("participant",participants.contains(id));people.add(p);}t.put("people",people);
        var hs=new ListTag();homes.forEach((id,h)->{var p=vector(h.at);p.putUUID("id",id);p.putString("dimension",h.dimension.location().toString());p.putFloat("yaw",h.yaw);p.putFloat("pitch",h.pitch);hs.add(p);});t.put("homes",hs);
        var its=new ListTag();items.forEach((id,stacks)->{var p=new CompoundTag();p.putUUID("id",id);var list=new ListTag();list.addAll(stacks);p.put("stacks",list);its.add(p);});t.put("items",its);
        var changes=new ListTag();scenery.forEach((pos,c)->{var p=new CompoundTag();p.putLong("pos",pos.asLong());p.put("before",NbtUtils.writeBlockState(c.before));p.put("after",NbtUtils.writeBlockState(c.after));changes.add(p);});t.put("scenery",changes);
        return t;
    }
}
