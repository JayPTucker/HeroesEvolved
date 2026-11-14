package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The power-themed TaCZ guns: what happens when their rounds land. TaCZ fires them (they are a gun pack in
 * {@code data/heroesevolved/index/guns}); {@link TaczLink} tells us about each shot and hit.
 *
 * <ul>
 *   <li><b>Heat gun</b>: a short stream of fire. Every round sets the target alight and keeps it burning a little
 *   longer, up to five seconds, and thaws anything frozen. A weak Pyrokinesis.</li>
 *   <li><b>Cold gun</b>: frost bolts. Each adds frost (slower and slower); the fifth or so freezes the target
 *   solid for two seconds (one for a player). A weak Cryokinesis. Bolts that hit water skin it with ice.</li>
 *   <li><b>Taser</b>: one cartridge at a time, eight blocks. A hit locks the target's muscles: barely moving, no
 *   jumping, no landing blows (see {@link Stun}). A weak Electrokinesis hit.</li>
 *   <li><b>Nullifier</b>: dampener rounds made from the Forge Energy in its own battery, not from ammo items. A hit
 *   dampens the target's power for a while (see {@code dampener/Dampening} and {@code dampener/Nullifier}).</li>
 * </ul>
 */
public final class PowerWeapons {
    public static final ResourceLocation HEAT_GUN = id("heat_gun"), COLD_GUN = id("cold_gun"), TASER = id("taser");
    public static final ResourceLocation FUEL_CELL = id("fuel_cell"), CRYO_CELL = id("cryo_cell"), TASER_CARTRIDGE = id("taser_cartridge");
    /** The Nullifier and its dampener round. The rounds come out of the gun's RF battery, never the inventory. */
    public static final ResourceLocation DAMPENER_GUN = id("dampener_gun"), DAMPENER_ROUND = id("dampener_round");
    /** The Company's sidearm: TaCZ's own Glock 17. */
    public static final ResourceLocation AGENT_PISTOL = ResourceLocation.fromNamespaceAndPath("tacz", "glock_17");

    /** Burning time each heat round adds, and the most it can build to. */
    public static final int HEAT_TICKS = 20, HEAT_MAX = 100;
    /** Frost each cold round adds, and how long its freeze holds a creature or a player. */
    public static final int FROST_TICKS = 30, FREEZE_MOB = 40, FREEZE_PLAYER = 20;
    public static final double HEAT_RANGE = 10, COLD_RANGE = 40, TASER_RANGE = 8;

