package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.assembly.Ballista;
import com.jayptucker.heroesevolved.assembly.Matter;
import com.jayptucker.heroesevolved.flight.Microburst;
import com.jayptucker.heroesevolved.hydro.Leviathan;
import com.jayptucker.heroesevolved.phasing.MountainArmor;
import com.jayptucker.heroesevolved.quake.Epicenter;
import com.jayptucker.heroesevolved.radiation.FissionStar;
import com.jayptucker.heroesevolved.sonic.StandingWave;
import com.jayptucker.heroesevolved.speed.LightningRound;
import com.jayptucker.heroesevolved.amplify.Overdrive;
import com.jayptucker.heroesevolved.invisibility.Bastion;
import com.jayptucker.heroesevolved.mimicry.Chorus;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.telepathy.Puppeteer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Every shot the trailer driver can film, in storyboard order (Act II, the powers). The signatures keep the signature
 * preview's places, foes, camera spots and beats ({@code uitest/SignaturesPreview}), in the shot frame of {@link Stage}:
 * the fighter at the origin facing forward (+Z), which the stage turns to face the sunset.
 */
final class Shots {
    private Shots() {}

    /** Late sunset, and dusk (the sky gone violet, so glowing powers read best). */
    static final long SUNSET = 12450, DUSK = 13300;

    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }

    static List<Shot> all() {
        return List.of(
                // 10. Matter Assembly: the Ballista's blueprint, its parts flying in, built and shooting, folding home.
                new SignatureShot("10-ballista", ModAbilities.MATTER_ASSEMBLY_ID, "matter_assembly_ballista", SUNSET, 74, p -> {
                    Stage.place(p, 0, 0);
                    Matter.fill(p);
                    Stage.aim(p, Stage.at(1, -.5, 5));
                    Stage.puppet(p, EntityType.PILLAGER, 2, 13);
                    Stage.puppet(p, EntityType.VINDICATOR, -3, 14);
                    // Over the fighter's right shoulder and down the line of fire: the fighter, the Ballista and its
                    // targets in one frame, the sunset beyond them (the preview's side view lost the fighter).
                }, p -> Ballista.activate(p, 5), v(-2.8, 2.4, -3.2), v(.8, 1.2, 6), 41, 38, 46, .5, 60, "ballista")
                        .frame(1.0, .9, 56, 50, 16),
                // 11. Firebending at sunset.
                new FirebendingShot(),
                // 12. Superspeed: Lightning Round, four foes visited and home again, all in slow motion.
                new SignatureShot("12-lightning-round", ModAbilities.SUPERSPEED_ID, "superspeed_lightning_round", DUSK, 40, p -> {
                    Stage.place(p, 0, 0);
                    Stage.puppet(p, EntityType.PILLAGER, 0, 6);
                    Stage.puppet(p, EntityType.VINDICATOR, 5, 5);
                    Stage.puppet(p, EntityType.HUSK, -5, 4);
                    Stage.puppet(p, EntityType.PILLAGER, 3, 10);
                }, p -> LightningRound.activate(p, 5), v(7, 4, -1), v(0, .5, 5), 9, 2, 22, .3, -1, null)
                        .frame(1.2, 1.0, 60, 54, 18),
                // 13. Quake: Epicenter, foes in the ring; the faults warn, the plates heave, the ground drops back.
                new SignatureShot("13-epicenter", ModAbilities.QUAKE_ID, "quake_epicenter", SUNSET, 58, p -> {
                    Stage.place(p, 0, 0);
                    Stage.puppet(p, EntityType.PILLAGER, 5, 2);
                    Stage.puppet(p, EntityType.VINDICATOR, -4, 4);
                    Stage.puppet(p, EntityType.HUSK, 1, -6);
                }, p -> Epicenter.activate(p, 5), v(11, 7, -10), v(0, 0, 0), 26, 22, 34, .4, -1, null)
                        .frame(1.0, .85, 60, 54, 24),
                // 14. Hydrokinesis: Leviathan rising, rearing over a marked spot, the bite, the dive.
                new SignatureShot("14-leviathan", ModAbilities.HYDROKINESIS_ID, "hydrokinesis_leviathan", SUNSET, 84, p -> {
                    Stage.place(p, 0, -2);
                    Stage.aim(p, Stage.at(0, -.5, 6));
                    Stage.puppet(p, EntityType.PILLAGER, 3, 10);
                    Stage.puppet(p, EntityType.VINDICATOR, -3, 11);
                }, p -> Leviathan.activate(p, 5), v(11, 4, 3), v(0, 2.2, 8), 26, 22, 32, .4, -1, null)
                        .frame(.85, .72, 58, 50, 22),
                // 15. Density: Mountain Armor, the plates flying on, worn, blasting off.
                new SignatureShot("15-mountain-armor", ModAbilities.PHASING_ID, "phasing_mountain_armor", SUNSET, 49, p -> {
                    Stage.place(p, 0, 0);
                    // Both foes on the fighter's right, off the camera's line (on the left they filled the frame).
                    Stage.puppet(p, EntityType.PILLAGER, -2.5, 3.5);
                    Stage.puppet(p, EntityType.HUSK, -1.5, 5);
                }, p -> MountainArmor.activate(p, 5), v(3.2, 1.9, 3.6), v(0, 1.1, 0), 33, 30, 38, .4, 30, "mountain_armor")
                        .frame(1.2, 1.0, 54, 48, 26),
                // 16. Induced Radioactivity: Fission Star, the core grows, flies, and goes off on a foe.
                new SignatureShot("16-fission-star", ModAbilities.RADIATION_ID, "radiation_fission_star", DUSK, 68, p -> {
                    Stage.place(p, 0, 0);
                    Stage.puppet(p, EntityType.PILLAGER, 0, 10);
                    Stage.puppet(p, EntityType.VINDICATOR, 2, 11.5);
                }, p -> FissionStar.activate(p, 5), v(13, 4, 2), v(0, 1.5, 6), 43, 40, 50, .35, -1, null),
                // 17. Flight: Microburst, the lid over a spot eight blocks out, the column, the outflow.
                new SignatureShot("17-microburst", ModAbilities.FLIGHT_ID, "flight_microburst", SUNSET, 50, p -> {
                    Stage.place(p, 0, -2);
                    Stage.aim(p, Stage.at(0, -.5, 7));
                    Stage.puppet(p, EntityType.PILLAGER, 2, 7);
                    Stage.puppet(p, EntityType.VINDICATOR, -2, 8);
                    Stage.puppet(p, EntityType.HUSK, 6, 12);
                }, p -> Microburst.activate(p, 5), v(15, 9, -4), v(0, 5, 7), 21, 18, 27, .4, -1, null)
                        .frame(1.0, .85, 60, 54, 20),
                // 18. Sonic Resonance: Standing Wave, the column, a beat, the swell, the break.
                new SignatureShot("18-standing-wave", ModAbilities.SONIC_RESONANCE_ID, "sonic_resonance_standing_wave", SUNSET, 87, p -> {
                    Stage.place(p, 0, -1);
                    Stage.aim(p, Stage.at(0, -.5, 7));
                    Stage.puppet(p, EntityType.PILLAGER, 3, 7);
                    Stage.puppet(p, EntityType.VINDICATOR, -2, 9);
                    Stage.puppet(p, EntityType.HUSK, 1, 4);
                }, p -> StandingWave.activate(p, 5), v(10, 4, 1), v(0, 2.5, 7), 73, 70, 80, .4, 70, "standing_wave"),
                // 19. Telepathy: Puppeteer, strings on three foes, then cut.
                new SignatureShot("19-puppeteer", ModAbilities.TELEPATHY_ID, "telepathy_puppeteer", DUSK, 67, p -> {
                    Stage.place(p, 0, 0);
                    Stage.puppet(p, EntityType.PILLAGER, -2.5, 6);
                    Stage.puppet(p, EntityType.VINDICATOR, 2.5, 6.5);
                    Stage.puppet(p, EntityType.HUSK, 0, 9);
                }, p -> Puppeteer.activate(p, 5), v(7, 3, 0), v(0, 1.6, 6), 53, 49, 58, .4, 50, "puppeteer"),
                // 20. Invisibility: Bastion, the dome rising, arrows glancing off it, the burst.
                new SignatureShot("20-bastion", ModAbilities.INVISIBILITY_ID, "invisibility_bastion", SUNSET, 57, p -> {
                    Stage.place(p, 0, 0);
                    Stage.puppet(p, EntityType.PILLAGER, 0, 12);
                    Stage.puppet(p, EntityType.VINDICATOR, 2, 1);
                    Stage.puppet(p, EntityType.HUSK, -7, 3);
                }, p -> Bastion.activate(p, 5) && volley(p), v(12, 5, -9), v(0, 2, 0), 43, 40, 48, .4, 40, "bastion"),
                // 21. Empathic Mimicry: Chorus, the ring of borrowed voices, one voice, all together.
                new SignatureShot("21-chorus", ModAbilities.MIMICRY_ID, "empathic_mimicry_chorus", SUNSET, 66, p -> {
                    Stage.place(p, 0, 0);
                    for (var power : List.of(ModAbilities.PYROKINESIS_ID, ModAbilities.CRYOKINESIS_ID, ModAbilities.ELECTROKINESIS_ID,
                            ModAbilities.TELEKINESIS_ID, ModAbilities.FLIGHT_ID))
                        MimicryService.remember(p, power, 4);
                    Stage.puppet(p, EntityType.PILLAGER, 0, 7);
                    Stage.puppet(p, EntityType.VINDICATOR, 3, 9);
                    Stage.puppet(p, EntityType.HUSK, -3, 8);
                }, p -> Chorus.activate(p, 5), v(4, 2.4, -3), v(0, 1.4, 2), 52, 49, 56, .4, 50, "chorus"),
                // 22. Power Amplification: Overdrive, the web over a friend and the amplifier, then the forks.
                new SignatureShot("22-overdrive", ModAbilities.AMPLIFICATION_ID, "power_amplification_overdrive", DUSK, 56, p -> {
                    Stage.place(p, 0, 0);
                    if (Stage.puppet(p, EntityType.WOLF, 2, 0) instanceof Wolf wolf) wolf.tame(p);
                    Stage.puppet(p, EntityType.PILLAGER, 0, 7);
                    Stage.puppet(p, EntityType.VINDICATOR, 4, 8);
                }, p -> Overdrive.activate(p, 5), v(5, 2.5, -4), v(1, 1, 2), 42, 40, 46, .45, 40, "overdrive"),
                // 23 to 29. The powers without a signature, by their Offense Ultimate, each through its own camera.
                new UltimateShot("23-hailstorm", ModAbilities.CRYOKINESIS_ID, "hailstorm", SUNSET),
                new UltimateShot("24-tesla-spire", ModAbilities.ELECTROKINESIS_ID, "tesla_spire", DUSK),
                new UltimateShot("25-implosion", ModAbilities.TELEKINESIS_ID, "implosion", SUNSET),
                new UltimateShot("26-hour-hand", ModAbilities.TIME_MANIPULATION_ID, "hour_hand", SUNSET),
                new UltimateShot("27-vital-burst", ModAbilities.REGENERATION_ID, "vital_burst", DUSK),
                new UltimateShot("28-many-hands", ModAbilities.SHAPESHIFTING_ID, "shapeshifting_many_hands", SUNSET),
                new UltimateShot("29-crosswave", ModAbilities.VIBE_ID, "vibe_crosswave", DUSK));
    }

    /**
     * Bastion's test: three arrows from the archer (the first actor) flying at the fighter, as the signature preview
     * fires them, so the dome has something to turn.
     */
    private static boolean volley(net.minecraft.server.level.ServerPlayer p) {
        var archer = Stage.ACTORS.getFirst();
        Vec3 flight = Stage.at(0, 0, -1.3).subtract(Stage.feet());
        for (int k = 0; k < 3; k++) {
            Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
            arrow.setOwner(archer);
            arrow.setNoGravity(true);
            arrow.setPos(Stage.at(-2 + 2 * k, 1.6 + k * .7, 9 + k * 4));
            arrow.setDeltaMovement(flight);
            p.serverLevel().addFreshEntity(arrow);
        }
        return true;
    }
}
