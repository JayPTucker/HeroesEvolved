package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Ultimates.Run;
import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.styles.StyleOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Matter Assembly's three Ultimates, built the Matter Assembly way: the plan is drawn first, then the parts fly from
 * her hand and snap in at the ticks the server builds them (the beats here are the schedule both read), and when it
 * is over every fragment flies home. Each part spends one Matter while she has it (the orbit thins as it is built) and
 * gives it back at the end; with none left the parts are pulled out of the air, so it never refuses.
 */
public final class AssemblyUltimates {
    private AssemblyUltimates() {}

    public static final Ultimate OFFENSE = Ultimate.offense(GrandDesign::new, 10);
    public static final Ultimate DEFENSE = Ultimate.defense(Citadel::new);
    public static final Ultimate SUPPORT = Ultimate.support(FieldHospital::new);

    private static final String POWER = "matter_assembly";

    // ------------------------------------------------------------------
    // Offense: Grand Design
    // ------------------------------------------------------------------

    /**
     * She frames the foe between her hands: the plan of a cage appears round it, and its twelve bars fly in from her
     * hand and lock shut one by one. Over it, the plan of a great hammer; its six parts fly up, snap in with a flare of
     * seams, and a sheen runs down it. A flick of her hand brings it down: cage and hammer shatter together, the shards
     * ricochet into everyone near, and every fragment flies home.
     */
    public static final class GrandDesign extends Run {
        public static final int CAGE_PLAN = 2, BAR = 12, BARS = 12, HAMMER_PLAN = 34, PART = 40, PARTS = 6, SHEEN = 58,
                SWING = 62, IMPACT = 68, HOME = 72, TICKS = 120;
        public static final double HAMMER_HEIGHT = 5.5;
        private int spent;

        public GrandDesign(ServerPlayer p, int tier, LivingEntity foe) { super(p, tier, POWER, "forge_cannon", foe); }

        @Override protected int length() { return TICKS; }

        /** The tick cage bar {@code i} locks in. */
        public static int bar(int i) { return BAR + i * 2; }

        /** The tick hammer part {@code i} snaps in. */
        public static int part(int i) { return PART + i * 3; }

        /** The hammer's swing: 0 raised, 1 down on the cage. */
        public static float swing(float t) { return t < SWING ? 0 : Mth.clamp((t - SWING) / (IMPACT - SWING), 0, 1); }

        @Override protected void open() {
            face(foeCentre());
            cue();
            pose(ArmPose.MATTER_FORM, BAR + BARS * 2);
            piece(Piece.MATTER_ULT_DESIGN, origin, base, foe.getBbHeight());
            sound(foeCentre(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, .8F);
            double d = Math.sqrt(base.subtract(origin).horizontalDistanceSqr());
            cinema(cam()
                    .key(0, -2.6, 2.2, 1.8, d, 0, 1.2)
                    .key(BAR + 12, d - 4, 3.6, 2.2, d, 0, 1.4)
                    .key(HAMMER_PLAN + 6, d - 7, -4.5, 4, d, 0, HAMMER_HEIGHT - 1)
                    .key(SHEEN, d - 8, -5.5, 5, d, 0, HAMMER_HEIGHT * .7)
                    .key(IMPACT + 2, d - 7, 5, 3.5, d, 0, .8)
                    .key(TICKS - 14, -3, 3, 2.4, d * .5, 0, 1.2)
                    .until(TICKS - 10));
        }

        private void build() {
            if (Matter.take(player)) spent++;
        }

        @Override protected void beat(int age) {
            if (age <= BAR) lift(.4 * age / BAR);
            for (int i = 0; i < BARS; i++) if (age == bar(i)) {
                build();
                sound(foeCentre(), SoundEvents.AMETHYST_CLUSTER_PLACE, .9F, .8F + i * .05F);
            }
            if (age == bar(BARS - 1) + 1) { Takedowns.flinch(foe); hit(foe, 2); }
            if (age == HAMMER_PLAN) { pose(ArmPose.MATTER_RAISE, SWING - HAMMER_PLAN); sound(base.add(0, HAMMER_HEIGHT, 0), SoundEvents.BEACON_ACTIVATE, 1, 1.4F); }
            for (int i = 0; i < PARTS; i++) if (age == part(i)) {
                build();
                sound(base.add(0, HAMMER_HEIGHT, 0), SoundEvents.AMETHYST_BLOCK_PLACE, 1.4F, .5F + i * .1F);
            }
            if (age == SHEEN) sound(base.add(0, HAMMER_HEIGHT, 0), SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 1.2F);
            if (age == SWING) { pose(ArmPose.MATTER_FLICK, 10); sound(base, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2F, .5F); }
            if (age == IMPACT) {
                hit(foe, 18);
                for (LivingEntity t : others(base, 6)) { hit(t, 6); shove(t, base, 1.1, .4); }
                sound(base, SoundEvents.AMETHYST_CLUSTER_BREAK, 2, .5F);
                sound(base, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.1F);
                shake(base, 24, .55F, 10);
                release();
            }
            if (age == HOME) {
                pose(ArmPose.MATTER_CALL, 20);
                Matter.returned(player, base.add(0, 1, 0), spent);
                spent = 0;
            }
        }

        @Override protected void tidy(boolean broken) { if (spent > 0) Matter.returned(player, base.add(0, 1, 0), spent); spent = 0; }
    }

