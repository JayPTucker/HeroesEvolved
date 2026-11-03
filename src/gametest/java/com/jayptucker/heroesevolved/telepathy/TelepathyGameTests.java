package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelepathyGameTests {
    private record Actor(ServerPlayer player,List<Packet<?>> packets,boolean previousPvp){}
    private static Actor player(GameTestHelper h,int z){
        boolean previousPvp=h.getLevel().getServer().isPvpAllowed();
        h.getLevel().getServer().setPvpAllowed(true);
        List<Packet<?>> packets=new ArrayList<>();
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"mind-test")){
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return !isAlliedTo(other);}
        };
        try {
            Connection connection=p.connection.getConnection();var f=Connection.class.getDeclaredField("channel");f.setAccessible(true);if(connection.channel()==null)f.set(connection,new EmbeddedChannel());
            p.connection=new ServerGamePacketListenerImpl(p.server,connection,p,CommonListenerCookie.createInitial(p.getGameProfile(),false)){
                @Override public void send(Packet<?> packet){packets.add(packet);}
            };
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3,35,z))));p.setNoGravity(true);p.setYRot(0);p.setXRot(0);p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().assignDormant(ModAbilities.TELEPATHY_ID).unlock(ModAbilities.TELEPATHY_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.TELEPATHY_ID));
        PlayerProgressionService.setLevel(p,5);PlayerEnergyService.restore(p,PlayerEnergyService.getMaximumEnergy(p));h.getLevel().addNewPlayer(p);
        return new Actor(p,packets,previousPvp);
    }
    private static void clean(Actor... actors){for(int i=actors.length-1;i>=0;i--){Actor a=actors[i];TelepathyService.stopAll(a.player);a.player.discard();a.player.server.setPvpAllowed(a.previousPvp);}}
    private static MindStatePayload state(Actor a){return a.packets.stream().filter(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof MindStatePayload).map(p->(MindStatePayload)((ClientboundCustomPayloadPacket)p).payload()).reduce((x,y)->y).orElseThrow();}
    private static void focus(GameTestHelper h,Actor a,Actor b){h.assertTrue(TelepathyFocus.start(a.player,5),"Read Mind finds a real mind");for(int i=0;i<35;i++)TelepathyFocus.tick(h.getLevel().getServer());TelepathyFocus.stop(a.player);h.assertTrue(TelepathyFocus.focus(a.player,b.player)>=60,"Holding builds a deep thread");}

    @GameTest(template="rewind_empty",batch="telepathy.rules")
    public static void weakerTelepathsAreEasierToResist(GameTestHelper h){
        h.assertTrue(MentalContest.gain(3,15)>MentalContest.gain(15,3),"A lower level attacker gives the victim substantially more resistance per correct press");
        h.assertTrue(MentalContest.windup(4)>MentalContest.windup(5),"Stronger telepaths establish control sooner");
        h.assertTrue(MentalContest.duration(5)<=120,"Possession stays brief");h.succeed();
    }
    @GameTest(template="rewind_empty",batch="telepathy.focus")
    public static void possessionRequiresReadingAndRejectsNestedSessions(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7);
        try{
            h.assertTrue(!PossessionService.start(a.player,b.player,5,false),"Cannot seize an unread mind");focus(h,a,b);
            h.assertTrue(PossessionService.start(a.player,b.player,5,false),"A deeply read mind accepts a takeover attempt");
            h.assertTrue(!PossessionService.start(b.player,a.player,5,false),"Nested possession is refused");
            h.assertTrue(!PossessionService.locked(b.player),"Victim still moves during windup");
            PossessionService.hurt(a.player,3);
            h.assertTrue(!PossessionService.occupied(a.player)&&!PossessionService.occupied(b.player),"Damage interrupts both ends");h.succeed();
        }finally{clean(a,b);}
    }
    @GameTest(template="rewind_empty",batch="telepathy.resist")
    public static void resistanceRejectsReplayAndForeignInputs(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7),outsider=player(h,11);
        try{
            focus(h,a,b);h.assertTrue(PossessionService.start(a.player,b.player,5,false),"Takeover starts");MindStatePayload initial=state(b);
            MindInputPayload press=new MindInputPayload(1,initial.cue(),initial.nonce(),0,0,0,0,false,false);
            PossessionService.input(outsider.player,press);h.assertTrue(state(b).resistance()==0,"A third party cannot answer for the victim");
            PossessionService.input(b.player,press);int progress=state(b).resistance();h.assertTrue(progress>0,"A fresh correct key makes progress");
            PossessionService.input(b.player,press);h.assertTrue(state(b).resistance()==progress,"Replayed challenge and key spam cannot add progress");
            PossessionService.forget(b.player);h.assertTrue(!PossessionService.occupied(a.player),"Victim departure restores the controller");h.succeed();
        }finally{clean(a,b,outsider);}
    }
    @GameTest(template="rewind_empty",batch="telepathy.control",timeoutTicks=100)
    public static void controlledActionsUseVictimInventoryAndReturnCleanly(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7);focus(h,a,b);h.assertTrue(PossessionService.start(a.player,b.player,5,false),"Takeover starts");
        h.runAfterDelay(35,()->{try{
            h.assertTrue(PossessionService.controlling(a.player),"Windup becomes possession");
            Vec3 beforeMove=b.player.position();
            PossessionService.input(a.player,new MindInputPayload(0,0,0,1,0,0,0,false,false));
            PossessionService.tick(h.getLevel().getServer());
            h.assertTrue(b.player.position().distanceToSqr(beforeMove)>.000001,"Controller input moves the victim's actual body");
            PossessionService.input(a.player,new MindInputPayload(0,0,0,Float.NaN,0,0,0,false,false));
            h.assertTrue(Double.isFinite(b.player.getX()),"Invalid movement input cannot corrupt the body");
            h.assertTrue(PossessionService.actionActor(a.player)==b.player,"Abilities resolve against the victim's real character");
            h.assertTrue(PossessionService.actionActor(b.player)==null,"Victim ability requests cannot race the controller");
            h.assertTrue(PossessionService.route(a.player,new ServerboundSetCarriedItemPacket(4)),"Gameplay packet is routed");
            h.assertTrue(b.player.getInventory().selected==4&&a.player.getInventory().selected==0,"Selected slot changes on the real victim only");
            PossessionService.route(b.player,new ServerboundSetCarriedItemPacket(1));h.assertTrue(b.player.getInventory().selected==4,"Victim's own hotbar packet is blocked");
            b.player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
            a.player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK,2));
            PossessionService.route(a.player,new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(0,b.player.inventoryMenu.getStateId(),36,0,net.minecraft.world.inventory.ClickType.PICKUP,net.minecraft.world.item.ItemStack.EMPTY,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
            h.assertTrue(b.player.inventoryMenu.getCarried().is(net.minecraft.world.item.Items.DIAMOND),"Inventory clicks use the real victim menu");
            h.assertTrue(a.player.getInventory().getItem(0).is(net.minecraft.world.item.Items.STICK),"Controller's inventory is never exchanged");
            b.player.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
            b.player.setData(ModDataAttachments.POWER_INTRO_HISTORY,b.player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PYROKINESIS_ID));
            int victimEnergy=PlayerEnergyService.getEnergy(b.player),ownerEnergy=PlayerEnergyService.getEnergy(a.player);
            PossessionService.action(a.player,new ActionInputPayload(0,"base:PRIMARY",true,false));
            h.assertTrue(PlayerEnergyService.getEnergy(b.player)<victimEnergy,"Borrowed cast spends victim stamina");
            h.assertTrue(PlayerEnergyService.getEnergy(a.player)==ownerEnergy,"Borrowed cast does not spend caster stamina");
            PossessionService.end(a.player,"Test release");
            h.assertTrue(b.player.inventoryMenu.getCarried().isEmpty(),"Release returns the cursor stack to the victim");
            h.assertTrue(b.player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND)==3,"Release preserves the real items exactly once");h.assertTrue(PossessionService.actionActor(b.player)==b.player,"Control returns");
            h.assertTrue(!PossessionService.start(a.player,b.player,5,false),"Recent victims resist immediate re-possession");h.succeed();
        }finally{clean(a,b);}});
    }
    @GameTest(template="rewind_empty",batch="telepathy.escape",timeoutTicks=100)
    public static void aPlayerCanWinTheStruggleBeforeControlBegins(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7);focus(h,a,b);h.assertTrue(PossessionService.start(a.player,b.player,5,false),"Attempt starts");
        pressUntilFree(h,a,b,0);
    }
    private static void pressUntilFree(GameTestHelper h,Actor a,Actor b,int presses){
        try{
            if(!PossessionService.occupied(b.player)){h.assertTrue(presses>=3&&presses<=8,"Several deliberate presses break the connection");h.assertTrue(!PossessionService.start(a.player,b.player,5,false),"Freedom grants temporary resistance");clean(a,b);h.succeed();return;}
            h.assertTrue(presses<9,"The struggle is winnable");MindStatePayload cue=state(b);
            PossessionService.input(b.player,new MindInputPayload(1,cue.cue(),cue.nonce(),0,0,0,0,false,false));
            h.runAfterDelay(6,()->pressUntilFree(h,a,b,presses+1));
        }catch(Throwable ex){clean(a,b);throw ex;}
    }
    @GameTest(template="rewind_empty",batch="telepathy.skills")
    public static void allThreeOptionalBranchesHaveTheirNewConsequences(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7);
        var mob=net.minecraft.world.entity.EntityType.PILLAGER.create(h.getLevel());mob.setPos(a.player.position().add(0,0,2));mob.setNoAi(true);h.getLevel().addFreshEntity(mob);
        try{
            var c=new com.jayptucker.heroesevolved.ability.AbilityUseContext(a.player,ModAbilities.TELEPATHY_ID,5,false);
            h.assertTrue(com.jayptucker.heroesevolved.skills.SkillActionService.activate(c,0),"Implanted Command opens the choice for a mob");
            SuggestionService.choose(a.player,3);
            h.assertTrue(SuggestionService.forbidden(mob,a.player),"Forget Me actually suppresses the mob's target");
            mob.discard();b.player.setShiftKeyDown(true);
            h.assertTrue(com.jayptucker.heroesevolved.skills.SkillActionService.activate(c,1),"Shared Sight establishes a voluntary link");
            h.assertTrue(com.jayptucker.heroesevolved.skills.SkillActionService.activate(c,1)&&PossessionService.occupied(a.player),"Recasting looks through the linked mind");
            PossessionService.end(a.player,"Return");
            h.assertTrue(com.jayptucker.heroesevolved.skills.SkillActionService.activate(c,2),"Dream Double opens illusion choices for a player");
            IllusionService.choose(a.player,0);
            h.assertTrue(b.packets.stream().anyMatch(p->p instanceof ClientboundCustomPayloadPacket cp&&cp.payload() instanceof IllusionPayload i&&i.kind()==0),"Choice creates the private clone scene");h.succeed();
        }finally{mob.discard();clean(a,b);}
    }
    @GameTest(template="rewind_empty",batch="telepathy.aggro",timeoutTicks=80)
    public static void suggestionClearsAnExistingAggroTargetAndPreventsReacquisition(GameTestHelper h){
        Actor a=player(h,3);
        var mob=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        mob.setPos(a.player.position().add(0,0,4));mob.setNoGravity(true);h.getLevel().addFreshEntity(mob);
        try{
            mob.setTarget(a.player);mob.setLastHurtByMob(a.player);
            h.assertTrue(mob.getTarget()==a.player,"Regression begins with an aggressive mob");
            h.assertTrue(SuggestionService.open(a.player),"Suggestion finds the attacker");SuggestionService.choose(a.player,3);
            h.assertTrue(mob.getTarget()==null,"Forget Me must actually clear the existing target");
            mob.setTarget(a.player);
            h.assertTrue(mob.getTarget()==null,"AI cannot reacquire the forbidden target");
            h.runAfterDelay(20,()->{try{
                h.assertTrue(mob.getTarget()==null,"Aggro remains suppressed across real AI ticks");
                SuggestionService.forget(a.player);mob.setTarget(a.player);
                h.assertTrue(mob.getTarget()==a.player,"Normal targeting returns after the suggestion ends");h.succeed();
            }finally{mob.discard();clean(a);}});
        }catch(Throwable ex){mob.discard();clean(a);throw ex;}
    }
    @GameTest(template="rewind_empty",batch="telepathy.mob_control",timeoutTicks=100)
    public static void puppetWalksAndAttacksThroughARealMob(GameTestHelper h){
        Actor a=player(h,3);
        var mob=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        var victim=net.minecraft.world.entity.EntityType.VILLAGER.create(h.getLevel());
        for(int x=-4;x<=4;x++)for(int z=-2;z<=14;z++)h.getLevel().setBlock(a.player.blockPosition().offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        mob.setPos(a.player.position().add(0,0,4));mob.setNoGravity(true);mob.setPersistenceRequired();h.getLevel().addFreshEntity(mob);
        victim.setNoAi(true);victim.setNoGravity(true);
        h.assertTrue(TelepathyFocus.start(a.player,5),"Read the mob");for(int n=0;n<35;n++)TelepathyFocus.tick(h.getLevel().getServer());TelepathyFocus.stop(a.player);
        h.assertTrue(PossessionService.start(a.player,mob,5,false),"Possess the mob");
        h.runAfterDelay(35,()->{try{
            h.assertTrue(PossessionService.controlling(a.player),"Puppet enters active control");
            mob.setPos(a.player.position().add(0,0,4));mob.setDeltaMovement(Vec3.ZERO);mob.setOnGround(true);
            Vec3 before=mob.position();
            PossessionService.input(a.player,new MindInputPayload(0,0,0,1,0,0,0,false,false));
            for(int n=0;n<8;n++)PossessionService.tick(h.getLevel().getServer());
            h.assertTrue(mob.getZ()>before.z+.3,"Forward input must move a possessed mob on the ground");
            h.assertTrue(mob.isNoAi(),"Autonomous AI stays paused between controlled movement steps");
            victim.setPos(mob.position().add(0,0,2));h.getLevel().addFreshEntity(victim);float health=victim.getHealth();
            PossessionService.input(a.player,new MindInputPayload(6,0,0,0,0,0,0,false,false));
            h.assertTrue(victim.getHealth()<health,"Attack input must deal the possessed mob's melee damage");
            float after=victim.getHealth();PossessionService.input(a.player,new MindInputPayload(6,0,0,0,0,0,0,false,false));
            h.assertTrue(victim.getHealth()==after,"Repeated attack packets cannot bypass the attack interval");
            PossessionService.end(a.player,"Release");h.assertTrue(!mob.isNoAi(),"Original autonomous AI is restored on release");h.succeed();
        }finally{mob.discard();victim.discard();clean(a);}});
    }
    @GameTest(template="rewind_empty",batch="telepathy.illusion")
    public static void illusionsArePrivateAndNeverReplaceRealBlocks(GameTestHelper h){
        Actor a=player(h,3),b=player(h,7),observer=player(h,11);
        try{
            BlockPos pos=b.player.blockPosition().offset(0,0,3);var before=h.getLevel().getBlockState(pos);
            h.assertTrue(IllusionService.create(a.player,b.player,3,Vec3.atBottomCenterOf(pos),Vec3.ZERO,100),"A private chest can be woven");
            var payloads=b.packets.stream().filter(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof IllusionPayload).map(p->(IllusionPayload)((ClientboundCustomPayloadPacket)p).payload()).toList();
            h.assertTrue(payloads.size()==1,"Only intended viewer receives the vision");
            h.assertTrue(observer.packets.stream().noneMatch(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof IllusionPayload),"Bystander sees no illusion packet");
            h.assertTrue(h.getLevel().getBlockState(pos)==before,"No server block or chest inventory is created");
            IllusionService.dissolve(observer.player,payloads.getFirst().id());h.assertTrue(b.packets.stream().filter(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof IllusionPayload).count()==1,"A bystander cannot dissolve another mind's vision");
            IllusionService.forget(b.player);h.assertTrue(b.packets.stream().anyMatch(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload() instanceof IllusionPayload i&&i.kind()==-1),"Cleanup explicitly dissolves the private vision");h.succeed();
        }finally{clean(a,b,observer);}
    }
}
