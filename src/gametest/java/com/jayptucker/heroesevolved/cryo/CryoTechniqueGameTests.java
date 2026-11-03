package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CryoTechniqueGameTests {
    private static ServerPlayer player(GameTestHelper h,boolean cryo,int x) {
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"cryo-technique")) {
            @Override public boolean isInvulnerableTo(DamageSource source){return false;}
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other){return true;}
        };
        try {
            Connection connection=p.connection.getConnection();
            Field channel=Connection.class.getDeclaredField("channel");channel.setAccessible(true);
            if(connection.channel()==null)channel.set(connection,new EmbeddedChannel());
            p.connection=new ServerGamePacketListenerImpl(p.server,connection,p,CommonListenerCookie.createInitial(p.getGameProfile(),false)) {
                @Override public void send(Packet<?> packet){}
            };
            Field grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,2,16))));p.setNoGravity(true);p.setYRot(0);p.setXRot(0);p.setGameMode(GameType.SURVIVAL);
        if(cryo) {
            p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
        }
        PlayerProgressionService.setLevel(p,5);PlayerEnergyService.restore(p,999);h.getLevel().addNewPlayer(p);return p;
    }
    private static AbilityUseContext context(ServerPlayer p){return new AbilityUseContext(p,ModAbilities.CRYOKINESIS_ID,5,false);}
    private static Mob enemy(GameTestHelper h,Vec3 at) {
        Mob target=EntityType.PILLAGER.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();target.setPos(at);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);h.getLevel().addFreshEntity(target);return target;
    }
    private static void aim(ServerPlayer p,Vec3 at){Vec3 delta=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));}
    private static void clean(ServerPlayer... players){for(ServerPlayer p:players){PowerRules.rewinding(p,false);CryoTechniqueService.forget(p);CryoService.stopAll(p);p.discard();}}

    @GameTest(template="cryo_rework_arena",batch="cryo.technique_loom")
    public static void aLoomGrowsOverTimeCanBendAndRestoresItsFoundation(GameTestHelper h) {
        ServerPlayer p=player(h,true,8);Vec3 start=p.position();
        h.assertTrue(CryoTechniqueService.activate(context(p),"rime_loom"),"A clear foundation starts the weave");
        h.assertTrue(CryoTechniqueService.loomBlocks(p).size()==1,"The first cell is real, while the rest is still growing");
        h.runAfterDelay(9,()->{
            int count=CryoTechniqueService.loomBlocks(p).size();
            h.assertTrue(count>1&&count<16,"Rime Loom grows in timed segments rather than appearing instantly");
            p.setYRot(-90);h.assertTrue(CryoTechniqueService.followUp(context(p),"rime_loom"),"An explicit second press guides the unfinished weave");
        });
        h.runAfterDelay(40,()->{
            try {
                var blocks=CryoTechniqueService.loomBlocks(p);
                h.assertTrue(blocks.stream().anyMatch(pos->pos.getX()>start.x+2)&&blocks.stream().anyMatch(pos->pos.getZ()>start.z+1),"The constructed collision path contains a real bend");
                CryoTechniqueService.forget(p);
                h.assertTrue(blocks.stream().allMatch(pos->h.getLevel().getBlockState(pos).is(Blocks.STONE)),"Removing the weave restores its exact original stone floor");h.succeed();
            }finally{clean(p);}
        });
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_loom_permission")
    public static void loomCannotBuildWithoutTerrainPermission(GameTestHelper h) {
        ServerPlayer p=player(h,true,8);p.setGameMode(GameType.ADVENTURE);
        try {
            h.assertTrue(!CryoTechniqueService.activate(context(p),"rime_loom")&&CryoTechniqueService.loomBlocks(p).isEmpty(),"A denied cast does not schedule future ice through build protection");h.succeed();
        }finally{clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_native_ice")
    public static void loomCanFollowNativeIceWithoutClaimingOrRestoringIt(GameTestHelper h) {
        ServerPlayer p=player(h,true,8);
        var originals=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int z=16;z<=34;z++) {
            BlockPos pos=h.absolutePos(new BlockPos(8,1,z));
            var state=(z%3==0?Blocks.ICE:z%3==1?Blocks.PACKED_ICE:Blocks.BLUE_ICE).defaultBlockState();
            originals.put(pos,state);h.getLevel().setBlock(pos,state,3);
        }
        p.setGameMode(GameType.ADVENTURE);
        h.assertTrue(!CryoTechniqueService.activate(context(p),"rime_loom"),"Native ice still respects the caster's build permission");
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(CryoTechniqueService.activate(context(p),"rime_loom")&&CryoTechniqueService.isWeaving(p),"An existing ice floor can start a growing weave");
        h.runAfterDelay(50,()->{
            try {
                h.assertTrue(!CryoTechniqueService.isWeaving(p)&&CryoTechniqueService.loomBlocks(p).isEmpty(),"The weave can finish over all native ice types without taking ownership");
                CryoTechniqueService.forget(p);
                h.assertTrue(originals.entrySet().stream().allMatch(e->h.getLevel().getBlockState(e.getKey()).equals(e.getValue())&&!TemporaryBlocks.isTemporary(h.getLevel(),e.getKey())),
                        "Cleanup leaves every original ice cell exactly as it was");h.succeed();
            }finally{clean(p);}
        });
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_cocoon_pressure")
    public static void cocoonStoresImpactAndEarlyOpeningSpendsItWithoutHarmingAllies(GameTestHelper h) {
        ServerPlayer p=player(h,true,12),friend=player(h,false,13);p.setHealth(10);p.setShiftKeyDown(true);
        Mob hostile=enemy(h,p.position().add(0,0,3));
        var board=p.getScoreboard();var team=board.addPlayerTeam("ice-"+UUID.randomUUID().toString().substring(0,8));
        board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(friend.getScoreboardName(),team);
        try {
            h.assertTrue(CryoTechniqueService.activate(context(p),"stillheart"),"Stillheart shelters its caster");
            p.hurt(p.damageSources().generic(),10);
            h.assertTrue(Math.abs(CryoTechniqueService.cocoonPressure(p)-8)<.01&&p.getHealth()>7.9F,"The shell catches eighty percent of a real impact as finite pressure");
            float friendHealth=friend.getHealth(),enemyHealth=hostile.getHealth();
            h.assertTrue(CryoTechniqueService.canReleaseCocoon(p)&&CryoTechniqueService.releaseCocoon(p),"The caster can deliberately open its own preservation lock");
            h.assertTrue(!CryoTechniqueService.preserved(p)&&p.getHealth()>8&&CryoService.frostAmount(hostile)>0,"Opening converts pressure into recovery and a Frost pulse");
            h.assertTrue(hostile.getHealth()==enemyHealth&&friend.getHealth()==friendHealth&&CryoService.frostAmount(friend)==0,"The release pulse is nonlethal and excludes nearby allies");h.succeed();
        }finally{board.removePlayerTeam(team);hostile.discard();clean(p,friend);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_cocoon_cleanup")
    public static void revokedCocoonDoesNotAwardReleaseHealing(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);p.setHealth(10);p.setShiftKeyDown(true);
        try {
            CryoTechniqueService.activate(context(p),"stillheart");p.hurt(p.damageSources().generic(),10);float before=p.getHealth();
            p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty());CryoTechniqueService.tick(h.getLevel().getServer());
            h.assertTrue(!CryoTechniqueService.preserved(p)&&p.getHealth()==before,"Loss of the power removes the shell without a healing or Frost reward");h.succeed();
        }finally{clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_cocoon_friends")
    public static void aRescuedVillagerAndNearbyFriendsAreNeverChilledByTheRelease(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);
        var recipient=EntityType.VILLAGER.create(h.getLevel());recipient.setNoAi(true);recipient.setNoGravity(true);recipient.setPos(p.position().add(0,0,3));recipient.setHealth(10);h.getLevel().addFreshEntity(recipient);
        var friend=EntityType.VILLAGER.create(h.getLevel());friend.setNoAi(true);friend.setNoGravity(true);friend.setPos(p.position().add(2,0,3));friend.setHealth(10);h.getLevel().addFreshEntity(friend);
        Mob enemy=enemy(h,p.position().add(0,0,5));
        p.setData(ModDataAttachments.SKILL_TREES,com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(15,java.util.Set.of("cryokinesis_second_spring"),java.util.Map.of())));
        aim(p,recipient.getEyePosition());
        try {
            h.assertTrue(CryoTechniqueService.activate(context(p),"stillheart")&&CryoTechniqueService.preserved(recipient),"A willing friendly mob receives the rescue shell");
            recipient.hurt(recipient.damageSources().generic(),10);
            h.assertTrue(CryoTechniqueService.cocoonPressure(recipient)>0&&CryoTechniqueService.releaseCocoon(p),"An actual impact supplies cold for the rescue release");
            h.assertTrue(recipient.getHealth()>8&&friend.getHealth()>10,"Second Spring restores the recipient and nearby friendly mobs");
            h.assertTrue(CryoService.frostAmount(recipient)==0&&CryoService.frostAmount(friend)==0&&!CryoService.isFrozen(recipient)&&!CryoService.isFrozen(friend),
                    "The rescued mob and healed neighbors are excluded from the release Frost pulse");
            h.assertTrue(CryoService.frostAmount(enemy)>0,"The same release still chills a nearby hostile mob");h.succeed();
        }finally{recipient.discard();friend.discard();enemy.discard();clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_cocoon_lock")
    public static void earlyOpeningCannotBypassAnIndependentRewindLock(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);p.setShiftKeyDown(true);
        try {
            CryoTechniqueService.activate(context(p),"stillheart");PowerRules.rewinding(p,true);
            h.assertTrue(!CryoTechniqueService.canReleaseCocoon(p)&&!CryoTechniqueService.releaseCocoon(p),"Only the cocoon's own action lock may be bypassed by its release");h.succeed();
        }finally{clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_cocoon_input")
    public static void actualCocoonKeyCanOpenPreservationWithoutUnlockingOtherActions(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);
        p.setData(ModDataAttachments.SKILL_TREES,com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(15,java.util.Set.of("cryokinesis_1_0","cryokinesis_stillheart"),java.util.Map.of())));
        try {
            h.assertTrue(com.jayptucker.heroesevolved.skills.SkillTreeService.equip(p,com.jayptucker.heroesevolved.ability.AbilitySlot.PRIMARY,"cryokinesis_stillheart"),"The learned cocoon can be bound to an actual ability key");
            p.setShiftKeyDown(true);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p,com.jayptucker.heroesevolved.ability.AbilitySlot.PRIMARY,false)
                    ==com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS&&CryoTechniqueService.preserved(p),"The real key route creates self-preservation");
            int energy=PlayerEnergyService.getEnergy(p);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p,com.jayptucker.heroesevolved.ability.AbilitySlot.SECONDARY,false)
                    ==com.jayptucker.heroesevolved.ability.AbilityUseResult.CANNOT_USE,"Preservation still blocks unrelated powers");
            p.setShiftKeyDown(false);
            h.assertTrue(com.jayptucker.heroesevolved.ability.service.AbilityUseService.activateAssignedAction(p,com.jayptucker.heroesevolved.ability.AbilitySlot.PRIMARY,false)
                    ==com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS&&!CryoTechniqueService.preserved(p)&&PlayerEnergyService.getEnergy(p)==energy,
                    "A second cocoon key press opens its own lock without charging again");h.succeed();
        }finally{clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_frost_network")
    public static void constellationMarksFreshTargetsAndSharesNewFrostOnce(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);Mob first=enemy(h,p.position().add(0,0,4)),second=enemy(h,p.position().add(2,0,4));aim(p,first.getEyePosition());
        try {
            h.assertTrue(CryoTechniqueService.activate(context(p),"brittle_constellation"),"Visible enemies can be connected");
            h.assertTrue(CryoTechniqueService.linkedCount(p)==2&&!CryoService.isFrozen(first)&&!CryoService.isFrozen(second),"Fresh marks begin with Frost instead of instantly freezing every target");
            int before=CryoService.frostAmount(second);CryoService.chill(first,60,p);
            h.assertTrue(CryoService.frostAmount(second)>before&&CryoService.frostAmount(second)<=before+15,"A new Frost application shares a bounded portion without recursively duplicating itself");h.succeed();
        }finally{first.discard();second.discard();clean(p);}
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_staged_chain")
    public static void aFrozenStarSendsOneDelayedFractureAlongItsLinks(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);Mob first=enemy(h,p.position().add(0,0,4)),second=enemy(h,p.position().add(2,0,4));aim(p,first.getEyePosition());
        CryoTechniqueService.activate(context(p),"brittle_constellation");CryoService.freezeSolid(first,80,p);
        float before=second.getHealth();first.hurt(p.damageSources().playerAttack(p),6);
        h.assertTrue(second.getHealth()==before&&CryoTechniqueService.chainCount()>0,"Shattering a star creates a travelling fracture rather than immediate extra hits");
        h.runAfterDelay(2,()->h.assertTrue(second.getHealth()==before,"The connection has a real visible travel delay"));
        h.runAfterDelay(12,()->{
            try {
                h.assertTrue(second.getHealth()==before-4&&CryoTechniqueService.chainCount()==0&&CryoTechniqueService.linkedCount(p)==0,"The staged fracture arrives exactly once and spends its network");h.succeed();
            }finally{first.discard();second.discard();clean(p);}
        });
    }
    @GameTest(template="cryo_rework_arena",batch="cryo.technique_chain_cover")
    public static void newSolidCoverBreaksTheTravellingFracture(GameTestHelper h) {
        ServerPlayer p=player(h,true,12);Mob first=enemy(h,p.position().add(0,0,4)),second=enemy(h,p.position().add(2,0,4));aim(p,first.getEyePosition());
        CryoTechniqueService.activate(context(p),"brittle_constellation");CryoService.freezeSolid(first,80,p);
        float before=second.getHealth();first.hurt(p.damageSources().playerAttack(p),6);
        BlockPos wall=first.blockPosition().east();for(int y=0;y<3;y++)h.getLevel().setBlock(wall.above(y),Blocks.STONE.defaultBlockState(),3);
        h.runAfterDelay(8,()->{
            try {
                h.assertTrue(second.getHealth()==before&&CryoTechniqueService.chainCount()==0,"A physical wall breaks the link before its pulse arrives");h.succeed();
            }finally{first.discard();second.discard();clean(p);}
        });
    }
}
