package com.jayptucker.heroesevolved.compat.jade;

import com.jayptucker.heroesevolved.client.ClientGifted;
import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.client.UnseenClient;
import com.jayptucker.heroesevolved.client.effects.TimeVeilClient;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.cryo.FrostConstructBlock;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlock;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Jade on the client. What Jade names is what the eye sees:
 * <ul>
 *   <li>a hidden or time-veiled body ({@link UnseenClient#concealed}) is looked through, to the block behind it;</li>
 *   <li>a veiled player's still stand-in, where they started, is named as them;</li>
 *   <li>a shapeshifter, a creature wearing a Borrowed Face and a Decoy are named as the body drawn for them
 *   ({@link MorphClient#seenAs}), with no server data, so nothing inside leaks out;</li>
 *   <li>carry anchors, ghost blocks and quake debris are never shown (they can't be aimed at anyway).</li>
 * </ul>
 * And a few lines worth having: a gifted mob's power and rarity, a villager melting down, the dampener's field.
 */
final class JadeClient {
    private static IWailaClientRegistration client;
    /** Set if the look-through or disguise step ever throws: Jade then shows its own target. */
    private static boolean rayBroken;

    private JadeClient() {}

    static void register(IWailaClientRegistration registration) {
        client = registration;
        HeroesJadePlugin.guard("hidden entities", () -> {
            registration.hideTarget(ModEntities.CARRY_ANCHOR.get());
            registration.hideTarget(ModEntities.TEMPORAL_GHOST_BLOCK.get());
            registration.hideTarget(ModEntities.QUAKE_DEBRIS.get());
            registration.hideTarget(ModEntities.RIFT_SKULL.get());
            registration.hideTarget(ModEntities.END_BOLT.get());
        });
        HeroesJadePlugin.guard("disguises", () -> registration.addRayTraceCallback(JadeClient::seen));
        HeroesJadePlugin.guard("gifted", () -> registration.registerEntityComponent(Gifted.INSTANCE, LivingEntity.class));
        HeroesJadePlugin.guard("meltdown", () -> registration.registerEntityComponent(Meltdown.INSTANCE, Villager.class));
        HeroesJadePlugin.guard("dampener", () -> registration.registerBlockComponent(JadeProviders.Dampener.INSTANCE, AbilityDampenerBlock.class));
        HeroesJadePlugin.guard("frost icon", () -> registration.registerBlockIcon(FrostIcon.INSTANCE, FrostConstructBlock.class));
        HeroesJadePlugin.guard("lab progress", () -> registration.registerProgressClient(JadeProviders.LabProgress.INSTANCE));
    }

    /** Jade's ray-trace callback: swaps what it found for what this client actually sees there. */
    private static Accessor<?> seen(HitResult hit, Accessor<?> accessor, Accessor<?> original) {
        if (rayBroken) return accessor;
        try {
            Minecraft mc = Minecraft.getInstance();
            Player viewer = mc.player;
            if (viewer == null || mc.level == null) return accessor;
            Vec3 eye = viewer.getEyePosition(), look = viewer.getViewVector(1);
            double reach = Math.max(viewer.entityInteractionRange(), viewer.blockInteractionRange());
            double found = accessor == null || hit == null || hit.getType() == HitResult.Type.MISS ? reach * reach
                    : eye.distanceToSqr(hit.getLocation());
            // A veiled player's stand-in is what anyone else sees standing there.
            TimeVeilClient.StandHit stand = TimeVeilClient.standOn(eye, eye.add(look.scale(reach)));
            if (stand != null && eye.distanceToSqr(stand.at()) < found)
                return client.entityAccessor().level(mc.level).player(viewer).hit(new EntityHitResult(stand.player(), stand.at()))
                        .entity(stand.player()).serverConnected(false).showDetails(client.isShowDetailsPressed()).build();
            if (!(accessor instanceof EntityAccessor entity)) return accessor;
            Entity target = entity.getEntity();
            if (UnseenClient.concealed(target)) return behind(accessor, viewer, eye, look);
            LivingEntity disguise = MorphClient.seenAs(target);
            if (disguise != null)
                return client.entityAccessor().from(entity).entity(disguise).serverConnected(false).build();
            return accessor;
        } catch (Throwable t) {
            rayBroken = true;
            HeroesJadePlugin.fail("disguises", t);
            return accessor;
        }
    }

    /** The block behind a body this client can't see, as if the body weren't there; null if there's only air. */
    private static Accessor<?> behind(Accessor<?> accessor, Player viewer, Vec3 eye, Vec3 look) {
        var level = viewer.level();
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(viewer.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        if (block.getType() != HitResult.Type.BLOCK) return null;
        BlockState state = level.getBlockState(block.getBlockPos());
        if (state.isAir()) return null;
        return client.blockAccessor().level(level).player(viewer).hit(block).blockState(state)
                .blockEntity(() -> level.getBlockEntity(block.getBlockPos()))
                .serverConnected(accessor.isServerConnected()).showDetails(accessor.showDetails()).build();
    }

    /** A gifted mob's power and rarity, as its sigil shows them: "Pyrokinesis · Rare". */
    private enum Gifted implements IEntityComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = HeroesJadePlugin.id("gifted");

        @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            try {
                ClientGifted.State state = ClientGifted.get(accessor.getEntity());
                if (state == null) return;
                String power = MimicColors.power(state.power());
                Rarity rarity = Rarity.of(state.rarity());
                Component line = Component.empty()
                        .append(power == null ? Component.translatable("jade.heroesevolved.gifted")
                                : Component.translatable("ability.heroesevolved." + power)
                                        .withStyle(s -> s.withColor(TextColor.fromRgb(MimicColors.color(state.power())))))
                        .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("jade.heroesevolved.rarity." + rarity.name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(rarity.color));
                if (state.unbound()) line = line.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("jade.heroesevolved.gifted.unbound").withStyle(ChatFormatting.RED));
                tooltip.add(line);
            } catch (Throwable t) {
                HeroesJadePlugin.fail("gifted tooltip", t);
            }
        }

        @Override public ResourceLocation getUid() { return UID; }
    }

    /** A villager whose power is running away with him: a warning to get clear. */
    private enum Meltdown implements IEntityComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = HeroesJadePlugin.id("meltdown");

        @Override public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            try {
                if (SetPieceClient.boundTo(Piece.VILLAGE_MELTDOWN, accessor.getEntity().getId()) != null)
                    tooltip.add(Component.translatable("jade.heroesevolved.meltdown").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } catch (Throwable t) {
                HeroesJadePlugin.fail("meltdown tooltip", t);
            }
        }

        @Override public ResourceLocation getUid() { return UID; }
    }

    /** Frost constructs have no item, so Jade would show no icon: show ice. */
    private enum FrostIcon implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = HeroesJadePlugin.id("frost_icon");

        @Override public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement current) {
            return IElementHelper.get().item(new ItemStack(Items.PACKED_ICE));
        }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {}

        @Override public ResourceLocation getUid() { return UID; }
    }
}
