package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Mountain Armor (Density, Mountain signature): the earthbender calls the ground up onto herself. Plates of stone tear
 * out of the floor round her feet and fly up onto her body one after another, snapping into place ({@link #ASSEMBLE}
 * ticks: the armor only counts once the drawing has finished closing it). For eight seconds she wears the mountain:
 * +10 armor, +4 toughness, no knockback, a little slower, and every punch lands 3 + tier/2 harder and shoves. When it
 * runs out, or on a second press, the plates blast off her: 5 + tier to every foe within five blocks, thrown back.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class MountainArmor {
    public static final String ID = "mountain_armor";
    public static final int ASSEMBLE = 10, HOLD = 160, RESEND = 20;
    public static final double BURST = 5;
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mountain_armor");

    private MountainArmor() {}

    public static boolean canUse(ServerPlayer p) { return true; }

    public static boolean activate(ServerPlayer p, int tier) {
        Run run = new Run(p, tier);
        if (!Signatures.start(ID, run)) return false;
        run.show();
        ArmPosePayload.show(p, ArmPose.EARTH_RAISE, ASSEMBLE + 4);
        run.level.playSound(null, p.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.6F, .6F);
        run.level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1, 1.2F);
        return true;
    }

    /** Whether this player is wearing the mountain (once it has closed). */
    public static boolean wearing(ServerPlayer p) {
        Run run = Signatures.get(ID, p);
        return run != null && run.age >= ASSEMBLE && !run.burst;
    }

    static final class Run extends Signatures.Run {
        boolean burst, armed;

        Run(ServerPlayer player, int tier) { super(player, tier); }

        void show() {
            SetPiecePayload.sendSeeded(level, Piece.DENSITY_ARMOR, player.position(), player.position().add(player.getLookAngle()), 1, player, 0, age);
        }

        @Override protected boolean tick() {
            if (age == ASSEMBLE) {
                arm(true);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .8F, .6F);
                ArmPosePayload.show(player, ArmPose.EARTH_STANCE, 14);
            }
            if (age % RESEND == 0 && age < HOLD) show();
            if (age >= HOLD) { blast(); return false; }
            return true;
        }

        @Override protected boolean press() {
            if (age < ASSEMBLE) return false;
            blast();
            Signatures.end(ID, player);
            return true;
        }

        @Override protected void cancel() { arm(false); }

        private void arm(boolean on) {
            if (armed == on) return;
            armed = on;
            modify(Attributes.ARMOR, 10, AttributeModifier.Operation.ADD_VALUE, on);
            modify(Attributes.ARMOR_TOUGHNESS, 4, AttributeModifier.Operation.ADD_VALUE, on);
            modify(Attributes.KNOCKBACK_RESISTANCE, 1, AttributeModifier.Operation.ADD_VALUE, on);
            modify(Attributes.MOVEMENT_SPEED, -.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        }

        private void modify(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op, boolean on) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) return;
            instance.removeModifier(MODIFIER);
            if (on) instance.addTransientModifier(new AttributeModifier(MODIFIER, amount, op));
        }

        /** The plates blast off her. */
        void blast() {
            if (burst) return;
            burst = true;
            arm(false);
            float damage = 5 + tier;
            Vec3 at = player.position();
            for (LivingEntity foe : Signatures.foes(player, at.add(0, 1, 0), BURST, true)) {
                Signatures.hit(foe, player.damageSources().explosion(player, player), damage);
                Vec3 out = foe.position().subtract(at).multiply(1, 0, 1);
                Signatures.fling(foe, (out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize()).scale(1.1).add(0, .4, 0));
            }
            SetPiecePayload.send(level, Piece.DENSITY_ARMOR_BURST, at, at.add(player.getLookAngle()), (float) BURST, player);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, .8F);
            level.playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2, .5F);
            CameraShakePayload.sendAround(level, at, BURST * 3, .9F, 10);
            ArmPosePayload.show(player, ArmPose.DENSITY_BURST, 12);
        }
    }

    /** A punch in the mountain lands harder and shoves. */
    @SubscribeEvent public static void punch(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p
                || !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || !wearing(p)) return;
        Run run = Signatures.get(ID, p);
        event.setAmount(event.getAmount() + 3 + run.tier / 2F);
        LivingEntity target = event.getEntity();
        Vec3 out = target.position().subtract(p.position()).multiply(1, 0, 1);
        if (out.lengthSqr() > 1e-4) Signatures.fling(target, out.normalize().scale(.8).add(0, .25, 0));
    }
}
