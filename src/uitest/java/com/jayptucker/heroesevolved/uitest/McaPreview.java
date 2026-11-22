package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.village.Villagers;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Villagers with Minecraft Comes Alive, as a player sees them: a story scene's villager (vanilla, untouched) beside
 * ordinary villagers the mod spawned (MCA's humans, with a face, clothes and a name, one of them a child and one carrying
 * a gift). Checks on the client what each one really is. Staged 2600 blocks from spawn; shots go to
 * build/mca-preview/screenshots.
 *
 * <pre>./gradlew runClient --init-script tools/mca-preview.init.gradle -Pmca</pre>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class McaPreview {
    private static final int X = 2600, Y = -60, Z = 0;
    private static boolean started;
    private static int ticks, worldTicks, failed;
    private static String pending;
    private static Villager actor, adult, child, gifted;
    private static final List<Villager> PEOPLE = new ArrayList<>();

    private static void check(String what, boolean ok) {
        if (!ok) failed++;
        PreviewQuiet.report("MCA_CHECK " + (ok ? "PASS" : "FAIL") + ": " + what);
    }

    private static boolean human(Entity e) { return e != null && "mca".equals(EntityType.getKey(e.getType()).getNamespace()); }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(mc.player.getUUID()));
            } catch (Throwable t) {
                PreviewQuiet.report("MCA_CHECK FAIL: server step threw " + t);
            }
        });
    }

    private static void shot(String name) { pending = "mca-" + name + ".png"; }

    /** Stands the player {@code back} blocks north of the line, looking at {@code x}. */
    private static void look(Minecraft mc, double x, double back, double aim) {
        double dy = (Y + aim) - (Y + 1.62);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, back));
        onServer(mc, p -> {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(), x, Y, Z + 5.5 - back, 0, pitch);
        });
    }

    /** An ordinary villager, the way the mod's own systems bring one in. No AI after they are in, to stand for the photo. */
    private static Villager person(ServerLevel level, double x, VillagerProfession job, boolean baby, boolean gift) {
        Villager v = EntityType.VILLAGER.create(level);
        v.moveTo(x, Y, Z + 5.5, 180, 0);
        v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        if (baby) v.setBaby(true);
        if (gift) GiftedVillagers.give(v, ModAbilities.CRYOKINESIS_ID);
        Villager in = Villagers.enter(level, v);
        in.setNoAi(true);
        in.setPersistenceRequired();
        in.setCustomNameVisible(true);
        in.setYRot(180);
        in.setYHeadRot(180);
        in.setYBodyRot(180);
        PEOPLE.add(in);
        return in;
    }

    private static void stage(ServerLevel level) {
        for (int dx = -10; dx <= 12; dx++)
            for (int dz = -8; dz <= 9; dz++)
                level.setBlockAndUpdate(new BlockPos(X + dx, Y - 1, Z + dz),
                        ((dx + dz) % 5 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS).defaultBlockState());
        for (int dx = -10; dx <= 12; dx++) for (int dy = 0; dy < 5; dy++)
            level.setBlockAndUpdate(new BlockPos(X + dx, Y + dy, Z + 9), Blocks.SMOOTH_STONE.defaultBlockState());
        actor = Villagers.actor(level);
        actor.moveTo(X - 3.5, Y, Z + 5.5, 180, 0);
        actor.setVillagerData(actor.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(2));
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        actor.setCustomName(Component.literal("Story cast"));
        actor.setCustomNameVisible(true);
        actor.setYHeadRot(180);
        actor.setYBodyRot(180);
        check("a scene's villager is added", level.addFreshEntity(actor));
        adult = person(level, X - 0.5, VillagerProfession.FARMER, false, false);
        child = person(level, X + 2.5, VillagerProfession.NONE, true, false);
        gifted = person(level, X + 5.5, VillagerProfession.CLERIC, false, true);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.mca")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            System.out.println("MCA_PREVIEW mca=" + ModList.get().isLoaded("mca"));
            mc.options.pauseOnLostFocus = false;
            mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Mca-" + System.currentTimeMillis(),
                    new LevelSettings("MCA preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        // MCA greets a new player with a screen of its own; anything open would cover the shots.
        if (mc.screen != null) mc.setScreen(null);
        int t = ++worldTicks;
        if (t >= 100) mc.getToasts().clear();
        switch (t) {
            case 104 -> onServer(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 110 -> {
                com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
                onServer(mc, com.jayptucker.heroesevolved.story.bunker.CryoChamberService::release);
            }
            case 150 -> onServer(mc, p -> {
                ServerLevel world = p.getServer().overworld();
                for (int cx = (X - 16) >> 4; cx <= (X + 16) >> 4; cx++)
                    for (int cz = (Z - 16) >> 4; cz <= (Z + 16) >> 4; cz++) world.setChunkForced(cx, cz, true);
                world.setDayTime(6000);
                p.getAbilities().invulnerable = true;
                p.onUpdateAbilities();
                p.teleportTo(world, X + 1, Y, Z - 2, 0, 5);
                p.removeAllEffects();
                stage(world);
                System.out.println("MCA_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + p.level().getBlockState(p.blockPosition().below()));
            });
            case 220 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                look(mc, X + 1, 8, 1.1);
            }
            case 290 -> {
                shot("1-line-up");
                check("the story villager is vanilla on the client", mc.level.getEntity(actor.getId()) instanceof Villager v && !human(v));
                check("the adult is MCA's human on the client", human(mc.level.getEntity(adult.getId())));
                check("the child is MCA's human on the client", human(mc.level.getEntity(child.getId())));
                check("the gifted villager is MCA's human on the client", human(mc.level.getEntity(gifted.getId())));
                check("the child is a child", child.isBaby());
                check("the gift is kept", ModAbilities.CRYOKINESIS_ID.equals(GiftedVillagers.gift(gifted)));
                check("MCA gave them names", adult.hasCustomName() && gifted.hasCustomName());
                System.out.println("MCA_PREVIEW names: " + adult.getName().getString() + ", " + child.getName().getString() + ", " + gifted.getName().getString());
            }
            case 296 -> look(mc, X - 2, 3.4, 1.2);
            case 350 -> shot("2-vanilla-beside-human");
            case 356 -> look(mc, X + 4, 3.4, 1.0);
            case 410 -> shot("3-child-and-gifted");
            case 440 -> {
                onServer(mc, p -> { actor.discard(); PEOPLE.forEach(Entity::discard); });
                System.out.println("MCA_PREVIEW done, failed=" + failed);
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
        } catch (Exception ex) {
            PreviewQuiet.report("MCA_CHECK FAIL: screenshot " + pending + " " + ex);
        }
        pending = null;
    }
}
