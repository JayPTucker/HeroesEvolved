package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cryo.CryoBlastService;
import com.jayptucker.heroesevolved.cryo.CryoKitService;
import com.jayptucker.heroesevolved.cryo.CryoRideService;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.CryoTechniqueService;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Checks real survival controls and photographs solid ice from useful oblique views. */
@EventBusSubscriber(modid="heroesevolved", value=Dist.CLIENT)
public final class CryoPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Mob victim;
    private static ServerPlayer subject;
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
    private static void power(ServerPlayer player, ResourceLocation power) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(player, 5);
        PlayerEnergyService.restore(player, 999);
    }
    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void stageSubject(ServerPlayer owner, ResourceLocation power, String name) {
        subject = new FakePlayer(owner.serverLevel(), new GameProfile(UUID.randomUUID(), name));
        try {
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            var connection = subject.connection.getConnection();
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        subject.moveTo(owner.getX(), owner.getY(), owner.getZ() + 6, -90, 0);
        power(subject, power);
        PlayerList list = owner.server.getPlayerList();
        CryoPreview.<List<ServerPlayer>>field(list, "players").add(subject);
        CryoPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(subject.getUUID(), subject);
        owner.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(subject)));
        owner.serverLevel().addNewPlayer(subject);
        view(owner, subject.getBoundingBox().getCenter());
    }
    private static void removeSubject() {
        RadiationService.stopAll(subject);
        PyroService.forget(subject);
        PlayerList list = subject.server.getPlayerList();
        CryoPreview.<List<ServerPlayer>>field(list, "players").remove(subject);
        CryoPreview.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(subject.getUUID());
        subject.serverLevel().removePlayerImmediately(subject, Entity.RemovalReason.DISCARDED);
        subject = null;
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
                power(p, ModAbilities.CRYOKINESIS_ID);
                PlayerPowerSyncService.sync(p);
                p.serverLevel().setDayTime(10000);p.serverLevel().setWeatherParameters(6000,0,false,false);
                scene(p,200.5,0,3);victim=target(p,200.5,6.5);view(p,victim.getBoundingBox().getCenter());
            });
            case 195 -> {mc.gui.getChat().clearMessages(true);mc.getToasts().clear();mc.options.hideGui=true;}
            case 200 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);ModKeyMappings.ABILITY_1.setDown(true);}
            case 201 -> pending="cryo-1a-ice-blast-windup.png";
            case 205 -> pending="cryo-1b-ice-blast-behind.png";
            case 209 -> pending="cryo-1b-ice-blast-profile.png";
            case 211 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 213 -> pending="cryo-1c-ice-blast-front.png";
            case 216 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;}
            case 221 -> pending="cryo-1d-ice-blast-first-person.png";
            case 225 -> {ModKeyMappings.ABILITY_1.setDown(false);mc.options.hideGui=true;}
            case 229 -> pending="cryo-1e-ice-blast-aftermath.png";
            case 232 -> server(mc,p->{
                if(CryoBlastService.isStreaming(p)||CryoService.frostAmount(victim)<=0&&!CryoService.isFrozen(victim))throw new AssertionError("Ice Blast must build Frost and end on release");
                System.out.println("CRYO_CLIENT_BLAST PASS frost="+CryoService.frostAmount(victim));
            });
            case 245 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;server(mc,p->scene(p,400.5,0,30));}
            case 255 -> ModKeyMappings.ABILITY_2.setDown(true);
            case 256 -> ModKeyMappings.ABILITY_2.setDown(false);
            case 259 -> pending="cryo-2a-wall-frost-first-person.png";
            case 260 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.options.hideGui=true;}
            case 263 -> pending="cryo-2b-wall-frost-profile.png";
            case 269 -> server(mc,p->{
                if(CryoKitService.constructBlocks(p).isEmpty())throw new AssertionError("Sculpt must grow real terrain");
                var blocks=CryoKitService.constructBlocks(p);
                Vec3 center=Vec3.atCenterOf(blocks.get(blocks.size()/2));
                p.connection.teleport(405.5,-60,-2.5,40,0);view(p,center);
                System.out.println("CRYO_CLIENT_SCULPT PASS blocks="+blocks.size());
            });
            case 275 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);pending="cryo-2-solid-sculpt-wall.png";}
            case 290 -> server(mc,p->scene(p,600.5,0,35));
            case 300 -> {mc.options.keyShift.setDown(true);ModKeyMappings.ABILITY_2.setDown(true);}
            case 301 -> {ModKeyMappings.ABILITY_2.setDown(false);mc.options.keyShift.setDown(false);}
            case 306 -> pending="cryo-3a-ramp-frost.png";
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
            case 410 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;server(mc,p->{scene(p,1000.5,0,0);victim=target(p,1000.5,5.5);CryoService.chill(victim,60,p);view(p,victim.getBoundingBox().getCenter());});}
            case 420 -> ModKeyMappings.ABILITY_4.setDown(true);
            case 421 -> ModKeyMappings.ABILITY_4.setDown(false);
            case 425 -> pending="cryo-7a-prison-frost.png";
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
            case 481 -> pending="cryo-9a-whiteout-frost.png";
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
            case 575 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=true;server(mc,p->{
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
            case 625 -> server(mc,p->{
                power(p, ModAbilities.CRYOKINESIS_ID);
                PlayerPowerSyncService.sync(p);
                scene(p,1600.5,0,0);
            });
            case 640 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui=false;
                server(mc,p->{
                    stageSubject(p, ModAbilities.RADIATION_ID, "Reactor");
                    RadiationService.addMass(subject, 100);
                    RadiationService.beginDetonation(subject, 5, false);
                });
            }
            case 655 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 669 -> pending="cryo-14-reactor-cooling.png";
            case 686 -> pending="cryo-15-reactor-stabilized.png";
            case 688 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 695 -> server(mc,p->{
                boolean stable = !RadiationService.critical(subject) && RadiationService.mass(subject) <= 30;
                removeSubject();
                if (!stable) throw new AssertionError("Held Ice Blast must defuse the reactor");
                System.out.println("CRYO_CLIENT_REACTOR PASS");
            });
            case 710 -> server(mc,p->{
                scene(p,1800.5,0,0);
                p.serverLevel().setDayTime(13000);
                stageSubject(p, ModAbilities.PYROKINESIS_ID, "Pyro");
                PyroService.addHeat(subject, 75);
                if (!PyroService.startPalm(subject, 5)) throw new AssertionError("The Pyro stand-in must breathe fire");
            });
            case 719 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 720 -> pending="cryo-16-pyro-before.png";
            case 724 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 733 -> pending="cryo-17-pyro-quenched.png";
            case 735 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 740 -> ModKeyMappings.ABILITY_1.setDown(false);
            case 748 -> server(mc,p->{
                boolean extinguished = PyroService.heat(subject) == 0 && !PyroService.stopPalm(subject);
                removeSubject();
                if (!extinguished) throw new AssertionError("Ice Blast must quench the fire channel and drain Heat");
                System.out.println("CRYO_CLIENT_PYRO PASS");
            });
            case 762 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.options.hideGui=true;
                server(mc,p->{
                    p.serverLevel().setDayTime(10000);
                    scene(p,2000.5,0,34);
                });
            }
            case 777 -> server(mc,p->{
                if (!CryoKitService.sculpt(p,5,true)) throw new AssertionError("Walkable stair preview could not place its first step");
            });
            case 802 -> mc.options.keyUp.setDown(true);
            case 830 -> pending="cryo-18-walking-ice-steps.png";
            case 834 -> {
                mc.options.keyUp.setDown(false);
                server(mc,p->{
                    if (p.getY() < -58.5) throw new AssertionError("The player must walk up the real ice steps without jumping");
                    System.out.println("CRYO_CLIENT_RAMP_WALK PASS height="+p.getY());
                });
            }
            case 850 -> server(mc,p->{
                scene(p,2200.5,0,5);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.CRYOKINESIS_ID.toString(),
                        new SkillTreeData.Build(15, Set.of("cryokinesis_double_weave"), Map.of())));
                for (int x=-3;x<=3;x++) for (int z=2;z<=10;z++) for (int y=-62;y<=-61;y++)
                    p.serverLevel().setBlock(new BlockPos(2200+x,y,z),Blocks.AIR.defaultBlockState(),3);
            });
            case 865 -> server(mc,p->{
                if (!CryoTechniqueService.activate(new AbilityUseContext(p,ModAbilities.CRYOKINESIS_ID,5,false),"rime_loom"))
                    throw new AssertionError("Rime Loom must start a deck over the gap");
            });
            case 874 -> pending="cryo-19a-loom-frost.png";
            case 896 -> pending="cryo-19-frost-bridge-growing.png";
            case 921 -> mc.options.keyUp.setDown(true);
            case 953 -> pending="cryo-20-crossing-frost-bridge.png";
            case 957 -> {
                mc.options.keyUp.setDown(false);
                server(mc,p->{
                    if (p.getZ() < 5 || p.getY() < -60.15) throw new AssertionError("The woven deck must support a walking player over the gap");
                    System.out.println("CRYO_CLIENT_LOOM_WALK PASS at="+p.position());
                });
            }
            case 969 -> mc.stop();
            default -> { }
        }
    }
    @SubscribeEvent public static void gui(RenderGuiEvent.Pre event) {
        if (Boolean.getBoolean("heroes.preview.cryo")
                && (world >= 216 && world < 225 || world >= 245 && world < 260 || world >= 410 && world < 575 || world >= 640))
            event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(pending==null)return;var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);
            System.out.println("CRYO_CAPTURE "+out);
        }catch(Exception ex){throw new RuntimeException(ex);}pending=null;
    }
}
