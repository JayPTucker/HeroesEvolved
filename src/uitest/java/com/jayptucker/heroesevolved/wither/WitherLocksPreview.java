package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.admin.AdminService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gui.screen.*;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
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
 * Photographs the Wither's locks (combat/BossLocks): the Star Ward and its three hearts, the glint a player's own power
 * sees, an aimed override, a heart shattering and re-forming, the ward breaking and throwing it down, a feeding heart,
 * the brood carrying bone back, the Hunger Mark, the open star, the discharge's last moment and a dodge out of it, and
 * the Guide's lines for the player's power. Flag {@code heroes.preview.witherLocks}; shots in
 * {@code build/wither-locks-preview/screenshots/}.
 */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class WitherLocksPreview {
    private static boolean started,spared;private static int startup,age;private static String capture;private static float before;
    private static void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("WITHER_LOCKS FAIL "+e);}});}
    private static void look(Vec3 eye,Vec3 at){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(eye,at);}
    private static void power(ServerPlayer p,ResourceLocation power){
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(power));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);
    }
    private static RiftWitherEntity boss(){var b=WitherArena.boss();if(b==null)throw new IllegalStateException("No Wither fight");return b;}
    private static void at(ServerPlayer p,Vec3 feet,Vec3 lookAt){p.teleportTo(p.serverLevel(),feet.x,feet.y,feet.z,0,0);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,lookAt);p.connection.teleport(feet.x,feet.y,feet.z,p.getYRot(),p.getXRot());}
    private static void click(String text){var mc=Minecraft.getInstance();if(mc.screen instanceof AdminScreen screen){for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().contains(text)&&button.active){screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+button.getHeight()/2D,0);return;}}PreviewQuiet.report("WITHER_LOCKS_CLICK FAIL "+text);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.witherLocks"))return;var mc=Minecraft.getInstance();
        if(!started&&++startup>35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(7);mc.options.guiScale().set(2);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Wither-Locks-"+System.currentTimeMillis(),new LevelSettings("Wither Locks",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);age++;
        mc.getToasts().clear();ClientScreenTintState.clear();
        Vec3 floor=WitherGeometry.floor();
        switch(age){
            case 104 -> server(PrologueService::skip);
            case 115 -> server(p->{CryoChamberService.release(p);p.removeAllEffects();WitherArena.reset(p.getServer(),false);p.teleportTo(p.getServer().overworld(),150.5,-60,150.5,0,0);power(p,ModAbilities.MIMICRY_ID);});
            case 140 -> server(p->PreviewQuiet.report("WITHER_LOCKS_BREACH "+WitherArena.operatorStart(p,0)));
            case 330 -> server(p->{PreviewQuiet.report("WITHER_LOCKS_ENTER "+WitherArena.operatorStart(p,1));var b=boss();b.skipArrival();b.still(true);b.holdOff(100000);p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;});
            // The ward: a lattice over its ribs and a thread from each heart in its pylon.
            case 440 -> {mc.options.hideGui=true;look(floor.add(-16,9,20),floor.add(-3,4,-2));server(p->at(p,floor.add(6,0,18),boss().core()));}
            case 460 -> capture="01-warded-by-three-hearts.png";
            case 470 -> {look(floor.add(-8,5,2),floor.add(0,5,0));}
            case 480 -> capture="02-the-star-ward.png";
            // A heart in its cradle, as an empath sees it: the turning diamond only a power with a key to it shows.
            case 490 -> {var c=WitherGeometry.heartCenter(0);look(c.add(0,.7,4.6),c);server(p->at(p,c.add(1,-2.5,9),c));}
            case 505 -> capture="03-heart-and-its-glint.png";
            case 515 -> {var c=WitherGeometry.heartCenter(2);look(c.add(-4.6,1.2,-6.5),c.add(0,-.6,-2.8));server(p->at(p,new Vec3(c.x,floor.y,c.z-6),c));}
            case 525 -> server(p->{before=boss().hearts[2].getHealth();BossLocks.used(p,ModAbilities.MIMICRY_ID,"preview",20);
                PreviewQuiet.report("WITHER_LOCKS_AIMED "+(boss().hearts[2]!=null&&boss().hearts[2].getHealth()<before?"PASS":"FAIL")+" "+before+" -> "+(boss().hearts[2]==null?0:boss().hearts[2].getHealth()));});
            case 528 -> capture="04-empath-severs-a-heart.png";
            case 545 -> {var c=WitherGeometry.heartCenter(2);look(c.add(0,.5,-4.2),c);}
            case 552 -> capture="05-heart-cracked.png";
            case 556 -> server(p->{var h=boss().hearts[2];h.take(h.getHealth()+1,p);});
            case 562 -> capture="06-heart-shatters.png";
            case 575 -> server(p->{var b=boss();b.mendAt[2]=b.clock+WitherGeometry.HEART_MEND/2;});
            case 590 -> capture="07-a-broken-heart-reforming.png";
            case 600 -> {look(floor.add(-12,8,14),floor.add(0,4,0));}
            case 610 -> capture="08-ward-thinned.png";
            // Break the rest: the ward shatters off its ribs and throws it down.
            case 620 -> {look(floor.add(-13,7,13),floor.add(0,4,0));server(p->{var b=boss();b.setPos(floor.add(0,6,0));b.mendAt[2]=-1;for(int i=0;i<3;i++)if(b.hearts[i]!=null)b.hearts[i].take(1000,p);
                PreviewQuiet.report("WITHER_LOCKS_WARD "+(!b.ward&&b.crash>0?"PASS":"FAIL"));});}
            case 626 -> capture="09-ward-shatters.png";
            case 640 -> capture="10-glass-falls.png";
            case 660 -> capture="11-thrown-down.png";
            // The broken vessel: a heart lights when it draws on it; its brood carries bone back.
            case 700 -> server(p->{var b=boss();b.operatorPhase();b.transition=0;b.crash=0;b.cancelChain();b.setPos(floor.add(0,.35,0));});
            case 720 -> {var c=WitherGeometry.heartCenter(1);look(c.add(-1.2,1.4,5.6),c.add(-2.5,0,0));server(p->{at(p,c.add(-2,-2.5,5),boss().core());boss().start(WitherGeometry.Move.RAIN);});}
            case 735 -> capture="12-feeding-heart.png";
            case 760 -> server(p->{var b=boss();b.cancelChain();var cage=p.serverLevel();for(int i=0;i<2;i++){var s=EntityType.WITHER_SKELETON.create(cage);WitherMoves.brood(s);
                s.moveTo(floor.add(-9+i*2,0,9-i));cage.addFreshEntity(s);b.minions.add(s);s.tickCount=WitherGeometry.BROOD_FLIGHT+WitherGeometry.BROOD_RETURN+1;}b.setHealth(b.getMaxHealth()*.5F);before=b.getHealth();});
            case 762 -> look(floor.add(-14,6,16),floor.add(-3,3,3));
            case 790 -> capture="13-brood-carries-bone-back.png";
            case 860 -> {server(p->PreviewQuiet.report("WITHER_LOCKS_MEND "+(boss().getHealth()>before?"PASS":"FAIL")+" "+before+" -> "+boss().getHealth()));}
            case 862 -> server(p->{var b=boss();for(var m:java.util.List.copyOf(b.minions))m.discard();b.minions.clear();at(p,floor.add(5,0,9),b.core());
                p.setGameMode(GameType.SURVIVAL);p.addEffect(new MobEffectInstance(ModItems.HUNGERING,400));});
            case 866 -> look(floor.add(12,5,4),floor.add(2,3,4));
            case 885 -> capture="14-hunger-mark.png";
            // Critical mass, as a pyrokinetic sees it: the open star glints for a power that hits it there.
            case 900 -> server(p->{p.removeEffect(ModItems.HUNGERING);var b=boss();b.operatorPhase();b.transition=0;b.cancelChain();b.setPos(floor.add(0,.35,0));power(p,ModAbilities.PYROKINESIS_ID);at(p,floor.add(0,0,12),b.core());});
            case 925 -> look(floor.add(6,6,10),boss_core());
            case 935 -> capture="15-open-star-glint.png";
            case 945 -> {look(floor.add(-10,8,16),floor.add(0,4,0));server(p->{p.getAbilities().invulnerable=false;p.setHealth(20);at(p,floor.add(3,0,12),boss().core());before=p.getHealth();boss().start(WitherGeometry.Move.HORIZON);});}
            default -> {}
        }
        // The discharge's last moment (the white ring closing), a power used in it, and the fighter carried out of it.
        if(age>945){
            var list=mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(90));
            if(!list.isEmpty()){
                var b=list.getFirst();
                if(b.action()==WitherGeometry.Move.HORIZON){
                    int left=b.actionWindup()-(int)b.actionAge(0);
                    if(left==8&&capture==null)capture="16-the-last-moment.png";
                    if(left==6)server(p->BossLocks.markUsed(p));
                    if(left==-3&&capture==null&&!spared){spared=true;capture="17-slipped-out-of-it.png";}
                }
            }
        }
        switch(age){
            case 1020 -> server(p->PreviewQuiet.report("WITHER_LOCKS_SPARED "+(p.getHealth()>=before?"PASS":"FAIL")+" "+before+" -> "+p.getHealth()));
            case 1030 -> {mc.options.hideGui=false;TempestCamera.fix(null,null);var g=new GuideScreen(null).topic("wither");mc.setScreen(g);}
            case 1036 -> {if(mc.screen!=null)for(int i=0;i<12;i++)mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth()/2D,mc.getWindow().getGuiScaledHeight()/2D,0,-1);}
            case 1045 -> capture="18-guide-your-power.png";
            case 1060 -> {mc.setScreen(null);AdminScreen.showStory();server(AdminService::open);}
            case 1080 -> click("Wither: Containment Failure");
            case 1095 -> capture="19-admin-status.png";
            case 1110 -> PreviewQuiet.quit();
            default -> {}
        }
    }
    private static Vec3 boss_core(){var mc=Minecraft.getInstance();var list=mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(90));return list.isEmpty()?WitherGeometry.floor().add(0,5,0):list.getFirst().core();}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var path=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(path.getParent());shot.writeToFile(path);System.out.println("WITHER_LOCKS_CAPTURE "+capture);
        }catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
