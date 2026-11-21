package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A guardian's favours: about once a day a gifted villager may want something for their work ({@link GiftedWork.Job}),
 * asks players who come near (calm words over their head) and on the radio, and pays whoever brings it: emeralds, a word
 * of thanks, and their village thinking better of them. Saved on the villager. What the player sees:
 * {@code docs/gifted-villagers.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Favours {
    static final String TAG = HeroesEvolved.MOD_ID + "_favour";
    /** A favour lasts two days; a guardian asks at most once a day; players are asked from this close. */
    public static final long LASTS = 48_000, EVERY = 24_000;
    public static final double ASK_REACH = 6;
    private static final Map<Villager, Long> ASKED = new WeakHashMap<>();

    /** What they want, how many, what they pay, and when they asked. */
    public record Favour(Item item, int count, int reward, long posted) {
        /** "eight coal". */
        public String describe() { return (count == 1 ? "a" : words(count)) + " " + name(item, count); }
    }

    private Favours() {}

    /** The favour {@code v} wants, or null (none, or it has lapsed). */
    public static Favour open(Villager v) {
        CompoundTag data = v.getPersistentData();
        if (!data.contains(TAG)) return null;
        CompoundTag t = data.getCompound(TAG);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(t.getString("item")));
        if (item == Items.AIR || t.getInt("count") <= 0) return null;
        Favour f = new Favour(item, t.getInt("count"), t.getInt("reward"), t.getLong("posted"));
        // Two days on the calendar (village/Days), slept nights included.
        long clock = v.level() instanceof ServerLevel level ? Days.clock(level) : f.posted;
        if (clock - f.posted > LASTS) { data.remove(TAG); return null; }
        return f;
    }

    /** {@code v} asks for something their job needs (admin, tests, and once a day on their own). */
    public static Favour ask(Villager v, GiftedWork.Job job) {
        var r = v.getRandom();
        Favour f = switch (job) {
            case SMITH -> r.nextBoolean() ? favour(v, Items.COAL, 8, 4, 6) : favour(v, Items.IRON_INGOT, 4, 4, 6);
            case WATERER, TILLER -> favour(v, Items.BONE_MEAL, 8, 3, 5);
            case ICE_HOUSE -> favour(v, Items.WATER_BUCKET, 1, 2, 4);
            case COURIER -> favour(v, Items.LEATHER, 4, 3, 5);
            case POST -> favour(v, Items.PAPER, 8, 2, 4);
            case HEALER -> favour(v, Items.GLISTERING_MELON_SLICE, 2, 4, 6);
            case KEEPER -> favour(v, Items.COPPER_INGOT, 4, 3, 5);
            case HARVESTER -> favour(v, Items.WHEAT_SEEDS, 8, 2, 3);
            case BELL -> favour(v, Items.AMETHYST_SHARD, 4, 4, 6);
            case LOOKOUT -> favour(v, Items.ARROW, 16, 3, 5);
        };
        CompoundTag t = new CompoundTag();
        t.putString("item", BuiltInRegistries.ITEM.getKey(f.item).toString());
        t.putInt("count", f.count);
        t.putInt("reward", f.reward);
        t.putLong("posted", f.posted);
        t.putLong("day", v.level().getDayTime() / 24_000);
        v.getPersistentData().put(TAG, t);
        // The radio talks about it for as long as it is open (radio/BandChatter asks open()), not as news.
        ASKED.remove(v);
        return f;
    }

    private static Favour favour(Villager v, Item item, int count, int least, int most) {
        long clock = v.level() instanceof ServerLevel level ? Days.clock(level) : v.level().getDayTime();
        return new Favour(item, count, least + v.getRandom().nextInt(most - least + 1), clock);
    }

    /** From {@link GiftedWork#tick} every five seconds: ask once a day (sometimes), and ask whoever comes near. */
    static void tick(ServerLevel level, Villager v, GiftedWork.Job job) {
        Favour f = open(v);
        long day = level.getDayTime() / 24_000, t = level.getDayTime() % 24_000;
        if (f == null) {
            CompoundTag data = v.getPersistentData();
            long last = data.contains(TAG + "_day") ? data.getLong(TAG + "_day") : -1;
            if (last != day && t > 2_000 && t < 10_000) {
                data.putLong(TAG + "_day", day);
                if (level.random.nextFloat() < 0.5F) ask(v, job);
            }
            return;
        }
        long now = level.getGameTime();
        if (now - ASKED.getOrDefault(v, -1000L) < 600) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(v) > ASK_REACH * ASK_REACH) continue;
            ASKED.put(v, now);
            Shouts.talk(level, v, "Could you bring me " + f.describe() + "? I'll pay.", 100);
            return;
        }
    }

    /** Holding what they asked for, a player uses them: it's done. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Villager v)
                || !(event.getEntity() instanceof ServerPlayer p) || !GiftedMobs.isGood(v)) return;
        Favour f = open(v);
        ItemStack held = p.getMainHandItem();
        if (f == null || !held.is(f.item) || held.getCount() < f.count) return;
        if (deliver(p, v)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** {@code p} brings {@code v} what they asked for (from their main hand): paid, thanked, remembered. */
    public static boolean deliver(ServerPlayer p, Villager v) {
        Favour f = open(v);
        ItemStack held = p.getMainHandItem();
        if (f == null || !held.is(f.item) || held.getCount() < f.count || !(v.level() instanceof ServerLevel level)) return false;
        ItemStack back = held.getCount() == f.count && held.is(Items.WATER_BUCKET) ? new ItemStack(Items.BUCKET) : ItemStack.EMPTY;
        held.shrink(f.count);
        if (!back.isEmpty() && !p.getInventory().add(back)) p.drop(back, false);
        ItemStack pay = new ItemStack(Items.EMERALD, f.reward);
        if (!p.getInventory().add(pay)) p.drop(pay, false);
        v.getPersistentData().remove(TAG);
        String who = name(v, GiftedWork.job(v));
        Shouts.talk(level, v, "Thank you, " + p.getName().getString() + "!", 80);
        level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1, 1);
        p.sendSystemMessage(Component.literal(who + " pays you " + f.reward + " emerald" + (f.reward == 1 ? "" : "s") + ".")
                .withStyle(ChatFormatting.GREEN));
        v.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 10);
        VillageMemory.thanked(level, v.blockPosition(), p, 1);
        return true;
    }

    /** "Hollis the Ashen", or "Ashford's smith". */
    static String name(Villager v, GiftedWork.Job job) {
        if (v.hasCustomName()) return v.getCustomName().getString();
        String place = v.level() instanceof ServerLevel level ? VillageMemory.name(level, v.blockPosition()) : null;
        return (place == null ? "The" : place + "'s") + " " + (job == null ? "guardian" : job.title);
    }

    private static final String[] WORDS = {"no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen"};

    static String words(int n) { return n >= 0 && n < WORDS.length ? WORDS[n] : String.valueOf(n); }

    /** An item's name as a request says it: "coal", "iron ingots", "a water bucket". */
    static String name(Item item, int count) {
        String n = new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
        if (count == 1) return n;
        if (n.endsWith("s") || n.equals("coal") || n.endsWith("meal") || n.endsWith("seeds") || n.endsWith("paper") || n.endsWith("leather")) return n;
        return n + "s";
    }
}
