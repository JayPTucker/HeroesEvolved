package com.jayptucker.heroesevolved.shapeshift;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shapeshifting's three Ultimates; {@code ShapeArt} draws them from these beats. */
public final class ShapeUltimates {
    private ShapeUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(ApexPredator::new, 9);
    public static final Ultimate DEFENSE = Ultimate.defense(LivingFortress::new);
    public static final Ultimate SUPPORT = Ultimate.support(ManyFaces::new);

    private static final String POWER = "shapeshifting";
    private static final ResourceLocation STEADY = ResourceLocation.fromNamespaceAndPath("heroesevolved", "ultimate_fortress");

    // ------------------------------------------------------------------
    // Offense: Apex Predator
    // ------------------------------------------------------------------

    /**
     * Plates close over the shifter and open on a ravager (a wolf where a ravager won't fit). It pounces and pins the
     * foe, mauls it four times, tosses it into the air, leaps after it and brings it down; then the beast bursts into
     * plates and the shifter steps out of them.
     */
    public static final class ApexPredator extends Run {
        public static final int SHIFT = 6, POUNCE = 14, MAUL = 20, MAULS = 4, EVERY = 10, TOSS = 62, LEAP = 72, SLAM = 82, BACK = 92, TICKS = 110;
        public static final double TOSS_HEIGHT = 5;
        private ShapeDecoy beast;

        public ApexPredator(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "shapeshifting_many_hands", foe); }

        @Override protected int length() { return TICKS; }

        /** Where the beast stands at {@code t}: from the shifter's spot in the pounce to the foe, rocking as it mauls. */
        private Vec3 beastAt(int t) {
            double reach = foe.getBbWidth() * .5 + (beast != null ? beast.getBbWidth() * .5 : .6) + .2;
            Vec3 at = base.subtract(facing.scale(reach));
            if (t < POUNCE) return origin.lerp(at, Math.max(0, (t - SHIFT) / (float) (POUNCE - SHIFT)));
            if (t < TOSS) return at.subtract(facing.scale(.35 * Math.abs(Math.sin((t - MAUL) * Math.PI / EVERY))));
            if (t < LEAP) return at;
            if (t < SLAM) return at.add(0, TOSS_HEIGHT * Math.sin((t - LEAP) / (float) (SLAM - LEAP) * Math.PI) * .8, 0);
            return at;
        }

