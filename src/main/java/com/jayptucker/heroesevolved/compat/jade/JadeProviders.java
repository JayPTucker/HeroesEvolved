package com.jayptucker.heroesevolved.compat.jade;

import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlock;
import com.jayptucker.heroesevolved.dampener.AbilityDampenerBlockEntity;
import com.jayptucker.heroesevolved.dampener.DampenerGeometry;
import com.jayptucker.heroesevolved.formula.CentrifugeBlockEntity;
import com.jayptucker.heroesevolved.formula.SynthesisStationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ProgressView;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

/** The providers both sides know about: the server fills them in, the client draws them. */
final class JadeProviders {
    private JadeProviders() {}

    /**
     * The Ability Dampener: on or off (its block state, which every client has), and the radius and upkeep (the block
     * entity's, sent by the server). Jade draws the Forge Energy bar itself from the block's energy capability.
     */
    enum Dampener implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = HeroesJadePlugin.id("dampener");

        @Override public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            try {
                if (!(accessor.getBlockEntity() instanceof AbilityDampenerBlockEntity dampener)) return;
                data.putInt("radius", dampener.radius());
                data.putBoolean("enabled", dampener.enabled());
            } catch (Throwable t) {
                HeroesJadePlugin.fail("dampener data", t);
            }
        }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {
                CompoundTag data = accessor.getServerData();
                boolean on = accessor.getBlockState().hasProperty(AbilityDampenerBlock.ACTIVE)
                        && accessor.getBlockState().getValue(AbilityDampenerBlock.ACTIVE);
                if (!data.contains("radius")) {
                    tooltip.add(Component.translatable(on ? "jade.heroesevolved.dampener.live" : "jade.heroesevolved.dampener.idle")
                            .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                    return;
                }
                int radius = data.getInt("radius");
                String state = on ? "live" : data.getBoolean("enabled") ? "unpowered" : "idle";
                tooltip.add(Component.translatable("jade.heroesevolved.dampener." + state)
                        .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                tooltip.add(Component.translatable("jade.heroesevolved.dampener.field", radius, DampenerGeometry.upkeep(radius)));
            } catch (Throwable t) {
                HeroesJadePlugin.fail("dampener tooltip", t);
            }
        }

        @Override public ResourceLocation getUid() { return UID; }
    }

    /** The Centrifuge's spin and the Synthesis Station's brew, as Jade's progress bar. */
    enum LabProgress implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, ProgressView> {
        INSTANCE;

        private static final ResourceLocation UID = HeroesJadePlugin.id("lab_progress");

        @Override public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            try {
                Object target = accessor instanceof BlockAccessor block ? block.getBlockEntity() : accessor.getTarget();
                float done = target instanceof CentrifugeBlockEntity centrifuge ? centrifuge.spun()
                        : target instanceof SynthesisStationBlockEntity station ? station.brewed() : 0;
                return done <= 0 ? List.of() : List.of(new ViewGroup<>(List.of(ProgressView.create(done))));
            } catch (Throwable t) {
                HeroesJadePlugin.fail("lab progress", t);
                return List.of();
            }
        }

        @Override public List<ClientViewGroup<ProgressView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
            return ClientViewGroup.map(groups, ProgressView::read, (group, view) -> {});
        }

        @Override public ResourceLocation getUid() { return UID; }
    }
}
