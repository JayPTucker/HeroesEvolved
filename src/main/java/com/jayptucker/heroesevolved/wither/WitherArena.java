package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.bunker.BunkerLogs;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A persistent entrance, an arena rebuilt for each attempt, and a safe way home for every participant. */
public final class WitherArena {
    private static RiftWitherEntity intro, boss;
    private static boolean pending, ready, built, sourceForced;
    private static ServerLevel forcedSource;
    private static ChunkPos sourceChunk;
    private static int emptyTicks, closeIn=-1;
    private static int generation;
    private static final Map<UUID,ServerPlayer> MEMBERS=new LinkedHashMap<>();
    private static final Set<UUID> MOVING=new HashSet<>(), LEAVE_FIRST=new HashSet<>();
    private static final Map<ItemEntity,UUID> PRIZES=new HashMap<>();
    private static Map<String,List<BlockPos>> markers=Map.of();
    private static final Set<Long> CAGE_HELD=new HashSet<>();
    private WitherArena() {}
    public static boolean inside(Entity e) { return e.level().dimension().equals(WitherGeometry.DIMENSION); }
    public static boolean moving(ServerPlayer p) { return MOVING.contains(p.getUUID()); }
    public static RiftWitherEntity boss() { return boss; }
    public static boolean ready() { return ready; }
    public static String status(MinecraftServer server){
        if(!busy(server))return "No Wither breach. Open one near the selected player or use the ordinary skull construction.";
        if(boss==null)return ready?"Breach ready. Entry requires an awakened ability and Power Level 16.":"The summoning vessel is folding into the breach.";
        return "Phase "+boss.phase()+"; "+Math.round(boss.getHealth())+" / "+Math.round(boss.getMaxHealth())+" health; "+boss.fighters().size()+" fighter(s). "
                +(boss.dying()?"Final collapse.":boss.action()==null?"Pursuing / recovering.":boss.action().name()+"; cracked stance "+boss.stance()+" / "+WitherGeometry.STANCE_HITS+".")
                +" Cancellation returns every fighter and clears the source breach.";
    }
    public static String operatorStart(ServerPlayer target,int variant){
        if(variant==0)return start(target.serverLevel(),target.position().add(target.getLookAngle().multiply(1,0,1).normalize().scale(7)),target.getYRot())?"Forming a Wither breach near the selected player.":"A Wither breach is already open, or this dimension cannot host its source.";
        if(variant==1)return enter(target)?"Entered the Wither fight.":"Entry refused: wait for the breach, leave any current attempt, and check awakened Power Level 16.";
        if(variant==2&&boss!=null&&boss.fighting()&&boss.phase()<3){boss.operatorPhase();return "Advanced the live Wither to phase "+boss.phase()+".";}
        return "No live Wither can advance to another phase.";
    }
    public static boolean busy(MinecraftServer server) { return WitherData.get(server).source!=null; }
    public static boolean sheltered(Entity p) { return inside(p) && p.getZ()<WitherGeometry.ORIGIN.getZ()-34 && p.getZ()>WitherGeometry.ORIGIN.getZ()-42
            && Math.abs(p.getX()-WitherGeometry.ORIGIN.getX())<4 && p.getY()<WitherGeometry.floor().y+5; }

