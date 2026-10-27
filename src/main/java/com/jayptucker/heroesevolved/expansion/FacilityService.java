package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** An isolated, bounded mission site: building never overwrites a player's settlement. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class FacilityService {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "unwritten_annex"));
    public static final int WIDTH = 29, DEPTH = 33, HEIGHT = 9, BUILD_VOLUME = WIDTH * DEPTH * HEIGHT;
    public static final BlockPos CORE = new BlockPos(14, 65, 15), GATE = new BlockPos(14, 65, 9), EXIT = new BlockPos(14, 65, 2);
    private FacilityService() {}
    public static boolean atSite(ServerPlayer p) { return p.level().dimension().equals(DIMENSION); }
    public static void discovery(ServerPlayer p, String id) {
        FacilityData d = FacilityData.get(p.getServer());
        if (d.discover(p.getUUID(), id)) {
            if (id.equals("brittle_fan") || id.equals("rime_circuit") || id.equals("seam_chorus") || id.equals("annex_saved")) SkillTreeService.awardTraining(p);
            p.sendSystemMessage(Component.literal("Discovery: " + id.replace('_', ' ') + ". Your journal remembers."));
        }
    }
    public static boolean plan(ServerPlayer p) {
        var d = FacilityData.get(p.getServer());
        discovery(p, "unfinished_hour");
        if (d.phase == FacilityData.Phase.PLANNED) { d.phase = FacilityData.Phase.BUILDING; d.setDirty(); }
        return p.getServer().getLevel(DIMENSION) != null;
    }
    public static boolean enter(ServerPlayer p) {
        if (!PowerRules.canAct(p) || p.isPassenger() || p.isSpectator() || atSite(p)) return false;
        var d = FacilityData.get(p.getServer()); var level = p.getServer().getLevel(DIMENSION);
        if (level == null || d.visitors.size() >= 8 || !d.discovered(p.getUUID(), "unfinished_hour")) return false;
        if (d.phase == FacilityData.Phase.SAVED || d.phase == FacilityData.Phase.SCARRED) {
            if (!level.players().isEmpty()) { tell(p, "The annex is awaiting its visitors' return. Leave before starting another operation."); return false; }
            for (UUID id : new UUID[]{d.first, d.second}) if (id != null) { var old = level.getEntity(id); if (old != null) old.discard(); }
            d.reset(); PropheticPainting.refresh(p.getServer());
        }
        if (d.phase == FacilityData.Phase.PLANNED || d.phase == FacilityData.Phase.BUILDING) { plan(p); tell(p, "The painting is resolving. The annex is being prepared; try again shortly."); return false; }
        if (d.visitors.containsKey(p.getUUID())) return false;
        d.visitors.put(p.getUUID(), new FacilityData.ReturnPoint(p.level().dimension().location(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
        d.setDirty(); // Save a recovery ticket before crossing dimensions.
        p.teleportTo(level, 14.5, 65, 3.5, 0, 0);
        if (d.phase == FacilityData.Phase.READY) { d.phase = FacilityData.Phase.ACTIVE; d.setDirty(); }
        tell(p, "HOURGLASS ANNEX: the pictured disaster has not happened yet. Open the partition, cool the relay, and escort Mara and Ivo to the yellow exit. You have two minutes while anyone is inside.");
        tell(p, "Manual route: west switch, east switch; then turn the orange relay valve. Powers offer other routes. Click a captive to call them. The exit button or your Journal returns you home.");
        return true;
    }
    public static boolean leave(ServerPlayer p) {
        var d = FacilityData.get(p.getServer()); var point = d.visitors.get(p.getUUID());
        if (point == null && !atSite(p)) return false;
        ServerLevel level = point == null ? p.getServer().overworld() : p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, point.dimension()));
        if (level == null || level.dimension().equals(DIMENSION)) level = p.getServer().overworld();
        Vec3 dest = point == null ? Vec3.atBottomCenterOf(level.getSharedSpawnPos()) : new Vec3(point.x(), point.y(), point.z());
        if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(dest))) dest = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        level.getChunk(BlockPos.containing(dest));
        var bounds = p.getBoundingBox().move(dest.subtract(p.position()));
        if (!level.getWorldBorder().isWithinBounds(bounds) || !level.noCollision(p, bounds)) dest = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        p.teleportTo(level, dest.x, dest.y, dest.z, point == null ? 0 : point.yaw(), point == null ? 0 : point.pitch());
        d.visitors.remove(p.getUUID()); d.setDirty(); return true;
    }
    public static void technique(ServerPlayer p, BlockPos pos, String technique) {
        if (!atSite(p) || !PowerRules.canAct(p)) return;
        var d = FacilityData.get(p.getServer()); if (d.phase != FacilityData.Phase.ACTIVE) return;
        if (technique.equals("fracture") && pos.distSqr(GATE) <= 36 || technique.equals("current") && pos.distSqr(CORE) <= 36) openGate(p.serverLevel(), d);
        if (technique.equals("cold") && pos.distSqr(CORE) <= 64) stabilize(p, d);
    }
    public static void onPowerUse(ServerPlayer p, String action) {
        if (!atSite(p)) return;
        String technique = action.startsWith("cryo_") ? "cold" : action.startsWith("electro_") ? "current" : action.startsWith("quake_") ? "fracture" : "";
        if (!technique.isEmpty()) technique(p, ExpansionSkills.aim(p), technique);
    }
    private static void openGate(ServerLevel level, FacilityData d) {
        if (d.gate) return; d.gate = true; d.setDirty();
        for (int x : new int[]{4, 5, 6, 13, 14, 15, 22, 23, 24}) for (int y = 65; y <= 67; y++) level.setBlock(new BlockPos(x, y, x < 10 || x > 20 ? 18 : 9), Blocks.AIR.defaultBlockState(), 3);
        broadcast(level, "The partition releases. Two heartbeats remain behind it.");
    }
    private static void stabilize(ServerPlayer p, FacilityData d) {
        if (d.stable) return; d.stable = true; d.setDirty();
        p.serverLevel().setBlock(CORE, Blocks.SEA_LANTERN.defaultBlockState(), 3);
        for (int y = 1; y <= 3; y++) p.serverLevel().setBlock(CORE.above(y), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        for (int y : new int[]{1, 3}) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (p.level().getBlockState(CORE.offset(x, y, z)).is(Blocks.ORANGE_STAINED_GLASS))
                p.serverLevel().setBlock(CORE.offset(x, y, z), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 3);
        broadcast(p.serverLevel(), "The relay cools. The orange light in the painting begins to fade.");
        PropheticPainting.refresh(p.getServer());
    }
    public static void interact(ServerPlayer p, BlockPos pos) {
        if (!atSite(p) || !PowerRules.canAct(p) || p.distanceToSqr(Vec3.atCenterOf(pos)) > 36) return;
        var d = FacilityData.get(p.getServer());
        if (pos.distSqr(EXIT) <= 2) { leave(p); return; }
        if (d.phase != FacilityData.Phase.ACTIVE) return;
        if (pos.equals(new BlockPos(10, 65, 7))) { d.manual = 1; d.setDirty(); tell(p, "West isolator armed. Use the east switch next."); }
        else if (pos.equals(new BlockPos(18, 65, 7)) && d.manual == 1) { d.manual = 2; openGate(p.serverLevel(), d); }
        else if (pos.equals(CORE.west(3)) && d.gate) stabilize(p, d);
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer p && atSite(p)) interact(p, e.getPos());
    }
    @SubscribeEvent public static void readCanvas(PlayerInteractEvent.RightClickItem e) {
        if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer p && PropheticPainting.isCanvas(e.getItemStack()) && PowerRules.canAct(p)) {
            plan(p); tell(p, journal(p));
        }
    }
    @SubscribeEvent public static void cartographer(PlayerInteractEvent.EntityInteract e) {
        if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer p && !atSite(p) && PowerRules.canAct(p)
                && e.getTarget() instanceof Villager v && !v.isBaby() && v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.CARTOGRAPHER) {
            plan(p);
            if (FacilityData.get(p.getServer()).discover(p.getUUID(), "canvas_received")) PropheticPainting.give(p);
            tell(p, "The cartographer: I painted that copper heart before I knew it existed. Read the omen in your Journal. Bring them home.");
        }
    }
    @SubscribeEvent public static void captive(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !atSite(p)) return;
        var d = FacilityData.get(p.getServer());
        if (e.getTarget() instanceof Villager v && (v.getUUID().equals(d.first) || v.getUUID().equals(d.second))) {
            boolean openCell = p.serverLevel().getBlockState(new BlockPos(v.getUUID().equals(d.first) ? 5 : 23, 65, 18)).getCollisionShape(p.level(), new BlockPos(5, 65, 18)).isEmpty();
            if (!d.gate && !openCell) tell(p, "The cell is still locked. Find its isolators, break a route, or carry me clear.");
            else { v.setNoAi(false); v.getNavigation().moveTo(p, .8); tell(p, v.getName().getString() + ": I'll follow. Stay close and take me to the yellow exit."); }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !atSite(p) && FacilityData.get(p.getServer()).visitors.remove(p.getUUID()) != null) FacilityData.get(p.getServer()).setDirty();
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void casualty(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !level.dimension().equals(DIMENSION)) return;
        var d = FacilityData.get(level.getServer());
        if (e.getEntity().getUUID().equals(d.first)) d.casualties |= 1;
        if (e.getEntity().getUUID().equals(d.second)) d.casualties |= 2;
        d.setDirty();
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { FacilityData.get(p.getServer()).visitors.remove(p.getUUID()); FacilityData.get(p.getServer()).setDirty(); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) { tick(e.getServer()); }
    public static void tick(MinecraftServer server) {
        var d = FacilityData.get(server); ServerLevel level = server.getLevel(DIMENSION); if (level == null) return;
        if (d.phase == FacilityData.Phase.BUILDING) {
            int end = Math.min(BUILD_VOLUME, d.cursor + 256);
            for (; d.cursor < end; d.cursor++) {
                int x = d.cursor % WIDTH, z = d.cursor / WIDTH % DEPTH, y = 64 + d.cursor / (WIDTH * DEPTH);
                level.setBlock(new BlockPos(x, y, z), blockAt(x, y, z).defaultBlockState(), 2);
            }
            d.setDirty();
            if (d.cursor == BUILD_VOLUME) { d.phase = FacilityData.Phase.READY; devices(level); PropheticPainting.refresh(server); }
            return;
        }
        List<ServerPlayer> present = level.players().stream().filter(p -> d.visitors.containsKey(p.getUUID()) && p.isAlive()).toList();
        if (present.isEmpty() || d.phase != FacilityData.Phase.ACTIVE) return;
        if (!d.spawned) {
            if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(0, 1).toLong())
                    || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(1, 1).toLong())) return;
            d.first = spawn(level, 5.5, 21.5, "Mara, the Cartographer"); d.second = spawn(level, 23.5, 21.5, "Ivo, the Relay Keeper"); d.spawned = true; d.setDirty();
        }
        d.elapsed++; d.setDirty(); // Incident time pauses while everyone is away, including across restarts.
        if (d.elapsed % 20 != 0) return;
        for (int i = 0; i < 2; i++) {
            int bit = 1 << i; if (((d.rescued | d.casualties) & bit) != 0) continue;
            Entity entity = level.getEntity(i == 0 ? d.first : d.second);
            if (entity == null) continue; // An unloaded or newly loading entity is not a confirmed casualty.
            if (!(entity instanceof Villager v) || !v.isAlive()) { d.casualties |= bit; continue; }
            if (v.position().distanceToSqr(Vec3.atCenterOf(EXIT)) < 9) { d.rescued |= bit; v.discard(); broadcast(level, v.getName().getString() + " reaches the exit."); continue; }
            if (d.gate || level.getBlockState(new BlockPos(i == 0 ? 5 : 23, 65, 18)).isAir()) {
                v.setNoAi(false); var guide = present.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(v))).orElseThrow();
                if (guide.distanceToSqr(v) <= 100) v.getNavigation().moveTo(guide, .8);
            }
        }
        if (!d.stable) {
            AbilityEffects.send(level, Kind.NUCLEAR_CHARGE, Vec3.atCenterOf(CORE), Vec3.atCenterOf(CORE.above(3)), 3);
            for (var p : present) if (p.distanceToSqr(Vec3.atCenterOf(CORE)) < 16) p.hurt(level.damageSources().hotFloor(), 2);
        }
        if (d.stable && d.rescued == 3) finish(server, d, true);
        else if (d.elapsed >= 2400 || (d.rescued | d.casualties) == 3 && d.casualties != 0) finish(server, d, false);
    }
    public static void finish(MinecraftServer server, FacilityData d, boolean saved) {
        if (d.phase != FacilityData.Phase.ACTIVE) return;
        d.phase = saved ? FacilityData.Phase.SAVED : FacilityData.Phase.SCARRED; d.setDirty(); PropheticPainting.refresh(server);
        for (UUID id : List.copyOf(d.visitors.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id); if (p == null) continue;
            if (saved) discovery(p, "annex_saved");
            tell(p, saved ? "A FUTURE REVISED: two people live. The painting remembers their escape. The city may still be saved."
                    : "A SCAR IN THE FUTURE: the operation ended before everyone was safe. Return, study the painting, and attempt another route.");
        }
    }
    private static UUID spawn(ServerLevel level, double x, double z, String name) {
        Villager v = EntityType.VILLAGER.create(level); v.setPos(x, 65, z); v.setCustomName(Component.literal(name)); v.setCustomNameVisible(true); v.setPersistenceRequired(); v.setNoAi(true); level.addFreshEntity(v); return v.getUUID();
    }
    private static Block blockAt(int x, int y, int z) {
        if (y == 64 && x >= 10 && x <= 18 && z >= 12 && z <= 18) return (x + z) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE;
        if (y == 64) return x == 14 ? Blocks.YELLOW_CONCRETE : (x + z) % 4 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.DEEPSLATE_TILES;
        if (y == 72) return x % 7 == 0 && z % 7 == 0 ? Blocks.SEA_LANTERN : x == 14 ? Blocks.TINTED_GLASS : Blocks.DEEPSLATE_TILES;
        if (x == 0 || x == 28 || z == 0 || z == 32) return y == 66 || y == 67 ? Blocks.TINTED_GLASS : Blocks.DEEPSLATE_BRICKS;
        if (z == 9 && y < 69) return x >= 13 && x <= 15 ? Blocks.IRON_BARS : Blocks.POLISHED_DEEPSLATE;
        if (z == 18 && (x >= 2 && x <= 8 || x >= 20 && x <= 26) && y < 69) return Blocks.IRON_BARS;
        boolean cellX = x >= 2 && x <= 8 || x >= 20 && x <= 26;
        boolean cellSide = x == 2 || x == 8 || x == 20 || x == 26;
        if (cellX && z >= 18 && z <= 25 && y == 69) return Blocks.CUT_COPPER;
        if (cellX && z >= 18 && z <= 25 && y < 69 && (cellSide || z == 25))
            return y == 65 || y == 68 || cellSide && (z == 18 || z == 25) ? Blocks.CUT_COPPER : Blocks.LIGHT_BLUE_STAINED_GLASS;
        if ((x == 2 || x == 26) && (z == 12 || z == 24) && y <= 68) return y == 68 ? Blocks.SEA_LANTERN : Blocks.CUT_COPPER;
        if (y == 65 && z == 28 && (x >= 3 && x <= 7 || x >= 21 && x <= 25)) return x % 2 == 0 ? Blocks.LECTERN : Blocks.BOOKSHELF;
        if (y == 65 && z == 15 && x >= 11 && x <= 17) return Blocks.COPPER_BLOCK;
        return Blocks.AIR;
    }
    private static void devices(ServerLevel level) {
        for (BlockPos p : List.of(new BlockPos(10, 65, 7), new BlockPos(18, 65, 7), CORE.west(3), EXIT)) level.setBlock(p, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 3);
        for (int y = 0; y <= 4; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            boolean rim = y == 0 || y == 4;
            if (rim || y != 2 || x == 0 && z == 0)
                level.setBlock(CORE.offset(x, y, z), (rim ? Blocks.CUT_COPPER : x == 0 && z == 0 ? Blocks.SHROOMLIGHT : Blocks.ORANGE_STAINED_GLASS).defaultBlockState(), 3);
        }
        level.setBlock(CORE.above(5), Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
        sign(level, new BlockPos(11, 65, 4), "HOURGLASS ANNEX", "West, then East", "Cool orange relay", "Bring them home");
        sign(level, new BlockPos(17, 65, 17), "THE COPPER HEART", "Turn west valve", "or draw out heat", "Keep your distance");
    }
    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var text = new net.minecraft.world.level.block.entity.SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]), Component.literal(lines[i]));
            sign.setText(text, true); sign.setChanged();
        }
    }
    public static String journal(ServerPlayer p) {
        var d = FacilityData.get(p.getServer());
        return "The Unfinished Hour / " + d.phase.name() + ": " + (d.stable ? "relay cooled" : "relay unstable") + ", " + Integer.bitCount(d.rescued) + "/2 rescued. "
                + (atSite(p) ? "Use Leave annex to return. " : "Read the painting, then Follow omen. ") + "Manual route: west isolator, east isolator, orange relay valve; escort both captives to the yellow exit.";
    }
    private static void broadcast(ServerLevel level, String message) { level.players().forEach(p -> tell(p, message)); }
    private static void tell(ServerPlayer p, String message) { p.sendSystemMessage(Component.literal(message)); }
}
