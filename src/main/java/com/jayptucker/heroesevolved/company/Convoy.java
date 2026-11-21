package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import com.jayptucker.heroesevolved.village.Villages;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Company takes someone with a gift: three armed agents and a Suppressor walk a villager out of their village at a
 * walk, the prisoner beside the Suppressor, whose quiet keeps their gift asleep. The village calls for help on the
 * emergency band. Ambush it and free them (every agent down), or it walks {@link #LENGTH} blocks out and they are gone.
 *
 * <p>The convoy moves as one point along a straight line away from its subject, at {@link #PACE}; each member walks to
 * their place round it, and the point waits for the Suppressor and the prisoner. Its agents stop nobody until it is
 * alarmed: someone too close, a power used near it, or one of them hurt.</p>
 */
public final class Convoy extends CompanyOps.Op {
    public enum Stage { MARCHING, FIGHTING, FREED, TAKEN }
    /** How far it walks, how fast its middle moves (blocks a tick), and how far behind it may leave its two. */
    public static final double LENGTH = 160, PACE = 0.045, KEEP_UP = 3.5;
    /** The lead agent warns you inside {@link #WARN}; inside {@link #CLOSE}, a power used inside {@link #LOUD_REACH}, or a blow, and it fights. */
    public static final double WARN = 14, CLOSE = 6, LOUD_REACH = 20, FIGHT_REACH = 32;
    public static final int GUARDS = 3, CALM_TICKS = 200;
    /** It calls up one more agent for each player past the first who fights it, up to this many in all. */
    public static final int MOST_GUARDS = 6;
    /** Its road ticket moves on every this many blocks, so the ground ahead of it is always loaded. */
    public static final double ROAD_STEP = 16;

    final BlockPos bell;
    final String village;
    Villager prisoner;
    ResourceLocation power;
    String who;
    CompanyAgentEntity suppressor;
    final List<CompanyAgentEntity> guards = new ArrayList<>();
    Vec3 start, heading, center;
    double along;
    Stage stage = Stage.MARCHING;
    boolean alerted, warned, giftBack;
    int calmFor;
    Vec3 road;
    EmergencyBand.Call call;
    ServerPlayer hero;

    private Convoy(ServerLevel level, ServerPlayer subject, BlockPos bell) {
        super(level, subject);
        this.bell = bell.immutable();
        this.village = VillageMemory.of(level, bell).name();
    }

    @Override public CompanyOps.Kind kind() { return CompanyOps.Kind.CONVOY; }
    public Stage stage() { return stage; }
    public Villager prisoner() { return prisoner; }
    public CompanyAgentEntity suppressor() { return suppressor; }
    public List<CompanyAgentEntity> guards() { return List.copyOf(guards); }
    public Vec3 center() { return center; }
    public boolean alerted() { return alerted; }
    public double along() { return along; }
    public BlockPos bell() { return bell; }
    public EmergencyBand.Call call() { return call; }

    /**
     * Takes someone from the village round {@code bell}, walking them out along {@code heading} (null: away from
     * {@code subject}). Null if there is nobody to take or the subject already has an operation.
     */
    public static Convoy start(ServerLevel level, ServerPlayer subject, BlockPos bell, Vec3 heading) {
        // Never into a village already calling for help.
        if (CompanyOps.current(subject) != null || com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(level, bell)
                || !CompanyOps.prepare(level, bell, subject)) return null;
        Villager prisoner = pick(level, bell);
        if (prisoner == null) return null;
        Convoy op = new Convoy(level, subject, bell);
        op.prisoner = prisoner;
        op.power = GiftedMobs.isGifted(prisoner) ? GiftedMobs.powerOf(prisoner) : GiftedVillagers.gift(prisoner);
        if (op.power == null) {
            // A gift nobody knew about, but the Company did.
            do op.power = GiftedMobs.randomPower(prisoner.getRandom());
            while (op.power.equals(ModAbilities.RADIATION_ID));
            GiftedVillagers.give(prisoner, op.power);
        }
        op.who = name(prisoner, op.village);
        if (heading == null) {
            heading = prisoner.position().subtract(subject.position()).multiply(1, 0, 1);
            if (heading.lengthSqr() < 1) {
                double a = level.random.nextDouble() * Math.PI * 2;
                heading = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
        }
        op.heading = heading.multiply(1, 0, 1).normalize();
        op.start = prisoner.position();
        op.center = op.start;
        // Its village stays loaded (to remember how it ended), and so does the road under it as it walks.
        op.keep(bell, "village");
        op.road = op.center;
        op.keep(BlockPos.containing(op.center), "road");
        if (prisoner.isSleeping()) prisoner.stopSleeping();
        CompanyOps.LIVE.add(prisoner.getUUID());
        prisoner.addTag(CompanyOps.PRISONER_TAG);
        Vec3 s = op.slot(Slot.SUPPRESSOR);
        op.suppressor = op.spawn(CompanyAgentEntity.Role.SUPPRESSOR, s, CompanyOps.yaw(s, s.add(op.heading)));
        for (Slot slot : List.of(Slot.LEAD, Slot.LEFT, Slot.RIGHT)) {
            Vec3 at = op.slot(slot);
            CompanyAgentEntity g = op.spawn(CompanyAgentEntity.Role.GUARD, at, CompanyOps.yaw(at, at.add(op.heading)));
            if (g != null) op.guards.add(g);
        }
        if (op.suppressor == null || op.guards.isEmpty()) {
            op.callOff();
            return null;
        }
        int ticks = (int) (LENGTH / PACE) + 2400;
        op.call = EmergencyBand.call(level, bell, EmergencyBand.Trouble.COMPANY, Component.literal("They've taken " + op.who
                + "! Men in dark suits are walking them out of " + op.village + " to the " + compass(op.heading) + "!"), ticks);
        Shouts.say(level, prisoner, "Let go of me!", 60);
        CompanyOps.add(op);
        return op;
    }

    /** Who the Company takes: a guardian first, then someone with a dormant gift, then anyone grown. */
    static Villager pick(ServerLevel level, BlockPos bell) {
        List<Villager> guardians = new ArrayList<>(), dormant = new ArrayList<>(), anyone = new ArrayList<>();
        for (Villager v : Villages.members(level, bell)) {
            if (v.isBaby() || GiftedMobs.isEvil(v) || VillagerMeltdown.critical(v) || Renegades.turning(v)
                    || com.jayptucker.heroesevolved.village.overload.Overloads.critical(v) || v.getTags().contains(CompanyOps.PRISONER_TAG)) continue;
            if (GiftedMobs.isGood(v)) guardians.add(v);
            else if (GiftedVillagers.gift(v) != null) dormant.add(v);
            else anyone.add(v);
        }
        List<Villager> from = !guardians.isEmpty() ? guardians : !dormant.isEmpty() ? dormant : anyone;
        return from.isEmpty() ? null : from.get(level.random.nextInt(from.size()));
    }

    /** "Hollis the Ashen", or "Ashford's smith". */
    static String name(Villager v, String village) {
        if (v.hasCustomName()) return v.getCustomName().getString();
        VillagerProfession job = v.getVillagerData().getProfession();
        if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) return "one of " + village + "'s people";
        String path = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath();
        String word = switch (path) {
            case "armorer", "weaponsmith", "toolsmith" -> "smith";
            case "cleric" -> "priest";
            default -> path;
        };
        return village + "'s " + word;
    }

    static String compass(Vec3 h) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double angle = Math.toDegrees(Math.atan2(h.z, h.x));
        return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    // ------------------------------------------------------------------
    // Marching
    // ------------------------------------------------------------------

    enum Slot { LEAD, SUPPRESSOR, PRISONER, LEFT, RIGHT }

    /** Where {@code slot} walks, round the convoy's middle. */
    Vec3 slot(Slot slot) {
        Vec3 right = new Vec3(-heading.z, 0, heading.x);
        Vec3 at = switch (slot) {
            case LEAD -> center.add(heading.scale(5));
            case SUPPRESSOR -> center.add(right.scale(1.3));
            case PRISONER -> center.subtract(right.scale(1.3));
            case LEFT -> center.subtract(heading.scale(4)).subtract(right.scale(2.5));
            case RIGHT -> center.subtract(heading.scale(4)).add(right.scale(2.5));
        };
        return CompanyOps.ground(level, at.x, at.z, center.y);
    }

    @Override void tick() {
        if (prisoner == null || !prisoner.isAlive() || prisoner.isRemoved()) { lost(); return; }
        List<CompanyAgentEntity> standing = standing();
        if (standing.isEmpty()) { free(); return; }
        if (!giftBack && CompanyOps.downed(suppressor)) {
            giftBack = true;
            Shouts.say(level, prisoner, "My gift... I can feel it again!", 80);
        }
        if (age % 5 == 0) watch(standing);
        if (over) return;
        if (alerted) {
            stage = Stage.FIGHTING;
            if (age % 100 == 0) backup();
            hold(prisoner, prisoner.position());
            boolean anyone = false;
            for (ServerPlayer p : level.players()) if (fightable(p)) anyone = true;
            if (anyone) calmFor = 0;
            else if (++calmFor > CALM_TICKS) {
                // Nobody left to fight: they gather up and move on.
                alerted = false;
                stage = Stage.MARCHING;
            }
            return;
        }
        march(standing);
        if (center.distanceToSqr(road) >= ROAD_STEP * ROAD_STEP) {
            road = center;
            keep(BlockPos.containing(center), "road");
        }
        if (along >= LENGTH) taken();
    }

    private void march(List<CompanyAgentEntity> standing) {
        boolean together = age < 40 || (prisoner.position().distanceToSqr(slot(Slot.PRISONER)) <= KEEP_UP * KEEP_UP
                && (CompanyOps.downed(suppressor) || suppressor.position().distanceToSqr(slot(Slot.SUPPRESSOR)) <= KEEP_UP * KEEP_UP));
        if (together) along += PACE;
        if (age % 5 != 0) return;
        Vec3 flat = start.add(heading.scale(along));
        center = CompanyOps.ground(level, flat.x, flat.z, center.y);
        for (CompanyAgentEntity a : standing) {
            if (a.getTarget() != null) continue;
            Slot slot = a == suppressor ? Slot.SUPPRESSOR : guards.indexOf(a) == 0 ? Slot.LEAD : guards.indexOf(a) == 1 ? Slot.LEFT : Slot.RIGHT;
            a.walkTo(slot(slot), 0.85);
        }
        hold(prisoner, slot(Slot.PRISONER));
    }

    /** Walks the prisoner to {@code to} (or keeps them there), whatever their own brain wants. */
    private void hold(Villager v, Vec3 to) {
        var brain = v.getBrain();
        if (v.position().distanceToSqr(to) > 1.2) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.7F, 0));
        else {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getNavigation().stop();
        }
    }

    /** Someone too close, warned, or worth fighting. */
    private void watch(List<CompanyAgentEntity> standing) {
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            double nearest = p.distanceToSqr(prisoner);
            for (CompanyAgentEntity a : standing) nearest = Math.min(nearest, p.distanceToSqr(a));
            if (!alerted && nearest <= CLOSE * CLOSE) { alert(p); return; }
            if (!alerted && !warned && nearest <= WARN * WARN) {
                warned = true;
                speak(guards.isEmpty() || CompanyOps.downed(guards.getFirst()) ? standing.getFirst() : guards.getFirst(),
                        "Company business. Keep walking.", 24);
            }
        }
    }

    private boolean fightable(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator() && p.isAlive() && p.distanceToSqr(center) <= FIGHT_REACH * FIGHT_REACH;
    }

    /** Alarmed by {@code p}: every agent turns on them. */
    void alert(ServerPlayer p) {
        if (hero == null) hero = p;
        if (alerted) return;
        alerted = true;
        warned = true;
        calmFor = 0;
        List<CompanyAgentEntity> standing = standing();
        if (!standing.isEmpty()) speak(standing.getFirst(), "Contact! Protect the package!", 32);
        for (CompanyAgentEntity a : standing) {
            a.stopWalking();
            if (a.getTarget() == null) a.setTarget(p);
        }
        backup();
    }

    /**
     * Backup up the road: one more agent for each player past the first who is fighting it, up to {@link #MOST_GUARDS},
     * so a party doesn't walk through what one player has to work for.
     */
    void backup() {
        List<ServerPlayer> fighting = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (fightable(p)) fighting.add(p);
        int want = Math.min(MOST_GUARDS, GUARDS + Math.max(0, fighting.size() - 1));
        int added = 0;
        while (guards.size() < want && !fighting.isEmpty()) {
            Vec3 ahead = CompanyOps.ground(level, center.x + heading.x * 10 + (guards.size() - 3) * 1.5, center.z + heading.z * 10, center.y);
            CompanyAgentEntity g = spawn(CompanyAgentEntity.Role.GUARD, ahead, CompanyOps.yaw(ahead, center));
            if (g == null) break;
            guards.add(g);
            g.setTarget(fighting.get(added % fighting.size()));
            added++;
        }
        if (added > 0) speak(guards.getLast(), "Backup's here. Keep them off the package!", 32);
    }

    @Override boolean stops(CompanyAgentEntity agent, ServerPlayer p) { return alerted && fightable(p); }

    @Override boolean powerUsed(ServerPlayer p, int cost) {
        if (alerted || p.isCreative() || p.distanceToSqr(center) > LOUD_REACH * LOUD_REACH) return false;
        alert(p);
        return true;
    }

    /** One of them was hurt by {@code p}. */
    void hurt(ServerPlayer p) {
        hero = p;
        alert(p);
    }

    @Override void down(CompanyAgentEntity agent, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) hero = p;
    }

    // ------------------------------------------------------------------
    // How it ends
    // ------------------------------------------------------------------

    /** Every agent down: the prisoner is free, wakes as their village's guardian, and walks home. */
    private void free() {
        stage = Stage.FREED;
        release();
        // Nobody good can hold Induced Radioactivity: that gift stays asleep. Their home is their village, not the road.
        if (!GiftedMobs.isGifted(prisoner) && !power.equals(ModAbilities.RADIATION_ID)) Renegades.guard(prisoner, power, bell);
        Shouts.talk(level, prisoner, "Thank you! I thought I'd never see " + village + " again.", 100);
        Vec3 at = prisoner.position();
        List<ItemStack> spoils = new ArrayList<>();
        spoils.add(PowerWeapons.ammo(PowerWeapons.TASER_CARTRIDGE, 2 * GUARDS));
        spoils.add(PowerWeapons.ammo(PowerWeapons.DAMPENER_ROUND, 3));
        spoils.add(new ItemStack(Items.EMERALD, 4 + level.random.nextInt(5)));
        CompanyOps.drop(level, at, spoils);
        List<ServerPlayer> present = new ArrayList<>();
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.distanceToSqr(at) <= 64 * 64) present.add(p);
        if (hero == null && !present.isEmpty()) hero = present.getFirst();
        int members = Villages.members(level, bell).size();
        VillageMemory.record(level, bell, "the Company", VillageMemory.Outcome.SAVED, hero, present, members, members);
        if (hero != null) CompanyAttention.raise(hero, CompanyAttention.FREED, null, "freed the Company's prisoner");
        tell(at, 96, who.substring(0, 1).toUpperCase() + who.substring(1) + " is free, and walks home to " + village + " with their gift awake.");
        EmergencyBand.end(call);
        finish(CompanyOps.AFTER_OP);
    }

    /** It walked out of reach: the prisoner is gone. */
    private void taken() {
        stage = Stage.TAKEN;
        for (CompanyAgentEntity a : agents) CompanyOps.leave(a);
        release();
        prisoner.discard();
        tell(center, 160, "The convoy is gone, and " + who + " with it. " + village + " won't forget.");
        int members = Villages.members(level, bell).size();
        VillageMemory.record(level, bell, "the Company", VillageMemory.Outcome.HIT, null, List.of(), members, members + 1);
        CompanyAttention.lower(subject, CompanyAttention.GOT_AWAY);
        EmergencyBand.end(call);
        finish(CompanyOps.AFTER_OP);
    }

    /** The prisoner died on the way. */
    private void lost() {
        for (CompanyAgentEntity a : agents) CompanyOps.leave(a);
        if (prisoner != null) CompanyOps.LIVE.remove(prisoner.getUUID());
        tell(center, 96, who.substring(0, 1).toUpperCase() + who.substring(1) + " didn't live through it.");
        int members = Villages.members(level, bell).size();
        VillageMemory.record(level, bell, "the Company", VillageMemory.Outcome.HIT, null, List.of(), members, members + 1);
        EmergencyBand.end(call);
        finish(CompanyOps.AFTER_OP);
    }

    private void release() {
        prisoner.removeTag(CompanyOps.PRISONER_TAG);
        CompanyOps.LIVE.remove(prisoner.getUUID());
        prisoner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /** The prisoner unloaded (or gone to another level) counts as losing track of them too. */
    @Override boolean lostTrack() { return super.lostTrack() || CompanyOps.lost(prisoner); }

    @Override void callOff() {
        if (prisoner != null && !prisoner.isRemoved()) release();
        else if (prisoner != null) {
            // Unloaded with their chunk: they are let go when they load (CompanyOps.joined), not taken.
            prisoner.removeTag(CompanyOps.PRISONER_TAG);
            CompanyOps.LIVE.remove(prisoner.getUUID());
        }
        if (call != null) EmergencyBand.end(call);
        super.callOff();
    }

    /** Moves it {@code blocks} further along at once (tests, previews). */
    public void hurry(double blocks) {
        along += blocks;
        Vec3 flat = start.add(heading.scale(along));
        center = CompanyOps.ground(level, flat.x, flat.z, center.y);
    }
}
