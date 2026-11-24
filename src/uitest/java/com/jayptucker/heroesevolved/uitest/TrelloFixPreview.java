package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.cryo.*;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.*;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.shapeshift.*;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.function.Consumer;

/** Real key holds, stable frozen poses, targetable doubles, both cyclones, and the new overexertion look. */
@EventBusSubscriber(modid="heroesevolved", value=Dist.CLIENT)
public final class TrelloFixPreview {
    private static int startup, age, photos;
    private static boolean started;
    private static String pending;
    private static Mob frozen;
    private static final Vec3 STAGE = new Vec3(150.5, -60, 150.5);
    private static void server(Consumer<ServerPlayer> action) {
        var mc=Minecraft.getInstance(); var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable t) { PreviewQuiet.report("TRELLO_PREVIEW FAIL: "+t); }
        });
    }
    private static void reset(ServerPlayer p, ResourceLocation power) {
        CryoService.stopAll(p); FlightService.stopForPowerRemoval(p);
        com.jayptucker.heroesevolved.speed.SpeedService.forget(p); ShapeshiftService.forget(p);
        for (Entity e:p.serverLevel().getAllEntities()) if (e instanceof Mob) e.discard();
        com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(p);
        com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerAbilityService.replaceWithAbility(p,power);
        PlayerProgressionService.setPowerLevel(p,20);
        com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
        p.setGameMode(GameType.SURVIVAL); p.removeAllEffects(); p.setHealth(p.getMaxHealth());
        p.setNoGravity(false); p.setDeltaMovement(Vec3.ZERO);
        var level=p.getServer().overworld();
        for(int x=7;x<=12;x++) for(int z=7;z<=12;z++) level.setChunkForced(x,z,true);
        level.setDayTime(6000);
        p.teleportTo(level,STAGE.x,STAGE.y,STAGE.z,0,0);
        PlayerEnergyService.restore(p,999); PlayerPowerSyncService.sync(p);
        System.out.println("TRELLO_STAGE "+p.level().dimension().location()+" on "+level.getBlockState(p.blockPosition().below()));
    }
    private static void camera(Vec3 at,Vec3 focus) {
        var mc=Minecraft.getInstance(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        TempestCamera.fix(at,focus);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("heroes.preview.trellofixes"))return;
        var mc=Minecraft.getInstance();
        if(!started&&++startup>=35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true; mc.options.pauseOnLostFocus=false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.renderDistance().set(6); mc.options.screenEffectScale().set(0D);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Trello-"+System.currentTimeMillis(),
                    new LevelSettings("Trello Fixes",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        age++; mc.getToasts().clear();
        if(age%20==0)ClientScreenTintState.clear();
        if(age==100)server(p->{com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(p);
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);});
        if(age==120){mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.options.hideGui=true;server(p->reset(p,ModAbilities.CRYOKINESIS_ID));}
        if(age>=300&&age<=425)camera(mc.player.position().add(7,3,-7),mc.player.position().add(0,.9,0));
        switch(age){
            case 170 -> server(p->{frozen=EntityType.ZOMBIE.create(p.serverLevel());frozen.setPersistenceRequired();
                frozen.setPos(p.position().add(0,0,4));frozen.setTarget(p);p.serverLevel().addFreshEntity(frozen);});
            case 183 -> server(p->CryoService.freezeSolid(frozen,160,p));
            case 190 -> camera(STAGE.add(6,2,7),STAGE.add(0,1,4));
            case 200 -> pending="01-frozen-side.png";
            case 205 -> pending="02-frozen-same-pose.png";
            case 220 -> {camera(STAGE.add(-4,2,9),STAGE.add(0,1,4));pending="03-frozen-front.png";}
            case 240 -> server(p->{reset(p,ModAbilities.SHAPESHIFTING_ID);ShapeshiftService.morph(p,EntityType.ZOMBIE);ShapeshiftSuspicion.raise(p,100);});
            case 250 -> {TempestCamera.fix(null,null);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;}
            case 260 -> pending="04-morph-cooldown.png";
            case 285 -> {mc.options.hideGui=true;server(p->reset(p,ModAbilities.CRYOKINESIS_ID));}
            case 300 -> com.jayptucker.heroesevolved.input.ModKeyMappings.ABILITY_3.setDown(true);
            case 308 -> mc.options.keyUp.setDown(true);
            case 325 -> pending="05-glacier-running.png";
            case 330 -> mc.options.keyUp.setDown(false);
            case 410 -> {server(p->PreviewQuiet.report("TRELLO_PREVIEW "+(CryoRideService.isRiding(p)?"PASS":"FAIL")+": Glacier Ride held beyond five seconds"));pending="06-glacier-still-held.png";}
            case 430 -> com.jayptucker.heroesevolved.input.ModKeyMappings.ABILITY_3.setDown(false);
            case 440 -> server(p->PreviewQuiet.report("TRELLO_PREVIEW "+(!CryoRideService.isRiding(p)?"PASS":"FAIL")+": Glacier Ride key release"));
            case 460 -> server(p->{reset(p,ModAbilities.SUPERSPEED_ID);
                com.jayptucker.heroesevolved.expansion.AdvancedTechniques.activate(new AbilityUseContext(p,ModAbilities.SUPERSPEED_ID,5,false),"mirage");});
            case 475 -> camera(STAGE.add(10,5,11),STAGE.add(0,1,0));
            case 485 -> pending="07-moving-decoys.png";
            case 505 -> pending="08-moving-decoys-later.png";
            case 540 -> server(p->reset(p,ModAbilities.SUPERSPEED_ID));
            case 555 -> camera(STAGE.add(18,10,18),STAGE.add(0,1,0));
            case 560 -> com.jayptucker.heroesevolved.input.ModKeyMappings.ABILITY_4.setDown(true);
            case 585 -> {mc.getEntityRenderDispatcher().setRenderHitBoxes(true);pending="09-cyclone-real-hitbox.png";}
            case 590 -> mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
            case 700 -> pending="10-cyclone-long-windup.png";
            case 755 -> pending="11-cyclone-rising.png";
            case 785 -> pending="12-speed-tornado.png";
            case 800 -> com.jayptucker.heroesevolved.input.ModKeyMappings.ABILITY_4.setDown(false);
            case 835 -> server(p->{reset(p,ModAbilities.FLIGHT_ID);p.teleportTo(p.serverLevel(),STAGE.x,STAGE.y+12,STAGE.z,0,0);FlightService.startCyclone(p);});
            case 840 -> camera(STAGE.add(32,17,33),STAGE.add(0,9,7));
            case 845 -> pending="13-tempest-start.png";
            case 860 -> pending="14-tempest-gathering.png";
            case 890 -> pending="15-tempest-entry.png";
            case 930 -> pending="16-tempest-orbit-and-trails.png";
            case 975 -> server(p->FlightService.cancelCyclone(p));
            case 1010 -> server(p->{reset(p,ModAbilities.TELEKINESIS_ID);PlayerEnergyService.consumeUpTo(p,999);OverexertionService.apply(p,30);});
            case 1020 -> {mc.options.hideGui=false;camera(STAGE.add(1.1,1.8,1.8),STAGE.add(0,1.55,0));}
            case 1035 -> pending="17-overexertion-nosebleed.png";
            case 1050 -> {TempestCamera.fix(null,null);mc.options.setCameraType(CameraType.FIRST_PERSON);}
            case 1060 -> pending="18-overexertion-view.png";
            case 1100 -> server(p->{reset(p,ModAbilities.TELEKINESIS_ID);
                var boss=com.jayptucker.heroesevolved.registry.ModEntities.SYLAR.get().create(p.serverLevel());
                boss.actor(true);boss.setPos(STAGE.add(0,0,8));boss.setYRot(180);boss.yBodyRot=180;boss.yHeadRot=180;
                p.serverLevel().addFreshEntity(boss);
                p.setItemSlot(EquipmentSlot.MAINHAND,com.jayptucker.heroesevolved.weapons.PowerWeapons.gun(
                        com.jayptucker.heroesevolved.weapons.PowerWeapons.AGENT_PISTOL));
                com.jayptucker.heroesevolved.weapons.PowerWeapons.draw(p);
            });
            case 1115 -> camera(STAGE.add(6,2.5,3),STAGE.add(0,1.3,7));
            case 1120 -> server(p->{
                String result=com.jayptucker.heroesevolved.weapons.PowerWeapons.fire(p,STAGE.add(0,1.4,8));
                PreviewQuiet.report("TRELLO_PREVIEW "+(result.equals("SUCCESS")?"PASS":"FAIL")+": real TaCZ shot "+result);
            });
            case 1125 -> pending="19-sylar-caught-bullets.png";
            case 1140 -> pending="20-sylar-returned-bullets.png";
            case 1180 -> {mc.options.hideGui=true; server(p->{reset(p,ModAbilities.TELEKINESIS_ID);
                com.jayptucker.heroesevolved.story.prologue.TrelloGuidePreview.stage(p);});}
            case 1190 -> {var at=com.jayptucker.heroesevolved.story.prologue.TrelloGuidePreview.STAGE;
                camera(at.add(8,8,-4),at.add(0,.7,5));}
            case 1200 -> pending="21-guide-street-detour.png";
            case 1210 -> {TempestCamera.fix(null,null);mc.options.setCameraType(CameraType.FIRST_PERSON);}
            case 1220 -> pending="22-guide-from-the-player.png";
            case 1250 -> {PreviewQuiet.report("TRELLO_PREVIEW "+(photos==22?"PASS":"FAIL")+": "+photos+"/22 photographs");PreviewQuiet.quit();}
            default -> {}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(pending==null)return;
        var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);photos++;
        }catch(Exception e){PreviewQuiet.report("TRELLO_PREVIEW FAIL: "+e);}
        pending=null;
    }
}
