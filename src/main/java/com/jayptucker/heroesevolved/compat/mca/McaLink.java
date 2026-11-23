package com.jayptucker.heroesevolved.compat.mca;

import com.jayptucker.heroesevolved.village.Villagers;
import com.mojang.logging.LogUtils;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.server.SpawnQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

/**
 * The only class that touches Minecraft Comes Alive (MCA Reborn, NeoForge 1.21.1), built only when MCA is loaded. How MCA
 * works, read from its source and checked against the 7.7.36 jar:
 * <ul>
 *   <li>Its humans ({@code mca:male_villager}, {@code mca:female_villager}) extend vanilla {@link Villager}.
 *   <li>{@code ServerLevel.addEntity} is intercepted: a plain vanilla villager whose spawn reason is allowed (no reason
 *       counts as natural) is refused, so {@code addFreshEntity} answers false, and a tick later a new human with a new
 *       UUID stands there with only the name, job, trades and tags copied. {@link #holdBack} and {@link #become} are how
 *       our own spawns avoid that.
 *   <li>{@code /mca-admin convertVanillaVillagers} can queue villagers already in the world for the same swap; {@link #tick} takes them back out and
 *       swaps them keeping everything, or leaves them if a scene or event is using them.
 *   <li>MCA answers {@code interactAt}, which the game tries before the plain use, so {@code EntityInteract} (favours,
 *       markets, carrying and every other touch of ours) would never fire on its humans. {@link #usedAt} fixes that.
 * </ul>
 * Every call is guarded: a change in MCA logs once and switches this link off, and villagers stay vanilla.
 */
public final class McaLink implements Villagers.Bridge {
    private static final Logger LOG = LogUtils.getLogger();

    /** Spawn reasons that change nothing on a vanilla villager, in the order we'd rather claim one. */
    private static final List<MobSpawnType> QUIET = List.of(MobSpawnType.EVENT, MobSpawnType.MOB_SUMMONED, MobSpawnType.TRIGGERED,
            MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY, MobSpawnType.PATROL);

    private static final ResourceLocation SPAWN_BONUS = ResourceLocation.withDefaultNamespace("random_spawn_bonus");

    private boolean broken;
    private Queue<Villager> queue;

    public McaLink() {
        LOG.info("Minecraft Comes Alive found: ordinary villagers will be its humans, story casts stay as written");
    }

    private void fail(String what, Throwable cause) {
        if (!broken) LOG.error("Minecraft Comes Alive link switched off ({}); villagers stay vanilla", what, cause);
        broken = true;
    }

