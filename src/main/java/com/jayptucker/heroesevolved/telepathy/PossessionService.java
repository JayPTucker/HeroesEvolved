package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Real-body possession. Inventory and interactions execute through the victim's normal server handler. */
public final class PossessionService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> RESISTANT = new HashMap<>();
    private static boolean forwarding, mirroring;
    static final class Session {
        final ServerPlayer owner; final LivingEntity body; final int tier; final long started; final Vec3 anchor;
        final boolean sight; final boolean noAi;
        int resistance, cue, nonce; long lastPress = -100, lastInput, lastAttack = -100; boolean active;
        float forward, strafe; boolean jump, sneak;
        final Set<Integer> heldKeys = new HashSet<>(); final Set<String> heldActions = new HashSet<>();
        Session(ServerPlayer p, LivingEntity b, int t, boolean sight) {
            owner=p; body=b; tier=t; this.sight=sight; started=now(p); anchor=p.position();
            noAi=b instanceof Mob m && m.isNoAi(); cue=p.getRandom().nextInt(4); nonce=p.getRandom().nextInt(Integer.MAX_VALUE-1000);
        }
    }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    public static boolean controlling(ServerPlayer p) { Session s=SESSIONS.get(p.getUUID()); return s!=null && s.active && !s.sight; }
    public static boolean occupied(ServerPlayer p) { return SESSIONS.containsKey(p.getUUID()) || victim(p)!=null; }
    private static Session victim(ServerPlayer p) { return SESSIONS.values().stream().filter(s->s.body==p && !s.sight).findFirst().orElse(null); }
    public static boolean locked(ServerPlayer p) { return !forwarding && (SESSIONS.containsKey(p.getUUID()) || victim(p)!=null && victim(p).active); }
    public static ServerPlayer actionActor(ServerPlayer p) {
        if (forwarding) return p;
        Session s=SESSIONS.get(p.getUUID());
        if (s!=null) return s.active && !s.sight && s.body instanceof ServerPlayer b ? b : null;
        return victim(p)!=null && victim(p).active ? null : p;
    }
    public static boolean start(ServerPlayer owner, LivingEntity body, int tier, boolean sight) {
        if (body==null || body==owner || !body.isAlive() || body.level()!=owner.level() || occupied(owner)
            || !PowerRules.canAct(owner) || owner.isPassenger() || body.isPassenger() || !owner.hasLineOfSight(body)
            || body.distanceToSqr(owner)>48*48 || body instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon || body instanceof net.minecraft.world.entity.boss.wither.WitherBoss) return false;
        if(!sight && SESSIONS.values().stream().anyMatch(s -> !s.sight && s.body==body))return false;
        if (body instanceof ServerPlayer p && (occupied(p) || p.isCreative() || p.isSpectator() || !PowerRules.canAct(p)
            || !sight && (!owner.canHarmPlayer(p) || !owner.server.isPvpAllowed() || RESISTANT.getOrDefault(p.getUUID(),0L)>now(owner)))) return false;
        if (!sight && TelepathyFocus.focus(owner,body)<60) {
            owner.displayClientMessage(Component.literal("Hold Read Mind until the thread reaches 60 Focus."),true); return false;
        }
        owner.closeContainer();
        Session s=new Session(owner,body,tier,sight); SESSIONS.put(owner.getUUID(),s);
        if (sight) s.active=true;
        sync(s); return true;
    }
    public static void input(ServerPlayer p, MindInputPayload in) {
        if(in.operation()==3){SuggestionService.choose(p,in.cue());return;}
        if(in.operation()==4){IllusionService.dissolve(p,in.cue());return;}
        if(in.operation()==5){IllusionService.choose(p,in.cue());return;}
        if (in.operation()==1) {
            Session s=victim(p);
            if (s==null || in.nonce()!=s.nonce || now(p)-s.lastPress<5 || in.cue()<0 || in.cue()>3) return;
            s.lastPress=now(p);
            if(in.cue()==s.cue) s.resistance=Math.min(100,s.resistance+MentalContest.gain(PlayerProgressionService.getPowerLevel(s.owner),PlayerProgressionService.getPowerLevel(p)));
            else s.resistance=Math.max(0,s.resistance-5);
            s.cue=(s.cue+1+p.getRandom().nextInt(3))%4; s.nonce++;
            if(s.resistance>=100) end(s.owner,"The mind thread snaps. You are free."); else sync(s);
            return;
        }
        Session s=SESSIONS.get(p.getUUID()); if(s==null) return;
        if(in.operation()==2) { end(p,"You return to your own mind."); return; }
        if(in.operation()==6 && s.active && !s.sight && s.body instanceof Mob mob && now(p)-s.lastAttack>=10){
            if(!Float.isFinite(in.yaw()) || !Float.isFinite(in.pitch()))return;
            mob.setYRot(in.yaw()%360);mob.setXRot(Math.clamp(in.pitch(),-90,90));mob.setYHeadRot(mob.getYRot());
            s.lastAttack=now(p);
            Vec3 from=mob.getEyePosition(), to=from.add(mob.getLookAngle().scale(3));
            var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mob,from,to,mob.getBoundingBox().expandTowards(mob.getLookAngle().scale(3)).inflate(1),
                target->target instanceof LivingEntity && target!=mob && target!=p && target.isAlive() && mob.hasLineOfSight(target)
                && (!(target instanceof ServerPlayer other)||p.server.isPvpAllowed()&&p.canHarmPlayer(other)),9);
            if(hit!=null){
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if(mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)!=null)mob.doHurtTarget(hit.getEntity());
                else hit.getEntity().hurt(mob.damageSources().mobAttack(mob),2.0f);
            }return;
        }
        if(in.operation()!=0 || !s.active || s.sight || !Float.isFinite(in.forward()) || !Float.isFinite(in.strafe()) || !Float.isFinite(in.yaw()) || !Float.isFinite(in.pitch())) return;
        s.forward=Math.clamp(in.forward(),-1,1); s.strafe=Math.clamp(in.strafe(),-1,1); s.lastInput=now(p);
        s.body.setSprinting(in.cue()==1 && !in.sneak());
        s.jump=in.jump(); s.sneak=in.sneak(); s.body.setYRot(in.yaw()%360); s.body.setXRot(Math.clamp(in.pitch(),-90,90)); s.body.setYHeadRot(s.body.getYRot());
    }
    public static void tick(MinecraftServer server) {
        RESISTANT.entrySet().removeIf(e->e.getValue()<server.overworld().getGameTime());
        for(Session s:List.copyOf(SESSIONS.values())) {
            long age=now(s.owner)-s.started;
            if(!s.owner.isAlive() || !s.body.isAlive() || s.owner.hasDisconnected() || s.body.isRemoved()
                || s.body instanceof ServerPlayer p && p.hasDisconnected() || s.owner.level()!=s.body.level()
                || s.owner.distanceToSqr(s.body)>64*64 || !PowerRules.canAct(s.owner)
                || s.body instanceof ServerPlayer p && !PowerRules.canAct(p)
                || age> (s.sight?100:MentalContest.windup(s.tier)+MentalContest.duration(s.tier))) { end(s.owner,"The connection fades."); continue; }
            if(!s.active && !s.owner.hasLineOfSight(s.body)) {end(s.owner,"The mind slips out of reach.");continue;}
            s.owner.setDeltaMovement(Vec3.ZERO);
            if(s.owner.position().distanceToSqr(s.anchor)>.01) s.owner.connection.teleport(s.anchor.x,s.anchor.y,s.anchor.z,s.owner.getYRot(),s.owner.getXRot());
            if(!s.active && age>=MentalContest.windup(s.tier)) {
                s.active=true;
                if(s.body instanceof Mob m) {m.setNoAi(true);m.getNavigation().stop();}
                if(s.body instanceof ServerPlayer b) {
                    b.closeContainer(); b.stopUsingItem();
                    mirrorInventory(b); PlayerPowerSyncService.forget(b); PlayerPowerSyncService.sync(b);
                }
            }
            if(s.active && !s.sight) {
                if(now(s.owner)-s.lastInput>5) {s.forward=0;s.strafe=0;s.jump=false;}
                s.body.setShiftKeyDown(s.sneak);
                if(s.jump && s.body.onGround()) s.body.setDeltaMovement(s.body.getDeltaMovement().x,.42,s.body.getDeltaMovement().z);
                travel(s,new Vec3(s.strafe*(s.sneak?.3:1),0,s.forward*(s.sneak?.3:1)));
                if(s.body instanceof ServerPlayer b && age%2==0) b.connection.teleport(b.getX(),b.getY(),b.getZ(),b.getYRot(),b.getXRot());
            }
            if(age%4==0) {
                AbilityEffects.send(s.owner.serverLevel(),AbilityEffectPayload.Kind.MIND_LINK,s.owner.getEyePosition(),s.body.getEyePosition(),s.active?1.4:.7);
                com.jayptucker.heroesevolved.network.ArmPosePayload.show(s.owner,com.jayptucker.heroesevolved.ability.ArmPose.TEMPLE,8);
                sync(s);
            }
        }
    }
    private static void travel(Session session, Vec3 input) {
        if (!(session.body instanceof Mob mob)) { session.body.travel(input); return; }
        // NoAI also disables Entity.isControlledByLocalInstance(), so ordinary travel becomes a no-op.
        // Enable only the synchronous physics step. No brain, goals or navigation are ticked here.
        boolean noAi = mob.isNoAi();
        float speed = mob.getSpeed();
        try {
            mob.setNoAi(false);
            mob.setSpeed((float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
            mob.travel(input);
        } finally {
            mob.setSpeed(speed);
            mob.setXxa(0); mob.setYya(0); mob.setZza(0);
            mob.setNoAi(noAi);
        }
    }
    private static void sync(Session s) {
        int left=(int)Math.max(0,(s.sight?100:MentalContest.windup(s.tier)+MentalContest.duration(s.tier))-(now(s.owner)-s.started));
        PacketDistributor.sendToPlayer(s.owner,new MindStatePayload(s.sight?4:s.active?2:1,s.body.getId(),left,s.resistance,s.cue,s.nonce,TelepathyFocus.focus(s.owner,s.body),TelepathyFocus.strain(s.owner)));
        if(!s.sight && s.body instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p,new MindStatePayload(3,s.owner.getId(),left,s.resistance,s.cue,s.nonce,s.active?1:0,0));
    }
    public static void hurt(ServerPlayer p,float amount) { if(amount>=2 && SESSIONS.containsKey(p.getUUID())) end(p,"Pain tears you back into your own body."); }
    public static void forget(ServerPlayer p) { Session v=victim(p); if(v!=null) end(v.owner,"The connection ends."); end(p,"The connection ends."); }
    public static void clear() { for(Session s:List.copyOf(SESSIONS.values())) end(s.owner,"The connection ends."); RESISTANT.clear(); }
    public static void end(ServerPlayer owner,String reason) {
        Session s=SESSIONS.remove(owner.getUUID()); if(s==null)return;
        if(!s.sight) {
            if(s.body instanceof Mob m) m.setNoAi(s.noAi);
            s.body.setShiftKeyDown(false); s.body.setSprinting(false);
            if(s.body instanceof ServerPlayer p) {
                // Release the exact held inputs before restoring either UI; toggles keep their native ownership.
                s.heldKeys.forEach(k->AbilityUseService.releaseKey(p,k)); s.heldActions.forEach(k->AbilityUseService.releaseAction(p,k));
                com.jayptucker.heroesevolved.flight.FlightService.setForwardInput(p,false);
                p.stopUsingItem(); p.closeContainer(); RESISTANT.put(p.getUUID(),now(owner)+400);
                PacketDistributor.sendToPlayer(p,new MindStatePayload(0,-1,0,0,0,0,0,0));
                p.displayClientMessage(Component.literal(reason),true); p.inventoryMenu.sendAllDataToRemote();
            }
        }
        ArmPosePayload.clear(owner);
        owner.closeContainer(); owner.inventoryMenu.sendAllDataToRemote();
        owner.connection.send(new ClientboundSetCarriedItemPacket(owner.getInventory().selected));
        owner.connection.send(new ClientboundSetHealthPacket(owner.getHealth(),owner.getFoodData().getFoodLevel(),owner.getFoodData().getSaturationLevel()));
        PlayerPowerSyncService.forget(owner); PlayerPowerSyncService.sync(owner);
        PacketDistributor.sendToPlayer(owner,new MindStatePayload(0,-1,0,0,0,0,TelepathyFocus.focus(owner,s.body),TelepathyFocus.strain(owner)));
        owner.displayClientMessage(Component.literal(reason),true);
        AbilityEffects.send(owner.serverLevel(),AbilityEffectPayload.Kind.MIND_PULSE,s.body.getEyePosition(),owner.getEyePosition(),1.5);
    }
    public static void action(ServerPlayer sender, ActionInputPayload p) {
        ServerPlayer actor=actionActor(sender); if(actor==null)return;
        Session s=SESSIONS.get(sender.getUUID());
        if(s!=null) { if(p.key()==0) {if(p.down())s.heldActions.add(p.actionKey());else s.heldActions.remove(p.actionKey());} else {if(p.down())s.heldKeys.add(p.key());else s.heldKeys.remove(p.key());} }
        if(p.down()) PowerFeedback.report(actor,p.key()==0?AbilityUseService.activateAction(actor,p.actionKey(),p.overexert()):AbilityUseService.activateKey(actor,p.key(),p.overexert()));
        else if(p.key()==0) AbilityUseService.releaseAction(actor,p.actionKey()); else AbilityUseService.releaseKey(actor,p.key());
    }
    /** Whitelisted gameplay packets only. Chat, commands, creative slots and permissions never transfer. */
    public static boolean route(ServerPlayer sender, Packet<ServerGamePacketListener> packet) {
        if(forwarding || !sender.server.isSameThread())return false;
        Session s=SESSIONS.get(sender.getUUID());
        if(s==null)return victim(sender)!=null && victim(sender).active;
        if(!s.active || s.sight || !(s.body instanceof ServerPlayer body))return true;
        forwarding=true;
        try {packet.handle(body.connection);} finally {forwarding=false;}
        return true;
    }
    private static boolean mirrored(Packet<?> p) {
        if(p instanceof ClientboundBlockChangedAckPacket || p instanceof ClientboundContainerSetContentPacket || p instanceof ClientboundContainerSetSlotPacket || p instanceof ClientboundContainerSetDataPacket || p instanceof ClientboundOpenScreenPacket || p instanceof ClientboundContainerClosePacket || p instanceof ClientboundSetCarriedItemPacket || p instanceof ClientboundSetHealthPacket) return true;
        if(p instanceof ClientboundCustomPayloadPacket c) return c.payload() instanceof PlayerPowerSyncPayload || c.payload() instanceof LoadoutPayload || c.payload() instanceof MomentumPayload || c.payload() instanceof ResonancePayload || c.payload() instanceof ClarityPayload || c.payload() instanceof StrainPayload || c.payload() instanceof DensityPayload || c.payload() instanceof RadiationStatePayload || c.payload() instanceof RegenerationStatePayload || c.payload() instanceof PyroStatePayload || c.payload() instanceof OpticalStatePayload;
        return false;
    }
    /** Returns true only for the controller's superseded personal UI updates. */
    public static boolean mirror(ServerPlayer recipient,Packet<?> packet) {
        if(mirroring || !recipient.server.isSameThread() || !mirrored(packet))return false;
        Session own=SESSIONS.get(recipient.getUUID());
        if(own!=null && own.active && !own.sight && own.body instanceof ServerPlayer)return true;
        Session s=victim(recipient);
        if(s!=null && s.active) {mirroring=true;try{s.owner.connection.send(packet);}finally{mirroring=false;}}
        return false;
    }
    private static void mirrorInventory(ServerPlayer p) { p.inventoryMenu.sendAllDataToRemote(); p.connection.send(new ClientboundSetCarriedItemPacket(p.getInventory().selected)); p.connection.send(new ClientboundSetHealthPacket(p.getHealth(),p.getFoodData().getFoodLevel(),p.getFoodData().getSaturationLevel())); }
    private PossessionService(){}
}
