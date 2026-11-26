package com.jayptucker.heroesevolved.amplify;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AmpSightPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every power, supercharged. A Surge gives each power a bigger version of what it already does; an Overcharge gives it
 * something it can't normally do, then a backlash. What changes a power's own numbers is asked where that power decides
 * them ({@link Amplification#surged} and {@link Amplification#overcharged} in its own code); what a power can't do on its
 * own is done here. The table is in {@code docs/power-amplification.md}.
 *
 * <ul>
 *   <li>Asked in the power's code: Fold Space's reach and Brave New World (Space-Time), Read Mind's reach (Telepathy),
 *   breach life, cost, worlds and overshoot (Vibe), Skyburst's charge and length (Flight), blue fire, Heat and white fire
 *   (Pyrokinesis), one more chain (Electrokinesis), free water and the sea's boost (Hydrokinesis), longer freezes
 *   (Cryokinesis), Strain and its power (Quake), Resonance, Ringing and shattering (Sonic Resonance), a longer Grip
 *   (Telekinesis), a longer Stone Skin (Density), twice the Pressure (Invisibility), a stronger Rapid Mending (Regeneration),
 *   a faster-filling Core (Induced Radioactivity), an extra Shard (Matter Assembly), instant
 *   attunement and free Echoes (Empathic Mimicry), and slower Suspicion (Shapeshifting).</li>
 *   <li>Done here: Momentum, Daphne's run, the thousand voices, the storm, refilling water, the flash freeze, the
 *   levitation, the shelter of ground, the dome, the spilling heal, the Core pouring out, the grid answering, the shard orbit,
 *   the Apex growth, and every backlash.</li>
 * </ul>
 */
final class AmpTuned {
    private static final ResourceLocation OVER_SPEED = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_over_speed"),
            APEX_SCALE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_apex_scale"),
            APEX_DAMAGE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "amp_apex_damage");
    static final double TELEPATH_RANGE = 96, STORM_RANGE = 12, FREEZE_RADIUS = 6, LIFT_RANGE = 10, GHOST_RADIUS = 4,
            DOME_RADIUS = 6, SPILL_RADIUS = 4, CORE_RADIUS = 8, GRID_RADIUS = 32, ORBIT_RANGE = 16, FOLD_FRIENDS = 8;
    static final int DAPHNE_TICKS = 60, STORM_BOLTS = 6;

    private AmpTuned() {}

    private static String path(Amplification.Boost b) { return b.power == null ? "" : b.power.getPath(); }

    // ------------------------------------------------------------------
    // Surge
    // ------------------------------------------------------------------

    static void surgeStart(Amplification.Boost b) {
        if (b.target instanceof ServerPlayer p && path(b).equals("superspeed"))
            com.jayptucker.heroesevolved.speed.SpeedService.atLeastMomentum(p, 100);
    }

    // ------------------------------------------------------------------
    // Overcharge
    // ------------------------------------------------------------------

    static void overStart(Amplification.Boost b) {
        LivingEntity target = b.target;
        if (!(target instanceof ServerPlayer p)) {
            // A pet or a gifted mob: faster on its feet for the Overcharge (its moves already hit half again as hard).
            var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(OVER_SPEED, .4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            return;
        }
        ServerLevel level = p.serverLevel();
        switch (path(b)) {
            case "telepathy" -> sight(p);
            case "vibe" -> tell(p, "vibe");
            case "flight" -> tell(p, "flight");
            case "electrokinesis" -> { b.count = STORM_BOLTS; b.mark = level.getGameTime(); }
            case "hydrokinesis" -> com.jayptucker.heroesevolved.hydro.HydroService.fill(p, com.jayptucker.heroesevolved.hydro.HydroService.capacity(p));
            case "cryokinesis" -> flashFreeze(p);
            case "sonic_resonance" -> com.jayptucker.heroesevolved.sonic.SonicService.addResonance(p, 100);
            case "telekinesis" -> lift(p);
            case "phasing", "invisibility" -> b.at = p.position();
            case "shapeshifting" -> apex(p, true);
            case "time_manipulation" -> tell(p, "time");
            case "superspeed" -> tell(p, "speed");
            default -> {}
        }
    }

    static void overTick(Amplification.Boost b, long now) {
        if (!(b.target instanceof ServerPlayer p)) return;
        ServerLevel level = p.serverLevel();
        switch (path(b)) {
            case "superspeed" -> {
                // Daphne's run: a second of sprinting and you come out three seconds in the past.
                if (b.used == 0 && p.isSprinting()) { if (++b.count >= 20) { daphne(p); b.used = 1; } }
                else if (!p.isSprinting()) b.count = 0;
            }
            case "telepathy" -> { if ((now - b.overStart) % 20 == 0) sight(p); }
            case "electrokinesis" -> {
                if (b.count > 0 && (now - b.mark) % 20 == 0) storm(p, b);
            }
            case "hydrokinesis" -> {
                if (now % 20 == 0) com.jayptucker.heroesevolved.hydro.HydroService.fill(p, com.jayptucker.heroesevolved.hydro.HydroService.capacity(p));
            }
            case "sonic_resonance" -> { if (now % 20 == 0) com.jayptucker.heroesevolved.sonic.SonicService.addResonance(p, 100); }
            case "telekinesis" -> {
                // A nosebleed while it holds (not when the Overcharge was given safe: A Thousand Times, the awakening).
                if (!b.safe && (now - b.overStart) % 20 == 10) { p.invulnerableTime = 0; p.hurt(p.damageSources().magic(), b.backlash); }
            }
            case "phasing" -> {
                b.at = p.position();
                if (now % 10 == 0) SetPiecePayload.send(level, Piece.AMP_DOME, p.position(), p.position().add(0, 1, 0), (float) GHOST_RADIUS, p, 1);
            }
            case "invisibility" -> {
                if (now % 10 == 0 && b.at != null) SetPiecePayload.send(level, Piece.AMP_DOME, b.at, b.at.add(0, 1, 0), (float) DOME_RADIUS, null, 0);
            }
            case "regeneration" -> {
                if (now % 10 == 0) for (LivingEntity f : StyleOps.friends(p, p.position(), SPILL_RADIUS))
                    if (f != p && f.getHealth() < f.getMaxHealth()) { StyleOps.heal(f, 1); if (now % 20 == 0) zap(level, p, f); }
            }
            case "radiation" -> {
                if ((now - b.overStart) % 20 == 0) {
                    for (LivingEntity foe : StyleOps.foes(p, p.position(), CORE_RADIUS))
                        com.jayptucker.heroesevolved.radiation.RadiationService.irradiate(foe, p, 1, 100);
                    com.jayptucker.heroesevolved.radiation.RadiationService.vent(p, 10);
                    SetPiecePayload.send(level, Piece.AMP_WAVE, p.position(), p.position().add(0, 1, 0), (float) CORE_RADIUS, p);
                }
            }
            case "matter_assembly" -> {
                if ((now - b.overStart) % 10 == 0) {
                    LivingEntity foe = StyleOps.foes(p, p.position(), ORBIT_RANGE).stream().findFirst().orElse(null);
                    if (foe != null) {
                        Vec3 from = p.getEyePosition().add(0, .4, 0);
                        com.jayptucker.heroesevolved.assembly.MatterAssemblyService.launch(p, from, foe.getBoundingBox().getCenter().subtract(from).normalize(),
                                6 + AmpService.tier(p));
                    }
                }
            }
            case "pyrokinesis" -> { if (now % 20 == 0) melt(p); }
            default -> {}
        }
    }

    static void overEnd(Amplification.Boost b, boolean backlash) {
        LivingEntity target = b.target;
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(OVER_SPEED);
        if (target instanceof ServerPlayer p && path(b).equals("shapeshifting")) apex(p, false);
        if (!backlash || !(target instanceof ServerPlayer p)) return;
        float scale = b.backlash;
        int ticks = Math.round(scale * 20);
        switch (path(b)) {
            case "superspeed" -> com.jayptucker.heroesevolved.speed.SpeedFriction.burst(p, 60 * scale);
            case "time_manipulation" -> daze(p, 2 * ticks);
            case "telepathy" -> Amplification.lock(p, b.power, 4 * ticks);
            case "flight" -> p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 3 * ticks + 20, 0));
            case "pyrokinesis" -> p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 2 * ticks));
            case "electrokinesis" -> {
                SetPiecePayload.send(p.serverLevel(), Piece.AMP_STORM, p.position().add(0, 12, 0), p.position(), 1, null);
                p.invulnerableTime = 0;
                p.hurt(p.damageSources().magic(), 3 * scale);
            }
            case "hydrokinesis" -> {
                com.jayptucker.heroesevolved.hydro.HydroService.soak(p, 120);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4 * ticks, 1));
            }
            case "cryokinesis" -> {
                p.setTicksFrozen(Math.max(p.getTicksFrozen(), Math.round(130 * scale)));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * ticks, 1));
            }
            case "quake" -> com.jayptucker.heroesevolved.quake.QuakeKit.setStrain(p,
                    Math.max(com.jayptucker.heroesevolved.quake.QuakeKit.strain(p), Math.round(90 * scale)));
            case "sonic_resonance" -> Amplification.lock(p, b.power, 4 * ticks);
            case "phasing" -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5 * ticks, 1));
            case "invisibility" -> PlayerEnergyService.consumeUpTo(p, Math.round(PlayerEnergyService.getEnergy(p) * scale));
            case "regeneration" -> com.jayptucker.heroesevolved.regeneration.RegenerationService.addRegrowth(p,
                    -Math.round(com.jayptucker.heroesevolved.regeneration.RegenerationService.regrowth(p) * scale));
            case "radiation" -> com.jayptucker.heroesevolved.radiation.RadiationService.addMass(p, Math.round(40 * scale));
            case "matter_assembly" -> p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6 * ticks, 0));
            case "empathic_mimicry" -> Amplification.lock(p, b.power, 8 * ticks);
            case "shapeshifting" -> Amplification.lock(p, b.power, 6 * ticks);
            default -> { return; }
        }
        Amplification.show(b, Piece.AMP_BACKLASH, 1);
        p.displayClientMessage(Component.translatable("amp.heroesevolved.backlash." + path(b)).withStyle(ChatFormatting.DARK_RED), true);
    }

    // ------------------------------------------------------------------
    // What a power can't do on its own
    // ------------------------------------------------------------------

    private static void tell(ServerPlayer p, String what) {
        p.displayClientMessage(Component.translatable("amp.heroesevolved.over." + what).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private static void zap(ServerLevel level, LivingEntity from, LivingEntity to) {
        SetPiecePayload.send(level, Piece.AMP_ZAP, from.getBoundingBox().getCenter(), to.getBoundingBox().getCenter(), .5F, from, to.getId());
    }

    /** Daphne's run: out of the sprint three seconds in the past, where you were, with the health you had then. */
    static boolean daphne(ServerPlayer p) {
        var past = com.jayptucker.heroesevolved.time.SpaceTimeService.past(p, DAPHNE_TICKS);
        if (past == null) return false;
        Vec3 from = p.position();
        p.teleportTo(past.position().x, past.position().y, past.position().z);
        p.absMoveTo(past.position().x, past.position().y, past.position().z);
        p.setHealth(Math.min(p.getMaxHealth(), Math.max(p.getHealth(), past.health())));
        SetPiecePayload.send(p.serverLevel(), Piece.TIME_SECOND, past.position(), from, 1, p);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_SURGE, past.position(), past.position().add(0, 2, 0), 1.8F, p);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1, 1.4F);
        tell(p, "daphne");
        return true;
    }

    /** Brave New World: the overcharged traveller's fold takes every friend within eight blocks with them. */
    static int folded(ServerPlayer p, Vec3 from, Vec3 to) {
        Amplification.Boost b = Amplification.BOOSTS.get(p.getUUID());
        if (b == null || !b.over || b.used != 0 || !path(b).equals("time_manipulation")) return 0;
        b.used = 1;
        List<LivingEntity> friends = new ArrayList<>();
        for (LivingEntity f : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(FOLD_FRIENDS),
                e -> e != p && e.isAlive() && AmpService.ally(p, e) && e.position().distanceToSqr(from) <= FOLD_FRIENDS * FOLD_FRIENDS && !e.isPassenger()))
            friends.add(f);
        for (int i = 0; i < friends.size(); i++) {
            LivingEntity f = friends.get(i);
            double angle = i * Math.PI * 2 / Math.max(1, friends.size());
            Vec3 spot = to.add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6);
            if (!com.jayptucker.heroesevolved.speed.SpeedService.safe(f, spot)) spot = to;
            Vec3 was = f.position();
            if (f instanceof ServerPlayer fp) fp.teleportTo(spot.x, spot.y, spot.z);
            else f.teleportTo(spot.x, spot.y, spot.z);
            f.absMoveTo(spot.x, spot.y, spot.z);
            daze(f, 40);
            SetPiecePayload.send(p.serverLevel(), Piece.TIME_SECOND, spot, was, 1, f);
        }
        if (!friends.isEmpty()) tell(p, "brave_new_world");
        return friends.size();
    }

    private static void daze(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks + 20, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
    }

    /** A thousand voices: every mind within 96 blocks shows through walls, for the telepath alone. */
    private static void sight(ServerPlayer p) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(TELEPATH_RANGE),
                e -> e != p && e.isAlive() && e.distanceToSqr(p) <= TELEPATH_RANGE * TELEPATH_RANGE)) {
            ids.add(e.getId());
            if (ids.size() >= 128) break;
        }
        if (p.connection != null) PacketDistributor.sendToPlayer(p, new AmpSightPayload(ids, 30));
    }

    /** The storm: a red-and-blue bolt from the sky onto a foe near the overcharged electrokinetic, once a second. */
    private static void storm(ServerPlayer p, Amplification.Boost b) {
        b.count--;
        List<LivingEntity> foes = StyleOps.foes(p, p.position(), STORM_RANGE);
        if (foes.isEmpty()) return;
        LivingEntity foe = foes.get(p.getRandom().nextInt(foes.size()));
        Vec3 at = foe.position();
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_STORM, at.add(0, 14, 0), at, 1.4F, null);
        com.jayptucker.heroesevolved.electro.ElectroService.shock(p, foe, 5 + AmpService.tier(p));
        p.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1, 1.2F);
    }

    /** Flash freeze: every foe within six blocks freezes solid at once. */
    private static void flashFreeze(ServerPlayer p) {
        for (LivingEntity foe : StyleOps.foes(p, p.position(), FREEZE_RADIUS))
            com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(foe, 100, p);
        SetPiecePayload.send(p.serverLevel(), Piece.AMP_WAVE, p.position(), p.position().add(0, 1, 0), (float) FREEZE_RADIUS, p, 2);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, .6F);
    }

    /** Everything at once: every foe in front, within ten blocks, rises off the ground for the Overcharge. */
    private static void lift(ServerPlayer p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        for (LivingEntity foe : StyleOps.foes(p, p.position(), LIFT_RANGE)) {
            Vec3 d = foe.position().subtract(p.position()).multiply(1, 0, 1);
            if (d.lengthSqr() > .5 && d.normalize().dot(look) < .3) continue;
            foe.addEffect(new MobEffectInstance(MobEffects.LEVITATION, Amplification.OVERCHARGE_TICKS, 0));
            SetPiecePayload.send(p.serverLevel(), Piece.STYLE_TK_GRASP, foe.position(), foe.position(), Amplification.OVERCHARGE_TICKS, foe);
        }
    }

    /** White fire: snow and ice melt round the overcharged pyrokinetic. */
    private static void melt(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition();
        int melted = 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -2, -3), base.offset(3, 2, 3))) {
            if (melted >= 12) break;
            BlockState state = level.getBlockState(pos);
            BlockState into = state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) ? Blocks.AIR.defaultBlockState()
                    : state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) ? Blocks.WATER.defaultBlockState() : null;
            if (into == null || !com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(p, level, pos)) continue;
            level.setBlockAndUpdate(pos, into);
            melted++;
        }
    }

    /** Apex now: the shapeshifter grows half again and hits half again as hard for the Overcharge. */
    private static void apex(ServerPlayer p, boolean on) {
        var scale = p.getAttribute(Attributes.SCALE);
        var damage = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (on) {
            if (scale != null) scale.addOrUpdateTransientModifier(new AttributeModifier(APEX_SCALE, .6, AttributeModifier.Operation.ADD_VALUE));
            if (damage != null) damage.addOrUpdateTransientModifier(new AttributeModifier(APEX_DAMAGE, .5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            tell(p, "apex");
        } else {
            if (scale != null) scale.removeModifier(APEX_SCALE);
            if (damage != null) damage.removeModifier(APEX_DAMAGE);
        }
        p.refreshDimensions();
    }

    /** Sonic Resonance overcharged: a Cry breaks glass, ice and stone where it lands. */
    static void shatter(ServerPlayer p, BlockPos center) {
        if (!Amplification.overcharged(p)) return;
        ServerLevel level = p.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (state.isAir() || hardness < 0 || hardness > 3 || level.getBlockEntity(pos) != null) continue;
            if (!com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(p, level, pos)) continue;
            level.destroyBlock(pos, true, p);
        }
    }

    // ------------------------------------------------------------------
    // Protection: the ghost party and the dome
    // ------------------------------------------------------------------

    /** Friends inside an overcharged earthbender's shelter of ground, or an overcharged force-field user's dome, take no harm. */
    static void incoming(LivingIncomingDamageEvent event) {
        if (Amplification.BOOSTS.isEmpty() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity victim = event.getEntity();
        for (Amplification.Boost b : Amplification.BOOSTS.values()) {
            if (!b.over || b.at == null || !(b.target instanceof ServerPlayer p) || p.level() != victim.level()) continue;
            String path = path(b);
            double radius = path.equals("phasing") ? GHOST_RADIUS : path.equals("invisibility") ? DOME_RADIUS : -1;
            if (radius < 0 || victim.position().distanceToSqr(b.at) > radius * radius) continue;
            if (victim != p && !StyleOps.isFriend(p, victim) && !AmpService.ally(p, victim)) continue;
            Entity attacker = event.getSource().getEntity();
            if (path.equals("invisibility") && attacker != null && attacker.position().distanceToSqr(b.at) <= radius * radius) continue;
            event.setCanceled(true);
            return;
        }
    }

    /** For tests: the overcharge pieces that are nearest a body. */
    static LivingEntity nearestFoe(ServerPlayer p, double radius) {
        return StyleOps.foes(p, p.position(), radius).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }
}