    // ------------------------------------------------------------------
    // Defense: Citadel
    // ------------------------------------------------------------------

    /**
     * The plan of a twelve-panel shell rises round her, and its panels assemble from her hand one at a time: each one
     * stops every shot from its own side the moment it is in. Once it is whole, blows that reach her are halved. After
     * twelve seconds the panels come apart and fly home.
     */
    public static final class Citadel extends Run {
        public static final int TICKS = 240, INTRO = 22, FIRST = 6, EVERY = 3, PANELS = 12;
        public static final double RADIUS = 2.2;
        private int spent, built;

        public Citadel(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "bulwark_lattice", null); }

        @Override protected int length() { return TICKS; }

        /** The tick panel {@code i} locks in. */
        public static int panel(int i) { return FIRST + i * EVERY; }

        /** Which of the six sides a panel stands on (two rows, the lower first). */
        public static double angle(int i) { return (i % 6) * Math.PI / 3 + (i >= 6 ? Math.PI / 6 : 0); }

        @Override protected void open() {
            cue();
            pose(ArmPose.MATTER_FORM, INTRO);
            heroShot(INTRO, .8);
            piece(Piece.MATTER_ULT_CITADEL, player.position(), player.position(), (float) RADIUS, player, 0);
        }

        @Override protected void beat(int age) {
            for (int i = 0; i < PANELS; i++) if (age == panel(i)) {
                if (Matter.take(player)) spent++;
                built++;
                sound(player.position(), SoundEvents.AMETHYST_BLOCK_PLACE, 1.2F, .7F + i * .04F);
            }
            if (age % 20 == 0 && age < TICKS) piece(Piece.MATTER_ULT_CITADEL, player.position(), player.position(), (float) RADIUS, player, age / 20);
            Vec3 body = player.getBoundingBox().getCenter();
            for (Projectile shot : shots(body, RADIUS + 1.2)) {
                Vec3 d = shot.position().subtract(player.position());
                double a = Mth.positiveModulo(Math.atan2(d.z, d.x), Math.PI * 2);
                // The panel on the shot's side: the lower ring below chest height, the upper (turned half a side) above.
                int lower = Math.floorMod((int) Math.round(a / (Math.PI / 3)), 6);
                int upper = 6 + Math.floorMod((int) Math.round((a - Math.PI / 6) / (Math.PI / 3)), 6);
                int side = d.y > 1.2 ? upper : lower;
                if (side >= built) continue;
                piece(Piece.MATTER_BOUNCE, shot.position(), body, 1, null, 0);
                shot.discard();
                sound(shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, .9F, 1.4F);
            }
        }

