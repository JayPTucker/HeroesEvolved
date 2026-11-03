package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.effect.MobEffects;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The ice is real terrain; its control, counterplay and recovery remain server-owned. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CryoCombatGameTests {
    private static ServerPlayer player(GameTestHelper h, int x, boolean cryo) {
        for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)),
                h.absolutePos(new BlockPos(11, 1, 11)))) h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "cryo-combat")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) { return !isAlliedTo(other); }
        };
        try {
            Connection c = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (c.channel() == null) channel.set(c, new EmbeddedChannel());
            p.connection = new ServerGamePacketListenerImpl(p.server, c, p,
                    CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { }
            };
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 3))));
        p.setNoGravity(true); p.setYRot(0); p.setXRot(0); p.setGameMode(GameType.SURVIVAL);
        if (cryo) {
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                    .assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                    p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
        }
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        h.getLevel().addNewPlayer(p);
        return p;
    }
    private static Mob enemy(GameTestHelper h, ServerPlayer p, double distance) {
        var t = EntityType.PILLAGER.create(h.getLevel());
        t.setNoAi(true); t.setNoGravity(true); t.setPersistenceRequired();
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); t.setHealth(100);
        t.setPos(p.position().add(0, 0, distance)); h.getLevel().addFreshEntity(t); return t;
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 delta = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
    private static void clean(ServerPlayer... players) {
        for (ServerPlayer p : players) { CryoService.stopAll(p); p.discard(); }
    }
    private static String ownerState(ServerPlayer p) {
        return " alive=" + p.isAlive() + " removed=" + p.isRemoved()
                + " canAct=" + com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p)
                + " authorized=" + CryoKitService.authorized(p) + " at=" + p.position()
                + " below=" + p.level().getBlockState(p.blockPosition().below())
                + " body=" + p.level().getBlockState(p.blockPosition());
    }

    private static AbilityUseContext context(ServerPlayer p) {
        return new AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, false);
    }
    private static void learnArmor(ServerPlayer p) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                new SkillTreeData.Build(15, Set.of("cryokinesis_1_0", "cryokinesis_1_1"), Map.of())));
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_lance_forge")
    public static void aLanceForgesBeforeItsPhysicalProjectileStrikes(GameTestHelper h) {
        ServerPlayer p=player(h,8,true); Mob target=enemy(h,p,8); aim(p,target.getBoundingBox().getCenter());
        h.assertTrue(CryoCombatSkills.activate(context(p),0),"Crystal Lance starts a real forge");
        h.assertTrue(target.getHealth()==100 && CryoCombatSkills.isForging(p),"Starting the forge cannot hit a distant target instantly");
        h.runAfterDelay(8,()->h.assertTrue(target.getHealth()==100 && CryoCombatSkills.isForging(p),"The visible wind-up precedes projectile damage"));
        h.runAfterDelay(23,()->{
            try {
                h.assertTrue(target.getHealth()<100 && CryoService.frostAmount(target)>0,"The travelling lance must reach and chill a visible enemy");
                h.assertTrue(!CryoCombatSkills.isForging(p) && CryoCombatSkills.lanceCount(p)==0,"An impacted spear consumes its forge and projectile");
                h.succeed();
            }finally{target.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_lance_aim")
    public static void theCasterCanAdjustLanceAimWhileForging(GameTestHelper h) {
        ServerPlayer p=player(h,8,true); Mob initial=enemy(h,p,8), moved=enemy(h,p,8); moved.setPos(moved.position().add(4,0,0));
        aim(p,initial.getBoundingBox().getCenter()); CryoCombatSkills.activate(context(p),0);
        h.runAfterDelay(6,()->aim(p,moved.getBoundingBox().getCenter()));
        h.runAfterDelay(24,()->{
            try {
                h.assertTrue(initial.getHealth()==100 && moved.getHealth()<100,"Release uses the caster's final aim, not a hidden target selected on the first press");
                h.succeed();
            }finally{initial.discard();moved.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_lance_cover")
    public static void newCoverStopsTheLanceBeforeItCanReachItsTarget(GameTestHelper h) {
        ServerPlayer p=player(h,8,true); Mob target=enemy(h,p,8); aim(p,target.getBoundingBox().getCenter());
        CryoCombatSkills.activate(context(p),0);
        h.runAfterDelay(6,()->{
            for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)h.getLevel().setBlock(p.blockPosition().offset(x,y,4),Blocks.STONE.defaultBlockState(),3);
        });
        h.runAfterDelay(24,()->{
            try {
                h.assertTrue(target.getHealth()==100 && CryoService.frostAmount(target)==0,"A physical spear cannot damage or freeze through newly placed cover");
                h.assertTrue(CryoCombatSkills.lanceCount(p)==0,"The obstruction consumes the projectile");h.succeed();
            }finally{target.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_armor_projectiles")
    public static void sixArmorPlatesCatchOnlySixHostileProjectiles(GameTestHelper h) {
        ServerPlayer p=player(h,8,true); Mob shooter=enemy(h,p,5);
        CryoCombatSkills.activate(context(p),1);
        var shots=new java.util.ArrayList<Arrow>();
        for(int i=0;i<7;i++) {
            var arrow=new Arrow(EntityType.ARROW,h.getLevel());arrow.setOwner(shooter);arrow.setNoGravity(true);
            arrow.setPos(p.position().add(.9,1+i*.025,0));arrow.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(arrow);shots.add(arrow);
        }
        var ownShot=new Arrow(EntityType.ARROW,h.getLevel());ownShot.setOwner(p);ownShot.setNoGravity(true);
        ownShot.setPos(p.position().add(-.9,1,0));h.getLevel().addFreshEntity(ownShot);
        h.runAfterDelay(5,()->{
            try {
                h.assertTrue(CryoCombatSkills.armorPlates(p)==0,"Each caught projectile spends a finite plate");
                h.assertTrue(shots.stream().filter(net.minecraft.world.entity.Entity::isRemoved).count()==6 && !ownShot.isRemoved(),
                        "The seventh hostile projectile and the caster's own shot pass the exhausted guard");
                h.assertTrue(!p.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&!p.hasEffect(MobEffects.FIRE_RESISTANCE),"The new armor does not leave the old potion-only shield behind");
                h.succeed();
            }finally{shots.forEach(net.minecraft.world.entity.Entity::discard);ownShot.discard();shooter.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_armor_impacts")
    public static void PhysicalImpactsSpendArmorAndLeaveStoredCold(GameTestHelper h) {
        ServerPlayer p=player(h,8,true); Mob attacker=enemy(h,p,5); CryoCombatSkills.activate(context(p),1);
        float before=p.getHealth();p.hurt(p.damageSources().mobAttack(attacker),4);
        h.assertTrue(p.getHealth()>before-4 && p.getHealth()<before,"A plate absorbs part of the real incoming impact");
        h.assertTrue(CryoCombatSkills.armorPlates(p)==5 && CryoCombatSkills.storedCold(p)>0,"Absorbing an impact consumes one plate and stores cold for a countershot");
        for(int i=0;i<5;i++){p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(attacker),1);}
        float bare=p.getHealth();p.invulnerableTime=0;p.hurt(p.damageSources().mobAttack(attacker),2);
        try {
            h.assertTrue(CryoCombatSkills.armorPlates(p)==0 && Math.abs((bare-p.getHealth())-2)<.01,"Exhausted armor cannot keep mitigating damage");h.succeed();
        }finally{attacker.discard();clean(p);}
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_armor_followup")
    public static void anExplicitArmorFollowUpFiresTheRemainingPlatesDuringCooldown(GameTestHelper h) {
        ServerPlayer p=player(h,8,true);learnArmor(p);
        h.assertTrue(AbilityUseService.activateAction(p,"cryokinesis_1_1",false)==AbilityUseResult.SUCCESS,"The learned armor action activates");
        var action=SkillCatalog.find("cryokinesis","cryokinesis_1_1").orElseThrow().action();
        int energy=PlayerEnergyService.getEnergy(p);
        h.assertTrue(!action.deactivate(context(p)) && CryoCombatSkills.armorPlates(p)==6,"Rebinding cleanup cannot secretly fire armor plates");
        h.assertTrue(AbilityUseService.activateAction(p,"cryokinesis_1_1",false)==AbilityUseResult.SUCCESS
                && CryoCombatSkills.armorPlates(p)==0 && CryoCombatSkills.activeShots(p)==6,"A second real press reaches the aimed shard fan before the initial cooldown");
        try {h.assertTrue(PlayerEnergyService.getEnergy(p)==energy,"The countershot spends prepared plates without charging a second activation");h.succeed();}
        finally{clean(p);}
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_snap_front")
    public static void coldSnapTravelsOutwardAndBuildsFrostOnFreshEnemies(GameTestHelper h) {
        ServerPlayer p=player(h,8,true);Mob near=enemy(h,p,3),far=enemy(h,p,6.5);
        CryoCombatSkills.activate(context(p),2);
        h.assertTrue(CryoService.frostAmount(near)==0 && CryoService.frostAmount(far)==0,"The first press does not freeze the entire radius instantly");
        h.runAfterDelay(7,()->h.assertTrue(CryoService.frostAmount(near)>0 && CryoService.frostAmount(far)==0,"The near enemy meets the travelling front before the far enemy"));
        h.runAfterDelay(16,()->{
            try {
                h.assertTrue(CryoService.frostAmount(far)>0 && !CryoService.isFrozen(near) && !CryoService.isFrozen(far),"Fresh targets receive Frost and need preparation before a solid freeze");
                h.assertTrue(Math.abs(near.getHealth()-98)<.01 && Math.abs(far.getHealth()-98)<.01,"A single front cannot repeatedly damage a target");h.succeed();
            }finally{near.discard();far.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_snap_setup")
    public static void coldSnapFreezesPreparedEnemiesAndShattersFrozenOnes(GameTestHelper h) {
        ServerPlayer p=player(h,8,true);Mob prepared=enemy(h,p,3),frozen=enemy(h,p,4);frozen.setPos(frozen.position().add(2,0,0));
        CryoService.chill(prepared,60,p);CryoService.freezeSolid(frozen,80,p);float health=frozen.getHealth();
        CryoCombatSkills.activate(context(p),2);
        h.runAfterDelay(16,()->{
            try {
                h.assertTrue(CryoService.isFrozen(prepared),"Prepared Frost becomes a solid freeze when the front arrives");
                h.assertTrue(!CryoService.isFrozen(frozen)&&frozen.getHealth()<health,"An already frozen target deliberately fractures instead of receiving another freeze");h.succeed();
            }finally{prepared.discard();frozen.discard();clean(p);}
        });
    }

    @GameTest(template="cryo_rework_arena", batch="cryo.skills_revoke")
    public static void losingCryokinesisCancelsForgingArmorAndTheColdFront(GameTestHelper h) {
        ServerPlayer p=player(h,8,true);Mob target=enemy(h,p,8);aim(p,target.getBoundingBox().getCenter());
        CryoCombatSkills.activate(context(p),0);CryoCombatSkills.activate(context(p),1);CryoCombatSkills.activate(context(p),2);
        p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty());
        h.runAfterDelay(20,()->{
            try {
                h.assertTrue(CryoCombatSkills.lanceCount(p)==0 && CryoCombatSkills.armorPlates(p)==0 && CryoCombatSkills.snapCount(p)==0,"Invalid owner state cannot leave active skill jobs running");
                h.assertTrue(target.getHealth()==100 && CryoService.frostAmount(target)==0,"A revoked forge or cold front cannot finish attacking later");h.succeed();
            }finally{target.discard();clean(p);}
        });
    }
}