    /**
     * MCA reads the spawn reason from {@code finalizeSpawn}, and turns away any it isn't configured for. Claim the first
     * quiet reason it doesn't handle, with a group that never rolls a baby. This runs even with the link switched off:
     * it needs nothing of MCA's but its config, and without it MCA would swallow a story's whole cast.
     */
    @Override public void holdBack(ServerLevel level, Villager villager) {
        try {
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), quietReason(),
                    new AgeableMob.AgeableMobGroupData(false));
        } catch (RuntimeException twice) {
            // Vanilla throws if a villager is finalized twice, after MCA has already read the reason: they are held back.
            LOG.debug("A villager was already finalized", twice);
        }
    }

    private MobSpawnType quietReason() {
        try {
            List<String> allowed = Config.getInstance().allowedSpawnReasons;
            for (MobSpawnType reason : QUIET) if (!allowed.contains(reason.name().toLowerCase(Locale.ROOT))) return reason;
            LOG.warn("MCA is configured to convert every spawn reason, so a scripted villager can't be held back");
        } catch (Throwable e) {
            fail("reading MCA's config", e);
        }
        return MobSpawnType.EVENT;
    }

    /** A new arrival follows the pack's MCA settings: where MCA wouldn't convert a villager, neither do we. */
    @Override public Villager become(ServerLevel level, Villager villager) {
        if (broken) return null;
        try {
            Config config = Config.getInstance();
            if (!config.overwriteOriginalVillagers
                    || config.villagerDimensionBlacklist.contains(level.dimension().location().toString())
                    || !SpawnQueue.shouldGetConverted(villager)) return null;
        } catch (Throwable e) {
            fail("reading MCA's config", e);
            return null;
        }
        return swap(level, villager);
    }

    /** The swap itself: same UUID, data, job, trades, gift and modifiers, in a body MCA has dressed and named. */
    private Villager swap(ServerLevel level, Villager old) {
        boolean inLevel = level.getEntity(old.getUUID()) == old;
        try {
            if (old instanceof VillagerEntityMCA) return inLevel ? old : level.addFreshEntity(old) ? old : null;
            VillagerEntityMCA body = Gender.getRandom().getVillagerType().create(level);
            if (body == null) return null;
            CompoundTag data = old.saveWithoutId(new CompoundTag());
            data.remove("Passengers");
            keepOwnBases(body, data);
            body.load(data);
            // After the load: this is what gives them a face, clothes, a personality, parents and (if unnamed) a name.
            body.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(body.position())), MobSpawnType.EVENT,
                    new AgeableMob.AgeableMobGroupData(false));
            if (inLevel) old.discard();
            if (level.addFreshEntity(body)) return body;
            LOG.warn("Minecraft Comes Alive's villager {} could not be added", old.getUUID());
            return null;
        } catch (Throwable e) {
            fail("replacing a villager", e);
            return null;
        }
    }

    /**
     * A vanilla villager's saved attributes carry vanilla base values (health 20). Keep their modifiers, a gift's among
     * them, on MCA's bases. The spawn bonus goes: {@code finalizeSpawn} adds its own, and adding it twice throws.
     */
    private static void keepOwnBases(VillagerEntityMCA body, CompoundTag data) {
        ListTag attributes = data.getList("attributes", Tag.TAG_COMPOUND);
        for (int i = attributes.size() - 1; i >= 0; i--) {
            CompoundTag entry = attributes.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            var holder = id == null ? null : BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
            var own = holder == null ? null : body.getAttribute(holder);
            if (own == null) { attributes.remove(i); continue; }
            entry.putDouble("base", own.getBaseValue());
            ListTag modifiers = entry.getList("modifiers", Tag.TAG_COMPOUND);
            modifiers.removeIf(m -> SPAWN_BONUS.toString().equals(((CompoundTag) m).getString("id")));
        }
    }

    @Override public void leaving(Villager villager) {
        if (broken || !(villager instanceof VillagerEntityMCA human)) return;
        try {
            human.getResidency().leaveHome();
        } catch (Throwable e) {
            fail("moving a villager out", e);
        }
    }

    /** Villagers already in the world that {@code /mca-admin} queued: swapped here with everything kept, or left alone. */
    @Override public void tick(MinecraftServer server) {
        if (broken) return;
        try {
            Queue<Villager> waiting = queue();
            if (waiting.isEmpty()) return;
            for (Villager villager : List.copyOf(waiting)) {
                if (!(villager.level() instanceof ServerLevel level) || level.getEntity(villager.getUUID()) != villager) continue;
                waiting.remove(villager);
                if (!Villagers.staysAsIs(villager)) swap(level, villager);    // an admin asked: MCA's settings don't apply
            }
        } catch (Throwable e) {
            fail("reading MCA's spawn queue", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Queue<Villager> queue() throws ReflectiveOperationException {
        if (queue == null) {
            Field field = SpawnQueue.class.getDeclaredField("villagerSpawnQueue");
            field.setAccessible(true);
            queue = (Queue<Villager>) field.get(SpawnQueue.getInstance());
        }
        return queue;
    }

    /**
     * MCA takes a use in {@code interactAt} (its menu, or trading when crouched). Crouched, send it on to the plain use,
     * where carrying is, and MCA's own {@code mobInteract} still trades. Otherwise ask our plain-use handlers first, and
     * let MCA's menu open only if none of them took it.
     */
    @Override public void usedAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (broken || !(event.getTarget() instanceof VillagerEntityMCA human)) return;
        try {
            Player player = event.getEntity();
            if (!answers(human, player, event.getHand())) return;
            if (player.isShiftKeyDown()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.PASS);
                return;
            }
            var plain = new PlayerInteractEvent.EntityInteract(player, event.getHand(), human);
            NeoForge.EVENT_BUS.post(plain);
            if (plain.isCanceled()) {
                event.setCanceled(true);
                event.setCancellationResult(plain.getCancellationResult());
            }
        } catch (Throwable e) {
            fail("passing on a use", e);
        }
    }

    /** The same test MCA's {@code interactAt} makes: is this a use it will answer itself? */
    private static boolean answers(VillagerEntityMCA human, Player player, InteractionHand hand) {
        Entity vehicle = human.getVehicle();
        if (hand != InteractionHand.MAIN_HAND || vehicle != null && vehicle.equals(player)) return false;
        ItemStack stack = player.getItemInHand(hand);
        return !Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                && !stack.is(TagsMCA.Items.VILLAGER_EGGS)
                && human.canInteractWithItemStackInHand(stack)
                && !human.getVillagerBrain().isPanicking();
    }
}