        @Override protected void incoming(LivingIncomingDamageEvent event, LivingEntity victim) {
            if (victim != player || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            event.setAmount(event.getAmount() * (1 - .5F * built / PANELS));
        }

        @Override protected void tidy(boolean broken) {
            piece(Piece.MATTER_ULT_CITADEL, player.position(), player.position(), (float) RADIUS, player, -1);
            if (spent > 0) Matter.returned(player, player.position().add(0, 1, 0), spent);
            spent = 0;
        }
    }

    // ------------------------------------------------------------------
    // Support: Field Hospital
    // ------------------------------------------------------------------

    /**
     * Four pillars and then a roof of eight crystal panels assemble over everyone within four blocks, plan first, a
     * sheen across the roof when it is done. For fourteen seconds friends under it mend, are cleansed and given extra
     * hearts once, and shots that fall on the roof stop there. Then it comes apart and the fragments fly home.
     */
    public static final class FieldHospital extends Run {
        public static final int TICKS = 280, INTRO = 24, PILLAR = 4, PILLARS = 4, ROOF = 18, PANELS = 8, SHEEN = 44;
        public static final double RADIUS = 4, HEIGHT = 3.2;
        private Vec3 centre;
        private int spent;
        private final Set<UUID> tended = new HashSet<>();

        public FieldHospital(ServerPlayer p, int tier, LivingEntity unused) { super(p, tier, POWER, "mending_lattice", null); }

        @Override protected int length() { return TICKS; }

        public static int pillar(int i) { return PILLAR + i * 3; }

        public static int roof(int i) { return ROOF + i * 3; }

        @Override protected void open() {
            centre = player.position();
            cue();
            pose(ArmPose.MATTER_RAISE, INTRO);
            heroShot(INTRO, 2);
            piece(Piece.MATTER_ULT_HOSPITAL, centre, centre, (float) RADIUS, player, 0);
        }

        @Override protected void beat(int age) {
            for (int i = 0; i < PILLARS; i++) if (age == pillar(i)) { if (Matter.take(player)) spent++; sound(centre, SoundEvents.AMETHYST_BLOCK_PLACE, 1.2F, .6F); }
            for (int i = 0; i < PANELS; i++) if (age == roof(i)) { if (Matter.take(player)) spent++; sound(centre.add(0, HEIGHT, 0), SoundEvents.AMETHYST_CLUSTER_PLACE, 1, .8F + i * .05F); }
            if (age == SHEEN) sound(centre.add(0, HEIGHT, 0), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.3F);
            if (age % 20 == 0 && age < TICKS) {
                piece(Piece.MATTER_ULT_HOSPITAL, centre, centre, (float) RADIUS, player, age / 20);
                for (LivingEntity f : StyleOps.friends(player, centre, RADIUS + .5)) {
                    StyleOps.heal(f, 1.2F);
                    if (tended.add(f.getUUID())) {
                        StyleOps.cleanse(f);
                        StyleOps.effect(f, MobEffects.ABSORPTION, TICKS, 1);
                    }
                }
            }
            if (age >= roof(PANELS - 1)) for (Projectile shot : shots(centre.add(0, HEIGHT, 0), RADIUS + 1)) {
                if (shot.position().y < centre.y + HEIGHT - .6 || shot.getDeltaMovement().y > 0) continue;
                piece(Piece.MATTER_BOUNCE, shot.position(), centre.add(0, HEIGHT + 1, 0), 1, null, 0);
                shot.discard();
            }
        }

        @Override protected void tidy(boolean broken) {
            piece(Piece.MATTER_ULT_HOSPITAL, centre, centre, (float) RADIUS, player, -1);
            if (spent > 0) Matter.returned(player, centre.add(0, HEIGHT, 0), spent);
            spent = 0;
        }

        public Vec3 centre() { return centre; }
    }
}
