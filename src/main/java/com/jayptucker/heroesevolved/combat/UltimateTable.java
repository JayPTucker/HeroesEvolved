package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.combat.Ultimates.Ultimate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which Ultimate each class mechanic in {@code skill_trees.json} plays. The mechanics keep the ids of the class
 * abilities they replaced, so loadouts, keys and saved classes still match.
 */
final class UltimateTable {
    private UltimateTable() {}

    static Map<String, Ultimate> build() {
        Map<String, Ultimate> m = new LinkedHashMap<>();
        // Superspeed
        m.put("lightning_flurry", com.jayptucker.heroesevolved.speed.SpeedUltimates.OFFENSE);
        m.put("phase_blur", com.jayptucker.heroesevolved.speed.SpeedUltimates.DEFENSE);
        m.put("relay_run", com.jayptucker.heroesevolved.speed.SpeedUltimates.SUPPORT);
        // Pyrokinesis
        m.put("sunfall", com.jayptucker.heroesevolved.pyro.PyroUltimates.OFFENSE);
        m.put("ember_shroud", com.jayptucker.heroesevolved.pyro.PyroUltimates.DEFENSE);
        m.put("hearthfire", com.jayptucker.heroesevolved.pyro.PyroUltimates.SUPPORT);
        // Electrokinesis
        m.put("tesla_spire", com.jayptucker.heroesevolved.electro.ElectroUltimates.OFFENSE);
        m.put("faraday_cage", com.jayptucker.heroesevolved.electro.ElectroUltimates.DEFENSE);
        m.put("jumpstart", com.jayptucker.heroesevolved.electro.ElectroUltimates.SUPPORT);
        // Hydrokinesis
        m.put("hydro_tsunami", com.jayptucker.heroesevolved.hydro.HydroUltimates.OFFENSE);
        m.put("hydro_tidal_dome", com.jayptucker.heroesevolved.hydro.HydroUltimates.DEFENSE);
        m.put("hydro_healing_spring", com.jayptucker.heroesevolved.hydro.HydroUltimates.SUPPORT);
        // Cryokinesis
        m.put("hailstorm", com.jayptucker.heroesevolved.cryo.CryoUltimates.OFFENSE);
        m.put("crystal_bastion", com.jayptucker.heroesevolved.cryo.CryoUltimates.DEFENSE);
        m.put("aurora_veil", com.jayptucker.heroesevolved.cryo.CryoUltimates.SUPPORT);
        // Flight
        m.put("tempest_drill", com.jayptucker.heroesevolved.flight.FlightUltimates.OFFENSE);
        m.put("gale_wall", com.jayptucker.heroesevolved.flight.FlightUltimates.DEFENSE);
        m.put("formation_flight", com.jayptucker.heroesevolved.flight.FlightUltimates.SUPPORT);
        // Quake
        m.put("fissure_maw", com.jayptucker.heroesevolved.quake.QuakeUltimates.OFFENSE);
        m.put("stone_rampart", com.jayptucker.heroesevolved.quake.QuakeUltimates.DEFENSE);
        m.put("bedrock_stance", com.jayptucker.heroesevolved.quake.QuakeUltimates.SUPPORT);
        // Sonic Resonance
        m.put("resonance_cascade", com.jayptucker.heroesevolved.sonic.SonicUltimates.OFFENSE);
        m.put("silence_dome", com.jayptucker.heroesevolved.sonic.SonicUltimates.DEFENSE);
        m.put("battle_hymn", com.jayptucker.heroesevolved.sonic.SonicUltimates.SUPPORT);
        // Telepathy
        m.put("psychic_storm", com.jayptucker.heroesevolved.telepathy.TelepathyUltimates.OFFENSE);
        m.put("blind_spot", com.jayptucker.heroesevolved.telepathy.TelepathyUltimates.DEFENSE);
        m.put("hive_mind", com.jayptucker.heroesevolved.telepathy.TelepathyUltimates.SUPPORT);
        // Telekinesis
        m.put("implosion", com.jayptucker.heroesevolved.telekinesis.KineticUltimates.OFFENSE);
        m.put("debris_halo", com.jayptucker.heroesevolved.telekinesis.KineticUltimates.DEFENSE);
        m.put("gathering_hand", com.jayptucker.heroesevolved.telekinesis.KineticUltimates.SUPPORT);
        // Space-Time Manipulation
        m.put("hour_hand", com.jayptucker.heroesevolved.time.TimeUltimates.OFFENSE);
        m.put("time_loop", com.jayptucker.heroesevolved.time.TimeUltimates.DEFENSE);
        m.put("borrowed_time", com.jayptucker.heroesevolved.time.TimeUltimates.SUPPORT);
        // Regeneration
        m.put("vital_burst", com.jayptucker.heroesevolved.regeneration.RegenUltimates.OFFENSE);
        m.put("undying", com.jayptucker.heroesevolved.regeneration.RegenUltimates.DEFENSE);
        m.put("lifebloom", com.jayptucker.heroesevolved.regeneration.RegenUltimates.SUPPORT);
        // Invisibility
        m.put("shadow_strike", com.jayptucker.heroesevolved.invisibility.ForceUltimates.OFFENSE);
        m.put("refraction_cloak", com.jayptucker.heroesevolved.invisibility.ForceUltimates.DEFENSE);
        m.put("prism_haze", com.jayptucker.heroesevolved.invisibility.ForceUltimates.SUPPORT);
        // Empathic Mimicry
        m.put("prismatic_overflow", com.jayptucker.heroesevolved.mimicry.MimicUltimates.OFFENSE);
        m.put("empathic_ward", com.jayptucker.heroesevolved.mimicry.MimicUltimates.DEFENSE);
        m.put("kindred_light", com.jayptucker.heroesevolved.mimicry.MimicUltimates.SUPPORT);
        // Matter Assembly
        m.put("forge_cannon", com.jayptucker.heroesevolved.assembly.AssemblyUltimates.OFFENSE);
        m.put("bulwark_lattice", com.jayptucker.heroesevolved.assembly.AssemblyUltimates.DEFENSE);
        m.put("mending_lattice", com.jayptucker.heroesevolved.assembly.AssemblyUltimates.SUPPORT);
        // Vibe
        m.put("vibe_crosswave", com.jayptucker.heroesevolved.vibe.VibeUltimates.OFFENSE);
        m.put("vibe_mirror", com.jayptucker.heroesevolved.vibe.VibeUltimates.DEFENSE);
        m.put("vibe_sanctuary", com.jayptucker.heroesevolved.vibe.VibeUltimates.SUPPORT);
        // Power Amplification
        m.put("amp_lightning_cannon", com.jayptucker.heroesevolved.amplify.AmpUltimates.OFFENSE);
        m.put("amp_lights_back_on", com.jayptucker.heroesevolved.amplify.AmpUltimates.DEFENSE);
        m.put("amp_got_your_back", com.jayptucker.heroesevolved.amplify.AmpUltimates.SUPPORT);
        // Shapeshifting
        m.put("shapeshifting_many_hands", com.jayptucker.heroesevolved.shapeshift.ShapeUltimates.OFFENSE);
        m.put("shapeshifting_plate_mail", com.jayptucker.heroesevolved.shapeshift.ShapeUltimates.DEFENSE);
        m.put("shapeshifting_masquerade", com.jayptucker.heroesevolved.shapeshift.ShapeUltimates.SUPPORT);
        // Density
        m.put("phase_eruption", com.jayptucker.heroesevolved.phasing.DensityUltimates.OFFENSE);
        m.put("dense_bulwark", com.jayptucker.heroesevolved.phasing.DensityUltimates.DEFENSE);
        m.put("phase_field", com.jayptucker.heroesevolved.phasing.DensityUltimates.SUPPORT);
        // Induced Radioactivity
        m.put("ground_zero", com.jayptucker.heroesevolved.radiation.RadiationUltimates.OFFENSE);
        m.put("containment_dome", com.jayptucker.heroesevolved.radiation.RadiationUltimates.DEFENSE);
        m.put("reactor_hearth", com.jayptucker.heroesevolved.radiation.RadiationUltimates.SUPPORT);
        return Map.copyOf(m);
    }
}
