package com.jayptucker.heroesevolved.dampener;

import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * An Ability Dampener's insides: a Forge Energy battery and the field it pays for. Every tick it stands switched on,
 * unpowered by redstone and with enough energy, it burns {@link DampenerGeometry#upkeep} FE and holds a sphere of
 * {@code radius} blocks round itself in which no power answers.
 *
 * <p>The field is only live while the block entity ticks: an EMP that stalls it (or drains it) drops the field that
 * same tick, because {@link #live()} asks when it last paid.
 */
public final class AbilityDampenerBlockEntity extends BlockEntity {
    private final Battery energy = new Battery();
    private int radius = DampenerGeometry.DEFAULT_RADIUS;
    private boolean enabled = true;
    /** Ticks the field has been spinning up, 0 when down, {@link DampenerGeometry#SPIN_UP} once full. */
    private int spin;
    /** The game tick it last paid for a full field; the field is live only while this is current. */
    private long paidAt = Long.MIN_VALUE / 2;

    public AbilityDampenerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ABILITY_DAMPENER_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // What the rest of the mod asks
    // ------------------------------------------------------------------

    public IEnergyStorage energy() { return energy; }
    public int stored() { return energy.getEnergyStored(); }
    public int radius() { return radius; }
    public boolean enabled() { return enabled; }

    /** True while the field is up: fully spun and paid for this tick or the last. */
    public boolean live() {
        return level != null && !isRemoved() && spin >= DampenerGeometry.SPIN_UP && level.getGameTime() - paidAt <= 1;
    }

    /** Whether {@code point} is inside the live field. */
    public boolean covers(Vec3 point) {
        return live() && point.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) radius * radius;
    }

    /** Fills the battery directly (tests, and the creative tab's full projector). */
    public void fill(int fe) {
        energy.set(Math.clamp((long) stored() + fe, 0, DampenerGeometry.CAPACITY));
        setChanged();
    }

    // ------------------------------------------------------------------
    // What a player changes
    // ------------------------------------------------------------------

    /** The next radius. Returns it. */
    int cycleRadius() {
        radius = DampenerGeometry.next(radius);
        setChanged();
        return radius;
    }

    /** Switches it on or off. Returns whether it is now on. */
    boolean toggle() {
        enabled = !enabled;
        setChanged();
        return enabled;
    }

    // ------------------------------------------------------------------
    // Ticking (server only; an EMP's stall skips this)
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbilityDampenerBlockEntity self) {
        if (!(level instanceof ServerLevel server)) return;
        boolean wanted = self.enabled && !level.hasNeighborSignal(pos);
        int cost = DampenerGeometry.upkeep(self.radius);
        boolean paid = wanted && self.energy.burn(cost);
        boolean wasLive = self.live();
        if (paid) {
            if (self.spin == 0) server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
            self.spin = Math.min(DampenerGeometry.SPIN_UP, self.spin + 1);
            if (self.spin >= DampenerGeometry.SPIN_UP) self.paidAt = server.getGameTime();
            self.setChanged();
        } else if (self.spin > 0) {
            self.spin = 0;
            server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        boolean live = self.live();
        if (live != wasLive || live) Dampening.track(self, live);
        boolean lit = self.spin > 0;
        if (state.getValue(AbilityDampenerBlock.ACTIVE) != lit)
            level.setBlock(pos, state.setValue(AbilityDampenerBlock.ACTIVE, lit), Block.UPDATE_CLIENTS);
        // The first copy goes out the moment it starts, so the dome is seen growing from the pylon.
        if (self.spin == 1 || self.spin > 0 && server.getGameTime() % DampenerGeometry.FIELD_RESEND == 0) self.show(server);
        if (live && server.getGameTime() % 80 == 0)
            server.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.55F);
    }

    /**
     * The dome: origin at the block's centre, target's x is how far it has spun (0 to 1), scale is its radius. Bound
     * to nothing, so an unbound successor at the same origin and target would stop it; the art fades it out itself.
     */
    private void show(ServerLevel level) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        float grown = spin / (float) DampenerGeometry.SPIN_UP;
        SetPiecePayload.send(level, Piece.DAMPENER_FIELD, center, center.add(grown, 0, 0), radius, null);
    }

    @Override public void setRemoved() {
        super.setRemoved();
        Dampening.track(this, false);
    }

    // ------------------------------------------------------------------
    // Saving, and carrying the charge in the item when it's broken
    // ------------------------------------------------------------------

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", stored());
        tag.putInt("radius", radius);
        tag.putBoolean("enabled", enabled);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(Math.clamp(tag.getInt("energy"), 0, DampenerGeometry.CAPACITY));
        radius = DampenerGeometry.valid(tag.contains("radius") ? tag.getInt("radius") : DampenerGeometry.DEFAULT_RADIUS);
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (stored() > 0) components.set(ModItems.STORED_ENERGY.get(), stored());
    }

    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        energy.set(Math.clamp(input.getOrDefault(ModItems.STORED_ENERGY.get(), 0), 0, DampenerGeometry.CAPACITY));
    }

    @Override public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("energy");
    }

    /** The projector's battery. Machines, cables and an Electrokinetic's lightning fill it; an EMP drains it. */
    private final class Battery extends EnergyStorage {
        Battery() { super(DampenerGeometry.CAPACITY, DampenerGeometry.MAX_RECEIVE, DampenerGeometry.MAX_EXTRACT); }

        void set(long fe) { energy = (int) Math.clamp(fe, 0, capacity); }

        /** Pays {@code fe} if it has it. */
        boolean burn(int fe) {
            if (energy < fe) return false;
            energy -= fe;
            return true;
        }

        @Override public int receiveEnergy(int toReceive, boolean simulate) {
            if (level instanceof ServerLevel server && GridDisruption.jammed(server, worldPosition)) return 0;
            int got = super.receiveEnergy(toReceive, simulate);
            if (got > 0 && !simulate) setChanged();
            return got;
        }

        @Override public int extractEnergy(int toExtract, boolean simulate) {
            int took = super.extractEnergy(toExtract, simulate);
            if (took > 0 && !simulate) setChanged();
            return took;
        }
    }
}
