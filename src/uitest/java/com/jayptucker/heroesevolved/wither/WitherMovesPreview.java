package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Photographs the Wither's other ways to fight: a Breach Step (both tears opening, coming through, out behind you), Tail
 * Reap (coiled, sweeping, its trail), the Bone Cage (its marks, closing, holding, broken by a power) and a Starving Well
 * (swelling in its mouth, drifting, about to collapse, collapsing). Flag {@code heroes.preview.witherMoves}; shots in
 * {@code build/wither-moves-preview/screenshots/}.
 */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class WitherMovesPreview {
    private static boolean started;private static int startup,age;private static String capture;
    private static void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("WITHER_MOVES FAIL "+e);}});}
    private static void look(Vec3 eye,Vec3 at){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(eye,at);}
    private static RiftWitherEntity boss(){var b=WitherArena.boss();if(b==null)throw new IllegalStateException("No Wither fight");return b;}
    private static void at(ServerPlayer p,Vec3 feet){p.teleportTo(p.serverLevel(),feet.x,feet.y,feet.z,0,0);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,boss().core());p.connection.teleport(feet.x,feet.y,feet.z,p.getYRot(),p.getXRot());}
    private static void reset(ServerPlayer p){var b=boss();b.cancelChain();b.stagger=b.transition=b.crash=0;b.setPos(WitherGeometry.floor().add(0,.35,0));p.setHealth(p.getMaxHealth());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.witherMoves"))return;var mc=Minecraft.getInstance();
        if(!started&&++startup>35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(7);mc.options.guiScale().set(2);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Wither-Moves-"+System.currentTimeMillis(),new LevelSettings("Wither Moves",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);age++;
        mc.getToasts().clear();ClientScreenTintState.clear();
        Vec3 floor=WitherGeometry.floor();
        switch(age){
            case 104 -> server(PrologueService::skip);
            case 115 -> server(p->{CryoChamberService.release(p);p.removeAllEffects();WitherArena.reset(p.getServer(),false);p.teleportTo(p.getServer().overworld(),150.5,-60,150.5,0,0);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.SUPERSPEED_ID));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);});
            case 140 -> server(p->PreviewQuiet.report("WITHER_MOVES_BREACH "+WitherArena.operatorStart(p,0)));
            case 330 -> server(p->{PreviewQuiet.report("WITHER_MOVES_ENTER "+WitherArena.operatorStart(p,1));var b=boss();b.skipArrival();b.still(true);b.holdOff(100000);b.unward();p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();});
            // Breach Step: it steps through a tear and out of another past you.
            case 420 -> {mc.options.hideGui=true;server(p->{reset(p);at(p,floor.add(0,0,-11));});look(floor.add(15,7,-6),floor.add(0,3,-13));}
            case 430 -> server(p->boss().start(WitherGeometry.Move.STEP));
            case 440 -> capture="01-breach-step-tears-open.png";
            case 448 -> capture="02-coming-through-behind-you.png";
            case 453 -> {capture="03-out-of-the-tear.png";server(p->{var b=boss();PreviewQuiet.report("WITHER_MOVES_STEP "+(b.position().distanceTo(p.position())<8&&b.getZ()<p.getZ()?"PASS":"FAIL")+" at "+b.position());});}
            // Tail Reap: it coils with you at its back, then spins a full circle.
            case 480 -> {server(p->{reset(p);var b=boss();b.setYRot(0);b.yBodyRot=0;at(p,floor.add(0,0,-5.5));});look(floor.add(-10,10,9),floor.add(0,1,-1));}
            case 486 -> server(p->boss().start(WitherGeometry.Move.TAIL));
            case 495 -> capture="04-tail-coils.png";
            case 503 -> capture="05-tail-sweeps.png";
            case 507 -> capture="06-the-sweep's-trail.png";
            // Bone Cage: ribs thrown round you; a power breaks you out.
            case 540 -> server(p->{var b=boss();b.operatorPhase();b.transition=0;reset(p);at(p,floor.add(0,0,10));});
            case 560 -> {look(floor.add(9,8,17),floor.add(0,1,10));server(p->boss().start(WitherGeometry.Move.CAGE));}
            case 570 -> capture="07-ribs-mark-the-ring.png";
            case 580 -> {capture="08-caged.png";server(p->PreviewQuiet.report("WITHER_MOVES_CAGE "+(boss().cage!=null&&boss().cage.held.containsKey(p.getUUID())?"PASS":"FAIL")));}
            case 596 -> capture="09-the-cage-holds.png";
            case 600 -> server(BossLocks::markUsed);
            case 604 -> {capture="10-a-power-breaks-the-bars.png";server(p->PreviewQuiet.report("WITHER_MOVES_BREAK "+(boss().cage==null?"PASS":"FAIL")));}
            // Starving Well: a hungry little star drifts after the farthest fighter and collapses.
            case 640 -> server(p->{var b=boss();b.operatorPhase();b.transition=0;b.unward();reset(p);at(p,floor.add(2,0,20));});
            case 660 -> {look(floor.add(-12,7,6),floor.add(0,3,10));server(p->boss().start(WitherGeometry.Move.WELL));}
            case 670 -> capture="11-the-star-swells-in-its-mouth.png";
            case 680 -> server(p->PreviewQuiet.report("WITHER_MOVES_WELL "+(boss().wells.size()==1?"PASS":"FAIL")));
            case 700 -> capture="12-the-well-drifts.png";
            case 730 -> {look(floor.add(-9,6,10),floor.add(2,1.5,18));capture=null;}
            case 760 -> capture="13-about-to-collapse.png";
            case 774 -> capture="14-collapse.png";
            case 800 -> PreviewQuiet.quit();
            default -> {}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var path=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(path.getParent());shot.writeToFile(path);System.out.println("WITHER_MOVES_CAPTURE "+capture);
        }catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
