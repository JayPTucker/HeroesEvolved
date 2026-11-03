package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.BlizzardStatePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Cryokinesis's connected kit: laid ice, accumulating Frost, controlled prisons and a prepared shatter. */
public final class CryoKitService {
    private record Stream(ServerPlayer owner, ServerLevel level, int tier, long until) {}
    private record Cell(BlockPos pos, long at) {}
    private static final class Construct {
        final ServerPlayer owner; final ServerLevel level; final Vec3 base; final String shape; final long until;
        final Deque<Cell> pending; final Set<BlockPos> blocks = new LinkedHashSet<>();
        Construct(ServerPlayer owner, Vec3 base, String shape, List<Cell> cells, long until) {
            this.owner=owner;this.level=owner.serverLevel();this.base=base;this.shape=shape;this.until=until;
            cells.sort(Comparator.comparingLong(Cell::at));pending=new ArrayDeque<>(cells);
        }
    }
    private record Prison(ServerPlayer owner, LivingEntity target, ServerLevel level) {}
    private static final class Storm {
        final ServerPlayer owner; final ServerLevel level; final Vec3 center; final int tier; final long began,until;
        final Set<BlockPos> spires = new LinkedHashSet<>();
        Storm(ServerPlayer p,Vec3 center,int tier) {owner=p;level=p.serverLevel();this.center=center;this.tier=tier;began=level.getGameTime();until=began+220;}
    }
    private static final Map<UUID,Stream> STREAMS=new HashMap<>();
    private static final Map<UUID,List<Construct>> CONSTRUCTS=new HashMap<>();
    private static final Map<UUID,Long> RESHAPE_READY=new HashMap<>();
    private static final Map<UUID,Prison> PRISONS=new HashMap<>();
    private static final Map<UUID,Storm> STORMS=new HashMap<>();
    private CryoKitService() {}
    public static boolean authorized(ServerPlayer p) {
        return PlayerAbilityService.getData(p).assignedPower().filter(e->e.getKey().equals(ModAbilities.CRYOKINESIS_ID)&&e.getValue().isUnlocked()).isPresent()
                || com.jayptucker.heroesevolved.mimicry.MimicryService.isBorrowingCryokinesis(p);
    }
    private static boolean valid(ServerPlayer p,ServerLevel level) {return p.level()==level&&!p.isRemoved()&&PowerRules.canAct(p)&&authorized(p);}
    private static List<LivingEntity> enemies(ServerPlayer p,Vec3 center,double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),
                e->ExpansionSkills.hostile(p,e)&&e.position().distanceToSqr(center)<=radius*radius).stream().limit(32).toList();
    }
    private static boolean visible(ServerLevel level,Vec3 point,LivingEntity target) {
        return level.clip(new ClipContext(point,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.MISS;
    }
    private static Vec3 flat(ServerPlayer p) {
        Vec3 v=p.getLookAngle().multiply(1,0,1);return v.lengthSqr()<.001?Vec3.directionFromRotation(0,p.getYRot()):v.normalize();
    }
    /** Find the aimed foundation below the cast, without selecting an overhead ceiling. */
    private static BlockPos surfaceBelow(ServerLevel level,BlockPos around,int depth) {
        for(int down=0;down<=depth;down++) {
            BlockPos pos=around.below(down);var state=level.getBlockState(pos);var above=level.getBlockState(pos.above());
            if((state.isFaceSturdy(level,pos,net.minecraft.core.Direction.UP)||state.is(Blocks.WATER))
                    &&(above.isAir()||above.canBeReplaced())&&!above.is(Blocks.WATER))return pos;
        }
        return null;
    }
    public static boolean isStreaming(ServerPlayer p) {return STREAMS.containsKey(p.getUUID());}
    public static boolean startStream(ServerPlayer p,int tier) {
        if(!PowerRules.canAct(p)||isStreaming(p))return false;
        STREAMS.put(p.getUUID(),new Stream(p,p.serverLevel(),Math.clamp(tier,1,5),p.serverLevel().getGameTime()+100));
        ArmPosePayload.show(p,ArmPose.CRYO_WEAVE,100);pulse(STREAMS.get(p.getUUID()));return true;
    }
    public static boolean stopStream(ServerPlayer p) {
        if(STREAMS.remove(p.getUUID())==null)return false;ArmPosePayload.clear(p);return true;
    }
    private static void pulse(Stream s) {
        var p=s.owner;var level=s.level;Vec3 eye=p.getEyePosition(),aim=p.getLookAngle();
        var hit=level.clip(new ClipContext(eye,eye.add(aim.scale(12)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
        double length=eye.distanceTo(hit.getLocation());
        for(LivingEntity target:enemies(p,eye,13)) {
            Vec3 delta=target.getBoundingBox().getCenter().subtract(eye);double along=delta.dot(aim);
            if(along<0||along>length+.3||delta.subtract(aim.scale(along)).lengthSqr()>.65+target.getBbWidth()*.5||!p.hasLineOfSight(target))continue;
            CryoService.coldHit(p,target,.8F+s.tier*.15F);
            CryoService.chill(target,18+(SkillTreeService.has(p,"cryokinesis_2_0")?8:0),p);
            AbilityEffects.send(level,Kind.CRYO_PRISON,target.position(),target.getEyePosition(),Math.max(.1,CryoService.frostPercent(target)/100.0));
        }
        if(hit.getType()==HitResult.Type.BLOCK) {
            BlockPos pos=hit.getBlockPos();
            if(level.getBlockState(pos).is(Blocks.WATER))TemporaryBlocks.freezeWater(level,pos,p);
            else if(level.getBlockState(pos.above()).isAir())TemporaryBlocks.frostSurface(level,pos,100,p);
        }
        Vec3 palm=eye.add(aim.scale(.65)).add(new Vec3(-aim.z,0,aim.x).scale(.24)).add(0,-.38,0);
        AbilityEffects.send(level,Kind.CRYO_RIME_STREAM,palm,hit.getLocation(),1);
        if(level.getGameTime()%10==0)level.playSound(null,p.blockPosition(),SoundEvents.POWDER_SNOW_BREAK,SoundSource.PLAYERS,.65F,1.45F);
    }
    public static int constructCount(ServerPlayer p) {return CONSTRUCTS.getOrDefault(p.getUUID(),List.of()).size();}
    public static List<BlockPos> constructBlocks(ServerPlayer p) {return CONSTRUCTS.getOrDefault(p.getUUID(),List.of()).stream().flatMap(c->c.blocks.stream()).toList();}
    public static boolean isAimingConstruct(ServerPlayer p) {
        var hit=p.serverLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(16)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK&&TemporaryBlocks.isTemporary(p.serverLevel(),hit.getBlockPos())
                &&CONSTRUCTS.getOrDefault(p.getUUID(),List.of()).stream().anyMatch(c->c.level==p.level()&&c.blocks.contains(hit.getBlockPos()));
    }
    /** Editing prepared ice has its own short cadence instead of waiting for the full new-wall cooldown. */
    public static boolean reshape(ServerPlayer p,int tier,boolean ramp) {
        if(!isAimingConstruct(p))return false;
        if(!PowerRules.canAct(p)||!authorized(p))return true;
        if(p.level().getGameTime()<RESHAPE_READY.getOrDefault(p.getUUID(),0L)) {
            p.displayClientMessage(Component.literal("The ice is still settling. Reshape again in a moment."),true);return true;
        }
        if(PlayerEnergyService.getEnergy(p)<8) {
            p.displayClientMessage(Component.literal("Reshaping needs 8 stamina."),true);return true;
        }
        if(sculpt(p,tier,ramp)) {
            PlayerEnergyService.tryConsume(p,8);MasteryService.awardEnergySpent(p,8);
        } else p.displayClientMessage(Component.literal("There is no clear space to reshape this ice."),true);
        return true;
    }
    public static boolean sculpt(ServerPlayer p,int tier,boolean ramp) {
        if(!PowerRules.canAct(p))return false;
        var level=p.serverLevel();var hit=level.clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(16)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        var list=CONSTRUCTS.computeIfAbsent(p.getUUID(),id->new ArrayList<>());
        Construct existing=list.stream().filter(c->c.level==level&&c.blocks.contains(hit.getBlockPos())).findFirst().orElse(null);
        Vec3 forward=flat(p),right=new Vec3(-forward.z,0,forward.x);
        String shape=ramp?"ramp":existing==null?"wall":existing.shape.equals("wall")?"curve":existing.shape.equals("curve")?"ramp":"wall";
        Vec3 proposed=hit.getType()==HitResult.Type.BLOCK?hit.getLocation():p.position().add(forward.scale(4));
        BlockPos ground=surfaceBelow(level,BlockPos.containing(proposed),5);
        if(existing!=null)proposed=existing.base;
        else if(ground!=null)proposed=Vec3.atBottomCenterOf(ground.above());
        else {p.displayClientMessage(Component.literal("Aim at ground or one of your ice constructs."),true);return false;}
        if(proposed.distanceToSqr(p.position())>18*18)return false;
        long now=level.getGameTime();List<Cell> cells=new ArrayList<>();
        int half=shape.equals("ramp")?1:tier>=5?3:2;
        for(int step=0;step<(shape.equals("ramp")?6:3);step++)for(int col=-half;col<=half;col++) {
            Vec3 point=shape.equals("ramp")?proposed.add(forward.scale(step)).add(right.scale(col)).add(0,step/2,0)
                    :proposed.add(right.scale(col)).add(forward.scale(shape.equals("curve")?col*col*.16:0)).add(0,step,0);
            BlockPos pos=BlockPos.containing(point);
            if(new AABB(pos).intersects(p.getBoundingBox()))continue;
            cells.add(new Cell(pos,now+step*3L+Math.abs(col)));
        }
        if(cells.isEmpty())return false;
        int total=CONSTRUCTS.values().stream().mapToInt(List::size).sum();
        if(total>=64&&existing==null&&list.size()<3)return false;
        long duration=(12+Math.clamp(tier,1,5)*2+(SkillTreeService.has(p,"cryokinesis_0_0")?4:0))*20L;
        // Fail a denied placement before consuming the cast, then let the remaining cells grow in order.
        Cell first=cells.getFirst();
        if(!TemporaryBlocks.place(level,first.pos,Blocks.PACKED_ICE.defaultBlockState(),(int)duration,p))return false;
        // Keep existing cover until the replacement has passed the real placement hooks.
        if(existing!=null){restoreExcept(existing,first.pos);list.remove(existing);}
        while(list.size()>=3)restoreExcept(list.removeFirst(),first.pos);
        Construct c=new Construct(p,proposed,shape,cells,now+duration);c.blocks.add(first.pos);c.pending.removeIf(cell->cell.pos.equals(first.pos));list.add(c);
        RESHAPE_READY.put(p.getUUID(),now+40);
        ArmPosePayload.show(p,ArmPose.CRYO_SCULPT,20);
        AbilityEffects.send(level,Kind.CRYO_SCULPT,proposed,proposed.add(0,3,0),1);
        Vec3 firstBase=Vec3.atBottomCenterOf(first.pos);
        AbilityEffects.send(level,Kind.CRYO_SCULPT,firstBase,firstBase.add(0,1,0),.6);
        level.playSound(null,p.blockPosition(),SoundEvents.GLASS_PLACE,SoundSource.PLAYERS,1,.7F);return true;
    }
    private static void restore(Construct c) {for(BlockPos pos:c.blocks)TemporaryBlocks.restore(c.level,pos,c.owner);c.pending.clear();}
    private static void restoreExcept(Construct c,BlockPos keep) {for(BlockPos pos:c.blocks)if(!pos.equals(keep))TemporaryBlocks.restore(c.level,pos,c.owner);c.pending.clear();}
    public static boolean hasPrison(ServerPlayer p) {Prison s=PRISONS.get(p.getUUID());return s!=null&&s.level==p.level()&&CryoService.isFrozen(s.target);}
    public static boolean prison(ServerPlayer p,int tier) {
        LivingEntity target=Targeting.livingEntityInSight(p,6,e->ExpansionSkills.hostile(p,e));
        if(target==null)return false;
        if(CryoService.frostAmount(target)<40&&!CryoService.isFrozen(target)) {
            CryoService.chill(target,40,p);
            p.displayClientMessage(Component.literal("Frost takes hold. Use Rime Stream, then seal the prison."),true);
            return CryoService.frostAmount(target)>0;
        }
        if(!CryoService.freezeSolid(target,target instanceof ServerPlayer?60:80+tier*8,p))return false;
        PRISONS.put(p.getUUID(),new Prison(p,target,p.serverLevel()));
        ArmPosePayload.show(p,ArmPose.CRYO_PRISON,18);AbilityEffects.send(p.serverLevel(),Kind.CRYO_PRISON,target.position(),target.getEyePosition(),1);return true;
    }
    public static boolean shatterPrison(ServerPlayer p) {
        Prison prison=PRISONS.remove(p.getUUID());if(prison==null)return false;
        if(!valid(p,prison.level)||!CryoService.isFrozen(prison.target)||p.distanceToSqr(prison.target)>16*16||!p.hasLineOfSight(prison.target))return true;
        fracture(p,prison.target.position(),3,8);ArmPosePayload.show(p,ArmPose.CRYO_SHATTER,12);return true;
    }
    private static void fracture(ServerPlayer p,Vec3 center,double radius,float damage) {
        for(LivingEntity target:enemies(p,center,radius)) {
            if(!visible(p.serverLevel(),center.add(0,.5,0),target))continue;
            if(CryoService.isFrozen(target)) CryoService.shatterHit(p,target,damage);
            else CryoService.coldHit(p,target,damage);
            if(SkillTreeService.has(p,"cryokinesis_2_2"))CryoService.chill(target,35,p);
        }
        AbilityEffects.send(p.serverLevel(),Kind.CRYO_FRACTURE,center,center.add(0,1,0),radius);
        p.serverLevel().playSound(null,BlockPos.containing(center),SoundEvents.GLASS_BREAK,SoundSource.PLAYERS,1.2F,.7F);
    }
    public static boolean hasWhiteout(ServerPlayer p) {return STORMS.containsKey(p.getUUID());}
    public static boolean whiteout(ServerPlayer p,int tier) {
        if(hasWhiteout(p)||!PowerRules.canAct(p))return false;
        var hit=p.serverLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(20)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        Vec3 center=hit.getType()==HitResult.Type.BLOCK?hit.getLocation():p.position().add(flat(p).scale(6));
        BlockPos ground=surfaceBelow(p.serverLevel(),BlockPos.containing(center),6);
        if(ground!=null)center=Vec3.atBottomCenterOf(ground.above());
        STORMS.put(p.getUUID(),new Storm(p,center,tier));ArmPosePayload.show(p,ArmPose.CRYO_WHITEOUT,60);
        AbilityEffects.send(p.serverLevel(),Kind.CRYO_WHITEOUT,center,center.add(0,1,0),1);return true;
    }
    private static void stormSync(Storm s,int radius) {
        for(ServerPlayer viewer:s.level.players())if(radius==0||viewer.position().distanceToSqr(s.center)<96*96)
            PacketDistributor.sendToPlayer(viewer,new BlizzardStatePayload(s.owner.getUUID(),s.center,radius,radius==0?0:28));
    }
    public static boolean endWhiteout(ServerPlayer p,boolean collapse) {
        Storm s=STORMS.remove(p.getUUID());if(s==null)return false;stormSync(s,0);
        if(collapse&&valid(p,s.level)&&s.level.getGameTime()-s.began>=20) {
            for(BlockPos pos:s.spires)TemporaryBlocks.restore(s.level,pos,p);
            var constructs=CONSTRUCTS.getOrDefault(p.getUUID(),List.of());
            for(Construct c:List.copyOf(constructs))if(c.level==s.level&&c.base.distanceToSqr(s.center)<=144) {
                restore(c);constructs.remove(c);
                AbilityEffects.send(s.level,Kind.CRYO_BRANCH,c.base,s.center,1);
                if(SkillTreeService.has(p,"cryokinesis_0_2"))for(LivingEntity e:enemies(p,c.base,3))if(visible(s.level,c.base.add(0,1,0),e))CryoService.chill(e,35,p);
            }
            // Collapse rewards prepared, frozen targets; it does not freeze and shatter fresh enemies in one cast.
            for(LivingEntity target:enemies(p,s.center,12))if(CryoService.isFrozen(target)&&visible(s.level,s.center.add(0,1,0),target)) {
                CryoService.shatterHit(p,target,10+s.tier);
                if(SkillTreeService.has(p,"cryokinesis_2_2"))for(LivingEntity near:enemies(p,target.position(),3))if(visible(s.level,target.getBoundingBox().getCenter(),near))CryoService.chill(near,35,p);
            }
            AbilityEffects.send(s.level,Kind.CRYO_FRACTURE,s.center,s.center.add(0,1,0),12);
            ArmPosePayload.show(p,ArmPose.CRYO_SHATTER,12);
        }
        return true;
    }
    public static boolean skill(AbilityUseContext c,int branch) {
        return CryoCombatSkills.activate(c,branch);
    }
    public static void tick(MinecraftServer server) {
        CryoCombatSkills.tick(server);
        for(Stream s:List.copyOf(STREAMS.values())) {
            long now=s.level.getGameTime();if(!valid(s.owner,s.level)||now>=s.until){stopStream(s.owner);continue;}
            if(now%5==0){if(!PlayerEnergyService.tryConsume(s.owner,2)){stopStream(s.owner);continue;}MasteryService.awardEnergySpent(s.owner,2);pulse(s);}
        }
        int budget=64;
        for(var entry:List.copyOf(CONSTRUCTS.entrySet())) {
            var list=entry.getValue();
            for(Construct c:List.copyOf(list)) {
                long now=c.level.getGameTime();if(!valid(c.owner,c.level)||now>=c.until){restore(c);list.remove(c);continue;}
                while(budget>0&&!c.pending.isEmpty()&&c.pending.peekFirst().at<=now) {
                    Cell cell=c.pending.removeFirst();budget--;
                    if(TemporaryBlocks.place(c.level,cell.pos,Blocks.PACKED_ICE.defaultBlockState(),(int)Math.max(1,c.until-now),c.owner)) {
                        c.blocks.add(cell.pos);Vec3 from=Vec3.atBottomCenterOf(cell.pos);
                        AbilityEffects.send(c.level,Kind.CRYO_SCULPT,from,from.add(0,1,0),.6);
                    }
                }
            }
            if(list.isEmpty())CONSTRUCTS.remove(entry.getKey());
        }
        PRISONS.values().removeIf(p->!valid(p.owner,p.level)||!CryoService.isFrozen(p.target));
        for(Storm s:List.copyOf(STORMS.values())) {
            long now=s.level.getGameTime(),age=now-s.began;
            if(!valid(s.owner,s.level)||now>=s.until){endWhiteout(s.owner,false);continue;}
            if(now%10!=0)continue;
            int radius=(int)Math.max(1,12*Math.clamp(age/20.0,0,1));stormSync(s,radius);
            AbilityEffects.send(s.level,Kind.CRYO_WHITEOUT,s.center,s.center.add(0,1,0),radius);
            if(age<20)continue;
            for(LivingEntity target:enemies(s.owner,s.center,12))if(visible(s.level,s.center.add(0,1,0),target)) {
                CryoService.coldHit(s.owner,target,1);CryoService.chill(target,20,s.owner);
            }
            if(now%20==0) {
                double angle=age*.17;Vec3 point=s.center.add(Math.cos(angle)*7,0,Math.sin(angle)*7);
                BlockPos ground=surfaceBelow(s.level,BlockPos.containing(point),4);
                if(ground!=null) {
                    TemporaryBlocks.frostSurface(s.level,ground,140,s.owner);
                    if(s.spires.size()<32)for(int height=1;height<=2;height++) {
                        BlockPos pos=ground.above(height);if(TemporaryBlocks.place(s.level,pos,Blocks.BLUE_ICE.defaultBlockState(),140,s.owner)) {
                            s.spires.add(pos);Vec3 base=Vec3.atBottomCenterOf(pos);
                            AbilityEffects.send(s.level,Kind.CRYO_SCULPT,base,base.add(0,1,0),.8);
                        }
                    }
                }
                for(Construct c:CONSTRUCTS.getOrDefault(s.owner.getUUID(),List.of()))if(c.base.distanceToSqr(s.center)<=144)
                    AbilityEffects.send(s.level,Kind.CRYO_BRANCH,s.center,c.base,1);
            }
        }
    }
    public static void forget(ServerPlayer p) {
        stopStream(p);CryoRideService.forget(p);CryoCombatSkills.forget(p);CryoTechniqueService.forget(p);PRISONS.remove(p.getUUID());RESHAPE_READY.remove(p.getUUID());
        Storm storm=STORMS.get(p.getUUID());if(storm!=null){for(BlockPos pos:storm.spires)TemporaryBlocks.restore(storm.level,pos,p);endWhiteout(p,false);}
        var constructs=CONSTRUCTS.remove(p.getUUID());if(constructs!=null)constructs.forEach(CryoKitService::restore);
    }
    public static void clear() {
        Set<ServerPlayer> owners=new HashSet<>();STREAMS.values().forEach(s->owners.add(s.owner));
        CONSTRUCTS.values().forEach(l->l.forEach(c->owners.add(c.owner)));STORMS.values().forEach(s->owners.add(s.owner));
        PRISONS.values().forEach(p->owners.add(p.owner));owners.forEach(CryoKitService::forget);
        STREAMS.clear();CONSTRUCTS.clear();RESHAPE_READY.clear();PRISONS.clear();STORMS.clear();CryoCombatSkills.clear();CryoTechniqueService.clear();CryoRideService.clear();
    }
}
