package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;

/** Which drawing each set piece uses. */
final class PieceArt {
    private PieceArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case END_BATTLE_FIELD, END_BATTLE_WARNING, END_BATTLE_CHANNEL, END_BATTLE_CANCEL,
                    END_BATTLE_GROUNDED, END_BATTLE_PHASE, END_BATTLE_ABSORB, END_BATTLE_RELEASE,
                    END_BATTLE_REFUGE, END_BATTLE_BACKFIRE, END_BATTLE_STRIKE, END_BATTLE_NOVA,
                    END_BATTLE_IMPACT, END_BATTLE_WAVE, END_BATTLE_DASH, END_BATTLE_BOLT, END_BATTLE_POOL -> EndBattleArt.draw(c,a,t);
            case END_SEAL_WARNING, END_SEAL_KEY, END_SEAL_CRACK, END_SEAL_SHATTER, END_SEAL_SPARED -> SealArt.draw(c, a, t);
            case KINETIC_SEND, KINETIC_SHOVE, KINETIC_GRIP, KINETIC_THROW, KINETIC_IMPACT, KINETIC_CATCH, KINETIC_SHOT,
                    KINETIC_RETURN, KINETIC_PLUCK, KINETIC_FLING, KINETIC_STORM, KINETIC_SLAM, KINETIC_PIN, KINETIC_BECKON,
                    KINETIC_ROUND_UP, KINETIC_POP, KINETIC_STEP, KINETIC_LEVITATE, KINETIC_CUSHION, KINETIC_SKYFALL,
                    KINETIC_CLENCH, KINETIC_WARD, KINETIC_GATHER -> KineticArt.draw(c, a, t);
            case WITHER_FORM, WITHER_BREACH, WITHER_AURA, WITHER_WARNING, WITHER_PULSE, WITHER_IMPACT,
                    WITHER_NOVA, WITHER_BACKFIRE, WITHER_PHASE, WITHER_DEATH, WITHER_CANCEL,
                    WITHER_FIRE, WITHER_WAKE, WITHER_ARC, WITHER_HEAD_WARN, WITHER_MUZZLE,
                    WITHER_CRACK, WITHER_SPIRES, WITHER_DETONATE, WITHER_TEAR, WITHER_OPEN, WITHER_SPEW,
                    WITHER_KEY, WITHER_HEART_BREAK, WITHER_WARD_BREAK, WITHER_WARD_HIT, WITHER_MARK, WITHER_MEND,
                    WITHER_SPARED, WITHER_STEP, WITHER_CAGE, WITHER_WELL -> WitherArt.draw(c,a,t);
            case END_PORTAL_DORMANT, END_PORTAL_CHANNEL, END_PORTAL_IMPRINT, END_PORTAL_OPEN, END_PORTAL_CANCEL -> PortalArt.draw(c, a, t);
            case OVEREXERTION -> OverexertionArt.draw(c, a, t);
            case FLIGHT_RELEASE, FLIGHT_SKYBURST, FLIGHT_IMPACT, FLIGHT_RESCUE, FLIGHT_SHELTER,
                    FLIGHT_CURRENT, FLIGHT_THERMAL_MARK, FLIGHT_THERMAL_RISE, FLIGHT_INTERCEPT,
                    TEMPEST_DRILL, GALE_WALL, FORMATION_FLIGHT -> FlightArt.draw(c, a, t);
            case PYRO_FORGE, PYRO_SPEAR, PYRO_BREATH, PYRO_FIRESTORM_PRIME, PYRO_FIRESTORM,
                    PYRO_COLLAPSE, PYRO_BURST, PYRO_SUN_CHARGE, PYRO_SUN_DROP, PYRO_LASH, PYRO_MANTLE,
                    PYRO_CAUTERIZE, PYRO_MINE_ARM, PYRO_MINE_WARN, PYRO_LANCE, PYRO_SHEPHERD, PYRO_TAKEOFF, PYRO_PHOENIX, PYRO_WAKE,
                    SUNFALL, EMBER_SHROUD, HEARTHFIRE, PYRO_JAB, PYRO_KICK, PYRO_SWEEP_KICK, PYRO_JET_STEP, PYRO_FLIP,
                    PYRO_COUNTER, FINISH_DRAGON, FINISH_PHOENIX, FINISH_HEARTH -> PyroArt.draw(c, a, t);
            case ELECTRO_DRAW, ELECTRO_ARC, ELECTRO_CURRENT, ELECTRO_ORB, ELECTRO_PRIME, ELECTRO_WARN,
                    ELECTRO_DEVICE, ELECTRO_JAM, ELECTRO_HAUL, ELECTRO_REEL,
                    ELECTRO_STRIKE, ELECTRO_PULSE, ELECTRO_RIDE, ELECTRO_SCAR, ELECTRO_COIL, ELECTRO_SIPHON,
                    ELECTRO_BLACKOUT, ELECTRO_AURA, ELECTRO_FIELD, ELECTRO_SCREEN,
                    TESLA_SPIRE, TESLA_ZAP, FARADAY_CAGE, JUMPSTART, ELECTRO_LANCE, ELECTRO_THREAD,
                    ELECTRO_OVERCHARGE, ELECTRO_SINK, ELECTRO_SINK_BURST, ELECTRO_ANCHOR, ELECTRO_WIRE,
                    ELECTRO_SPARK, ELECTRO_CLAP, ELECTRO_GROUND_ARC, ELECTRO_ARC_STEP, ELECTRO_DECOY, ELECTRO_GROUND_OUT,
                    ELECTRO_LOADED -> ElectroArt.draw(c, a, t);
            case MIMIC_ATTUNE, MIMIC_IMPRINT, MIMIC_SHIFT -> MimicArt.draw(c, a, t);
            case SONIC_CRY, SONIC_SCREAM, SONIC_NOTE, SONIC_NOTE_BURST, SONIC_WALL, SONIC_WALL_PUSH, SONIC_VAULT,
                    SONIC_DIVE, SONIC_LANDING, SONIC_GATHER, SONIC_CRESCENDO, SONIC_SHRIEK, SONIC_RING, SONIC_SHATTER,
                    SONIC_DISSONANCE, SONIC_ECHO, SONIC_GUARD, SONIC_QUIET,
                    RESONANCE_CASCADE, SILENCE_DOME, BATTLE_HYMN -> SonicArt.draw(c, a, t);
            case QUAKE_DUST, QUAKE_SHATTER, QUAKE_LANCE, QUAKE_SHELL, QUAKE_BRACE, QUAKE_DASH, QUAKE_SEAM, QUAKE_TRACE ->
                    QuakeArt.draw(c, a, t);
            case REGEN_STREAM, REGEN_FAN, REGEN_IMPACT, REGEN_PAYBACK, REGEN_FEIGN, REGEN_CPR -> RegenArt.draw(c, a, t);
            case TELE_COMMAND, TELE_LISTEN, TELE_VOICE, TELE_WEAVE, TELE_PERSUADE, TELE_WON, TELE_RECALL, TELE_MEMORY,
                    TELE_ECHO, TELE_LOST, TELE_FRIGHT, TELE_HIVE_LINK, TELE_HALO, TELE_PROJECT,
                    PSYCHIC_STORM, BLIND_SPOT, HIVE_MIND, FINISH_LIGHTS -> TelepathyArt.draw(c, a, t);
            case VIBE_GATHER, VIBE_WAVE, VIBE_CROSSWAVE, VIBE_HIT, VIBE_BREACH_SEED, VIBE_BREACH_OPEN,
                    VIBE_BREACH_ARRIVE, VIBE_BREACH_CLOSE, VIBE_TRANSIT, VIBE_TOUCH, VIBE_ECHO, VIBE_GUARD,
                    VIBE_GUARD_CLOSE, VIBE_LOCK_WARN, VIBE_LOCK, VIBE_AWAKEN, VIBE_BEAST, VIBE_KICK, VIBE_SEAM,
                    VIBE_BREACH_CHANNEL, VIBE_PROJECT, VIBE_HOP, VIBE_STREAM,
                    VIBE_RIFT_ZONE, VIBE_RIFT_SKY, VIBE_RIFT_FALL -> VibeArt.draw(c, a, t);
            case TIME_STASIS, TIME_RELEASE, TIME_ANCHOR, TIME_SECOND, TIME_SEAL, TIME_SEAL_BREAK, TIME_CUT, TIME_SNAP,
                    TIME_MARK, TIME_FORWARD, FINISH_LOOP -> TimeArt.draw(c, a, t);
            case DENSITY_HARDEN, DENSITY_REBOUND, DENSITY_PILE, DENSITY_SINK_MARK, DENSITY_SINKHOLE, DENSITY_ENTOMB, DENSITY_UPROOT,
                    DENSITY_GRASP -> DensityArt.draw(c, a, t);
            case EARTH_STANCE, EARTH_LIFT, EARTH_THROW, EARTH_SHATTER, EARTH_SHACKLE, EARTH_CRUSH, EARTH_TUNNEL, EARTH_PILLAR,
                    EARTH_WALL, EARTH_SHOVE, EARTH_MOUNTAIN_MARK, EARTH_MOUNTAIN, EARTH_MOUNTAIN_SLAM, EARTH_GAUNTLET, EARTH_AVALANCHE,
                    EARTH_TENT, EARTH_SURF, EARTH_STOMP, EARTH_LISTEN, EARTH_CLAW, EARTH_SLAB, EARTH_SPIKES,
                    EARTH_SLIDE -> EarthArt.draw(c, a, t);
            case AMP_ZAP, AMP_SURGE, AMP_OVERCHARGE, AMP_BOLT, AMP_BEAM, AMP_BURST, AMP_SHOCK, AMP_JUMP, AMP_GATHER, AMP_WAVE,
                    AMP_THREAD, AMP_BURNOUT, AMP_WILD, AMP_DOOR, AMP_DOME, AMP_STORM, AMP_AWAKEN, AMP_BACKLASH,
                    FINISH_JUICE, FINISH_RED_WAVE, FINISH_REVERSE -> AmpArt.draw(c, a, t);
            case GROUND_ZERO -> ElementArt.groundZero(c, a, t);
            case CONTAINMENT_DOME -> ElementArt.containment(c, a, t);
            case REACTOR_HEARTH -> ElementArt.reactor(c, a, t);
            case HOUR_HAND -> MotionArt.hourHand(c, a, t);
            case TIME_LOOP -> MotionArt.timeLoop(c, a, t);
            case BORROWED_TIME -> MotionArt.borrowedTime(c, a, t);
            case HAILSTORM -> ElementArt.hailstorm(c, a, t);
            case CRYSTAL_BASTION -> ElementArt.bastion(c, a, t);
            case AURORA_VEIL -> ElementArt.aurora(c, a, t);
            case FISSURE_MAW -> ElementArt.fissure(c, a, t);
            case STONE_RAMPART -> ElementArt.rampart(c, a, t);
            case BEDROCK_STANCE -> ElementArt.bedrock(c, a, t);
            case VITAL_BURST -> MindArt.vitalBurst(c, a, t);
            case UNDYING -> MindArt.undying(c, a, t);
            case LIFEBLOOM -> MindArt.lifebloom(c, a, t);
            case LIGHTNING_FLURRY -> MotionArt.flurry(c, a, t);
            case RELAY_RUN -> MotionArt.relay(c, a, t);
            case PHASE_BLUR -> MotionArt.phaseBlur(c, a, t);
            case SHADOW_STRIKE -> MotionArt.shadowStrike(c, a, t);
            case REFRACTION_CLOAK -> MotionArt.refraction(c, a, t);
            case PRISM_HAZE -> MotionArt.prismHaze(c, a, t);
            case PHASE_ERUPTION -> MotionArt.eruption(c, a, t);
            case DENSE_BULWARK -> MotionArt.dense(c, a, t);
            case PHASE_FIELD -> MotionArt.phaseField(c, a, t);
            case PRISMATIC_OVERFLOW -> MindArt.overflow(c, a, t);
            case EMPATHIC_WARD -> MindArt.ward(c, a, t);
            case KINDRED_LIGHT -> MindArt.kindred(c, a, t);
            case FORGE_CANNON -> MindArt.cannon(c, a, t);
            case FORGE_SLUG -> MindArt.slug(c, a, t);
            case BULWARK_LATTICE -> MindArt.lattice(c, a, t);
            case MENDING_LATTICE -> MindArt.mending(c, a, t);
            case EXPLODING_MAN -> MiniBlast.draw(c, a.origin(), a.scale() / 80F, a.scale() * 1.15F, t, a.piece().ticks);
            case SPEED_GUST -> SpeedArt.gust(c, a, t);
            case SPEED_SHARED_TIME -> SpeedArt.sharedTime(c, a, t);
            case SPEED_ARMS -> SpeedArt.arms(c, a, t);
            case SPEED_DEFLECT -> SpeedArt.deflect(c, a, t);
            case SPEED_DISINTEGRATE -> SpeedArt.disintegrate(c, a, t);
            case SHIELD_BRACE -> ShieldArt.draw(c, a, t);
            case VILLAGE_MELTDOWN, VILLAGE_SAFE -> VillageArt.draw(c, a, t);
            case GIFT_AWAKEN, GIFT_TURN, GIFT_UNBOUND -> GiftArt.draw(c, a, t);
            case OVERLOAD_EMBER, OVERLOAD_FIRESTORM, OVERLOAD_DEEP_FREEZE -> OverloadArt.draw(c, a, t);
            case METEOR_IMPACT, METEOR_SHATTER -> MeteorArt.draw(c, a, t);
            case CRYO_BLAST, CRYO_QUENCH, CRYO_CAST, CRYO_TERRAIN_FROST -> CryoArt.draw(c, a, t);
            case STYLE_ICE_STRIP -> CryoArt.strip(c, a.origin(), a.target(), t, a.piece().ticks, a.seed());
            case CRYO_FX -> CryoArt.fx(c, a, t);
            case REGEN_FX -> RegenArt.fx(c, a, t);
            case REGEN_STAND -> RegenArt.stand(c, a, t);
            case REGEN_TAUNT -> RegenArt.taunt(c, a, t);
            case REGEN_RELEASE -> RegenArt.release(c, a, t);
            case QUAKE_EPICENTER -> QuakeArt.epicenter(c, a, t);
            case FLIGHT_MICROBURST -> FlightArt.microburst(c, a, t);
            case TELEPATHY_STRINGS -> TelepathyArt.strings(c, a, t);
            case TELEPATHY_SNIP -> TelepathyArt.snip(c, a, t);
            case RAD_STAR_CHARGE, RAD_STAR, RAD_STAR_BLAST -> RadiationArt.draw(c, a, t);
            case SONIC_STANDING -> SonicArt.standing(c, a, t);
            case SONIC_STANDING_BREAK -> SonicArt.breakWave(c, a, t);
            case SPEED_ROUND -> SpeedArt.round(c, a, t);
            case SPEED_ROUND_HIT -> SpeedArt.roundHit(c, a, t);
            case FORCE_BASTION -> ForceArt.bastion(c, a, t);
            case FORCE_BASTION_BURST -> ForceArt.bastionBurst(c, a, t);
            case DENSITY_ARMOR -> DensityArt.mountain(c, a, t);
            case DENSITY_ARMOR_BURST -> DensityArt.mountainBurst(c, a, t);
            case MIMIC_CHORUS -> MimicArt.chorus(c, a, t);
            case MIMIC_VOICE -> MimicArt.voice(c, a, t);
            case MATTER_BALLISTA -> AssemblyArt.ballista(c, a, t);
            case MATTER_TURRET -> AssemblyArt.turret(c, a, t);
            case HYDRO_LEVIATHAN_RISE, HYDRO_LEVIATHAN_STRIKE, HYDRO_LEVIATHAN_DIVE -> HydroArt.leviathan(c, a, t);
            case AMP_OVERDRIVE -> AmpArt.overdrive(c, a, t);
            case AMP_DISCHARGE -> AmpArt.discharge(c, a, t);
            case CRYO_ZERO -> CryoArt.zero(c, a, t);
            case CRYO_EMBER -> CryoArt.ember(c, a, t);
            case RAD_HEAT, RAD_LEAP, RAD_SLAM, RAD_ZONE, RAD_EMP, RAD_BEAM, RAD_CHARGE, RAD_LANCE -> RadiationArt.draw(c, a, t);
            case RAD_SHOCK, RAD_FIELD, RAD_FLAKE, RAD_DISINTEGRATE -> HazardArt.draw(c, a, t);
            case SPEED_VACUUM -> CycloneArt.vacuum(c, a, t);
            case FORCE_SHIELD, FORCE_PUSH, FORCE_FORM, FORCE_BRIDGE, FORCE_WALL, FORCE_BUBBLE, FORCE_CRUSH, FORCE_BEAD,
                    FORCE_NOVA_CHARGE, FORCE_NOVA, FORCE_DISC, FORCE_VANISH, FORCE_NET, FORCE_SHATTER, FORCE_RIPPLE -> ForceArt.draw(c, a, t);
            case OVERRUN_DEPART -> SpeedArt.depart(c, a, t);
            case OVERRUN_ARRIVE -> SpeedArt.arrive(c, a, t);
            case MATTER_RISE -> AssemblyArt.rise(c, a, t);
            case MATTER_PANEL -> AssemblyArt.panel(c, a, t);
            case MATTER_PLATE -> AssemblyArt.plate(c, a, t);
            case MATTER_PAD -> AssemblyArt.pad(c, a, t, false);
            case MATTER_PAD_PLACE -> AssemblyArt.pad(c, a, t, true);
            case MATTER_LAUNCH -> AssemblyArt.launch(c, a, t);
            case MATTER_KALEIDO -> AssemblyArt.kaleido(c, a, t);
            case MATTER_SHARD -> AssemblyArt.shard(c, a, t);
            case MATTER_BOUNCE -> AssemblyArt.bounce(c, a, t);
            case MATTER_RETURN -> AssemblyArt.returning(c, a, t);
            case SENTINEL -> AssemblyArt.sentinel(c, a, t);
            case STORY_BOLT, STORY_GRIP -> StoryArt.draw(c, a, t);
            case FUTURE_OMEN, FUTURE_BREACH, FUTURE_SHUT -> FutureWarningArt.draw(c, a, t);
            case SYLAR_ROOM, SYLAR_LOCK, SYLAR_HURL_MARK, SYLAR_IMPACT, SYLAR_REEL_MARK, SYLAR_REEL, SYLAR_LATTICE_MARK,
                    SYLAR_LATTICE, SYLAR_ICE_MARK, SYLAR_ICE, SYLAR_FAULT_MARK, SYLAR_FAULT, SYLAR_RING_MARK, SYLAR_RING,
                    SYLAR_HAND_MARK, SYLAR_HAND, SYLAR_BLUR_MARK, SYLAR_BLUR, SYLAR_HOURS_MARK, SYLAR_HOURS, SYLAR_LIGHT_MARK,
                    SYLAR_LIGHT, SYLAR_FIZZLE, SYLAR_APPRAISE_MARK, SYLAR_HOLD, SYLAR_TAKE, SYLAR_RELEASE, SYLAR_YOURS_MARK,
                    SYLAR_YOURS, SYLAR_BACKFIRE, SYLAR_STAGGER, SYLAR_PHASE, SYLAR_COVER, SYLAR_SHOVE, SYLAR_ARRIVE,
                    SYLAR_DEATH -> SylarArt.draw(c, a, t);
            case GUN_HEAT, GUN_FROST, GUN_TASER -> WeaponArt.draw(c, a, t);
            case SHIFT_STUDY, SHIFT_TOUCH, SHIFT_LUNGE, SHIFT_INSTINCT, SHIFT_HOLD, SHIFT_SONIC, SHIFT_GAZE,
                 SHIFT_DECOY, SHIFT_DECOY_BURST, SHIFT_REFLECT, SHIFT_NIGHTMARE, SHIFT_EXPOSED, SHIFT_DAZE, SHIFT_PLATES -> ShapeArt.draw(c, a, t);
            case DAMPENER_FIELD, DAMPENER_SHOT, DAMPENER_HIT, DAMPENER_MARK, DAMPENER_CHARGE -> DampenerArt.draw(c, a, t);
            case HYDRO_WHIP, HYDRO_SNARE, HYDRO_WAVE, HYDRO_TSUNAMI, HYDRO_TENDRIL, HYDRO_BURST, HYDRO_SPLASH, HYDRO_WHIRLPOOL,
                    HYDRO_DELUGE, HYDRO_LASH, HYDRO_DAGGER, HYDRO_WHEEL, HYDRO_FLOW, HYDRO_VAULT, HYDRO_UNDERTOW, HYDRO_LOADED,
                    HYDRO_GEYSER_MARK, HYDRO_GEYSER, HYDRO_JET, HYDRO_WALL, HYDRO_HEAL, HYDRO_RAIN, HYDRO_DOME, HYDRO_SPRING,
                    HYDRO_AWAKEN -> HydroArt.draw(c, a, t);
            case STYLE_LENS_JAB, STYLE_SHARDS, STYLE_FORCE_BURST, STYLE_FIELD_SLAM, STYLE_LENS_SLIDE,
                    STYLE_BOUNCE -> StyleArt.draw(c, a, t);
            case STYLE_TK_PALM, STYLE_TK_GRASP, FINISH_STRINGS, FINISH_CRUSH, FINISH_TOSS -> KineticArt.draw(c, a, t);
            case FINISH_SHORT -> ElectroArt.finisher(c, a, t);
            case FINISH_BREAKER -> HydroArt.finisher(c, a, t);
            case FINISH_WARMTH -> CryoArt.warmth(c, a, t);
            case FINISH_THUNDER -> SpeedArt.finisher(c, a, t);
            case FINISH_DOWNBURST -> FlightArt.finisher(c, a, t);
            case FINISH_BEAT -> QuakeArt.finisher(c, a, t);
            case FINISH_ENCORE -> SonicArt.finisher(c, a, t);
            case FINISH_SIXFEET -> DensityArt.finisher(c, a, t);
            case FINISH_MIRRORS -> ForceArt.finisher(c, a, t);
            case FINISH_PAID -> RegenArt.finisher(c, a, t);
            case FINISH_CHAIN -> RadiationArt.finisher(c, a, t);
            case FINISH_FREQUENCY -> VibeArt.finisher(c, a, t);
            case FINISH_MONUMENT -> AssemblyArt.finisher(c, a, t);
            case FINISH_TAKE -> MimicArt.finisher(c, a, t);
            case FINISH_UNMADE -> ShapeArt.unmade(c, a, t);
            // The alternate finishers, each power's two in its own art class.
            case FINISH_STOPPED, FINISH_UNWOUND -> TimeArt.alternates(c, a, t);
            case FINISH_PRESSURE, FINISH_THOUGHT -> TelepathyArt.alternates(c, a, t);
            case FINISH_ROD, FINISH_OVERLOAD -> ElectroArt.alternates(c, a, t);
            case FINISH_BUBBLE, FINISH_RIPTIDE -> HydroArt.alternates(c, a, t);
            case FINISH_COFFIN, FINISH_ICICLES -> CryoArt.cryoFinishers(c, a, t);
            case FINISH_FISTS, FINISH_FLICK -> SpeedArt.alternates(c, a, t);
            case FINISH_CLAP, FINISH_UPDRAFT -> FlightArt.alternates(c, a, t);
            case FINISH_FAULT, FINISH_SHATTER -> QuakeArt.alternates(c, a, t);
            case FINISH_NOTE, FINISH_SILENCE -> SonicArt.alternates(c, a, t);
            case FINISH_PHASE, FINISH_HEAVY -> DensityArt.alternates(c, a, t);
            case FINISH_BUBBLE_CRUSH, FINISH_LENS -> ForceArt.alternates(c, a, t);
            case FINISH_NO_PAIN, FINISH_GRIP -> RegenArt.alternates(c, a, t);
            case FINISH_HALF_LIFE, FINISH_POCKET_SUN -> RadiationArt.alternates(c, a, t);
            case FINISH_BREACH, FINISH_POINT_BLANK -> VibeArt.alternates(c, a, t);
            case FINISH_HAMMER, FINISH_ORBIT -> AssemblyArt.alternates(c, a, t);
            case FINISH_ECHO, FINISH_BORROW -> MimicArt.alternates(c, a, t);
            case FINISH_APEX, FINISH_FACE -> ShapeArt.alternates(c, a, t);
        }
    }
}
