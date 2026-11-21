package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.telepathy.TelepathyFocus;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The first thing the Company does about someone it has noticed: a man in a dark suit walks into the village where a
 * big move was seen, questions three of the villagers who were there (question and answer over their heads), makes
 * notes at the spot and walks away. What he leaves with depends on the player: his notes, nothing (a village that owes
 * them covers for them; a telepath takes the afternoon out of his head), or nothing ever again (killed).
 */
public final class Investigation extends CompanyOps.Op {
    public enum Stage { ARRIVING, QUESTIONING, NOTES, FIGHTING, LEAVING }
    public static final int QUESTIONS = 3, ASK = 60, ANSWER = 60, NOTES_TICKS = 80, WALK_MAX = 1200, ASK_REACH = 32;
    public static final double START_DISTANCE = 30, TALK = 2.8, GREET = 6;
    /** A telepath with a thread this deep on him can wipe what he learnt. */
    public static final int WIPE_FOCUS = 30;
    private static final String[] QUESTION = {"Did you see who did this?", "Anyone round here with... unusual talents?",
            "Take your time. What did you see?"};
    private static final String[] COVER = {"Never saw a thing, mister.", "Nothing happened here.", "Must've been the weather."};

    final BlockPos site, bell;
    final String village;
    CompanyAgentEntity agent;
    Stage stage = Stage.ARRIVING;
    int stageAge, askAge, calmFor;
    final List<Villager> asked = new ArrayList<>();
    Villager asking;
    boolean covered, wiped, sawPower, spoke, fought, killed;
    Vec3 exit;

    private Investigation(ServerLevel level, ServerPlayer subject, BlockPos site, BlockPos bell) {
        super(level, subject);
        this.site = site.immutable();
        this.bell = bell.immutable();
        this.village = VillageMemory.of(level, bell).name();
    }

    @Override public CompanyOps.Kind kind() { return CompanyOps.Kind.INVESTIGATION; }
    public Stage stage() { return stage; }
    public CompanyAgentEntity agent() { return agent; }
    public boolean covered() { return covered; }
    public boolean wiped() { return wiped; }
    public int questioned() { return asked.size(); }
    /** The villager he is questioning now, or null. */
    public Villager asking() { return asking; }
    public BlockPos site() { return site; }

