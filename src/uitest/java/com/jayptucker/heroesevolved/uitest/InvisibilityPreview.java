package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.invisibility.OpticalDecoyService;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/** Exercises actual survival controls and photographs optical effects from useful camera angles. */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class InvisibilityPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Mob pursuer;
    private static Vec3 stepStart, veilStart, hiddenStart;
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server=mc.getSingleplayerServer();
        server.execute(()->{var p=server.getPlayerList().getPlayer(mc.player.getUUID());if(p!=null)action.accept(p);});
    }
    private static void scene(ServerPlayer p,double x) {
        InvisibilityService.forget(p); OpticalDecoyService.clear(p);
        p.connection.teleport(x,-60,.5,0,0); p.setDeltaMovement(Vec3.ZERO);p.resetFallDistance();
        p.setHealth(p.getMaxHealth()); PlayerEnergyService.restore(p,999);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("heroes.preview.invisibility"))return;
        var mc=Minecraft.getInstance();
        if(!started&&++ticks>40&&mc.screen!=null&&mc.getOverlay()==null) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.hideGui=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Optical-"+System.currentTimeMillis(),
                    new LevelSettings("Invisibility preview",GameType.SURVIVAL,false,Difficulty.EASY,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(92L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.level==null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        switch(++world) {
            case 180 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc,p->{
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().assignDormant(ModAbilities.INVISIBILITY_ID).unlock(ModAbilities.INVISIBILITY_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.INVISIBILITY_ID));
                    PlayerProgressionService.setLevel(p,3);PlayerPowerSyncService.sync(p);scene(p,400.5);
                    p.serverLevel().setDayTime(11000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                    p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
                    p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
                    pursuer=EntityType.HUSK.create(p.serverLevel());pursuer.moveTo(400.5,-60,12.5,180,0);
                    pursuer.setPersistenceRequired();p.serverLevel().addFreshEntity(pursuer);pursuer.setTarget(p);
                });
            }
            case 190 -> {mc.gui.getChat().clearMessages(true);mc.getToasts().clear();pending="optic-1-visible-equipment.png";}
            case 195 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 196 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 199 -> pending="optic-2-layered-dissolve.png";
            case 213 -> {
                pending="optic-3-concealed-body.png";
                server(mc,p->{
                    if(!InvisibilityService.isUnseen(p)||pursuer.getTarget()==p)throw new AssertionError("Fade must conceal its owner and really clear the pursuing target");
                    hiddenStart=p.position();System.out.println("OPTIC_CLIENT_FADE PASS");
                });
            }
            case 215 -> mc.options.keyRight.setDown(true);
            case 220 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);pending="optic-4-faint-hands.png";}
            case 226 -> mc.options.keyRight.setDown(false);
            case 228 -> server(mc,p->{
                var clue=pursuer.getNavigation().getTargetPos();
                double moved=p.position().distanceTo(hiddenStart);
                if(moved<.7||pursuer.getTarget()==p||clue==null||Math.abs(clue.getX()+.5-p.getX())<.4)
                    throw new AssertionError("Live hunter must search the stale position instead of tracking the hidden sidestep: moved="+moved+" clue="+clue+" player="+p.position());
                System.out.println("OPTIC_CLIENT_LAST_KNOWN_SEARCH PASS moved="+moved+" clue="+clue);
                pursuer.discard();
            });
            case 230 -> {mc.options.keyUp.setDown(true);mc.options.keySprint.setDown(true);}
            case 238 -> server(mc,p->InvisibilityService.addExposure(p,30));
            case 247 -> pending="optic-5-moving-exposure.png";
            case 250 -> {
                mc.options.keyUp.setDown(false);mc.options.keySprint.setDown(false);
                server(mc,p->{if(InvisibilityService.exposure(p)<=0)throw new AssertionError("Moving hidden must generate Exposure");System.out.println("OPTIC_CLIENT_EXPOSURE PASS value="+InvisibilityService.exposure(p));});
            }
            case 260 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);server(mc,InvisibilityService::unfade);}
            case 264 -> pending="optic-6-reform.png";
            case 280 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->scene(p,800.5));}
            case 285 -> ModKeyMappings.ABILITY_3.setDown(true);
            case 286 -> ModKeyMappings.ABILITY_3.setDown(false);
            case 297 -> pending="optic-7-public-mirage.png";
            case 301 -> {
                mc.options.keyAttack.setDown(true);
                net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
            }
            case 302 -> mc.options.keyAttack.setDown(false);
            case 304 -> pending="optic-8-mirage-shatter.png";
            case 312 -> server(mc,p->{if(OpticalDecoyService.count(p)!=0)throw new AssertionError("A real client hit must dissolve the nearby Mirage");System.out.println("OPTIC_CLIENT_MIRAGE_INTERACT PASS");});
            case 320 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc,p->{scene(p,1000.5);stepStart=p.position();
                    for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)p.serverLevel().setBlock(BlockPos.containing(1000.5+x,-60+y,6),Blocks.STONE.defaultBlockState(),3);
                });
            }
            case 330 -> ModKeyMappings.ABILITY_2.setDown(true);
            case 331 -> ModKeyMappings.ABILITY_2.setDown(false);
            case 334 -> pending="optic-9-refraction-step.png";
            case 350 -> server(mc,p->{
                if(p.position().distanceTo(stepStart)<2||p.getZ()>5.75)throw new AssertionError("Refraction Step must move physically and stop at the solid wall: "+p.position());
                System.out.println("OPTIC_CLIENT_STEP_COLLISION PASS distance="+p.position().distanceTo(stepStart));
            });
            case 370 -> server(mc,p->{scene(p,1200.5);veilStart=p.position();PlayerProgressionService.setLevel(p,5);PlayerPowerSyncService.sync(p);});
            case 380 -> ModKeyMappings.ABILITY_4.setDown(true);
            case 381 -> ModKeyMappings.ABILITY_4.setDown(false);
            case 385 -> mc.options.keyUp.setDown(true);
            case 395 -> pending="optic-10-moving-veil.png";
            case 400 -> {
                mc.options.keyUp.setDown(false);
                server(mc,p->{if(!InvisibilityService.veilActive(p)||p.position().distanceTo(veilStart)<.5)throw new AssertionError("Shared Veil must follow actual movement");System.out.println("OPTIC_CLIENT_VEIL PASS");});
            }
            case 420 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);server(mc,p->scene(p,1400.5));}
            case 430 -> ModKeyMappings.ABILITY_5.setDown(true);
            case 431 -> ModKeyMappings.ABILITY_5.setDown(false);
            case 443 -> pending="optic-11-ghost-protocol.png";
            case 450 -> server(mc,p->{if(!InvisibilityService.ghostActive(p)||OpticalDecoyService.count(p)!=3)throw new AssertionError("Ghost Protocol must cast three distinct public optical echoes");System.out.println("OPTIC_CLIENT_GHOST PASS");});
            case 480 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc,p->{InvisibilityService.forget(p);if(OpticalDecoyService.count(p)!=0||InvisibilityService.isFaded(p))throw new AssertionError("Power cleanup must remove cloak and all doubles");System.out.println("OPTIC_CLIENT_CLEANUP PASS");});
            }
            case 484 -> pending="optic-12-final-reform.png";
            case 505 -> mc.stop();
            default -> {}
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(pending==null)return;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);
            System.out.println("OPTIC_CAPTURE "+out);
        }catch(Exception ex){throw new RuntimeException(ex);}pending=null;
    }
}
