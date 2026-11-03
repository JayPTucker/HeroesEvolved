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
public final class CryoPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Mob victim;
    private static Vec3 rideStart;
    private CryoPreview() {}
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }
    private static void scene(ServerPlayer p, double x, float yaw, float pitch) {
        CryoService.stopAll(p);
        if (victim != null) { victim.discard(); victim = null; }
        p.connection.teleport(x, -60, .5, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO); p.setOnGround(true); p.resetFallDistance();
        PlayerEnergyService.restore(p, 999);
        com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
    }
    private static Mob target(ServerPlayer p, double x, double z) {
        var t = EntityType.PILLAGER.create(p.serverLevel());
        t.setNoAi(true); t.setPersistenceRequired();
        t.moveTo(x, -60, z, 180, 0); p.serverLevel().addFreshEntity(t); return t;
    }
    private static void view(ServerPlayer p, Vec3 at) {
        Vec3 delta = at.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.cryo")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started=true; mc.options.pauseOnLostFocus=false; mc.options.guiScale().set(2);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Cryo-"+System.currentTimeMillis(),
                    new LevelSettings("Cryokinesis preview",GameType.SURVIVAL,false,Difficulty.EASY,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L,false,false),
                    access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.level==null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        switch(++world) {
            case 180 -> server(mc,p->{
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.CRYOKINESIS_ID));
                PlayerProgressionService.setLevel(p,5); PlayerPowerSyncService.sync(p);
                p.serverLevel().setDayTime(10000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                scene(p,200.5,0,3);victim=target(p,200.5,6.5);view(p,victim.getBoundingBox().getCenter());
            });
            case 195 -> {mc.gui.getChat().clearMessages(true);mc.getToasts().clear();}
            case 200 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 210 -> pending="cryo-1-rime-stream.png";
            case 225 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 232 -> server(mc,p->{
                if(CryoKitService.isStreaming(p)||CryoService.frostAmount(victim)<=0&&!CryoService.isFrozen(victim))throw new AssertionError("Rime Stream must build Frost and end on release");
                System.out.println("CRYO_CLIENT_RIME PASS frost="+CryoService.frostAmount(victim));
            });
            case 245 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->scene(p,400.5,0,30));}
            case 255 -> ModKeyMappings.ABILITY_2.setDown(true);
            case 256 -> ModKeyMappings.ABILITY_2.setDown(false);
            case 269 -> server(mc,p->{
                if(CryoKitService.constructBlocks(p).isEmpty())throw new AssertionError("Sculpt must grow real terrain");
                var blocks=CryoKitService.constructBlocks(p);
                Vec3 center=Vec3.atCenterOf(blocks.get(blocks.size()/2));
                p.connection.teleport(405.5,-60,-2.5,40,0);view(p,center);
                System.out.println("CRYO_CLIENT_SCULPT PASS blocks="+blocks.size());
            });
            case 275 -> pending="cryo-2-solid-sculpt-wall.png";
            case 290 -> server(mc,p->scene(p,600.5,0,35));
            case 300 -> {mc.options.keyShift.setDown(true);ModKeyMappings.ABILITY_2.setDown(true);}
            case 301 -> {ModKeyMappings.ABILITY_2.setDown(false);mc.options.keyShift.setDown(false);}
            case 315 -> server(mc,p->{
                var blocks=CryoKitService.constructBlocks(p);
                if(blocks.isEmpty())throw new AssertionError("Shift Sculpt must grow a ramp");
                Vec3 center=Vec3.atCenterOf(blocks.get(blocks.size()/2));
                p.connection.teleport(605.5,-60,-2.5,40,0);view(p,center);
            });
            case 320 -> pending="cryo-3-solid-sculpt-ramp.png";
            case 335 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);server(mc,p->{
                scene(p,800.5,0,0);rideStart=p.position();
                // A six-block water crossing makes the solid riding ribbon visible beneath the skates.
                for(int x=-2;x<=2;x++)for(int z=4;z<=10;z++)p.serverLevel().setBlock(BlockPos.containing(800.5+x,-61,z),Blocks.WATER.defaultBlockState(),3);
            });}
            case 345 -> ModKeyMappings.ABILITY_3.setDown(true);
            case 346 -> {ModKeyMappings.ABILITY_3.setDown(false);mc.options.keyUp.setDown(true);}
            case 360 -> pending="cryo-4-glacier-ribbon.png";
            case 366 -> mc.player.setYRot(mc.player.getYRot()+12);
            case 370 -> pending="cryo-5-glacier-carve.png";
            case 375 -> {mc.options.keyJump.setDown(true);mc.player.setXRot(-12);}
            case 382 -> pending="cryo-6-rising-ice-ramp.png";
            case 385 -> {mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);}
            case 392 -> server(mc,p->{
                if(p.position().distanceTo(rideStart)<2)throw new AssertionError("Real Glacier Ride controls must move the skater");
                System.out.println("CRYO_CLIENT_RIDE PASS moved="+p.position().distanceTo(rideStart));
                CryoRideService.stop(p,true);
            });
            case 410 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->{scene(p,1000.5,0,0);victim=target(p,1000.5,5.5);CryoService.chill(victim,60,p);view(p,victim.getBoundingBox().getCenter());});}
            case 420 -> ModKeyMappings.ABILITY_4.setDown(true);
            case 421 -> ModKeyMappings.ABILITY_4.setDown(false);
            case 430 -> pending="cryo-7-layered-prison.png";
            case 435 -> server(mc,p->{
                if(!CryoService.isFrozen(victim)||!CryoKitService.hasPrison(p))throw new AssertionError("Prepared Frost must create Crystal Prison");
                System.out.println("CRYO_CLIENT_PRISON PASS");
            });
            case 440 -> ModKeyMappings.ABILITY_4.setDown(true);
            case 441 -> ModKeyMappings.ABILITY_4.setDown(false);
            case 444 -> pending="cryo-8-shatter-shards.png";
            case 450 -> server(mc,p->{
                if(CryoService.isFrozen(victim)||CryoKitService.hasPrison(p))throw new AssertionError("Second prison key must shatter the prepared shell");
                System.out.println("CRYO_CLIENT_SHATTER PASS");
            });
            case 465 -> server(mc,p->{scene(p,1200.5,0,20);victim=target(p,1200.5,7.5);});
            case 475 -> ModKeyMappings.ABILITY_5.setDown(true);
            case 476 -> ModKeyMappings.ABILITY_5.setDown(false);
            case 485 -> pending="cryo-9-whiteout-growing.png";
            case 515 -> pending="cryo-10-whiteout-spires.png";
            case 535 -> server(mc,p->{
                p.connection.teleport(1210.5,-56,-9.5,0,0);view(p,new Vec3(1200.5,-59,5));
            });
            case 543 -> pending="cryo-10b-whiteout-field.png";
            case 550 -> {mc.options.keyShift.setDown(true);ModKeyMappings.ABILITY_5.setDown(true);}
            case 551 -> {ModKeyMappings.ABILITY_5.setDown(false);mc.options.keyShift.setDown(false);}
            case 555 -> pending="cryo-11-whiteout-collapse.png";
            case 565 -> server(mc,p->{
                if(CryoKitService.hasWhiteout(p))throw new AssertionError("Whiteout collapse must stop its active region");
                System.out.println("CRYO_CLIENT_WHITEOUT PASS");CryoService.stopAll(p);
            });
            case 575 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);server(mc,p->{
                scene(p,1400.5,0,0);
                p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty());PlayerPowerSyncService.sync(p);
                if(!CryoService.freezeSolid(p,60,null))throw new AssertionError("The local player must be trapped to exercise real shell inputs");
            });}
            case 582 -> pending="cryo-12-trapped-player.png";
            case 585,595,605 -> mc.options.keyJump.setDown(true);
            case 586,596,606 -> mc.options.keyJump.setDown(false);
            case 590,600 -> mc.options.keyAttack.setDown(true);
            case 591,601 -> mc.options.keyAttack.setDown(false);
            case 603 -> pending="cryo-13-prison-struggle.png";
            case 612 -> server(mc,p->{
                if(CryoService.isFrozen(p))throw new AssertionError("Alternating real Jump/Attack edges must crack the shell before its sixty-tick expiry");
                System.out.println("CRYO_CLIENT_STRUGGLE PASS");
            });
            case 625 -> mc.stop();
            default -> { }
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(pending==null)return;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);
            System.out.println("CRYO_CAPTURE "+out);
        }catch(Exception ex){throw new RuntimeException(ex);}pending=null;
    }
}
