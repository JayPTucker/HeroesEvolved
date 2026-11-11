package com.jayptucker.heroesevolved.uitest;
import com.jayptucker.heroesevolved.client.*;
import com.jayptucker.heroesevolved.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class TelepathyPreview {
    static net.minecraft.world.entity.Mob puppet, target; static Vec3 puppetStart;
    static int ticks,world;static boolean started;static String pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("heroes.preview.telepathy"))return;
        Minecraft mc=Minecraft.getInstance();
        if(!started&&++ticks>40&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(3);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Telepathy-"+System.currentTimeMillis(),new LevelSettings("Telepathy preview",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(77L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.level==null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        world++;
        if(world==180)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
            p.connection.teleport(200.5,-60,200.5,0,0);p.serverLevel().setDayTime(11000);
        });
        if(world==225){mc.gui.getChat().clearMessages(true);mc.getToasts().clear();}
        if(world==230)IllusionClient.accept(new IllusionPayload(1,0,mc.player.getId(),new Vec3(200.5,-60,207),new Vec3(200.5,-60,202),180));
        if(world==255)pending="telepathy-double.png";
        if(world==275){IllusionClient.accept(new IllusionPayload(1,-1,0,Vec3.ZERO,Vec3.ZERO,0));IllusionClient.accept(new IllusionPayload(2,3,0,new Vec3(200.5,-60,205),Vec3.ZERO,120));}
        if(world==295)pending="telepathy-chest.png";
        if(world==310){IllusionClient.accept(new IllusionPayload(2,-1,0,Vec3.ZERO,Vec3.ZERO,0));IllusionClient.accept(new IllusionPayload(3,4,0,new Vec3(200.5,-60,206),Vec3.ZERO,120));}
        if(world==330)pending="telepathy-shelter.png";
        if(world>=350&&world<=380)TelepathyClient.update(new MindStatePayload(3,0,96,48,1,1,1,0));
        // The possessor is typing as you: their half-written line shows where your chat box would be.
        if(world==350)TelepathyClient.typed("meet me by the old water tow");
        if(world==365)pending="telepathy-resistance.png";
        if(world==395){TelepathyClient.update(new MindStatePayload(0,-1,0,0,0,0,0,0));TelepathyClient.update(new MindStatePayload(6,0,0,0,0,0,0,0));}
        if(world==405)pending="telepathy-choices.png";
        if(world==430){
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var id=com.jayptucker.heroesevolved.ability.registry.ModAbilities.TELEPATHY_ID;
                p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_ABILITIES,com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty().assignDormant(id).unlock(id));
                p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.POWER_INTRO_HISTORY,p.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.POWER_INTRO_HISTORY).with(id));
                com.jayptucker.heroesevolved.progression.PlayerProgressionService.setLevel(p,5);
                p.connection.teleport(200.5,-60,200.5,0,0);
                puppet=net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                puppet.moveTo(200.5,-60,204.5,0,0);puppet.setYHeadRot(0);puppet.setNoAi(true);p.serverLevel().addFreshEntity(puppet);puppetStart=puppet.position();
                target=net.minecraft.world.entity.EntityType.VILLAGER.create(p.serverLevel());target.setPos(200.5,-60,208);target.setNoAi(true);p.serverLevel().addFreshEntity(target);
                if(!com.jayptucker.heroesevolved.telepathy.TelepathyFocus.start(p,5))throw new AssertionError("Preview cannot read the mob");
            });
        }
        if(world==468)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.telepathy.TelepathyFocus.stop(p);
            if(!com.jayptucker.heroesevolved.telepathy.PossessionService.start(p,puppet,5,false))throw new AssertionError("Preview cannot possess mob");
        });
        if(world==505){mc.player.setYRot(0);mc.player.setXRot(0);mc.options.keyUp.setDown(true);}
        if(world==517)mc.options.keyUp.setDown(false);
        if(world==518)mc.getSingleplayerServer().execute(()->target.setPos(puppet.position().add(0,0,2)));
        if(world==520){mc.options.keyAttack.setDown(true);net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());}
        if(world==521){mc.options.keyAttack.setDown(false);pending="telepathy-mob-control.png";}
        if(world==530)mc.getSingleplayerServer().execute(()->{
            double moved=puppet.position().distanceTo(puppetStart);
            if(moved<.3||target.getHealth()>=target.getMaxHealth())throw new AssertionError("Live client mob controls failed: moved="+moved+", target health="+target.getHealth());
            System.out.println("TELEPATHY_MOB_CLIENT_CHECK PASS moved="+moved+" target health="+target.getHealth());
        });
        if(world==550)mc.stop();
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post e){if(pending==null)return;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);}catch(Exception ex){throw new RuntimeException(ex);}pending=null;}
}
