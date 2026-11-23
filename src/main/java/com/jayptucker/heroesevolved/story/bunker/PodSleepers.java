package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.night.NightThreeBunker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Odessa's people in the stasis pods. On the Last Day, those who made it down under Primatech are sealed in the pods
 * nobody else was given ({@code story/prologue/LastDayShelter}). A hundred and three years later, in the bunker under
 * spawn, about half of the hall's pods no sleeper has woken in still hold them ({@link #keeps}): frosted over, head bowed,
 * under a red nameplate, deceased, cause unknown. The other half failed, and stand wrecked and empty.
 * <p>
 * A sleeper is a villager with no will (no AI, so it never moves, falls or is pushed), never hurt, silent, a little
 * smaller than a villager is (a pod is under two blocks inside), and tagged so it is found again and never traded with.
 * No village event counts, picks or moves one ({@link com.jayptucker.heroesevolved.village.Villagers#inStasis}). The
 * present's bodies are kept in step with who has a pod ({@link #present}): a new sleeper's pod is emptied and made whole.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PodSleepers {
    /** On every body in a pod, of the past or the present. */
    public static final String TAG = "heroesevolved_pod_sleeper";
    /** How big a sleeper is drawn, so a villager fits under a pod's lid. */
    static final float SCALE = 0.88F;
    /** The names on the nameplates, by pod: the same in the past and the present. Original, ordinary names. */
    private static final String[] NAMES = {"M. ALVAREZ", "D. KOWALSKI", "J. PRUITT", "L. HAYES", "T. NGUYEN", "R. OKAFOR",
            "S. BAKER", "K. FOSTER", "A. REYES", "B. COLLINS", "E. WARD", "G. MORENO", "H. PATEL", "C. BENNET", "N. BRANDT"};
    private static final VillagerType[] LOOKS = {VillagerType.PLAINS, VillagerType.TAIGA, VillagerType.SAVANNA,
            VillagerType.DESERT, VillagerType.SNOW, VillagerType.JUNGLE, VillagerType.SWAMP};

    private PodSleepers() {}

    /** Who was sealed in pod {@code index}, as their nameplate gives it. */
    public static String name(int index) { return NAMES[Math.floorMod(index, NAMES.length)]; }

    /** The tag naming the pod a sleeper is in. */
    private static String podTag(int index) { return TAG + "_" + index; }

    /** The hall pods anyone could be sealed in: every one but Claire's. */
    public static boolean hallPod(int index) { return index >= 0 && index < BunkerLayout.HALL_PODS && index != NightThreeBunker.CLAIRE; }

    /**
     * Whether pod {@code index} of the bunker at {@code origin} still holds someone, if nobody has claimed it: about half
     * of each wall's pods, a different half in each world, the same one every time in this one.
     */
    public static boolean keeps(BlockPos origin, int index) {
        if (!hallPod(index)) return false;
        int first = index < BunkerLayout.HALL_PODS / 2 ? 0 : BunkerLayout.HALL_PODS / 2;
        List<Integer> wall = new java.util.ArrayList<>();
        for (int i = first; i < first + BunkerLayout.HALL_PODS / 2; i++) if (hallPod(i)) wall.add(i);
        wall.sort(java.util.Comparator.comparingInt(i -> mix(origin, i)));
        return wall.indexOf(index) < (wall.size() + 1) / 2;
    }

    /** How pod {@code index} failed, if it did. */
    private static CryoChamberBlock.Condition wreck(BlockPos origin, int index) {
        return (mix(origin, index + 100) & 1) == 0 ? CryoChamberBlock.Condition.SHATTERED : CryoChamberBlock.Condition.SPRUNG;
    }

    private static int mix(BlockPos origin, int index) {
        long h = (origin.asLong() ^ index * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        return (int) (h ^ h >>> 31);
    }

    /** The space inside pod {@code base}. */
    private static AABB inside(BlockPos base) { return new AABB(base).expandTowards(0, 1, 0).deflate(0.05); }

    /** Whoever is sealed in the pod at {@code base} (none if its chunk's entities aren't loaded). */
    public static List<Villager> in(ServerLevel level, BlockPos base) {
        return level.getEntitiesOfClass(Villager.class, inside(base), v -> v.getTags().contains(TAG));
    }

    /** Whether someone is sealed in the pod at {@code base}. */
    public static boolean occupied(ServerLevel level, BlockPos base) { return !in(level, base).isEmpty(); }

    /**
     * Seals {@code body} in pod {@code index} of the bunker at {@code origin}: on the pod's floor (its feet in the base
     * plate), facing out of the glass, a little smaller, still for good; {@code dead}, its head bowed. The pod shuts on it.
     */
    public static void seal(ServerLevel level, BlockPos origin, BunkerLayout layout, int index, Villager body, boolean dead) {
        BlockPos base = origin.offset(layout.podOffset(index));
        Direction facing = layout.podFacing(index);
        float yaw = CryoChamberBlock.yaw(facing);
        body.stopRiding();
        body.ejectPassengers();
        body.setPose(net.minecraft.world.entity.Pose.STANDING);
        body.getBrain().clearMemories();
        body.getNavigation().stop();
        body.setNoAi(true);
        body.setInvulnerable(true);
        body.setSilent(true);
        body.setPersistenceRequired();
        AttributeInstance scale = body.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(SCALE);
        Vec3 at = Vec3.atBottomCenterOf(base);
        body.moveTo(at.x, at.y, at.z, yaw, dead ? 38 : 0);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.setDeltaMovement(Vec3.ZERO);
        body.addTag(TAG);
        body.addTag(podTag(index));
        if (level.getBlockState(base).getBlock() instanceof CryoChamberBlock) CryoChamberBlock.setOpen(level, base, false);
    }

    /** A new body for pod {@code index}: one of Odessa's people, in an ordinary robe, not a child. */
    public static Villager body(ServerLevel level, int index) {
        Villager villager = com.jayptucker.heroesevolved.village.Villagers.actor(level);
        if (villager == null) return null;
        villager.setVillagerData(villager.getVillagerData().setType(LOOKS[Math.floorMod(index * 3 + 1, LOOKS.length)])
                .setProfession(VillagerProfession.NONE));
        return villager;
    }

    /**
     * The bunker under spawn, a hundred and three years on. Of the hall pods nobody has a claim on, those that {@link #keeps
     * keep} their sleeper hold a frosted body under a red nameplate, and the rest stand wrecked and empty; a pod that is
     * someone's is whole and holds nobody else. Only where the pod's chunk and its entities are loaded, so a body is never
     * put in twice. Run every second while the bunker is built, which also brings older worlds' full halls round to this.
     */
    public static void present(ServerLevel level, BunkerData data) {
        if (!data.built) return;
        BunkerLayout layout = data.layout();
        for (int i = 0; i < BunkerLayout.HALL_PODS; i++) {
            if (!hallPod(i)) continue;
            BlockPos base = data.origin.offset(layout.podOffset(i));
            if (!level.isLoaded(base) || !level.areEntitiesLoaded(ChunkPos.asLong(base))) continue;
            if (!(level.getBlockState(base).getBlock() instanceof CryoChamberBlock)) continue;
            List<Villager> there = in(level, base);
            if (data.pods.containsValue(i)) {
                // Someone's pod: theirs, whole, and nobody else in it.
                there.forEach(Entity::discard);
                CryoChamberBlock.setCondition(level, base, CryoChamberBlock.Condition.SOUND);
                continue;
            }
            if (!keeps(data.origin, i)) {
                // This one failed, long ago: nobody in it, its door no longer shutting on anything.
                there.forEach(Entity::discard);
                CryoChamberBlock.setOpen(level, base, true);
                CryoChamberBlock.setCondition(level, base, wreck(data.origin, i));
                plate(level, data, layout, i, "POD BREACHED", "NO REMAINS");
                continue;
            }
            if (there.isEmpty()) {
                Villager body = body(level, i);
                if (body == null) continue;
                seal(level, data.origin, layout, i, body, true);
                if (!level.addFreshEntity(body)) continue;
            } else {
                if (there.size() > 1) there.subList(1, there.size()).forEach(Entity::discard);
                still(there.getFirst());
            }
            CryoChamberBlock.setOpen(level, base, false);
            CryoChamberBlock.setCondition(level, base, CryoChamberBlock.Condition.FROSTED);
            // Its plate says so, whenever it doesn't yet: a plate whose chunk wasn't loaded when the body was put in (or
            // one a waking relabelled before the pod was given back) is caught up here.
            plate(level, data, layout, i, "DECEASED", "CAUSE UNKNOWN");
        }
    }

    /** Whatever reached a body saved before they were out of every event's reach: no flames, and no gift to wake. */
    private static void still(Villager body) {
        body.clearFire();
        if (com.jayptucker.heroesevolved.mimicry.GiftedVillagers.gift(body) != null)
            com.jayptucker.heroesevolved.mimicry.GiftedVillagers.give(body, null);
    }

    /** Pod {@code index}'s nameplate in the present, if it doesn't already say {@code state}. */
    private static void plate(ServerLevel level, BunkerData data, BunkerLayout layout, int index, String state, String detail) {
        BlockPos cell = layout.podSign(index);
        if (cell == null) return;
        BlockPos pos = data.origin.offset(cell);
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) return;
        var text = sign.getFrontText();
        if (text.getMessage(2, false).getString().equals(state) && text.getMessage(1, false).getString().equals(name(index))) return;
        BunkerBuilder.signPod(level, data.origin, layout, index, DyeColor.RED,
                String.format("SUBJECT %02d", index + 1), name(index), state, detail);
    }

    /** Empties pod {@code index} of the bunker under spawn for the sleeper who has just been given it, and makes it whole. */
    public static void clear(ServerLevel level, BunkerData data, int index) {
        BlockPos base = data.origin.offset(data.layout().podOffset(index));
        if (!level.isLoaded(base)) return;
        in(level, base).forEach(Entity::discard);
        CryoChamberBlock.setCondition(level, base, CryoChamberBlock.Condition.SOUND);
    }

    /** Nothing lingers on a body in stasis: no potion, poison, glow or radiation an event would put on it. */
    @SubscribeEvent
    public static void noEffects(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (com.jayptucker.heroesevolved.village.Villagers.inStasis(event.getEntity()))
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** Nor does lightning make a witch of one. */
    @SubscribeEvent
    public static void noChange(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre event) {
        if (com.jayptucker.heroesevolved.village.Villagers.inStasis(event.getEntity())) event.setCanceled(true);
    }

    /** Nobody trades with, or leads off, the dead (or the sleeping). */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!event.getTarget().getTags().contains(TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getTarget().getTags().contains(TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }
}
