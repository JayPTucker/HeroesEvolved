package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primatech's Level 5, under the bunker's containment block, where the Company kept Gabriel Gray for a hundred and three
 * years: a corridor of ruined cells, his own cell behind glass at the far end, the Company's watch list on the wall by
 * it, the release lever in the guard post opposite, and the lift back up at the near end.
 *
 * <p>It lives in its own pocket dimension ({@link #DIMENSION}), built once from the {@code level_five} blueprint (again
 * after {@link #VERSION} changes), with its chunks kept loaded. The way down is a hatch in the floor of Gray's old cell
 * in containment ({@link #placeHatch}); the way up is the lift's button. Nobody can break or build here except in
 * creative. What happens here (his talk, the list, the lever, the reveal) is {@link SylarStory}'s; this class is the
 * place.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class LevelFive {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "level_five"));
    /** Where the blueprint's anchor (the floor under the lift's doors) is placed. */
    public static final BlockPos ORIGIN = new BlockPos(64, 64, 64);
    public static final String BLUEPRINT = "level_five";
    /** Bump after changing the blueprint, so worlds that built it build it again. */
    public static final int VERSION = 1;
    /** Where the hatch goes in a Sublevel 4 bunker: the floor of Cell 3, Gray's, from the bunker's origin. */
    public static final BlockPos CELL_THREE_HATCH = new BlockPos(-14, 0, 27);
    /** The tag on the Sylar who stands in the cell. */
    public static final String PRISONER = HeroesEvolved.MOD_ID + "_prisoner";

    private static Map<String, List<BlockPos>> marks;

    private LevelFive() {}

    public static ServerLevel level(MinecraftServer server) { return server.getLevel(DIMENSION); }
    public static boolean in(Entity entity) { return entity.level().dimension().equals(DIMENSION); }

    /** Every cell carrying {@code marker}, in world positions (read from the blueprint, so it works before building). */
    public static List<BlockPos> marked(String marker) {
        if (marks == null) {
            Blueprint blueprint = Blueprint.load(BLUEPRINT);
            Map<String, List<BlockPos>> found = new HashMap<>();
            for (String m : List.of("spawn:arrival", "exit", "glass", "pod", "spawn:prisoner", "sign:list0", "sign:head",
                    "sign:list1", "sign:list2", "sign:list3", "sign:list4", "lever", "sign:release", "sign:lift"))
                found.put(m, blueprint.markerOffsets(m).stream().map(ORIGIN::offset).toList());
            marks = found;
        }
        return marks.getOrDefault(marker, List.of());
    }

    public static BlockPos at(String marker) { return marked(marker).getFirst(); }
    /** Where you step out of the lift, facing his cell. */
    public static Vec3 arrival() { return Vec3.atBottomCenterOf(at("spawn:arrival").above()); }
    /** Where he stands in his cell. */
    public static Vec3 cell() { return Vec3.atBottomCenterOf(at("spawn:prisoner").above()); }
    /** The middle of his cell's glass front. */
    public static Vec3 glassMiddle() {
        List<BlockPos> glass = marked("glass");
        double x = 0, y = 0, z = 0;
        for (BlockPos p : glass) { x += p.getX() + .5; y += p.getY() + .5; z += p.getZ() + .5; }
        return new Vec3(x / glass.size(), y / glass.size(), z / glass.size());
    }
    /** The middle of the watch list on the wall. */
    public static Vec3 listMiddle() { return Vec3.atCenterOf(at("sign:list1")).add(0, 0, -0.5); }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /** Builds Level 5 if this world hasn't (or built an older one). Returns the level, or null with no dimension. */
    public static ServerLevel ensure(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) return null;
        SylarData data = SylarData.get(server);
        if (data.levelFiveBuilt() < VERSION) build(level);
        return level;
    }

    /** Builds (or rebuilds) it, and sets it to the story's state: the glass, the list, the lever. */
    public static void build(ServerLevel level) {
        Blueprint blueprint = Blueprint.load(BLUEPRINT);
        BlockPos min = ORIGIN.offset(-blueprint.anchorX, -blueprint.anchorY, -blueprint.anchorZ);
        BlockPos max = min.offset(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1);
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
        AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).inflate(2);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
        blueprint.place(level, ORIGIN, Rotation.NONE);
        SylarData.get(level.getServer()).levelFiveBuilt(VERSION);
        refresh(level.getServer());
    }

    /** Puts the place in step with the story: his glass (whole until he is out), the list, the lever, who is in the cell. */
    public static void refresh(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null || SylarData.get(server).levelFiveBuilt() < VERSION) return;
        int stage = SylarData.get(server).stage();
        glass(level, stage <= SylarData.AWAKE, false);
        lever(level, stage >= SylarData.OUT);
        signs(server);
        if (stage != SylarData.AWAKE) {
            for (SylarEntity s : prisoners(level)) s.discard();
            if (cellmate != null && cellmate.getTags().contains(PRISONER)) cellmate.discard();
            cellmate = null;
        }
    }

    /** His cell's glass: whole, or gone ({@code shatter} breaks it loudly, as the lever does). */
    static void glass(ServerLevel level, boolean whole, boolean shatter) {
        for (BlockPos pos : marked("glass")) {
            BlockState now = level.getBlockState(pos);
            if (whole && !now.is(Blocks.GLASS)) level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            if (!whole && now.is(Blocks.GLASS)) {
                if (shatter) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, now), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                            12, .3, .3, .3, .1);
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (shatter) {
            Vec3 c = glassMiddle();
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.9F);
        }
    }

    private static void lever(ServerLevel level, boolean down) {
        BlockPos pos = at("lever");
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(LeverBlock.POWERED) && state.getValue(LeverBlock.POWERED) != down)
            level.setBlock(pos, state.setValue(LeverBlock.POWERED, down), 3);
    }

    /** The watch list, in the order {@link SylarStory#listLines} gives it: down the left column, then down the right. */
    static void signs(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) return;
        List<Component> lines = SylarStory.listLines(server);
        for (int s = 0; s < 4; s++) {
            if (!(level.getBlockEntity(at("sign:list" + (s + 1))) instanceof SignBlockEntity sign)) continue;
            // Glowing, or the list can't be read in the dark of Level 5.
            SignText text = new SignText().setColor(DyeColor.WHITE).setHasGlowingText(true);
            for (int i = 0; i < 4; i++) {
                int k = s * 4 + i;
                Component line = k < lines.size() ? lines.get(k) : Component.empty();
                text = text.setMessage(i, line, line);
            }
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }

    /** The Sylar in his cell (there is one only while he is awake and in it). */
    public static List<SylarEntity> prisoners(ServerLevel level) {
        return level.getEntitiesOfClass(SylarEntity.class, new AABB(ORIGIN).inflate(48), s -> s.getTags().contains(PRISONER));
    }

    /** The one in the cell, held directly: a freshly loaded chunk hides its entities from searches for a few seconds. */
    private static SylarEntity cellmate;

    /** Puts him in his cell if he isn't there, facing the glass. Returns him, or null. */
    static SylarEntity prisoner(ServerLevel level) {
        if (cellmate != null && !cellmate.isRemoved() && cellmate.level() == level && cellmate.getTags().contains(PRISONER)) return cellmate;
        List<SylarEntity> there = prisoners(level);
        if (!there.isEmpty()) {
            for (int i = 1; i < there.size(); i++) there.get(i).discard();
            return cellmate = there.getFirst();
        }
        SylarEntity gray = ModEntities.SYLAR.get().create(level);
        if (gray == null) return null;
        Vec3 at = cell();
        gray.moveTo(at.x, at.y, at.z, 0, 0);
        gray.setYHeadRot(0);
        gray.addTag(PRISONER);
        gray.actor(true);
        if (!level.addFreshEntity(gray)) return null;
        return cellmate = gray;
    }

    // ------------------------------------------------------------------
    // The way down and the way up
    // ------------------------------------------------------------------

    /**
     * Puts the hatch down, once: in the floor of Gray's old cell in a Sublevel 4 bunker, or (in a world with no such
     * bunker) in the ground beside world spawn. Returns where it is.
     */
    public static BlockPos placeHatch(MinecraftServer server) {
        SylarData data = SylarData.get(server);
        ServerLevel overworld = server.overworld();
        BlockPos pos = data.hatch();
        if (pos == null) {
            var bunker = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(server);
            if (bunker.built && bunker.design >= com.jayptucker.heroesevolved.story.bunker.BunkerLayout.STASIS_DESIGN) {
                pos = bunker.origin.offset(CELL_THREE_HATCH);
            } else {
                BlockPos spawn = overworld.getSharedSpawnPos().offset(3, 0, 3);
                pos = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn).below();
            }
            data.hatch(pos);
        }
        overworld.getChunk(pos);
        if (!isHatch(overworld.getBlockState(pos))) overworld.setBlock(pos, hatchState(), 3);
        return pos;
    }

    static BlockState hatchState() {
        return Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, net.minecraft.core.Direction.NORTH);
    }

    static boolean isHatch(BlockState state) { return state.is(Blocks.IRON_TRAPDOOR); }

    /** Down the hatch: the lift hums, and you step out at the far end of the corridor from his cell. */
    public static void descend(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = ensure(server);
        if (level == null) return;
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.7F);
        Vec3 to = arrival();
        move(player, level, to, 180, 0);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.6F);
        SylarStory.descended(player);
    }

    /** Up the lift, to stand on the hatch in Gray's old cell (or world spawn if the hatch is gone). */
    public static void ascend(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel overworld = server.overworld();
        BlockPos hatch = SylarData.get(server).hatch();
        Vec3 to = hatch == null ? Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()) : Vec3.atBottomCenterOf(hatch.above());
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.8F);
        move(player, overworld, to, 90, 0);
        overworld.playSound(null, to.x, to.y, to.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
        SylarStory.left(player);
    }

    static void move(ServerPlayer player, ServerLevel level, Vec3 to, float yaw, float pitch) {
        level.getChunk(BlockPos.containing(to));
        player.teleportTo(level, to.x, to.y, to.z, yaw, pitch);
        player.absMoveTo(to.x, to.y, to.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    /** Nobody falls out of Level 5: whoever drops below its floor is back at the lift. */
    static void tick(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) return;
        for (ServerPlayer player : List.copyOf(level.players()))
            if (player.getY() < ORIGIN.getY() - 6) move(player, level, arrival(), player.getYRot(), 0);
    }

    // ------------------------------------------------------------------
    // What players do to it
    // ------------------------------------------------------------------

    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        SylarData data = SylarData.get(player.getServer());
        // One click is one pull: the off hand's second try at the same block is swallowed, never acted on.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            if (pos.equals(data.hatch()) && player.level().dimension() == Level.OVERWORLD
                    || in(player) && (pos.equals(at("exit")) || pos.equals(at("lever")))) accept(event);
            return;
        }
        if (player.level().dimension() == Level.OVERWORLD) {
            if (pos.equals(data.hatch()) && data.stage() >= SylarData.AWAKE && isHatch(player.level().getBlockState(pos))) {
                accept(event);
                descend(player);
            }
            return;
        }
        if (!in(player)) return;
        if (pos.equals(at("exit"))) {
            accept(event);
            player.serverLevel().playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 0.8F);
            ascend(player);
        } else if (pos.equals(at("lever"))) {
            accept(event);
            SylarStory.lever(player);
        }
    }

    private static void accept(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Level 5 and the hatch can't be broken, except in creative. */
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer() != null && event.getPlayer().isCreative()) return;
        if (level.dimension().equals(DIMENSION)) { event.setCanceled(true); return; }
        if (level.dimension() == Level.OVERWORLD && event.getPos().equals(SylarData.get(level.getServer()).hatch())) event.setCanceled(true);
    }

    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(DIMENSION)) return;
        if (event.getEntity() instanceof Player player && player.isCreative()) return;
        event.setCanceled(true);
    }

    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension().equals(DIMENSION)) { event.getAffectedBlocks().clear(); return; }
        BlockPos hatch = SylarData.get(level.getServer()).hatch();
        if (hatch != null && level.dimension() == Level.OVERWORLD) event.getAffectedBlocks().remove(hatch);
    }

    /** For tests: forget the cached marker positions (the blueprint is read again). */
    static void forgetMarks() { marks = null; }
}
