package com.jayptucker.heroesevolved.admin;

import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.company.CompanyCommands;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.meteor.Meteors;
import com.jayptucker.heroesevolved.meteor.Starfall;
import com.jayptucker.heroesevolved.network.AdminStatePayload.EventEntry;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.story.night.NightThreeService;
import com.jayptucker.heroesevolved.expansion.FacilityService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.jayptucker.heroesevolved.story.sylar.CollectionArena;
import com.jayptucker.heroesevolved.story.sylar.SylarCommands;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import com.jayptucker.heroesevolved.village.VillageIncidents.Kind;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import com.jayptucker.heroesevolved.village.overload.Overloads;
import com.jayptucker.heroesevolved.village.overload.Storm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.ArrayList;
import java.util.List;

/** The menu's event catalogue and lifecycle dispatch. Uses each event's own start/cleanup implementation. */
public final class AdminEvents {
    private AdminEvents() {}

    public static List<EventEntry> describe(MinecraftServer server, ServerPlayer target) {
        boolean player = target != null;
        String near = player ? "Near " + target.getName().getString() : "Select an online player";
        List<EventEntry> out = new ArrayList<>();
        out.add(entry("eclipse", "Eclipse", false, "Start / cancel: entire server", EclipseService.describe(server).getString(),
                EclipseService.isActive(server), true, "First contact", "Totality"));
        int meltdowns = VillagerMeltdown.all().size() + VillageIncidents.pending(Kind.MELTDOWN);
        out.add(entry("meltdown", "Villager meltdown", false, near + " / cancel: server", VillagerMeltdown.describe()
                + " Queued: " + VillageIncidents.pending(Kind.MELTDOWN) + ".", meltdowns > 0, player,
                "60 seconds", "20 seconds", "Find village"));
        int gifts = Renegades.all().size() + Renegades.turningCount() + VillageIncidents.pending(Kind.AWAKENING)
                + VillageIncidents.pending(Kind.RENEGADE) + VillageIncidents.pending(Kind.GUARDIAN);
        out.add(entry("gifts", "Village awakenings", false, near + " / cancel: server", Renegades.all().size()
                + " siege(s), " + Renegades.turningCount() + " turning; queued starts: "
                + (gifts - Renegades.all().size() - Renegades.turningCount())
                + ". Cancel stops sieges and queued awakenings; existing guardians and hostile mobs remain.",
                gifts > 0, player, "Random gift", "Renegade", "Guardian"));
        out.add(entry("overload", "Overload storms", false, near + " / cancel: server", Overloads.describe()
                + " Queued: " + VillageIncidents.pending(Kind.OVERLOAD) + ".", !Overloads.all().isEmpty()
                || VillageIncidents.pending(Kind.OVERLOAD) > 0, player, "Wildfire", "Supercell", "Blizzard", "Find village"));
        out.add(entry("starfall", "Starfall shower", false, "Start / cancel: Overworld", Starfall.describe(server)
                + " Cancel also clears falling meteors and queued great meteors.", Starfall.active(server), true, "Start shower"));
        out.add(entry("meteors", "Falling meteors", false, near + " / cancel: server", Meteors.describe()
                + " Queued great meteors: " + VillageIncidents.pending(Kind.METEOR) + ".", !Meteors.all().isEmpty()
                || VillageIncidents.pending(Kind.METEOR) > 0, player, "Falling star", "Great meteor", "Find village"));
        var raid = player ? raidAt(target) : null;
        out.add(entry("raid", "Village raid", false, near + " / cancel: same village", raid == null
                ? "No raid within 64 blocks. Start needs a loaded village bell. Surviving raiders remain after cancellation."
                : "Raid at " + raid.getCenter().toShortString() + ", wave " + raid.getGroupsSpawned() + ". Surviving raiders remain.",
                raid != null, player, "Start raid"));
        String company = player ? "Attention " + Math.round(CompanyAttention.of(target)) + " ("
                + CompanyAttention.band(target).name().toLowerCase(java.util.Locale.ROOT) + "). " : "";
        out.add(entry("company", "Company operations", false, near + " / cancel: server", company
                + CompanyOps.all().stream().filter(op -> !op.over()).count() + " operation(s). "
                + (player && CompanyOps.pending(target) ? "Investigation queued for this player. " : "")
                + "Cancel recalls agents, releases prisoners and clears queued operations.",
                !CompanyOps.all().isEmpty() || player && CompanyOps.pending(target), player,
                "Investigate", "Convoy", "Home raid"));
        boolean playing = !com.jayptucker.heroesevolved.combat.Ultimates.live().isEmpty();
        out.add(entry("ultimate", "Class Ultimates", false, (player ? target.getName().getString() : "Select an online player")
                        + " / cancel: this player (no selection: every Ultimate playing)",
                com.jayptucker.heroesevolved.combat.UltimateCommands.status(server, target)
                        + " Cast ignores stamina and the wait; Offense still needs a foe in front of the player.",
                player ? com.jayptucker.heroesevolved.combat.Ultimates.running(target) : playing, player,
                "Cast their class's Ultimate", "Ready it again"));
        out.add(entry("wither", "Wither: Containment Failure", true, near + " / phase and cancel: all fighters",
                com.jayptucker.heroesevolved.wither.WitherArena.status(server),com.jayptucker.heroesevolved.wither.WitherArena.busy(server),player,
                "Open breach", "Enter fight", "Advance phase", "Finish it", "Break ward"));
        out.add(entry("dragon", "End: The Living Seal", true, near + " / phases and cancel: all fighters",
                com.jayptucker.heroesevolved.endgame.EndBattle.status(server), com.jayptucker.heroesevolved.endgame.EndBattle.active(), player,
                "Start fight", "Advance phase", "Break its locks", "Ground it", "Finish Dragon"));
        out.add(entry("sylar", "Sylar's Collection", true, near + " / cancel: all fighters", SylarCommands.status(server),
                CollectionArena.open(), player && !CollectionArena.open(), "Start fight"));
        out.add(entry("prologue", "The Last Day", true, player ? target.getName().getString() + " / pods: all participants" : near,
                "Phase: " + PrologueService.phase() + ". Start enrolls this player; cancel wakes only this player in the bunker.",
                player && (PrologueService.isMember(target) || PrologueService.isWaiting(target)), player,
                "Enter prologue"));
        out.add(entry("annex", "Hourglass Annex", true, near + " / cancel: this player", "Prepare builds the annex. Enter starts the rescue when ready; cancellation returns this player.",
                player && FacilityService.atSite(target), player, "Prepare annex", "Enter annex"));
        out.add(entry("avenue", "The avenue", true, near + " / cancel: all runners", "Operator entry bypasses the story unlock. Odessa must finish preparing first. Cancellation returns all runners.",
                AvenueService.running(), player, "Enter avenue"));
        out.add(entry("night", "Night Three", true, near + " / cancel: all runners", "Operator entry bypasses the story unlock. Cancellation returns all runners to the present.",
                NightThreeService.running(), player, "Enter Night Three"));
        out.add(entry("vision", "Odessa vision", true, near + " / cancel: all vision viewers", "Replay the awakening vision for this player without changing their power. It waits until Odessa is available.",
                TimeIntroService.sceneRunning() || player && (TimeIntroService.isQueued(target)
                || com.jayptucker.heroesevolved.time.intro.TimeGather.gathering(target)), player, "Replay vision"));
        out.add(entry("futurewarning", "Future-self warning", true, near + " / cancel: this player (no selection: server)",
                com.jayptucker.heroesevolved.story.future.FutureWarning.status(server, target),
                player ? com.jayptucker.heroesevolved.story.future.FutureWarning.active(target)
                        || com.jayptucker.heroesevolved.story.future.FutureWarning.pending(target)
                        : com.jayptucker.heroesevolved.story.future.FutureWarningData.get(server).pendingCount() > 0,
                player && !com.jayptucker.heroesevolved.story.future.FutureWarning.active(target),
                "Omen, then one day", "Warning now", "Reset encounter"));
        return List.copyOf(out);
    }