    /**
     * Sends an investigator to {@code site} in the village round {@code bell}, about {@code subject}, walking in from
     * {@code from} (null: thirty blocks out, on the far side from them). Null if they already have an operation.
     */
    public static Investigation start(ServerLevel level, ServerPlayer subject, BlockPos site, BlockPos bell, Vec3 from) {
        if (CompanyOps.current(subject) != null) return null;
        Investigation op = new Investigation(level, subject, site, bell);
        Vec3 spot = Vec3.atBottomCenterOf(site);
        if (from == null) {
            double angle = Math.atan2(spot.z - subject.getZ(), spot.x - subject.getX()) + (level.random.nextDouble() - 0.5);
            from = CompanyOps.out(level, spot, angle, START_DISTANCE);
        }
        op.exit = from;
        op.agent = op.spawn(CompanyAgentEntity.Role.GUARD, from, CompanyOps.yaw(from, spot));
        if (op.agent == null) return null;
        // His gun on his hip, his notebook in his hand.
        op.agent.holster();
        op.agent.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WRITABLE_BOOK));
        VillageMemory.Village v = VillageMemory.near(level, bell);
        op.covered = v != null && v.owes(subject.getUUID());
        CompanyOps.add(op);
        if (subject.distanceToSqr(spot) <= 96 * 96)
            op.tell(spot, 96, "A man in a dark suit is walking into " + op.village + ", toward where you were seen.");
        return op;
    }

    private Vec3 spot() { return Vec3.atBottomCenterOf(site); }

    private void to(Stage next) {
        stage = next;
        stageAge = 0;
    }

    @Override void tick() {
        if (agent == null || CompanyOps.downed(agent)) {
            if (!killed) callOff();
            return;
        }
        stageAge++;
        if (stage != Stage.FIGHTING && stage != Stage.LEAVING && agent.getTarget() != null) {
            letGo();
            fought = true;
            to(Stage.FIGHTING);
        }
        if (!wiped && stage != Stage.LEAVING && age % 10 == 0) wipe();
        if (over) return;
        switch (stage) {
            case ARRIVING -> {
                agent.walkTo(spot(), 0.9);
                if (agent.distanceToSqr(spot()) <= 16 || stageAge > WALK_MAX) to(Stage.QUESTIONING);
            }
            case QUESTIONING -> question();
            case NOTES -> notes();
            case FIGHTING -> {
                agent.stopWalking();
                if (agent.getTarget() == null || !agent.getTarget().isAlive()) {
                    if (++calmFor > 200) to(Stage.LEAVING);
                } else calmFor = 0;
            }
            case LEAVING -> {
                agent.walkTo(exit, 1.0);
                if (agent.distanceToSqr(exit) <= 9 || stageAge > WALK_MAX) resolve();
            }
        }
        if (!spoke && stage != Stage.FIGHTING && stage != Stage.LEAVING && subject.distanceToSqr(agent) <= GREET * GREET
                && !CompanyAttention.hidden(subject)) {
            spoke = true;
            speak(agent, "Afternoon. Quite a mess. You wouldn't know anything about it?", 12);
        }
    }

    // ------------------------------------------------------------------
    // Questions
    // ------------------------------------------------------------------

    private void question() {
        if (asking == null) {
            if (asked.size() >= QUESTIONS || (asking = next()) == null) { to(Stage.NOTES); return; }
            askAge = 0;
        }
        if (!asking.isAlive()) { asking = null; return; }
        boolean close = agent.distanceTo(asking) <= TALK;
        if (askAge == 0 && !close && stageAge < 400) {
            agent.walkTo(asking.position(), 0.9);
            hold(asking);
            return;
        }
        // Talking: both stand still and face each other.
        agent.stopWalking();
        agent.face(asking);
        hold(asking);
        askAge++;
        if (askAge == 1) Shouts.talk(level, agent, QUESTION[asked.size() % QUESTION.length], ASK);
        if (askAge == ASK) Shouts.talk(level, asking, answer(asked.size()), ANSWER);
        if (askAge >= ASK + ANSWER) {
            asked.add(asking);
            asking = null;
            stageAge = 0;
        }
    }

    /** The nearest villager who was there and hasn't been asked: awake, grown, within reach of the spot. */
    private Villager next() {
        Villager best = null;
        for (Villager v : level.getEntitiesOfClass(Villager.class, agent.getBoundingBox().inflate(ASK_REACH + 16),
                v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !asked.contains(v) && v.distanceToSqr(spot()) <= ASK_REACH * ASK_REACH
                        && !v.getTags().contains(CompanyOps.PRISONER_TAG)))
            if (best == null || v.distanceToSqr(agent) < best.distanceToSqr(agent)) best = v;
        return best;
    }

    /** Keeps {@code v} where they are, looking at him. */
    private void hold(Villager v) {
        var brain = v.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(agent, true));
        v.getNavigation().stop();
    }

    private void letGo() {
        if (asking != null) asking.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        asking = null;
    }

    /** What the {@code n}th villager says: nothing, if the village owes the subject; otherwise, the truth. */
    String answer(int n) {
        if (covered && !sawPower) return COVER[n % COVER.length];
        String name = subject.getName().getString();
        return switch (n % 3) {
            case 0 -> "It was " + name + "! " + seen() + "!";
            case 1 -> "Everyone saw. " + name + ".";
            default -> "Ask " + name + ". They live up the way.";
        };
    }

    /** What a villager saw the subject's power do. */
    private String seen() {
        ResourceLocation power = PlayerAbilityService.getData(subject).assignedPower().map(e -> e.getKey()).orElse(null);
        if (power == null) return "They did things nobody should do";
        String[][] said = {
                {"pyrokinesis", "Fire, out of their hands"}, {"cryokinesis", "Ice, out of nowhere"},
                {"electrokinesis", "Lightning from their fingers"}, {"flight", "They flew right over the roofs"},
                {"superspeed", "Too fast to see"}, {"radiation", "They glowed like the sun"},
                {"quake", "The ground shook under us"}, {"hydrokinesis", "The water moved for them"},
                {"telekinesis", "Things flew about on their own"}, {"sonic", "A sound that broke every window"},
                {"time", "They were here, then they were there"}, {"phasing", "The earth stood up for them"}};
        for (String[] s : said) if (power.getPath().contains(s[0])) return s[1];
        return "They did things nobody should do";
    }

    private void notes() {
        if (agent.distanceToSqr(spot()) > 9 && stageAge < 200) { agent.walkTo(spot(), 0.9); return; }
        agent.stopWalking();
        if (stageAge % 20 == 1) level.playSound(null, agent.getX(), agent.getY(), agent.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1, 1);
        if (stageAge % 40 == 20) {
            // A photograph of the spot.
            Vec3 eye = agent.getEyePosition();
            level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.NEUTRAL, 1, 1.6F);
        }
        if (stageAge >= NOTES_TICKS + 200 || (stageAge >= NOTES_TICKS && agent.distanceToSqr(spot()) <= 9)) to(Stage.LEAVING);
    }

    // ------------------------------------------------------------------
    // How it ends
    // ------------------------------------------------------------------

    /** A telepath with a deep thread on him takes what he learnt. */
    private void wipe() {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(agent) > 48 * 48 || TelepathyFocus.focus(p, agent) < WIPE_FOCUS) continue;
            wiped = true;
            letGo();
            agent.setTarget(null);
            p.sendSystemMessage(Component.literal("You take the afternoon out of the agent's head. He looks round as if he has forgotten why he came.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            to(Stage.LEAVING);
            return;
        }
    }

    @Override boolean powerUsed(ServerPlayer p, int cost) {
        if (sawPower || wiped || stage == Stage.LEAVING || p.distanceToSqr(agent) > 32 * 32 || CompanyAttention.hidden(p)
                || !agent.hasLineOfSight(p)) return false;
        if (p != subject) return true;
        sawPower = true;
        letGo();
        speak(agent, "There you are.", 24);
        to(Stage.LEAVING);
        return true;
    }

    @Override void down(CompanyAgentEntity a, DamageSource source) {
        if (killed) return;
        killed = true;
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
        if (killer != null) CompanyAttention.raise(killer, CompanyAttention.KILLED, null, "killed an investigator");
        tell(a.position(), 64, "The man who came asking about you won't be going back. The Company will notice that.");
        finish(CompanyOps.AFTER_INVESTIGATION);
    }

    private void resolve() {
        String line;
        if (wiped) {
            CompanyAttention.lower(subject, CompanyAttention.WIPED);
            line = "The man in the dark suit leaves " + village + " with an empty notebook, and no idea why he came.";
        } else if (covered && !sawPower && !fought) {
            CompanyAttention.lower(subject, CompanyAttention.COVERED);
            line = village + "'s people told the man in the dark suit nothing. He leaves with an empty notebook.";
        } else {
            CompanyAttention.raise(subject, CompanyAttention.FILED, null, "an investigator filed his notes");
            line = "The man in the dark suit closes his notebook and leaves " + village + ". He has what he came for.";
        }
        tell(spot(), 96, line);
        CompanyOps.leave(agent);
        finish(CompanyOps.AFTER_INVESTIGATION);
    }

    @Override void callOff() {
        letGo();
        super.callOff();
    }
}
