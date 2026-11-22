package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.radio.BandChatter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A home of your own in a village or town. Click its notice board: a place that owes you (standing
 * {@link #GIFT_STANDING} or more) builds you one as thanks, anywhere else you can buy one for emeralds; click again to say
 * yes. The order goes on the board after whatever it is building, and goes up like any project. When it's done, every
 * block of it is yours ({@link PlayerBuilt}: the village never wears, rebuilds or touches it), no villager can claim its
 * beds, and its bed is where you wake. One home per player per village. What the player sees: {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Homes {
    /** Standing at which a village builds you a home as thanks. */
    public static final int GIFT_STANDING = 3;
    /** What a home costs, in emeralds: a village's is a small house, a town's a town house. */
    public static final int VILLAGE_PRICE = 24, TOWN_PRICE = 40;
    /** How long an offer stands, in ticks, for the second click. */
    static final int ANSWER = 200;

    private Homes() {}

    /** A home a player owns: the house, its bed, their name. */
    public record Owned(Projects.Home home, BlockPos bed, String name) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.put("home", home.save());
            t.putLong("bed", bed.asLong());
            t.putString("name", name);
            return t;
        }

        static Owned load(CompoundTag t) {
            Projects.Home h = Projects.Home.load(t.getCompound("home"));
            return h == null ? null : new Owned(h, BlockPos.of(t.getLong("bed")), t.getString("name"));
        }
    }

    private record Offer(BlockPos bell, long until, int price) {}

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    /** What a home in {@code v} costs {@code p}: nothing if it owes them, its price otherwise; -1 if it has none to give. */
    public static int price(ServerLevel level, VillageMemory.Village v, ServerPlayer p) {
        Projects.Tier tier = Projects.tier(level, v);
        if (tier == Projects.Tier.HAMLET) return -1;
        if (v.standing(p.getUUID()) >= GIFT_STANDING || v.owes(p.getUUID())) return 0;
        return tier == Projects.Tier.TOWN ? TOWN_PRICE : VILLAGE_PRICE;
    }

    /** {@code p} clicked {@code v}'s board: an offer, or, the second time, the order. */
    public static void ask(ServerLevel level, VillageMemory.Village v, ServerPlayer p) {
        Owned mine = v.owned.get(p.getUUID());
        if (mine != null) {
            tell(p, "Your home in " + v.name + " is by the " + Growth.compass(v.bell, mine.bed()) + " road.");
            return;
        }
        if (v.orders.contains(p.getUUID())) {
            tell(p, v.name + " has your home on its list" + (v.project != null && p.getUUID().equals(v.project.owner) ? " and is building it now." : ", after what it's building now."));
            return;
        }
        int price = price(level, v, p);
        if (price < 0) {
            tell(p, v.name + " is only a hamlet: it has no home to spare yet.");
            return;
        }
        Offer offer = OFFERS.get(p.getUUID());
        long now = level.getGameTime();
        if (offer == null || !offer.bell.equals(v.bell) || now > offer.until || offer.price != price) {
            OFFERS.put(p.getUUID(), new Offer(v.bell, now + ANSWER, price));
            tell(p, v.name + " would build you a home of your own" + (price == 0 ? ", as thanks for what you've done." : ", for " + price + " emeralds.")
                    + " Click the board again to say yes.");
            return;
        }
        OFFERS.remove(p.getUUID());
        if (price > 0 && !pay(p, price)) {
            tell(p, "You'll need " + price + " emeralds.");
            return;
        }
        // Whether it waits its turn is known before the order goes in (an idle board starts on it at once).
        boolean waits = !v.orders.isEmpty() || busy(v);
        order(level, v, p.getUUID(), p.getName().getString());
        p.sendSystemMessage(Component.literal(v.name + " will build your home" + (waits ? " after what it's building now." : ", starting at dawn."))
                .withStyle(ChatFormatting.GREEN));
    }

    /** Takes {@code price} emeralds from {@code p}'s inventory; false (and nothing taken) if they haven't got them. */
    static boolean pay(ServerPlayer p, int price) {
        if (p.getAbilities().instabuild) return true;
        var inv = p.getInventory();
        if (inv.countItem(Items.EMERALD) < price) return false;
        int left = price;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            var stack = inv.getItem(i);
            if (!stack.is(Items.EMERALD)) continue;
            int n = Math.min(left, stack.getCount());
            stack.shrink(n);
            left -= n;
        }
        inv.setChanged();
        return true;
    }

    private static boolean busy(VillageMemory.Village v) {
        return v.project != null && v.project.stage != Projects.Stage.DONE && !(v.project.stage == Projects.Stage.GATHERING && v.project.fraction() <= 0);
    }

    /**
     * Puts a home for {@code owner} on {@code v}'s list; if the board is idle (nothing gathered yet, or done), it goes up
     * now. Admin and tests use this directly.
     */
    public static void order(ServerLevel level, VillageMemory.Village v, UUID owner, String name) {
        if (v.orders.contains(owner) || v.owned.containsKey(owner)) return;
        v.orders.add(owner);
        v.names.put(owner, name);
        VillageMemory.get(level).setDirty();
        if (!busy(v)) post(level, v);
        BandChatter.news(level, v.bell, v.name + "'s building a house for " + name + ". Someone's settling down.", (int) Days.DAY);
    }

    /** Posts the first home on {@code v}'s list as its project, ready to build: nothing to gather. Null if none. */
    static Projects.Project post(ServerLevel level, VillageMemory.Village v) {
        if (v.orders.isEmpty()) return null;
        UUID owner = v.orders.getFirst();
        String name = v.names.getOrDefault(owner, "a newcomer");
        Projects.Tier tier = Projects.tier(level, v);
        ResourceLocation design;
        if (tier == Projects.Tier.TOWN) design = Projects.TOWN_HOUSES.get(level.random.nextInt(Projects.TOWN_HOUSES.size()));
        else {
            String kind = Growth.kind(level, v.bell);
            int count = kind.equals("taiga") ? 5 : 8;
            design = ResourceLocation.withDefaultNamespace("village/" + kind + "/houses/" + kind + "_small_house_" + (level.random.nextInt(count) + 1));
        }
        Projects.Project p = new Projects.Project();
        p.template = design;
        p.title = "A home for " + name;
        p.owner = owner;
        p.posted = Days.today(level);
        p.stage = Projects.Stage.READY;
        v.project = p;
        VillageMemory.get(level).setDirty();
        Projects.start(level, v);
        Projects.signs(level, v);
        return p;
    }

    /**
     * {@code v} finished the home in {@code s} for its project's owner: every block of it is theirs, its beds are taken (no
     * villager can claim them), and its bed is where they wake.
     */
    static void built(ServerLevel level, VillageMemory.Village v, Rebuild.Site s, UUID owner) {
        BlockPos bed = null;
        for (BlockPos p : s.positions) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            PlayerBuilt.mark(level, p);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                claim(level, p);
                if (bed == null) bed = p.immutable();
            }
        }
        if (bed == null) bed = s.center();
        String name = v.names.getOrDefault(owner, "a newcomer");
        v.orders.remove(owner);
        v.owned.put(owner, new Owned(s.home(), bed, name));
        VillageMemory.get(level).setDirty();
        if (level.getPlayerByUUID(owner) instanceof ServerPlayer p) wake(level, v, p);
        else v.waking.add(owner);
        BandChatter.news(level, v.bell, v.name + " has built " + name + " a house by the " + Growth.compass(v.bell, s.center()) + " road.", (int) Days.DAY);
    }

    /** Takes a bed's place from the village: any villager who claimed it lets it go, and the place is held. */
    static void claim(ServerLevel level, BlockPos head) {
        GlobalPos at = GlobalPos.of(level.dimension(), head);
        for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(head).inflate(Villages.RADIUS + 48))) {
            var brain = villager.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.HOME) && at.equals(brain.getMemory(MemoryModuleType.HOME).orElse(null)))
                brain.eraseMemory(MemoryModuleType.HOME);
        }
        var poi = level.getPoiManager();
        if (!poi.exists(head, t -> t.is(PoiTypes.HOME))) return;
        poi.release(head);
        poi.take(t -> t.is(PoiTypes.HOME), (t, p) -> p.equals(head), head, 1);
    }

    /** {@code p} wakes in their home's bed from now on (told once, when it's ready or when they're next about). */
    static void wake(ServerLevel level, VillageMemory.Village v, ServerPlayer p) {
        Owned mine = v.owned.get(p.getUUID());
        if (mine == null) return;
        p.setRespawnPosition(level.dimension(), mine.bed(), 0, true, false);
        p.sendSystemMessage(Component.literal("Your home in " + v.name + " is ready, by the " + Growth.compass(v.bell, mine.bed())
                + " road. Its beds and chests are yours, and you'll wake there.").withStyle(ChatFormatting.GREEN));
    }

    /** Each pass: an owner who was away when their home was finished is told, and wakes there, once they're back. */
    static void tick(ServerLevel level, VillageMemory.Village v) {
        if (v.waking.isEmpty()) return;
        for (UUID id : List.copyOf(v.waking))
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p && p.level() == level) {
                v.waking.remove(id);
                wake(level, v, p);
                VillageMemory.get(level).setDirty();
            }
    }

    /** Whether {@code pos} is one of the signs on a village's board, and whose. */
    static VillageMemory.Village boardOf(ServerLevel level, BlockPos pos) {
        VillageMemory.Village v = VillageMemory.near(level, pos);
        if (v == null || v.board == null) return null;
        for (int i = 0; i < 6; i++) if (v.board.sign(i).equals(pos)) return v;
        return null;
    }

    @SubscribeEvent
    public static void clicked(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        VillageMemory.Village v = boardOf(p.serverLevel(), event.getPos());
        if (v == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ask(p.serverLevel(), v, p);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { OFFERS.clear(); }

    private static void tell(ServerPlayer p, String line) { p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY)); }

    /** One line per owned home, for the village memory command. */
    public static List<String> describe(VillageMemory.Village v) {
        List<String> out = new java.util.ArrayList<>();
        v.owned.forEach((id, o) -> out.add("  " + o.name() + "'s home: " + o.home().template().getPath() + ", bed at " + o.bed().toShortString()));
        if (!v.orders.isEmpty()) out.add("  Homes on order: " + v.orders.size());
        return out;
    }
}