    private static EventEntry entry(String id, String title, boolean story, String scope, String status,
                                    boolean active, boolean canStart, String... starts) {
        return new EventEntry(id, title, story, scope, status, active, canStart, List.of(starts));
    }

    public static String start(MinecraftServer server, ServerPlayer target, String id, int variant) {
        EventEntry entry = describe(server, target).stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
        if (entry == null || variant < 0 || variant >= entry.starts().size()) return "Unknown event or start option.";
        if (!entry.canStart()) return "This event cannot start now; select an online player or cancel its current run.";
        return switch (id) {
            case "wither" -> com.jayptucker.heroesevolved.wither.WitherArena.operatorStart(target,variant);
            case "dragon" -> com.jayptucker.heroesevolved.endgame.EndBattle.operatorStart(target, variant);
            case "eclipse" -> (variant == 0 ? EclipseService.start(server) : EclipseService.beginTotality(server))
                    ? "Started " + entry.starts().get(variant) + "." : "The Eclipse is already in that phase.";
            case "meltdown" -> variant == 2 ? incident(target, Kind.MELTDOWN)
                    : VillagerMeltdown.startNear(target.serverLevel(), target.position(), (variant == 0 ? 60 : 20) * 20) != null
                    ? "Started a meltdown near " + target.getName().getString() + "." : "No eligible grown villager within 64 blocks.";
            case "gifts" -> incident(target, new Kind[]{Kind.AWAKENING, Kind.RENEGADE, Kind.GUARDIAN}[variant]);
            case "overload" -> variant == 3 ? incident(target, Kind.OVERLOAD)
                    : Overloads.startNear(target.serverLevel(), target.position(), Storm.values()[variant],
                    HeroesEvolvedConfig.COMMON.overloadTicks.get()) != null ? "Started " + entry.starts().get(variant) + "."
                    : "No eligible grown villager within 64 blocks.";
            case "starfall" -> { Starfall.force(server, true); yield "Started Starfall in the Overworld."; }
            case "ultimate" -> variant == 0 ? com.jayptucker.heroesevolved.combat.UltimateCommands.cast(target, null)
                    : com.jayptucker.heroesevolved.combat.UltimateCommands.reset(target);
            case "meteors" -> {
                if (variant == 2) yield incident(target, Kind.METEOR);
                if (variant == 0) {
                    if (Starfall.near(target) == null) yield "No loaded dry ground nearby for a falling star.";
                } else Meteors.great(target.serverLevel(), target.blockPosition(), HeroesEvolvedConfig.COMMON.meteorGreatTicks.get());
                yield "A " + entry.starts().get(variant).toLowerCase(java.util.Locale.ROOT) + " is falling near " + target.getName().getString() + ".";
            }
            case "raid" -> startRaid(target);
            case "company" -> CompanyCommands.startFor(new String[]{"investigate", "convoy", "raid"}[variant], target) != null
                    ? "Started Company " + entry.starts().get(variant).toLowerCase(java.util.Locale.ROOT) + "."
                    : "Company start refused: already busy, or no loaded village/prisoner available.";
            case "sylar" -> {
                var refused = CollectionArena.refusal(target);
                yield refused != null ? refused.getString() : CollectionArena.open(target) != null
                        ? "Opened the Collection." : "The Collection could not open.";
            }
            case "prologue" -> {
                if (PrologueService.isMember(target) || PrologueService.isWaiting(target)) yield "That player is already in the Last Day.";
                PrologueService.join(target); yield "Enrolled " + target.getName().getString() + " in the Last Day.";
            }
            case "annex" -> variant == 0 ? FacilityService.plan(target) ? "Preparing the annex; enter once it is ready." : "The annex dimension is unavailable."
                    : FacilityService.enter(target) ? "Entered the annex." : "The annex is not ready, or this player is in another scene. Prepare it first.";
            case "avenue" -> AvenueService.enter(target, true) ? "Entered the avenue." : "Avenue entry refused: city preparing, scene busy, or player unavailable.";
            case "night" -> NightThreeService.enter(target, true) ? "Entered Night Three." : "Night Three entry refused: scene busy or player unavailable.";
            case "vision" -> TimeIntroService.replay(target) ? "Queued the Odessa vision for this player." : "This player is already in a scene or queued for a vision.";
            case "futurewarning" -> {
                if (variant == 2) {
                    com.jayptucker.heroesevolved.story.future.FutureWarning.cancel(target, true);
                    yield "Reset this player's warning; they may be chosen again.";
                }
                yield com.jayptucker.heroesevolved.story.future.FutureWarning.start(target, variant == 1)
                        ? "Started " + entry.starts().get(variant) + " for " + target.getName().getString() + "."
                        : "Warning refused: player busy or unsafe, or no clear space behind them. Try standing on dry, open ground in the Overworld.";
            }
            default -> "Unknown event.";
        };
    }