        public static double height(float t) {
            if (t < TOSS) return 0;
            if (t < SLAM - 3) return TOSS_HEIGHT * Math.min(1, (t - TOSS) / 8F);
            return TOSS_HEIGHT * Math.max(0, 1 - (t - (SLAM - 3)) / 3F);
        }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.SHIFT_GUARD, SHIFT);
            piece(Piece.SHIFT_ULT_APEX, origin, base, foe.getBbHeight());
            com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, Piece.SHIFT_PLATES, origin, origin.add(0, player.getBbHeight(), 0), player.getBbWidth(), player);
            sound(player.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1, .7F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.4, 1.8, d, 0, 1.2)
                    .key(POUNCE, d * .4, 4.5, 2, d * .8, 0, 1)
                    .key(MAUL + EVERY * 2, d - 3.5, -4, 2, d - .6, 0, 1)
                    .watch(TOSS + 6, d - 6, 5, 2, foe)
                    .key(SLAM + 2, d - 6.5, 5.5, 3.2, d, 0, .6)
                    .key(TICKS - 14, d - 8, 6, 3.6, d, 0, 1)
                    .until(TICKS - 10));
        }

        @Override protected void beat(int age) {
            if (age == SHIFT) {
                hide();
                beast = ModEntities.SHAPE_DECOY.get().create(level);
                if (beast != null) {
                    beast.setup(ShapeshiftForm.of(EntityType.RAVAGER), player, BACK - SHIFT + 2, null, false);
                    if (!level.noCollision(beast, beast.getDimensions(beast.getPose()).makeBoundingBox(origin)))
                        beast.setup(ShapeshiftForm.of(EntityType.WOLF), player, BACK - SHIFT + 2, null, false);
                    float yaw = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
                    beast.moveTo(origin.x, origin.y, origin.z, yaw, 0);
                    beast.setYBodyRot(yaw);
                    beast.setYHeadRot(yaw);
                    level.addFreshEntity(beast);
                }
                com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, Piece.SHIFT_DECOY_BURST, origin, origin.add(0, 1.8, 0), 0, null);
                sound(origin, SoundEvents.RAVAGER_ROAR, 1.2F, 1);
            }
            if (beast != null && beast.isAlive() && age > SHIFT && age < BACK) {
                Vec3 at = beastAt(age);
                beast.setPos(at.x, at.y, at.z);
            }
            if (age == POUNCE) {
                hit(foe, 2);
                Takedowns.flinch(foe);
                sound(foeAt(), SoundEvents.RAVAGER_ATTACK, 1.2F, 1);
            }
            if (age >= MAUL && age < MAUL + MAULS * EVERY && (age - MAUL) % EVERY == 0) {
                hit(foe, 2.5F);
                Takedowns.flinch(foe);
                sound(foeAt(), SoundEvents.RAVAGER_ATTACK, 1, .9F + (age - MAUL) * .01F);
            }
            if (age >= TOSS) lift(height(age));
            if (age == TOSS) sound(foeAt(), SoundEvents.RAVAGER_ROAR, 1, 1.3F);
            if (age == SLAM) {
                hit(foe, 12);
                for (LivingEntity t : others(base, 5)) { hit(t, 5); shove(t, base, 1.1, .4); }
                sound(base, SoundEvents.RAVAGER_STUNNED, 1.4F, .8F);
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), 1, 1.2F);
                shake(base, 22, .5F, 10);
                release();
            }
            if (age == BACK) {
                Vec3 at = beast != null && beast.isAlive() ? beast.position() : origin;
                if (beast != null) ShapeshiftTricks.burst(beast, null);
                beast = null;
                if (SpeedService.safe(player, at)) StyleHits.blink(player, at);
                show();
                pose(ArmPose.SHIFT_SHED, 10);
            }
        }

        @Override protected void tidy(boolean broken) {
            if (beast != null && !beast.isRemoved()) ShapeshiftTricks.burst(beast, null);
        }
    }

    // ------------------------------------------------------------------
    // Defense: Living Fortress
    // ------------------------------------------------------------------

    /**
     * For twelve seconds the shifter wears armour of their own making, overlapping plates from head to foot: blows land at
     * less than half, nothing knocks them off their feet, and anyone who strikes them cuts themselves on the edges. When
     * it ends the plates burst off in every direction.
     */
    public static final class LivingFortress extends Run {
        public static final int TICKS = 240, INTRO = 22;
        private final Map<UUID, Integer> cut = new HashMap<>();

        public LivingFortress(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "shapeshifting_plate_mail", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.SHIFT_GUARD, 20);
            heroShot(INTRO, .5);
            var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knock != null) knock.addOrUpdateTransientModifier(new AttributeModifier(STEADY, 1, AttributeModifier.Operation.ADD_VALUE));
            plates(0);
            sound(player.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.4F, .6F);
        }

        private void plates(int second) {
            piece(Piece.SHIFT_ULT_FORTRESS, player.position(), player.position().add(0, player.getBbHeight(), 0), player.getBbWidth(), player, second);
        }

        @Override protected void beat(int age) { if (age % 20 == 0 && age < TICKS) plates(age / 20); }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * .45F);
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player && StyleOps.isFoe(player, attacker)
                    && event.getSource().getDirectEntity() == attacker) {
                int last = cut.getOrDefault(attacker.getUUID(), -100);
                if (age - last < 20) return;
                cut.put(attacker.getUUID(), age);
                hit(attacker, 2);
                sound(attacker.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1, 1.4F);
            }
        }

        @Override protected void tidy(boolean broken) {
            var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (knock != null) knock.removeModifier(STEADY);
        }

        @Override protected void finish() {
            Vec3 at = player.position();
            for (LivingEntity t : StyleOps.foes(player, at, 4)) { hit(t, 4); shove(t, at, 1, .3); }
            piece(Piece.SHIFT_ULT_FORTRESS, at, at.add(0, player.getBbHeight(), 0), player.getBbWidth(), player, -1);
            sound(at, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.4F, .5F);
        }
    }

    // ------------------------------------------------------------------
    // Support: Many Faces
    // ------------------------------------------------------------------

    /**
     * Four copies of the shifter peel off them and walk out in four directions; everything hunting the shifter or a friend
     * within twenty blocks goes after a copy instead. For twelve seconds friends within twelve blocks mend and move
     * quicker. When it ends, the copies burst into plates.
     */
    public static final class ManyFaces extends Run {
        public static final int TICKS = 240, INTRO = 22, COPIES = 4;
        public static final double RANGE = 12, DRAW = 20;
        private final List<ShapeDecoy> copies = new ArrayList<>();

        public ManyFaces(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "shapeshifting_masquerade", null); }

        @Override protected int length() { return TICKS; }

        @Override protected void open() {
            cue();
            pose(ArmPose.SHIFT_SHED, 16);
            heroShot(INTRO, .8);
            Vec3 at = player.onGround() ? player.position() : StyleOps.ground(level, player.position());
            for (int i = 0; i < COPIES; i++) {
                double a = Math.atan2(facing.z, facing.x) + Math.PI / 4 + i * Math.PI / 2;
                Vec3 walk = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(.07);
                ShapeDecoy copy = ModEntities.SHAPE_DECOY.get().create(level);
                if (copy == null) continue;
                copy.setup(ShapeshiftForm.of(player.getGameProfile()), player, TICKS + 4, walk, false);
                float yaw = (float) Math.toDegrees(Math.atan2(-walk.x, walk.z));
                copy.moveTo(at.x, at.y, at.z, yaw, 0);
                copy.setYBodyRot(yaw);
                copy.setYHeadRot(yaw);
                level.addFreshEntity(copy);
                copies.add(copy);
                com.jayptucker.heroesevolved.network.SetPiecePayload.send(level, Piece.SHIFT_DECOY, copy.position(), copy.position().add(walk), copy.getBbHeight(), copy);
            }
            for (LivingEntity f : friends(RANGE)) {
                StyleOps.effect(f, MobEffects.REGENERATION, TICKS, 1);
                StyleOps.effect(f, MobEffects.MOVEMENT_SPEED, TICKS, 0);
            }
            sound(player.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.4F, .8F);
        }

        @Override protected void beat(int age) {
            if (age % 10 != 0 || copies.isEmpty()) return;
            List<LivingEntity> friends = friends(RANGE + 4);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DRAW), m -> m.isAlive() && m.getTarget() != null
                    && friends.contains(m.getTarget()))) {
                ShapeDecoy copy = copies.get(Math.floorMod(mob.getId(), copies.size()));
                if (copy.isRemoved()) continue;
                mob.setTarget(copy);
                if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
                    mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, copy);
            }
        }

        @Override protected void tidy(boolean broken) {
            for (ShapeDecoy copy : copies) if (!copy.isRemoved()) ShapeshiftTricks.burst(copy, null);
        }

        public List<ShapeDecoy> copies() { return copies; }
    }
}
