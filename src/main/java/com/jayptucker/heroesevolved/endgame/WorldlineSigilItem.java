package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.mixin.EndFightAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Re-fights use Minecraft's own four-crystal resurrection and receive a new native Dragon UUID. */
public final class WorldlineSigilItem extends Item {
    public WorldlineSigilItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level world,net.minecraft.world.entity.player.Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(world.isClientSide)return InteractionResultHolder.success(stack);
        if(!(world instanceof ServerLevel level)||!level.dimension().equals(Level.END)||!(player instanceof ServerPlayer p)||!EndBattle.eligible(p)
                ||EndBattle.active()||!EndData.get(p.getServer()).completed||level.getDragonFight()==null)return InteractionResultHolder.fail(stack);
        var access=(EndFightAccess)level.getDragonFight();BlockPos podium=access.heroes$portal();
        if(podium==null||p.position().distanceToSqr(Vec3.atCenterOf(podium))>12*12||access.heroes$respawn()!=null)return InteractionResultHolder.fail(stack);
        List<net.minecraft.world.entity.boss.enderdragon.EndCrystal> made=new ArrayList<>();
        // Validate the entire ritual before creating any entity, including obstruction by a player's fountain build.
        for(Direction direction:Direction.Plane.HORIZONTAL){BlockPos at=podium.above().relative(direction,2);
            if(!level.hasChunkAt(at)||!level.isPositionEntityTicking(at))return InteractionResultHolder.fail(stack);
            if(level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,new net.minecraft.world.phys.AABB(at)).isEmpty()
                    &&(!level.getBlockState(at).isAir()||!level.getBlockState(at.above()).isAir()))return InteractionResultHolder.fail(stack);
        }
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos at=podium.above().relative(direction,2);if(!level.hasChunkAt(at))return InteractionResultHolder.fail(stack);
            if(level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,new net.minecraft.world.phys.AABB(at)).isEmpty()){
                var crystal=EntityType.END_CRYSTAL.create(level);crystal.setPos(Vec3.atBottomCenterOf(at));level.addFreshEntity(crystal);made.add(crystal);
            }
        }
        level.getDragonFight().tryRespawn();
        if(access.heroes$respawn()==null){made.forEach(net.minecraft.world.entity.Entity::discard);return InteractionResultHolder.fail(stack);}
        if(!p.isCreative())stack.shrink(1);return InteractionResultHolder.consume(stack);
    }
}
