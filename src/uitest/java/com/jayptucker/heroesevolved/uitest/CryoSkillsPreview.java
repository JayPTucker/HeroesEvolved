package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoKitService;
import com.jayptucker.heroesevolved.cryo.CryoRideService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Checks real survival controls and photographs solid ice from useful oblique views. */
@EventBusSubscriber(modid="heroesevolved", value=Dist.CLIENT)
public final class CryoSkillsPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Mob victim;
    private static final java.util.List<Mob> targets=new java.util.ArrayList<>();
    private static Vec3 rideStart;
    private CryoSkillsPreview() {}
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }
    private static void scene(ServerPlayer p, double x, float yaw, float pitch) {
        CryoService.stopAll(p);
        for(Mob mob:targets)mob.discard(); targets.clear();victim=null;
        com.jayptucker.heroesevolved.expansion.ExpansionSkills.forget(p);
        p.setNoGravity(false);p.setHealth(20);p.removeAllEffects();p.clearFire();
        p.connection.teleport(x, -60, .5, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO); p.setOnGround(true); p.resetFallDistance();
        PlayerEnergyService.restore(p, 999);
        com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
    }
    private static Mob target(ServerPlayer p, double x, double z) {
        var t = EntityType.PILLAGER.create(p.serverLevel());
        t.setNoAi(true); t.setPersistenceRequired();
        t.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);t.setHealth(100);
        t.moveTo(x, -60, z, 180, 0); p.serverLevel().addFreshEntity(t);targets.add(t);return t;
    }
    private static void view(ServerPlayer p, Vec3 at) {
        Vec3 delta = at.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }
    private static void bind(ServerPlayer p,String id,String capstone) {
        var learned=com.jayptucker.heroesevolved.skills.SkillCatalog.nodes("cryokinesis").stream()
                .filter(n->!n.capstone()||n.id().equals(capstone)).map(com.jayptucker.heroesevolved.skills.SkillCatalog.Node::id)
                .collect(java.util.stream.Collectors.toSet());
        p.setData(ModDataAttachments.SKILL_TREES,com.jayptucker.heroesevolved.skills.SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                new com.jayptucker.heroesevolved.skills.SkillTreeData.Build(15,learned,java.util.Map.of(),6)));
        if(!com.jayptucker.heroesevolved.skills.SkillTreeService.bind(p,1,id))throw new AssertionError("Could not bind "+id);
        PlayerPowerSyncService.sync(p);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.cryoSkills")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started=true; mc.options.pauseOnLostFocus=false; mc.options.guiScale().set(2);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("CryoSkills-"+System.currentTimeMillis(),
                    new LevelSettings("Cryokinesis preview",GameType.SURVIVAL,false,Difficulty.EASY,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L,false,false),
                    access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.level==null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        switch(++world) {
            case 180 -> server(mc,p->{
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
                PlayerProgressionService.setLevel(p,5);p.serverLevel().setDayTime(10000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                scene(p,200.5,0,0);victim=target(p,200.5,10.5);view(p,victim.getBoundingBox().getCenter());bind(p,"cryokinesis_0_1","");
            });
            case 195 -> {mc.gui.getChat().clearMessages(true);mc.getToasts().clear();}
            case 200 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 201 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 207 -> pending="cryo-skill-1-lance-forge.png";
            case 215 -> pending="cryo-skill-2-lance-flight.png";
            case 226 -> server(mc,p->{
                if(victim.getHealth()>=100||CryoService.frostAmount(victim)<=0)throw new AssertionError("Real Crystal Lance input must forge, travel, strike and build Frost");
                System.out.println("CRYO_SKILLS_LANCE PASS");
            });
            case 245 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);server(mc,p->{scene(p,400.5,0,0);victim=target(p,400.5,8.5);bind(p,"cryokinesis_1_1","");});}
            case 255 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 256 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 266 -> pending="cryo-skill-3-orbiting-armor.png";
            case 270 -> server(mc,p->{
                var arrow=new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW,p.serverLevel());arrow.setOwner(victim);arrow.setNoGravity(true);
                arrow.setPos(p.position().add(.9,1,0));arrow.setDeltaMovement(Vec3.ZERO);p.serverLevel().addFreshEntity(arrow);
            });
            case 276 -> server(mc,p->{
                if(com.jayptucker.heroesevolved.cryo.CryoCombatSkills.armorPlates(p)!=5)throw new AssertionError("A caught arrow must consume one visible plate");
            });
            case 277 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 280 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 281 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 283 -> pending="cryo-skill-4-armor-countershot.png";
            case 294 -> server(mc,p->{
                if(com.jayptucker.heroesevolved.cryo.CryoCombatSkills.armorPlates(p)!=0||victim.getHealth()>=100)throw new AssertionError("Armor follow-up must launch actual aimed shards");
                System.out.println("CRYO_SKILLS_ARMOR PASS");
            });
            case 310 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);server(mc,p->{
                scene(p,600.5,0,25);victim=target(p,600.5,3.5);CryoService.chill(victim,60,p);
                var frozen=target(p,602.5,5);CryoService.freezeSolid(frozen,80,p);target(p,598.5,6.5);bind(p,"cryokinesis_2_1","");
            });}
            case 320 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 321 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 326 -> pending="cryo-skill-5-cold-snap-front.png";
            case 332 -> pending="cryo-skill-6-cold-snap-fracture.png";
            case 342 -> server(mc,p->{
                if(!CryoService.isFrozen(victim)||CryoService.isFrozen(targets.get(1))||targets.get(1).getHealth()>=100||CryoService.frostAmount(targets.get(2))<=0)
                    throw new AssertionError("Cold Snap must freeze prepared targets, shatter frozen ones, and chill fresh targets");
                System.out.println("CRYO_SKILLS_SNAP PASS");
            });
            case 360 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->{scene(p,800.5,0,-25);bind(p,"cryokinesis_rime_loom","cryokinesis_suspension_stitch");});}
            case 375 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 376 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 388 -> pending="cryo-skill-7-growing-loom.png";
            case 392 -> server(mc,p->p.connection.teleport(p.getX(),p.getY(),p.getZ(),-45,-25));
            case 395 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 396 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 410 -> server(mc,p->{p.connection.teleport(807.5,-57,-3.5,0,0);p.setNoGravity(true);view(p,new Vec3(806,-58,12));});
            case 420 -> pending="cryo-skill-8-guided-loom.png";
            case 452 -> pending="cryo-skill-9-solid-woven-ramp.png";
            case 456 -> server(mc,p->{
                var cells=com.jayptucker.heroesevolved.cryo.CryoTechniqueService.loomBlocks(p);
                if(cells.size()<24||cells.stream().mapToInt(BlockPos::getY).max().orElse(0)<=cells.stream().mapToInt(BlockPos::getY).min().orElse(0)
                        ||cells.stream().mapToInt(BlockPos::getX).max().orElse(0)-cells.stream().mapToInt(BlockPos::getX).min().orElse(0)<4)
                    throw new AssertionError("Real Rime Loom keys must guide a growing physical stair path");
                System.out.println("CRYO_SKILLS_LOOM PASS cells="+cells.size());
            });
            case 470 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);server(mc,p->{scene(p,1000.5,0,0);p.setHealth(10);bind(p,"cryokinesis_stillheart","cryokinesis_second_spring");});}
            case 480 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 481 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 486 -> server(mc,p->{
                if(!com.jayptucker.heroesevolved.cryo.CryoTechniqueService.preserved(p))throw new AssertionError("Stillheart must shelter its own caster");
                p.hurt(p.damageSources().generic(),10);
            });
            case 493 -> pending="cryo-skill-10-pressure-cocoon.png";
            case 500 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 501 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 503 -> pending="cryo-skill-11-cocoon-opening.png";
            case 510 -> server(mc,p->{
                if(com.jayptucker.heroesevolved.cryo.CryoTechniqueService.preserved(p)||p.getHealth()<=10)throw new AssertionError("Second Stillheart input must open a self-cocoon early and recover stored pressure");
                System.out.println("CRYO_SKILLS_COCOON PASS");
            });
            case 530 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->{
                scene(p,1200.5,0,0);victim=target(p,1200.5,5.5);target(p,1203,6.5);target(p,1198,7.5);target(p,1201.5,9);
                bind(p,"cryokinesis_brittle_constellation","cryokinesis_shattered_sky");view(p,victim.getBoundingBox().getCenter());
            });}
            case 540 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 541 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 547 -> pending="cryo-skill-12-crystal-constellation.png";
            case 550 -> server(mc,p->CryoService.chill(victim,90,p));
            case 555 -> server(mc,p->CryoService.freezeSolid(victim,80,p));
            case 562 -> server(mc,p->CryoService.shatterHit(p,victim,1));
            case 565 -> pending="cryo-skill-13-travelling-fracture.png";
            case 571 -> pending="cryo-skill-14-linked-shatter.png";
            case 585 -> server(mc,p->{
                if(targets.get(1).getHealth()>=100||!CryoService.isFrozen(targets.get(1))||com.jayptucker.heroesevolved.cryo.CryoTechniqueService.linkedCount(p)!=0)
                    throw new AssertionError("Constellation must share Frost, spend the network and propagate staged prepared fractures");
                System.out.println("CRYO_SKILLS_CONSTELLATION PASS");CryoService.stopAll(p);
            });
            case 610 -> mc.stop();
            default -> { }
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(pending==null)return;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);
            System.out.println("CRYO_SKILL_CAPTURE "+out);
        }catch(Exception ex){throw new RuntimeException(ex);}pending=null;
    }
}
