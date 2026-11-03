package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.*;

/** Guided construction, pressure-bearing rescue ice and Frost-sharing fracture networks. */
public final class CryoTechniqueService {
    private static final class Loom {
        final ServerPlayer owner; final ServerLevel level; final int length, width, baseFloor;
        final long expires; final Set<BlockPos> cells = new LinkedHashSet<>();
        Vec3 cursor, direction, desired; int steps, floor; long next, guideAt; boolean rise;
        Loom(ServerPlayer p) {
            owner=p;level=p.serverLevel();length=has(p,"cryokinesis_suspension_stitch")?24:16;
            width=has(p,"cryokinesis_double_weave")?3:1;
            cursor=p.position().add(0,-1,0);direction=desired=flat(p);floor=baseFloor=BlockPos.containing(cursor).getY();
            rise=has(p,"cryokinesis_suspension_stitch")&&p.getLookAngle().y>.25;
            next=level.getGameTime();expires=next+240+length*3L;
        }
    }
    private static final class Cocoon {
        final ServerPlayer owner; final LivingEntity target; final ServerLevel level; final Vec3 position;
        final long until; final float capacity; final boolean borrowed; float stored;
        Cocoon(ServerPlayer p,LivingEntity t,int tier) {
            owner=p;target=t;level=p.serverLevel();position=t.position();until=level.getGameTime()+(t instanceof ServerPlayer?60:120);
            capacity=12+Math.clamp(tier,1,5)*2;
            borrowed=com.jayptucker.heroesevolved.mimicry.MimicryService.isMimic(p)&&CryoKitService.authorized(p);
        }
    }
    private static final class Stars {
        final ServerPlayer owner; final ServerLevel level; final List<LivingEntity> targets; final long until;
        long sharedAt=Long.MIN_VALUE/2;
        Stars(ServerPlayer p,List<LivingEntity> targets) {
            owner=p;level=p.serverLevel();this.targets=targets;until=level.getGameTime()+(has(p,"cryokinesis_long_winter")?200:120);
        }
    }
    private static final class Chain {
        final ServerPlayer owner; final ServerLevel level; final Deque<LivingEntity> targets; final boolean strong;
        Vec3 source; long next;
        Chain(Stars stars,LivingEntity first) {
            owner=stars.owner;level=stars.level;source=first.getBoundingBox().getCenter();strong=has(owner,"cryokinesis_shattered_sky");
            targets=new ArrayDeque<>();var remaining=new ArrayList<>(stars.targets);remaining.remove(first);Vec3 cursor=source;
            while(!remaining.isEmpty()) {
                Vec3 from=cursor;
                LivingEntity closest=remaining.stream().min(Comparator.comparingDouble(t->t.position().distanceToSqr(from))).orElseThrow();
                targets.add(closest);remaining.remove(closest);cursor=closest.getBoundingBox().getCenter();
            }
            next=level.getGameTime()+4;
        }
    }
    private static final Map<UUID,Loom> LOOMS=new HashMap<>();
    private static final Map<UUID,Cocoon> COCOONS=new HashMap<>();
    private static final Map<UUID,Stars> STARS=new HashMap<>();
    private static final List<Chain> CHAINS=new ArrayList<>();
    private static boolean sharing;
    private CryoTechniqueService() {}
    private static boolean has(ServerPlayer p,String id){return SkillTreeService.has(p,id);}
    private static boolean valid(ServerPlayer p,ServerLevel level){return p.level()==level&&!p.isRemoved()&&CryoKitService.authorized(p)&&PowerRules.canAct(p);}
    private static boolean visible(ServerLevel level,Vec3 from,LivingEntity target){return level.clip(new ClipContext(from,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.MISS;}
    private static Vec3 flat(ServerPlayer p){var v=p.getLookAngle().multiply(1,0,1);return v.lengthSqr()<.001?Vec3.directionFromRotation(0,p.getYRot()):v.normalize();}
    private static void message(ServerPlayer p,String value){p.displayClientMessage(Component.literal(value),true);}
    public static boolean activate(AbilityUseContext c,String mechanic) {
        if(!valid(c.player(),c.player().serverLevel())||c.player().isPassenger())return false;
        return switch(mechanic) {
            case "rime_loom" -> startLoom(c.player());
            case "stillheart" -> startCocoon(c);
            case "brittle_constellation" -> startStars(c.player());
            default -> false;
        };
    }
    public static boolean followUp(AbilityUseContext c,String mechanic) {
        if(mechanic.equals("stillheart"))return releaseCocoon(c.player());
        if(!mechanic.equals("rime_loom"))return false;
        Loom loom=LOOMS.get(c.player().getUUID());
        if(loom==null||loom.steps>=loom.length||!valid(loom.owner,loom.level))return false;
        long now=loom.level.getGameTime();
        if(now-loom.guideAt>=4) {
            loom.desired=flat(c.player());loom.rise=has(c.player(),"cryokinesis_suspension_stitch")&&c.player().getLookAngle().y>.25;
            loom.guideAt=now;ArmPosePayload.show(c.player(),ArmPose.CRYO_LOOM,12);
        }
        return true;
    }
    private static boolean startLoom(ServerPlayer p) {
        if(LOOMS.containsKey(p.getUUID())||LOOMS.size()>=32)return false;
        Loom loom=new Loom(p);
        if(!lay(loom))return false;
        LOOMS.put(p.getUUID(),loom);ArmPosePayload.show(p,ArmPose.CRYO_LOOM,30);
        message(p,"Rime Loom is growing. Aim and press again to bend the remaining weave.");return true;
    }
    private static boolean cell(Loom loom,BlockPos pos) {
        ServerLevel level=loom.level;
        if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var state=level.getBlockState(pos);boolean temporary=TemporaryBlocks.isTemporary(level,pos);
        // Existing ice is a usable foundation, never a new owned temporary block.
        if(!temporary&&state.is(net.minecraft.tags.BlockTags.ICE))
            return com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(loom.owner,level,pos);
        boolean placed=temporary?state.is(net.minecraft.tags.BlockTags.ICE)&&TemporaryBlocks.refresh(level,pos,240,loom.owner)
                :state.isFaceSturdy(level,pos,Direction.UP)&&!state.is(Blocks.WATER)
                ?TemporaryBlocks.frostSurface(level,pos,240,loom.owner)
                :TemporaryBlocks.place(level,pos,Blocks.PACKED_ICE.defaultBlockState(),240,loom.owner);
        if(placed&&(!temporary||loom.cells.contains(pos)))loom.cells.add(pos.immutable());
        if(placed){Vec3 base=Vec3.atBottomCenterOf(pos);AbilityEffects.send(level,Kind.CRYO_SCULPT,base,base.add(0,1,0),.6);}
        return placed;
    }
    private static boolean lay(Loom loom) {
        Vec3 prior=loom.cursor;
        double heading=Math.atan2(loom.direction.z,loom.direction.x),wanted=Math.atan2(loom.desired.z,loom.desired.x);
        double turn=Math.atan2(Math.sin(wanted-heading),Math.cos(wanted-heading));
        heading+=Math.clamp(turn,-Math.PI/6,Math.PI/6);loom.direction=new Vec3(Math.cos(heading),0,Math.sin(heading));
        Vec3 next=prior.add(loom.direction);
        if(loom.rise&&loom.steps>0&&loom.steps%4==0&&loom.floor<loom.baseFloor+6)loom.floor++;
        BlockPos center=BlockPos.containing(next).atY(loom.floor);
        if(!cell(loom,center))return false;
        // A diagonal weave fills every crossed cell, keeping collision continuous through its bend.
        BlockPos middle=BlockPos.containing(prior.lerp(next,.5)).atY(loom.floor);
        if(!middle.equals(center)&&!middle.equals(BlockPos.containing(prior).atY(loom.floor)))cell(loom,middle);
        if(loom.width==3) {
            Vec3 side=new Vec3(-loom.direction.z,0,loom.direction.x);
            for(int sign:new int[]{-1,1})cell(loom,BlockPos.containing(next.add(side.scale(sign))).atY(loom.floor));
        }
        loom.cursor=new Vec3(next.x,loom.floor,next.z);loom.steps++;loom.next=loom.level.getGameTime()+3;
        AbilityEffects.send(loom.level,Kind.CRYO_LOOM,prior.add(0,1.02,0),loom.cursor.add(0,1.02,0),loom.width);
        return true;
    }
    private static boolean startCocoon(AbilityUseContext c) {
        ServerPlayer p=c.player();LivingEntity target=c.modifierHeld()||p.isShiftKeyDown()?p:Targeting.livingEntityInSight(p,6,t->ExpansionSkills.friendly(p,t));
        if(target==null)target=p;
        if(COCOONS.containsKey(target.getUUID())||COCOONS.values().stream().anyMatch(s->s.owner==p)||COCOONS.size()>=16)return false;
        Cocoon cocoon=new Cocoon(p,target,c.abilityLevel());COCOONS.put(target.getUUID(),cocoon);
        if(target instanceof Mob mob){mob.getNavigation().stop();mob.setTarget(null);}
        target.clearFire();
        if(has(p,"cryokinesis_sterile_ice"))for(var effect:List.copyOf(target.getActiveEffects()))
            if(effect.getEffect().value().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL)target.removeEffect(effect.getEffect());
        ArmPosePayload.show(p,ArmPose.CRYO_SHELTER,20);shell(cocoon);
        message(p,"Stillheart catches impacts in its shell. Press again to open it and release the stored cold.");return true;
    }
    private static boolean ownerOfCocoonValid(Cocoon c) {
        // A valid Echo was proven before preservation locked actions. Its release still requires the
        // same remembered power; borrowed-session revocation explicitly forgets the technique.
        boolean borrowed=c.borrowed&&com.jayptucker.heroesevolved.mimicry.MimicryService.isMimic(c.owner)
                &&com.jayptucker.heroesevolved.ability.registry.ModAbilities.CRYOKINESIS_ID.equals(
                        com.jayptucker.heroesevolved.mimicry.MimicryService.memory(c.owner).current());
        return c.owner.level()==c.level&&c.target.level()==c.level&&c.owner.isAlive()&&!c.owner.isRemoved()
                &&c.target.isAlive()&&!c.target.isRemoved()&&(CryoKitService.authorized(c.owner)||borrowed)
                &&PowerRules.canActWithoutPreservation(c.owner);
    }
    public static boolean canReleaseCocoon(ServerPlayer p) {
        Cocoon enclosing=COCOONS.get(p.getUUID());
        if(enclosing!=null&&enclosing.owner!=p)return false;
        return COCOONS.values().stream().anyMatch(c->c.owner==p&&ownerOfCocoonValid(c));
    }
    public static boolean releaseCocoon(ServerPlayer p) {
        if(!canReleaseCocoon(p))return false;
        Cocoon cocoon=COCOONS.values().stream().filter(c->c.owner==p&&ownerOfCocoonValid(c)).findFirst().orElse(null);
        if(cocoon==null)return false;open(cocoon,true);return true;
    }
    private static void shell(Cocoon c){AbilityEffects.send(c.level,Kind.CRYO_COCOON,c.position,c.position.add(0,c.target.getBbHeight(),0),c.stored/c.capacity);}
    private static void open(Cocoon c,boolean recover) {
        COCOONS.remove(c.target.getUUID());
        AbilityEffects.send(c.level,Kind.CRYO_COCOON,c.position,c.position.add(0,c.target.getBbHeight(),0),-1);
        if(!recover||!ownerOfCocoonValid(c))return;
        c.target.heal((has(c.owner,"cryokinesis_second_spring")?6:2)+Math.min(4,c.stored*.2F));
        if(has(c.owner,"cryokinesis_second_spring"))for(LivingEntity friend:c.level.getEntitiesOfClass(LivingEntity.class,c.target.getBoundingBox().inflate(5),
                t->t!=c.target&&ExpansionSkills.friendly(c.owner,t)&&visible(c.level,c.target.getEyePosition(),t)))friend.heal(2+Math.min(2,c.stored*.1F));
        int frost=20+(int)Math.min(60,c.stored*3);
        for(LivingEntity enemy:ExpansionSkills.enemies(c.owner,c.position,4))
            if(enemy!=c.target&&!ExpansionSkills.friendly(c.owner,enemy)&&visible(c.level,c.target.getEyePosition(),enemy))
                CryoService.chill(enemy,frost,c.owner);
        AbilityEffects.send(c.level,Kind.CRYO_FRACTURE,c.position,c.position.add(0,1,0),2+c.stored/c.capacity*2);
        ArmPosePayload.show(c.owner,ArmPose.CRYO_SHELTER,12);
    }
    private static boolean startStars(ServerPlayer p) {
        if(!STARS.containsKey(p.getUUID())&&STARS.size()>=32)return false;
        LivingEntity aimed=Targeting.livingEntityInSight(p,10,t->ExpansionSkills.hostile(p,t));
        Vec3 center=aimed==null?Vec3.atCenterOf(ExpansionSkills.aim(p)):aimed.position();
        var targets=ExpansionSkills.enemies(p,center,8).stream().limit(4).toList();if(targets.isEmpty())return false;
        Stars stars=new Stars(p,targets);STARS.put(p.getUUID(),stars);
        sharing=true;
        try{for(LivingEntity target:targets)CryoService.chill(target,30,p);}finally{sharing=false;}
        ArmPosePayload.show(p,ArmPose.CRYO_STARS,24);starVisuals(stars);return true;
    }
    public static void shareFrost(ServerPlayer caster,LivingEntity origin,int amount) {
        if(sharing||caster==null||amount<=0)return;
        Stars stars=STARS.get(caster.getUUID());
        if(stars==null||origin.level()!=stars.level||stars.level.getGameTime()>=stars.until||!stars.targets.contains(origin)||!valid(caster,stars.level))return;
        long now=stars.level.getGameTime();if(now-stars.sharedAt<4)return;stars.sharedAt=now;
        sharing=true;
        try {
            for(LivingEntity target:stars.targets)if(target!=origin&&ExpansionSkills.hostile(caster,target)&&target.level()==stars.level
                    &&origin.distanceToSqr(target)<=144&&visible(stars.level,origin.getBoundingBox().getCenter(),target)) {
                CryoService.chill(target,Math.clamp(amount/3,1,15),caster);
                AbilityEffects.send(stars.level,Kind.CRYO_CONSTELLATION,origin.getEyePosition(),target.getEyePosition(),1);
            }
        }finally{sharing=false;}
    }
    /** Returns true when a protective cocoon handled the incoming damage. */
    public static boolean damage(LivingIncomingDamageEvent event) {
        if(event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        Cocoon c=COCOONS.get(event.getEntity().getUUID());
        if(c!=null&&ownerOfCocoonValid(c)) {
            float caught=Math.min(Math.max(0,event.getAmount())*.8F,c.capacity-c.stored);
            c.stored+=caught;event.setAmount(event.getAmount()-caught);shell(c);
            if(c.stored>=c.capacity)open(c,true);return true;
        }
        if(event.getAmount()<=0||event.getSource().is(DamageTypeTags.IS_FREEZING)||CryoService.isApplyingColdDamage()||!CryoService.isFrozen(event.getEntity()))return false;
        for(Stars stars:List.copyOf(STARS.values()))if(event.getEntity().level()==stars.level&&stars.level.getGameTime()<stars.until
                &&stars.targets.contains(event.getEntity())&&ExpansionSkills.hostile(stars.owner,event.getEntity())&&valid(stars.owner,stars.level)) {
            STARS.remove(stars.owner.getUUID());
            if(CHAINS.size()<32)CHAINS.add(new Chain(stars,event.getEntity()));
        }
        return false;
    }
    private static void starVisuals(Stars stars) {
        var targets=stars.targets.stream().filter(t->t.isAlive()&&t.level()==stars.level&&ExpansionSkills.hostile(stars.owner,t)).toList();
        for(int i=0;i<targets.size();i++) {
            LivingEntity a=targets.get(i),b=targets.get((i+1)%targets.size());
            Vec3 from=a.getEyePosition().add(0,.5,0),to=b.getEyePosition().add(0,.5,0);
            if(a==b||a.distanceToSqr(b)<=144&&visible(stars.level,a.getBoundingBox().getCenter(),b))
                AbilityEffects.send(stars.level,Kind.CRYO_CONSTELLATION,from,to,CryoService.frostPercent(a)/100.0);
        }
    }
    public static void tick(MinecraftServer server) {
        for(Loom loom:List.copyOf(LOOMS.values())) {
            long now=loom.level.getGameTime();
            if(!valid(loom.owner,loom.level)||now>=loom.expires){remove(loom);continue;}
            if(loom.steps<loom.length&&now>=loom.next&&!lay(loom))loom.steps=loom.length;
        }
        for(Cocoon c:List.copyOf(COCOONS.values())) {
            if(!ownerOfCocoonValid(c)){open(c,false);continue;}
            if(c.level.getGameTime()>=c.until){open(c,true);continue;}
            if(c.target instanceof ServerPlayer p&&p.position().distanceToSqr(c.position)>.001)p.connection.teleport(c.position.x,c.position.y,c.position.z,p.getYRot(),p.getXRot());
            c.target.setPos(c.position);c.target.setDeltaMovement(Vec3.ZERO);
            if(c.target instanceof Mob mob){mob.getNavigation().stop();mob.setTarget(null);}
            if(c.level.getGameTime()%5==0)shell(c);
        }
        for(Stars stars:List.copyOf(STARS.values())) {
            if(!valid(stars.owner,stars.level)||stars.level.getGameTime()>=stars.until){STARS.remove(stars.owner.getUUID());continue;}
            if(stars.level.getGameTime()%5==0)starVisuals(stars);
        }
        for(Chain chain:List.copyOf(CHAINS)) {
            if(!valid(chain.owner,chain.level)||chain.targets.isEmpty()){CHAINS.remove(chain);continue;}
            if(chain.level.getGameTime()<chain.next)continue;
            LivingEntity target=chain.targets.removeFirst();
            if(target.level()!=chain.level||!ExpansionSkills.hostile(chain.owner,target)||target.position().distanceToSqr(chain.source)>144
                    ||!visible(chain.level,chain.source,target)){CHAINS.remove(chain);continue;}
            boolean prepared=CryoService.frostAmount(target)>=40;
            if(CryoService.isFrozen(target))CryoService.shatterHit(chain.owner,target,chain.strong?7:4);
            else CryoService.coldHit(chain.owner,target,chain.strong?7:4);
            CryoService.chill(target,35,chain.owner);
            if(chain.strong&&prepared&&target instanceof Mob&&target.isAlive())CryoService.freezeSolid(target,40,chain.owner);
            AbilityEffects.send(chain.level,Kind.CRYO_CONSTELLATION,chain.source,target.getEyePosition(),1);
            AbilityEffects.send(chain.level,Kind.CRYO_FRACTURE,target.position(),target.getEyePosition(),1);
            chain.source=target.getBoundingBox().getCenter();chain.next=chain.level.getGameTime()+4;
            if(chain.targets.isEmpty())CHAINS.remove(chain);
        }
    }
    private static void remove(Loom loom){LOOMS.remove(loom.owner.getUUID());for(BlockPos pos:loom.cells)TemporaryBlocks.restore(loom.level,pos,loom.owner);}
    public static boolean preserved(Entity entity){Cocoon c=COCOONS.get(entity.getUUID());return c!=null&&c.target==entity&&entity.isAlive()&&entity.level()==c.level;}
    public static int activeFields(){return LOOMS.size()+COCOONS.size()+STARS.size()+CHAINS.size();}
    public static boolean isWeaving(ServerPlayer p){Loom l=LOOMS.get(p.getUUID());return l!=null&&l.steps<l.length;}
    public static List<BlockPos> loomBlocks(ServerPlayer p){Loom l=LOOMS.get(p.getUUID());return l==null?List.of():List.copyOf(l.cells);}
    public static float cocoonPressure(LivingEntity target){Cocoon c=COCOONS.get(target.getUUID());return c==null?0:c.stored;}
    public static int linkedCount(ServerPlayer p){Stars s=STARS.get(p.getUUID());return s==null?0:s.targets.size();}
    public static int chainCount(){return CHAINS.size();}
    public static void forget(ServerPlayer p) {
        Loom loom=LOOMS.get(p.getUUID());if(loom!=null)remove(loom);
        for(Cocoon c:List.copyOf(COCOONS.values()))if(c.owner==p||c.target==p)open(c,false);
        STARS.remove(p.getUUID());CHAINS.removeIf(c->c.owner==p);
    }
    public static void clear(){for(Loom l:List.copyOf(LOOMS.values()))remove(l);for(Cocoon c:List.copyOf(COCOONS.values()))open(c,false);STARS.clear();CHAINS.clear();sharing=false;}
}