    public static String incident(ServerPlayer target, Kind kind) {
        var level = target.serverLevel();
        // Prefer a loaded bell, including operator-built villages in worlds without generated structures.
        var village = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), target.blockPosition(),
                64, PoiManager.Occupancy.ANY).orElse(null);
        if (village == null) village = VillageIncidents.find(level, target.blockPosition(),
                HeroesEvolvedConfig.COMMON.villageIncidentRange.get(), level.random);
        if (village == null) return "No village within the configured incident range.";
        if (EmergencyBand.troubled(level, village)) return "That village is already in trouble.";
        VillageIncidents.requestAt(level, village, kind);
        return "Queued " + kind.name().toLowerCase(java.util.Locale.ROOT) + " at " + village.toShortString() + "; waiting for villagers to load.";
    }

    public static String cancel(MinecraftServer server, ServerPlayer target, String id) {
        return switch (id) {
            case "dragon" -> { boolean was = com.jayptucker.heroesevolved.endgame.EndBattle.active(); com.jayptucker.heroesevolved.endgame.EndBattle.reset(server, false);
                yield was ? "Cancelled the End attempt; temporary scenery is restored and fighters keep their belongings." : "No End fight was running; its saved stage was cleared."; }
            case "wither" -> {com.jayptucker.heroesevolved.wither.WitherArena.reset(server,false);yield "Cancelled the Wither attempt and source breach; fighters return home and keep their belongings.";}
            case "eclipse" -> EclipseService.end(server) ? "Cancelled the Eclipse." : "No Eclipse is active.";
            case "meltdown" -> "Cancelled " + (VillageIncidents.cancel(Kind.MELTDOWN) + VillagerMeltdown.cancelAll()) + " meltdown(s) / queued starts.";
            case "gifts" -> "Cancelled " + (VillageIncidents.cancel(Kind.AWAKENING, Kind.RENEGADE, Kind.GUARDIAN) + Renegades.cancelAll())
                    + " siege(s) / queued awakenings. Existing guardians and hostile mobs remain.";
            case "overload" -> "Cancelled " + (VillageIncidents.cancel(Kind.OVERLOAD) + Overloads.cancelAll()) + " overload(s) / queued starts.";
            case "starfall" -> { Starfall.force(server, false); Meteors.cancelAll(); VillageIncidents.cancel(Kind.METEOR); yield "Cancelled Starfall and all falling / queued meteors."; }
            case "meteors" -> "Cancelled " + (VillageIncidents.cancel(Kind.METEOR) + Meteors.cancelAll()) + " meteor(s) / queued starts. The shower schedule is unchanged.";
            case "company" -> "Called off " + CompanyOps.callOffAll() + " Company operation(s) and cleared queued operations.";
            case "ultimate" -> target != null ? com.jayptucker.heroesevolved.combat.UltimateCommands.stop(target)
                    : "Stopped " + com.jayptucker.heroesevolved.combat.UltimateCommands.stopAll() + " Ultimate(s); held foes were let go.";
            case "raid" -> {
                var raid = target == null ? null : raidAt(target);
                if (raid == null) yield "No active raid within 64 blocks of the selected player.";
                raid.stop(); yield "Cancelled the village raid. Surviving raiders remain.";
            }
            case "sylar" -> { CollectionArena.end(server, CollectionArena.Outcome.ABANDONED); yield "Closed the Collection; everyone returns home."; }
            case "prologue" -> {
                if (target == null || !PrologueService.isMember(target) && !PrologueService.isWaiting(target)) yield "That player is not in the Last Day.";
                PrologueService.skip(target); yield "Ended the Last Day for " + target.getName().getString() + "; waking in the bunker.";
            }
            case "annex" -> target != null && FacilityService.leave(target) ? "Returned this player from the annex." : "This player is not visiting the annex.";
            case "avenue" -> { AvenueService.shutdown(server); yield "Cancelled the avenue; all runners return home."; }
            case "night" -> { NightThreeService.shutdown(server); yield "Cancelled Night Three; all runners return home."; }
            case "vision" -> { TimeIntroService.cancelVision(server); yield "Cancelled the vision and pending gathers; viewers return home."; }
            case "futurewarning" -> {
                if (target == null) com.jayptucker.heroesevolved.story.future.FutureWarning.cancelAll(server);
                else com.jayptucker.heroesevolved.story.future.FutureWarning.cancel(target, false);
                yield "Cancelled the warning and its saved pending encounter; reset to allow another natural visit.";
            }
            default -> "Unknown event.";
        };
    }

    /** Vanilla lookup uses the selected village, even when the player stands just outside the raid boundary. */
    public static net.minecraft.world.entity.raid.Raid raidAt(ServerPlayer target) {
        var level = target.serverLevel();
        var raid = level.getRaidAt(target.blockPosition());
        if (raid == null) {
            var bell = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), target.blockPosition(),
                    64, PoiManager.Occupancy.ANY).orElse(null);
            if (bell != null) raid = level.getRaidAt(bell);
        }
        return raid != null && raid.isActive() && !raid.isStopped() ? raid : null;
    }

    private static String startRaid(ServerPlayer target) {
        var level = target.serverLevel();
        var bell = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), target.blockPosition(),
                64, PoiManager.Occupancy.ANY).orElse(null);
        if (bell == null) return "No loaded village bell within 64 blocks.";
        if (raidAt(target) != null) return "That village is already being raided.";
        return level.getRaids().createOrExtendRaid(target, bell) != null ? "Started a village raid."
                : "Raid refused: Peaceful difficulty, raids disabled, or this is not a village.";
    }

    /** Vanilla exposes raids by id. Read its saved ids to include raids with no player standing nearby. */
    public static int cancelAllRaids(MinecraftServer server) {
        int count = 0;
        for (var level : server.getAllLevels()) {
            var raids = level.getRaids();
            var saved = raids.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()).getList("Raids", 10);
            for (int i = 0; i < saved.size(); i++) {
                var raid = raids.get(saved.getCompound(i).getInt("Id"));
                if (raid != null && !raid.isStopped()) { raid.stop(); count++; }
            }
            raids.setDirty();
        }
        return count;
    }

    public static List<String> diagnostics(MinecraftServer server, ServerPlayer target) {
        var level = target == null ? server.overworld() : target.serverLevel();
        return List.of("Server tick: " + server.getTickCount() + " | players: " + server.getPlayerList().getPlayerCount(),
                "Dimension: " + level.dimension().location(), "Game time: " + level.getGameTime() + " | day time: " + level.getDayTime(),
                "Queued village incidents: " + VillageIncidents.pending(),
                "Meltdowns: " + VillagerMeltdown.all().size() + " | overloads: " + Overloads.all().size(),
                "Sieges: " + Renegades.all().size() + " | turning: " + Renegades.turningCount() + " | meteors: " + Meteors.all().size(),
                "Company operations: " + CompanyOps.all().size(),
                "Radio: " + (EmergencyBand.onAir() ? "connected" : "subtitle fallback") + " | distress calls: " + EmergencyBand.calls(),
                "Last Day: " + PrologueService.phase() + " | Collection: " + (CollectionArena.open() ? "open" : "closed"));
    }
}