    public static boolean start(ServerLevel source,Vec3 at,float yaw) {
        MinecraftServer server=source.getServer();
        if(busy(server)||server.getLevel(WitherGeometry.DIMENSION)==null||insideDimension(source))return false;
        WitherData.get(server).source(new WitherData.Home(source.dimension(),at,yaw,0));
        pending=true; ready=false;int run=++generation;
        server.execute(()->{
            if(run!=generation||WitherData.get(server).source==null)return;
            var actor=ModEntities.RIFT_WITHER.get().create(source);
            pending=false;
            if(actor==null)return;
            actor.moveTo(at.x,at.y,at.z,yaw,0);actor.yBodyRot=actor.yBodyRotO=yaw;actor.setYHeadRot(yaw);actor.beginIntro();
            intro=actor;source.addFreshEntity(actor);
        });
        return true;
    }
    private static boolean insideDimension(ServerLevel level) { return level.dimension().equals(WitherGeometry.DIMENSION); }
    public static void swallowed(RiftWitherEntity actor) {
        if(actor!=intro||WitherData.get(actor.level().getServer()).source==null){actor.discard();return;}
        ready=true;intro=null;
        ServerLevel level=(ServerLevel)actor.level();
        for(ServerPlayer p:level.players())if(p.distanceToSqr(actor)<32*32)award(p,"wither_anomaly");
        actor.discard();
    }
    private static void award(ServerPlayer p,String id) {
        var advancement=p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,id));
        if(advancement!=null)p.getAdvancements().award(advancement,"observed");
    }
    public static boolean eligible(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&PlayerProgressionService.getPowerLevel(p)>=WitherGeometry.MINIMUM_LEVEL
                &&PlayerAbilityService.getData(p).assignedPower().filter(e->e.getValue().isUnlocked()).isPresent();
    }
    public static boolean enter(ServerPlayer p) {
        MinecraftServer server=p.getServer();var data=WitherData.get(server);
        if(!ready||data.source==null||closeIn>=0||inside(p)||boss!=null&&boss.dying())return false;
        if(!eligible(p)) {
            if(!LEAVE_FIRST.contains(p.getUUID()))p.sendSystemMessage(Component.translatable("wither.heroesevolved.level",WitherGeometry.MINIMUM_LEVEL));
            LEAVE_FIRST.add(p.getUUID());return false;
        }
        ServerLevel cage=server.getLevel(WitherGeometry.DIMENSION);if(cage==null)return false;
        if(!built) {
            holdCage(cage,true);
            markers=Blueprint.load("wither_cage").place(cage,WitherGeometry.ORIGIN,Rotation.NONE);
            built=true;
        }
        data.home(p.getUUID(),new WitherData.Home(p.level().dimension(),p.position(),p.getYRot(),p.getXRot()));
        MEMBERS.put(p.getUUID(),p);move(p,cage,WitherGeometry.entrance(),0,0);
        if(boss==null||!boss.isAlive()) {
            boss=ModEntities.RIFT_WITHER.get().create(cage);
            if(boss==null){sendHome(p);return false;}
            boss.moveTo(WitherGeometry.floor().add(0,.35,0));boss.beginFight();cage.addFreshEntity(boss);
        }
        boss.join(p);emptyTicks=0;
        p.sendSystemMessage(Component.translatable("wither.heroesevolved.enter"));
        return true;
    }
    private static void holdCage(ServerLevel level,boolean hold) {
        if(hold)for(int x=-3;x<=2;x++)for(int z=-3;z<=2;z++) {
            long key=ChunkPos.asLong(x,z);
            if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);CAGE_HELD.add(key);}
        }
        else {for(long key:CAGE_HELD){ChunkPos at=new ChunkPos(key);level.setChunkForced(at.x,at.z,false);}CAGE_HELD.clear();}
    }
    private static void move(ServerPlayer p,ServerLevel level,Vec3 at,float yaw,float pitch) {
        MOVING.add(p.getUUID());
        try {p.teleportTo(level,at.x,at.y,at.z,yaw,pitch);p.absMoveTo(at.x,at.y,at.z,yaw,pitch);}
        finally {MOVING.remove(p.getUUID());}
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
    public static void sendHome(ServerPlayer p) {
        var data=WitherData.get(p.getServer());var home=data.home(p.getUUID());
        ServerLevel level=home==null?null:p.getServer().getLevel(home.dimension());
        if(level==null||insideDimension(level)){level=p.getServer().overworld();home=null;}
        Vec3 at=home==null?Vec3.atBottomCenterOf(level.getSharedSpawnPos()):home.at();
        level.getChunk(BlockPos.containing(at));
        if(!level.noCollision(p,p.getBoundingBox().move(at.subtract(p.position())))) {
            Vec3 raised=at.add(0,2,0);
            at=level.noCollision(p,p.getBoundingBox().move(raised.subtract(p.position())))?raised:Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        }
        move(p,level,at,home==null?p.getYRot():home.yaw(),home==null?0:home.pitch());
        data.clearHome(p.getUUID());LEAVE_FIRST.add(p.getUUID());deliver(p);
    }
    public static void deliver(ServerPlayer p) {
        for(ItemStack stack:WitherData.get(p.getServer()).take(p.getUUID(),p.registryAccess()))
            if(!p.getInventory().add(stack))p.drop(stack,false);
    }
    public static void victory(RiftWitherEntity defeated) {
        if(defeated!=boss||closeIn>=0)return;
        ServerLevel level=(ServerLevel)defeated.level();var data=WitherData.get(level.getServer());
        UUID starOwner=defeated.killer();if(starOwner==null||!MEMBERS.containsKey(starOwner))starOwner=MEMBERS.keySet().stream().findFirst().orElse(null);
        if(starOwner!=null)prize(level,defeated.position(),new ItemStack(Items.NETHER_STAR),starOwner);
        for(var entry:MEMBERS.entrySet()) {
            UUID id=entry.getKey();ServerPlayer p=entry.getValue();
            prize(level,defeated.position(),new ItemStack(ModItems.FRACTURED_STAR.get()),id);
            if(data.win(id))prize(level,defeated.position(),BunkerLogs.book("Containment Failure","Unknown researcher",
                    "Three heads. One cage.\n\nThe star was never a heart. It was something the bones were keeping in.",
                    "The twelve eyes of the stronghold are another cage. Eyes alone cannot open it.\n\nTwo touching frames. Two offerings from gifts beyond Power Level 15. A frame remembers long enough for one traveler to make both."),id);
            else if(level.random.nextFloat()<.1F)prize(level,defeated.position(),new ItemStack(ModItems.MUSIC_DISC_CONTAINMENT.get()),id);
            award(p,"wither_contained");
        }
        closeIn=100;
    }
    private static void prize(ServerLevel level,Vec3 at,ItemStack stack,UUID owner) {
        var entity=new ItemEntity(level,at.x,at.y,at.z,stack);entity.setTarget(owner);entity.setPickUpDelay(10);
        level.addFreshEntity(entity);PRIZES.put(entity,owner);
    }
    private static boolean touching(ServerPlayer p,Vec3 center,Vec3 normal) {
        Vec3 d=p.position().add(0,.9,0).subtract(center),side=normal.cross(new Vec3(0,1,0));
        return Math.abs(d.dot(normal))<.75&&Math.abs(d.dot(side))<1.3&&Math.abs(d.y)<2;
    }
    public static void tick(MinecraftServer server) {
        var data=WitherData.get(server);var source=data.source;
        if(source==null)return;
        ServerLevel world=server.getLevel(source.dimension());if(world==null)return;
        if(forcedSource==null) {
            sourceChunk=new ChunkPos(BlockPos.containing(source.at()));forcedSource=world;
            sourceForced=!world.getForcedChunks().contains(sourceChunk.toLong());
            if(sourceForced)world.setChunkForced(sourceChunk.x,sourceChunk.z,true);
        }
        if(!pending&&(intro==null||intro.isRemoved())&&!ready)ready=true; // A saved entrance resumes after a restart.
        Vec3 gate=source.at().add(0,1.8,0),normal=Vec3.directionFromRotation(0,source.yaw());
        if(ready&&world.getGameTime()%10==0)
            SetPiecePayload.send(world,Piece.WITHER_BREACH,gate,gate.add(normal),2,null);
        if(ready)for(ServerPlayer p:List.copyOf(world.players())) {
            if(p.position().distanceToSqr(gate)>16)LEAVE_FIRST.remove(p.getUUID());
            if(!LEAVE_FIRST.contains(p.getUUID())&&touching(p,gate,normal))enter(p);
        }
        ServerLevel cage=server.getLevel(WitherGeometry.DIMENSION);
        if(!built||cage==null)return;
        if(cage.getGameTime()%10==0)SetPiecePayload.send(cage,Piece.WITHER_BREACH,WitherGeometry.exit(),WitherGeometry.exit().add(0,0,1),2,null);
        for(ServerPlayer p:List.copyOf(MEMBERS.values()))if(p.isAlive()&&inside(p)) {
            if(touching(p,WitherGeometry.exit(),new Vec3(0,0,1))){sendHome(p);continue;}
            if(p.getY()<WitherGeometry.ORIGIN.getY()-5||p.getY()>WitherGeometry.floor().y+WitherGeometry.FLIGHT_HEIGHT||p.position().multiply(1,0,1).distanceTo(WitherGeometry.floor().multiply(1,0,1))>44)
                move(p,cage,WitherGeometry.entrance(),0,0);
        }
        if(closeIn>0&&--closeIn==0){reset(server,false);return;}
        if(closeIn<0&&boss!=null) {
            if(boss.fighters().isEmpty())emptyTicks++;else emptyTicks=0;
            if(emptyTicks>=400)resetAttempt(server);
        }
    }
    private static void collect(MinecraftServer server) {
        var data=WitherData.get(server);
        for(var entry:PRIZES.entrySet())if(!entry.getKey().isRemoved()&&!entry.getKey().getItem().isEmpty()) {
            data.stash(entry.getValue(),entry.getKey().getItem(),server.registryAccess());entry.getKey().discard();
        }
        PRIZES.clear();
    }
    private static void resetAttempt(MinecraftServer server) {
        for(ServerPlayer p:List.copyOf(MEMBERS.values()))if(p.isAlive()&&inside(p))sendHome(p);
        collect(server);if(boss!=null)boss.discard();boss=null;
        ServerLevel cage=server.getLevel(WitherGeometry.DIMENSION);
        if(cage!=null)holdCage(cage,false);
        built=false;emptyTicks=0;MEMBERS.clear();markers=Map.of();
    }
    public static void phase(int phase) {
        if(boss==null||!(boss.level() instanceof ServerLevel level))return;
        for(BlockPos at:markers.getOrDefault("wither:channel",List.of()))if(level.hasChunkAt(at))
            level.setBlock(at,(phase==2?net.minecraft.world.level.block.Blocks.SEA_LANTERN:net.minecraft.world.level.block.Blocks.SHROOMLIGHT).defaultBlockState(),3);
    }
    /** Server stops preserve the entrance. Victory and operator/test cleanup clear it. */
    public static void reset(MinecraftServer server,boolean preserveEntrance) {
        generation++;
        collect(server);
        for(ServerPlayer p:List.copyOf(MEMBERS.values())) {if(p.isAlive()&&inside(p))sendHome(p);else if(p.isAlive())deliver(p);}
        resetAttempt(server);
        if(intro!=null)intro.discard();intro=null;pending=false;ready=false;closeIn=-1;
        if(forcedSource!=null&&sourceForced)forcedSource.setChunkForced(sourceChunk.x,sourceChunk.z,false);
        forcedSource=null;sourceChunk=null;sourceForced=false;
        if(!preserveEntrance)WitherData.get(server).source(null);
        MOVING.clear();LEAVE_FIRST.clear();
    }
}