    private PowerWeapons() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }

    /** Starts listening to TaCZ. Called once from the mod's constructor. */
    public static void init() { TaczLink.listen(); }

    /** False if TaCZ's API has changed under us; the guns then fire without their effects. */
    public static boolean available() { return TaczLink.available(); }

    /** Whether {@code entity} is a gun's bullet in flight. */
    public static boolean isBullet(Entity entity) { return TaczLink.bullet(entity); }

    /** An explosive bullet's {radius, breaks blocks (1 or 0)}, or null for an ordinary round. */
    public static @Nullable float[] bulletExplosion(Entity entity) { return TaczLink.explosive(entity); }

    /** A bare bullet entity for tests: ordinary when {@code radius} is 0, explosive otherwise. */
    public static @Nullable Entity testBullet(ServerLevel level, float radius) { return TaczLink.testBullet(level, radius); }

    /** A loaded gun: a full magazine and a round chambered. Empty if TaCZ doesn't know the id. */
    public static ItemStack gun(ResourceLocation id) {
        ItemStack gun = TaczLink.gun(id, magazine(id), id.equals(HEAT_GUN));
        if (id.equals(DAMPENER_GUN) && !gun.isEmpty()) com.jayptucker.heroesevolved.dampener.Nullifier.prepare(gun);
        return gun;
    }

    /** A gun's internal reserve (the Nullifier's battery, in rounds), or -1 if it has none. */
    public static int reserve(ItemStack stack) { return TaczLink.reserve(stack); }

    /** Sets a gun's internal reserve and its cap. TaCZ reloads from it instead of the inventory. */
    public static void reserve(ItemStack stack, int rounds, int max) { TaczLink.reserve(stack, rounds, max); }

    public static ItemStack ammo(ResourceLocation id, int count) { return TaczLink.ammo(id, count); }

    /** The TaCZ gun id of a stack, or null if it isn't a TaCZ gun. */
    public static @Nullable ResourceLocation gunId(ItemStack stack) { return TaczLink.gunId(stack); }

    public static boolean is(ItemStack stack, ResourceLocation gun) { return gun.equals(gunId(stack)); }

    /** Rounds left in a gun, the chambered one included. */
    public static int rounds(ItemStack stack) { return TaczLink.ammoCount(stack); }

    /** A magazine's worth for our guns and the agents' pistol. */
    public static int magazine(ResourceLocation id) {
        if (id.equals(HEAT_GUN)) return 40;
        if (id.equals(COLD_GUN)) return 15;
        if (id.equals(TASER)) return 1;
        if (id.equals(DAMPENER_GUN)) return com.jayptucker.heroesevolved.dampener.DampenerGeometry.MAGAZINE;
        return 17;
    }

    // ------------------------------------------------------------------
    // Firing (for creatures; players fire through TaCZ's own controls)
    // ------------------------------------------------------------------

    /** Raises the gun in a creature's main hand. Call it whenever the hand changes, then wait for the draw. */
    public static void draw(LivingEntity shooter) { TaczLink.draw(shooter); }

    /** Fills a creature's gun (an agent changing magazines). */
    public static void reload(ItemStack gun) {
        ResourceLocation id = gunId(gun);
        if (id != null) TaczLink.reload(gun, magazine(id) - 1);
    }

    /**
     * Fires a creature's drawn gun at a point. Returns TaCZ's result: {@code "SUCCESS"}, or why not
     * ({@code "IS_DRAWING"}, {@code "COOL_DOWN"}, {@code "NO_AMMO"}...).
     */
    public static String fire(LivingEntity shooter, Vec3 at) {
        Vec3 from = shooter.getEyePosition(), d = at.subtract(from);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
        shooter.setYRot(yaw);
        shooter.setYHeadRot(yaw);
        shooter.setXRot(pitch);
        return TaczLink.shoot(shooter, pitch, yaw);
    }

    // ------------------------------------------------------------------
    // Hooks from TaczLink
    // ------------------------------------------------------------------

    /** A shot left the muzzle: the heat gun's flame and the cold gun's frost streak, and their sounds. */
    static void shot(LivingEntity shooter, ResourceLocation gun) {
        if (!(shooter.level() instanceof ServerLevel level)) return;
        if (gun.equals(HEAT_GUN)) {
            SetPiecePayload.send(level, Piece.GUN_HEAT, muzzle(shooter), reach(shooter, HEAT_RANGE), 1, shooter);
            if (shooter.tickCount % 3 == 0)
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35F, 1.5F);
        } else if (gun.equals(COLD_GUN)) {
            SetPiecePayload.send(level, Piece.GUN_FROST, muzzle(shooter), reach(shooter, COLD_RANGE), 0, shooter);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.0F, 1.6F);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.35F, 1.9F);
        } else if (gun.equals(TASER)) {
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
        } else if (gun.equals(DAMPENER_GUN)) {
            SetPiecePayload.send(level, Piece.DAMPENER_SHOT, muzzle(shooter),
                    reach(shooter, com.jayptucker.heroesevolved.dampener.DampenerGeometry.RANGE), 0, shooter);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.7F, 0.7F);
        }
    }

    /** A round hurt something. */
    static void hit(@Nullable LivingEntity attacker, LivingEntity target, ResourceLocation gun, Vec3 at) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || !allowed(attacker, target)) return;
        if (gun.equals(HEAT_GUN)) heat(attacker, target);
        else if (gun.equals(COLD_GUN)) cold(attacker, target, at);
        else if (gun.equals(TASER)) tase(attacker, target, at);
        else if (gun.equals(DAMPENER_GUN)) com.jayptucker.heroesevolved.dampener.Dampening.strike(attacker, target, at);
    }

    /** A round struck a block: cold bolts skin water with ice that melts again. */
    static void hitBlock(ServerLevel level, BlockHitResult hit, @Nullable Entity owner, ResourceLocation gun) {
        if (!gun.equals(COLD_GUN)) return;
        SetPiecePayload.send(level, Piece.GUN_FROST, hit.getLocation(), hit.getLocation(), 1, null);
        BlockPos center = hit.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            if (level.getBlockState(pos).getFluidState().getType() != Fluids.WATER || !level.getBlockState(pos).is(Blocks.WATER)
                    || !level.getBlockState(pos.above()).isAir() || !mayChange(level, pos, owner)) continue;
            level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.random, 60, 120));
        }
    }

    // ------------------------------------------------------------------
    // The effects (public for tests)
    // ------------------------------------------------------------------

    /** A heat round: alight, a little longer each time, and any ice it was locked in gone. */
    public static void heat(@Nullable LivingEntity attacker, LivingEntity target) {
        if (CryoService.isFrozen(target)) CryoService.releaseEntity(target);
        if (target.fireImmune() || target.isInWaterRainOrBubble()) return;
        target.setRemainingFireTicks(Math.min(HEAT_MAX, Math.max(target.getRemainingFireTicks(), 0) + HEAT_TICKS));
    }

    /** A cold round: frost that builds, then a short freeze. Cryokinetics shrug it off, as they do real frost. */
    public static void cold(@Nullable LivingEntity attacker, LivingEntity target, Vec3 at) {
        if (!(target.level() instanceof ServerLevel level)) return;
        SetPiecePayload.send(level, Piece.GUN_FROST, at, at, 1, null);
        if (CryoService.isFrozen(target)) return;
        if (CryoService.frostAmount(target) + FROST_TICKS >= target.getTicksRequiredToFreeze())
            CryoService.freezeSolid(target, target instanceof Player ? FREEZE_PLAYER : FREEZE_MOB, null);
        else CryoService.chill(target, FROST_TICKS);
    }

    /** A taser hit: the barbs bite and the current locks the target up. */
    public static void tase(@Nullable LivingEntity attacker, LivingEntity target, Vec3 at) {
        if (!(target.level() instanceof ServerLevel level)) return;
        Vec3 from = attacker == null ? at : muzzle(attacker);
        SetPiecePayload.send(level, Piece.GUN_TASER, from, at, 1, target);
        Stun.apply(target, attacker);
    }

    // ------------------------------------------------------------------

    /** Player-caused effects reach other players only where PvP is on, and nobody stuns themselves. */
    private static boolean allowed(@Nullable LivingEntity attacker, LivingEntity target) {
        if (attacker == target) return false;
        if (attacker instanceof ServerPlayer player && target instanceof ServerPlayer other)
            return player.getServer() != null && player.getServer().isPvpAllowed() && player.canHarmPlayer(other);
        return true;
    }

    /** A player may change a block they could build at; a creature, where mobs may grief. No break-event probe. */
    private static boolean mayChange(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
        if (owner instanceof ServerPlayer player)
            return player.mayBuild() && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Roughly where the muzzle is: a little forward, right and down from the eyes. */
    static Vec3 muzzle(LivingEntity shooter) {
        Vec3 look = shooter.getLookAngle();
        Vec3 right = Vec3.directionFromRotation(0, shooter.getYRot() + 90);
        return shooter.getEyePosition().add(look.scale(0.7)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    /** Where a straight shot along the look would stop: the first block, or {@code range} away. */
    static Vec3 reach(LivingEntity shooter, double range) {
        Vec3 eye = shooter.getEyePosition(), end = eye.add(shooter.getLookAngle().scale(range));
        BlockHitResult hit = shooter.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
}
